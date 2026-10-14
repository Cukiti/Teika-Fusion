package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contratos del bioma Cocina Voraz ([GluttonBiomeRules]):
 * reloj ocioso, boca, mordisco, digestión, targets y Teikas.
 */
class GluttonBiomeTest {

    @Test
    fun advanceIdle_ticksToOpenThenBite() {
        val (c1, open1, bite1) = GluttonBiomeRules.advanceIdle(0, mouthOpen = false, fed = false)
        assertEquals(1, c1)
        assertFalse(open1)
        assertFalse(bite1)

        val (c2, open2, bite2) = GluttonBiomeRules.advanceIdle(1, mouthOpen = false, fed = false)
        assertEquals(2, c2)
        assertTrue(open2)
        assertFalse(bite2)

        val (c3, open3, bite3) = GluttonBiomeRules.advanceIdle(2, mouthOpen = true, fed = false)
        assertEquals(0, c3)
        assertFalse(open3)
        assertTrue(bite3)
    }

    @Test
    fun advanceIdle_fed_resets() {
        val (c, open, bite) = GluttonBiomeRules.advanceIdle(2, mouthOpen = true, fed = true)
        assertEquals(0, c)
        assertFalse(open)
        assertFalse(bite)
    }

    @Test
    fun digest_removesExpiredAndDecrements() {
        val dead = mapOf(
            Position(0, 0) to 1,
            Position(1, 0) to 3
        )
        val next = GluttonBiomeRules.digest(dead)
        assertFalse(Position(0, 0) in next)
        assertEquals(2, next[Position(1, 0)])
    }

    /**
     * REGRESIÓN (playtest 2026-07-20): el mordisco se lleva el triángulo ENTERO, con Teika encima o
     * sin él. Antes se filtraban las celdas pisadas por un cuerpo y, como la gravedad amontona los
     * Teikas justo en los rincones, la dentellada se comía una sola casilla y parecía buguearse.
     * Ahora el cuerpo se aparta a empujones (ver [GluttonBiomeRules.pushDirectionFromCorner]).
     */
    @Test
    fun biteableCells_muerdeElTrianguloEnteroAunqueHayaTeika() {
        val corner = GameState.gluttonCornerCells(0) // (0,0),(1,0),(0,1)
        val bitten = GluttonBiomeRules.biteableCells(corner)
        assertEquals(3, bitten.size)
        assertTrue(Position(0, 0) in bitten)
        assertTrue(Position(1, 0) in bitten) // estrella: también se muerde
        assertTrue(Position(0, 1) in bitten) // celda con Teika: ya NO se respeta
    }

    /** El empujón sale del rincón hacia DENTRO del tablero, en diagonal. */
    @Test
    fun pushDirectionFromCorner_empujaHaciaElCentro() {
        val (dx0, dy0) = GluttonBiomeRules.pushDirectionFromCorner(0)   // sup-izq
        assertTrue(dx0 > 0f && dy0 > 0f)
        val (dx3, dy3) = GluttonBiomeRules.pushDirectionFromCorner(3)   // inf-der
        assertTrue(dx3 < 0f && dy3 < 0f)
    }

    @Test
    fun eligibleCorners_excludesDeadTriangles() {
        val dead = GameState.gluttonCornerCells(0).toSet()
        assertFalse(GluttonBiomeRules.isCornerEligible(0, dead))
        assertTrue(GluttonBiomeRules.isCornerEligible(1, dead))
        val elig = GluttonBiomeRules.eligibleCorners(dead)
        assertFalse(0 in elig)
        assertTrue(1 in elig && 2 in elig && 3 in elig)
    }

    @Test
    fun holdCounterWhenNoCorner_isOpenMinusOne() {
        assertEquals(1, GluttonBiomeRules.holdCounterWhenNoCorner())
    }

    @Test
    fun freeOneCorner_clearsBlockedTriangleWithLowestTurns() {
        val c0 = GameState.gluttonCornerCells(0)
        val c1 = GameState.gluttonCornerCells(1)
        val dead = c0.associateWith { 2 } + c1.associateWith { 6 }
        val next = GluttonBiomeRules.freeOneCorner(dead)
        // Corner 0 (sum 6) se libera antes que corner 1 (sum 18).
        c0.forEach { assertFalse(it in next) }
        c1.forEach { assertTrue(it in next) }
        assertTrue(GluttonBiomeRules.eligibleCorners(next.keys).contains(0))
    }

    @Test
    fun accelerateDigest_removesExpired() {
        val dead = mapOf(Position(0, 0) to 2, Position(1, 0) to 5)
        val next = GluttonBiomeRules.accelerateDigest(dead, extra = 2)
        assertFalse(Position(0, 0) in next)
        assertEquals(3, next[Position(1, 0)])
    }

    @Test
    fun cornerCells_areThreePerCorner() {
        for (c in 0..3) {
            assertEquals(3, GameState.gluttonCornerCells(c).size)
        }
    }

    @Test
    fun deadCells_areFixedFill_notGalacticBlock() {
        // Contrato de diseño: glutton dead rellena líneas (fixed), no bloquea como galáctico.
        val s = GameState(gluttonDeadCells = mapOf(Position(0, 0) to 5))
        assertTrue(Position(0, 0) in s.unplaceableCells)
        assertTrue(Position(0, 0) in s.gluttonDeadCells.keys)
    }

    @Test
    fun constants_matchOpenBiteRecover() {
        assertEquals(2, GluttonBiomeRules.OPEN_AT)
        assertEquals(3, GluttonBiomeRules.BITE_AT)
        assertEquals(7, GluttonBiomeRules.RECOVER_TURNS)
        assertEquals(GluttonBiomeRules.OPEN_AT, GameState.GLUTTON_OPEN_AT)
        assertEquals(GluttonBiomeRules.BITE_AT, GameState.GLUTTON_BITE_AT)
    }

    /**
     * La pista tiene que describir la regla REAL (invariante de `mechanicHint`). Desde el 2026-09-05
     * el contrajuego del Glotón —y de los trece biomas con reloj— es **romper una fila**, y la
     * fusión ya no le calma: pedía «fusionar» un texto que ahora mentiría.
     */
    @Test
    fun mechanicHint_mentionsClearNotFuse() {
        val hint = mechanicHint(BiomeType.GLUTTON).lowercase()
        assertTrue(hint.contains("rompe"))   // P-353: «PARADA: rompe una línea que pase por el rincón…»
        assertTrue("la fusión ya no calma al Glotón", !hint.contains("fusion"))
        assertTrue(hint.contains("rincón") || hint.contains("rincon") || hint.contains("muerde"))
    }

    @Test
    fun chapter_gluttonThenToxic_secondary() {
        assertEquals(BiomeType.GLUTTON, LevelMap.biomeFor(188))
        assertEquals(BiomeType.GHOST, BiomeRegistry.previousBiome(188))
        assertEquals(BiomeType.TOXIC, LevelMap.biomeFor(208))
        assertEquals(BiomeType.GLUTTON, BiomeRegistry.previousBiome(208))
    }
}
