package com.cukiti.teikafusion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cukiti.teikafusion.R

/**
 * Panel reutilizable "Respalda tu cuenta": explica por qué una función (comprar teikoins, competir en
 * el ranking) exige una cuenta vinculada y ofrece el botón de marca de Google. Lo usan
 * el overlay de la tienda y el estado bloqueado del ranking, para que la invitación a vincular sea
 * idéntica en toda la app.
 *
 * El progreso anónimo es 100% local: si el jugador quiere que sus compras y su puesto en el ranking
 * sobrevivan a un cambio de móvil, tiene que respaldar la cuenta primero.
 */
@Composable
fun CloudBackupPrompt(
    title: String,
    subtitle: String,
    onConnectGoogle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF21A2147), Color(0xF20C1130))))
            .border(1.5.dp, Color(0xFF4DB8FF).copy(alpha = 0.45f), RoundedCornerShape(22.dp))
            .padding(horizontal = 22.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Candado dentro de un aro azul: comunica "bloqueado hasta respaldar" sin depender del texto.
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(Color(0xFF4DB8FF).copy(alpha = 0.12f), CircleShape)
                .border(1.5.dp, Color(0xFF4DB8FF).copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            RawImage(R.raw.ic_lock, Modifier.size(34.dp))
        }
        Text(
            text = title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            color = Color.White.copy(alpha = 0.68f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(2.dp))
        BrandConnectRow(
            label = "Continuar con Google",
            bg = Color.White,
            textColor = Color(0xFF3C4043),
            onClick = onConnectGoogle
        ) { GoogleMarkSmall(24.dp) }
    }
}

/** Botón de marca a ancho completo: icono a la izquierda + etiqueta centrada. */
@Composable
private fun BrandConnectRow(
    label: String,
    bg: Color,
    textColor: Color,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        icon()
        Text(
            text = label,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        // Hueco simétrico al icono para que la etiqueta quede ópticamente centrada.
        Spacer(Modifier.size(24.dp))
    }
}

/** "G" multicolor oficial de Google (Lottie ic_google), sin loop. */
@Composable
private fun GoogleMarkSmall(size: Dp) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.ic_google))
    val progress by animateLottieCompositionAsState(composition = composition, iterations = 1)
    LottieAnimation(composition = composition, progress = { progress }, modifier = Modifier.size(size))
}
