"""Виртуальный Xbox 360 геймпад через драйвер ViGEmBus.

Игра видит обычный проводной контроллер Xbox, поэтому неважно, откуда она
установлена: Steam, Epic, GOG, пиратка или диск.
"""

import os
import subprocess
import sys
from pathlib import Path

os.environ.setdefault("VGAMEPAD_SKIP_VIGEMBUS_INSTALL", "true")

_vg = None
_error = None


def _load():
    global _vg, _error
    if _vg is not None or _error is not None:
        return
    try:
        import vgamepad
        _vg = vgamepad
    except Exception as e:  # драйвер не стоит или DLL не нашлась
        _error = str(e) or e.__class__.__name__


def available() -> bool:
    _load()
    return _vg is not None


def error() -> str:
    _load()
    return _error or ""


def _signed8(v: int) -> int:
    v &= 0xFF
    return v - 256 if v > 127 else v


class Pad:
    def __init__(self, on_rumble=None):
        _load()
        if _vg is None:
            raise RuntimeError(_error or "ViGEmBus недоступен")
        self._pad = _vg.VX360Gamepad()
        self._last = None
        if on_rumble:
            def cb(client, target, large_motor, small_motor, led_number, user_data):
                on_rumble(large_motor, small_motor)
            self._cb = cb
            self._pad.register_notification(callback_function=cb)

    def set_state(self, buttons, lt, rt, lx, ly, rx, ry):
        state = (buttons, lt, rt, lx, ly, rx, ry)
        if state == self._last:
            return
        self._last = state
        r = self._pad.report
        r.wButtons = buttons
        r.bLeftTrigger = _signed8(lt)
        r.bRightTrigger = _signed8(rt)
        r.sThumbLX, r.sThumbLY, r.sThumbRX, r.sThumbRY = lx, ly, rx, ry
        self._pad.update()

    def reset(self):
        self.set_state(0, 0, 0, 0, 0, 0, 0)

    def close(self):
        try:
            self.reset()
        except Exception:
            pass
        self._pad = None


def driver_installer() -> Path | None:
    """MSI установщика ViGEmBus, который лежит внутри vgamepad / нашего exe."""
    base = Path(getattr(sys, "_MEIPASS", "")) if getattr(sys, "frozen", False) else None
    candidates = []
    if base:
        candidates.append(base / "vgamepad" / "win" / "vigem" / "install" / "x64" / "ViGEmBusSetup_x64.msi")
    try:
        import importlib.util
        spec = importlib.util.find_spec("vgamepad")
        if spec and spec.origin:
            candidates.append(Path(spec.origin).parent / "win" / "vigem" / "install" / "x64" / "ViGEmBusSetup_x64.msi")
    except Exception:
        pass
    for c in candidates:
        if c.exists():
            return c
    return None


def install_driver() -> bool:
    msi = driver_installer()
    if not msi:
        return False
    subprocess.Popen(["msiexec", "/i", str(msi)], shell=False)
    return True


def dry_run():
    """Геймпад-заглушка: печатает состояние вместо драйвера. Для проверки без ViGEmBus."""
    global Pad, _vg, _error

    class DryPad:
        def __init__(self, on_rumble=None):
            self._last = None

        def set_state(self, *state):
            if state != self._last:
                self._last = state
                print("[dry] pad", state, flush=True)

        def reset(self):
            self.set_state(0, 0, 0, 0, 0, 0, 0)

        def close(self):
            pass

    Pad = DryPad
    _vg, _error = object(), None
