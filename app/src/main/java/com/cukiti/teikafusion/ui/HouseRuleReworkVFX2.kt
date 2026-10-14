package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.cukiti.teikafusion.model.BoardCurrent
import com.cukiti.teikafusion.model.CellContent
import com.cukiti.teikafusion.model.ChainReaction
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.model.TeikaType
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 *  SEGUNDA TANDA DEL REWORK DE REGLAS DE LA CASA (2026-09-22): la noria, los barriles y el huevo.
 *
 *  Mismo contrato que la primera tanda (ver `HouseRuleReworkVFX.kt`): cada regla dibuja lo que HAY,
 *  lo que PASARÍA si sueltas aquí (en `HouseRuleDragPreviewLayer`) y lo que ACABA de pasar.
 *
 *  Vocabulario: la noria es el ACERO de la familia de la corriente (la misma que la cinta a la que
 *  sustituye), el barril es naranja con aros oscuros y mecha encendida, y el huevo es crema con
 *  motas. Ninguno reutiliza el dorado ni el neón azul.
 * ═══════════════════════════════════════════════════════════════════════════════════════════════
 */

private val WheelSteel = Color(0xFF7CC6FF)
private val WheelHot = Color(0xFFFFE08A)
private val BarrelBody = Color(0xFFE0592A)
private val BarrelBand = Color(0xFF5E2410)
private val BarrelTop = Color(0xFFF58B52)
private val BarrelFire = Color(0xFFFFB02E)
private val EggShell = Color(0xFFFFF3D6)
private val EggShade = Color(0xFFE8D2A6)
private val EggSpot = Color(0xFFC9A36B)
private val EggGlow = Color(0xFFFFE9A8)

/** Lo que tarda un galón de la noria en recorrer una casilla. */
private const val WHEEL_BEAT_SEC = 1.6f
/** Entre una oleada de barriles y la siguiente: lo bastante para que se VEA la cadena. */
private const val BARREL_WAVE_MS = 150
private const val BARREL_BLAST_MS = 520

// ───────────────────────────────────────── LA NORIA ────────────────────────────────────────────

/**
 * Punto del recorrido de la noria en la fracción [u] (0..1) del anillo, por el CENTRO de las
 * casillas del borde y en el sentido de las agujas del reloj desde la esquina de arriba a la
 * izquierda — el mismo orden que [BoardCurrent.ringOf].
 */
internal fun ringPoint(u: Float, cols: Int, rows: Int, cellPx: Float): Offset {
    val w = (cols - 1).toFloat()
    val h = (rows - 1).toFloat()
    val total = 2f * (w + h)
    var d = (((u % 1f) + 1f) % 1f) * total
    val x: Float
    val y: Float
    when {
        d < w -> { x = d; y = 0f }
        d < w + h -> { d -= w; x = w; y = d }
        d < 2f * w + h -> { d -= w + h; x = w - d; y = h }
        else -> { d -= 2f * w + h; x = 0f; y = h - d }
    }
    return Offset((x + 0.5f) * cellPx, (y + 0.5f) * cellPx)
}

/**
 * **LA NORIA EN REPOSO** (P-231): el borde del tablero dibujado como una VÍA que gira — dos raíles
 * (el marco y el interior del anillo) y galones que desfilan en el sentido del giro.
 *
 * Se ve desde el turno 1 y todo el nivel, como la cinta a la que sustituye: el jugador tiene que
 * saber que el borde se mueve para poder usarlo. Cuando **la próxima colocación hace girar la
 * noria**, los galones se encienden y aceleran — es la cuenta atrás, dicha sin un número. En la
 * esquina de arriba a la izquierda, además, unas marcas cuentan las colocaciones que faltan.
 */
@Composable
fun WheelRingLayer(
    cols: Int,
    rows: Int,
    direction: Int,
    turnsUntil: Int,
    period: Int,
    cellSize: Dp,
    animationsOn: Boolean,
) {
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val rail = remember(cellPx) { Stroke(width = cellPx * 0.05f) }
    val inminente = turnsUntil <= 1
    Canvas(Modifier.fillMaxSize()) {
        val clock = if (animationsOn) biomeClockSec.floatValue else 0f
        val w = cols * cellPx
        val h = rows * cellPx
        val tint = if (inminente) WheelHot else WheelSteel
        // La banda del anillo: el borde entero, apenas más claro.
        drawRect(tint.copy(alpha = 0.07f), Offset.Zero, Size(w, cellPx))
        drawRect(tint.copy(alpha = 0.07f), Offset(0f, h - cellPx), Size(w, cellPx))
        drawRect(tint.copy(alpha = 0.07f), Offset(0f, cellPx), Size(cellPx, h - 2 * cellPx))
        drawRect(tint.copy(alpha = 0.07f), Offset(w - cellPx, cellPx), Size(cellPx, h - 2 * cellPx))
        // Los dos raíles: el de fuera sigue el marco, el de dentro separa el anillo del centro quieto.
        val inset = cellPx * 0.06f
        drawRect(tint.copy(alpha = 0.40f), Offset(inset, inset), Size(w - inset * 2, h - inset * 2), style = rail)
        drawRect(tint.copy(alpha = 0.40f), Offset(cellPx, cellPx), Size(w - 2 * cellPx, h - 2 * cellPx), style = rail)
        // Los galones: uno por casilla del anillo, desfilando en el sentido del giro.
        val n = 2 * (cols - 1) + 2 * (rows - 1)
        val speed = if (inminente) 2.2f else 1f
        val shift = (clock * speed / WHEEL_BEAT_SEC) % 1f
        val dir = if (direction >= 0) 1f else -1f
        val alpha = if (inminente) 0.55f + 0.35f * ambientBeat(animationsOn, 0.5f) else 0.45f
        for (i in 0 until n) {
            val u = (i + dir * shift) / n
            val p = ringPoint(u, cols, rows, cellPx)
            val ahead = ringPoint(u + dir * 0.25f / n, cols, rows, cellPx)
            val dx = ahead.x - p.x
            val dy = ahead.y - p.y
            val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
            val tx = dx / len; val ty = dy / len
            val nx = -ty; val ny = tx
            val s = cellPx * 0.16f
            val tip = Offset(p.x + tx * s, p.y + ty * s)
            drawLine(tint.copy(alpha = alpha), Offset(p.x - tx * s + nx * s, p.y - ty * s + ny * s), tip,
                strokeWidth = cellPx * 0.06f, cap = StrokeCap.Round)
            drawLine(tint.copy(alpha = alpha), Offset(p.x - tx * s - nx * s, p.y - ty * s - ny * s), tip,
                strokeWidth = cellPx * 0.06f, cap = StrokeCap.Round)
        }
        // Las marcas de la cuenta atrás, en la esquina: llenas las colocaciones que faltan.
        val gap = cellPx * 0.16f
        for (i in 0 until period) {
            val c = Offset(cellPx * 0.18f + gap * i, cellPx * 0.18f)
            if (i < turnsUntil) drawCircle(tint.copy(alpha = 0.95f), cellPx * 0.055f, c)
            else drawCircle(tint.copy(alpha = 0.4f), cellPx * 0.05f, c, style = Stroke(cellPx * 0.02f))
        }
    }
}

/** El barrido de la noria: chispas que dan la vuelta al marco (aviso: finas; giro: brillantes). */
@Composable
fun WheelFlowVFX(
    event: GameEvent.BoardCurrentFlow,
    cols: Int,
    rows: Int,
    cellSize: Dp,
    onComplete: () -> Unit,
) {
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val clock = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        clock.animateTo(1f, tween(event.durationMs, easing = LinearEasing))
        onComplete()
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val dir = if (event.direction >= 0) 1f else -1f
        val count = if (event.warning) 6 else 12
        val fade = sin(PI.toFloat() * t)
        val tint = if (event.warning) WheelSteel else WheelHot
        for (i in 0 until count) {
            val base = i.toFloat() / count
            // Cada chispa recorre UNA casilla del anillo por cada casilla que gira la noria: se ve
            // dar la vuelta al marco, no cruzar el tablero.
            val head = base + dir * t * (if (event.warning) 0.5f else 1f) / count * 4f
            for (k in 0 until 4) {
                val u = head - dir * k * 0.006f
                val a = fade * (if (event.warning) 0.35f else 0.8f) * (1f - k / 4f)
                drawCircle(tint.copy(alpha = a), cellPx * (0.09f - k * 0.015f), ringPoint(u, cols, rows, cellPx))
            }
        }
    }
}

// ───────────────────────────────────────── LOS BARRILES ────────────────────────────────────────

/** Un barril centrado en ([cx], [cy]) en un cuadro de lado [s]; [spark] 0..1 hace latir la mecha. */
internal fun DrawScope.drawBarrel(cx: Float, cy: Float, s: Float, spark: Float, alpha: Float = 1f) {
    val bw = s * 0.46f
    val bh = s * 0.52f
    val left = cx - bw / 2f
    val top = cy - bh / 2f + s * 0.05f
    drawRoundRect(BarrelBody.copy(alpha = alpha), Offset(left, top), Size(bw, bh), CornerRadius(bw * 0.28f))
    drawRect(BarrelBand.copy(alpha = alpha), Offset(left, top + bh * 0.22f), Size(bw, bh * 0.10f))
    drawRect(BarrelBand.copy(alpha = alpha), Offset(left, top + bh * 0.68f), Size(bw, bh * 0.10f))
    drawOval(BarrelTop.copy(alpha = alpha), Offset(left + bw * 0.08f, top - bh * 0.06f), Size(bw * 0.84f, bh * 0.20f))
    // La mecha y su chispa.
    val fx = cx + bw * 0.18f
    drawLine(BarrelBand.copy(alpha = alpha), Offset(fx, top), Offset(fx + s * 0.06f, top - s * 0.14f),
        strokeWidth = s * 0.045f, cap = StrokeCap.Round)
    val sc = Offset(fx + s * 0.07f, top - s * 0.16f)
    drawCircle(BarrelFire.copy(alpha = alpha * (0.55f + 0.45f * spark)), s * (0.06f + 0.04f * spark), sc)
    drawCircle(Color.White.copy(alpha = alpha * 0.9f), s * 0.025f, sc)
}

/**
 * **LOS BARRILES PUESTOS** (P-234): cada bloque-barril del tablero lleva su barril encima, con la
 * mecha encendida. Encima de los bloques: el barril ES lo que el bloque va a hacer.
 */
@Composable
fun BarrelLayer(
    grid: Map<Position, CellContent>,
    cellSize: Dp,
    animationsOn: Boolean,
) {
    val barrels = remember(grid) {
        grid.entries.filter { (it.value as? CellContent.Wood)?.barrel == true }.map { it.key }
    }
    if (barrels.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    Canvas(Modifier.fillMaxSize()) {
        val spark = ambientBeat(animationsOn, 0.7f)
        barrels.forEach { p ->
            drawBarrel((p.x + 0.5f) * cellPx, (p.y + 0.5f) * cellPx, cellPx, spark)
        }
    }
}

/**
 * **LA REACCIÓN EN CADENA** (P-234): oleada a oleada, cada barril estalla — bola de fuego, onda y
 * cascotes — y su 3×3 destella. La pausa entre oleadas es la que hace que se lea como CADENA.
 */
@Composable
fun BarrelBlastVFX(
    events: List<GameEvent.BarrelBlast>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val total = BARREL_BLAST_MS + BARREL_WAVE_MS * (event.waves.size - 1).coerceAtLeast(0)
            val clock = remember(event.id) { Animatable(0f) }
            val areas = remember(event.id) {
                event.waves.map { wave -> wave.flatMap { ChainReaction.blastArea(it) }.toSet() }
            }
            LaunchedEffect(event.id) {
                delay(event.delayMs.toLong())
                clock.animateTo(1f, tween(total, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val ms = clock.value * total
                if (clock.value <= 0f || clock.value >= 1f) return@Canvas
                event.waves.forEachIndexed { i, wave ->
                    val local = (ms - i * BARREL_WAVE_MS) / BARREL_BLAST_MS
                    if (local <= 0f || local >= 1f) return@forEachIndexed
                    val a = 1f - local
                    areas[i].forEach { c ->
                        drawRect(BarrelFire.copy(alpha = 0.55f * a), Offset(c.x * cellPx, c.y * cellPx), Size(cellPx, cellPx))
                    }
                    wave.forEach { p ->
                        val center = Offset((p.x + 0.5f) * cellPx, (p.y + 0.5f) * cellPx)
                        drawCircle(BarrelFire.copy(alpha = 0.85f * a), cellPx * (0.4f + 1.1f * local), center)
                        drawCircle(Color.White.copy(alpha = 0.9f * a * a), cellPx * (0.25f + 0.5f * local), center)
                        drawCircle(
                            BarrelBody.copy(alpha = a), cellPx * (0.5f + 1.3f * local), center,
                            style = Stroke(width = cellPx * 0.12f * a),
                        )
                        for (k in 0 until 8) {
                            val ang = (k * 45f + 20f) * PI.toFloat() / 180f
                            val d = cellPx * (0.3f + 1.6f * local)
                            drawRect(
                                BarrelBand.copy(alpha = a),
                                Offset(center.x + cos(ang) * d, center.y + sin(ang) * d + cellPx * local * local),
                                Size(cellPx * 0.12f, cellPx * 0.12f),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────────────────────── EL HUEVO ────────────────────────────────────────────

/** Un huevo centrado en ([cx], [cy]) en un cuadro de lado [s], con [cracks] grietas dibujadas. */
internal fun DrawScope.drawEgg(cx: Float, cy: Float, s: Float, cracks: Int, wobble: Float = 0f) {
    val ew = s * 0.58f
    val eh = s * 0.74f
    val left = cx - ew / 2f + wobble
    val top = cy - eh / 2f
    drawOval(EggShade, Offset(left, top + eh * 0.05f), Size(ew, eh))
    drawOval(EggShell, Offset(left, top), Size(ew, eh * 0.96f))
    drawCircle(EggSpot.copy(alpha = 0.8f), s * 0.05f, Offset(left + ew * 0.32f, top + eh * 0.38f))
    drawCircle(EggSpot.copy(alpha = 0.8f), s * 0.035f, Offset(left + ew * 0.66f, top + eh * 0.56f))
    drawCircle(EggSpot.copy(alpha = 0.8f), s * 0.03f, Offset(left + ew * 0.42f, top + eh * 0.72f))
    drawCircle(Color.White.copy(alpha = 0.7f), s * 0.05f, Offset(left + ew * 0.30f, top + eh * 0.20f))
    // Las grietas: un zigzag por grieta, a distintas alturas.
    for (k in 0 until cracks.coerceAtMost(3)) {
        val y = top + eh * (0.35f + 0.18f * k)
        val x0 = left + ew * 0.15f
        val step = ew * 0.14f
        var px = x0
        var py = y
        for (j in 0 until 5) {
            val nx = px + step
            val ny = y + (if (j % 2 == 0) -1f else 1f) * s * 0.05f
            drawLine(Color(0xFF6B4A22), Offset(px, py), Offset(nx, ny), strokeWidth = s * 0.03f, cap = StrokeCap.Round)
            px = nx; py = ny
        }
    }
}

/**
 * **EL HUEVO EN REPOSO** (P-235): el huevo en su casilla, sus grietas y —lo que lo hace legible—
 * **la silueta del Teika que va a nacer**, dibujada a su tamaño real alrededor: el jugador ve
 * cuánto sitio va a ocupar antes de romperlo. Y dos guías punteadas por su fila y su columna, que
 * enseñan la regla sin texto: las líneas que pasan por aquí lo agrietan.
 */
@Composable
fun EggLayer(
    egg: Position?,
    cracks: Int,
    needed: Int,
    hatchType: TeikaType,
    cols: Int,
    rows: Int,
    cellSize: Dp,
    animationsOn: Boolean,
) {
    if (egg == null) return
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val dash = remember(cellPx) {
        Stroke(width = cellPx * 0.04f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(cellPx * 0.10f, cellPx * 0.14f)))
    }
    Canvas(Modifier.fillMaxSize()) {
        val beat = ambientBeat(animationsOn, 1.8f)
        val c = Offset((egg.x + 0.5f) * cellPx, (egg.y + 0.5f) * cellPx)
        // Las guías: su fila y su columna.
        drawLine(EggGlow.copy(alpha = 0.22f), Offset(0f, c.y), Offset(cols * cellPx, c.y),
            strokeWidth = cellPx * 0.04f, pathEffect = dash.pathEffect)
        drawLine(EggGlow.copy(alpha = 0.22f), Offset(c.x, 0f), Offset(c.x, rows * cellPx),
            strokeWidth = cellPx * 0.04f, pathEffect = dash.pathEffect)
        // La silueta de lo que va a nacer, a tamaño real (diámetro = tier en casillas).
        val r = hatchType.level * 0.5f * cellPx
        val cerca = cracks >= needed - 1
        drawCircle(EggGlow.copy(alpha = if (cerca) 0.14f + 0.12f * beat else 0.07f), r, c)
        drawCircle(EggGlow.copy(alpha = if (cerca) 0.6f + 0.3f * beat else 0.35f), r, c, style = dash)
        drawEgg(c.x, c.y, cellPx, cracks)
        // Grietas que faltan: tantos puntos como pide, llenos los ya hechos.
        val gap = cellPx * 0.16f
        val startX = c.x - gap * (needed - 1) / 2f
        for (i in 0 until needed) {
            val p = Offset(startX + gap * i, (egg.y + 1) * cellPx - cellPx * 0.1f)
            if (i < cracks) drawCircle(Color(0xFF6B4A22), cellPx * 0.05f, p)
            else drawCircle(EggShade, cellPx * 0.05f, p, style = Stroke(cellPx * 0.02f))
        }
    }
}

/** **LA GRIETA DEL HUEVO** (P-235): el huevo tiembla y suelta esquirlas de cáscara. */
@Composable
fun EggCrackVFX(
    events: List<GameEvent.EggCrack>,
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
                drawCircle(Color.White.copy(alpha = 0.7f * (1f - t)), cellPx * (0.3f + 0.4f * t), c,
                    style = Stroke(cellPx * 0.08f * (1f - t)))
                for (k in 0 until 6) {
                    val ang = (k * 60f - 90f) * PI.toFloat() / 180f
                    val d = cellPx * 0.7f * t
                    drawRect(EggShell.copy(alpha = 1f - t),
                        Offset(c.x + cos(ang) * d, c.y + sin(ang) * d + cellPx * 0.5f * t * t),
                        Size(cellPx * 0.1f, cellPx * 0.07f))
                }
            }
        }
    }
}

/**
 * **EL HUEVO ECLOSIONA** (P-235): la cáscara se abre en dos mitades que salen despedidas, destello,
 * y una chispa vuela hasta donde aparece el huevo siguiente.
 */
@Composable
fun EggHatchVFX(
    events: List<GameEvent.EggHatch>,
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
                clock.animateTo(1f, tween(900, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = clock.value
                if (t <= 0f || t >= 1f) return@Canvas
                val c = Offset((event.cell.x + 0.5f) * cellPx, (event.cell.y + 0.5f) * cellPx)
                val open = (t / 0.5f).coerceIn(0f, 1f)
                if (open < 1f) {
                    val a = 1f - open
                    val lift = cellPx * 0.9f * open
                    drawOval(EggShell.copy(alpha = a),
                        Offset(c.x - cellPx * 0.3f - cellPx * 0.6f * open, c.y - cellPx * 0.3f - lift),
                        Size(cellPx * 0.3f, cellPx * 0.4f))
                    drawOval(EggShell.copy(alpha = a),
                        Offset(c.x + cellPx * 0.6f * open, c.y - cellPx * 0.3f - lift),
                        Size(cellPx * 0.3f, cellPx * 0.4f))
                    val r = event.type.level * 0.5f * cellPx
                    drawCircle(EggGlow.copy(alpha = 0.8f * a), r * (0.6f + 0.6f * open), c)
                    drawCircle(Color.White.copy(alpha = 0.9f * a), r * (0.8f + 0.7f * open), c,
                        style = Stroke(cellPx * 0.08f * a))
                }
                val next = event.next ?: return@Canvas
                val travel = ((t - 0.35f) / 0.65f).coerceIn(0f, 1f)
                if (travel > 0f) {
                    val n = Offset((next.x + 0.5f) * cellPx, (next.y + 0.5f) * cellPx)
                    val arc = -cellPx * 1.4f * sin(PI.toFloat() * travel)
                    val p = Offset(c.x + (n.x - c.x) * travel, c.y + (n.y - c.y) * travel + arc)
                    drawCircle(EggGlow.copy(alpha = 0.5f), cellPx * 0.2f, p)
                    drawCircle(Color.White, cellPx * 0.07f, p)
                    if (travel > 0.85f) {
                        val pop = (travel - 0.85f) / 0.15f
                        drawCircle(EggGlow.copy(alpha = 1f - pop), cellPx * (0.2f + 0.4f * pop), n,
                            style = Stroke(cellPx * 0.06f))
                    }
                }
            }
        }
    }
}
