package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El inventario canónico de COMPAÑEROS, fijado por test (la hermana de `BiomeRegistryTest`):
 * **14 compañeros con nombre · 14 habilidades · exactamente uno por capítulo/bioma.**
 *
 *  · Los ids son CLAVES persistidas (DataStore `claimedCharacterIds`, Cloud Save v3, habilidades,
 *    VFX): renombrar uno des-reclamaría compañeros de todo el mundo. Cambios aquí deben romper
 *    este test a propósito.
 *  · `unlockLevel` se DERIVA de [LevelMap.chapterStart] — si un compañero se desvía de su
 *    capítulo, el desbloqueo y el mapa dejan de contar la misma historia.
 *  · Los 4 desactivados (decisión 2026-07-11) deben seguir existiendo en el registro: son
 *    colección sin habilidad, no fantasmas.
 */
class NamedTeikaRegistryTest {

    @Test
    fun canonicalInventoryIsFourteenCompanionsOnePerChapter() {
        assertEquals(14, NamedTeikaRegistry.all.size)
        assertEquals(LevelMap.chapters.size, NamedTeikaRegistry.all.size)
        // El compañero i se desbloquea exactamente al entrar al capítulo i.
        NamedTeikaRegistry.all.forEachIndexed { i, companion ->
            assertEquals("compañero ${companion.id} fuera de su capítulo",
                LevelMap.chapterStart[i], companion.unlockLevel)
        }
    }

    @Test
    fun idsNamesAndLottiesAreUnique() {
        val all = NamedTeikaRegistry.all
        assertEquals("ids duplicados", all.size, all.map { it.id }.distinct().size)
        assertEquals("nombres duplicados", all.size, all.map { it.name }.distinct().size)
        assertEquals("Lottie duplicado", all.size, all.map { it.lottieResId }.distinct().size)
    }

    @Test
    fun theFourteenPersistedIdsAreLiterallyPinned() {
        // Claves de DataStore/cloud: NO renombrar (ver KDoc de la clase).
        assertEquals(
            listOf(
                "cristal", "dormilona", "miedoso", "planta", "electrico", "fuego", "ninja",
                "hielo", "fantasma", "gloton", "toxico", "estrella", "rey", "galactico"
            ),
            NamedTeikaRegistry.all.map { it.id }
        )
    }

    @Test
    fun everyCompanionHasExactlyOneAbilityAndViceVersa() {
        val registryIds = NamedTeikaRegistry.all.map { it.id }.toSet()
        assertEquals("14 compañeros ↔ 14 habilidades", registryIds, COMPANION_ABILITIES.keys)
        // Cualquier desactivado debe existir en el registro (colección sin habilidad, no fantasmas).
        assertTrue(DISABLED_COMPANION_ABILITIES.all { it in registryIds })
        // Tras el rework de habilidades (2026-07-17) los 14 compañeros tienen habilidad activa: ninguno
        // desactivado. (electrico/fuego/hielo/toxico recuperaron habilidad; el resto se reescribió.)
        assertEquals(emptySet<String>(), DISABLED_COMPANION_ABILITIES)
    }

    @Test
    fun lookupsBehave() {
        // forLevel: el inicio de cada capítulo tiene compañero; un nivel intermedio no.
        NamedTeikaRegistry.all.forEach { assertEquals(it, NamedTeikaRegistry.forLevel(it.unlockLevel)) }
        val starts = NamedTeikaRegistry.all.map { it.unlockLevel }.toSet()
        val nonStart = (1..LevelMap.totalLevels).first { it !in starts }
        assertNull(NamedTeikaRegistry.forLevel(nonStart))
        assertNotNull(NamedTeikaRegistry.byId("dormilona"))
        assertNull(NamedTeikaRegistry.byId("no-existe"))
    }

    @Test
    fun unlockLevelsGrowStrictlyWithTheAdventure() {
        NamedTeikaRegistry.all.zipWithNext { a, b ->
            assertTrue("desbloqueos fuera de orden: ${a.id} → ${b.id}", a.unlockLevel < b.unlockLevel)
        }
    }
}
