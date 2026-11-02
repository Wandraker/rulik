package app.rulik.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import app.rulik.R
import app.rulik.model.Action
import app.rulik.model.CType
import app.rulik.model.Control
import app.rulik.model.Kind
import app.rulik.model.Layout
import app.rulik.model.Out
import app.rulik.model.Shape
import app.rulik.model.newId
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private fun snap(v: Float, step: Float = 0.005f) = (v / step).roundToInt() * step

@Composable
fun EditorScreen(initial: Layout, onSave: (Layout) -> Unit, onClose: () -> Unit) {
    var layout by remember { mutableStateOf(initial) }
    var selected by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Control?>(null) }
    var settings by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var confirmExit by remember { mutableStateOf(false) }
    val tm = rememberTextMeasurer()

    fun update(c: Control) {
        layout = layout.copy(controls = layout.controls.map { if (it.id == c.id) c else it })
    }

    fun add(type: CType) {
        val c = when (type) {
            CType.BUTTON -> Control(type = type, x = .5f, y = .5f, w = .16f, action = Action(Kind.KEY, keys = listOf(0x20)))
            CType.STICK -> Control(type = type, x = .5f, y = .5f, w = .40f, out = Out.LEFT_STICK)
            CType.DPAD -> Control(type = type, x = .5f, y = .5f, w = .32f, out = Out.DPAD)
            CType.TOUCHPAD -> Control(type = type, x = .5f, y = .5f, w = .9f, h = .6f, out = Out.MOUSE, shape = Shape.ROUNDED)
        }
        // тачпад кладём под остальные элементы, чтобы он не перекрывал кнопки
        layout = layout.copy(controls = if (type == CType.TOUCHPAD) listOf(c) + layout.controls else layout.controls + c)
        selected = c.id
    }

    BackHandler { if (layout != initial) confirmExit = true else onClose() }

    Box(Modifier.fillMaxSize().background(Color(0xFF07080B))) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val handleR = HANDLE_DP.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val sz = size.toSize()
                        val unit = min(sz.width, sz.height)
                        val current = layout
                        val sel = current.controls.firstOrNull { it.id == selected }
                        val resizing = sel != null &&
                            (down.position - handleOf(geoOf(sel, sz, density))).getDistance() < handleR
                        val target = if (resizing) sel else current.controls.lastOrNull {
                            geoOf(it, sz, density).contains(down.position, 4.dp.toPx())
                        }
                        selected = target?.id
                        if (target == null) return@awaitEachGesture
                        val start = down.position
                        while (true) {
                            val ev = awaitPointerEvent()
                            val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                            if (!ch.pressed) break
                            val d = ch.position - start
                            val moved = if (resizing) {
                                val round = target.type != CType.TOUCHPAD && !(target.type == CType.BUTTON && target.shape == Shape.ROUNDED)
                                var w = (target.w + 2 * d.x / unit).coerceIn(0.06f, 2.4f)
                                var h = (target.h + 2 * d.y / unit).coerceIn(0.06f, 2.4f)
                                if (round) { val m = max(w, h); w = m; h = m }
                                target.copy(w = snap(w), h = snap(h))
                            } else {
                                target.copy(
                                    x = snap((target.x + d.x / sz.width).coerceIn(0f, 1f)),
                                    y = snap((target.y + d.y / sz.height).coerceIn(0f, 1f)),
                                )
                            }
                            update(moved)
                            ch.consume()
                        }
                    }
                },
        ) {
            val sz = Size(size.width, size.height)
            // сетка для ориентира
            val step = min(sz.width, sz.height) / 10f
            var gx = 0f
            while (gx < sz.width) { drawLine(Color.White.copy(alpha = 0.04f), Offset(gx, 0f), Offset(gx, sz.height)); gx += step }
            var gy = 0f
            while (gy < sz.height) { drawLine(Color.White.copy(alpha = 0.04f), Offset(0f, gy), Offset(sz.width, gy)); gy += step }
            layout.controls.forEach { c ->
                drawControl(c, geoOf(c, sz, density), null, tm, 1f, selected = c.id == selected)
            }
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = 6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color(0xCC171A21))
                .padding(horizontal = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                FilledIconButton(onClick = { addMenu = true }) { Icon(Icons.Default.Add, stringResource(R.string.add)) }
                DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                    listOf(
                        CType.BUTTON to R.string.t_button, CType.STICK to R.string.t_stick,
                        CType.DPAD to R.string.t_dpad, CType.TOUCHPAD to R.string.t_touchpad,
                    ).forEach { (t, label) ->
                        DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { addMenu = false; add(t) })
                    }
                }
            }
            val sel = layout.controls.firstOrNull { it.id == selected }
            FilledTonalIconButton(onClick = { editing = sel }, enabled = sel != null) { Icon(Icons.Default.Tune, stringResource(R.string.properties)) }
            FilledTonalIconButton(onClick = {
                sel ?: return@FilledTonalIconButton
                val copy = sel.copy(id = newId(), x = (sel.x + 0.04f).coerceAtMost(1f), y = (sel.y + 0.04f).coerceAtMost(1f))
                layout = layout.copy(controls = layout.controls + copy)
                selected = copy.id
            }, enabled = sel != null) { Icon(Icons.Default.ContentCopy, stringResource(R.string.duplicate)) }
            FilledTonalIconButton(onClick = {
                layout = layout.copy(controls = layout.controls.filterNot { it.id == selected })
                selected = null
            }, enabled = sel != null) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
            FilledTonalIconButton(onClick = { settings = true }) { Icon(Icons.Default.Settings, stringResource(R.string.layout_settings)) }
            FilledIconButton(
                onClick = { onSave(layout) },
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Good, contentColor = Color.Black),
            ) { Icon(Icons.Default.Check, stringResource(R.string.save)) }
            FilledTonalIconButton(onClick = { if (layout != initial) confirmExit = true else onClose() }) {
                Icon(Icons.Default.Close, stringResource(R.string.close))
            }
        }

        if (layout.controls.isEmpty()) {
            Text(stringResource(R.string.editor_empty), Modifier.align(Alignment.Center).padding(32.dp), color = Dim)
        }
    }

    editing?.let { c ->
        ControlDialog(c, onDone = { update(it); editing = null }, onDismiss = { editing = null })
    }
    if (settings) {
        LayoutSettingsDialog(layout, onDone = { layout = it; settings = false }, onDismiss = { settings = false })
    }
    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text(stringResource(R.string.unsaved_title)) },
            text = { Text(stringResource(R.string.unsaved_text)) },
            confirmButton = { TextButton(onClick = { confirmExit = false; onSave(layout) }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { confirmExit = false; onClose() }) { Text(stringResource(R.string.discard)) } },
        )
    }
}
