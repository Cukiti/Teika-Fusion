package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * P-373 (2026-09-30): los consejos de la pantalla de carga. Se leen de un vistazo mientras la barra
 * avanza, así que tienen que caber en dos líneas y no repetirse dentro de una misma carga.
 */
class LoadingTipsTest {

    @Test
    fun `hay consejos suficientes para una carga larga`() {
        // La espera de cuenta tiene tope de 8 s y cada consejo dura ~2,6 s: con 4 ya no se repite.
        assertTrue(LoadingTips.all.size >= 4)
    }

    @Test
    fun `cada consejo cabe en dos lineas`() {
        LoadingTips.all.forEach {
            assertTrue("Consejo demasiado largo (${it.length}): $it", it.length <= LoadingTips.MAX_CHARS)
            assertTrue("Consejo vacío", it.isNotBlank())
        }
    }

    @Test
    fun `una sesion recorre todos los consejos sin repetir`() {
        val orden = LoadingTips.sessionOrder(Random(7))
        assertEquals(LoadingTips.all.toSet(), orden.toSet())
        assertEquals(LoadingTips.all.size, orden.size)
    }
}
