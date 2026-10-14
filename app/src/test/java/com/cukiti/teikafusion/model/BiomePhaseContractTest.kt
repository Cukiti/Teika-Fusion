package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **EL CONTRATO DE LAS TRES FASES** (P-68, auditoría de eventos de bioma 2026-08-30).
 *
 * ## Por qué existe
 *
 * La auditoría encontró que un bioma se disfruta si tiene **telegrafía → impacto → consecuencia**, y
 * que los que se sentían muertos se saltaban una o dos. El problema no era que faltara talento
 * visual: era que **nada lo comprobaba**. `CrystalCrack` y `DrowsyOnset` llevaban meses emitiéndose
 * sin un solo composable que los dibujara, y el Volcán se comía una fila sin avisar, y el árbol
 * compilaba y los tests pasaban.
 *
 * Aquí se fija lo que antes solo estaba en la cabeza de quien miraba: cada bioma **declara** sus dos
 * señales en [BiomeRhythm.phasesFor], y estas pruebas comprueban contra el CÓDIGO FUENTE que existen
 * de verdad y que la UI las dibuja. Un bioma nuevo sin telegrafía suspende, en vez de descubrirse
 * jugando tres meses después.
 *
 * ## Y el reloj
 *
 * La otra mitad: que se respete la **regla de la casa** —dos turnos de aviso y al tercero ejecuta—
 * y que el reloj **no vuelva a escalar por nivel**. Ese escalado hacía que el mismo bioma castigara
 * en el turno 5 o en el 2 según dónde estuvieras dentro del capítulo, así que la regla no se podía
 * aprender jugando.
 */
class BiomePhaseContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, MAIN_SRC + "model/GameEvent.kt").isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    private fun sourcesIn(relativeDir: String): String =
        File(repoRoot, relativeDir).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

    private val uiSource: String by lazy { sourcesIn(MAIN_SRC + "ui") }
    private val eventSource: String by lazy { File(repoRoot, MAIN_SRC + "model/GameEvent.kt").readText() }

    /** Los 14 biomas reales (todos menos [BiomeType.NONE]). */
    private val realBiomes = BiomeType.entries.filter { it != BiomeType.NONE }

    // ── FASES ────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `los catorce biomas declaran telegrafia e impacto`() {
        val missing = realBiomes.filter { BiomeRhythm.phasesFor(it) == null }
        assertTrue(
            "Biomas sin fases declaradas (todo bioma AVISA antes de castigar): $missing",
            missing.isEmpty()
        )
    }

    /**
     * Cada nombre declarado tiene que EXISTIR: o es un `GameEvent` de verdad, o es un composable
     * dirigido por estado. Un nombre inventado haría pasar la prueba sin que el jugador viera nada.
     */
    @Test
    fun `cada senal declarada existe en el codigo`() {
        val unresolved = mutableListOf<String>()
        realBiomes.forEach { biome ->
            val phases = BiomeRhythm.phasesFor(biome) ?: return@forEach
            listOf("telegrafía" to phases.telegraph, "impacto" to phases.impact).forEach { (role, name) ->
                val isEvent = Regex("""^ {4}(?:data class|data object|class) $name\b""", RegexOption.MULTILINE)
                    .containsMatchIn(eventSource)
                val isComposable = Regex("""fun $name\s*\(""").containsMatchIn(uiSource)
                if (!isEvent && !isComposable) unresolved += "$biome/$role → $name"
            }
        }
        assertTrue(
            "Señales declaradas que no existen ni como GameEvent ni como composable: $unresolved",
            unresolved.isEmpty()
        )
    }

    /**
     * Y la UI tiene que DIBUJARLAS. Un evento declarado como impacto que nadie pinta es exactamente
     * el fallo que esta auditoría encontró en `CrystalCrack` y `DrowsyOnset`.
     */
    @Test
    fun `la UI dibuja las dos senales de cada bioma`() {
        val undrawn = mutableListOf<String>()
        realBiomes.forEach { biome ->
            val phases = BiomeRhythm.phasesFor(biome) ?: return@forEach
            listOf("telegrafía" to phases.telegraph, "impacto" to phases.impact).forEach { (role, name) ->
                if (!Regex("""\b$name\b""").containsMatchIn(uiSource)) undrawn += "$biome/$role → $name"
            }
        }
        assertTrue("Señales que la UI no menciona (nadie las dibuja): $undrawn", undrawn.isEmpty())
    }

    /** Telegrafía e impacto no pueden ser la misma cosa: entonces no hay aviso, hay solo castigo. */
    @Test
    fun `la telegrafia no es el propio castigo`() {
        val collapsed = realBiomes.filter { biome ->
            val p = BiomeRhythm.phasesFor(biome) ?: return@filter false
            p.telegraph == p.impact
        }
        assertTrue("Biomas cuyo aviso ES el castigo (o sea: sin aviso): $collapsed", collapsed.isEmpty())
    }

    // ── EL RELOJ (regla de la casa) ───────────────────────────────────────────────────────────────

    /**
     * **LA REGLA DE LA CASA**: dos turnos de aviso y al tercero ejecuta. **Sin excepciones.**
     *
     * Es lo que hace el sistema aprendible: el jugador lo entiende UNA vez y le sirve para los
     * catorce capítulos.
     *
     * Hasta el 2026-09-05 esta prueba llevaba dentro un mapa de dos excepciones —FEAR ejecutaba al
     * 2.º y GARDEN al 4.º— cada una con su justificación escrita. El usuario las retiró: quiere la
     * mecánica de dificultad a la **tercera colocación en todos los biomas temáticos**, y un test
     * que admite excepciones es un test que invita a la siguiente. Ahora el mapa no existe, así que
     * cualquier compás propio suspende y hay que venir aquí a cambiarlo a propósito.
     */
    @Test
    fun `dos turnos de aviso y al tercero ejecuta`() {
        realBiomes.forEach { biome ->
            if (BiomeRhythm.idleBase(biome) == 0 || biome == BiomeType.FEAR) return@forEach   // CRYSTAL y STORM no tienen reloj
            assertEquals(
                "$biome ejecuta en el turno ${BiomeRhythm.executeTurn(biome)}: la regla de la casa " +
                    "es al ${BiomeRhythm.EJECUTA}.º en TODOS, sin excepciones (2026-09-05)",
                BiomeRhythm.EJECUTA, BiomeRhythm.executeTurn(biome)
            )
        }
    }

    /**
     * **SOLO ROMPER LÍNEAS CALMA EL BIOMA** (decisión del usuario, 2026-09-05).
     *
     * El umbral de arriba no vale nada si cualquier cosa lo reinicia. Los Teikas son cuerpos físicos
     * que se funden SOLOS entre turnos, y mientras esas fusiones espontáneas contaran como actividad
     * regalaban el turno que el reloj necesitaba: había capítulos cuya mecánica no se veía nunca.
     *
     * `activityWeightSinceTick` es de donde sale el `boardActive` que leen todas las secciones de
     * `applyBiomeTick`, así que esta prueba vigila que nadie vuelva a sumarle una fusión. Se lee el
     * fuente porque la regla vive en un `var` privado del ViewModel: no hay forma de preguntárselo.
     */
    @Test
    fun `la fusion no reinicia el reloj de ningun bioma`() {
        val vm = File(repoRoot, MAIN_SRC + "viewmodel/GameViewModel.kt").readText()
        val sumas = Regex("""activityWeightSinceTick\s*\+=\s*(\S+)""")
            .findAll(vm).map { it.groupValues[1] }.toList()
        assertTrue(
            "Alguien volvió a sumar actividad al reloj del bioma: $sumas. Solo pueden sumar ROTURAS " +
                "de línea (el pase normal, la descarga del circuito) y la fila que la Tormenta " +
                "convierte en circuito — nunca una fusión, que ocurre sola.",
            sumas.isNotEmpty() && sumas.none { it == "1" }
        )
    }

    /** Todo bioma con reloj avisa ANTES de golpear: cero turnos de aviso no es una mecánica justa. */
    @Test
    fun `nadie castiga sin avisar`() {
        realBiomes.forEach { biome ->
            if (BiomeRhythm.idleBase(biome) == 0 || biome == BiomeType.FEAR) return@forEach
            assertTrue(
                "$biome castiga sin un solo turno de aviso",
                BiomeRhythm.warningTurns(biome) >= 1
            )
        }
    }

    /**
     * **El reloj NO escala por nivel** (decisión del usuario, 2026-08-30).
     *
     * Antes el acto del capítulo (±1) y el tipo de nivel (±1) movían el umbral, y el mismo bioma
     * castigaba en el turno 5 o en el 2 según dónde estuvieras: imposible aprender la regla de la
     * casa. Si alguien vuelve a meter una curva aquí, esta prueba lo dice.
     */
    @Test
    fun `el reloj vale lo mismo en todos los niveles del capitulo`() {
        LevelMap.chapters.forEachIndexed { ci, ch ->
            val base = BiomeRhythm.idleBase(ch.biome)
            if (base == 0) return@forEachIndexed
            val levels = LevelMap.chapterStart[ci]..LevelMap.chapterLastLevel(ci)
            val distintos = levels.map { LevelDirector.idleThreshold(base, it) }.toSet()
            assertEquals(
                "${ch.biome}: el umbral cambia por nivel dentro del capítulo ($distintos)",
                setOf(base), distintos
            )
        }
    }

    /** Y las DURACIONES del castigo tampoco escalan. */
    @Test
    fun `las duraciones del castigo son fijas`() {
        LevelMap.chapters.forEachIndexed { ci, ch ->
            val levels = (LevelMap.chapterStart[ci]..LevelMap.chapterLastLevel(ci)).toList()
            val dur: ((Int) -> Int)? = when (ch.biome) {
                BiomeType.SLEEP -> LevelDirector::sleepDuration
                BiomeType.GLACIER -> LevelDirector::glacierFrozenTurns
                BiomeType.GLUTTON -> LevelDirector::gluttonRecoverTurns
                else -> null
            }
            if (dur == null) return@forEachIndexed
            assertEquals(
                "${ch.biome}: la duración del castigo cambia por nivel",
                1, levels.map(dur).toSet().size
            )
        }
    }

    /**
     * Las tres duraciones que el usuario fijó a mano el 2026-08-30. Están aquí y no solo en
     * [BiomeRhythm] porque son decisiones de diseño, no cifras derivadas: si alguien las mueve, que
     * sea a sabiendas.
     */
    @Test
    fun `las duraciones acordadas se respetan`() {
        // (2026-09-27, decisión del usuario) Todo castigo dura AL MENOS dos turnos: con uno no se sentía.
        assertEquals(2, BiomeRhythm.punishmentTurns(BiomeType.SLEEP))     // turnos dormida (P-312: 2 → 1; 2026-09-27: 1 → 2)
        assertEquals(2, BiomeRhythm.punishmentTurns(BiomeType.GLACIER))   // turnos congelado (P-254: 2 → 1; 1 → 2; P-352: 2 → 3; P-359: 3 → 2)
        assertEquals(5, KingPiece.LANE_TURNS)                              // calle bloqueada por la pieza del Rey (P-352: 2 → 3; P-360: 3 → 5)
        // (2026-09-23) El agujero negro de 3 turnos se retiró con la BRECHA EN EL CASCO (P-248,
        // decisión del usuario): la despresurización es instantánea, así que no hay duración.
        assertEquals(0, BiomeRhythm.punishmentTurns(BiomeType.GALACTIC))
    }

    /**
     * La escalera del Volcán: dos turnos viendo arder la cresta, al tercero se come una fila, y a
     * partir de ahí **cada turno seco**. Es el único umbral de 1 del juego.
     */
    @Test
    fun `la escalera del volcan acelera`() {
        // P-353 (2026-09-27, decisión del usuario): la lava baja una vez por CICLO (5, o 4 en el último
        // tercio), también asomada. Ya no baja cada turno seco: ahora no se frena rompiendo cualquier fila.
        for (level in (LevelMap.introLevels + 1)..LevelMap.totalLevels) {
            assertEquals(BiomeCycle.strikeAt(level), LevelDirector.volcanoSurfacedThreshold(level))
            assertEquals(BiomeCycle.strikeAt(level), LevelDirector.volcanoFirstThreshold(level))
        }
    }

    // ── LAS PISTAS NO PUEDEN MENTIR ──────────────────────────────────────────────────────────────

    /**
     * CLAUDE.md lo pide explícitamente: la pista describe la **regla real**. Los números estaban
     * escritos a mano, así que cambiar el reloj dejaba la pista mintiendo sin que nada fallara.
     */
    @Test
    fun `las pistas no llevan el umbral escrito a mano`() {
        val hintSource = File(repoRoot, MAIN_SRC + "model/Biome.kt").readText()
        val block = hintSource.substringAfter("fun mechanicHint").substringBefore("\n}")
        val liars = realBiomes.filter { biome ->
            if (BiomeRhythm.idleBase(biome) == 0) return@filter false   // CRYSTAL / STORM: sin reloj
            val line = block.lines().firstOrNull { it.contains("BiomeType.${biome.name} ") } ?: return@filter false
            // Con reloj y con texto: o interpola el umbral, o no debe nombrar un número de turnos.
            !line.contains("idle(BiomeType.${biome.name})") &&
                Regex("""\b\d+ (colocaciones|turnos)""").containsMatchIn(line)
        }
        assertTrue(
            "Pistas con el umbral ESCRITO A MANO (se desincronizan del reloj en silencio): $liars",
            liars.isEmpty()
        )
    }

    @Test
    fun `toda pista de bioma dice algo`() {
        realBiomes.forEach {
            assertTrue("$it sin pista de mecánica", mechanicHint(it).length > 30)
        }
    }

    /**
     * **LA CADENA Y EL TIPO TIENEN QUE DECIR LO MISMO** (P-176, 2026-09-04).
     *
     * `phasesFor` nombra el impacto con una **cadena** —es el censo, y lo que este test cruza contra
     * el código— y `isImpact` lo reconoce con un **tipo**, que es lo que se usa en caliente para
     * saber si el bioma acaba de golpear. Dos fuentes para el mismo dato, y por un buen motivo: la
     * cadena permite auditar, el tipo sobrevive a R8 (comparar `javaClass.simpleName` no: renombra la
     * clase y la comparación es falsa para siempre en la app publicada — pasó, y costó las ocho
     * cápsulas de bioma durante meses).
     *
     * Dos fuentes sin nadie que las cruce es exactamente cómo empiezan estas cosas, así que aquí se
     * cruzan: para cada bioma, el `is GameEvent.X` de `isImpact` debe nombrar la misma X que declara
     * `phasesFor`.
     */
    @Test
    fun `el tipo que reconoce el impacto es el mismo que declara el censo`() {
        val rhythm = File(repoRoot, MAIN_SRC + "model/BiomeRhythm.kt").readText()
        val body = rhythm.substringAfter("fun isImpact(")
        val declared = Regex("""BiomeType\.(\w+) -> event is GameEvent\.(\w+)""")
            .findAll(body)
            .associate { it.groupValues[1] to it.groupValues[2] }

        assertEquals(
            "isImpact no cubre los catorce biomas reales",
            realBiomes.size, declared.size
        )

        val mismatched = realBiomes.mapNotNull { biome ->
            val fromTable = BiomeRhythm.phasesFor(biome)?.impact
            val fromType = declared[biome.name]
            if (fromTable != null && fromType != null && fromTable != fromType) {
                "${biome.name}: censo dice '$fromTable' y el tipo es '$fromType'"
            } else null
        }
        assertTrue(
            "El censo y el reconocedor del impacto discrepan: $mismatched — si divergen, la cápsula " +
                "de ese bioma deja de salir (o sale con el evento equivocado) sin que nada falle.",
            mismatched.isEmpty()
        )
    }

    private companion object {
        const val MAIN_SRC = "app/src/main/java/com/cukiti/teikafusion/"
    }
}
