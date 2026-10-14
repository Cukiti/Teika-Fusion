package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Morti / POSESIÓN — las reglas puras.
 *
 * Lo que se fija aquí son las dos formas en que esta habilidad podría perjudicar al jugador en vez
 * de ayudarle: **deshacer la línea que está completando** (robarle un bloque) y **desmontar el
 * trabajo del jugador** (llevarse un bloque de una fila casi llena para rellenar otra). Ambas son
 * fáciles de introducir sin darse cuenta al tocar el reparto de donantes.
 */
class GhostPossessionRulesTest {

    private val n = GameState.GRID_SIZE

    private fun row(y: Int, xs: IntRange) = xs.map { Position(it, y) }.toSet()

    @Test
    fun `sin madera no hay posesion`() {
        assertTrue(GhostPossessionRules.plan(emptySet(), emptySet(), maxMoves = 5).isEmpty())
    }

    @Test
    fun `sin presupuesto no hay posesion`() {
        val wood = row(0, 0 until n - 1) + row(4, 0..3)
        assertTrue(GhostPossessionRules.plan(wood, emptySet(), maxMoves = 0).isEmpty())
    }

    @Test
    fun `cierra la fila a la que le falta un bloque`() {
        // Fila 0 llena salvo la última celda; fila 4 con material suelto del que tirar.
        val wood = row(0, 0 until n - 1) + row(4, 0..2)
        val moves = GhostPossessionRules.plan(wood, emptySet(), maxMoves = 3)

        assertEquals(1, moves.size)
        assertEquals(Position(n - 1, 0), moves.first().to)
        // El donante sale de la fila suelta, nunca de la que se está cerrando.
        assertEquals(4, moves.first().from.y)
    }

    @Test
    fun `nunca roba de la linea que esta completando`() {
        // Toda la madera del tablero está en la fila 0 salvo un bloque suelto. Si Morti tirase de
        // la propia fila 0 para taparse el hueco, la dejaría igual de incompleta: movimiento nulo.
        val wood = row(0, 0 until n - 1) + setOf(Position(3, 6))
        val moves = GhostPossessionRules.plan(wood, emptySet(), maxMoves = 4)

        assertEquals(1, moves.size)
        assertEquals(Position(3, 6), moves.first().from)
        assertFalse(moves.any { it.from.y == 0 })
    }

    @Test
    fun `no hay plan si el unico material esta dentro de la linea objetivo`() {
        // Sin donantes fuera de la fila que se quiere cerrar, la habilidad no puede hacer nada útil
        // y debe decirlo: el ViewModel devuelve la carga en vez de cobrar por un paseo.
        val wood = row(0, 0 until n - 1)
        assertTrue(GhostPossessionRules.plan(wood, emptySet(), maxMoves = 5).isEmpty())
    }

    @Test
    fun `prefiere el bloque de la zona mas vacia`() {
        // Fila 0: le falta 1. Fila 2: casi llena (trabajo del jugador, no tocar).
        // Fila 7: un bloque solo y perdido → ese es el que Morti debe llevarse.
        val wood = row(0, 0 until n - 1) + row(2, 0..n - 3) + setOf(Position(0, 7))
        val moves = GhostPossessionRules.plan(wood, emptySet(), maxMoves = 2)

        assertEquals(1, moves.size)
        assertEquals(Position(0, 7), moves.first().from)
    }

    @Test
    fun `respeta el presupuesto`() {
        // Dos filas a las que les faltan 2 bloques cada una, y material de sobra suelto.
        val wood = row(0, 0..n - 3) + row(1, 0..n - 3) +
            row(5, 0 until n - 1) + row(6, 0 until n - 1)
        val moves = GhostPossessionRules.plan(wood, emptySet(), maxMoves = 2)

        assertTrue("No puede mover más bloques de los que se le permiten", moves.size <= 2)
        assertTrue(moves.isNotEmpty())
    }

    @Test
    fun `no pisa celdas bloqueadas ni tira de ellas`() {
        // La fila 0 tiene un Teika encima (bloqueada): esa celda ya cuenta como ocupada, así que a
        // la fila le falta solo la última — y el Teika no puede ser donante ni destino.
        val teika = Position(2, 0)
        val wood = row(0, 0 until n - 1) - teika + setOf(Position(4, 5), Position(5, 5))
        val moves = GhostPossessionRules.plan(wood, blocked = setOf(teika), maxMoves = 3)

        assertEquals(1, moves.size)
        assertEquals(Position(n - 1, 0), moves.first().to)
        assertFalse(moves.any { it.from == teika || it.to == teika })
    }

    @Test
    fun `origen y destino nunca coinciden y no se repiten destinos`() {
        val wood = row(0, 0..n - 3) + row(3, 0 until n - 1) + row(6, 0..4)
        val moves = GhostPossessionRules.plan(wood, emptySet(), maxMoves = 6)

        assertTrue(moves.none { it.from == it.to })
        assertEquals(moves.size, moves.map { it.to }.distinct().size)
        assertEquals(moves.size, moves.map { it.from }.distinct().size)
        // Un destino siempre estaba VACÍO y un origen siempre tenía madera.
        assertTrue(moves.none { it.to in wood })
        assertTrue(moves.all { it.from in wood })
    }
}
