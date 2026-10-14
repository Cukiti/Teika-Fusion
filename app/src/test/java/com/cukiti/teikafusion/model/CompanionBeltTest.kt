package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * EL CINTURÓN restringe de verdad, así que sus reglas son reglas de juego, no de UI: si `sanitized`
 * deja pasar un id fantasma, el jugador entra al nivel con una ranura gastada en nadie.
 */
class CompanionBeltTest {

    private val claimed = setOf("cristal", "dormilona", "miedoso", "planta", "fuego")

    @Test
    fun `equipar llena las ranuras en orden y respeta el tope`() {
        var belt = CompanionBelt()
        belt = belt.equip("cristal")!!
        belt = belt.equip("dormilona")!!
        belt = belt.equip("miedoso")!!
        assertEquals(listOf("cristal", "dormilona", "miedoso"), belt.slots)
        assertTrue(belt.isFull)
        assertNull("Un cinturón lleno no admite un cuarto", belt.equip("planta"))
    }

    @Test
    fun `equipar dos veces al mismo no gasta una segunda ranura`() {
        val belt = CompanionBelt().equip("cristal")!!
        assertNull(belt.equip("cristal"))
        assertEquals(1, belt.size)
    }

    @Test
    fun `soltar deja hueco y conserva el orden del resto`() {
        val belt = CompanionBelt(listOf("cristal", "dormilona", "miedoso"))
        val after = belt.unequip("dormilona")
        assertEquals(listOf("cristal", "miedoso"), after.slots)
        assertFalse(after.isFull)
        // Soltar a quien no está no cambia nada.
        assertEquals(after, after.unequip("planta"))
    }

    @Test
    fun `clearSlot vacia por indice y aguanta un indice fuera de rango`() {
        val belt = CompanionBelt(listOf("cristal", "dormilona"))
        assertEquals(listOf("dormilona"), belt.clearSlot(0).slots)
        assertEquals(belt, belt.clearSlot(7))
        assertEquals(belt, belt.clearSlot(-1))
    }

    @Test
    fun `sanitized tira ids desconocidos, no reclamados y duplicados`() {
        val sucio = CompanionBelt(listOf("cristal", "cristal", "no_existe", "galactico", "dormilona"))
        val limpio = sucio.sanitized(claimed)
        // "galactico" existe en el registro pero NO está reclamado en este dispositivo.
        assertEquals(listOf("cristal", "dormilona"), limpio.slots)
    }

    @Test
    fun `sanitized recorta al tope aunque vengan mas de la nube`() {
        val gordo = CompanionBelt(listOf("cristal", "dormilona", "miedoso", "planta", "fuego"))
        assertEquals(CompanionBelt.MAX_SLOTS, gordo.sanitized(claimed).size)
    }

    @Test
    fun `sanitized devuelve la MISMA instancia cuando no hay nada que limpiar`() {
        // Importa: `sanitized` se llama en cada carga y en cada fusión de nube. Si siempre copiara,
        // dispararía escrituras de DataStore y recomposiciones por nada.
        val limpio = CompanionBelt(listOf("cristal", "dormilona"))
        assertTrue(limpio === limpio.sanitized(claimed))
    }

    @Test
    fun `filledFrom completa los huecos sin repetir y sin pasarse`() {
        val belt = CompanionBelt(listOf("cristal"))
        val lleno = belt.filledFrom(listOf("cristal", "dormilona", "miedoso", "planta"))
        assertEquals(listOf("cristal", "dormilona", "miedoso"), lleno.slots)
    }

    @Test
    fun `filledFrom no toca un cinturon ya lleno`() {
        val lleno = CompanionBelt(listOf("cristal", "dormilona", "miedoso"))
        assertTrue(lleno === lleno.filledFrom(listOf("planta", "fuego")))
    }

    @Test
    fun `todo compañero del registro cabe en el cinturon`() {
        // Si algún día se apaga la habilidad de alguien (DISABLED_COMPANION_ABILITIES), `sanitized`
        // lo tirará: esta prueba es la que avisa de que hay que decidir qué hacer con esa ranura.
        val todos = NamedTeikaRegistry.all.map { it.id }.toSet()
        val belt = CompanionBelt(NamedTeikaRegistry.all.take(3).map { it.id })
        assertEquals(3, belt.sanitized(todos).size)
    }
}
