package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-309 / P-362: la madera inicial por capítulo y la recta de objetivos del bot. */
class ChapterDifficultyTest {

    @Test
    fun `hay una madera por capitulo y dentro de su limite`() {
        assertEquals(LevelMap.chapters.size, ChapterDifficulty.CHAPTER_WOOD_BONUS.size)
        ChapterDifficulty.CHAPTER_WOOD_BONUS.forEach {
            assertTrue("madera $it fuera de rango", it in -ChapterDifficulty.WOOD_BONUS_LIMIT..ChapterDifficulty.WOOD_BONUS_LIMIT)
        }
    }

    @Test
    fun `el objetivo va de 85 a 55 en linea recta`() {
        assertEquals(85.0, ChapterDifficulty.targetWinRate(0), 0.001)
        assertEquals(55.0, ChapterDifficulty.targetWinRate(LevelMap.chapters.size - 1), 0.001)
    }

    @Test
    fun `la madera se recorta a un rango con sentido en todos los niveles`() {
        for (level in 1..LevelMap.totalLevels) {
            assertTrue(ChapterDifficulty.densityBonus(level) in -12..12)
        }
    }

    @Test
    fun `la intro y el tutorial usan el lado amable`() {
        for (level in 1..LevelMap.introLevels) {
            assertEquals(ChapterDifficulty.INTRO_WOOD_BONUS, ChapterDifficulty.densityBonus(level))
        }
    }

    /**
     * P-362: la tabla sustituye al dial SIN cambiar el juego. Estos son los valores que daba
     * `round(lerp(-4, 6, dial)).coerceIn(-12, 12)` con el dial de la iteración 6 (2026-09-28).
     */
    @Test
    fun `la tabla da lo mismo que el dial al que sustituye`() {
        val dial = doubleArrayOf(-1.0, -1.0, -1.0, 0.34, 0.49, 1.6, 1.6, -1.0, 1.6, 1.6, 0.31, 1.6, 1.6, 1.5)
        dial.forEachIndexed { chapter, d ->
            val old = Math.round(-4.0 + (6.0 - -4.0) * d).toInt().coerceIn(-12, 12)
            assertEquals("capítulo $chapter", old, ChapterDifficulty.CHAPTER_WOOD_BONUS[chapter])
        }
    }
}
