package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

// B-3: paths reutilizables — DrawScope es single-threaded, no hay riesgo de concurrencia.
private val _reusableStarPath = Path()
private val _reusableGemPath  = Path()

// NOTA: la antigua ParticleExplosion (un Canvas full-screen + Animatable POR evento) se reemplazó
// por BatchParticleExplosionVFX en VFX.kt (todas las ráfagas en un solo Canvas/reloj) por rendimiento.

// ─────────────────────────────────────────────────────────────────────────────
// Star burst — partículas tipo estrella/gema con física (salto + gravedad)
// ─────────────────────────────────────────────────────────────────────────────

private data class StarParticle(
    val start: Offset,
    val angleRad: Float,
    val initialSpeed: Float,
    val baseSize: Float,
    val color: Color,
    val rotationStart: Float,
    val rotationSpeed: Float,
    val isGem: Boolean
)

@Composable
fun StarParticleExplosion(
    center: Offset,
    isGem: Boolean = false,
    intensity: Float = 1f,
    delayMs: Int = 0,
    onComplete: () -> Unit
) {
    val count = (12 * intensity).toInt().coerceAtLeast(8)
    val starPalette = listOf(
        Color(0xFFFFE066), Color(0xFFFFD700), Color(0xFFFFC233),
        Color(0xFFFF8C00), Color(0xFFFFAB40)
    )
    val gemPalette = listOf(
        Color(0xFF40C4FF), Color(0xFFEA80FC), Color(0xFF69F0AE),
        Color(0xFFFF80AB), Color(0xFFB388FF)
    )
    val palette = if (isGem) gemPalette else starPalette

    val particles = remember(center.x, center.y, intensity) {
        List(count) {
            val angle = (it * 360f / count + Random.nextFloat() * 25f) * PI.toFloat() / 180f
            StarParticle(
                start         = center,
                angleRad      = angle,
                initialSpeed  = (180f + Random.nextFloat() * 140f) * intensity,
                baseSize      = (10f + Random.nextFloat() * 8f) * intensity,
                color         = palette.random(),
                rotationStart = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 540f,
                isGem         = isGem
            )
        }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        progress.animateTo(1f, tween(durationMillis = 900, easing = LinearOutSlowInEasing))
        onComplete()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (progress.value <= 0f) return@Canvas  // todavía en delay
        val t = progress.value
        // Trayectoria: salida rápida + gravedad creciente (parabólica)
        val gravity = 480f * t * t
        particles.forEach { p ->
            val dist = p.initialSpeed * t * (1f - 0.45f * t)  // ease-out
            val x = p.start.x + cos(p.angleRad) * dist
            val y = p.start.y + sin(p.angleRad) * dist + gravity
            val alpha = when {
                t < 0.10f -> t / 0.10f
                t > 0.55f -> ((1f - t) / 0.45f).coerceIn(0f, 1f)
                else      -> 1f
            }
            val size = p.baseSize * (1.0f + 0.3f * sin(t * PI.toFloat())) * (1f - t * 0.3f)
            rotate(degrees = p.rotationStart + p.rotationSpeed * t, pivot = Offset(x, y)) {
                if (p.isGem) drawGem(Offset(x, y), size, p.color.copy(alpha = alpha))
                else         drawStar(Offset(x, y), size, p.color.copy(alpha = alpha))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Block burst — cubitos 3D del color del bloque destruido (estilo Block Blast)
// ─────────────────────────────────────────────────────────────────────────────

private data class CubeParticle(
    val start: Offset,
    val angleRad: Float,
    val initialSpeed: Float,
    val baseSize: Float,
    val rotationStart: Float,
    val rotationSpeed: Float
)

@Composable
fun BlockBurstExplosion(
    center: Offset,
    colorType: Int,
    intensity: Float = 1f,
    delayMs: Int = 0,
    onComplete: () -> Unit
) {
    // Paleta del color del bloque — saca lighter (top bevel) y darker (bottom bevel)
    val palette = pieceColors[colorType.coerceAtLeast(0) % pieceColors.size]
    val baseColor   = palette[0]
    val lightColor  = palette[1]
    val darkColor   = palette[2]

    val count = (10 * intensity).toInt().coerceAtLeast(6)
    val particles = remember(center.x, center.y, intensity) {
        List(count) {
            // Distribución hacia arriba/laterales (no hacia abajo) — efecto cascada
            val baseAng = (-90f + (it * (220f / count) - 110f) + Random.nextFloat() * 12f - 6f)
            val ang = baseAng * PI.toFloat() / 180f
            CubeParticle(
                start         = center,
                angleRad      = ang,
                initialSpeed  = (150f + Random.nextFloat() * 130f) * intensity,
                baseSize      = (8f + Random.nextFloat() * 7f) * intensity,
                rotationStart = Random.nextFloat() * 360f,
                rotationSpeed = (Random.nextFloat() - 0.5f) * 720f
            )
        }
    }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs.toLong())
        progress.animateTo(1f, tween(durationMillis = 850, easing = LinearOutSlowInEasing))
        onComplete()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        if (progress.value <= 0f) return@Canvas
        val t = progress.value
        val gravity = 580f * t * t   // bajan rápido como en la ilustración
        particles.forEach { p ->
            val dist = p.initialSpeed * t * (1f - 0.40f * t)
            val x = p.start.x + cos(p.angleRad) * dist
            val y = p.start.y + sin(p.angleRad) * dist + gravity
            val alpha = when {
                t < 0.10f -> t / 0.10f
                t > 0.60f -> ((1f - t) / 0.40f).coerceIn(0f, 1f)
                else      -> 1f
            }
            val size = p.baseSize * (1f - t * 0.25f)
            rotate(degrees = p.rotationStart + p.rotationSpeed * t, pivot = Offset(x, y)) {
                drawMiniCube(
                    center = Offset(x, y),
                    half   = size,
                    base   = baseColor.copy(alpha = alpha),
                    light  = lightColor.copy(alpha = alpha),
                    dark   = darkColor.copy(alpha = alpha)
                )
            }
        }
    }
}

/** Cubo pequeño con relieve 3D redondeado (top-light → base → bottom-dark + brillo). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawMiniCube(
    center: Offset,
    half: Float,
    base: Color,
    light: Color,
    dark: Color
) {
    val tl   = Offset(center.x - half, center.y - half)
    val full = androidx.compose.ui.geometry.Size(half * 2, half * 2)
    val cr   = androidx.compose.ui.geometry.CornerRadius(half * 0.4f, half * 0.4f)
    // Base (cuerpo principal)
    drawRoundRect(color = base, topLeft = tl, size = full, cornerRadius = cr)
    // Top highlight (más claro)
    drawRoundRect(
        color = light, topLeft = tl,
        size = androidx.compose.ui.geometry.Size(half * 2, half * 0.62f), cornerRadius = cr
    )
    // Bottom shadow (más oscuro)
    drawRoundRect(
        color = dark, topLeft = Offset(center.x - half, center.y + half * 0.36f),
        size = androidx.compose.ui.geometry.Size(half * 2, half * 0.64f), cornerRadius = cr
    )
    // Punto especular
    drawCircle(
        color  = Color.White.copy(alpha = base.alpha * 0.55f),
        radius = half * 0.22f,
        center = Offset(center.x - half * 0.28f, center.y - half * 0.3f)
    )
    // Filo de definición
    drawRoundRect(
        color = dark, topLeft = tl, size = full, cornerRadius = cr,
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.2f)
    )
}

/** Estrella de 5 puntas dibujada como Path. B-3: reutiliza _reusableStarPath sin new alloc. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStar(
    center: Offset,
    radius: Float,
    color: Color
) {
    val outer = radius
    val inner = radius * 0.42f
    _reusableStarPath.reset()
    for (i in 0 until 10) {
        val r   = if (i % 2 == 0) outer else inner
        val ang = -PI.toFloat() / 2f + i * (PI.toFloat() / 5f)
        val x   = center.x + r * cos(ang)
        val y   = center.y + r * sin(ang)
        if (i == 0) _reusableStarPath.moveTo(x, y) else _reusableStarPath.lineTo(x, y)
    }
    _reusableStarPath.close()
    drawPath(_reusableStarPath, color)
    drawPath(
        path  = _reusableStarPath,
        color = color.copy(alpha = (color.alpha * 0.6f).coerceIn(0f, 1f)),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
    )
    drawCircle(Color.White.copy(alpha = color.alpha * 0.7f), radius = radius * 0.18f, center = center)
}

/** Gema (rombo con highlight). B-3: reutiliza _reusableGemPath sin new alloc. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGem(
    center: Offset,
    radius: Float,
    color: Color
) {
    _reusableGemPath.reset()
    _reusableGemPath.moveTo(center.x, center.y - radius)
    _reusableGemPath.lineTo(center.x + radius * 0.65f, center.y)
    _reusableGemPath.lineTo(center.x, center.y + radius)
    _reusableGemPath.lineTo(center.x - radius * 0.65f, center.y)
    _reusableGemPath.close()
    drawPath(_reusableGemPath, color)
    drawCircle(Color.White.copy(alpha = color.alpha * 0.55f), radius = radius * 0.22f,
               center = Offset(center.x - radius * 0.18f, center.y - radius * 0.25f))
}

