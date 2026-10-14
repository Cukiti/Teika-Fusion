package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeikaPhysicsProfileTest {

    private val liveTypes = listOf(
        TeikaType.TEIKA_1,
        TeikaType.TEIKA_2,
        TeikaType.TEIKA_3,
        TeikaType.TEIKA_4
    )

    @Test
    fun `cada tier vivo responde menos a la inclinacion que el anterior`() {
        val responses = liveTypes.map { TeikaPhysicsProfiles.of(it).tiltResponse }
        responses.zipWithNext().forEach { (lighter, heavier) ->
            assertTrue("el tier pesado debe responder menos: $lighter -> $heavier", heavier < lighter)
        }
    }

    @Test
    fun `cada tier vivo rueda con mas resistencia que el anterior`() {
        val drag = liveTypes.map { TeikaPhysicsProfiles.of(it).rollingDrag }
        drag.zipWithNext().forEach { (lighter, heavier) ->
            assertTrue("el tier pesado debe tener mas resistencia: $lighter -> $heavier", heavier > lighter)
        }
    }

    @Test
    fun `cada tier vivo rebota menos que el anterior`() {
        val restitution = liveTypes.map { TeikaPhysicsProfiles.of(it).restitution }
        restitution.zipWithNext().forEach { (lighter, heavier) ->
            assertTrue("el tier pesado debe rebotar menos: $lighter -> $heavier", heavier < lighter)
        }
    }

    @Test
    fun `la onda de una fusion crece con el tier resultante`() {
        val push = liveTypes.map { TeikaPhysicsProfiles.of(it).fusionPushSpeed }
        push.zipWithNext().forEach { (lighter, heavier) ->
            assertTrue("el resultado mayor debe reclamar mas espacio: $lighter -> $heavier", heavier > lighter)
        }
    }

    @Test
    fun `T2 conserva el feel base del motor anterior`() {
        val t2 = TeikaPhysicsProfiles.of(TeikaType.TEIKA_2)
        assertEquals(1f, t2.tiltResponse, 0f)
        assertEquals(1f, t2.rollingDrag, 0f)
        assertEquals(0.22f, t2.restitution, 0f)
    }
}
