---
title: Для разработчиков
hint: Исходный код, сборка и сетевой протокол
lead: Rulik полностью открыт под GPL-3.0. Вот как он устроен и как его собрать.
keywords: исходный код github сборка gradle python pyinstaller протокол udp tcp порт 47800 разработка
---
## Из чего состоит

| Папка | Что внутри |
|---|---|
| `android/` | Приложение: Kotlin, Jetpack Compose, Android 10+ |
| `server/` | Rulik Server для Windows: Python, SendInput, ViGEmBus через vgamepad, окно на tkinter |
| `site/` | Этот сайт: статичные страницы на двух языках, статьи вики в Markdown |
| `docs/PROTOCOL.md` | Описание сетевого протокола |

Репозиторий: [github.com/Wandraker/rulik](https://github.com/Wandraker/rulik).

## Сборка

**Приложение** (JDK 17+, Android SDK): в папке `android` выполните `./gradlew assembleRelease`. Строки интерфейса правятся в `android/tools/strings.py`, после правки запустите этот файл.

**Сервер** (Python 3.12+, Windows): тесты `python -m unittest discover -s tests`, exe собирается `build.ps1`. Запуск из исходников: `python run.py`. Флаг `--dry` печатает ввод в консоль вместо отправки в Windows, `--nogui` запускает сервер без окна.

**Сайт**: `python site/build.py`, готовые страницы появятся в `site/dist`.

## Протокол

Один порт `47800`: UDP по Wi-Fi и TCP по кабелю через `adb reverse`. Каждый пакет начинается с `RK`, версии протокола и типа. Телефон шлёт полное состояние геймпада, движения мыши, нажатия клавиш и текст, а ПК отвечает приветствием и вибрацией.

Полное описание: [docs/PROTOCOL.md](https://github.com/Wandraker/rulik/blob/main/docs/PROTOCOL.md). Его хватит, чтобы написать своего клиента, например для iPhone.

## Помочь проекту

- Нашли ошибку: [issue на GitHub](https://github.com/Wandraker/rulik/issues).
- Хотите поправить статью вики: внизу каждой статьи есть ссылка «Исправить на GitHub».
- Сделали раскладку: выложите её, и она может попасть в [каталог](../layouts.html).
