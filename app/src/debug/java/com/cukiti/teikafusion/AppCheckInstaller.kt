package com.cukiti.teikafusion

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Variante DEBUG: provider de depuración de App Check. Al primer arranque imprime en Logcat un
 * "debug secret" que hay que registrar en Firebase Console → App Check → gestionar tokens de debug.
 * El artefacto appcheck-debug solo entra al buildType debug (debugImplementation), por eso esta
 * función vive en el source set debug y no en main.
 *
 * **Por qué además pide un token aquí (2026-07-31).** Si se activa el *enforcement* de App Check en
 * la consola y este dispositivo no tiene su secreto de debug registrado, TODO el tráfico a Auth y
 * Firestore empieza a rechazarse: el inicio de sesión deja de funcionar de un día para otro, sin
 * ningún cambio de código y sin ningún mensaje que lo explique. Pedir un token al arrancar convierte
 * ese fallo invisible en una línea de Logcat: filtra por el TAG **AppCheck**.
 */
fun installAppCheck() {
    val appCheck = FirebaseAppCheck.getInstance()
    appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    appCheck.getAppCheckToken(false)
        .addOnSuccessListener {
            Log.i("AppCheck", "✓ token de App Check obtenido (debug) — el enforcement no bloqueará")
        }
        .addOnFailureListener { e ->
            Log.e(
                "AppCheck",
                "✗ SIN token de App Check. Si el enforcement está ACTIVADO en la consola, Auth y " +
                    "Firestore rechazarán todo (el inicio de sesión dejará de funcionar).",
                e
            )
            logDebugSecret()
        }
}

/**
 * Imprime el **debug secret** de este dispositivo.
 *
 * El SDK lo escupe **una sola vez**, la primera vez que se genera, y luego nunca más: si esa línea
 * se pierde entre el ruido del arranque —que es lo normal—, la única forma de recuperarla era borrar
 * los datos de la app para forzar un secreto nuevo. Está guardado en SharedPreferences, así que se
 * lee y se vuelve a imprimir cada vez que falla la atestación, que es justo cuando hace falta.
 *
 * Se registra en **Firebase Console → App Check → Apps → ⋮ → Administrar tokens de depuración**.
 */
private fun logDebugSecret() {
    try {
        val app = com.google.firebase.FirebaseApp.getInstance()
        // Nombres del propio SDK (StorageHelper): el fichero lleva la clave de persistencia de la
        // FirebaseApp, que en la app por defecto es "[DEFAULT]".
        val prefs = app.applicationContext.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.${app.persistenceKey}",
            android.content.Context.MODE_PRIVATE
        )
        val secret = prefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
        if (secret.isNullOrBlank()) {
            Log.w("AppCheck", "No hay debug secret guardado todavía — reinicia la app y vuelve a mirar")
        } else {
            Log.e("AppCheck", "▶ DEBUG SECRET DE ESTE DISPOSITIVO: $secret")
            Log.e("AppCheck", "▶ Regístralo en Firebase Console → App Check → Administrar tokens de depuración")
        }
    } catch (e: Exception) {
        Log.w("AppCheck", "No se pudo leer el debug secret", e)
    }
}
