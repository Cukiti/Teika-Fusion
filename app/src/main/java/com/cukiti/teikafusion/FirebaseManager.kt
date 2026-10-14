package com.cukiti.teikafusion

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.auth
import com.google.firebase.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

object FirebaseManager {
    private const val TAG = "FirebaseManager"
    private var auth: FirebaseAuth? = null
    private val _currentUid = MutableStateFlow<String?>(null)
    val currentUidFlow: StateFlow<String?> = _currentUid.asStateFlow()

    fun init(context: Context) {
        try {
            auth = Firebase.auth.also { firebaseAuth ->
                _currentUid.value = firebaseAuth.currentUser?.uid
                firebaseAuth.addAuthStateListener { state ->
                    _currentUid.value = state.currentUser?.uid
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Firebase Auth init failed", e)
        }
    }

    /**
     * Devuelve el UID de la sesión de Firebase.
     *
     * Con [createAnonymous] = true crea una sesión anónima si aún no hay ninguna (necesario justo
     * antes de VINCULAR con Google: ese anónimo se convierte al instante en la cuenta real).
     *
     * Con [createAnonymous] = false NO crea nada: solo lee la sesión existente (un jugador vinculado
     * que vuelve). Los anónimos juegan 100% en local y NUNCA generan un usuario en Firebase Auth —
     * así la consola de Auth no se inunda de cuentas fantasma que no respaldan nada.
     * Seguro de llamar varias veces.
     */
    suspend fun ensureSignedIn(createAnonymous: Boolean = true): String? = try {
        val a = auth ?: return null
        if (a.currentUser == null && createAnonymous) a.signInAnonymously().await()
        a.currentUser?.uid.also { _currentUid.value = it }
    } catch (e: Exception) {
        Log.e(TAG, "Anonymous sign-in failed", e)
        null
    }

    /**
     * Ejecuta [block] garantizando que, si el vínculo NO llega a completarse, el usuario anónimo
     * creado para intentarlo **se borra**.
     *
     * Sin esto, cada intento fallido de vincular (quedarse sin red a mitad, cancelar el diálogo del
     * proveedor…) dejaba una cuenta anónima flotando en Firebase Auth que nadie limpiaba nunca. Se
     * acumulaban indefinidamente y ensuciaban la consola sin respaldar ningún progreso.
     *
     * **YA NO CREA NINGÚN ANÓNIMO (2026-07-31).** Antes llamaba a `ensureSignedIn()` con su valor por
     * defecto, así que *fabricaba* una sesión anónima justo para poder vincularla. Eso era peor que
     * inútil: por política, un jugador anónimo NUNCA tiene usuario en Firebase Auth (juega 100 % en
     * local), luego ese anónimo recién nacido no respaldaba nada — y a cambio metía todo el login en
     * la ruta de `linkWithCredential`, que con una cuenta ya existente **colisiona siempre**. Cada
     * "Continuar con Google" acababa así: crear anónimo → colisión → borrar anónimo → reintentar con
     * el mismo ID token, **que Google ya había consumido**. Resultado: "No se pudo vincular tu cuenta".
     *
     * Ahora solo se adopta un anónimo que YA existiera (el único caso en que hay progreso que
     * preservar); si no hay sesión, el proveedor entra por la vía directa (`signInWithCredential`).
     */
    private suspend inline fun withThrowawayAnonymous(block: () -> LinkAuthResult): LinkAuthResult {
        val a = auth
        // NO se crea sesión: solo se mira la que hubiera. Un anónimo preexistente sí es nuestro y se
        // intentará vincular para conservar su UID.
        val adopted = a?.currentUser?.takeIf { it.isAnonymous }?.uid

        val result = block()

        // El vínculo no cuajó y seguimos siendo aquel anónimo → a la basura (no respaldaba nada).
        if (adopted != null && !result.isSuccess) {
            val orphan = a?.currentUser
            if (orphan?.isAnonymous == true && orphan.uid == adopted) {
                try {
                    orphan.delete().await()
                    _currentUid.value = null
                    Log.i(TAG, "Anónimo desechado tras fallar el vínculo: ${orphan.uid}")
                } catch (e: Exception) {
                    OnlineTelemetry.report(TAG, "deleteThrowawayAnonymous($adopted)", e)
                }
            }
        }
        return result
    }

    fun currentUid(): String? = try { auth?.currentUser?.uid } catch (_: Exception) { null }

    /** Cierra la sesión de Firebase (anónima o vinculada). Tras reiniciar NO se crea sesión anónima:
     *  el jugador vuelve a la bienvenida y juega en local hasta que decida vincular una cuenta. */
    fun signOut() {
        try {
            auth?.signOut()
            _currentUid.value = null
            Log.i(TAG, "Firebase sign-out OK")
        } catch (e: Exception) {
            Log.e(TAG, "Firebase sign-out failed", e)
        }
    }

    /** Resultado de [deleteAccount]: distingue "no se pudo" de "hay que volver a iniciar sesión". */
    enum class DeleteAccountResult {
        /** El usuario ya no existe en Firebase Auth. */
        DELETED,

        /**
         * Firebase exige una credencial reciente para una operación tan sensible
         * ([com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException]). El jugador debe
         * volver a entrar con su proveedor y repetir el borrado.
         */
        REQUIRES_RECENT_LOGIN,

        /** Fallo de red u otro error: no se borró nada, se puede reintentar. */
        FAILED,

        /** No había sesión: nada que borrar (jugador invitado). */
        NO_SESSION
    }

    /**
     * Elimina la cuenta del jugador de Firebase Auth.
     *
     * **Solo la identidad.** Los documentos de Firestore (cloud_saves, player_stats, leaderboard)
     * hay que borrarlos ANTES de llamar aquí: sus reglas exigen `request.auth.uid`, así que una vez
     * eliminado el usuario ya no hay permiso para tocarlos y quedarían huérfanos para siempre.
     */
    suspend fun deleteAccount(): DeleteAccountResult {
        val user = try { auth?.currentUser } catch (_: Exception) { null }
            ?: return DeleteAccountResult.NO_SESSION
        return try {
            user.delete().await()
            _currentUid.value = null
            Log.i(TAG, "Cuenta eliminada de Firebase Auth")
            DeleteAccountResult.DELETED
        } catch (e: com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException) {
            Log.w(TAG, "Borrado de cuenta: Firebase pide reautenticación reciente", e)
            DeleteAccountResult.REQUIRES_RECENT_LOGIN
        } catch (e: Exception) {
            Log.e(TAG, "Borrado de cuenta falló", e)
            DeleteAccountResult.FAILED
        }
    }

    data class LinkAuthResult(
        val uid: String?,
        val linkedAnonymousAccount: Boolean = false,
        val switchedToExistingAccount: Boolean = false,
        val error: Throwable? = null
    ) {
        val isSuccess: Boolean get() = uid != null && error == null
    }

    /**
     * Convierte un Google ID token en la identidad canónica de Firebase: si la instalación es
     * anónima la vincula para conservar el UID; si esa cuenta de Google ya existía en otro
     * dispositivo, cambia a la cuenta existente (borrando antes el anónimo huérfano).
     *
     * Los ID tokens de Google son de un solo uso, pero en una colisión Firebase entrega
     * `collision.updatedCredential` reutilizable.
     */
    suspend fun authenticateWithGoogle(
        idToken: String,
        freshIdToken: (suspend () -> String?)? = null
    ): LinkAuthResult {
        val a = auth ?: return LinkAuthResult(
            uid = null,
            error = IllegalStateException("Firebase Auth no está inicializado")
        )
        if (idToken.isBlank()) {
            return LinkAuthResult(
                uid = a.currentUser?.uid,
                error = IllegalStateException("Google ID token vacío")
            )
        }
        return withThrowawayAnonymous { authenticateWithGoogleInner(a, idToken, freshIdToken) }
    }

    private suspend fun authenticateWithGoogleInner(
        a: FirebaseAuth,
        idToken: String,
        freshIdToken: (suspend () -> String?)?
    ): LinkAuthResult {
        return try {
            val credential = com.google.firebase.auth.GoogleAuthProvider.getCredential(idToken, null)
            val current = a.currentUser

            if (current != null && current.providerData.any {
                    it.providerId == com.google.firebase.auth.GoogleAuthProvider.PROVIDER_ID
                }
            ) {
                _currentUid.value = current.uid
                LinkAuthResult(uid = current.uid)
            } else if (current?.isAnonymous == true) {
                try {
                    val linked = current.linkWithCredential(credential).await().user
                    _currentUid.value = linked?.uid
                    Log.i(TAG, "Anonymous UID linked to Google: ${linked?.uid}")
                    LinkAuthResult(uid = linked?.uid, linkedAnonymousAccount = true)
                } catch (collision: FirebaseAuthUserCollisionException) {
                    // Esa cuenta de Google ya existía → entra a la cuenta canónica existente.
                    //
                    // OJO CON LA CREDENCIAL: un ID token de Google **se consume** al intentar el
                    // vínculo, así que reutilizar el mismo aquí devolvía INVALID_IDP_RESPONSE y el
                    // jugador veía "No se pudo vincular tu cuenta". Firebase suele entregar una
                    // credencial ya renovada en `updatedCredential`; cuando no lo hace, se pide un
                    // ID token NUEVO al proveedor ([freshIdToken]) antes de rendirse.
                    val collisionCredential = collision.updatedCredential
                        ?: freshIdToken?.invoke()?.takeIf { it.isNotBlank() }?.let {
                            Log.i(TAG, "Colisión Google: ID token renovado para entrar a la cuenta existente")
                            com.google.firebase.auth.GoogleAuthProvider.getCredential(it, null)
                        }
                        ?: return LinkAuthResult(
                            uid = null,
                            error = IllegalStateException(
                                "Esa cuenta de Google ya existe y no se pudo renovar el ID token"
                            )
                        )
                    val orphanUid = current.uid
                    try { current.delete().await() } catch (e: Exception) {
                        // Que esto falle deja una cuenta anónima fantasma en Firebase Auth para
                        // siempre (comprobado: quedó una del 2026-07-30). Se reporta, no se traga.
                        OnlineTelemetry.report(TAG, "deleteOrphanAnonymous($orphanUid)", e)
                    }
                    val existing = a.signInWithCredential(collisionCredential).await().user
                    _currentUid.value = existing?.uid
                    Log.i(TAG, "Switched duplicate anonymous UID $orphanUid to ${existing?.uid}")
                    LinkAuthResult(uid = existing?.uid, switchedToExistingAccount = true)
                }
            } else {
                val signedIn = a.signInWithCredential(credential).await().user
                _currentUid.value = signedIn?.uid
                LinkAuthResult(uid = signedIn?.uid)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Firebase authentication failed", e)
            LinkAuthResult(uid = a.currentUser?.uid, error = e)
        }
    }
}
