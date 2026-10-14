package com.cukiti.teikafusion.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp

/**
 * **El bloque CRISTAL** (P-251, 2026-09-23): una gema tallada con la silueta CUADRADA del Clásico
 * (la regla de la vitrina: la forma no se toca, la personalidad va en la cara).
 *
 * - **Talla:** el marco se parte en OCHO facetas —cada lado en dos mitades de luz distinta, con la
 *   luz arriba a la izquierda— alrededor de una MESA central con un reflejo en diagonal. Es lo que la
 *   distingue del bisel del Clásico, que tiene cuatro caras lisas.
 * - **Sin destello** (2026-09-30, decisión del usuario: «quiero quitar ese brillo… también de los bloques»): hasta entonces una banda de luz cruzaba cada gema y
 *   recorría el tablero en onda. La gema es ahora estática.
 *
 * Todo lo que no es tiempo se construye UNA vez por tamaño (`drawWithCache`): cero asignaciones por
 * frame, como el resto de la capa del tablero.
 */
private const val FACET_INSET = 0.27f
/**
 * ¿Está avanzando el reloj compartido? Lo mantiene [BiomeClockDriver]. Con el reloj parado (pantallas
 * sin tablero, «reducir efectos») el destello no se dibuja: un reloj congelado dejaría una banda de
 * luz clavada a media gema.
 */
internal val biomeClockRunning = mutableStateOf(false)

private class FacetGeometry(
    val clip: Path,
    val facets: Array<Path>,
    val table: Offset, val tableSize: Size, val tableCorner: CornerRadius,
    val tableShine: Path,
    val oL: Float, val oT: Float, val bw: Float, val bh: Float, val corner: CornerRadius,
) {
    /** Arista de la mesa: se construye una vez, no en cada celda y fotograma. */
    val edgeStroke = Stroke(width = bw * 0.03f)
}

private fun quad(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float) =
    Path().apply { moveTo(ax, ay); lineTo(bx, by); lineTo(cx, cy); lineTo(dx, dy); close() }

private fun facetGeometry(topLeft: Offset, size: Size, cornerFraction: Float, gapFraction: Float): FacetGeometry? {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return null
    val gap = minOf(w, h) * gapFraction
    val oL = topLeft.x + gap; val oT = topLeft.y + gap
    val bw = w - gap * 2f; val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val oR = oL + bw; val oB = oT + bh
    val blockMin = minOf(bw, bh)
    val r = blockMin * cornerFraction
    val corner = CornerRadius(r, r)
    val inset = blockMin * FACET_INSET
    val iL = oL + inset; val iT = oT + inset; val iR = oR - inset; val iB = oB - inset
    val mx = (oL + oR) / 2f; val my = (oT + oB) / 2f
    // Orden de [facets] = orden de [facetColors]: arriba-izq, arriba-der, izq-arriba, izq-abajo,
    // der-arriba, der-abajo, abajo-izq, abajo-der.
    val facets = arrayOf(
        quad(oL, oT, mx, oT, mx, iT, iL, iT),
        quad(mx, oT, oR, oT, iR, iT, mx, iT),
        quad(oL, oT, iL, iT, iL, my, oL, my),
        quad(oL, my, iL, my, iL, iB, oL, oB),
        quad(oR, oT, oR, my, iR, my, iR, iT),
        quad(oR, my, oR, oB, iR, iB, iR, my),
        quad(oL, oB, iL, iB, mx, iB, mx, oB),
        quad(mx, oB, mx, iB, iR, iB, oR, oB),
    )
    val tw = iR - iL; val th = iB - iT
    val shine = Path().apply {
        moveTo(iL, iT); lineTo(iL + tw * 0.62f, iT); lineTo(iL, iT + th * 0.62f); close()
    }
    return FacetGeometry(
        clip = Path().apply { addRoundRect(RoundRect(Rect(oL, oT, oR, oB), corner)) },
        facets = facets,
        table = Offset(iL, iT), tableSize = Size(tw, th), tableCorner = CornerRadius(r * 0.4f, r * 0.4f),
        tableShine = shine,
        oL = oL, oT = oT, bw = bw, bh = bh, corner = corner,
    )
}

/** Luz de cada faceta, de la más iluminada (arriba-izquierda) a la más en sombra (abajo-derecha). */
private fun facetColors(base: Color, light: Color, dark: Color) = arrayOf(
    light,
    lerp(light, base, 0.35f),
    lerp(light, base, 0.55f),
    lerp(base, dark, 0.12f),
    lerp(base, dark, 0.22f),
    lerp(base, dark, 0.55f),
    lerp(base, dark, 0.62f),
    dark,
)

private fun DrawScope.drawFacets(g: FacetGeometry, colors: Array<Color>, tableBrush: Brush, edge: Color, alpha: Float) {
    clipPath(g.clip) {
        for (i in g.facets.indices) drawPath(g.facets[i], colors[i], alpha = alpha)
        drawRoundRect(tableBrush, g.table, g.tableSize, g.tableCorner, alpha = alpha)
        drawPath(g.tableShine, Color.White, alpha = 0.30f * alpha)
        // Arista de la mesa: el filo que separa la talla de la cara.
        drawRoundRect(edge, g.table, g.tableSize, g.tableCorner, alpha = 0.55f * alpha, style = g.edgeStroke)
    }
}

private fun tableBrushFor(g: FacetGeometry, base: Color, light: Color) = Brush.linearGradient(
    listOf(lerp(base, light, 0.55f), base, lerp(base, light, 0.2f)),
    start = g.table, end = Offset(g.table.x + g.tableSize.width, g.table.y + g.tableSize.height)
)

/**
 * Gema tallada, versión inmediata: la usan `drawStyledTile` y todo lo que pinta bloques en movimiento
 * (efectos, fantasma de arrastre). Sin destello: un bloque que vuela no necesita brillar, y así esta
 * ruta no depende del reloj.
 */
fun DrawScope.drawFacetedTile(
    topLeft: Offset, size: Size, base: Color, light: Color, dark: Color,
    cornerFraction: Float = 0.10f, alpha: Float = 1f, gapFraction: Float = 0.05f,
) {
    drawCachedTile(
        BlockTileStyle.FACETED, topLeft, size, base, light, dark, cornerFraction, gapFraction,
        0, alpha, build = ::facetRecipe,
    )
}

/** Receta cacheada de la ruta inmediata (ver [drawCachedTile]): construida en el origen. */
private fun facetRecipe(k: TileKey): TileRecipe? {
    val g = facetGeometry(Offset.Zero, k.size, k.cornerFraction, k.gapFraction) ?: return null
    val colors = facetColors(k.base, k.light, k.dark)
    val tableBrush = tableBrushFor(g, k.base, k.light)
    val edge = lerp(k.light, Color.White, 0.5f)
    return TileRecipe { scope, a -> scope.drawFacets(g, colors, tableBrush, edge, a) }
}

/** Gema tallada cacheada por tamaño (sin destello desde el 2026-09-30). */
fun Modifier.facetedTile(
    base: Color, light: Color, dark: Color, cornerFraction: Float = 0.10f,
    gapFraction: Float = 0.05f,
): Modifier = this.drawWithCache {
    val g = facetGeometry(Offset.Zero, size, cornerFraction, gapFraction)
        ?: return@drawWithCache onDrawBehind {}
    val colors = facetColors(base, light, dark)
    val tableBrush = tableBrushFor(g, base, light)
    val edge = lerp(light, Color.White, 0.5f)
    onDrawBehind { drawFacets(g, colors, tableBrush, edge, 1f) }
}
