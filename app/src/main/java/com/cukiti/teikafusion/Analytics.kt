package com.cukiti.teikafusion

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Analítica de producto y de NEGOCIO.
 *
 * Hasta 2026-07-28 solo había ocho eventos, todos de gameplay: ni una compra, ni un anuncio, ni un
 * inicio de sesión. Sin `purchase` no existen ARPU, LTV ni ROAS, así que ninguna campaña de
 * adquisición se puede evaluar; y sin `level_start`/`level_fail` no se sabe qué nivel expulsa
 * jugadores, que es *el* dato de un juego de puzles.
 *
 * Todo evento es best-effort y silencioso: la analítica nunca puede tumbar una partida.
 */
object Analytics {
    private var fa: FirebaseAnalytics? = null

    fun init(context: Context) {
        try {
            fa = FirebaseAnalytics.getInstance(context)
        } catch (_: Exception) {}
    }

    // ── Identidad ────────────────────────────────────────────────────────────────────────────
    // Se propaga a Crashlytics también: sin esto, un crash no se puede atar a un jugador ni cruzar
    // con su guardado en la nube, que es justo lo que hace falta para reproducir un fallo raro.

    /** Ata la sesión al UID de Firebase (o la desata al cerrar sesión / borrar cuenta). */
    fun setPlayer(uid: String?) {
        try { fa?.setUserId(uid) } catch (_: Exception) {}
        try { FirebaseCrashlytics.getInstance().setUserId(uid ?: "") } catch (_: Exception) {}
    }

    /** Propiedades de segmentación: permiten cortar cualquier métrica por perfil de jugador. */
    fun setPlayerProperties(maxLevel: Int, linkedProvider: String?, companionsOwned: Int) {
        try {
            fa?.setUserProperty("max_level_bucket", levelBucket(maxLevel))
            fa?.setUserProperty("account_type", linkedProvider ?: "guest")
            fa?.setUserProperty("companions_owned", companionsOwned.toString())
        } catch (_: Exception) {}
        crashKeys(
            "max_level" to maxLevel,
            "account_type" to (linkedProvider ?: "guest")
        )
    }

    /**
     * Contexto que acompaña a un crash. Analytics no lo necesita (ya va en los eventos), pero en
     * Crashlytics es la diferencia entre "crasheó" y "crasheó en el bioma volcán con la marea alta".
     */
    fun crashKeys(vararg pairs: Pair<String, Any>) {
        try {
            val c = FirebaseCrashlytics.getInstance()
            pairs.forEach { (k, v) ->
                when (v) {
                    is Int -> c.setCustomKey(k, v)
                    is Long -> c.setCustomKey(k, v)
                    is Float -> c.setCustomKey(k, v)
                    is Double -> c.setCustomKey(k, v)
                    is Boolean -> c.setCustomKey(k, v)
                    else -> c.setCustomKey(k, v.toString())
                }
            }
        } catch (_: Exception) {}
    }

    /** Los niveles sueltos son demasiado granulares para segmentar; las bandas sí sirven. */
    private fun levelBucket(level: Int): String = when {
        level <= 1 -> "00_nuevo"
        level < 10 -> "01_9"
        level < 25 -> "10_24"
        level < 50 -> "25_49"
        level < 100 -> "50_99"
        level < 200 -> "100_199"
        else -> "200_mas"
    }

    // ── Funnel de nivel ──────────────────────────────────────────────────────────────────────

    /**
     * **CUÁNTO TARDA EL JUEGO EN ESTAR JUGABLE** (P-179, 2026-09-04).
     *
     * Del `Application.onCreate` al instante en que el splash suelta y aparece Inicio. Es la única
     * medida de arranque que existe: hasta hoy el único dato era un `simpleperf` a mano de agosto, y
     * las dos mejoras de arranque de esta semana (adelantar la ronda, sacar el parseo del tablero
     * del hilo principal) se hicieron **a ciegas** — correctas por razonamiento, pero sin nadie que
     * pudiera decir si se notan.
     *
     * Va a Analytics y no a un log porque lo que importa no es el número de UN móvil: es la
     * distribución real por gama de dispositivo, que es la que decide si el arranque está bien o
     * mal. [cold] distingue el arranque de verdad del que solo recrea la Activity.
     */
    fun trackStartupReady(millis: Long, cold: Boolean) = log("startup_ready") {
        putLong("millis", millis)
        putInt("cold", if (cold) 1 else 0)
    }

    fun trackGameStart(level: Int) = log("game_start") {
        putInt("level", level)
    }

    /** Arranque de un intento de nivel: el denominador de la tasa de superación. */
    fun trackLevelStart(level: Int, biome: String, attempt: Int) = log("level_start") {
        putInt("level", level)
        putString("biome", biome)
        putInt("attempt", attempt)
    }

    /**
     * Derrota, CON LA CAUSA. Sin la causa no se distingue "el nivel es demasiado difícil" de
     * "el jugador se quedó sin espacio por un fallo de diseño del tablero".
     */
    fun trackLevelFail(level: Int, biome: String, reason: String, attempt: Int, targetsCollected: Int) =
        log("level_fail") {
            putInt("level", level)
            putString("biome", biome)
            putString("reason", reason)
            putInt("attempt", attempt)
            // Cuánto avanzó antes de caer: separa "perdió al principio" de "se quedó a una estrella".
            putInt("targets_collected", targetsCollected)
        }

    fun trackGameOver(score: Int, level: Int, teikas: Int) = log("game_over") {
        putInt("score", score)
        putInt("level", level)
        putInt("teikas_formed", teikas)
    }

    /** Nivel COMPLETADO (la victoria; game_over solo cubre derrotas): el evento de funnel clave. */
    fun trackLevelComplete(level: Int, score: Int, stars: Int) = log("level_complete") {
        putInt("level", level)
        putInt("score", score)
        putInt("stars", stars)
    }

    // (ERRADICADO 2026-08-09, con el modo WALLBLOCK) `trackInfiniteRun`: el evento
    // `infinite_run_end` con la profundidad alcanzada. Los datos ya recogidos siguen en la consola.

    fun trackReviveUsed() = log("revive_used", null)

    // ── Monetización ─────────────────────────────────────────────────────────────────────────

    /**
     * Compra confirmada. Usa el evento ESTÁNDAR de Firebase (`purchase` con `value`/`currency`),
     * que es el único que alimenta los informes de ingresos y el ROAS de las redes de anuncios.
     * Un evento propio no serviría: quedaría fuera de esos informes.
     */
    fun trackPurchase(productId: String, priceMicros: Long, currency: String, teikoins: Int) =
        log(FirebaseAnalytics.Event.PURCHASE) {
            putString(FirebaseAnalytics.Param.TRANSACTION_ID, productId)
            putDouble(FirebaseAnalytics.Param.VALUE, priceMicros / 1_000_000.0)
            putString(FirebaseAnalytics.Param.CURRENCY, currency)
            putString(FirebaseAnalytics.Param.ITEM_ID, productId)
            putString(FirebaseAnalytics.Param.ITEM_NAME, "teikoins_$teikoins")
            putString(FirebaseAnalytics.Param.ITEM_CATEGORY, "currency_pack")
            putInt("teikoins", teikoins)
        }

    /** El jugador abrió la tienda: numerador del funnel de conversión. */
    fun trackStoreOpen(source: String) = log("store_open") {
        putString("source", source)
    }

    /** Intento de compra (antes del diálogo de Play): mide el abandono en la pasarela. */
    fun trackPurchaseAttempt(productId: String) = log("purchase_attempt") {
        putString(FirebaseAnalytics.Param.ITEM_ID, productId)
    }

    /** Anuncio recompensado completado. `ad_impression` es el evento estándar de ingresos por ads. */
    fun trackRewardedAdWatched(placement: String, coins: Int) = log("rewarded_ad_watched") {
        putString("placement", placement)
        putInt("coins", coins)
    }

    fun trackRewardedAdFailed(placement: String, reason: String) = log("rewarded_ad_failed") {
        putString("placement", placement)
        putString("reason", reason)
    }

    /**
     * **INGRESO REAL de un impresión** (`OnPaidEventListener` de AdMob). Sin esto se sabía cuántos
     * anuncios se veían, pero no cuánto valen: ni qué hueco paga, ni cuánto vale un jugador, que es
     * el dato con el que se decide dónde tocar. Se manda como `ad_impression` —el evento ESTÁNDAR
     * que Firebase entiende como ingreso— con el valor en unidades de moneda (AdMob lo da en
     * micros: 1.000.000 = 1 unidad).
     */
    fun trackAdRevenue(
        placement: String,
        format: String,
        valueMicros: Long,
        currency: String,
        precision: Int
    ) = log("ad_impression") {
        putString("ad_platform", "AdMob")
        putString("ad_source", "AdMob")
        putString("ad_format", format)
        putString("ad_unit_name", placement)
        putDouble("value", valueMicros / 1_000_000.0)
        putString("currency", currency)
        putInt("precision_type", precision)
    }

    // ── Cuenta ───────────────────────────────────────────────────────────────────────────────

    /** Primer vínculo de una cuenta (evento estándar `sign_up`). */
    fun trackSignUp(provider: String) = log(FirebaseAnalytics.Event.SIGN_UP) {
        putString(FirebaseAnalytics.Param.METHOD, provider)
    }

    /** Vuelta de un jugador ya vinculado (evento estándar `login`). */
    fun trackLogin(provider: String) = log(FirebaseAnalytics.Event.LOGIN) {
        putString(FirebaseAnalytics.Param.METHOD, provider)
    }

    fun trackAccountDeleted() = log("account_deleted", null)

    // ── Progresión / economía ────────────────────────────────────────────────────────────────

    /**
     * Compañero desbloqueado gratis al completar su nivel (modelo V-04).
     * Evento `companion_unlock`. El antiguo `companion_purchase` (pago con teikoins) ya no se emite.
     */
    fun trackCompanionUnlock(id: String) = log("companion_unlock") {
        putString("companion_id", id)
    }

    /** Subida de rango de jugador (V-03). */
    fun trackRankUp(rankName: String, totalXp: Int) = log("rank_up") {
        putString("rank", rankName)
        putInt("total_xp", totalXp)
    }

    fun trackAbilityUpgrade(companionId: String, toLevel: Int, coinCost: Int) =
        log("ability_upgrade") {
            putString("companion_id", companionId)
            putInt("to_level", toLevel)
            putInt("coin_cost", coinCost)
        }


    fun trackTutorialDone() = log("tutorial_done", null)

    fun trackProgressReset() = log("progress_reset", null)

    private fun log(event: String, params: (Bundle.() -> Unit)?) {
        try {
            fa?.logEvent(event, params?.let { Bundle().apply(it) })
        } catch (_: Exception) {}
    }
}
