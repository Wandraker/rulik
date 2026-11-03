"""Звук ПК для стрима: WASAPI loopback, то есть ровно то, что сейчас играет в колонках.

Берём общий микс Windows (обычно 48 кГц, float32) и шлём как есть: по локальной сети
3 Мбит/с для стерео не проблема, а сжатие добавило бы и задержку, и зависимость.
Всё через ctypes, без сторонних пакетов.
"""

import array
import ctypes
import threading
import time
from ctypes import POINTER, byref, c_int64, c_uint32, c_uint64, c_void_p, wintypes

CHUNK = 1176  # байт звука в одном пакете, кратно 8 (кадр стерео float32)

CLSCTX_ALL = 0x17
AUDCLNT_STREAMFLAGS_LOOPBACK = 0x00020000
AUDCLNT_BUFFERFLAGS_SILENT = 0x2
E_RENDER, E_CONSOLE = 0, 0
WAVE_FORMAT_IEEE_FLOAT = 3
WAVE_FORMAT_EXTENSIBLE = 0xFFFE


class GUID(ctypes.Structure):
    _fields_ = [("d1", wintypes.DWORD), ("d2", wintypes.WORD), ("d3", wintypes.WORD), ("d4", ctypes.c_ubyte * 8)]

    @classmethod
    def of(cls, s):
        import uuid
        return cls.from_buffer_copy(uuid.UUID(s).bytes_le)


CLSID_MMDeviceEnumerator = GUID.of("BCDE0395-E52F-467C-8E3D-C4579291692E")
IID_IMMDeviceEnumerator = GUID.of("A95664D2-9614-4F35-A746-DE8DB63617E6")
IID_IAudioClient = GUID.of("1CB9AD4C-DBFA-4C32-B178-C2F568A703B2")
IID_IAudioCaptureClient = GUID.of("C8ADBD64-E71E-48A0-A4DE-185C395CD317")
SUBTYPE_FLOAT = GUID.of("00000003-0000-0010-8000-00AA00389B71")


class WAVEFORMATEX(ctypes.Structure):
    _pack_ = 1
    _fields_ = [("wFormatTag", wintypes.WORD), ("nChannels", wintypes.WORD), ("nSamplesPerSec", wintypes.DWORD),
                ("nAvgBytesPerSec", wintypes.DWORD), ("nBlockAlign", wintypes.WORD),
                ("wBitsPerSample", wintypes.WORD), ("cbSize", wintypes.WORD)]


class WAVEFORMATEXTENSIBLE(ctypes.Structure):
    _pack_ = 1
    _fields_ = [("Format", WAVEFORMATEX), ("Samples", wintypes.WORD), ("dwChannelMask", wintypes.DWORD),
                ("SubFormat", GUID)]


def _method(obj, index, *argtypes):
    """Метод COM-интерфейса по номеру в таблице виртуальных функций."""
    vtbl = ctypes.cast(obj, POINTER(POINTER(c_void_p))).contents
    proto = ctypes.WINFUNCTYPE(ctypes.HRESULT, c_void_p, *argtypes)
    fn = proto(vtbl[index])
    return lambda *args: fn(obj, *args)


def _release(obj):
    if obj:
        _method(obj, 2)()


class Loopback:
    """Открытый захват звука по умолчанию. read() отдаёт байты float32 стерео."""

    def __init__(self):
        ole32 = ctypes.windll.ole32
        ole32.CoInitializeEx(None, 0)
        self._objs = []
        enum = c_void_p()
        ole32.CoCreateInstance(byref(CLSID_MMDeviceEnumerator), None, CLSCTX_ALL, byref(IID_IMMDeviceEnumerator), byref(enum))
        self._objs.append(enum)
        dev = c_void_p()
        _method(enum, 4, ctypes.c_int, ctypes.c_int, POINTER(c_void_p))(E_RENDER, E_CONSOLE, byref(dev))
        self._objs.append(dev)
        client = c_void_p()
        _method(dev, 3, POINTER(GUID), wintypes.DWORD, c_void_p, POINTER(c_void_p))(
            byref(IID_IAudioClient), CLSCTX_ALL, None, byref(client))
        self._objs.append(client)
        self.client = client
        fmt_ptr = POINTER(WAVEFORMATEX)()
        _method(client, 8, POINTER(POINTER(WAVEFORMATEX)))(byref(fmt_ptr))
        fmt = fmt_ptr.contents
        self.rate = fmt.nSamplesPerSec
        self.channels = fmt.nChannels
        self.bits = fmt.wBitsPerSample
        self.block = fmt.nBlockAlign
        is_float = fmt.wFormatTag == WAVE_FORMAT_IEEE_FLOAT
        if fmt.wFormatTag == WAVE_FORMAT_EXTENSIBLE:
            ext = ctypes.cast(fmt_ptr, POINTER(WAVEFORMATEXTENSIBLE)).contents
            is_float = bytes(ext.SubFormat) == bytes(SUBTYPE_FLOAT)
        self.is_float = is_float and self.bits == 32
        # буфер 50 мс: меньше Windows для loopback всё равно не даёт
        _method(client, 3, ctypes.c_int, wintypes.DWORD, c_int64, c_int64, POINTER(WAVEFORMATEX), c_void_p)(
            0, AUDCLNT_STREAMFLAGS_LOOPBACK, 500_000, 0, fmt_ptr, None)
        ctypes.windll.ole32.CoTaskMemFree(fmt_ptr)
        capture = c_void_p()
        _method(client, 14, POINTER(GUID), POINTER(c_void_p))(byref(IID_IAudioCaptureClient), byref(capture))
        self._objs.append(capture)
        self._get = _method(capture, 3, POINTER(c_void_p), POINTER(c_uint32), POINTER(wintypes.DWORD),
                            POINTER(c_uint64), POINTER(c_uint64))
        self._rel = _method(capture, 4, c_uint32)
        self._next = _method(capture, 5, POINTER(c_uint32))
        _method(client, 10)()

    def read(self):
        """Всё, что накопилось с прошлого раза. b'' если тишина и Windows ничего не дала."""
        out = []
        size = c_uint32()
        while True:
            self._next(byref(size))
            if size.value == 0:
                break
            data = c_void_p()
            frames = c_uint32()
            flags = wintypes.DWORD()
            self._get(byref(data), byref(frames), byref(flags), None, None)
            n = frames.value
            if flags.value & AUDCLNT_BUFFERFLAGS_SILENT or not data.value:
                raw = bytes(n * self.block)
            else:
                raw = ctypes.string_at(data.value, n * self.block)
            self._rel(n)
            out.append(self._stereo_float(raw, n))
        return b"".join(out)

    def _stereo_float(self, raw, n):
        if self.is_float and self.channels == 2:
            return raw
        # редкий случай: 5.1 или целые отсчёты. Берём левый и правый, переводим в float.
        if self.is_float:
            src = array.array("f", raw)
            scale = 1.0
        elif self.bits == 16:
            src = array.array("h", raw)
            scale = 1 / 32768
        else:
            src = array.array("i", raw)
            scale = 1 / 2147483648
        ch = self.channels
        out = array.array("f", bytes(n * 8))
        for i in range(n):
            out[2 * i] = src[i * ch] * scale
            out[2 * i + 1] = src[i * ch + (1 if ch > 1 else 0)] * scale
        return out.tobytes()

    def close(self):
        try:
            _method(self.client, 11)()
        except OSError:
            pass
        for o in reversed(self._objs):
            try:
                _release(o)
            except OSError:
                pass
        self._objs = []


class AudioStreamer:
    """Поток: читает loopback каждые 5 мс и отдаёт пакеты в send(rate, байты)."""

    def __init__(self, send, log=print):
        self._send = send
        self._log = log
        self._stop = threading.Event()
        self._thread = None

    def start(self):
        if self._thread and self._thread.is_alive():
            return
        self._stop.clear()
        self._thread = threading.Thread(target=self._run, daemon=True)
        self._thread.start()

    def stop(self):
        self._stop.set()

    def _run(self):
        while not self._stop.is_set():
            try:
                cap = Loopback()
            except OSError as e:
                self._log(f"audio: {e}")
                if self._stop.wait(3):
                    return
                continue
            try:
                while not self._stop.wait(0.005):
                    data = cap.read()
                    for i in range(0, len(data), CHUNK):
                        self._send(cap.rate, data[i:i + CHUNK])
            except OSError:
                # сменили устройство вывода (наушники, HDMI): открываем заново
                time.sleep(0.2)
            finally:
                cap.close()
