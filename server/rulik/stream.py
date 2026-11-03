"""Стрим экрана на телефон: ffmpeg снимает рабочий стол и жмёт его видеокартой в H.264.

ffmpeg отдаёт FLV в stdout: там каждый кадр лежит отдельным тегом с длиной, так что
кадр можно отправлять сразу, как только он закодирован, не дожидаясь следующего.
Кадр режем на куски под один UDP-пакет и рассылаем всем, кто смотрит.
"""

import ctypes
import os
import shutil
import struct
import subprocess
import sys
import threading
import time
from ctypes import wintypes
from pathlib import Path

from . import audio, protocol as P

CHUNK = 1180          # полезная нагрузка одного пакета VIDEO: влезает в MTU 1500 с запасом
ENCODERS = ("h264_nvenc", "h264_amf", "h264_qsv", "libx264")
ZERO_COPY = {"h264_nvenc", "h264_amf"}  # берут текстуры D3D11 прямо с видеокарты

NO_WINDOW = 0x08000000 if os.name == "nt" else 0


# --- где взять ffmpeg ---

def tools_dir() -> Path:
    return Path(os.environ.get("LOCALAPPDATA", Path.home())) / "Rulik"


def find_ffmpeg(cfg=None):
    if cfg and cfg.get("ffmpeg") and Path(cfg["ffmpeg"]).is_file():
        return cfg["ffmpeg"]
    here = Path(sys.executable).parent if getattr(sys, "frozen", False) else Path(__file__).resolve().parent.parent
    for p in (here / "ffmpeg.exe", tools_dir() / "ffmpeg.exe"):
        if p.is_file():
            return str(p)
    return shutil.which("ffmpeg")


# Готовые сборки ffmpeg под Windows: первая меньше, вторая запасная на GitHub.
FFMPEG_URLS = (
    "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip",
    "https://github.com/BtbN/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-win64-gpl.zip",
)


def download_ffmpeg(progress=None):
    """Скачивает ffmpeg.exe в папку Rulik внутри LOCALAPPDATA. progress(доля 0..1). Возвращает путь или кидает ошибку."""
    import tempfile
    import urllib.request
    import zipfile
    dest = tools_dir() / "ffmpeg.exe"
    dest.parent.mkdir(parents=True, exist_ok=True)
    last = None
    for url in FFMPEG_URLS:
        tmp = Path(tempfile.gettempdir()) / "rulik-ffmpeg.zip"
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Rulik"})
            with urllib.request.urlopen(req, timeout=30) as r, open(tmp, "wb") as f:
                total = int(r.headers.get("Content-Length") or 0)
                done = 0
                while True:
                    chunk = r.read(1 << 16)
                    if not chunk:
                        break
                    f.write(chunk)
                    done += len(chunk)
                    if progress and total:
                        progress(done / total)
            with zipfile.ZipFile(tmp) as z:
                name = next(n for n in z.namelist() if n.endswith("/bin/ffmpeg.exe"))
                part = dest.with_suffix(".part")
                with z.open(name) as src, open(part, "wb") as out:
                    shutil.copyfileobj(src, out, 1 << 20)
                part.replace(dest)
            _encoder_cache.pop(str(dest), None)
            return str(dest)
        except Exception as e:  # пробуем следующее зеркало
            last = e
        finally:
            try:
                tmp.unlink()
            except OSError:
                pass
    raise last or RuntimeError("download failed")


_encoder_cache = {}


def pick_encoder(ffmpeg):
    """Первый энкодер из списка, который реально запускается на этом ПК."""
    if ffmpeg in _encoder_cache:
        return _encoder_cache[ffmpeg]
    found = None
    for enc in ENCODERS:
        try:
            r = subprocess.run([ffmpeg, "-hide_banner", "-loglevel", "error", "-f", "lavfi", "-i",
                                "color=black:s=640x360:r=30", "-frames:v", "2", "-c:v", enc, "-f", "null", "-"],
                               capture_output=True, timeout=15, creationflags=NO_WINDOW)
            if r.returncode == 0:
                found = enc
                break
        except (OSError, subprocess.TimeoutExpired):
            continue
    _encoder_cache[ffmpeg] = found
    return found


# --- мониторы ---

def monitors():
    """Прямоугольники мониторов (x, y, w, h), основной первым: так же их нумерует DXGI в простых случаях."""
    out = []
    if os.name != "nt":
        return [(0, 0, 1920, 1080)]
    user32 = ctypes.windll.user32

    class MONITORINFO(ctypes.Structure):
        _fields_ = [("cbSize", wintypes.DWORD), ("rcMonitor", wintypes.RECT),
                    ("rcWork", wintypes.RECT), ("dwFlags", wintypes.DWORD)]

    proc_t = ctypes.WINFUNCTYPE(wintypes.BOOL, wintypes.HMONITOR, wintypes.HDC,
                                ctypes.POINTER(wintypes.RECT), wintypes.LPARAM)

    def cb(hmon, _hdc, _rect, _lp):
        mi = MONITORINFO(cbSize=ctypes.sizeof(MONITORINFO))
        user32.GetMonitorInfoW(hmon, ctypes.byref(mi))
        r = mi.rcMonitor
        out.append((mi.dwFlags & 1, (r.left, r.top, r.right - r.left, r.bottom - r.top)))
        return True

    user32.EnumDisplayMonitors(None, None, proc_t(cb), 0)
    out.sort(key=lambda m: -m[0])
    return [m[1] for m in out] or [(0, 0, 1920, 1080)]


def fit(src_w, src_h, max_w, max_h):
    """Наибольший размер с пропорциями монитора, не больше запрошенного. Чётный: так требует H.264."""
    if max_w <= 0 or max_h <= 0 or (max_w >= src_w and max_h >= src_h):
        return src_w, src_h
    k = min(max_w / src_w, max_h / src_h)
    return max(2, int(src_w * k) // 2 * 2), max(2, int(src_h * k) // 2 * 2)


# --- FLV ---

class FlvReader:
    """Достаёт из FLV кадры H.264 и переводит их в Annex B (00 00 00 01 перед каждым NAL)."""

    START = b"\x00\x00\x00\x01"

    def __init__(self, read):
        self._read = read
        self.headers = b""  # SPS и PPS: кладём перед каждым ключевым кадром

    def _exact(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self._read(n - len(buf))
            if not chunk:
                raise EOFError
            buf += chunk
        return buf

    def frames(self):
        """Генератор (ключевой, данные Annex B). Кончается, когда ffmpeg закрыл поток."""
        try:
            head = self._exact(9)
            if head[:3] != b"FLV":
                return
            self._exact(struct.unpack(">I", head[5:9])[0] - 9 + 4)
            while True:
                tag = self._exact(11)
                size = int.from_bytes(tag[1:4], "big")
                data = self._exact(size)
                self._exact(4)
                if tag[0] != 9 or len(data) < 5 or data[0] & 0x0F != 7:
                    continue
                key = data[0] >> 4 == 1
                if data[1] == 0:
                    self.headers = self._avcc_config(data[5:])
                elif data[1] == 1:
                    body = self._nals(data[5:])
                    if body:
                        yield key, (self.headers + body) if key else body
        except EOFError:
            return

    def _nals(self, buf):
        out = []
        pos = 0
        while pos + 4 <= len(buf):
            n = struct.unpack_from(">I", buf, pos)[0]
            pos += 4
            out.append(self.START + buf[pos:pos + n])
            pos += n
        return b"".join(out)

    def _avcc_config(self, rec):
        out = []
        pos = 5
        count = rec[pos] & 0x1F
        pos += 1
        for _ in range(count):
            n = struct.unpack_from(">H", rec, pos)[0]
            out.append(self.START + rec[pos + 2:pos + 2 + n])
            pos += 2 + n
        count = rec[pos]
        pos += 1
        for _ in range(count):
            n = struct.unpack_from(">H", rec, pos)[0]
            out.append(self.START + rec[pos + 2:pos + 2 + n])
            pos += 2 + n
        return b"".join(out)


def packets(frame_id, key, data, stamp):
    """Режет кадр на пакеты VIDEO."""
    count = max(1, -(-len(data) // CHUNK))
    flags = 1 if key else 0
    for i in range(count):
        yield P.video(frame_id, i, count, flags, stamp, data[i * CHUNK:(i + 1) * CHUNK])


# --- стример ---

class Params:
    def __init__(self, width=1920, height=1080, fps=60, kbps=12000, monitor=0):
        self.width, self.height, self.fps, self.kbps, self.monitor = width, height, fps, kbps, monitor

    def key(self):
        return (self.width, self.height, self.fps, self.kbps, self.monitor)


class Streamer:
    """Один ffmpeg на всех зрителей. Запускается с первым зрителем, гаснет с последним."""

    def __init__(self, server):
        self.server = server
        self.watchers = {}           # id сессии -> сессия
        self.params = None
        self.size = (0, 0)
        self.encoder = ""
        self.status = P.SS_STOPPED
        self.lock = threading.RLock()
        self._proc = None
        self._gen = 0
        self.monitor_rect = monitors()[0]
        self.stats = {"fps": 0.0, "kbps": 0}
        self.listeners = set()       # кому ещё и звук
        self._audio = None
        self._audio_seq = 0

    # --- зрители ---

    def watch(self, session, params: Params, sound=False):
        with self.lock:
            self.watchers[session.id] = session
            if sound:
                self.listeners.add(session.id)
            else:
                self.listeners.discard(session.id)
            self._update_audio()
            restart = self.params is None or self.params.key() != params.key() or self._proc is None
            if restart:
                self.params = params
                self._gen += 1
                gen = self._gen
                threading.Thread(target=self._run, args=(gen, params), daemon=True).start()
            else:
                session.send(self.info())
        if restart:
            self.server.emit()

    def unwatch(self, session_id):
        with self.lock:
            if self.watchers.pop(session_id, None) is None:
                return
            self.listeners.discard(session_id)
            self._update_audio()
            if not self.watchers:
                self._stop_locked()
        self.server.emit()

    def stop(self):
        with self.lock:
            self.watchers.clear()
            self.listeners.clear()
            self._update_audio()
            self._stop_locked()

    # --- звук ---

    def _update_audio(self):
        if self.listeners and self._audio is None and os.name == "nt":
            self._audio = audio.AudioStreamer(self._send_audio, self.server.log)
            self._audio.start()
        elif not self.listeners and self._audio is not None:
            self._audio.stop()
            self._audio = None

    def _send_audio(self, rate, pcm):
        self._audio_seq = (self._audio_seq + 1) & 0xFFFF
        pkt = P.audio(self._audio_seq, rate, pcm)
        with self.lock:
            targets = [self.watchers[i] for i in self.listeners if i in self.watchers]
        for s in targets:
            s.send(pkt)

    def _stop_locked(self):
        self._gen += 1
        self.params = None
        self.status = P.SS_STOPPED
        self._kill()

    def _kill(self):
        proc, self._proc = self._proc, None
        if proc and proc.poll() is None:
            try:
                proc.kill()
            except OSError:
                pass

    def info(self):
        w, h = self.size
        fps = self.params.fps if self.params else 0
        return P.stream_info(self.status, w, h, fps, self.encoder)

    def _broadcast(self, data):
        with self.lock:
            targets = list(self.watchers.values())
        for s in targets:
            s.send(data)

    def _broadcast_frame(self, pkts, key):
        with self.lock:
            targets = list(self.watchers.values())
        for s in targets:
            s.send_frame(pkts, key)

    # --- ffmpeg ---

    def command(self, ffmpeg, encoder, params):
        mons = monitors()
        idx = min(max(params.monitor, 0), len(mons) - 1)
        self.monitor_rect = mons[idx]
        src_w, src_h = self.monitor_rect[2], self.monitor_rect[3]
        w, h = fit(src_w, src_h, params.width, params.height)
        self.size = (w, h)
        fps = max(10, min(params.fps, 144))
        rate = f"{params.kbps}k"
        grab = f"ddagrab=output_idx={idx}:framerate={fps}:dup_frames=1"
        if encoder in ZERO_COPY and (w, h) == (src_w, src_h):
            vf = grab
        else:
            fmt = "yuv420p" if encoder == "libx264" else "nv12"
            vf = f"{grab},hwdownload,format=bgra,scale={w}:{h}:flags=fast_bilinear,format={fmt}"
        cmd = [ffmpeg, "-hide_banner", "-loglevel", "error", "-fflags", "nobuffer",
               "-f", "lavfi", "-i", vf, "-an", "-c:v", encoder]
        if encoder == "h264_nvenc":
            cmd += ["-preset", "p1", "-tune", "ull", "-zerolatency", "1", "-delay", "0", "-rc", "cbr"]
        elif encoder == "h264_amf":
            cmd += ["-usage", "ultralowlatency", "-quality", "speed", "-rc", "cbr"]
        elif encoder == "h264_qsv":
            cmd += ["-preset", "veryfast", "-async_depth", "1", "-look_ahead", "0"]
        else:
            cmd += ["-preset", "ultrafast", "-tune", "zerolatency"]
        cmd += ["-b:v", rate, "-maxrate", rate, "-bufsize", f"{max(params.kbps * 2 // fps, 50)}k",
                "-g", str(fps), "-bf", "0", "-profile:v", "high",
                "-flush_packets", "1", "-f", "flv", "-"]
        return cmd

    def _set(self, gen, status):
        with self.lock:
            if gen != self._gen:
                return False
            self.status = status
        self._broadcast(self.info())
        self.server.emit()
        return True

    def _run(self, gen, params):
        ffmpeg = find_ffmpeg(self.server.config)
        if not ffmpeg:
            self._set(gen, P.SS_NO_FFMPEG)
            return
        encoder = pick_encoder(ffmpeg)
        if not encoder:
            self._set(gen, P.SS_FAILED)
            return
        with self.lock:
            if gen != self._gen:
                return
            self._kill()
            self.encoder = encoder
            cmd = self.command(ffmpeg, encoder, params)
            try:
                proc = subprocess.Popen(cmd, stdin=subprocess.DEVNULL, stdout=subprocess.PIPE,
                                        stderr=subprocess.PIPE, bufsize=0, creationflags=NO_WINDOW)
            except OSError as e:
                self.server.log(f"ffmpeg: {e}")
                self.status = P.SS_FAILED
                proc = None
            self._proc = proc
        if proc is None:
            self._set(gen, P.SS_FAILED)
            return
        self._set(gen, P.SS_OK)
        self.server.log(f"stream {self.size[0]}x{self.size[1]} {params.fps} fps {params.kbps} kbps · {encoder}")
        threading.Thread(target=self._drain_errors, args=(proc,), daemon=True).start()
        frame_id = 0
        window_start = time.monotonic()
        frames = 0
        sent = 0
        for key, data in FlvReader(proc.stdout.read).frames():
            if gen != self._gen:
                break
            stamp = int(time.monotonic() * 1000) & 0xFFFFFFFF
            self._broadcast_frame(list(packets(frame_id, key, data, stamp)), key)
            frame_id = (frame_id + 1) & 0xFFFFFFFF
            frames += 1
            sent += len(data)
            now = time.monotonic()
            if now - window_start >= 1:
                self.stats = {"fps": frames / (now - window_start), "kbps": int(sent * 8 / 1000 / (now - window_start))}
                window_start, frames, sent = now, 0, 0
        if proc.poll() is None:
            proc.kill()
        if gen == self._gen:
            # ffmpeg упал сам: например, игра переключила режим экрана. Перезапускаем.
            self.server.log("ffmpeg stopped, restarting")
            time.sleep(0.5)
            with self.lock:
                if gen != self._gen or not self.watchers:
                    return
                self._gen += 1
                gen = self._gen
            self._run(gen, params)

    def _drain_errors(self, proc):
        for line in iter(proc.stderr.readline, b""):
            msg = line.decode("utf-8", "replace").strip()
            if msg:
                self.server.log("ffmpeg: " + msg[:200])

    # --- мышь по картинке ---

    def absolute(self, nx, ny):
        """Координаты 0..65535 на картинке в точку на рабочем столе Windows."""
        x, y, w, h = self.monitor_rect
        return x + nx * (w - 1) // 65535, y + ny * (h - 1) // 65535
