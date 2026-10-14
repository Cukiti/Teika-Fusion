package com.cukiti.teikafusion.model

import com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class TurbineRulesTest {
    private val beam=TurbineBeam(0f,4f,1f,0f)
    private fun state()=GameState(level=HouseRule.levelsWith(HouseRule.SLIDE).first(),grid=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap())
    @Test fun `cone excludes behind out of range and beyond thirty degrees`() {
        assertTrue(TurbineRules.reaches(beam,3f,4f,emptySet(),8,8,false))
        assertTrue(TurbineRules.reaches(beam,6f,4f,emptySet(),8,8,false))
        assertTrue(TurbineRules.reaches(beam,3f,5.7f,emptySet(),8,8,false))
        assertFalse(TurbineRules.reaches(beam,3f,5.8f,emptySet(),8,8,false))
        assertFalse(TurbineRules.reaches(beam,6.1f,4f,emptySet(),8,8,false))
        assertFalse(TurbineRules.reaches(beam,-1f,4f,emptySet(),8,8,false))
    }
    @Test fun `air is blocked at exact wood faces and never crosses ninja`() {
        val b=beam.copy(y=4.5f); val wall=setOf(Position(2,4))
        assertEquals(2f,TurbineRules.rayLength(b,1f,0f,wall,8,8,false),0f)
        assertTrue(TurbineRules.reaches(b,1f,4.5f,wall,8,8,false))
        assertFalse(TurbineRules.reaches(b,3f,4.5f,wall,8,8,false))
        assertFalse(TurbineRules.reaches(b,5f,4.5f,emptySet(),8,8,true))
        assertEquals(4f,TurbineRules.rayLength(b,1f,0f,emptySet(),8,8,true),0f)
    }
    @Test fun `all board protections lava and wood interrupt current but empty cells do not`() {
        val s=state().copy(grid=state().grid+(Position(1,1) to CellContent.Wood()),
            frozenCells=setOf(Position(2,2)),glacierFrostCells=setOf(Position(3,3)),biomePrimary=BiomeType.VOLCANO,lavaRow=1)
        val blocked=TurbineRules.blocked(s)
        assertTrue(setOf(Position(1,1),Position(2,2),Position(3,3),Position(6,0)).all { it in blocked })
        assertFalse(Position(6,6) in blocked)
    }
    @Test fun `edge direction clamps inward and rejects corrupt beams`() {
        for((x,y) in listOf(0f to 3f,3f to 0f,8f to 3f,3f to 8f)) {
            val b=TurbineRules.anchor(x,y,8,8)
            assertTrue(TurbineRules.valid(b,8,8))
            assertTrue(TurbineRules.valid(TurbineRules.aim(b,-99f,-99f,8,8),8,8))
        }
        assertFalse(TurbineRules.valid(beam.copy(x=1f),8,8))
        assertFalse(TurbineRules.valid(beam.copy(dx=Float.NaN),8,8))
        assertFalse(TurbineRules.valid(beam.copy(dx=-1f),8,8))
    }
    @Test fun `old slide pending is cleared without granting new charge and remains idempotent`() {
        val restored=ReplacementMigration.restore(state().copy(slidePendingCells=setOf(Position(2,3)),slideWave=9))
        assertTrue(restored.slidePendingCells.isEmpty()); assertEquals(0,restored.slideWave)
        assertFalse(restored.turbineCharged); assertNull(restored.turbineGust)
        assertFalse(SlideRules.appliesTo(restored.level)); assertEquals(restored,ReplacementMigration.restore(restored))
        assertEquals(12,restored.replacementRulesVersion)
    }
    @Test fun `new save keeps remaining clock and finite velocity only for actual bodies`() {
        val s=state().copy(turbineCharged=true,turbineGust=TurbineGust(beam,1350f,listOf(TurbineVelocity("a",3f,-2f),TurbineVelocity("ghost",1f,1f))),
            teikaBodySaves=listOf(TeikaBodySave("a",TeikaType.TEIKA_1,3f,4f)))
        val json=Json { allowStructuredMapKeys=true }
        val r=ReplacementMigration.restore(json.decodeFromString<GameState>(json.encodeToString(GameState.serializer(),s)))
        assertEquals(1350f,r.turbineGust!!.elapsedMs,0f)
        assertEquals(listOf(TurbineVelocity("a",3f,-2f)),r.turbineGust!!.velocities); assertFalse(r.turbineCharged)
        assertNull(ReplacementMigration.restore(s.copy(level=1)).turbineGust)
        assertNull(ReplacementMigration.restore(s.copy(turbineGust=TurbineGust(beam,Float.NaN))).turbineGust)
    }
    @Test fun `empty frozen bubbled or shielded worlds have no reachable move`() {
        val s=state(); val free=TeikaBodySave("a",TeikaType.TEIKA_1,3f,4f)
        assertTrue(TurbineRules.hasMove(s,listOf(free)))
        assertFalse(TurbineRules.hasMove(s,emptyList()))
        assertFalse(TurbineRules.hasMove(s,listOf(free.copy(frozenTurns=1))))
        assertFalse(TurbineRules.hasMove(s,listOf(free.copy(bubble=1))))
        assertFalse(TurbineRules.hasMove(s.copy(grid=s.grid.mapValues { CellContent.Wood() }),listOf(free)))
    }
    private fun world(type: TeikaType=TeikaType.TEIKA_1)=TeikaPhysicsWorld().apply { addBody(type,3f,4f,id="body"); setGravityDirection(0f,0f) }
    @Test fun `same force moves small bodies more than large ones`() {
        fun speed(type: TeikaType): Float {
            val w=world(type); w.setTurbine(beam,180f,emptySet(),8,8,false); w.step(.04f,40)
            return w.getBody("body")!!.vx
        }
        assertTrue(speed(TeikaType.TEIKA_1)>speed(TeikaType.TEIKA_4)*8)
    }
    @Test fun `fixed step airflow agrees at thirty sixty and one hundred twenty fps`() {
        fun simulate(fps: Int): Pair<Float,Float> {
            val w=world(); w.setTurbine(beam,0f,emptySet(),8,8,false)
            repeat(fps/2) { w.step(1f/fps,it*1000L/fps) }
            val b=w.getBody("body")!!; return b.x to b.vx
        }
        val ref=simulate(120)
        for(fps in listOf(30,60)) { val p=simulate(fps); assertEquals(ref.first,p.first,.025f); assertEquals(ref.second,p.second,.08f) }
    }
    @Test fun `protected bodies and shadowed bodies receive no turbine acceleration`() {
        val baseline=world(); baseline.step(.04f,40); val vx=baseline.getBody("body")!!.vx
        for(mode in 0..3) {
            val w=world()
            if(mode==0) w.setFrozenTurns("body",1)
            if(mode==1) w.setKinematic("body",true)
            if(mode==2) w.setBubble("body",1)
            w.setTurbine(beam,300f,if(mode==3) setOf(Position(1,3),Position(1,4)) else emptySet(),8,8,false)
            w.step(.04f,40); assertEquals(vx,w.getBody("body")!!.vx,.00001f)
        }
    }
    @Test fun `expired gust applies no force and clear removes current from next world`() {
        val w=world(); w.setTurbine(beam,2000f,emptySet(),8,8,false); w.step(.04f,40)
        assertEquals(0f,w.getBody("body")!!.vx,0f)
        w.setTurbine(beam,300f,emptySet(),8,8,false); w.clear(); w.addBody(TeikaType.TEIKA_1,3f,4f,id="body")
        w.step(.04f,80); assertEquals(0f,w.getBody("body")!!.vx,0f)
    }
    @Test fun `restoring gust velocity never unfreezes a body and never emits an impulse`() {
        val w=world(); w.restoreTurbineVelocities(listOf(TurbineVelocity("body",4f,2f)))
        assertEquals(4f,w.getBody("body")!!.vx,0f); assertEquals(2f,w.getBody("body")!!.vy,0f)
        w.setFrozenTurns("body",1); val before=w.getBody("body")!!
        w.restoreTurbineVelocities(listOf(TurbineVelocity("body",7f,7f)))
        assertEquals(before,w.getBody("body"))
    }
    @Test fun `driven velocity is limited without slowing an existing faster motion`() {
        val w=world(); w.setTurbine(beam,180f,emptySet(),8,8,false)
        repeat(60) { w.step(1f/120, it*8L); assertTrue(w.getBody("body")!!.vx<=TurbineRules.SPEED+.001f) }
        val fast=world(); fast.restoreTurbineVelocities(listOf(TurbineVelocity("body",12f,0f)))
        fast.setTurbine(beam,300f,emptySet(),8,8,false); fast.step(1f/120,8)
        assertTrue(fast.getBody("body")!!.vx>10f)
    }
    @Test fun `save keeps pending fusion pairs but removes nonexistent or protected participants`() {
        val a=TeikaBodySave("a",TeikaType.TEIKA_1,3f,4f); val b=a.copy(id="b",x=4f)
        val s=state().copy(teikaBodySaves=listOf(a,b),turbineGust=TurbineGust(beam,2000f,fusions=listOf(TurbineFusion("a","b"),TurbineFusion("a","ghost"))))
        assertEquals(listOf(TurbineFusion("a","b")),ReplacementMigration.restore(s).turbineGust!!.fusions)
        assertTrue(ReplacementMigration.restore(s.copy(teikaBodySaves=listOf(a,b.copy(frozenTurns=1)))).turbineGust!!.fusions.isEmpty())
        assertTrue(ReplacementMigration.restore(s.copy(ninjaCutActive=true)).turbineGust!!.fusions.isEmpty())
        assertTrue(ReplacementMigration.restore(s.copy(teikaBodySaves=listOf(a,b.copy(x=7f)))).turbineGust!!.fusions.isEmpty())
    }
    @Test fun `turbine slot keeps all ten campaign numbers and its enum ordinal`() {
        assertEquals(8,HouseRule.SLIDE.ordinal); assertEquals(28,HouseRule.entries.size)
        assertEquals(listOf(36,47,58,108,119,158,169,219,230,280),HouseRule.levelsWith(HouseRule.SLIDE).map(LevelMap::displayNumber))
        assertEquals("turbine",MechanicCapsule.SLIDE.id)
    }
}
