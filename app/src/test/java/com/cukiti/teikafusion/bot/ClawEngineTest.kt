package com.cukiti.teikafusion.bot
import com.cukiti.teikafusion.model.*
import com.cukiti.teikafusion.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class ClawEngineTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(d: EngineDriver,name: String)=GameViewModel::class.java.getDeclaredField(name).apply { isAccessible=true }.get(d.vm) as MutableStateFlow<T>
    private fun world(d: EngineDriver)=GameViewModel::class.java.getDeclaredField("physicsWorld").apply { isAccessible=true }.get(d.vm) as TeikaPhysicsWorld
    private fun fixture(): EngineDriver {
        val d=EngineDriver(); field<Boolean>(d,"_starterPackGranted").value=true
        field<GameState>(d,"_gameState").value=GameState(level=HouseRule.levelsWith(HouseRule.ELEVATOR).first(),status=GameStatus.PLAYING,biomePrimary=BiomeType.NONE,
            grid=(0..7).flatMap { y->(0..7).map { x->Position(x,y) to CellContent.Empty } }.toMap(),clawCharged=true,
            availablePieces=listOf(Piece(shape=listOf(Position(0,0)))))
        return d
    }
    private fun execute(d: EngineDriver, action: suspend ()->Unit) {
        val job=CoroutineScope(Dispatchers.Default).async { action() }
        repeat(2500) { if(job.isCompleted) { runBlocking { job.await() }; return }; d.tick(); Thread.sleep(2) }
        fail("garra action did not finish")
    }
    private fun aim(d: EngineDriver) {
        assertTrue(d.vm.beginClawAim()); d.vm.selectClawBody(1.5f,6.5f); d.vm.aimClaw(6.5f,2.5f)
        assertNotNull(d.vm.previewClaw())
    }
    @Test fun `selection freezes physics cancel and invalid destination preserve charge`() {
        val d=fixture(); val w=world(d); val b=w.addBody(TeikaType.TEIKA_1,1.5f,6.5f)
        assertTrue(d.vm.beginClawAim()); d.vm.selectClawBody(b.x,b.y); d.vm.aimClaw(8f,8f)
        runBlocking { repeat(20) { d.vm.onTeikaPhysicsFrame(.016f) } }
        assertEquals(b.y,w.getBody(b.id)!!.y,0f); assertNull(d.vm.previewClaw())
        execute(d) { d.vm.confirmClaw().join() }
        assertTrue(d.state.clawCharged); assertNull(d.state.clawTrip)
        d.vm.cancelClawAim(); assertFalse(d.vm.boardBusy.value); assertTrue(d.state.clawCharged)
    }
    @Test fun `real passenger travels over wood once with no turn destruction births or points`() {
        val d=fixture(); val w=world(d); val b=w.addBody(TeikaType.TEIKA_1,1.5f,6.5f)
        field<GameState>(d,"_gameState").value=d.state.copy(grid=d.state.grid+(Position(3,4) to CellContent.Wood(targetColor=TargetColor.RED,teikaSeed=true)))
        val grid=d.state.grid; aim(d)
        execute(d) { d.vm.confirmClaw().join(); d.vm.confirmClaw().join() }
        runBlocking { repeat(100) { if(d.state.clawTrip!=null) d.vm.onTeikaPhysicsFrame(.016f) } }
        val moved=w.getBody(b.id)!!
        assertEquals(6.5f,moved.x,.001f); assertEquals(2.5f,moved.y,.001f); assertFalse(moved.kinematic)
        assertEquals(grid,d.state.grid); assertEquals(1,w.snapshot().size); assertEquals(0,d.state.score)
        assertEquals(0,d.state.placementsInLevel); assertTrue(d.state.teikasCreatedInLevel.isEmpty())
        assertFalse(d.state.clawCharged); assertFalse(d.vm.boardBusy.value)
    }
    @Test fun `pause cancels a draft and freezes an already confirmed trip`() {
        val d=fixture(); val w=world(d); w.addBody(TeikaType.TEIKA_1,1.5f,6.5f); aim(d)
        d.vm.pauseGame(); assertNull(d.vm.clawAim.value); assertTrue(d.state.clawCharged); assertFalse(d.vm.boardBusy.value)
        d.vm.resumeGame(); aim(d); execute(d) { d.vm.confirmClaw().join() }
        d.vm.pauseGame(); val trip=d.state.clawTrip!!; val body=w.getBody(trip.bodyId)!!
        runBlocking { repeat(30) { d.vm.onTeikaPhysicsFrame(.016f) } }
        assertEquals(trip,d.state.clawTrip); assertEquals(body,w.getBody(body.id))
        d.vm.resumeGame(); runBlocking { repeat(100) { if(d.state.clawTrip!=null) d.vm.onTeikaPhysicsFrame(.016f) } }
        assertNull(d.state.clawTrip); assertFalse(d.vm.boardBusy.value); assertFalse(d.state.clawCharged)
    }
    @Test fun `new obstacle aborts safely returns origin and restores charge once`() {
        val d=fixture(); val w=world(d); val b=w.addBody(TeikaType.TEIKA_1,1.5f,6.5f); aim(d)
        execute(d) { d.vm.confirmClaw().join() }
        field<GameState>(d,"_gameState").value=d.state.copy(kingLaneCells=setOf(Position(6,2)))
        runBlocking { d.vm.onTeikaPhysicsFrame(.016f) }
        assertNull(d.state.clawTrip); assertTrue(d.state.clawCharged); assertFalse(d.vm.boardBusy.value)
        assertEquals(b.x,w.getBody(b.id)!!.x,0f); assertEquals(b.y,w.getBody(b.id)!!.y,0f)
    }
    @Test fun `frozen retained and bubbled bodies cannot be selected`() {
        for(mode in 0..2) {
            val d=fixture(); val w=world(d); val b=w.addBody(TeikaType.TEIKA_1,1.5f,6.5f)
            when(mode) { 0->w.setFrozenTurns(b.id,1); 1->w.setKinematic(b.id,true); else->w.load(listOf(b.copy(bubble=1))) }
            assertTrue(d.vm.beginClawAim()); d.vm.selectClawBody(b.x,b.y)
            assertNull(d.vm.clawAim.value!!.bodyId); d.vm.cancelClawAim()
        }
    }
    @Test fun `any real row charges once and moving never automatically triggers`() {
        val d=fixture(); field<GameState>(d,"_gameState").value=d.state.copy(clawCharged=false,
            grid=d.state.grid+(0..6).associate { Position(it,2) to CellContent.Wood() })
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,2).join() }
        assertTrue(d.state.clawCharged); assertNull(d.state.clawTrip); assertEquals(1,d.state.linesClearedInLevel)
        assertEquals(1,d.state.placementsInLevel)
    }
    @Test fun `continue restores a saved suspended load without replay or duplicate bodies`() {
        val d=fixture(); val t=ClawTrip("saved-load",1.5f,6.5f,6.5f,2.5f,.5f,.5f); val (x,y)=ClawRules.position(t)
        val s=d.state.copy(clawCharged=false,clawTrip=t,teikaBodySaves=listOf(TeikaBodySave(t.bodyId,TeikaType.TEIKA_1,x,y)))
        val prefs=GameViewModel::class.java.getDeclaredField("prefs").apply { isAccessible=true }.get(d.vm) as PreferencesManager
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; ignoreUnknownKeys=true; encodeDefaults=true }
        execute(d) { prefs.saveGameState(json.encodeToString(GameState.serializer(),s),level=s.level); d.vm.continueGame() }
        var attempts=0; while(d.state.clawTrip==null && attempts++<40) { d.tick(); Thread.sleep(2) }
        assertNotNull(d.state.clawTrip); assertTrue(world(d).getBody(t.bodyId)!!.kinematic)
        runBlocking { repeat(100) { if(d.state.clawTrip!=null) d.vm.onTeikaPhysicsFrame(.016f) } }
        assertNull(d.state.clawTrip); assertFalse(d.vm.boardBusy.value); assertEquals(1,world(d).snapshot().size)
        assertEquals(t.toX,world(d).getBody(t.bodyId)!!.x,.001f); assertFalse(d.state.clawCharged)
    }
    @Test fun `storm discharge charges a single transfer without consuming a placement`() {
        val d=fixture(); val row=(0..7).associate { Position(it,0) to CellContent.Wood(coil=1,coilLit=true) }
        field<GameState>(d,"_gameState").value=d.state.copy(clawCharged=false,biomePrimary=BiomeType.STORM,grid=d.state.grid+row)
        val m=GameViewModel::class.java.getDeclaredMethod("detonateCircuitRows",Set::class.java,kotlin.coroutines.Continuation::class.java).apply { isAccessible=true }
        execute(d) { kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Unit> { c -> m.invoke(d.vm,setOf(0),c) } }
        assertTrue(d.state.clawCharged); assertEquals(0,d.state.placementsInLevel); assertNull(d.state.clawTrip)
    }
    @Test fun `reduced effects finish on one active physics frame and return control`() {
        val d=fixture(); val w=world(d); val b=w.addBody(TeikaType.TEIKA_1,1.5f,6.5f)
        field<Boolean>(d,"_reducedFx").value=true
        aim(d); execute(d) { d.vm.confirmClaw().join() }
        runBlocking { d.vm.onTeikaPhysicsFrame(.016f) }
        assertNull(d.state.clawTrip); assertEquals(6.5f,w.getBody(b.id)!!.x,.001f)
        assertFalse(d.vm.boardBusy.value); assertFalse(d.state.clawCharged)
    }

}
