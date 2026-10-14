package com.cukiti.teikafusion

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestoreException

/**
 * Telemetría de la capa online (Firestore/Auth). Antes cada repositorio tragaba sus excepciones en
 * silencio (`catch (_: Exception) {}`): correcto para no romper la partida, pero en producción deja
 * ciegos ante un despliegue de reglas roto o un esquema que dejó de validar.
 *
 * Política: TODO fallo se loggea; solo los INESPERADOS suben a Crashlytics como non-fatal. Estar
 * offline es rutina (UNAVAILABLE / DEADLINE_EXCEEDED / IOException) y reportarlo solo llenaría
 * Crashlytics de ruido — un PERMISSION_DENIED, en cambio, es una regla mal desplegada y hay que verlo.
 */
object OnlineTelemetry {

    // Contadores de la sesión. Un fallo suelto no dice nada; la PROPORCIÓN sí: "este crash ocurrió
    // con 47 fallos de red acumulados" es una pista, y "el 4 % de las escrituras falla" es un
    // incidente. Se adjuntan como claves de Crashlytics para que viajen con cada informe.
    private val totalFailures = java.util.concurrent.atomic.AtomicInteger(0)
    private val unexpectedFailures = java.util.concurrent.atomic.AtomicInteger(0)

    fun report(tag: String, op: String, e: Exception) {
        Log.e(tag, "✗ $op falló", e)
        val total = totalFailures.incrementAndGet()
        if (isExpectedOffline(e)) {
            // Estar sin red es rutina: se cuenta pero no se reporta (llenaría Crashlytics de ruido).
            trySetKeys(total, unexpectedFailures.get())
            return
        }
        val unexpected = unexpectedFailures.incrementAndGet()
        try {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("online_op", "$tag.$op")
                recordException(e)
            }
        } catch (_: Exception) {
            // Crashlytics no inicializado (tests/arranque temprano): el Log.e ya quedó.
        }
        trySetKeys(total, unexpected)
    }

    private fun trySetKeys(total: Int, unexpected: Int) {
        try {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("online_failures", total)
                setCustomKey("online_failures_unexpected", unexpected)
            }
        } catch (_: Exception) {}
    }

    private fun isExpectedOffline(e: Exception): Boolean {
        if (e is java.io.IOException) return true
        val code = (e as? FirebaseFirestoreException)?.code
        return code == FirebaseFirestoreException.Code.UNAVAILABLE ||
            code == FirebaseFirestoreException.Code.DEADLINE_EXCEEDED
    }
}
