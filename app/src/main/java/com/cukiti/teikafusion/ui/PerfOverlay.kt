package com.cukiti.teikafusion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * **P-371 · El contador de FPS, desde Ajustes** (2026-09-30, decisión del usuario). Antes era una
 * constante en código (`SHOW_FPS_OVERLAY`) y cada playtest de fluidez pedía recompilar.
 *
 * - Disponible directamente en Ajustes y pausa, tanto en debug como en release.
 * - Vive en MEMORIA a propósito, no en DataStore: es una herramienta de medición, no una preferencia
 *   del jugador — así no toca el esquema de preferencias ni el de la nube. Se apaga al cerrar la app.
 * - Se enciende desde Ajustes o desde la pausa (la rejilla de interruptores es la misma) y se pinta
 *   encima de todo, en cualquier pantalla: sirve también para medir el mapa y los menús.
 */
internal const val fpsOverlayAvailable = true

/** ¿Está encendido el contador? Solo tiene efecto si [fpsOverlayAvailable]. */
internal val fpsOverlayOn = mutableStateOf(false)

/**
 * **P-369**: vigila el ritmo de la PARTIDA con [com.cukiti.teikafusion.model.JankWatch] mientras [active]
 * y llama a [onJank] una vez si los tirones son sostenidos. Mismo reloj que [FpsOverlay]
 * (`withFrameNanos`): mide lo que tarda cada fotograma en llegar, que es lo que el jugador nota.
 */
@Composable
fun JankWatchDriver(active: Boolean, onJank: () -> Unit) {
    val onJankNow by androidx.compose.runtime.rememberUpdatedState(onJank)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val watch = com.cukiti.teikafusion.model.JankWatch()
        var last = 0L
        while (true) withFrameNanos { now ->
            if (last != 0L && watch.onFrame(now - last)) onJankNow()
            last = now
        }
    }
}

/**
 * Overlay ligero de FPS basado en [withFrameNanos]. Reporta FPS promedio en ventanas de 0.5 s
 * y el PEOR frame de la ventana (en ms) — el indicador real de *jank*: un pico de 33-50 ms es
 * un tirón visible aunque el promedio se vea bien. Color: verde ≥55, ámbar ≥40, rojo <40.
 */
@Composable
fun FpsOverlay(modifier: Modifier = Modifier) {
    var fps by remember { mutableIntStateOf(0) }
    var worstMs by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        var lastTime = 0L
        var frames = 0
        var accumNs = 0L
        var windowWorst = 0f
        while (true) {
            withFrameNanos { now ->
                if (lastTime != 0L) {
                    val dtNs = now - lastTime
                    val ms = dtNs / 1_000_000f
                    if (ms > windowWorst) windowWorst = ms
                    accumNs += dtNs
                    frames++
                    if (accumNs >= 500_000_000L) {
                        fps = (frames * 1_000_000_000L / accumNs).toInt()
                        worstMs = windowWorst
                        frames = 0; accumNs = 0L; windowWorst = 0f
                    }
                }
                lastTime = now
            }
        }
    }

    val color = when {
        fps >= 55 -> Color(0xFF69F0AE)
        fps >= 40 -> Color(0xFFFFD740)
        else      -> Color(0xFFFF5252)
    }
    Text(
        text = "FPS $fps · peor ${worstMs.roundToInt()}ms",
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
