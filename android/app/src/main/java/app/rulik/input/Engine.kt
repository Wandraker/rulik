package app.rulik.input

import app.rulik.model.Action
import app.rulik.model.Kind
import app.rulik.net.Proto
import kotlin.math.roundToInt

/**
 * Собирает ввод от всех элементов раскладки и шлёт на ПК.
 * Счётчики нужны, чтобы две кнопки на одну клавишу не отпускали её друг у друга.
 */
class Engine(private val sink: (ByteArray) -> Unit) {
    private val lock = Any()
    private val keyCount = HashMap<Int, Int>()
    private val mouseCount = IntArray(6)
    private val padCount = HashMap<Int, Int>()
    private var lt = 0
    private var rt = 0
    private var ltAnalog = 0f
    private var rtAnalog = 0f
    /** Несколько источников на один стик (тачпад-камера плюс гироскоп) складываются. */
    private val sticks = HashMap<String, FloatArray>()
    private var padUsed = false
    private var lastPad: ByteArray? = null
    private var mouseRemX = 0f
    private var mouseRemY = 0f
    private var wheelRem = 0f

    var gyroHeld = 0
        private set

    var onText: (() -> Unit)? = null

    fun press(a: Action) = synchronized(lock) {
        when (a.kind) {
            Kind.NONE -> {}
            Kind.PAD -> { padCount.merge(a.code, 1, Int::plus); padChanged() }
            Kind.LT -> { lt++; padChanged() }
            Kind.RT -> { rt++; padChanged() }
            Kind.KEY -> a.keys.forEach { keyDown(it) }
            Kind.MOUSE -> if (a.code in 1..5 && mouseCount[a.code]++ == 0) sink(Proto.mouseButton(a.code, true))
            Kind.WHEEL_UP -> sink(Proto.scroll(120, 0))
            Kind.WHEEL_DOWN -> sink(Proto.scroll(-120, 0))
            Kind.GYRO -> gyroHeld++
            Kind.TEXT -> onText?.invoke()
        }
    }

    fun release(a: Action) = synchronized(lock) {
        when (a.kind) {
            Kind.PAD -> {
                val n = (padCount[a.code] ?: 0) - 1
                if (n <= 0) padCount.remove(a.code) else padCount[a.code] = n
                padChanged()
            }
            Kind.LT -> { lt = (lt - 1).coerceAtLeast(0); padChanged() }
            Kind.RT -> { rt = (rt - 1).coerceAtLeast(0); padChanged() }
            Kind.KEY -> a.keys.asReversed().forEach { keyUp(it) }
            Kind.MOUSE -> if (a.code in 1..5 && mouseCount[a.code] > 0 && --mouseCount[a.code] == 0) {
                sink(Proto.mouseButton(a.code, false))
            }
            Kind.GYRO -> gyroHeld = (gyroHeld - 1).coerceAtLeast(0)
            else -> {}
        }
    }

    fun keyDown(vk: Int) = synchronized(lock) {
        if (keyCount.merge(vk, 1, Int::plus) == 1) sink(Proto.key(vk, true))
    }

    fun keyUp(vk: Int) = synchronized(lock) {
        val n = (keyCount[vk] ?: return@synchronized) - 1
        if (n <= 0) {
            keyCount.remove(vk)
            sink(Proto.key(vk, false))
        } else keyCount[vk] = n
    }

    /** x и y от -1 до 1, y вверх положительный, как в XInput. */
    fun stick(source: String, right: Boolean, x: Float, y: Float) = synchronized(lock) {
        sticks[source] = floatArrayOf(if (right) 1f else 0f, x, y)
        padChanged()
    }

    fun clearStick(source: String) = synchronized(lock) {
        if (sticks.remove(source) != null) padChanged()
    }

    fun trigger(right: Boolean, value: Float) = synchronized(lock) {
        if (right) rtAnalog = value else ltAnalog = value
        padChanged()
    }

    fun mouseMove(dx: Float, dy: Float) = synchronized(lock) {
        mouseRemX += dx
        mouseRemY += dy
        val ix = mouseRemX.toInt().coerceIn(-32000, 32000)
        val iy = mouseRemY.toInt().coerceIn(-32000, 32000)
        if (ix != 0 || iy != 0) {
            mouseRemX -= ix
            mouseRemY -= iy
            sink(Proto.mouseMove(ix, iy))
        }
    }

    /** amount в «щелчках» колеса, дробные копятся. */
    fun wheel(amount: Float) = synchronized(lock) {
        wheelRem += amount * 120f
        val i = wheelRem.toInt()
        if (i != 0) {
            wheelRem -= i
            sink(Proto.scroll(i, 0))
        }
    }

    fun text(s: String) {
        if (s.isNotEmpty()) sink(Proto.text(s))
    }

    private fun padChanged() {
        padUsed = true
        val packet = buildPad()
        if (!packet.contentEquals(lastPad)) {
            lastPad = packet
            sink(packet)
        }
    }

    private fun axis(v: Float): Int = (v.coerceIn(-1f, 1f) * 32767f).roundToInt()

    private fun buildPad(): ByteArray {
        var buttons = 0
        padCount.keys.forEach { buttons = buttons or it }
        var lx = 0f; var ly = 0f; var rx = 0f; var ry = 0f
        sticks.values.forEach { s ->
            if (s[0] > 0f) { rx += s[1]; ry += s[2] } else { lx += s[1]; ly += s[2] }
        }
        val l = if (lt > 0) 255 else (ltAnalog * 255).roundToInt().coerceIn(0, 255)
        val r = if (rt > 0) 255 else (rtAnalog * 255).roundToInt().coerceIn(0, 255)
        return Proto.pad(buttons, l, r, axis(lx), axis(ly), axis(rx), axis(ry))
    }

    /** Раз в 250 мс: подтверждаем состояние, чтобы потерянный UDP-пакет не оставил зажатую клавишу. */
    fun heartbeat() = synchronized(lock) {
        var mask = 0
        for (b in 1..5) if (mouseCount[b] > 0) mask = mask or (1 shl (b - 1))
        sink(Proto.held(mask, keyCount.keys))
        if (padUsed) sink(buildPad())
    }

    fun releaseAll() = synchronized(lock) {
        keyCount.keys.toList().forEach { sink(Proto.key(it, false)) }
        keyCount.clear()
        for (b in 1..5) if (mouseCount[b] > 0) { mouseCount[b] = 0; sink(Proto.mouseButton(b, false)) }
        padCount.clear(); sticks.clear(); lt = 0; rt = 0; ltAnalog = 0f; rtAnalog = 0f; gyroHeld = 0
        if (padUsed) { lastPad = null; padChanged() }
        sink(Proto.held(0, emptyList()))
    }
}
