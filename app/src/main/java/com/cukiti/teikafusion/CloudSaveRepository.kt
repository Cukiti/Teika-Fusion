package com.cukiti.teikafusion

import android.util.Log
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.tasks.await

/**
 * Respaldo del progreso en Firestore (`cloud_saves/{uid}`) para usuarios que NO usan Play Games
 * (el respaldo va a Firestore, no a Play Games Snapshots).
 *
 * Guarda el mismo [com.cukiti.teikafusion.model.CloudSaveData] JSON que produce el ViewModel, así
 * que la lógica de fusión (`mergedWith`) se reutiliza tal cual. La política de conflicto la resuelve
 * el merge del ViewModel (máximos por campo), igual que con Snapshots.
 */
object CloudSaveRepository {
    private const val TAG = "CloudSaveRepository"
    private const val COLLECTION = "cloud_saves"
    private const val FIELD_JSON = "json"
    private const val FIELD_UPDATED = "updatedAtEpochMs"
    // Tope de las reglas de Firestore para cloud_saves.json (900 KB). Se comprueba en cliente para
    // no gastar una escritura que las reglas rechazarían con PERMISSION_DENIED (ruido en Crashlytics).
    private const val MAX_JSON_CHARS = 900_000

    /**
     * Lee el JSON de progreso del jugador. Distingue dos casos para no pisar la nube real estando
     * offline:
     *   • Result.success(json)  → lectura confirmada; json puede ser null si la cuenta es nueva/vacía.
     *   • Result.failure(e)      → no se pudo leer (sin internet / reglas / error). NO se debe subir.
     */
    suspend fun load(uid: String): Result<String?> = try {
        val doc = Firebase.firestore.collection(COLLECTION).document(uid).get().await()
        val json = doc.getString(FIELD_JSON)?.takeIf { it.isNotBlank() }
        if (json == null) Log.d(TAG, "load: sin progreso en la nube para uid=$uid (cuenta nueva)")
        else Log.i(TAG, "✓ load: ${json.length} chars leídos de Firestore")
        Result.success(json)
    } catch (e: Exception) {
        OnlineTelemetry.report(TAG, "load", e)
        Result.failure(e)
    }

    /** Borra el respaldo en la nube del jugador (usado por "Restablecer progreso"). */
    suspend fun delete(uid: String): Boolean = try {
        Firebase.firestore.collection(COLLECTION).document(uid).delete().await()
        Log.i(TAG, "✓ delete: cloud_saves/$uid eliminado")
        true
    } catch (e: Exception) {
        OnlineTelemetry.report(TAG, "delete", e)
        false
    }

    /**
     * Escribe el JSON de progreso **fusionándolo con lo que haya en la nube**, dentro de una
     * transacción. Devuelve true si se confirmó.
     *
     * Antes era un `set()` a secas, y eso era «el último que cierra, gana»: con la misma cuenta en
     * dos dispositivos el mismo día, el segundo en cerrar subía el estado que había leído al
     * arrancar y BORRABA todo lo que el otro hubiera hecho. La fusión por máximos existía, pero solo
     * se aplicaba al ARRANCAR — la escritura se la saltaba.
     *
     * [merge] recibe el JSON remoto (o null si no hay) y devuelve el JSON definitivo a escribir. Lo
     * resuelve el ViewModel, que es quien conoce `CloudSaveData.mergedWith`. Firestore reintenta el
     * bloque si el documento cambió por debajo, así que [merge] puede ejecutarse varias veces y debe
     * ser puro.
     */
    suspend fun save(uid: String, json: String, merge: (String?) -> String = { json }): Boolean {
        if (json.length < 2 || json.length > MAX_JSON_CHARS) {
            Log.e(TAG, "✗ save: JSON fuera de rango (${json.length} chars), no se sube")
            return false
        }
        return runSave(uid, merge)
    }

    private suspend fun runSave(uid: String, merge: (String?) -> String): Boolean = try {
        val ref = Firebase.firestore.collection(COLLECTION).document(uid)
        val written = Firebase.firestore.runTransaction { tx ->
            val remote = tx.get(ref).getString(FIELD_JSON)?.takeIf { it.isNotBlank() }
            val finalJson = merge(remote)
            // La red de seguridad de tamaño se repite aquí: la fusión puede crecer respecto al
            // JSON que se validó arriba, y las reglas rechazarían la escritura entera.
            require(finalJson.length in 2..MAX_JSON_CHARS) {
                "JSON fusionado fuera de rango (${finalJson.length} chars)"
            }
            tx.set(ref, mapOf(FIELD_JSON to finalJson, FIELD_UPDATED to System.currentTimeMillis()))
            finalJson.length
        }.await()
        Log.i(TAG, "✓ save: $written chars subidos a Firestore (fusionado en transacción)")
        true
    } catch (e: Exception) {
        OnlineTelemetry.report(TAG, "save", e)
        false
    }
}
