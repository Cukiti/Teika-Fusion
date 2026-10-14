package com.cukiti.teikafusion.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView

/**
 * Banner de AdMob anclado abajo del juego. Se carga una vez y se destruye al salir de la pantalla.
 *
 * **El id sale de un recurso por buildType** (`admob_banner_unit_id`), igual que el recompensado:
 * `debug` usa el de prueba de Google y `release` el real. Antes era una constante de prueba
 * incrustada en el código, así que la versión publicada enseñaba "Test Ad" y ese hueco no ingresaba
 * nada; ahora, el día que exista el bloque real, se cambia UNA línea de
 * `app/src/release/res/values/admob.xml` y no se toca Kotlin.
 *
 * Quién decide si este banner existe no es este archivo: es `AdManager.adsOfferedFlow` (compra de
 * «Quitar anuncios» + kill-switch remoto). Si no se ofrece, ni se compone ni se le reserva sitio.
 */
@Composable
fun BannerAdView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // `stringResource`, no `context.getString`: dentro de un composable el segundo no se recompone
    // al cambiar la configuración (y lint lo marca como error).
    val bannerUnitId = androidx.compose.ui.res.stringResource(
        com.cukiti.teikafusion.R.string.admob_banner_unit_id
    )
    val adView = remember {
        AdView(context).apply {
            setAdSize(AdSize.BANNER)   // 320×50 dp estándar
            adUnitId = bannerUnitId
            // P-21: ingreso REAL de cada impresión del banner. Sin esto solo se sabía que el banner
            // existía, no lo que aporta frente al hueco de pantalla que cuesta.
            setOnPaidEventListener { value ->
                com.cukiti.teikafusion.Analytics.trackAdRevenue(
                    placement = "banner_partida",
                    format = "banner",
                    valueMicros = value.valueMicros,
                    currency = value.currencyCode,
                    precision = value.precisionType
                )
            }
        }
    }
    DisposableEffect(Unit) {
        runCatching { adView.loadAd(AdRequest.Builder().build()) }
        onDispose { adView.destroy() }
    }
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { adView }
    )
}
