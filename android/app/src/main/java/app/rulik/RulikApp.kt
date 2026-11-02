package app.rulik

import android.app.Application
import app.rulik.data.Store
import app.rulik.input.Engine
import app.rulik.input.Haptics
import app.rulik.net.Client

class RulikApp : Application() {
    lateinit var store: Store
        private set
    val client = Client()
    val engine = Engine(client::send)
    lateinit var haptics: Haptics
        private set

    override fun onCreate() {
        super.onCreate()
        store = Store(this)
        haptics = Haptics(this)
        client.onRumble = { large, small -> haptics.rumble(large, small) }
        client.onHeartbeat = { engine.heartbeat() }
    }
}
