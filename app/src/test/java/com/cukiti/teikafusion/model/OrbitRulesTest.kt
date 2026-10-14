package com.cukiti.teikafusion.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class OrbitRulesTest {
    private val c=Position(2,2)
    private fun state()=GameState(level=HouseRule.levelsWith(HouseRule.COMET).first(),grid=(0..7).flatMap { y ->
        (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()+(Position(1,1) to CellContent.Wood(id="star",targetColor=TargetColor.RED,teikaSeed=true)))
    @Test fun `clockwise and reverse are permutations preserving every complete block`() {
        val s=state(); val p=requireNotNull(OrbitRules.plan(s,emptySet(),c,true)); val next=OrbitRules.apply(s.grid,p)
        assertEquals(s.grid[Position(1,1)],next[Position(3,1)]); assertEquals(CellContent.Empty,next[Position(1,1)])
        val reverse=requireNotNull(OrbitRules.plan(s.copy(grid=next),emptySet(),c,false))
        assertEquals(s.grid,OrbitRules.apply(next,reverse))
        assertEquals(s.grid.values.filterIsInstance<CellContent.Wood>().map { it.id }.sorted(),next.values.filterIsInstance<CellContent.Wood>().map { it.id }.sorted())
    }
    @Test fun `four rotations restore the original board and centre block stays at centre`() {
        val s=state(); var grid=s.grid+(c to CellContent.Wood(id="center",colorType=2))
        val initial=grid
        repeat(4) { grid=OrbitRules.apply(grid,requireNotNull(OrbitRules.plan(s.copy(grid=grid),emptySet(),c,true))) }
        assertEquals(initial,grid)
    }
    @Test fun `all anchored and armored cells block the entire footprint`() {
        val s=state(); val p=Position(3,3)
        val variants=listOf(s.copy(frozenCells=setOf(p)),s.copy(glacierFrostCells=setOf(p)),s.copy(hiddenWoodCells=setOf(p)),
            s.copy(kingLaneCells=setOf(p)),s.copy(starAstroTargetCells=setOf(p)),s.copy(gluttonDeadCells=mapOf(p to 1)),
            s.copy(grid=s.grid+(p to CellContent.Wood(armor=1))),s.copy(grid=s.grid+(p to CellContent.Wood(poisoned=true))),
            s.copy(grid=s.grid+(p to CellContent.Wood(coil=1))))
        variants.forEach { assertNull(OrbitRules.plan(it,emptySet(),c,true)) }
        assertNull(OrbitRules.plan(s,setOf(p),c,true))
        assertNotNull(OrbitRules.plan(s,setOf(Position(7,7)),c,true))
    }
    @Test fun `lava edges missing cells and Ninja cut cannot be crossed`() {
        val s=state()
        assertNull(OrbitRules.plan(s,emptySet(),Position(0,0),true))
        assertNull(OrbitRules.plan(s.copy(grid=s.grid-Position(3,3)),emptySet(),c,true))
        assertNull(OrbitRules.plan(s.copy(biomePrimary=BiomeType.VOLCANO,lavaRow=2),emptySet(),c,true))
        assertNull(OrbitRules.plan(s.copy(ninjaCutActive=true),emptySet(),Position(3,2),true))
        assertNotNull(OrbitRules.plan(s.copy(ninjaCutActive=true),emptySet(),c,true))
    }
    @Test fun `symmetric wood with different IDs and empty zones are not a useful move`() {
        val s=state(); val symmetric=s.copy(grid=s.grid+OrbitRules.cells(c).associateWith { CellContent.Wood() })
        assertNull(OrbitRules.plan(symmetric,emptySet(),c,true))
        assertNull(OrbitRules.plan(s.copy(grid=s.grid.mapValues { CellContent.Empty }),emptySet(),c,true))
        assertTrue(OrbitRules.hasMove(s,emptySet())); assertFalse(OrbitRules.hasMove(s,s.grid.keys))
    }
    @Test fun `corner orbits stay within their original footprint and reach exact preview`() {
        for(clockwise in listOf(true,false)) for(from in OrbitRules.cells(c)) {
            for(i in 0..100) {
                val (x,y)=OrbitRules.point(from,c,clockwise,i/100f)
                assertTrue(x in 1f..3f && y in 1f..3f)
            }
            val end=OrbitRules.point(from,c,clockwise,1f); val target=OrbitRules.destination(from,c,clockwise)
            assertEquals(target.x.toFloat(),end.first,.00001f); assertEquals(target.y.toFloat(),end.second,.00001f)
        }
    }
    @Test fun `legacy JSON clears Comet while a committed orbit remains pending and idempotent`() {
        val s=state(); val json=Json { allowStructuredMapKeys=true; encodeDefaults=true }
        val p=requireNotNull(OrbitRules.plan(s,emptySet(),c,true))
        val pending=s.copy(grid=OrbitRules.apply(s.grid,p),orbitCharged=true,orbitPending=true,cometMark=CometMark(c,1),cometWave=2)
        val restored=ReplacementMigration.restore(json.decodeFromString(GameState.serializer(),json.encodeToString(GameState.serializer(),pending)))
        assertTrue(restored.orbitPending); assertFalse(restored.orbitCharged); assertNull(restored.cometMark); assertEquals(0,restored.cometWave)
        assertEquals(pending.grid,restored.grid); assertEquals(restored,ReplacementMigration.restore(restored)); assertEquals(12,restored.replacementRulesVersion)
        val legacy=ReplacementMigration.restore(s.copy(cometMark=CometMark(c,0)))
        assertFalse(legacy.orbitCharged); assertFalse(legacy.orbitPending)
        val absent=json.decodeFromString(GameState.serializer(),"{}")
        assertFalse(absent.orbitCharged); assertFalse(absent.orbitPending)
    }
    @Test fun `a Teika touching only a sliver of the footprint blocks rotation`() {
        val s=state(); val b=TeikaBodySave("near",TeikaType.TEIKA_1,4.48f,2.5f,0)
        assertNull(OrbitRules.plan(s,OrbitRules.bodyCells(listOf(b),8,8),c,true))
        assertNotNull(OrbitRules.plan(s,OrbitRules.bodyCells(listOf(b.copy(x=4.51f)),8,8),c,true))
    }
    @Test fun `campaign ordinals slots and introductory text remain compatible`() {
        assertEquals(27,HouseRule.COMET.ordinal); assertEquals(28,HouseRule.entries.size)
        assertEquals(listOf(35,46,57,107,118,157,168,218,229,279),HouseRule.levelsWith(HouseRule.COMET).map(LevelMap::displayNumber))
        assertEquals("GIRO ORBITAL",HouseRule.COMET.badge)
        assertTrue(OrbitRules.appliesTo(state().level)); assertFalse(CometRules.appliesTo(state().level))
    }
}
