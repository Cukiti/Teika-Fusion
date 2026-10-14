package com.cukiti.teikafusion.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class ForgeRulesTest {
    private val cells=listOf(Position(2,2),Position(3,2),Position(2,3),Position(3,3))
    private fun state()=GameState(level=HouseRule.levelsWith(HouseRule.SHOCKWAVE).first(),
        grid=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()+
            cells.mapIndexed { i,p -> p to CellContent.Wood(colorType=i,targetColor=if(i==0) TargetColor.RED else null) }.toMap())
    @Test fun `four distinct connected woods of any color permit a reclaimed output`() {
        val s=state(); val plan=ForgeRules.plan(s,emptyList(),cells,cells.first())!!
        assertEquals(cells,plan.cells); assertEquals(cells.first(),plan.destination)
        assertTrue(ForgeRules.hasMove(s,emptyList()))
        assertNull(ForgeRules.plan(s,emptyList(),cells.take(3),cells.first()))
        assertNull(ForgeRules.plan(s,emptyList(),cells+cells.first(),cells.first()))
        assertFalse(ForgeRules.connected(listOf(Position(0,0),Position(1,0),Position(3,0),Position(4,0))))
    }
    @Test fun `seed and every protected wood are unavailable without changing their contents`() {
        val s=state(); val p=cells.first(); val original=s.grid[p] as CellContent.Wood
        val forbidden=listOf(original.copy(teikaSeed=true),original.copy(armor=1),original.copy(coil=1),
            original.copy(poisoned=true),original.copy(magma=true),original.copy(stubborn=true))
        forbidden.forEach { w -> assertNull(ForgeRules.plan(s.copy(grid=s.grid+(p to w)),emptyList(),cells,Position(6,6))) }
        listOf(s.copy(frozenCells=setOf(p)),s.copy(glacierFrostCells=setOf(p)),s.copy(hiddenWoodCells=setOf(p)),
            s.copy(kingLaneCells=setOf(p)),s.copy(starAstroTargetCells=setOf(p)),s.copy(starAstroMoonWoodCells=setOf(p)),
            s.copy(gluttonDeadCells=mapOf(p to 1)),s.copy(biomePrimary=BiomeType.VOLCANO,lavaRow=3)).forEach {
            assertNull(ForgeRules.plan(it,emptyList(),cells,Position(6,6))); assertFalse(ForgeRules.hasMove(it,emptyList()))
        }
    }
    @Test fun `destination checks full body radius and every remaining solid cell`() {
        val s=state(); val dest=Position(6,6)
        assertNotNull(ForgeRules.plan(s,emptyList(),cells,dest))
        assertNull(ForgeRules.plan(s.copy(grid=s.grid+(dest to CellContent.Wood())),emptyList(),cells,dest))
        assertNull(ForgeRules.plan(s.copy(frozenCells=setOf(dest)),emptyList(),cells,dest))
        assertNull(ForgeRules.plan(s.copy(kingLaneCells=setOf(dest)),emptyList(),cells,dest))
        assertNull(ForgeRules.plan(s, listOf(TeikaBodySave("large",TeikaType.TEIKA_3,7.7f,6.5f)),cells,dest))
        assertNotNull(ForgeRules.plan(s,listOf(TeikaBodySave("tangent",TeikaType.TEIKA_1,7.5f,6.5f)),cells,dest))
        assertNull(ForgeRules.plan(s,listOf(TeikaBodySave("bad",TeikaType.TEIKA_1,Float.NaN,6f)),cells,dest))
        assertNull(ForgeRules.plan(s,emptyList(),cells,Position(-1,0)))
    }
    @Test fun `ninja separates both materials and output and bodies protect materials`() {
        val s=state().copy(ninjaCutActive=true)
        assertNull(ForgeRules.plan(s,emptyList(),cells,Position(6,6)))
        assertNotNull(ForgeRules.plan(s,emptyList(),cells,Position(0,0)))
        assertFalse(ForgeRules.hasMove(s,listOf(TeikaBodySave("cover",TeikaType.TEIKA_2,2.5f,2.5f))))
        val cross=listOf(Position(3,1),Position(4,1),Position(3,2),Position(4,2))
        val withWood=s.copy(grid=s.grid+cross.associateWith { CellContent.Wood() })
        assertNull(ForgeRules.plan(withWood,emptyList(),cross,Position(0,0)))
    }
    @Test fun `old saves gain no charge and committed saves do not replay rewards`() {
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; encodeDefaults=true }
        val s=state().copy(replacementRulesVersion=11)
        val oldJson=json.encodeToString(s).replace("\"forgeCharged\":false,","").replace("\"forgePending\":false,","")
        val legacy=json.decodeFromString<GameState>(oldJson)
        assertFalse(ReplacementMigration.restore(legacy).forgeCharged)
        val saved=ReplacementMigration.restore(s.copy(forgePending=true,forgeCharged=true,
            targetsCollected=mapOf(TargetColor.RED to 1),teikaBodySaves=listOf(TeikaBodySave("born",TeikaType.TEIKA_1,6.5f,6.5f))))
        assertTrue(saved.forgePending); assertFalse(saved.forgeCharged); assertEquals(12,saved.replacementRulesVersion)
        assertEquals(saved,ReplacementMigration.restore(json.decodeFromString<GameState>(json.encodeToString(saved))))
        assertFalse(ReplacementMigration.restore(saved.copy(level=1)).forgePending)
        assertEquals(legacy.grid,saved.grid)
    }
    @Test fun `campaign slot remains stable and old wave is never active`() {
        assertEquals(17,HouseRule.SHOCKWAVE.ordinal)
        assertEquals("FORJA TEIKA",HouseRule.SHOCKWAVE.badge)
        assertEquals("forge",MechanicCapsule.SHOCKWAVE.id)
        assertEquals(28,HouseRule.entries.size)
        assertEquals(listOf(48,59,109,120,159,170,181,220,231,281),HouseRule.levelsWith(HouseRule.SHOCKWAVE).map(LevelMap::displayNumber))
        for(level in 1..LevelMap.totalLevels) {
            assertEquals(HouseRule.forLevel(level)==HouseRule.SHOCKWAVE,ForgeRules.appliesTo(level))
            assertFalse(ShockwaveRules.appliesTo(level))
        }
    }
}
