package com.cukiti.teikafusion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cukiti.teikafusion.R
import kotlinx.coroutines.delay

/** Icono Lottie compacto para las superficies de cuenta (se reproduce una vez al aparecer). */
@Composable
private fun WelcomeLottieIcon(rawRes: Int, size: androidx.compose.ui.unit.Dp) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(rawRes))
    val progress by animateLottieCompositionAsState(composition = composition, iterations = 1)
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = Modifier.size(size)
    )
}

private val WelcomePurple = Color(0xFF9C6CFF)
private val WelcomeBlue = Color(0xFF49C6FF)
private val WelcomeGreen = Color(0xFF5CFFB0)
private val WelcomePanel = Color(0xFF151A38)

/**
 * Pantalla de carga de cuenta — cubre TODO mientras se descarga/sincroniza el progreso con la nube
 * (arranque con sesión ya vinculada, login, logout, switch). Estilo AAA: Teika girando + "Cargando…".
 */
@Composable
fun AccountLoadingOverlay(
    message: String = "Cargando tu cuenta…",
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { }
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.teika_1))
    val lottieProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )
    val ambient = rememberInfiniteTransition(label = "loading_ambient")
    val glow by ambient.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "loading_glow"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF080B22),
                        Color(0xFF11183D),
                        Color(0xFF080A1C)
                    )
                )
            )
            .safeDrawingPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .graphicsLayer {
                        scaleX = 0.94f + glow * 0.06f
                        scaleY = 0.94f + glow * 0.06f
                    }
                    .background(WelcomePurple.copy(alpha = 0.12f * glow), CircleShape)
                    .border(1.5.dp, WelcomeBlue.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                LottieAnimation(
                    composition = composition,
                    progress = { lottieProgress },
                    modifier = Modifier.size(104.dp)
                )
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = message,
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        color = Color.White.copy(alpha = 0.62f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 36.dp)
                    )
                }
            }
            // Tres puntos pulsantes
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { i ->
                    val dot by ambient.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600, delayMillis = i * 180, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "loading_dot_$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .graphicsLayer { alpha = dot }
                            .background(WelcomeGreen, CircleShape)
                    )
                }
            }
        }
    }
}

/**
 * Banda de aviso de cuenta: mensaje breve que baja desde arriba, se queda 4 s y se va. **No bloquea
 * nada** — el Box exterior no captura toques, solo la propia banda. Sirve para que los fallos de
 * vinculación (sin cuentas de Google, sin conexión, Google no configurado en esta build) dejen de
 * ser silenciosos: antes el botón "Continuar con Google" fallaba sin decir una palabra.
 */
@Composable
fun AccountNoticeBanner(message: String, onDismiss: () -> Unit) {
    val enter = remember { Animatable(-1f) }
    LaunchedEffect(message) {
        enter.snapTo(-1f)
        enter.animateTo(0f, spring(dampingRatio = 0.78f, stiffness = 380f))
        // Un aviso corto se lee en 4 s; los de depuración llevan pegada la causa técnica y con ese
        // tiempo no daba ni para terminar de leerlos. Se escala con lo que hay que leer.
        delay(if (message.length > 90) 9000L else 4000L)
        enter.animateTo(-1f, tween(220))
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Row(
            modifier = Modifier
                .graphicsLayer {
                    // -1 = fuera de pantalla por arriba; 0 = en su sitio.
                    translationY = enter.value * (size.height + 24f)
                    alpha = 1f + enter.value
                }
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(WelcomePanel)
                .border(1.5.dp, WelcomePurple.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button
                ) { onDismiss() }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("⚠", fontSize = 18.sp)
            Text(
                text = message,
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

/**
 * Única invitación automática a guardar el progreso local: aparece al terminar el tutorial 4.
 * Tarjeta premium con Google + "Ahora no"; no vuelve a programarse al ganar niveles.
 */
@Composable
fun PostTutorialBackupPrompt(
    onConnectGoogle: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) { onDismiss() }
    val cardScale = remember { Animatable(0.86f) }
    LaunchedEffect(Unit) { cardScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 340f)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            // Scrim: tocar fuera cierra. Se describe para que no sea un nodo mudo del tamaño de la
            // pantalla (mismo criterio que el del selector de compañero).
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button
            ) { onDismiss() }
            .semantics { contentDescription = "Cerrar" }
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .graphicsLayer { scaleX = cardScale.value; scaleY = cardScale.value }
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                // Absorbe toques para no cerrar al pulsar dentro de la tarjeta.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .background(
                    brush = Brush.horizontalGradient(listOf(WelcomePurple, WelcomeBlue, WelcomeGreen)),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(2.dp)
                .background(WelcomePanel, RoundedCornerShape(24.dp))
                .padding(22.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(WelcomePurple.copy(alpha = 0.16f), CircleShape)
                    .border(1.5.dp, WelcomeBlue.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("☁", fontSize = 30.sp)
            }
            Text(
                text = "Guarda tu progreso",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Terminaste el tutorial. Si quieres, vincula Google para recuperar este avance en cualquier dispositivo.",
                color = Color(0xFFBBC5E8),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))

            // Google
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button
                    ) { onConnectGoogle() },
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    WelcomeLottieIcon(R.raw.ic_google, size = 24.dp)
                    Text("Vincular Google", color = Color(0xFF3C4043), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button
                    ) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text("Ahora no", color = Color(0xFF8892B8), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Diálogo de conflicto de cuenta: al vincular, cuando TANTO el progreso local como el de la
 * cuenta anterior (nube) tienen avance y difieren. El jugador decide cuál conservar — nunca se
 * sobrescribe en automático.
 */
@Composable
fun CloudAccountConflictDialog(
    conflict: com.cukiti.teikafusion.viewmodel.CloudAccountConflict,
    onUseCloud: () -> Unit,
    onKeepLocal: () -> Unit
) {
    BackHandler(enabled = true) { }
    val cardScale = remember { Animatable(0.85f) }
    LaunchedEffect(Unit) { cardScale.animateTo(1f, spring(stiffness = 420f)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.82f))
            // Absorbe los toques para que no lleguen a la UI de fondo mientras se decide.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {}
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .graphicsLayer { scaleX = cardScale.value; scaleY = cardScale.value }
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(listOf(WelcomeBlue, WelcomePurple)),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(3.dp)
                .background(WelcomePanel, RoundedCornerShape(24.dp))
                .padding(24.dp)
        ) {
            Text(
                text = "¿QUÉ CUENTA USAR?",
                color = WelcomeBlue,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tu cuenta ya tiene progreso guardado. Elige cuál conservar — el otro se reemplazará.",
                color = Color.White.copy(alpha = 0.82f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))

            // Opción A: entrar a la cuenta anterior (nube)
            AccountChoiceCard(
                title = "Entrar a mi cuenta anterior",
                subtitle = if (conflict.cloudName.isNotBlank()) conflict.cloudName else "Cuenta en la nube",
                level = conflict.cloudLevel,
                coins = conflict.cloudCoins,
                xp = conflict.cloudXp,
                accent = WelcomeGreen,
                onClick = onUseCloud
            )
            Spacer(Modifier.height(12.dp))
            // Opción B: mantener el progreso actual (local)
            AccountChoiceCard(
                title = "Mantener mi progreso actual",
                subtitle = if (conflict.localName.isNotBlank()) conflict.localName else "Este dispositivo",
                level = conflict.localLevel,
                coins = conflict.localCoins,
                xp = conflict.localXp,
                accent = WelcomeBlue,
                onClick = onKeepLocal
            )

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Tus monedas y compras siempre se conservan. El progreso de niveles se reemplaza según tu elección.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun AccountChoiceCard(
    title: String,
    subtitle: String,
    level: Int,
    coins: Int,
    xp: Int,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.5.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(16.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(title, color = accent, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(2.dp))
        Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Nivel ${com.cukiti.teikafusion.model.LevelMap.displayNumber(level)}",
                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
            TeikoinAmount(
                amount = coins, color = Color.White,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, iconSize = 13.dp,
            )
            Text("XP $xp", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
