package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** El magma del Volcán (2026-09-23): fila que arde, columnas vetadas y filas de magma sin premio. */
class VolcanoMagmaTest {

    @Test
    fun `arde la fila justo bajo la lava y solo con la lava dentro`() {
        assertEquals(2, VolcanoMagma.burningRow(lavaActive = true, lavaRow = 2))
        assertNull(VolcanoMagma.burningRow(lavaActive = true, lavaRow = 0))
        assertNull(VolcanoMagma.burningRow(lavaActive = false, lavaRow = 3))
        // La lava llegó al fondo: no queda fila jugable que arda.
        assertNull(VolcanoMagma.burningRow(lavaActive = true, lavaRow = GameState.GRID_SIZE))
    }

    @Test
    fun `una fila con un solo bloque de magma no da premio`() {
        val row = (0 until 8).map { Position(it, 3) }
        val grid = row.associateWith { CellContent.Wood() as CellContent }.toMutableMap()
        assertFalse(VolcanoMagma.isMagmaLine(row, grid))
        grid[Position(5, 3)] = CellContent.Wood(magma = true)
        assertTrue(VolcanoMagma.isMagmaLine(row, grid))
    }

    @Test
    fun `se funde la madera normal de la fila, no la ya fundida ni las bobinas ni los huecos`() {
        val grid = mapOf(
            Position(0, 2) to CellContent.Wood(),
            Position(1, 2) to CellContent.Wood(magma = true),
            Position(2, 2) to CellContent.Wood(coil = 1),
            Position(3, 2) to CellContent.Empty,
            Position(4, 3) to CellContent.Wood(),
        )
        assertEquals(setOf(Position(0, 2)), VolcanoMagma.forgeable(2, grid))
    }

    @Test
    fun `el calor sube con el tiempo y se topa en uno`() {
        assertEquals(0f, VolcanoMagma.heatFraction(0L))
        assertEquals(0.5f, VolcanoMagma.heatFraction(VolcanoMagma.HEAT_MS / 2), 0.001f)
        assertEquals(1f, VolcanoMagma.heatFraction(VolcanoMagma.HEAT_MS * 3))
    }
}
