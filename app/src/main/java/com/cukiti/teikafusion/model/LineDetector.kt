package com.cukiti.teikafusion.model

/**
 * **P-446 · QUÉ LÍNEAS ESTÁN COMPLETAS, ESCRITO UNA SOLA VEZ** (2026-10-01).
 *
 * El motor (`GameViewModel.resolveLineClearPass`) y la vista previa del arrastre
 * (`GameViewModel.previewClearLines`) llevaban cada uno su copia de «casilla llena», «madera que se
 * puede romper», «la bobina bloquea la línea» y del propio barrido de filas y columnas. Es la
 * trampa de este repositorio («dos copias de un dibujo»): la Tormenta ya se había separado así
 * ([StormCircuit]). Ahora los dos llaman a estas funciones, y `LineDetectorTest` comprueba en miles
 * de tableros aleatorios que la vista previa (ficha «superpuesta») y el motor (ficha ya estampada)
 * ven exactamente las mismas líneas.
 *
 * Lo único que el motor añade y la vista previa no puede ver son estados que solo existen A MITAD de
 * la cadena (la madera que Rayco atrae en vuelo y la ya lavada por el Tóxico en esta jugada): entran
 * por `extraUnclearable` / `extraLineBlock`, vacíos al arrastrar.
 */
object LineDetector {

    /** Filas (y → tramo de x; con el corte ninja, medias filas) y columnas completas. */
    data class Lines(val rows: List<Pair<Int, IntRange>>, val cols: List<Int>)

    /**
     * Casillas que cuentan como llenas sin ser madera ni romperse: lo devorado por el Glotón, lo
     * cubierto por la lava, el huevo (P-235) y la pieza del Rey que se quedó. El cúmulo galáctico
     * NO entra: bloquea la línea, no la rellena.
     */
    fun fixedCells(
        gluttonDead: Set<Position>,
        lavaCovered: Set<Position>,
        egg: Position?,
        kingRest: Position?,
    ): Set<Position> = gluttonDead + lavaCovered + setOfNotNull(egg, kingRest)

    /**
     * «Casilla llena» para cerrar una línea: madera, un Teika (comodín, congelados incluidos, P-254)
     * o una casilla fija. [stamped] = la ficha de la vista previa, que aún no está en [grid].
     */
    fun filled(
        grid: Map<Position, CellContent>,
        teikaCells: Set<Position>,
        fixed: Set<Position>,
        stamped: Set<Position> = emptySet(),
    ): (Position) -> Boolean = { p ->
        p in stamped || grid[p] is CellContent.Wood || p in teikaCells || p in fixed
    }

    /**
     * ¿Esta madera se rompería con su línea? No: la bobina, el cristal (armor), lo congelado o con
     * escarcha, el testarudo (P-71: si no, una línea de puros testarudos se daría por rota sin cambiar
     * nada y la cadena entraría en bucle) y lo que está oculto en vuelo.
     */
    fun woodClearable(
        w: CellContent.Wood,
        p: Position,
        frozen: Set<Position>,
        frost: Set<Position>,
        hidden: Set<Position>,
        extraUnclearable: Set<Position> = emptySet(),
    ): Boolean =
        !w.stubborn && w.coil == 0 && w.armor == 0 &&
            p !in frozen && p !in frost && p !in hidden && p !in extraUnclearable

    /**
     * [woodClearable] sobre una casilla. La ficha de la vista previa ([stamped]) se trata como la
     * madera LISA que será al estamparse — el mismo trato que le dará el motor.
     */
    fun clearable(
        grid: Map<Position, CellContent>,
        frozen: Set<Position>,
        frost: Set<Position>,
        hidden: Set<Position>,
        stamped: Set<Position> = emptySet(),
        extraUnclearable: Set<Position> = emptySet(),
    ): (Position) -> Boolean = { p ->
        if (p in stamped) {
            p !in frozen && p !in frost && p !in hidden && p !in extraUnclearable
        } else {
            (grid[p] as? CellContent.Wood)
                ?.let { woodClearable(it, p, frozen, frost, hidden, extraUnclearable) } ?: false
        }
    }

    /**
     * ¿Algo impide que la línea se rompa aunque esté llena? Una bobina sin descargar (Tormenta) o una
     * casilla oculta en vuelo (más [extraLineBlock] del motor).
     */
    fun lineBlocked(
        grid: Map<Position, CellContent>,
        hidden: Set<Position>,
        extraLineBlock: Set<Position> = emptySet(),
    ): (List<Position>) -> Boolean = { cells ->
        cells.any { p ->
            (grid[p] as? CellContent.Wood)?.let { it.coil > 0 } == true || p in hidden || p in extraLineBlock
        }
    }

    /**
     * El barrido: una línea está completa si todas sus casillas están llenas, nada la bloquea y al
     * menos una casilla se rompería de verdad. Las filas van por los tramos de [rowSpans] (con el
     * corte ninja, mitades) y además deben pasar [halfRowAllowed]. Con [mustTouch] (la vista previa)
     * solo cuentan las líneas que contienen alguna casilla de la ficha: el aviso telegrafía lo que
     * TU colocación rompe.
     */
    fun detect(
        width: Int,
        height: Int,
        filled: (Position) -> Boolean,
        clearable: (Position) -> Boolean,
        lineBlocked: (List<Position>) -> Boolean,
        rowSpans: List<IntRange>,
        halfRowAllowed: (List<Position>) -> Boolean,
        mustTouch: Set<Position>? = null,
    ): Lines {
        fun complete(cells: List<Position>): Boolean =
            (mustTouch == null || cells.any { it in mustTouch }) &&
                cells.all(filled) && !lineBlocked(cells) && cells.any(clearable)
        val rows = (0 until height).flatMap { y ->
            rowSpans.mapNotNull { span ->
                val cells = span.map { x -> Position(x, y) }
                if (complete(cells) && halfRowAllowed(cells)) y to span else null
            }
        }
        val cols = (0 until width).filter { x -> complete((0 until height).map { y -> Position(x, y) }) }
        return Lines(rows, cols)
    }
}
