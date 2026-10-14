package com.cukiti.teikafusion.model

/**
 * **LA FICHA QUE CRECE** (P-77, 2026-08-30) — guardarla es una apuesta.
 *
 * Una ficha marcada que **no uses en este turno gana un bloque**, hasta un tope. Colocarla ahora es
 * lo seguro; dejarla en la bandeja la hace más grande, y una ficha más grande cierra más línea.
 *
 * ## Por qué esto es una decisión y no un regalo
 *
 * Porque **el juego ya te castiga por no romper nada**. Los catorce biomas comparten la misma forma
 * —contador de colocaciones ociosas → aviso → castigo— y esta ficha te pide justo eso: pasar turnos
 * sin resolver. Guardarla no es «esperar gratis», es correr contra el reloj del bioma.
 *
 * Esa tensión es la razón de que crezca **sola** y no «alimentándola» con líneas rotas, que era la
 * otra variante que la propuesta dejaba abierta. Alimentarla habría premiado lo que el juego ya
 * premia y la ficha no habría añadido ninguna decisión nueva.
 *
 * ## El peligro real, y cómo se acota
 *
 * `PieceTrayPlanner` garantiza que existe una colocación para cada ficha **en el momento de
 * repartirla** — no dos turnos después. Una ficha que engorda puede dejar de caber, y una bandeja
 * sin jugada posible es una derrota por culpa del repartidor, que es exactamente el fallo que costó
 * los 14 niveles del damero (P-53).
 *
 * Por eso el crecimiento es **condicional**: [grow] devuelve la forma nueva solo si el llamante
 * confirma que sigue cabiendo en el tablero de ese momento. Si no cabe, la ficha **deja de crecer**
 * y se queda como está. Nunca se cambia una ficha jugable por una que no lo es.
 *
 * ## Dónde crece el bloque nuevo
 *
 * En la primera casilla libre pegada a la forma, recorriendo en orden fijo (arriba, izquierda,
 * derecha, abajo, por celdas ordenadas). **Determinista a propósito**: el jugador tiene que poder
 * PREVER en qué se va a convertir su ficha, o guardarla no sería una apuesta sino una lotería.
 */
object GrowingPiece {

    /** Tope de tamaño. Cinco celdas es el mayor polinomio «cómodo» del catálogo (I5, L5, U, cruz, W). */
    const val MAX_CELLS = 5

    /** Cada cuántas reposiciones de bandeja se marca una ficha para que crezca. */
    const val EVERY_REFILLS = 5

    /** ¿Toca ficha que crece en esta reposición? Mismo contador persistido que la Brocha. */
    fun onRefill(refillCount: Int): Boolean =
        refillCount > 0 && refillCount % EVERY_REFILLS == 0

    /**
     * A qué ficha se le da: **la más pequeña**.
     *
     * Es la que más margen tiene antes del tope de [MAX_CELLS] —una de dos celdas puede crecer tres
     * veces, una de cuatro solo una— así que es donde la apuesta dura más turnos y de verdad se
     * siente. Devuelve el índice, o -1 si no hay fichas.
     */
    fun targetIndex(sizes: List<Int>): Int {
        if (sizes.isEmpty()) return -1
        var best = 0
        for (i in sizes.indices) if (sizes[i] < sizes[best]) best = i
        return best
    }

    /** ¿Puede esta forma crecer todavía? */
    fun canGrow(shape: List<Position>): Boolean = shape.size < MAX_CELLS

    /**
     * La forma con **un bloque más**, o `null` si ya está en el tope.
     *
     * El candidato sale de recorrer las celdas en orden y probar sus cuatro vecinos en orden fijo:
     * mismo resultado siempre para la misma forma. El resultado va **normalizado** a la esquina
     * (0,0), como todas las formas del catálogo, para que `rotate()` y el dibujo se comporten igual.
     */
    fun grow(shape: List<Position>): List<Position>? {
        if (!canGrow(shape)) return null
        val cells = shape.toHashSet()
        val ordered = shape.sortedWith(compareBy({ it.y }, { it.x }))
        for (cell in ordered) {
            // Arriba, izquierda, derecha, abajo: el orden fija el resultado y lo hace previsible.
            val vecinos = listOf(
                Position(cell.x, cell.y - 1),
                Position(cell.x - 1, cell.y),
                Position(cell.x + 1, cell.y),
                Position(cell.x, cell.y + 1),
            )
            for (v in vecinos) {
                if (v in cells) continue
                return normalize(shape + v)
            }
        }
        return null
    }

    /** Lleva la forma a la esquina (0,0) y la ordena, como el catálogo de `Piece.SHAPES`. */
    fun normalize(shape: List<Position>): List<Position> {
        val minX = shape.minOf { it.x }
        val minY = shape.minOf { it.y }
        return shape
            .map { Position(it.x - minX, it.y - minY) }
            .sortedWith(compareBy({ it.y }, { it.x }))
    }
}
