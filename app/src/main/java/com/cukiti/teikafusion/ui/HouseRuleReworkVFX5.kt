package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.cukiti.teikafusion.model.Compass
import com.cukiti.teikafusion.model.GameEvent

/*
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *  ÚLTIMA TANDA DEL REWORK DE REGLAS DE LA CASA (2026-09-22): la brújula y las fichas que se funden.
 *
 *  Mismo contrato: lo que HAY (la rosa y la pared hacia la que se cae; las gemelas en la bandeja),
 *  lo que PASARÍA (el giro que viene, en la rosa; el cuadro destino que se ilumina en verde o rojo
 *  al pasar una gemela por encima) y lo que ACABA de pasar (el giro barriendo el tablero; la ficha
 *  fundida latiendo en su cuadro).
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 */

private val CompassTint = Color(0xFF9FE8C8)
private val CompassNext = Color(0xFFFFE08A)
private val FuseOk = Color(0xFF69F0AE)
private val FuseNo = Color(0xFFFF6B6B)

/** Ángulo de dibujo (grados, 0 = hacia abajo) de una dirección de la brújula. */
private fun angleOf(dir: Int): Float = when (dir.mod(4)) {
    Compass.DOWN -> 0f
    Compass.LEFT -> 90f
    Compass.UP -> 180f
    else -> 270f
}

/** La aguja de la brújula apuntando a [dir] (rotada alrededor de [c]). */
private fun DrawScope.drawNeedle(c: Offset, r: Float, dir: Int, color: Color, width: Float) {
    rotate(angleOf(dir), pivot = c) {
        drawLine(color, Offset(c.x, c.y - r * 0.55f), Offset(c.x, c.y + r * 0.75f), width, StrokeCap.Round)
        drawLine(color, Offset(c.x, c.y + r * 0.75f), Offset(c.x - r * 0.3f, c.y + r * 0.4f), width, StrokeCap.Round)
        drawLine(color, Offset(c.x, c.y + r * 0.75f), Offset(c.x + r * 0.3f, c.y + r * 0.4f), width, StrokeCap.Round)
    }
}

/**
 * **LA BRÚJULA** (P-241): la pared hacia la que caen ahora los Teikas brilla como un suelo, y en la
 * esquina una rosa con su aguja dice hacia dónde, con marcas de las colocaciones que faltan. Cuando
 * la próxima colocación la hace girar, aparece en la rosa la aguja del giro que viene, latiendo: el
 * jugador lo ve antes de soltar.
 */
@Composable
fun CompassLayer(
    dir: Int,
    nextDir: Int,
    turnsUntil: Int,
    period: Int,
    cols: Int,
    rows: Int,
    cellSize: Dp,
    animationsOn: Boolean,
) {
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    // P-293 (2026-09-25, playtest): la rosa vivía en la casilla de arriba a la izquierda y la banda
    // del «suelo» dentro de las casillas del borde: las dos tapaban bloques. Ahora todo va POR FUERA
    // del tablero (se monta en el Box externo, que no recorta): una barra pegada a la pared hacia la
    // que se cae, con las colocaciones que faltan marcadas en ella; y, cuando la próxima colocación
    // la hace girar, la barra de la pared nueva late a su lado. El aviso grande va en [CompassTurnVFX].
    Canvas(Modifier.fillMaxSize()) {
        val beat = ambientBeat(animationsOn, 0.9f)
        val w = cols * cellPx
        val h = rows * cellPx
        val gap = cellPx * 0.07f
        val th = cellPx * 0.10f
        fun wall(d: Int): Pair<Offset, Size> = when (d.mod(4)) {
            Compass.DOWN -> Offset(0f, h + gap) to Size(w, th)
            Compass.UP -> Offset(0f, -gap - th) to Size(w, th)
            Compass.LEFT -> Offset(-gap - th, 0f) to Size(th, h)
            else -> Offset(w + gap, 0f) to Size(th, h)
        }
        val inminente = turnsUntil <= 1
        if (inminente && nextDir.mod(4) != dir.mod(4)) {
            val (nt, ns) = wall(nextDir)
            drawRoundRect(CompassNext.copy(alpha = 0.30f + 0.60f * beat), nt, ns, CornerRadius(th / 2f))
        }
        val (tl, sz) = wall(dir)
        drawRoundRect(CompassTint.copy(alpha = 0.70f + 0.25f * beat), tl, sz, CornerRadius(th / 2f))
        // Las colocaciones que faltan, como cuentas sobre la barra (llenas = faltan).
        val horizontal = sz.width > sz.height
        val step = cellPx * 0.30f
        val mid = Offset(tl.x + sz.width / 2f, tl.y + sz.height / 2f)
        for (i in 0 until period) {
            val o = (i - (period - 1) / 2f) * step
            val p = if (horizontal) Offset(mid.x + o, mid.y) else Offset(mid.x, mid.y + o)
            if (i < turnsUntil) drawCircle(Color(0xFF0E1A22), th * 0.34f, p)
            else drawCircle(Color(0xFF0E1A22).copy(alpha = 0.35f), th * 0.20f, p)
        }
    }
}

/**
 * **LA BRÚJULA GIRA** (P-241): unas flechas grandes barren el tablero hacia la pared nueva — es
 * hacia donde van a rodar todos los cuerpos en el mismo instante.
 */
@Composable
fun CompassTurnVFX(
    events: List<GameEvent.CompassTurn>,
    cols: Int,
    rows: Int,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(760, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t >= 1f) return@Canvas
                val (vx, vy) = Compass.vectorOf(event.to)
                val w = cols * cellPx
                val h = rows * cellPx
                val a = kotlin.math.sin(kotlin.math.PI.toFloat() * t)
                // P-293 · EL AVISO GRANDE (idea del usuario): la rosa, enorme en el centro del tablero,
                // con la aguja hacia la pared nueva. Entra creciendo y se va con las flechas.
                run {
                    val grow = 0.75f + 0.25f * (t * 3f).coerceAtMost(1f)
                    val r = cellPx * 1.7f * grow
                    val c = Offset(w / 2f, h / 2f)
                    drawCircle(Color(0xFF0E1A22).copy(alpha = 0.60f * a), r, c)
                    drawCircle(CompassTint.copy(alpha = 0.95f * a), r, c, style = Stroke(cellPx * 0.10f))
                    drawNeedle(c, r * 0.95f, event.to, CompassTint.copy(alpha = a), cellPx * 0.22f)
                }
                for (i in 0 until 4) {
                    val lane = (i + 0.5f) / 4f
                    val travel = -0.2f + 1.4f * ((t + i * 0.08f) % 1f)
                    val cx = if (vx != 0f) (if (vx > 0) travel else 1f - travel) * w else lane * w
                    val cy = if (vy != 0f) (if (vy > 0) travel else 1f - travel) * h else lane * h
                    val c = Offset(cx, cy)
                    val len = cellPx * 1.2f
                    val tail = Offset(c.x - vx * len, c.y - vy * len)
                    drawLine(CompassTint.copy(alpha = 0.7f * a), tail, c, cellPx * 0.16f, StrokeCap.Round)
                    drawLine(CompassTint.copy(alpha = 0.7f * a), c,
                        Offset(c.x - vx * cellPx * 0.4f - vy * cellPx * 0.35f, c.y - vy * cellPx * 0.4f + vx * cellPx * 0.35f),
                        cellPx * 0.16f, StrokeCap.Round)
                    drawLine(CompassTint.copy(alpha = 0.7f * a), c,
                        Offset(c.x - vx * cellPx * 0.4f + vy * cellPx * 0.35f, c.y - vy * cellPx * 0.4f - vx * cellPx * 0.35f),
                        cellPx * 0.16f, StrokeCap.Round)
                }
            }
        }
    }
}

// ─────────────────────────────────── LAS FICHAS QUE SE FUNDEN ──────────────────────────────────

/**
 * **EL CUADRO DESTINO** (P-232): mientras arrastras una gemela sobre la otra, su cuadro se ilumina —
 * verde si se fundirán, rojo si lo que saldría no cabe en el tablero —. Es el aviso ANTES de soltar.
 */
@Composable
fun FuseTargetGlow(ok: Boolean, modifier: Modifier = Modifier) {
    val beat = rememberInfiniteTransition(label = "fuse").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse), label = "fuseBeat"
    )
    Canvas(modifier = modifier) {
        val b = beat.value
        val color = if (ok) FuseOk else FuseNo
        val cr = CornerRadius(size.minDimension * 0.18f)
        drawRoundRect(color.copy(alpha = 0.16f + 0.14f * b), cornerRadius = cr)
        drawRoundRect(color.copy(alpha = 0.7f + 0.3f * b), cornerRadius = cr, style = Stroke(size.minDimension * 0.05f))
        if (!ok) {
            val k = size.minDimension * 0.16f
            val c = Offset(size.width / 2f, size.height / 2f)
            drawLine(Color.White, Offset(c.x - k, c.y - k), Offset(c.x + k, c.y + k), size.minDimension * 0.05f, StrokeCap.Round)
            drawLine(Color.White, Offset(c.x - k, c.y + k), Offset(c.x + k, c.y - k), size.minDimension * 0.05f, StrokeCap.Round)
        }
    }
}

/** **LA FICHA FUNDIDA** (P-232): un anillo que se abre desde su cuadro y un destello. */
@Composable
fun PiecesFusedPulse(event: GameEvent.PiecesFused, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val clock = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        clock.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        onComplete()
    }
    Canvas(modifier = modifier) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(FuseOk.copy(alpha = 0.8f * (1f - t)), size.minDimension * (0.2f + 0.5f * t), c,
            style = Stroke(size.minDimension * 0.06f * (1f - t)))
        drawCircle(Color.White.copy(alpha = 0.5f * (1f - t)), size.minDimension * 0.3f * (1f - t), c)
    }
}
