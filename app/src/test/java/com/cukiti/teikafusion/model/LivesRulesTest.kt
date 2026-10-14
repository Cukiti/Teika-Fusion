package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **LA REGENERACIÓN DE VIDAS** (2026-09-03), extraída del ViewModel siguiendo el patrón de la casa.
 *
 * La aritmética estaba escrita dos veces palabra por palabra —al cargar y en el bucle de 30 s— y
 * con reloj de pared, que es la combinación que más se rompe sola. El caso que da nombre a la mitad
 * de estas pruebas ya ocurrió de verdad: sin la guarda `now >= nextRegenAt`, **reabrir la app
 * devolvía una vida gratis**.
 */
class LivesRulesTest {

    private val T0 = 1_700_000_000_000L   // instante base cualquiera

    @Test
    fun `sin temporizador no se regenera`() {
        val r = LivesRules.regenerate(lives = 3, nextRegenAt = 0L, now = T0)
        assertFalse(r.changed)
        assertEquals(3, r.lives)
        assertEquals(0L, r.nextRegenAt)
    }

    @Test
    fun `con las vidas llenas no se regenera aunque el instante haya pasado`() {
        val r = LivesRules.regenerate(lives = LivesRules.MAX, nextRegenAt = T0, now = T0 + 10 * LivesRules.REGEN_MS)
        assertFalse(r.changed)
        assertEquals(LivesRules.MAX, r.lives)
    }

    @Test
    fun `reabrir con el temporizador AUN corriendo no regala una vida`() {
        // EL BUG HISTÓRICO. Sin la guarda: (now - next) < 0 → /REGEN < 0 → coerceAtLeast(0) = 0 →
        // +1 = 1, y el jugador recuperaba la vida que acababa de perder solo con salir y entrar.
        val faltaUnMinuto = T0 + LivesRules.REGEN_MS - 60_000L
        val r = LivesRules.regenerate(lives = 2, nextRegenAt = T0 + LivesRules.REGEN_MS, now = faltaUnMinuto)
        assertFalse("no debe regenerar antes de tiempo", r.changed)
        assertEquals(2, r.lives)
        assertEquals(T0 + LivesRules.REGEN_MS, r.nextRegenAt)
    }

    @Test
    fun `justo al vencer devuelve exactamente una vida`() {
        val r = LivesRules.regenerate(lives = 2, nextRegenAt = T0, now = T0)
        assertTrue(r.changed)
        assertEquals(3, r.lives)
        // Y el siguiente vence un periodo después, no desde `now`: el reloj no se reinicia por mirar.
        assertEquals(T0 + LivesRules.REGEN_MS, r.nextRegenAt)
    }

    @Test
    fun `el tiempo ya cumplido NO se pierde al regenerar`() {
        // El instante siguiente se calcula desde `nextRegenAt`, **no desde `now`**. Aquí el jugador
        // abre la app 10 min después de que venciera una vida: se lleva esa vida y la próxima le
        // queda a 20 min, no a 30. Si se recalculara desde `now` perdería esos 10 minutos en cada
        // visita, y quien abre la app a menudo saldría castigado justo por hacerlo.
        val diezMin = 10 * 60 * 1000L
        val r = LivesRules.regenerate(lives = 1, nextRegenAt = T0, now = T0 + diezMin)
        assertTrue(r.changed)
        assertEquals(2, r.lives)
        assertEquals(T0 + LivesRules.REGEN_MS, r.nextRegenAt)
        assertEquals(
            "quedan 20 min para la siguiente, no 30",
            LivesRules.REGEN_MS - diezMin, r.nextRegenAt - (T0 + diezMin)
        )
    }

    @Test
    fun `hora y media devuelve las vidas de los periodos cumplidos`() {
        // 45 min desde el vencimiento con periodo de 30: vencieron DOS (la de T0 y la de T0+30).
        val r = LivesRules.regenerate(lives = 1, nextRegenAt = T0, now = T0 + 45 * 60 * 1000L)
        assertTrue(r.changed)
        assertEquals(3, r.lives)
        assertEquals(T0 + 2 * LivesRules.REGEN_MS, r.nextRegenAt)
    }

    @Test
    fun `varios periodos devuelven varias vidas`() {
        // 2 periodos y pico desde el vencimiento = la del vencimiento + 2 = 3 vidas.
        val r = LivesRules.regenerate(lives = 0, nextRegenAt = T0, now = T0 + 2 * LivesRules.REGEN_MS + 1)
        assertTrue(r.changed)
        assertEquals(3, r.lives)
        assertEquals(T0 + 3 * LivesRules.REGEN_MS, r.nextRegenAt)
    }

    @Test
    fun `al llenarse el temporizador se apaga`() {
        val r = LivesRules.regenerate(lives = 0, nextRegenAt = T0, now = T0 + 50 * LivesRules.REGEN_MS)
        assertTrue(r.changed)
        assertEquals(LivesRules.MAX, r.lives)
        assertEquals("con las vidas llenas no debe quedar temporizador vivo", 0L, r.nextRegenAt)
    }

    @Test
    fun `nunca se pasa del tope`() {
        for (lives in 0 until LivesRules.MAX) {
            for (periodos in 0..20) {
                val r = LivesRules.regenerate(lives, T0, T0 + periodos * LivesRules.REGEN_MS)
                assertTrue("$lives vidas + $periodos periodos se pasó del tope", r.lives <= LivesRules.MAX)
                assertTrue("las vidas nunca bajan al regenerar", r.lives >= lives)
            }
        }
    }

    @Test
    fun `un reloj disparatado no desborda ni rompe el tope`() {
        // Mover el reloj del móvil hacia delante SÍ adelanta la regeneración —es una economía local,
        // decisión asumida— pero no puede producir basura: ni Int desbordado ni vidas de más.
        val r = LivesRules.regenerate(lives = 0, nextRegenAt = T0, now = Long.MAX_VALUE / 2)
        assertEquals(LivesRules.MAX, r.lives)
        assertEquals(0L, r.nextRegenAt)
    }

    @Test
    fun `el reloj movido hacia atras no regenera`() {
        val r = LivesRules.regenerate(lives = 1, nextRegenAt = T0, now = T0 - 365L * 24 * 3600 * 1000)
        assertFalse(r.changed)
        assertEquals(1, r.lives)
    }

    @Test
    fun `aplicar dos veces seguidas el mismo instante es idempotente`() {
        // El bucle corre cada 30 s y la carga ocurre al arrancar: los dos pueden verse el mismo
        // milisegundo. La segunda pasada no debe añadir nada.
        val primera = LivesRules.regenerate(lives = 1, nextRegenAt = T0, now = T0 + 1)
        val segunda = LivesRules.regenerate(primera.lives, primera.nextRegenAt, now = T0 + 1)
        assertFalse(segunda.changed)
        assertEquals(primera.lives, segunda.lives)
        assertEquals(primera.nextRegenAt, segunda.nextRegenAt)
    }
}
