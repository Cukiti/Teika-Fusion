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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.MAX_DISPLAY_NAME_LENGTH
import kotlinx.coroutines.delay

private val NamePurple = Color(0xFF9C6CFF)
private val NameBlue = Color(0xFF49C6FF)
private val NameGreen = Color(0xFF5CFFB0)
private val NamePanel = Color(0xFF151A38)

@Composable
fun PlayerNameOnboarding(
    isSaving: Boolean,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(enabled = true) { }

    var name by remember { mutableStateOf("") }
    val cleanedName = name.trim()
    val canSubmit = cleanedName.isNotEmpty() && !isSaving
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val cardScale = remember { Animatable(0.86f) }
    val cardAlpha = remember { Animatable(0f) }
    val heroOffset = remember { Animatable(24f) }
    val ambient = rememberInfiniteTransition(label = "name_ambient")
    val glow by ambient.animateFloat(
        initialValue = 0.72f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "name_glow"
    )
    val floatY by ambient.animateFloat(
        initialValue = -4f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "name_float"
    )

    fun submit() {
        if (canSubmit) {
            keyboard?.hide()
            onSave(cleanedName)
        }
    }

    LaunchedEffect(Unit) {
        cardAlpha.animateTo(1f, tween(220))
        cardScale.animateTo(
            1f,
            spring(dampingRatio = 0.72f, stiffness = 320f)
        )
    }
    LaunchedEffect(Unit) {
        delay(90)
        heroOffset.animateTo(0f, tween(380, easing = FastOutSlowInEasing))
        delay(240)
        focusRequester.requestFocus()
        keyboard?.show()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF080B22).copy(alpha = 0.98f),
                        Color(0xFF11183D).copy(alpha = 0.98f),
                        Color(0xFF080A1C).copy(alpha = 0.99f)
                    )
                )
            )
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                color = NamePurple.copy(alpha = 0.14f * glow),
                radius = size.minDimension * 0.46f,
                center = Offset(size.width * 0.12f, size.height * 0.24f)
            )
            drawCircle(
                color = NameBlue.copy(alpha = 0.11f * glow),
                radius = size.minDimension * 0.38f,
                center = Offset(size.width * 0.92f, size.height * 0.73f)
            )
            drawCircle(
                color = NameGreen.copy(alpha = 0.07f * glow),
                radius = size.minDimension * 0.22f,
                center = Offset(size.width * 0.75f, size.height * 0.12f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Scroll + imePadding del Box: al abrir el teclado el contenido se desplaza en vez de
                // comprimirse, así el botón "COMENZAR A JUGAR" nunca queda empujado/cortado.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 24.dp)
                .widthIn(max = 430.dp)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha = cardAlpha.value
                },
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.teika_1))
            val lottieProgress by animateLottieCompositionAsState(
                composition = composition,
                iterations = LottieConstants.IterateForever
            )

            Box(
                modifier = Modifier
                    .size(124.dp)
                    .graphicsLayer {
                        translationY = heroOffset.value + floatY
                        scaleX = 0.96f + glow * 0.04f
                        scaleY = 0.96f + glow * 0.04f
                    }
                    .background(NamePurple.copy(alpha = 0.12f * glow), CircleShape)
                    .border(1.5.dp, NameBlue.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                LottieAnimation(
                    composition = composition,
                    progress = { lottieProgress },
                    modifier = Modifier.size(108.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(NamePanel.copy(alpha = 0.98f), Color(0xFF0D1129))
                        )
                    )
                    .border(
                        width = 1.5.dp,
                        brush = Brush.horizontalGradient(listOf(NamePurple, NameBlue, NameGreen)),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 22.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "BIENVENIDO, FUSIONADOR",
                    color = NameGreen,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "¿Cómo te llamas?",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    style = TextStyle(
                        shadow = Shadow(
                            color = NamePurple.copy(alpha = 0.8f),
                            offset = Offset.Zero,
                            blurRadius = 16f
                        )
                    )
                )
                Text(
                    text = "Este será tu nombre en el ranking global y en tu perfil.",
                    color = Color(0xFFBBC5E8),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(4.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { value ->
                        name = value.replace("\n", "").take(MAX_DISPLAY_NAME_LENGTH)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    enabled = !isSaving,
                    singleLine = true,
                    placeholder = {
                        Text("Escribe tu nombre", color = Color(0xFF7580A8))
                    },
                    supportingText = {
                        Text(
                            text = "${name.length}/$MAX_DISPLAY_NAME_LENGTH",
                            color = if (name.length == MAX_DISPLAY_NAME_LENGTH) NameGreen else Color(0xFF7580A8),
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.End
                        )
                    },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onDone = { submit() }
                    ),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NameBlue,
                        unfocusedBorderColor = Color(0xFF4A547A),
                        cursorColor = NameGreen,
                        focusedContainerColor = Color.Black.copy(alpha = 0.18f),
                        unfocusedContainerColor = Color.Black.copy(alpha = 0.12f),
                        disabledContainerColor = Color.Black.copy(alpha = 0.12f)
                    )
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (canSubmit) {
                                Brush.horizontalGradient(listOf(NamePurple, Color(0xFF6B7CFF), NameBlue))
                            } else {
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF343A59), Color(0xFF292E4A))
                                )
                            }
                        )
                        .clickable(
                            enabled = canSubmit,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = ::submit
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSaving) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "CREANDO TU PERFIL...",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    } else {
                        Text(
                            text = "COMENZAR A JUGAR",
                            color = if (canSubmit) Color.White else Color(0xFF858CA8),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                Text(
                    text = "Podrás identificarte fácilmente en la clasificación.",
                    color = Color(0xFF7580A8),
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
