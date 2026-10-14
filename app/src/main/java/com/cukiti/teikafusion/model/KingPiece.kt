package com.cukiti.teikafusion.model

import kotlin.random.Random

/**
 * **P-329 · LAS PIEZAS DEL REY** (2026-09-26, decisión del usuario) — el Trono Real alterna dos
 * castigos en el MISMO reloj: un golpe es la espada ([KingSword]) y el siguiente una pieza de ajedrez.
 * Cada golpe de pieza trae UNA sola, al azar entre torre, alfil y caballo (ni rey, ni reina, ni
 * peones), y su color alterna blanca/negra de un golpe al siguiente.
 *
 *  - **Torre**: entra por un extremo de una fila o columna (al azar, con más peso donde más madera
 *    hay) y la arrasa.
 *  - **Alfil**: igual, por una diagonal de al menos [MIN_DIAGONAL] casillas.
 *  - **Caballo**: salta en L y cae sobre una casilla; aplasta el 3×3 que la rodea.
 *
 * Lo que la pieza arrasa se rompe SIN puntos y sus estrellas se pierden, como con la espada; los
 * Teikas salen lanzados hacia arriba. Después, **la pieza se queda** en su casilla de llegada
 * ([landingCell]) y bloquea lo que ataca como pieza de ajedrez ([restLane]) hasta que el jugador rompa
 * su fila o su columna (2026-09-28). Si ese bloqueo dejara la bandeja sin jugada, la calle se abre
 * pero la pieza sigue: un castigo nunca puede costar la partida.
 *
 * **Contrajuego** ([parriedBy], 2026-09-28): en el turno del golpe, romper una línea que cruce su casilla
 * de llegada (la diana del aviso) hace que la pieza se RETIRE. Después, echarla rompiendo su fila o columna.
 *
 * El objetivo se guarda como (`kind`, `a`, `b`): torre = eje/índice ([KingSword.ROW]/[KingSword.COL]),
 * alfil = dirección/constante ([DIAG_DOWN]: x−y=b, [DIAG_UP]: x+y=b), caballo = casilla (x, y).
 */
object KingPiece {

    const val NONE = -1
    const val ROOK = 0
    const val BISHOP = 1
    const val KNIGHT = 2

    val KINDS = listOf(ROOK, BISHOP, KNIGHT)

    /**
     * Colocaciones que el camino arrasado queda BLOQUEADO (2026-09-27, decisión del usuario: 1 → 2,
     * «que el castigo se sienta al menos dos turnos»). Nunca a costa de dejar la bandeja sin jugada.
     */
    const val LANE_TURNS = 5   // P-352: 2 → 3 · P-360 (2026-09-28): 3 → 5 (Trono 88 % → 69 %; con 7 subió a 86 %: una calle larga
    // hace que el reparto dé fichas más pequeñas y el bot sobreviva más)

    /**
     * Piezas por cada espada (2026-09-28, decisión del usuario: 1 → 2). El reloj ya no alterna uno y uno:
     * pieza, pieza, espada. Medido con el bot, la pieza golpeaba 1,6 veces por partida frente a 4,6 de la
     * espada y el Trono seguía en el 76 % (objetivo 57): lo que duele es la calle bloqueada, no el tajo.
     * Si hay una pieza quieta en el tablero, su turno lo sigue dando la espada (nunca dos piezas).
     */
    const val PIECES_PER_SWORD = 2

    /** ¿Tras [piecesInRow] piezas seguidas, le toca otra pieza? Si no, le toca a la espada. */
    fun pieceTurnAfter(piecesInRow: Int, perSword: Int = PIECES_PER_SWORD): Boolean = piecesInRow < perSword

    /** Diagonal «\»: casillas con x − y = b (b en −(n−1)..n−1). */
    const val DIAG_DOWN = 0
    /** Diagonal «/»: casillas con x + y = b (b en 0..2n−2). */
    const val DIAG_UP = 1

    /** Diagonal más corta que un alfil recorre: en las esquinas no hay camino que merezca el golpe. */
    const val MIN_DIAGONAL = 4

    /**
     * En qué % del golpe cae de verdad la pieza: fin de la cámara lenta. Lo comparten el motor (vacía
     * las casillas y lanza los Teikas en ese instante) y el VFX (estalla en ese instante). Entrada
     * rápida 0–[SLOWMO_FROM_PCT] %, cámara lenta hasta aquí, consecuencia rápida hasta el 100 %.
     */
    const val IMPACT_PCT = 62
    const val SLOWMO_FROM_PCT = 18

    /** Las casillas que la pieza arrasa (y luego bloquea). Vacío si no hay pieza o el objetivo no vale. */
    fun cells(kind: Int, a: Int, b: Int, size: Int = GameState.GRID_SIZE): List<Position> = when (kind) {
        ROOK -> KingSword.cells(a, b, size)
        BISHOP -> when (a) {
            DIAG_DOWN -> (0 until size).mapNotNull { x -> (x - b).takeIf { it in 0 until size }?.let { Position(x, it) } }
            DIAG_UP -> (0 until size).mapNotNull { x -> (b - x).takeIf { it in 0 until size }?.let { Position(x, it) } }
            else -> emptyList()
        }
        KNIGHT -> if (a !in 0 until size || b !in 0 until size) emptyList() else
            (-1..1).flatMap { dy -> (-1..1).map { dx -> Position(a + dx, b + dy) } }
                .filter { it.x in 0 until size && it.y in 0 until size }
        else -> emptyList()
    }

    /** Todos los objetivos posibles de [kind] en un tablero de [size]. */
    fun targets(kind: Int, size: Int = GameState.GRID_SIZE): List<Pair<Int, Int>> = when (kind) {
        ROOK -> KingSword.allLines(size)
        BISHOP -> {
            val reach = size - MIN_DIAGONAL
            (-reach..reach).map { DIAG_DOWN to it } +
                (MIN_DIAGONAL - 1..2 * size - 1 - MIN_DIAGONAL).map { DIAG_UP to it }
        }
        KNIGHT -> (0 until size).flatMap { y -> (0 until size).map { x -> x to y } }
        else -> emptyList()
    }

    /** Lo que arrasaría: los bloques de su camino (los Teikas no se rompen, salen lanzados). */
    fun damage(kind: Int, a: Int, b: Int, grid: Map<Position, CellContent>): Int =
        cells(kind, a, b).count { grid[it] is CellContent.Wood }

    /**
     * El objetivo de la pieza: **al azar, con peso = bloques que arrasaría** (el usuario pidió que el
     * posicionamiento fuera aleatorio; el peso evita que la pieza gaste el golpe en una calle vacía
     * habiendo madera). `null` si no hay ni un bloque a su alcance.
     */
    fun choose(kind: Int, grid: Map<Position, CellContent>, rng: Random): Pair<Int, Int>? {
        val weighted = targets(kind).map { it to damage(kind, it.first, it.second, grid) }.filter { it.second > 0 }
        val total = weighted.sumOf { it.second }
        if (total == 0) return null
        var roll = rng.nextInt(total)
        for ((target, w) in weighted) {
            if (roll < w) return target
            roll -= w
        }
        return weighted.last().first
    }

    // ── LA PIEZA SE QUEDA (2026-09-28, decisión del usuario) ─────────────────────────────────────
    // Tras el golpe, la pieza se queda EN el tablero, en su casilla de llegada, y bloquea lo que
    // ATACA como pieza de ajedrez: la torre su fila o columna (la que acaba de recorrer), el alfil su
    // diagonal, el caballo sus saltos en L. Se retira cuando el jugador rompe la FILA o la COLUMNA de
    // la pieza; su casilla cuenta como llena para cerrar esa línea (como el huevo). Por construcción,
    // lo que bloquea nunca cae en su propia fila Y columna a la vez: siempre hay una línea para echarla.

    /** Los ocho saltos del caballo. */
    private val KNIGHT_JUMPS = listOf(1 to 2, 2 to 1, 2 to -1, 1 to -2, -1 to -2, -2 to -1, -2 to 1, -1 to 2)

    /**
     * Dónde se queda la pieza tras el golpe: la torre y el alfil en el extremo de su camino por el que
     * SALEN (el contrario a [fromEnd]); el caballo en la casilla donde cae. `null` si no hay pieza.
     */
    fun landingCell(kind: Int, a: Int, b: Int, fromEnd: Boolean, size: Int = GameState.GRID_SIZE): Position? {
        if (kind == KNIGHT) return Position(a, b).takeIf { a in 0 until size && b in 0 until size }
        val path = cells(kind, a, b, size)
        if (path.isEmpty()) return null
        return if (fromEnd) path.first() else path.last()
    }

    /**
     * Lo que la pieza quieta en [at] ATACA, sin su propia casilla: torre y alfil, la calle que
     * recorrieron ([kind]/[a]/[b]); el caballo, las casillas a un salto de L.
     */
    fun restLane(kind: Int, a: Int, b: Int, at: Position, size: Int = GameState.GRID_SIZE): Set<Position> = when (kind) {
        KNIGHT -> KNIGHT_JUMPS.map { (dx, dy) -> Position(at.x + dx, at.y + dy) }
            .filterTo(HashSet()) { it.x in 0 until size && it.y in 0 until size }
        else -> cells(kind, a, b, size).filterTo(HashSet()) { it != at }
    }

    /** ¿Se rompió la fila o la columna de la pieza quieta en [at]? Entonces se retira. */
    fun restFreedBy(at: Position?, clearedRows: Collection<Int>, clearedCols: Collection<Int>): Boolean =
        at != null && (at.y in clearedRows || at.x in clearedCols)

    /**
     * **La parada de la pieza** (2026-09-28, decisión del usuario): en el turno del golpe, la pieza se
     * retira solo si una línea rota cruza su CASILLA DE LLEGADA ([landingCell], la diana del aviso). Antes
     * valía cualquier línea que pisara su camino —la fila entera de la torre, su diagonal, el 3×3 del
     * caballo— y el bot la paraba sin querer: de 3,9 piezas por partida solo 1,7 llegaban a golpear.
     */
    fun parriedBy(kind: Int, a: Int, b: Int, fromEnd: Boolean, clearedRows: Collection<Int>, clearedCols: Collection<Int>): Boolean {
        if (kind == NONE) return false
        return restFreedBy(landingCell(kind, a, b, fromEnd), clearedRows, clearedCols)
    }

    /**
     * De dónde sale la pieza, en casillas (puede quedar FUERA del tablero): la torre y el alfil por el
     * extremo de su camino que indica [fromEnd] (false = el primero de [cells]); el caballo, a un salto
     * de L de su casilla, hacia el lado del tablero con más sitio para verlo volar.
     */
    fun origin(kind: Int, a: Int, b: Int, fromEnd: Boolean, size: Int = GameState.GRID_SIZE): Pair<Float, Float> {
        val path = cells(kind, a, b, size)
        if (path.isEmpty()) return 0f to 0f
        return when (kind) {
            ROOK, BISHOP -> {
                val first = if (fromEnd) path.last() else path.first()
                val next = if (fromEnd) path[path.size - 2] else path[1]
                // Una casilla más allá del extremo, en la dirección contraria al camino: entra desde fuera.
                (first.x + (first.x - next.x)).toFloat() to (first.y + (first.y - next.y)).toFloat()
            }
            else -> {
                val dx = if (a < size / 2) 1 else -1
                val dy = if (b < size / 2) 2 else -2
                (a + dx).toFloat() to (b + dy).toFloat()
            }
        }
    }
}
