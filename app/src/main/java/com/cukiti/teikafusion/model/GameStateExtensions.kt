package com.cukiti.teikafusion.model

/** Returns all unique Teika instances on the grid, deduplicated by id. */
fun GameState.allTeikas(): List<CellContent.Teika> =
    grid.values.filterIsInstance<CellContent.Teika>().distinctBy { it.id }

/**
 * ¿La LAVA está activa en este estado? Fuente ÚNICA de la pregunta. Desde el rework de la fase 3
 * (2026-07-24) la lava es **exclusiva del bioma Volcán**: el Infinito dejó de tomar prestadas las
 * mecánicas de los niveles y tiene su propia amenaza, la MAREA. Todo lo que dependa de la lava
 * —overlay, celdas cubiertas, retroceso al romper filas, presión idle— debe preguntar aquí.
 */
val GameState.lavaActive: Boolean
    get() = BiomeType.VOLCANO == biomePrimary

/**
 * Returns a copy of this GameState with the grid mutated by [block].
 * Usage: state.withGrid { this[pos] = CellContent.Empty }
 */
inline fun GameState.withGrid(block: MutableMap<Position, CellContent>.() -> Unit): GameState {
    val newGrid = grid.toMutableMap().apply(block)
    return copy(grid = newGrid)
}
