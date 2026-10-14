package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable
import kotlin.math.hypot

@Serializable
data class PinballFlight(val bodyId: String, val vx: Float, val vy: Float,
    val bouncesLeft: Int = 6, val secondsLeft: Float = 3f)

object PinballRules {
    const val SPEED = 10f
    const val BOUNCES = 6
    const val SECONDS = 3f
    fun appliesTo(level: Int) = HouseRule.isActive(level, HouseRule.TRAMPOLINE)
    fun velocity(pullX: Float, pullY: Float): Pair<Float,Float>? {
        val distance=hypot(pullX,pullY)
        if(!distance.isFinite() || distance<.35f) return null
        return -pullX/distance*SPEED to -pullY/distance*SPEED
    }
    fun valid(f: PinballFlight) = f.bodyId.isNotBlank() && f.vx.isFinite() && f.vy.isFinite() &&
        hypot(f.vx,f.vy) in .1f..(SPEED+.1f) && f.bouncesLeft in 1..BOUNCES &&
        f.secondsLeft.isFinite() && f.secondsLeft>0f && f.secondsLeft<=SECONDS
    fun canBreak(c: CellContent?, blocked: Boolean) = !blocked && c is CellContent.Wood &&
        c.armor==0 && c.coil==0 && !c.poisoned && !c.stubborn
}
