package app.rulik.net

import android.content.Context
import android.net.wifi.WifiManager

/**
 * Пока идёт игра, держим Wi-Fi в режиме низкой задержки. Без этого телефон экономит заряд
 * и между пакетами засыпает, а пинг скачет на десятки миллисекунд.
 */
class WifiBoost(context: Context) {
    private val lock = runCatching {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "rulik:play").apply { setReferenceCounted(false) }
    }.getOrNull()

    fun start() {
        runCatching { lock?.acquire() }
    }

    fun stop() {
        runCatching { if (lock?.isHeld == true) lock.release() }
    }
}
