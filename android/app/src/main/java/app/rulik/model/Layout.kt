package app.rulik.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** Что делает кнопка. */
@Serializable
enum class Kind { NONE, PAD, LT, RT, KEY, MOUSE, WHEEL_UP, WHEEL_DOWN, GYRO, TEXT }

/**
 * PAD: code это бит кнопки Xbox (см. [Pad]).
 * KEY: keys это виртуальные коды Windows, несколько штук значит сочетание (Alt+Tab).
 * MOUSE: code 1 левая, 2 правая, 3 колесо, 4 и 5 боковые.
 */
@Serializable
data class Action(
    val kind: Kind = Kind.NONE,
    val code: Int = 0,
    val keys: List<Int> = emptyList(),
)

@Serializable
enum class CType { BUTTON, STICK, DPAD, TOUCHPAD }

/** Куда идёт стик, крестовина или тачпад. */
@Serializable
enum class Out { LEFT_STICK, RIGHT_STICK, WASD, ARROWS, MOUSE, DPAD }

@Serializable
enum class BMode { HOLD, TOGGLE, TURBO }

@Serializable
enum class Shape { CIRCLE, ROUNDED }

/**
 * Элемент раскладки. x, y это центр в долях экрана, w, h размер в долях короткой стороны экрана,
 * поэтому круг остаётся кругом на любом телефоне.
 */
@Serializable
data class Control(
    val id: String = newId(),
    val type: CType,
    val x: Float,
    val y: Float,
    val w: Float,
    val h: Float = w,
    val label: String = "",
    val action: Action = Action(),
    val mode: BMode = BMode.HOLD,
    val out: Out = Out.LEFT_STICK,
    val sens: Float = 1f,
    val deadzone: Float = 0.12f,
    val shape: Shape = Shape.CIRCLE,
    val floating: Boolean = false,
)

@Serializable
enum class GyroMode { OFF, MOUSE, RIGHT_STICK }

@Serializable
data class Layout(
    val id: String = newId(),
    val name: String,
    val controls: List<Control> = emptyList(),
    val portrait: Boolean = false,
    val opacity: Float = 0.85f,
    val haptics: Boolean = true,
    val gyro: GyroMode = GyroMode.OFF,
    val gyroSens: Float = 1f,
    val gyroAlways: Boolean = true,
    val gyroInvertX: Boolean = false,
    val gyroInvertY: Boolean = false,
    val tilt: Boolean = false,
    val tiltAngle: Float = 35f,
    val tiltInvert: Boolean = false,
)

fun newId(): String = UUID.randomUUID().toString().substring(0, 8)

/** Биты кнопок XInput, ровно как в отчёте XUSB. */
object Pad {
    const val UP = 0x0001
    const val DOWN = 0x0002
    const val LEFT = 0x0004
    const val RIGHT = 0x0008
    const val START = 0x0010
    const val BACK = 0x0020
    const val LS = 0x0040
    const val RS = 0x0080
    const val LB = 0x0100
    const val RB = 0x0200
    const val GUIDE = 0x0400
    const val A = 0x1000
    const val B = 0x2000
    const val X = 0x4000
    const val Y = 0x8000

    val all = listOf(
        A to "A", B to "B", X to "X", Y to "Y", LB to "LB", RB to "RB",
        BACK to "Back", START to "Start", GUIDE to "Xbox", LS to "L3", RS to "R3",
        UP to "↑", DOWN to "↓", LEFT to "←", RIGHT to "→",
    )

    fun name(bit: Int) = all.firstOrNull { it.first == bit }?.second ?: "?"
}

fun Action.describe(): String = when (kind) {
    Kind.NONE -> "—"
    Kind.PAD -> Pad.name(code)
    Kind.LT -> "LT"
    Kind.RT -> "RT"
    Kind.KEY -> keys.joinToString("+") { Keys.name(it) }
    Kind.MOUSE -> when (code) { 1 -> "LMB"; 2 -> "RMB"; 3 -> "MMB"; 4 -> "M4"; else -> "M5" }
    Kind.WHEEL_UP -> "↥"
    Kind.WHEEL_DOWN -> "↧"
    Kind.GYRO -> "Gyro"
    Kind.TEXT -> "⌨"
}
