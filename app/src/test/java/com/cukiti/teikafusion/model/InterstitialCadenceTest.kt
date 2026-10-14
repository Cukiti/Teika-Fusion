package com.cukiti.teikafusion.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Los tres frenos del intersticial, cada uno probado por separado y comprobando que **basta uno**
 * para bloquearlo. La regla es puro cálculo: aquí es donde se verifica, no en el emulador.
 */
class InterstitialCadenceTest {

    private val now = 1_000_000L

    @Test
    fun `con los tres frenos satisfechos, toca anuncio`() {
        assertTrue(
            InterstitialCadence.isDue(
                breaks = InterstitialCadence.BREAKS_PER_AD,
                level = InterstitialCadence.MIN_LEVEL,
                nowMs = now,
                lastFullScreenAdAtMs = now - InterstitialCadence.MIN_GAP_MS
            )
        )
    }

    @Test
    fun `los primeros niveles quedan limpios aunque sobren cortes`() {
        assertFalse(
            InterstitialCadence.isDue(
                breaks = 99,
                level = InterstitialCadence.MIN_LEVEL - 1,
                nowMs = now,
                lastFullScreenAdAtMs = 0L
            )
        )
    }

    @Test
    fun `no salta antes de acumular los cortes`() {
        assertFalse(
            InterstitialCadence.isDue(
                breaks = InterstitialCadence.BREAKS_PER_AD - 1,
                level = 50,
                nowMs = now,
                lastFullScreenAdAtMs = 0L
            )
        )
    }

    /** El caso que motivó el hueco: ver el vídeo de revivir y perder acto seguido. */
    @Test
    fun `un anuncio reciente a pantalla completa bloquea el intersticial`() {
        assertFalse(
            InterstitialCadence.isDue(
                breaks = 99,
                level = 50,
                nowMs = now,
                lastFullScreenAdAtMs = now - (InterstitialCadence.MIN_GAP_MS - 1)
            )
        )
    }

    @Test
    fun `sin anuncios previos en la sesion el hueco no bloquea`() {
        assertTrue(
            InterstitialCadence.isDue(
                breaks = InterstitialCadence.BREAKS_PER_AD,
                level = 50,
                nowMs = now,
                lastFullScreenAdAtMs = 0L
            )
        )
    }

    /**
     * Los cinco primeros niveles limpios es una DECISIÓN de producto (2026-08-09), no un detalle de
     * implementación: si alguien baja este umbral, que sea a sabiendas.
     */
    @Test
    fun `los topes son los acordados`() {
        assertTrue(InterstitialCadence.MIN_LEVEL == 6)
        assertTrue(InterstitialCadence.BREAKS_PER_AD == 3)
        assertTrue(InterstitialCadence.MIN_GAP_MS == 90_000L)
    }
}
