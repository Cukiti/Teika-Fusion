package com.cukiti.teikafusion.model

import kotlin.random.Random

/**
 * **EL REPARTO DE LA BANDEJA** — desde el 2026-09-26 (decisión del usuario), **lo más grande que quepa,
 * y las fichas de la bandeja caben todas juntas**.
 *
 * ## Por qué dejó de ser azar con pesos
 *
 * El reparto con pesos (P-255) medía el tablero en tres escalones (limpio, medio, lleno) y sorteaba
 * cada ranura por su cuenta: con el tablero vacío, una de cada tres bandejas traía una ficha de 1–3
 * casillas, y un tablero con 40 casillas libres repartía igual que uno con 64. Encima, dos diales de
 * capítulo y de tipo de nivel movían el tamaño sin que el jugador pudiera leerlo. El usuario pidió lo
 * contrario: que el tamaño se pueda PREDECIR mirando el tablero, que las grandes salgan siempre que se
 * puedan colocar —ponerlas y romper filas juntas es lo satisfactorio—, y que el tablero que el MEGA deja
 * vacío se reponga solo con fichas grandes.
 *
 * ## Cómo se elige cada ficha
 *
 *  - **Caben juntas**: una ficha solo entra si ella y las ya elegidas se pueden colocar A LA VEZ sin
 *    pisarse, sobre el tablero de ahora (sin contar las líneas que se romperían por el camino: es la
 *    lectura prudente). Los Teikas ya van contados donde se van a QUEDAR, no donde nacen (lo calcula
 *    quien llama, con `TeikaPhysicsWorld.predictRestingCells`).
 *  - **Lo más grande que quepa**: de las fichas que cumplen lo anterior se mira el mayor tamaño, y solo
 *    compiten las de ese tamaño y las tres inmediatamente menores ([SIZE_BAND]), con el mayor pesando
 *    más ([BAND_WEIGHTS]). Con **premio** (P-316, una doble línea de una ficha) la banda se estrecha a
 *    las dos mayores ([REWARD_SIZE_BAND]): la bandeja premiada trae fichas más grandes que la normal. Nunca baja de [SMALL_FLOOR] casillas si algo de ese tamaño cabe: una ficha de
 *    1–3 casillas sale solo cuando no cabe nada mayor, o si es la que cierra una línea.
 *  - **La ranura que ayuda**: si hay líneas a punto de cerrarse ([TrayAssist.lineGaps]), una ficha de
 *    la bandeja siempre tapa alguno de sus huecos (la más grande que pueda hacerlo).
 *  - **Variedad**: nunca repite forma dentro de la bandeja (salvo que no quepa otra cosa) y una forma de
 *    la bandeja anterior pesa menos ([LAST_TRAY_WEIGHT]). La orientación la elige el reparto entre las
 *    que caben (el jugador puede girarla después, P-276).
 *
 * El tamaño ya NO depende del capítulo, del tipo de nivel, del sabor de bandeja, de las reglas de fundir
 * ni de la reposición instantánea: solo del tablero. Se conservan las gemelas (post-pase, P-76) y el
 * Corte Ninja (una ficha no cabe «a caballo»).
 *
 * ## Coste
 *
 * El tablero es de 8×8 = 64 casillas, así que cada colocación es un `Long` y comprobar si dos se pisan
 * es un AND. La búsqueda conjunta tiene un presupuesto de nodos por llamada ([SEARCH_BUDGET]): este
 * fichero ya provocó un ANR una vez (2026-08-24). Si se agota, lo que no se pudo comprobar cuenta como
 * «no cabe» y la bandeja sale más pequeña, nunca imposible.
 */
object PieceTrayPlanner {

    data class PlannedShape(
        val shape: List<Position>,
        val reservedCells: Set<Position>
    )

    /**
     * Una forma del catálogo con todas sus colocaciones libres en ESTE tablero, en todas sus
     * orientaciones: [masks] son las celdas como bits y [maskOrientation] dice de qué orientación sale
     * cada una.
     */
    private class ShapeOption(
        val id: Int,
        val size: Int,
        val orientations: List<List<Position>>,
        val masks: LongArray,
        val maskOrientation: IntArray,
    )

    /** Una ficha elegida y las colocaciones que se le permiten (todas, o solo las que ayudan). */
    private class Pick(val option: ShapeOption, val masks: LongArray, val maskOrientation: IntArray)

    /** Nodos de búsqueda por llamada a [plan] (cada nodo es un AND de 64 bits). */
    private const val SEARCH_BUDGET = 200_000

    /** Techo de la búsqueda de [fitsTogether] (apertura). */
    private const val FIT_TOGETHER_NODES = 4_000

    /**
     * Cuántos tamaños por debajo del mayor que cabe compiten todavía por la ranura. Era 2 hasta P-315
     * (2026-09-27, decisión del usuario): con un hueco de 3×3 la bandeja salía siempre de las mismas
     * cuatro formas (3×3, 2×4, P7, L7). Con 3 entran también las de 6 (2×3, L6).
     */
    private const val SIZE_BAND = 3

    /** La banda de la bandeja PREMIADA (P-316): solo el mayor tamaño que cabe y el siguiente. */
    private const val REWARD_SIZE_BAND = 1

    /** Peso de cada tamaño de la banda: el mayor que cabe, uno menos, dos menos, tres menos. */
    private val BAND_WEIGHTS = doubleArrayOf(1.0, 0.6, 0.35, 0.2)

    /**
     * Tamaño mínimo mientras quepa algo de este tamaño: con el mayor en 5 o 6 casillas, la banda no
     * baja a fichas de 3. Las pequeñas quedan para cuando no cabe nada mayor.
     */
    private const val SMALL_FLOOR = 4

    /**
     * **P-358 · FICHAS ROBUSTAS** (2026-09-28): una ficha de la bandeja tiene que poder ir en al menos
     * tantos sitios (contando sus giros) del tablero de ahora, siempre que alguna lo cumpla.
     *
     * La campaña del bot sin banco (572 partidas) midió que TODAS las derrotas eran por falta de sitio y
     * que, en el tablero de la derrota, quedaban **16–22 casillas libres** mientras la ficha que no cabía
     * era de **6–7** (tres de cada cuatro). El reparto la había dado porque en ESE momento cabía en un
     * único hueco, y el Teika que nació en la jugada siguiente —o el castigo del bioma— lo tapó. Una
     * ficha con un solo sitio es frágil; con tres, sobrevive a un cuerpo que rueda. Con el tablero
     * abierto no cambia nada (un 3×3 tiene 36 sitios en el tablero vacío): solo recorta el tamaño
     * cuando el tablero aprieta, que es donde el reparto «lo más grande que quepa» mataba.
     */
    private const val ROBUST_PLACEMENTS = 8

    /**
     * **P-358b · EL PRESUPUESTO DE LA BANDEJA** (2026-09-28): la mano entera (lo que entra más lo que el
     * jugador aún tiene) no ocupa más de esta fracción de las casillas libres, siempre que se pueda.
     *
     * Los [ROBUST_PLACEMENTS] miran cada ficha por separado sobre el tablero del reparto, y el bot seguía
     * muriendo con la ÚLTIMA ficha de la bandeja: un 3×3 que cabía en doce sitios de una mitad vacía dejaba
     * de caber en cuanto las otras dos fichas grandes se colocaban en esa misma mitad. Con el tablero vacío
     * no cambia nada (64 libres → 32 casillas, tres fichas de 9 caben); con 30 libres las fichas rondan las
     * 5 casillas y con 20, las 3. Solo depende del tablero.
     */
    private const val TRAY_BUDGET = 0.5

    /** Una forma de la bandeja ANTERIOR (o que el jugador aún tiene en la mano) pesa esto. */
    private const val LAST_TRAY_WEIGHT = 0.4

    fun plan(
        boardSize: Int,
        blockedCells: Set<Position>,
        count: Int = 3,
        /** Solo decide a qué ficha se le copia la forma cuando el nivel reparte gemelas. */
        level: Int = 1,
        random: Random = Random.Default,
        /** Alto del tablero; por defecto, cuadrado — que es lo que son los niveles. */
        boardHeight: Int = boardSize,
        /**
         * **CORTE NINJA** (Dojo): columna frontera del tablero partido, o -1 si está entero. Una
         * ficha no puede quedar a caballo entre las dos mitades, así que el planificador tampoco
         * puede reservarle un hueco que las cruce.
         */
        splitAtX: Int = -1,
        /**
         * **FICHAS GEMELAS** (P-76): intenta que dos ranuras traigan la MISMA forma. Es un post-pase
         * ([applyTwins]): si la geometría no lo admite, el plan se queda tal cual.
         */
        twins: Boolean = false,
        /**
         * **P-232 · LA PAREJA PARA FUNDIR** (2026-09-26): si es mayor que 0, dos ranuras traen la MISMA
         * forma, de como mucho estas casillas, y caben juntas. Es la excepción de los niveles de fundir
         * al «lo más grande que quepa»: sin ella no salían nunca gemelas pequeñas y la regla moría.
         */
        smallTwins: Int = 0,
        /** Formas del reparto anterior y de la mano: no se vetan, pesan [LAST_TRAY_WEIGHT]. */
        avoidShapes: List<List<Position>> = emptyList(),
        /**
         * **P-204 · LA RANURA QUE AYUDA**: huecos de las filas y columnas a punto de cerrarse
         * ([TrayAssist.lineGaps]). Los calcula quien llama porque «celda que cuenta para una línea»
         * es cosa del motor. Vacío = ninguna línea cerca.
         */
        helpfulCells: Set<Position> = emptySet(),
        /**
         * Fichas que el jugador YA tiene en la mano (reposición instantánea, P-144): las nuevas tienen
         * que caber junto a ellas, en cualquier orientación. No se devuelven. Una que hoy no cabe en
         * ningún sitio no se cuenta.
         */
        keptShapes: List<List<Position>> = emptyList(),
        /**
         * **P-316 · EL PREMIO SUBE LA BANDA** (2026-09-27, decisión del usuario): la bandeja que llega
         * tras una doble línea compite solo entre los dos mayores tamaños que caben.
         */
        rewarded: Boolean = false,
    ): List<PlannedShape> {
        if (count <= 0) return emptyList()
        require(boardSize * boardHeight <= 64) { "El reparto trabaja con tableros de hasta 64 casillas" }
        val playableCells = (0 until boardHeight).flatMap { y ->
            (0 until boardSize).map { x -> Position(x, y) }
        }.filterNotTo(mutableSetOf()) { it in blockedCells }
        if (playableCells.isEmpty()) return emptyList()
        val desiredCount = minOf(count, playableCells.size)

        fun bit(p: Position): Long = 1L shl (p.y * boardSize + p.x)
        val blockedMask = blockedCells.fold(0L) { acc, p ->
            if (p.x in 0 until boardSize && p.y in 0 until boardHeight) acc or bit(p) else acc
        }
        val helpfulMask = helpfulCells.fold(0L) { acc, p ->
            if (p.x in 0 until boardSize && p.y in 0 until boardHeight) acc or bit(p) else acc
        }

        val baseShapes = Piece.SHAPES.distinctBy(::canonicalShape)
        val options = baseShapes.mapIndexedNotNull { id, shape ->
            // Orientaciones en orden aleatorio: la búsqueda se queda con la primera que encaja, y así
            // la orientación repartida varía.
            val orientations = uniqueRotations(shape).shuffled(random)
            val masks = ArrayList<Long>()
            val owners = ArrayList<Int>()
            orientations.forEachIndexed { o, rotation ->
                placementsFor(rotation, boardSize, boardHeight, blockedCells, splitAtX).forEach { cells ->
                    masks += cells.fold(0L) { acc, p -> acc or bit(p) }
                    owners += o
                }
            }
            if (masks.isEmpty()) null
            else ShapeOption(id, shape.size, orientations, masks.toLongArray(), owners.toIntArray())
        }
        if (options.isEmpty()) return safeFallback(playableCells, desiredCount, random)

        val avoidIds: Set<Int> = if (avoidShapes.isEmpty()) emptySet() else {
            val byCanonical = HashMap<String, Int>(baseShapes.size * 2)
            baseShapes.forEachIndexed { id, shape -> byCanonical[canonicalShape(shape)] = id }
            avoidShapes.mapNotNullTo(HashSet()) { byCanonical[canonicalShape(it)] }
        }

        var nodes = SEARCH_BUDGET
        /** Coloca todas las [picks] a la vez sin pisarse; devuelve la máscara elegida de cada una. */
        fun solve(picks: List<Pick>): IntArray? {
            // Primero la más difícil de colocar (menos colocaciones): poda casi todo.
            val order = picks.indices.sortedBy { picks[it].masks.size }
            val chosen = IntArray(picks.size)
            fun go(k: Int, occupied: Long): Boolean {
                if (k == order.size) return true
                val i = order[k]
                val masks = picks[i].masks
                for (m in masks.indices) {
                    if (--nodes < 0) return false
                    if (masks[m] and occupied != 0L) continue
                    chosen[i] = m
                    if (go(k + 1, occupied or masks[m])) return true
                }
                return false
            }
            return if (go(0, blockedMask)) chosen else null
        }

        val joint = ArrayList<Pick>(desiredCount + keptShapes.size)
        var jointSolution: IntArray? = IntArray(0)
        val optionById = options.associateBy { it.id }
        val idByCanonical = HashMap<String, Int>(baseShapes.size * 2).also { map ->
            baseShapes.forEachIndexed { id, shape -> map[canonicalShape(shape)] = id }
        }
        for (shape in keptShapes) {
            val option = idByCanonical[canonicalShape(shape)]?.let(optionById::get) ?: continue
            val pick = Pick(option, option.masks, option.maskOrientation)
            // Si la mano ya no cabe junta, la que sobra no se cuenta: sería exigir lo imposible.
            solve(joint + pick)?.let { jointSolution = it; joint += pick }
        }
        val keptCount = joint.size
        val loosePicks = ArrayList<ShapeOption>()
        val freeCells = playableCells.size
        // El monominó es el relleno mínimo de una ranura: reservar uno por cada ranura que falta es la
        // forma barata de exigir que, tras esta ficha, todavía quepa ALGO en las demás.
        val filler = options.firstOrNull { it.size == 1 }?.let { Pick(it, it.masks, it.maskOrientation) }
        // El suelo de la bandeja: lo fija la primera ficha elegida y vale para las siguientes. Sin él,
        // cada ranura medía su propia banda y en el tablero vacío la tercera (tras un 3×3 y un 2×4)
        // bajaba a fichas de 5.
        var trayFloor = Int.MIN_VALUE
        val sizeBand = if (rewarded) REWARD_SIZE_BAND else SIZE_BAND

        /**
         * Elige una ficha entre [candidates] (con sus colocaciones permitidas): la mayor que quepa junto
         * a las ya elegidas DEJANDO sitio para las [reserve] ranuras que faltan, sorteada dentro de su
         * banda de tamaños. Sin la reserva, un único hueco de 3×3 se llevaba el 3×3 y las otras dos ya no
         * cabían. Null si ninguna cabe junta.
         */
        val budgetCells = maxOf(desiredCount, (freeCells * TRAY_BUDGET).toInt())
        fun pickJoint(
            candidates: List<Pick>, reserve: Int, restricted: Boolean = false, copies: Int = 1,
            budget: Boolean = false,
        ): Pick? {
            val usedCells = joint.sumOf { it.option.size }
            val fillers = if (filler == null) emptyList() else List(reserve) { filler }
            val feasible = ArrayList<Pair<Pick, IntArray>>()
            var floor = Int.MIN_VALUE
            for (c in candidates.sortedByDescending { it.option.size }) {
                if (c.option.size < floor) break
                if (usedCells + c.option.size * copies + reserve > freeCells) continue
                // P-358b: sin pasarse del presupuesto (dejando una casilla por cada ranura que falta).
                if (budget && usedCells + c.option.size * copies + reserve > budgetCells) continue
                val solution = solve(joint + List(copies) { c } + fillers)?.copyOf(joint.size + copies) ?: continue
                if (feasible.isEmpty()) {
                    val top = c.option.size
                    floor = maxOf(top - sizeBand, minOf(top, SMALL_FLOOR), minOf(top, trayFloor))
                }
                feasible += c to solution
            }
            if (feasible.isEmpty()) return null
            val top = feasible.first().first.option.size
            val perSize = feasible.groupingBy { it.first.option.size }.eachCount()
            val weights = DoubleArray(feasible.size) { i ->
                val option = feasible[i].first.option
                val band = BAND_WEIGHTS[(top - option.size).coerceIn(0, BAND_WEIGHTS.size - 1)]
                // El peso es del TAMAÑO, repartido entre sus formas: hay 8 formas de 5 casillas y 2 de
                // 6, y sin repartirlo las de 5 se comían la banda.
                var w = band / perSize.getValue(option.size)
                if (option.id in avoidIds) w *= LAST_TRAY_WEIGHT
                w
            }
            val (pick, solution) = feasible[weightedPick(weights, random)]
            jointSolution = solution
            if (trayFloor == Int.MIN_VALUE && !restricted) trayFloor = floor
            return pick
        }

        fun fullPick(o: ShapeOption) = Pick(o, o.masks, o.maskOrientation)
        fun notYetPicked(o: ShapeOption) = joint.none { it.option.id == o.id } && loosePicks.none { it.id == o.id }
        fun dealt() = joint.size - keptCount + loosePicks.size

        // La pareja para fundir, antes que nada: es la que más condiciones tiene.
        if (smallTwins > 0 && desiredCount >= 2) {
            // Una candidata por ORIENTACIÓN: fundir exige la misma forma tal cual (`PieceFusion.canFuse`),
            // y dos Z giradas distinto no se funden.
            val small = options.filter { it.size in 2..smallTwins }.flatMap { o ->
                o.orientations.indices.map { orient ->
                    val keep = o.masks.indices.filter { o.maskOrientation[it] == orient }
                    Pick(o, LongArray(keep.size) { o.masks[keep[it]] }, IntArray(keep.size) { orient })
                }.filter { it.masks.isNotEmpty() }
            }
            pickJoint(small, reserve = desiredCount - 2, restricted = true, copies = 2)
                ?.let { joint += it; joint += it }
        }
        // La ranura que ayuda: sus colocaciones se limitan a las que tapan un hueco.
        if (helpfulMask != 0L && dealt() < desiredCount) {
            val helpers = options.mapNotNull { o ->
                val keep = o.masks.indices.filter { o.masks[it] and helpfulMask != 0L }
                if (keep.isEmpty()) null
                else Pick(o, LongArray(keep.size) { o.masks[keep[it]] }, IntArray(keep.size) { o.maskOrientation[keep[it]] })
            }
            pickJoint(helpers, reserve = desiredCount - 1, restricted = true)?.let { joint += it }
        }
        while (dealt() < desiredCount) {
            val fresh = options.filter(::notYetPicked).map(::fullPick)
            val reserve = desiredCount - dealt() - 1
            // P-358/358b: primero las ROBUSTAS dentro del presupuesto; luego cualquiera dentro del
            // presupuesto; luego las robustas; y si nada de eso cabe junto, el reparto de siempre.
            val robust = fresh.filter { it.option.masks.size >= ROBUST_PLACEMENTS }
            val chosen = pickJoint(robust, reserve, budget = true)
                ?: pickJoint(fresh, reserve, budget = true)
                ?: pickJoint(robust, reserve)
                ?: pickJoint(fresh, reserve)
                // Sin forma nueva que quepa junta: se admite repetir forma antes que romper el encaje.
                ?: pickJoint(options.map(::fullPick), reserve)
                // Y si ni así, se deja de reservar sitio para las que faltan.
                ?: pickJoint(options.map(::fullPick), reserve = 0)
            if (chosen != null) { joint += chosen; continue }
            // Ya no cabe nada junto a lo elegido: la mayor que quepa SOLA (el jugador puede abrir sitio
            // rompiendo líneas). Es el tablero de verdad lleno.
            val alone = options.filter(::notYetPicked).ifEmpty { options }
            val top = alone.maxOf { it.size }
            loosePicks += alone.filter { it.size == top }.random(random)
        }

        val solution = jointSolution
        val picked = ArrayList<PlannedShape>(desiredCount)
        joint.forEachIndexed { i, pick ->
            if (i < keptCount) return@forEachIndexed
            if (solution != null && i < solution.size) {
                val m = solution[i]
                val shape = pick.option.orientations[pick.maskOrientation[m]]
                picked += PlannedShape(shape, cellsOf(pick.masks[m], boardSize))
            } else {
                picked += PlannedShape(pick.option.orientations.first(), emptySet())
            }
        }
        loosePicks.forEach { o ->
            val m = random.nextInt(o.masks.size)
            picked += PlannedShape(o.orientations[o.maskOrientation[m]], cellsOf(o.masks[m], boardSize))
        }
        return if (twins) {
            applyTwins(picked, boardSize, boardHeight, blockedCells, splitAtX, level)
        } else picked
    }

    private fun cellsOf(mask: Long, boardSize: Int): Set<Position> {
        val out = HashSet<Position>()
        var m = mask
        while (m != 0L) {
            val i = java.lang.Long.numberOfTrailingZeros(m)
            out += Position(i % boardSize, i / boardSize)
            m = m and (m - 1)
        }
        return out
    }

    /** Sorteo proporcional a los pesos. */
    private fun weightedPick(weights: DoubleArray, random: Random): Int {
        val total = weights.sum()
        if (total <= 0.0) return random.nextInt(weights.size)
        var r = random.nextDouble() * total
        for (i in weights.indices) {
            r -= weights[i]
            if (r <= 0.0) return i
        }
        return weights.size - 1
    }

    /**
     * **POST-PASE DE GEMELAS** (P-76). Coge un plan YA VÁLIDO y sustituye una de sus fichas por una
     * segunda copia de otra, si existe un hueco libre donde quepa **sin pisar** las reservas que se
     * conservan — así la bandeja sigue cabiendo junta.
     *
     * Si no lo encuentra —o si la bandeja resultante rompería la garantía de bandeja mixta
     * ([TwinTrayRules.allows])— devuelve el plan **intacto**.
     */
    private fun applyTwins(
        plan: List<PlannedShape>,
        boardSize: Int,
        boardHeight: Int,
        blockedCells: Set<Position>,
        splitAtX: Int,
        /** El nivel decide **a cuál** de las fichas se le copia la forma; ver
         *  [TwinTrayRules.preferenceOrderFor]. */
        level: Int,
    ): List<PlannedShape> {
        if (plan.size < 2) return plan
        val sizes = plan.map { it.shape.size }
        for (source in TwinTrayRules.preferenceOrderFor(level, sizes)) {
            val shape = plan[source].shape
            if (shape.size <= 1) continue
            for (replaced in plan.indices) {
                if (replaced == source) continue
                if (!TwinTrayRules.allows(sizes, shape.size, replaced)) continue
                val keep = HashSet<Position>(blockedCells)
                plan.forEachIndexed { i, item -> if (i != replaced) keep.addAll(item.reservedCells) }
                val spot = placementsFor(shape, boardSize, boardHeight, keep, splitAtX).firstOrNull()
                    ?: continue
                return plan.toMutableList().also {
                    it[replaced] = PlannedShape(shape = shape, reservedCells = spot)
                }
            }
        }
        return plan
    }

    private fun safeFallback(
        playableCells: Set<Position>,
        desiredCount: Int,
        random: Random
    ): List<PlannedShape> = playableCells
        .shuffled(random)
        .take(desiredCount)
        .map { cell ->
            PlannedShape(
                shape = listOf(Position(0, 0)),
                reservedCells = setOf(cell)
            )
        }

    /**
     * ¿Cabe [shape] en ALGÚN sitio del tablero, con [occupied] ocupado, en alguna orientación?
     * Lo usa P-77 (la ficha que crece) antes de engordar una ficha.
     */
    fun shapeFitsAnywhere(
        shape: List<Position>,
        boardSize: Int,
        boardHeight: Int,
        occupied: Set<Position>,
        splitAtX: Int = -1,
    ): Boolean {
        var rotated = shape
        repeat(4) {
            if (placementsFor(rotated, boardSize, boardHeight, occupied, splitAtX).isNotEmpty()) {
                return true
            }
            rotated = Piece(shape = rotated, colorType = 0).rotate().shape
        }
        return false
    }

    /**
     * ¿Caben TODAS estas formas a la vez, sin pisarse, con [blockedCells] ocupado? Búsqueda pequeña
     * con techo de nodos ([FIT_TOGETHER_NODES]) que usa la bandeja de apertura. Sin techo, esta clase
     * de búsqueda ya provocó un ANR (2026-08-24). Si se agota el techo, responde `false`.
     */
    fun fitsTogether(
        shapes: List<List<Position>>,
        blockedCells: Set<Position>,
        boardSize: Int = GameState.GRID_SIZE,
    ): Boolean {
        var nodes = FIT_TOGETHER_NODES
        val ordered = shapes.sortedByDescending { it.size }
        fun go(i: Int, occupied: Set<Position>): Boolean {
            if (i == ordered.size) return true
            if (nodes-- <= 0) return false
            for (cells in placementsFor(ordered[i], boardSize, boardSize, occupied, -1)) {
                if (go(i + 1, occupied + cells)) return true
                if (nodes <= 0) return false
            }
            return false
        }
        return go(0, blockedCells)
    }

    /**
     * ¿Es válida la orientación que el reparto le dio a cada ficha? Toda ficha con colocación
     * (`reservedCells` no vacío) tiene que caber SOLA en ella: tantas celdas como la forma, ninguna
     * ocupada, dentro del tablero y sin cruzar el Corte Ninja.
     */
    fun reservationsAreValid(
        plan: List<PlannedShape>,
        blockedCells: Set<Position>,
        boardSize: Int = GameState.GRID_SIZE,
        splitAtX: Int = -1,
    ): Boolean = plan.all { item ->
        val cells = item.reservedCells
        if (cells.isEmpty()) return@all true
        cells.size == item.shape.size &&
            cells.none { it in blockedCells } &&
            cells.all { it.x in 0 until boardSize && it.y in 0 until boardSize } &&
            (splitAtX < 0 || cells.all { it.x < splitAtX } || cells.all { it.x >= splitAtX })
    }

    private fun placementsFor(
        shape: List<Position>,
        boardSize: Int,
        boardHeight: Int,
        occupied: Set<Position>,
        splitAtX: Int = -1
    ): List<Set<Position>> {
        val width = (shape.maxOfOrNull { it.x } ?: 0) + 1
        val height = (shape.maxOfOrNull { it.y } ?: 0) + 1
        if (width > boardSize || height > boardHeight) return emptyList()

        val result = mutableListOf<Set<Position>>()
        for (y in 0..(boardHeight - height)) {
            for (x in 0..(boardSize - width)) {
                // Corte Ninja: descartar de raíz lo que cruce la frontera. La ficha ocupa
                // [x, x+width-1]; cruza si empieza a la izquierda del corte y termina en él o pasado.
                if (splitAtX > 0 && x < splitAtX && x + width - 1 >= splitAtX) continue
                val cells = shape.mapTo(mutableSetOf()) { Position(x + it.x, y + it.y) }
                if (cells.none { it in occupied }) result += cells
            }
        }
        return result
    }

    private fun uniqueRotations(shape: List<Position>): List<List<Position>> {
        val rotations = mutableListOf<List<Position>>()
        var current = normalize(shape)
        repeat(4) {
            if (current !in rotations) rotations += current
            current = normalize(current.map { Position(-it.y, it.x) })
        }
        return rotations
    }

    private fun normalize(shape: List<Position>): List<Position> {
        val minX = shape.minOf { it.x }
        val minY = shape.minOf { it.y }
        return shape
            .map { Position(it.x - minX, it.y - minY) }
            .sortedWith(compareBy({ it.y }, { it.x }))
    }

    private fun canonicalShape(shape: List<Position>): String =
        uniqueRotations(shape)
            .map { rotation -> rotation.joinToString(";") { "${it.x},${it.y}" } }
            .minOrNull()
            .orEmpty()
}
