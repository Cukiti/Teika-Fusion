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
class WorkshopEngineTest {
    @Suppress("UNCHECKED_CAST")
    private fun <T> field(vm: GameViewModel, name: String): MutableStateFlow<T> =
        GameViewModel::class.java.getDeclaredField(name).apply { isAccessible = true }.get(vm) as MutableStateFlow<T>

    private fun fixture(): EngineDriver {
        val driver = EngineDriver()
        field<Boolean>(driver.vm, "_starterPackGranted").value = true
        val level = (BlockBankRules.FIRST_LEVEL..LevelMap.totalLevels).first {
            !ForcedQueue.appliesTo(it) && !FaceDownTray.appliesTo(it) && !HorizonTray.appliesTo(it) && !TrayFive.appliesTo(it)
        }
        field<GameState>(driver.vm, "_gameState").value = GameState(
            level = level, status = GameStatus.PLAYING, biomePrimary = BiomeType.NONE,
            placementsInLevel = 11, blockBank = BlockBank(blocks = 7),
            availablePieces = List(3) { Piece(shape = listOf(Position(0, 0), Position(1, 0))) },
            levelObjective = null,
        )
        driver.vm.setWorkshopActive(true)
        return driver
    }

    private fun <T> execute(driver: EngineDriver, action: suspend () -> T): T {
        val job = CoroutineScope(Dispatchers.Main).async { action() }
        for (i in 0 until 2500) {
            if (job.isCompleted) return runBlocking { job.await() }
            driver.tick(); Thread.sleep(2)
        }
        fail("el taller no termina")
        error("timeout")
    }

    @Test fun `lote consume un turno y no repone la bandeja intermedia ni se duplica`() {
        val d = fixture()
        val before = d.state
        val offer = execute(d) { d.vm.workshopOffer() }
        assertNotNull(offer.plan)
        val built = execute(d) { d.vm.craftWorkshop(offer) }
        assertNotNull(built.plan)
        val after = d.state
        assertEquals(before.placementsInLevel + 1, after.placementsInLevel)
        assertEquals(before.biomeTurnCounter + 1, after.biomeTurnCounter)
        assertEquals(before.trayRefillCount, after.trayRefillCount)
        assertEquals(1, after.availablePieces.size)
        assertTrue(after.availablePieces.single().isFichona)
        assertEquals(after.placementsInLevel + 20, after.blockBank.lockedUntil)
        assertEquals(requireNotNull(built.plan).remainder, after.blockBank.blocks)
        assertFalse(d.vm.boardBusy.value)
        assertNull(execute(d) { d.vm.craftWorkshop(offer) }.plan)
        assertEquals(after.placementsInLevel, d.state.placementsInLevel)
    }

    @Test fun `si el bioma duerme una ficha conserva mano y material pero cobra el turno`() {
        val d = fixture()
        val asleepNext = d.state.copy(biomePrimary = BiomeType.SLEEP,
            sleepIdleTurns = BiomeCycle.strikeAt(d.state.level) - 1,
            drowsyMarkedId = d.state.availablePieces.first().id)
        field<GameState>(d.vm, "_gameState").value = asleepNext
        val offer = execute(d) { d.vm.workshopOffer() }
        assertNotNull(offer.plan)
        val result = execute(d) { d.vm.craftWorkshop(offer) }
        assertNull(result.plan)
        assertEquals(asleepNext.placementsInLevel + 1, d.state.placementsInLevel)
        assertEquals(asleepNext.availablePieces.map { it.id }, d.state.availablePieces.map { it.id })
        assertEquals(asleepNext.blockBank, d.state.blockBank)
        assertTrue(d.state.availablePieces.any { d.state.isPieceAsleep(it.id) })
        assertFalse(d.vm.boardBusy.value)
    }

    @Test fun `propuesta antigua no consume turno ni descarta una bandeja nueva`() {
        val d = fixture()
        val offer = execute(d) { d.vm.workshopOffer() }
        val changed = d.state.copy(availablePieces = listOf(Piece(shape = listOf(Position(0, 0)))))
        field<GameState>(d.vm, "_gameState").value = changed
        assertNull(execute(d) { d.vm.craftWorkshop(offer) }.plan)
        assertEquals(changed.placementsInLevel, d.state.placementsInLevel)
        assertEquals(changed.availablePieces, d.state.availablePieces)
        assertEquals(changed.blockBank, d.state.blockBank)
        assertFalse(d.vm.boardBusy.value)
    }

    @Test fun `reciclar conserva el aviso de Maite sobre la ficha sucesora`() {
        val d = fixture()
        val old = d.state.copy(biomePrimary = BiomeType.SLEEP,
            sleepIdleTurns = BiomeCycle.strikeAt(d.state.level) - 3,
            drowsyMarkedId = d.state.availablePieces.first().id)
        field<GameState>(d.vm, "_gameState").value = old
        val result = execute(d) { d.vm.craftWorkshop(d.vm.workshopOffer()) }
        assertNotNull(result.plan)
        assertEquals(d.state.availablePieces.first().id, d.state.drowsyMarkedId)
        assertNotEquals(old.drowsyMarkedId, d.state.drowsyMarkedId)
    }
    @Test fun `soltar fuera pierde la Fichona y repone sin turno ni reembolso ni duplicado`() {
        val d = fixture()
        execute(d) { d.vm.craftWorkshop(d.vm.workshopOffer()) }
        val before = d.state
        val piece = before.availablePieces.single()
        execute(d) { d.vm.releaseFichona(piece, null, 30f, 40f, 50f).join() }
        assertEquals(before.placementsInLevel, d.state.placementsInLevel)
        assertEquals(before.biomeTurnCounter, d.state.biomeTurnCounter)
        assertEquals(before.blockBank, d.state.blockBank)
        assertEquals(before.trayRefillCount + 1, d.state.trayRefillCount)
        assertFalse(d.state.availablePieces.any { it.id == piece.id })
        assertEquals(1, d.state.events.filterIsInstance<GameEvent.FichonaShattered>().size)
        execute(d) { d.vm.releaseFichona(piece, null, 30f, 40f, 50f).join() }
        assertEquals(before.trayRefillCount + 1, d.state.trayRefillCount)
    }

    @Test fun `cancelar gesto conserva Fichona y materiales`() {
        val d = fixture()
        execute(d) { d.vm.craftWorkshop(d.vm.workshopOffer()) }
        val before = d.state
        d.vm.onDragStart(before.availablePieces.single())
        d.vm.onDragCancel()
        assertEquals(before.availablePieces, d.state.availablePieces)
        assertEquals(before.blockBank, d.state.blockBank)
        assertTrue(d.state.events.none { it is GameEvent.FichonaShattered })
    }

    @Test fun `colocacion valida consume turno normal y no rompe Fichona`() {
        val d = fixture()
        val offer = execute(d) { d.vm.craftWorkshop(d.vm.workshopOffer()) }
        val cells = requireNotNull(offer.plan).reservations.single()
        val before = d.state
        execute(d) { d.vm.releaseFichona(before.availablePieces.single(), Position(cells.minOf { it.x }, cells.minOf { it.y }), 0f, 0f, 40f).join() }
        assertEquals(before.placementsInLevel + 1, d.state.placementsInLevel)
        assertTrue(d.state.events.none { it is GameEvent.FichonaShattered })
        assertFalse(d.vm.boardBusy.value)
    }

}
