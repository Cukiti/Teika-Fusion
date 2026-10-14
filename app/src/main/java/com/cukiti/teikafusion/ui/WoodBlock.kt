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

/**
 * **El bloque MADERA** (P-251 2/7, 2026-09-25): una tabla PINTADA con la silueta CUADRADA del Clásico
 * (la regla de la vitrina: la forma no se toca, la personalidad va en la cara).
 *
 * - **Cara:** la pintura del color de la ficha (así los cinco colores se siguen distinguiendo), con
 *   las VETAS de la madera asomando por debajo en tres líneas onduladas, un NUDO y dos CLAVOS en
 *   esquinas opuestas. Bisel claro arriba y canto oscuro abajo: se lee como una caja de fruta.
 * - **Variedad:** en el tablero cada celda elige una de tres vetas según su posición (`grainVariant`),
 *   para que la madera no parezca un estampado repetido. La bandeja y los efectos usan la 0.
 * - **Rotura:** estalla en ASTILLAS alargadas con serrín (ver `BatchLineCellShatterVFX`).
 *
 * No tiene reposo animado: la madera es el bloque «tranquilo» de la vitrina. Todo se construye UNA vez
 * por tamaño (`drawWithCache`): cero asignaciones por frame.
 */
private class WoodGeometry(
    val clip: Path,
    val body: RoundRect,
    val grain: Array<Path>,
    val knotCenter: Offset, val knotR: Float,
    val nailA: Offset, val nailB: Offset, val nailR: Float,
    val oL: Float, val oT: Float, val bw: Float, val bh: Float,
) {
    /** Trazos construidos una vez por tamaño, no en cada dibujo. */
    val grainStroke = Stroke(width = bh * 0.035f, cap = StrokeCap.Round)
    val knotStroke = Stroke(width = bh * 0.035f)
    val rimStroke = Stroke(width = bw * 0.03f)
}

private fun woodGeometry(topLeft: Offset, size: Size, cornerFraction: Float, gapFraction: Float, variant: Int): WoodGeometry? {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return null
    val gap = minOf(w, h) * gapFraction
    val oL = topLeft.x + gap; val oT = topLeft.y + gap
    val bw = w - gap * 2f; val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val r = minOf(bw, bh) * cornerFraction
    val body = RoundRect(Rect(oL, oT, oL + bw, oT + bh), CornerRadius(r, r))
    // Tres vetas onduladas; cada variante las desplaza y cambia el sentido de la onda.
    val v = ((variant % 3) + 3) % 3
    val shift = floatArrayOf(0f, 0.07f, -0.05f)[v]
    val flip = if (v == 1) -1f else 1f
    val grain = Array(3) { i ->
        val y = oT + bh * (0.30f + i * 0.20f + shift)
        val amp = bh * 0.045f * flip * (if (i % 2 == 0) 1f else -1f)
        Path().apply {
            moveTo(oL, y)
            quadraticTo(oL + bw * 0.28f, y - amp, oL + bw * 0.5f, y)
            quadraticTo(oL + bw * 0.72f, y + amp, oL + bw, y)
        }
    }
    val knot = when (v) {
        0 -> Offset(oL + bw * 0.68f, oT + bh * 0.58f)
        1 -> Offset(oL + bw * 0.32f, oT + bh * 0.46f)
        else -> Offset(oL + bw * 0.60f, oT + bh * 0.72f)
    }
    val inset = minOf(bw, bh) * 0.16f
    return WoodGeometry(
        clip = Path().apply { addRoundRect(body) },
        body = body,
        grain = grain,
        knotCenter = knot, knotR = minOf(bw, bh) * 0.075f,
        nailA = Offset(oL + inset, oT + inset), nailB = Offset(oL + bw - inset, oT + bh - inset),
        nailR = minOf(bw, bh) * 0.045f,
        oL = oL, oT = oT, bw = bw, bh = bh,
    )
}

private fun DrawScope.drawWood(g: WoodGeometry, face: Brush, base: Color, light: Color, dark: Color, alpha: Float) {
    clipPath(g.clip) {
        drawRect(face, Offset(g.oL, g.oT), Size(g.bw, g.bh), alpha = alpha)
        // Vetas: el color oscuro de la pintura, fino y translúcido — asoman, no mandan.
        for (p in g.grain) {
            drawPath(p, dark, alpha = 0.34f * alpha, style = g.grainStroke)
        }
        drawOval(
            dark, Offset(g.knotCenter.x - g.knotR * 1.3f, g.knotCenter.y - g.knotR),
            Size(g.knotR * 2.6f, g.knotR * 2f), alpha = 0.38f * alpha, style = g.knotStroke
        )
        // Bisel: luz en el canto de arriba y sombra en el de abajo.
        drawRect(light, Offset(g.oL, g.oT), Size(g.bw, g.bh * 0.12f), alpha = 0.55f * alpha)
        drawRect(dark, Offset(g.oL, g.oT + g.bh * 0.86f), Size(g.bw, g.bh * 0.14f), alpha = 0.55f * alpha)
        // Clavos: cabeza gris con su brillo.
        val nail = Color(0xFF4A4550)
        for (n in 0..1) {
            val c = if (n == 0) g.nailA else g.nailB
            drawCircle(nail, g.nailR, c, alpha = 0.85f * alpha)
            drawCircle(Color.White, g.nailR * 0.38f, Offset(c.x - g.nailR * 0.3f, c.y - g.nailR * 0.3f), alpha = 0.55f * alpha)
        }
    }
    // Filo del contorno: separa una tabla de la de al lado.
    drawRoundRect(
        lerp(dark, Color.Black, 0.25f), Offset(g.oL, g.oT), Size(g.bw, g.bh),
        g.body.topLeftCornerRadius, alpha = 0.6f * alpha, style = g.rimStroke
    )
}

private fun woodFace(g: WoodGeometry, base: Color, light: Color) = Brush.verticalGradient(
    listOf(lerp(base, light, 0.30f), base, lerp(base, Color.Black, 0.06f)),
    startY = g.oT, endY = g.oT + g.bh
)

/**
 * Tabla de madera, versión inmediata: la usan `drawStyledTile` y todo lo que pinta bloques en
 * movimiento (efectos, fantasma de arrastre).
 */
fun DrawScope.drawWoodTile(
    topLeft: Offset, size: Size, base: Color, light: Color, dark: Color,
    cornerFraction: Float = 0.12f, alpha: Float = 1f, gapFraction: Float = 0.05f, grainVariant: Int = 0,
) {
    drawCachedTile(
        BlockTileStyle.WOOD, topLeft, size, base, light, dark, cornerFraction, gapFraction,
        ((grainVariant % 3) + 3) % 3, alpha, build = ::woodRecipe,
    )
}

/** Receta cacheada de la ruta inmediata (ver [drawCachedTile]): construida en el origen. */
private fun woodRecipe(k: TileKey): TileRecipe? {
    val g = woodGeometry(Offset.Zero, k.size, k.cornerFraction, k.gapFraction, k.variant) ?: return null
    val face = woodFace(g, k.base, k.light)
    return TileRecipe { scope, a -> scope.drawWood(g, face, k.base, k.light, k.dark, a) }
}

/** Tabla de madera cacheada por tamaño. [grainVariant] elige la veta (el tablero pasa su posición). */
fun Modifier.woodTile(
    base: Color, light: Color, dark: Color, cornerFraction: Float = 0.12f,
    gapFraction: Float = 0.05f, grainVariant: Int = 0,
): Modifier = this.drawWithCache {
    val g = woodGeometry(Offset.Zero, size, cornerFraction, gapFraction, grainVariant)
        ?: return@drawWithCache onDrawBehind {}
    val face = woodFace(g, base, light)
    onDrawBehind { drawWood(g, face, base, light, dark, 1f) }
}
