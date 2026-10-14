package com.cukiti.teikafusion.model

import kotlin.math.*

data class OrbitAim(val center: Position? = null, val clockwise: Boolean = true, val feedback: String = "Elige una zona")
data class OrbitPlan(val center: Position, val clockwise: Boolean, val moves: List<Pair<Position,Position>>) {
    val cells get() = OrbitRules.cells(center)
}

object OrbitRules {
    const val PREPARE_MS=180
    const val MOVE_MS=640
    const val TOTAL_MS=1060
    fun appliesTo(level: Int)=HouseRule.isActive(level,HouseRule.COMET)
    fun cells(c: Position)=(-1..1).flatMap { y -> (-1..1).map { x -> Position(c.x+x,c.y+y) } }
    fun destination(p: Position,c: Position,clockwise: Boolean): Position {
        val x=p.x-c.x; val y=p.y-c.y
        return if(clockwise) Position(c.x-y,c.y+x) else Position(c.x+y,c.y-x)
    }
    /** Unlike ordinary placement tolerance, even a small physical overlap blocks a rotating zone. */
    fun bodyCells(bodies: List<TeikaBodySave>,width: Int,height: Int): Set<Position> = buildSet {
        bodies.forEach { b ->
            val r=b.type.level*.5f
            if(b.x.isFinite() && b.y.isFinite() && r>0f) for(y in 0 until height) for(x in 0 until width) {
                val dx=b.x-b.x.coerceIn(x.toFloat(),x+1f); val dy=b.y-b.y.coerceIn(y.toFloat(),y+1f)
                if(dx*dx+dy*dy<r*r) add(Position(x,y))
            }
        }
    }
    fun error(s: GameState,bodies: Set<Position>,c: Position,clockwise: Boolean): String? {
        val zone=cells(c)
        if(zone.any { it.x !in 0 until s.gridW || it.y !in 0 until s.gridH || it !in s.grid }) return "Zona fuera del tablero"
        if(s.ninjaCutActive && zone.map { it.x<4 }.distinct().size>1) return "El corte bloquea la zona"
        if(zone.any { it in bodies }) return "Hay un Teika en la zona"
        val protected=CompactorRules.blocked(s,bodies)+s.grid.filterValues { it is CellContent.Wood && (it.armor>0 || it.poisoned || it.stubborn) }.keys
        if(zone.any { it in protected }) return "Zona protegida"
        fun visible(v: CellContent?)=if(v is CellContent.Wood) v.copy(id="",skipSpawnAnimation=false) else v
        if(zone.all { visible(s.grid[it])==visible(s.grid[destination(it,c,clockwise)]) }) return "El giro no cambia nada"
        return null
    }
    fun plan(s: GameState,bodies: Set<Position>,c: Position,clockwise: Boolean): OrbitPlan? {
        if(error(s,bodies,c,clockwise)!=null) return null
        return OrbitPlan(c,clockwise,cells(c).filter { s.grid[it] is CellContent.Wood }.map { it to destination(it,c,clockwise) })
    }
    fun apply(grid: Map<Position,CellContent>,p: OrbitPlan): Map<Position,CellContent> = grid.toMutableMap().apply {
        p.cells.forEach { from -> this[destination(from,p.center,p.clockwise)]=grid.getValue(from) }
    }
    fun hasMove(s: GameState,bodies: Set<Position>)=(1 until s.gridH-1).any { y ->
        (1 until s.gridW-1).any { x -> plan(s,bodies,Position(x,y),true)!=null }
    }
    /** Quarter orbit contracts its corner radius to stay inside the original 3×3 footprint. */
    fun point(from: Position,c: Position,clockwise: Boolean,progress: Float): Pair<Float,Float> {
        val t=progress.coerceIn(0f,1f); val ease=t*t*(3f-2f*t)
        if(t==0f) return from.x.toFloat() to from.y.toFloat()
        if(t==1f) { val p=destination(from,c,clockwise); return p.x.toFloat() to p.y.toFloat() }
        val a=ease*PI.toFloat()/2f*(if(clockwise) 1 else -1)
        val ca=cos(a); val sa=sin(a); val shrink=1f/(abs(ca)+abs(sa))
        val dx=(from.x-c.x).toFloat(); val dy=(from.y-c.y).toFloat()
        return (c.x+(dx*ca-dy*sa)*shrink) to (c.y+(dx*sa+dy*ca)*shrink)
    }
}
