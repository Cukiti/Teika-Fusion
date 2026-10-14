package com.cukiti.teikafusion.ui

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.NamedTeika
import com.cukiti.teikafusion.model.NamedTeikaRegistry
import com.cukiti.teikafusion.model.PlayerRank
import com.cukiti.teikafusion.model.TeikaType
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.ui.theme.AppBackground
import com.cukiti.teikafusion.ui.theme.BoardBackground
import com.cukiti.teikafusion.ui.theme.NeonBlue
import com.cukiti.teikafusion.ui.theme.NeonOrange
import com.cukiti.teikafusion.ui.theme.NeonPurple
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/** Dorado de las VIDAS INFINITAS. El mismo que usa la tarjeta de la tienda: el jugador debe
 *  reconocer de un vistazo que el chip de Inicio y lo que compró son la misma cosa. */
private val InfiniteLivesGold = Color(0xFFFFD700)
/** El mismo dorado, tirando a ámbar: el chip de vidas infinitas a punto de caducar (P-211). */
private val InfiniteLivesEnding = Color(0xFFFFA23A)

// ── Decoraciones ──────────────────────────────────────────────────────────────


/**
 * Tarjeta de arte del bioma actual en Home. Muestra la escena temática del bioma ([BiomeBackdrop])
 * del nivel seleccionado, o un
 * Teika de adorno en los niveles de intro (sin bioma). Es clickable → abre el picker de mundos. Late
 * suavemente e invita con "Toca para cambiar".
 */
@Composable
private fun BiomeArtCard(level: Int, onClick: () -> Unit) {
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        if (pressed) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "artPress"
    )
    val biome = com.cukiti.teikafusion.model.LevelMap.biomeFor(level)
    val hasBiome = biome != com.cukiti.teikafusion.model.BiomeType.NONE
    val accent = levelAccentColor(level)

    Box(
        modifier = Modifier
            .fillMaxWidth(0.82f)
            .aspectRatio(1.6f)
            .scale(pressScale)
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF141B30), Color(0xFF0A0F22))))
            .border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
            .clickable(interactionSource = press, indication = null, onClick = onClick, role = Role.Button)
            // La tarjeta es puro arte: sin descripción el lector solo diría "Cambiar" (el chip de la
            // esquina) y no qué mundo está seleccionado, que es toda la información que da.
            .semantics(mergeDescendants = true) {
                val world = if (hasBiome) biome.displayName else "Aventura"
                // El número que VE el jugador (sin las 7 lecciones), no el interno: TalkBack decía
                // «nivel 110» con «NIVEL 103» en pantalla (análisis a fondo, 2026-10-01).
                val shown = com.cukiti.teikafusion.model.LevelMap.displayNumber(level)
                contentDescription = "$world, nivel $shown. Toca para cambiar de mundo"
            },
        contentAlignment = Alignment.Center
    ) {
        when {
            hasBiome -> BiomeBackdrop(
                primary = biome,
                next = com.cukiti.teikafusion.model.BiomeRegistry.nextBiome(level),
                transition = com.cukiti.teikafusion.model.BiomeRegistry.transitionProgress(level),
                // Le pasamos un CrackController propio para que el fondo (bioma cristal) NO capture el
                // tap con su efecto de grieta; así el clic llega a la tarjeta y reabre el picker.
                crackController = remember { CrackController() },
                modifier = Modifier.fillMaxSize()
            )
            else -> TeikaIcon(type = TeikaType.MEGA_TEIKA, modifier = Modifier.size(120.dp).alpha(0.85f))
        }

        // Chip "Toca para cambiar" (esquina inferior derecha).
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("↔", fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f))
            Text("Cambiar", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

// ── Player ID overlay ─────────────────────────────────────────────────────────

@Composable
private fun PlayerIdOverlay(playerId: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboard.current
    var copied by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val copyColor by animateColorAsState(
        targetValue = if (copied) Color(0xFF43A047) else NeonBlue,
        animationSpec = tween(300),
        label = "copyColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .background(Color(0xFF1A2040), RoundedCornerShape(20.dp))
                .border(1.dp, NeonBlue.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("ID de jugador", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(
                "Comparte este ID para recibir monedas o cambios de nivel",
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = playerId.ifBlank { "—" },
                    color = NeonBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(copyColor.copy(alpha = 0.18f))
                    .border(1.dp, copyColor, RoundedCornerShape(12.dp))
                    // Sin ID (sin sesión todavía) no hay nada que copiar: antes copiaba un texto vacío.
                    .clickable(enabled = playerId.isNotBlank(), role = Role.Button) {
                        // setClipEntry es suspend (API nueva de LocalClipboard) → dentro del scope.
                        scope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(ClipData.newPlainText("Player ID", playerId))
                            )
                            copied = true
                            delay(2000); copied = false
                        }
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (copied) "✓  ¡Copiado!" else "Copiar ID",
                    color = copyColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = Color.White.copy(alpha = 0.55f))
            }
        }
    }
}

// ── Pantalla principal ────────────────────────────────────────────────────────

/**
 * Tarjeta de "anzuelo de progresión": muestra al SIGUIENTE compañero por desbloquear como silueta
 * misteriosa + barra de progreso al nivel donde aparece. Crea una meta siempre visible ("Estás a N
 * niveles de desbloquear [X]") que tira del jugador hacia la próxima recompensa de colección.
 *
 * @param maxLevel    nivel máximo alcanzado por el jugador (gameState.maxUnlockedLevel).
 * @param claimedIds  ids de personajes ya reclamados (para saltar a la próxima meta).
 */
@Composable
private fun NextCompanionProgress(
    maxLevel: Int,
    claimedIds: Set<String>,
    modifier: Modifier = Modifier
) {
    // Próxima meta = compañero NO reclamado de menor nivel de desbloqueo.
    val next: NamedTeika = remember(claimedIds) {
        NamedTeikaRegistry.all.filter { it.id !in claimedIds }.minByOrNull { it.unlockLevel }
    } ?: return  // todos reclamados → sin tarjeta

    // Rango de la barra: del desbloqueo del compañero anterior al de este.
    val prevUnlock = remember(next.id) {
        NamedTeikaRegistry.all.map { it.unlockLevel }.filter { it < next.unlockLevel }.maxOrNull() ?: 1
    }
    val reached = maxLevel >= next.unlockLevel
    val levelsLeft = (next.unlockLevel - maxLevel).coerceAtLeast(0)
    val frac = if (reached) 1f
               else ((maxLevel - prevUnlock).toFloat() / (next.unlockLevel - prevUnlock).coerceAtLeast(1)).coerceIn(0f, 1f)
    val animFrac by animateFloatAsState(frac, tween(700, easing = FastOutSlowInEasing), label = "compProg")

    // Pulso de "¡disponible!" para crear urgencia (FOMO) cuando ya se puede reclamar.
    // Solo late si (a) se puede reclamar de verdad —antes latía siempre, aunque `pulseA` ni se
    // usara— y (b) la pestaña está a la vista. Ver [LocalTabActive].
    val pulseA: Float = if (reached && LocalTabActive.current) {
        rememberInfiniteTransition(label = "compPulse").animateFloat(
            initialValue = 0.35f, targetValue = 0.85f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "compPulseA"
        ).value
    } else 0.35f   // el valor inicial: al volver a la pestaña, la animación arranca sin salto
    val borderColor = if (reached) Color(0xFFFFD54F).copy(alpha = pulseA)
                      else Color.White.copy(alpha = 0.12f)

    Row(
        modifier = modifier
            .fillMaxWidth(0.82f)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black.copy(alpha = 0.28f))
            .border(1.5.dp, borderColor, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Silueta misteriosa del compañero
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0E1530).copy(alpha = 0.85f)),
            contentAlignment = Alignment.Center
        ) {
            if (reached) {
                // Disponible → se revela parcialmente, latiendo (te invita a reclamarlo).
                TeikaIcon(type = next.teikaType, modifier = Modifier.size(44.dp).alpha(0.55f + pulseA * 0.45f))
            } else {
                TeikaIcon(type = next.teikaType, modifier = Modifier.size(44.dp).alpha(0.16f))
                Text("?", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color.White.copy(alpha = 0.8f))
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = if (reached) "¡${next.name} te espera!" else "Próximo compañero",
                fontSize = 13.sp, fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD54F), letterSpacing = 0.5.sp
            )

            // Barra de progreso
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animFrac)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(5.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF4DB8FF), Color(0xFFFFD54F))
                            )
                        )
                )
            }

            Text(
                text = if (reached)
                    "¡Disponible! Completa el nivel ${com.cukiti.teikafusion.model.LevelMap.displayNumber(next.unlockLevel)} para reclamarlo"
                else
                    "A $levelsLeft ${if (levelsLeft == 1) "nivel" else "niveles"} de desbloquear a ${next.name}",
                fontSize = 11.sp, color = Color.White.copy(alpha = 0.75f),
                lineHeight = 14.sp
            )
        }
    }
}

@Composable
fun HomeScreen(
    gameState: GameState,
    claimedCharacterIds: Set<String> = emptySet(),
    streak: Int = 0,
    // Racha PERSISTENTE de victorias de niveles + logros (2026-07-20).
    winStreak: Int = 0,
    dailyStreakCelebration: Int? = null,
    onConsumeStreakCelebration: () -> Unit = {},
    lives: Int = 5,
    /** Instante (epoch ms) en que caducan las vidas infinitas. `<= ahora` = no hay. */
    infiniteLivesUntil: Long = 0L,
    /**
     * Abre la ventana de VIDAS (2026-09-06). El chip del corazón era un indicador **mudo**: enseñaba
     * el número y no llevaba a ninguna parte, así que para recargar había que saber que eso vivía
     * dentro de una pestaña de la tienda. Ahora el sitio donde miras cuando te quedas sin vidas es
     * el sitio donde se arreglan.
     */
    onOpenLives: () -> Unit = {},
    /** El bloque equipado: las fichas de la lluvia de fondo caen con él. */
    activeCosmetic: String? = null,
    avatarType: TeikaType = TeikaType.TEIKA_1,
    displayName: String = "",
    totalXp: Int = 0,
    /**
     * **P-227 · El puesto del jugador en el ranking global**, o 0 si todavía no se le conoce ninguno.
     * Lo enseña el carné del perfil, en el sitio que dejaron libre los avatares al pasar a una fila.
     */
    globalRank: Int = 0,
    /** P-437: cuándo se vio [globalRank] (epoch ms; 0 = sin fecha). */
    globalRankSeenAt: Long = 0L,
    selectedLevel: Int = 0,               // cursor de Home; define arte, nivel y nombre
    onOpenBiomePicker: () -> Unit = {},    // tap en el arte del bioma → picker de mundos
    onPlayPressed: () -> Unit = {},        // botón ¡JUGAR! → abre objetivo y compañero del nivel
    onOpenLeaderboard: () -> Unit = {},
    onOpenShop: () -> Unit = {},
    onAvatarSelected: (TeikaType) -> Unit = {},
    hasSavedGame: Boolean = false,
    savedGameLevel: Int = 0,              // nivel del intento sin terminar (0 = ninguno)
    onContinue: () -> Unit = {},          // botón CONTINUAR → retoma el tablero, SIN cobrar vida
    playerId: String = "",
    onOpenSettings: () -> Unit = {},
    onUiClick: () -> Unit = {},   // clic de UI (navbar) para el botón de perfil
    active: Boolean = true
) {
    // ¿Vidas infinitas AHORA? Hace falta un reloj: `infiniteLivesUntil` es un instante fijo y sin
    // latido el chip se quedaría dorado para siempre tras caducar. El reloj es el ÚNICO del juego
    // desde P-126 — vive en SharedUi junto a LocalTabActive, y solo corre con Inicio a la vista.
    // P-211 · LAS VIDAS INFINITAS AVISAN ANTES DE CADUCAR. Se compran por horas y hasta hoy la
    // cuenta atrás solo se veía abriendo la ventana de vidas: cuando se acababan, el jugador se
    // enteraba al quedarse sin vidas — el peor momento y la peor manera. Con menos de una hora por
    // delante, el chip deja de decir «∞» y dice cuánto queda, en ámbar. No hace falta ventana nueva:
    // el chip YA abre la ventana de vidas, que YA sabe ampliar (los packs se acumulan).
    val infiniteRemainingMs = rememberInfiniteLivesRemaining(infiniteLivesUntil, active)
    val infiniteLives = infiniteRemainingMs > 0L
    val infiniteEndingSoon = infiniteLives && infiniteRemainingMs < 60L * 60L * 1000L
    val infiniteRemainingLabel = if (!infiniteEndingSoon) "" else {
        val minutos = ((infiniteRemainingMs + 59_999L) / 60_000L).toInt().coerceAtLeast(1)
        "${minutos}m"
    }

    var showPlayerIdOverlay by remember { mutableStateOf(false) }
    var showProfileOverlay by remember { mutableStateOf(false) }
    var showAbandonWarning by remember { mutableStateOf(false) }
    var titleTapCount by remember { mutableIntStateOf(0) }
    val view = LocalView.current
    val headerAlpha = remember { Animatable(0f) }
    val titleScale  = remember { Animatable(0.82f) }
    val bottomAlpha = remember { Animatable(0f) }

    val ambientPhase = rememberAmbientPhase(active)


    LaunchedEffect(Unit) {
        launch { headerAlpha.animateTo(1f, tween(500)) }
        launch {
            delay(150)
            titleScale.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
        }
        launch {
            delay(450)
            bottomAlpha.animateTo(1f, tween(400))
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
        Box(modifier = Modifier.fillMaxSize()) {
            PremiumBackground(phase = { ambientPhase.value }, accent = Color(0xFF4DB8FF), modifier = Modifier.fillMaxSize())
            HomeFallingBackdrop(skin = BlockSkins.byId(activeCosmetic), modifier = Modifier.fillMaxSize())

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Top Bar (fijo) ────────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .alpha(headerAlpha.value),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón de perfil — izquierda (fijo). Solo el avatar: la etiqueta "Perfil"
                    // apretaba el avatar y quedaba ilegible, además de robar ancho que empujaba
                    // los chips de la derecha fuera de pantalla.
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(Color.Black.copy(alpha = 0.25f))
                            .border(2.dp, NeonBlue.copy(alpha = 0.5f), RoundedCornerShape(percent = 50))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button
                            ) { onUiClick(); showProfileOverlay = true }
                            .semantics { contentDescription = "Tu perfil" },
                        contentAlignment = Alignment.Center
                    ) {
                        TeikaIcon(type = avatarType, modifier = Modifier.size(48.dp))
                    }

                    // Espaciador flexible — absorbe el exceso de los chips de moneda
                    Spacer(modifier = Modifier.weight(1f))

                    // Chips de recursos (teikoins)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Wallet Teikoin
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                                                                                .background(Color.Black.copy(alpha = 0.20f), RoundedCornerShape(14.dp))
                                .clickable(role = Role.Button) { onOpenShop() }
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription =
                                        "${gameState.teikoins} Teikoins. Abrir la tienda"
                                }
                        ) {
                            RawImage(resId = R.raw.teikoin, modifier = Modifier.size(26.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = com.cukiti.teikafusion.model.AmountFormat.compact(gameState.teikoins),
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }

                        // Botones de acción
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clickable(role = Role.Button) { onOpenLeaderboard() }
                                .semantics { contentDescription = "Clasificación" },
                            contentAlignment = Alignment.Center
                        ) {
                            RawImage(resId = R.raw.rankingicon, modifier = Modifier.size(54.dp))
                        }
                        // Indicador de VIDAS (corazón + cantidad), estilo chip premium como la cartera.
                        // Con VIDAS INFINITAS el chip cambia de identidad entero — dorado y con "∞",
                        // igual que la tarjeta de la tienda. Antes seguía marcando "5" y el jugador
                        // que acababa de comprarlas no veía por ningún lado lo que había pagado.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    if (infiniteEndingSoon) Color(0xFF3A2410).copy(alpha = 0.70f)
                                    else if (infiniteLives) Color(0xFF3A2F10).copy(alpha = 0.65f)
                                    else Color(0xFF3A1420).copy(alpha = 0.55f),
                                    RoundedCornerShape(14.dp)
                                )
                                .border(
                                    1.dp,
                                    if (infiniteEndingSoon) InfiniteLivesEnding.copy(alpha = 0.85f)
                                    else if (infiniteLives) InfiniteLivesGold.copy(alpha = 0.75f)
                                    else Color(0xFFFF5C7A).copy(alpha = 0.45f),
                                    RoundedCornerShape(14.dp)
                                )
                                // El chip ABRE la ventana de vidas (2026-09-06). El `clip` va antes
                                // del `clickable` a propósito: sin él, la onda del toque se pintaría
                                // cuadrada sobre un chip redondeado.
                                .clickable(role = Role.Button) { onOpenLives() }
                                .padding(horizontal = 9.dp, vertical = 7.dp)
                                .semantics(mergeDescendants = true) {
                                    contentDescription = when {
                                        infiniteEndingSoon ->
                                            "Vidas infinitas: quedan $infiniteRemainingLabel. Abrir vidas para ampliar"
                                        infiniteLives -> "Vidas infinitas activas. Abrir vidas"
                                        lives == 1    -> "1 vida. Abrir vidas"
                                        else          -> "$lives vidas. Abrir vidas"
                                    }
                                }
                        ) {
                            RawImage(resId = R.raw.heartful, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                // (2026-09-25, pedido del usuario) Con vidas infinitas el chip dice
                                // SIEMPRE «∞»: antes, en la última hora, cambiaba a «45m». El aviso de
                                // que se acaban queda en el color ámbar y en la ventana de vidas.
                                text = if (infiniteLives) "∞" else "$lives",
                                color = when {
                                    infiniteEndingSoon -> InfiniteLivesEnding
                                    infiniteLives      -> InfiniteLivesGold
                                    else               -> Color.White
                                },
                                // El glifo "∞" pinta más bajo que un dígito: sube un punto para que
                                // el chip no crezca ni el símbolo se lea descolgado del corazón.
                                fontSize = if (infiniteLives) 20.sp else 16.sp,
                                fontWeight = if (infiniteLives) FontWeight.Black else FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(54.dp)
                                .clickable(role = Role.Button) { onOpenSettings() }
                                .semantics { contentDescription = "Ajustes" },
                            contentAlignment = Alignment.Center
                        ) {
                            RawImage(resId = R.raw.settings, modifier = Modifier.size(54.dp))
                        }
                    }
                }

                // ── Contenido central fijo ───────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 5-tap secreto en el título abre el overlay de Player ID
                    val titleInteraction = remember { MutableInteractionSource() }
                    LaunchedEffect(titleTapCount) {
                        if (titleTapCount in 1..4) {
                            delay(2000)
                            titleTapCount = 0
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                            .scale(titleScale.value)
                            .clickable(
                                interactionSource = titleInteraction,
                                indication = null
                            ) {
                                titleTapCount++
                                if (titleTapCount >= 5) {
                                    titleTapCount = 0
                                    showPlayerIdOverlay = true
                                    Haptics.perform(view,HapticFeedbackConstants.LONG_PRESS)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        RawImage(
                            resId = R.raw.teikafusiontitulo,
                            contentDescription = "Teika Fusion",
                            modifier = Modifier.fillMaxWidth(0.96f)
                        )
                    }

                    // Anzuelo de progresión: siguiente compañero por desbloquear — JUSTO bajo el título
                    // (antes estaba al final de la columna y se salía por debajo del nav bar).
                    Spacer(modifier = Modifier.height(4.dp))
                    NextCompanionProgress(
                        maxLevel   = gameState.maxUnlockedLevel,
                        claimedIds = claimedCharacterIds
                    )

                    // Espacio restante: botones centrados bajo el título.
                    // El botón JUGAR respira solo si Inicio está a la vista (ver [LocalTabActive]).
                    val playScale: Float = if (LocalTabActive.current) {
                        rememberInfiniteTransition(label = "playBreath").animateFloat(
                            initialValue  = 1f,
                            targetValue   = 1.04f,
                            animationSpec = infiniteRepeatable(
                                tween(900, easing = FastOutSlowInEasing),
                                RepeatMode.Reverse
                            ),
                            label = "playBreathScale"
                        ).value
                    } else 1f
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .alpha(bottomAlpha.value)
                    ) {
                        // Scroll de seguridad: cuando el contenido (p. ej. con el banner "Guarda tu
                        // progreso") supera el alto disponible, se puede desplazar en vez de quedar
                        // recortado bajo la nav bar. Cuando cabe, `heightIn(min)` + arrangement
                        // centrado lo mantiene centrado como antes.
                        val availableHeight = maxHeight
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .heightIn(min = availableHeight)
                                // Reserva el alto REAL del navbar (fuente única): estaba a 80 dp
                                // desde antes del rediseño y la barra se comía el borde inferior.
                                .padding(bottom = NavBarHeight),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                        ) {
                            // ── Arte del bioma actual (tap → picker de mundos) ──────────
                            BiomeArtCard(
                                level      = selectedLevel,
                                onClick    = { Haptics.perform(view, HapticFeedbackConstants.KEYBOARD_TAP); onOpenBiomePicker() }
                            )

                            // ── Texto del nivel ────────────────────────────────────────
                            Text(
                                // P-92: «LECCIÓN n» mientras el jugador está en el tutorial, y el
                                // número de nivel ya sin las siete lecciones a partir de ahí.
                                text = "${com.cukiti.teikafusion.model.LevelMap.displayWord(selectedLevel)} " +
                                    "${com.cukiti.teikafusion.model.LevelMap.displayNumber(selectedLevel)}",
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                letterSpacing = 1.sp
                            )

                            // ── Botón ¡JUGAR! / CONTINUAR ──────────────────────────────
                            val jugarSource = remember { MutableInteractionSource() }
                            val isJugarPressed by jugarSource.collectIsPressedAsState()
                            val jugarPressScale by animateFloatAsState(
                                if (isJugarPressed) 0.95f else 1f,
                                spring(stiffness = Spring.StiffnessHigh),
                                label = "jugarPress"
                            )
                            // ── CONTINUAR: la salida gratis del intento abierto (2026-09-03) ─────
                            // `continueGame()` llevaba desde el borrado del modo Infinito SIN UN
                            // SOLO LLAMANTE, y este botón era el que faltaba: el juego autoguardaba
                            // en cada colocación y al pausar, marcaba «intento abierto», COBRABA una
                            // vida al volver a entrar (regla C-6) y no ofrecía forma de retomar nada.
                            // El jugador pagaba por un intento que no podía recuperar, y lo único
                            // que oía era el sonido de perder el corazón encima del de lanzamiento.
                            //
                            // Solo se ofrece si el guardado es DE ESTE nivel: con el cursor en otro,
                            // empezar sí es abandonar y sí debe cobrar. Reiniciar el nivel sigue
                            // costando vida desde la pausa (`restartCostsLife`), que es donde el
                            // jugador ya sabe que está tirando el intento.
                            val canContinue = hasSavedGame && savedGameLevel > 0 &&
                                savedGameLevel == selectedLevel
                            // P-420 · empezar OTRO nivel con una partida a medias la borra y cobra vida
                            // (C-6): se avisa antes. Las lecciones no cobran ni por las dos puntas.
                            val wouldAbandon = hasSavedGame && savedGameLevel > 0 &&
                                savedGameLevel != selectedLevel &&
                                !com.cukiti.teikafusion.model.TutorialCampaign.isTutorialLevel(savedGameLevel) &&
                                !com.cukiti.teikafusion.model.TutorialCampaign.isTutorialLevel(selectedLevel)
                            val playLabel = if (canContinue) "CONTINUAR" else "¡JUGAR!"
                            val playGradient = if (canContinue)
                                listOf(Color(0xFF5CFFB0), Color(0xFF1B9E6B))
                            else
                                listOf(Color(0xFF4DB8FF), Color(0xFF7B2FBE))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.78f)
                                    .height(84.dp)
                                    .scale(playScale * jugarPressScale)
                                    .clip(RoundedCornerShape(36.dp))
                                    .background(brush = Brush.horizontalGradient(colors = playGradient))
                                    .border(2.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(36.dp))
                                    .clickable(
                                        interactionSource = jugarSource,
                                        indication = null,
                                        role       = Role.Button,
                                        onClick    = {
                                            Haptics.perform(view,HapticFeedbackConstants.KEYBOARD_TAP)
                                            when {
                                                canContinue -> onContinue()
                                                wouldAbandon -> showAbandonWarning = true
                                                else -> onPlayPressed()
                                            }
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text          = playLabel,
                                    fontSize      = 28.sp,
                                    fontWeight    = FontWeight.Black,
                                    color         = Color.White,
                                    letterSpacing = 3.sp
                                )
                            }

                            // La única invitación automática a guardar aparece al terminar el
                            // tutorial 4; Home no vuelve a insistir después de «Ahora no».
                        }
                    }
                }
            }

            // (RETIRADO 2026-09-23, pedido del usuario) El acceso rápido a la vitrina de bloques
            // (P-250) flotaba bajo el avatar y tapaba la «T» del título. La vitrina sigue abriéndose
            // desde el selector de mundos.

            // Atrás cierra la ventana abierta, no el juego (auditoría 2026-10-01): sin esto, en Inicio el
            // handler de AppNavigation está apagado y el sistema cerraba la app con el carné delante.
            BackHandler(enabled = showProfileOverlay || showPlayerIdOverlay) {
                showProfileOverlay = false
                showPlayerIdOverlay = false
            }

            if (showAbandonWarning) {
                AbandonAttemptOverlay(
                    savedLevel = savedGameLevel,
                    targetLevel = selectedLevel,
                    costsLife = !infiniteLives,
                    onContinueSaved = { showAbandonWarning = false; onContinue() },
                    onStartAnyway = { showAbandonWarning = false; onPlayPressed() },
                    onDismiss = { onUiClick(); showAbandonWarning = false }
                )
            }

            if (showPlayerIdOverlay) {
                PlayerIdOverlay(
                    playerId = playerId,
                    onDismiss = { showPlayerIdOverlay = false }
                )
            }

            // ── Perfil: SCRIM + VENTANA, ambos con CORTE SECO ────────────────────────────────────
            // Sin fundido ni "pop": abrir y cerrar el perfil es instantáneo (2026-07-28). El rebote
            // obligaba a componer y animar la ventana entera durante ~14 fotogramas.
            AnimatedVisibility(
                visible = showProfileOverlay,
                enter   = androidx.compose.animation.EnterTransition.None,
                exit    = androidx.compose.animation.ExitTransition.None
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.66f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showProfileOverlay = false }
                        // El scrim ocupa toda la pantalla y descarta al tocar: que lo diga.
                        .semantics { contentDescription = "Cerrar" }
                )
            }
            AnimatedVisibility(
                visible = showProfileOverlay,
                enter = androidx.compose.animation.EnterTransition.None,
                exit  = androidx.compose.animation.ExitTransition.None
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        // La navbar se dibuja en una capa superior sobre la franja inferior: este
                        // padding sube el centro efectivo para que la ventana no quede tapada ni
                        // pegada a la barra. Alto de la fuente única (el comentario viejo decía
                        // "~92dp" y el literal 108: dos números, ninguno el real).
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = NavBarHeight),
                    contentAlignment = Alignment.Center
                ) {
                    HomeAvatarPickerSheet(
                        currentAvatar = avatarType,
                        displayName = displayName,
                        totalXp = totalXp,
                        globalRank = globalRank,
                        globalRankSeenAt = globalRankSeenAt,
                        onOpenRanking = {
                            onUiClick()
                            showProfileOverlay = false
                            onOpenLeaderboard()
                        },
                        dailyStreak = streak,
                        winStreak = winStreak,
                        highScore = gameState.highScore,
                        maxLevel = gameState.maxUnlockedLevel,
                        maxTeikas = gameState.maxTeikas,
                        onSelect = { type ->
                            onAvatarSelected(type)
                            showProfileOverlay = false
                        },
                        onDismiss = { showProfileOverlay = false }
                    )
                }
            }

            // ── Celebración de racha diaria (auto-descarta a los 3.5 s) ──
            // Revivido en la it. 41: el banner existía completo pero NADIE lo disparaba (UI muerta
            // desde la economía tiburón). Ahora celebra la racha VIVA — sin moneda: el premio es
            // el reconocimiento + el XP del día, y da la cara por el "Día N 🔥" de la notificación.
            if (dailyStreakCelebration != null) {
                LaunchedEffect(dailyStreakCelebration) {
                    delay(3500)
                    onConsumeStreakCelebration()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .safeDrawingPadding()
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.horizontalGradient(listOf(Color(0xFFFF8C00), Color(0xFFFFD700)))
                        )
                        .border(1.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onConsumeStreakCelebration() }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Racha diaria de $dailyStreakCelebration días. " +
                                "Vuelve mañana para conservarla"
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        RawImage(R.raw.ic_streak, Modifier.size(28.dp))
                        Column {
                            Text(
                                text = "¡Racha de $dailyStreakCelebration días!",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Text(
                                text = "Vuelve mañana para conservarla",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF5D2E00)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Perfil: CARNÉ DE JUGADOR (rediseño 2026-07-20b) ────────────────────────────────────────────
//
// El perfil anterior apilaba en vertical insignia + stats + 2 chips + 6 filas de logro + 2 filas de
// avatares: ~780dp dentro de una hoja al 90% con `verticalScroll`. Nunca cabía, y los dos chips de
// racha eran CONDICIONALES, así que al aparecer/desaparecer empujaban todo lo de abajo.
//
// Rediseño (elegido por el usuario): un CARNÉ con dos zonas y presupuesto de alto cerrado.
//   · HÉROE fijo arriba: avatar + nombre + rango + barra de XP + 3 stats + las 2 rachas, que ahora
//     SIEMPRE ocupan su sitio (apagadas si están a 0) → nada se mueve nunca.
//   · Panel inferior de ALTURA FIJA con dos pestañas, LOGROS y AVATAR. Sea cual sea la pestaña, el
//     alto del panel es el mismo, así que cambiar de una a otra no reflowea nada.
// Los 6 logros pasan de filas con barra (~290dp) a una rejilla 3×2 de MONEDAS con el progreso en el
// aro (~150dp): ahí está la mitad del ahorro que hace que todo quepa sin scroll.

/**
 * Alto del panel del selector de avatar. Fijo a propósito: es lo que garantiza que nada empuje.
 *
 * De 200 dp a 104 (2026-09-07): los cinco Teikas iban en 3+2, o sea una segunda fila con dos
 * huecos vacíos, y debajo un «Toca para seleccionar» explicando lo que hace cualquier rejilla de
 * caras con una seleccionada. Los cinco caben en UNA fila, y el carné entero adelgaza ~96 dp.
 */
private val PROFILE_PANEL_HEIGHT = 104.dp

@Composable
private fun HomeAvatarPickerSheet(
    currentAvatar: TeikaType,
    displayName: String,
    totalXp: Int,
    dailyStreak: Int = 0,
    winStreak: Int = 0,
    highScore: Int = 0,
    maxLevel: Int = 1,
    maxTeikas: Int = 0,
    globalRank: Int = 0,
    globalRankSeenAt: Long = 0L,
    onOpenRanking: () -> Unit = {},
    onSelect: (TeikaType) -> Unit,
    onDismiss: () -> Unit
) {
    val rank = PlayerRank.get(totalXp)
    val rankColor = Color(rank.colorArgb)
    // VENTANA de juego: tarjeta flotante con TODAS las esquinas redondeadas (no una hoja pegada al
    // borde). El radio grande + la sombra proyectada la despegan del fondo → se lee como un cartel.
    val windowShape = RoundedCornerShape(28.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)                    // no se estira feo en pantallas anchas
            // Sombra proyectada teñida del rango: da volumen y "flotación" de panel de juego.
            .shadow(24.dp, windowShape, spotColor = rankColor, ambientColor = Color.Black)
            .clip(windowShape)
            .background(
                // Degradado TINTADO del rango arriba y más profundo abajo: el azul plano de antes
                // hacía que la ventana se confundiera con el fondo de Inicio (ver art/fondonegro.jpeg).
                Brush.verticalGradient(
                    listOf(
                        androidx.compose.ui.graphics.lerp(Color(0xFF2A3568), rankColor, 0.14f),
                        Color(0xFF141A34),
                        Color(0xFF0D1228)
                    )
                )
            )
            // Brillo glossy superior: volumen de "placa" en vez de rectángulo recortado.
            .drawBehind {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.08f), Color.Transparent),
                        endY = size.height * 0.30f
                    ),
                    size = Size(size.width, size.height * 0.30f)
                )
            }
            // Doble marco: aro luminoso del rango + filo interior claro = acabado de "placa".
            .border(
                2.dp,
                Brush.linearGradient(listOf(rankColor.copy(alpha = 0.75f), rankColor.copy(alpha = 0.25f))),
                windowShape
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        // ── Barra de título de la ventana: rótulo centrado + botón de cierre tipo control ──
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                "PERFIL",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = rankColor.copy(alpha = 0.9f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .clearAndSetSemantics { }
            )
            // Cerrar: control de ventana explícito, arriba a la derecha.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    // P-09: área táctil de 48 dp sin tocar el dibujo (30 dp).
                    .minimumInteractiveComponentSize()
                    .size(30.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.10f))
                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(50))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .semantics { contentDescription = "Cerrar perfil" }
            ) {
                RawImage(R.raw.ic_close, Modifier.size(14.dp))
            }
        }

        // ── HÉROE: identidad + progreso + números, todo en un bloque de alto constante ──
        ProfileHeroCard(
            avatar = currentAvatar,
            displayName = displayName,
            totalXp = totalXp,
            highScore = highScore,
            maxLevel = maxLevel,
            maxTeikas = maxTeikas,
            dailyStreak = dailyStreak,
            winStreak = winStreak
        )

        // ── P-227 · EL PUESTO DEL RANKING ──────────────────────────────────────────────────
        // El sitio lo dejaron libre los avatares al pasar de 3+2 a una fila. Lo que se pone ahí es
        // el único dato de orgullo que el jugador tenía que salir del perfil para ver: dónde está en
        // la tabla. Y como enseñarlo sin poder ir es media respuesta, la franja ABRE el ranking.
        //
        // Es el puesto de la ÚLTIMA VEZ que miró la tabla, no una consulta nueva: pedirle a
        // Firestore un puesto por cada visita a Inicio es el coste que este proyecto ya retiró una
        // vez. Por eso, mientras no haya puesto, la franja no inventa un número: invita a mirar.
        RankStrip(rank = globalRank, seenAtMs = globalRankSeenAt, accent = rankColor, onClick = onOpenRanking)

        // ── Panel de ALTURA FIJA: solo AVATAR ──
        // Los LOGROS se retiraron del perfil (2026-07-28): eran una rejilla de medallas derivadas
        // que nadie miraba dos veces y obligaban a un conmutador de pestañas. El carné queda en lo
        // que sí se usa: identidad, rango y elegir avatar. Sin pestañas, cero pasos.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(PROFILE_PANEL_HEIGHT),
            contentAlignment = Alignment.Center
        ) {
            AvatarPickerPanel(currentAvatar = currentAvatar, onSelect = onSelect)
        }
    }
}

/**
 * Cabecera del carné: avatar a la izquierda, identidad y progreso de rango a la derecha, y debajo
 * una franja con las 3 estadísticas y las 2 rachas. Las rachas SIEMPRE se dibujan (apagadas cuando
 * están a 0): es la regla que impide que el layout se mueva cuando el jugador empieza una racha.
 */
@Composable
private fun ProfileHeroCard(
    avatar: TeikaType,
    displayName: String,
    totalXp: Int,
    highScore: Int,
    maxLevel: Int,
    maxTeikas: Int,
    dailyStreak: Int,
    winStreak: Int
) {
    val rank = PlayerRank.get(totalXp)
    val next = PlayerRank.nextRank(totalXp)
    val xpToNext = PlayerRank.xpToNext(totalXp)
    val rankColor = Color(rank.colorArgb)
    val animProgress by animateFloatAsState(
        targetValue = PlayerRank.progressFraction(totalXp),
        animationSpec = tween(700),
        label = "rankProgress"
    )
    val progressLabel = if (next != null && xpToNext != null)
        "$xpToNext XP para ${next.name}" else "Rango máximo"

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .semantics(mergeDescendants = true) {
                    contentDescription = buildString {
                        if (displayName.isNotBlank()) append("$displayName. ")
                        append("Rango ${rank.name}, $totalXp puntos de experiencia. $progressLabel")
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Avatar con el aro del color del rango: el rango se lee sin necesidad de texto.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(66.dp)
                    .clip(RoundedCornerShape(50))
                    .background(rankColor.copy(alpha = 0.14f))
                    .border(2.dp, rankColor.copy(alpha = 0.85f), RoundedCornerShape(50))
            ) {
                TeikaIcon(type = avatar, modifier = Modifier.size(50.dp))
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (displayName.isNotBlank()) {
                    Text(
                        displayName,
                        fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, color = Color.White,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        rank.name.uppercase(),
                        fontSize = 12.sp, fontWeight = FontWeight.Black,
                        color = rankColor, letterSpacing = 1.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        "$totalXp XP",
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.5f), maxLines = 1
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.38f))
                ) {
                    if (animProgress > 0f) Box(
                        modifier = Modifier
                            .fillMaxWidth(animProgress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(rankColor, next?.let { Color(it.colorArgb) } ?: rankColor)
                                )
                            )
                    )
                }
                Text(progressLabel, fontSize = 10.sp, color = Color.White.copy(alpha = 0.55f), maxLines = 1)
            }
        }

        // Estadísticas: tres columnas de igual peso → los números nunca descolocan a los vecinos.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.22f))
                .padding(vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HeroStat(R.raw.ic_trophy, compactNumber(highScore), "RÉCORD", "Récord de puntuación: $highScore", Modifier.weight(1f))
            val maxLevelShown = com.cukiti.teikafusion.model.LevelMap.displayNumber(maxLevel)
            HeroStat(R.raw.ic_map, maxLevelShown.toString(), "NIVEL", "Nivel más lejano alcanzado: $maxLevelShown", Modifier.weight(1f))
            HeroStat(R.raw.ic_teikas, maxTeikas.toString(), "TEIKAS", "Mejor cosecha de Teikas: $maxTeikas", Modifier.weight(1f))
        }

        // Rachas: SIEMPRE presentes. Apagadas a 0 → empezar una racha no mueve nada de sitio.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            StreakChip(
                iconRes = R.raw.ic_streak,
                text = if (dailyStreak >= 1) "$dailyStreak ${if (dailyStreak == 1) "día" else "días"}" else "Sin racha",
                color = Color(0xFFFFB300),
                on = dailyStreak >= 1,
                description = if (dailyStreak >= 1) "Racha diaria: $dailyStreak días" else "Sin racha diaria",
                modifier = Modifier.weight(1f)
            )
            StreakChip(
                iconRes = R.raw.ic_medal,
                text = if (winStreak >= 2) "×$winStreak victorias" else "Sin victorias",
                color = Color(0xFF7BE495),
                on = winStreak >= 2,
                description = if (winStreak >= 2) "Racha de victorias: $winStreak niveles seguidos"
                    else "Sin racha de victorias",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * **P-227 · La franja del puesto global.** Trofeo + rótulo + el número, y un chevron que promete que
 * lleva a algún sitio (lleva al ranking). Sin puesto conocido dice qué hacer para tenerlo, en vez de
 * enseñar un «#0» que no significa nada.
 */
@Composable
private fun RankStrip(rank: Int, seenAtMs: Long, accent: Color, onClick: () -> Unit) {
    // P-437: de cuándo es el puesto. Se calcula al abrir el carné (no hace falta un reloj vivo).
    val age = remember(seenAtMs) { com.cukiti.teikafusion.model.RankAge.label(seenAtMs, System.currentTimeMillis()) }
    val beyond = rank == com.cukiti.teikafusion.model.LeaderboardEntry.RANK_BEYOND
    val known = rank > 0 || beyond
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.22f))
            .border(1.dp, accent.copy(alpha = if (known) 0.45f else 0.18f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = androidx.compose.ui.semantics.Role.Button,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .semantics {
                contentDescription = if (beyond)
                    "Más allá del puesto 100 del ranking global. Abre el ranking"
                else if (known)
                    "Puesto $rank del ranking global${if (age.isNotEmpty()) ", $age" else ""}. Abre el ranking"
                else "Todavía sin puesto en el ranking global. Abre el ranking"
            }
    ) {
        RawImage(R.raw.ic_leaderboard, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "RANKING GLOBAL",
                fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp,
                color = Color.White.copy(alpha = 0.55f),
            )
            if (known && age.isNotEmpty()) {
                Text(age, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.4f))
            }
        }
        Text(
            if (known) "#${com.cukiti.teikafusion.model.LeaderboardEntry.rankLabel(rank)}" else "Ver tabla",
            fontSize = if (known) 17.sp else 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (known) accent else Color.White.copy(alpha = 0.6f),
            maxLines = 1
        )
        Spacer(Modifier.width(6.dp))
        Text("›", fontSize = 20.sp, color = accent.copy(alpha = 0.8f))
    }
}

/** Una estadística del héroe: icono + número grande + etiqueta. Un solo nodo para el lector. */
@Composable
private fun HeroStat(
    iconRes: Int,
    value: String,
    label: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = modifier.clearAndSetSemantics { contentDescription = description }
    ) {
        RawImage(iconRes, Modifier.size(15.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = Color.White, maxLines = 1)
        Text(
            label, fontSize = 9.sp, fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.42f), letterSpacing = 1.sp, maxLines = 1
        )
    }
}

/** Chip de racha. Ocupa su sitio esté encendida o no (ver la regla de "nada empuja"). */
@Composable
private fun StreakChip(
    iconRes: Int,
    text: String,
    color: Color,
    on: Boolean,
    description: String,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = if (on) 0.16f else 0.05f))
            .border(1.dp, color.copy(alpha = if (on) 0.5f else 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clearAndSetSemantics { contentDescription = description }
    ) {
        RawImage(iconRes, Modifier.size(15.dp).alpha(if (on) 1f else 0.35f))
        Text(
            text, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold,
            color = if (on) color else Color.White.copy(alpha = 0.35f),
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/** Números largos sin romper la columna (P-419: el formato único de [AmountFormat]). */
private fun compactNumber(v: Int): String = com.cukiti.teikafusion.model.AmountFormat.compact(v)

// (ProfileTabSwitch / ProfileTab BORRADOS con la retirada de los LOGROS 2026-07-28: el carné ya
//  no tiene pestañas, el panel es siempre el selector de avatar.)

/** Panel AVATAR: los 5 Teikas en UNA fila, centrados dentro del panel de alto fijo. */
@Composable
private fun AvatarPickerPanel(currentAvatar: TeikaType, onSelect: (TeikaType) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(
            TeikaType.TEIKA_1, TeikaType.TEIKA_2, TeikaType.TEIKA_3,
            TeikaType.TEIKA_4, TeikaType.MEGA_TEIKA
        ).forEach { type ->
            HomeAvatarOption(type = type, selected = type == currentAvatar, onSelect = { onSelect(type) })
        }
    }
}

@Composable
private fun HomeAvatarOption(type: TeikaType, selected: Boolean, onSelect: () -> Unit) {
    val scale by animateFloatAsState(
        targetValue   = if (selected) 1.15f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy),
        label         = "avatarScale"
    )
    val labels = mapOf(
        TeikaType.TEIKA_1   to "Teika 1",
        TeikaType.TEIKA_2   to "Teika 2",
        TeikaType.TEIKA_3   to "Teika 3",
        TeikaType.TEIKA_4   to "Teika 4",
        TeikaType.MEGA_TEIKA to "Mega"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) NeonBlue.copy(alpha = 0.18f) else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.5.dp, NeonBlue.copy(alpha = 0.7f), RoundedCornerShape(16.dp))
                else Modifier
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = onSelect
            )
            // TalkBack dice cuál es el avatar elegido (antes las cinco sonaban igual).
            .semantics { this.selected = selected; role = Role.RadioButton }
            // 5 caras en una fila: el aire de la casilla baja a 5 dp y la cara a 42. El área táctil
            // real sigue por encima de los 48 dp de Material (42 + 5·2 + el escalado del elegido).
            .padding(horizontal = 5.dp, vertical = 6.dp)
    ) {
        TeikaIcon(type = type, modifier = Modifier.size(42.dp))
        Text(
            labels[type] ?: "",
            fontSize   = 9.sp,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
            color      = if (selected) NeonBlue else Color.White.copy(alpha = 0.6f),
            maxLines   = 1
        )
    }
}

// (PlayerStatsRow / StatPill / PlayerRankBadge ERRADICADOS con el rediseño del perfil 2026-07-20b:
//  el rango, la barra de XP y las tres estadísticas viven ahora dentro de ProfileHeroCard, que las
//  presenta en un bloque de alto constante. Ver HomeAvatarPickerSheet.)

// ── LOGROS RETIRADOS DEL PERFIL (2026-07-28) ─────────────────────────────────────────────────────
// ProfileMedal / AchievementsGrid / MedalCoin / MedalExplainDialog borrados. Las stats que los
// alimentaban (bestWinStreak, bestCombo) siguen persistiéndose — solo desaparece su UI.
