package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.model.KingPiece
import com.cukiti.teikafusion.model.Position
import kotlin.math.cos
import kotlin.math.sin

/**
 * **P-329 · LAS PIEZAS DEL REY** (2026-09-26) — lo que se ve cuando le toca a la pieza de ajedrez en
 * el reloj del Trono Real. Reglas en [KingPiece]. Referencia visual: `art/escena.mp4` (tablero
 * oscuro, casillas que brillan en cian o rojo, el caballo que cae a cámara lenta y estalla en luz).
 *
 * **Reparto Lottie / código** (decisión del usuario: «la mejor opción»): la PIEZA es Lottie
 * (`ajedrez_<pieza>_<color>`, generado por `tools/gen_biome_lottie.py`) porque es un personaje con
 * brillo propio; todo lo que toca el TABLERO —el camino que arde, la cámara lenta, las esquirlas, la
 * calle bloqueada— es código, porque depende de casillas que el Lottie no conoce.
 *
 * Tres fases, como todo evento de bioma (tope 1200 ms):
 *  1. **Telegrafía** — [KingPieceLayer]: la pieza en su puesto y su camino encendido; en el aviso 2 el
 *     camino arde y sus bloques tiemblan. Entrar tiene su VFX ([KingPieceRaiseVFX]).
 *  2. **Impacto** — [KingPieceStrikeVFX]: entrada rápida, **cámara lenta** mientras recorre el camino
 *     (o cuelga en el aire, el caballo) y el golpe en [KingPiece.IMPACT_PCT]: destello y esquirlas.
 *  3. **Consecuencia** — el camino vacío y BLOQUEADO una colocación ([KingLaneLayer]); si la retiraste,
 *     [KingPieceRetreatVFX].
 */

private val WhiteAura = Color(0xFF6FE3FF)
private val WhiteAuraLight = Color(0xFFD8F8FF)
private val BlackAura = Color(0xFFFF4D5E)
private val BlackAuraLight = Color(0xFFFFC2C8)

private fun aura(black: Boolean) = if (black) BlackAura else WhiteAura
private fun auraLight(black: Boolean) = if (black) BlackAuraLight else WhiteAuraLight

/**
 * Lado del recuadro de la pieza Lottie, en casillas. La figura ocupa ~72 % del alto del Lottie
 * (214 de 300 px), así que con 1,3 queda dentro de UNA casilla (~0,93 de alto, ~0,7 de ancho) y solo
 * el halo asoma un poco. Antes era 1,9, subida un cuarto de casilla y +15 % en el aviso 2: la pieza
 * ocupaba cuatro casillas y se salía del tablero (playtest del usuario, 2026-09-27).
 */
private const val PIECE_BOX_CELLS = 1.3f

/** Lo que dura la entrada de la pieza (aviso 1). */
const val KING_PIECE_RAISE_MS = 620

/** Lo que dura la retirada (consecuencia buena). */
const val KING_PIECE_RETREAT_MS = 640

private fun pieceRes(kind: Int, black: Boolean): Int = when (kind) {
    KingPiece.ROOK -> if (black) R.raw.ajedrez_torre_negra else R.raw.ajedrez_torre_blanca
    KingPiece.BISHOP -> if (black) R.raw.ajedrez_alfil_negra else R.raw.ajedrez_alfil_blanca
    else -> if (black) R.raw.ajedrez_caballo_negra else R.raw.ajedrez_caballo_blanca
}

/** Centro en píxeles de una casilla (admite casillas fuera del tablero, en coma flotante). */
private fun center(x: Float, y: Float, cellPx: Float) = Offset((x + 0.5f) * cellPx, (y + 0.5f) * cellPx)

private fun center(p: Position, cellPx: Float) = center(p.x.toFloat(), p.y.toFloat(), cellPx)

/**
 * El camino ORDENADO en el sentido en que la pieza lo recorre: desde el extremo por el que entra.
 * El caballo no recorre: su «camino» es el 3×3 donde cae.
 */
private fun orderedPath(kind: Int, a: Int, b: Int, fromEnd: Boolean): List<Position> {
    val cells = KingPiece.cells(kind, a, b)
    return if (fromEnd && kind != KingPiece.KNIGHT) cells.reversed() else cells
}

/**
 * Dónde espera la pieza durante los avisos: en la primera casilla de su camino (torre y alfil) o en
 * su casilla de salto, dentro del tablero (caballo). Dentro, siempre: fuera se cortaba contra el borde
 * de la pantalla, como le pasó a la espada.
 */
private fun restCell(kind: Int, a: Int, b: Int, fromEnd: Boolean): Pair<Float, Float> {
    if (kind == KingPiece.KNIGHT) {
        val (ox, oy) = KingPiece.origin(kind, a, b, fromEnd)
        val max = (GameState.GRID_SIZE - 1).toFloat()
        return ox.coerceIn(0f, max) to oy.coerceIn(0f, max)
    }
    val first = orderedPath(kind, a, b, fromEnd).firstOrNull() ?: return 0f to 0f
    return first.x.toFloat() to first.y.toFloat()
}

@Composable
private fun PieceLottie(kind: Int, black: Boolean, modifier: Modifier) {
    // Sin corte al aparecer: composición precargada y bucle en fase entre el VFX y la capa (BiomeLottie).
    BiomeLottie(pieceRes(kind, black), modifier)
}

/** La pieza Lottie centrada en [at] (un poco por encima: el pie queda sobre la casilla). */
@Composable
private fun PlacedPiece(
    kind: Int,
    black: Boolean,
    at: Offset,
    cellPx: Float,
    scale: Float = 1f,
    alpha: Float = 1f,
    rotation: Float = 0f,
    mirror: Boolean = false,
) {
    val density = LocalDensity.current
    val boxPx = cellPx * PIECE_BOX_CELLS
    Box(
        Modifier
            .size(with(density) { boxPx.toDp() })
            .graphicsLayer {
                translationX = at.x - boxPx / 2f
                // Centrada en su casilla: la figura ya está centrada en el Lottie (pie abajo, cabeza
                // arriba), así que subirla la sacaba de la casilla.
                translationY = at.y - boxPx / 2f
                scaleX = scale * (if (mirror) -1f else 1f); scaleY = scale
                rotationZ = rotation
                this.alpha = alpha
            }
    ) { PieceLottie(kind, black, Modifier.fillMaxSize()) }
}

/** Una casilla encendida del camino: relleno + canto, como las casillas del vídeo. */
private fun DrawScope.litCell(
    p: Position, cellPx: Float, color: Color, fillA: Float, strokeA: Float,
    /** El canto; quien pinta el camino cada fotograma pasa uno construido una vez. */
    stroke: Stroke = Stroke(cellPx * 0.05f),
) {
    val tl = Offset(p.x * cellPx, p.y * cellPx)
    val sz = Size(cellPx, cellPx)
    drawRect(color.copy(alpha = fillA), tl, sz)
    drawRect(color.copy(alpha = strokeA), tl + Offset(cellPx * 0.06f, cellPx * 0.06f),
        Size(cellPx * 0.88f, cellPx * 0.88f), style = stroke)
}

/** Puntea el tramo s→e con [steps] puntos (de [first] a [last] incluidos, en pasos de 1/[steps]). */
private inline fun DrawScope.dotted(s: Offset, e: Offset, steps: Int, first: Int, last: Int, draw: (Offset) -> Unit) {
    for (i in first..last) draw(s + (e - s) * (i / steps.toFloat()))
}

/** TELEGRAFÍA: la pieza en su puesto y su camino encendido; en el aviso 2, arde. */
@Composable
fun KingPieceLayer(
    kind: Int,
    a: Int,
    b: Int,
    black: Boolean,
    fromEnd: Boolean,
    idleTurns: Int,
    threshold: Int,
    cellPx: Float,
    animationsOn: Boolean,
    hidePiece: Boolean,
    modifier: Modifier = Modifier,
) {
    if (kind == KingPiece.NONE || cellPx <= 0f) return
    val glowing = idleTurns >= threshold - 1
    val path = remember(kind, a, b, fromEnd) { orderedPath(kind, a, b, fromEnd) }
    val (rx, ry) = remember(kind, a, b, fromEnd) { restCell(kind, a, b, fromEnd) }
    val color = aura(black)
    val edge = remember(cellPx) { Stroke(cellPx * 0.05f) }
    val ring = remember(cellPx) { Stroke(cellPx * 0.07f) }
    Box(modifier) {
        // Capa propia (auditoría de fluidez 2026-09-29): late cada fotograma durante el aviso.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val beat = ambientBeat(animationsOn, 0.8f)
            path.forEach { p ->
                litCell(p, cellPx, color,
                    fillA = if (glowing) 0.30f + 0.22f * beat else 0.10f + 0.06f * beat,
                    strokeA = if (glowing) 0.95f else 0.45f, stroke = edge)
            }
            if (kind == KingPiece.KNIGHT) {
                // El salto en L, punteado: de dónde sale a dónde cae.
                val from = center(rx, ry, cellPx)
                val to = center(a.toFloat(), b.toFloat(), cellPx)
                val corner = Offset(to.x, from.y)
                // Los dos tramos de la L, sin lista de pares por fotograma.
                val dotA = if (glowing) 0.9f else 0.5f
                dotted(from, corner, 6, 0, 5) { drawCircle(color, radius = cellPx * 0.06f, center = it, alpha = dotA) }
                dotted(corner, to, 6, 0, 5) { drawCircle(color, radius = cellPx * 0.06f, center = it, alpha = dotA) }
                // La diana donde caerá.
                drawCircle(color, radius = cellPx * (0.30f + 0.06f * beat), center = to,
                    style = ring, alpha = if (glowing) 1f else 0.6f)
            } else if (path.isNotEmpty()) {
                // La diana: donde se quedará, y la ÚNICA casilla por la que una línea la para (2026-09-28).
                drawCircle(color, radius = cellPx * (0.30f + 0.06f * beat), center = center(path.last(), cellPx),
                    style = ring, alpha = if (glowing) 1f else 0.6f)
                if (glowing) {
                    // La luz que recorre el camino: «por aquí pasará».
                    val k = (biomeClockSec.floatValue * 0.9f) % 1f
                    val s = center(path.first(), cellPx)
                    val e = center(path.last(), cellPx)
                    drawCircle(auraLight(black), radius = cellPx * 0.42f, center = s + (e - s) * k, alpha = 0.55f)
                }
            }
        }
        if (!hidePiece) {
            PlacedPiece(
                kind, black, center(rx, ry, cellPx), cellPx,
                // En el aviso 2 la pieza late con su camino, sin crecer fuera de la casilla.
                scale = if (glowing) 1.05f else 1f,
                mirror = kind == KingPiece.KNIGHT && a > rx,
            )
        }
    }
}

/** La pieza ENTRA (aviso 1): cae desde arriba sobre su puesto con un anillo de luz. */
@Composable
fun KingPieceRaiseVFX(event: GameEvent.KingPieceRaise, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(KING_PIECE_RAISE_MS, easing = FastOutSlowInEasing))
        onComplete()
    }
    val (rx, ry) = restCell(event.kind, event.a, event.b, event.fromEnd)
    val at = center(rx, ry, cellPx)
    val p = progress.value
    val color = aura(event.black)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(color, radius = cellPx * (0.4f + 1.3f * p), center = at,
                style = Stroke(cellPx * 0.08f * (1f - p)), alpha = 1f - p)
            drawCircle(auraLight(event.black), radius = cellPx * (0.5f + 1.0f * p), center = at, alpha = 0.45f * (1f - p))
        }
        PlacedPiece(
            event.kind, event.black, at - Offset(0f, cellPx * 1.6f * (1f - p)), cellPx,
            scale = 0.7f + 0.3f * p, alpha = p.coerceAtLeast(0.05f),
            mirror = event.kind == KingPiece.KNIGHT && event.a > rx,
        )
    }
}

/**
 * **El reloj de la cámara lenta.** Recibe el tiempo real del golpe (0..1) y devuelve cuánto ha
 * avanzado la pieza por su recorrido (0..1): la entrada es rápida (hasta [KingPiece.SLOWMO_FROM_PCT]
 * cubre el 20 % del recorrido), la cámara lenta ocupa hasta [KingPiece.IMPACT_PCT] y cubre casi todo el
 * resto (el mundo va despacio, la pieza también), y tras el golpe la pieza sale disparada.
 */
private fun slowMo(t: Float): Float {
    val from = KingPiece.SLOWMO_FROM_PCT / 100f
    val hit = KingPiece.IMPACT_PCT / 100f
    return when {
        t < from -> 0.2f * FastOutSlowInEasing.transform(t / from)
        t < hit -> 0.2f + 0.8f * ((t - from) / (hit - from))
        else -> 1f
    }
}

/** ¿Estamos dentro de la cámara lenta? 0 fuera, 1 en pleno (con rampas suaves de entrada y salida). */
private fun slowMoWeight(t: Float): Float {
    val from = KingPiece.SLOWMO_FROM_PCT / 100f
    val hit = KingPiece.IMPACT_PCT / 100f
    if (t <= from || t >= hit + 0.08f) return 0f
    val rampIn = ((t - from) / 0.06f).coerceIn(0f, 1f)
    val rampOut = if (t < hit) 1f else 1f - (t - hit) / 0.08f
    return rampIn * rampOut
}

/**
 * IMPACTO: la pieza arrasa su camino. Torre y alfil lo RECORREN a cámara lenta resquebrajando lo que
 * pisan; el caballo SALTA y cuelga en el aire. En [KingPiece.IMPACT_PCT] todo estalla a la vez
 * —destello, onda y esquirlas— y es justo cuando el motor vacía las casillas.
 */
@Composable
fun KingPieceStrikeVFX(event: GameEvent.KingPieceStrike, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }
    val skin = LocalBlockSkin.current
    val t = progress.value
    val hit = KingPiece.IMPACT_PCT / 100f
    val s = slowMo(t)
    val slow = slowMoWeight(t)
    val after = ((t - hit) / (1f - hit)).coerceIn(0f, 1f)
    val color = aura(event.black)
    val light = auraLight(event.black)
    val knight = event.kind == KingPiece.KNIGHT
    val path = remember(event.id) { orderedPath(event.kind, event.a, event.b, event.fromEnd) }
    val (rx, ry) = remember(event.id) { restCell(event.kind, event.a, event.b, event.fromEnd) }
    val start = center(rx, ry, cellPx)
    val target = center(event.a.toFloat(), event.b.toFloat(), cellPx)
    val end = if (knight) target else path.lastOrNull()?.let { center(it, cellPx) } ?: start
    // Dónde va la pieza: torre y alfil en línea; el caballo en parábola, con el vértice alto.
    val piecePos = if (knight) {
        val lin = start + (end - start) * s
        lin - Offset(0f, cellPx * 2.4f * sin(s * Math.PI.toFloat()))
    } else {
        // Si se QUEDA, frena en su casilla de llegada; si no, sale disparada.
        val exit = if (event.stays) Offset.Zero else (end - start) * (0.25f * after)
        start + (end - start) * s + exit
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            // La cámara lenta oscurece el tablero y deja la luz solo en el camino.
            if (slow > 0f) drawRect(Color.Black.copy(alpha = 0.38f * slow))
            val pathA = if (t < hit) 0.35f + 0.45f * slow else 0.8f * (1f - after)
            path.forEach { p -> litCell(p, cellPx, color, fillA = pathA * 0.6f, strokeA = pathA) }

            // Grietas: torre y alfil resquebrajan lo que ya han pisado; el caballo, al caer, todo.
            if (t < hit) {
                event.cuts.forEach { c ->
                    val reached = if (knight) s > 0.85f
                    else path.indexOf(c.cell).let { it >= 0 && it <= s * (path.size - 1) }
                    if (!reached) return@forEach
                    val cc = center(c.cell, cellPx)
                    for (k in 0 until 3) {
                        val ang = (c.cell.x * 1.7f + c.cell.y * 2.3f + k * 2.1f)
                        drawLine(light.copy(alpha = 0.9f), cc,
                            cc + Offset(cos(ang), sin(ang)) * (cellPx * 0.42f), cellPx * 0.035f, StrokeCap.Round)
                    }
                }
            }

            // El rastro de la cámara lenta: ecos de luz detrás de la pieza.
            if (slow > 0f) {
                for (k in 1..4) {
                    val sk = (s - k * 0.05f).coerceAtLeast(0f)
                    val echo = if (knight) start + (end - start) * sk - Offset(0f, cellPx * 2.4f * sin(sk * Math.PI.toFloat()))
                    else start + (end - start) * sk
                    drawCircle(color, radius = cellPx * (0.55f - k * 0.08f), center = echo - Offset(0f, cellPx * 0.25f),
                        alpha = 0.28f * slow * (1f - k / 5f))
                }
            }

            // EL GOLPE: destello de todo el tablero, onda y esquirlas.
            if (t >= hit) {
                val flash = (1f - after / 0.35f).coerceIn(0f, 1f)
                drawRect(light.copy(alpha = 0.45f * flash))
                val waveCenter = if (knight) target else (start + end) / 2f
                val waveR = cellPx * (0.5f + 5.5f * after)
                drawCircle(Brush.radialGradient(listOf(Color.Transparent, color.copy(alpha = 0.7f * (1f - after)), Color.Transparent),
                    waveCenter, waveR), radius = waveR, center = waveCenter)
                drawCircle(Color.White, radius = waveR * 0.92f, center = waveCenter,
                    style = Stroke(cellPx * 0.06f * (1f - after)), alpha = 1f - after)
                event.cuts.forEach { c -> shards(c, cellPx, after, skin.colorsFor(c.colorType)[0], light) }
            }
        }
        val pieceAlpha = if (t < hit || event.stays) 1f else (1f - after * 1.6f).coerceIn(0f, 1f)
        if (pieceAlpha > 0f) {
            PlacedPiece(
                event.kind, event.black, piecePos, cellPx,
                // En el aire el caballo crece (se acerca a la cámara); al caer, aplasta.
                scale = if (knight) 1f + 0.45f * sin(s * Math.PI.toFloat()) + (if (t >= hit) 0.2f * (1f - after) else 0f)
                else if (event.stays && t >= hit) 1.1f - 0.1f * after
                else 1.1f + 0.15f * slow,
                alpha = pieceAlpha,
                rotation = if (knight) -18f * sin(s * Math.PI.toFloat()) else 0f,
                mirror = knight && event.a > rx,
            )
        }
    }
}

/**
 * Las esquirlas de un bloque arrasado: seis triángulos que salen despedidos girando, caen y se
 * apagan (la rotura del tablero del vídeo). Deterministas por casilla: sin azar en el dibujo.
 */
private fun DrawScope.shards(c: GameEvent.KingSwordStrike.SwordCut, cellPx: Float, k: Float, fill: Color, light: Color) {
    val cc = center(c.cell, cellPx)
    val half = cellPx * 0.22f
    for (i in 0 until 6) {
        val ang = i * 1.047f + (c.cell.x * 0.9f + c.cell.y * 1.3f)
        val speed = cellPx * (1.1f + 0.35f * ((i * 7 + c.cell.x) % 3))
        val pos = cc + Offset(cos(ang), sin(ang)) * (speed * k) + Offset(0f, cellPx * 2.2f * k * k)
        val a = (1f - k).coerceIn(0f, 1f)
        rotate(degrees = (i * 60f) + 540f * k, pivot = pos) {
            val tri = Path().apply {
                moveTo(pos.x, pos.y - half)
                lineTo(pos.x + half * 0.9f, pos.y + half * 0.6f)
                lineTo(pos.x - half * 0.9f, pos.y + half * 0.6f)
                close()
            }
            drawPath(tri, fill, alpha = a)
            drawPath(tri, light, alpha = a * 0.8f, style = Stroke(cellPx * 0.025f))
        }
    }
    if (c.targetColor != null) drawCircle(Color(0xFFFFE27A), radius = half * 0.6f,
        center = cc + Offset(0f, -cellPx * 1.2f * k), alpha = (1f - k).coerceIn(0f, 1f))
}

/** CONSECUENCIA buena: rompiste una línea que pisaba el camino y la pieza se RETIRA. */
@Composable
fun KingPieceRetreatVFX(event: GameEvent.KingPieceRetreat, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(KING_PIECE_RETREAT_MS, easing = FastOutSlowInEasing))
        onComplete()
    }
    val (rx, ry) = event.at?.let { it.x.toFloat() to it.y.toFloat() }
        ?: restCell(event.kind, event.a, event.b, event.fromEnd)
    val at = center(rx, ry, cellPx)
    val p = progress.value
    val color = aura(event.black)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 10) {
                val ang = i * 0.628f
                val r = cellPx * (0.3f + 1.2f * p)
                drawCircle(color, radius = cellPx * 0.06f * (1f - p), center = at + Offset(cos(ang), sin(ang)) * r, alpha = 1f - p)
            }
        }
        PlacedPiece(
            event.kind, event.black, at - Offset(0f, cellPx * 1.8f * p), cellPx,
            scale = 1f - 0.4f * p, alpha = 1f - p,
            mirror = event.at == null && event.kind == KingPiece.KNIGHT && event.a > rx,
        )
    }
}

/**
 * **LA PIEZA SE QUEDA** (2026-09-28): la pieza quieta en su casilla y, saliendo de ella, lo que ATACA
 * como pieza de ajedrez — un haz por la calle de la torre o el alfil, los saltos en L del caballo —
 * para que se lea DE QUIÉN es cada casilla bloqueada (petición del usuario). Bajo sus pies, un anillo
 * en la fila y la columna que la echan. Información de juego: no se apaga con «reducir efectos»
 * (solo deja de latir).
 */
@Composable
fun KingRestLayer(
    kind: Int,
    at: Position?,
    black: Boolean,
    lane: Set<Position>,
    cellPx: Float,
    animationsOn: Boolean,
    modifier: Modifier = Modifier,
) {
    if (at == null || kind == KingPiece.NONE || cellPx <= 0f) return
    val color = aura(black)
    val light = auraLight(black)
    val here = center(at, cellPx)
    val ring = remember(cellPx) { Stroke(cellPx * 0.06f) }
    Box(modifier) {
        // Capa propia: late cada fotograma mientras la pieza descansa.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val beat = ambientBeat(animationsOn, 0.6f)
            // Su fila y su columna, apenas marcadas: «rompe una de estas y se va».
            val n = GameState.GRID_SIZE
            drawRect(light.copy(alpha = 0.05f + 0.03f * beat), Offset(0f, at.y * cellPx), Size(n * cellPx, cellPx))
            drawRect(light.copy(alpha = 0.05f + 0.03f * beat), Offset(at.x * cellPx, 0f), Size(cellPx, n * cellPx))
            if (kind == KingPiece.KNIGHT) {
                // Cada salto en L, punteado desde la pieza hasta la casilla que ataca.
                lane.forEach { p ->
                    val to = center(p, cellPx)
                    val corner = if (kotlin.math.abs(p.x - at.x) == 2) Offset(to.x, here.y) else Offset(here.x, to.y)
                    val dotA = 0.55f + 0.3f * beat
                    dotted(here, corner, 4, 1, 4) { drawCircle(color, radius = cellPx * 0.045f, center = it, alpha = dotA) }
                    dotted(corner, to, 4, 1, 4) { drawCircle(color, radius = cellPx * 0.045f, center = it, alpha = dotA) }
                }
            } else if (lane.isNotEmpty()) {
                // El haz de la calle: de la pieza a la casilla bloqueada más lejana.
                val far = lane.maxByOrNull { kotlin.math.abs(it.x - at.x) + kotlin.math.abs(it.y - at.y) }!!
                val end = center(far, cellPx)
                drawLine(color.copy(alpha = 0.35f + 0.2f * beat), here, end, cellPx * 0.16f, StrokeCap.Round)
                drawLine(light.copy(alpha = 0.7f), here, end, cellPx * 0.04f, StrokeCap.Round)
            }
            // El anillo bajo la pieza.
            drawCircle(color, radius = cellPx * (0.40f + 0.04f * beat), center = here,
                style = ring, alpha = 0.85f)
        }
        PlacedPiece(kind, black, here, cellPx)
    }
}

/**
 * CONSECUENCIA: el camino que la pieza arrasó, BLOQUEADO una colocación. Rayado en el color de la
 * pieza: se lee como «aquí no» sin tapar el tablero. Es información de juego, no adorno: no se
 * apaga con «reducir efectos».
 */
@Composable
fun KingLaneLayer(cells: Set<Position>, black: Boolean, cellPx: Float, modifier: Modifier = Modifier) {
    if (cells.isEmpty() || cellPx <= 0f) return
    val color = aura(black)
    // Capa propia: estático, que no se regrabe con cada hermano animado.
    Canvas(modifier.graphicsLayer()) {
        cells.forEach { p ->
            val tl = Offset(p.x * cellPx, p.y * cellPx)
            drawRect(Color.Black.copy(alpha = 0.35f), tl, Size(cellPx, cellPx))
            drawRect(color.copy(alpha = 0.18f), tl, Size(cellPx, cellPx))
            // Rayas diagonales dentro de la casilla.
            for (k in 1..3) {
                val f = k / 4f
                drawLine(color.copy(alpha = 0.55f), tl + Offset(cellPx * f, 0f), tl + Offset(0f, cellPx * f), cellPx * 0.04f)
                drawLine(color.copy(alpha = 0.55f), tl + Offset(cellPx, cellPx * f), tl + Offset(cellPx * f, cellPx), cellPx * 0.04f)
            }
            drawRect(color.copy(alpha = 0.8f), tl + Offset(cellPx * 0.05f, cellPx * 0.05f),
                Size(cellPx * 0.9f, cellPx * 0.9f), style = Stroke(cellPx * 0.04f))
        }
    }
}
