package com.cukiti.teikafusion.model

/**
 * Regla PURA de la racha diaria, extraída de `PreferencesManager.checkAndUpdateStreak` para poder
 * testearla (el patrón de la casa: la regla en `model/`, el I/O en su dueño).
 *
 * La racha cuenta DÍAS DE CALENDARIO consecutivos con la app abierta (época `LocalDate.toEpochDay`):
 *  · mismo día → no pasa nada (no es día nuevo);
 *  · ayer → la racha sube 1;
 *  · cualquier hueco (o primer arranque, lastPlayedDay = -1) → la racha vuelve a 1.
 *
 * La racha NO regala teikoins (economía tiburón, decisión 2026-07): premia con XP
 * ([XpAward.FIRST_GAME_OF_DAY]), la celebración en Home y el aviso "Día N 🔥" de retención.
 */
object DailyStreakRules {

    /** ¿[today] es un día nuevo respecto al último jugado? */
    fun isNewDay(lastPlayedDay: Long, today: Long): Boolean = lastPlayedDay != today

    /**
     * Racha resultante al abrir la app el día [today] habiendo jugado por última vez
     * [lastPlayedDay]. Si no es día nuevo, devuelve [currentStreak] intacta.
     */
    fun advance(lastPlayedDay: Long, today: Long, currentStreak: Int): Int = when {
        lastPlayedDay == today     -> currentStreak
        lastPlayedDay == today - 1 -> currentStreak + 1
        else                       -> 1
    }
}
