package com.cukiti.teikafusion.model

/**
 * **P-294 · LA ESPADA DEL REY** — la mecánica del Trono Real desde el 2026-09-25 (decisión del usuario;
 * sustituye al Jaque, al que le faltaba el impacto de los demás biomas).
 *
 * Si dejas de romper líneas, el Rey ALZA su espada y señala una línea —la fila o columna con más
 * bloques— (aviso 1); en el segundo turno la hoja BRILLA sobre ella (aviso 2); en el tercero CAE y la
 * parte: los bloques de la línea se rompen SIN puntos y sus estrellas se pierden. Los Teikas no se
 * cortan: el golpe los lanza hacia arriba (decisión del usuario).
 *
 * **Contrajuego**: romper una línea que CRUCE la señalada —cualquier casilla suya en las líneas rotas—
 * hace que el Rey la ENVAINE, en cualquiera de los turnos de aviso (como el jaque mate del Jaque).
 * Romper otra línea solo congela el reloj ([BiomeClock]).
 */
object KingSword {

    const val NONE = -1
    /** La espada señala la fila [line]. */
    const val ROW = 0
    /** La espada señala la columna [line]. */
    const val COL = 1

    /** Las casillas de la línea señalada ([axis]/[line]); vacío si no hay espada. */
    fun cells(axis: Int, line: Int, size: Int = GameState.GRID_SIZE): List<Position> = when {
        axis == NONE || line !in 0 until size -> emptyList()
        axis == ROW -> (0 until size).map { Position(it, line) }
        else -> (0 until size).map { Position(line, it) }
    }

    /** Lo que partiría la espada en esa línea: sus bloques (los Teikas no se cortan). */
    /**
     * P-352 · LA HOJA ANCHA: la espada parte la línea señalada y su PAREJA, la de debajo (o la de la
     * derecha); en el borde, la de encima (o la de la izquierda).
     */
    fun partner(line: Int, size: Int = GameState.GRID_SIZE): Int = if (line + 1 < size) line + 1 else line - 1

    /** Todo lo que corta el golpe: la línea señalada y su pareja. */
    fun strikeCells(axis: Int, line: Int, size: Int = GameState.GRID_SIZE): List<Position> =
        cells(axis, line, size) + cells(axis, partner(line, size), size)

    /** El eje de la hoja, en casillas: justo entre la línea y su pareja (lo usa el dibujo). */
    fun midOf(line: Int, size: Int = GameState.GRID_SIZE): Float = minOf(line, partner(line, size)) + 1f

    /**
     * Bloques de la línea SEÑALADA: con esto elige el Rey. Se cuenta solo la señalada, no la pareja —si
     * no, podría apuntar a una línea vacía por tener llena la de al lado—; la pareja se suma al cortar.
     */
    fun damage(axis: Int, line: Int, grid: Map<Position, CellContent>): Int =
        cells(axis, line).count { grid[it] is CellContent.Wood }

    /** Las 16 líneas posibles de un tablero 8×8: cada fila y cada columna. */
    fun allLines(size: Int = GameState.GRID_SIZE): List<Pair<Int, Int>> =
        (0 until size).flatMap { l -> listOf(ROW to l, COL to l) }

    /**
     * La línea que el Rey señala: la que más bloques partiría. Empates → la primera de [order], que el
     * llamador baraja para que no sea siempre la misma. ([NONE] to -1) si no hay ni un bloque.
     */
    fun choose(
        grid: Map<Position, CellContent>,
        order: List<Pair<Int, Int>> = allLines(),
    ): Pair<Int, Int> {
        var best = NONE to -1
        var bestDamage = 0
        for ((axis, line) in order) {
            val d = damage(axis, line, grid)
            if (d > bestDamage) { best = axis to line; bestDamage = d }
        }
        return best
    }

    /** ¿Alguna de las casillas rotas este turno está en la línea señalada? El Rey envaina. */
    fun sheathedBy(axis: Int, line: Int, clearedCells: Collection<Position>): Boolean {
        if (axis == NONE) return false
        val target = cells(axis, line).toSet()
        return clearedCells.any { it in target }
    }
}
