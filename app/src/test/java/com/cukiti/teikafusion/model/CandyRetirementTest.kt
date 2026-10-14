package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CandyRetirementTest {

    @Test
    fun `quien lo compro recupera lo que pago y vuelve al clasico`() {
        val o = CandyRetirement.apply(setOf("candy", "fall3d"), active = "candy", refunded = false)
        assertEquals(2_000, o.refund)
        assertNull(o.active)
        assertTrue(o.refunded)
    }

    @Test
    fun `la devolucion se paga una sola vez`() {
        val o = CandyRetirement.apply(setOf("candy"), active = null, refunded = true)
        assertEquals(0, o.refund)
        assertTrue(o.refunded)
    }

    @Test
    fun `quien nunca lo tuvo no cobra nada ni se le marca`() {
        val o = CandyRetirement.apply(setOf("fall3d"), active = "fall3d", refunded = false)
        assertEquals(0, o.refund)
        assertEquals("fall3d", o.active)
        assertFalse(o.refunded)
    }

    @Test
    fun `un caramelo equipado sin comprar se quita igual`() {
        // Un save manipulado o de una versión rota: equipado pero no en la lista. No se paga (no
        // hay compra que devolver), pero tampoco se deja un id que ya no existe puesto.
        val o = CandyRetirement.apply(emptySet(), active = "candy", refunded = false)
        assertEquals(0, o.refund)
        assertNull(o.active)
    }

    @Test
    fun `reinstalar con la marca de la nube no vuelve a pagar`() {
        // Arranque local vacío → la fusión de la nube trae el caramelo Y la marca: nada que pagar.
        val local = CandyRetirement.apply(emptySet(), active = null, refunded = false)
        assertEquals(0, local.refund)
        val afterCloud = CandyRetirement.apply(setOf("candy"), active = null, refunded = true)
        assertEquals(0, afterCloud.refund)
    }
}
