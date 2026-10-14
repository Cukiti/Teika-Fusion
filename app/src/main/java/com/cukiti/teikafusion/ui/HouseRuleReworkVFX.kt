package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.cukiti.teikafusion.model.CellContent
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.model.MagnetPiece
import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.model.StainedGlass
import com.cukiti.teikafusion.viewmodel.TeikaBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *  LA PRIMERA TANDA DEL REWORK DE REGLAS DE LA CASA (2026-09-22): vidriera, imán y plomo.
 *
 *  El encargo del usuario vino con una condición: «si alguna necesita feedback para saber qué es lo
 *  que tiene que hacer el jugador, hay que hacer el feedback». Por eso cada regla tiene TRES cosas
 *  dibujadas, y ninguna es decoración:
 *
 *   1. **Lo que hay** (capa persistente): la vidriera y sus grietas, el imán con su campo y sus
 *      turnos, el cuerpo de plomo que ya cae con fuerza para aplastar y el bloque que va a aplastar.
 *   2. **Lo que va a pasar si sueltas aquí** (aviso del arrastre): qué vidrios agrietaría esta
 *      ficha y, si es la última grieta, QUÉ se llevaría el estallido; hasta dónde llegaría el imán
 *      y a qué Teikas alcanzaría.
 *   3. **Lo que acaba de pasar** (evento de un solo uso): la grieta, el estallido y la vidriera que
 *      nace en otro sitio; la onda del imán al ponerlo y su rotura; el golpe de plomo.
 *
 *  Vocabulario (ver `docs/vocabulario-marcas-tablero.md`): el VIDRIO es un rombo facetado con
 *  plomo oscuro —ninguna marca del tablero usaba ya esa forma—, el imán es una herradura roja y el
 *  plomo es pizarra con una mirilla naranja. Ni dorado ni neón azul: los dos ya dicen otras cosas.
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 */

private val GlassHues = listOf(
    Color(0xFFB57EDC), // violeta
    Color(0xFF4DD9C8), // turquesa
    Color(0xFFFF7FB0), // rosa
    Color(0xFF8FD16A), // verde
)
private val GlassLead = Color(0xFF231A3A)
private val GlassBlast = Color(0xFFFFB3D9)
private val MagnetRed = Color(0xFFE8394A)
private val MagnetDark = Color(0xFF8E1622)
private val MagnetSilver = Color(0xFFE3E9EE)
private val MagnetField = Color(0xFFFF6B81)
private val LeadSlate = Color(0xFF5B6770)
private val LeadAim = Color(0xFFFF7A3D)

/** El latido de la vidriera: lento, es un objeto quieto. */
private const val GLASS_BEAT_SEC = 2.4f
/** El pulso del campo del imán. */
private const val MAGNET_FIELD_SEC = 1.6f

// ───────────────────────────────────────── LA VIDRIERA ─────────────────────────────────────────

/**
 * **LA VIDRIERA EN REPOSO** (P-244): cada vidrio es un rombo de cuatro facetas de colores con su
 * plomo; el agrietado lleva la grieta blanca encima y brilla más.
 *
 * Se dibuja **ENCIMA de los bloques**, como la celda caliente: el vidrio es una marca del SUELO y la
 * regla habla de por dónde pasa la línea, no de qué bloque lo tapa. Translúcido para que la madera
 * se siga leyendo debajo.
 *
 * **Cuando solo queda un vidrio sano**, la capa dibuja además las cruces que se llevará el
 * estallido. Es la mitad jugable de la regla: el jugador tiene que poder decidir CUÁNDO romperla, y
 * eso exige ver qué va a abrir.
 */
@Composable
fun StainedGlassLayer(
    glass: Set<Position>,
    cracked: Set<Position>,
    cellSize: Dp,
    animationsOn: Boolean,
    // P-293: los vidrios con un bloque encima se retiran al borde (un lado de cada color).
    covered: Set<Position> = emptySet(),
) {
    if (glass.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val sanos = glass.count { it !in cracked }
    val area = remember(glass) { StainedGlass.shatterArea(glass) }
    Spacer(
        Modifier.fillMaxSize().drawWithCache {
            // Todo lo que no cambia frame a frame se construye una vez: las cuatro facetas y la
            // grieta de cada vidrio. En el dibujo solo se lee el latido.
            val panes = glass.filter { it !in covered }.map { p -> p to glassPanePaths(p, cellPx) }
            val cubiertos = glass.filter { it in covered }
            val cracks = glass.filter { it in cracked && it !in covered }.map { glassCrackPath(it, cellPx) }
            val lead = Stroke(width = cellPx * 0.055f)
            val crackStroke = Stroke(width = cellPx * 0.045f, cap = StrokeCap.Round)
            val dash = Stroke(
                width = cellPx * 0.05f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(cellPx * 0.14f, cellPx * 0.10f)),
            )
            onDrawBehind {
                val beat = ambientBeat(animationsOn, GLASS_BEAT_SEC)
                panes.forEach { (p, facets) ->
                    val roto = p in cracked
                    val alpha = if (roto) 0.42f + 0.18f * beat else 0.26f + 0.08f * beat
                    facets.forEachIndexed { i, path ->
                        drawPath(path, GlassHues[i].copy(alpha = alpha))
                        drawPath(path, GlassLead.copy(alpha = 0.80f), style = lead)
                    }
                }
                cracks.forEach { path ->
                    drawPath(path, Color.White.copy(alpha = 0.85f), style = crackStroke)
                }
                // P-293: bajo un bloque, el vidrio es su borde de cuatro colores; el agrietado, más vivo.
                cubiertos.forEach { p ->
                    val roto = p in cracked
                    drawCellEdgeMark(p, cellPx, GlassHues, if (roto) 0.80f + 0.20f * beat else 0.60f + 0.10f * beat)
                }
                // A UNA GRIETA DE ESTALLAR: se enseña lo que se llevará.
                if (sanos == 1) {
                    val inset = cellPx * 0.10f
                    area.forEach { a ->
                        drawRect(
                            color = GlassBlast.copy(alpha = 0.10f + 0.12f * beat),
                            topLeft = Offset(a.x * cellPx + inset, a.y * cellPx + inset),
                            size = Size(cellPx - inset * 2f, cellPx - inset * 2f),
                        )
                        drawRect(
                            color = GlassBlast.copy(alpha = 0.45f + 0.35f * beat),
                            topLeft = Offset(a.x * cellPx + inset, a.y * cellPx + inset),
                            size = Size(cellPx - inset * 2f, cellPx - inset * 2f),
                            style = dash,
                        )
                    }
                }
            }
        }
    )
}

/** Las cuatro facetas triangulares del rombo de un vidrio, en el orden de [GlassHues]. */
private fun glassPanePaths(p: Position, cellPx: Float): List<Path> {
    val inset = cellPx * 0.10f
    val l = p.x * cellPx + inset
    val t = p.y * cellPx + inset
    val r = (p.x + 1) * cellPx - inset
    val b = (p.y + 1) * cellPx - inset
    val cx = (l + r) / 2f
    val cy = (t + b) / 2f
    val top = Offset(cx, t); val right = Offset(r, cy); val bottom = Offset(cx, b); val left = Offset(l, cy)
    val c = Offset(cx, cy)
    fun tri(a: Offset, b2: Offset) = Path().apply {
        moveTo(c.x, c.y); lineTo(a.x, a.y); lineTo(b2.x, b2.y); close()
    }
    return listOf(tri(left, top), tri(top, right), tri(right, bottom), tri(bottom, left))
}

/** Grieta de un vidrio: una estrella de cuatro ramas quebradas, fija por posición (no parpadea). */
private fun glassCrackPath(p: Position, cellPx: Float): Path {
    val cx = (p.x + 0.5f) * cellPx
    val cy = (p.y + 0.5f) * cellPx
    val seed = (p.x * 31 + p.y * 17) % 7
    return Path().apply {
        for (i in 0 until 4) {
            val ang = (i * 90f + 25f + seed * 9f) * PI.toFloat() / 180f
            val mid = cellPx * 0.18f
            val far = cellPx * 0.34f
            moveTo(cx, cy)
            lineTo(cx + cos(ang) * mid, cy + sin(ang) * mid)
            val bend = ang + 0.45f
            lineTo(cx + cos(bend) * far, cy + sin(bend) * far)
        }
    }
}

/** **LA GRIETA** (P-244): destello blanco y la grieta abriéndose desde el centro del vidrio. */
@Composable
fun GlassCrackVFX(
    events: List<GameEvent.GlassCrack>,
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
                clock.animateTo(1f, tween(GLASS_CRACK_MS, easing = FastOutSlowInEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t <= 0f || t >= 1f) return@Canvas
                event.cells.forEach { p ->
                    val cx = (p.x + 0.5f) * cellPx
                    val cy = (p.y + 0.5f) * cellPx
                    drawRect(
                        color = Color.White.copy(alpha = 0.75f * (1f - t)),
                        topLeft = Offset(p.x * cellPx, p.y * cellPx),
                        size = Size(cellPx, cellPx),
                    )
                    for (i in 0 until 6) {
                        val ang = (i * 60f + 15f) * PI.toFloat() / 180f
                        val len = cellPx * 0.45f * t
                        drawLine(
                            color = Color.White.copy(alpha = 0.9f * (1f - t)),
                            start = Offset(cx, cy),
                            end = Offset(cx + cos(ang) * len, cy + sin(ang) * len),
                            strokeWidth = cellPx * 0.05f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
            }
        }
    }
}

private const val GLASS_CRACK_MS = 460

/**
 * **EL ESTALLIDO** (P-244): los vidrios revientan en esquirlas de sus colores, la cruz de cada uno
 * destella, y unas chispas vuelan de la vidriera vieja a la NUEVA — el jugador tiene que ver adónde
 * se ha ido, o la regla parecería haberse acabado.
 */
@Composable
fun GlassShatterVFX(
    events: List<GameEvent.GlassShatter>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            val viejos = remember(event.id) { event.glass.toList() }
            val nuevos = remember(event.id) { event.next.toList() }
            LaunchedEffect(event.id) {
                delay(event.delayMs.toLong())
                clock.animateTo(1f, tween(GLASS_SHATTER_MS, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t <= 0f || t >= 1f) return@Canvas
                // Fase 1 (0 → 0,4): esquirlas y destello de las cruces.
                val burst = (t / 0.4f).coerceIn(0f, 1f)
                if (burst < 1f) {
                    event.cleared.forEach { a ->
                        drawRect(
                            color = GlassBlast.copy(alpha = 0.7f * (1f - burst)),
                            topLeft = Offset(a.x * cellPx, a.y * cellPx),
                            size = Size(cellPx, cellPx),
                        )
                    }
                    viejos.forEach { p ->
                        val cx = (p.x + 0.5f) * cellPx
                        val cy = (p.y + 0.5f) * cellPx
                        for (i in 0 until 8) {
                            val ang = (i * 45f + (p.x * 13 + p.y * 7) % 30) * PI.toFloat() / 180f
                            val d = cellPx * (0.2f + 1.3f * burst)
                            val sx = cx + cos(ang) * d
                            val sy = cy + sin(ang) * d + cellPx * 0.6f * burst * burst
                            val s = cellPx * 0.16f * (1f - burst * 0.6f)
                            drawRect(
                                color = GlassHues[i % GlassHues.size].copy(alpha = 1f - burst),
                                topLeft = Offset(sx - s / 2f, sy - s / 2f),
                                size = Size(s, s * 0.6f),
                            )
                        }
                    }
                }
                // Fase 2 (0,35 → 1): chispas de cada vidrio viejo a uno nuevo, y pop al llegar.
                val travel = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                if (travel > 0f && nuevos.isNotEmpty()) {
                    viejos.forEachIndexed { i, from ->
                        val to = nuevos[i % nuevos.size]
                        val ax = (from.x + 0.5f) * cellPx; val ay = (from.y + 0.5f) * cellPx
                        val bx = (to.x + 0.5f) * cellPx; val by = (to.y + 0.5f) * cellPx
                        val arc = -cellPx * 1.2f * sin(PI.toFloat() * travel)
                        val px = ax + (bx - ax) * travel
                        val py = ay + (by - ay) * travel + arc
                        val hue = GlassHues[i % GlassHues.size]
                        drawCircle(hue.copy(alpha = 0.35f), cellPx * 0.22f, Offset(px, py))
                        drawCircle(Color.White.copy(alpha = 0.9f), cellPx * 0.08f, Offset(px, py))
                    }
                    if (travel > 0.8f) {
                        val pop = (travel - 0.8f) / 0.2f
                        nuevos.forEach { p ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.8f * (1f - pop)),
                                radius = cellPx * (0.2f + 0.45f * pop),
                                center = Offset((p.x + 0.5f) * cellPx, (p.y + 0.5f) * cellPx),
                                style = Stroke(width = cellPx * 0.06f),
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val GLASS_SHATTER_MS = 1000

// ───────────────────────────────────────── EL IMÁN ─────────────────────────────────────────────

/**
 * La herradura del imán, centrada en ([cx], [cy]) dentro de un cuadro de lado [size]. Sin asignar:
 * el trazo lo trae el que llama, cacheado.
 */
internal fun DrawScope.drawHorseshoe(cx: Float, cy: Float, size: Float, stroke: Stroke, alpha: Float = 1f) {
    val w = stroke.width
    val r = size * 0.24f
    val arcY = cy + size * 0.04f
    val armTop = cy - size * 0.26f
    drawArc(
        color = MagnetRed.copy(alpha = alpha),
        startAngle = 0f, sweepAngle = 180f, useCenter = false,
        topLeft = Offset(cx - r, arcY - r), size = Size(r * 2f, r * 2f),
        style = stroke,
    )
    drawLine(MagnetRed.copy(alpha = alpha), Offset(cx - r, arcY), Offset(cx - r, armTop + w * 0.5f), w)
    drawLine(MagnetRed.copy(alpha = alpha), Offset(cx + r, arcY), Offset(cx + r, armTop + w * 0.5f), w)
    // Sombra interior de la curva: da volumen sin un Brush.
    drawArc(
        color = MagnetDark.copy(alpha = alpha * 0.6f),
        startAngle = 20f, sweepAngle = 140f, useCenter = false,
        topLeft = Offset(cx - r, arcY - r), size = Size(r * 2f, r * 2f),
        style = Stroke(width = w * 0.35f),
    )
    // Los polos plateados.
    drawRect(MagnetSilver.copy(alpha = alpha), Offset(cx - r - w / 2f, armTop - w * 0.3f), Size(w, w * 0.9f))
    drawRect(MagnetSilver.copy(alpha = alpha), Offset(cx + r - w / 2f, armTop - w * 0.3f), Size(w, w * 0.9f))
}

/**
 * **EL IMÁN PUESTO** (P-240): la herradura sobre su bloque, el campo que late hasta su alcance, un
 * hilo hacia cada Teika que está atrayendo y **las marcas de los turnos que le quedan** encima.
 *
 * Los hilos son la mitad legible: sin ellos, un cuerpo que sube despacio hacia un bloque se lee como
 * física rara. Las posiciones se leen en DIBUJO, nunca en el cuerpo del composable.
 */
@Composable
fun MagnetLayer(
    grid: Map<Position, CellContent>,
    turnsLeft: Int,
    range: Float,
    bodiesFlow: StateFlow<List<TeikaBody>>,
    cellSize: Dp,
    animationsOn: Boolean,
    // (2026-09-25) Si el imán va volando a su casilla (gravedad, Corriente…), espera a que encaje.
    hidden: Set<Position> = emptySet(),
) {
    if (turnsLeft <= 0 || range <= 0f) return
    val cell = remember(grid) {
        grid.entries.firstOrNull { (it.value as? CellContent.Wood)?.magnet == true }?.key
    }?.takeIf { it !in hidden } ?: return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val bodiesState = bodiesFlow.collectAsState()
    val iconStroke = remember(cellPx) { Stroke(width = cellPx * 0.16f) }
    val ringStroke = remember(cellPx) { Stroke(width = cellPx * 0.05f) }
    // El reloj también se cuenta en la marca: `TURNS + 1` es el turno de la propia colocación.
    val restantes = turnsLeft.coerceAtMost(MagnetPiece.TURNS)
    Canvas(Modifier.fillMaxSize()) {
        val cx = (cell.x + 0.5f) * cellPx
        val cy = (cell.y + 0.5f) * cellPx
        val rangePx = range * cellPx
        val clockSec = if (animationsOn) biomeClockSec.floatValue else 0f
        // Tres anillos que salen del imán hacia su alcance: la onda ES el alcance.
        for (i in 0 until 3) {
            val phase = ((clockSec / MAGNET_FIELD_SEC) + i / 3f) % 1f
            val rr = cellPx * 0.5f + (rangePx - cellPx * 0.5f) * phase
            drawCircle(MagnetField.copy(alpha = 0.32f * (1f - phase)), rr, Offset(cx, cy), style = ringStroke)
        }
        // El borde del alcance, quieto y tenue: hasta aquí llega.
        drawCircle(MagnetField.copy(alpha = 0.14f), rangePx, Offset(cx, cy), style = ringStroke)
        // Un hilo hacia cada cuerpo que está tirando.
        val bodies = bodiesState.value
        for (b in bodies) {
            if (b.frozenTurns > 0 || b.kinematic) continue
            val dx = cx / cellPx - b.x
            val dy = cy / cellPx - b.y
            val d = sqrt(dx * dx + dy * dy)
            val pull = MagnetPiece.pullAt(d, range)
            if (pull <= 0f || d < 1e-3f) continue
            val sx = (b.x + dx / d * b.radius) * cellPx
            val sy = (b.y + dy / d * b.radius) * cellPx
            drawLine(
                color = MagnetField.copy(alpha = 0.25f + 0.45f * pull),
                start = Offset(sx, sy), end = Offset(cx, cy),
                strokeWidth = cellPx * 0.04f, cap = StrokeCap.Round,
            )
            // Una chispa viaja por el hilo hacia el imán: dice DIRECCIÓN, no solo «unidos».
            val u = ((clockSec * 1.4f) + b.x * 0.13f) % 1f
            drawCircle(
                Color.White.copy(alpha = 0.8f),
                cellPx * 0.06f,
                Offset(sx + (cx - sx) * u, sy + (cy - sy) * u),
            )
        }
        drawHorseshoe(cx, cy + cellPx * 0.02f, cellPx, iconStroke)
        // Las marcas de turnos: TURNS puntos encima de la casilla, llenos los que quedan.
        val gap = cellPx * 0.2f
        val startX = cx - gap * (MagnetPiece.TURNS - 1) / 2f
        for (i in 0 until MagnetPiece.TURNS) {
            val center = Offset(startX + gap * i, cell.y * cellPx - cellPx * 0.08f)
            if (i < restantes) {
                drawCircle(MagnetRed, cellPx * 0.07f, center)
                drawCircle(Color.White.copy(alpha = 0.7f), cellPx * 0.07f, center, style = Stroke(cellPx * 0.02f))
            } else {
                drawCircle(Color.White.copy(alpha = 0.35f), cellPx * 0.06f, center, style = Stroke(cellPx * 0.02f))
            }
        }
    }
}

/** **EL IMÁN SE ENCIENDE** (P-240): una onda que sale hasta su alcance y un destello en la casilla. */
@Composable
fun MagnetPlacedVFX(
    events: List<GameEvent.MagnetPlaced>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(MAGNET_PLACED_MS, easing = FastOutSlowInEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t >= 1f) return@Canvas
                val c = Offset((event.position.x + 0.5f) * cellPx, (event.position.y + 0.5f) * cellPx)
                drawCircle(
                    MagnetField.copy(alpha = 0.8f * (1f - t)),
                    cellPx * 0.4f + event.range * cellPx * t,
                    c,
                    style = Stroke(width = cellPx * (0.14f * (1f - t) + 0.03f)),
                )
                drawCircle(Color.White.copy(alpha = 0.7f * (1f - t)), cellPx * 0.5f * (1f - t * 0.5f), c)
            }
        }
    }
}

private const val MAGNET_PLACED_MS = 560

/** **EL IMÁN SE ROMPE** (P-240): la herradura se parte en dos y suelta chispas. */
@Composable
fun MagnetBreakVFX(
    events: List<GameEvent.MagnetBreak>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val iconStroke = remember(cellPx) { Stroke(width = cellPx * 0.16f) }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(MAGNET_BREAK_MS, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t >= 1f) return@Canvas
                val cx = (event.position.x + 0.5f) * cellPx
                val cy = (event.position.y + 0.5f) * cellPx
                val split = cellPx * 0.5f * t
                val fall = cellPx * 0.8f * t * t
                val a = 1f - t
                // Dos mitades que se separan y caen: la herradura partida.
                clipRectHalf(cx, cy, cellPx, left = true) {
                    drawHorseshoe(cx - split, cy + fall, cellPx, iconStroke, a)
                }
                clipRectHalf(cx, cy, cellPx, left = false) {
                    drawHorseshoe(cx + split, cy + fall, cellPx, iconStroke, a)
                }
                for (i in 0 until 7) {
                    val ang = (i * 51f + 10f) * PI.toFloat() / 180f
                    val d = cellPx * 0.9f * t
                    drawCircle(
                        MagnetSilver.copy(alpha = a),
                        cellPx * 0.05f,
                        Offset(cx + cos(ang) * d, cy + sin(ang) * d),
                    )
                }
            }
        }
    }
}

private const val MAGNET_BREAK_MS = 480

private inline fun DrawScope.clipRectHalf(
    cx: Float, cy: Float, cellPx: Float, left: Boolean, block: DrawScope.() -> Unit,
) {
    val l = if (left) cx - cellPx * 2f else cx
    val r = if (left) cx else cx + cellPx * 2f
    drawContext.canvas.save()
    drawContext.canvas.clipRect(l, cy - cellPx * 2f, r, cy + cellPx * 2f)
    block()
    drawContext.canvas.restore()
}

// ───────────────────────────────────────── EL PLOMO ────────────────────────────────────────────

/**
 * **TEIKAS DE PLOMO, EN EL AIRE** (P-239): el cuerpo que ya cae lo bastante rápido para aplastar
 * lleva una estela de pizarra, y el bloque sobre el que va a caer, una mirilla naranja.
 *
 * Es el aviso que convierte «un bloque se ha roto solo» en «ese Teika lo ha aplastado al caer»: se
 * ve ANTES del golpe, así que el jugador aprende la regla mirando, y aprende a buscarla inclinando.
 * La mirilla solo aparece sobre un cuerpo ya ARMADO, nunca sobre uno que baja despacio.
 */
@Composable
fun LeadFallLayer(
    bodiesFlow: StateFlow<List<TeikaBody>>,
    grid: Map<Position, CellContent>,
    crushSpeed: Float,
    cellSize: Dp,
) {
    if (crushSpeed <= 0f) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val bodiesState = bodiesFlow.collectAsState()
    // Máscara de madera por celda: la búsqueda del blanco corre por frame y no puede asignar.
    val wood = remember(grid) {
        BooleanArray(GameState.GRID_SIZE * GameState.GRID_SIZE).also { mask ->
            grid.forEach { (p, c) ->
                if (c is CellContent.Wood && p.x in 0 until GameState.GRID_SIZE && p.y in 0 until GameState.GRID_SIZE) {
                    mask[p.y * GameState.GRID_SIZE + p.x] = true
                }
            }
        }
    }
    val aimStroke = remember(cellPx) { Stroke(width = cellPx * 0.07f, cap = StrokeCap.Round) }
    Canvas(Modifier.fillMaxSize()) {
        val armed = crushSpeed * 0.8f
        for (b in bodiesState.value) {
            if (b.frozenTurns > 0 || b.kinematic || b.vy < armed) continue
            val power = ((b.vy - armed) / (crushSpeed * 0.5f)).coerceIn(0.35f, 1f)
            val cx = b.x * cellPx
            val cy = b.y * cellPx
            val r = b.radius * cellPx
            // Estela: tres trazos de pizarra por encima del cuerpo.
            for (i in 1..3) {
                val off = r * 0.55f * i
                drawLine(
                    LeadSlate.copy(alpha = 0.45f * power / i),
                    Offset(cx - r * 0.5f, cy - r - off * 0.4f),
                    Offset(cx - r * 0.5f, cy - r - off),
                    strokeWidth = cellPx * 0.08f, cap = StrokeCap.Round,
                )
                drawLine(
                    LeadSlate.copy(alpha = 0.45f * power / i),
                    Offset(cx + r * 0.5f, cy - r - off * 0.4f),
                    Offset(cx + r * 0.5f, cy - r - off),
                    strokeWidth = cellPx * 0.08f, cap = StrokeCap.Round,
                )
            }
            drawCircle(LeadSlate.copy(alpha = 0.35f * power), r * 1.04f, Offset(cx, cy), style = aimStroke)
            // El blanco: el primer bloque debajo del centro del cuerpo.
            val col = floor(b.x).toInt()
            if (col !in 0 until GameState.GRID_SIZE) continue
            var y = floor(b.y + b.radius).toInt().coerceAtLeast(0)
            while (y < GameState.GRID_SIZE && !wood[y * GameState.GRID_SIZE + col]) y++
            if (y >= GameState.GRID_SIZE) continue
            val l = col * cellPx
            val t = y * cellPx
            val k = cellPx * 0.28f
            val a = 0.55f + 0.45f * power
            // Mirilla de cuatro esquinas: «aquí».
            drawLine(LeadAim.copy(alpha = a), Offset(l, t), Offset(l + k, t), aimStroke.width)
            drawLine(LeadAim.copy(alpha = a), Offset(l, t), Offset(l, t + k), aimStroke.width)
            drawLine(LeadAim.copy(alpha = a), Offset(l + cellPx, t), Offset(l + cellPx - k, t), aimStroke.width)
            drawLine(LeadAim.copy(alpha = a), Offset(l + cellPx, t), Offset(l + cellPx, t + k), aimStroke.width)
            drawLine(LeadAim.copy(alpha = a), Offset(l, t + cellPx), Offset(l + k, t + cellPx), aimStroke.width)
            drawLine(LeadAim.copy(alpha = a), Offset(l, t + cellPx), Offset(l, t + cellPx - k), aimStroke.width)
            drawLine(LeadAim.copy(alpha = a), Offset(l + cellPx, t + cellPx), Offset(l + cellPx - k, t + cellPx), aimStroke.width)
            drawLine(LeadAim.copy(alpha = a), Offset(l + cellPx, t + cellPx), Offset(l + cellPx, t + cellPx - k), aimStroke.width)
            // (P-293, 2026-09-25) Aquí iba un velo naranja sobre toda la casilla: teñía la cara del
            // bloque apuntado. La mirilla de cuatro esquinas ya dice «aquí» sin taparla.
        }
    }
}

/** **EL GOLPE DE PLOMO** (P-239): cráter, polvo que sale a los lados y grietas hacia abajo. */
@Composable
fun LeadCrushVFX(
    events: List<GameEvent.LeadCrush>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(LEAD_CRUSH_MS, easing = FastOutSlowInEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t >= 1f) return@Canvas
                val cx = (event.position.x + 0.5f) * cellPx
                val top = event.position.y * cellPx
                val a = 1f - t
                // Cráter: un óvalo que se abre sobre la cara de arriba del bloque.
                val w = cellPx * (0.6f + 1.2f * t)
                drawOval(
                    LeadSlate.copy(alpha = 0.7f * a),
                    topLeft = Offset(cx - w / 2f, top - cellPx * 0.12f),
                    size = Size(w, cellPx * 0.24f),
                    style = Stroke(width = cellPx * 0.07f),
                )
                // Polvo a los dos lados.
                for (i in 0 until 6) {
                    val side = if (i % 2 == 0) -1f else 1f
                    val d = cellPx * (0.3f + 0.9f * t) * (1f + i / 6f)
                    drawCircle(
                        Color(0xFFD9D2C5).copy(alpha = 0.55f * a),
                        cellPx * (0.10f + 0.12f * t),
                        Offset(cx + side * d, top - cellPx * 0.25f * t * (1 + i % 3)),
                    )
                }
                // Grietas que bajan: el golpe ATRAVIESA.
                for (i in -1..1) {
                    drawLine(
                        Color.White.copy(alpha = 0.8f * a),
                        Offset(cx + i * cellPx * 0.18f, top),
                        Offset(cx + i * cellPx * 0.28f, top + cellPx * 0.9f * t),
                        strokeWidth = cellPx * 0.04f, cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private const val LEAD_CRUSH_MS = 520

// ───────────────────────────────────── EL AVISO DEL ARRASTRE ───────────────────────────────────

/**
 * **LO QUE PASARÍA SI SUELTAS AQUÍ** (vidriera e imán).
 *
 *  - **Vidriera**: los vidrios que agrietaría esta ficha se iluminan con un contorno blanco que
 *    late; y si con ellos la vidriera ya no tiene vidrios sanos, se dibujan también las cruces que
 *    se llevaría el estallido. Es la regla entera explicada en el sitio donde se decide.
 *  - **Imán**: el círculo de su alcance alrededor de la sombra y un hilo hacia cada Teika que
 *    atraparía. El jugador ve a quién va a mover antes de soltarlo.
 */
@Composable
fun HouseRuleDragPreviewLayer(
    dragState: DragState,
    glass: Set<Position>,
    cracked: Set<Position>,
    magnetRange: Float,
    bodiesFlow: StateFlow<List<TeikaBody>>,
    cellSizePx: Float,
    modifier: Modifier = Modifier,
    // ── Segunda tanda (2026-09-22) ──
    grid: Map<Position, CellContent> = emptyMap(),
    barrelsActive: Boolean = false,
    egg: Position? = null,
    eggCracks: Int = 0,
    eggNeeded: Int = 0,
    eggHatchType: com.cukiti.teikafusion.model.TeikaType = com.cukiti.teikafusion.model.TeikaType.TEIKA_2,
    /** La noria gira con ESTA colocación: se enseña adónde viaja lo que la ficha deja en el borde. */
    wheelTurnsNow: Boolean = false,
    wheelDirection: Int = 1,
    cols: Int = GameState.GRID_SIZE,
    rows: Int = GameState.GRID_SIZE,
    // ── Tercera tanda (2026-09-22) ──
    /** P-237: velocidad del rebote del nivel (0 = sin rebote). */
    recoilSpeed: Float = 0f,
    pinata: Position? = null,
    pinataHits: Int = 0,
    pinataNeeded: Int = 0,
    // ── Cuarta tanda (2026-09-22) ──
    moldCells: Set<Position> = emptySet(),
    portals: Pair<Int, Int>? = null,
) {
    val drag = dragState as? DragState.Dragging ?: return
    val ghost = drag.ghostGridPosition ?: return
    if (!drag.isValidPlacement || cellSizePx <= 0f) return
    val isMagnet = drag.piece.isMagnet && magnetRange > 0f
    // Las casillas que limpiarían las líneas de esta colocación (el mismo tramo que pinta el aviso
    // de rotura), en una sola cuenta que comparten la vidriera, los barriles y el huevo.
    val lineCells = remember(drag.clearRowSpans, drag.clearCols) {
        buildSet {
            drag.clearRowSpans.forEach { (y, xs) -> xs.forEach { x -> add(Position(x, y)) } }
            drag.clearCols.forEach { x -> for (y in 0 until rows) add(Position(x, y)) }
        }
    }
    val predicted = remember(lineCells, glass, cracked) {
        if (glass.isEmpty()) emptySet() else glass.filterTo(LinkedHashSet()) { it !in cracked && it in lineCells }
    }
    // P-234: la cadena que desataría esta colocación, contando los barriles que trae la propia ficha.
    val chain = remember(lineCells, grid, drag.piece, ghost, barrelsActive) {
        if (!barrelsActive || lineCells.isEmpty()) null else {
            val propios = drag.piece.shape.indices
                .filter { drag.piece.barrelMask.getOrNull(it) == true }
                .map { Position(ghost.x + drag.piece.shape[it].x, ghost.y + drag.piece.shape[it].y) }
            val barrels = grid.entries.filter { (it.value as? CellContent.Wood)?.barrel == true }
                .mapTo(HashSet()) { it.key } + propios
            val seeds = barrels.filterTo(LinkedHashSet()) { it in lineCells }
            if (seeds.isEmpty()) null else com.cukiti.teikafusion.model.ChainReaction.resolve(seeds, barrels, cols, rows)
        }
    }
    // P-235: ¿agrieta el huevo? ¿lo abre?
    val eggHit = egg != null && egg in lineCells
    val eggHatches = eggHit && eggCracks + 1 >= eggNeeded
    // P-231: las casillas de la ficha que están en el borde, y adónde las lleva el giro.
    val wheelMoves = remember(ghost, drag.piece, wheelTurnsNow, wheelDirection, cols, rows) {
        if (!wheelTurnsNow) emptyList() else {
            val ring = com.cukiti.teikafusion.model.BoardCurrent.ringOf(cols, rows)
            val index = ring.withIndex().associate { it.value to it.index }
            drag.piece.shape.mapNotNull { off ->
                val p = Position(ghost.x + off.x, ghost.y + off.y)
                index[p]?.let { i -> p to ring[(i + (if (wheelDirection >= 0) 1 else -1) + ring.size) % ring.size] }
            }
        }
    }
    // P-242: ¿golpea la piñata? ¿la revienta?
    val pinataHit = pinata != null && pinata in lineCells
    val pinataBursts = pinataHit && pinataHits + 1 >= pinataNeeded
    val recoil = recoilSpeed > 0f && (drag.clearRowSpans.isNotEmpty() || drag.clearCols.isNotEmpty())
    // P-233: ¿encaja la ficha EXACTAMENTE en el molde? Entonces se enseña lo que se llevará.
    val footprint = remember(drag.piece, ghost) {
        drag.piece.shape.mapTo(HashSet()) { Position(ghost.x + it.x, ghost.y + it.y) }
    }
    val moldBlast = remember(footprint, moldCells) {
        if (com.cukiti.teikafusion.model.MoldRule.matches(footprint, moldCells))
            com.cukiti.teikafusion.model.MoldRule.blastArea(moldCells, cols, rows) else emptySet()
    }
    // P-243: ¿tapa la ficha la SALIDA de algún portal? Entonces ese portal se cerrará.
    val portalCells = remember(portals, cols) { portals?.let { com.cukiti.teikafusion.model.Portals.cellsOf(it, cols) } }
    val tapaIzq = portalCells != null && portalCells.second.any { it in footprint }   // cierra el izquierdo
    val tapaDer = portalCells != null && portalCells.first.any { it in footprint }    // cierra el derecho
    val bubbleCheck = lineCells.isNotEmpty()
    if (!isMagnet && predicted.isEmpty() && chain == null && !eggHit && wheelMoves.isEmpty() &&
        drag.implosionPulls.isEmpty() && !pinataHit && !recoil && moldBlast.isEmpty() &&
        !tapaIzq && !tapaDer && !bubbleCheck
    ) return
    val estalla = predicted.isNotEmpty() && glass.all { it in cracked || it in predicted }
    val area = remember(glass, estalla) { if (estalla) StainedGlass.shatterArea(glass) else emptySet() }
    val bodiesState = bodiesFlow.collectAsState()
    val dash = remember(cellSizePx) {
        Stroke(
            width = cellSizePx * 0.05f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(cellSizePx * 0.18f, cellSizePx * 0.12f)),
        )
    }
    val solid = remember(cellSizePx) { Stroke(width = cellSizePx * 0.07f) }
    Canvas(modifier.fillMaxSize()) {
        val beat = ambientBeat(true, 0.9f)
        predicted.forEach { g ->
            val inset = cellSizePx * 0.06f
            drawRect(
                Color.White.copy(alpha = 0.55f + 0.4f * beat),
                Offset(g.x * cellSizePx + inset, g.y * cellSizePx + inset),
                Size(cellSizePx - inset * 2f, cellSizePx - inset * 2f),
                style = solid,
            )
        }
        area.forEach { a ->
            val inset = cellSizePx * 0.08f
            drawRect(
                GlassBlast.copy(alpha = 0.22f + 0.18f * beat),
                Offset(a.x * cellSizePx + inset, a.y * cellSizePx + inset),
                Size(cellSizePx - inset * 2f, cellSizePx - inset * 2f),
            )
            drawRect(
                GlassBlast.copy(alpha = 0.85f),
                Offset(a.x * cellSizePx + inset, a.y * cellSizePx + inset),
                Size(cellSizePx - inset * 2f, cellSizePx - inset * 2f),
                style = dash,
            )
        }
        // P-234: el 3×3 de cada barril que estallaría, y la mecha de los que lo empiezan.
        chain?.let { ch ->
            ch.area.forEach { a ->
                val inset = cellSizePx * 0.06f
                drawRect(
                    Color(0xFFFFB02E).copy(alpha = 0.18f + 0.14f * beat),
                    Offset(a.x * cellSizePx + inset, a.y * cellSizePx + inset),
                    Size(cellSizePx - inset * 2f, cellSizePx - inset * 2f),
                )
                drawRect(
                    Color(0xFFFF7A3D).copy(alpha = 0.8f),
                    Offset(a.x * cellSizePx + inset, a.y * cellSizePx + inset),
                    Size(cellSizePx - inset * 2f, cellSizePx - inset * 2f),
                    style = dash,
                )
            }
            ch.waves.firstOrNull()?.forEach { p ->
                drawCircle(
                    Color.White.copy(alpha = 0.6f + 0.4f * beat), cellSizePx * 0.18f,
                    Offset((p.x + 0.5f) * cellSizePx, (p.y + 0.5f) * cellSizePx),
                    style = solid,
                )
            }
        }
        // P-235: la línea cruza el huevo — y si es su última grieta, la silueta de lo que nace.
        if (eggHit && egg != null) {
            val c = Offset((egg.x + 0.5f) * cellSizePx, (egg.y + 0.5f) * cellSizePx)
            drawCircle(Color.White.copy(alpha = 0.55f + 0.4f * beat), cellSizePx * 0.45f, c, style = solid)
            if (eggHatches) {
                val r = eggHatchType.level * 0.5f * cellSizePx
                drawCircle(Color(0xFFFFE9A8).copy(alpha = 0.25f + 0.2f * beat), r, c)
                drawCircle(Color(0xFFFFE9A8), r, c, style = dash)
            }
        }
        // P-231: la noria gira con esta colocación — flecha de cada casilla del borde a su destino.
        wheelMoves.forEach { (from, to) ->
            val a = Offset((from.x + 0.5f) * cellSizePx, (from.y + 0.5f) * cellSizePx)
            val b = Offset((to.x + 0.5f) * cellSizePx, (to.y + 0.5f) * cellSizePx)
            drawLine(Color(0xFFFFE08A).copy(alpha = 0.9f), a, b, strokeWidth = cellSizePx * 0.07f, cap = StrokeCap.Round)
            drawCircle(Color(0xFFFFE08A), cellSizePx * 0.09f, b)
        }
        // P-236: lo que aspiraría cada línea — una flecha por bloque, hacia el hueco.
        drag.implosionPulls.forEach { (from, to) ->
            drawArrow(
                Offset((from.x + 0.5f) * cellSizePx, (from.y + 0.5f) * cellSizePx),
                Offset((to.x + 0.5f) * cellSizePx, (to.y + 0.5f) * cellSizePx),
                Color(0xFFB9F3FF).copy(alpha = 0.7f + 0.3f * beat), cellSizePx * 0.07f,
            )
        }
        // P-237: el empujón que recibiría cada Teika — la MISMA cuenta que la física
        // ([LineRecoil.impulseFor]), sumando lo que le den todas las líneas de la jugada.
        if (recoil) {
            val rowsR = drag.clearRowSpans.map { it.first }.distinct()
            for (b in bodiesState.value) {
                if (b.frozenTurns > 0 || b.kinematic) continue
                var vy = 0f
                var vx = 0f
                rowsR.forEach { y -> vy += com.cukiti.teikafusion.model.LineRecoil.impulseFor(b.y, b.radius, y + 0.5f, recoilSpeed) }
                drag.clearCols.forEach { x -> vx += com.cukiti.teikafusion.model.LineRecoil.impulseFor(b.x, b.radius, x + 0.5f, recoilSpeed) }
                if (vx == 0f && vy == 0f) continue
                val c = Offset(b.x * cellSizePx, b.y * cellSizePx)
                val k = cellSizePx * 0.12f
                drawArrow(c, Offset(c.x + vx * k, c.y + vy * k), Color(0xFFFFF2B3), cellSizePx * 0.07f)
            }
        }
        // P-242: el golpe a la piñata — y si es el último, la estrella del reventón.
        if (pinataHit && pinata != null) {
            val c = Offset((pinata.x + 0.5f) * cellSizePx, (pinata.y + 0.5f) * cellSizePx)
            drawCircle(Color.White.copy(alpha = 0.6f + 0.4f * beat), cellSizePx * 0.5f, c, style = solid)
            if (pinataBursts) {
                drawCircle(Color(0xFFFF5FA2).copy(alpha = 0.25f + 0.2f * beat), cellSizePx * 0.9f, c)
                drawCircle(Color(0xFFFFE066), cellSizePx * 0.9f, c, style = dash)
            }
        }
        // P-233: encaja en el molde — el estallido que vendrá al cerrar el turno.
        moldBlast.forEach { a ->
            val inset = cellSizePx * 0.06f
            drawRect(Color(0xFFD9E4FF).copy(alpha = 0.22f + 0.2f * beat),
                Offset(a.x * cellSizePx + inset, a.y * cellSizePx + inset),
                Size(cellSizePx - inset * 2f, cellSizePx - inset * 2f))
            drawRect(Color.White.copy(alpha = 0.85f),
                Offset(a.x * cellSizePx + inset, a.y * cellSizePx + inset),
                Size(cellSizePx - inset * 2f, cellSizePx - inset * 2f), style = dash)
        }
        // P-238: la pompa que la línea de esta jugada golpearía (el cuerpo toca alguna casilla suya).
        if (bubbleCheck) for (b in bodiesState.value) {
            if (b.bubble <= 0) continue
            val toca = lineCells.any { c ->
                val nx = b.x.coerceIn(c.x.toFloat(), c.x + 1f)
                val ny = b.y.coerceIn(c.y.toFloat(), c.y + 1f)
                val dx = b.x - nx; val dy = b.y - ny
                dx * dx + dy * dy < b.radius * b.radius
            }
            if (!toca) continue
            val c = Offset(b.x * cellSizePx, b.y * cellSizePx)
            drawCircle(Color.White.copy(alpha = 0.6f + 0.4f * beat), b.radius * 1.35f * cellSizePx, c, style = dash)
        }
        // P-243: el portal que esta ficha dejaría cerrado, tachado.
        if (portals != null && (tapaIzq || tapaDer)) {
            fun tacha(left: Boolean, top: Int) {
                val cy = (top + com.cukiti.teikafusion.model.Portals.SPAN / 2f) * cellSizePx
                val cx = if (left) cellSizePx * 0.3f else cols * cellSizePx - cellSizePx * 0.3f
                val k = cellSizePx * 0.3f
                drawLine(Color(0xFFFF6B6B), Offset(cx - k, cy - k), Offset(cx + k, cy + k), cellSizePx * 0.09f, StrokeCap.Round)
                drawLine(Color(0xFFFF6B6B), Offset(cx - k, cy + k), Offset(cx + k, cy - k), cellSizePx * 0.09f, StrokeCap.Round)
            }
            if (tapaIzq) tacha(true, portals.first)
            if (tapaDer) tacha(false, portals.second)
        }
        if (isMagnet) {
            val mx = ghost.x + 0.5f
            val my = ghost.y + 0.5f
            val c = Offset(mx * cellSizePx, my * cellSizePx)
            drawCircle(MagnetField.copy(alpha = 0.10f), magnetRange * cellSizePx, c)
            drawCircle(MagnetField.copy(alpha = 0.75f), magnetRange * cellSizePx, c, style = dash)
            for (b in bodiesState.value) {
                if (b.frozenTurns > 0 || b.kinematic) continue
                val dx = mx - b.x
                val dy = my - b.y
                val d = sqrt(dx * dx + dy * dy)
                if (MagnetPiece.pullAt(d, magnetRange) <= 0f || d < 1e-3f) continue
                drawLine(
                    MagnetField.copy(alpha = 0.85f),
                    Offset((b.x + dx / d * b.radius) * cellSizePx, (b.y + dy / d * b.radius) * cellSizePx),
                    c,
                    strokeWidth = cellSizePx * 0.05f,
                    cap = StrokeCap.Round,
                    pathEffect = dash.pathEffect,
                )
                drawCircle(MagnetField, b.radius * cellSizePx * 1.05f, Offset(b.x * cellSizePx, b.y * cellSizePx), style = solid)
            }
        }
    }
}
