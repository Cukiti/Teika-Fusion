package com.cukiti.teikafusion.model

/**
 * Reglas PURAS del Miasma Venenoso de Toño (habilidad de compañero, rework 2026-07-17).
 *
 * Al castear, un miasma tóxico se esparce y corroe la madera VIVA del tablero hasta que TODA la
 * marcada revienta casi a la vez (cuenta como líneas: recoge estrellas, da combo y score). Las
 * fichas que el jugador coloque DESPUÉS quedan sanas — el efecto es un disparo único sin estado
 * persistente. El nivel de habilidad escala CUÁNTA madera abarca el miasma:
 *   · L1 → las [TOXIC_COLS_L1] columnas con más madera
 *   · L2 → las [TOXIC_COLS_L2] columnas con más madera
 *   · L3 → TODO el tablero
 *
 * Extraído a `model/` para poder testear el reparto sin instanciar el ViewModel (patrón del repo).
 */
object ToxicMiasmaRules {

    const val TOXIC_COLS_L1 = 4
    const val TOXIC_COLS_L2 = 6

    /** Nº de columnas que abarca el miasma para [level] (1..3). L3 = todas ([gridSize]). */
    fun columnsForLevel(level: Int, gridSize: Int): Int = when (level.coerceIn(1, 3)) {
        1 -> TOXIC_COLS_L1
        2 -> TOXIC_COLS_L2
        else -> gridSize
    }

    /**
     * Madera que el miasma marca para corroer. Para L1/L2 elige las N columnas con MÁS madera
     * (desempate: la columna más a la izquierda); para L3 marca toda la madera. Determinista.
     *
     * @param wood posiciones de todas las celdas de madera vivas del tablero.
     */
    fun selectMiasmaCells(wood: Collection<Position>, level: Int, gridSize: Int): List<Position> {
        if (wood.isEmpty()) return emptyList()
        val nCols = columnsForLevel(level, gridSize)
        if (nCols >= gridSize) return wood.toList()
        val byCol: Map<Int, List<Position>> = wood.groupBy { it.x }
        val chosen: Set<Int> = byCol.entries
            .sortedWith(compareByDescending<Map.Entry<Int, List<Position>>> { it.value.size }.thenBy { it.key })
            .take(nCols)
            .map { it.key }
            .toSet()
        return wood.filter { it.x in chosen }
    }
}
