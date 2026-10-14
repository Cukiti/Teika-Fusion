package com.cukiti.teikafusion.model

import kotlin.random.Random

/**
 * **P-294 · LLUVIA DE METEORITOS** — la mecánica del Espacio Profundo desde el 2026-09-25 (decisión del
 * usuario; sustituye a la Brecha en el casco).
 *
 * Si dejas de romper líneas, una MIRA recorre el tablero buscando (aviso 1); en el segundo turno FIJA
 * el CRÁTER 2×2 donde caerá —el que más bloques tiene— (aviso 2); en el tercero cae el meteorito: hasta
 * [MAX_ROCKS] bloques del cráter se funden en ROCA ESPACIAL y los Teikas salen despedidos.
 *
 * **La roca** (decisión del usuario): un bloque BLINDADO ([petrify]) que sigue contando para completar
 * líneas, pero necesita DOS: la primera línea le arranca la roca y la segunda rompe el bloque. Las
 * casillas vacías del cráter siguen libres.
 *
 * **Contrajuego**: romper una línea que cruce el cráter marcado lo DESVÍA, en cualquiera de los turnos
 * de aviso. Romper otra solo congela el reloj ([BiomeClock]).
 *
 * ## P-319 · El Espacio Profundo se endurece (2026-09-26, decisión del usuario)
 *
 *  - **Metralla** ([shardsFor]): el impacto lanza [SHARDS] fragmentos de roca a casillas VACÍAS junto al
 *    cráter. Llenan el tablero, que es lo que aprieta en un juego de encajar. Nunca cierran una línea.
 *  - **La roca se agrieta** ([tickRocks]): una roca que sigue en el tablero [CRACK_TURNS] colocaciones
 *    contagia a un bloque vecino. Aviso: la grieta asoma (1.ª colocación) y brilla (2.ª); a la 3.ª
 *    contagia. Contrajuego: romperla (dos líneas) antes. Tope de [MAX_BOARD_ROCKS] rocas en el tablero.
 *  - **Lluvia doble** ([doubleShowerFor]): en el último tercio del capítulo caen DOS meteoritos a la vez,
 *    con dos cráteres que no se tocan. Cada uno se desvía por separado.
 */
object MeteorShower {

    /** Lado del cráter, en casillas. */
    const val SIZE = 2

    /**
     * Cuántos bloques del cráter funde como mucho un impacto (2026-09-25, decisión del usuario). Con los
     * cuatro, la roca se acumulaba y ahogaba el tablero: el bot pasó de 8/10 a 3/10 en el Galáctico.
     */
    const val MAX_ROCKS = 2   // P-352: 2 → 3 (P-294 lo había bajado de 4 a 2) · P-359: 3 → 2 (Espacio 35 %, objetivo 55 %)

    /** P-319: fragmentos de roca que lanza cada impacto. */
    const val SHARDS = 2   // P-352: 2 → 3 · P-359: 3 → 2

    /** P-319: distancia (en casillas, contando diagonales) del cráter a la que puede caer un fragmento. */
    const val SHARD_REACH = 2

    /** P-319: colocaciones que aguanta una roca antes de contagiar a un vecino. */
    const val CRACK_TURNS = 3

    /** P-319: con este número de rocas en el tablero ya no se contagia ninguna más (la roca no ahoga). */
    const val MAX_BOARD_ROCKS = 8

    /** P-319: fracción final del capítulo en la que caen dos meteoritos (el último tercio). */
    const val DOUBLE_SHOWER_FROM = 2.0 / 3.0

    /** P-361: ¿cae la lluvia doble del último tercio del capítulo? */
    const val DOUBLE_SHOWER = false

    /** Las casillas del cráter cuya esquina de arriba a la izquierda es [at]. */
    fun craterCells(at: Position): List<Position> =
        (0 until SIZE).flatMap { dy -> (0 until SIZE).map { dx -> Position(at.x + dx, at.y + dy) } }

    /** Los bloques que fundiría el impacto: los del cráter que aún no son roca, como mucho [MAX_ROCKS]. */
    fun rocksFor(at: Position, grid: Map<Position, CellContent>): List<Position> =
        craterCells(at).filter { (grid[it] as? CellContent.Wood)?.let { w -> !w.rock } == true }.take(MAX_ROCKS)

    /** Lo que fundiría el impacto (ver [rocksFor]). */
    fun damage(at: Position, grid: Map<Position, CellContent>): Int = rocksFor(at, grid).size

    /** Todas las esquinas posibles de un cráter 2×2 en un tablero de [size]. */
    fun allCorners(size: Int = GameState.GRID_SIZE): List<Position> =
        (0..size - SIZE).flatMap { y -> (0..size - SIZE).map { x -> Position(x, y) } }

    /**
     * Dónde fija el meteorito su cráter: el que más bloques fundiría, sin tocar casillas vetadas
     * ([blocked]: rincones del Glotón, el huevo, el otro cráter…). Empates → el primero de [order], que
     * el llamador baraja. Null si ningún cráter tiene nada.
     */
    fun choose(
        grid: Map<Position, CellContent>,
        blocked: Set<Position> = emptySet(),
        order: List<Position> = allCorners(),
    ): Position? {
        var best: Position? = null
        var bestDamage = 0
        for (at in order) {
            if (craterCells(at).any { it in blocked }) continue
            val d = damage(at, grid)
            if (d > bestDamage) { best = at; bestDamage = d }
        }
        return best
    }

    /**
     * P-319: el SEGUNDO cráter de la lluvia doble. No puede pisar ni tocar el primero (su cráter y el
     * anillo de alrededor quedan vetados): dos cráteres pegados se leerían como uno de 4×2.
     */
    fun chooseSecond(
        grid: Map<Position, CellContent>,
        first: Position,
        blocked: Set<Position> = emptySet(),
        order: List<Position> = allCorners(),
    ): Position? {
        val halo = craterCells(first).flatMap { c ->
            (-1..1).flatMap { dy -> (-1..1).map { dx -> Position(c.x + dx, c.y + dy) } }
        }
        return choose(grid, blocked + halo, order)
    }

    /**
     * P-319: ¿caen DOS meteoritos en [level]? En el último tercio del capítulo del Espacio Profundo
     * (por nivel dentro del capítulo, sin azar): la dificultad sube dentro del bioma.
     */
    fun doubleShowerFor(level: Int): Boolean {
        // P-361 (2026-09-28): apagada. Con el reparto robusto el Espacio quedó en el 43 % (objetivo 55 %) y la
        // lluvia doble del último tercio es lo que más fragmenta el tablero. La regla se queda por si vuelve.
        if (!DOUBLE_SHOWER) return false
        val chapter = LevelMap.chapterIndexForLevel(level)
        if (chapter < 0) return false
        val size = LevelMap.chapters[chapter].levelCount
        if (size <= 1) return false
        return LevelMap.localIndex(level).toDouble() / (size - 1) >= DOUBLE_SHOWER_FROM
    }

    /** ¿Alguna casilla rota este turno está en el cráter marcado? El meteorito se desvía. */
    fun deflectedBy(at: Position?, clearedCells: Collection<Position>): Boolean {
        if (at == null) return false
        val crater = craterCells(at).toSet()
        return clearedCells.any { it in crater }
    }

    /** El bloque fundido en roca: blindado (una línea extra) y con la cara de roca. */
    fun petrify(w: CellContent.Wood): CellContent.Wood =
        w.copy(armor = maxOf(w.armor, 1), rock = true, rockAge = 0)

    /**
     * P-319 · LA METRALLA: dónde caen los fragmentos del impacto en [at]. Solo casillas VACÍAS a como mucho
     * [SHARD_REACH] del cráter (primero las más cercanas), que no estén en [unusable] (Teikas, lava,
     * rincones del Glotón, el huevo, lo ya elegido…) y que no CIERREN una fila o columna: un fragmento que
     * completara una línea sería un regalo, no un castigo. [filled] = lo que cuenta como lleno para esa
     * comprobación además de la madera (los Teikas, que son comodín).
     */
    fun shardsFor(
        at: Position,
        grid: Map<Position, CellContent>,
        unusable: Set<Position>,
        filled: Set<Position>,
        random: Random,
        count: Int = SHARDS,
        size: Int = GameState.GRID_SIZE,
    ): List<Position> {
        val crater = craterCells(at).toSet()
        fun distance(p: Position) = crater.minOf { maxOf(kotlin.math.abs(it.x - p.x), kotlin.math.abs(it.y - p.y)) }
        val full = HashSet<Position>(filled).apply {
            grid.forEach { (p, c) -> if (c.isOccupied) add(p) }
        }
        val candidates = (0 until size).flatMap { y -> (0 until size).map { x -> Position(x, y) } }
            .filter { it !in crater && it !in full && it !in unusable && distance(it) <= SHARD_REACH }
            .shuffled(random)
            .sortedBy(::distance)
        val chosen = ArrayList<Position>(count)
        for (p in candidates) {
            if (chosen.size == count) break
            full += p
            val closesRow = (0 until size).all { x -> Position(x, p.y) in full }
            val closesCol = (0 until size).all { y -> Position(p.x, y) in full }
            if (closesRow || closesCol) { full -= p; continue }
            chosen += p
        }
        return chosen
    }

    /** P-319: el fragmento de metralla es roca desde que cae (blindado, necesita dos líneas). */
    fun shard(colorType: Int): CellContent.Wood = petrify(CellContent.Wood(colorType = colorType))

    /** Un contagio: la roca de [from] agrieta el bloque de [to]. */
    data class Crack(val from: Position, val to: Position)

    /**
     * P-319 · LA ROCA SE AGRIETA: una colocación más para cada roca. La que llega a [CRACK_TURNS] contagia
     * a un bloque vecino (arriba, abajo, izquierda o derecha) que no sea roca ni esté en [protected], y
     * vuelve a 0. Sin vecino posible, o con [MAX_BOARD_ROCKS] rocas en el tablero, espera en su último
     * aviso. Devuelve el tablero con las EDADES ya avanzadas y los contagios por aplicar: el bloque
     * contagiado se funde cuando lo dibuja su VFX (el estado no cambia antes que su animación).
     */
    fun tickRocks(
        grid: Map<Position, CellContent>,
        random: Random,
        protected: Set<Position> = emptySet(),
    ): Pair<Map<Position, CellContent>, List<Crack>> {
        val rocks = grid.entries
            .filter { (_, c) -> (c as? CellContent.Wood)?.rock == true }
            .map { it.key }
            .sortedWith(compareBy({ it.y }, { it.x }))
        if (rocks.isEmpty()) return grid to emptyList()
        val out = grid.toMutableMap()
        val cracks = ArrayList<Crack>()
        val targeted = HashSet<Position>()
        var total = rocks.size
        for (p in rocks) {
            val w = out[p] as CellContent.Wood
            val age = w.rockAge + 1
            if (age < CRACK_TURNS) { out[p] = w.copy(rockAge = age); continue }
            val neighbour = if (total >= MAX_BOARD_ROCKS) null else listOf(
                Position(p.x, p.y - 1), Position(p.x, p.y + 1), Position(p.x - 1, p.y), Position(p.x + 1, p.y),
            ).filter { n ->
                val nw = out[n] as? CellContent.Wood
                nw != null && !nw.rock && n !in protected && n !in targeted
            }.randomOrNull(random)
            if (neighbour == null) { out[p] = w.copy(rockAge = CRACK_TURNS); continue }
            out[p] = w.copy(rockAge = 0)
            targeted += neighbour
            cracks += Crack(p, neighbour)
            total++
        }
        return out to cracks
    }

    /** P-319: ¿se ve la grieta de esta roca? 0 = no, 1 = asoma, 2 = brilla (contagia en la siguiente). */
    fun crackStage(w: CellContent.Wood): Int =
        if (!w.rock) 0 else (w.rockAge - (CRACK_TURNS - 3)).coerceIn(0, 2)
}
