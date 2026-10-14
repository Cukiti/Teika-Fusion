package com.cukiti.teikafusion.model

/**
 * Reglas puras del bioma Cocina Voraz (presión idle → boca → mordisco → digestión).
 * Alineadas con [com.cukiti.teikafusion.viewmodel.GameViewModel] y [GameState.GLUTTON_*].
 * Sin Android — unit-testeable.
 */
object GluttonBiomeRules {

    const val OPEN_AT = 2
    const val BITE_AT = 3
    const val RECOVER_TURNS = 7

    /** Calma el reloj: fusión de Teikas o clear (boardActive / gluttonFed). */
    fun isFed(fusionOrClear: Boolean): Boolean = fusionOrClear

    /**
     * Avanza el contador ocioso.
     * @return (nuevoCounter, shouldOpen, shouldBite)
     * shouldOpen/shouldBite son mutuamente excluyentes por tick (bite tiene prioridad si ya hay boca).
     */
    fun advanceIdle(
        currentCounter: Int,
        mouthOpen: Boolean,
        fed: Boolean,
        openAt: Int = OPEN_AT,
        biteAt: Int = BITE_AT
    ): Triple<Int, Boolean, Boolean> {
        if (fed) return Triple(0, false, false)
        val next = currentCounter + 1
        if (mouthOpen && next >= biteAt) return Triple(0, false, true)
        if (!mouthOpen && next >= openAt) return Triple(next, true, false)
        return Triple(next, false, false)
    }

    /** Digestión: baja 1 turno; elimina celdas que llegan a 0. */
    fun digest(dead: Map<Position, Int>): Map<Position, Int> =
        dead.mapNotNull { (p, t) -> if (t - 1 <= 0) null else p to (t - 1) }.toMap()

    /**
     * Celdas que se lleva el mordisco: **TODO el triángulo, siempre**.
     *
     * Histórico (bug del playtest 2026-07-20): antes se filtraban las celdas pisadas por un cuerpo
     * de Teika. Con un Teika apoyado en el rincón —lo normal, la gravedad los amontona ahí— el
     * mordisco se comía 1 sola celda o ninguna, y el bioma parecía roto justo cuando más amenaza
     * debía dar. Ahora el glotón muerde el triángulo entero y **empuja** al Teika fuera con la
     * dentellada (ver `pushDirectionFromCorner` + `launchBody` en GameViewModel): la boca aparta lo
     * que estorba, que es lo que la lógica de una mordida pide.
     *
     * La madera CON estrella también se muerde (el glotón se la traga); el objetivo se ACREDITA al
     * jugador en [GameViewModel] para no perder progreso ni dejar el bloque flotando en la boca.
     */
    fun biteableCells(cornerCells: List<Position>): List<Position> = cornerCells

    /**
     * Dirección normalizada (x, y) en la que la dentellada escupe a un cuerpo que ocupaba el rincón
     * [corner]: siempre HACIA DENTRO del tablero en diagonal, lejos de la boca. 0=sup-izq, 1=sup-der,
     * 2=inf-izq, 3=inf-der.
     */
    fun pushDirectionFromCorner(corner: Int): Pair<Float, Float> {
        val inv = 0.7071f   // 1/√2 — diagonal normalizada
        return when (corner) {
            0 -> inv to inv       // sup-izq → abajo-derecha
            1 -> -inv to inv      // sup-der → abajo-izquierda
            2 -> inv to -inv      // inf-izq → arriba-derecha
            else -> -inv to -inv  // inf-der → arriba-izquierda
        }
    }

    /** Rincón elegible para abrir boca: ninguna celda del triángulo ya digiriéndose. */
    fun isCornerEligible(corner: Int, deadKeys: Set<Position>): Boolean =
        GameState.gluttonCornerCells(corner).none { it in deadKeys }

    fun eligibleCorners(deadKeys: Set<Position>): List<Int> =
        (0..3).filter { isCornerEligible(it, deadKeys) }

    /**
     * Si no hay rincón libre para abrir la boca, el contador se congela aquí
     * (OPEN_AT - 1) para no quedar en "open fantasma" sin boca.
     */
    fun holdCounterWhenNoCorner(openAt: Int = OPEN_AT): Int = (openAt - 1).coerceAtLeast(0)

    /**
     * Libera el rincón bloqueado más cerca de digiérese (menor suma de turnos restantes).
     * Usado cuando shouldOpen pero no hay elegible, y al saciar con Devorar.
     */
    fun freeOneCorner(dead: Map<Position, Int>): Map<Position, Int> {
        if (dead.isEmpty()) return dead
        val blocked = (0..3).filter { !isCornerEligible(it, dead.keys) }
        if (blocked.isEmpty()) return dead
        val corner = blocked.minByOrNull { c ->
            GameState.gluttonCornerCells(c).sumOf { p -> dead[p] ?: 0 }
        } ?: return dead
        val free = GameState.gluttonCornerCells(corner).toSet()
        return dead.filterKeys { it !in free }
    }

    /** Baja [extra] turnos a todos los pozos (digestión acelerada). */
    fun accelerateDigest(dead: Map<Position, Int>, extra: Int = 2): Map<Position, Int> {
        if (extra <= 0 || dead.isEmpty()) return dead
        return dead.mapNotNull { (p, t) ->
            val nt = t - extra
            if (nt <= 0) null else p to nt
        }.toMap()
    }
}
