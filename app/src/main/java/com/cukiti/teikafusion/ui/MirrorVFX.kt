package com.cukiti.teikafusion.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import com.cukiti.teikafusion.model.CellContent
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.HauntedMirror
import com.cukiti.teikafusion.model.Position
import kotlin.math.sin

/**
 * **P-246 · EL ESPEJO EMBRUJADO** — la Mansión Espectral desde el 2026-09-23. Las tres fases:
 *
 *  1. TELEGRAFÍA — [MirrorOverlay]. Primer aviso: asoma el PLANO del espejo en el eje del tablero,
 *     empañado, y la mitad que va a reflejar se tiñe de violeta. Segundo aviso: además se ve el
 *     REFLEJO — siluetas espectrales en las casillas exactas donde caerán los fantasmas.
 *  2. IMPACTO — [MirrorFlashVFX]: el espejo destella y cada fantasma sale de su bloque original y vuela
 *     en espejo hasta su casilla.
 *  3. CONSECUENCIA — [PhantomCellsLayer] (los bloques fantasma, translúcidos y titilantes mientras
 *     duran) y [PhantomFadeVFX] (se deshacen en bruma al cumplir sus turnos).
 *
 * El reflejo del segundo aviso se calcula con la MISMA función que usa el motor
 * ([HauntedMirror.phantomsFor]); lo único que la UI no ve son los Teikas, que pueden tapar alguna
 * casilla más en el momento del destello.
 */
@Composable
fun MirrorOverlay(
    grid: Map<Position, CellContent>,
    source: Int,
    idleTurns: Int,
    threshold: Int,
    unplaceable: Set<Position>,
    cellPx: Float,
    modifier: Modifier = Modifier,
) {
    val on by animateFloatAsState(
        targetValue = if (source == HauntedMirror.NONE) 0f else 1f,
        animationSpec = tween(460, easing = FastOutSlowInEasing), label = "mirrorOn"
    )
    val showReflection = source != HauntedMirror.NONE && idleTurns >= threshold - 1
    val refl by animateFloatAsState(
        targetValue = if (showReflection) 1f else 0f,
        animationSpec = tween(520, easing = FastOutSlowInEasing), label = "mirrorRefl"
    )
    if (on <= 0.01f && source == HauntedMirror.NONE) return
    val reflection = remember(grid, source, unplaceable) {
        HauntedMirror.phantomsFor(grid, source, { t -> grid[t] is CellContent.Empty && t !in unplaceable })
    }
    val vertical = source == HauntedMirror.LEFT || source == HauntedMirror.RIGHT
    Box(
        // Capa propia (auditoría de fluidez 2026-09-29): respira cada fotograma todo el nivel.
        modifier.fillMaxSize().graphicsLayer().drawWithCache {
            val w = size.width; val h = size.height
            val glass = if (vertical) Brush.horizontalGradient(
                listOf(Color.Transparent, Color(0xFFCFE8FF).copy(alpha = 0.55f), Color(0xFFFFFFFF).copy(alpha = 0.8f),
                    Color(0xFFCFE8FF).copy(alpha = 0.55f), Color.Transparent),
                startX = w / 2f - cellPx * 0.35f, endX = w / 2f + cellPx * 0.35f
            ) else Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFFCFE8FF).copy(alpha = 0.55f), Color(0xFFFFFFFF).copy(alpha = 0.8f),
                    Color(0xFFCFE8FF).copy(alpha = 0.55f), Color.Transparent),
                startY = h / 2f - cellPx * 0.35f, endY = h / 2f + cellPx * 0.35f
            )
            val corner = CornerRadius(cellPx * 0.16f, cellPx * 0.16f)
            // La mitad que se refleja, calculada una vez (no por fotograma).
            val halfTopLeft = when (source) {
                HauntedMirror.RIGHT -> Offset(w / 2f, 0f)
                HauntedMirror.BOTTOM -> Offset(0f, h / 2f)
                else -> Offset.Zero
            }
            val halfSize = if (vertical) Size(w / 2f, h) else Size(w, h / 2f)
            val reflStroke = Stroke(cellPx * 0.04f)
            onDrawBehind {
                val t = biomeClockSec.floatValue
                val breathe = 0.75f + 0.25f * sin(t * 2.2f)
                // La mitad que se refleja: un velo violeta frío.
                drawRect(Color(0xFF6B4BB8), topLeft = halfTopLeft, size = halfSize, alpha = 0.16f * on * breathe)
                // El plano del espejo: una lámina de cristal empañado en el eje.
                if (vertical) drawRect(glass, topLeft = Offset(w / 2f - cellPx * 0.35f, 0f), size = Size(cellPx * 0.7f, h), alpha = on * breathe)
                else drawRect(glass, topLeft = Offset(0f, h / 2f - cellPx * 0.35f), size = Size(w, cellPx * 0.7f), alpha = on * breathe)
                drawMirrorFinials(vertical, w, h, cellPx, on)
                // Segundo aviso: el reflejo, casilla a casilla, donde caerán los fantasmas.
                if (refl > 0.01f) reflection.keys.forEach { p ->
                    val flick = 0.6f + 0.4f * sin(t * 5f + p.x * 1.7f + p.y * 1.1f)
                    val o = Offset(p.x * cellPx + cellPx * 0.1f, p.y * cellPx + cellPx * 0.1f)
                    val s = Size(cellPx * 0.8f, cellPx * 0.8f)
                    drawRoundRect(Color(0xFF9BE7FF), o, s, corner, alpha = 0.18f * refl * flick)
                    drawRoundRect(Color(0xFFD7F4FF), o, s, corner, style = reflStroke, alpha = 0.55f * refl * flick)
                }
            }
        }
    )
}

/** Remates dorados del marco en los dos extremos del eje: el espejo «cuelga» del tablero. */
private fun DrawScope.drawMirrorFinials(vertical: Boolean, w: Float, h: Float, cellPx: Float, alpha: Float) {
    val gold = Color(0xFFE8C26A)
    val r = cellPx * 0.22f
    // Sin lista por fotograma: los dos extremos, uno tras otro.
    for (e in 0..1) {
        val c = if (vertical) Offset(w / 2f, if (e == 0) r else h - r) else Offset(if (e == 0) r else w - r, h / 2f)
        drawCircle(gold, radius = r, center = c, alpha = alpha)
        drawCircle(Color(0xFF7A5A1E), radius = r * 0.55f, center = c, alpha = alpha)
    }
}

/**
 * CONSECUENCIA: los bloques fantasma sobre la madera que dibuja [CellView]. Un velo espectral cian que
 * titila; con el último turno por delante parpadea más, para que se vea que se van.
 */
@Composable
fun PhantomCellsLayer(phantoms: Map<Position, Int>, cellPx: Float, modifier: Modifier = Modifier) {
    if (phantoms.isEmpty()) return
    Box(
        // Capa propia: titila cada fotograma mientras haya fantasmas.
        modifier.fillMaxSize().graphicsLayer().drawWithCache {
            val corner = CornerRadius(cellPx * 0.14f, cellPx * 0.14f)
            val veil = Brush.verticalGradient(
                listOf(Color(0xFFBDF1FF).copy(alpha = 0.75f), Color(0xFF6FA8FF).copy(alpha = 0.55f)),
                startY = 0f, endY = cellPx
            )
            val inset = Offset(cellPx * 0.04f, cellPx * 0.04f)
            val cellSize = Size(cellPx * 0.92f, cellPx * 0.92f)
            val rim = Stroke(cellPx * 0.05f)
            onDrawBehind {
                val t = biomeClockSec.floatValue
                phantoms.forEach { (p, turnsLeft) ->
                    val last = turnsLeft <= 1
                    val flick = if (last) 0.45f + 0.55f * sin(t * 9f + p.x) else 0.75f + 0.25f * sin(t * 3f + p.y)
                    // El degradado vive en coordenadas de UNA celda: se dibuja trasladado a cada una.
                    translate(p.x * cellPx, p.y * cellPx) {
                        drawRoundRect(veil, inset, cellSize, corner, alpha = 0.62f * flick)
                        drawRoundRect(Color(0xFFE6FBFF), inset, cellSize, corner, style = rim, alpha = 0.8f * flick)
                    }
                }
            }
        }
    )
}

/** Duración del destello (impacto), dentro del tope de 1200 ms. */
const val MIRROR_FLASH_MS = 900
/** Duración del desvanecerse de los fantasmas. */
const val PHANTOM_FADE_MS = 640

/** IMPACTO: el espejo destella y cada fantasma vuela de su bloque original a su casilla. */
@Composable
fun MirrorFlashVFX(event: GameEvent.MirrorFlash, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(MIRROR_FLASH_MS, easing = LinearEasing))
        onComplete()
    }
    val vertical = event.source == HauntedMirror.LEFT || event.source == HauntedMirror.RIGHT
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val flash = (1f - p / 0.3f).coerceIn(0f, 1f)
        // Fogonazo a lo largo del eje.
        if (vertical) drawRect(Color.White, topLeft = Offset(size.width / 2f - cellPx * 0.6f, 0f), size = Size(cellPx * 1.2f, size.height), alpha = 0.8f * flash)
        else drawRect(Color.White, topLeft = Offset(0f, size.height / 2f - cellPx * 0.6f), size = Size(size.width, cellPx * 1.2f), alpha = 0.8f * flash)
        // Cada fantasma sale de su original (el reflejo de su destino) y vuela hasta él.
        val fly = ((p - 0.15f) / 0.75f).coerceIn(0f, 1f)
        val e = fly * fly * (3f - 2f * fly)
        event.phantoms.keys.forEach { to ->
            val from = HauntedMirror.reflect(to, event.source)
            val x = (from.x + (to.x - from.x) * e) * cellPx
            val y = (from.y + (to.y - from.y) * e) * cellPx
            val a = if (fly >= 1f) 0f else 0.85f
            drawRoundRect(Color(0xFFCFF3FF), Offset(x + cellPx * 0.12f, y + cellPx * 0.12f), Size(cellPx * 0.76f, cellPx * 0.76f),
                CornerRadius(cellPx * 0.16f, cellPx * 0.16f), alpha = a * (0.4f + 0.6f * (1f - e)))
            drawCircle(Color.White, radius = cellPx * 0.18f * (1f - e), center = Offset(x + cellPx / 2f, y + cellPx / 2f), alpha = a)
        }
    }
}

/** CONSECUENCIA final: los fantasmas que cumplieron sus turnos se deshacen en bruma que sube. */
@Composable
fun PhantomFadeVFX(event: GameEvent.PhantomFade, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(PHANTOM_FADE_MS, easing = LinearEasing))
        onComplete()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        event.cells.forEach { c ->
            val cx = (c.x + 0.5f) * cellPx; val cy = (c.y + 0.5f) * cellPx
            drawRoundRect(Color(0xFFBDF1FF), Offset(c.x * cellPx + cellPx * 0.1f, c.y * cellPx + cellPx * 0.1f - cellPx * 0.3f * p),
                Size(cellPx * 0.8f, cellPx * 0.8f), CornerRadius(cellPx * 0.16f, cellPx * 0.16f), alpha = 0.55f * (1f - p))
            for (k in 0 until 3) {
                val dx = (k - 1) * cellPx * 0.28f
                drawCircle(Color(0xFFE6FBFF), radius = cellPx * (0.08f + 0.1f * p),
                    center = Offset(cx + dx, cy - cellPx * (0.2f + 0.9f * p) - k * cellPx * 0.1f), alpha = 0.5f * (1f - p))
            }
        }
    }
}
