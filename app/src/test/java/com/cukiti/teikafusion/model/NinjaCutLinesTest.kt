package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El corte del Dojo: con el tablero partido cada fila se completa por MITADES (vuelto el
 * 2026-09-24, P-254); sin corte, la fila entera como siempre.
 */
class NinjaCutLinesTest {

    @Test
    fun `con el tablero partido cada mitad de fila se completa por su cuenta`() {
        assertEquals(listOf(0 until 4, 4 until 8), NinjaCutLines.rowSpans(cutActive = true, width = 8))
    }

    @Test
    fun `sin corte la fila es entera`() {
        assertEquals(listOf(0 until 8), NinjaCutLines.rowSpans(cutActive = false, width = 8))
    }

    /** Nadie vuelve a validar filas por mitades a mano: la regla vive en un solo sitio. */
    @Test
    fun `el motor no parte filas por su cuenta`() {
        val vm = java.io.File("src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt").readText()
        assertTrue(
            "GameViewModel vuelve a construir tramos de media fila; usa NinjaCutLines.rowSpans",
            !vm.contains("NINJA_CUT_COL until")
        )
    }

    /** 2026-09-28: con el corte, la media fila solo se rompe con un Teika dentro. */
    @Test
    fun `con el tablero partido la media fila pide un Teika`() {
        val mitad = (0 until 4).map { Position(it, 5) }
        assertTrue(!NinjaCutLines.halfRowAllowed(cutActive = true, cells = mitad, teikaCells = emptySet()))
        assertTrue(!NinjaCutLines.halfRowAllowed(cutActive = true, cells = mitad, teikaCells = setOf(Position(5, 5))))
        assertTrue(NinjaCutLines.halfRowAllowed(cutActive = true, cells = mitad, teikaCells = setOf(Position(2, 5))))
        // Sin corte, la fila entera no pide nada.
        assertTrue(NinjaCutLines.halfRowAllowed(cutActive = false, cells = (0 until 8).map { Position(it, 5) }, teikaCells = emptySet()))
    }

    /** Las cuatro lecturas de «qué fila se completa» aplican la misma regla (dos copias de un dibujo). */
    @Test
    fun `resolucion vista previa pureza y tinte piden el Teika`() {
        val vm = java.io.File("src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt").readText()
        assertEquals(4, Regex("""NinjaCutLines\.halfRowAllowed\(""").findAll(vm).count())
    }
}
