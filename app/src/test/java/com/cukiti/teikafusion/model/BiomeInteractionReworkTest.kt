package com.cukiti.teikafusion.model

import org.junit.Assert.*
import org.junit.Test

class BiomeInteractionReworkTest {
    private val footprint = setOf(Position(3, 3))
    private fun move(grid: Map<Position, CellContent>, blocked: Set<Position> = emptySet(),
        protected: Set<Position> = emptySet()) = fearFleeMove(grid, footprint, blocked, protected, 8, 8)

    @Test fun fearStartsAtFirstPlacementAndHasNoCalmPeriod() {
        assertEquals(1, BiomeRhythm.executeTurn(BiomeType.FEAR))
        for (level in 1..LevelMap.totalLevels) assertEquals(0, LevelDirector.fearCalmTurns(level))
    }

    /** P-262: huye el MÁS LEJANO a la ficha (antes el más cercano, que deshacía tu jugada). */
    @Test fun fearMovesDistantWoodBeforeNeighborsAndNeverNewPiece() {
        val near = Position(4, 3)
        val far = Position(0, 0)
        assertEquals(far to Position(1, 0), move(mapOf(
            near to CellContent.Wood(), far to CellContent.Wood(),
            Position(3, 3) to CellContent.Wood(),
        )))
        assertNull(move(mapOf(Position(3, 3) to CellContent.Wood())))
    }

    /** P-262: un bloque de una línea que esta jugada completa no huye nunca. */
    @Test fun fearNeverTouchesTheLinesThisPlacementCompletes() {
        val far = Position(0, 0)
        val grid = mapOf(far to CellContent.Wood(), Position(4, 3) to CellContent.Wood())
        val result = fearFleeMove(grid, footprint, emptySet(), emptySet(), 8, 8, protectedLines = setOf(far))
        assertEquals(Position(4, 3), result!!.first)
    }

    @Test fun fearCanMoveDistantWoodFromFirstTurnOnSparseBoard() {
        val from = Position(0, 0)
        val result = move(mapOf(from to CellContent.Wood()))
        assertNotNull(result)
        assertEquals(from, result!!.first)
        assertTrue(result.second.x in 0..7 && result.second.y in 0..7)
    }

    @Test fun fearNeverMovesProtectedOrArmoredWood() {
        val from = Position(4, 3)
        assertNull(move(mapOf(from to CellContent.Wood(armor = 1))))
        assertNull(move(mapOf(from to CellContent.Wood(coil = 1))))
        assertNull(move(mapOf(from to CellContent.Wood()), protected = setOf(from)))
        assertNull(move(mapOf(from to CellContent.Wood()), blocked = setOf(from)))
    }

    @Test fun fearNeverOverwritesOrEntersBlockedDestinations() {
        val full = (0..7).flatMap { y -> (0..7).map { x -> Position(x, y) to CellContent.Wood() } }.toMap()
        assertNull(move(full))
        val hole = Position(7, 7)
        assertNull(move(full - hole, blocked = setOf(hole)))
        assertEquals(hole, move(full - hole)!!.second)
    }

    @Test fun gardenAlwaysNeedsTwoLinesAndKeepsItsDragBudget() {
        for (level in 1..LevelMap.totalLevels) {
            assertEquals(2, LevelDirector.gardenRescueLines(level))
            assertTrue(LevelDirector.gardenAbductionTurns(level) in 5..7)   // P-352: 7/6/5 → 5/5/4 · P-360: vuelve a 7/6/5
        }
        assertEquals(3, BiomeRhythm.executeTurn(BiomeType.GARDEN))
    }

    @Test fun rescueOffsetStartsAtOldPositionAndSettlesExactlyWithSingleInwardBounce() {
        assertEquals(1f, plantRescueOffsetFraction(0f), 0.0001f)
        assertEquals(0f, plantRescueOffsetFraction(1f), 0.0001f)
        assertTrue(plantRescueOffsetFraction(0.8f) < 0f)
        for (i in 0..100) assertTrue(plantRescueOffsetFraction(i / 100f) in -0.12f..1f)
    }

    /** P-312: el susto no toca una fila a la que le faltan 1-2 casillas. */
    @Test
    fun `el pasaje respeta las lineas casi completas`() {
        // Fila 7 con 6 bloques (le faltan 2) y un bloque suelto lejos, en (0,0).
        val grid = HashMap<Position, CellContent>()
        for (x in 0 until 6) grid[Position(x, 7)] = CellContent.Wood()
        grid[Position(0, 0)] = CellContent.Wood()
        val footprint = setOf(Position(7, 3))
        val result = fearFleeMove(grid, footprint, emptySet(), emptySet(), 8, 8)
        assertEquals(Position(0, 0), result?.first)
        // Y si el suelto no está, no huye nadie de la fila casi hecha.
        grid.remove(Position(0, 0))
        assertEquals(null, fearFleeMove(grid, footprint, emptySet(), emptySet(), 8, 8))
    }
}
