package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.cukiti.teikafusion.model.GameEvent
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * **LA MUTACIÓN DE LAS ESTRELLAS HUÉRFANAS** (2026-08-26).
 *
 * Un color del objetivo acaba de quedar cubierto y sus estrellas vivas se han retintado al color que
 * más falta ([com.cukiti.teikafusion.model.StarObjectiveRetint]). El estado YA cambió cuando este
 * efecto arranca —la celda ya se pinta del color nuevo—, así que lo que dibuja esto no es el cambio:
 * es el **aviso** de que ha pasado, para que el jugador no se encuentre un tablero repintado sin
 * explicación. Una onda que sale de cada celda y una estrella que gira sobre ella, escalonadas.
 *
 * Consumidor ÚNICO del evento `StarRetint` (contrato de eventos: exactamente un sitio consume).
 *
 * Rendimiento: un solo `Canvas` para todas las celdas, un solo reloj, cero asignaciones en el bucle
 * de dibujo (el `Path` de la estrella se reutiliza).
 */
@Composable
fun StarRetintVFX(
    cells: List<GameEvent.StarRetint.Retinted>,
    boardOffsetPx: Offset,
    cellSizePx: Float,
    reducedFx: Boolean,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val finish by rememberUpdatedState(onComplete)
    val total = remember(cells.size, reducedFx) {
        if (reducedFx) 220 else (BASE_MS + STAGGER_MS * (cells.size - 1).coerceAtMost(MAX_STAGGERED))
    }
    val clock = remember(cells) { Animatable(0f) }
    LaunchedEffect(cells) {
        clock.snapTo(0f)
        clock.animateTo(1f, tween(total, easing = LinearEasing))
        finish()
    }

    val starPath = remember { Path() }
    Canvas(modifier = modifier) {
        val t = clock.value
        if (t <= 0f) return@Canvas
        val radius = cellSizePx * 0.5f
        cells.forEachIndexed { index, retinted ->
            // Escalonado: cada celda arranca un pelín después que la anterior, y a partir de
            // [MAX_STAGGERED] van todas juntas — con medio tablero retintado, esperar el turno de la
            // celda 40 haría durar el efecto varios segundos.
            val delayed = (index.coerceAtMost(MAX_STAGGERED) * STAGGER_MS).toFloat() / total
            val local = ((t - delayed) / (BASE_MS.toFloat() / total)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEachIndexed
            val center = Offset(
                boardOffsetPx.x + (retinted.cell.x + 0.5f) * cellSizePx,
                boardOffsetPx.y + (retinted.cell.y + 0.5f) * cellSizePx,
            )
            val color = Color(retinted.color.hex)
            // Onda: sale de la celda y se apaga.
            val ringAlpha = (1f - local) * 0.85f
            if (ringAlpha > 0.01f) {
                drawCircle(
                    color = color.copy(alpha = ringAlpha),
                    radius = radius * (0.35f + 1.25f * local),
                    center = center,
                    style = Stroke(width = cellSizePx * 0.09f * (1f - local * 0.5f)),
                )
            }
            // Estrella: entra girando media vuelta y se asienta sobre la celda.
            val pop = if (local < 0.45f) local / 0.45f else 1f - (local - 0.45f) / 0.55f
            val starAlpha = pop.coerceIn(0f, 1f)
            if (starAlpha > 0.01f) {
                rotate(degrees = 180f * (1f - local), pivot = center) {
                    drawRetintStar(
                        path = starPath,
                        center = center,
                        radius = radius * (0.30f + 0.28f * pop),
                        color = color.copy(alpha = starAlpha),
                    )
                }
            }
        }
    }
}

/** Duración de la mutación de UNA celda. */
private const val BASE_MS = 520

/** Retardo entre celda y celda. */
private const val STAGGER_MS = 26

/** A partir de aquí las celdas dejan de escalonarse y mutan a la vez. */
private const val MAX_STAGGERED = 14

/** Estrella de cinco puntas reutilizando el mismo [Path] en cada celda: cero asignaciones por frame. */
private fun DrawScope.drawRetintStar(path: Path, center: Offset, radius: Float, color: Color) {
    val inner = radius * 0.42f
    path.reset()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) radius else inner
        val angle = -PI.toFloat() / 2f + i * (PI.toFloat() / 5f)
        val x = center.x + r * cos(angle)
        val y = center.y + r * sin(angle)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, color)
    drawCircle(Color.White.copy(alpha = color.alpha * 0.75f), radius = radius * 0.2f, center = center)
}
