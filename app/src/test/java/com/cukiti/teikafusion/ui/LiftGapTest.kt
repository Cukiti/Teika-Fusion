package com.cukiti.teikafusion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-448: la ficha sube alta al salir de la bandeja y baja de forma continua hasta lo alto del tablero. */
class LiftGapTest {
    private val boardTop = 400f
    private val trayTop = 1400f

    @Test
    fun `alta en la bandeja y baja arriba`() {
        assertEquals(FINGER_LIFT_CELLS, liftGapCells(trayTop + 50f, boardTop, trayTop), 1e-4f)
        assertEquals(FINGER_LIFT_MIN_CELLS, liftGapCells(boardTop - 50f, boardTop, trayTop), 1e-4f)
    }

    @Test
    fun `sin saltos por el camino`() {
        var prev = liftGapCells(trayTop, boardTop, trayTop)
        var y = trayTop
        while (y > boardTop) {
            y -= 10f
            val now = liftGapCells(y, boardTop, trayTop)
            assertTrue("sube o salta en y=$y", now <= prev && prev - now < 0.05f)
            prev = now
        }
    }

    @Test
    fun `sin medidas todavia se queda la de siempre`() {
        assertEquals(FINGER_LIFT_CELLS, liftGapCells(500f, 0f, 0f), 1e-4f)
    }
}
