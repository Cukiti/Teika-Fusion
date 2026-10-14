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
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.lerp

/**
 * **El bloque VACÍO** (P-251 7/7, 2026-09-25): un agujero negro de bolsillo con la silueta CUADRADA
 * del Clásico (la forma no se toca; la personalidad va en la cara).
 *
 * - **Cara:** espacio casi negro con estrellitas, un ANILLO DE ACRECIÓN inclinado del color de la
 *   ficha —es lo que distingue los cinco colores— alrededor de un centro más negro que el fondo, con
 *   el borde del horizonte encendido. Cada celda inclina el anillo hacia un lado (`swirlVariant`).
 * - **Reposo:** quieto. Sin el brillo que giraba por el anillo (2026-09-30, decisión del usuario: «quiero quitar ese brillo… también de los bloques»).
 * - **Rotura:** el bloque IMPLOSIONA —los trozos caen hacia el centro— y acaba en un destello
 *   (`BatchLineCellShatterVFX`).
 *
 * Todo lo que no es tiempo se construye UNA vez por tamaño: cero asignaciones por frame.
 */
private val VoidBlack = Color(0xFF05060F)

private class VoidGeometry(
    val clip: Path,
    val body: RoundRect,
    val center: Offset,
    val ringW: Float, val ringH: Float, val tiltDeg: Float,
    val holeR: Float,
    val dust: Array<Offset>, val dustR: Float,
    val oL: Float, val oT: Float, val bw: Float, val bh: Float,
) {
    /** Volumen común (P-283), construido una vez por tamaño. */
    val depth = TileDepth(oL, oT, bw, bh)
}

private fun voidGeometry(topLeft: Offset, size: Size, cornerFraction: Float, gapFraction: Float, variant: Int): VoidGeometry? {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return null
    val gap = minOf(w, h) * gapFraction
    val oL = topLeft.x + gap; val oT = topLeft.y + gap
    val bw = w - gap * 2f; val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val m = minOf(bw, bh)
    val r = m * cornerFraction
    val body = RoundRect(Rect(oL, oT, oL + bw, oT + bh), CornerRadius(r, r))
    val tilt = floatArrayOf(-18f, 14f, -8f, 22f)[((variant % 4) + 4) % 4]
    return VoidGeometry(
        clip = Path().apply { addRoundRect(body) },
        body = body,
        center = Offset(oL + bw * 0.5f, oT + bh * 0.52f),
        ringW = m * 0.78f, ringH = m * 0.32f, tiltDeg = tilt,
        holeR = m * 0.17f,
        dust = arrayOf(
            Offset(oL + bw * 0.16f, oT + bh * 0.18f), Offset(oL + bw * 0.82f, oT + bh * 0.22f),
            Offset(oL + bw * 0.20f, oT + bh * 0.84f), Offset(oL + bw * 0.78f, oT + bh * 0.86f),
            Offset(oL + bw * 0.52f, oT + bh * 0.12f),
        ),
        dustR = m * 0.018f,
        oL = oL, oT = oT, bw = bw, bh = bh,
    )
}

private class VoidPaint(
    val space: Brush, val ring: Brush, val halo: Brush, val horizon: Color, val rim: Color,
    val ringStroke: Stroke, val rimStroke: Stroke,
)

private fun voidPaint(g: VoidGeometry, base: Color, light: Color, dark: Color): VoidPaint {
    val m = minOf(g.bw, g.bh)
    return VoidPaint(
        space = Brush.radialGradient(
            listOf(lerp(dark, VoidBlack, 0.55f), VoidBlack),
            center = g.center, radius = m * 0.8f
        ),
        ring = Brush.horizontalGradient(
            listOf(lerp(base, VoidBlack, 0.3f), lerp(light, Color.White, 0.2f), base, lerp(base, VoidBlack, 0.3f)),
            startX = g.center.x - g.ringW / 2f, endX = g.center.x + g.ringW / 2f
        ),
        halo = Brush.radialGradient(
            listOf(base.copy(alpha = 0.55f), base.copy(alpha = 0f)),
            center = g.center, radius = m * 0.5f
        ),
        horizon = lerp(light, Color.White, 0.3f),
        rim = lerp(base, Color.White, 0.25f),
        ringStroke = Stroke(width = m * 0.10f),
        rimStroke = Stroke(width = g.bw * 0.03f),
    )
}

private fun DrawScope.drawVoid(g: VoidGeometry, p: VoidPaint, alpha: Float) {
    clipPath(g.clip) {
        drawRect(p.space, Offset(g.oL, g.oT), Size(g.bw, g.bh), alpha = alpha)
        for (d in g.dust) drawCircle(Color.White, g.dustR, d, alpha = 0.7f * alpha)
        drawRect(p.halo, Offset(g.oL, g.oT), Size(g.bw, g.bh), alpha = alpha)
        rotate(g.tiltDeg, g.center) {
            val tl = Offset(g.center.x - g.ringW / 2f, g.center.y - g.ringH / 2f)
            val sz = Size(g.ringW, g.ringH)
            drawOval(p.ring, tl, sz, alpha = alpha, style = p.ringStroke)
        }
        // El horizonte: un borde encendido y el negro absoluto dentro.
        drawCircle(p.horizon, g.holeR * 1.12f, g.center, alpha = 0.9f * alpha)
        drawCircle(VoidBlack, g.holeR, g.center, alpha = alpha)
        // La mitad trasera del anillo pasa por DELANTE del agujero en su parte baja: se lee inclinado.
        rotate(g.tiltDeg, g.center) {
            val tl = Offset(g.center.x - g.ringW / 2f, g.center.y - g.ringH / 2f)
            drawArc(p.ring, 20f, 140f, useCenter = false, topLeft = tl, size = Size(g.ringW, g.ringH), alpha = alpha, style = p.ringStroke)
        }
        g.depth.draw(this, alpha)
    }
    drawRoundRect(p.rim, Offset(g.oL, g.oT), Size(g.bw, g.bh), g.body.topLeftCornerRadius, alpha = 0.55f * alpha, style = p.rimStroke)
}

/** Vacío, versión inmediata (efectos, fantasma de arrastre). Sin brillo girando. */
fun DrawScope.drawVoidTile(
    topLeft: Offset, size: Size, base: Color, light: Color, dark: Color,
    cornerFraction: Float = 0.12f, alpha: Float = 1f, gapFraction: Float = 0.05f, swirlVariant: Int = 0,
) {
    drawCachedTile(
        BlockTileStyle.VOID, topLeft, size, base, light, dark, cornerFraction, gapFraction,
        swirlVariant, alpha, build = ::voidRecipe,
    )
}

/** Receta cacheada de la ruta inmediata (ver [drawCachedTile]): construida en el origen. */
private fun voidRecipe(k: TileKey): TileRecipe? {
    val g = voidGeometry(Offset.Zero, k.size, k.cornerFraction, k.gapFraction, k.variant) ?: return null
    val p = voidPaint(g, k.base, k.light, k.dark)
    return TileRecipe { scope, a -> scope.drawVoid(g, p, a) }
}

/** Vacío cacheado por tamaño (sin brillo girando desde el 2026-09-30). */
fun Modifier.voidTile(
    base: Color, light: Color, dark: Color, cornerFraction: Float = 0.12f,
    gapFraction: Float = 0.05f, swirlVariant: Int = 0,
): Modifier = this.drawWithCache {
    val g = voidGeometry(Offset.Zero, size, cornerFraction, gapFraction, swirlVariant)
        ?: return@drawWithCache onDrawBehind {}
    val paint = voidPaint(g, base, light, dark)
    onDrawBehind { drawVoid(g, paint, 1f) }
}
