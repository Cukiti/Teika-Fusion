package com.cukiti.teikafusion.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class TravelReplacementRulesTest {
    private fun grid()=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()
    @Test fun `surf only sees real removed horizontal supports not columns ice or distant bodies`() {
        val row=(0..7).map { Position(it,4) }.toSet()
        assertTrue(SurfRules.supports(3.5f,3.5f,.5f,0f,listOf(row),row))
        assertFalse(SurfRules.supports(3.5f,3.5f,.5f,0f,listOf(row),emptySet()))
        assertFalse(SurfRules.supports(3.5f,2f,.5f,0f,listOf(row),row))
        assertFalse(SurfRules.supports(3.5f,3.5f,.5f,5f,listOf(row),row))
        assertFalse(SurfRules.supports(3.5f,3.5f,.5f,0f,listOf((0..7).map { Position(3,it) }.toSet()),row))
    }
    @Test fun `surf direction alternates once per pass`() {
        assertEquals(-SurfRules.direction(50,0),SurfRules.direction(50,1))
        assertEquals(SurfRules.direction(50,0),SurfRules.direction(50,2))
    }
    @Test fun `comet stops at armor poison coils protected cells and ninja wall`() {
        val mark=CometMark(Position(1,3),0)
        for(protection in listOf(CellContent.Wood(armor=1),CellContent.Wood(poisoned=true),CellContent.Wood(coil=2))) {
            val board=grid()+ (Position(4,3) to protection)
            assertEquals(listOf(Position(2,3),Position(3,3)),CometRules.ray(mark,board,emptySet()))
        }
        assertEquals(listOf(Position(2,3)),CometRules.ray(mark,grid(),setOf(Position(3,3))))
        assertEquals(listOf(Position(2,3),Position(3,3)),CometRules.ray(mark,grid(),emptySet(),splitAtX=4))
        assertTrue(CometRules.ray(mark.copy(direction=-1),grid(),emptySet()).isEmpty())
    }
    @Test fun `comet keeps empty travel cells and breaks only announced lane`() {
        val path=CometRules.ray(CometMark(Position(3,5),3),grid(),emptySet())
        assertEquals((4 downTo 0).map { Position(3,it) },path)
        assertEquals(600,CometRules.hit(path.lastIndex,path.size))
        assertTrue(CometRules.hit(0,path.size)>180)
    }
    @Test fun `seeding favors useful lanes and has a bounded no space result`() {
        val board=grid()+((1..6).associate { Position(it,2) to CellContent.Wood() })
        val mark=requireNotNull(CometRules.seed(32,0,board,emptySet()))
        assertEquals(mark,CometRules.seed(32,0,board,emptySet()))
        assertTrue(CometRules.ray(mark,board,emptySet()).count { board[it] is CellContent.Wood }>=5)
        assertNull(CometRules.seed(32,0,board,board.keys))
    }
    @Test fun `old pinata and glass saves are cleared without replacing their tray or IDs`() {
        val level=HouseRule.levelsWith(HouseRule.COMET).first()
        val state=GameState(level=level,grid=grid(),pinataCell=Position(2,2),glassCells=setOf(Position(3,3)),glassCracked=setOf(Position(3,3)),availablePieces=listOf(Piece(shape=listOf(Position(0,0)))))
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; ignoreUnknownKeys=true }
        val restored=ReplacementMigration.restore(json.decodeFromString<GameState>(json.encodeToString(state)))
        assertNull(restored.pinataCell); assertTrue(restored.glassCells.isEmpty()); assertTrue(restored.glassCracked.isEmpty())
        assertEquals(state.availablePieces,restored.availablePieces); assertNull(restored.cometMark)
        val next=restored.copy(cometWave=2,cometMark=CometMark(Position(5,4),2))
        assertNull(ReplacementMigration.restore(json.decodeFromString<GameState>(json.encodeToString(next))).cometMark)
    }
    @Test fun `campaign slots stay unchanged`() {
        assertEquals(25,HouseRule.SURF.ordinal); assertEquals(27,HouseRule.COMET.ordinal)
        assertEquals(28,HouseRule.entries.size)
        assertEquals(listOf(2,52,63,74,113,124,174,185,235,246,285),HouseRule.levelsWith(HouseRule.SURF).map(LevelMap::displayNumber))
        assertEquals(listOf(35,46,57,107,118,157,168,218,229,279),HouseRule.levelsWith(HouseRule.COMET).map(LevelMap::displayNumber))
    }
    @Test fun `legacy comet marks are removed without granting an orbital charge`() {
        val level=HouseRule.levelsWith(HouseRule.COMET).first()
        val state=GameState(level=level,grid=grid(),cometMark=CometMark(Position(3,0),9),cometWave=-2,
            biomePrimary=BiomeType.VOLCANO,lavaRow=3,frozenCells=setOf(Position(1,4)),kingLaneCells=setOf(Position(2,5)))
        val restored=ReplacementMigration.restore(state)
        assertNull(restored.cometMark); assertEquals(0,restored.cometWave)
        assertEquals(12,restored.replacementRulesVersion)
        assertEquals(state.grid,restored.grid); assertFalse(restored.orbitCharged)
    }
}
