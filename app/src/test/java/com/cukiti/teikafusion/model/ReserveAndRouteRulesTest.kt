package com.cukiti.teikafusion.model

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json

class ReserveAndRouteRulesTest {
    private fun grid()=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()
    @Test fun `reserve exchange conserves identities masks and uses one exchange per refill`() {
        val level=HouseRule.levelsWith(HouseRule.RESERVE).first()
        val p=Piece(shape=listOf(Position(0,0),Position(1,0)),targetMask=listOf(TargetColor.RED,null),crystalMask=listOf(true,false),teikaMask=listOf(false,true))
        val held=p.copy(id="held",colorType=3)
        val s=GameState(level=level,grid=grid(),availablePieces=listOf(p),reservePieces=listOf(held))
        val swapped=requireNotNull(ReserveRules.exchange(s,p.id))
        assertEquals(listOf(held),swapped.availablePieces); assertEquals(listOf(p),swapped.reservePieces)
        assertNull(ReserveRules.exchange(swapped,held.id)); assertTrue(ReserveRules.canSwap(swapped.copy(trayRefillCount=1),held.id))
        assertEquals(0,swapped.placementsInLevel); assertEquals(s.blockBank,swapped.blockBank)
    }
    @Test fun `reserve cannot hide sleeping warnings or fichona and candidate queries remain scoped`() {
        val p=Piece(shape=listOf(Position(0,0))); val held=p.copy(id="held")
        val s=GameState(level=HouseRule.levelsWith(HouseRule.RESERVE).first(),grid=grid(),availablePieces=listOf(p),reservePieces=listOf(held))
        assertFalse(ReserveRules.canSwap(s.copy(drowsyMarkedId=p.id),p.id))
        assertFalse(ReserveRules.canSwap(s.copy(availablePieces=listOf(p.copy(isFichona=true))),p.id))
        assertEquals(listOf(p,held),ReserveRules.candidates(s,s.availablePieces))
        assertEquals(listOf(held),ReserveRules.candidates(s,listOf(held)))
        assertEquals(listOf(p),ReserveRules.candidates(s.copy(reserveSwapRefill=0),s.availablePieces))
    }
    @Test fun `weighted reserve hit testing cannot pick an active piece from the stored slot or a gap`() {
        for(width in listOf(320f,411f,800f)) {
            val gap=10f; val ratio=1f/3.7f; val play=(width-gap*3)*ratio
            repeat(3) { index ->
                val start=index*(play+gap)
                assertEquals(index,ReserveRules.slotIndex(start+play*.5f,width,gap,ratio))
                assertEquals(-1,ReserveRules.slotIndex(start+play+gap*.5f,width,gap,ratio))
            }
            assertEquals(-1,ReserveRules.slotIndex(width-1f,width,gap,ratio))
            assertEquals(-1,ReserveRules.slotIndex(-1f,width,gap,ratio))
        }
    }

    @Test fun `legacy five piece save preserves all blocks in three active slots and a reserve queue`() {
        val pieces=(0..4).map { Piece(id="legacy-$it",shape=listOf(Position(0,0)),targetMask=listOf(TargetColor.RED)) }
        val old=GameState(level=HouseRule.levelsWith(HouseRule.RESERVE).first(),grid=grid(),availablePieces=pieces,replacementRulesVersion=4)
        val json=Json { allowStructuredMapKeys=true; encodeDefaults=true; ignoreUnknownKeys=true }
        val restored=ReplacementMigration.restore(json.decodeFromString(GameState.serializer(),json.encodeToString(GameState.serializer(),old)))
        assertEquals(3,restored.availablePieces.size); assertEquals(pieces.drop(3),restored.reservePieces)
        assertEquals(pieces,(restored.availablePieces+restored.reservePieces))
        val next=requireNotNull(ReserveRules.exchange(restored,pieces[0].id))
        assertEquals(pieces[4].id,next.reservePieces.first().id)
        assertEquals(pieces.map { it.id }.toSet(),(next.availablePieces+next.reservePieces).map { it.id }.toSet())
        assertEquals(next,ReplacementMigration.restore(next))
    }
    @Test fun `routes use disjoint legal nodes and cannot cross a wall`() {
        val s=GameState(level=HouseRule.levelsWith(HouseRule.ROUTES).first(),grid=grid(),ninjaCutActive=true)
        val tracks=RouteRules.reconcile(s,s.grid.keys)
        assertEquals(listOf(3,3),tracks.map { it.cells.size })
        assertEquals(6,tracks.flatMap { it.cells }.distinct().size)
        tracks.forEach { t -> t.cells.zipWithNext().forEach { (a,b) ->
            assertEquals(1,kotlin.math.abs(a.x-b.x)+kotlin.math.abs(a.y-b.y)); assertEquals(a.x<4,b.x<4)
        } }
        assertTrue(RouteRules.path(s.level,0,0,setOf(Position(3,0),Position(4,0),Position(5,0)),4).isEmpty())
    }
    @Test fun `route progress survives a clear but inaccessible pending nodes relocate without a reward`() {
        val level=HouseRule.levelsWith(HouseRule.ROUTES).first()
        val cells=listOf(Position(0,0),Position(1,0),Position(2,0))
        val track=RouteRules.progress(RouteTrack(cells),setOf(cells.first()))
        val s=GameState(level=level,grid=grid(),routes=listOf(track))
        val result=RouteRules.reconcile(s,s.grid.keys-cells.toSet()).first()
        assertTrue(cells.first() in result.covered); assertEquals(3,result.cells.size)
        assertFalse(cells[1] in result.cells); assertFalse(RouteRules.complete(result))
        assertFalse(RouteRules.complete(RouteTrack(cells.take(2),cells.take(2).toSet())))
    }
    @Test fun `renewing a route cannot steal another live path and one remaining node can relocate`() {
        val level=HouseRule.levelsWith(HouseRule.ROUTES).first()
        val original=RouteRules.reconcile(GameState(level=level,grid=grid()),grid().keys)
        val second=original[1]
        repeat(20) { wave ->
            val result=RouteRules.reconcile(GameState(level=level,grid=grid(),routes=listOf(RouteTrack(wave=wave),second)),grid().keys)
            assertEquals(second,result[1]); assertTrue(result[0].cells.none { it in second.cells })
        }
        val kept=listOf(Position(0,0),Position(1,0),Position(2,0))
        val t=RouteTrack(kept,kept.take(2).toSet())
        val next=RouteRules.reconcile(GameState(level=level,grid=grid(),routes=listOf(t)),setOf(Position(3,0))).first()
        assertEquals(3,next.cells.size); assertEquals(kept.take(2).toSet(),next.covered); assertTrue(Position(3,0) in next.cells)
    }

    @Test fun `route save validates bounds and preserves temporary reward without inventory data`() {
        val level=HouseRule.levelsWith(HouseRule.ROUTES).first()
        val s=GameState(level=level,grid=grid(),routes=listOf(RouteTrack(listOf(Position(-1,0),Position(0,0)),setOf(Position(-1,0),Position(0,0)))),routeSkillEarned=true,routeFreeCompanionId="fuego")
        val restored=ReplacementMigration.restore(s)
        assertEquals("fuego",restored.routeFreeCompanionId); assertTrue(restored.routeSkillEarned)
        assertFalse(restored.routes.flatMap { it.cells }.contains(Position(-1,0)))
        val other=ReplacementMigration.restore(s.copy(level=HouseRule.levelsWith(HouseRule.GRAVITY).first()))
        assertTrue(other.routes.isEmpty()); assertFalse(other.routeSkillEarned); assertNull(other.routeFreeCompanionId)
    }
    @Test fun `campaign slots stay stable with three active pieces and one opening reserve`() {
        assertEquals(28,HouseRule.entries.size); assertEquals(10,HouseRule.RESERVE.ordinal); assertEquals(13,HouseRule.ROUTES.ordinal)
        assertEquals(listOf(30,41,91,102,152,163,213,224,263,274),HouseRule.levelsWith(HouseRule.RESERVE).map { it-7 })
        assertEquals(listOf(10,21,71,82,132,143,193,204,243,254),HouseRule.levelsWith(HouseRule.ROUTES).map { it-7 })
        HouseRule.levelsWith(HouseRule.RESERVE).forEach { level ->
            val s=GameState.initial(level); assertEquals(3,s.availablePieces.size); assertEquals(1,s.reservePieces.size)
            assertEquals(3,s.trayCapacity); assertEquals(4,(s.availablePieces+s.reservePieces).map { it.id }.distinct().size)
        }
        val reserve=HouseRule.levelsWith(HouseRule.RESERVE).first(); val routes=HouseRule.levelsWith(HouseRule.ROUTES).first()
        assertEquals("reserve",HouseRule.forLevel(reserve)?.capsule?.id)
        assertTrue(MechanicCapsule.atRoundStart(GameState.initial(routes)).contains(MechanicCapsule.ROUTES))
    }
}
