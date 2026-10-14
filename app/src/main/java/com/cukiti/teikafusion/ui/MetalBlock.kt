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
 * **El bloque METAL** (2026-09-25, pedido del usuario: «me gustó tanto el bloque-Teika metálico que
 * quiero añadirlo como tipo de bloque»). Es la placa de aluminio cepillado de aquel bloque —bisel,
 * marco interior y cuatro remaches— pero ANODIZADA: teñida del color de cada ficha, para que los cinco
 * colores se sigan distinguiendo. Sin las puntas: en un tablero lleno, 64 casillas con puntas que
 * asoman se taparían unas a otras.
 *
 * - **Reposo:** quieta. Sin el brillo que recorría el tablero (2026-09-30, decisión del usuario: «quiero quitar ese brillo… también de los bloques»).
 * - **Rotura:** los remaches saltan y la chapa se dobla y cae (`BatchLineCellShatterVFX`).
 *
 * No es un bloque de bioma: se compra en la vitrina en la gama Legendaria. Todo lo que no es tiempo se
 * construye UNA vez por tamaño: cero asignaciones por frame.
 */
private class MetalGeometry(
    val clip: Path,
    val body: RoundRect,
    val brushY: FloatArray,
    val inner: Offset, val innerSize: Size, val innerCorner: CornerRadius,
    val rivets: Array<Offset>, val rivetR: Float,
    val oL: Float, val oT: Float, val bw: Float, val bh: Float,
) {
    val depth = TileDepth(oL, oT, bw, bh)
}

private fun metalGeometry(topLeft: Offset, size: Size, cornerFraction: Float, gapFraction: Float): MetalGeometry? {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return null
    val gap = minOf(w, h) * gapFraction
    val oL = topLeft.x + gap; val oT = topLeft.y + gap
    val bw = w - gap * 2f; val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val m = minOf(bw, bh)
    val r = m * cornerFraction
    val body = RoundRect(Rect(oL, oT, oL + bw, oT + bh), CornerRadius(r, r))
    val inset = m * 0.07f
    return MetalGeometry(
        clip = Path().apply { addRoundRect(body) },
        body = body,
        brushY = FloatArray(7) { i -> oT + bh * (0.14f + i * 0.12f) },
        inner = Offset(oL + inset, oT + inset), innerSize = Size(bw - inset * 2, bh - inset * 2),
        innerCorner = CornerRadius(r * 0.7f, r * 0.7f),
        rivets = arrayOf(
            Offset(oL + bw * 0.16f, oT + bh * 0.16f), Offset(oL + bw * 0.84f, oT + bh * 0.16f),
            Offset(oL + bw * 0.84f, oT + bh * 0.84f), Offset(oL + bw * 0.16f, oT + bh * 0.84f),
        ),
        rivetR = m * 0.045f,
        oL = oL, oT = oT, bw = bw, bh = bh,
    )
}

private class MetalPaint(val plate: Brush, val brushLight: Color, val brushDark: Color, val bevel: Color,
                         val rivet: Color, val bevelStroke: Stroke, val innerStroke: Stroke, val brushW: Float)

private fun metalPaint(g: MetalGeometry, base: Color, light: Color, dark: Color): MetalPaint {
    val m = minOf(g.bw, g.bh)
    return MetalPaint(
        // Anodizado: el color de la ficha con el brillo del metal por encima (claro arriba-izquierda).
        plate = Brush.linearGradient(
            listOf(lerp(light, Color.White, 0.45f), lerp(base, light, 0.25f), lerp(base, Color.White, 0.18f), lerp(base, dark, 0.35f)),
            start = Offset(g.oL, g.oT), end = Offset(g.oL + g.bw, g.oT + g.bh)
        ),
        brushLight = Color.White, brushDark = lerp(dark, Color.Black, 0.2f),
        bevel = lerp(dark, Color.Black, 0.35f),
        rivet = lerp(dark, Color(0xFF7D8591), 0.5f),
        bevelStroke = Stroke(width = m * 0.05f),
        innerStroke = Stroke(width = m * 0.02f),
        brushW = m * 0.012f,
    )
}

private fun DrawScope.drawMetal(g: MetalGeometry, p: MetalPaint, alpha: Float) {
    clipPath(g.clip) {
        drawRect(p.plate, Offset(g.oL, g.oT), Size(g.bw, g.bh), alpha = alpha)
        for (i in g.brushY.indices) {
            val y = g.brushY[i]
            drawLine(if (i % 2 == 0) p.brushLight else p.brushDark,
                Offset(g.oL + g.bw * 0.07f, y), Offset(g.oL + g.bw * 0.93f, y),
                strokeWidth = p.brushW, alpha = (if (i % 2 == 0) 0.40f else 0.22f) * alpha)
        }
        g.depth.draw(this, alpha)
    }
    drawRoundRect(p.bevel, Offset(g.oL, g.oT), Size(g.bw, g.bh), g.body.topLeftCornerRadius, alpha = 0.8f * alpha, style = p.bevelStroke)
    drawRoundRect(Color.White, g.inner, g.innerSize, g.innerCorner, alpha = 0.40f * alpha, style = p.innerStroke)
    for (r in g.rivets) {
        drawCircle(p.rivet, radius = g.rivetR, center = r, alpha = alpha)
        drawCircle(Color.White, radius = g.rivetR * 0.45f, center = Offset(r.x - g.rivetR * 0.3f, r.y - g.rivetR * 0.3f), alpha = 0.8f * alpha)
    }
}

/** Placa de metal anodizado, versión inmediata (efectos, fantasma de arrastre). Sin brillo. */
fun DrawScope.drawMetalTile(
    topLeft: Offset, size: Size, base: Color, light: Color, dark: Color,
    cornerFraction: Float = 0.10f, alpha: Float = 1f, gapFraction: Float = 0.05f,
) {
    drawCachedTile(
        BlockTileStyle.METAL, topLeft, size, base, light, dark, cornerFraction, gapFraction,
        0, alpha, build = ::metalRecipe,
    )
}

/** Receta cacheada de la ruta inmediata (ver [drawCachedTile]): construida en el origen. */
private fun metalRecipe(k: TileKey): TileRecipe? {
    val g = metalGeometry(Offset.Zero, k.size, k.cornerFraction, k.gapFraction) ?: return null
    val p = metalPaint(g, k.base, k.light, k.dark)
    return TileRecipe { scope, a -> scope.drawMetal(g, p, a) }
}

/** Placa de metal cacheada por tamaño (sin brillo desde el 2026-09-30). */
fun Modifier.metalTile(
    base: Color, light: Color, dark: Color, cornerFraction: Float = 0.10f,
    gapFraction: Float = 0.05f,
): Modifier = this.drawWithCache {
    val g = metalGeometry(Offset.Zero, size, cornerFraction, gapFraction)
        ?: return@drawWithCache onDrawBehind {}
    val paint = metalPaint(g, base, light, dark)
    onDrawBehind { drawMetal(g, paint, 1f) }
}
