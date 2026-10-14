package com.cukiti.teikafusion

import android.app.Application
import com.cukiti.teikafusion.data.RemoteConfigManager
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.launch

class TeikaFusionApp : Application() {
    override fun onCreate() {
        super.onCreate()
        processStartMs = android.os.SystemClock.elapsedRealtime()   // P-179
        Analytics.init(this)
        com.cukiti.teikafusion.ui.Haptics.init(this)
        FirebaseManager.init(this)
        // App Check antes de cualquier tráfico a Firestore: la fábrica depende del buildType
        // (Play Integrity en release, provider de debug en debug — ver AppCheckInstaller).
        try {
            installAppCheck()
        } catch (e: Exception) {
            android.util.Log.e("TeikaFusionApp", "App Check init failed", e)
        }
        purgeFirestoreQueueOnce()
        // Log interno del SDK de Firestore. APAGADO por defecto: es utilísimo pero escribe cientos de
        // líneas por segundo y ahoga el resto del logcat. Se enciende a mano ([FIRESTORE_VERBOSE])
        // cuando hay que distinguir entre reglas, App Check, gRPC bloqueado o caché local atascada —
        // un fallo de red de Firestore es MUDO de otro modo: el repositorio solo ve que su `await()`
        // no vuelve, y el síntoma que llega al jugador («sin conexión») no dice nada. TAG: Firestore.
        if (BuildConfig.DEBUG && FIRESTORE_VERBOSE) {
            try {
                com.google.firebase.firestore.FirebaseFirestore.setLoggingEnabled(true)
            } catch (_: Exception) {}
        }
        // AdMob se inicializa después del onboarding y de UMP en MainActivity. Hacerlo aquí corría
        // antes de que el jugador pudiera aceptar/rechazar el consentimiento publicitario.
        BillingManager.init(this)
        GoogleManager.init(this)
        // Volcado de la configuración de login (solo debug, una vez): huella del APK instalado,
        // cliente OAuth web y sesión viva. Ver AuthDiagnostics — TAG "AuthDiag".
        AuthDiagnostics.dump(this)
        try {
            FirebaseCrashlytics.getInstance().isCrashlyticsCollectionEnabled = true
            // La clave "app_phase = overhaul_v2" era constante y no informaba de nada. El contexto
            // útil (nivel, bioma, modo, tipo de cuenta) lo escribe Analytics.crashKeys según juega
            // el jugador, y la identidad la ata Analytics.setPlayer.
            FirebaseCrashlytics.getInstance().setCustomKey("build_type", BuildConfig.BUILD_TYPE)
        } catch (_: Exception) {}
        // ── FUERA DEL ARRANQUE (P-27, 2026-07-31) ─────────────────────────────────────────────
        // Lo que NO hace falta para pintar el primer fotograma se va a un hilo de fondo. Los dos que
        // había aquí tocan RED (fetch de Remote Config y suscripción a FCM) y estaban en el camino
        // crítico de `Application.onCreate`, que corre antes de que exista una sola vista: cada
        // milisegundo ahí es tiempo mirando un icono.
        //
        // El resto de inits se quedan a propósito: la auth, App Check, Billing y Google tienen
        // consumidores que arrancan en `MainActivity.onCreate`, y moverlos sin medir en
        // dispositivo cambiaría un arranque lento por un login roto. Ese es el pendiente aquí.
        com.cukiti.teikafusion.ui.introLog("App.onCreate casi fin")
        appScope.launch {
            RemoteConfigManager.init()
            subscribeToCampaignTopic()
            // Los Lottie de Teika se parsean de forma SÍNCRONA en el hilo de composición
            // (`TeikaIcon` usa `fromRawResSync` para no parpadear al re-montarse). Dejarlos ya
            // parseados aquí convierte esa primera aparición —el icono del MEGA del panel de JUGAR
            // NIVEL, la presentación del objetivo, el primer Teika del tablero— en un acierto de
            // caché en vez de un parseo de JSON en mitad del fotograma.
            com.cukiti.teikafusion.ui.warmTeikaCompositions(this@TeikaFusionApp)
            com.cukiti.teikafusion.ui.warmBiomeLotties(this@TeikaFusionApp)
        }
    }

    /**
     * Vacía **una sola vez** la caché local de Firestore (documentos + cola de escrituras pendientes).
     *
     * El bucle de `unlock_companions` (ver `PlayerOverrideManager.overrideFlow`) llegó a encolar más
     * de **11.000 escrituras idénticas** al mismo documento `player_stats`. Firestore serializa las
     * escrituras de un documento y las despacha desde la MISMA cola que las lecturas: con ese atasco,
     * el ranking global y `cloud_saves` no llegaban ni a salir a la red y expiraban, así que la app
     * decía «sin conexión» estando online. Arreglar el bucle detiene la sangría, pero el atasco ya
     * encolado tardaría ~9 horas en drenar solo.
     *
     * Se puede tirar sin pérdida: son escrituras redundantes del MISMO estado (el espejo de solo
     * lectura que mira el admin), regenerable en el siguiente push. El progreso del jugador NO vive
     * aquí — vive en DataStore ([com.cukiti.teikafusion.model.PreferencesManager]) y en `cloud_saves`.
     *
     * Tiene que correr **antes de que arranque el cliente de Firestore**: por eso está en
     * `Application.onCreate` con SharedPreferences (lectura síncrona) y no con DataStore. Si algo se
     * adelanta, `clearPersistence` falla con FAILED_PRECONDITION; el flag NO se marca y se reintenta
     * en el siguiente arranque.
     */
    private fun purgeFirestoreQueueOnce() {
        try {
            val prefs = getSharedPreferences("firestore_maintenance", MODE_PRIVATE)
            if (prefs.getBoolean(KEY_QUEUE_PURGED, false)) return
            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .clearPersistence()
                .addOnSuccessListener {
                    prefs.edit().putBoolean(KEY_QUEUE_PURGED, true).apply()
                    android.util.Log.i("TeikaFusionApp", "✓ caché local de Firestore purgada (cola atascada)")
                }
                .addOnFailureListener { e ->
                    android.util.Log.w("TeikaFusionApp", "Purga de Firestore pospuesta al próximo arranque", e)
                }
        } catch (e: Exception) {
            android.util.Log.w("TeikaFusionApp", "No se pudo purgar la caché de Firestore", e)
        }
    }

    /** Scope de la Application: sobrevive a todo y no bloquea el arranque. */
    private val appScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    /**
     * Suscribe el dispositivo al tema de campañas de reenganche.
     *
     * Antes esto vivía SOLO en `NotificationService.onNewToken`, que dispara una única vez al crear
     * el token. Si en ese preciso momento no había red, la suscripción fallaba y **no se reintentaba
     * jamás**: ese dispositivo quedaba fuera de todas las campañas para siempre. Aquí se reintenta
     * en cada arranque, y es idempotente.
     */
    private fun subscribeToCampaignTopic() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance()
                .subscribeToTopic(FCM_CAMPAIGN_TOPIC)
                .addOnFailureListener { e ->
                    // Sin red es lo normal: el próximo arranque lo vuelve a intentar.
                    android.util.Log.d("TeikaFusionApp", "Suscripción a '$FCM_CAMPAIGN_TOPIC' pendiente: ${e.message}")
                }
        } catch (e: Exception) {
            android.util.Log.w("TeikaFusionApp", "FCM no disponible", e)
        }
    }

    companion object {
        /**
         * Instante (reloj monótono) en que el proceso empezó a construir la app. Lo lee el ViewModel
         * al soltar el splash para medir el arranque de punta a punta (P-179). `0` = no medido.
         */
        @Volatile
        var processStartMs: Long = 0L
            private set

        /** Tema de FCM para campañas masivas desde la consola de Firebase. */
        const val FCM_CAMPAIGN_TOPIC = "all"

        /** Marca de la purga única de la caché de Firestore. Ver [purgeFirestoreQueueOnce]. */
        private const val KEY_QUEUE_PURGED = "queue_purged_v1"

        /** Interruptor manual del log interno del SDK de Firestore (solo debug). */
        private const val FIRESTORE_VERBOSE = false
    }

    /**
     * El sistema anda escaso de memoria (o nos fuimos a segundo plano): soltamos los bitmaps de
     * `res/raw` cacheados. Se volverán a decodificar solos la próxima vez que se pidan.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            com.cukiti.teikafusion.ui.trimRawImageCache()
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        BillingManager.release()
    }
}
