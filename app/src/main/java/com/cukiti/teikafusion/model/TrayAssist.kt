package com.cukiti.teikafusion.model

/**
 * **LA RANURA QUE AYUDA** (P-204, 2026-09-06, aprobada por el usuario).
 *
 * Una ficha de la bandeja tira hacia los huecos de las líneas que están a punto de cerrarse. Sin esto,
 * con una fila a un hueco y ese hueco con forma de L, la bandeja podía pasarse cinco turnos sin ofrecer
 * una sola L: el jugador veía la jugada y el repartidor no se la daba nunca. Desde el reparto con pesos
 * (P-255, 2026-09-23) es además **el premio a jugar bien**: quien prepara líneas recibe con más
 * frecuencia la ficha que las cierra (`PieceTrayPlanner.NOT_HELPFUL_IN_HELP_SLOT`).
 *
 * **Una sola ranura, a propósito**: si las tres fichas tiraran a cerrar líneas, el nivel lo resolvería
 * el repartidor y no el jugador.
 *
 * (RETIRADO 2026-09-23) **P-205 · LA FICHA DE RESCATE**: con el tablero apretado, una ficha salía
 * pequeña pase lo que pase. Era una red para que el reparto nunca dejara sin jugada, y el usuario
 * eligió lo contrario: azar con pesos, donde jugar mal puede atascar la partida.
 */
object TrayAssist {

    /**
     * Cuántas celdas puede faltarle a una línea para que la bandeja intente cerrarla.
     *
     * **Dos.** Con una sola celda que falte, la ayuda sería un regalo —el jugador ya tiene la jugada
     * y solo le falta cualquier monominó—; con tres o más deja de ser «esta línea está a punto» y
     * pasa a ser media pizarra. Dos es la distancia a la que el jugador **ve** la línea y necesita la
     * ficha correcta.
     */
    const val MAX_GAP = 2

    /**
     * Cuántos huecos útiles se le cuentan como mucho a una colocación.
     *
     * El tope existe para que la ayuda no se convierta en «la ficha que barre el tablero»: tres
     * celdas útiles ya son una línea cerrada o dos a medias.
     */
    const val HELPFUL_BONUS_CELLS_CAP = 3

    /**
     * Celdas LIBRES que pertenecen a una fila o columna a la que le faltan entre 1 y [MAX_GAP]
     * celdas para completarse.
     *
     * @param filled celdas que cuentan como llenas **para una línea**. Es una lista más corta que
     *   «lo que estorba»: madera, comodines de Teika, muros del Rey, rincones del Glotón y lava.
     *   Un cuerpo de Teika que solo BLOQUEA (congelado) o una celda de color vacía estorban pero no
     *   completan, y contarlas aquí haría que la bandeja persiguiera líneas que no se van a cerrar.
     * @param unusable celdas donde el jugador **no puede** colocar (cuerpos de Teika, marcas de
     *   color, lava…). Si a una línea le falta una de estas, ninguna ficha la va a cerrar: la línea
     *   entera se descarta en vez de mandar la bandeja a un sitio sin premio.
     */
    fun lineGaps(
        filled: Set<Position>,
        unusable: Set<Position>,
        size: Int = GameState.GRID_SIZE,
    ): Set<Position> {
        val gaps = HashSet<Position>()
        fun scan(cells: List<Position>) {
            val missing = cells.filterNot { it in filled }
            if (missing.isEmpty() || missing.size > MAX_GAP) return
            if (missing.any { it in unusable }) return
            gaps.addAll(missing)
        }
        for (y in 0 until size) scan((0 until size).map { x -> Position(x, y) })
        for (x in 0 until size) scan((0 until size).map { y -> Position(x, y) })
        return gaps
    }
}
