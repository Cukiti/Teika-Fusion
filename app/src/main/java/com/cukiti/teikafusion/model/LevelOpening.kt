package com.cukiti.teikafusion.model

import kotlin.random.Random

/**
 * **Garantía de apertura de un nivel** (2026-08-24, pedido del usuario: «arregla los niveles del
 * damero, que sean jugables»).
 *
 * El generador ([LevelLayouts.buildLevel]) elige una figura curada del pool y la recorta a
 * `maxFill`, que al final del juego llega a 56 de 64 celdas. Ninguno de los dos pasos mira si lo que
 * queda LIBRE admite una jugada: el censo del 2026-08-24 encontró **14 niveles** cuyo tablero
 * inicial no acepta ni una sola ficha de 3 celdas, y en cuatro de ellos —el **damero** de los
 * niveles 269, 272, 286 y 289— media pizarra está libre y aun así no cabe **ni un dominó**, porque
 * ninguna celda libre toca a otra. Con la bandeja reducida a monominós y un objetivo de ~110
 * estrellas en ~42 turnos, esos niveles no se podían jugar.
 *
 * (De paso, era la causa del ANR del mismo día: las políticas de variedad del planificador de
 * bandeja son imposibles en esos tableros, así que su backtracking se recorría entero.)
 *
 * La regla que impone este archivo es deliberadamente **la mínima**, y son DOS condiciones:
 *
 *  1. **Bandeja mixta posible** ([admitsMixedTray]): tres reservas que no compitan, una de 3+
 *     celdas, una de 2+ y una suelta. Es exactamente lo que pide la política preferente de
 *     [PieceTrayPlanner], así que el nivel abre pudiendo repartir fichas de verdad.
 *  2. **Los huecos no pueden estar mayoritariamente sueltos** ([MAX_ISOLATED_RATIO]). Sin esto el
 *     damero seguía sin arreglo real: abrirle UNA ventana le da una bandeja decente en el turno 1,
 *     pero en cuanto se llena vuelve a ser un tablero de celdas aisladas donde solo cabe el
 *     monominó. La medición del 2026-08-24 lo deja claro: los cuatro dameros están en **74-77 %**
 *     de huecos aislados y **ningún otro nivel del juego pasa del 56 %**.
 *
 * Lo que **no** exige, a propósito: que el tablero esté despejado. 111 de los 293 niveles no tienen
 * un claro rectangular de 2×3, y 53 tienen su mayor bolsa de huecos por debajo de 6 celdas. Eso no
 * es un fallo, es su dificultad, y se queda como está.
 *
 * Cuando un nivel no cumple, [ensurePlayableOpening] **abre claros de 3×2** en la figura hasta que
 * cumpla: el hueco más barato de conseguir (el que menos celdas curadas se lleva por delante) y, a
 * igualdad de coste, el más alejado del centro de la figura — así la silueta conserva su cuerpo, lo
 * que se muerde es el borde y las ventanas salen repartidas en vez de amontonadas. Un rectángulo de
 * 6 celdas libres basta por construcción para la condición 1: I3 + dominó + una suelta caben dentro.
 */
object LevelOpening {

    /** Ancho y alto del claro que se abre cuando un nivel no admite bandeja mixta. */
    const val POCKET_LONG = 3
    const val POCKET_SHORT = 2

    /** Celdas libres mínimas para que quepan las tres reservas (3 + 2 + 1). */
    private const val MIN_FREE_CELLS = 6

    /**
     * Fracción máxima de huecos SUELTOS (sin ninguna celda libre pegada en cruz).
     *
     * Medio tablero es el listón porque es donde está la frontera real: el juego entero se mueve
     * entre el 0 % y el 56 %, y los cuatro dameros se despegan en el 74-77 %. Un hueco suelto solo
     * lo puede llenar un monominó, y la bandeja reparte como mucho uno.
     */
    private const val MAX_ISOLATED_RATIO = 0.5f

    /** Como mucho tantos claros por nivel: si con esto no basta, la figura es irrecuperable. */
    private const val MAX_CARVES = 6

    /**
     * **HOLGURA DE LÍNEA** (2026-08-26, pedido del usuario: «que los bloques no estén unidos
     * completando filas»).
     *
     * Celdas como mucho que puede traer YA PUESTAS una fila o una columna. Con 8 de 8 la línea está
     * hecha: la primera ficha que el jugador suelte en cualquier parte del tablero dispara
     * `resolveBoardChain` y la rompe **sola**, sin que él haya construido nada. Con 7 de 8 le falta
     * una celda, o sea una ficha cualquiera puesta en el único hueco.
     *
     * El censo del 2026-08-26 mide lo que eso vale hoy: **150 de los 293 niveles** abren con al menos
     * una línea completa, con una media de **3,79 líneas regaladas** y un pico de **9**. Ese es el
     * mecanismo exacto que denunció el usuario: cuantos más bloques trae el nivel, más FÁCIL es —al
     * revés de lo que la densidad promete—, porque el estorbo se convierte en cosecha gratis (líneas,
     * Teikas de línea y ~3,5 estrellas por línea) antes de la segunda jugada.
     *
     * Con **6** hacen falta dos colocaciones para cerrar cualquier línea de salida, así que el
     * tablero denso vuelve a ser lo que dice ser: un estorbo. El techo real de relleno pasa de 56 a
     * 48 celdas (6 × 8), y eso ya está pagado en el presupuesto de turnos — ver
     * [LevelBudget] y [BoardClutterCost].
     */
    const val MAX_LINE_FILL = 6

    private val TROMINOES: List<List<Pair<Int, Int>>> = listOf(
        listOf(0 to 0, 1 to 0, 2 to 0),
        listOf(0 to 0, 0 to 1, 0 to 2),
        listOf(0 to 0, 1 to 0, 0 to 1),
        listOf(0 to 0, 1 to 0, 1 to 1),
        listOf(0 to 0, 0 to 1, 1 to 1),
        listOf(1 to 0, 0 to 1, 1 to 1),
    )

    private val DOMINOES: List<List<Pair<Int, Int>>> = listOf(
        listOf(0 to 0, 1 to 0),
        listOf(0 to 0, 0 to 1),
    )

    /**
     * ¿Admite este tablero una bandeja MIXTA? Es decir: ¿existen tres colocaciones que no se pisen,
     * una de 3 celdas, otra de 2 y otra de 1?
     *
     * Es una condición geométrica cerrada, no una simulación del planificador: si es cierta, la
     * política preferente de [PieceTrayPlanner] tiene solución; si es falsa, no la tiene y el nivel
     * abre repartiendo monominós.
     */
    fun admitsMixedTray(
        blocked: Set<Position>,
        boardSize: Int = GameState.GRID_SIZE,
        boardHeight: Int = boardSize,
    ): Boolean {
        val free = freeCells(blocked, boardSize, boardHeight)
        if (free.size < MIN_FREE_CELLS) return false
        for (tromino in placementsOf(TROMINOES, free, boardSize, boardHeight)) {
            for (domino in placementsOf(DOMINOES, free, boardSize, boardHeight)) {
                if (domino.none { it in tromino }) return true
            }
        }
        return false
    }

    /**
     * Fracción de huecos que están SUELTOS: sin ninguna otra celda libre pegada en cruz. Un tablero
     * lleno (sin huecos) devuelve 1f — no se puede jugar en él.
     */
    fun isolatedHoleRatio(
        blocked: Set<Position>,
        boardSize: Int = GameState.GRID_SIZE,
        boardHeight: Int = boardSize,
    ): Float {
        val free = freeCells(blocked, boardSize, boardHeight)
        if (free.isEmpty()) return 1f
        val alone = free.count { p ->
            Position(p.x + 1, p.y) !in free &&
                Position(p.x - 1, p.y) !in free &&
                Position(p.x, p.y + 1) !in free &&
                Position(p.x, p.y - 1) !in free
        }
        return alone.toFloat() / free.size
    }

    /**
     * **¿Hay al menos un hueco libre de 2×2?** (2026-09-26, bug.) Los dos niveles 144 y 194 abrían con un
     * ENREJADO EN DIAGONAL: huecos encadenados en zigzag (así que no eran «sueltos» y pasaban
     * [isolatedHoleRatio]) donde solo cabían fichas en S y ninguna línea se podía cerrar. El bot los
     * perdía siempre en la 5.ª colocación sin romper una sola línea. Un cuadrado libre de 2×2 es lo
     * mínimo para que quepa un cuadrado, una L o una barra doblada: sin él, el tablero no se juega.
     */
    fun hasFreeSquare(
        blocked: Set<Position>,
        boardSize: Int = GameState.GRID_SIZE,
        boardHeight: Int = boardSize,
    ): Boolean = (0 until boardHeight - 1).any { y ->
        (0 until boardSize - 1).any { x ->
            listOf(Position(x, y), Position(x + 1, y), Position(x, y + 1), Position(x + 1, y + 1))
                .none { it in blocked }
        }
    }

    /** Las condiciones de la clase, juntas: ¿este tablero se puede jugar de verdad? */
    fun isPlayableOpening(
        blocked: Set<Position>,
        boardSize: Int = GameState.GRID_SIZE,
        boardHeight: Int = boardSize,
    ): Boolean = admitsMixedTray(blocked, boardSize, boardHeight) &&
        isolatedHoleRatio(blocked, boardSize, boardHeight) <= MAX_ISOLATED_RATIO &&
        hasFreeSquare(blocked, boardSize, boardHeight)

    /**
     * Devuelve la figura tal cual si el tablero ya es jugable ([isPlayableOpening]); si no, le abre
     * claros de [POCKET_LONG]×[POCKET_SHORT] hasta que lo sea.
     *
     * Conserva el ORDEN original de las celdas (el generador reparte los colores del objetivo en
     * round-robin sobre esa lista) y nunca AÑADE celdas: abrir un nivel solo puede aflojarlo.
     */
    fun ensurePlayableOpening(
        figure: List<Pair<Int, Int>>,
        rng: Random,
        boardSize: Int = GameState.GRID_SIZE,
        boardHeight: Int = boardSize,
        /**
         * Celdas que NO son de la figura pero tampoco admiten ficha: la silueta muerta del nivel
         * ([LevelVariety.BoardShape], 2026-08-26f).
         *
         * Van aparte de [figure] porque **no se pueden morder**: abrir un claro puede quitar madera,
         * nunca resucitar una celda que el nivel declaró muerta. Pero sí cuentan como ocupadas para
         * decidir si el tablero admite una bandeja mixta — si no, la garantía certificaría una
         * jugada que en el tablero real no cabe, que es justo el fallo que este archivo existe para
         * impedir.
         */
        alsoBlocked: Set<Position> = emptySet(),
    ): List<Pair<Int, Int>> {
        var current = figure
        val carved = HashSet<Position>()
        repeat(MAX_CARVES) {
            val blocked = current.mapTo(HashSet()) { (x, y) -> Position(x, y) }
            blocked += alsoBlocked
            if (isPlayableOpening(blocked, boardSize, boardHeight)) return current
            val pocket = cheapestPocket(current, boardSize, boardHeight, rng, carved) ?: return current
            carved += pocket
            current = current.filterNot { (x, y) -> Position(x, y) in pocket }
        }
        return current
    }

    /**
     * Líneas (filas + columnas) de [figure] que pasan de [MAX_LINE_FILL]. `0` = la apertura ya tiene
     * holgura. Es la métrica que vigila el test de censo.
     */
    fun overfilledLines(
        figure: Collection<Pair<Int, Int>>,
        boardSize: Int = GameState.GRID_SIZE,
        boardHeight: Int = boardSize,
    ): Int {
        val rows = IntArray(boardHeight)
        val cols = IntArray(boardSize)
        figure.forEach { (x, y) ->
            if (x in 0 until boardSize && y in 0 until boardHeight) {
                rows[y]++
                cols[x]++
            }
        }
        return rows.count { it > MAX_LINE_FILL } + cols.count { it > MAX_LINE_FILL }
    }

    /**
     * Quita celdas de la figura hasta que ninguna fila ni columna pase de [MAX_LINE_FILL].
     *
     * Nunca añade nada y conserva el ORDEN original de las que sobreviven (el generador reparte los
     * colores del objetivo en round-robin sobre esa lista, así que reordenar cambiaría el objetivo).
     *
     * A quién le toca salir: primero a la celda que descongestiona DOS líneas a la vez (está en una
     * fila y en una columna pasadas de rosca), luego a la de las líneas más saturadas, y en tercer
     * lugar a la más ALEJADA del centro de la figura — se muerde el borde, no el cuerpo, igual que
     * hace [cheapestPocket] con los claros. El azar sembrado del nivel deshace el último empate para
     * que dos niveles con la misma figura no se abran igual.
     */
    fun ensureLineSlack(
        figure: List<Pair<Int, Int>>,
        rng: Random,
        boardSize: Int = GameState.GRID_SIZE,
        boardHeight: Int = boardSize,
    ): List<Pair<Int, Int>> {
        if (figure.isEmpty()) return figure
        val rows = IntArray(boardHeight)
        val cols = IntArray(boardSize)
        figure.forEach { (x, y) ->
            if (x in 0 until boardSize && y in 0 until boardHeight) {
                rows[y]++
                cols[x]++
            }
        }
        if (rows.none { it > MAX_LINE_FILL } && cols.none { it > MAX_LINE_FILL }) return figure

        val cx = figure.sumOf { it.first }.toFloat() / figure.size
        val cy = figure.sumOf { it.second }.toFloat() / figure.size
        // Desempate estable: una tirada por celda, no una por vuelta del bucle (así el resultado no
        // depende de cuántas vueltas dé el bucle, que es lo que haría irreproducible el nivel).
        val jitter = figure.associateWith { rng.nextInt(0, 1_000) }
        val doomed = HashSet<Pair<Int, Int>>()

        var guard = figure.size
        while (guard-- > 0 && (rows.any { it > MAX_LINE_FILL } || cols.any { it > MAX_LINE_FILL })) {
            var best: Pair<Int, Int>? = null
            var bestScore = -1
            var bestPressure = -1
            var bestDistance = -1f
            var bestTie = -1
            for (cell in figure) {
                if (cell in doomed) continue
                val (x, y) = cell
                if (x !in 0 until boardSize || y !in 0 until boardHeight) continue
                val rowOver = rows[y] > MAX_LINE_FILL
                val colOver = cols[x] > MAX_LINE_FILL
                if (!rowOver && !colOver) continue
                val score = (if (rowOver) 1 else 0) + (if (colOver) 1 else 0)
                val pressure = (if (rowOver) rows[y] else 0) + (if (colOver) cols[x] else 0)
                val dx = x - cx
                val dy = y - cy
                val distance = dx * dx + dy * dy
                val tie = jitter[cell] ?: 0
                val better = when {
                    score != bestScore -> score > bestScore
                    pressure != bestPressure -> pressure > bestPressure
                    distance != bestDistance -> distance > bestDistance
                    else -> tie > bestTie
                }
                if (better) {
                    best = cell
                    bestScore = score
                    bestPressure = pressure
                    bestDistance = distance
                    bestTie = tie
                }
            }
            val victim = best ?: break
            doomed += victim
            rows[victim.second]--
            cols[victim.first]--
        }
        return figure.filterNot { it in doomed }
    }

    /**
     * El rectángulo de 3×2 (o 2×3) que cuesta menos celdas de la figura. A igualdad de coste gana el
     * más alejado del punto de referencia, y el azar sembrado del nivel deshace el último empate
     * para que dos niveles con la misma figura no abran igual.
     *
     * La referencia es el centro de la figura en el PRIMER claro —se muerde el borde, no el cuerpo—
     * y el centro de lo ya abierto en los siguientes: así las ventanas salen REPARTIDAS por el
     * tablero en vez de amontonarse en una esquina. Con el damero importa de verdad: un solo agujero
     * grande deja el resto de la pizarra igual de muerto, tres repartidos la abren entera.
     */
    private fun cheapestPocket(
        figure: List<Pair<Int, Int>>,
        boardSize: Int,
        boardHeight: Int,
        rng: Random,
        carved: Set<Position>,
    ): Set<Position>? {
        if (figure.isEmpty()) return null
        val blocked = figure.mapTo(HashSet()) { (x, y) -> Position(x, y) }
        val cx: Float
        val cy: Float
        if (carved.isEmpty()) {
            cx = figure.sumOf { it.first }.toFloat() / figure.size
            cy = figure.sumOf { it.second }.toFloat() / figure.size
        } else {
            cx = carved.sumOf { it.x }.toFloat() / carved.size
            cy = carved.sumOf { it.y }.toFloat() / carved.size
        }

        var bestCells: Set<Position>? = null
        var bestCost = Int.MAX_VALUE
        var bestDistance = -1f
        var bestTie = -1

        for ((w, h) in listOf(POCKET_LONG to POCKET_SHORT, POCKET_SHORT to POCKET_LONG)) {
            if (w > boardSize || h > boardHeight) continue
            for (y in 0..(boardHeight - h)) {
                for (x in 0..(boardSize - w)) {
                    val cells = HashSet<Position>(w * h)
                    var cost = 0
                    for (dy in 0 until h) {
                        for (dx in 0 until w) {
                            val p = Position(x + dx, y + dy)
                            if (p in blocked) {
                                cells += p
                                cost++
                            }
                        }
                    }
                    // Una ventana que ya está vacía no libera nada: es justo la que se acaba de
                    // abrir, y elegirla otra vez dejaría el bucle sin avanzar.
                    if (cost == 0) continue
                    val mx = x + (w - 1) / 2f
                    val my = y + (h - 1) / 2f
                    val distance = (mx - cx) * (mx - cx) + (my - cy) * (my - cy)
                    val tie = rng.nextInt(0, 1_000)
                    val better = when {
                        cost != bestCost -> cost < bestCost
                        distance != bestDistance -> distance > bestDistance
                        else -> tie > bestTie
                    }
                    if (better) {
                        bestCells = cells
                        bestCost = cost
                        bestDistance = distance
                        bestTie = tie
                    }
                }
            }
        }
        // Un claro que no libera ninguna celda no acerca nada: mejor rendirse que girar en vano.
        return bestCells?.takeIf { it.isNotEmpty() }
    }

    private fun freeCells(
        blocked: Set<Position>,
        boardSize: Int,
        boardHeight: Int,
    ): Set<Position> = buildSet {
        for (y in 0 until boardHeight) {
            for (x in 0 until boardSize) {
                val p = Position(x, y)
                if (p !in blocked) add(p)
            }
        }
    }

    private fun placementsOf(
        shapes: List<List<Pair<Int, Int>>>,
        free: Set<Position>,
        boardSize: Int,
        boardHeight: Int,
    ): List<Set<Position>> {
        val out = ArrayList<Set<Position>>()
        for (shape in shapes) {
            val w = shape.maxOf { it.first } + 1
            val h = shape.maxOf { it.second } + 1
            if (w > boardSize || h > boardHeight) continue
            for (y in 0..(boardHeight - h)) {
                for (x in 0..(boardSize - w)) {
                    val cells = shape.mapTo(HashSet<Position>(shape.size)) { (dx, dy) ->
                        Position(x + dx, y + dy)
                    }
                    if (cells.all { it in free }) out += cells
                }
            }
        }
        return out
    }
}
