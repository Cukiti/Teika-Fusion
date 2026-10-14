package com.cukiti.teikafusion.bot

import com.cukiti.teikafusion.model.GameStatus
import com.cukiti.teikafusion.model.TutorialCampaign
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * **LAS SIETE LECCIONES SE PUEDEN TERMINAR** (2026-09-27, auditoría de onboarding).
 *
 * Se juegan contra el motor REAL, como lo haría el jugador que sigue la mano: la ficha guiada a su
 * casilla, la inclinación hacia donde dice la lección y el rayo en la de la habilidad. Nació de un bug:
 * la regla de nacimientos del 2026-09-26 (todo Teika nace T1 salvo 2+ líneas con 2+ bloques-Teika)
 * dejó la lección 2 —que pide un T2 con UN solo bloque— imposible de terminar, y nada lo vio porque
 * ningún test jugaba el tutorial entero (arreglado el mismo día: la lección pide un T1). Un cambio de
 * reglas que rompa otra lección falla aquí.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class TutorialLessonsPlayTest {

    @After fun tearDown() = Dispatchers.resetMain()

    /** Arranca una lección que puede empezar con la bandeja VACÍA (inclinación, habilidad). */
    private fun startLesson(driver: EngineDriver, level: Int): Boolean {
        driver.vm.rng = kotlin.random.Random(level.toLong())
        driver.vm.startGame(level)
        return driver.waitFor(20_000) { driver.state.status == GameStatus.PLAYING && driver.state.level == level }
    }

    /**
     * La lección terminó. `LEVEL_COMPLETE_PENDING` cuenta: cuando el MEGA cierra el nivel, la ventana la
     * abre la UI al acabar la ceremonia (`GameScreen` → `confirmLevelComplete`), y aquí no hay UI.
     */
    private fun completes(driver: EngineDriver) = driver.waitFor(30_000) { finished(driver) }

    private fun finished(driver: EngineDriver) =
        driver.state.status == GameStatus.LEVEL_COMPLETE || driver.state.status == GameStatus.LEVEL_COMPLETE_PENDING

    /** Lecciones de ficha guiada: la ficha a su casilla, y a esperar la ventana de victoria. */
    private fun playGuided(level: Int) {
        val driver = EngineDriver()
        assertTrue("la lección $level no arranca", driver.start(level, seed = level.toLong()))
        val spec = TutorialCampaign.spec(level)!!
        val piece = driver.state.availablePieces.first { it.id == spec.piece!!.id }
        driver.place(piece, spec.targetOrigin!!.x, spec.targetOrigin!!.y)
        assertTrue(
            "la lección $level no se completa: líneas=${driver.state.linesClearedInLevel} " +
                "teikas=${driver.state.teikasCreatedInLevel} estado=${driver.state.status}",
            completes(driver)
        )
    }

    @Test fun `leccion 1 arrastra y completa`() = playGuided(1)
    @Test fun `leccion 2 crea un teika`() = playGuided(2)
    @Test fun `leccion 5 recoge las estrellas`() = playGuided(5)
    @Test fun `leccion 6 deja sitio`() = playGuided(6)

    /** Lecciones de inclinación: se inclina hacia donde dice la lección hasta que se complete. */
    private fun playTilt(level: Int) {
        val driver = EngineDriver()
        assertTrue("la lección $level no arranca", startLesson(driver, level))
        driver.waitFor(3_000) { false }
        var done = false
        for (i in 0 until 30) {
            // Hacia el lado que marca la guía EN ESTE MOMENTO (la 4 cambia de lado en cada etapa) y
            // vuelta a plano: lo que hace la mano del jugador.
            val dir = driver.vm.tutorialTiltDirection.value.takeIf { it != 0 }
                ?: TutorialCampaign.spec(level)!!.initialTiltDirection
            // El sensor da la gravedad de pantalla: un valor POSITIVO empuja a la izquierda (`TiltRules.gravityX`).
            driver.vm.onDeviceTilt(-6f * dir)
            if (driver.waitFor(1_500) { finished(driver) }) { done = true; break }
            driver.vm.onDeviceTilt(0f)
            if (driver.waitFor(1_000) { finished(driver) }) { done = true; break }
        }
        assertTrue(
            "la lección $level no se completa inclinando: teikas=${driver.state.teikasCreatedInLevel} " +
                "estado=${driver.state.status} etapa=${driver.vm.tutorialStage.value} " +
                "cuerpos=${driver.vm.teikaBodies.value.map { "${it.type}@(${"%.1f".format(it.x)},${"%.1f".format(it.y)})k=${it.kinematic}" }}",
            done || completes(driver)
        )
    }

    @Test fun `leccion 3 domina la inclinacion`() = playTilt(3)
    @Test fun `leccion 4 la escalera final`() = playTilt(4)

    @Test
    fun `leccion 7 se completa lanzando la habilidad`() {
        val driver = EngineDriver()
        assertTrue("la lección 7 no arranca", startLesson(driver, 7))
        driver.waitFor(3_000) { false }
        driver.vm.activateCompanionAbility(TutorialCampaign.ABILITY_COMPANION)
        assertTrue(
            "la lección 7 no se completa: líneas=${driver.state.linesClearedInLevel} " +
                "lanzada=${driver.state.tutorialAbilityCast}",
            completes(driver)
        )
        assertEquals(7, driver.state.level)
    }
}
