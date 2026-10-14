package com.cukiti.teikafusion

import com.cukiti.teikafusion.model.LeaderboardEntry
import com.cukiti.teikafusion.model.TeikaType
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import kotlinx.coroutines.tasks.await

object LeaderboardRepository {
    private const val TAG = "LeaderboardRepository"
    private const val COLLECTION = "leaderboard"

    // ── Escrituras del ranking: VÍA SERVIDOR ────────────────────────────────────────────────
    //
    // Desde 2026-07-28 el cliente NO puede escribir en `leaderboard` (las reglas lo prohíben) y
    // estas funciones solo envían la marca a una Cloud Function. Antes el jugador escribía su
    // propia entrada: las reglas validaban el RANGO, pero `level = 2000, xp = 100000000` cabía
    // dentro del rango y era una escritura válida desde un APK modificado.
    //
    // El servidor (`functions/index.js`) es quien comprueba lo que una regla no puede: que la marca
    // nunca baje, que el nivel no suba más rápido de lo humanamente posible y que el XP sea
    // coherente con el nivel. Ver `submitLeaderboardEntry`.
    //
    // Fallo en silencio, como siempre: el ranking es un extra social y nunca debe interrumpir una
    // partida. Sin conexión, el envío se pierde y la siguiente victoria lo reintenta con la marca
    // acumulada (todas las llamadas mandan valores ABSOLUTOS, no incrementos: son idempotentes).
    private val functions by lazy { com.google.firebase.functions.FirebaseFunctions.getInstance() }

    /**
     * Kill-switch remoto del ranking. Válvula de escape si las Cloud Functions fallan, si Firestore
     * se satura o si hay que parar un abuso: el juego sigue funcionando entero, solo desaparece la
     * tabla. Se comprueba tanto al escribir como al leer.
     */
    private val enabled: Boolean
        get() = com.cukiti.teikafusion.data.RemoteConfigManager.snapshot.value.leaderboardEnabled

    private suspend fun callSubmit(op: String, name: String, payload: Map<String, Any>) {
        if (!enabled) return
        try {
            functions.getHttpsCallable(name).call(payload).await()
        } catch (e: Exception) { OnlineTelemetry.report(TAG, op, e) }
    }

    /**
     * Envía el progreso de niveles. [uid] ya no se usa para direccionar el documento —el servidor
     * lo toma de la sesión autenticada, que es justo lo que impide escribir en la entrada de otro—
     * pero se conserva en la firma como guarda de que hay sesión.
     */
    suspend fun submitLevel(uid: String, displayName: String, level: Int, teikas: Int, avatarType: TeikaType = TeikaType.TEIKA_1, xp: Int = 0) {
        if (uid.isBlank()) return
        callSubmit(
            "submitLevel", "submitLeaderboardEntry",
            mapOf(
                "level" to level,
                "teikas" to teikas,
                "xp" to xp,
                "displayName" to displayName,
                "avatarType" to avatarType.name
            )
        )
    }

    /** Alta/actualización de la entrada (fija el nombre visible y sube el progreso si es mayor). */
    suspend fun upsertEntry(uid: String, displayName: String, level: Int, teikas: Int, avatarType: TeikaType = TeikaType.TEIKA_1, xp: Int = 0) =
        submitLevel(uid, displayName, level, teikas, avatarType, xp)

    /* (ERRADICADOS 2026-08-09, con el modo WALLBLOCK) `submitInfiniteScore`,
     * `getInfiniteScoreRivals` y `getTopEntriesByDepth`: subida, rivales en carrera y tabla por
     * PROFUNDIDAD. El campo `infiniteScore` sigue existiendo en los documentos ya escritos y la
     * Cloud Function `submitInfiniteScore` sigue desplegada — ninguna de las dos cosas se toca
     * desde aquí; simplemente el cliente ya no las usa. */

    /** Fetches the top N players sorted by level descending. */
    suspend fun getTopEntries(limit: Long = 50): List<LeaderboardEntry>? = if (!enabled) null else try {
        val snapshot = Firebase.firestore.collection(COLLECTION)
            .orderBy("level", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        // P-436: el puesto y el orden fino (desempate por XP) los pone `LeaderboardRanking`.
        com.cukiti.teikafusion.model.LeaderboardRanking.rank(
            snapshot.documents.map { doc ->
                LeaderboardEntry(
                    rank        = 0,
                    uid         = doc.id,
                    displayName = doc.getString("displayName") ?: "Player",
                    level       = doc.getLong("level")?.toInt() ?: 0,
                    teikas      = doc.getLong("teikas")?.toInt() ?: 0,
                    totalXp     = doc.getLong("xp")?.toInt() ?: 0,
                    avatarType  = doc.getString("avatarType")
                        ?.let { runCatching { TeikaType.valueOf(it) }.getOrNull() }
                        ?: TeikaType.TEIKA_1
                )
            }
        )
    } catch (e: Exception) { OnlineTelemetry.report(TAG, "getTopEntries", e); null }

    /** Fetches the top N players sorted by XP descending. */
    suspend fun getTopEntriesByXp(limit: Long = 50): List<LeaderboardEntry>? = if (!enabled) null else try {
        val snapshot = Firebase.firestore.collection(COLLECTION)
            .orderBy("xp", Query.Direction.DESCENDING)
            .limit(limit)
            .get()
            .await()

        snapshot.documents.mapIndexed { index, doc ->
            LeaderboardEntry(
                rank        = index + 1,
                uid         = doc.id,
                displayName = doc.getString("displayName") ?: "Player",
                level       = doc.getLong("level")?.toInt() ?: 0,
                teikas      = doc.getLong("teikas")?.toInt() ?: 0,
                totalXp     = doc.getLong("xp")?.toInt() ?: 0,
                avatarType  = doc.getString("avatarType")
                    ?.let { runCatching { TeikaType.valueOf(it) }.getOrNull() }
                    ?: TeikaType.TEIKA_1
            )
        }
    } catch (e: Exception) { OnlineTelemetry.report(TAG, "getTopEntriesByXp", e); null }

    /** Fetches a specific user's entry (without rank, rank must be computed by UI if outside top 50). */
    suspend fun getEntry(uid: String): LeaderboardEntry? = if (!enabled) null else try {
        val doc = Firebase.firestore.collection(COLLECTION).document(uid).get().await()
        if (doc.exists()) {
            LeaderboardEntry(
                rank        = -1,
                uid         = doc.id,
                displayName = doc.getString("displayName") ?: "Player",
                level       = doc.getLong("level")?.toInt() ?: 0,
                teikas      = doc.getLong("teikas")?.toInt() ?: 0,
                totalXp     = doc.getLong("xp")?.toInt() ?: 0,
                avatarType  = doc.getString("avatarType")
                    ?.let { runCatching { TeikaType.valueOf(it) }.getOrNull() }
                    ?: TeikaType.TEIKA_1
            )
        } else null
    } catch (e: Exception) { OnlineTelemetry.report(TAG, "getEntry", e); null }

    /**
     * El puesto REAL de quien no sale en el top: cuántos jugadores tienen MÁS nivel, + 1. Es una
     * consulta de RECUENTO (una lectura), no una descarga. Empatados comparten puesto.
     *
     * Las reglas (`firestore.rules`) solo dejan listar con `limit ≤ 100` —para que nadie barra la
     * colección—, y un recuento cuenta como lista: por eso se cuenta HASTA [RANK_COUNT_CAP]. Con
     * menos delante, el puesto es exacto; con el tope lleno devuelve [LeaderboardEntry.RANK_BEYOND]
     * («100+»). `null` si no responde: el puesto queda desconocido y la UI no inventa uno.
     */
    suspend fun rankForLevel(level: Int): Int? = if (!enabled) null else try {
        val ahead = Firebase.firestore.collection(COLLECTION)
            .whereGreaterThan("level", level)
            .limit(RANK_COUNT_CAP)
            .count()
            .get(com.google.firebase.firestore.AggregateSource.SERVER)
            .await()
            .count
        if (ahead >= RANK_COUNT_CAP) LeaderboardEntry.RANK_BEYOND else (ahead + 1).toInt()
    } catch (e: Exception) { OnlineTelemetry.report(TAG, "rankForLevel", e); null }

    /**
     * P-438 · El puesto EXACTO, contado por el servidor (Cloud Function `getMyRank`, que no está
     * sujeta al `limit ≤ 100` de las reglas). Solo se pide cuando el recuento local se queda en
     * «100+». `null` si la función no responde (o aún no está desplegada): se queda «100+».
     */
    suspend fun rankFromServer(): Int? = if (!enabled) null else try {
        val res = functions.getHttpsCallable("getMyRank").call().await()
        ((res.getData() as? Map<*, *>)?.get("rank") as? Number)?.toInt()?.takeIf { it > 0 }
    } catch (e: Exception) { OnlineTelemetry.report(TAG, "rankFromServer", e); null }

    /** Hasta dónde se cuenta el puesto: el `limit` máximo que permiten las reglas de Firestore. */
    private const val RANK_COUNT_CAP = 100L

    /** Fetches just the user's best level (useful for local sync on startup). */
    suspend fun getCloudBestLevel(uid: String): Int = try {
        Firebase.firestore.collection(COLLECTION)
            .document(uid).get().await()
            .getLong("level")?.toInt() ?: 0
    } catch (e: Exception) { OnlineTelemetry.report(TAG, "getCloudBestLevel", e); 0 }

    /**
     * Elimina la entrada del UID autenticado actual: se usa al migrar a una cuenta existente y al
     * ELIMINAR LA CUENTA. Devuelve true si se confirmó — el borrado de cuenta necesita saberlo para
     * no eliminar el usuario de Auth dejando una entrada de ranking huérfana.
     */
    suspend fun deleteOwnEntry(uid: String): Boolean = try {
        Firebase.firestore.collection(COLLECTION).document(uid).delete().await()
        true
    } catch (e: Exception) { OnlineTelemetry.report(TAG, "deleteOwnEntry", e); false }

    /**
     * Saca al jugador del ranking al restablecer su progreso.
     *
     * Antes reescribía la entrada con `level = 1`, pero el cliente ya no puede escribir en
     * `leaderboard` (solo la Cloud Function). BORRARLA es además más correcto: un jugador que
     * empieza de cero no debería aparecer en la tabla hasta que vuelva a jugar, y así
     * `getCloudBestLevel` devuelve 0 y la sincronización nube→local no revierte el reset.
     */
    suspend fun forceResetProgress(uid: String) {
        deleteOwnEntry(uid)
    }
}
