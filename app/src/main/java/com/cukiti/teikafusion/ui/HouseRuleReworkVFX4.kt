package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.cukiti.teikafusion.model.CellContent
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.Portals
import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.viewmodel.TeikaBody
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *  CUARTA TANDA DEL REWORK DE REGLAS DE LA CASA (2026-09-22): el molde, las pompas y los portales.
 *
 *  Mismo contrato que las anteriores: lo que HAY, lo que PASARÍA si sueltas aquí (en
 *  `HouseRuleDragPreviewLayer`) y lo que ACABA de pasar.
 *
 *  Vocabulario: el molde es PLATA (contorno de la forma entera, nunca dorado); la pompa es un anillo
 *  irisado alrededor del cuerpo; los portales son óvalos en la pared, violeta y turquesa, los mismos
 *  dos colores en los dos para que se lean como un par.
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 */

private val MoldSilver = Color(0xFFD9E4FF)
private val MoldGlow = Color(0xFFB9A8FF)
private val BubbleHues = listOf(Color(0xFFFF9FD8), Color(0xFF9FE8FF), Color(0xFFC9FF9F), Color(0xFFFFE69F))
private val PortalA = Color(0xFFB57EDC)
private val PortalB = Color(0xFF4DD9C8)
private val PortalClosed = Color(0xFF7A7F8C)

// ───────────────────────────────────────── EL MOLDE ────────────────────────────────────────────

/** El contorno de un conjunto de casillas: solo los bordes que no comparten con otra del conjunto. */
private fun DrawScope.drawOutline(cells: Set<Position>, cellPx: Float, color: Color, width: Float, effect: PathEffect? = null) {
    cells.forEach { c ->
        val l = c.x * cellPx; val t = c.y * cellPx; val r = l + cellPx; val b = t + cellPx
        if (Position(c.x, c.y - 1) !in cells) drawLine(color, Offset(l, t), Offset(r, t), width, pathEffect = effect)
        if (Position(c.x, c.y + 1) !in cells) drawLine(color, Offset(l, b), Offset(r, b), width, pathEffect = effect)
        if (Position(c.x - 1, c.y) !in cells) drawLine(color, Offset(l, t), Offset(l, b), width, pathEffect = effect)
        if (Position(c.x + 1, c.y) !in cells) drawLine(color, Offset(r, t), Offset(r, b), width, pathEffect = effect)
    }
}

/**
 * **EL MOLDE** (P-233): la silueta de plata de la forma que hay que encajar, con un brillo que la
 * recorre. Debajo de los bloques: está en casillas libres, y si algo lo tapa se muda solo.
 */
@Composable
fun MoldLayer(
    cells: Set<Position>,
    cellSize: Dp,
    animationsOn: Boolean,
) {
    if (cells.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val dash = remember(cellPx) { PathEffect.dashPathEffect(floatArrayOf(cellPx * 0.16f, cellPx * 0.08f)) }
    Canvas(Modifier.fillMaxSize()) {
        val beat = ambientBeat(animationsOn, 1.6f)
        cells.forEach { c ->
            drawRect(MoldGlow.copy(alpha = 0.16f + 0.10f * beat), Offset(c.x * cellPx, c.y * cellPx), Size(cellPx, cellPx))
        }
        drawOutline(cells, cellPx, MoldSilver.copy(alpha = 0.55f + 0.4f * beat), cellPx * 0.07f, dash)
        // El brillo que recorre el molde: una casilla cada vez, por orden.
        val ordered = cells.sortedWith(compareBy({ it.y }, { it.x }))
        val i = if (animationsOn) ((biomeClockSec.floatValue * 3f).toInt() % ordered.size) else 0
        val g = ordered[i]
        drawCircle(Color.White.copy(alpha = 0.6f), cellPx * 0.12f, Offset((g.x + 0.5f) * cellPx, (g.y + 0.5f) * cellPx))
    }
}

/** **EL MOLDE ESTALLA** (P-233): la silueta destella en plata, lo que se lleva se ilumina y unas chispas vuelan al molde nuevo. */
@Composable
fun MoldStampVFX(
    events: List<GameEvent.MoldStamp>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(900, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t >= 1f) return@Canvas
                val burst = (t / 0.45f).coerceIn(0f, 1f)
                if (burst < 1f) {
                    val a = 1f - burst
                    event.cleared.forEach { c ->
                        drawRect(MoldSilver.copy(alpha = 0.7f * a), Offset(c.x * cellPx, c.y * cellPx), Size(cellPx, cellPx))
                    }
                    drawOutline(event.cells, cellPx, Color.White.copy(alpha = a), cellPx * (0.08f + 0.2f * burst))
                    val cx = event.cells.map { it.x + 0.5f }.average().toFloat() * cellPx
                    val cy = event.cells.map { it.y + 0.5f }.average().toFloat() * cellPx
                    drawCircle(MoldGlow.copy(alpha = 0.8f * a), cellPx * (0.5f + 2.2f * burst), Offset(cx, cy),
                        style = Stroke(cellPx * 0.12f * a))
                }
                val travel = ((t - 0.4f) / 0.6f).coerceIn(0f, 1f)
                if (travel > 0f && event.next.isNotEmpty()) {
                    val from = event.cells.toList()
                    event.next.forEachIndexed { i, to ->
                        val f = from[i % from.size]
                        val p = Offset(
                            (f.x + (to.x - f.x) * travel + 0.5f) * cellPx,
                            (f.y + (to.y - f.y) * travel + 0.5f) * cellPx - cellPx * sin(PI.toFloat() * travel),
                        )
                        drawCircle(MoldSilver, cellPx * 0.08f, p)
                    }
                    if (travel > 0.85f) drawOutline(event.next, cellPx, Color.White.copy(alpha = (1f - travel) / 0.15f), cellPx * 0.1f)
                }
            }
        }
    }
}

/** **EL MOLDE SE MUDA** (P-233): la silueta vieja se apaga mientras la nueva se enciende. */
@Composable
fun MoldMovedVFX(
    events: List<GameEvent.MoldMoved>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t >= 1f) return@Canvas
                drawOutline(event.from, cellPx, MoldSilver.copy(alpha = 0.8f * (1f - t)), cellPx * 0.06f)
                drawOutline(event.to, cellPx, Color.White.copy(alpha = 0.9f * (1f - t)), cellPx * (0.06f + 0.14f * (1f - t)))
            }
        }
    }
}

/** El sello del molde en la bandeja: contorno de plata que late. Lo lleva la ficha que encaja. */
@Composable
fun MoldTile(modifier: Modifier = Modifier) {
    val beat = androidx.compose.animation.core.rememberInfiniteTransition(label = "mold").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            tween(900, easing = LinearEasing), androidx.compose.animation.core.RepeatMode.Reverse
        ), label = "moldBeat"
    )
    Canvas(modifier = modifier) {
        val b = beat.value
        drawRect(MoldSilver.copy(alpha = 0.35f + 0.35f * b), style = Stroke(width = size.minDimension * 0.10f))
        drawCircle(Color.White.copy(alpha = 0.7f), size.minDimension * 0.07f,
            Offset(size.width * 0.8f, size.height * 0.2f))
    }
}

// ───────────────────────────────────────── LAS POMPAS ──────────────────────────────────────────

/**
 * **LAS POMPAS** (P-238): alrededor de cada Teika que sigue en su pompa, un anillo irisado que
 * tiembla, con su brillo; si la pompa es doble, dos anillos. Encima del cuerpo, leída en DIBUJO.
 */
@Composable
fun BubbleLayer(
    bodiesFlow: StateFlow<List<TeikaBody>>,
    cellSize: Dp,
    animationsOn: Boolean,
) {
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val bodiesState = bodiesFlow.collectAsState()
    val ring = remember(cellPx) { Stroke(width = cellPx * 0.06f) }
    Canvas(Modifier.fillMaxSize()) {
        val clock = if (animationsOn) biomeClockSec.floatValue else 0f
        for (b in bodiesState.value) {
            if (b.bubble <= 0) continue
            val c = Offset(b.x * cellPx, b.y * cellPx)
            val wobble = 1f + 0.04f * sin(clock * 5f + b.x)
            for (layer in 0 until b.bubble.coerceAtMost(2)) {
                val r = (b.radius * 1.2f + layer * 0.14f) * cellPx * wobble
                for (i in BubbleHues.indices) {
                    drawArc(
                        BubbleHues[i].copy(alpha = 0.75f),
                        startAngle = i * 90f + clock * 40f, sweepAngle = 90f, useCenter = false,
                        topLeft = Offset(c.x - r, c.y - r), size = Size(r * 2, r * 2), style = ring,
                    )
                }
            }
            val r = b.radius * 1.2f * cellPx * wobble
            drawCircle(Color.White.copy(alpha = 0.08f), r, c)
            drawArc(Color.White.copy(alpha = 0.8f), 200f, 50f, false,
                Offset(c.x - r * 0.8f, c.y - r * 0.8f), Size(r * 1.6f, r * 1.6f), style = ring)
        }
    }
}

/** **LA POMPA REVIENTA** (P-238): gotitas que salen en corona; si solo se agrieta, un destello. */
@Composable
fun BubblePopVFX(
    events: List<GameEvent.BubblePop>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                kotlinx.coroutines.delay(event.delayMs.toLong())
                clock.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t <= 0f || t >= 1f) return@Canvas
                val c = Offset(event.atX * cellPx, event.atY * cellPx)
                val a = 1f - t
                drawCircle(Color.White.copy(alpha = 0.7f * a), cellPx * (0.6f + 0.6f * t), c, style = Stroke(cellPx * 0.05f * a))
                if (event.popped) for (k in 0 until 10) {
                    val ang = k * 36f * PI.toFloat() / 180f
                    val d = cellPx * (0.6f + 0.9f * t)
                    drawCircle(BubbleHues[k % BubbleHues.size].copy(alpha = a), cellPx * 0.06f,
                        Offset(c.x + cos(ang) * d, c.y + sin(ang) * d + cellPx * 0.4f * t * t))
                }
            }
        }
    }
}

// ───────────────────────────────────────── LOS PORTALES ────────────────────────────────────────

/** Un portal en la pared ([left] = pared izquierda) cuya boca empieza en la fila [top]. */
private fun DrawScope.drawPortal(left: Boolean, top: Int, cols: Int, cellPx: Float, clock: Float, open: Boolean) {
    val h = Portals.SPAN * cellPx
    val cy = top * cellPx + h / 2f
    // P-293 (2026-09-25): la boca va POR FUERA de la pared. Antes estaba centrada en ella y su mitad
    // de dentro tapaba las casillas del borde; ahora solo roza el filo del tablero. Se dibuja desde
    // el Box externo del tablero, que no recorta.
    val w = cellPx * 0.30f
    val cx = if (left) -w * 0.40f else cols * cellPx + w * 0.40f
    val rim = if (!open) PortalClosed else if (left) PortalA else PortalB
    val core = if (!open) PortalClosed else if (left) PortalB else PortalA
    drawOval(core.copy(alpha = 0.30f), Offset(cx - w / 2f, cy - h / 2f), Size(w, h))
    // El remolino: tres arcos que giran dentro de la boca.
    if (open) for (i in 0 until 3) {
        drawArc(core.copy(alpha = 0.8f), startAngle = clock * 180f + i * 120f, sweepAngle = 70f, useCenter = false,
            topLeft = Offset(cx - w * 0.35f, cy - h * 0.35f), size = Size(w * 0.7f, h * 0.7f),
            style = Stroke(cellPx * 0.045f, cap = StrokeCap.Round))
    }
    drawOval(rim.copy(alpha = 0.95f), Offset(cx - w / 2f, cy - h / 2f), Size(w, h), style = Stroke(cellPx * 0.06f))
    if (!open) {
        // Cerrado: la salida está tapada. Una X que lo dice sin texto.
        val k = cellPx * 0.12f
        val x = cx
        drawLine(Color.White.copy(alpha = 0.85f), Offset(x - k, cy - k), Offset(x + k, cy + k), cellPx * 0.07f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.85f), Offset(x - k, cy + k), Offset(x + k, cy - k), cellPx * 0.07f, StrokeCap.Round)
    }
}

/**
 * **LOS PORTALES** (P-243): las dos bocas en las paredes, con su remolino. Una boca cuya SALIDA está
 * tapada por madera se dibuja gris y con una X: es la única forma de que el jugador entienda por qué
 * un Teika que empuja contra ella no pasa.
 */
@Composable
fun PortalsLayer(
    rows: Pair<Int, Int>?,
    grid: Map<Position, CellContent>,
    cols: Int,
    cellSize: Dp,
    animationsOn: Boolean,
) {
    if (rows == null) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val (izq, der) = remember(rows, cols) { Portals.cellsOf(rows, cols) }
    // Una boca está abierta si su SALIDA (las casillas de la otra) no tiene madera.
    val izqAbierta = remember(grid, der) { der.none { grid[it]?.isOccupied == true } }
    val derAbierta = remember(grid, izq) { izq.none { grid[it]?.isOccupied == true } }
    Canvas(Modifier.fillMaxSize()) {
        val clock = if (animationsOn) biomeClockSec.floatValue else 0f
        drawPortal(left = true, top = rows.first, cols = cols, cellPx = cellPx, clock = clock, open = izqAbierta)
        drawPortal(left = false, top = rows.second, cols = cols, cellPx = cellPx, clock = clock, open = derAbierta)
    }
}

/** **EL PASO POR EL PORTAL** (P-243): remolino en la entrada, destello en la salida. */
@Composable
fun PortalHopVFX(
    events: List<GameEvent.PortalHop>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t >= 1f) return@Canvas
                val a = 1f - t
                val from = Offset(event.fromX * cellPx, event.fromY * cellPx)
                val to = Offset(event.toX * cellPx, event.toY * cellPx)
                drawCircle(PortalA.copy(alpha = 0.8f * a), cellPx * (0.6f * (1f - t) + 0.1f), from, style = Stroke(cellPx * 0.1f))
                drawCircle(PortalB.copy(alpha = 0.8f * a), cellPx * (0.2f + 0.8f * t), to, style = Stroke(cellPx * 0.1f * a))
                drawCircle(Color.White.copy(alpha = 0.6f * a), cellPx * 0.3f * a, to)
            }
        }
    }
}

/** **LOS PORTALES SE MUDAN** (P-243): las bocas viejas se cierran y las nuevas se abren. */
@Composable
fun PortalsMovedVFX(
    events: List<GameEvent.PortalsMoved>,
    cols: Int,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t >= 1f) return@Canvas
                val h = Portals.SPAN * cellPx
                fun oval(left: Boolean, top: Int, grow: Float, color: Color) {
                    val cy = top * cellPx + h / 2f
                    val cx = if (left) 0f else cols * cellPx
                    val w = cellPx * 0.9f * grow
                    drawOval(color, Offset(cx - w / 2f, cy - h * grow / 2f), Size(w, h * grow), style = Stroke(cellPx * 0.08f))
                }
                oval(true, event.fromLeft, 1f - t, PortalA.copy(alpha = 1f - t))
                oval(false, event.fromRight, 1f - t, PortalB.copy(alpha = 1f - t))
                oval(true, event.toLeft, t, Color.White.copy(alpha = 1f - t))
                oval(false, event.toRight, t, Color.White.copy(alpha = 1f - t))
            }
        }
    }
}
