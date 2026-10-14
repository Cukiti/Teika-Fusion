package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialCampaignTest {

    @Test
    fun `las dos lecciones de arrastre completan exactamente sus lineas objetivo`() {
        for (level in 1..2) {
            val spec = TutorialCampaign.spec(level)!!
            val piece = spec.piece!!
            val initial = spec.initialCells.map { Position(it.x, it.y) }.toSet()
            assertEquals(piece.shape.size, spec.gap.size)
            assertTrue("tutorial $level: el tablero ocupa el destino", initial.intersect(spec.gap).isEmpty())

            val completed = initial + spec.gap
            val fullRows = (0 until 8).count { y -> (0 until 8).all { x -> Position(x, y) in completed } }
            val fullCols = (0 until 8).count { x -> (0 until 8).all { y -> Position(x, y) in completed } }
            assertEquals("tutorial $level: líneas creadas", spec.requiredLines, fullRows + fullCols)
        }
    }

    @Test
    fun `tableros de colocacion y sus colores son simetricos en ambos ejes`() {
        for (level in 1..2) {
            val spec = TutorialCampaign.spec(level)!!
            val cells = spec.initialCells.map { Position(it.x, it.y) }.toSet()
            assertEquals(cells, cells.map { Position(7 - it.x, it.y) }.toSet())
            assertEquals(cells, cells.map { Position(it.x, 7 - it.y) }.toSet())
            cells.forEach { position ->
                val color = TutorialCampaign.colorTypeFor(level, position.x, position.y)
                assertEquals(color, TutorialCampaign.colorTypeFor(level, 7 - position.x, position.y))
                assertEquals(color, TutorialCampaign.colorTypeFor(level, position.x, 7 - position.y))
            }
        }
    }

    @Test
    fun `nivel uno usa ficha 2x2 en la cruz central`() {
        val spec = TutorialCampaign.spec(1)!!
        assertEquals(4, spec.piece!!.shape.size)
        assertEquals(2, spec.piece.width)
        assertEquals(2, spec.piece.height)
        assertEquals(Position(3, 3), spec.targetOrigin)
        assertEquals(4, spec.requiredLines)
        assertNull(spec.targetTeika)
    }

    @Test
    fun `la progresion termina en mega solo en la cuarta leccion`() {
        assertNull(TutorialCampaign.spec(1)!!.targetTeika)
        assertEquals(TeikaType.TEIKA_1, TutorialCampaign.spec(2)!!.targetTeika)
        assertEquals(TeikaType.TEIKA_2, TutorialCampaign.spec(3)!!.targetTeika)
        assertEquals(TeikaType.MEGA_TEIKA, TutorialCampaign.spec(4)!!.targetTeika)
        assertEquals(2, TutorialCampaign.spec(3)!!.initialTeikas.size)
        assertTrue(TutorialCampaign.spec(3)!!.initialTeikas.all { it.type == TeikaType.TEIKA_1 })
        assertEquals(2, TutorialCampaign.spec(4)!!.initialTeikas.size)
        assertTrue(TutorialCampaign.spec(4)!!.initialTeikas.all { it.type == TeikaType.TEIKA_2 })
        assertEquals(1, TutorialCampaign.spec(3)!!.tiltStages)
        assertEquals(3, TutorialCampaign.spec(4)!!.tiltStages)
        assertFalse(ChapterArc.requiresMega(1))
        assertFalse(ChapterArc.requiresMega(2))
        assertFalse(ChapterArc.requiresMega(3))
        assertTrue(ChapterArc.requiresMega(4))
    }

    @Test
    fun `la tercera leccion forma una escalera descendente sin completar lineas`() {
        val spec = TutorialCampaign.spec(3)!!
        val cells = spec.initialCells.map { Position(it.x, it.y) }.toSet()
        assertEquals(12, cells.size)
        for (step in 0 until 5) {
            val left = 6 - step
            val y = 2 + step
            assertTrue(Position(left, y) in cells)
            assertTrue(Position(left + 1, y) in cells)
        }
        assertTrue(Position(0, 7) in cells)
        assertTrue(Position(1, 7) in cells)
        assertTrue((0 until 8).none { y -> (0 until 8).all { x -> Position(x, y) in cells } })
        assertTrue((0 until 8).none { x -> (0 until 8).all { y -> Position(x, y) in cells } })
        assertEquals(-1, spec.initialTiltDirection)
    }

    @Test
    fun `la cuarta leccion declara la cadena final por etapas`() {
        assertEquals(TeikaType.TEIKA_3, TutorialCampaign.lessonFourExpectedResult(0))
        assertEquals(TeikaType.TEIKA_4, TutorialCampaign.lessonFourExpectedResult(1))
        assertEquals(TeikaType.MEGA_TEIKA, TutorialCampaign.lessonFourExpectedResult(2))
        assertNull(TutorialCampaign.lessonFourExpectedResult(3))
        assertEquals(TeikaType.TEIKA_3, TutorialCampaign.lessonFourPartnerAfter(TeikaType.TEIKA_3))
        assertEquals(TeikaType.TEIKA_4, TutorialCampaign.lessonFourPartnerAfter(TeikaType.TEIKA_4))
        assertNull(TutorialCampaign.lessonFourPartnerAfter(TeikaType.TEIKA_2))
    }

    @Test
    fun `estado inicial de tutorial no tiene bioma ni bandeja aleatoria`() {
        // Ninguna lección tiene bioma ni reparte bandeja al azar: eso no cambió con la ampliación a
        // siete. Lo que SÍ cambió es que dos lecciones enseñan el objetivo y los turnos, así que
        // esos dos los gobierna su spec y no una regla global de "el tutorial no tiene nada".
        for (level in TutorialCampaign.FIRST_LEVEL..TutorialCampaign.LAST_LEVEL) {
            val state = GameState.initial(level)
            val spec = TutorialCampaign.spec(level)!!
            assertEquals(BiomeType.NONE, state.biomePrimary)
            // Sin turnos desde el 2026-09-23: ninguna lección tiene presupuesto.
            assertEquals("tutorial $level", 0, state.movesTotal)
            val estrellas = spec.requiredStars
            if (estrellas != null) {
                assertEquals(estrellas, state.levelObjective!!.required)
                assertEquals(LevelGoal.STARS, state.levelObjective.goal)
            } else {
                assertTrue(state.levelObjective!!.required.isEmpty())
                assertEquals(LevelGoal.LINES, state.levelObjective.goal)
                assertEquals(spec.requiredLines, state.levelObjective.amount)
            }
            assertEquals(listOfNotNull(spec.piece), state.availablePieces)
            assertEquals(spec.targetTeika ?: TeikaType.TEIKA_1, state.targetTeika)
        }
    }

    @Test
    fun `el primer nivel tras el tutorial tiene objetivo y bandeja jugables, sin limite de turnos`() {
        val state = GameState.initial(TutorialCampaign.LAST_LEVEL + 1)

        assertFalse(TutorialCampaign.isTutorialLevel(state.level))
        assertEquals(0, state.movesTotal)
        assertEquals(3, state.availablePieces.size)
        assertTrue(state.availablePieces.all { it.shape.isNotEmpty() })
        assertTrue(state.levelObjective!!.required.isNotEmpty())
        assertTrue(ChapterArc.requiresMega(state.level))
    }

    @Test
    fun `solo se acepta la ficha guiada en su origen y las arenas no aceptan fichas`() {
        val spec = TutorialCampaign.spec(2)!!
        val piece = spec.piece!!
        assertTrue(TutorialCampaign.isCorrectPlacement(2, piece, 2, 3))
        assertFalse(TutorialCampaign.isCorrectPlacement(2, piece, 1, 3))
        assertFalse(TutorialCampaign.isCorrectPlacement(2, piece.copy(id = "otra"), 2, 3))
        // Una leccion SIN ficha guiada no dirige nada, asi que admite cualquier jugada legal: lo que
        // impide colocar en las de inclinacion es que su bandeja esta VACIA, no este guard. Devolver
        // `false` aqui era lo que dejaba la leccion 7 sin salida (grabacion `tutorialexample`).
        assertTrue(TutorialCampaign.isCorrectPlacement(3, piece, 0, 0))
        assertTrue(TutorialCampaign.spec(3)!!.piece == null)
        // Fuera del tutorial no hay ficha guiada que valga: vale cualquier colocación.
        assertTrue(
            TutorialCampaign.isCorrectPlacement(TutorialCampaign.LAST_LEVEL + 1, piece, 0, 0)
        )
    }

    @Test
    fun `quien ya paso del tutorial no lo repite`() {
        val ultima = TutorialCampaign.LAST_LEVEL
        assertFalse(TutorialCampaign.isAlreadyCompleted(false, ultima))
        assertTrue(TutorialCampaign.isAlreadyCompleted(false, ultima + 1))
        assertTrue(TutorialCampaign.isAlreadyCompleted(true, 1))
    }
}
