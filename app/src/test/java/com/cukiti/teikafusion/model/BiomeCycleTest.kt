package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-353 · EL CASTIGO SIEMPRE LLEGA: el ritmo que decidió el usuario (ciclo de 5, 4 al final). */
class BiomeCycleTest {

    @Test
    fun `el golpe cae tras la calma del capitulo y los dos avisos`() {
        // P-361: la calma de cada capítulo es una tabla; el último tercio lleva un escalón menos.
        assertEquals(LevelMap.chapters.size, BiomeCycle.CHAPTER_CALM.size)
        assertEquals(LevelMap.chapters.size, BiomeCycle.CHAPTER_CALM_LATE.size)
        for (c in LevelMap.chapters.indices) {
            val first = LevelMap.chapterStart[c]
            val last = LevelMap.chapterLastLevel(c)
            assertEquals("el primer nivel del capítulo $c", BiomeCycle.CHAPTER_CALM[c] + 3, BiomeCycle.strikeAt(first))
            assertEquals("el último nivel del capítulo $c", BiomeCycle.CHAPTER_CALM_LATE[c] + 3, BiomeCycle.strikeAt(last))
            assertTrue("el último tercio no es más calmado", BiomeCycle.CHAPTER_CALM_LATE[c] <= BiomeCycle.CHAPTER_CALM[c])
            // Los dos avisos, siempre.
            assertEquals(BiomeCycle.strikeAt(last) - 2, BiomeCycle.warnAt(last))
            assertTrue(BiomeCycle.warnAt(last) >= 1)
        }
    }

    @Test
    fun `el contador siempre avanza y golpea al llegar`() {
        val level = LevelMap.chapterStart[5]
        var c = 0
        repeat(BiomeCycle.strikeAt(level) - 1) { c = BiomeCycle.advance(c); assertFalse(BiomeCycle.isStrike(c, level)) }
        c = BiomeCycle.advance(c)
        assertTrue(BiomeCycle.isStrike(c, level))
    }

    @Test
    fun `los nueve biomas con reloj golpean al final del ciclo`() {
        val level = LevelMap.chapterStart[5]
        val at = BiomeCycle.strikeAt(level)
        assertEquals(at, LevelDirector.swordThreshold(level))
        assertEquals(at, LevelDirector.meteorThreshold(level))
        assertEquals(at, LevelDirector.mirrorThreshold(level))
        assertEquals(at, LevelDirector.starIdleThreshold(level))
        assertEquals(at, LevelDirector.ninjaCutThreshold(level))
        assertEquals(at, LevelDirector.gluttonBiteThreshold(level))
        assertEquals(at - 1, LevelDirector.gluttonOpenThreshold(level))
        assertEquals(at, LevelDirector.volcanoFirstThreshold(level))
        assertEquals(at, LevelDirector.volcanoSurfacedThreshold(level))
    }

    @Test
    fun `la espada ancha parte su linea y la de al lado`() {
        assertEquals(4, KingSword.partner(3))
        assertEquals(6, KingSword.partner(7))
        assertEquals(16, KingSword.strikeCells(KingSword.ROW, 2).size)
    }
}
