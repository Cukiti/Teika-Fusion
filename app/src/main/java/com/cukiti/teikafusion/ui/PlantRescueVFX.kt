package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.viewmodel.TeikaBody
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.sin
import kotlin.math.PI

/** Sigue al cuerpo liberado: no crea un segundo cuerpo ni decide cuándo se puede fusionar. */
@Composable
fun PlantRescueVFX(
    event: GameEvent.PlantRescue,
    bodiesFlow: StateFlow<List<TeikaBody>>,
    cellSize: Dp,
    onComplete: () -> Unit,
) {
    val clock = remember(event.id) { Animatable(0f) }
    val bodies by bodiesFlow.collectAsState()
    val body = bodies.firstOrNull { it.id == event.teikaId }
    val animationsOn = LocalAnimationsActive.current
    LaunchedEffect(event.id, animationsOn) {
        if (animationsOn) {
            clock.animateTo(1f, tween(((1f - clock.value) * event.durationMillis).toInt().coerceAtLeast(1),
                easing = LinearEasing))
            onComplete()
        }
    }
    // Si otra mecánica consumió el cuerpo durante la liberación, no dibujar un clon fantasma.
    if (body == null) return
    val t = clock.value
    val returnOffset = com.cukiti.teikafusion.model.plantRescueOffsetFraction(t)
    val x = body.x + (event.fromX - event.landingX) * returnOffset - body.radius
    val y = body.y + (event.fromY - event.landingY) * returnOffset - body.radius
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    Canvas(Modifier.fillMaxSize()) {
        val cut = (1f - t * 3f).coerceIn(0f, 1f)
        val center = Offset((x + body.radius) * cellPx, (y + body.radius) * cellPx)
        val perp = if (event.edge == com.cukiti.teikafusion.model.PlantEdge.TOP ||
            event.edge == com.cukiti.teikafusion.model.PlantEdge.BOTTOM)
            Offset(cellPx * 0.34f, 0f) else Offset(0f, cellPx * 0.34f)
        if (event.rootsBefore == 2) drawPlantTether(event.edge, center - perp, cut, cut)
        drawPlantTether(event.edge, center + perp, cut, cut)
    }
    TeikaIcon(
        type = body.type,
        modifier = Modifier.offset(cellSize * x, cellSize * y)
            .size(cellSize * body.type.width, cellSize * body.type.height)
            .graphicsLayer {
                val release = sin(t * PI).toFloat() * 0.05f
                val base = teikaVisualScale(body.type)
                scaleX = base * (1f + release)
                scaleY = base * (1f - release)
                rotationZ = sin(t * PI * 2).toFloat() * (1f - t) * 3f
            },
    )
}
