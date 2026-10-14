package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable

object ReserveRules {
    const val SWAP_MS = 420
    fun appliesTo(level: Int) = HouseRule.isActive(level, HouseRule.RESERVE)
    fun eligible(s: GameState, p: Piece): Boolean = !s.isPieceAsleep(p.id) && s.drowsyMarkedId != p.id && !p.isFichona
    fun canSwap(s: GameState, id: String): Boolean = appliesTo(s.level) && s.reserveSwapRefill != s.trayRefillCount &&
        s.reservePieces.firstOrNull()?.let { eligible(s,it) } == true && s.availablePieces.any { it.id == id && eligible(s,it) }
    /** Compatibility queues retain every old four/five-piece tray; ordinary rounds have one reserve. */
    fun exchange(s: GameState, id: String): GameState? {
        if (!canSwap(s,id)) return null
        val held = s.reservePieces.first()
        val outgoing = s.availablePieces.first { it.id == id }
        return s.copy(availablePieces=s.availablePieces.map { if(it.id==id) held else it },
            reservePieces=s.reservePieces.drop(1)+outgoing, reserveSwapRefill=s.trayRefillCount,
            unplayablePieceIds=s.unplayablePieceIds-id-held.id)
    }
    /** Hit test the three weighted playable slots; the reserve and the gaps never lift a piece. */
    fun slotIndex(x: Float, width: Float, gap: Float, playRatio: Float, slots: Int=3): Int {
        if(x<0f || x>=width || slots<=0) return -1
        val playWidth=(width-gap*slots)*playRatio
        if(playWidth<=0f) return -1
        val index=(x/(playWidth+gap)).toInt()
        return if(index in 0 until slots && x-index*(playWidth+gap)<playWidth) index else -1
    }
    fun candidates(s: GameState, tray: List<Piece>): List<Piece> =
        if(tray == s.availablePieces && tray.any { canSwap(s,it.id) }) tray+s.reservePieces.take(1) else tray
}

@Serializable
data class RouteTrack(val cells: List<Position> = emptyList(), val covered: Set<Position> = emptySet(), val wave: Int = 0)

object RouteRules {
    const val NODES = 3
    const val POINTS = 200
    const val COMPLETE_MS = 760
    fun appliesTo(level: Int) = false // Retired; helpers below only describe historical route data.
    /** Disjoint grid paths. Never connect across Ninja, a blocked cell, or an occupied cell. */
    fun path(level: Int, wave: Int, route: Int, free: Set<Position>, splitAtX: Int=-1, nodes: Int=NODES): List<Position> {
        val starts=free.sortedWith(compareBy<Position> { (it.x*19+it.y*31+level*7+wave*11+route*29).mod(67) }.thenBy { it.y }.thenBy { it.x })
        for(start in starts) {
            fun search(path: List<Position>): List<Position>? {
                if(path.size==nodes.coerceIn(1,NODES)) return path
                val p=path.last()
                for(d in DominoRules.steps) {
                    val q=Position(p.x+d.x,p.y+d.y)
                    if(q in free && q !in path && (splitAtX<0 || (p.x<splitAtX)==(q.x<splitAtX)))
                        search(path+q)?.let { return it }
                }
                return null
            }
            search(listOf(start))?.let { return it }
        }
        return emptyList() // Wait for space instead of lowering the reward requirement.
    }
    /** Keep earned lights; rebuild inaccessible pending nodes without awarding anything. */
    fun reconcile(s: GameState, free: Set<Position>): List<RouteTrack> {
        if(!HouseRule.isActive(s.level,HouseRule.ROUTES)) return emptyList()
        val used=mutableSetOf<Position>()
        return (0..1).map { index ->
            val old=s.routes.getOrNull(index) ?: RouteTrack()
            val kept=old.cells.filter { it !in used && (it in old.covered || it in free) }.distinct().take(NODES)
            val covered=old.covered.intersect(kept.toSet())
            val otherNodes=s.routes.filterIndexed { i,_ -> i!=index }.flatMap { it.cells }.toSet()
            val candidates=free-used-kept.toSet()-otherNodes
            val replacement=path(s.level,old.wave,index,candidates,if(s.ninjaCutActive) 4 else -1,(NODES-kept.size).coerceAtLeast(1))
            // Relocation preserves earned progress. The three nodes may become separate after a biome changes terrain.
            val cells=if(kept.size==NODES) kept else if(kept.isEmpty()) replacement else kept+replacement.take(NODES-kept.size)
            used.addAll(cells)
            RouteTrack(cells,covered,old.wave.coerceAtLeast(0))
        }
    }
    fun progress(track: RouteTrack, placed: Set<Position>) = track.copy(covered=track.covered+track.cells.filter { it in placed })
    fun complete(track: RouteTrack) = track.cells.size==NODES && track.covered.containsAll(track.cells)
}
