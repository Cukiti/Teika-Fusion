package com.cukiti.teikafusion.ui

import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// EL RELOJ DE LA INTRO (2026-09-30, medido en un Galaxy A15). Las animaciones de Compose y `delay()`
// miden tiempo REAL, y eso tenía dos consecuencias en la intro:
//   · Tras un tirón (montar el menú congelaba ~1,2 s en debug) la animación SALTABA para recuperar el
//     tiempo perdido: la línea se teletransportaba en vez de seguir su camino.
//   · Con la pantalla apagada, los fotogramas se paran pero `delay()` sigue: la firma sonaba a
//     oscuras, la carga empezaba por debajo de una palabra congelada y, al volver, las fases salían
//     descuadradas (pregunta del usuario: «¿qué pasa si el jugador apaga el teléfono de repente?»).
//
// Este reloj avanza FOTOGRAMA A FOTOGRAMA y cada fotograma suma como mucho [MAX_PASO_MS]: si no hay
// fotogramas (pantalla apagada), no avanza; si uno tarda un segundo, la intro solo avanza 34 ms y
// sigue desde donde estaba. Todo lo que se sincroniza en la intro (sonido, fases, mínimos) cuelga de él.
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/** Lo máximo que avanza la intro en un fotograma: dos fotogramas a 60 Hz. */
const val MAX_PASO_MS = 34f

/**
 * Avanza [reloj] (ms de animación) hasta [hastaMs], fotograma a fotograma. [alPaso] recibe el valor
 * nuevo en cada fotograma: ahí se disparan los hitos (sonido, cambios de fase).
 */
suspend fun avanzarReloj(reloj: MutableFloatState, hastaMs: Float, alPaso: (Float) -> Unit = {}) {
    var antes = withFrameNanos { it }
    while (reloj.floatValue < hastaMs) {
        val ahora = withFrameNanos { it }
        val paso = ((ahora - antes) / 1_000_000f).coerceIn(0f, MAX_PASO_MS)
        antes = ahora
        reloj.floatValue = (reloj.floatValue + paso).coerceAtMost(hastaMs)
        alPaso(reloj.floatValue)
    }
}

/** Espera [ms] de tiempo de ANIMACIÓN: se pausa con la pantalla apagada y no se come los tirones. */
suspend fun esperaAnimada(ms: Float) = avanzarReloj(mutableFloatStateOf(0f), ms)
