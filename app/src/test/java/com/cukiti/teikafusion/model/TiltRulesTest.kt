package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * INCLINACIÓN (fix `nivelbug`): el eje de pantalla y la curva de respuesta. El criterio del brief
 * —inclinar 25° tiene que EMPUJAR de verdad— se comprueba aquí sobre la gravedad resultante.
 */
class TiltRulesTest {

    private val MAX = 12f   // espeja TeikaPhysicsWorld.TILT_GRAVITY_MAX

    @Test
    fun `en rotacion natural el eje de pantalla es el del dispositivo`() {
        assertEquals(3f, TiltRules.screenX(3f, -7f, 0), 1e-4f)
    }

    @Test
    fun `el remapeo gira el eje con el display`() {
        assertEquals(7f, TiltRules.screenX(3f, -7f, 90), 1e-4f)
        assertEquals(-3f, TiltRules.screenX(3f, -7f, 180), 1e-4f)
        assertEquals(-7f, TiltRules.screenX(3f, -7f, 270), 1e-4f)
    }

    @Test
    fun `una rotacion desconocida cae en la natural`() {
        assertEquals(3f, TiltRules.screenX(3f, -7f, 45), 1e-4f)
    }

    @Test
    fun `mesa plana no produce deriva`() {
        assertEquals(0f, TiltRules.gravityX(0f, MAX), 1e-6f)
        // Ruido dentro de la zona muerta (≈1°): sigue siendo cero.
        assertEquals(0f, TiltRules.gravityX(0.17f, MAX), 1e-6f)
        assertEquals(0f, TiltRules.gravityX(-0.17f, MAX), 1e-6f)
    }

    @Test
    fun `el signo invierte el sensor (sensor +X = rodar a la izquierda)`() {
        assertTrue(TiltRules.gravityX(5f, MAX) < 0f)
        assertTrue(TiltRules.gravityX(-5f, MAX) > 0f)
    }

    @Test
    fun `inclinar 25 grados empuja lo justo para rodar, no para volar`() {
        // sin(25°) ≈ 0,4226 → g_x del sensor ≈ 4,15 m/s².
        val g = kotlin.math.abs(TiltRules.gravityX(9.81f * 0.4226f, MAX))
        // Suelo: tiene que superar el rozamiento estático (TILT_STICTION_G = 2,5) o inclinar no
        // movería nada — el bug `nivelbug` original.
        assertTrue("gravedad lateral demasiado floja a 25°: $g", g >= 3f)
        // Techo (playtest 2026-07-27, «se mueven muy rápido»): un ladeo cómodo NO puede acercarse a
        // la gravedad vertical del mundo (26 celdas/s²). Con la curva vieja de 0,75 daba 9,0.
        assertTrue("gravedad lateral desbocada a 25°: $g", g <= 6f)
    }

    @Test
    fun `la respuesta es proporcional al angulo`() {
        // Con RESPONSE_EXP = 1 el doble de componente horizontal da el doble de empuje: es lo que
        // hace que el gesto se sienta lógico. La zona muerta, al reescalarse, castiga un pelín más
        // al ángulo pequeño, así que el cociente queda JUSTO por encima de 2 — nunca por debajo, y
        // nunca cerca del 2,7 que daba la curva exagerada de 0,75.
        val g15 = kotlin.math.abs(TiltRules.gravityX(9.81f * 0.2588f, MAX))   // sin 15°
        val g31 = kotlin.math.abs(TiltRules.gravityX(9.81f * 0.5176f, MAX))   // sin 31° = 2×sin 15°
        val ratio = g31 / g15
        assertTrue("la respuesta no es proporcional: ×$ratio", ratio in 2.0f..2.3f)
    }

    @Test
    fun `no hay salto al cruzar la zona muerta`() {
        val justInside = kotlin.math.abs(TiltRules.gravityX(9.81f * (TiltRules.DEADZONE - 1e-4f), MAX))
        val justOutside = kotlin.math.abs(TiltRules.gravityX(9.81f * (TiltRules.DEADZONE + 1e-3f), MAX))
        assertEquals(0f, justInside, 1e-6f)
        assertTrue("el umbral no puede dar un salto: $justOutside", justOutside < 1.5f)
    }

    @Test
    fun `a tope no se pasa del maximo`() {
        assertEquals(-MAX, TiltRules.gravityX(9.81f, MAX), 1e-3f)
        assertEquals(MAX, TiltRules.gravityX(-40f, MAX), 1e-3f)
    }
}
