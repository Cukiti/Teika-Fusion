package com.cukiti.teikafusion.bot

import com.cukiti.teikafusion.model.*
import com.cukiti.teikafusion.viewmodel.GameViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TurbineEngineTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(vm: GameViewModel, name: String): MutableStateFlow<T> = GameViewModel::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(vm) as MutableStateFlow<T>

    private fun fixture(rule: HouseRule=HouseRule.SLIDE): EngineDriver {
        val d = EngineDriver()
        field<Boolean>(d.vm,"_starterPackGranted").value = true
        val grid = (0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()
        field<GameState>(d.vm,"_gameState").value = GameState(level = HouseRule.levelsWith(rule).first(),
            status = GameStatus.PLAYING, biomePrimary = BiomeType.NONE, grid = grid, replacementRulesVersion = 1,
            availablePieces = listOf(Piece(shape = listOf(Position(0,0),Position(1,0),Position(2,0),Position(3,0)))))
        return d
    }

    private fun execute(d: EngineDriver, action: suspend () -> Unit) {
        val job = CoroutineScope(Dispatchers.Default).async { action() }
        repeat(2500) {
            if (job.isCompleted) { runBlocking { job.await() }; return }
            d.tick(); Thread.sleep(2)
        }
        fail("replacement action did not finish")
    }

    private fun world(d: EngineDriver)=GameViewModel::class.java.getDeclaredField("physicsWorld").apply { isAccessible=true }.get(d.vm) as com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld
    private fun charged(d: EngineDriver) {
        field<GameState>(d.vm,"_gameState").value=d.state.copy(turbineCharged=true)
        world(d).clear(); world(d).addBody(TeikaType.TEIKA_1,3f,4.5f,id="wind-a")
    }
    private fun aim(d: EngineDriver) {
        assertTrue(d.vm.beginTurbineAim()); d.vm.selectTurbineAnchor(0f,4.5f); d.vm.aimTurbine(3f,4.5f)
        assertNotNull(d.vm.previewTurbine())
    }
    @Test fun `draft freezes physics cancel and pause conserve charge`() {
        val d=fixture(); charged(d)
        execute(d) {
            aim(d); val before=world(d).snapshot(); repeat(10) { d.vm.onTeikaPhysicsFrame(.05f) }
            assertEquals(before,world(d).snapshot()); assertNull(d.state.turbineGust)
            d.vm.cancelTurbineAim(); assertTrue(d.state.turbineCharged); assertFalse(d.vm.boardBusy.value)
            aim(d); d.vm.pauseGame(); assertNull(d.vm.turbineAim.value); assertTrue(d.state.turbineCharged)
            assertFalse(d.vm.boardBusy.value); d.vm.resumeGame()
        }
    }
    @Test fun `gust costs no turn blocks other actions and expires after active simulation`() {
        val d=fixture(); charged(d)
        execute(d) {
            aim(d); d.vm.confirmTurbine().join(); d.vm.confirmTurbine().join()
            assertFalse(d.state.turbineCharged); assertNotNull(d.state.turbineGust)
            d.vm.placePiece(d.state.availablePieces.first(),0,0).join(); assertEquals(0,d.state.placementsInLevel)
            repeat(8) { d.vm.onTeikaPhysicsFrame(.05f) }
            val remaining=d.state.turbineGust!!.elapsedMs; val before=world(d).snapshot()
            d.vm.pauseGame(); repeat(30) { d.vm.onTeikaPhysicsFrame(.05f) }
            assertEquals(remaining,d.state.turbineGust!!.elapsedMs,0f); assertEquals(before,world(d).snapshot())
            d.vm.resumeGame(); repeat(50) { d.vm.onTeikaPhysicsFrame(.05f) }
        }
        assertNull(d.state.turbineGust); assertFalse(d.vm.boardBusy.value); assertFalse(d.state.turbineCharged)
        assertEquals(0,d.state.placementsInLevel); assertEquals(0,d.state.linesClearedInLevel)
    }
    @Test fun `invalid blocked or no longer free target preserves charge`() {
        val d=fixture(); charged(d)
        execute(d) {
            aim(d); world(d).setKinematic("wind-a",true); d.vm.confirmTurbine().join()
            assertNull(d.state.turbineGust); assertTrue(d.state.turbineCharged)
            d.vm.cancelTurbineAim(); world(d).setKinematic("wind-a",false); aim(d)
            field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=d.state.grid+(Position(1,4) to CellContent.Wood()))
            d.vm.confirmTurbine().join(); assertNull(d.state.turbineGust); assertTrue(d.state.turbineCharged)
            d.vm.cancelTurbineAim()
        }
        assertFalse(d.vm.boardBusy.value)
    }
    @Test fun `empty world still finishes two seconds and reduced FX does not shorten gust`() {
        val d=fixture(); charged(d); field<Boolean>(d.vm,"_reducedFx").value=true
        execute(d) {
            aim(d); d.vm.confirmTurbine().join(); world(d).clear()
            repeat(20) { d.vm.onTeikaPhysicsFrame(.05f) }; assertNotNull(d.state.turbineGust)
            assertEquals(1000f,d.state.turbineGust!!.elapsedMs,.01f)
            repeat(21) { d.vm.onTeikaPhysicsFrame(.05f) }
        }
        assertNull(d.state.turbineGust); assertFalse(d.vm.boardBusy.value)
    }
    @Test fun `save snapshot resumes remaining time and velocity without replaying force`() {
        val d=fixture(); charged(d)
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; encodeDefaults=true }
        val prefs=GameViewModel::class.java.getDeclaredField("prefs").apply { isAccessible=true }.get(d.vm) as PreferencesManager
        execute(d) {
            aim(d); d.vm.confirmTurbine().join(); repeat(8) { d.vm.onTeikaPhysicsFrame(.05f) }
            val stamp=GameViewModel::class.java.getDeclaredMethod("withTeikaBodySaves",GameState::class.java).apply { isAccessible=true }
            val saved=stamp.invoke(d.vm,d.state) as GameState
            val motion=world(d).getBody("wind-a")!!
            assertEquals(400f,saved.turbineGust!!.elapsedMs,.01f)
            assertEquals(motion.vx,saved.turbineGust!!.velocities.single().vx,0f)
            prefs.saveGameState(json.encodeToString(GameState.serializer(),saved),level=saved.level)
            field<GameState>(d.vm,"_gameState").value=d.state.copy(score=-1)
            d.vm.continueGame(); while(d.state.score == -1) delay(16)
            // EngineDriver ticks while the DataStore load runs. The gust must resume, never restart.
            assertNotNull(d.state.turbineGust); assertTrue(d.state.turbineGust!!.elapsedMs>=400f)
            assertFalse(d.state.turbineCharged); assertTrue(d.vm.boardBusy.value)
            repeat(42) { d.vm.onTeikaPhysicsFrame(.05f) }
        }
        assertNull(d.state.turbineGust); assertFalse(d.vm.boardBusy.value); assertEquals(0,d.state.placementsInLevel)
    }
    @Test fun `late fusion retains gust ownership until ceremony completes`() {
        val d=fixture(); charged(d)
        val w=world(d); w.addBody(TeikaType.TEIKA_1,4f,4.5f,id="wind-b")
        aim(d); execute(d) { d.vm.confirmTurbine().join() }
        // One test-controlled frame, without racing EngineDriver's virtual scheduler.
        field<GameState>(d.vm,"_gameState").value=d.state.copy(turbineGust=d.state.turbineGust!!.copy(elapsedMs=1990f))
        val m=GameViewModel::class.java.getDeclaredMethod("startFusionCharge",String::class.java,String::class.java,
            Float::class.javaPrimitiveType,Float::class.javaPrimitiveType,Float::class.javaPrimitiveType,Float::class.javaPrimitiveType,Int::class.javaPrimitiveType).apply { isAccessible=true }
        m.invoke(d.vm,"wind-a","wind-b",3f,4.5f,4f,4.5f,1)
        runBlocking { d.vm.onTeikaPhysicsFrame(.05f) }
        assertEquals(2000f,d.state.turbineGust!!.elapsedMs,0f)
        assertEquals(listOf(TurbineFusion("wind-a","wind-b")),d.state.turbineGust!!.fusions)
        assertTrue(d.vm.boardBusy.value)
        execute(d) { delay(1000) }
        assertNull(d.state.turbineGust); assertFalse(d.state.turbineCharged); assertFalse(d.vm.boardBusy.value)
        assertTrue(w.snapshot().any { it.type==TeikaType.TEIKA_2 })
    }
    @Test fun `paused completed wind finishes its saved line chain without a recharge`() {
        val d=fixture(); charged(d)
        execute(d) {
            aim(d); d.vm.confirmTurbine().join(); d.vm.pauseGame()
            val grid=d.state.grid.toMutableMap(); for(x in 0..7) grid[Position(x,0)]=CellContent.Wood()
            field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,turbineGust=d.state.turbineGust!!.copy(elapsedMs=2000f,chainPending=true))
            d.vm.resumeGame(); while(d.state.turbineGust!=null) delay(16)
        }
        assertEquals(1,d.state.linesClearedInLevel); assertFalse(d.state.turbineCharged)
        assertFalse(d.vm.boardBusy.value); assertEquals(0,d.state.placementsInLevel)
    }
    @Test fun `saved late fusion resumes once without resurrecting wind or giving a charge`() {
        val d=fixture()
        val saved=d.state.copy(turbineGust=TurbineGust(TurbineBeam(0f,4.5f,1f,0f),2000f,fusions=listOf(TurbineFusion("a","b"))),
            teikaBodySaves=listOf(TeikaBodySave("a",TeikaType.TEIKA_1,3f,4.5f),TeikaBodySave("b",TeikaType.TEIKA_1,4f,4.5f)))
        val prefs=GameViewModel::class.java.getDeclaredField("prefs").apply { isAccessible=true }.get(d.vm) as PreferencesManager
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; encodeDefaults=true }
        execute(d) {
            prefs.saveGameState(json.encodeToString(GameState.serializer(),saved),level=saved.level)
            field<GameState>(d.vm,"_gameState").value=d.state.copy(score=-1)
            d.vm.continueGame(); while(d.state.score == -1 || d.state.turbineGust!=null) delay(16)
        }
        assertFalse(d.state.turbineCharged); assertFalse(d.vm.boardBusy.value)
        assertEquals(1,world(d).snapshot().count { it.type==TeikaType.TEIKA_2 })
        assertEquals(0,d.state.placementsInLevel)
    }
    @Test fun `normal and Storm real lines charge once but gust lines cannot recharge`() {
        for(storm in listOf(false,true)) for(active in listOf(false,true)) {
            val d=fixture(); val grid=d.state.grid.toMutableMap()
            for(x in 0..if(storm) 7 else 6) grid[Position(x,0)]=CellContent.Wood()
            field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,
                turbineGust=if(active) TurbineGust(TurbineBeam(0f,4f,1f,0f),100f) else null,
                availablePieces=List(2) { Piece(shape=listOf(Position(0,0))) })
            if(storm) {
                val m=GameViewModel::class.java.getDeclaredMethod("detonateCircuitRows",Set::class.java,kotlin.coroutines.Continuation::class.java).apply { isAccessible=true }
                execute(d) { kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Unit> { c -> m.invoke(d.vm,setOf(0),c) } }
            } else if(!active) execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,0).join() }
            else {
                field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid+(Position(7,0) to CellContent.Wood()))
                val m=GameViewModel::class.java.getDeclaredMethod("resolveBoardChain",Long::class.javaPrimitiveType,Boolean::class.javaPrimitiveType,kotlin.coroutines.Continuation::class.java).apply { isAccessible=true }
                execute(d) { kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Unit> { c -> m.invoke(d.vm,0L,false,c) } }
            }
            assertEquals(!active,d.state.turbineCharged); assertTrue(d.state.slidePendingCells.isEmpty()); assertEquals(0,d.state.slideWave)
            assertEquals(if(!storm && !active) 1 else 0,d.state.placementsInLevel)
        }
    }
}
