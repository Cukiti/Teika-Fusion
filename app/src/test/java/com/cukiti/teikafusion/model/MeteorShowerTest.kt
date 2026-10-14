package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-294 · LLUVIA DE METEORITOS: dónde cae, qué funde, qué lo desvía y cómo es la roca. */
class MeteorShowerTest {

    private fun wood(vararg cells: Pair<Int, Int>): Map<Position, CellContent> =
        cells.associate { (x, y) -> Position(x, y) to CellContent.Wood() }

    @Test
    fun `el crater es el 2x2 con mas bloques`() {
        // Solo el cráter de (4,2) recoge los dos bloques en diagonal.
        val grid = wood(4 to 2, 5 to 3, 0 to 0)
        assertEquals(Position(4, 2), MeteorShower.choose(grid))
        assertEquals(2, MeteorShower.damage(Position(4, 2), grid))
    }

    /** Con el cráter lleno, el impacto funde como mucho [MeteorShower.MAX_ROCKS] (2026-09-25). */
    @Test
    fun `el impacto funde como mucho dos bloques`() {
        val grid = wood(4 to 2, 5 to 2, 4 to 3, 5 to 3)
        assertEquals(MeteorShower.MAX_ROCKS, MeteorShower.rocksFor(Position(4, 2), grid).size)
        assertEquals(2, MeteorShower.MAX_ROCKS)   // P-352: 2 → 3 · P-359: 3 → 2
    }

    @Test
    fun `la roca ya fundida no cuenta`() {
        val grid = mapOf(Position(0, 0) to CellContent.Wood(armor = 1, rock = true))
        assertNull(MeteorShower.choose(grid))
    }

    @Test
    fun `no cae sobre casillas vetadas`() {
        val grid = wood(4 to 2, 5 to 2, 5 to 3)
        val at = MeteorShower.choose(grid, blocked = setOf(Position(5, 3)))
        assertTrue(at == null || Position(5, 3) !in MeteorShower.craterCells(at))
    }

    @Test
    fun `una linea que cruza el crater lo desvia`() {
        val fila3 = (0 until 8).map { Position(it, 3) }
        assertTrue(MeteorShower.deflectedBy(Position(4, 2), fila3))
        val fila6 = (0 until 8).map { Position(it, 6) }
        assertFalse(MeteorShower.deflectedBy(Position(4, 2), fila6))
        assertFalse(MeteorShower.deflectedBy(null, fila3))
    }

    /** La roca: blindada (necesita dos líneas) y con su cara; conserva color y estrella. */
    @Test
    fun `la roca es un bloque blindado`() {
        val w = CellContent.Wood(colorType = 2, targetColor = TargetColor.RED)
        val r = MeteorShower.petrify(w)
        assertEquals(1, r.armor)
        assertTrue(r.rock)
        assertEquals(2, r.colorType)
        assertEquals(TargetColor.RED, r.targetColor)
    }

    // ── P-319 · el Espacio Profundo se endurece ─────────────────────────────────────────────────

    private fun rock(age: Int = 0) = CellContent.Wood(armor = 1, rock = true, rockAge = age)

    @Test
    fun `la metralla cae en huecos junto al crater y nunca cierra una linea`() {
        val at = Position(3, 3)
        // Fila 2 casi llena: le falta solo (2,2), que está al lado del cráter. Ahí no puede caer.
        val grid = (0 until 8).filter { it != 2 }.associate { Position(it, 2) to CellContent.Wood() as CellContent }
        val crater = MeteorShower.craterCells(at).toSet()
        repeat(40) { seed ->
            val shards = MeteorShower.shardsFor(at, grid, unusable = emptySet(), filled = emptySet(), random = kotlin.random.Random(seed))
            assertEquals(MeteorShower.SHARDS, shards.size)
            shards.forEach { p ->
                assertTrue("$p en el cráter", p !in crater)
                assertTrue("$p ocupada", grid[p] == null)
                assertTrue("$p lejos del cráter", crater.any { c -> maxOf(kotlin.math.abs(c.x - p.x), kotlin.math.abs(c.y - p.y)) <= MeteorShower.SHARD_REACH })
            }
            assertFalse("un fragmento cerró la fila 2", Position(2, 2) in shards)
        }
    }

    @Test
    fun `la metralla respeta las casillas que no se pueden usar`() {
        val at = Position(0, 0)
        val ring = (0..3).flatMap { y -> (0..3).map { x -> Position(x, y) } }.toSet() - MeteorShower.craterCells(at).toSet()
        val shards = MeteorShower.shardsFor(at, emptyMap(), unusable = ring, filled = emptySet(), random = kotlin.random.Random(1))
        assertTrue(shards.isEmpty())
    }

    @Test
    fun `el fragmento es roca`() {
        val s = MeteorShower.shard(3)
        assertTrue(s.rock)
        assertEquals(1, s.armor)
        assertEquals(3, s.colorType)
    }

    @Test
    fun `la roca se agrieta a la tercera colocacion y contagia a un vecino`() {
        var grid: Map<Position, CellContent> = mapOf(Position(3, 3) to rock(), Position(4, 3) to CellContent.Wood())
        val rnd = kotlin.random.Random(5)
        repeat(MeteorShower.CRACK_TURNS - 1) {
            val (g, cracks) = MeteorShower.tickRocks(grid, rnd)
            assertTrue(cracks.isEmpty())
            grid = g
        }
        assertEquals(2, MeteorShower.crackStage(grid[Position(3, 3)] as CellContent.Wood))
        val (g, cracks) = MeteorShower.tickRocks(grid, rnd)
        assertEquals(listOf(MeteorShower.Crack(Position(3, 3), Position(4, 3))), cracks)
        assertEquals(0, (g[Position(3, 3)] as CellContent.Wood).rockAge)
        // El contagiado se funde en su VFX: el estado aún no lo tiene.
        assertFalse((g[Position(4, 3)] as CellContent.Wood).rock)
    }

    @Test
    fun `sin vecino la roca espera en su ultimo aviso`() {
        val grid = mapOf(Position(0, 0) to rock(age = MeteorShower.CRACK_TURNS - 1))
        val (g, cracks) = MeteorShower.tickRocks(grid, kotlin.random.Random(1))
        assertTrue(cracks.isEmpty())
        assertEquals(2, MeteorShower.crackStage(g[Position(0, 0)] as CellContent.Wood))
    }

    @Test
    fun `con el tope de rocas ya no contagia`() {
        val grid = HashMap<Position, CellContent>()
        (0 until MeteorShower.MAX_BOARD_ROCKS).forEach { grid[Position(it, 0)] = rock(age = MeteorShower.CRACK_TURNS - 1) }
        (0 until 8).forEach { grid[Position(it, 1)] = CellContent.Wood() }
        val (_, cracks) = MeteorShower.tickRocks(grid, kotlin.random.Random(1))
        assertTrue(cracks.isEmpty())
    }

    @Test
    fun `dos rocas no contagian el mismo bloque`() {
        val grid = mapOf(
            Position(2, 2) to rock(age = MeteorShower.CRACK_TURNS - 1),
            Position(4, 2) to rock(age = MeteorShower.CRACK_TURNS - 1),
            Position(3, 2) to CellContent.Wood(),
        )
        val (_, cracks) = MeteorShower.tickRocks(grid, kotlin.random.Random(1))
        assertEquals(1, cracks.size)
    }

    @Test
    fun `la lluvia doble solo cae en el ultimo tercio del capitulo`() {
        // P-361 (2026-09-28): la lluvia doble está apagada. Si se vuelve a encender, cae al final del capítulo.
        if (!MeteorShower.DOUBLE_SHOWER) {
            assertTrue((1..LevelMap.totalLevels).none { MeteorShower.doubleShowerFor(it) })
            return
        }
        val chapter = LevelMap.chapters.indexOfFirst { it.biome == BiomeType.GALACTIC }
        val levels = (1..LevelMap.totalLevels).filter { LevelMap.chapterIndexForLevel(it) == chapter }
        val dobles = levels.filter { MeteorShower.doubleShowerFor(it) }
        assertTrue(dobles.isNotEmpty())
        assertFalse(MeteorShower.doubleShowerFor(levels.first()))
        assertTrue(MeteorShower.doubleShowerFor(levels.last()))
        assertTrue("los dobles son el final del capítulo", dobles == levels.takeLast(dobles.size))
        assertTrue(dobles.size in (levels.size / 4)..(levels.size / 2))
    }

    @Test
    fun `el segundo crater no toca el primero`() {
        val grid = (0 until 8).flatMap { y -> (0 until 8).map { x -> Position(x, y) } }.associateWith { CellContent.Wood() as CellContent }
        val first = MeteorShower.choose(grid)!!
        val second = MeteorShower.chooseSecond(grid, first)!!
        val a = MeteorShower.craterCells(first)
        MeteorShower.craterCells(second).forEach { p ->
            assertTrue(a.all { maxOf(kotlin.math.abs(it.x - p.x), kotlin.math.abs(it.y - p.y)) >= 2 })
        }
    }
}
