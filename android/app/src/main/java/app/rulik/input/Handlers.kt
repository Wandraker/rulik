package app.rulik.input

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import app.rulik.model.Action
import app.rulik.model.BMode
import app.rulik.model.CType
import app.rulik.model.Control
import app.rulik.model.Keys
import app.rulik.model.Kind
import app.rulik.model.Out
import app.rulik.model.Pad
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt

/** Геометрия элемента на экране в пикселях. */
data class Geo(val cx: Float, val cy: Float, val hw: Float, val hh: Float, val density: Float) {
    fun contains(p: Offset, slack: Float = 0f) =
        abs(p.x - cx) <= hw + slack && abs(p.y - cy) <= hh + slack
}

abstract class Handler(val c: Control, protected val e: Engine, protected val fx: Haptics?) {
    var pressed by mutableStateOf(false)
        protected set
    abstract fun down(id: Long, p: Offset, g: Geo, t: Long)
    open fun move(id: Long, p: Offset, g: Geo, t: Long) {}
    abstract fun up(id: Long, g: Geo, t: Long)
    open fun frame(dt: Float) {}
    abstract fun reset()
    /** Можно ли пальцу съехать с этого элемента на соседнюю кнопку. */
    open val slidable = false

    companion object {
        fun of(c: Control, e: Engine, fx: Haptics?): Handler = when (c.type) {
            CType.BUTTON -> ButtonHandler(c, e, fx)
            CType.STICK -> StickHandler(c, e, fx)
            CType.DPAD -> DpadHandler(c, e, fx)
            CType.TOUCHPAD -> TouchpadHandler(c, e, fx)
        }
    }
}

class ButtonHandler(c: Control, e: Engine, fx: Haptics?) : Handler(c, e, fx) {
    private var latched = false
    private var turboOn = false
    private var turboClock = 0f
    private var fingers = 0
    override val slidable get() = c.mode == BMode.HOLD

    override fun down(id: Long, p: Offset, g: Geo, t: Long) {
        if (fingers++ > 0) return
        fx?.tick()
        when (c.mode) {
            BMode.HOLD -> { e.press(c.action); pressed = true }
            BMode.TOGGLE -> {
                latched = !latched
                if (latched) e.press(c.action) else e.release(c.action)
                pressed = latched
            }
            BMode.TURBO -> { pressed = true; turboOn = true; turboClock = 0f; e.press(c.action) }
        }
    }

    override fun up(id: Long, g: Geo, t: Long) {
        if (fingers == 0 || --fingers > 0) return
        when (c.mode) {
            BMode.HOLD -> { e.release(c.action); pressed = false }
            BMode.TOGGLE -> {}
            BMode.TURBO -> { if (turboOn) e.release(c.action); turboOn = false; pressed = false }
        }
    }

    override fun frame(dt: Float) {
        if (!pressed || c.mode != BMode.TURBO) return
        turboClock += dt
        // примерно 12 нажатий в секунду, чувствительность ускоряет
        val half = 0.042f / c.sens.coerceIn(0.3f, 3f)
        if (turboClock >= half) {
            turboClock = 0f
            turboOn = !turboOn
            if (turboOn) e.press(c.action) else e.release(c.action)
        }
    }

    override fun reset() {
        if (pressed || latched) {
            if (c.mode != BMode.TURBO || turboOn) e.release(c.action)
        }
        pressed = false; latched = false; turboOn = false; fingers = 0
    }
}

/** Направления для WASD/стрелок/крестовины по вектору. */
private class DirectionKeys(private val e: Engine, private val out: Out) {
    private var cur = 0 // биты: 1 вверх, 2 вниз, 4 влево, 8 вправо

    fun set(x: Float, y: Float, threshold: Float) {
        var bits = 0
        val len = hypot(x, y)
        if (len >= threshold) {
            // 8 секторов по 45°: диагонали включают две клавиши
            val ang = Math.toDegrees(atan2(-y.toDouble(), x.toDouble())).let { if (it < 0) it + 360 else it }
            val sector = ((ang + 22.5) / 45).toInt() % 8
            bits = when (sector) {
                0 -> 8; 1 -> 1 or 8; 2 -> 1; 3 -> 1 or 4; 4 -> 4; 5 -> 2 or 4; 6 -> 2; else -> 2 or 8
            }
        }
        apply(bits)
    }

    fun apply(bits: Int) {
        if (bits == cur) return
        for (b in intArrayOf(1, 2, 4, 8)) {
            val was = cur and b != 0
            val now = bits and b != 0
            if (was != now) emit(b, now)
        }
        cur = bits
    }

    private fun emit(b: Int, down: Boolean) {
        if (out == Out.DPAD) {
            val bit = when (b) { 1 -> Pad.UP; 2 -> Pad.DOWN; 4 -> Pad.LEFT; else -> Pad.RIGHT }
            val a = Action(Kind.PAD, bit)
            if (down) e.press(a) else e.release(a)
            return
        }
        val vk = if (out == Out.ARROWS) {
            when (b) { 1 -> Keys.UP; 2 -> Keys.DOWN; 4 -> Keys.LEFT; else -> Keys.RIGHT }
        } else {
            when (b) { 1 -> Keys.W; 2 -> Keys.S; 4 -> Keys.A; else -> Keys.D }
        }
        if (down) e.keyDown(vk) else e.keyUp(vk)
    }

    val active get() = cur
}

class StickHandler(c: Control, e: Engine, fx: Haptics?) : Handler(c, e, fx) {
    var knob by mutableStateOf(Offset.Zero)
        private set
    var origin by mutableStateOf<Offset?>(null)
        private set
    private var pointer: Long? = null
    private val dirs = DirectionKeys(e, c.out)
    private var vec = Offset.Zero

    override fun down(id: Long, p: Offset, g: Geo, t: Long) {
        if (pointer != null) return
        pointer = id
        pressed = true
        origin = if (c.floating) p else Offset(g.cx, g.cy)
        move(id, p, g, t)
    }

    override fun move(id: Long, p: Offset, g: Geo, t: Long) {
        if (id != pointer) return
        val o = origin ?: return
        val r = g.hw
        var v = Offset((p.x - o.x) / r, (p.y - o.y) / r)
        val len = v.getDistance()
        if (len > 1f) v /= len
        knob = v
        // мёртвая зона с пересчётом, чтобы после неё ход шёл плавно с нуля
        val dz = c.deadzone.coerceIn(0f, 0.6f)
        val l = v.getDistance()
        vec = if (l <= dz) Offset.Zero else v * ((l - dz) / (1 - dz) / l)
        output()
    }

    private fun output() {
        when (c.out) {
            Out.LEFT_STICK, Out.RIGHT_STICK -> {
                val s = c.sens.coerceIn(0.2f, 3f)
                e.stick(c.id, c.out == Out.RIGHT_STICK, (vec.x * s).coerceIn(-1f, 1f), (-vec.y * s).coerceIn(-1f, 1f))
            }
            Out.WASD, Out.ARROWS, Out.DPAD -> dirs.set(vec.x, vec.y, 0.35f)
            Out.MOUSE -> {} // в frame()
        }
    }

    override fun frame(dt: Float) {
        if (c.out != Out.MOUSE || pointer == null) return
        val l = vec.getDistance()
        if (l == 0f) return
        // кривая: точное наведение у центра, быстрый разворот на краю
        val speed = 1800f * c.sens * l.toDouble().pow(1.6).toFloat()
        e.mouseMove(vec.x / l * speed * dt, vec.y / l * speed * dt)
    }

    override fun up(id: Long, g: Geo, t: Long) {
        if (id != pointer) return
        reset()
    }

    override fun reset() {
        pointer = null
        pressed = false
        knob = Offset.Zero
        origin = null
        vec = Offset.Zero
        e.clearStick(c.id)
        dirs.apply(0)
    }
}

class DpadHandler(c: Control, e: Engine, fx: Haptics?) : Handler(c, e, fx) {
    var dir by mutableIntStateOf(0)
        private set
    private var pointer: Long? = null
    private val dirs = DirectionKeys(e, if (c.out == Out.LEFT_STICK || c.out == Out.RIGHT_STICK || c.out == Out.MOUSE) Out.DPAD else c.out)

    override fun down(id: Long, p: Offset, g: Geo, t: Long) {
        if (pointer != null) return
        pointer = id
        pressed = true
        move(id, p, g, t)
    }

    override fun move(id: Long, p: Offset, g: Geo, t: Long) {
        if (id != pointer) return
        val before = dirs.active
        dirs.set((p.x - g.cx) / g.hw, (p.y - g.cy) / g.hh, 0.25f)
        if (dirs.active != before && dirs.active != 0) fx?.tick()
        dir = dirs.active
    }

    override fun up(id: Long, g: Geo, t: Long) {
        if (id == pointer) reset()
    }

    override fun reset() {
        pointer = null
        pressed = false
        dirs.apply(0)
        dir = 0
    }
}

class TouchpadHandler(c: Control, e: Engine, fx: Haptics?) : Handler(c, e, fx) {
    private class Finger(var pos: Offset, val start: Offset, val t0: Long, var lastT: Long, var travel: Float = 0f)

    private val fingers = LinkedHashMap<Long, Finger>()
    private var maxFingers = 0
    private var gestureStart = 0L
    private var gestureMoved = false
    private var lastTap = 0L
    private var dragging = false
    private var accX = 0f
    private var accY = 0f
    private var idle = 0f
    var touches by mutableIntStateOf(0)
        private set

    private val camera get() = c.out == Out.RIGHT_STICK || c.out == Out.LEFT_STICK

    override fun down(id: Long, p: Offset, g: Geo, t: Long) {
        if (fingers.isEmpty()) {
            maxFingers = 0
            gestureStart = t
            gestureMoved = false
            if (!camera && t - lastTap < 250) {
                dragging = true
                e.press(Action(Kind.MOUSE, 1))
            }
        }
        fingers[id] = Finger(p, p, t, t)
        maxFingers = maxOf(maxFingers, fingers.size)
        touches = fingers.size
        pressed = true
    }

    override fun move(id: Long, p: Offset, g: Geo, t: Long) {
        val f = fingers[id] ?: return
        val d = p - f.pos
        val dtMs = (t - f.lastT).coerceAtLeast(1)
        f.pos = p
        f.lastT = t
        f.travel += d.getDistance()
        if (f.travel > 10 * g.density) gestureMoved = true
        val dx = d.x / g.density
        val dy = d.y / g.density
        if (camera) {
            if (fingers.keys.first() == id) { accX += dx; accY += dy; idle = 0f }
            return
        }
        when (fingers.size) {
            1 -> {
                // лёгкое ускорение: медленно точно, быстро далеко
                val speed = sqrt(dx * dx + dy * dy) / dtMs * 1000f // dp/с
                val accel = 1f + (speed / 1500f).coerceAtMost(1.5f)
                val k = 1.6f * c.sens * accel
                e.mouseMove(dx * k, dy * k)
            }
            2 -> if (fingers.keys.first() == id) e.wheel(dy / 40f * c.sens)
        }
    }

    override fun up(id: Long, g: Geo, t: Long) {
        fingers.remove(id) ?: return
        touches = fingers.size
        if (fingers.isNotEmpty()) return
        pressed = false
        if (camera) { accX = 0f; accY = 0f; e.clearStick(c.id); return }
        if (dragging) {
            dragging = false
            e.release(Action(Kind.MOUSE, 1))
            return
        }
        if (!gestureMoved && t - gestureStart < 220) {
            val btn = if (maxFingers >= 2) 2 else 1
            fx?.tick()
            e.press(Action(Kind.MOUSE, btn))
            e.release(Action(Kind.MOUSE, btn))
            lastTap = if (btn == 1) t else 0L
        }
    }

    override fun frame(dt: Float) {
        if (!camera || fingers.isEmpty() || dt <= 0f) return
        idle += dt
        val k = c.sens / 900f
        val x = (accX / dt * k).coerceIn(-1f, 1f)
        val y = (-accY / dt * k).coerceIn(-1f, 1f)
        accX = 0f; accY = 0f
        if (idle > 0.05f) e.stick(c.id, c.out == Out.RIGHT_STICK, 0f, 0f)
        else e.stick(c.id, c.out == Out.RIGHT_STICK, x, y)
    }

    override fun reset() {
        if (dragging) e.release(Action(Kind.MOUSE, 1))
        dragging = false
        fingers.clear()
        touches = 0
        pressed = false
        e.clearStick(c.id)
    }
}
