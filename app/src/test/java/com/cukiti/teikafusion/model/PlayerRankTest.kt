package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fija la tabla de rangos de jugador y su contrato. Los umbrales son BALANCE (no se retocan sin
 * decisión del usuario) y la tabla alimenta dos superficies (perfil de Home y ranking global):
 * si alguien la desordena o le abre un hueco, estos tests lo cantan antes que el playtest.
 */
class PlayerRankTest {

    // ── Inventario canónico ──────────────────────────────────────────────────────

    @Test
    fun `hay exactamente 6 rangos, el canon del juego`() {
        assertEquals(6, PlayerRank.ALL.size)
    }

    @Test
    fun `la tabla es contigua y sin huecos, del 0 al infinito`() {
        assertEquals(0, PlayerRank.ALL.first().minXp)
        assertEquals(-1, PlayerRank.ALL.last().maxXp)
        PlayerRank.ALL.zipWithNext().forEach { (rank, siguiente) ->
            // El techo de un rango es EXACTAMENTE el suelo del siguiente: ni hueco ni solape.
            assertEquals("hueco entre ${rank.name} y ${siguiente.name}", rank.maxXp, siguiente.minXp)
        }
    }

    @Test
    fun `solo el ultimo rango no tiene techo`() {
        PlayerRank.ALL.dropLast(1).forEach { assertTrue(it.maxXp > it.minXp) }
    }

    // ── get: el umbral pertenece al rango que empieza ────────────────────────────

    @Test
    fun `en el umbral exacto ya eres del rango nuevo`() {
        assertEquals("Novato", PlayerRank.get(499).name)
        assertEquals("Explorador", PlayerRank.get(500).name)
        assertEquals("Leyenda Teika", PlayerRank.get(15_000).name)
    }

    @Test
    fun `xp cero o negativo cae en el primer rango sin explotar`() {
        assertEquals("Novato", PlayerRank.get(0).name)
        assertEquals("Novato", PlayerRank.get(-100).name)   // defensa: el XP nunca debería ser <0
    }

    // ── progressFraction / xpToNext / nextRank ───────────────────────────────────

    @Test
    fun `el progreso arranca en 0 al entrar al rango y roza 1 al filo del siguiente`() {
        assertEquals(0f, PlayerRank.progressFraction(500), 0.0001f)
        assertEquals(0.5f, PlayerRank.progressFraction(1_000), 0.0001f)   // mitad de 500..1500
        assertTrue(PlayerRank.progressFraction(1_499) < 1f)
    }

    @Test
    fun `el ultimo rango muestra la barra llena para siempre`() {
        assertEquals(1f, PlayerRank.progressFraction(15_000), 0f)
        assertEquals(1f, PlayerRank.progressFraction(1_000_000), 0f)
    }

    @Test
    fun `xpToNext cuenta lo que falta y se apaga en la cima`() {
        assertEquals(500, PlayerRank.xpToNext(0))
        assertEquals(1, PlayerRank.xpToNext(1_499))
        assertNull(PlayerRank.xpToNext(15_000))
    }

    @Test
    fun `nextRank recorre la escalera y devuelve null en la cima`() {
        assertEquals("Explorador", PlayerRank.nextRank(0)?.name)
        assertEquals("Leyenda Teika", PlayerRank.nextRank(7_000)?.name)
        assertNull(PlayerRank.nextRank(15_000))
    }

    // ── rankAfterXpGain (V-03) ───────────────────────────────────────────────────

    @Test
    fun `rankAfterXpGain null si no se cruza umbral`() {
        assertNull(PlayerRank.rankAfterXpGain(100, 499))
        assertNull(PlayerRank.rankAfterXpGain(500, 500))
        assertNull(PlayerRank.rankAfterXpGain(600, 500))
    }

    @Test
    fun `rankAfterXpGain devuelve el rango nuevo al cruzar`() {
        assertEquals("Explorador", PlayerRank.rankAfterXpGain(499, 500)?.name)
        assertEquals("Fusionador", PlayerRank.rankAfterXpGain(1_400, 1_500)?.name)
    }

    @Test
    fun `rankAfterXpGain salta varios rangos y devuelve el final`() {
        assertEquals("Experto", PlayerRank.rankAfterXpGain(0, 3_500)?.name)
    }

    /**
     * "Mejor cosecha" (it. 61): la estadística que llevaba desde siempre clavada en 0 porque nadie
     * la calculaba. Suma los nacimientos de la partida y SOLO sube.
     */
    @Test
    fun `bestHarvest suma los nacimientos de la partida`() {
        val created = mapOf(
            TeikaType.TEIKA_1 to 7,
            TeikaType.TEIKA_2 to 3,
            TeikaType.TEIKA_3 to 1,
            TeikaType.TEIKA_4 to 0
        )
        assertEquals(11, PlayerStats.bestHarvest(created, currentMax = 0))
    }

    @Test
    fun `bestHarvest nunca baja la marca anterior`() {
        val floja = mapOf(TeikaType.TEIKA_1 to 2)
        assertEquals(11, PlayerStats.bestHarvest(floja, currentMax = 11))
        assertEquals(11, PlayerStats.bestHarvest(emptyMap(), currentMax = 11))
    }

    @Test
    fun `bestHarvest ignora contadores corruptos`() {
        // Un negativo (save manipulado o migración rara) no debe restar cosecha.
        val raro = mapOf(TeikaType.TEIKA_1 to 5, TeikaType.TEIKA_2 to -3)
        assertEquals(5, PlayerStats.bestHarvest(raro, currentMax = 0))
    }
}
