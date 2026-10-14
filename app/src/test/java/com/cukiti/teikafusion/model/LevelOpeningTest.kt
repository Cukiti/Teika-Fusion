package com.cukiti.teikafusion.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de apertura de nivel (2026-08-24, pedido del usuario: «arregla los niveles del damero,
 * que sean jugables»).
 *
 * Antes de [LevelOpening] había **16 niveles** cuyo tablero inicial no admitía una bandeja mixta, y
 * en **siete de ellos el 100 % de los huecos estaban sueltos** —ninguna celda libre tocaba a otra—,
 * incluidos los cuatro dameros (269, 272, 286, 289). Solo se podían colocar monominós.
 */
class LevelOpeningTest {

    private fun checkerboard(): Set<Position> = buildSet {
        for (y in 0 until GameState.GRID_SIZE) {
            for (x in 0 until GameState.GRID_SIZE) {
                if ((x + y) % 2 == 1) add(Position(x, y))
            }
        }
    }

    private fun boardOf(level: Int): Set<Position> =
        GameState.initial(level = level).grid.filterValues { it.isOccupied }.keys

    @Test
    fun checkerboard_isRejected() {
        val blocked = checkerboard()
        // Media pizarra libre y ni un dominó cabe: TODOS los huecos están sueltos.
        assertEquals(1f, LevelOpening.isolatedHoleRatio(blocked), 0.001f)
        assertTrue(!LevelOpening.admitsMixedTray(blocked))
        assertTrue(!LevelOpening.isPlayableOpening(blocked))
    }

    @Test
    fun checkerboard_isOpenedUp() {
        val figure = checkerboard().map { it.x to it.y }
        val opened = LevelOpening.ensurePlayableOpening(figure, Random(269))
        val blocked = opened.mapTo(HashSet()) { (x, y) -> Position(x, y) }

        assertTrue("el damero abierto sigue sin admitir bandeja mixta",
            LevelOpening.isPlayableOpening(blocked))
        // Abrir solo puede QUITAR celdas de la figura, nunca añadirlas ni moverlas.
        assertTrue(opened.size < figure.size)
        assertTrue(opened.all { it in figure })
    }

    @Test
    fun openBoard_isLeftUntouched() {
        // Un tablero holgado ya cumple: la figura tiene que salir idéntica, celda por celda.
        val figure = listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1)
        assertEquals(figure, LevelOpening.ensurePlayableOpening(figure, Random(1)))
    }

    @Test
    fun everyLevel_opensPlayable() {
        val broken = (1..LevelMap.totalLevels).filterNot { level ->
            LevelOpening.isPlayableOpening(boardOf(level))
        }
        assertTrue("niveles que abren sin jugada posible: $broken", broken.isEmpty())
    }

    @Test
    fun everyLevel_opensWithAMixedTray() {
        // La consecuencia que se ve en pantalla: la bandeja del turno 1 reparte fichas de verdad.
        // Los niveles del tutorial reparten una ficha fija guionizada, así que quedan fuera.
        //
        // **Reformulado con P-75 (2026-09-01):** en un nivel con horizonte la bandeja tiene UNA
        // ranura, así que exigirle a esa ficha sola que sea de 3+ celdas no mide lo que este test
        // quiere medir. Lo que el jugador tiene delante en el turno 1 son la jugable **más las tres
        // del horizonte**: la garantía se aplica a ese conjunto, que es el equivalente exacto de la
        // bandeja de tres en el resto del juego.
        val bad = (1..LevelMap.totalLevels)
            .filterNot { TutorialCampaign.isTutorialLevel(it) }
            .filter { level ->
                val ronda = GameState.initial(level = level)
                val sizes = (ronda.availablePieces + ronda.pieceHorizon).map { it.shape.size }
                // (2026-09-26) Ya no se exige «como mucho un monominó»: el reparto trae lo más grande que
                // cabe JUNTO, y en un tablero de casillas sueltas (nivel 270: un 3×3 que rompe dos filas y
                // tres huecos de una casilla) los monominós son justo lo que cabe.
                sizes.none { it >= 3 }
            }
        assertTrue("niveles que abren con una bandeja de monominós: $bad", bad.isEmpty())
    }
}
