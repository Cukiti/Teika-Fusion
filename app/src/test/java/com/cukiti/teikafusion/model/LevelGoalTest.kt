package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de **QUÉ TE PIDE UN NIVEL** (reescrito 2026-08-01).
 *
 * Entre el 2026-07-31b y hoy el generador repartía tres objetivos distintos por acto de capítulo
 * (ESTRELLAS / LÍNEAS / DESPEJE) buscando variedad. En pantalla salió caro: cada sabor necesitaba su
 * propio chip en el HUD y entre ellos se empujaban y descuadraban la zona de objetivos nivel a
 * nivel. Decisión del usuario: **la pregunta es siempre la misma — TURNOS + ESTRELLAS + MEGA
 * TEIKA**, y la variedad se queda donde no cuesta legibilidad (figura, bioma, arco, presupuesto de
 * turnos).
 *
 * Lo que se fija aquí es lo que rompería el juego si se descuadra: que ningún nivel quede sin pedir
 * nada, que ninguno nazca ya ganado —eso no se detecta compilando— y que los sabores retirados
 * sigan existiendo en el enum pero **sin generarse**, porque viajan dentro de partidas guardadas.
 */
class LevelGoalTest {

    private fun objetivoDe(level: Int): LevelObjective = LevelLayouts.get(level)!!.second

    @Test
    fun `los niveles normales piden ESTRELLAS`() {
        for (level in (TutorialCampaign.LAST_LEVEL + 1)..LevelMap.totalLevels) {
            assertEquals(
                "el nivel $level no pide estrellas",
                LevelGoal.STARS, objetivoDe(level).goal
            )
        }
    }

    @Test
    fun `ningun nivel normal genera los sabores retirados`() {
        // LINES / CLEAR / SURVIVE siguen en el enum SOLO para no romper saves viejos (igual que
        // GameStatus.CHESS). El tutorial usa LINES como contador interno sin estrellas; el mapa no.
        val retirados = setOf(LevelGoal.LINES, LevelGoal.CLEAR, LevelGoal.SURVIVE)
        for (level in (TutorialCampaign.LAST_LEVEL + 1)..LevelMap.totalLevels) {
            assertTrue(
                "el nivel $level genera un objetivo retirado",
                objetivoDe(level).goal !in retirados
            )
        }
    }

    @Test
    fun `ningun objetivo se cumple de salida`() {
        // Un nivel que ya está ganado al entrar es el peor fallo posible de este sistema, y es
        // invisible: compila, arranca y se completa solo.
        for (level in 1..LevelMap.totalLevels) {
            if (TutorialCampaign.isTutorialLevel(level)) continue
            assertTrue("nivel $level", objetivoDe(level).total > 0)
        }
    }

    @Test
    fun `todos los bloques de la figura llevan estrella`() {
        // Pedido del usuario (2026-08-01): el objetivo tiene que verse ENTERO desde el primer frame.
        // Antes solo un 25-45 % de la figura iba marcada y el grueso de las estrellas llegaba en las
        // fichas de la bandeja, o sea al azar y a mitad de partida.
        for (level in 1..LevelMap.totalLevels) {
            val cells = LevelLayouts.get(level)!!.first
            if (TutorialCampaign.isTutorialLevel(level)) continue
            assertTrue("el nivel $level nace sin figura", cells.isNotEmpty())
            assertTrue(
                "el nivel $level tiene bloques sin estrella",
                cells.all { it.targetColor != null }
            )
        }
    }

    @Test
    fun `cada color del objetivo aparece en el tablero`() {
        // El reparto round-robin garantiza representación: sin esto, un color exigido podía no salir
        // ni una vez en la figura y el nivel dependía de que la bandeja lo repartiera.
        for (level in 1..LevelMap.totalLevels) {
            val (cells, obj) = LevelLayouts.get(level)!!
            val enTablero = cells.mapNotNull { it.targetColor }.toSet()
            assertTrue(
                "nivel $level: exige ${obj.required.keys} y el tablero solo trae $enTablero",
                enTablero.containsAll(obj.required.keys)
            )
        }
    }

    @Test
    fun `los tutoriales no piden estrellas, salvo la leccion que las ensena`() {
        // La 5ª lección SÍ pide estrellas, y a propósito (2026-09-01): enseñar cuál es el objetivo
        // del juego con un objetivo de mentira no enseñaría nada. El resto sigue midiéndose en
        // líneas, que es un hito interno y no una calificación.
        for (level in TutorialCampaign.FIRST_LEVEL..TutorialCampaign.LAST_LEVEL) {
            val (cells, objective) = LevelLayouts.get(level)!!
            val pideEstrellas = TutorialCampaign.spec(level)?.requiredStars
            if (pideEstrellas != null) {
                assertEquals("tutorial $level", pideEstrellas, objective.required)
                assertTrue(
                    "tutorial $level pide estrellas que no están en el tablero",
                    cells.mapNotNull { it.targetColor }.toSet().containsAll(pideEstrellas.keys)
                )
            } else {
                assertTrue("tutorial $level mezcla estrellas", objective.required.isEmpty())
                assertEquals("tutorial $level tiene rating objetivo", 0, objective.total)
                assertTrue(
                    "tutorial $level tiene estrellas en el tablero",
                    cells.none { it.targetColor != null }
                )
            }
        }
    }

    @Test
    fun `un save antiguo entra como nivel de estrellas`() {
        // `LevelObjective` NO se convirtió en jerarquía sellada justamente para esto: los campos
        // nuevos tienen valor por defecto, así que un objetivo deserializado de un save viejo
        // —sin `goal` ni `amount`— se comporta como siempre.
        val viejo = LevelObjective(required = mapOf(TargetColor.entries.first() to 5))
        assertEquals(LevelGoal.STARS, viejo.goal)
        assertEquals(0, viejo.amount)
        assertEquals(5, viejo.total)
    }
}
