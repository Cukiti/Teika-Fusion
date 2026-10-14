package com.cukiti.teikafusion.model

/** Pure rules shared by previews, commits and save restoration. */
object DominoRules {
    val steps = listOf(Position(1, 0), Position(0, 1), Position(-1, 0), Position(0, -1))
    fun appliesTo(level: Int) = false // Legacy pure helpers; WreckingBall owns the slot.
    fun next(p: Position, direction: Int): Position? = steps.getOrNull(direction)?.let { Position(p.x + it.x, p.y + it.y) }
    fun seed(p: Position, occupied: Set<Position>, level: Int): Int {
        if (!appliesTo(level)) return -1
        val candidates = steps.indices.filter { next(p, it) in occupied }
        // A serpentine ordering prevents short back-and-forth loops in the opening network.
        fun order(q: Position) = q.y * 16 + if (q.y % 2 == 0) q.x else 15 - q.x
        return candidates.filter { order(next(p,it)!!) > order(p) }.minByOrNull { order(next(p,it)!!) }
            ?: candidates.maxByOrNull { order(next(p,it)!!) } ?: -1
    }
    data class Hit(val cell: Position, val wood: CellContent.Wood, val broken: Boolean)
    fun cascade(original: Map<Position, CellContent>, afterLines: Map<Position, CellContent>, protected: Set<Position>, roots: Set<Position> = original.keys, splitAtX: Int = -1): List<Hit> {
        val queue = ArrayDeque<Position>()
        original.forEach { (p, c) -> if (p in roots && c is CellContent.Wood && c.dominoDir >= 0 && afterLines[p] is CellContent.Empty) queue.add(p) }
        val seen = mutableSetOf<Position>()
        val hits = mutableListOf<Hit>()
        while (queue.isNotEmpty()) {
            val from = queue.removeFirst()
            val source = original[from] as? CellContent.Wood ?: continue
            val p = next(from, source.dominoDir) ?: continue
            if (splitAtX > 0 && from.y == p.y && minOf(from.x,p.x) < splitAtX && maxOf(from.x,p.x) >= splitAtX) continue
            if (!seen.add(p) || p in protected) continue
            val wood = afterLines[p] as? CellContent.Wood
            if (wood == null) {
                // Another line may have removed this marked neighbor in the same pass.
                if ((original[p] as? CellContent.Wood)?.dominoDir?.let { it >= 0 } == true) queue.add(p)
                continue
            }
            if (wood.coil > 0) continue
            hits.add(Hit(p, wood, wood.armor == 0 && !wood.poisoned))
            if (hits.last().broken && wood.dominoDir >= 0) queue.add(p)
        }
        return hits
    }
}

object ConstellationRules {
    fun appliesTo(level: Int) = HouseRule.forLevel(level) == HouseRule.CONSTELLATION
    fun points(level: Int, wave: Int, free: Set<Position>, wood: Set<Position> = emptySet()): List<Position> {
        val pool = free.sortedWith(compareBy<Position> { (it.x * 19 + it.y * 31 + level * 7 + wave * 11).mod(67) }
            .thenBy { it.y }.thenBy { it.x }).take(24)
        if (wood.isEmpty() || pool.size < 3) return pool.take(3)
        var best = pool.take(3); var score = -1
        // Bounded search once per constellation, never per physics frame.
        for (i in pool.indices) for (j in i + 1 until pool.size) for (k in j + 1 until pool.size) {
            val triple = listOf(pool[i],pool[j],pool[k])
            val hits = path(triple).count { it in wood }
            if (hits > score) { score = hits; best = triple }
        }
        return best
    }
    /** A grid-aligned staircase connects each pair; includes endpoints, never outside the board. */
    fun trace(points: List<Position>): List<Position> = buildList {
        points.zipWithNext().forEach { (a, b) ->
            var x = a.x; var y = a.y; add(a)
            while (x != b.x || y != b.y) {
                if (x != b.x) x += if (b.x > x) 1 else -1
                else y += if (b.y > y) 1 else -1
                add(Position(x, y))
            }
        }
    }
    fun path(points: List<Position>): Set<Position> = trace(points).toSet()
}

object TrampolineRules {
    fun appliesTo(level: Int) = false // Compatibility helper; Pinball owns this slot.
    fun cell(level: Int, width: Int, height: Int, free: Set<Position>? = null): Position {
        val floor = free?.filter { it.y == height - 1 }?.sortedBy { it.x }.orEmpty()
        return if (floor.isNotEmpty()) floor[level.mod(floor.size)] else Position(1 + level.mod((width - 2).coerceAtLeast(1)), height - 1)
    }
    fun horizontalSpeed(level: Int) = if (level % 2 == 0) 4f else -4f
}

object PrecisionCut {
    fun appliesTo(level: Int) = false // Retained split helpers only for compatibility tests.
    fun separators(piece: Piece): List<Pair<Int, Int>> {
        if (piece.shape.size < 2 || piece.isFichona || WorkshopPlanner.hasWholePieceProperty(piece)) return emptyList()
        val cells = piece.shape.toSet()
        fun connected(part: Set<Position>): Boolean {
            if (part.isEmpty()) return false
            val seen = mutableSetOf(part.first()); val queue = ArrayDeque(seen)
            while (queue.isNotEmpty()) {
                val p = queue.removeFirst()
                DominoRules.steps.forEach { d -> val q = Position(p.x + d.x, p.y + d.y); if (q in part && seen.add(q)) queue.add(q) }
            }
            return seen.size == part.size
        }
        val candidates = mutableListOf<Pair<Pair<Int, Int>, Int>>()
        for (axis in 0..1) {
            val max = if (axis == 0) piece.width else piece.height
            for (boundary in 1 until max) {
                val a = cells.filterTo(linkedSetOf()) { (if (axis == 0) it.x else it.y) < boundary }
                val b = cells - a
                if (connected(a) && connected(b)) candidates += (axis to boundary) to kotlin.math.abs(a.size - b.size)
            }
        }
        return candidates.sortedBy { it.second }.map { it.first }
    }
    fun separator(piece: Piece, choice: Int = 0): Pair<Int, Int>? = separators(piece).take(2).getOrNull(choice)
    fun split(piece: Piece, choice: Int = 0): Pair<Piece, Piece>? {
        val (axis, boundary) = separator(piece, choice) ?: return null
        val a = piece.shape.filterTo(linkedSetOf()) { (if (axis == 0) it.x else it.y) < boundary }
        val cut = a to (piece.shape.toSet() - a)
        fun half(part: Set<Position>): Piece {
            val indices = piece.shape.indices.filter { piece.shape[it] in part }
            val minX = part.minOf { it.x }; val minY = part.minOf { it.y }
            return piece.copy(id = java.util.UUID.randomUUID().toString(),
                shape = indices.map { Position(piece.shape[it].x - minX, piece.shape[it].y - minY) },
                targetMask = indices.map { piece.targetMask.getOrNull(it) },
                crystalMask = indices.map { piece.crystalMask.getOrNull(it) == true },
                barrelMask = indices.map { piece.barrelMask.getOrNull(it) == true },
                teikaMask = indices.map { piece.teikaMask.getOrNull(it) == true })
        }
        return half(cut.first) to half(cut.second)
    }
}
