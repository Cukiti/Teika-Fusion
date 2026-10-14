package com.cukiti.teikafusion.model

/**
 * Medición geométrica del tablero que ya está puesto cuando comienza un nivel.
 *
 * La cantidad de bloques no es una dificultad fiable: un tablero denso puede traer varias filas o
 * columnas a una sola celda de completarse y regalar progreso. Esta métrica solo concede crédito
 * por ese progreso inicial potencial; no convierte automáticamente un tablero lleno en un nivel
 * más difícil ni en uno más fácil.
 */
data class InitialBoardProfile(
    val occupiedCells: Int,
    val emptyCells: Int,
    val nearCompleteRows: Int,
    val nearCompleteColumns: Int,
    val almostCompleteRows: Int,
    val almostCompleteColumns: Int
) {
    /** Líneas que empiezan a una o dos celdas de completarse. */
    val freeProgressScore: Int
        get() = nearCompleteRows + nearCompleteColumns +
            (almostCompleteRows + almostCompleteColumns) * 2
}

object LevelInitialDifficulty {
    const val BOARD_SIZE = 8
    const val MAX_INITIAL_FREE_CREDIT = 4

    fun analyze(cells: Collection<LevelCell>): InitialBoardProfile {
        val occupied = cells.asSequence()
            .map { it.x.coerceIn(0, BOARD_SIZE - 1) to it.y.coerceIn(0, BOARD_SIZE - 1) }
            .toSet()
        val rowCounts = IntArray(BOARD_SIZE)
        val columnCounts = IntArray(BOARD_SIZE)
        occupied.forEach { (x, y) ->
            rowCounts[y]++
            columnCounts[x]++
        }

        fun countWithMissing(missing: IntArray): Int = missing.count { it == 1 }
        fun countWithTwoMissing(missing: IntArray): Int = missing.count { it == 2 }

        val rowMissing = rowCounts.map { BOARD_SIZE - it }.toIntArray()
        val columnMissing = columnCounts.map { BOARD_SIZE - it }.toIntArray()
        return InitialBoardProfile(
            occupiedCells = occupied.size,
            emptyCells = BOARD_SIZE * BOARD_SIZE - occupied.size,
            nearCompleteRows = countWithMissing(rowMissing),
            nearCompleteColumns = countWithMissing(columnMissing),
            almostCompleteRows = countWithTwoMissing(rowMissing),
            almostCompleteColumns = countWithTwoMissing(columnMissing)
        )
    }

    /**
     * Turnos que se descuentan de la curva medida por progreso casi regalado.
     *
     * **Solo cuentan las líneas a UNA celda de cerrarse (2026-08-26).** Antes también puntuaban las
     * de dos huecos, y con la holgura de línea ([LevelOpening.MAX_LINE_FILL] = 6) eso se convirtió en
     * un contrasentido: 234 de los 293 niveles abren ahora con alguna línea a exactamente dos
     * huecos, porque **dos huecos es el máximo permitido**, no una generosidad. Seguir premiándolo
     * habría devuelto en descuento (−4 turnos en casi todo el mapa) justo lo que la regla nueva
     * pretendía cobrar.
     *
     * Un tablero del mapa recibe hoy, por construcción, crédito **cero**: ya no regala nada. La
     * métrica sigue viva porque los layouts del tutorial y las partidas guardadas de antes de la
     * regla sí pueden traer líneas a una celda.
     */
    fun initialFreeProgressCredit(cells: Collection<LevelCell>): Int {
        if (cells.isEmpty()) return 0
        val profile = analyze(cells)
        return ((profile.nearCompleteRows + profile.nearCompleteColumns) / 2)
            .coerceIn(0, MAX_INITIAL_FREE_CREDIT)
    }
}
