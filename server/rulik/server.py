"""Сетевое ядро: UDP для Wi-Fi, TCP для USB (adb reverse) и всё, что приходит, в ввод ПК."""

import queue
import socket
import struct
import sys
import threading
import time

from . import gamepad, protocol as P, stream, winput
from .i18n import t

RELEASE_AFTER = 1.5   # нет пакетов столько секунд: отпускаем все кнопки
DROP_AFTER = 20.0     # а через столько забываем устройство и убираем его геймпад
MAX_CLIENTS = 8


class Session:
    def __init__(self, server, key, send, name):
        self.server = server
        self.id = key
        self._send = send
        self.name = name
        self.addr = key if isinstance(key, tuple) else None
        self.last_seen = time.monotonic()
        self.keys = set()
        self.mouse = set()
        self.pad = None
        self.pad_failed = False
        self.rtt_ms = None
        self.idle = False
        self.watching = False
        self._video = None
        self._skip_to_key = False

    def send(self, data: bytes):
        try:
            self._send(data)
        except OSError:
            pass

    def send_frame(self, packets, key):
        """Кадр видео. По Wi-Fi уходит сразу. По кабелю идёт через очередь: если она забита,
        кадр выкидываем, иначе видео встанет в TCP перед кнопками и пингом."""
        if self.addr is not None:
            for p in packets:
                self.send(p)
            return
        if self._video is None:
            self._video = queue.Queue(maxsize=3)
            threading.Thread(target=self._video_loop, daemon=True).start()
        if self._skip_to_key and not key:
            return
        try:
            self._video.put_nowait(packets)
            self._skip_to_key = False
        except queue.Full:
            self._skip_to_key = True

    def _video_loop(self):
        while True:
            packets = self._video.get()
            if packets is None:
                return
            for p in packets:
                self.send(p)

    # --- клавиатура и мышь ---

    def key(self, vk, down):
        if down and vk not in self.keys:
            self.keys.add(vk)
            winput.key(vk, True)
        elif not down and vk in self.keys:
            self.keys.discard(vk)
            winput.key(vk, False)

    def mouse_button(self, btn, down):
        if down and btn not in self.mouse:
            self.mouse.add(btn)
            winput.mouse_button(btn, True)
        elif not down and btn in self.mouse:
            self.mouse.discard(btn)
            winput.mouse_button(btn, False)

    def reconcile(self, mouse_mask, vks):
        """Телефон периодически шлёт, что у него зажато. Чиним потерянные пакеты UDP."""
        for vk in list(self.keys - vks):
            self.key(vk, False)
        for vk in vks - self.keys:
            self.key(vk, True)
        held = {b for b in range(1, 6) if mouse_mask & (1 << (b - 1))}
        for b in list(self.mouse - held):
            self.mouse_button(b, False)
        for b in held - self.mouse:
            self.mouse_button(b, True)

    # --- геймпад ---

    def pad_state(self, *state):
        if self.pad is None and not self.pad_failed:
            try:
                self.pad = gamepad.Pad(on_rumble=self._rumble)
                self.server.emit()
            except Exception as e:
                self.pad_failed = True
                self.server.log(t("pad_fail", name=self.name, err=e))
        if self.pad:
            self.pad.set_state(*state)

    def _rumble(self, large, small):
        self.send(P.rumble(large, small))

    def release_all(self):
        for vk in list(self.keys):
            self.key(vk, False)
        for b in list(self.mouse):
            self.mouse_button(b, False)
        if self.pad:
            self.pad.reset()

    def close(self):
        if self._video is not None:
            try:
                self._video.put_nowait(None)
            except queue.Full:
                pass
        self.release_all()
        if self.pad:
            self.pad.close()
            self.pad = None


class Server:
    def __init__(self, config, on_change=None, on_log=None):
        self.config = config
        self.port = int(config.get("port", P.DEFAULT_PORT))
        self.sessions: dict = {}
        self.lock = threading.RLock()
        self._on_change = on_change
        self._on_log = on_log
        self._stop = threading.Event()
        self.udp = None
        self.tcp = None
        self.streamer = stream.Streamer(self)

    # --- служебное ---

    def log(self, msg):
        if self._on_log:
            self._on_log(msg)
        else:
            try:
                print(msg, flush=True)
            except UnicodeEncodeError:
                # консоль Windows в cp1251/cp866 не знает «×» и подобного: лучше вопросик, чем упавший поток
                enc = getattr(sys.stdout, "encoding", None) or "ascii"
                print(msg.encode(enc, "replace").decode(enc), flush=True)

    def emit(self):
        if self._on_change:
            self._on_change()

    @property
    def pin(self) -> str:
        return self.config.get("pin", "") if self.config.get("require_pin", True) else ""

    @property
    def name(self) -> str:
        return self.config.get("name") or socket.gethostname()

    def clients(self):
        with self.lock:
            return list(self.sessions.values())

    # --- запуск ---

    def start(self):
        self.udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        self.udp.setsockopt(socket.SOL_SOCKET, socket.SO_RCVBUF, 1 << 20)
        self.udp.bind(("0.0.0.0", self.port))
        self.tcp = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.tcp.bind(("0.0.0.0", self.port))
        self.tcp.listen(4)
        for target in (self._udp_loop, self._tcp_loop, self._watchdog):
            threading.Thread(target=target, daemon=True).start()
        self.log(t("listening", port=self.port))

    def stop(self):
        self._stop.set()
        self.streamer.stop()
        with self.lock:
            for s in self.sessions.values():
                s.send(P.header(P.BYE))
                s.close()
            self.sessions.clear()
        for sock in (self.udp, self.tcp):
            try:
                sock.close()
            except Exception:
                pass

    def kick_all(self):
        self.streamer.stop()
        with self.lock:
            for s in list(self.sessions.values()):
                s.send(P.header(P.BYE))
                s.close()
            self.sessions.clear()
        self.emit()

    # --- сеть ---

    def _udp_loop(self):
        while not self._stop.is_set():
            try:
                data, addr = self.udp.recvfrom(2048)
            except OSError:
                if self._stop.is_set():
                    return
                continue
            self.handle(data, addr, lambda b, a=addr: self.udp.sendto(b, a))

    def _tcp_loop(self):
        while not self._stop.is_set():
            try:
                conn, addr = self.tcp.accept()
            except OSError:
                if self._stop.is_set():
                    return
                continue
            conn.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
            # маленький буфер: лучше выкинуть кадр, чем копить секунду видео в кабеле
            conn.setsockopt(socket.SOL_SOCKET, socket.SO_SNDBUF, 1 << 16)
            threading.Thread(target=self._tcp_client, args=(conn,), daemon=True).start()

    def _tcp_client(self, conn):
        key = ("tcp", id(conn))
        lock = threading.Lock()

        def send(b):
            with lock:
                conn.sendall(struct.pack("<H", len(b)) + b)

        buf = b""
        try:
            while not self._stop.is_set():
                chunk = conn.recv(4096)
                if not chunk:
                    break
                buf += chunk
                while len(buf) >= 2:
                    n = struct.unpack_from("<H", buf)[0]
                    if len(buf) < 2 + n:
                        break
                    self.handle(buf[2:2 + n], key, send)
                    buf = buf[2 + n:]
        except OSError:
            pass
        finally:
            self._drop(key)
            conn.close()

    def _drop(self, key):
        with self.lock:
            s = self.sessions.pop(key, None)
        if s:
            self.streamer.unwatch(s.id)
            s.close()
            self.log(t("left", name=s.name))
            self.emit()

    def _watchdog(self):
        while not self._stop.wait(0.25):
            now = time.monotonic()
            changed = False
            with self.lock:
                for key, s in list(self.sessions.items()):
                    quiet = now - s.last_seen
                    if quiet > DROP_AFTER and s.addr is not None:
                        self.sessions.pop(key)
                        self.streamer.unwatch(s.id)
                        s.close()
                        self.log(t("vanished", name=s.name))
                        changed = True
                    elif quiet > RELEASE_AFTER and not s.idle:
                        s.idle = True
                        s.release_all()
                        changed = True
            if changed:
                self.emit()

    # --- разбор пакетов ---

    def handle(self, data, key, send):
        parsed = P.parse(data)
        if not parsed:
            return
        kind, body = parsed
        try:
            if kind == P.DISCOVER:
                send(P.discover_reply(self.port, bool(self.pin), self.name))
                return
            if kind == P.HELLO:
                self._hello(body, key, send)
                return
            with self.lock:
                s = self.sessions.get(key)
            if s is None:
                if kind == P.PING:
                    # сервер перезапускали: пусть телефон заново поздоровается
                    send(P.welcome(P.REHELLO, 0, self.name))
                return
            s.last_seen = time.monotonic()
            if s.idle:
                s.idle = False
                self.emit()
            self._dispatch(s, kind, body)
        except (struct.error, IndexError, ValueError):
            pass  # битый пакет просто выкидываем
        except Exception as e:  # что угодно ещё не должно ронять сетевой поток
            self.log(f"{e.__class__.__name__}: {e}")

    def _hello(self, body, key, send):
        pin, pos = P.read_str(body, 0)
        name, _ = P.read_str(body, pos)
        caps = (P.CAP_GAMEPAD if gamepad.available() else 0) | P.CAP_STREAM
        if self.pin and pin != self.pin:
            send(P.welcome(P.BAD_PIN, caps, self.name))
            self.log(t("bad_pin", name=name or key))
            return
        with self.lock:
            s = self.sessions.get(key)
            if s is None:
                if len(self.sessions) >= MAX_CLIENTS:
                    send(P.welcome(P.FULL, caps, self.name))
                    return
                s = Session(self, key, send, name or t("phone"))
                self.sessions[key] = s
                self.log(t("joined", name=s.name))
            else:
                s.last_seen = time.monotonic()
        send(P.welcome(P.OK, caps, self.name))
        self.emit()

    def _dispatch(self, s: Session, kind, body):
        if kind == P.PAD:
            s.pad_state(*P.PAD_FMT.unpack_from(body))
        elif kind == P.MOUSE_MOVE:
            dx, dy = P.MOVE_FMT.unpack_from(body)
            winput.mouse_move(dx, dy)
        elif kind == P.MOUSE_BTN:
            s.mouse_button(body[0], bool(body[1]))
        elif kind == P.SCROLL:
            v, h = P.MOVE_FMT.unpack_from(body)
            winput.scroll(v, h)
        elif kind == P.KEY:
            vk, down = struct.unpack_from("<HB", body)
            s.key(vk, bool(down))
        elif kind == P.TEXT:
            winput.text(body.decode("utf-8", "replace"))
        elif kind == P.HELD:
            mask, count = body[0], body[1]
            vks = set(struct.unpack_from(f"<{count}H", body, 2)) if count else set()
            s.reconcile(mask, vks)
        elif kind == P.PING:
            s.send(P.pong(body))
            if len(body) >= 8:
                s.rtt_ms = struct.unpack_from("<I", body, 4)[0] or None
        elif kind == P.MOUSE_ABS:
            nx, ny = P.ABS_FMT.unpack_from(body)
            winput.mouse_to(*self.streamer.absolute(nx, ny))
        elif kind == P.STREAM_START:
            w, h, fps, kbps, mon, flags = P.START_FMT.unpack_from(body)
            s.watching = True
            self.streamer.watch(s, stream.Params(w, h, fps, kbps, mon), bool(flags & P.START_AUDIO))
        elif kind == P.STREAM_STOP:
            s.watching = False
            self.streamer.unwatch(s.id)
        elif kind == P.BYE:
            self._drop(s.id)
