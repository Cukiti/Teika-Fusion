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
class PortalSwapEngineTest {
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

    private fun aim(d: EngineDriver,a: Position=Position(0,0),b: Position=Position(4,4)) {
        assertTrue(d.vm.beginPortalSwapAim()); d.vm.selectPortalSwapZone(a); d.vm.selectPortalSwapZone(b)
        assertNotNull(d.vm.previewPortalSwap())
    }
    @Test fun `exchange conserves complete wood and cannot spend twice or add a turn`() {
        val d=fixture(HouseRule.ROUTES)
        val wood=CellContent.Wood(targetColor=TargetColor.RED,teikaSeed=true,armor=1,poisoned=true)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(portalSwapCharged=true,grid=d.state.grid+(Position(0,0) to wood))
        execute(d) { aim(d); d.vm.confirmPortalSwap().join(); d.vm.confirmPortalSwap().join() }
        val landed=d.state.grid[Position(4,4)] as CellContent.Wood
        assertEquals(wood.copy(skipSpawnAnimation=true),landed)
        assertEquals(CellContent.Empty,d.state.grid[Position(0,0)])
        assertEquals(0,d.state.placementsInLevel); assertEquals(0,d.state.linesClearedInLevel)
        assertFalse(d.state.portalSwapCharged); assertFalse(d.state.portalSwapPending)
        assertNull(d.vm.portalSwapAim.value); assertFalse(d.vm.boardBusy.value); assertTrue(d.state.hiddenWoodCells.isEmpty())
    }
    @Test fun `empty protected overlapping and cancelled selections never consume a charge`() {
        val d=fixture(HouseRule.ROUTES)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(portalSwapCharged=true)
        execute(d) {
            assertTrue(d.vm.beginPortalSwapAim()); d.vm.selectPortalSwapZone(Position(0,0)); d.vm.selectPortalSwapZone(Position(4,4))
            assertNull(d.vm.previewPortalSwap()); d.vm.confirmPortalSwap().join()
            assertTrue(d.state.portalSwapCharged); d.vm.cancelPortalSwapAim()
            field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=d.state.grid+(Position(0,0) to CellContent.Wood()),frozenCells=setOf(Position(5,5)))
            assertTrue(d.vm.beginPortalSwapAim()); d.vm.selectPortalSwapZone(Position(0,0))
            d.vm.selectPortalSwapZone(Position(4,4)); assertEquals("Zona protegida",d.vm.portalSwapAim.value!!.feedback)
            d.vm.selectPortalSwapZone(Position(1,0)); assertEquals("Separa las dos zonas",d.vm.portalSwapAim.value!!.feedback)
            d.vm.pauseGame(); assertNull(d.vm.portalSwapAim.value); d.vm.resumeGame()
        }
        assertTrue(d.state.portalSwapCharged); assertFalse(d.vm.boardBusy.value)
    }
    @Test fun `own line rewards are genuine but do not recharge or advance biome`() {
        val d=fixture(HouseRule.ROUTES)
        val grid=d.state.grid.toMutableMap()
        for(y in 0..7) if(y!=4) grid[Position(4,y)]=CellContent.Wood()
        grid[Position(0,0)]=CellContent.Wood(targetColor=TargetColor.RED)
        grid[Position(0,1)]=CellContent.Wood()
        field<GameState>(d.vm,"_gameState").value=d.state.copy(portalSwapCharged=true,grid=grid)
        execute(d) { aim(d); d.vm.confirmPortalSwap().join() }
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.targetsCollected[TargetColor.RED])
        assertEquals(0,d.state.placementsInLevel); assertFalse(d.state.portalSwapCharged)
    }
    @Test fun `charged portal avoids premature defeat and locks gestures and Storm taps`() {
        val d=fixture(HouseRule.ROUTES)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(portalSwapCharged=true,
            grid=d.state.grid+mapOf(Position(0,0) to CellContent.Wood(),Position(7,7) to CellContent.Wood(coil=1)),
            availablePieces=listOf(Piece(shape=(0..12).map { Position(it,0) })),biomePrimary=BiomeType.STORM)
        execute(d) {
            assertNull(d.vm.evaluateDeadEnd()); assertTrue(d.vm.beginPortalSwapAim())
            d.vm.tapCoil(Position(7,7)); d.vm.placePiece(d.state.availablePieces.first(),0,1).join(); delay(50)
            assertFalse((d.state.grid[Position(7,7)] as CellContent.Wood).coilLit)
            assertEquals(0,d.state.placementsInLevel); assertFalse(d.vm.canBuildFromBank()); d.vm.cancelPortalSwapAim()
        }
    }
    @Test fun `pause after commit retains swap and resumes line chain once`() {
        val d=fixture(HouseRule.ROUTES)
        val grid=d.state.grid.toMutableMap()
        for(y in 0..7) if(y!=4) grid[Position(4,y)]=CellContent.Wood()
        grid[Position(0,0)]=CellContent.Wood(targetColor=TargetColor.RED)
        grid[Position(0,1)]=CellContent.Wood()
        field<GameState>(d.vm,"_gameState").value=d.state.copy(portalSwapCharged=true,grid=grid)
        execute(d) {
            aim(d); val job=d.vm.confirmPortalSwap()
            while(!d.state.portalSwapPending) delay(1)
            d.vm.pauseGame(); val ids=d.state.grid.values.filterIsInstance<CellContent.Wood>().map { it.id }.toSet()
            delay(1100); assertTrue(d.state.portalSwapPending); assertEquals(GameStatus.PAUSED,d.state.status)
            assertEquals(ids,d.state.grid.values.filterIsInstance<CellContent.Wood>().map { it.id }.toSet())
            d.vm.resumeGame(); job.join()
        }
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.targetsCollected[TargetColor.RED])
        assertFalse(d.state.portalSwapPending); assertFalse(d.vm.boardBusy.value)
    }
    @Test fun `normal and Storm lines charge portal once`() {
        for(storm in listOf(false,true)) {
            val d=fixture(HouseRule.ROUTES)
            val grid=d.state.grid.toMutableMap()
            for(x in 0..if(storm) 7 else 6) grid[Position(x,0)]=CellContent.Wood()
            field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,biomePrimary=if(storm) BiomeType.STORM else BiomeType.NONE,
                availablePieces=List(2) { Piece(shape=listOf(Position(0,0))) })
            if(storm) {
                val m=GameViewModel::class.java.getDeclaredMethod("detonateCircuitRows",Set::class.java,kotlin.coroutines.Continuation::class.java).apply { isAccessible=true }
                execute(d) { kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Unit> { c -> m.invoke(d.vm,setOf(0),c) } }
            } else execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,0).join() }
            assertTrue(d.state.portalSwapCharged); assertEquals(if(storm) 0 else 1,d.state.placementsInLevel)
        }
    }
    @Test fun `saved portal chain finishes without replay or charge`() {
        val d=fixture(HouseRule.ROUTES)
        val grid=d.state.grid.toMutableMap()
        for(y in 0..7) grid[Position(7,y)]=CellContent.Wood(targetColor=if(y==0) TargetColor.RED else null)
        val saved=d.state.copy(grid=grid,portalSwapPending=true,hiddenWoodCells=setOf(Position(7,0)))
        val prefs=GameViewModel::class.java.getDeclaredField("prefs").apply { isAccessible=true }.get(d.vm) as PreferencesManager
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; encodeDefaults=true }
        execute(d) {
            prefs.saveGameState(json.encodeToString(GameState.serializer(),saved),level=saved.level)
            field<GameState>(d.vm,"_gameState").value=saved.copy(score=-1)
            d.vm.continueGame()
            while(d.state.score == -1 || d.state.portalSwapPending || d.vm.boardBusy.value) delay(16)
        }
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.targetsCollected[TargetColor.RED])
        assertFalse(d.state.portalSwapCharged); assertTrue(d.state.hiddenWoodCells.isEmpty())
        assertEquals(0,d.state.placementsInLevel)
    }

}
