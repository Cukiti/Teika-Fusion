package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-246 · El Espejo Embrujado: qué refleja, dónde, y qué nunca copia. */
class HauntedMirrorTest {

    private fun wood(vararg cells: Pair<Int, Int>, color: Int = 3): Map<Position, CellContent> =
        cells.associate { (x, y) -> Position(x, y) to (CellContent.Wood(colorType = color) as CellContent) }

    private val allFree: (Position) -> Boolean = { true }

    @Test
    fun `la mitad izquierda cae en espejo sobre la derecha`() {
        val grid = wood(0 to 2, 3 to 5)
        val phantoms = HauntedMirror.phantomsFor(grid, HauntedMirror.LEFT, allFree)
        assertEquals(setOf(Position(7, 2), Position(4, 5)), phantoms.keys)
        assertEquals(3, phantoms[Position(7, 2)])
    }

    @Test
    fun `arriba cae sobre abajo y la otra mitad no se refleja`() {
        val grid = wood(1 to 0, 1 to 7)
        assertEquals(setOf(Position(1, 7)), HauntedMirror.phantomsFor(grid, HauntedMirror.TOP, allFree).keys)
    }

    @Test
    fun `solo en huecos libres`() {
        val grid = wood(0 to 0)
        assertTrue(HauntedMirror.phantomsFor(grid, HauntedMirror.LEFT, free = { it != Position(7, 0) }).isEmpty())
    }

    @Test
    fun `un fantasma no se refleja y el reflejo es madera lisa`() {
        val grid = mapOf<Position, CellContent>(
            Position(0, 0) to CellContent.Wood(colorType = 2, phantom = 1),
            Position(1, 1) to CellContent.Wood(colorType = 5, targetColor = TargetColor.BLUE, poisoned = true),
        )
        val phantoms = HauntedMirror.phantomsFor(grid, HauntedMirror.LEFT, allFree)
        assertEquals(setOf(Position(6, 1)), phantoms.keys)
        val w = HauntedMirror.phantomWood(phantoms.getValue(Position(6, 1)))
        assertEquals(HauntedMirror.PHANTOM_TURNS, w.phantom)
        assertEquals(null, w.targetColor)
        assertFalse(w.poisoned)
    }

    @Test
    fun `elige la mitad que mas fantasmas deja`() {
        // Abajo hay 4 bloques repartidos por las dos mitades; izquierda 2, derecha 3, arriba 1.
        val grid = wood(1 to 6, 6 to 6, 3 to 7, 5 to 7, 6 to 1)
        assertEquals(HauntedMirror.BOTTOM, HauntedMirror.chooseSource(grid, allFree))
    }

    @Test
    fun `sin espejo no hay fantasmas`() {
        assertTrue(HauntedMirror.phantomsFor(wood(0 to 0), HauntedMirror.NONE, allFree).isEmpty())
    }
}
