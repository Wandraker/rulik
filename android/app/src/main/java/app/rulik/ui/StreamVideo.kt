package app.rulik.ui

import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.rulik.R
import app.rulik.RulikApp
import app.rulik.net.AudioPlayer
import app.rulik.net.ConnState
import app.rulik.net.Proto
import app.rulik.net.VideoPlayer
import app.rulik.net.VideoStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * Картинка с ПК во весь экран с сохранением пропорций.
 * [onRect] получает, где именно на экране лежит видео: по нему касания переводятся в точки на мониторе.
 */
@Composable
fun StreamVideo(app: RulikApp, modifier: Modifier = Modifier, onRect: (Rect) -> Unit = {}) {
    val client = app.client
    val info by client.streamInfo.collectAsState()
    val state by client.state.collectAsState()
    val player = remember { MutableStateFlow<VideoPlayer?>(null) }
    var sound by remember { mutableStateOf<AudioPlayer?>(null) }
    val current by player.collectAsState()
    val first by remember(current) { current?.firstFrame ?: flowOf(false) }.collectAsState(false)
    val stats by remember(current) { current?.stats ?: flowOf(VideoStats()) }.collectAsState(VideoStats())

    DisposableEffect(Unit) {
        onDispose {
            client.onAudio = null
            sound?.close()
            sound = null
            client.onVideo = null
            client.stopStream()
            player.value?.close()
            player.value = null
        }
    }

    val ratio = info?.takeIf { it.width > 0 && it.height > 0 }?.let { it.width.toFloat() / it.height } ?: (16f / 9f)
    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(h: SurfaceHolder) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                // экран подстроится под частоту стрима: 120 кадров на 120 Гц без пропусков
                                runCatching {
                                    h.surface.setFrameRate(app.store.streamFps.toFloat(), Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE)
                                }
                            }
                            val p = VideoPlayer(h.surface)
                            player.value = p
                            client.onVideo = p::feed
                            if (app.store.streamAudio) {
                                val a = AudioPlayer()
                                sound = a
                                client.onAudio = a::feed
                            }
                            client.startStream(app.store.streamRequest())
                        }

                        override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {}

                        override fun surfaceDestroyed(h: SurfaceHolder) {
                            // свернули приложение: картинку не гоним зря
                            client.onVideo = null
                            client.stopStream()
                            player.value?.close()
                            player.value = null
                            client.onAudio = null
                            sound?.close()
                            sound = null
                        }
                    })
                }
            },
            modifier = Modifier
                .aspectRatio(ratio, matchHeightConstraintsFirst = maxWidth / maxHeight > ratio)
                .onGloballyPositioned { onRect(it.boundsInRoot()) },
        )

        val s = state
        val problem = when {
            s !is ConnState.Connected -> stringResource(R.string.stream_no_pc)
            !s.stream -> stringResource(R.string.stream_old_server)
            info?.status == Proto.SS_NO_FFMPEG -> stringResource(R.string.stream_no_ffmpeg)
            info?.status == Proto.SS_FAILED -> stringResource(R.string.stream_failed)
            else -> null
        }
        if (problem != null || !first) {
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xCC171A21)).padding(20.dp).widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (problem == null) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 3.dp)
                Text(problem ?: stringResource(R.string.stream_waiting), color = Color.White, textAlign = TextAlign.Center)
            }
        }

        if (first && app.store.showPing) {
            Text(
                "${stats.fps} fps · ${"%.1f".format(stats.mbps)} Mbit/s · ${stats.decodeMs} ms" +
                    (if (stats.dropped > 0) " · −${stats.dropped}" else "") +
                    (if (stats.decoder.isNotEmpty()) " · ${stats.decoder}" else ""),
                color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
                    .clip(RoundedCornerShape(6.dp)).background(Color(0x88000000)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
