package com.cukiti.teikafusion.model

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json

class CompactorRulesTest {
    private fun state(): GameState = GameState(level=HouseRule.levelsWith(HouseRule.PRECISION_CUT).first(),
        grid=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap())
    @Test fun `all directions preserve order and compact the whole reachable segment`() {
        for(d in 0..3) {
            val s=state(); val horizontal=d%2==0
            fun p(i:Int)=if(horizontal) Position(i,2) else Position(2,i)
            val plan=requireNotNull(CompactorRules.plan(s.copy(grid=s.grid+mapOf(p(1) to CellContent.Wood(),p(3) to CellContent.Wood())),emptySet(),2,2,d))
            assertEquals(listOf(p(1),p(3)),plan.moves.map { it.first }.sortedBy { if(horizontal) it.x else it.y })
            assertEquals(if(d<2) setOf(p(6),p(7)) else setOf(p(0),p(1)),plan.moves.map { it.second }.toSet())
        }
    }
    @Test fun `first obstacle stops the press and everything beyond survives`() {
        val s=state(); val a=Position(0,1); val b=Position(5,1)
        val grid=s.grid+mapOf(a to CellContent.Wood(),b to CellContent.Wood())
        val plan=requireNotNull(CompactorRules.plan(s.copy(grid=grid),setOf(Position(3,1)),0,1,0))
        assertEquals(listOf(a to Position(2,1)),plan.moves)
        assertFalse(b in plan.cells)
        assertTrue(CompactorRules.plan(s.copy(grid=grid),setOf(a),0,1,0)!!.moves.isEmpty())
    }
    @Test fun `anchors Ninja lava and invalid gestures cannot be crossed`() {
        val s=state(); val a=Position(0,1)
        val g=s.grid+(a to CellContent.Wood())
        assertEquals(Position(3,1),CompactorRules.plan(s.copy(grid=g,ninjaCutActive=true),emptySet(),0,1,0)!!.moves.single().second)
        for(blocked in listOf(s.copy(grid=g,frozenCells=setOf(Position(2,1))),s.copy(grid=g,glacierFrostCells=setOf(Position(2,1))),
            s.copy(grid=g,hiddenWoodCells=setOf(Position(2,1)))))
            assertEquals(Position(1,1),CompactorRules.plan(blocked,emptySet(),0,1,0)!!.moves.single().second)
        assertTrue(CompactorRules.plan(s.copy(grid=g,biomePrimary=BiomeType.VOLCANO,lavaRow=2),emptySet(),0,1,0)!!.moves.isEmpty())
        assertNull(CompactorRules.plan(s,emptySet(),-1,0,0)); assertNull(CompactorRules.plan(s,emptySet(),0,0,4))
        assertFalse(CompactorRules.hasMove(s,emptySet()))
    }
    @Test fun `old cut saves conserve the hidden half and migration is idempotent`() {
        val p=Piece(shape=listOf(Position(0,0)),targetMask=listOf(TargetColor.RED),teikaMask=listOf(true))
        val q=Piece(shape=listOf(Position(0,0),Position(1,0)),crystalMask=listOf(true,false))
        val s=state().copy(availablePieces=listOf(p),cutReserve=q,cutActiveId=p.id,cutRefill=3,compactorCharged=true)
        val restored=ReplacementMigration.restore(s)
        assertNull(restored.cutReserve); assertNull(restored.cutActiveId)
        assertEquals(listOf(p,q),restored.availablePieces); assertTrue(restored.compactorCharged)
        assertEquals(restored,ReplacementMigration.restore(restored))
        val full=ReplacementMigration.restore(s.copy(availablePieces=listOf(p,Piece(shape=listOf(Position(0,0))),Piece(shape=listOf(Position(0,0))))))
        assertEquals(4,full.availablePieces.size); assertEquals(4,full.trayCapacity)
        val json=Json { allowStructuredMapKeys=true; encodeDefaults=true }
        assertEquals(restored,json.decodeFromString(GameState.serializer(),json.encodeToString(GameState.serializer(),restored)))
    }
    @Test fun `campaign slots and dormant cut stay stable`() {
        assertEquals(listOf(7,18,68,79,129,140,179,190,201,240,251),HouseRule.levelsWith(HouseRule.PRECISION_CUT).map { it-7 })
        assertFalse(PrecisionCut.appliesTo(HouseRule.levelsWith(HouseRule.PRECISION_CUT).first()))
        assertEquals(14,HouseRule.PRECISION_CUT.ordinal)
        assertEquals("compactor",MechanicCapsule.PRECISION_CUT.id)
    }
    @Test fun `random corridors preserve identities without overlap or moving anchors`() {
        val random=kotlin.random.Random(91)
        repeat(100) {
            val base=state()
            val grid=base.grid.mapValues { if(random.nextBoolean()) CellContent.Wood() else CellContent.Empty }
            val anchors=grid.keys.filterTo(HashSet()) { random.nextInt(10)==0 }
            val s=base.copy(grid=grid,frozenCells=anchors)
            for(d in 0..3) {
                val plan=requireNotNull(CompactorRules.plan(s,emptySet(),3,3,d))
                val out=grid.toMutableMap()
                val carried=plan.moves.map { it to out.getValue(it.first) }
                carried.forEach { (m,_) -> out[m.first]=CellContent.Empty }
                carried.forEach { (m,w) -> assertTrue(out[m.second] is CellContent.Empty); out[m.second]=w }
                assertEquals(grid.values.filterIsInstance<CellContent.Wood>().map { it.id }.toSet(),out.values.filterIsInstance<CellContent.Wood>().map { it.id }.toSet())
                assertEquals(grid.values.count { it is CellContent.Wood },out.values.count { it is CellContent.Wood })
                anchors.forEach { assertEquals(grid[it],out[it]) }
            }
        }
    }

}
