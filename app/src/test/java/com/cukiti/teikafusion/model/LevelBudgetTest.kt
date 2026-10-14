package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de los TURNOS RESTANTES (2026-07-31c, «la mecánica de Candy Crush»; recalibrado el
 * 2026-08-01b contra el PLAYTEST del usuario: ~25 turnos en los primeros niveles con bioma y ~35 en
 * los últimos, así que el presupuesto base va de 29 a 40. El perfil real puede sumar presión del
 * bioma y ayuda limitada del compañero.
 *
 * Un presupuesto mal calculado es el fallo más caro posible: **un nivel imposible no falla al
 * compilar** —arranca, se juega y se pierde siempre— y el jugador no tiene forma de saber que la
 * culpa no es suya. Estas pruebas fijan el suelo del modelo y que ningún nivel nazca sin margen.
 */
class LevelBudgetTest {

    @Test
    fun `todo nivel cubre el camino del MEGA`() {
        // El MEGA (16 T1 = 16 líneas limpiadas) es obligatorio en todo el mapa desde 2026-08-01.
        // Donde se exige, el presupuesto tiene que pagarlo o el nivel es IMPOSIBLE.
        assertEquals(16, LevelBudget.linesForMega())
        val soloMega = LevelBudget.placementsForLines(LevelBudget.linesForMega())
        for (level in 1..LevelMap.totalLevels) {
            if (!ChapterArc.requiresMega(level)) continue
            if (TutorialCampaign.isTutorialLevel(level)) continue // resuelto por tablero guiado en 1 ficha
            assertTrue(
                "nivel $level: ${LevelBudget.movesForLevel(level)} turnos no cubren ni el MEGA ($soloMega)",
                LevelBudget.movesForLevel(level) >= soloMega
            )
        }
    }

    @Test
    fun `el MEGA es obligatorio solo en el cierre del tutorial y en el mapa`() {
        // El MEGA lo exige la lección que lo ENSEÑA, no la última del tutorial: desde la ampliación
        // a siete lecciones (2026-09-01) esas dos dejaron de ser la misma. La fuente es el spec.
        for (level in 1..LevelMap.introLevels) {
            val esperado = TutorialCampaign.spec(level)?.requiresMega == true
            assertEquals("tutorial $level", esperado, ChapterArc.requiresMega(level))
        }
        assertTrue("la lección del Mega debe terminar al formarlo", ChapterArc.requiresMega(4))
        for (level in (LevelMap.introLevels + 1)..LevelMap.totalLevels) {
            assertTrue("el nivel $level debe exigir MEGA", ChapterArc.requiresMega(level))
        }
    }

    @Test
    fun `ningun nivel se va de turnos`() {
        // Techo global, intro incluida. Historia de por qué existe: mientras el presupuesto se
        // derivaba de las líneas exigidas, subir el objetivo de estrellas lo inflaba solo —llegó a
        // 79 turnos en el nivel 293— y el contador dejaba de apretar sin que nadie se enterara.
        // La ruta derivada puede crecer por encima de la medición, pero nunca debe escapar de una
        // banda razonable.
        val techo = 80
        for (level in 1..LevelMap.totalLevels) {
            val turnos = LevelBudget.movesForLevel(level)
            assertTrue("nivel $level: $turnos turnos, por encima del techo $techo", turnos <= techo)
        }
    }

    /**
     * **NO SE PIERDE POR TURNOS** (2026-09-23, decisión del usuario). Ningún nivel —ni lección— arranca
     * con presupuesto: `movesTotal = 0` es el centinela de «sin límite» que el motor ya entendía. La
     * curva de [LevelBudget] sigue viva solo como medida de dificultad para los umbrales de estrellas.
     */
    @Test
    fun `ningun nivel tiene limite de turnos`() {
        for (level in listOf(1, 6, 7, 8, 20, 60, 137, 274, LevelMap.totalLevels)) {
            val ronda = GameState.initial(level)
            assertEquals("nivel $level", 0, ronda.movesTotal)
            assertEquals("nivel $level", 0, ronda.movesLeft)
        }
    }

    @Test
    fun `el mapa se juega dentro de la banda medida mas impuesto ambiental`() {
        // La curva base sigue siendo 29-40. Encima puede sumar el impuesto AMBIENTAL del bioma (6) y,
        // desde el 2026-08-26, el ESTORBO REAL del tablero ([BoardClutterCost], hasta 12): el mapa ya
        // no regala líneas hechas de salida ([LevelOpening.MAX_LINE_FILL]) y eso hay que pagarlo.
        // Medición del día: media 38,3 turnos, mediana 39, tope 50 — sigue muy lejos de la curva
        // antigua de 79 turnos, que es lo que esta prueba existe para impedir.
        val maximo = LevelBudget.MOVES_AT_END + 6 + BoardClutterCost.MAX_EXTRA_MOVES
        for (level in (LevelMap.introLevels + 1)..LevelMap.totalLevels) {
            val turnos = LevelBudget.movesForLevel(level)
            assertTrue(
                "nivel $level: $turnos turnos, fuera de la banda medida " +
                    "[${LevelBudget.MOVES_AT_START}, $maximo]",
                turnos in LevelBudget.MOVES_AT_START..maximo
            )
        }
    }

    @Test
    fun `el presupuesto solo baja cuando el tablero lo justifica`() {
        // La curva de referencia sigue siendo monótona; el presupuesto REAL puede bajar, pero solo
        // por dos motivos que se pueden señalar con el dedo en el tablero:
        //
        //   1. el nivel regala progreso de salida (líneas a una celda de cerrarse),
        //   2. estorba MENOS que el anterior — menos cristal, menos bobinas, menos huecos de un solo
        //      monominó ([BoardClutterCost], 2026-08-26), o
        //   3. su presión AMBIENTAL es menor: el arco de capítulo baja de clímax a presentación y el
        //      impuesto de bioma con él.
        //
        // Lo que sigue prohibido es lo de siempre: que el presupuesto caiga **porque sí**. Cada turno
        // que un nivel pierde respecto al anterior tiene que poder señalarse con el dedo en su
        // tablero o en su bioma.
        var previo = 0
        var estorboPrevio = 0
        var ambientePrevio = 0
        for (level in (LevelMap.introLevels + 1)..LevelMap.totalLevels) {
            val turnos = LevelBudget.movesForLevel(level)
            val credito = LevelInitialDifficulty.initialFreeProgressCredit(
                LevelLayouts.get(level)!!.first
            )
            val estorbo = LevelBudget.clutterTaxForLevel(level)
            val ambiente = BiomeDifficultyProfile.extraMoves(level, LevelMap.biomeFor(level))
            val alivio = (estorboPrevio - estorbo).coerceAtLeast(0) +
                (ambientePrevio - ambiente).coerceAtLeast(0)
            assertTrue(
                "nivel $level baja de $previo a $turnos sin justificación " +
                    "(crédito=$credito, alivio=$alivio)",
                turnos + credito + alivio >= previo
            )
            previo = turnos
            estorboPrevio = estorbo
            ambientePrevio = ambiente
        }
    }

    @Test
    fun `un objetivo desbocado manda sobre la curva`() {
        // La guardia de nivel imposible: si el objetivo pide más trabajo del que paga la curva
        // medida, manda el objetivo. 40 líneas son 48 turnos, muy por encima de la curva.
        val desbocado = LevelObjective(emptyMap(), LevelGoal.LINES, 40)
        assertTrue(
            "desbocado=${LevelBudget.movesFor(100, desbocado, true)} base=${LevelBudget.playtestMoves(100)}",
            LevelBudget.movesFor(100, desbocado, true) > LevelBudget.playtestMoves(100)
        )
        assertTrue(
            LevelBudget.movesFor(100, desbocado, true) >=
                LevelBudget.placementsForLines(40)
        )
        // Dentro de la curva el tamaño del objetivo da igual: paga la medición, no el modelo.
        val pocasLineas = LevelObjective(emptyMap(), LevelGoal.LINES, 4)
        assertEquals(
            "pocas=${LevelBudget.movesFor(100, pocasLineas, true)} base=${LevelBudget.playtestMoves(100)}",
            LevelBudget.playtestMoves(100),
            LevelBudget.movesFor(100, pocasLineas, true)
        )
        assertEquals(
            LevelBudget.movesFor(100, pocasLineas, true),
            LevelBudget.movesFor(100, LevelObjective(emptyMap(), LevelGoal.LINES, 1), true)
        )
    }

    @Test
    fun `la curva medida va de un extremo al otro`() {
        // Los dos anclajes del playtest, cada uno en su punta del mapa.
        assertEquals(LevelBudget.MOVES_AT_START, LevelBudget.playtestMoves(LevelMap.introLevels + 1))
        assertEquals(LevelBudget.MOVES_AT_END, LevelBudget.playtestMoves(LevelMap.totalLevels))
    }

    @Test
    fun `el despeje no pide lineas cuando ya se cumple`() {
        // Sin figura que despejar (figureSize 0) no hay trabajo que pagar.
        assertEquals(0, LevelBudget.linesForObjective(LevelObjective(emptyMap(), LevelGoal.CLEAR, 3)))
    }

    @Test
    fun `el bioma da colchon en la ruta derivada`() {
        // En el mapa, cuando se conoce el bioma concreto, se aplica su impuesto específico en vez
        // de tratar todos los biomas igual. La antigua ruta de intro ya no usa presupuesto.
        val obj = LevelObjective(emptyMap(), LevelGoal.LINES, 14)
        val sinBioma = LevelBudget.movesFor(50, obj, hasBiome = false)
        val tormenta = LevelBudget.movesFor(
            50, obj, hasBiome = true,
            biomePrimary = BiomeType.STORM
        )
        assertTrue("Tormenta debe reflejar su presión: $tormenta vs $sinBioma", tormenta > sinBioma)
    }

    @Test
    fun `sin objetivo no hay limite`() {
        // 0 = centinela de «sin límite»: Infinito y niveles sin layout.
        assertEquals(0, LevelBudget.movesFor(1, null, hasBiome = false))
    }

    @Test
    fun `ningun nivel baja del suelo`() {
        for (level in 1..LevelMap.totalLevels) {
            if (TutorialCampaign.isTutorialLevel(level)) continue
            assertTrue(
                "nivel $level por debajo del suelo",
                LevelBudget.movesForLevel(level) >= LevelBudget.MIN_MOVES
            )
        }
    }

    @Test
    fun `revivir devuelve turnos de verdad`() {
        // Volver al tablero con 0 movimientos sería perder otra vez en la jugada siguiente: revivir
        // tiene que dar margen real.
        assertTrue(LevelBudget.REVIVE_MOVES > 0)
    }

    @Test
    fun `el presupuesto crece con el juego`() {
        // Los últimos niveles piden más líneas, así que deben traer más turnos que los primeros.
        val primeroConBioma = LevelMap.introLevels + 1
        assertTrue(
            LevelBudget.movesForLevel(LevelMap.totalLevels) > LevelBudget.movesForLevel(primeroConBioma)
        )
    }

    // ── P-86 · EL PANEL Y LA RONDA NO PUEDEN DISCREPAR ──────────────────────────────────────────

    /**
     * Y que el compañero SE NOTE de verdad: si `movesForLevel` lo ignorara en silencio, el test de
     * arriba pasaría igual (los dos lados devolverían el mismo número equivocado).
     */
    @Test
    fun `el companero cambia el presupuesto`() {
        val distintos = listOf(20, 60, 137, 274).count { level ->
            LevelBudget.movesForLevel(level) !=
                LevelBudget.movesForLevel(level, "galactico", 3)
        }
        assertTrue(
            "movesForLevel devuelve lo mismo con y sin compañero: el parámetro no se está usando",
            distintos > 0
        )
    }


}
