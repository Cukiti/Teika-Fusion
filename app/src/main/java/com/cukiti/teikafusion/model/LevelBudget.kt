package com.cukiti.teikafusion.model

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * **TURNOS RESTANTES** (2026-07-31c, pedido del usuario: «la mecánica de Candy Crush»).
 *
 * Cada nivel trae un presupuesto de colocaciones. Al agotarse sin haber completado el objetivo se
 * ofrece revivir; si no, se pierde. Sustituye al objetivo SUPERVIVENCIA, que pedía justo lo
 * contrario —aguantar turnos— y dejaba de tener sentido en cuanto los turnos son el recurso.
 *
 * ## De dónde sale el número
 *
 * **De una medición, desde el 2026-08-01b.** Hasta entonces se derivaba entero del trabajo que el
 * nivel pide (líneas → turnos) y el resultado estaba mal por casi el doble: el modelo daba 42 turnos
 * al nivel 5 y **79 al 293**, cuando el playtest del usuario los cierra en **~25 y ~35**.
 *
 * El error no es un dial mal puesto, es la **forma** de la curva. Las líneas exigidas van de 16
 * (suelo del MEGA) a 32 (objetivo de estrellas tardío): se DUPLICAN. El tiempo real del jugador solo
 * crece un 40 %, porque cuanto más lleno está el tablero **menos cuesta cada línea** —una ficha
 * cierra varias a la vez, la irrupción del MEGA cosecha estrellas de golpe y las habilidades
 * recogen por su cuenta—. Ninguna cantidad de retoque a [PLACEMENTS_PER_LINE] produce una recta que
 * suba 16→32 en trabajo y solo 25→35 en turnos.
 *
 * Así que el presupuesto de los niveles del mapa **es la medición**: [MOVES_AT_START] turnos en el
 * primer nivel con MEGA, [MOVES_AT_END] en el último, recta por el medio. Los dos llevan ~15 % de
 * margen sobre lo cronometrado (25 → 29, 35 → 40): el contador tiene que apretar al que desperdicia
 * fichas, no matar al que juega bien.
 *
 * ## Qué queda de la derivación
 *
 * Sigue viva y hace dos trabajos que la curva no puede hacer:
 *
 * 1. **Guardia de nivel imposible.** Si un objetivo de LÍNEAS pide más trabajo del que la curva
 *    paga, manda el trabajo. Un nivel imposible **no falla al compilar**: arranca, se juega y se
 *    pierde siempre.
 * 2. **Los niveles de intro**, que no exigen MEGA y piden objetivos diminutos: ahí no hay medición
 *    que aplicar y el presupuesto sale del trabajo + [BIOME_ALLOWANCE] + el regalo de [EXTRA].
 *
 * **Todas las constantes son diales de playtest.** Si el próximo playtest dice otra cosa, lo que se
 * mueve son [MOVES_AT_START] y [MOVES_AT_END] — ya no hay que pelearse con el modelo.
 */
object LevelBudget {

    /** Teikas T1 que hay que encadenar para un MEGA: 2⁴ = 16 (cuatro niveles de fusión). */
    const val T1_PER_MEGA = 16

    /**
     * Turnos de la MEDICIÓN en el primer nivel con MEGA (~25 cronometrados + 15 % de margen).
     *
     * Es el presupuesto más apretado del mapa a propósito: el tablero está casi vacío, no hay
     * estorbo que jugar alrededor y el objetivo es el mínimo.
     */
    const val MOVES_AT_START = 29

    /**
     * Turnos de la MEDICIÓN en el último nivel del mapa (~35 cronometrados + 15 % de margen).
     *
     * Los niveles tardíos piden el DOBLE de líneas que los primeros y aun así solo cuestan 10 turnos
     * más: el tablero lleno cierra líneas de varias en varias. Por eso la curva es plana comparada
     * con el trabajo — ver el KDoc de la clase.
     */
    const val MOVES_AT_END = 40

    /**
     * Turnos que cuesta, de media, cerrar una línea jugando bien.
     *
     * **2,0 → 1,2 (2026-08-01b, playtest del usuario).** Ya no fija el presupuesto de los niveles
     * del mapa —eso lo hace la curva medida—, solo la guardia de «objetivo que se sale de la curva»
     * y los niveles de intro. El 1,2 sale de la propia medición: ~25 turnos para ~20 líneas
     * efectivas. El 2,0 asumía una línea por cada dos fichas y el jugador cierra bastante más.
     */
    const val PLACEMENTS_PER_LINE = 1.2f

    /**
     * Estrellas que se recogen, de media, en cada línea que rompes.
     *
     * **2,5 → 3,5 (2026-08-01).** El 2,5 es de cuando la figura solo llevaba estrella en una parte
     * de sus celdas; desde que la lleva ENTERA, una línea se traga ~3,5 celdas ya puestas
     * ([CLEAR_CELLS_PER_LINE]) y **todas** valen estrella. El número estaba simplemente desfasado, y
     * por eso pedir más estrellas parecía costar más líneas de las que cuesta.
     */
    const val STARS_PER_LINE = 3.5f

    /**
     * Colchón por tener una mecánica de bioma encima quitándote turnos.
     *
     * Solo lo cobra la ruta derivada (intro y objetivos fuera de curva): la medición se hizo **con**
     * el bioma puesto, así que sumárselo otra vez a la curva sería contarlo dos veces.
     */
    const val BIOME_ALLOWANCE = 4

    /**
     * Celdas pre-colocadas que valen un turno de colchón (solo en la ruta derivada).
     *
     * Cada bloque que ya está puesto es una celda que no controlas: estorba las colocaciones y
     * obliga a jugar alrededor. Una fila entera de estorbo (8 celdas) vale un turno. En los niveles
     * del mapa esto ya está dentro de la pendiente de la curva medida, que sube justo porque el
     * tablero tardío arranca lleno.
     */
    const val CELLS_PER_EXTRA_MOVE = 8

    /**
     * El regalo: turnos por encima de lo calculado, en la ruta derivada.
     *
     * Cubre el arranque, donde las primeras colocaciones todavía no cierran nada. La curva medida no
     * lo cobra aparte: su 15 % de margen sobre lo cronometrado ES ese regalo.
     */
    const val EXTRA = 4

    /** Turnos que devuelve revivir. */
    const val REVIVE_MOVES = 5

    /**
     * **Lo que cuesta la segunda oportunidad, en teikoins.** Vivía como un `500` suelto repetido en
     * cuatro sitios (el cobro, la guarda de «ni monedas ni anuncio», el precio del botón y el color
     * del contador). Se reúne aquí porque desde P-105 hay un quinto lector que **compara** contra
     * él: el cartel de «SIN SALIDA» no ofrece comprar una carga si sale más cara que revivir, y esa
     * comparación con dos números sueltos se desincroniza a la primera que alguien mueva el precio.
     */
    const val REVIVE_COST_TEIKOINS = 500

    /** Suelo absoluto: ningún nivel puede nacer con menos margen que esto. */
    const val MIN_MOVES = 16

    /**
     * Celdas YA PUESTAS que se lleva por delante una línea, de media.
     *
     * Una línea son 8 celdas, pero la mitad largas las pones tú al completarla: solo ~3,5 son
     * bloques que ya estorbaban. Es lo que convierte «me sobran N bloques» en «me faltan N líneas».
     */
    const val CLEAR_CELLS_PER_LINE = 3.5f

    /** Líneas que hacen falta SOLO para el MEGA, cuando el nivel lo exige. */
    fun linesForMega(): Int = T1_PER_MEGA

    /**
     * Líneas que hace falta romper para cumplir [objective].
     *
     * [figureSize] son los bloques con los que arranca el tablero: solo lo usa el DESPEJE, que se
     * mide contra ellos.
     */
    fun linesForObjective(objective: LevelObjective, figureSize: Int = 0): Int = when (objective.goal) {
        LevelGoal.STARS -> ceil(objective.total / STARS_PER_LINE).toInt()
        LevelGoal.LINES -> objective.amount
        // DESPEJE: hay que quitar del tablero lo que sobra por encima de la meta. Se paga entero
        // aunque la irrupción del MEGA vacíe el tablero: el presupuesto no puede asumir que el
        // jugador llegará al MEGA antes de despejar, y de las dos rutas la cara es esta.
        LevelGoal.CLEAR ->
            ceil((figureSize - objective.amount).coerceAtLeast(0) / CLEAR_CELLS_PER_LINE).toInt()
        // OBSOLETO (ver [LevelGoal.SURVIVE]): ningún nivel lo genera desde 2026-07-31c.
        LevelGoal.SURVIVE -> objective.amount
    }

    /** Turnos que cuesta romper [lines] líneas. */
    fun placementsForLines(lines: Int): Int = ceil(lines * PLACEMENTS_PER_LINE).toInt()

    /**
     * La curva MEDIDA: [MOVES_AT_START] turnos en el primer nivel con MEGA → [MOVES_AT_END] en el
     * último del mapa, recta por el medio.
     *
     * El eje es el nivel dentro del mapa, no el capítulo: un capítulo es el 7 % de la recta, así que
     * dentro de un mismo bioma el presupuesto es prácticamente constante y lo que cambia de un
     * capítulo al siguiente es la mecánica, no la cuenta atrás.
     */
    fun playtestMoves(level: Int): Int {
        val first = LevelMap.introLevels + 1
        val span = (LevelMap.totalLevels - first).coerceAtLeast(1)
        val t = ((level - first).toFloat() / span).coerceIn(0f, 1f)
        return (MOVES_AT_START + (MOVES_AT_END - MOVES_AT_START) * t).roundToInt()
    }

    /**
     * Presupuesto de turnos de [level]. `0` = **sin límite** (Infinito y niveles sin layout): el
     * modo Infinito se juega contra el muro, no contra un contador.
     *
     * Dos rutas, y cuál manda lo decide si el nivel exige MEGA (o sea, si está en el mapa):
     * la **curva medida** para los niveles del mapa, y el **trabajo derivado** para la intro. La
     * curva lleva además una guardia: si el objetivo pide más líneas de las que paga, manda el
     * objetivo, porque un nivel imposible no falla al compilar.
     */
    fun movesFor(
        level: Int,
        objective: LevelObjective?,
        hasBiome: Boolean,
        figureSize: Int = 0,
        initialCells: Collection<LevelCell> = emptyList(),
        biomePrimary: BiomeType = BiomeType.NONE,
        companionId: String? = null,
        companionLevel: Int = 1
    ): Int {
        val candidate = movesForRaw(
            level, objective, hasBiome, figureSize, initialCells, biomePrimary,
            companionId, companionLevel
        )
        if (objective == null || !ChapterArc.requiresMega(level)) return candidate
        // La curva sigue siendo monótona como referencia, pero un tablero que empieza con líneas
        // casi hechas puede justificar una pequeña bajada real de turnos. El crédito solo se resta
        // del suelo anterior; así no se compensa un tablero generoso con una caída arbitraria por
        // cambio de bioma, y el presupuesto sigue dentro de la banda calibrada.
        val initialFreeCredit = LevelInitialDifficulty.initialFreeProgressCredit(initialCells)
        // (2026-09-04) Aquí se sumaron un momento los turnos extra de UN SOLO EJE (P-152), cuando esa
        // regla impedía limpiar por el eje muerto. Se retiró junto con aquella versión: el
        // presupuesto de turnos **no se deriva de las reglas**, es la curva medida en el playtest
        // (2026-08-01b), y compensar una mecánica por aquí habría inflado esa curva por la puerta de
        // atrás. La regla se rediseñó para no necesitarlo, y acabó retirada el 2026-09-05;
        // la sustituyó EL REBOTE, que tampoco lo toca — ver el KDoc de [LineRecoil].
        return maxOf(
            candidate,
            monotonicMapFloor(level - 1, companionId, companionLevel, biomePrimary != BiomeType.NONE) -
                initialFreeCredit
        )
    }

    /**
     * Suelo monótono de la curva AMBIENTAL: el presupuesto de un nivel no puede caer por debajo del
     * de los anteriores solo porque le haya tocado otro bioma.
     *
     * **Solo términos ambientales, y por eso es barato (2026-08-26).** Antes esto era una recursión
     * de hasta 289 niveles y cada peldaño llamaba a `LevelLayouts.get`, que con una caché de ocho
     * entradas significaba **reconstruir el nivel entero** —figura, transformación, recorte,
     * apertura— una vez por peldaño. Medido: **218 ms** para el presupuesto del nivel 293 y **148 ms**
     * más para sus umbrales de estrella, en un PC de escritorio; en un teléfono es el tirón que se
     * siente al abrir el panel de JUGAR NIVEL, que pide las tres cosas durante la composición.
     *
     * El arreglo es además el significado correcto: este suelo dice en su propio KDoc que «solo
     * suaviza la curva ambiental», y para eso ya pasaba un objetivo vacío. Pasarle a la vez el
     * TABLERO de cada nivel era la contradicción — el estorbo de la pizarra es específico del nivel y
     * lo cobra `candidate`, no el suelo. Sin layouts esto es aritmética pura: un bucle de 289
     * cuentas, microsegundos.
     */
    /**
     * Memoria del suelo. El panel de JUGAR NIVEL pide TRES cosas al abrirse —turnos, umbral de 2★ y
     * umbral de 3★— y las tres pasan por aquí, así que sin caché el mismo recorrido se hace tres
     * veces por apertura. La clave lleva el compañero porque su crédito entra en la cuenta.
     */
    private val floorCache = java.util.concurrent.ConcurrentHashMap<String, Int>()

    private fun monotonicMapFloor(level: Int, companionId: String?, companionLevel: Int, concreteBiomes: Boolean): Int {
        if (level <= LevelMap.introLevels) return 0
        val key = "$level|${companionId ?: "-"}|$companionLevel|$concreteBiomes"
        floorCache[key]?.let { return it }
        val companionAllowance = CompanionChallengeModifier.expectedTurnCredit(companionId, companionLevel)
        var floor = 0
        for (l in (LevelMap.introLevels + 1)..level) {
            val primary = if (concreteBiomes) LevelMap.biomeFor(l) else BiomeType.NONE
            val ambient = maxOf(
                playtestMoves(l) +
                    BiomeDifficultyProfile.extraMoves(l, primary) +
                    companionAllowance,
                MOVES_AT_START
            ).coerceAtLeast(MIN_MOVES)
            if (ambient > floor) floor = ambient
        }
        floorCache[key] = floor
        return floor
    }

    private fun movesForRaw(
        level: Int,
        objective: LevelObjective?,
        hasBiome: Boolean,
        figureSize: Int = 0,
        initialCells: Collection<LevelCell> = emptyList(),
        /** Biomas reales activos. Se dejan opcionales para mantener compatibilidad con saves/tests. */
        biomePrimary: BiomeType = BiomeType.NONE,
        companionId: String? = null,
        companionLevel: Int = 1
    ): Int {
        if (objective == null) return 0
        val objectiveLines = linesForObjective(objective, figureSize)
        if (!ChapterArc.requiresMega(level)) {
            // Intro: sin medición que aplicar, se paga el trabajo tal cual.
            val allowance = if (hasBiome) BIOME_ALLOWANCE else 0
            val clutter = figureSize / CELLS_PER_EXTRA_MOVE
            val companionAllowance = CompanionChallengeModifier.expectedTurnCredit(companionId, companionLevel)
            return (placementsForLines(objectiveLines) + allowance + clutter + EXTRA + companionAllowance)
                .coerceAtLeast(MIN_MOVES)
        }
        // El suelo del MEGA (16 líneas) ya está dentro de lo cronometrado: solo se compara con la
        // curva el trabajo que el OBJETIVO pide por encima. La curva histórica incluye un bioma
        // genérico; si conocemos las dos mecánicas reales, reemplazamos ese margen por el impuesto
        // específico para no tratar igual Cristal, Tormenta y Galáctico.
        val guard = placementsForLines(objectiveLines)
        val hasConcreteBiome = biomePrimary != BiomeType.NONE
        val biomeTax = if (hasConcreteBiome) {
            BiomeDifficultyProfile.extraMoves(level, biomePrimary)
        } else 0
        val companionAllowance = CompanionChallengeModifier.expectedTurnCredit(companionId, companionLevel)
        val initialFreeCredit = LevelInitialDifficulty.initialFreeProgressCredit(initialCells)
        // ── ESTORBO REAL DEL TABLERO (2026-08-26, pedido del usuario) ────────────────────────────
        // La curva medida y el impuesto de bioma describen el nivel por fuera: en qué punto del mapa
        // está y qué etiqueta lleva. [BoardClutterCost] lo describe por dentro —celdas ocupadas,
        // cristal sembrado y el que llega en la bandeja, veneno, bobinas, huecos donde solo cabe un
        // monominó, los Teikas que paren las propias líneas del objetivo y cuántos colores hay que
        // atender— y cobra la diferencia contra el tablero mediano, que es el que se cronometró.
        //
        // Sin esto, quitarle al mapa las líneas regaladas de salida (ver [LevelOpening.MAX_LINE_FILL])
        // habría sido una subida de dificultad a coste cero para el presupuesto.
        val baseMoves = playtestMoves(level)
        val clutterTax = BoardClutterCost.extraMoves(
            level = level,
            objective = objective,
            cells = initialCells,
            biomePrimary = biomePrimary,
            baseMoves = baseMoves,
        )
        return maxOf(
            baseMoves - initialFreeCredit + biomeTax + clutterTax + companionAllowance,
            guard,
            MOVES_AT_START
        )
            .coerceAtLeast(MIN_MOVES)
    }

    /**
     * Turnos que [level] cobra por el ESTORBO de su tablero ([BoardClutterCost]), resueltos desde el
     * mapa. `0` = este tablero no estorba más que el mediano.
     *
     * Es la cifra que explica por qué dos niveles vecinos con la misma curva no tienen los mismos
     * turnos, y la que vigila el test de monotonía.
     */
    fun clutterTaxForLevel(level: Int): Int {
        val layout = LevelLayouts.get(level) ?: return 0
        return BoardClutterCost.extraMoves(
            level = level,
            objective = layout.second,
            cells = layout.first,
            biomePrimary = LevelMap.biomeFor(level),
            baseMoves = playtestMoves(level),
        )
    }

    /** Atajo: resuelve objetivo, bioma y estorbo del tablero desde el mapa de niveles. */
    /**
     * Presupuesto de turnos de [level] **con el compañero que va a jugarlo**.
     *
     * ## Por qué acepta el compañero (P-86, 2026-08-30)
     *
     * Porque no aceptarlo era un bug visible en el móvil: el panel de nivel anunciaba **46 turnos**
     * y la ronda arrancaba con **49**. Los dos calculaban el presupuesto, pero el panel llamaba aquí
     * —sin compañero— y `GameState.initial` llamaba a [movesFor] por su cuenta **pasándolo**. La
     * diferencia era exactamente el crédito del compañero.
     *
     * El arreglo no fue añadir el parámetro en el panel: fue dejar **una sola función**. Ahora
     * `GameState.initial` también entra por aquí, así que panel y ronda no pueden discrepar aunque
     * alguien cambie la fórmula — no hay dos fórmulas que mantener sincronizadas. Lo fija
     * `LevelBudgetTest`.
     */
    fun movesForLevel(
        level: Int,
        companionId: String? = null,
        companionLevel: Int = 1,
    ): Int {
        val layout = LevelLayouts.get(level)
        return movesFor(
            level = level,
            objective = layout?.second,
            hasBiome = LevelMap.biomeFor(level) != BiomeType.NONE,
            figureSize = layout?.first?.size ?: 0,
            initialCells = layout?.first ?: emptyList(),
            biomePrimary = LevelMap.biomeFor(level),
            companionId = companionId,
            companionLevel = companionLevel,
        )
    }
}
