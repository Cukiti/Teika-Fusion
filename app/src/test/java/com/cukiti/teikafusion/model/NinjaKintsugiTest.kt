package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** P-323 · La cicatriz de oro del Dojo en Sombras: dónde cuaja el kintsugi y qué deja. */
class NinjaKintsugiTest {

    private val cut = 4

    @Test
    fun `el oro cae en huecos de la costura empezando por abajo`() {
        repeat(30) { seed ->
            val cells = NinjaKintsugi.scarCells(emptyMap(), cut, emptySet(), emptySet(), Random(seed))
            assertEquals(NinjaKintsugi.SCARS, cells.size)
            cells.forEach { assertTrue("$it fuera de la costura", it.x in NinjaKintsugi.seamColumns(cut)) }
            // Tablero vacío: la fila de abajo tiene las dos casillas de la costura libres.
            assertTrue(cells.all { it.y == GameState.GRID_SIZE - 1 })
        }
    }

    @Test
    fun `nunca cierra una linea ni pisa casillas vetadas`() {
        // Fila 7 llena salvo la costura: el oro no puede cerrarla.
        val grid = (0 until 8).filter { it !in NinjaKintsugi.seamColumns(cut) }
            .associate { Position(it, 7) to CellContent.Wood() as CellContent }
        val vetada = Position(3, 6)
        repeat(30) { seed ->
            val cells = NinjaKintsugi.scarCells(grid, cut, setOf(vetada), emptySet(), Random(seed))
            assertFalse(vetada in cells)
            val lleno = grid.keys + cells
            assertFalse("el oro cerró la fila 7", (0 until 8).all { Position(it, 7) in lleno })
        }
    }

    @Test
    fun `sin huecos en la costura no deja nada`() {
        val grid = (0 until 8).flatMap { y -> NinjaKintsugi.seamColumns(cut).map { x -> Position(x, y) } }
            .associateWith { CellContent.Wood() as CellContent }
        assertTrue(NinjaKintsugi.scarCells(grid, cut, emptySet(), emptySet(), Random(1)).isEmpty())
    }

    @Test
    fun `el bloque de oro es madera normal con su cara`() {
        val w = NinjaKintsugi.scar(2)
        assertTrue(w.kintsugi)
        assertEquals(2, w.colorType)
        assertEquals(0, w.armor)
    }

    @Test
    fun `la grieta dura cuatro colocaciones y la tregua una`() {
        assertEquals(4, BiomeRhythm.punishmentTurns(BiomeType.NINJA))   // P-352: 4 → 5 · P-359: 5 → 4
        assertEquals(1, LevelDirector.ninjaTruceTurns(100))
    }
}
