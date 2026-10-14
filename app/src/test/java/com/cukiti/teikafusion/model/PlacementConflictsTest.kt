package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El aviso de «aquí no cabe» (P-16). Nació para cazar el rechazo mudo de las celdas de color
 * (auditoría del 2026-09-16); retiradas estas el 2026-09-22, defiende lo que queda: que toda causa
 * de rechazo tenga su aro.
 */
class PlacementConflictsTest {
    private val origin = Position(0, 0)
    private val marked = Position(2, 3)
    private val piece = Piece(shape = listOf(origin, Position(1, 0)), colorType = 0)
    private val state = GameState()

    private fun conflicts(
        s: GameState = state,
        p: Piece = piece,
        at: Position = marked,
        blocked: Set<Position> = emptySet(),
        overlappable: Set<Position> = emptySet(),
        splitAtX: Int = -1,
    ) = placementConflictCells(s, p, at, blocked, overlappable, splitAtX)

    @Test fun freeCellsHaveNoWarning() {
        assertTrue(conflicts().isEmpty())
    }

    @Test fun occupiedAndBlockedCellsAreMarkedWithoutDuplicates() {
        val neighbor = Position(3, 3)
        assertEquals(listOf(marked, neighbor), conflicts(
            s = state.copy(grid = mapOf(marked to CellContent.Wood(), neighbor to CellContent.Wood())),
            blocked = setOf(marked),
        ))
    }

    @Test fun thawRemovesOccupancyConflictButNotTerrain() {
        val frozen = state.copy(grid = mapOf(marked to CellContent.Wood()))
        assertTrue(conflicts(s = frozen, overlappable = setOf(marked)).isEmpty())
        assertEquals(listOf(marked), conflicts(s = frozen, overlappable = setOf(marked), blocked = setOf(marked)))
    }

    @Test fun lavaAndNinjaCutKeepTheirWarnings() {
        assertEquals(listOf(marked, Position(3, 3)), conflicts(s = state.copy(lavaRow = 4)))
        assertEquals(listOf(Position(3, 3), Position(4, 3)),
            conflicts(at = Position(3, 3), splitAtX = 4))
    }

    @Test fun cellsOutsideBoardAreNeverMarked() {
        val outside = Position(-1, 0)
        assertEquals(listOf(origin), conflicts(at = outside, blocked = setOf(origin)))
    }

    /** La vidriera no rechaza nada: una ficha sobre un vidrio cabe como en cualquier casilla. */
    @Test fun stainedGlassNeverBlocksAPlacement() {
        assertTrue(conflicts(s = state.copy(glassCells = setOf(marked))).isEmpty())
    }
}
