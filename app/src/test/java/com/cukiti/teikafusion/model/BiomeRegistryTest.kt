package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de MECÁNICAS de [BiomeRegistry] (la hermana de [BiomeTransitionTest], que fija el morph
 * VISUAL): en cada nivel corren como mucho DOS biomas — el del capítulo actual como primario y el
 * del capítulo anterior como secundario — y nunca tres. `GameState.startLevel` deriva
 * `biomePrimary`/`biomeSecondary` directamente de aquí, así que este test protege lo que el
 * ViewModel da por hecho.
 *
 * Además fija el INVENTARIO CANÓNICO del juego (ver MEJORAS.md): 14 capítulos, cada bioma
 * exactamente una vez, y el total de niveles derivado de [LevelMap] — si un cambio en los
 * capítulos altera estas cuentas, debe ser una decisión consciente, no un accidente.
 */
class BiomeRegistryTest {

    /** Nunca más de 2 mecánicas, sin NONE y sin duplicados — en TODOS los niveles del juego. */
    @Test
    fun nuncaTresMecanicasNiNone() {
        for (level in 1..LevelMap.totalLevels) {
            val active = BiomeRegistry.activeMechanics(level)
            assertTrue("Nivel $level: ${active.size} mecánicas (máx. 2)", active.size <= 2)
            assertTrue("Nivel $level: NONE no es una mecánica", BiomeType.NONE !in active)
            assertEquals("Nivel $level: mecánicas duplicadas", active.distinct(), active)
        }
    }

    /** El primario es SIEMPRE el bioma del capítulo del nivel (lo que hereda GameState.biomePrimary). */
    @Test
    fun elPrimarioEsElBiomaDelCapitulo() {
        LevelMap.chapters.forEachIndexed { i, chapter ->
            for (level in LevelMap.chapterStart[i]..LevelMap.chapterLastLevel(i)) {
                assertEquals(
                    "Nivel $level: el primario debe ser el bioma de su capítulo",
                    chapter.biome, BiomeRegistry.currentBiome(level)
                )
                assertEquals(
                    "Nivel $level: la primera mecánica activa debe ser el primario",
                    chapter.biome, BiomeRegistry.activeMechanics(level).first()
                )
            }
        }
    }

    /** El secundario es el bioma del capítulo ANTERIOR (NONE en el primer capítulo). */
    @Test
    fun elSecundarioEsElCapituloAnterior() {
        LevelMap.chapters.forEachIndexed { i, _ ->
            val expected = if (i == 0) BiomeType.NONE else LevelMap.chapters[i - 1].biome
            for (level in LevelMap.chapterStart[i]..LevelMap.chapterLastLevel(i)) {
                assertEquals(
                    "Nivel $level: el secundario debe ser el bioma del capítulo anterior",
                    expected, BiomeRegistry.previousBiome(level)
                )
            }
        }
    }

    /** Los niveles de intro (1..introLevels) no tienen bioma ni mecánica alguna. */
    @Test
    fun losNivelesDeIntroNoTienenMecanicas() {
        for (level in 1..LevelMap.introLevels) {
            assertEquals(BiomeType.NONE, BiomeRegistry.currentBiome(level))
            assertTrue(
                "Nivel de intro $level: sin mecánicas",
                BiomeRegistry.activeMechanics(level).isEmpty()
            )
        }
    }

    /** Inventario canónico: 14 capítulos, cada bioma exactamente una vez, 293 niveles totales. */
    @Test
    fun elInventarioCanonicoSeMantiene() {
        assertEquals("Capítulos", 14, LevelMap.chapters.size)
        val biomes = LevelMap.chapters.map { it.biome }
        assertEquals("Cada bioma aparece exactamente una vez", biomes.distinct(), biomes)
        assertTrue("NONE no es un capítulo", BiomeType.NONE !in biomes)
        // 14 biomas + NONE = 15 valores del enum: si nace un bioma nuevo, este test pide decidir
        // conscientemente su capítulo (o su ausencia) en LevelMap.
        assertEquals("Biomas del enum sin capítulo", BiomeType.entries.size - 1, biomes.size)
        assertEquals("Total de niveles derivado de LevelMap", 293, LevelMap.totalLevels)
    }

    /** Todo hint describe una regla real: ningún bioma jugable puede quedarse sin pista. */
    @Test
    fun todoBiomaJugableTieneHint() {
        for (biome in BiomeType.entries.filter { it != BiomeType.NONE }) {
            assertTrue("El bioma $biome no tiene mechanicHint", mechanicHint(biome).isNotBlank())
        }
        assertTrue("NONE no lleva hint", mechanicHint(BiomeType.NONE).isEmpty())
    }
}
