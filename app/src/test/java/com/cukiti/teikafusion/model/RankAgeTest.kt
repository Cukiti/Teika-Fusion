package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** P-437: días de calendario, no bloques de 24 h. */
class RankAgeTest {
    private val zone = ZoneId.of("Europe/Madrid")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `hoy ayer y hace n dias`() {
        val now = ms(2026, 10, 2, 0, 5)
        assertEquals("visto hoy", RankAge.label(ms(2026, 10, 2, 0, 1), now, zone))
        assertEquals("visto ayer", RankAge.label(ms(2026, 10, 1, 23, 50), now, zone))
        assertEquals("visto hace 5 días", RankAge.label(ms(2026, 9, 27, 12, 0), now, zone))
    }

    @Test
    fun `sin fecha no dice nada`() {
        assertEquals("", RankAge.label(0L, ms(2026, 10, 2, 0, 5), zone))
    }
}
