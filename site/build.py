"""Сборка сайта: фрагменты из src/<язык>/ оборачиваются в общий шаблон.

python site/build.py  ->  site/dist/ (русский в корне, английский в /en/)
Раскладки для галереи лежат в src/layouts/*.json, это обычный экспорт из приложения.
"""

import hashlib
import html
import re
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src"
DIST = ROOT / "dist"
REPO = "https://github.com/Wandraker/rulik"
APK = f"{REPO}/releases/latest/download/rulik.apk"
EXE = f"{REPO}/releases/latest/download/rulik-server.exe"

PAGES = ["index", "download", "guide", "layouts", "faq"]

T = {
    "ru": {
        "nav": {"index": "Главная", "download": "Скачать", "guide": "Как подключить", "layouts": "Раскладки", "faq": "Вопросы"},
        "titles": {"index": "Rulik: рули своим ПК с телефона", "download": "Скачать Rulik", "guide": "Как подключить Rulik",
                   "layouts": "Раскладки Rulik", "faq": "Вопросы о Rulik"},
        "desc": "Rulik превращает Android-телефон в геймпад, мышь и клавиатуру для ПК. Любая игра, Wi-Fi или кабель, свои раскладки.",
        "other": "EN", "footer": "Открытый проект под лицензией GPL-3.0.",
        "copy": "Скопировать", "copied": "Скопировано", "json": "Файл .json",
        "controls": "элементов",
    },
    "en": {
        "nav": {"index": "Home", "download": "Download", "guide": "How to connect", "layouts": "Layouts", "faq": "FAQ"},
        "titles": {"index": "Rulik: control your PC from your phone", "download": "Download Rulik", "guide": "How to connect Rulik",
                   "layouts": "Rulik layouts", "faq": "Rulik FAQ"},
        "desc": "Rulik turns an Android phone into a gamepad, mouse and keyboard for your PC. Any game, Wi-Fi or cable, your own layouts.",
        "other": "RU", "footer": "Open source under the GPL-3.0 license.",
        "copy": "Copy", "copied": "Copied", "json": ".json file",
        "controls": "controls",
    },
}

LOGO = ('<svg viewBox="0 0 108 108" aria-hidden="true"><circle cx="54" cy="54" r="24" fill="none" stroke="currentColor" stroke-width="7"/>'
        '<path d="M31 58h15M62 58h15M54 65v12" stroke="currentColor" stroke-width="7" stroke-linecap="round"/>'
        '<circle cx="54" cy="58" r="8" fill="var(--accent)"/></svg>')


# Иконки в стиле сайта: тонкие линии, цвет берут из currentColor
_I = ('<svg viewBox="0 0 24 24" width="22" height="22" fill="none" stroke="currentColor" stroke-width="1.8" '
      'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">{}</svg>')
ICONS = {
    "pad": _I.format('<path d="M7.5 7h9a4.5 4.5 0 0 1 4.4 5.5l-1 4.3a2.6 2.6 0 0 1-4.4 1.2L13.8 16h-3.6l-1.7 2a2.6 2.6 0 0 1-4.4-1.2l-1-4.3A4.5 4.5 0 0 1 7.5 7z"/>'
                     '<path d="M8 10.5v3M6.5 12h3"/><circle cx="15.5" cy="11" r=".6" fill="currentColor"/><circle cx="17.3" cy="13" r=".6" fill="currentColor"/>'),
    "link": _I.format('<path d="M2.5 9a14 14 0 0 1 19 0M5.5 12.5a9.5 9.5 0 0 1 13 0M8.6 16a5 5 0 0 1 6.8 0"/><circle cx="12" cy="19.3" r=".9" fill="currentColor"/>'),
    "layout": _I.format('<rect x="3" y="3" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="2"/>'
                        '<rect x="3" y="13.5" width="7.5" height="7.5" rx="2"/><circle cx="17.25" cy="17.25" r="3.75"/>'),
    "gyro": _I.format('<rect x="8.5" y="4" width="7" height="16" rx="1.8"/><path d="M11 17h2"/>'
                      '<path d="M5 8.5a8.5 8.5 0 0 0 0 7M19 15.5a8.5 8.5 0 0 0 0-7"/><path d="M3.3 14.3 5 15.6l1.4-1.6M20.7 9.7 19 8.4l-1.4 1.6"/>'),
    "phone": _I.format('<rect x="6.5" y="2.5" width="11" height="19" rx="2.5"/><path d="M10.5 18.5h3"/>'),
    "pc": _I.format('<rect x="2.5" y="4" width="19" height="12.5" rx="2"/><path d="M8 20.5h8M12 16.5v4"/>'),
}


def layout_cards(lang: str) -> str:
    cards = []
    for f in sorted((SRC / "layouts").glob("*.json")):
        data = json.loads(f.read_text(encoding="utf-8"))
        meta = data.pop("_meta", {})
        name = meta.get(f"name_{lang}", data["name"])
        about = meta.get(f"about_{lang}", "")
        data["name"] = name
        compact = json.dumps(data, ensure_ascii=False, separators=(",", ":"))
        out = DIST / "layouts"
        out.mkdir(parents=True, exist_ok=True)
        (out / f"{f.stem}-{lang}.json").write_text(compact, encoding="utf-8")
        base = "" if lang == "ru" else "../"
        tags = [f'{len(data["controls"])} {T[lang]["controls"]}']
        if data.get("gyro", "OFF") != "OFF":
            tags.append("gyro")
        if data.get("tilt"):
            tags.append("tilt")
        if data.get("portrait"):
            tags.append("portrait")
        cards.append(f"""
<article class="card layout">
  <h3>{html.escape(name)}</h3>
  <p>{html.escape(about)}</p>
  <p class="tags">{" · ".join(tags)}</p>
  <textarea hidden>{html.escape(compact)}</textarea>
  <div class="row">
    <button class="btn small" data-copy data-done="{T[lang]["copied"]}">{T[lang]["copy"]}</button>
    <a class="btn small ghost" href="{base}layouts/{f.stem}-{lang}.json" download>{T[lang]["json"]}</a>
  </div>
</article>""")
    return "\n".join(cards)


def asset_version() -> str:
    """Хеш CSS и JS в ссылке: после обновления браузер не возьмёт старый файл из кеша."""
    h = hashlib.sha256()
    for name in ("style.css", "site.js"):
        h.update((SRC / "assets" / name).read_bytes())
    return h.hexdigest()[:10]


def page(lang: str, slug: str, body: str) -> str:
    t = T[lang]
    prefix = "" if lang == "ru" else "../"
    here = "" if lang == "ru" else "en/"
    other_href = (f"en/{slug}.html" if lang == "ru" else f"../{slug}.html").replace("index.html", "")
    nav = "".join(
        f'<a href="{p}.html"{" aria-current=page" if p == slug else ""}>{t["nav"][p]}</a>'.replace('href="index.html"', 'href="./"')
        for p in PAGES)
    return f"""<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{t["titles"][slug]}</title>
<meta name="description" content="{t["desc"]}">
<meta property="og:title" content="{t["titles"][slug]}">
<meta property="og:description" content="{t["desc"]}">
<link rel="icon" href="{prefix}assets/favicon.svg" type="image/svg+xml">
<link rel="alternate" hreflang="ru" href="https://wandraker.github.io/rulik/{slug if slug != "index" else ""}{".html" if slug != "index" else ""}">
<link rel="alternate" hreflang="en" href="https://wandraker.github.io/rulik/en/{slug if slug != "index" else ""}{".html" if slug != "index" else ""}">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Unbounded:wght@600;800&display=swap" rel="stylesheet">
<link rel="stylesheet" href="{prefix}assets/style.css?v={asset_version()}">
</head>
<body data-apk="{APK}" data-exe="{EXE}" data-lang="{lang}">
<header class="top">
  <a class="brand" href="./">{LOGO}<span>Rulik</span></a>
  <nav>{nav}</nav>
  <a class="lang" href="{other_href or "./"}" hreflang="{"en" if lang == "ru" else "ru"}">{t["other"]}</a>
</header>
<main class="{slug}">
{body}
</main>
<footer>
  <span>Rulik · {t["footer"]}</span>
  <a href="{REPO}">GitHub</a>
</footer>
<script src="{prefix}assets/site.js?v={asset_version()}" defer></script>
</body>
</html>
"""


def main():
    if DIST.exists():
        shutil.rmtree(DIST)
    shutil.copytree(SRC / "assets", DIST / "assets")
    for lang in ("ru", "en"):
        out = DIST if lang == "ru" else DIST / "en"
        out.mkdir(parents=True, exist_ok=True)
        for slug in PAGES:
            body = (SRC / lang / f"{slug}.html").read_text(encoding="utf-8")
            body = (body.replace("{{APK}}", APK).replace("{{EXE}}", EXE).replace("{{REPO}}", REPO)
                    .replace("{{LAYOUTS}}", layout_cards(lang) if slug == "layouts" else ""))
            body = re.sub(r"\{\{icon:(\w+)\}\}", lambda m: ICONS[m.group(1)], body)
            (out / f"{slug}.html").write_text(page(lang, slug, body), encoding="utf-8")
    (DIST / ".nojekyll").write_text("")
    print(f"site -> {DIST}")


if __name__ == "__main__":
    main()
