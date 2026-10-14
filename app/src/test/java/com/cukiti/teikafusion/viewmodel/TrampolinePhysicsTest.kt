package com.cukiti.teikafusion.viewmodel

import com.cukiti.teikafusion.model.TeikaType
import org.junit.Assert.*
import org.junit.Test

class TrampolinePhysicsTest {
    @Test fun `bounce reverses a falling body but never wakes ice or kinematic bodies`() {
        val world = TeikaPhysicsWorld()
        val normal = world.addBody(TeikaType.TEIKA_1,2f,2f,nowMs = 0L)
        world.step(.05f,0L)
        assertTrue(world.trampolineBounce(normal.id,4f,-10f))
        assertEquals(-10f,world.getBody(normal.id)!!.vy,.001f)
        assertFalse(world.trampolineBounce(normal.id,4f,-10f))
        val frozen = world.addBody(TeikaType.TEIKA_1,4f,2f,nowMs = 0L)
        world.setFrozenTurns(frozen.id,2)
        assertFalse(world.trampolineBounce(frozen.id,4f,-10f))
        assertEquals(2,world.getBody(frozen.id)!!.frozenTurns)
        val parked = world.addBody(TeikaType.TEIKA_1,6f,2f,nowMs = 0L)
        world.setKinematic(parked.id,true)
        assertFalse(world.trampolineBounce(parked.id,4f,-10f))
        assertTrue(world.getBody(parked.id)!!.kinematic)
    }
}
