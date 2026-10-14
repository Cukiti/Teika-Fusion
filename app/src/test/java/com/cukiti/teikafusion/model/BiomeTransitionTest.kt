package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Continuidad del morph visual entre biomas.
 *
 * El test que de verdad importa es [laTransicionCierraLaVentanaEnUno]: mientras el progreso se
 * normalizó contra la ventana ENTERA, el último nivel de cada capítulo se quedaba en ~0,94 y el
 * bioma saliente desaparecía de golpe al pasar de nivel — el "corte duro" que el morph existe para
 * evitar. Aquí queda fijado que la ventana **cierra en 1f exacto**.
 */
class BiomeTransitionTest {

    /** Primer nivel de cada capítulo: el bioma es enteramente el suyo, sin rastro del siguiente. */
    @Test
    fun laVentanaAbreEnCero() {
        for (start in LevelMap.chapterStart) {
            assertEquals(
                "El nivel $start abre su capítulo: debe arrancar con su bioma al 100 %",
                0f, BiomeRegistry.transitionProgress(start), 0.0001f
            )
        }
    }

    /**
     * Último nivel de cada capítulo: la transición está COMPLETA. Es la mitad de la continuidad —
     * la otra mitad la pone [laFronteraEntreCapitulosNoDaSalto].
     */
    @Test
    fun laTransicionCierraLaVentanaEnUno() {
        // El último capítulo no cuenta: no hay bioma siguiente al que transformarse.
        for (i in 0 until LevelMap.chapters.size - 1) {
            val lastLevel = LevelMap.chapterStart[i + 1] - 1
            assertEquals(
                "El nivel $lastLevel cierra su capítulo: la transición debe estar completa",
                1f, BiomeRegistry.transitionProgress(lastLevel), 0.0001f
            )
        }
    }

    /**
     * **La invariante de continuidad.** En el último nivel del capítulo i, el fondo dibuja
     * `nextBiome` al 100 % (progreso 1f). En el primero del capítulo i+1, dibuja `currentBiome` al
     * 100 % (progreso 0f). Para que no haya salto, **ese bioma tiene que ser el mismo**.
     */
    @Test
    fun laFronteraEntreCapitulosNoDaSalto() {
        for (i in 0 until LevelMap.chapters.size - 1) {
            val lastOfChapter = LevelMap.chapterStart[i + 1] - 1
            val firstOfNext = LevelMap.chapterStart[i + 1]

            // Lo que se ve al 100 % en el último nivel (el invasor, ya completo)…
            val shownAtEnd = BiomeRegistry.nextBiome(lastOfChapter)
            // …es lo que se ve al 100 % en el primero del capítulo siguiente (ya como bioma propio).
            val shownAtStart = BiomeRegistry.currentBiome(firstOfNext)

            assertEquals(
                "Frontera $lastOfChapter → $firstOfNext: el bioma dibujado al 100 % cambia de golpe",
                shownAtEnd, shownAtStart
            )
            assertEquals(1f, BiomeRegistry.transitionProgress(lastOfChapter), 0.0001f)
            assertEquals(0f, BiomeRegistry.transitionProgress(firstOfNext), 0.0001f)
        }
    }

    /** El progreso crece de forma monótona dentro de cada capítulo: nunca retrocede ni salta. */
    @Test
    fun elProgresoCreceMonotonoDentroDelCapitulo() {
        for (i in 0 until LevelMap.chapters.size - 1) {
            val start = LevelMap.chapterStart[i]
            val end = LevelMap.chapterStart[i + 1] - 1
            var previous = -1f
            for (level in start..end) {
                val p = BiomeRegistry.transitionProgress(level)
                assertTrue("El progreso retrocede en el nivel $level", p > previous)
                assertTrue("El progreso se sale de 0..1 en el nivel $level", p in 0f..1f)
                previous = p
            }
        }
    }

    /** El ÚLTIMO capítulo no tiene destino: se queda quieto en su bioma, sin morph. */
    @Test
    fun elUltimoCapituloNoSeTransforma() {
        val lastStart = LevelMap.chapterStart.last()
        for (level in lastStart..LevelMap.totalLevels) {
            assertEquals(
                "El último capítulo no tiene bioma siguiente: no debe haber morph (nivel $level)",
                0f, BiomeRegistry.transitionProgress(level), 0.0001f
            )
        }
    }

    /** Niveles de intro (antes del primer capítulo): sin bioma y sin morph. */
    @Test
    fun losNivelesDeIntroNoTienenMorph() {
        for (level in 1 until LevelMap.chapterStart.first()) {
            assertEquals(BiomeType.NONE, BiomeRegistry.currentBiome(level))
            assertEquals(0f, BiomeRegistry.transitionProgress(level), 0.0001f)
        }
    }
}
