package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable
import kotlin.math.*

@Serializable
data class ClawTrip(val bodyId: String, val fromX: Float, val fromY: Float, val toX: Float, val toY: Float,
    val radius: Float, val progress: Float = 0f)
data class ClawAim(val bodyId: String? = null, val x: Float? = null, val y: Float? = null,
    val feedback: String = "Elige un Teika", val radius: Float = .5f, val fromX: Float? = null, val fromY: Float? = null)

object ClawRules {
    const val DURATION_MS = 1200
    fun appliesTo(level: Int) = HouseRule.isActive(level, HouseRule.ELEVATOR)
    fun free(b: TeikaBodySave) = b.type.level in 1..4 && b.frozenTurns == 0 && b.bubble == 0
    fun blocked(s: GameState) = s.grid.filterValues { it.isOccupied }.keys + s.unplaceableCells +
        s.hiddenWoodCells + s.frozenCells + s.glacierFrostCells + s.gluttonDeadCells.keys
    fun destination(s: GameState, b: TeikaBodySave, others: List<TeikaBodySave>, x: Float, y: Float): Boolean {
        val radius=b.type.level*.5f
        if(!free(b) || !b.x.isFinite() || !b.y.isFinite() ||
            !ElevatorRules.clear(x,y,radius,s.gridW,s.gridH,blocked(s),if(s.lavaActive) s.lavaRow else 0,if(s.ninjaCutActive) 4 else -1)) return false
        if(s.ninjaCutActive && (b.x<4)!=(x<4)) return false
        if(hypot(x-b.x,y-b.y)<.1f) return false
        return others.filter { it.id!=b.id }.none {
            !it.x.isFinite() || !it.y.isFinite() || hypot(x-it.x,y-it.y)<radius+it.type.level*.5f-.001f
        }
    }
    fun valid(s: GameState, t: ClawTrip): Boolean {
        if(!appliesTo(s.level) || !listOf(t.fromX,t.fromY,t.toX,t.toY,t.radius,t.progress).all { it.isFinite() } ||
            t.progress !in 0f..1f || t.radius !in .5f..2f) return false
        val b=s.teikaBodySaves.firstOrNull { it.id==t.bodyId } ?: return false
        if(!b.x.isFinite() || !b.y.isFinite() || s.plantAbduction?.teikaId==b.id || b.type.level*.5f!=t.radius) return false
        val origin=b.copy(x=t.fromX,y=t.fromY)
        return ElevatorRules.clear(t.fromX,t.fromY,t.radius,s.gridW,s.gridH,emptySet(),if(s.lavaActive) s.lavaRow else 0,if(s.ninjaCutActive) 4 else -1) &&
            destination(s,origin,s.teikaBodySaves,t.toX,t.toY)
    }
    /** Pick up, ease into a suspended traverse, then accelerate into a short landing. */
    fun position(t: ClawTrip): Pair<Float,Float> {
        val p=t.progress.coerceIn(0f,1f)
        val lift=min(.65f,min(t.fromY,t.toY)-t.radius).coerceAtLeast(0f)
        fun smooth(v: Float)=v*v*(3-2*v)
        return when {
            p<.25f -> t.fromX to (t.fromY-lift*smooth(p/.25f))
            p<.8f -> { val q=smooth((p-.25f)/.55f); (t.fromX+(t.toX-t.fromX)*q) to (t.fromY+(t.toY-t.fromY)*q-lift) }
            else -> { val q=(p-.8f)/.2f; t.toX to (t.toY-lift*(1-q*q)) }
        }
    }
}
