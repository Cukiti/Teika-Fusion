package com.cukiti.teikafusion.model

object SlideRules {
    const val PREPARE_MS=150
    const val MOVE_MS=380
    const val DURATION_MS=700
    fun appliesTo(@Suppress("UNUSED_PARAMETER") level: Int)=false
    fun direction(level: Int,wave: Int)=if((level+wave)%2==0) 1 else -1
    /** One step into a free vacancy, never wrap, overwrite, cross a wall or move an anchor. */
    fun moves(grid: Map<Position,CellContent>,cleared: Set<Position>,blocked: Set<Position>,direction: Int,width: Int=8,height: Int=8,splitAtX: Int=-1): List<Pair<Position,Position>> {
        val dx=if(direction>0) 1 else -1
        val neighbour=cleared.flatMap { p -> listOf(Position(p.x,p.y-1),Position(p.x,p.y+1)) }
            .filterTo(HashSet()) { it !in cleared && it.x in 0 until width && it.y in 0 until height }
        val occupied=grid.filterValues { it !is CellContent.Empty }.keys.toMutableSet()
        val result=mutableListOf<Pair<Position,Position>>()
        val ordered=neighbour.sortedWith(compareBy<Position> { it.y }.thenBy { -dx*it.x })
        for(p in ordered) {
            if(grid[p] !is CellContent.Wood || p in blocked) continue
            val q=Position(p.x+dx,p.y)
            if(q.x !in 0 until width || q !in grid || q in blocked || q in occupied ||
                (splitAtX>=0 && (p.x<splitAtX)!=(q.x<splitAtX))) continue
            result.add(p to q); occupied.remove(p); occupied.add(q)
        }
        return result
    }
}

@kotlinx.serialization.Serializable
data class RelayTarget(val cell: Position, val woodId: String)

@kotlinx.serialization.Serializable
data class RelayShot(val direction: Int, val paths: List<List<Position>>, val targets: List<RelayTarget>)

object RelayRules {
    const val MAX_CELLS=64
    const val DURATION_MS=1000
    fun appliesTo(level: Int)=HouseRule.isActive(level,HouseRule.RELAY)
    fun afterAnnouncedClear(grid: Map<Position,CellContent>,cleared: Set<Position>,blocked: Set<Position>) = grid.mapValues { (p,c) ->
        if(p in cleared && p !in blocked && c is CellContent.Wood && c.armor==0 && c.coil==0 && !c.poisoned && !c.stubborn) CellContent.Empty else c
    }
    /** Shape-wide rays, chosen by reachable wood count; ties consistently prefer right/down/left/up. */
    fun plan(footprint: Set<Position>,grid: Map<Position,CellContent>,blocked: Set<Position>,width: Int=8,height: Int=8,splitAtX: Int=-1): RelayShot {
        val shape=footprint.filter { it.x in 0 until width && it.y in 0 until height }.toSet()
        val choices=DominoRules.steps.mapIndexed { direction,d ->
            val horizontal=d.x!=0
            val lanes=shape.groupBy { if(horizontal) it.y else it.x }.toSortedMap()
            val paths=lanes.values.map { lane ->
                val origin=lane.maxBy { it.x*d.x+it.y*d.y }
                val path=mutableListOf(origin)
                var q=Position(origin.x+d.x,origin.y+d.y)
                while(q.x in 0 until width && q.y in 0 until height && q in grid &&
                    (splitAtX<0 || (origin.x<splitAtX)==(q.x<splitAtX))) {
                    val c=grid[q]
                    if(q in blocked || (c !is CellContent.Empty && (c !is CellContent.Wood ||
                        c.armor>0 || c.coil>0 || c.poisoned || c.stubborn))) break
                    path.add(q)
                    q=Position(q.x+d.x,q.y+d.y)
                }
                path.toList()
            }
            val targets=paths.flatMap { it.drop(1) }.distinct().mapNotNull { p ->
                (grid[p] as? CellContent.Wood)?.let { RelayTarget(p,it.id) }
            }.sortedWith(compareBy<RelayTarget> { target ->
                paths.first { target.cell in it }.indexOf(target.cell)
            }.thenBy { it.cell.y }.thenBy { it.cell.x }).take(MAX_CELLS)
            RelayShot(direction,paths,targets)
        }
        return choices.maxBy { it.targets.size }
    }
    fun affected(footprint: Set<Position>,grid: Map<Position,CellContent>,blocked: Set<Position>,width: Int=8,height: Int=8,splitAtX: Int=-1)=
        plan(footprint,grid,blocked,width,height,splitAtX).targets.map { it.cell }
    fun hit(distance: Int)=240+distance.coerceIn(0,8)*50
    fun hit(shot: RelayShot,p: Position)=hit(shot.paths.firstOrNull { p in it }?.indexOf(p) ?: 0)
    fun survivingPaths(shot: RelayShot,grid: Map<Position,CellContent>,blocked: Set<Position>): List<List<Position>> {
        val ids=shot.targets.associate { it.cell to it.woodId }
        return shot.paths.map { path ->
            path.take(1)+path.drop(1).takeWhile { p ->
                val c=grid[p]
                p !in blocked && (c is CellContent.Empty || (c is CellContent.Wood &&
                    c.id==ids[p] && c.armor==0 && c.coil==0 && !c.poisoned && !c.stubborn))
            }
        }
    }
    fun remaining(shot: RelayShot,grid: Map<Position,CellContent>,blocked: Set<Position>): List<Position> {
        val reachable=survivingPaths(shot,grid,blocked).flatMap { it.drop(1) }.toSet()
        return shot.targets.map { it.cell }.filter { it in reachable && grid[it] is CellContent.Wood }.distinct()
    }
    fun valid(shot: RelayShot,footprint: Set<Position>,width: Int,height: Int,splitAtX: Int=-1): Boolean {
        val d=DominoRules.steps.getOrNull(shot.direction) ?: return false
        fun inBounds(p: Position)=p.x in 0 until width && p.y in 0 until height
        return shot.paths.size<=maxOf(width,height) && shot.paths.all { path ->
            path.isNotEmpty() && path.size<=maxOf(width,height)+1 && path.first() in footprint &&
                path.all(::inBounds) && path.zipWithNext().all { (a,b) -> b==Position(a.x+d.x,a.y+d.y) &&
                    (splitAtX<0 || (a.x<splitAtX)==(b.x<splitAtX)) }
        } && shot.targets.size<=MAX_CELLS && shot.targets.distinctBy { it.cell }.size==shot.targets.size &&
            shot.targets.all { it.woodId.isNotBlank() && it.cell !in footprint && shot.paths.any { path -> it.cell in path.drop(1) } }
    }
}
