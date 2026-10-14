package com.cukiti.teikafusion.model

import org.junit.Assert.*
import org.junit.Test

class BlockBankTest {
    @Test fun `el peaje nunca fabrica material ni elimina un monomino`() {
        for (size in 1..27) assertTrue(BlockBankRules.blocksFor(size) in 1..size)
        assertEquals(4, BlockBankRules.blocksFor(5))
        assertEquals(1, BlockBankRules.blocksFor(1))
    }
    @Test fun `enfriamiento de veinte turnos con limite exacto`() {
        assertEquals(20, BlockBankRules.COOLDOWN_TURNS)
        val bank = BlockBank(blocks = 30, lockedUntil = 40)
        assertEquals(20, BlockBankRules.turnsLocked(bank, 20))
        assertTrue(BlockBankRules.isLocked(bank, 39))
        assertFalse(BlockBankRules.isLocked(bank, 40))
    }
    @Test fun `el taller conserva el desbloqueo por capitulo y excluye lecciones`() {
        assertFalse(BlockBankRules.appliesTo(TutorialCampaign.LAST_LEVEL))
        assertFalse(BlockBankRules.appliesTo(BlockBankRules.FIRST_LEVEL - 1))
        assertTrue(BlockBankRules.appliesTo(BlockBankRules.FIRST_LEVEL))
        assertTrue(BlockBankRules.appliesTo(LevelMap.totalLevels))
    }
}
