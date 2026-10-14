package com.cukiti.teikafusion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **TODO BLOQUE TIENE TARJETA EN LA VITRINA** (2026-09-27).
 *
 * El Metal (P-285) existió dos días como skin completo —paleta, dibujo, rotura, sonido y precio— sin
 * aparecer en la vitrina: `showcaseEntries` solo pintaba los fijos (Básico, Cerámica) y los siete de
 * bioma, y el Metal no es ninguna de las dos cosas. Un bloque sin tarjeta no se puede comprar ni
 * equipar, así que para el jugador no existe.
 */
class ShowcaseCoverageTest {

    @Test
    fun `cada bloque de BlockSkins tiene exactamente una tarjeta`() {
        val ids = showcaseEntries(maxUnlockedLevel = 1, owned = emptySet(), equippedId = BlockSkins.DEFAULT_ID)
            .map { it.id }
        BlockSkins.all.forEach { skin ->
            assertTrue("«${skin.name}» no sale en la vitrina", skin.id in ids)
        }
        assertEquals("tarjetas repetidas: $ids", ids.size, ids.toSet().size)
    }

    @Test
    fun `el Metal se vende desde el principio porque no es de bioma`() {
        val metal = showcaseEntries(maxUnlockedLevel = 1, owned = emptySet(), equippedId = BlockSkins.DEFAULT_ID)
            .first { it.id == BlockSkins.metal.id }
        assertEquals(ShowcaseState.FOR_SALE, metal.state)
    }

    /** P-408: el Neón y el Dorado tampoco son de bioma — a la venta desde el nivel 1. */
    @Test
    fun `el Neon y el Dorado se venden desde el principio`() {
        val entries = showcaseEntries(maxUnlockedLevel = 1, owned = emptySet(), equippedId = BlockSkins.DEFAULT_ID)
        for (skin in listOf(BlockSkins.neon, BlockSkins.dorado)) {
            assertEquals(skin.name, ShowcaseState.FOR_SALE, entries.first { it.id == skin.id }.state)
        }
        assertEquals(4000, BlockSkins.neon.priceTeikoins)
        assertEquals(6000, BlockSkins.dorado.priceTeikoins)
    }
}
