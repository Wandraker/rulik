"""Протокол Rulik. Описание форматов: docs/PROTOCOL.md.

Все числа little-endian. Каждый пакет начинается с заголовка из 4 байт:
'R' 'K' <версия> <тип>. По TCP перед пакетом идёт длина (u16).
"""

import struct

MAGIC = b"RK"
VERSION = 1
DEFAULT_PORT = 47800

# Телефон -> ПК
HELLO = 0x01
PAD = 0x02
MOUSE_MOVE = 0x03
MOUSE_BTN = 0x04
SCROLL = 0x05
KEY = 0x06
TEXT = 0x07
PING = 0x08
BYE = 0x09
HELD = 0x0B
DISCOVER = 0x10

# ПК -> телефон
WELCOME = 0x81
RUMBLE = 0x83
PONG = 0x88
DISCOVER_REPLY = 0x90

# Статусы WELCOME
OK = 0
BAD_PIN = 1
FULL = 2
REHELLO = 3  # сервер не знает это устройство (перезапускали), нужен новый HELLO

# Флаги возможностей сервера
CAP_GAMEPAD = 0x01

PAD_FMT = struct.Struct("<HBBhhhh")
MOVE_FMT = struct.Struct("<hh")
U32 = struct.Struct("<I")


def header(kind: int) -> bytes:
    return MAGIC + bytes((VERSION, kind))


def parse(data: bytes):
    """Возвращает (тип, тело) или None, если пакет не наш."""
    if len(data) < 4 or data[:2] != MAGIC or data[2] != VERSION:
        return None
    return data[3], data[4:]


def read_str(buf: bytes, pos: int):
    n = buf[pos]
    return buf[pos + 1:pos + 1 + n].decode("utf-8", "replace"), pos + 1 + n


def short_str(s: str) -> bytes:
    raw = s.encode("utf-8")[:255]
    return bytes((len(raw),)) + raw


def welcome(status: int, caps: int, name: str) -> bytes:
    return header(WELCOME) + bytes((status, caps)) + short_str(name)


def discover_reply(port: int, pin_required: bool, name: str) -> bytes:
    return header(DISCOVER_REPLY) + struct.pack("<HB", port, 1 if pin_required else 0) + short_str(name)


def pong(stamp: bytes) -> bytes:
    return header(PONG) + stamp[:4]


def rumble(large: int, small: int) -> bytes:
    return header(RUMBLE) + bytes((large & 0xFF, small & 0xFF))
