package com.cukiti.teikafusion.bot

import com.cukiti.teikafusion.model.CellContent
import com.cukiti.teikafusion.model.ForcedQueue
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.model.Piece
import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.model.TutorialCampaign
import com.cukiti.teikafusion.viewmodel.GameViewModel

/**
 * La estrategia del bot: un jugador COMPETENTE que **planifica la bandeja entera**, como hace quien
 * juega bien a Block Blast — no «¿dónde pongo esta?» sino «¿en qué orden y dónde pongo las que tengo
 * para que todas quepan y rompa lo máximo?».
 *
 * - La PRIMERA jugada la valida el motor (`canPlacePiece`, la misma pregunta que al soltar el dedo),
 *   con las mismas exclusiones que aplica la UI (ficha dormida, cola forzada).
 * - Las siguientes se imaginan sobre una copia geométrica del tablero (madera + casillas vetadas +
 *   CUERPOS de Teika, rompiendo las líneas que se completen). Desde el 2026-09-26 los Teikas cuentan
 *   como en el motor: tapan su casilla y valen de comodín para cerrar una línea, pero la línea no se
 *   los lleva. No prevé que rueden ni dónde nacerán: es una aproximación para ORDENAR jugadas, no
 *   para decidir su legalidad (esa la da el motor en la primera jugada).
 * - Búsqueda en haz: las [BEAM_FIRST] mejores primeras jugadas, y para cada una las [BEAM_DEEP] mejores
 *   continuaciones por nivel. Dejar una ficha de la bandeja sin sitio cuesta más que cualquier línea.
 *
 * La puntuación de cada paso es la de manual del género: romper líneas (y varias a la vez), romper las
 * que llevan estrella, no dejar agujeros encerrados, pegar las fichas a lo que ya hay.
 *
 * Con `noise > 0` elige al azar entre los planes casi empatados: varias pasadas del mismo nivel dan
 * una distribución en vez de una sola partida.
 *
 * **Tablero en BITBOARD** (2026-09-28): un `Long` de 64 bits (bit `y*8+x`) en vez de `Set<Position>`.
 * El haz evalúa ~300 000 pasos por decisión y cada uno copiaba un `HashSet`; ahora son desplazamientos
 * y `bitCount`. Misma búsqueda, mismo orden y misma puntuación que la versión de conjuntos: con la misma
 * semilla elige la misma jugada, así que un informe viejo sigue valiendo de «Antes».
 */
class GreedyPolicy(private val random: kotlin.random.Random, private val noise: Int = 8) {

    data class Move(val piece: Piece, val x: Int, val y: Int, val score: Int)

    private class Step(val score: Int, val after: Long, val stars: Long)

    /** Una orientación de una ficha, con su máscara ya colocada en cada casilla (0 = se sale). */
    private class Orient(val piece: Piece, val masks: LongArray, val starMasks: LongArray)

    /** Los cuerpos de la jugada en curso: ocupan y cierran líneas, pero una línea no los rompe. */
    private var bodies = 0L

    /** Orientaciones por id de ficha, calculadas una vez por decisión. */
    private val orients = HashMap<String, List<Orient>>()

    internal fun choose(
        state: GameState,
        exclude: Set<Triple<String, Int, Int>> = emptySet(),
        /** Casillas que pisan los cuerpos de Teika (`GameViewModel.teikaOccupiedCellsForUi`). */
        bodies: Set<Position> = emptySet(),
        /**
         * P-366: si la próxima colocación es el golpe de un bioma, la pregunta del motor «¿esta jugada lo
         * para?» (`GameViewModel.parryForNextPlacement`). Las jugadas que paran reciben [PARRY_BONUS].
         */
        parry: ((GameViewModel.ParryProbe) -> Boolean)? = null,
        canPlace: (Piece, Int, Int) -> Boolean,
    ): Move? {
        this.bodies = maskOf(bodies)
        orients.clear()
        var filled = 0L
        var stars = 0L
        // Lo que el motor rechaza SEGURO (vetadas, cuerpos, lava, madera que no es hielo pisable): se
        // descarta sin preguntarle. Solo ahorra llamadas: el motor sigue decidiendo el resto.
        var surelyBlocked = 0L
        for ((p, c) in state.grid) {
            if (!inGrid(p)) continue
            if (c !is CellContent.Empty) filled = filled or bit(p)
            if (c.isOccupied && p !in state.frozenCells) surelyBlocked = surelyBlocked or bit(p)
            if (c is CellContent.Wood && c.targetColor != null) stars = stars or bit(p)
        }
        val unplaceable = maskOf(state.unplaceableCells)
        filled = filled or unplaceable or this.bodies
        for (y in 0 until state.lavaRow.coerceIn(0, GRID)) surelyBlocked = surelyBlocked or (0xFFL shl (y * GRID))
        surelyBlocked = surelyBlocked or unplaceable or this.bodies
        val tray = state.availablePieces.filter { !state.isPieceAsleep(it.id) }
        // REGLA HORIZONTE: la ficha que viene ya se ve, y quien juega bien le deja sitio. Se planificó
        // contra el tablero de antes, así que nada garantiza que quepa si no se le guarda hueco.
        val coming = state.pieceHorizon

        // 1) Primeras jugadas: legalidad del MOTOR.
        val first = ArrayList<Pair<Move, Step>>()
        for (dealt in ForcedQueue.playable(state.level, state.availablePieces)) {
            if (state.isPieceAsleep(dealt.id)) continue
            // P-276: el jugador puede girar la ficha con un toque, así que el bot también la prueba
            // en sus cuatro rotaciones (el driver la gira por el ViewModel antes de colocarla).
            for (o in orientsOf(dealt, state.level)) {
                val piece = o.piece
                for (y in 0 until GRID) for (x in 0 until GRID) {
                    val cells = o.masks[y * GRID + x]
                    if (cells == 0L || cells and surelyBlocked != 0L) continue
                    if (exclude.isNotEmpty() && Triple(refusalKeyOf(piece), x, y) in exclude) continue
                    if (!canPlace(piece, x, y)) continue
                    // Algunas reglas dejan pisar celdas ocupadas (hielo): se cuentan como libres.
                    val step = step(filled and cells.inv(), stars or o.starMasks[y * GRID + x], cells)
                    val parried = parry != null && parry(probeOf(filled and cells.inv(), cells, dealt.id))
                    first += Move(piece, x, y, step.score + if (parried) PARRY_BONUS else 0) to step
                }
            }
        }
        if (first.isEmpty()) return null

        // 2) Para las mejores, el mejor plan con lo que queda de bandeja.
        val planned = first.sortedByDescending { it.first.score }.take(BEAM_FIRST).map { (move, step) ->
            val rest = tray.filter { it.id != move.piece.id } + coming
            // `move.score - step.score` es la prima de parada (0 si no para): se conserva en el plan.
            move.copy(score = move.score + bestContinuation(state.level, step.after, step.stars, rest, depth = 1))
        }
        val best = planned.maxOf { it.score }
        return planned.filter { it.score >= best - noise }.random(random)
    }

    /** Mejor suma de puntos colocando [rest] (en el orden que permita la cola) sobre [filled]. */
    private fun bestContinuation(level: Int, filled: Long, stars: Long, rest: List<Piece>, depth: Int): Int {
        if (rest.isEmpty() || depth > MAX_DEPTH) return 0
        val options = ArrayList<Pair<String, Step>>()
        for (dealt in ForcedQueue.playable(level, rest)) {
            for (o in orientsOf(dealt, level)) {
                for (i in 0 until GRID * GRID) {
                    val cells = o.masks[i]
                    if (cells == 0L || cells and filled != 0L) continue
                    options += dealt.id to step(filled, stars, cells)
                }
            }
        }
        if (options.isEmpty()) return -STRANDED * rest.size
        val beam = if (depth <= 1) BEAM_DEEP else BEAM_DEEPER
        return options.sortedByDescending { it.second.score }.take(beam).maxOf { (id, step) ->
            // Por id: la ficha girada ya no es igual a la repartida.
            step.score + bestContinuation(level, step.after, step.stars, rest.filter { it.id != id }, depth + 1)
        }
    }

    /**
     * P-366: lo que rompería colocar [cells] sobre [filled], con la forma que miran los biomas. Solo líneas
     * LLENAS del tablero imaginado (el bot no ve las medias filas del Dojo ni dónde rodarán los Teikas).
     */
    private fun probeOf(filled: Long, cells: Long, pieceId: String): GameViewModel.ParryProbe {
        val after = filled or cells or bodies
        val rows = HashSet<Int>(2); val cols = HashSet<Int>(2); val hit = HashSet<Position>()
        for (k in 0 until GRID) {
            if (after and (ROW0 shl (k * GRID)) == ROW0 shl (k * GRID)) {
                rows += k; for (x in 0 until GRID) hit += Position(x, k)
            }
            if (after and (COL0 shl k) == COL0 shl k) {
                cols += k; for (y in 0 until GRID) hit += Position(k, y)
            }
        }
        return GameViewModel.ParryProbe(rows, cols, hit, pieceId)
    }

    /** Coloca [cells] sobre [filled], rompe las líneas completas y puntúa el resultado. */
    private fun step(filled: Long, stars: Long, cells: Long): Step {
        var after = filled or cells
        var cleared = 0L
        var lines = 0
        for (k in 0 until GRID) {
            val row = ROW0 shl (k * GRID)
            if (after and row == row) { cleared = cleared or row; lines++ }
            val col = COL0 shl k
            if (after and col == col) { cleared = cleared or col; lines++ }
        }
        val starsTaken = java.lang.Long.bitCount(stars and cleared)
        after = after and (cleared and bodies.inv()).inv()

        val holes = java.lang.Long.bitCount(
            after.inv() and blockedLeft(after) and blockedRight(after) and blockedUp(after) and blockedDown(after)
        )
        val around = filled and cells.inv()
        val contact = java.lang.Long.bitCount(cells and blockedLeft(around)) +
            java.lang.Long.bitCount(cells and blockedRight(around)) +
            java.lang.Long.bitCount(cells and blockedUp(around)) +
            java.lang.Long.bitCount(cells and blockedDown(around))
        // Filas/columnas a punto de cerrarse: preparar la siguiente línea también es jugar bien.
        var nearlyFull = 0
        for (k in 0 until GRID) {
            if (java.lang.Long.bitCount(after and (ROW0 shl (k * GRID))) >= GRID - 2) nearlyFull++
            if (java.lang.Long.bitCount(after and (COL0 shl k)) >= GRID - 2) nearlyFull++
        }

        val score = lines * 100 + (if (lines >= 2) lines * 60 else 0) + starsTaken * 45 -
            holes * 25 + contact * 3 + nearlyFull * 6 - java.lang.Long.bitCount(after)
        return Step(score, after, stars and cleared.inv())
    }

    /** Las orientaciones que el jugador puede darle a [piece]: las cuatro, salvo en el tutorial. */
    private fun orientsOf(piece: Piece, level: Int): List<Orient> = orients.getOrPut(piece.id) {
        val out = ArrayList<Orient>(4)
        var current = piece
        repeat(if (TutorialCampaign.isTutorialLevel(level)) 1 else 4) {
            if (out.none { it.piece.shape == current.shape }) out += orientOf(current)
            current = current.rotate()
        }
        out
    }

    private fun orientOf(piece: Piece): Orient {
        val masks = LongArray(GRID * GRID)
        val starMasks = LongArray(GRID * GRID)
        for (y in 0 until GRID) for (x in 0 until GRID) {
            var m = 0L
            var s = 0L
            var inside = true
            piece.shape.forEachIndexed { i, c ->
                val cx = x + c.x
                val cy = y + c.y
                if (cx !in 0 until GRID || cy !in 0 until GRID) { inside = false; return@forEachIndexed }
                m = m or (1L shl (cy * GRID + cx))
                if (piece.targetMask.getOrNull(i) != null) s = s or (1L shl (cy * GRID + cx))
            }
            if (inside) { masks[y * GRID + x] = m; starMasks[y * GRID + x] = s }
        }
        return Orient(piece, masks, starMasks)
    }

    private companion object {
        const val GRID = 8
        const val BEAM_FIRST = 12
        const val BEAM_DEEP = 6
        const val BEAM_DEEPER = 3
        /** Profundidad de la continuación: la bandeja de cinco (o tres + horizonte) entera. */
        const val MAX_DEPTH = 4
        /** Dejar una ficha sin sitio en ninguna rotación: sin reparador, es perder. Pesa más que cualquier línea. */
        const val STRANDED = 700

        /**
         * P-366: prima de una jugada que PARA el golpe del bioma. Vale más que romper tres líneas (~480)
         * y menos que dejar una ficha sin sitio ([STRANDED]): parar compensa casi siempre, pero no a
         * costa de perder la bandeja.
         */
        const val PARRY_BONUS = 500

        const val ROW0 = 0xFFL
        const val COL0 = 0x0101010101010101L
        const val COL7 = COL0 shl 7
        const val ROW7 = ROW0 shl 56

        fun inGrid(p: Position) = p.x in 0 until GRID && p.y in 0 until GRID
        fun bit(p: Position) = 1L shl (p.y * GRID + p.x)
        fun maskOf(cells: Set<Position>): Long {
            var m = 0L
            for (p in cells) if (inGrid(p)) m = m or bit(p)
            return m
        }

        // ¿Está bloqueado el vecino de cada casilla (ocupado en [b] o fuera del tablero)?
        fun blockedLeft(b: Long) = ((b shl 1) and COL0.inv()) or COL0
        fun blockedRight(b: Long) = ((b ushr 1) and COL7.inv()) or COL7
        fun blockedUp(b: Long) = (b shl GRID) or ROW0
        fun blockedDown(b: Long) = (b ushr GRID) or ROW7
    }
}

/**
 * B-2 (2026-09-27): la clave de una jugada RECHAZADA incluye la ORIENTACIÓN. Con solo el id, rechazar la
 * ficha en una casilla vetaba también sus otras tres rotaciones en esa casilla.
 */
internal fun refusalKeyOf(piece: com.cukiti.teikafusion.model.Piece): String =
    piece.id + "#" + piece.shape.joinToString(";") { "${it.x},${it.y}" }
