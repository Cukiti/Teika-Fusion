package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-357: las reglas de este nivel que enseña la pausa. */
class LevelRulesTest {

    @Test
    fun `el tutorial no tiene seccion de reglas`() {
        for (level in TutorialCampaign.FIRST_LEVEL..TutorialCampaign.LAST_LEVEL) {
            assertTrue("lección $level", LevelRules.forLevel(level).isEmpty())
        }
    }

    @Test
    fun `cada nivel cuenta su bioma, su regla de la casa y el giro`() {
        for (level in (LevelMap.introLevels + 1)..LevelMap.totalLevels) {
            val titles = LevelRules.forLevel(level).map { it.title }
            val biome = BiomeRegistry.currentBiome(level)
            assertTrue("nivel $level sin su bioma: $titles", biome.displayName.uppercase() in titles)
            HouseRule.forLevel(level)?.let { assertTrue("nivel $level sin su regla: $titles", it.badge in titles) }
            assertTrue("nivel $level sin el giro", "GIRAR" in titles)
            assertEquals("nivel $level y el banco", BlockBankRules.appliesTo(level), "FICHONA" in titles)
            assertTrue("nivel $level con un texto vacío", LevelRules.forLevel(level).none { it.text.isBlank() })
        }
    }

    @Test
    fun `el cristal no habla del banco y el sueno si`() {
        assertFalse("FICHONA" in LevelRules.forLevel(LevelMap.chapterStart[0]).map { it.title })
        assertTrue("FICHONA" in LevelRules.forLevel(LevelMap.chapterStart[1]).map { it.title })
    }

    @Test
    fun `toda regla de la casa tiene nombre y ninguno se repite`() {
        val badges = HouseRule.entries.map { it.badge }
        assertTrue(badges.none { it.isBlank() })
        assertEquals(badges.size, badges.toSet().size)
    }
}
