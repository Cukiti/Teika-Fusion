package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La regla de la racha diaria ([DailyStreakRules]), fijada por test. Es el corazón de la retención:
 * si esta regla se rompe, la notificación "Día N 🔥" amenaza con un número falso y el jugador
 * pierde (o conserva) rachas que no debía.
 */
class DailyStreakRulesTest {

    private val today = 20_000L   // época en días (LocalDate.toEpochDay)

    @Test
    fun sameDayIsNotANewDayAndKeepsTheStreak() {
        assertFalse(DailyStreakRules.isNewDay(lastPlayedDay = today, today = today))
        assertEquals(7, DailyStreakRules.advance(lastPlayedDay = today, today = today, currentStreak = 7))
    }

    @Test
    fun consecutiveDayGrowsTheStreakByOne() {
        assertTrue(DailyStreakRules.isNewDay(lastPlayedDay = today - 1, today = today))
        assertEquals(8, DailyStreakRules.advance(lastPlayedDay = today - 1, today = today, currentStreak = 7))
    }

    @Test
    fun anyGapResetsTheStreakToOne() {
        assertEquals(1, DailyStreakRules.advance(lastPlayedDay = today - 2, today = today, currentStreak = 30))
        assertEquals(1, DailyStreakRules.advance(lastPlayedDay = today - 365, today = today, currentStreak = 99))
    }

    @Test
    fun firstEverLaunchStartsAtOne() {
        assertTrue(DailyStreakRules.isNewDay(lastPlayedDay = -1L, today = today))
        assertEquals(1, DailyStreakRules.advance(lastPlayedDay = -1L, today = today, currentStreak = 0))
    }
}
