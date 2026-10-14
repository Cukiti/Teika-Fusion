package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** P-329 · LAS PIEZAS DEL REY: qué arrasa cada pieza, a dónde apunta y qué la retira. */
class KingPieceTest {

    private fun wood(vararg cells: Pair<Int, Int>): Map<Position, CellContent> =
        cells.associate { (x, y) -> Position(x, y) to CellContent.Wood() }

    @Test
    fun `la torre arrasa una fila o una columna entera`() {
        assertEquals((0 until 8).map { Position(it, 4) }, KingPiece.cells(KingPiece.ROOK, KingSword.ROW, 4))
        assertEquals((0 until 8).map { Position(6, it) }, KingPiece.cells(KingPiece.ROOK, KingSword.COL, 6))
    }

    @Test
    fun `el alfil recorre sus dos diagonales`() {
        // «\» x − y = 0: la diagonal mayor.
        assertEquals((0 until 8).map { Position(it, it) }, KingPiece.cells(KingPiece.BISHOP, KingPiece.DIAG_DOWN, 0))
        // «/» x + y = 3: de (0,3) a (3,0), cuatro casillas.
        assertEquals(
            listOf(Position(0, 3), Position(1, 2), Position(2, 1), Position(3, 0)),
            KingPiece.cells(KingPiece.BISHOP, KingPiece.DIAG_UP, 3)
        )
    }

    @Test
    fun `el alfil nunca apunta a una diagonal de menos de cuatro casillas`() {
        KingPiece.targets(KingPiece.BISHOP).forEach { (a, b) ->
            assertTrue("($a,$b) es demasiado corta",
                KingPiece.cells(KingPiece.BISHOP, a, b).size >= KingPiece.MIN_DIAGONAL)
        }
    }

    @Test
    fun `el caballo aplasta el 3x3 y se recorta en el borde`() {
        assertEquals(9, KingPiece.cells(KingPiece.KNIGHT, 4, 4).size)
        assertEquals(setOf(Position(0, 0), Position(1, 0), Position(0, 1), Position(1, 1)),
            KingPiece.cells(KingPiece.KNIGHT, 0, 0).toSet())
    }

    @Test
    fun `sin madera a su alcance no hay objetivo`() {
        KingPiece.KINDS.forEach { assertNull(KingPiece.choose(it, emptyMap(), Random(1))) }
    }

    @Test
    fun `el objetivo es aleatorio pero siempre arrasa algo`() {
        val grid = wood(0 to 7, 1 to 7, 5 to 2, 6 to 3)
        val seen = mutableSetOf<Pair<Int, Int>>()
        repeat(200) { seed ->
            KingPiece.KINDS.forEach { kind ->
                val target = KingPiece.choose(kind, grid, Random(seed))!!
                assertTrue(KingPiece.damage(kind, target.first, target.second, grid) > 0)
                if (kind == KingPiece.ROOK) seen += target
            }
        }
        // Aleatorio: la torre no apunta siempre a la misma calle.
        assertTrue(seen.size > 1)
    }

    @Test
    fun `la misma semilla elige lo mismo`() {
        val grid = wood(0 to 7, 1 to 7, 5 to 2, 6 to 3, 3 to 3)
        KingPiece.KINDS.forEach { kind ->
            assertEquals(KingPiece.choose(kind, grid, Random(42)), KingPiece.choose(kind, grid, Random(42)))
        }
    }

    @Test
    fun `solo una linea que cruza la casilla de llegada la para`() {
        // Torre por la columna 5, entrando por arriba: llega a (5, 7).
        assertTrue(KingPiece.parriedBy(KingPiece.ROOK, KingSword.COL, 5, false, clearedRows = listOf(7), clearedCols = emptyList()))
        // Una fila que cruza su camino pero no su llegada ya NO la para (antes sí).
        assertFalse(KingPiece.parriedBy(KingPiece.ROOK, KingSword.COL, 5, false, clearedRows = listOf(2), clearedCols = emptyList()))
        // Caballo en (4, 3): su fila o su columna.
        assertTrue(KingPiece.parriedBy(KingPiece.KNIGHT, 4, 3, false, clearedRows = emptyList(), clearedCols = listOf(4)))
        assertFalse(KingPiece.parriedBy(KingPiece.KNIGHT, 4, 3, false, clearedRows = listOf(2), clearedCols = listOf(3)))
        assertFalse(KingPiece.parriedBy(KingPiece.NONE, 0, 0, false, clearedRows = listOf(0), clearedCols = listOf(0)))
    }

    @Test
    fun `torre y alfil entran desde fuera del tablero`() {
        val (x, y) = KingPiece.origin(KingPiece.ROOK, KingSword.ROW, 3, fromEnd = false)
        assertEquals(-1f to 3f, x to y)
        val (x2, y2) = KingPiece.origin(KingPiece.ROOK, KingSword.ROW, 3, fromEnd = true)
        assertEquals(8f to 3f, x2 to y2)
    }

    /** 2026-09-28: pieza, pieza, espada. */
    @Test
    fun `dos piezas por cada espada`() {
        assertTrue(KingPiece.pieceTurnAfter(0))
        assertTrue(KingPiece.pieceTurnAfter(1))
        assertFalse(KingPiece.pieceTurnAfter(KingPiece.PIECES_PER_SWORD))
    }

    // ── LA PIEZA SE QUEDA (2026-09-28) ──

    @Test
    fun `la torre se queda en el extremo por el que sale y bloquea el resto de su fila`() {
        val at = KingPiece.landingCell(KingPiece.ROOK, KingSword.ROW, 3, fromEnd = false)
        assertEquals(Position(7, 3), at)
        assertEquals(Position(0, 3), KingPiece.landingCell(KingPiece.ROOK, KingSword.ROW, 3, fromEnd = true))
        val lane = KingPiece.restLane(KingPiece.ROOK, KingSword.ROW, 3, at!!)
        assertEquals((0 until 7).map { Position(it, 3) }.toSet(), lane)
    }

    @Test
    fun `el caballo bloquea sus saltos en L dentro del tablero`() {
        val at = KingPiece.landingCell(KingPiece.KNIGHT, 0, 0, fromEnd = false)!!
        assertEquals(setOf(Position(1, 2), Position(2, 1)), KingPiece.restLane(KingPiece.KNIGHT, 0, 0, at))
        assertEquals(8, KingPiece.restLane(KingPiece.KNIGHT, 4, 4, Position(4, 4)).size)
    }

    @Test
    fun `lo que bloquea nunca tapa su fila y su columna a la vez`() {
        // Siempre queda una línea con la que echarla: la calle no puede cubrir las dos.
        for (kind in KingPiece.KINDS) for ((a, b) in KingPiece.targets(kind)) for (fromEnd in listOf(false, true)) {
            val at = KingPiece.landingCell(kind, a, b, fromEnd) ?: continue
            val lane = KingPiece.restLane(kind, a, b, at)
            assertFalse(at in lane)
            val rowBlocked = lane.any { it.y == at.y }
            val colBlocked = lane.any { it.x == at.x }
            assertFalse("$kind $a $b", rowBlocked && colBlocked)
        }
    }

    @Test
    fun `romper su fila o su columna la echa`() {
        val at = Position(2, 5)
        assertTrue(KingPiece.restFreedBy(at, clearedRows = listOf(5), clearedCols = emptyList()))
        assertTrue(KingPiece.restFreedBy(at, clearedRows = emptyList(), clearedCols = listOf(2)))
        assertFalse(KingPiece.restFreedBy(at, clearedRows = listOf(4), clearedCols = listOf(3)))
        assertFalse(KingPiece.restFreedBy(null, clearedRows = listOf(5), clearedCols = listOf(2)))
    }
}
