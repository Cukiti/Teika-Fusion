package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **LO QUE CUESTA EL TABLERO** (2026-08-26, pedido del usuario: reasignar los turnos «tomando en
 * cuenta todo lo que interactúe directa e indirectamente con el tablero»).
 *
 * Lo que fija esta prueba no son los números concretos —son diales de playtest y se moverán— sino la
 * DIRECCIÓN: que un tablero más hostil nunca salga más barato que uno limpio, que el modelo esté
 * topado, y que el mapa entero se quede dentro de una banda que se pueda mirar de un vistazo.
 */
class BoardClutterCostTest {

    private fun tablero(cells: Int): List<LevelCell> =
        (0 until cells).map { LevelCell(it % 8, it / 8) }

    private val objetivo = LevelObjective(
        mapOf(TargetColor.RED to 30, TargetColor.BLUE to 30, TargetColor.PINK to 30)
    )

    @Test
    fun `un tablero vacio no cobra estorbo`() {
        assertEquals(
            0,
            BoardClutterCost.extraMoves(
                level = 100,
                objective = objetivo,
                cells = emptyList(),
                biomePrimary = BiomeType.NONE,
                baseMoves = 36,
            )
        )
    }

    @Test
    fun `mas celdas ocupadas nunca cuestan menos`() {
        var previo = 0
        for (cells in 0..46 step 6) {
            val coste = BoardClutterCost.extraMoves(
                level = 200, objective = objetivo, cells = tablero(cells),
                biomePrimary = BiomeType.NONE, baseMoves = 38,
            )
            assertTrue("$cells celdas cuestan $coste, menos que $previo", coste >= previo)
            previo = coste
        }
    }

    @Test
    fun `el cristal nunca cuesta menos que el mismo tablero sin bioma`() {
        val liso = BoardClutterCost.extraMoves(
            level = 250, objective = objetivo, cells = tablero(40),
            biomePrimary = BiomeType.NONE, baseMoves = 39,
        )
        val cristal = BoardClutterCost.extraMoves(
            level = 250, objective = objetivo, cells = tablero(40),
            biomePrimary = BiomeType.CRYSTAL, baseMoves = 39,
        )
        // P-360 (2026-09-28): con un 30 % del cristal de antes, su coste extra puede redondear al del tablero
        // liso; lo que no puede pasar es que el cristal salga más barato.
        assertTrue("liso=$liso cristal=$cristal", cristal >= liso)
    }

    @Test
    fun `las bobinas del circuito son el estorbo mas caro`() {
        val base = tablero(40)
        val tormenta = BoardClutterCost.extraMoves(
            level = 250, objective = objetivo, cells = base,
            biomePrimary = BiomeType.STORM, baseMoves = 39,
        )
        val veneno = BoardClutterCost.extraMoves(
            level = 250, objective = objetivo, cells = base,
            biomePrimary = BiomeType.TOXIC, baseMoves = 39,
        )
        assertTrue("tormenta=$tormenta veneno=$veneno", tormenta > veneno)
    }

    @Test
    fun `el modelo esta topado`() {
        // Tablero al máximo permitido por la holgura de línea, en el bioma más material del juego.
        val coste = BoardClutterCost.extraMoves(
            level = LevelMap.totalLevels,
            objective = objetivo,
            cells = tablero(48),
            biomePrimary = BiomeType.STORM,
            baseMoves = 40,
        )
        assertTrue(coste <= BoardClutterCost.MAX_EXTRA_MOVES)
    }

    @Test
    fun `el estorbo del mapa se reparte, no infla`() {
        // La media del juego se queda donde estaba (36,5 turnos antes de la regla; 38,3 después) y el
        // tope sube solo donde el tablero lo justifica. Si esta prueba se rompe, lo que hay que mirar
        // es [BoardClutterCost.MEASURED_BASELINE_MOVES]: es el dial que decide si esto redistribuye o
        // infla el mapa entero.
        val turnos = ((LevelMap.introLevels + 1)..LevelMap.totalLevels)
            .map { LevelBudget.movesForLevel(it) }
        assertTrue("media=${turnos.average()}", turnos.average() in 35.0..41.0)
        assertTrue("máximo=${turnos.max()}", turnos.max() <= 52)
    }
}
