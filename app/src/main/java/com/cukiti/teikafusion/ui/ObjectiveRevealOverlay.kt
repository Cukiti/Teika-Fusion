package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.cukiti.teikafusion.ui.theme.NeonBlue
import com.cukiti.teikafusion.model.LevelObjective
import com.cukiti.teikafusion.model.TargetColor
import com.cukiti.teikafusion.model.TeikaType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val OBJECTIVE_DESTINATION_TIMEOUT_MS = 1_200L
private const val OBJECTIVE_DESTINATION_REDUCED_TIMEOUT_MS = 700L

// ── Recuadro del objetivo ────────────────────────────────────────────────────────
// La cinta entra LANZADA desde la derecha, frena en seco y aguanta quieta para que dé tiempo a leer
// el objetivo, y se va disparada por la izquierda en el mismo instante en que las estrellas salen
// volando al HUD: la salida ES el aviso de "empieza el nivel".
// P-58 (2026-09-04, números elegidos por el usuario): la ceremonia se acorta ~25 %. Es el camino que
// el jugador repite 293 veces, y lo que se recorta es sobre todo la ESPERA (620 → 380), no la
// entrada ni el frenazo: la cinta sigue llegando lanzada y parando en seco, que es lo que se lee
// como ceremonia. Lo que se pierde es el rato de más que la cinta pasaba posando.
internal const val OBJECTIVE_BANNER_ENTER_MS = 180
internal const val OBJECTIVE_BANNER_HOLD_MS = 380
internal const val OBJECTIVE_BANNER_EXIT_MS = 260

/**
 * Los vuelos arrancan cuando el recuadro se va: la espera del recuadro ES el tiempo de preparación.
 * Subirlo alarga el bloqueo del inicio de nivel (lo vigila `ObjectiveRevealMotionTest`).
 */
private const val OBJECTIVE_SETUP_MS = OBJECTIVE_BANNER_ENTER_MS + OBJECTIVE_BANNER_HOLD_MS
private const val OBJECTIVE_STAGGER_MS = 38
private const val OBJECTIVE_STAR_FLIGHT_MS = 420
// El MEGA sigue volando MÁS que una estrella (es el premio gordo y su vuelo lo dice): se recorta en
// la misma proporción que el resto, no hasta igualarlo. Lo fija `ObjectiveRevealMotionTest`.
private const val OBJECTIVE_MEGA_FLIGHT_MS = 455
private const val OBJECTIVE_SETTLE_MS = 120
private const val OBJECTIVE_REDUCED_HOLD_MS = 180L

/** Destino semántico compartido por el vuelo, el HUD y la háptica de aterrizaje. */
internal sealed interface ObjectiveRevealTarget {
    data class Star(val color: TargetColor) : ObjectiveRevealTarget
    data object Mega : ObjectiveRevealTarget
}

internal enum class ObjectiveRevealKind { RED, BLUE, PURPLE, PINK, MEGA }
internal enum class ObjectiveAuraStyle { COMET, RIPPLE, ORBIT, SPARKLE, GRAVITY }

/** Perfil de movimiento: todos comparten tempo, pero no personalidad ni silueta de trayectoria. */
internal data class ObjectiveMotionProfile(
    val kind: ObjectiveRevealKind,
    val arcLift: Float,
    val lateralWave: Float,
    val rotationDegrees: Float,
    val scaleBoost: Float,
    val auraStyle: ObjectiveAuraStyle,
    val accent: Color,
)

internal fun objectiveMotionProfile(target: ObjectiveRevealTarget): ObjectiveMotionProfile =
    when (target) {
        is ObjectiveRevealTarget.Star -> when (target.color) {
            TargetColor.RED -> ObjectiveMotionProfile(
                ObjectiveRevealKind.RED, 0.46f, 0.05f, 24f, 0.07f,
                ObjectiveAuraStyle.COMET, Color(0xFFFF5A5A)
            )
            TargetColor.BLUE -> ObjectiveMotionProfile(
                ObjectiveRevealKind.BLUE, 1.10f, -0.08f, -18f, 0.04f,
                ObjectiveAuraStyle.RIPPLE, Color(0xFF4DB8FF)
            )
            TargetColor.PURPLE -> ObjectiveMotionProfile(
                ObjectiveRevealKind.PURPLE, 0.82f, 0.20f, 42f, 0.09f,
                ObjectiveAuraStyle.ORBIT, Color(0xFFB57EDC)
            )
            TargetColor.PINK -> ObjectiveMotionProfile(
                ObjectiveRevealKind.PINK, 0.68f, -0.22f, -32f, 0.12f,
                ObjectiveAuraStyle.SPARKLE, Color(0xFFFF8FD0)
            )
        }
        ObjectiveRevealTarget.Mega -> ObjectiveMotionProfile(
            ObjectiveRevealKind.MEGA, 0.34f, 0f, 0f, 0.02f,
            ObjectiveAuraStyle.GRAVITY, Color(0xFFFFD54F)
        )
    }

internal data class ObjectiveFlightWindow(val startMs: Int, val durationMs: Int) {
    val landingMs: Int get() = startMs + durationMs
}

internal fun objectiveFlightWindow(
    index: Int,
    target: ObjectiveRevealTarget,
): ObjectiveFlightWindow = ObjectiveFlightWindow(
    startMs = OBJECTIVE_SETUP_MS + index.coerceAtLeast(0) * OBJECTIVE_STAGGER_MS,
    durationMs = if (target == ObjectiveRevealTarget.Mega) {
        OBJECTIVE_MEGA_FLIGHT_MS
    } else {
        OBJECTIVE_STAR_FLIGHT_MS
    },
)

internal fun objectiveRevealDurationMs(targets: List<ObjectiveRevealTarget>): Int =
    (targets.mapIndexed { index, target -> objectiveFlightWindow(index, target).landingMs }
        .maxOrNull() ?: OBJECTIVE_SETUP_MS) + OBJECTIVE_SETTLE_MS

private data class ObjectiveRevealItem(
    val target: ObjectiveRevealTarget,
    val amount: Int,
)

private val objectiveFlightEasing = CubicBezierEasing(0.05f, 0.72f, 0.12f, 1f)

// Entrada: casi toda la distancia en el primer tercio del tiempo y un frenazo largo al final —
// esa cola es la que se lee como cámara lenta. Salida: al revés, arranca lento y se dispara.
private val objectiveBannerEnterEasing = CubicBezierEasing(0.08f, 0.86f, 0.12f, 1f)
private val objectiveBannerExitEasing = CubicBezierEasing(0.66f, 0f, 0.9f, 0.2f)

/**
 * Estado del recuadro en un instante del reloj. Todo en unidades relativas para que no dependa de
 * la pantalla: [travel] va en anchos de cinta (+1 = fuera por la derecha, −1 = fuera por la
 * izquierda), y el resto son factores 0..1.
 */
internal data class ObjectiveBannerMotion(
    val travel: Float,
    val alpha: Float,
    /** Estelas de velocidad: 1 mientras va lanzada, 0 cuando está parada. */
    val streak: Float,
    /** Recorrido de la luz que baña el filo mientras el recuadro espera (0..1, una sola pasada). */
    val edgeLight: Float,
    /** Estirado horizontal por velocidad: deforma la cinta al entrar y sobre todo al salir. */
    val stretch: Float,
)

/** Movimiento del recuadro en `timeMs` del mismo reloj que gobierna los vuelos. */
internal fun objectiveBannerMotion(timeMs: Float): ObjectiveBannerMotion = when {
    timeMs < OBJECTIVE_BANNER_ENTER_MS -> {
        val eased = objectiveBannerEnterEasing
            .transform((timeMs / OBJECTIVE_BANNER_ENTER_MS).coerceIn(0f, 1f))
        ObjectiveBannerMotion(
            travel = 1f - eased,
            alpha = (timeMs / 70f).coerceIn(0f, 1f),
            streak = 1f - eased,
            edgeLight = 0f,
            stretch = (1f - eased) * 0.22f,
        )
    }
    timeMs < OBJECTIVE_SETUP_MS -> {
        val held = timeMs - OBJECTIVE_BANNER_ENTER_MS
        // Al frenar, las estelas se COMPRIMEN contra el filo y se apagan; la luz toma el relevo.
        val settle = (held / 140f).coerceIn(0f, 1f)
        ObjectiveBannerMotion(
            travel = 0f,
            alpha = 1f,
            streak = (1f - settle) * 0.55f,
            edgeLight = (held / OBJECTIVE_BANNER_HOLD_MS).coerceIn(0f, 1f),
            stretch = 0f,
        )
    }
    else -> {
        val p = ((timeMs - OBJECTIVE_SETUP_MS) / OBJECTIVE_BANNER_EXIT_MS).coerceIn(0f, 1f)
        val eased = objectiveBannerExitEasing.transform(p)
        // Anticipación: la cinta retrocede un pelo a la derecha antes del latigazo a la izquierda.
        val anticipation = if (p < 0.22f) sin(p / 0.22f * PI.toFloat()) * 0.04f else 0f
        ObjectiveBannerMotion(
            travel = anticipation - eased * 1.3f,
            alpha = 1f - ((p - 0.7f) / 0.3f).coerceIn(0f, 1f),
            streak = eased,
            edgeLight = 1f,
            stretch = eased * 0.4f,
        )
    }
}

/** Los iconos aparecen sobre el recuadro YA frenado, nunca antes: primero llega la cinta. */
internal fun objectiveContentEnter(timeMs: Float): Float =
    ((timeMs - OBJECTIVE_BANNER_ENTER_MS) / 200f).coerceIn(0f, 1f)

/**
 * Presentación automática del objetivo. Un solo reloj coordina entrada, vuelos, entregas al HUD y
 * salida. El reloj se lee exclusivamente desde layout/dibujo/capas gráficas: durante el vuelo no
 * se recompone ni se vuelve a medir el árbol completo.
 */
@Composable
internal fun ObjectiveRevealOverlay(
    objective: LevelObjective,
    megaRequired: Boolean,
    targetRects: Map<TargetColor, Rect>,
    megaRect: Rect,
    rootOffset: Offset,
    reducedFx: Boolean,
    onItemLanded: (ObjectiveRevealTarget) -> Unit,
    onFinished: () -> Unit,
) {
    val items = remember(objective.required, megaRequired) {
        buildList {
            objective.required.forEach { (color, amount) ->
                add(ObjectiveRevealItem(ObjectiveRevealTarget.Star(color), amount))
            }
            if (megaRequired) add(ObjectiveRevealItem(ObjectiveRevealTarget.Mega, 1))
        }
    }
    val destinationsReady = items.isNotEmpty() && items.all { item ->
        val rect = when (val target = item.target) {
            is ObjectiveRevealTarget.Star -> targetRects[target.color]
            ObjectiveRevealTarget.Mega -> megaRect
        }
        rect != null && rect.width > 0f && rect.height > 0f
    }
    val durationMs = remember(items) { objectiveRevealDurationMs(items.map { it.target }) }
    val timelineMs = remember(items) { Animatable(0f) }
    val latestFinished by rememberUpdatedState(onFinished)
    val latestItemLanded by rememberUpdatedState(onItemLanded)
    val latestDestinationsReady by rememberUpdatedState(destinationsReady)
    val presentationFinished = remember(items) { mutableStateOf(false) }
    val finishPresentation = {
        if (!presentationFinished.value) {
            presentationFinished.value = true
            latestFinished()
        }
    }

    // Una medición rota nunca puede dejar una capa invisible secuestrando los toques. onFinished
    // hace visible el HUD completo antes de que el ViewModel desbloquee la partida.
    LaunchedEffect(items, reducedFx) {
        delay(
            if (reducedFx) OBJECTIVE_DESTINATION_REDUCED_TIMEOUT_MS
            else OBJECTIVE_DESTINATION_TIMEOUT_MS
        )
        if (!latestDestinationsReady) finishPresentation()
    }

    LaunchedEffect(items, destinationsReady, reducedFx) {
        if (!destinationsReady) return@LaunchedEffect
        if (reducedFx) {
            // Accesibilidad: lectura breve sin desplazamiento espacial ni vibraciones encadenadas.
            timelineMs.snapTo(OBJECTIVE_SETUP_MS.toFloat())
            delay(OBJECTIVE_REDUCED_HOLD_MS)
            items.forEach { latestItemLanded(it.target) }
            timelineMs.snapTo(durationMs.toFloat())
        } else {
            coroutineScope {
                val landings = items.mapIndexed { index, item ->
                    launch {
                        delay(objectiveFlightWindow(index, item.target).landingMs.toLong())
                        latestItemLanded(item.target)
                    }
                }
                val master = launch {
                    timelineMs.animateTo(
                        durationMs.toFloat(),
                        tween(durationMillis = durationMs, easing = LinearEasing)
                    )
                }
                (landings + master).joinAll()
            }
        }
        finishPresentation()
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawWithCache {
                onDrawBehind {
                    val t = timelineMs.value
                    // El oscurecido entra CON la cinta y se retira con ella (no con los vuelos):
                    // cuando el recuadro se va, el tablero ya se ve limpio para jugar.
                    val enter = LinearOutSlowInEasing
                        .transform((t / OBJECTIVE_BANNER_ENTER_MS).coerceIn(0f, 1f))
                    val exit = ((t - OBJECTIVE_SETUP_MS) / OBJECTIVE_BANNER_EXIT_MS.toFloat())
                        .coerceIn(0f, 1f)
                    drawRect(Color.Black.copy(alpha = 0.62f * enter * (1f - exit)))
                }
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val iconSize = if (items.size >= 4) 56.dp else 72.dp
        val iconPx = with(density) { iconSize.toPx() }
        val maxStep = with(density) { 82.dp.toPx() }
        val stepPx = min(maxStep, widthPx / (items.size + 0.45f))
        val startY = heightPx * 0.42f
        val maxLift = with(density) { 96.dp.toPx() }

        // El recuadro va DETRÁS de los iconos (se compone antes) y los envuelve: el rótulo viaja
        // con la cinta, las estrellas se quedan y luego vuelan al HUD.
        val bannerTitleBlock = 34.dp
        val bannerPadding = 18.dp
        ObjectiveBanner(
            timelineMs = timelineMs,
            width = maxWidth,
            height = bannerTitleBlock + iconSize + 30.dp + bannerPadding * 2,
            titleTopPadding = bannerPadding,
            topPx = startY - iconPx / 2f -
                with(density) { (bannerTitleBlock + bannerPadding).toPx() },
            reducedFx = reducedFx,
        )

        items.forEachIndexed { index, item ->
            val destinationRect = when (val target = item.target) {
                is ObjectiveRevealTarget.Star -> targetRects[target.color]
                ObjectiveRevealTarget.Mega -> megaRect
            }
            if (destinationRect == null || destinationRect.width <= 0f || destinationRect.height <= 0f) {
                return@forEachIndexed
            }
            val profile = objectiveMotionProfile(item.target)
            val window = objectiveFlightWindow(index, item.target)
            val start = Offset(
                x = widthPx / 2f + (index - items.lastIndex / 2f) * stepPx,
                y = startY,
            )
            val end = destinationRect.center - rootOffset
            val dx = end.x - start.x
            val dy = end.y - start.y
            val distance = hypot(dx, dy).coerceAtLeast(1f)
            val baseLift = min(maxLift, (abs(dx) + abs(dy)) * 0.18f)
            val perpendicular = Offset(-dy / distance, dx / distance)
            val targetScale = (destinationRect.width / iconPx).coerceIn(0.48f, 0.92f)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset {
                        val raw = ((timelineMs.value - window.startMs) / window.durationMs)
                            .coerceIn(0f, 1f)
                        val flight = objectiveFlightEasing.transform(raw)
                        val arch = sin(flight * PI.toFloat())
                        val lateral = perpendicular * (arch * baseLift * profile.lateralWave)
                        val x = lerp(start.x, end.x, flight) + lateral.x
                        val y = lerp(start.y, end.y, flight) -
                            arch * baseLift * profile.arcLift + lateral.y
                        IntOffset(
                            (x - iconPx / 2f).roundToInt(),
                            (y - iconPx / 2f).roundToInt()
                        )
                    }
                    .width(iconSize)
                    .height(iconSize + 30.dp)
                    .graphicsLayer {
                        val t = timelineMs.value
                        val enter = LinearOutSlowInEasing.transform(objectiveContentEnter(t))
                        val raw = ((t - window.startMs) / window.durationMs).coerceIn(0f, 1f)
                        val flight = objectiveFlightEasing.transform(raw)
                        val handoff = ((raw - 0.87f) / 0.13f).coerceIn(0f, 1f)
                        val anticipation = sin(raw * PI.toFloat()) * profile.scaleBoost
                        val scale = lerp(0.52f, 1f, enter) *
                            lerp(1f + anticipation, targetScale, flight)
                        alpha = enter * (1f - handoff)
                        scaleX = scale
                        scaleY = scale
                        rotationZ = sin(flight * PI.toFloat()) * profile.rotationDegrees
                    }
            ) {
                Box(modifier = Modifier.size(iconSize), contentAlignment = Alignment.Center) {
                    ObjectiveAura(
                        profile = profile,
                        window = window,
                        timelineMs = timelineMs,
                        modifier = Modifier.matchParentSize()
                    )
                    when (val target = item.target) {
                        is ObjectiveRevealTarget.Star -> TargetStar(
                            targetColor = target.color,
                            modifier = Modifier.matchParentSize()
                        )
                        ObjectiveRevealTarget.Mega -> TeikaIcon(
                            type = TeikaType.MEGA_TEIKA,
                            modifier = Modifier.matchParentSize()
                        )
                    }
                }
                Text(
                    text = item.amount.toString(),
                    color = Color.White,
                    fontSize = if (items.size >= 4) 20.sp else 24.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
    }
}

/**
 * Cinta a sangre que presenta el objetivo, DETRÁS de las estrellas. Entra lanzada desde la derecha,
 * frena en seco —las estelas se comprimen contra el filo y una luz lo recorre mientras espera— y se
 * va disparada por la izquierda en el mismo instante en que las estrellas vuelan al HUD.
 *
 * Todo lo que cambia por frame se lee en LAYOUT (`offset`), CAPA (`graphicsLayer`) o DIBUJO
 * (`onDrawBehind`): la cinta no recompone ni una sola vez mientras se mueve.
 */
@Composable
private fun ObjectiveBanner(
    timelineMs: Animatable<Float, *>,
    width: Dp,
    height: Dp,
    titleTopPadding: Dp,
    topPx: Float,
    reducedFx: Boolean,
) {
    val widthPx = with(LocalDensity.current) { width.toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (objectiveBannerMotion(timelineMs.value).travel * widthPx).roundToInt(),
                    topPx.roundToInt(),
                )
            }
            .width(width)
            .height(height)
            .graphicsLayer {
                val motion = objectiveBannerMotion(timelineMs.value)
                alpha = motion.alpha
                scaleX = 1f + motion.stretch
            }
            .drawWithCache {
                // El cuerpo es fijo → un solo Brush cacheado. Por frame solo se crean los dos
                // degradados cortos (estela y luz del filo), y únicamente mientras se ven.
                val body = Brush.verticalGradient(listOf(Color(0xFF6E80D8), Color(0xFF5566B4)))
                val edge = 2.dp.toPx()
                onDrawBehind {
                    val motion = objectiveBannerMotion(timelineMs.value)
                    drawRect(body)
                    drawRect(NeonBlue.copy(alpha = 0.55f), size = Size(size.width, edge))
                    drawRect(
                        NeonBlue.copy(alpha = 0.55f),
                        topLeft = Offset(0f, size.height - edge),
                        size = Size(size.width, edge),
                    )
                    if (reducedFx) return@onDrawBehind

                    // Estelas: cinco rayas ancladas al filo DERECHO (la cinta siempre viaja hacia la
                    // izquierda), largas mientras vuela y comprimidas a nada cuando frena.
                    if (motion.streak > 0.01f) {
                        val trail = size.width * 0.55f * motion.streak
                        val streakBrush = Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.18f)),
                            startX = size.width - trail,
                            endX = size.width,
                        )
                        for (i in 0 until 5) {
                            val y = size.height * (0.16f + i * 0.17f)
                            drawRect(
                                streakBrush,
                                topLeft = Offset(size.width - trail, y),
                                size = Size(trail, edge * (1.4f - i * 0.15f)),
                            )
                        }
                    }

                    // Luz del filo: una sola pasada mientras la cinta está quieta. Va de ida por
                    // arriba y de vuelta por abajo, para que el parón se sienta vivo y no congelado.
                    if (motion.edgeLight > 0f && motion.edgeLight < 1f) {
                        val seg = size.width * 0.22f
                        val x = motion.edgeLight * (size.width + seg) - seg
                        val glow = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.8f),
                            Color.Transparent,
                        )
                        drawRect(
                            Brush.horizontalGradient(glow, startX = x, endX = x + seg),
                            size = Size(size.width, edge),
                        )
                        val back = size.width - x - seg
                        drawRect(
                            Brush.horizontalGradient(glow, startX = back, endX = back + seg),
                            topLeft = Offset(0f, size.height - edge),
                            size = Size(size.width, edge),
                        )
                    }
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = "OBJETIVO",
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = titleTopPadding),
        )
    }
}

/** Cinco firmas visuales dibujadas en un Canvas barato; ninguna crea partículas ni corrutinas. */
@Composable
private fun ObjectiveAura(
    profile: ObjectiveMotionProfile,
    window: ObjectiveFlightWindow,
    timelineMs: Animatable<Float, *>,
    modifier: Modifier,
) {
    Canvas(modifier = modifier) {
        val raw = ((timelineMs.value - window.startMs) / window.durationMs).coerceIn(0f, 1f)
        if (raw <= 0f || raw >= 1f) return@Canvas
        val pulse = sin(raw * PI.toFloat()).coerceAtLeast(0f)
        val base = size.minDimension
        val c = center
        when (profile.auraStyle) {
            ObjectiveAuraStyle.COMET -> {
                repeat(3) { i ->
                    drawCircle(
                        profile.accent.copy(alpha = pulse * (0.34f - i * 0.08f)),
                        radius = base * (0.10f - i * 0.015f),
                        center = Offset(c.x - base * (0.28f + i * 0.16f), c.y + base * 0.10f)
                    )
                }
            }
            ObjectiveAuraStyle.RIPPLE -> repeat(2) { i ->
                drawCircle(
                    profile.accent.copy(alpha = pulse * (0.34f - i * 0.10f)),
                    radius = base * (0.48f + i * 0.14f + raw * 0.08f),
                    center = c,
                    style = Stroke(width = base * 0.035f)
                )
            }
            ObjectiveAuraStyle.ORBIT -> {
                val angle = raw * PI.toFloat() * 3f
                repeat(2) { i ->
                    val a = angle + i * PI.toFloat()
                    drawCircle(
                        profile.accent.copy(alpha = 0.60f * pulse),
                        radius = base * 0.07f,
                        center = Offset(c.x + cos(a) * base * 0.54f, c.y + sin(a) * base * 0.30f)
                    )
                }
            }
            ObjectiveAuraStyle.SPARKLE -> repeat(4) { i ->
                val a = i * PI.toFloat() / 2f + raw * PI.toFloat()
                drawCircle(
                    Color.White.copy(alpha = 0.72f * pulse),
                    radius = base * 0.045f,
                    center = Offset(c.x + cos(a) * base * 0.55f, c.y + sin(a) * base * 0.55f)
                )
            }
            ObjectiveAuraStyle.GRAVITY -> repeat(2) { i ->
                drawCircle(
                    profile.accent.copy(alpha = pulse * (0.30f - i * 0.09f)),
                    radius = base * (0.55f + i * 0.15f - raw * 0.12f),
                    center = c,
                    style = Stroke(width = base * (0.055f - i * 0.012f))
                )
            }
        }
    }
}
