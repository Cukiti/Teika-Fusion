package com.cukiti.teikafusion.model

/** Geometry shared by the actual line clear and its preview. Secondary hits never count as lines. */
object ShockwaveRules {
    fun appliesTo(level: Int): Boolean = false // Retired Onda; pure geometry remains dormant.

    fun centers(lines: List<Set<Position>>): Set<Position> = buildSet {
        for (i in lines.indices) for (j in i + 1 until lines.size) addAll(lines[i].intersect(lines[j]))
    }

    fun ring(centers: Set<Position>, width: Int, height: Int, splitAtX: Int = -1): Set<Position> = buildSet {
        centers.forEach { center ->
            for (dy in -2..2) for (dx in -2..2) {
                if (dx == 0 && dy == 0) continue
                val p = Position(center.x + dx, center.y + dy)
                if (p.x !in 0 until width || p.y !in 0 until height) continue
                if (splitAtX >= 0 && (center.x < splitAtX) != (p.x < splitAtX)) continue
                add(p)
            }
        }
    }
    fun affected(centers: Set<Position>, width: Int, height: Int, splitAtX: Int, eligible: Set<Position>): List<Position> =
        ring(centers,width,height,splitAtX).filter { it in eligible }.sortedWith(
            compareBy<Position> { p -> distance(p,centers) }.thenBy { it.y }.thenBy { it.x }).take(8)
    fun distance(p: Position, centers: Set<Position>) = centers.minOfOrNull { maxOf(kotlin.math.abs(p.x-it.x),kotlin.math.abs(p.y-it.y)) } ?: 0

}

object AvalancheRules {
    fun appliesTo(level: Int) = HouseRule.isActive(level, HouseRule.AVALANCHE)
    fun triggered(level: Int, lineCount: Int) = appliesTo(level) && lineCount >= 2
    fun charge(level: Int, previous: Int, lineCount: Int) = if (appliesTo(level)) (previous + lineCount).coerceAtMost(2) else 0
}

/** Producer and renderer share these bounded event clocks. */
object RuleMotion {
    const val CONSTELLATION_MS = 900
    const val CUT_MS = 520
    const val WAVE_MS = 680
    fun constellationHit(index: Int, count: Int) = 180 + index * 420 / (count - 1).coerceAtLeast(1)
    fun dominoHit(index: Int) = 180 + index.coerceAtMost(16) * 32
    fun waveHit(distance: Int) = 180 + distance * 80
}
