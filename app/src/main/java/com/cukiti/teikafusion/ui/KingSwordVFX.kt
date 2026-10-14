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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.KingSword
import kotlin.math.sin

/**
 * **P-294 · LA ESPADA DEL REY** (2026-09-25) — lo que se ve del castigo del Trono Real. Reglas en
 * [KingSword]; tres fases, como todo evento de bioma (tope 1200 ms):
 *
 *  1. **Telegrafía** — [KingSwordLayer]: la espada (Lottie `rey_espada`) flota junto a la línea
 *     señalada, un poco fuera del tablero. En el aviso 2 crece, la línea se enciende en oro y sus
 *     bloques tiemblan (`GameBoard.threatShakeCells`). Alzarla tiene su propio VFX ([KingSwordRaiseVFX]).
 *  2. **Impacto** — [KingSwordStrikeVFX]: la hoja barre la línea, un tajo blanco y los bloques se
 *     parten en dos mitades que saltan.
 *  3. **Consecuencia** — la línea vacía y los Teikas lanzados hacia arriba (lo hace el motor); si la
 *     envainaste, [KingSwordSheatheVFX].
 */

private val SwordGold = Color(0xFFFFC93C)
private val SwordGoldLight = Color(0xFFFFF1B8)

/** Lado de la espada flotante, en casillas. */
private const val SWORD_BOX_CELLS = 2.4f

/**
 * Dónde flota la espada para la línea [axis]/[line]: sobre el ARRANQUE de la línea (a la izquierda en
 * una fila, arriba en una columna), dentro del tablero —fuera, la empuñadura se salía de la pantalla—.
 * Devuelve el centro en píxeles y el giro: la hoja apunta a lo largo de la línea.
 */
private fun swordAnchor(axis: Int, line: Int, cellPx: Float): Pair<Offset, Float> =
    // P-352: la hoja ancha flota entre la línea y su pareja.
    if (axis == KingSword.ROW) Offset(cellPx * 0.9f, KingSword.midOf(line) * cellPx) to 90f
    else Offset(KingSword.midOf(line) * cellPx, cellPx * 0.9f) to 180f

@Composable
private fun SwordLottie(modifier: Modifier) {
    // Sin corte al aparecer: composición precargada y bucle en fase entre el VFX y la capa (BiomeLottie).
    BiomeLottie(R.raw.rey_espada, modifier)
}

/** Colocación de la espada Lottie: centrada en [center], girada [rotation] y escalada [scale]. */
@Composable
private fun PlacedSword(center: Offset, rotation: Float, scale: Float, alpha: Float, cellPx: Float) {
    val density = LocalDensity.current
    val boxPx = cellPx * SWORD_BOX_CELLS
    val boxDp: Dp = with(density) { boxPx.toDp() }
    Box(
        Modifier
            .size(boxDp)
            .graphicsLayer {
                translationX = center.x - boxPx / 2f
                translationY = center.y - boxPx / 2f
                rotationZ = rotation
                scaleX = scale; scaleY = scale
                this.alpha = alpha
            }
    ) { SwordLottie(Modifier.fillMaxSize()) }
}

/** TELEGRAFÍA: la espada alzada junto a su línea; en el aviso 2, la línea arde en oro. */
@Composable
fun KingSwordLayer(
    axis: Int,
    line: Int,
    idleTurns: Int,
    threshold: Int,
    cellPx: Float,
    animationsOn: Boolean,
    hideSword: Boolean,
    modifier: Modifier = Modifier,
) {
    if (axis == KingSword.NONE || cellPx <= 0f) return
    val glowing = idleTurns >= threshold - 1
    val edge = remember(cellPx, glowing) {
        androidx.compose.ui.graphics.drawscope.Stroke(cellPx * (if (glowing) 0.07f else 0.04f))
    }
    Box(modifier) {
        // Capa propia (auditoría de fluidez 2026-09-29): late cada fotograma durante el aviso.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val beat = ambientBeat(animationsOn, 0.8f)
            // P-352: las DOS líneas que partirá la hoja ancha.
            val first = KingSword.midOf(line) - 1f
            // Sin `Pair` por fotograma: esquina y tamaño por separado.
            val tl = if (axis == KingSword.ROW) Offset(0f, first * cellPx) else Offset(first * cellPx, 0f)
            val sz = if (axis == KingSword.ROW) Size(size.width, cellPx * 2f) else Size(cellPx * 2f, size.height)
            val a = if (glowing) 0.30f + 0.25f * beat else 0.10f + 0.06f * beat
            drawRect(SwordGold.copy(alpha = a), tl, sz)
            drawRect(
                SwordGold.copy(alpha = if (glowing) 0.95f else 0.45f), tl, sz,
                style = edge
            )
            if (glowing) {
                // El filo que recorre la línea: «aquí caerá».
                val k = (biomeClockSec.floatValue * 0.9f) % 1f
                val c = if (axis == KingSword.ROW) Offset(size.width * k, KingSword.midOf(line) * cellPx)
                else Offset(KingSword.midOf(line) * cellPx, size.height * k)
                drawCircle(SwordGoldLight, radius = cellPx * 0.8f, center = c, alpha = 0.55f)
            }
        }
        if (!hideSword) {
            val (center, rot) = swordAnchor(axis, line, cellPx)
            PlacedSword(center, rot, scale = if (glowing) 1.3f else 1.05f, alpha = if (glowing) 1f else 0.85f, cellPx = cellPx)
        }
    }
}

/** El Rey ALZA la espada (aviso 1): sube desde el borde con un destello. */
@Composable
fun KingSwordRaiseVFX(event: GameEvent.KingSwordRaise, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(KING_SWORD_RAISE_MS, easing = FastOutSlowInEasing))
        onComplete()
    }
    val (center, rot) = swordAnchor(event.axis, event.line, cellPx)
    val p = progress.value
    val out = if (event.axis == KingSword.ROW) Offset(-cellPx * 1.6f, 0f) else Offset(0f, -cellPx * 1.6f)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(SwordGoldLight, radius = cellPx * (0.6f + 1.4f * p), center = center, alpha = 0.55f * (1f - p))
        }
        PlacedSword(center + out * (1f - p), rot, scale = 0.6f + 0.3f * p, alpha = p, cellPx = cellPx)
    }
}

/** Lo que dura el alzado de la espada (aviso). */
const val KING_SWORD_RAISE_MS = 620

/**
 * IMPACTO: la espada CAE. Hasta el 40 % la hoja barre la línea con su tajo; desde ahí cada bloque se
 * parte en dos mitades que saltan y caen, con chispas.
 */
@Composable
fun KingSwordStrikeVFX(event: GameEvent.KingSwordStrike, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }
    val skin = LocalBlockSkin.current
    val (anchor, rot) = swordAnchor(event.axis, event.line, cellPx)
    val p = progress.value
    val row = event.axis == KingSword.ROW
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val sweep = (p / 0.4f).coerceIn(0f, 1f)
            val lineMid = KingSword.midOf(event.line) * cellPx
            // El tajo: una estela blanca que avanza por la línea.
            val len = (if (row) size.width else size.height) * sweep
            val from = if (row) Offset(0f, lineMid) else Offset(lineMid, 0f)
            val to = if (row) Offset(len, lineMid) else Offset(lineMid, len)
            val fade = (1f - ((p - 0.4f) / 0.3f).coerceIn(0f, 1f))
            // P-352: el tajo cubre las dos líneas.
            drawLine(SwordGold.copy(alpha = 0.55f * fade), from, to, cellPx * 1.6f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.95f * fade), from, to, cellPx * 0.22f, StrokeCap.Round)
            // Los bloques partidos: dos mitades que se separan, saltan y caen.
            val split = ((p - 0.4f) / 0.6f).coerceIn(0f, 1f)
            event.cuts.forEach { c ->
                val cx = (c.cell.x + 0.5f) * cellPx
                val cy = (c.cell.y + 0.5f) * cellPx
                val colors = skin.colorsFor(c.colorType)
                val half = cellPx * 0.42f
                val gap = split * cellPx * 0.45f
                val fall = split * split * cellPx * 2.4f - sin(split * 3.14f) * cellPx * 0.6f
                val alpha = 1f - split * 0.85f
                for (side in listOf(-1f, 1f)) {
                    // Fila: la hoja corta en horizontal → mitades arriba/abajo. Columna: izquierda/derecha.
                    val off = if (row) Offset(0f, side * gap) else Offset(side * gap, 0f)
                    val center = Offset(cx, cy + fall) + off
                    val w = if (row) half * 2 else half
                    val h = if (row) half else half * 2
                    rotate(degrees = side * 25f * split, pivot = center) {
                        drawRoundRect(
                            colors[0], Offset(center.x - w / 2f, center.y - h / 2f), Size(w, h),
                            CornerRadius(half * 0.3f, half * 0.3f), alpha = alpha
                        )
                    }
                }
                if (c.targetColor != null) drawCircle(Color(0xFFFFE27A), radius = half * 0.35f, center = Offset(cx, cy + fall), alpha = alpha)
                // Chispas del corte.
                for (i in 0 until 4) {
                    val a = i * 1.57f + c.cell.x
                    val r = cellPx * (0.2f + 0.8f * split)
                    drawCircle(SwordGoldLight, radius = cellPx * 0.05f * (1f - split),
                        center = Offset(cx + sin(a + 1.57f) * r, cy + sin(a) * r), alpha = 1f - split)
                }
            }
        }
        // La espada viaja por la línea con el tajo y se desvanece al final.
        val sweep = (p / 0.4f).coerceIn(0f, 1f)
        val along = if (row) Offset(sweep * cellPx * 8f, 0f) else Offset(0f, sweep * cellPx * 8f)
        PlacedSword(anchor + along, rot, scale = 1.15f, alpha = (1f - ((p - 0.5f) / 0.3f).coerceIn(0f, 1f)), cellPx = cellPx)
    }
}

/** CONSECUENCIA buena: rompiste una línea que cruzaba la señalada y el Rey ENVAINA. */
@Composable
fun KingSwordSheatheVFX(event: GameEvent.KingSwordSheathe, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(KING_SWORD_SHEATHE_MS, easing = FastOutSlowInEasing))
        onComplete()
    }
    val (center, rot) = swordAnchor(event.axis, event.line, cellPx)
    val p = progress.value
    val away = if (event.axis == KingSword.ROW) Offset(-cellPx * 2f, 0f) else Offset(0f, -cellPx * 2f)
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            for (i in 0 until 10) {
                val a = i * 0.628f
                val r = cellPx * (0.3f + 1.2f * p)
                drawCircle(SwordGold, radius = cellPx * 0.06f * (1f - p),
                    center = center + Offset(sin(a + 1.57f) * r, sin(a) * r), alpha = 1f - p)
            }
            drawCircle(
                Brush.radialGradient(listOf(SwordGoldLight.copy(alpha = 0.6f * (1f - p)), Color.Transparent), center, cellPx * 1.6f),
                radius = cellPx * 1.6f, center = center
            )
        }
        PlacedSword(center + away * p, rot, scale = 1f - 0.4f * p, alpha = 1f - p, cellPx = cellPx)
    }
}

/** Lo que dura la envainada (consecuencia buena). */
const val KING_SWORD_SHEATHE_MS = 640
