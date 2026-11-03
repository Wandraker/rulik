package app.rulik.net

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Звук с ПК: float32 стерео как есть. Пишем без ожидания: если буфер полон, кусок
 * выкидываем. Так задержка звука не растёт, даже когда сеть дёрнулась.
 */
class AudioPlayer {
    private var track: AudioTrack? = null
    private var rate = 0
    private var floats = FloatArray(512)
    @Volatile private var closed = false

    /** Тело пакета AUDIO. Зовётся из сетевого потока. */
    fun feed(body: ByteBuffer) {
        if (closed || body.remaining() < 6) return
        body.short // номер куска: порядок у UDP почти всегда сохраняется, не сортируем
        val r = body.int
        if (r != rate || track == null) open(r)
        val t = track ?: return
        val n = body.remaining() / 4
        if (floats.size < n) floats = FloatArray(n)
        body.slice().order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(floats, 0, n)
        runCatching { t.write(floats, 0, n - n % 2, AudioTrack.WRITE_NON_BLOCKING) }
    }

    @Synchronized
    private fun open(r: Int) {
        close(keepOpen = true)
        if (r !in 8000..192000) return
        val min = AudioTrack.getMinBufferSize(r, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
        // около 60 мс: хватает на дрожание Wi-Fi, но звук не отстаёт от картинки
        val size = maxOf(min, r * 8 * 60 / 1000)
        track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(r).setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
                .setBufferSizeInBytes(size)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build().also { it.play() }
        }.getOrNull()
        rate = r
    }

    @Synchronized
    fun close(keepOpen: Boolean = false) {
        if (!keepOpen) closed = true
        track?.let { runCatching { it.stop() }; runCatching { it.release() } }
        track = null
    }
}
