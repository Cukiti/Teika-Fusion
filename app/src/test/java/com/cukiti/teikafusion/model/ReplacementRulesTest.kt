package com.cukiti.teikafusion.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class ReplacementRulesTest {
    private val GameStateJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; allowStructuredMapKeys = true }
    private val a = Position(0, 0)
    private val b = Position(1, 0)
    private val c = Position(2, 0)

    @Test fun `replacement slots keep exactly the former levels`() {
        val expected = mapOf(
            HouseRule.DOMINO to listOf(12,23,73,84,95,134,145,195,206,256,267),
            HouseRule.CONSTELLATION to listOf(1,51,62,112,123,173,184,234,245,284),
            HouseRule.TRAMPOLINE to listOf(8,19,69,80,130,141,180,191,202,241,252),
            HouseRule.PRECISION_CUT to listOf(7,18,68,79,129,140,179,190,201,240,251))
        expected.forEach { (rule, levels) -> assertEquals(levels, HouseRule.levelsWith(rule).map(LevelMap::displayNumber)) }
        assertEquals(28, HouseRule.entries.size)
    }

    @Test fun `domino follows arrows and never hits the same block twice`() {
        val original = mapOf(a to CellContent.Wood(dominoDir = 0), b to CellContent.Wood(dominoDir = 0), c to CellContent.Wood(dominoDir = 2))
        val hits = DominoRules.cascade(original, original + (a to CellContent.Empty), emptySet())
        assertEquals(listOf(b,c), hits.map { it.cell })
        assertTrue(hits.all { it.broken })
    }

    @Test fun `armor takes a hit but stops the cascade`() {
        val original = mapOf(a to CellContent.Wood(dominoDir = 0), b to CellContent.Wood(dominoDir = 0, armor = 1), c to CellContent.Wood())
        val hits = DominoRules.cascade(original, original + (a to CellContent.Empty), emptySet())
        assertEquals(listOf(b), hits.map { it.cell }); assertFalse(hits.single().broken)
    }

    @Test fun `ice and coils cannot be bypassed by domino`() {
        val original = mapOf(a to CellContent.Wood(dominoDir = 0), b to CellContent.Wood(dominoDir = 0, coil = 1), c to CellContent.Wood())
        assertTrue(DominoRules.cascade(original, original + (a to CellContent.Empty), emptySet()).isEmpty())
        assertTrue(DominoRules.cascade(original, original + (a to CellContent.Empty), setOf(b)).isEmpty())
    }

    @Test fun `already stripped crystal is protected for the rest of the clear`() {
        val original = mapOf(a to CellContent.Wood(dominoDir = 0), b to CellContent.Wood(armor = 1))
        assertTrue(DominoRules.cascade(original, mapOf(a to CellContent.Empty, b to CellContent.Wood()), setOf(b)).isEmpty())
    }

    @Test fun `constellation draws only inside its endpoint rectangle`() {
        val points = listOf(Position(0, 0), Position(3, 2), Position(1, 4))
        val path = ConstellationRules.path(points)
        assertTrue(path.containsAll(points)); assertTrue(path.all { it.x in 0..3 && it.y in 0..4 })
        assertEquals(10, path.size)
    }

    @Test fun `constellation placement is deterministic and uses legal free cells`() {
        val free = setOf(a,b,c,Position(4,4))
        val points = ConstellationRules.points(8,0,free)
        assertEquals(3, points.size); assertTrue(free.containsAll(points))
        assertEquals(points, ConstellationRules.points(8,0,free.reversed().toSet()))
    }

    @Test fun `cut retains every annotated block without duplication`() {
        val shape = listOf(a,b,Position(0,1),Position(1,1))
        val piece = Piece(shape = shape, targetMask = listOf(TargetColor.RED, null, null, null),
            crystalMask = listOf(false,true,false,false), teikaMask = listOf(false,false,true,false), barrelMask = listOf(false,false,false,true))
        val (left,right) = requireNotNull(PrecisionCut.split(piece))
        assertEquals(shape.size, left.shape.size + right.shape.size)
        assertEquals(1, (left.targetMask + right.targetMask).count { it == TargetColor.RED })
        assertEquals(1, (left.crystalMask + right.crystalMask).count { it })
        assertEquals(1, (left.teikaMask + right.teikaMask).count { it })
        assertEquals(1, (left.barrelMask + right.barrelMask).count { it })
        assertNotEquals(piece.id,left.id); assertNotEquals(left.id,right.id)
    }

    @Test fun `cut never makes disconnected halves across the entire shape catalogue`() {
        Piece.SHAPES.forEach { shape ->
            val split = PrecisionCut.split(Piece(shape = shape)) ?: return@forEach
            assertEquals(shape.size, split.first.shape.size + split.second.shape.size)
            for (part in listOf(split.first, split.second)) {
                val seen = mutableSetOf(part.shape.first())
                repeat(part.shape.size) {
                    part.shape.forEach { p -> if (DominoRules.steps.any { Position(p.x + it.x,p.y + it.y) in seen }) seen += p }
                }
                assertEquals(part.shape.toSet(), seen)
            }
        }
    }

    @Test fun `fichona and whole piece powers cannot be cut`() {
        val piece = Piece(shape = listOf(a,b))
        assertNull(PrecisionCut.split(piece.copy(isFichona = true)))
        assertNull(PrecisionCut.split(piece.copy(isStarWish = true)))
        assertNull(PrecisionCut.split(piece.copy(isBrush = true)))
        assertNull(PrecisionCut.split(Piece(shape = listOf(a))))
    }

    @Test fun `new save keeps constellation progress and both cut halves`() {
        val halves = requireNotNull(PrecisionCut.split(Piece(shape = listOf(a,b,c))))
        val state = GameState(replacementRulesVersion = 1, constellationPoints = listOf(a,b,c), constellationCovered = setOf(a),
            cutReserve = halves.second, cutActiveId = halves.first.id, cutRefill = 7, trampolineCharged = true)
        val restored = GameStateJson.decodeFromString<GameState>(GameStateJson.encodeToString(state))
        assertEquals(state.constellationCovered, restored.constellationCovered)
        assertEquals(state.cutReserve, restored.cutReserve); assertEquals(7,restored.cutRefill); assertTrue(restored.trampolineCharged)
    }

    @Test fun `old saves retain wood and stars while retired effects are removed`() {
        val level = HouseRule.levelsWith(HouseRule.TRAMPOLINE).first()
        val piece = Piece(shape = listOf(a), isMagnet = true)
        val state = GameState(level = level, availablePieces = listOf(piece), magnetTurnsLeft = 2,
            grid = mapOf(a to CellContent.Wood(stubborn = true, magnet = true, targetColor = TargetColor.RED)), moldAt = a, moldShape = listOf(a))
        val restored = ReplacementMigration.restore(state)
        val wood = restored.grid[a] as CellContent.Wood
        assertFalse(wood.stubborn); assertFalse(wood.magnet); assertEquals(TargetColor.RED,wood.targetColor)
        assertFalse(restored.availablePieces.single().isMagnet); assertEquals(piece.id,restored.availablePieces.single().id)
        assertEquals(0,restored.magnetTurnsLeft); assertNull(restored.moldAt); assertNull(restored.trampolineCell)
    }

    @Test fun `migration does not add arrows to ordinary wood in new saves`() {
        val level = HouseRule.levelsWith(HouseRule.DOMINO).first()
        val s = GameState(level = level, replacementRulesVersion = 1, grid = mapOf(a to CellContent.Wood(), b to CellContent.Wood()))
        assertEquals(-1,(ReplacementMigration.restore(s).grid[a] as CellContent.Wood).dominoDir)
    }
    @Test fun `both cut choices preserve every special block across the catalogue`() {
        Piece.SHAPES.forEach { shape ->
            val piece=Piece(shape=shape,targetMask=shape.indices.map { if(it%2==0) TargetColor.RED else null },teikaMask=shape.indices.map { it%3==0 })
            repeat(minOf(2,PrecisionCut.separators(piece).size)) { choice ->
                val (a,b)=requireNotNull(PrecisionCut.split(piece,choice))
                assertEquals(shape.size,a.shape.size+b.shape.size)
                assertEquals(piece.targetMask.count { it!=null },(a.targetMask+b.targetMask).count { it!=null })
                assertEquals(piece.teikaMask.count { it },(a.teikaMask+b.teikaMask).count { it })
            }
        }
        val square=Piece(shape=listOf(Position(0,0),Position(1,0),Position(0,1),Position(1,1)))
        assertNotEquals(PrecisionCut.separator(square,0),PrecisionCut.separator(square,1))
        assertNull(PrecisionCut.split(square,2))
    }
    @Test fun `constellation trace is continuous and searches useful wood paths`() {
        val points=listOf(Position(0,0),Position(3,2),Position(1,4))
        ConstellationRules.trace(points).zipWithNext().forEach { (a,b) -> assertTrue(kotlin.math.abs(a.x-b.x)+kotlin.math.abs(a.y-b.y)<=1) }
        val free=(0..4).flatMap { y -> (0..4).map { x -> Position(x,y) } }.toSet()-Position(2,2)
        val selected=ConstellationRules.points(8,0,free,setOf(Position(2,2)))
        assertTrue(Position(2,2) in ConstellationRules.path(selected))
        assertTrue(selected.all { it in free })
    }
    @Test fun `retired domino no longer seeds arrows in its preserved slot`() {
        val level=HouseRule.levelsWith(HouseRule.DOMINO).first()
        assertFalse(DominoRules.appliesTo(level))
        assertEquals(-1,DominoRules.seed(Position(0,0),setOf(Position(1,0)),level))
        assertTrue(WreckingBallRules.appliesTo(level))
    }

}
