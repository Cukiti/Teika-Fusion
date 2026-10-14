package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class StarAstroOmenTest {

    @Test
    fun `se sortea al primer turno ocioso y se mantiene hasta el disparo`() {
        val omen = StarAstroOmen.next(StarAstroOmen.NONE, idleAfterTick = 1) { true }
        assertEquals(StarAstroOmen.SUN, omen)
        // El segundo turno no vuelve a tirar el dado: el astro anunciado es el que sale.
        assertEquals(StarAstroOmen.SUN, StarAstroOmen.next(omen, idleAfterTick = 2) { false })
    }

    @Test
    fun `romper la racha borra el presagio`() {
        assertEquals(StarAstroOmen.NONE, StarAstroOmen.next(StarAstroOmen.MOON, idleAfterTick = 0) { true })
    }

    /** El reparto es 50/50 de verdad: ya no depende de qué astros tienen presa. */
    @Test
    fun `el sorteo es mitad y mitad`() {
        val rng = Random(7)
        val suns = (1..10_000).count { StarAstroOmen.next(StarAstroOmen.NONE, 1) { rng.nextBoolean() } == StarAstroOmen.SUN }
        assertEquals(5_000.0, suns.toDouble(), 250.0)
    }

    @Test
    fun `sin Teikas el Sol calcina la madera mas cercana al centro y respeta las estrellas`() {
        val centro = Position(3, 4)
        val estrellaCentral = Position(4, 3)
        val esquina = Position(0, 0)
        assertEquals(centro, StarAstroOmen.sunWoodTarget(listOf(esquina, centro, estrellaCentral), setOf(estrellaCentral)))
        // Solo estrellas: se lleva una antes que no hacer nada.
        assertEquals(estrellaCentral, StarAstroOmen.sunWoodTarget(listOf(estrellaCentral), setOf(estrellaCentral)))
        assertNull(StarAstroOmen.sunWoodTarget(emptyList(), emptySet()))
    }

    // ── P-321 · la Luna devuelve lo que roba ─────────────────────────────────────────────────────

    @Test
    fun `la luna devuelve sus bloques en huecos libres sin cerrar lineas`() {
        // Fila 0 casi llena: le falta (7,0). Ahí no puede caer nada.
        val grid = (0 until 7).associate { Position(it, 0) to CellContent.Wood() as CellContent }
        val vaciadas = setOf(Position(3, 4), Position(4, 4))
        repeat(40) { seed ->
            val cells = StarAstroOmen.moonReturnCells(
                grid, 3, unusable = vaciadas, filled = emptySet(), random = kotlin.random.Random(seed),
            )
            org.junit.Assert.assertEquals(3, cells.size)
            cells.forEach { p ->
                org.junit.Assert.assertTrue("$p ocupada", grid[p] == null)
                org.junit.Assert.assertTrue("$p es una casilla que la Luna acaba de vaciar", p !in vaciadas)
            }
            org.junit.Assert.assertTrue("un bloque cerró la fila 0", Position(7, 0) !in cells)
            org.junit.Assert.assertEquals(cells.size, cells.toSet().size)
        }
    }

    @Test
    fun `sin sitio la luna devuelve menos`() {
        val all = (0 until 8).flatMap { y -> (0 until 8).map { x -> Position(x, y) } }
        val grid = all.filter { it != Position(0, 0) && it != Position(5, 5) }.associateWith { CellContent.Wood() as CellContent }
        // Las dos casillas libres cerrarían su fila: no cae ninguna.
        org.junit.Assert.assertTrue(StarAstroOmen.moonReturnCells(grid, 2, emptySet(), emptySet(), kotlin.random.Random(1)).isEmpty())
        org.junit.Assert.assertTrue(StarAstroOmen.moonReturnCells(emptyMap(), 0, emptySet(), emptySet(), kotlin.random.Random(1)).isEmpty())
    }
}
