package com.cukiti.teikafusion.model

import kotlin.math.abs
import kotlin.math.hypot

data class ForgeAim(val cells: List<Position> = emptyList(), val choosingDestination: Boolean = false,
    val destination: Position? = null, val feedback: String = "Madera 0/4")
data class ForgePlan(val cells: List<Position>, val destination: Position)

/** Four conserved wood cells become exactly one T1. Preview and commitment use the same geometry. */
object ForgeRules {
    const val DURATION_MS = 1200
    const val REVEAL_MS = 820
    fun appliesTo(level: Int) = HouseRule.isActive(level, HouseRule.SHOCKWAVE)
    fun eligible(s: GameState, bodies: List<TeikaBodySave>): Set<Position> {
        val blocked = ReactorRules.blocked(s, OrbitRules.bodyCells(bodies, s.gridW, s.gridH))
        return s.grid.filter { (p,w) -> p.x in 0 until s.gridW && p.y in 0 until s.gridH && p !in blocked && w is CellContent.Wood && !w.teikaSeed }.keys
    }
    fun connected(cells: List<Position>): Boolean {
        if(cells.size != 4 || cells.distinct().size != 4) return false
        val seen = mutableSetOf(cells.first())
        repeat(3) { cells.filter { p -> seen.any { abs(p.x-it.x)+abs(p.y-it.y)==1 } }.forEach(seen::add) }
        return seen.size==4
    }
    fun materialsValid(s: GameState, bodies: List<TeikaBodySave>, cells: List<Position>): Boolean {
        if(!connected(cells) || (s.ninjaCutActive && cells.any { (it.x<4)!=(cells.first().x<4) })) return false
        val available=eligible(s,bodies)
        return cells.all { it in available }
    }
    fun plan(s: GameState, bodies: List<TeikaBodySave>, cells: List<Position>, destination: Position?): ForgePlan? {
        val p=destination ?: return null
        if(!materialsValid(s,bodies,cells) || p !in s.grid ||
            (s.ninjaCutActive && (p.x<4)!=(cells.first().x<4))) return null
        val remaining=s.copy(grid=s.grid.mapValues { (q,c) -> if(q in cells) CellContent.Empty else c })
        val blocked=ClawRules.blocked(remaining) + CompactorRules.blocked(remaining,emptySet())
        val x=p.x+.5f; val y=p.y+.5f
        if(!ElevatorRules.clear(x,y,.5f,s.gridW,s.gridH,blocked,if(s.lavaActive) s.lavaRow else 0,if(s.ninjaCutActive) 4 else -1) ||
            bodies.any { !it.x.isFinite() || !it.y.isFinite() || hypot(x-it.x,y-it.y)<.5f+it.type.level*.5f-.001f }) return null
        return ForgePlan(cells,p)
    }
    fun hasMove(s: GameState, bodies: List<TeikaBodySave>): Boolean {
        val eligible=eligible(s,bodies)
        val seen=HashSet<Set<Position>>()
        fun find(cells: Set<Position>): Boolean {
            if(!seen.add(cells)) return false
            if(cells.size==4) return cells.any { plan(s,bodies,cells.toList(),it)!=null }
            val next=cells.flatMap { p -> DominoRules.steps.map { Position(p.x+it.x,p.y+it.y) } }.distinct()
            return next.any { p -> p in eligible && p !in cells &&
                (!s.ninjaCutActive || (p.x<4)==(cells.first().x<4)) && find(cells+p) }
        }
        return eligible.any { find(setOf(it)) }
    }
}
