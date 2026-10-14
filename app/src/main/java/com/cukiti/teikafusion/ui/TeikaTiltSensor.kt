package com.cukiti.teikafusion.ui

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.cukiti.teikafusion.model.TiltRules

/**
 * Inclinación premium del motor físico Suika: escucha el sensor de GRAVEDAD (fusionado, poco
 * ruido; cae a acelerómetro si no existe) mientras [enabled] y reporta el eje X FILTRADO
 * (paso-bajo) **en marco de PANTALLA** a [onTilt] — el ViewModel lo convierte en gravedad
 * horizontal y los Teikas ruedan hacia donde el jugador inclina el teléfono.
 *
 * El remapeo por rotación del display y las constantes del filtro viven en [TiltRules] (puras y
 * testeadas): aquí solo queda el ciclo de vida del sensor. Ver el KDoc de [TiltRules] para el
 * porqué de cada número — es el fix del bug `nivelbug` (inclinar no movía nada).
 *
 * Ciclo de vida limpio: se registra solo con [enabled] (partida en curso) y al deshabilitar/
 * salir se desregistra Y se reporta 0 (los cuerpos no quedan con una gravedad lateral colgada).
 * Sin permisos: los sensores de movimiento no los requieren.
 */
@Composable
fun TeikaTiltEffect(enabled: Boolean, onTilt: (Float) -> Unit) {
    val context = LocalContext.current
    val currentOnTilt by rememberUpdatedState(onTilt)
    DisposableEffect(enabled) {
        if (!enabled) {
            currentOnTilt(0f)
            return@DisposableEffect onDispose { }
        }
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        // Rotación del display: la app está bloqueada en vertical, pero leerla aquí evita que el día
        // que se desbloquee el eje quede girado 90° sin que nadie relacione la causa.
        val rotationDeg = when (displayRotation(context)) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        var filtered = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val sx = TiltRules.screenX(event.values[0], event.values[1], rotationDeg)
                // Paso-bajo: mata el temblor de la mano sin tragarse el gesto (ver TiltRules).
                filtered += TiltRules.LOW_PASS_ALPHA * (sx - filtered)
                currentOnTilt(filtered)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        }
        onDispose {
            sm.unregisterListener(listener)
            currentOnTilt(0f)
        }
    }
}

/** Rotación actual del display, por la vía disponible en cada API (R+ usa `Context.display`). */
private fun displayRotation(context: Context): Int = try {
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
        context.display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        (context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager)
            .defaultDisplay.rotation
    }
} catch (_: Exception) {
    Surface.ROTATION_0
}
