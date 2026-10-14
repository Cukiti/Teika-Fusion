package com.cukiti.teikafusion.ui

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
 * **El bloque DORADO** (P-408, 2026-09-30): un marco biselado de ORO —el mismo para las cinco fichas—
 * con una GEMA del color de la ficha engastada en el centro. Inspirado en los block puzzle dorados de
 * la tienda, pero sin hacer todo el bloque de oro: el color de la ficha tiene que seguir leyéndose,
 * porque las líneas de un solo color puntúan aparte.
 *
 * Bisel de cuatro facetas como el Básico, pero con el marco más estrecho (la gema a un 19 % del borde)
 * para que el color mande. Gama Legendaria, siempre a la vista en la vitrina.
 */
private val GOLD_TOP = Color(0xFFFFF1B0)
private val GOLD_LEFT = Color(0xFFF5C94A)
private val GOLD_RIGHT = Color(0xFFC88E1E)
private val GOLD_BOTTOM = Color(0xFF8A5A0E)
private val GOLD_RIM = Color(0xFF4A2E05)

private class GoldGeometry(
    val clip: Path,
    val top: Path, val bottom: Path, val left: Path, val right: Path,
    val body: RoundRect,
    val gemTL: Offset, val gemSize: Size, val gemCorner: CornerRadius,
    val glint: Path,
    val setting: Stroke, val rim: Stroke,
    val iT: Float, val iB: Float,
)

private fun quad(x0: Float, y0: Float, x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) =
    Path().apply { moveTo(x0, y0); lineTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close() }

private fun goldGeometry(size: Size, cornerFraction: Float, gapFraction: Float): GoldGeometry? {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return null
    val gap = minOf(w, h) * gapFraction
    val oL = gap; val oT = gap
    val bw = w - gap * 2f; val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val oR = oL + bw; val oB = oT + bh
    val m = minOf(bw, bh)
    val r = m * cornerFraction
    // Marco más estrecho que el del Básico (19 % y no 26 %): la gema tiene que mandar, o a tamaño de
    // tablero el oro se come el color de la ficha.
    val inset = m * 0.19f
    val iL = oL + inset; val iT = oT + inset; val iR = oR - inset; val iB = oB - inset
    val body = RoundRect(Rect(oL, oT, oR, oB), CornerRadius(r, r))
    val face = minOf(iR - iL, iB - iT)
    val g = face * 0.12f
    val leg = face * 0.38f
    return GoldGeometry(
        clip = Path().apply { addRoundRect(body) },
        top = quad(oL, oT, oR, oT, iR, iT, iL, iT),
        bottom = quad(oL, oB, oR, oB, iR, iB, iL, iB),
        left = quad(oL, oT, oL, oB, iL, iB, iL, iT),
        right = quad(oR, oT, oR, oB, iR, iB, iR, iT),
        body = body,
        gemTL = Offset(iL, iT), gemSize = Size(iR - iL, iB - iT), gemCorner = CornerRadius(r * 0.5f, r * 0.5f),
        glint = Path().apply { moveTo(iL + g, iT + g); lineTo(iL + g + leg, iT + g); lineTo(iL + g, iT + g + leg); close() },
        // El engaste: un filo oscuro alrededor de la gema que la separa del oro.
        setting = Stroke(width = m * 0.035f),
        // Como en el Básico, la mitad de fuera la recorta el clip: se pide el doble.
        rim = Stroke(width = m * 0.05f),
        iT = iT, iB = iB,
    )
}

private class GoldPaint(val gem: Brush, val glint: Color, val setting: Color)

private fun goldPaint(g: GoldGeometry, base: Color, light: Color, dark: Color) = GoldPaint(
    gem = Brush.verticalGradient(
        listOf(lerp(base, light, 0.45f), base, lerp(base, dark, 0.45f)), startY = g.iT, endY = g.iB
    ),
    glint = lerp(light, Color.White, 0.7f),
    setting = lerp(dark, GOLD_RIM, 0.5f),
)

private fun DrawScope.drawGold(g: GoldGeometry, p: GoldPaint, alpha: Float) {
    clipPath(g.clip) {
        drawPath(g.top, GOLD_TOP, alpha = alpha)
        drawPath(g.bottom, GOLD_BOTTOM, alpha = alpha)
        drawPath(g.left, GOLD_LEFT, alpha = alpha)
        drawPath(g.right, GOLD_RIGHT, alpha = alpha)
        drawRoundRect(p.gem, g.gemTL, g.gemSize, g.gemCorner, alpha = alpha)
        drawRoundRect(p.setting, g.gemTL, g.gemSize, g.gemCorner, alpha = 0.85f * alpha, style = g.setting)
        drawPath(g.glint, p.glint, alpha = 0.9f * alpha)
        drawRoundRect(GOLD_RIM, Offset(g.body.left, g.body.top), Size(g.body.width, g.body.height), g.body.topLeftCornerRadius, alpha = alpha, style = g.rim)
    }
}

/** Oro con gema, versión inmediata (efectos, fantasma de arrastre): pinta desde la caché. */
fun DrawScope.drawGoldTile(
    topLeft: Offset, size: Size, base: Color, light: Color, dark: Color,
    cornerFraction: Float = 0.12f, alpha: Float = 1f, gapFraction: Float = 0.05f,
) {
    drawCachedTile(
        BlockTileStyle.GOLD, topLeft, size, base, light, dark, cornerFraction, gapFraction,
        0, alpha, build = ::goldRecipe,
    )
}

/** Receta cacheada (ver [drawCachedTile]): construida en el origen, una vez por clave. */
private fun goldRecipe(k: TileKey): TileRecipe? {
    val g = goldGeometry(k.size, k.cornerFraction, k.gapFraction) ?: return null
    val p = goldPaint(g, k.base, k.light, k.dark)
    return TileRecipe { scope, a -> scope.drawGold(g, p, a) }
}

/** Oro con gema cacheado por tamaño (bandeja, vitrina). Misma geometría y pintura que la ruta inmediata. */
fun Modifier.goldTile(
    base: Color, light: Color, dark: Color, cornerFraction: Float = 0.12f,
    gapFraction: Float = 0.05f,
): Modifier = this.drawWithCache {
    val g = goldGeometry(size, cornerFraction, gapFraction) ?: return@drawWithCache onDrawBehind {}
    val p = goldPaint(g, base, light, dark)
    onDrawBehind { drawGold(g, p, 1f) }
}
