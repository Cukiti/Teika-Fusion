package com.cukiti.teikafusion.ui

import com.cukiti.teikafusion.model.TargetColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectiveRevealMotionTest {

    private val stars = listOf(
        TargetColor.RED,
        TargetColor.BLUE,
        TargetColor.PURPLE,
        TargetColor.PINK,
    ).map { ObjectiveRevealTarget.Star(it) }

    @Test
    fun `cada objetivo conserva una firma visual propia`() {
        val profiles = (stars + ObjectiveRevealTarget.Mega).map(::objectiveMotionProfile)

        assertEquals(5, profiles.map { it.kind }.distinct().size)
        assertEquals(5, profiles.map { it.auraStyle }.distinct().size)
        assertEquals(
            5,
            profiles.map { Triple(it.arcLift, it.lateralWave, it.rotationDegrees) }
                .distinct().size,
        )
    }

    @Test
    fun `los aterrizajes forman una cascada corta y ordenada`() {
        val targets = stars + ObjectiveRevealTarget.Mega
        val windows = targets.mapIndexed(::objectiveFlightWindow)
        val starts = windows.map { it.startMs }
        val landings = windows.map { it.landingMs }

        assertEquals(listOf(38, 38, 38, 38), starts.zipWithNext { a, b -> b - a })
        assertTrue(landings.zipWithNext().all { (a, b) -> b > a })
        assertTrue(starts.last() - starts.first() <= 500)
        // P-58 (2026-09-04): 1.680 → 1.287 ms en el caso más pesado (4 estrellas + MEGA). El recorte
        // sale casi entero de la ESPERA del recuadro, no de los vuelos.
        assertEquals(1_287, objectiveRevealDurationMs(targets))
    }

    @Test
    fun `mega comunica mas peso sin alargar el bloqueo`() {
        val starWindow = objectiveFlightWindow(0, stars.first())
        val megaWindow = objectiveFlightWindow(0, ObjectiveRevealTarget.Mega)

        assertTrue(megaWindow.durationMs > starWindow.durationMs)
        // El techo baja con el recorte de P-58: 1,4 s. Antes eran 1,8 —el presupuesto que se dio al
        // meter el recuadro— y el margen que sobraba es justo el que se acaba de gastar. La espera
        // del recuadro ES el tiempo de lectura del objetivo: no dejar que vuelva a crecer.
        assertTrue(objectiveRevealDurationMs(stars + ObjectiveRevealTarget.Mega) <= 1_400)
        assertEquals(ObjectiveAuraStyle.GRAVITY, objectiveMotionProfile(ObjectiveRevealTarget.Mega).auraStyle)
    }

    @Test
    fun `el recuadro entra por la derecha, se para a leer y sale por la izquierda`() {
        val entering = objectiveBannerMotion(0f)
        val braking = objectiveBannerMotion(OBJECTIVE_BANNER_ENTER_MS * 0.85f)
        val holding = objectiveBannerMotion(
            (OBJECTIVE_BANNER_ENTER_MS + OBJECTIVE_BANNER_HOLD_MS / 2).toFloat()
        )
        val leaving = objectiveBannerMotion(
            (OBJECTIVE_BANNER_ENTER_MS + OBJECTIVE_BANNER_HOLD_MS + OBJECTIVE_BANNER_EXIT_MS).toFloat()
        )

        // Nace fuera por la derecha y acaba fuera por la izquierda, sin volver a entrar.
        assertEquals(1f, entering.travel, 0.001f)
        assertEquals(0f, holding.travel, 0.001f)
        assertTrue(leaving.travel < -1f)

        // La entrada frena de verdad: en el último 15% del tiempo le queda menos del 15% del camino.
        assertTrue(braking.travel < 0.15f)

        // Estelas solo en movimiento; luz del filo solo en la espera.
        assertTrue(entering.streak > 0.9f)
        assertEquals(0f, holding.streak, 0.001f)
        assertTrue(leaving.streak > 0.9f)
        assertEquals(0f, entering.edgeLight, 0.001f)
        assertTrue(holding.edgeLight in 0.01f..0.99f)
    }

    @Test
    fun `los iconos no aparecen antes que el recuadro`() {
        assertEquals(0f, objectiveContentEnter(0f), 0.001f)
        assertEquals(0f, objectiveContentEnter(OBJECTIVE_BANNER_ENTER_MS.toFloat()), 0.001f)
        assertEquals(1f, objectiveContentEnter(OBJECTIVE_BANNER_ENTER_MS + 200f), 0.001f)
    }
}
