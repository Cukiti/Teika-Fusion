package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas del Miasma Venenoso de Toño. Fija el reparto por nivel (cuánta madera abarca el miasma):
 * L1 = 4 columnas más pesadas, L2 = 6, L3 = todo el tablero. Determinista.
 */
class ToxicMiasmaRulesTest {

    private val GRID = 8

    /** Madera en las columnas [cols]; `heights[i]` bloques (desde abajo) en la columna `cols[i]`. */
    private fun wood(cols: List<Int>, heights: List<Int>): List<Position> =
        cols.flatMapIndexed { i, x -> (0 until heights[i]).map { Position(x, GRID - 1 - it) } }

    @Test
    fun `tablero vacio no marca nada`() {
        assertTrue(ToxicMiasmaRules.selectMiasmaCells(emptyList(), level = 3, gridSize = GRID).isEmpty())
    }

    @Test
    fun `L3 marca TODA la madera`() {
        val w = wood(listOf(0, 3, 7), listOf(2, 5, 1))
        val marked = ToxicMiasmaRules.selectMiasmaCells(w, level = 3, gridSize = GRID)
        assertEquals(w.toSet(), marked.toSet())
    }

    @Test
    fun `L1 abarca solo las 4 columnas mas pesadas`() {
        // 5 columnas con madera: la más ligera (col 5, 1 bloque) debe quedar FUERA en L1 (4 cols).
        val w = wood(listOf(1, 2, 3, 4, 5), listOf(6, 5, 4, 3, 1))
        val marked = ToxicMiasmaRules.selectMiasmaCells(w, level = 1, gridSize = GRID).toSet()
        val markedCols = marked.map { it.x }.toSet()
        assertEquals(setOf(1, 2, 3, 4), markedCols)
        assertTrue(marked.none { it.x == 5 })
    }

    @Test
    fun `L2 abarca 6 columnas`() {
        val w = wood(listOf(0, 1, 2, 3, 4, 5, 6), listOf(7, 6, 5, 4, 3, 2, 1))
        val markedCols = ToxicMiasmaRules.selectMiasmaCells(w, level = 2, gridSize = GRID).map { it.x }.toSet()
        assertEquals(6, markedCols.size)
        assertTrue(6 !in markedCols)   // la columna más ligera queda fuera
    }

    @Test
    fun `menos columnas que el cupo marca todas`() {
        val w = wood(listOf(2, 5), listOf(3, 3))
        val marked = ToxicMiasmaRules.selectMiasmaCells(w, level = 1, gridSize = GRID)
        assertEquals(w.toSet(), marked.toSet())
    }

    @Test
    fun `empate de altura desempata por columna izquierda`() {
        // 5 columnas iguales (2 bloques). L1 toma 4 → debe quedar fuera la de índice MAYOR (col 8? no):
        // columnas 0..4, todas altura 2 → se marcan 0,1,2,3 y queda fuera la 4.
        val w = wood(listOf(0, 1, 2, 3, 4), listOf(2, 2, 2, 2, 2))
        val markedCols = ToxicMiasmaRules.selectMiasmaCells(w, level = 1, gridSize = GRID).map { it.x }.toSet()
        assertEquals(setOf(0, 1, 2, 3), markedCols)
    }
}
