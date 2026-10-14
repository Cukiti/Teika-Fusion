package com.cukiti.teikafusion.model

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class LevelChallengeTest {

    @Test
    fun `el impuesto del bioma se aplica a la partida real`() {
        val layout = LevelLayouts.get(50)!!
        val sinBioma = LevelBudget.movesFor(
            50, layout.second, hasBiome = false, figureSize = layout.first.size
        )
        val conTormenta = LevelBudget.movesFor(
            50, layout.second, hasBiome = true, figureSize = layout.first.size,
            biomePrimary = BiomeType.STORM
        )
        assertTrue(conTormenta >= sinBioma)
    }

    @Test
    fun `el compañero ayuda con un limite`() {
        // Se mide sobre el MAPA ENTERO y no sobre un nivel escogido a dedo. El crédito del compañero
        // entra en un suelo monótono ([LevelBudget.movesForLevel]), así que en un nivel concreto
        // puede quedar absorbido por el máximo acumulado —y eso es correcto: el suelo existe justo
        // para que la curva no baje—. Lo que el sistema promete es que el compañero **nunca resta**,
        // que en alguna parte se nota, y que su ayuda está topada.
        // Los dos lados salen de la MISMA puerta (`movesForLevel`) y solo se diferencian en el
        // compañero. Llamar por un lado a `movesFor` a mano es como se cuela una comparación
        // tramposa: esa sobrecarga también recibe `initialCells`, y omitirlas cambia el coste de
        // estorbo del tablero — en 14 niveles daba "el compañero resta turnos" cuando lo que
        // faltaba era el tablero.
        var mejoraMaxima = 0
        for (level in (LevelMap.introLevels + 1)..LevelMap.totalLevels) {
            val sin = LevelBudget.movesForLevel(level)
            val con = LevelBudget.movesForLevel(level, companionId = "galactico", companionLevel = 3)
            assertTrue("nivel $level: el compañero RESTA turnos", con >= sin)
            mejoraMaxima = maxOf(mejoraMaxima, con - sin)
        }
        assertTrue("el compañero no ayuda en ningún nivel", mejoraMaxima > 0)
        assertTrue("el compañero ayuda demasiado ($mejoraMaxima)", mejoraMaxima <= 4)
    }

    @Test
    fun `los turnos sobrantes suman puntos con tope`() {
        val profile = LevelRating.profile(150)
        val small = LevelRating.spareMoveBonus(1, 1, profile.movesTotal, level = 150)
        val huge = LevelRating.spareMoveBonus(
            profile.movesTotal, profile.movesTotal, profile.movesTotal, level = 150
        )
        assertTrue(small > 0)
        assertTrue(huge >= small)
        // Mismo redondeo que el tope de `spareMoveBonus` (con la 3★ a 1,45 truncar se quedaba 1 corto).
        assertTrue(huge <= (profile.threeStarScore * 0.12f).roundToInt())
    }

    @Test
    fun `una puntuacion epica puede dar tres estrellas sin holgura fija`() {
        val profile = LevelRating.profile(150)
        assertTrue(
            LevelRating.starsFor(150, profile.threeStarScore, 0, profile.movesTotal) == 3
        )
    }
}
