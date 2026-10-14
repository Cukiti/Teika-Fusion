package com.cukiti.teikafusion.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp

/**
 * **El bloque NEÓN** (P-408, 2026-09-30): un tubo de luz del color de la ficha sobre una casilla casi
 * negra, como los block puzzle «neón» de la tienda. El halo NO es un desenfoque (caro y, en 64 casillas,
 * un tirón): son tres trazos concéntricos de grosor decreciente y opacidad creciente — halo ancho y
 * tenue, tubo, y núcleo casi blanco. Todo queda DENTRO de la casilla: el contrato de juego dice que la
 * silueta ocupa exactamente una celda.
 *
 * Gama Premium, siempre a la vista en la vitrina (no es de bioma). Construido UNA vez por tamaño.
 */
private class NeonGeometry(
    val clip: Path,
    val oL: Float, val oT: Float, val bw: Float, val bh: Float,
    val corner: CornerRadius,
    val tubeTL: Offset, val tubeSize: Size, val tubeCorner: CornerRadius,
    val coreTL: Offset, val coreSize: Size, val coreCorner: CornerRadius,
    val halo: Stroke, val tube: Stroke, val core: Stroke, val rimStroke: Stroke,
)

private fun neonGeometry(size: Size, cornerFraction: Float, gapFraction: Float): NeonGeometry? {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return null
    val gap = minOf(w, h) * gapFraction
    val oL = gap; val oT = gap
    val bw = w - gap * 2f; val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val m = minOf(bw, bh)
    val r = m * cornerFraction
    // El tubo corre a un 14 % del borde; el núcleo, por su mismo centro.
    val inset = m * 0.14f
    val tubeR = (r * 1.6f).coerceAtMost(m * 0.22f)
    return NeonGeometry(
        clip = Path().apply { addRoundRect(RoundRect(Rect(oL, oT, oL + bw, oT + bh), CornerRadius(r, r))) },
        oL = oL, oT = oT, bw = bw, bh = bh, corner = CornerRadius(r, r),
        tubeTL = Offset(oL + inset, oT + inset), tubeSize = Size(bw - inset * 2, bh - inset * 2),
        tubeCorner = CornerRadius(tubeR, tubeR),
        coreTL = Offset(oL + inset, oT + inset), coreSize = Size(bw - inset * 2, bh - inset * 2),
        coreCorner = CornerRadius(tubeR, tubeR),
        halo = Stroke(width = m * 0.20f), tube = Stroke(width = m * 0.085f), core = Stroke(width = m * 0.03f),
        rimStroke = Stroke(width = m * 0.03f),
    )
}

private class NeonPaint(val night: Color, val inner: Color, val haloC: Color, val tubeC: Color, val coreC: Color, val rim: Color)

private fun neonPaint(base: Color, light: Color, dark: Color) = NeonPaint(
    night = lerp(dark, Color(0xFF07061A), 0.82f),
    inner = base,
    haloC = base,
    tubeC = lerp(base, light, 0.35f),
    coreC = lerp(light, Color.White, 0.65f),
    rim = lerp(dark, Color.Black, 0.3f),
)

private fun DrawScope.drawNeon(g: NeonGeometry, p: NeonPaint, alpha: Float) {
    clipPath(g.clip) {
        drawRect(p.night, Offset(g.oL, g.oT), Size(g.bw, g.bh), alpha = alpha)
        // Un velo del color dentro del tubo: la luz tiñe el cristal que encierra.
        drawRoundRect(p.inner, g.tubeTL, g.tubeSize, g.tubeCorner, alpha = 0.16f * alpha)
        drawRoundRect(p.haloC, g.tubeTL, g.tubeSize, g.tubeCorner, alpha = 0.28f * alpha, style = g.halo)
        drawRoundRect(p.tubeC, g.tubeTL, g.tubeSize, g.tubeCorner, alpha = 0.95f * alpha, style = g.tube)
        drawRoundRect(p.coreC, g.coreTL, g.coreSize, g.coreCorner, alpha = alpha, style = g.core)
    }
    // Canto oscuro: separa casillas vecinas igual que el contorno del Básico.
    drawRoundRect(p.rim, Offset(g.oL, g.oT), Size(g.bw, g.bh), g.corner, alpha = 0.9f * alpha, style = g.rimStroke)
}

/** Tubo de neón, versión inmediata (efectos, fantasma de arrastre): pinta desde la caché. */
fun DrawScope.drawNeonTile(
    topLeft: Offset, size: Size, base: Color, light: Color, dark: Color,
    cornerFraction: Float = 0.12f, alpha: Float = 1f, gapFraction: Float = 0.05f,
) {
    drawCachedTile(
        BlockTileStyle.NEON, topLeft, size, base, light, dark, cornerFraction, gapFraction,
        0, alpha, build = ::neonRecipe,
    )
}

/** Receta cacheada (ver [drawCachedTile]): construida en el origen, una vez por clave. */
private fun neonRecipe(k: TileKey): TileRecipe? {
    val g = neonGeometry(k.size, k.cornerFraction, k.gapFraction) ?: return null
    val p = neonPaint(k.base, k.light, k.dark)
    return TileRecipe { scope, a -> scope.drawNeon(g, p, a) }
}

/** Tubo de neón cacheado por tamaño (bandeja, vitrina). Misma geometría y pintura que la ruta inmediata. */
fun Modifier.neonTile(
    base: Color, light: Color, dark: Color, cornerFraction: Float = 0.12f,
    gapFraction: Float = 0.05f,
): Modifier = this.drawWithCache {
    val g = neonGeometry(size, cornerFraction, gapFraction) ?: return@drawWithCache onDrawBehind {}
    val p = neonPaint(base, light, dark)
    onDrawBehind { drawNeon(g, p, 1f) }
}
