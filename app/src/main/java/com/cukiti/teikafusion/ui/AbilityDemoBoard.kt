package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.COMPANION_ABILITIES
import com.cukiti.teikafusion.model.DemoFx
import com.cukiti.teikafusion.model.DemoTile
import com.cukiti.teikafusion.model.DemoTileKind
import com.cukiti.teikafusion.model.DemoTimeline
import com.cukiti.teikafusion.model.FxTone
import com.cukiti.teikafusion.model.abilityDemoReel
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

private val DemoNight = Color(0xFF0B1024)
private val DemoEmpty = Color(0xFF182044)


/**
 * **LA HABILIDAD, JUGADA EN DIMINUTO** (pedido 2026-09-30): el minitablero del Inventario reproduce
 * en bucle el guion de [abilityDemoScript] —fichas que caen, líneas que revientan, carámbanos,
 * disparos— para que el jugador vea QUÉ hace el poder. Con [animate] en `false` (reducir efectos)
 * se queda en la foto inicial.
 *
 * El reloj se lee solo en DIBUJO: el bucle no recompone nada.
 */
@Composable
internal fun AbilityDemoBoard(
    companionId: String,
    animate: Boolean,
    modifier: Modifier = Modifier,
    /** Nivel que se enseña (P-383): el guion hace lo que hace la habilidad a ESE nivel. */
    level: Int = 1,
) {
    // Varias pasadas en los niveles con eco (P-389): solo algunas lo repiten, como en partida.
    val reel = remember(companionId, level) { abilityDemoReel(companionId, level) }
    val accent = remember(companionId) { companionAccentColor(companionId) }
    val star = remember { unitStarPath() }
    val icicle = remember { unitIciclePath() }
    val clock = remember(companionId) { mutableLongStateOf(0L) }
    // Pasada en curso para el contador (P-392). `derivedStateOf`: el reloj cambia cada fotograma,
    // pero el rótulo solo se recompone cuando cambia de pasada.
    val passNow = remember(reel) {
        derivedStateOf { if (reel == null || reel.size < 2) 0 else reel.passAt(clock.longValue) }
    }
    // Cambiar de nivel reinicia la demo: se ve el guion nuevo desde el principio.
    LaunchedEffect(companionId, animate, level) {
        clock.longValue = 0L
        if (!animate || reel == null) return@LaunchedEffect
        val t0 = withFrameMillis { it }
        while (true) withFrameMillis { clock.longValue = it - t0 }
    }
    val name = COMPANION_ABILITIES[companionId]?.name ?: "Poder"
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(DemoNight)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .semantics { contentDescription = "Demostración de $name, nivel $level" },
        contentAlignment = Alignment.Center
    ) {
        val side = minOf(maxWidth, maxHeight) - 12.dp
        Box(Modifier.size(side)) {
            Canvas(Modifier.fillMaxSize()) {
                val cell = size.width / 8f
                drawEmptyBoard(cell)
                if (reel == null) return@Canvas
                // Qué pasada suena: cada una dura lo suyo más el fundido entre pasadas.
                val now = clock.longValue
                val pass = reel.passAt(now)
                drawDemo(reel.passes[pass], reel.sceneTime(now, pass), reel.fade(now, pass), cell, accent, star, icicle)
            }
        }
        // Qué nivel se está viendo: sin esto, ojear el nivel 5 y ver «más» no se atribuye a nada.
        // Con varias pasadas, cuál suena: «1/4» dice que el eco (siempre en la 1) sale una de cada
        // cuatro. El contador va en ORO solo durante la pasada con eco (P-395): el mismo oro que sus
        // aros, para que el jugador ate la cifra al efecto. El resto del tiempo, apagado como el nivel.
        val dim = Color.White.copy(alpha = 0.55f)
        val labelStyle = Modifier.align(Alignment.TopStart).padding(start = 10.dp, top = 8.dp)
        Row(labelStyle, verticalAlignment = Alignment.CenterVertically) {
            Text(
                "NIV. $level",
                color = dim, fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp,
            )
            if (reel != null && reel.size > 1) {
                val echoPass = passNow.value == 0
                Text(
                    " · ${passNow.value + 1}/${reel.size}",
                    color = if (echoPass) Color(0xFFFFC93C) else dim,
                    fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp,
                    // Latido A LA VEZ que los aros del eco (P-398). El reloj se lee en la capa —en
                    // dibujo—, así que latir no recompone el rótulo.
                    modifier = Modifier.graphicsLayer {
                        val sc = 1f + 0.35f * reel.echoBeat(clock.longValue)
                        scaleX = sc
                        scaleY = sc
                        transformOrigin = TransformOrigin(0.15f, 0.5f)
                    }
                )
            }
        }
    }
}

private fun DrawScope.drawEmptyBoard(cell: Float) {
    val gap = cell * 0.08f
    for (y in 0 until 8) for (x in 0 until 8) {
        drawRoundRect(
            DemoEmpty,
            topLeft = Offset(x * cell + gap, y * cell + gap),
            size = Size(cell - gap * 2, cell - gap * 2),
            cornerRadius = CornerRadius(cell * 0.14f)
        )
    }
}

private fun DrawScope.drawDemo(
    tl: DemoTimeline, time: Int, fade: Float, cell: Float, accent: Color, star: Path, icicle: Path,
) {
    val i = tl.frameAt(time)
    val frame = tl.frames[i]
    val raw = if (i == 0 || frame.ms == 0) 1f else ((time - tl.starts[i]) / frame.ms.toFloat()).coerceIn(0f, 1f)
    val p = FastOutSlowInEasing.transform(raw)

    // Los destellos de línea van DEBAJO de las fichas, como el tinte de línea en partida.
    for (fx in frame.fx) drawFxUnder(fx, raw, cell, fade)

    val before = tl.before[i]
    val after = tl.after[i]
    for (k in before.indices) {
        val a = before[k]
        val b = after[k]
        when {
            a != null && b != null -> drawTile(
                x = lerpF(a.x, b.x, p), y = lerpF(a.y, b.y, p),
                size = lerpF(a.size, b.size, p), scale = lerpF(a.scale, b.scale, p),
                from = a.kind, to = b.kind, mix = p, alpha = fade, flash = 0f,
                cell = cell, star = star
            )
            // Revienta: se hincha, destella en blanco y se desvanece.
            a != null -> drawTile(
                x = a.x, y = a.y, size = a.size, scale = a.scale * (1f + 0.35f * raw),
                from = a.kind, to = a.kind, mix = 0f, alpha = (1f - raw) * fade, flash = 1f - raw,
                cell = cell, star = star
            )
            // Brota: crece desde nada con un pequeño rebote.
            b != null -> drawTile(
                x = b.x, y = b.y, size = b.size, scale = b.scale * backOut(raw),
                from = b.kind, to = b.kind, mix = 0f, alpha = raw.coerceAtMost(1f) * fade, flash = 0f,
                cell = cell, star = star
            )
        }
    }
    for (fx in frame.fx) drawFxOver(fx, raw, cell, fade, accent, star, icicle)
}

private fun DrawScope.drawTile(
    x: Float, y: Float, size: Float, scale: Float,
    from: DemoTileKind, to: DemoTileKind, mix: Float,
    alpha: Float, flash: Float, cell: Float, star: Path,
) {
    if (alpha <= 0.01f || scale <= 0.01f) return
    val fill = lerp(kindColor(from), kindColor(to), mix)
    val a = alpha * lerpF(kindAlpha(from), kindAlpha(to), mix)
    if (to.round) {
        val c = Offset(x * cell, y * cell)
        val r = size * cell * 0.5f * scale
        drawCircle(fill, r, c, alpha = a)
        drawCircle(Color.White, r * 0.35f, c + Offset(-r * 0.3f, -r * 0.35f), alpha = a * 0.35f)
        if (to != DemoTileKind.FLOWER) {
            // Carita: dos ojos. Es lo que hace que se lea «Teika» y no «bola».
            val eye = r * 0.13f
            drawCircle(Color(0xFF10142A), eye, c + Offset(-r * 0.32f, -r * 0.05f), alpha = a)
            drawCircle(Color(0xFF10142A), eye, c + Offset(r * 0.32f, -r * 0.05f), alpha = a)
        } else {
            for (k in 0 until 5) {
                val ang = (k / 5f) * 2f * PI.toFloat()
                drawCircle(Color(0xFFFF9BD2), r * 0.42f, c + Offset(cos(ang) * r * 0.62f, sin(ang) * r * 0.62f), alpha = a)
            }
            drawCircle(Color(0xFFFFE066), r * 0.36f, c, alpha = a)
        }
        return
    }
    val gap = cell * 0.08f
    val w = size * cell - gap * 2
    val h = cell - gap * 2
    val cx = x * cell + gap + w / 2f
    val cy = y * cell + gap + h / 2f
    val sw = w * scale
    val sh = h * scale
    val tl = Offset(cx - sw / 2f, cy - sh / 2f)
    val radius = CornerRadius(cell * 0.14f * scale)
    drawRoundRect(fill, tl, Size(sw, sh), radius, alpha = a)
    // Canto claro arriba: volumen de bloque con dos rectángulos, sin degradados que asignar.
    drawRoundRect(Color.White, tl, Size(sw, sh * 0.32f), radius, alpha = a * 0.22f)
    if (from == DemoTileKind.STAR || to == DemoTileKind.STAR) {
        val starA = if (from == to) 1f else if (to == DemoTileKind.STAR) mix else 1f - mix
        val s = minOf(sw, sh) * 0.62f
        translate(cx - s / 2f, cy - s / 2f) {
            scale(s, s, pivot = Offset.Zero) { drawPath(star, Color(0xFFFFD84A), alpha = a * starA) }
        }
    }
    if (flash > 0f) drawRoundRect(Color.White, tl, Size(sw, sh), radius, alpha = flash * 0.7f * alpha.coerceAtLeast(0.3f))
}

private fun DrawScope.drawFxUnder(fx: DemoFx, p: Float, cell: Float, fade: Float) {
    val pulse = sin(p * PI.toFloat()).coerceAtLeast(0.25f) * fade
    when (fx) {
        is DemoFx.RowFlash -> drawRect(toneColor(fx.tone), Offset(0f, fx.row * cell), Size(cell * 8, cell), alpha = 0.55f * pulse)
        is DemoFx.ColFlash -> drawRect(toneColor(fx.tone), Offset(fx.col * cell, 0f), Size(cell, cell * 8), alpha = 0.55f * pulse)
        is DemoFx.Fog -> drawRect(Color(0xFF6BD13A), Offset.Zero, Size(cell * 8, cell * 8), alpha = lerpF(fx.a0, fx.a1, p) * fade)
        else -> Unit
    }
}

private fun DrawScope.drawFxOver(
    fx: DemoFx, p: Float, cell: Float, fade: Float, accent: Color, star: Path, icicle: Path,
) {
    when (fx) {
        is DemoFx.Ring -> {
            val beat = 0.5f + 0.5f * sin(p * 4f * PI.toFloat())
            drawCircle(
                toneColor(fx.tone), fx.r * cell * (1f + 0.12f * beat), Offset(fx.cx * cell, fx.cy * cell),
                alpha = (0.55f + 0.4f * beat) * fade, style = Stroke(cell * 0.12f)
            )
        }
        is DemoFx.Aim -> {
            val dx = fx.x1 - fx.x0
            val dy = fx.y1 - fx.y0
            val len = sqrt(dx * dx + dy * dy)
            val dots = (len / 0.4f).toInt()
            val shown = (dots * (p * 1.6f).coerceAtMost(1f)).toInt()
            for (k in 0..shown) {
                val f = k / dots.toFloat()
                drawCircle(Color.White, cell * 0.07f, Offset((fx.x0 + dx * f) * cell, (fx.y0 + dy * f) * cell), alpha = 0.8f * fade)
            }
        }
        is DemoFx.Shot -> {
            val bx = lerpF(fx.x0, fx.x1, p)
            val by = lerpF(fx.y0, fx.y1, p)
            val c = toneColor(fx.tone)
            drawLine(c, Offset(fx.x0 * cell, fx.y0 * cell), Offset(bx * cell, by * cell), cell * 0.16f, StrokeCap.Round, alpha = 0.5f * fade)
            drawCircle(c, cell * 0.3f, Offset(bx * cell, by * cell), alpha = fade)
            drawCircle(Color.White, cell * 0.13f, Offset(bx * cell, by * cell), alpha = fade)
        }
        is DemoFx.Slash -> {
            val ex = lerpF(fx.x0, fx.x1, p)
            val ey = lerpF(fx.y0, fx.y1, p)
            // La cola se borra a la vez que avanza la punta: se ve el tajo pasar, no una raya fija.
            val tail = (p - 0.45f).coerceAtLeast(0f) / 0.55f
            val sx = lerpF(fx.x0, fx.x1, tail)
            val sy = lerpF(fx.y0, fx.y1, tail)
            drawLine(accent, Offset(sx * cell, sy * cell), Offset(ex * cell, ey * cell), cell * 0.22f, StrokeCap.Round, alpha = 0.6f * fade)
            drawLine(Color.White, Offset(sx * cell, sy * cell), Offset(ex * cell, ey * cell), cell * 0.08f, StrokeCap.Round, alpha = fade)
        }
        is DemoFx.Icicle -> {
            val tipY = lerpF(fx.y0, fx.y1, p) * cell
            val w = cell * 0.8f
            val h = cell * 1.6f
            translate((fx.col + 0.5f) * cell - w / 2f, tipY - h) {
                scale(w, h, pivot = Offset.Zero) { drawPath(icicle, Color(0xFFBFF3FF), alpha = fade) }
            }
            drawCircle(Color.White, cell * 0.12f, Offset((fx.col + 0.5f) * cell, tipY - cell * 0.15f), alpha = 0.9f * fade)
        }
        is DemoFx.Mouth -> {
            val open = lerpF(fx.open0, fx.open1, p)
            if (open > 0.01f) {
                val w = cell * 3.2f
                val h = cell * 1.9f * open
                val top = cell * 8f - h
                val left = fx.cx * cell - w / 2f
                drawOval(Color(0xFFE0507A), Offset(left - cell * 0.15f, top - cell * 0.12f), Size(w + cell * 0.3f, h * 2f), alpha = fade)
                drawOval(Color(0xFF2A0B1A), Offset(left, top), Size(w, h * 2f), alpha = fade)
                for (k in 0 until 4) {
                    val tx = left + w * (0.18f + k * 0.21f)
                    drawRect(Color.White, Offset(tx, top + h * 0.12f), Size(cell * 0.22f, cell * 0.3f * open), alpha = fade)
                }
            }
        }
        is DemoFx.Eclipse -> {
            val c = Offset(lerpF(fx.cx0, fx.cx1, p) * cell, lerpF(fx.cy0, fx.cy1, p) * cell)
            val r = lerpF(fx.r0, fx.r1, p) * cell
            val a = lerpF(fx.a0, fx.a1, p) * fade
            drawCircle(accent, r * 1.18f, c, alpha = 0.35f * a)
            drawCircle(Color(0xFFFFE9A8), r * 1.04f, c, alpha = 0.8f * a, style = Stroke(cell * 0.14f))
            drawCircle(Color(0xFF05060F), r, c, alpha = 0.92f * a)
        }
        is DemoFx.Sparkle -> {
            val rise = p * cell * 0.9f
            val s = cell * 0.5f
            val a = (1f - p) * fade
            for (k in 0 until 2) {
                val ox = (fx.x + 0.5f) * cell + (if (k == 0) -0.3f else 0.3f) * cell - s / 2f
                val oy = (fx.y + 0.2f) * cell - rise - (if (k == 0) 0f else 0.25f * cell)
                translate(ox, oy) { scale(s, s, pivot = Offset.Zero) { drawPath(star, Color(0xFFFFD84A), alpha = a) } }
            }
        }
        DemoFx.Echo -> {
            // Dos aros dorados salen del centro mientras el tablero se rehace: la carga vuelve.
            for (k in 0 until 2) {
                val q = (p * 1.4f - k * 0.4f).coerceIn(0f, 1f)
                if (q <= 0f || q >= 1f) continue
                drawCircle(Color(0xFFFFC93C), cell * (0.4f + 5f * q), Offset(cell * 4, cell * 4), alpha = (1f - q) * fade, style = Stroke(cell * 0.16f))
            }
        }
        is DemoFx.RowFlash, is DemoFx.ColFlash, is DemoFx.Fog -> Unit
    }
}

private fun kindColor(k: DemoTileKind): Color = when (k) {
    DemoTileKind.WOOD, DemoTileKind.STAR -> Color(0xFFB4875A)
    DemoTileKind.CRYSTAL -> Color(0xFF7FE3FF)
    DemoTileKind.FIRE -> Color(0xFFFF7A2E)
    DemoTileKind.GHOST -> Color(0xFFB983FF)
    DemoTileKind.TOXIC -> Color(0xFF7BD94A)
    DemoTileKind.GOLD -> Color(0xFFFFC93C)
    DemoTileKind.SLEEP -> Color(0xFFB9A7FF)
    DemoTileKind.PLACED -> Color(0xFFD9A46C)
    DemoTileKind.TEIKA_1 -> Color(0xFF8BE0FF)
    DemoTileKind.TEIKA_2 -> Color(0xFF8BFF6B)
    DemoTileKind.TEIKA_3 -> Color(0xFFFFB347)
    DemoTileKind.FLOWER -> Color(0xFF5FCB5A)
}

private fun kindAlpha(k: DemoTileKind): Float = if (k == DemoTileKind.GHOST) 0.55f else 1f

private fun toneColor(t: FxTone): Color = when (t) {
    FxTone.WHITE -> Color.White
    FxTone.GOLD -> Color(0xFFFFD84A)
    FxTone.FIRE -> Color(0xFFFF6A1F)
    FxTone.ELECTRIC -> Color(0xFFFFF04A)
    FxTone.ICE -> Color(0xFFBFF3FF)
    FxTone.GHOST -> Color(0xFFC9A0FF)
    FxTone.TOXIC -> Color(0xFF7BD94A)
    FxTone.CRYSTAL -> Color(0xFF7FE3FF)
    FxTone.NATURE -> Color(0xFFFF9BD2)
    FxTone.SLEEP -> Color(0xFFB9A7FF)
}

private fun lerpF(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Crecer con un pequeño pasarse de rosca, como las fichas al aparecer en la bandeja. */
private fun backOut(t: Float): Float {
    val c = 1.70158f
    val u = t - 1f
    return 1f + (c + 1f) * u * u * u + c * u * u
}

/** Estrella de cinco puntas en el cuadrado unidad. */
private fun unitStarPath(): Path = Path().apply {
    for (k in 0 until 10) {
        val r = if (k % 2 == 0) 0.5f else 0.21f
        val ang = -PI.toFloat() / 2f + k * PI.toFloat() / 5f
        val px = 0.5f + cos(ang) * r
        val py = 0.53f + sin(ang) * r
        if (k == 0) moveTo(px, py) else lineTo(px, py)
    }
    close()
}

/** Carámbano en el rectángulo unidad, con la punta abajo (0,5 ; 1). */
private fun unitIciclePath(): Path = Path().apply {
    moveTo(0f, 0f); lineTo(1f, 0f); lineTo(0.5f, 1f); close()
}
