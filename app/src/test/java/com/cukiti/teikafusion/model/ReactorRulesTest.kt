package com.cukiti.teikafusion.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class ReactorRulesTest {
    private val GameStateJson=kotlinx.serialization.json.Json { ignoreUnknownKeys=true; allowStructuredMapKeys=true; encodeDefaults=true }
    private fun state(cells: Map<Position,CellContent>) = GameState(level=HouseRule.levelsWith(HouseRule.TRAIL).first(),
        grid=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()+cells)
    private val group=listOf(Position(2,2),Position(3,2),Position(2,3))
    @Test fun `sides connect using wood color regardless of star and seed`() {
        val s=state(group.mapIndexed { i,p -> p to CellContent.Wood(colorType=2,targetColor=if(i==0) TargetColor.RED else TargetColor.BLUE,teikaSeed=i==2) }.toMap()+
            mapOf(Position(3,3) to CellContent.Wood(colorType=3),Position(1,1) to CellContent.Wood(colorType=2)))
        val plan=requireNotNull(ReactorRules.plan(s,emptySet(),group.first()))
        assertEquals(group.toSet(),plan.cells.toSet()); assertEquals(listOf(0,1,1),plan.depths)
        assertEquals(2,plan.links.size); assertEquals(plan.hit(1),plan.hit(2))
    }
    @Test fun `all protections interrupt connectivity and remain unchanged`() {
        val positions=(0..4).map { Position(it,2) }
        val base=state(positions.associateWith { CellContent.Wood(colorType=1) })
        val anchor=positions[2]
        val variants=listOf(base.copy(frozenCells=setOf(anchor)),base.copy(glacierFrostCells=setOf(anchor)),
            base.copy(kingLaneCells=setOf(anchor)),base.copy(hiddenWoodCells=setOf(anchor)),
            base.copy(starAstroTargetCells=setOf(anchor)),base.copy(starAstroMoonWoodCells=setOf(anchor)),
            base.copy(grid=base.grid+(anchor to CellContent.Wood(colorType=1,armor=1))),
            base.copy(grid=base.grid+(anchor to CellContent.Wood(colorType=1,coil=1))),
            base.copy(grid=base.grid+(anchor to CellContent.Wood(colorType=1,poisoned=true))),
            base.copy(grid=base.grid+(anchor to CellContent.Wood(colorType=1,magma=true))),
            base.copy(grid=base.grid+(anchor to CellContent.Wood(colorType=1,stubborn=true))))
        variants.forEach { assertNull(ReactorRules.plan(it,emptySet(),positions.first())); assertFalse(ReactorRules.hasGroup(it,emptySet())) }
        assertNull(ReactorRules.plan(base,setOf(anchor),positions.first()))
        assertNull(ReactorRules.plan(base.copy(biomePrimary=BiomeType.VOLCANO,lavaRow=3),emptySet(),positions.first()))
    }
    @Test fun `Ninja halves never connect and small or empty groups cannot spend charge`() {
        val s=state((2..5).associate { Position(it,3) to CellContent.Wood() }).copy(ninjaCutActive=true)
        assertFalse(ReactorRules.hasGroup(s,emptySet()))
        assertNull(ReactorRules.plan(s,emptySet(),Position(0,0)))
        assertNull(ReactorRules.plan(s,emptySet(),Position(-1,3)))
        assertNotNull(ReactorRules.plan(s.copy(ninjaCutActive=false),emptySet(),Position(2,3)))
    }
    @Test fun `whole board reaction is bounded and every edge increases depth`() {
        val s=state((0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Wood(colorType=4) } }.toMap())
        for(origin in s.grid.keys) {
            val p=requireNotNull(ReactorRules.plan(s,emptySet(),origin))
            assertEquals(64,p.cells.size); assertEquals(64,p.cells.distinct().size); assertEquals(63,p.links.size)
            p.links.forEach { (a,b) -> assertEquals(p.depths[p.cells.indexOf(a)]+1,p.depths[p.cells.indexOf(b)]) }
            assertTrue(p.durationMs<=1290)
        }
    }
    @Test fun `old saves clear Estela and new pending saves never recharge or repeat`() {
        val old=state(emptyMap()).copy(trailCells=group,reactorCharged=true,reactorPending=true)
        val saved=ReplacementMigration.restore(GameStateJson.decodeFromString<GameState>(GameStateJson.encodeToString(old)))
        assertTrue(saved.trailCells.isEmpty()); assertFalse(saved.reactorCharged); assertTrue(saved.reactorPending)
        assertEquals(saved,ReplacementMigration.restore(saved)); assertEquals(12,saved.replacementRulesVersion)
        val legacyJson=GameStateJson.encodeToString(old).replace("\"reactorCharged\":true,", "").replace("\"reactorPending\":true,", "")
        val legacy=ReplacementMigration.restore(GameStateJson.decodeFromString<GameState>(legacyJson))
        assertFalse(legacy.reactorCharged); assertFalse(legacy.reactorPending)
        val other=ReplacementMigration.restore(old.copy(level=HouseRule.levelsWith(HouseRule.ROUTES).first()))
        assertFalse(other.reactorCharged); assertFalse(other.reactorPending)
    }
    @Test fun `campaign slot remains unchanged and Estela is retired`() {
        assertEquals(22,HouseRule.TRAIL.ordinal); assertEquals(28,HouseRule.entries.size)
        assertEquals(listOf(11,22,72,83,133,144,194,205,244,255,266),HouseRule.levelsWith(HouseRule.TRAIL).map { it-7 })
        assertEquals("reactor_color",MechanicCapsule.TRAIL.id)
        HouseRule.levelsWith(HouseRule.TRAIL).forEach { assertTrue(ReactorRules.appliesTo(it)); assertFalse(TrailRules.appliesTo(it)) }
    }
}
