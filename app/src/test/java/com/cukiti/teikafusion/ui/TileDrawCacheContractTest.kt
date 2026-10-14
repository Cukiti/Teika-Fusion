package com.cukiti.teikafusion.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Los bloques en movimiento no construyen nada al pintarse** (auditoría de fluidez, 2026-09-29).
 *
 * La rotura de línea, la ficha fantasma, la caída de madera y demás efectos pintan los bloques con la
 * ruta INMEDIATA de cada skin (`drawXxxTile`), muchas veces por fotograma. Esa ruta rehacía sus `Path`
 * nativos y sus degradados en cada llamada —miles de objetos por segundo al romper una doble línea— y
 * el GC los cobraba en forma de tirón. Ahora pasa por [drawCachedTile]: se construye una vez y se
 * pinta trasladada.
 *
 * Este test lee el código: si un skin nuevo (o un retoque) vuelve a construir en la ruta inmediata en
 * vez de pedir su receta a la caché, falla. La construcción va en la receta (`xxxRecipe`), que solo
 * corre cuando la caché no la tiene.
 */
class TileDrawCacheContractTest {

    private val uiDir: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, UI).isDirectory) dir = dir.parentFile
        File(requireNotNull(dir) { "No encuentro la raíz del repo" }, UI)
    }

    /** Del `fun DrawScope.drawXxxTile(` a la siguiente declaración de primer nivel. */
    private fun immediateBody(file: String, fn: String): String {
        val src = File(uiDir, file).readText()
        val start = src.indexOf("fun DrawScope.$fn(")
        assertTrue("$file ya no declara $fn", start >= 0)
        val ends = listOf("\nfun ", "\nprivate fun ", "\ninternal fun ", "\n/**", "\n@Composable")
            .map { src.indexOf(it, start + 1) }.filter { it > 0 }
        return src.substring(start, ends.minOrNull() ?: src.length)
    }

    @Test
    fun `la ruta inmediata de cada skin pinta desde la cache`() {
        val immediate = mapOf(
            "PieceView.kt" to listOf("drawBlockTile", "drawExtrudedTile", "drawBevelTile"),
            "CrystalBlock.kt" to listOf("drawFacetedTile"),
            "WoodBlock.kt" to listOf("drawWoodTile"),
            "LavaBlock.kt" to listOf("drawLavaTile"),
            "IceBlock.kt" to listOf("drawIceTile"),
            "SpectreBlock.kt" to listOf("drawSpectreTile"),
            "ConstellationBlock.kt" to listOf("drawConstellationTile"),
            "VoidBlock.kt" to listOf("drawVoidTile"),
            "MetalBlock.kt" to listOf("drawMetalTile"),
            "NeonBlock.kt" to listOf("drawNeonTile"),
            "GoldBlock.kt" to listOf("drawGoldTile"),
        )
        for ((file, fns) in immediate) for (fn in fns) {
            val body = immediateBody(file, fn)
            assertTrue("$fn debe pintar con drawCachedTile", body.contains("drawCachedTile("))
            assertFalse("$fn construye un Path en cada llamada", body.contains("Path()"))
            assertFalse("$fn construye un Brush en cada llamada", body.contains("Brush."))
        }
    }

    @Test
    fun `todos los estilos de bloque tienen su ruta inmediata cubierta`() {
        // Un estilo nuevo en BlockTileStyle obliga a revisar este test (y a darle su receta).
        assertEquals(13, BlockTileStyle.entries.size)
    }

    @Test
    fun `el halo de la ficha fantasma se desenfoca una sola vez`() {
        val src = File(uiDir, "GhostPreviewLayer.kt").readText()
        // El único BlurMaskFilter vive en la máscara que se construye con `remember`, no en el dibujo.
        assertEquals(1, Regex("BlurMaskFilter\\(").findAll(src).count())
        val build = src.indexOf("private fun buildGhostGlow(")
        assertTrue(build >= 0 && src.indexOf("BlurMaskFilter(") > build)
    }

    private companion object {
        const val UI = "app/src/main/java/com/cukiti/teikafusion/ui"
    }
}
