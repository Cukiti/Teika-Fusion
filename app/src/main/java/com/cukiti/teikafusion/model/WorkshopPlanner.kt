package com.cukiti.teikafusion.model

/** A fabrication proposal owns no game state. Its reservations are checked again at commit. */
data class WorkshopPlan(
    val pieces: List<Piece>,
    val reservations: List<Set<Position>>,
    val available: Int,
) {
    val used: Int get() = pieces.sumOf { it.shape.size }
    val remainder: Int get() = available - used
}

data class WorkshopOffer(
    val sources: List<Piece>,
    val bank: BlockBank,
    val plan: WorkshopPlan? = null,
    val refusal: String? = null,
)

/** One connected Fichona in the largest playable region; no fallback to a smaller region. */
object WorkshopPlanner {
    const val MAX_CELLS = 16
    fun hasWholePieceProperty(p: Piece) =
        p.isWildcard || p.isStarWish || p.isBrush || p.isChameleon || p.isGrowing || p.isMagnet
    fun materialBudget(bank: BlockBank, sources: List<Piece>): Int =
        bank.blocks + sources.sumOf { BlockBankRules.blocksFor(it.shape.size) }

    fun plan(bank: BlockBank, sources: List<Piece>, width: Int, height: Int,
        helpfulCells: Set<Position> = emptySet(), canPlace: (Piece, Int, Int) -> Boolean,
    ): WorkshopPlan? {
        require(width > 0 && height > 0 && width * height <= 64)
        if (sources.size !in 1..3 || sources.any { hasWholePieceProperty(it) || it.isFichona }) return null
        val budget = materialBudget(bank, sources)
        val annotations = sources.flatMap { p -> p.shape.indices.mapNotNull { i ->
            if (p.targetMask.getOrNull(i) != null || p.crystalMask.getOrNull(i) == true ||
                p.barrelMask.getOrNull(i) == true || p.teikaMask.getOrNull(i) == true) p to i else null
        } }
        val minimum = maxOf(3, annotations.size)
        if (budget < minimum || minimum > MAX_CELLS) return null
        val color = sources.groupBy { it.colorType }.maxBy { (_, ps) -> ps.sumOf { it.shape.size } }.key
        val probe = Piece(shape = listOf(Position(0, 0)), colorType = color)
        val free = buildSet { for (y in 0 until height) for (x in 0 until width)
            if (canPlace(probe, x, y)) add(Position(x, y)) }
        fun neighbors(p: Position): List<Position> = listOf(Position(p.x - 1, p.y), Position(p.x + 1, p.y),
            Position(p.x, p.y - 1), Position(p.x, p.y + 1)).filter { q ->
            if (q !in free) false else {
                // A playable cell on either side of the Ninja cut does not connect across the cut.
                val x = minOf(p.x, q.x); val y = minOf(p.y, q.y)
                canPlace(probe.copy(shape = listOf(Position(p.x - x, p.y - y), Position(q.x - x, q.y - y))), x, y)
            }
        }
        val unseen = free.toMutableSet()
        val regions = mutableListOf<Set<Position>>()
        while (unseen.isNotEmpty()) {
            val region = linkedSetOf(unseen.first()); val queue = ArrayDeque<Position>()
            queue.add(region.first()); unseen.remove(region.first())
            while (queue.isNotEmpty()) for (q in neighbors(queue.removeFirst()))
                if (unseen.remove(q)) { region.add(q); queue.add(q) }
            regions.add(region)
        }
        val largest = regions.maxOfOrNull { it.size } ?: return null
        val size = minOf(budget, MAX_CELLS, largest)
        if (size < minimum) return null
        var bestCells: List<Position>? = null
        var bestPiece: Piece? = null
        var bestScore = Int.MIN_VALUE
        for (region in regions.filter { it.size == largest }) for (start in region) {
            val selected = linkedSetOf(start)
            while (selected.size < size) {
                val frontier = selected.flatMap { neighbors(it) }.filter { it in region && it !in selected }.distinct()
                val next = frontier.maxWithOrNull(compareBy<Position>(
                    { if (it in helpfulCells) 100 else 0 },
                    { q -> selected.count { it.y == q.y } * 4 + selected.count { it.x == q.x } },
                    { -it.y }, { -it.x })) ?: break
                selected.add(next)
            }
            if (selected.size != size) continue
            val cells = selected.sortedWith(compareBy({ it.y }, { it.x }))
            val x = cells.minOf { it.x }; val y = cells.minOf { it.y }
            val shape = cells.map { Position(it.x - x, it.y - y) }
            val piece = Piece(shape = shape, colorType = color, isHandmade = true, isFichona = true,
                targetMask = List(size) { i -> annotations.getOrNull(i)?.let { (p, idx) -> p.targetMask.getOrNull(idx) } },
                crystalMask = List(size) { i -> annotations.getOrNull(i)?.let { (p, idx) -> p.crystalMask.getOrNull(idx) } == true },
                barrelMask = List(size) { i -> annotations.getOrNull(i)?.let { (p, idx) -> p.barrelMask.getOrNull(idx) } == true },
                teikaMask = List(size) { i -> annotations.getOrNull(i)?.let { (p, idx) -> p.teikaMask.getOrNull(idx) } == true })
            if (!canPlace(piece, x, y)) continue
            val score = cells.count { it in helpfulCells } * 100 +
                cells.groupBy { it.y }.values.sumOf { it.size * it.size } + cells.groupBy { it.x }.values.sumOf { it.size * it.size }
            if (score > bestScore) { bestScore = score; bestCells = cells; bestPiece = piece }
        }
        return bestPiece?.let { WorkshopPlan(listOf(it), listOf(requireNotNull(bestCells).toSet()), budget) }
    }
}
