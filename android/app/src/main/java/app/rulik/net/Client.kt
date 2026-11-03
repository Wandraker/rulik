package app.rulik.net

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

data class Target(val host: String, val port: Int = Proto.PORT, val usb: Boolean = false)

sealed interface ConnState {
    data object Idle : ConnState
    data class Connecting(val target: Target) : ConnState
    data class Connected(val target: Target, val serverName: String, val gamepad: Boolean, val stream: Boolean = false) : ConnState
    data class Failed(val target: Target, val reason: Reason, val detail: String = "") : ConnState
}

enum class Reason { BAD_PIN, FULL, TIMEOUT, NETWORK }

data class StreamInfo(val status: Int, val width: Int, val height: Int, val fps: Int, val encoder: String)

private interface Transport {
    fun send(data: ByteArray)
    /** Блокирующее чтение одного пакета. null: соединение закрыто. */
    fun receive(): ByteArray?
    fun close()
}

private class UdpTransport(target: Target) : Transport {
    private val socket = DatagramSocket().apply {
        connect(InetSocketAddress(InetAddress.getByName(target.host), target.port))
        trafficClass = 0xB8 // DSCP EF: роутеры с QoS пропускают такие пакеты первыми
        receiveBufferSize = 4 shl 20 // ключевой кадр видео прилетает пачкой в сотню пакетов
    }
    private val buf = ByteArray(2048)

    override fun send(data: ByteArray) = socket.send(DatagramPacket(data, data.size))

    override fun receive(): ByteArray? {
        val p = DatagramPacket(buf, buf.size)
        socket.receive(p)
        return buf.copyOf(p.length)
    }

    override fun close() = socket.close()
}

private class TcpTransport(target: Target) : Transport {
    private val socket = Socket().apply {
        tcpNoDelay = true
        connect(InetSocketAddress(target.host, target.port), 2000)
    }
    private val out = socket.getOutputStream()
    private val input = DataInputStream(socket.getInputStream())

    override fun send(data: ByteArray) {
        val frame = ByteBuffer.allocate(2 + data.size).order(ByteOrder.LITTLE_ENDIAN)
            .putShort(data.size.toShort()).put(data).array()
        out.write(frame)
    }

    override fun receive(): ByteArray? {
        val lo = input.read()
        val hi = input.read()
        if (lo < 0 || hi < 0) return null
        val data = ByteArray(lo or (hi shl 8))
        input.readFully(data)
        return data
    }

    override fun close() = socket.close()
}

/**
 * Соединение с Rulik Server. Отправка идёт через отдельный поток с очередью,
 * чтобы касания никогда не ждали сеть.
 */
class Client {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<ConnState>(ConnState.Idle)
    val state: StateFlow<ConnState> = _state
    private val _ping = MutableStateFlow<Int?>(null)
    val ping: StateFlow<Int?> = _ping
    private val _alive = MutableStateFlow(false)
    val alive: StateFlow<Boolean> = _alive

    var onRumble: ((large: Int, small: Int) -> Unit)? = null
    /** Вызывается каждые 250 мс: движок досылает состояние (зажатые клавиши, геймпад). */
    var onHeartbeat: (() -> Unit)? = null
    /** Кусок кадра видео (тело пакета VIDEO). Зовётся из сетевого потока. */
    @Volatile var onVideo: ((ByteBuffer) -> Unit)? = null
    /** Кусок звука (тело пакета AUDIO). */
    @Volatile var onAudio: ((ByteBuffer) -> Unit)? = null
    private val _streamInfo = MutableStateFlow<StreamInfo?>(null)
    val streamInfo: StateFlow<StreamInfo?> = _streamInfo

    /** Пока задан, повторяем его раз в секунду: так ПК не забудет, что мы смотрим, даже после перезапуска. */
    @Volatile private var streamRequest: ByteArray? = null
    private var beats = 0

    fun startStream(request: ByteArray) {
        _streamInfo.value = null
        streamRequest = request
        send(request)
    }

    fun stopStream() {
        if (streamRequest == null) return
        streamRequest = null
        send(Proto.streamStop())
        _streamInfo.value = null
    }

    @Volatile private var transport: Transport? = null
    private val queue = LinkedBlockingQueue<ByteArray>(512)
    private var jobs = listOf<Job>()
    private var pin = ""
    private var deviceName = ""
    @Volatile private var lastPong = 0L
    @Volatile private var welcome: Int? = null

    val connected get() = _state.value is ConnState.Connected

    fun send(data: ByteArray) {
        if (transport != null) queue.offer(data)
    }

    fun connect(target: Target, pin: String, deviceName: String) {
        disconnect()
        this.pin = pin
        this.deviceName = deviceName
        _state.value = ConnState.Connecting(target)
        scope.launch {
            val t = try {
                if (target.usb) TcpTransport(target) else UdpTransport(target)
            } catch (e: Exception) {
                _state.value = ConnState.Failed(target, Reason.NETWORK, e.message ?: "")
                return@launch
            }
            transport = t
            welcome = null
            queue.clear()
            jobs = listOf(launch { senderLoop(t) }, launch { receiverLoop(t, target) })
            var caps = 0
            for (attempt in 0 until 6) {
                t.trySend(Proto.hello(pin, deviceName))
                val deadline = SystemClock.uptimeMillis() + 500
                while (welcome == null && SystemClock.uptimeMillis() < deadline) delay(10)
                if (welcome != null) break
            }
            val status = welcome
            when (status) {
                null -> fail(target, Reason.TIMEOUT)
                Proto.ST_BAD_PIN -> fail(target, Reason.BAD_PIN)
                Proto.ST_FULL -> fail(target, Reason.FULL)
                else -> {
                    caps = welcomeCaps
                    lastPong = SystemClock.uptimeMillis()
                    _alive.value = true
                    _state.value = ConnState.Connected(target, welcomeName, caps and Proto.CAP_GAMEPAD != 0,
                        caps and Proto.CAP_STREAM != 0)
                    jobs = jobs + launch { heartbeatLoop() }
                }
            }
        }
    }

    private fun fail(target: Target, reason: Reason) {
        closeTransport()
        _state.value = ConnState.Failed(target, reason)
    }

    fun disconnect() {
        streamRequest = null
        transport?.trySend(Proto.bye())
        closeTransport()
        _state.value = ConnState.Idle
    }

    private fun closeTransport() {
        jobs.forEach { it.cancel() }
        jobs = emptyList()
        transport?.let { runCatching { it.close() } }
        transport = null
        _alive.value = false
        _ping.value = null
    }

    private fun Transport.trySend(data: ByteArray) {
        runCatching { send(data) }
    }

    private suspend fun senderLoop(t: Transport) {
        val ctx = kotlin.coroutines.coroutineContext
        while (ctx.isActive) {
            val data = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
            try {
                t.send(data)
            } catch (_: Exception) {
                // у UDP это обычно «сеть недоступна» на долю секунды, просто идём дальше
            }
        }
    }

    @Volatile private var welcomeName = ""
    @Volatile private var welcomeCaps = 0

    private suspend fun receiverLoop(t: Transport, target: Target) {
        val ctx = kotlin.coroutines.coroutineContext
        while (ctx.isActive) {
            val data = try {
                t.receive()
            } catch (_: Exception) {
                if (!ctx.isActive) return
                if (target.usb) null else { delay(50); continue }
            }
            if (data == null) {
                if (_state.value is ConnState.Connected) {
                    closeTransport()
                    _state.value = ConnState.Failed(target, Reason.NETWORK)
                }
                return
            }
            val (type, body) = Proto.parse(data) ?: continue
            when (type) {
                Proto.WELCOME -> {
                    val status = body.get().toInt() and 0xFF
                    welcomeCaps = body.get().toInt() and 0xFF
                    welcomeName = Proto.readStr(body)
                    if (status == Proto.ST_REHELLO) {
                        // ПК перезапустили, здороваемся заново без шума для пользователя
                        t.trySend(Proto.hello(pin, deviceName))
                    } else {
                        welcome = status
                        val s = _state.value
                        if (s is ConnState.Connected && status == Proto.ST_OK) {
                            _state.value = s.copy(gamepad = welcomeCaps and Proto.CAP_GAMEPAD != 0,
                                stream = welcomeCaps and Proto.CAP_STREAM != 0)
                            streamRequest?.let { t.trySend(it) }
                        }
                    }
                }
                Proto.PONG -> {
                    val sent = body.int
                    val now = (SystemClock.uptimeMillis() and 0x7fffffff).toInt()
                    lastPong = SystemClock.uptimeMillis()
                    _alive.value = true
                    _ping.value = (now - sent).coerceAtLeast(0)
                }
                Proto.VIDEO -> onVideo?.invoke(body)
                Proto.AUDIO -> onAudio?.invoke(body)
                Proto.STREAM_INFO -> {
                    val status = body.get().toInt() and 0xFF
                    val w = body.short.toInt() and 0xFFFF
                    val h = body.short.toInt() and 0xFFFF
                    val fps = body.get().toInt() and 0xFF
                    _streamInfo.value = StreamInfo(status, w, h, fps, Proto.readStr(body))
                }
                Proto.RUMBLE -> {
                    val large = body.get().toInt() and 0xFF
                    val small = body.get().toInt() and 0xFF
                    onRumble?.invoke(large, small)
                }
                Proto.BYE -> {
                    closeTransport()
                    _state.value = ConnState.Idle
                    return
                }
            }
        }
    }

    private suspend fun heartbeatLoop() {
        val ctx = kotlin.coroutines.coroutineContext
        while (ctx.isActive) {
            val now = SystemClock.uptimeMillis()
            send(Proto.ping((now and 0x7fffffff).toInt(), _ping.value ?: 0))
            onHeartbeat?.invoke()
            if (++beats % 4 == 0) streamRequest?.let { send(it) }
            if (now - lastPong > 2500) {
                _alive.value = false
                _ping.value = null
            }
            delay(250)
        }
    }
}
