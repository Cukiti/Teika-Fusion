package com.cukiti.teikafusion.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class WorkshopPlannerTest {
    private val square = listOf(Position(0, 0), Position(1, 0), Position(0, 1), Position(1, 1))
    private fun plan(sources: List<Piece>, bank: BlockBank = BlockBank(), blocked: Set<Position> = emptySet()) =
        WorkshopPlanner.plan(bank, sources, 8, 8) { p, x, y ->
            p.shape.all { Position(x + it.x, y + it.y).let { cell -> cell.x in 0..7 && cell.y in 0..7 && cell !in blocked } }
        }

    @Test fun `presupuesto por ficha y sobrantes sin truncar a nueve`() {
        val bank = BlockBank(blocks = 40)
        val sources = List(3) { Piece(shape = square) }
        val result = requireNotNull(plan(sources, bank))
        assertEquals(49, result.available)
        assertEquals(1, result.pieces.size)
        assertEquals(16, result.used)
        assertTrue(result.pieces.single().isFichona)
        assertEquals(result.available, result.used + result.remainder)
        assertTrue(result.remainder > 9)
        assertTrue(result.pieces.none { it.id in sources.map { it.id } })
    }

    @Test fun `una ficha disponible fabrica Fichona sin inventar material`() {
        val result = requireNotNull(plan(listOf(Piece(shape = square))))
        assertEquals(3, result.available)
        assertEquals(3, result.used)
        assertEquals(0, result.remainder)
        assertNull(plan(listOf(Piece(shape = listOf(Position(0, 0), Position(1, 0))))))
    }

    @Test fun `no devuelve tres cuando solo quedan dos casillas`() {
        val blocked = (0..7).flatMap { y -> (0..7).map { x -> Position(x, y) } }.toSet() - setOf(Position(0, 0), Position(7, 7))
        assertNull(plan(List(3) { Piece(shape = square) }, blocked = blocked))
    }

    @Test fun `estrellas semillas cristal y barriles se conservan incluso superpuestos`() {
        val sources = listOf(Piece(shape = square,
            targetMask = listOf(TargetColor.BLUE, null, TargetColor.entries.last(), null),
            crystalMask = listOf(true, false, false, false),
            barrelMask = listOf(false, true, false, false),
            teikaMask = listOf(true, false, false, false)))
        val result = requireNotNull(plan(sources, BlockBank(blocks = 9)))
        assertEquals(sources.flatMap { it.targetMask }.filterNotNull().sortedBy { it.ordinal },
            result.pieces.flatMap { it.targetMask }.filterNotNull().sortedBy { it.ordinal })
        assertEquals(1, result.pieces.sumOf { it.crystalMask.count { b -> b } })
        assertEquals(1, result.pieces.sumOf { it.barrelMask.count { b -> b } })
        assertEquals(1, result.pieces.sumOf { it.teikaMask.count { b -> b } })
    }

    @Test fun `poderes completos deben jugarse antes de fabricar`() {
        val base = Piece(shape = square)
        for (special in listOf(base.copy(isWildcard = true), base.copy(isBrush = true),
            base.copy(isChameleon = true), base.copy(isGrowing = true), base.copy(isMagnet = true), base.copy(isStarWish = true)))
            assertNull(plan(listOf(special), BlockBank(blocks = 40)))
    }

    @Test fun `elige la mayor zona conectada y no une huecos diagonales`() {
        val large = (0..3).flatMap { y -> (0..3).map { x -> Position(x, y) } }.toSet()
        val small = setOf(Position(5, 5), Position(6, 5), Position(5, 6))
        val all = (0..7).flatMap { y -> (0..7).map { x -> Position(x, y) } }.toSet()
        val result = requireNotNull(plan(List(3) { Piece(shape = square) }, BlockBank(blocks = 20), all - large - small))
        assertEquals(16, result.used)
        assertEquals(large, result.reservations.single())
    }

    @Test fun `contactos diagonales no forman una region grande`() {
        val free = setOf(Position(0, 0), Position(1, 0), Position(2, 1), Position(3, 1))
        val all = (0..7).flatMap { y -> (0..7).map { x -> Position(x, y) } }.toSet()
        assertNull(plan(List(3) { Piece(shape = square) }, BlockBank(blocks = 40), all - free))
    }

    @Test fun `no borra anotaciones cuando hay mas especiales que capacidad`() {
        val source = Piece(shape = List(17) { Position(it % 8, it / 8) }, targetMask = List(17) { TargetColor.BLUE })
        assertNull(plan(listOf(source), BlockBank(blocks = 40)))
    }

    @Test fun `cada reserva coincide con la orientacion mostrada y ninguna se solapa`() {
        val random = Random(450)
        repeat(120) {
            val blocked = (0..7).flatMap { y -> (0..7).map { x -> Position(x, y) } }.filterTo(mutableSetOf()) { random.nextBoolean() }
            val result = plan(List(3) { Piece(shape = square) }, blocked = blocked) ?: return@repeat
            val union = mutableSetOf<Position>()
            result.pieces.zip(result.reservations).forEach { (p, cells) ->
                val x = cells.minOf { it.x }; val y = cells.minOf { it.y }
                assertEquals(cells, p.shape.mapTo(mutableSetOf()) { Position(x + it.x, y + it.y) })
                assertTrue(cells.none { it in blocked || it in union })
                union.addAll(cells)
            }
            assertTrue(result.used <= result.available)
        }
    }

    @Test fun `datos antiguos y sobrantes nuevos se restauran con las mismas claves`() {
        val json = Json { ignoreUnknownKeys = true }
        val old = json.decodeFromString<BlockBank>("""{"blocks":8,"colorType":2,"usableFrom":7,"lockedUntil":27}""")
        assertEquals(8, old.blocks)
        assertEquals(20, BlockBankRules.turnsLocked(old, 7))
        assertEquals(BlockBank(), json.decodeFromString<BlockBank>("{}"))
        val newer = old.copy(blocks = 42)
        assertEquals(newer, json.decodeFromString<BlockBank>(json.encodeToString(newer)))
        val oldRound = json.decodeFromString<GameState>("""{"status":"PLAYING","level":16,"blockBank":{"blocks":8,"usableFrom":7,"lockedUntil":27},"availablePieces":[{"shape":[{"x":0,"y":0}]}]}""")
        assertEquals(8, oldRound.blockBank.blocks)
        assertFalse(oldRound.availablePieces.single().isHandmade)
        assertFalse(oldRound.availablePieces.single().isFichona)
        val round = oldRound.copy(blockBank = newer, availablePieces = listOf(Piece(shape = square,
            targetMask = listOf(TargetColor.BLUE, null, null, null), teikaMask = listOf(false, true, false, false), isHandmade = true, isFichona = true)))
        assertEquals(round, json.decodeFromString<GameState>(json.encodeToString(round)))
    }
}
