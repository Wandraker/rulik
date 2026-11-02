package app.rulik.net

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException

data class FoundPc(val name: String, val host: String, val port: Int, val pinRequired: Boolean)

object Discovery {
    /** Широковещательный поиск по всем сетям телефона: Wi-Fi, USB-модем, точка доступа. */
    suspend fun scan(timeoutMs: Long = 1200): List<FoundPc> = withContext(Dispatchers.IO) {
        val found = linkedMapOf<String, FoundPc>()
        runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                socket.soTimeout = 150
                val packet = Proto.discover()
                val targets = broadcastAddresses() + InetAddress.getByName("255.255.255.255")
                val deadline = SystemClock.uptimeMillis() + timeoutMs
                var nextSend = 0L
                val buf = ByteArray(1024)
                while (SystemClock.uptimeMillis() < deadline) {
                    if (SystemClock.uptimeMillis() >= nextSend) {
                        for (addr in targets) {
                            runCatching { socket.send(DatagramPacket(packet, packet.size, addr, Proto.PORT)) }
                        }
                        nextSend = SystemClock.uptimeMillis() + 400
                    }
                    val p = DatagramPacket(buf, buf.size)
                    try {
                        socket.receive(p)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val (type, body) = Proto.parse(buf, p.length) ?: continue
                    if (type != Proto.DISCOVER_REPLY) continue
                    val port = body.short.toInt() and 0xFFFF
                    val flags = body.get().toInt()
                    val name = Proto.readStr(body)
                    val host = p.address.hostAddress ?: continue
                    found[host] = FoundPc(name, host, port, flags and 1 != 0)
                }
            }
        }
        found.values.toList()
    }

    private fun broadcastAddresses(): List<InetAddress> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { it.broadcast }
    }.getOrDefault(emptyList())
}
