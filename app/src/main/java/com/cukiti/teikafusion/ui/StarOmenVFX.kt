package com.cukiti.teikafusion.ui

import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import com.cukiti.teikafusion.model.StarAstroOmen
import kotlin.math.cos
import kotlin.math.sin

/**
 * **El presagio del Cielo Estelar** (2026-09-23): el tablero anuncia QUÉ astro viene mientras dura el
 * aviso. Antes los turnos ociosos no decían nada y el astro se descubría al asomar, con el castigo ya
 * encima.
 *
 * - **Sol**: una luz dorada cae desde el cielo del tablero, con haces que giran despacio y un filo
 *   cálido en el borde. Es la amenaza a tus Teikas (o, sin Teikas, a un bloque).
 * - **Luna**: se hace de noche — velo azul, estrellas que titilan y un halo plateado que baja por el
 *   borde. Es la amenaza a tus estrellas.
 *
 * [strength] sube con el aviso (≈0,55 en el primer turno ocioso, 1 en el segundo): el cielo se va
 * cerrando hasta que el astro asoma y toma el relevo (`AstroEventOverlay`).
 *
 * Rendimiento: todo el tiempo se lee de [biomeClockSec] en fase de DIBUJO y los pinceles se cachean
 * por tamaño (`drawWithCache`) — cero asignaciones por frame, como pide el resto de la capa de bioma.
 */
@Composable
fun StarOmenOverlay(omen: Int, strength: Float, modifier: Modifier = Modifier) {
    val k by animateFloatAsState(
        targetValue = if (omen == StarAstroOmen.NONE) 0f else strength.coerceIn(0f, 1f),
        animationSpec = tween(520, easing = FastOutSlowInEasing),
        label = "starOmen"
    )
    if (k <= 0.01f && omen == StarAstroOmen.NONE) return
    val isSun = omen == StarAstroOmen.SUN
    val starCount = fxCount(14)
    Box(
        // Recortado al tablero: los haces nacen fuera (el sol está «arriba») y no deben salirse.
        // Capa propia (auditoría de fluidez 2026-09-29): respira cada fotograma durante el presagio.
        modifier.clipToBounds().graphicsLayer().drawWithCache {
            val w = size.width
            val h = size.height
            val sunWash = Brush.verticalGradient(
                listOf(Color(0xFFFFC94A).copy(alpha = 0.58f), Color(0xFFFF9A2E).copy(alpha = 0.24f), Color(0xFFFF8A24).copy(alpha = 0.06f)),
                startY = 0f, endY = h
            )
            val moonVeil = Brush.verticalGradient(
                listOf(Color(0xFF0B1640).copy(alpha = 0.62f), Color(0xFF1A2A66).copy(alpha = 0.34f), Color(0xFF0B1640).copy(alpha = 0.18f)),
                startY = 0f, endY = h
            )
            val moonHalo = Brush.radialGradient(
                listOf(Color(0xFFDCEBFF).copy(alpha = 0.45f), Color(0xFF9FC3FF).copy(alpha = 0.12f), Color.Transparent),
                center = Offset(w / 2f, -h * 0.06f), radius = w * 0.62f
            )
            val rayBrush = Brush.verticalGradient(
                listOf(Color(0xFFFFE08A).copy(alpha = 0.75f), Color(0xFFFFB84D).copy(alpha = 0.0f)),
                startY = 0f, endY = h * 0.9f
            )
            val corner = CornerRadius(w * 0.03f, w * 0.03f)
            onDrawBehind {
                val t = biomeClockSec.floatValue
                val breathe = 0.82f + 0.18f * sin(t * 2.1f)
                if (isSun) {
                    drawRect(sunWash, alpha = k * breathe)
                    // Haces: cuñas finas que giran alrededor del sol (fuera, arriba al centro).
                    val pivot = Offset(w / 2f, -h * 0.08f)
                    rotate(degrees = sin(t * 0.35f) * 9f, pivot = pivot) {
                        for (i in 0 until 7) {
                            val a = (-0.75f + i * 0.25f)
                            val tip = Offset(pivot.x + sin(a) * h * 1.2f, pivot.y + cos(a) * h * 1.2f)
                            drawLine(
                                rayBrush, pivot, tip,
                                strokeWidth = w * (0.05f + 0.02f * sin(t * 1.3f + i)),
                                alpha = k * 0.22f * (0.6f + 0.4f * sin(t * 1.7f + i * 1.1f))
                            )
                        }
                    }
                    drawRoundRect(
                        Color(0xFFFFC24D), cornerRadius = corner, style = Stroke(width = w * 0.012f),
                        alpha = k * (0.35f + 0.25f * breathe)
                    )
                } else {
                    drawRect(moonVeil, alpha = k)
                    drawRect(moonHalo, alpha = k * breathe)
                    // Estrellas fijas que titilan a su propio ritmo. Posiciones con la secuencia R2 (baja
                    // discrepancia): repartidas por todo el cielo, sin grumos ni columnas.
                    for (i in 0 until starCount) {
                        val x = ((0.17f + i * 0.7548776f) % 1f) * w
                        val y = ((0.31f + i * 0.5698403f) % 1f) * h * 0.88f
                        val tw = 0.5f + 0.5f * sin(t * (1.6f + (i % 5) * 0.37f) + i)
                        drawCircle(Color(0xFFEAF2FF), radius = w * (0.006f + 0.006f * tw), center = Offset(x, y), alpha = k * tw)
                    }
                    drawRoundRect(
                        Color(0xFFBFE0FF), cornerRadius = corner, style = Stroke(width = w * 0.012f),
                        alpha = k * (0.30f + 0.25f * breathe)
                    )
                }
            }
        }
    )
}

/** Fuerza del presagio según el aviso: primer turno ocioso a media luz, el siguiente a pleno cielo. */
fun starOmenStrength(idleTurns: Int, threshold: Int): Float = when {
    idleTurns <= 0 -> 0f
    idleTurns >= threshold - 1 -> 1f
    else -> 0.55f
}
