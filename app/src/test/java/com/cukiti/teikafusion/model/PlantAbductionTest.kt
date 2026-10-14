package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlantAbductionTest {

    @Test
    fun topAbductionReachesOutsideOnFinalTurn() {
        var abduction = PlantAbduction(
            teikaId = "t",
            teikaType = TeikaType.TEIKA_2,
            edge = PlantEdge.TOP,
            currentRoot = Position(3, 5),
            maxTurns = 6
        )

        repeat(5) {
            val next = abduction.nextRoot()
            abduction = abduction.copy(currentRoot = next, turnsElapsed = abduction.turnsElapsed + 1)
            assertFalse(abduction.isFullyOutside())
        }

        val finalRoot = abduction.nextRoot()
        abduction = abduction.copy(currentRoot = finalRoot, turnsElapsed = 6)
        assertTrue(abduction.isFullyOutside())
        assertEquals(Position(3, -2), finalRoot)
    }

    @Test
    fun rightAbductionMovesMonotonicallyTowardEdge() {
        var abduction = PlantAbduction(
            teikaId = "t",
            teikaType = TeikaType.TEIKA_1,
            edge = PlantEdge.RIGHT,
            currentRoot = Position(1, 4),
            maxTurns = 6
        )
        var previousX = abduction.currentRoot.x

        repeat(6) {
            val next = abduction.nextRoot()
            assertTrue(next.x > previousX)
            previousX = next.x
            abduction = abduction.copy(currentRoot = next, turnsElapsed = abduction.turnsElapsed + 1)
        }

        assertEquals(GameState.GRID_SIZE, abduction.currentRoot.x)
        assertTrue(abduction.isFullyOutside())
    }

    @Test
    fun footprintIncludesWholeTeikaOutsideAndInsideBoard() {
        val footprint = plantFootprint(Position(-1, 2), TeikaType.TEIKA_2)

        assertEquals(4, footprint.size)
        assertTrue(Position(-1, 2) in footprint)
        assertTrue(Position(0, 3) in footprint)
    }

    /**
     * **DÓNDE ESTÁ EL CUERPO RAPTADO** (2026-09-04). El centro del círculo lo calculan ahora DOS
     * caminos —el arrastre de cada turno y la restauración del guardado— y por eso vive en una sola
     * función. Si divergieran, retomar la partida devolvería el Teika pegado a una raíz que está en
     * otro sitio: la mecánica seguiría contando turnos mientras el jugador ve el cuerpo donde no es.
     */
    @Test
    fun `el centro del cuerpo es el del footprint, dentro y fuera del tablero`() {
        val abduction = PlantAbduction(
            teikaId = "t",
            teikaType = TeikaType.TEIKA_2,   // 2x2
            edge = PlantEdge.LEFT,
            currentRoot = Position(3, 4)
        )

        assertEquals(4f, abduction.bodyCenterX(), 0.001f)
        assertEquals(5f, abduction.bodyCenterY(), 0.001f)

        // Medio fuera por la izquierda: la raíz se lleva el cuerpo con ella, y el centro la sigue a
        // coordenadas negativas. Es justo el estado en el que un guardado puede pillar la mecánica.
        val outside = abduction.copy(currentRoot = Position(-1, 4))
        assertEquals(0f, outside.bodyCenterX(), 0.001f)
        assertEquals(5f, outside.bodyCenterY(), 0.001f)
    }
}
