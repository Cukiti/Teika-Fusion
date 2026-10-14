package com.cukiti.teikafusion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regresión del **bucle de escritura infinito** de `player_stats` (2026-08-01).
 *
 * `unlock_companions` es un registro permanente de lo que el jugador posee, no un comando de un solo
 * uso: nunca se resetea. Cuando la condición de emisión lo trataba como comando, cada snapshot del
 * documento generaba una emisión, el colector respondía con un push de espejo (`updated_at =
 * serverTimestamp()`), el documento cambiaba y el listener volvía a disparar. Resultado: una
 * escritura cada ~2,6 s mientras la app estuviera abierta, **11.717 mutaciones encoladas** al mismo
 * documento y, como Firestore despacha lecturas y escrituras por la misma cola, el ranking global y
 * `cloud_saves` expiraban sin llegar siquiera a la red — la app decía «sin conexión» estando online.
 */
class PlayerOverrideLoopTest {

    @Test
    fun `una lista de desbloqueos ya entregada no genera comando`() {
        val override = PlayerOverrideManager.pendingOverride(
            setCoins = 0,
            setLevel = 0,
            allUnlocks = listOf("fuego", "ninja"),
            deliveredUnlocks = setOf("fuego", "ninja"),
            forceCompanion = null,
            setCrystals = emptyMap()
        )
        assertNull("un registro permanente no es un comando: aquí nacía el bucle", override)
    }

    @Test
    fun `el primer snapshot entrega los desbloqueos pendientes`() {
        val override = PlayerOverrideManager.pendingOverride(
            setCoins = 0,
            setLevel = 0,
            allUnlocks = listOf("fuego", "ninja"),
            deliveredUnlocks = emptySet(),
            forceCompanion = null,
            setCrystals = emptyMap()
        )
        assertEquals(listOf("fuego", "ninja"), override?.unlockCompanions)
    }

    @Test
    fun `un compañero nuevo llega aunque los anteriores ya se entregaran`() {
        val override = PlayerOverrideManager.pendingOverride(
            setCoins = 0,
            setLevel = 0,
            allUnlocks = listOf("fuego", "ninja", "hielo"),
            deliveredUnlocks = setOf("fuego", "ninja"),
            forceCompanion = null,
            setCrystals = emptyMap()
        )
        assertEquals(listOf("hielo"), override?.unlockCompanions)
    }

    @Test
    fun `los comandos de un solo uso siguen emitiendo con desbloqueos ya entregados`() {
        val delivered = setOf("fuego")
        assertTrue(
            PlayerOverrideManager.pendingOverride(
                setCoins = 500, setLevel = 0, allUnlocks = listOf("fuego"),
                deliveredUnlocks = delivered, forceCompanion = null, setCrystals = emptyMap()
            ) != null
        )
        assertTrue(
            PlayerOverrideManager.pendingOverride(
                setCoins = 0, setLevel = 12, allUnlocks = listOf("fuego"),
                deliveredUnlocks = delivered, forceCompanion = null, setCrystals = emptyMap()
            ) != null
        )
        assertTrue(
            PlayerOverrideManager.pendingOverride(
                setCoins = 0, setLevel = 0, allUnlocks = listOf("fuego"),
                deliveredUnlocks = delivered, forceCompanion = "timoteo", setCrystals = emptyMap()
            ) != null
        )
        assertTrue(
            PlayerOverrideManager.pendingOverride(
                setCoins = 0, setLevel = 0, allUnlocks = listOf("fuego"),
                deliveredUnlocks = delivered, forceCompanion = null, setCrystals = mapOf(1 to 0)
            ) != null
        )
    }

    @Test
    fun `un documento sin comandos no emite nada`() {
        assertNull(
            PlayerOverrideManager.pendingOverride(
                setCoins = 0,
                setLevel = 0,
                allUnlocks = emptyList(),
                deliveredUnlocks = emptySet(),
                forceCompanion = null,
                setCrystals = emptyMap()
            )
        )
    }
}
