package com.cukiti.teikafusion

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * Variante RELEASE: App Check con Play Integrity — atestigua que cada petición a Firestore/Auth
 * sale de un binario legítimo instalado desde Play. Mientras el "enforcement" no se active en
 * Firebase Console no bloquea nada: solo etiqueta el tráfico como verificado.
 *
 * **Y AVISA SI NO PUEDE** (2026-09-04). La variante de debug pide un token al arrancar precisamente
 * para convertir un fallo invisible en una línea de Logcat —su propio KDoc lo dice: con el
 * enforcement activado y sin token, «el inicio de sesión deja de funcionar de un día para otro, sin
 * ningún cambio de código y sin ningún mensaje que lo explique»—. La de release no pedía nada, así
 * que en producción, que es donde el enforcement se va a activar de verdad y donde no hay Logcat que
 * mirar, ese fallo era **mudo del todo**.
 *
 * Ahora pide el token y, si falla, lo registra como **no fatal en Crashlytics**. No bloquea ni
 * reintenta: es una sonda. Play Integrity falla legítimamente en instalaciones que no vienen de Play
 * —un APK de prueba cargado a mano, por ejemplo—, así que el aviso es una señal para diagnosticar,
 * no una alarma: lo que importa es si aparece en MUCHOS dispositivos a la vez.
 */
fun installAppCheck() {
    val appCheck = FirebaseAppCheck.getInstance()
    appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
    appCheck.getAppCheckToken(false)
        .addOnFailureListener { e ->
            Log.w("AppCheck", "✗ sin token de App Check (Play Integrity)", e)
            try {
                com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance()
                    .recordException(IllegalStateException("App Check sin token: Play Integrity falló", e))
            } catch (_: Exception) {
            }
        }
}
