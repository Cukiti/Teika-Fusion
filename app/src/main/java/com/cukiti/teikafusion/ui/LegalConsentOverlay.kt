package com.cukiti.teikafusion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Primera decisión del jugador: aceptar los documentos de la app antes de entrar al juego.
 *
 * Vive en la jerarquía normal de Compose para conservar el modo inmersivo. El consentimiento de
 * anuncios (UMP) sigue siendo otro flujo y no se marca desde aquí.
 *
 * ## Rehecha el 2026-09-01 (grabación `analisistutorial`)
 *
 * **Fuera el decorado falso.** Detrás vivía `TutorialConsentBackdrop`: una vista previa del primer
 * tablero que la propia ventana tapaba por completo, más un «0» gigante imitando un marcador que el
 * juego ya no dibuja así y la ficha del nivel 1 flotando suelta al pie de la pantalla. Del decorado
 * entero solo asomaban esas dos piezas huérfanas, sin contexto que las explicara: lo primero que
 * veía un jugador nuevo eran dos objetos sin sentido. Ahora el fondo es solo la marca.
 *
 * **Y fuera la letra pequeña.** El texto explicaba el trato (datos, reglas, 13 años, cuenta de
 * Google opcional) en cuatro bloques de tipografía distinta. Nada de eso decide nada: quien quiera
 * el detalle abre los documentos, que están a un toque. Aquí queda la frase que sí importa y los
 * dos enlaces, con tamaño de botón de verdad.
 */
@Composable
fun LegalConsentOverlay(
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onAccept: () -> Unit
) {
    BackHandler(enabled = true) { }
    var submitted by remember { mutableStateOf(false) }
    val reducedMotion = LocalReducedFx.current
    val entry = remember { Animatable(if (reducedMotion) 1f else 0.92f) }
    LaunchedEffect(Unit) {
        if (!reducedMotion) entry.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF1B2450), Color(0xFF121A3A), Color(0xFF090D1E))
                )
            )
            // Halo de marca detrás de la tarjeta: da profundidad sin dibujar nada reconocible que
            // el jugador tenga que interpretar.
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF6C4DFF).copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(size.width / 2f, size.height * 0.40f),
                        radius = size.width * 0.95f
                    ),
                    radius = size.width * 0.95f,
                    center = Offset(size.width / 2f, size.height * 0.40f)
                )
            }
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Box(
            modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .graphicsLayer {
                        scaleX = entry.value
                        scaleY = entry.value
                        alpha = entry.value
                    }
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF232D57), MaterialTheme.colorScheme.surface)
                        )
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF4FC3F7), Color(0xFF7C4DFF))
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Héroe con halo propio: el Teika se recorta contra la tarjeta en vez de flotar.
                Box(
                    modifier = Modifier
                        .size(104.dp)
                        .drawBehind {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    listOf(
                                        Color(0xFF4FC3F7).copy(alpha = 0.34f),
                                        Color.Transparent
                                    )
                                ),
                                radius = size.minDimension / 2f
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    TeikaIcon(
                        type = com.cukiti.teikafusion.model.TeikaType.TEIKA_1,
                        modifier = Modifier.size(84.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = "TEIKA FUSION",
                    color = Color(0xFF9C7BFF),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.2.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Todo listo para jugar",
                    color = Color.White,
                    fontSize = 26.sp,
                    lineHeight = 31.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Solo falta que aceptes las reglas. Después empieza el tutorial.",
                    color = Color.White.copy(alpha = 0.74f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    LegalLinkChip(
                        text = "Privacidad",
                        accent = Color(0xFF62CFFF),
                        onClick = onOpenPrivacy,
                        modifier = Modifier.weight(1f)
                    )
                    LegalLinkChip(
                        text = "Condiciones",
                        accent = Color(0xFFB48CFF),
                        onClick = onOpenTerms,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Al continuar aceptas las condiciones y confirmas que tienes 13 años o más.",
                    color = Color.White.copy(alpha = 0.52f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))
                // Botón con degradado propio (Material3 no acepta un Brush como contenedor) y el
                // mismo halo de la marca: es el único destino de esta pantalla y tiene que pesar.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(62.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(
                                if (submitted) listOf(Color(0xFF463A93), Color(0xFF3E3480))
                                else listOf(Color(0xFF7C4DFF), Color(0xFF5B6BFF))
                            )
                        )
                        .clickable(
                            enabled = !submitted,
                            role = Role.Button
                        ) {
                            submitted = true
                            onAccept()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (submitted) "PREPARANDO TUTORIAL…" else "EMPEZAR A JUGAR",
                        color = if (submitted) Color.White.copy(alpha = 0.72f) else Color.White,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
    }
}

/** Enlace legal con superficie propia: se ve y se toca como un botón, no como texto azul. */
@Composable
private fun LegalLinkChip(
    text: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, accent.copy(alpha = 0.42f), RoundedCornerShape(13.dp))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
