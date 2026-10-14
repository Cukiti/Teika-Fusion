package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Puntería automática del Cañón Floral (habilidades pasivas, 2026-07-25). */
class FloraAutoAimTest {

    private val GRID = GameState.GRID_SIZE

    @Test
    fun `el capullo se planta bajo la columna con mas madera`() {
        // Columna 6 con 5 bloques; el resto, uno suelto.
        val wood = (0 until 5).map { Position(6, it) } + listOf(Position(1, 7))
        val aim = FloraAutoAim.aim(wood, GRID)
        assertEquals(6.5f, aim.cocoonX, 1e-4f)
    }

    @Test
    fun `siempre dispara hacia arriba`() {
        val cases = listOf(
            listOf(Position(0, 0)),
            listOf(Position(4, GRID - 1)),                       // madera pegada al suelo
            (0 until GRID).map { Position(it, GRID - 1) }        // fila entera abajo
        )
        cases.forEach { wood ->
            assertTrue("$wood", FloraAutoAim.aim(wood, GRID).dirY < 0f)
        }
    }

    @Test
    fun `la direccion sale normalizada`() {
        val wood = listOf(Position(2, 2), Position(3, 2), Position(2, 3))
        val aim = FloraAutoAim.aim(wood, GRID)
        assertEquals(1f, kotlin.math.hypot(aim.dirX, aim.dirY), 1e-3f)
    }

    @Test
    fun `un tiro vertical se inclina hacia el lado con mas madera`() {
        // Todo el peso a la DERECHA de la columna más llena → el tiro debe irse a la derecha.
        val wood = (0 until 4).map { Position(2, it) } + (0 until 3).flatMap { y ->
            listOf(Position(5, y), Position(6, y), Position(7, y))
        }
        val aim = FloraAutoAim.aim(wood, GRID)
        assertTrue("debería inclinarse a la derecha: ${aim.dirX}", aim.dirX > 0f)
        assertTrue(kotlin.math.abs(aim.dirX) >= FloraAutoAim.MIN_TILT - 1e-3f)
    }

    @Test
    fun `nunca sale un tiro perfectamente vertical (desperdiciaria los rebotes)`() {
        // Una sola columna: el centro de masas cae justo sobre el capullo.
        val wood = (0 until 6).map { Position(3, it) }
        val aim = FloraAutoAim.aim(wood, GRID)
        assertTrue(kotlin.math.abs(aim.dirX) >= FloraAutoAim.MIN_TILT - 1e-3f)
    }

    @Test
    fun `sin madera dispara desde el centro y no revienta`() {
        val aim = FloraAutoAim.aim(emptyList(), GRID)
        assertEquals(GRID / 2f, aim.cocoonX, 1e-4f)
        assertTrue(aim.dirY < 0f)
        assertEquals(1f, kotlin.math.hypot(aim.dirX, aim.dirY), 1e-3f)
    }

    @Test
    fun `el capullo nunca sale del tablero`() {
        listOf(0, GRID - 1).forEach { col ->
            val wood = (0 until 5).map { Position(col, it) }
            val x = FloraAutoAim.aim(wood, GRID).cocoonX
            assertTrue("$col → $x", x > 0f && x < GRID)
        }
    }

    @Test
    fun `en empate de columnas gana la mas centrada`() {
        val wood = listOf(Position(0, 0), Position(4, 0))
        assertEquals(4.5f, FloraAutoAim.aim(wood, GRID).cocoonX, 1e-4f)
    }
}
