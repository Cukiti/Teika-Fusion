package com.cukiti.teikafusion.model

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Identidad jugable de un nivel dentro de su capítulo.
 *
 * No se serializa: se deriva únicamente de [level], por lo que una partida guardada conserva la
 * misma configuración después de actualizar la aplicación y no hace falta migrar el esquema.
 */
enum class LevelChallengeKind {
    DISCOVERY,
    PATTERN,
    MOMENTUM,
    PRECISION,
    PRESSURE,
    CLIMAX
}

data class DirectedLevel(
    val level: Int,
    val biome: BiomeType,
    val localNumber: Int,
    val chapterSize: Int,
    val kind: LevelChallengeKind,
    val wave: Int,
    /**
     * **El nombre del nivel, SIN el bioma** («Precisión II»). Es lo que pinta la ceremonia de
     * apertura (P-83): el panel ya enseña el bioma en su propia línea, así que repetirlo en el
     * nombre sería decir dos veces lo mismo en dos renglones seguidos.
     */
    val name: String,
    /** [name] con el bioma detrás («Precisión II · Volcán»), para donde no haya contexto de bioma. */
    val title: String,
    val brief: String,
    val layoutDensityDelta: Int,
)

/**
 * Director puro de los 293 niveles.
 *
 * Mantiene la pregunta global `ESTRELLAS + MEGA`, pero cambia cómo se presenta el problema: densidad
 * inicial, familia de tamaños de la bandeja, presupuesto compensatorio y parámetros reales de cada
 * bioma. Los cuatro retos centrales se alternan; el primer nivel presenta la mecánica y el último es
 * un clímax inequívoco. Cuando un reto vuelve a aparecer, [wave] lo identifica como una oleada nueva.
 */
object LevelDirector {

    private val middleCycle = listOf(
        LevelChallengeKind.PATTERN,
        LevelChallengeKind.MOMENTUM,
        LevelChallengeKind.PRECISION,
        LevelChallengeKind.PRESSURE
    )

    /**
     * **P-84 · EL CICLO DE CUATRO DEJA DE SER SIEMPRE EL MISMO** (2026-08-30).
     *
     * [middleCycle] se recorría con `%`, así que el tramo central de un capítulo era literalmente
     * `PATRÓN, IMPULSO, PRECISIÓN, PRESIÓN` repetido: el Volcán (22 niveles) daba **cinco vueltas
     * idénticas** al mismo orden y el jugador se lo aprendía hacia el octavo nivel — a partir de ahí
     * sabía qué tablero le tocaba antes de abrirlo.
     *
     * Ahora **cada oleada usa una permutación distinta** de los mismos cuatro retos, elegida de esta
     * tabla por (capítulo, oleada). Las tres propiedades que hacían falta:
     *
     *  - **Determinista.** Mismo nivel → mismo reto, siempre. La identidad del nivel se DERIVA del
     *    número (nunca se serializa), así que esto no migra ni invalida un solo guardado.
     *  - **No toca el balance.** Una permutación contiene los cuatro retos exactamente una vez, así
     *    que cada bloque de cuatro niveles sigue repartiendo la misma mezcla de densidades y sesgos
     *    de bandeja. Cambia el ORDEN, no la dieta.
     *  - **`wave` sigue significando lo mismo.** Como cada oleada trae cada reto una vez, la N-ésima
     *    oleada contiene la N-ésima aparición de cada uno: «Precisión II» sigue siendo la segunda
     *    Precisión del capítulo, y los títulos no se repiten (lo vigila `LevelDirectorTest`).
     *
     * En orden lexicográfico sobre [middleCycle], así que la permutación 0 es el ciclo de siempre —
     * y con capítulo 0 y oleada 0 (el arranque del Cristal) sale exactamente el orden histórico.
     */
    private val cyclePermutations: List<List<LevelChallengeKind>> = permutationsOf(middleCycle)

    private fun permutationsOf(items: List<LevelChallengeKind>): List<List<LevelChallengeKind>> {
        if (items.size <= 1) return listOf(items)
        val out = ArrayList<List<LevelChallengeKind>>()
        items.indices.forEach { i ->
            val rest = items.toMutableList().also { it.removeAt(i) }
            permutationsOf(rest).forEach { tail -> out.add(listOf(items[i]) + tail) }
        }
        return out
    }

    /**
     * Permutación que le toca a la [wave] (0-based) del capítulo [chapterIndex].
     *
     * Los dos pasos (13 por capítulo, 7 por oleada) son **coprimos con las 24 permutaciones**: dentro
     * de un capítulo —seis oleadas como mucho— no se repite ninguna, y dos capítulos vecinos no
     * arrancan por la misma. Sin estado mutable y sin asignar nada por llamada: [kindFor] lo llama
     * desde sitios que la UI recompone.
     */
    private fun cycleFor(chapterIndex: Int, wave: Int): List<LevelChallengeKind> {
        val slot = chapterIndex.coerceAtLeast(0) * 13 + wave.coerceAtLeast(0) * 7
        return cyclePermutations[slot % cyclePermutations.size]
    }

    fun identityFor(level: Int): DirectedLevel {
        val chapterIndex = LevelMap.chapterIndexForLevel(level)
        if (chapterIndex < 0) {
            return DirectedLevel(
                level = level,
                biome = BiomeType.NONE,
                localNumber = level.coerceAtLeast(1),
                chapterSize = LevelMap.introLevels,
                kind = LevelChallengeKind.DISCOVERY,
                wave = 1,
                name = "Primeros pasos",
                title = "Primeros pasos",
                brief = "Aprende a colocar, limpiar y fusionar antes de que empiecen los biomas.",
                layoutDensityDelta = 0,
            )
        }

        val chapter = LevelMap.chapters[chapterIndex]
        val localIndex = LevelMap.localIndex(level)
        val localNumber = localIndex + 1
        val kind = kindFor(level)
        val wave = if (localIndex <= 0) 1 else ((localIndex - 1) / middleCycle.size) + 1
        val tuning = tuningFor(kind)
        val provisional = DirectedLevel(
            level = level,
            biome = chapter.biome,
            localNumber = localNumber,
            chapterSize = chapter.levelCount,
            kind = kind,
            wave = wave,
            name = nameFor(kind, wave),
            title = titleFor(kind, wave, chapter.biome),
            brief = "",
            layoutDensityDelta = tuning.layoutDensityDelta,
        )
        return provisional.copy(brief = briefFor(provisional))
    }

    /**
     * **Umbral de amenaza del bioma. YA NO ESCALA POR NIVEL** (decisión del usuario, 2026-08-30).
     *
     * Devuelve el umbral tal cual lo declara [BiomeRhythm]. Antes se le sumaba el acto del capítulo
     * ([ChapterArc], ±1) y el carácter del nivel (±1), y el mismo bioma acababa castigando en el
     * turno 5 o en el 2 según dónde estuvieras dentro del capítulo: el jugador no podía aprender
     * "dos turnos de aviso y al tercero cae", que es la regla de la casa.
     *
     * La progresión del capítulo vive ahora en el TABLERO (densidad inicial, sesgo de bandeja,
     * objetivo), que es lo que [tuningFor] sigue moviendo.
     *
     * [level] se conserva en la firma a propósito: hay ~15 llamantes y volver a meter una curva de
     * dificultad —si algún día se decide— no debería obligar a tocarlos todos.
     */
    @Suppress("UNUSED_PARAMETER")
    fun idleThreshold(base: Int, level: Int): Int = base.coerceAtLeast(1)

    // ── P-312 · LA FUERZA DEL CASTIGO (2026-09-26, decisión del usuario) ──────────────────────────
    // El bot dejó el Cristal en el 73 % (objetivo 85) y el Tóxico en el 73 % (objetivo 62). Sin tocar
    // CUÁNDO castigan (el reloj es decisión congelada), se cambia CUÁNTO: menos cristal y menos corazas
    // dobles; más veneno y recargas más grandes. La tabla por capítulo (P-309) queda neutra en ellos.
    /** Cristal: fracción que se conserva del cristal de tablero y fichas de cada reto. */
    const val CRYSTAL_AMOUNT_FACTOR = 0.30f   // P-360 (2026-09-28): 0.7 → 0.45 → 0.30 (Cristal 77 %, objetivo 85 %)
    /** Cristal: techo de corazas dobles. */
    const val CRYSTAL_DOUBLE_ARMOR_MAX = 0.0f   // P-360: 0.10 → 0 (el primer capítulo, sin corazas dobles)
    /** Tóxico: multiplicador del veneno inicial. */
    const val TOXIC_SEED_FACTOR = 1.5f   // P-352: 1.25 → 1.5
    // (2026-09-28: TOXIC_REFILL_EXTRA retirado con la recarga del charco.)

    fun crystalSeedChance(level: Int): Float = crystalSeedChanceBase(level) * CRYSTAL_AMOUNT_FACTOR

    private fun crystalSeedChanceBase(level: Int): Float = when (kindFor(level)) {
        LevelChallengeKind.DISCOVERY -> 0.18f
        LevelChallengeKind.PATTERN -> 0.42f
        LevelChallengeKind.MOMENTUM -> 0.26f
        LevelChallengeKind.PRECISION -> 0.34f
        LevelChallengeKind.PRESSURE -> 0.48f
        LevelChallengeKind.CLIMAX -> 0.58f
    }

    fun crystalPieceChance(level: Int): Double = crystalPieceChanceBase(level) * CRYSTAL_AMOUNT_FACTOR.toDouble()

    private fun crystalPieceChanceBase(level: Int): Double = when (kindFor(level)) {
        LevelChallengeKind.DISCOVERY -> 0.14
        LevelChallengeKind.PATTERN -> 0.46
        LevelChallengeKind.MOMENTUM -> 0.22
        LevelChallengeKind.PRECISION -> 0.34
        LevelChallengeKind.PRESSURE -> 0.50
        LevelChallengeKind.CLIMAX -> 0.60
    }

    /** Parte de los cristales iniciales que necesita dos impactos para perder la coraza. */
    fun crystalDoubleArmorChance(level: Int): Float = crystalDoubleArmorChanceBase(level).coerceAtMost(CRYSTAL_DOUBLE_ARMOR_MAX)

    private fun crystalDoubleArmorChanceBase(level: Int): Float = when (kindFor(level)) {
        LevelChallengeKind.DISCOVERY -> 0f
        LevelChallengeKind.PATTERN -> 0.06f
        LevelChallengeKind.MOMENTUM -> 0f
        LevelChallengeKind.PRECISION -> 0.10f
        LevelChallengeKind.PRESSURE -> 0.18f
        LevelChallengeKind.CLIMAX -> 0.28f
    }

    /** Turnos con una ficha DORMIDA. Fijo — ver [BiomeRhythm.punishmentTurns]. */
    @Suppress("UNUSED_PARAMETER")
    fun sleepDuration(level: Int): Int = BiomeRhythm.punishmentTurns(BiomeType.SLEEP)

    /** Compatibilidad del estado: Pasaje Encantado no tiene turnos de calma. */
    @Suppress("UNUSED_PARAMETER")
    fun fearCalmTurns(level: Int): Int = 0

    /** Dos raíces visibles, una línea por raíz, en todos los niveles. */
    @Suppress("UNUSED_PARAMETER")
    fun gardenRescueLines(level: Int): Int = 2

    // P-352: la raíz arrastra más deprisa (7/6/5 → 5/5/4).
    fun gardenAbductionTurns(level: Int): Int = when (kindFor(level)) {
        // P-360 (2026-09-28): vuelve a 7/6/5 (P-352 lo había dejado en 5/5/4). Hasta hoy la raíz casi no
        // atacaba (un bug ponía su reloj a cero con cada línea); ahora que ataca en cada ciclo, el arrastre
        // lento es lo que la deja en su sitio de la curva.
        LevelChallengeKind.DISCOVERY, LevelChallengeKind.MOMENTUM -> 7
        LevelChallengeKind.PATTERN, LevelChallengeKind.PRECISION -> 6
        LevelChallengeKind.PRESSURE, LevelChallengeKind.CLIMAX -> 5
    }

    // P-352: una bobina más por circuito (3/4/5/6 → 4/5/6/7).
    fun stormCoilCount(level: Int): Int = when (kindFor(level)) {
        LevelChallengeKind.DISCOVERY, LevelChallengeKind.MOMENTUM -> 4
        LevelChallengeKind.PRECISION -> 5
        LevelChallengeKind.PATTERN, LevelChallengeKind.PRESSURE -> 6
        LevelChallengeKind.CLIMAX -> 7
    }

    // P-353: la lava baja al final de CADA ciclo (el castigo siempre llega), también la primera vez.
    fun volcanoFirstThreshold(level: Int): Int = BiomeCycle.strikeAt(level)

    /**
     * Cadencia de descenso con la lava YA asomada: **cada turno seco**.
     *
     * La escalera del bioma es 3 → 1. Dos turnos viendo arder la cresta, al tercero se come la
     * primera fila, y desde ahí ya no suelta: cada colocación sin romper fila le cuesta otra.
     *
     * Puede permitirse un umbral de 1 —el único del juego— porque su castigo no es un golpe único e
     * irreversible: es una fila de lava, y **retrocede** con cada línea que rompas (`retreatLava`).
     * El turno de reacción que los demás biomas protegen con su aviso, aquí lo da la propia mecánica.
     */
    // P-353: ya asomada, también baja una vez por ciclo (antes: cada turno seco).
    fun volcanoSurfacedThreshold(level: Int): Int = BiomeCycle.strikeAt(level)

    /** Corte del Dojo: compás LENTO (parte el tablero y cuesta 4 líneas repararlo). */
    fun ninjaCutThreshold(level: Int): Int = BiomeCycle.strikeAt(level)   // P-353

    fun ninjaRepairLines(level: Int): Int = when (kindFor(level)) {
        // 3/4/5 → 2/3/3 (2026-09-24, P-254, decisión del usuario: bot Ninja 18 %). Con el corte no
        // se completa ninguna fila, así que cada columna cuesta mucho más que una línea normal.
        // P-352: reparar pide una línea más (2/3/3 → 3/4/4). P-359 (2026-09-28, campaña sin banco): vuelve a 2/3/3 (Dojo 52 %, objetivo 71 %).
        LevelChallengeKind.DISCOVERY, LevelChallengeKind.MOMENTUM -> 2
        LevelChallengeKind.PATTERN, LevelChallengeKind.PRECISION -> 3
        LevelChallengeKind.PRESSURE, LevelChallengeKind.CLIMAX -> 3
    }

    /** P-254: colocaciones con el tablero partido hasta que la grieta se cierra sola. Fijo. */
    @Suppress("UNUSED_PARAMETER")
    fun ninjaCutHealTurns(level: Int): Int = BiomeRhythm.punishmentTurns(BiomeType.NINJA)

    /**
     * P-254 · LA TREGUA: colocaciones tras cerrarse la grieta en que la katana no avisa. Después vuelve
     * la regla de la 3.ª tal cual. **P-323 (2026-09-26, decisión del usuario): de 3 a 1** — el Dojo
     * estaba en ~90 % y el corte se cerraba y no volvía en mucho rato.
     */
    @Suppress("UNUSED_PARAMETER")
    fun ninjaTruceTurns(level: Int): Int = NINJA_TRUCE_TURNS

    /** P-323: colocaciones de tregua tras cerrarse la grieta. */
    const val NINJA_TRUCE_TURNS = 1

    /** Turnos que el Teika queda CONGELADO. Fijo — ver [BiomeRhythm.punishmentTurns]. */
    @Suppress("UNUSED_PARAMETER")
    /**
     * P-359 (2026-09-28): la ola de frío llega con el ritmo del ciclo (cada 5 colocaciones, 4 en el último
     * tercio del capítulo) y no cada 3: con 3, más 2–3 turnos de hielo, los Teikas pasaban el nivel
     * congelados y el MEGA no llegaba.
     */
    fun glacierWaveEvery(level: Int): Int = BiomeCycle.strikeAt(level)

    fun glacierFrozenTurns(level: Int): Int = BiomeRhythm.punishmentTurns(BiomeType.GLACIER)

    /**
     * EL ESPEJO EMBRUJADO (P-246): colocaciones sin romper líneas hasta que DESTELLA. Compás del
     * bioma ([BiomeRhythm.idleBase]); se empaña un turno antes de destellar y entre medias enseña el
     * reflejo — ver [mirrorWarnThreshold].
     */
    fun mirrorThreshold(level: Int): Int = BiomeCycle.strikeAt(level)   // P-353

    /** Cuándo se EMPAÑA (primer aviso): dos turnos antes del destello, nunca antes del primero. */
    fun mirrorWarnThreshold(level: Int): Int = (mirrorThreshold(level) - 2).coerceAtLeast(1)

    /** Apertura de la boca: compás RÁPIDO (abrir aún no muerde; el mordisco es open + 1). */
    fun gluttonOpenThreshold(level: Int): Int = BiomeCycle.strikeAt(level) - 1   // P-353: abre en el aviso 2

    fun gluttonBiteThreshold(level: Int): Int = gluttonOpenThreshold(level) + 1

    /** Turnos que el POZO tarda en digerirse. Fijo en todo el capítulo. */
    @Suppress("UNUSED_PARAMETER")
    // P-352: 7 → 10 · P-359 (2026-09-28): 10 → 7 (Cocina 45 %) · P-361: 7 → 10 (con el reparto robusto, Cocina 76 %, objetivo 64 %)
    fun gluttonRecoverTurns(level: Int): Int = 10

    fun toxicSeedChance(level: Int): Float = toxicSeedChanceBase(level) * TOXIC_SEED_FACTOR

    private fun toxicSeedChanceBase(level: Int): Float = when (kindFor(level)) {
        LevelChallengeKind.DISCOVERY -> 0.18f
        LevelChallengeKind.PATTERN -> 0.40f
        LevelChallengeKind.MOMENTUM -> 0.25f
        LevelChallengeKind.PRECISION -> 0.32f
        LevelChallengeKind.PRESSURE -> 0.45f
        LevelChallengeKind.CLIMAX -> 0.52f
    }

    /** Tóxico (2026-09-28): colocaciones seguidas sin romper líneas ni avanzar antes de que el veneno salpique. */
    @Suppress("UNUSED_PARAMETER")
    fun toxicSplashAfter(level: Int): Int = ToxicBiomeRules.SPLASH_AFTER_TURNS

    /** Tóxico (2026-09-28): bloques que envenena cada salpicadura. */
    @Suppress("UNUSED_PARAMETER")
    fun toxicSplashCount(level: Int): Int = ToxicBiomeRules.SPLASH_COUNT

    /** Astro del Cielo Estelar: compás LENTO (una vez baja, dispara solo — no hay contrajuego). */
    fun starIdleThreshold(level: Int): Int = BiomeCycle.strikeAt(level)   // P-353

    // P-352: la Luna se lleva una parte mayor de las estrellas (+12 puntos, como mucho el 80 %).
    fun starMoonFraction(level: Int): Float = (harmfulFraction(level) + 0.12f).coerceAtMost(0.80f)

    /**
     * LA ESPADA DEL REY (P-294): colocaciones sin romper líneas hasta que la espada CAE. El Rey la alza
     * y señala la línea dos turnos antes ([swordWarnThreshold]) y la hoja brilla en el anterior.
     */
    fun swordThreshold(level: Int): Int = BiomeCycle.strikeAt(level)   // P-353

    /** Cuándo alza el Rey la espada y señala la línea: primer aviso. */
    fun swordWarnThreshold(level: Int): Int = (swordThreshold(level) - 2).coerceAtLeast(1)

    /**
     * LLUVIA DE METEORITOS (P-294): colocaciones sin romper líneas hasta el IMPACTO. El meteorito
     * aparece a lo lejos dos turnos antes ([meteorWarnThreshold]) y marca su cráter en el anterior.
     */
    fun meteorThreshold(level: Int): Int = BiomeCycle.strikeAt(level)   // P-353

    /** Cuándo aparece el meteorito (primer aviso). */
    fun meteorWarnThreshold(level: Int): Int = (meteorThreshold(level) - 2).coerceAtLeast(1)

    fun starVictimCount(total: Int, level: Int): Int =
        ceil(total.coerceAtLeast(0) * starMoonFraction(level)).toInt().coerceIn(0, total.coerceAtLeast(0))

    /** Ver [GhostSteal.victimCount]: la fracción propone y el tope absoluto dispone. */
    private data class Tuning(
        val layoutDensityDelta: Int,
    )

    /**
     * ## (RETIRADO 2026-09-03, P-119) La tercera columna: `idleDelta`
     *
     * Hubo una tercera columna que movía el umbral ocioso del bioma por tipo de nivel, y su historia
     * merece quedarse porque explica por qué NO vuelve. Medido el 2026-08-30: DISCOVERY y MOMENTUM
     * sumaban `+1` y PRESSURE restaba `−1`, o sea un recorrido de **2 turnos que se repetía cada
     * CUATRO niveles**, mientras el arco del capítulo solo movía la banda **±1**. El tramo central
     * del Dojo salía `4, 3, 4, 5, 4, 3, 4, 5…`: no era una rampa, era un vaivén.
     *
     * Ese mismo día se decidieron los **turnos FIJOS** («dos de aviso y al tercero ejecuta») y
     * [idleThreshold] pasó a devolver la base tal cual. A partir de ahí `idleDelta` se seguía
     * calculando y propagando hasta [LevelChallenge] **sin que nadie lo leyera nunca**: número
     * muerto con un KDoc que describía una curva que ya no ocurría — la trampa de `usesNewEngine`.
     * Se retira la columna entera. Si algún día vuelve una curva de ocio, se implementa completa.
     *
     * La densidad del tablero **sí manda**: ahí el diente de sierra es la gracia, cada tipo de nivel
     * plantea un problema distinto. (La columna del tamaño de bandeja se retiró el 2026-09-26: el
     * tamaño de las fichas lo decide solo el tablero, `PieceTrayPlanner`.)
     */
    private fun tuningFor(kind: LevelChallengeKind): Tuning = when (kind) {
        //                                        densidad
        LevelChallengeKind.DISCOVERY -> Tuning(-5)
        LevelChallengeKind.PATTERN -> Tuning(+3)
        LevelChallengeKind.MOMENTUM -> Tuning(-2)
        LevelChallengeKind.PRECISION -> Tuning(+5)
        // El único que se desvía: «Presión» adelanta el castigo un turno.
        LevelChallengeKind.PRESSURE -> Tuning(+2)
        LevelChallengeKind.CLIMAX -> Tuning(+6)
    }

    private fun harmfulFraction(level: Int): Float = when (kindFor(level)) {
        LevelChallengeKind.DISCOVERY -> 0.25f
        LevelChallengeKind.MOMENTUM -> 0.34f
        LevelChallengeKind.PRECISION -> 0.42f
        LevelChallengeKind.PATTERN -> 0.50f
        LevelChallengeKind.PRESSURE -> 0.60f
        LevelChallengeKind.CLIMAX -> 0.67f
    }

    /** El nombre a secas, sin bioma. Ver [DirectedLevel.name]. */
    private fun nameFor(kind: LevelChallengeKind, wave: Int): String = when (kind) {
        LevelChallengeKind.DISCOVERY -> "Descubrimiento"
        LevelChallengeKind.PATTERN -> "Patrón ${roman(wave)}"
        LevelChallengeKind.MOMENTUM -> "Impulso ${roman(wave)}"
        LevelChallengeKind.PRECISION -> "Precisión ${roman(wave)}"
        LevelChallengeKind.PRESSURE -> "Presión ${roman(wave)}"
        LevelChallengeKind.CLIMAX -> "Clímax"
    }

    private fun titleFor(kind: LevelChallengeKind, wave: Int, biome: BiomeType): String =
        "${nameFor(kind, wave)} · ${biome.displayName}"

    private fun kindFor(level: Int): LevelChallengeKind {
        val chapterIndex = LevelMap.chapterIndexForLevel(level)
        if (chapterIndex < 0) return LevelChallengeKind.DISCOVERY
        val localIndex = LevelMap.localIndex(level)
        val chapterSize = LevelMap.chapters[chapterIndex].levelCount
        return when {
            localIndex == 0 -> LevelChallengeKind.DISCOVERY
            localIndex + 1 == chapterSize -> LevelChallengeKind.CLIMAX
            else -> {
                // P-84: la oleada elige su propia permutación del ciclo de cuatro.
                val step = localIndex - 1
                cycleFor(chapterIndex, step / middleCycle.size)[step % middleCycle.size]
            }
        }
    }

    private fun briefFor(identity: DirectedLevel): String {
        val level = identity.level
        val mechanic = when (identity.biome) {
            BiomeType.CRYSTAL -> {
                val seed = (crystalSeedChance(level) * 100).roundToInt()
                val pieces = (crystalPieceChance(level) * 100).roundToInt()
                val double = (crystalDoubleArmorChance(level) * 100).roundToInt()
                "Cristal en $seed% del tablero y $pieces% de las fichas" +
                    if (double > 0) "; $double% de las corazas son dobles." else "."
            }
            BiomeType.SLEEP ->
                "La somnolencia llega tras ${idleThreshold(3, level)} turnos y duerme una ficha ${sleepDuration(level)} turno(s)."
            BiomeType.FEAR ->
                "Un bloque huye en cada colocación desde la primera, si tiene un destino libre; limpiar no lo detiene."
            BiomeType.GARDEN ->
                "Las raíces atacan tras ${idleThreshold(3, level)} turnos; rescata al Teika con ${gardenRescueLines(level)} línea(s) en ${gardenAbductionTurns(level)} turnos."
            BiomeType.STORM ->
                "Las filas eléctricas se convierten en circuitos de ${stormCoilCount(level)} bobinas."
            BiomeType.VOLCANO -> {
                val surfaced = volcanoSurfacedThreshold(level)
                val cadence = if (surfaced <= 1) "baja en cada turno seco"
                else "avanza cada $surfaced turnos secos"
                "La lava aparece tras ${volcanoFirstThreshold(level)} turnos secos y luego $cadence."
            }
            BiomeType.NINJA ->
                "La katana cae tras ${ninjaCutThreshold(level)} turnos; repara el corte con ${ninjaRepairLines(level)} líneas o espera ${ninjaCutHealTurns(level)} turnos."
            BiomeType.GLACIER ->
                "La ventisca llega tras ${idleThreshold(3, level)} turnos y congela durante ${glacierFrozenTurns(level)} ${if (glacierFrozenTurns(level) == 1) "turno" else "turnos"}."
            BiomeType.GHOST ->
                "El espejo se empaña en ${mirrorWarnThreshold(level)} turnos y destella en ${mirrorThreshold(level)}: sus fantasmas duran ${HauntedMirror.PHANTOM_TURNS}."
            BiomeType.GLUTTON ->
                "La boca abre en ${gluttonOpenThreshold(level)} turnos, muerde en ${gluttonBiteThreshold(level)} y el pozo dura ${gluttonRecoverTurns(level)}."
            BiomeType.TOXIC -> {
                val pct = (toxicSeedChance(level) * 100).roundToInt()
                "El veneno cubre $pct% del inicio, se extiende en cada colocación sin líneas y salpica ${toxicSplashCount(level)} bloques tras ${toxicSplashAfter(level)} turnos sin avanzar."
            }
            BiomeType.STAR -> {
                val pct = (starMoonFraction(level) * 100).roundToInt()
                "El astro baja tras ${starIdleThreshold(level)} turnos; la Luna puede llevarse $pct% de las estrellas."
            }
            BiomeType.KING ->
                "El Rey amenaza en ${swordWarnThreshold(level)} turnos y golpea en ${swordThreshold(level)}: una vez la espada, otra una pieza de ajedrez."
            BiomeType.GALACTIC ->
                (if (MeteorShower.doubleShowerFor(level)) "Dos meteoritos asoman" else "El meteorito asoma") +
                    " en ${meteorWarnThreshold(level)} turnos e impacta${if (MeteorShower.doubleShowerFor(level)) "n" else ""} en ${meteorThreshold(level)}."
            BiomeType.NONE -> "Aprende las reglas esenciales del tablero."
        }
        val board = when (identity.kind) {
            LevelChallengeKind.DISCOVERY -> "Tablero abierto y bandeja amable."
            LevelChallengeKind.PATTERN -> "Lee la figura compacta antes de colocar."
            LevelChallengeKind.MOMENTUM -> "La bandeja favorece piezas grandes para encadenar líneas."
            LevelChallengeKind.PRECISION -> "Tablero denso y piezas pequeñas: cada hueco importa."
            LevelChallengeKind.PRESSURE -> "La amenaza acelera: protege cada colocación."
            LevelChallengeKind.CLIMAX -> "La versión más intensa del bioma cierra el capítulo."
        }
        return "$mechanic $board"
    }

    private fun roman(value: Int): String = when (value.coerceIn(1, 6)) {
        1 -> "I"
        2 -> "II"
        3 -> "III"
        4 -> "IV"
        5 -> "V"
        else -> "VI"
    }
}
