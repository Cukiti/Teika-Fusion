package com.cukiti.teikafusion.model

import kotlin.math.*

/** Grid units. The ball centre follows this circular arc; its diameter is one cell. */
data class BallArc(val x: Float, val y: Float, val radius: Float, val start: Float, val endFraction: Float = 1f) {
    fun point(f: Float): Pair<Float,Float> {
        val angle=start+f.coerceIn(0f,endFraction)*PI.toFloat()/2
        return x+cos(angle)*radius to y+sin(angle)*radius
    }
}
data class BallAim(val anchor: Position? = null, val side: Int = 0, val arc: BallArc? = null,
    val feedback: String = "Elige un borde y arrastra")
data class BallPlan(val arc: BallArc, val cells: List<Position>, val hitTimes: List<Int>, val obstacle: Position?) {
    fun hit(i: Int)=hitTimes[i]
    val stopMs get()=180+(sqrt(arc.endFraction)*620).roundToInt()
    val durationMs get()=stopMs+320
}

object WreckingBallRules {
    fun appliesTo(level: Int)=HouseRule.isActive(level,HouseRule.DOMINO)
    fun blocked(s: GameState,bodies: Set<Position>)=ReactorRules.blocked(s,bodies)
    // Sides: left, top, right, bottom. Anchor centres sit exactly on the selected edge.
    fun anchor(s: GameState,x: Float,y: Float): Pair<Position,Int>? {
        if(!x.isFinite() || !y.isFinite() || x<0 || y<0 || x>s.gridW || y>s.gridH) return null
        val distances=listOf(x,y,s.gridW-x,s.gridH-y)
        val side=distances.indices.minBy { distances[it] }
        if(distances[side]>.8f) return null
        return (if(side%2==0) Position(if(side==0) 0 else s.gridW-1,floor(y).toInt().coerceIn(0,s.gridH-1))
            else Position(floor(x).toInt().coerceIn(0,s.gridW-1),if(side==1) 0 else s.gridH-1)) to side
    }
    fun arc(s: GameState,p: Position,side: Int,x: Float,y: Float): BallArc? {
        if(side !in 0..3 || p.x !in 0 until s.gridW || p.y !in 0 until s.gridH || !x.isFinite() || !y.isFinite()) return null
        val ax=when(side) { 0->0f; 2->s.gridW.toFloat(); else->p.x+.5f }
        val ay=when(side) { 1->0f; 3->s.gridH.toFloat(); else->p.y+.5f }
        val inward=when(side) { 0->0f; 1->PI.toFloat()/2; 2->PI.toFloat(); else->-PI.toFloat()/2 }
        var relative=atan2(y-ay,x-ax)-inward
        while(relative>PI) relative-=2*PI.toFloat()
        while(relative< -PI) relative+=2*PI.toFloat()
        val middle=inward+relative.coerceIn(-PI.toFloat()/4,PI.toFloat()/4)
        return BallArc(ax,ay,hypot(x-ax,y-ay).coerceIn(1f,4f),middle-PI.toFloat()/4)
    }
    fun plan(s: GameState,bodies: Set<Position>,arc: BallArc): BallPlan? {
        if(!valid(s,arc)) return null
        val blocked=blocked(s,bodies)
        val cells=linkedMapOf<Position,Int>()
        var stop: Position?=null
        var fraction=1f
        val anchorLeft=arc.x<4f
        // 192 intervals: maximum step < .033 cell, with conservative circle/cell contacts.
        for(i in 0..192) {
            val f=i/192f; val (x,y)=arc.point(f)
            if(s.ninjaCutActive && if(anchorLeft) x+.5f>4f+1e-5f else x-.5f<4f-1e-5f) {
                fraction=f; stop=Position(4,floor(y).toInt().coerceIn(0,s.gridH-1)); break
            }
            val touched=buildList {
                for(cy in floor(y-.5f).toInt()..floor(y+.5f).toInt())
                    for(cx in floor(x-.5f).toInt()..floor(x+.5f).toInt()) {
                        val p=Position(cx,cy)
                        val dx=x-x.coerceIn(cx.toFloat(),cx+1f); val dy=y-y.coerceIn(cy.toFloat(),cy+1f)
                        // Float tolerance: tangency is not a contact with the neighbouring cell.
                        if(cx in 0 until s.gridW && cy in 0 until s.gridH && p in s.grid && dx*dx+dy*dy<.25f-1e-5f) add(p)
                    }
            }
            stop=touched.firstOrNull { it in blocked }
            if(stop!=null) { fraction=f; break } // Never hit another cell during the same blocked sample.
            touched.forEach { p -> if(s.grid[p] is CellContent.Wood) cells.putIfAbsent(p,180+(sqrt(f)*620).roundToInt()) }
        }
        return BallPlan(arc.copy(endFraction=fraction),cells.keys.toList(),cells.values.toList(),stop)
    }
    fun valid(s: GameState,a: BallArc): Boolean {
        if(!a.x.isFinite() || !a.y.isFinite() || !a.radius.isFinite() || !a.start.isFinite() ||
            a.radius !in 1f..4f || a.endFraction!=1f) return false
        val epsilon=.001f
        val onLeft=abs(a.x)<epsilon && a.y in .5f..s.gridH-.5f
        val onRight=abs(a.x-s.gridW)<epsilon && a.y in .5f..s.gridH-.5f
        val onTop=abs(a.y)<epsilon && a.x in .5f..s.gridW-.5f
        val onBottom=abs(a.y-s.gridH)<epsilon && a.x in .5f..s.gridW-.5f
        if(!onLeft && !onRight && !onTop && !onBottom) return false
        return listOf(0f,.5f,1f).all { f -> val (x,y)=a.point(f)
            when { onLeft->x>=-.001f; onRight->x<=s.gridW+.001f; onTop->y>=-.001f; else->y<=s.gridH+.001f } }
    }
    fun hasShot(s: GameState,bodies: Set<Position>): Boolean {
        if(s.grid.values.none { it is CellContent.Wood }) return false
        // Include arcs starting at the target, not only arcs centred on it: earlier obstacles
        // must not hide a legal launch and cause premature defeat.
        val protected=blocked(s,bodies)
        val targets=s.grid.filter { (p,c)->c is CellContent.Wood && p.x in 0 until s.gridW && p.y in 0 until s.gridH && p !in protected }.keys
        return (0..3).any { side -> (0 until if(side%2==0) s.gridH else s.gridW).any { n ->
            val p=if(side%2==0) Position(if(side==0) 0 else s.gridW-1,n) else Position(n,if(side==1) 0 else s.gridH-1)
            targets.any { t -> arc(s,p,side,t.x+.5f,t.y+.5f)?.let { centred->
                val dx=t.x+.5f-centred.x; val dy=t.y+.5f-centred.y
                val direct=BallArc(centred.x,centred.y,hypot(dx,dy),atan2(dy,dx))
                plan(s,bodies,direct)?.cells?.isNotEmpty()==true || plan(s,bodies,centred)?.cells?.isNotEmpty()==true
            }==true }
        } }
    }
}
