---
title: FAQ
hint: Short answers to the most common questions
lead: Didn't find an answer? Ask in a GitHub issue.
keywords: questions faq games price free iphone ios mac linux internet driver vigembus
---
## Which games does it work with?

Any game you can play with an Xbox gamepad or with mouse and keyboard. Rulik creates a real virtual controller in the system, so it doesn't matter where the game came from: Steam, Epic, GOG, the Xbox app, a disc or anywhere else. Games without gamepad support work through [keyboard layouts](no-gamepad.html).

## Why the ViGEmBus driver?

Without a driver, Windows doesn't let a program create a gamepad. ViGEmBus is used by DS4Windows, Steam Input and many emulators. It ships inside Rulik Server and installs with one button. Mouse and keyboard work without it.

## What's the latency?

On home Wi-Fi it's usually 2–10 ms one way, even less over USB tethering. Packets are tiny and go over UDP without extra acknowledgements. More in [If it lags](lag.html).

## Does it need the internet?

No. The phone and the PC talk directly over your network or a cable. You only need the internet to download the app.

## Can two people play?

Yes, up to 8 phones per PC, each with its own gamepad. See [Several phones](multiplayer.html).

## Will anti-cheat ban me?

In most games, no. More in [Anti-cheat](anticheat.html).

## What about iPhone, macOS, Linux?

Only Android and Windows for now. The protocol is open and documented in the repository, so ports to other systems are possible. See [For developers](dev.html).

## How much does it cost?

Free, no ads, no sign-up. The app's code is open under GPL-3.0: you can study and improve it, but any version based on it must stay open too.
