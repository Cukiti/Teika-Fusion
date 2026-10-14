package com.cukiti.teikafusion.model

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class PhysicalReplacementRulesTest {
    private val GameStateJson = kotlinx.serialization.json.Json { ignoreUnknownKeys=true; allowStructuredMapKeys=true }
    @Test fun `platform needs its real horizontal line not a secondary explosion`() {
        val p=Position(3,5)
        assertTrue(ElevatorRules.markedLine(p,listOf((0..7).map { Position(it,5) }.toSet())))
        assertFalse(ElevatorRules.markedLine(p,listOf((0..7).map { Position(3,it) }.toSet())))
        assertFalse(ElevatorRules.markedLine(p,listOf(setOf(p))))
    }
    @Test fun `corridor respects radius obstacles lava and central wall`() {
        assertTrue(ElevatorRules.corridor(3.5f,5.5f,3.5f,.5f,8,8,emptySet()))
        assertFalse(ElevatorRules.corridor(3.5f,5.5f,3.5f,.5f,8,8,setOf(Position(3,4))))
        assertFalse(ElevatorRules.corridor(3.5f,5.5f,3.5f,1f,8,8,setOf(Position(4,4))))
        assertFalse(ElevatorRules.corridor(3.5f,5.5f,3.5f,.5f,8,8,emptySet(),top=4))
        assertFalse(ElevatorRules.corridor(3.8f,5.5f,3.5f,.5f,8,8,emptySet(),splitAtX=4))
        assertFalse(ElevatorRules.corridor(Float.NaN,5.5f,3.5f,.5f,8,8,emptySet()))
    }
    @Test fun `trail ignores discontinuities and keeps a bounded ordered path`() {
        assertEquals(.5f,TrailRules.distance(1f,1f,1.5f,1f),.001f)
        assertEquals(0f,TrailRules.distance(1f,1f,6f,1f),0f)
        assertEquals(0f,TrailRules.distance(Float.NaN,1f,1f,1f),0f)
        var path=emptyList<Position>()
        repeat(100) { path=TrailRules.path(path,Position(it%8,it/8%8)) }
        assertEquals(16,path.size)
        assertEquals(path,TrailRules.path(path,path.last()))
    }
    @Test fun `trail sweep is bounded and never crosses ninja wall`() {
        val sweep=TrailRules.sweep(listOf(Position(3,0)),8,8,4)
        assertFalse(Position(4,0) in sweep)
        assertTrue(Position(2,0) in sweep)
        assertTrue(sweep.all { it.x in 0..7 && it.y in 0..7 })
    }
    @Test fun `save migration removes legacy egg and clears retired trail and returns retired elevator passenger to origin`() {
        val level=HouseRule.levelsWith(HouseRule.ELEVATOR).first()
        val body=TeikaBodySave("passenger",TeikaType.TEIKA_1,3.5f,4.5f)
        val trip=ElevatorTrip(body.id,3.5f,5.5f,3.5f,.5f)
        val original=GameState(level=level,eggCell=Position(3,3),eggCracks=2,elevatorTrip=trip,teikaBodySaves=listOf(body))
        val saved=ReplacementMigration.restore(GameStateJson.decodeFromString<GameState>(GameStateJson.encodeToString(original)))
        assertNull(saved.eggCell); assertEquals(0,saved.eggCracks); assertNull(saved.elevatorTrip); assertTrue(saved.clawCharged); assertEquals(5.5f,saved.teikaBodySaves.single().y,0f)
        assertNull(ReplacementMigration.restore(original.copy(teikaBodySaves=emptyList())).elevatorTrip)
        val grid=(0..7).associate { Position(it,3) to CellContent.Empty }
        val trail=GameState(level=HouseRule.levelsWith(HouseRule.TRAIL).first(),grid=grid,trailCells=listOf(Position(1,3),Position(2,3)))
        assertEquals(emptyList<Position>(),ReplacementMigration.restore(GameStateJson.decodeFromString<GameState>(GameStateJson.encodeToString(trail))).trailCells)
    }
    @Test fun `a wall raised after charging separates the path before its sweep`() {
        val path=(1..6).map { Position(it,3) }
        val line=setOf(Position(2,3),Position(2,4))
        assertEquals(path.take(3),TrailRules.crossedSegment(path,listOf(line),emptySet(),4))
        val diagonal=listOf(Position(1,1),Position(2,2),Position(3,3))
        assertEquals(diagonal,TrailRules.crossedSegment(diagonal,listOf(setOf(Position(2,2))),emptySet()))
        val blocked=setOf(Position(3,3))
        assertEquals(path.take(2),TrailRules.crossedSegment(path,listOf(line),blocked))
        assertTrue(TrailRules.crossedSegment(path,listOf(setOf(Position(0,0))),emptySet()).isEmpty())
    }
    @Test fun `trail impacts follow pulse position rather than number of destroyed blocks`() {
        val path=(0..5).map { Position(3,it) }
        assertEquals(180,TrailRules.impact(Position(4,0),path))
        assertEquals(580,TrailRules.impact(Position(4,5),path))
    }
    @Test fun `campaign positions remain unchanged and retired mechanics never reactivate`() {
        assertEquals(18,HouseRule.ELEVATOR.ordinal); assertEquals(22,HouseRule.TRAIL.ordinal)
        assertEquals(28,HouseRule.entries.size)
        assertEquals(listOf(34,45,56,106,117,156,167,217,228,278),HouseRule.levelsWith(HouseRule.ELEVATOR).map { it-7 })
        assertEquals(listOf(11,22,72,83,133,144,194,205,244,255,266),HouseRule.levelsWith(HouseRule.TRAIL).map { it-7 })
    }
}
