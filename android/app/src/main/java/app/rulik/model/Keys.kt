package app.rulik.model

/** Виртуальные коды клавиш Windows, сгруппированные для выбора в редакторе. */
object Keys {
    data class Group(val title: String, val keys: List<Pair<Int, String>>)

    val groups: List<Group> = listOf(
        Group("A–Z", ('A'..'Z').map { it.code to it.toString() }),
        Group("0–9", ('0'..'9').map { it.code to it.toString() }),
        Group("F1–F12", (1..12).map { 0x6F + it to "F$it" }),
        Group("Mod", listOf(
            0x10 to "Shift", 0x11 to "Ctrl", 0x12 to "Alt", 0x5B to "Win",
            0xA1 to "RShift", 0xA3 to "RCtrl", 0xA5 to "RAlt",
        )),
        Group("Main", listOf(
            0x1B to "Esc", 0x09 to "Tab", 0x20 to "Space", 0x0D to "Enter", 0x08 to "Bksp",
            0x14 to "Caps", 0xC0 to "`", 0xBD to "-", 0xBB to "=", 0xDB to "[", 0xDD to "]",
            0xBA to ";", 0xDE to "'", 0xBC to ",", 0xBE to ".", 0xBF to "/", 0xDC to "\\",
        )),
        Group("Nav", listOf(
            0x26 to "↑", 0x28 to "↓", 0x25 to "←", 0x27 to "→",
            0x2D to "Ins", 0x2E to "Del", 0x24 to "Home", 0x23 to "End", 0x21 to "PgUp", 0x22 to "PgDn",
            0x2C to "PrtSc",
        )),
        Group("Num", (0..9).map { 0x60 + it to "Num$it" } + listOf(
            0x6A to "Num*", 0x6B to "Num+", 0x6D to "Num-", 0x6E to "Num.", 0x6F to "Num/",
        )),
        Group("Media", listOf(
            0xB3 to "⏯", 0xB0 to "⏭", 0xB1 to "⏮", 0xB2 to "⏹",
            0xAF to "Vol+", 0xAE to "Vol-", 0xAD to "Mute",
        )),
    )

    private val names: Map<Int, String> = groups.flatMap { it.keys }.toMap()

    fun name(vk: Int): String = names[vk] ?: "0x%02X".format(vk)

    const val W = 0x57
    const val A = 0x41
    const val S = 0x53
    const val D = 0x44
    const val UP = 0x26
    const val DOWN = 0x28
    const val LEFT = 0x25
    const val RIGHT = 0x27
}
