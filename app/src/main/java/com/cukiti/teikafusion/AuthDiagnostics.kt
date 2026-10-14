package com.cukiti.teikafusion

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import java.security.MessageDigest

/**
 * Volcado de la configuración de login, una vez por arranque y **solo en debug**.
 *
 * Nace de la avería del 2026-07-31: los inicios de sesión dejaron de funcionar y no había
 * forma de saber por qué desde el móvil. Todos los ingredientes de un login roto (huella con la que
 * está firmado el APK instalado, cliente OAuth web, sesión de Firebase viva)
 * son comprobables en tiempo de ejecución, pero estaban repartidos entre `google-services.json`,
 * el keystore y la consola. Aquí salen juntos y en un formato que se puede pegar.
 *
 * Lo que de verdad no se podía comprobar de otra forma es la **huella del APK que está corriendo**:
 * el par (applicationId, SHA-1) tiene que coincidir con un cliente OAuth del proyecto, y comparar
 * eso a ojo entre el keystore del PC y lo que Play/Android Studio instalaron es justo donde se
 * esconden los fallos. El SHA-1 impreso aquí es el del binario instalado, sin intermediarios.
 *
 * Filtra el TAG **AuthDiag** en Logcat.
 */
object AuthDiagnostics {

    private const val TAG = "AuthDiag"
    private var dumped = false

    fun dump(context: Context) {
        if (!BuildConfig.DEBUG || dumped) return
        dumped = true
        try {
            val app = context.applicationContext
            Log.i(TAG, "──────── CONFIGURACIÓN DE LOGIN ────────")
            Log.i(TAG, "paquete=${app.packageName} versión=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE}) build=${BuildConfig.BUILD_TYPE}")
            Log.i(TAG, "dispositivo=${Build.MANUFACTURER} ${Build.MODEL} api=${Build.VERSION.SDK_INT}")

            // Huella del binario INSTALADO. Es la que Google compara contra los clientes OAuth.
            signingSha1(app)?.let { Log.i(TAG, "SHA-1 del APK instalado=$it") }
                ?: Log.w(TAG, "SHA-1 del APK instalado=NO SE PUDO LEER")

            Log.i(TAG, "project_id=${stringRes(app, "project_id") ?: "AUSENTE"}")
            Log.i(TAG, "google_app_id=${stringRes(app, "google_app_id") ?: "AUSENTE"}")
            // El cliente OAuth web (client_type 3). Sin él Google Sign-In no puede ni empezar.
            val webClient = stringRes(app, "default_web_client_id")
            Log.i(TAG, "default_web_client_id=${webClient ?: "⚠ AUSENTE — Google Sign-In no funcionará"}")

            // Estado de sesión: distingue "no hay cuenta" de "hay cuenta pero la nube falla".
            val user = runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser }.getOrNull()
            if (user == null) {
                Log.i(TAG, "sesión Firebase=NINGUNA")
            } else {
                val providers = user.providerData.joinToString(",") { it.providerId }
                Log.i(TAG, "sesión Firebase uid=${user.uid} anónimo=${user.isAnonymous} proveedores=[$providers]")
            }
            Log.i(TAG, "────────────────────────────────────────")
        } catch (e: Exception) {
            Log.e(TAG, "El volcado de diagnóstico falló", e)
        }
    }

    /** SHA-1 del certificado con el que está firmado el APK que se está ejecutando. */
    private fun signingSha1(context: Context): String? = try {
        val pm = context.packageManager
        val certificates: List<ByteArray> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            val signing = info.signingInfo
            when {
                signing == null -> emptyList()
                signing.hasMultipleSigners() -> signing.apkContentsSigners.map { it.toByteArray() }
                else -> signing.signingCertificateHistory.map { it.toByteArray() }
            }
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                .signatures.orEmpty().map { it.toByteArray() }
        }
        certificates.firstOrNull()?.let { der ->
            MessageDigest.getInstance("SHA-1").digest(der)
                .joinToString(":") { "%02X".format(it) }
        }
    } catch (e: Exception) {
        Log.w(TAG, "No se pudo leer la firma del paquete", e)
        null
    }

    /**
     * Lee un recurso de tipo string POR NOMBRE. Es a propósito: estos los genera el plugin
     * google-services y una referencia directa a `R.string` rompería el build de cualquier variante
     * cuyo `google-services.json` no los traiga, que es justo el caso que hay que diagnosticar.
     * (Recuerda: todo recurso leído así va en `res/raw/keep.xml` — lo vigila ShrinkerKeepContractTest.)
     */
    @Suppress("DiscouragedApi")
    private fun stringRes(context: Context, name: String): String? = try {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        if (id != 0) context.getString(id) else null
    } catch (_: Exception) {
        null
    }
}
