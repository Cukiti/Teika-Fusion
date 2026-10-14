package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reglas del Parto Real de Reynaldo. Fija cuántos Teikas paren por nivel:
 * L1 = 1 (el de mayor tier), L2 = mitad (redondeo arriba), L3 = todos.
 */
class BirthDecreeRulesTest {

    @Test
    fun `sin elegibles nadie pare en ningun nivel`() {
        for (lvl in 1..3) assertEquals(0, BirthDecreeRules.birthCount(0, lvl))
    }

    @Test
    fun `L1 siempre pare exactamente uno si hay elegibles`() {
        assertEquals(1, BirthDecreeRules.birthCount(1, 1))
        assertEquals(1, BirthDecreeRules.birthCount(5, 1))
    }

    @Test
    fun `L2 pare la mitad redondeando hacia arriba`() {
        assertEquals(1, BirthDecreeRules.birthCount(1, 2))
        assertEquals(1, BirthDecreeRules.birthCount(2, 2))
        assertEquals(2, BirthDecreeRules.birthCount(3, 2))
        assertEquals(3, BirthDecreeRules.birthCount(5, 2))
    }

    @Test
    fun `L3 pare todos los elegibles`() {
        assertEquals(1, BirthDecreeRules.birthCount(1, 3))
        assertEquals(7, BirthDecreeRules.birthCount(7, 3))
    }

    @Test
    fun `nivel fuera de rango se recorta`() {
        assertEquals(1, BirthDecreeRules.birthCount(4, 0))   // trata como L1
        assertEquals(4, BirthDecreeRules.birthCount(4, 9))   // trata como L3
    }
}
