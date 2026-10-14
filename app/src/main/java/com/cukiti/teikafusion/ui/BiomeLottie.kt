package com.cukiti.teikafusion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.LottieComposition
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.compose.LottieAnimation
import com.cukiti.teikafusion.R

/**
 * **LOS LOTTIE DE LOS CASTIGOS DE BIOMA, SIN CORTE AL APARECER** (2026-09-27, playtest del usuario:
 * «la espada o cualquier ficha no hacen una animación fluida de aparición, tienen como un corte cuando
 * salen; ya cuando aparecen se siente fluida y en bucle»).
 *
 * Dos causas, las dos de la forma en que se montaban (`rememberLottieComposition` +
 * `animateLottieCompositionAsState` en cada sitio):
 *
 *  1. **La composición se parseaba de forma ASÍNCRONA cada vez que se montaba.** Durante los primeros
 *     fotogramas no había nada que dibujar, mientras la animación de entrada (la pieza que cae, la
 *     espada que se alza) ya corría: la figura aparecía de golpe a mitad de su entrada.
 *  2. **Cada instancia empezaba su bucle en el fotograma 0.** La entrada la dibuja un VFX y el reposo
 *     la capa del tablero: dos `LottieAnimation` distintas. Al pasar de una a otra, la figura saltaba
 *     al principio del bucle.
 *
 * Aquí: composición parseada una vez por proceso (se precalienta en `Application`, fuera del hilo
 * principal, como los Teikas en [warmTeikaCompositions]) y el progreso sale del RELOJ DE FOTOGRAMAS, no
 * de un animador por instancia — dos instancias en el mismo instante enseñan el mismo fotograma, así
 * que el relevo entre el VFX y la capa es invisible.
 */
private val biomeLottieCache =
    java.util.concurrent.ConcurrentHashMap<Int, LottieComposition>()

/** Los Lottie de castigo de bioma (`tools/gen_biome_lottie.py`). */
private val BIOME_LOTTIE_RES = intArrayOf(
    R.raw.rey_espada, R.raw.meteorito,
    R.raw.ajedrez_torre_blanca, R.raw.ajedrez_torre_negra,
    R.raw.ajedrez_alfil_blanca, R.raw.ajedrez_alfil_negra,
    R.raw.ajedrez_caballo_blanca, R.raw.ajedrez_caballo_negra,
)

/** Parsea los Lottie de bioma fuera del hilo principal, una vez por proceso. Idempotente, no lanza. */
fun warmBiomeLotties(context: android.content.Context) {
    val app = context.applicationContext
    for (resId in BIOME_LOTTIE_RES) {
        if (biomeLottieCache.containsKey(resId)) continue
        runCatching { LottieCompositionFactory.fromRawResSync(app, resId).value }
            .getOrNull()?.let { biomeLottieCache[resId] = it }
    }
}

/** Fase del bucle (0..1) en el instante [nanos]: la misma para todas las instancias. */
private fun loopPhase(nanos: Long, durationMs: Float): Float =
    if (durationMs <= 0f) 0f else ((nanos / 1_000_000L) % durationMs.toLong()) / durationMs

/** Un Lottie de castigo de bioma en bucle, listo desde su primer fotograma y en fase con los demás. */
@Composable
fun BiomeLottie(resId: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val composition = remember(resId) {
        biomeLottieCache[resId] ?: runCatching {
            LottieCompositionFactory.fromRawResSync(context.applicationContext, resId).value
        }.getOrNull()?.also { biomeLottieCache[resId] = it }
    } ?: return
    val durationMs = composition.duration
    val progress = remember(resId) { mutableFloatStateOf(loopPhase(System.nanoTime(), durationMs)) }
    LaunchedEffect(resId) {
        while (true) withFrameNanos { progress.floatValue = loopPhase(it, durationMs) }
    }
    LottieAnimation(composition, { progress.floatValue }, modifier)
}
