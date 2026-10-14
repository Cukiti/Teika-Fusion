package com.cukiti.teikafusion

import android.app.Activity
import android.content.Context
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * CONSENTIMIENTO DE PUBLICIDAD (UMP — el CMP de Google).
 *
 * Google **exige** un CMP certificado para servir anuncios a usuarios del EEE y Reino Unido. Sin
 * pasar por aquí, ese tráfico o no se monetiza o incumple la política de AdMob: no es una mejora de
 * producto, es requisito para publicar con anuncios.
 *
 * Cómo funciona, en tres pasos:
 *  1. `gather(activity)` pregunta al SDK qué hace falta según la región del jugador;
 *  2. si hace falta formulario, lo enseña **antes de que nadie pida un anuncio**;
 *  3. al terminar (o si no hacía falta), [canRequestAds] pasa a `true` y ahí es cuando se carga.
 *
 * Fuera del EEE el SDK responde "no requerido" y el jugador no ve absolutamente nada: el juego
 * arranca igual de rápido.
 *
 * **PENDIENTE DE CONSOLA (tuyo):** en AdMob → *Privacidad y mensajes* hay que crear el mensaje de
 * consentimiento (GDPR) y publicarlo para esta app. Hasta que exista, el SDK no tiene nada que
 * mostrar y `canRequestAds` se resuelve con lo que diga la consola por defecto.
 */
object ConsentManager {

    private val _canRequestAds = MutableStateFlow(false)
    /** `true` cuando ya es LEGAL pedir anuncios para este jugador. Nadie debe cargar antes. */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    @Volatile private var gathering = false

    /**
     * Pide (y muestra si procede) el formulario de consentimiento. Idempotente: se puede llamar en
     * cada arranque; el SDK cachea la decisión del jugador y solo vuelve a preguntar si caduca.
     *
     * [onResolved] se invoca SIEMPRE al terminar —haya formulario o no, con éxito o con error—, para
     * que el llamante dispare la primera carga de anuncios sin quedarse esperando.
     */
    fun gather(activity: Activity, onResolved: (canRequestAds: Boolean) -> Unit = {}) {
        if (gathering) return
        gathering = true
        val consentInfo = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .apply {
                // En DEBUG se puede forzar la geografía del EEE para ver el formulario sin viajar.
                if (BuildConfig.DEBUG) {
                    setConsentDebugSettings(
                        ConsentDebugSettings.Builder(activity)
                            .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                            .build()
                    )
                }
            }
            .build()

        consentInfo.requestConsentInfoUpdate(
            activity,
            params,
            {
                // Hay info fresca: si toca formulario, se enseña ahora (el SDK decide si aplica).
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    finish(consentInfo, onResolved)
                }
            },
            {
                // Sin red o error del SDK: no se bloquea al jugador. `canRequestAds` decide.
                finish(consentInfo, onResolved)
            }
        )
    }

    private fun finish(
        consentInfo: ConsentInformation,
        onResolved: (Boolean) -> Unit
    ) {
        gathering = false
        val allowed = runCatching { consentInfo.canRequestAds() }.getOrDefault(false)
        _canRequestAds.value = allowed
        onResolved(allowed)
    }

    /** ¿Se le puede ofrecer al jugador cambiar su decisión? (Requisito del EEE: debe poder.) */
    fun isPrivacyOptionsRequired(context: Context): Boolean = runCatching {
        UserMessagingPlatform.getConsentInformation(context).privacyOptionsRequirementStatus ==
            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }.getOrDefault(false)

    /** Reabre el formulario para que el jugador cambie su consentimiento (enlace de Ajustes). */
    fun showPrivacyOptions(activity: Activity) {
        runCatching { UserMessagingPlatform.showPrivacyOptionsForm(activity) { } }
    }
}
