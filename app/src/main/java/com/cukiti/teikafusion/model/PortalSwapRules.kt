package com.cukiti.teikafusion.model

/** The two footprints exchange all four cells, including holes, with no rotation or copying. */
data class PortalSwapPlan(val first: Position, val second: Position, val moves: List<Pair<Position, Position>>) {
    val cells get() = PortalSwapRules.cells(first) + PortalSwapRules.cells(second)
}

/** Transient selection. Only the committed board and pending chain are saved. */
data class PortalSwapAim(val first: Position? = null, val second: Position? = null, val feedback: String = "Elige una zona")

object PortalSwapRules {
    const val PREPARE_MS = 160
    const val TRANSFER_MS = 620
    const val TOTAL_MS = 960
    fun appliesTo(level: Int) = HouseRule.isActive(level, HouseRule.ROUTES)
    fun cells(anchor: Position) = (0..1).flatMap { y -> (0..1).map { x -> Position(anchor.x+x,anchor.y+y) } }
    fun zoneError(s: GameState, bodies: Set<Position>, anchor: Position): String? = zoneError(s,bodies,anchor,CompactorRules.blocked(s,bodies))
    private fun zoneError(s: GameState, bodies: Set<Position>, anchor: Position, blocked: Set<Position>): String? {
        val cells=cells(anchor)
        if(cells.any { it.x !in 0 until s.gridW || it.y !in 0 until s.gridH || it !in s.grid }) return "Elige una zona de 2×2"
        if(s.ninjaCutActive && cells.map { it.x<4 }.distinct().size>1) return "El corte bloquea esta zona"
        if(cells.any { it in bodies }) return "Hay un Teika en esta zona"
        if(cells.any { it in blocked }) return "Zona protegida"
        return null
    }
    fun error(s: GameState, bodies: Set<Position>, a: Position, b: Position): String? {
        zoneError(s,bodies,a)?.let { return it }; zoneError(s,bodies,b)?.let { return it }
        if(cells(a).any { it in cells(b) }) return "Separa las dos zonas"
        if(s.ninjaCutActive && (a.x<4)!=(b.x<4)) return "El corte separa los portales"
        if(unchanged(s,a,b)) return "El intercambio no cambia nada"
        return null
    }
    fun plan(s: GameState, bodies: Set<Position>, a: Position, b: Position): PortalSwapPlan? {
        if(error(s,bodies,a,b)!=null) return null
        val pairs=cells(a).zip(cells(b))
        return PortalSwapPlan(a,b,pairs.flatMap { (from,to) ->
            listOfNotNull((from to to).takeIf { s.grid[from] is CellContent.Wood },
                (to to from).takeIf { s.grid[to] is CellContent.Wood })
        })
    }
    fun apply(grid: Map<Position,CellContent>, plan: PortalSwapPlan): Map<Position,CellContent> {
        val result=grid.toMutableMap()
        cells(plan.first).zip(cells(plan.second)).forEach { (a,b) -> result[a]=grid.getValue(b); result[b]=grid.getValue(a) }
        return result
    }
    private fun unchanged(s: GameState,a: Position,b: Position): Boolean {
        fun identityFree(c: CellContent?) = if(c is CellContent.Wood) c.copy(id="",skipSpawnAnimation=false) else c
        return cells(a).zip(cells(b)).all { (x,y) -> identityFree(s.grid[x])==identityFree(s.grid[y]) }
    }
    fun hasMove(s: GameState, bodies: Set<Position>): Boolean {
        val blocked=CompactorRules.blocked(s,bodies)
        val zones=s.grid.keys.filter { zoneError(s,bodies,it,blocked)==null }
        // At most 49 four-cell signatures; do not allocate hundreds of grids or wood copies per pair.
        val signatures=zones.map { a -> cells(a).map { p ->
            val c=s.grid[p]; if(c is CellContent.Wood) c.copy(id="",skipSpawnAnimation=false) else c
        } }
        return zones.indices.any { i -> (i+1 until zones.size).any { j ->
            val a=zones[i]; val b=zones[j]
            (!s.ninjaCutActive || (a.x<4)==(b.x<4)) &&
                (kotlin.math.abs(a.x-b.x)>=2 || kotlin.math.abs(a.y-b.y)>=2) && signatures[i]!=signatures[j]
        } }
    }
}
