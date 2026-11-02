import json
import os
import secrets
from pathlib import Path

from . import protocol as P

DIR = Path(os.environ.get("APPDATA", Path.home())) / "Rulik"
FILE = DIR / "config.json"


def new_pin() -> str:
    return f"{secrets.randbelow(10000):04d}"


def load() -> dict:
    cfg = {}
    try:
        cfg = json.loads(FILE.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        pass
    cfg.setdefault("port", P.DEFAULT_PORT)
    cfg.setdefault("pin", new_pin())
    cfg.setdefault("require_pin", True)
    cfg.setdefault("name", "")
    return cfg


def save(cfg: dict):
    DIR.mkdir(parents=True, exist_ok=True)
    FILE.write_text(json.dumps(cfg, ensure_ascii=False, indent=2), encoding="utf-8")
