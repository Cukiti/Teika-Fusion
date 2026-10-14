package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.TargetColor
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

// Fases del recolectado de estrella (mejora de "presentación"):
//  1. PRESENT  — la estrella CRECE desde el tamaño exacto que tenía en el bloque hasta destacarse,
//                gira y muestra un halo brillante. Se queda un instante para que el jugador la VEA.
//  2. FLIGHT   — desde ahí vuela en arco hasta su contador del panel de objetivo, encogiéndose.
private const val PRESENT_HOLD_MS = 120L // pausa de exhibición antes de volar
private const val FLIGHT_MS = 560    // vuelo al panel
/** Cuánto se agranda la estrella respecto a su tamaño en el bloque durante la presentación. */
private const val PRESENT_GROW = 1.35f

/**
 * Estrella PNG real. Al romper un bloque con estrella, primero SE PRESENTA (crece con rebote y halo
 * sobre el bloque), y luego vuela en arco Bézier desde [fromPx] hasta [toPx] (su contador de objetivo).
 *
 * CONTINUIDAD (fix 2026-07-20): arranca al tamaño y en la posición EXACTOS de la gema dibujada en el
 * bloque — `skin.starScale` y `skin.starOffsetXFraction/YFraction`, los mismos que usan CellView y el
 * shatter. Antes usaba una constante 1.7× fija y una escala que subía desde 0, así que la gema del
 * bloque desaparecía y "brotaba" otra distinta, pequeña, desde el centro de la celda: se leía como si
 * la del bloque fuese un señuelo y la de verdad saliera de debajo.
 */
@Composable
fun TargetCollectVFX(
    fromPx: Offset,
    toPx: Offset,
    targetColor: TargetColor,
    cellSizePx: Float,
    delayMs: Int = 0,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val skin = LocalBlockSkin.current
    // Origen REAL de la gema en el bloque (no el centro geométrico de la celda): el bisel/labio de
    // cada skin la desplaza, y el vuelo debe partir justo de ahí para que el relevo sea invisible.
    val originPx = Offset(
        fromPx.x + cellSizePx * skin.starOffsetXFraction,
        fromPx.y + cellSizePx * skin.starOffsetYFraction
    )
    // fase 1: presentación. Arranca en 1 = tamaño exacto en el bloque (nunca en 0: eso hacía
    // "aparecer" una estrella nueva desde la nada).
    val appear   = remember { Animatable(1f) }
    val progress = remember { Animatable(0f) }   // fase 2: vuelo al contador (0→1)
    val rotation = remember { Animatable(0f) }
    val burst    = remember { Animatable(0f) }   // esquirlas que estallan en el ORIGEN (estilo Block Blast)

    LaunchedEffect(Unit) {
        if (delayMs > 0) delay(delayMs.toLong())
        // Estallido de chispas en el bloque roto (cobrar objetivo = premio) — arranca al instante.
        launch { burst.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
        // Gira a lo largo de TODA la vida (presentación + vuelo) → sensación de estrella "viva".
        launch { rotation.animateTo(900f, tween(FLIGHT_MS)) }
        // ── Fase 1: PRESENTACIÓN — crece con rebote DESDE el tamaño del bloque y se exhibe ──
        appear.animateTo(PRESENT_GROW, spring(dampingRatio = 0.42f, stiffness = 380f))
        delay(PRESENT_HOLD_MS)
        // ── Fase 2: VUELO — arco Bézier hasta el contador del panel de objetivo ──
        coroutineScope {
            launch { progress.animateTo(1f, tween(FLIGHT_MS, easing = FastOutSlowInEasing)) }
        }
        onComplete()
    }

    val a   = appear.value       // 1→PRESENT_GROW presentación
    val t   = progress.value     // 0→1 vuelo
    val rot = rotation.value

    // Posición: durante la presentación se queda clavada en la gema del bloque (t=0 → originPx). En
    // el vuelo describe un arco cuadrático que sube hacia el contador del objetivo.
    val midX = (originPx.x + toPx.x) / 2f
    val midY = minOf(originPx.y, toPx.y) - 120f
    val x = (1 - t) * (1 - t) * originPx.x + 2 * (1 - t) * t * midX + t * t * toPx.x
    val y = (1 - t) * (1 - t) * originPx.y + 2 * (1 - t) * t * midY + t * t * toPx.y

    // Escala: la base ES el tamaño de la gema en el bloque (skin.starScale). `a` la agranda para
    // lucirla y el vuelo luego la encoge de camino al contador.
    val initialSizePx = cellSizePx * skin.starScale
    val flightScale  = 1f - t * 0.72f
    val currentScale = (a * flightScale).coerceAtLeast(0.22f)
    // Alpha: totalmente visible salvo el último tramo del vuelo (se funde al aterrizar en el contador).
    val alpha = if (t > 0.80f) ((1f - t) / 0.20f).coerceIn(0f, 1f) else 1f
    // Halo: brilla al crecer y se apaga al empezar a volar (destello de "estrella conseguida").
    val glow = (((a - 1f) / (PRESENT_GROW - 1f)) * (1f - t * 1.4f)).coerceIn(0f, 1f)

    val density = LocalDensity.current
    val starSizeDp  = with(density) { initialSizePx.toDp() }
    val halfSizePx  = initialSizePx / 2f

    val resId = when (targetColor) {
        TargetColor.RED    -> R.drawable.target_red
        TargetColor.BLUE   -> R.drawable.target_blue
        TargetColor.PURPLE -> R.drawable.target_purple
        TargetColor.PINK   -> R.drawable.target_pink
    }
    val glowColor = when (targetColor) {
        TargetColor.RED    -> Color(0xFFFF6B6B)
        TargetColor.BLUE   -> Color(0xFF4DB8FF)
        TargetColor.PURPLE -> Color(0xFFB57EDC)
        TargetColor.PINK   -> Color(0xFFFF8FD0)
    }

    Box(modifier = modifier) {
        // Esquirlas del color del objetivo + blancas que estallan desde el bloque roto (matte, SrcOver).
        val b = burst.value
        if (b > 0f && b < 1f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val n = 7
                val maxDist = cellSizePx * 1.35f
                val fade = (1f - b).coerceIn(0f, 1f)
                for (i in 0 until n) {
                    val ang = (i * (360f / n) + 15f) * (Math.PI.toFloat() / 180f)
                    val d = maxDist * b
                    val px = originPx.x + kotlin.math.cos(ang) * d
                    val py = originPx.y + kotlin.math.sin(ang) * d
                    val col = if (i % 3 == 0) Color.White else glowColor
                    drawCircle(
                        col.copy(alpha = 0.9f * fade),
                        radius = cellSizePx * (0.09f * fade + 0.02f),
                        center = Offset(px, py)
                    )
                }
            }
        }
        // ESTELA del vuelo (referencia video Block Blast, 2026-07-20): fantasmas del color de la
        // gema rezagados sobre el MISMO arco Bézier — la trayectoria se lee de un vistazo y el
        // aterrizaje en el contador gana peso. Solo durante el vuelo (t > 0).
        if (t > 0.02f && alpha > 0.05f) {
            Canvas(modifier = Modifier.matchParentSize()) {
                for (i in 1..4) {
                    val tt = (t - i * 0.055f).coerceAtLeast(0f)
                    if (tt <= 0f) continue
                    val gx = (1 - tt) * (1 - tt) * originPx.x + 2 * (1 - tt) * tt * midX + tt * tt * toPx.x
                    val gy = (1 - tt) * (1 - tt) * originPx.y + 2 * (1 - tt) * tt * midY + tt * tt * toPx.y
                    val fade = (1f - i * 0.22f) * alpha
                    drawCircle(
                        glowColor.copy(alpha = 0.38f * fade),
                        radius = cellSizePx * 0.16f * (1f - i * 0.16f) * currentScale.coerceAtLeast(0.3f),
                        center = Offset(gx, gy)
                    )
                    drawCircle(
                        Color.White.copy(alpha = 0.30f * fade),
                        radius = cellSizePx * 0.06f * (1f - i * 0.16f),
                        center = Offset(gx, gy)
                    )
                }
            }
        }
        Image(
            painter = painterResource(id = resId),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(starSizeDp)
                .offset { IntOffset((x - halfSizePx).roundToInt(), (y - halfSizePx).roundToInt()) }
                // Halo radial detrás de la estrella durante la presentación.
                .drawBehind {
                    if (glow > 0.01f) {
                        val r = size.minDimension * 0.95f * currentScale
                        drawCircle(
                            brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                colors = listOf(
                                    glowColor.copy(alpha = 0.55f * glow),
                                    glowColor.copy(alpha = 0f)
                                ),
                                center = center,
                                radius = r
                            ),
                            radius = r,
                            center = center
                        )
                    }
                }
                .graphicsLayer {
                    rotationZ       = rot
                    scaleX          = currentScale
                    scaleY          = currentScale
                    this.alpha      = alpha
                    transformOrigin = TransformOrigin.Center
                }
        )
    }
}
