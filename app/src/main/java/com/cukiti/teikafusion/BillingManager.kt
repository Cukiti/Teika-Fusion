package com.cukiti.teikafusion

import android.app.Activity
import android.content.Context
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** Qué acaba de comprar el jugador. */
sealed interface PurchaseResult {
    /**
     * Pack de monedas (consumible): se puede volver a comprar.
     *
     * Lleva el [productId] además de las monedas porque el **bono de bienvenida** se decide por
     * pack ([com.cukiti.teikafusion.model.StorePacks.welcomeBonusFor]): la primera compra de cada
     * uno paga el doble, y sin saber cuál es no se puede saber si le toca.
     */
    data class Coins(val productId: String, val teikoins: Int) : PurchaseResult
    /** Quitar anuncios (permanente): se compra UNA vez y se restaura al reinstalar. */
    data object AdsRemoved : PurchaseResult
    /**
     * **EL PAQUETE INICIAL** (2026-09-06). No consumible, como [AdsRemoved], así que Play lo
     * reenvía en **cada arranque** — el consumidor tiene que ser idempotente o regalaría su
     * contenido una vez por sesión. Ver `GameViewModel.applyStarterPack`.
     */
    data object StarterPack : PurchaseResult
}

object BillingManager {
    const val COINS_250  = "coins_250"
    const val COINS_1200 = "coins_1200"
    const val COINS_5000 = "coins_5000"

    /**
     * Quitar anuncios. **NO consumible**, a diferencia de los packs de monedas: se compra una sola
     * vez y hay que RECONOCERLO (`acknowledgePurchase`), no consumirlo. Si se consumiera, Play lo
     * volvería a ofrecer a la venta y el jugador podría pagar dos veces por lo mismo.
     */
    const val REMOVE_ADS = "remove_ads"

    /**
     * **EL PAQUETE INICIAL**. Como [REMOVE_ADS], **no consumible**: se compra una sola vez en la
     * vida de la cuenta y se restaura al reinstalar. Su contenido lo define
     * [com.cukiti.teikafusion.model.StorePacks].
     */
    const val STARTER_PACK = com.cukiti.teikafusion.model.StorePacks.STARTER_PACK_ID

    /** Los productos que NO se consumen: se reconocen y Play los devuelve para siempre. */
    private const val RETRY_MIN_MS = 1_000L
    private const val RETRY_MAX_MS = 60_000L

    private val NON_CONSUMABLE = setOf(REMOVE_ADS, STARTER_PACK)

    private val COINS_MAP   = mapOf(COINS_250 to 250, COINS_1200 to 1200, COINS_5000 to 5000)
    // El orden manda en la tienda: primero el pack inicial (es el gancho), luego quitar anuncios y
    // después las monedas de menos a más.
    private val PRODUCT_IDS = listOf(STARTER_PACK, REMOVE_ADS, COINS_250, COINS_1200, COINS_5000)

    // Channel (no SharedFlow): son EVENTOS DE DINERO con un único consumidor (GameViewModel) que se
    // suscribe TARDE (espera coinsLoaded). Un SharedFlow sin replay descarta la emisión si nadie
    // colecta en ese instante — una compra pendiente procesada al arrancar se perdía sin acreditar.
    // El Channel guarda lo emitido hasta que el consumidor llegue, y entrega exactamente una vez.
    private val _purchaseEvents = Channel<PurchaseResult>(Channel.UNLIMITED)
    val purchaseEvents: Flow<PurchaseResult> = _purchaseEvents.receiveAsFlow()

    private val _products = MutableStateFlow<List<ProductDetails>>(emptyList())
    val products: StateFlow<List<ProductDetails>> = _products.asStateFlow()

    private var billingClient: BillingClient? = null

    // ── RECONEXIÓN SOLA (2026-10-01, sustituye al botón «Restaurar compras», P-432 retirada) ─────
    // Antes, si Play no respondía al arrancar (sin red, Play Store actualizándose) o la conexión se
    // caía, nadie volvía a intentarlo hasta el siguiente arranque: ni catálogo ni compras pendientes.
    // Ahora se reintenta con espera creciente (1 s, 2 s, 4 s… hasta 60 s) y, además, en cada vuelta
    // a primer plano ([refresh]), que es lo que recomienda Google para las compras hechas fuera.
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var connecting = false
    private var retryDelayMs = RETRY_MIN_MS
    private val reconnect = Runnable { connect() }

    fun init(context: Context) {
        billingClient = BillingClient.newBuilder(context.applicationContext)
            .setListener { billingResult, purchases ->
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases?.forEach { handlePurchase(it) }
                }
            }
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
            )
            .build()
        connect()
    }

    private fun connect() {
        val client = billingClient ?: return
        if (client.isReady || connecting) return
        connecting = true
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connecting = false
                when (result.responseCode) {
                    BillingClient.BillingResponseCode.OK -> {
                        retryDelayMs = RETRY_MIN_MS
                        queryProducts()
                        processPendingPurchases()
                    }
                    // Este aparato no tiene Play Billing (sin Play Store): reintentar cada minuto no
                    // lo arreglará. Solo se vuelve a probar al volver a primer plano.
                    BillingClient.BillingResponseCode.BILLING_UNAVAILABLE -> Unit
                    else -> scheduleReconnect()
                }
            }
            override fun onBillingServiceDisconnected() {
                connecting = false
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        main.removeCallbacks(reconnect)
        main.postDelayed(reconnect, retryDelayMs)
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_MS)
    }

    /**
     * Al volver a primer plano (`MainActivity.onResume`): si hay conexión, vuelve a pedir las compras
     * (y el catálogo si no llegó); si no, reconecta ya, sin esperar al reintento programado.
     * [handlePurchase] es idempotente, así que pedirlas de más no entrega nada dos veces.
     */
    fun refresh() {
        val client = billingClient ?: return
        if (client.isReady) {
            if (_products.value.isEmpty()) queryProducts()
            processPendingPurchases()
        } else {
            main.removeCallbacks(reconnect)
            retryDelayMs = RETRY_MIN_MS
            connect()
        }
    }

    /**
     * Kill-switch remoto de las compras. Apagarlo vacía el catálogo, y como la tienda se pinta a
     * partir de [products], los packs desaparecen sin tocar la UI. Se aplica aquí y no en la
     * pantalla para que ninguna ruta de compra sobreviva por olvido.
     *
     * OJO: NO apaga [processPendingPurchases]. Una compra ya cobrada por Google tiene que
     * acreditarse aunque la tienda esté cerrada — si no, el jugador paga y no recibe nada.
     */
    private val billingEnabled: Boolean
        get() = com.cukiti.teikafusion.data.RemoteConfigManager.snapshot.value.billingEnabled

    private fun queryProducts() {
        if (!billingEnabled) {
            _products.value = emptyList()
            return
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(PRODUCT_IDS.map { id ->
                QueryProductDetailsParams.Product.newBuilder()
                    .setProductId(id)
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            })
            .build()
        billingClient?.queryProductDetailsAsync(params) { result, details ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                // Billing 8+: el callback entrega QueryProductDetailsResult, no la lista directa.
                val lista = details.productDetailsList
                _products.value = lista.sortedBy { PRODUCT_IDS.indexOf(it.productId) }
                // ⚠️ EL FALLO MÁS SILENCIOSO DE LA TIENDA (2026-09-07). `GameShopScreen` pinta
                // EXACTAMENTE lo que Play devuelve: un producto que no llega no da error, no deja
                // hueco y no se distingue de «todavía no ha respondido» — la sección entera
                // simplemente no existe. Sin esta traza, «no veo los teikoins en la tienda» no tiene
                // forma de diagnosticarse desde dentro del juego.
                //
                // Que falten NO significa que estén mal dados de alta: Play no los sirve hasta que
                // hay un binario con el mismo `applicationId` publicado en algún canal (aunque sea
                // pruebas internas) y ha pasado un rato desde que se activaron.
                val faltan = PRODUCT_IDS - lista.map { it.productId }.toSet()
                if (faltan.isNotEmpty()) {
                    android.util.Log.w(
                        "BillingManager",
                        "Play NO devolvió ${faltan.size} de ${PRODUCT_IDS.size} productos: $faltan · " +
                            "sus tarjetas NO se pintarán. Revisa que estén ACTIVOS en Play Console y " +
                            "que haya un AAB publicado en un canal con este applicationId."
                    )
                } else {
                    android.util.Log.i("BillingManager", "Play devolvió los ${lista.size} productos")
                }
            } else {
                // Antes esta rama no existía: un error de Play dejaba la tienda vacía sin una línea.
                android.util.Log.w(
                    "BillingManager",
                    "queryProductDetails falló (código ${result.responseCode}): ${result.debugMessage} · " +
                        "la tienda se queda sin productos de dinero real"
                )
            }
        }
    }

    /**
     * Se llama en cada arranque. Cubre dos casos a la vez:
     *   · packs de monedas pendientes de consumir (una compra que se quedó a medias);
     *   · **restauración de «quitar anuncios»** tras reinstalar o cambiar de móvil — al no ser
     *     consumible, sigue apareciendo aquí para siempre, que es justo lo que permite devolverle
     *     al jugador algo que ya pagó sin que tenga que hacer nada.
     */
    private fun processPendingPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient?.queryPurchasesAsync(params) { _, purchases ->
            purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                .forEach { handlePurchase(it) }
        }
    }

    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return

        // ── Productos NO consumibles: quitar anuncios y el paquete inicial ────────────────
        // Se RECONOCEN (no se consumen) para que Play los considere entregados y deje de ofrecerlos.
        // El evento se emite SIEMPRE, esté ya reconocido o no: es como se restaura la compra al
        // reinstalar. Y por eso mismo **los dos consumidores tienen que ser idempotentes** — con
        // «quitar anuncios» es gratis (encender un flag ya encendido no hace nada), pero el paquete
        // inicial regala cinco mil teikoins, así que su marca de entregado es obligatoria y vive en
        // la cuenta (`CloudSaveData.starterPackGranted`).
        val noConsumible = purchase.products.firstOrNull { it in NON_CONSUMABLE }
        if (noConsumible != null) {
            _purchaseEvents.trySend(
                if (noConsumible == STARTER_PACK) PurchaseResult.StarterPack
                else PurchaseResult.AdsRemoved
            )
            if (!purchase.isAcknowledged) {
                val ackParams = com.android.billingclient.api.AcknowledgePurchaseParams.newBuilder()
                    .setPurchaseToken(purchase.purchaseToken)
                    .build()
                billingClient?.acknowledgePurchase(ackParams) { result ->
                    if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                        val offer = _products.value
                            .firstOrNull { it.productId == noConsumible }
                            ?.oneTimePurchaseOfferDetails
                        Analytics.trackPurchase(
                            productId = noConsumible,
                            priceMicros = offer?.priceAmountMicros ?: 0L,
                            currency = offer?.priceCurrencyCode ?: "USD",
                            teikoins = if (noConsumible == STARTER_PACK)
                                com.cukiti.teikafusion.model.StorePacks.STARTER_COINS else 0
                        )
                    }
                }
            }
            return
        }

        val teikoins = purchase.products.firstNotNullOfOrNull { COINS_MAP[it] } ?: return

        // CONSUMIR, no solo acknowledgear: los packs de monedas son consumibles (se compran muchas
        // veces). Con acknowledgePurchase Google Play los trataba como no-consumibles ("ya lo
        // posees") y la SEGUNDA compra del mismo pack fallaba con ITEM_ALREADY_OWNED — cada pack
        // solo se podía comprar UNA vez en la vida. consumeAsync acredita Y libera el producto
        // para recomprarlo (consumir implica acknowledge).
        val consumeParams = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient?.consumeAsync(consumeParams) { result, _ ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                _purchaseEvents.trySend(
                    PurchaseResult.Coins(
                        productId = purchase.products.firstOrNull { it in COINS_MAP }.orEmpty(),
                        teikoins = teikoins,
                    )
                )
                // Ingreso REAL. Se registra aquí, tras confirmar el consumo, y no al lanzar el
                // diálogo de Play: solo lo que Google confirmó es dinero. El precio sale del
                // ProductDetails ya consultado; si aún no está, se envía sin importe antes que
                // perder el evento (una compra sin `value` sigue contando para la conversión).
                val productId = purchase.products.firstOrNull().orEmpty()
                val offer = _products.value
                    .firstOrNull { it.productId == productId }
                    ?.oneTimePurchaseOfferDetails
                Analytics.trackPurchase(
                    productId = productId,
                    priceMicros = offer?.priceAmountMicros ?: 0L,
                    currency = offer?.priceCurrencyCode ?: "USD",
                    teikoins = teikoins
                )
            }
        }
    }

    fun purchase(activity: Activity, productDetails: ProductDetails) {
        if (!billingEnabled) return   // por si el catálogo quedó en pantalla al apagarse en remoto
        // Intento: comparado con `purchase`, da la tasa de abandono en la pasarela de Play.
        Analytics.trackPurchaseAttempt(productDetails.productId)
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(productDetails)
                        .build()
                )
            )
            .build()
        billingClient?.launchBillingFlow(activity, params)
    }

    fun release() {
        main.removeCallbacks(reconnect)
        billingClient?.endConnection()
        billingClient = null
    }
}
