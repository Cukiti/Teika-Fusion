package com.cukiti.teikafusion.data

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Sistema Firebase bidireccional de monitoreo y control por dispositivo.
 *
 * ── ÚNICO DOCUMENTO: player_stats/{deviceId} ──────────────────────────────────
 *
 * Campos de solo lectura para el admin (escritos por la app):
 *   • owner_uid    — Firebase Auth UID dueño del documento
 *   • player_id    — ANDROID_ID del dispositivo
 *   • coins        — monedas actuales
 *   • max_level    — nivel máximo desbloqueado
 *   • display_name — nombre del jugador
 *   • updated_at   — última sincronización
 *
 * Campos de comando para el admin (escritos por el admin, reseteados por la app):
 *   • set_coins      (Number) → establece las monedas al valor exacto  (0 = sin comando)
 *   • set_level      (Number) → establece el nivel máximo al valor exacto (0 = sin comando)
 *   • set_crystal_1..4 (Number) → establece los cristales mágicos de ese tier al valor exacto
 *                                  (-1 = sin comando; permite fijar a 0 para vaciar un tier)
 *
 * ── FLUJO ADMIN ───────────────────────────────────────────────────────────────
 * 1. Abrir Firebase Console → Firestore → player_stats → documento del jugador
 * 2. Editar campo "set_coins" con el valor deseado (ej: 3000) → Guardar
 * 3. La app lo detecta en < 1 segundo, aplica el cambio y resetea set_coins a 0
 * 4. El campo "coins" se actualiza automáticamente con el nuevo valor
 *
 * ── IDENTIFICAR JUGADOR ───────────────────────────────────────────────────────
 * El jugador toca el título "Teika Fusión" 5 veces en la pantalla de inicio
 * → aparece overlay con su device ID y botón de copiar.
 *
 * ── POR QUÉ NO HAY RACE CONDITION ─────────────────────────────────────────────
 * pushPlayerStats() solo escribe: owner_uid, coins, max_level, display_name, updated_at
 * El listener solo lee y resetea: set_coins, set_level
 * Son campos completamente distintos → sin colisión posible.
 */
data class PlayerOverride(
    val setCoins: Int = 0,   // valor absoluto a establecer (0 = sin comando)
    val setLevel: Int = 0,   // valor absoluto a establecer (0 = sin comando)
    // IDs de compañeros a DESBLOQUEAR para este jugador (campo "unlock_companions",
    // array de strings en Firestore). Persistente: no se resetea (documenta lo que posee).
    val unlockCompanions: List<String> = emptyList(),
    // Compañero a SELECCIONAR forzadamente (campo "force_companion", string). One-shot:
    // se aplica y se resetea para no pisar la elección del jugador en cada arranque.
    val forceCompanion: String? = null,
    // Cristales mágicos a ESTABLECER por tier (campos "set_crystal_1".."set_crystal_4" en Firestore,
    // Number, -1 = sin comando). Valor absoluto por tier con comando; los demás se dejan intactos.
    // One-shot: tras aplicarse, cada tier usado se resetea a -1.
    val setCrystals: Map<Int, Int> = emptyMap()
)

object PlayerOverrideManager {
    private const val TAG        = "PlayerOverrideMgr"
    private const val COLLECTION = "player_stats"

    /**
     * ID con el que se llavea `player_stats`: **siempre el UID de Firebase**, o null si aún no hay
     * sesión.
     *
     * Antes caía al ANDROID_ID cuando no había UID, y eso generaba DOS documentos por jugador: el
     * primer arranque (sin sesión, porque los anónimos ya no se crean solos) escribía con el
     * ANDROID_ID y, al vincular cuenta, se creaba otro con el UID. El primero quedaba huérfano para
     * siempre —`performCloudSync` solo borra el UID previo, nunca un ANDROID_ID— y el admin acababa
     * viendo dos perfiles del mismo jugador sin saber en cuál escribir los comandos.
     *
     * Sin UID no se escribe nada: un jugador invitado no debe existir en Firestore.
     */
    fun getPlayerId(): String? =
        com.cukiti.teikafusion.FirebaseManager.currentUid()?.takeIf { it.isNotBlank() }

    /**
     * ANDROID_ID del dispositivo. **Solo** para localizar y limpiar los documentos legados descritos
     * en [getPlayerId]; nunca para llavear escrituras nuevas.
     */
    // HardwareIds suprimido a propósito: identifica un documento antiguo que hay que borrar, no se
    // usa para tracking ni publicidad.
    @Suppress("HardwareIds")
    fun legacyAndroidId(context: Context): String? =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf { it.isNotBlank() }

    /**
     * Borra el documento player_stats de un UID abandonado (p. ej. el anónimo transitorio que se deja
     * al cambiar a la cuenta canónica). Su progreso real ya vive en cloud_saves, así que
     * este doc es basura y debe eliminarse para no acumular perfiles duplicados.
     */
    suspend fun deleteProfile(uid: String) {
        if (uid.isBlank()) return
        val db = try { FirebaseFirestore.getInstance() } catch (_: Exception) { return }
        try {
            db.collection(COLLECTION).document(uid).delete().await()
            Log.i(TAG, "Perfil huérfano eliminado: $uid")
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo borrar perfil huérfano $uid: ${e.message}")
        }
    }

    /**
     * Borra TODOS los documentos de este jugador en `player_stats`, informando del resultado (a
     * diferencia de [deleteProfile], que es best-effort silencioso para basura).
     *
     * Se le pasan varios ids porque históricamente un mismo jugador podía tener dos documentos: el
     * primer arranque escribía con el ANDROID_ID (aún no había UID) y, al vincular cuenta, se creaba
     * otro con el UID. El borrado de cuenta tiene que llevarse ambos.
     *
     * Devuelve false si alguno falló: el que llama NO debe eliminar el usuario de Auth todavía, o
     * los documentos quedarían huérfanos sin nadie con permiso para tocarlos.
     */
    suspend fun deleteAllProfilesOf(ids: Collection<String>): Boolean {
        val db = try { FirebaseFirestore.getInstance() } catch (_: Exception) { return false }
        var allOk = true
        ids.filter { it.isNotBlank() }.distinct().forEach { id ->
            try {
                db.collection(COLLECTION).document(id).delete().await()
                Log.i(TAG, "player_stats/$id eliminado")
            } catch (e: Exception) {
                com.cukiti.teikafusion.OnlineTelemetry.report(TAG, "deleteAllProfilesOf[$id]", e)
                allOk = false
            }
        }
        return allOk
    }

    /**
     * Huella del último estado REALMENTE escrito en esta ejecución. Ver [pushPlayerStats].
     */
    private var lastPushedFingerprint: String? = null

    /** El arranque vuelve a permitir un latido aunque el estado no haya cambiado. */
    fun resetPushCache() { lastPushedFingerprint = null }

    /**
     * Escribe el estado actual del jugador a Firestore.
     * Solo toca los campos de estado (coins, max_level, display_name, updated_at).
     * NUNCA toca set_coins ni set_level para evitar colisiones con comandos del admin.
     *
     * ## Por qué `updated_at` ya no se mueve solo (fix 2026-08-01)
     *
     * `updated_at` es `FieldValue.serverTimestamp()`, así que **cada llamada lo reescribía**, hubiera
     * cambiado algo o no. Y llamantes hay cinco: el arranque, la sincronización de nube, guardar el
     * nombre, aplicar un comando del admin y —el ruidoso— un colector de `_gameState` que dispara con
     * cada cambio de monedas o de nivel máximo. Resultado: el documento parecía escribirse
     * continuamente y era imposible saber, mirando la consola, si la app había sincronizado algo de
     * verdad o solo había vuelto a estampar la hora.
     *
     * Ahora se compara con la huella de lo último escrito y **si nada cambió, no se escribe**. Con
     * [force] (el push de arranque) se escribe igualmente: ese latido es justo el que el admin busca
     * para comprobar que la app sigue sincronizando. Se ahorran además escrituras de Firestore.
     */
    suspend fun pushPlayerStats(
        context: Context,
        coins: Int,
        maxLevel: Int,
        displayName: String,
        crystals: Map<Int, Int> = emptyMap(),
        /** Latido de arranque: escribe aunque el estado sea idéntico al último enviado. */
        force: Boolean = false
    ): Boolean {
        val db   = try { FirebaseFirestore.getInstance() } catch (_: Exception) { return false }
        val auth = try { FirebaseAuth.getInstance() }       catch (_: Exception) { return false }

        val uid = auth.currentUser?.uid
        if (uid == null) {
            Log.w(TAG, "pushPlayerStats: sin sesión auth, omitiendo")
            return false
        }
        // El documento se llavea SIEMPRE por UID (ver getPlayerId): un solo perfil por jugador.
        val playerId = uid

        // Huella de lo que este push escribiría. Si coincide con lo último enviado, la escritura
        // sería un no-op salvo por `updated_at` — y ese es exactamente el ruido que se quería quitar.
        val fingerprint = buildString {
            append(uid); append('|')
            append(coins); append('|')
            append(maxLevel); append('|')
            append(displayName.ifBlank { "—" }); append('|')
            crystals.toSortedMap().forEach { (t, n) -> append(t); append(':'); append(n); append(',') }
        }
        if (!force && fingerprint == lastPushedFingerprint) {
            Log.d(TAG, "Stats sin cambios [$playerId] → no se reescribe (updated_at intacto)")
            return true
        }

        return try {
            val fields = mutableMapOf<String, Any>(
                "owner_uid"    to uid,
                "player_id"    to playerId,
                "coins"        to coins,
                "max_level"    to maxLevel,
                "display_name" to displayName.ifBlank { "—" },
                "updated_at"   to FieldValue.serverTimestamp()
            )
            // Espejo de solo lectura de la mochila de cristales (para que el admin vea el saldo
            // por tier antes de escribir set_crystals). Solo se escribe si se proporcionó.
            if (crystals.isNotEmpty()) {
                fields["crystals"] = crystals.entries.associate { (t, n) -> t.toString() to n }
            }
            db.collection(COLLECTION).document(playerId).set(
                fields,
                SetOptions.merge()   // solo actualiza estos campos; set_coins/set_level intactos
            ).await()
            lastPushedFingerprint = fingerprint
            Log.d(TAG, "Stats sincronizados [$playerId] coins=$coins lvl=$maxLevel")
            true
        } catch (e: Exception) {
            com.cukiti.teikafusion.OnlineTelemetry.report(TAG, "pushPlayerStats", e)
            false
        }
    }

    /**
     * Flow en tiempo real que emite un [PlayerOverride] en cuanto el admin escribe
     * un valor no-cero en set_coins o set_level del documento player_stats/{deviceId}.
     *
     * Después de emitir, resetea ambos campos a 0 para que no se apliquen dos veces.
     * Debe iniciarse DESPUÉS de que Firebase Auth confirme sesión.
     */
    /**
     * Decide si un snapshot de `player_stats` trae algo que APLICAR, o si no hay nada que hacer.
     *
     * Es la regla que rompe el bucle descrito en [overrideFlow]: `unlock_companions` es un registro
     * PERMANENTE, no un comando de un solo uso, así que por sí solo nunca debe contar como "hay
     * comando" — solo cuentan los compañeros que aún no se han entregado ([deliveredUnlocks]).
     * Devuelve `null` cuando no hay nada, y así el snapshot no genera emisión ni, por tanto, la
     * escritura de espejo que volvería a disparar el listener.
     *
     * Pura a propósito: es la única parte de la escucha que se puede cubrir con un test JVM.
     */
    internal fun pendingOverride(
        setCoins: Int,
        setLevel: Int,
        allUnlocks: List<String>,
        deliveredUnlocks: Set<String>,
        forceCompanion: String?,
        setCrystals: Map<Int, Int>
    ): PlayerOverride? {
        val newUnlocks = allUnlocks.filter { it !in deliveredUnlocks }
        val hasCommand = setCoins > 0 ||
            setLevel > 0 ||
            newUnlocks.isNotEmpty() ||
            forceCompanion != null ||
            setCrystals.isNotEmpty()
        if (!hasCommand) return null
        return PlayerOverride(
            setCoins = setCoins,
            setLevel = setLevel,
            unlockCompanions = newUnlocks,
            forceCompanion = forceCompanion,
            setCrystals = setCrystals
        )
    }

    fun overrideFlow(playerId: String): Flow<PlayerOverride> = callbackFlow {
        val db = try { FirebaseFirestore.getInstance() } catch (_: Exception) {
            awaitClose {}
            return@callbackFlow
        }
        val docRef = db.collection(COLLECTION).document(playerId)

        // Compañeros ya entregados en esta escucha. `unlock_companions` NO es un comando de un solo
        // uso: se conserva como registro de lo que el jugador posee, así que el campo sigue lleno
        // para siempre. Sin esta memoria, la condición de emisión de abajo se cumplía en CADA
        // snapshot, y el colector responde a cada emisión con un push de `player_stats` —
        // que reescribe `updated_at` y vuelve a disparar este mismo listener.
        //
        // Eso era un BUCLE DE ESCRITURA INFINITO (2026-08-01): una escritura cada ~2,6 s mientras la
        // app estuviera abierta. Además de quemar cuota, saturaba la cola del cliente de Firestore y
        // las LECTURAS no llegaban a emitirse nunca: el ranking global y la carga de `cloud_saves`
        // expiraban a los 8 s y la app decía «sin conexión» estando perfectamente online.
        val deliveredUnlocks = mutableSetOf<String>()

        val reg: ListenerRegistration = docRef.addSnapshotListener { snap, err ->
            if (err != null) {
                // A Crashlytics, no solo a Logcat: si esto falla, los comandos del admin dejan de
                // llegar EN SILENCIO y desde la consola parece que la app los ignora. Un
                // PERMISSION_DENIED aquí significa reglas mal desplegadas o App Check bloqueando.
                com.cukiti.teikafusion.OnlineTelemetry.report(TAG, "overrideFlow[$playerId]", err)
                return@addSnapshotListener
            }
            if (snap == null || !snap.exists()) return@addSnapshotListener

            val rawSetCoins = snap.getLong("set_coins")
            val rawSetLevel  = snap.getLong("set_level")
            // Compañeros: array de IDs a desbloquear + string de compañero a forzar.
            val rawUnlock = snap.get("unlock_companions") as? List<*>
            val allUnlocks = rawUnlock?.mapNotNull { (it as? String)?.takeIf { s -> s.isNotBlank() } } ?: emptyList()
            val forceCompanion = snap.getString("force_companion")?.takeIf { it.isNotBlank() }
            // Cristales: 4 campos escalares set_crystal_1..4 (Number). -1 = sin comando; ≥0 = set
            // ABSOLUTO de ese tier. Igual de visible/editable que set_coins en la consola.
            val setCrystals: Map<Int, Int> = buildMap {
                for (t in 1..4) {
                    val v = snap.getLong("set_crystal_$t")
                    if (v != null && v >= 0) put(t, v.toInt())
                }
            }

            // Los campos de comando no existen aún en este documento (primer push del jugador).
            // Los inicializamos para que el admin los vea y pueda editarlos en Firebase Console.
            run {
                val init = mutableMapOf<String, Any>()
                if (rawSetCoins == null)                       init["set_coins"]         = 0L
                if (rawSetLevel  == null)                      init["set_level"]         = 0L
                if (!snap.contains("unlock_companions"))       init["unlock_companions"] = emptyList<String>()
                if (!snap.contains("force_companion"))         init["force_companion"]   = ""
                // 4 campos escalares de cristales (-1 = sin comando) — visibles para el admin.
                for (t in 1..4) {
                    if (!snap.contains("set_crystal_$t"))      init["set_crystal_$t"]    = -1L
                }
                if (init.isNotEmpty()) {
                    docRef.update(init).addOnFailureListener { e ->
                        com.cukiti.teikafusion.OnlineTelemetry.report(TAG, "initCommandSchema", e)
                    }
                }
            }

            val setCoins = rawSetCoins?.toInt() ?: 0
            val setLevel  = rawSetLevel?.toInt()  ?: 0

            val override = pendingOverride(
                setCoins = setCoins,
                setLevel = setLevel,
                allUnlocks = allUnlocks,
                deliveredUnlocks = deliveredUnlocks,
                forceCompanion = forceCompanion,
                setCrystals = setCrystals
            )
            if (override != null) {
                Log.d(TAG, "Comando admin: coins=$setCoins level=$setLevel unlock=${override.unlockCompanions} force=$forceCompanion crystals=$setCrystals")
                deliveredUnlocks += override.unlockCompanions
                trySend(override)

                // Resetear SOLO los comandos one-shot. unlock_companions se conserva como
                // registro persistente de lo que el jugador posee (claimCharacter es idempotente).
                val resets = mutableMapOf<String, Any>()
                if (setCoins > 0)            resets["set_coins"]      = 0L
                if (setLevel > 0)            resets["set_level"]      = 0L
                if (forceCompanion != null)  resets["force_companion"] = ""
                setCrystals.keys.forEach { t -> resets["set_crystal_$t"] = -1L }
                if (resets.isNotEmpty()) {
                    resets["cmd_applied_at"] = FieldValue.serverTimestamp()
                    docRef.update(resets)
                        .addOnSuccessListener { Log.d(TAG, "Comandos one-shot reseteados") }
                        .addOnFailureListener { e ->
                            // Si el reset falla, el comando SE QUEDA PUESTO en el documento y se
                            // vuelve a aplicar en cada snapshot: un bucle silencioso. Tiene que
                            // verse en Crashlytics, no solo en un Logcat que nadie está mirando.
                            com.cukiti.teikafusion.OnlineTelemetry.report(TAG, "resetAdminCommands", e)
                        }
                }
            }
        }
        awaitClose { reg.remove() }
    }
}
