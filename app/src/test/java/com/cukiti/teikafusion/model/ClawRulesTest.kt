package com.cukiti.teikafusion.model
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class ClawRulesTest {
    private fun state()=GameState(level=HouseRule.levelsWith(HouseRule.ELEVATOR).first(),grid=(0..7).flatMap { y->(0..7).map { x->Position(x,y) to CellContent.Empty } }.toMap())
    private val b=TeikaBodySave("load",TeikaType.TEIKA_1,1.5f,6.5f)
    @Test fun `transport flies over wood without destroying it but landing requires room`() {
        val s=state().copy(grid=state().grid+(Position(3,4) to CellContent.Wood(targetColor=TargetColor.RED,teikaSeed=true)))
        assertTrue(ClawRules.destination(s,b,listOf(b),6.5f,2.5f))
        assertFalse(ClawRules.destination(s,b,listOf(b),3.5f,4.5f))
        assertTrue(s.grid[Position(3,4)] is CellContent.Wood)
    }
    @Test fun `whole radius respects bounds protection lava and other physical bodies`() {
        val large=b.copy(type=TeikaType.TEIKA_4,x=2f,y=6f)
        assertFalse(ClawRules.destination(state(),large,listOf(large),7f,2f))
        assertFalse(ClawRules.destination(state().copy(frozenCells=setOf(Position(6,2))),b,listOf(b),6.5f,2.5f))
        assertFalse(ClawRules.destination(state().copy(biomePrimary=BiomeType.VOLCANO,lavaRow=3),b,listOf(b),6.5f,2.5f))
        assertFalse(ClawRules.destination(state(),b,listOf(b,b.copy(id="other",x=6.5f,y=2.5f)),6.5f,2.5f))
        assertTrue(ClawRules.destination(state(),large,listOf(large),6f,2f))
    }
    @Test fun `ninja wall separates both the load and its destination`() {
        val s=state().copy(ninjaCutActive=true)
        assertFalse(ClawRules.destination(s,b,listOf(b),6.5f,2.5f))
        assertFalse(ClawRules.destination(s,b,listOf(b),3.8f,2.5f))
        assertTrue(ClawRules.destination(s,b,listOf(b),3.5f,2.5f))
    }
    @Test fun `frozen bubbles nonfinite and unchanged destinations never commit`() {
        assertFalse(ClawRules.destination(state(),b.copy(frozenTurns=1),listOf(b),6.5f,2.5f))
        assertFalse(ClawRules.destination(state(),b.copy(bubble=1),listOf(b),6.5f,2.5f))
        assertFalse(ClawRules.destination(state(),b,listOf(b),Float.NaN,2.5f))
        assertFalse(ClawRules.destination(state(),b,listOf(b),b.x,b.y))
    }
    @Test fun `current trip survives JSON migration and pending never restores a second charge`() {
        val t=ClawTrip(b.id,b.x,b.y,6.5f,2.5f,.5f,.5f); val (x,y)=ClawRules.position(t)
        val s=state().copy(clawCharged=true,clawTrip=t,teikaBodySaves=listOf(b.copy(x=x,y=y)))
        val json=kotlinx.serialization.json.Json { allowStructuredMapKeys=true; ignoreUnknownKeys=true }
        val loaded=ReplacementMigration.restore(json.decodeFromString<GameState>(json.encodeToString(s)))
        assertEquals(t,loaded.clawTrip); assertFalse(loaded.clawCharged)
        assertEquals(loaded,ReplacementMigration.restore(loaded))
    }
    @Test fun `invalid restored trip refunds once and returns passenger to origin`() {
        val t=ClawTrip(b.id,b.x,b.y,6.5f,2.5f,.5f,.6f)
        val s=state().copy(clawTrip=t,teikaBodySaves=listOf(b.copy(x=4f,y=4f)),kingLaneCells=setOf(Position(6,2)))
        val loaded=ReplacementMigration.restore(s)
        assertNull(loaded.clawTrip); assertTrue(loaded.clawCharged); assertEquals(b,loaded.teikaBodySaves.single())
        assertEquals(loaded,ReplacementMigration.restore(loaded))
    }
    @Test fun `legacy elevator returns a real body and transfers its charge without births`() {
        val s=state().copy(elevatorTrip=ElevatorTrip(b.id,b.x,b.y,b.y-2,.5f),teikaBodySaves=listOf(b.copy(y=b.y-1)))
        val loaded=ReplacementMigration.restore(s)
        assertNull(loaded.elevatorTrip); assertFalse(loaded.elevatorCharged); assertTrue(loaded.clawCharged)
        assertEquals(listOf(b),loaded.teikaBodySaves)
        assertFalse(ElevatorRules.appliesTo(s.level))
    }
    @Test fun `campaign slot stays stable with a fresh capsule and retired rules do not charge other levels`() {
        assertEquals(18,HouseRule.ELEVATOR.ordinal)
        assertEquals(listOf(34,45,56,106,117,156,167,217,228,278),HouseRule.levelsWith(HouseRule.ELEVATOR).map(LevelMap::displayNumber))
        assertEquals("teika_claw",MechanicCapsule.ELEVATOR.id)
        val s=state().copy(level=1,elevatorCharged=true)
        assertFalse(ReplacementMigration.restore(s).clawCharged)
    }
}
