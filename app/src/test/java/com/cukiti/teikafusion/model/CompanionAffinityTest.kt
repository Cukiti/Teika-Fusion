package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La marca de afinidad es un CONSEJO que el juego le da al jugador antes de gastar turnos y cargas.
 * Un consejo que apunta a un compañero inexistente, a una habilidad apagada o a un bioma sin
 * mecánica es peor que no dar ninguno — de ahí esta prueba.
 */
class CompanionAffinityTest {

    private val biomasConMecanica = BiomeType.entries.filter { it != BiomeType.NONE }

    @Test
    fun `todo bioma con mecanica tiene al menos un compañero afin`() {
        val sinAfinidad = biomasConMecanica.filter { CompanionAffinities.forBiome(it).isEmpty() }
        assertTrue("Biomas sin ningún consejo: $sinAfinidad", sinAfinidad.isEmpty())
    }

    @Test
    fun `los niveles de intro no dan consejo`() {
        // BiomeType.NONE son los niveles 1-4: no hay mecánica ambiental que contrarrestar.
        assertTrue(CompanionAffinities.forBiome(BiomeType.NONE).isEmpty())
    }

    @Test
    fun `todo id afin existe en el registro y tiene habilidad viva`() {
        val malos = mutableListOf<String>()
        biomasConMecanica.forEach { biome ->
            CompanionAffinities.forBiome(biome).forEach { af ->
                if (NamedTeikaRegistry.byId(af.companionId) == null) malos += "${biome.name}:${af.companionId} (no existe)"
                if (COMPANION_ABILITIES[af.companionId] == null) malos += "${biome.name}:${af.companionId} (sin habilidad)"
                if (!companionAbilityEnabled(af.companionId)) malos += "${biome.name}:${af.companionId} (habilidad apagada)"
            }
        }
        assertTrue("Afinidades que apuntan a nadie: $malos", malos.isEmpty())
    }

    @Test
    fun `ningun bioma repite compañero`() {
        biomasConMecanica.forEach { biome ->
            val ids = CompanionAffinities.forBiome(biome).map { it.companionId }
            assertEquals("$biome repite un compañero: $ids", ids.size, ids.distinct().size)
        }
    }

    @Test
    fun `la razon cabe bajo la tarjeta`() {
        // La razón se pinta en una línea bajo el retrato. Si crece, se corta con puntos suspensivos
        // y el consejo deja de leerse — mejor que falle aquí que en el móvil del jugador.
        val largas = biomasConMecanica.flatMap { CompanionAffinities.forBiome(it) }
            .filter { it.razon.isBlank() || it.razon.length > MAX_RAZON }
            .map { "${it.companionId}: '${it.razon}' (${it.razon.length})" }
        assertTrue("Razones vacías o de más de $MAX_RAZON caracteres: $largas", largas.isEmpty())
    }

    @Test
    fun `reason e isAffine coinciden y devuelven null fuera de la tabla`() {
        val volcan = CompanionAffinities.forBiome(BiomeType.VOLCANO).first()
        assertNotNull(CompanionAffinities.reason(volcan.companionId, BiomeType.VOLCANO))
        assertTrue(CompanionAffinities.isAffine(volcan.companionId, BiomeType.VOLCANO))
        assertNull(CompanionAffinities.reason("no_existe", BiomeType.VOLCANO))
        assertNull(CompanionAffinities.reason(volcan.companionId, BiomeType.NONE))
    }

    @Test
    fun `la afinidad NO es la tematica del capitulo`() {
        // La decisión fue tabla escrita a mano, no `compañero i ↔ bioma i`. Si alguien "simplifica"
        // el fichero derivándolo del registro, esta prueba lo caza: el compañero de un bioma no
        // tiene por qué ser el bueno contra ese bioma.
        val tematicos = LevelMap.chapters.indices.count { i ->
            val biome = LevelMap.chapters[i].biome
            val propio = NamedTeikaRegistry.all.getOrNull(i)?.id
            propio != null && CompanionAffinities.isAffine(propio, biome)
        }
        assertTrue(
            "Todos los biomas recomiendan a su propio personaje: la tabla se ha derivado del capítulo",
            tematicos < LevelMap.chapters.size
        )
    }

    private companion object {
        const val MAX_RAZON = 46
    }
}
