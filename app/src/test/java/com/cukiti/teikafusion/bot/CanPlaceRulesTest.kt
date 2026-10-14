package com.cukiti.teikafusion.bot

import com.cukiti.teikafusion.model.ForcedQueue
import com.cukiti.teikafusion.model.LevelMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **P-245** (2026-09-24): `canPlacePiece` —la pregunta del fantasma y de cualquier llamador nuevo—
 * tiene que decir lo mismo que `placePiece`. Una ficha que la COLA FORZADA aún no deja jugar no cabe
 * en ninguna parte, aunque el hueco esté libre. Se prueba contra el motor real (el del bot).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CanPlaceRulesTest {

    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `la cola forzada tambien cierra canPlacePiece`() {
        val level = ((LevelMap.introLevels + 1)..LevelMap.totalLevels).first { ForcedQueue.appliesTo(it) }
        val driver = EngineDriver()
        assertTrue("el nivel $level no arranca", driver.start(level))
        val s = driver.state
        val free = ForcedQueue.freeSlotsFor(level)
        assertTrue("la bandeja debe tener fichas fuera de la cola", s.availablePieces.size > free)

        val cells = (0 until s.gridH).flatMap { y -> (0 until s.gridW).map { x -> x to y } }
        val locked = s.availablePieces[free]
        assertFalse(
            "la ficha ${free + 1} de la cola se promete jugable",
            cells.any { (x, y) -> driver.vm.canPlacePiece(locked, x, y) }
        )
        // La cabeza de la cola sí tiene sitio: la apertura garantiza que las fichas quepan.
        val head = s.availablePieces[0]
        assertTrue(cells.any { (x, y) -> driver.vm.canPlacePiece(head, x, y) })
    }
}
