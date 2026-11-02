"""Сборка сайта: фрагменты из src/<язык>/ оборачиваются в общий шаблон.

python site/build.py  ->  site/dist/ (русский в корне, английский в /en/)
Раскладки для галереи лежат в src/layouts/*.json, это обычный экспорт из приложения.
Статьи вики лежат в src/wiki/<язык>/*.md, порядок и группы задаёт WIKI ниже.
"""

import hashlib
import html
import re
import json
import shutil
import struct
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "src"
DIST = ROOT / "dist"
REPO = "https://github.com/Wandraker/rulik"
APK = f"{REPO}/releases/latest/download/rulik.apk"
EXE = f"{REPO}/releases/latest/download/rulik-server.exe"

SITE = "https://wandraker.github.io/rulik/"

# обычные страницы из src/<язык>/<имя>.html
PAGES = ["index", "download", "layouts"]
# верхнее меню: ключ и путь от корня языка
NAV = [("index", ""), ("download", "download.html"), ("wiki", "wiki/"), ("layouts", "layouts.html")]

# Вики: группы в боковом меню, статьи лежат в src/wiki/<язык>/<имя>.md.
# "@layouts" — ссылка на каталог раскладок, а не статья.
WIKI = [
    ("start", "flag", ["index", "install", "wifi", "usb-tether", "adb"]),
    ("app", "phone", ["presets", "editor", "accuracy", "mouse", "gyro", "multiplayer", "security"]),
    ("games", "play", ["gamepad", "no-gamepad", "@layouts"]),
    ("gamepads", "pad", ["gamepads", "xbox", "playstation", "other"]),
    ("problems", "wrench", ["not-found", "lag", "anticheat"]),
    ("more", "help", ["faq", "dev"]),
]

# старые адреса: страница -> (статья по умолчанию, якорь -> статья)
REDIRECTS = {
    "guide": ("install", {"wifi": "wifi", "tether": "usb-tether", "adb": "adb", "editor": "editor", "gyro": "gyro",
                          "lag": "lag", "gamepad": "gamepad", "nopad": "no-gamepad"}),
    "gamepads": ("gamepads", {"xbox": "xbox", "ps": "playstation", "other": "other", "nopad": "no-gamepad"}),
    "faq": ("faq", {}),
}

T = {
    "ru": {
        "nav": {"index": "Главная", "download": "Скачать", "wiki": "Вики", "layouts": "Раскладки"},
        "titles": {"index": "Rulik: рули своим ПК с телефона", "download": "Скачать Rulik", "layouts": "Раскладки Rulik"},
        "desc": "Rulik превращает Android-телефон в геймпад, мышь и клавиатуру для ПК. Любая игра, Wi-Fi или кабель, свои раскладки.",
        "other": "EN", "footer": "© 2026 Wandraker. Все права на сайт защищены. Код приложения открыт под GPL-3.0.",
        "copy": "Скопировать", "copied": "Скопировано", "json": "Файл .json",
        "controls": "элементов",
        "wiki_title": "Вики Rulik", "sections": "Разделы вики", "search": "Поиск", "search_label": "Поиск по вики",
        "nothing": "Ничего не нашлось", "on_page": "На этой странице", "edit": "Исправить на GitHub",
        "prev": "Назад", "next": "Далее",
        "groups": {"start": "Начало", "app": "Приложение", "games": "Игры", "gamepads": "Геймпады",
                   "problems": "Проблемы", "more": "Ещё"},
        "layouts_link": ("Каталог раскладок", "Готовые раскладки для игр и программ"),
    },
    "en": {
        "nav": {"index": "Home", "download": "Download", "wiki": "Wiki", "layouts": "Layouts"},
        "titles": {"index": "Rulik: control your PC from your phone", "download": "Download Rulik", "layouts": "Rulik layouts"},
        "desc": "Rulik turns an Android phone into a gamepad, mouse and keyboard for your PC. Any game, Wi-Fi or cable, your own layouts.",
        "other": "RU", "footer": "© 2026 Wandraker. All rights reserved for the website. The app's code is open under GPL-3.0.",
        "copy": "Copy", "copied": "Copied", "json": ".json file",
        "controls": "controls",
        "wiki_title": "Rulik wiki", "sections": "Wiki sections", "search": "Search", "search_label": "Search the wiki",
        "nothing": "Nothing found", "on_page": "On this page", "edit": "Edit on GitHub",
        "prev": "Previous", "next": "Next",
        "groups": {"start": "Getting started", "app": "The app", "games": "Games", "gamepads": "Gamepads",
                   "problems": "Problems", "more": "More"},
        "layouts_link": ("Layout catalog", "Ready-made layouts for games and apps"),
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
    "keys": _I.format('<rect x="2.5" y="6" width="19" height="12" rx="2"/>'
                      '<path d="M6 9.5h.01M9.3 9.5h.01M12.6 9.5h.01M15.9 9.5h.01M18 9.5h.01M6 12.3h.01M18 12.3h.01M8.5 15h7"/>'),
    "pc": _I.format('<rect x="2.5" y="4" width="19" height="12.5" rx="2"/><path d="M8 20.5h8M12 16.5v4"/>'),
    "flag": _I.format('<path d="M5 21V4M5 4h11l-2 4 2 4H5"/>'),
    "play": _I.format('<circle cx="12" cy="12" r="9"/><path d="M10 8.5v7l5.5-3.5z"/>'),
    "wrench": _I.format('<path d="M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.8-3.8a6 6 0 0 1-7.9 7.9l-6.9 6.9a2.1 2.1 0 0 1-3-3l6.9-6.9a6 6 0 0 1 7.9-7.9z"/>'),
    "help": _I.format('<circle cx="12" cy="12" r="9"/><path d="M9.5 9.5a2.5 2.5 0 1 1 3.5 2.3c-.6.3-1 .9-1 1.6v.6"/><circle cx="12" cy="17" r=".6" fill="currentColor"/>'),
    "search": _I.format('<circle cx="11" cy="11" r="6.5"/><path d="m16 16 4.5 4.5"/>'),
    "arrow": _I.format('<path d="M5 12h14M13 6l6 6-6 6"/>'),
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


def inline(s: str) -> str:
    """Строчная разметка: `код`, **жирный**, [ссылка](адрес)."""
    s = html.escape(s, quote=False)
    s = re.sub(r"`([^`]+)`", r"<code>\1</code>", s)
    s = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", s)
    return re.sub(r"\[([^\]]+)\]\(([^)\s]+)\)", r'<a href="\2">\1</a>', s)


def markdown(text: str) -> str:
    """Маленький Markdown для статей вики: заголовки, абзацы, списки, таблицы и заметки (>)."""
    out = []
    for block in re.split(r"\n\s*\n", text.strip()):
        lines = block.strip().split("\n")
        first = lines[0]
        if first.startswith("!["):
            # картинка, подпись и легенда: строки «N текст» объясняют номера на скриншоте
            m = re.match(r"!\[([^\]]*)\]\(([^)\s]+)\)", first)
            caption, src = m.group(1), m.group(2)
            legend = "".join(f'<li><span class="n">{n}</span>{inline(t)}</li>'
                             for n, t in (re.match(r"(\d+)\s+(.*)", l.strip()).groups() for l in lines[1:]))
            out.append(f'<figure><a href="{src}"><img src="{src}" alt="{html.escape(caption)}" loading="lazy"></a>'
                       f'<figcaption>{inline(caption)}{f"<ol class=legend>{legend}</ol>" if legend else ""}</figcaption></figure>')
        elif first.startswith("#"):
            assert len(lines) == 1, f"после заголовка нужна пустая строка: {first}"
            level = len(first) - len(first.lstrip("#"))
            out.append(f"<h{level}>{inline(first[level:].strip())}</h{level}>")
        elif first.startswith(">"):
            out.append(f'<div class="note">{inline(" ".join(l.lstrip("> ").strip() for l in lines))}</div>')
        elif first.startswith("|"):
            rows = [[c.strip() for c in l.strip().strip("|").split("|")] for l in lines if not re.match(r"^\|[\s|:-]+\|$", l.strip())]
            head = "".join(f"<th>{inline(c)}</th>" for c in rows[0])
            body = "".join("<tr>" + "".join(f"<td>{inline(c)}</td>" for c in r) + "</tr>" for r in rows[1:])
            out.append(f'<div class="table"><table><thead><tr>{head}</tr></thead><tbody>{body}</tbody></table></div>')
        elif first.startswith("- ") or re.match(r"\d+\. ", first):
            tag = "ul" if first.startswith("- ") else "ol"
            items = []
            for l in lines:
                m = re.match(r"(?:- |\d+\. )(.*)", l)
                if m:
                    items.append(m.group(1))
                else:
                    items[-1] += " " + l.strip()
            out.append(f"<{tag}>" + "".join(f"<li>{inline(i)}</li>" for i in items) + f"</{tag}>")
        else:
            out.append(f"<p>{inline(' '.join(l.strip() for l in lines))}</p>")
    return "\n".join(out)


def jpeg_size(path: Path) -> tuple[int, int]:
    data = path.read_bytes()
    i = 2
    while i < len(data):
        marker = data[i + 1]
        if marker in (0xC0, 0xC1, 0xC2):
            h, w = struct.unpack(">HH", data[i + 5:i + 9])
            return w, h
        i += 2 + struct.unpack(">H", data[i + 2:i + 4])[0]
    raise ValueError(f"не JPEG: {path}")


def read_article(lang: str, slug: str) -> dict:
    raw = (SRC / "wiki" / lang / f"{slug}.md").read_text(encoding="utf-8")
    _, head, body = raw.split("---", 2)
    meta = dict(line.split(":", 1) for line in head.strip().split("\n"))
    meta = {k.strip(): v.strip() for k, v in meta.items()}
    # размеры картинок сразу в разметку, чтобы страница не прыгала при загрузке
    def sized(m):
        w, h = jpeg_size(SRC / "wiki" / lang / m.group(1))
        return f'<img src="{m.group(1)}" width="{w}" height="{h}"' + (' class="tall"' if h > w else "")
    meta["html"] = re.sub(r'<img src="([^"]+)"', sized, markdown(body))
    meta["slug"] = slug
    return meta


def wiki_sidebar(lang: str, articles: dict, current: str) -> str:
    t = T[lang]
    groups = []
    for key, icon, slugs in WIKI:
        links = []
        for slug in slugs:
            if slug == "@layouts":
                label, hint = t["layouts_link"]
                links.append(f'<a href="../layouts.html" data-wiki-link data-hint="{html.escape(hint)}">{label}</a>')
                continue
            a = articles[slug]
            href = "./" if slug == "index" else f"{slug}.html"
            cur = " aria-current=page" if slug == current else ""
            hint = html.escape(f'{a["hint"]} {a.get("keywords", "")}')
            links.append(f'<a href="{href}"{cur} data-wiki-link data-slug="{slug}" data-hint="{hint}">{html.escape(a["title"])}</a>')
        groups.append(f'<div class="wiki-group" data-wiki-group><p class="wiki-group-title">{ICONS[icon]}{t["groups"][key]}</p>'
                      + "".join(links) + "</div>")
    return f"""<aside class="wiki-side">
  <details class="wiki-side-toggle" open>
    <summary>{t["sections"]}</summary>
    <label class="wiki-search">{ICONS["search"]}<input type="search" placeholder="{t["search"]}" aria-label="{t["search_label"]}" data-wiki-search><kbd>Ctrl K</kbd></label>
    <nav aria-label="{t["sections"]}">{"".join(groups)}<p class="wiki-empty" data-wiki-empty hidden>{t["nothing"]}</p></nav>
  </details>
</aside>"""


def wiki_index_cards(lang: str, articles: dict) -> str:
    t = T[lang]
    out = []
    for key, icon, slugs in WIKI:
        cards = []
        for slug in slugs:
            if slug == "index":
                continue
            if slug == "@layouts":
                (label, hint), href = t["layouts_link"], "../layouts.html"
            else:
                label, hint, href = articles[slug]["title"], articles[slug]["hint"], f"{slug}.html"
            cards.append(f'<a class="wiki-card" href="{href}"><span class="wiki-card-title">{html.escape(label)}</span>'
                         f'<span class="wiki-card-hint">{html.escape(hint)}</span>{ICONS["arrow"]}</a>')
        out.append(f'<section class="wiki-section"><h2 class="with-ico"><span class="ico sm">{ICONS[icon]}</span>{t["groups"][key]}</h2>'
                   f'<div class="wiki-cards">{"".join(cards)}</div></section>')
    return "\n".join(out)


def wiki_page(lang: str, articles: dict, slug: str) -> str:
    t = T[lang]
    a = articles[slug]
    order = [s for _, _, slugs in WIKI for s in slugs if not s.startswith("@")]
    group = next(key for key, _, slugs in WIKI if slug in slugs)
    i = order.index(slug)
    pager = ""
    if i > 0:
        p = articles[order[i - 1]]
        href = "./" if p["slug"] == "index" else f'{p["slug"]}.html'
        pager += f'<a class="prev" href="{href}"><small>{t["prev"]}</small>{html.escape(p["title"])}</a>'
    if i + 1 < len(order):
        n = articles[order[i + 1]]
        pager += f'<a class="next" href="{n["slug"]}.html"><small>{t["next"]}</small>{html.escape(n["title"])}</a>'
    body = wiki_index_cards(lang, articles) if slug == "index" else a["html"]
    eyebrow = "" if slug == "index" else f'<p class="eyebrow">{t["groups"][group]}</p>'
    return f"""<div class="wiki-grid">
{wiki_sidebar(lang, articles, slug)}
<article class="wiki-content">
  <header class="wiki-head">{eyebrow}<h1>{html.escape(a["title"])}</h1><p class="lead">{inline(a["lead"])}</p></header>
  {body}
  <nav class="wiki-pager">{pager}</nav>
  <p class="wiki-edit"><a href="{REPO}/edit/main/site/src/wiki/{lang}/{slug}.md">{t["edit"]}</a></p>
</article>
<aside class="wiki-toc" aria-label="{t["on_page"]}"><p class="wiki-toc-title">{t["on_page"]}</p><nav data-wiki-toc></nav></aside>
</div>"""


def page(lang: str, path: str, body: str, title: str, desc: str, current: str) -> str:
    """path — адрес страницы от корня языка: "", "download.html", "wiki/", "wiki/xbox.html"."""
    t = T[lang]
    up = "../" * path.count("/")
    assets = up + ("" if lang == "ru" else "../")
    other_href = (up + "en/" + path) if lang == "ru" else ("../" * (path.count("/") + 1) + path)
    nav = "".join(
        f'<a href="{(up + href) or "./"}"{" aria-current=page" if key == current else ""}>{t["nav"][key]}</a>'
        for key, href in NAV)
    return f"""<!doctype html>
<html lang="{lang}">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>{html.escape(title)}</title>
<meta name="description" content="{html.escape(desc)}">
<meta property="og:title" content="{html.escape(title)}">
<meta property="og:description" content="{html.escape(desc)}">
<link rel="icon" href="{assets}assets/favicon.svg" type="image/svg+xml">
<link rel="alternate" hreflang="ru" href="{SITE}{path}">
<link rel="alternate" hreflang="en" href="{SITE}en/{path}">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Inter:wght@400;500;600&family=Unbounded:wght@600;800&display=swap" rel="stylesheet">
<link rel="stylesheet" href="{assets}assets/style.css?v={asset_version()}">
</head>
<body data-apk="{APK}" data-exe="{EXE}" data-lang="{lang}">
<header class="top">
  <a class="brand" href="{up or "./"}">{LOGO}<span>Rulik</span></a>
  <nav>{nav}</nav>
  <a class="lang" href="{other_href}" hreflang="{"en" if lang == "ru" else "ru"}">{t["other"]}</a>
</header>
<main class="{current}">
{body}
</main>
<footer>
  <span>Rulik · {t["footer"]}</span>
  <a href="{REPO}">GitHub</a>
</footer>
<script src="{assets}assets/site.js?v={asset_version()}" defer></script>
</body>
</html>
"""


def redirect(default: str, anchors: dict) -> str:
    """Старый адрес ведёт на статью вики, якорь выбирает нужную статью."""
    return f"""<!doctype html>
<meta charset="utf-8">
<title>Rulik</title>
<script>location.replace("wiki/" + ({json.dumps(anchors)}[location.hash.slice(1)] || "{default}") + ".html")</script>
<meta http-equiv="refresh" content="0; url=wiki/{default}.html">
<link rel="canonical" href="wiki/{default}.html">
<a href="wiki/{default}.html">wiki/{default}.html</a>
"""


def main():
    if DIST.exists():
        shutil.rmtree(DIST)
    shutil.copytree(SRC / "assets", DIST / "assets")
    for lang in ("ru", "en"):
        t = T[lang]
        out = DIST if lang == "ru" else DIST / "en"
        (out / "wiki").mkdir(parents=True, exist_ok=True)
        if (SRC / "wiki" / lang / "img").exists():
            shutil.copytree(SRC / "wiki" / lang / "img", out / "wiki" / "img")
        for slug in PAGES:
            body = (SRC / lang / f"{slug}.html").read_text(encoding="utf-8")
            body = (body.replace("{{APK}}", APK).replace("{{EXE}}", EXE).replace("{{REPO}}", REPO)
                    .replace("{{LAYOUTS}}", layout_cards(lang) if slug == "layouts" else ""))
            body = re.sub(r"\{\{icon:(\w+)\}\}", lambda m: ICONS[m.group(1)], body)
            path = "" if slug == "index" else f"{slug}.html"
            (out / f"{slug}.html").write_text(page(lang, path, body, t["titles"][slug], t["desc"], slug), encoding="utf-8")

        slugs = [s for _, _, group in WIKI for s in group if not s.startswith("@")]
        articles = {s: read_article(lang, s) for s in slugs}
        search = {}
        for slug, a in articles.items():
            path = "wiki/" if slug == "index" else f"wiki/{slug}.html"
            title = a["title"] if slug == "index" else f'{a["title"]} · {t["wiki_title"]}'
            name = "index.html" if slug == "index" else f"{slug}.html"
            (out / "wiki" / name).write_text(page(lang, path, wiki_page(lang, articles, slug), title, a["hint"], "wiki"), encoding="utf-8")
            text = re.sub(r"<[^>]+>", " ", a["html"])
            search[slug] = re.sub(r"\s+", " ", html.unescape(f'{a["title"]} {a["hint"]} {a.get("keywords", "")} {text}')).lower()
        (out / "wiki" / "search.json").write_text(json.dumps(search, ensure_ascii=False), encoding="utf-8")
        for old, (default, anchors) in REDIRECTS.items():
            (out / f"{old}.html").write_text(redirect(default, anchors), encoding="utf-8")
    (DIST / ".nojekyll").write_text("")
    print(f"site -> {DIST}")


if __name__ == "__main__":
    main()
