package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariantes del generador de niveles ([LevelLayouts]) — el modo NIVELES entero se deriva de aquí.
 *
 *  1. Los ~293 niveles CONSTRUYEN: celdas dentro del 8×8, sin duplicados, con hueco jugable y un
 *     objetivo no vacío (un nivel sin estrellas que recoger sería incompletable: la victoria exige
 *     recogerlas todas + formar un MEGA).
 *  2. Los umbrales de estrellas son FUENTE ÚNICA: [LevelLayouts.starsFor] debe ser coherente con
 *     [LevelLayouts.twoStarScore]/[LevelLayouts.threeStarScore] — la barra de meta del HUD y el
 *     popup de objetivo muestran ESOS números, y el mural del mapa califica con starsFor. Si esta
 *     coherencia se rompe, la UI promete un umbral y el juego califica con otro.
 *  3. El generador es DETERMINISTA por nivel (semilla fija): el layout que ve el jugador al
 *     reintentar un nivel es siempre el mismo.
 */
class LevelLayoutsTest {

    @Test
    fun everyLevelBuildsAPlayableBoardWithNonEmptyObjective() {
        for (level in 1..LevelMap.totalLevels) {
            val built = LevelLayouts.get(level)
            assertNotNull("nivel $level no construye", built)
            val (cells, objective) = built!!
            val tutorial = TutorialCampaign.isTutorialLevel(level)
            // Desde 2026-07-31b hay cuatro sabores: los de estrellas piden colores, el resto pide
            // una cantidad. Lo que NO puede pasar nunca es que un nivel no pida nada.
            if (tutorial) {
                // Excepción con nombre: la lección que enseña el objetivo pide estrellas de verdad.
                val ensenaEstrellas = TutorialCampaign.spec(level)?.requiredStars != null
                assertEquals(
                    "tutorial $level y sus estrellas",
                    ensenaEstrellas, objective.required.isNotEmpty()
                )
            } else if (objective.goal == LevelGoal.STARS) {
                assertTrue("nivel $level sin objetivo", objective.required.isNotEmpty())
                assertTrue("nivel $level con objetivo de 0 estrellas", objective.total > 0)
            } else {
                assertTrue(
                    "nivel $level (${objective.goal}) con cantidad 0: se ganaría solo",
                    objective.amount > 0
                )
                assertTrue(
                    "nivel $level (${objective.goal}) mezcla estrellas con otro objetivo",
                    objective.required.isEmpty()
                )
            }
            assertTrue("nivel $level: celdas fuera del tablero",
                cells.all { it.x in 0..7 && it.y in 0..7 })
            assertEquals("nivel $level: celdas duplicadas",
                cells.size, cells.map { it.x to it.y }.distinct().size)
            assertTrue("nivel $level nace sin hueco jugable (${cells.size} celdas)",
                cells.size < GameState.GRID_SIZE * GameState.GRID_SIZE)
        }
    }

    // ── ESTRELLAS EN LA FIGURA (2026-07-31b, pedido del usuario) ─────────────────────────────────
    // El tablero pre-colocado tenía las estrellas a suerte (`0,10 + t·0,35`): en los biomas
    // tempranos salía un 10 %, o sea UNO O DOS bloques marcados de 16, y a veces un color del
    // objetivo no aparecía ni una vez. La figura decoraba, no jugaba. Ahora es un cupo repartido.

    @Test
    fun everyLevelPutsStarsOnTheBoard() {
        for (level in 1..LevelMap.totalLevels) {
            if (TutorialCampaign.isTutorialLevel(level)) continue
            val (cells, _) = LevelLayouts.get(level)!!
            val conEstrella = cells.count { it.targetColor != null }
            assertTrue("nivel $level: figura SIN estrellas (${cells.size} bloques)", conEstrella > 0)
            // Cupo: al menos ~1 de cada 5 bloques. (La fracción va de 0,25 a 0,45; se comprueba con
            // holgura porque el redondeo y el mínimo por color pueden moverla un poco.)
            assertTrue(
                "nivel $level: solo $conEstrella estrellas en ${cells.size} bloques",
                conEstrella * 5 >= cells.size
            )
        }
    }

    @Test
    fun everyObjectiveColorIsPresentOnTheBoard() {
        // Si un color del objetivo no está en el tablero, el jugador depende por completo de que la
        // bandeja se lo regale — y el objetivo se lee como injusto sin que nada explique por qué.
        for (level in 1..LevelMap.totalLevels) {
            val (cells, objective) = LevelLayouts.get(level)!!
            val enTablero = cells.mapNotNull { it.targetColor }.toSet()
            assertTrue(
                "nivel $level: el objetivo pide ${objective.required.keys} y el tablero solo trae $enTablero",
                enTablero.containsAll(objective.required.keys)
            )
        }
    }

    @Test
    fun starsAreSpreadNotClustered() {
        // Todas las estrellas juntas en una esquina = un solo clear se lo lleva todo y el resto del
        // tablero es relleno. Se exige que ocupen más de una fila en cuanto la figura da para ello.
        for (level in listOf(5, 40, 120, 250, LevelMap.totalLevels)) {
            val (cells, _) = LevelLayouts.get(level)!!
            val filasConEstrella = cells.filter { it.targetColor != null }.map { it.y }.toSet()
            val filasDeLaFigura = cells.map { it.y }.toSet()
            if (filasDeLaFigura.size > 1) {
                assertTrue(
                    "nivel $level: todas las estrellas en la misma fila",
                    filasConEstrella.size > 1
                )
            }
        }
    }

    @Test
    fun levelsOutOfRangeDoNotBuild() {
        assertNull(LevelLayouts.get(0))
        assertNull(LevelLayouts.get(LevelMap.totalLevels + 1))
    }

    /**
     * La calificación gradual: los turnos sobrantes añaden puntos, pero una jugada épica también
     * puede alcanzar 3★ sin depender de una barrera binaria de turnos.
     */
    @Test
    fun laCalificacionUsaPuntosYTurnosSinBarreraDura() {
        for (level in listOf(5, 25, 150, LevelMap.totalLevels)) {
            val total = LevelBudget.movesForLevel(level)
            val perfil = LevelRating.profile(level)
            val dos = perfil.twoStarScore
            val tres = LevelLayouts.threeStarScore(level)
            assertTrue("nivel $level: la 2★ ($dos) debe pedir menos que la 3★ ($tres)", dos < tres)

            // La puntuación baja no alcanza 2★ aunque sobren turnos.
            assertEquals(1, LevelLayouts.starsFor(level, dos - 1, 0, total))
            // El rendimiento de 2★ se puede alcanzar aun terminando en el último turno.
            assertEquals(2, LevelLayouts.starsFor(level, dos, 0, total))
            // Una jugada épica puede alcanzar 3★ sin cumplir un porcentaje fijo de turnos.
            assertEquals(3, LevelLayouts.starsFor(level, tres, 0, total))

            // Los turnos sobrantes son puntos adicionales, con límite para no dominar el marcador.
            val bonus = LevelRating.spareMoveBonus(total, total, level = level)
            assertTrue("nivel $level sin bonus de turnos", bonus > 0)
        }
    }

    /**
     * El umbral de 3★ tiene que estar POR ENCIMA de lo que el motor paga solo por ganar.
     *
     * Es el fallo que se corrigió el 2026-08-01: `megaTeikaScore` valía la mitad del umbral y el
     * MEGA pasó a ser obligatorio, así que ganar el nivel regalaba las 3 estrellas. Si algún día un
     * retune vuelve a dejar el umbral por debajo de [LevelLayouts.guaranteedScore], la calificación
     * deja de significar nada y no lo notaría nadie hasta jugarlo.
     */
    @Test
    fun lasTresEstrellasPidenMasQueGanarElNivel() {
        for (level in (LevelMap.introLevels + 1)..LevelMap.totalLevels) {
            val renta = LevelLayouts.guaranteedScore(level)
            val tres = LevelLayouts.threeStarScore(level)
            assertTrue(
                "nivel $level: 3★ pide $tres y ganar ya paga $renta — se regalan",
                tres > renta
            )
        }
    }

    /** El bonus del MEGA no puede volver a depender del umbral: era el bucle que lo rompía todo. */
    @Test
    fun elBonusDelMegaNoEscalaConElUmbral() {
        val primero = LevelMap.introLevels + 1
        assertEquals(
            LevelLayouts.megaTeikaScore(primero),
            LevelLayouts.megaTeikaScore(LevelMap.totalLevels)
        )
    }

    @Test
    fun generatorIsDeterministicPerLevel() {
        for (level in listOf(1, 47, 200)) {
            assertEquals("el nivel $level debe ser idéntico en cada construcción",
                LevelLayouts.get(level), LevelLayouts.get(level))
        }
    }
}
