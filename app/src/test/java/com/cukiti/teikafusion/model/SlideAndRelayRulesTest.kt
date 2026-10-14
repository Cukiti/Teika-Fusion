package com.cukiti.teikafusion.model

import org.junit.Assert.*
import org.junit.Test

class SlideAndRelayRulesTest {
    private fun grid(): Map<Position,CellContent> = (0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()
    private val row=(0..7).map { Position(it,5) }.toSet()
    @Test fun `slide carries a chain into its vacancy one step without wrapping or collisions`() {
        val board=grid()+(Position(1,4) to CellContent.Wood())+(Position(2,4) to CellContent.Wood())+(Position(7,6) to CellContent.Wood())
        val moves=SlideRules.moves(board,row,emptySet(),1)
        assertEquals(listOf(Position(2,4) to Position(3,4),Position(1,4) to Position(2,4)),moves)
        assertEquals(moves.size,moves.map { it.second }.distinct().size)
        assertTrue(moves.all { (a,b) -> a.y==b.y && b.x-a.x==1 })
        assertTrue(SlideRules.moves(board,emptySet(),emptySet(),1).isEmpty())
    }
    @Test fun `slide respects anchors occupied destination ninja halves and actual cleared span`() {
        val board=grid()+(Position(3,4) to CellContent.Wood())+(Position(6,4) to CellContent.Wood())
        assertTrue(SlideRules.moves(board,row,board.keys,1).isEmpty())
        assertFalse(SlideRules.moves(board,row,emptySet(),1,splitAtX=4).any { it.first.x==3 })
        val left=row.filterTo(HashSet()) { it.x<4 }
        assertFalse(SlideRules.moves(board,left,emptySet(),1).any { it.first.x>=4 })
        assertEquals(-SlideRules.direction(50,0),SlideRules.direction(50,1))
    }
    @Test fun `relay chooses the fullest reachable direction and retains every protection`() {
        val footprint=setOf(Position(3,3),Position(4,3))
        val board=grid().mapValues { CellContent.Wood() as CellContent }+
            (Position(3,2) to CellContent.Wood(armor=1))+(Position(4,2) to CellContent.Wood(poisoned=true))+
            (Position(3,4) to CellContent.Wood(coil=1))
        val cells=RelayRules.affected(footprint,board,setOf(Position(4,4)))
        assertEquals(listOf(Position(5,3),Position(6,3),Position(7,3)),cells)
        assertFalse(cells.any { it in footprint })
        assertFalse(RelayRules.affected(setOf(Position(3,3)),board,emptySet(),splitAtX=4).any { it.x>=4 })
    }
    @Test fun `relay cap and impacts are bounded and deterministic for a mega piece`() {
        val footprint=(0..7).map { Position(it,3) }.toSet()
        val board=grid().mapValues { CellContent.Wood() as CellContent }
        val cells=RelayRules.affected(footprint,board,emptySet())
        assertEquals(32,cells.size); assertEquals(cells,RelayRules.affected(footprint,board,emptySet()))
        assertTrue(RelayRules.hit(cells.lastIndex)<RelayRules.DURATION_MS)
    }
    @Test fun `wide discharge crosses vacancies but each protected lane stops independently`() {
        val shape=setOf(Position(1,2),Position(1,3),Position(1,4))
        val shield=Position(4,3)
        val board=grid()+(Position(3,2) to CellContent.Wood())+(Position(7,2) to CellContent.Wood())+
            (Position(3,3) to CellContent.Wood())+(shield to CellContent.Wood(armor=1))+
            (Position(5,3) to CellContent.Wood())+(Position(7,4) to CellContent.Wood())
        val shot=RelayRules.plan(shape,board,emptySet())
        assertEquals(0,shot.direction); assertEquals(3,shot.paths.size)
        assertEquals(setOf(Position(3,2),Position(7,2),Position(3,3),Position(7,4)),shot.targets.map { it.cell }.toSet())
        assertTrue(RelayRules.valid(shot,shape,8,8))
        assertEquals(shot.targets.map { it.cell },RelayRules.remaining(shot,board,emptySet()))
    }
    @Test fun `saved direction never retargets removed wood or destroys replacement IDs`() {
        val a=Position(3,3); val b=Position(5,3)
        val board=grid()+(a to CellContent.Wood())+(b to CellContent.Wood())
        val shot=RelayRules.plan(setOf(Position(1,3)),board,emptySet())
        assertEquals(listOf(b),RelayRules.remaining(shot,board+(a to CellContent.Empty),emptySet()))
        assertTrue(RelayRules.remaining(shot,board+(a to CellContent.Wood()),emptySet()).isEmpty())
        assertTrue(RelayRules.remaining(shot,board,setOf(a)).isEmpty())
        assertEquals(Position(2,3),RelayRules.survivingPaths(shot,board,setOf(a)).single().last())
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true }
        assertEquals(shot,json.decodeFromString<RelayShot>(json.encodeToString(shot)))
    }
    @Test fun `announced normal lines do not steal the discharge from remaining targets`() {
        val origin=Position(7,5); val above=Position(7,4)
        val line=(0..6).map { Position(it,5) }.toSet()
        val board=grid()+line.associateWith { CellContent.Wood() }+(above to CellContent.Wood())
        val projected=RelayRules.afterAnnouncedClear(board,line,emptySet())
        val shot=RelayRules.plan(setOf(origin),projected,emptySet())
        assertEquals(3,shot.direction); assertEquals(listOf(above),shot.targets.map { it.cell })
    }
    @Test fun `malformed saved rays and ninja crossing cannot become a shot`() {
        val shape=setOf(Position(3,3))
        val crossing=RelayShot(0,listOf(listOf(Position(3,3),Position(4,3))),listOf(RelayTarget(Position(4,3),"wood")))
        assertFalse(RelayRules.valid(crossing,shape,8,8,4))
        assertFalse(RelayRules.valid(crossing.copy(direction=9),shape,8,8))
        val state=GameState(level=HouseRule.levelsWith(HouseRule.RELAY).first(),relayPendingFootprint=shape,relayPendingShot=crossing,ninjaCutActive=true)
        assertTrue(requireNotNull(ReplacementMigration.restore(state).relayPendingShot).targets.isEmpty())
    }
    @Test fun `migration preserves pending work and charge without replacing twins shapes or IDs`() {
        val level=HouseRule.levelsWith(HouseRule.RELAY).first()
        val piece=Piece(id="same-shape-a",shape=listOf(Position(0,0),Position(1,0)))
        val state=GameState(level=level,grid=grid(),availablePieces=listOf(piece,piece.copy(id="same-shape-b")),relayCharged=true,
            relayPendingFootprint=setOf(Position(3,3)),relayProtectedCells=setOf(Position(3,2)))
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; ignoreUnknownKeys=true }
        val restored=ReplacementMigration.restore(json.decodeFromString<GameState>(json.encodeToString(state)))
        assertEquals(state.availablePieces,restored.availablePieces); assertTrue(restored.relayCharged)
        assertEquals(state.relayPendingFootprint,restored.relayPendingFootprint); assertEquals(state.relayProtectedCells,restored.relayProtectedCells)
        assertEquals(12,restored.replacementRulesVersion)
        val slide=restored.copy(level=HouseRule.levelsWith(HouseRule.SLIDE).first(),slidePendingCells=row,slideWave=2)
        val fixed=ReplacementMigration.restore(slide)
        assertTrue(fixed.slidePendingCells.isEmpty()); assertEquals(0,fixed.slideWave); assertFalse(fixed.turbineCharged); assertFalse(fixed.relayCharged)
        assertTrue(fixed.relayPendingFootprint.isEmpty()); assertTrue(fixed.relayProtectedCells.isEmpty())
    }
    @Test fun `two replacement slots keep the exact visible campaign levels`() {
        assertEquals(28,HouseRule.entries.size); assertEquals(8,HouseRule.SLIDE.ordinal); assertEquals(4,HouseRule.RELAY.ordinal)
        assertEquals(listOf(36,47,58,108,119,158,169,219,230,280),HouseRule.levelsWith(HouseRule.SLIDE).map(LevelMap::displayNumber))
        assertEquals(listOf(9,20,70,81,131,142,192,203,242,253),HouseRule.levelsWith(HouseRule.RELAY).map(LevelMap::displayNumber))
        assertTrue((1..LevelMap.totalLevels).none(TwinTrayRules::appliesTo))
        assertTrue(HouseRule.levelsWith(HouseRule.SLIDE).none(BoardCurrent::appliesTo))
    }
}
