package com.cukiti.teikafusion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-368: el reloj de ambiente avanza a pasos de 1/30 s en pantallas rápidas y libre a 60 Hz. */
class AmbientClockTest {

    private val step = 1_000_000_000L / AMBIENT_FPS

    @Test
    fun `sin paso el tiempo pasa tal cual`() {
        assertEquals(12_345_678L, ambientNs(12_345_678L, 0L))
    }

    @Test
    fun `con paso se redondea hacia abajo al paso anterior`() {
        assertEquals(0L, ambientNs(step - 1, step))
        assertEquals(step, ambientNs(step, step))
        assertEquals(step, ambientNs(step + step / 2, step))
    }

    @Test
    fun `a 120 Hz el reloj cambia una vez cada cuatro fotogramas`() {
        val frameNs = 1_000_000_000L / 120
        val changes = (1..120).count { f -> ambientNs(f * frameNs, step) != ambientNs((f - 1) * frameNs, step) }
        // Un segundo de fotogramas a 120 Hz = ~30 escrituras del reloj, no 120 (29 o 30: 120 fotogramas
        // de 8 333 333 ns se quedan a 40 ns del segundo exacto).
        assertTrue("cambió $changes veces", changes in AMBIENT_FPS - 1..AMBIENT_FPS)
    }
}
