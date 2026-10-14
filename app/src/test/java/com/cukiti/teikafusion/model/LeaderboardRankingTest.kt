package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** P-436: empatados en nivel comparten puesto; el XP ordena dentro del empate; el orden es estable. */
class LeaderboardRankingTest {
    private fun e(uid: String, level: Int, xp: Int) =
        LeaderboardEntry(rank = 0, uid = uid, displayName = uid, level = level, teikas = 0, totalXp = xp)

    @Test
    fun `empatados comparten puesto y el siguiente salta`() {
        val r = LeaderboardRanking.rank(listOf(e("a", 50, 10), e("b", 60, 0), e("c", 50, 90), e("d", 40, 0)))
        assertEquals(listOf("b", "c", "a", "d"), r.map { it.uid })
        assertEquals(listOf(1, 2, 2, 4), r.map { it.rank })
    }

    @Test
    fun `el orden no baila entre visitas`() {
        val xs = listOf(e("z", 30, 5), e("y", 30, 5), e("x", 30, 5))
        assertEquals(LeaderboardRanking.rank(xs).map { it.uid }, LeaderboardRanking.rank(xs.reversed()).map { it.uid })
        assertEquals(listOf(1, 1, 1), LeaderboardRanking.rank(xs).map { it.rank })
    }
}
