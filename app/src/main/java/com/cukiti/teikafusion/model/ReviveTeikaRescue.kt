package com.cukiti.teikafusion.model

import kotlin.math.hypot

/** Resultado puro del rescate: dos cuerpos iguales, separados, que el jugador debe unir inclinando. */
data class ReviveTeikaRescuePlacement(
    val type: TeikaType,
    val ax: Float,
    val ay: Float,
    val bx: Float,
    val by: Float,
)

/** Reglas deterministas del rescate de un tablero bloqueado exclusivamente por Teikas físicos. */
object ReviveTeikaRescuePlanner {
    private fun fusionMass(type: TeikaType): Int = when (type) {
        TeikaType.TEIKA_1 -> 1
        TeikaType.TEIKA_2 -> 2
        TeikaType.TEIKA_3 -> 4
        TeikaType.TEIKA_4 -> 8
        // Un Mega persistente solo puede venir de compatibilidad heredada; equivale a dos T4.
        TeikaType.MEGA_TEIKA -> 16
    }

    /** Tiers posibles, de mayor a menor. El rescate elige el primero que además deje una jugada. */
    fun candidateTypes(bodies: List<TeikaType>): List<TeikaType> {
        val mass = bodies.sumOf(::fusionMass)
        // (2026-09-26) El rescate no CREA un tamaño que no hubiera: como mucho, el del cuerpo más
        // grande del tablero. Solo una jugada de 2+ líneas cría Teikas grandes.
        val largest = bodies.maxOfOrNull { it.level } ?: 1
        return listOf(
            TeikaType.TEIKA_4,
            TeikaType.TEIKA_3,
            TeikaType.TEIKA_2,
            TeikaType.TEIKA_1,
        ).filter { mass >= fusionMass(it) * 2 && it.level <= largest }
    }

    /**
     * Dos diagonales opuestas dentro del área jugable. Nunca devuelve cuerpos ya tocándose: la
     * última fusión sigue perteneciendo al jugador y requiere inclinar el teléfono.
     */
    fun placements(
        type: TeikaType,
        left: Float = 0f,
        top: Float = 0f,
        right: Float = GameState.GRID_SIZE.toFloat(),
        bottom: Float = GameState.GRID_SIZE.toFloat(),
    ): List<ReviveTeikaRescuePlacement> {
        val r = type.level * 0.5f
        val minX = left + r
        val maxX = right - r
        val minY = top + r
        val maxY = bottom - r
        if (minX > maxX || minY > maxY) return emptyList()
        val separation = hypot(maxX - minX, maxY - minY)
        if (separation <= r * 2f + 0.35f) return emptyList()
        return listOf(
            ReviveTeikaRescuePlacement(type, minX, minY, maxX, maxY),
            ReviveTeikaRescuePlacement(type, maxX, minY, minX, maxY),
        )
    }
}
