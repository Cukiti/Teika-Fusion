package com.cukiti.teikafusion.model

import kotlin.random.Random

/**
 * **P-324 · LA ENREDADERA DEL JARDÍN** (2026-09-26, decisión del usuario; el Jardín estaba en ~90 % con
 * un objetivo de 78 %).
 *
 * El castigo del Jardín AYUDABA por tres lados: el arrastre trituraba la madera de su camino (casillas
 * gratis), contaba como recogidas las estrellas que trituraba, y perder el Teika abría sitio. Además, sin
 * Teikas en el tablero —lo normal desde que nacen solo de los bloques-Teika— la raíz no hacía nada.
 *
 *  - **Enredadera en el camino** ([wakeCells]): por donde la raíz arrastra al Teika brota ENREDADERA —
 *    madera verde que ocupa su casilla, sin estrella ni puntos; una línea la rompe—. La madera que el
 *    cuerpo aplasta ya no regala su estrella. Al llevárselo, su último sitio también se enreda.
 *  - **Invasión sin Teika** ([invasionCells]): si el reloj llega al tercer turno y no hay Teika que
 *    agarrar, brotan [INVASION] enredaderas desde un borde, en casillas vacías.
 *
 * Una enredadera nueva en una casilla VACÍA nunca cierra una fila o columna (sería un regalo).
 */
object GardenVine {

    /** Enredaderas que brotan de un borde cuando no hay Teika que agarrar. */
    const val INVASION = 3   // P-352: 3 → 4 · P-359: 4 → 3 (Jardín 63 %, objetivo 78 %)

    /** Lo que deja atrás el arrastre: lo que tapaba el cuerpo en [from] y ya no tapa en [to]. */
    fun wakeCells(from: Position, to: Position, type: TeikaType, size: Int = GameState.GRID_SIZE): Set<Position> =
        (plantFootprint(from, type) - plantFootprint(to, type))
            .filterTo(HashSet()) { it.x in 0 until size && it.y in 0 until size }

    /**
     * Qué casillas de [candidates] se enredan: la madera que no esté ya enredada, y las VACÍAS que no estén
     * en [unusable] ni cierren una línea ([filled] cuenta como lleno además de la madera: los Teikas).
     */
    fun vineTargets(
        candidates: Collection<Position>,
        grid: Map<Position, CellContent>,
        unusable: Set<Position>,
        filled: Set<Position>,
        size: Int = GameState.GRID_SIZE,
    ): List<Position> {
        val full = HashSet<Position>(filled).apply { grid.forEach { (p, c) -> if (c.isOccupied) add(p) } }
        val out = ArrayList<Position>()
        for (p in candidates.sortedWith(compareBy({ it.y }, { it.x }))) {
            if (p in unusable) continue
            when (val c = grid[p]) {
                is CellContent.Wood -> if (!c.vine) out += p
                null, CellContent.Empty -> {
                    if (p in full) continue
                    full += p
                    val closes = (0 until size).all { x -> Position(x, p.y) in full } ||
                        (0 until size).all { y -> Position(p.x, y) in full }
                    if (closes) full -= p else out += p
                }
                else -> {}
            }
        }
        return out
    }

    /**
     * La invasión: [count] casillas VACÍAS pegadas al borde [edge] (primero la fila o columna del borde,
     * luego la siguiente), barajadas, sin [unusable] y sin cerrar líneas.
     */
    fun invasionCells(
        edge: PlantEdge,
        grid: Map<Position, CellContent>,
        unusable: Set<Position>,
        filled: Set<Position>,
        random: Random,
        count: Int = INVASION,
        size: Int = GameState.GRID_SIZE,
    ): List<Position> {
        fun depth(p: Position) = when (edge) {
            PlantEdge.TOP -> p.y
            PlantEdge.BOTTOM -> size - 1 - p.y
            PlantEdge.LEFT -> p.x
            PlantEdge.RIGHT -> size - 1 - p.x
        }
        val empty = (0 until size).flatMap { y -> (0 until size).map { x -> Position(x, y) } }
            .filter { depth(it) <= 1 && grid[it]?.isOccupied != true && it !in filled && it !in unusable }
            .shuffled(random)
            .sortedBy(::depth)
        return vineTargets(empty, grid, unusable, filled, size).let { ok ->
            // `vineTargets` ordena por posición; aquí manda el orden barajado por profundidad.
            empty.filter { it in ok.toSet() }.take(count)
        }
    }

    /** La enredadera: conserva el color de la madera que cubre (o [colorType] si la casilla estaba vacía). */
    fun vine(over: CellContent?, colorType: Int): CellContent.Wood =
        (over as? CellContent.Wood)?.copy(vine = true, targetColor = null, poisoned = false)
            ?: CellContent.Wood(colorType = colorType, vine = true)
}
