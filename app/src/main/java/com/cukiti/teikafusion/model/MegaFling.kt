package com.cukiti.teikafusion.model

import kotlin.math.pow

/**
 * Coreografía del MEGA TEIKA: **el empuje va ACOPLADO al crecimiento**, no después
 * (rework 2026-07-25, `docs/plan-mega-teika-aparicion.md`).
 *
 * Antes el Mega crecía 1,1 s, se asentaba 120 ms y SOLO ENTONCES empezaba el empuje: el jugador veía
 * un Mega hinchándose **encima** de un tablero lleno y quieto, y luego todo volaba de golpe. Ahora el
 * radio del Mega es lo que dispara cada bloque: cuando `radio(t) >= distancia_al_centro`, ese item
 * arranca su vuelo. Los del centro salen en los primeros frames; los de las esquinas, cuando el radio
 * los alcanza. Al terminar el crecimiento el tablero visual ya está limpio o en vuelo.
 *
 * Todo son funciones puras sobre el reloj en ms para poder testearlas fuera de Compose
 * (`MegaFlingTest`); el VFX solo las evalúa por frame.
 *
 * El «sin flash, anillos ni rótulo» se LEVANTÓ el 2026-09-24 (decisión del usuario): el MEGA llega
 * tras el colapso estelar ([MegaCollapse]) y su nacimiento lleva fogonazo, ondas de choque, rayos y
 * el rótulo «¡MEGA TEIKA!» encima de este empuje, que sigue siendo el corazón del momento.
 */
object MegaFling {

    /** Crecimiento del Mega hasta llenar el tablero (EaseOutCubic: irrumpe fuerte y asienta). */
    const val GROW_MS = 780f
    /** Escala inicial del pop de aparición. */
    const val START_SCALE = 0.12f
    /** Duración del vuelo de CADA item desde que el radio lo alcanza. */
    const val PUSH_MS = 680f
    /** Radio de impacto = `escala * medioTablero * MUL`; >1 para que las esquinas entren dentro. */
    const val IMPACT_RADIUS_MUL = 1.28f
    /** Holgura de contacto: el item sale cuando el borde del Mega roza su celda, no su centro. */
    const val CONTACT_SLACK_CELLS = 0.35f
    /** Distancia recorrida por un item en su vuelo, en fracción del tablero. */
    const val TRAVEL_FRAC = 0.95f

    /** Reloj total del VFX: el último item en ser alcanzado aún tiene su vuelo completo por delante. */
    const val CLOCK_END_MS = GROW_MS + PUSH_MS

    /** Espera tras el crecimiento antes de retirar el Mega — Infinito corre, Niveles celebra. */
    const val HOLD_INFINITE_MS = 260L
    const val HOLD_LEVELS_MS = 1100L
    /** Retirada del Mega (escala + alpha). */
    const val FADE_MS = 300

    /** Escala del Mega en el instante [tMs] (EaseOutCubic de [START_SCALE] a 1). */
    fun growScale(tMs: Float): Float {
        val x = (tMs / GROW_MS).coerceIn(0f, 1f)
        val eased = 1f - (1f - x).pow(3)
        return START_SCALE + (1f - START_SCALE) * eased
    }

    /**
     * Instante (ms) en que el radio del Mega alcanza a un item a distancia [d0] px del centro.
     * Es la inversa de [growScale]: se resuelve analíticamente para precalcularlo UNA vez por item
     * (nada de buscar por frame en el bucle de dibujo).
     */
    fun hitTimeMs(d0: Float, halfBoardPx: Float, slackPx: Float): Float {
        val maxRadius = halfBoardPx * IMPACT_RADIUS_MUL
        if (maxRadius <= 0f) return 0f
        val neededScale = ((d0 - slackPx) / maxRadius).coerceIn(0f, 1f)
        val eased = ((neededScale - START_SCALE) / (1f - START_SCALE)).coerceIn(0f, 1f)
        val x = 1f - (1f - eased).pow(1f / 3f)
        return x * GROW_MS
    }

    /** Progreso 0..1 del vuelo de un item alcanzado en [hitMs], suavizado (smoothstep). */
    fun itemProgress(tMs: Float, hitMs: Float): Float {
        val p = ((tMs - hitMs) / PUSH_MS).coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }

    /** Desvanecido del item en el último tramo de su vuelo. */
    fun itemFade(progress: Float): Float =
        (1f - (progress - 0.55f) / 0.45f).coerceIn(0f, 1f)
}
