package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Test

/** El premio de la bandeja (2026-09-26): qué anuncio merece cada jugada. */
class TrayRewardTest {

    @Test
    fun `una linea no premia, dos premian doble y tres o mas triple`() {
        assertEquals(TrayReward.NONE, TrayReward.forLines(0))
        assertEquals(TrayReward.NONE, TrayReward.forLines(1))
        assertEquals(TrayReward.DOUBLE, TrayReward.forLines(2))
        assertEquals(TrayReward.TRIPLE, TrayReward.forLines(3))
        assertEquals(TrayReward.TRIPLE, TrayReward.forLines(5))
    }

    @Test
    fun `solo cuentan las lineas que atraviesan la ficha colocada`() {
        // P-310: una doble línea en cascada que no toca la ficha no es premio de la ficha.
        val ficha = setOf(Position(2, 0), Position(3, 0))
        val fila0 = (0 until 8).map { Position(it, 0) }
        val fila5 = (0 until 8).map { Position(it, 5) }
        val col3 = (0 until 8).map { Position(3, it) }
        assertEquals(2, TrayReward.linesWithPiece(listOf(fila0, fila5, col3)) { it in ficha })
        assertEquals(0, TrayReward.linesWithPiece(listOf(fila5)) { it in ficha })
        assertEquals(0, TrayReward.linesWithPiece(emptyList()) { it in ficha })
    }
}
