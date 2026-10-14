package com.cukiti.teikafusion.model

import kotlin.math.roundToInt

/**
 * **LO QUE CUESTA EL TABLERO** (2026-08-26, pedido del usuario: «hay que hacer un cálculo para
 * reasignar los turnos de cada nivel de cada bioma, tomando en cuenta la aparición de fichas, los
 * Teikas que ocupan espacio, las estrellas del objetivo y todo lo que interactúe directa e
 * indirectamente con el tablero»).
 *
 * [LevelBudget] sabía dos cosas: la curva medida en el playtest (29 → 40 turnos) y un impuesto de
 * bioma de 0-6 turnos ([BiomeDifficultyProfile]) que trata a los catorce biomas como una etiqueta.
 * Lo que no sabía es **qué hay realmente en la pizarra**: cuántas celdas nacen ocupadas, cuántas de
 * ellas son cristal —que se come un clear entero antes de romperse—, cuántas bobinas bloquean su
 * fila y su columna hasta que las deduzcas, cuántos huecos son tan estrechos que solo admiten un
 * monominó, y cuánto tablero le van comiendo al jugador los **Teikas que nacen de sus propias
 * líneas**. Este archivo pone número a todo eso.
 *
 * ## Por qué ahora
 *
 * Hasta el 2026-08-26 la mitad del mapa abría con líneas YA hechas (censo: 150 niveles, 3,79 líneas
 * de media). Esas líneas se rompían solas con la primera ficha y devolvían Teikas y ~8 estrellas
 * cada una, así que el tablero denso era un DESCUENTO disfrazado de estorbo, y ese descuento estaba
 * dentro de la medición de 25-35 turnos que fija la curva. [LevelOpening.MAX_LINE_FILL] acaba con
 * él: ahora el estorbo estorba de verdad, y hay que pagarle al jugador lo que antes se cobraba solo.
 *
 * ## Cómo se lee el modelo
 *
 * Cada término es **turnos**, y todos son diales de playtest. El resultado se mide contra
 * [MEASURED_BASELINE_MOVES] —el estorbo que la propia medición ya llevaba encima, o sea el del
 * tablero mediano del mapa: 30 celdas, sin huecos aislados, cuatro colores— y se topa en
 * [MAX_EXTRA_MOVES] para que ningún nivel se convierta en una sesión.
 *
 * **Convive con [BiomeDifficultyProfile], no lo sustituye.** Son dos ejes distintos a propósito: el
 * impuesto de bioma paga el RELOJ ambiental (el corte ninja que parte el tablero, la cacería
 * fantasma, la lava que sube), y este archivo paga la MATERIA que hay sobre las celdas. Un bioma
 * como el Cristal cobra por los dos porque hace las dos cosas; el tope común los mantiene a raya.
 */
data class BoardClutterProfile(
    /** Celdas que el nivel trae ya ocupadas. */
    val occupiedCells: Int,
    /** Huecos sin ningún hueco pegado en cruz: ahí solo cabe un monominó. */
    val isolatedHoles: Int,
    /** Celdas de cristal SEMBRADAS por el layout (valor esperado, no una tirada). */
    val seededCrystalCells: Float,
    /** De las anteriores, las que nacen con coraza doble y piden dos clears. */
    val doubleArmorCells: Float,
    /** Celdas de cristal que llegan por la BANDEJA en cada colocación (valor esperado). */
    val crystalCellsPerTurn: Float,
    /** Celdas envenenadas de salida (Pantano Tóxico). */
    val poisonedCells: Float,
    /** Bobinas del Circuito: cada una bloquea su fila y su columna hasta ser deducida. */
    val coilCells: Int,
    /** Líneas que el objetivo exige. Cada una pare un Teika que ocupa tablero. */
    val requiredLines: Int,
    /** Colores distintos que pide el objetivo. */
    val objectiveColors: Int,
)

object BoardClutterCost {

    /** Celdas ocupadas de salida que valen un turno. */
    const val CELLS_PER_MOVE = 12f

    /** Turnos que cuesta cada hueco aislado: es una celda que solo llenará un monominó. */
    const val ISOLATED_HOLE_MOVES = 0.25f

    /**
     * Turnos por celda de cristal.
     *
     * Un cristal no pide una línea propia: pide REPETIR una línea que el jugador iba a romper de
     * todos modos (el primer clear le quita la coraza, el segundo lo rompe). Por eso vale una
     * fracción de turno y no uno entero.
     */
    const val CRYSTAL_CELL_MOVES = 0.12f

    /** Turnos extra por cada cristal de coraza DOBLE: un clear más que el cristal normal. */
    const val DOUBLE_ARMOR_MOVES = 0.10f

    /** Turnos por celda envenenada. Barato: el veneno se desintegra solo, lo que cuesta es la prisa. */
    const val POISON_CELL_MOVES = 0.10f

    /**
     * Turnos por bobina. El más caro del modelo con diferencia: mientras no se deduce su orden,
     * una bobina cancela la limpieza de su fila **y** de su columna — dos de las dieciséis líneas
     * del tablero.
     */
    const val COIL_MOVES = 0.8f

    /**
     * Turnos por cada línea que el objetivo exige, en concepto de **Teikas**.
     *
     * Cada línea rota pare un T1 ([GameViewModel.spawnLineClearTeika]) que cae, rueda y ocupa
     * celdas hasta que se fusiona; un T4 tapa 16 celdas y bloquea su fila y su columna a propósito.
     * El jugador paga ese espacio en colocaciones peores, y lo paga durante todo el nivel.
     */
    const val TEIKA_MOVES_PER_LINE = 0.12f

    /**
     * Turnos por cada color del objetivo **a partir del segundo**.
     *
     * Con un color, toda estrella rota suma. Con cuatro, el jugador tiene que elegir qué línea rompe
     * y en qué orden, y las estrellas que le sobran de un color ya cubierto no valían nada — eso
     * último lo arregla [StarObjectiveRetint], así que el coste por color es menor que antes.
     */
    const val COLOR_MOVES = 0.6f

    // (RETIRADOS 2026-08-28) `DEAD_CELL_MOVES` y `SEEDED_TEIKA_CELL_MOVES`: cobraban en turnos la
    // silueta de celdas muertas y el Teika sembrado. Los dos ejes salieron del perfil de variedad,
    // así que ya no hay nada que cobrar.

    /**
     * Estorbo que la MEDICIÓN ya llevaba dentro: el del tablero mediano del mapa (30 celdas, cero
     * huecos aislados, cuatro colores, ~31 líneas de objetivo). Sin restarlo, el modelo cobraría dos
     * veces lo que la curva de playtest ya cobra.
     *
     * **Este número es el que decide si el cambio es una REDISTRIBUCIÓN o una inflación.** Está
     * puesto para que el nivel mediano del mapa pague ~1 turno de estorbo y la media del juego se
     * quede donde estaba (censo del 2026-08-26: media 36,5 turnos antes de todo esto). Lo que se
     * mueve es el reparto: el tablero hostil —cristal de clímax, circuito, huecos de un solo
     * monominó— sube hasta [MAX_EXTRA_MOVES], y el tablero limpio se queda igual que siempre.
     * Bajarlo sube el mapa entero; subirlo lo baja entero.
     */
    const val MEASURED_BASELINE_MOVES = 7f

    /** Techo del modelo. Ningún tablero, por hostil que sea, añade más que esto. */
    const val MAX_EXTRA_MOVES = 12

    /**
     * Perfil del tablero con el que ARRANCA [level]: no simula la partida, describe la salida y lo
     * que el objetivo obliga a producir.
     */
    fun profile(
        level: Int,
        objective: LevelObjective?,
        cells: Collection<LevelCell>,
        biomePrimary: BiomeType,
    ): BoardClutterProfile {
        val occupied = cells.mapTo(HashSet()) { Position(it.x, it.y) }
        val crystalActive = BiomeType.CRYSTAL == biomePrimary
        val toxicActive = biomePrimary == BiomeType.TOXIC
        val stormActive = biomePrimary == BiomeType.STORM

        val seededCrystal =
            if (crystalActive) occupied.size * LevelDirector.crystalSeedChance(level) else 0f
        val doubleArmor = seededCrystal * LevelDirector.crystalDoubleArmorChance(level)
        // Una ficha de cristal trae 1-2 celdas con coraza (Piece.generatePieceForShape), o sea 1,5
        // de media, y solo la trae con la probabilidad del director.
        val crystalPerTurn =
            if (crystalActive) (LevelDirector.crystalPieceChance(level) * 1.5f).toFloat() else 0f
        val poisoned =
            if (toxicActive) occupied.size * LevelDirector.toxicSeedChance(level) else 0f
        // Las bobinas del Circuito nacen sobre filas COMPLETAS de madera. Desde la holgura de línea
        // ([LevelOpening.MAX_LINE_FILL]) el layout ya no puede traer ninguna, así que las que se
        // encuentre el jugador salen de las filas que complete él: se cobra una tanda.
        val coils = if (stormActive) LevelDirector.stormCoilCount(level) else 0

        val lines = objective?.let { LevelBudget.linesForObjective(it, occupied.size) } ?: 0
        return BoardClutterProfile(
            occupiedCells = occupied.size,
            isolatedHoles = isolatedHoles(occupied),
            seededCrystalCells = seededCrystal,
            doubleArmorCells = doubleArmor,
            crystalCellsPerTurn = crystalPerTurn,
            poisonedCells = poisoned,
            coilCells = coils,
            requiredLines = lines,
            objectiveColors = objective?.required?.size ?: 0,
        )
    }

    /**
     * Turnos que hay que sumarle a la curva medida para que [profile] sea jugable con el mismo
     * desahogo con el que se cronometró el tablero mediano.
     *
     * [baseMoves] son los turnos de la curva ANTES del estorbo: solo lo usa el cristal que llega por
     * la bandeja, que es un goteo por colocación y no una cantidad fija. Se lee la curva base y no el
     * resultado para que el cálculo no se muerda la cola.
     */
    fun extraMoves(profile: BoardClutterProfile, baseMoves: Int): Int {
        val raw =
            profile.occupiedCells / CELLS_PER_MOVE +
                profile.isolatedHoles * ISOLATED_HOLE_MOVES +
                profile.seededCrystalCells * CRYSTAL_CELL_MOVES +
                profile.doubleArmorCells * DOUBLE_ARMOR_MOVES +
                profile.crystalCellsPerTurn * baseMoves * CRYSTAL_CELL_MOVES +
                profile.poisonedCells * POISON_CELL_MOVES +
                profile.coilCells * COIL_MOVES +
                profile.requiredLines * TEIKA_MOVES_PER_LINE +
                (profile.objectiveColors - 1).coerceAtLeast(0) * COLOR_MOVES
        return (raw - MEASURED_BASELINE_MOVES).roundToInt().coerceIn(0, MAX_EXTRA_MOVES)
    }

    /** Atajo: perfila y cobra de una vez. */
    fun extraMoves(
        level: Int,
        objective: LevelObjective?,
        cells: Collection<LevelCell>,
        biomePrimary: BiomeType,
        baseMoves: Int,
    ): Int = extraMoves(
        profile(level, objective, cells, biomePrimary),
        baseMoves
    )

    /** Huecos sin ningún otro hueco pegado en cruz. Mismo criterio que [LevelOpening]. */
    private fun isolatedHoles(occupied: Set<Position>): Int {
        val size = LevelInitialDifficulty.BOARD_SIZE
        var alone = 0
        for (y in 0 until size) {
            for (x in 0 until size) {
                val p = Position(x, y)
                if (p in occupied) continue
                fun free(nx: Int, ny: Int): Boolean =
                    nx in 0 until size && ny in 0 until size && Position(nx, ny) !in occupied
                val hasNeighbour = free(x + 1, y) || free(x - 1, y) || free(x, y + 1) || free(x, y - 1)
                if (!hasNeighbour) alone++
            }
        }
        return alone
    }
}
