---
title: For developers
hint: Source code, building and the network protocol
lead: Rulik is fully open under GPL-3.0. Here's how it's built and how to build it.
keywords: source code github build gradle python pyinstaller protocol udp tcp port 47800 development
---
## What's inside

| Folder | Contents |
|---|---|
| `android/` | The app: Kotlin, Jetpack Compose, Android 10+ |
| `server/` | Rulik Server for Windows: Python, SendInput, ViGEmBus via vgamepad, a tkinter window |
| `site/` | This website: static pages in two languages, wiki articles in Markdown |
| `docs/PROTOCOL.md` | The network protocol |

Repository: [github.com/Wandraker/rulik](https://github.com/Wandraker/rulik).

## Building

**App** (JDK 17+, Android SDK): run `./gradlew assembleRelease` in the `android` folder. UI strings live in `android/tools/strings.py`; run that file after editing it.

**Server** (Python 3.12+, Windows): tests with `python -m unittest discover -s tests`, the exe is built by `build.ps1`. To run from source: `python run.py`. The `--dry` flag prints input to the console instead of sending it to Windows, and `--nogui` runs the server without a window.

**Website**: `python site/build.py`, the pages end up in `site/dist`.

## Protocol

One port, `47800`: UDP over Wi-Fi and TCP over a cable through `adb reverse`. Every packet starts with `RK`, the protocol version and a type. The phone sends the full gamepad state, mouse movement, key presses and text, and the PC answers with a welcome and rumble.

Full description: [docs/PROTOCOL.md](https://github.com/Wandraker/rulik/blob/main/docs/PROTOCOL.md). It's enough to write your own client, for an iPhone for example.

## Helping out

- Found a bug: [GitHub issue](https://github.com/Wandraker/rulik/issues).
- Want to fix a wiki article: every article has an "Edit on GitHub" link at the bottom.
- Made a layout: post it and it may make it into the [catalog](../layouts.html).
