package com.cukiti.teikafusion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.AdManager
import com.cukiti.teikafusion.R

/**
 * Placa redonda de los botones del HUD (salir y pausa, P-439/P-440): 40 dp de dibujo con 48 dp de
 * área táctil, el mismo idioma que el «volver» del ranking y la ✕ de Ajustes.
 */
@Composable
private fun HudPlate(onClick: () -> Unit, description: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.32f))
            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** Las dos barras de pausa; el contraste lo pone la placa oscura de [HudPlate]. */
@Composable
private fun PauseGlyph() {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(2) {
            Box(
                Modifier
                    .size(width = 5.dp, height = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White)
            )
        }
    }
}

/** Cada cuánto se vuelve a pedir el vídeo de monedas mientras no esté listo. */
private const val AD_RETRY_MS = 30_000L

private fun scoreColorFor(score: Int): Color = when {
    score >= 500_000 -> Color(0xFFE040FB) // Magenta
    score >= 400_000 -> Color(0xFFFF4081) // Pink
    score >= 300_000 -> Color(0xFFFF6D00) // Naranja profundo
    score >= 200_000 -> Color(0xFFFFD600) // Dorado
    score >= 100_000 -> Color(0xFF69F0AE) // Verde neón
    score >= 1_000   -> Color(0xFF40C4FF) // Azul cielo
    else             -> Color.White
}

@Composable
fun GameTopBar(
    score: Int,
    highScore: Int,
    teikoins: Int,
    /**
     * El «kick» del marcador, como LAMBDA y no como `Float` (perf 2026-09-26). Es un muelle de ~1 s
     * que salta en cada jugada que puntúa: leído en el cuerpo de `GameScreen`, cada fotograma del
     * muelle recomponía la PANTALLA ENTERA —HUD, tablero y bandeja— justo al romper líneas. Aquí se
     * lee dentro de `graphicsLayer`, en la fase de dibujo: el muelle ya no recompone nada.
     */
    scoreScale: () -> Float,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onAdClick: () -> Unit,
    /** P-18/P-19: usos de anuncio que quedan hoy y segundos de enfriamiento (0 = ya). */
    adQuotaLeft: Int = 0,
    cooldownLeftSec: Int = 0,
    // La cifra VIVA (Remote Config puede haberla movido), no la constante de compilación: el botón
    // tiene que prometer exactamente lo que el anuncio va a pagar.
    adRewardCoins: Int = com.cukiti.teikafusion.AdManager.rewardCoins,
    onCoinCounterPositioned: ((Rect) -> Unit)? = null,
    onScoreCounterPositioned: ((Rect) -> Unit)? = null,
    /**
     * ¿Se pinta la CIFRA? (P-90, 2026-09-02, grabación `analisistutorial`.)
     *
     * En las siete lecciones no: la campaña declara que «los tutoriales enseñan una regla, no
     * califican rendimiento» —no dan estrellas, no tocan el récord, no cobran fusión— y aun así el
     * jugador veía 3.500, 2.093 y 1.050 puntos subiendo arriba a la izquierda mientras aprendía a
     * arrastrar una ficha. Un número que sube sin significar nada compite por la mirada justo con lo
     * que la lección está señalando.
     *
     * Lo que NO se apaga es el botón: esta cápsula es también la salida de la partida, y dejar una
     * lección sin puerta sería un problema peor que el que se arregla. Se queda el «❮» solo.
     */
    showScore: Boolean = true,
    /**
     * ¿Se pinta la puerta de salida (el «❮») y el botón de AJUSTES?
     *
     * **El tutorial dice que no** (2026-09-04, pedido del usuario). Mientras la campaña sigue
     * pendiente, una lección tenía DOS salidas laterales que se saltaban el guion: el «❮» abría el
     * diálogo de abandonar, y el engranaje abría el panel de pausa entero —Continuar, Reiniciar,
     * Salir al inicio— desde dentro de la primera lección. Un tutorial con tres puertas traseras no
     * es un tutorial: es un nivel con instrucciones.
     *
     * La única salida pasa a ser **SALTAR TUTORIAL**, que además deja la cuenta coherente (marca la
     * campaña por vista y desbloquea el primer nivel de verdad). El «❮» del sistema tampoco se
     * cuela: `GameScreen` se lo traga mientras dura la lección.
     */
    showExits: Boolean = true,
) {
    val adReady by AdManager.isReadyFlow.collectAsState()

    // (Auditoría 2026-10-01) Si el vídeo de monedas no cargaba al entrar (sin red, sin inventario),
    // el botón quedaba APAGADO toda la partida: la recarga vivía en su `onClick`, y un botón apagado
    // no recibe toques. Ahora se vuelve a pedir cada 30 s mientras no esté listo y quede cupo.
    // `loadAd` ya ignora la petición si hay una en curso, si no hay consentimiento o si está apagado.
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(adReady, adQuotaLeft > 0) {
        if (adReady || adQuotaLeft <= 0) return@LaunchedEffect
        while (true) {
            AdManager.loadAd(context)
            kotlinx.coroutines.delay(AD_RETRY_MS)
        }
    }

    val animatedScore = remember { Animatable(score.toFloat()) }
    LaunchedEffect(score) {
        animatedScore.animateTo(
            targetValue = score.toFloat(),
            animationSpec = tween(durationMillis = 550, easing = FastOutSlowInEasing)
        )
    }
    val displayedScore = animatedScore.value.toInt()

    val scoreColor by animateColorAsState(
        targetValue = scoreColorFor(score),
        animationSpec = tween(600),
        label = "scoreColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Izquierda: volver + la cifra que mide la partida — todo un botón.
        // En NIVELES es la PUNTUACIÓN; en WALLBLOCK, la PROFUNDIDAD en filas (P-41).
        // Sin salidas (tutorial) la cápsula entera desaparece: no queda un botón muerto ni un hueco
        // que invite a tocarlo. El `Spacer` conserva el reparto de la fila.
        // P-439 (2026-10-01): la SALIDA es un botón propio y el MARCADOR su propia píldora. Juntos en
        // una cápsula («‹ 1250»), la flecha se leía como parte de la cifra y no como una puerta.
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!showExits) {
                Spacer(modifier = Modifier.width(4.dp))
            } else {
                HudPlate(onClick = onBack, description = "Salir de la partida") {
                    RawImage(R.raw.ic_back, Modifier.size(20.dp))
                }
            }
            if (showScore) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black.copy(alpha = 0.28f))
                        // El marcador animado cambia 60 veces por segundo mientras sube: el lector
                        // lee el valor final, no la cuenta.
                        .semantics(mergeDescendants = true) {
                            contentDescription = "Puntuación: $score. Récord: $highScore"
                        }
                        .padding(horizontal = 14.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = "$displayedScore",
                        color = scoreColor,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .padding(top = 3.dp)
                            // Se mide ANTES de escalar: medido después, el rectángulo crecía y
                            // encogía con el muelle y cada fotograma le llegaba a `GameScreen` como
                            // un destino nuevo del «+score» → otra recomposición entera por frame.
                            .onGloballyPositioned { onScoreCounterPositioned?.invoke(it.boundsInWindow()) }
                            .graphicsLayer {
                                val s = scoreScale()
                                scaleX = s
                                scaleY = s
                            }
                    )
                }
            }
        }

        // Derecha: monedas + anuncio + ajustes
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .onGloballyPositioned { coords ->
                        onCoinCounterPositioned?.invoke(coords.boundsInWindow())
                    }
                    // Icono + número se anuncian juntos: "245 Teikoins", no un "245" sin contexto.
                    .semantics(mergeDescendants = true) {
                        contentDescription = "$teikoins teikoins"
                    }
            ) {
                RawImage(resId = R.raw.teikoin, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = com.cukiti.teikafusion.model.AmountFormat.compact(rememberCountUp(teikoins)),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }

            // BOTÓN DE ANUNCIO POR MONEDAS. P-19: dice lo que DA y cuándo vuelve a estar. Antes era
            // un icono siempre igual —sin cifra, sin cupo, sin enfriamiento—: el jugador no sabía si
            // valía la pena tocarlo, y a veces no pasaba nada. Ahora lleva la recompensa encima y se
            // apaga con su cuenta atrás cuando el cupo (P-18) está en enfriamiento.
            val enabled = adReady && adQuotaLeft > 0 && cooldownLeftSec == 0
            // P-441: queda cupo y no hay espera, solo falta que el vídeo llegue — eso es «cargando»,
            // no «apagado». Decía «+150» en gris y parecía roto.
            val loading = !adReady && adQuotaLeft > 0 && cooldownLeftSec == 0
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled, onClick = onAdClick, role = Role.Button)
                    .semantics {
                        contentDescription = when {
                            loading -> "Cargando el vídeo de $adRewardCoins teikoins"
                            !adReady -> "Anuncio no disponible todavía"
                            adQuotaLeft <= 0 -> "Ya has visto todos los anuncios de hoy. Vuelve mañana"
                            cooldownLeftSec > 0 -> "Otro anuncio en $cooldownLeftSec segundos"
                            else -> "Ver un anuncio para ganar $adRewardCoins Teikoins. " +
                                "Te quedan $adQuotaLeft hoy"
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                RawImage(
                    resId = R.raw.adicon,
                    modifier = Modifier.size(46.dp).alpha(if (enabled) 1f else if (loading) 0.6f else 0.35f)
                )
                if (loading) {
                    // Un arco que gira sobre el icono. Con «Reducir efectos», quieto (el «…» de la
                    // etiqueta ya dice que está en camino).
                    val reduced = LocalReducedFx.current
                    val spin = if (reduced) null else rememberInfiniteTransition(label = "adLoad")
                        .animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "adSpin")
                    androidx.compose.foundation.Canvas(
                        Modifier.size(30.dp).graphicsLayer { rotationZ = spin?.value ?: 0f }
                    ) {
                        drawArc(
                            color = Color(0xFFFFD54F),
                            startAngle = 0f, sweepAngle = 270f, useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(
                                width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                        )
                    }
                }
                // Etiqueta del premio (o de la espera), pegada al pie del icono.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = when {
                            adQuotaLeft <= 0 -> "—"
                            cooldownLeftSec > 0 -> "${cooldownLeftSec}s"
                            loading -> "…"
                            else -> "+$adRewardCoins"
                        },
                        color = if (enabled) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.6f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                }
            }

            if (showExits) {
                // P-440 (2026-10-01): el engranaje abría la PAUSA pero decía «ajustes», y el jugador
                // buscaba un botón de pausa. Ahora es el signo universal ❚❚, en la misma placa que
                // la salida de la izquierda (los ajustes siguen dentro de la pausa).
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    HudPlate(onClick = onSettings, description = "Pausar la partida") { PauseGlyph() }
                }
            }
        }
    }
}
