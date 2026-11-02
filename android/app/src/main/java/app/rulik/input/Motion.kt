package app.rulik.input

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import app.rulik.model.GyroMode
import app.rulik.model.Layout
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Гироприцел и руль наклоном. Оси пересчитываются под поворот экрана,
 * чтобы «повернул телефон вправо» всегда значило «камера вправо».
 */
class Motion(context: Context, private val engine: Engine, private val rotation: () -> Int) : SensorEventListener {
    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroSensor = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val gravitySensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var layout: Layout? = null
    private var lastGyroNs = 0L
    private var gyroWasActive = false

    val hasGyro get() = gyroSensor != null

    fun start(l: Layout) {
        stop()
        layout = l
        if (l.gyro != GyroMode.OFF && gyroSensor != null) {
            sm.registerListener(this, gyroSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        if (l.tilt && gravitySensor != null) {
            sm.registerListener(this, gravitySensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sm.unregisterListener(this)
        engine.clearStick(GYRO)
        engine.clearStick(TILT)
        layout = null
        lastGyroNs = 0L
    }

    override fun onSensorChanged(ev: SensorEvent) {
        val l = layout ?: return
        when (ev.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> gyro(l, ev)
            else -> tilt(l, ev)
        }
    }

    private fun gyro(l: Layout, ev: SensorEvent) {
        val dt = if (lastGyroNs == 0L) 0f else (ev.timestamp - lastGyroNs) / 1e9f
        lastGyroNs = ev.timestamp
        if (dt <= 0f || dt > 0.1f) return
        val active = l.gyroAlways || engine.gyroHeld > 0
        if (!active) {
            if (gyroWasActive) engine.clearStick(GYRO)
            gyroWasActive = false
            return
        }
        gyroWasActive = true
        val gx = ev.values[0]
        val gy = ev.values[1]
        // скорость поворота в рад/с: yaw вправо даёт +x, наклон вверх даёт -y (как мышь)
        var yaw: Float
        var pitch: Float
        when (rotation()) {
            Surface.ROTATION_90 -> { yaw = -gx; pitch = gy }
            Surface.ROTATION_270 -> { yaw = gx; pitch = -gy }
            Surface.ROTATION_180 -> { yaw = gy; pitch = gx }
            else -> { yaw = -gy; pitch = -gx }
        }
        if (l.gyroInvertX) yaw = -yaw
        if (l.gyroInvertY) pitch = -pitch
        // крошечный порог гасит дрожание рук и дрейф датчика
        if (abs(yaw) < 0.015f) yaw = 0f
        if (abs(pitch) < 0.015f) pitch = 0f
        when (l.gyro) {
            GyroMode.MOUSE -> engine.mouseMove(yaw * dt * 1100f * l.gyroSens, pitch * dt * 1100f * l.gyroSens)
            GyroMode.RIGHT_STICK -> engine.stick(GYRO, true, (yaw * 0.45f * l.gyroSens).coerceIn(-1f, 1f),
                (-pitch * 0.45f * l.gyroSens).coerceIn(-1f, 1f))
            GyroMode.OFF -> {}
        }
    }

    private fun tilt(l: Layout, ev: SensorEvent) {
        val x = ev.values[0]
        val y = ev.values[1]
        val angle = when (rotation()) {
            Surface.ROTATION_90 -> atan2(y, x)
            Surface.ROTATION_270 -> atan2(-y, -x)
            Surface.ROTATION_180 -> atan2(x, -y)
            else -> atan2(-x, y)
        }
        var steer = Math.toDegrees(angle.toDouble()).toFloat() / l.tiltAngle.coerceIn(5f, 90f)
        if (l.tiltInvert) steer = -steer
        if (abs(steer) < 0.03f) steer = 0f
        engine.stick(TILT, false, steer.coerceIn(-1f, 1f), 0f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        const val GYRO = "~gyro"
        const val TILT = "~tilt"
    }
}
