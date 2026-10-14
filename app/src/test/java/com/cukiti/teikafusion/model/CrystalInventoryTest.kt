package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El cristal único (P-267, 2026-09-24): la mochila, la caída, la conversión de la mochila vieja por
 * tiers y —lo que importa de verdad— la ESCASEZ que eligió el usuario (~0,7×: hay que elegir a quién
 * mejorar). Si alguien toca las cifras y la campaña vuelve a dar para todo, lo dice este test.
 */
class CrystalInventoryTest {

    @Test
    fun `sumar y gastar cristales`() {
        val inv = CrystalInventory().add(10)
        assertEquals(10, inv.count())
        assertTrue(inv.canSpend(10))
        assertFalse(inv.canSpend(11))
        assertEquals(4, inv.spend(6).count())
        assertEquals(0, inv.spend(99).count())      // nunca negativo
        assertEquals(inv, inv.add(0))                // sumar nada no cambia nada
    }

    @Test
    fun `la caida es fija por capitulo y paga el 3 estrellas`() {
        val primero = LevelMap.chapterStart[0]
        val ultimo = LevelMap.totalLevels
        assertEquals(2, CrystalEconomy.perLevel(primero))
        assertEquals(5, CrystalEconomy.perLevel(ultimo))
        assertEquals(1, CrystalEconomy.perLevel(1))  // lección
        assertEquals(CrystalEconomy.perLevel(primero) + CrystalEconomy.THREE_STAR_BONUS, CrystalEconomy.dropFor(primero, 3))
        assertEquals(CrystalEconomy.perLevel(primero), CrystalEconomy.dropFor(primero, 2))
        // Nunca baja al avanzar.
        (2..LevelMap.totalLevels).forEach { l ->
            assertTrue("nivel $l", CrystalEconomy.perLevel(l) >= CrystalEconomy.perLevel(l - 1) || l == LevelMap.introLevels + 1)
        }
    }

    /** El corazón de P-267: la campaña da ~0,7× lo que cuesta mejorar a los 14 al máximo. */
    @Test
    fun `la campaña da menos de lo que cuesta mejorarlo todo pero mucho`() {
        val campaña = (1..LevelMap.totalLevels).sumOf { CrystalEconomy.perLevel(it) } +
            LevelMap.chapters.size * CrystalEconomy.MURAL_BONUS
        val todo = NamedTeikaRegistry.all.sumOf { c ->
            var u = CompanionAbilityUpgrades()
            var total = 0
            while (!u.isMaxed(c.id)) { total += u.crystalCostToNext(c.id); u = u.withUpgrade(c.id) }
            total
        }
        val ratio = campaña.toFloat() / todo
        // Sin 3★: con todas las 3★ sube ~0,19. El objetivo del diseño es ~0,7 en una partida normal.
        assertTrue("la campaña da $campaña de $todo (${"%.2f".format(ratio)}×)", ratio in 0.6f..0.85f)
    }

    @Test
    fun `la mochila vieja por tiers se convierte una vez y con tope`() {
        val vieja = CrystalInventory(counts = mapOf(1 to 10, 2 to 2, 4 to 1))   // 10 + 6 + 27 = 43
        // Tope alto: se convierte entera.
        val alta = vieja.normalized(maxUnlockedLevel = LevelMap.totalLevels)
        assertEquals(43, alta.count())
        assertTrue(alta.legacyConverted)
        assertTrue(alta.counts.isEmpty())
        // Idempotente: convertir otra vez no suma nada.
        assertEquals(alta, alta.normalized(LevelMap.totalLevels))
        // Tope bajo: lo que el sistema nuevo habría dado hasta ese nivel.
        val tope = CrystalEconomy.cumulativeBefore(LevelMap.chapterStart[0] + 3)
        assertEquals(tope, vieja.normalized(LevelMap.chapterStart[0] + 3).count())
        assertTrue(tope < 43)
    }

    @Test
    fun `una mochila nueva no se toca al normalizar`() {
        val nueva = CrystalInventory(crystals = 12, legacyConverted = true)
        assertEquals(nueva, nueva.normalized(200))
    }
}
