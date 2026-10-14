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
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * **El bloque ESPECTRO** (P-251 5/7, 2026-09-25): un fantasmita encerrado en la silueta CUADRADA del
 * Clásico (la forma no se toca; la personalidad va en la cara).
 *
 * - **Cara:** fondo nocturno teñido del color de la ficha, una NIEBLA luminosa de ese color en el
 *   centro —es lo que hace que los cinco colores se distingan— y una banda de ECTOPLASMA ondulada
 *   abajo. Arriba, dos OJITOS: pequeños a propósito, para que la estrella o el cronómetro que se
 *   pintan encima se sigan leyendo.
 * - **Reposo:** la niebla RESPIRA y los ojos PARPADEAN de vez en cuando, cada celda a su ritmo, con
 *   el reloj compartido [biomeClockSec]. Solo mientras [biomeClockRunning].
 * - **Rotura:** se deshace en VOLUTAS que suben ondulando y se desvanecen (`BatchLineCellShatterVFX`).
 *
 * Todo lo que no es tiempo se construye UNA vez por tamaño: cero asignaciones por frame.
 */
internal const val SPECTRE_BREATH_PERIOD_SEC = 3.0f
internal const val SPECTRE_BLINK_PERIOD_SEC = 4.6f

private val SpectreNight = Color(0xFF14102A)

private class SpectreGeometry(
    val clip: Path,
    val body: RoundRect,
    val ecto: Path,
    val eyeL: Offset, val eyeR: Offset, val eyeW: Float, val eyeH: Float,
    val oL: Float, val oT: Float, val bw: Float, val bh: Float,
) {
    /** Volumen común (P-283), construido una vez por tamaño. */
    val depth = TileDepth(oL, oT, bw, bh)
}

private fun spectreGeometry(topLeft: Offset, size: Size, cornerFraction: Float, gapFraction: Float, variant: Int): SpectreGeometry? {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return null
    val gap = minOf(w, h) * gapFraction
    val oL = topLeft.x + gap; val oT = topLeft.y + gap
    val bw = w - gap * 2f; val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val m = minOf(bw, bh)
    val r = m * cornerFraction
    val body = RoundRect(Rect(oL, oT, oL + bw, oT + bh), CornerRadius(r, r))
    // El ectoplasma: tres ondas a lo ancho, un poco desplazadas en cada variante.
    val shift = floatArrayOf(0f, 0.08f, -0.06f)[((variant % 3) + 3) % 3]
    val y0 = oT + bh * 0.74f
    val amp = bh * 0.06f
    val ecto = Path().apply {
        moveTo(oL - bw * 0.1f, oT + bh * 1.1f)
        lineTo(oL - bw * 0.1f, y0)
        var x = oL - bw * 0.1f + bw * shift
        var up = true
        val step = bw / 6f
        while (x < oL + bw * 1.1f) {
            quadraticTo(x + step / 2f, if (up) y0 - amp else y0 + amp, x + step, y0)
            x += step; up = !up
        }
        lineTo(oL + bw * 1.1f, oT + bh * 1.1f)
        close()
    }
    val eyeY = oT + bh * 0.30f
    return SpectreGeometry(
        clip = Path().apply { addRoundRect(body) },
        body = body, ecto = ecto,
        eyeL = Offset(oL + bw * 0.36f, eyeY), eyeR = Offset(oL + bw * 0.64f, eyeY),
        eyeW = m * 0.10f, eyeH = m * 0.14f,
        oL = oL, oT = oT, bw = bw, bh = bh,
    )
}

private class SpectrePaint(val night: Brush, val mist: Brush, val ecto: Color, val eye: Color, val rim: Color, val rimStroke: Stroke)

private fun spectrePaint(g: SpectreGeometry, base: Color, light: Color, dark: Color) = SpectrePaint(
    night = Brush.verticalGradient(
        listOf(lerp(base, SpectreNight, 0.45f), lerp(dark, SpectreNight, 0.62f)),
        startY = g.oT, endY = g.oT + g.bh
    ),
    mist = Brush.radialGradient(
        listOf(lerp(light, Color.White, 0.15f), base.copy(alpha = 0.55f), base.copy(alpha = 0f)),
        center = Offset(g.oL + g.bw * 0.5f, g.oT + g.bh * 0.5f), radius = minOf(g.bw, g.bh) * 0.62f
    ),
    ecto = lerp(light, Color.White, 0.3f),
    eye = SpectreNight,
    rim = lerp(light, Color.White, 0.4f),
    rimStroke = Stroke(width = g.bw * 0.03f),
)

private fun DrawScope.drawSpectre(g: SpectreGeometry, p: SpectrePaint, alpha: Float, mistAlpha: Float, eyeOpen: Float) {
    clipPath(g.clip) {
        drawRect(p.night, Offset(g.oL, g.oT), Size(g.bw, g.bh), alpha = alpha)
        drawRect(p.mist, Offset(g.oL, g.oT), Size(g.bw, g.bh), alpha = mistAlpha * alpha)
        drawPath(g.ecto, p.ecto, alpha = 0.42f * alpha)
        // Los ojos: óvalos oscuros con su brillo; al parpadear se aplastan hasta una rayita.
        val eh = (g.eyeH * eyeOpen).coerceAtLeast(g.eyeH * 0.12f)
        for (e in 0..1) {
            val c = if (e == 0) g.eyeL else g.eyeR
            drawOval(p.eye, Offset(c.x - g.eyeW / 2f, c.y - eh / 2f), Size(g.eyeW, eh), alpha = 0.9f * alpha)
            if (eyeOpen > 0.5f) {
                drawCircle(Color.White, g.eyeW * 0.2f, Offset(c.x - g.eyeW * 0.15f, c.y - eh * 0.2f), alpha = 0.85f * alpha)
            }
        }
        g.depth.draw(this, alpha)
    }
    drawRoundRect(p.rim, Offset(g.oL, g.oT), Size(g.bw, g.bh), g.body.topLeftCornerRadius, alpha = 0.55f * alpha, style = p.rimStroke)
}

/** Espectro, versión inmediata (efectos, fantasma de arrastre). Ojos abiertos, niebla quieta. */
fun DrawScope.drawSpectreTile(
    topLeft: Offset, size: Size, base: Color, light: Color, dark: Color,
    cornerFraction: Float = 0.14f, alpha: Float = 1f, gapFraction: Float = 0.05f, mistVariant: Int = 0,
) {
    drawCachedTile(
        BlockTileStyle.SPECTRE, topLeft, size, base, light, dark, cornerFraction, gapFraction,
        mistVariant, alpha, build = ::spectreRecipe,
    )
}

/** Receta cacheada de la ruta inmediata (ver [drawCachedTile]): construida en el origen. */
private fun spectreRecipe(k: TileKey): TileRecipe? {
    val g = spectreGeometry(Offset.Zero, k.size, k.cornerFraction, k.gapFraction, k.variant) ?: return null
    val p = spectrePaint(g, k.base, k.light, k.dark)
    return TileRecipe { scope, a -> scope.drawSpectre(g, p, a, mistAlpha = 0.85f, eyeOpen = 1f) }
}

/** Espectro cacheado por tamaño, CON respiración y parpadeo. [mistVariant] desfasa a cada celda. */
fun Modifier.spectreTile(
    base: Color, light: Color, dark: Color, cornerFraction: Float = 0.14f,
    gapFraction: Float = 0.05f, mistVariant: Int = 0,
): Modifier = this.drawWithCache {
    val g = spectreGeometry(Offset.Zero, size, cornerFraction, gapFraction, mistVariant)
        ?: return@drawWithCache onDrawBehind {}
    val paint = spectrePaint(g, base, light, dark)
    onDrawBehind {
        if (!biomeClockRunning.value) {
            drawSpectre(g, paint, 1f, mistAlpha = 0.85f, eyeOpen = 1f)
            return@onDrawBehind
        }
        val t = biomeClockSec.floatValue
        val breath = 0.5f + 0.5f * sin(t / SPECTRE_BREATH_PERIOD_SEC * 2f * PI.toFloat() - mistVariant * 0.8f)
        // Parpadeo: cerrados solo en una franja corta del periodo, y cada celda en su momento.
        val blinkCycle = t / SPECTRE_BLINK_PERIOD_SEC + mistVariant * 0.29f
        val local = blinkCycle - floor(blinkCycle)
        val eyeOpen = if (local < 0.05f) kotlin.math.abs(local / 0.025f - 1f) else 1f
        drawSpectre(g, paint, 1f, mistAlpha = 0.65f + 0.35f * breath, eyeOpen = eyeOpen)
    }
}
