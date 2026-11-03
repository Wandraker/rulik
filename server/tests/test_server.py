"""Сервер целиком по сети: поиск, PIN, UDP и TCP, сброс зажатых клавиш. Ввод в Windows не уходит."""

import socket
import struct
import time
import unittest

from rulik import protocol as P, winput

events = []


def _fake(name):
    return lambda *args: events.append((name,) + args)


for _n in ("key", "text", "mouse_move", "mouse_button", "scroll"):
    setattr(winput, _n, _fake(_n))

from rulik.server import Server  # noqa: E402  (после подмены winput)


def free_port():
    with socket.socket() as s:
        s.bind(("127.0.0.1", 0))
        return s.getsockname()[1]


class ServerTest(unittest.TestCase):
    def setUp(self):
        events.clear()
        self.port = free_port()
        self.srv = Server({"port": self.port, "pin": "1234", "require_pin": True, "name": "TestPC"}, on_log=lambda m: None)
        self.srv.start()
        self.addr = ("127.0.0.1", self.port)
        self.u = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.u.settimeout(2)

    def tearDown(self):
        self.u.close()
        self.srv.stop()

    def rx(self):
        data, _ = self.u.recvfrom(2048)
        return P.parse(data)

    def send(self, kind, body=b""):
        self.u.sendto(P.header(kind) + body, self.addr)

    def hello(self, pin="1234"):
        self.send(P.HELLO, P.short_str(pin) + P.short_str("Phone"))
        return self.rx()

    def wait(self, cond, timeout=1.0):
        end = time.time() + timeout
        while time.time() < end:
            if cond():
                return True
            time.sleep(0.01)
        return False

    def test_video_channel_is_hidden_helper(self):
        self.send(P.HELLO, P.short_str("1234") + P.short_str("Phone" + P.VIDEO_SUFFIX))
        self.assertEqual(self.rx()[0], P.WELCOME)
        s = self.srv.clients()[0]
        self.assertTrue(s.aux)
        self.assertEqual(s.name, "Phone")

    def test_discover(self):
        self.send(P.DISCOVER)
        kind, body = self.rx()
        self.assertEqual(kind, P.DISCOVER_REPLY)
        port, flags = struct.unpack_from("<HB", body)
        self.assertEqual((port, flags), (self.port, 1))
        self.assertEqual(P.read_str(body, 3)[0], "TestPC")

    def test_pin(self):
        self.assertEqual(self.hello("0000")[1][0], P.BAD_PIN)
        self.assertEqual(self.hello()[1][0], P.OK)

    def test_unknown_device_is_asked_to_rehello(self):
        self.send(P.PING, struct.pack("<I", 1))
        kind, body = self.rx()
        self.assertEqual((kind, body[0]), (P.WELCOME, P.REHELLO))

    def test_input_needs_auth(self):
        self.send(P.KEY, struct.pack("<HB", 0x41, 1))
        time.sleep(0.1)
        self.assertEqual(events, [])

    def test_keys_and_reconcile(self):
        self.hello()
        self.send(P.KEY, struct.pack("<HB", 0x57, 1))
        self.send(P.KEY, struct.pack("<HB", 0x57, 1))  # повтор не должен жать второй раз
        self.assertTrue(self.wait(lambda: ("key", 0x57, True) in events))
        # телефон говорит «ничего не зажато»: сервер отпускает W сам
        self.send(P.HELD, bytes((0, 0)))
        self.assertTrue(self.wait(lambda: ("key", 0x57, False) in events))
        self.assertEqual(events.count(("key", 0x57, True)), 1)

    def test_ping(self):
        self.hello()
        self.send(P.PING, struct.pack("<II", 77, 5))
        kind, body = self.rx()
        self.assertEqual((kind, struct.unpack("<I", body)[0]), (P.PONG, 77))

    def test_tcp(self):
        t = socket.create_connection(self.addr, timeout=2)
        try:
            msg = P.header(P.HELLO) + P.short_str("1234") + P.short_str("Usb")
            t.sendall(struct.pack("<H", len(msg)) + msg)
            data = t.recv(64)
            self.assertEqual(P.parse(data[2:])[1][0], P.OK)
            move = P.header(P.MOUSE_MOVE) + struct.pack("<hh", 5, -3)
            t.sendall(struct.pack("<H", len(move)) + move)
            self.assertTrue(self.wait(lambda: ("mouse_move", 5, -3) in events))
        finally:
            t.close()
        self.assertTrue(self.wait(lambda: all(s.addr for s in self.srv.clients())))


if __name__ == "__main__":
    unittest.main()


class StreamPartsTest(unittest.TestCase):
    def test_flv_to_annexb(self):
        from rulik.stream import FlvReader
        sps, pps, idr, p = b"\x67\x64\x00\x20", b"\x68\xee", b"\x65\x88\x84", b"\x41\x9a"

        def tag(kind, data):
            return bytes((kind,)) + len(data).to_bytes(3, "big") + b"\0" * 7 + data + struct.pack(">I", len(data) + 11)

        conf = b"\x01\x64\x00\x20\xff\xe1" + struct.pack(">H", len(sps)) + sps + b"\x01" + struct.pack(">H", len(pps)) + pps
        flv = b"FLV\x01\x01\x00\x00\x00\x09" + b"\0\0\0\0"
        flv += tag(18, b"meta")
        flv += tag(9, b"\x17\x00\x00\x00\x00" + conf)
        flv += tag(9, b"\x17\x01\x00\x00\x00" + struct.pack(">I", len(idr)) + idr)
        flv += tag(9, b"\x27\x01\x00\x00\x00" + struct.pack(">I", len(p)) + p)
        pos = [0]

        def read(n):
            chunk = flv[pos[0]:pos[0] + n]
            pos[0] += len(chunk)
            return chunk

        frames = list(FlvReader(read).frames())
        start = b"\0\0\0\1"
        self.assertEqual(frames, [(True, start + sps + start + pps + start + idr), (False, start + p)])

    def test_packets_split_and_fit(self):
        from rulik.stream import CHUNK, fit, packets
        data = bytes(range(256)) * 20
        pkts = list(packets(7, True, data, 123))
        self.assertEqual(len(pkts), -(-len(data) // CHUNK))
        body = b"".join(P.parse(x)[1][P.VIDEO_FMT.size:] for x in pkts)
        self.assertEqual(body, data)
        self.assertTrue(all(len(x) <= 1300 for x in pkts))
        self.assertEqual(fit(1920, 1080, 1280, 720), (1280, 720))
        self.assertEqual(fit(2560, 1440, 1920, 1080), (1920, 1080))
        self.assertEqual(fit(1920, 1080, 0, 0), (1920, 1080))
        self.assertEqual(fit(3440, 1440, 1280, 720), (1280, 534))
