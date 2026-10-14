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
class ReactorEngineTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(vm: GameViewModel, name: String): MutableStateFlow<T> = GameViewModel::class.java.getDeclaredField(name)
        .apply { isAccessible = true }.get(vm) as MutableStateFlow<T>

    private fun fixture(rule: HouseRule): EngineDriver {
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

    private val origin=Position(2,2)
    private fun ready(d: EngineDriver, seed: Boolean=false) {
        val group=listOf(origin,Position(3,2),Position(2,3),Position(3,3))
        val grid=d.state.grid+group.mapIndexed { i,p -> p to CellContent.Wood(id="reactor-$i",colorType=2,
            targetColor=if(i==0) TargetColor.RED else null,teikaSeed=seed && i==3) }.toMap()
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,reactorCharged=true)
    }
    private fun aim(d: EngineDriver) { assertTrue(d.vm.beginReactorAim()); d.vm.selectReactorGroup(origin); assertNotNull(d.vm.previewReactor()) }
    @Test fun `reaction collects stars and releases T1 seeds once without turn or line rewards`() {
        val d=fixture(HouseRule.TRAIL); ready(d,seed=true)
        val score=d.state.score; val turn=d.state.placementsInLevel
        execute(d) { aim(d); val first=d.vm.confirmReactor(); val second=d.vm.confirmReactor(); joinAll(first,second) }
        assertEquals(1,d.state.targetsCollected[TargetColor.RED]); assertEquals(turn,d.state.placementsInLevel)
        assertEquals(score,d.state.score); assertEquals(0,d.state.linesClearedInLevel)
        assertFalse(d.state.reactorCharged); assertFalse(d.state.reactorPending); assertFalse(d.vm.boardBusy.value)
        assertEquals(1,d.state.teikasCreatedInLevel[TeikaType.TEIKA_1]); assertEquals(1,d.vm.teikaBodies.value.size)
        assertTrue(d.vm.teikaBodies.value.all { it.type==TeikaType.TEIKA_1 })
        assertFalse(d.state.grid.values.filterIsInstance<CellContent.Wood>().any { it.id.startsWith("reactor-") })
    }
    @Test fun `preview and cancellation are free and protections are revalidated at confirmation`() {
        val d=fixture(HouseRule.TRAIL); ready(d)
        val before=d.state.grid
        execute(d) {
            aim(d); assertEquals(before,d.state.grid); d.vm.cancelReactorAim()
            assertTrue(d.state.reactorCharged); assertFalse(d.vm.boardBusy.value)
            aim(d)
            field<GameState>(d.vm,"_gameState").value=d.state.copy(frozenCells=setOf(origin,Position(3,3)))
            d.vm.confirmReactor().join()
            assertNotNull(d.vm.reactorAim.value); assertEquals(before,d.state.grid); assertTrue(d.state.reactorCharged)
            d.vm.cancelReactorAim()
        }
    }
    @Test fun `charged reactor avoids defeat only when a valid group exists and locks other inputs`() {
        val d=fixture(HouseRule.TRAIL); ready(d)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(availablePieces=listOf(Piece(shape=(0..12).map { Position(it,0) })),
            biomePrimary=BiomeType.STORM,grid=d.state.grid+(Position(7,7) to CellContent.Wood(coil=1)))
        execute(d) {
            assertNull(d.vm.evaluateDeadEnd()); aim(d)
            d.vm.tapCoil(Position(7,7)); d.vm.placePiece(d.state.availablePieces.first(),0,0).join(); delay(50)
            assertFalse((d.state.grid[Position(7,7)] as CellContent.Wood).coilLit)
            assertEquals(0,d.state.placementsInLevel); assertFalse(d.vm.canBuildFromBank())
            d.vm.pauseGame(); assertNull(d.vm.reactorAim.value); assertTrue(d.state.reactorCharged)
            assertFalse(d.vm.boardBusy.value)
        }
        field<GameState>(d.vm,"_gameState").value=d.state.copy(status=GameStatus.PLAYING,biomePrimary=BiomeType.NONE,grid=d.state.grid.mapValues { CellContent.Wood(coil=1) })
        assertFalse(ReactorRules.hasGroup(d.state,emptySet())); assertNotNull(d.vm.evaluateDeadEnd())
    }
    @Test fun `pause after commitment retains all rewards and resumes without repeat`() {
        val d=fixture(HouseRule.TRAIL); ready(d,seed=true)
        execute(d) {
            aim(d); val job=d.vm.confirmReactor()
            while(!d.state.reactorPending) delay(1)
            d.vm.pauseGame(); val ids=d.vm.teikaBodies.value.map { it.id }
            delay(900); assertTrue(d.state.reactorPending); assertEquals(GameStatus.PAUSED,d.state.status)
            assertEquals(1,d.state.targetsCollected[TargetColor.RED]); assertEquals(ids,d.vm.teikaBodies.value.map { it.id })
            d.vm.resumeGame(); job.join()
        }
        assertEquals(1,d.state.targetsCollected[TargetColor.RED]); assertEquals(1,d.state.teikasCreatedInLevel[TeikaType.TEIKA_1])
        assertFalse(d.state.reactorPending); assertFalse(d.vm.boardBusy.value)
    }
    @Test fun `normal and Storm real lines charge reactor`() {
        for(storm in listOf(false,true)) {
            val d=fixture(HouseRule.TRAIL)
            val grid=d.state.grid.toMutableMap()
            for(x in 0..if(storm) 7 else 6) grid[Position(x,0)]=CellContent.Wood()
            field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,biomePrimary=if(storm) BiomeType.STORM else BiomeType.NONE,
                availablePieces=List(2) { Piece(shape=listOf(Position(0,0))) })
            if(storm) {
                val m=GameViewModel::class.java.getDeclaredMethod("detonateCircuitRows",Set::class.java,kotlin.coroutines.Continuation::class.java).apply { isAccessible=true }
                execute(d) { kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Unit> { c -> m.invoke(d.vm,setOf(0),c) } }
            } else execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,0).join() }
            assertTrue(d.state.reactorCharged); assertEquals(if(storm) 0 else 1,d.state.placementsInLevel)
        }
    }
    @Test fun `own resulting line gives ordinary rewards without recharging reactor`() {
        val d=fixture(HouseRule.TRAIL); ready(d)
        val row=(0..7).associate { Position(it,7) to CellContent.Wood(colorType=0,targetColor=if(it==0) TargetColor.BLUE else null) }
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=d.state.grid+row)
        execute(d) { aim(d); d.vm.confirmReactor().join() }
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.targetsCollected[TargetColor.BLUE])
        assertFalse(d.state.reactorCharged); assertEquals(0,d.state.placementsInLevel)
    }
    @Test fun `saved committed reaction keeps born bodies and collected stars without replay`() {
        val d=fixture(HouseRule.TRAIL)
        val saved=d.state.copy(reactorPending=true,targetsCollected=mapOf(TargetColor.RED to 1),
            teikasCreatedInLevel=mapOf(TeikaType.TEIKA_1 to 1),
            teikaBodySaves=listOf(TeikaBodySave("reactor-born",TeikaType.TEIKA_1,3.5f,3.5f)))
        val prefs=GameViewModel::class.java.getDeclaredField("prefs").apply { isAccessible=true }.get(d.vm) as PreferencesManager
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; encodeDefaults=true }
        execute(d) {
            prefs.saveGameState(json.encodeToString(GameState.serializer(),saved),level=saved.level)
            field<GameState>(d.vm,"_gameState").value=saved.copy(score=-1)
            d.vm.continueGame()
            while(d.state.score == -1 || d.state.reactorPending || d.vm.boardBusy.value) delay(16)
        }
        assertEquals(1,d.state.targetsCollected[TargetColor.RED]); assertEquals(1,d.vm.teikaBodies.value.size)
        assertEquals("reactor-born",d.vm.teikaBodies.value.single().id); assertFalse(d.state.reactorCharged)
        assertEquals(0,d.state.placementsInLevel); assertEquals(0,d.state.linesClearedInLevel)
    }
    @Test fun `saved final objective acknowledges victory without another placement`() {
        val d=fixture(HouseRule.TRAIL)
        val saved=d.state.copy(reactorPending=true,levelObjective=LevelObjective(emptyMap(),LevelGoal.CLEAR,0),
            targetsCollected=mapOf(TargetColor.RED to 1),teikasCreatedInLevel=mapOf(TeikaType.MEGA_TEIKA to 1))
        val prefs=GameViewModel::class.java.getDeclaredField("prefs").apply { isAccessible=true }.get(d.vm) as PreferencesManager
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; encodeDefaults=true }
        execute(d) {
            prefs.saveGameState(json.encodeToString(GameState.serializer(),saved),level=saved.level)
            field<GameState>(d.vm,"_gameState").value=saved.copy(score=-1)
            d.vm.continueGame()
            while(d.state.score == -1 || d.state.reactorPending || d.vm.boardBusy.value) delay(16)
        }
        assertTrue(d.state.status in setOf(GameStatus.LEVEL_COMPLETE_PENDING,GameStatus.LEVEL_COMPLETE))
        assertEquals(1,d.state.targetsCollected[TargetColor.RED]); assertEquals(0,d.state.placementsInLevel)
    }
    @Test fun `legacy Teika movement no longer charges Estela or reactor`() {
        val d=fixture(HouseRule.TRAIL)
        val w=GameViewModel::class.java.getDeclaredField("physicsWorld").apply { isAccessible=true }.get(d.vm) as com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld
        w.addBody(TeikaType.TEIKA_1,2.5f,1f)
        runBlocking { repeat(80) { d.vm.onTeikaPhysicsFrame(.016f) } }
        assertTrue(d.state.trailCells.isEmpty()); assertFalse(d.state.reactorCharged)
        assertFalse(d.state.events.filterIsInstance<GameEvent.RulePulse>().any { it.kind.startsWith("trail") })
    }
}
