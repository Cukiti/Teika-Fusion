package com.cukiti.teikafusion.ui

import com.cukiti.teikafusion.model.NamedTeikaRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-282 · qué power-ups ofrece el selector del panel del nivel. */
class PowerUpPickerTest {

    @Test
    fun `sin companeros no hay nada que elegir`() {
        assertTrue(pickablePowerUps(emptySet()).isEmpty())
    }

    @Test
    fun `solo los reclamados y en el orden del viaje`() {
        val all = NamedTeikaRegistry.all.map { it.id }
        val claimed = setOf(all[5], all[0], all[3])
        // El orden no lo decide el conjunto: es el del registro, el orden en que se unen.
        assertEquals(listOf(all[0], all[3], all[5]), pickablePowerUps(claimed))
    }

    @Test
    fun `un id que no es compañero no se cuela`() {
        val first = NamedTeikaRegistry.all.first().id
        assertEquals(listOf(first), pickablePowerUps(setOf(first, "no-existe")))
    }

    @Test
    fun `cada power-up tiene su emblema`() {
        // Sin rama propia caería en el emblema de la Estrella: dos casillas iguales en el Inventario.
        val estrella = abilityIconRes("estrella")
        NamedTeikaRegistry.all.map { it.id }.filter { it != "estrella" }.forEach { id ->
            assertTrue("$id usa el emblema de otro", abilityIconRes(id) != estrella)
        }
    }
}
