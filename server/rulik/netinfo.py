import ctypes
import json
import os
import shutil
import socket
import subprocess
import sys
from pathlib import Path

from .i18n import t


def local_ips() -> list[str]:
    ips = []
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("10.255.255.255", 1))
            ips.append(s.getsockname()[0])
    except OSError:
        pass
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            ip = info[4][0]
            if ip not in ips and not ip.startswith("127."):
                ips.append(ip)
    except OSError:
        pass
    return ips or ["127.0.0.1"]


_NO_WINDOW = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0

_PS_NETWORKS = (
    "Get-NetIPAddress -AddressFamily IPv4 | Where-Object { $_.IPAddress -notlike '127.*' -and $_.IPAddress -notlike '169.254.*' } | "
    "ForEach-Object { $p = Get-NetConnectionProfile -InterfaceIndex $_.InterfaceIndex -ErrorAction SilentlyContinue; "
    "[pscustomobject]@{ ip = $_.IPAddress; alias = $_.InterfaceAlias; "
    "public = [bool]($p -and $p.NetworkCategory -eq 'Public') } } | ConvertTo-Json -Compress"
)


def networks() -> list[dict]:
    """Все IPv4 адреса ПК с названием сети и пометкой «общедоступная».

    USB-модем телефона Windows почти всегда помечает общедоступным, и брандмауэр
    режет входящие пакеты, если программе разрешены только частные сети.
    """
    try:
        out = subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-Command", _PS_NETWORKS],
                             capture_output=True, text=True, timeout=8, creationflags=_NO_WINDOW)
        data = json.loads(out.stdout or "[]")
    except (OSError, subprocess.TimeoutExpired, ValueError):
        return [{"ip": ip, "alias": "", "public": False} for ip in local_ips()]
    return data if isinstance(data, list) else [data]


def has_port_rule() -> bool:
    """Есть ли наше правило на порт для всех типов сетей (его ставит кнопка «Брандмауэр»)."""
    try:
        out = subprocess.run(["netsh", "advfirewall", "firewall", "show", "rule", "name=Rulik UDP"],
                             capture_output=True, text=True, timeout=5, creationflags=_NO_WINDOW)
    except (OSError, subprocess.TimeoutExpired):
        return False
    return out.returncode == 0


def find_adb() -> str | None:
    found = shutil.which("adb")
    if found:
        return found
    here = Path(sys.executable).parent if getattr(sys, "frozen", False) else Path(__file__).parent
    for c in (here / "adb.exe",
              here / "platform-tools" / "adb.exe",
              Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk" / "platform-tools" / "adb.exe"):
        if c.exists():
            return str(c)
    return None


def adb_reverse(port: int) -> tuple[bool, str]:
    adb = find_adb()
    if not adb:
        return False, t("no_adb")
    flags = subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0
    try:
        out = subprocess.run([adb, "reverse", f"tcp:{port}", f"tcp:{port}"], capture_output=True,
                             text=True, timeout=10, creationflags=flags)
    except (OSError, subprocess.TimeoutExpired) as e:
        return False, str(e)
    msg = (out.stdout + out.stderr).strip()
    if out.returncode == 0:
        return True, t("adb_ok")
    return False, msg or t("adb_err")


def open_firewall(port: int) -> bool:
    """Правило брандмауэра на порт Rulik. Windows попросит права администратора."""
    rules = " & ".join(
        f'netsh advfirewall firewall add rule name="Rulik {proto}" dir=in action=allow protocol={proto} localport={port} profile=any'
        for proto in ("UDP", "TCP"))
    rc = ctypes.windll.shell32.ShellExecuteW(None, "runas", "cmd.exe", f"/c {rules}", None, 0)
    return rc > 32
