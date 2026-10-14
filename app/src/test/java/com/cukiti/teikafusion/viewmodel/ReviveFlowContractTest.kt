package com.cukiti.teikafusion.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ReviveFlowContractTest {
    private val source: String by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, VIEW_MODEL).isFile) dir = dir.parentFile
        File(requireNotNull(dir), VIEW_MODEL).readText()
    }

    @Test
    fun `el barrido de revive conserva y acredita estrellas objetivo`() {
        val block = source.substringAfter("private suspend fun autoReviveSweepBoard()")
            .substringBefore("private data class PreparedReviveRecovery")
        assertTrue(block.contains("targetColor = c.targetColor"))
        assertTrue(block.contains("GameEvent.TargetCollected(target, p"))
        assertTrue(block.contains("targetsCollected = collected"))
    }

    @Test
    fun `el rescate solo actua si la fisica es el bloqueo y no otorga progreso`() {
        val planner = source.substringAfter("private fun prepareReviveRecovery")
            .substringBefore("private suspend fun playReviveTeikaRescue")
        val choreography = source.substringAfter("private suspend fun playReviveTeikaRescue")
            .substringBefore("fun declineRevive")
        assertTrue(planner.contains("physicalBodiesAreTheBlocker"))
        assertTrue(planner.contains("hasAnyLegalPlacement"))
        assertTrue(planner.contains("teikaBlockedOverride = blocked"))
        assertFalse(choreography.contains("teikasCreatedInLevel"))
        assertFalse(choreography.contains("awardXp"))
        assertFalse(choreography.contains("score ="))
        // (2026-09-23) Revivir ya no devuelve turnos: se retiraron del juego.
    }

    private companion object {
        const val VIEW_MODEL =
            "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt"
    }
}
