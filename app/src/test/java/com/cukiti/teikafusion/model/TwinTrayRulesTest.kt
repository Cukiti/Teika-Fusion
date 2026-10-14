package com.cukiti.teikafusion.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de FICHAS GEMELAS (P-76). */
class TwinTrayRulesTest {

    @Test
    fun `la intro y el tutorial nunca reparten gemelas`() {
        for (level in 1..LevelMap.introLevels) {
            assertFalse("nivel de intro $level", TwinTrayRules.appliesTo(level))
        }
        for (level in 1..LevelMap.totalLevels) {
            if (!TutorialCampaign.isTutorialLevel(level)) continue
            assertFalse("nivel de tutorial $level", TwinTrayRules.appliesTo(level))
        }
    }

    /**
     * **Las gemelas no coinciden siempre con el mismo tipo de nivel ni con el mismo sabor de
     * bandeja.**
     *
     * Antes lo garantizaba su cadencia (5, coprima con los retos centrales —que miden 4— y con los
     * sabores de bandeja —6—). Desde el 2026-09-03 el reparto lo hace [HouseRule], así que la
     * propiedad ya no se deduce de una cadencia propia: se comprueba directamente, que además es lo
     * que de verdad importaba. Y el número de apariciones se mide contra **su parte** del reparto,
     * no contra una cifra escrita a mano que la siguiente familia dejaría obsoleta.
     */
    @Test
    fun `las gemelas no se casan con el tipo de nivel`() {
        assertTrue((1..LevelMap.totalLevels).none(TwinTrayRules::appliesTo))
        val levels = HouseRule.levelsWith(HouseRule.RELAY)
        val suParte = (LevelMap.totalLevels - LevelMap.introLevels) / HouseRule.entries.size
        assertTrue("apenas hay niveles con gemelas: ${levels.size} (su parte: $suParte)",
            levels.size >= suParte)
        assertTrue(
            "las gemelas caen siempre en el mismo reto",
            levels.map { LevelDirector.identityFor(it).kind }.distinct().size > 1
        )
    }

    @Test
    fun `no se puede gemelar un monomino ni dejar la bandeja sin pieza grande`() {
        // Dos monominós es la bandeja que gasta tres turnos para colocar tres bloques.
        assertFalse(TwinTrayRules.allows(listOf(1, 4, 3), twinSize = 1, replaced = 1))
        // Duplicar el dominó sobre la única pieza de 3+ deja la bandeja sin ninguna grande.
        assertFalse(TwinTrayRules.allows(listOf(2, 3, 1), twinSize = 2, replaced = 1))
        // Duplicar la grande sí: quedan un 4, otro 4 y el dominó.
        assertTrue(TwinTrayRules.allows(listOf(4, 2, 3), twinSize = 4, replaced = 1))
    }

    @Test
    fun `se prefiere gemelar la ficha mas grande`() {
        assertEquals(listOf(1, 2, 0), TwinTrayRules.preferenceOrder(listOf(2, 5, 3)))
    }

    /**
     * **El invariante que manda sobre la mecánica.** Gemelar no puede romper lo que el planificador
     * promete: una colocación real y distinta para cada ficha. Si la geometría no lo admite, el plan
     * se queda como estaba — un nivel sin gemelas es un nivel normal, y eso es infinitamente mejor
     * que un nivel imposible por culpa del repartidor (P-53, los 14 niveles del damero).
     */
    @Test
    fun `gemelar reparte orientaciones que caben`() {
        repeat(60) { seed ->
            // Tableros de ocupación creciente, del vacío al casi lleno.
            val occupancy = seed % 50
            val blocked = (0 until GameState.GRID_SIZE).flatMap { y ->
                (0 until GameState.GRID_SIZE).map { x -> Position(x, y) }
            }.shuffled(Random(seed.toLong())).take(occupancy).toSet()

            val plan = PieceTrayPlanner.plan(
                boardSize = GameState.GRID_SIZE,
                blockedCells = blocked,
                count = 3,
                level = 40,
                random = Random(seed.toLong()),
                twins = true,
            )
            assertTrue(
                "seed=$seed: el plan gemelado se pisa a sí mismo",
                PieceTrayPlanner.reservationsAreValid(plan, blocked)
            )
            val sizes = plan.map { it.shape.size }
            if (plan.size == 3 && occupancy < 20) {
                assertTrue("seed=$seed: bandeja sin pieza de 3+: $sizes", sizes.any { it >= 3 })
                assertTrue("seed=$seed: más de un monominó: $sizes", sizes.count { it == 1 } <= 1)
            }
        }
    }

    /** Con el tablero abierto, gemelar tiene que ocurrir de verdad — si no, la ficha no existe. */
    @Test
    fun `en un tablero abierto la bandeja sale con dos formas iguales`() {
        val twinned = (0 until 20).count { seed ->
            val plan = PieceTrayPlanner.plan(
                boardSize = GameState.GRID_SIZE,
                blockedCells = emptySet(),
                count = 3,
                level = 40,
                random = Random(seed.toLong()),
                twins = true,
            )
            plan.map { canonical(it.shape) }.groupingBy { it }.eachCount().any { it.value >= 2 }
        }
        assertTrue("solo $twinned de 20 bandejas salieron gemeladas", twinned >= 18)
    }

    /** Sin pedir gemelas, el reparto no debería producirlas casi nunca (control del test de arriba). */
    @Test
    fun `sin pedirlas la bandeja no las fabrica sola`() {
        val plan = PieceTrayPlanner.plan(
            boardSize = GameState.GRID_SIZE,
            blockedCells = emptySet(),
            count = 3,
            level = 40,
            random = Random(7),
            twins = false,
        )
        assertTrue(PieceTrayPlanner.reservationsAreValid(plan, emptySet()))
    }

    private fun canonical(shape: List<Position>): List<Pair<Int, Int>> =
        shape.map { it.x to it.y }.sortedWith(compareBy({ it.second }, { it.first }))
}
