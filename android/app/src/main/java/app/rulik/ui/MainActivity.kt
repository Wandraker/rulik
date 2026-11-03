package app.rulik.ui

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import app.rulik.R
import app.rulik.RulikApp
import app.rulik.net.ConnState
import app.rulik.net.Target

private sealed interface Screen {
    data object Home : Screen
    data class Play(val id: String, val stream: Boolean) : Screen
    data object Desktop : Screen
    data class Edit(val id: String, val thenPlay: Boolean) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as RulikApp
        val connector = Connector(app)

        // при запуске сразу пробуем ПК, к которому подключались в прошлый раз
        if (savedInstanceState == null && app.client.state.value is ConnState.Idle && app.store.lastHost.isNotEmpty()) {
            app.client.connect(
                Target(app.store.lastHost, app.store.lastPort, app.store.usbMode),
                app.store.pinFor(app.store.lastHost), app.store.deviceName,
            )
        }

        setContent {
            RulikTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                // из редактора возвращаемся в тот же режим, из которого ушли
                var lastStream by remember { mutableStateOf(false) }
                (screen as? Screen.Play)?.let { lastStream = it.stream }
                val layouts by app.store.layouts.collectAsState()
                when (val s = screen) {
                    Screen.Home -> {
                        Fullscreen(false, null)
                        Home(app, connector,
                            onPlay = { id, stream -> app.store.lastLayout = id; screen = Screen.Play(id, stream) },
                            onDesktop = { screen = Screen.Desktop },
                            onEdit = { screen = Screen.Edit(it, false) })
                    }
                    is Screen.Play -> {
                        val l = layouts.firstOrNull { it.id == s.id }
                        if (l == null) screen = Screen.Home else {
                            // картинка с ПК широкая: в стриме всегда альбомная ориентация
                            Fullscreen(true, l.portrait && !s.stream)
                            PlayScreen(l, app, s.stream, onExit = { screen = Screen.Home },
                                onEdit = { screen = Screen.Edit(l.id, true) })
                        }
                    }
                    Screen.Desktop -> {
                        Fullscreen(true, false)
                        DesktopScreen(app, onExit = { screen = Screen.Home })
                    }
                    is Screen.Edit -> {
                        val l = layouts.firstOrNull { it.id == s.id }
                        if (l == null) screen = Screen.Home else {
                            Fullscreen(true, l.portrait)
                            val back = if (s.thenPlay) Screen.Play(l.id, lastStream) else Screen.Home
                            EditorScreen(l, onSave = { app.store.save(it); screen = back }, onClose = { screen = back })
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Fullscreen(on: Boolean, portrait: Boolean?) {
        DisposableEffect(on, portrait) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (on) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                requestedOrientation = if (portrait == true) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            onDispose {}
        }
    }
}

@Composable
private fun Home(app: RulikApp, connector: Connector, onPlay: (String, Boolean) -> Unit, onDesktop: () -> Unit, onEdit: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(if (app.store.lastHost.isEmpty()) 2 else 0) }
    val tabs = listOf(
        Icons.Default.Home to R.string.tab_home,
        Icons.Default.SportsEsports to R.string.layouts,
        Icons.Default.Computer to R.string.tab_pc,
        Icons.Default.Settings to R.string.settings,
    )
    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Icon(icon, null) },
                        label = { Text(stringResource(label)) })
                }
            }
        },
    ) { padding ->
        when (tab) {
            0 -> HomeTab(app, padding, onPlay = onPlay, onDesktop = onDesktop, onOpenPc = { tab = 2 })
            1 -> LayoutsScreen(app, padding, onPlay = { onPlay(it.id, false) }, onEdit = { onEdit(it.id) })
            2 -> ConnectScreen(app, connector, padding)
            else -> SettingsScreen(app, padding)
        }
    }
}
