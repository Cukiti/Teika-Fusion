package com.cukiti.teikafusion.screenshots

import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.cukiti.teikafusion.ui.theme.TeikaFusionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **La vitrina de bloques no cambia de tamaño al elegir otro bloque** (2026-09-24, reporte del
 * usuario). La ventana va centrada, así que si crece aunque sea 6 dp, todo salta bajo el dedo: pasó
 * con la etiqueta de estado junto al nombre, que se ocultaba con el bloque en uso. Se mide la
 * posición de la cabecera («BLOQUES») tras tocar bloques en estados distintos: tiene que ser la misma.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ShowcaseStableSizeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `elegir otro bloque no mueve la ventana`() {
        val sleepLast = com.cukiti.teikafusion.model.LevelMap.chapterLastLevel(1)
        compose.setContent {
            TeikaFusionTheme {
                com.cukiti.teikafusion.ui.BlockShowcaseOverlay(
                    teikoins = 1_200, maxUnlockedLevel = sleepLast + 1,
                    unlockedCosmetics = setOf("fall3d"), activeCosmetic = null, animationsOn = false,
                    onBuy = { true }, onEquip = {}, onUiClick = {}, onDismiss = {},
                )
            }
        }
        compose.waitForIdle()
        fun headerTop() = compose.onNodeWithText("BLOQUES").getBoundsInRoot().top
        val initial = headerTop()   // Básico, EN USO
        // Tuyo (Cerámica), descubierto a la venta (Cristal) y la tarjeta de los próximamente.
        // La tarjeta de los próximamente se llama «+N», y N baja con cada bloque de bioma que se
        // construye (P-251): se calcula en vez de escribirse.
        val soon = com.cukiti.teikafusion.model.BlockDiscovery.slots.count { slot ->
            com.cukiti.teikafusion.ui.BlockSkins.all.none { it.id == slot.skinId }
        }
        // Con los siete construidos (P-251, 2026-09-25) ya no hay tarjeta de próximamente.
        val names = listOf("Cerámica", "Cristal") + (if (soon > 0) listOf("+$soon") else listOf("Vacío"))
        for (name in names) {
            // Con cinco tarjetas el carrusel ya no cabe entero: se desliza hasta la que toca.
            compose.onAllNodes(androidx.compose.ui.test.hasScrollAction())[0]
                .performScrollToNode(androidx.compose.ui.test.hasContentDescription(name, substring = true))
            compose.waitForIdle()
            compose.onAllNodesWithContentDescription(name, substring = true)[0].performClick()
            compose.waitForIdle()
            assertEquals("la ventana se movió al elegir $name", initial, headerTop())
        }
    }
}
