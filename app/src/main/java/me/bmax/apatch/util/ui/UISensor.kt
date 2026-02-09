package me.bmax.apatch.util.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

@Composable
fun rememberUISensor(): UISensor? {
    val context = LocalContext.current
    val uiSensor = remember {
        runCatching { UISensor(context) }.getOrNull()
    }

    DisposableEffect(uiSensor) {
        if (uiSensor != null) {
            uiSensor.start()
            onDispose { uiSensor.stop() }
        } else {
            onDispose { /* Nothing to dispose */ }
        }
    }

    return uiSensor
}

class UISensor(context: Context) {
    var gravityAngle: Float by mutableFloatStateOf(45f)
        private set
    var gravity: Offset by mutableStateOf(Offset.Zero)
        private set

    private var isRegistered = false
    private var lastUpdateTime = 0L
    private val minUpdateInterval = 16L // ms

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event == null ||
                event.sensor.type != Sensor.TYPE_ACCELEROMETER ||
                accelerometer == null
            ) return

            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime < minUpdateInterval) return
            lastUpdateTime = currentTime

            val (x, y, z) = event.values
            val norm = sqrt(x * x + y * y + z * z)

            if (norm == 0f) return

            val nx = x / norm
            val ny = y / norm

            val angle = atan2(ny, nx) * (180f / PI).toFloat()
            val normalizedAngle = ((angle % 360f) + 360f) % 360f

            val alpha = 0.5f
            gravityAngle = gravityAngle * (1f - alpha) + normalizedAngle * alpha
            gravity = gravity * (1f - alpha) + Offset(nx, ny) * alpha
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun start() {
        if (isRegistered || accelerometer == null || sensorManager == null) return

        try {
            sensorManager.registerListener(
                listener,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
            isRegistered = true
        } catch (e: Exception) {
            Log.e("UISensor", "Failed to start sensor", e)
        }
    }

    fun stop() {
        if (isRegistered && sensorManager != null) {
            try {
                sensorManager.unregisterListener(listener)
            } catch (e: Exception) {
                Log.e("UISensor", "Error unregistering sensor", e)
            }
            isRegistered = false
        }
    }
}