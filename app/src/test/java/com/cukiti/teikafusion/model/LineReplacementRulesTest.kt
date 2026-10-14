package com.cukiti.teikafusion.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class LineReplacementRulesTest {
    @Test fun `parallel lines have no intersection and no wave`() {
        val rows = listOf(0,1).map { y -> (0..7).mapTo(linkedSetOf()) { Position(it,y) } }
        assertTrue(ShockwaveRules.centers(rows).isEmpty())
    }

    @Test fun `wave stays in bounds and never crosses the ninja seam`() {
        assertEquals(8,ShockwaveRules.ring(setOf(Position(0,0)),8,8).size)
        val ring = ShockwaveRules.ring(setOf(Position(3,4)),8,8,4)
        assertTrue(ring.all { it.x < 4 })
        assertEquals(14,ring.size)
    }

    @Test fun `double line activation does not change other campaign rules`() {
        for (level in 1..LevelMap.totalLevels) {
            assertFalse(AvalancheRules.triggered(level,1))
            assertEquals(HouseRule.forLevel(level) == HouseRule.AVALANCHE,AvalancheRules.triggered(level,2))
        }
        assertEquals(17,HouseRule.SHOCKWAVE.ordinal)
        assertEquals(20,HouseRule.AVALANCHE.ordinal)
    }

    @Test fun `pending collapse survives save while old barrel powers are stripped without losing masks`() {
        val json = kotlinx.serialization.json.Json { allowStructuredMapKeys = true; ignoreUnknownKeys = true }
        val p = Position(0,0)
        val state = GameState(level = HouseRule.levelsWith(HouseRule.AVALANCHE).first(), avalanchePending = true,
            grid = mapOf(p to CellContent.Wood(barrel = true,targetColor = TargetColor.RED)),
            availablePieces = listOf(Piece(shape = listOf(p), barrelMask = listOf(true), targetMask = listOf(TargetColor.BLUE),teikaMask = listOf(true))))
        val decoded = json.decodeFromString<GameState>(json.encodeToString(state))
        val restored = ReplacementMigration.restore(decoded)
        assertTrue(restored.avalanchePending)
        assertFalse((restored.grid[p] as CellContent.Wood).barrel)
        assertEquals(TargetColor.RED,(restored.grid[p] as CellContent.Wood).targetColor)
        assertTrue(restored.availablePieces.single().barrelMask.isEmpty())
        assertEquals(state.availablePieces.single().targetMask,restored.availablePieces.single().targetMask)
        assertEquals(state.availablePieces.single().teikaMask,restored.availablePieces.single().teikaMask)
        assertEquals(restored,ReplacementMigration.restore(restored))
    }
    @Test fun `wave selects at most eight legal blocks nearest the intersections`() {
        val center=setOf(Position(4,4))
        val eligible=ShockwaveRules.ring(center,8,8)-Position(3,3)
        val hits=ShockwaveRules.affected(center,8,8,-1,eligible)
        assertEquals(8,hits.size)
        assertFalse(Position(3,3) in hits)
        assertEquals(1,ShockwaveRules.distance(hits.first(),center))
        assertEquals(2,ShockwaveRules.distance(hits.last(),center))
        assertTrue(ShockwaveRules.affected(emptySet(),8,8,-1,eligible).isEmpty())
    }
    @Test fun `avalanche accumulates only actual lines and restores one charge`() {
        val level=HouseRule.levelsWith(HouseRule.AVALANCHE).first()
        assertEquals(1,AvalancheRules.charge(level,0,1))
        assertEquals(1,AvalancheRules.charge(level,1,0))
        assertEquals(2,AvalancheRules.charge(level,1,1))
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; encodeDefaults=true }
        val saved=GameState(level=level,avalancheCharge=1,replacementRulesVersion=1)
        assertEquals(1,ReplacementMigration.restore(json.decodeFromString<GameState>(json.encodeToString(saved))).avalancheCharge)
        assertEquals(0,ReplacementMigration.restore(saved.copy(level=HouseRule.levelsWith(HouseRule.DOMINO).first())).avalancheCharge)
    }

}
