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
class ReplacementEngineTest {
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

    @Test fun `compactadora cancels freely and moves complete blocks without a turn`() {
        val d=fixture(HouseRule.PRECISION_CUT)
        val wood=CellContent.Wood(targetColor=TargetColor.RED,armor=1,poisoned=true)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(compactorCharged=true,grid=d.state.grid+(Position(0,2) to wood))
        execute(d) { assertTrue(d.vm.beginCompactorAim()); d.vm.cancelCompactorAim() }
        assertTrue(d.state.compactorCharged); assertFalse(d.vm.boardBusy.value)
        execute(d) { assertTrue(d.vm.beginCompactorAim()); d.vm.releaseCompactor(0,2,0).join() }
        val landed=d.state.grid[Position(7,2)] as CellContent.Wood
        assertEquals(wood.id,landed.id); assertEquals(wood.targetColor,landed.targetColor)
        assertEquals(1,landed.armor); assertTrue(landed.poisoned)
        assertEquals(0,d.state.placementsInLevel); assertEquals(0,d.state.linesClearedInLevel)
        assertFalse(d.state.compactorCharged); assertFalse(d.state.compactorPending)
        assertFalse(d.vm.boardBusy.value); assertTrue(d.state.hiddenWoodCells.isEmpty())
        assertFalse(d.vm.canCutPiece(d.state.availablePieces.first().id))
    }
    @Test fun `compactadora clears real lines but never recharges itself or ticks biome`() {
        val d=fixture(HouseRule.PRECISION_CUT)
        val grid=d.state.grid.toMutableMap()
        for(y in 1..7) grid[Position(7,y)]=CellContent.Wood()
        grid[Position(0,0)]=CellContent.Wood(targetColor=TargetColor.RED)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(compactorCharged=true,grid=grid)
        execute(d) { assertTrue(d.vm.beginCompactorAim()); d.vm.releaseCompactor(0,0,0).join() }
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.targetsCollected[TargetColor.RED])
        assertEquals(0,d.state.placementsInLevel); assertFalse(d.state.compactorCharged)
        assertFalse(d.state.compactorPending); assertFalse(d.vm.boardBusy.value)
    }
    @Test fun `compactadora no movement and pause preserve charge`() {
        val d=fixture(HouseRule.PRECISION_CUT)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(compactorCharged=true)
        execute(d) { assertTrue(d.vm.beginCompactorAim()); d.vm.releaseCompactor(0,0,0).join() }
        assertTrue(d.state.compactorCharged); assertFalse(d.vm.boardBusy.value)
        execute(d) { assertTrue(d.vm.beginCompactorAim()); d.vm.pauseGame(); d.vm.resumeGame() }
        assertTrue(d.state.compactorCharged); assertFalse(d.vm.compactorAiming.value)
        assertFalse(d.vm.boardBusy.value)
    }

    @Test fun `charged compactadora is a way out and blocks Storm touches while aiming`() {
        val d=fixture(HouseRule.PRECISION_CUT)
        val grid=d.state.grid+(Position(0,1) to CellContent.Wood())
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,compactorCharged=true,biomePrimary=BiomeType.NONE,
            availablePieces=listOf(Piece(shape=(0..12).map { Position(it,0) })))
        execute(d) {
            assertNull(d.vm.evaluateDeadEnd())
            field<GameState>(d.vm,"_gameState").value=d.state.copy(biomePrimary=BiomeType.STORM,grid=d.state.grid+(Position(7,7) to CellContent.Wood(coil=1)))
            assertTrue(d.vm.beginCompactorAim())
            d.vm.tapCoil(Position(7,7)); delay(50)
            assertFalse((d.state.grid[Position(7,7)] as CellContent.Wood).coilLit)
            d.vm.cancelCompactorAim()
        }
        assertTrue(d.state.compactorCharged)
    }

    @Test fun `compactadora pauses a committed press and resumes its chain once`() {
        val d=fixture(HouseRule.PRECISION_CUT)
        val grid=d.state.grid.toMutableMap()
        for(y in 1..7) grid[Position(7,y)]=CellContent.Wood()
        grid[Position(0,0)]=CellContent.Wood(targetColor=TargetColor.RED)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(compactorCharged=true,grid=grid)
        execute(d) {
            assertTrue(d.vm.beginCompactorAim())
            val press=d.vm.releaseCompactor(0,0,0)
            while(!d.state.compactorPending) delay(1)
            d.vm.pauseGame()
            val movedIds=d.state.grid.values.filterIsInstance<CellContent.Wood>().map { it.id }.toSet()
            delay(1100)
            assertEquals(GameStatus.PAUSED,d.state.status)
            assertTrue(d.state.compactorPending); assertFalse(d.state.compactorCharged)
            assertEquals(movedIds,d.state.grid.values.filterIsInstance<CellContent.Wood>().map { it.id }.toSet())
            d.vm.resumeGame(); press.join()
        }
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.targetsCollected[TargetColor.RED])
        assertEquals(0,d.state.placementsInLevel)
        assertFalse(d.state.compactorPending); assertFalse(d.vm.boardBusy.value)
    }

    @Test fun `covering constellation points over multiple placements collects each star once`() {
        val d = fixture(HouseRule.CONSTELLATION)
        val points = listOf(Position(0,0),Position(2,0),Position(4,0))
        val target = Position(1,0)
        field<GameState>(d.vm,"_gameState").value = d.state.copy(constellationPoints = points,
            grid = d.state.grid + (target to CellContent.Wood(targetColor = TargetColor.RED)),
            availablePieces = List(3) { Piece(shape = listOf(Position(0,0))) })
        for (p in points) execute(d) { d.vm.placePiece(d.state.availablePieces.first(),p.x,p.y).join() }
        assertEquals(1,d.state.targetsCollected[TargetColor.RED]); assertTrue(d.state.grid[target] is CellContent.Empty)
        assertEquals(1,d.state.constellationWave); assertTrue(d.state.constellationCovered.isEmpty())
        assertEquals(3,d.state.placementsInLevel)
    }

    @Test fun `real line charges ball without firing legacy domino arrows`() {
        val d = fixture(HouseRule.DOMINO)
        val root = Position(6,0); val hit = Position(6,1); val protected = Position(6,2)
        val grid = d.state.grid.toMutableMap()
        (0..6).forEach { grid[Position(it,0)] = CellContent.Wood() }
        grid[root] = CellContent.Wood(dominoDir = 1)
        grid[hit] = CellContent.Wood(dominoDir = 1,targetColor = TargetColor.RED)
        grid[protected] = CellContent.Wood(armor = 1,targetColor = TargetColor.BLUE)
        field<GameState>(d.vm,"_gameState").value = d.state.copy(grid = grid, availablePieces = List(2) { Piece(shape = listOf(Position(0,0))) })
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,0).join() }
        assertTrue(d.state.grid[hit] is CellContent.Wood)
        assertNull(d.state.targetsCollected[TargetColor.RED]); assertNull(d.state.targetsCollected[TargetColor.BLUE])
        assertEquals(1,(d.state.grid[protected] as CellContent.Wood).armor)
        assertTrue(d.state.ballCharged)
        assertEquals(1,d.state.linesClearedInLevel)
    }

    @Test fun `star wish expansion covers constellation points even if their wood clears immediately`() {
        val d = fixture(HouseRule.CONSTELLATION)
        field<GameState>(d.vm,"_gameState").value = d.state.copy(
            constellationPoints = listOf(Position(0,0),Position(1,0),Position(0,1)),
            availablePieces = listOf(Piece(shape = listOf(Position(0,0)),isStarWish = true),Piece(shape = listOf(Position(0,0)))))
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),0,0).join() }
        assertEquals(1,d.state.constellationWave)
    }

    @Test fun `pinball aim cancellation keeps charge and launch consumes it without a turn`() {
        val d=fixture(HouseRule.TRAMPOLINE)
        val world=GameViewModel::class.java.getDeclaredField("physicsWorld").apply { isAccessible=true }.get(d.vm) as com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld
        field<GameState>(d.vm,"_gameState").value=d.state.copy(pinballCharged=true)
        val body=world.addBody(TeikaType.TEIKA_1,3.5f,3f)
        assertTrue(d.vm.beginPinballAim(body.id)); d.vm.releasePinball(body.id,0f,0f)
        assertTrue(d.state.pinballCharged); assertNull(d.state.pinballFlight)
        assertTrue(d.vm.beginPinballAim(body.id)); d.vm.cancelPinballAim()
        assertTrue(d.state.pinballCharged)
        assertTrue(d.vm.beginPinballAim(body.id)); d.vm.releasePinball(body.id,-1f,0f)
        execute(d) { repeat(2) { d.vm.onTeikaPhysicsFrame(.016f); delay(10) } }
        assertFalse(d.state.pinballCharged); assertEquals(0,d.state.placementsInLevel)
        assertNotNull(d.state.pinballFlight)
    }
    @Test fun `pinball collects impacted stars once preserves shields and pauses its clock`() {
        val d=fixture(HouseRule.TRAMPOLINE)
        val world=GameViewModel::class.java.getDeclaredField("physicsWorld").apply { isAccessible=true }.get(d.vm) as com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld
        val target=Position(4,3); val shield=Position(1,3)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(pinballCharged=true,grid=d.state.grid+
            (target to CellContent.Wood(targetColor=TargetColor.RED))+(shield to CellContent.Wood(armor=1)))
        val body=world.addBody(TeikaType.TEIKA_1,2.5f,3.5f)
        assertTrue(d.vm.beginPinballAim(body.id)); d.vm.releasePinball(body.id,-1f,0f)
        execute(d) { delay(20); repeat(24) { d.vm.onTeikaPhysicsFrame(.016f) } }
        assertTrue(d.state.grid[target] is CellContent.Empty)
        assertEquals(1,d.state.targetsCollected[TargetColor.RED]); assertEquals(1,(d.state.grid[shield] as CellContent.Wood).armor)
        val seconds=requireNotNull(world.pinballSnapshot()).secondsLeft
        val saved=GameViewModel::class.java.getDeclaredMethod("withTeikaBodySaves",GameState::class.java).apply { isAccessible=true }.invoke(d.vm,d.state) as GameState
        assertEquals(world.pinballSnapshot(),saved.pinballFlight)
        assertEquals(world.getBody(body.id)!!.x,saved.teikaBodySaves.first { it.id==body.id }.x,0f)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(status=GameStatus.PAUSED)
        execute(d) { repeat(20) { d.vm.onTeikaPhysicsFrame(.05f) } }
        assertEquals(seconds,requireNotNull(world.pinballSnapshot()).secondsLeft,0f)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(status=GameStatus.PLAYING)
        execute(d) { repeat(220) { d.vm.onTeikaPhysicsFrame(.016f) } }
        assertNull(d.state.pinballFlight); assertFalse(d.vm.boardBusy.value); assertEquals(1,d.state.targetsCollected[TargetColor.RED]); assertEquals(0,d.state.placementsInLevel)
    }
    @Test fun `crossed lines charge forge without the retired secondary wave`() {
        val d = fixture(HouseRule.SHOCKWAVE)
        val grid = d.state.grid.toMutableMap()
        for (i in 0..7) if (i != 4) {
            grid[Position(i,4)] = CellContent.Wood()
            grid[Position(4,i)] = CellContent.Wood()
        }
        val target = Position(3,3)
        grid[target] = CellContent.Wood(targetColor = TargetColor.RED, teikaSeed = true)
        grid[Position(5,5)] = CellContent.Wood(armor = 1, targetColor = TargetColor.BLUE)
        field<GameState>(d.vm,"_gameState").value = d.state.copy(grid = grid,
            availablePieces = List(2) { Piece(shape = listOf(Position(0,0))) })
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),4,4).join() }
        assertTrue(d.state.grid[target] is CellContent.Wood)
        assertTrue(d.state.forgeCharged)
        assertFalse(d.state.events.filterIsInstance<GameEvent.RulePulse>().any { it.kind=="shockwave" })
        assertEquals(0,d.state.targetsCollected[TargetColor.RED] ?: 0)
        assertEquals(1,(d.state.grid[Position(5,5)] as CellContent.Wood).armor)
        assertTrue(d.state.teikasCreatedInLevel.isEmpty())
        assertEquals(2,d.state.linesClearedInLevel)
        assertEquals(1,d.state.placementsInLevel)
    }

    @Test fun `single line does not trigger avalanche or move floating wood`() {
        val d = fixture(HouseRule.AVALANCHE)
        val grid = d.state.grid.toMutableMap()
        (0..6).forEach { grid[Position(it,7)] = CellContent.Wood() }
        val wood = CellContent.Wood(targetColor = TargetColor.RED)
        grid[Position(0,0)] = wood
        field<GameState>(d.vm,"_gameState").value = d.state.copy(grid = grid,
            availablePieces = List(2) { Piece(shape = listOf(Position(0,0))) })
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,7).join() }
        assertEquals(wood.id,(d.state.grid[Position(0,0)] as CellContent.Wood).id)
        assertFalse(d.state.avalanchePending)
        assertEquals(1,d.state.avalancheCharge)
    }

    @Test fun `double line collapses wood preserving its star and ice anchors`() {
        val d = fixture(HouseRule.AVALANCHE)
        val grid = d.state.grid.toMutableMap()
        for (y in 6..7) for (x in 0..6) grid[Position(x,y)] = CellContent.Wood()
        val moving = CellContent.Wood(targetColor = TargetColor.RED, armor = 1)
        val frozen = CellContent.Wood(targetColor = TargetColor.BLUE)
        grid[Position(0,0)] = moving; grid[Position(1,0)] = frozen
        field<GameState>(d.vm,"_gameState").value = d.state.copy(grid = grid, frozenCells = setOf(Position(1,0)),
            availablePieces = listOf(Piece(shape = listOf(Position(0,0),Position(0,1))),Piece(shape = listOf(Position(0,0)))))
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,6).join() }
        val landed = d.state.grid[Position(0,7)] as CellContent.Wood
        assertEquals(moving.id,landed.id); assertEquals(TargetColor.RED,landed.targetColor); assertEquals(1,landed.armor)
        assertEquals(frozen.id,(d.state.grid[Position(1,0)] as CellContent.Wood).id)
        assertTrue(d.state.grid[Position(0,0)] is CellContent.Empty)
        assertFalse(d.state.avalanchePending); assertTrue(d.state.hiddenWoodCells.isEmpty())
        assertEquals(2,d.state.linesClearedInLevel)
        assertEquals(1,d.state.placementsInLevel)
    }

    @Test fun `storm multi row discharge also triggers avalanche without another turn`() {
        val d = fixture(HouseRule.AVALANCHE)
        val grid = d.state.grid.toMutableMap()
        for (y in 6..7) for (x in 0..7) grid[Position(x,y)] = CellContent.Wood()
        val moving = CellContent.Wood(targetColor = TargetColor.RED)
        grid[Position(0,0)] = moving
        field<GameState>(d.vm,"_gameState").value = d.state.copy(grid = grid, biomePrimary = BiomeType.STORM)
        val method = GameViewModel::class.java.getDeclaredMethod("detonateCircuitRows",Set::class.java,kotlin.coroutines.Continuation::class.java)
            .apply { isAccessible = true }
        execute(d) {
            kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Unit> { c -> method.invoke(d.vm,setOf(6,7),c) }
        }
        assertEquals(moving.id,(d.state.grid[Position(0,7)] as CellContent.Wood).id)
        assertFalse(d.state.avalanchePending); assertTrue(d.state.hiddenWoodCells.isEmpty())
        assertEquals(0,d.state.placementsInLevel)
    }

    @Test fun `continuing a saved pending avalanche finishes collapse before another placement`() {
        val d = fixture(HouseRule.AVALANCHE)
        val wood = CellContent.Wood(targetColor = TargetColor.RED)
        val state = d.state.copy(avalanchePending = true, grid = d.state.grid + (Position(0,0) to wood))
        field<GameState>(d.vm,"_gameState").value = state
        val prefs = GameViewModel::class.java.getDeclaredField("prefs").apply { isAccessible = true }.get(d.vm) as PreferencesManager
        val json = kotlinx.serialization.json.Json { allowStructuredMapKeys = true; ignoreUnknownKeys = true; encodeDefaults = true }
        execute(d) {
            prefs.saveGameState(json.encodeToString(GameState.serializer(),state),level = state.level)
            d.vm.continueGame()
            while (d.state.avalanchePending || d.state.hiddenWoodCells.isNotEmpty() || d.vm.boardBusy.value) delay(16)
        }
        assertEquals(wood.id,(d.state.grid[Position(0,7)] as CellContent.Wood).id)
        assertEquals(0,d.state.placementsInLevel)
        assertFalse(d.vm.boardBusy.value)
    }

    @Test fun `save during the visible fall retains the pending marker and resumes without duplicating wood`() {
        val d = fixture(HouseRule.AVALANCHE)
        val wood = CellContent.Wood(targetColor = TargetColor.RED)
        field<GameState>(d.vm,"_gameState").value = d.state.copy(avalanchePending = true,grid = d.state.grid + (Position(0,0) to wood))
        val prefs = GameViewModel::class.java.getDeclaredField("prefs").apply { isAccessible = true }.get(d.vm) as PreferencesManager
        val json = kotlinx.serialization.json.Json { allowStructuredMapKeys = true; ignoreUnknownKeys = true; encodeDefaults = true }
        val settle = GameViewModel::class.java.getDeclaredMethod("settleBoardGravity",kotlin.coroutines.Continuation::class.java).apply { isAccessible = true }
        execute(d) {
            coroutineScope {
                val falling = async { kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Boolean> { c -> settle.invoke(d.vm,c) } }
                while (d.state.hiddenWoodCells.isEmpty()) delay(1)
                val midFall = d.state
                assertTrue(midFall.avalanchePending)
                val encoded = json.encodeToString(GameState.serializer(),midFall)
                falling.await()
                prefs.saveGameState(encoded,level = midFall.level)
                field<GameState>(d.vm,"_gameState").value = d.state.copy(score = -1)
                d.vm.continueGame()
                // A sentinel distinguishes actual reload from the already finished original fall.
                while (d.state.score == -1) delay(16)
                while (d.state.avalanchePending || d.state.hiddenWoodCells.isNotEmpty() || d.vm.boardBusy.value) delay(16)
            }
        }
        assertFalse(d.state.avalanchePending)
        assertEquals(1,d.state.grid.values.filterIsInstance<CellContent.Wood>().count { it.id == wood.id })
        assertEquals(wood.id,(d.state.grid[Position(0,7)] as CellContent.Wood).id)
        assertEquals(0,d.state.placementsInLevel)
    }

    @Test fun `two separate single line placements collapse the board without a third turn`() {
        val d=fixture(HouseRule.AVALANCHE)
        val wood=CellContent.Wood(targetColor=TargetColor.RED)
        val grid=d.state.grid.toMutableMap()
        for(y in 6..7) for(x in 0..6) grid[Position(x,y)]=CellContent.Wood()
        grid[Position(0,0)]=wood
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,availablePieces=List(3) { Piece(shape=listOf(Position(0,0))) })
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,7).join() }
        assertEquals(1,d.state.avalancheCharge)
        assertEquals(wood.id,(d.state.grid[Position(0,0)] as CellContent.Wood).id)
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,6).join() }
        assertEquals(wood.id,(d.state.grid[Position(0,7)] as CellContent.Wood).id)
        assertEquals(0,d.state.avalancheCharge); assertFalse(d.state.avalanchePending)
        assertEquals(2,d.state.placementsInLevel); assertEquals(2,d.state.linesClearedInLevel)
    }
    @Test fun `a normal line arms a single pinball charge`() {
        val d=fixture(HouseRule.TRAMPOLINE)
        val grid=d.state.grid.toMutableMap()
        for(x in 0..6) grid[Position(x,0)]=CellContent.Wood()
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,trampolineCell=Position(3,7),availablePieces=List(2) { Piece(shape=listOf(Position(0,0))) })
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,0).join() }
        assertTrue(d.state.pinballCharged); assertEquals(1,d.state.placementsInLevel)
    }
    @Test fun `normal and Storm lines charge compactadora once`() {
        for(storm in listOf(false,true)) {
            val d=fixture(HouseRule.PRECISION_CUT)
            val grid=d.state.grid.toMutableMap()
            for(x in 0..if(storm) 7 else 6) grid[Position(x,0)]=CellContent.Wood()
            field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,biomePrimary=if(storm) BiomeType.STORM else BiomeType.NONE,
                availablePieces=List(2) { Piece(shape=listOf(Position(0,0))) })
            if(storm) {
                val m=GameViewModel::class.java.getDeclaredMethod("detonateCircuitRows",Set::class.java,kotlin.coroutines.Continuation::class.java).apply { isAccessible=true }
                execute(d) { kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Unit> { c -> m.invoke(d.vm,setOf(0),c) } }
            } else execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,0).join() }
            assertTrue(d.state.compactorCharged); assertEquals(if(storm) 0 else 1,d.state.placementsInLevel)
        }
    }
    @Test fun `saved compactadora chain finishes without replay or charge`() {
        val d=fixture(HouseRule.PRECISION_CUT)
        val grid=d.state.grid.toMutableMap()
        for(y in 0..7) grid[Position(7,y)]=CellContent.Wood(targetColor=if(y==0) TargetColor.RED else null)
        val saved=d.state.copy(grid=grid,compactorPending=true,hiddenWoodCells=setOf(Position(7,0)))
        val prefs=GameViewModel::class.java.getDeclaredField("prefs").apply { isAccessible=true }.get(d.vm) as PreferencesManager
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; encodeDefaults=true }
        execute(d) {
            prefs.saveGameState(json.encodeToString(GameState.serializer(),saved),level=saved.level)
            field<GameState>(d.vm,"_gameState").value=saved.copy(score=-1)
            d.vm.continueGame()
            while(d.state.score == -1 || d.state.compactorPending || d.vm.boardBusy.value) delay(16)
        }
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.targetsCollected[TargetColor.RED])
        assertFalse(d.state.compactorCharged); assertTrue(d.state.hiddenWoodCells.isEmpty())
        assertEquals(0,d.state.placementsInLevel)
    }

    @Test fun `presentation holds physics by motor clock even without a UI event consumer`() {
        val d=fixture(HouseRule.DOMINO)
        var now=0L
        d.vm.clockMs={ now }
        val world=GameViewModel::class.java.getDeclaredField("physicsWorld").apply { isAccessible=true }.get(d.vm) as com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld
        val body=world.addBody(TeikaType.TEIKA_1,3.5f,3f,vy=1f)
        GameViewModel::class.java.getDeclaredMethod("holdRulePhysics",java.lang.Long.TYPE).apply { isAccessible=true }.invoke(d.vm,900L)
        execute(d) { d.vm.onTeikaPhysicsFrame(.016f) }
        assertEquals(body.y,requireNotNull(world.getBody(body.id)).y,.0001f)
        now=901L
        execute(d) { d.vm.onTeikaPhysicsFrame(.016f) }
        assertTrue(requireNotNull(world.getBody(body.id)).y>body.y)
    }


    private fun world(d: EngineDriver) = GameViewModel::class.java.getDeclaredField("physicsWorld").apply { isAccessible=true }.get(d.vm) as com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld

    @Test fun `legacy elevator no longer automatically carries a passenger`() {
        val d=fixture(HouseRule.ELEVATOR); val p=ElevatorRules.platform(d.state.level)
        val w=world(d); w.addBody(TeikaType.TEIKA_1,p.x+.5f,5.5f)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(elevatorCharged=true)
        runBlocking { repeat(8) { d.vm.onTeikaPhysicsFrame(.016f) } }
        assertNull(d.state.elevatorTrip); assertNull(d.state.clawTrip); assertFalse(d.vm.boardBusy.value)
    }

    @Test fun `surf impulse preserves protected bodies and moves a real passenger`() {
        val d=fixture(HouseRule.SURF); val w=world(d)
        val passenger=w.addBody(TeikaType.TEIKA_1,2.5f,3.5f)
        val frozen=w.addBody(TeikaType.TEIKA_2,5f,3f)
        w.setFrozenTurns(frozen.id,2)
        val captured=w.addBody(TeikaType.TEIKA_3,3f,1.5f)
        w.setKinematic(captured.id,true)
        val bubble=w.addBody(TeikaType.TEIKA_1,6.5f,1f)
        w.setBubble(bubble.id,2)
        assertTrue(w.surfPush(passenger.id,-1))
        assertFalse(w.surfPush(frozen.id,1)); assertFalse(w.surfPush(captured.id,1)); assertFalse(w.surfPush(bubble.id,1))
        assertFalse(w.surfPush("missing",1))
        assertEquals(-6f,requireNotNull(w.getBody(passenger.id)).vx,0f)
        assertEquals(2,requireNotNull(w.getBody(frozen.id)).frozenTurns)
        assertTrue(requireNotNull(w.getBody(captured.id)).kinematic)
        assertEquals(2,requireNotNull(w.getBody(bubble.id)).bubble)
        assertEquals(4,w.snapshot().size)
        runBlocking { d.vm.onTeikaPhysicsFrame(.016f) }
        assertTrue(requireNotNull(w.getBody(passenger.id)).x<passenger.x)
    }

    @Test fun `row clear charges turbine without moving any adjacent protected wood`() {
        val d=fixture(HouseRule.SLIDE); val grid=d.state.grid.toMutableMap()
        for(x in 0..6) grid[Position(x,5)]=CellContent.Wood(colorType=x%3)
        val wood=CellContent.Wood(targetColor=TargetColor.RED,armor=1,teikaSeed=true)
        val frozen=CellContent.Wood(targetColor=TargetColor.BLUE)
        grid[Position(3,4)]=wood; grid[Position(5,4)]=frozen
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,frozenCells=setOf(Position(5,4)),availablePieces=List(2) { Piece(shape=listOf(Position(0,0))) })
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,5).join() }
        val destination=Position(3,4)
        val moved=d.state.grid[destination] as CellContent.Wood
        assertEquals(wood.id,moved.id); assertEquals(wood.targetColor,moved.targetColor)
        assertEquals(1,moved.armor); assertTrue(moved.teikaSeed)
        assertEquals(frozen.id,(d.state.grid[Position(5,4)] as CellContent.Wood).id)
        assertTrue(d.state.turbineCharged); assertEquals(0,d.state.slideWave); assertTrue(d.state.slidePendingCells.isEmpty()); assertTrue(d.state.hiddenWoodCells.isEmpty())
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.placementsInLevel)
    }

    @Test fun `clearing only a column cannot advance or slide adjacent rows`() {
        val d=fixture(HouseRule.SLIDE); val grid=d.state.grid.toMutableMap()
        for(y in 0..6) grid[Position(7,y)]=CellContent.Wood(colorType=y%3)
        val wood=CellContent.Wood(); grid[Position(3,4)]=wood
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,availablePieces=List(2) { Piece(shape=listOf(Position(0,0))) })
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,7).join() }
        assertTrue(d.state.turbineCharged); assertEquals(0,d.state.slideWave); assertEquals(wood.id,(d.state.grid[Position(3,4)] as CellContent.Wood).id)
    }

    @Test fun `storm charges turbine without moving wood or adding a placement`() {
        val d=fixture(HouseRule.SLIDE); val grid=d.state.grid.toMutableMap()
        for(x in 0..7) grid[Position(x,5)]=CellContent.Wood(colorType=x%3)
        val wood=CellContent.Wood(targetColor=TargetColor.RED); grid[Position(3,4)]=wood
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid)
        val method=GameViewModel::class.java.getDeclaredMethod("detonateCircuitRows",Set::class.java,kotlin.coroutines.Continuation::class.java).apply { isAccessible=true }
        execute(d) { kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Unit> { c -> method.invoke(d.vm,setOf(5),c) } }
        assertEquals(wood.id,(d.state.grid[Position(3,4)] as CellContent.Wood).id)
        assertTrue(d.state.turbineCharged); assertEquals(0,d.state.slideWave); assertEquals(0,d.state.placementsInLevel)
    }

    @Test fun `relay charges on a line survives invalid release and sweeps chosen next piece once`() {
        val d=fixture(HouseRule.RELAY); val grid=d.state.grid.toMutableMap()
        for(x in 0..6) grid[Position(x,7)]=CellContent.Wood(colorType=x%3)
        val red=Position(3,2); val armor=Position(4,3)
        grid[red]=CellContent.Wood(targetColor=TargetColor.RED,teikaSeed=true)
        grid[armor]=CellContent.Wood(armor=1,targetColor=TargetColor.BLUE)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,availablePieces=List(3) { Piece(shape=listOf(Position(0,0))) })
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,7).join() }
        assertTrue(d.state.relayCharged)
        execute(d) { d.vm.placePiece(d.state.availablePieces.last(),-1,3).join() }
        assertTrue(d.state.relayCharged); assertEquals(1,d.state.placementsInLevel)
        execute(d) { d.vm.placePiece(d.state.availablePieces.last(),3,3).join() }
        assertFalse(d.state.relayCharged); assertTrue(d.state.relayPendingFootprint.isEmpty())
        assertTrue(d.state.grid[red] is CellContent.Empty); assertEquals(grid[armor],d.state.grid[armor])
        assertEquals(1,d.state.targetsCollected[TargetColor.RED]); assertNull(d.state.targetsCollected[TargetColor.BLUE])
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(2,d.state.placementsInLevel)
        assertEquals(0,world(d).snapshot().size)
    }

    @Test fun `wide charged Fichona expels distant wood beyond the old twelve block limit`() {
        val d=fixture(HouseRule.RELAY)
        val targets=(1..4).flatMap { y -> listOf(2,4,6,7).map { Position(it,y) } }
        val grid=d.state.grid+targets.associateWith { CellContent.Wood(targetColor=TargetColor.RED,teikaSeed=true) }
        val mega=Piece(shape=(0..3).map { Position(0,it) },isFichona=true)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,relayCharged=true,availablePieces=listOf(mega))
        execute(d) { d.vm.placePiece(mega,1,1).join() }
        assertTrue(targets.all { d.state.grid[it] is CellContent.Empty })
        assertEquals(16,d.state.targetsCollected[TargetColor.RED])
        assertEquals(1,d.state.placementsInLevel); assertEquals(0,d.state.linesClearedInLevel)
        assertFalse(d.state.relayCharged); assertNull(d.state.relayPendingShot)
        assertEquals(0,world(d).snapshot().size)
    }

    @Test fun `charged placement resolves its normal line before relay and recharges once`() {
        val d=fixture(HouseRule.RELAY); val grid=d.state.grid.toMutableMap()
        for(x in 0..6) grid[Position(x,5)]=CellContent.Wood(colorType=x%3)
        val red=Position(7,4); grid[red]=CellContent.Wood(targetColor=TargetColor.RED)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,relayCharged=true,availablePieces=List(2) { Piece(shape=listOf(Position(0,0))) })
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,5).join() }
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.placementsInLevel)
        assertEquals(1,d.state.targetsCollected[TargetColor.RED]); assertTrue(d.state.relayCharged)
        assertTrue(d.state.relayPendingFootprint.isEmpty())
    }

    @Test fun `resuming a committed relay keeps initial protection and never duplicates the sweep`() {
        val d=fixture(HouseRule.RELAY); val red=Position(3,2); val shield=Position(4,3)
        val grid=d.state.grid+(red to CellContent.Wood(targetColor=TargetColor.RED))+(shield to CellContent.Wood())+(Position(3,3) to CellContent.Wood())
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,status=GameStatus.PAUSED,relayPendingFootprint=setOf(Position(3,3)),relayProtectedCells=setOf(shield),relayCharged=true,placementsInLevel=1)
        execute(d) { d.vm.resumeGame(); while(d.state.relayPendingFootprint.isNotEmpty() || d.vm.boardBusy.value) delay(16) }
        assertEquals(1,d.state.targetsCollected[TargetColor.RED]); assertTrue(d.state.grid[shield] is CellContent.Wood)
        assertEquals(1,d.state.placementsInLevel); assertTrue(d.state.relayCharged)
        execute(d) { d.vm.pauseGame(); d.vm.resumeGame() }
        assertEquals(1,d.state.targetsCollected[TargetColor.RED]); assertTrue(d.state.relayProtectedCells.isEmpty())
    }

    @Test fun `paused legacy slide does not execute movement on resume`() {
        val d=fixture(HouseRule.SLIDE); val wood=CellContent.Wood(targetColor=TargetColor.RED)
        field<GameState>(d.vm,"_gameState").value=ReplacementMigration.restore(d.state.copy(grid=d.state.grid+(Position(3,4) to wood),status=GameStatus.PAUSED,slidePendingCells=(0..7).mapTo(HashSet()) { Position(it,5) }))
        execute(d) { d.vm.resumeGame() }
        assertEquals(wood.id,(d.state.grid[Position(3,4)] as CellContent.Wood).id)
        assertEquals(0,d.state.slideWave); assertFalse(d.state.turbineCharged); assertFalse(d.vm.boardBusy.value)
    }

    @Test fun `saved slide is retired while relay still completes once after reload`() {
        for (rule in listOf(HouseRule.SLIDE, HouseRule.RELAY)) {
            val d=fixture(rule)
            val wood=CellContent.Wood(targetColor=TargetColor.RED)
            val source=if(rule==HouseRule.SLIDE) Position(3,4) else Position(3,2)
            val saved=d.state.copy(
                grid=d.state.grid+(source to wood),
                slidePendingCells=if(rule==HouseRule.SLIDE) (0..7).mapTo(HashSet()) { Position(it,5) } else emptySet(),
                relayPendingFootprint=if(rule==HouseRule.RELAY) setOf(Position(3,3)) else emptySet(),
                relayPendingShot=if(rule==HouseRule.RELAY) RelayRules.plan(setOf(Position(3,3)),d.state.grid+(source to wood),emptySet()) else null,
                relayCharged=rule==HouseRule.RELAY
            )
            val prefs=GameViewModel::class.java.getDeclaredField("prefs").apply { isAccessible=true }.get(d.vm) as PreferencesManager
            val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; ignoreUnknownKeys=true; encodeDefaults=true }
            execute(d) {
                prefs.saveGameState(json.encodeToString(GameState.serializer(),saved),level=saved.level)
                field<GameState>(d.vm,"_gameState").value=d.state.copy(score=-1)
                d.vm.continueGame()
                while(d.state.score==-1) delay(16)
                while(d.state.slidePendingCells.isNotEmpty() || d.state.relayPendingFootprint.isNotEmpty() || d.vm.boardBusy.value) delay(16)
            }
            assertEquals(0,d.state.placementsInLevel)
            if(rule==HouseRule.SLIDE) {
                assertEquals(0,d.state.slideWave)
                assertEquals(wood.id,(d.state.grid[Position(3,4)] as CellContent.Wood).id)
                assertEquals(1,d.state.grid.values.filterIsInstance<CellContent.Wood>().count { it.id==wood.id })
            } else {
                assertEquals(1,d.state.targetsCollected[TargetColor.RED])
                assertTrue(d.state.relayCharged)
                assertTrue(d.state.grid[source] is CellContent.Empty)
            }
            execute(d) { d.vm.pauseGame(); d.vm.resumeGame() }
            assertEquals(0,d.state.slideWave)
            if(rule==HouseRule.RELAY) assertEquals(1,d.state.targetsCollected[TargetColor.RED])
        }
    }

    @Test fun `reserve exchange is atomic free and preserves the held block properties`() {
        val d=fixture(HouseRule.RESERVE); val original=d.state.availablePieces.single()
        val held=Piece(shape=listOf(Position(0,0),Position(1,0)),targetMask=listOf(TargetColor.RED,null),crystalMask=listOf(true,false),teikaMask=listOf(false,true))
        field<GameState>(d.vm,"_gameState").value=d.state.copy(reservePieces=listOf(held))
        val grid=d.state.grid
        execute(d) { d.vm.swapReserve(original.id).join() }
        assertEquals(listOf(held),d.state.availablePieces); assertEquals(listOf(original),d.state.reservePieces)
        assertEquals(grid,d.state.grid); assertEquals(0,d.state.placementsInLevel); assertEquals(0,d.state.trayRefillCount)
        assertFalse(d.vm.canSwapReserve(held.id)); assertFalse(d.vm.boardBusy.value)
        execute(d) { d.vm.swapReserve(held.id).join() }
        assertEquals(held,d.state.availablePieces.single())
        execute(d) { d.vm.placePiece(held,0,0).join() }
        assertEquals(original,d.state.reservePieces.single()); assertEquals(1,d.state.trayRefillCount)
        assertTrue(d.vm.canSwapReserve(d.state.availablePieces.first().id))
    }

    @Test fun `reserve is a real escape without declaring a non fitting active piece playable`() {
        val d=fixture(HouseRule.RESERVE); val grid=d.state.grid.toMutableMap().also { g -> g.keys.toList().forEach { g[it]=CellContent.Wood() } }
        grid[Position(3,3)]=CellContent.Empty
        val tooLarge=Piece(shape=listOf(Position(0,0),Position(1,0)))
        val held=Piece(shape=listOf(Position(0,0)))
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,availablePieces=listOf(tooLarge),reservePieces=listOf(held))
        assertFalse(d.vm.canPlacePiece(tooLarge,3,3)); assertNull(d.vm.evaluateDeadEnd())
        execute(d) { d.vm.swapReserve(tooLarge.id).join() }
        assertTrue(d.vm.canPlacePiece(held,3,3))
        field<GameState>(d.vm,"_gameState").value=d.state.copy(availablePieces=listOf(tooLarge),reservePieces=listOf(held))
        assertNotNull(d.vm.evaluateDeadEnd()) // The exchange has already been spent in this tray.
    }

    @Test fun `sleep warning and pause cannot be bypassed by a reserve exchange`() {
        val d=fixture(HouseRule.RESERVE); val p=d.state.availablePieces.single(); val held=p.copy(id="held")
        field<GameState>(d.vm,"_gameState").value=d.state.copy(reservePieces=listOf(held),drowsyMarkedId=p.id)
        execute(d) { d.vm.swapReserve(p.id).join() }
        assertEquals(p,d.state.availablePieces.single()); assertEquals(-1,d.state.reserveSwapRefill)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(drowsyMarkedId=null,status=GameStatus.PAUSED)
        execute(d) { d.vm.swapReserve(p.id).join() }
        assertEquals(p,d.state.availablePieces.single()); assertEquals(0,d.state.placementsInLevel)
    }

    @Test fun `retired routes cannot award points or free uses on placements`() {
        val d=fixture(HouseRule.ROUTES); val cells=(0..2).map { Position(it,1) }
        val piece=Piece(shape=(0..2).map { Position(it,0) })
        field<GameState>(d.vm,"_gameState").value=d.state.copy(routes=listOf(RouteTrack(cells),RouteTrack(cells)),availablePieces=listOf(piece))
        execute(d) { d.vm.placePiece(piece,0,1).join() }
        assertEquals(0,d.state.score); assertNull(d.state.routeFreeCompanionId); assertFalse(d.state.routeSkillEarned)
    }

    @Test fun `legacy route use spends before paid charges without awarding a new use`() {
        val d=fixture(HouseRule.ROUTES); val cells=(0..2).map { Position(it,1) }
        val piece=Piece(shape=(0..2).map { Position(it,0) })
        field<Set<String>>(d.vm,"_claimedCharacterIds").value=setOf("fuego")
        field<String?>(d.vm,"_selectedCompanionId").value="fuego"
        field<CompanionCharges>(d.vm,"_companionCharges").value=CompanionCharges(mapOf("fuego" to 2))
        field<GameState>(d.vm,"_gameState").value=d.state.copy(routeSkillEarned=true,routeFreeCompanionId="fuego",availablePieces=listOf(piece))
        assertEquals("fuego",d.state.routeFreeCompanionId); assertTrue(d.state.routeSkillEarned)
        assertEquals(0,d.state.score); assertEquals(2,d.vm.companionCharges.value.countOf("fuego"))
        assertTrue(d.vm.canCastCompanionAbility("fuego"))
        execute(d) { d.vm.activateCompanionAbility("fuego"); while(d.state.companionPassiveEffect!="fire") delay(16) }
        assertNull(d.state.routeFreeCompanionId); assertEquals(2,d.vm.companionCharges.value.countOf("fuego"))
        field<GameState>(d.vm,"_gameState").value=d.state.copy(companionPassiveEffect=null,companionPassiveCount=0)
        execute(d) { d.vm.activateCompanionAbility("fuego"); while(d.state.companionPassiveEffect!="fire") delay(16) }
        assertEquals(1,d.vm.companionCharges.value.countOf("fuego"))
    }

    @Test fun `a local instant use cannot spend or refund inventory when there is no effect`() {
        val d=fixture(HouseRule.ROUTES)
        field<Set<String>>(d.vm,"_claimedCharacterIds").value=setOf("gloton")
        field<CompanionCharges>(d.vm,"_companionCharges").value=CompanionCharges()
        field<GameState>(d.vm,"_gameState").value=d.state.copy(routeSkillEarned=true,routeFreeCompanionId="gloton")
        assertFalse(d.vm.canCastCompanionAbility("gloton"))
        execute(d) { d.vm.activateCompanionAbility("gloton") }
        assertEquals("gloton",d.state.routeFreeCompanionId); assertEquals(0,d.vm.companionCharges.value.countOf("gloton"))
    }

    @Test fun `saved reserve use and route reward survive reload without another reward`() {
        for(rule in listOf(HouseRule.RESERVE,HouseRule.ROUTES)) {
            val d=fixture(rule); val held=Piece(shape=listOf(Position(0,0)),targetMask=listOf(TargetColor.BLUE))
            val track=RouteTrack(listOf(Position(0,0),Position(1,0),Position(2,0)),setOf(Position(0,0)))
            val saved=d.state.copy(reservePieces=if(rule==HouseRule.RESERVE) listOf(held) else emptyList(),
                reserveSwapRefill=0,routes=if(rule==HouseRule.ROUTES) listOf(track) else emptyList(),
                routeSkillEarned=rule==HouseRule.ROUTES,routeFreeCompanionId=if(rule==HouseRule.ROUTES) "fuego" else null,score=200)
            val prefs=GameViewModel::class.java.getDeclaredField("prefs").apply { isAccessible=true }.get(d.vm) as PreferencesManager
            val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; encodeDefaults=true; ignoreUnknownKeys=true }
            execute(d) {
                prefs.saveGameState(json.encodeToString(GameState.serializer(),saved),level=saved.level)
                field<GameState>(d.vm,"_gameState").value=d.state.copy(score=-1)
                d.vm.continueGame(); while(d.state.score==-1) delay(16)
            }
            assertEquals(200,d.state.score); assertEquals(0,d.state.placementsInLevel)
            if(rule==HouseRule.RESERVE) { assertEquals(held,d.state.reservePieces.single()); assertFalse(d.vm.canSwapReserve(d.state.availablePieces.first().id)) }
            else { assertEquals("fuego",d.state.routeFreeCompanionId); assertTrue(d.state.routes.isEmpty()) }
        }
    }

    @Test fun `surf row clear advances one wave and pushes supported passenger without another turn`() {
        val d=fixture(HouseRule.SURF); val grid=d.state.grid.toMutableMap()
        for(x in 0..6) grid[Position(x,5)]=CellContent.Wood(colorType=x%3)
        val w=world(d); w.setTerrain(grid.filterValues { it is CellContent.Wood }.keys)
        val passenger=w.addBody(TeikaType.TEIKA_1,3.5f,4.5f)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,availablePieces=List(2) { Piece(shape=listOf(Position(0,0))) })
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),7,5).join() }
        assertEquals(1,d.state.surfWave); assertEquals(1,d.state.placementsInLevel)
        assertEquals(1,d.state.linesClearedInLevel)
        val body=requireNotNull(w.getBody(passenger.id))
        assertTrue((body.x-passenger.x)*SurfRules.direction(d.state.level,0)>0f || body.vx*SurfRules.direction(d.state.level,0)>0f)
        assertEquals(1,w.snapshot().size)
    }

    @Test fun `legacy comet core cannot fire when a column charges orbit`() {
        val d=fixture(HouseRule.COMET); val grid=d.state.grid.toMutableMap()
        for(y in 0..6) grid[Position(1,y)]=CellContent.Wood(colorType=y%3)
        val red=Position(3,4); val crystal=Position(4,4); val beyond=Position(5,4)
        grid[red]=CellContent.Wood(targetColor=TargetColor.RED)
        grid[crystal]=CellContent.Wood(armor=1)
        grid[beyond]=CellContent.Wood(targetColor=TargetColor.BLUE)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,cometMark=CometMark(Position(1,4),0),availablePieces=List(2) { Piece(shape=listOf(Position(0,0))) })
        execute(d) { d.vm.placePiece(d.state.availablePieces.first(),1,7).join() }
        assertEquals(grid[red],d.state.grid[red])
        assertNull(d.state.targetsCollected[TargetColor.RED]); assertNull(d.state.targetsCollected[TargetColor.BLUE])
        assertEquals(grid[crystal],d.state.grid[crystal]); assertEquals(grid[beyond],d.state.grid[beyond])
        assertEquals(0,d.state.cometWave); assertTrue(d.state.orbitCharged)
        assertEquals(1,d.state.linesClearedInLevel); assertEquals(1,d.state.placementsInLevel)
    }

    @Test fun `storm surf keeps frozen passenger and advances a single wave for two rows`() {
        val d=fixture(HouseRule.SURF); val grid=d.state.grid.toMutableMap()
        for(y in 5..6) for(x in 0..7) grid[Position(x,y)]=CellContent.Wood(colorType=x%3)
        val w=world(d); val frozen=w.addBody(TeikaType.TEIKA_1,3.5f,4.5f)
        w.setFrozenTurns(frozen.id,2)
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid)
        val method=GameViewModel::class.java.getDeclaredMethod("detonateCircuitRows",Set::class.java,kotlin.coroutines.Continuation::class.java).apply { isAccessible=true }
        execute(d) { kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Unit> { c -> method.invoke(d.vm,setOf(5,6),c) } }
        assertEquals(1,d.state.surfWave); assertEquals(0,d.state.placementsInLevel)
        assertEquals(2,requireNotNull(w.getBody(frozen.id)).frozenTurns)
        assertEquals(frozen.x,requireNotNull(w.getBody(frozen.id)).x,0f)
        assertEquals(0f,requireNotNull(w.getBody(frozen.id)).vx,0f)
    }

    @Test fun `storm line charges orbit without firing a legacy comet`() {
        val d=fixture(HouseRule.COMET); val grid=d.state.grid.toMutableMap()
        for(x in 0..7) grid[Position(x,5)]=CellContent.Wood(colorType=x%3)
        val red=Position(3,3); val ice=Position(3,2); val beyond=Position(3,1)
        grid[red]=CellContent.Wood(targetColor=TargetColor.RED)
        grid[ice]=CellContent.Wood(targetColor=TargetColor.BLUE); grid[beyond]=CellContent.Wood()
        field<GameState>(d.vm,"_gameState").value=d.state.copy(grid=grid,cometMark=CometMark(Position(3,5),3),frozenCells=setOf(ice))
        val method=GameViewModel::class.java.getDeclaredMethod("detonateCircuitRows",Set::class.java,kotlin.coroutines.Continuation::class.java).apply { isAccessible=true }
        execute(d) { kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn<Unit> { c -> method.invoke(d.vm,setOf(5),c) } }
        assertNull(d.state.targetsCollected[TargetColor.RED]); assertNull(d.state.targetsCollected[TargetColor.BLUE])
        assertEquals(grid[ice],d.state.grid[ice]); assertEquals(grid[beyond],d.state.grid[beyond])
        assertEquals(0,d.state.cometWave); assertTrue(d.state.orbitCharged); assertEquals(0,d.state.placementsInLevel)
    }

    @Test fun `repositioning between frames cannot combine disconnected movement into a charged trail`() {
        val d=fixture(HouseRule.TRAIL); val world=world(d)
        val body=world.addBody(TeikaType.TEIKA_1,2.5f,1f)
        runBlocking {
            repeat(100) { i ->
                world.setBodyPosition(body.id,2.5f,1f+(i%3)*2f)
                d.vm.onTeikaPhysicsFrame(.05f)
            }
        }
        assertTrue(d.state.trailCells.isEmpty())
        assertFalse(d.state.events.filterIsInstance<GameEvent.RulePulse>().any { it.kind=="trail_charge" })
    }
}
