package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.Piece
import kotlin.math.*

/** Bounded spark trail; frame state is read in draw, without recomposing the game each frame. */
@Composable
fun FichonaDragVFX(piece: Piece, position: State<Offset>, root: Offset, cellPx: Float, reduced: Boolean) {
    if (reduced) return
    data class Spark(val at: Offset, val born: Long, val drift: Offset)
    val sparks = remember(piece.id) { ArrayDeque<Spark>() }
    var time by remember(piece.id) { mutableLongStateOf(0L) }
    LaunchedEffect(piece.id) {
        var previous = position.value
        var lastEmit = 0L
        while (true) withFrameNanos { now ->
            val at = position.value
            val delta = at - previous
            if (delta.getDistance() > 2f && now - lastEmit > 45_000_000L) {
                val center = at + Offset(piece.width * cellPx / 2f, piece.height * cellPx)
                repeat(2) { i -> sparks.addLast(Spark(center + Offset((i - 0.5f) * cellPx, 0f), now,
                    Offset(-delta.x.coerceIn(-30f, 30f) * 2f + (i - 0.5f) * 40f, 35f))) }
                lastEmit = now
            }
            previous = at
            while (sparks.isNotEmpty() && (now - sparks.first().born > 350_000_000L || sparks.size > 18)) sparks.removeFirst()
            time = now
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        for (spark in sparks) {
            val age = ((time - spark.born) / 350_000_000f).coerceIn(0f, 1f)
            val at = spark.at - root + spark.drift * age
            drawLine(Color(0xFFFFCC63).copy(alpha = (1f - age) * 0.9f), at,
                at + Offset(-spark.drift.x * 0.12f, -4.dp.toPx()), (1f - age) * 2.dp.toPx())
        }
    }
}

@Composable
fun FichonaShatterVFX(event: GameEvent.FichonaShattered, root: Offset, reduced: Boolean, onDone: () -> Unit) {
    val clock = remember(event.id) { Animatable(0f) }
    val materialColor = LocalBlockSkin.current.colorsFor(event.piece.colorType).first()
    LaunchedEffect(event.id) {
        Haptics.invalid()
        clock.animateTo(1f, tween(if (reduced) 180 else 460))
        onDone()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = clock.value
        val cell = event.cellSizePx
        event.piece.shape.forEachIndexed { i, block ->
            val start = Offset(event.originX, event.originY) - root + Offset((block.x + 0.5f) * cell, (block.y + 0.5f) * cell)
            val angle = i * 2.39996f
            val drift = if (reduced) Offset.Zero else Offset(cos(angle) * cell * 1.5f * p,
                sin(angle) * cell * p + cell * p * p)
            val at = start + drift
            val side = cell * 0.8f * (1f - p * 0.85f)
            rotate(if (reduced) 0f else (i % 2 * 2 - 1) * p * 75f, at) {
                drawRect(materialColor.copy(alpha = 1f - p), at - Offset(side / 2, side / 2), Size(side, side))
                drawLine(Color.White.copy(alpha = (1f - p) * 0.7f), at - Offset(side / 2, 0f), at + Offset(side / 3, side / 4), 1.dp.toPx())
            }
        }
    }
}
