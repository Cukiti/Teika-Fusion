package com.cukiti.teikafusion.model

/**
 * PUNTUACIÓN POR LÍNEAS ROTAS.
 *
 * Vivía en `InfiniteScoring` por accidente histórico —nació con «La Prensa»—, pero nunca fue una
 * regla de aquel modo: es la que puntúa **todas** las roturas de línea del juego, y `resolveLineClearPass`
 * es el único resolvedor vivo. Al eliminarse WALLBLOCK (2026-08-09) se rescata aquí con el nombre
 * que le corresponde; los números son exactamente los mismos.
 *
 * Puro y sin Android: se prueba en la JVM (ver `LineScoringTest`).
 */
object LineScoring {

    /** Lo que vale una línea suelta. */
    const val LINE = 100

    /**
     * BONUS DE SIMULTANEIDAD. Romper tres líneas de una tacada no vale el triple, vale **más** del
     * triple: es la vía por la que la HABILIDAD mueve el marcador sin gastar turnos de más.
     *
     * 1 → ×1 · 2 → ×2 · 3 → ×3,5 · 4 o más → ×5
     */
    fun simultaneousMultiplier(lines: Int): Float = when {
        lines <= 1 -> 1f
        lines == 2 -> 2f
        lines == 3 -> 3.5f
        else -> 5f
    }

    /** Puntos por romper [lines] líneas en la misma pasada. */
    /**
     * Puntos de romper [lines] líneas a la vez, de las que [pureLines] eran PURAS (un solo color).
     *
     * **La línea pura paga el doble** (2026-09-26, decisión del usuario): vale 2 × [LINE] antes del
     * multiplicador de simultaneidad. Hasta hoy su premio era criar un Teika un tier por encima, y se
     * retiró con la regla nueva de nacimientos (solo una jugada de 2+ líneas cría T2/T3) — el color
     * sigue valiendo la pena, y ahora es lo que dice su cápsula.
     */
    fun lineScore(lines: Int, pureLines: Int = 0): Int {
        if (lines <= 0) return 0
        val base = lines + pureLines.coerceIn(0, lines)
        return (base * LINE * simultaneousMultiplier(lines)).toInt()
    }
}
