package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reglas del Energizado de Rayco: qué líneas perpendiculares rematar cuando un Teika energizado
 * participa en una línea completada.
 */
class EnergizedTeikaRulesTest {

    /** Celdas de un T2 (2×2) con esquina superior-izquierda en (x,y). */
    private fun t2(x: Int, y: Int): Set<Position> =
        setOf(Position(x, y), Position(x + 1, y), Position(x, y + 1), Position(x + 1, y + 1))

    @Test
    fun `sin celdas no participa`() {
        val d = EnergizedTeikaRules.dischargeFor(emptySet(), { true }, { true })
        assertFalse(d.participates)
    }

    @Test
    fun `no participa si ninguna de sus lineas esta completa`() {
        val d = EnergizedTeikaRules.dischargeFor(t2(6, 6), rowComplete = { false }, colComplete = { false })
        assertFalse(d.participates)
        assertTrue(d.targetRows.isEmpty() && d.targetCols.isEmpty())
    }

    @Test
    fun `T2 con su fila inferior completa remata la otra fila y sus dos columnas`() {
        // fila 7 completa; filas 6,7 y columnas 6,7 del T2. Targets = fila 6 + columnas 6 y 7.
        val d = EnergizedTeikaRules.dischargeFor(
            t2(6, 6),
            rowComplete = { it == 7 },
            colComplete = { false }
        )
        assertTrue(d.participates)
        assertEquals(listOf(6), d.targetRows)
        assertEquals(listOf(6, 7), d.targetCols)
    }

    @Test
    fun `no re-lista una linea perpendicular que ya estaba completa`() {
        // fila 7 y columna 6 completas → targets = fila 6 + columna 7 (las que faltan).
        val d = EnergizedTeikaRules.dischargeFor(
            t2(6, 6),
            rowComplete = { it == 7 },
            colComplete = { it == 6 }
        )
        assertTrue(d.participates)
        assertEquals(listOf(6), d.targetRows)
        assertEquals(listOf(7), d.targetCols)
    }
}
