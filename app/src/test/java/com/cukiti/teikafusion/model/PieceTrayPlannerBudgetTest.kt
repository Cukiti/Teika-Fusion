package com.cukiti.teikafusion.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión del ANR del 2026-08-24 («Teika Fusion no responde» al pulsar JUGAR NIVEL).
 *
 * La bandeja se planifica DENTRO del click (`GameState.initial` → [PieceTrayPlanner.plan]), así que
 * su coste es tiempo del hilo principal. En los tableros donde las políticas de variedad son
 * geométricamente IMPOSIBLES —el damero de los niveles 269/272/286/289, donde no cabe ni un
 * dominó— la búsqueda se recorría entera 90 veces: más de 6 s por nivel en JVM de escritorio, y
 * decenas de segundos en el móvil.
 *
 * Estos tests fijan las dos propiedades que lo impiden: la búsqueda está ACOTADA y sigue
 * devolviendo una bandeja legal.
 */
class PieceTrayPlannerBudgetTest {

    /** Damero: mitad del tablero libre y aun así no cabe ninguna ficha de 2 bloques. */
    private fun checkerboard(): Set<Position> = buildSet {
        for (y in 0 until GameState.GRID_SIZE) {
            for (x in 0 until GameState.GRID_SIZE) {
                if ((x + y) % 2 == 1) add(Position(x, y))
            }
        }
    }

    @Test
    fun plan_onImpossiblePolicyBoard_staysWithinBudget() {
        val blocked = checkerboard()
        val started = System.nanoTime()
        val plan = PieceTrayPlanner.plan(
            boardSize = GameState.GRID_SIZE,
            blockedCells = blocked,
            count = 3,
            level = 269,
            random = Random(269)
        )
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(3, plan.size)
        assertTrue(PieceTrayPlanner.reservationsAreValid(plan, blocked))
        // Antes del arreglo: >6.000 ms. El techo es deliberadamente holgado — sólo tiene que
        // cazar una reintroducción del backtracking sin presupuesto, no medir la máquina.
        assertTrue("plan() tardó $elapsedMs ms en un damero", elapsedMs < 2_000)
    }

    @Test
    fun plan_onFragmentedBoard_staysWithinBudget() {
        // Ocho huecos sueltos, ninguno adyacente a otro (perfil de los niveles 290/293).
        val free = setOf(
            Position(2, 1), Position(5, 1),
            Position(1, 2), Position(6, 2),
            Position(1, 5), Position(6, 5),
            Position(2, 6), Position(5, 6),
        )
        val blocked = allBoardCells() - free
        val started = System.nanoTime()
        val plan = PieceTrayPlanner.plan(
            boardSize = GameState.GRID_SIZE,
            blockedCells = blocked,
            count = 3,
            level = 293,
            random = Random(293)
        )
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(3, plan.size)
        assertTrue(PieceTrayPlanner.reservationsAreValid(plan, blocked))
        assertTrue("plan() tardó $elapsedMs ms en un tablero fragmentado", elapsedMs < 2_000)
    }

    @Test
    fun plan_onOpenBoard_isStillFastAndMixed() {
        val started = System.nanoTime()
        val plan = PieceTrayPlanner.plan(
            boardSize = GameState.GRID_SIZE,
            blockedCells = emptySet(),
            count = 3,
            level = 40,
            random = Random(11)
        )
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertEquals(3, plan.size)
        assertTrue(PieceTrayPlanner.reservationsAreValid(plan, emptySet()))
        // Tablero abierto: la política mixta se cumple sin gastar presupuesto.
        assertTrue(plan.count { it.shape.size == 1 } <= 1)
        assertTrue(plan.any { it.shape.size >= 3 })
        assertTrue("plan() tardó $elapsedMs ms en un tablero vacío", elapsedMs < 2_000)
    }

    private fun allBoardCells(): Set<Position> = buildSet {
        for (y in 0 until GameState.GRID_SIZE) {
            for (x in 0 until GameState.GRID_SIZE) add(Position(x, y))
        }
    }
}
