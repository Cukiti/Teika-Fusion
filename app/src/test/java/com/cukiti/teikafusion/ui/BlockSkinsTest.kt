package com.cukiti.teikafusion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fija el contrato del catálogo de skins de bloque. Los `id` se PERSISTEN (DataStore
 * `unlockedCosmetics`/`activeCosmetic` y CloudSaveData v3): renombrar uno desequiparía el skin
 * comprado de todo el mundo, y un guardado con un id retirado (hubo 4: classic/neon/gem/matte)
 * debe caer al Clásico sin explotar. Si alguien rompe cualquiera de las dos cosas, esto lo canta.
 */
class BlockSkinsTest {

    @Test
    fun `los bloques jugables comparten limite sin hueco ni solapamiento`() {
        assertEquals(0f, GAMEPLAY_TILE_GAP_FRACTION)
        assertEquals(0.15f, boundedCellScale(0.15f, isWood = true))
        assertEquals(1f, boundedCellScale(1.14f, isWood = true))
        assertEquals(1.14f, boundedCellScale(1.14f, isWood = false))
    }

    @Test
    fun `los ids del catalogo son unicos y estables`() {
        val ids = BlockSkins.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // Los ids vivos, literales a propósito: cambiar uno rompe los guardados.
        assertTrue("blockblast" in ids)
        assertTrue("cristal" in ids)
        assertTrue("fall3d" in ids)
    }

    @Test
    fun `el skin por defecto existe y es el Clasico`() {
        assertEquals(BlockSkins.classico, BlockSkins.byId(BlockSkins.DEFAULT_ID))
        assertEquals(0, BlockSkins.classico.priceTeikoins)   // el default es gratis
    }

    @Test
    fun `byId cae al Clasico con null y con ids retirados`() {
        assertEquals(BlockSkins.classico, BlockSkins.byId(null))
        // ids que EXISTIERON y se eliminaron del catálogo (2026-06-03) — pueden venir en guardados.
        // «candy» (el Caramelo) se retiró el 2026-09-23 (P-249) y sigue en los comprados como registro.
        listOf("classic", "neon", "gem", "matte", "candy", "loQueSea").forEach {
            assertEquals(BlockSkins.classico, BlockSkins.byId(it))
        }
        assertEquals(BlockSkins.cristal, BlockSkins.byId("cristal"))
    }

    /**
     * LA REGLA MADRE DE LA VITRINA (P-249…251, decisión del usuario 2026-09-23): todos los bloques
     * son CUADRADOS. El Caramelo se retiró por redondeado —«no le da ese toque de cuadrado a las
     * fichas»—; la personalidad de un bloque va en su cara, no en su silueta.
     */
    @Test
    fun `todos los bloques son cuadrados`() {
        BlockSkins.all.forEach { skin ->
            assertTrue("${skin.name} es redondeado (${skin.cornerFraction})", skin.cornerFraction <= 0.14f)
            assertTrue("${skin.name} usa la forma de gelatina redondeada", skin.style != BlockTileStyle.JELLY)
        }
    }

    @Test
    fun `cada skin tiene paleta de 5 colores con triple base-light-dark`() {
        BlockSkins.all.forEach { skin ->
            assertEquals("paleta de ${skin.name}", 5, skin.palette.size)
            skin.palette.forEach { triple -> assertEquals(3, triple.size) }
            assertTrue("descripción vacía en ${skin.name}", skin.description.isNotBlank())
        }
    }

    @Test
    fun `colorsFor no explota con el comodin ni con colores legacy`() {
        BlockSkins.all.forEach { skin ->
            // 0..4 = colores reales de ficha; 7 = comodín (CellView lo dibuja aparte, pero
            // colorsFor debe seguir siendo total); 5-6 = valores legacy de comentarios viejos.
            (0..7).forEach { ct -> assertEquals(3, skin.colorsFor(ct).size) }
        }
    }
}
