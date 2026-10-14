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
import kotlin.math.PI
import kotlin.math.sin

/**
 * **El bloque CONSTELACIÓN** (P-251 6/7, 2026-09-25): un trozo de cielo nocturno con la silueta
 * CUADRADA del Clásico (la forma no se toca; la personalidad va en la cara).
 *
 * - **Cara:** cielo que va del color de la ficha arriba a la noche abajo, una NEBULOSA suave y una
 *   CONSTELACIÓN de cuatro estrellas unidas por líneas finas. Hay cuatro dibujos distintos y cada
 *   celda del tablero lleva uno (`skyVariant`): el tablero entero parece un mapa del cielo.
 * - **Reposo:** las estrellas TITILAN, cada una a su ritmo, con el reloj compartido [biomeClockSec].
 *   Solo mientras [biomeClockRunning].
 * - **Rotura:** las estrellas salen disparadas como ESTRELLAS FUGACES con estela (`BatchLineCellShatterVFX`).
 *
 * Todo lo que no es tiempo se construye UNA vez por tamaño: cero asignaciones por frame.
 */
internal const val STAR_TWINKLE_PERIOD_SEC = 2.2f

private val SkyNight = Color(0xFF0B1030)

/** Los cuatro dibujos: cuatro estrellas cada uno, en fracciones de la cara. */
private val CONSTELLATIONS = arrayOf(
    floatArrayOf(0.22f, 0.30f, 0.46f, 0.20f, 0.66f, 0.42f, 0.80f, 0.72f),
    floatArrayOf(0.20f, 0.70f, 0.40f, 0.48f, 0.64f, 0.56f, 0.78f, 0.24f),
    floatArrayOf(0.26f, 0.22f, 0.30f, 0.60f, 0.62f, 0.74f, 0.76f, 0.36f),
    floatArrayOf(0.18f, 0.46f, 0.44f, 0.30f, 0.56f, 0.70f, 0.82f, 0.56f),
)

private class SkyGeometry(
    val clip: Path,
    val body: RoundRect,
    val stars: Array<Offset>, val starR: FloatArray,
    val lines: Path,
    val nebula: Offset,
    val oL: Float, val oT: Float, val bw: Float, val bh: Float,
) {
    /** Volumen común (P-283), construido una vez por tamaño. */
    val depth = TileDepth(oL, oT, bw, bh)
}

private fun skyGeometry(topLeft: Offset, size: Size, cornerFraction: Float, gapFraction: Float, variant: Int): SkyGeometry? {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return null
    val gap = minOf(w, h) * gapFraction
    val oL = topLeft.x + gap; val oT = topLeft.y + gap
    val bw = w - gap * 2f; val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val m = minOf(bw, bh)
    val r = m * cornerFraction
    val body = RoundRect(Rect(oL, oT, oL + bw, oT + bh), CornerRadius(r, r))
    val pts = CONSTELLATIONS[((variant % 4) + 4) % 4]
    val stars = Array(4) { i -> Offset(oL + bw * pts[i * 2], oT + bh * pts[i * 2 + 1]) }
    val lines = Path().apply {
        moveTo(stars[0].x, stars[0].y)
        for (i in 1 until 4) lineTo(stars[i].x, stars[i].y)
    }
    return SkyGeometry(
        clip = Path().apply { addRoundRect(body) },
        body = body,
        stars = stars, starR = floatArrayOf(m * 0.055f, m * 0.075f, m * 0.05f, m * 0.065f),
        lines = lines,
        nebula = Offset(oL + bw * 0.6f, oT + bh * 0.62f),
        oL = oL, oT = oT, bw = bw, bh = bh,
    )
}

private class SkyPaint(val sky: Brush, val nebula: Brush, val line: Color, val star: Color, val rim: Color, val lineStroke: Stroke, val rimStroke: Stroke)

private fun skyPaint(g: SkyGeometry, base: Color, light: Color, dark: Color) = SkyPaint(
    sky = Brush.verticalGradient(
        listOf(lerp(base, SkyNight, 0.18f), lerp(dark, SkyNight, 0.55f), SkyNight),
        startY = g.oT, endY = g.oT + g.bh
    ),
    nebula = Brush.radialGradient(
        listOf(light.copy(alpha = 0.45f), base.copy(alpha = 0f)),
        center = g.nebula, radius = minOf(g.bw, g.bh) * 0.5f
    ),
    line = lerp(light, Color.White, 0.5f),
    star = Color.White,
    rim = lerp(light, Color.White, 0.3f),
    lineStroke = Stroke(width = minOf(g.bw, g.bh) * 0.022f, cap = StrokeCap.Round),
    rimStroke = Stroke(width = g.bw * 0.03f),
)

private fun DrawScope.drawSkyStar(c: Offset, r: Float, glow: Color, alpha: Float) {
    drawCircle(glow, r * 2.2f, c, alpha = 0.25f * alpha)
    // Estrella de cuatro puntas: dos rombos finos cruzados.
    drawLine(Color.White, Offset(c.x - r * 1.6f, c.y), Offset(c.x + r * 1.6f, c.y), strokeWidth = r * 0.45f, cap = StrokeCap.Round, alpha = alpha)
    drawLine(Color.White, Offset(c.x, c.y - r * 1.6f), Offset(c.x, c.y + r * 1.6f), strokeWidth = r * 0.45f, cap = StrokeCap.Round, alpha = alpha)
    drawCircle(Color.White, r * 0.6f, c, alpha = alpha)
}

private inline fun DrawScope.drawSky(g: SkyGeometry, p: SkyPaint, glow: Color, alpha: Float, twinkle: (Int) -> Float) {
    clipPath(g.clip) {
        drawRect(p.sky, Offset(g.oL, g.oT), Size(g.bw, g.bh), alpha = alpha)
        drawRect(p.nebula, Offset(g.oL, g.oT), Size(g.bw, g.bh), alpha = alpha)
        drawPath(g.lines, p.line, alpha = 0.55f * alpha, style = p.lineStroke)
        for (i in g.stars.indices) drawSkyStar(g.stars[i], g.starR[i], glow, alpha * twinkle(i))
        g.depth.draw(this, alpha)
    }
    drawRoundRect(p.rim, Offset(g.oL, g.oT), Size(g.bw, g.bh), g.body.topLeftCornerRadius, alpha = 0.6f * alpha, style = p.rimStroke)
}

/** Constelación, versión inmediata (efectos, fantasma de arrastre). Estrellas quietas. */
fun DrawScope.drawConstellationTile(
    topLeft: Offset, size: Size, base: Color, light: Color, dark: Color,
    cornerFraction: Float = 0.12f, alpha: Float = 1f, gapFraction: Float = 0.05f, skyVariant: Int = 0,
) {
    drawCachedTile(
        BlockTileStyle.CONSTELLATION, topLeft, size, base, light, dark, cornerFraction, gapFraction,
        skyVariant, alpha, build = ::skyRecipe,
    )
}

/** Receta cacheada de la ruta inmediata (ver [drawCachedTile]): construida en el origen. */
private fun skyRecipe(k: TileKey): TileRecipe? {
    val g = skyGeometry(Offset.Zero, k.size, k.cornerFraction, k.gapFraction, k.variant) ?: return null
    val p = skyPaint(g, k.base, k.light, k.dark)
    return TileRecipe { scope, a -> scope.drawSky(g, p, k.light, a) { 1f } }
}

/** Constelación cacheada por tamaño, CON titileo. [skyVariant] elige el dibujo y desfasa el ritmo. */
fun Modifier.constellationTile(
    base: Color, light: Color, dark: Color, cornerFraction: Float = 0.12f,
    gapFraction: Float = 0.05f, skyVariant: Int = 0,
): Modifier = this.drawWithCache {
    val g = skyGeometry(Offset.Zero, size, cornerFraction, gapFraction, skyVariant)
        ?: return@drawWithCache onDrawBehind {}
    val paint = skyPaint(g, base, light, dark)
    onDrawBehind {
        if (!biomeClockRunning.value) {
            drawSky(g, paint, light, 1f) { 1f }
            return@onDrawBehind
        }
        val t = biomeClockSec.floatValue / STAR_TWINKLE_PERIOD_SEC * 2f * PI.toFloat()
        drawSky(g, paint, light, 1f) { i -> 0.55f + 0.45f * sin(t * (1f + i * 0.23f) + skyVariant * 1.1f + i * 1.7f) }
    }
}
