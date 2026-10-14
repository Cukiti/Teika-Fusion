package com.cukiti.teikafusion

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.Calendar
import java.util.concurrent.TimeUnit

/**
 * Worker que publica una notificación ya redactada (título/cuerpo en inputData). Lo agenda
 * [NotificationScheduler] con un retraso inicial; WorkManager lo dispara aunque la app esté cerrada.
 */
class NotificationWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        val title = inputData.getString(KEY_TITLE) ?: return Result.success()
        val body = inputData.getString(KEY_BODY) ?: return Result.success()
        val notifId = inputData.getInt(KEY_NOTIF_ID, 9000)
        NotificationHelper.show(applicationContext, notifId, title, body)
        return Result.success()
    }

    companion object {
        const val KEY_TITLE = "title"
        const val KEY_BODY = "body"
        const val KEY_NOTIF_ID = "notif_id"
    }
}

/**
 * Agenda (y cancela) las notificaciones LOCALES de reenganche con WorkManager. Pensado para llamarse
 * cuando la app pasa a segundo plano (programar) y al volver al primer plano (cancelar la de
 * energía, ya irrelevante mientras juega).
 */
object NotificationScheduler {
    private const val WORK_ENERGY = "notif_energy_full"
    private const val WORK_DAILY = "notif_daily_reminder"
    private const val WORK_STREAK = "notif_streak_danger"

    const val NOTIF_ENERGY = 2001
    const val NOTIF_DAILY = 2002
    const val NOTIF_STREAK = 2003

    /**
     * Notifica cuando las vidas se recargan al máximo. [delayMs] = tiempo hasta que estén llenas.
     * Si ya están llenas (<= 0) cancela cualquier aviso pendiente.
     */
    fun scheduleEnergyFull(context: Context, delayMs: Long) {
        if (delayMs <= 0L) {
            cancelEnergyFull(context)
            return
        }
        val data = workDataOf(
            // «Vidas», no «energía» (P-135): es como lo llama el juego, y como lo llamaba ya el
            // cuerpo de esta misma notificación. Los identificadores internos (WORK_ENERGY,
            // NOTIF_ENERGY) se quedan: su string es la clave de trabajo único de WorkManager y
            // cambiarla dejaría huérfanos los avisos ya programados en los móviles de la gente.
            NotificationWorker.KEY_TITLE to "¡Vidas al máximo!",
            NotificationWorker.KEY_BODY to "Tus vidas están llenas. ¡Vuelve y rompe líneas en Teika Fusion!",
            NotificationWorker.KEY_NOTIF_ID to NOTIF_ENERGY
        )
        enqueue(context, WORK_ENERGY, delayMs, data)
    }

    fun cancelEnergyFull(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_ENERGY)
    }

    /**
     * Recordatorio diario de partida, ~19:00 hora local. OJO con el copy: las MISIONES DIARIAS se
     * eliminaron del juego — el texto anterior prometía "misiones de hoy" y "recompensas" que ya no
     * existen (abrir la app tras ese aviso era encontrarse con nada: promesa rota). Ahora invita a
     * lo que el juego SÍ tiene: la aventura de niveles y las fusiones.
     */
    fun scheduleDailyReminder(context: Context) {
        val data = workDataOf(
            NotificationWorker.KEY_TITLE to "¿Un nivel más? 🧩",
            NotificationWorker.KEY_BODY to "Tus Teikas te esperan. Sigue tu aventura por los biomas de Teika Fusion.",
            NotificationWorker.KEY_NOTIF_ID to NOTIF_DAILY
        )
        enqueue(context, WORK_DAILY, millisUntilHour(19), data)
    }

    /**
     * Aviso de racha en peligro ("Día N 🔥 ¡No rompas tu racha!"), ~20:00. Solo si hay racha viva
     * ([streak] >= 1). Si el jugador ya jugó hoy ([playedToday]) se programa para mañana (no molesta
     * con la racha ya asegurada); si no, se programa para esta misma tarde.
     */
    fun scheduleStreakReminder(context: Context, streak: Int, playedToday: Boolean) {
        if (streak < 1) {
            cancelStreakReminder(context)
            return
        }
        val body = "Día $streak 🔥 ¡No rompas tu racha! Juega una partida hoy para conservarla."
        val data = workDataOf(
            NotificationWorker.KEY_TITLE to "Tu racha está en peligro 🔥",
            NotificationWorker.KEY_BODY to body,
            NotificationWorker.KEY_NOTIF_ID to NOTIF_STREAK
        )
        enqueue(context, WORK_STREAK, millisUntilHour(20, minDaysAhead = if (playedToday) 1 else 0), data)
    }

    fun cancelStreakReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_STREAK)
    }

    fun cancelDailyReminder(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_DAILY)
    }

    /**
     * Apaga los tres avisos (P-166). Lo llama el interruptor de Ajustes: no basta con dejar de
     * programarlos, porque los que ya están en la cola de WorkManager seguirían saltando durante
     * horas después de que el jugador dijera que no los quiere.
     */
    fun cancelAll(context: Context) {
        cancelEnergyFull(context)
        cancelDailyReminder(context)
        cancelStreakReminder(context)
    }

    private fun enqueue(context: Context, workName: String, delayMs: Long, data: androidx.work.Data) {
        val req = OneTimeWorkRequestBuilder<NotificationWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, req)
    }

    /**
     * Milisegundos hasta la próxima ocurrencia de [hour]:00 local. [minDaysAhead] fuerza un mínimo de
     * días por delante (1 = al menos mañana), usado para no avisar de la racha el mismo día que ya jugó.
     */
    private fun millisUntilHour(hour: Int, minDaysAhead: Int = 0): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (minDaysAhead > 0) add(Calendar.DAY_OF_MONTH, minDaysAhead)
            while (timeInMillis <= now.timeInMillis) add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
