package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fija el tope diario de anuncios de monedas (P-18). Lo importante no son los números —esos son
 * economía del usuario— sino que el cupo **no se pueda burlar**: ni encadenando anuncios, ni
 * esperando al enfriamiento cuando ya no quedan usos, ni al cambiar de día a medias.
 */
class RewardedAdQuotaTest {

    private val day0 = 1_800_000_000_000L      // un instante cualquiera
    private val oneDay = 24L * 60 * 60 * 1000

    @Test
    fun `de partida se puede ver un anuncio`() {
        val s = RewardedAdQuota.State()
        assertTrue(RewardedAdQuota.canWatch(s, day0))
        assertEquals(RewardedAdQuota.DAILY_LIMIT, RewardedAdQuota.remaining(s, day0))
    }

    @Test
    fun `el enfriamiento bloquea el encadenado aunque queden usos`() {
        val after = RewardedAdQuota.consume(RewardedAdQuota.State(), day0)
        assertFalse(RewardedAdQuota.canWatch(after, day0 + 1_000))
        assertTrue(RewardedAdQuota.remaining(after, day0) > 0)   // quedaban usos: frenó el cooldown
        assertTrue(RewardedAdQuota.canWatch(after, day0 + RewardedAdQuota.COOLDOWN_MS))
    }

    @Test
    fun `al agotar el cupo del dia no se puede ver mas`() {
        var s = RewardedAdQuota.State()
        var t = day0
        repeat(RewardedAdQuota.DAILY_LIMIT) {
            assertTrue("Debería quedar cupo en la vuelta $it", RewardedAdQuota.canWatch(s, t))
            s = RewardedAdQuota.consume(s, t)
            t += RewardedAdQuota.COOLDOWN_MS          // se respeta el enfriamiento
        }
        assertEquals(0, RewardedAdQuota.remaining(s, t))
        assertFalse("El cupo diario no se puede burlar esperando", RewardedAdQuota.canWatch(s, t))
    }

    @Test
    fun `al dia siguiente se recupera el cupo entero`() {
        var s = RewardedAdQuota.State()
        var t = day0
        repeat(RewardedAdQuota.DAILY_LIMIT) { s = RewardedAdQuota.consume(s, t); t += RewardedAdQuota.COOLDOWN_MS }
        val tomorrow = day0 + oneDay
        assertEquals(RewardedAdQuota.DAILY_LIMIT, RewardedAdQuota.remaining(s, tomorrow))
        assertTrue(RewardedAdQuota.canWatch(s, tomorrow))
    }

    @Test
    fun `consumir en un dia nuevo reinicia la cuenta, no la acumula`() {
        val s = RewardedAdQuota.State(day = RewardedAdQuota.dayKey(day0), watched = 4, lastAtMs = day0)
        val next = RewardedAdQuota.consume(s, day0 + oneDay)
        assertEquals(1, next.watched)
    }
}
