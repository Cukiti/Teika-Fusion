package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-369: cuándo el juego va a tirones SOSTENIDOS (ver [JankWatch]). */
class JankWatchTest {

    /**
     * Alimenta [seconds] segundos a [fps] y devuelve cuántas veces saltó. Ritmos EXACTOS en ns (25, 50,
     * 100 fps) para que cada ventana cierre justo al segundo y el test no dependa de redondeos.
     */
    private fun JankWatch.feed(fps: Int, seconds: Int): Int {
        val frameNs = 1_000_000_000L / fps
        return (1..fps * seconds).count { onFrame(frameNs) }
    }

    @Test
    fun `a buen ritmo no salta nunca`() {
        val w = JankWatch()
        assertEquals(0, w.feed(50, 60))
        assertEquals(0, w.feed(100, 60))
    }

    @Test
    fun `a tirones sostenidos salta una sola vez`() {
        val w = JankWatch()
        // 3 s de calentamiento + 6 ventanas malas = salta en el segundo 9.
        assertEquals(1, w.feed(25, 12))
        // Ya avisó: no vuelve a saltar aunque siga yendo mal.
        assertEquals(0, w.feed(25, 30))
    }

    @Test
    fun `el calentamiento no cuenta`() {
        val w = JankWatch()
        // Solo 8 s malos: 3 de calentamiento + 5 malas → aún no.
        assertEquals(0, w.feed(25, 8))
    }

    @Test
    fun `un pico suelto no basta`() {
        val w = JankWatch()
        w.feed(50, 4)
        repeat(10) {
            assertEquals(0, w.feed(25, 3))   // 3 s malos
            assertEquals(0, w.feed(50, 2))   // y se recupera: la racha se corta
        }
    }

    @Test
    fun `un hueco largo no es un tiron`() {
        val w = JankWatch()
        w.feed(50, 4)
        // Volver de segundo plano: 30 fotogramas de 2 s. Ninguno cuenta ni cierra ventana mala.
        repeat(30) { assertFalse(w.onFrame(2_000_000_000L)) }
        assertEquals(0, w.feed(50, 10))
    }

    @Test
    fun `salta tras seis ventanas malas seguidas`() {
        val w = JankWatch()
        w.feed(50, 4)                  // calentamiento cumplido, ritmo bueno
        assertEquals(0, w.feed(25, 5)) // 5 malas
        assertTrue((1..25).any { w.onFrame(1_000_000_000L / 25) }) // la 6ª
    }
}
