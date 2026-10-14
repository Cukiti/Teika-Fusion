package com.cukiti.teikafusion.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate

/**
 * **Caché de bloques YA CONSTRUIDOS para la ruta inmediata de los skins** (auditoría de fluidez,
 * 2026-09-29).
 *
 * Las celdas del tablero usan la versión `Modifier` de cada skin, que construye sus `Path` y sus
 * degradados una vez por tamaño (`drawWithCache`). Pero todo lo que pinta bloques EN MOVIMIENTO —la
 * rotura de línea (`BatchLineCellShatterVFX`), la ficha fantasma del arrastre, la caída de madera, la
 * huida, el empuje del Rey— llamaba a la versión inmediata (`drawXxxTile`), que lo rehacía TODO en
 * cada llamada: con un skin de bioma, 2-5 `Path` nativos y 2-3 `Brush` por casilla y por fotograma.
 * Una doble línea son 16 casillas: a 120 Hz, miles de objetos por segundo tirados a la basura justo
 * en el momento que más se mira, y el GC los cobra en forma de tirón.
 *
 * Aquí cada bloque se construye UNA vez en el origen, por (estilo, tamaño, redondeo, hueco, variante,
 * colores), y se pinta TRASLADADO a su sitio: mismos píxeles, cero asignaciones en el bucle de dibujo.
 * El alfa sigue siendo un parámetro del dibujo, así que un bloque que se desvanece acierta en caché.
 *
 * Solo se usa desde fase de dibujo de Compose, que corre siempre en el hilo principal: sin cerrojo.
 */
internal fun interface TileRecipe {
    fun draw(scope: DrawScope, alpha: Float)
}

/** Todo lo que cambia el dibujo de un bloque, salvo su posición y su alfa. */
internal data class TileKey(
    val style: BlockTileStyle,
    val size: Size,
    val cornerFraction: Float,
    val gapFraction: Float,
    val variant: Int,
    val base: Color,
    val light: Color,
    val dark: Color,
    /** Parámetro propio de un estilo (el hundido del bisel); 0 en los demás. */
    val extra: Float = 0f,
)

private const val TILE_CACHE_MAX = 160

/** LRU por orden de acceso: 5 colores × 16 variantes × pocos tamaños caben de sobra. */
private val tileCache = object : LinkedHashMap<TileKey, TileRecipe>(TILE_CACHE_MAX, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TileKey, TileRecipe>?): Boolean =
        size > TILE_CACHE_MAX
}

/**
 * Pinta el bloque de [style] en [topLeft] con la receta cacheada. [build] recibe la clave y construye
 * la receta en el ORIGEN; devuelve `null` si el tamaño no deja dibujar nada (no se cachea).
 */
internal fun DrawScope.drawCachedTile(
    style: BlockTileStyle,
    topLeft: Offset,
    size: Size,
    base: Color,
    light: Color,
    dark: Color,
    cornerFraction: Float,
    gapFraction: Float,
    variant: Int,
    alpha: Float,
    extra: Float = 0f,
    build: (TileKey) -> TileRecipe?,
) {
    if (size.width <= 0f || size.height <= 0f) return
    val key = TileKey(style, size, cornerFraction, gapFraction, variant, base, light, dark, extra)
    val recipe = tileCache[key] ?: (build(key) ?: return).also { tileCache[key] = it }
    translate(topLeft.x, topLeft.y) { recipe.draw(this, alpha) }
}
