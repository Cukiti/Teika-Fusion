package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La curva de puntos por línea, fijada por test al rescatarla de `InfiniteScoring` (2026-08-09).
 * No tenía ninguno, y es la única fórmula que puntúa el juego entero: si alguien la toca sin querer,
 * el balance de estrellas de los ~293 niveles se mueve en bloque y nada avisa.
 */
class LineScoringTest {

    @Test
    fun `una linea vale cien`() {
        assertEquals(100, LineScoring.lineScore(1))
    }

    @Test
    fun `romper varias a la vez paga MAS que romperlas sueltas`() {
        assertEquals(400, LineScoring.lineScore(2))    // 2×100×2
        assertEquals(1050, LineScoring.lineScore(3))   // 3×100×3,5
        assertEquals(2000, LineScoring.lineScore(4))   // 4×100×5
        (2..5).forEach { n ->
            assertTrue(
                "Con $n líneas la simultaneidad deja de premiar",
                LineScoring.lineScore(n) > n * LineScoring.lineScore(1)
            )
        }
    }

    @Test
    fun `sin lineas no hay puntos`() {
        assertEquals(0, LineScoring.lineScore(0))
        assertEquals(0, LineScoring.lineScore(-3))
    }

    @Test
    fun `el multiplicador se topa en cinco`() {
        assertEquals(5f, LineScoring.simultaneousMultiplier(4), 0.001f)
        assertEquals(5f, LineScoring.simultaneousMultiplier(9), 0.001f)
        assertEquals(1f, LineScoring.simultaneousMultiplier(1), 0.001f)
    }

    /** 2026-09-26: la línea pura vale el doble antes del multiplicador de simultaneidad. */
    @Test
    fun `la linea pura paga el doble`() {
        assertEquals(200, LineScoring.lineScore(1, pureLines = 1))
        // Dos líneas, una pura: (2 + 1) × 100 × 2.
        assertEquals(600, LineScoring.lineScore(2, pureLines = 1))
        // Nunca más puras que líneas.
        assertEquals(LineScoring.lineScore(1, pureLines = 1), LineScoring.lineScore(1, pureLines = 5))
        assertEquals(LineScoring.lineScore(3), LineScoring.lineScore(3, pureLines = 0))
    }
}
