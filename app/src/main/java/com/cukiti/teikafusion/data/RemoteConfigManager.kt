package com.cukiti.teikafusion.data

import com.google.firebase.Firebase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Configuración remota: interruptores de emergencia y balance afinable sin publicar versión.
 *
 * Antes solo tenía dos claves de balance y **ningún kill-switch**: si AdMob empezaba a crashear o
 * las Cloud Functions fallaban, la única salida era compilar, subir a Play y esperar el despliegue.
 *
 * **Todo arranca ENCENDIDO** (decisión 2026-07-28). Los valores por defecto van compilados en el
 * APK, así que un jugador sin red juega el juego completo; el interruptor remoto solo puede APAGAR.
 * El precio es que un apagado de emergencia tarda hasta la primera lectura con red — segundos —, y a
 * cambio nadie ve el juego mutilado por tener mala cobertura.
 */
data class RemoteConfigSnapshot(
    // ── Balance ────────────────────────────────────────────────────────────────
    val gatingStarsRequiredPerLevel: Int,
    val fusionCostCoins: Int,
    /** Multiplicador del coste en teikoins de mejorar una habilidad (100 = sin cambio). */
    val abilityUpgradeCostPercent: Int,
    /** Monedas que entrega un anuncio recompensado. */
    val rewardedAdCoins: Int,
    /** Multiplicador de la cantidad de cristales del drop de fin de nivel (100 = sin cambio). */
    val crystalDropPercent: Int,
    /** Multiplicador de la velocidad del muro del Infinito (100 = sin cambio). */
    val tideSpeedPercent: Int,

    // ── Kill-switches ──────────────────────────────────────────────────────────
    val adsEnabled: Boolean,
    val billingEnabled: Boolean,
    val cloudSyncEnabled: Boolean,
    val leaderboardEnabled: Boolean,

    // ── Actualización forzada ──────────────────────────────────────────────────
    /** Por debajo de este `versionCode` se pide actualizar. 0 = sin exigencia. */
    val minSupportedVersionCode: Int,
    /** Texto del aviso. Vacío = usar el de por defecto de la app. */
    val updateMessage: String
) {
    companion object {
        val DEFAULTS = RemoteConfigSnapshot(
            gatingStarsRequiredPerLevel = 1,
            fusionCostCoins = 0,
            abilityUpgradeCostPercent = 100,
            rewardedAdCoins = com.cukiti.teikafusion.AdManager.REWARD_COINS,
            crystalDropPercent = 100,
            tideSpeedPercent = 100,
            adsEnabled = true,
            billingEnabled = true,
            cloudSyncEnabled = true,
            leaderboardEnabled = true,
            minSupportedVersionCode = 0,
            updateMessage = ""
        )
    }
}

object RemoteConfigManager {
    // Una hora entre lecturas: suficiente para un apagado de emergencia (el listener en tiempo real
    // de abajo lo hace inmediato de todas formas) y sin castigar la cuota.
    private const val MIN_FETCH_INTERVAL_SECONDS = 3600L

    // Claves de la consola. Se mantienen en un solo sitio para que un typo no pase desapercibido:
    // `getLong` de una clave inexistente devuelve 0 en silencio, que aquí significaría "apagado".
    private const val KEY_GATING_STARS = "gating_stars_required_per_level"
    private const val KEY_FUSION_COST = "fusion_cost_coins"
    private const val KEY_ABILITY_COST_PCT = "ability_upgrade_cost_percent"
    private const val KEY_REWARDED_COINS = "rewarded_ad_coins"
    private const val KEY_CRYSTAL_DROP_PCT = "crystal_drop_percent"
    private const val KEY_TIDE_SPEED_PCT = "tide_speed_percent"
    private const val KEY_ADS_ENABLED = "ads_enabled"
    private const val KEY_BILLING_ENABLED = "billing_enabled"
    private const val KEY_CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
    private const val KEY_LEADERBOARD_ENABLED = "leaderboard_enabled"
    private const val KEY_MIN_VERSION = "min_supported_version_code"
    private const val KEY_UPDATE_MESSAGE = "update_message"

    private val defaultsMap: Map<String, Any> = with(RemoteConfigSnapshot.DEFAULTS) {
        mapOf(
            KEY_GATING_STARS to gatingStarsRequiredPerLevel.toLong(),
            KEY_FUSION_COST to fusionCostCoins.toLong(),
            KEY_ABILITY_COST_PCT to abilityUpgradeCostPercent.toLong(),
            KEY_REWARDED_COINS to rewardedAdCoins.toLong(),
            KEY_CRYSTAL_DROP_PCT to crystalDropPercent.toLong(),
            KEY_TIDE_SPEED_PCT to tideSpeedPercent.toLong(),
            KEY_ADS_ENABLED to adsEnabled,
            KEY_BILLING_ENABLED to billingEnabled,
            KEY_CLOUD_SYNC_ENABLED to cloudSyncEnabled,
            KEY_LEADERBOARD_ENABLED to leaderboardEnabled,
            KEY_MIN_VERSION to minSupportedVersionCode.toLong(),
            KEY_UPDATE_MESSAGE to updateMessage
        )
    }

    private val _snapshot = MutableStateFlow(RemoteConfigSnapshot.DEFAULTS)
    val snapshot: StateFlow<RemoteConfigSnapshot> get() = _snapshot

    private var initialized = false

    // Lint avisa de `StaticFieldLeak` por tener un objeto de Firebase en un campo estático. Aquí es un
    // FALSO POSITIVO, y por eso se suprime en vez de reestructurar:
    //  · [init] no recibe NINGÚN Context — no hay forma de que se cuele el de una Activity.
    //  · `Firebase.remoteConfig` se obtiene de `FirebaseApp`, que sostiene el Context de *Application*.
    //  · Ese singleton vive tanto como el proceso de todas formas, así que no hay nada que "filtrar".
    @Suppress("StaticFieldLeak")
    private var remoteConfig: FirebaseRemoteConfig? = null

    fun init() {
        if (initialized) return
        initialized = true
        try {
            val config = Firebase.remoteConfig
            config.setConfigSettingsAsync(remoteConfigSettings {
                minimumFetchIntervalInSeconds = MIN_FETCH_INTERVAL_SECONDS
            })
            config.setDefaultsAsync(defaultsMap)
            config.fetchAndActivate().addOnCompleteListener {
                _snapshot.value = readSnapshot(config)
            }
            config.addOnConfigUpdateListener(object : ConfigUpdateListener {
                override fun onUpdate(configUpdate: ConfigUpdate) {
                    config.activate().addOnCompleteListener {
                        _snapshot.value = readSnapshot(config)
                    }
                }

                override fun onError(error: FirebaseRemoteConfigException) {}
            })
            remoteConfig = config
        } catch (_: Exception) {
        }
    }

    fun updates(): Flow<RemoteConfigSnapshot> = callbackFlow {
        trySend(_snapshot.value)
        val config = remoteConfig
        if (config == null) {
            awaitClose { }
            return@callbackFlow
        }
        val listener = object : ConfigUpdateListener {
            override fun onUpdate(configUpdate: ConfigUpdate) {
                config.activate().addOnCompleteListener {
                    val snap = readSnapshot(config)
                    _snapshot.value = snap
                    trySend(snap)
                }
            }

            override fun onError(error: FirebaseRemoteConfigException) {}
        }
        val registration = config.addOnConfigUpdateListener(listener)
        awaitClose { registration.remove() }
    }

    private fun readSnapshot(c: FirebaseRemoteConfig) = RemoteConfigSnapshot(
        gatingStarsRequiredPerLevel = c.getLong(KEY_GATING_STARS).toInt(),
        fusionCostCoins = c.getLong(KEY_FUSION_COST).toInt(),
        // Los porcentajes se acotan: un 0 escrito por error en la consola no puede dejar el juego
        // en un estado absurdo (coste cero, muro parado, drop vacío).
        abilityUpgradeCostPercent = c.getLong(KEY_ABILITY_COST_PCT).toInt().coerceIn(10, 500),
        rewardedAdCoins = c.getLong(KEY_REWARDED_COINS).toInt().coerceIn(0, 10_000),
        crystalDropPercent = c.getLong(KEY_CRYSTAL_DROP_PCT).toInt().coerceIn(10, 500),
        tideSpeedPercent = c.getLong(KEY_TIDE_SPEED_PCT).toInt().coerceIn(25, 400),
        adsEnabled = c.getBoolean(KEY_ADS_ENABLED),
        billingEnabled = c.getBoolean(KEY_BILLING_ENABLED),
        cloudSyncEnabled = c.getBoolean(KEY_CLOUD_SYNC_ENABLED),
        leaderboardEnabled = c.getBoolean(KEY_LEADERBOARD_ENABLED),
        minSupportedVersionCode = c.getLong(KEY_MIN_VERSION).toInt(),
        updateMessage = c.getString(KEY_UPDATE_MESSAGE)
    )
}
