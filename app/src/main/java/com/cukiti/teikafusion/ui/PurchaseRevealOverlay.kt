package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.activity.compose.BackHandler
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.PurchaseReveal
import com.cukiti.teikafusion.model.RewardItem
import com.cukiti.teikafusion.model.RewardKind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Overlay ÉPICO de revelación de compra (spec 2026-07-23): al comprar CUALQUIER artículo, un cofre
 * dorado irrumpe con rayos giratorios, destello y las cartas de premio entrando escalonadas con
 * rebote. Los premios SORPRESA (`bonus`) llevan sello "¡BONUS!". Toca en cualquier lado para cerrar.
 *
 * Es full-screen y transitorio: un solo reloj [Animatable] alimenta todo (rayos, pop del cofre,
 * entrada de las cartas) para no disparar varias corrutinas. El `key` de [reveal].id garantiza que
 * relance aunque el contenido se repita.
 */
@Composable
fun PurchaseRevealOverlay(reveal: PurchaseReveal, onDismiss: () -> Unit) {
    val accent = Color(reveal.accentArgb)
    // Atrás cierra (patrón de overlays full-screen del proyecto).
    BackHandler { onDismiss() }

    // Reloj maestro 0→1: fase A (0..0.35) irrumpe el cofre; fase B las cartas entran escalonadas.
    val clock = remember(reveal.id) { Animatable(0f) }
    // Reloj del fondo (rayos giratorios). Es un BUCLE INFINITO, no un tween de 6 s: antes corría una
    // sola vez y, si el jugador se quedaba mirando el premio, los rayos se congelaban de golpe.
    // El ciclo cierra perfecto porque todo lo que lo consume es 2π-periódico en `spin` (ver el Canvas),
    // así que al saltar de 1 → 0 el dibujo es idéntico y no se nota la costura.
    val spin = remember(reveal.id) { Animatable(0f) }
    LaunchedEffect(reveal.id) {
        while (true) {
            spin.snapTo(0f)
            spin.animateTo(1f, tween(6000, easing = LinearEasing))
        }
    }
    LaunchedEffect(reveal.id) {
        clock.snapTo(0f)
        clock.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    val t = clock.value

    val descItems = reveal.items.joinToString(", ") { it.label }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070F).copy(alpha = (0.86f * t.coerceIn(0f, 1f))))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
            .semantics { contentDescription = "${reveal.title}. Conseguiste: $descItems. Toca para continuar" },
        contentAlignment = Alignment.Center
    ) {
        // Rayos radiales giratorios detrás del cofre (destello de premio).
        Canvas(modifier = Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2f, size.height * 0.40f)
            val m = size.minDimension
            val rays = 16
            val twoPi = 2f * PI.toFloat()
            val rot = spin.value * twoPi
            val burst = (t * 1.4f).coerceIn(0f, 1f)
            for (i in 0 until rays) {
                val ang = rot + i * (twoPi / rays)
                // 2π EXACTO (no 6.28f): con el valor redondeado el largo del rayo daba un salto
                // visible cada vuelta cuando el reloj reinicia.
                val len = m * (0.34f + 0.10f * sin(spin.value * twoPi + i))
                val end = c + Offset(cos(ang) * len * burst, sin(ang) * len * burst)
                drawLine(
                    color = accent.copy(alpha = 0.14f * burst),
                    start = c, end = end, strokeWidth = 26f
                )
            }
            // Halo central.
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.30f * burst), Color.Transparent),
                    center = c, radius = m * 0.42f
                ),
                radius = m * 0.42f, center = c
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Título irrumpiendo (entra con rebote de escala).
            val titlePop = (t / 0.5f).coerceIn(0f, 1f)
            Text(
                text = reveal.title,
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer {
                        val s = 0.4f + 0.6f * titlePop
                        scaleX = s; scaleY = s; alpha = titlePop
                    }
                    .semantics { }
            )

            // Cartas de premio: una por RewardItem, entrando escalonadas.
            reveal.items.forEachIndexed { i, item ->
                // Cada carta arranca su entrada un poco después que la anterior.
                val start = 0.30f + i * 0.14f
                val p = ((t - start) / (1f - start)).coerceIn(0f, 1f)
                RewardCard(
                    item = item,
                    accent = accent,
                    modifier = Modifier.graphicsLayer {
                        translationY = (1f - p) * 60f
                        alpha = p
                        val s = 0.7f + 0.3f * p
                        scaleX = s; scaleY = s
                    }
                )
            }

            // Pie: pista de cierre (aparece al final).
            val hintAlpha = ((t - 0.75f) / 0.25f).coerceIn(0f, 1f)
            Text(
                "TOCA PARA CONTINUAR",
                color = Color.White.copy(alpha = 0.55f * hintAlpha),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

/** Una carta de premio: icono grande a la izquierda + etiqueta; sello "¡BONUS!" si es sorpresa. */
@Composable
private fun RewardCard(item: RewardItem, accent: Color, modifier: Modifier = Modifier) {
    val cardAccent = if (item.bonus) Color(0xFFFFD700) else accent
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF222B57), Color(0xFF141A38))))
            .border(1.5.dp, cardAccent.copy(alpha = 0.65f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardAccent.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                RewardIcon(item)
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(item.label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                if (item.bonus) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFFFD700).copy(alpha = 0.22f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("¡BONUS SORPRESA!", color = Color(0xFFFFD700), fontSize = 10.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                    }
                } else {
                    Text("¡Conseguido!", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Icono del premio según su tipo (reusa los assets/gemas del proyecto). */
@Composable
private fun RewardIcon(item: RewardItem) {
    when (item.kind) {
        RewardKind.TEIKOINS -> RawImage(R.raw.teikoin, Modifier.size(40.dp))
        RewardKind.LIVES -> RawImage(R.raw.heartful, Modifier.size(38.dp))
        RewardKind.INFINITE_LIVES -> Row(verticalAlignment = Alignment.CenterVertically) {
            RawImage(R.raw.heartful, Modifier.size(26.dp))
            Text("∞", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFD700))
        }
        RewardKind.CRYSTAL -> RawImage(R.raw.ic_crystal, Modifier.size(40.dp))
        RewardKind.BLOCK_SKIN -> RawImage(R.raw.ic_block_skin, Modifier.size(40.dp))
    }
}
