package com.cukiti.teikafusion.model

import org.junit.Assert.*
import org.junit.Test

class WreckingBallRulesTest {
    private fun state()=GameState(level=HouseRule.levelsWith(HouseRule.DOMINO).first(),
        grid=(0..7).flatMap { y->(0..7).map { x->Position(x,y) to CellContent.Empty } }.toMap())
    private fun arc(s: GameState)=requireNotNull(WreckingBallRules.arc(s,Position(0,3),0,3.5f,3.5f))
    @Test fun `circle sweeps quarter turn with unique contacts and ordered impact times`() {
        val s=state().let { it.copy(grid=it.grid.mapValues { CellContent.Wood(colorType=(it.key.x+it.key.y)%5) }) }
        val p=requireNotNull(WreckingBallRules.plan(s,emptySet(),arc(s)))
        assertTrue(p.cells.size>4); assertEquals(p.cells.size,p.cells.toSet().size)
        assertEquals(p.hitTimes.sorted(),p.hitTimes); assertTrue(p.hitTimes.all { it in 180..800 })
        assertEquals(1120,p.durationMs); assertNull(p.obstacle)
        assertFalse(p.cells.any { it.x>=5 }); assertTrue(WreckingBallRules.hasShot(s,emptySet()))
    }
    @Test fun `first anchor stops entire ball before protected or later cells`() {
        val base=state(); val obstacle=Position(3,3); val tail=Position(3,4)
        val s=base.copy(grid=base.grid+(Position(2,1) to CellContent.Wood())+(obstacle to CellContent.Wood(armor=1))+(tail to CellContent.Wood()))
        val plan=requireNotNull(WreckingBallRules.plan(s,emptySet(),arc(s)))
        assertEquals(obstacle,plan.obstacle); assertTrue(Position(2,1) in plan.cells)
        assertFalse(obstacle in plan.cells); assertFalse(tail in plan.cells)
        assertTrue(plan.arc.endFraction<1); assertTrue(plan.durationMs<1120)
        for(protected in listOf(s.copy(frozenCells=setOf(obstacle)),s.copy(glacierFrostCells=setOf(obstacle)),s.copy(hiddenWoodCells=setOf(obstacle)),s.copy(kingLaneCells=setOf(obstacle)))) {
            assertEquals(obstacle,WreckingBallRules.plan(protected,emptySet(),arc(protected))!!.obstacle)
        }
        val body=base.copy(grid=base.grid+(tail to CellContent.Wood()))
        assertEquals(obstacle,WreckingBallRules.plan(body,setOf(obstacle),arc(body))!!.obstacle)
    }
    @Test fun `ninja cut stops even across empty cells and invalid geometry cannot launch`() {
        val s=state().copy(ninjaCutActive=true)
        val a=arc(s).copy(radius=4f)
        assertTrue(WreckingBallRules.plan(s,emptySet(),a)!!.arc.endFraction<1f)
        assertNull(WreckingBallRules.plan(s,emptySet(),a.copy(radius=4.1f)))
        assertNull(WreckingBallRules.plan(s,emptySet(),a.copy(x=Float.NaN)))
        assertNull(WreckingBallRules.plan(s,emptySet(),a.copy(x=3f)))
        assertNull(WreckingBallRules.plan(s,emptySet(),a.copy(endFraction=.5f)))
        assertNull(WreckingBallRules.anchor(s,4f,4f))
        assertNull(WreckingBallRules.arc(s,Position(0,3),0,Float.NaN,0f))
    }
    @Test fun `touching ninja boundary without crossing allows the full left arc`() {
        val base=state()
        val s=base.copy(ninjaCutActive=true,grid=base.grid.mapValues { CellContent.Wood() })
        val arc=BallArc(3.5f,0f,1f,kotlin.math.PI.toFloat()/2)
        val plan=requireNotNull(WreckingBallRules.plan(s,emptySet(),arc))
        assertNull(plan.obstacle); assertEquals(1f,plan.arc.endFraction,0f)
        assertTrue(plan.cells.isNotEmpty()); assertFalse(plan.cells.any { it.x>=4 })
    }
    @Test fun `reachable wood surrounded by protections remains a real chance`() {
        val base=state(); val target=Position(3,3)
        val s=base.copy(grid=base.grid.mapValues { if(it.key==target) CellContent.Wood() else CellContent.Wood(coil=1) })
        val shot=requireNotNull(WreckingBallRules.plan(s,emptySet(),BallArc(0f,3.5f,3.5f,0f)))
        assertEquals(listOf(target),shot.cells)
        assertTrue(WreckingBallRules.hasShot(s,emptySet()))
        assertFalse(WreckingBallRules.hasShot(s,setOf(target)))
        // Every isolated wood on the 8×8 board has a legal start pose from an edge.
        for(y in 0..7) for(x in 0..7) for(cut in listOf(false,true)) {
            val p=Position(x,y)
            val boxed=base.copy(ninjaCutActive=cut,grid=base.grid.mapValues { if(it.key==p) CellContent.Wood() else CellContent.Wood(coil=1) })
            assertTrue("$p with Ninja=$cut lost its real chance",WreckingBallRules.hasShot(boxed,emptySet()))
        }
    }
    @Test fun `malformed saved cells outside board never become hits or a chance`() {
        val base=state(); val outside=Position(3,9)
        val s=base.copy(grid=base.grid+(outside to CellContent.Wood(targetColor=TargetColor.RED)))
        val arc=BallArc(0f,7.5f,4f,0f)
        assertTrue(WreckingBallRules.plan(s,emptySet(),arc)!!.cells.isEmpty())
        assertFalse(WreckingBallRules.hasShot(s,emptySet()))
    }
    @Test fun `all four edge gestures keep radius bounded and offer honest empty feedback`() {
        val s=state()
        for(side in 0..3) for(n in 0..7) {
            val p=if(side%2==0) Position(if(side==0) 0 else 7,n) else Position(n,if(side==1) 0 else 7)
            val a=requireNotNull(WreckingBallRules.arc(s,p,side,4f,4f))
            assertTrue(a.radius in 1f..4f); assertTrue(WreckingBallRules.valid(s,a))
            assertTrue(WreckingBallRules.plan(s,emptySet(),a)!!.cells.isEmpty())
        }
        assertFalse(WreckingBallRules.hasShot(s,emptySet()))
    }
    @Test fun `migration is idempotent filters flags and preserves paid pending action`() {
        val s=state().copy(ballCharged=true,ballPending=true)
        val restored=ReplacementMigration.restore(s)
        assertTrue(restored.ballPending); assertFalse(restored.ballCharged)
        assertEquals(restored,ReplacementMigration.restore(restored)); assertEquals(12,restored.replacementRulesVersion)
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; encodeDefaults=true }
        assertEquals(restored,json.decodeFromString(GameState.serializer(),json.encodeToString(GameState.serializer(),restored)))
        val other=ReplacementMigration.restore(s.copy(level=HouseRule.levelsWith(HouseRule.TRAIL).first()))
        assertFalse(other.ballPending); assertFalse(other.ballCharged)
        assertEquals(listOf(12,23,73,84,95,134,145,195,206,256,267),HouseRule.levelsWith(HouseRule.DOMINO).map(LevelMap::displayNumber))
        assertEquals("wrecking_ball",MechanicCapsule.DOMINO.id)
    }
}
