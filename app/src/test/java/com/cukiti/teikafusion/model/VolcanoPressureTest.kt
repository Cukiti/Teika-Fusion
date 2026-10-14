package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VolcanoPressureTest {

    @Test
    fun firstDryTurnOnlyArmsWarning() {
        val step = nextVolcanoPressure(currentDryTurns = 0, linesCleared = 0)

        assertEquals(1, step.dryTurns)
        assertFalse(step.advanceLava)
    }

    @Test
    fun thirdDryTurnAdvancesLavaAndResetsCounter() {
        val step = nextVolcanoPressure(currentDryTurns = VOLCANO_DRY_TURNS_TO_ADVANCE - 1, linesCleared = 0)

        assertEquals(0, step.dryTurns)
        assertTrue(step.advanceLava)
    }

    /**
     * Con la lava YA asomada baja en CADA turno seco — no espera un segundo (playtest 2026-08-30:
     * «si ya está abajo no tiene por qué esperar dos turnos para seguir bajando»). Es la escalera
     * 3 → 1 que da su firma rítmica al bioma; ver [VOLCANO_DRY_TURNS_SURFACED].
     */
    @Test
    fun surfacedLavaAdvancesOnEveryDryTurn() {
        val first = nextVolcanoPressure(currentDryTurns = 0, linesCleared = 0, lavaRow = 3)
        assertEquals(0, first.dryTurns)
        assertTrue(first.advanceLava)

        // Y el siguiente turno seco vuelve a bajarla: la presión no se toma descansos.
        val second = nextVolcanoPressure(currentDryTurns = 0, linesCleared = 0, lavaRow = 4)
        assertEquals(0, second.dryTurns)
        assertTrue(second.advanceLava)
    }

    /** Pero SIN lava todavía siguen haciendo falta los 3 turnos de gracia (el otro peldaño). */
    @Test
    fun unsurfacedLavaStillGetsThreeDryTurns() {
        val first = nextVolcanoPressure(currentDryTurns = 0, linesCleared = 0, lavaRow = 0)
        assertEquals(1, first.dryTurns)
        assertFalse(first.advanceLava)
    }

    /** Reloj que se congela (2026-09-23): romper en un turno de AVISO deja la lava encendida donde estaba. */
    @Test
    fun clearingLineOnAWarningTurnFreezesTheCount() {
        val step = nextVolcanoPressure(currentDryTurns = 1, linesCleared = 1, firstThreshold = 3)

        assertEquals(1, step.dryTurns)
        assertFalse(step.advanceLava)
    }

    /** …y romper JUSTO en el turno en que iba a bajar la apaga. */
    @Test
    fun clearingLineOnTheDropTurnCancelsIt() {
        val step = nextVolcanoPressure(currentDryTurns = 2, linesCleared = 1, firstThreshold = 3)

        assertEquals(0, step.dryTurns)
        assertFalse(step.advanceLava)
    }

    @Test
    fun directedThresholdsReplaceTheFixedCadence() {
        val armed = nextVolcanoPressure(
            currentDryTurns = 3,
            linesCleared = 0,
            lavaRow = 0,
            firstThreshold = 5,
            surfacedThreshold = 4
        )
        assertEquals(4, armed.dryTurns)
        assertFalse(armed.advanceLava)

        val advanced = nextVolcanoPressure(
            currentDryTurns = 3,
            linesCleared = 0,
            lavaRow = 2,
            firstThreshold = 5,
            surfacedThreshold = 4
        )
        assertEquals(0, advanced.dryTurns)
        assertTrue(advanced.advanceLava)
    }

    @Test
    fun simultaneousLinesRetreatLavaOneRowPerLine() {
        // P-262 dio dos filas por línea; P-352 (2026-09-27) la deja en una.
        assertEquals(4, retreatLava(currentLavaRow = 5, linesCleared = 1))
        assertEquals(2, retreatLava(currentLavaRow = 5, linesCleared = 3))
    }

    @Test
    fun lavaRetreatNeverCrossesTopEdge() {
        assertEquals(0, retreatLava(currentLavaRow = 2, linesCleared = 4))
    }

    /**
     * El aviso en dos tiempos (2026-09-23): nada al empezar, cresta en el primer turno seco, hierve en
     * el segundo y la lava baja en el tercero — y cada fase coincide con lo que hace el motor.
     */
    @Test
    fun warningAppearsOnFirstDryTurnAndBoilsOnSecond() {
        val threshold = VOLCANO_DRY_TURNS_TO_ADVANCE
        assertEquals(VOLCANO_WARN_NONE, volcanoWarningPhase(lavaRow = 0, dryTurns = 0, threshold = threshold))
        assertEquals(VOLCANO_WARN_CREST, volcanoWarningPhase(lavaRow = 0, dryTurns = 1, threshold = threshold))
        assertEquals(VOLCANO_WARN_BOIL, volcanoWarningPhase(lavaRow = 0, dryTurns = 2, threshold = threshold))
        // Y el motor baja la lava en la colocación seca siguiente a la del hervor.
        assertTrue(nextVolcanoPressure(currentDryTurns = 2, linesCleared = 0, lavaRow = 0).advanceLava)
        assertFalse(nextVolcanoPressure(currentDryTurns = 1, linesCleared = 0, lavaRow = 0).advanceLava)
    }

    @Test
    fun warningFollowsLongerThresholdsAndSurfacedLavaAlwaysBoils() {
        assertEquals(VOLCANO_WARN_NONE, volcanoWarningPhase(lavaRow = 0, dryTurns = 1, threshold = 4))
        assertEquals(VOLCANO_WARN_CREST, volcanoWarningPhase(lavaRow = 0, dryTurns = 2, threshold = 4))
        assertEquals(VOLCANO_WARN_BOIL, volcanoWarningPhase(lavaRow = 2, dryTurns = 0, threshold = 1))
    }

    /**
     * P-353 (2026-09-28): romper líneas retira la lava pero NO reinicia su reloj. El reinicio sobrevivió
     * en la cadena de líneas, en la habilidad y en la descarga del circuito, y la lava bajaba 0,11 veces por
     * partida. Solo el tick (vía [nextVolcanoPressure]) y la derrota por lava pueden escribir el contador.
     */
    @Test
    fun `romper lineas no reinicia el reloj de la lava`() {
        val vm = java.io.File("src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt").readText()
        // Revivir tras morir POR LAVA sí lo pone a cero (DefeatCause.LAVA): esa es la única excepción.
        val writes = Regex("""volcanoDryTurns\s*=\s*(if \([^)]*\)\s*)?0\b""").findAll(vm)
            .count { "DefeatCause.LAVA" !in it.value }
        assertEquals("Alguien vuelve a poner a cero el reloj de la lava al romper", 0, writes)
    }
}
