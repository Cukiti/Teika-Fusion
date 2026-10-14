package com.cukiti.teikafusion.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **LAS DOS RANURAS QUE MIRAN EL TABLERO** (P-204 y P-205, 2026-09-06) y **EL CATÁLOGO AMPLIADO**
 * (P-207).
 *
 * Lo que estos tests defienden por encima de todo es la línea que la familia de reglas de bandeja no
 * puede cruzar: **ni la ayuda ni el rescate pueden dejar al jugador sin jugada**. Las dos son
 * puntuación y objetivo de tamaño dentro del ranking, nunca filtros — y aquí se comprueba que siguen
 * siéndolo.
 */
class TrayAssistTest {

    private fun row(y: Int, skip: Set<Int> = emptySet()): List<Position> =
        (0 until GameState.GRID_SIZE).filterNot { it in skip }.map { Position(it, y) }

    private fun stacked(rowsFull: Int): Set<Position> = buildSet {
        for (y in (GameState.GRID_SIZE - rowsFull) until GameState.GRID_SIZE) {
            for (x in 0 until GameState.GRID_SIZE) add(Position(x, y))
        }
    }

    // ── P-205 · LA FICHA DE RESCATE ─────────────────────────────────────────────────────────────

    @Test
    fun `los huecos son los de las lineas a punto de cerrarse`() {
        val filled = (row(7, skip = setOf(3, 4)) + row(6, skip = setOf(1))).toSet()
        val gaps = TrayAssist.lineGaps(filled = filled, unusable = emptySet())
        assertEquals(setOf(Position(3, 7), Position(4, 7), Position(1, 6)), gaps)
    }

    @Test
    fun `una linea a la que le faltan tres celdas no cuenta`() {
        val filled = row(7, skip = setOf(2, 3, 4)).toSet()
        assertTrue(TrayAssist.lineGaps(filled = filled, unusable = emptySet()).isEmpty())
    }

    @Test
    fun `una linea ya completa no genera huecos`() {
        val filled = row(7).toSet()
        assertTrue(TrayAssist.lineGaps(filled = filled, unusable = emptySet()).isEmpty())
    }

    @Test
    fun `una linea cuyo hueco el jugador no puede ocupar se descarta entera`() {
        // Mandar la bandeja hacia un premio inalcanzable es peor que no ayudar: si el hueco lo pisa
        // un cuerpo de Teika o lo reserva una marca de color, ninguna ficha va a cerrar esa línea.
        val filled = row(7, skip = setOf(3)).toSet()
        assertTrue(
            TrayAssist.lineGaps(filled = filled, unusable = setOf(Position(3, 7))).isEmpty()
        )
    }

    @Test
    fun `las columnas cuentan igual que las filas`() {
        val filled = (0 until GameState.GRID_SIZE).filter { it != 5 }.map { Position(2, it) }.toSet()
        assertEquals(setOf(Position(2, 5)), TrayAssist.lineGaps(filled = filled, unusable = emptySet()))
    }

    @Test
    fun `la ayuda hace que la bandeja apunte a los huecos mucho mas a menudo`() {
        val casi = buildSet {
            addAll(row(7, skip = setOf(3, 4)))
            addAll(row(6, skip = setOf(1)))
        }
        val gaps = TrayAssist.lineGaps(filled = casi, unusable = emptySet())
        assertTrue(gaps.isNotEmpty())
        var conAyuda = 0
        var sinAyuda = 0
        repeat(120) { seed ->
            fun apunta(plan: List<PieceTrayPlanner.PlannedShape>) =
                plan.any { item -> item.reservedCells.any { it in gaps } }
            conAyuda += if (apunta(
                    PieceTrayPlanner.plan(
                        boardSize = GameState.GRID_SIZE, blockedCells = casi, count = 3, level = 40,
                        random = Random(seed.toLong()), helpfulCells = gaps,
                    )
                )
            ) 1 else 0
            sinAyuda += if (apunta(
                    PieceTrayPlanner.plan(
                        boardSize = GameState.GRID_SIZE, blockedCells = casi, count = 3, level = 40,
                        random = Random(seed.toLong()),
                    )
                )
            ) 1 else 0
        }
        assertTrue(
            "La ayuda no cambia nada: con $conAyuda de 120, sin ella $sinAyuda",
            conAyuda > sinAyuda * 2
        )
    }

    @Test
    fun `la ayuda reparte orientaciones que caben`() {
        val casi = buildSet {
            addAll(row(7, skip = setOf(3, 4)))
            addAll(row(6, skip = setOf(1)))
            addAll(row(5, skip = setOf(0, 7)))
        }
        val gaps = TrayAssist.lineGaps(filled = casi, unusable = emptySet())
        repeat(60) { seed ->
            val plan = PieceTrayPlanner.plan(
                boardSize = GameState.GRID_SIZE, blockedCells = casi, count = 3, level = 40,
                random = Random(seed.toLong()), helpfulCells = gaps,
            )
            assertEquals(3, plan.size)
            assertTrue("seed=$seed", PieceTrayPlanner.reservationsAreValid(plan, casi))
        }
    }

    // ── EL REPARTO DE LAS DOS RANURAS ───────────────────────────────────────────────────────────

    @Test
    fun `el tramo grande ya no tiene un solo representante por tamaño`() {
        fun canonical(shape: List<Position>): String {
            fun normalize(s: List<Position>): List<Position> {
                val minX = s.minOf { it.x }
                val minY = s.minOf { it.y }
                return s.map { Position(it.x - minX, it.y - minY) }
                    .sortedWith(compareBy({ it.y }, { it.x }))
            }
            var current = normalize(shape)
            val keys = ArrayList<String>(4)
            repeat(4) {
                keys += current.joinToString(";") { "${it.x},${it.y}" }
                current = normalize(current.map { Position(-it.y, it.x) })
            }
            return keys.min()
        }
        val porTamano = Piece.SHAPES.map(::canonical).distinct()
            .groupingBy { key -> key.split(";").size }
            .eachCount()
        // Antes de P-207: 5 celdas → 5 formas, 6 → 1, 7 → NINGUNA, 8 → 1.
        assertTrue("Formas de 5 celdas: ${porTamano[5]}", (porTamano[5] ?: 0) >= 8)
        assertTrue("Formas de 6 celdas: ${porTamano[6]}", (porTamano[6] ?: 0) >= 2)
        assertTrue("No hay ninguna ficha de 7 celdas", (porTamano[7] ?: 0) >= 2)
    }

    @Test
    fun `el tramo grande tiene las tres familias de forma`() {
        // Los sabores de bandeja ([LevelVariety.trayAllows]) parten el catálogo en tres por
        // exclusión: barras, bloques macizos y «con recovecos» (todo lo demás). Si el tramo grande
        // se quedara sin una de las tres, el sabor correspondiente perdería sus fichas grandes y
        // repartiría solo pieza pequeña — que es el vicio del que salió P-207.
        val nuevas = Piece.SHAPES.filter { it.size in 5..7 }
        fun familia(shape: List<Position>): String {
            val w = shape.maxOf { it.x } + 1
            val h = shape.maxOf { it.y } + 1
            return when {
                w == 1 || h == 1 -> "barra"
                shape.size == w * h -> "macizo"
                else -> "recovecos"
            }
        }
        val porFamilia = nuevas.groupingBy(::familia).eachCount()
        assertTrue("Sin barras grandes: $porFamilia", (porFamilia["barra"] ?: 0) >= 1)
        assertTrue("Sin macizos grandes: $porFamilia", (porFamilia["macizo"] ?: 0) >= 1)
        // Antes de P-207 había 4 con recovecos entre 5 y 7 celdas, y ninguna de 7.
        assertTrue("Pocas formas grandes con recovecos: $porFamilia",
            (porFamilia["recovecos"] ?: 0) >= 8)
        nuevas.forEach { shape ->
            assertTrue(
                "Una forma de ${shape.size} celdas no cabe en el tablero vacío",
                PieceTrayPlanner.shapeFitsAnywhere(
                    shape, GameState.GRID_SIZE, GameState.GRID_SIZE, emptySet()
                )
            )
        }
    }
}
