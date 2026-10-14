package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **P-92 (2026-09-02)**: el número de nivel que se le ENSEÑA al jugador.
 *
 * La casilla real del mapa no se toca —de ella cuelgan biomas, compañeros y murales—, pero las siete
 * primeras son las LECCIONES y el jugador nunca las juega como niveles. Con la numeración cruda, su
 * primer nivel de verdad se anunciaba «NIVEL 8 COMPLETADO» y el inicio le decía «NIVEL 10» tras dos
 * partidas (grabación `analisistutorial`).
 */
class LevelDisplayNumberTest {

    @Test
    fun `las siete lecciones conservan su propio numero y se llaman leccion`() {
        for (level in TutorialCampaign.FIRST_LEVEL..TutorialCampaign.LAST_LEVEL) {
            assertTrue("nivel $level debería ser lección", LevelMap.isLesson(level))
            assertEquals(level, LevelMap.displayNumber(level))
            assertEquals("LECCIÓN", LevelMap.displayWord(level))
        }
    }

    @Test
    fun `el primer nivel de verdad se presenta como el numero uno`() {
        val firstRealLevel = LevelMap.introLevels + 1
        assertFalse(LevelMap.isLesson(firstRealLevel))
        assertEquals(1, LevelMap.displayNumber(firstRealLevel))
        assertEquals("NIVEL", LevelMap.displayWord(firstRealLevel))
    }

    @Test
    fun `la numeracion mostrada es continua y no salta en la frontera`() {
        // Justo lo que rompía el vídeo: de «LECCIÓN 7 DE 7» se pasaba a «NIVEL 8».
        assertEquals(7, LevelMap.displayNumber(TutorialCampaign.LAST_LEVEL))
        assertEquals(1, LevelMap.displayNumber(TutorialCampaign.LAST_LEVEL + 1))
        // Y a partir de ahí sube de uno en uno hasta el final del juego.
        for (level in LevelMap.introLevels + 1 until LevelMap.totalLevels) {
            assertEquals(
                LevelMap.displayNumber(level) + 1,
                LevelMap.displayNumber(level + 1)
            )
        }
    }

    @Test
    fun `el ultimo nivel mostrado es el total sin las lecciones`() {
        assertEquals(
            LevelMap.totalLevels - LevelMap.introLevels,
            LevelMap.displayNumber(LevelMap.totalLevels)
        )
    }

    @Test
    fun `las lecciones y los niveles de intro del mapa son exactamente los mismos`() {
        // Si estos dos números se separan, la numeración mostrada empieza a mentir por los dos
        // lados. El KDoc de `LevelMap.introLevels` ya lo pide; aquí se vigila.
        assertEquals(TutorialCampaign.LAST_LEVEL, LevelMap.introLevels)
    }

    @Test
    fun `un nivel invalido no produce un numero absurdo`() {
        assertEquals(1, LevelMap.displayNumber(0))
        assertEquals(1, LevelMap.displayNumber(-3))
    }
}
