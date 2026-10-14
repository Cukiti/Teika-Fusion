package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-402: el reloj de la tira de la demo (pasadas, fundido, latido del eco), fuera de la UI. */
class DemoReelTest {

    private fun tl(vararg ms: Int, echoAt: Int = -1) = DemoTimeline(
        ms.mapIndexed { i, m -> DemoFrame(emptyList(), m, if (i == echoAt) listOf(DemoFx.Echo) else emptyList()) }
    )

    // Pasada 1: 1000 ms con los aros de 400 a 600; pasada 2: 500 ms. Fundido de 200.
    private val reel = DemoReel(listOf(tl(400, 200, 400, echoAt = 1), tl(500)), gapMs = 200)

    @Test fun `el ciclo suma guiones y fundidos`() {
        assertEquals(1000L + 200 + 500 + 200, reel.cycleMs)
    }

    @Test fun `passAt recorre las pasadas y da la vuelta`() {
        assertEquals(0, reel.passAt(0))
        assertEquals(0, reel.passAt(1199))      // fundido de la pasada 1
        assertEquals(1, reel.passAt(1200))
        assertEquals(1, reel.passAt(1899))
        assertEquals(0, reel.passAt(1900))      // vuelta al principio
        assertEquals(1, reel.passAt(1900 + 1300))
    }

    @Test fun `el fundido baja a cero y vuelve`() {
        assertEquals(1f, reel.fade(500, 0), 0f)
        assertEquals(1f, reel.fade(1000, 0), 0f)
        assertEquals(0.5f, reel.fade(1050, 0), 1e-4f)
        assertEquals(0f, reel.fade(1100, 0), 1e-4f)
        assertEquals(0.5f, reel.fade(1150, 0), 1e-4f)
    }

    @Test fun `durante el fundido la escena se queda en la foto final y luego en la inicial`() {
        assertEquals(700, reel.sceneTime(700, 0))
        assertEquals(999, reel.sceneTime(1050, 0))
        assertEquals(0, reel.sceneTime(1150, 0))
        assertEquals(300, reel.sceneTime(1500, 1))
    }

    @Test fun `el eco late solo con sus aros`() {
        assertEquals(0f, reel.echoBeat(399), 0f)
        assertEquals(1f, reel.echoBeat(500), 1e-4f)
        assertEquals(0f, reel.echoBeat(600), 0f)
        assertEquals(1f, reel.echoBeat(1900 + 500), 1e-4f)
        // Una tira de una sola pasada no tiene contador que latir.
        assertEquals(0f, DemoReel(listOf(tl(400, 200, 400, echoAt = 1))).echoBeat(500), 0f)
    }

    @Test fun `la tira real del nivel 4 con eco late dentro de su primera pasada`() {
        val real = abilityDemoReel("fuego", 4)!!
        assertEquals(4, real.size)
        assertTrue(real.echoStartMs in 1 until real.passes.first().totalMs)
        assertTrue(real.echoBeat(real.echoStartMs + real.echoLenMs / 2) > 0.9f)
    }
}
