package com.cukiti.teikafusion.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P-447: ningún nivel pinta TODOS sus bloques iniciales de un solo color (el «sólido temático»
 * dejaba el tablero plano; en Cerámica, todo verde). Y los colores siguen en la paleta (0–4).
 */
class LevelPaletteTest {
    @Test
    fun `ningun nivel sale de un solo color`() {
        for (level in 1..LevelMap.totalLevels) {
            val colors = (0 until 8).flatMap { y -> (0 until 8).map { x -> LevelPalette.colorTypeFor(level, x, y) } }
            assertTrue("Nivel $level fuera de paleta", colors.all { it in 0..4 })
            assertTrue("Nivel $level de un solo color", colors.toSet().size >= 2)
        }
    }
}
