package com.cukiti.teikafusion.model

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **LA BANDEJA DEJA DE SER MONOCULTIVO** (2026-09-06, pedido del usuario: «se sienten muy
 * repetitivas las fichas y salen muchas pequeñas muy seguidas veces habiendo espacio para grandes»).
 *
 * `PieceTrayPlannerTest` defiende la **garantía de encaje** — que la bandeja siempre se pueda jugar.
 * Este defiende lo otro: que la bandeja se sienta como un reparto y no como un molde. Las cuatro
 * cifras que lo motivan, medidas sobre 2.000 bandejas ANTES del arreglo:
 *
 *  - con 4 filas llenas, el **100 %** de las fichas repartidas eran de 4 celdas (las tres ranuras);
 *  - un claro limpio de 5×5 repartía el **100 %** de sus fichas de 3 celdas;
 *  - el rectángulo de 8 celdas era el **23 %** de todo lo repartido y el 3×3 el **0,5 %**;
 *  - el **54 %** de las bandejas repetía forma dentro de sí misma.
 *
 * Los umbrales de aquí son deliberadamente holgados: fijan que el vicio NO ha vuelto, no una
 * distribución concreta —eso ataría el balance a un test y lo volvería imposible de retocar—.
 */
class TrayVarietyTest {

    private fun allBoardCells(): Set<Position> = buildSet {
        for (y in 0 until GameState.GRID_SIZE) {
            for (x in 0 until GameState.GRID_SIZE) add(Position(x, y))
        }
    }

    /** Las N filas de abajo llenas: el tablero típico de mitad de partida. */
    private fun stacked(rowsFull: Int): Set<Position> = buildSet {
        for (y in (GameState.GRID_SIZE - rowsFull) until GameState.GRID_SIZE) {
            for (x in 0 until GameState.GRID_SIZE) add(Position(x, y))
        }
    }

    private fun canonical(shape: List<Position>): String {
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

    /** Un tablero de partida: [freeRatio] de casillas libres, sorteadas con [seed]. */
    private fun scattered(freeRatio: Float, seed: Int): Set<Position> {
        val all = allBoardCells().shuffled(Random(seed))
        return all.take(((1f - freeRatio) * all.size).toInt()).toSet()
    }

    private fun trays(blocked: Set<Position>, count: Int = 3, howMany: Int = 120) =
        (0 until howMany).map { seed ->
            PieceTrayPlanner.plan(
                boardSize = GameState.GRID_SIZE,
                blockedCells = blocked,
                count = count,
                level = 40,
                random = Random(seed.toLong() * 7919L + 13L),
            )
        }.filter { it.size == count }

    @Test
    fun `una bandeja no es tres veces el mismo tamaño`() {
        // El vicio original: el castigo por desviarse del tamaño objetivo (20 por escalón) aplastaba
        // al ruido del puntaje (0..18), así que el "sesgo" de tamaño era en realidad un FILTRO.
        for (rowsFull in 0..4) {
            val blocked = stacked(rowsFull)
            val batch = trays(blocked)
            val monotone = batch.count { tray -> tray.map { it.shape.size }.toSet().size == 1 }
            assertTrue(
                "Con $rowsFull filas llenas, $monotone de ${batch.size} bandejas traen un solo " +
                    "tamaño: la bandeja ha vuelto a ser un molde",
                monotone * 10 < batch.size
            )
        }
    }

    @Test
    fun `con un claro grande la bandeja reparte fichas grandes`() {
        // El caso que reportó el usuario: espacio de sobra y tres fichas pequeñas. Un claro limpio
        // de 5×5 admite un 3×3, un 2×3 y una I5; antes repartía el 100 % de sus fichas de 3 celdas.
        val blocked = allBoardCells().filterTo(mutableSetOf()) { it.x >= 5 || it.y >= 5 }
        val batch = trays(blocked)
        val sizes = batch.flatMap { tray -> tray.map { it.shape.size } }
        val media = sizes.average()
        assertTrue("Media de tamaño demasiado baja con un claro de 5×5: $media", media >= 3.6)
        assertTrue(
            "Ninguna bandeja aprovecha el claro con una ficha de 5+ celdas",
            batch.count { tray -> tray.any { it.shape.size >= 5 } } * 3 > batch.size
        )
    }

    @Test
    fun `el suelo de tamaño lo marca lo que de verdad cabe, no el porcentaje ocupado`() {
        // 5 filas llenas = 24 celdas libres (37,5 %), por debajo del escalón de "0,42" de la
        // apertura. Pero las tres filas de arriba están enteras y ahí cabe hasta un 4×2.
        val blocked = stacked(5)
        assertTrue(
            "El tablero de la prueba debería admitir una ficha de 8 celdas",
            Piece.SHAPES.any { it.size >= 8 && PieceTrayPlanner.shapeFitsAnywhere(it, 8, 8, blocked) }
        )
        val sizes = trays(blocked).flatMap { tray -> tray.map { it.shape.size } }
        assertTrue(
            "Con 24 celdas libres en tres filas enteras, la bandeja sigue siendo de trominós: " +
                "media ${sizes.average()}",
            sizes.average() >= 3.6
        )
        assertTrue("Ni una sola ficha de 5+ en todo el lote", sizes.any { it >= 5 })
    }

    @Test
    fun `la misma forma no sale dos veces en la misma bandeja`() {
        for (rowsFull in 0..4) {
            val batch = trays(stacked(rowsFull))
            val repetidas = batch.count { tray -> tray.map { canonical(it.shape) }.toSet().size < tray.size }
            assertTrue(
                "Con $rowsFull filas llenas, $repetidas de ${batch.size} bandejas repiten forma " +
                    "dentro de sí mismas (antes del arreglo: el 54 %)",
                repetidas * 5 < batch.size
            )
        }
    }

    @Test
    fun `avoidShapes aparta las formas de la bandeja anterior`() {
        // No es un veto: es un castigo. Lo que se comprueba es que MUEVE la aguja, no que la clave.
        // (2026-09-26) Sobre tableros troceados: en uno con un hueco de 3×3 solo compiten las cuatro
        // formas de 7–9 casillas y tres ranuras repiten por fuerza.
        var conMemoria = 0
        var sinMemoria = 0
        repeat(150) { seed ->
            val blocked = scattered(0.55f, seed)
            val previa = PieceTrayPlanner.plan(
                boardSize = GameState.GRID_SIZE, blockedCells = blocked, count = 3, level = 40,
                random = Random(seed.toLong()),
            )
            val formasPrevias = previa.map { canonical(it.shape) }.toSet()
            fun repite(plan: List<PieceTrayPlanner.PlannedShape>) =
                plan.any { canonical(it.shape) in formasPrevias }

            val random = Random(seed.toLong() + 500_000L)
            sinMemoria += if (repite(
                    PieceTrayPlanner.plan(
                        boardSize = GameState.GRID_SIZE, blockedCells = blocked, count = 3,
                        level = 40, random = Random(seed.toLong() + 500_000L),
                    )
                )
            ) 1 else 0
            conMemoria += if (repite(
                    PieceTrayPlanner.plan(
                        boardSize = GameState.GRID_SIZE, blockedCells = blocked, count = 3,
                        level = 40, random = random, avoidShapes = previa.map { it.shape },
                    )
                )
            ) 1 else 0
        }
        assertTrue(
            "La memoria de bandeja no reduce la repetición: con memoria $conMemoria, sin ella $sinMemoria",
            conMemoria < sinMemoria
        )
    }

    @Test
    fun `con toda la variedad encima cada ficha viene en una orientacion que cabe`() {
        // La línea que ninguna regla de bandeja puede cruzar (P-53): si el planificador devuelve N
        // fichas, existe una secuencia real para colocar las N.
        for (rowsFull in 0..6) {
            val blocked = stacked(rowsFull)
            trays(blocked, howMany = 60).forEach { plan ->
                assertTrue(
                    "Plan inválido con $rowsFull filas llenas: ${plan.map { it.shape.size }}",
                    PieceTrayPlanner.reservationsAreValid(plan, blocked)
                )
            }
        }
    }

    @Test
    fun `con una sola ranura el tamaño tambien varia`() {
        // Horizonte (P-75) y reposición instantánea (P-144) reparten de una en una: la mezcla no
        // puede estar DENTRO de la bandeja, así que tiene que estar en la sucesión de fichas.
        val sizes = (0 until 120).map { seed ->
            PieceTrayPlanner.plan(
                boardSize = GameState.GRID_SIZE, blockedCells = stacked(3), count = 1, level = 40,
                random = Random(seed.toLong() * 31L + 7L),
            ).single().shape.size
        }
        assertTrue("Repartiendo de una en una sale siempre el mismo tamaño: ${sizes.toSet()}",
            sizes.toSet().size >= 3)
    }

    @Test
    fun `el catalogo repartido cubre casi todas las formas`() {
        // A lo largo de una partida, no en una foto: un tablero vacío pide fichas grandes y uno
        // troceado, las que caben en sus huecos. El catálogo se cubre recorriendo los dos.
        val formas = ((0..5).map { stacked(it) } + (0 until 120).map { scattered(0.3f + (it % 6) * 0.1f, it) })
            .flatMap { blocked -> trays(blocked, howMany = 4) }
            .flatMap { tray -> tray.map { canonical(it.shape) } }
            .toSet()
        val catalogo = Piece.SHAPES.map(::canonical).toSet()
        assertTrue(
            "Solo ${formas.size} de ${catalogo.size} formas del catálogo llegan a repartirse",
            formas.size * 2 >= catalogo.size
        )
    }

    // ── EL SUELO DE ESTRELLAS ───────────────────────────────────────────────────────────────────

    @Test
    fun `sin estrellas a la vista se marca la ficha mas pequeña`() {
        assertEquals(
            1,
            TrayStarFloor.targetIndex(
                objectiveOpen = true, starsAlreadyVisible = false, sizes = listOf(5, 2, 4)
            )
        )
    }

    @Test
    fun `con estrellas ya a la vista no se toca nada`() {
        assertEquals(
            -1,
            TrayStarFloor.targetIndex(
                objectiveOpen = true, starsAlreadyVisible = true, sizes = listOf(5, 2, 4)
            )
        )
    }

    @Test
    fun `con el objetivo cumplido no se marca nada`() {
        assertEquals(
            -1,
            TrayStarFloor.targetIndex(
                objectiveOpen = false, starsAlreadyVisible = false, sizes = listOf(5, 2, 4)
            )
        )
    }

    @Test
    fun `la ficha que crece nunca lleva la marca`() {
        // Su máscara va celda a celda contra una forma que le cambia cada turno: `markGrowing` la
        // limpia justo por eso, y volver a ponerla la desincronizaría.
        val idx = TrayStarFloor.targetIndex(
            objectiveOpen = true,
            starsAlreadyVisible = false,
            sizes = listOf(5, 2, 4),
            blocked = listOf(false, true, false),
        )
        assertEquals(2, idx)
    }

    @Test
    fun `la brocha solo lleva la marca si no hay otra ficha`() {
        // Con alternativa se respeta la herramienta…
        assertEquals(
            2,
            TrayStarFloor.targetIndex(
                objectiveOpen = true, starsAlreadyVisible = false, sizes = listOf(3, 5, 4),
                discouraged = listOf(true, true, false),
            )
        )
        // …y sin ella manda el suelo: antes una brocha con estrellas que una bandeja muda.
        assertEquals(
            0,
            TrayStarFloor.targetIndex(
                objectiveOpen = true, starsAlreadyVisible = false, sizes = listOf(3, 5, 4),
                discouraged = listOf(true, true, true),
            )
        )
    }

    @Test
    fun `si ninguna ficha puede llevarla, no se fuerza`() {
        assertEquals(
            -1,
            TrayStarFloor.targetIndex(
                objectiveOpen = true, starsAlreadyVisible = false, sizes = listOf(3, 5),
                blocked = listOf(true, true),
            )
        )
    }
}
