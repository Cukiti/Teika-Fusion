package com.cukiti.teikafusion.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.Position
import kotlin.math.PI
import kotlin.math.sin

/**
 * **Pantano Tóxico · EL VENENO ES UNA CAPA** (2026-09-28, decisión del usuario). Reglas en
 * [com.cukiti.teikafusion.model.ToxicBiomeRules].
 *
 * El usuario pidió un líquido **unificado**: los bloques envenenados vecinos no son charcos sueltos
 * sino una sola masa, con puentes entre casillas, un flujo animado en bucle perfecto y el avance
 * dibujado como líquido que pasa de una casilla a la vecina. Por eso el veneno ya no lo pinta cada
 * celda (el antiguo `PoisonOverlay` de `CellView`) sino esta capa, que ve el tablero entero:
 *
 *  - la superficie ondula con la X ABSOLUTA del tablero, así que dos casillas vecinas comparten ola;
 *  - una casilla con veneno encima se llena hasta arriba (el líquido baja de una a otra);
 *  - el contorno solo se dibuja donde el veneno acaba.
 *
 * Todo sale del reloj compartido [biomeClockSec] con fases de multiplicador ENTERO (como el viejo
 * `PoisonOverlay`): al reiniciarse el ciclo cada término recae en el mismo valor y no hay corte.
 *
 * Tres fases, como todo bioma (tope 1200 ms):
 *  1. **Telegrafía** — [ToxicLiquidLayer]: una lengua de líquido asoma hacia cada bloque que el veneno
 *     alcanzará si no rompes una línea; si ya no alcanza ninguno y está a punto de salpicar, la masa
 *     hierve con gotas que saltan.
 *  2. **Impacto** — [PoisonSpreadVFX]: el líquido FLUYE de la casilla a la vecina (o la gota vuela
 *     lejos, en la salpicadura).
 *  3. **Consecuencia** — la masa más grande; o, si rompiste una línea con veneno, [PoisonWashVFX]: el
 *     veneno se escurre y el bloque queda al descubierto.
 */

private val ToxicBody = Color(0xD03FA82E)
private val ToxicDeep = Color(0xC01C6B1A)
private val ToxicMeniscus = Color(0xFFC4FF93)
private val ToxicEdge = Color(0xFF35E04A)
private val ToxicFoam = Color(0xFFD6FFB0)

/** Periodo del ciclo del líquido. */
private const val TOXIC_PERIOD_SEC = 3.2f

/** Nivel de la superficie en una casilla sin veneno encima (fracción del lado, desde arriba). */
private const val SURFACE_LEVEL = 0.28f

/** Margen hasta el canto del bloque en los bordes donde el veneno acaba. */
private const val EDGE_INSET = 0.07f

private const val TWO_PI = (2.0 * PI).toFloat()

/** La ola de la superficie en la X absoluta [ax] (px). Multiplicadores de [phase] ENTEROS: bucle perfecto. */
private fun waveOffset(ax: Float, cellPx: Float, phase: Float): Float {
    val amp = cellPx * 0.05f
    val u = ax / cellPx
    return amp * sin(u * 0.5f * TWO_PI + phase * TWO_PI) +
        amp * 0.5f * sin(u * 1.1f * TWO_PI - phase * TWO_PI * 2f)
}

/**
 * TELEGRAFÍA + CONSECUENCIA: el veneno del tablero como una sola masa de líquido.
 *
 * @param poisoned casillas envenenadas que se ven (sin las que están volando ni las que el VFX de
 *   avance está llenando todavía).
 * @param front el avance del turno que viene: (origen, destino) de cada bloque que alcanzará.
 * @param splashSoon no alcanza ninguno y salpicará en la próxima colocación sin líneas.
 */
@Composable
fun ToxicLiquidLayer(
    poisoned: Set<Position>,
    front: List<Pair<Position, Position>>,
    splashSoon: Boolean,
    cellPx: Float,
    animationsOn: Boolean,
    modifier: Modifier = Modifier,
) {
    if (poisoned.isEmpty() || cellPx <= 0f) return
    // Paths reutilizados: cero asignaciones por frame (se hace reset() en cada pasada).
    val body = remember { Path() }
    val surface = remember { Path() }
    // Vecindad de cada casilla, UNA vez por cambio del veneno (auditoría de fluidez 2026-09-29): antes
    // cada fotograma creaba 8 `Position` por casilla (y 1 más en la salpicadura) solo para preguntar
    // por sus vecinas. Mismos datos, en arrays.
    val cells = remember(poisoned) { ToxicCells(poisoned) }
    val meniscus = remember(cellPx) { Stroke(width = cellPx * 0.025f, cap = StrokeCap.Round) }
    // Capa propia: el líquido ondula cada fotograma todo el capítulo.
    Canvas(modifier.fillMaxSize().graphicsLayer()) {
        val phase = if (animationsOn) (biomeClockSec.floatValue / TOXIC_PERIOD_SEC) % 1f else 0.25f
        val beat = ambientBeat(animationsOn, 1.1f)
        val c = cellPx
        val inset = c * EDGE_INSET
        val steps = 8

        body.reset()
        surface.reset()
        for (ci in 0 until cells.size) {
            val p = cells.pos[ci]
            val up = cells.up[ci]; val left = cells.left[ci]; val right = cells.right[ci]; val down = cells.down[ci]
            val x0 = p.x * c + if (left) 0f else inset
            val x1 = (p.x + 1) * c - if (right) 0f else inset
            val yBottom = (p.y + 1) * c - if (down) 0f else inset
            if (up) {
                // Lleno hasta arriba: el líquido baja sin corte desde la casilla de encima.
                body.moveTo(x0, p.y * c); body.lineTo(x1, p.y * c)
                body.lineTo(x1, yBottom); body.lineTo(x0, yBottom); body.close()
            } else {
                val base = p.y * c + c * SURFACE_LEVEL
                body.moveTo(x0, yBottom)
                for (i in 0..steps) {
                    val x = x0 + (x1 - x0) * i / steps
                    val y = base + waveOffset(x, c, phase)
                    body.lineTo(x, y)
                    if (i == 0) surface.moveTo(x, y) else surface.lineTo(x, y)
                }
                body.lineTo(x1, yBottom); body.close()
            }
        }
        drawPath(body, ToxicBody)
        drawPath(surface, ToxicMeniscus, style = meniscus)

        for (ci in 0 until cells.size) {
            val p = cells.pos[ci]
            val up = cells.up[ci]; val left = cells.left[ci]; val right = cells.right[ci]; val down = cells.down[ci]
            val x0 = p.x * c + if (left) 0f else inset
            val x1 = (p.x + 1) * c - if (right) 0f else inset
            val yTop = if (up) p.y * c else p.y * c + c * SURFACE_LEVEL
            val yBottom = (p.y + 1) * c - if (down) 0f else inset
            // Fondo más oscuro: da volumen sin partir la masa.
            if (!down) drawRect(ToxicDeep, Offset(x0, yBottom - c * 0.22f), Size(x1 - x0, c * 0.22f))
            // Contorno SOLO donde el veneno acaba.
            val edgeA = 0.55f + 0.3f * beat
            val w = c * 0.045f
            if (!left) drawLine(ToxicEdge.copy(alpha = edgeA), Offset(x0, yTop), Offset(x0, yBottom), w, StrokeCap.Round)
            if (!right) drawLine(ToxicEdge.copy(alpha = edgeA), Offset(x1, yTop), Offset(x1, yBottom), w, StrokeCap.Round)
            if (!down) drawLine(ToxicEdge.copy(alpha = edgeA), Offset(x0, yBottom), Offset(x1, yBottom), w, StrokeCap.Round)

            // EL FLUJO: brillos que viajan por los puentes, hacia la derecha y hacia abajo (bucle perfecto:
            // la fase entra con multiplicador entero y el desfase por casilla es constante).
            val seed = ((p.x * 7 + p.y * 13) % 10) / 10f
            if (right) {
                val k = (phase * 2f + seed) % 1f
                val fx = p.x * c + c * (0.5f + k)
                val fy = (yTop + yBottom) / 2f
                drawOval(ToxicFoam.copy(alpha = 0.45f * sin(k * PI.toFloat())),
                    Offset(fx - c * 0.16f, fy - c * 0.035f), Size(c * 0.32f, c * 0.07f))
            }
            if (down) {
                val k = (phase * 2f + seed + 0.5f) % 1f
                val fy = p.y * c + c * (0.5f + k)
                val fx = p.x * c + c * 0.5f
                drawOval(ToxicFoam.copy(alpha = 0.40f * sin(k * PI.toFloat())),
                    Offset(fx - c * 0.03f, fy - c * 0.14f), Size(c * 0.06f, c * 0.28f))
            }
            // Burbujas que suben (opacidad 0 al nacer y al estallar).
            for (i in 0 until 2) {
                val prog = (phase * 2f + i * 0.5f + seed) % 1f
                val bx = p.x * c + c * (0.25f + 0.5f * ((i * 0.47f + seed) % 1f))
                val by = yBottom - prog * (yBottom - yTop - c * 0.06f)
                val rise = sin(prog * PI.toFloat())
                drawCircle(ToxicFoam.copy(alpha = 0.55f * rise), c * 0.05f * (0.7f + 0.3f * prog), Offset(bx, by))
            }
        }

        // TELEGRAFÍA · la lengua de líquido asoma hacia cada bloque que alcanzará.
        for ((from, to) in front) {
            drawTongue(from, to, c, beat)
        }
        // TELEGRAFÍA · a punto de SALPICAR: la masa hierve y lanza gotitas que vuelven a caer.
        if (splashSoon) {
            for (ci in 0 until cells.size) {
                val p = cells.pos[ci]
                if (cells.up[ci]) continue
                val seed = ((p.x * 5 + p.y * 11) % 7) / 7f
                val k = (phase * 3f + seed) % 1f
                val hop = sin(k * PI.toFloat())
                val cx = p.x * c + c * (0.3f + 0.4f * seed)
                val cy = p.y * c + c * SURFACE_LEVEL - hop * c * 0.35f
                drawCircle(ToxicEdge.copy(alpha = 0.8f * hop), c * 0.07f, Offset(cx, cy))
                drawCircle(ToxicFoam.copy(alpha = 0.6f * hop), c * 0.025f, Offset(cx - c * 0.02f, cy - c * 0.02f))
            }
        }
    }
}

/** Las casillas envenenadas con sus cuatro vecinas ya resueltas (mismo orden que el `Set`). */
private class ToxicCells(poisoned: Set<Position>) {
    val pos: Array<Position> = poisoned.toTypedArray()
    val size = pos.size
    val up = BooleanArray(size) { Position(pos[it].x, pos[it].y - 1) in poisoned }
    val left = BooleanArray(size) { Position(pos[it].x - 1, pos[it].y) in poisoned }
    val right = BooleanArray(size) { Position(pos[it].x + 1, pos[it].y) in poisoned }
    val down = BooleanArray(size) { Position(pos[it].x, pos[it].y + 1) in poisoned }
}

/** La lengua que asoma de [from] hacia [to]: un saliente de líquido que late sobre el borde común. */
private fun DrawScope.drawTongue(from: Position, to: Position, c: Float, beat: Float) {
    val dx = to.x - from.x
    val dy = to.y - from.y
    val len = c * (0.12f + 0.10f * beat)
    val thick = c * 0.34f
    val cx = (from.x + 0.5f) * c + dx * c * 0.5f
    val cy = (from.y + 0.5f) * c + dy * c * 0.5f
    val (tl, size) = when {
        dx > 0 -> Offset(cx, cy - thick / 2f) to Size(len, thick)
        dx < 0 -> Offset(cx - len, cy - thick / 2f) to Size(len, thick)
        dy > 0 -> Offset(cx - thick / 2f, cy) to Size(thick, len)
        else -> Offset(cx - thick / 2f, cy - len) to Size(thick, len)
    }
    drawRoundRect(ToxicBody, tl, size, CornerRadius(thick / 2f))
    drawRoundRect(ToxicMeniscus.copy(alpha = 0.8f), tl, size, CornerRadius(thick / 2f), style = Stroke(c * 0.02f))
    // El bloque que va a caer: un canto verde que late.
    drawRoundRect(ToxicEdge.copy(alpha = 0.25f + 0.25f * beat), Offset(to.x * c + c * 0.06f, to.y * c + c * 0.06f),
        Size(c * 0.88f, c * 0.88f), CornerRadius(c * 0.16f), style = Stroke(c * 0.035f))
}

/** Duración del avance de una casilla a su vecina. */
private const val POISON_FLOW_MS = 520

/** Duración del vuelo de la salpicadura. */
private const val POISON_SPLASH_MS = 640

/**
 * IMPACTO: el veneno llega a [event.to]. En el avance normal el líquido CRUZA el borde común y llena
 * la casilla vecina desde ese lado; en la salpicadura, una gota vuela en arco y revienta sobre el
 * bloque. Mientras dura, [ToxicLiquidLayer] no pinta el destino (lo pinta esto) — el relevo es exacto
 * porque al terminar se consume el evento y la capa lo dibuja en el mismo frame.
 */
@Composable
fun PoisonSpreadVFX(event: GameEvent.PoisonSpread, cellSize: androidx.compose.ui.unit.Dp, onComplete: () -> Unit) {
    val anim = remember(event.id) { Animatable(0f) }
    val cs = with(androidx.compose.ui.platform.LocalDensity.current) { cellSize.toPx() }
    LaunchedEffect(event.id) {
        kotlinx.coroutines.delay(event.delayMs.toLong().coerceAtLeast(0L))
        anim.animateTo(1f, tween(if (event.splash) POISON_SPLASH_MS else POISON_FLOW_MS,
            easing = if (event.splash) LinearEasing else FastOutSlowInEasing))
        onComplete()
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = anim.value
        val to = event.to
        val inset = cs * EDGE_INSET
        if (!event.splash) {
            // El líquido entra por el lado de [from] y avanza hasta llenar la casilla al nivel de la masa.
            val dx = to.x - event.from.x
            val dy = to.y - event.from.y
            val x0 = to.x * cs + inset
            val x1 = (to.x + 1) * cs - inset
            val yTop = to.y * cs + cs * SURFACE_LEVEL
            val yBottom = (to.y + 1) * cs - inset
            val fill = FastOutSlowInEasing.transform(t)
            when {
                dx > 0 -> drawRect(ToxicBody, Offset(to.x * cs, yTop), Size((x1 - to.x * cs) * fill, yBottom - yTop))
                dx < 0 -> { val w = ((to.x + 1) * cs - x0) * fill; drawRect(ToxicBody, Offset((to.x + 1) * cs - w, yTop), Size(w, yBottom - yTop)) }
                dy > 0 -> drawRect(ToxicBody, Offset(x0, to.y * cs), Size(x1 - x0, (yBottom - to.y * cs) * fill))
                else -> { val h = (yBottom - yTop) * fill; drawRect(ToxicBody, Offset(x0, yBottom - h), Size(x1 - x0, h)) }
            }
            // El frente del líquido: una línea brillante que avanza.
            val frontA = 0.9f * (1f - t)
            when {
                dx != 0 -> {
                    val fx = if (dx > 0) to.x * cs + (x1 - to.x * cs) * fill else (to.x + 1) * cs - ((to.x + 1) * cs - x0) * fill
                    drawLine(ToxicMeniscus.copy(alpha = frontA), Offset(fx, yTop), Offset(fx, yBottom), cs * 0.05f, StrokeCap.Round)
                }
                else -> {
                    val fy = if (dy > 0) to.y * cs + (yBottom - to.y * cs) * fill else yBottom - (yBottom - yTop) * fill
                    drawLine(ToxicMeniscus.copy(alpha = frontA), Offset(x0, fy), Offset(x1, fy), cs * 0.05f, StrokeCap.Round)
                }
            }
        } else {
            // La salpicadura: arco alto, y al caer revienta en una mancha que se asienta.
            val from = Offset((event.from.x + 0.5f) * cs, (event.from.y + SURFACE_LEVEL) * cs)
            val dest = Offset((to.x + 0.5f) * cs, (to.y + 0.5f) * cs)
            val fly = (t / 0.7f).coerceIn(0f, 1f)
            if (t < 0.7f) {
                val pos = from + (dest - from) * fly - Offset(0f, cs * 1.6f * sin(fly * PI.toFloat()))
                drawCircle(ToxicBody, cs * 0.16f, pos)
                drawCircle(ToxicFoam.copy(alpha = 0.7f), cs * 0.05f, pos - Offset(cs * 0.04f, cs * 0.04f))
                for (k in 1..3) {
                    val fk = (fly - k * 0.06f).coerceAtLeast(0f)
                    val trail = from + (dest - from) * fk - Offset(0f, cs * 1.6f * sin(fk * PI.toFloat()))
                    drawCircle(ToxicEdge.copy(alpha = 0.35f * (1f - k / 4f)), cs * (0.10f - k * 0.02f), trail)
                }
            } else {
                val k = (t - 0.7f) / 0.3f
                drawRoundRect(ToxicBody.copy(alpha = ToxicBody.alpha * k),
                    Offset(to.x * cs + inset, to.y * cs + cs * SURFACE_LEVEL),
                    Size(cs - 2 * inset, cs * (1f - SURFACE_LEVEL) - inset), CornerRadius(cs * 0.12f))
                for (i in 0 until 6) {
                    val ang = i * (TWO_PI / 6f)
                    val r = cs * (0.2f + 0.5f * k)
                    drawCircle(ToxicEdge.copy(alpha = 1f - k), cs * 0.05f * (1f - k),
                        dest + Offset(kotlin.math.cos(ang) * r, sin(ang) * r))
                }
            }
        }
    }
}

/** Duración del lavado. */
private const val POISON_WASH_MS = 460

/**
 * CONSECUENCIA buena: la línea LAVÓ el veneno de [event.cell]. El líquido se escurre hacia abajo y
 * gotea fuera del bloque, que queda al descubierto (sin romperse: se rompe limpio en la siguiente).
 */
@Composable
fun PoisonWashVFX(event: GameEvent.PoisonWash, cellSize: androidx.compose.ui.unit.Dp, onComplete: () -> Unit) {
    val anim = remember(event.id) { Animatable(0f) }
    var started by remember(event.id) { mutableStateOf(event.delayMs <= 0) }
    val cs = with(androidx.compose.ui.platform.LocalDensity.current) { cellSize.toPx() }
    LaunchedEffect(event.id) {
        if (event.delayMs > 0) kotlinx.coroutines.delay(event.delayMs.toLong())
        started = true
        anim.animateTo(1f, tween(POISON_WASH_MS, easing = FastOutSlowInEasing))
        onComplete()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = event.cell
        val inset = cs * EDGE_INSET
        val t = if (started) anim.value else 0f
        // La película baja y se estrecha hasta desaparecer.
        val yTop = p.y * cs + cs * SURFACE_LEVEL + (cs * (1f - SURFACE_LEVEL) - inset) * t
        val yBottom = (p.y + 1) * cs - inset
        if (yBottom > yTop) {
            drawRect(ToxicBody.copy(alpha = ToxicBody.alpha * (1f - t)), Offset(p.x * cs + inset, yTop),
                Size(cs - 2 * inset, yBottom - yTop))
        }
        // Gotas que se escurren por debajo del bloque.
        for (i in 0 until 3) {
            val gx = p.x * cs + cs * (0.25f + 0.25f * i)
            val gy = yBottom + cs * 0.6f * t * t * (1f + 0.3f * i)
            drawCircle(ToxicEdge.copy(alpha = (1f - t)), cs * 0.05f, Offset(gx, gy))
        }
    }
}
