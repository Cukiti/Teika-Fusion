package com.cukiti.teikafusion.model

import com.cukiti.teikafusion.viewmodel.TeikaBody
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TeikaColorRetirementTest {
    private val root: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "app/src/main/java/com/cukiti/teikafusion").isDirectory }
    }
    private fun source(path: String) =
        File(root, "app/src/main/java/com/cukiti/teikafusion/$path").readText()

    @Test fun oldColoredBodyFixtureRemainsReadable() {
        val old = Json.decodeFromString<TeikaBodySave>(
            """{"id":"old","type":"TEIKA_2","x":2.5,"y":5.5,"frozenTurns":2,"colorType":3}"""
        )
        assertEquals("old", old.id)
        assertEquals(TeikaType.TEIKA_2, old.type)
        assertEquals(2.5f, old.x)
        assertEquals(5.5f, old.y)
        assertEquals(2, old.frozenTurns)
        assertEquals(3, old.colorType) // Metadata only; never copied into a physical body.
        val uncolored = Json.decodeFromString<TeikaBodySave>(
            """{"id":"older","type":"TEIKA_1","x":1.5,"y":6.5}"""
        )
        assertEquals(-1, uncolored.colorType)
    }

    @Test fun physicalBodiesCannotCarryLegacyColor() {
        assertFalse(TeikaBody::class.java.declaredFields.any { it.name == "colorType" })
        assertEquals(-1, TeikaBodySave("new", TeikaType.TEIKA_1, 2f, 3f).colorType)
    }

    @Test fun fusionKeepsNormalScoreAndPhysicalEffectsWithoutColorRewards() {
        val vm = source("viewmodel/GameViewModel.kt")
        val fusion = vm.substringAfter("private suspend fun processContactFusion")
            .substringBefore("private fun withTeikaBodySaves")
        assertTrue("if (b1.type != b2.type) return@withLock" in fusion)
        assertTrue("val fusionBase = b1.type.level * 100" in fusion)
        assertTrue("crushWoodUnderCircle(" in fusion)
        assertTrue("physicsWorld.addFusionBody(" in fusion)
        assertTrue("GameEvent.FusionBurst(" in fusion)
        assertFalse("sameColorFusion" in fusion)
        assertFalse("pendingColorWave" in fusion)
        assertFalse("ColorTeikaRules" in vm)
        assertFalse("colorType = it.colorType" in vm)
    }

    @Test fun retiredVisualsAreGoneButRaycoAuraRemains() {
        val board = source("ui/GameBoard.kt")
        assertFalse("ColorHaloLayer" in board)
        assertFalse("ColorWaveVFX" in board)
        assertFalse("ColorFusionWave" in source("model/GameEvent.kt"))
        assertTrue("OverloadChargeGlow(" in board)
    }
}
