package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **QUE CADA NIVEL SEA OTRO NIVEL** (2026-08-26f, pedido del usuario).
 *
 * El diagnóstico que lo motivó: `LevelDirector` cicla **cuatro** tipos de nivel, y el capítulo
 * Cristal tiene 18, así que daba **cuatro vueltas al mismo ciclo** — los niveles 6, 10, 14 y 18 eran
 * numéricamente el mismo nivel (mismo cristal, mismas fichas, mismo sesgo de bandeja). Lo único que
 * cambiaba de verdad era la figura de salida.
 *
 * Lo que fija esta prueba no son las siluetas concretas —son contenido y se retocarán— sino las
 * **garantías** de las que depende que la variedad sea variedad y no un generador de niveles rotos.
 */
class LevelVarietyTest {

    private val playable = (LevelMap.introLevels + 1)..LevelMap.totalLevels

    @Test
    fun `los niveles que antes eran identicos ahora son distintos`() {
        // Cuatro niveles del primer capítulo separados por el ciclo del director: mismo tipo
        // (PATRÓN), mismos porcentajes de cristal. Eran el mismo nivel cuatro veces y es el caso que
        // reportó el usuario. Se derivan del arranque del capítulo y no se escriben a mano: al
        // ampliar el tutorial a siete lecciones (2026-09-01) los números se movieron tres puestos.
        val primero = LevelMap.chapterStart[0]
        val antes = listOf(primero + 1, primero + 5, primero + 9, primero + 13)
            .map { LevelVariety.profileFor(it) }
        assertEquals("siguen siendo el mismo nivel", 4, antes.toSet().size)
        assertEquals("y ni siquiera comparten paleta", 4, antes.map { it.palette }.toSet().size)
    }

    @Test
    fun `el perfil es determinista`() {
        playable.forEach { level ->
            assertEquals(LevelVariety.profileFor(level), LevelVariety.profileFor(level))
        }
    }

    @Test
    fun `la paleta nunca deja un color del objetivo fuera del tablero`() {
        // Si un color del objetivo no está en el tablero, solo se consigue por suerte de bandeja.
        // Las paletas geométricas lo rompen con facilidad, por eso `applyPalette` repara.
        playable.forEach { level ->
            val layout = LevelLayouts.get(level) ?: return@forEach
            val pedidos = layout.second.required.keys
            val presentes = layout.first.mapNotNull { it.targetColor }.toSet()
            assertTrue(
                "el nivel $level pide $pedidos y en el tablero solo hay $presentes",
                presentes.containsAll(pedidos)
            )
        }
    }

    @Test
    fun `la variedad no infla la curva de turnos`() {
        // La condición que puso el usuario: variedad, no dificultad. Lo que cuesta (silueta y Teika
        // sembrado) lo cobra BoardClutterCost, así que la media tiene que quedarse donde estaba.
        val turnos = playable.map { LevelBudget.movesForLevel(it) }
        assertTrue("media=${turnos.average()}", turnos.average() in 35.0..41.0)
        assertTrue("máximo=${turnos.max()}", turnos.max() <= 52)
    }
}
