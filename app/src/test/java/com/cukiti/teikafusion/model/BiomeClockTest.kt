package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiomeClockTest {

    private val ejecuta = BiomeRhythm.EJECUTA   // 3

    @Test
    fun `el ejemplo del usuario, turno a turno`() {
        var idle = 0
        idle = BiomeClock.advance(idle, lineCleared = false, executeAt = ejecuta); assertEquals(1, idle) // se prende
        idle = BiomeClock.advance(idle, lineCleared = true, executeAt = ejecuta); assertEquals(1, idle)  // rompe: sigue encendido
        idle = BiomeClock.advance(idle, lineCleared = false, executeAt = ejecuta); assertEquals(2, idle) // continúa: hierve
        assertTrue(BiomeClock.isExecutionTurn(idle, ejecuta))                                            // el siguiente ejecuta…
        assertEquals(0, BiomeClock.advance(idle, lineCleared = true, executeAt = ejecuta))               // …salvo que rompa: se apaga
        assertEquals(3, BiomeClock.advance(idle, lineCleared = false, executeAt = ejecuta))              // y si no, cae
    }

    @Test
    fun `romper en un aviso ni avanza ni apaga`() {
        assertEquals(0, BiomeClock.onLineCleared(0, ejecuta))
        assertEquals(1, BiomeClock.onLineCleared(1, ejecuta))
        assertFalse(BiomeClock.isExecutionTurn(1, ejecuta))
    }

    @Test
    fun `romper en el turno de ejecutar cancela`() {
        assertEquals(0, BiomeClock.onLineCleared(2, ejecuta))
        // Un reloj que se pasó (esperando presa) también cancela.
        assertEquals(0, BiomeClock.onLineCleared(5, ejecuta))
    }

    @Test
    fun `sin linea siempre avanza`() {
        (0..4).forEach { assertEquals(it + 1, BiomeClock.advance(it, lineCleared = false, executeAt = ejecuta)) }
    }
}
