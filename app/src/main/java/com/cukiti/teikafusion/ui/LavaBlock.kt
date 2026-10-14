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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.sin

/**
 * **El bloque LAVA** (P-251 3/7, 2026-09-25): roca volcánica recién enfriada con la silueta CUADRADA
 * del Clásico (la forma no se toca; la personalidad va en la cara).
 *
 * - **Cara:** cuatro PLACAS de roca oscura —teñidas del color de la ficha para que los cinco colores
 *   se sigan distinguiendo— separadas por GRIETAS por las que asoma el magma, que es el color de la
 *   ficha encendido. Cada celda del tablero parte la roca por un sitio distinto (`crackVariant`).
 * - **Reposo:** las grietas LATEN, más y menos incandescentes, con el reloj compartido [biomeClockSec]
 *   y un desfase por celda para que el latido recorra el tablero. Se lee en DIBUJO y solo mientras
 *   [biomeClockRunning]; con «reducir efectos» la roca queda quieta.
 * - **Rotura:** estalla en ASCUAS que suben y caen y en trozos de roca (ver `BatchLineCellShatterVFX`).
 *
 * Todo lo que no es tiempo se construye UNA vez por tamaño: cero asignaciones por frame.
 */
internal const val LAVA_PULSE_PERIOD_SEC = 2.6f

private class LavaGeometry(
    val clip: Path,
    val body: RoundRect,
    val cracks: Path,
    val specks: Array<Offset>, val speckR: Float,
    val oL: Float, val oT: Float, val bw: Float, val bh: Float,
) {
    /** Volumen común (P-283), construido una vez por tamaño. */
    val depth = TileDepth(oL, oT, bw, bh)
}

private fun lavaGeometry(topLeft: Offset, size: Size, cornerFraction: Float, gapFraction: Float, variant: Int): LavaGeometry? {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return null
    val gap = minOf(w, h) * gapFraction
    val oL = topLeft.x + gap; val oT = topLeft.y + gap
    val bw = w - gap * 2f; val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val r = minOf(bw, bh) * cornerFraction
    // Dónde se parte la roca: cuatro variantes (espejadas en X y/o en Y).
    val v = ((variant % 4) + 4) % 4
    fun px(f: Float) = if (v and 1 == 1) oL + bw * (1f - f) else oL + bw * f
    fun py(f: Float) = if (v and 2 == 2) oT + bh * (1f - f) else oT + bh * f
    // Una grieta principal en zigzag que baja, y dos ramas que salen de ella: se lee como roca
    // partida, no como una cruz.
    val cracks = Path().apply {
        moveTo(px(0.38f), py(-0.02f)); lineTo(px(0.47f), py(0.18f)); lineTo(px(0.40f), py(0.33f))
        lineTo(px(0.55f), py(0.50f)); lineTo(px(0.48f), py(0.70f)); lineTo(px(0.60f), py(1.02f))
        moveTo(px(0.40f), py(0.33f)); lineTo(px(0.22f), py(0.40f)); lineTo(px(0.12f), py(0.58f)); lineTo(px(-0.02f), py(0.62f))
        moveTo(px(0.55f), py(0.50f)); lineTo(px(0.74f), py(0.44f)); lineTo(px(0.86f), py(0.26f)); lineTo(px(1.02f), py(0.22f))
        moveTo(px(0.48f), py(0.70f)); lineTo(px(0.70f), py(0.78f)); lineTo(px(0.80f), py(0.92f))
    }
    val specks = arrayOf(
        Offset(px(0.20f), py(0.18f)), Offset(px(0.78f), py(0.64f)), Offset(px(0.24f), py(0.82f)),
        Offset(px(0.70f), py(0.12f)),
    )
    val body = RoundRect(Rect(oL, oT, oL + bw, oT + bh), CornerRadius(r, r))
    return LavaGeometry(
        clip = Path().apply { addRoundRect(body) },
        body = body, cracks = cracks, specks = specks, speckR = minOf(bw, bh) * 0.035f,
        oL = oL, oT = oT, bw = bw, bh = bh,
    )
}

private val Basalt = Color(0xFF1B1412)

private class LavaPaint(
    val rock: Brush, val magma: Color, val core: Color, val rim: Color, val edge: Color,
    val crackOuter: Stroke, val crackCore: Stroke, val halo: Stroke, val edgeStroke: Stroke,
)

private fun lavaPaint(g: LavaGeometry, base: Color, light: Color, dark: Color): LavaPaint {
    val m = minOf(g.bw, g.bh)
    return LavaPaint(
        rock = Brush.verticalGradient(
            listOf(lerp(base, Basalt, 0.50f), lerp(dark, Basalt, 0.62f)),
            startY = g.oT, endY = g.oT + g.bh
        ),
        magma = base,
        core = lerp(light, Color.White, 0.35f),
        rim = lerp(base, Color.White, 0.2f),
        edge = lerp(dark, Color.Black, 0.45f),
        crackOuter = Stroke(width = m * 0.10f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        crackCore = Stroke(width = m * 0.04f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        halo = Stroke(width = m * 0.20f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        edgeStroke = Stroke(width = g.bw * 0.03f),
    )
}

private fun DrawScope.drawLava(g: LavaGeometry, p: LavaPaint, alpha: Float) {
    clipPath(g.clip) {
        drawRect(p.rock, Offset(g.oL, g.oT), Size(g.bw, g.bh), alpha = alpha)
        // Poros del basalto: motas claras del color de la ficha.
        for (c in g.specks) drawCircle(p.rim, g.speckR, c, alpha = 0.28f * alpha)
        // La grieta: magma del color de la ficha y un núcleo casi blanco.
        drawPath(g.cracks, p.magma, alpha = alpha, style = p.crackOuter)
        drawPath(g.cracks, p.core, alpha = 0.9f * alpha, style = p.crackCore)
        // Luz de arriba en el canto: la roca tiene volumen.
        drawRect(Color.White, Offset(g.oL, g.oT), Size(g.bw, g.bh * 0.08f), alpha = 0.10f * alpha)
        g.depth.draw(this, alpha)
    }
    drawRoundRect(
        p.edge, Offset(g.oL, g.oT), Size(g.bw, g.bh), g.body.topLeftCornerRadius,
        alpha = 0.7f * alpha, style = p.edgeStroke
    )
}

/**
 * Bloque de lava, versión inmediata: la usan `drawStyledTile` y todo lo que pinta bloques en
 * movimiento. Sin latido: un bloque que vuela no necesita respirar.
 */
fun DrawScope.drawLavaTile(
    topLeft: Offset, size: Size, base: Color, light: Color, dark: Color,
    cornerFraction: Float = 0.12f, alpha: Float = 1f, gapFraction: Float = 0.05f, crackVariant: Int = 0,
) {
    drawCachedTile(
        BlockTileStyle.LAVA, topLeft, size, base, light, dark, cornerFraction, gapFraction,
        ((crackVariant % 4) + 4) % 4, alpha, build = ::lavaRecipe,
    )
}

/** Receta cacheada de la ruta inmediata (ver [drawCachedTile]): construida en el origen. */
private fun lavaRecipe(k: TileKey): TileRecipe? {
    val g = lavaGeometry(Offset.Zero, k.size, k.cornerFraction, k.gapFraction, k.variant) ?: return null
    val p = lavaPaint(g, k.base, k.light, k.dark)
    return TileRecipe { scope, a -> scope.drawLava(g, p, a) }
}

/** Bloque de lava cacheado por tamaño, CON latido. [crackVariant] parte la roca y desfasa el latido. */
fun Modifier.lavaTile(
    base: Color, light: Color, dark: Color, cornerFraction: Float = 0.12f,
    gapFraction: Float = 0.05f, crackVariant: Int = 0,
): Modifier = this.drawWithCache {
    val g = lavaGeometry(Offset.Zero, size, cornerFraction, gapFraction, crackVariant)
        ?: return@drawWithCache onDrawBehind {}
    val paint = lavaPaint(g, base, light, dark)
    onDrawBehind {
        drawLava(g, paint, 1f)
        if (!biomeClockRunning.value) return@onDrawBehind
        val phase = biomeClockSec.floatValue / LAVA_PULSE_PERIOD_SEC * 2f * PI.toFloat() - crackVariant * 0.7f
        val pulse = 0.5f + 0.5f * sin(phase)
        // El latido: un halo del magma que se abre y se cierra alrededor de la grieta.
        clipPath(g.clip) {
            drawPath(g.cracks, paint.magma, alpha = 0.10f + 0.30f * pulse, style = paint.halo)
        }
    }
}
