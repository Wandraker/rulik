"""Клавиатура и мышь через SendInput.

Клавиши шлём скан-кодами: игры на DirectInput/RawInput читают именно их,
виртуальные коды VK многие игры просто не видят.
"""

import ctypes
from ctypes import wintypes

user32 = ctypes.WinDLL("user32", use_last_error=True)

INPUT_MOUSE = 0
INPUT_KEYBOARD = 1

KEYEVENTF_EXTENDEDKEY = 0x0001
KEYEVENTF_KEYUP = 0x0002
KEYEVENTF_UNICODE = 0x0004
KEYEVENTF_SCANCODE = 0x0008

MOUSEEVENTF_MOVE = 0x0001
MOUSEEVENTF_LEFTDOWN = 0x0002
MOUSEEVENTF_LEFTUP = 0x0004
MOUSEEVENTF_RIGHTDOWN = 0x0008
MOUSEEVENTF_RIGHTUP = 0x0010
MOUSEEVENTF_MIDDLEDOWN = 0x0020
MOUSEEVENTF_MIDDLEUP = 0x0040
MOUSEEVENTF_XDOWN = 0x0080
MOUSEEVENTF_XUP = 0x0100
MOUSEEVENTF_WHEEL = 0x0800
MOUSEEVENTF_HWHEEL = 0x1000

MAPVK_VK_TO_VSC_EX = 4

ULONG_PTR = ctypes.c_size_t


class MOUSEINPUT(ctypes.Structure):
    _fields_ = [("dx", wintypes.LONG), ("dy", wintypes.LONG), ("mouseData", wintypes.DWORD),
                ("dwFlags", wintypes.DWORD), ("time", wintypes.DWORD), ("dwExtraInfo", ULONG_PTR)]


class KEYBDINPUT(ctypes.Structure):
    _fields_ = [("wVk", wintypes.WORD), ("wScan", wintypes.WORD), ("dwFlags", wintypes.DWORD),
                ("time", wintypes.DWORD), ("dwExtraInfo", ULONG_PTR)]


class HARDWAREINPUT(ctypes.Structure):
    _fields_ = [("uMsg", wintypes.DWORD), ("wParamL", wintypes.WORD), ("wParamH", wintypes.WORD)]


class _U(ctypes.Union):
    _fields_ = [("mi", MOUSEINPUT), ("ki", KEYBDINPUT), ("hi", HARDWAREINPUT)]


class INPUT(ctypes.Structure):
    _anonymous_ = ("u",)
    _fields_ = [("type", wintypes.DWORD), ("u", _U)]


user32.SendInput.argtypes = (wintypes.UINT, ctypes.POINTER(INPUT), ctypes.c_int)
user32.SendInput.restype = wintypes.UINT
user32.MapVirtualKeyW.argtypes = (wintypes.UINT, wintypes.UINT)
user32.MapVirtualKeyW.restype = wintypes.UINT

# Медиа- и браузерные клавиши у SendInput работают только через VK
_VK_ONLY = set(range(0xA6, 0xB8))

# Клавиши с префиксом E0. MapVirtualKey для стрелок его не возвращает,
# и без флага игра получила бы цифры с numpad вместо стрелок.
_EXTENDED = {0x21, 0x22, 0x23, 0x24, 0x25, 0x26, 0x27, 0x28, 0x2C, 0x2D, 0x2E,
             0x5B, 0x5C, 0x5D, 0x6F, 0x90, 0xA3, 0xA5}

_MOUSE_FLAGS = {
    1: (MOUSEEVENTF_LEFTDOWN, MOUSEEVENTF_LEFTUP, 0),
    2: (MOUSEEVENTF_RIGHTDOWN, MOUSEEVENTF_RIGHTUP, 0),
    3: (MOUSEEVENTF_MIDDLEDOWN, MOUSEEVENTF_MIDDLEUP, 0),
    4: (MOUSEEVENTF_XDOWN, MOUSEEVENTF_XUP, 1),
    5: (MOUSEEVENTF_XDOWN, MOUSEEVENTF_XUP, 2),
}


def _send(*inputs: INPUT):
    arr = (INPUT * len(inputs))(*inputs)
    user32.SendInput(len(inputs), arr, ctypes.sizeof(INPUT))


def key(vk: int, down: bool):
    inp = INPUT(type=INPUT_KEYBOARD)
    flags = 0 if down else KEYEVENTF_KEYUP
    scan = 0 if vk in _VK_ONLY else user32.MapVirtualKeyW(vk, MAPVK_VK_TO_VSC_EX)
    if scan:
        inp.ki.wScan = scan & 0xFF
        flags |= KEYEVENTF_SCANCODE
        if vk in _EXTENDED or scan & 0xFF00 in (0xE000, 0xE100):
            flags |= KEYEVENTF_EXTENDEDKEY
    else:
        inp.ki.wVk = vk
    inp.ki.dwFlags = flags
    _send(inp)


def text(s: str):
    raw = s.encode("utf-16-le")
    events = []
    for i in range(0, len(raw), 2):
        unit = int.from_bytes(raw[i:i + 2], "little")
        for flags in (KEYEVENTF_UNICODE, KEYEVENTF_UNICODE | KEYEVENTF_KEYUP):
            inp = INPUT(type=INPUT_KEYBOARD)
            inp.ki.wScan = unit
            inp.ki.dwFlags = flags
            events.append(inp)
    if events:
        _send(*events)


def mouse_move(dx: int, dy: int):
    inp = INPUT(type=INPUT_MOUSE)
    inp.mi.dx, inp.mi.dy, inp.mi.dwFlags = dx, dy, MOUSEEVENTF_MOVE
    _send(inp)


def mouse_button(button: int, down: bool):
    spec = _MOUSE_FLAGS.get(button)
    if not spec:
        return
    inp = INPUT(type=INPUT_MOUSE)
    inp.mi.dwFlags = spec[0] if down else spec[1]
    inp.mi.mouseData = spec[2]
    _send(inp)


def scroll(vertical: int, horizontal: int):
    events = []
    for amount, flag in ((vertical, MOUSEEVENTF_WHEEL), (horizontal, MOUSEEVENTF_HWHEEL)):
        if amount:
            inp = INPUT(type=INPUT_MOUSE)
            inp.mi.mouseData = ctypes.c_uint32(amount).value
            inp.mi.dwFlags = flag
            events.append(inp)
    if events:
        _send(*events)


def dry_run():
    """Вместо ввода в Windows печатаем события. Удобно проверять телефон, не трогая ПК."""
    g = globals()
    for name in ("key", "text", "mouse_move", "mouse_button", "scroll"):
        def logger(*args, _n=name):
            print(f"[dry] {_n}{args}", flush=True)
        g[name] = logger
