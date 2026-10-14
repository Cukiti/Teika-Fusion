package com.cukiti.teikafusion.model

data class ReactorPlan(val cells: List<Position>, val depths: List<Int>, val links: List<Pair<Position, Position>>) {
    fun hit(index: Int) = 220 + depths[index] * 55
    val durationMs get() = hit(cells.lastIndex) + 300
}

data class ReactorAim(val origin: Position? = null, val feedback: String = "Elige un grupo de color")

/** A breadth-first reaction: sides connect, protections and Ninja boundaries never conduct. */
object ReactorRules {
    fun appliesTo(level: Int) = HouseRule.isActive(level, HouseRule.TRAIL)
    fun blocked(s: GameState, bodies: Set<Position>) = CompactorRules.blocked(s, bodies) +
        s.grid.filterValues { it is CellContent.Wood && (it.armor > 0 || it.poisoned || it.stubborn || it.magma) }.keys

    fun plan(s: GameState, bodies: Set<Position>, origin: Position): ReactorPlan? {
        val blocked = blocked(s, bodies)
        val color = (s.grid[origin] as? CellContent.Wood)?.colorType ?: return null
        if (origin in blocked) return null
        val cells = mutableListOf(origin)
        val depths = mutableListOf(0)
        val links = mutableListOf<Pair<Position, Position>>()
        val seen = hashSetOf(origin)
        var i = 0
        while (i < cells.size) {
            val p = cells[i]
            for (d in DominoRules.steps) {
                val q = Position(p.x + d.x, p.y + d.y)
                if (q.x !in 0 until s.gridW || q.y !in 0 until s.gridH || q in blocked || q in seen ||
                    (s.ninjaCutActive && (p.x < 4) != (q.x < 4)) ||
                    (s.grid[q] as? CellContent.Wood)?.colorType != color) continue
                seen.add(q); cells.add(q); depths.add(depths[i] + 1); links.add(p to q)
            }
            i++
        }
        return if (cells.size >= 3) ReactorPlan(cells, depths, links) else null
    }

    fun hasGroup(s: GameState, bodies: Set<Position>): Boolean {
        val seen = HashSet<Position>()
        return s.grid.keys.any { p -> p !in seen && plan(s, bodies, p)?.let { seen.addAll(it.cells); true } == true }
    }
}
