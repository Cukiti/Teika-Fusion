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
class OrbitEngineTest {
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

    private val c=Position(2,2)
    private fun aim(d: EngineDriver) { assertTrue(d.vm.beginOrbitAim()); d.vm.selectOrbitZone(c); assertNotNull(d.vm.previewOrbit()) }
    @Test fun `giro preserves stars seeds and IDs and cannot spend twice or add a turn`() {
        val d=fixture(HouseRule.COMET); val wood=CellContent.Wood(targetColor=TargetColor.RED,teikaSeed=true)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(orbitCharged=true,grid=d.state.grid+(Position(1,1) to wood))
        execute(d) { aim(d); d.vm.confirmOrbit().join(); d.vm.confirmOrbit().join() }
        assertEquals(wood.copy(skipSpawnAnimation=true),d.state.grid[Position(3,1)])
        assertEquals(CellContent.Empty,d.state.grid[Position(1,1)])
        assertEquals(0,d.state.placementsInLevel); assertEquals(0,d.state.linesClearedInLevel); assertEquals(0,d.state.score)
        assertTrue(d.state.targetsCollected.isEmpty()); assertTrue(d.state.teikaBodySaves.isEmpty())
        assertFalse(d.state.orbitCharged); assertFalse(d.state.orbitPending); assertFalse(d.vm.boardBusy.value); assertTrue(d.state.hiddenWoodCells.isEmpty())
    }
    @Test fun `reduced giro commits the same board and promptly releases its lease`() {
        val d=fixture(HouseRule.COMET)
        field<Boolean>(d.vm,"_reducedFx").value=true
        field<GameState>(d.vm,"_gameState").value=d.state.copy(orbitCharged=true,grid=d.state.grid+(Position(1,1) to CellContent.Wood()))
        execute(d) { aim(d); d.vm.confirmOrbit().join() }
        assertTrue(d.state.grid[Position(3,1)] is CellContent.Wood); assertFalse(d.state.orbitPending)
        assertFalse(d.vm.boardBusy.value); assertTrue(d.state.hiddenWoodCells.isEmpty()); assertEquals(0,d.state.placementsInLevel)
    }
    @Test fun `reverse preview and committed result agree`() {
        val d=fixture(HouseRule.COMET)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(orbitCharged=true,grid=d.state.grid+(Position(1,1) to CellContent.Wood()))
        execute(d) {
            aim(d); d.vm.toggleOrbitDirection()
            val expected=OrbitRules.apply(d.state.grid,requireNotNull(d.vm.previewOrbit())).mapValues { (p,v) -> if(p in OrbitRules.cells(c) && v is CellContent.Wood) v.copy(skipSpawnAnimation=true) else v }
            d.vm.confirmOrbit().join(); assertEquals(expected,d.state.grid)
        }
    }
    @Test fun `invalid changed protection and pause cancellation conserve charge`() {
        val d=fixture(HouseRule.COMET)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(orbitCharged=true)
        execute(d) {
            assertTrue(d.vm.beginOrbitAim()); d.vm.selectOrbitZone(c); assertNull(d.vm.previewOrbit()); d.vm.confirmOrbit().join()
            assertTrue(d.state.orbitCharged); d.vm.cancelOrbitAim()
            field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=d.state.grid+(Position(1,1) to CellContent.Wood()))
            aim(d)
            field<GameState>(d.vm,"_gameState").value=d.state.copy(frozenCells=setOf(Position(3,3)))
            d.vm.confirmOrbit().join(); assertTrue(d.state.orbitCharged); assertFalse(d.state.orbitPending)
            d.vm.pauseGame(); assertNull(d.vm.orbitAim.value); d.vm.resumeGame()
        }
        assertFalse(d.vm.boardBusy.value); assertTrue(d.state.orbitCharged)
    }
    private fun lineFixture(d: EngineDriver) {
        val grid=d.state.grid.toMutableMap()
        for(x in 0..7) if(x !in 1..3) grid[Position(x,1)]=CellContent.Wood()
        grid[Position(1,1)]=CellContent.Wood()
        grid[Position(1,2)]=CellContent.Wood()
        grid[Position(1,3)]=CellContent.Wood(targetColor=TargetColor.RED)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(orbitCharged=true,grid=grid)
    }
    @Test fun `own lines give normal rewards without recharging or advancing biome`() {
        val d=fixture(HouseRule.COMET); lineFixture(d)
        execute(d) { aim(d); d.vm.confirmOrbit().join() }
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.targetsCollected[TargetColor.RED])
        assertEquals(0,d.state.placementsInLevel); assertFalse(d.state.orbitCharged)
    }
    @Test fun `pause after commit keeps the giro and resolves its line once on resume`() {
        val d=fixture(HouseRule.COMET); lineFixture(d)
        execute(d) {
            aim(d); val job=d.vm.confirmOrbit()
            while(!d.state.orbitPending) delay(1)
            d.vm.pauseGame(); val ids=d.state.grid.values.filterIsInstance<CellContent.Wood>().map { it.id }.toSet()
            delay(1200); assertTrue(d.state.orbitPending); assertEquals(GameStatus.PAUSED,d.state.status)
            assertEquals(ids,d.state.grid.values.filterIsInstance<CellContent.Wood>().map { it.id }.toSet())
            d.vm.resumeGame(); job.join()
        }
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.targetsCollected[TargetColor.RED])
        assertFalse(d.state.orbitPending); assertFalse(d.vm.boardBusy.value)
    }
    @Test fun `selection freezes real physics and blocks other actions including Storm`() {
        val d=fixture(HouseRule.COMET)
        val world=GameViewModel::class.java.getDeclaredField("physicsWorld").apply { isAccessible=true }.get(d.vm) as com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld
        val b=world.addBody(TeikaType.TEIKA_1,4.48f,2.5f)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(orbitCharged=true,biomePrimary=BiomeType.STORM,
            grid=d.state.grid+mapOf(Position(1,1) to CellContent.Wood(),Position(7,7) to CellContent.Wood(coil=1)),
            availablePieces=listOf(Piece(shape=(0..12).map { Position(it,0) })))
        execute(d) {
            assertTrue(d.vm.beginOrbitAim()); d.vm.selectOrbitZone(c); assertNull(d.vm.previewOrbit())
            val initial=world.getBody(b.id)!!
            repeat(5) { d.vm.onTeikaPhysicsFrame(.05f) }; assertEquals(initial.x,world.getBody(b.id)!!.x,0f); assertEquals(initial.y,world.getBody(b.id)!!.y,0f)
            d.vm.tapCoil(Position(7,7)); d.vm.placePiece(d.state.availablePieces.first(),0,5).join()
            assertFalse((d.state.grid[Position(7,7)] as CellContent.Wood).coilLit); assertEquals(0,d.state.placementsInLevel)
            assertFalse(d.vm.canBuildFromBank()); d.vm.cancelOrbitAim()
            assertNull(d.vm.evaluateDeadEnd())
        }
        assertTrue(d.state.orbitCharged); assertFalse(d.vm.boardBusy.value)
    }
    @Test fun `normal and Storm lines charge orbit once`() {
        for(storm in listOf(false,true)) {
            val d=fixture(HouseRule.COMET)
            val grid=d.state.grid.toMutableMap()
            for(x in 0..if(storm) 7 else 6) grid[Position(x,0)]=CellContent.Wood()
            field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,biomePrimary=if(storm) BiomeType.STORM else BiomeType.NONE,
                availablePieces=List(2) { Piece(shape=listOf(Position(0,0))) })
            if(storm) {
                val m=GameViewModel::class.java.getDeclaredMethod("detonateCircuitRows",Set::class.java,kotlin.coroutines.Continuation::class.java).apply { isAccessible=true }
                execute(d) { kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Unit> { c -> m.invoke(d.vm,setOf(0),c) } }
            } else execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,0).join() }
            assertTrue(d.state.orbitCharged); assertEquals(if(storm) 0 else 1,d.state.placementsInLevel)
        }
    }
    @Test fun `saved orbit chain finishes without replay or charge`() {
        val d=fixture(HouseRule.COMET)
        val grid=d.state.grid.toMutableMap()
        for(y in 0..7) grid[Position(7,y)]=CellContent.Wood(targetColor=if(y==0) TargetColor.RED else null)
        val saved=d.state.copy(grid=grid,orbitPending=true,hiddenWoodCells=setOf(Position(7,0)))
        val prefs=GameViewModel::class.java.getDeclaredField("prefs").apply { isAccessible=true }.get(d.vm) as PreferencesManager
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; encodeDefaults=true }
        execute(d) {
            prefs.saveGameState(json.encodeToString(GameState.serializer(),saved),level=saved.level)
            field<GameState>(d.vm,"_gameState").value=saved.copy(score=-1)
            d.vm.continueGame()
            while(d.state.score == -1 || d.state.orbitPending || d.vm.boardBusy.value) delay(16)
        }
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.targetsCollected[TargetColor.RED])
        assertFalse(d.state.orbitCharged); assertTrue(d.state.hiddenWoodCells.isEmpty())
        assertEquals(0,d.state.placementsInLevel)
    }

}
