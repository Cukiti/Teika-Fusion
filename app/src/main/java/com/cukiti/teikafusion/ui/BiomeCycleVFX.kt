package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.PlantEdge
import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.viewmodel.TeikaBody
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * **P-353 · EL CASTIGO SIEMPRE LLEGA — lo que se ve** (2026-09-27). Reglas en [com.cukiti.teikafusion.model.BiomeCycle].
 *
 *  - [BiomeParryVFX]: el rótulo «¡PARADA!» cuando el jugador rompe una línea por la zona amenazada
 *    justo en el turno del golpe. Único consumidor de [GameEvent.BiomeParry].
 *  - [GardenMarkOverlay]: el Jardín MARCA en el aviso el borde por el que entrará la raíz y el Teika que
 *    se llevará (antes no se sabía hasta el golpe, y sin saberlo no había nada que mitigar).
 *  - [StarMarkOverlay]: el Cielo Estelar marca su presa antes de bajar (el Teika del Sol, las estrellas
 *    de la Luna).
 *
 * Tope de 1200 ms, como todo evento de bioma.
 */

private val ParryGold = Color(0xFFFFD66B)
private val MarkGreen = Color(0xFF8BE36A)
private val MarkStar = Color(0xFFFFE9A8)

const val BIOME_PARRY_MS = 1000

@Composable
fun BiomeParryVFX(event: GameEvent.BiomeParry, onComplete: () -> Unit) {
    val t = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        t.animateTo(1f, tween(BIOME_PARRY_MS, easing = LinearEasing))
        onComplete()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val p = t.value
            val c = Offset(size.width / 2f, size.height / 2f)
            val ring = (p / 0.6f).coerceIn(0f, 1f)
            drawCircle(
                ParryGold.copy(alpha = (1f - ring) * 0.9f), radius = size.minDimension * (0.12f + 0.45f * ring),
                center = c, style = Stroke(width = 6.dp.toPx() * (1f - ring))
            )
            for (i in 0 until 12) {
                val a = i * (2f * PI.toFloat() / 12f)
                val r0 = size.minDimension * (0.16f + 0.30f * ring)
                val r1 = r0 + size.minDimension * 0.08f * (1f - ring)
                drawLine(
                    ParryGold.copy(alpha = 1f - ring), c + Offset(cos(a) * r0, sin(a) * r0),
                    c + Offset(cos(a) * r1, sin(a) * r1), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round
                )
            }
        }
        Text(
            "¡PARADA!",
            color = ParryGold,
            fontSize = 34.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.graphicsLayer {
                val p = t.value
                val pop = if (p < 0.18f) 0.6f + 0.6f * (p / 0.18f) else 1.2f - 0.2f * ((p - 0.18f) / 0.82f).coerceIn(0f, 1f)
                scaleX = pop; scaleY = pop
                alpha = if (p > 0.8f) (1f - p) / 0.2f else 1f
                translationY = -18.dp.toPx() * p
            }
        )
    }
}

/**
 * El borde por el que entrará la raíz (una franja que late) y un aro sobre el Teika que se llevará. La
 * posición del cuerpo se lee al DIBUJAR: el Teika rueda y el aro lo sigue sin recomponer.
 */
@Composable
fun GardenMarkOverlay(
    edge: PlantEdge,
    teikaId: String?,
    bodiesFlow: StateFlow<List<TeikaBody>>,
    cellPx: Float,
    animationsOn: Boolean,
) {
    val bodies = bodiesFlow.collectAsState()
    // Capa propia (auditoría de fluidez 2026-09-29): late y sigue al Teika cada fotograma.
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val beat = ambientBeat(animationsOn, 1.1f)
        val band = cellPx * 2f
        // Sin `Pair` por fotograma: esquina y tamaño por separado.
        val tl = when (edge) {
            PlantEdge.BOTTOM -> Offset(0f, size.height - band)
            PlantEdge.RIGHT -> Offset(size.width - band, 0f)
            PlantEdge.TOP, PlantEdge.LEFT -> Offset.Zero
        }
        val sz = when (edge) {
            PlantEdge.TOP, PlantEdge.BOTTOM -> Size(size.width, band)
            PlantEdge.LEFT, PlantEdge.RIGHT -> Size(band, size.height)
        }
        drawRect(MarkGreen.copy(alpha = 0.10f + 0.10f * beat), tl, sz)
        drawRect(MarkGreen.copy(alpha = 0.55f + 0.3f * beat), tl, sz, style = Stroke(2.dp.toPx()))
        val b = teikaId?.let { id -> bodies.value.firstOrNull { it.id == id } } ?: return@Canvas
        val r = b.radius * cellPx * (1.25f + 0.08f * beat)
        drawCircle(MarkGreen.copy(alpha = 0.9f), radius = r, center = Offset(b.x * cellPx, b.y * cellPx), style = Stroke(3.dp.toPx()))
    }
}

/** La presa del astro antes de bajar: un aro sobre el Teika del Sol y un marco sobre cada estrella de la Luna. */
@Composable
fun StarMarkOverlay(
    teikaId: String?,
    starCells: Set<Position>,
    bodiesFlow: StateFlow<List<TeikaBody>>,
    cellPx: Float,
    animationsOn: Boolean,
) {
    val bodies = bodiesFlow.collectAsState()
    // Capa propia: late y sigue al Teika cada fotograma.
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val beat = ambientBeat(animationsOn, 1.3f)
        starCells.forEach { p ->
            val inset = cellPx * 0.06f
            drawRoundRect(
                MarkStar.copy(alpha = 0.6f + 0.35f * beat), Offset(p.x * cellPx + inset, p.y * cellPx + inset),
                Size(cellPx - inset * 2, cellPx - inset * 2), CornerRadius(cellPx * 0.2f), style = Stroke(2.5.dp.toPx())
            )
        }
        val b = teikaId?.let { id -> bodies.value.firstOrNull { it.id == id } } ?: return@Canvas
        val r = b.radius * cellPx * (1.25f + 0.08f * beat)
        drawCircle(MarkStar.copy(alpha = 0.9f), radius = r, center = Offset(b.x * cellPx, b.y * cellPx), style = Stroke(3.dp.toPx()))
    }
}

/**
 * La ficha que la Maite ha MARCADO en el aviso: un «Zz» que late en la esquina de su cuadro. Jugarla
 * antes del golpe la aparta (duerme otra, un solo turno); colocarla rompiendo línea en el golpe, la para.
 */
@Composable
fun DrowsyMarkBadge(modifier: Modifier = Modifier) {
    val pulse = androidx.compose.animation.core.rememberInfiniteTransition(label = "drowsyMark")
        .animateFloat(
            0f, 1f,
            androidx.compose.animation.core.infiniteRepeatable(
                tween(700), androidx.compose.animation.core.RepeatMode.Reverse
            ),
            label = "drowsyMarkPulse"
        )
    Text(
        "Zz",
        color = Color(0xFF1A1030),
        fontSize = 13.sp,
        fontWeight = FontWeight.Black,
        modifier = modifier
            .graphicsLayer {
                val k = 1f + 0.12f * pulse.value
                scaleX = k; scaleY = k
            }
            .background(Color(0xFFC9B6FF), androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
}
