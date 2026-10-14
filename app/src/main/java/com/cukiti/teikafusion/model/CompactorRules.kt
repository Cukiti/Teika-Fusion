package com.cukiti.teikafusion.model

/** Direction order matches the board: right, down, left, up. The entry stops at its first anchor. */
data class CompactorPlan(val direction: Int, val cells: List<Position>, val moves: List<Pair<Position, Position>>)

object CompactorRules {
    const val PREPARE_MS = 180
    const val MOVE_MS = 620
    const val TOTAL_MS = 1000
    fun appliesTo(level: Int) = HouseRule.isActive(level, HouseRule.PRECISION_CUT)
    fun blocked(s: GameState, bodies: Set<Position>) = bodies + s.unplaceableCells + s.frozenCells +
        s.glacierFrostCells + s.starAstroTargetCells + s.starAstroMoonWoodCells + s.hiddenWoodCells +
        s.gluttonDeadCells.keys + s.grid.keys.filter { s.lavaActive && it.y < s.lavaRow } +
        s.grid.filterValues { it !is CellContent.Empty && (it !is CellContent.Wood || it.coil > 0) }.keys

    fun plan(s: GameState, bodyCells: Set<Position>, x: Int, y: Int, direction: Int): CompactorPlan? {
        if (x !in 0 until s.gridW || y !in 0 until s.gridH || direction !in 0..3) return null
        val horizontal = direction % 2 == 0
        val reverse = direction >= 2
        val length = if (horizontal) s.gridW else s.gridH
        val blocked = blocked(s, bodyCells)
        val corridor = mutableListOf<Position>()
        for (n in 0 until length) {
            val i = if (reverse) length - 1 - n else n
            val p = if (horizontal) Position(i, y) else Position(x, i)
            // Never cross the Ninja cut, even when its cells are empty.
            if (horizontal && s.ninjaCutActive && corridor.lastOrNull()?.let { (it.x < 4) != (p.x < 4) } == true) break
            if (p !in s.grid || p in blocked) break
            corridor.add(p)
        }
        val wood = corridor.filter { s.grid[it] is CellContent.Wood }
        val destinations = corridor.takeLast(wood.size)
        val moves = wood.zip(destinations).filter { it.first != it.second }
        return CompactorPlan(direction, corridor, moves)
    }

    fun hasMove(s: GameState, bodies: Set<Position>): Boolean = (0..3).any { d ->
        (0 until if (d % 2 == 0) s.gridH else s.gridW).any { i ->
            plan(s, bodies, if (d % 2 == 0) 0 else i, if (d % 2 == 0) i else 0, d)?.moves?.isNotEmpty() == true
        }
    }
}
