package com.cukiti.teikafusion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.InfiniteLivesPack

/**
 * **LA VENTANA DE LAS VIDAS** (2026-09-06, pedido del usuario).
 *
 * Todo el mecanismo de las vidas —cuántas tienes, cuándo vuelve la siguiente, comprar recargas y
 * comprar vidas infinitas— en **una sola pantalla y sin una sola pasada de scroll**. Se abre desde
 * el corazón de Inicio, que hasta hoy era un indicador mudo: enseñaba el número y no llevaba a
 * ninguna parte, así que para recargar había que saber que eso vivía en una pestaña de la tienda.
 *
 * ## Por qué sale de la tienda
 *
 * Las vidas estaban en la pestaña «Recargas» de la tienda, mezcladas con los bloques y por debajo de
 * tres tarjetas a pantalla completa. Dos cosas iban mal: **el sitio** —quien se queda sin vidas mira
 * el corazón, no el carrito— y **el formato**: seis tarjetas apiladas obligaban a desplazarse para
 * ver la última opción, justo cuando el jugador tiene prisa. Aquí las seis opciones caben a la vez.
 *
 * ## Cómo cabe todo sin scroll
 *
 * Las tarjetas grandes de la tienda (una por opción, a lo ancho) se convierten en **dos filas de
 * tres fichas**: infinitas arriba, recargas abajo. Una ficha es icono + cantidad + precio, que es
 * todo lo que hace falta para decidir; lo que se pierde son las descripciones largas, que en una
 * pantalla de urgencia nadie lee. Alto total ≈ 430 dp, así que entra entera en el móvil más bajo
 * que soportamos sin tocar el `safeDrawingPadding`.
 *
 * ⛔ **No es un `Dialog` ni un `Popup`** — la app es inmersiva y una ventana del sistema devuelve las
 * barras (lo vigila `ImmersiveWindowContractTest`). Es una capa más del `Box` de `AppNavigation`,
 * como [ConfirmOverlay].
 */
@Composable
fun LivesOverlay(
    lives: Int,
    maxLives: Int,
    livesNextRegenAt: Long,
    infiniteLivesUntil: Long,
    teikoins: Int,
    infinitePacks: List<InfiniteLivesPack>,
    onBuyLives: (Int) -> Unit,
    onBuyInfiniteLives: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }
    val scale = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }
    val infiniteActive = rememberInfiniteLivesActive(infiniteLivesUntil)

    // La lluvia de teikoins la pone el sistema ([CoinBurstHost]): los [PriceButton] de las fichas
    // la disparan solos desde su propio centro, así que esta ventana no cablea nada.
    CoinBurstHost(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .scale(scale.value)
                .fillMaxWidth()
                // Traga el toque para que no llegue al scrim, que cierra.
                .pointerInput(Unit) { detectTapGestures { } }
                .background(
                    Brush.sweepGradient(listOf(LivesGoldSoft, LivesRed, LivesGoldSoft)),
                    RoundedCornerShape(26.dp)
                )
                .padding(2.5.dp)
                .background(Brush.verticalGradient(listOf(LivesCardTop, LivesCardBottom)), RoundedCornerShape(24.dp))
                // El scroll es una RED, no la forma de usar esto: con los tamaños de letra normales
                // el contenido mide ~460 dp y cabe entero en cualquier móvil que soportemos — que es
                // el encargo—. Pero con la letra del sistema al 200 % (accesibilidad) o en una
                // pantalla muy baja, lo único que puede pasar sin él es que la última fila se
                // RECORTE, y una opción de compra a medias es peor que un scroll que nadie usa.
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            LivesHeader(teikoins = teikoins, onDismiss = onDismiss)

            LivesStateStrip(
                lives = lives,
                maxLives = maxLives,
                livesNextRegenAt = livesNextRegenAt,
                infiniteLivesUntil = infiniteLivesUntil,
                infiniteActive = infiniteActive,
            )

            // ── VIDAS INFINITAS ──────────────────────────────────────────────────────────────
            SectionLabel(if (infiniteActive) "AMPLÍA TUS VIDAS INFINITAS" else "VIDAS INFINITAS")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                infinitePacks.forEach { pack ->
                    LivesTile(
                        modifier = Modifier.weight(1f),
                        headline = infiniteLabel(pack.hours),
                        caption = "sin límite",
                        cost = pack.cost,
                        accent = LivesGold,
                        enabled = teikoins >= pack.cost,
                        // Con infinitas activas no se bloquea: se ACUMULA (`grantInfiniteLives`
                        // suma sobre lo que quede), y poder ampliar es media razón para comprarlas.
                        lockedReason = null,
                        contentDescription = "Vidas infinitas ${infiniteLabel(pack.hours)} " +
                            "por ${pack.cost} teikoins",
                        icon = { RawImage(R.raw.heartful, Modifier.size(22.dp)) },
                        onBuy = { onBuyInfiniteLives(pack.hours); true },
                    )
                }
            }

            // ── RECARGA ──────────────────────────────────────────────────────────────────────
            // Las tres opciones se ven SIEMPRE y cada una se desbloquea según las vidas que de
            // verdad faltan, para que nadie pague por vidas que se perderían al desbordar el tope.
            // Es la regla que ya tenía la tienda; lo que cambia es el sitio y el tamaño.
            SectionLabel("RECARGA")
            val missing = (maxLives - lives).coerceAtLeast(0)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RefillOption.entries.forEach { option ->
                    val amount = option.amountFor(missing)
                    val needed = !infiniteActive && missing >= option.threshold(maxLives)
                    LivesTile(
                        modifier = Modifier.weight(1f),
                        headline = option.headline,
                        caption = option.caption,
                        cost = option.cost,
                        accent = option.accent,
                        enabled = needed && teikoins >= option.cost,
                        lockedReason = when {
                            infiniteActive -> "Ya tienes infinitas"
                            !needed -> "No te hace falta"
                            else -> null
                        },
                        contentDescription = "${option.headline} por ${option.cost} teikoins",
                        icon = { RawImage(R.raw.heartful, Modifier.size(22.dp)) },
                        onBuy = { onBuyLives(amount); true },
                    )
                }
            }
        }
    }
}

// ── Paleta ───────────────────────────────────────────────────────────────────────────────────

private val LivesRed = Color(0xFFFF4D6D)
private val LivesGold = Color(0xFFFFC93C)
private val LivesGoldSoft = Color(0xFFFFE9A8)
private val LivesGreen = Color(0xFF3ED598)
private val LivesBlue = Color(0xFF4DB8FF)
private val LivesPurple = Color(0xFFBB6EE8)
private val LivesCardTop = Color(0xFF232B55)
private val LivesCardBottom = Color(0xFF141A33)

/** Las tres opciones de recarga. Los precios son los mismos que cobra `GameViewModel.buyLives`. */
private enum class RefillOption(
    val headline: String,
    val caption: String,
    val cost: Int,
    val accent: Color,
) {
    ONE("+1 vida", "un intento", 50, LivesBlue),
    THREE("+3 vidas", "el habitual", 120, LivesPurple),
    FULL("AL MÁXIMO", "de golpe", 200, LivesGreen);

    /**
     * Vidas que deben FALTAR para que esta opción tenga sentido. Comprar tres cuando solo falta una
     * tiraría dos a la basura: el tope no se puede desbordar.
     */
    fun threshold(maxLives: Int): Int = when (this) {
        ONE -> 1
        THREE -> 3
        FULL -> maxLives
    }

    /** Cuántas vidas pide de verdad. La llena pide las que falten, nunca menos de una. */
    fun amountFor(missing: Int): Int = when (this) {
        ONE -> 1
        THREE -> 3
        FULL -> missing.coerceAtLeast(1)
    }
}

private fun infiniteLabel(hours: Int): String = when {
    hours % 24 == 0 && hours >= 24 -> "${hours / 24} día${if (hours > 24) "s" else ""}"
    else -> "$hours h"
}

// ── Piezas ───────────────────────────────────────────────────────────────────────────────────

@Composable
private fun LivesHeader(teikoins: Int, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RawImage(R.raw.heartful, Modifier.size(26.dp))
        Text(
            "VIDAS",
            color = Color.White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.4.sp,
            modifier = Modifier.weight(1f)
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.35f))
                .border(1.dp, LivesGold.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .clearAndSetSemantics { contentDescription = "Saldo: $teikoins teikoins" }
        ) {
            RawImage(R.raw.teikoin, Modifier.size(16.dp))
            Text("$teikoins", color = LivesGold, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(Color.White.copy(alpha = 0.10f))
                .clickable(role = Role.Button, onClick = onDismiss)
                .semantics { contentDescription = "Cerrar" },
            contentAlignment = Alignment.Center
        ) {
            RawImage(R.raw.ic_close, Modifier.size(15.dp).alpha(0.8f))
        }
    }
}

/**
 * El estado, en **una banda** y no en una tarjeta a pantalla completa. Es el recorte que hace que
 * las seis opciones quepan sin scroll: la misma información (corazones, cuenta y reloj) en una línea
 * en vez de en tres.
 */
@Composable
private fun LivesStateStrip(
    lives: Int,
    maxLives: Int,
    livesNextRegenAt: Long,
    infiniteLivesUntil: Long,
    infiniteActive: Boolean,
) {
    val full = lives >= maxLives
    val accent = when {
        infiniteActive -> LivesGold
        full -> LivesGreen
        else -> LivesRed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(accent.copy(alpha = 0.18f), Color.Transparent)))
            .border(1.4.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (infiniteActive) {
            Text("∞", color = LivesGold, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Column(Modifier.weight(1f)) {
                Text(
                    "Vidas infinitas activas",
                    color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black
                )
                Text(
                    "Juega sin gastar ni una",
                    color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp
                )
            }
            InfiniteLivesCountdownPill(infiniteLivesUntil)
        } else {
            // Los corazones son DECORATIVOS para el lector: el dato lo da el texto de al lado, y
            // anunciar cinco imágenes seguidas antes del número es ruido.
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.clearAndSetSemantics { }
            ) {
                repeat(maxLives) { i ->
                    RawImage(
                        resId = if (i < lives) R.raw.heartful else R.raw.heartloss,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                "$lives/$maxLives",
                color = accent, fontSize = 17.sp, fontWeight = FontWeight.Black,
                modifier = Modifier
                    .weight(1f)
                    .semantics { contentDescription = "Tienes $lives vidas de $maxLives" }
            )
            when {
                full -> Text(
                    "¡A jugar!",
                    color = LivesGreen, fontSize = 12.sp, fontWeight = FontWeight.Black
                )
                livesNextRegenAt > 0L -> NextLifePill(livesNextRegenAt)
            }
        }
    }
}

/** Cuenta atrás de la próxima vida. Usa el reloj único de [rememberLivesRegenRemaining] (P-167). */
@Composable
private fun NextLifePill(nextRegenAt: Long) {
    val remaining = rememberLivesRegenRemaining(nextRegenAt)
    val totalSeconds = (remaining / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    Column(horizontalAlignment = Alignment.End) {
        Text("Próxima en", color = Color.White.copy(alpha = 0.45f), fontSize = 9.sp)
        Text(
            "%02d:%02d".format(minutes, seconds),
            color = LivesRed, fontSize = 16.sp, fontWeight = FontWeight.Black,
            modifier = Modifier.semantics {
                contentDescription = "Próxima vida en $minutes minutos $seconds segundos"
            }
        )
    }
}

/** Lo que queda de vidas infinitas. Mismo reloj compartido que el chip de Inicio. */
@Composable
private fun InfiniteLivesCountdownPill(untilMs: Long) {
    val remaining = rememberInfiniteLivesRemaining(untilMs)
    val totalMinutes = (remaining / 60_000L).coerceAtLeast(0L)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val texto = if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(LivesGold.copy(alpha = 0.16f))
            .border(1.dp, LivesGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .semantics { contentDescription = "Te quedan $texto de vidas infinitas" }
    ) {
        Text(texto, color = LivesGold, fontSize = 14.sp, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.42f),
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 1.8.sp,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Una ficha de compra: icono, titular, precio y el botón dentro.
 *
 * El botón es el mismo [ArcadeButton] de la hoja de habilidades porque es **la misma acción** —pagar
 * teikoins— y en este juego lo que hace lo mismo tiene que decirlo igual. [onBuy] recibe el centro
 * del botón en coordenadas de raíz: es de donde salen las monedas.
 */
@Composable
private fun LivesTile(
    headline: String,
    caption: String,
    cost: Int,
    accent: Color,
    enabled: Boolean,
    lockedReason: String?,
    contentDescription: String,
    icon: @Composable () -> Unit,
    onBuy: () -> Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    if (enabled) listOf(accent.copy(alpha = 0.20f), Color(0xFF141A33))
                    else listOf(Color(0xFF1B2140), Color(0xFF141A33))
                )
            )
            .border(
                1.3.dp,
                if (enabled) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.07f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            headline,
            color = if (enabled) Color.White else Color.White.copy(alpha = 0.42f),
            fontSize = 13.sp, fontWeight = FontWeight.Black,
            maxLines = 1, textAlign = TextAlign.Center
        )
        // El renglón del pie NUNCA cambia de alto: el motivo del bloqueo ocupa el sitio del
        // subtítulo en vez de añadirse, así que las seis fichas miden igual estén como estén.
        Text(
            lockedReason ?: caption,
            color = if (lockedReason != null) accent.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.42f),
            fontSize = 9.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        PriceButton(
            price = cost,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            height = 28.dp,
            fontSize = 11.sp,
            depth = 3.dp,
            contentDescription = contentDescription + (lockedReason?.let { ". $it" } ?: ""),
            onBuy = onBuy,
        )
    }
}
