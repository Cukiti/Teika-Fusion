package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-294 · LA ESPADA DEL REY: a qué línea apunta, qué parte y qué la envaina. */
class KingSwordTest {

    private fun wood(vararg cells: Pair<Int, Int>): Map<Position, CellContent> =
        cells.associate { (x, y) -> Position(x, y) to CellContent.Wood() }

    @Test
    fun `señala la linea con mas bloques`() {
        val grid = wood(0 to 5, 1 to 5, 2 to 5, 3 to 1, 3 to 2)
        assertEquals(KingSword.ROW to 5, KingSword.choose(grid))
        assertEquals(3, KingSword.damage(KingSword.ROW, 5, grid))
    }

    @Test
    fun `sin bloques no alza la espada`() {
        assertEquals(KingSword.NONE to -1, KingSword.choose(emptyMap()))
    }

    @Test
    fun `una columna es su linea vertical`() {
        assertEquals((0 until 8).map { Position(2, it) }, KingSword.cells(KingSword.COL, 2))
        assertTrue(KingSword.cells(KingSword.NONE, 2).isEmpty())
    }

    /** Cualquier línea que CRUCE la señalada la envaina; una paralela, no. */
    @Test
    fun `romper una linea que la cruza la envaina`() {
        val columna3 = (0 until 8).map { Position(3, it) }
        assertTrue(KingSword.sheathedBy(KingSword.ROW, 4, columna3))
        val fila6 = (0 until 8).map { Position(it, 6) }
        assertFalse(KingSword.sheathedBy(KingSword.ROW, 4, fila6))
        assertFalse(KingSword.sheathedBy(KingSword.NONE, 4, columna3))
    }
}
