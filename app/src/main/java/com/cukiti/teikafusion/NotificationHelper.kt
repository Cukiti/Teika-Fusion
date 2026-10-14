package com.cukiti.teikafusion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Construye y publica las notificaciones de reenganche (energía llena, reto diario, racha en
 * peligro) y crea el canal. Usado tanto por el [NotificationWorker] (locales programadas) como por
 * [NotificationService] (push remotas de FCM).
 */
object NotificationHelper {
    /** Debe coincidir con @string/notif_channel_reengage (lo usa el manifest para las push de FCM). */
    const val CHANNEL_ID = "teika_reengage"
    private const val CHANNEL_NAME = "Recordatorios de Teika"
    // Sin "retos diarios": las misiones diarias se eliminaron del juego (no prometer lo que no hay).
    // Y **VIDAS**, no «energía» (P-135, 2026-09-03): el juego solo habla de vidas y corazones —
    // «energía» no aparece en ninguna pantalla, y era el título de la propia notificación cuyo
    // cuerpo decía «tus vidas están llenas». Dos nombres para lo mismo en dos líneas seguidas.
    // OJO: el canal solo se crea si no existe, así que las instalaciones que ya lo tienen conservan
    // la descripción vieja. No se cambia el CHANNEL_ID para arreglarlo: eso borraría los ajustes de
    // notificación que el jugador haya tocado.
    private const val CHANNEL_DESC = "Vidas llenas, recordatorios de partida y rachas en peligro"

    fun ensureChannel(context: Context) {
        // (Sin guard de SDK_INT: minSdk 26 = O, los canales existen siempre.)
        val mgr = context.getSystemService(NotificationManager::class.java) ?: return
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
                enableVibration(true)
            }
            mgr.createNotificationChannel(ch)
        }
    }

    /** En Android 13+ POST_NOTIFICATIONS es un permiso en runtime; antes siempre concedido. */
    fun hasPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, notifId: Int, title: String, body: String) {
        ensureChannel(context)
        if (!hasPermission(context)) return

        val launch = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, notifId, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF4DB8FF.toInt())   // acento neón de la marca para el tinte del icono
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pi)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        } catch (_: SecurityException) {
            // El usuario revocó el permiso entre el check y el notify — ignorar.
        }
    }
}
