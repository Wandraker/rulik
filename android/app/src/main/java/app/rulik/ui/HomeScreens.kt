package app.rulik.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import app.rulik.R
import app.rulik.RulikApp
import app.rulik.model.CType
import app.rulik.model.GyroMode
import app.rulik.model.Layout
import app.rulik.model.Presets
import app.rulik.net.ConnState
import app.rulik.net.Discovery
import app.rulik.net.FoundPc
import app.rulik.net.Reason
import app.rulik.net.Target
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

const val SITE = "https://wandraker.github.io/rulik/"

/** Подключение с учётом PIN: если его нет или он не подошёл, спросим. */
class Connector(private val app: RulikApp) {
    var askPinFor by mutableStateOf<Target?>(null)

    fun connect(target: Target, pinRequired: Boolean = false) {
        val pin = app.store.pinFor(target.host)
        if (pinRequired && pin.isEmpty()) {
            askPinFor = target
            return
        }
        app.store.lastHost = target.host
        app.store.lastPort = target.port
        app.store.usbMode = target.usb
        app.client.connect(target, pin, app.store.deviceName)
    }

    fun submitPin(pin: String) {
        val t = askPinFor ?: return
        app.store.setPin(t.host, pin)
        askPinFor = null
        connect(t)
    }
}

@Composable
internal fun CardBox(content: @Composable () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) { Column(Modifier.padding(16.dp)) { content() } }
}

@Composable
fun ConnectScreen(app: RulikApp, connector: Connector, padding: PaddingValues) {
    val state by app.client.state.collectAsState()
    val ping by app.client.ping.collectAsState()
    val alive by app.client.alive.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var usb by remember { mutableStateOf(app.store.usbMode) }
    var found by remember { mutableStateOf<List<FoundPc>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var manual by remember { mutableStateOf(app.store.lastHost.takeIf { !app.store.usbMode } ?: "") }

    fun scan() {
        if (scanning) return
        scanning = true
        scope.launch {
            found = Discovery.scan()
            scanning = false
        }
    }
    LaunchedEffect(usb) { if (!usb) scan() }

    LaunchedEffect(state) {
        val s = state
        if (s is ConnState.Failed && s.reason == Reason.BAD_PIN) {
            app.store.setPin(s.target.host, "")
            connector.askPinFor = s.target
        }
    }

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("RULIK", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Accent)
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.tagline), color = Dim, modifier = Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(8.dp))

        CardBox {
            val s = state
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = when {
                    s is ConnState.Connected && alive -> Good
                    s is ConnState.Connecting -> Warn
                    else -> Accent
                }
                androidx.compose.foundation.layout.Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (s) {
                            ConnState.Idle -> stringResource(R.string.st_idle)
                            is ConnState.Connecting -> stringResource(R.string.st_connecting, s.target.host)
                            is ConnState.Connected -> stringResource(R.string.st_connected, s.serverName)
                            is ConnState.Failed -> when (s.reason) {
                                Reason.BAD_PIN -> stringResource(if (connector.askPinFor != null) R.string.st_need_pin else R.string.st_bad_pin)
                                Reason.FULL -> stringResource(R.string.st_full)
                                Reason.TIMEOUT -> stringResource(R.string.st_timeout)
                                Reason.NETWORK -> stringResource(R.string.st_network)
                            }
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (s is ConnState.Connected) {
                        Text(
                            (if (s.target.usb) "USB" else "Wi-Fi") + " · " + s.target.host +
                                (if (alive && ping != null) " · $ping ms" else if (!alive) " · " + stringResource(R.string.no_link) else ""),
                            color = Dim, style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                if (s is ConnState.Connecting) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                if (s is ConnState.Connected) TextButton(onClick = { app.client.disconnect() }) { Text(stringResource(R.string.disconnect)) }
            }
            if (s is ConnState.Connected && !s.gamepad) {
                Text(stringResource(R.string.no_driver), color = Warn, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp))
            }
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            SegmentedButton(selected = !usb, onClick = { usb = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Wi-Fi") }
            SegmentedButton(selected = usb, onClick = { usb = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("USB") }
        }

        if (!usb) {
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.pcs_found), Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                    if (scanning) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { scan() }) { Icon(Icons.Default.Refresh, stringResource(R.string.refresh)) }
                }
                if (found.isEmpty() && !scanning) {
                    Text(stringResource(R.string.nothing_found), color = Dim, style = MaterialTheme.typography.bodySmall)
                }
                found.forEach { pc ->
                    Row(
                        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                            .clickable { connector.connect(Target(pc.host, pc.port), pc.pinRequired) }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Computer, null, tint = Accent)
                        Spacer(Modifier.size(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pc.name, fontWeight = FontWeight.SemiBold)
                            Text(pc.host, color = Dim, style = MaterialTheme.typography.bodySmall)
                        }
                        if (pc.pinRequired) Icon(Icons.Default.Lock, null, tint = Dim, modifier = Modifier.size(16.dp))
                    }
                }
            }
            CardBox {
                Text(stringResource(R.string.manual), fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = manual, onValueChange = { manual = it.trim() }, singleLine = true,
                        placeholder = { Text("192.168.1.10") }, modifier = Modifier.weight(1f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Spacer(Modifier.size(8.dp))
                    Button(onClick = {
                        val host = manual.substringBefore(':')
                        val port = manual.substringAfter(':', "").toIntOrNull() ?: app.store.lastPort
                        if (host.isNotEmpty()) connector.connect(Target(host, port))
                    }) { Text(stringResource(R.string.connect)) }
                }
            }
        } else {
            CardBox {
                Text(stringResource(R.string.usb_title), fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.usb_steps), color = Dim, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 8.dp))
                Button(onClick = { connector.connect(Target("127.0.0.1", app.store.lastPort, usb = true)) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.connect_usb))
                }
                Text(stringResource(R.string.usb_tether), color = Dim, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 10.dp))
            }
        }

        Spacer(Modifier.height(16.dp))
    }

    connector.askPinFor?.let { t ->
        PinDialog(t.host, onDone = connector::submitPin, onDismiss = { connector.askPinFor = null })
    }
}

@Composable
fun SettingsScreen(app: RulikApp, padding: PaddingValues) {
    val context = LocalContext.current
    var deviceName by remember { mutableStateOf(app.store.deviceName) }
    var showPing by remember { mutableStateOf(app.store.showPing) }
    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = 8.dp))
        CardBox {
            Text(stringResource(R.string.settings), fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = deviceName, onValueChange = { deviceName = it.take(40); app.store.deviceName = deviceName },
                label = { Text(stringResource(R.string.device_name)) }, singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.show_ping), Modifier.weight(1f))
                Switch(checked = showPing, onCheckedChange = { showPing = it; app.store.showPing = it })
            }
            TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SITE))) }) {
                Text(stringResource(R.string.open_site))
            }
        }

        CardBox {
            Text(stringResource(R.string.aim_title), fontWeight = FontWeight.SemiBold)
            SwitchHint(R.string.aim_snap, R.string.aim_snap_hint, app.store.aimSnap) { app.store.aimSnap = it }
            SwitchHint(R.string.aim_edge, R.string.aim_edge_hint, app.store.aimEdge) { app.store.aimEdge = it }
            SwitchHint(R.string.aim_learn, R.string.aim_learn_hint, app.store.aimLearn) { app.store.aimLearn = it }
            val resetDone = stringResource(R.string.aim_reset_done)
            TextButton(onClick = {
                app.store.resetAim()
                Toast.makeText(context, resetDone, Toast.LENGTH_SHORT).show()
            }) { Text(stringResource(R.string.aim_reset)) }
        }
        StreamSettings(app)
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StreamSettings(app: RulikApp) {
    val store = app.store
    var res by remember { mutableStateOf(store.streamRes) }
    var fps by remember { mutableStateOf(store.streamFps) }
    var mbps by remember { mutableStateOf(store.streamMbps.toFloat()) }
    var alpha by remember { mutableStateOf(store.overlayAlpha) }
    CardBox {
        Text(stringResource(R.string.stream_title), fontWeight = FontWeight.SemiBold)
        SwitchHint(R.string.stream_audio, R.string.stream_audio_hint, store.streamAudio) { store.streamAudio = it }
        Text(stringResource(R.string.stream_res), color = Dim, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
        val resNames = listOf(stringResource(R.string.res_native), "1080p", "720p", "480p")
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            resNames.forEachIndexed { i, name ->
                SegmentedButton(selected = res == i, onClick = { res = i; store.streamRes = i },
                    shape = SegmentedButtonDefaults.itemShape(i, resNames.size)) { Text(name, maxLines = 1) }
            }
        }
        Text(stringResource(R.string.stream_fps), color = Dim, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        val rates = listOf(30, 60, 90, 120)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            rates.forEachIndexed { i, r ->
                SegmentedButton(selected = fps == r, onClick = { fps = r; store.streamFps = r },
                    shape = SegmentedButtonDefaults.itemShape(i, rates.size)) { Text("$r") }
            }
        }
        Text(stringResource(R.string.stream_bitrate, mbps.roundToInt()), modifier = Modifier.padding(top = 12.dp))
        Slider(value = mbps, onValueChange = { mbps = it; store.streamMbps = it.roundToInt() }, valueRange = 3f..50f)
        Text(stringResource(R.string.stream_bitrate_hint), color = Dim, style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.overlay_alpha, (alpha * 100).roundToInt()), modifier = Modifier.padding(top = 12.dp))
        Slider(value = alpha, onValueChange = { alpha = it; store.overlayAlpha = it }, valueRange = 0.15f..1f)
    }
}

/** Переключатель с пояснением мелким текстом. */
@Composable
private fun SwitchHint(title: Int, hint: Int, initial: Boolean, onChange: (Boolean) -> Unit) {
    var on by remember { mutableStateOf(initial) }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(title))
            Text(stringResource(hint), color = Dim, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = on, onCheckedChange = { on = it; onChange(it) })
    }
}

@Composable
private fun PinDialog(host: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pin_title)) },
        text = {
            Column {
                Text(stringResource(R.string.pin_text, host), color = Dim)
                OutlinedTextField(
                    value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    textStyle = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onDone(pin) }, enabled = pin.length >= 4) { Text(stringResource(R.string.connect)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Rulik layout", text))
}

private fun paste(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    return cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString()
}

@Composable
fun LayoutsScreen(app: RulikApp, padding: PaddingValues, onPlay: (Layout) -> Unit, onEdit: (Layout) -> Unit) {
    val layouts by app.store.layouts.collectAsState()
    val context = LocalContext.current
    var deleting by remember { mutableStateOf<Layout?>(null) }
    val copySuffix = stringResource(R.string.copy_suffix)
    val importedMsg = stringResource(R.string.imported)
    val badImportMsg = stringResource(R.string.bad_import)
    val copiedMsg = stringResource(R.string.copied)

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.layouts), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                IconButton(onClick = {
                    val text = paste(context)
                    val l = text?.let { app.store.import(it) }
                    Toast.makeText(context, if (l != null) importedMsg else badImportMsg, Toast.LENGTH_SHORT).show()
                }) { Icon(Icons.Default.ContentPaste, stringResource(R.string.import_clip)) }
                IconButton(onClick = { app.store.restorePresets() }) { Icon(Icons.Default.Restore, stringResource(R.string.restore_presets)) }
                FilledIconButton(onClick = {
                    val l = Presets.blank(app.store.ru)
                    app.store.save(l)
                    onEdit(l)
                }) { Icon(Icons.Default.Add, stringResource(R.string.new_layout)) }
            }
            Spacer(Modifier.height(8.dp))
        }
        items(layouts, key = { it.id }) { l ->
            var menu by remember { mutableStateOf(false) }
            Card(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { onPlay(l) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(l.name, fontWeight = FontWeight.SemiBold)
                        Text(summary(l), color = Dim, style = MaterialTheme.typography.bodySmall)
                    }
                    FilledIconButton(onClick = { onPlay(l) }) { Icon(Icons.Default.PlayArrow, stringResource(R.string.play)) }
                    androidx.compose.foundation.layout.Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.edit_layout)) }, onClick = { menu = false; onEdit(l) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.duplicate)) }, onClick = {
                                menu = false; app.store.duplicate(l, copySuffix)
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.share)) }, onClick = {
                                menu = false
                                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, app.store.export(l))
                                context.startActivity(Intent.createChooser(send, l.name))
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.copy_json)) }, onClick = {
                                menu = false
                                copy(context, app.store.export(l))
                                Toast.makeText(context, copiedMsg, Toast.LENGTH_SHORT).show()
                            })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { menu = false; deleting = l })
                        }
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SITE + "layouts.html"))) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(stringResource(R.string.more_layouts))
            }
        }
    }

    deleting?.let { l ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_q, l.name)) },
            confirmButton = { TextButton(onClick = { app.store.delete(l.id); deleting = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun summary(l: Layout): String {
    val parts = mutableListOf(stringResource(R.string.n_controls, l.controls.size))
    if (l.controls.any { it.type == CType.TOUCHPAD }) parts += stringResource(R.string.t_touchpad).lowercase()
    if (l.gyro != GyroMode.OFF) parts += stringResource(R.string.gyro).lowercase()
    if (l.tilt) parts += stringResource(R.string.tilt).lowercase()
    if (l.portrait) parts += stringResource(R.string.portrait).lowercase()
    return parts.joinToString(" · ")
}
