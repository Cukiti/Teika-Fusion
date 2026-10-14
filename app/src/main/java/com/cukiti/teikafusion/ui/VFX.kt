package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.TeikaType
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.model.MegaCollapse
import com.cukiti.teikafusion.model.MegaFling
import com.cukiti.teikafusion.model.PlantAbduction
import com.cukiti.teikafusion.model.PlantEdge
import com.cukiti.teikafusion.viewmodel.TeikaBody
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.random.Random
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.drawWithCache
import com.cukiti.teikafusion.model.OrbitRules
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import com.cukiti.teikafusion.model.WILDCARD_COLOR

private fun formatScore(score: Int): String = when {
    score >= 1_000_000 -> "%.1fM".format(score / 1_000_000.0)
    score >= 1_000     -> "%.1fK".format(score / 1_000.0)
    else               -> score.toString()
}

private fun comboTierColor(streak: Int): Color = when {
    streak >= 10 -> Color(0xFFFFD700)
    streak >= 9  -> Color(0xFF00E5FF)
    streak >= 8  -> Color(0xFFFFAB00)
    streak >= 7  -> Color(0xFFFF1744)
    streak >= 6  -> Color(0xFFEA80FC)
    streak >= 4  -> Color(0xFFFF6E40)
    streak >= 3  -> Color(0xFFFFD740)
    streak >= 2  -> Color(0xFF69F0AE)
    else         -> Color(0xFF32CD32)
}

/**
 * INFINITO: el "+score" no se limita a subir y desvanecerse — vuela en arco Bézier HASTA el marcador
 * (contador de score del top bar) y hace un pop al partir. Cierra el bucle "romper → recolectar →
 * marcador salta" (el count-up + kick del marcador ya corren al cambiar el score). Coordenadas en el
 * espacio LOCAL del root de GameScreen (igual que el vuelo de cronómetros).
 */
@Composable
fun ScoreFlyVFX(
    text: String,
    fromPx: Offset,
    toPx: Offset,
    color: Color,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    val pop      = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
        progress.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        onComplete()
    }
    val t = progress.value
    // Arco con punto de control elevado (el número "salta" hacia el marcador).
    val midX = (fromPx.x + toPx.x) / 2f
    val midY = minOf(fromPx.y, toPx.y) - 120f
    val x = (1 - t) * (1 - t) * fromPx.x + 2 * (1 - t) * t * midX + t * t * toPx.x
    val y = (1 - t) * (1 - t) * fromPx.y + 2 * (1 - t) * t * midY + t * t * toPx.y
    val scale = (pop.value * (1f - t * 0.45f)).coerceAtLeast(0.25f)
    val alpha = if (t > 0.8f) ((1f - t) / 0.2f).coerceIn(0f, 1f) else 1f

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                // Centrado aproximado del número sobre (x,y) (el flyer es transitorio y veloz).
                .offset { IntOffset((x - 28f).roundToInt(), (y - 18f).roundToInt()) }
                .graphicsLayer {
                    scaleX = scale; scaleY = scale; this.alpha = alpha
                },
            contentAlignment = Alignment.Center
        ) {
            val halo = Color.Black.copy(alpha = 0.55f)
            Text(text, color = halo, fontSize = 24.sp, fontWeight = FontWeight.Black,
                maxLines = 1, softWrap = false, modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp))
            Text(text, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black,
                maxLines = 1, softWrap = false)
        }
    }
}

@Composable
fun FloatingScoreAnim(
    event: GameEvent.FloatingScore,
    onComplete: () -> Unit
) {
    val offsetY = remember { Animatable(0f) }
    val alpha   = remember { Animatable(0f) }
    val scale   = remember { Animatable(0.05f) }

    val streak   = event.comboStreak
    val mainColor = if (event.isCombo) comboTierColor(streak) else Color(0xFF32CD32)
    val peakScale = if (event.isCombo) 1.6f + (streak - 2).coerceAtLeast(0) * 0.1f else 1.0f
    // Combo: deriva corta para quedarse en su zona inferior (no invade el ×N del centro).
    val travelY   = if (event.isCombo) -120f else -150f
    val duration  = if (event.isCombo) 1400 else 1100

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(peakScale, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium))
            scale.animateTo(peakScale * 0.85f, tween(90))
        }
        launch {
            alpha.animateTo(1f, tween(120))
            alpha.animateTo(0f, tween(680, delayMillis = if (event.isCombo) 550 else 300))
        }
        launch {
            offsetY.animateTo(travelY, tween(duration, easing = FastOutSlowInEasing))
            onComplete()
        }
    }

    val text = "+${formatScore(event.score)}"
    val fontSize = if (event.isCombo) (26 + (streak - 2).coerceIn(0, 4) * 2).sp else 22.sp
    val fillColor = if (event.isCombo) Color.White else mainColor

    // UN solo contenedor: centra su contenido y aplica escala+alpha+subida vía graphicsLayer. La
    // escala crece SIMÉTRICA alrededor del centro del texto (antes crecía desde la esquina + un
    // offset mágico negativo, lo que arrastraba el número a la izquierda sobre los Teikas y "comía"
    // su mitad). Las 4 sombras forman un halo de contraste para que se lea sobre cualquier ficha.
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.graphicsLayer {
            translationY = offsetY.value
            scaleX = scale.value
            scaleY = scale.value
            this.alpha = alpha.value
        }
    ) {
        val halo = Color.Black.copy(alpha = 0.55f)
        Text(text, color = halo, fontSize = fontSize, fontWeight = FontWeight.Black,
            maxLines = 1, softWrap = false, modifier = Modifier.offset(x = 1.5.dp, y = 1.5.dp))
        Text(text, color = halo, fontSize = fontSize, fontWeight = FontWeight.Black,
            maxLines = 1, softWrap = false, modifier = Modifier.offset(x = (-1.5).dp, y = (-1.5).dp))
        Text(text, color = halo, fontSize = fontSize, fontWeight = FontWeight.Black,
            maxLines = 1, softWrap = false, modifier = Modifier.offset(x = 1.5.dp, y = (-1.5).dp))
        Text(text, color = halo, fontSize = fontSize, fontWeight = FontWeight.Black,
            maxLines = 1, softWrap = false, modifier = Modifier.offset(x = (-1.5).dp, y = 1.5.dp))
        Text(text, color = fillColor, fontSize = fontSize, fontWeight = FontWeight.Black,
            maxLines = 1, softWrap = false)
    }
}




private fun DrawScope.plantEdgeSource(edge: PlantEdge, boardPx: Float, target: Offset): Offset =
    when (edge) {
        PlantEdge.TOP -> Offset(target.x, -boardPx * 0.06f)
        PlantEdge.RIGHT -> Offset(boardPx * 1.06f, target.y)
        PlantEdge.BOTTOM -> Offset(target.x, boardPx * 1.06f)
        PlantEdge.LEFT -> Offset(-boardPx * 0.06f, target.y)
    }

internal fun DrawScope.drawPlantTether(
    edge: PlantEdge,
    target: Offset,
    strength: Float,
    alpha: Float = 1f,
    /** Path reutilizable: la raíz que vive varios turnos pasa uno propio y no crea uno por fotograma. */
    path: Path = Path(),
) {
    val boardPx = size.minDimension
    val source = plantEdgeSource(edge, boardPx, target)
    val dx = target.x - source.x
    val dy = target.y - source.y
    val sideways = when (edge) {
        PlantEdge.TOP, PlantEdge.BOTTOM -> boardPx * 0.10f
        PlantEdge.LEFT, PlantEdge.RIGHT -> 0f
    }
    val vertical = when (edge) {
        PlantEdge.LEFT, PlantEdge.RIGHT -> boardPx * 0.10f
        PlantEdge.TOP, PlantEdge.BOTTOM -> 0f
    }
    path.rewind()
    path.apply {
        moveTo(source.x, source.y)
        cubicTo(
            source.x + dx * 0.34f + sideways,
            source.y + dy * 0.34f + vertical,
            source.x + dx * 0.68f - sideways,
            source.y + dy * 0.68f - vertical,
            source.x + dx * strength,
            source.y + dy * strength
        )
    }
    drawPath(path, Color(0xFF173D1B).copy(alpha = 0.92f * alpha), style = Stroke(boardPx * 0.045f, cap = StrokeCap.Round))
    drawPath(path, Color(0xFF397A2B).copy(alpha = 0.95f * alpha), style = Stroke(boardPx * 0.028f, cap = StrokeCap.Round))
    drawPath(path, Color(0xFF8AD45C).copy(alpha = 0.55f * alpha), style = Stroke(boardPx * 0.009f, cap = StrokeCap.Round))
}

private fun DrawScope.drawPlantGrip(
    center: Offset,
    radius: Float,
    progress: Float,
    pulse: Float = 1f,
    anger: Float = 0f,
    alpha: Float = 1f
) {
    val grip = progress.coerceIn(0f, 1f)
    if (grip <= 0f) return
    val rootColor = lerp(Color(0xFF397A2B), Color(0xFF9A3D16), anger.coerceIn(0f, 1f))
    val highlight = lerp(Color(0xFF9BE36A), Color(0xFFFFB33B), anger.coerceIn(0f, 1f))
    val sweep = 205f * grip

    repeat(4) { index ->
        val start = index * 90f - 58f
        val ringRadius = radius * (0.78f + index % 2 * 0.15f) * pulse
        drawArc(
            color = Color(0xFF173D1B).copy(alpha = 0.9f * alpha),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(radius * 0.22f, cap = StrokeCap.Round)
        )
        drawArc(
            color = rootColor.copy(alpha = 0.96f * alpha),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(radius * 0.13f, cap = StrokeCap.Round)
        )
        drawArc(
            color = highlight.copy(alpha = 0.72f * alpha),
            startAngle = start,
            sweepAngle = sweep,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(radius * 0.035f, cap = StrokeCap.Round)
        )
    }

    repeat(4) { index ->
        val angle = index * PI.toFloat() / 2f + PI.toFloat() * 0.25f
        val knot = Offset(
            center.x + cos(angle) * radius * 0.82f,
            center.y + sin(angle) * radius * 0.82f
        )
        drawCircle(Color(0xFF173D1B).copy(alpha = alpha), radius * 0.19f * pulse, knot)
        drawCircle(highlight.copy(alpha = alpha), radius * 0.085f * pulse, knot)
    }
}

private fun DrawScope.drawPlantFrontGrip(
    center: Offset,
    radius: Float,
    progress: Float,
    anger: Float = 0f,
    alpha: Float = 1f
) {
    val grip = progress.coerceIn(0f, 1f)
    if (grip <= 0f) return
    val rootColor = lerp(Color(0xFF397A2B), Color(0xFFA74619), anger.coerceIn(0f, 1f))
    val highlight = lerp(Color(0xFFA7F36B), Color(0xFFFFD36A), anger.coerceIn(0f, 1f))
    for (index in 0..1) {
        val start = if (index == 0) -28f else 154f
        val ringRadius = radius * (0.80f + index * 0.13f)
        drawArc(
            color = Color(0xFF102915).copy(alpha = 0.86f * alpha),
            startAngle = start,
            sweepAngle = 112f * grip,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(radius * 0.18f, cap = StrokeCap.Round)
        )
        drawArc(
            color = rootColor.copy(alpha = 0.96f * alpha),
            startAngle = start,
            sweepAngle = 112f * grip,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(radius * 0.105f, cap = StrokeCap.Round)
        )
        drawArc(
            color = highlight.copy(alpha = 0.72f * alpha),
            startAngle = start + 8f,
            sweepAngle = 70f * grip,
            useCenter = false,
            topLeft = Offset(center.x - ringRadius, center.y - ringRadius),
            size = Size(ringRadius * 2f, ringRadius * 2f),
            style = Stroke(radius * 0.028f, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun PlantAbductionOverlay(
    abduction: PlantAbduction,
    cellSize: Dp,
    isMoving: Boolean,
) {
    val density = LocalDensity.current
    val capture = remember(abduction.teikaId) { Animatable(0f) }
    LaunchedEffect(abduction.teikaId) {
        capture.animateTo(1f, tween(900, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)))
    }
    val pulseTransition = rememberInfiniteTransition(label = "plantCapturePulse")
    val pulse by pulseTransition.animateFloat(
        0.82f, 1.08f,
        infiniteRepeatable(tween(1050, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "plantCapturePulse"
    )
    val cs = with(density) { cellSize.toPx() }
    val center = Offset(
        (abduction.currentRoot.x + abduction.teikaType.width / 2f) * cs,
        (abduction.currentRoot.y + abduction.teikaType.height / 2f) * cs
    )
    // La captura dura VARIOS TURNOS latiendo cada fotograma: sus dos raíces reutilizan su Path.
    val tetherA = remember { Path() }
    val tetherB = remember { Path() }

    if (!isMoving) {
        // Capa propia (auditoría de fluidez 2026-09-29): el latido anima cada fotograma y, sin capa,
        // arrastraba a regrabar a los hermanos del tablero.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val perpendicular = if (abduction.edge == PlantEdge.TOP || abduction.edge == PlantEdge.BOTTOM) {
                Offset(cs * 0.36f, 0f)
            } else {
                Offset(0f, cs * 0.36f)
            }
            if (abduction.linesBroken < 1) drawPlantTether(abduction.edge, center - perpendicular, capture.value, path = tetherA)
            if (abduction.linesBroken < 2) drawPlantTether(abduction.edge, center + perpendicular, capture.value, path = tetherB)
            drawPlantGrip(
                center = center,
                radius = cs * abduction.teikaType.width.coerceAtLeast(1) * 0.48f,
                progress = capture.value,
                pulse = pulse
            )
        }
        TeikaIcon(
            type = abduction.teikaType,
            modifier = Modifier
                .offset(
                    x = cellSize * abduction.currentRoot.x,
                    y = cellSize * abduction.currentRoot.y
                )
                .size(
                    width = cellSize * abduction.teikaType.width,
                    height = cellSize * abduction.teikaType.height
                )
                .scale(teikaVisualScale(abduction.teikaType))
        )
        Canvas(Modifier.fillMaxSize()) {
            drawPlantFrontGrip(
                center = center,
                radius = cs * abduction.teikaType.width.coerceAtLeast(1) * 0.48f,
                progress = capture.value
            )

        }
    }
}

@Composable
fun PlantDragVFX(event: GameEvent.PlantDrag, cellSize: Dp, startDelayMillis: Int = 0, onComplete: () -> Unit) {
    val density = LocalDensity.current
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(startDelayMillis.toLong())
        progress.animateTo(
            1f,
            tween(event.durationMillis, easing = LinearEasing)
        )
        onComplete()
    }
    val cs = with(density) { cellSize.toPx() }
    val raw = progress.value
    val grip = if (event.isInitialCapture) (raw / 0.38f).coerceIn(0f, 1f) else 1f
    val moveRaw = if (event.isInitialCapture) ((raw - 0.28f) / 0.72f).coerceIn(0f, 1f) else raw
    val move = moveRaw * moveRaw * (3f - 2f * moveRaw)
    val gridX = event.from.x + (event.to.x - event.from.x) * move
    val gridY = event.from.y + (event.to.y - event.from.y) * move
    val x = gridX * cs
    val y = gridY * cs
    val center = Offset(
        x + event.teikaType.width * cs / 2f,
        y + event.teikaType.height * cs / 2f
    )

    Canvas(Modifier.fillMaxSize()) {
        val perpendicular = if (event.edge == PlantEdge.TOP || event.edge == PlantEdge.BOTTOM) {
            Offset(cs * 0.34f, 0f)
        } else {
            Offset(0f, cs * 0.34f)
        }
        if (event.linesBroken < 1) drawPlantTether(event.edge, center - perpendicular, grip)
        if (event.linesBroken < 2) drawPlantTether(event.edge, center + perpendicular, grip)
        drawPlantGrip(
            center = center,
            radius = cs * event.teikaType.width.coerceAtLeast(1) * 0.48f,
            progress = grip,
            pulse = 1f + sin(raw * PI.toFloat() * 5f) * 0.035f
        )
        if (event.isInitialCapture) {
            val flash = (1f - abs(raw - 0.30f) / 0.12f).coerceIn(0f, 1f)
            drawCircle(
                Color(0xFFC9FF8B).copy(alpha = flash * 0.42f),
                cs * event.teikaType.width * (0.7f + flash * 0.45f),
                center
            )
        }
    }
    val struggle = if (event.isInitialCapture && raw < 0.45f) {
        sin(raw * PI.toFloat() * 12f) * (1f - raw / 0.45f)
    } else 0f
    TeikaIcon(
        type = event.teikaType,
        modifier = Modifier
            .offset(x = cellSize * gridX, y = cellSize * gridY)
            .size(cellSize * event.teikaType.width, cellSize * event.teikaType.height)
            .graphicsLayer {
                val base = teikaVisualScale(event.teikaType)
                scaleX = base * (1f + abs(struggle) * 0.035f)
                scaleY = base * (1f - abs(struggle) * 0.025f) *
                    (1f - if (event.lethal) raw * 0.18f else 0f)
                rotationZ = struggle * 4.5f
            }
            .alpha(if (event.lethal) 1f - progress.value * 0.35f else 1f)
    )
    Canvas(Modifier.fillMaxSize()) {
        drawPlantFrontGrip(
            center = center,
            radius = cs * event.teikaType.width.coerceAtLeast(1) * 0.48f,
            progress = grip,
            alpha = if (event.lethal) 1f - raw * 0.35f else 1f
        )
    }
}

/**
 * Bioma Jardín: la planta RECIBE DAÑO al romperse una línea mientras arrastra un Teika (aún no lo
 * suelta del todo). El agarre tiembla y enrojece, un destello de impacto + onda de choque parten del
 * punto de sujeción y salpican savia y hojas. La intensidad escala con [event.progress] (cuanto más
 * cerca del rescate, más fuerte el golpe). Da el feedback de "rompe otra línea para liberarlo".
 */
@Composable
fun PlantHurtVFX(event: GameEvent.PlantHurt, cellSize: Dp, onComplete: () -> Unit) {
    val density = LocalDensity.current
    val cs = with(density) { cellSize.toPx() }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        clock.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
        onComplete()
    }
    // Salpicadura determinista por id (savia + hojas).
    val bits = remember(event.id) {
        val h = event.id.hashCode()
        List(12) { i ->
            val s = h + i * 0x9E3779
            val ang = (i * 30f + (s and 0x1F) - 16f) * PI.toFloat() / 180f
            val spd = 0.55f + (s ushr 3 and 0x3F) / 64f
            val leaf = (s ushr 2) and 1 == 0
            Triple(ang, spd, leaf)
        }
    }
    val type = event.teikaType
    val center = Offset(
        (event.root.x + type.width / 2f) * cs,
        (event.root.y + type.height / 2f) * cs
    )
    val gripRadius = cs * type.width.coerceAtLeast(1) * 0.48f
    val sap   = Color(0xFFB6E86A)
    val sapHi = Color(0xFFFFF0A0)
    val leafC = Color(0xFF6FB04A)

    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val intensity = 0.6f + event.progress * 0.4f
        // 1) El tallo que sujeta tiembla (recoil del golpe) y se tensa enrojecido.
        val shudder = sin(t * PI.toFloat() * 7f) * (1f - t) * cs * 0.11f * intensity
        val perp = if (event.edge == PlantEdge.TOP || event.edge == PlantEdge.BOTTOM)
            Offset(cs * 0.34f, 0f) else Offset(0f, cs * 0.34f)
        val whip = if (event.edge == PlantEdge.TOP || event.edge == PlantEdge.BOTTOM)
            Offset(shudder, 0f) else Offset(0f, shudder)
        val tetherA = (1f - t) * 0.9f
        // Solo se rompe la primera raíz; la segunda sigue sujetando al Teika.
        drawPlantTether(event.edge, center - perp + whip, 1f - t, tetherA)
        drawPlantGrip(
            center = center + whip,
            radius = gripRadius,
            progress = (1f - t).coerceIn(0f, 1f),
            pulse = 1f + (1f - t) * 0.12f,
            anger = 0.35f + event.progress * 0.4f,
            alpha = tetherA
        )
        // 2) Destello de impacto en el agarre.
        val flash = (1f - t / 0.30f).coerceIn(0f, 1f)
        if (flash > 0f) {
            drawCircle(sapHi.copy(alpha = flash * 0.70f * intensity), gripRadius * (0.6f + (1f - flash) * 0.7f), center)
            drawCircle(Color.White.copy(alpha = flash * 0.50f), gripRadius * 0.40f, center)
        }
        // 3) Onda de choque verde que se expande.
        val ringT = (t / 0.70f).coerceIn(0f, 1f)
        if (ringT in 0.001f..0.999f) {
            drawCircle(
                sap.copy(alpha = (1f - ringT) * 0.70f),
                gripRadius * (0.5f + ringT * 1.1f),
                center,
                style = Stroke(cs * 0.045f * (1f - ringT))
            )
        }
        // 4) Salpicadura de savia + hojas arrancadas.
        bits.forEach { (ang, spd, leaf) ->
            val dist = spd * cs * t * intensity
            val fall = cs * t * t * 0.5f
            val p = Offset(center.x + cos(ang) * dist, center.y + sin(ang) * dist + fall)
            val a = (1f - t).coerceIn(0f, 1f)
            if (leaf) drawCircle(leafC.copy(alpha = a), cs * 0.06f * (1f - 0.3f * t), p)
            else drawCircle(sap.copy(alpha = a * 0.9f), cs * 0.035f, p)
        }
    }
}

// ── Cristal / Escudo: vuelo suave de pieza bandeja → tablero ────────────────────
// Reconstruye la pieza desde targetCells y reusa PieceView (bloques glossy idénticos
// al tablero). Sigue un arco, crece de tamaño-bandeja a tamaño-tablero y deja una
// estela cristalina cian. El ViewModel estampa la pieza real al aterrizar.
@Composable
fun CrystalFlightVfx(
    event: GameEvent.CrystalFlight,
    boardOffsetPx: Offset,
    rootOffset: Offset,
    cellSizePx: Float,
    cellSize: Dp,
    trayTopLeft: Offset?,
    onComplete: () -> Unit
) {
    val minCell = event.targetCells.minWithOrNull(compareBy({ it.y }, { it.x }))
    if (minCell == null) { LaunchedEffect(event.id) { onComplete() }; return }

    // Reconstruir la pieza para reusar el render glossy de PieceView.
    val piece = remember(event.id) {
        com.cukiti.teikafusion.model.Piece(
            shape = event.targetCells.map {
                com.cukiti.teikafusion.model.Position(it.x - minCell.x, it.y - minCell.y)
            },
            colorType = event.colorType,
            isWildcard = event.isWildcard
        )
    }
    // Origen congelado en el primer frame (la bandeja se reordena al colocar).
    val frozenStart = remember(event.id) { trayTopLeft }

    val p = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        p.animateTo(1f, tween(event.durationMs, easing = FastOutSlowInEasing))
        onComplete()
    }
    val t = p.value

    // Destino y origen en coords root-local (igual convención que las demás capas VFX).
    val endX = boardOffsetPx.x + minCell.x * cellSizePx - rootOffset.x
    val endY = boardOffsetPx.y + minCell.y * cellSizePx - rootOffset.y
    val startX = (frozenStart?.x ?: (boardOffsetPx.x + minCell.x * cellSizePx)) - rootOffset.x
    val startY = (frozenStart?.y ?: (boardOffsetPx.y + (minCell.y + 6) * cellSizePx)) - rootOffset.y

    val arc = cellSizePx * 1.1f
    val curX = startX + (endX - startX) * t
    val curY = startY + (endY - startY) * t - arc * kotlin.math.sin(Math.PI.toFloat() * t)

    // La pieza de bandeja es más pequeña → crece al tamaño del tablero, con bote al aterrizar.
    val startScale = 0.62f
    val landRaw = ((t - 0.80f) / 0.20f).coerceIn(0f, 1f)
    val landPop = 1f + 0.12f * kotlin.math.sin(landRaw * Math.PI.toFloat())
    val scale = (startScale + (1f - startScale) * t) * landPop
    val alpha = if (t > 0.90f) ((1f - t) / 0.10f).coerceIn(0f, 1f) else 1f

    Box(
        modifier = Modifier
            .offset { androidx.compose.ui.unit.IntOffset(curX.toInt(), curY.toInt()) }
            .graphicsLayer {
                this.scaleX = scale
                this.scaleY = scale
                this.alpha = alpha
                // Cristal: la pieza "gira" en vuelo hasta su orientación final (cuartos de giro)
                // → el jugador ve cómo se rota para encajar mejor. Aterriza ya sin giro.
                this.rotationZ = (1f - FastOutSlowInEasing.transform(t)) * 90f * event.rotationTurns
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
            }
    ) {
        // Resplandor detrás de la pieza (Canvas → dibuja primero, debajo). Cristal = cian;
        // clon ninja (Kenji) = AURA índigo + estelas de velocidad + imágenes residuales.
        Canvas(modifier = Modifier.size(cellSize * piece.width, cellSize * piece.height)) {
            val auraColor = if (event.ninja) Color(0xFF7C4DFF) else Color(0xFF9BE8FF)
            if (event.ninja) {
                // Dirección del vuelo (para tirar las estelas HACIA ATRÁS).
                val fdx = endX - startX; val fdy = endY - startY
                val flen = kotlin.math.hypot(fdx, fdy).coerceAtLeast(1f)
                val bx = -fdx / flen; val by = -fdy / flen
                val c = Offset(size.width / 2f, size.height / 2f)
                // Imágenes residuales (afterimages) desvaneciéndose detrás.
                repeat(2) { i ->
                    val back = cellSizePx * (0.7f + 0.7f * i)
                    drawRoundRect(
                        color = auraColor.copy(alpha = (0.22f - 0.09f * i) * (1f - t)),
                        topLeft = Offset(bx * back, by * back),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.2f)
                    )
                }
                // Estelas de velocidad.
                repeat(4) { i ->
                    val off = (i - 1.5f) * size.minDimension * 0.22f
                    val perp = Offset(-by * off, bx * off)
                    drawLine(
                        auraColor.copy(alpha = 0.4f * (1f - t)),
                        Offset(c.x + perp.x, c.y + perp.y),
                        Offset(c.x + perp.x + bx * cellSizePx * 1.6f, c.y + perp.y + by * cellSizePx * 1.6f),
                        strokeWidth = size.minDimension * 0.045f,
                        cap = StrokeCap.Round
                    )
                }
            }
            drawRoundRect(
                brush = Brush.radialGradient(
                    listOf(auraColor.copy(alpha = if (event.ninja) 0.50f else 0.55f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.maxDimension * 0.7f
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.2f)
            )
        }
        PieceView(
            piece        = piece,
            isDragging   = false,
            isQueuePiece = false,
            cellSize     = cellSize,
            onPositioned = { _, _ -> }
        )
    }
}

// ── Combo Text Banner ──────────────────────────────────────────────────────────

/**
 * Palabra de elogio por escalón de combo (índice = tier 1..9; el 0 no se muestra). La usan tanto el
 * banner del tablero ([ComboTextVFX], tiers 1-4) como la celebración a pantalla completa
 * ([EpicComboVFX], tiers ≥ [EPIC_COMBO_TIER]).
 *
 * En ESPAÑOL, como todo el texto que ve el jugador. "TEIKA MAX!" se conserva a propósito: es el
 * clímax de marca, no una palabra traducible.
 *
 * Longitudes acotadas (≤ 12 caracteres, como el peor caso anterior) para no desbordar el ancho del
 * tablero en pantallas estrechas — el tamaño de fuente se deriva del tamaño de celda.
 */
private val comboLabels = listOf(
    "", "¡BIEN!", "¡GENIAL!", "¡ASOMBROSO!", "¡EXCELENTE!", "¡LEGENDARIO!",
    "¡INCREÍBLE!", "¡DIVINO!", "¡IMPARABLE!", "TEIKA MAX!"
)
private val comboColors = listOf(
    Color.White,
    Color(0xFFB2FF59),
    Color(0xFFFFD740),
    Color(0xFFFF6E40),
    Color(0xFF40C4FF),
    Color(0xFFEA80FC),
    Color(0xFFFF1744),
    Color(0xFFFFAB00),
    Color(0xFF00E5FF),
    Color(0xFFFFD700),
)

/**
 * A partir de este tier de combo (LEGENDARY, racha ≥ 6) la celebración deja de ser el banner
 * discreto del tablero y pasa al overlay ÉPICO a pantalla completa ([EpicComboVFX], estilo
 * "salvada épica"), escalando progresivamente hasta TEIKA MAX. Por debajo se usa [ComboTextVFX].
 */
const val EPIC_COMBO_TIER = 5

@Composable
fun ComboTextVFX(
    event: GameEvent.ComboText,
    cellSize: Dp,
    onComplete: () -> Unit
) {
    val tier   = event.tier.coerceIn(1, 9)
    val label  = comboLabels[tier]
    val color  = comboColors[tier]
    val streak = event.streak.coerceAtLeast(2)

    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }

    // Una sola línea de tiempo (0→1) gobierna escala, alpha, subida y burst.
    // Se lee SOLO dentro de graphicsLayer y del Canvas → redibuja sin recomponer
    // (cero recomposiciones por frame durante toda la animación).
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(840, easing = LinearEasing))
        onComplete()
    }

    val burstCount  = (9 + tier * 3).coerceAtMost(26)
    val burstColors = remember(tier) { listOf(color, Color(0xFFFFE066), Color.White, Color(0xFFFFD700)) }
    val burstSeed = remember(event.id) {
        List(burstCount) { i ->
            val ang = (i * (360f / burstCount) + (i * 37 % 23)) * Math.PI.toFloat() / 180f
            Triple(ang, 0.55f + (i % 5) * 0.11f, burstColors[i % burstColors.size])
        }
    }

    // Palabra de elogio (NICE! / GREAT! …) — zona superior, más discreta.
    val praiseSp = (cellSize.value * (0.40f + tier * 0.02f)).coerceIn(16f, 30f)
    // Multiplicador ×N — protagonista, dead-center del tablero ("el botón de combo en el medio").
    // Crece con tier Y con la racha real: combos largos (×10, ×15…) muestran un número cada vez
    // más grande aunque el tier ya esté saturado en 9 → la recompensa sigue escalando visualmente.
    val comboSp  = (cellSize.value * (0.66f + tier * 0.03f + (streak - 2).coerceAtLeast(0) * 0.012f))
        .coerceIn(34f, 72f)

    // Centrado sobre todo el tablero → momento de celebración inmersivo.
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

        // ── Glow radial + anillo de choque + estrellas (drawWithCache: cero alloc/frame) ──
        // Brushes radiales y Path de estrella se construyen UNA vez (al fijarse el tamaño). En el
        // frame solo se leen `p.value` y se modulan opacidad/posición: los brushes usan alpha PLENO
        // horneado + `alpha =` por-draw, y la estrella reutiliza un Path unitario vía withTransform
        // en lugar de reconstruir hasta 26 Paths por frame (era el mayor coste de este VFX).
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val c  = Offset(size.width / 2f, size.height / 2f)
                    val scrimR = cellPx * (2.6f + tier * 0.25f)
                    val scrimBrush = Brush.radialGradient(
                        listOf(Color.Black, Color.Black.copy(alpha = 0.45f), Color.Transparent),
                        center = c, radius = scrimR
                    )
                    val glowR = cellPx * (1.7f + tier * 0.4f)
                    val glowBrush = Brush.radialGradient(
                        listOf(color, color.copy(alpha = 0.35f), Color.Transparent),
                        center = c, radius = glowR
                    )
                    val unitStar = buildUnitStarPath()
                    val reach = cellPx * (1.25f + tier * 0.28f)
                    val srBaseK = cellPx * 0.13f + tier * 0.7f

                    onDrawBehind {
                        val t = (p.value / 0.5f).coerceIn(0f, 1f)
                        val env = sin(t * Math.PI.toFloat())

                        // Spotlight: oscurecido radial sutil que florece y se retira → enfoca la mirada.
                        val scrimA = env * 0.28f
                        if (scrimA > 0.01f) drawCircle(brush = scrimBrush, radius = scrimR, center = c, alpha = scrimA)

                        // Glow radial que florece y se desvanece (foco visual).
                        val glowA = env * 0.5f
                        if (glowA > 0.01f) drawCircle(brush = glowBrush, radius = glowR, center = c, alpha = glowA)

                        // Anillo(s) de choque expansivos.
                        if (t > 0f && t < 1f) {
                            val ringR = cellPx * (1.4f + tier * 0.42f) * t
                            val ringA = (1f - t).coerceIn(0f, 1f)
                            val sw    = (cellPx * 0.11f * (1f - t * 0.6f)).coerceAtLeast(2f)
                            drawCircle(color, ringR, c, alpha = ringA * 0.70f, style = Stroke(width = sw))
                            if (tier >= 3) {
                                drawCircle(Color.White, ringR * 0.66f, c, alpha = ringA * 0.40f,
                                    style = Stroke(width = (sw * 0.5f).coerceAtLeast(1.5f)))
                            }
                        }

                        // Estrellas radiales — Path unitario reutilizado (translate + scale por estrella).
                        if (t > 0f) {
                            val a = (1f - t).coerceIn(0f, 1f)
                            val d0 = reach * t * (1f + 0.22f * env)
                            val sr = srBaseK * (1f - t * 0.4f)
                            burstSeed.forEach { (ang, dist01, col) ->
                                val d  = d0 * dist01
                                val px = c.x + cos(ang) * d
                                val py = c.y + sin(ang) * d
                                withTransform({
                                    translate(px, py)
                                    scale(sr, sr, pivot = Offset.Zero)
                                }) {
                                    drawPath(unitStar, col, alpha = a)
                                    drawCircle(Color.White, radius = 0.22f, center = Offset.Zero, alpha = a * 0.7f)
                                }
                            }
                        }
                    }
                }
        )

        // ── Grupo de texto: palabra de elogio (arriba) + ×N protagonista (centro) ──
        // Ambos comparten una sola línea de tiempo y se apilan en una Column → cada
        // mensaje tiene su zona propia sin solaparse (palabra arriba, ×N debajo).
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val pv = p.value
                // Escala: crecimiento con overshoot → asentado → leve crecida al salir
                val sc = when {
                    pv < 0.16f -> { val g = pv / 0.16f; (g * (2f - g)) * 1.18f }
                    pv < 0.30f -> 1.18f - 0.18f * ((pv - 0.16f) / 0.14f)
                    pv < 0.66f -> 1f
                    else       -> 1f + 0.10f * ((pv - 0.66f) / 0.34f)
                }
                scaleX = sc; scaleY = sc
                rotationZ = if (pv < 0.16f) -6f * (1f - pv / 0.16f) else 0f
                translationY = if (pv < 0.58f) 0f else -cellPx * 0.45f * ((pv - 0.58f) / 0.42f)
                alpha = when {
                    pv < 0.10f -> pv / 0.10f
                    pv < 0.62f -> 1f
                    else       -> (1f - (pv - 0.62f) / 0.38f).coerceIn(0f, 1f)
                }
            }
        ) {
            // Palabra de elogio (zona superior)
            val ps = praiseSp.sp
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label, color = Color.Black.copy(alpha = 0.55f), fontSize = ps,
                    fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, softWrap = false,
                    modifier = Modifier.offset(x = 2.dp, y = 2.5.dp)
                )
                Text(
                    text = label, color = color, fontSize = ps,
                    fontWeight = FontWeight.Black, letterSpacing = 1.sp, maxLines = 1, softWrap = false
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Multiplicador ×N — protagonista, dead-center del tablero
            val cs = comboSp.sp
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "×$streak", color = Color.Black.copy(alpha = 0.55f), fontSize = cs,
                    fontWeight = FontWeight.Black, letterSpacing = (-1).sp, maxLines = 1, softWrap = false,
                    modifier = Modifier.offset(x = 3.dp, y = 3.5.dp)
                )
                Text(
                    text = "×$streak", color = Color.White, fontSize = cs,
                    fontWeight = FontWeight.Black, letterSpacing = (-1).sp, maxLines = 1, softWrap = false
                )
                Text(
                    text = "×$streak", color = color.copy(alpha = 0.9f), fontSize = cs,
                    fontWeight = FontWeight.Black, letterSpacing = (-1).sp, maxLines = 1, softWrap = false,
                    modifier = Modifier.offset(x = (-1).dp, y = (-1).dp)
                )
            }
        }
    }
}

/**
 * Estrella de 5 puntas UNITARIA (radio exterior 1, centrada en el origen), construida una sola vez
 * y reutilizada por frame vía withTransform (translate + scale) — evita reconstruir un Path por
 * estrella y por frame en el bucle de dibujo.
 */
private fun buildUnitStarPath(): Path {
    val inner = 0.42f
    val path  = Path()
    for (i in 0 until 10) {
        val r   = if (i % 2 == 0) 1f else inner
        val ang = -Math.PI.toFloat() / 2f + i * (Math.PI.toFloat() / 5f)
        val x   = r * cos(ang)
        val y   = r * sin(ang)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    return path
}

/** Texto con doble sombra/contorno para máxima legibilidad sobre scrim oscuro. */
@Composable
private fun EpicOutlinedText(text: String, size: androidx.compose.ui.unit.TextUnit, fill: Color, outline: Color) {
    Box(contentAlignment = Alignment.Center) {
        Text(
            text = text, color = outline, fontSize = size, fontWeight = FontWeight.Black,
            letterSpacing = 1.sp, maxLines = 1, softWrap = false,
            modifier = Modifier.offset(x = 3.dp, y = 3.5.dp)
        )
        Text(
            text = text, color = fill, fontSize = size, fontWeight = FontWeight.Black,
            letterSpacing = 1.sp, maxLines = 1, softWrap = false
        )
    }
}

// ── Epic Combo — celebración a pantalla completa para combos altos (tier ≥ EPIC_COMBO_TIER) ──────

/**
 * Versión ÉPICA del banner de combo: flash de impacto, scrim que enfoca (pseudo slow-mo), god-rays que giran y
 * texto gigante (palabra de combo + ×N). La intensidad — nº de rayos, scrim, duración del slow-mo,
 * tamaño del texto — ESCALA PROGRESIVAMENTE con el [tier] (de LEGENDARY a TEIKA MAX).
 *
 * Pantalla completa (se monta en GameScreen, encima de la UI). Una sola Animatable → redibuja sin
 * recomponer. Para tiers por debajo del umbral se sigue usando [ComboTextVFX] a nivel de tablero.
 */
@Composable
fun EpicComboVFX(
    tier: Int,
    streak: Int,
    onComplete: () -> Unit,
    /**
     * **ENCADENADO** (perf, 2026-07-31): este épico llega pisándole los talones al anterior.
     *
     * La celebración cuesta TRES pasadas a pantalla completa por frame —flash blanco, tinte del
     * tier y scrim radial— encima del tablero, que en ese momento está resolviendo su cascada. Una
     * vez se aguanta; encadenadas se notan como tirones. Y además no aportan: el ojo viene de un
     * fogonazo idéntico de hace medio segundo. Cuando encadena, la celebración se queda con lo que
     * de verdad comunica —los rayos y el texto— y suelta las pasadas de pantalla completa.
     */
    chained: Boolean = false,
    /**
     * FOGONAZO BLANCO a pantalla completa al entrar la celebración.
     *
     * WALLBLOCK lo apaga (usuario, 2026-08-08l): allí el combo se alimenta de cascadas automáticas,
     * así que los épicos llegan mucho más seguidos que en niveles y el fogonazo dejaba de ser un
     * golpe para volverse un parpadeo constante. El resto de la celebración —rayos, scrim, texto—
     * se conserva intacta: lo que sobraba era el blanco, no la fiesta.
     */
    whiteFlash: Boolean = true,
    /**
     * **LA BANDA DE MANDOS NO SE APAGA** (2026-08-26c, bug reportado por el usuario: «cuando hago un
     * combo largo el panel de fichas se quita»).
     *
     * Píxeles de la parte de ABAJO de la pantalla que el scrim tiene prohibido tocar: la bandeja.
     *
     * El scrim es un degradado radial `Transparent → negro` centrado en la pantalla, así que en las
     * ORILLAS llega a **negro puro**, y la bandeja vive justo en la orilla de abajo. Con `scrimA`
     * subiendo hasta 0,60 y la bandeja ya atenuada al 80 % por `boardBusy` mientras la cascada
     * resuelve, la bandeja se quedaba en torno al 30 % de su presencia durante ~1,5 s: no se leía
     * como «atenuada», se leía como «se ha ido». Y solo pasaba en combos largos porque solo a partir
     * de [EPIC_COMBO_TIER] entra esta celebración — que es exactamente lo que reportó el usuario.
     *
     * El resto de la fiesta (fogonazo, rayos, glow, texto) sigue siendo a pantalla completa: lo que
     * se recorta es únicamente el APAGÓN. Una celebración no puede apagar los mandos.
     *
     * `0` = comportamiento antiguo (scrim hasta el borde). Lo usan las llamadas que no tienen una
     * bandeja debajo.
     */
    protectedBottomPx: Float = 0f
) {
    val t = tier.coerceIn(EPIC_COMBO_TIER, 9)
    val label = comboLabels[t]
    val accent = comboColors[t]
    val s = streak.coerceAtLeast(2)

    // 0..1 progresivo dentro del rango alto, con base para que incluso el umbral se sienta épico.
    val intensity = (0.4f + 0.6f * ((t - EPIC_COMBO_TIER) / (9f - EPIC_COMBO_TIER))).coerceIn(0.4f, 1f)
    // La racha CRUDA sigue escalando la celebración aunque el tier ya esté saturado en 9: Combo 10,
    // 13, 20… se sienten cada vez MÁS grandes (más rayos, texto mayor), estilo Block Blast.
    val streakBoost = ((s - 6).coerceAtLeast(0) / 12f).coerceIn(0f, 1f)
    val durationMs = (850 + intensity * 650).toInt()

    // Tope de rayos: por encima de ~20 líneas por frame no se distinguen y solo cuestan.
    val rayCount = (12 + (intensity * 12).toInt() + (streakBoost * 10).toInt()).coerceAtMost(20)
    val rays = remember(streak) {
        List(rayCount) { i ->
            val ang = (i * (360f / rayCount) + (i * 41 % 17)) * Math.PI.toFloat() / 180f
            Pair(ang, 0.75f + (i % 4) * 0.13f)
        }
    }

    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(durationMs, easing = LinearEasing))
        onComplete()
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {

        // drawWithCache: los brushes radiales y la geometría se construyen UNA vez (al fijarse el
        // tamaño), no en cada frame. El alpha animado se aplica por-draw (`alpha = …`), así el scrim,
        // los rayos y el glow modulan su opacidad SIN reasignar Brush/List/Color por frame — cero
        // basura en el bucle de dibujo (regla de perf del proyecto).
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val scrimR = size.maxDimension * 0.72f
                    // Stops a alpha PLENO; el alpha animado (scrimA) los escala uniformemente por-draw.
                    val scrimBrush = Brush.radialGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.5f), Color.Black),
                        center = c, radius = scrimR
                    )
                    val glowR = size.minDimension * 0.32f
                    val glowBrush = Brush.radialGradient(
                        listOf(accent, Color.Transparent), center = c, radius = glowR
                    )
                    val rayStroke = size.minDimension * 0.011f
                    val baseLen = size.minDimension * (0.34f + 0.20f * intensity + 0.12f * streakBoost)
                    // Zona protegida del scrim + su difuminado. Se resuelve aqui (con el tamano ya
                    // fijado) y no en el bucle de dibujo: cero calculo por frame.
                    val guardTop = (size.height - protectedBottomPx).coerceIn(0f, size.height)
                    val featherPx = (size.height * 0.045f).coerceAtMost(guardTop)
                    val featherTop = guardTop - featherPx
                    // Costura: en `featherTop` el degradado radial ya esta en su parada exterior
                    // (negro), asi que la banda arranca en negro y se apaga hacia la bandeja. Sin
                    // esto quedaria una linea horizontal dura cruzando la pantalla.
                    val featherBrush = if (protectedBottomPx > 0.5f && featherPx > 0.5f) {
                        Brush.verticalGradient(
                            listOf(Color.Black, Color.Transparent),
                            startY = featherTop,
                            endY = guardTop
                        )
                    } else null

                    onDrawBehind {
                        val pv = p.value

                        // Flash de impacto (más fuerte cuanto mayor el combo). UNA sola pasada:
                        // el tinte del tier va mezclado en el color, no en un segundo drawRect a
                        // pantalla completa. Encadenando, ni eso.
                        val flashA = if (chained || !whiteFlash) 0f
                            else (1f - pv / 0.14f).coerceIn(0f, 1f) * (0.30f + 0.35f * intensity)
                        if (flashA > 0.01f) {
                            drawRect(lerp(Color.White, accent, 0.25f), alpha = flashA)
                        }

                        // Scrim que enfoca (slow-mo): aparece, se mantiene y se retira.
                        val scrimEnv = when {
                            pv < 0.12f -> pv / 0.12f
                            pv < 0.74f -> 1f
                            else       -> (1f - (pv - 0.74f) / 0.26f).coerceIn(0f, 1f)
                        }
                        val scrimA = if (chained) 0f else scrimEnv * (0.30f + 0.30f * intensity)
                        if (scrimA > 0.01f) {
                            if (featherBrush == null) {
                                drawRect(brush = scrimBrush, alpha = scrimA)
                            } else {
                                // El apagon llega hasta donde empiezan los mandos, y muere con un
                                // difuminado corto para que el corte no se vea.
                                clipRect(bottom = featherTop) {
                                    drawRect(brush = scrimBrush, alpha = scrimA)
                                }
                                drawRect(
                                    brush = featherBrush,
                                    topLeft = Offset(0f, featherTop),
                                    size = Size(size.width, featherPx),
                                    alpha = scrimA
                                )
                            }
                        }

                        // God-rays que giran detrás del texto, tintados con el color del tier.
                        val rayEnv = sin(pv.coerceIn(0f, 1f) * Math.PI.toFloat())
                        if (rayEnv > 0.02f) {
                            val spin = pv * 16f * (Math.PI.toFloat() / 180f)
                            val lenK = 0.5f + 0.5f * pv
                            val rayA = rayEnv * (0.18f + 0.18f * intensity)
                            rays.forEach { (ang, len01) ->
                                val a = ang + spin
                                val L = baseLen * len01 * lenK
                                drawLine(
                                    color = accent,
                                    start = c, end = Offset(c.x + cos(a) * L, c.y + sin(a) * L),
                                    strokeWidth = rayStroke,
                                    cap = StrokeCap.Round,
                                    alpha = rayA
                                )
                            }
                            drawCircle(brush = glowBrush, radius = glowR, center = c, alpha = rayEnv * 0.40f)
                        }
                    }
                }
        )

        // Texto: palabra de combo (arriba) + ×N protagonista — entra con overshoot, posa, sale arriba.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                val pv = p.value
                val sc = when {
                    pv < 0.18f -> { val g = pv / 0.18f; (g * (2f - g)) * 1.20f }
                    pv < 0.32f -> 1.20f - 0.20f * ((pv - 0.18f) / 0.14f)
                    pv < 0.72f -> 1f
                    else       -> 1f + 0.20f * ((pv - 0.72f) / 0.28f)
                }
                scaleX = sc; scaleY = sc
                rotationZ = if (pv < 0.18f) -7f * (1f - pv / 0.18f) else 0f
                translationY = if (pv < 0.72f) 0f else -size.height * 0.16f * ((pv - 0.72f) / 0.28f)
                alpha = when {
                    pv < 0.08f -> pv / 0.08f
                    pv < 0.78f -> 1f
                    else       -> (1f - (pv - 0.78f) / 0.22f).coerceIn(0f, 1f)
                }
            }
        ) {
            EpicOutlinedText(label, (26 + intensity * 18 + streakBoost * 8).sp, accent, Color(0xFF06121F))
            Spacer(modifier = Modifier.height(4.dp))
            EpicOutlinedText("×$s", (44 + intensity * 24 + streakBoost * 22).sp, Color.White, accent)
        }
    }
}

// ── Line Cell Shatter — UN solo Canvas para todos los eventos simultáneos ──────
// Antes: N composables con N Animatables → N recomposiciones por frame.
// Ahora: 1 Animatable compartido → 1 recomposición → todo dibujado en 1 Canvas.
// Cada evento registra su startMs al aparecer; los fragmentos se animan
// independientemente dentro del mismo loop de dibujo.

private data class ShatterCube(
    val ang: Float,      // dirección de salida (rad)
    val speed: Float,    // distancia de viaje (en celdas)
    val sizeMul: Float,  // tamaño relativo a la media celda
    val spin: Float,     // giro total (grados sobre su vida)
    val gravity: Float   // caída (en celdas)
)

private const val SHATTER_LIFE_MS = 720f
// TELEGRAPH pre-clear: en el último tramo de la espera (dominó/beat) el bloque se OSCURECE
// progresivamente antes de reventar — anticipación estilo Block Blast. Debe coincidir con
// GameViewModel.LINE_CLEAR_TELEGRAPH_MS para que el clear normal se oscurezca toda su espera.
private const val SHATTER_TELEGRAPH_MS = 170f
private const val SHATTER_TELEGRAPH_MAX_DARKEN = 0.34f

private data class ShatterEventData(
    val absoluteStartMs: Long,
    val seeds: List<ShatterCube>,
    val position: Position,
    val colorType: Int,
    /** Cofre dorado con estrella-objetivo: se dibuja fiel (paleta oro + PNG) hasta reventar. */
    val targetColor: com.cukiti.teikafusion.model.TargetColor? = null,
    /** Clear con combo: glint dorado de contribución sobre la celda durante el telegraph. */
    val bonusSparkle: Boolean = false,
    /** Fogonazo blanco al reventar. WALLBLOCK lo apaga: en cascada lava la pantalla. */
    val whiteFlash: Boolean = true
)

/**
 * Duración del agrietado del cristal. Corto a propósito: ocurre DENTRO de una limpieza de línea,
 * junto a los `LineCellShatter` de las celdas vecinas, y no puede quedarse con la escena.
 */
private const val CRYSTAL_CRACK_MS = 360

/** Duración de la erupción del volcán. Se solapa con el tween de 620 ms de la propia superficie. */
private const val LAVA_SURGE_MS = 620

/** Lo que dura el estallido de una cáscara de cristal (después de su `delayMs`). */
private const val CRYSTAL_SHATTER_MS = 620

/** Una cáscara estallando: cuándo arranca en el reloj del lote, dónde, de qué color y sus esquirlas. */
private class CrystalShatterData(
    val startMs: Float,
    val cx: Float,
    val cy: Float,
    val tint: Color,
    val shards: List<CrystalShard>,
)

/**
 * Bioma Cristal: las cáscaras de vidrio estallan cuando sus bloques se rompen de verdad. Cada una dibuja
 * un destello frío de revelación + esquirlas triangulares translúcidas saliendo con gravedad y giro, y
 * se sincroniza con el [LineCellShatter] de su bloque interior vía [GameEvent.CrystalShatter.delayMs].
 *
 * **P-370 · EN LOTE** (2026-09-30, decisión del usuario): antes cada casilla montaba SU PROPIO
 * `CrystalShatterVFX` — un Canvas a pantalla completa con su reloj—, así que una línea de cristal eran 8
 * capas y 8 relojes a la vez. Ahora, como [BatchLineCellShatterVFX], es UN lienzo con capa propia, UN
 * reloj y un `Path` compartido. Mismo dibujo: cada casilla guarda su arranque y sus esquirlas (misma
 * semilla por id). Único consumidor de [GameEvent.CrystalShatter].
 */
@Composable
fun BatchCrystalShatterVFX(
    events: List<GameEvent.CrystalShatter>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }
    val skin = LocalBlockSkin.current
    // 8 esquirlas por cáscara con dirección/velocidad/giro pseudo-aleatorios derivados del id.
    // P-67: el ADORNO se recorta con «Reducir efectos»; el destello de revelación no (informa).
    val shardCount = fxCount(8)

    // Reloj ÚNICO del lote (ms desde que se montó) y datos de cada cáscara, que sobreviven a que su
    // evento salga de la lista.
    val clock = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shells = remember { mutableMapOf<String, CrystalShatterData>() }
    // Esquirla: UN Path reutilizado (rewind + 3 vértices) y un trazo fijo para todo el lote.
    val tri = remember { Path() }
    val shardEdge = remember(cellPx) { Stroke(width = cellPx * 0.02f) }

    SideEffect {
        val nowMs = clock.value
        events.forEach { event ->
            if (event.id in shells) return@forEach
            val h = event.id.hashCode()
            shells[event.id] = CrystalShatterData(
                startMs = nowMs + event.delayMs,
                cx = (event.position.x + 0.5f) * cellPx,
                cy = (event.position.y + 0.5f) * cellPx,
                tint = skin.colorsFor(event.colorType.coerceAtLeast(0))[1],
                shards = List(shardCount) { i ->
                    val s     = h + i * 0x9E3779
                    val ang   = (i * 45f + (s and 0x1F) - 16f) * kotlin.math.PI.toFloat() / 180f
                    val speed = 0.55f + (s ushr 3 and 0x3F) / 90f
                    val sz    = 0.16f + (s ushr 6 and 0x1F) / 150f
                    val spin  = (if ((s ushr 2) and 1 == 0) 1f else -1f) * (220f + (s ushr 4 and 0x7F))
                    val grav  = 1.0f + (s ushr 9 and 0x1F) / 28f
                    CrystalShard(ang, speed, sz, spin, grav)
                },
            )
            val id = event.id
            scope.launch {
                delay((event.delayMs + CRYSTAL_SHATTER_MS).toLong())
                // Mismo hilo (Main) que el dibujo → purga segura entre fotogramas.
                shells.remove(id)
                onEventComplete(id)
            }
        }
    }

    LaunchedEffect(Unit) {
        clock.animateTo(100_000f, tween(100_000, easing = LinearEasing))
    }

    // Capa propia: se redibuja cada fotograma mientras haya cáscaras estallando.
    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
        val nowMs = clock.value
        val glassEdge = Color(0xFFBFEFFF)
        shells.values.forEach { d ->
            val t = (nowMs - d.startMs) / CRYSTAL_SHATTER_MS
            if (t <= 0f || t >= 1f) return@forEach
            val cx = d.cx; val cy = d.cy
            val center = Offset(cx, cy)

            // Destello frío de revelación al instante de romperse.
            val flash = (1f - t / 0.30f).coerceIn(0f, 1f)
            if (flash > 0f) {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.85f * flash), glassEdge.copy(alpha = 0.35f * flash), Color.Transparent),
                        center = center, radius = cellPx * 0.9f
                    ),
                    radius = cellPx * 0.9f, center = center
                )
            }

            val alpha = (1f - t).coerceIn(0f, 1f)
            d.shards.forEach { sh ->
                val dist = sh.speed * cellPx * t
                val fall = sh.gravity * cellPx * t * t * 0.7f
                val px = cx + kotlin.math.cos(sh.ang) * dist
                val py = cy + kotlin.math.sin(sh.ang) * dist + fall
                val half = sh.size * cellPx * (1f - 0.25f * t)
                withTransform({ rotate(sh.spin * t, Offset(px, py)) }) {
                    tri.rewind()
                    tri.moveTo(px, py - half)
                    tri.lineTo(px + half * 0.9f, py + half)
                    tri.lineTo(px - half * 0.9f, py + half)
                    tri.close()
                    drawPath(tri, color = glassEdge.copy(alpha = 0.30f * alpha), blendMode = BlendMode.Screen)
                    drawPath(tri, color = d.tint.copy(alpha = 0.18f * alpha), blendMode = BlendMode.Screen)
                    drawPath(tri, color = Color.White.copy(alpha = 0.55f * alpha), style = shardEdge)
                }
            }
        }
    }
}

private data class CrystalShard(
    val ang: Float,
    val speed: Float,
    val size: Float,
    val spin: Float,
    val gravity: Float
)

/** Una fisura del agrietado: ángulo de salida, quiebro a mitad de camino y longitud final. */
private data class CrystalFissure(
    val ang: Float,
    val kink: Float,
    val length: Float,
    val width: Float
)

/**
 * **Bioma Cristal: el bloque SE AGRIETA** (P-64, auditoría de eventos de bioma 2026-08-30).
 *
 * `CrystalCrack` era uno de los dos únicos eventos del juego **sin composable propio**: la armadura
 * bajaba de 2 a 1 y lo único que ocurría era que la celda cambiaba de sprite, con una vibración y un
 * sonido. Y es el bioma **1** del juego — la primera impresión de todo el sistema de biomas era su
 * mecánica ejecutándose en silencio visual.
 *
 * Dura ~360 ms (tope de bioma: `BIOME_EVENT_MAX_MS`), porque esto pasa **dentro de una limpieza de
 * línea**: compite con el `LineCellShatter` de sus vecinas y no puede robarles la escena.
 *
 * [GameEvent.CrystalCrack.remainingArmor] manda en la lectura: con 1 (el próximo golpe la parte) la
 * red de grietas es más densa y más blanca — la celda **avisa de que está a punto**.
 */
@Composable
fun CrystalCrackVFX(
    event: GameEvent.CrystalCrack,
    cellSize: Dp,
    onComplete: () -> Unit
) {
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    // Última capa de armadura → más fisuras y más largas: la celda se lee "a punto de romperse".
    val critical = event.remainingArmor <= 1
    val fissureCount = if (critical) 7 else 5

    val fissures = remember(event.id) {
        val h = event.id.hashCode()
        List(fissureCount) { i ->
            val s = h + i * 0x9E3779
            CrystalFissure(
                ang = (i * (360f / fissureCount) + (s and 0x1F) - 16f) * kotlin.math.PI.toFloat() / 180f,
                kink = ((s ushr 5 and 0x1F) - 16f) * kotlin.math.PI.toFloat() / 180f,
                length = (if (critical) 0.46f else 0.34f) + (s ushr 3 and 0x1F) / 220f,
                width = 0.020f + (s ushr 8 and 0x0F) / 900f
            )
        }
    }
    // Cuatro esquirlas que saltan del golpe (el estallido de verdad tiene ocho — este es menor).
    // P-67: las esquirlas son adorno. Las GRIETAS no se tocan — son lo que enseña «aguantó, pero
    // el siguiente golpe la parte», que es justo la información del bioma.
    val chipCount = fxCount(4)
    val chips = remember(event.id, chipCount) {
        val h = event.id.hashCode() * 31
        List(chipCount) { i ->
            val s = h + i * 0x85EBCA
            CrystalShard(
                ang = (i * 90f + (s and 0x3F) - 32f) * kotlin.math.PI.toFloat() / 180f,
                speed = 0.30f + (s ushr 3 and 0x1F) / 130f,
                size = 0.07f + (s ushr 6 and 0x0F) / 240f,
                spin = (if ((s ushr 2) and 1 == 0) 1f else -1f) * (180f + (s ushr 4 and 0x7F)),
                gravity = 0.8f + (s ushr 9 and 0x1F) / 40f
            )
        }
    }

    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        if (event.delayMs > 0) delay(event.delayMs.toLong())
        clock.animateTo(1f, tween(CRYSTAL_CRACK_MS, easing = LinearEasing))
        onComplete()
    }

    val cx = (event.position.x + 0.5f) * cellPx
    val cy = (event.position.y + 0.5f) * cellPx
    val glassEdge = Color(0xFFBFEFFF)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.value
        if (t <= 0f || t >= 1f) return@Canvas
        val center = Offset(cx, cy)

        // ── 1. El GOLPE: destello frío corto y seco (primer 22%). ──
        val hit = (1f - t / 0.22f).coerceIn(0f, 1f)
        if (hit > 0f) {
            val r = cellPx * (0.34f + 0.34f * (1f - hit))
            drawCircle(Color.White.copy(alpha = 0.55f * hit), r * 0.45f, center)
            drawCircle(glassEdge.copy(alpha = 0.60f * hit), r, center, style = Stroke(width = cellPx * 0.05f * hit))
        }

        // (El «aguantó» —squash + rebote elástico de la celda— ya lo hace CellView reaccionando al
        //  cambio de `content.armor`. No se repite aquí: dos señales para el mismo instante se leen
        //  peor que una, y este VFX está para lo que faltaba, que son las GRIETAS.)

        // ── 2. Las GRIETAS se PROPAGAN desde el impacto y se quedan (se apagan al final). ──
        //    Cada fisura son dos tramos con un quiebro: una recta pura no se lee como vidrio roto.
        val grow = (t / 0.34f).coerceIn(0f, 1f)
        val growE = 1f - (1f - grow) * (1f - grow)          // sale rápido y frena (astilla)
        val fade = 1f - ((t - 0.62f) / 0.38f).coerceIn(0f, 1f)
        if (fade > 0.01f) {
            fissures.forEach { f ->
                val len = f.length * cellPx * growE
                val midLen = len * 0.55f
                val mid = Offset(
                    cx + kotlin.math.cos(f.ang) * midLen,
                    cy + kotlin.math.sin(f.ang) * midLen
                )
                val end = Offset(
                    mid.x + kotlin.math.cos(f.ang + f.kink) * (len - midLen),
                    mid.y + kotlin.math.sin(f.ang + f.kink) * (len - midLen)
                )
                val w = f.width * cellPx
                // Halo ancho + filo blanco: da grosor sin pintar dos veces la misma línea plana.
                drawLine(glassEdge.copy(alpha = 0.35f * fade), center, mid, strokeWidth = w * 2.2f, cap = StrokeCap.Round)
                drawLine(glassEdge.copy(alpha = 0.35f * fade), mid, end, strokeWidth = w * 1.8f, cap = StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.80f * fade), center, mid, strokeWidth = w, cap = StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.70f * fade), mid, end, strokeWidth = w * 0.8f, cap = StrokeCap.Round)
            }
        }

        // ── 3. Cuatro ESQUIRLAS que saltan del punto de impacto. ──
        val chipAlpha = (1f - t).coerceIn(0f, 1f)
        chips.forEach { c ->
            val dist = c.speed * cellPx * t
            val fall = c.gravity * cellPx * t * t * 0.8f
            val px = cx + kotlin.math.cos(c.ang) * dist
            val py = cy + kotlin.math.sin(c.ang) * dist + fall
            val half = c.size * cellPx * (1f - 0.3f * t)
            withTransform({ rotate(c.spin * t, Offset(px, py)) }) {
                drawRect(
                    Color.White.copy(alpha = 0.65f * chipAlpha),
                    topLeft = Offset(px - half, py - half),
                    size = Size(half * 2f, half * 2f)
                )
            }
        }
    }
}

private data class TeikaPushShard(
    val angleOffset: Float,
    val speed: Float,
    val size: Float,
    val spin: Float
)

/**
 * Rotura causada por falta real de espacio físico. Micro-historia: compresión breve en la dirección
 * del empuje → vidrio/madera ceden juntos → fragmentos continúan el vector y el Teika ocupa el
 * hueco. Se monta después de la capa de cuerpos, para que el cuerpo no tape justo el impacto.
 */
@Composable
fun TeikaPushBreakVFX(
    event: GameEvent.TeikaPushBreak,
    cellSize: Dp,
    reducedFx: Boolean,
    onComplete: () -> Unit
) {
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val skin = LocalBlockSkin.current
    val isCrystal = event.crystalArmor > 0
    val isWildcard = event.colorType == WILDCARD_COLOR
    val isGoldChest = event.targetColor != null && !isCrystal && !isWildcard
    val palette = woodPalette(skin, event.colorType, isGoldChest)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val starBitmaps = remember(ctx) { targetStarBitmaps(ctx) }
    val shards = remember(event.id) {
        val h = event.id.hashCode()
        val offsets = floatArrayOf(-68f, -40f, -16f, 12f, 38f, 66f)
        offsets.mapIndexed { index, offset ->
            val seed = h + index * 0x9E3779
            TeikaPushShard(
                angleOffset = offset + ((seed ushr 4) and 0x0F) - 7f,
                speed = 0.62f + ((seed ushr 7) and 0x1F) / 70f,
                size = 0.12f + ((seed ushr 11) and 0x0F) / 110f,
                spin = (if (seed and 1 == 0) 1f else -1f) * (90f + ((seed ushr 2) and 0x7F))
            )
        }
    }

    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id, reducedFx) {
        clock.animateTo(
            1f,
            tween(if (reducedFx) 140 else 280, easing = LinearEasing)
        )
        onComplete()
    }

    Canvas(Modifier.fillMaxSize()) {
        val t = clock.value.coerceIn(0f, 1f)
        if (t >= 1f) return@Canvas
        val center = Offset(
            (event.position.x + 0.5f) * cellPx,
            (event.position.y + 0.5f) * cellPx
        )
        val full = Size(cellPx, cellPx)
        val topLeft = Offset(center.x - cellPx * 0.5f, center.y - cellPx * 0.5f)
        val dirLen = hypot(event.pushX, event.pushY)
        val dirX = if (dirLen > 1e-4f) event.pushX / dirLen else 0f
        val dirY = if (dirLen > 1e-4f) event.pushY / dirLen else -1f
        val baseAngle = atan2(dirY, dirX) * 180f / PI.toFloat()

        // Vidrio: respuesta frágil, sin rebote. Solo 45–55 ms de compresión antes de ceder.
        val impactT = (t / 0.20f).coerceIn(0f, 1f)
        val blockAlpha = if (t < 0.24f) 1f - (t / 0.24f).coerceIn(0f, 1f) else 0f
        if (blockAlpha > 0f) {
            val compression = 0.10f * impactT
            val sideStretch = 0.035f * impactT
            val scaleX = 1f - compression * abs(dirX) + sideStretch * abs(dirY)
            val scaleY = 1f - compression * abs(dirY) + sideStretch * abs(dirX)
            withTransform({ scale(scaleX, scaleY, center) }) {
                if (isWildcard) {
                    drawWildcardTile(topLeft, full, skin.cornerFraction, blockAlpha)
                } else {
                    drawStyledTile(
                        skin.style,
                        topLeft,
                        full,
                        palette[0],
                        palette[1],
                        palette[2],
                        skin.cornerFraction,
                        blockAlpha
                    )
                }
                event.targetColor?.let { target ->
                    starBitmaps[target]?.let { bitmap ->
                        val starW = cellPx * skin.starScale
                        val starH = starW * bitmap.height.toFloat() / bitmap.width.toFloat()
                        val starCy = center.y + cellPx * skin.starOffsetYFraction
                        drawImage(
                            image = bitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = androidx.compose.ui.unit.IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(
                                (center.x - starW / 2f).roundToInt(),
                                (starCy - starH / 2f).roundToInt()
                            ),
                            dstSize = androidx.compose.ui.unit.IntSize(starW.roundToInt(), starH.roundToInt()),
                            alpha = blockAlpha
                        )
                    }
                }
                if (isCrystal) drawCrystalShell(topLeft, full)
            }
        }

        // Impacto localizado: nada de fogonazo blanco de pantalla completa sobre el tablero oscuro.
        val flashT = (t / 0.28f).coerceIn(0f, 1f)
        val flashAlpha = (1f - flashT) * if (reducedFx) 0.28f else 0.52f
        if (flashAlpha > 0f) {
            val accent = if (isCrystal) Color(0xFFBFEFFF) else palette[1]
            drawCircle(
                color = accent.copy(alpha = flashAlpha),
                radius = cellPx * (0.40f + 0.45f * flashT),
                center = center,
                style = Stroke(width = cellPx * 0.055f * (1f - flashT * 0.5f))
            )
        }

        val shardT = ((t - 0.12f) / 0.88f).coerceIn(0f, 1f)
        if (shardT <= 0f) return@Canvas
        // ease-out-cubic: salida decisiva y frenado legible; nunca movimiento espacial lineal.
        val moveT = 1f - (1f - shardT) * (1f - shardT) * (1f - shardT)
        val alpha = (1f - shardT).coerceIn(0f, 1f)
        val travelMul = if (reducedFx) 0.18f else 1f
        shards.forEachIndexed { index, shard ->
            val angle = (baseAngle + shard.angleOffset) * PI.toFloat() / 180f
            val distance = shard.speed * cellPx * moveT * travelMul
            val x = center.x + cos(angle) * distance
            val y = center.y + sin(angle) * distance + if (reducedFx) 0f else cellPx * 0.12f * shardT * shardT
            val half = shard.size * cellPx * (1f - shardT * 0.28f)
            withTransform({ rotate(shard.spin * moveT, Offset(x, y)) }) {
                if (isCrystal && index % 2 == 0) {
                    val triangle = Path().apply {
                        moveTo(x, y - half)
                        lineTo(x + half * 0.82f, y + half)
                        lineTo(x - half * 0.62f, y + half * 0.72f)
                        close()
                    }
                    drawPath(triangle, Color(0x66BFEFFF).copy(alpha = alpha))
                    drawPath(
                        triangle,
                        Color.White.copy(alpha = alpha * 0.65f),
                        style = Stroke(width = cellPx * 0.018f)
                    )
                } else {
                    val color = palette[index % palette.size]
                    drawRoundRect(
                        color = color.copy(alpha = alpha),
                        topLeft = Offset(x - half, y - half),
                        size = Size(half * 2f, half * 2f),
                        cornerRadius = CornerRadius(half * 0.22f, half * 0.22f)
                    )
                }
            }
        }
    }
}

/**
 * Bioma Miedoso: dibuja un bloque escabulléndose de [from] a [to] (1 celda) con squash en la
 * dirección del movimiento + polvo en el origen. El grid ya lo movió y lo oculta en su destino
 * (hiddenWoodCells) hasta que esta animación termina.
 */
@Composable
fun BlockFleeVFX(
    event: GameEvent.BlockFlee,
    cellSize: Dp,
    onComplete: () -> Unit
) {
    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }
    val skin    = LocalBlockSkin.current
    // La celda con estrella/objetivo se pinta como bloque-COFRE dorado en reposo (ver CellView). El
    // vuelo debe usar ESA MISMA paleta dorada; antes volaba con su colorType crudo y "cambiaba de
    // color" a mitad de huida, volviendo al dorado al aterrizar. Comodín conserva su look propio.
    val isWildcard  = event.colorType == WILDCARD_COLOR
    val isGoldChest = event.targetColor != null && !isWildcard
    val palette = woodPalette(skin, event.colorType, isGoldChest)
    // Estrella de objetivo (mismo PNG que la celda) y veneno VIAJAN con el bloque que huye — antes
    // desaparecían durante los ~280 ms del vuelo y reaparecían al aterrizar.
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val targetStarBitmaps = remember(ctx) { targetStarBitmaps(ctx) }

    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        if (event.delayMs > 0) delay(event.delayMs.toLong())
        clock.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        onComplete()
    }

    val fromX = event.from.x * cellPx; val fromY = event.from.y * cellPx
    val toX   = event.to.x * cellPx;   val toY   = event.to.y * cellPx
    val dirX  = (event.to.x - event.from.x).toFloat()
    val dirY  = (event.to.y - event.from.y).toFloat()

    // La cápsula del bloque-Teika viaja con el bloque (la capa de cápsulas no la pinta mientras vuela).
    val seedGeo = remember(cellPx) { SeedGeometry(cellPx) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Al terminar (t=1) se SIGUE dibujando el bloque en reposo hasta que el evento se consuma:
        // la celda real se revela en onComplete y ambos dibujos son idénticos → sin hueco de frames
        // entre el fin del vuelo y el revelado (el parpadeo de Timoteo venía de ese hueco).
        val t = clock.value.coerceAtMost(1f)
        // overshoot suave hacia el destino
        val ease = t * t * (3f - 2f * t)
        val x = fromX + (toX - fromX) * ease
        val y = fromY + (toY - fromY) * ease
        val tl = Offset(x, y)
        val full = Size(cellPx, cellPx)

        // Polvo en el origen (se desvanece).
        val dustA = (1f - t / 0.6f).coerceIn(0f, 1f)
        if (dustA > 0f) {
            val oc = Offset(fromX + cellPx / 2f, fromY + cellPx / 2f)
            repeat(4) { i ->
                val a = i * 90f * kotlin.math.PI.toFloat() / 180f
                drawCircle(
                    Color(0x66B8C4D8).copy(alpha = 0.4f * dustA),
                    radius = cellPx * 0.10f,
                    center = Offset(oc.x + kotlin.math.cos(a) * cellPx * 0.22f, oc.y + kotlin.math.sin(a) * cellPx * 0.22f)
                )
            }
        }

        // Squash en la dirección del movimiento (se estira al correr).
        val center = Offset(x + cellPx / 2f, y + cellPx / 2f)
        val stretch = 1f + 0.18f * kotlin.math.sin(t * kotlin.math.PI.toFloat())
        val sx = if (kotlin.math.abs(dirX) > kotlin.math.abs(dirY)) stretch else 2f - stretch
        val sy = if (kotlin.math.abs(dirY) >= kotlin.math.abs(dirX)) stretch else 2f - stretch
        withTransform({ scale(sx, sy, center) }) {
            drawStyledTile(skin.style, tl, full, palette[0], palette[1], palette[2], skin.cornerFraction,
                variant = event.wood?.let { tileVariantOf(it.id) } ?: 0)
            // El marco del TESTARUDO (P-71) viaja con él.
            event.wood?.takeIf { it.stubborn }?.let { drawStubbornFrame(tl, full, skin.colorsFor(it.colorType).getOrElse(2) { Color.Black }) }
            if (event.poisoned) drawPoisonLayer(tl, full)
            // Bioma Cristal: la carcasa VIAJA con el bloque (antes el cristal se volvía madera
            // lisa durante el vuelo y reaparecía al aterrizar).
            // La ROCA del meteorito (P-294) es blindaje con otra cara: viaja como roca, no como cristal.
            if (event.armor > 0) { if (event.wood?.rock == true) drawSpaceRock(tl, full) else drawCrystalShell(tl, full) }
            event.targetColor?.let { tc ->
                val bmp = targetStarBitmaps[tc] ?: return@let
                // Resplandor interior tras la gema (paridad con el cofre dorado en reposo).
                if (isGoldChest) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xD9FFF1B8), Color(0x33FFDF8E), Color(0x00FFDF8E)),
                            center = center, radius = cellPx * 0.6f
                        ),
                        radius = cellPx * 0.6f, center = center
                    )
                }
                val starW = cellPx * skin.starScale
                val starH = starW * bmp.height.toFloat() / bmp.width.toFloat()
                val starCy = center.y + cellPx * skin.starOffsetYFraction
                drawImage(
                    image = bmp,
                    srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                    srcSize = androidx.compose.ui.unit.IntSize(bmp.width, bmp.height),
                    dstOffset = androidx.compose.ui.unit.IntOffset(
                        (center.x - starW / 2f).roundToInt(), (starCy - starH / 2f).roundToInt()
                    ),
                    dstSize = androidx.compose.ui.unit.IntSize(starW.roundToInt(), starH.roundToInt())
                )
            }
        }
        if (event.wood?.teikaSeed == true) {
            translate(left = center.x, top = center.y) { drawSeed(seedGeo, cellPx, biomeClockSec.floatValue, live = true) }
        }
    }
}

/**
 * Bioma Miedoso: un bloque acorralado tiembla en su sitio (no pudo huir). Dibuja un contorno
 * nervioso que vibra + un par de marcas, SIN repintar el bloque (la celda real sigue visible).
 */
@Composable
fun BlockTrembleVFX(
    event: GameEvent.BlockTremble,
    cellSize: Dp,
    onComplete: () -> Unit
) {
    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        clock.animateTo(1f, tween(300, easing = LinearEasing))
        onComplete()
    }
    val baseX = event.position.x * cellPx
    val baseY = event.position.y * cellPx
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val alpha = (1f - t).coerceIn(0f, 1f)
        val jitter = kotlin.math.sin(t * 34f) * cellPx * 0.06f * alpha
        val tl = Offset(baseX + jitter, baseY)
        val corner = CornerRadius(cellPx * 0.18f, cellPx * 0.18f)
        drawRoundRect(
            Color(0xFFFFE08A).copy(alpha = 0.7f * alpha),
            topLeft = tl, size = Size(cellPx, cellPx),
            cornerRadius = corner,
            style = Stroke(width = cellPx * 0.05f)
        )
        // Pequeñas marcas nerviosas a los lados.
        val mark = Color(0xFFFFF2C0).copy(alpha = 0.6f * alpha)
        drawLine(mark, Offset(baseX - cellPx * 0.06f, baseY + cellPx * 0.3f), Offset(baseX - cellPx * 0.16f, baseY + cellPx * 0.3f), strokeWidth = cellPx * 0.04f)
        drawLine(mark, Offset(baseX + cellPx * 1.06f, baseY + cellPx * 0.5f), Offset(baseX + cellPx * 1.16f, baseY + cellPx * 0.5f), strokeWidth = cellPx * 0.04f)
    }
}


// ── M-02 · SHEEN de los bloques — RETIRADO 2026-07-31b (decisión del usuario) ────────────────
// Aquí vivía `BlockSheenSweep`: el brillo "de caramelo" que cruzaba los bloques quietos cada 7 s,
// traído de la referencia de mercado (Block Blast). Estaba bien construido —un solo Canvas, bucle
// dormido entre pasadas, silueta cacheada— pero seguía siendo ADORNO PURO: no enseñaba ninguna
// regla, y por eso ya era lo primero que apagaba «reducir efectos» (P-12). El usuario lo retiró en
// la pasada de rendimiento de la pantalla de juego. No readoptar sin decisión suya.

/**
 * Bioma Glaciar: bloque de HIELO encerrando un Teika congelado por la brisa. El [modifier] lo
 * dimensiona a la huella del Teika (lo coloca GameBoard). Velo cian + facetas cristalinas + borde
 * de escarcha + brillo pulsante.
 *
 * M-03 (2026-07-30): el hielo **se agrieta turno a turno**. Antes no decía cuánto le quedaba al
 * deshielo y el jugador no podía planear alrededor de él. Se resolvió con GRIETAS en vez de un
 * número: [turnsLeft] frente a [turnsTotal] decide cuántas se dibujan, el velo pierde cuerpo y la
 * escarcha se apaga — al último turno el bloque se ve claramente a punto de romperse. Sin cifras
 * sobre el tablero (misma línea que el resto del juego).
 */
@Composable
fun FrozenTeikaOverlay(
    modifier: Modifier = Modifier,
    turnsLeft: Int = 0,
    turnsTotal: Int = 0
) {
    val transition = rememberInfiniteTransition(label = "teikaIce")
    val shimmer by transition.animateFloat(
        initialValue = 0.4f, targetValue = 0.72f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "sh"
    )
    // 0 = recién congelado (hielo entero) · 1 = último turno (a punto de estallar).
    val wear = if (turnsTotal <= 1 || turnsLeft <= 0) 0f
               else ((turnsTotal - turnsLeft).toFloat() / (turnsTotal - 1)).coerceIn(0f, 1f)
    // Cacheado por tamaño y desgaste (auditoría de fluidez 2026-09-29): el brillo late cada fotograma
    // durante los turnos que dura el hielo, y antes cada fotograma rehacía 3 Path, un degradado, cinco
    // trazos y una lista. Ahora solo el brillo se lee al dibujar; la geometría se construye una vez.
    Spacer(modifier = modifier.drawWithCache {
        val w = size.width; val h = size.height
        val sw = size.minDimension * 0.03f
        val cr = CornerRadius(w * 0.12f, h * 0.12f)
        val veil = Brush.linearGradient(listOf(Color(0x884FC8FF), Color(0x553A8FD0), Color(0x886FD0FF)))
        val facets = Path().apply {
            moveTo(w * 0.3f, 0f); lineTo(w * 0.5f, h * 0.45f); lineTo(w * 0.35f, h)
            moveTo(w * 0.72f, 0f); lineTo(w * 0.55f, h * 0.5f); lineTo(w * 0.82f, h)
            moveTo(0f, h * 0.35f); lineTo(w * 0.5f, h * 0.5f); lineTo(w, h * 0.42f)
        }
        val facetStroke = Stroke(width = sw * 0.7f, cap = StrokeCap.Round)
        val rimStroke = Stroke(width = sw)
        val corners = arrayOf(Offset(0f, 0f), Offset(w, 0f), Offset(0f, h), Offset(w, h))
        // NIEVE ACUMULADA ARRIBA: un montículo blanco festoneado sobre el borde superior (como nieve
        // posada encima del Teika congelado). Curvas suaves entre 3 lóbulos → look de nevada.
        val snow = Path().apply {
            moveTo(0f, h * 0.16f)
            quadraticTo(w * 0.16f, h * 0.02f, w * 0.34f, h * 0.12f)
            quadraticTo(w * 0.5f, h * 0.22f, w * 0.66f, h * 0.10f)
            quadraticTo(w * 0.84f, h * 0.0f, w, h * 0.15f)
            lineTo(w, 0f); lineTo(0f, 0f); close()
        }
        val snowStroke = Stroke(width = sw * 0.5f)
        // ── M-03: GRIETAS del deshielo ────────────────────────────────────────────────────────
        // Cada turno que pasa abre una fractura más. No es decoración: es el contador del deshielo
        // dicho en el idioma del tablero. Trazos fijos (sin animación propia: coste 0 por frame).
        val crack = if (wear > 0.01f) Path().apply {
            // Fractura principal: nace arriba y baja quebrada hasta el centro.
            moveTo(w * 0.46f, h * 0.06f)
            lineTo(w * 0.54f, h * 0.30f)
            lineTo(w * 0.42f, h * 0.48f)
            lineTo(w * 0.52f, h * 0.66f)
            if (wear > 0.49f) {
                // Segunda fractura + ramas: el bloque ya está cediendo.
                moveTo(w * 0.20f, h * 0.30f); lineTo(w * 0.40f, h * 0.44f)
                moveTo(w * 0.80f, h * 0.24f); lineTo(w * 0.58f, h * 0.40f)
                moveTo(w * 0.52f, h * 0.66f); lineTo(w * 0.70f, h * 0.88f)
                moveTo(w * 0.42f, h * 0.48f); lineTo(w * 0.24f, h * 0.74f)
            }
        } else null
        val crackStroke = Stroke(width = sw * (0.55f + 0.35f * wear), cap = StrokeCap.Round)
        val crackCol = Color.White.copy(alpha = 0.55f + 0.35f * wear)
        onDrawBehind {
            val sh = shimmer
            // Velo de hielo translúcido.
            drawRoundRect(brush = veil, cornerRadius = cr)
            // Facetas cristalinas cruzando el hielo.
            drawPath(facets, Color(0xFFCDEBFF).copy(alpha = sh), style = facetStroke)
            // Borde de escarcha.
            drawRoundRect(Color(0xFFEAF6FF).copy(alpha = sh * 0.85f), cornerRadius = cr, style = rimStroke)
            // Escarcha en esquinas + destello central.
            for (c in corners) drawCircle(Color(0xFFEAF6FF).copy(alpha = sh * 0.6f), size.minDimension * 0.08f, c)
            drawCircle(Color.White.copy(alpha = sh * 0.45f), size.minDimension * 0.05f, Offset(w * 0.5f, h * 0.4f))
            drawPath(snow, Color.White.copy(alpha = 0.9f * (1f - wear * 0.45f)))
            drawPath(snow, Color(0xFFDCEFFF).copy(alpha = sh * 0.5f), style = snowStroke)
            if (crack != null) drawPath(crack, crackCol, style = crackStroke)
        }
    })
}


/** Lo que tarda la ráfaga en cruzar el tablero de lado a lado. */
const val GLACIER_WAVE_MS = 1150

/**
 * **Instante del CONGELADO** dentro de la ráfaga (P-06, 2026-07-31): el sonido de escarcha se
 * disparaba al EMITIR el evento, o sea cuando el frente aún estaba fuera del tablero por la
 * izquierda; el hielo se cerraba hasta un segundo después y en silencio. Esto devuelve los ms que
 * hay que esperar para que el "clac" caiga en el frame en que el frente pasa por el Teika — mismo
 * patrón que `NINJA_CUT_IMPACT_MS`.
 *
 * La geometría es la del propio VFX y vive aquí para que no pueda desincronizarse: el frente
 * interpola de `-bandW*0.6` a `board + bandW*1.3`, con `bandW = board*0.42`, todo en CELDAS.
 *
 * @param targetCellX columna de la esquina sup-izq del cuerpo congelado.
 * @param level       tamaño del Teika en celdas (su centro cae medio cuerpo a la derecha).
 */
fun glacierImpactMs(targetCellX: Int, level: Int): Long {
    val board = GameState.GRID_SIZE.toFloat()
    val bandW = board * 0.42f
    val from = -bandW * 0.6f
    val to = board + bandW * 1.3f
    val centerX = targetCellX + level / 2f
    val frac = ((centerX - from) / (to - from)).coerceIn(0f, 1f)
    return (frac * GLACIER_WAVE_MS).toLong()
}

/**
 * Glaciar (rework): BRISA DE ESCARCHA. Una verdadera ráfaga de nieve congelante barre el tablero de
 * izquierda a derecha: velo frío que respira, estela ya helada, líneas de viento (motion streaks),
 * una NUBE de copos arremolinados arrastrados por la ráfaga (con turbulencia) y un frente brillante.
 * Al cruzar el frente por el Teika objetivo estalla un anillo de cristales (el bloque de hielo
 * persistente lo toma FrozenBodiesOverlay). Un solo Canvas + Animatable; se autoconsume al terminar.
 */
@Composable
fun GlacierColdWaveVFX(event: GameEvent.GlacierColdWave, cellSize: Dp, onComplete: () -> Unit) {
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(GLACIER_WAVE_MS, easing = LinearEasing))
        onComplete()
    }
    val boardPx = cellPx * GameState.GRID_SIZE
    val target = Offset(
        (event.targetCell.x + event.level / 2f) * cellPx,
        (event.targetCell.y + event.level / 2f) * cellPx
    )
    // Copos precomputados (0 allocs/frame): [yFrac, trail(0..1 → retraso tras el frente), tam,
    // freqWobble, faseWobble, deriva vertical, largoEstela].
    val flakes = remember(event.id) {
        val r = kotlin.random.Random(event.id.hashCode())
        List(44) {
            floatArrayOf(
                r.nextFloat(), r.nextFloat(), 0.35f + r.nextFloat() * 0.9f,
                2.5f + r.nextFloat() * 4f, r.nextFloat() * 6.28f,
                (r.nextFloat() * 2f - 1f), 0.4f + r.nextFloat() * 1.2f
            )
        }
    }
    // Líneas de viento: [yFrac, trail, largo(×bandW), grosor, alpha].
    val streaks = remember(event.id) {
        val r = kotlin.random.Random(event.id.hashCode() * 31 + 7)
        List(13) {
            floatArrayOf(
                r.nextFloat(), r.nextFloat() * 0.9f, 0.5f + r.nextFloat() * 1.1f,
                0.02f + r.nextFloat() * 0.05f, 0.25f + r.nextFloat() * 0.45f
            )
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val bandW = boardPx * 0.42f
        // El frente entra por la izquierda y sale sobrepasando el borde derecho.
        val front = androidx.compose.ui.util.lerp(-bandW * 0.6f, boardPx + bandW * 1.3f, p)
        val fade = (if (p < 0.82f) 1f else 1f - (p - 0.82f) / 0.18f).coerceIn(0f, 1f)
        val ice = Color(0xFFCDEBFF)
        val deepIce = Color(0xFF4FC8FF)

        // Velo frío global que sube y baja al pasar la brisa.
        val coldA = sin(p * Math.PI.toFloat()) * 0.12f * fade
        if (coldA > 0.001f) drawRect(deepIce.copy(alpha = coldA), size = Size(boardPx, boardPx))

        // Estela ya "helada" a la izquierda del frente (lo que ya barrió).
        val wakeX = front.coerceIn(0f, boardPx)
        if (wakeX > 0f) {
            drawRect(
                brush = Brush.horizontalGradient(
                    listOf(Color(0xFF3A8FD0).copy(alpha = 0.05f * fade), Color(0xFF6FD0FF).copy(alpha = 0.20f * fade)),
                    startX = 0f, endX = wakeX
                ),
                size = Size(wakeX, boardPx)
            )
        }
        // Banda de escarcha del frente (dos pasadas para suavidad).
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, deepIce.copy(alpha = 0.30f), ice.copy(alpha = 0.62f), deepIce.copy(alpha = 0.30f), Color.Transparent),
                startX = front - bandW, endX = front + bandW * 0.6f
            ),
            alpha = fade
        )

        // Líneas de viento (motion streaks) que preceden y siguen al frente.
        streaks.forEach { s ->
            val yy = s[0] * boardPx
            val hx = front - s[1] * bandW * 1.6f
            val len = s[2] * bandW
            if (hx > -len && hx < boardPx + len) {
                drawLine(
                    ice.copy(alpha = s[4] * fade),
                    Offset(hx - len, yy), Offset(hx, yy),
                    strokeWidth = boardPx * s[3], cap = StrokeCap.Round
                )
            }
        }

        // NUBE DE COPOS arrastrados por la ráfaga, con turbulencia (arremolinados).
        flakes.forEach { f ->
            val hx = front - f[1] * bandW * 2.4f
            if (hx < -cellPx || hx > boardPx + cellPx) return@forEach
            val wob = sin(p * f[3] * 6.28f + f[4]) * cellPx * 0.28f
            val yy = f[0] * boardPx + wob + p * f[5] * cellPx * 0.8f
            val a = fade * (0.85f - 0.35f * f[1])           // los más rezagados, más tenues
            val rad = cellPx * 0.05f * f[2]
            // Estela corta (motion) + cabeza brillante.
            drawLine(
                Color.White.copy(alpha = a * 0.5f),
                Offset(hx - rad * f[6] * 6f, yy), Offset(hx, yy),
                strokeWidth = rad * 1.2f, cap = StrokeCap.Round
            )
            drawCircle(Color.White.copy(alpha = a), rad, Offset(hx, yy))
        }

        // Frente brillante (borde delantero de la ráfaga).
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.5f * fade)),
                startX = front - cellPx * 0.9f, endX = front
            ),
            topLeft = Offset(front - cellPx * 0.9f, 0f),
            size = Size(cellPx * 0.9f, boardPx)
        )

        // Impacto en el Teika: al cruzar el frente por su x, estalla un anillo de cristales.
        val hit = ((front - target.x) / (bandW * 0.6f)).coerceIn(0f, 1f)
        if (hit > 0f) {
            val r = event.level * cellPx * 0.62f * hit
            drawCircle(
                Color(0xFFEAF6FF).copy(alpha = 0.7f * (1f - hit) + 0.15f),
                radius = r, center = target, style = Stroke(width = cellPx * 0.10f)
            )
            val spikes = 8
            for (i in 0 until spikes) {
                val ang = (i / spikes.toFloat()) * 2f * Math.PI.toFloat()
                val d = r * (0.7f + 0.3f * (i % 2))
                val tip = Offset(target.x + kotlin.math.cos(ang) * d, target.y + kotlin.math.sin(ang) * d)
                drawLine(
                    Color(0xFFCDEBFF).copy(alpha = 0.85f * (1f - hit)),
                    target, tip, strokeWidth = cellPx * 0.06f, cap = StrokeCap.Round
                )
            }
        }
    }
}



/** Dibuja un rayo zigzag de [from] a [to] con [segments] tramos y desvío [amp] (px). */
private fun DrawScope.drawLightning(
    from: Offset, to: Offset, segments: Int, amp: Float, color: Color, width: Float, seed: Int
) {
    val path = Path().apply { moveTo(from.x, from.y) }
    val dx = (to.x - from.x); val dy = (to.y - from.y)
    val nx = -dy; val ny = dx
    val nlen = kotlin.math.sqrt(nx * nx + ny * ny).coerceAtLeast(0.0001f)
    for (i in 1 until segments) {
        val f = i.toFloat() / segments
        val jitter = (((seed + i * 0x9E3779) and 0xFF) / 255f - 0.5f) * 2f * amp
        val px = from.x + dx * f + nx / nlen * jitter
        val py = from.y + dy * f + ny / nlen * jitter
        path.lineTo(px, py)
    }
    path.lineTo(to.x, to.y)
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

/** Bioma Eléctrico: una bobina se descargó (toque en orden). Destello + rayos radiales. */
@Composable
fun CoilChargedVFX(event: GameEvent.CoilCharged, cellSize: Dp, onComplete: () -> Unit) {
    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) { clock.animateTo(1f, tween(440, easing = LinearEasing)); onComplete() }
    val cx = (event.position.x + 0.5f) * cellPx
    val cy = (event.position.y + 0.5f) * cellPx
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val a = (1f - t).coerceIn(0f, 1f)
        val c = Offset(cx, cy)
        val flash = (1f - t / 0.4f).coerceIn(0f, 1f)
        if (flash > 0f) drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.9f * flash), Color(0x885FE0FF).copy(alpha = flash), Color.Transparent), c, cellPx),
            radius = cellPx, center = c
        )
        repeat(6) { i ->
            val ang = (i * 60f) * kotlin.math.PI.toFloat() / 180f
            val end = Offset(cx + kotlin.math.cos(ang) * cellPx * (0.5f + t), cy + kotlin.math.sin(ang) * cellPx * (0.5f + t))
            drawLightning(c, end, 5, cellPx * 0.12f, Color(0xFFBFF4FF).copy(alpha = a), cellPx * 0.04f, i + event.number * 7)
        }
    }
}

// ── Habilidades de compañero (rework 2026-07-17): VFX premium ─────────────────

/**
 * Rayco: rayo/corriente serpenteante que une dos puntos del tablero ([from]→[to]). Núcleo blanco +
 * arcos cian ramificados + destello en ambos extremos. Hoy lo dispara «Energía Cruzada» al
 * autocompletar las perpendiculares de un Teika energizado.
 */
@Composable
fun OverloadBeamVFX(event: GameEvent.OverloadBeam, cellPx: Float, onComplete: () -> Unit) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) { clock.animateTo(1f, tween(460, easing = LinearEasing)); onComplete() }
    val from = Offset((event.from.x + 0.5f) * cellPx, (event.from.y + 0.5f) * cellPx)
    val to   = Offset((event.to.x + 0.5f) * cellPx, (event.to.y + 0.5f) * cellPx)
    val cyan = Color(0xFF6FE8FF)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val a = if (t < 0.25f) t / 0.25f else (1f - (t - 0.25f) / 0.75f)   // sube rápido, decae
        val amp = cellPx * (0.28f + 0.20f * kotlin.math.sin(t * 22f))
        val seed = event.id.hashCode()
        // Halo grueso + núcleo blanco + arcos ramificados.
        drawLightning(from, to, 11, amp * 1.5f, cyan.copy(alpha = 0.35f * a), cellPx * 0.22f, seed)
        drawLightning(from, to, 13, amp, cyan.copy(alpha = 0.85f * a), cellPx * 0.10f, seed + 1)
        drawLightning(from, to, 15, amp * 0.6f, Color.White.copy(alpha = a), cellPx * 0.045f, seed + 2)
        // Destellos en los polos (Teikas "sobrecargados").
        listOf(from, to).forEach { c ->
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.White.copy(alpha = a), cyan.copy(alpha = 0.6f * a), Color.Transparent),
                    c, cellPx * 0.9f
                ),
                radius = cellPx * 0.9f, center = c
            )
        }
    }
}

/**
 * Rayco / Energía Cruzada: aura eléctrica persistente sobre los Teikas ENERGIZADOS (anillo latiendo
 * + chispas, siguiendo la posición VIVA del cuerpo). Nació para la vieja «Sobrecarga» (glow del
 * Teika elegido mientras se escogía el segundo), podada el 2026-07-30; hoy su único usuario es
 * `energizedTeikaIds`.
 */
@Composable
fun OverloadChargeGlow(
    /** Se lee al DIBUJAR: los cuerpos cambian cada tick de física y leerlos en composición recomponía el tablero. */
    bodiesState: androidx.compose.runtime.State<List<TeikaBody>>,
    selectedIds: Set<String>,
    cellPx: Float,
) {
    if (selectedIds.isEmpty()) return
    val pulse = rememberInfiniteTransition(label = "overloadGlow")
    val t by pulse.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(620, easing = LinearEasing)), label = "overloadGlowT"
    )
    val cyan = Color(0xFF6FE8FF)
    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
        bodiesState.value.forEach { b ->
            if (b.id !in selectedIds) return@forEach
            val c = Offset(b.x * cellPx, b.y * cellPx)
            val r = b.radius * cellPx
            val ring = r * (1.02f + 0.10f * kotlin.math.sin(t * 2f * Math.PI.toFloat()))
            drawCircle(cyan.copy(alpha = 0.30f), ring * 1.25f, c, style = Stroke(width = cellPx * 0.10f))
            drawCircle(Color.White.copy(alpha = 0.85f), ring, c, style = Stroke(width = cellPx * 0.06f))
            // Chispas orbitando.
            repeat(6) { i ->
                val ang = (i * 60f) * Math.PI.toFloat() / 180f + t * 2f * Math.PI.toFloat()
                val sp = Offset(c.x + kotlin.math.cos(ang) * ring, c.y + kotlin.math.sin(ang) * ring)
                drawCircle(cyan.copy(alpha = 0.9f), cellPx * 0.05f, sp)
            }
        }
    }
}

/**
 * Nieves / Estalactitas: un carámbano cae del techo sobre la columna [col] y la perfora de arriba
 * abajo, dejando esquirlas de escarcha.
 */
@Composable
fun IcicleDropVFX(event: GameEvent.IcicleDrop, cellPx: Float, onComplete: () -> Unit) {
    // Timeline AAA (rework it.54), sincronizado con el ViewModel (impacto ≈ delayMs + 600 ms):
    //  0.00–0.22  FORMACIÓN — el carámbano CRISTALIZA colgando del techo (crece + destellos).
    //  0.22–0.50  CAÍDA — acelera (ease-in) con estela de motion blur y escarcha.
    //  0.50–1.00  IMPACTO — onda de choque, esquirlas en abanico, floración de escarcha que
    //             baja por la columna acompañando la perforación celda a celda.
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        kotlinx.coroutines.delay(event.delayMs.toLong())
        clock.animateTo(1f, tween(1200, easing = LinearEasing)); onComplete()
    }
    val seed = remember(event.id) { event.id.hashCode() }
    val boardH = GameState.GRID_SIZE * cellPx
    val cx = (event.col + 0.5f) * cellPx
    val ice = Color(0xFFCDEFFF)
    val iceDeep = Color(0xFF7EC8F0)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val formP = (t / 0.22f).coerceIn(0f, 1f)
        val fallP = ((t - 0.22f) / 0.28f).coerceIn(0f, 1f)
        val hitP  = ((t - 0.50f) / 0.50f).coerceIn(0f, 1f)
        val len = cellPx * 1.7f
        // Punta del carámbano: cuelga del techo mientras se forma, cae con ease-in cuadrático.
        val tipY = when {
            fallP <= 0f -> len * formP
            else        -> len + (boardH - len) * fallP * fallP
        }
        if (hitP < 1f && fallP < 1f || hitP <= 0f) {
            // Cuerpo del carámbano (triángulo alargado, brillo lateral) — crece al formarse.
            val grow = formP
            val halfW = cellPx * 0.28f * grow
            val topY = tipY - len * grow
            val path = Path().apply {
                moveTo(cx, tipY)
                lineTo(cx - halfW, topY)
                lineTo(cx + halfW, topY)
                close()
            }
            drawPath(path, ice.copy(alpha = 0.95f))
            drawPath(path, Color.White.copy(alpha = 0.7f), style = Stroke(width = cellPx * 0.05f))
            // Brillo interior (faceta de hielo).
            drawLine(Color.White.copy(alpha = 0.8f * grow),
                Offset(cx - halfW * 0.4f, topY + len * 0.15f), Offset(cx - halfW * 0.15f, tipY - len * 0.2f),
                strokeWidth = cellPx * 0.045f)
            // Destellos de cristalización durante la formación.
            if (formP < 1f) {
                repeat(4) { i ->
                    val hh = seed + i * 0x51ED
                    val gx = cx + ((hh and 0xFF) / 255f - 0.5f) * cellPx * 0.8f
                    val gy = topY + ((hh ushr 8 and 0xFF) / 255f) * len * grow
                    val tw = (0.5f + 0.5f * kotlin.math.sin(t * 40f + i * 1.9f)) * (1f - formP * 0.5f)
                    drawCircle(Color.White.copy(alpha = 0.85f * tw), cellPx * 0.05f, Offset(gx, gy))
                }
            }
            // CAÍDA: estela de motion blur + escarcha suspendida detrás.
            if (fallP > 0f) {
                drawLine(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, ice.copy(alpha = 0.55f)),
                        startY = (topY - cellPx * 2.2f).coerceAtLeast(0f), endY = topY
                    ),
                    Offset(cx, (topY - cellPx * 2.2f).coerceAtLeast(0f)), Offset(cx, topY),
                    strokeWidth = cellPx * 0.20f
                )
                repeat(4) { i ->
                    val hh = seed + i * 0x9E37
                    val fy = topY - ((hh and 0xFF) / 255f) * cellPx * 1.8f
                    drawCircle(ice.copy(alpha = 0.5f * (1f - fallP)), cellPx * 0.045f,
                        Offset(cx + ((hh ushr 8 and 0xFF) / 255f - 0.5f) * cellPx * 0.5f, fy))
                }
            }
        }
        // IMPACTO: onda de choque + esquirlas en abanico + floración de escarcha por la columna.
        if (hitP > 0f) {
            val impact = Offset(cx, boardH - cellPx * 0.2f)
            // Onda de choque elíptica en el suelo.
            val ringT = (hitP / 0.45f).coerceIn(0f, 1f)
            if (ringT < 1f) {
                drawOval(
                    Color.White.copy(alpha = (1f - ringT) * 0.7f),
                    topLeft = Offset(impact.x - cellPx * (0.3f + 1.3f * ringT), impact.y - cellPx * (0.12f + 0.35f * ringT)),
                    size = Size(cellPx * (0.6f + 2.6f * ringT), cellPx * (0.24f + 0.7f * ringT)),
                    style = Stroke(width = cellPx * 0.07f * (1f - ringT))
                )
            }
            // Esquirlas de hielo saltando en abanico (parábola).
            repeat(7) { i ->
                val hh = seed + i * 0x77F1
                val ang = (-2.6f + (i / 6f) * 2.06f) + ((hh and 0x1F) / 255f)
                val spd = 1.1f + ((hh ushr 5 and 0x3F) / 63f) * 1.3f
                val d = spd * cellPx * hitP
                val px = impact.x + kotlin.math.cos(ang) * d
                val py = impact.y + kotlin.math.sin(ang) * d + hitP * hitP * cellPx * 1.6f
                withTransform({ rotate(hitP * 420f + i * 51f, Offset(px, py)) }) {
                    drawRect(iceDeep.copy(alpha = (1f - hitP) * 0.9f),
                        topLeft = Offset(px - cellPx * 0.05f, py - cellPx * 0.09f),
                        size = Size(cellPx * 0.10f, cellPx * 0.18f))
                }
            }
            // Floración de ESCARCHA descendiendo por la columna (acompaña la perforación).
            val frostY = boardH * (hitP * 1.15f).coerceAtMost(1f)
            drawRect(
                Brush.verticalGradient(
                    listOf(ice.copy(alpha = 0.30f * (1f - hitP * 0.7f)), Color.Transparent),
                    startY = 0f, endY = frostY
                ),
                topLeft = Offset(cx - cellPx * 0.5f, 0f),
                size = Size(cellPx, frostY)
            )
            // Cristalitos de escarcha en los bordes de la columna.
            repeat(6) { i ->
                val hh = seed + i * 0x31AB
                val fy = ((hh and 0xFF) / 255f) * frostY
                val side = if (i % 2 == 0) -1f else 1f
                drawCircle(ice.copy(alpha = 0.6f * (1f - hitP)), cellPx * 0.05f,
                    Offset(cx + side * cellPx * 0.42f, fy))
            }
        }
    }
}

/**
 * Iñaki / Cruz de Fuego: la fila y la columna centrales se prenden desde [center] hacia los bordes.
 */
@Composable
fun FireCrossVFX(event: GameEvent.FireCross, cellPx: Float, onComplete: () -> Unit) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) { clock.animateTo(1f, tween(620, easing = LinearEasing)); onComplete() }
    val boardPx = GameState.GRID_SIZE * cellPx
    val c = Offset((event.center.x + 0.5f) * cellPx, (event.center.y + 0.5f) * cellPx)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val reach = boardPx * (0.5f + t * 0.6f).coerceAtMost(1f)
        val a = (1f - t).coerceIn(0f, 1f)
        val thick = cellPx * (0.9f + 0.3f * kotlin.math.sin(t * 30f))
        val fireOuter = Color(0xFFFF6A00); val fireInner = Color(0xFFFFE082)
        // Barra horizontal (fila) y vertical (columna), núcleo claro + halo naranja.
        fun bar(horizontal: Boolean) {
            val start = if (horizontal) Offset(c.x - reach, c.y) else Offset(c.x, c.y - reach)
            val end   = if (horizontal) Offset(c.x + reach, c.y) else Offset(c.x, c.y + reach)
            drawLine(fireOuter.copy(alpha = 0.55f * a), start, end, strokeWidth = thick * 1.7f, cap = StrokeCap.Round)
            drawLine(fireInner.copy(alpha = 0.95f * a), start, end, strokeWidth = thick, cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.7f * a), start, end, strokeWidth = thick * 0.4f, cap = StrokeCap.Round)
        }
        bar(true); bar(false)
        // Núcleo ardiente en el cruce.
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = a), fireInner.copy(alpha = 0.8f * a), Color.Transparent), c, cellPx * 1.4f),
            radius = cellPx * 1.4f, center = c
        )
    }
}

/**
 * Iñaki / Fichas en Llamas: la IGNICIÓN de la bandeja. Lenguas de fuego que suben desde la base del
 * panel de fichas (aquí es donde las fichas se prendieron), con flash inicial, flicker por lengua y
 * chispas ascendiendo. [count] escala la intensidad (1 ficha = discreto, bandeja entera = incendio).
 */
@Composable
fun TrayIgniteVFX(count: Int, onComplete: () -> Unit, modifier: Modifier = Modifier) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) { clock.animateTo(1f, tween(1050, easing = LinearEasing)); onComplete() }
    val intensity = (0.55f + 0.225f * count.coerceIn(1, 3)).coerceAtMost(1.25f)
    Canvas(modifier = modifier) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val w = size.width; val h = size.height
        val fireOuter = Color(0xFFFF6A00); val fireInner = Color(0xFFFFE082)
        // Envolvente: entra rápido, sostiene, se apaga.
        val env = when {
            t < 0.12f -> t / 0.12f
            t < 0.62f -> 1f
            else      -> (1f - (t - 0.62f) / 0.38f).coerceIn(0f, 1f)
        } * intensity
        // Flash de ignición.
        val flash = (1f - t / 0.14f).coerceIn(0f, 1f)
        if (flash > 0f) drawRect(fireInner.copy(alpha = 0.28f * flash * intensity), size = size)
        // Cama de brasas: gradiente cálido desde la base del panel.
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, fireOuter.copy(alpha = 0.30f * env)),
                startY = h * 0.25f, endY = h
            ),
            topLeft = Offset(0f, h * 0.25f), size = Size(w, h * 0.75f)
        )
        // Lenguas de fuego subiendo (reparto áureo + flicker propio por lengua).
        val tongues = 9
        for (k in 0 until tongues) {
            val fx = w * ((k * 0.618034f + 0.09f) % 1f)
            val phase = k * 1.7f
            val flick = 0.6f + 0.4f * kotlin.math.sin(t * 26f + phase)
            val rise = h * (0.30f + 0.42f * flick)
            val baseY = h * 0.98f
            val tipY = baseY - rise
            val ww = w * 0.035f * (0.8f + 0.4f * flick)
            val flame = Path().apply {
                moveTo(fx, tipY)
                quadraticTo(fx + ww, baseY - rise * 0.45f, fx + ww * 0.7f, baseY)
                lineTo(fx - ww * 0.7f, baseY)
                quadraticTo(fx - ww, baseY - rise * 0.45f, fx, tipY)
                close()
            }
            drawPath(flame, fireOuter.copy(alpha = 0.55f * env))
            drawPath(flame, fireInner.copy(alpha = 0.40f * env * flick), style = Stroke(width = ww * 0.4f))
        }
        // Chispas ascendiendo.
        repeat(12) { i ->
            val sx = w * ((i * 0.618034f + 0.31f) % 1f)
            val ph = (i * 0.083f + t * (0.9f + (i % 3) * 0.25f)) % 1f
            val sy = h * (1f - ph)
            drawCircle(fireInner.copy(alpha = (1f - ph) * 0.8f * env), w * 0.006f * (1f + (i % 2)), Offset(sx, sy))
        }
    }
}

/**
 * Kenji / CORTE DE SOMBRAS (rework 2026-07-28, sustituye al VFX del clon de Doble Sombra).
 *
 * Dos capas, una sola pasada de Canvas y CERO asignaciones por fotograma:
 *   1. TAJOS — [GameEvent.ShadowBladeCut.slashes] destellos de katana que barren el tablero entero
 *      en diagonal, escalonados y alternando dirección. Cada tajo es una línea que "crece" de un
 *      borde al otro con estela índigo y filo blanco: es el gesto que el jugador lee como *corte*.
 *   2. MITADES — cada bloque con estrella víctima se parte: sus dos mitades se separan del eje del
 *      corte, giran un poco y se apagan, con la cicatriz blanca del filo entre ambas.
 *
 * El escalonado (`delay` por índice de víctima) está sincronizado con el `delayMs` de los
 * `TargetCollected` que emite el ViewModel: la estrella sale justo cuando el bloque se abre.
 */
@Composable
fun ShadowBladeCutVFX(event: GameEvent.ShadowBladeCut, cellPx: Float, onComplete: () -> Unit) {
    val n = event.cells.size
    val totalMs = 420 + n * 55 + 320
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) { clock.animateTo(1f, tween(totalMs, easing = LinearEasing)); onComplete() }
    val cells = event.cells
    val slashes = event.slashes.coerceIn(1, 9)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val indigo = Color(0xFF7C4DFF)
        val elapsed = t * totalMs

        // ── 1) Los TAJOS que cruzan la pantalla ──────────────────────────────────────────────
        // Reparte los tajos en los primeros 420 ms; cada uno dura 200 ms y cruza de lado a lado.
        for (i in 0 until slashes) {
            val startAt = i * (420f / slashes)
            val st = ((elapsed - startAt) / 200f)
            if (st <= 0f || st >= 1f) continue
            // Alterna sentido y desplaza el punto de entrada por índice → abanico de cortes.
            val down = i % 2 == 0
            val off = (i + 1f) / (slashes + 1f)
            val x0 = -size.width * 0.15f
            val x1 = size.width * 1.15f
            val yA = size.height * (off - 0.35f)
            val yB = size.height * (off + 0.35f)
            val sy = if (down) yA else yB
            val ey = if (down) yB else yA
            val head = st
            val tail = (st - 0.35f).coerceAtLeast(0f)
            val p0 = Offset(x0 + (x1 - x0) * tail, sy + (ey - sy) * tail)
            val p1 = Offset(x0 + (x1 - x0) * head, sy + (ey - sy) * head)
            val a = kotlin.math.sin(st * kotlin.math.PI.toFloat())
            drawLine(indigo.copy(alpha = 0.45f * a), p0, p1, strokeWidth = cellPx * 0.34f, cap = StrokeCap.Round)
            drawLine(Color.White.copy(alpha = 0.95f * a), p0, p1, strokeWidth = cellPx * 0.09f, cap = StrokeCap.Round)
        }

        // ── 2) Las MITADES de cada bloque con estrella ───────────────────────────────────────
        cells.forEachIndexed { i, p ->
            val startAt = 120f + i * 55f
            val ct = ((elapsed - startAt) / 320f)
            if (ct <= 0f) return@forEachIndexed
            if (ct >= 1f) return@forEachIndexed
            val a = 1f - ct
            val cx = (p.x + 0.5f) * cellPx
            val cy = (p.y + 0.5f) * cellPx
            val half = cellPx * 0.5f
            val gap = ct * cellPx * 0.55f          // las mitades se separan
            val drop = ct * ct * cellPx * 0.5f     // y caen un poco
            // Mitad superior-izquierda y mitad inferior-derecha, separándose en diagonal.
            for (s in -1..1 step 2) {
                val ox = cx + s * gap * 0.7f
                val oy = cy + s * gap * 0.7f + drop
                withTransform({ rotate(s * ct * 26f, Offset(ox, oy)) }) {
                    drawRect(
                        Color(0xFFE8EDFF).copy(alpha = 0.32f * a),
                        topLeft = Offset(ox - half, oy - half),
                        size = Size(cellPx, half)
                    )
                }
            }
            // Cicatriz del filo: la línea blanca que separa las dos mitades.
            val sc = cellPx * (0.7f + 0.5f * ct)
            drawLine(
                Color.White.copy(alpha = a),
                Offset(cx - sc, cy + sc), Offset(cx + sc, cy - sc),
                strokeWidth = cellPx * 0.07f * a, cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Flora / Cañón Floral (rework it.54): la CEREMONIA completa. Fase 1 — una flor CERRADA nace en el
 * centro y se abre lenta y dramática (pétalos en dos anillos desplegándose con un giro suave +
 * corazón dorado). Fase 2 — el Teika de mayor tier SALTA en arco sobre la flor encogiéndose en
 * vuelo (TeikaIcon real, no un círculo). Fase 3 — la flor se CIERRA con él dentro y se ENTIERRA:
 * se hunde en el tablero con tierra saltando y un anillo de polvo. El capullo-cañón aparece después
 * en el borde del panel de fichas (FloraAimLayer).
 */
@Composable
fun FloraCeremonyVFX(
    event: GameEvent.FloraCeremony,
    cellSize: Dp,
    cellPx: Float,
    onComplete: () -> Unit
) {
    val totalMs = event.openMs + event.jumpMs + event.closeMs
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        clock.animateTo(totalMs.toFloat(), tween(totalMs, easing = LinearEasing))
        onComplete()
    }
    val now = clock.value
    val g = GameState.GRID_SIZE
    val cx = g / 2f * cellPx
    val cyBase = g / 2f * cellPx
    val openP = (now / event.openMs).coerceIn(0f, 1f)
    val jumpP = ((now - event.openMs) / event.jumpMs).coerceIn(0f, 1f)
    val closeP = ((now - event.openMs - event.jumpMs) / event.closeMs).coerceIn(0f, 1f)
    fun easeInOut(x: Float) = x * x * (3f - 2f * x)

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            if (now >= totalMs) return@Canvas
            val petal = Color(0xFFFF7EB0)
            val petalDeep = Color(0xFFE91E63)
            val core = Color(0xFFFFD54F)
            val leaf = Color(0xFF66BB6A)
            val soil = Color(0xFF5D4037)
            // Entierro: todo el conjunto se hunde y se desvanece al final.
            val sink = easeInOut(closeP) * cellPx * 1.1f
            val cy = cyBase + sink
            val globalA = (1f - ((closeP - 0.72f) / 0.28f)).coerceIn(0f, 1f)
            // Nacimiento del capullo (primeros ~300 ms) + apertura LENTA y dramática.
            val budIn = (now / 300f).coerceIn(0f, 1f)
            val open = when {
                closeP > 0f -> 1f - easeInOut((closeP * 1.8f).coerceIn(0f, 1f))   // se cierra rápido
                else        -> easeInOut(openP)
            }
            // Agujero de tierra bajo la flor (aparece al enterrarse).
            if (closeP > 0.15f) {
                val holeA = ((closeP - 0.15f) / 0.4f).coerceIn(0f, 1f)
                drawOval(
                    soil.copy(alpha = 0.55f * holeA * globalA),
                    topLeft = Offset(cx - cellPx * 1.1f, cyBase + cellPx * 0.55f),
                    size = Size(cellPx * 2.2f, cellPx * 0.8f)
                )
            }
            // Tallo/hojas en la base (crecen con el capullo).
            drawLine(leaf.copy(alpha = 0.9f * budIn * globalA),
                Offset(cx, cy + cellPx * 0.9f), Offset(cx, cy + cellPx * 0.2f),
                strokeWidth = cellPx * 0.12f, cap = StrokeCap.Round)
            // Dos anillos de pétalos desplegándose con giro suave.
            val reachOut = cellPx * (0.30f + 1.45f * open) * budIn
            val reachIn  = cellPx * (0.22f + 0.85f * open) * budIn
            val petalR   = cellPx * (0.30f + 0.42f * open) * budIn
            repeat(8) { i ->
                val ang = (i / 8f) * 6.28318f + open * 0.5f - 1.5708f
                val p = Offset(cx + kotlin.math.cos(ang) * reachOut, cy + kotlin.math.sin(ang) * reachOut)
                drawCircle(petal.copy(alpha = 0.85f * globalA), petalR, p)
                drawCircle(petalDeep.copy(alpha = 0.38f * globalA), petalR * 0.62f, p)
                drawCircle(Color.White.copy(alpha = 0.25f * open * globalA), petalR,
                    p, style = Stroke(width = cellPx * 0.03f))
            }
            repeat(6) { i ->
                val ang = (i / 6f) * 6.28318f - open * 0.4f
                val p = Offset(cx + kotlin.math.cos(ang) * reachIn, cy + kotlin.math.sin(ang) * reachIn)
                drawCircle(petal.copy(alpha = 0.9f * globalA), petalR * 0.75f, p)
            }
            // Corazón dorado (late mientras espera el salto). OJO: con budIn=0 en el primer frame
            // el radio era 0 y RadialGradient exige radio > 0 → crasheaba la app al castear.
            val beat = 1f + 0.08f * kotlin.math.sin(now * 0.012f) * open
            val coreR = cellPx * 0.75f * beat * budIn
            if (coreR >= 1f) {
                drawCircle(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = 0.9f * open * globalA),
                            core.copy(alpha = 0.85f * open * globalA), Color.Transparent),
                        Offset(cx, cy), coreR
                    ),
                    radius = coreR, center = Offset(cx, cy)
                )
            }
            // Polen flotando durante la apertura (dramatismo).
            if (open > 0.25f && closeP <= 0f) {
                repeat(7) { i ->
                    val ph = (i * 0.143f + now * 0.00045f) % 1f
                    val ang = i * 0.897f + now * 0.0011f
                    val rr = cellPx * (0.6f + 1.3f * ph)
                    drawCircle(core.copy(alpha = (1f - ph) * 0.5f * open),
                        cellPx * 0.05f,
                        Offset(cx + kotlin.math.cos(ang) * rr, cy + kotlin.math.sin(ang) * rr - ph * cellPx * 0.8f))
                }
            }
            // Tierra saltando al enterrarse (terrones en parábola) + anillo de polvo.
            if (closeP > 0.2f) {
                val dirtP = ((closeP - 0.2f) / 0.6f).coerceIn(0f, 1f)
                repeat(9) { i ->
                    val ang = -1.5708f + (i - 4) * 0.32f
                    val d = cellPx * (0.5f + 1.1f * dirtP)
                    val px = cx + kotlin.math.cos(ang) * d
                    val py = cyBase + cellPx * 0.4f + kotlin.math.sin(ang) * d * 0.5f + dirtP * dirtP * cellPx * 1.2f
                    drawCircle(soil.copy(alpha = (1f - dirtP) * 0.8f), cellPx * (0.06f + 0.04f * (i % 3)), Offset(px, py))
                }
                drawCircle(Color(0xFFBCAAA4).copy(alpha = (1f - dirtP) * 0.4f),
                    cellPx * (0.6f + dirtP * 1.4f), Offset(cx, cyBase + cellPx * 0.5f),
                    style = Stroke(width = cellPx * 0.08f * (1f - dirtP)))
            }
        }
        // Fase 2: el Teika REAL salta en arco hasta la flor encogiéndose; la flor se lo traga.
        if (jumpP > 0f && closeP < 0.35f && now < totalMs) {
            val t = easeInOut(jumpP)
            val fromCx = event.fromX; val fromCy = event.fromY
            val toC = g / 2f
            val px = fromCx + (toC - fromCx) * t
            val arc = -kotlin.math.sin(t * kotlin.math.PI).toFloat() * 1.4f
            val py = fromCy + (toC - fromCy) * t + arc
            // Encoge de su tamaño real a ~0.85 celdas al posarse; al cerrarse la flor, desaparece.
            val diam = event.teikaType.level + (0.85f - event.teikaType.level) * t
            val vanish = if (closeP > 0f) (1f - closeP / 0.35f).coerceIn(0f, 1f) else 1f
            val half = diam / 2f
            TeikaIcon(
                type = event.teikaType,
                modifier = Modifier
                    .offset { IntOffset(((px - half) * cellPx).roundToInt(), ((py - half) * cellPx).roundToInt()) }
                    .size(cellSize * diam)
                    .graphicsLayer { alpha = vanish; scaleX = vanish.coerceAtLeast(0.4f); scaleY = vanish.coerceAtLeast(0.4f) }
            )
        }
    }
}

/** El marco del TESTARUDO (P-71) en un rectángulo cualquiera: el mismo de la casilla en reposo. */
internal fun DrawScope.drawStubbornFrame(tl: Offset, sz: Size, edge: Color) {
    val w = sz.width
    val h = sz.height
    val t = minOf(w, h) * 0.13f
    drawRect(edge.copy(alpha = 0.95f), Offset(tl.x + t / 2f, tl.y + t / 2f), Size(w - t, h - t), style = Stroke(width = t))
    val n = minOf(w, h) * 0.17f
    listOf(Offset(w / 2f, t), Offset(w / 2f, h - t), Offset(t, h / 2f), Offset(w - t, h / 2f)).forEach { c ->
        drawCircle(edge.copy(alpha = 0.95f), radius = n * 0.32f, center = tl + c)
    }
}

/**
 * Un bloque de madera CAE por gravedad (WoodFall): acelera (ease-in cuadrático, como un cuerpo en
 * caída), aterriza con squash y se asienta. Dibuja el bloque FIEL (skin + estrella + veneno), igual
 * que BlockFleeVFX pero con física de caída en vez de escabullida. Usado por el desplome de Maite.
 */
@Composable
fun WoodFallVFX(
    event: GameEvent.WoodFall,
    cellSize: Dp,
    onComplete: () -> Unit,
    reducedEffects: Boolean = false
) {
    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }
    val skin    = LocalBlockSkin.current
    val isWildcard  = event.colorType == WILDCARD_COLOR
    val isGoldChest = event.targetColor != null && !isWildcard
    val palette = woodPalette(skin, event.colorType, isGoldChest)
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val starBitmaps = remember(ctx) { targetStarBitmaps(ctx) }

    val settleMs = 110
    // Reloj ABSOLUTO en ms (materialización opcional → espera → caída → asentado): permite que las
    // filas-bonus de Maite hagan pop arriba (appearAtMs) y caigan juntas más tarde (delayMs).
    val totalMs = if (reducedEffects) 120 else event.delayMs + event.durationMs + settleMs
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        clock.animateTo(totalMs.toFloat(), tween(totalMs, easing = LinearEasing))
        onComplete()
    }
    val fromX = event.from.x * cellPx
    val fromY = event.from.y * cellPx
    val toX   = event.to.x * cellPx
    val toY   = event.to.y * cellPx

    // La cápsula del bloque-Teika viaja con el bloque (la capa de cápsulas no la pinta mientras vuela).
    val seedGeo = remember(cellPx) { SeedGeometry(cellPx) }
    Canvas(modifier = Modifier.fillMaxSize()) {
        // Al asentarse se SIGUE dibujando el bloque en reposo hasta que el evento se consuma:
        // la celda real se revela en onComplete y ambos dibujos son idénticos → sin hueco de frames
        // entre el fin de la caída y el revelado (el parpadeo del desplome venía de ese hueco).
        val now = clock.value.coerceAtMost(totalMs.toFloat())
        // Aún no materializado (solo filas-bonus: appearAtMs ≥ 0).
        if (event.appearAtMs >= 0 && now < event.appearAtMs) return@Canvas
        // Pop de materialización (escala con overshoot 0→1.16→1 + flash blanco).
        val spawnP = if (event.appearAtMs >= 0) ((now - event.appearAtMs) / 150f).coerceIn(0f, 1f) else 1f
        val overshoot = if (reducedEffects) 1f else if (spawnP < 1f) spawnP * (1.65f - 0.65f * spawnP) else 1f
        val fp = ((now - event.delayMs) / event.durationMs.toFloat()).coerceIn(0f, 1f)
        // Deslizamiento (Timoteo): loseta LIMPIA — interpola AMBOS ejes con ease-in-out y sin
        // deformación alguna (el modo caída solo animaba Y: un paso horizontal se teletransportaba).
        // Caída (Maite): ease-in cuadrático (gravedad) solo en Y + squash al aterrizar.
        val ease = if (event.slide) fp * fp * (3f - 2f * fp) else fp * fp
        val portal=event.portalOrigin!=null && event.portalDestination!=null
        val phase=if(fp<.5f) fp*2f else (fp-.5f)*2f
        val phaseEase=phase*phase*(3f-2f*phase)
        val origin=event.portalOrigin
        val destination=event.portalDestination
        val portalScale=if(!portal || reducedEffects) 1f else if(fp<.5f) 1f-phaseEase else phaseEase
        val orbitPoint=event.orbitCenter?.let { OrbitRules.point(event.from,it,event.orbitClockwise,fp) }
        val x = if(reducedEffects) toX else if(orbitPoint!=null) orbitPoint.first*cellPx else if(portal) {
            if(fp<.5f) fromX+((origin!!.x+.5f)*cellPx-fromX)*phaseEase
            else (destination!!.x+.5f)*cellPx+(toX-(destination.x+.5f)*cellPx)*phaseEase
        } else if (event.slide) fromX + (toX - fromX) * ease else toX
        val y = if(reducedEffects) toY else if(orbitPoint!=null) orbitPoint.second*cellPx else if(portal) {
            if(fp<.5f) fromY+((origin!!.y+.5f)*cellPx-fromY)*phaseEase
            else (destination!!.y+.5f)*cellPx+(toY-(destination.y+.5f)*cellPx)*phaseEase
        } else fromY + (toY - fromY) * ease
        val settle = ((now - event.delayMs - event.durationMs) / settleMs.toFloat()).coerceIn(0f, 1f)
        val squash = if (!reducedEffects && !event.slide && settle > 0f)
            1f - 0.16f * kotlin.math.sin(settle * kotlin.math.PI.toFloat()) else 1f
        val lift=if(event.orbitCenter!=null && !reducedEffects) {
            val up=(now/180f).coerceIn(0f,1f)
            val down=1f-((now-event.delayMs-event.durationMs)/110f).coerceIn(0f,1f)
            minOf(minOf(up,down)*cellPx*.055f,y.coerceAtLeast(0f))
        } else 0f
        val center = Offset(x + cellPx / 2f, y + cellPx / 2f-lift)
        val orbitScale=if(event.orbitCenter!=null && !reducedEffects) { val e=fp*fp*(3-2*fp); .5f+.5f*abs(kotlin.math.cos(e*PI.toFloat())) } else 1f
        if(lift>0f) {
            val shadowSide=cellPx*.88f*orbitScale
            drawRoundRect(Color.Black.copy(alpha=.24f),Offset(x+cellPx/2-shadowSide/2+cellPx*.035f,y+cellPx/2-shadowSide/2+cellPx*.055f),
                Size(shadowSide,shadowSide),cornerRadius=androidx.compose.ui.geometry.CornerRadius(cellPx*.1f*orbitScale))
        }
        val tl = Offset(x, y-lift)
        val full = Size(cellPx, cellPx)
        withTransform({ scale((2f - squash) * overshoot * portalScale * orbitScale, squash * overshoot * portalScale * orbitScale, center) }) {
            drawStyledTile(skin.style, tl, full, palette[0], palette[1], palette[2], skin.cornerFraction,
                variant = event.wood?.let { tileVariantOf(it.id) } ?: 0)
            // El marco del TESTARUDO (P-71) viaja con él.
            event.wood?.takeIf { it.stubborn }?.let { drawStubbornFrame(tl, full, skin.colorsFor(it.colorType).getOrElse(2) { Color.Black }) }
            if (event.poisoned) drawPoisonLayer(tl, full)
            // Bioma Cristal: la carcasa VIAJA con el bloque (ver BlockFleeVFX).
            // La ROCA del meteorito (P-294) es blindaje con otra cara: viaja como roca, no como cristal.
            if (event.armor > 0) { if (event.wood?.rock == true) drawSpaceRock(tl, full) else drawCrystalShell(tl, full) }
            event.targetColor?.let { tc ->
                val bmp = starBitmaps[tc] ?: return@let
                if (isGoldChest) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color(0xD9FFF1B8), Color(0x33FFDF8E), Color(0x00FFDF8E)),
                            center = center, radius = cellPx * 0.6f
                        ),
                        radius = cellPx * 0.6f, center = center
                    )
                }
                val starW = cellPx * skin.starScale
                val starH = starW * bmp.height.toFloat() / bmp.width.toFloat()
                val starCy = center.y + cellPx * skin.starOffsetYFraction
                drawImage(
                    image = bmp,
                    srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                    srcSize = androidx.compose.ui.unit.IntSize(bmp.width, bmp.height),
                    dstOffset = androidx.compose.ui.unit.IntOffset(
                        (center.x - starW / 2f).roundToInt(), (starCy - starH / 2f).roundToInt()
                    ),
                    dstSize = androidx.compose.ui.unit.IntSize(starW.roundToInt(), starH.roundToInt())
                )
            }
            // Flash blanco de materialización (solo filas-bonus, se apaga rápido).
            if (event.appearAtMs >= 0) {
                val flash = (1f - spawnP / 0.55f).coerceIn(0f, 1f)
                if (flash > 0f) drawRect(Color.White.copy(alpha = 0.6f * flash), tl, full)
            }
        }
        if (event.wood?.teikaSeed == true) {
            withTransform({ scale(portalScale*orbitScale,portalScale*orbitScale,center) }) {
                translate(left = center.x, top = center.y) { drawSeed(seedGeo, cellPx, biomeClockSec.floatValue, live = true) }
            }
        }
        // Polvillo de aterrizaje (solo caída: una loseta que desliza no levanta polvo).
        if (!event.slide && settle > 0f && settle < 0.8f) {
            val dustA = (1f - settle / 0.8f) * 0.5f
            val by = toY + cellPx
            repeat(3) { i ->
                val dx = (i - 1) * cellPx * 0.32f
                drawCircle(Color(0x66B8C4D8).copy(alpha = dustA), cellPx * 0.08f,
                    Offset(center.x + dx, by - cellPx * 0.08f - settle * cellPx * 0.12f))
            }
        }
    }
}

// (MaiteBonusRowVFX ERRADICADO en playtest r3: la fila rígida se reemplazó por bloques WoodFall
//  independientes con materialización — ver WoodFallVFX/appearAtMs.)

/**
 * Reynaldo / **PARTO REAL** — la ceremonia (rework 2026-08-27).
 *
 * Antes era un destello dorado de 560 ms sobre un hijo que ya existía: no se veía un parto, se veía
 * un fogonazo y un Teika nuevo. Ahora son cuatro tiempos y el hijo **nace al terminar la extrusión**
 * (el ViewModel lo crea en `spawnAtMs`), así que lo que se ve es lo que pasa:
 *
 *  1. **Corona** — desciende desde arriba y se posa sobre el padre.
 *  2. **Anillo** — un aro de luz dorada se expande y el padre se ilumina desde dentro.
 *  3. **Extrusión** — el hijo se estira despacio fuera del cuerpo del padre, unidos por un cuello
 *     que adelgaza (metaball): la parte lenta, la que hace que parezca que cuesta.
 *  4. **Desprendimiento** — el cuello se rompe, pop elástico y polvo dorado.
 */
@Composable
fun TeikaBirthVFX(event: GameEvent.TeikaBirth, cellPx: Float, onComplete: () -> Unit) {
    val clock = remember { Animatable(0f) }
    val total = event.totalMs - event.delayMs
    LaunchedEffect(event.id) {
        kotlinx.coroutines.delay(event.delayMs.toLong())
        clock.animateTo(1f, tween(total, easing = LinearEasing))
        onComplete()
    }
    val seed = remember(event.id) { event.id.hashCode() }
    val gold = Color(0xFFFFD54F)
    val goldDeep = Color(0xFFFFA000)
    val goldPale = Color(0xFFFFF3C4)
    val c = Offset((event.position.x + 0.5f) * cellPx, (event.position.y + 0.5f) * cellPx)
    // Punta de la corona: UN Path reutilizado (rewind + 3 vértices) en vez de tres nuevos por fotograma.
    val peakPath = remember { Path() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val nowMs = clock.value * total
        val crownEnd = event.crownMs.toFloat()
        val ringEnd = crownEnd + event.ringMs
        val extrudeEnd = ringEnd + event.extrudeMs
        val childR = cellPx * event.childLevel * 0.5f
        val parentR = childR + cellPx * 0.55f

        // ── 1. La corona baja y se posa ────────────────────────────────────────
        if (nowMs < ringEnd) {
            val t = (nowMs / crownEnd).coerceIn(0f, 1f)
            val ease = 1f - (1f - t) * (1f - t)                       // ease-out: cae y frena
            val cy = c.y - parentR * 1.5f - (1f - ease) * cellPx * 2.2f
            val a = (t * 2.2f).coerceAtMost(1f)
            val w = parentR * 1.1f
            // Aro de la corona + tres puntas con su gema.
            drawArc(
                color = gold.copy(alpha = a),
                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                topLeft = Offset(c.x - w * 0.5f, cy - w * 0.16f),
                size = androidx.compose.ui.geometry.Size(w, w * 0.32f),
                style = Stroke(width = cellPx * 0.09f)
            )
            for (k in -1..1) {
                val px = c.x + k * w * 0.36f
                val peak = cy - w * (if (k == 0) 0.42f else 0.3f)
                peakPath.rewind()
                peakPath.moveTo(px - w * 0.11f, cy)
                peakPath.lineTo(px, peak)
                peakPath.lineTo(px + w * 0.11f, cy)
                peakPath.close()
                drawPath(peakPath, gold.copy(alpha = a))
                drawCircle(goldPale.copy(alpha = a), cellPx * 0.045f, Offset(px, peak))
            }
        }

        // ── 2. Anillo de luz + el padre iluminado por dentro ───────────────────
        if (nowMs in crownEnd..extrudeEnd) {
            val t = ((nowMs - crownEnd) / event.ringMs).coerceIn(0f, 1f)
            val r = parentR * (0.5f + 1.4f * t)
            drawCircle(
                gold.copy(alpha = 0.65f * (1f - t)), r, c,
                style = Stroke(width = cellPx * 0.1f * (1f - t) + cellPx * 0.02f)
            )
            // Resplandor interno sostenido durante toda la extrusión.
            val glow = if (nowMs < ringEnd) t else 1f - ((nowMs - ringEnd) / event.extrudeMs) * 0.5f
            drawCircle(
                Brush.radialGradient(
                    listOf(goldPale.copy(alpha = 0.45f * glow), goldDeep.copy(alpha = 0.16f * glow), Color.Transparent),
                    c, parentR * 1.4f
                ),
                radius = parentR * 1.4f, center = c
            )
        }

        // ── 3. Extrusión: el hijo se estira fuera, unido por un cuello ─────────
        if (nowMs in ringEnd..extrudeEnd) {
            val t = ((nowMs - ringEnd) / event.extrudeMs).coerceIn(0f, 1f)
            // Ease-in-out con tirón final: cuesta salir y de pronto cede.
            val e = t * t * (3f - 2f * t)
            val travel = (parentR + childR) * 0.95f * e
            val cx = c.x + event.dir * travel
            val cy = c.y + childR * 0.35f * e
            // Cuello: se estrecha conforme el hijo se aleja (efecto metaball barato y legible).
            val neckHalf = childR * (0.78f - 0.62f * e)
            if (neckHalf > 0.5f) {
                val x0 = kotlin.math.min(c.x, cx)
                val x1 = kotlin.math.max(c.x, cx)
                drawRoundRect(
                    color = gold.copy(alpha = 0.5f + 0.3f * (1f - e)),
                    topLeft = Offset(x0, c.y - neckHalf),
                    size = androidx.compose.ui.geometry.Size((x1 - x0).coerceAtLeast(1f), neckHalf * 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(neckHalf)
                )
            }
            // El hijo: crece mientras sale, con brillo propio.
            val r = childR * (0.35f + 0.65f * e)
            drawCircle(
                Brush.radialGradient(
                    listOf(goldPale, gold, goldDeep),
                    Offset(cx - r * 0.3f, cy - r * 0.3f), r * 1.35f
                ),
                radius = r, center = Offset(cx, cy)
            )
            drawCircle(Color.White.copy(alpha = 0.5f), r * 0.26f, Offset(cx - r * 0.3f, cy - r * 0.32f))
        }

        // ── 4. Desprendimiento: pop elástico + polvo dorado ────────────────────
        if (nowMs >= extrudeEnd) {
            val t = ((nowMs - extrudeEnd) / event.detachMs).coerceIn(0f, 1f)
            val a = 1f - t
            val cx = c.x + event.dir * (parentR + childR) * 0.95f
            val cy = c.y + childR * 0.35f
            // Onda del corte: el cuello acaba de romperse.
            drawCircle(
                goldPale.copy(alpha = 0.6f * a), childR * (0.6f + 1.6f * t),
                Offset((c.x + cx) / 2f, (c.y + cy) / 2f),
                style = Stroke(width = cellPx * 0.06f * a)
            )
            // Polvo dorado saliendo del punto de ruptura.
            for (i in 0 until 10) {
                val ang = (i / 10f + (seed and 0xF) / 16f) * 6.28318f
                val rr = childR * (0.5f + 1.9f * t) * (0.7f + 0.3f * (((seed + i * 53) and 0x7) / 7f))
                drawCircle(
                    gold.copy(alpha = 0.75f * a), cellPx * 0.045f,
                    Offset(
                        (c.x + cx) / 2f + kotlin.math.cos(ang) * rr,
                        (c.y + cy) / 2f + kotlin.math.sin(ang) * rr - t * cellPx * 0.2f
                    )
                )
            }
        }
    }
}

/**
 * Toño / Miasma Venenoso: una niebla tóxica verde crece desde [PoisonMiasma.center] cubriendo el
 * tablero, se concentra sobre las celdas marcadas (la madera que va a corroerse) y remolinea con
 * esporas ácidas antes de que todo reviente casi a la vez.
 */
@Composable
fun PoisonMiasmaVFX(event: GameEvent.PoisonMiasma, cellPx: Float, onComplete: () -> Unit) {
    // Timeline sincronizado con el ViewModel (rework it.54): 0–0.29 la niebla se esparce (~550 ms),
    // 0.29–0.79 la madera marcada se CORROE a la vista (manchas ácidas comiéndose el bloque, ~1 s),
    // 0.79–1 la niebla se disuelve mientras la ola de shatters revienta todo (~1500–1800 ms).
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) { clock.animateTo(1f, tween(1900, easing = LinearEasing)); onComplete() }
    val seed = remember(event.id) { event.id.hashCode() }
    val toxic = Color(0xFF7CFF3D)
    val toxicDeep = Color(0xFF2E7D32)
    val acidDark = Color(0xFF1B3D12)
    val c = Offset(event.center.x * cellPx, event.center.y * cellPx)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val grow = FastOutSlowInEasing.transform((t / 0.29f).coerceIn(0f, 1f))
        val corrode = ((t - 0.29f) / 0.50f).coerceIn(0f, 1f)          // avance de la corrosión
        val fade = (1f - ((t - 0.82f) / 0.18f)).coerceIn(0f, 1f)      // disolución final
        val boardR = cellPx * GameState.GRID_SIZE
        // ── Fase 1: halo de niebla que crece desde el centro cubriendo el tablero ──
        val r = (boardR * 0.95f * grow).coerceAtLeast(1f)
        drawCircle(
            Brush.radialGradient(
                listOf(toxic.copy(alpha = 0.20f * fade), toxicDeep.copy(alpha = 0.15f * fade), Color.Transparent),
                c, r
            ),
            radius = r, center = c
        )
        // Jirones de niebla que respiran (remolino lento de baja frecuencia).
        repeat(5) { k ->
            val ang = (k / 5f) * 6.28318f + t * 1.6f
            val rr = r * (0.35f + 0.14f * k)
            val wisp = Offset(c.x + kotlin.math.cos(ang) * rr, c.y + kotlin.math.sin(ang) * rr)
            drawCircle(toxic.copy(alpha = 0.07f * fade * grow), cellPx * (1.4f + 0.5f * (k % 3)), wisp)
        }
        // ── Fase 2: CORROSIÓN por celda — el bloque se DESGASTA a la vista ──
        event.cells.forEach { p ->
            val cell = Offset((p.x + 0.5f) * cellPx, (p.y + 0.5f) * cellPx)
            val dist = kotlin.math.hypot(cell.x - c.x, cell.y - c.y)
            // La ola de niebla tiene que ALCANZAR la celda antes de empezar a corroerla.
            val reach = ((grow * boardR - dist) / cellPx).coerceIn(0f, 1f)
            if (reach <= 0f) return@forEach
            val h = seed + p.x * 71 + p.y * 131
            // Progreso de corrosión propio de la celda (ligero desfase orgánico por celda).
            val cLocal = (corrode * (1.15f - ((h and 0x1F) / 255f)) ).coerceIn(0f, 1f) * reach
            // Baba de concentración (fase 1→2).
            drawCircle(toxic.copy(alpha = (0.14f + 0.10f * cLocal) * fade), cellPx * 0.62f, cell)
            // Manchas ácidas que van COMIÉNDOSE el bloque desde puntos aleatorios de su cara:
            // crecen con cLocal hasta cubrirlo (el desgaste visible que faltaba).
            if (cLocal > 0f) {
                repeat(4) { i ->
                    val hh = h + i * 0x9E37
                    val ox = ((hh and 0xFF) / 255f - 0.5f) * 0.72f
                    val oy = (((hh ushr 8) and 0xFF) / 255f - 0.5f) * 0.72f
                    val patchR = cellPx * (0.10f + 0.34f * cLocal) * (0.7f + 0.3f * ((hh ushr 4 and 0x7) / 7f))
                    val pc = Offset(cell.x + ox * cellPx, cell.y + oy * cellPx)
                    drawCircle(acidDark.copy(alpha = 0.55f * cLocal * fade), patchR, pc)
                    drawCircle(toxicDeep.copy(alpha = 0.40f * cLocal * fade), patchR * 0.7f, pc)
                    // Borde brillante de la mancha (el ácido "vivo" royendo).
                    drawCircle(toxic.copy(alpha = 0.35f * cLocal * fade *
                        (0.6f + 0.4f * kotlin.math.sin(t * 18f + i * 2.1f))),
                        patchR, pc, style = Stroke(width = cellPx * 0.035f))
                }
                // Burbujas de sizzle subiendo del bloque corroído.
                repeat(3) { i ->
                    val ph = ((h + i * 271) and 0xFF) / 255f
                    val bx = cell.x + (ph - 0.5f) * cellPx * 0.7f
                    val rise = ((t * 1.7f + ph) % 1f)
                    val by = cell.y + cellPx * (0.35f - rise * 0.9f)
                    drawCircle(toxic.copy(alpha = (1f - rise) * 0.55f * cLocal * fade), cellPx * (0.045f + 0.03f * (i % 2)), Offset(bx, by))
                }
            }
            // Esporas remolineando alrededor mientras la celda aún espera su rotura.
            repeat(2) { i ->
                val ph = ((h + i * 271) and 0xFF) / 255f
                val ang = (ph + t * 1.2f) * 6.28318f
                val rad = cellPx * (0.2f + 0.3f * ((t + ph) % 1f))
                val sp = Offset(cell.x + kotlin.math.cos(ang) * rad, cell.y + kotlin.math.sin(ang) * rad)
                drawCircle(toxic.copy(alpha = 0.45f * reach * fade), cellPx * 0.06f, sp)
            }
        }
    }
}

// (PODADO 2026-07-31, bloque 21) Aquí vivía `DematerializeVFX`, los jirones espectrales del
//  «Desvanecer» de Morti. La habilidad de Morti es el TEMBLEQUE desde el rework de las its. 52-54
//  (`runMortiShake`): nadie emitía ya `GameEvent.Dematerialize`, así que el VFX y su colector en
//  GameBoard eran un efecto completo que ningún jugador podía ver. Mismo fósil que los 4 de la
//  it. 63 (AcidTide / GasCloud / PanicWave / GhostBlockShift).

/** Bioma Eléctrico: cortocircuito (toque fuera de orden). Chispas rojas erráticas + flash. */
@Composable
fun CoilShortCircuitVFX(event: GameEvent.CoilShortCircuit, cellSize: Dp, onComplete: () -> Unit) {
    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) { clock.animateTo(1f, tween(460, easing = LinearEasing)); onComplete() }
    val cx = (event.position.x + 0.5f) * cellPx
    val cy = (event.position.y + 0.5f) * cellPx
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.value
        if (t >= 1f) return@Canvas
        val a = (1f - t).coerceIn(0f, 1f)
        val c = Offset(cx, cy)
        val flash = (1f - t / 0.3f).coerceIn(0f, 1f)
        if (flash > 0f) drawCircle(Color(0xFFFF5A3C).copy(alpha = 0.6f * flash), radius = cellPx * 0.7f, center = c)
        repeat(7) { i ->
            val ang = ((i * 51f) + t * 120f) * kotlin.math.PI.toFloat() / 180f
            val end = Offset(cx + kotlin.math.cos(ang) * cellPx * (0.4f + 0.8f * t), cy + kotlin.math.sin(ang) * cellPx * (0.4f + 0.8f * t))
            drawLightning(c, end, 4, cellPx * 0.16f, Color(0xFFFFC23C).copy(alpha = a), cellPx * 0.035f, i * 13 + (t * 30).toInt())
        }
    }
}

/** Bioma Eléctrico: secuencia completa → rayo en cadena recorriendo todas las bobinas. */
@Composable
fun CoilDischargeVFX(event: GameEvent.CoilDischarge, cellSize: Dp, onComplete: () -> Unit) {
    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) { clock.animateTo(1f, tween(720, easing = LinearEasing)); onComplete() }
    val pts = remember(event.id) { event.positions.map { Offset((it.x + 0.5f) * cellPx, (it.y + 0.5f) * cellPx) } }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.value
        if (t >= 1f || pts.size < 2) return@Canvas
        val a = (1f - t).coerceIn(0f, 1f)
        // El frente del rayo avanza por la cadena; se dibujan los tramos ya recorridos.
        val frontf = t * (pts.size - 1)
        for (i in 0 until pts.size - 1) {
            if (i > frontf) break
            drawLightning(pts[i], pts[i + 1], 7, cellPx * 0.22f, Color.White.copy(alpha = a), cellPx * 0.05f, i * 17)
            drawLightning(pts[i], pts[i + 1], 7, cellPx * 0.22f, Color(0xFF5FE0FF).copy(alpha = a * 0.8f), cellPx * 0.10f, i * 17 + 3)
        }
        pts.forEach { drawCircle(Color(0xCCFFE680).copy(alpha = a), radius = cellPx * 0.18f, center = it) }
    }
}

/**
 * Bioma Fuego/Volcán: lava que BAJA desde ARRIBA cubriendo las [lavaRow] filas superiores. La altura
 * anima suavemente; el borde INFERIOR (superficie) ondula (molten), con burbujas y ceniza ascendentes.
 * Cuando lavaRow==0 muestra solo una fina cresta amenazante en el borde superior del tablero, y solo
 * cuando el aviso la pide ([crestVisible]).
 */
@Composable
fun LavaOverlay(
    lavaRow: Int,
    cellSize: Dp,
    modifier: Modifier = Modifier,
    /**
     * **La próxima colocación seca hace bajar la lava** (P-65). La cresta HIERVE: se hincha, se
     * aclara y suelta brasas. Es la única telegrafía del bioma — antes la lava caía sin avisar.
     *
     * Lo decide quien llama comparando `volcanoDryTurns` con el umbral del nivel, así que no hace
     * falta ni un campo nuevo en `GameState`: la información ya estaba, solo que nadie la pintaba.
     */
    imminent: Boolean = false,
    /**
     * La cresta del techo (sin lava todavía). Antes se veía desde el primer fotograma del nivel; ahora
     * es el PRIMER tiempo del aviso y solo asoma a dos colocaciones secas del descenso.
     */
    crestVisible: Boolean = true
) {
    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }
    val crestAlpha by animateFloatAsState(
        targetValue = if (crestVisible || imminent) 1f else 0f,
        animationSpec = tween(420, easing = FastOutSlowInEasing),
        label = "lavaCrest"
    )
    // La ebullición entra y sale con suavidad: encenderla de golpe se leía como un parpadeo.
    val boil by animateFloatAsState(
        targetValue = if (imminent) 1f else 0f,
        animationSpec = tween(340, easing = FastOutSlowInEasing),
        label = "lavaBoil"
    )
    val boardW  = cellPx * GameState.GRID_SIZE
    val animLava by animateFloatAsState(
        targetValue = lavaRow.toFloat(),
        animationSpec = tween(620, easing = FastOutSlowInEasing),
        label = "lavaH"
    )
    // Reloj MONÓTONO (no reinicia cada pocos segundos → sin salto perceptible en el bucle). Avanza
    // linealmente durante ~100 min antes de reciclar (en la práctica, una sola partida nunca lo alcanza).
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            clock.snapTo(0f)
            clock.animateTo(6_000_000f, tween(6_000_000, easing = LinearEasing))
        }
    }

    // Con la bajada inminente la lava se AGITA: más oleaje y superficie hinchada media celda.
    // La HINCHAZÓN solo si YA hay lava: con `lavaRow` = 0 dibujaría media celda de lava que no
    // existe, y el aviso del primer descenso mentiría enseñando el castigo ya cumplido. Sin lava el
    // aviso vive entero en la cresta del techo (ver el Canvas).
    val waveAmp  = cellPx * (0.14f + 0.10f * boil)

    // ── LA LAVA NO BAJA DE SU LÍNEA (bug de playtest, 2026-09-03) ──────────────────────────────
    // Reporte: «la bajada de la lava tapa demasiado los bloques de arriba, tanto la animación como
    // la línea». Era literal, y se sumaban TRES cosas por debajo de `lavaRow`:
    //   · un `swell` de MEDIA CELDA hacia abajo mientras la bajada era inminente (el peor con
    //     diferencia: hinchar la superficie era el aviso de P-65, pero lo pagaba tapando la fila
    //     que el jugador necesita leer para decidir su jugada);
    //   · la cresta, que oscilaba CENTRADA en la línea y por tanto bajaba hasta 0,4·waveAmp;
    //   · el trazo del brillo, centrado en esa curva, que añadía media anchura más.
    // En total ~0,62 celdas mordiendo la parte alta de los bloques justo cuando más urge verlos.
    //
    // Ahora [boundaryY] es una línea DURA: nada se dibuja por debajo. La cresta se ancla de modo
    // que su punto más bajo —brillo incluido— aterrice exactamente ahí, y el aviso de bajada
    // inminente pasa a contarse **hacia arriba**: `boil` sigue agrandando [waveAmp], así que la
    // superficie se agita más y más alto (más las brasas y el aclarado que ya tenía). Se lee igual
    // de bien y no le quita al jugador ni un píxel de tablero.
    /** Dónde acaba la lava. Ni un píxel por debajo: ahí empieza la fila jugable. */
    val boundaryY = animLava * cellPx
    /** Pico máximo de la suma de las dos sinusoides de la cresta (1 + 0,4). */
    val waveSpan = waveAmp * 1.4f
    /**
     * Media anchura del trazo del brillo, que se dibuja centrado sobre la curva. Son DOS trazos: el
     * fijo (0,05 de ancho) y el del filo al rojo blanco cuando hierve (0,09·boil). Se toma el peor
     * de los dos para que ninguno de los dos asome por debajo de [boundaryY].
     */
    val glowHalf = cellPx * (0.025f + 0.020f * boil)
    /** Centro de la cresta: colocado para que el punto más bajo del brillo toque [boundaryY]. */
    val crestY = boundaryY - waveSpan - glowHalf

    // PERF: brushes y Paths CACHEADOS → 0 asignaciones por frame mientras la lava está QUIETA (el
    // caso común: el reloj continuo redibuja el Canvas a 60 fps SIN recomponer). Solo se reconstruyen
    // durante la bajada (animLava cambia ~620 ms) o al cambiar el tamaño de celda. Antes se asignaban
    // 1 Path×2 + 3 Brush por cada frame, permanentemente, aunque la lava no se moviera.
    val crestBrush = remember(cellPx) {
        Brush.verticalGradient(
            listOf(Color(0x99FF6A1E), Color(0x33FF3000), Color.Transparent),
            startY = 0f, endY = cellPx * 0.5f
        )
    }
    val bodyBrush = remember(boundaryY) {
        Brush.verticalGradient(
            listOf(Color(0xE62A0700), Color(0xDC7A1500), Color(0xD4C42E00)),
            startY = 0f, endY = boundaryY.coerceAtLeast(1f)
        )
    }
    val surfBrush = remember(boundaryY, cellPx) {
        Brush.verticalGradient(
            listOf(Color(0xD4C42E00), Color(0xE0FF6A1E)),
            startY = boundaryY - cellPx, endY = boundaryY
        )
    }
    val surfPath = remember { Path() }
    val glowPath = remember { Path() }
    // P-67: burbujas, ceniza y brasas son AMBIENTE — se recortan con «Reducir efectos». La
    // superficie, la cresta y la hinchazón del aviso no: son la información del bioma.
    val bubbleCount = fxCount(5)
    val ashCount = fxCount(6)
    val emberCount = fxCount(9)
    val crestEmberCount = fxCount(7)
    val glowStroke = remember(cellPx) { Stroke(width = cellPx * 0.05f, cap = StrokeCap.Round) }

    // Capa propia (auditoría de fluidez 2026-09-29): vive todo el capítulo del Volcán.
    Canvas(modifier = modifier.fillMaxSize().graphicsLayer()) {
        val bodyH = boundaryY
        // El reloj solo se lee si algo de lo que se va a pintar depende de él. Con la lava a cero y sin
        // cresta hirviendo (el arranque de cada nivel) este lienzo se invalidaba cada fotograma para
        // no pintar nada, o solo la cresta quieta.
        val needsClock = bodyH > 0.5f || (bodyH < cellPx * 0.4f && crestAlpha > 0.01f && boil > 0.01f)
        val ms = if (needsClock) clock.value else 0f
        val phase = ms / 520f

        // Cresta amenazante en el borde superior del tablero cuando aún hay poca lava (lavaRow ≈ 0).
        if (bodyH < cellPx * 0.4f && crestAlpha > 0.01f) {
            drawRect(brush = crestBrush, topLeft = Offset(0f, 0f), size = Size(boardW, cellPx * 0.5f), alpha = crestAlpha)
            // AVISO DEL PRIMER DESCENSO (P-65): sin lava todavía, el aviso es la cresta ARDIENDO —
            // una banda que late y gotea brasas por el techo. Es el único momento del bioma en que
            // el jugador puede evitar la lava del todo, así que tiene que verse.
            if (boil > 0.01f) {
                val beat = 0.55f + 0.45f * kotlin.math.sin(ms / 150f)
                drawRect(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFFFC24D).copy(alpha = 0.55f * boil * beat),
                            Color(0xFFFF6A1E).copy(alpha = 0.30f * boil),
                            Color.Transparent
                        ),
                        startY = 0f, endY = cellPx * 0.85f
                    ),
                    topLeft = Offset(0f, 0f), size = Size(boardW, cellPx * 0.85f)
                )
                for (i in 0 until crestEmberCount) {
                    val seed = i * 0x45D9F3 + 7
                    val ex = ((seed % 991) / 991f) * boardW
                    val cyclePos = ((ms / 700f) + i * 0.14f) % 1f
                    val env = kotlin.math.sin(cyclePos * 3.14159f)
                    drawCircle(
                        Color(0xFFFF7A18).copy(alpha = 0.8f * env * boil),
                        radius = cellPx * (0.03f + 0.02f * env),
                        center = Offset(ex, cyclePos * cellPx * 0.8f)
                    )
                }
            }
        }
        if (bodyH <= 0.5f) return@Canvas

        // Cuerpo de lava (degradado caliente) hasta justo por encima del borde ondulante.
        drawRect(
            brush = bodyBrush,
            topLeft = Offset(0f, 0f), size = Size(boardW, crestY.coerceAtLeast(0f))
        )

        // Superficie ondulante (molten) — DOS sinusoides superpuestas (una lenta + una corta) para un
        // oleaje más orgánico. Función compartida con el brillo para que coincidan exactamente.
        fun surfaceYAt(x: Float): Float {
            val t = x / boardW
            val w1 = kotlin.math.sin(t * 6.2832f * 2.2f + phase) * waveAmp
            val w2 = kotlin.math.sin(t * 6.2832f * 4.7f - phase * 1.7f) * (waveAmp * 0.4f)
            return crestY + w1 + w2
        }
        val waveSegments = 36
        surfPath.rewind()
        surfPath.moveTo(0f, 0f)
        surfPath.lineTo(0f, boundaryY)
        for (segment in 0..waveSegments) {
            val x = boardW * segment / waveSegments.toFloat()
            surfPath.lineTo(x, surfaceYAt(x))
        }
        surfPath.lineTo(boardW, 0f)
        surfPath.close()
        drawPath(surfPath, brush = surfBrush)

        // Brillo de la superficie (misma curva), reutilizando un único Path.
        glowPath.rewind()
        for (segment in 0..waveSegments) {
            val x = boardW * segment / waveSegments.toFloat()
            val y = surfaceYAt(x)
            if (segment == 0) glowPath.moveTo(x, y) else glowPath.lineTo(x, y)
        }
        drawPath(glowPath, color = Color(0xE6FFC04D), style = glowStroke)

        // Burbujas dentro de la lava. Alfa con envolvente sin(cyclePos·π) → aparecen y desaparecen
        // suavemente (sin "pop" al reciclar en el borde del bucle).
        for (i in 0 until bubbleCount) {
            val seed = i * 0x9E3779
            val bx = ((seed % 1000) / 1000f) * boardW
            val cyclePos = ((ms / (900f + (seed % 500))) + i * 0.2f) % 1f
            val by = boundaryY - cyclePos * (boundaryY * 0.8f)
            val env = kotlin.math.sin(cyclePos * 3.14159f)        // 0 en extremos, 1 en el centro
            val r = cellPx * (0.05f + 0.05f * env)
            if (by in 0f..boundaryY) {
                drawCircle(Color(0xFFFFB020).copy(alpha = 0.5f * env), radius = r, center = Offset(bx, by))
            }
        }

        // Ceniza ascendente por encima de la lava (misma envolvente suave).
        for (i in 0 until ashCount) {
            val seed = i * 0x27D4EB + 13
            val ax = ((seed % 997) / 997f) * boardW
            val cyclePos = ((ms / 1700f) + i * 0.17f) % 1f
            val ay = boundaryY - cyclePos * cellPx * 2.2f
            val a = kotlin.math.sin(cyclePos * 3.14159f) * 0.5f
            if (ay > 0f) drawCircle(Color(0xFF3A3A3A).copy(alpha = a), radius = cellPx * 0.04f, center = Offset(ax, ay))
        }

        // ── AVISO (P-65): la lava HIERVE porque la próxima colocación seca la hace bajar. ──
        //    Brasas que SALTAN de la cresta hacia abajo (a donde va a llegar) + filo al rojo blanco.
        //    Solo cuesta algo cuando de verdad está a punto: con boil = 0 ni entra.
        if (boil > 0.01f) {
            // El filo se pone al rojo BLANCO. Reutiliza `glowPath` (ya dibujado arriba, misma curva):
            // rebobinar y repintar cuesta menos que un segundo Path cacheado que casi nunca se usa.
            val edgeGlow = Color(0xFFFFD08A).copy(alpha = 0.55f * boil)
            glowPath.rewind()
            for (segment in 0..waveSegments) {
                val x = boardW * segment / waveSegments.toFloat()
                val y = surfaceYAt(x)
                if (segment == 0) glowPath.moveTo(x, y) else glowPath.lineTo(x, y)
            }
            drawPath(glowPath, color = edgeGlow, style = Stroke(width = cellPx * 0.09f * boil, cap = StrokeCap.Round))
            for (i in 0 until emberCount) {
                val seed = i * 0x45D9F3 + 7
                val ex = ((seed % 991) / 991f) * boardW
                val cyclePos = ((ms / 620f) + i * 0.11f) % 1f
                // Salta hacia ABAJO: enseña la fila que se va a comer.
                val ey = surfaceYAt(ex) + cyclePos * cellPx * 0.85f
                val env = kotlin.math.sin(cyclePos * 3.14159f)
                drawCircle(
                    Color(0xFFFF7A18).copy(alpha = 0.85f * env * boil),
                    radius = cellPx * (0.035f + 0.025f * env),
                    center = Offset(ex, ey)
                )
            }
        }
    }
}

/**
 * **Volcán / LA ERUPCIÓN** (P-65): la lava acaba de comerse una fila y se ve el golpe.
 *
 * `LavaOverlay` pinta el ESTADO (dónde está la lava, con su tween de 620 ms); esto pinta el MOMENTO.
 * Sin esto, el único castigo del juego que llega sin aviso llegaba además sin impacto: la superficie
 * se deslizaba y no pasaba nada más.
 *
 * Tres tiempos en 620 ms: **frente** que barre la fila nueva → **salpicadura** de gotas que rebotan
 * hacia arriba desde el filo → **brasas** que se apagan subiendo. Si además se llevó Teikas por
 * delante ([GameEvent.LavaSurge.burnedBodies]) el frente entra más brillante y con más gotas — la
 * fila que te cuesta un cuerpo tiene que verse más cara que la que solo cuesta espacio.
 */
@Composable
fun LavaSurgeVFX(
    event: GameEvent.LavaSurge,
    cellSize: Dp,
    onComplete: () -> Unit
) {
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val boardW = cellPx * GameState.GRID_SIZE
    val violent = event.burnedBodies > 0

    // P-67: la salpicadura es adorno; el FRENTE que barre la fila se queda entero (es el castigo).
    val dropCount = fxCount(if (violent) 16 else 11)
    val drops = remember(event.id, dropCount) {
        val h = event.id.hashCode()
        List(dropCount) { i ->
            val s = h + i * 0x9E3779
            LavaDrop(
                x = ((s ushr 3 and 0x3FF) / 1023f),
                rise = 0.55f + (s ushr 6 and 0x3F) / 70f,
                drift = ((s ushr 12 and 0x1F) - 16f) / 60f,
                size = 0.045f + (s ushr 9 and 0x0F) / 260f
            )
        }
    }

    val surgeEmbers = fxCount(7)
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        clock.animateTo(1f, tween(LAVA_SURGE_MS, easing = LinearEasing))
        onComplete()
    }

    // Filo NUEVO de la lava: donde queda tras tragarse la fila.
    val edgeY = event.toRow * cellPx
    val fromY = event.fromRow * cellPx

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.value
        if (t <= 0f || t >= 1f) return@Canvas

        // ── 1. EL FRENTE: una banda caliente barre de la fila vieja a la nueva y se apaga. ──
        val sweep = (t / 0.34f).coerceIn(0f, 1f)
        val sweepE = 1f - (1f - sweep) * (1f - sweep)
        if (sweep < 1f || t < 0.5f) {
            val frontY = fromY + (edgeY - fromY) * sweepE
            val bandH = cellPx * (0.55f + 0.35f * (1f - sweepE))
            val a = (1f - t / 0.5f).coerceIn(0f, 1f)
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        Color(0xFFFF8A24).copy(alpha = 0.55f * a * (if (violent) 1.3f else 1f)),
                        Color(0xFFFFE0A3).copy(alpha = 0.85f * a)
                    ),
                    startY = frontY - bandH, endY = frontY
                ),
                topLeft = Offset(0f, (frontY - bandH).coerceAtLeast(0f)),
                size = Size(boardW, bandH.coerceAtMost(frontY.coerceAtLeast(0f)))
            )
            // Filo al rojo blanco justo en la cabeza del frente.
            drawLine(
                Color(0xFFFFF3D6).copy(alpha = 0.9f * a),
                Offset(0f, frontY), Offset(boardW, frontY),
                strokeWidth = cellPx * 0.07f
            )
        }

        // ── 2. SALPICADURA: gotas que saltan del filo y caen de vuelta (parábola). ──
        val splash = ((t - 0.10f) / 0.90f).coerceIn(0f, 1f)
        if (splash > 0f) {
            val alpha = (1f - splash * splash)
            drops.forEach { d ->
                val dx = d.x * boardW + d.drift * cellPx * splash * 6f
                // Sube y vuelve a caer: -4h(t-t²) es una parábola con cima en t = 0,5.
                val dy = edgeY - d.rise * cellPx * 4f * (splash - splash * splash)
                drawCircle(
                    Color(0xFFFFA640).copy(alpha = 0.9f * alpha),
                    radius = d.size * cellPx * (1f - 0.3f * splash),
                    center = Offset(dx, dy)
                )
            }
        }

        // ── 3. BRASAS que se apagan subiendo (la cola del golpe). ──
        val embers = ((t - 0.25f) / 0.75f).coerceIn(0f, 1f)
        if (embers > 0f) {
            for (i in 0 until surgeEmbers) {
                val seed = i * 0x27D4EB + 91
                val ex = ((seed % 983) / 983f) * boardW
                val ey = edgeY - embers * cellPx * (1.4f + (seed % 5) * 0.25f)
                val a = (1f - embers) * 0.7f
                if (ey > 0f) {
                    drawCircle(Color(0xFFFF6A1E).copy(alpha = a), cellPx * 0.045f, Offset(ex, ey))
                }
            }
        }
    }
}

/** Una gota de la salpicadura de la erupción. */
private data class LavaDrop(
    val x: Float,
    val rise: Float,
    val drift: Float,
    val size: Float
)


// ── Batch Particle Explosion — TODAS las GameEvent.Particles en UN solo Canvas/reloj ──────────────
// PERF: antes cada GameEvent.Particles montaba su propio ParticleExplosion (Canvas full-screen +
// Animatable independiente). En momentos épicos (Mega Teika limpiando el tablero, color blast,
// cascadas de fusión) se emiten 10-30 a la vez → 10-30 Canvas animándose en paralelo = tirones.
// Aquí un único Canvas dibuja todas las ráfagas vivas con un solo reloj de tween (mismo patrón que
// BatchLineCellShatterVFX). Cada ráfaga se purga al expirar → la lista no crece sin límite.

private const val PARTICLE_LIFE_MS = 800f
private const val PARTICLE_BURST_COUNT = 15

private class BatchExplosionParticle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val color: Color
)

private class ParticleBurstData(
    val absoluteStartMs: Long,
    val center: Offset,
    val particles: List<BatchExplosionParticle>
)

@Composable
fun BatchParticleExplosionVFX(
    events: List<GameEvent.Particles>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit
) {
    if (events.isEmpty()) return

    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }

    val clock = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Datos de cada ráfaga viva. Se purgan al expirar (a diferencia del shatter, que las retiene).
    val bursts    = remember { mutableMapOf<String, ParticleBurstData>() }
    val scheduled = remember { mutableSetOf<String>() }

    SideEffect {
        val nowMs = clock.value.toLong()
        events.forEach { event ->
            if (event.id !in bursts) {
                val rnd = Random(event.id.hashCode())
                val center = Offset((event.position.x + 0.5f) * cellPx, (event.position.y + 0.5f) * cellPx)
                val list = List(PARTICLE_BURST_COUNT) {
                    val angle = rnd.nextFloat() * 2f * PI.toFloat()
                    val speed = rnd.nextFloat() * 150f + 50f
                    val col = when {
                        event.colorType == -1 ->
                            listOf(Color.White, Color.Yellow, Color.Cyan)[rnd.nextInt(3)]
                        // Pantano Tóxico: limpiar veneno → salpicadura ácida (identidad del bioma).
                        event.colorType == com.cukiti.teikafusion.model.ToxicBiomeRules.POISON_CLEAR_PARTICLE_COLOR ->
                            listOf(
                                Color(0xFFB6FF6E), Color(0xFF7CEA5F), Color(0xFF39FF14),
                                Color(0xFFE8FFB0), Color(0xFF4FB52A)
                            )[rnd.nextInt(5)]
                        else -> {
                            val palette = pieceColors[event.colorType % pieceColors.size]
                            palette[rnd.nextInt(palette.size)]
                        }
                    }
                    BatchExplosionParticle(angle, speed, rnd.nextFloat() * 15f + 10f, col)
                }
                bursts[event.id] = ParticleBurstData(nowMs, center, list)
            }
            if (event.id !in scheduled) {
                scheduled.add(event.id)
                val data = bursts[event.id] ?: return@forEach
                val remainingMs = (data.absoluteStartMs + PARTICLE_LIFE_MS.toLong() - nowMs).coerceAtLeast(0L)
                val id = event.id
                scope.launch {
                    if (remainingMs > 0) delay(remainingMs)
                    // Mismo hilo (Main) que el draw → purga segura entre frames.
                    bursts.remove(id)
                    scheduled.remove(id)
                    onEventComplete(id)
                }
            }
        }
    }

    // Reloj de FRAMES único; un solo redibujo del Canvas mientras haya ráfagas.
    LaunchedEffect(Unit) {
        clock.animateTo(100_000f, tween(100_000, easing = LinearEasing))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val nowMs = clock.value.toLong()
        bursts.values.forEach { data ->
            val progress = ((nowMs - data.absoluteStartMs) / PARTICLE_LIFE_MS).coerceIn(0f, 1f)
            if (progress >= 1f) return@forEach
            val alpha = (1f - progress).coerceIn(0f, 1f)
            for (i in data.particles.indices) {
                val p = data.particles[i]
                val currentSpeed = p.speed * (1f - progress)
                val distance = p.speed * progress + (currentSpeed * progress * 0.5f)
                val x = data.center.x + cos(p.angle) * distance
                val y = data.center.y + sin(p.angle) * distance + (progress * 100f)   // gravedad
                val sz = p.size * (1f - progress * 0.5f)
                drawCircle(p.color.copy(alpha = alpha), sz, Offset(x, y))
            }
        }
    }
}

@Composable
fun BatchLineCellShatterVFX(
    events: List<GameEvent.LineCellShatter>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
    /** P-283: la firma háptica de la rotura. La demo de la vitrina la apaga: allí nadie está jugando. */
    haptics: Boolean = true,
) {
    if (events.isEmpty()) return

    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }
    // Alto de la pantalla en px: el skin DERRUMBE hace caer el cubo hasta SALIR del juego por abajo
    // (pedido 2026-07-20). El Canvas nace en la esquina del tablero, así que con la altura completa
    // de la pantalla se garantiza que el cubo abandona el viewport aunque rompiera en la fila 0.
    val screenHeightPx = with(density) {
        androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx()
    }

    // Skin activo: la destrucción usa su paleta y su forma (cubos bisel vs jelly redondeado).
    // La ROTURA es parte de la identidad de cada skin (decisión 2026-07-20): cada una tiene la suya.
    val skin       = LocalBlockSkin.current
    val isBevel    = skin.style == BlockTileStyle.BEVEL
    val isSpectre  = skin.style == BlockTileStyle.SPECTRE  // rotura «volutas que suben» (P-251 5/7)
    val isSky      = skin.style == BlockTileStyle.CONSTELLATION  // rotura «estrellas fugaces» (P-251 6/7)
    val isVoid     = skin.style == BlockTileStyle.VOID  // rotura «implosión + destello» (P-251 7/7)
    // P-283: los bloques de bioma llevan además una primera capa común (fogonazo + onda).
    val premiumBreak = Haptics.hasBlockSignature(skin.style)
    val isMetal    = skin.style == BlockTileStyle.METAL  // rotura «remaches que saltan + chapa que cae»
    val isNeon     = skin.style == BlockTileStyle.NEON   // rotura «tubo que se funde + chispas» (P-410)
    val isGold     = skin.style == BlockTileStyle.GOLD   // rotura «gema que salta + monedas» (P-410)
    val isCrystal  = skin.style == BlockTileStyle.FACETED   // rotura «esquirlas de gema + destellos»
    val isWood     = skin.style == BlockTileStyle.WOOD      // rotura «astillas + serrín» (P-251 2/7)
    val isLava     = skin.style == BlockTileStyle.LAVA      // rotura «ascuas + roca» (P-251 3/7)
    val isIce      = skin.style == BlockTileStyle.ICE       // rotura «trozos de hielo + vaho» (P-251 4/7)
    val isFall     = skin.id == BlockSkins.fall3d.id   // rotura "desprendimiento + caída 3D"
    // Usar el redondeo REAL del skin para el bloque que colapsa (si no, un bloque redondeado se
    // «pondría cuadrado» al saltar a un radio fijo menor).
    val blockCornerFr = if (isBevel) 0.10f else skin.cornerFraction
    val fragCornerFr  = if (isBevel) 0.12f else 0.35f

    // P-410: trazos del tubo de neón y del engaste de la gema, una vez por tamaño de casilla.
    val neonHaloStroke = remember(cellPx) { Stroke(width = cellPx * 0.20f) }
    val neonCoreStroke = remember(cellPx) { Stroke(width = cellPx * 0.04f) }
    val goldGemStroke  = remember(cellPx) { Stroke(width = cellPx * 0.035f) }

    // Esquirla del Cristal: UN Path reutilizado (reset + 3 vértices por esquirla) → cero asignaciones
    // por frame aunque revienten líneas enteras a la vez.
    val shardPath = remember { androidx.compose.ui.graphics.Path() }

    // Estrellas-objetivo: el bloque-cofre en espera se dibuja FIEL (PNG de la gema a tamaño real).
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val starBitmapsShatter = remember(ctx) { targetStarBitmaps(ctx) }

    // Reloj unificado: ms desde que este composable fue creado (0 → ~2000)
    val clock   = remember { Animatable(0f) }
    val scope   = rememberCoroutineScope()

    // Datos persistentes de cada evento (sobreviven a su eliminación de la lista)
    val eventData   = remember { mutableMapOf<String, ShatterEventData>() }
    val scheduledIds = remember { mutableSetOf<String>() }

    // P-283: la firma háptica de la rotura, una vez por pasada (no por casilla) y a la vez que la
    // primera casilla estalla. Solo los bloques de bioma la tienen.
    val breakHapticAt = remember { longArrayOf(Long.MIN_VALUE) }

    // Registrar eventos nuevos y programar sus completions
    SideEffect {
        val nowMs = clock.value.toLong()
        val firstNewDelay = events.filter { it.id !in eventData }.minOfOrNull { it.delayMs }
        if (haptics && firstNewDelay != null && Haptics.hasBlockSignature(skin.style) &&
            nowMs + firstNewDelay - breakHapticAt[0] > 150L
        ) {
            breakHapticAt[0] = nowMs + firstNewDelay
            val style = skin.style
            scope.launch {
                if (firstNewDelay > 0) delay(firstNewDelay.toLong())
                Haptics.blockBreak(style)
            }
        }
        events.forEach { event ->
            if (event.id !in eventData) {
                val h = event.id.hashCode()
                eventData[event.id] = ShatterEventData(
                    absoluteStartMs = nowMs + event.delayMs,
                    seeds = List(4) { i ->
                        val s        = h + i * 0x9E3779
                        val ang      = (i * 90f + (s and 0x1F) - 16f) * kotlin.math.PI.toFloat() / 180f
                        val speed    = 0.85f + (s ushr 3 and 0x3F) / 70f
                        val sizeMul  = 0.26f + (s ushr 6 and 0x1F) / 120f
                        val spin     = (if ((s ushr 2) and 1 == 0) 1f else -1f) * (200f + (s ushr 4 and 0x7F))
                        val gravity  = 1.1f + (s ushr 9 and 0x1F) / 26f
                        ShatterCube(ang, speed, sizeMul, spin, gravity)
                    },
                    position    = event.position,
                    colorType   = event.colorType,
                    targetColor = event.targetColor,
                    bonusSparkle = event.bonusSparkle,
                    whiteFlash = event.whiteFlash
                )
            }
            if (event.id !in scheduledIds) {
                scheduledIds.add(event.id)
                val data        = eventData[event.id] ?: return@forEach
                val remainingMs = (data.absoluteStartMs + SHATTER_LIFE_MS.toLong() - nowMs).coerceAtLeast(0L)
                scope.launch {
                    if (remainingMs > 0) delay(remainingMs)
                    onEventComplete(event.id)
                }
            }
        }
    }

    // Avanza el reloj en cada frame — actualiza una sola vez, redibuja 1 Canvas
    LaunchedEffect(Unit) {
        clock.animateTo(100_000f, tween(100_000, easing = LinearEasing))
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val nowMs = clock.value.toLong()

        eventData.values.forEach { data ->
            val rawT = (nowMs - data.absoluteStartMs) / SHATTER_LIFE_MS
            if (rawT >= 1f) return@forEach
            // rawT < 0 → bloque esperando su turno de destruirse (efecto dominó).
            // Se dibuja estático a tamaño/alpha completo hasta que arranque la animación.
            val t = rawT.coerceAtLeast(0f)

            // Misma paleta que en reposo — la decide `woodPalette` (ver STAR_BLOCK_OVERRIDES_COLOR).
            val palette    = woodPalette(skin, data.colorType, data.targetColor != null)
            val baseColor  = palette[0]
            val lightColor = palette[1]
            val darkColor  = palette[2]

            val cx       = (data.position.x + 0.5f) * cellPx
            val cy       = (data.position.y + 0.5f) * cellPx
            val cellHalf = cellPx / 2f
            val cornerPx = cellPx * blockCornerFr
            val center   = Offset(cx, cy)

            // ── Bloque: pop de anticipación → colapso → desvanecido + flash ──
            // DERRUMBE: el bloque no se desvanece en su celda — en cuanto arranca la rotura
            // desaparece de la celda y lo sustituye el CUBO ENTERO cayendo (sección de fragmentos).
            val blockAlpha = when {
                isFall    -> if (t < 0.04f) 1f else 0f
                t < 0.32f -> 1f
                t < 0.50f -> 1f - (t - 0.32f) / 0.18f
                else      -> 0f
            }
            if (blockAlpha > 0f) {
                val tl = Offset(cx - cellHalf, cy - cellHalf)
                val full = androidx.compose.ui.geometry.Size(cellPx, cellPx)
                val corner = androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx)
                val flashAlpha = (when {
                    t < 0.10f -> t / 0.10f
                    t < 0.26f -> 1f - (t - 0.10f) / 0.16f
                    else      -> 0f
                }).coerceIn(0f, 1f) * 0.85f

                // Cada skin conserva su DISEÑO real (bevel/jelly/extrusionado) y NO se agranda:
                // se mantiene a tamaño normal y solo encoge al final antes de reventar.
                val s = if (t < 0.30f) 1f else 1f - 0.78f * ((t - 0.30f) / 0.20f).coerceIn(0f, 1f)
                withTransform({ scale(s, s, center) }) {
                    drawStyledTile(
                        skin.style, tl, full,
                        baseColor, lightColor, darkColor,
                        skin.cornerFraction, blockAlpha
                    )
                    // Estrella-objetivo a TAMAÑO COMPLETO mientras el bloque espera su turno del
                    // dominó (rawT < 0). En el instante de reventar deja de dibujarse aquí y el
                    // vuelo de TargetCollected la recoge — la gema nunca "encoge" antes de volar.
                    val tcStar = data.targetColor
                    if (tcStar != null && rawT < 0f) {
                        starBitmapsShatter[tcStar]?.let { bmp ->
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(Color(0xD9FFF1B8), Color(0x33FFDF8E), Color(0x00FFDF8E)),
                                    center = center, radius = cellPx * 0.6f
                                ),
                                radius = cellPx * 0.6f, center = center
                            )
                            val starW = cellPx * skin.starScale
                            val starH = starW * bmp.height.toFloat() / bmp.width.toFloat()
                            val starCy = center.y + cellPx * skin.starOffsetYFraction
                            drawImage(
                                image = bmp,
                                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                srcSize = androidx.compose.ui.unit.IntSize(bmp.width, bmp.height),
                                dstOffset = androidx.compose.ui.unit.IntOffset(
                                    (center.x - starW / 2f).roundToInt(), (starCy - starH / 2f).roundToInt()
                                ),
                                dstSize = androidx.compose.ui.unit.IntSize(starW.roundToInt(), starH.roundToInt())
                            )
                        }
                    }
                    // TELEGRAPH: mientras el bloque espera su turno (rawT < 0) se oscurece
                    // progresivamente en el último tramo — el beat de anticipación pre-clear.
                    if (rawT < 0f) {
                        val waitLeft = (data.absoluteStartMs - nowMs).toFloat()
                        val darkT = (1f - waitLeft / SHATTER_TELEGRAPH_MS).coerceIn(0f, 1f)
                        if (darkT > 0f) {
                            drawRoundRect(
                                Color.Black.copy(alpha = SHATTER_TELEGRAPH_MAX_DARKEN * darkT),
                                tl, full, corner
                            )
                            // BEAT ARCOÍRIS pre-clear (video Block Blast, pedido 2026-07-20): junto
                            // al oscurecido, un marco de color que RECORRE el arcoíris — el matiz
                            // avanza con el tiempo y se desfasa por celda, así la línea entera se
                            // lee como una onda multicolor que "carga" justo antes de reventar.
                            // Color.hsv = value class, cero allocs por frame.
                            val hue = ((nowMs * 0.55f + (data.position.x + data.position.y) * 42f) % 360f + 360f) % 360f
                            val rainbow = Color.hsv(hue, 0.85f, 1f)
                            val frameW = cellPx * 0.06f
                            drawRoundRect(
                                rainbow.copy(alpha = 0.85f * darkT),
                                tl, full, corner,
                                style = Stroke(width = frameW)
                            )
                            drawRoundRect(
                                Color.White.copy(alpha = 0.45f * darkT),
                                tl, full, corner,
                                style = Stroke(width = frameW * 0.4f)
                            )
                            // Glint de CONTRIBUCIÓN al combo: cruz dorada pequeña en la esquina
                            // superior derecha de la celda mientras espera — marca "esta celda
                            // suma al combo" (referencia Block Blast). Barato: 2 líneas + punto.
                            if (data.bonusSparkle) {
                                val sx = cx + cellHalf * 0.45f
                                val sy = cy - cellHalf * 0.45f
                                val sr = cellPx * 0.15f
                                val sw = cellPx * 0.032f
                                val gold = Color(0xFFFFE08A).copy(alpha = darkT)
                                drawLine(gold, Offset(sx - sr, sy), Offset(sx + sr, sy), strokeWidth = sw)
                                drawLine(gold, Offset(sx, sy - sr), Offset(sx, sy + sr), strokeWidth = sw)
                                drawCircle(Color.White.copy(alpha = 0.9f * darkT), cellPx * 0.05f, Offset(sx, sy))
                            }
                        }
                    }
                    // FOGONAZO BLANCO de la celda. Lo apaga quien emite el evento
                    // (`LineCellShatter.whiteFlash`): WALLBLOCK rompe grupos enteros en cascada y
                    // decenas de estos seguidos dejaban la pantalla lavada de blanco.
                    if (flashAlpha > 0f && data.whiteFlash) {
                        drawRoundRect(Color.White.copy(alpha = flashAlpha * blockAlpha), tl, full, corner)
                    }
                }
            }

            // ── Fragmentos ────────────────────────────────────────────────────
            if (rawT > 0f) {
                val shardT = ((t - 0.08f) / 0.92f).coerceIn(0f, 1f)
                if (shardT > 0f) {
                    val fragAlpha = when {
                        shardT < 0.12f -> shardT / 0.12f
                        shardT > 0.62f -> ((1f - shardT) / 0.38f).coerceIn(0f, 1f)
                        else           -> 1f
                    }

                    // P-283 · PRIMERA CAPA de la rotura premium, común a los bloques de bioma: un
                    // fogonazo del color del bloque que se apaga y una onda que se abre. Encima va
                    // la rotura propia de cada uno (astillas, ascuas, trozos de hielo…).
                    if (premiumBreak && shardT < 0.35f) {
                        val k = shardT / 0.35f
                        val fl = (1f - k)
                        drawCircle(lightColor.copy(alpha = 0.55f * fl * fl), radius = cellPx * (0.35f + 0.25f * k), center = center)
                        drawCircle(
                            baseColor.copy(alpha = 0.7f * fl),
                            radius = cellPx * (0.4f + 0.75f * k), center = center,
                            style = Stroke(width = cellPx * 0.07f * fl)
                        )
                    }

                    if (isFall) {
                        // ── DERRUMBE: el bloque ENTERO se desprende, salta, gira y CAE ──
                        // (referencia: videoblock.mp4). Rework 2026-07-20: la caída ya no muere a
                        // media altura — el cubo recorre TODA la pantalla y se va por debajo del
                        // borde inferior del juego. Por eso NO se desvanece (antes se apagaba a
                        // partir de shardT 0.80, así que se esfumaba en el aire en vez de salir):
                        // desaparece porque abandona el viewport, que es lo que pide la animación.
                        val p0 = data.seeds[0]
                        // Impulso lateral aleatorio (izq/der según semilla) + salto inicial.
                        val dir  = if (cos(p0.ang) >= 0f) 1f else -1f
                        val vx   = dir * (0.35f + p0.speed * 0.30f)
                        val fallX = cx + vx * cellPx * shardT
                        // Parábola calibrada en px de PANTALLA (no en celdas): a shardT=1 el cubo ya
                        // está por debajo del borde inferior salga de la fila que salga.
                        val fallSpan = screenHeightPx + cellPx * 2f
                        val fallY = cy - 1.15f * cellPx * shardT + fallSpan * shardT * shardT
                        // CULL: en cuanto el cubo pasa el borde inferior de la pantalla ya no hay
                        // nada que dibujar, pero el evento sigue vivo hasta SHATTER_LIFE_MS. Sin este
                        // corte se seguían rasterizando cubo + escombros fuera de cuadro durante la
                        // mayor parte de la animación (los de las filas bajas salen casi al instante).
                        if (fallY - cellHalf > screenHeightPx) return@forEach
                        val rot   = p0.spin * shardT * 0.9f
                        val cubeAlpha = 1f
                        val tlF  = Offset(fallX - cellHalf, fallY - cellHalf)
                        val fullF = androidx.compose.ui.geometry.Size(cellPx, cellPx)
                        withTransform({ rotate(rot, Offset(fallX, fallY)) }) {
                            // Variante sin asignaciones: el cubo cae girando y el degradado no se
                            // percibe, pero sí costaba un Brush por cubo y frame.
                            drawFallingCube(
                                tlF, fullF,
                                baseColor, lightColor, darkColor,
                                skin.cornerFraction, cubeAlpha
                            )
                        }
                        // Escombros: 2 mini-cubos que saltan al lado contrario (lectura de impacto).
                        // Caen algo más despacio que el cubo grande, pero también salen de pantalla.
                        for (i in 1..2) {
                            val p = data.seeds[i]
                            val dd  = p.speed * cellPx * shardT * 0.55f
                            val ex  = cx - dir * dd * 0.6f + cos(p.ang) * dd * 0.3f
                            val ey  = cy - 0.7f * cellPx * shardT + fallSpan * 0.82f * shardT * shardT
                            val hw  = cellHalf * p.sizeMul * 0.7f
                            if (hw <= 0.5f) continue
                            if (ey - hw > screenHeightPx) continue   // ya fuera de cuadro
                            val crE = androidx.compose.ui.geometry.CornerRadius(hw * 0.14f, hw * 0.14f)
                            rotate(p.spin * shardT, Offset(ex, ey)) {
                                drawRoundRect(
                                    baseColor.copy(alpha = cubeAlpha),
                                    Offset(ex - hw, ey - hw),
                                    androidx.compose.ui.geometry.Size(hw * 2f, hw * 2f), crE
                                )
                                drawRoundRect(
                                    darkColor.copy(alpha = cubeAlpha * 0.85f),
                                    Offset(ex - hw, ey + hw * 0.35f),
                                    androidx.compose.ui.geometry.Size(hw * 2f, hw * 0.65f), crE
                                )
                            }
                        }
                        // Puff de polvo en la celda de origen: el "desprendimiento" se lee ahí.
                        val dustT = (shardT / 0.5f).coerceIn(0f, 1f)
                        if (dustT < 1f) {
                            drawCircle(
                                Color.White.copy(alpha = (1f - dustT) * 0.35f),
                                radius = cellPx * (0.18f + 0.35f * dustT),
                                center = center,
                                style = Stroke(width = cellPx * 0.05f * (1f - dustT))
                            )
                        }
                    } else if (isNeon) {
                        // ── Neón (P-410): el TUBO SE FUNDE — parpadea apagándose y salta en chispas ──
                        // El parpadeo es irregular (encendido, casi apagado, encendido…) y se extingue
                        // del todo a los 7/10 de la rotura: el «tubo fundido» de cualquier letrero.
                        val die = (shardT / 0.7f).coerceIn(0f, 1f)
                        if (die < 1f) {
                            val phase = (shardT * 22f + data.seeds[0].ang * 3f).toInt()
                            val flick = (if (phase % 3 == 0) 0.12f else 1f) * (1f - die)
                            val inset = cellPx * 0.14f
                            val tubeTl = Offset(cx - cellHalf + inset, cy - cellHalf + inset)
                            val tubeSz = androidx.compose.ui.geometry.Size(cellPx - inset * 2f, cellPx - inset * 2f)
                            val tubeCr = androidx.compose.ui.geometry.CornerRadius(cellPx * 0.19f, cellPx * 0.19f)
                            drawRoundRect(baseColor.copy(alpha = 0.35f * flick), tubeTl, tubeSz, tubeCr, style = neonHaloStroke)
                            drawRoundRect(
                                androidx.compose.ui.graphics.lerp(lightColor, Color.White, 0.6f).copy(alpha = flick),
                                tubeTl, tubeSz, tubeCr, style = neonCoreStroke
                            )
                        }
                        data.seeds.forEach { p ->
                            val d = p.speed * cellPx * 0.9f * shardT
                            val sx = cx + cos(p.ang) * d
                            val sy = cy + sin(p.ang) * d + p.gravity * cellPx * shardT * shardT * 0.8f
                            val sa = (1f - shardT) * fragAlpha
                            if (sa > 0f) {
                                val tail = cellPx * 0.24f * (1f - shardT)
                                drawLine(
                                    lightColor.copy(alpha = sa),
                                    Offset(sx - cos(p.ang) * tail, sy - sin(p.ang) * tail), Offset(sx, sy),
                                    strokeWidth = cellPx * 0.04f, cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                drawCircle(Color.White.copy(alpha = sa), cellPx * 0.03f, Offset(sx, sy))
                            }
                        }
                    } else if (isGold) {
                        // ── Dorado (P-410): la GEMA salta del engaste y caen MONEDAS girando ──
                        val p0 = data.seeds[0]
                        val dir = if (cos(p0.ang) >= 0f) 1f else -1f
                        val gx = cx + dir * cellPx * 0.5f * shardT
                        val gy = cy - cellPx * 0.9f * shardT + cellPx * 2.6f * shardT * shardT
                        val gs = cellHalf * 0.62f
                        rotate(p0.spin * shardT * 0.8f, Offset(gx, gy)) {
                            val gemTl = Offset(gx - gs, gy - gs)
                            val gemSz = androidx.compose.ui.geometry.Size(gs * 2f, gs * 2f)
                            drawRect(baseColor.copy(alpha = fragAlpha), gemTl, gemSz)
                            drawRect(lightColor.copy(alpha = fragAlpha * 0.9f), gemTl, androidx.compose.ui.geometry.Size(gs * 2f, gs * 0.6f))
                            drawRect(darkColor.copy(alpha = fragAlpha), gemTl, gemSz, style = goldGemStroke)
                        }
                        for (i in 1..3) {
                            val p = data.seeds[i]
                            val d = p.speed * cellPx * 0.8f * shardT
                            val mx = cx + cos(p.ang) * d
                            val my = cy - cellPx * 0.7f * shardT * p.speed + p.gravity * cellPx * 1.6f * shardT * shardT
                            // La moneda gira: su ancho late como una moneda vista de canto.
                            val r = cellPx * 0.13f
                            val sq = kotlin.math.abs(cos(shardT * 9f + p.ang)) * 0.8f + 0.2f
                            drawOval(Color(0xFFE8A92A).copy(alpha = fragAlpha), Offset(mx - r * sq, my - r), androidx.compose.ui.geometry.Size(2f * r * sq, 2f * r))
                            drawOval(Color(0xFFFFE27A).copy(alpha = fragAlpha), Offset(mx - r * sq * 0.6f, my - r * 0.6f), androidx.compose.ui.geometry.Size(1.2f * r * sq, 1.2f * r))
                        }
                        // Destello de oro en la casilla al soltar la gema.
                        if (shardT < 0.3f) {
                            val ga = (1f - shardT / 0.3f) * fragAlpha
                            val gr = cellPx * 0.4f
                            drawLine(Color(0xFFFFF1B0).copy(alpha = ga), Offset(cx - gr, cy), Offset(cx + gr, cy), strokeWidth = cellPx * 0.05f)
                            drawLine(Color(0xFFFFF1B0).copy(alpha = ga), Offset(cx, cy - gr), Offset(cx, cy + gr), strokeWidth = cellPx * 0.05f)
                        }
                    } else if (isMetal) {
                        // ── Metal: los REMACHES saltan y la CHAPA se dobla y cae girando ──
                        val fall = shardT * shardT
                        rotate((if (data.seeds[0].spin > 0f) 1f else -1f) * 40f * shardT, Offset(cx, cy + cellPx * fall)) {
                            val hs = cellHalf * (1f - 0.25f * shardT)
                            drawRect(
                                androidx.compose.ui.graphics.lerp(baseColor, Color.White, 0.2f).copy(alpha = fragAlpha),
                                Offset(cx - hs, cy - hs + cellPx * 1.4f * fall), androidx.compose.ui.geometry.Size(hs * 2f, hs * 2f * (1f - 0.35f * shardT))
                            )
                            drawRect(
                                darkColor.copy(alpha = fragAlpha * 0.8f),
                                Offset(cx - hs, cy - hs + cellPx * 1.4f * fall), androidx.compose.ui.geometry.Size(hs * 2f, hs * 2f * (1f - 0.35f * shardT)),
                                style = Stroke(width = cellPx * 0.05f)
                            )
                        }
                        data.seeds.forEachIndexed { idx, p ->
                            val corner = Offset(if (idx % 2 == 0) -0.34f else 0.34f, if (idx < 2) -0.34f else 0.34f)
                            val d = p.speed * cellPx * 0.9f * shardT
                            val rx = cx + corner.x * cellPx + kotlin.math.sign(corner.x) * d
                            val ry = cy + corner.y * cellPx - cellPx * 0.6f * shardT + p.gravity * cellPx * shardT * shardT * 1.4f
                            drawCircle(Color(0xFF8A929C).copy(alpha = fragAlpha), cellPx * 0.06f, Offset(rx, ry))
                            drawCircle(Color.White.copy(alpha = fragAlpha * 0.8f), cellPx * 0.025f, Offset(rx - cellPx * 0.015f, ry - cellPx * 0.015f))
                        }
                    } else if (isVoid) {
                        // ── Vacío: el bloque IMPLOSIONA — los trozos caen al centro y acaba en destello ──
                        val pull = (shardT / 0.6f).coerceIn(0f, 1f)
                        data.seeds.forEachIndexed { idx, p ->
                            // Salen un poco y vuelven en espiral hacia el centro.
                            val out = cellPx * 0.55f * p.speed * 0.25f * sin(pull * 3.14159f)
                            val spinA = p.ang + pull * 4f
                            val vx = cx + cos(spinA) * (cellHalf * (1f - pull) + out)
                            val vy = cy + sin(spinA) * (cellHalf * (1f - pull) + out)
                            val hs = cellHalf * (0.22f + 0.2f * p.sizeMul) * (1f - pull)
                            if (hs > 0.5f) {
                                rotate(p.spin * shardT * 2f, Offset(vx, vy)) {
                                    drawRect(
                                        baseColor.copy(alpha = fragAlpha),
                                        Offset(vx - hs, vy - hs), androidx.compose.ui.geometry.Size(hs * 2f, hs * 2f)
                                    )
                                }
                            }
                            // El destello final: un aro y un punto blanco al cerrarse.
                            if (idx == 0 && shardT > 0.55f) {
                                val f = ((shardT - 0.55f) / 0.45f).coerceIn(0f, 1f)
                                drawCircle(Color.White.copy(alpha = (1f - f) * 0.9f), cellPx * 0.12f * (1f - f), center)
                                drawCircle(
                                    lightColor.copy(alpha = (1f - f) * 0.7f),
                                    radius = cellPx * (0.1f + 0.6f * f), center = center,
                                    style = Stroke(width = cellPx * 0.05f * (1f - f))
                                )
                            }
                        }
                    } else if (isSky) {
                        // ── Constelación: las estrellas salen como FUGACES, con estela ──
                        data.seeds.forEachIndexed { idx, p ->
                            val dist = p.speed * cellPx * shardT * 1.1f
                            val sx = cx + cos(p.ang) * dist
                            val sy = cy + sin(p.ang) * dist + p.gravity * cellPx * shardT * shardT * 0.6f
                            val sa = (1f - shardT).coerceIn(0f, 1f) * fragAlpha
                            if (sa > 0f) {
                                val tail = cellPx * (0.35f + 0.3f * p.sizeMul)
                                drawLine(
                                    lightColor.copy(alpha = sa * 0.6f),
                                    Offset(sx - cos(p.ang) * tail, sy - sin(p.ang) * tail), Offset(sx, sy),
                                    strokeWidth = cellPx * 0.05f, cap = androidx.compose.ui.graphics.StrokeCap.Round
                                )
                                val sr = cellPx * (0.07f + 0.04f * p.sizeMul)
                                drawLine(Color.White.copy(alpha = sa), Offset(sx - sr * 1.6f, sy), Offset(sx + sr * 1.6f, sy), strokeWidth = sr * 0.45f)
                                drawLine(Color.White.copy(alpha = sa), Offset(sx, sy - sr * 1.6f), Offset(sx, sy + sr * 1.6f), strokeWidth = sr * 0.45f)
                                drawCircle(Color.White.copy(alpha = sa), sr * 0.6f, Offset(sx, sy))
                            }
                            // Un velo del color del cielo que se apaga en la celda.
                            if (idx == 0) {
                                val veil = (1f - shardT / 0.4f).coerceIn(0f, 1f)
                                if (veil > 0f) drawCircle(baseColor.copy(alpha = veil * 0.35f), cellPx * 0.45f, center)
                            }
                        }
                    } else if (isSpectre) {
                        // ── Espectro: el fantasmita se DESHACE en volutas que suben ondulando ──
                        data.seeds.forEachIndexed { idx, p ->
                            for (k in 0 until 2) {
                                val rise = cellPx * (0.9f + 0.5f * p.sizeMul + k * 0.3f) * shardT
                                val sway = sin(shardT * 7f + p.ang * 3f + k * 2f) * cellPx * 0.18f
                                val wx = cx + cos(p.ang) * cellPx * 0.35f * shardT + sway
                                val wy = cy - rise
                                val wa = ((1f - shardT) * (if (shardT < 0.15f) shardT / 0.15f else 1f)).coerceIn(0f, 1f) * fragAlpha
                                if (wa <= 0f) continue
                                val wr = cellPx * (0.10f + 0.10f * p.sizeMul) * (1f + shardT * 0.8f)
                                drawCircle(baseColor.copy(alpha = wa * 0.35f), wr * 1.8f, Offset(wx, wy))
                                drawCircle(androidx.compose.ui.graphics.lerp(lightColor, Color.White, 0.4f).copy(alpha = wa * 0.8f), wr, Offset(wx, wy))
                            }
                            // Un aro que se abre en el sitio del bloque: el «puf» del fantasma.
                            if (idx == 0) {
                                val ringT = (shardT / 0.45f).coerceIn(0f, 1f)
                                if (ringT < 1f) {
                                    drawCircle(
                                        lightColor.copy(alpha = (1f - ringT) * 0.6f),
                                        radius = cellPx * (0.25f + 0.5f * ringT), center = center,
                                        style = Stroke(width = cellPx * 0.05f * (1f - ringT))
                                    )
                                }
                            }
                        }
                    } else if (isIce) {
                        // ── Hielo: el cubito se parte en TROZOS que caen girando y suelta VAHO ──
                        data.seeds.forEachIndexed { idx, p ->
                            val dist = p.speed * cellPx * shardT * 0.9f
                            val ix = cx + cos(p.ang) * dist
                            val iy = cy + sin(p.ang) * dist * 0.7f + p.gravity * cellPx * shardT * shardT * 1.2f
                            val hs = cellHalf * (0.24f + p.sizeMul * 0.32f) * (1f - shardT * 0.25f)
                            if (hs > 0.5f) {
                                rotate(p.spin * shardT * 1.3f, Offset(ix, iy)) {
                                    val crI = androidx.compose.ui.geometry.CornerRadius(hs * 0.3f, hs * 0.3f)
                                    drawRoundRect(
                                        androidx.compose.ui.graphics.lerp(baseColor, Color.White, 0.35f).copy(alpha = fragAlpha * 0.9f),
                                        Offset(ix - hs, iy - hs), androidx.compose.ui.geometry.Size(hs * 2f, hs * 2f), crI
                                    )
                                    drawRoundRect(
                                        Color.White.copy(alpha = fragAlpha * 0.8f),
                                        Offset(ix - hs, iy - hs), androidx.compose.ui.geometry.Size(hs * 2f, hs * 2f), crI,
                                        style = Stroke(width = hs * 0.18f)
                                    )
                                }
                            }
                            // Vaho: nubecillas blancas que suben despacio y se deshacen.
                            if (idx % 2 == 0) {
                                val va = (1f - shardT).coerceIn(0f, 1f) * 0.45f * fragAlpha
                                if (va > 0f) {
                                    val vx = cx + cos(p.ang + 1.1f) * cellPx * 0.3f * shardT
                                    val vy = cy - cellPx * 0.7f * shardT
                                    drawCircle(Color.White.copy(alpha = va), cellPx * (0.14f + 0.22f * shardT), Offset(vx, vy))
                                }
                            }
                        }
                    } else if (isLava) {
                        // ── Lava: la roca revienta — trozos de basalto que caen y ASCUAS que suben ──
                        data.seeds.forEach { p ->
                            // Trozo de roca: cae con gravedad y gira.
                            val dist = p.speed * cellPx * shardT * 0.8f
                            val rx = cx + cos(p.ang) * dist
                            val ry = cy + sin(p.ang) * dist * 0.6f + p.gravity * cellPx * shardT * shardT * 1.3f
                            val hs = cellHalf * (0.28f + p.sizeMul * 0.35f) * (1f - shardT * 0.3f)
                            if (hs > 0.5f) {
                                rotate(p.spin * shardT, Offset(rx, ry)) {
                                    drawRect(
                                        androidx.compose.ui.graphics.lerp(darkColor, Color(0xFF1B1412), 0.5f).copy(alpha = fragAlpha),
                                        Offset(rx - hs, ry - hs), androidx.compose.ui.geometry.Size(hs * 2f, hs * 1.6f)
                                    )
                                    drawRect(
                                        lightColor.copy(alpha = fragAlpha * 0.8f),
                                        Offset(rx - hs, ry - hs), androidx.compose.ui.geometry.Size(hs * 2f, hs * 0.3f)
                                    )
                                }
                            }
                            // Ascuas: suben primero (el calor) y luego caen apagándose.
                            for (k in 0 until 2) {
                                val a2 = p.ang + k * 1.9f
                                val ex = cx + cos(a2) * cellPx * 0.55f * shardT * (0.6f + p.sizeMul)
                                val ey = cy - cellPx * 1.1f * shardT * (0.7f + k * 0.3f) + cellPx * 1.6f * shardT * shardT
                                val ea = (1f - shardT).coerceIn(0f, 1f) * fragAlpha
                                if (ea <= 0f) continue
                                val er = cellPx * (0.045f + 0.03f * p.sizeMul)
                                drawCircle(baseColor.copy(alpha = ea * 0.35f), er * 2.4f, Offset(ex, ey))
                                drawCircle(androidx.compose.ui.graphics.lerp(lightColor, Color.White, 0.4f).copy(alpha = ea), er, Offset(ex, ey))
                            }
                        }
                    } else if (isWood) {
                        // ── Madera: la tabla se ASTILLA — listones finos que giran y caen, y serrín ──
                        data.seeds.forEachIndexed { idx, p ->
                            for (branch in 0 until 2) {
                                val ang  = p.ang + (branch * 0.9f - 0.45f)
                                val spd  = p.speed * (if (branch == 0) 1.0f else 0.75f)
                                val dist = spd * cellPx * shardT * (1f - 0.2f * shardT)
                                val fx   = cx + cos(ang) * dist
                                val fy   = cy + sin(ang) * dist + p.gravity * cellPx * shardT * shardT * 1.15f
                                val len  = cellHalf * (0.75f + p.sizeMul) * (1f - shardT * 0.3f)
                                if (len <= 0.5f) continue
                                val wid  = len * 0.2f
                                val col  = if ((idx + branch) % 2 == 0) baseColor else darkColor
                                rotate(p.ang * 57.2958f + p.spin * shardT * 1.6f, Offset(fx, fy)) {
                                    drawRect(
                                        col.copy(alpha = fragAlpha),
                                        Offset(fx - len * 0.5f, fy - wid * 0.5f),
                                        androidx.compose.ui.geometry.Size(len, wid)
                                    )
                                    // Veta clara a lo largo: se lee como madera, no como confeti.
                                    drawLine(
                                        lightColor.copy(alpha = fragAlpha * 0.6f),
                                        Offset(fx - len * 0.45f, fy - wid * 0.15f), Offset(fx + len * 0.45f, fy - wid * 0.15f),
                                        strokeWidth = wid * 0.25f
                                    )
                                }
                            }
                            // Serrín: una mota clara por semilla que sube un poco y se posa.
                            val dustA = (1f - shardT * 1.4f).coerceIn(0f, 1f)
                            if (dustA > 0f) {
                                val dx = cx + cos(p.ang + 0.8f) * cellPx * 0.45f * shardT
                                val dy = cy - cellPx * 0.25f * shardT + cellPx * 0.6f * shardT * shardT
                                drawCircle(lightColor.copy(alpha = dustA * 0.8f), cellPx * 0.05f * (1f + p.sizeMul), Offset(dx, dy))
                            }
                        }
                    } else if (isCrystal) {
                        // ── Cristal: la gema estalla en ESQUIRLAS talladas + destellos en cruz ──
                        // Anillo fino y rápido: el «clinc» del cristal, más seco que un pop.
                        val ringT = (shardT / 0.30f).coerceIn(0f, 1f)
                        if (ringT > 0f && ringT < 1f) {
                            drawCircle(
                                color = lightColor.copy(alpha = (1f - ringT) * 0.7f),
                                radius = cellPx * (0.30f + 0.55f * ringT),
                                center = center,
                                style = Stroke(width = cellPx * 0.035f * (1f - ringT))
                            )
                        }
                        // Esquirlas: 2 por semilla (8), triángulos alargados que giran y caen. Las
                        // de la cara iluminada van en el tono claro y las de la sombra en el base.
                        data.seeds.forEachIndexed { idx, p ->
                            for (branch in 0 until 2) {
                                val ang  = p.ang + (branch * 0.7f - 0.35f)
                                val spd  = p.speed * (if (branch == 0) 1.05f else 0.8f)
                                val dist = spd * cellPx * shardT * (1f - 0.25f * shardT)
                                val fx   = cx + cos(ang) * dist
                                val fy   = cy + sin(ang) * dist + p.gravity * cellPx * shardT * shardT
                                val len  = cellHalf * (0.55f + p.sizeMul) * (1f - shardT * 0.35f)
                                if (len <= 0.5f) continue
                                val wid  = len * 0.42f
                                shardPath.reset()
                                shardPath.moveTo(fx, fy - len * 0.6f)
                                shardPath.lineTo(fx + wid * 0.5f, fy + len * 0.4f)
                                shardPath.lineTo(fx - wid * 0.5f, fy + len * 0.25f)
                                shardPath.close()
                                val col = if ((idx + branch) % 2 == 0) lightColor else baseColor
                                rotate(p.ang * 57.2958f + p.spin * shardT * 1.2f, Offset(fx, fy)) {
                                    drawPath(shardPath, col, alpha = fragAlpha)
                                    // Filo de luz en un lado: se lee como vidrio, no como papel.
                                    drawLine(
                                        Color.White.copy(alpha = fragAlpha * 0.75f),
                                        Offset(fx, fy - len * 0.6f), Offset(fx + wid * 0.5f, fy + len * 0.4f),
                                        strokeWidth = cellPx * 0.025f
                                    )
                                }
                            }
                            // Destello en cruz que titila una vez: dos por celda, en semillas alternas.
                            if (idx % 2 == 0) {
                                val tw = (shardT / 0.55f).coerceIn(0f, 1f)
                                val a  = (if (tw < 0.4f) tw / 0.4f else 1f - (tw - 0.4f) / 0.6f).coerceIn(0f, 1f)
                                if (a > 0f) {
                                    val sx = cx + cos(p.ang + 1.3f) * cellPx * (0.25f + 0.45f * tw)
                                    val sy = cy + sin(p.ang + 1.3f) * cellPx * (0.25f + 0.45f * tw)
                                    val sr = cellPx * (0.10f + 0.10f * p.sizeMul)
                                    val sw = cellPx * 0.03f
                                    val white = Color.White.copy(alpha = a)
                                    drawLine(white, Offset(sx - sr, sy), Offset(sx + sr, sy), strokeWidth = sw)
                                    drawLine(white, Offset(sx, sy - sr), Offset(sx, sy + sr), strokeWidth = sw)
                                }
                            }
                        }
                    } else {
                        // ── Clásico/Gomita: cubitos 3D disparados con gravedad y giro ──
                        data.seeds.forEach { p ->
                            val dist = p.speed * cellPx * shardT * (1f - 0.32f * shardT)
                            val fx   = cx + cos(p.ang) * dist
                            val fy   = cy + sin(p.ang) * dist + p.gravity * cellPx * shardT * shardT
                            val hw   = cellHalf * p.sizeMul * (1f - shardT * 0.4f)
                            if (hw <= 0.5f) return@forEach
                            val cr   = androidx.compose.ui.geometry.CornerRadius(hw * fragCornerFr, hw * fragCornerFr)
                            rotate(p.spin * shardT, Offset(fx, fy)) {
                                drawRoundRect(
                                    baseColor.copy(alpha = fragAlpha),
                                    Offset(fx - hw, fy - hw),
                                    androidx.compose.ui.geometry.Size(hw * 2f, hw * 2f), cr
                                )
                                drawRoundRect(
                                    lightColor.copy(alpha = fragAlpha * 0.9f),
                                    Offset(fx - hw, fy - hw),
                                    androidx.compose.ui.geometry.Size(hw * 2f, hw * 0.8f), cr
                                )
                            }
                        }

                        // ── Destellos blancos (glints en cruz) ──
                        val sparkT = ((t - 0.05f) / 0.55f).coerceIn(0f, 1f)
                        if (sparkT > 0f && sparkT < 1f) {
                            val sa = (1f - sparkT) * 0.9f
                            val sw = cellPx * 0.025f
                            for (i in 0 until 4) {
                                val sang = (i * 90f + 32f) * PI.toFloat() / 180f
                                val sd   = cellPx * (0.28f + 1.05f * sparkT)
                                val sx   = cx + cos(sang) * sd
                                val sy   = cy + sin(sang) * sd
                                val sr   = cellPx * 0.11f * (1f - sparkT * 0.5f)
                                drawLine(Color.White.copy(alpha = sa), Offset(sx - sr, sy), Offset(sx + sr, sy), strokeWidth = sw)
                                drawLine(Color.White.copy(alpha = sa), Offset(sx, sy - sr), Offset(sx, sy + sr), strokeWidth = sw)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Line Clear Beam — haz de luz cromático que barre la línea (estilo Block Blast) ──
// Se dibuja ENCIMA del LineCellShatter. Rework 2026-07-09: SLAM MATE (ver la función), fuera el láser.

private const val BEAM_LIFE_MS = 440
private const val FUSION_BURST_MS = 440

/**
 * Clímax de fusión de Teikas: onda de choque en ANILLO + destello del color del tier, todo MATE
 * (SrcOver, sin BlendMode.Plus, sin partículas). Escala con el tier (T2 sutil → T4 momentazo). El
 * "clunk"/squash lo aporta el rebote de aparición del recién nacido (spawnScale de la capa de cuerpos).
 */
@Composable
fun FusionBurstVFX(
    event: GameEvent.FusionBurst,
    cellSize: Dp,
    onComplete: () -> Unit
) {
    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }
    val clock   = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(1f, tween(FUSION_BURST_MS, easing = FastOutSlowInEasing))
        onComplete()
    }
    // Color del tier resultante (celeste → morado → dorado); el resto, blanco.
    val tierColor = when (event.tier) {
        2    -> Color(0xFF6FC8FF)
        3    -> Color(0xFFB07CF0)
        4    -> Color(0xFFFFC93D)
        else -> Color.White
    }
    val cx   = (event.center.x + 0.5f) * cellPx
    val cy   = (event.center.y + 0.5f) * cellPx
    val maxR = cellPx * (0.9f + event.tier * 0.55f)   // T2 ≈ 2 celdas … T4 ≈ 3.1 celdas

    Canvas(modifier = Modifier.fillMaxSize()) {
        val p = clock.value
        if (p <= 0f || p >= 1f) return@Canvas
        val c = Offset(cx, cy)

        // 1) Destello de impacto (disco mate) que revienta y se va en el primer ~35%.
        val flash = (1f - p / 0.35f).coerceIn(0f, 1f)
        if (flash > 0f) {
            drawCircle(tierColor.copy(alpha = 0.42f * flash), cellPx * (0.42f + 0.55f * (1f - flash)), c)
            drawCircle(Color.White.copy(alpha = 0.65f * flash), cellPx * 0.30f * (1f - flash * 0.4f), c)
        }

        // 2) Onda de choque en anillo MATE: se expande y adelgaza al desvanecer.
        val ringR = maxR * p
        val ringFade = (1f - p).coerceIn(0f, 1f)
        if (ringFade > 0.01f && ringR > 0.1f) {
            val w = cellPx * (0.20f * ringFade + 0.03f)
            drawCircle(tierColor.copy(alpha = 0.85f * ringFade), ringR, c, style = Stroke(width = w))
            drawCircle(Color.White.copy(alpha = 0.30f * ringFade), ringR * 0.9f, c, style = Stroke(width = w * 0.4f))
        }
    }
}

/**
 * Anticipación DRAMÁTICA de un T4 (dos T3 se tocaron). Durante `event.durationMs`: los dos orbes T3
 * se ACERCAN al centro y crecen, un scrim radial ENFOCA el centro (pseudo cámara lenta) y un punto de
 * contacto blanco-incandescente FLORECE — luego el ViewModel funde y sale el FusionBurst + shake. Los
 * Los cuerpos reales permanecen montados y se acercan dentro de TeikaPhysicsBodiesLayer, conservando
 * su key y su Lottie. Esta capa sólo dibuja el foco y el punto incandescente, con un Animatable que
 * redibuja sin recomponer.
 */
@Composable
fun FusionChargeVFX(
    event: GameEvent.FusionCharge,
    cellSize: Dp,
    reducedFx: Boolean,
    onComplete: () -> Unit
) {
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val p = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        p.animateTo(1f, tween(event.durationMs, easing = FastOutSlowInEasing))
        onComplete()
    }
    val tier = event.tier.coerceIn(1, 4)
    // El DRAMA sube con el tier: un T1+T1 no merece cámara lenta, un T3+T3 sí.
    val dramaK = if (reducedFx) 0f else when (tier) { 1 -> 0f; 2 -> 0.45f; else -> 1f }

    Box(modifier = Modifier.fillMaxSize()) {
        // Scrim radial que enfoca el centro (slow-mo) + punto de contacto incandescente.
        Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val cx = event.cx * cellPx
                    val cy = event.cy * cellPx
                    val scrimR = cellPx * (1.6f + 1.8f * dramaK)
                    val scrim = Brush.radialGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f), Color.Black.copy(alpha = 0.66f)),
                        center = Offset(cx, cy), radius = scrimR
                    )
                    val hotR = cellPx * (0.5f + 0.55f * tier / 3f)
                    val hotBrush = Brush.radialGradient(
                        listOf(Color.White, Color(0xFFFFE9A8), Color(0x00FFE9A8)),
                        center = Offset(cx, cy), radius = hotR
                    )
                    onDrawBehind {
                        val pv = p.value
                        val scrimEnv = when {
                            pv < 0.22f -> pv / 0.22f
                            pv < 0.86f -> 1f
                            else       -> (1f - (pv - 0.86f) / 0.14f).coerceIn(0f, 1f)
                        }
                        // Sin scrim en las fusiones pequeñas: oscurecer la pantalla por dos T1 sería
                        // dramatizar lo que solo tiene que leerse.
                        if (dramaK > 0.01f && scrimEnv > 0.01f) drawRect(brush = scrim, alpha = scrimEnv * dramaK)
                        // Punto de contacto blanco-incandescente que florece al acercarse los orbes.
                        val hot = ((pv - 0.30f) / 0.6f).coerceIn(0f, 1f)
                        if (hot > 0.01f) drawCircle(brush = hotBrush, radius = hotR * (0.2f + 0.8f * hot), center = Offset(cx, cy), alpha = hot)
                    }
                }
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COLAPSO ESTELAR — la ceremonia ÚNICA de T4+T4 → MEGA (2026-09-24)
// Los dos T4 reales orbitan en espiral dentro de TeikaPhysicsBodiesLayer; esta capa pinta el
// universo alrededor: el vacío que se traga el tablero, motas succionadas, disco de acreción,
// grietas de luz y la singularidad que arde en blanco antes de que el MEGA estalle. Curvas y
// tiempos: model/MegaCollapse.kt. Todo se precalcula en drawWithCache: el dibujo no asigna.
// ─────────────────────────────────────────────────────────────────────────────
private val COLLAPSE_VOID   = Color(0xFF07031A)
private val COLLAPSE_GOLD   = Color(0xFFFFC93D)
private val COLLAPSE_VIOLET = Color(0xFFB07CF0)
private val COLLAPSE_CYAN   = Color(0xFF7FE3FF)
private val COLLAPSE_HOT    = Color(0xFFFFF4D6)
private const val COLLAPSE_STARS = 36
private const val COLLAPSE_CRACK_SEGS = 6

@Composable
fun MegaCollapseVFX(
    event: GameEvent.FusionCharge,
    cellSize: Dp,
    /**
     * Dos capas, una por lado de los cuerpos: la de FONDO (vacío, estrellas, motas, disco) va
     * DEBAJO de los T4 —si no, el vacío los apagaría a ellos también— y la de FRENTE (grietas,
     * singularidad, fogonazo) va ENCIMA. Solo la de frente consume el evento.
     */
    front: Boolean,
    onComplete: () -> Unit = {}
) {
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        // P-273: el crescendo háptico acompaña a la ceremonia. Solo la capa de FRENTE lo lanza —las
        // dos capas viven a la vez y cada colapso tiene que vibrar una sola vez.
        if (front) Haptics.megaCollapse()
        // Lineal: las curvas (aceleración de la caída, respiración…) viven en MegaCollapse.
        clock.animateTo(1f, tween(event.durationMs, easing = LinearEasing))
        onComplete()
    }
    val half = GameState.GRID_SIZE / 2f
    // Radio de salida de la pareja: el disco de acreción la abraza desde fuera.
    val r0 = hypot(event.ax - event.cx, event.ay - event.cy)

    Spacer(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                val boardPx = size.minDimension
                // Semilla por evento: dos colapsos en la misma partida no dibujan el mismo cielo.
                val rnd = java.util.Random(event.aId.hashCode().toLong() * 31 + event.bId.hashCode())
                // Estrellas del vacío (x, y, fase) en px.
                val stars = FloatArray(COLLAPSE_STARS * 3) { i ->
                    if (i % 3 == 2) rnd.nextFloat() * 6.2831855f else rnd.nextFloat() * boardPx
                }
                // Motas succionadas: ángulo y distancia de salida respecto al centro de la espiral.
                val moteAng = FloatArray(MegaCollapse.MOTES) { rnd.nextFloat() * 6.2831855f }
                val moteDist = FloatArray(MegaCollapse.MOTES) { cellPx * (1.6f + rnd.nextFloat() * 3.6f) }
                val moteColor = Array(MegaCollapse.MOTES) { i ->
                    when (i % 4) { 0 -> COLLAPSE_GOLD; 1 -> COLLAPSE_VIOLET; 2 -> COLLAPSE_CYAN; else -> Color.White }
                }
                // Grietas: polilíneas quebradas en coordenadas LOCALES (origen = singularidad).
                val crackPts = FloatArray(MegaCollapse.CRACKS * (COLLAPSE_CRACK_SEGS + 1) * 2)
                for (ci in 0 until MegaCollapse.CRACKS) {
                    val base = ci * 6.2831855f / MegaCollapse.CRACKS + rnd.nextFloat() * 0.35f
                    val len = cellPx * (3.2f + rnd.nextFloat() * 2.4f)
                    for (s in 0..COLLAPSE_CRACK_SEGS) {
                        val f = s / COLLAPSE_CRACK_SEGS.toFloat()
                        val a = base + if (s == 0) 0f else (rnd.nextFloat() - 0.5f) * 0.55f
                        val idx = (ci * (COLLAPSE_CRACK_SEGS + 1) + s) * 2
                        crackPts[idx] = kotlin.math.cos(a) * len * f
                        crackPts[idx + 1] = kotlin.math.sin(a) * len * f
                    }
                }
                val vignette = Brush.radialGradient(
                    listOf(Color.Transparent, COLLAPSE_VOID.copy(alpha = 0.55f), COLLAPSE_VOID),
                    center = Offset(half * cellPx, half * cellPx), radius = boardPx * 0.75f
                )
                val diskStroke = Stroke(width = cellPx * 0.16f, cap = StrokeCap.Round)
                val diskThin = Stroke(width = cellPx * 0.06f, cap = StrokeCap.Round)
                val lensStroke = Stroke(width = cellPx * 0.05f)
                onDrawBehind {
                    val k = clock.value
                    if (k <= 0f || k >= 1f) return@onDrawBehind
                    val cx = MegaCollapse.center(k, event.cx, half) * cellPx
                    val cy = MegaCollapse.center(k, event.cy, half) * cellPx
                    val c = Offset(cx, cy)

                    // 1) EL VACÍO: el tablero se apaga en un índigo profundo con viñeta y estrellas.
                    val scrim = MegaCollapse.scrim(k)
                    val voidK = scrim / MegaCollapse.SCRIM_MAX
                    if (!front && scrim > 0.005f) {
                        drawRect(COLLAPSE_VOID, alpha = scrim * 0.7f)
                        drawRect(brush = vignette, alpha = scrim)
                        for (i in 0 until COLLAPSE_STARS) {
                            val tw = 0.5f + 0.5f * kotlin.math.sin(k * 38f + stars[i * 3 + 2])
                            drawCircle(
                                Color.White, radius = cellPx * (0.025f + 0.03f * tw),
                                center = Offset(stars[i * 3], stars[i * 3 + 1]),
                                alpha = voidK * (0.25f + 0.55f * tw)
                            )
                        }
                    }

                    // 2) MOTAS SUCCIONADAS: caen en espiral hacia la singularidad dejando estela.
                    for (i in 0 until MegaCollapse.MOTES) {
                        if (front) break
                        val p = MegaCollapse.moteProgress(k, i)
                        if (p >= 1f) continue
                        val d = moteDist[i] * (1f - p)
                        val a = moteAng[i] + p * 2.4f
                        val px = cx + kotlin.math.cos(a) * d
                        val py = cy + kotlin.math.sin(a) * d
                        val alpha = voidK * (0.35f + 0.65f * p) *
                            (1f - ((p - 0.85f) / 0.15f).coerceIn(0f, 1f))
                        if (alpha <= 0.01f) continue
                        // Estela: donde estaba hace un instante (más lejos y menos girada).
                        val back = (p - 0.08f).coerceAtLeast(0f)
                        val bd = moteDist[i] * (1f - back)
                        val ba = moteAng[i] + back * 2.4f
                        drawLine(
                            moteColor[i],
                            start = Offset(cx + kotlin.math.cos(ba) * bd, cy + kotlin.math.sin(ba) * bd),
                            end = Offset(px, py),
                            strokeWidth = cellPx * (0.05f + 0.07f * p),
                            cap = StrokeCap.Round,
                            alpha = alpha * 0.8f,
                            blendMode = BlendMode.Plus
                        )
                        drawCircle(moteColor[i], radius = cellPx * (0.05f + 0.05f * p), center = Offset(px, py), alpha = alpha)
                    }

                    // 3) DISCO DE ACRECIÓN: arcos que giran alrededor de la pareja y se cierran con ella.
                    val acc = MegaCollapse.accretion(k)
                    if (!front && acc > 0.01f) {
                        val orbitR = MegaCollapse.orbitRadius(k, r0) * cellPx
                        val diskR = orbitR + cellPx * (2.3f - 1.4f * k)
                        val spin = Math.toDegrees(MegaCollapse.orbitAngle(k, 0f).toDouble()).toFloat() * 1.35f
                        val tl = Offset(cx - diskR, cy - diskR)
                        val sz = Size(diskR * 2f, diskR * 2f)
                        for (arc in 0 until 3) {
                            val start = spin + arc * 120f
                            drawArc(COLLAPSE_GOLD, start, 78f, false, tl, sz,
                                alpha = acc * 0.75f, style = diskStroke, blendMode = BlendMode.Plus)
                            drawArc(COLLAPSE_VIOLET, start + 60f, 40f, false, tl, sz,
                                alpha = acc * 0.6f, style = diskThin, blendMode = BlendMode.Plus)
                        }
                        // Lente gravitatoria: anillo fino que se contrae más rápido que el disco.
                        val lensR = diskR * (1.25f - 0.55f * k)
                        drawCircle(Color.White, radius = lensR, center = c, alpha = acc * 0.35f, style = lensStroke)
                    }

                    // 4) GRIETAS DE LUZ: rayos quebrados que se abren desde la singularidad.
                    if (!front) return@onDrawBehind
                    val cr = MegaCollapse.cracks(k)
                    if (cr > 0.01f) {
                        val segsShown = cr * COLLAPSE_CRACK_SEGS
                        rotate(k * 55f, pivot = c) {
                            for (ci in 0 until MegaCollapse.CRACKS) {
                                val flicker = 0.55f + 0.45f * kotlin.math.sin(k * 70f + ci * 1.7f)
                                for (s in 0 until COLLAPSE_CRACK_SEGS) {
                                    val f = (segsShown - s).coerceIn(0f, 1f)
                                    if (f <= 0f) break
                                    val i0 = (ci * (COLLAPSE_CRACK_SEGS + 1) + s) * 2
                                    val x0 = cx + crackPts[i0]
                                    val y0 = cy + crackPts[i0 + 1]
                                    val x1 = cx + crackPts[i0 + 2]
                                    val y1 = cy + crackPts[i0 + 3]
                                    val end = Offset(x0 + (x1 - x0) * f, y0 + (y1 - y0) * f)
                                    val fade = 1f - s / COLLAPSE_CRACK_SEGS.toFloat() * 0.6f
                                    drawLine(COLLAPSE_GOLD, Offset(x0, y0), end, cellPx * 0.14f, StrokeCap.Round,
                                        alpha = cr * flicker * fade * 0.45f, blendMode = BlendMode.Plus)
                                    drawLine(COLLAPSE_HOT, Offset(x0, y0), end, cellPx * 0.045f, StrokeCap.Round,
                                        alpha = cr * flicker * fade, blendMode = BlendMode.Plus)
                                }
                            }
                        }
                    }

                    // 5) LA SINGULARIDAD: núcleo que arde en blanco + destello en cruz.
                    val sg = MegaCollapse.singularity(k)
                    if (sg > 0.01f) {
                        val coreR = cellPx * (0.18f + 1.1f * sg)
                        drawCircle(COLLAPSE_VIOLET, coreR * 2.2f, c, alpha = sg * 0.22f, blendMode = BlendMode.Plus)
                        drawCircle(COLLAPSE_GOLD, coreR * 1.45f, c, alpha = sg * 0.45f, blendMode = BlendMode.Plus)
                        drawCircle(COLLAPSE_HOT, coreR, c, alpha = sg * 0.85f, blendMode = BlendMode.Plus)
                        drawCircle(Color.White, coreR * 0.55f, c, alpha = sg)
                        val flare = cellPx * 7f * sg * sg
                        rotate(k * 30f, pivot = c) {
                            drawLine(Color.White, Offset(cx - flare, cy), Offset(cx + flare, cy),
                                cellPx * 0.07f * sg, StrokeCap.Round, alpha = sg * 0.9f, blendMode = BlendMode.Plus)
                            drawLine(Color.White, Offset(cx, cy - flare * 0.75f), Offset(cx, cy + flare * 0.75f),
                                cellPx * 0.06f * sg, StrokeCap.Round, alpha = sg * 0.8f, blendMode = BlendMode.Plus)
                        }
                    }

                    // 6) FOGONAZO de relevo: el último 5 % se funde en blanco y el MEGA nace de ahí.
                    val flash = ((k - 0.95f) / 0.05f).coerceIn(0f, 1f)
                    if (flash > 0f) drawRect(Color.White, alpha = flash * 0.85f)
                }
            }
    )
}

@Composable
fun LineClearBeamVFX(
    event: GameEvent.LineClearBeam,
    cellSize: Dp,
    boardCols: Int = GameState.GRID_W_LEVELS,
    // FILAS del tablero: 8 en niveles, 18 en el pozo del Infinito. El cap del barrido usaba
    // `GRID_SIZE` (que solo es el ANCHO) y en el pozo la barra de una COLUMNA se quedaba corta,
    // cubriendo 8 de 18 filas (fix 2026-07-26).
    boardRows: Int = GameState.GRID_SIZE,
    // INFINITO: fila (con decimales) donde empieza el MURO. La barra de una columna muere ahí: lo
    // que hay debajo es hormigón, y el clear no se lo lleva — que la animación siguiera hasta el
    // fondo del tablero prometía una demolición que nunca ocurre. [boardRows] = sin muro.
    wallTopRow: () -> Float = { boardRows.toFloat() },
    onComplete: () -> Unit
) {
    val density  = LocalDensity.current
    val cellPx   = with(density) { cellSize.toPx() }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (event.delayMs > 0) delay(event.delayMs.toLong())
        progress.animateTo(1f, tween(BEAM_LIFE_MS, easing = LinearEasing))
        onComplete()
    }

    // SLAM MATE (rework 2026-07-09: fuera el láser — aberración cromática, glow aditivo, hotspot
    // viajero y puntas luminosas). La fila destella como una barra SÓLIDA y PLANA del color de la
    // ficha, con un pop de grosor (overshoot) y un desvanecido rápido que descubre la trituración
    // chunky de los bloques (BatchLineCellShatterVFX). Todo SrcOver, cero BlendMode.Plus.
    val palette = LocalBlockSkin.current.colorsFor(event.colorType.coerceAtLeast(0))
    val base = palette[0]
    val light = palette[1]

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = progress.value
        if (t <= 0f || t >= 1f) return@Canvas
        val isRow       = event.isRow
        // Largo del eje por el que barre la barra: ANCHO en filas, ALTO en columnas. Eran el mismo
        // número mientras el tablero fue cuadrado; en el pozo del Infinito no lo son.
        val axisPx      = if (isRow) cellPx * boardCols else cellPx * boardRows
        val crossCenter = (event.lineIndex + 0.5f) * cellPx
        // Tramo del barrido (media fila del corte ninja): la barra solo cubre su mitad.
        val spanOrigin  = event.spanStart * cellPx
        // En una COLUMNA el tramo no cruza el hormigón (wallTopRow) NI se alarga más que spanCount:
        // mono-columna y clears parciales sobre el muro pintan solo el tramo real (infinito2).
        val axisLimit   = if (isRow) axisPx else minOf(axisPx, wallTopRow() * cellPx)
        val spanLen     = (event.spanCount * cellPx)
            .coerceAtMost(axisLimit - spanOrigin)
            .coerceAtLeast(0f)
        if (spanLen <= 0.5f) return@Canvas

        // Pop instantáneo (0→1 en ~8%) y se va en el primer ~55% de vida → deja ver los trozos.
        val appear = (t / 0.08f).coerceIn(0f, 1f)
        val fade   = 1f - (t / 0.55f).coerceIn(0f, 1f)
        val env    = appear * fade
        if (env <= 0.003f) return@Canvas

        // Grosor: overshoot al aparecer (slam ~1.25 celda) y adelgaza al desvanecer.
        val overshoot = 1f + 0.25f * (1f - appear)
        val halfThick = cellPx * 0.5f * overshoot * (0.35f + 0.65f * fade)

        fun barGeom(ht: Float): Pair<Offset, Size> =
            if (isRow) Offset(spanOrigin, crossCenter - ht) to Size(spanLen, ht * 2f)
            else       Offset(crossCenter - ht, spanOrigin) to Size(ht * 2f, spanLen)

        fun bar(color: Color, a: Float, ht: Float) {
            if (a <= 0.003f || ht <= 0.1f) return
            val (tl, sz) = barGeom(ht)
            drawRoundRect(
                color = color.copy(alpha = a.coerceIn(0f, 1f)),
                topLeft = tl, size = sz,
                cornerRadius = CornerRadius(ht, ht)   // SrcOver por defecto → mate, sin brillo
            )
        }

        // Barra con GRADIENTE DIRECCIONAL que BARRE la línea (referencia Block Blast): horizontal
        // en filas, vertical en columnas. Sigue siendo mate (SrcOver, sin BlendMode.Plus): el
        // "barrido" es un hotspot claro del color de la ficha que viaja de punta a punta durante
        // la vida visible de la barra, no un láser aditivo.
        run {
            val a = env * 0.92f
            if (a > 0.003f && halfThick > 0.1f) {
                val (tl, sz) = barGeom(halfThick)
                val sweepT = (t / 0.55f).coerceIn(0f, 1f)
                // El hotspot entra desde fuera de un extremo y sale por el otro (−25% → 125%),
                // recorriendo SOLO el tramo de la barra (media fila con el corte ninja).
                val hotspot  = spanOrigin + spanLen * (sweepT * 1.5f - 0.25f)
                val bandHalf = spanLen * 0.30f
                val hot = lerp(light, Color.White, 0.5f)
                val brush =
                    if (isRow) Brush.horizontalGradient(
                        colors = listOf(base, hot, base),
                        startX = hotspot - bandHalf, endX = hotspot + bandHalf
                    ) else Brush.verticalGradient(
                        colors = listOf(base, hot, base),
                        startY = hotspot - bandHalf, endY = hotspot + bandHalf
                    )
                drawRoundRect(
                    brush = brush, topLeft = tl, size = sz,
                    cornerRadius = CornerRadius(halfThick, halfThick),
                    alpha = a.coerceIn(0f, 1f)
                )
            }
        }
        // Destello blanco brevísimo (primeros ~2 frames) para el "impacto".
        val whiteFlash = (1f - t / 0.10f).coerceIn(0f, 1f)
        if (whiteFlash > 0f) bar(Color.White, whiteFlash * 0.85f, halfThick * 0.8f)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Mega Teika — irrumpe llenando el tablero y EMPUJA todo fuera (rework 2026-07-09)
// El Mega aparece con el mismo "pop" de spawn de un Teika, llena el tablero y despide bloques y
// demás Teikas por los bordes, limpiándolo. 2026-09-24 (decisión del usuario): se LEVANTA el «sin
// flash, anillos ni rótulo» — tras el colapso estelar el nacimiento lleva fogonazo, tres ondas de
// choque, rayos divinos detrás del Mega y el rótulo «¡MEGA TEIKA!».
// 2026-07-25: el empuje va ACOPLADO al crecimiento — cada item sale cuando el radio del Mega lo
// alcanza, no en un segundo acto tras el asentamiento. Constantes y fórmulas: model/MegaFling.kt.
// ─────────────────────────────────────────────────────────────────────────────
/** Lo que tarda en reventar un bloque CON ESTRELLA alcanzado por el Mega (no vuela: se rompe). */
private const val MEGA_BREAK_MS = 260f

@Composable
fun InfiniteMegaTeikaExplosionVFX(
    event: GameEvent.MegaTeikaFriendExpansion,
    cellSize: Dp,
    reducedFx: Boolean = false,
    onComplete: () -> Unit
) {
    val density = LocalDensity.current
    val cellPx  = with(density) { cellSize.toPx() }
    val boardPx = cellPx * GameState.GRID_SIZE
    val cx      = boardPx / 2f
    val cy      = boardPx / 2f
    val skin    = LocalBlockSkin.current
    val ctx     = androidx.compose.ui.platform.LocalContext.current
    val targetStarBitmaps = remember(ctx) { targetStarBitmaps(ctx) }

    // Un ÚNICO reloj (ms) manda sobre toda la coreografía: el Mega crece Y su radio va empujando lo
    // que toca EN PARALELO (rework 2026-07-25, docs/plan-mega-teika-aparicion.md). Antes eran dos
    // actos —crecer 1,1 s, asentar 120 ms, y solo entonces empujar—, y se leía como "el Mega se
    // hincha encima de un tablero lleno y quieto". Ahora cada bloque arranca su vuelo cuando el radio
    // del Mega lo alcanza. Encima va la celebración del colapso estelar (2026-09-24): fogonazo,
    // ondas, rayos y rótulo. Fórmulas puras en model/MegaFling.kt.
    val clock     = remember { Animatable(0f) }
    val megaExit  = remember { Animatable(1f) }   // rebote de retirada al final
    val megaAlpha = remember { Animatable(1f) }
    // Reloj de la celebración (rayos y rótulo): dura TODO el evento, no solo el empuje.
    val glory     = remember { Animatable(0f) }
    val labelIn   = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch {
            glory.animateTo(
                1f,
                tween(
                    (MegaFling.GROW_MS + MegaFling.HOLD_LEVELS_MS + MegaFling.FADE_MS).roundToInt(),
                    easing = LinearEasing
                )
            )
        }
        launch {
            delay(MEGA_LABEL_DELAY_MS)
            labelIn.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        }
        launch {
            clock.animateTo(
                MegaFling.CLOCK_END_MS,
                tween(MegaFling.CLOCK_END_MS.roundToInt(), easing = LinearEasing)
            )
        }
        // El Mega queda VISIBLE llenando el tablero antes de que aparezca la ventana de nivel
        // completado (onComplete confirma el LEVEL_COMPLETE_PENDING).
        delay(MegaFling.GROW_MS.toLong() + MegaFling.HOLD_LEVELS_MS)
        // El Mega se retira dejando el tablero limpio (sin rótulo).
        launch { megaExit.animateTo(1.16f, tween(MegaFling.FADE_MS, easing = FastOutSlowInEasing)) }
        launch { megaAlpha.animateTo(0f, tween(MegaFling.FADE_MS)) }
        delay(MegaFling.FADE_MS + 20L)
        onComplete()
    }

    // Instante en que el radio del Mega alcanza a cada item: se precalcula UNA vez (inversa analítica
    // de la curva de crecimiento) para que el bucle de dibujo siga sin allocs ni búsquedas por frame.
    val halfBoardPx = boardPx / 2f
    val slackPx     = MegaFling.CONTACT_SLACK_CELLS * cellPx
    val blockHitMs = remember(event, cellPx) {
        FloatArray(event.flungBlocks.size) { i ->
            val fb = event.flungBlocks[i]
            val ox = (fb.pos.x + 0.5f) * cellPx
            val oy = (fb.pos.y + 0.5f) * cellPx
            MegaFling.hitTimeMs(hypot(ox - cx, oy - cy), halfBoardPx, slackPx)
        }
    }
    // Bloques CON ESTRELLA: mismo reloj, otro destino — se ROMPEN en su celda (no salen despedidos).
    val brokenHitMs = remember(event, cellPx) {
        FloatArray(event.brokenBlocks.size) { i ->
            val fb = event.brokenBlocks[i]
            val ox = (fb.pos.x + 0.5f) * cellPx
            val oy = (fb.pos.y + 0.5f) * cellPx
            MegaFling.hitTimeMs(hypot(ox - cx, oy - cy), halfBoardPx, slackPx)
        }
    }
    val teikaHitMs = remember(event, cellPx) {
        FloatArray(event.flungTeikas.size) { i ->
            val ft = event.flungTeikas[i]
            // El Teika es un cuerpo con volumen: el Mega lo toca en su BORDE, no en su centro.
            val edgeSlack = slackPx + ft.type.level * 0.5f * cellPx
            MegaFling.hitTimeMs(hypot(ft.x * cellPx - cx, ft.y * cellPx - cy), halfBoardPx, edgeSlack)
        }
    }

    // Dirección de salida (unitaria) desde el centro; lo que está EN el centro se abre en abanico.
    fun outDir(ox: Float, oy: Float, seed: Int): Pair<Float, Float> {
        val dx = ox - cx; val dy = oy - cy
        val len = kotlin.math.sqrt(dx * dx + dy * dy)
        if (len < 1f) {
            val a = seed * 2.3999632f   // ángulo áureo → reparto uniforme
            return kotlin.math.cos(a) to kotlin.math.sin(a)
        }
        return dx / len to dy / len
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Bloques saliendo despedidos por los bordes ──────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            val now = clock.value
            val travel = boardPx * MegaFling.TRAVEL_FRAC
            event.flungBlocks.forEachIndexed { i, fb ->
                // Cada bloque tiene su propio reloj: no se mueve hasta que el radio del Mega lo toca.
                val t = MegaFling.itemProgress(now, blockHitMs[i])
                if (t >= 1f) return@forEachIndexed
                val fade = MegaFling.itemFade(t)   // se desvanece en el último tramo
                val ox = (fb.pos.x + 0.5f) * cellPx
                val oy = (fb.pos.y + 0.5f) * cellPx
                // Dirección de salida inline (sin Pair): en el bucle de DIBUJO evita boxear 2 floats
                // por bloque y por frame — hasta 64 bloques a 60 fps era una ráfaga de GC en el clímax.
                val vdx = ox - cx; val vdy = oy - cy
                val vlen = kotlin.math.sqrt(vdx * vdx + vdy * vdy)
                val dx: Float; val dy: Float
                if (vlen < 1f) {
                    val ang = i * 2.3999632f   // ángulo áureo → reparto uniforme
                    dx = kotlin.math.cos(ang); dy = kotlin.math.sin(ang)
                } else {
                    dx = vdx / vlen; dy = vdy / vlen
                }
                val bcx = ox + dx * t * travel
                val bcy = oy + dy * t * travel
                val tl = Offset(bcx - cellPx / 2f, bcy - cellPx / 2f)
                val full = Size(cellPx, cellPx)
                val isGold = fb.targetColor != null && !fb.isCrystal && fb.colorType != WILDCARD_COLOR
                val pal = woodPalette(skin, fb.colorType, isGold)
                val spin = t * (if (i % 2 == 0) 30f else -30f)
                withTransform({ rotate(spin, Offset(bcx, bcy)) }) {
                    drawStyledTile(skin.style, tl, full, pal[0], pal[1], pal[2], skin.cornerFraction, fade)
                    fb.targetColor?.let { tc ->
                        val bmp = targetStarBitmaps[tc] ?: return@let
                        val starW = cellPx * skin.starScale
                        val starH = starW * bmp.height.toFloat() / bmp.width.toFloat()
                        drawImage(
                            image = bmp,
                            srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                            srcSize = androidx.compose.ui.unit.IntSize(bmp.width, bmp.height),
                            dstOffset = androidx.compose.ui.unit.IntOffset(
                                (bcx - starW / 2f).roundToInt(), (bcy - starH / 2f).roundToInt()
                            ),
                            dstSize = androidx.compose.ui.unit.IntSize(starW.roundToInt(), starH.roundToInt()),
                            alpha = fade
                        )
                    }
                }
            }

            // ── Bloques CON ESTRELLA: se ROMPEN en su celda (2026-08-09) ─────────
            // No vuelan: el Mega los revienta al alcanzarlos y su estrella se va al panel por su
            // propio `TargetCollected` (mismo retardo, así que la estrella arranca justo del
            // estallido). Aquí solo se dibuja la ficha reventando: crece un poco y se apaga.
            event.brokenBlocks.forEachIndexed { i, fb ->
                val t = ((now - brokenHitMs[i]) / MEGA_BREAK_MS).coerceIn(0f, 1f)
                if (t <= 0f || t >= 1f) return@forEachIndexed
                val fade = 1f - t
                val scale = 1f + t * 0.45f
                val side = cellPx * scale
                val bcx = (fb.pos.x + 0.5f) * cellPx
                val bcy = (fb.pos.y + 0.5f) * cellPx
                val pal = skin.colorsFor(fb.colorType.coerceAtLeast(0))
                drawStyledTile(
                    skin.style,
                    Offset(bcx - side / 2f, bcy - side / 2f),
                    Size(side, side),
                    pal[0], pal[1], pal[2], skin.cornerFraction, fade
                )
            }
        }

        // ── Los demás Teikas del tablero: salen despedidos igual (su icono real) ──
        event.flungTeikas.forEachIndexed { i, ft ->
            val ox = ft.x * cellPx
            val oy = ft.y * cellPx
            val (dx, dy) = outDir(ox, oy, i + event.flungBlocks.size)
            val diameter = cellSize * ft.type.level
            val radPx = ft.type.level * 0.5f * cellPx
            TeikaIcon(
                type = ft.type,
                modifier = Modifier
                    .size(diameter)
                    .offset {
                        val t = MegaFling.itemProgress(clock.value, teikaHitMs[i])
                        val travel = t * boardPx * MegaFling.TRAVEL_FRAC
                        IntOffset(
                            (ox + dx * travel - radPx).roundToInt(),
                            (oy + dy * travel - radPx).roundToInt()
                        )
                    }
                    .graphicsLayer {
                        val t = MegaFling.itemProgress(clock.value, teikaHitMs[i])
                        val s = teikaVisualScale(ft.type)
                        scaleX = s; scaleY = s
                        alpha = MegaFling.itemFade(t)
                    }
            )
        }

        // ── Rayos divinos: giran DETRÁS del Mega mientras dura su reinado ──
        if (!reducedFx) Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val rayLen = boardPx * 0.95f
                    val ray = Path().apply {
                        moveTo(0f, 0f)
                        lineTo(rayLen, -rayLen * 0.13f)
                        lineTo(rayLen, rayLen * 0.13f)
                        close()
                    }
                    val rayBrush = Brush.horizontalGradient(
                        listOf(Color(0xFFFFE9A8), Color(0x00FFC93D)), startX = 0f, endX = rayLen
                    )
                    onDrawBehind {
                        val env = (clock.value / 260f).coerceIn(0f, 1f) * megaAlpha.value
                        if (env <= 0.01f) return@onDrawBehind
                        val spin = glory.value * 140f
                        for (r in 0 until MEGA_RAYS) {
                            withTransform({
                                translate(cx, cy)
                                rotate(spin + r * 360f / MEGA_RAYS, pivot = Offset.Zero)
                            }) {
                                drawPath(
                                    ray, rayBrush,
                                    alpha = env * (if (r % 2 == 0) 0.55f else 0.3f),
                                    blendMode = BlendMode.Plus
                                )
                            }
                        }
                    }
                }
        )

        // ── Mega Teika: irrumpe con pop y llena TODO el tablero, luego se retira ──
        TeikaIcon(
            type = com.cukiti.teikafusion.model.TeikaType.MEGA_TEIKA,
            modifier = Modifier
                .size(cellSize * GameState.GRID_SIZE)
                .align(Alignment.Center)
                .graphicsLayer {
                    val s = MegaFling.growScale(clock.value) * megaExit.value *
                        teikaVisualScale(com.cukiti.teikafusion.model.TeikaType.MEGA_TEIKA)
                    scaleX = s; scaleY = s
                    alpha = megaAlpha.value
                }
        )

        // ── Fogonazo + tres ondas de choque desde el centro ─────────────────────
        if (!reducedFx) Spacer(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val ringColors = arrayOf(Color.White, Color(0xFFFFC93D), Color(0xFFB07CF0))
                    val c = Offset(cx, cy)
                    onDrawBehind {
                        val now = clock.value
                        val flash = 1f - (now / MEGA_FLASH_MS).coerceIn(0f, 1f)
                        if (flash > 0f) drawRect(Color.White, alpha = 0.9f * flash * flash)
                        for (r in 0 until 3) {
                            val t = ((now - r * MEGA_RING_STAGGER_MS) / MEGA_RING_MS).coerceIn(0f, 1f)
                            if (t <= 0f || t >= 1f) continue
                            val eased = 1f - (1f - t) * (1f - t) * (1f - t)
                            val fade = 1f - t
                            drawCircle(
                                ringColors[r], radius = boardPx * (0.08f + 0.85f * eased), center = c,
                                alpha = fade * 0.85f,
                                style = Stroke(width = cellPx * (0.08f + 0.40f * fade)),
                                blendMode = BlendMode.Plus
                            )
                        }
                    }
                }
        )

        // ── Rótulo «¡MEGA TEIKA!»: entra de golpe y se va con el Mega ──────────
        if (!reducedFx) {
            val labelSp = with(density) { (cellPx * 0.82f).toSp() }
            val labelStyle = androidx.compose.ui.text.TextStyle(
                fontSize = labelSp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                letterSpacing = 1.5.sp
            )
            val label = "¡MEGA TEIKA!"
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset { IntOffset(0, (cellPx * 2.4f).roundToInt()) }
                    .graphicsLayer {
                        val k = labelIn.value
                        val s = 2.4f - 1.4f * k
                        scaleX = s; scaleY = s
                        alpha = k.coerceIn(0f, 1f) * megaAlpha.value
                    }
            ) {
                Text(
                    label,
                    style = labelStyle.copy(
                        color = Color(0xFF2A0F4A),
                        drawStyle = Stroke(
                            width = cellPx * 0.16f,
                            join = androidx.compose.ui.graphics.StrokeJoin.Round
                        )
                    )
                )
                Text(
                    label,
                    style = labelStyle.copy(
                        brush = Brush.verticalGradient(
                            listOf(Color(0xFFFFF4C2), Color(0xFFFFC93D), Color(0xFFFF8A1F))
                        ),
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color(0xFFFFC93D).copy(alpha = 0.8f), blurRadius = cellPx * 0.5f
                        )
                    )
                )
            }
        }
    }
}

/** Coreografía del NACIMIENTO del Mega tras el colapso estelar (2026-09-24). */
private const val MEGA_FLASH_MS = 260f
private const val MEGA_RING_MS = 680f
private const val MEGA_RING_STAGGER_MS = 110f
private const val MEGA_LABEL_DELAY_MS = 360L
private const val MEGA_RAYS = 14

// ── Combo Grace Warning — aviso de última oportunidad ────────────────────────────────

@Composable
fun ComboGraceVFX(
    event: GameEvent.ComboGrace,
    onComplete: () -> Unit
) {
    val scale  = remember { Animatable(0f) }
    val alpha  = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { scale.animateTo(1.3f, spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMedium))
                 scale.animateTo(1f, tween(120)) }
        launch { alpha.animateTo(1f, tween(100)) }
        launch { offsetY.animateTo(-80f, tween(1600, easing = FastOutSlowInEasing)) }
        delay(900)
        launch { alpha.animateTo(0f, tween(400)) }
        delay(700)
        onComplete()
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(0, offsetY.value.roundToInt()) }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value }
    ) {
        // Sombra
        Text("⚠ GRACIA ×${event.comboStreak}", color = Color.Black.copy(alpha = 0.6f),
            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.offset(2.dp, 2.dp))
        Text("⚠ GRACIA ×${event.comboStreak}", color = Color(0xFFFF6E40),
            fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ── "No hay espacio": el tablero se ENFRÍA ──────────────────────────────────────────────────

/**
 * Capa que se dibuja SOBRE el tablero en el instante de la derrota por falta de espacio: viñeta
 * roja que late desde los bordes hacia dentro. El tablero ya NO se apaga entero (referencia Block
 * Blast): el mensaje va en una BANDA horizontal que cruza el centro (ver GameScreen) y el resto
 * del tablero queda iluminado — el jugador puede comprobar que, en efecto, no cabía nada. Sigue
 * encadenando de forma natural con el scrim de la oferta de revivir, ~1,1 s después.
 *
 * El latido vive dentro de este composable, que solo se monta mientras el aviso está visible → no
 * deja una animación infinita corriendo tras desaparecer.
 */
@Composable
fun NoSpaceBoardChill(modifier: Modifier = Modifier) {
    val pulse = rememberInfiniteTransition(label = "noSpaceChill")
    val breath by pulse.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(760, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "noSpaceBreath"
    )

    Spacer(
        modifier = modifier.drawWithCache {
            val radius = size.minDimension * 0.78f
            // Viñeta: centro limpio (el tablero se sigue leyendo) → orillas rojas.
            val vignette = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color(0x00E53935), Color(0xCCB71C1C)),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = radius
            )
            val corner = CornerRadius(16.dp.toPx())
            onDrawBehind {
                // Sin apagón global: solo la viñeta roja latiendo — la banda central lleva el texto.
                drawRoundRect(brush = vignette, alpha = breath, cornerRadius = corner)
            }
        }
    )
}

// NOTA (it. 11): aquí vivió `ComboStreakPill`, un contador "COMBO ×N" persistente en la esquina del
// tablero. Se RETIRÓ tras el playtest: el usuario lo vio fuera de sitio. El combo ya se comunica de
// sobra con [ComboTextVFX] / [EpicComboVFX] (la palabra), el tono ascendente de `playCombo` y el
// marco arcoíris de pantalla ([ComboBoardGlowVFX]). No volver a añadirlo sin pedirlo él.

// ── Combo Screen Glow — marco arcoíris pulsante alrededor de la PANTALLA durante el combo ──
// Se dibuja edge-to-edge a nivel de GameScreen (no del tablero) → enmarca el borde físico del
// teléfono y la cámara. Arcoíris estático que late + destella con cada línea; intensidad por tier.

@Composable
fun ComboBoardGlowVFX(
    comboStreak: Int,
    screenCornerPx: Float = 0f,
    modifier: Modifier = Modifier
) {
    val active = comboStreak >= 2
    val density = LocalDensity.current

    // PROGRESO CONTINUO del combo (ya NO se capa por tier): racha 2 → ~0, racha ≥16 → 1. El marco,
    // el resplandor y la viñeta de color crecen con CADA eslabón → sensación de progreso real hacia
    // un clímax. Se relaja suavemente al subir/perder racha.
    val growthState = animateFloatAsState(
        targetValue = if (active) ((comboStreak - 1) / 15f).coerceIn(0.06f, 1f) else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "comboFrameGrowth"
    )

    // Color del tier actual → el marco/viñeta vira de verde a dorado conforme sube la racha.
    val tierColor = comboTierColor(comboStreak)

    // Viñeta de color recordada (CERO Brush alloc por frame): centro transparente → orillas teñidas
    // del color del tier. Radio/centro por defecto = se ajustan al área de dibujo. Se modula su
    // visibilidad con el parámetro `alpha` de drawRect, no recreando el brush.
    val vignette = remember(tierColor) {
        Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, tierColor))
    }

    // Pulso BRILLANTE en cada incremento de racha (cierre de "línea completada").
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(comboStreak) {
        if (comboStreak >= 2) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(680, easing = FastOutSlowInEasing))
        } else {
            pulse.snapTo(0f)
        }
    }

    // Respiración SUAVE del grosor (el marco "late", NO gira). Reloj propio que corre solo
    // mientras hay combo → cero frames en reposo. Period ≈ 1.6 s (lento, no mareante).
    // BUCLE INFINITO: el tween de 100 s NO debe terminar nunca; si el combo dura más (teléfono
    // quieto en una racha) hay que re-encadenar el avance o el efecto se congela. Avance
    // ACUMULATIVO (value + …) → sin saltos de fase al re-encadenar.
    val breathClock = remember { Animatable(0f) }
    LaunchedEffect(active) {
        while (active) {
            breathClock.animateTo(breathClock.value + 100_000f, tween(100_000, easing = LinearEasing))
        }
    }

    // Arcoíris remembered (CERO Brush alloc por frame). ESTÁTICO: el sweepGradient ya envuelve
    // el perímetro (cada borde fluye por el espectro). No se rota → no hay giro brusco ni
    // cuñas diagonales cruzando la grilla; solo late y destella con cada línea.
    val rainbow = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFFF1744), Color(0xFFFF9100), Color(0xFFFFEA00),
                Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFF2979FF),
                Color(0xFFD500F9), Color(0xFFFF1744)
            )
        )
    }

    // Conversiones dp→px HOISTEADAS fuera del draw (no cambian por frame). El grosor va de ~6dp
    // (combo recién nacido) hasta ~6+30 = 36dp (combo masivo) → el marco crece MUCHO con la racha.
    val minThickPx      = with(density) { 6.dp.toPx() }
    val maxThickGainPx  = with(density) { 30.dp.toPx() }
    val fallbackOuterPx = with(density) { 40.dp.toPx() }
    val minCrPx         = with(density) { 8.dp.toPx() }

    // Esta capa se dibuja a PANTALLA COMPLETA (edge-to-edge), DETRÁS de la UI de juego → el marco
    // abraza el borde físico del teléfono (cámara/notch) y el tablero opaco + botones lo tapan, así
    // no pinta sobre la grilla ni oculta info. Cero asignaciones por frame: brush remembered, sin
    // lambdas ni Offset/Size/CornerRadius con heap (value classes). Solo redibuja con combo activo.
    Canvas(modifier = modifier.fillMaxSize()) {
        val g   = growthState.value
        val pul = pulse.value
        if (g <= 0.001f && pul <= 0.01f) return@Canvas

        val intensity = (0.30f + g * 0.62f + pul * 0.35f).coerceIn(0f, 1f)

        // 1) VIÑETA de color hacia DENTRO: las orillas de la pantalla se tiñen del color del tier y
        //    se intensifican con la racha → "la pantalla se vuelve épica" sin tapar el tablero (centro
        //    transparente; el tablero opaco y la UI van encima). Brush recordado + alpha modulado.
        val vigA = ((g * 0.32f + pul * 0.14f) * intensity).coerceIn(0f, 1f)
        if (vigA > 0.01f) {
            drawRect(brush = vignette, alpha = vigA, blendMode = BlendMode.Plus)
        }

        // 2) MARCO arcoíris: el grosor crece con la racha (g).
        val breath = 1f + 0.10f * sin(breathClock.value * 0.004f)
        val w = (minThickPx + maxThickGainPx * g) * breath * (1f + pul * 0.35f)
        val tl = Offset(w * 0.5f, w * 0.5f)
        val sz = Size(size.width - w, size.height - w)
        // Esquinas REDONDEADAS premium. Radio EXTERIOR objetivo = el de la esquina redondeada REAL
        // del móvil cuando se conoce (calza con la curva del display → sin pico ni hueco); si no, un
        // default redondeado. Trazo centrado en el path (inset w/2) → radio del PATH = exterior − w/2.
        val targetOuter = if (screenCornerPx > 0f) screenCornerPx else fallbackOuterPx
        val cr = (targetOuter - w * 0.5f)
            .coerceIn(minCrPx, minOf(size.width, size.height) * 0.5f - w)
        val corner = CornerRadius(cr, cr)

        // Glow exterior DELGADO pegado al borde.
        drawRoundRect(
            brush = rainbow, topLeft = tl, size = sz, cornerRadius = corner,
            style = Stroke(width = w * 1.8f),
            blendMode = BlendMode.Plus, alpha = intensity * 0.18f
        )
        // Marco neón nítido (arcoíris envolviendo el perímetro de la pantalla, estable).
        drawRoundRect(
            brush = rainbow, topLeft = tl, size = sz, cornerRadius = corner,
            style = Stroke(width = w), alpha = intensity
        )
        // Destello blanco en el golpe del pulso (sincroniza con la rotura de línea).
        if (pul > 0.01f) {
            drawRoundRect(
                color = Color.White.copy(alpha = pul * 0.5f * intensity),
                topLeft = tl, size = sz, cornerRadius = corner,
                style = Stroke(width = (w * 0.4f).coerceAtLeast(2f)),
                blendMode = BlendMode.Plus
            )
        }
    }
}

// (Las partículas de combo a pantalla completa — ComboAmbientParticles/ComboBurstParticle — se
//  eliminaron a petición del usuario. El marco arcoíris del combo y el overlay épico se conservan.)

// ─────────────────────────────────────────────────────────────────────────────
// Companion ability VFX — un efecto temático por habilidad (CompanionAbilityFired)
// ─────────────────────────────────────────────────────────────────────────────

private data class AbilityTheme(
    val iconRes: Int,
    val core: Color,
    val ring: Color,
    val rings: Int = 2,
    val sparks: Int = 12
)

private fun abilityTheme(id: String): AbilityTheme = when (id) {
    "cristal"   -> AbilityTheme(R.raw.ic_ability_cristal, Color(0xFFB3E5FC), Color(0xFFFFFFFF), 3, 8)
    "dormilona" -> AbilityTheme(R.raw.ic_ability_dormilona, Color(0xFF9FA8DA), Color(0xFFC5CAE9), 2, 6)
    "miedoso"   -> AbilityTheme(R.raw.ic_ability_miedoso, Color(0xFFCFD8DC), Color(0xFFECEFF1), 2, 14)
    "planta"    -> AbilityTheme(R.raw.ic_ability_planta, Color(0xFF81C784), Color(0xFFC8E6C9), 2, 12)
    "electrico" -> AbilityTheme(R.raw.ic_ability_electrico, Color(0xFFFFD54F), Color(0xFFFFF59D), 2, 16)
    "fuego"     -> AbilityTheme(R.raw.ic_ability_fuego, Color(0xFFFF7043), Color(0xFFFFCCBC), 3, 14)
    "ninja"     -> AbilityTheme(R.raw.ic_ability_ninja, Color(0xFF4DB6AC), Color(0xFFB2DFDB), 2, 10)
    "hielo"     -> AbilityTheme(R.raw.ic_ability_hielo, Color(0xFF81D4FA), Color(0xFFE1F5FE), 3, 12)
    "fantasma"  -> AbilityTheme(R.raw.ic_ability_fantasma, Color(0xFFB39DDB), Color(0xFFEDE7F6), 2, 8)
    "gloton"    -> AbilityTheme(R.raw.ic_ability_gloton, Color(0xFF4FC3F7), Color(0xFFB3E5FC), 2, 12)
    "toxico"    -> AbilityTheme(R.raw.ic_ability_toxico, Color(0xFF9CCC65), Color(0xFFDCEDC8), 3, 14)
    "estrella",
    "ESTRELLA_LLUVIA" -> AbilityTheme(R.raw.ic_ability_estrella, Color(0xFFFFD740), Color(0xFFFFF8E1), 2, 18)
    "rey", "REY_DECRETO" -> AbilityTheme(R.raw.ic_ability_rey, Color(0xFFFFCA28), Color(0xFFFFE082), 3, 14)
    "galactico" -> AbilityTheme(R.raw.ic_ability_galactico, Color(0xFF1A1030), Color(0xFFFFD54F), 3, 20)
    else        -> AbilityTheme(R.raw.ic_ability_estrella, Color(0xFFFFD740), Color(0xFFFFF8E1), 2, 12)
}

/**
 * Emblema ilustrado del power-up de [id] (`ic_ability_*`). Desde el rediseño del 2026-09-24 es el
 * icono del OBJETO: lo pintan el Inventario, el selector del panel del nivel y el botón de partida.
 */
fun abilityIconRes(id: String): Int = abilityTheme(id).iconRes

/**
 * Los emblemas `ic_ability_*` miden 1024 px y solo se dibujan en fichas de ~50–120 dp (≤ ~360 px en
 * xxhdpi): decodificarlos a 512 px no pierde nitidez y cuesta la cuarta parte (2026-09-30). Todo
 * [RawImage] de un [abilityIconRes] debe pasar este techo (la caché es por recurso).
 */
const val ABILITY_ICON_MAX_PX = 512

/** Color de acento principal del compañero — usado por la cinemática de entrada y la UI. */
fun companionAccentColor(id: String): Color = abilityTheme(id).core
/** Color secundario (anillos/halo) del compañero. */
fun companionRingColor(id: String): Color = abilityTheme(id).ring

/** Duración del VFX de tablero por habilidad (ms). */
private fun abilityVfxDurationMs(id: String): Int = when (id) {
    "galactico"                             -> 1700   // Eclipse Vivo: disco + corona + noche
    "fuego", "estrella", "ESTRELLA_LLUVIA", "planta", "toxico" -> 1300
    "rey", "hielo", "cristal"               -> 1200
    "electrico", "ninja", "gloton"          -> 1050
    else                                    -> 950
}

/** Estrella de [points] puntas como Path (radio externo [outer], interno [inner]). */
private fun starPath(cx: Float, cy: Float, outer: Float, inner: Float, points: Int, rotDeg: Float): Path {
    val path = Path()
    var angle = Math.toRadians(rotDeg.toDouble()) - PI / 2.0
    val step = PI / points
    path.moveTo(cx + (cos(angle) * outer).toFloat(), cy + (sin(angle) * outer).toFloat())
    for (i in 1 until points * 2) {
        val r = if (i % 2 == 0) outer else inner
        angle += step
        path.lineTo(cx + (cos(angle) * r).toFloat(), cy + (sin(angle) * r).toFloat())
    }
    path.close()
    return path
}

/** Rayo eléctrico quebrado de [from] a [to]. */
private fun DrawScope.drawNeonBolt(from: Offset, to: Offset, color: Color, width: Float, seed: Float, jitter: Float) {
    val path = Path()
    path.moveTo(from.x, from.y)
    val dxl = to.x - from.x; val dyl = to.y - from.y
    val len = hypot(dxl.toDouble(), dyl.toDouble()).toFloat().coerceAtLeast(1f)
    val nx = -dyl / len; val ny = dxl / len
    val segs = 7
    for (i in 1 until segs) {
        val t = i / segs.toFloat()
        val off = sin(t * 14.0 + seed).toFloat() * jitter * (1f - abs(t - 0.5f) * 1.4f)
        path.lineTo(from.x + dxl * t + nx * off, from.y + dyl * t + ny * off)
    }
    path.lineTo(to.x, to.y)
    drawPath(path, color, style = Stroke(width = width))
}

/**
 * VFX premium de habilidad de compañero, enrutado por id. Se dibuja sobre el tablero
 * (Canvas a tamaño completo del board). Desde el 2026-09-24 (P-282) ya no pinta el emblema de la
 * habilidad encima: solo el efecto temático. Dura [abilityVfxDurationMs] y luego
 * invoca [onComplete] para consumir el evento.
 *
 * [center] está en píxeles dentro del tablero. [cellPx] = lado de una celda en px.
 */
@Composable
fun CompanionAbilityVFX(
    companionId: String,
    center: Offset,
    cellPx: Float,
    onComplete: () -> Unit,
    axis: Int = -1
) {
    val theme  = remember(companionId) { abilityTheme(companionId) }
    val accent = theme.core
    val ring   = theme.ring
    val durMs  = remember(companionId) { abilityVfxDurationMs(companionId) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(companionId, center) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(durMs, easing = LinearEasing))
        onComplete()
    }
    val p = progress.value

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val boardPx = cellPx * GameState.GRID_SIZE
            when (companionId) {
                "cristal"   -> drawCrystalShield(center, cellPx, boardPx, accent, ring, p)
                "dormilona" -> drawSleepWaves(center, cellPx, boardPx, accent, ring, p)
                "miedoso"   -> drawWindGust(center, cellPx, boardPx, accent, ring, p)
                "planta"    -> drawVines(center, cellPx, boardPx, accent, ring, p)
                "electrico" -> drawOvercharge(center, cellPx, boardPx, accent, ring, p)
                "fuego"     -> drawFlameLine(center, cellPx, boardPx, accent, ring, p, axis)
                "ninja"     -> drawNinjaDash(center, cellPx, boardPx, accent, ring, p)
                "hielo"     -> drawFrostWave(center, cellPx, boardPx, accent, ring, p)
                "fantasma"  -> drawPoltergeist(center, cellPx, boardPx, accent, ring, p)
                "gloton"    -> drawDevour(center, cellPx, boardPx, accent, ring, p)
                "toxico"    -> drawToxicSpread(center, cellPx, boardPx, accent, ring, p)
                "estrella",
                "ESTRELLA_LLUVIA" -> drawStarRain(center, cellPx, boardPx, accent, ring, p)
                "rey", "REY_DECRETO" -> drawRoyalDecree(center, cellPx, boardPx, accent, ring, p)
                "galactico" -> drawEclipse(center, cellPx, boardPx, accent, ring, p)
                else        -> drawGenericPulse(center, cellPx, boardPx, accent, ring, p)
            }
        }
        // (RETIRADO 2026-09-24, P-282) El EMBLEMA gigante de la habilidad se pintaba aquí, encima del
        // efecto, durante el golpe principal. El usuario lo quitó: el emblema pasó a ser el icono del
        // power-up en el Inventario, el panel del nivel y el botón de partida ([abilityIconRes]).
    }
}

// ── Efectos temáticos (Canvas) ────────────────────────────────────────────────

private fun DrawScope.drawGenericPulse(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val fade = 1f - p
    val maxR = cellPx * 3.2f
    drawCircle(accent.copy(alpha = 0.5f * fade), cellPx * (0.5f + p * 1.3f), center)
    drawCircle(Color.White.copy(alpha = 0.7f * (1f - p * 2f).coerceIn(0f, 1f)), cellPx * (0.25f + p * 0.6f), center)
    for (i in 0 until 2) {
        val rp = (p - i * 0.15f).coerceIn(0f, 1f)
        if (rp <= 0f) continue
        drawCircle(ring.copy(alpha = 0.55f * (1f - rp)), maxR * rp, center, style = Stroke(width = cellPx * 0.10f))
    }
    for (k in 0 until 12) {
        val rad = Math.toRadians(k * 30.0)
        val rr = maxR * 0.9f * p
        drawCircle(accent.copy(alpha = fade), cellPx * 0.10f * fade,
            Offset(center.x + (cos(rad) * rr).toFloat(), center.y + (sin(rad) * rr).toFloat()))
    }
}

/**
 * Galo / Eclipse Vivo: un disco oscuro con corona dorada baja sobre el clúster de madera más
 * denso ([center] ya viene en la zona eclipsada, no en el centro del tablero). El interior entra
 * en "noche" con destellos de estrella mientras la madera se pulveriza, y al final un flash marca
 * la salida del eclipse.
 */
private fun DrawScope.drawEclipse(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val discR = cellPx * 1.95f
    val grow = (p / 0.28f).coerceIn(0f, 1f)                       // el disco baja y crece
    val fade = (1f - (p - 0.82f) / 0.18f).coerceIn(0f, 1f)        // la corona se disipa al final
    val r = discR * grow
    // 1) Sombra de noche bajo el disco.
    drawCircle(Color.Black.copy(alpha = 0.40f * fade), r * 1.15f, center)
    // 2) Disco oscuro central (accent = índigo profundo).
    drawCircle(accent.copy(alpha = 0.92f * fade), r, center)
    drawCircle(Color.Black.copy(alpha = 0.55f * fade), r * 0.82f, center)
    // 3) Corona dorada: anillo brillante + rayos que titilan en el borde.
    drawCircle(ring.copy(alpha = 0.85f * fade), r * 1.02f, center, style = Stroke(width = cellPx * 0.13f))
    val rays = 20
    for (k in 0 until rays) {
        val a = Math.toRadians(k * (360.0 / rays) + p * 20.0)
        val flick = 0.7f + 0.3f * sin((p * 12f + k))
        val inner = r * 1.05f
        val outer = r * (1.14f + 0.24f * flick)
        val s = Offset(center.x + (cos(a) * inner).toFloat(), center.y + (sin(a) * inner).toFloat())
        val e = Offset(center.x + (cos(a) * outer).toFloat(), center.y + (sin(a) * outer).toFloat())
        drawLine(ring.copy(alpha = 0.7f * fade * flick), s, e, strokeWidth = cellPx * 0.06f)
    }
    // 4) Destellos de estrella dentro de la "noche" (reparto por ángulo áureo).
    for (k in 0 until 10) {
        val a = k * 2.399963f
        val rr = r * (0.15f + 0.7f * ((k * 0.137f) % 1f))
        val tw = (0.5f + 0.5f * sin((p * 16f + k * 1.7f))) * fade
        drawCircle(Color.White.copy(alpha = 0.9f * tw), cellPx * 0.05f,
            Offset(center.x + cos(a) * rr, center.y + sin(a) * rr))
    }
    // 5) Flash de salida del eclipse.
    val flash = ((p - 0.82f) / 0.18f).coerceIn(0f, 1f) * (1f - p).coerceIn(0f, 1f) * 5f
    if (flash > 0f) drawCircle(Color.White.copy(alpha = 0.45f * flash.coerceIn(0f, 1f)), discR * 1.3f, center)
}

/**
 * Fantasma / Poltergeist: el centro implosiona y revienta en una onda espectral que empuja la
 * madera hacia los bordes, con tendones de
 * ectoplasma serpenteando del centro hacia fuera.
 */
private fun DrawScope.drawPoltergeist(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val maxR = boardPx * 0.72f
    // 1) Núcleo espectral que se ilumina y se apaga rápido (la "presencia" que estalla).
    val coreFade = (1f - p * 1.8f).coerceIn(0f, 1f)
    drawCircle(ring.copy(alpha = 0.5f * coreFade), cellPx * (1.0f + p * 2.2f), center)
    drawCircle(Color.White.copy(alpha = 0.85f * coreFade), cellPx * (0.5f + p * 1.2f), center)
    // 2) Onda espectral que se expande hacia los bordes (empuja la madera fuera del centro).
    for (i in 0 until 2) {
        val rp = (p * 1.2f - i * 0.18f).coerceIn(0f, 1f)
        if (rp <= 0f) continue
        drawCircle(
            accent.copy(alpha = 0.55f * (1f - rp)), maxR * rp, center,
            style = Stroke(width = cellPx * (0.20f - i * 0.06f))
        )
    }
    // 3) Tendones de ectoplasma que serpentean del centro hacia afuera + gota fantasmal en la punta.
    val fade = 1f - p
    val wisps = 8
    val reach = maxR * (0.35f + 0.65f * FastOutSlowInEasing.transform(p.coerceIn(0f, 1f)))
    for (k in 0 until wisps) {
        val baseAng = k * (2.0 * PI / wisps) + p * 0.6
        val path = Path()
        path.moveTo(center.x, center.y)
        val segs = 6
        for (s in 1..segs) {
            val t = s / segs.toFloat()
            val wobble = sin(t * 7.0 + k + p * 6.0).toFloat() * cellPx * 0.5f * (1f - t)
            val r = reach * t
            path.lineTo(
                center.x + (cos(baseAng) * r).toFloat() + (cos(baseAng + PI / 2) * wobble).toFloat(),
                center.y + (sin(baseAng) * r).toFloat() + (sin(baseAng + PI / 2) * wobble).toFloat()
            )
        }
        drawPath(path, ring.copy(alpha = 0.5f * fade), style = Stroke(width = cellPx * 0.16f * (0.6f + fade)))
        val hx = center.x + (cos(baseAng) * reach).toFloat()
        val hy = center.y + (sin(baseAng) * reach).toFloat()
        drawCircle(Color.White.copy(alpha = 0.7f * fade), cellPx * 0.18f * (0.5f + fade), Offset(hx, hy))
    }
}

private fun DrawScope.drawCrystalShield(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val grow = FastOutSlowInEasing.transform((p / 0.45f).coerceIn(0f, 1f))
    val fade = (1f - (p - 0.6f).coerceAtLeast(0f) / 0.4f).coerceIn(0f, 1f)
    val c = Offset(boardPx / 2f, boardPx / 2f)
    val r = boardPx * 0.46f * grow
    val hex = Path()
    for (i in 0..6) {
        val a = Math.toRadians(i * 60.0 - 90.0)
        val x = c.x + (cos(a) * r).toFloat(); val y = c.y + (sin(a) * r).toFloat()
        if (i == 0) hex.moveTo(x, y) else hex.lineTo(x, y)
    }
    hex.close()
    drawPath(hex, accent.copy(alpha = 0.16f * fade))
    drawPath(hex, ring.copy(alpha = 0.85f * fade), style = Stroke(width = cellPx * 0.07f))
    for (i in 0 until 6) {
        val a = Math.toRadians(i * 60.0 - 90.0)
        drawLine(ring.copy(alpha = 0.35f * fade), c,
            Offset(c.x + (cos(a) * r).toFloat(), c.y + (sin(a) * r).toFloat()), strokeWidth = cellPx * 0.03f)
    }
    for (k in 0 until 6) {
        val t = (p * 1.6f + k * 0.16f) % 1f
        val a = Math.toRadians(k * 60.0 + p * 120.0)
        drawCircle(Color.White.copy(alpha = (1f - t) * 0.7f * fade), cellPx * 0.11f,
            Offset(c.x + (cos(a) * r * t).toFloat(), c.y + (sin(a) * r * t).toFloat()))
    }
}

private fun DrawScope.drawSleepWaves(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val fade = (1f - (p - 0.6f).coerceAtLeast(0f) / 0.4f).coerceIn(0f, 1f)
    val c = Offset(boardPx / 2f, boardPx / 2f)
    drawRect(accent.copy(alpha = 0.10f * fade), size = size)
    for (i in 0 until 3) {
        val rp = (p * 1.2f - i * 0.2f).coerceIn(0f, 1f)
        if (rp <= 0f) continue
        drawCircle(ring.copy(alpha = 0.4f * (1f - rp) * fade), boardPx * 0.5f * rp, c, style = Stroke(width = cellPx * 0.05f))
    }
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0 + p * 40.0)
        val rr = boardPx * (0.14f + 0.26f * ((p + k * 0.12f) % 1f))
        val sp = starPath(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat(), cellPx * 0.16f, cellPx * 0.06f, 4, p * 120f)
        drawPath(sp, Color.White.copy(alpha = 0.5f * fade))
    }
}

private fun DrawScope.drawWindGust(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val fade = 1f - p
    val sweep = p * boardPx * 1.5f - boardPx * 0.25f
    for (i in 0 until 10) {
        val y = boardPx * (i + 0.5f) / 10f
        val x = sweep - (i % 3) * cellPx * 0.8f
        drawLine(ring.copy(alpha = 0.6f * fade), Offset(x - boardPx * 0.3f, y), Offset(x, y),
            strokeWidth = cellPx * 0.06f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
    for (k in 0 until 3) {
        val cx = sweep - k * cellPx * 1.4f
        val cy = boardPx * (0.3f + 0.2f * k)
        drawArc(ring.copy(alpha = 0.5f * fade), startAngle = p * 360f, sweepAngle = 250f, useCenter = false,
            topLeft = Offset(cx - cellPx * 0.4f, cy - cellPx * 0.4f),
            size = androidx.compose.ui.geometry.Size(cellPx * 0.8f, cellPx * 0.8f), style = Stroke(width = cellPx * 0.05f))
    }
}

private fun DrawScope.drawVines(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val grow = FastOutSlowInEasing.transform((p / 0.7f).coerceIn(0f, 1f))
    val fade = (1f - (p - 0.8f).coerceAtLeast(0f) / 0.2f).coerceIn(0f, 1f)
    val vines = 5
    for (v in 0 until vines) {
        val baseX = boardPx * (v + 0.5f) / vines
        val maxH = boardPx * (0.7f + 0.25f * abs(sin(v * 1.7).toFloat()))
        val h = maxH * grow
        val path = Path()
        path.moveTo(baseX, boardPx)
        val steps = 14
        for (s in 1..steps) {
            val t = s / steps.toFloat()
            val yy = boardPx - h * t
            val xx = baseX + sin(t * 6.0 + v).toFloat() * cellPx * 0.6f
            path.lineTo(xx, yy)
            if (s % 3 == 0) drawCircle(accent.copy(alpha = 0.8f * fade), cellPx * 0.12f, Offset(xx, yy))
        }
        drawPath(path, ring.copy(alpha = 0.85f * fade), style = Stroke(width = cellPx * 0.07f))
        val flower = starPath(baseX, boardPx - h, cellPx * 0.22f, cellPx * 0.09f, 5, p * 90f)
        drawPath(flower, Color(0xFFFFEB3B).copy(alpha = grow * fade))
    }
}

private fun DrawScope.drawOvercharge(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val flick = sin(p * 40.0).toFloat() * 0.5f + 0.5f
    val fade = 1f - p
    val c = Offset(boardPx / 2f, boardPx / 2f)
    val targets = listOf(
        Offset(boardPx * 0.1f, boardPx * 0.1f), Offset(boardPx * 0.9f, boardPx * 0.12f),
        Offset(boardPx * 0.12f, boardPx * 0.9f), Offset(boardPx * 0.88f, boardPx * 0.88f),
        Offset(boardPx * 0.5f, boardPx * 0.05f), Offset(boardPx * 0.5f, boardPx * 0.95f)
    )
    targets.forEachIndexed { i, t ->
        drawNeonBolt(c, t, ring.copy(alpha = (0.5f + 0.5f * flick) * fade), cellPx * 0.05f, i * 3f + p * 30f, cellPx * 0.7f)
        drawNeonBolt(c, t, Color.White.copy(alpha = 0.6f * flick * fade), cellPx * 0.02f, i * 3f + p * 30f, cellPx * 0.5f)
    }
    drawCircle(Color.White.copy(alpha = 0.8f * fade), cellPx * (0.3f + flick * 0.2f), c)
    drawCircle(accent.copy(alpha = 0.5f * fade), cellPx * 0.7f, c)
}

private fun DrawScope.drawFlameLine(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float, axis: Int) {
    val reveal = (p / 0.45f).coerceIn(0f, 1f)
    val fade = (1f - (p - 0.55f).coerceAtLeast(0f) / 0.45f).coerceIn(0f, 1f)
    val len = boardPx * reveal
    // Paleta de fuego real: base roja → naranja → amarillo → núcleo casi blanco.
    val deepRed = Color(0xFFB71C1C)
    val orange  = Color(0xFFFF6D00)
    val yellow  = Color(0xFFFFD54F)
    val core    = Color(0xFFFFF8E1)

    // Dibuja una lengua de fuego (3 capas) que se eleva [h] desde (bx, baseY) hacia -[dir].
    fun tongue(bx: Float, baseY: Float, h: Float, w: Float, seed: Float, horizontal: Boolean) {
        // h>0 sube; para columnas dibujamos horizontal usando rotación de coords manual.
        fun flamePath(scale: Float): Path {
            val flick = sin((seed + p * 26f).toDouble()).toFloat() * 0.18f
            val hh = h * scale
            val ww = w * scale
            return Path().apply {
                if (!horizontal) {
                    moveTo(bx - ww, baseY)
                    cubicTo(bx - ww * 0.6f, baseY - hh * 0.45f, bx - ww * (0.5f - flick), baseY - hh * 0.8f, bx + ww * flick, baseY - hh)
                    cubicTo(bx + ww * (0.5f + flick), baseY - hh * 0.8f, bx + ww * 0.6f, baseY - hh * 0.45f, bx + ww, baseY)
                    close()
                } else {
                    moveTo(baseY, bx - ww)
                    cubicTo(baseY - hh * 0.45f, bx - ww * 0.6f, baseY - hh * 0.8f, bx - ww * (0.5f - flick), baseY - hh, bx + ww * flick)
                    cubicTo(baseY - hh * 0.8f, bx + ww * (0.5f + flick), baseY - hh * 0.45f, bx + ww * 0.6f, baseY, bx + ww)
                    close()
                }
            }
        }
        drawPath(flamePath(1.0f), orange.copy(alpha = 0.85f * fade))
        drawPath(flamePath(0.66f), yellow.copy(alpha = 0.9f * fade))
        drawPath(flamePath(0.32f), core.copy(alpha = 0.95f * fade))
    }

    if (axis == 1) {
        val colX = center.x
        // Resplandor base (brasa) y franja roja.
        drawRect(deepRed.copy(alpha = 0.30f * fade), topLeft = Offset(colX - cellPx * 0.6f, 0f),
            size = androidx.compose.ui.geometry.Size(cellPx * 1.2f, len))
        val n = (len / (cellPx * 0.5f)).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val fy = i * cellPx * 0.5f
            val flick = 0.7f + 0.6f * abs(sin(i * 0.8 + p * 22.0).toFloat())
            tongue(fy + cellPx * 0.25f, colX, cellPx * 1.0f * flick, cellPx * 0.34f, i * 1.7f, horizontal = true)
        }
        // Brasas voladoras.
        for (k in 0 until 18) {
            val prog = (k * 0.137f + p * 1.3f) % 1f
            val ey = ((k * 0.091f + p * 0.7f) % 1f) * len
            val ex = colX + cellPx * (0.4f + 1.8f * prog) * (if (k % 2 == 0) 1f else -1f)
            drawCircle(core.copy(alpha = (1f - prog) * 0.9f * fade), cellPx * 0.05f * (1f - prog), Offset(ex, ey))
        }
    } else {
        val rowY = center.y
        drawRect(deepRed.copy(alpha = 0.30f * fade), topLeft = Offset(0f, rowY - cellPx * 0.6f),
            size = androidx.compose.ui.geometry.Size(len, cellPx * 1.2f))
        val n = (len / (cellPx * 0.5f)).toInt().coerceAtLeast(1)
        for (i in 0 until n) {
            val fx = i * cellPx * 0.5f
            val flick = 0.7f + 0.6f * abs(sin(i * 0.8 + p * 22.0).toFloat())
            tongue(fx + cellPx * 0.25f, rowY, cellPx * 1.0f * flick, cellPx * 0.34f, i * 1.7f, horizontal = false)
        }
        for (k in 0 until 18) {
            val prog = (k * 0.137f + p * 1.3f) % 1f
            val ex = ((k * 0.091f + p * 0.7f) % 1f) * len
            val ey = rowY - cellPx * (0.4f + 1.8f * prog) * (if (k % 2 == 0) 1f else -1f)
            drawCircle(core.copy(alpha = (1f - prog) * 0.9f * fade), cellPx * 0.05f * (1f - prog), Offset(ex, ey))
        }
    }
}

private fun DrawScope.drawNinjaDash(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val fade = 1f - p
    val a = center
    // Doble Sombra: dash diagonal del clon + humo en origen y destino.
    val b = Offset(center.x + boardPx * 0.3f, center.y - boardPx * 0.2f)
    val t = (p / 0.5f).coerceIn(0f, 1f)              // el shuriken viaja de A a B
    val curX = a.x + (b.x - a.x) * t
    val curY = a.y + (b.y - a.y) * t
    // Estela del dash desde A hasta la posición actual.
    drawLine(Color.White.copy(alpha = 0.7f * fade), a, Offset(curX, curY),
        strokeWidth = cellPx * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    // Nubes de humo en ambas celdas (origen y destino del intercambio).
    for (pt in listOf(a, b)) {
        for (k in 0 until 4) {
            val tt = (p + k * 0.22f) % 1f
            drawCircle(
                ring.copy(alpha = (1f - tt) * 0.30f * fade),
                cellPx * (0.25f + tt * 1.0f),
                Offset(pt.x + sin((tt * 6.0 + k)).toFloat() * cellPx * 0.3f, pt.y)
            )
        }
    }
    // Shuriken giratorio que recorre el camino del intercambio.
    drawPath(starPath(curX, curY, cellPx * 0.38f, cellPx * 0.13f, 4, p * 540f), accent.copy(alpha = 0.9f * fade))
    if (t >= 1f) drawCircle(Color.White.copy(alpha = 0.8f * fade), cellPx * 0.12f, b)
}

private fun DrawScope.drawFrostWave(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val fade = (1f - (p - 0.6f).coerceAtLeast(0f) / 0.4f).coerceIn(0f, 1f)
    val c = Offset(boardPx / 2f, boardPx / 2f)
    val frontR = boardPx * 0.6f * (p / 0.5f).coerceIn(0f, 1f)
    drawCircle(accent.copy(alpha = 0.15f * fade), frontR, c)
    drawCircle(ring.copy(alpha = 0.6f * fade), frontR, c, style = Stroke(width = cellPx * 0.08f))
    for (k in 0 until 10) {
        val a = Math.toRadians(k * 36.0 + p * 30.0)
        val rr = frontR * (0.4f + 0.5f * ((k * 0.13f + p) % 1f))
        val fx = c.x + (cos(a) * rr).toFloat(); val fy = c.y + (sin(a) * rr).toFloat()
        for (arm in 0 until 6) {
            val aa = Math.toRadians(arm * 60.0 + p * 60.0)
            drawLine(Color.White.copy(alpha = 0.7f * fade), Offset(fx, fy),
                Offset(fx + (cos(aa) * cellPx * 0.2f).toFloat(), fy + (sin(aa) * cellPx * 0.2f).toFloat()),
                strokeWidth = cellPx * 0.03f)
        }
    }
}

private fun DrawScope.drawDevour(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val fade = 1f - p
    val c = Offset(boardPx / 2f, boardPx / 2f)
    for (k in 0 until 18) {
        val a = Math.toRadians(k * 20.0 + p * 50.0)
        val t = (k * 0.07f + p * 1.3f) % 1f
        val rr = boardPx * 0.55f * (1f - t)
        drawCircle(ring.copy(alpha = t * 0.7f * fade), cellPx * 0.1f * (1f - t * 0.5f),
            Offset(c.x + (cos(a) * rr).toFloat(), c.y + (sin(a) * rr).toFloat()))
    }
    val mouthR = cellPx * (0.8f + 0.3f * sin(p * 12.0).toFloat())
    drawCircle(Color.Black.copy(alpha = 0.55f * fade), mouthR, c)
    drawCircle(accent.copy(alpha = 0.8f * fade), mouthR, c, style = Stroke(width = cellPx * 0.1f))
}

private fun DrawScope.drawToxicSpread(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val fade = (1f - (p - 0.7f).coerceAtLeast(0f) / 0.3f).coerceIn(0f, 1f)
    val c = center
    val poolR = cellPx * (0.5f + 2f * (p / 0.5f).coerceIn(0f, 1f))
    drawCircle(accent.copy(alpha = 0.25f * fade), poolR, c)
    drawCircle(ring.copy(alpha = 0.5f * fade), poolR, c, style = Stroke(width = cellPx * 0.05f))
    for (k in 0 until 12) {
        val t = (k * 0.09f + p * 1.2f) % 1f
        val bx = c.x + sin(k * 2.0 + t * 6.0).toFloat() * cellPx * 0.8f
        val by = c.y - t * cellPx * 2.5f
        drawCircle(ring.copy(alpha = (1f - t) * 0.7f * fade), cellPx * (0.08f + 0.1f * t), Offset(bx, by))
    }
    for (k in 0 until 8) {
        val a = Math.toRadians(k * 45.0)
        drawCircle(accent.copy(alpha = 0.6f * fade), cellPx * 0.1f,
            Offset(c.x + (cos(a) * poolR * 1.1f).toFloat(), c.y + (sin(a) * poolR * 1.1f).toFloat()))
    }
}

private fun DrawScope.drawStarRain(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val fade = (1f - (p - 0.7f).coerceAtLeast(0f) / 0.3f).coerceIn(0f, 1f)
    val n = 9
    for (k in 0 until n) {
        val sx = boardPx * (k + 0.5f) / n + sin(k * 3.0).toFloat() * cellPx * 0.3f
        val t = (p * 1.3f + k * 0.11f) % 1f
        val sy = -cellPx + t * (boardPx + cellPx)
        drawLine(ring.copy(alpha = 0.4f * fade), Offset(sx, sy - cellPx * 0.9f), Offset(sx, sy), strokeWidth = cellPx * 0.05f)
        drawPath(starPath(sx, sy, cellPx * 0.3f, cellPx * 0.12f, 5, t * 180f), accent.copy(alpha = 0.9f * fade))
        drawCircle(Color.White.copy(alpha = 0.7f * fade), cellPx * 0.06f, Offset(sx, sy))
    }
}

private fun DrawScope.drawRoyalDecree(center: Offset, cellPx: Float, boardPx: Float, accent: Color, ring: Color, p: Float) {
    val c = Offset(boardPx / 2f, boardPx / 2f)
    val grow = FastOutSlowInEasing.transform((p / 0.4f).coerceIn(0f, 1f))
    val fade = (1f - (p - 0.55f).coerceAtLeast(0f) / 0.45f).coerceIn(0f, 1f)
    // Resplandor dorado de fondo (luz divina inundando el tablero).
    drawCircle(
        brush = androidx.compose.ui.graphics.Brush.radialGradient(
            colors = listOf(Color(0xFFFFE082).copy(alpha = 0.22f * fade), Color.Transparent),
            center = c, radius = boardPx * 0.6f
        ),
        radius = boardPx * 0.6f, center = c
    )
    // Rayos de luz rotando lentamente (god rays).
    val rays = 12
    for (k in 0 until rays) {
        val a = Math.toRadians(k * (360.0 / rays) + p * 30.0)
        val len = boardPx * 0.55f * grow
        val w = cellPx * (0.10f + 0.06f * sin(p * 6.2831f + k).toFloat())
        drawLine(
            Color(0xFFFFF59D).copy(alpha = 0.18f * fade), c,
            Offset(c.x + (cos(a) * len).toFloat(), c.y + (sin(a) * len).toFloat()),
            strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
    for (i in 0 until 3) {
        val rp = (p * 1.2f - i * 0.18f).coerceIn(0f, 1f)
        if (rp <= 0f) continue
        drawCircle(ring.copy(alpha = 0.5f * (1f - rp) * fade), boardPx * 0.55f * rp, c, style = Stroke(width = cellPx * 0.07f))
    }
    val cw = cellPx * 2.2f * grow; val ch = cellPx * 1.4f * grow
    val left = c.x - cw / 2f; val baseY = c.y + ch * 0.4f; val topY = c.y - ch * 0.5f
    val crown = Path()
    crown.moveTo(left, baseY)
    crown.lineTo(left, topY + ch * 0.3f)
    crown.lineTo(left + cw * 0.2f, c.y)
    crown.lineTo(left + cw * 0.35f, topY)
    crown.lineTo(left + cw * 0.5f, c.y - ch * 0.1f)
    crown.lineTo(left + cw * 0.65f, topY)
    crown.lineTo(left + cw * 0.8f, c.y)
    crown.lineTo(left + cw, topY + ch * 0.3f)
    crown.lineTo(left + cw, baseY)
    crown.close()
    drawPath(crown, accent.copy(alpha = 0.9f * fade))
    drawPath(crown, Color(0xFFFFF59D).copy(alpha = fade), style = Stroke(width = cellPx * 0.05f))
    // Destello coronario al asentarse la corona (pico ~p=0.4).
    val coronate = (1f - kotlin.math.abs(p - 0.4f) / 0.18f).coerceIn(0f, 1f)
    if (coronate > 0.01f) {
        drawCircle(Color.White.copy(alpha = 0.75f * coronate), cellPx * (0.4f + 2.2f * (1f - coronate)), c)
    }
    // Gemas brillantes de la corona con destello pulsante.
    for (g in 0 until 3) {
        val tw = 0.6f + 0.4f * sin(p * 6.2831f * 2f + g).toFloat()
        drawCircle(Color.White.copy(alpha = 0.85f * fade), cellPx * (0.08f + 0.04f * tw), Offset(left + cw * (0.25f + g * 0.25f), baseY - ch * 0.25f))
    }
    // Polvo dorado descendente (chispas que llueven sobre el reino).
    for (k in 0 until 14) {
        val seed = k * 0.137f
        val ph = (p * 1.4f + seed) % 1f
        val sx = c.x + ((seed * 2f - 1f) * boardPx * 0.5f)
        val sy = c.y - boardPx * 0.35f + ph * boardPx * 0.7f
        drawCircle(Color(0xFFFFE082).copy(alpha = (1f - ph) * 0.7f * fade), cellPx * 0.05f, Offset(sx, sy))
    }
}


// Cache de las estrellas de objetivo como ImageBitmap (decodifica el PNG 1 sola vez por color; los VFX
// de caída se crean muchas veces por partida).
private val targetStarBitmapCache =
    java.util.concurrent.ConcurrentHashMap<com.cukiti.teikafusion.model.TargetColor, androidx.compose.ui.graphics.ImageBitmap>()
internal fun targetStarBitmaps(ctx: android.content.Context): Map<com.cukiti.teikafusion.model.TargetColor, androidx.compose.ui.graphics.ImageBitmap> {
    fun load(tc: com.cukiti.teikafusion.model.TargetColor, resId: Int) =
        targetStarBitmapCache.getOrPut(tc) { android.graphics.BitmapFactory.decodeResource(ctx.resources, resId).asImageBitmap() }
    return mapOf(
        com.cukiti.teikafusion.model.TargetColor.RED    to load(com.cukiti.teikafusion.model.TargetColor.RED,    R.drawable.target_red),
        com.cukiti.teikafusion.model.TargetColor.BLUE   to load(com.cukiti.teikafusion.model.TargetColor.BLUE,   R.drawable.target_blue),
        com.cukiti.teikafusion.model.TargetColor.PURPLE to load(com.cukiti.teikafusion.model.TargetColor.PURPLE, R.drawable.target_purple),
        com.cukiti.teikafusion.model.TargetColor.PINK   to load(com.cukiti.teikafusion.model.TargetColor.PINK,   R.drawable.target_pink)
    )
}













// ── Cielo Estelar / ANCLA ÚNICA DEL ASTRO ───────────────────────────────────────────────────────
/**
 * Lado de la caja de la Lottie del astro, en CELDAS. Lo comparten los TRES overlays del bioma
 * (aviso, retirada y disparo) a propósito: montan la misma Lottie y se relevan unos a otros en el
 * mismo frame —`starAstroEmerged = false` y el `AstroStrike` viajan en el MISMO update de estado—,
 * así que cualquier diferencia de tamaño se ve como un salto justo en el momento del disparo.
 * (Antes: 4,0 en el aviso y 3,6 en el disparo → el astro encogía un 10 % de golpe al disparar.)
 */
private const val ASTRO_BOX_CELLS = 3.6f

/** Fracción de la caja que el astro deja POR ENCIMA del borde superior del tablero, en reposo. */
private const val ASTRO_REST_OFFSET = 0.12f

/**
 * Y del CENTRO del disco del astro, dado el mismo [offsetFraction] que recibe
 * `.offset(y = -astroSize * f)` bajo `Alignment.TopCenter`. Las Lottie (`astro_sol` / `astro_luna`,
 * 300×300) llevan el disco centrado en su caja, así que el centro de la caja ES el centro del astro.
 *
 * **Es la única fuente de verdad de dónde está el astro.** Cierra el bug del playtest 2026-08-30
 * («el láser sale desde arriba en el borde del tablero y no desde el centro del sol»): el origen del
 * láser y de los rayos eran constantes sueltas (`Offset(w/2, 0f)` y `cellPx * 0.15f`) que nadie
 * sincronizaba con la posición real de la Lottie — el disco quedaba **1,2-1,4 celdas por debajo**
 * del punto del que salía su propio rayo.
 *
 * **El escalado NO entra aquí a propósito**: los overlays laten con `graphicsLayer`, que transforma
 * alrededor del centro y por tanto no lo mueve. Con el `.size(astroSize * bob)` de antes —anclado
 * arriba por `TopCenter`— el Sol bajaba +0,29 celdas mientras cargaba el láser, así que el desfase
 * con el rayo CRECÍA justo en el instante que más se mira.
 */
private fun astroAnchorY(astroSizePx: Float, offsetFraction: Float): Float =
    astroSizePx * (0.5f - offsetFraction)

/**
 * Cielo Estelar / Evento Astral (AVISO persistente): el Sol o la Luna BAJAN DESDE ARRIBA.
 * Marca Teika (halo vivo) y, si es Luna, las maderas pre-elegidas ([moonWoodCells]) en plateado.
 */
@Composable
fun AstroEventOverlay(
    body: Int,
    targetCells: Set<Position>,
    cellPx: Float,
    targetTeikaId: String? = null,
    /**
     * Los cuerpos, como `State` que se lee al DIBUJAR (auditoría de fluidez 2026-09-29): cambian cada tick
     * de física, y recibirlos como lista obligaba a quien llama a leerlos en composición.
     */
    teikaBodies: androidx.compose.runtime.State<List<com.cukiti.teikafusion.viewmodel.TeikaBody>>? = null,
    moonWoodCells: Set<Position> = emptySet()
) {
    val isSun = body == 1
    val tint = if (isSun) Color(0xFFFF8A24) else Color(0xFF7FA8FF)
    val glowTint = if (isSun) Color(0xFFFFC24D) else Color(0xFFBFE0FF)
    val moonGlow = Color(0xFFB3E5FC)
    val infinite = rememberInfiniteTransition(label = "astro")
    val pulse by infinite.animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "astroPulse"
    )
    // Vaivén ingrávido: el astro nunca se queda clavado — flota despacio arriba y abajo.
    val idleBob by infinite.animateFloat(
        initialValue = -0.015f, targetValue = 0.035f,
        animationSpec = infiniteRepeatable(tween(1700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "astroIdleBob"
    )
    // Entrada DRAMÁTICA: el astro baja del cielo con un rebote elástico y se queda bien a la vista.
    // Rigidez MediumLow (no Low): el aviso solo dura STAR_ASTRO_WARN_MS = **800 ms** antes del
    // disparo autónomo — con el muelle lento el astro aún estaba entrando cuando ya tocaba disparar.
    // (El comentario decía «~1,6 s»; nunca lo fue. Si tocas ese muelle, mide contra los 800 ms.)
    val slide = remember(body) { androidx.compose.animation.core.Animatable(1.35f) }
    LaunchedEffect(body) {
        slide.animateTo(ASTRO_REST_OFFSET, androidx.compose.animation.core.spring(
            dampingRatio = 0.62f, stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ))
    }

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(if (isSun) R.raw.astro_sol else R.raw.astro_luna)
    )
    val lottieProgress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)

    // Halo de reserva (sin Teika vivo al que apuntar): depende de las casillas, no del tiempo.
    val cellHalo: List<Offset> = remember(targetCells, moonWoodCells, cellPx) {
        (targetCells - moonWoodCells).map { p -> Offset((p.x + 0.5f) * cellPx, (p.y + 0.5f) * cellPx) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Capa propia: late cada fotograma mientras el astro está fuera.
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
            // La víctima se busca AL DIBUJAR: el Teika rueda y el halo lo sigue sin recomponer nada.
            val liveVictim = targetTeikaId?.let { id -> teikaBodies?.value?.find { it.id == id } }
            val victimRadiusPx = if (liveVictim != null) liveVictim.radius * cellPx else cellPx * 0.5f
            val w = size.width
            drawRect(Brush.verticalGradient(
                0f to tint.copy(alpha = 0.38f + 0.14f * pulse),
                0.35f to tint.copy(alpha = 0.14f + 0.08f * pulse),
                0.75f to Color.Transparent
            ))
            drawRect(Brush.radialGradient(
                listOf(Color.Transparent, tint.copy(alpha = 0.14f * (0.6f + 0.4f * pulse))),
                center = Offset(w / 2f, size.height * 0.2f), radius = w * 0.9f
            ))
            // Los rayos nacen del CENTRO DEL DISCO, no del borde del tablero — y siguen al astro en
            // su vaivén ingrávido (`idleBob`) y en su entrada elástica (`slide`). Ver [astroAnchorY].
            val origin = Offset(w / 2f, astroAnchorY(cellPx * ASTRO_BOX_CELLS, slide.value + idleBob))
            // Maderas marcadas: rect + rayo (teach de qué bloques se llevará). Plata para la Luna; oro
            // para el Sol cuando no hay Teika y calcina un bloque (presagio, 2026-09-23).
            val woodMark = if (isSun) glowTint else moonGlow
            moonWoodCells.forEach { p ->
                val c = Offset((p.x + 0.5f) * cellPx, (p.y + 0.5f) * cellPx)
                val tl = Offset(p.x * cellPx + cellPx * 0.08f, p.y * cellPx + cellPx * 0.08f)
                val sz = Size(cellPx * 0.84f, cellPx * 0.84f)
                drawRoundRect(
                    woodMark.copy(alpha = 0.18f + 0.16f * pulse),
                    topLeft = tl, size = sz,
                    cornerRadius = CornerRadius(cellPx * 0.16f, cellPx * 0.16f)
                )
                drawRoundRect(
                    woodMark.copy(alpha = 0.55f + 0.35f * pulse),
                    topLeft = tl, size = sz,
                    cornerRadius = CornerRadius(cellPx * 0.16f, cellPx * 0.16f),
                    style = Stroke(width = cellPx * 0.05f)
                )
                drawLine(woodMark.copy(alpha = 0.2f + 0.15f * pulse), origin, c,
                    strokeWidth = cellPx * 0.035f, cap = StrokeCap.Round)
            }
            // Teika: rayo + halo.
            fun halo(c: Offset) {
                drawLine(glowTint.copy(alpha = 0.18f + 0.22f * pulse), origin, c,
                    strokeWidth = cellPx * 0.06f, cap = StrokeCap.Round)
                val r = (victimRadiusPx * 1.15f + cellPx * 0.12f * pulse).coerceAtLeast(cellPx * 0.4f)
                drawCircle(glowTint.copy(alpha = 0.28f + 0.32f * pulse), r * 1.15f, c)
                drawCircle(glowTint.copy(alpha = 0.6f + 0.4f * pulse), r, c, style = Stroke(width = cellPx * 0.09f))
                drawCircle(Color.White.copy(alpha = 0.25f + 0.2f * pulse), r * 0.35f, c)
            }
            if (liveVictim != null && cellPx > 0f) halo(Offset(liveVictim.x * cellPx, liveVictim.y * cellPx))
            else for (i in cellHalo.indices) halo(cellHalo[i])
        }
        val astroSize = with(LocalDensity.current) { (cellPx * ASTRO_BOX_CELLS).toDp() }
        LottieAnimation(
            composition = composition,
            progress = { lottieProgress },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(astroSize)
                .offset { IntOffset(0, (-astroSize * (slide.value + idleBob)).roundToPx()) }
        )
    }
}

/** Cielo Estelar: el astro se RETIRA al cielo (counterplay exitoso). */
@Composable
fun AstroRetreatVFX(event: GameEvent.AstroRetreat, cellPx: Float, onComplete: () -> Unit) {
    val isSun = event.body == 1
    val anim = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(620, easing = FastOutSlowInEasing))
        onComplete()
    }
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(if (isSun) R.raw.astro_sol else R.raw.astro_luna)
    )
    val lottieProgress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    val t = anim.value
    val tint = if (isSun) Color(0xFFFF8A24) else Color(0xFF7FA8FF)
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(
                0f to tint.copy(alpha = 0.3f * (1f - t)),
                0.4f to Color.Transparent
            ))
            // Destellos que suben.
            for (i in 0 until 12) {
                val x = size.width * (0.2f + (i % 5) * 0.15f)
                val y = size.height * 0.15f - t * cellPx * (2f + i * 0.2f)
                drawCircle(Color.White.copy(alpha = 0.4f * (1f - t)), cellPx * 0.06f, Offset(x, y))
            }
        }
        val astroSize = with(LocalDensity.current) { (cellPx * ASTRO_BOX_CELLS).toDp() }
        LottieAnimation(
            composition = composition,
            progress = { lottieProgress },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(astroSize)
                // Arranca EXACTAMENTE donde el aviso dejó el astro (ASTRO_REST_OFFSET) y sube. Con el
                // 0,2 de antes daba un salto hacia arriba en el primer frame de la retirada.
                .offset(y = -astroSize * (ASTRO_REST_OFFSET + t * 0.9f))
                .graphicsLayer {
                    // Encoge desde el CENTRO (no desde el borde superior, como hacía `.size()`).
                    val s = 1f - t * 0.35f
                    scaleX = s; scaleY = s
                    alpha = 1f - t
                }
        )
    }
}

/**
 * Cielo Estelar: pips de amenaza del cielo (idle hacia el Astro / cooldown).
 *  · [idlePips] 0..threshold: relleno = turnos ociosos acumulados.
 *  · [emerged]: parpadeo de peligro máximo.
 *  · [cooldownLeft]: pips fríos que se apagan (respiro tras disparo/retirada).
 */

/**
 * Cielo Estelar / Evento Astral (DISPARO, one-shot, PREMIUM):
 * [event.lifts] describe cada pieza/Teika que se VE volar.
 *
 *  · Sol (1): haz cenital → combustión del Teika (encoge, brasas, ceniza sube al Sol).
 *  · Luna (2): flujo visual **continuo** — los bloques NO desaparecen del tablero y reaparecen:
 *    están ocultos en [GameState.hiddenWoodCells] y este VFX los dibuja desde el frame 0 en su
 *    celda (DESPEGUE) → levitación → viaje a la Luna → absorción. Look fiel al tablero (skin,
 *    cofre dorado, PNG de estrella-objetivo, cristal, veneno).
 */
@Composable
fun AstroStrikeVFX(event: GameEvent.AstroStrike, cellPx: Float, onComplete: () -> Unit) {
    val isSun = event.body == 1
    // P-67: brasas y chispas son adorno. El LÁSER, el halo de la víctima y el viaje del botín se
    // quedan enteros: son lo que dice a quién y qué te están quitando.
    val astroEmbers = fxCount(8)
    val astroBoomSparks = fxCount(18)
    val astroBurnSparks = fxCount(10)
    val duration = event.durationMillis.coerceIn(600, 3200)
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(event.id) {
        anim.snapTo(0f)
        // t LINEAL: las fases internas (carga/flotación/viaje/explosión) llevan su propio easing.
        // Imprescindible para que el delay en tiempo real del ViewModel (retiro del cuerpo del
        // Teika) caiga EXACTAMENTE en la explosión que se ve en pantalla.
        anim.animateTo(1f, tween(duration, easing = LinearEasing))
        onComplete()
    }
    // Payload visual: lifts enriquecidos, o fallback a celdas planas.
    val lifts = remember(event.id) {
        if (event.lifts.isNotEmpty()) event.lifts
        else event.cells.map {
            GameEvent.AstroStrike.AstroLift(cell = it, colorType = 0, isTeika = false, cx = it.x + 0.5f, cy = it.y + 0.5f)
        }
    }
    val skin = LocalBlockSkin.current
    // Bitmaps REALES de la estrella-objetivo (mismos PNG que CellView / BlockFlee).
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val starBitmaps = remember(ctx) { targetStarBitmaps(ctx) }
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(if (isSun) R.raw.astro_sol else R.raw.astro_luna)
    )
    val lottieProgress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    val t = anim.value
    // (`boardTop` y `zenith` retirados 2026-08-30: el ancla del astro es [astroAnchorY], y `zenith`
    //  nunca llegó a usarse.)

    Box(modifier = Modifier.fillMaxSize()) {
        // Astro arriba (receptor del botín) — late mientras se lleva las cosas y, al final, se RETIRA
        // hacia el cielo DRAMÁTICAMENTE (se eleva y se desvanece despacio).
        val astroSize = with(LocalDensity.current) { (cellPx * ASTRO_BOX_CELLS).toDp() }
        // El astro NO se esfuma: tras actuar SUBE despacio hacia el cielo — Sol tras la explosión
        // (~0.64), Luna tras absorber los bloques (~0.80) — y solo se funde en el último tramo,
        // cuando ya está casi fuera de vista.
        val exitStart = if (isSun) 0.64f else 0.80f
        val exit = ((t - exitStart) / (1f - exitStart)).coerceIn(0f, 1f)
        val exitEased = exit * exit * (3f - 2f * exit)   // smoothstep = ascenso suave y lento
        // El astro REACCIONA mientras actúa (no estático): el Sol CRECE cargando el láser; la Luna
        // PALPITA mientras absorbe. Se calma al retirarse.
        val actionThrob = 1f + (
            if (isSun) 0.16f * (t / 0.40f).coerceIn(0f, 1f)
            else 0.08f * (0.5f + 0.5f * sin(t * 18f))
        ) * (1f - exitEased)
        val bob = (1f - t * 0.10f) * (1f + 0.08f * exitEased) * actionThrob
        LottieAnimation(
            composition = composition,
            progress = { lottieProgress },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(astroSize)
                .offset(y = -astroSize * (ASTRO_REST_OFFSET + 1.9f * exitEased))  // sube bien alto (se va)
                .graphicsLayer {
                    // El latido escala desde el CENTRO. Con el `.size(astroSize * bob)` de antes el
                    // Sol crecía anclado por arriba (TopCenter), así que su centro BAJABA +0,29
                    // celdas justo mientras cargaba el láser y el rayo se le despegaba a la vista.
                    scaleX = bob; scaleY = bob
                    // Opaco casi todo el ascenso; funde solo el 35% final (ya bien arriba).
                    alpha = 1f - ((exitEased - 0.65f) / 0.35f).coerceIn(0f, 1f)
                        .let { f -> f * f }
                }
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // El apex ES el centro del disco del astro (ver [astroAnchorY]) — le pasa el MISMO
            // desplazamiento que a la Lottie, así que lo sigue exactamente en su ascenso: el láser,
            // el destino del viaje de los bloques y el vórtice de absorción nacen del astro, no del
            // borde superior del tablero.
            val apex = Offset(
                w / 2f,
                astroAnchorY(cellPx * ASTRO_BOX_CELLS, ASTRO_REST_OFFSET + 1.9f * exitEased)
            )
            val tint = if (isSun) Color(0xFFFF8A24) else Color(0xFF7FA8FF)
            val glow = if (isSun) Color(0xFFFFE082) else Color(0xFFB3E5FC)

            // Viñeta + neblina superior durante el robo.
            drawRect(
                Brush.verticalGradient(
                    0f to tint.copy(alpha = 0.35f * (1f - t * 0.4f)),
                    0.45f to Color.Transparent
                )
            )

            lifts.forEachIndexed { idx, lift ->
                // Stagger suave: cada bloque arranca un poco después, PERO siempre se dibuja en
                // su celda (local visual 0) hasta que le toca — nunca “desaparece” del tablero.
                val stagger = (idx * 0.05f).coerceAtMost(0.18f)
                val localRaw = ((t - stagger) / (1f - stagger)).coerceAtLeast(0f)
                val local = localRaw.coerceIn(0f, 1f)
                val origin = Offset(lift.cx * cellPx, lift.cy * cellPx)

                // ── SOL / muerte de Teika: NO se levanta. El láser desde el Sol se INTENSIFICA (más
                //    grueso/brillante/rápido) hasta que el Teika se pone al rojo blanco y EXPLOTA. ──
                if (isSun && lift.isTeika) {
                    if (localRaw <= 0f) return@forEachIndexed
                    // El láser sale del MISMO punto que todo lo demás: el centro del Sol. (Antes
                    // tenía su propio `apexS` clavado a 0,15 celdas del borde — 1,2 celdas por
                    // encima del disco, que es exactamente lo que se veía mal.)
                    val apexS = apex
                    val r = cellPx * lift.teikaLevel.coerceIn(1, 5) * 0.48f
                    // t es LINEAL: láser 0→0.42, explosión 0.42→0.62 y salida del Sol 0.64→1. El
                    // ViewModel retira el cuerpo físico en 0.42·dur (inicio del boom): el Teika real
                    // desaparece TAPADO por el flash blanco, no segundos después.
                    val beamT = (local / 0.42f).coerceIn(0f, 1f)          // carga del rayo 0→1
                    val boom = ((local - 0.42f) / 0.20f).coerceIn(0f, 1f) // explosión 0→1 (rápida)
                    if (boom < 1f) {
                        // Rayo que se intensifica (grosor ∝ carga, parpadeo cada vez más rápido).
                        val flick = 0.55f + 0.45f * sin(local * (12f + beamT * 70f))
                        val bw = cellPx * (0.05f + 0.20f * beamT) * flick
                        drawLine(tint.copy(alpha = (0.30f + 0.55f * beamT) * (1f - boom)), apexS, origin,
                            strokeWidth = bw * 1.6f, cap = StrokeCap.Round)
                        drawLine(glow.copy(alpha = (0.40f + 0.55f * beamT) * (1f - boom)), apexS, origin,
                            strokeWidth = bw, cap = StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = (0.45f + 0.5f * beamT) * (1f - boom)), apexS, origin,
                            strokeWidth = bw * 0.4f, cap = StrokeCap.Round)
                        // Teika calentándose: tiembla y pasa de su color a blanco incandescente.
                        val tremble = sin(local * 46f) * cellPx * 0.035f * beamT
                        val hp = Offset(origin.x + tremble, origin.y)
                        drawCircle(Color(0xFFFF3D00).copy(alpha = 0.35f * beamT * (1f - boom)), r * (1.25f + 0.35f * beamT), hp)
                        drawCircle(
                            androidx.compose.ui.graphics.lerp(Color(0xFFFFB74D), Color(0xFFFFFDE7), beamT)
                                .copy(alpha = (1f - boom)),
                            r * (1f + 0.08f * beamT), hp
                        )
                        // Brasas convergiendo hacia el Teika (el rayo lo "alimenta").
                        for (i in 0 until astroEmbers) {
                            val ang = Math.toRadians((i * 45 + local * 200).toDouble())
                            val d = r * (2.2f - 1.6f * beamT)
                            drawCircle(Color(0xFFFF7043).copy(alpha = 0.5f * beamT * (1f - boom)),
                                cellPx * 0.05f, Offset(hp.x + (cos(ang) * d).toFloat(), hp.y + (sin(ang) * d).toFloat()))
                        }
                    }
                    if (boom > 0f) {
                        val e = boom
                        drawCircle(Color.White.copy(alpha = 0.75f * (1f - e)), r * (1f + e * 3.2f), origin)
                        drawCircle(Color(0xFFFF8A24).copy(alpha = 0.6f * (1f - e)), r * (0.5f + e * 4.5f), origin,
                            style = Stroke(width = cellPx * 0.12f * (1f - e)))
                        drawCircle(Color(0xFFFFE082).copy(alpha = 0.5f * (1f - e)), r * (0.3f + e * 3.4f), origin,
                            style = Stroke(width = cellPx * 0.07f * (1f - e)))
                        for (i in 0 until astroBoomSparks) {
                            val ang = Math.toRadians((i * 20 + lift.cell.x * 13).toDouble())
                            val d = r * (0.5f + e * 4.8f)
                            drawCircle(
                                androidx.compose.ui.graphics.lerp(Color(0xFFFFF3E0), Color(0xFFFF3D00), e)
                                    .copy(alpha = (1f - e)),
                                cellPx * (0.08f * (1f - e) + 0.02f),
                                Offset(origin.x + (cos(ang) * d).toFloat(), origin.y + (sin(ang) * d).toFloat() - e * cellPx * 0.5f)
                            )
                        }
                    }
                    return@forEachIndexed
                }

                // Timing por astro.
                // LUNA madera: DESPEGUE del tablero (0–0.16) → FLOTAR (0.16–0.38) → VIAJE (0.38–0.78)
                // → ABSORCIÓN (0.72–0.88). Siempre visible desde el frame 0 en su celda.
                // SOL madera (si aplica): subida rápida.
                val charge: Float
                val flyEased: Float
                val pos: Offset
                val scale: Float
                val alpha: Float
                val blockRot: Float
                val peeling: Boolean  // true mientras aún se despega del suelo (sombra / sin haz fuerte)
                if (isSun) {
                    if (localRaw <= 0f) return@forEachIndexed
                    peeling = false
                    charge = (local / 0.18f).coerceIn(0f, 1f)
                    val fly = ((local - 0.18f) / 0.82f).coerceIn(0f, 1f)
                    flyEased = fly * fly
                    pos = if (local < 0.18f) origin + Offset(0f, -cellPx * 0.12f * charge)
                    else {
                        val mx = origin.x + (apex.x - origin.x) * flyEased
                        val my = origin.y + (apex.y - origin.y - cellPx * 0.4f) * flyEased -
                            cellPx * 1.2f * flyEased * flyEased
                        Offset(mx, my)
                    }
                    scale = (1f - local * 0.85f).coerceAtLeast(0.12f)
                    alpha = (1f - local * 0.9f).coerceAtLeast(0f)
                    blockRot = 0f
                } else {
                    // local efectivo: 0 mientras espera su stagger (bloque “anclado” al tablero).
                    val L = local
                    // ── DESPEGUE: se arranca del suelo (squash → pop + sombra) ──
                    val peelT = (L / 0.16f).coerceIn(0f, 1f)
                    val peelE = peelT * peelT * (3f - 2f * peelT) // smoothstep
                    peeling = L < 0.16f
                    // ── FLOTAR: antigravedad con vaivén ──
                    val floatT = ((L - 0.16f) / 0.22f).coerceIn(0f, 1f)
                    val floatE = 1f - (1f - floatT) * (1f - floatT)
                    // ── VIAJE a la luna ──
                    val travel = ((L - 0.38f) / 0.40f).coerceIn(0f, 1f)
                    val te = travel * travel * (3f - 2f * travel)
                    // ── ABSORCIÓN ──
                    val absorb = ((L - 0.72f) / 0.16f).coerceIn(0f, 1f)
                    charge = when {
                        peeling -> peelE * 0.55f
                        else -> 0.55f + 0.45f * floatE * (1f - te)
                    }
                    flyEased = te
                    val hover = sin(L * 7f + lift.cell.x * 1.3f)
                    // Despegue: sube desde el centro de la casilla; luego flota más alto.
                    val peelDoneY = -cellPx * 0.38f
                    val peelLift = peelDoneY * peelE
                    val floatExtra = -cellPx * (0.55f * floatE + 0.10f * hover * floatE * (1f - te))
                    val liftY = if (L < 0.16f) peelLift else peelDoneY + floatExtra
                    val swayX = if (L < 0.16f) 0f
                    else cos(L * 5f + lift.cell.y * 1.7f) * cellPx * 0.10f * floatE * (1f - te)
                    val startX = origin.x + swayX
                    val startY = origin.y + liftY
                    if (L < 0.38f || te <= 0f) {
                        pos = Offset(startX, startY)
                    } else {
                        val mxp = (startX + apex.x) / 2f; val myp = (startY + apex.y) / 2f
                        val ctrlX = mxp + (apex.x - mxp) * 0.6f
                        val ctrlY = myp + (apex.y - myp) * 0.6f
                        val mt = 1f - te
                        pos = Offset(
                            mt * mt * startX + 2f * mt * te * ctrlX + te * te * apex.x,
                            mt * mt * startY + 2f * mt * te * ctrlY + te * te * apex.y
                        )
                    }
                    // Scale en despegue: 1 → 1.08 (pop) → 1; luego encoge al viajar/absorber.
                    val peelScale = if (peeling) {
                        1f + 0.10f * sin(peelE * PI.toFloat()) // pop al despegar
                    } else 1f
                    scale = peelScale * (1f - 0.45f * te).coerceAtLeast(0.2f) * (1f - absorb)
                    alpha = (1f - absorb).coerceAtLeast(0f)
                    blockRot = if (peeling) peelE * -8f
                    else hover * 6f * floatE * (1f - te) + te * 80f
                    // Sombra en el suelo de la celda mientras se despega (lectura “estaba aquí”).
                    if (L < 0.42f && alpha > 0.05f) {
                        val shadowFade = (1f - (L / 0.42f)).coerceIn(0f, 1f) * alpha
                        val shR = cellPx * (0.38f - 0.12f * peelE)
                        drawOval(
                            Color(0xFF0A0E18).copy(alpha = 0.28f * shadowFade),
                            topLeft = Offset(origin.x - shR, origin.y + cellPx * 0.28f - shR * 0.35f),
                            size = Size(shR * 2f, shR * 0.7f)
                        )
                    }
                }

                // Haz del astro: en la Luna se enciende al despegar (no mientras el bloque “aún es del tablero”).
                if (alpha > 0.05f && (!peeling || charge > 0.2f)) {
                    val beamCore = if (isSun) 0.22f else 0.42f
                    val beamA = if (isSun) 1f else charge.coerceIn(0f, 1f)
                    drawLine(
                        glow.copy(alpha = beamCore * alpha * beamA * (0.45f + 0.55f * charge)),
                        apex, pos,
                        strokeWidth = cellPx * (0.05f + 0.06f * (1f - flyEased)),
                        cap = StrokeCap.Round
                    )
                    if (!isSun) {
                        drawLine(
                            Color.White.copy(alpha = 0.5f * alpha * charge * beamA),
                            apex, pos,
                            strokeWidth = cellPx * (0.02f + 0.02f * (1f - flyEased)),
                            cap = StrokeCap.Round
                        )
                    }
                }

                if (lift.isTeika) {
                    // Teika: burbuja por nivel + cara (lectura “se lo llevan”, no un rect fantasma).
                    val r = cellPx * lift.teikaLevel.coerceIn(1, 5) * 0.48f * scale
                    if (isSun) {
                        // Combustión: núcleo blanco → naranja, brasas radiales, humo.
                        drawCircle(Color(0xFFFFF3E0).copy(alpha = 0.9f * alpha), r * 0.55f, pos)
                        drawCircle(Color(0xFFFF8A24).copy(alpha = 0.85f * alpha), r, pos)
                        drawCircle(Color(0xFFFF3D00).copy(alpha = 0.55f * alpha), r * 1.15f, pos, style = Stroke(width = cellPx * 0.06f))
                        for (i in 0 until astroBurnSparks) {
                            val ang = Math.toRadians((i * 36 + lift.cell.x * 17 + local * 120).toDouble())
                            val dist = r * (0.9f + local * 1.8f)
                            val spark = Offset(
                                pos.x + (cos(ang) * dist).toFloat(),
                                pos.y + (sin(ang) * dist).toFloat() - local * cellPx * 0.8f
                            )
                            drawCircle(
                                Color(0xFFFF7043).copy(alpha = alpha * (1f - local)),
                                cellPx * (0.06f + 0.05f * (1f - local)),
                                spark
                            )
                        }
                        // Ceniza que sube al Sol.
                        for (i in 0 until 5) {
                            val ay = pos.y - local * cellPx * (1.2f + i * 0.35f)
                            val ax = pos.x + sin((local * 8 + i).toDouble()).toFloat() * cellPx * 0.2f
                            drawCircle(Color(0xFFBDBDBD).copy(alpha = 0.35f * alpha), cellPx * 0.05f, Offset(ax, ay))
                        }
                    } else {
                        // Luna: Teika plateado levitando con anillo y estela.
                        drawCircle(Color(0xFF81D4FA).copy(alpha = 0.35f * alpha), r * 1.35f, pos)
                        drawCircle(
                            Brush.radialGradient(
                                listOf(Color(0xFFE3F2FD).copy(alpha = alpha), Color(0xFF42A5F5).copy(alpha = alpha * 0.85f)),
                                center = pos, radius = r
                            ),
                            r, pos
                        )
                        drawCircle(Color.White.copy(alpha = 0.75f * alpha), r, pos, style = Stroke(width = cellPx * 0.05f))
                        // Estela de destellos bajo el Teika.
                        for (i in 1..5) {
                            val trailT = flyEased - i * 0.06f
                            if (trailT <= 0f) continue
                            val ty = origin.y + (pos.y - origin.y) * trailT.coerceIn(0f, 1f)
                            val tx = origin.x + (pos.x - origin.x) * trailT.coerceIn(0f, 1f)
                            drawCircle(
                                Color(0xFFB3E5FC).copy(alpha = alpha * (0.35f / i)),
                                r * (0.35f / i),
                                Offset(tx, ty)
                            )
                        }
                    }
                } else {
                    // Bloque FIEL al tablero: mismo tile (bevel/block), cofre dorado + PNG de
                    // estrella-objetivo (TargetColor), cristal y veneno — idéntico a CellView /
                    // BlockFleeVFX. La animación de levitación no cambia.
                    val isWildcard = lift.colorType == WILDCARD_COLOR
                    val isCrystal = lift.armor > 0
                    val target = lift.targetColor
                    val isGoldChest = target != null && !isCrystal && !isWildcard
                    val palette = try {
                        woodPalette(skin, lift.colorType, isGoldChest)
                    } catch (_: Throwable) {
                        pieceColors[lift.colorType.coerceIn(0, pieceColors.lastIndex)]
                    }
                    val s = cellPx * scale
                    val tl = Offset(pos.x - s / 2f, pos.y - s / 2f)
                    val full = Size(s, s)
                    val base = palette[0]
                    if (!isSun) {
                        // Estela del color real del bloque (cofre dorado o skin).
                        for (i in 1..4) {
                            val trailT = (flyEased - i * 0.07f).coerceAtLeast(0f)
                            if (trailT <= 0f && flyEased > 0f) continue
                            val ty = origin.y + (pos.y - origin.y) * trailT
                            val tx = origin.x + (pos.x - origin.x) * trailT
                            val ts = s * (1f - i * 0.12f)
                            drawRoundRect(
                                base.copy(alpha = alpha * (0.28f / i)),
                                topLeft = Offset(tx - ts / 2f, ty - ts / 2f),
                                size = Size(ts, ts),
                                cornerRadius = CornerRadius(ts * 0.22f, ts * 0.22f)
                            )
                        }
                        drawCircle(glow.copy(alpha = 0.22f * alpha), s * 0.72f, pos)
                    }
                    // El bloque GIRA al viajar hacia la luna (blockRot; 0 en el Sol).
                    rotate(degrees = blockRot, pivot = pos) {
                        if (alpha > 0.02f) {
                            drawStyledTile(
                                skin.style, tl, full, palette[0], palette[1], palette[2],
                                skin.cornerFraction, alpha
                            )
                            if (lift.poisoned) drawPoisonLayer(tl, full)
                            // Resplandor del cofre + estrella PNG del color de objetivo.
                            target?.let { tc ->
                                val bmp = starBitmaps[tc]
                                if (isGoldChest) {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            listOf(
                                                Color(0xD9FFF1B8).copy(alpha = 0.85f * alpha),
                                                Color(0x33FFDF8E).copy(alpha = 0.4f * alpha),
                                                Color(0x00FFDF8E)
                                            ),
                                            center = pos, radius = s * 0.6f
                                        ),
                                        radius = s * 0.6f, center = pos
                                    )
                                }
                                if (bmp != null) {
                                    val starW = s * skin.starScale
                                    val starH = starW * bmp.height.toFloat() / bmp.width.toFloat()
                                    val starCy = pos.y + s * skin.starOffsetYFraction
                                    drawImage(
                                        image = bmp,
                                        srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                        srcSize = androidx.compose.ui.unit.IntSize(bmp.width, bmp.height),
                                        dstOffset = androidx.compose.ui.unit.IntOffset(
                                            (pos.x - starW / 2f).roundToInt(),
                                            (starCy - starH / 2f).roundToInt()
                                        ),
                                        dstSize = androidx.compose.ui.unit.IntSize(
                                            starW.roundToInt().coerceAtLeast(1),
                                            starH.roundToInt().coerceAtLeast(1)
                                        ),
                                        alpha = alpha
                                    )
                                }
                            }
                            // Cristal: borde cian legible en vuelo (como el vuelo del Miedo).
                            if (isCrystal) {
                                drawRoundRect(
                                    Color(0xFF80DEEA).copy(alpha = 0.45f * alpha),
                                    topLeft = tl, size = full,
                                    cornerRadius = CornerRadius(s * 0.2f, s * 0.2f),
                                    style = Stroke(width = s * 0.08f)
                                )
                                drawRoundRect(
                                    Color(0xFFB2EBF2).copy(alpha = 0.18f * alpha),
                                    topLeft = tl, size = full,
                                    cornerRadius = CornerRadius(s * 0.2f, s * 0.2f)
                                )
                            }
                        }
                    }
                    if (isSun) {
                        drawCircle(Color(0xFFFF7043).copy(alpha = 0.5f * alpha), s * 0.2f, pos)
                    }
                }
            }

            // LUNA: VÓRTICE DE ABSORCIÓN — disco de partículas girando y convergiendo a la luna (como el
            // disco de acreción del agujero negro), para que se lea claramente que "traga" los bloques.
            if (!isSun) {
                val vortex = 1f - ((t - 0.80f) / 0.20f).coerceIn(0f, 1f)   // se apaga cuando la luna sube
                if (vortex > 0.02f) {
                    for (k in 0 until 26) {
                        val a = Math.toRadians(k * 13.85 + t * 320.0)
                        val ph = (k * 0.038f + t * 1.9f) % 1f
                        val rr = cellPx * (0.45f + 1.7f * (1f - ph))       // converge hacia dentro
                        drawCircle(
                            Color(0xFFB3E5FC).copy(alpha = 0.55f * ph * vortex),
                            cellPx * 0.055f,
                            Offset(apex.x + (cos(a) * rr).toFloat(), apex.y + (sin(a) * rr).toFloat())
                        )
                    }
                    // Aro de succión pulsante en la boca de la luna.
                    val ring = 1f + 0.08f * sin(t * 16.0).toFloat()
                    drawCircle(Color(0xFFE1F5FE).copy(alpha = 0.5f * vortex), cellPx * 0.9f * ring, apex,
                        style = Stroke(width = cellPx * 0.06f))
                }
            }

            // Flash final al absorber en el zenit.
            if (t > 0.82f) {
                val flash = ((t - 0.82f) / 0.18f).coerceIn(0f, 1f)
                val a = if (flash < 0.5f) flash * 2f else (1f - flash) * 2f
                drawCircle(glow.copy(alpha = 0.55f * a), cellPx * (1.2f + flash * 1.8f), apex)
                drawCircle(Color.White.copy(alpha = 0.4f * a), cellPx * (0.5f + flash), apex)
            }
        }
    }
}

// (PODADO 2026-08-27) `GluttonRunVFX`: la criatura que recorría el path del zigzag masticando.
// Se fue con «Devorar» → EL BANQUETE.

/**
 * Chueta / **EL BANQUETE** ([GameEvent.GluttonFeast]) — la ceremonia lenta de la habilidad.
 *
 * Tres tiempos, y se leen en este orden a propósito: **anticipación** (las fauces asoman por el
 * borde inferior y se abren), **acción** (los bloques con estrella se despegan uno a uno y caen en
 * arco a la boca, que muerde con cada bocado) y **resolución** (se relame y se hunde). El jugador
 * tiene que poder mirarlo: un barrido instantáneo se lee como un parpadeo y no se disfruta.
 *
 * Paleta = la del Lottie `teika_gloton`, la misma que usa el bioma, para que sea EL personaje y no
 * una boca genérica.
 */
@Composable
fun GluttonFeastVFX(
    event: GameEvent.GluttonFeast,
    cellPx: Float,
    onComplete: () -> Unit
) {
    val cells = event.cells
    if (cells.isEmpty()) { LaunchedEffect(event.id) { onComplete() }; return }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }

    // Centros de celda precalculados: el bucle de dibujo no debe asignar nada por frame.
    val origins = remember(event.id, cellPx) {
        FloatArray(cells.size * 2).also { arr ->
            cells.forEachIndexed { i, p ->
                arr[i * 2] = (p.x + 0.5f) * cellPx
                arr[i * 2 + 1] = (p.y + 0.5f) * cellPx
            }
        }
    }

    val total = event.durationMillis.toFloat()
    val openMs = event.openMs.toFloat()
    val stepMs = event.stepMs.toFloat()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val nowMs = progress.value * total
        // La boca vive en el borde INFERIOR, centrada. Asoma hacia dentro del tablero lo justo para
        // que se vea entera sin tapar la zona de juego (el tablero recorta lo que baja de aquí).
        val mouthR = cellPx * 1.55f
        val rise = ((nowMs / openMs).coerceIn(0f, 1f)).let { it * it * (3f - 2f * it) }  // smoothstep
        val sink = ((nowMs - (total - 420f)) / 420f).coerceIn(0f, 1f)
        val peek = mouthR * (0.62f * rise - 0.62f * sink)
        val mouthC = Offset(size.width * 0.5f, size.height + mouthR - peek)

        // ── Bocados: cuántos han entrado ya y con qué fuerza mastica ahora mismo ──
        var chompPulse = 0f
        for (i in cells.indices) {
            val startAt = openMs + i * stepMs
            val landAt = startAt + GLUTTON_FEAST_FALL
            val since = nowMs - landAt
            if (since in 0f..220f) {
                val k = 1f - (since / 220f)
                if (k > chompPulse) chompPulse = k
            }
        }

        // ── Fauces ──────────────────────────────────────────────────────────────
        val open = rise * (1f - sink)
        // Mastica al tragar: la apertura se cierra de golpe y vuelve a abrirse.
        val jawDeg = (14f + 44f * open) * (1f - 0.75f * chompPulse)
        val bodyAlpha = (rise * (1f - sink)).coerceIn(0f, 1f)

        if (bodyAlpha > 0.01f) {
            // Cuerpo redondo con el gradiente del personaje.
            drawCircle(
                brush = Brush.radialGradient(
                    0f to GLUTTON_BODY_HI, 0.45f to GLUTTON_BODY_MID, 1f to GLUTTON_BODY_LO,
                    center = Offset(mouthC.x - mouthR * 0.25f, mouthC.y - mouthR * 0.3f),
                    radius = mouthR * 1.5f
                ),
                radius = mouthR,
                center = mouthC,
                alpha = bodyAlpha
            )
            drawCircle(
                GLUTTON_OUTLINE.copy(alpha = bodyAlpha),
                mouthR, mouthC, style = Stroke(width = mouthR * 0.05f)
            )
            // Boca abierta hacia ARRIBA (cuña): el hueco negro por el que entran los bloques.
            val maw = Path().apply {
                moveTo(mouthC.x, mouthC.y)
                val a0 = Math.toRadians((-90.0 - jawDeg))
                lineTo(
                    mouthC.x + (cos(a0) * mouthR * 1.12f).toFloat(),
                    mouthC.y + (sin(a0) * mouthR * 1.12f).toFloat()
                )
                arcTo(
                    androidx.compose.ui.geometry.Rect(
                        mouthC.x - mouthR * 1.12f, mouthC.y - mouthR * 1.12f,
                        mouthC.x + mouthR * 1.12f, mouthC.y + mouthR * 1.12f
                    ),
                    -90f - jawDeg, jawDeg * 2f, false
                )
                close()
            }
            drawPath(maw, GLUTTON_EYE.copy(alpha = bodyAlpha))
            // Lengua asomando en el fondo de las fauces.
            drawCircle(
                GLUTTON_TONGUE.copy(alpha = bodyAlpha * 0.9f),
                mouthR * 0.3f,
                Offset(mouthC.x, mouthC.y - mouthR * 0.18f - chompPulse * mouthR * 0.12f)
            )
            // Colmillos: dos por lado, en el borde de la cuña.
            for (s in -1..1 step 2) {
                for (k in 0..1) {
                    val ang = Math.toRadians((-90.0 + s * (jawDeg - 6.0 - k * 11.0)))
                    val tip = Offset(
                        mouthC.x + (cos(ang) * mouthR * 0.86f).toFloat(),
                        mouthC.y + (sin(ang) * mouthR * 0.86f).toFloat()
                    )
                    drawCircle(GLUTTON_TOOTH.copy(alpha = bodyAlpha), mouthR * 0.07f, tip)
                }
            }
            // Ojos por encima de las fauces, mirando al festín.
            for (s in -1..1 step 2) {
                val e = Offset(mouthC.x + s * mouthR * 0.52f, mouthC.y - mouthR * 0.62f)
                drawCircle(GLUTTON_EYE.copy(alpha = bodyAlpha), mouthR * 0.13f, e)
                drawCircle(Color.White.copy(alpha = bodyAlpha * 0.9f), mouthR * 0.05f,
                    Offset(e.x + mouthR * 0.04f, e.y - mouthR * 0.05f))
            }
            // Cachetes.
            for (s in -1..1 step 2) {
                drawCircle(
                    GLUTTON_ORANGE.copy(alpha = bodyAlpha * 0.55f), mouthR * 0.16f,
                    Offset(mouthC.x + s * mouthR * 0.78f, mouthC.y - mouthR * 0.28f)
                )
            }
        }

        // ── Los bloques cayendo a la boca ───────────────────────────────────────
        for (i in cells.indices) {
            val startAt = openMs + i * stepMs
            val t = ((nowMs - startAt) / GLUTTON_FEAST_FALL).coerceIn(0f, 1f)
            if (nowMs < startAt || t >= 1f) continue
            val ox = origins[i * 2]
            val oy = origins[i * 2 + 1]
            // Caída con aceleración (ease-in): se despega despacio y se precipita al final.
            val e = t * t
            // Arco: se desvía hacia la boca en horizontal antes de caer del todo.
            val x = ox + (mouthC.x - ox) * (t * t * (3f - 2f * t))
            val y = oy + (mouthC.y - mouthR * 0.35f - oy) * e
            // Encoge al acercarse (perspectiva de "entrar en el agujero") y gira.
            val scale = 1f - 0.55f * t
            val half = cellPx * 0.36f * scale
            rotate(degrees = t * 190f * (if (i % 2 == 0) 1f else -1f), pivot = Offset(x, y)) {
                drawRoundRect(
                    color = STAR_FALL_TINT,
                    topLeft = Offset(x - half, y - half),
                    size = androidx.compose.ui.geometry.Size(half * 2f, half * 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(half * 0.45f),
                    alpha = 1f - 0.25f * t
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.55f * (1f - t)),
                    topLeft = Offset(x - half, y - half),
                    size = androidx.compose.ui.geometry.Size(half * 2f, half * 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(half * 0.45f),
                    style = Stroke(width = half * 0.16f)
                )
            }
            // Estela de brillo detrás del bloque: lo hace legible sobre un tablero cargado.
            drawCircle(
                STAR_FALL_TINT.copy(alpha = 0.22f * (1f - t)),
                half * 1.5f,
                Offset(x, y - half * 1.1f)
            )
        }
    }
}

/**
 * Morti / **POSESIÓN** ([GameEvent.GhostPossession]) — el viaje espectral.
 *
 * Tres tiempos por bloque, escalonados para que salgan en fila y no en bandada: **se despega**
 * (tiembla en su sitio y pierde el color mientras Morti lo toma), **atraviesa** el tablero
 * translúcido con estela y cara fantasma, y **se solidifica** con un chasquido dentro del hueco.
 *
 * Lo que hace que la habilidad se entienda es el destino: el bloque no se desvanece, **encaja**.
 * Por eso el aterrizaje dibuja el marco de la celda antes de que el bloque llegue — el jugador ve a
 * dónde va desde el primer frame y entiende que Morti está cerrando una línea.
 */
@Composable
fun GhostPossessionVFX(
    event: GameEvent.GhostPossession,
    cellPx: Float,
    onComplete: () -> Unit
) {
    val steps = event.steps
    if (steps.isEmpty()) { LaunchedEffect(event.id) { onComplete() }; return }

    val progress = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }

    val total = event.durationMillis.toFloat()
    val liftMs = event.liftMs.toFloat()
    val flightMs = event.flightMs.toFloat()
    val stepMs = event.stepMs.toFloat()

    Canvas(modifier = Modifier.fillMaxSize()) {
        val nowMs = progress.value * total
        val half = cellPx * 0.42f

        for (i in steps.indices) {
            val s = steps[i]
            val liftAt = i * stepMs
            val flyAt = liftAt + liftMs
            val landAt = flyAt + flightMs

            val fromX = (s.from.x + 0.5f) * cellPx
            val fromY = (s.from.y + 0.5f) * cellPx
            val toX = (s.to.x + 0.5f) * cellPx
            val toY = (s.to.y + 0.5f) * cellPx

            // ── El hueco de destino se marca DESDE EL PRINCIPIO: es la promesa de la habilidad ──
            if (nowMs >= liftAt) {
                val settle = ((nowMs - landAt) / 180f).coerceIn(0f, 1f)
                val pulse = 0.35f + 0.35f * kotlin.math.sin(nowMs / 90f + i).let { it * it }
                drawRoundRect(
                    color = GHOST_SPECTRAL.copy(alpha = (1f - settle) * pulse),
                    topLeft = Offset(toX - half, toY - half),
                    size = androidx.compose.ui.geometry.Size(half * 2f, half * 2f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(half * 0.4f),
                    style = Stroke(width = cellPx * 0.05f)
                )
            }

            when {
                // ── 1. Despegue: tiembla en su sitio mientras Morti lo posee ──
                nowMs in liftAt..flyAt -> {
                    val t = ((nowMs - liftAt) / liftMs).coerceIn(0f, 1f)
                    val jitter = (1f - t) * cellPx * 0.06f
                    val jx = fromX + kotlin.math.sin(nowMs / 28f) * jitter
                    val jy = fromY + kotlin.math.cos(nowMs / 23f) * jitter - t * cellPx * 0.18f
                    drawGhostBlock(jx, jy, half * (1f + 0.08f * t), alpha = 0.35f + 0.5f * (1f - t))
                }
                // ── 2. Travesía: atraviesa el tablero con estela ──
                nowMs in flyAt..landAt -> {
                    val t = ((nowMs - flyAt) / flightMs).coerceIn(0f, 1f)
                    // Ease-in-out: sale despacio, cruza y frena en el hueco.
                    val e = t * t * (3f - 2f * t)
                    val x = fromX + (toX - fromX) * e
                    val y = fromY - cellPx * 0.18f + (toY - (fromY - cellPx * 0.18f)) * e
                    // Estela: fantasmas rezagados en el camino ya recorrido.
                    for (k in 1..4) {
                        val te = (e - k * 0.055f).coerceAtLeast(0f)
                        drawGhostBlock(
                            fromX + (toX - fromX) * te,
                            fromY - cellPx * 0.18f + (toY - (fromY - cellPx * 0.18f)) * te,
                            half * (1f - k * 0.09f),
                            alpha = 0.13f * (1f - k / 5f)
                        )
                    }
                    drawGhostBlock(x, y, half, alpha = 0.62f)
                    // Ojitos fantasma: es Morti quien lo lleva, no una física cualquiera.
                    for (sgn in -1..1 step 2) {
                        drawCircle(
                            Color.White.copy(alpha = 0.75f), half * 0.11f,
                            Offset(x + sgn * half * 0.3f, y - half * 0.18f)
                        )
                    }
                }
                // ── 3. Solidificación: chasquido y onda al encajar ──
                nowMs in landAt..(landAt + 260f) -> {
                    val t = ((nowMs - landAt) / 260f).coerceIn(0f, 1f)
                    // Sobre-escala que colapsa: el bloque "cuaja" en su sitio.
                    val pop = 1f + 0.28f * (1f - t) * kotlin.math.sin(t * Math.PI.toFloat())
                    drawGhostBlock(toX, toY, half * pop, alpha = 0.6f * (1f - t))
                    drawCircle(
                        GHOST_SPECTRAL.copy(alpha = 0.5f * (1f - t)),
                        half * (1.1f + t * 1.5f),
                        Offset(toX, toY),
                        style = Stroke(width = cellPx * 0.05f * (1f - t))
                    )
                }
            }
        }
    }
}

/** Bloque espectral: relleno translúcido + contorno, la firma visual de la posesión. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawGhostBlock(
    cx: Float, cy: Float, half: Float, alpha: Float
) {
    val topLeft = Offset(cx - half, cy - half)
    val size = androidx.compose.ui.geometry.Size(half * 2f, half * 2f)
    val corner = androidx.compose.ui.geometry.CornerRadius(half * 0.4f)
    drawRoundRect(GHOST_SPECTRAL.copy(alpha = alpha * 0.55f), topLeft, size, corner)
    drawRoundRect(
        Color.White.copy(alpha = alpha * 0.8f), topLeft, size, corner,
        style = Stroke(width = half * 0.14f)
    )
}

/** Verde-azulado fantasma de Morti: el mismo tinte de su bioma, para que se lea como suyo. */
private val GHOST_SPECTRAL = Color(0xFF9BE7FF)

/**
 * Maite / **SIESTA** ([GameEvent.DrowsySpell]) — la ceremonia.
 *
 * La mecánica (desplome + filas-bonus) ya funcionaba; lo que no existía era **la siesta**. Sin esto
 * el tablero se caía solo, con un destello genérico encima, y Maite no pintaba nada en su propia
 * habilidad. Ahora:
 *
 *  1. **Bostezo** — una ola de sueño índigo baja por el tablero. Detrás de ella la madera queda
 *     "dormida": se apaga y le salen zetas. Es la anticipación que explica el desplome.
 *  2. (el desplome lo animan los `WoodFall` de siempre, ya retrasados por el ViewModel)
 *  3. **Nube de sueño** — arriba se forma una nube blanda de la que nacen las filas-bonus, sobre
 *     las columnas donde de verdad van a caer.
 */
@Composable
fun DrowsySpellVFX(
    event: GameEvent.DrowsySpell,
    cellPx: Float,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val nowMs = progress.value * event.durationMillis
        val yawn = event.yawnMs.toFloat()
        val boardH = size.height

        // ── 1. La ola de sueño baja por el tablero ─────────────────────────────
        if (nowMs < yawn + 260f) {
            val t = (nowMs / yawn).coerceIn(0f, 1f)
            val frontY = boardH * t
            val fade = (1f - ((nowMs - yawn) / 260f)).coerceIn(0f, 1f)
            // Velo índigo por encima del frente: lo que ya está dormido.
            drawRect(
                brush = Brush.verticalGradient(
                    0f to DROWSY_VEIL.copy(alpha = 0.30f * fade),
                    1f to DROWSY_VEIL.copy(alpha = 0.05f * fade),
                    startY = 0f, endY = frontY.coerceAtLeast(1f)
                ),
                size = androidx.compose.ui.geometry.Size(size.width, frontY.coerceAtLeast(1f))
            )
            // El frente de la ola: una banda brillante que va pasando.
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, DROWSY_GLOW.copy(alpha = 0.5f * fade), Color.Transparent)
                ),
                topLeft = Offset(0f, frontY - cellPx * 0.6f),
                size = androidx.compose.ui.geometry.Size(size.width, cellPx * 1.2f)
            )
            // Zetas subiendo desde las celdas que ya están dormidas.
            for (i in event.collapseCells.indices) {
                val cell = event.collapseCells[i]
                val cy = (cell.y + 0.5f) * cellPx
                if (cy > frontY) continue
                val since = ((frontY - cy) / boardH).coerceIn(0f, 1f)
                val zx = (cell.x + 0.5f) * cellPx + kotlin.math.sin(since * 7f + i) * cellPx * 0.22f
                val zy = cy - since * cellPx * 1.6f
                drawZ(zx, zy, cellPx * 0.17f * (1f - since * 0.4f), DROWSY_GLOW.copy(alpha = 0.75f * (1f - since) * fade))
            }
        }

        // ── 3. Nube de sueño de la que nacen las filas-bonus ───────────────────
        if (event.cloudColumns.isNotEmpty() && nowMs >= event.cloudAtMs - 220f) {
            val t = ((nowMs - (event.cloudAtMs - 220f)) / (event.cloudMs + 220f)).coerceIn(0f, 1f)
            // Entra hinchándose y se disuelve al final: nunca tapa el aterrizaje de los bloques.
            val a = kotlin.math.sin(t * Math.PI.toFloat()).coerceIn(0f, 1f)
            val minX = (event.cloudColumns.min() + 0.5f) * cellPx
            val maxX = (event.cloudColumns.max() + 0.5f) * cellPx
            val cy = cellPx * 0.35f
            // Nube = varios círculos blandos solapados a lo largo de las columnas implicadas.
            var x = minX - cellPx * 0.5f
            var k = 0
            while (x <= maxX + cellPx * 0.5f) {
                val bob = kotlin.math.sin(nowMs / 260f + k) * cellPx * 0.06f
                val r = cellPx * (0.45f + 0.14f * kotlin.math.sin(k * 1.7f)) * (0.6f + 0.4f * a)
                drawCircle(Color.White.copy(alpha = 0.30f * a), r, Offset(x, cy + bob))
                drawCircle(DROWSY_GLOW.copy(alpha = 0.18f * a), r * 1.25f, Offset(x, cy + bob))
                x += cellPx * 0.55f
                k++
            }
            // Gotas de sueño desprendiéndose: la nube "pare" los bloques.
            for (i in event.cloudColumns.indices) {
                val col = event.cloudColumns[i]
                val ph = ((nowMs / 420f) + i * 0.23f) % 1f
                drawCircle(
                    Color.White.copy(alpha = 0.4f * a * (1f - ph)),
                    cellPx * 0.07f * (1f - ph * 0.5f),
                    Offset((col + 0.5f) * cellPx, cy + ph * cellPx * 1.1f)
                )
            }
        }
    }
}

/** Una "z" de sueño, dibujada a trazos: el signo universal de que algo se ha dormido. */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawZ(
    cx: Float, cy: Float, s: Float, color: Color
) {
    val w = s * 0.42f
    drawLine(color, Offset(cx - w, cy - s * 0.5f), Offset(cx + w, cy - s * 0.5f), strokeWidth = s * 0.2f)
    drawLine(color, Offset(cx + w, cy - s * 0.5f), Offset(cx - w, cy + s * 0.5f), strokeWidth = s * 0.2f)
    drawLine(color, Offset(cx - w, cy + s * 0.5f), Offset(cx + w, cy + s * 0.5f), strokeWidth = s * 0.2f)
}

/** Índigo del sueño de Maite (el mismo velo del bioma DORMILÓN). */
private val DROWSY_VEIL = Color(0xFF3B3F8F)
private val DROWSY_GLOW = Color(0xFFBFC6FF)

/** Duración de la entrada de la Maite. Bajo el tope de bioma (`BIOME_EVENT_MAX_MS` = 1200). */
private const val DROWSY_ONSET_MS = 1150

/** Lado de la caja Lottie de la Maite, en celdas. */
private const val MAITE_BOX_CELLS = 2.6f

/**
 * **ANCLA ÚNICA de la Maite**: desplazamiento horizontal de su caja (en múltiplos de su propio lado)
 * para un avance de entrada [enterE] y de salida [leave].
 *
 * Existe por la lección del astro (P-59, 2026-08-30): allí el Sol y su láser se colocaban con dos
 * fórmulas distintas y el rayo acabó saliendo 1,2 celdas fuera del disco. Aquí la Lottie y el cono
 * del soplido leen esto, así que **la bruma sale de su boca por construcción**, no por coincidencia.
 *
 * Va de fuera del borde izquierdo (−1,35) a un cuarto de tablero (+0,25) y se retira por donde vino.
 */
private fun maiteOffsetFactor(enterE: Float, leave: Float): Float =
    -1.35f + 1.6f * enterE - 2.0f * leave

/**
 * **Bosque del Sueño: ENTRA LA MAITE** (P-63, auditoría de eventos de bioma 2026-08-30).
 *
 * `DrowsyOnset` era el otro evento del juego **sin composable propio** (con `CrystalCrack`). Lo
 * único que ocurría al dormirte eran unas «z» flotando en el fondo del panel de fichas: **el jugador
 * perdía la rotación y no sabía por qué**, y la dueña del bioma —que tiene su propio Lottie desde
 * siempre, `teika_dormilon`— no aparecía nunca.
 *
 * Cuatro tiempos: la Maite **entra** flotando por el borde izquierdo → **sopla** un cono de bruma →
 * la bruma **barre** el tablero con sus «z» → se **retira** por donde vino, dejando el velo que la
 * bandeja ya recoge (ver el adormecimiento de las fichas en `GameScreen`).
 *
 * El velo se queda por DEBAJO de la mitad de opacidad del de la habilidad `DrowsySpell`: esto es la
 * mecánica ambiental, que dura varios turnos, no el clímax de una habilidad. Tapar el tablero
 * durante todo el sueño haría el bioma injugable.
 */
@Composable
fun DrowsyOnsetVFX(
    event: GameEvent.DrowsyOnset,
    cellPx: Float,
    onComplete: () -> Unit
) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        clock.snapTo(0f)
        clock.animateTo(1f, tween(DROWSY_ONSET_MS, easing = LinearEasing))
        onComplete()
    }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.teika_dormilon))
    val lottieProgress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)

    // Las «z» del soplido: posición y ritmo fijos por evento (nada de RNG por frame).
    // P-67: las «z» son adorno. El velo, el cono y la propia Maite se quedan: son lo que
    // explica por qué dejas de poder rotar.
    val zCount = fxCount(5)
    val zs = remember(event.id, zCount) {
        val h = event.id.hashCode()
        List(zCount) { i ->
            val s = h + i * 0x9E3779
            DrowsyPuff(
                delay = i * 0.075f,
                reach = 0.45f + (s ushr 4 and 0x3F) / 150f,
                drift = ((s ushr 10 and 0x1F) - 16f) / 42f,
                size = 0.34f + (s ushr 7 and 0x0F) / 60f
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val t = clock.value
            if (t >= 1f) return@Canvas
            val w = size.width
            val h = size.height
            // Entrada 0→0.30 · soplido 0.30→0.55 · barrido 0.55→0.82 · retirada 0.82→1.
            val enter = (t / 0.30f).coerceIn(0f, 1f)
            val enterE = 1f - (1f - enter) * (1f - enter) * (1f - enter)   // frena al llegar
            val blow = ((t - 0.30f) / 0.25f).coerceIn(0f, 1f)
            val sweep = ((t - 0.42f) / 0.40f).coerceIn(0f, 1f)
            val leave = ((t - 0.82f) / 0.18f).coerceIn(0f, 1f)

            // BOCA de la Maite, derivada del MISMO anclaje que su Lottie ([maiteOffsetFactor]): la
            // caja va alineada a CenterStart, así que su centro es offset + medio lado, y la boca
            // cae a su derecha. Sin esto el cono saldría de un punto aproximado y no de ella.
            val maiteBox = cellPx * MAITE_BOX_CELLS
            val bob = kotlin.math.sin(t * 9f) * 0.03f
            val mouthX = maiteBox * (maiteOffsetFactor(enterE, leave) + 0.5f + 0.30f)
            val mouthY = h * 0.5f + maiteBox * (-0.16f + bob)

            // ── 1. VELO que avanza con el soplido — se queda tenue, no tapa el tablero. ──
            if (sweep > 0f) {
                val veilA = 0.26f * sweep * (1f - 0.35f * leave)
                drawRect(
                    Brush.radialGradient(
                        listOf(
                            DROWSY_VEIL.copy(alpha = veilA),
                            DROWSY_VEIL.copy(alpha = veilA * 0.55f),
                            Color.Transparent
                        ),
                        center = Offset(mouthX, mouthY),
                        radius = (w * 1.5f * sweep).coerceAtLeast(1f)
                    )
                )
            }

            // ── 2. EL SOPLIDO: cono de bruma que sale de su boca hacia el tablero. ──
            if (blow > 0f && leave < 1f) {
                val cone = blow * (1f - leave)
                for (k in 0 until 3) {
                    val spread = h * (0.10f + k * 0.07f) * cone
                    val reach = w * (0.35f + k * 0.22f) * cone
                    drawOval(
                        DROWSY_GLOW.copy(alpha = 0.16f * cone * (1f - k * 0.25f)),
                        topLeft = Offset(mouthX, mouthY - spread),
                        size = Size(reach, spread * 2f)
                    )
                }
            }

            // ── 3. Las «z» que viajan con el soplido y suben. ──
            zs.forEach { p ->
                val local = ((t - 0.30f - p.delay) / 0.55f).coerceIn(0f, 1f)
                if (local <= 0f || local >= 1f) return@forEach
                val a = kotlin.math.sin(local * 3.14159f) * 0.9f
                val zx = mouthX + w * p.reach * local
                val zy = mouthY - h * 0.22f * local + p.drift * h * local
                drawSleepZGlyph(Offset(zx, zy), cellPx * p.size, DROWSY_GLOW.copy(alpha = a))
            }
        }

        // La MAITE: flota, cabecea y se va por donde vino. `graphicsLayer` para que la escala y el
        // desplazamiento no toquen layout — y para que escale desde su centro, no desde una esquina.
        val maiteSize = with(LocalDensity.current) { (cellPx * MAITE_BOX_CELLS).toDp() }
        LottieAnimation(
            composition = composition,
            progress = { lottieProgress },
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(maiteSize)
                .graphicsLayer {
                    val t = clock.value
                    val enter = (t / 0.30f).coerceIn(0f, 1f)
                    val enterE = 1f - (1f - enter) * (1f - enter) * (1f - enter)
                    val leave = ((t - 0.82f) / 0.18f).coerceIn(0f, 1f)
                    val bob = kotlin.math.sin(t * 9f) * 0.03f
                    // MISMA fórmula que la boca del soplido — ver [maiteOffsetFactor].
                    translationX = size.width * maiteOffsetFactor(enterE, leave)
                    translationY = size.height * (-0.16f + bob)
                    // Escala con `graphicsLayer` (alrededor del CENTRO), no con `.size()`: la lección
                    // del astro — `.size()` bajo una alineación de borde mueve el centro al escalar y
                    // desengancha lo que se haya anclado a él.
                    val sc = 0.72f + 0.28f * enterE
                    scaleX = sc; scaleY = sc
                    alpha = enter * (1f - leave)
                    // Cabecea de sueño mientras sopla.
                    rotationZ = kotlin.math.sin(t * 6f) * 6f * enterE
                }
        )
    }
}

/** Una «z» del soplido de la Maite. */
private data class DrowsyPuff(
    val delay: Float,
    val reach: Float,
    val drift: Float,
    val size: Float
)

/** La «z» de sueño (tres trazos). Gemela de `drawSleepZ` de la bandeja, para que sean la misma cosa. */
private fun DrawScope.drawSleepZGlyph(center: Offset, s: Float, color: Color) {
    val w = s * 0.8f
    val tl = Offset(center.x - w / 2f, center.y - s / 2f)
    val tr = Offset(center.x + w / 2f, center.y - s / 2f)
    val bl = Offset(center.x - w / 2f, center.y + s / 2f)
    val br = Offset(center.x + w / 2f, center.y + s / 2f)
    val sw = s * 0.14f
    drawLine(color, tl, tr, sw)
    drawLine(color, tr, bl, sw)
    drawLine(color, bl, br, sw)
}

/**
 * Rayco / **ENERGÍA CRUZADA** ([GameEvent.EnergizeStrike]) — el momento de energizar.
 *
 * Un rayo cae sobre cada Teika elegido, escalonado: **destello del cielo** (el aire se carga sobre
 * el objetivo) → **descarga** (el rayo baja quebrado, con ramas) → **impacto** (fogonazo, anillo y
 * chispas). El aura persistente de `OverloadChargeGlow` se enciende con el impacto, no antes: el
 * ViewModel añade cada Teika a `energizedTeikaIds` cuando su rayo llega.
 *
 * El rayo se dibuja con un zigzag determinista (semilla del id) en vez de aleatorio por frame: si
 * cambiara cada frame se vería como ruido en vez de como un rayo.
 */
@Composable
fun EnergizeStrikeVFX(
    event: GameEvent.EnergizeStrike,
    cellPx: Float,
    onComplete: () -> Unit
) {
    if (event.targets.isEmpty()) { LaunchedEffect(event.id) { onComplete() }; return }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }
    val seed = remember(event.id) { event.id.hashCode() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val nowMs = progress.value * event.durationMillis
        val bolt = event.boltMs.toFloat()

        for (i in event.targets.indices) {
            val tg = event.targets[i]
            val startAt = i * event.stepMs
            val hitAt = startAt + bolt
            if (nowMs < startAt) continue
            val cx = tg.x * cellPx
            val cy = tg.y * cellPx
            val r = tg.radius * cellPx

            // ── Carga: el aire se enciende sobre el objetivo antes de que baje el rayo ──
            if (nowMs < hitAt) {
                val t = ((nowMs - startAt) / bolt).coerceIn(0f, 1f)
                drawCircle(
                    Brush.radialGradient(
                        listOf(ELECTRIC_CYAN.copy(alpha = 0.35f * t), Color.Transparent),
                        Offset(cx, 0f), r * 2.2f
                    ),
                    radius = r * 2.2f, center = Offset(cx, 0f)
                )
                // El rayo baja: se dibuja solo la parte ya recorrida.
                val head = cy * t
                var px = cx
                var py = 0f
                var k = 0
                while (py < head) {
                    val ny = (py + cellPx * 0.42f).coerceAtMost(head)
                    // Zigzag determinista: mismo rayo en todos los frames.
                    val nx = cx + (((seed + i * 31 + k * 17) % 7) - 3) * cellPx * 0.09f
                    drawLine(
                        ELECTRIC_WHITE.copy(alpha = 0.95f),
                        Offset(px, py), Offset(nx, ny),
                        strokeWidth = cellPx * 0.075f
                    )
                    drawLine(
                        ELECTRIC_CYAN.copy(alpha = 0.5f),
                        Offset(px, py), Offset(nx, ny),
                        strokeWidth = cellPx * 0.19f
                    )
                    // Rama ocasional: un rayo recto no parece un rayo.
                    if (k % 3 == 1) {
                        val bx = nx + (((seed + k * 13) % 5) - 2) * cellPx * 0.28f
                        drawLine(
                            ELECTRIC_CYAN.copy(alpha = 0.4f),
                            Offset(nx, ny), Offset(bx, ny + cellPx * 0.3f),
                            strokeWidth = cellPx * 0.045f
                        )
                    }
                    px = nx; py = ny; k++
                }
            }

            // ── Impacto: fogonazo, anillo y chispas ──
            if (nowMs in hitAt..(hitAt + 320f)) {
                val t = ((nowMs - hitAt) / 320f).coerceIn(0f, 1f)
                val a = 1f - t
                drawCircle(
                    Brush.radialGradient(
                        listOf(ELECTRIC_WHITE.copy(alpha = 0.75f * a), ELECTRIC_CYAN.copy(alpha = 0.3f * a), Color.Transparent),
                        Offset(cx, cy), r * 2f
                    ),
                    radius = r * 2f, center = Offset(cx, cy)
                )
                drawCircle(
                    ELECTRIC_CYAN.copy(alpha = 0.7f * a),
                    r * (1f + t * 1.4f), Offset(cx, cy),
                    style = Stroke(width = cellPx * 0.07f * a)
                )
                for (s in 0 until 9) {
                    val ang = (s / 9f + (seed and 0x7) / 8f) * 6.28318f
                    val rr = r * (1f + 1.7f * t)
                    drawCircle(
                        ELECTRIC_WHITE.copy(alpha = 0.8f * a), cellPx * 0.04f,
                        Offset(cx + kotlin.math.cos(ang) * rr, cy + kotlin.math.sin(ang) * rr)
                    )
                }
            }
        }
    }
}

private val ELECTRIC_CYAN = Color(0xFF6FE8FF)
private val ELECTRIC_WHITE = Color(0xFFEAFBFF)

/**
 * Cristóbal / **ESCUDO CRISTALINO** ([GameEvent.CrystalAegis]) — la entrada.
 *
 * Dos tiempos: **cristalización** (facetas creciendo desde los bordes hacia dentro, refractando) y
 * **escaneo** (un barrido de luz cruza el tablero y destella en los huecos). El escaneo es el que
 * hace el trabajo narrativo: sin él, las fichas que vuelan después se leían como el juego jugando
 * solo en vez de como Cristóbal decidiendo dónde van.
 *
 * ## P-88 — rehecha el 2026-09-02 (grabación `analisistutorial`)
 *
 * Tal como estaba, esto **parecía un fallo de dibujo**: una orla de triángulos GRISES y duros
 * comiéndose el tablero durante un segundo. Y es la primera habilidad que un jugador ve en su vida,
 * porque la lección 7 se la presta. Cuatro causas, todas medibles:
 *
 *  - **El color no era el suyo.** Las facetas se pintaban con `#BFE9FF` al 16 % de opacidad sobre un
 *    tablero casi negro: un azul hielo tan desaturado y tan translúcido pierde el tono por completo
 *    y sale gris. Cristóbal es **verde azulado saturado** — muestreado de su propia carta de
 *    «¡NUEVO PERSONAJE!»: `#70DED5`. Ahora es ese, y las facetas van en degradado (brillantes en el
 *    borde, transparentes en la punta) en vez de placas planas.
 *  - **Tapaban el tablero.** Las cuñas medían `0.34` del lado menor: un tercio de la superficie de
 *    juego por cada lado, o sea el tablero entero. Ahora `0.15`: **enmarcan**, no tragan.
 *  - **No se alineaban con nada.** Siete cuñas por lado sobre ocho columnas ⇒ ninguna coincidía con
 *    la rejilla y el conjunto se leía como ruido. Ahora hay una por columna y por fila: el tablero
 *    se cristaliza, no se ensucia.
 *  - **Se cortaban en seco.** El `hold` bajaba solo al 45 % y desaparecía de golpe al terminar el
 *    evento. Ahora se retira a cero con el escaneo: el escudo **se repliega**.
 *
 * Se le añade además el aro del borde del tablero, que es lo que dice «escudo» sin escribirlo.
 */
@Composable
fun CrystalAegisVFX(
    event: GameEvent.CrystalAegis,
    cellPx: Float,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }
    val seed = remember(event.id) { event.id.hashCode() }
    // Una cuña por columna y por fila: la rejilla del tablero es la que manda. Con «Reducir efectos»
    // bajan a la mitad y el remate sigue leyéndose igual.
    val wedgesFull = GameState.GRID_SIZE
    val wedges = fxCount(wedgesFull).coerceAtLeast(3)
    // Se reutiliza en todo el fotograma: este Canvas dibuja hasta 32 cuñas y construir un Path por
    // cada una era basura por frame (regla de la casa: cero asignaciones en el bucle de dibujo).
    val wedge = remember(event.id) { Path() }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val nowMs = progress.value * event.durationMillis
        val grow = event.growMs.toFloat()
        val w = size.width
        val h = size.height

        // ── 1. Cristalización: facetas creciendo desde los cuatro bordes ───────
        val g = (nowMs / grow).coerceIn(0f, 1f)
        val ease = g * g * (3f - 2f * g)
        // Se retira A CERO con el escaneo: el escudo se repliega en vez de cortarse en seco.
        val hold =
            if (nowMs < grow) 1f
            else 1f - ((nowMs - grow) / (event.scanMs * 0.85f)).coerceIn(0f, 1f)

        // El aro del borde: lo que dice «escudo» sin escribirlo. Entra con la cristalización y se
        // va con ella.
        val rim = (ease * hold).coerceIn(0f, 1f)
        if (rim > 0.01f) {
            drawRoundRect(
                color = CRYSTAL_TEAL.copy(alpha = 0.55f * rim),
                cornerRadius = CornerRadius(cellPx * 0.28f),
                style = Stroke(width = cellPx * 0.10f * rim)
            )
        }

        if (ease > 0.01f && hold > 0.01f) {
            val depth = kotlin.math.min(w, h) * 0.15f * ease
            val faceA = 0.34f * hold
            val edgeA = 0.62f * hold
            for (side in 0 until 4) {
                // Un solo degradado por LADO (no por cuña): brillante en el borde, transparente en la
                // punta. Es lo que convierte las placas planas en luz refractando hacia dentro.
                val brush = when (side) {
                    0 -> Brush.verticalGradient(
                        listOf(CRYSTAL_FACE.copy(alpha = faceA), Color.Transparent), 0f, depth
                    )
                    1 -> Brush.verticalGradient(
                        listOf(Color.Transparent, CRYSTAL_FACE.copy(alpha = faceA)), h - depth, h
                    )
                    2 -> Brush.horizontalGradient(
                        listOf(CRYSTAL_FACE.copy(alpha = faceA), Color.Transparent), 0f, depth
                    )
                    else -> Brush.horizontalGradient(
                        listOf(Color.Transparent, CRYSTAL_FACE.copy(alpha = faceA)), w - depth, w
                    )
                }
                for (k in 0 until wedges) {
                    val jitter = (((seed + side * 13 + k * 29) % 5) - 2) * 0.06f
                    val d = depth * (0.62f + 0.38f * ((k % 3) / 2f)) * (1f + jitter)
                    wedge.reset()
                    when (side) {
                        0 -> {  // arriba
                            val x0 = w * k / wedges; val x1 = w * (k + 1) / wedges
                            wedge.moveTo(x0, 0f); wedge.lineTo(x1, 0f); wedge.lineTo((x0 + x1) / 2f, d)
                        }
                        1 -> {  // abajo
                            val x0 = w * k / wedges; val x1 = w * (k + 1) / wedges
                            wedge.moveTo(x0, h); wedge.lineTo(x1, h); wedge.lineTo((x0 + x1) / 2f, h - d)
                        }
                        2 -> {  // izquierda
                            val y0 = h * k / wedges; val y1 = h * (k + 1) / wedges
                            wedge.moveTo(0f, y0); wedge.lineTo(0f, y1); wedge.lineTo(d, (y0 + y1) / 2f)
                        }
                        else -> { // derecha
                            val y0 = h * k / wedges; val y1 = h * (k + 1) / wedges
                            wedge.moveTo(w, y0); wedge.lineTo(w, y1); wedge.lineTo(w - d, (y0 + y1) / 2f)
                        }
                    }
                    wedge.close()
                    drawPath(wedge, brush)
                    drawPath(
                        wedge,
                        CRYSTAL_EDGE.copy(alpha = edgeA),
                        style = Stroke(width = cellPx * 0.05f)
                    )
                }
            }
        }

        // ── 2. Escaneo: barrido de luz que destella al cruzar ──────────────────
        if (nowMs >= grow) {
            val t = ((nowMs - grow) / event.scanMs).coerceIn(0f, 1f)
            val band = h * 0.16f
            val y = -band + (h + band * 2f) * t
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        CRYSTAL_TEAL.copy(alpha = 0.62f),
                        CRYSTAL_EDGE.copy(alpha = 0.42f),
                        Color.Transparent
                    )
                ),
                topLeft = Offset(0f, y),
                size = androidx.compose.ui.geometry.Size(w, band)
            )
            // Destellos en la línea del barrido: el «ya sé dónde va esto».
            val cols = (w / cellPx).toInt().coerceAtLeast(1)
            for (c in 0 until cols) {
                val gx = (c + 0.5f) * cellPx
                val gy = y + band * 0.5f
                val tw = 0.4f + 0.6f * kotlin.math.sin((c * 1.9f + t * 9f)).let { it * it }
                drawCircle(CRYSTAL_EDGE.copy(alpha = 0.7f * tw), cellPx * 0.07f, Offset(gx, gy))
            }
        }
    }
}

/**
 * La paleta de Cristóbal, y es la SUYA (P-88, 2026-09-02): [CRYSTAL_TEAL] está muestreado de su
 * propia carta de «¡NUEVO PERSONAJE!» en la grabación `analisistutorial` (`#70DED5`). Antes las
 * facetas iban en un azul hielo casi blanco que, al 16 % sobre un tablero negro, salía gris.
 */
private val CRYSTAL_TEAL = Color(0xFF70DED5)
private val CRYSTAL_FACE = Color(0xFF8FF0E6)
private val CRYSTAL_EDGE = Color(0xFFE6FFFB)

/**
 * Timoteo / **PUZZLE DESLIZANTE** ([GameEvent.PuzzleGridReveal]) — la entrada.
 *
 * Una rejilla de rompecabezas se dibuja sobre el tablero (con sus pestañas y muescas), **tiembla**
 * —Timoteo es el miedoso: le tiembla el pulso— y se desvanece dejando paso a las losetas. Sin esto
 * las piezas empezaban a deslizarse tras un destello genérico que no anunciaba nada.
 */
@Composable
fun PuzzleGridRevealVFX(
    event: GameEvent.PuzzleGridReveal,
    cellPx: Float,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = progress.value
        // Se dibuja de fuera adentro y se retira al final: nunca tapa el inicio de las losetas.
        val draw = (t / 0.55f).coerceIn(0f, 1f)
        val fade = 1f - ((t - 0.7f) / 0.3f).coerceIn(0f, 1f)
        if (fade <= 0.01f) return@Canvas
        val a = 0.55f * fade
        // Tembleque del pulso: pequeño y rápido, nunca desplaza más de un pelo.
        val shake = kotlin.math.sin(t * 46f) * cellPx * 0.02f * fade
        val cols = (size.width / cellPx).toInt().coerceAtLeast(1)
        val rows = (size.height / cellPx).toInt().coerceAtLeast(1)
        val tab = cellPx * 0.13f

        // Líneas verticales con pestaña/muesca alterna en cada tramo.
        for (c in 1 until cols) {
            val x = c * cellPx + shake
            if (c.toFloat() / cols > draw) continue
            for (r in 0 until rows) {
                val y0 = r * cellPx
                val y1 = y0 + cellPx
                val mid = (y0 + y1) / 2f
                val out = if ((r + c) % 2 == 0) 1f else -1f
                drawLine(PUZZLE_INK.copy(alpha = a), Offset(x, y0), Offset(x, mid - tab), strokeWidth = cellPx * 0.035f)
                // La pestaña: un semicírculo saliendo hacia el lado que toca.
                drawArc(
                    color = PUZZLE_INK.copy(alpha = a),
                    startAngle = if (out > 0) -90f else 90f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(x - tab, mid - tab),
                    size = androidx.compose.ui.geometry.Size(tab * 2f, tab * 2f),
                    style = Stroke(width = cellPx * 0.035f)
                )
                drawLine(PUZZLE_INK.copy(alpha = a), Offset(x, mid + tab), Offset(x, y1), strokeWidth = cellPx * 0.035f)
            }
        }
        // Líneas horizontales, mismo patrón.
        for (r in 1 until rows) {
            val y = r * cellPx + shake
            if (r.toFloat() / rows > draw) continue
            for (c in 0 until cols) {
                val x0 = c * cellPx
                val x1 = x0 + cellPx
                val mid = (x0 + x1) / 2f
                val out = if ((r + c) % 2 == 0) 1f else -1f
                drawLine(PUZZLE_INK.copy(alpha = a), Offset(x0, y), Offset(mid - tab, y), strokeWidth = cellPx * 0.035f)
                drawArc(
                    color = PUZZLE_INK.copy(alpha = a),
                    startAngle = if (out > 0) 0f else 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(mid - tab, y - tab),
                    size = androidx.compose.ui.geometry.Size(tab * 2f, tab * 2f),
                    style = Stroke(width = cellPx * 0.035f)
                )
                drawLine(PUZZLE_INK.copy(alpha = a), Offset(mid + tab, y), Offset(x1, y), strokeWidth = cellPx * 0.035f)
            }
        }
    }
}

/** Tinta clara de la rejilla: visible sobre el tablero oscuro sin competir con los bloques. */
private val PUZZLE_INK = Color(0xFFCFE0FF)

/**
 * Galo / **ECLIPSE VIVO** ([GameEvent.LivingEclipse]) — la ceremonia.
 *
 * Antes la habilidad se anunciaba con el destello genérico de compañero: pasaba algo, pero no se
 * leía un eclipse. Ahora hay cuatro tiempos, y la madera **no revienta hasta el tercero** (el
 * ViewModel escalona sus `LineCellShatter` desde `impactAtMs`), así que el golpe se ve venir:
 *
 *  1. **Oscurecimiento** — un disco negro cruza el tablero y lo va apagando; su borde enciende la
 *     corona. Es lo que convierte "una zona explota" en "ha caído la noche encima".
 *  2. **Lente** — anillos de lente gravitacional se cierran sobre la zona: la promesa del daño.
 *  3. **Implosión** — la zona espirala hacia dentro y se traga a sí misma.
 *  4. **Vuelta de la luz** — destello con aberración cromática (los tres canales llegan
 *     desalineados y se recomponen) y el tablero recupera su color.
 */
@Composable
fun LivingEclipseVFX(
    event: GameEvent.LivingEclipse,
    cellPx: Float,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(event.totalMs, easing = LinearEasing))
        onComplete()
    }
    // Radio de la zona en celdas: la lente y la implosión se ciñen a lo que de verdad se pulveriza.
    val zoneR = remember(event.id) {
        (event.cells.maxOfOrNull { c ->
            kotlin.math.hypot(
                (c.x - event.center.x).toFloat(), (c.y - event.center.y).toFloat()
            )
        } ?: 1f) + 0.6f
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val nowMs = progress.value * event.totalMs
        val darkEnd = event.darkenMs.toFloat()
        val lensEnd = darkEnd + event.lensMs
        val implodeEnd = lensEnd + event.implodeMs
        val c = Offset((event.center.x + 0.5f) * cellPx, (event.center.y + 0.5f) * cellPx)
        val rPx = zoneR * cellPx

        // ── 1. Oscurecimiento: el disco cruza y apaga el tablero ───────────────
        // La sombra se mantiene durante lente e implosión y se retira con la luz.
        val darkness = when {
            nowMs < darkEnd -> (nowMs / darkEnd).let { it * it * (3f - 2f * it) }
            nowMs < implodeEnd -> 1f
            else -> 1f - ((nowMs - implodeEnd) / event.restoreMs).coerceIn(0f, 1f)
        }
        if (darkness > 0.01f) {
            drawRect(Color(0xFF05060F).copy(alpha = 0.62f * darkness))
            // Corona: el borde del disco encendido, lo que hace que sea un ECLIPSE y no un apagón.
            val coronaR = rPx * (1.15f + 0.12f * kotlin.math.sin(nowMs / 140f))
            drawCircle(
                Brush.radialGradient(
                    listOf(Color.Transparent, Color(0xFFFFE7A8).copy(alpha = 0.55f * darkness), Color.Transparent),
                    c, coronaR * 1.25f
                ),
                radius = coronaR * 1.25f, center = c
            )
            drawCircle(Color(0xFF05060F).copy(alpha = 0.9f * darkness), rPx * 0.98f, c)
        }

        // ── 2. Lente gravitacional: anillos cerrándose sobre la zona ───────────
        if (nowMs in darkEnd..implodeEnd) {
            val t = ((nowMs - darkEnd) / (lensEnd - darkEnd)).coerceIn(0f, 1f)
            for (k in 0 until 3) {
                val ph = (t + k / 3f) % 1f
                drawCircle(
                    Color(0xFFB388FF).copy(alpha = 0.5f * (1f - ph)),
                    rPx * (1.5f - ph * 0.9f), c,
                    style = Stroke(width = cellPx * 0.06f)
                )
            }
        }

        // ── 3. Implosión: la zona espirala hacia dentro ────────────────────────
        if (nowMs in lensEnd..implodeEnd) {
            val t = ((nowMs - lensEnd) / event.implodeMs).coerceIn(0f, 1f)
            for (i in event.cells.indices) {
                val cell = event.cells[i]
                val from = Offset((cell.x + 0.5f) * cellPx, (cell.y + 0.5f) * cellPx)
                // Espiral: además de acercarse, gira alrededor del centro.
                val e = t * t
                val ang = kotlin.math.atan2(from.y - c.y, from.x - c.x) + e * 2.4f
                val dist = kotlin.math.hypot(from.x - c.x, from.y - c.y) * (1f - e)
                val p = Offset(c.x + kotlin.math.cos(ang) * dist, c.y + kotlin.math.sin(ang) * dist)
                drawCircle(
                    Color(0xFFD1B3FF).copy(alpha = 0.5f * (1f - e)),
                    cellPx * 0.16f * (1f - 0.5f * e), p
                )
            }
            // Núcleo que se traga la zona.
            drawCircle(Color(0xFF120A2A), rPx * 0.28f * (0.3f + t * 0.7f), c)
        }

        // ── 4. Vuelta de la luz con aberración cromática ───────────────────────
        if (nowMs >= implodeEnd) {
            val t = ((nowMs - implodeEnd) / event.restoreMs).coerceIn(0f, 1f)
            val a = 1f - t
            val shift = cellPx * 0.22f * a
            // Los tres canales llegan desalineados y se recomponen: la luz "vuelve a cuadrar".
            drawCircle(Color(0xFFFF4D6D).copy(alpha = 0.30f * a), rPx * (0.5f + t * 1.6f), Offset(c.x - shift, c.y))
            drawCircle(Color(0xFF4DFFC3).copy(alpha = 0.30f * a), rPx * (0.5f + t * 1.6f), c)
            drawCircle(Color(0xFF4DB8FF).copy(alpha = 0.30f * a), rPx * (0.5f + t * 1.6f), Offset(c.x + shift, c.y))
            drawCircle(Color.White.copy(alpha = 0.5f * a * a), rPx * 0.4f * a, c)
        }
    }
}

/** Lo que tarda un bloque en llegar a la boca (espejo de `GameViewModel.GLUTTON_FEAST_FALL_MS`). */
private const val GLUTTON_FEAST_FALL = 340f

/** Tinte de los bloques-estrella mientras vuelan hacia las fauces (dorado de objetivo). */
private val STAR_FALL_TINT = Color(0xFFFFC93C)

// ── Cocina Voraz / Glotón (bioma, nivel 66) ─────────────────────────────────────────────────
// Paleta EXACTA del Lottie `teika_gloton` (blob redondo PLANO, sin 3D): cuerpo con gradiente radial
// crema→naranja→marrón, contorno marrón, ojos oscuros con destellos, cejas, cachetes naranjas, boca-trazo.
private val GLUTTON_ORANGE   = Color(0xFFF09650)   // cachetes (#F09650 @65%)
private val GLUTTON_TOOTH    = Color(0xFFFFF6E9)   // dientes de las marcas de mordisco en el tablero
private val GLUTTON_PIT      = Color(0xFF0B1024)   // fondo del POZO mordido (más oscuro que el tablero)
private val GLUTTON_TONGUE   = Color(0xFFE85C86)   // lengua dentro de la boca abierta
private val GLUTTON_BODY_HI  = Color(0xFFFADFC2)   // gradiente cuerpo: stop 0    (0.982,0.876,0.762)
private val GLUTTON_BODY_MID = Color(0xFFF5B978)   //                   stop 0.45 (0.961,0.725,0.471)
private val GLUTTON_BODY_LO  = Color(0xFFB08556)   //                   stop 1    (0.692,0.522,0.339)
private val GLUTTON_OUTLINE  = Color(0xFF8E6B46)   // contorno del cuerpo
private val GLUTTON_EYE      = Color(0xFF17171F)   // ojos / interior de la boca
private val GLUTTON_BROW     = Color(0xFF1A1A21)   // cejas + trazo de la boca

// Dibuja el GLOTÓN como el Lottie `teika_gloton`: blob redondo PLANO (sin 3D). [center] = centro del
// cuerpo; [r] = radio del cuerpo. La cara se dibuja en orientación Lottie (ojos arriba, boca abajo) y
// se rota [faceRot]=[angleDeg]−90 para que la boca apunte hacia el interior del tablero. [openDeg] abre
// la boca (0 = sonrisa cerrada). [flash] (0..1) la blanquea al morder. Proporciones = Lottie/202.
private fun DrawScope.drawGluttonHead(
    center: Offset,
    r: Float,
    angleDeg: Float,
    openDeg: Float,
    alpha: Float = 1f,
    flash: Float = 0f,
    /** Path de la boca, reutilizable: las fauces que se quedan en el rincón pasan uno propio. */
    mouthPath: Path = Path(),
) {
    val faceRot = angleDeg - 90f
    val openness = (openDeg / 30f).coerceIn(0f, 1f)
    withTransform({ translate(center.x, center.y); rotate(faceRot, pivot = Offset.Zero) }) {
        // CUERPO: círculo con gradiente radial (crema arriba-izq → naranja → marrón) + contorno marrón.
        drawCircle(
            brush = Brush.radialGradient(
                0f to GLUTTON_BODY_HI, 0.45f to GLUTTON_BODY_MID, 1f to GLUTTON_BODY_LO,
                center = Offset(-r * 0.11f, -r * 0.27f), radius = r * 1.27f
            ),
            radius = r, center = Offset.Zero, alpha = alpha
        )
        drawCircle(GLUTTON_OUTLINE.copy(alpha = alpha), r, Offset.Zero, style = Stroke(width = r * 0.035f))

        // BRILLOS (elipses blancas arriba-izquierda).
        drawOval(
            Color.White.copy(alpha = alpha * 0.30f),
            topLeft = Offset(-r * 0.564f, -r * 0.559f), size = Size(r * 0.594f, r * 0.386f)
        )
        drawOval(
            Color.White.copy(alpha = alpha * 0.72f),
            topLeft = Offset(-r * 0.451f, -r * 0.505f), size = Size(r * 0.228f, r * 0.158f)
        )

        // CACHETES naranjas a los lados.
        for (side in 0..1) {
            val sgn = if (side == 0) -1f else 1f
            drawOval(
                GLUTTON_ORANGE.copy(alpha = alpha * 0.65f),
                topLeft = Offset(sgn * r * 0.718f - r * 0.223f, r * 0.099f - r * 0.161f),
                size = Size(r * 0.446f, r * 0.322f)
            )
        }

        // OJOS oscuros grandes con 2 destellos blancos cada uno.
        for (side in 0..1) {
            val sgn = if (side == 0) -1f else 1f
            val ec = Offset(sgn * r * 0.297f, -r * 0.089f)
            drawCircle(GLUTTON_EYE.copy(alpha = alpha), r * 0.124f, ec)
            drawCircle(Color.White.copy(alpha = alpha * 0.95f), r * 0.040f, Offset(ec.x - r * 0.045f, ec.y - r * 0.050f))
            drawCircle(Color.White.copy(alpha = alpha * 0.55f), r * 0.020f, Offset(ec.x + r * 0.040f, ec.y + r * 0.045f))
        }

        // CEJAS (trazos oscuros inclinados sobre los ojos).
        drawLine(GLUTTON_BROW.copy(alpha = alpha), Offset(-r * 0.149f, -r * 0.356f), Offset(-r * 0.505f, -r * 0.386f), strokeWidth = r * 0.054f, cap = StrokeCap.Round)
        drawLine(GLUTTON_BROW.copy(alpha = alpha), Offset(r * 0.149f, -r * 0.356f), Offset(r * 0.505f, -r * 0.386f), strokeWidth = r * 0.054f, cap = StrokeCap.Round)

        // BOCA: cerrada = sonrisa-trazo (estilo Lottie); abierta = boca oscura con lengua para "comer".
        val mw = r * 0.22f
        val ulY = r * 0.34f
        if (openness < 0.06f) {
            val smile = mouthPath.apply {
                rewind()
                moveTo(-mw, ulY); quadraticTo(0f, ulY + r * 0.10f, mw, ulY)
            }
            drawPath(smile, GLUTTON_BROW.copy(alpha = alpha), style = Stroke(width = r * 0.06f, cap = StrokeCap.Round))
        } else {
            val lowY = ulY + r * 0.10f + openness * r * 0.55f
            val mouth = mouthPath.apply {
                rewind()
                moveTo(-mw, ulY)
                quadraticTo(0f, ulY + r * 0.09f, mw, ulY)      // labio superior (sonrisa leve)
                quadraticTo(mw * 0.55f, lowY, 0f, lowY)         // baja a la base
                quadraticTo(-mw * 0.55f, lowY, -mw, ulY)        // vuelve al inicio
                close()
            }
            drawPath(mouth, GLUTTON_EYE.copy(alpha = alpha))    // interior oscuro
            drawOval(
                GLUTTON_TONGUE.copy(alpha = alpha * 0.95f),
                topLeft = Offset(-mw * 0.6f, lowY - r * 0.16f - openness * r * 0.06f),
                size = Size(mw * 1.2f, r * 0.12f + openness * r * 0.16f)
            )
            drawPath(mouth, GLUTTON_BROW.copy(alpha = alpha), style = Stroke(width = r * 0.045f, cap = StrokeCap.Round))
        }

        // Destello del mordisco.
        if (flash > 0.01f) {
            drawCircle(Color.White.copy(alpha = alpha * flash * 0.6f), r * 0.55f, Offset(0f, ulY))
        }
    }
}

/**
 * Borde DENTADO de mordisco a lo largo del segmento a→b: una hilera de [count] dientes triangulares
 * cuyo vértice se hunde [depth] px en la dirección [pointDir] (hacia el rincón). Deja la huella de
 * "le marcaron los dientes" en el borde de la zona devorada.
 */
private fun DrawScope.drawBiteTeeth(
    path: Path, a: Offset, b: Offset, pointDir: Offset, count: Int, depth: Float, color: Color
) {
    for (i in 0 until count) {
        val t0 = i.toFloat() / count
        val t1 = (i + 1f) / count
        val p0 = Offset(a.x + (b.x - a.x) * t0, a.y + (b.y - a.y) * t0)
        val p1 = Offset(a.x + (b.x - a.x) * t1, a.y + (b.y - a.y) * t1)
        val apex = Offset((p0.x + p1.x) / 2f + pointDir.x * depth, (p0.y + p1.y) / 2f + pointDir.y * depth)
        // Path reutilizado (reset por diente) → sin asignar Path por frame.
        path.reset()
        path.moveTo(p0.x, p0.y); path.lineTo(apex.x, apex.y); path.lineTo(p1.x, p1.y); path.close()
        drawPath(path, color)
    }
}

/** Esquina [corner] (0=SI,1=SD,2=II,3=ID) → (ancla en px, ángulo hacia el centro en grados). */
private fun gluttonCornerAnchor(corner: Int, boardPx: Float): Pair<Offset, Float> = when (corner) {
    0 -> Offset(0f, 0f) to 45f
    1 -> Offset(boardPx, 0f) to 135f
    2 -> Offset(0f, boardPx) to -45f
    else -> Offset(boardPx, boardPx) to 225f
}

/**
 * Cocina Voraz / Glotón (persistente): pozos digiriéndose + fauces en el rincón amenazado +
 * pips de presión ociosa y texto de counterplay. [idleCounter] = gluttonCounter del estado.
 */
@Composable
fun GluttonBiomeOverlay(
    deadCells: Map<Position, Int>,
    openCorner: Int,
    idleCounter: Int = 0,
    /** Pantano Tóxico también activo (p. ej. TOXIC primary + GLUTTON secondary). */
    toxicAlsoActive: Boolean = false,
    cellSize: Dp,
    modifier: Modifier = Modifier
) {
    val clock = rememberInfiniteTransition(label = "gluttonBiome")
    val pulse by clock.animateFloat(
        0f, 1f, infiniteRepeatable(tween(820, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(openCorner) {
        if (openCorner in 0..3) {
            entrance.snapTo(0f)
            entrance.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
        } else entrance.snapTo(0f)
    }
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val mouthOpen = openCorner in 0..3

    Box(modifier = modifier) {
        // Capa propia (auditoría de fluidez 2026-09-29): los pozos laten cada fotograma todo el nivel.
        Spacer(Modifier.fillMaxSize().graphicsLayer().drawWithCache {
            val cs = cellSize.toPx()
            val mid = GameState.GRID_SIZE / 2
            val deadSet = deadCells.keys
            val shadowDepth = cs * 0.34f
            val shadowOps = ArrayList<Triple<Brush, Offset, Size>>()
            deadCells.forEach { (pos, _) ->
                val tl0 = Offset(pos.x * cs, pos.y * cs)
                if (Position(pos.x, pos.y - 1) !in deadSet) shadowOps.add(Triple(
                    Brush.verticalGradient(listOf(Color(0xCC000000), Color(0x00000000)), startY = tl0.y, endY = tl0.y + shadowDepth),
                    tl0, Size(cs, shadowDepth)))
                if (Position(pos.x, pos.y + 1) !in deadSet) shadowOps.add(Triple(
                    Brush.verticalGradient(listOf(Color(0x00000000), Color(0xCC000000)), startY = tl0.y + cs - shadowDepth, endY = tl0.y + cs),
                    Offset(tl0.x, tl0.y + cs - shadowDepth), Size(cs, shadowDepth)))
                if (Position(pos.x - 1, pos.y) !in deadSet) shadowOps.add(Triple(
                    Brush.horizontalGradient(listOf(Color(0xCC000000), Color(0x00000000)), startX = tl0.x, endX = tl0.x + shadowDepth),
                    tl0, Size(shadowDepth, cs)))
                if (Position(pos.x + 1, pos.y) !in deadSet) shadowOps.add(Triple(
                    Brush.horizontalGradient(listOf(Color(0x00000000), Color(0xCC000000)), startX = tl0.x + cs - shadowDepth, endX = tl0.x + cs),
                    Offset(tl0.x + cs - shadowDepth, tl0.y), Size(shadowDepth, cs)))
            }
            val cornerInfo = if (openCorner in 0..3) gluttonCornerAnchor(openCorner, GameState.GRID_SIZE * cs) else null
            var auraBrush: Brush? = null
            var auraC = Offset.Zero; var auraR = 0f
            var headAngle = 0f; var headR = 0f
            var headSettled = Offset.Zero; var headStart = Offset.Zero
            if (cornerInfo != null) {
                val (anchor, angle) = cornerInfo
                val rad = Math.toRadians(angle.toDouble())
                val dirIn = Offset(cos(rad).toFloat(), sin(rad).toFloat())
                headR = cs * 1.6f
                headAngle = angle
                headSettled = Offset(anchor.x - dirIn.x * headR * 0.22f, anchor.y - dirIn.y * headR * 0.22f)
                headStart   = Offset(anchor.x - dirIn.x * headR * 1.4f, anchor.y - dirIn.y * headR * 1.4f)
                auraC = Offset(anchor.x + dirIn.x * cs * 0.5f, anchor.y + dirIn.y * cs * 0.5f)
                auraR = headR * 1.4f
                auraBrush = Brush.radialGradient(listOf(Color(0xFFFF5252), Color(0x00FF5252)), center = auraC, radius = auraR)
            }
            val teethPath = Path()
            val headMouth = Path()
            val pitRim = Stroke(width = cs * 0.06f)
            // Qué bordes de cada pozo llevan dientes: depende del conjunto, no del tiempo. Antes eran
            // 2 `Position` nuevas por pozo y fotograma.
            val biteX = deadCells.keys.associateWith { pos ->
                Position(pos.x + (if (pos.x < mid) 1 else -1), pos.y) !in deadSet
            }
            val biteY = deadCells.keys.associateWith { pos ->
                Position(pos.x, pos.y + (if (pos.y < mid) 1 else -1)) !in deadSet
            }
            onDrawBehind {
                deadCells.forEach { (pos, turns) ->
                    val tl = Offset(pos.x * cs, pos.y * cs)
                    drawRect(GLUTTON_PIT, topLeft = tl, size = Size(cs, cs))
                    // Digestión legible: anillo que se apaga según turnos restantes.
                    val life = (turns.toFloat() / com.cukiti.teikafusion.model.GluttonBiomeRules.RECOVER_TURNS)
                        .coerceIn(0.15f, 1f)
                    drawRect(
                        Color(0xFFFFA24D).copy(alpha = 0.12f + 0.18f * life * pulse),
                        topLeft = tl, size = Size(cs, cs),
                        style = pitRim
                    )
                }
                shadowOps.forEach { (brush, tl, sz) -> drawRect(brush, topLeft = tl, size = sz) }
                deadCells.forEach { (pos, turns) ->
                    val a = if (turns <= 2) 0.5f else 0.9f
                    val tl0 = Offset(pos.x * cs, pos.y * cs)
                    val ix = if (pos.x < mid) 1 else -1
                    val iy = if (pos.y < mid) 1 else -1
                    val toothCol = GLUTTON_TOOTH.copy(alpha = a)
                    val depth = cs * 0.20f
                    if (biteX[pos] == true) {
                        val ex = tl0.x + if (ix > 0) cs else 0f
                        drawBiteTeeth(teethPath, Offset(ex, tl0.y), Offset(ex, tl0.y + cs), Offset(-ix.toFloat(), 0f), 3, depth, toothCol)
                    }
                    if (biteY[pos] == true) {
                        val ey = tl0.y + if (iy > 0) cs else 0f
                        drawBiteTeeth(teethPath, Offset(tl0.x, ey), Offset(tl0.x + cs, ey), Offset(0f, -iy.toFloat()), 3, depth, toothCol)
                    }
                }
                val ab = auraBrush
                if (ab != null) {
                    val e = entrance.value
                    val cen = Offset(headStart.x + (headSettled.x - headStart.x) * e, headStart.y + (headSettled.y - headStart.y) * e)
                    val openFrac = ((e - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    val openDeg = openFrac * (24f + pulse * 8f)
                    drawCircle(ab, auraR, auraC, alpha = (0.10f + 0.16f * pulse) * e)
                    drawGluttonHead(cen, headR, headAngle, openDeg, mouthPath = headMouth)
                }
                // (Pips de presión RETIRADOS a pedido — los círculos que se llenaban turno a turno abajo
                // del tablero, tanto en Cocina Voraz como en Pantano Tóxico donde el Glotón corre de 2ª capa.)
            }
        })
    }
}

/**
 * Cocina Voraz / Glotón (one-shot): la dentellada. Las fauces se cierran de golpe sobre el rincón
 * [GameEvent.GluttonBite.cells] con un destello + lluvia de migajas, luego se retiran.
 */
@Composable
fun GluttonBiteVFX(
    event: GameEvent.GluttonBite,
    cellPx: Float,
    onComplete: () -> Unit
) {
    if (event.cells.isEmpty()) { LaunchedEffect(event.id) { onComplete() }; return }
    val anim = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(640, easing = LinearEasing))
        onComplete()
    }
    val p = anim.value
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cs = cellPx
        val boardPx = GameState.GRID_SIZE * cs
        val (anchor, angle) = gluttonCornerAnchor(event.corner, boardPx)
        val rad = Math.toRadians(angle.toDouble())
        val dirIn = Offset(cos(rad).toFloat(), sin(rad).toFloat())
        val r = cs * 1.6f
        val cen = Offset(anchor.x - dirIn.x * r * 0.22f, anchor.y - dirIn.y * r * 0.22f)
        // La boca se cierra de golpe (abierta → mordida) en el primer 45%; luego se retira tras el 70%.
        val snap = (p / 0.45f).coerceIn(0f, 1f)
        val recede = ((p - 0.7f) / 0.3f).coerceIn(0f, 1f)
        val openDeg = 30f - 30f * snap   // de abierta a cerrada (mordida)
        val alpha = 1f - recede
        val flash = if (snap >= 0.8f) ((snap - 0.8f) / 0.2f) * (1f - recede) else 0f
        drawGluttonHead(cen, r, angle, openDeg, alpha, flash)
        // Migajas saltando tras el chasquido.
        if (snap >= 1f) {
            val crumbT = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
            event.cells.forEach { pos ->
                val cx = pos.x * cs + cs / 2f; val cy = pos.y * cs + cs / 2f
                for (k in 0 until 5) {
                    val aa = Math.toRadians(k * 72.0 + event.corner * 30.0)
                    val rr = cs * (0.3f + crumbT * 1.4f)
                    drawCircle(
                        Color(0xFFD8B26A).copy(alpha = (1f - crumbT) * 0.8f),
                        cs * 0.08f,
                        Offset(cx + (cos(aa) * rr).toFloat(), cy + (sin(aa) * rr).toFloat() - crumbT * cs * 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Cocina Voraz / Glotón (one-shot): RETIRADA suave. El jugador rompió una línea o fusionó a tiempo
 * → el glotón cierra la boca y se hunde de vuelta al rincón [GameEvent.GluttonRetreat.corner],
 * encogiendo y desvaneciéndose. Premio visual por reaccionar antes del mordisco.
 */
@Composable
fun GluttonRetreatVFX(
    event: GameEvent.GluttonRetreat,
    cellPx: Float,
    onComplete: () -> Unit
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        anim.snapTo(0f)
        anim.animateTo(1f, tween(440, easing = FastOutSlowInEasing))
        onComplete()
    }
    val p = anim.value
    Canvas(modifier = Modifier.fillMaxSize()) {
        val cs = cellPx
        val boardPx = GameState.GRID_SIZE * cs
        val (anchor, angle) = gluttonCornerAnchor(event.corner, boardPx)
        val rad = Math.toRadians(angle.toDouble())
        val dirIn = Offset(cos(rad).toFloat(), sin(rad).toFloat())
        val r = cs * 1.3f
        // Cierra la boca y se HUNDE de vuelta hacia fuera del tablero (la bisagra retrocede).
        val inset = cs * 0.12f * (1f - p) - cs * 1.9f * p
        val hinge = Offset(anchor.x + dirIn.x * inset, anchor.y + dirIn.y * inset)
        val openDeg = 22f * (1f - p)                       // cierra la boca al retirarse
        val alpha = if (p < 0.55f) 1f else (1f - (p - 0.55f) / 0.45f)
        drawGluttonHead(hinge, r, angle, openDeg, alpha)
    }
}

// (PODADO 2026-09-29, auditoría de fluidez) Aquí vivía `GhostlyPieceSmoke`, el humo de la ficha fantasmal de
// la bandeja: sin un solo llamante desde que el Fantasma / Atravesar dejó de pintarlo. Fósil.

// ── ¡PERFECTO! — tablero limpio tras un clear (referencia Block Blast, 2026-07-20) ────────────────
// Celebración centrada en el tablero: rótulo dorado con pop de spring + "+bonus" + confeti radial
// multicolor con gravedad. ÚNICO consumidor de GameEvent.PerfectClear (lo monta GameBoard en el
// Box EXTERNO, encima de los cuerpos). Espera el delayMs del evento (fin del telegraph del clear).

private const val PERFECT_LIFE_MS = 1300
private const val PERFECT_CONFETTI = 26

@Composable
fun PerfectClearVFX(
    event: GameEvent.PerfectClear,
    cellSize: Dp,
    onComplete: () -> Unit
) {
    val progress = remember { Animatable(0f) }
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (event.delayMs > 0) kotlinx.coroutines.delay(event.delayMs.toLong())
        started = true
        progress.animateTo(1f, tween(PERFECT_LIFE_MS, easing = LinearEasing))
        onComplete()
    }
    if (!started) return

    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    // Semillas del confeti: dirección, velocidad, tamaño, giro y color — estables por evento.
    val confetti = remember {
        val rnd = kotlin.random.Random(event.id.hashCode())
        List(PERFECT_CONFETTI) {
            val ang = rnd.nextFloat() * 2f * PI.toFloat()
            floatArrayOf(
                ang,
                0.8f + rnd.nextFloat() * 2.4f,                 // velocidad (celdas)
                0.10f + rnd.nextFloat() * 0.12f,               // tamaño (fracción de celda)
                (rnd.nextFloat() - 0.5f) * 720f,               // giro total
                rnd.nextInt(pieceColors.size).toFloat()        // paleta
            )
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val t = progress.value
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val confT = (t / 0.85f).coerceIn(0f, 1f)
            val confAlpha = if (confT > 0.7f) ((1f - confT) / 0.3f).coerceIn(0f, 1f) else 1f
            confetti.forEach { c ->
                val dist = c[1] * cellPx * confT
                val x = cx + cos(c[0]) * dist
                val y = cy + sin(c[0]) * dist + confT * confT * cellPx * 1.4f
                val hw = cellPx * c[2] * (1f - confT * 0.3f)
                val col = pieceColors[c[4].toInt() % pieceColors.size][1]
                rotate(c[3] * confT, Offset(x, y)) {
                    drawRoundRect(
                        col.copy(alpha = confAlpha),
                        Offset(x - hw, y - hw * 0.55f),
                        Size(hw * 2f, hw * 1.1f),
                        CornerRadius(hw * 0.3f, hw * 0.3f)
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Pop de entrada (overshoot corto) + desvanecido final.
            val pop = when {
                t < 0.12f -> 0.4f + (t / 0.12f) * 0.75f
                t < 0.22f -> 1.15f - ((t - 0.12f) / 0.10f) * 0.15f
                else      -> 1f
            }
            val textAlpha = if (t > 0.78f) ((1f - t) / 0.22f).coerceIn(0f, 1f) else 1f
            Text(
                text = "¡PERFECTO!",
                fontSize = 34.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFFFFD54F),
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color(0xFFB33217), offset = Offset(0f, 4f), blurRadius = 10f
                    )
                ),
                modifier = Modifier.graphicsLayer {
                    scaleX = pop; scaleY = pop; alpha = textAlpha
                }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "+${event.bonus}",
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha * ((t - 0.15f) / 0.1f).coerceIn(0f, 1f)
                    translationY = -t * 26f
                }
            )
        }
    }
}

/**
 * **P-79 · LA CORRIENTE**: el barrido que cruza el tablero cuando se desliza una columna.
 *
 * Es el evento el que hace visible la mecánica, no los bloques: una fila atascada contra una roca no
 * mueve nada, y sin este barrido la corriente sería invisible justo en el turno en que más castiga.
 *
 * Dos intensidades con un solo dibujo, que es lo que las hace leerse como la misma cosa:
 *  - **Aviso** (`warning`): estelas finas y lentas, insinuadas. «Viene por aquí».
 *  - **Embestida**: estelas gruesas, rápidas y con cabeza brillante, a la vez que los `WoodFall` en
 *    modo `slide` arrastran los bloques. El barrido y el tablero se mueven al mismo tiempo.
 */
@Composable
fun BoardCurrentFlowVFX(
    event: GameEvent.BoardCurrentFlow,
    cellSize: Dp,
    onComplete: () -> Unit
) {
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val clock = remember { Animatable(0f) }
    LaunchedEffect(event.id) {
        clock.animateTo(1f, tween(event.durationMs, easing = LinearEasing))
        onComplete()
    }
    val warn = event.warning
    // (2026-09-22) Aquí se repartían las estelas por los carriles de LA CINTA, retirada. La noria que
    // la sustituye tiene su propio barrido alrededor del marco (`WheelFlowVFX`).
    val streaks = if (warn) 5 else 9
    val tint = if (warn) Color(0xFF7CC6FF) else Color(0xFF9FE4FF)

    Canvas(modifier = Modifier.fillMaxSize()) {
        val t = clock.value
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        // La estela nace fuera del borde de barlovento y sale por el de sotavento, así que el
        // recorrido siempre cruza el tablero entero sea cual sea el sentido.
        //
        // (2026-09-03) Y sea cual sea el EJE: con la corriente vertical el recorrido va de arriba
        // abajo y el carril es una columna. El dibujo es el mismo con las dos coordenadas
        // intercambiadas — «a lo largo» y «a lo ancho» en vez de x e y.
        val vertical = event.vertical
        val along = if (vertical) h else w            // por donde viaja la estela
        val across = if (vertical) w else h           // por donde se reparten los carriles
        val dir = if (event.direction >= 0) 1f else -1f
        val head = if (warn) 0.9f else 1.35f          // largo de la estela, en anchos de tablero
        for (i in 0 until streaks) {
            // Escalonado: cada estela arranca un poco después que la anterior y a distinta altura.
            val lane = (i + 0.5f) / streaks
            val delay = (i % 3) * 0.10f
            val p = ((t - delay) / (1f - delay).coerceAtLeast(0.001f)).coerceIn(0f, 1f)
            if (p <= 0f) continue
            val travel = -0.4f + p * 1.8f              // de fuera a fuera
            val cAlong = if (dir > 0f) travel * along else (1f - travel) * along
            val cAcross = lane * across
            val len = head * cellPx * (if (warn) 1.6f else 2.6f)
            val alpha = (kotlin.math.sin(p * kotlin.math.PI).toFloat()) * (if (warn) 0.32f else 0.62f)
            if (alpha <= 0.01f) continue
            val headPt = if (vertical) Offset(cAcross, cAlong) else Offset(cAlong, cAcross)
            val tail = if (vertical) Offset(cAcross, cAlong - dir * len)
                       else Offset(cAlong - dir * len, cAcross)
            drawLine(
                brush = Brush.linearGradient(
                    listOf(tint.copy(alpha = 0f), tint.copy(alpha = alpha)),
                    start = tail, end = headPt
                ),
                start = tail,
                end = headPt,
                strokeWidth = cellPx * (if (warn) 0.06f else 0.13f),
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
            if (!warn) {
                drawCircle(
                    color = Color.White.copy(alpha = alpha * 0.7f),
                    radius = cellPx * 0.07f,
                    center = headPt
                )
            }
        }
    }
}

/**
 * Cielo Estelar · **LA LUNA DEVUELVE LO QUE ROBA** (P-321, consecuencia): los bloques que se llevó caen
 * del cielo, ya sin estrella, con una estela plateada, y aterrizan al 70 % (el motor los pone en el
 * tablero en ese instante) con un anillo de plata.
 */
@Composable
fun MoonReturnVFX(event: GameEvent.MoonReturn, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }
    val silver = Color(0xFFD6E4FF)
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        event.cells.forEachIndexed { i, cell ->
            val dest = Offset((cell.x + 0.5f) * cellPx, (cell.y + 0.5f) * cellPx)
            // Cada bloque sale un poco después que el anterior; la caída acelera como un peso.
            val start = 0.08f * i
            val fall = ((p - start) / (0.70f - start).coerceAtLeast(0.1f)).coerceIn(0f, 1f)
            if (fall < 1f) {
                val from = Offset(dest.x, -cellPx)
                val c = from + (dest - from) * (fall * fall)
                val half = cellPx * 0.42f
                drawLine(silver, Offset(c.x, c.y - cellPx * 1.4f), c, cellPx * 0.18f, alpha = 0.35f * fall)
                drawRoundRect(
                    pieceColors[event.colorTypes.getOrElse(i) { 0 }.coerceIn(0, pieceColors.lastIndex)][0],
                    Offset(c.x - half, c.y - half),
                    androidx.compose.ui.geometry.Size(half * 2, half * 2),
                    androidx.compose.ui.geometry.CornerRadius(half * 0.3f),
                )
                drawCircle(silver, radius = half * 1.1f, center = c, alpha = 0.25f)
            } else {
                val land = ((p - 0.70f) / 0.30f).coerceIn(0f, 1f)
                drawCircle(silver, radius = cellPx * (0.35f + 0.55f * land), center = dest, alpha = 0.8f * (1f - land),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(cellPx * 0.07f))
            }
        }
    }
}

/**
 * Jardín · **LA ENREDADERA BROTA** (P-324): en cada casilla un tallo crece desde el lado del borde [edge],
 * echa dos hojas y, a la mitad (cuando el motor pone la enredadera en el tablero), suelta un estallido de
 * hojitas.
 */
@Composable
fun VineSproutVFX(event: GameEvent.VineSprout, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }
    val stem = Color(0xFF2E7D32)
    val leaf = Color(0xFF7CD65A)
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val grow = (p / 0.5f).coerceIn(0f, 1f)
        val burst = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
        val dir = when (event.edge) {
            com.cukiti.teikafusion.model.PlantEdge.TOP -> Offset(0f, 1f)
            com.cukiti.teikafusion.model.PlantEdge.BOTTOM -> Offset(0f, -1f)
            com.cukiti.teikafusion.model.PlantEdge.LEFT -> Offset(1f, 0f)
            com.cukiti.teikafusion.model.PlantEdge.RIGHT -> Offset(-1f, 0f)
        }
        event.cells.forEachIndexed { i, cell ->
            val c = Offset((cell.x + 0.5f) * cellPx, (cell.y + 0.5f) * cellPx)
            val start = c - dir * (cellPx * 0.5f)
            val tip = start + dir * (cellPx * 0.9f * grow)
            if (burst < 1f) {
                drawLine(stem, start, tip, cellPx * 0.09f, alpha = 1f - burst)
                val side = Offset(-dir.y, dir.x) * (cellPx * 0.22f * grow)
                val mid = start + (tip - start) * 0.55f
                drawCircle(leaf, radius = cellPx * 0.13f * grow, center = mid + side, alpha = 1f - burst)
                drawCircle(leaf, radius = cellPx * 0.13f * grow, center = mid - side, alpha = 1f - burst)
            }
            if (burst > 0f) {
                for (k in 0 until 5) {
                    val a = k * 1.2566f + i
                    val d = cellPx * (0.2f + 0.5f * burst)
                    drawCircle(leaf, radius = cellPx * 0.07f * (1f - burst),
                        center = c + Offset(kotlin.math.cos(a), kotlin.math.sin(a)) * d, alpha = 1f - burst)
                }
            }
        }
    }
}
