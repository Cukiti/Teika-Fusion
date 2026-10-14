package com.cukiti.teikafusion

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * Recibe las push REMOTAS de Firebase Cloud Messaging (campañas de reenganche enviadas desde la
 * consola de Firebase). Las locales programadas (energía/diario/racha) van por [NotificationScheduler].
 *
 * Si la app está en segundo plano y el mensaje trae bloque `notification`, el SDK la muestra solo
 * (usando el canal/icono por defecto del manifest). Aquí manejamos el caso de primer plano y los
 * mensajes solo-`data` para mostrarlas igualmente con nuestro canal.
 */
class NotificationService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val notif = message.notification
        // El payload `data` es texto arbitrario que viene de fuera. Hoy solo lo envía la consola de
        // Firebase, pero se acota igualmente: el día que haya backend propio, esto ya está cerrado.
        val title = (notif?.title ?: message.data["title"] ?: "Teika Fusion").sanitize(60)
        val body = (notif?.body ?: message.data["body"] ?: return).sanitize(240)
        if (body.isBlank()) return
        NotificationHelper.show(applicationContext, REMOTE_NOTIF_ID, title, body)
    }

    /** Recorta a [max] y quita saltos de línea/controles que rompen el formato de la notificación. */
    private fun String.sanitize(max: Int): String =
        filter { it == ' ' || !it.isISOControl() }.trim().take(max)

    // Hereda la deprecación del SDK de FCM (BoM 34); sigue siendo el callback vigente que el
    // servicio invoca al rotar el token — la anotación solo silencia el warning de herencia.
    @Deprecated("Deprecado aguas arriba en FirebaseMessagingService")
    override fun onNewToken(token: String) {
        // Sin backend propio: la consola de Firebase puede segmentar por app/tema sin token explícito.
        // Al ROTAR el token hay que re-suscribir. El alta inicial la cubre además
        // TeikaFusionApp.onCreate, porque este callback solo dispara una vez y sin red se perdía.
        Log.i(TAG, "FCM token actualizado")
        com.google.firebase.messaging.FirebaseMessaging.getInstance()
            .subscribeToTopic(TeikaFusionApp.FCM_CAMPAIGN_TOPIC)
    }

    companion object {
        private const val TAG = "NotificationService"
        private const val REMOTE_NOTIF_ID = 3001
    }
}
