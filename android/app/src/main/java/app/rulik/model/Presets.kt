package app.rulik.model

import app.rulik.model.Kind.KEY
import app.rulik.model.Kind.MOUSE
import app.rulik.model.Kind.PAD

/** Готовые раскладки, которые появляются при первом запуске. */
object Presets {
    private fun btn(x: Float, y: Float, w: Float, label: String, action: Action, h: Float = w, shape: Shape = Shape.CIRCLE, mode: BMode = BMode.HOLD) =
        Control(type = CType.BUTTON, x = x, y = y, w = w, h = h, label = label, action = action, shape = shape, mode = mode)

    private fun pad(bit: Int) = Action(PAD, bit)
    private fun key(vararg vk: Int) = Action(KEY, keys = vk.toList())
    private fun mouse(b: Int) = Action(MOUSE, b)

    private fun xboxControls() = listOf(
        Control(type = CType.STICK, x = .15f, y = .68f, w = .42f, out = Out.LEFT_STICK),
        Control(type = CType.DPAD, x = .34f, y = .82f, w = .30f, out = Out.DPAD),
        Control(type = CType.STICK, x = .66f, y = .80f, w = .34f, out = Out.RIGHT_STICK),
        btn(.86f, .70f, .16f, "A", pad(Pad.A)),
        btn(.925f, .55f, .16f, "B", pad(Pad.B)),
        btn(.795f, .55f, .16f, "X", pad(Pad.X)),
        btn(.86f, .40f, .16f, "Y", pad(Pad.Y)),
        btn(.12f, .10f, .30f, "LT", Action(Kind.LT), h = .13f, shape = Shape.ROUNDED),
        btn(.12f, .26f, .30f, "LB", pad(Pad.LB), h = .13f, shape = Shape.ROUNDED),
        btn(.88f, .10f, .30f, "RT", Action(Kind.RT), h = .13f, shape = Shape.ROUNDED),
        btn(.88f, .26f, .30f, "RB", pad(Pad.RB), h = .13f, shape = Shape.ROUNDED),
        btn(.40f, .22f, .12f, "⧉", pad(Pad.BACK)),
        btn(.50f, .20f, .14f, "Ⓧ", pad(Pad.GUIDE)),
        btn(.60f, .22f, .12f, "☰", pad(Pad.START)),
        btn(.30f, .48f, .12f, "L3", pad(Pad.LS)),
        btn(.56f, .56f, .12f, "R3", pad(Pad.RS)),
    )

    fun all(ru: Boolean): List<Layout> = listOf(
        Layout(name = if (ru) "Xbox геймпад" else "Xbox gamepad", controls = xboxControls()),
        Layout(
            name = if (ru) "Геймпад + гироприцел" else "Gamepad + gyro aim",
            controls = xboxControls(), gyro = GyroMode.RIGHT_STICK,
        ),
        Layout(
            name = if (ru) "Мышь и клавиатура" else "Mouse & keyboard",
            controls = listOf(
                Control(type = CType.TOUCHPAD, x = .5f, y = .42f, w = 1.6f, h = .72f, out = Out.MOUSE, shape = Shape.ROUNDED),
                btn(.33f, .9f, .75f, "LMB", mouse(1), h = .16f, shape = Shape.ROUNDED),
                btn(.67f, .9f, .75f, "RMB", mouse(2), h = .16f, shape = Shape.ROUNDED),
                btn(.94f, .14f, .14f, "↥", Action(Kind.WHEEL_UP)),
                btn(.94f, .34f, .14f, "↧", Action(Kind.WHEEL_DOWN)),
                btn(.94f, .54f, .14f, "⌨", Action(Kind.TEXT)),
                btn(.94f, .74f, .14f, "Esc", key(0x1B)),
                btn(.06f, .14f, .14f, "Win", key(0x5B)),
                btn(.06f, .34f, .14f, "Alt⇥", key(0x12, 0x09)),
                btn(.06f, .54f, .14f, "⏎", key(0x0D)),
                btn(.06f, .74f, .14f, "⌫", key(0x08)),
            ),
        ),
        Layout(
            name = if (ru) "Шутер: WASD + мышь" else "Shooter: WASD + mouse",
            controls = listOf(
                Control(type = CType.TOUCHPAD, x = .62f, y = .52f, w = .95f, h = .8f, out = Out.MOUSE, shape = Shape.ROUNDED, sens = 1.4f),
                Control(type = CType.STICK, x = .15f, y = .66f, w = .42f, out = Out.WASD, floating = true),
                btn(.88f, .72f, .20f, "🔥", mouse(1)),
                btn(.88f, .45f, .15f, "◎", mouse(2)),
                btn(.76f, .88f, .14f, "␣", key(0x20)),
                btn(.62f, .88f, .13f, "Ctrl", key(0x11)),
                btn(.94f, .22f, .12f, "R", key(0x52)),
                btn(.30f, .46f, .12f, "Shift", key(0x10)),
                btn(.30f, .24f, .12f, "E", key(0x45)),
                btn(.42f, .22f, .10f, "1", key(0x31)),
                btn(.50f, .22f, .10f, "2", key(0x32)),
                btn(.06f, .08f, .10f, "Tab", key(0x09)),
                btn(.94f, .06f, .10f, "Esc", key(0x1B)),
            ),
        ),
        Layout(
            name = if (ru) "Гонки: руль наклоном" else "Racing: tilt steering",
            tilt = true,
            controls = listOf(
                btn(.86f, .66f, .34f, if (ru) "Газ" else "Gas", Action(Kind.RT), h = .50f, shape = Shape.ROUNDED),
                btn(.14f, .66f, .34f, if (ru) "Тормоз" else "Brake", Action(Kind.LT), h = .50f, shape = Shape.ROUNDED),
                btn(.66f, .72f, .16f, "A", pad(Pad.A)),
                btn(.66f, .44f, .16f, "B", pad(Pad.B)),
                btn(.34f, .72f, .16f, "X", pad(Pad.X)),
                btn(.34f, .44f, .16f, "Y", pad(Pad.Y)),
                btn(.14f, .16f, .30f, "LB", pad(Pad.LB), h = .14f, shape = Shape.ROUNDED),
                btn(.86f, .16f, .30f, "RB", pad(Pad.RB), h = .14f, shape = Shape.ROUNDED),
                btn(.44f, .24f, .12f, "⧉", pad(Pad.BACK)),
                btn(.56f, .24f, .12f, "☰", pad(Pad.START)),
            ),
        ),
        Layout(
            name = if (ru) "Медиа-пульт" else "Media remote",
            portrait = true,
            controls = listOf(
                Control(type = CType.TOUCHPAD, x = .5f, y = .24f, w = .9f, h = .8f, out = Out.MOUSE, shape = Shape.ROUNDED),
                btn(.27f, .48f, .42f, "LMB", mouse(1), h = .14f, shape = Shape.ROUNDED),
                btn(.73f, .48f, .42f, "RMB", mouse(2), h = .14f, shape = Shape.ROUNDED),
                btn(.2f, .60f, .2f, "⏮", key(0xB1)),
                btn(.5f, .60f, .26f, "⏯", key(0xB3)),
                btn(.8f, .60f, .2f, "⏭", key(0xB0)),
                btn(.2f, .71f, .2f, "🔉", key(0xAE)),
                btn(.5f, .71f, .2f, "🔇", key(0xAD)),
                btn(.8f, .71f, .2f, "🔊", key(0xAF)),
                btn(.2f, .82f, .2f, "⏪", key(0x25)),
                btn(.5f, .82f, .2f, "␣", key(0x20)),
                btn(.8f, .82f, .2f, "⏩", key(0x27)),
                btn(.2f, .93f, .2f, "Esc", key(0x1B)),
                btn(.5f, .93f, .2f, "⌨", Action(Kind.TEXT)),
                btn(.8f, .93f, .2f, "F", key(0x46)),
            ),
        ),
    )

    fun blank(ru: Boolean) = Layout(name = if (ru) "Новая раскладка" else "New layout")
}
