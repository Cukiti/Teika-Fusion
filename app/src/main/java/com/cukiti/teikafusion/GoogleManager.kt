package com.cukiti.teikafusion

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Resultado del login con Google. Existe para que el botón NUNCA quede mudo: antes `login`
 * devolvía `null` tanto si el jugador cancelaba como si la app estaba mal configurada o no había
 * red, y la UI no podía distinguirlos — el jugador pulsaba y no pasaba nada.
 */
sealed interface GoogleLoginResult {
    /** El jugador eligió cuenta y Google devolvió el ID token. */
    data class Success(val idToken: String) : GoogleLoginResult
    /** El jugador cerró el selector — silencio deliberado, no se avisa de nada. */
    object Cancelled : GoogleLoginResult
    /** Fallo real: [userMessage] es el texto en español que se le enseña al jugador. */
    data class Failure(val userMessage: String) : GoogleLoginResult
}

/**
 * Login con Google ("Continuar con Google") sobre el **Credential Manager** moderno
 * (androidx.credentials + googleid), el flujo recomendado por Firebase. Es el ÚNICO proveedor de
 * identidad del juego desde el 2026-07-31.
 *
 * Detalle que condiciona todo lo demás: Credential Manager NO persiste una sesión propia (no hay
 * ningún token que sobreviva a un reinicio). Quien persiste la sesión de Google es **Firebase Auth**
 * (el usuario queda logueado con provider `google.com`). Por eso [init] rehidrata el estado leyendo
 * `FirebaseAuth.currentUser`, y al arrancar NO hace falta un token para el usuario que vuelve — basta
 * la sesión de Firebase ya existente.
 *
 * Filtra el TAG "GoogleManager" en Logcat para ver init/login/cancel/error.
 */
object GoogleManager {
    private const val TAG = "GoogleManager"

    /** Player ID de Google (uid canónico de Firebase con provider google.com) — null si no hay sesión. */
    private val _googleId = MutableStateFlow<String?>(null)
    val googleId: StateFlow<String?> = _googleId.asStateFlow()

    /** Nombre visible de la cuenta de Google — null si no hay sesión. */
    private val _displayName = MutableStateFlow<String?>(null)
    val displayName: StateFlow<String?> = _displayName.asStateFlow()

    private var serverClientId: String? = null

    /**
     * Rehidrata la sesión desde Firebase: si el usuario actual está vinculado con Google, publica su
     * uid/nombre para que la UI lo muestre como "vinculado" tras reabrir la app. También cachea el
     * serverClientId (el web client que genera el plugin google-services en strings.xml).
     */
    fun init(context: Context) {
        try {
            // getIdentifier A PROPÓSITO (no R.string directo): default_web_client_id lo GENERA el
            // plugin google-services solo si google-services.json trae el cliente web OAuth. Con la
            // referencia directa, un json sin ese cliente ROMPERÍA el build entero; así, solo se
            // degrada el login de Google (con aviso en el log), que es el comportamiento deseado.
            @Suppress("DiscouragedApi")
            serverClientId = runCatching {
                val resId = context.resources.getIdentifier(
                    "default_web_client_id", "string", context.packageName
                )
                if (resId != 0) context.getString(resId) else null
            }.getOrNull()
            if (serverClientId.isNullOrBlank()) {
                Log.w(TAG, "⚠ default_web_client_id ausente — Google Sign-In fallará (revisa google-services.json)")
            }
            observeFirebaseSession()
        } catch (e: Exception) {
            Log.e(TAG, "GoogleManager init falló", e)
        }
    }

    /**
     * Mantiene id/nombre **atados a la sesión de Firebase**, no a una foto del arranque.
     *
     * Hasta el 2026-09-01 esto era un `hydrateFromFirebase()` de UNA sola pasada, y de ahí salía
     * media cara del bug que reportó el usuario: **Ajustes podía decir «inicia sesión» mientras el
     * juego mostraba su cuenta**. La sesión de Firebase no siempre está lista en el instante del
     * `init` —se descifra del disco, se refresca contra el servidor, puede llegar tarde o no llegar
     * nunca si el blob restaurado no se puede abrir— y esa foto se quedaba congelada hasta reiniciar
     * la app. Con un listener, la fila de Ajustes **no puede** contradecir a la sesión real.
     *
     * Es el mismo patrón que ya usaba `FirebaseManager.currentUidFlow`; lo raro era tener dos formas
     * de preguntar lo mismo y que solo una se enterase de los cambios.
     */
    fun observeFirebaseSession() {
        try {
            com.google.firebase.auth.FirebaseAuth.getInstance().addAuthStateListener { auth ->
                publishFromUser(auth.currentUser)
            }
        } catch (e: Exception) {
            Log.e(TAG, "observeFirebaseSession falló", e)
        }
    }

    /** Lee la sesión de Firebase AHORA y publica lo que haya. Idempotente. */
    fun hydrateFromFirebase() {
        try {
            publishFromUser(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser)
        } catch (e: Exception) {
            Log.e(TAG, "hydrateFromFirebase falló", e)
        }
    }

    /**
     * Traduce un usuario de Firebase a lo que la UI enseña. **También apaga**: un usuario anónimo,
     * o ninguno, significa que no hay cuenta vinculada, y dejar el id anterior encendido es
     * exactamente cómo Ajustes acababa enseñando una cuenta que ya no existía.
     */
    private fun publishFromUser(user: com.google.firebase.auth.FirebaseUser?) {
        val isGoogle = user != null &&
            !user.isAnonymous &&
            user.providerData.any { it.providerId == GoogleAuthProvider.PROVIDER_ID }
        if (!isGoogle) {
            if (_googleId.value != null) Log.d(TAG, "sesión Google ya no activa — se apaga la fila")
            _googleId.value = null
            _displayName.value = null
            return
        }
        val real = user!!
        _googleId.value = real.uid
        val name = real.displayName
            ?: real.providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }?.displayName
        _displayName.value = name?.takeIf { it.isNotBlank() }
        Log.d(TAG, "sesión Google activa uid=${real.uid} name=$name")
    }

    /** false si el web client aún no está disponible (google-services mal configurado). */
    fun isConfigured(): Boolean = !serverClientId.isNullOrBlank()

    /**
     * Última causa técnica de fallo (nombre de excepción + mensaje del sistema). Se adjunta al aviso
     * SOLO en builds de debug: en producción el jugador ve una frase en español, pero en el móvil de
     * desarrollo hace falta saber *cuál* de la docena de fallos posibles de Credential Manager fue,
     * sin tener que estar mirando Logcat.
     */
    @Volatile
    var lastErrorDetail: String? = null
        private set

    /**
     * Lanza el selector de cuentas de Google y devuelve el **Google ID token** para que el ViewModel
     * autentique con Firebase (`authenticateWithGoogle`) y sincronice el progreso. Es `suspend`
     * (Credential Manager es basado en corrutinas — no usa onActivityResult).
     *
     * **DOS INTENTOS, y el orden importa (2026-07-31).** Antes solo se usaba [GetGoogleIdOption], que
     * es el flujo de *hoja inferior automática*: Credential Manager le aplica un **enfriamiento** —si
     * el jugador cierra la hoja varias veces, deja de mostrarla durante ~24 h y responde al instante
     * con `NoCredentialException`/cancelación. Desde fuera es idéntico a "el botón no hace nada", y es
     * la causa clásica de "ayer funcionaba y hoy no" mientras se prueba el login una y otra vez.
     *
     * Por eso el intento principal es ahora [GetSignInWithGoogleOption], el flujo **de botón**: es el
     * que Google especifica para un "Continuar con Google" pulsado a propósito, siempre abre el
     * selector y no está sujeto a ese enfriamiento. La hoja queda de reserva por si el flujo de botón
     * no está disponible en el dispositivo.
     *
     * Distingue cancelación (silencio) de fallo (mensaje al jugador): ver [GoogleLoginResult].
     */
    suspend fun login(activity: Activity): GoogleLoginResult {
        lastErrorDetail = null
        val clientId = serverClientId
        if (clientId.isNullOrBlank()) {
            // Falta `default_web_client_id`: el google-services.json de esta variante no trae el
            // cliente OAuth web (client_type 3). Es un fallo de CONFIGURACIÓN de la build, no del
            // jugador — se avisa igualmente para que el botón no quede mudo.
            Log.w(TAG, "login omitido: serverClientId no configurado")
            lastErrorDetail = "default_web_client_id ausente"
            return GoogleLoginResult.Failure("Google no está disponible en esta versión del juego.")
        }

        // 1) Flujo de BOTÓN — el correcto para un toque explícito, sin enfriamiento.
        val buttonOption = GetSignInWithGoogleOption.Builder(clientId).build()
        when (val first = attempt(activity, buttonOption, "boton")) {
            is Attempt.Done -> return first.result
            Attempt.TryNext -> Unit   // no disponible aquí: se prueba la hoja
        }

        // 2) RESERVA: hoja inferior clásica. filterByAuthorizedAccounts=false → cualquier cuenta.
        val sheetOption = GetGoogleIdOption.Builder()
            .setServerClientId(clientId)
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()
        return when (val second = attempt(activity, sheetOption, "hoja")) {
            is Attempt.Done -> second.result
            // Ni botón ni hoja: el dispositivo no tiene cuenta utilizable.
            Attempt.TryNext -> {
                Log.w(TAG, "✗ login Google: ni el flujo de botón ni la hoja devolvieron credencial")
                GoogleLoginResult.Failure("No hay ninguna cuenta de Google disponible en este dispositivo.")
            }
        }
    }

    /** Resultado de un intento: o hay veredicto final, o hay que probar el siguiente flujo. */
    private sealed interface Attempt {
        data class Done(val result: GoogleLoginResult) : Attempt
        object TryNext : Attempt
    }

    private suspend fun attempt(
        activity: Activity,
        option: androidx.credentials.CredentialOption,
        label: String
    ): Attempt = try {
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val response = CredentialManager.create(activity).getCredential(activity, request)
        val credential = response.credential
        val idToken = try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            Log.e(TAG, "[$label] No se pudo parsear el Google ID token (tipo=${credential.type})", e)
            null
        }
        if (idToken.isNullOrBlank()) {
            lastErrorDetail = "[$label] credencial sin idToken (tipo=${credential.type})"
            Log.w(TAG, "[$label] credencial recibida pero sin idToken — tipo=${credential.type}")
            Attempt.Done(GoogleLoginResult.Failure("Google no devolvió tu cuenta. Inténtalo de nuevo."))
        } else {
            Log.i(TAG, "✓ [$label] login OK — idToken recibido")
            Attempt.Done(GoogleLoginResult.Success(idToken))
        }
    } catch (e: androidx.credentials.exceptions.GetCredentialCancellationException) {
        // El jugador cerró el selector — decisión suya, no se le molesta con un aviso.
        Log.d(TAG, "[$label] login Google cancelado por el jugador")
        Attempt.Done(GoogleLoginResult.Cancelled)
    } catch (e: androidx.credentials.exceptions.NoCredentialException) {
        // Sin credencial por este camino: NO es final — puede que el otro flujo sí la tenga.
        lastErrorDetail = "[$label] NoCredentialException: ${e.message}"
        Log.w(TAG, "[$label] sin credencial disponible: ${e.message}")
        Attempt.TryNext
    } catch (e: androidx.credentials.exceptions.GetCredentialProviderConfigurationException) {
        // Falta el proveedor de Play Services (credentials-play-services-auth) o está desactualizado.
        lastErrorDetail = "[$label] proveedor no configurado: ${e.message}"
        Log.e(TAG, "[$label] proveedor de credenciales no configurado", e)
        Attempt.Done(GoogleLoginResult.Failure("Actualiza los Servicios de Google Play para continuar con Google."))
    } catch (e: GetCredentialException) {
        // Resto de fallos del Credential Manager (configuración OAuth, red, cuenta bloqueada…).
        // `type` trae el código canónico de Android y es lo que de verdad identifica el fallo.
        lastErrorDetail = "[$label] ${e.javaClass.simpleName}/${e.type}: ${e.message}"
        Log.w(TAG, "✗ [$label] login Google: ${e.javaClass.simpleName} type=${e.type} msg=${e.message}")
        OnlineTelemetry.report(TAG, "login($label)", e)
        Attempt.Done(GoogleLoginResult.Failure("No se pudo conectar con Google. Inténtalo de nuevo."))
    } catch (e: Exception) {
        lastErrorDetail = "[$label] ${e.javaClass.simpleName}: ${e.message}"
        Log.e(TAG, "✗ [$label] login Google falló", e)
        OnlineTelemetry.report(TAG, "login($label)", e)
        Attempt.Done(GoogleLoginResult.Failure("No se pudo conectar con Google. Inténtalo de nuevo."))
    }

    /** Publica id/nombre tras autenticar con Firebase (lo llama el ViewModel con el uid canónico). */
    fun publishSession(uid: String?, name: String?) {
        _googleId.value = uid
        if (!name.isNullOrBlank()) _displayName.value = name
    }

    fun logOut(context: Context) {
        try {
            _googleId.value = null
            _displayName.value = null
            // Limpia el estado de credenciales para que el próximo login vuelva a preguntar la cuenta.
            val cm = CredentialManager.create(context.applicationContext)
            // clearCredentialState es suspend; se lanza best-effort sin bloquear el logout.
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { cm.clearCredentialState(ClearCredentialStateRequest()) }
            }
            Log.d(TAG, "logOut: sesión Google limpiada")
        } catch (e: Exception) { Log.e(TAG, "logOut falló", e) }
    }
}
