package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Contratos del bioma Pantano Tóxico ([ToxicBiomeRules]) — EL VENENO ES UNA CAPA (2026-09-28):
 * se extiende un paso por colocación, salpica lejos cuando se atasca, y la siembra.
 */
class ToxicBiomeTest {

    private fun p(x: Int, y: Int) = Position(x, y)

    @Test
    fun `un paso de expansion alcanza a todos los vecinos de golpe y solo una vez`() {
        val poisoned = setOf(p(3, 3), p(4, 3))
        val wood = setOf(p(3, 2), p(4, 2), p(2, 3), p(5, 3), p(3, 4), p(6, 3))
        val step = ToxicBiomeRules.spreadStep(poisoned) { it in wood }
        // Los vecinos directos, uniforme; (6,3) está a dos casillas: espera al paso siguiente.
        assertEquals(setOf(p(3, 2), p(4, 2), p(2, 3), p(5, 3), p(3, 4)), step.map { it.second }.toSet())
        assertEquals(step.size, step.map { it.second }.toSet().size)
        // Cada destino sale de un envenenado que lo toca.
        step.forEach { (from, to) ->
            assertTrue(from in poisoned)
            assertEquals(1, kotlin.math.abs(from.x - to.x) + kotlin.math.abs(from.y - to.y))
        }
    }

    @Test
    fun `sin bloques vecinos la expansion no alcanza nada`() {
        assertTrue(ToxicBiomeRules.spreadStep(setOf(p(0, 0))) { false }.isEmpty())
        assertTrue(ToxicBiomeRules.spreadStep(emptySet()) { true }.isEmpty())
    }

    @Test
    fun `la expansion es determinista`() {
        val poisoned = setOf(p(1, 1), p(5, 5), p(2, 6))
        val a = ToxicBiomeRules.spreadStep(poisoned) { true }
        val b = ToxicBiomeRules.spreadStep(poisoned.reversed().toSet()) { true }
        assertEquals(a, b)
    }

    @Test
    fun `salpica al llegar a los turnos seguidos sin avance`() {
        assertFalse(ToxicBiomeRules.shouldSplash(ToxicBiomeRules.SPLASH_AFTER_TURNS - 1))
        assertTrue(ToxicBiomeRules.shouldSplash(ToxicBiomeRules.SPLASH_AFTER_TURNS))
    }

    @Test
    fun `la salpicadura cae lejos del veneno`() {
        val poisoned = setOf(p(0, 0), p(1, 0))
        val candidates = (0 until 8).flatMap { y -> (0 until 8).map { x -> p(x, y) } }.filter { it !in poisoned }
        val targets = ToxicBiomeRules.splashTargets(poisoned, candidates, Random(7))
        assertEquals(ToxicBiomeRules.SPLASH_COUNT, targets.size)
        targets.forEach { t ->
            val d = poisoned.minOf { maxOf(kotlin.math.abs(it.x - t.x), kotlin.math.abs(it.y - t.y)) }
            assertTrue("$t demasiado cerca", d >= ToxicBiomeRules.SPLASH_MIN_DISTANCE)
        }
    }

    @Test
    fun `sin sitio lejos la salpicadura usa lo mas lejano que haya`() {
        val poisoned = setOf(p(0, 0))
        val targets = ToxicBiomeRules.splashTargets(poisoned, listOf(p(1, 0), p(2, 0)), Random(1))
        assertEquals(setOf(p(1, 0), p(2, 0)), targets.toSet())
    }

    @Test
    fun `sin veneno la salpicadura vuelve a sembrarlo`() {
        val targets = ToxicBiomeRules.splashTargets(emptySet(), listOf(p(4, 4), p(0, 7)), Random(3))
        assertEquals(2, targets.size)
        assertEquals(null, ToxicBiomeRules.nearestSource(emptySet(), p(4, 4)))
    }

    @Test
    fun isSpreadCandidate_excludesSpecialsAndTeikas() {
        val plain = CellContent.Wood(colorType = 0)
        val pos = Position(3, 3)
        fun ok(w: CellContent.Wood = plain, lavaRow: Int = 0, unplaceable: Set<Position> = emptySet(),
               placed: Set<Position> = emptySet(), teikas: Set<Position> = emptySet()) =
            ToxicBiomeRules.isSpreadCandidate(w, pos, lavaRow, unplaceable, placed, teikas)
        assertTrue(ok())
        assertFalse(ok(plain.copy(poisoned = true)))
        assertFalse(ok(plain.copy(sludge = true)))
        // Las estrellas SÍ se envenenan desde el 2026-09-23.
        assertTrue(ok(plain.copy(targetColor = TargetColor.BLUE)))
        assertFalse(ok(plain.copy(armor = 1)))
        assertFalse(ok(plain.copy(coil = 2)))
        assertFalse(ok(lavaRow = 4))
        assertFalse(ok(unplaceable = setOf(pos)))
        // «No a la ficha recién colocada».
        assertFalse(ok(placed = setOf(pos)))
        assertFalse(ok(teikas = setOf(pos)))
    }

    @Test
    fun shouldSeedPoison_neverOnCrystalOrTarget() {
        assertTrue(ToxicBiomeRules.shouldSeedPoison(isCrystal = false, hasTarget = false, roll = 0.1f))
        assertFalse(ToxicBiomeRules.shouldSeedPoison(isCrystal = true, hasTarget = false, roll = 0.1f))
        assertFalse(ToxicBiomeRules.shouldSeedPoison(isCrystal = false, hasTarget = true, roll = 0.1f))
        assertFalse(ToxicBiomeRules.shouldSeedPoison(isCrystal = false, hasTarget = false, roll = 0.9f))
        assertTrue(
            ToxicBiomeRules.shouldSeedPoison(isCrystal = false, hasTarget = true, roll = 0.1f, allowTarget = true)
        )
    }

    @Test
    fun `el director usa las constantes de la regla`() {
        assertEquals(ToxicBiomeRules.SPLASH_AFTER_TURNS, LevelDirector.toxicSplashAfter(200))
        assertEquals(ToxicBiomeRules.SPLASH_COUNT, LevelDirector.toxicSplashCount(200))
        assertEquals(2, ToxicBiomeRules.POISON_CLEAR_PARTICLE_COLOR)
    }

    @Test
    fun `la pista describe la capa que se extiende salpica y se lava`() {
        val hint = mechanicHint(BiomeType.TOXIC).lowercase()
        assertTrue(hint.contains("veneno"))
        assertTrue(hint.contains("extiende"))
        assertTrue(hint.contains("salpica"))
        assertTrue(hint.contains("lava"))
        assertFalse(hint.contains("lodo"))
        assertFalse(hint.contains("pudr"))
    }

    @Test
    fun toxicHeavy_onlyPrimary_documentedViaHintSecondaryStar() {
        assertEquals(BiomeType.TOXIC, LevelMap.biomeFor(208))
        assertEquals(BiomeType.STAR, LevelMap.biomeFor(230))
        assertEquals(BiomeType.GLUTTON, BiomeRegistry.previousBiome(208))
        assertEquals(BiomeType.TOXIC, BiomeRegistry.previousBiome(230))
    }
}
