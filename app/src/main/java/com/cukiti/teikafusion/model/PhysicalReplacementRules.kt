package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class ElevatorTrip(val bodyId: String, val x: Float, val fromY: Float, val toY: Float, val progress: Float = 0f, val radius: Float = .5f)

/** Bounded circle/cell geometry: the whole passenger, not just its center, must fit. */
object ElevatorRules {
    const val DURATION_MS = 800
    fun appliesTo(level: Int): Boolean = false // Retired Ascensor; geometry reused by Garra.
    fun platform(level: Int) = Position(1 + level.mod(6), 5)
    fun markedLine(platform: Position, lines: List<Set<Position>>) = lines.any { line ->
        line.size >= 2 && line.all { it.y == platform.y } && platform in line
    }
    fun clear(x: Float, y: Float, radius: Float, width: Int, height: Int, blocked: Set<Position>, top: Int = 0, splitAtX: Int = -1): Boolean {
        if (!x.isFinite() || !y.isFinite() || radius <= 0f || x-radius < 0 || x+radius > width || y-radius < top || y+radius > height) return false
        if (splitAtX >= 0 && x-radius < splitAtX && x+radius > splitAtX) return false
        return blocked.none { p ->
            val dx = x-x.coerceIn(p.x.toFloat(),p.x+1f)
            val dy = y-y.coerceIn(p.y.toFloat(),p.y+1f)
            dx*dx+dy*dy < radius*radius-.001f
        }
    }
    fun corridor(x: Float, fromY: Float, toY: Float, radius: Float, width: Int, height: Int, blocked: Set<Position>, top: Int = 0, splitAtX: Int = -1): Boolean {
        if (!fromY.isFinite() || !toY.isFinite() || toY > fromY || fromY-toY > 3f) return false
        return (0..24).all { i -> clear(x,fromY+(toY-fromY)*i/24f,radius,width,height,blocked,top,splitAtX) }
    }
}

object TrailRules {
    const val DISTANCE = 3f
    const val MAX_CELLS = 16
    const val DURATION_MS = 800
    fun appliesTo(level: Int): Boolean = false // Retired Estela; pure helpers only for compatibility tests.
    /** Discontinuous position changes (portals, rescue, spawn) are never travel. */
    fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val d = hypot(bx-ax,by-ay)
        return if (d.isFinite() && d <= .75f) d else 0f
    }
    fun path(cells: List<Position>, next: Position): List<Position> = if (cells.lastOrNull() == next) cells else (cells+next).takeLast(MAX_CELLS)
    /** A newly raised wall/lava can divide an already charged path. Only the crossed segment fires. */
    fun crossedSegment(path: List<Position>, lines: List<Set<Position>>, blocked: Set<Position>, splitAtX: Int = -1): List<Position> {
        val crossed=lines.flatten().toSet()
        val groups=mutableListOf<MutableList<Position>>()
        path.forEach { p ->
            if(p in blocked) { if(groups.lastOrNull()?.isNotEmpty()==true) groups.add(mutableListOf()); return@forEach }
            val last=groups.lastOrNull()?.lastOrNull()
            if(groups.isEmpty() || (last!=null && (maxOf(abs(last.x-p.x),abs(last.y-p.y))>1 || (splitAtX>=0 && (last.x<splitAtX)!=(p.x<splitAtX))))) groups.add(mutableListOf())
            groups.last().add(p)
        }
        return groups.firstOrNull { segment -> segment.any { it in crossed } }.orEmpty()
    }
    fun sweep(path: List<Position>, width: Int, height: Int, splitAtX: Int = -1): List<Position> = buildSet {
        path.forEach { p ->
            (listOf(Position(0,0))+DominoRules.steps).forEach { d ->
                val q=Position(p.x+d.x,p.y+d.y)
                if (q.x in 0 until width && q.y in 0 until height && (splitAtX < 0 || (p.x < splitAtX)==(q.x < splitAtX))) add(q)
            }
        }
    }.toList()
    fun impact(cell: Position, path: List<Position>): Int {
        val index=path.indices.minByOrNull { i -> abs(cell.x-path[i].x)+abs(cell.y-path[i].y) } ?: 0
        return hit(index,path.size)
    }
    fun hit(index: Int, count: Int) = 180 + index*400/(count-1).coerceAtLeast(1)
}
