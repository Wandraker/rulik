---
title: How gamepads work
hint: Why some gamepads just work and others don't
lead: A short primer on real gamepads on PC: Xbox, PlayStation, Switch and the rest.
keywords: gamepad xinput directinput dinput 8bitdo dualshock dualsense switch pro budget drift
---
## XInput and DirectInput

Almost every PC game is built for the Xbox gamepad (the XInput standard), and Windows already has its driver. Other gamepads work out of the box only if they can pretend to be an Xbox pad or the game supports them directly.

Modern games often ignore the old DirectInput standard ("some device with buttons") or mix up its buttons.

| Gamepad | How it works |
|---|---|
| Xbox 360, One, Series | Out of the box, in every game with gamepad support. [More](xbox.html) |
| 8BitDo, budget pads | Out of the box in X-input mode. [More](other.html) |
| DualShock 4, DualSense | Out of the box only in games with PlayStation support. Otherwise through Steam or DS4Windows. [More](playstation.html) |
| Switch Pro | Through Steam. [More](other.html) |
| A phone with Rulik | Out of the box, pretends to be an Xbox 360 pad. [More](gamepad.html) |

## Testing

Press Win+R and type `joy.cpl`. The gamepad should be in the list, and under Properties you can watch the buttons and sticks react. If it shows up there but the game doesn't see it, check the game's control settings and restart it with the gamepad connected.

## Troubleshooting

- **Buttons are mixed up**: the gamepad is in D-input mode. Switch it to X-input or connect it through Steam.
- **The character or camera moves on its own**: stick drift. Raise the dead zone in the game or in Steam.
- **Bluetooth lag**: use a cable or the adapter, and turn off other Bluetooth devices nearby.
- **The game picks the wrong gamepad**: disconnect the extra ones and connect the one you want first, or choose it in the game's settings.
- **The game doesn't support gamepads at all**: see [Games without gamepad support](no-gamepad.html).

## Latency

A wired gamepad adds roughly 1–4 ms, Bluetooth 5–15 ms. Rulik over Wi-Fi or a cable shows 2–10 ms, on par with a regular wireless gamepad.
