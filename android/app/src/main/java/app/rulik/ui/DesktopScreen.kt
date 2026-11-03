package app.rulik.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rulik.R
import app.rulik.RulikApp
import app.rulik.model.Action
import app.rulik.model.Kind
import app.rulik.net.Proto
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

private val LEFT = Action(Kind.MOUSE, 1)
private val RIGHT = Action(Kind.MOUSE, 2)

private enum class Gesture { TAP, HOLD, DRAG, SCROLL }

/**
 * Рабочий стол ПК на экране телефона. Палец работает как на сенсорном мониторе:
 * касание кликает, удержание кликает правой, протяжка тащит, двумя пальцами прокрутка.
 */
@Composable
fun DesktopScreen(app: RulikApp, onExit: () -> Unit) {
    val engine = app.engine
    var rect by remember { mutableStateOf(Rect.Zero) }
    var showText by remember { mutableStateOf(false) }

    DisposableEffect(Unit) { onDispose { engine.releaseAll() } }
    BackHandler(onBack = onExit)

    fun moveTo(p: Offset) {
        val r = rect
        if (r.width <= 0f || r.height <= 0f) return
        val x = ((p.x - r.left) / r.width).coerceIn(0f, 1f)
        val y = ((p.y - r.top) / r.height).coerceIn(0f, 1f)
        app.client.send(Proto.mouseAbs((x * 65535).roundToInt(), (y * 65535).roundToInt()))
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        StreamVideo(app, onRect = { rect = it })
        Box(
            Modifier
                .fillMaxSize()
                .systemGestureExclusion()
                .pointerInput(Unit) {
                    val slop = 10.dp.toPx()
                    val notch = 40.dp.toPx()
                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitPointerEvent().changes.firstOrNull { it.pressed } ?: continue
                            val start = down.position
                            moveTo(start)
                            // первые полсекунды решают, что это: клик, протяжка, удержание или два пальца
                            var gesture = Gesture.HOLD
                            withTimeoutOrNull(450) {
                                while (true) {
                                    val pressed = awaitPointerEvent().changes.filter { it.pressed }
                                    gesture = when {
                                        pressed.isEmpty() -> Gesture.TAP
                                        pressed.size >= 2 -> Gesture.SCROLL
                                        (pressed[0].position - start).getDistance() > slop -> Gesture.DRAG
                                        else -> continue
                                    }
                                    break
                                }
                            }
                            when (gesture) {
                                Gesture.TAP -> { engine.press(LEFT); engine.release(LEFT); continue }
                                Gesture.HOLD -> { engine.press(RIGHT); engine.release(RIGHT); app.haptics.tick() }
                                Gesture.DRAG -> engine.press(LEFT)
                                Gesture.SCROLL -> {}
                            }
                            var scroll = 0f
                            var scrolled = false
                            var lastMid: Offset? = null
                            while (true) {
                                val ev = awaitPointerEvent()
                                val pressed = ev.changes.filter { it.pressed }
                                ev.changes.forEach { it.consume() }
                                if (pressed.isEmpty()) break
                                when (gesture) {
                                    Gesture.DRAG -> moveTo(pressed[0].position)
                                    Gesture.SCROLL -> if (pressed.size >= 2) {
                                        val mid = (pressed[0].position + pressed[1].position) / 2f
                                        lastMid?.let { scroll += mid.y - it.y }
                                        lastMid = mid
                                        val steps = (scroll / notch).toInt()
                                        if (steps != 0) {
                                            engine.wheel(steps.toFloat())
                                            scroll -= steps * notch
                                            scrolled = true
                                        }
                                    }
                                    else -> {} // после правого клика курсор не двигаем, иначе уедет с меню
                                }
                            }
                            if (gesture == Gesture.DRAG) engine.release(LEFT)
                            // касание двумя пальцами без прокрутки: правая кнопка, как на ноутбуке
                            if (gesture == Gesture.SCROLL && !scrolled) { engine.press(RIGHT); engine.release(RIGHT) }
                        }
                    }
                },
        )
        DesktopPill(app, Modifier.align(Alignment.TopCenter), onText = { showText = true }, onExit = onExit)
    }

    if (showText) TextDialog(onSend = engine::text, onKey = { vk -> engine.keyDown(vk); engine.keyUp(vk) }) { showText = false }
}

@Composable
private fun DesktopPill(app: RulikApp, modifier: Modifier, onText: () -> Unit, onExit: () -> Unit) {
    val alive by app.client.alive.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier.padding(top = 4.dp).clip(RoundedCornerShape(50)).background(Color(0x99171A21))
                .clickable { menu = true }.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (alive) Good else Accent))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.desktop), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
            Text("  ⋯", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.keyboard)) }, leadingIcon = { Icon(Icons.Default.Keyboard, null) },
                onClick = { menu = false; onText() })
            DropdownMenuItem(text = { Text(stringResource(R.string.exit)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                onClick = { menu = false; onExit() })
        }
    }
}
