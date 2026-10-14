package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

object SurfRules {
    const val DURATION_MS = 1100
    const val IMPACT_MS = 180
    fun appliesTo(level: Int) = HouseRule.isActive(level,HouseRule.SURF)
    fun direction(level: Int, wave: Int) = if((level+wave)%2==0) 1 else -1
    fun supports(x: Float, y: Float, radius: Float, vy: Float, lines: List<Set<Position>>, removed: Set<Position>): Boolean =
        x.isFinite() && y.isFinite() && radius.isFinite() && radius>0f && abs(vy)<=2f && lines.any { line ->
            line.size>=2 && line.all { it.y==line.first().y } && abs(y+radius-line.first().y)<.28f &&
                line.any { it in removed && abs(x-(it.x+.5f))<=radius*.7f+.5f }
        }
}

@Serializable
data class CometMark(val cell: Position, val direction: Int)

object CometRules {
    const val DURATION_MS = 850
    /** Retired: helpers only read legacy geometry; no new core or shot is produced. */
    fun appliesTo(level: Int) = false
    fun ray(mark: CometMark, grid: Map<Position,CellContent>, blocked: Set<Position>, width: Int=8, height: Int=8, splitAtX: Int=-1): List<Position> {
        if(mark.cell.x !in 0 until width || mark.cell.y !in 0 until height || mark.cell !in grid) return emptyList()
        val step=DominoRules.steps.getOrNull(mark.direction) ?: return emptyList()
        val out=mutableListOf<Position>(); var p=mark.cell
        repeat(maxOf(width,height)) {
            val q=Position(p.x+step.x,p.y+step.y)
            if(q.x !in 0 until width || q.y !in 0 until height || q in blocked ||
                (splitAtX>=0 && (p.x<splitAtX)!=(q.x<splitAtX))) return out
            val content=grid[q] ?: return out
            if(content is CellContent.Wood) {
                if(content.armor>0 || content.poisoned || content.coil>0 || content.stubborn) return out
            } else if(content !is CellContent.Empty) return out
            out.add(q); p=q
        }
        return out
    }
    /** Once per launch/restore, never per frame. Favor a useful, fully announced lane. */
    fun seed(level: Int, wave: Int, grid: Map<Position,CellContent>, blocked: Set<Position>, width: Int=8, height: Int=8, splitAtX: Int=-1, avoid: Position?=null): CometMark? {
        val free=grid.filter { (p,c) -> c is CellContent.Empty && p !in blocked }.keys
        val pool=(free- setOfNotNull(avoid)).ifEmpty { free }.sortedWith(compareBy<Position> { (it.x*19+it.y*31+level*7+wave*11).mod(67) }.thenBy { it.y }.thenBy { it.x })
        var best: CometMark?=null; var score=-1
        for(p in pool) for(d in 0..3) {
            val mark=CometMark(p,d); val path=ray(mark,grid,blocked,width,height,splitAtX)
            if(path.isEmpty()) continue
            val value=path.sumOf { q -> (grid[q] as? CellContent.Wood)?.let { if(it.targetColor!=null) 30 else 10 } ?: 0 }+path.size
            if(value>score) { score=value; best=mark }
        }
        return best
    }
    fun hit(index: Int, count: Int)=180+(index+1)*420/count.coerceAtLeast(1)
}
