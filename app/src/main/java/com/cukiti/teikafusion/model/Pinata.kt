package com.cukiti.teikafusion.model

/**
 * RETIRADO 2026-10-09: helper de compatibilidad; sin activación en campaña.
 * **P-242 · LA PIÑATA** (2026-09-22, elegida por el usuario para sustituir a LA FILA PREMIADA).
 *
 * Una piñata cuelga del techo sobre una casilla y **se balancea**: cada colocación se mueve una
 * casilla a un lado, y al llegar al borde da la vuelta. Cada rotura de línea que pasa **por su
 * casilla** la golpea — su fila entera, o su columna justo cuando está encima. Al último golpe
 * **revienta** y llueven Teikas pequeños sobre el tablero. Después cuelga otra, en otra altura.
 *
 * ## Qué decide el jugador
 *
 * **Cuándo** golpearla. Su fila es un blanco quieto, pero su columna cambia cada turno, y la lluvia
 * cae alrededor de donde revienta: esperar a que esté sobre la esquina buena es la jugada. La Fila
 * premiada pagaba puntos que no se veían; esto paga cuerpos que caen, ruedan y se funden.
 *
 * ## Lo que NO es
 *
 * No bloquea nada: cuelga por encima del tablero, como una marca del suelo. Y lo que cae son T1,
 * los mismos que cría una línea: el premio acelera el MEGA, no se lo salta.
 *
 * ## El dial
 *
 * Dos golpes y cuatro Teikas en el Cristal; tres golpes y tres Teikas en el Galáctico.
 */
object Pinata {

    const val HITS_FIRST_CHAPTER = 2
    const val HITS_LAST_CHAPTER = 3
    const val DROPS_FIRST_CHAPTER = 4
    const val DROPS_LAST_CHAPTER = 3

    fun appliesTo(level: Int): Boolean = false // Retired; Surf owns this campaign slot.

    /** Golpes que aguanta la piñata en [level]. */
    fun hitsFor(level: Int): Int =
        HouseRule.dial(level, HITS_FIRST_CHAPTER, HITS_LAST_CHAPTER)
            .coerceIn(HITS_FIRST_CHAPTER, HITS_LAST_CHAPTER)

    /** Teikas que suelta al reventar en [level]. */
    fun dropsFor(level: Int): Int =
        HouseRule.dial(level, DROPS_FIRST_CHAPTER, DROPS_LAST_CHAPTER)
            .coerceIn(DROPS_LAST_CHAPTER, DROPS_FIRST_CHAPTER)

    /**
     * Dónde cuelga la piñata número [wave] de [level]. Siempre en la mitad de ARRIBA (filas 1 a 3):
     * lo que suelta tiene que caer, y desde la fila 6 no caería nada. Determinista, sin RNG.
     */
    fun cellFor(level: Int, wave: Int, width: Int = GameState.GRID_SIZE): Position? {
        if (!appliesTo(level) || width < 3) return null
        val y = 1 + (level * 3 + wave * 5).mod(3)
        val x = 1 + (level + wave * 3).mod(width - 2)
        return Position(x, y)
    }

    /** Sentido inicial del balanceo de la piñata [wave] de [level]. */
    fun startDirFor(level: Int, wave: Int): Int = if ((level + wave) % 2 == 0) 1 else -1

    /**
     * Un paso de balanceo desde [x] hacia [dir]: la nueva columna y el sentido. Rebota en los bordes
     * del tablero, así que el jugador puede contar dónde estará dentro de dos o tres turnos.
     */
    fun swing(x: Int, dir: Int, width: Int = GameState.GRID_SIZE): Pair<Int, Int> {
        val d = if (dir >= 0) 1 else -1
        val next = x + d
        return if (next !in 0 until width) (x - d) to -d else next to d
    }

    /** ¿La golpea esta pasada? Sí si alguna de las casillas que limpia es la suya. */
    fun hitBy(cell: Position?, lineCells: Set<Position>): Boolean = cell != null && cell in lineCells

    /**
     * Dónde caen los [count] Teikas al reventar en [cell]: casillas LIBRES de [blocked], en las
     * filas de la piñata y por encima, lo más cerca posible de su columna. Pueden ser menos si el
     * tablero no deja sitio — la lluvia nunca cae dentro de la madera.
     */
    fun dropCells(
        cell: Position,
        count: Int,
        blocked: Set<Position>,
        width: Int = GameState.GRID_SIZE,
    ): List<Position> {
        val candidates = (0..cell.y).flatMap { y -> (0 until width).map { x -> Position(x, y) } }
            .filter { it !in blocked }
            .sortedWith(compareBy({ kotlin.math.abs(it.x - cell.x) }, { it.y }, { it.x }))
        // Separados: dos T1 en casillas vecinas se funden al nacer, y la lluvia se leería como uno.
        val out = ArrayList<Position>()
        for (c in candidates) {
            if (out.size >= count) break
            if (out.none { kotlin.math.abs(it.x - c.x) <= 1 && kotlin.math.abs(it.y - c.y) <= 1 }) out += c
        }
        return out
    }
}
