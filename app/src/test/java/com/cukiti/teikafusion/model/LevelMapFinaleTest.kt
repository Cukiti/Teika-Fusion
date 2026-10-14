package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Fija la aritmética de FINAL DE CAPÍTULO de [LevelMap] — es el disparador del premio único del
 * mural y su cinemática ([MuralCelebration]): si `finaleChapterForLevel` se desvía un nivel, el
 * jugador cobra el mural antes de tiempo o no lo cobra nunca. (El resto de la aritmética del mapa
 * ya está fijada en BiomeRegistryTest / BiomeTransitionTest / NamedTeikaRegistryTest.)
 */
class LevelMapFinaleTest {

    @Test
    fun `el ultimo nivel de cada capitulo es su finale y dispara SU indice`() {
        for (i in LevelMap.chapters.indices) {
            val last = LevelMap.chapterLastLevel(i)
            assertEquals("chapterLastLevel($i) debe ser el finale del capítulo $i",
                i, LevelMap.finaleChapterForLevel(last))
        }
    }

    @Test
    fun `ningun nivel que no sea ultimo de capitulo es finale`() {
        val finales = LevelMap.chapters.indices.map { LevelMap.chapterLastLevel(it) }.toSet()
        for (level in 1..LevelMap.totalLevels) {
            if (level in finales) continue
            assertEquals("El nivel $level no cierra ningún capítulo",
                -1, LevelMap.finaleChapterForLevel(level))
        }
    }

    @Test
    fun `los niveles de intro no pertenecen a ningun capitulo`() {
        for (level in 1..LevelMap.introLevels) {
            assertEquals(-1, LevelMap.chapterIndexForLevel(level))
            assertEquals(-1, LevelMap.finaleChapterForLevel(level))
        }
    }

    @Test
    fun `el finale del ultimo capitulo es el ultimo nivel del juego`() {
        assertEquals(
            LevelMap.totalLevels,
            LevelMap.chapterLastLevel(LevelMap.chapters.size - 1)
        )
    }

    @Test
    fun `chapterLastLevel de cada capitulo es el nivel anterior al arranque del siguiente`() {
        for (i in 0 until LevelMap.chapters.size - 1) {
            assertEquals(LevelMap.chapterStart[i + 1] - 1, LevelMap.chapterLastLevel(i))
        }
    }
}
