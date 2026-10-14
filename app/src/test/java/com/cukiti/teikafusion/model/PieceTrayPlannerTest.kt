package com.cukiti.teikafusion.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **El reparto predecible** (2026-09-26, decisión del usuario): lo más grande que quepa, y las fichas
 * de la bandeja caben TODAS juntas. La prueba de que caben juntas son las reservas: cada ficha trae
 * una colocación, y las colocaciones de una bandeja no se pisan.
 */
class PieceTrayPlannerTest {

    private fun trays(
        blocked: Set<Position>,
        n: Int,
        count: Int = 3,
        splitAtX: Int = -1,
        helpful: Set<Position> = emptySet(),
    ) = (0 until n).map { seed ->
        PieceTrayPlanner.plan(
            boardSize = GameState.GRID_SIZE, blockedCells = blocked, count = count, level = 110,
            random = Random(seed), splitAtX = splitAtX, helpfulCells = helpful,
        )
    }

    /** Un tablero con [freeRatio] de celdas libres, sorteadas con [seed]. */
    private fun board(freeRatio: Float, seed: Int): Set<Position> {
        val all = allBoardCells().shuffled(Random(seed))
        return all.take(((1f - freeRatio) * all.size).toInt()).toSet()
    }

    /** Las colocaciones de la bandeja existen, son válidas y no se pisan entre sí. */
    private fun fitsTogether(plan: List<PieceTrayPlanner.PlannedShape>, blocked: Set<Position>, splitAtX: Int = -1): Boolean {
        if (plan.any { it.reservedCells.isEmpty() }) return false
        if (!PieceTrayPlanner.reservationsAreValid(plan, blocked, splitAtX = splitAtX)) return false
        val all = plan.flatMap { it.reservedCells }
        return all.size == all.toSet().size
    }

    @Test
    fun `con el tablero vacio solo salen fichas grandes`() {
        // El tablero que deja el MEGA: la reposición trae solo fichas de 6 casillas o más (6 desde P-315:
        // la banda baja tres tamaños bajo el mayor para que entren 2×3 y L6).
        val trays = trays(emptySet(), 200)
        trays.forEach { plan ->
            assertTrue("bandeja en tablero vacío: ${plan.map { it.shape.size }}", plan.all { it.shape.size >= 6 })
            assertTrue(fitsTogether(plan, emptySet()))
        }
        assertTrue("P-315: las de 6 casillas no salen nunca", trays.flatten().any { it.shape.size == 6 })
    }

    @Test
    fun `la bandeja premiada trae fichas mas grandes que la normal`() {
        // P-316: tras una doble línea de la ficha, la banda se estrecha al mayor tamaño y el siguiente.
        // (La tercera puede bajar a 7 en el tablero vacío: solo hay una forma de 9 y una de 8, y la
        // bandeja no repite forma.)
        // Con el tablero vacío: con medio tablero el presupuesto (P-358b) iguala las dos bandejas.
        val blocked = emptySet<Position>()
        fun mean(rewarded: Boolean) = (0 until 200).flatMap { seed ->
            PieceTrayPlanner.plan(
                boardSize = GameState.GRID_SIZE, blockedCells = blocked, count = 3, level = 110,
                random = Random(seed), rewarded = rewarded,
            )
        }.map { it.shape.size }.average()
        val normal = mean(false)
        val premiada = mean(true)
        assertTrue("premiada $premiada no supera a la normal $normal", premiada > normal + 0.2)
    }

    @Test
    fun `con media tabla llena la mano no pasa de la mitad del hueco`() {
        // 50 % libre (un 8×4). P-358b (2026-09-28): la mano entera cabe en la MITAD de las casillas libres
        // (16), así que ya no salen tres fichas grandes; la primera sigue siendo la mayor que cabe.
        val blocked = bottomFull(4)
        trays(blocked, 200).forEach { plan ->
            val sizes = plan.map { it.shape.size }
            assertTrue("bandeja con un 8×4 libre: $sizes", sizes.sum() <= 16)
            assertTrue("la primera ya no es grande: $sizes", sizes.max() >= 6)
            assertTrue(fitsTogether(plan, blocked))
        }
    }

    @Test
    fun `la bandeja cabe entera siempre que el tablero lo permite`() {
        (0 until 300).forEach { seed ->
            val blocked = board(0.25f + (seed % 6) * 0.1f, seed)
            val plan = trays(blocked, 1).first()
            assertEquals(3, plan.size)
            assertTrue("seed=$seed sizes=${plan.map { it.shape.size }}", fitsTogether(plan, blocked))
        }
    }

    @Test
    fun `un unico hueco de 3x3 no se lo lleva una sola ficha`() {
        // Sin reservar sitio para las demás ranuras, la primera ficha era el 3×3 y las otras dos ya no
        // cabían en ningún sitio.
        val blocked = allBoardCells().filterNot { it.x in 2..4 && it.y in 1..3 }.toSet()
        trays(blocked, 100).forEach { plan ->
            assertTrue("sizes=${plan.map { it.shape.size }}", fitsTogether(plan, blocked))
            assertTrue(plan.sumOf { it.shape.size } <= 9)
        }
    }

    @Test
    fun `el tablero limpio da fichas mas grandes que el lleno`() {
        val limpio = trays(emptySet(), 100).flatten().map { it.shape.size }.average()
        val lleno = (0 until 100).flatMap { trays(board(0.30f, it), 1).flatten() }.map { it.shape.size }.average()
        assertTrue("limpio=$limpio lleno=$lleno", limpio > lleno + 2.0)
    }

    @Test
    fun `una ficha pequeña solo sale si no cabe nada mayor`() {
        // Una fila entera libre y el resto lleno: cabe una barra de 4 (y la I5 no, 8 de ancho sí). En
        // una fila de 8 caben I4 + I3 + monominó juntas, o I5 + I3; nunca debería salir una de 1–2
        // habiendo sitio para mayores.
        val blocked = allBoardCells().filterNot { it.y == 7 }.toSet()
        trays(blocked, 60).forEach { plan ->
            assertTrue(fitsTogether(plan, blocked))
            assertTrue("la mayor es de ${plan.maxOf { it.shape.size }}", plan.maxOf { it.shape.size } >= 4)
        }
    }

    @Test
    fun `una ficha de la bandeja tapa el hueco de la linea a punto de cerrarse`() {
        val row = (0 until 6).map { Position(it, 7) }.toSet()
        val gaps = setOf(Position(6, 7), Position(7, 7))
        trays(row, 100, helpful = gaps).forEach { plan ->
            assertTrue(fitsTogether(plan, row))
            assertTrue("ninguna ficha tapa el hueco", plan.any { p -> p.reservedCells.any { it in gaps } })
        }
    }

    @Test
    fun `una bandeja no repite forma`() {
        (trays(emptySet(), 100) + trays(bottomFull(5), 100)).forEach { plan ->
            val formas = plan.map { it.shape.sortedWith(compareBy({ p -> p.y }, { p -> p.x })) }
            assertEquals("bandeja con forma repetida: ${plan.map { it.shape.size }}", formas.size, formas.toSet().size)
        }
    }

    @Test
    fun `lo que entra cabe junto a lo que queda en la mano`() {
        // Solo un 3×6 libre y un 3×3 en la mano: la ficha nueva tiene que caber en los 3×3 restantes.
        val blocked = allBoardCells().filterNot { it.x in 0..2 && it.y in 0..5 }.toSet()
        val square = (0 until 3).flatMap { y -> (0 until 3).map { x -> Position(x, y) } }
        (0 until 60).forEach { seed ->
            val plan = PieceTrayPlanner.plan(
                boardSize = GameState.GRID_SIZE, blockedCells = blocked, count = 1, random = Random(seed),
                keptShapes = listOf(square),
            )
            val nueva = plan.single().shape
            assertTrue("no cabe junto al 3×3: $nueva", PieceTrayPlanner.fitsTogether(listOf(square, nueva), blocked))
        }
    }

    @Test
    fun catalogContainsNewFiveAndEightBlockShapes() {
        assertTrue(Piece.SHAPES.any { it.size == 5 })
        assertTrue(Piece.SHAPES.any { it.size == 8 })
    }

    // ── CORTE NINJA (Dojo) — bug del bloque 5, 2026-07-31 ────────────────────────────────────────
    // Con el tablero partido una ficha NO puede quedar a caballo entre las dos mitades.

    @Test
    fun `con el tablero partido ninguna ficha viene a caballo`() {
        val cut = 4
        trays(emptySet(), 60, splitAtX = cut).forEach { plan ->
            assertEquals(3, plan.size)
            assertTrue(fitsTogether(plan, emptySet(), splitAtX = cut))
        }
    }

    @Test
    fun `el corte sigue dando bandeja con una mitad llena`() {
        val cut = 4
        val blocked = buildSet {
            for (y in 0 until GameState.GRID_SIZE) for (x in 0 until cut) add(Position(x, y))
        }
        trays(blocked, 30, splitAtX = cut).forEach { plan ->
            assertEquals(3, plan.size)
            assertTrue(fitsTogether(plan, blocked, splitAtX = cut))
        }
    }

    /** Las filas de abajo [fullRows] llenas; el resto, un rectángulo vacío de lado a lado. */
    private fun bottomFull(fullRows: Int): Set<Position> = buildSet {
        for (y in GameState.GRID_SIZE - fullRows until GameState.GRID_SIZE)
            for (x in 0 until GameState.GRID_SIZE) add(Position(x, y))
    }

    private fun allBoardCells(): List<Position> = buildList {
        for (y in 0 until GameState.GRID_SIZE) {
            for (x in 0 until GameState.GRID_SIZE) add(Position(x, y))
        }
    }
}
