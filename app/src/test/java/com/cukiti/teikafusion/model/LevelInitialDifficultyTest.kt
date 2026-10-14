package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelInitialDifficultyTest {

    @Test
    fun onlyNearCompleteLinesCreateFreeProgressCredit() {
        val cells = buildList {
            for (x in 0..6) add(LevelCell(x, 0))
            for (x in 0..6) add(LevelCell(x, 1))
            for (y in 0..6) add(LevelCell(2, y))
        }

        val profile = LevelInitialDifficulty.analyze(cells)

        assertEquals(2, profile.nearCompleteRows)
        assertEquals(1, profile.nearCompleteColumns)
        assertEquals(0, profile.almostCompleteRows)
        assertTrue(profile.freeProgressScore > 0)
        assertTrue(LevelInitialDifficulty.initialFreeProgressCredit(cells) > 0)
    }

    @Test
    fun aFullBoardDoesNotPretendToHaveAFreeLine() {
        val cells = buildList {
            for (y in 0..7) for (x in 0..7) add(LevelCell(x, y))
        }

        val profile = LevelInitialDifficulty.analyze(cells)

        assertEquals(0, profile.nearCompleteRows)
        assertEquals(0, profile.nearCompleteColumns)
        assertEquals(0, LevelInitialDifficulty.initialFreeProgressCredit(cells))
    }

    @Test
    fun finalLevelsCanLoseTurnsOnlyWhenTheyStartWithFreeProgress() {
        val easy = buildList {
            for (x in 0..6) add(LevelCell(x, 0))
            for (x in 0..6) add(LevelCell(x, 1))
            for (x in 0..6) add(LevelCell(x, 2))
        }
        val base = LevelBudget.movesFor(
            level = LevelMap.totalLevels,
            objective = LevelObjective(emptyMap(), LevelGoal.LINES, 1),
            hasBiome = true,
            biomePrimary = BiomeType.GALACTIC
        )
        val adjusted = LevelBudget.movesFor(
            level = LevelMap.totalLevels,
            objective = LevelObjective(emptyMap(), LevelGoal.LINES, 1),
            hasBiome = true,
            initialCells = easy,
            biomePrimary = BiomeType.GALACTIC
        )

        assertTrue(
            "el progreso inicial debe apretar los turnos: base=$base ajustado=$adjusted " +
                "credito=${LevelInitialDifficulty.initialFreeProgressCredit(easy)}",
            adjusted < base
        )
        assertTrue(adjusted >= LevelBudget.MOVES_AT_START)
    }

    /**
     * El tablero generado tiene que INFLUIR en su propio presupuesto — no basta con que la curva y el
     * bioma decidan por él.
     *
     * **Cambió de signo el 2026-08-26.** Antes se comprobaba a la baja: el nivel 26 traía líneas casi
     * hechas, así que su figura le RESTABA turnos. Con la holgura de línea
     * ([LevelOpening.MAX_LINE_FILL]) ningún nivel del mapa regala ya una línea a una celda de
     * cerrarse, y el crédito de progreso inicial es cero en los 293 **por construcción**. Quien
     * conecta ahora la figura con el presupuesto es el estorbo ([BoardClutterCost]), y lo hace al
     * alza: el tablero se paga, no se cobra.
     */
    @Test
    fun generatedLayoutIsActuallyUsedByItsTurnBudget() {
        val niveles = (LevelMap.introLevels + 1)..LevelMap.totalLevels

        // Ni un solo nivel del mapa regala ya progreso de salida: es lo que garantiza la holgura de
        // línea, y por eso el crédito dejó de ser el vínculo entre figura y presupuesto.
        niveles.forEach { level ->
            assertEquals(
                "el nivel $level regala progreso de salida",
                0,
                LevelInitialDifficulty.initialFreeProgressCredit(LevelLayouts.get(level)!!.first)
            )
        }

        // El vínculo nuevo: hay niveles cuyo TABLERO les compra turnos que su curva no les daría.
        val conEstorbo = niveles.filter { level ->
            val layout = LevelLayouts.get(level)!!
            val sinFigura = LevelBudget.movesFor(
                level = level,
                objective = layout.second,
                hasBiome = true,
                figureSize = layout.first.size,
                biomePrimary = LevelMap.biomeFor(level)
            )
            LevelBudget.movesForLevel(level) > sinFigura
        }
        assertTrue(
            "ningún nivel cobra su tablero: el estorbo no está llegando al presupuesto",
            conEstorbo.isNotEmpty()
        )
    }
}
