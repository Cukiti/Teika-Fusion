package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** P-324 · La enredadera del Jardín: qué deja la raíz al arrastrar y dónde brota sin Teika. */
class GardenVineTest {

    @Test
    fun `el arrastre deja atras lo que el cuerpo ya no tapa`() {
        // Un T2 (2×2) que la raíz mueve una casilla a la derecha: queda atrás la columna de la izquierda.
        val wake = GardenVine.wakeCells(Position(2, 3), Position(3, 3), TeikaType.TEIKA_2)
        assertEquals(setOf(Position(2, 3), Position(2, 4)), wake)
        // Sin moverse (el agarre inicial) no deja nada.
        assertTrue(GardenVine.wakeCells(Position(2, 3), Position(2, 3), TeikaType.TEIKA_2).isEmpty())
        // Lo que queda fuera del tablero no cuenta.
        assertTrue(GardenVine.wakeCells(Position(7, 0), Position(8, 0), TeikaType.TEIKA_1).all { it.x < 8 })
    }

    @Test
    fun `la enredadera cubre madera y huecos sin cerrar lineas`() {
        // Fila 0 llena salvo (7,0): una enredadera en ese hueco la cerraría.
        val grid = (0 until 7).associate { Position(it, 0) to CellContent.Wood() as CellContent }
        val targets = GardenVine.vineTargets(
            listOf(Position(7, 0), Position(3, 0), Position(4, 4)), grid, emptySet(), emptySet(),
        )
        assertTrue(Position(3, 0) in targets)      // madera: se enreda
        assertTrue(Position(4, 4) in targets)      // hueco: brota
        assertFalse(Position(7, 0) in targets)     // cerraría la fila 0
    }

    @Test
    fun `la enredadera no pisa casillas vetadas ni repite`() {
        val grid = mapOf(Position(1, 1) to CellContent.Wood(vine = true) as CellContent)
        val targets = GardenVine.vineTargets(listOf(Position(1, 1), Position(2, 2)), grid, setOf(Position(2, 2)), emptySet())
        assertTrue(targets.isEmpty())
    }

    @Test
    fun `la invasion brota pegada al borde`() {
        PlantEdge.entries.forEach { edge ->
            repeat(10) { seed ->
                val cells = GardenVine.invasionCells(edge, emptyMap(), emptySet(), emptySet(), Random(seed))
                assertEquals(GardenVine.INVASION, cells.size)
                cells.forEach { p ->
                    val depth = when (edge) {
                        PlantEdge.TOP -> p.y
                        PlantEdge.BOTTOM -> 7 - p.y
                        PlantEdge.LEFT -> p.x
                        PlantEdge.RIGHT -> 7 - p.x
                    }
                    // Tablero vacío: todas caben en la fila o columna del borde.
                    assertEquals("$edge $p", 0, depth)
                }
            }
        }
    }

    @Test
    fun `la enredadera quita la estrella y conserva el color`() {
        val w = CellContent.Wood(colorType = 3, targetColor = TargetColor.entries.first())
        val v = GardenVine.vine(w, colorType = 0)
        assertTrue(v.vine)
        assertEquals(null, v.targetColor)
        assertEquals(3, v.colorType)
        assertTrue(GardenVine.vine(null, colorType = 2).let { it.vine && it.colorType == 2 })
    }
}
