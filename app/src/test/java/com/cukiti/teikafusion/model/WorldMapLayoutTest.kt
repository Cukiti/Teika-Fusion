package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class WorldMapLayoutTest {

    private val allNodes = LevelMap.chapters.indices.flatMap { WorldMapLayout.nodes(it) }

    @Test
    fun `cada nivel jugable aparece una sola vez y ninguna leccion`() {
        val levels = allNodes.map { it.level }
        assertEquals(levels.size, levels.toSet().size)
        assertEquals(((LevelMap.introLevels + 1)..LevelMap.totalLevels).toList(), levels.sorted())
    }

    @Test
    fun `los nodos suben dentro de su tramo y no se salen por los lados`() {
        LevelMap.chapters.indices.forEach { c ->
            val nodes = WorldMapLayout.nodes(c)
            nodes.zipWithNext().forEach { (a, b) -> assertTrue("capítulo $c", b.fy < a.fy) }
            nodes.forEach {
                assertTrue(it.fx in 0.2f..0.8f)
                assertTrue(it.fy in 0.05f..0.95f)
            }
        }
    }

    /**
     * Dos nodos seguidos no pueden pisarse: un nodo mide ~48 dp en un móvil de ~411 dp (0,117 anchos).
     * Se mide en anchos de pantalla, con la proporción real del tramo (arte o provisional).
     */
    @Test
    fun `dos niveles seguidos quedan separados en cualquier tramo`() {
        // Alto de cada fondo pintado (res/drawable-nodpi/mapa_NN_*.webp, 1024 px de ancho). Si se
        // regenera un fondo más bajo, este test dice si sus niveles siguen cabiendo sin pisarse.
        val artHeights = listOf(3072, 4480, 3840, 3840, 4480, 4312, 4312, 4312, 4312, 3672, 4312, 4312, 4312, 4312)
        assertEquals(LevelMap.chapters.size, artHeights.size)
        LevelMap.chapters.indices.forEach { c ->
            val withArt = WorldMapLayout.stripAspect(c, artHeights[c] / 1024f)
            val placeholder = WorldMapLayout.stripAspect(c, null)
            for (aspect in listOf(withArt, placeholder)) WorldMapLayout.nodes(c).zipWithNext().forEach { (a, b) ->
                val d = hypot((b.fx - a.fx).toDouble(), ((b.fy - a.fy) * aspect).toDouble())
                assertTrue("capítulo $c: ${a.level}→${b.level} a $d", d > 0.15)
            }
        }
    }

    @Test
    fun `sin arte el tramo provisional tambien deja sitio`() {
        LevelMap.chapters.indices.forEach { c ->
            val aspect = WorldMapLayout.stripAspect(c, null)
            WorldMapLayout.nodes(c).zipWithNext().forEach { (a, b) ->
                val d = hypot((b.fx - a.fx).toDouble(), ((b.fy - a.fy) * aspect).toDouble())
                assertTrue("capítulo $c: ${a.level}→${b.level} a $d", d > 0.15)
            }
        }
    }

    @Test
    fun `el mapa se abre en el nivel elegido o en la frontera`() {
        val first = LevelMap.introLevels + 1
        assertEquals(first + 3, WorldMapLayout.focusLevel(first + 3, first + 10))
        // Elegido más allá de la frontera, o una lección → la frontera.
        assertEquals(first + 10, WorldMapLayout.focusLevel(first + 20, first + 10))
        assertEquals(first + 10, WorldMapLayout.focusLevel(2, first + 10))
        // Juego terminado → el último nivel.
        assertEquals(LevelMap.totalLevels, WorldMapLayout.focusLevel(0, LevelMap.totalLevels + 1))
    }

    @Test
    fun `el deslizador va en el orden del mapa y cada punto devuelve su capitulo`() {
        val last = LevelMap.chapters.size - 1
        assertEquals(0, WorldMapLayout.sliderChapter(1f))       // abajo: el primero
        assertEquals(last, WorldMapLayout.sliderChapter(0f))    // arriba: el último
        // El dedo fuera de la columna no se sale del mapa.
        assertEquals(0, WorldMapLayout.sliderChapter(1.4f))
        assertEquals(last, WorldMapLayout.sliderChapter(-0.3f))
        LevelMap.chapters.indices.forEach { c ->
            assertEquals(c, WorldMapLayout.sliderChapter(WorldMapLayout.sliderFraction(c)))
        }
    }

    @Test
    fun `el capitulo visto es el que ocupa el centro de la pantalla`() {
        val strip = { _: Int -> 1000f }
        // Al pie del mapa, con 800 px de pantalla: el centro cae en el primer tramo.
        assertEquals(0, WorldMapLayout.chapterAtCenter(0, 0, 800f, strip))
        // 700 px dentro del primero: el centro (700 + 400) ya está en el segundo.
        assertEquals(1, WorldMapLayout.chapterAtCenter(0, 700, 800f, strip))
        assertEquals(5, WorldMapLayout.chapterAtCenter(5, 100, 800f, strip))
        // Por encima del último capítulo (El Abismo) sigue contando como el último.
        val last = LevelMap.chapters.size - 1
        assertEquals(last, WorldMapLayout.chapterAtCenter(last, 5000, 800f, strip))
        assertEquals(last, WorldMapLayout.chapterAtCenter(last + 1, 0, 800f, strip))
    }
}
