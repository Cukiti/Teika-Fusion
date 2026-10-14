package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** La coreografía del COLAPSO ESTELAR (T4+T4 → MEGA): que cierre donde y cuando debe. */
class MegaCollapseTest {

    private val steps = (0..100).map { it / 100f }

    @Test
    fun `la ceremonia no se eterniza`() {
        // Retiene los dos T4 kinematic: más de ~1,2 s y la partida se siente congelada.
        assertTrue(MegaCollapse.CHARGE_MS in 800..1200)
    }

    @Test
    fun `la orbita arranca en su sitio y se cierra en un punto sin abrirse nunca`() {
        assertEquals(2f, MegaCollapse.orbitRadius(0f, 2f), 1e-5f)
        assertEquals(0f, MegaCollapse.orbitRadius(1f, 2f), 1e-5f)
        steps.zipWithNext().forEach { (a, b) ->
            assertTrue(MegaCollapse.orbitRadius(b, 2f) <= MegaCollapse.orbitRadius(a, 2f))
        }
    }

    @Test
    fun `el giro nunca retrocede`() {
        steps.zipWithNext().forEach { (a, b) ->
            assertTrue(MegaCollapse.orbitAngle(b, 0f) >= MegaCollapse.orbitAngle(a, 0f))
        }
    }

    @Test
    fun `la pareja termina en el centro del tablero, de donde nace el MEGA`() {
        assertEquals(1.5f, MegaCollapse.center(0f, 1.5f, 4f), 1e-5f)
        assertEquals(4f, MegaCollapse.center(1f, 1.5f, 4f), 1e-5f)
    }

    @Test
    fun `los cuerpos se ven al empezar y desaparecen al fundirse`() {
        assertEquals(1f, MegaCollapse.bodyAlpha(0f), 1e-5f)
        assertEquals(1f, MegaCollapse.bodyAlpha(0.8f), 1e-5f)
        assertEquals(0f, MegaCollapse.bodyAlpha(1f), 1e-5f)
        steps.forEach { assertTrue(MegaCollapse.bodyScale(it) > 0f) }
        assertTrue(MegaCollapse.bodyScale(1f) < 0.3f)
    }

    @Test
    fun `el vacio entra, se sostiene y cede al fogonazo`() {
        assertEquals(0f, MegaCollapse.scrim(0f), 1e-5f)
        assertEquals(MegaCollapse.SCRIM_MAX, MegaCollapse.scrim(0.5f), 1e-5f)
        assertEquals(0f, MegaCollapse.scrim(1f), 1e-5f)
    }

    @Test
    fun `toda mota ha sido tragada antes de la implosion final`() {
        (0 until MegaCollapse.MOTES).forEach { i ->
            assertEquals(0f, MegaCollapse.moteProgress(0f, i), 1e-5f)
            assertEquals("mota $i", 1f, MegaCollapse.moteProgress(1f, i), 1e-5f)
        }
    }

    @Test
    fun `la singularidad florece del todo al cerrar`() {
        assertEquals(0f, MegaCollapse.singularity(0f), 1e-5f)
        assertEquals(1f, MegaCollapse.singularity(1f), 1e-5f)
    }
}
