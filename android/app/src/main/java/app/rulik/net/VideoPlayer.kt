package app.rulik.net

import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.nio.ByteBuffer
import java.util.ArrayDeque

data class VideoStats(val fps: Int = 0, val mbps: Float = 0f, val decodeMs: Int = 0, val dropped: Int = 0)

/**
 * Собирает кадры из пакетов VIDEO и сразу отдаёт их аппаратному декодеру.
 * Ничего не буферизуем: кадр показывается, как только декодер его выдал.
 * Если кусок кадра потерялся, ждём следующий ключевой кадр, чтобы не показывать мусор.
 */
class VideoPlayer(private val surface: Surface) {
    private class Frame(val id: Int, count: Int, val key: Boolean, val arrived: Long) {
        val parts = arrayOfNulls<ByteArray>(count)
        var got = 0
        var size = 0
    }

    private val thread = HandlerThread("rulik-video").apply { start() }
    private val handler = Handler(thread.looper)
    private val lock = Object()
    private var codec: MediaCodec? = null
    private var configured: Pair<Int, Int>? = null
    private val freeInputs = ArrayDeque<Int>()
    private val ready = ArrayDeque<Frame>()
    private val building = HashMap<Int, Frame>()
    private var lastDone = Int.MIN_VALUE
    private var waitKey = true
    @Volatile private var closed = false

    private val _stats = MutableStateFlow(VideoStats())
    val stats: StateFlow<VideoStats> = _stats
    private val _firstFrame = MutableStateFlow(false)
    /** Хотя бы один кадр уже на экране. */
    val firstFrame: StateFlow<Boolean> = _firstFrame

    private var winStart = SystemClock.uptimeMillis()
    private var winFrames = 0
    private var winBytes = 0L
    private var winDecode = 0L
    private var dropped = 0

    /** Тело пакета VIDEO. Зовётся из сетевого потока. */
    fun feed(body: ByteBuffer) {
        if (closed || body.remaining() < 13) return
        val id = body.int
        val idx = body.short.toInt() and 0xFFFF
        val count = body.short.toInt() and 0xFFFF
        val flags = body.get().toInt() and 0xFF
        body.int // метка времени ПК: часы разные, для задержки не годится
        if (count == 0 || idx >= count) return
        synchronized(lock) {
            if (lastDone != Int.MIN_VALUE && id - lastDone <= 0) return // опоздавший кусок старого кадра
            val f = building.getOrPut(id) { Frame(id, count, flags and 1 != 0, SystemClock.uptimeMillis()) }
            if (f.parts.size != count || f.parts[idx] != null) return
            val chunk = ByteArray(body.remaining())
            body.get(chunk)
            f.parts[idx] = chunk
            f.got++
            f.size += chunk.size
            if (f.got < count) return
            complete(f)
        }
    }

    private fun complete(f: Frame) {
        building.remove(f.id)
        // всё, что старше готового кадра и не собралось, уже не нужно
        val stale = building.keys.filter { it - f.id < 0 }
        if (stale.isNotEmpty()) {
            stale.forEach { building.remove(it) }
            dropped += stale.size
            waitKey = true
        }
        if (lastDone != Int.MIN_VALUE && f.id - lastDone > 1 && stale.isEmpty()) {
            dropped += f.id - lastDone - 1
            waitKey = true
        }
        lastDone = f.id
        if (waitKey && !f.key) return
        waitKey = false
        if (f.key) {
            // поменяли качество в настройках: кадр другого размера, декодер создаём заново
            val size = H264.parameterSets(join(f)).first?.let { H264.size(it) }
            if (codec != null && size != null && size != configured) {
                codec?.let { runCatching { it.stop() }; runCatching { it.release() } }
                codec = null
                freeInputs.clear()
                ready.clear()
            }
        }
        if (codec == null) start(f)
        // декодер не успевает: старые кадры выкидываем, свежий важнее
        if (ready.size >= 2) {
            dropped += ready.size
            ready.clear()
            if (!f.key) {
                waitKey = true
                return
            }
        }
        ready.addLast(f)
        winBytes += f.size
        pump()
    }

    private fun start(first: Frame) {
        val data = join(first)
        val (sps, pps) = H264.parameterSets(data)
        val size = sps?.let { H264.size(it) } ?: (1920 to 1080)
        configured = size
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, size.first, size.second).apply {
            sps?.let { setByteBuffer("csd-0", ByteBuffer.wrap(it)) }
            pps?.let { setByteBuffer("csd-1", ByteBuffer.wrap(it)) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
            setInteger(MediaFormat.KEY_PRIORITY, 0)
            setInteger(MediaFormat.KEY_OPERATING_RATE, Short.MAX_VALUE.toInt())
            // декодеры Qualcomm без этого держат несколько кадров у себя
            setInteger("vendor.qti-ext-dec-low-latency.enable", 1)
        }
        val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        c.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                synchronized(lock) {
                    if (codec !== this@VideoPlayer.codec) return // старый декодер после смены размера
                    freeInputs.addLast(index)
                    pump()
                }
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                if (closed) return
                runCatching { codec.releaseOutputBuffer(index, true) }
                val now = SystemClock.uptimeMillis()
                synchronized(lock) {
                    winFrames++
                    winDecode += now - info.presentationTimeUs / 1000
                    val span = now - winStart
                    if (span >= 1000) {
                        _stats.value = VideoStats(
                            fps = (winFrames * 1000 / span).toInt(),
                            mbps = winBytes * 8f / 1000f / span,
                            decodeMs = if (winFrames > 0) (winDecode / winFrames).toInt() else 0,
                            dropped = dropped,
                        )
                        winStart = now; winFrames = 0; winBytes = 0; winDecode = 0
                    }
                }
                _firstFrame.value = true
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                synchronized(lock) { waitKey = true }
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {}
        }, handler)
        c.configure(format, surface, null, 0)
        c.start()
        codec = c
    }

    private fun pump() {
        val c = codec ?: return
        while (freeInputs.isNotEmpty() && ready.isNotEmpty()) {
            val index = freeInputs.pollFirst()!!
            val f = ready.pollFirst()!!
            try {
                val buf = c.getInputBuffer(index) ?: continue
                buf.clear()
                f.parts.forEach { buf.put(it!!) }
                // время прихода первого куска: по нему считаем, сколько кадр шёл до экрана
                c.queueInputBuffer(index, 0, f.size, f.arrived * 1000, if (f.key) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
            } catch (_: Exception) {
                waitKey = true
            }
        }
    }

    private fun join(f: Frame): ByteArray {
        val out = ByteArray(f.size)
        var pos = 0
        f.parts.forEach { System.arraycopy(it!!, 0, out, pos, it.size); pos += it.size }
        return out
    }

    fun close() {
        closed = true
        synchronized(lock) {
            codec?.let { runCatching { it.stop() }; runCatching { it.release() } }
            codec = null
            building.clear()
            ready.clear()
            freeInputs.clear()
        }
        thread.quitSafely()
    }
}

/** Самое нужное из H.264: найти SPS/PPS и вытащить размер кадра из SPS. */
object H264 {
    /** NAL-юниты из потока Annex B, без стартовых кодов. */
    fun nals(data: ByteArray): List<ByteArray> {
        val starts = mutableListOf<Pair<Int, Int>>() // начало кода, начало NAL
        var i = 0
        while (i + 3 <= data.size) {
            if (data[i].toInt() == 0 && data[i + 1].toInt() == 0) {
                if (data[i + 2].toInt() == 1) { starts += i to i + 3; i += 3; continue }
                if (i + 4 <= data.size && data[i + 2].toInt() == 0 && data[i + 3].toInt() == 1) { starts += i to i + 4; i += 4; continue }
            }
            i++
        }
        return starts.mapIndexed { n, (_, from) ->
            val to = if (n + 1 < starts.size) starts[n + 1].first else data.size
            data.copyOfRange(from, to)
        }
    }

    /** SPS и PPS со стартовым кодом, как их ждёт MediaCodec в csd-0 и csd-1. */
    fun parameterSets(data: ByteArray): Pair<ByteArray?, ByteArray?> {
        var sps: ByteArray? = null
        var pps: ByteArray? = null
        val start = byteArrayOf(0, 0, 0, 1)
        for (nal in nals(data)) {
            when (nal.firstOrNull()?.toInt()?.and(0x1F)) {
                7 -> if (sps == null) sps = start + nal
                8 -> if (pps == null) pps = start + nal
            }
        }
        return sps to pps
    }

    /** Ширина и высота из SPS (со стартовым кодом или без). null, если разобрать не вышло. */
    fun size(spsWithStart: ByteArray): Pair<Int, Int>? = runCatching {
        val nal = nals(spsWithStart).firstOrNull() ?: spsWithStart
        val r = Bits(unescape(nal), 8)
        val profile = r.u(8)
        r.u(16) // constraint flags, level
        r.ue()  // sps id
        var chroma = 1
        if (profile in setOf(100, 110, 122, 244, 44, 83, 86, 118, 128, 138, 139, 134, 135)) {
            chroma = r.ue()
            if (chroma == 3) r.u(1)
            r.ue(); r.ue(); r.u(1)
            if (r.u(1) == 1) {
                repeat(if (chroma != 3) 8 else 12) { k ->
                    if (r.u(1) == 1) {
                        var last = 8; var next = 8
                        repeat(if (k < 6) 16 else 64) {
                            if (next != 0) next = (last + r.se() + 256) % 256
                            if (next != 0) last = next
                        }
                    }
                }
            }
        }
        r.ue() // log2_max_frame_num
        when (r.ue()) {
            0 -> r.ue()
            1 -> { r.u(1); r.se(); r.se(); repeat(r.ue()) { r.se() } }
        }
        r.ue(); r.u(1)
        val wMbs = r.ue() + 1
        val hUnits = r.ue() + 1
        val frameMbsOnly = r.u(1)
        if (frameMbsOnly == 0) r.u(1)
        r.u(1)
        var w = wMbs * 16
        var h = hUnits * 16 * (2 - frameMbsOnly)
        if (r.u(1) == 1) {
            val cx = if (chroma == 0) 1 else 2
            val cy = (if (chroma == 1) 2 else 1) * (2 - frameMbsOnly)
            val l = r.ue(); val rr = r.ue(); val t = r.ue(); val b = r.ue()
            w -= (l + rr) * cx
            h -= (t + b) * cy
        }
        w to h
    }.getOrNull()

    private fun unescape(nal: ByteArray): ByteArray {
        val out = java.io.ByteArrayOutputStream(nal.size)
        var zeros = 0
        for (b in nal) {
            if (zeros >= 2 && b.toInt() == 3) { zeros = 0; continue }
            out.write(b.toInt())
            zeros = if (b.toInt() == 0) zeros + 1 else 0
        }
        return out.toByteArray()
    }

    private class Bits(val d: ByteArray, var pos: Int) {
        fun u(n: Int): Int {
            var v = 0
            repeat(n) {
                val bit = (d[pos ushr 3].toInt() ushr (7 - (pos and 7))) and 1
                v = (v shl 1) or bit
                pos++
            }
            return v
        }

        fun ue(): Int {
            var zeros = 0
            while (u(1) == 0) zeros++
            return (1 shl zeros) - 1 + if (zeros > 0) u(zeros) else 0
        }

        fun se(): Int {
            val k = ue()
            return if (k and 1 == 1) (k + 1) / 2 else -(k / 2)
        }
    }
}
