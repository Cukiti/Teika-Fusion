package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * **P-445 · LA VISTA PREVIA DICE LO QUE HARÁ EL MOTOR** (2026-10-01).
 *
 * La vista previa mira el tablero con la ficha SUPERPUESTA (aún no está en la rejilla); el motor,
 * con la ficha ya ESTAMPADA. En miles de tableros aleatorios —bobinas, cristal, testarudos, hielo,
 * escarcha, madera oculta, Teikas comodín, casillas fijas, corte ninja— las dos lecturas tienen que
 * dar exactamente las mismas líneas (las que tocan la ficha). Y un contrato de fuente: el motor y la
 * vista previa llaman a [LineDetector.detect] y nadie más vuelve a escribir el barrido a mano.
 */
class LineDetectorTest {
    private val w = 8
    private val h = 8

    private data class Board(
        val grid: Map<Position, CellContent>,
        val teika: Set<Position>,
        val fixed: Set<Position>,
        val frozen: Set<Position>,
        val frost: Set<Position>,
        val hidden: Set<Position>,
        val ninja: Boolean,
        val stamped: Set<Position>,
    )

    private fun randomBoard(rnd: Random): Board {
        val grid = HashMap<Position, CellContent>()
        val teika = HashSet<Position>()
        val fixed = HashSet<Position>()
        val woodDensity = 0.55f + rnd.nextFloat() * 0.4f   // tableros llenos: más líneas que mirar
        for (y in 0 until h) for (x in 0 until w) {
            val p = Position(x, y)
            val r = rnd.nextFloat()
            when {
                r < woodDensity -> grid[p] = CellContent.Wood(
                    colorType = rnd.nextInt(5),
                    coil = if (rnd.nextFloat() < 0.04f) 1 + rnd.nextInt(4) else 0,
                    armor = if (rnd.nextFloat() < 0.05f) 1 else 0,
                    stubborn = rnd.nextFloat() < 0.06f,
                )
                r < woodDensity + 0.05f -> { grid[p] = CellContent.Empty; teika += p }
                r < woodDensity + 0.08f -> { grid[p] = CellContent.Empty; fixed += p }
                else -> grid[p] = CellContent.Empty
            }
        }
        val wood = grid.filterValues { it is CellContent.Wood }.keys.toList()
        fun someWood(chance: Float) = wood.filterTo(HashSet()) { rnd.nextFloat() < chance }
        val frozen = someWood(0.04f)
        val frost = someWood(0.04f)
        val hidden = someWood(0.03f)
        // La ficha: 1–5 casillas libres (sin madera, Teika ni casilla fija) de una zona pequeña.
        val free = grid.filter { (p, c) -> c is CellContent.Empty && p !in teika && p !in fixed }.keys.toList()
        val stamped = if (free.isEmpty()) emptySet() else {
            val seed = free[rnd.nextInt(free.size)]
            free.filter { kotlin.math.abs(it.x - seed.x) <= 2 && kotlin.math.abs(it.y - seed.y) <= 2 }
                .shuffled(rnd).take(1 + rnd.nextInt(5)).toSet() + seed
        }
        return Board(grid, teika, fixed, frozen, frost, hidden, rnd.nextBoolean(), stamped)
    }

    private fun halfRow(b: Board): (List<Position>) -> Boolean =
        { cells -> NinjaCutLines.halfRowAllowed(b.ninja, cells, b.teika) }

    /** Lo que avisa la vista previa: ficha superpuesta, solo líneas que la tocan. */
    private fun preview(b: Board) = LineDetector.detect(
        w, h,
        filled = LineDetector.filled(b.grid, b.teika, b.fixed, b.stamped),
        clearable = LineDetector.clearable(b.grid, b.frozen, b.frost, b.hidden, b.stamped),
        lineBlocked = LineDetector.lineBlocked(b.grid, b.hidden),
        rowSpans = NinjaCutLines.rowSpans(b.ninja, w),
        halfRowAllowed = halfRow(b),
        mustTouch = b.stamped,
    )

    /** Lo que rompe el motor tras estampar, filtrado a las líneas que tocan la ficha. */
    private fun engine(b: Board): LineDetector.Lines {
        val g = b.grid.toMutableMap()
        b.stamped.forEach { g[it] = CellContent.Wood(colorType = 1) }
        val all = LineDetector.detect(
            w, h,
            filled = LineDetector.filled(g, b.teika, b.fixed),
            clearable = { p ->
                (g[p] as? CellContent.Wood)?.let { LineDetector.woodClearable(it, p, b.frozen, b.frost, b.hidden) } ?: false
            },
            lineBlocked = LineDetector.lineBlocked(g, b.hidden),
            rowSpans = NinjaCutLines.rowSpans(b.ninja, w),
            halfRowAllowed = halfRow(b),
        )
        return LineDetector.Lines(
            rows = all.rows.filter { (y, span) -> span.any { Position(it, y) in b.stamped } },
            cols = all.cols.filter { x -> (0 until h).any { Position(x, it) in b.stamped } },
        )
    }

    @Test
    fun `la vista previa y el motor ven las mismas lineas en miles de tableros`() {
        val rnd = Random(20261001)
        var withLines = 0
        repeat(6000) { i ->
            val b = randomBoard(rnd)
            if (b.stamped.isEmpty()) return@repeat
            val p = preview(b)
            val e = engine(b)
            assertEquals("Tablero #$i: la vista previa promete otra cosa que el motor", e, p)
            if (p.rows.isNotEmpty() || p.cols.isNotEmpty()) withLines++
        }
        // Si el generador nunca cerrara líneas, el test pasaría sin probar nada.
        assertTrue("Solo $withLines tableros con líneas: el muestreo no ejercita el barrido", withLines > 300)
    }

    @Test
    fun `una linea de puros testarudos no se da por completa`() {
        val grid = (0 until w).associate { x -> Position(x, 0) to CellContent.Wood(colorType = 0, stubborn = true) }
        val lines = LineDetector.detect(
            w, h,
            filled = LineDetector.filled(grid, emptySet(), emptySet()),
            clearable = LineDetector.clearable(grid, emptySet(), emptySet(), emptySet()),
            lineBlocked = LineDetector.lineBlocked(grid, emptySet()),
            rowSpans = NinjaCutLines.rowSpans(false, w),
            halfRowAllowed = { true },
        )
        assertTrue(lines.rows.isEmpty())
    }

    @Test
    fun `el motor y la vista previa usan el mismo detector`() {
        var dir: File? = File("").absoluteFile
        val rel = "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt"
        while (dir != null && !File(dir, rel).isFile) dir = dir.parentFile
        val src = File(requireNotNull(dir), rel).readText()
        assertEquals(
            "resolveLineClearPass y previewClearLines deben barrer con LineDetector.detect (y solo ellos)",
            2, Regex("""LineDetector\.detect\(""").findAll(src).count()
        )
        assertFalse(
            "Ha vuelto un `clearable` escrito a mano en la vista previa",
            src.contains("if (p in stamped) return p !in s.hiddenWoodCells")
        )
    }
}
