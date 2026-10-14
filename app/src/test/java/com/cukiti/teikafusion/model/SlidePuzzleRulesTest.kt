package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Timoteo / Puzzle Deslizante: la madera se cierra sobre sí misma sin pisar obstáculos. */
class SlidePuzzleRulesTest {

    /** Aplica pasadas hasta que el rompecabezas deja de moverse (o se agota el tope). */
    private fun settle(
        wood: Set<Position>,
        blocked: Set<Position> = emptySet(),
        gridSize: Int = 8,
        maxPasses: Int = 40
    ): Set<Position> {
        var current = wood
        repeat(maxPasses) {
            val moves = SlidePuzzleRules.computePass(current, blocked, gridSize)
            if (moves.isEmpty()) return current
            val next = current.toMutableSet()
            moves.forEach { next.remove(it.from); next.add(it.to) }
            current = next
        }
        return current
    }

    /** Área del rectángulo que envuelve la madera: cuanto menor, más "juntita" está. */
    private fun bboxArea(cells: Set<Position>): Int {
        val w = cells.maxOf { it.x } - cells.minOf { it.x } + 1
        val h = cells.maxOf { it.y } - cells.minOf { it.y } + 1
        return w * h
    }

    @Test
    fun `bloques dispersos acaban agrupados`() {
        val scattered = setOf(
            Position(0, 0), Position(7, 0), Position(0, 7), Position(7, 7), Position(4, 3)
        )
        val packed = settle(scattered)

        assertEquals("no se pierde ni se duplica madera", scattered.size, packed.size)
        assertTrue(
            "el grupo final debe ocupar menos caja que el disperso",
            bboxArea(packed) < bboxArea(scattered)
        )
    }

    @Test
    fun `la madera nunca pisa una celda bloqueada`() {
        val blocked = setOf(Position(4, 4), Position(3, 4), Position(4, 3))
        val wood = setOf(Position(0, 4), Position(7, 4), Position(4, 0), Position(4, 7))
        val packed = settle(wood, blocked)

        assertTrue("ningún bloque acaba sobre un obstáculo", packed.none { it in blocked })
        assertEquals(wood.size, packed.size)
    }

    @Test
    fun `un grupo ya compacto no se mueve`() {
        val tight = setOf(
            Position(3, 3), Position(4, 3),
            Position(3, 4), Position(4, 4)
        )
        assertTrue(
            "un bloque 2x2 ya está juntito: nada que deslizar",
            SlidePuzzleRules.computePass(tight, emptySet(), 8).isEmpty()
        )
    }

    @Test
    fun `un solo bloque no tiene rompecabezas que armar`() {
        assertTrue(SlidePuzzleRules.computePass(setOf(Position(2, 2)), emptySet(), 8).isEmpty())
        assertTrue(SlidePuzzleRules.computePass(emptySet(), emptySet(), 8).isEmpty())
    }

    @Test
    fun `los pasos son siempre de una celda y sin colisiones`() {
        val wood = setOf(
            Position(1, 1), Position(6, 1), Position(1, 6), Position(6, 6), Position(3, 3)
        )
        val moves = SlidePuzzleRules.computePass(wood, emptySet(), 8)

        moves.forEach { m ->
            val step = kotlin.math.abs(m.to.x - m.from.x) + kotlin.math.abs(m.to.y - m.from.y)
            assertEquals("cada paso es de una sola celda en un solo eje", 1, step)
        }
        val destinations = moves.map { it.to }
        assertEquals("dos bloques nunca caen en la misma celda", destinations.size, destinations.toSet().size)

        val after = wood.toMutableSet().apply { moves.forEach { remove(it.from); add(it.to) } }
        assertEquals("la pasada conserva el número de bloques", wood.size, after.size)
    }
}
