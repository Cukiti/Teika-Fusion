package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.foundation.layout.Box
import com.cukiti.teikafusion.model.GameEvent
import androidx.compose.ui.graphics.graphicsLayer
import com.cukiti.teikafusion.model.Position
import kotlin.math.sin

/**
 * **El magma del Volcán** (2026-09-23) — las tres fases que pide el contrato de biomas:
 *
 *  1. TELEGRAFÍA — [MagmaLayer], fila que arde: la fila justo bajo la lava recibe su calor (brillo que
 *     sube desde la superficie) y suelta brasas; sus bloques TIEMBLAN más cuanto más calor llevan (el
 *     temblor lo aplica GameBoard a las propias celdas, leyendo [heat] en dibujo).
 *  2. IMPACTO — [MagmaForgeVFX]: el temple. Los bloques se ponen al rojo blanco y se enfrían.
 *  3. CONSECUENCIA — [MagmaLayer], magma: roca oscura con grietas de lava que laten. Se lee de un
 *     vistazo que ese bloque ya no es madera normal.
 *
 * Rendimiento: tiempo de [biomeClockSec] y calor leídos en DIBUJO; pinceles cacheados por tamaño.
 */
@Composable
fun MagmaLayer(
    magmaCells: Set<Position>,
    burningCells: Set<Position>,
    heat: State<Float>,
    cellPx: Float,
    modifier: Modifier = Modifier,
) {
    if (magmaCells.isEmpty() && burningCells.isEmpty()) return
    val emberCount = fxCount(3)
    Box(
        // Capa propia (auditoría de fluidez 2026-09-29): late cada fotograma todo el capítulo y, sin
        // capa, arrastraba a regrabar a los hermanos del tablero.
        modifier.fillMaxSize().graphicsLayer().drawWithCache {
            val rock = Brush.verticalGradient(
                listOf(Color(0xFF3A1A12), Color(0xFF1E0D09)), startY = 0f, endY = cellPx
            )
            val heatGlow = Brush.verticalGradient(
                listOf(Color(0xFFFF7A1A).copy(alpha = 0.85f), Color(0xFFFF3D00).copy(alpha = 0.25f), Color.Transparent),
                startY = 0f, endY = cellPx
            )
            val inset = cellPx * 0.06f
            val corner = CornerRadius(cellPx * 0.14f, cellPx * 0.14f)
            // Las grietas son deterministas (semilla por posición): se calculan UNA vez por conjunto de
            // celdas, no en cada fotograma. Por celda, 2 grietas × 5 vértices × (x, y).
            val magmaList = magmaCells.toList()
            val cracks = FloatArray(magmaList.size * MAGMA_CRACK_FLOATS)
            magmaList.forEachIndexed { ci, p -> buildMagmaCracks(p, cellPx, inset, cracks, ci * MAGMA_CRACK_FLOATS) }
            onDrawBehind {
                val t = biomeClockSec.floatValue
                val h = heat.value
                // ── Consecuencia: bloques de MAGMA ──
                for (ci in magmaList.indices) {
                    val p = magmaList[ci]
                    val ox = p.x * cellPx; val oy = p.y * cellPx
                    translate(ox, oy) {
                        drawRoundRect(rock, topLeft = Offset(inset, inset), size = Size(cellPx - 2 * inset, cellPx - 2 * inset), cornerRadius = corner)
                        val beat = 0.55f + 0.45f * sin(t * 2.4f + p.x * 1.3f + p.y * 0.7f)
                        val crack = Color(0xFFFFA23A).copy(alpha = 0.60f + 0.35f * beat)
                        val glow = Color(0xFFFF5A00).copy(alpha = 0.22f + 0.18f * beat)
                        // Dos grietas QUEBRADAS por celda, distintas en cada una (semilla por
                        // posición): cruzan la roca de borde a borde, con un halo de lava debajo.
                        val base = ci * MAGMA_CRACK_FLOATS
                        for (k in 0 until 2) {
                            val o = base + k * 10
                            for (seg in 1..4) {
                                val prev = Offset(cracks[o + (seg - 1) * 2], cracks[o + (seg - 1) * 2 + 1])
                                val next = Offset(cracks[o + seg * 2], cracks[o + seg * 2 + 1])
                                drawLine(glow, prev, next, cellPx * 0.11f, StrokeCap.Round)
                                drawLine(crack, prev, next, cellPx * (0.045f - seg * 0.005f), StrokeCap.Round)
                            }
                        }
                        drawRoundRect(Color(0xFFFF5A00), topLeft = Offset(inset, inset), size = Size(cellPx - 2 * inset, cellPx - 2 * inset),
                            cornerRadius = corner, alpha = 0.10f + 0.10f * beat)
                    }
                }
                // ── Telegrafía: la fila que ARDE ──
                burningCells.forEach { p ->
                    val ox = p.x * cellPx; val oy = p.y * cellPx
                    translate(ox, oy) {
                        val flick = 0.75f + 0.25f * sin(t * 11f + p.x * 2.1f)
                        drawRect(heatGlow, size = Size(cellPx, cellPx), alpha = (0.30f + 0.55f * h) * flick)
                        for (i in 0 until emberCount) {
                            val cyc = ((t * (0.9f + i * 0.23f)) + p.x * 0.37f + i * 0.31f) % 1f
                            val ex = cellPx * (0.2f + 0.6f * (((p.x + 1) * (i + 3) * 37) % 100) / 100f)
                            val ey = cellPx * (1f - cyc)
                            drawCircle(Color(0xFFFFB347), radius = cellPx * (0.035f + 0.03f * h),
                                center = Offset(ex, ey), alpha = (0.4f + 0.5f * h) * sin(cyc * 3.14159f))
                        }
                    }
                }
            }
        }
    )
}

/** Floats por celda de magma: 2 grietas × 5 vértices × (x, y). */
private const val MAGMA_CRACK_FLOATS = 20

/**
 * Las dos grietas quebradas de la celda [p], en coordenadas locales de la celda, escritas en [out] desde
 * [at]. Misma semilla y mismos pasos que antes se repetían en cada fotograma: mismos vértices exactos.
 */
private fun buildMagmaCracks(p: Position, cellPx: Float, inset: Float, out: FloatArray, at: Int) {
    for (k in 0 until 2) {
        var seed = p.x * 73 + p.y * 151 + k * 997
        fun rnd(): Float { seed = (seed * 1103515245 + 12345) and 0x7fffffff; return (seed % 1000) / 1000f }
        val vertical = (k + p.x + p.y) % 2 == 0
        var px = if (vertical) cellPx * (0.2f + 0.6f * rnd()) else inset
        var py = if (vertical) inset else cellPx * (0.2f + 0.6f * rnd())
        val o = at + k * 10
        out[o] = px; out[o + 1] = py
        for (seg in 1..4) {
            val f = seg / 4f
            val jitter = cellPx * (rnd() - 0.5f) * 0.34f
            if (vertical) {
                px = (px + jitter).coerceIn(inset, cellPx - inset); py = inset + (cellPx - 2 * inset) * f
            } else {
                px = inset + (cellPx - 2 * inset) * f; py = (py + jitter).coerceIn(inset, cellPx - inset)
            }
            out[o + seg * 2] = px; out[o + seg * 2 + 1] = py
        }
    }
}

/** Duración del temple (impacto). Dentro del tope de 1200 ms de los eventos de bioma. */
const val MAGMA_FORGE_MS = 760

/** IMPACTO: el temple — al rojo blanco, destello y enfriado en roca. Consume su evento al acabar. */
@Composable
fun MagmaForgeVFX(event: GameEvent.MagmaForge, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(MAGMA_FORGE_MS, easing = LinearEasing))
        onComplete()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val white = (1f - p / 0.35f).coerceIn(0f, 1f)          // 0–0,35: al rojo blanco
        val orange = sin((p * 3.14159f).coerceIn(0f, 3.14159f)) // luego naranja que se apaga
        event.cells.forEach { c ->
            val ox = c.x * cellPx; val oy = c.y * cellPx
            drawRect(Color(0xFFFFF4D6), topLeft = Offset(ox, oy), size = Size(cellPx, cellPx), alpha = 0.85f * white)
            drawRect(Color(0xFFFF6A00), topLeft = Offset(ox, oy), size = Size(cellPx, cellPx), alpha = 0.55f * orange * (1f - white))
            // Chispas que saltan hacia arriba, una por celda y lado.
            for (k in 0 until 2) {
                val dir = if (k == 0) -1f else 1f
                val sx = ox + cellPx * 0.5f + dir * cellPx * 0.9f * p
                val sy = oy + cellPx * 0.3f - cellPx * 1.4f * p + cellPx * 1.6f * p * p
                drawCircle(Color(0xFFFFC04D), radius = cellPx * 0.06f * (1f - p), center = Offset(sx, sy), alpha = (1f - p))
            }
        }
    }
}
