package app.rulik.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.widthIn
import app.rulik.R
import app.rulik.model.Action
import app.rulik.model.BMode
import app.rulik.model.CType
import app.rulik.model.Control
import app.rulik.model.GyroMode
import app.rulik.model.Keys
import app.rulik.model.Kind
import app.rulik.model.Layout
import app.rulik.model.Out
import app.rulik.model.Pad
import app.rulik.model.Shape
import app.rulik.model.describe

/** В альбомной ориентации стандартный диалог слишком узкий для сетки клавиш. */
private val WIDE = DialogProperties(usePlatformDefaultWidth = false)

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.labelLarge, color = Dim, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choice(options: List<Pair<T, String>>, value: T, onPick: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { (v, label) ->
            FilterChip(selected = v == value, onClick = { onPick(v) }, label = { Text(label) })
        }
    }
}

@Composable
private fun LabeledSlider(title: String, value: Float, range: ClosedFloatingPointRange<Float>, format: (Float) -> String, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = Dim)
        Text(format(value), style = MaterialTheme.typography.labelLarge)
    }
    Slider(value = value, onValueChange = onChange, valueRange = range)
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
fun ControlDialog(initial: Control, onDone: (Control) -> Unit, onDismiss: () -> Unit) {
    var c by remember { mutableStateOf(initial) }
    var picking by remember { mutableStateOf(false) }
    val pct = { v: Float -> "${(v * 100).toInt()}%" }
    val x = { v: Float -> "×%.1f".format(v) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = WIDE,
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 720.dp),
        title = { Text(stringResource(when (c.type) {
            CType.BUTTON -> R.string.t_button; CType.STICK -> R.string.t_stick
            CType.DPAD -> R.string.t_dpad; CType.TOUCHPAD -> R.string.t_touchpad
        })) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = c.label, onValueChange = { c = c.copy(label = it.take(12)) },
                    label = { Text(stringResource(R.string.label)) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                )
                when (c.type) {
                    CType.BUTTON -> {
                        Section(stringResource(R.string.action))
                        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) { Text(c.action.describe()) }
                        Section(stringResource(R.string.mode))
                        Choice(listOf(BMode.HOLD to stringResource(R.string.m_hold), BMode.TOGGLE to stringResource(R.string.m_toggle),
                            BMode.TURBO to stringResource(R.string.m_turbo)), c.mode) { c = c.copy(mode = it) }
                        Section(stringResource(R.string.shape))
                        Choice(listOf(Shape.CIRCLE to stringResource(R.string.s_circle), Shape.ROUNDED to stringResource(R.string.s_rect)), c.shape) {
                            c = c.copy(shape = it, h = if (it == Shape.CIRCLE) c.w else c.h)
                        }
                        if (c.mode == BMode.TURBO) LabeledSlider(stringResource(R.string.turbo_speed), c.sens, 0.3f..3f, x) { c = c.copy(sens = it) }
                    }
                    CType.STICK -> {
                        Section(stringResource(R.string.output))
                        Choice(listOf(
                            Out.LEFT_STICK to stringResource(R.string.o_left), Out.RIGHT_STICK to stringResource(R.string.o_right),
                            Out.WASD to "WASD", Out.ARROWS to stringResource(R.string.o_arrows),
                            Out.MOUSE to stringResource(R.string.o_mouse), Out.DPAD to stringResource(R.string.o_dpad),
                        ), c.out) { c = c.copy(out = it) }
                        SwitchRow(stringResource(R.string.floating), c.floating) { c = c.copy(floating = it) }
                        LabeledSlider(stringResource(R.string.deadzone), c.deadzone, 0f..0.5f, pct) { c = c.copy(deadzone = it) }
                        LabeledSlider(stringResource(R.string.sensitivity), c.sens, 0.3f..3f, x) { c = c.copy(sens = it) }
                    }
                    CType.DPAD -> {
                        Section(stringResource(R.string.output))
                        Choice(listOf(Out.DPAD to stringResource(R.string.o_dpad), Out.WASD to "WASD", Out.ARROWS to stringResource(R.string.o_arrows)),
                            c.out) { c = c.copy(out = it) }
                    }
                    CType.TOUCHPAD -> {
                        Section(stringResource(R.string.output))
                        Choice(listOf(Out.MOUSE to stringResource(R.string.o_mouse), Out.RIGHT_STICK to stringResource(R.string.o_camera),
                            Out.LEFT_STICK to stringResource(R.string.o_left)), c.out) { c = c.copy(out = it) }
                        Text(stringResource(if (c.out == Out.MOUSE) R.string.touchpad_hint else R.string.camera_hint),
                            style = MaterialTheme.typography.bodySmall, color = Dim, modifier = Modifier.padding(top = 6.dp))
                        LabeledSlider(stringResource(R.string.sensitivity), c.sens, 0.2f..4f, x) { c = c.copy(sens = it) }
                    }
                }
                val square = c.type != CType.TOUCHPAD && !(c.type == CType.BUTTON && c.shape == Shape.ROUNDED)
                LabeledSlider(stringResource(if (square) R.string.size else R.string.width), c.w, 0.06f..2.4f, pct) {
                    c = if (square) c.copy(w = it, h = it) else c.copy(w = it)
                }
                if (!square) LabeledSlider(stringResource(R.string.height), c.h, 0.06f..1.2f, pct) { c = c.copy(h = it) }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(c) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )

    if (picking) ActionPicker(c.action, onPick = { c = c.copy(action = it); picking = false }, onDismiss = { picking = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActionPicker(initial: Action, onPick: (Action) -> Unit, onDismiss: () -> Unit) {
    var tab by remember {
        mutableIntStateOf(when (initial.kind) {
            Kind.PAD, Kind.LT, Kind.RT -> 0; Kind.KEY -> 1; Kind.MOUSE, Kind.WHEEL_UP, Kind.WHEEL_DOWN -> 2; else -> 3
        })
    }
    var combo by remember { mutableStateOf(if (initial.kind == Kind.KEY) initial.keys else emptyList()) }
    val tabs = listOf(R.string.tab_pad, R.string.tab_keys, R.string.tab_mouse, R.string.tab_special)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = WIDE,
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 720.dp),
        title = { Text(stringResource(R.string.action)) },
        text = {
            Column {
                ScrollableTabRow(selectedTabIndex = tab, edgePadding = 0.dp, containerColor = Color.Transparent) {
                    tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(stringResource(t)) }) }
                }
                Spacer(Modifier.padding(4.dp))
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    when (tab) {
                        0 -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Pad.all.forEach { (bit, name) ->
                                FilterChip(selected = initial.kind == Kind.PAD && initial.code == bit,
                                    onClick = { onPick(Action(Kind.PAD, bit)) }, label = { Text(name) })
                            }
                            FilterChip(selected = initial.kind == Kind.LT, onClick = { onPick(Action(Kind.LT)) }, label = { Text("LT") })
                            FilterChip(selected = initial.kind == Kind.RT, onClick = { onPick(Action(Kind.RT)) }, label = { Text("RT") })
                        }
                        1 -> {
                            Text(stringResource(R.string.combo_hint, combo.joinToString("+") { Keys.name(it) }.ifEmpty { "—" }),
                                style = MaterialTheme.typography.bodySmall, color = Dim)
                            Keys.groups.forEach { g ->
                                Section(g.title)
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    g.keys.forEach { (vk, name) ->
                                        FilterChip(selected = vk in combo, onClick = {
                                            combo = if (vk in combo) combo - vk else (combo + vk).takeLast(4)
                                        }, label = { Text(name) })
                                    }
                                }
                            }
                        }
                        2 -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(1, 2, 3, 4, 5).forEach { b ->
                                val a = Action(Kind.MOUSE, b)
                                FilterChip(selected = initial == a, onClick = { onPick(a) }, label = { Text(a.describe()) })
                            }
                            FilterChip(selected = initial.kind == Kind.WHEEL_UP, onClick = { onPick(Action(Kind.WHEEL_UP)) },
                                label = { Text(stringResource(R.string.wheel_up)) })
                            FilterChip(selected = initial.kind == Kind.WHEEL_DOWN, onClick = { onPick(Action(Kind.WHEEL_DOWN)) },
                                label = { Text(stringResource(R.string.wheel_down)) })
                        }
                        else -> Column {
                            FilterChip(selected = initial.kind == Kind.GYRO, onClick = { onPick(Action(Kind.GYRO)) },
                                label = { Text(stringResource(R.string.a_gyro)) })
                            FilterChip(selected = initial.kind == Kind.TEXT, onClick = { onPick(Action(Kind.TEXT)) },
                                label = { Text(stringResource(R.string.a_text)) })
                            FilterChip(selected = initial.kind == Kind.NONE, onClick = { onPick(Action()) },
                                label = { Text(stringResource(R.string.a_none)) })
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (tab == 1) TextButton(onClick = { onPick(Action(Kind.KEY, keys = combo)) }, enabled = combo.isNotEmpty()) {
                Text(stringResource(R.string.done))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun LayoutSettingsDialog(initial: Layout, onDone: (Layout) -> Unit, onDismiss: () -> Unit) {
    var l by remember { mutableStateOf(initial) }
    val x = { v: Float -> "×%.1f".format(v) }
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = WIDE,
        modifier = Modifier.fillMaxWidth(0.92f).widthIn(max = 720.dp),
        title = { Text(stringResource(R.string.layout_settings)) },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = l.name, onValueChange = { l = l.copy(name = it.take(40)) },
                    label = { Text(stringResource(R.string.name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SwitchRow(stringResource(R.string.portrait), l.portrait) { l = l.copy(portrait = it) }
                SwitchRow(stringResource(R.string.haptics), l.haptics) { l = l.copy(haptics = it) }
                LabeledSlider(stringResource(R.string.opacity), l.opacity, 0.2f..1f, { "${(it * 100).toInt()}%" }) { l = l.copy(opacity = it) }

                Section(stringResource(R.string.gyro))
                Choice(listOf(GyroMode.OFF to stringResource(R.string.off), GyroMode.MOUSE to stringResource(R.string.o_mouse),
                    GyroMode.RIGHT_STICK to stringResource(R.string.o_right)), l.gyro) { l = l.copy(gyro = it) }
                if (l.gyro != GyroMode.OFF) {
                    LabeledSlider(stringResource(R.string.sensitivity), l.gyroSens, 0.2f..4f, x) { l = l.copy(gyroSens = it) }
                    SwitchRow(stringResource(R.string.gyro_always), l.gyroAlways) { l = l.copy(gyroAlways = it) }
                    if (!l.gyroAlways) Text(stringResource(R.string.gyro_hold_hint), style = MaterialTheme.typography.bodySmall, color = Dim)
                    SwitchRow(stringResource(R.string.invert_x), l.gyroInvertX) { l = l.copy(gyroInvertX = it) }
                    SwitchRow(stringResource(R.string.invert_y), l.gyroInvertY) { l = l.copy(gyroInvertY = it) }
                }

                Section(stringResource(R.string.tilt))
                SwitchRow(stringResource(R.string.tilt_on), l.tilt) { l = l.copy(tilt = it) }
                if (l.tilt) {
                    LabeledSlider(stringResource(R.string.tilt_angle), l.tiltAngle, 10f..80f, { "${it.toInt()}°" }) { l = l.copy(tiltAngle = it) }
                    SwitchRow(stringResource(R.string.invert), l.tiltInvert) { l = l.copy(tiltInvert = it) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(l) }) { Text(stringResource(R.string.done)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
