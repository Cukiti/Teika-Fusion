package com.cukiti.teikafusion.model

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **TODO NIVEL ABRE CON UNA BANDEJA QUE SE PUEDE COLOCAR ENTERA** (2026-09-26, bug).
 *
 * Con la madera de salida de P-309 once niveles del Glaciar en adelante abrían con una bandeja que no
 * cabía: el bot perdía el nivel 180 en la 2.ª colocación. La apertura se reparte ahora con la garantía
 * del premio de la bandeja (ver `GameState.initial`). Este test lo defiende en los 286 niveles, contando
 * lo que puede hacer el jugador: GIRAR las fichas y ROMPER las líneas que va completando.
 */
class OpeningTrayTest {

    private fun rotations(p: Piece): List<List<Position>> {
        val out = ArrayList<List<Position>>()
        var c = p
        repeat(4) { if (out.none { it.toSet() == c.shape.toSet() }) out += c.shape; c = c.rotate() }
        return out
    }

    /** ¿Hay algún orden, giro y sitio para colocar todas, rompiendo las líneas que se completen? */
    private fun placeable(pieces: List<Piece>, filled: Set<Position>): Boolean {
        if (pieces.isEmpty()) return true
        for (piece in pieces) for (shape in rotations(piece)) for (y in 0 until 8) for (x in 0 until 8) {
            val cells = shape.map { Position(x + it.x, y + it.y) }
            if (cells.any { it.x !in 0 until 8 || it.y !in 0 until 8 || it in filled }) continue
            val after = (filled + cells).toMutableSet()
            val rows = (0 until 8).filter { yy -> (0 until 8).all { Position(it, yy) in after } }
            val cols = (0 until 8).filter { xx -> (0 until 8).all { Position(xx, it) in after } }
            after.removeAll { it.y in rows || it.x in cols }
            if (placeable(pieces - piece, after)) return true
        }
        return false
    }

    @Test
    fun `todo nivel abre con una bandeja que se puede colocar entera`() {
        val malos = (LevelMap.introLevels + 1..LevelMap.totalLevels).filter { level ->
            val s = GameState.initial(level)
            val filled = s.grid.filterValues { it.isOccupied }.keys + s.unplaceableCells
            !placeable(s.availablePieces, filled)
        }
        assertTrue("Niveles que abren con una bandeja imposible: $malos", malos.isEmpty())
    }
}
