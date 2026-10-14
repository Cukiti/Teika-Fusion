package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **UN NIVEL, UNA MECÁNICA DE VARIEDAD** (P-129, 2026-09-03).
 *
 * La regla existía desde que entró la segunda de estas mecánicas, pero estaba escrita como
 * negaciones sueltas dentro de cada `appliesTo` y probada **par a par**: `BoardCurrentTest` cubría
 * «nunca con gravedad ni con horizonte» y `HorizonTrayTest` «nunca con gravedad». Esa forma de
 * probar tiene el mismo agujero que el código que prueba —cubre lo que alguien se acordó de
 * escribir—, y por ahí se coló P-128: `StubbornBlocks`, la más frecuente de las cuatro (1 de cada
 * 7), no excluía a nadie ni nadie la excluía, así que **seis niveles llevaban dos mecánicas
 * encima** (77, 154 y 231 con Horizonte; 91, 182 y 273 con Corriente).
 *
 * Este test no comprueba pares: recorre **los 293 niveles** y afirma la matriz entera. Cuando entre
 * la quinta mecánica de variedad bastará con añadirla a [mecanicas] — y si su autor olvida la
 * exclusión, esto cae con el número de nivel exacto en el mensaje.
 */
class VarietyMechanicMatrixTest {

    /** Las mecánicas de variedad del mapa, con su predicado de activación. */
    private val mecanicas: Map<String, (Int) -> Boolean> = mapOf(
        "Gravedad"   to { l: Int -> BoardGravity.appliesTo(l) },
        "Horizonte"  to { l: Int -> HorizonTray.appliesTo(l) },
        "Corriente"  to { l: Int -> BoardCurrent.appliesTo(l) },
        "Bola demoledora" to { l: Int -> WreckingBallRules.appliesTo(l) }
    )

    private val niveles = 1..LevelMap.totalLevels

    @Test
    fun `ningun nivel lleva dos mecanicas de variedad`() {
        val choques = niveles.mapNotNull { level ->
            val activas = mecanicas.filterValues { it(level) }.keys
            if (activas.size > 1) "nivel $level: ${activas.sorted().joinToString(" + ")}" else null
        }
        assertEquals(
            "Hay niveles con más de una mecánica de variedad. Si el solape es DELIBERADO, " +
                "declárelo aquí en vez de borrar el test:\n" + choques.joinToString("\n"),
            emptyList<String>(), choques
        )
    }

    @Test
    fun `las cuatro siguen apareciendo lo bastante como para notarse`() {
        // La red de P-128 se paga con apariciones perdidas, y una mecánica que casi no sale es una
        // mecánica que no existe. Este suelo es la contrapartida del test de arriba: si una futura
        // exclusión deja a alguna en cuatro niveles de 293, algo se ha ido de las manos.
        mecanicas.forEach { (nombre, activa) ->
            val cuantos = niveles.count { activa(it) }
            assertTrue(
                "«$nombre» solo aparece en $cuantos niveles de ${LevelMap.totalLevels}: " +
                    "las exclusiones se la están comiendo.",
                cuantos >= 10
            )
        }
    }

    @Test
    fun `ninguna mecanica de variedad pisa la intro ni el tutorial`() {
        val introYTutorial = niveles.filter {
            it <= LevelMap.introLevels || TutorialCampaign.isTutorialLevel(it)
        }
        introYTutorial.forEach { level ->
            val activas = mecanicas.filterValues { it(level) }.keys
            assertTrue(
                "El nivel $level es de intro/tutorial y lleva ${activas.joinToString()}: ahí se " +
                    "enseñan las reglas base, no se añaden reglas nuevas.",
                activas.isEmpty()
            )
        }
    }

    @Test
    fun `los seis niveles de P-128 quedaron con una sola mecanica`() {
        // Los que destapó la auditoría, clavados: si alguien deshace la exclusión de StubbornBlocks
        // sin querer, el fallo dice exactamente cuál volvió a romperse.
        listOf(77, 154, 231, 91, 182, 273).forEach { level ->
            val activas = mecanicas.filterValues { it(level) }.keys
            assertTrue(
                "El nivel $level volvió a llevar dos mecánicas (${activas.joinToString()}): " +
                    "es uno de los seis que P-128 arregló.",
                activas.size <= 1
            )
        }
    }
}
