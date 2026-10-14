package com.cukiti.teikafusion.ui

import com.airbnb.lottie.LottieCompositionFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import com.cukiti.teikafusion.R

/**
 * La flecha de «mejora lista» ([UpgradeArrowBadge]) es un Lottie escrito a mano por
 * `tools/lottie/gen_upgrade_arrow.py`, no exportado de una herramienta. Este test lo carga con el
 * parser REAL de lottie-android: si un campo se escribe mal o usa algo que Android no pinta (mate,
 * degradado con opacidad, estrella), la composición llega nula o con avisos.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpgradeArrowLottieTest {

    @Test
    fun `la flecha de mejora se carga entera y sin avisos`() {
        val ctx = RuntimeEnvironment.getApplication()
        val result = LottieCompositionFactory.fromRawResSync(ctx, R.raw.ic_upgrade_arrow)
        val comp = result.value
        assertNotNull("No se pudo leer: ${result.exception}", comp)
        comp!!
        assertTrue("Avisos de lottie-android: ${comp.warnings}", comp.warnings.isEmpty())
        assertEquals(60f, comp.frameRate)
        // El reposo (fotograma 0) es lo que se ve con «Reducir efectos»: el bucle dura 1,6 s.
        assertEquals(96f, comp.durationFrames, 0.05f)
    }
}
