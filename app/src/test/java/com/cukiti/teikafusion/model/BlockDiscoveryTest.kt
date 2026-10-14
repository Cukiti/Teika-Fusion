package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockDiscoveryTest {

    private fun lastOf(biome: BiomeType): Int =
        LevelMap.chapterLastLevel(LevelMap.chapters.indexOfFirst { it.biome == biome })

    @Test
    fun `el cristal se descubre justo al terminar el Bosque del Sueno`() {
        val last = lastOf(BiomeType.SLEEP)
        // maxUnlockedLevel = primer nivel sin jugar: jugando el último, aún no; superado, sí.
        assertFalse(BlockDiscovery.isDiscovered("cristal", last))
        assertTrue(BlockDiscovery.isDiscovered("cristal", last + 1))
    }

    @Test
    fun `los bloques que no son de bioma estan siempre a la vista`() {
        assertTrue(BlockDiscovery.isDiscovered("blockblast", 1))
        assertTrue(BlockDiscovery.isDiscovered("fall3d", 1))
    }

    @Test
    fun `terminado el juego se ha descubierto todo`() {
        BlockDiscovery.slots.forEach {
            assertTrue(it.name, BlockDiscovery.isDiscovered(it.skinId, LevelMap.totalLevels + 1))
        }
    }

    @Test
    fun `el mural anuncia el bloque del capitulo que cierra y ningun otro`() {
        assertEquals("cristal", BlockDiscovery.discoveredBy(lastOf(BiomeType.SLEEP))?.skinId)
        assertEquals("vacio", BlockDiscovery.discoveredBy(lastOf(BiomeType.GALACTIC))?.skinId)
        // El Cristal (la caverna) no guarda bloque: el suyo lo guarda el capítulo siguiente.
        assertNull(BlockDiscovery.discoveredBy(lastOf(BiomeType.CRYSTAL)))
        assertNull(BlockDiscovery.discoveredBy(lastOf(BiomeType.SLEEP) - 1))
    }

    @Test
    fun `siete bloques, uno cada dos biomas, en orden de viaje`() {
        assertEquals(7, BlockDiscovery.slots.size)
        val chapters = BlockDiscovery.slots.map { s -> LevelMap.chapters.indexOfFirst { it.biome == s.biome } }
        assertEquals(chapters.sorted(), chapters)
        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13), chapters)
    }

    @Test
    fun `cada bloque construido del catalogo tiene su hueco y su precio`() {
        // El catálogo (ui) y la progresión (model) nombran los mismos ids: si un bloque de bioma se
        // añade al catálogo sin hueco aquí, se vendería sin descubrirse.
        val paidBiomeSkins = com.cukiti.teikafusion.ui.BlockSkins.all
            // Cerámica, Metal, Neón y Dorado no son de bioma: se venden siempre a la vista, sin descubrirse.
            .filter { it.priceTeikoins > 0 && it.id !in setOf("fall3d", "metal", "neon_tubo", "dorado") }
        paidBiomeSkins.forEach { skin ->
            assertTrue("${skin.id} no tiene hueco de bioma", BlockDiscovery.slotFor(skin.id) != null)
        }
    }

    @Test
    fun `los siete bloques de bioma estan construidos y cuestan lo que marca su gama`() {
        // P-251 terminada el 2026-09-25: ningún hueco se queda en «próximamente». Desde P-283 el precio
        // lo pone la GAMA de calidad (Estándar · Premium · Legendario), no el orden del viaje; lo que se
        // conserva es que nunca BAJA a medida que avanzas (el mejor bloque no llega el primero).
        val prices = BlockDiscovery.slots.map { slot ->
            val skin = com.cukiti.teikafusion.ui.BlockSkins.all.firstOrNull { it.id == slot.skinId }
            assertTrue("${slot.skinId} tiene hueco pero no está construido", skin != null)
            skin!!.priceTeikoins
        }
        prices.forEach { assertTrue("Precio $it fuera de las gamas", it in com.cukiti.teikafusion.ui.BlockPriceTier.all) }
        assertEquals("El precio de los bloques de bioma no puede bajar con el viaje", prices.sorted(), prices)
    }

    @Test
    fun `cada bloque de bioma suena con su propia voz`() {
        // El sonido de colocar y de romper es parte de la identidad de cada bloque (P-250). Lee el
        // código de SoundManager: un id sin rama propia caería en el sonido del Clásico sin avisar.
        val src = java.io.File(
            generateSequence(java.io.File("").absoluteFile) { it.parentFile }
                .first { java.io.File(it, "settings.gradle.kts").isFile },
            "app/src/main/java/com/cukiti/teikafusion/ui/SoundManager.kt"
        ).readText()
        val place = src.substringAfter("private val placeSampleId").substringBefore("else")
        val clear = src.substringAfter("private val clearSampleId").substringBefore("else")
        BlockDiscovery.slots.forEach { slot ->
            assertTrue("${slot.skinId} no tiene sonido de colocar", "\"${slot.skinId}\" ->" in place)
            assertTrue("${slot.skinId} no tiene sonido de romper", "\"${slot.skinId}\" ->" in clear)
        }
    }

    @Test
    fun `la pista lleva articulo`() {
        assertEquals("Completa el Glaciar", BlockDiscovery.hint(BlockDiscovery.slotFor("hielo")!!))
    }
}
