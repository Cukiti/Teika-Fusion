package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Contrato del director que evita que un capítulo vuelva a ser una secuencia de niveles gemelos. */
class LevelDirectorTest {

    @Test
    fun `cada capitulo abre con descubrimiento y cierra con climax`() {
        LevelMap.chapters.indices.forEach { chapter ->
            val first = LevelMap.chapterStart[chapter]
            val last = LevelMap.chapterLastLevel(chapter)
            assertEquals(LevelChallengeKind.DISCOVERY, LevelDirector.identityFor(first).kind)
            assertEquals(LevelChallengeKind.CLIMAX, LevelDirector.identityFor(last).kind)
        }
    }

    @Test
    fun `cada capitulo recorre las seis identidades`() {
        LevelMap.chapters.indices.forEach { chapter ->
            val kinds = (LevelMap.chapterStart[chapter]..LevelMap.chapterLastLevel(chapter))
                .map { LevelDirector.identityFor(it).kind }
                .toSet()
            assertEquals(
                "capítulo ${LevelMap.chapters[chapter].biome}: $kinds",
                LevelChallengeKind.entries.toSet(),
                kinds
            )
        }
    }

    @Test
    fun `todos los niveles tienen identidad determinista legible`() {
        for (level in 1..LevelMap.totalLevels) {
            val first = LevelDirector.identityFor(level)
            val second = LevelDirector.identityFor(level)
            assertEquals("nivel $level cambia entre lecturas", first, second)
            assertTrue("nivel $level sin título", first.title.isNotBlank())
            assertTrue("nivel $level sin explicación", first.brief.isNotBlank())
            assertEquals(level, first.level)
        }
    }

    @Test
    fun `los titulos no se repiten dentro de un capitulo`() {
        LevelMap.chapters.indices.forEach { chapter ->
            val range = LevelMap.chapterStart[chapter]..LevelMap.chapterLastLevel(chapter)
            val titles = range.map { LevelDirector.identityFor(it).title }
            assertEquals(
                "títulos repetidos en ${LevelMap.chapters[chapter].biome}",
                titles.size,
                titles.distinct().size
            )
        }
    }

    /**
     * **P-84.** El tramo central de un capítulo era `PATRÓN, IMPULSO, PRECISIÓN, PRESIÓN` repetido
     * tal cual, así que el Volcán daba cinco vueltas idénticas y el jugador se lo aprendía. Cada
     * oleada usa ahora su propia permutación: dos oleadas seguidas no pueden abrir igual.
     */
    @Test
    fun `dos oleadas seguidas no repiten el orden del ciclo`() {
        LevelMap.chapters.indices.forEach { chapter ->
            val start = LevelMap.chapterStart[chapter]
            val size = LevelMap.chapters[chapter].levelCount
            // El tramo central: fuera el DESCUBRIMIENTO de apertura y el CLÍMAX de cierre.
            val middle = (start + 1)..(start + size - 2)
            val waves = middle.map { LevelDirector.identityFor(it).kind }.chunked(4)
                .filter { it.size == 4 }
            assertTrue("capítulo $chapter sin oleadas completas", waves.size >= 2)
            waves.zipWithNext().forEachIndexed { i, (a, b) ->
                assertTrue(
                    "capítulo $chapter: las oleadas ${i + 1} y ${i + 2} abren igual ($a)",
                    a != b
                )
            }
        }
    }

    /**
     * **P-84, el invariante de balance.** Una permutación reparte los cuatro retos exactamente una
     * vez, así que cada bloque de cuatro niveles sigue trayendo la misma mezcla de densidades y
     * sesgos de bandeja que antes. Cambia el ORDEN, no la dieta — y es lo que mantiene honesto el
     * número romano del título («Precisión II» = la 2.ª Precisión del capítulo).
     */
    @Test
    fun `cada oleada trae los cuatro retos centrales una sola vez`() {
        val central = setOf(
            LevelChallengeKind.PATTERN,
            LevelChallengeKind.MOMENTUM,
            LevelChallengeKind.PRECISION,
            LevelChallengeKind.PRESSURE,
        )
        LevelMap.chapters.indices.forEach { chapter ->
            val start = LevelMap.chapterStart[chapter]
            val size = LevelMap.chapters[chapter].levelCount
            val middle = (start + 1)..(start + size - 2)
            middle.map { LevelDirector.identityFor(it).kind }
                .chunked(4)
                .filter { it.size == 4 }
                .forEachIndexed { i, wave ->
                    assertEquals(
                        "capítulo $chapter, oleada ${i + 1}: $wave",
                        central,
                        wave.toSet()
                    )
                }
        }
    }

    /**
     * **P-83.** El panel de apertura pinta el nombre A SECAS, debajo de una línea que ya dice el
     * bioma: si el nombre lo repitiera, el panel diría dos veces lo mismo en dos renglones seguidos.
     */
    @Test
    fun `el nombre del nivel no repite el bioma y el titulo si lo lleva`() {
        for (level in 1..LevelMap.totalLevels) {
            val identity = LevelDirector.identityFor(level)
            assertTrue("nivel $level sin nombre", identity.name.isNotBlank())
            val biome = identity.biome
            if (biome == BiomeType.NONE) continue
            assertTrue(
                "nivel $level: el nombre «${identity.name}» ya trae el bioma",
                !identity.name.contains(biome.displayName)
            )
            assertEquals(
                "nivel $level: el título dejó de ser «nombre · bioma»",
                "${identity.name} · ${biome.displayName}",
                identity.title
            )
        }
    }

    @Test
    fun `cada bioma tiene al menos cinco configuraciones jugables distintas`() {
        LevelMap.chapters.indices.forEach { chapter ->
            val biome = LevelMap.chapters[chapter].biome
            val signatures = (LevelMap.chapterStart[chapter]..LevelMap.chapterLastLevel(chapter))
                .map { mechanicalSignature(it, biome) }
                .toSet()
            assertTrue(
                "$biome solo tiene ${signatures.size} configuraciones: $signatures",
                signatures.size >= 5
            )
        }
    }

    /**
     * Reescrita el 2026-08-30: antes comprobaba que `idleThreshold` topara todo en 2. Ese suelo
     * desapareció con el escalado por nivel —el umbral es ahora el que declara [BiomeRhythm] tal
     * cual—, así que el invariante que de verdad importa es este: **ningún bioma golpea en el mismo
     * turno en que empieza a contar**. Lo que se protegía era el turno de reacción, no el número 2.
     */
    @Test
    fun `ninguna amenaza cae sin turno de respuesta`() {
        BiomeType.entries.filter { it != BiomeType.NONE }.forEach { biome ->
            if (BiomeRhythm.idleBase(biome) == 0 || biome == BiomeType.FEAR) return@forEach
            assertTrue(
                "$biome golpea en el turno ${BiomeRhythm.executeTurn(biome)} sin dar margen",
                BiomeRhythm.executeTurn(biome) >= 2
            )
        }
        for (level in (LevelMap.introLevels + 1)..LevelMap.totalLevels) {
            assertTrue("volcán inicial $level", LevelDirector.volcanoFirstThreshold(level) >= 2)
        }
    }

    /**
     * **La única excepción al suelo de 2**, y por eso tiene su propia prueba en vez de un hueco en
     * la de arriba: con la lava ya asomada baja en CADA turno seco (2026-08-30).
     *
     * Se lo puede permitir porque su castigo no es un golpe único e irreversible como el del resto
     * de biomas —es una fila de lava, y romper una línea la hace retroceder—, así que el turno de
     * reacción que el suelo de 2 protege en los demás lo da aquí la propia mecánica.
     *
     * Lo que sí sigue vigilado es que **nunca baje de 1**: con 0 la lava avanzaría varias filas en
     * el mismo turno.
     */
    @Test
    fun `la lava asomada es la unica amenaza que puede caer cada turno`() {
        val volcanoChapter = LevelMap.chapters.indexOfFirst { it.biome == BiomeType.VOLCANO }
        assertTrue("el capítulo del Volcán desapareció del mapa", volcanoChapter >= 0)
        val levels = LevelMap.chapterStart[volcanoChapter]..LevelMap.chapterLastLevel(volcanoChapter)

        val cadences = levels.map { LevelDirector.volcanoSurfacedThreshold(it) }
        // P-353: la lava baja una vez por ciclo, rompas o no — el ciclo del capítulo (BiomeCycle), que desde
        // el 2026-09-28 es 4, o 3 en el último tercio. Nunca menos de 3: calma 0 + los dos avisos.
        cadences.forEachIndexed { i, surfaced ->
            assertEquals("nivel ${levels.first + i}", BiomeCycle.strikeAt(levels.first + i), surfaced)
            assertTrue("nivel ${levels.first + i}: cadencia $surfaced por debajo de 3", surfaced >= 3)
        }
        // Y el capítulo tiene que APRETAR: el final no puede ser más blando que el principio.
        assertTrue("el volcán no acelera a lo largo del capítulo", cadences.last() <= cadences.first())
    }

    @Test
    fun `los conteos proporcionales nunca salen del conjunto disponible`() {
        for (level in 1..LevelMap.totalLevels) {
            for (total in 0..64) {
                assertTrue(LevelDirector.starVictimCount(total, level) in 0..total)
            }
        }
    }

    @Test
    fun `los catorce biomas estan conectados al tick real`() {
        val viewModel = source("viewmodel/GameViewModel.kt")
        val calls = listOf(
            "sleepDuration", "fearCalmTurns", "gardenRescueLines", "gardenAbductionTurns",
            "stormCoilCount", "volcanoFirstThreshold", "ninjaCutThreshold", "ninjaRepairLines",
            "glacierFrozenTurns", "mirrorThreshold", "gluttonOpenThreshold",
            "toxicSplashAfter", "toxicSplashCount", "starVictimCount", "swordThreshold",
            "meteorThreshold"
        )
        val missing = calls.filterNot { "LevelDirector.$it" in viewModel }
        assertTrue("Ajustes del director sin consumidor en GameViewModel: $missing", missing.isEmpty())
    }

    @Test
    fun `layout bandeja y siembra consumen la identidad mecanica`() {
        val integrations = mapOf(
            "model/LevelLayout.kt" to "LevelDirector.identityFor(level).layoutDensityDelta",
            "model/GameState.kt" to "LevelDirector.crystalSeedChance(level)",
            "model/Piece.kt" to "LevelDirector.crystalPieceChance(level)",
            // P-83: el nombre del nivel volvió a tener lector. `title` y `brief` llevaban meses
            // calculándose para nada; esta fila impide que el nombre vuelva a quedarse mudo.
            "ui/LevelObjectiveOverlay.kt" to "LevelDirector.identityFor(level).name"
        )
        val missing = integrations.filterNot { (path, token) -> token in source(path) }
        assertTrue("Integraciones del director ausentes: $missing", missing.isEmpty())
    }

    private fun mechanicalSignature(level: Int, biome: BiomeType): String {
        val identity = LevelDirector.identityFor(level)
        val common = "${identity.layoutDensityDelta}"
        val biomeTuning = when (biome) {
            BiomeType.CRYSTAL ->
                "${LevelDirector.crystalSeedChance(level)}|${LevelDirector.crystalPieceChance(level)}|" +
                    LevelDirector.crystalDoubleArmorChance(level)
            BiomeType.SLEEP ->
                "${LevelDirector.idleThreshold(3, level)}|${LevelDirector.sleepDuration(level)}"
            BiomeType.FEAR ->
                "${LevelDirector.idleThreshold(2, level)}|${LevelDirector.fearCalmTurns(level)}"
            BiomeType.GARDEN ->
                "${LevelDirector.idleThreshold(3, level)}|${LevelDirector.gardenRescueLines(level)}|" +
                    LevelDirector.gardenAbductionTurns(level)
            BiomeType.STORM -> LevelDirector.stormCoilCount(level).toString()
            BiomeType.VOLCANO ->
                "${LevelDirector.volcanoFirstThreshold(level)}|${LevelDirector.volcanoSurfacedThreshold(level)}"
            BiomeType.NINJA ->
                "${LevelDirector.ninjaCutThreshold(level)}|${LevelDirector.ninjaRepairLines(level)}"
            BiomeType.GLACIER ->
                "${LevelDirector.idleThreshold(3, level)}|${LevelDirector.glacierFrozenTurns(level)}"
            BiomeType.GHOST ->
                "${LevelDirector.mirrorWarnThreshold(level)}|${LevelDirector.mirrorThreshold(level)}"
            BiomeType.GLUTTON ->
                "${LevelDirector.gluttonOpenThreshold(level)}|${LevelDirector.gluttonBiteThreshold(level)}|" +
                    LevelDirector.gluttonRecoverTurns(level)
            BiomeType.TOXIC ->
                "${LevelDirector.toxicSeedChance(level)}|${LevelDirector.toxicSplashAfter(level)}|" +
                    "${LevelDirector.toxicSplashCount(level)}"
            BiomeType.STAR ->
                "${LevelDirector.starIdleThreshold(level)}|${LevelDirector.starMoonFraction(level)}"
            BiomeType.KING -> "${LevelDirector.swordWarnThreshold(level)}|${LevelDirector.swordThreshold(level)}"
            BiomeType.GALACTIC ->
                "${LevelDirector.meteorWarnThreshold(level)}|${LevelDirector.meteorThreshold(level)}"
            BiomeType.NONE -> "intro"
        }
        return "$common|$biomeTuning"
    }

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, MAIN_SRC).isDirectory) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    private fun source(relative: String): String = File(repoRoot, MAIN_SRC + relative).readText()

    private companion object {
        const val MAIN_SRC = "app/src/main/java/com/cukiti/teikafusion/"
    }
}
