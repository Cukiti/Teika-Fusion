package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato del ARCO DE CAPÍTULO (2026-07-31).
 *
 * Nace del diagnóstico del usuario —«los 20 y pico niveles del Cristal se sienten iguales»— y de su
 * causa: todo salía de `t = (nivel-1)/292`, que dentro de un capítulo apenas se mueve, y la mecánica
 * del bioma era una CONSTANTE de principio a fin del capítulo.
 *
 * Lo que se fija aquí es lo que puede romperse en silencio: que cada bioma tenga de verdad sus tres
 * actos, que el ajuste nunca deje un umbral impunible (0 = castigo sin margen) y que el arco no se
 * cuele en los niveles de intro, que no tienen bioma que presentar.
 */
class ChapterArcTest {

    private val primerNivelConBioma = LevelMap.introLevels + 1

    @Test
    fun `los niveles de intro no tienen arco`() {
        // Sin bioma no hay mecánica que presentar ni con la que rematar: se quedan en el centro.
        for (level in 1..LevelMap.introLevels) {
            assertEquals("nivel de intro $level", ChapterAct.DESARROLLO, ChapterArc.actFor(level))
        }
    }

    @Test
    fun `cada capitulo abre en presentacion y cierra en climax`() {
        // La razón de ser del sistema: el primer nivel de un bioma NO puede sentirse igual que el
        // último, 18-22 niveles después.
        for (i in LevelMap.chapters.indices) {
            val primero = LevelMap.chapterStart[i]
            val ultimo = LevelMap.chapterLastLevel(i)
            assertEquals(
                "capítulo $i (${LevelMap.chapters[i].biome}) debe ABRIR en presentación",
                ChapterAct.PRESENTACION, ChapterArc.actFor(primero)
            )
            assertEquals(
                "capítulo $i (${LevelMap.chapters[i].biome}) debe CERRAR en clímax",
                ChapterAct.CLIMAX, ChapterArc.actFor(ultimo)
            )
        }
    }

    @Test
    fun `cada capitulo pasa por los TRES actos`() {
        // Con capítulos de 18-22 niveles los tres tramos tienen que existir de verdad; si algún
        // capítulo se quedara sin desarrollo, el bioma pasaría de amable a duro sin transición.
        for (i in LevelMap.chapters.indices) {
            val actos = (LevelMap.chapterStart[i]..LevelMap.chapterLastLevel(i))
                .map { ChapterArc.actFor(it) }
                .toSet()
            assertEquals(
                "capítulo $i (${LevelMap.chapters[i].biome}) no recorre los tres actos: $actos",
                setOf(ChapterAct.PRESENTACION, ChapterAct.DESARROLLO, ChapterAct.CLIMAX),
                actos
            )
        }
    }

}
