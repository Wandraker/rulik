"""Русский, если Windows на русском, иначе английский."""

import ctypes
import locale


def _detect() -> str:
    try:
        lang_id = ctypes.windll.kernel32.GetUserDefaultUILanguage()
        return "ru" if lang_id & 0x3FF == 0x19 else "en"
    except Exception:
        return "ru" if (locale.getlocale()[0] or "").lower().startswith("ru") else "en"


LANG = _detect()

_T = {
    "tagline": ("рули своим ПК с телефона", "control your PC from your phone"),
    "address": ("Адрес этого ПК", "This PC's address"),
    "public_warn": ("Сеть «{name}» Windows считает общедоступной и не пускает телефон. Нажмите «Брандмауэр».",
                    "Windows treats the \"{name}\" network as public and blocks the phone. Press \"Firewall\"."),
    "public_tag": ("общедоступная", "public"),
    "via_net": ("сеть", "network"),
    "address_hint": ("Телефон найдёт ПК сам в общей сети Wi-Fi или через USB-модем.",
                     "The phone finds this PC by itself over shared Wi-Fi or USB tethering."),
    "pin": ("PIN для подключения", "Connection PIN"),
    "new_pin": ("Новый PIN", "New PIN"),
    "ask_pin": ("Спрашивать PIN", "Require PIN"),
    "pad": ("Геймпад Xbox", "Xbox gamepad"),
    "pad_ok": ("Готов. Каждый телефон получает свой геймпад.", "Ready. Every phone gets its own gamepad."),
    "pad_missing": ("Нужен драйвер ViGEmBus. Без него работают только мышь и клавиатура.",
                    "The ViGEmBus driver is needed. Without it only mouse and keyboard work."),
    "install_driver": ("Установить драйвер", "Install driver"),
    "driver_started": ("Запустился установщик ViGEmBus. После установки перезапустите Rulik Server.",
                       "The ViGEmBus installer has started. Restart Rulik Server when it finishes."),
    "stream": ("Картинка на телефон", "Picture to the phone"),
    "stream_ready": ("Готово. Телефон может показывать экран этого ПК.", "Ready. A phone can show this PC's screen."),
    "streaming": ("Идёт: {w}×{h}, {fps} к/с, {mbps} Мбит/с · {enc}", "Streaming: {w}×{h}, {fps} fps, {mbps} Mbit/s · {enc}"),
    "no_ffmpeg": ("Для картинки на телефоне нужен ffmpeg (около 100 МБ, один раз).",
                  "Showing the picture on the phone needs ffmpeg (about 100 MB, once)."),
    "get_ffmpeg": ("Скачать ffmpeg", "Download ffmpeg"),
    "ffmpeg_loading": ("Скачиваю ffmpeg… {pct}%", "Downloading ffmpeg… {pct}%"),
    "ffmpeg_ok": ("ffmpeg скачан, картинку на телефон можно включать", "ffmpeg downloaded, the phone can show the picture now"),
    "ffmpeg_fail": ("ffmpeg не скачался: {err}", "ffmpeg download failed: {err}"),
    "watching": ("смотрит экран", "watching"),
    "devices": ("Подключённые устройства", "Connected devices"),
    "nobody": ("Пока никого. Откройте Rulik на телефоне.", "Nobody yet. Open Rulik on your phone."),
    "gamepad": ("геймпад", "gamepad"),
    "lost": ("нет связи", "no link"),
    "ms": ("мс", "ms"),
    "usb": ("USB (adb)", "USB (adb)"),
    "firewall": ("Брандмауэр", "Firewall"),
    "kick": ("Отключить всех", "Disconnect all"),
    "port_busy": ("Порт {port} занят. Rulik уже запущен?", "Port {port} is busy. Is Rulik already running?"),
    "fw_added": ("Правило брандмауэра добавлено", "Firewall rule added"),
    "listening": ("Rulik слушает порт {port} (UDP и TCP)", "Rulik is listening on port {port} (UDP and TCP)"),
    "joined": ("Подключился: {name}", "Connected: {name}"),
    "left": ("Отключился: {name}", "Disconnected: {name}"),
    "vanished": ("Пропал: {name}", "Lost: {name}"),
    "bad_pin": ("Неверный PIN от {name}", "Wrong PIN from {name}"),
    "pad_fail": ("{name}: геймпад не создан ({err})", "{name}: gamepad not created ({err})"),
    "phone": ("Телефон", "Phone"),
    "no_adb": ("adb не найден. Поставьте Android platform-tools или включите на телефоне USB-модем.",
               "adb not found. Install Android platform-tools or turn on USB tethering on the phone."),
    "adb_ok": ("USB готов: в приложении выберите режим USB", "USB is ready: pick USB mode in the app"),
    "adb_err": ("adb вернул ошибку", "adb returned an error"),
    "addresses": ("Адреса:", "Addresses:"),
    "pin_off": ("выключен", "off"),
}


def t(key: str, **kw) -> str:
    ru, en = _T[key]
    s = ru if LANG == "ru" else en
    return s.format(**kw) if kw else s
