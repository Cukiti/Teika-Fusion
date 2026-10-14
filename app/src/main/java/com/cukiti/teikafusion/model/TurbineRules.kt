package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable data class TurbineBeam(val x: Float,val y: Float,val dx: Float,val dy: Float)
@Serializable data class TurbineVelocity(val id: String,val vx: Float,val vy: Float)
@Serializable data class TurbineFusion(val aId: String,val bId: String)
@Serializable data class TurbineGust(val beam: TurbineBeam,val elapsedMs: Float=0f,val velocities: List<TurbineVelocity> = emptyList(),val fusions: List<TurbineFusion> = emptyList(),val chainPending: Boolean=false)
data class TurbineAim(val beam: TurbineBeam?=null,val feedback: String="Elige un borde")

/** Geometry is shared by preview and fixed-step physics. Bodies do not obstruct the air. */
object TurbineRules {
    const val DURATION_MS=2000f
    const val RANGE=6f
    const val FORCE=6f
    const val SPEED=8f
    fun appliesTo(level: Int)=HouseRule.isActive(level,HouseRule.SLIDE)
    fun blocked(s: GameState)=CompactorRules.blocked(s,emptySet())+s.grid.filterValues { it is CellContent.Wood }.keys
    fun anchor(x: Float,y: Float,w: Int,h: Int): TurbineBeam {
        val side=listOf(abs(x),abs(y),abs(w-x),abs(h-y)).withIndex().minBy { it.value }.index
        return when(side) {
            0->TurbineBeam(0f,y.coerceIn(.5f,h-.5f),1f,0f)
            1->TurbineBeam(x.coerceIn(.5f,w-.5f),0f,0f,1f)
            2->TurbineBeam(w.toFloat(),y.coerceIn(.5f,h-.5f),-1f,0f)
            else->TurbineBeam(x.coerceIn(.5f,w-.5f),h.toFloat(),0f,-1f)
        }
    }
    fun aim(b: TurbineBeam,x: Float,y: Float,w: Int,h: Int): TurbineBeam {
        val normal=anchor(b.x,b.y,w,h)
        val tx=x-b.x; val ty=y-b.y
        if(!tx.isFinite() || !ty.isFinite() || hypot(tx,ty)<.1f) return b
        val base=atan2(normal.dy,normal.dx)
        val angle=atan2(sin(atan2(ty,tx)-base),cos(atan2(ty,tx)-base)).coerceIn(-PI.toFloat()/3,PI.toFloat()/3)
        return b.copy(dx=cos(base+angle),dy=sin(base+angle))
    }
    fun valid(b: TurbineBeam,w: Int,h: Int): Boolean {
        if(listOf(b.x,b.y,b.dx,b.dy).any { !it.isFinite() } || abs(hypot(b.dx,b.dy)-1f)>.001f) return false
        if(b.x !in 0f..w.toFloat() || b.y !in 0f..h.toFloat()) return false
        val edge=(b.x==0f || b.x==w.toFloat()) && b.y in .5f..h-.5f || (b.y==0f || b.y==h.toFloat()) && b.x in .5f..w-.5f
        if(!edge) return false
        val n=anchor(b.x,b.y,w,h)
        return b.dx*n.dx+b.dy*n.dy>=.4999f
    }
    /** First obstruction along a ray, including exact cell faces and the Ninja partition. */
    fun rayLength(b: TurbineBeam,dx: Float,dy: Float,blocked: Set<Position>,w: Int,h: Int,split: Boolean,maxLength: Float=RANGE): Float {
        var limit=maxLength
        if(dx>1e-6f) limit=min(limit,(w-b.x)/dx) else if(dx< -1e-6f) limit=min(limit,-b.x/dx)
        if(dy>1e-6f) limit=min(limit,(h-b.y)/dy) else if(dy< -1e-6f) limit=min(limit,-b.y/dy)
        if(split && abs(dx)>1e-6f) { val t=(4f-b.x)/dx; if(t>=0f) limit=min(limit,t) }
        for(p in blocked) {
            var lo=0f; var hi=limit
            if(abs(dx)<1e-6f) {
                if(b.x<p.x || b.x>p.x+1f) continue
            } else {
                val a=(p.x-b.x)/dx; val z=(p.x+1f-b.x)/dx
                lo=max(lo,min(a,z)); hi=min(hi,max(a,z))
                if(lo>hi) continue
            }
            if(abs(dy)<1e-6f) {
                if(b.y<p.y || b.y>p.y+1f) continue
            } else {
                val a=(p.y-b.y)/dy; val z=(p.y+1f-b.y)/dy
                lo=max(lo,min(a,z)); hi=min(hi,max(a,z))
                if(lo>hi) continue
            }
            limit=min(limit,lo)
        }
        return limit.coerceIn(0f,maxLength)
    }
    fun reaches(b: TurbineBeam,x: Float,y: Float,blocked: Set<Position>,w: Int,h: Int,split: Boolean): Boolean {
        val tx=x-b.x; val ty=y-b.y; val dist=hypot(tx,ty)
        if(!dist.isFinite() || dist<.001f || dist>RANGE) return false
        val dx=tx/dist; val dy=ty/dist
        return dx*b.dx+dy*b.dy>=.8660254f-.0001f && dist<rayLength(b,dx,dy,blocked,w,h,split,RANGE+.001f)-.0001f
    }
    fun envelope(elapsed: Float)=min(elapsed/180f,(DURATION_MS-elapsed)/240f).coerceIn(0f,1f)
    fun hasMove(s: GameState,bodies: List<TeikaBodySave>): Boolean {
        val obstacles=blocked(s)
        return bodies.filter { it.frozenTurns==0 && it.bubble==0 }.any { body ->
            (0..3).any { side ->
                val length=if(side%2==0) s.gridH else s.gridW
                val offsets=(1 until length*2).map { it*.5f }+if(side%2==0) body.y else body.x
                offsets.any { at ->
                    val edge=when(side) { 0->anchor(0f,at,s.gridW,s.gridH); 1->anchor(at,0f,s.gridW,s.gridH); 2->anchor(s.gridW.toFloat(),at,s.gridW,s.gridH); else->anchor(at,s.gridH.toFloat(),s.gridW,s.gridH) }
                    reaches(aim(edge,body.x,body.y,s.gridW,s.gridH),body.x,body.y,obstacles,s.gridW,s.gridH,s.ninjaCutActive)
                }
            }
        }
    }
}
