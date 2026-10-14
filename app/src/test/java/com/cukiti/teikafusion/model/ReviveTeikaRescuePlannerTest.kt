package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class ReviveTeikaRescuePlannerTest {

    /** (2026-09-26) El rescate no crea un tamaño que no hubiera en el tablero. */
    @Test
    fun `dieciseis T1 no se convierten en T4`() {
        val candidates = ReviveTeikaRescuePlanner.candidateTypes(List(16) { TeikaType.TEIKA_1 })
        assertEquals(listOf(TeikaType.TEIKA_1), candidates)
    }

    @Test
    fun `con un T3 en el tablero el rescate llega como mucho a pareja T3`() {
        val candidates = ReviveTeikaRescuePlanner.candidateTypes(listOf(TeikaType.TEIKA_3, TeikaType.TEIKA_3, TeikaType.TEIKA_2))
        assertEquals(TeikaType.TEIKA_3, candidates.first())
        assertTrue(TeikaType.TEIKA_4 !in candidates)
    }

    @Test
    fun `la pareja final nace dentro del tablero y sin contacto`() {
        val placement = ReviveTeikaRescuePlanner.placements(TeikaType.TEIKA_4).first()
        val radius = placement.type.level * 0.5f
        assertTrue(placement.ax >= radius && placement.ay >= radius)
        assertTrue(placement.bx <= GameState.GRID_SIZE - radius)
        assertTrue(placement.by <= GameState.GRID_SIZE - radius)
        assertTrue(hypot(placement.bx - placement.ax, placement.by - placement.ay) > radius * 2f)
    }

    @Test
    fun `un area estrecha rechaza el tier que no puede separar`() {
        val placements = ReviveTeikaRescuePlanner.placements(
            type = TeikaType.TEIKA_4,
            left = 2f,
            top = 2f,
            right = 6f,
            bottom = 6f,
        )
        assertTrue(placements.isEmpty())
    }
}
