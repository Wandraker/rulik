package app.rulik.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.rulik.R
import app.rulik.RulikApp
import app.rulik.net.ConnState

/** Главная: к какому ПК подключены, чем играем и в каком режиме. */
@Composable
fun HomeTab(
    app: RulikApp,
    padding: PaddingValues,
    onPlay: (layoutId: String, stream: Boolean) -> Unit,
    onDesktop: () -> Unit,
    onOpenPc: () -> Unit,
) {
    val state by app.client.state.collectAsState()
    val alive by app.client.alive.collectAsState()
    val ping by app.client.ping.collectAsState()
    val layouts by app.store.layouts.collectAsState()
    var chosen by remember { mutableStateOf(app.store.lastLayout) }
    val layout = layouts.firstOrNull { it.id == chosen } ?: layouts.firstOrNull()
    val s = state
    val connected = s is ConnState.Connected
    val canStream = s is ConnState.Connected && s.stream

    Column(
        Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("RULIK", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = Accent)
            Spacer(Modifier.size(10.dp))
            Text(stringResource(R.string.tagline), color = Dim, modifier = Modifier.padding(bottom = 5.dp))
        }
        Spacer(Modifier.height(8.dp))

        // ПК: нажатие ведёт на вкладку подключения
        CardBox {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenPc), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(
                    when {
                        s is ConnState.Connected && alive -> Good
                        s is ConnState.Connecting -> Warn
                        else -> Accent
                    },
                ))
                Spacer(Modifier.size(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (s) {
                            is ConnState.Connected -> s.serverName
                            is ConnState.Connecting -> stringResource(R.string.st_connecting, s.target.host)
                            else -> stringResource(R.string.st_idle)
                        },
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (s is ConnState.Connected) {
                            (if (s.target.usb) "USB" else "Wi-Fi") + " · " + s.target.host + (if (alive && ping != null) " · $ping ms" else "")
                        } else stringResource(R.string.connect_first),
                        color = Dim, style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!connected) TextButton(onClick = onOpenPc) { Text(stringResource(R.string.connect)) }
            }
        }

        // раскладка, которой будем играть
        LayoutPicker(layouts.map { it.id to it.name }, layout?.id) { chosen = it; app.store.lastLayout = it }

        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeCard(Icons.Default.SportsEsports, R.string.mode_control, R.string.mode_control_hint, connected && layout != null,
                Modifier.weight(1f)) {
                if (!connected) onOpenPc() else layout?.let { onPlay(it.id, false) }
            }
            ModeCard(Icons.Default.Tv, R.string.mode_stream, R.string.mode_stream_hint, canStream && layout != null,
                Modifier.weight(1f)) {
                if (!connected) onOpenPc() else layout?.let { onPlay(it.id, true) }
            }
        }
        if (connected && !canStream) {
            Text(stringResource(R.string.stream_old_hint), color = Warn, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 4.dp))
        }

        Card(
            Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { if (!connected) onOpenPc() else onDesktop() },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DesktopWindows, null, tint = if (canStream) Accent else Dim)
                Spacer(Modifier.size(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.desktop), fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.desktop_hint), color = Dim, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LayoutPicker(items: List<Pair<String, String>>, selected: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Card(
            Modifier.fillMaxWidth().clickable { open = true },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.play_layout), color = Dim, style = MaterialTheme.typography.bodySmall)
                    Text(items.firstOrNull { it.first == selected }?.second ?: "—", fontWeight = FontWeight.SemiBold)
                }
                Icon(Icons.Default.ArrowDropDown, null)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(id) })
            }
        }
    }
}

@Composable
private fun ModeCard(icon: ImageVector, title: Int, hint: Int, enabled: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (enabled) Color(0xFF2A1D1A) else MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(if (enabled) Accent else Color(0xFF353B48)),
                contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(stringResource(title), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(hint), color = Dim, style = MaterialTheme.typography.bodySmall)
        }
    }
}
