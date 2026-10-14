package com.cukiti.teikafusion.model

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class PortalSwapRulesTest {
    private fun state()=GameState(level=HouseRule.levelsWith(HouseRule.ROUTES).first(),grid=(0..7).flatMap { y ->
        (0..7).map { x -> Position(x,y) to CellContent.Empty }
    }.toMap())
    private val a=Position(0,0)
    private val b=Position(4,4)
    @Test fun `swap carries holes complete wood and properties without rotation`() {
        val wood=CellContent.Wood(targetColor=TargetColor.RED,teikaSeed=true,armor=1,poisoned=true,rock=true)
        val other=CellContent.Wood(colorType=2)
        val s=state().copy(grid=state().grid+mapOf(a to wood,Position(5,5) to other))
        val plan=requireNotNull(PortalSwapRules.plan(s,emptySet(),a,b))
        val out=PortalSwapRules.apply(s.grid,plan)
        assertEquals(wood,out[b]); assertEquals(other,out[Position(1,1)])
        assertEquals(CellContent.Empty,out[a]); assertEquals(CellContent.Empty,out[Position(5,5)])
        assertEquals(s.grid,PortalSwapRules.apply(out,plan))
        assertEquals(setOf(a to b,Position(5,5) to Position(1,1)),plan.moves.toSet())
    }
    @Test fun `every protection rejects the whole footprint including an empty anchor`() {
        val s=state().copy(grid=state().grid+(a to CellContent.Wood()))
        val p=Position(5,5)
        val protected=listOf(s.copy(frozenCells=setOf(p)),s.copy(glacierFrostCells=setOf(p)),
            s.copy(kingLaneCells=setOf(p)),s.copy(hiddenWoodCells=setOf(p)),
            s.copy(starAstroTargetCells=setOf(p)),s.copy(starAstroMoonWoodCells=setOf(p)),
            s.copy(grid=s.grid+(p to CellContent.Wood(coil=1))),
            s.copy(biomePrimary=BiomeType.VOLCANO,lavaRow=6))
        protected.forEach { assertNull(PortalSwapRules.plan(it,emptySet(),a,b)) }
        assertNull(PortalSwapRules.plan(s,setOf(p),a,b))
        assertEquals("Hay un Teika en esta zona",PortalSwapRules.zoneError(s,setOf(p),b))
    }
    @Test fun `bounds overlap unchanged zones and Ninja crossing preserve charge eligibility`() {
        val s=state().copy(grid=state().grid+(a to CellContent.Wood()))
        assertNull(PortalSwapRules.plan(s,emptySet(),a,Position(1,0)))
        assertNull(PortalSwapRules.plan(s,emptySet(),a,Position(-1,3)))
        assertNull(PortalSwapRules.plan(s,emptySet(),a,Position(7,3)))
        assertNull(PortalSwapRules.plan(state(),emptySet(),a,b))
        assertNull(PortalSwapRules.plan(s.copy(ninjaCutActive=true),emptySet(),a,b))
        assertNull(PortalSwapRules.plan(s.copy(ninjaCutActive=true),emptySet(),a,Position(3,3)))
        assertNotNull(PortalSwapRules.plan(s.copy(ninjaCutActive=true),emptySet(),a,Position(2,3)))
        assertFalse(PortalSwapRules.hasMove(state(),emptySet()))
        val uniform=state().let { it.copy(grid=it.grid.mapValues { CellContent.Wood() }) }
        assertFalse(PortalSwapRules.hasMove(uniform,emptySet()))
        assertTrue(PortalSwapRules.hasMove(s,emptySet()))
    }
    @Test fun `random swaps conserve every cell and identity and leave everything outside intact`() {
        val r=kotlin.random.Random(450)
        repeat(120) {
            val s=state().let { it.copy(grid=it.grid.mapValues { if(r.nextBoolean()) CellContent.Wood(colorType=r.nextInt(4),teikaSeed=r.nextBoolean()) else CellContent.Empty }) }
            val plan=PortalSwapRules.plan(s,emptySet(),a,b) ?: return@repeat
            val out=PortalSwapRules.apply(s.grid,plan)
            assertEquals(s.grid.values.groupingBy { it }.eachCount(),out.values.groupingBy { it }.eachCount())
            s.grid.filterKeys { it !in plan.cells }.forEach { (p,c) -> assertEquals(c,out[p]) }
            assertEquals(s.grid,PortalSwapRules.apply(out,plan))
        }
    }
    @Test fun `old routes retire without gifting a portal or deleting earned companion use`() {
        val s=state().copy(routes=listOf(RouteTrack(listOf(a,Position(1,0),Position(2,0)),setOf(a))),
            routeSkillEarned=true,routeFreeCompanionId="fuego",score=200,replacementRulesVersion=5)
        val restored=ReplacementMigration.restore(s)
        assertTrue(restored.routes.isEmpty()); assertEquals(200,restored.score)
        assertEquals("fuego",restored.routeFreeCompanionId); assertTrue(restored.routeSkillEarned)
        assertFalse(restored.portalSwapCharged); assertFalse(restored.portalSwapPending)
        assertEquals(restored,ReplacementMigration.restore(restored))
        val json=Json { allowStructuredMapKeys=true; encodeDefaults=true }
        val oldObject=json.parseToJsonElement(json.encodeToString(GameState.serializer(),s)) as kotlinx.serialization.json.JsonObject
        val oldJson=kotlinx.serialization.json.JsonObject(oldObject.filterKeys { it !in setOf("portalSwapCharged","portalSwapPending") }).toString()
        assertEquals(restored,ReplacementMigration.restore(json.decodeFromString(GameState.serializer(),oldJson)))
        assertEquals(12,GameState.initial(s.level).replacementRulesVersion)
        val pending=restored.copy(portalSwapPending=true)
        assertEquals(pending,json.decodeFromString(GameState.serializer(),json.encodeToString(GameState.serializer(),pending)))
        assertFalse(ReplacementMigration.restore(pending.copy(level=HouseRule.levelsWith(HouseRule.CONSTELLATION).first())).portalSwapPending)
        assertEquals(13,HouseRule.ROUTES.ordinal); assertFalse(RouteRules.appliesTo(s.level))
        assertEquals("portal_swap",MechanicCapsule.ROUTES.id)
        assertEquals(listOf(10,21,71,82,132,143,193,204,243,254),HouseRule.levelsWith(HouseRule.ROUTES).map { it-7 })
    }
}
