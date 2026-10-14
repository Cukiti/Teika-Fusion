package com.cukiti.teikafusion.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.TeikaType
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

internal fun tierColor(type: TeikaType): Color = when (type) {
    TeikaType.TEIKA_1 -> Color(0xFF7FDBFF)
    TeikaType.TEIKA_2 -> Color(0xFFC89BFF)
    TeikaType.TEIKA_3 -> Color(0xFFFFD45E)
    TeikaType.TEIKA_4 -> Color(0xFF8FE36B)
    TeikaType.MEGA_TEIKA -> Color(0xFFFF72D7)
}

/**
 * Rescate celebratorio: la población actual se reúne en un núcleo y reaparece como dos cuerpos
 * iguales alejados. La UI sólo cuenta la historia; el ViewModel instala los cuerpos sin recompensas.
 */
@Composable
fun ReviveTeikaRescueVFX(
    event: GameEvent.ReviveTeikaRescue,
    boardOrigin: Offset,
    cellSizePx: Float,
    reducedFx: Boolean,
    onComplete: () -> Unit,
) {
    val progress = remember(event.id) { Animatable(0f) }
    val latestComplete by rememberUpdatedState(onComplete)
    val view = LocalView.current
    LaunchedEffect(event.id) {
        if (reducedFx) {
            progress.snapTo(0.46f)
            delay((event.durationMs * 0.52f).toLong())
            progress.snapTo(0.82f)
            delay((event.durationMs * 0.48f).toLong())
            progress.snapTo(1f)
        } else {
            progress.animateTo(1f, tween(event.durationMs, easing = LinearEasing))
        }
        latestComplete()
    }
    LaunchedEffect(event.id) {
        delay((event.durationMs * 0.55f).toLong())
        Haptics.perform(view, HapticFeedbackConstants.LONG_PRESS)
    }

    val p = progress.value
    val density = LocalDensity.current
    val boardCenter = boardOrigin + Offset(cellSizePx * 4f, cellSizePx * 4f)
    val sourceCenter = if (event.bodies.isEmpty()) boardCenter else Offset(
        x = boardOrigin.x + event.bodies.map { it.x }.average().toFloat() * cellSizePx,
        y = boardOrigin.y + event.bodies.map { it.y }.average().toFloat() * cellSizePx,
    )
    val core = Offset(
        x = (sourceCenter.x * 0.35f + boardCenter.x * 0.65f),
        y = (sourceCenter.y * 0.35f + boardCenter.y * 0.65f),
    )
    val gatherRaw = (p / 0.48f).coerceIn(0f, 1f)
    val gather = FastOutSlowInEasing.transform(gatherRaw)
    val splitRaw = ((p - 0.50f) / 0.43f).coerceIn(0f, 1f)
    val split = LinearOutSlowInEasing.transform(splitRaw)
    val scrimIn = (p / 0.14f).coerceIn(0f, 1f)
    val scrimOut = ((p - 0.76f) / 0.24f).coerceIn(0f, 1f)
    val accent = tierColor(event.finalType)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.46f * scrimIn * (1f - scrimOut)))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val pulse = sin(((p - 0.40f) / 0.34f).coerceIn(0f, 1f) * PI).toFloat().coerceAtLeast(0f)
            if (pulse > 0.01f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.White.copy(alpha = 0.72f * pulse),
                        0.30f to accent.copy(alpha = 0.48f * pulse),
                        1f to Color.Transparent,
                        center = core,
                        radius = cellSizePx * 2.6f,
                    ),
                    radius = cellSizePx * 2.6f,
                    center = core,
                )
                drawCircle(
                    color = accent.copy(alpha = 0.82f * pulse),
                    radius = cellSizePx * (0.55f + pulse * 1.65f),
                    center = core,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(cellSizePx * 0.08f),
                )
            }
        }

        // Límite deliberado: un tablero de 64 T1 no debe montar 64 Lotties durante el rescate.
        val visibleBodies = remember(event.bodies) {
            event.bodies.sortedByDescending { it.type.level }.take(24)
        }
        visibleBodies.forEachIndexed { index, body ->
            val start = boardOrigin + Offset(body.x * cellSizePx, body.y * cellSizePx)
            val phase = (index % 5) * 0.014f
            val localRaw = ((gatherRaw - phase) / (1f - phase)).coerceIn(0f, 1f)
            val local = FastOutSlowInEasing.transform(localRaw)
            val lift = sin(local * PI.toFloat()) * min(
                cellSizePx * 1.25f,
                (abs(core.x - start.x) + abs(core.y - start.y)) * 0.16f,
            )
            val center = Offset(
                x = androidx.compose.ui.util.lerp(start.x, core.x, local),
                y = androidx.compose.ui.util.lerp(start.y, core.y, local) - lift,
            )
            val sizePx = body.type.level * cellSizePx
            val sizeDp = with(density) { sizePx.toDp() }
            TeikaIcon(
                type = body.type,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (center.x - sizePx / 2f).roundToInt(),
                            (center.y - sizePx / 2f).roundToInt(),
                        )
                    }
                    .size(sizeDp)
                    .graphicsLayer {
                        alpha = (1f - ((localRaw - 0.66f) / 0.34f).coerceIn(0f, 1f))
                        val s = 1f - local * 0.42f
                        scaleX = s
                        scaleY = s
                        rotationZ = sin((index + 1) * 1.7f) * local * 12f
                    }
            )
        }

        val finals = listOf(
            Offset(event.finalAx, event.finalAy),
            Offset(event.finalBx, event.finalBy),
        )
        finals.forEachIndexed { index, cellCenter ->
            val end = boardOrigin + cellCenter * cellSizePx
            val side = if (index == 0) -1f else 1f
            val arc = sin(split * PI.toFloat()) * cellSizePx * 1.15f
            val center = Offset(
                x = androidx.compose.ui.util.lerp(core.x, end.x, split),
                y = androidx.compose.ui.util.lerp(core.y, end.y, split) - arc * side * 0.22f,
            )
            val sizePx = event.finalType.level * cellSizePx
            val sizeDp = with(density) { sizePx.toDp() }
            val appear = ((splitRaw) / 0.28f).coerceIn(0f, 1f)
            TeikaIcon(
                type = event.finalType,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (center.x - sizePx / 2f).roundToInt(),
                            (center.y - sizePx / 2f).roundToInt(),
                        )
                    }
                    .size(sizeDp)
                    .graphicsLayer {
                        alpha = appear
                        val s = (0.45f + appear * 0.55f) * (1f + sin(split * PI.toFloat()) * 0.10f)
                        scaleX = s
                        scaleY = s
                        rotationZ = side * (1f - split) * 8f
                    }
            )
        }
    }
}
