package com.cukiti.teikafusion

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

object AdManager {
    /**
     * ID del bloque recompensado. Sale de un recurso que cambia con el buildType: `release` trae el
     * REAL y `debug` los de prueba de Google (ver `res/values/admob.xml` de cada source set).
     *
     * No es una constante por un motivo de peso: pulsar tus propios anuncios reales durante el
     * desarrollo cuenta como tráfico inválido y AdMob suspende cuentas por ello. Separándolo por
     * buildType es imposible equivocarse.
     */
    private fun adUnitId(context: Context): String =
        context.getString(R.string.admob_rewarded_unit_id)

    /** Bloque INTERSTICIAL, con la misma separación por buildType y por el mismo motivo. */
    private fun interstitialUnitId(context: Context): String =
        context.getString(R.string.admob_interstitial_unit_id)

    /**
     * Recompensa por anuncio recompensado. Es el valor por DEFECTO: Remote Config puede afinarlo sin
     * publicar versión.
     *
     * Sube de 25 a 150 (saneo de economía 2026-08-09). La vieja cifra venía de la economía "tiburón",
     * que asumía una TIENDA DE TEIKOINS empujando la compra de paquetes — y esa pestaña está
     * desactivada: sin ella el anuncio no empuja a ninguna venta, solo racionaba una moneda que no se
     * podía conseguir de otra forma.
     *
     * **150 → 100 (2026-09-24, P-268, decisión del usuario)**: con 8 × 150 los vídeos daban 1.200 al
     * día, lo mismo que ~8 niveles: ver anuncios pagaba más que jugar. Techo hoy: 600 (6 × 100).
     */
    const val REWARD_COINS = 100

    /**
     * Monedas que entrega el anuncio ahora mismo (Remote Config, con [REWARD_COINS] por defecto).
     *
     * **Público a propósito**: la UI que ofrece el anuncio tiene que anunciar la MISMA cifra que se
     * va a pagar. Mientras leía `REWARD_COINS` directamente, el botón de la barra prometía 25 y el
     * anuncio pagaba lo que dijera Remote Config — dos números distintos para el mismo botón.
     */
    val rewardCoins: Int
        get() = com.cukiti.teikafusion.data.RemoteConfigManager.snapshot.value.rewardedAdCoins
            .takeIf { it > 0 } ?: REWARD_COINS

    /**
     * Kill-switch remoto. Apagarlo desde la consola deja `isReadyFlow` en false, y como TODOS los
     * puntos de la UI que ofrecen un anuncio ya consultan ese flag, la oferta desaparece sola sin
     * tocar ni una pantalla. Es la razón de aplicar el interruptor aquí y no en cada llamante.
     */
    private val remoteAdsEnabled: Boolean
        get() = com.cukiti.teikafusion.data.RemoteConfigManager.snapshot.value.adsEnabled

    /**
     * **Publicidad FORZADA**: el banner y el intersticial, los que el jugador no pide. Es
     * exactamente lo que se compra al comprar «Quitar anuncios», y por eso mira [adsRemoved].
     */
    private val forcedAdsEnabled: Boolean get() = remoteAdsEnabled && !adsRemoved

    /**
     * **Publicidad VOLUNTARIA**: el recompensado, que el jugador pide él para cobrar teikoins.
     *
     * **Sobrevive a «Quitar anuncios» a propósito** (2026-09-06, decisión del usuario). Antes no:
     * `setAdsRemoved(true)` soltaba también el vídeo recompensado, así que pagar por quitar los
     * anuncios te quitaba de paso hasta **600 teikoins diarios** gratis ([REWARD_COINS] × el cupo
     * de [com.cukiti.teikafusion.model.RewardedAdQuota]). Eso es castigar al que paga: el producto
     * que vende la tienda es *no ser interrumpido*, no *no poder ganar monedas*.
     *
     * Si algún día hay que apagarlo entero, el sitio es el kill-switch remoto, que sí lo cubre.
     */
    private val rewardedEnabled: Boolean get() = remoteAdsEnabled

    /**
     * El jugador PAGÓ por no ver anuncios. Lo fija el ViewModel al arrancar (leyendo DataStore) y al
     * confirmarse la compra.
     */
    private val _adsRemoved = MutableStateFlow(false)
    private val adsRemoved: Boolean get() = _adsRemoved.value

    fun setAdsRemoved(removed: Boolean) {
        _adsRemoved.value = removed
        if (removed) {
            // SOLO el intersticial. El recompensado NO se suelta: es voluntario y sigue pagando
            // teikoins a quien compró la tranquilidad. Ver [rewardedEnabled].
            interstitialAd = null
            _isInterstitialReady.value = false
        }
    }

    /**
     * **¿Se le puede ofrecer publicidad a este jugador AHORA MISMO?** Fuente ÚNICA de esa pregunta:
     * kill-switch remoto **y** compra de «Quitar anuncios».
     *
     * Existe porque `isReadyFlow` responde a otra pregunta —"¿hay un vídeo cargado?"— y usarlo como
     * si fueran la misma cosa dejaba dos agujeros reales (auditoría del bloque 17, 2026-07-31):
     *  · el **BANNER** no miraba nada: quien pagaba por quitar los anuncios seguía viéndolo abajo, y
     *    además seguía perdiendo los 50 dp de pantalla que ocupa;
     *  · la oferta de revivir mostraba «Cargando anuncio…» **para siempre** a quien había pagado
     *    (nunca iba a cargar ninguno), o sea un botón muerto girando en el peor momento del nivel.
     *
     * ⚠️ **Solo vale para la publicidad FORZADA** (banner e intersticial). Desde el 2026-09-06 la
     * oferta de un recompensado pregunta a [rewardedOfferedFlow], que no mira la compra: era este
     * mismo flag el que dejaba sin vídeos de monedas a quien había pagado.
     */
    val adsOfferedFlow: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(
            _adsRemoved,
            com.cukiti.teikafusion.data.RemoteConfigManager.snapshot,
            // CONSENTIMIENTO (UMP): si el jugador del EEE/RU dice NO —o si aún no ha contestado—,
            // no se le puede pedir publicidad a AdMob. Eso incluye el BANNER, que no pasa por
            // `loadAd` y se cargaba por su cuenta: sin esta condición, rechazar el formulario
            // dejaba el banner pidiendo anuncios igual. Arranca en `false` a propósito — mientras
            // el consentimiento no está resuelto, la respuesta correcta es "no".
            ConsentManager.canRequestAds
        ) { removed, config, consented -> consented && config.adsEnabled && !removed }
            .stateIn(
                kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
                ),
                kotlinx.coroutines.flow.SharingStarted.Eagerly,
                false
            )

    /**
     * **¿Se le puede OFRECER UN RECOMPENSADO a este jugador ahora mismo?**
     *
     * Igual que [adsOfferedFlow] pero **sin** la compra de «Quitar anuncios»: el vídeo voluntario es
     * la forma gratuita de ganar teikoins y quitársela a quien paga es cobrarle por perder algo. Lo
     * que sí lo apaga es el kill-switch remoto y la falta de consentimiento — las dos condiciones
     * que no son negociables.
     *
     * Quien pregunte por el recompensado (revivir, doblar monedas, la barra superior) usa **este**;
     * el banner y el intersticial siguen con [adsOfferedFlow].
     */
    val rewardedOfferedFlow: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(
            com.cukiti.teikafusion.data.RemoteConfigManager.snapshot,
            ConsentManager.canRequestAds
        ) { config, consented -> consented && config.adsEnabled }
            .stateIn(
                kotlinx.coroutines.CoroutineScope(
                    kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default
                ),
                kotlinx.coroutines.flow.SharingStarted.Eagerly,
                false
            )

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    /** Último hueco que pidió anuncio: etiqueta el ingreso de la impresión (P-21). */
    @Volatile private var lastPlacement: String = "unknown"

    private val _isReady = MutableStateFlow(false)
    val isReadyFlow: StateFlow<Boolean> = _isReady.asStateFlow()

    private var interstitialAd: InterstitialAd? = null
    private var isLoadingInterstitial = false

    /** ¿Hay un intersticial cargado AHORA? (equivalente de [isReadyFlow] para el otro formato.) */
    private val _isInterstitialReady = MutableStateFlow(false)
    val isInterstitialReadyFlow: StateFlow<Boolean> = _isInterstitialReady.asStateFlow()

    /**
     * Instante del último anuncio a PANTALLA COMPLETA que se llegó a mostrar, sea recompensado o
     * intersticial. 0 = ninguno en esta sesión.
     *
     * Vive aquí y no en la regla de cadencia porque los dos formatos se muestran desde sitios
     * distintos y ninguno conoce al otro: sin un sello común, quien veía el vídeo de revivir y acto
     * seguido perdía se comía **dos** pantallazos seguidos. Lo consume
     * [com.cukiti.teikafusion.model.InterstitialCadence.isDue].
     */
    @Volatile var lastFullScreenAdAtMs: Long = 0L
        private set

    /**
     * Arranca el SDK. **No pide anuncio aquí**. MainActivity llama este método únicamente después de
     * cargar «Quitar anuncios», terminar el onboarding y resolver UMP; así ni la inicialización ni
     * el primer inventario se adelantan a esas compuertas.
     */
    fun init(context: Context) {
        try {
            MobileAds.initialize(context) {}
        } catch (_: Exception) {}
    }

    /**
     * Asegura que hay inventario cargado: recompensado **y** intersticial.
     *
     * Los dos formatos van juntos a propósito. El intersticial salta solo, en un corte que el
     * jugador no anuncia, así que no hay ningún momento en el que pedirlo "justo antes": o está
     * precargado o no se muestra. Metiéndolo aquí, los tres sitios que ya cebaban el recompensado
     * (resolución del consentimiento y cierre de cada anuncio) ceban también
     * este, y no hay una segunda lista de call sites que mantener.
     */
    fun loadAd(context: Context) {
        loadRewarded(context)
        loadInterstitial(context)
    }

    private fun loadRewarded(context: Context) {
        // CONSENTIMIENTO PRIMERO (UMP): pedir inventario antes de que el jugador del EEE haya
        // respondido al formulario es exactamente lo que la política de Google prohíbe. Quien
        // resuelve el consentimiento (`ConsentManager.gather`) vuelve a llamar aquí al terminar.
        if (!ConsentManager.canRequestAds.value) return
        if (!rewardedEnabled) {
            // Apagado en remoto: se suelta el anuncio en memoria para que la oferta desaparezca
            // también en una sesión que ya estaba en marcha.
            rewardedAd = null
            _isReady.value = false
            return
        }
        if (isLoading || rewardedAd != null) return
        isLoading = true
        try {
            RewardedAd.load(
                context.applicationContext,
                adUnitId(context),
                AdRequest.Builder().build(),
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        // P-21: el valor REAL de la impresión. Se engancha al cargar porque el
                        // listener vive en el anuncio, no en el manager.
                        ad.setOnPaidEventListener { value ->
                            Analytics.trackAdRevenue(
                                placement = lastPlacement,
                                format = "rewarded",
                                valueMicros = value.valueMicros,
                                currency = value.currencyCode,
                                precision = value.precisionType
                            )
                        }
                        rewardedAd = ad
                        isLoading = false
                        _isReady.value = true
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        isLoading = false
                        _isReady.value = false
                    }
                }
            )
        } catch (_: Exception) { isLoading = false }
    }

    private fun loadInterstitial(context: Context) {
        if (!ConsentManager.canRequestAds.value) return
        if (!forcedAdsEnabled) {
            interstitialAd = null
            _isInterstitialReady.value = false
            return
        }
        if (isLoadingInterstitial || interstitialAd != null) return
        isLoadingInterstitial = true
        try {
            InterstitialAd.load(
                context.applicationContext,
                interstitialUnitId(context),
                AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        ad.setOnPaidEventListener { value ->
                            Analytics.trackAdRevenue(
                                placement = lastPlacement,
                                format = "interstitial",
                                valueMicros = value.valueMicros,
                                currency = value.currencyCode,
                                precision = value.precisionType
                            )
                        }
                        interstitialAd = ad
                        isLoadingInterstitial = false
                        _isInterstitialReady.value = true
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        isLoadingInterstitial = false
                        _isInterstitialReady.value = false
                    }
                }
            )
        } catch (_: Exception) { isLoadingInterstitial = false }
    }

    /**
     * Muestra el INTERSTICIAL y devuelve **si se llegó a mostrar de verdad**.
     *
     * Ese booleano es el contrato importante: el llamante solo debe dar por gastado el turno de
     * anuncio cuando la respuesta es `true`. Si es `false` no ha pasado nada —no había inventario,
     * está apagado en remoto o el jugador pagó por quitarlos— y [onClosed] **no** se invoca, así que
     * el llamante continúa su acción normalmente sin esperar a nadie.
     *
     * Cuando devuelve `true`, [onClosed] se invoca exactamente una vez al salir el anuncio de la
     * pantalla (cerrado o fallo al mostrarse): ahí es donde va la acción que el anuncio interrumpe
     * —pasar de nivel, volver a Inicio—, nunca antes, o el jugador vería la pantalla siguiente
     * moverse por debajo del anuncio.
     *
     * **No se llama nunca desde la partida en curso**: solo desde cortes naturales. Ver
     * [com.cukiti.teikafusion.model.InterstitialCadence].
     */
    fun showInterstitialAd(
        activity: Activity,
        placement: String = "unknown",
        onClosed: () -> Unit
    ): Boolean {
        if (!forcedAdsEnabled) return false
        val ad = interstitialAd ?: run {
            // Sin inventario: se ceba para el próximo corte, pero este pasa limpio.
            loadInterstitial(activity.applicationContext)
            return false
        }
        lastPlacement = placement
        interstitialAd = null
        _isInterstitialReady.value = false
        lastFullScreenAdAtMs = System.currentTimeMillis()

        var done = false
        fun finishOnce() {
            if (done) return
            done = true
            onClosed()
            loadInterstitial(activity.applicationContext)
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() = finishOnce()
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Analytics.trackRewardedAdFailed(placement, "interstitial_show_failed_${error.code}")
                finishOnce()
            }
        }
        return try {
            ad.show(activity)
            true
        } catch (_: Exception) {
            Analytics.trackRewardedAdFailed(placement, "interstitial_exception")
            lastFullScreenAdAtMs = 0L   // no llegó a verse: no debe contar como hueco gastado
            loadInterstitial(activity.applicationContext)
            false
        }
    }

    /**
     * Muestra el anuncio recompensado. [onRewarded] se invoca si el usuario gana la recompensa;
     * [onClosed] SIEMPRE se invoca cuando el anuncio deja la pantalla (cerrado, fallo o no disponible),
     * para que el llamante pueda limpiar estado ligado a "anuncio en pantalla" sin quedar colgado.
     */
    fun showRewardedAd(
        activity: Activity,
        onRewarded: (coins: Int) -> Unit,
        onClosed: () -> Unit = {},
        /** Dónde se pidió el anuncio (revive, tienda…): sin esto no se sabe qué sitio monetiza. */
        placement: String = "unknown"
    ) {
        if (!rewardedEnabled) {
            Analytics.trackRewardedAdFailed(placement, "disabled_remotely")
            onClosed(); return
        }
        val ad = rewardedAd ?: run {
            Analytics.trackRewardedAdFailed(placement, "not_loaded")
            onClosed(); return
        }
        lastPlacement = placement
        rewardedAd = null
        _isReady.value = false
        // El recompensado también cuenta para el hueco del intersticial: dos pantallazos seguidos
        // (ver el vídeo de revivir y perder acto seguido) es justo lo que el hueco existe para evitar.
        lastFullScreenAdAtMs = System.currentTimeMillis()

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() { onClosed(); loadAd(activity.applicationContext) }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Analytics.trackRewardedAdFailed(placement, "show_failed_${error.code}")
                onClosed(); loadAd(activity.applicationContext)
            }
        }

        try {
            ad.show(activity) {
                val coins = rewardCoins
                Analytics.trackRewardedAdWatched(placement, coins)
                onRewarded(coins)
            }
        } catch (_: Exception) {
            Analytics.trackRewardedAdFailed(placement, "exception")
            onClosed(); loadAd(activity.applicationContext)
        }
    }
}
