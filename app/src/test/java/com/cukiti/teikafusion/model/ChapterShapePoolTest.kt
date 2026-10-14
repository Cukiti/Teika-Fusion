package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato del REPARTO DEL POOL DE FIGURAS POR CAPÍTULO (2026-07-31b).
 *
 * Es la mitad "tablero" del diagnóstico del usuario: antes la figura salía de la posición GLOBAL, y
 * como un capítulo ocupa el 7 % del juego, un bioma entero dibujaba sobre ~12 de las 100 figuras.
 * Lo que se fija aquí es justo lo que hace que un capítulo NO se sienta igual de principio a fin.
 */
class ChapterShapePoolTest {

    @Test
    fun `ningun nivel de un capitulo repite figura`() {
        // LA garantía del sistema. Si esto se rompe, vuelve el «todos los niveles son el mismo».
        for (i in LevelMap.chapters.indices) {
            val niveles = LevelMap.chapterStart[i]..LevelMap.chapterLastLevel(i)
            val figuras = niveles.map { ChapterShapePool.shapeIdFor(it) }
            assertEquals(
                "capítulo $i (${LevelMap.chapters[i].biome}) repite figuras: $figuras",
                figuras.size, figuras.toSet().size
            )
        }
    }

    @Test
    fun `dos niveles seguidos no caen pegados en el pool`() {
        // Que sean distintas no basta: si fueran consecutivas serían "la de antes con un bloque
        // más". El paso es grande a propósito.
        for (i in LevelMap.chapters.indices) {
            val niveles = LevelMap.chapterStart[i]..LevelMap.chapterLastLevel(i)
            niveles.zipWithNext().forEach { (a, b) ->
                val saltoEnPool = kotlin.math.abs(
                    ChapterShapePool.shapeIdFor(b) - ChapterShapePool.shapeIdFor(a)
                )
                assertTrue(
                    "niveles $a→$b: figuras contiguas en el pool (salto $saltoEnPool)",
                    saltoEnPool > 1
                )
            }
        }
    }

    @Test
    fun `toda figura existe en el pool`() {
        // `LevelLayouts.getShape` cae a `s01()` fuera de 1..100 — un id fuera de rango convertiría
        // capítulos enteros en el mismo tablero sin que nada fallara a gritos.
        for (level in 1..LevelMap.totalLevels) {
            val id = ChapterShapePool.shapeIdFor(level)
            assertTrue("nivel $level → figura $id fuera del pool", id in 1..ChapterShapePool.POOL_SIZE)
        }
    }

    @Test
    fun `el pool avanza de capitulo en capitulo`() {
        // El juego tiene que seguir endureciéndose entre biomas: la ventana del último capítulo
        // empieza mucho más allá que la del primero.
        val primera = ChapterShapePool.windowStart(0, LevelMap.chapters.size)
        val ultima = ChapterShapePool.windowStart(LevelMap.chapters.size - 1, LevelMap.chapters.size)
        assertEquals(0, primera)
        assertEquals(ChapterShapePool.POOL_SIZE - ChapterShapePool.WINDOW, ultima)
        assertTrue("la ventana no avanza", ultima > primera)
    }

    @Test
    fun `capitulos distintos no abren con la misma figura`() {
        // Las ventanas se solapan a propósito (el salto entre biomas no debe ser un muro), pero
        // empezar dos biomas seguidos con el MISMO tablero delataría el truco.
        val aperturas = LevelMap.chapters.indices.map { ChapterShapePool.shapeIdFor(LevelMap.chapterStart[it]) }
        assertEquals("dos capítulos abren igual: $aperturas", aperturas.size, aperturas.toSet().size)
    }

    @Test
    fun `la ventana cabe en el pool`() {
        for (i in LevelMap.chapters.indices) {
            val start = ChapterShapePool.windowStart(i, LevelMap.chapters.size)
            assertTrue("capítulo $i se sale del pool", start + ChapterShapePool.WINDOW <= ChapterShapePool.POOL_SIZE)
        }
    }

    @Test
    fun `la ventana es mas grande que el capitulo mas largo`() {
        // Condición para que «ningún nivel repite figura» pueda cumplirse.
        val masLargo = LevelMap.chapters.maxOf { it.levelCount }
        assertTrue(
            "el capítulo más largo ($masLargo) no cabe en la ventana (${ChapterShapePool.WINDOW})",
            masLargo <= ChapterShapePool.WINDOW
        )
    }

    @Test
    fun `el paso es coprimo con la ventana`() {
        // De aquí sale la garantía de no repetición: si dejaran de ser coprimos, el recorrido se
        // cerraría antes de agotar la ventana y volverían las figuras repetidas dentro del bioma.
        var a = ChapterShapePool.STRIDE
        var b = ChapterShapePool.WINDOW
        while (b != 0) { val t = b; b = a % b; a = t }
        assertEquals("STRIDE y WINDOW deben ser coprimos", 1, a)
    }

    @Test
    fun `es determinista`() {
        // Un nivel no puede cambiar de tablero entre partidas.
        for (level in 1..LevelMap.totalLevels) {
            assertEquals(ChapterShapePool.shapeIdFor(level), ChapterShapePool.shapeIdFor(level))
        }
    }

    /** Los niveles reparados (2026-09-26) siguen sin repetir figura dentro de su capítulo. */
    @Test
    fun `los niveles reparados no repiten figura en su capitulo`() {
        ChapterShapePool.REPAIRED_LEVELS.keys.forEach { level ->
            val chapter = LevelMap.chapterIndexForLevel(level)
            val start = LevelMap.chapterStart[chapter]
            val figuras = (start until start + LevelMap.chapters[chapter].levelCount).map { ChapterShapePool.shapeIdFor(it) }
            assertEquals("capítulo $chapter repite figura: $figuras", figuras.size, figuras.distinct().size)
        }
    }
}
