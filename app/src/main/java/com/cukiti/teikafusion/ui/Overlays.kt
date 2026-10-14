package com.cukiti.teikafusion.ui

import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.zIndex
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import android.os.Build
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.geometry.Rect
import com.cukiti.teikafusion.BillingManager
import com.cukiti.teikafusion.model.TeikaType
import com.cukiti.teikafusion.model.Piece
import com.cukiti.teikafusion.ui.theme.DarkSurface
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cukiti.teikafusion.R
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.cukiti.teikafusion.model.LevelObjective
import com.cukiti.teikafusion.model.TargetColor


// (RETIRADO 2026-08-09) Aquí vivía `ConfettiParticle`, la lluvia de 52 cuadraditos de
// [LevelCompleteOverlay]. Era un `Canvas` a pantalla completa redibujándose a 60 fps **en bucle
// infinito** mientras la ventana estuviera abierta —y la ventana no se cierra sola—, encima de una
// pantalla que además sigue montando el tablero. Coste permanente por un adorno de fondo: fuera.

@Composable
fun LevelCompleteOverlay(
    level: Int,
    score: Int,
    highScore: Int,
    /** Turnos sin gastar al ganar: la 2ª estrella se paga con ellos (ver [LevelLayouts.starsFor]). */
    movesLeft: Int,
    movesTotal: Int,
    companionId: String? = null,
    companionLevel: Int = 1,
    consecutiveWins: Int = 0,
    onNextLevel: () -> Unit,
    onGoHome: () -> Unit,
    crystalsEarned: Int = 0,
    // Tic sonoro del count-up (0→1 = progreso del conteo): tono ascendente, ver playScoreTick.
    onScoreTick: (Float) -> Unit = {},
    /** P-20: teikoins que un anuncio puede DUPLICAR (0 = sin oferta). */
    doubleCoins: Int = 0,
    /** null = no hay anuncio disponible ahora mismo → la oferta no se pinta. */
    onDoubleCoins: (() -> Unit)? = null,
    reducedFx: Boolean = false,
) {
    // Entrada contenida: el panel aparece con presencia, sin el rebote exagerado de una ventana
    // casi a pantalla completa.
    val cardScale      = remember { Animatable(0.92f) }
    val star1Scale     = remember { Animatable(0f) }
    val star2Scale     = remember { Animatable(0f) }
    val star3Scale     = remember { Animatable(0f) }
    val displayedScore = remember { mutableIntStateOf(0) }
    val winsScale      = remember { Animatable(0f) }   // pop del banner "Victorias seguidas"

    val starsEarned = com.cukiti.teikafusion.model.LevelLayouts
        .starsFor(level, score, movesLeft, movesTotal, companionId, companionLevel)

    LaunchedEffect(Unit) {
        launch { cardScale.animateTo(1f, tween(360, easing = FastOutSlowInEasing)) }
        // Una victoria sola no es una racha (auditoría 2026-10-01): el chip sale desde la 2.ª seguida.
        if (consecutiveWins >= 2) launch {
            delay(280)
            winsScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
        launch {
            delay(350)
            val steps = 40
            for (i in 1..steps) {
                displayedScore.intValue = score * i / steps
                // Tic ascendente cada 4 pasos (~10 tics en todo el conteo): acompaña sin metralleta.
                if (score > 0 && i % 4 == 0) onScoreTick(i / steps.toFloat())
                delay(20)
            }
            displayedScore.intValue = score
        }
        delay(550)
        star1Scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        if (starsEarned >= 2) {
            delay(200)
            star2Scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
        if (starsEarned >= 3) {
            delay(200)
            star3Scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center
    ) {
        // ── Card ──────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center
        ) {
        val screenH = LocalConfiguration.current.screenHeightDp.dp
        Box(
            modifier = Modifier
                .scale(cardScale.value)
                .fillMaxWidth(0.86f)
                .heightIn(max = screenH * 0.76f)
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFFFE082), Color(0xFFFF8C00), Color(0xFFFFD700))
                    ),
                    shape = RoundedCornerShape(28.dp)
                )
                .padding(2.5.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF161B33), Color(0xFF222A52))
                        )
                    )
            ) {
                // ── CABECERA (fija): título + racha ──────────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(top = 14.dp, bottom = 4.dp)
                ) {
                    Text(
                        // P-92: el número que se ENSEÑA descuenta las siete lecciones. Sin esto, el
                        // primer nivel de verdad de un jugador nuevo se anunciaba como el octavo.
                        text = "NIVEL ${com.cukiti.teikafusion.model.LevelMap.displayNumber(level)} COMPLETADO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFD700),
                        letterSpacing = 1.4.sp,
                        textAlign = TextAlign.Center
                    )

                    // ── Victorias consecutivas (gancho "un nivel más", estilo Block Blast) ──
                    if (consecutiveWins >= 2) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .scale(winsScale.value)
                                .background(
                                    brush = Brush.horizontalGradient(listOf(Color(0xFFFF6E40), Color(0xFFFFB300))),
                                    shape = RoundedCornerShape(50)
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            RawImage(R.raw.ic_streak, Modifier.size(16.dp))
                            Text(
                                text = "VICTORIAS SEGUIDAS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White,
                                letterSpacing = 0.5.sp
                            )
                            Text(
                                text = "×$consecutiveWins",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }
                }

                // ── SECCIÓN CENTRAL: el pie queda fijo y sólo esta zona puede desplazarse ──
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp)
                        .padding(top = 4.dp, bottom = 2.dp)
                ) {
                // Stars row
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VictoryStar(animScale = star1Scale.value, earned = starsEarned >= 1, sizeSp = 30)
                    Spacer(modifier = Modifier.width(4.dp))
                    VictoryStar(animScale = star2Scale.value, earned = starsEarned >= 2, sizeSp = 40)
                    Spacer(modifier = Modifier.width(4.dp))
                    VictoryStar(animScale = star3Scale.value, earned = starsEarned >= 3, sizeSp = 30)
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ── Panel de puntuación (inset premium) ──────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF0E1226), Color(0xFF10152B))
                            )
                        )
                        .border(
                            1.dp,
                            Brush.horizontalGradient(
                                listOf(Color(0x33FFD700), Color(0x22FFD700), Color(0x33FFD700))
                            ),
                            RoundedCornerShape(14.dp)
                        )
                        .padding(vertical = 7.dp)
                ) {
                    Text(
                        text = "PUNTUACIÓN",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.50f),
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${displayedScore.intValue}",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        RawImage(R.raw.ic_trophy, Modifier.size(14.dp))
                        Text(
                            text = "MÁX  $highScore",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFFFD700).copy(alpha = 0.80f),
                            letterSpacing = 1.sp
                        )
                    }
                }

                // (RETIRADO 2026-09-05, pedido del usuario) Aquí vivía «TEIKAS FORMADOS»: cuatro
                //  fichas con el recuento de T1…T4 del nivel. Se va al entrar EL TRATO del nivel
                //  siguiente (P-195) y el criterio para elegir qué sobraba es el mismo que ya retiró
                //  la tira del mural en 2026-08-01: **esta ventana se está quedando sin sitio, así
                //  que gana lo que cambia la partida SIGUIENTE sobre lo que solo cuenta la anterior**.
                //  Las cartas del trato deciden cómo juegas el nivel que viene; el recuento de Teikas
                //  no se puede hacer nada con él — no se compara con nada, no entra en la puntuación
                //  y las estrellas, que sí son el premio, ya están arriba. Con él se fue
                //  `PremiumStatItem`, que no lo usaba nadie más.

                // (RETIRADO 2026-08-01, pedido del usuario) Aquí vivía `MuralProgressStrip(level)`,
                //  la tira de progreso del mural del bioma. La ventana de nivel completado ya cuenta
                //  lo que el jugador acaba de hacer —estrellas, puntuación y cristales; los Teikas
                //  se fueron el 2026-09-05, ver arriba— y el
                //  mural se ve entero en el mapa, que es su sitio. El componente sigue vivo en
                //  `BiomeAdventureMap.kt` para el mapa.

                // ── Cristales mágicos recolectados ────────────────────────────
                if (crystalsEarned > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "CRISTALES GANADOS",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.45f),
                        letterSpacing = 2.sp
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CrystalGem(size = 30.dp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "+$crystalsEarned",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                        }
                    }
                }

                } // ── fin SECCIÓN CENTRAL ──

                // ── PIE (fijo): botones SIEMPRE visibles ─────────────────────
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(top = 8.dp, bottom = 12.dp)
                ) {
                    // ── DOBLAR LA RECOMPENSA CON UN ANUNCIO (P-20) ───────────────────────────
                    // Va ANTES del botón de seguir y solo existe si de verdad hay algo que doblar
                    // y un anuncio cargado. Es el placement que mejor convierte del género porque
                    // llega en el momento de alegría — no como el revive, que llega enfadado. Si se
                    // ignora, no pasa nada: el jugador ya cobró su recompensa completa.
                    if (doubleCoins > 0 && onDoubleCoins != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(13.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF5B42B5), Color(0xFF382476))
                                    )
                                )
                                .border(
                                    1.5.dp,
                                    Color(0xFFB39DFF).copy(alpha = 0.65f),
                                    RoundedCornerShape(13.dp)
                                )
                                .clickable(role = Role.Button) { onDoubleCoins() }
                                .semantics {
                                    contentDescription =
                                        "Ver un anuncio para duplicar la recompensa: $doubleCoins Teikoins más"
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RawImage(R.raw.ic_play, Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "VER ANUNCIO",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "·  +$doubleCoins TEIKOINS",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFFD54F)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    // (RETIRADO 2026-09-23) «Elige tu trato»: las cartas de El Trato del nivel siguiente.
                    // Botón principal premium: relieve dorado + brillo animado.
                    val btnShine = rememberInfiniteTransition(label = "nextBtnShine")
                    val shineX by btnShine.animateFloat(
                        initialValue = -1f, targetValue = 2f,
                        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing)),
                        label = "shineX"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFFFFE082), Color(0xFFFFC107), Color(0xFFFF9800))
                                )
                            )
                            .border(
                                1.5.dp,
                                Brush.verticalGradient(listOf(Color(0xFFFFF3C4), Color(0xFFE68A00))),
                                    RoundedCornerShape(13.dp)
                            )
                            .clickable { onNextLevel() },
                        contentAlignment = Alignment.Center
                    ) {
                        // Barrido de brillo diagonal.
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                                        start = Offset(shineX * 400f, 0f),
                                        end   = Offset(shineX * 400f + 140f, 140f)
                                    )
                                )
                        )
                        Text(
                            text = "SIGUIENTE NIVEL  →",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF3A2600),
                            letterSpacing = 0.5.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    TextButton(
                        onClick = onGoHome,
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text = "Ir al Inicio",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun VictoryStar(animScale: Float, earned: Boolean, sizeSp: Int) {
    if (earned) {
        RawImage(
            resId = R.raw.ic_star,
            modifier = Modifier.size(sizeSp.dp).scale(animScale)
        )
    } else {
        Text(text = "☆", fontSize = sizeSp.sp, color = Color.White.copy(alpha = 0.22f))
    }
}

@Composable
private fun GoldCoinRain(modifier: Modifier = Modifier) {
    data class Coin(val x: Float, val seed: Float, val speed: Float, val radius: Float)
    val coins = remember {
        List(22) { i ->
            Coin(
                x      = (i * 47 % 100) / 100f,
                seed   = (i * 137 % 100) / 100f,
                speed  = 0.6f + (i * 83 % 40) / 100f,
                radius = 5f + (i * 61 % 6)
            )
        }
    }
    val rainTransition = rememberInfiniteTransition(label = "coinRain")
    val rainProgress by rainTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "rain"
    )
    Canvas(modifier = modifier.fillMaxSize()) {
        coins.forEach { coin ->
            val yFraction = ((rainProgress * coin.speed + coin.seed) % 1f)
            val y = yFraction * size.height
            val cx = coin.x * size.width
            val alpha = if (yFraction > 0.85f) (1f - yFraction) / 0.15f else 1f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFD700), Color(0xFFFF8C00)),
                    center = Offset(cx, y)
                ),
                radius = coin.radius,
                center = Offset(cx, y),
                alpha = alpha * 0.85f
            )
            drawCircle(
                color = Color(0xFFFFFFAA).copy(alpha = 0.5f * alpha),
                radius = coin.radius * 0.4f,
                center = Offset(cx - coin.radius * 0.25f, y - coin.radius * 0.25f)
            )
        }
    }
}

/* (ERRADICADO 2026-08-09, con el modo) `InfiniteGameOverOverlay`: el fin de partida de WALLBLOCK,
 * con su hito de profundidad, su causa de muerte y su CTA «OTRA». Con él debió irse
 * `TeikaType.displayName()` —el pie de foto de esa ventana—, que se quedó vivo y sin un solo
 * llamante hasta el 2026-09-05. */

@Composable
fun GameOverOverlay(
    score: Int,
    highScore: Int,
    isNewRecord: Boolean,
    onRestart: () -> Unit,
    onGoHome: () -> Unit,
    onShare: () -> Unit = {},
    onViewLeaderboard: () -> Unit = {},
    // Tic sonoro del count-up (0→1 = progreso del conteo): tono ascendente, ver playScoreTick.
    onScoreTick: (Float) -> Unit = {}
) {
    // P-295 (2026-09-25, playtest): esta era la última ventana con el estilo viejo —superficie de
    // Material, botones de sistema y un Teika suelto—. Ahora usa el marco de las ventanas de juego
    // (`GameDialogFrame`, el mismo de revivir y confirmar): cinta con el título, el Teika dentro de un
    // anillo que se tambalea, la puntuación contando en una chapa y botones de arcade.
    val displayedScore = remember { mutableIntStateOf(0) }
    val recordBadgeScale = remember { Animatable(0f) }
    val reduced = LocalReducedFx.current
    val wobble = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Count-up con tic ascendente cada 3 pasos (~10 tics, ver playScoreTick).
        val steps = 30
        for (i in 1..steps) {
            displayedScore.intValue = (score * i / steps)
            if (score > 0 && i % 3 == 0) onScoreTick(i / steps.toFloat())
            kotlinx.coroutines.delay(16)
        }
        displayedScore.intValue = score
        if (isNewRecord) {
            recordBadgeScale.animateTo(1.3f, tween(200))
            recordBadgeScale.animateTo(1f, tween(100))
        }
    }
    LaunchedEffect(reduced) {
        if (reduced) return@LaunchedEffect
        // El Teika se tambalea: casi se cae, y se recompone. Tres vaivenes y quieto.
        repeat(3) {
            wobble.animateTo(1f, tween(260))
            wobble.animateTo(-1f, tween(260))
        }
        wobble.animateTo(0f, tween(220))
    }

    val accent = Color(0xFFFF7A59)
    val accentDeep = Color(0xFF9E2A1E)
    Box(Modifier.fillMaxSize()) {
        GameDialogFrame(
            title = "¡ESTUVO CERCA!",
            accent = accent,
            accentDeep = accentDeep,
            onOutsideTap = null,
            iconSize = 104.dp,
            icon = {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Color(0xFF12162E), radius = size.minDimension / 2f, alpha = 0.78f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.45f), Color.Transparent),
                            center = center, radius = size.minDimension / 2f
                        ),
                        radius = size.minDimension / 2f
                    )
                    drawCircle(
                        accent, radius = size.minDimension / 2f - size.minDimension * 0.04f,
                        style = Stroke(size.minDimension * 0.06f)
                    )
                }
                TeikaIcon(
                    type = TeikaType.TEIKA_1,
                    modifier = Modifier
                        .size(66.dp)
                        .graphicsLayer {
                            rotationZ = 14f * wobble.value
                            translationX = 4.dp.toPx() * wobble.value
                        }
                )
            },
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                // Sale con cualquier derrota (sin sitio o la lava): no nombra la causa.
                "Te faltó muy poco. ¡La próxima es tuya!",
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.86f)
            )
            Spacer(Modifier.height(14.dp))
            // La puntuación en su chapa, con el récord debajo.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF232A55), Color(0xFF151A36))))
                    .border(
                        1.5.dp,
                        (if (isNewRecord) Color(0xFFFFD700) else accent).copy(alpha = 0.55f),
                        RoundedCornerShape(18.dp)
                    )
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "PUNTUACIÓN",
                    fontSize = 11.sp, fontWeight = FontWeight.Black, letterSpacing = 1.6.sp,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Text(
                    "${displayedScore.intValue}",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Black,
                    color = if (isNewRecord) Color(0xFFFFD700) else Color.White
                )
                if (isNewRecord && recordBadgeScale.value > 0f) {
                    Box(
                        modifier = Modifier
                            .scale(recordBadgeScale.value)
                            .background(
                                brush = Brush.horizontalGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8C00))),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "NUEVO RÉCORD", fontSize = 12.sp, fontWeight = FontWeight.Black,
                            color = Color(0xFF3A2400), letterSpacing = 1.sp
                        )
                    }
                } else {
                    Text(
                        "Mejor: $highScore",
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            ArcadeButton(
                label = "INTENTAR DE NUEVO",
                tone = ArcadeTone.GREEN,
                onClick = onRestart,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
                fontSize = 18.sp,
                depth = 6.dp,
                leading = { RawImage(R.raw.ic_retry, Modifier.size(22.dp)) },
            )
            Spacer(Modifier.height(10.dp))
            ArcadeButton(
                label = "VER RANKING",
                tone = ArcadeTone.BLUE,
                onClick = onViewLeaderboard,
                modifier = Modifier.fillMaxWidth(),
                height = 44.dp,
                fontSize = 15.sp,
                leading = { RawImage(R.raw.ic_leaderboard, Modifier.size(18.dp)) },
            )
            Spacer(Modifier.height(6.dp))
            TextButton(
                onClick = onGoHome,
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("Ir al inicio", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.55f))
            }
        }
        // Lluvia de monedas por encima de la ventana cuando se bate el récord.
        if (isNewRecord) GoldCoinRain()
    }
}

@Composable
fun ReviveOverlay(
    teikoins: Int,
    canWatchAd: Boolean,
    adLoading: Boolean,
    /**
     * **POR QUÉ ha perdido** (C-4, 2026-09-02). Esta ventana titulaba siempre «¡Sin Movimientos!» y
     * prometía «Despeja el tablero y recupera 5 movimientos» en las tres derrotas del juego — así
     * que a quien se le llenaba el tablero con turnos de sobra le ofrecía turnos, y a quien lo
     * cubría la lava le hablaba de despejar. Es la pantalla que decide si el jugador paga: tiene que
     * decirle qué le ha pasado y qué va a recibir a cambio.
     */
    cause: com.cukiti.teikafusion.model.DefeatCause,
    onReviveAd: () -> Unit,
    onReviveCoins: () -> Unit,
    onTimeout: () -> Unit,
    onDecline: () -> Unit
) {
    // (Aquí había un parámetro `isTimeRevive` que ramificaba icono, textos y botones para la oferta
    //  por TIEMPO agotado del Infinito. Murió con «La Prensa» (2026-07-24): el reloj ya no mata.
    //  La rama que hay hoy es otra cosa: no cambia los botones, solo dice la verdad sobre la derrota.)
    // SIN_TURNOS conserva el reloj: el catálogo nuevo solo incluye ilustraciones específicas para
    // falta de espacio y lava, y sustituirlo por una causa distinta mentiría sobre la derrota.
    val causeIconRes = when (cause) {
        com.cukiti.teikafusion.model.DefeatCause.SIN_TURNOS -> null
        com.cukiti.teikafusion.model.DefeatCause.SIN_ESPACIO -> R.raw.ic_defeat_no_space
        com.cukiti.teikafusion.model.DefeatCause.LAVA -> R.raw.ic_defeat_lava
    }
    val causeTitle = when (cause) {
        com.cukiti.teikafusion.model.DefeatCause.SIN_TURNOS -> "¡Sin Movimientos!"
        com.cukiti.teikafusion.model.DefeatCause.SIN_ESPACIO -> "¡Sin Espacio!"
        com.cukiti.teikafusion.model.DefeatCause.LAVA -> "¡Te cubrió la lava!"
    }
    val causeBody = when (cause) {
        com.cukiti.teikafusion.model.DefeatCause.SIN_TURNOS ->
            "Se te acabaron los turnos. Revive y recupera 5 movimientos."
        com.cukiti.teikafusion.model.DefeatCause.SIN_ESPACIO ->
            "Ninguna ficha cabe ya. Revive y el tablero se despeja entero."
        com.cukiti.teikafusion.model.DefeatCause.LAVA ->
            "La lava llegó abajo del todo. Revive y el volcán se calma."
    }
    val reviveCost = com.cukiti.teikafusion.model.LevelBudget.REVIVE_COST_TEIKOINS
    val canAffordCoins = teikoins >= reviveCost

    // Cuenta atrás de 10 s. Al expirar → derrota (animación de rotura + ventana de reintentar).
    val totalMs = 10_000L
    var remainingMs by remember { mutableLongStateOf(totalMs) }
    // P-260 (2026-09-24): con el reloj de FOTOGRAMAS de Compose, no el de pared. En las capturas de
    // Roborazzi el reloj es virtual y la cifra sale siempre igual (con `currentTimeMillis` cada
    // ejecución pintaba otro instante); y con la app en segundo plano la cuenta se para con ella.
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (remainingMs > 0) {
            delay(50L)
            val now = withFrameMillis { it }
            remainingMs = (totalMs - (now - start)).coerceAtLeast(0L)
        }
        onTimeout()
    }
    val secondsLeft = ((remainingMs + 999) / 1000).toInt()
    val timerProgress = (remainingMs.toFloat() / totalMs).coerceIn(0f, 1f)
    val hurry = secondsLeft <= 3
    // Latido en los tres últimos segundos: el anillo y la cifra golpean una vez por segundo.
    val beat = if (hurry && !LocalReducedFx.current) {
        val frac = (remainingMs % 1000L) / 1000f
        1f + 0.10f * (frac * frac)
    } else 1f

    // Color de la ventana según la causa: naranja la falta de sitio, rojo la lava, azul los turnos.
    val (accent, accentDeep) = when (cause) {
        com.cukiti.teikafusion.model.DefeatCause.SIN_ESPACIO -> Color(0xFFFFA43A) to Color(0xFFB2470A)
        com.cukiti.teikafusion.model.DefeatCause.LAVA -> Color(0xFFFF6A3D) to Color(0xFF9E1E08)
        com.cukiti.teikafusion.model.DefeatCause.SIN_TURNOS -> Color(0xFF5CB8FF) to Color(0xFF1C5BA8)
    }

    // (REDISEÑO 2026-09-23, «que se vea triple A») Ventana de juego: cinta con la causa, el dibujo de
    // la causa DENTRO del anillo de la cuenta atrás —antes eran dos piezas apiladas—, y botones de
    // arcade. Tocar fuera NO cierra: aquí decide el reloj o el jugador con un botón.
    GameDialogFrame(
        title = causeTitle,
        accent = accent,
        accentDeep = accentDeep,
        onOutsideTap = null,
        scrimAlpha = 0.62f,
        iconSize = 112.dp,
        icon = {
            val ringColor = if (hurry) Color(0xFFFF5252) else Color(0xFFFFC53D)
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { scaleX = beat; scaleY = beat }
            ) {
                val stroke = size.minDimension * 0.085f
                val inset = stroke / 2f
                val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                drawCircle(Color(0xFF12162E), radius = size.minDimension / 2f - stroke * 0.2f, alpha = 0.72f)
                drawArc(
                    color = Color.White.copy(alpha = 0.14f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = ringColor,
                    startAngle = -90f, sweepAngle = 360f * timerProgress, useCenter = false,
                    topLeft = Offset(inset, inset), size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                )
            }
            if (causeIconRes != null) {
                RawImage(causeIconRes, Modifier.size(62.dp))
            } else {
                Text(text = "⏳", fontSize = 46.sp)
            }
            // La cifra, en una chapa que muerde el pie del anillo.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 12.dp)
                    .graphicsLayer { scaleX = beat; scaleY = beat }
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(ringColor, ringColor.copy(alpha = 0.75f))))
                    .border(2.5.dp, Color(0xFF12162E), CircleShape)
                    .semantics { contentDescription = "Quedan $secondsLeft segundos" },
                contentAlignment = Alignment.Center
            ) {
                Text("$secondsLeft", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF2A1400))
            }
        },
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = causeBody,
            fontSize = 15.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = Color.White.copy(alpha = 0.86f)
        )
        Spacer(modifier = Modifier.height(12.dp))
        PremiumWalletPill(teikoins)
        Spacer(modifier = Modifier.height(16.dp))

        // Ruta 1: anuncio recompensado. GRATIS. Si aún no cargó pero PUEDE cargar, se enseña
        // «cargando» (deshabilitado) en vez de ocultarlo: el jugador sabe que la opción gratis viene.
        // Si no puede cargar (sin conexión), no se enseña.
        if (canWatchAd) {
            ArcadeButton(
                label = "REVIVIR GRATIS",
                tone = ArcadeTone.GREEN,
                onClick = onReviveAd,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
                fontSize = 18.sp,
                depth = 6.dp,
                contentDescription = "Revivir gratis viendo un anuncio",
                leading = { RawImage(R.raw.adicon, Modifier.size(26.dp)) },
            )
            Spacer(modifier = Modifier.height(10.dp))
        } else if (adLoading) {
            ArcadeButton(
                label = "Cargando anuncio…",
                tone = ArcadeTone.GREEN,
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                height = 54.dp,
                fontSize = 16.sp,
                depth = 6.dp,
                leading = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.5.dp
                    )
                },
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Ruta 2: pagar con teikoins. El precio va DENTRO del botón, con su moneda.
        ArcadeButton(
            label = "REVIVIR  $reviveCost",
            tone = ArcadeTone.GOLD,
            onClick = onReviveCoins,
            enabled = canAffordCoins,
            modifier = Modifier.fillMaxWidth(),
            height = 50.dp,
            fontSize = 17.sp,
            depth = 6.dp,
            contentDescription = "Revivir por $reviveCost Teikoins",
            leading = { RawImage(R.raw.teikoin, Modifier.size(22.dp)) },
        )

        Spacer(modifier = Modifier.height(6.dp))
        TextButton(
            onClick = onDecline,
            modifier = Modifier.fillMaxWidth().height(44.dp)
        ) {
            Text("Rendirse", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.45f))
        }
    }
}

/**
 * Overlay "Sin vidas". Aparece cuando el jugador intenta empezar un nivel con 0 vidas.
 * Antes el botón "JUGAR" simplemente no respondía (return silencioso) → "no me deja jugar".
 * Ahora se explica por qué y se ofrece: esperar la regeneración, comprar una vida aquí mismo, o
 * abrir la ventana de VIDAS con todas las opciones (2026-09-06 — antes llevaba a la tienda, que ya
 * no vende ninguna).
 */
/**
 * **«AVÍSAME CUANDO ESTÉN LLENAS»** — y con ello, el permiso de notificaciones (2026-09-04).
 *
 * El juego declara `POST_NOTIFICATIONS` en el manifiesto, tiene tres avisos locales escritos
 * (vidas llenas, recordatorio diario, racha en peligro), su canal, su `WorkManager`… y **nadie pedía
 * nunca el permiso**. En Android 13+ eso significa denegado por defecto, así que `NotificationHelper.
 * show` volvía sin hacer nada: el sistema de reenganche entero llevaba meses escrito, programado y
 * MUDO en la mayoría de los móviles del mundo. También se tragaba las notificaciones remotas.
 *
 * Se pide **aquí y solo aquí**, y esa es la decisión: es el único momento en que el aviso es un
 * favor y no una interrupción —el jugador está mirando una cuenta atrás porque se ha quedado sin
 * vidas—, y lo pide **él** al pulsar. Pedirlo al arrancar (que es lo fácil) quema el permiso el día
 * de la instalación, cuando nadie sabe todavía para qué sirve el juego; y Android solo deja
 * preguntar dos veces.
 *
 * El diálogo es del SISTEMA, no un `Dialog` de Compose: no incumple la decisión congelada de la app
 * inmersiva, y `MainActivity.onWindowFocusChanged` vuelve a ocultar las barras al recuperar el foco.
 */
@Composable
private fun NotifyWhenFullButton() {
    val context = LocalContext.current
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    var granted by remember {
        mutableStateOf(com.cukiti.teikafusion.NotificationHelper.hasPermission(context))
    }
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok -> granted = ok }

    if (granted) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clearAndSetSemantics {
                contentDescription = "Te avisaremos cuando las vidas estén llenas"
            }
        ) {
            RawImage(R.raw.ic_bell_on, Modifier.size(16.dp))
            Text(
                text = "Te avisaremos cuando estén llenas",
                fontSize = 12.sp,
                color = Color(0xFF4CAF50),
                textAlign = TextAlign.Center
            )
        }
    } else {
        TextButton(onClick = {
            launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }) {
            RawImage(R.raw.ic_bell_on, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "Avísame cuando estén llenas",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4DB8FF)
            )
        }
    }
    Spacer(Modifier.height(6.dp))
}

@Composable
fun OutOfLivesOverlay(
    teikoins: Int,
    livesNextRegenAt: Long,
    onBuyLife: () -> Unit,
    /**
     * **Abre la VENTANA DE VIDAS**, no la tienda (2026-09-06).
     *
     * Hasta hoy este botón decía «Ir a la tienda» y llevaba a la pestaña de la tienda, que es donde
     * vivían las recargas. Las recargas se han mudado a su propia ventana ([LivesOverlay]) y la
     * tienda ya no tiene ni una vida a la venta: dejarlo como estaba habría mandado al jugador **sin
     * vidas** a una pantalla donde no hay ninguna — el peor destino posible justo en el momento en
     * que más prisa tiene.
     */
    onMoreLives: () -> Unit,
    onDismiss: () -> Unit
) {
    // Atrás = cerrar el aviso (antes el atrás del sistema lo ignoraba).
    androidx.activity.compose.BackHandler { onDismiss() }
    val lifeCost = 50
    val canAfford = teikoins >= lifeCost
    val scale = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .scale(scale.value)
                .fillMaxWidth(0.85f)
                // Traga el toque para que NO llegue al scrim de detrás (que cierra el panel).
                // Con `clickable {}` la tarjeta entera se anunciaba como un botón sin nombre y
                // TalkBack ofrecía "activar" una acción que no hace nada; `detectTapGestures` come
                // el gesto sin añadir semántica (mismo idioma que el blocker de AppNavigation).
                .pointerInput(Unit) { detectTapGestures { } }
                .background(
                    brush = Brush.sweepGradient(
                        listOf(Color(0xFFE53935), Color(0xFFB71C1C), Color(0xFFE53935))
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(4.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
                    .padding(28.dp)
            ) {
                RawImage(R.raw.ic_heart_broken, Modifier.size(72.dp))
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "¡Sin vidas!",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFE53935),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Necesitas al menos 1 vida para jugar un nivel.",
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )

                Spacer(Modifier.height(18.dp))

                // Temporizador de regeneración (cuenta atrás en vivo)
                if (livesNextRegenAt > 0L) {
                    Text("Próxima vida gratis en", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    Spacer(Modifier.height(6.dp))
                    // Mismo reloj que la tienda (P-167). El `while` que había aquí era una copia
                    // vieja: no recalculaba al entrar, así que enseñaba el valor del objetivo
                    // anterior durante el primer segundo.
                    val remainingMs = rememberLivesRegenRemaining(livesNextRegenAt)
                    val totalSeconds = (remainingMs / 1000).coerceAtLeast(0)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFE53935).copy(alpha = 0.12f))
                            .border(1.dp, Color(0xFFE53935).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = "⏱ %02d:%02d".format(totalSeconds / 60, totalSeconds % 60),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFE53935)
                        )
                    }
                    Spacer(Modifier.height(20.dp))

                    // «Avísame cuando estén llenas»: el ÚNICO sitio donde se pide el permiso de
                    // notificaciones, y a propósito. Ver [NotifyWhenFullButton].
                    NotifyWhenFullButton()
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        text = "Tienes: $teikoins",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canAfford) Color(0xFF4CAF50) else Color(0xFFE53935)
                    )
                    RawImage(R.raw.teikoin, Modifier.size(16.dp))
                }

                Spacer(Modifier.height(16.dp))

                // Comprar 1 vida y jugar al instante
                Button(
                    onClick = onBuyLife,
                    enabled = canAfford,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFE53935),
                        disabledContainerColor = Color(0xFF555555)
                    )
                ) {
                    if (canAfford) {
                        Text(
                            text = "Comprar 1 vida y jugar ($lifeCost",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                        RawImage(R.raw.teikoin, Modifier.padding(horizontal = 3.dp).size(16.dp))
                        Text(")", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    } else {
                        Text(
                            text = "Sin monedas suficientes",
                            fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Más opciones: la ventana de vidas (recargas y vidas infinitas), no la tienda.
                Button(
                    onClick = onMoreLives,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text("Más opciones de vidas", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(4.dp))

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(44.dp)
                ) {
                    Text("Cerrar", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                }
            }
        }
    }
}

/**
 * **LA VENTANA DE PAUSA, A LA MITAD** (rediseño 2026-09-07, pedido del usuario sobre
 * `art/ventana4.jpeg`).
 *
 * Medía ~560 dp —más alta que el propio tablero— para tres acciones, y estaba llena de cosas que
 * ocupaban sitio sin decir nada nuevo. Lo que se ha ido, y por qué:
 *
 *  1. **El emblema de 52 sp y el rótulo de 26.** El jugador acaba de pulsar pausa con su propio
 *     dedo: no hace falta un cartel de 120 dp para confirmárselo. Emblema de 30 dp AL LADO del
 *     rótulo, en una línea.
 *  2. **La tarjeta de ajustes entera** ([AudioVibrationCard], con sus tres rótulos de sección y sus
 *     dos filas de ancho completo) → [QuickSettingsGrid], rejilla 3×2. **Los seis interruptores
 *     siguen ahí**: lo que se fue es el andamio, no las opciones.
 *  3. **El aviso «Reiniciar abandona el intento: cuesta 1 vida»**, dos líneas de párrafo al pie. El
 *     precio no desaparece —eso sería cobrar en silencio, que es lo que C-6 prohíbe—: se pinta como
 *     una chapita ♥−1 SOBRE el botón que cobra, que es donde el jugador la va a mirar.
 *  4. **Los rótulos de «Reiniciar nivel» y «Salir al Inicio».** Un icono de reintento y una casa no
 *     necesitan que se los traduzca; el nombre sigue vivo en `contentDescription` para TalkBack.
 *
 * Queda UNA fila de acciones: los dos iconos en los extremos y CONTINUAR grande en medio —el único
 * verbo escrito, porque es el único que el jugador viene a buscar, y en el centro está lejos de los
 * otros dos por si el pulgar falla.
 */
@Composable
fun PauseOverlay(
    musicVolume: Float,
    sfxVolume: Float,
    vibrationEnabled: Boolean,
    tiltEnabled: Boolean,
    isMuted: Boolean,
    reducedFx: Boolean,
    notificationsEnabled: Boolean = true,
    onMusicVolume: (Float) -> Unit,
    onSfxVolume: (Float) -> Unit,
    onSfxPreview: () -> Unit,
    onToggleVibration: () -> Unit,
    onToggleTilt: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleReducedFx: () -> Unit,
    onToggleNotifications: () -> Unit = {},
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onGoHome: () -> Unit,
    /** ¿Reiniciar cobra una vida? (C-6). Falso en el tutorial y antes de la primera colocación. */
    restartCostsLife: Boolean = false,
    /** P-357: las reglas vivas de este nivel ([com.cukiti.teikafusion.model.LevelRules]); vacío = sin sección. */
    levelRules: List<com.cukiti.teikafusion.model.LevelRules.Entry> = emptyList(),
    onDismiss: () -> Unit = {}
) {
    val scale         = remember { Animatable(0.85f) }
    var rulesOpen by remember { mutableStateOf(false) }
    val backdropAlpha = remember { Animatable(0f) }
    // P-226: salir al Inicio pasó a ser un icono de 58 dp pegado al botón grande, y eso es un roce de
    // distancia. No cobra vida y la partida se guarda, así que no es irreversible — pero SÍ saca al
    // jugador de la partida que está jugando, y eso no puede pasar por un dedo mal puesto.
    var confirmGoHome by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        launch { backdropAlpha.animateTo(0.85f, tween(250)) }
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backdropAlpha.value))
            // `detectTapGestures` y no `clickable`: el scrim es un rectángulo a pantalla completa,
            // y con `clickable` TalkBack anunciaba un botón gigante sin nombre por encima de todo
            // (patrón del bloque 24). Quien usa lector reanuda con «Continuar», que sí tiene nombre.
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .safeDrawingPadding()
                .padding(vertical = 20.dp)
                .scale(scale.value)
                .fillMaxWidth(0.86f)
                // Techo de ancho: en tablet la ventana se estiraba hasta parecer una pantalla.
                .widthIn(max = 400.dp)
                .pointerInput(Unit) { detectTapGestures { } }
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color(0xFF4FC3F7), Color(0xFF0288D1), Color(0xFF4FC3F7))
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(3.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(23.dp))
                    // Red de seguridad para pantallas muy cortas o fuente del sistema enorme.
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                // CABECERA EN UNA LÍNEA (2026-09-07). Antes: emoji de 52 sp + rótulo de 26 sp +
                // dos espaciadores = ~120 dp de ventana gastados en decir lo que el jugador acaba
                // de hacer con su propio dedo. El emblema baja a 30 dp y se pone AL LADO del
                // rótulo: la misma identidad, una cuarta parte del alto.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(
                                Brush.verticalGradient(listOf(Color(0xFFFFC24B), Color(0xFFF08A1E)))
                            )
                            .clearAndSetSemantics { },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            repeat(2) {
                                Box(
                                    Modifier
                                        .size(width = 4.dp, height = 13.dp)
                                        .background(Color.White, RoundedCornerShape(2.dp))
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "JUEGO EN PAUSA",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.8.sp,
                        color = Color(0xFF4FC3F7),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // (2026-07-26) «Continuar» YA NO va aquí arriba: bajó con los otros dos para que el
                // trío de acciones viva junto, al alcance del pulgar y con un solo lenguaje visual.

                // Los MISMOS seis interruptores que en Ajustes, pero en rejilla compacta: en pausa
                // no hacen falta los tres rótulos de sección (el color de cada casilla ya agrupa) ni
                // dos filas de ancho completo. Ver [QuickSettingsGrid].
                QuickSettingsGrid(
                    musicVolume       = musicVolume,
                    sfxVolume         = sfxVolume,
                    vibrationEnabled  = vibrationEnabled,
                    notificationsEnabled = notificationsEnabled,
                    tiltEnabled       = tiltEnabled,
                    isMuted           = isMuted,
                    reducedFx         = reducedFx,
                    onMusicVolume     = onMusicVolume,
                    onSfxVolume       = onSfxVolume,
                    onSfxPreview      = onSfxPreview,
                    onToggleVibration = onToggleVibration,
                    onToggleTilt      = onToggleTilt,
                    onToggleMute      = onToggleMute,
                    onToggleReducedFx = onToggleReducedFx,
                    onToggleNotifications = onToggleNotifications,
                    // Una sola fila de seis: en pausa la rejilla de 3 hacía casillas de ~100 dp.
                    columns = 6,
                )

                // ── P-357 · LAS REGLAS DE ESTE NIVEL, PLEGADAS ──────────────────────────────────────
                // Plegadas a propósito: la pausa sigue siendo corta y no reabre el cartel de reglas
                // antes de jugar que se retiró en la 26b. Solo las ve quien las busca.
                if (levelRules.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (rulesOpen) "▾ REGLAS DE ESTE NIVEL" else "▸ ¿CÓMO FUNCIONA ESTE NIVEL?",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        color = Color(0xFF4FC3F7),
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable(role = Role.Button) { rulesOpen = !rulesOpen }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    if (rulesOpen) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(14.dp))
                                .padding(12.dp)
                        ) {
                            levelRules.forEach { rule ->
                                Column {
                                    Text(rule.title, fontSize = 11.sp, fontWeight = FontWeight.Black,
                                        letterSpacing = 1.2.sp, color = Color(0xFFFFC24B))
                                    Text(rule.text, fontSize = 13.sp, lineHeight = 17.sp,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // ── LA BARRA DE ACCIONES ──────────────────────────────────────────────────────────
                // UNA SOLA FILA (2026-09-07). Antes eran dos botones con rótulo + un aviso de dos
                // líneas + un tercer botón de ancho completo: cuatro bloques apilados, ~180 dp, y
                // las tres acciones pesando lo mismo aunque solo una sea la que el jugador quiere.
                //
                // Ahora hay UN verbo escrito —CONTINUAR, que es a lo que se viene— y dos controles
                // de icono: casa (salir) e ícono de reintento (reiniciar). No llevan texto porque su
                // dibujo ya lo dice, y van en los EXTREMOS, uno a cada lado del botón grande: así
                // ninguno de los dos queda pegado al otro ni al pulgar que buscaba «Continuar».
                //
                // «Reiniciar nivel» ya NO pide doble confirmación (2026-07-26): la reconfirmación
                // «Toca otra vez para abandonar» convertía un botón en un acertijo de dos pasos.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PauseIconButton(
                        description = "Salir al Inicio",
                        gradient = listOf(Color(0xFF3A4260), Color(0xFF232941)),
                        onClick = { confirmGoHome = true }
                    ) {
                        Image(
                            painter = painterResource(R.drawable.homebutton),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    PausePrimaryButton(
                        text = "CONTINUAR",
                        onClick = onResume,
                        modifier = Modifier.weight(1f)
                    )

                    // C-6 (2026-09-02): reiniciar DESTRUYE el intento en curso y por eso cuesta una
                    // vida, igual que perderlo. Cobrar en silencio por un botón que ayer era gratis
                    // es exactamente lo que no se hace — pero decirlo no exige un párrafo debajo de
                    // la ventana: el precio va ENCIMA del botón que cobra, como un corazón y un −1.
                    // «Salir al Inicio» NO cobra: guarda la partida y se retoma con «Continuar».
                    PauseIconButton(
                        description = if (restartCostsLife)
                            "Reiniciar nivel, cuesta una vida" else "Reiniciar nivel",
                        gradient = listOf(Color(0xFFFFA000), Color(0xFFE64A19)),
                        cost = if (restartCostsLife) 1 else 0,
                        onClick = onRestart
                    ) {
                        RawImage(R.raw.ic_retry, Modifier.size(24.dp))
                    }
                }
            }
        }
    }

    // P-226 · SALIR PREGUNTA (2026-09-07b). Va al final del cuerpo, no arriba: el ORDEN DE
    // COMPOSICIÓN es lo que la pone por encima de la ventana de pausa, y su `BackHandler` —
    // registrado después— es el que gana el botón atrás (regla del bloque 23).
    //
    // NO es `danger`: no se pierde nada. La partida se guarda y se retoma con «Continuar», y el
    // texto lo dice, porque la mitad del miedo a este botón es no saber si se pierde el tablero.
    if (confirmGoHome) {
        ConfirmOverlay(
            title = "Salir al Inicio",
            message = "Tu partida se guarda tal y como está: podrás retomarla desde donde la dejas.",
            confirmLabel = "Salir",
            dismissLabel = "Seguir jugando",
            icon = {
                Image(
                    painter = painterResource(R.drawable.homebutton),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
            },
            onConfirm = {
                confirmGoHome = false
                onGoHome()
            },
            onDismiss = { confirmGoHome = false }
        )
    }
}

/**
 * **El verbo de la pausa.** Un solo botón con texto en toda la ventana: el que devuelve al juego.
 * Alto de 58 dp, gradiente azul y filo de luz superior — el relieve que lo hace un botón físico.
 */
@Composable
private fun PausePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = modifier
            .height(58.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF4FC3F7), Color(0xFF0277BD))))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .align(Alignment.TopCenter)
                .background(Color.White.copy(alpha = 0.30f))
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            RawImage(R.raw.ic_play, Modifier.size(19.dp))
            Spacer(Modifier.width(9.dp))
            Text(
                text = text,
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                color = Color.White,
                maxLines = 1
            )
        }
    }
}

/**
 * **Acción de la pausa SIN rótulo**: casa (salir) y reintento (reiniciar). El icono ya dice qué
 * hace, así que escribirlo debajo solo gastaba ancho; el nombre viaja en `contentDescription`, que
 * es donde hace falta de verdad (TalkBack lo lee igual que si estuviera pintado).
 *
 * @param cost vidas que cuesta la acción. Si es > 0 se pinta una chapita ♥−n arriba del icono:
 *   el precio tiene que verse SOBRE el botón que cobra, no en un párrafo al pie de la ventana.
 */
@Composable
private fun PauseIconButton(
    description: String,
    gradient: List<Color>,
    onClick: () -> Unit,
    cost: Int = 0,
    icon: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(17.dp)
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(shape)
            .background(Brush.verticalGradient(gradient))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .align(Alignment.TopCenter)
                .background(Color.White.copy(alpha = 0.28f))
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            icon()
            if (cost > 0) {
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.clearAndSetSemantics { }
                ) {
                    RawImage(R.raw.heartful, Modifier.size(10.dp))
                    Text(
                        text = "−$cost",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White.copy(alpha = 0.92f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * **VENTANA DE CONFIRMACIÓN en la jerarquía normal** (bloque 23, 2026-07-31).
 *
 * Sustituye a los `AlertDialog` de Material, que estaban PROHIBIDOS por la decisión congelada del
 * proyecto y aun así quedaban cinco vivos —los cuatro de Ajustes y el resultado del borrado de
 * cuenta—. El motivo no es estético: la app corre en **modo inmersivo**, y un `AlertDialog` abre una
 * VENTANA propia del sistema. Al abrirse, esa ventana devuelve las barras de estado y navegación, y
 * al recuperarlas el layout entero **se desplaza** de golpe; al cerrarla, vuelve a saltar. Cambiar
 * el nombre o cerrar sesión hacía brincar toda la interfaz por debajo del panel.
 *
 * Aquí no hay ventana: es un `Box` a pantalla completa dentro del mismo árbol, con el mismo idioma
 * visual que [QuitDialogOverlay] y [SettingsOverlay] (scrim + tarjeta con borde en gradiente).
 *
 * - **Atrás cierra ESTA ventana**, no la de debajo: su `BackHandler` se registra después que el del
 *   panel que la abre, así que gana por orden de composición (regla del bloque 23).
 * - **El scrim se come el toque con `detectTapGestures`, no con `clickable {}`**: un `clickable`
 *   anuncia a TalkBack un botón enorme y sin nombre por encima de todo (patrón del bloque 24). Quien
 *   usa lector cierra con el botón de cancelar, que sí tiene nombre.
 * - **`imePadding`**: sin la ventana del sistema, el teclado ya no reflota nada por su cuenta — la
 *   tarjeta tiene que subir sola cuando hay campo de texto (mismo patrón que `PlayerNameOnboarding`).
 *
 * @param danger tiñe de rojo el borde y la acción: se reserva a lo irreversible (borrar, restablecer).
 * @param content slot opcional bajo el mensaje (campos de texto de «cambiar nombre» / «ELIMINAR»).
 */
@Composable
fun ConfirmOverlay(
    title: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    message: String? = null,
    dismissLabel: String? = "Cancelar",
    danger: Boolean = false,
    confirmEnabled: Boolean = true,
    /** Dibujo opcional sobre el mensaje, con rayos detrás (casa para «salir», etc.). */
    icon: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = {}
) {
    androidx.activity.compose.BackHandler(enabled = true) { onDismiss() }
    // Rojo solo para lo irreversible; el resto, el morado de la casa.
    val accent = if (danger) Color(0xFFFF5A5F) else Color(0xFF8C6BFF)
    val accentDeep = if (danger) Color(0xFFA3122A) else Color(0xFF4A2FB8)
    GameDialogFrame(
        title = title,
        accent = accent,
        accentDeep = accentDeep,
        onOutsideTap = onDismiss,
        icon = icon?.let { i -> { i() } },
        iconSize = 84.dp,
    ) {
        if (message != null) {
            Text(
                text = message,
                fontSize = 15.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = Color.White.copy(alpha = 0.86f)
            )
            Spacer(Modifier.height(12.dp))
        }
        content()
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (dismissLabel != null) {
                ArcadeButton(
                    label = dismissLabel,
                    tone = ArcadeTone.NEUTRAL,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    height = 48.dp,
                    fontSize = 15.sp,
                    depth = 5.dp,
                )
            }
            ArcadeButton(
                label = confirmLabel,
                tone = if (danger) ArcadeTone.RED else ArcadeTone.GREEN,
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.weight(1f),
                height = 48.dp,
                fontSize = 15.sp,
                depth = 5.dp,
            )
        }
    }
}

/**
 * P-420 · AVISO DE ABANDONO (2026-10-01, decisión del usuario). Empezar otro nivel con una partida a
 * medias la BORRA (la ranura de guardado es única) y cobra una vida (regla C-6, `startGame`). La regla
 * no cambia; lo que faltaba era decirlo antes: el jugador perdía la partida y la vida sin enterarse.
 * Lo verde es lo gratis (retomar); empezar el otro queda como acción secundaria en rojo.
 */
@Composable
fun AbandonAttemptOverlay(
    savedLevel: Int,
    targetLevel: Int,
    costsLife: Boolean,
    onContinueSaved: () -> Unit,
    onStartAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    val saved = com.cukiti.teikafusion.model.LevelMap.displayNumber(savedLevel)
    val target = com.cukiti.teikafusion.model.LevelMap.displayNumber(targetLevel)
    ConfirmOverlay(
        title = "¿Dejas el nivel $saved?",
        message = "Tienes el nivel $saved a medias. Si empiezas el nivel $target, esa partida se pierde" +
            (if (costsLife) " y te cuesta una vida." else "."),
        confirmLabel = "Seguir el $saved",
        dismissLabel = "Cancelar",
        onConfirm = onContinueSaved,
        onDismiss = onDismiss,
    ) {
        ArcadeButton(
            label = "Empezar el $target igualmente",
            tone = ArcadeTone.RED,
            onClick = onStartAnyway,
            modifier = Modifier.fillMaxWidth(),
            height = 44.dp,
            fontSize = 14.sp,
            depth = 4.dp,
        )
    }
}

/**
 * «¿Salir del nivel?» del botón ATRÁS. Era su propia ventana —dos cajas de colores con un Teika
 * cada una y ni una palabra de qué pasaba con la partida—; ahora es una confirmación más, con la
 * casa como dibujo y la promesa que quita el miedo: la partida se guarda (ver `quitGame`).
 */
@Composable
fun QuitDialogOverlay(
    onConfirmQuit: () -> Unit,
    onCancel: () -> Unit
) {
    ConfirmOverlay(
        title = "¿Salir del nivel?",
        message = "Tu partida se guarda tal y como está: podrás retomarla desde donde la dejas.",
        confirmLabel = "Salir",
        dismissLabel = "Seguir jugando",
        onConfirm = onConfirmQuit,
        onDismiss = onCancel,
        icon = {
            Image(
                painter = painterResource(R.drawable.homebutton),
                contentDescription = null,
                modifier = Modifier.size(64.dp)
            )
        },
    )
}

// Posiciones reales de los elementos de la UI, capturadas con onGloballyPositioned.
// Todas en coordenadas de ventana (window coords). El TutorialOverlay resta rootOffset
// para convertir a coordenadas locales del BoxWithConstraints raíz.
data class TutorialPositions(
    val rootOffset: Offset = Offset.Zero,
    val board:      Rect   = Rect.Zero,
    val tray:       Rect   = Rect.Zero,
    val piece:      Rect   = Rect.Zero,
    /**
     * El tirador de HABILIDADES, que vive en la misma banda bajo la bandeja que la píldora de
     * progreso. Se pasa para que la píldora se aparte de él — ver `ProgressiveTutorialOverlay`.
     */
    val abilityHandle: Rect = Rect.Zero
)

/** Victoria sin estrellas, monedas ni anuncios para los cuatro niveles de aprendizaje. */
@Composable
fun TutorialLevelCompleteOverlay(level: Int, onContinue: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = true) { }
    val spec = com.cukiti.teikafusion.model.TutorialCampaign.spec(level) ?: return
    val entry = remember(level) { Animatable(0.88f) }
    val reducedMotion = LocalReducedFx.current
    LaunchedEffect(level, reducedMotion) {
        if (reducedMotion) entry.snapTo(1f)
        else entry.animateTo(1f, spring(dampingRatio = 0.74f, stiffness = 360f))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer { scaleX = entry.value; scaleY = entry.value; alpha = entry.value }
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF27305F), Color(0xFF151A39))),
                    RoundedCornerShape(26.dp)
                )
                .border(
                    2.dp,
                    Brush.horizontalGradient(listOf(Color(0xFF55E6FF), Color(0xFFA477FF))),
                    RoundedCornerShape(26.dp)
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TeikaIcon(spec.completionTeika, Modifier.size(100.dp))
            Spacer(Modifier.height(12.dp))
            // CENTRADO EXPLÍCITO (2026-09-01, grabación `analisistutorial`). Sin `textAlign` un texto
            // de UNA línea se centra porque su caja mide lo que mide, pero en cuanto parte en dos
            // —«¡INCLINACIÓN / DOMINADA!»— las líneas se alinean a la IZQUIERDA dentro de esa caja y
            // el título queda descuadrado contra un icono, un subtítulo y un botón que sí están
            // centrados. Con dos líneas también se le da aire lateral y se baja un punto el cuerpo,
            // para que el título no llegue a tocar el borde de la ventana.
            Text(
                text = spec.completionTitle,
                color = Color.White,
                fontSize = 24.sp,
                lineHeight = 29.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = spec.completionNote,
                color = Color.White.copy(alpha = 0.74f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C4DFF))
            ) {
                Text(
                    if (level < com.cukiti.teikafusion.model.TutorialCampaign.LAST_LEVEL)
                        "SIGUIENTE LECCIÓN" else "ENTRAR AL JUEGO",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}



/** Nivel al que se enciende la música. Es el que estaba afinado contra `SoundManager.MUSIC_CEILING`. */
private const val MUSIC_ON_LEVEL = 0.6f

/** Nivel al que se encienden los efectos. Van a tope: se disparan cortos y ya se mezclan por evento. */
private const val SFX_ON_LEVEL = 1f

/**
 * Ajustes de sonido y juego, en **interruptores** y repartidos en secciones.
 *
 * **Ya no hay sliders (2026-07-31).** Tenía uno para la música y otro para los efectos, más una fila
 * aparte de «Silencio general» que mandaba sobre los dos: tres controles para una decisión que en un
 * puzzle es binaria —suena o no suena—, y entre los tres se comían la mitad del panel. Ahora son dos
 * chips, y el estado se lee de un vistazo en vez de calcularse mirando dónde cayó el pulgar.
 *
 * **Dónde vive ahora el silencio general.** El flag `isMuted` sigue existiendo y persistiéndose, pero
 * su superficie son estos dos chips: apagar los dos deja el juego en silencio, y encender cualquiera
 * de los dos **levanta el mute maestro**. Eso importa porque el bug histórico de este ajuste fue
 * justo el contrario —quedó guardado a `true` sin ningún control que lo bajara y el jugador se quedó
 * sin audio para siempre—; aquí ese estado es imposible de alcanzar sin salida.
 */
/* ── (RETIRADO 2026-09-07b, P-225) `AudioVibrationCard` ──────────────────────────────────────────
 *
 * Era la versión de AJUSTES de los seis interruptores: tres rótulos de sección, dos chips en pareja
 * y dos filas de ancho completo. Dibujaba exactamente lo mismo que la rejilla de la pausa con otro
 * código, o sea DOS COPIAS DE UN DIBUJO — el bug recurrente de este repositorio: el séptimo ajuste
 * habría que acordarse de añadirlo en dos sitios.
 *
 * Ahora las dos ventanas llaman a [QuickSettingsGrid], que trae `sections` para elegir disposición.
 * Con la tarjeta se fueron sus tres piezas privadas, que no usaba nadie más: `ToggleChip` (el chip
 * horizontal en pareja), `SwitchRow` (la fila de ancho completo de «Reducir efectos») y
 * `NotificationsSwitchRow` (la fila de AVISOS con su doble puerta de permiso, cuya lógica vive ahora
 * dentro de la rejilla). `SettingsSectionHeader` se retiró después, el 2026-09-23 (ver abajo). */

/**
 * **LOS INTERRUPTORES, DEFINIDOS UNA SOLA VEZ** (P-225, 2026-09-07b) · **SOLO ICONOS** (2026-09-23).
 *
 * Una sola lista de ajustes y un solo dibujo para las dos ventanas que los enseñan —la PAUSA y
 * AJUSTES—: el día que entre uno nuevo se añade una línea aquí y aparece en las dos (antes había dos
 * copias del dibujo, el bug recurrente del repositorio).
 *
 * **Sin texto en las casillas** (pedido del usuario, 2026-09-23, sustituye a «AJUSTES por SECCIONES»
 * de 2026-07-31): los rótulos de sección y las palabras bajo cada icono hacían el panel casi tan alto
 * como la pantalla. Ahora cada ajuste es una casilla cuadrada con su icono, y el significado llega por
 * tres vías que no cuestan altura:
 * - **Estado**: encendida = color de su grupo (morado sonido, azul juego, ámbar avisos) con el icono
 *   vivo; apagada = gris, icono tenue y TACHADO — el signo universal de «desactivado».
 * - **Etiqueta flotante**: al tocar (o mantener pulsado, que solo pregunta) sale sobre la rejilla una
 *   píldora «Música · SÍ» durante ~1,3 s. Flota encima: no mueve nada.
 * - **Rebote + vibración corta** al tocar, para que el dedo sepa que ha acertado.
 * El lector de pantalla sigue leyendo nombre y estado (`Role.Switch` + descripción).
 *
 * [columns] decide la disposición (pausa 3×2; ajustes 4×2) y [extras] añade casillas de acción que no
 * son interruptores (la cuenta y «Más ajustes» en AJUSTES) con la misma forma y la misma etiqueta.
 */
@Composable
private fun QuickSettingsGrid(
    musicVolume: Float,
    sfxVolume: Float,
    vibrationEnabled: Boolean,
    tiltEnabled: Boolean,
    isMuted: Boolean,
    reducedFx: Boolean,
    notificationsEnabled: Boolean,
    onMusicVolume: (Float) -> Unit,
    onSfxVolume: (Float) -> Unit,
    onSfxPreview: () -> Unit,
    onToggleVibration: () -> Unit,
    onToggleTilt: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleReducedFx: () -> Unit,
    onToggleNotifications: () -> Unit,
    columns: Int = 3,
    extras: List<QuickToggle> = emptyList(),
) {
    val purple = Color(0xFF7C4DFF)
    val blue = Color(0xFF4FC3F7)
    val amber = Color(0xFFFFB300)
    val musicOn = !isMuted && musicVolume > 0f
    val sfxOn = !isMuted && sfxVolume > 0f

    // Avisos: las DOS puertas (permiso del sistema y preferencia de la app). Un interruptor
    // «encendido» que no enciende nada es peor que no tenerlo, así que lo que se pinta es la
    // conjunción y tocarlo sin permiso lo pide en vez de fingir.
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(com.cukiti.teikafusion.NotificationHelper.hasPermission(context))
    }
    // La etiqueta flotante (declarada aquí porque la usa también el permiso de avisos, abajo).
    var feedback by remember { mutableStateOf<QuickFeedback?>(null) }
    var nextToken by remember { mutableIntStateOf(0) }
    // P-434 · PERMISO DENEGADO PARA SIEMPRE. Tras dos «no», Android ya no enseña el diálogo: la
    // petición vuelve con «no» al instante y el interruptor parecía muerto. Una respuesta en menos
    // de medio segundo no la ha dado una persona — se abre entonces la ficha de notificaciones del
    // sistema, que es el único sitio donde aún se pueden encender, con una línea que lo dice.
    var askedAt by remember { mutableLongStateOf(0L) }
    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { ok ->
        granted = ok
        if (!ok && android.os.SystemClock.elapsedRealtime() - askedAt < AUTO_DENIED_MS) {
            feedback = QuickFeedback("Actívalos en los ajustes del móvil", null, Color(0xFFFFB300), ++nextToken)
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                )
            }
        }
    }
    // Al volver de los ajustes del sistema, el permiso puede haber cambiado: se relee.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                granted = com.cukiti.teikafusion.NotificationHelper.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val notifyOn = notificationsEnabled && granted

    // ── LA LISTA. Añadir un ajuste nuevo es añadir una línea AQUÍ, y aparece en las dos ventanas ──
    val toggles = listOf(
        QuickToggle("Música", musicOn, purple, onToggle = {
            if (musicOn) onMusicVolume(0f) else {
                // Encender un canal levanta el silencio general: si no, el jugador da al
                // interruptor, lo ve encendido y sigue sin oír nada.
                if (isMuted) onToggleMute()
                onMusicVolume(MUSIC_ON_LEVEL)
            }
        }) { SettingLottieIcon(R.raw.ic_music, Modifier.size(34.dp), loop = musicOn) },

        QuickToggle("Efectos", sfxOn, purple, onToggle = {
            if (sfxOn) onSfxVolume(0f) else {
                if (isMuted) onToggleMute()
                onSfxVolume(SFX_ON_LEVEL)
                onSfxPreview()   // se oye al instante lo que se acaba de encender
            }
        }) { SettingLottieIcon(R.raw.ic_sfx, Modifier.size(34.dp), loop = sfxOn) },

        QuickToggle("Vibración", vibrationEnabled, blue, onToggle = onToggleVibration) {
            SettingLottieIcon(R.raw.ic_vibration, Modifier.size(34.dp), loop = vibrationEnabled)
        },
        QuickToggle("Inclinar", tiltEnabled, blue, onToggle = onToggleTilt) {
            SettingLottieIcon(R.raw.ic_tilt, Modifier.size(34.dp), loop = tiltEnabled)
        },
        QuickToggle("Reducir efectos", reducedFx, blue, onToggle = onToggleReducedFx) {
            RawImage(R.raw.ic_calm_mode, Modifier.size(26.dp))
        },

        QuickToggle("Avisos", notifyOn, amber, onToggle = {
            if (!granted) {
                // Sin permiso, encender la preferencia no sirve de nada: se pide primero. Si estaba
                // apagada se enciende de paso, para que conceder el permiso deje el interruptor
                // donde el jugador quería dejarlo.
                if (!notificationsEnabled) onToggleNotifications()
                askedAt = android.os.SystemClock.elapsedRealtime()
                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else onToggleNotifications()
        }) {
            RawImage(if (notifyOn) R.raw.ic_bell_on else R.raw.ic_bell_off, Modifier.size(26.dp))
        },
    ) + fpsToggle() + extras

    // La etiqueta flotante: qué se tocó y cómo quedó. El `token` reinicia el temporizador aunque se
    // toque dos veces la misma casilla.
    // Lo que PINTA la píldora: sobrevive a `feedback = null` para que la salida se anime con texto.
    var shown by remember { mutableStateOf<QuickFeedback?>(null) }
    LaunchedEffect(feedback?.token) {
        val f = feedback ?: return@LaunchedEffect
        shown = f
        delay(QUICK_FEEDBACK_MS)
        feedback = null
    }
    val view = LocalView.current

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            toggles.chunked(columns).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    row.forEach { toggle ->
                        QuickToggleTile(
                            toggle,
                            onTap = {
                                Haptics.perform(view, HapticFeedbackConstants.KEYBOARD_TAP)
                                // El estado que se anuncia es el que QUEDA tras tocar. Las casillas
                                // de acción (sin interruptor) solo dicen su nombre.
                                feedback = QuickFeedback(toggle.label, toggle.checked?.not(), toggle.accent, ++nextToken)
                                toggle.onToggle()
                            },
                            onLongPress = {
                                Haptics.perform(view, HapticFeedbackConstants.LONG_PRESS)
                                feedback = QuickFeedback(toggle.label, toggle.checked, toggle.accent, ++nextToken)
                            },
                        )
                    }
                    // Huecos hasta completar la fila: así todas las casillas miden lo mismo.
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        // La píldora flota POR ENCIMA de la rejilla (sobre la cabecera de la ventana): no cuesta
        // altura y no empuja nada al aparecer.
        AnimatedVisibility(
            visible = feedback != null,
            enter = fadeIn(tween(120)) + androidx.compose.animation.scaleIn(
                initialScale = 0.7f,
                animationSpec = spring(Spring.DampingRatioMediumBouncy)
            ),
            exit = androidx.compose.animation.fadeOut(tween(180)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = (-34).dp)
                .zIndex(2f)
        ) {
            shown?.let { QuickFeedbackPill(it) }
        }
    }
}

/** P-434: una negativa más rápida que esto la ha dado el sistema, no el jugador. */
private const val AUTO_DENIED_MS = 500L

/** Cuánto se queda a la vista la etiqueta de la última casilla tocada. */
private const val QUICK_FEEDBACK_MS = 1300L

/**
 * Una casilla de [QuickSettingsGrid]: cómo se llama (lo dice la etiqueta flotante y el lector de
 * pantalla, no el dibujo), si está encendida, de qué color se enciende, qué hace y qué icono la dibuja.
 *
 * [checked] `null` = casilla de ACCIÓN (abrir algo), no interruptor: nunca se tacha y la etiqueta
 * solo dice el nombre. [badge] es un punto de color opcional en la esquina (la cuenta vinculada).
 */
/**
 * P-371: contador de FPS disponible en debug y release.
 * Está en la lista común, así que sale en Ajustes y en la pausa.
 */
private fun fpsToggle(): List<QuickToggle> {
    if (!fpsOverlayAvailable) return emptyList()
    val on = fpsOverlayOn.value
    return listOf(
        QuickToggle("Contador FPS", on, Color(0xFF69F0AE), onToggle = { fpsOverlayOn.value = !on }) {
            Text("FPS", color = if (on) Color(0xFF69F0AE) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black)
        }
    )
}

private class QuickToggle(
    val label: String,
    val checked: Boolean?,
    val accent: Color,
    val badge: Color? = null,
    val onToggle: () -> Unit,
    val icon: @Composable () -> Unit,
)

private class QuickFeedback(val label: String, val on: Boolean?, val accent: Color, val token: Int)

@Composable
private fun QuickFeedbackPill(f: QuickFeedback) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF12172E))
            .border(1.5.dp, f.accent.copy(alpha = 0.85f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clearAndSetSemantics { },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(f.label, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
        if (f.on != null) {
            Text(
                if (f.on) "SÍ" else "NO",
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                color = if (f.on) Color(0xFF69F0AE) else Color(0xFFFF8A80)
            )
        }
    }
}

/**
 * La casilla: cuadrada, solo icono. Encendida en su color de grupo; apagada gris, tenue y tachada.
 * Al tocar rebota (se hunde y vuelve con muelle). Mantener pulsado solo pregunta qué es.
 */
@Composable
private fun RowScope.QuickToggleTile(toggle: QuickToggle, onTap: () -> Unit, onLongPress: () -> Unit) {
    val checked = toggle.checked
    val lit = checked != false
    val accent = toggle.accent
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.86f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tilePress"
    )
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1f)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(
                if (lit) Brush.verticalGradient(listOf(accent.copy(alpha = 0.34f), accent.copy(alpha = 0.14f)))
                else Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.03f)))
            )
            .border(
                width = if (lit) 1.5.dp else 1.dp,
                color = if (lit) accent.copy(alpha = 0.85f) else Color.White.copy(alpha = 0.10f),
                shape = shape
            )
            .combinedClickable(
                interactionSource = press,
                indication = null,
                role = if (checked == null) Role.Button else Role.Switch,
                onLongClick = onLongPress,
                onClick = onTap,
            )
            // El dibujo ya no lleva palabra: el nombre y el estado los dice la semántica.
            .semantics {
                contentDescription = toggle.label
                if (checked != null) {
                    stateDescription = if (checked) "Activado" else "Desactivado"
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = if (lit) 1f else 0.42f }
                .drawWithContent {
                    drawContent()
                    // El tachado: una barra diagonal con filo oscuro, legible sobre cualquier icono.
                    if (!lit) {
                        val a = Offset(size.width * 0.12f, size.height * 0.12f)
                        val b = Offset(size.width * 0.88f, size.height * 0.88f)
                        drawLine(Color(0xFF0E1224), a, b, strokeWidth = size.width * 0.20f, cap = StrokeCap.Round)
                        drawLine(Color(0xFFFF8A80), a, b, strokeWidth = size.width * 0.10f, cap = StrokeCap.Round)
                    }
                },
            contentAlignment = Alignment.Center
        ) { toggle.icon() }
        toggle.badge?.let { badge ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(5.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(badge)
                    .border(1.5.dp, Color(0xFF12172E), CircleShape)
            )
        }
    }
}

/* (RETIRADO 2026-09-07b, P-225) `NotificationsSwitchRow`: la fila de AVISOS con su DOBLE PUERTA
 * —permiso del sistema y preferencia de la app—. La regla que defendía sigue viva palabra por
 * palabra dentro de [QuickSettingsGrid]: lo que se pinta es la CONJUNCIÓN de las dos puertas, y
 * tocar el interruptor sin permiso lo PIDE en vez de fingir que se ha encendido algo. Un interruptor
 * «encendido» que no enciende nada es peor que no tenerlo (P-166 + P-165). */

/* (RETIRADO 2026-09-23) `SettingsSectionHeader`: el rótulo de sección de Ajustes y de «Más ajustes».
 * Ajustes pasó a solo iconos y «Más ajustes» a tarjetas, y ninguno de los dos tiene ya secciones. */

/* (RETIRADO 2026-09-07b, P-225) `SwitchRow`: la fila de ancho completo que usaban «Reducir
 * efectos» y los avisos. Nació de P-12 —«gobierna TODO el juego, no puede ser un chip a medias»— y
 * hoy ninguno de los seis interruptores es un chip a medias: en la rejilla todos miden lo mismo, así
 * que la distinción que justificaba la fila ancha dejó de existir. */

/**
 * Icono Lottie reutilizable para los botones de Ajustes. `loop`=true reproduce en bucle (idle vivo);
 * false lo deja en el primer frame. Tamaño fijo — el layout que lo contiene decide el hueco.
 */
@Composable
private fun SettingLottieIcon(
    rawRes: Int,
    modifier: Modifier = Modifier,
    loop: Boolean = true
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = if (loop) LottieConstants.IterateForever else 1,
        isPlaying = loop
    )
    LottieAnimation(
        composition = composition,
        // Quieto = pose final, pero en 0,98 y no en 1: el fotograma final (op) cae FUERA del rango
        // de las capas y el icono salía vacío — invisible sin texto al lado (2026-09-23).
        progress = { if (loop) progress else 0.98f },
        modifier = modifier
    )
}

/* (RETIRADO 2026-09-07b, P-225) `ToggleChip`: el chip horizontal (icono + etiqueta + punto de
 * estado) que iba en pareja en Ajustes. Su casilla equivalente es [QuickToggleTile]. De él sobrevive
 * lo que importaba: `toggleable` con `Role.Switch` —un botón cuyo único dato es su estado tiene que
 * DECIR su estado— y el rótulo que ENCOGE antes que cortarse. */

@Composable
fun SettingsOverlay(
    musicVolume: Float,
    sfxVolume: Float,
    vibrationEnabled: Boolean,
    tiltEnabled: Boolean,
    isMuted: Boolean,
    reducedFx: Boolean,
    notificationsEnabled: Boolean = true,
    onMusicVolume: (Float) -> Unit,
    onSfxVolume: (Float) -> Unit,
    onSfxPreview: () -> Unit,
    onToggleVibration: () -> Unit,
    onToggleTilt: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleReducedFx: () -> Unit,
    onToggleNotifications: () -> Unit = {},
    onDismiss: () -> Unit,
    onConnectGoogle: () -> Unit = {},
    onLogOut: () -> Unit = {},
    onOpenMoreSettings: () -> Unit = {},
    // (Auditoría 2026-10-01) Fuera `onResetProgress`, `onPrivacyPolicy`, `onDeleteAccount`,
    // `currentName` y `onChangeName`: desde el 2026-09-23 el nombre, la privacidad y la zona de
    // peligro viven en «Más ajustes» ([MoreSettingsOverlay]). Aquí quedaban sus tres ventanas de
    // confirmación sin ningún botón que las abriera — y la de borrar cuenta, que era la COMPLETA,
    // no era la que veía el jugador. Dos copias de un dibujo: queda una.
) {
    // Botón atrás: cierra el panel (comportamiento de modal) en vez de propagar al juego.
    androidx.activity.compose.BackHandler(enabled = true) { onDismiss() }
    var showLogoutConfirm by remember { mutableStateOf(false) }

    val cardScale     = remember { Animatable(0.85f) }
    val backdropAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { backdropAlpha.animateTo(0.85f, tween(250)) }
        cardScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }

    Box(
        modifier = Modifier
            // Scrim EDGE-TO-EDGE: oscurece TODO (incluido el navbar) y bloquea toques detrás.
            .fillMaxSize()
            // El fundido y el rebote de entrada se leen al DIBUJAR: leídos en composición
            // recomponían el panel entero en cada fotograma de la apertura (auditoría 2026-10-01).
            .drawBehind { drawRect(Color.Black.copy(alpha = backdropAlpha.value)) }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                // Solo la TARJETA respeta barras/recortes y deja aire arriba/abajo, sin cortarse.
                .safeDrawingPadding()
                .padding(vertical = 24.dp)
                .graphicsLayer { scaleX = cardScale.value; scaleY = cardScale.value }
                // Compacta (2026-09-23): 78 % del ancho con tope de 340 dp — en un móvil de 360 dp
                // quedan casillas de ~58 dp, y en una tableta no se estira hasta ser un cartel.
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.78f)
                // Traga el toque para que NO llegue al scrim de detrás (que cierra el panel).
                // Con `clickable {}` la tarjeta entera se anunciaba como un botón sin nombre y
                // TalkBack ofrecía "activar" una acción que no hace nada; `detectTapGestures` come
                // el gesto sin añadir semántica (mismo idioma que el blocker de AppNavigation).
                .pointerInput(Unit) { detectTapGestures { } }
                .background(
                    brush = Brush.sweepGradient(
                        colors = listOf(Color(0xFF4FC3F7), Color(0xFF7C4DFF), Color(0xFF4FC3F7))
                    ),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(4.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(22.dp))
                    // Si el contenido excede la altura disponible (pantallas cortas / muchas
                    // opciones con cuenta vinculada), la tarjeta se limita al área visible y hace scroll.
                    .verticalScroll(rememberScrollState())
                    // 28 dp de margen alrededor de un panel que ya es estrecho gastaba ancho útil y
                    // estiraba la tarjeta a lo alto sin añadir nada. 18 dp deja el mismo aire visual.
                    .padding(horizontal = 18.dp, vertical = 18.dp)
            ) {
                // Cabecera compacta: título a la izquierda + X de cierre a la derecha (sin botón
                // "Cerrar" gigante al final → ahorra altura y da un look premium).
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "AJUSTES",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF7C4DFF),
                        letterSpacing = 2.sp
                    )
                    Box(
                        modifier = Modifier
                            // P-09: el dibujo sigue midiendo 30 dp, pero el ÁREA TÁCTIL se reserva
                            // a 48 dp (mínimo de Material/WCAG). Cerrar Ajustes era el botón más
                            // pequeño de la app y se fallaba el toque con el pulgar.
                            .minimumInteractiveComponentSize()
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button
                            ) { onDismiss() }
                            // Sin esto TalkBack leía el glifo "✕" (que además pronuncia como
                            // "multiplicación"): el único botón para salir de Ajustes era ilegible.
                            .semantics { contentDescription = "Cerrar ajustes" },
                        contentAlignment = Alignment.Center
                    ) {
                        RawImage(R.raw.ic_close, Modifier.size(15.dp).alpha(0.75f))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // SOLO ICONOS (2026-09-23): los seis interruptores de la pausa + la cuenta y «Más
                // ajustes» en una rejilla de 4×2. La cuenta ya no es una fila con nombre y botón:
                // es una casilla con la «G» y un punto verde cuando está vinculada. Tocarla vincula
                // (sin cuenta) o pide confirmar el cierre de sesión (con cuenta) — la confirmación
                // explica qué pasa, así que la casilla no necesita palabras. `onLogOut` reinicia el
                // juego: no puede ocurrir por un roce.
                val googleId by com.cukiti.teikafusion.GoogleManager.googleId.collectAsState()
                val googleDisplayName by com.cukiti.teikafusion.GoogleManager.displayName.collectAsState()
                val linked = googleId != null
                val accountName = googleDisplayName?.takeIf { it.isNotBlank() }
                val cyan = Color(0xFF4DB8FF)
                val extras = listOf(
                    QuickToggle(
                        label = if (linked) (accountName ?: "Google vinculada") else "Continuar con Google",
                        checked = null,
                        accent = if (linked) Color(0xFF69F0AE) else Color.White,
                        badge = if (linked) Color(0xFF69F0AE) else null,
                        onToggle = { if (linked) showLogoutConfirm = true else onConnectGoogle() },
                    ) {
                        Box(
                            Modifier.size(30.dp).clip(CircleShape).background(Color.White),
                            contentAlignment = Alignment.Center
                        ) { SettingLottieIcon(rawRes = R.raw.ic_google, modifier = Modifier.size(20.dp), loop = false) }
                    },
                    QuickToggle(
                        label = "Más ajustes",
                        checked = null,
                        accent = cyan,
                        onToggle = onOpenMoreSettings,
                    ) {
                        Text("•••", fontSize = 18.sp, fontWeight = FontWeight.Black, color = cyan, letterSpacing = 1.sp)
                    },
                )
                QuickSettingsGrid(
                    musicVolume       = musicVolume,
                    sfxVolume         = sfxVolume,
                    vibrationEnabled  = vibrationEnabled,
                    notificationsEnabled = notificationsEnabled,
                    tiltEnabled       = tiltEnabled,
                    isMuted           = isMuted,
                    reducedFx         = reducedFx,
                    onMusicVolume     = onMusicVolume,
                    onSfxVolume       = onSfxVolume,
                    onSfxPreview      = onSfxPreview,
                    onToggleVibration = onToggleVibration,
                    onToggleTilt      = onToggleTilt,
                    onToggleMute      = onToggleMute,
                    onToggleReducedFx = onToggleReducedFx,
                    onToggleNotifications = onToggleNotifications,
                    columns = 4,
                    extras = extras,
                )

                Spacer(modifier = Modifier.height(12.dp))

                // La versión estaba escrita a mano ("v1.0.0") y llevaba 21 publicaciones mintiendo.
                // Un número de versión falso convierte cualquier reporte de bug en una adivinanza.
                Text(
                    text = "Teika Fusion  v${com.cukiti.teikafusion.BuildConfig.VERSION_NAME}",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.35f),
                )
            }
        }
    }

    // ── Confirmaciones (bloque 23, 2026-07-31) ────────────────────────────────────────────────
    // Van AQUÍ, al final del cuerpo, y no arriba como estaban: al dejar de ser `AlertDialog` ya no
    // tienen ventana propia, así que el ORDEN DE COMPOSICIÓN es lo que las pone por encima del panel
    // (y su BackHandler, registrado después, es el que gana el botón atrás).
    if (showLogoutConfirm) {
        ConfirmOverlay(
            title = "Cerrar sesión",
            message = "Tu progreso se respaldará en la nube antes de salir. El juego se reiniciará " +
                "y podrás iniciar sesión con otra cuenta. ¿Continuar?",
            confirmLabel = "Cerrar sesión",
            danger = true,
            onConfirm = {
                showLogoutConfirm = false
                onLogOut()
            },
            onDismiss = { showLogoutConfirm = false }
        )
    }

}

/**
 * Panel secundario de cuenta y soporte. Se mantiene como overlay Compose (no Dialog) para no
 * devolver las barras del sistema en el modo inmersivo.
 *
 * [onPrivacyOptions] es **`null` fuera del EEE y del Reino Unido**, y entonces su fila no se dibuja.
 * No es una preferencia de diseño: el formulario de UMP solo existe donde el SDK ha decidido que
 * hacía falta consentimiento (`ConsentManager.isPrivacyOptionsRequired`), así que enseñar la fila a
 * un jugador de fuera abriría una ventana vacía. Donde SÍ aplica es obligatoria: publicar un mensaje
 * de consentimiento con «Gestionar opciones» obliga a que el jugador pueda cambiar su decisión más
 * tarde desde la app, y sin esta fila no tenía por dónde. Ojo con confundirla con «Privacidad», que
 * es otra cosa: esa abre la política en el navegador y no toca el consentimiento publicitario.
 */
@Composable
fun MoreSettingsOverlay(
    currentName: String,
    onChangeName: (String) -> Unit,
    onPrivacyPolicy: () -> Unit,
    onPrivacyOptions: (() -> Unit)?,
    onTermsOfService: () -> Unit,
    onContact: () -> Unit,
    onShare: () -> Unit,
    onResetProgress: () -> Unit,
    onDeleteAccount: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.activity.compose.BackHandler(enabled = true) { onDismiss() }
    val googleId by com.cukiti.teikafusion.GoogleManager.googleId.collectAsState()
    val linked = googleId != null
    var showNameDialog by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }
    var nameInput by remember(showNameDialog) { mutableStateOf(currentName) }
    var deleteConfirmInput by remember(showDeleteAccount) { mutableStateOf("") }

    var dangerOpen by remember { mutableStateOf(false) }

    // (REDISEÑO 2026-09-23, «visualmente premium») El marco de las ventanas de juego: cinta arriba,
    // perfil en una tarjeta, legal y ayuda en una rejilla de 2×2 con icono, y lo destructivo PLEGADO
    // en una zona de peligro que hay que abrir a propósito — antes «Eliminar mi cuenta» era una fila
    // más entre «Compartir» y «Restablecer», a un roce de distancia.
    GameDialogFrame(
        title = "Más ajustes",
        accent = Color(0xFF4FC3F7),
        accentDeep = Color(0xFF2A4FB8),
        onOutsideTap = onDismiss,
        onClose = onDismiss,
    ) {
        // PERFIL: el nombre del ranking, grande, con su lápiz. Solo con cuenta (sin ella no hay ranking).
        if (linked) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF2E4A8C), Color(0xFF263163))))
                    .border(1.5.dp, Color(0xFF4DB8FF).copy(alpha = 0.55f), RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button
                    ) { showNameDialog = true }
                    .semantics(mergeDescendants = true) {
                        contentDescription = "Nombre: ${currentName.ifBlank { "sin configurar" }}. Toca para cambiarlo"
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TU NOMBRE", fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp, color = Color(0xFF8FD3FF))
                    Text(
                        currentName.ifBlank { "Configura tu nombre" },
                        fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) { RawImage(R.raw.ic_edit, Modifier.size(20.dp)) }
            }
            Spacer(Modifier.height(12.dp))
        }

        // LEGAL Y AYUDA: cuatro tarjetas con icono en rejilla, en vez de cuatro filas de lista.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoreSettingsCard("Privacidad", Color(0xFF4DB8FF), Modifier.weight(1f), onPrivacyPolicy) {
                SettingLottieIcon(R.raw.ic_privacy, Modifier.size(26.dp), loop = false)
            }
            MoreSettingsCard("Condiciones", Color(0xFFB48CFF), Modifier.weight(1f), onTermsOfService) {
                Text("▤", fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color(0xFFB48CFF))
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MoreSettingsCard("Contáctanos", Color(0xFF5CFFB0), Modifier.weight(1f), onContact) {
                Text("@", fontSize = 21.sp, fontWeight = FontWeight.Black, color = Color(0xFF5CFFB0))
            }
            MoreSettingsCard("Compartir", Color(0xFFFFD166), Modifier.weight(1f), onShare) {
                Text("↗", fontSize = 21.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD166))
            }
        }
        if (onPrivacyOptions != null) {
            Spacer(Modifier.height(10.dp))
            MoreSettingsRow("◐", "Opciones de privacidad", Color(0xFF4DB8FF), onClick = onPrivacyOptions)
        }

        // ZONA DE PELIGRO, plegada.
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button
                ) { dangerOpen = !dangerOpen }
                .semantics { contentDescription = if (dangerOpen) "Ocultar zona de peligro" else "Mostrar zona de peligro" }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                "ZONA DE PELIGRO", fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.4.sp,
                color = Color(0xFFFF8A80).copy(alpha = 0.8f)
            )
            Spacer(Modifier.width(6.dp))
            Text(if (dangerOpen) "▴" else "▾", fontSize = 12.sp, color = Color(0xFFFF8A80).copy(alpha = 0.8f))
        }
        AnimatedVisibility(visible = dangerOpen) {
            Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                MoreSettingsRow(
                    "↻", "Restablecer progreso", Color(0xFFFFB74D),
                    subtitle = "Conserva tu cuenta y tus compras"
                ) { showResetConfirm = true }
                if (linked) {
                    MoreSettingsRow("!", "Eliminar mi cuenta", Color(0xFFE53935)) {
                        showDeleteAccount = true
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Text(
            "Teika Fusion  v${com.cukiti.teikafusion.BuildConfig.VERSION_NAME}",
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.35f)
        )
    }

    // (RETIRADO 2026-09-07) Aquí vivía «Acerca de nosotros»: una fila del panel que abría un
    // párrafo de folleto sobre el juego. Lo cuenta la ficha de la tienda; aquí solo ocupaba sitio.
    if (showNameDialog) {
        ConfirmOverlay(
            title = "Cambiar nombre",
            confirmLabel = "Guardar",
            confirmEnabled = nameInput.trim().isNotEmpty(),
            onConfirm = { onChangeName(nameInput.trim()); showNameDialog = false },
            onDismiss = { showNameDialog = false }
        ) {
            OutlinedTextField(
                value = nameInput,
                onValueChange = {
                    nameInput = it.replace("\n", "").take(com.cukiti.teikafusion.model.MAX_DISPLAY_NAME_LENGTH)
                },
                singleLine = true,
                placeholder = { Text("Tu nombre en el ranking") },
                supportingText = { Text("${nameInput.length}/${com.cukiti.teikafusion.model.MAX_DISPLAY_NAME_LENGTH}") }
            )
        }
    }
    if (showResetConfirm) {
        ConfirmOverlay(
            title = "Restablecer progreso",
            message = "Se borrarán tus niveles, puntuaciones, compañeros, mejoras de habilidad y cristales. " +
                "Tus monedas, tus compras y los usos de power-ups se conservan. Esta acción no se puede deshacer.",
            confirmLabel = "Borrar todo",
            danger = true,
            onConfirm = { showResetConfirm = false; onResetProgress(); onDismiss() },
            onDismiss = { showResetConfirm = false }
        )
    }
    // (RETIRADO 2026-09-07) La confirmación de «Cerrar sesión» se fue con su fila: el verbo vive en
    // la fila de la cuenta del panel principal, con su propia confirmación. Tenerlo en dos sitios
    // era tener dos ventanas idénticas que mantener a la vez.
    if (showDeleteAccount) {
        val confirmWord = "ELIMINAR"
        val canDelete = deleteConfirmInput.trim().equals(confirmWord, ignoreCase = true)
        ConfirmOverlay(
            title = "Eliminar mi cuenta",
            // La lista completa (venía de la copia muerta de Ajustes): quien borra su cuenta tiene
            // que saber QUÉ se borra, y Play pide decirlo.
            message = "Se eliminarán PARA SIEMPRE tu cuenta y todos tus datos:\n" +
                "•  tu progreso guardado en la nube\n" +
                "•  tu entrada del ranking\n" +
                "•  tu perfil de jugador\n" +
                "•  tu identidad de acceso de Google\n\n" +
                "Esto NO se puede deshacer y no podremos recuperarlo.",
            confirmLabel = "Eliminar cuenta",
            danger = true,
            confirmEnabled = canDelete,
            onConfirm = { showDeleteAccount = false; onDeleteAccount() },
            onDismiss = { showDeleteAccount = false }
        ) {
            OutlinedTextField(
                value = deleteConfirmInput,
                onValueChange = { deleteConfirmInput = it.replace("\n", "").take(16) },
                singleLine = true,
                placeholder = { Text(confirmWord) },
                supportingText = { Text("Escribe $confirmWord para confirmar") }
            )
        }
    }
}

/**
 * Tarjeta de la rejilla de «Más ajustes»: icono en su burbuja de color y la palabra debajo. Rebota
 * al pulsar (el mismo muelle que las casillas de Ajustes).
 */
@Composable
private fun MoreSettingsCard(
    title: String,
    tint: Color,
    modifier: Modifier,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.9f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "moreCard"
    )
    Column(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.18f), tint.copy(alpha = 0.06f))))
            .border(1.5.dp, tint.copy(alpha = 0.40f), RoundedCornerShape(16.dp))
            .clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = title }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.Black, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun MoreSettingsRow(
    icon: String,
    title: String,
    tint: Color,
    subtitle: String? = null,
    onClick: () -> Unit
) = MoreSettingsRowContent(title, subtitle, tint, onClick) {
    Text(icon, fontSize = 17.sp, fontWeight = FontWeight.Black, color = tint)
}

/**
 * Fila de «Más ajustes». El subtítulo es OPCIONAL y por defecto no existe: una fila de una sola
 * línea mide 42 dp y una de dos, 62 — con nueve filas, esa diferencia es la que decidía si el panel
 * cabía en la pantalla o pedía scroll. Solo se escribe cuando aporta un dato que el título no lleva.
 */
@Composable
private fun MoreSettingsRowContent(
    title: String,
    subtitle: String?,
    tint: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.24f), RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (subtitle != null) {
                Text(
                    subtitle, fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text("›", fontSize = 20.sp, color = tint)
    }
    Spacer(Modifier.height(6.dp))
}


// ─── Objectives Reminder — boss derrotado pero faltan estrellas ───────────────

@Composable
fun ObjectivesReminderOverlay(
    objective: LevelObjective?,
    targetsCollected: Map<TargetColor, Int>,
    onContinue: () -> Unit
) {
    val alpha  = remember { Animatable(0f) }
    val slideY = remember { Animatable(60f) }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { alpha.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
            launch { slideY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(alpha.value)
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(0, slideY.value.dp.roundToPx()) }
                .fillMaxWidth(0.86f)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF1A2240), Color(0xFF0D1528))
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(2.dp, Color(0xFFFFD700).copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "¡Mega Teika Derrotado!",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFFFFD700),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    // El copy DEBE decir lo que este nivel pide de verdad: desde 2026-07-31b hay
                    // niveles que no piden estrellas, y hablarles de estrellas mandaría al jugador a
                    // buscar algo que su tablero no tiene (regla de la it. 41: nada de prometer lo
                    // que el juego no tiene).
                    text = when (objective?.goal) {
                        com.cukiti.teikafusion.model.LevelGoal.LINES ->
                            "Pero aún te faltan líneas por romper.\nCompleta el objetivo para avanzar de nivel."
                        com.cukiti.teikafusion.model.LevelGoal.CLEAR ->
                            "Pero el tablero aún tiene demasiados bloques.\nDespéjalo para avanzar de nivel."
                        com.cukiti.teikafusion.model.LevelGoal.SURVIVE ->
                            "Pero aún tienes que aguantar más fichas.\nCompleta el objetivo para avanzar de nivel."
                        else ->
                            "Pero aún te faltan estrellas por recoger.\nCompleta los objetivos para avanzar de nivel."
                    },
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                if (objective != null) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        objective.required.entries.forEach { (color, needed) ->
                            val collected = targetsCollected[color] ?: 0
                            val done      = collected >= needed
                            val resId = when (color) {
                                TargetColor.RED    -> R.drawable.target_red
                                TargetColor.BLUE   -> R.drawable.target_blue
                                TargetColor.PURPLE -> R.drawable.target_purple
                                TargetColor.PINK   -> R.drawable.target_pink
                            }
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .background(
                                            if (done) Color(0xFF1B5E20).copy(alpha = 0.6f)
                                            else Color.White.copy(alpha = 0.08f),
                                            RoundedCornerShape(10.dp)
                                        )
                                        .border(
                                            1.dp,
                                            if (done) Color(0xFF69F0AE) else Color.White.copy(alpha = 0.2f),
                                            RoundedCornerShape(10.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        painter = painterResource(resId),
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.size(52.dp).alpha(if (done) 0.5f else 1f)
                                    )
                                    if (done) {
                                        RawImage(R.raw.ic_check, Modifier.size(22.dp))
                                    }
                                }
                                Text(
                                    text = "$collected/$needed",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (done) Color(0xFF69F0AE) else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4DB8FF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "¡Seguir recogiendo estrellas!",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

// ─── Actualización obligatoria (Remote Config) ───────────────────────────────

/**
 * Tapa el juego entero cuando la versión instalada está por debajo de
 * `min_supported_version_code`. Es la red de seguridad para el peor caso: una versión publicada con
 * un bug que corrompe guardados o rompe el respaldo en la nube.
 *
 * Deliberadamente **sin salida**: no hay botón de cerrar ni BackHandler que lo descarte, porque el
 * escenario que lo levanta es justo aquel en el que seguir jugando hace daño. El único camino es
 * actualizar. Se compone como última capa de [AppNavigation], por encima incluso del navbar.
 */
@Composable
fun UpdateRequiredOverlay(
    message: String,
    onOpenStore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF080C1E).copy(alpha = 0.97f))
            // Traga cualquier toque: nada de debajo puede recibirlo.
            .pointerInput(Unit) { detectTapGestures { } }
            .safeDrawingPadding()
            .padding(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Text("⬆", fontSize = 52.sp, color = Color(0xFF4DB8FF))
            Text(
                "Actualiza Teika Fusion",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                message,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.verticalGradient(listOf(Color(0xFFFFD700), Color(0xFFFF8F00)))
                    )
                    .clickable { onOpenStore() }
                    .padding(horizontal = 32.dp, vertical = 14.dp)
            ) {
                Text(
                    "ABRIR GOOGLE PLAY",
                    color = Color(0xFF1A1208),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

/**
 * Celebración de subida de rango (V-03). Capa global: toca o espera ~4 s para cerrar.
 */
@Composable
fun RankUpOverlay(
    rank: com.cukiti.teikafusion.model.RankInfo,
    onDismiss: () -> Unit
) {
    val rankColor = Color(rank.colorArgb)
    LaunchedEffect(rank.name) {
        kotlinx.coroutines.delay(4000)
        onDismiss()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
            .semantics(mergeDescendants = true) {
                contentDescription = "Nuevo rango: ${rank.name}. Toca para cerrar."
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(rankColor.copy(alpha = 0.35f), Color(0xFF12182E))
                    )
                )
                .border(
                    2.dp,
                    Brush.horizontalGradient(listOf(rankColor, Color.White.copy(alpha = 0.5f), rankColor)),
                    RoundedCornerShape(22.dp)
                )
                .padding(horizontal = 28.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RawImage(R.raw.ic_star, Modifier.size(48.dp))
            Text(
                "¡NUEVO RANGO!",
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = rankColor,
                letterSpacing = 1.2.sp
            )
            Text(
                rank.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                "Tu progreso te ha elevado. ¡Sigue fusionando!",
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
            Text(
                "Toca para continuar",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.45f)
            )
        }
    }
}
