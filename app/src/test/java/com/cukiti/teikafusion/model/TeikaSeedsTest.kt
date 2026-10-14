package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TeikaSeedsTest {

    private fun tray(n: Int) = List(n) { i ->
        Piece(shape = listOf(Position(0, 0), Position(1, 0), Position(0, 1)), colorType = i % 5)
    }

    private fun seeds(pieces: List<Piece>) = pieces.count { p -> p.teikaMask.any { it } }

    @Test
    fun `cada ficha de la bandeja lleva su bloque-Teika`() {
        for (level in 8..60) assertEquals("nivel $level", 3, seeds(TeikaSeeds.mark(tray(3), 0, level)))
    }

    @Test
    fun `repartiendo de tres en tres, todas lo llevan`() {
        for (level in listOf(20, 99, 250)) {
            var total = 0
            for (i in 0 until 20) total += seeds(TeikaSeeds.mark(tray(3), dealtBefore = i * 3, level = level))
            assertEquals("nivel $level", 60, total)
        }
    }

    @Test
    fun `repartiendo de una en una, todas lo llevan`() {
        for (level in listOf(20, 99, 250)) {
            var total = 0
            for (i in 0 until 30) total += seeds(TeikaSeeds.mark(tray(1), dealtBefore = i, level = level))
            assertEquals("nivel $level", 30, total)
        }
    }

    @Test
    fun `el bloque va en una sola casilla y sin pisar la estrella`() {
        val conEstrella = Piece(
            shape = listOf(Position(0, 0), Position(1, 0), Position(2, 0)),
            targetMask = listOf(null, TargetColor.BLUE, null),
        )
        val cell = TeikaSeeds.cellFor(conEstrella)
        assertTrue(cell >= 0)
        assertTrue("pisó la estrella", conEstrella.targetMask[cell] == null)
        val marcada = TeikaSeeds.mark(listOf(conEstrella, conEstrella, conEstrella), 0, 20)
            .first { it.teikaMask.any { m -> m } }
        assertEquals(1, marcada.teikaMask.count { it })
    }

    /** Las fichas de estrellas las llevan en TODAS sus casillas: antes se quedaban sin bloque. */
    @Test
    fun `una ficha toda de estrellas tambien trae bloque, que sustituye una estrella`() {
        val todaEstrellas = Piece(
            shape = listOf(Position(0, 0), Position(1, 0), Position(2, 0)),
            targetMask = List(3) { TargetColor.BLUE },
        )
        val marcada = TeikaSeeds.mark(listOf(todaEstrellas), 0, 20).single()
        val cell = marcada.teikaMask.indexOf(true)
        assertEquals("la casilla central", 1, cell)
        assertEquals(null, marcada.targetMask[cell])
        assertEquals("las otras estrellas se quedan", 2, marcada.targetMask.count { it != null })

        // Un monominó con estrella no pierde su única estrella.
        val mono = Piece(shape = listOf(Position(0, 0)), targetMask = listOf(TargetColor.BLUE))
        assertEquals(-1, TeikaSeeds.cellFor(mono))
    }

    @Test
    fun `las herramientas no llevan bloque, las fichas normales si`() {
        val iman = Piece(shape = listOf(Position(0, 0)), isMagnet = true)
        val normal = tray(3)
        for (level in 8..40) {
            val out = TeikaSeeds.mark(listOf(iman) + normal.drop(1), 0, level)
            assertTrue("nivel $level", out.first().teikaMask.none { it })
            assertTrue("nivel $level", out.drop(1).all { p -> p.teikaMask.count { it } == 1 })
        }
    }

    @Test
    fun `el tutorial no reparte bloques por su cuenta`() {
        for (level in 1..TutorialCampaign.LAST_LEVEL) assertEquals(0, seeds(TeikaSeeds.mark(tray(3), 0, level)))
    }

    /** 2026-09-26: el tamaño es el MENOR entre líneas y bloques-Teika rotos, con techo en T3. */
    @Test
    fun `hacen falta varias lineas Y varios bloques para un Teika grande`() {
        // Una sola línea o un solo bloque: siempre T1.
        assertEquals(TeikaType.TEIKA_1, TeikaSeeds.tierFor(1, 4))
        assertEquals(TeikaType.TEIKA_1, TeikaSeeds.tierFor(4, 1))
        // El menor de los dos manda.
        assertEquals(TeikaType.TEIKA_2, TeikaSeeds.tierFor(2, 3))
        assertEquals(TeikaType.TEIKA_2, TeikaSeeds.tierFor(3, 2))
        assertEquals(TeikaType.TEIKA_3, TeikaSeeds.tierFor(3, 3))
        // Nunca un T4 directo.
        assertEquals(TeikaType.TEIKA_3, TeikaSeeds.tierFor(4, 4))
        assertEquals(TeikaType.TEIKA_3, TeikaSeeds.tierFor(6, 6))
    }

    @Test
    fun `la leccion 2 ensena el bloque-Teika`() {
        val p = TutorialCampaign.spec(2)!!.piece!!
        assertEquals(1, p.teikaMask.count { it })
    }

    /** P-292: una doble línea con tres bloques suelta UN T2 y dos T1, no tres T2. */
    @Test
    fun `solo un bloque por pasada recibe el tier grande`() {
        assertEquals(listOf(TeikaType.TEIKA_2, TeikaType.TEIKA_1, TeikaType.TEIKA_1), TeikaSeeds.tiersForPass(2, 3))
        assertTrue(TeikaSeeds.tiersForPass(3, 0).isEmpty())
        assertEquals(listOf(TeikaType.TEIKA_1), TeikaSeeds.tiersForPass(4, 1))
    }

    /** P-361: en el Cristal la ficha grande lleva dos bloques-Teika; la pequeña y el resto del juego, uno. */
    @Test
    fun `la ficha grande del cristal lleva dos bloques`() {
        val grande = Piece(shape = (0 until 3).flatMap { x -> (0 until 2).map { y -> Position(x, y) } }, colorType = 0)
        val pequena = tray(1).first()
        val cristal = LevelMap.chapterStart[0] + 1
        val sueno = LevelMap.chapterStart[1] + 1
        assertEquals(2, TeikaSeeds.mark(listOf(grande), 0, cristal).first().teikaMask.count { it })
        assertEquals(1, TeikaSeeds.mark(listOf(pequena), 0, cristal).first().teikaMask.count { it })
        assertEquals(1, TeikaSeeds.mark(listOf(grande), 0, sueno).first().teikaMask.count { it })
    }
}
