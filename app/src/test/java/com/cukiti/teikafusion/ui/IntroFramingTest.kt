package com.cukiti.teikafusion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El encaje de la ilustración de la intro ([IntroFraming]): el título «TEIKA FUSIÓN» dibujado
 * (x 120…861 de 941 px) tiene que verse ENTERO en cualquier pantalla, con el zoom lento incluido.
 */
class IntroFramingTest {

    private val imgW = 941f
    private val imgH = 1672f

    /** Ventana visible en px de imagen tras el zoom máximo (1,03) centrado en la pantalla. */
    private fun visibleWindow(dstW: Float, dstH: Float): Pair<Float, Float> {
        val s = IntroFraming.scale(imgW, imgH, dstW, dstH)
        val left = IntroFraming.offsetX(imgW * s, dstW)
        val center = (dstW / 2f - left) / s
        val half = dstW / 2f / (s * 1.03f)
        return (center - half) to (center + half)
    }

    @Test
    fun `el titulo entero cabe en moviles alargados y en pantallas anchas`() {
        listOf(
            1080f to 2340f, // Galaxy A15, 9:19,5
            1080f to 2400f, // 9:20
            1440f to 3200f, // 9:20 QHD
            1080f to 2640f, // 9:22, muy alargado
            1080f to 1920f, // 9:16
            1600f to 2560f, // tablet 10:16
        ).forEach { (w, h) ->
            val (l, r) = visibleWindow(w, h)
            assertTrue("${w}x$h: el título se corta por la izquierda (ventana desde $l)", l <= 120f)
            assertTrue("${w}x$h: el título se corta por la derecha (ventana hasta $r)", r >= 861f)
        }
    }

    @Test
    fun `nunca se ve nada fuera de la imagen por los lados`() {
        listOf(1080f to 2340f, 1440f to 3200f, 1080f to 1920f, 1600f to 2560f).forEach { (w, h) ->
            val s = IntroFraming.scale(imgW, imgH, w, h)
            val x = IntroFraming.offsetX(imgW * s, w)
            assertTrue("${w}x$h: hueco a la izquierda", x <= 0.01f)
            assertTrue("${w}x$h: hueco a la derecha", x + imgW * s >= w - 0.01f)
        }
    }

    @Test
    fun `en 9 16 se comporta como Crop`() {
        assertEquals(1080f / imgW, IntroFraming.scale(imgW, imgH, 1080f, 1920f), 0.01f)
    }

    @Test
    fun `en un movil alargado amplia menos que Crop y deja franjas cortas`() {
        val s = IntroFraming.scale(imgW, imgH, 1080f, 2340f)
        assertTrue("sigue ampliando como Crop (×$s)", s < 2340f / imgH)
        val r = IntroFraming.imageRect(1080f, 2340f)
        assertTrue("franja de arriba demasiado alta: ${r.top} px", r.top in 0f..0.05f * 2340f)
    }
}
