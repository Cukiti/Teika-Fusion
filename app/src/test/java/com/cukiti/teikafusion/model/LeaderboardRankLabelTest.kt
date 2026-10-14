package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * El puesto fuera del top 50 se cuenta de verdad (antes todos salían «#51»). Las reglas solo dejan
 * contar hasta 100: más allá es «100+», y si el recuento falla el puesto es «—», nunca uno inventado.
 */
class LeaderboardRankLabelTest {
    @Test
    fun `los puestos se escriben sin inventar`() {
        assertEquals("7", LeaderboardEntry.rankLabel(7))
        assertEquals("83", LeaderboardEntry.rankLabel(83))
        assertEquals("100+", LeaderboardEntry.rankLabel(LeaderboardEntry.RANK_BEYOND))
        assertEquals("—", LeaderboardEntry.rankLabel(LeaderboardEntry.UNKNOWN_RANK))
        assertEquals("—", LeaderboardEntry.rankLabel(0))
    }
}
