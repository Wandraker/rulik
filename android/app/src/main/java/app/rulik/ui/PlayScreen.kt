package app.rulik.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import app.rulik.R
import app.rulik.RulikApp
import app.rulik.input.Aim
import app.rulik.input.ButtonHandler
import app.rulik.input.Handler
import app.rulik.input.Motion
import app.rulik.model.CType
import app.rulik.model.Layout
import app.rulik.net.WifiBoost

@Composable
fun PlayScreen(layout: Layout, app: RulikApp, stream: Boolean, onExit: () -> Unit, onEdit: () -> Unit) {
    val engine = app.engine
    val context = LocalContext.current
    val view = LocalView.current
    val handlers = remember(layout) {
        layout.controls.map { Handler.of(it, engine, if (layout.haptics) app.haptics else null) }
    }
    val owner = remember(layout) { HashMap<Long, Handler>() }
    // палец сейчас на своей кнопке или съехал с неё: для щелчка на краю
    val inside = remember(layout) { HashMap<Long, Boolean>() }
    val store = app.store
    val aim = remember(layout) { Aim(store.aimSnap, store.aimLearn, store.aimBiases(layout.id)) }
    val edge = remember { store.aimEdge }
    var showText by remember { mutableStateOf(false) }
    // поверх картинки с ПК раскладка полупрозрачная, чтобы не закрывать игру
    val opacity = remember(layout, stream) { if (stream) layout.opacity * store.overlayAlpha else layout.opacity }

    DisposableEffect(layout) {
        val motion = Motion(context, engine) { view.display?.rotation ?: 0 }
        motion.start(layout)
        val wifi = WifiBoost(context).also { it.start() }
        engine.onText = { showText = true }
        onDispose {
            if (aim.learn) {
                aim.keepOnly(layout.controls.map { it.id }.toSet())
                store.saveAimBiases(layout.id, aim.biases)
            }
            motion.stop()
            wifi.stop()
            handlers.forEach { it.reset() }
            engine.releaseAll()
            engine.onText = null
            app.haptics.stop()
        }
    }

    LaunchedEffect(handlers) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    val dt = ((now - last) / 1e9f).coerceAtMost(0.05f)
                    handlers.forEach { it.frame(dt) }
                }
                last = now
            }
        }
    }

    BackHandler(onBack = onExit)

    val tm = rememberTextMeasurer()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (stream) StreamVideo(app)
        Canvas(
            Modifier
                .fillMaxSize()
                .systemGestureExclusion()
                .pointerInput(handlers) {
                    val slack = 8.dp.toPx()
                    // радиус, в котором промах между кнопками засчитывается ближайшей: примерно полпальца
                    val reach = 28.dp.toPx()
                    fun geo(h: Handler) = geoOf(h.c, size.toSize(), density)
                    fun hit(p: Offset): Handler? = aim.hit(p, handlers, { it.c }, ::geo, slack, reach)
                    fun hasEdge(h: Handler) = h.c.type == CType.BUTTON || h.c.type == CType.DPAD
                    // щелчок на краю: ушёл с кнопки дальше запаса или вернулся на неё
                    fun edgeCheck(id: Long, h: Handler, p: Offset) {
                        if (!edge || !hasEdge(h)) return
                        val was = inside[id] ?: return
                        val now = if (was) geo(h).contains(p, slack) else aim.shifted(h.c, geo(h)).contains(p)
                        if (now != was) {
                            inside[id] = now
                            app.haptics.tick()
                        }
                    }
                    awaitPointerEventScope {
                        while (true) {
                            val ev = awaitPointerEvent()
                            for (ch in ev.changes) {
                                val id = ch.id.value
                                val t = ch.uptimeMillis
                                when {
                                    ch.changedToDownIgnoreConsumed() -> hit(ch.position)?.let { h ->
                                        owner[id] = h
                                        inside[id] = true
                                        aim.record(h.c, ch.position, geo(h))
                                        h.down(id, ch.position, geo(h), t)
                                    }
                                    ch.changedToUpIgnoreConsumed() -> {
                                        inside.remove(id)
                                        owner.remove(id)?.let { h -> h.up(id, geo(h), t) }
                                    }
                                    ch.pressed && ch.positionChanged() -> {
                                        val h = owner[id] ?: continue
                                        if (h.slidable && !geo(h).contains(ch.position, slack)) {
                                            // палец съехал на соседнюю кнопку: отпускаем старую, жмём новую
                                            val next = hit(ch.position)
                                            if (next is ButtonHandler && next !== h && next.slidable) {
                                                h.up(id, geo(h), t)
                                                owner[id] = next
                                                inside[id] = true
                                                next.down(id, ch.position, geo(next), t)
                                                if (edge) app.haptics.tick()
                                            } else {
                                                edgeCheck(id, h, ch.position)
                                            }
                                        } else {
                                            edgeCheck(id, h, ch.position)
                                            h.move(id, ch.position, geo(h), t)
                                        }
                                    }
                                }
                                ch.consume()
                            }
                        }
                    }
                },
        ) {
            val sz = Size(size.width, size.height)
            layout.controls.forEachIndexed { i, c ->
                drawControl(c, geoOf(c, sz, density), handlers[i], tm, opacity)
            }
        }

        StatusPill(app, Modifier.align(Alignment.TopCenter), onEdit = onEdit, onText = { showText = true }, onExit = onExit)
    }

    if (showText) TextDialog(onSend = engine::text, onKey = { vk -> engine.keyDown(vk); engine.keyUp(vk) }) { showText = false }
}

@Composable
private fun StatusPill(app: RulikApp, modifier: Modifier, onEdit: () -> Unit, onText: () -> Unit, onExit: () -> Unit) {
    val ping by app.client.ping.collectAsState()
    val alive by app.client.alive.collectAsState()
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .padding(top = 4.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0x99171A21))
                .clickable { menu = true }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (alive) Good else Accent))
            Spacer(Modifier.width(6.dp))
            Text(
                if (alive) (if (app.store.showPing && ping != null) "$ping ms" else "Rulik") else stringResource(R.string.no_link),
                fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f),
            )
            Text("  ⋯", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.keyboard)) }, leadingIcon = { Icon(Icons.Default.Keyboard, null) },
                onClick = { menu = false; onText() })
            DropdownMenuItem(text = { Text(stringResource(R.string.edit_layout)) }, leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { menu = false; onEdit() })
            DropdownMenuItem(text = { Text(stringResource(R.string.exit)) }, leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, null) },
                onClick = { menu = false; onExit() })
        }
    }
}

/** Набор текста на ПК: каждая буква уходит сразу, стирание шлёт Backspace. */
@Composable
internal fun TextDialog(onSend: (String) -> Unit, onKey: (Int) -> Unit, onDismiss: () -> Unit) {
    var value by remember { mutableStateOf(TextFieldValue("")) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.type_on_pc)) },
        text = {
            Column {
                OutlinedTextField(
                    value = value,
                    onValueChange = { nv ->
                        val old = value.text
                        val new = nv.text
                        val common = old.commonPrefixWith(new).length
                        repeat(old.length - common) { onKey(0x08) }
                        if (new.length > common) onSend(new.substring(common))
                        value = nv.copy(selection = TextRange(nv.text.length))
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text(stringResource(R.string.type_hint)) },
                )
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { onKey(0x0D); value = TextFieldValue("") }, label = { Text("Enter") })
                    AssistChip(onClick = { onKey(0x08) }, label = { Text("⌫") })
                    AssistChip(onClick = { onKey(0x09) }, label = { Text("Tab") })
                    AssistChip(onClick = { onKey(0x1B) }, label = { Text("Esc") })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
    )
}
