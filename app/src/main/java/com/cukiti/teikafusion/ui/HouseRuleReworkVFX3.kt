package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.Position
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *  TERCERA TANDA DEL REWORK DE REGLAS DE LA CASA (2026-09-22): la piñata, la implosión y el rebote.
 *
 *  Mismo contrato que las dos primeras (ver `HouseRuleReworkVFX.kt`): lo que HAY, lo que PASARÍA si
 *  sueltas aquí (en `HouseRuleDragPreviewLayer`) y lo que ACABA de pasar.
 *
 *  ⛔ Sin confeti (decisión congelada): la piñata revienta en ESTRELLAS de sus colores y lo que
 *  llueve son los propios Teikas.
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 */

private val PinataHues = listOf(
    Color(0xFFFF5FA2), Color(0xFFFFB347), Color(0xFF6FE3B5), Color(0xFF8FA8FF), Color(0xFFFFE066),
)
private val PinataString = Color(0xFFEDE3CF)
private val ImplosionTint = Color(0xFFB9F3FF)
private val RecoilTint = Color(0xFFFFF2B3)

/** Una estrella de cinco puntas centrada en ([cx], [cy]), de radio exterior [r]. */
private fun starPath(cx: Float, cy: Float, r: Float, rotDeg: Float = -90f): Path = Path().apply {
    for (i in 0 until 10) {
        val rr = if (i % 2 == 0) r else r * 0.48f
        val a = (rotDeg + i * 36f) * PI.toFloat() / 180f
        val x = cx + cos(a) * rr
        val y = cy + sin(a) * rr
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** La piñata: una estrella de franjas de colores, con [hits] desgarros. Sin asignar más que su Path. */
private fun DrawScope.drawPinata(cx: Float, cy: Float, s: Float, hits: Int, star: Path) {
    drawPath(star, PinataHues[0])
    // Franjas: anillos concéntricos de colores recortados por la propia estrella.
    for (i in 1 until PinataHues.size) {
        drawCircle(PinataHues[i].copy(alpha = 0.85f), s * (0.40f - i * 0.075f), Offset(cx, cy))
    }
    drawPath(star, Color(0xFF3A1F4F).copy(alpha = 0.8f), style = Stroke(width = s * 0.04f))
    // Los desgarros de cada golpe.
    for (k in 0 until hits.coerceAtMost(3)) {
        val a = (40f + k * 110f) * PI.toFloat() / 180f
        drawLine(Color(0xFF3A1F4F), Offset(cx + cos(a) * s * 0.08f, cy + sin(a) * s * 0.08f),
            Offset(cx + cos(a) * s * 0.30f, cy + sin(a) * s * 0.30f), strokeWidth = s * 0.035f, cap = StrokeCap.Round)
    }
}

/**
 * **LA PIÑATA COLGADA** (P-242): cuelga de un cordel que baja del borde de arriba, se balancea, y
 * lleva debajo los golpes que le faltan. Dos pistas más, que son las que la hacen jugable: una guía
 * punteada por SU FILA (el blanco quieto) y una flecha hacia donde estará el turno que viene (su
 * columna, el blanco que se mueve). La posición se desliza con un muelle al moverse: la piñata se
 * balancea, no se teletransporta.
 */
@Composable
fun PinataLayer(
    cell: Position?,
    hits: Int,
    needed: Int,
    dir: Int,
    cols: Int,
    cellSize: Dp,
    animationsOn: Boolean,
) {
    if (cell == null) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val x by animateFloatAsState(cell.x.toFloat(), spring(dampingRatio = 0.55f, stiffness = 180f), label = "pinataX")
    val dash = remember(cellPx) {
        Stroke(width = cellPx * 0.04f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cellPx * 0.10f, cellPx * 0.14f)))
    }
    Canvas(Modifier.fillMaxSize()) {
        val clock = if (animationsOn) biomeClockSec.floatValue else 0f
        val cx = (x + 0.5f) * cellPx
        val cy = (cell.y + 0.5f) * cellPx
        // La fila: el blanco quieto.
        drawLine(PinataHues[0].copy(alpha = 0.28f), Offset(0f, cy), Offset(cols * cellPx, cy),
            strokeWidth = cellPx * 0.04f, pathEffect = dash.pathEffect)
        // El turno que viene: un contorno donde estará.
        val (nx, _) = com.cukiti.teikafusion.model.Pinata.swing(cell.x, dir, cols)
        val ncx = (nx + 0.5f) * cellPx
        drawCircle(PinataHues[4].copy(alpha = 0.45f), cellPx * 0.30f, Offset(ncx, cy), style = dash)
        drawLine(PinataHues[4].copy(alpha = 0.6f), Offset(cx + (ncx - cx) * 0.45f, cy), Offset(cx + (ncx - cx) * 0.75f, cy),
            strokeWidth = cellPx * 0.05f, cap = StrokeCap.Round)
        // El cordel y el balanceo.
        val sway = sin(clock * 2.2f) * 8f
        rotate(sway, pivot = Offset(cx, 0f)) {
            drawLine(PinataString, Offset(cx, 0f), Offset(cx, cy - cellPx * 0.36f), strokeWidth = cellPx * 0.035f)
            drawPinata(cx, cy, cellPx, hits, starPath(cx, cy, cellPx * 0.44f))
        }
        // Los golpes que faltan.
        val gap = cellPx * 0.16f
        val sx = cx - gap * (needed - 1) / 2f
        for (i in 0 until needed) {
            val p = Offset(sx + gap * i, cy + cellPx * 0.52f)
            if (i < hits) drawCircle(PinataHues[0], cellPx * 0.05f, p)
            else drawCircle(Color.White.copy(alpha = 0.6f), cellPx * 0.05f, p, style = Stroke(cellPx * 0.02f))
        }
    }
}

/** **EL GOLPE** (P-242): destello y estrellitas que saltan de la piñata. */
@Composable
fun PinataHitVFX(
    events: List<GameEvent.PinataHit>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                delay(event.delayMs.toLong())
                clock.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t <= 0f || t >= 1f) return@Canvas
                val c = Offset((event.cell.x + 0.5f) * cellPx, (event.cell.y + 0.5f) * cellPx)
                drawCircle(Color.White.copy(alpha = 0.8f * (1f - t)), cellPx * (0.35f + 0.5f * t), c,
                    style = Stroke(cellPx * 0.08f * (1f - t)))
                for (k in 0 until 5) {
                    val a = (k * 72f - 90f) * PI.toFloat() / 180f
                    val d = cellPx * 0.9f * t
                    drawPath(starPath(c.x + cos(a) * d, c.y + sin(a) * d, cellPx * 0.08f),
                        PinataHues[k].copy(alpha = 1f - t))
                }
            }
        }
    }
}

/**
 * **LA PIÑATA REVIENTA** (P-242): cinco estrellas grandes de sus colores salen despedidas, un
 * destello, y una estela cae desde la piñata hasta cada casilla donde aterriza un Teika — el premio
 * se ve llegar a su sitio. Después, una chispa hasta donde cuelga la siguiente.
 */
@Composable
fun PinataBurstVFX(
    events: List<GameEvent.PinataBurst>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                delay(event.delayMs.toLong())
                clock.animateTo(1f, tween(1000, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t <= 0f || t >= 1f) return@Canvas
                val c = Offset((event.cell.x + 0.5f) * cellPx, (event.cell.y + 0.5f) * cellPx)
                val burst = (t / 0.45f).coerceIn(0f, 1f)
                if (burst < 1f) {
                    val a = 1f - burst
                    drawCircle(Color.White.copy(alpha = 0.9f * a), cellPx * (0.4f + 1.2f * burst), c)
                    for (k in 0 until 5) {
                        val ang = (k * 72f - 90f) * PI.toFloat() / 180f
                        val d = cellPx * 1.6f * burst
                        drawPath(starPath(c.x + cos(ang) * d, c.y + sin(ang) * d + cellPx * burst * burst,
                            cellPx * 0.22f * a, rotDeg = -90f + 180f * burst), PinataHues[k].copy(alpha = a))
                    }
                }
                // Las estelas hasta los Teikas que caen.
                val fall = ((t - 0.1f) / 0.5f).coerceIn(0f, 1f)
                if (fall in 0.001f..0.999f) event.drops.forEachIndexed { i, d ->
                    val to = Offset((d.x + 0.5f) * cellPx, (d.y + 0.5f) * cellPx)
                    val p = Offset(c.x + (to.x - c.x) * fall, c.y + (to.y - c.y) * fall)
                    drawLine(PinataHues[i % PinataHues.size].copy(alpha = 0.7f * (1f - fall)), c, p,
                        strokeWidth = cellPx * 0.06f, cap = StrokeCap.Round)
                }
                val next = event.next ?: return@Canvas
                val travel = ((t - 0.5f) / 0.5f).coerceIn(0f, 1f)
                if (travel > 0f) {
                    val n = Offset((next.x + 0.5f) * cellPx, (next.y + 0.5f) * cellPx)
                    val p = Offset(c.x + (n.x - c.x) * travel, c.y + (n.y - c.y) * travel - cellPx * sin(PI.toFloat() * travel))
                    drawPath(starPath(p.x, p.y, cellPx * 0.14f), PinataHues[4])
                }
            }
        }
    }
}

// ───────────────────────────────────────── LA IMPLOSIÓN ────────────────────────────────────────

/**
 * **LA SUCCIÓN** (P-236): la línea rota se ilumina y unas estelas convergen sobre ella desde cada
 * bloque que aspira. Los bloques en sí los arrastra `WoodFall`; esto dice POR QUÉ se mueven.
 */
@Composable
fun LineImplosionVFX(
    events: List<GameEvent.LineImplosion>,
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
                clock.animateTo(1f, tween(460, easing = FastOutSlowInEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t >= 1f) return@Canvas
                val a = 1f - t
                // La línea se «cierra»: una banda que se estrecha hacia su centro.
                val band = cellPx * (1.6f - 1.2f * t)
                event.rows.forEach { y ->
                    val cy = (y + 0.5f) * cellPx
                    drawRect(ImplosionTint.copy(alpha = 0.30f * a), Offset(0f, cy - band / 2f),
                        androidx.compose.ui.geometry.Size(cols * cellPx, band))
                }
                event.cols.forEach { x ->
                    val cx = (x + 0.5f) * cellPx
                    drawRect(ImplosionTint.copy(alpha = 0.30f * a), Offset(cx - band / 2f, 0f),
                        androidx.compose.ui.geometry.Size(band, rows * cellPx))
                }
                event.pulls.forEach { (from, to) ->
                    val f = Offset((from.x + 0.5f) * cellPx, (from.y + 0.5f) * cellPx)
                    val d = Offset((to.x + 0.5f) * cellPx, (to.y + 0.5f) * cellPx)
                    val head = Offset(f.x + (d.x - f.x) * t, f.y + (d.y - f.y) * t)
                    drawLine(ImplosionTint.copy(alpha = 0.8f * a), f, head, strokeWidth = cellPx * 0.08f, cap = StrokeCap.Round)
                }
            }
        }
    }
}

// ───────────────────────────────────────── EL REBOTE ───────────────────────────────────────────

/**
 * **EL GOLPE EN CADA CUERPO** (P-237): en cada Teika que la línea empuja, un anillo que estalla y una
 * estela en la dirección en la que sale, más larga cuanto más fuerte le dio. Dice A QUIÉN empujó la
 * línea — la onda de `LineRecoilWaveVFX` solo decía que empujaba.
 */
@Composable
fun RecoilPushVFX(
    events: List<GameEvent.RecoilPush>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(380, easing = FastOutSlowInEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t >= 1f) return@Canvas
                val a = 1f - t
                event.pushes.forEach { p ->
                    val c = Offset(p.atX * cellPx, p.atY * cellPx)
                    drawCircle(RecoilTint.copy(alpha = 0.8f * a), cellPx * (0.3f + 0.6f * t * (0.5f + p.power)), c,
                        style = Stroke(cellPx * 0.07f * a))
                    val len = cellPx * (0.6f + 1.4f * p.power) * t
                    // La estela sale del lado contrario: el cuerpo va DELANTE de ella.
                    drawLine(RecoilTint.copy(alpha = 0.7f * a), c,
                        Offset(c.x - p.dirX * len, c.y - p.dirY * len),
                        strokeWidth = cellPx * (0.06f + 0.08f * p.power), cap = StrokeCap.Round)
                }
            }
        }
    }
}

/** Una flecha de [from] a [to] en píxeles, con su punta. */
internal fun DrawScope.drawArrow(from: Offset, to: Offset, color: Color, width: Float) {
    drawLine(color, from, to, strokeWidth = width, cap = StrokeCap.Round)
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
    val ux = dx / len; val uy = dy / len
    val h = width * 2.6f
    drawLine(color, to, Offset(to.x - ux * h - uy * h * 0.7f, to.y - uy * h + ux * h * 0.7f), strokeWidth = width, cap = StrokeCap.Round)
    drawLine(color, to, Offset(to.x - ux * h + uy * h * 0.7f, to.y - uy * h - ux * h * 0.7f), strokeWidth = width, cap = StrokeCap.Round)
}
