package com.cukiti.teikafusion.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/**
 * Comprobación ligera de conectividad. Se usa para decidir si ofrecer la ruta de revivir con
 * anuncio: solo tiene sentido mostrar el temporizador de revivir si el jugador puede pagar con
 * teikoins O ver un anuncio (lo que requiere internet validado).
 */
object NetworkUtil {
    fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        } catch (_: Exception) {
            false
        }
    }
}
