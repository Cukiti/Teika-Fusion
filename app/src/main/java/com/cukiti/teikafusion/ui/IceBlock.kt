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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.sin

/**
 * **El bloque HIELO** (P-251 4/7, 2026-09-25): un cubito de hielo con la silueta CUADRADA del Clásico
 * (la forma no se toca; la personalidad va en la cara).
 *
 * - **Cara:** el color de la ficha congelado —aclarado, con un núcleo más intenso dentro, como el
 *   hielo coloreado que se ve más oscuro en el centro—, un REFLEJO ancho en diagonal, burbujas
 *   atrapadas y ESCARCHA en dos esquinas. El filo es blanco: se lee frío antes que de color.
 * - **Reposo:** quieto. Sin la estrellita que titilaba en una esquina (2026-09-30, decisión del usuario: «quiero quitar ese brillo… también de los bloques»).
 * - **Rotura:** estalla en trozos de hielo que caen y un VAHO blanco que sube (`BatchLineCellShatterVFX`).
 *
 * Todo lo que no es tiempo se construye UNA vez por tamaño: cero asignaciones por frame.
 */
private class IceGeometry(
    val clip: Path,
    val body: RoundRect,
    val core: RoundRect,
    val shine: Path,
    val frost: Path,
    val bubbles: Array<Offset>, val bubbleR: Float,
    val sparkle: Offset, val sparkleR: Float,
    val oL: Float, val oT: Float, val bw: Float, val bh: Float,
)

private fun iceGeometry(topLeft: Offset, size: Size, cornerFraction: Float, gapFraction: Float, variant: Int): IceGeometry? {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return null
    val gap = minOf(w, h) * gapFraction
    val oL = topLeft.x + gap; val oT = topLeft.y + gap
    val bw = w - gap * 2f; val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val m = minOf(bw, bh)
    val r = m * cornerFraction
    val v = ((variant % 3) + 3) % 3
    val body = RoundRect(Rect(oL, oT, oL + bw, oT + bh), CornerRadius(r, r))
    val ci = m * 0.2f
    val core = RoundRect(Rect(oL + ci, oT + ci, oL + bw - ci, oT + bh - ci), CornerRadius(r * 1.4f, r * 1.4f))
    // Reflejo: una banda ancha en diagonal por la mitad superior izquierda.
    val shine = Path().apply {
        moveTo(oL + bw * 0.10f, oT + bh * 0.58f); lineTo(oL + bw * 0.58f, oT + bh * 0.10f)
        lineTo(oL + bw * 0.76f, oT + bh * 0.10f); lineTo(oL + bw * 0.10f, oT + bh * 0.76f); close()
    }
    // Escarcha: agujas finas que nacen de dos esquinas opuestas.
    val frost = Path().apply {
        val a = Offset(oL, oT + bh); val b = Offset(oL + bw, oT)
        moveTo(a.x, a.y - bh * 0.30f); lineTo(a.x + bw * 0.10f, a.y - bh * 0.12f)
        moveTo(a.x + bw * 0.30f, a.y); lineTo(a.x + bw * 0.12f, a.y - bh * 0.10f)
        moveTo(a.x, a.y - bh * 0.16f); lineTo(a.x + bw * 0.16f, a.y)
        moveTo(b.x - bw * 0.30f, b.y); lineTo(b.x - bw * 0.12f, b.y + bh * 0.10f)
        moveTo(b.x, b.y + bh * 0.30f); lineTo(b.x - bw * 0.10f, b.y + bh * 0.12f)
    }
    val bubbles = when (v) {
        0 -> arrayOf(Offset(oL + bw * 0.66f, oT + bh * 0.62f), Offset(oL + bw * 0.58f, oT + bh * 0.74f))
        1 -> arrayOf(Offset(oL + bw * 0.40f, oT + bh * 0.70f), Offset(oL + bw * 0.70f, oT + bh * 0.50f))
        else -> arrayOf(Offset(oL + bw * 0.72f, oT + bh * 0.72f), Offset(oL + bw * 0.50f, oT + bh * 0.60f))
    }
    val sparkle = when (v) {
        0 -> Offset(oL + bw * 0.78f, oT + bh * 0.24f)
        1 -> Offset(oL + bw * 0.24f, oT + bh * 0.26f)
        else -> Offset(oL + bw * 0.76f, oT + bh * 0.78f)
    }
    return IceGeometry(
        clip = Path().apply { addRoundRect(body) },
        body = body, core = core, shine = shine, frost = frost,
        bubbles = bubbles, bubbleR = m * 0.045f,
        sparkle = sparkle, sparkleR = m * 0.16f,
        oL = oL, oT = oT, bw = bw, bh = bh,
    )
}

private class IcePaint(val face: Brush, val core: Brush, val rim: Color, val frostStroke: Stroke, val rimStroke: Stroke, val bubbleStroke: Stroke)

private fun icePaint(g: IceGeometry, base: Color, light: Color, dark: Color): IcePaint {
    val m = minOf(g.bw, g.bh)
    return IcePaint(
        face = Brush.linearGradient(
            listOf(lerp(light, Color.White, 0.55f), lerp(base, Color.White, 0.35f), lerp(base, dark, 0.15f)),
            start = Offset(g.oL, g.oT), end = Offset(g.oL + g.bw, g.oT + g.bh)
        ),
        core = Brush.radialGradient(
            listOf(lerp(base, dark, 0.25f), lerp(base, Color.White, 0.2f).copy(alpha = 0f)),
            center = Offset(g.oL + g.bw * 0.55f, g.oT + g.bh * 0.58f), radius = m * 0.5f
        ),
        rim = Color.White,
        frostStroke = Stroke(width = m * 0.028f, cap = StrokeCap.Round),
        rimStroke = Stroke(width = m * 0.04f),
        bubbleStroke = Stroke(width = g.bubbleR * 0.45f),
    )
}

private fun DrawScope.drawIce(g: IceGeometry, p: IcePaint, alpha: Float) {
    clipPath(g.clip) {
        drawRect(p.face, Offset(g.oL, g.oT), Size(g.bw, g.bh), alpha = alpha)
        drawRoundRect(p.core, g.core.topLeft(), g.core.size(), g.core.topLeftCornerRadius, alpha = 0.85f * alpha)
        drawPath(g.shine, Color.White, alpha = 0.30f * alpha)
        for (b in g.bubbles) {
            drawCircle(Color.White, g.bubbleR, b, alpha = 0.45f * alpha, style = p.bubbleStroke)
        }
        drawPath(g.frost, Color.White, alpha = 0.75f * alpha, style = p.frostStroke)
    }
    drawRoundRect(p.rim, Offset(g.oL, g.oT), Size(g.bw, g.bh), g.body.topLeftCornerRadius, alpha = 0.75f * alpha, style = p.rimStroke)
}

private fun RoundRect.topLeft() = Offset(left, top)
private fun RoundRect.size() = Size(width, height)

/** Cubito de hielo, versión inmediata (efectos, fantasma de arrastre). Sin destello. */
fun DrawScope.drawIceTile(
    topLeft: Offset, size: Size, base: Color, light: Color, dark: Color,
    cornerFraction: Float = 0.14f, alpha: Float = 1f, gapFraction: Float = 0.05f, frostVariant: Int = 0,
) {
    drawCachedTile(
        BlockTileStyle.ICE, topLeft, size, base, light, dark, cornerFraction, gapFraction,
        frostVariant, alpha, build = ::iceRecipe,
    )
}

/** Receta cacheada de la ruta inmediata (ver [drawCachedTile]): construida en el origen. */
private fun iceRecipe(k: TileKey): TileRecipe? {
    val g = iceGeometry(Offset.Zero, k.size, k.cornerFraction, k.gapFraction, k.variant) ?: return null
    val p = icePaint(g, k.base, k.light, k.dark)
    return TileRecipe { scope, a -> scope.drawIce(g, p, a) }
}

/** Cubito de hielo cacheado por tamaño (sin destello desde el 2026-09-30). [frostVariant] mueve las burbujas. */
fun Modifier.iceTile(
    base: Color, light: Color, dark: Color, cornerFraction: Float = 0.14f,
    gapFraction: Float = 0.05f, frostVariant: Int = 0,
): Modifier = this.drawWithCache {
    val g = iceGeometry(Offset.Zero, size, cornerFraction, gapFraction, frostVariant)
        ?: return@drawWithCache onDrawBehind {}
    val paint = icePaint(g, base, light, dark)
    onDrawBehind { drawIce(g, paint, 1f) }
}
