package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.Position
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * CONFETI DE COLOCACIÓN — estallido corto de chispas al posar una ficha en el tablero.
 * (Pedido del usuario 2026-07-20, referencia art/videoblock.mp4.)
 *
 * Comportamiento: pequeñas formas geométricas —cuadraditos y círculos— nacen en el CONTORNO de la
 * huella recién colocada (solo en los lados que dan al vacío, no en las juntas internas de la
 * pieza), salen disparadas ligeramente hacia fuera y se apagan por transparencia en ~380 ms.
 * Funciona con los tres skins: es feedback de "colocaste", no identidad de skin, así que toma el
 * color de la ficha en la paleta del skin activo.
 *
 * PERF (el patrón ya establecido en este archivo/VFX.kt): TODAS las ráfagas vivas se dibujan en UN
 * único Canvas con UN solo reloj de frames; las partículas se generan una vez por evento con una
 * semilla determinista (nada de Random por frame) y la ráfaga se purga al expirar, así que la lista
 * no crece. Cero asignaciones dentro del bucle de dibujo.
 */

private const val PLACE_POP_LIFE_MS = 380f
/**
 * P-283 · LA FIRMA DEL BLOQUE al colocar: un gesto propio de cada bloque de bioma, encima del confeti,
 * que dura algo más que él. Básico y Cerámica no la tienen. La madera NO se aplasta (decisión congelada:
 * «la madera no hace squash al aterrizar»): su firma es el polvo.
 */
private const val PLACE_SIGNATURE_MS = 480f
/** Partículas por celda de borde. Corto y limpio: un estallido, no una lluvia. */
private const val PLACE_POP_PER_EDGE = 3

private class PlacePopParticle(
    val ox: Float,          // origen (px relativos al tablero)
    val oy: Float,
    val angle: Float,       // dirección de salida
    val speed: Float,       // px/vida
    val size: Float,
    val spin: Float,
    val square: Boolean,    // cuadradito vs círculo
    val color: Color
)

private class PlacePopBurst(
    val startMs: Long,
    val particles: List<PlacePopParticle>,
    /** Centros de las casillas colocadas (px), para la firma del bloque. */
    val centers: List<Offset>,
    /** Centro de la ficha entera (px): el Vacío cierra su aro ahí. */
    val pieceCenter: Offset,
    val base: Color,
    val light: Color,
)

@Composable
fun BatchPlacePopVFX(
    events: List<GameEvent.PiecePlacedPop>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit
) {
    if (events.isEmpty()) return

    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val skin = LocalBlockSkin.current

    val clock = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val bursts = remember { mutableMapOf<String, PlacePopBurst>() }
    val scheduled = remember { mutableSetOf<String>() }

    SideEffect {
        val nowMs = clock.value.toLong()
        events.forEach { event ->
            if (event.id !in bursts) {
                val pal = skin.colorsFor(event.colorType)
                val centers = event.cells.map { Offset((it.x + 0.5f) * cellPx, (it.y + 0.5f) * cellPx) }
                val pc = Offset(centers.sumOf { it.x.toDouble() }.toFloat() / centers.size.coerceAtLeast(1),
                    centers.sumOf { it.y.toDouble() }.toFloat() / centers.size.coerceAtLeast(1))
                bursts[event.id] = PlacePopBurst(nowMs, buildParticles(event, cellPx, skin), centers, pc, pal[0], pal[1])
            }
            if (event.id !in scheduled) {
                scheduled.add(event.id)
                val data = bursts[event.id] ?: return@forEach
                val life = if (Haptics.hasBlockSignature(skin.style)) PLACE_SIGNATURE_MS else PLACE_POP_LIFE_MS
                val remaining = (data.startMs + life.toLong() - nowMs).coerceAtLeast(0L)
                val id = event.id
                scope.launch {
                    if (remaining > 0) delay(remaining)
                    // Mismo hilo (Main) que el dibujo → purga segura entre frames.
                    bursts.remove(id)
                    scheduled.remove(id)
                    onEventComplete(id)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        clock.animateTo(100_000f, tween(100_000, easing = LinearEasing))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val nowMs = clock.value.toLong()
        bursts.values.forEach { burst ->
            val ts = ((nowMs - burst.startMs) / PLACE_SIGNATURE_MS).coerceIn(0f, 1f)
            if (ts < 1f) drawPlaceSignature(skin.style, burst, cellPx, ts)
            val t = ((nowMs - burst.startMs) / PLACE_POP_LIFE_MS).coerceIn(0f, 1f)
            if (t >= 1f) return@forEach
            // Salida rápida que frena (ease-out) + desvanecido por transparencia, como en el vídeo.
            val ease = 1f - (1f - t) * (1f - t)
            val alpha = (1f - t) * (1f - t)
            burst.particles.forEach { p ->
                val d = p.speed * ease
                val px = p.ox + cos(p.angle) * d
                val py = p.oy + sin(p.angle) * d + cellPx * 0.30f * t * t   // pizca de gravedad
                val s = p.size * (1f - 0.35f * t)
                val col = p.color.copy(alpha = p.color.alpha * alpha)
                if (p.square) {
                    rotate(p.spin * ease, Offset(px, py)) {
                        drawRoundRect(
                            color = col,
                            topLeft = Offset(px - s, py - s),
                            size = Size(s * 2f, s * 2f),
                            cornerRadius = CornerRadius(s * 0.35f, s * 0.35f)
                        )
                    }
                } else {
                    drawCircle(col, radius = s, center = Offset(px, py))
                }
            }
        }
    }
}

/** La firma del bloque [style] sobre la ficha recién colocada, en el instante [t] (0..1). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPlaceSignature(
    style: BlockTileStyle, b: PlacePopBurst, cellPx: Float, t: Float,
) {
    val a = 1f - t
    val ease = 1f - (1f - t) * (1f - t)
    when (style) {
        // Cristal: un destello en diagonal cruza cada gema, una sola vez.
        BlockTileStyle.FACETED -> b.centers.forEach { c ->
            val u = t * 2.2f - 0.6f
            if (u in -0.3f..1.3f) {
                val x = c.x - cellPx * 0.5f + cellPx * u
                drawLine(Color.White, Offset(x - cellPx * 0.25f, c.y + cellPx * 0.45f),
                    Offset(x + cellPx * 0.25f, c.y - cellPx * 0.45f), strokeWidth = cellPx * 0.12f, alpha = 0.55f * a)
            }
        }
        // Madera: una nubecilla de polvo salta de la base de cada casilla y se posa.
        BlockTileStyle.WOOD -> b.centers.forEachIndexed { i, c ->
            for (k in -1..1 step 2) {
                val dx = k * cellPx * (0.18f + 0.30f * ease) + (i % 2) * cellPx * 0.05f
                val dy = cellPx * 0.55f - cellPx * 0.22f * sin(t * PI.toFloat())
                drawCircle(Color(0xFFE8D5B5), radius = cellPx * (0.07f + 0.10f * ease), center = Offset(c.x + dx, c.y + dy), alpha = 0.55f * a)
            }
        }
        // Lava: resplandor que se apaga y humo gris que sube de cada casilla.
        BlockTileStyle.LAVA -> b.centers.forEachIndexed { i, c ->
            drawCircle(b.light, radius = cellPx * 0.55f, center = c, alpha = 0.35f * a * a)
            val off = (i % 3 - 1) * cellPx * 0.18f
            drawCircle(Color(0xFF8C8C96), radius = cellPx * (0.08f + 0.14f * ease),
                center = Offset(c.x + off, c.y - cellPx * (0.1f + 0.7f * ease)), alpha = 0.45f * a)
        }
        // Hielo: un aro de escarcha se abre desde cada casilla y la cubre un instante de blanco.
        BlockTileStyle.ICE -> b.centers.forEach { c ->
            drawCircle(Color.White, radius = cellPx * (0.2f + 0.45f * ease), center = c, alpha = 0.7f * a,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = cellPx * 0.06f * a))
            drawRect(Color.White, topLeft = Offset(c.x - cellPx / 2f, c.y - cellPx / 2f), size = Size(cellPx, cellPx), alpha = 0.25f * a * a)
        }
        // Espectro: se MATERIALIZA — un velo luminoso que se disuelve y una voluta que sube.
        BlockTileStyle.SPECTRE -> b.centers.forEach { c ->
            drawRect(b.light, topLeft = Offset(c.x - cellPx / 2f, c.y - cellPx / 2f), size = Size(cellPx, cellPx), alpha = 0.6f * a * a)
            drawCircle(Color.White, radius = cellPx * (0.08f + 0.08f * ease), center = Offset(c.x, c.y - cellPx * 0.8f * ease), alpha = 0.5f * a)
        }
        // Constelación: una estrellita de cuatro puntas titila en una esquina de cada casilla.
        BlockTileStyle.CONSTELLATION -> b.centers.forEachIndexed { i, c ->
            val corner = Offset(c.x + (if (i % 2 == 0) 0.38f else -0.38f) * cellPx, c.y - 0.38f * cellPx)
            val r = cellPx * 0.18f * sin(t * PI.toFloat())
            drawLine(Color.White, Offset(corner.x - r, corner.y), Offset(corner.x + r, corner.y), strokeWidth = cellPx * 0.05f, alpha = a)
            drawLine(Color.White, Offset(corner.x, corner.y - r), Offset(corner.x, corner.y + r), strokeWidth = cellPx * 0.05f, alpha = a)
            drawCircle(b.light, radius = r * 0.35f, center = corner, alpha = a)
        }
        // Vacío: un aro del color de la ficha se CIERRA hacia el centro de la pieza, como tragado.
        BlockTileStyle.VOID -> {
            val r = cellPx * 1.6f * (1f - ease)
            drawCircle(b.base, radius = r.coerceAtLeast(0.5f), center = b.pieceCenter, alpha = 0.8f * a,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = cellPx * 0.10f))
            drawCircle(Color.Black, radius = cellPx * 0.3f * sin(t * PI.toFloat()), center = b.pieceCenter, alpha = 0.35f * a)
        }
        // Metal: los cuatro remaches destellan a la vez al asentarse la chapa.
        BlockTileStyle.METAL -> b.centers.forEach { c ->
            val r = cellPx * 0.07f * sin(t * PI.toFloat())
            for (dx in -1..1 step 2) for (dy in -1..1 step 2) {
                drawCircle(Color.White, radius = r, center = Offset(c.x + dx * cellPx * 0.34f, c.y + dy * cellPx * 0.34f), alpha = 0.9f * a)
            }
        }
        // Neón (P-412): el tubo se ENCIENDE como un letrero — titila (encendido, apagado, encendido) y
        // se queda un instante sobrebrillante. Con líneas y no con `Stroke`: cero asignaciones.
        BlockTileStyle.NEON -> {
            val on = when {
                t < 0.10f -> 1f
                t < 0.18f -> 0.1f
                t < 0.28f -> 1f
                t < 0.33f -> 0.25f
                else -> 1f - (t - 0.33f) / 0.67f
            }
            b.centers.forEach { c ->
                // Los tramos rectos del tubo, sin las esquinas: se cortan antes de la curva y con punta
                // redonda, así el tubo redondeado de debajo pone la curva y el destello no sale cuadrado.
                val h = cellPx * 0.36f
                val cut = cellPx * 0.14f
                val l = c.x - h; val r = c.x + h; val tp = c.y - h; val bt = c.y + h
                val cap = androidx.compose.ui.graphics.StrokeCap.Round
                for (k in 0..1) {
                    val col = if (k == 0) b.base else Color.White
                    val w = cellPx * (if (k == 0) 0.16f else 0.05f)
                    val al = (if (k == 0) 0.45f else 0.95f) * on
                    drawLine(col, Offset(l + cut, tp), Offset(r - cut, tp), strokeWidth = w, cap = cap, alpha = al)
                    drawLine(col, Offset(l + cut, bt), Offset(r - cut, bt), strokeWidth = w, cap = cap, alpha = al)
                    drawLine(col, Offset(l, tp + cut), Offset(l, bt - cut), strokeWidth = w, cap = cap, alpha = al)
                    drawLine(col, Offset(r, tp + cut), Offset(r, bt - cut), strokeWidth = w, cap = cap, alpha = al)
                }
            }
        }
        // Dorado (P-412): la gema DESTELLA al engastarse y una estrellita titila en su esquina.
        BlockTileStyle.GOLD -> b.centers.forEach { c ->
            val pulse = sin(t * PI.toFloat())
            val g = cellPx * 0.31f                       // media gema (marco al 19 %)
            drawRect(Color.White, topLeft = Offset(c.x - g, c.y - g), size = Size(g * 2f, g * 2f), alpha = 0.45f * pulse * a)
            val sx = c.x - g * 0.55f; val sy = c.y - g * 0.55f
            val sr = cellPx * 0.22f * pulse
            drawLine(Color.White, Offset(sx - sr, sy), Offset(sx + sr, sy), strokeWidth = cellPx * 0.05f, alpha = a)
            drawLine(Color.White, Offset(sx, sy - sr), Offset(sx, sy + sr), strokeWidth = cellPx * 0.05f, alpha = a)
            drawCircle(Color(0xFFFFF1B0), radius = sr * 0.3f, center = Offset(sx, sy), alpha = a)
        }
        else -> Unit
    }
}

/**
 * Genera la ráfaga de UN evento. Determinista a partir del id (misma ficha → misma ráfaga si el
 * composable se recompone) y sin `Random` en el bucle de dibujo.
 *
 * Solo se emiten partículas en las aristas EXPUESTAS de la huella (las que no lindan con otra celda
 * de la misma pieza): así el confeti perfila la ficha en vez de aparecer dentro de ella.
 */
private fun buildParticles(
    event: GameEvent.PiecePlacedPop,
    cellPx: Float,
    skin: BlockSkin
): List<PlacePopParticle> {
    val palette = skin.colorsFor(event.colorType)
    val colors = listOf(palette[1], palette[0], Color.White.copy(alpha = 0.92f), palette[1])
    val occupied = event.cells.toHashSet()
    // (dx, dy) de las 4 aristas + ángulo de salida hacia fuera.
    val dirs = arrayOf(
        intArrayOf(0, -1), intArrayOf(1, 0), intArrayOf(0, 1), intArrayOf(-1, 0)
    )
    val out = ArrayList<PlacePopParticle>(event.cells.size * 4)
    var seed = event.id.hashCode()
    fun rnd(): Float {                        // LCG barato y determinista
        seed = seed * 1664525 + 1013904223
        return ((seed ushr 8) and 0xFFFF) / 65535f
    }
    event.cells.forEach { cellPos ->
        dirs.forEach { d ->
            if (Position(cellPos.x + d[0], cellPos.y + d[1]) in occupied) return@forEach
            val baseAngle = kotlin.math.atan2(d[1].toFloat(), d[0].toFloat())
            repeat(PLACE_POP_PER_EDGE) { i ->
                // Punto de nacimiento repartido a lo largo de la arista.
                val along = (i + 0.5f + (rnd() - 0.5f) * 0.6f) / PLACE_POP_PER_EDGE
                val ex: Float
                val ey: Float
                if (d[0] != 0) {              // arista vertical (izquierda/derecha)
                    ex = (cellPos.x + if (d[0] > 0) 1f else 0f) * cellPx
                    ey = (cellPos.y + along) * cellPx
                } else {                      // arista horizontal (arriba/abajo)
                    ex = (cellPos.x + along) * cellPx
                    ey = (cellPos.y + if (d[1] > 0) 1f else 0f) * cellPx
                }
                out.add(
                    PlacePopParticle(
                        ox = ex,
                        oy = ey,
                        // Abanico de ±35° alrededor de la normal de la arista → "hacia afuera".
                        angle = baseAngle + (rnd() - 0.5f) * (70f * PI.toFloat() / 180f),
                        speed = cellPx * (0.30f + rnd() * 0.34f),
                        size = cellPx * (0.045f + rnd() * 0.035f),
                        spin = (if (rnd() > 0.5f) 1f else -1f) * (90f + rnd() * 150f),
                        square = rnd() > 0.4f,
                        color = colors[(rnd() * colors.size).toInt().coerceAtMost(colors.size - 1)]
                    )
                )
            }
        }
    }
    return out
}
