package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas de la economía de CARGAS de habilidad (rework 2026-08-27, sustituye a `AbilityChargeTest`).
 *
 * Lo que se fija aquí es lo que impide los dos fallos que arruinan un consumible: **gastar sin
 * tener** (lanzar gratis) y **cobrar sin lanzar** (robar). El resto —precios, recompensas— se
 * comprueba para que un retoque de balance sea una decisión, no un accidente.
 */
class CompanionChargesTest {

    @Test
    fun `un compañero sin cargas no puede lanzar`() {
        val charges = CompanionCharges()
        assertEquals(0, charges.countOf("cristal"))
        assertFalse(charges.has("cristal"))
    }

    @Test
    fun `gastar sin tener devuelve null en vez de una cuenta negativa`() {
        // Es la garantía central: el call site NO puede lanzar la habilidad y "olvidarse" de cobrar,
        // porque no hay inventario resultante con el que continuar.
        assertNull(CompanionCharges().minus("cristal"))
        assertNull(CompanionCharges(mapOf("cristal" to 1)).minus("cristal", 2))
    }

    @Test
    fun `gastar descuenta exactamente una carga`() {
        val after = CompanionCharges(mapOf("gloton" to 3)).minus("gloton")
        assertEquals(2, after?.countOf("gloton"))
    }

    @Test
    fun `sumar topa en el máximo y nunca lo supera`() {
        val full = CompanionCharges().plus("rey", 99)
        assertEquals(CompanionCharges.MAX_PER_COMPANION, full.countOf("rey"))
        assertEquals(CompanionCharges.MAX_PER_COMPANION, full.plus("rey", 5).countOf("rey"))
    }

    @Test
    fun `un mapa sucio se lee dentro de rango`() {
        // Un guardado manipulado o corrupto no puede regalar lanzamientos infinitos ni romper la UI.
        val dirty = CompanionCharges(mapOf("estrella" to 5000, "hielo" to -7))
        assertEquals(CompanionCharges.MAX_PER_COMPANION, dirty.countOf("estrella"))
        assertEquals(0, dirty.countOf("hielo"))
        assertFalse(dirty.has("hielo"))
    }

    @Test
    fun `el total suma todos los compañeros`() {
        val charges = CompanionCharges(mapOf("cristal" to 2, "planta" to 3, "rey" to 0))
        assertEquals(5, charges.total)
    }

    @Test
    fun `solo el 3 estrellas paga carga`() {
        assertEquals(0, CompanionChargeEconomy.rewardForStars(0))
        assertEquals(0, CompanionChargeEconomy.rewardForStars(1))
        assertEquals(0, CompanionChargeEconomy.rewardForStars(2))
        assertEquals(1, CompanionChargeEconomy.rewardForStars(3))
    }

    @Test
    fun `el precio sube con la rareza`() {
        // Cada compañero del registro tiene precio, y la escala es estrictamente creciente: un poder
        // legendario nunca puede salir más barato que uno común.
        val comun = CompanionChargeEconomy.priceOf("cristal")          // TUTORIAL  → COMUN
        val raro = CompanionChargeEconomy.priceOf("planta")            // EARLY     → RARO
        val epico = CompanionChargeEconomy.priceOf("hielo")            // MID       → EPICO
        val legendario = CompanionChargeEconomy.priceOf("rey")         // CLIMAX    → LEGENDARIO
        assertTrue(comun < raro)
        assertTrue(raro < epico)
        assertTrue(epico < legendario)
    }

    @Test
    fun `todos los compañeros del registro tienen precio positivo`() {
        NamedTeikaRegistry.all.forEach { companion ->
            assertTrue(
                "El compañero ${companion.id} no tiene precio de carga",
                CompanionChargeEconomy.priceOf(companion.id) > 0
            )
        }
    }

    @Test
    fun `un compañero recién reclamado puede probar su habilidad el mismo día`() {
        // Desbloquear un personaje y no poder usarlo lo convierte en un retrato. La carga de
        // bienvenida es lo que hace que el desbloqueo se sienta como recibir un poder.
        assertTrue(CompanionChargeEconomy.WELCOME_CHARGES >= 1)
        val welcomed = CompanionCharges().plus("ninja", CompanionChargeEconomy.WELCOME_CHARGES)
        assertTrue(welcomed.has("ninja"))
    }
}
