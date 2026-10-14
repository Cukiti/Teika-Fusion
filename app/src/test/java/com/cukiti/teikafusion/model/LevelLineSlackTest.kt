package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * **HOLGURA DE LÍNEA**: ningún nivel puede abrir con una fila o columna hecha (2026-08-26, pedido del
 * usuario).
 *
 * El censo que motivó la regla: **150 de los 293 niveles** abrían con al menos una línea completa,
 * **3,79 de media** y hasta **9**. Esas líneas se rompían solas con la primera ficha que el jugador
 * soltara en cualquier parte y le devolvían sus Teikas y sus ~8 estrellas cada una — o sea que
 * cuantos más bloques traía un nivel, más fácil era. Justo al revés de lo que promete la densidad.
 *
 * Esta prueba es la que impide que vuelva a pasar sin que nadie se entere.
 */
class LevelLineSlackTest {

    @Test
    fun `ningun nivel del mapa abre con una linea hecha ni a una celda de estarlo`() {
        for (level in 1..LevelMap.totalLevels) {
            val cells = LevelLayouts.get(level)?.first ?: continue
            val rows = IntArray(GameState.GRID_SIZE)
            val cols = IntArray(GameState.GRID_SIZE)
            cells.forEach { rows[it.y]++; cols[it.x]++ }
            val peor = maxOf(rows.max(), cols.max())
            assertTrue(
                "nivel $level abre con una línea de $peor celdas (máximo ${LevelOpening.MAX_LINE_FILL})",
                peor <= LevelOpening.MAX_LINE_FILL
            )
        }
    }

    @Test
    fun `el censo de lineas pasadas de rosca coincide con la regla`() {
        for (level in 1..LevelMap.totalLevels) {
            val cells = LevelLayouts.get(level)?.first ?: continue
            assertEquals(
                "nivel $level",
                0,
                LevelOpening.overfilledLines(cells.map { it.x to it.y })
            )
        }
    }

    @Test
    fun `abrir holgura solo QUITA celdas y conserva el orden`() {
        // Tablero entero: el caso extremo, dieciséis líneas pasadas de rosca a la vez.
        val lleno = (0 until 8).flatMap { y -> (0 until 8).map { x -> x to y } }
        val abierto = LevelOpening.ensureLineSlack(lleno, Random(7))

        assertTrue("no puede añadir celdas", abierto.all { it in lleno })
        assertEquals("el orden original se conserva", lleno.filter { it in abierto }, abierto)
        assertEquals(0, LevelOpening.overfilledLines(abierto))
        // Con el tope en 6 por línea, el techo real de relleno es 6×8 = 48 celdas.
        assertTrue("quedan ${abierto.size} celdas", abierto.size <= LevelOpening.MAX_LINE_FILL * 8)
    }

    @Test
    fun `una figura que ya tiene holgura no se toca`() {
        val figura = listOf(0 to 0, 1 to 0, 2 to 0, 0 to 1, 1 to 1)
        assertEquals(figura, LevelOpening.ensureLineSlack(figura, Random(1)))
    }

    @Test
    fun `el resultado es determinista para el mismo nivel`() {
        val figura = (0 until 8).flatMap { y -> (0 until 7).map { x -> x to y } }
        assertEquals(
            LevelOpening.ensureLineSlack(figura, Random(42)),
            LevelOpening.ensureLineSlack(figura, Random(42))
        )
    }
}
