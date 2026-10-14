package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de CELDAS CALIENTES (P-78). */
class HotCellsTest {

    private fun firstBiomeLevel() = LevelMap.introLevels + 1

    /**
     * **P-159 (2026-09-04): ya NO las lleva todo nivel con bioma.** Salían en el 100 % del mapa y por
     * eso habían dejado de marcar nada — una marca que está siempre es mobiliario. Ahora una de cada
     * tres, y lo que se afirma aquí es lo de siempre **allí donde salen**: entre tres y cuatro,
     * nunca menos.
     */
    @Test
    fun `el nivel que las lleva siembra entre tres y cuatro`() {
        var conCalientes = 0
        for (level in firstBiomeLevel()..LevelMap.totalLevels) {
            val cells = HotCells.forLevel(level)
            if (!HotCells.appliesTo(level)) {
                assertTrue("nivel $level siembra sin tocarle", cells.isEmpty())
                continue
            }
            conCalientes++
            assertTrue(
                "nivel $level sembró ${cells.size}",
                cells.size in HotCells.MIN_CELLS..HotCells.MAX_CELLS
            )
        }
        // Y salen lo bastante como para seguir siendo parte del vocabulario del tablero.
        val jugables = LevelMap.totalLevels - LevelMap.introLevels
        assertTrue("solo $conCalientes niveles con calientes", conCalientes >= jugables / 4)
    }

    /**
     * **Filas y columnas distintas.** Dos calientes en la misma fila las cobraría la MISMA línea de
     * un golpe: entonces no son dos decisiones de ruta, son una. Es el invariante que justifica la
     * mecánica entera.
     */
    /**
     * **NINGUNA CALIENTE CAE SOBRE LA PORTERÍA** (2026-09-02).
     *
     * Son dos marcas que dicen cosas distintas —«rómpela en una línea y paga» contra «trae un Teika
     * hasta aquí»— y hasta hoy podían compartir celda: el sembrador evita la madera, pero nadie le
     * pasaba la portería. Se comprueba con el estado REAL de la ronda, que es donde se juntan.
     */
    /**
     * **NI BAJO UN VIDRIO** (2026-09-22). La vidriera sustituye a las celdas de color y hereda su
     * guarda: una marca por celda. Se comprueba con el estado REAL de la ronda.
     */
    @Test
    fun `ninguna caliente cae bajo un vidrio`() {
        for (level in firstBiomeLevel()..LevelMap.totalLevels) {
            val estado = GameState.initial(level)
            val choque = estado.hotCells intersect estado.glassCells
            assertTrue("nivel $level: caliente y vidrio en $choque", choque.isEmpty())
        }
    }

    @Test
    fun `nunca comparten fila ni columna`() {
        for (level in firstBiomeLevel()..LevelMap.totalLevels) {
            val cells = HotCells.forLevel(level)
            assertEquals("nivel $level repite fila", cells.size, cells.map { it.y }.distinct().size)
            assertEquals("nivel $level repite columna", cells.size, cells.map { it.x }.distinct().size)
        }
    }

    @Test
    fun `siempre caen dentro del tablero`() {
        for (level in firstBiomeLevel()..LevelMap.totalLevels) {
            HotCells.forLevel(level).forEach {
                assertTrue(
                    "nivel $level: $it fuera del tablero",
                    it.x in 0 until GameState.GRID_SIZE && it.y in 0 until GameState.GRID_SIZE
                )
            }
        }
    }

    @Test
    fun `son deterministas`() {
        for (level in firstBiomeLevel()..LevelMap.totalLevels) {
            assertEquals(HotCells.forLevel(level), HotCells.forLevel(level))
        }
    }

    /** La intro y el tutorial enseñan las reglas: ahí no hay premios de ruta que buscar. */
    @Test
    fun `la intro y el tutorial no tienen celdas calientes`() {
        for (level in 1..LevelMap.introLevels) {
            assertTrue("nivel de intro $level", HotCells.forLevel(level).isEmpty())
        }
        for (level in 1..LevelMap.totalLevels) {
            if (!TutorialCampaign.isTutorialLevel(level)) continue
            assertTrue("nivel de tutorial $level", HotCells.forLevel(level).isEmpty())
        }
    }

    /**
     * Prefiere celdas LIBRES, pero un tablero muy lleno no puede quedarse sin calientes: mejor una
     * bajo un bloque que un nivel sin mecánica.
     */
    @Test
    fun `con el tablero entero ocupado sigue sembrando`() {
        val all = (0 until GameState.GRID_SIZE).flatMap { y ->
            (0 until GameState.GRID_SIZE).map { x -> Position(x, y) }
        }.toSet()
        // Un nivel QUE LAS LLEVA (P-159: ya no las lleva todo el mapa).
        val level = (firstBiomeLevel()..LevelMap.totalLevels).first { HotCells.appliesTo(it) }
        val cells = HotCells.forLevel(level, occupied = all)
        assertTrue("no sembró nada con el tablero lleno", cells.size >= HotCells.MIN_CELLS)
    }

    @Test
    fun `solo cobran las calientes que la linea cruza de verdad`() {
        val hot = setOf(Position(1, 1), Position(4, 4), Position(7, 7))
        val cleared = (0 until 8).map { Position(it, 1) }.toSet()   // la fila 1 entera
        assertEquals(setOf(Position(1, 1)), HotCells.collect(cleared, hot))
        assertEquals(HotCells.HOT_CELL_SCORE, HotCells.scoreFor(cleared, hot))
    }

    @Test
    fun `sin calientes no hay premio`() {
        val cleared = (0 until 8).map { Position(it, 1) }.toSet()
        assertTrue(HotCells.collect(cleared, emptySet()).isEmpty())
        assertEquals(0, HotCells.scoreFor(cleared, emptySet()))
    }

    /**
     * El premio tiene que NOTARSE sin decidir la calificación: cuatro calientes son ~12 % del umbral
     * de 3★, que es lo que se acordó al elegir «puntos» sobre «turnos» y «estrellas».
     */
    @Test
    fun `el premio total se queda en una fraccion sana del umbral de tres estrellas`() {
        for (level in firstBiomeLevel()..LevelMap.totalLevels step 17) {
            val total = HotCells.MAX_CELLS * HotCells.HOT_CELL_SCORE
            val threeStar = LevelLayouts.threeStarScore(level)
            assertTrue(
                "nivel $level: $total sobre $threeStar es más del 20 %",
                total <= threeStar * 0.20f
            )
        }
    }
}
