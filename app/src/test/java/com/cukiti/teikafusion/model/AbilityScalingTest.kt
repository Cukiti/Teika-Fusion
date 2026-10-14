package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P-270 (2026-09-24): cinco niveles de habilidad. Los tres primeros son EXACTAMENTE los de antes —nadie
 * nota nada al actualizar— y cada nivel mejora algo: más cantidad, o eco donde el 3 ya lo hacía todo.
 */
class AbilityScalingTest {

    @Test
    fun `los niveles 1 a 3 son los de antes`() {
        assertEquals(listOf(1, 2, 3), (1..3).map { AbilityScaling.cristalChains(it) })
        assertEquals(listOf(1, 2, 3), (1..3).map { AbilityScaling.dormilonaRows(it) })
        assertEquals(listOf(4, 7, 12), (1..3).map { AbilityScaling.miedosoPasses(it) })
        assertEquals(listOf(2, 4, 6), (1..3).map { AbilityScaling.plantaBounces(it) })
        assertEquals(listOf(3, 4, 5), (1..3).map { AbilityScaling.hieloIcicles(it) })
        assertEquals(listOf(3, 5, 8), (1..3).map { AbilityScaling.fantasmaMoves(it) })
        assertEquals(listOf(8, 12, 18), (1..3).map { AbilityScaling.estrellaCap(it) })
        assertEquals(listOf(4, 8, Int.MAX_VALUE), (1..3).map { AbilityScaling.glotonBites(it) })
        assertEquals(listOf(4, 8, Int.MAX_VALUE), (1..3).map { AbilityScaling.ninjaVictims(it) })
        assertEquals(listOf(3, 5, 7), (1..3).map { AbilityScaling.ninjaSlashes(it) })
        assertEquals(listOf(1, 2, Int.MAX_VALUE), (1..3).map { AbilityScaling.fuegoPieces(it) })
        assertEquals(listOf(1, 2, Int.MAX_VALUE), (1..3).map { AbilityScaling.electricoTargets(it) })
    }

    /** Cada subida da ALGO: más cantidad o, donde ya no cabe más, eco. */
    @Test
    fun `ningun nivel sube para nada`() {
        val cantidad: Map<String, (Int) -> Int> = mapOf(
            "cristal" to AbilityScaling::cristalChains, "dormilona" to AbilityScaling::dormilonaRows,
            "miedoso" to AbilityScaling::miedosoPasses, "planta" to AbilityScaling::plantaBounces,
            "hielo" to AbilityScaling::hieloIcicles, "fantasma" to AbilityScaling::fantasmaMoves,
            "estrella" to AbilityScaling::estrellaCap,
        )
        cantidad.forEach { (id, f) ->
            (2..COMPANION_ABILITY_MAX_LEVEL).forEach { l -> assertTrue("$id nivel $l", f(l) > f(l - 1)) }
        }
        listOf("fuego", "electrico", "toxico", "gloton", "rey", "ninja", "galactico").forEach { id ->
            assertEquals(0f, AbilityScaling.echoChance(id, 3))
            assertTrue(AbilityScaling.echoChance(id, 5) > AbilityScaling.echoChance(id, 4))
            assertTrue(AbilityScaling.echoChance(id, 4) > 0f)
        }
        // Quien sigue creciendo en cantidad no lleva eco.
        assertEquals(0f, AbilityScaling.echoChance("planta", 5))
    }

    /** Lo que la ficha dice por nivel tiene que existir para los cinco y para todos. */
    @Test
    fun `cada compañero tiene un texto por nivel`() {
        NamedTeikaRegistry.all.forEach { c ->
            val textos = COMPANION_ABILITY_LEVEL_TEXTS[c.id]
            assertEquals("textos de ${c.id}", COMPANION_ABILITY_MAX_LEVEL, textos?.size)
        }
    }

    @Test
    fun `mejorar cuesta cristal y teikoins en cada subida y nada al maximo`() {
        NamedTeikaRegistry.all.forEach { c ->
            var u = CompanionAbilityUpgrades()
            repeat(COMPANION_ABILITY_MAX_LEVEL - 1) {
                assertTrue(u.crystalCostToNext(c.id) > 0)
                assertTrue(u.costToNext(c.id) in 1 until Int.MAX_VALUE)
                u = u.withUpgrade(c.id)
            }
            assertTrue(u.isMaxed(c.id))
            assertEquals(0, u.crystalCostToNext(c.id))
        }
    }

    /**
     * El porcentaje de Remote Config se aplica en el MODELO: la ficha (precio, punto rojo, botón de
     * aguante) y el motor que cobra hacen la misma cuenta. Antes solo la hacía el motor.
     */
    @Test
    fun `el porcentaje remoto cambia el precio y la pregunta de si se puede mejorar`() {
        val id = NamedTeikaRegistry.all.first { companionAbilityEnabled(it.id) }.id
        val u = CompanionAbilityUpgrades()
        val base = u.costToNext(id)
        assertEquals(base, u.costToNext(id, 100))
        assertEquals((base.toLong() * 150 / 100).toInt(), u.costToNext(id, 150))
        val crystals = CrystalInventory().add(u.crystalCostToNext(id))
        assertTrue(u.canUpgradeNow(id, base, crystals))
        assertTrue(!u.canUpgradeNow(id, base, crystals, costPercent = 150))
        val maxed = (1 until COMPANION_ABILITY_MAX_LEVEL).fold(u) { acc, _ -> acc.withUpgrade(id) }
        assertEquals(Int.MAX_VALUE, maxed.costToNext(id, 150))
    }
}
