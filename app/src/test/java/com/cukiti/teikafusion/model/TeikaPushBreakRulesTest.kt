package com.cukiti.teikafusion.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeikaPushBreakRulesTest {

    @Test
    fun `crystal armor does not preserve an impossible overlap`() {
        assertTrue(
            TeikaPushBreakRules.canBreak(
                CellContent.Wood(armor = 2),
                protectedByBiome = false
            )
        )
    }

    @Test
    fun `normal wood is also allowed to open physical space`() {
        assertTrue(
            TeikaPushBreakRules.canBreak(
                CellContent.Wood(armor = 0),
                protectedByBiome = false
            )
        )
    }

    @Test
    fun `coils and biome protected cells remain hard terrain`() {
        assertFalse(
            TeikaPushBreakRules.canBreak(
                CellContent.Wood(coil = 1),
                protectedByBiome = false
            )
        )
        assertFalse(
            TeikaPushBreakRules.canBreak(
                CellContent.Wood(armor = 2),
                protectedByBiome = true
            )
        )
    }
}
