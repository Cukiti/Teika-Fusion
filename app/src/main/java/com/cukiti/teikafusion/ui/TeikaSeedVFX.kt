package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.cukiti.teikafusion.model.CellContent
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.Position
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * **EL BLOQUE-TEIKA** (P-256, 2026-09-23 · metálico P-284 · CÁPSULA GASHAPON el 2026-09-25, decisión
 * del usuario: la placa metálica pasó a ser el bloque Metal de la vitrina y el bloque-Teika estrenó
 * diseño propio para no confundirse con ningún skin).
 *
 * Es un bloque ESPECIAL que sustituye a la madera de su casilla: una placa nocturna ocupa la casilla
 * entera (tapa la madera sea cual sea el skin; la bandeja no la pinta) y encima una CÁPSULA DE PREMIO:
 * cúpula transparente arriba con la carita de un Teika que parpadea —el jugador VE que ahí dentro hay
 * un Teika— y abajo franjas de los cuatro colores de Teika (azul, amarillo, morado, verde), con su
 * junta blanca en medio. La cápsula se BALANCEA. Al romperse ([TeikaSeedBurstVFX]) se abre por la
 * mitad: la cúpula sale despedida hacia arriba, la base cae y salta confeti de los cuatro colores.
 *
 * El reloj es el compartido ([biomeClockSec]), leído en DIBUJO; con «reducir efectos» la cápsula queda
 * quieta. Trazados y pinceles se construyen una vez por tamaño: cero asignaciones por frame.
 */
internal val TEIKA_SPIKE_COLORS = arrayOf(
    Color(0xFF3FA9FF), // azul
    Color(0xFFFFD23F), // amarillo
    Color(0xFFB266FF), // morado
    Color(0xFF4CD964), // verde
)

internal class SeedGeometry(s: Float) {
    val half = s * 0.5f
    val plateCorner = androidx.compose.ui.geometry.CornerRadius(s * 0.12f, s * 0.12f)
    val plateTopLeft = Offset(-half, -half)
    val plateSize = Size(s, s)
    val plate = Brush.verticalGradient(listOf(Color(0xFF26306A), Color(0xFF111735)), startY = -half, endY = half)
    val plateEdge = Stroke(width = s * 0.04f)

    /** La cápsula: un círculo de radio [r], centrado un poco abajo para que el balanceo quede bonito. */
    val r = s * 0.39f
    val center = Offset(0f, s * 0.03f)
    val capsuleClip = androidx.compose.ui.graphics.Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(center.x - r, center.y - r, center.x + r, center.y + r))
    }
    val stripeW = r * 2f / 4f
    val baseShade = Brush.verticalGradient(
        listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)), startY = center.y, endY = center.y + r
    )
    val dome = Brush.radialGradient(
        listOf(Color(0x66FFFFFF), Color(0x3389D4FF), Color(0x552C4A8C)),
        center = Offset(center.x - r * 0.3f, center.y - r * 0.5f), radius = r * 1.3f
    )
    val seamH = s * 0.07f
    val outline = Stroke(width = s * 0.03f)
    val shineTopLeft = Offset(center.x - r * 0.78f, center.y - r * 0.78f)
    val shineSize = Size(r * 1.56f, r * 1.56f)
    val shineStroke = Stroke(width = s * 0.045f, cap = StrokeCap.Round)

    // La carita del Teika T1 (azul), dentro de la cúpula.
    val faceR = r * 0.52f
    val faceCenter = Offset(center.x, center.y - r * 0.42f)
    val body = Brush.radialGradient(
        listOf(Color(0xFFD4F4FF), Color(0xFF7FDBFF), Color(0xFF3A8FB8)),
        center = Offset(faceCenter.x - faceR * 0.35f, faceCenter.y - faceR * 0.4f), radius = faceR * 1.5f
    )
    val eyeDx = faceR * 0.40f
    val eyeY = faceR * 0.02f
    val eyeR = faceR * 0.26f
    val pupilR = faceR * 0.14f
    val blinkW = s * 0.028f
    val mouthTopLeft = Offset(-faceR * 0.22f, faceR * 0.18f)
    val mouthSize = Size(faceR * 0.44f, faceR * 0.34f)
    val mouthStroke = Stroke(width = s * 0.022f, cap = StrokeCap.Round)
}

/** La cápsula (sin la placa), centrada en el origen actual. [openUp]/[openDown] separan las mitades. */
private fun DrawScope.drawCapsule(
    g: SeedGeometry, s: Float, t: Float, live: Boolean, alpha: Float,
    openUp: Float = 0f, openDown: Float = 0f,
) {
    val c = g.center
    // ── Mitad de abajo: las cuatro franjas de color, recortadas a la cápsula. ──
    translate(0f, openDown) {
        clipPath(g.capsuleClip) {
            clipRect(c.x - g.r, c.y, c.x + g.r, c.y + g.r) {
                for (i in 0 until 4) {
                    drawRect(TEIKA_SPIKE_COLORS[i], Offset(c.x - g.r + i * g.stripeW, c.y), Size(g.stripeW + 1f, g.r), alpha = alpha)
                }
                drawRect(g.baseShade, Offset(c.x - g.r, c.y), Size(g.r * 2f, g.r), alpha = alpha)
            }
        }
        clipRect(c.x - g.r - 2f, c.y, c.x + g.r + 2f, c.y + g.r + 2f) {
            drawCircle(Color(0xFF0B1030), g.r, c, alpha = 0.8f * alpha, style = g.outline)
        }
    }
    // ── Mitad de arriba: cúpula transparente con el Teika dentro. ──
    translate(0f, -openUp) {
        clipRect(c.x - g.r - 2f, c.y - g.r - 2f, c.x + g.r + 2f, c.y) {
            clipPath(g.capsuleClip) {
                drawRect(g.dome, Offset(c.x - g.r, c.y - g.r), Size(g.r * 2f, g.r), alpha = alpha)
                val bob = if (live) sin(t * 2.4f) * s * 0.012f else 0f
                translate(g.faceCenter.x, g.faceCenter.y + bob) {
                    drawCircle(g.body, g.faceR, Offset.Zero, alpha = alpha)
                    val blink = live && (t % 3.4f) < 0.15f
                    for (side in 0..1) {
                        val ex = if (side == 0) -g.eyeDx else g.eyeDx
                        if (blink) {
                            drawLine(Color(0xFF16263C), Offset(ex - g.eyeR, g.eyeY), Offset(ex + g.eyeR, g.eyeY),
                                strokeWidth = g.blinkW, cap = StrokeCap.Round, alpha = alpha)
                        } else {
                            drawCircle(Color.White, g.eyeR, Offset(ex, g.eyeY), alpha = alpha)
                            drawCircle(Color(0xFF17171F), g.pupilR, Offset(ex, g.eyeY + g.eyeR * 0.25f), alpha = alpha)
                        }
                    }
                    drawArc(Color(0xFF16263C), 20f, 140f, false, g.mouthTopLeft, g.mouthSize, alpha = alpha, style = g.mouthStroke)
                }
            }
            drawCircle(Color(0xFF0B1030), g.r, c, alpha = 0.8f * alpha, style = g.outline)
            drawArc(Color.White, 200f, 70f, false, g.shineTopLeft, g.shineSize, alpha = 0.85f * alpha, style = g.shineStroke)
        }
        // La junta: una franja blanca en el ecuador (va con la cúpula al abrirse).
        drawRoundRect(
            Color.White, Offset(c.x - g.r * 0.98f, c.y - g.seamH / 2f), Size(g.r * 1.96f, g.seamH),
            androidx.compose.ui.geometry.CornerRadius(g.seamH / 2f, g.seamH / 2f), alpha = 0.95f * alpha
        )
    }
}

/** El bloque entero (placa + cápsula que se balancea), centrado en el origen actual. */
internal fun DrawScope.drawSeed(g: SeedGeometry, s: Float, t: Float, live: Boolean, alpha: Float = 1f) {
    drawRoundRect(g.plate, g.plateTopLeft, g.plateSize, g.plateCorner, alpha = alpha)
    drawRoundRect(Color(0xFF3B4890), g.plateTopLeft, g.plateSize, g.plateCorner, alpha = 0.9f * alpha, style = g.plateEdge)
    val sway = if (live) 7f * sin(t * 2.6f) else 0f
    rotate(degrees = sway, pivot = Offset(g.center.x, g.center.y + g.r)) {
        drawCapsule(g, s, t, live, alpha)
    }
}

/**
 * Versión bandeja. La ficha NO pinta la madera de esta casilla (`PieceView`): la placa ocupa su sitio.
 */
fun Modifier.teikaSeedTile(animationsOn: Boolean): Modifier = this.drawWithCache {
    val s = size.minDimension
    val g = SeedGeometry(s)
    onDrawBehind {
        val t = if (animationsOn) biomeClockSec.floatValue else 0f
        translate(size.width / 2f, size.height / 2f) { drawSeed(g, s, t, animationsOn) }
    }
}

/** Los bloques-Teika puestos en el tablero: la placa tapa la casilla entera, madera incluida. */
@Composable
fun TeikaSeedLayer(
    grid: Map<Position, CellContent>,
    cellSize: Dp,
    animationsOn: Boolean,
    // (2026-09-25) Las casillas cuyo bloque aún VUELA hacia ellas: la cápsula la dibuja el vuelo
    // (WoodFallVFX/BlockFleeVFX) y aparece aquí al encajar. Antes saltaba al destino al instante.
    hidden: Set<Position> = emptySet(),
) {
    val seeds = remember(grid, hidden) {
        grid.entries.filter { (it.value as? CellContent.Wood)?.teikaSeed == true && it.key !in hidden }.map { it.key }
    }
    if (seeds.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val g = remember(cellPx) { SeedGeometry(cellPx) }
    Canvas(Modifier.fillMaxSize()) {
        val t = if (animationsOn) biomeClockSec.floatValue else 0f
        seeds.forEach { p ->
            // Desfase por casilla: cada cápsula se balancea a su ritmo.
            translate((p.x + 0.5f) * cellPx, (p.y + 0.5f) * cellPx) { drawSeed(g, cellPx, t + (p.x * 3 + p.y) * 0.37f, animationsOn) }
        }
    }
}

private const val SEED_BURST_MS = 750
private const val SEED_CONFETTI = 14

/**
 * **SE ABRE LA CÁPSULA** (2026-09-25): la cúpula sale despedida hacia arriba girando, la base cae, la
 * placa se desvanece, salta confeti de los cuatro colores y un anillo marca dónde nace el Teika. Más
 * tier = anillo más grande: el jugador VE que su doble línea le ha dado algo mejor.
 */
@Composable
fun TeikaSeedBurstVFX(
    events: List<GameEvent.TeikaSeedBurst>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
    animationsOn: Boolean = true,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val g = remember(cellPx) { SeedGeometry(cellPx) }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                delay(if(animationsOn) event.delayMs.toLong() else 0L)
                clock.animateTo(1f, tween(if(animationsOn) SEED_BURST_MS else 120, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val k = clock.value
                if (k <= 0f || k >= 1f) return@Canvas
                val center = Offset((event.position.x + 0.5f) * cellPx, (event.position.y + 0.5f) * cellPx)
                val a = 1f - k
                if(!animationsOn) {
                    drawCircle(Color(0xFF92FFE8),radius=cellPx*.36f,center=center,alpha=a,style=Stroke(cellPx*.035f))
                    return@Canvas
                }
                val grow = 1f + 0.35f * (event.tier - 1)
                // 1 · La placa se apaga y la cápsula se abre: cúpula arriba girando, base abajo.
                translate(center.x, center.y) {
                    drawRoundRect(g.plate, g.plateTopLeft, g.plateSize, g.plateCorner, alpha = (1f - k * 2.5f).coerceIn(0f, 1f))
                    rotate(degrees = -30f * k, pivot = g.center) {
                        drawCapsule(g, cellPx, 0f, false, a,
                            openUp = cellPx * 1.1f * k - cellPx * 0.6f * k * k * 0f,
                            openDown = cellPx * 0.9f * k * k)
                    }
                }
                // 2 · Destello y anillo de los cuatro colores donde nace el Teika.
                drawCircle(Color.White, radius = cellPx * (0.25f + 0.45f * k) * grow, center = center, alpha = 0.8f * a * a)
                drawCircle(
                    TEIKA_SPIKE_COLORS[((k * 8f).toInt()) % 4],
                    radius = cellPx * (0.4f + 1.3f * k) * grow, center = center,
                    style = Stroke(width = cellPx * 0.10f * a), alpha = a
                )
                // 3 · Confeti de los cuatro colores: sale disparado y la gravedad lo curva.
                for (i in 0 until SEED_CONFETTI) {
                    val ang = (i * 360f / SEED_CONFETTI + 13f) * PI.toFloat() / 180f
                    val d = cellPx * 1.25f * k * grow
                    val px = center.x + cos(ang) * d
                    val py = center.y + sin(ang) * d + cellPx * 0.8f * k * k
                    val w = cellPx * 0.10f * (1f - 0.4f * k)
                    rotate(degrees = i * 47f + k * 360f, pivot = Offset(px, py)) {
                        drawRect(TEIKA_SPIKE_COLORS[i % 4], Offset(px - w, py - w * 0.5f), Size(w * 2f, w), alpha = a)
                    }
                }
            }
        }
    }
}
