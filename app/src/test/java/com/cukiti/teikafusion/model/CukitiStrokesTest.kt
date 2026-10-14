package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * P-374 (2026-09-30): «CUKITI» con UNA línea por letra que llega recta y se pliega. Si un tramo mide
 * cero, la articulación no tiene ángulo; si una letra se sale o pisa a la vecina, la palabra se forma
 * torcida — y es lo primero que ve el jugador al abrir el juego.
 */
class CukitiStrokesTest {

    private val letras = CukitiStrokes.letters

    @Test
    fun `seis letras, una linea cada una`() {
        assertEquals(6, letras.size)
        letras.forEach { assertTrue(it.size >= 2) }
    }

    @Test
    fun `ningun tramo mide cero ni se sale de la palabra`() {
        letras.forEachIndexed { l, letra ->
            for (i in 0 until letra.size) {
                assertTrue("x fuera en letra $l", letra.xs[i] in 0f..CukitiStrokes.width)
                assertTrue("y fuera en letra $l", letra.ys[i] in 0f..1f)
                if (i > 0) {
                    val d = hypot(letra.xs[i] - letra.xs[i - 1], letra.ys[i] - letra.ys[i - 1])
                    assertTrue("tramo nulo en letra $l, punto $i", d > 0.05f)
                }
            }
        }
    }

    @Test
    fun `las letras van de izquierda a derecha sin pisarse`() {
        val rangos = letras.map { it.xs.min()..it.xs.max() }
        rangos.zipWithNext().forEach { (a, b) -> assertTrue("letras solapadas: $a / $b", a.endInclusive < b.start) }
    }

    @Test
    fun `las lineas entran desde lados distintos`() {
        // El primer tramo decide por dónde llega la línea (su cuerpo va detrás, en esa dirección).
        val lados = letras.map { l ->
            val a = Math.toDegrees(atan2((l.ys[1] - l.ys[0]).toDouble(), (l.xs[1] - l.xs[0]).toDouble()))
            ((a + 360 + 45) % 360 / 90).toInt()   // 0 derecha · 1 abajo · 2 izquierda · 3 arriba
        }
        assertTrue("todas entran por el mismo lado: $lados", lados.toSet().size >= 3)
    }
}
