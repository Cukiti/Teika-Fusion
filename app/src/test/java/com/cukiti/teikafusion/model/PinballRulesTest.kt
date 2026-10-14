package com.cukiti.teikafusion.model

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

class PinballRulesTest {
    private val GameStateJson=kotlinx.serialization.json.Json { allowStructuredMapKeys=true }
    @Test fun `slingshot direction is opposite pull and rejects short or invalid gestures`() {
        assertNull(PinballRules.velocity(.1f,0f)); assertNull(PinballRules.velocity(Float.NaN,0f))
        assertEquals(-10f,PinballRules.velocity(2f,0f)!!.first,0f)
        assertEquals(10f,PinballRules.velocity(0f,-2f)!!.second,0f)
    }
    @Test fun `only ordinary unprotected wood is destructible`() {
        assertTrue(PinballRules.canBreak(CellContent.Wood(),false))
        assertFalse(PinballRules.canBreak(CellContent.Wood(armor=1),false))
        assertFalse(PinballRules.canBreak(CellContent.Wood(coil=1),false))
        assertFalse(PinballRules.canBreak(CellContent.Wood(poisoned=true),false))
        assertFalse(PinballRules.canBreak(CellContent.Wood(),true))
    }
    @Test fun `old charge migrates while invalid flights cannot resume`() {
        val level=HouseRule.levelsWith(HouseRule.TRAMPOLINE).first()
        val legacy=ReplacementMigration.restore(GameState(level=level,trampolineCharged=true,trampolineCell=Position(2,7)))
        assertTrue(legacy.pinballCharged); assertFalse(legacy.trampolineCharged); assertNull(legacy.trampolineCell)
        val flight=PinballFlight("saved",10f,0f)
        val state=legacy.copy(pinballFlight=flight,teikaBodySaves=listOf(TeikaBodySave("saved",TeikaType.TEIKA_1,2f,3f)))
        val json=GameStateJson.encodeToString(state)
        assertEquals(flight,ReplacementMigration.restore(GameStateJson.decodeFromString<GameState>(json)).pinballFlight)
        assertNull(ReplacementMigration.restore(state.copy(pinballFlight=flight.copy(secondsLeft=Float.NaN))).pinballFlight)
        assertNull(ReplacementMigration.restore(state.copy(teikaBodySaves=emptyList())).pinballFlight)
        assertEquals(listOf(8,19,69,80,130,141,180,191,202,241,252),HouseRule.levelsWith(HouseRule.TRAMPOLINE).map(LevelMap::displayNumber))
        assertTrue(HouseRule.levelsWith(HouseRule.TRAMPOLINE).all(PinballRules::appliesTo))
        assertFalse(TrampolineRules.appliesTo(level))
    }
}
