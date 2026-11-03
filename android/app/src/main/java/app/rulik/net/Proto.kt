package app.rulik.net

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Протокол Rulik, описание в docs/PROTOCOL.md. Всё little-endian. */
object Proto {
    const val PORT = 47800
    const val VERSION: Byte = 1

    const val HELLO = 0x01
    const val PAD = 0x02
    const val MOUSE_MOVE = 0x03
    const val MOUSE_BTN = 0x04
    const val SCROLL = 0x05
    const val KEY = 0x06
    const val TEXT = 0x07
    const val PING = 0x08
    const val BYE = 0x09
    const val HELD = 0x0B
    const val STREAM_START = 0x0C
    const val STREAM_STOP = 0x0D
    const val MOUSE_ABS = 0x0E
    const val DISCOVER = 0x10

    const val WELCOME = 0x81
    const val RUMBLE = 0x83
    const val VIDEO = 0x84
    const val STREAM_INFO = 0x85
    const val AUDIO = 0x86
    const val PONG = 0x88
    const val DISCOVER_REPLY = 0x90

    const val ST_OK = 0
    const val ST_BAD_PIN = 1
    const val ST_FULL = 2
    const val ST_REHELLO = 3

    const val CAP_GAMEPAD = 0x01
    const val CAP_STREAM = 0x02

    const val SS_OK = 0
    const val SS_NO_FFMPEG = 1
    const val SS_FAILED = 2
    const val SS_STOPPED = 3

    private fun packet(type: Int, bodySize: Int): ByteBuffer =
        ByteBuffer.allocate(4 + bodySize).order(ByteOrder.LITTLE_ENDIAN).apply {
            put('R'.code.toByte()); put('K'.code.toByte()); put(VERSION); put(type.toByte())
        }

    private fun shortStr(s: String): ByteArray {
        var raw = s.toByteArray(Charsets.UTF_8)
        if (raw.size > 255) raw = raw.copyOf(255)
        return byteArrayOf(raw.size.toByte()) + raw
    }

    fun discover(): ByteArray = packet(DISCOVER, 0).array()

    fun hello(pin: String, name: String): ByteArray {
        val p = shortStr(pin)
        val n = shortStr(name)
        return packet(HELLO, p.size + n.size).put(p).put(n).array()
    }

    fun pad(buttons: Int, lt: Int, rt: Int, lx: Int, ly: Int, rx: Int, ry: Int): ByteArray =
        packet(PAD, 12).putShort(buttons.toShort()).put(lt.toByte()).put(rt.toByte())
            .putShort(lx.toShort()).putShort(ly.toShort()).putShort(rx.toShort()).putShort(ry.toShort()).array()

    fun mouseMove(dx: Int, dy: Int): ByteArray =
        packet(MOUSE_MOVE, 4).putShort(dx.toShort()).putShort(dy.toShort()).array()

    fun mouseButton(button: Int, down: Boolean): ByteArray =
        packet(MOUSE_BTN, 2).put(button.toByte()).put(if (down) 1 else 0).array()

    fun scroll(vertical: Int, horizontal: Int): ByteArray =
        packet(SCROLL, 4).putShort(vertical.toShort()).putShort(horizontal.toShort()).array()

    fun key(vk: Int, down: Boolean): ByteArray =
        packet(KEY, 3).putShort(vk.toShort()).put(if (down) 1 else 0).array()

    fun text(s: String): ByteArray {
        val raw = s.toByteArray(Charsets.UTF_8)
        return packet(TEXT, raw.size).put(raw).array()
    }

    fun ping(stamp: Int, lastRtt: Int): ByteArray = packet(PING, 8).putInt(stamp).putInt(lastRtt).array()

    fun bye(): ByteArray = packet(BYE, 0).array()

    /** Попросить картинку с ПК. 0×0: размер как у монитора. */
    fun streamStart(width: Int, height: Int, fps: Int, kbps: Int, monitor: Int, audio: Boolean): ByteArray =
        packet(STREAM_START, 11).putShort(width.toShort()).putShort(height.toShort()).put(fps.toByte())
            .putInt(kbps).put(monitor.toByte()).put(if (audio) 1 else 0).array()

    fun streamStop(): ByteArray = packet(STREAM_STOP, 0).array()

    /** Курсор в точку картинки, обе координаты 0..65535. */
    fun mouseAbs(x: Int, y: Int): ByteArray =
        packet(MOUSE_ABS, 4).putShort(x.toShort()).putShort(y.toShort()).array()

    fun held(mouseMask: Int, keys: Collection<Int>): ByteArray {
        val list = keys.take(120)
        val b = packet(HELD, 2 + list.size * 2).put(mouseMask.toByte()).put(list.size.toByte())
        list.forEach { b.putShort(it.toShort()) }
        return b.array()
    }

    /** Возвращает тип и тело пакета либо null для чужих пакетов. */
    fun parse(data: ByteArray, length: Int = data.size): Pair<Int, ByteBuffer>? {
        if (length < 4 || data[0] != 'R'.code.toByte() || data[1] != 'K'.code.toByte() || data[2] != VERSION) return null
        val body = ByteBuffer.wrap(data, 4, length - 4).slice().order(ByteOrder.LITTLE_ENDIAN)
        return (data[3].toInt() and 0xFF) to body
    }

    fun readStr(b: ByteBuffer): String {
        val n = b.get().toInt() and 0xFF
        val raw = ByteArray(n)
        b.get(raw)
        return String(raw, Charsets.UTF_8)
    }
}
