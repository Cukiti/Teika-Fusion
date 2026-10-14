package com.cukiti.teikafusion.viewmodel

import com.cukiti.teikafusion.model.*
import org.junit.Assert.*
import org.junit.Test

class PinballPhysicsTest {
    @Test fun `all four free tiers retain their identity and physical radius`() {
        TeikaType.entries.filter { it.level in 1..4 }.forEach { type ->
            val world=TeikaPhysicsWorld(); val body=world.addBody(type,4f,4f)
            assertTrue(world.launchPinball(PinballFlight(body.id,10f,0f)))
            world.step(.05f,1000L)
            val moved=world.getBody(body.id)!!
            assertEquals(type,moved.type); assertEquals(type.level*.5f,moved.radius,0f)
            assertTrue(moved.x>4f)
        }
    }
    @Test fun `real terrain hit reflects and breaks only the eligible cell once`() {
        val world=TeikaPhysicsWorld(); val cell=Position(4,3)
        val body=world.addBody(TeikaType.TEIKA_1,2.5f,3.5f)
        world.setTerrain(setOf(cell)); world.setPinballBreakable(setOf(cell))
        assertTrue(world.launchPinball(PinballFlight(body.id,10f,0f)))
        repeat(15) { world.step(.016f,1000L) }
        assertEquals(listOf(cell),world.drainPinballImpacts())
        assertTrue(world.drainPinballImpacts().isEmpty())
        assertTrue(world.getBody(body.id)!!.vx<0f)
        assertEquals(5,world.pinballSnapshot()!!.bouncesLeft)
    }
    @Test fun `protected terrain reflects without destructive impact`() {
        val world=TeikaPhysicsWorld(); val cell=Position(4,3)
        val body=world.addBody(TeikaType.TEIKA_1,2.5f,3.5f)
        world.setTerrain(setOf(cell)); world.setPinballBreakable(emptySet())
        world.launchPinball(PinballFlight(body.id,10f,0f))
        repeat(15) { world.step(.016f,1000L) }
        assertTrue(world.drainPinballImpacts().isEmpty()); assertTrue(world.getBody(body.id)!!.vx<0f)
    }
    @Test fun `time and rebound budget both end without destroying the body`() {
        val timed=TeikaPhysicsWorld(); val a=timed.addBody(TeikaType.TEIKA_1,2f,2f)
        timed.launchPinball(PinballFlight(a.id,10f,0f,secondsLeft=.08f))
        repeat(10) { timed.step(.016f,1000L) }
        assertNull(timed.pinballSnapshot()); assertNotNull(timed.getBody(a.id))
        val bounded=TeikaPhysicsWorld(); val b=bounded.addBody(TeikaType.TEIKA_1,7.4f,2f)
        bounded.launchPinball(PinballFlight(b.id,10f,0f,bouncesLeft=1))
        bounded.step(.05f,1000L)
        assertNull(bounded.pinballSnapshot()); assertNotNull(bounded.getBody(b.id))
        assertEquals(1,bounded.drainPinballContacts().size)
    }
    @Test fun `contact ends projectile and reports normal equal tier fusion`() {
        val world=TeikaPhysicsWorld(); val a=world.addBody(TeikaType.TEIKA_1,2f,3f,nowMs=0L)
        val b=world.addBody(TeikaType.TEIKA_1,4f,3f,nowMs=0L)
        world.launchPinball(PinballFlight(a.id,10f,0f))
        val contacts=mutableSetOf<Pair<String,String>>()
        repeat(15) { contacts.addAll(world.step(.016f,1000L)) }
        assertNull(world.pinballSnapshot()); assertTrue(contacts.any { setOf(it.first,it.second)==setOf(a.id,b.id) })
    }
    @Test fun `ice capture and an existing ballistic projectile reject launch`() {
        val world=TeikaPhysicsWorld(); val a=world.addBody(TeikaType.TEIKA_2,3f,3f)
        world.setFrozenTurns(a.id,2); assertFalse(world.launchPinball(PinballFlight(a.id,10f,0f)))
        world.setFrozenTurns(a.id,0); world.setKinematic(a.id,true)
        assertFalse(world.launchPinball(PinballFlight(a.id,10f,0f)))
        world.setKinematic(a.id,false); world.setBallistic(a.id,3,10f)
        assertFalse(world.launchPinball(PinballFlight(a.id,10f,0f)))
    }
    @Test fun `restore resumes velocity timer and budget without repeating impacts`() {
        val world=TeikaPhysicsWorld(); val a=world.addBody(TeikaType.TEIKA_1,2f,3f)
        world.launchPinball(PinballFlight(a.id,10f,0f)); world.step(.05f,1000L)
        val flight=world.pinballSnapshot()!!; val bodies=world.snapshot()
        val restored=TeikaPhysicsWorld(); restored.load(bodies.map { it.copy(vx=0f,vy=0f) })
        assertTrue(restored.launchPinball(flight)); assertEquals(flight,restored.pinballSnapshot())
        assertTrue(restored.drainPinballImpacts().isEmpty())
    }
}
