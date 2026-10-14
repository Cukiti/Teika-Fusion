package com.cukiti.teikafusion.model

/**
 * **El magma del Volcán** (2026-09-23, pedido del usuario: el volcán «se supone que iba a ser una
 * de las más difíciles del juego y no lo está siendo»).
 *
 * Con la lava dentro del tablero, la fila que queda **justo debajo** de su superficie está demasiado
 * cerca: sus bloques ARDEN —tiemblan cada vez más— y si el jugador se queda quieto [HEAT_MS] se
 * vuelven **magma**. El reloj es de TIEMPO, no de turnos (así lo pidió el usuario): corre mientras
 * piensas y se reinicia cada vez que colocas. La lava ya no es solo un techo que baja; te mete prisa.
 *
 * Lo que cambia el magma:
 *  - Una fila que se completa con algún bloque de magma **se rompe, pero no engendra Teika ni da
 *    puntos** ([isMagmaLine]). Las estrellas que lleve se cobran igual: se liberan del bloque.
 *  - Sigue contando como ROTURA para los relojes (la lava retrocede, el bioma se calma): castigar
 *    también eso sería castigar al jugador por limpiar.
 *  - (Del 2026-09-23 al 2026-09-24 la lava prohibía completar columnas; P-262 lo retiró: el
 *    capítulo cayó al 9 % en el bot.) El 2026-09-28 se probó otra vez, ya con la lava bajando de verdad
 *    cada ciclo, y lo dejó en el 57 % (objetivo 73): se devolvió el mismo día. Las columnas se completan.
 */
object VolcanoMagma {

    /** Tiempo quieto, con la fila ardiendo, hasta que se vuelve magma. Perilla del playtest. */
    const val HEAT_MS = 6_000L

    /** La fila que arde: la primera jugable bajo la lava, o null sin lava dentro. */
    fun burningRow(lavaActive: Boolean, lavaRow: Int, height: Int = GameState.GRID_SIZE): Int? =
        if (lavaActive && lavaRow in 1 until height) lavaRow else null

    /** Una línea con algún bloque de magma: se rompe sin Teika ni puntos. */
    fun isMagmaLine(cells: Collection<Position>, grid: Map<Position, CellContent>): Boolean =
        cells.any { (grid[it] as? CellContent.Wood)?.magma == true }

    /** Bloques de [row] que el calor convierte: madera normal todavía sin fundir, ni bobinas. */
    fun forgeable(row: Int, grid: Map<Position, CellContent>, width: Int = GameState.GRID_SIZE): Set<Position> =
        (0 until width).map { Position(it, row) }.filter { p ->
            val w = grid[p] as? CellContent.Wood
            w != null && !w.magma && w.coil == 0
        }.toSet()

    /** Calor visible de 0 a 1 (el temblor crece con él). */
    fun heatFraction(heatMs: Long): Float = (heatMs.toFloat() / HEAT_MS).coerceIn(0f, 1f)
}
