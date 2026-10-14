package com.cukiti.teikafusion.model

/**
 * Una huida por colocación del Pasaje Encantado. Nunca mueve la ficha recién puesta.
 *
 * **P-262 (2026-09-24, decisión del usuario; bot: Miedo al 18 %, capítulo 3).** Antes huía el bloque
 * MÁS CERCANO a la ficha y ANTES de resolver las líneas, así que casi siempre salía de la fila o la
 * columna que esa misma jugada cerraba: la jugada buena se deshacía en el mismo turno. Ahora:
 *  - huye DESPUÉS de la cadena (lo decide `GameViewModel`): tus líneas ya se rompieron;
 *  - huye el bloque MÁS LEJANO a la ficha: el susto desordena el tablero, no la jugada que haces;
 *  - y nunca uno de [protectedLines] (filas/columnas que esta jugada completa), por si alguien vuelve
 *    a llamarla antes de la cadena.
 *
 * **P-312 (2026-09-26, decisión del usuario; bot: Pasaje al 58 % frente a un objetivo del 80 %).** El
 * susto RESPETA LO CASI HECHO: no huye ningún bloque de una fila o columna a la que le falten
 * [NEAR_COMPLETE_GAP] casillas o menos. Sigue huyendo uno en cada colocación (eso es decisión
 * congelada); lo que ya no hace es deshacerte la línea que estabas a punto de cerrar.
 */
internal fun fearFleeMove(
    grid: Map<Position, CellContent>,
    footprint: Set<Position>,
    blocked: Set<Position>,
    protected: Set<Position>,
    width: Int,
    height: Int,
    /** Celdas de las líneas que esta colocación completa: no se tocan. */
    protectedLines: Set<Position> = emptySet(),
): Pair<Position, Position>? {
    if (footprint.isEmpty()) return null
    val nearComplete = nearCompleteLineCells(grid, blocked, width, height)
    val cx = footprint.sumOf { it.x }.toFloat() / footprint.size
    val cy = footprint.sumOf { it.y }.toFloat() / footprint.size
    val candidates = grid.keys.filter { p ->
        val wood = grid[p] as? CellContent.Wood
        p !in footprint && p !in protected && p !in blocked && p !in protectedLines && p !in nearComplete &&
            wood != null && wood.armor == 0 && wood.coil == 0
    }.sortedWith(compareByDescending<Position> { (it.x - cx) * (it.x - cx) + (it.y - cy) * (it.y - cy) }
        .thenBy { it.y }.thenBy { it.x })
    for (from in candidates) {
        val dx = if (from.x > cx) 1 else if (from.x < cx) -1 else 0
        val dy = if (from.y > cy) 1 else if (from.y < cy) -1 else 0
        val preferred = listOf(Position(from.x + dx, from.y + dy),
            Position(from.x + dx, from.y), Position(from.x, from.y + dy))
        val neighbors = (-1..1).flatMap { y -> (-1..1).map { x -> Position(from.x + x, from.y + y) } }
        val to = (preferred + neighbors).firstOrNull { p ->
            p != from && p.x in 0 until width && p.y in 0 until height &&
                p !in footprint && p !in blocked && p !in protected &&
                (grid[p] ?: CellContent.Empty) is CellContent.Empty
        }
        if (to != null) return from to to
    }
    return null
}

/** Casillas que le pueden faltar a una línea para que el susto no la toque (P-312). */
internal const val NEAR_COMPLETE_GAP = 2

/**
 * Las casillas de toda fila o columna a la que le faltan entre 1 y [NEAR_COMPLETE_GAP] casillas. Cuenta
 * como llena la madera y lo que ya estorba ([blocked]: cuerpos de Teika, pozos, lava…).
 */
internal fun nearCompleteLineCells(
    grid: Map<Position, CellContent>,
    blocked: Set<Position>,
    width: Int,
    height: Int,
): Set<Position> {
    fun filled(p: Position) = (grid[p]?.isOccupied == true) || p in blocked
    val out = HashSet<Position>()
    for (y in 0 until height) {
        val cells = (0 until width).map { Position(it, y) }
        if (cells.count { !filled(it) } in 1..NEAR_COMPLETE_GAP) out += cells
    }
    for (x in 0 until width) {
        val cells = (0 until height).map { Position(x, it) }
        if (cells.count { !filled(it) } in 1..NEAR_COMPLETE_GAP) out += cells
    }
    return out
}

/**
 * P-360 (2026-09-28): el Pasaje asusta cada tantas colocaciones (antes, en todas). Pasaje 61 %, objetivo 80 %.
 * 2 → 3 el mismo día (decisión del usuario: «subir un poco»; 74–79 % con objetivo 80 %).
 */
const val FEAR_EVERY = 3
