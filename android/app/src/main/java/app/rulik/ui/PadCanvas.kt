package app.rulik.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import app.rulik.input.DpadHandler
import app.rulik.input.Geo
import app.rulik.input.Handler
import app.rulik.input.StickHandler
import app.rulik.input.TouchpadHandler
import app.rulik.model.CType
import app.rulik.model.Control
import app.rulik.model.Out
import app.rulik.model.Shape
import app.rulik.model.describe
import kotlin.math.min

fun geoOf(c: Control, size: Size, density: Float): Geo {
    val unit = min(size.width, size.height)
    return Geo(c.x * size.width, c.y * size.height, c.w * unit / 2f, c.h * unit / 2f, density)
}

fun Control.displayLabel(): String = label.ifEmpty {
    when (type) {
        CType.BUTTON -> action.describe()
        CType.STICK -> when (out) {
            Out.LEFT_STICK -> "L"; Out.RIGHT_STICK -> "R"; Out.WASD -> "WASD"; Out.ARROWS -> "↑↓"; Out.MOUSE -> "🖱"; Out.DPAD -> "✚"
        }
        CType.DPAD -> ""
        CType.TOUCHPAD -> if (out == Out.MOUSE) "" else "◎"
    }
}

private fun DrawScope.label(tm: TextMeasurer, text: String, center: Offset, maxPx: Float, color: Color, boxW: Float) {
    if (text.isEmpty()) return
    val sizePx = (maxPx * if (text.length <= 2) 0.9f else if (text.length <= 4) 0.62f else 0.45f).coerceAtLeast(9f * density)
    val layout = tm.measure(
        text,
        TextStyle(color = color, fontSize = sizePx.toSp(), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
        maxLines = 1,
        constraints = androidx.compose.ui.unit.Constraints(maxWidth = boxW.toInt().coerceAtLeast(1)),
    )
    drawText(layout, topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f))
}

fun DrawScope.drawControl(c: Control, g: Geo, h: Handler?, tm: TextMeasurer, alpha: Float, selected: Boolean = false) {
    val pressed = h?.pressed == true
    val fill = if (pressed) Accent.copy(alpha = 0.55f * alpha) else Color.White.copy(alpha = 0.08f * alpha)
    val line = if (pressed) Accent.copy(alpha = alpha) else Color.White.copy(alpha = 0.32f * alpha)
    val text = Color.White.copy(alpha = 0.92f * alpha)
    val center = Offset(g.cx, g.cy)
    val stroke = Stroke(width = 1.5f * density)
    val topLeft = Offset(g.cx - g.hw, g.cy - g.hh)
    val size = Size(g.hw * 2, g.hh * 2)

    when (c.type) {
        CType.BUTTON -> {
            if (c.shape == Shape.CIRCLE) {
                val r = min(g.hw, g.hh)
                drawCircle(fill, r, center)
                drawCircle(line, r, center, style = stroke)
            } else {
                val cr = CornerRadius(min(g.hw, g.hh) * 0.45f)
                drawRoundRect(fill, topLeft, size, cr)
                drawRoundRect(line, topLeft, size, cr, style = stroke)
            }
            label(tm, c.displayLabel(), center, min(g.hw, g.hh) * 0.8f, text, g.hw * 1.9f)
        }

        CType.STICK -> {
            val s = h as? StickHandler
            val origin = s?.origin ?: center
            val r = g.hw
            if (c.floating && s?.origin == null) {
                drawCircle(Color.White.copy(alpha = 0.04f * alpha), r, center)
                drawCircle(line.copy(alpha = 0.18f * alpha), r, center, style = Stroke(1f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))))
            } else {
                drawCircle(Color.White.copy(alpha = 0.06f * alpha), r, origin)
                drawCircle(line.copy(alpha = 0.25f * alpha), r, origin, style = stroke)
            }
            val knob = origin + (s?.knob ?: Offset.Zero) * (r * 0.6f)
            drawCircle(if (pressed) Accent.copy(alpha = 0.7f * alpha) else Color.White.copy(alpha = 0.18f * alpha), r * 0.42f, knob)
            drawCircle(line, r * 0.42f, knob, style = stroke)
            label(tm, c.displayLabel(), knob, r * 0.3f, text, r * 0.8f)
        }

        CType.DPAD -> {
            val dir = (h as? DpadHandler)?.dir ?: 0
            val arm = min(g.hw, g.hh) * 0.66f
            val cr = CornerRadius(arm * 0.18f)
            val arms = listOf(
                1 to (Offset(g.cx - arm / 2, g.cy - g.hh) to Size(arm, g.hh - arm / 2)),
                2 to (Offset(g.cx - arm / 2, g.cy + arm / 2) to Size(arm, g.hh - arm / 2)),
                4 to (Offset(g.cx - g.hw, g.cy - arm / 2) to Size(g.hw - arm / 2, arm)),
                8 to (Offset(g.cx + arm / 2, g.cy - arm / 2) to Size(g.hw - arm / 2, arm)),
            )
            drawRect(Color.White.copy(alpha = 0.08f * alpha), Offset(g.cx - arm / 2, g.cy - arm / 2), Size(arm, arm))
            for ((bit, rect) in arms) {
                val on = dir and bit != 0
                drawRoundRect(if (on) Accent.copy(alpha = 0.6f * alpha) else Color.White.copy(alpha = 0.08f * alpha), rect.first, rect.second, cr)
                drawRoundRect(if (on) Accent.copy(alpha = alpha) else line, rect.first, rect.second, cr, style = stroke)
            }
        }

        CType.TOUCHPAD -> {
            val cr = CornerRadius(18f * density)
            val touches = (h as? TouchpadHandler)?.touches ?: 0
            drawRoundRect(Color.White.copy(alpha = (if (touches > 0) 0.09f else 0.05f) * alpha), topLeft, size, cr)
            drawRoundRect(line.copy(alpha = 0.22f * alpha), topLeft, size, cr, style = stroke)
            label(tm, c.displayLabel(), center, 22f * density, text.copy(alpha = 0.35f * alpha), g.hw * 1.8f)
        }
    }

    if (selected) {
        val pad = 4f * density
        drawRect(Accent, topLeft - Offset(pad, pad), Size(size.width + pad * 2, size.height + pad * 2),
            style = Stroke(2f * density, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
        drawCircle(Accent, HANDLE_DP * density / 2, handleOf(g))
        drawCircle(Color.White, HANDLE_DP * density / 5, handleOf(g))
    }
}

const val HANDLE_DP = 28f

fun handleOf(g: Geo) = Offset(g.cx + g.hw + 4f * g.density, g.cy + g.hh + 4f * g.density)
