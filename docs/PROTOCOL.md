# Rulik protocol, version 1

Phone ↔ PC. One port, `47800` by default, for both UDP (Wi-Fi) and TCP (USB through `adb reverse`).

All numbers are little-endian. Every packet starts with a 4-byte header:

| byte | value |
|------|-------|
| 0–1  | `R` `K` |
| 2    | protocol version, `1` |
| 3    | packet type |

Over TCP every packet is prefixed with its length as `u16`. Over UDP one datagram is one packet.

A "short string" is `u8 length` followed by that many UTF-8 bytes.

## Phone → PC

| type | name | body |
|------|------|------|
| `0x01` | HELLO | short string PIN, short string device name |
| `0x02` | PAD | `u16 buttons`, `u8 LT`, `u8 RT`, `i16 LX`, `i16 LY`, `i16 RX`, `i16 RY` |
| `0x03` | MOUSE_MOVE | `i16 dx`, `i16 dy` (relative, pixels) |
| `0x04` | MOUSE_BTN | `u8 button` (1 left, 2 right, 3 middle, 4 and 5 side), `u8 down` |
| `0x05` | SCROLL | `i16 vertical`, `i16 horizontal` (120 = one notch, positive = up/right) |
| `0x06` | KEY | `u16 vk` (Windows virtual-key code), `u8 down` |
| `0x07` | TEXT | UTF-8 text until the end of the packet |
| `0x08` | PING | `u32 timestamp`, `u32 last measured RTT in ms` (optional) |
| `0x09` | BYE | none |
| `0x0B` | HELD | `u8 mouse mask` (bit 0 = button 1), `u8 count`, `count × u16 vk` |
| `0x0C` | STREAM_START | `u16 max width`, `u16 max height` (0×0 = the monitor's size), `u8 fps`, `u32 bitrate in kbit/s`, `u8 monitor`, `u8 flags` (bit 0: sound) |
| `0x0D` | STREAM_STOP | none |
| `0x0E` | MOUSE_ABS | `u16 x`, `u16 y`: a point on the streamed picture, 0..65535 |
| `0x10` | DISCOVER | none, sent as a broadcast |

`PAD` is the full XInput state, the same layout as `XUSB_REPORT`. Button bits: up `0x0001`, down `0x0002`, left `0x0004`, right `0x0008`, start `0x0010`, back `0x0020`, L3 `0x0040`, R3 `0x0080`, LB `0x0100`, RB `0x0200`, guide `0x0400`, A `0x1000`, B `0x2000`, X `0x4000`, Y `0x8000`. Stick Y is positive up.

## PC → phone

| type | name | body |
|------|------|------|
| `0x81` | WELCOME | `u8 status`, `u8 capabilities`, short string PC name |
| `0x83` | RUMBLE | `u8 large motor`, `u8 small motor` |
| `0x84` | VIDEO | `u32 frame`, `u16 piece`, `u16 pieces`, `u8 flags` (bit 0: keyframe), `u32 PC time in ms`, then up to 1180 bytes of the frame |
| `0x85` | STREAM_INFO | `u8 status`, `u16 width`, `u16 height`, `u8 fps`, short string encoder |
| `0x86` | AUDIO | `u16 sequence`, `u32 sample rate`, then float32 stereo samples |
| `0x88` | PONG | the `u32 timestamp` from PING |
| `0x90` | DISCOVER_REPLY | `u16 port`, `u8 flags` (bit 0: PIN required), short string PC name |

WELCOME status: `0` ok, `1` wrong PIN, `2` too many devices, `3` the PC doesn't know this device (it was restarted) and the phone should send HELLO again.

Capabilities: bit 0 means the virtual gamepad driver is available, bit 1 means the server can stream the screen.

STREAM_INFO status: `0` streaming, `1` ffmpeg is missing on the PC, `2` the stream failed to start, `3` stopped.

## Reliability over UDP

- Gamepad state is sent whole, so a lost PAD packet is fixed by the next one.
- Keys and mouse buttons are events. To survive a lost key-up, the phone sends PING, HELD and the current PAD every 250 ms. The server presses or releases whatever differs from HELD.
- If the server hears nothing from a device for 1.5 s, it releases everything that device holds. After 20 s it forgets the device and removes its gamepad.
- Packets from a device that hasn't passed HELLO are ignored, except DISCOVER, HELLO and PING (which gets WELCOME status 3).

## Streaming

The phone sends STREAM_START and repeats it every second while it watches, so a restarted server picks the stream up again. One stream is shared by everyone watching; a new request with other settings restarts it for all.

Video is H.264 in Annex B form (start codes before every NAL unit). Every keyframe carries SPS and PPS, and there is a keyframe every second. A frame is cut into VIDEO packets of up to 1180 bytes. If a piece is lost, the phone drops that frame and waits for the next keyframe.

Over TCP the server keeps at most three frames queued per phone and skips frames until the next keyframe when the queue is full, so video never holds up buttons and pings.

Sound is what Windows plays (WASAPI loopback), sent uncompressed as float32 stereo, about 3 Mbit/s at 48 kHz.
