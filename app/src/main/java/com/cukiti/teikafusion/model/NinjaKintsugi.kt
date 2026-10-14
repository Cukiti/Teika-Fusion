package com.cukiti.teikafusion.model

import kotlin.random.Random

/**
 * **P-323 · LA CICATRIZ DE ORO** (2026-09-26, decisión del usuario; el Dojo en Sombras estaba en ~90 %).
 *
 * Hasta hoy el Corte Ninja llegaba, se cerraba y no dejaba nada: el tablero quedaba igual que antes. Ahora
 * la reparación kintsugi —que ya sellaba la costura con oro— deja [SCARS] bloques de oro en casillas
 * VACÍAS de las dos columnas de la costura. Cada corte llena un poco el centro del tablero, que es por
 * donde pasan todas las filas.
 *
 * Nunca en una casilla inservible ni sobre un Teika, y nunca cerrando una fila o columna (sería un
 * regalo). La reparación por MEGA no deja cicatriz: el MEGA vacía el tablero entero.
 */
object NinjaKintsugi {

    /** Bloques de oro que deja cada reparación. */
    const val SCARS = 2

    /** Las dos columnas de la costura: la del corte y la de su izquierda. */
    fun seamColumns(cutCol: Int): List<Int> = listOf(cutCol - 1, cutCol)

    /**
     * Dónde caen los bloques de oro: casillas vacías de la costura que no estén en [unusable] ni cierren
     * una línea ([filled] cuenta como lleno además de la madera: los Teikas, que son comodín). Primero las
     * de más abajo (la laca de oro se asienta), barajadas dentro de cada fila. Puede devolver menos.
     */
    fun scarCells(
        grid: Map<Position, CellContent>,
        cutCol: Int,
        unusable: Set<Position>,
        filled: Set<Position>,
        random: Random,
        count: Int = SCARS,
        size: Int = GameState.GRID_SIZE,
    ): List<Position> {
        val full = HashSet<Position>(filled).apply { grid.forEach { (p, c) -> if (c.isOccupied) add(p) } }
        val candidates = (0 until size).flatMap { y -> seamColumns(cutCol).map { x -> Position(x, y) } }
            .filter { it.x in 0 until size && it !in full && it !in unusable }
            .shuffled(random)
            .sortedByDescending { it.y }
        val chosen = ArrayList<Position>(count)
        for (p in candidates) {
            if (chosen.size == count) break
            full += p
            val closesRow = (0 until size).all { x -> Position(x, p.y) in full }
            val closesCol = (0 until size).all { y -> Position(p.x, y) in full }
            if (closesRow || closesCol) { full -= p; continue }
            chosen += p
        }
        return chosen
    }

    /** El bloque de oro: madera normal con la cara de kintsugi. */
    fun scar(colorType: Int): CellContent.Wood = CellContent.Wood(colorType = colorType, kintsugi = true)
}
