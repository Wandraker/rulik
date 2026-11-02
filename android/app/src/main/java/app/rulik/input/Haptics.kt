package app.rulik.input

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class Haptics(context: Context) {
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    private val tickEffect = runCatching { VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) }
        .getOrElse { VibrationEffect.createOneShot(12, VibrationEffect.DEFAULT_AMPLITUDE) }
    var enabled = true
    private var rumbling = false
    private var lastTick = 0L

    fun tick() {
        // край кнопки и нажатие соседней могут прийти в одном движении: один щелчок, не два
        val now = SystemClock.uptimeMillis()
        if (now - lastTick < 40) return
        lastTick = now
        if (enabled && !rumbling) runCatching { vibrator?.vibrate(tickEffect) }
    }

    /** Вибрация из игры: моторы Xbox 0..255. */
    fun rumble(large: Int, small: Int) {
        val v = vibrator ?: return
        val level = maxOf(large, small)
        runCatching {
            if (level == 0) {
                rumbling = false
                v.cancel()
            } else {
                rumbling = true
                val amp = if (v.hasAmplitudeControl()) level.coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
                // игра сама пришлёт ноль, когда хватит; потолок 5 секунд на случай потери пакета
                v.vibrate(VibrationEffect.createOneShot(5000, amp))
            }
        }
    }

    fun stop() {
        rumbling = false
        runCatching { vibrator?.cancel() }
    }
}
