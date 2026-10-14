package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **NINGUNA ESTRELLA MUERTA** (2026-08-26, pedido del usuario).
 *
 * Cuando un color del objetivo llega a su cuota, las estrellas suyas que siguen en el tablero dejan
 * de valer y el jugador se queda mirando celdas que parecen objetivo y no lo son. La regla las
 * retinta al color que más falta, repartiendo por déficit.
 */
class StarObjectiveRetintTest {

    private val objetivo = mapOf(
        TargetColor.RED to 10,
        TargetColor.BLUE to 20,
        TargetColor.PINK to 5,
    )

    @Test
    fun `sin colores cubiertos no hay nada que hacer`() {
        val recogido = mapOf(TargetColor.RED to 3, TargetColor.BLUE to 4)
        assertFalse("ningún color está cubierto todavía", StarObjectiveRetint.hasWork(objetivo, recogido))
        assertEquals(3, StarObjectiveRetint.deficits(objetivo, recogido).size)
    }

    @Test
    fun `con el objetivo entero cubierto tampoco`() {
        val recogido = mapOf(TargetColor.RED to 10, TargetColor.BLUE to 20, TargetColor.PINK to 5)
        assertFalse("no queda destino posible", StarObjectiveRetint.hasWork(objetivo, recogido))
        assertTrue(StarObjectiveRetint.assign(objetivo, recogido, 8).isEmpty())
    }

    @Test
    fun `un objetivo de un solo color nunca retinta`() {
        val uno = mapOf(TargetColor.RED to 10)
        assertFalse(StarObjectiveRetint.hasWork(uno, mapOf(TargetColor.RED to 10)))
    }

    @Test
    fun `el rojo cubierto reparte sus huerfanas entre los pendientes`() {
        val recogido = mapOf(TargetColor.RED to 10, TargetColor.BLUE to 2, TargetColor.PINK to 1)
        assertTrue(StarObjectiveRetint.hasWork(objetivo, recogido))
        assertEquals(setOf(TargetColor.RED), StarObjectiveRetint.satisfiedColors(objetivo, recogido))

        val plan = StarObjectiveRetint.assign(objetivo, recogido, 8)

        assertEquals(8, plan.size)
        assertFalse("un color cubierto no puede volver a salir", TargetColor.RED in plan)
        // Déficits 18 (azul) y 4 (rosa): el reparto por déficit se lo lleva mayoritariamente el azul.
        assertTrue(
            "reparto=$plan",
            plan.count { it == TargetColor.BLUE } > plan.count { it == TargetColor.PINK }
        )
    }

    @Test
    fun `el reparto nunca sobrepasa lo que falta`() {
        val recogido = mapOf(TargetColor.RED to 10, TargetColor.BLUE to 19, TargetColor.PINK to 4)
        // Faltan 1 azul y 1 rosa: por muchas huérfanas que haya, solo se colocan dos.
        val plan = StarObjectiveRetint.assign(objetivo, recogido, 30)
        assertEquals(listOf(TargetColor.BLUE, TargetColor.PINK).sortedBy { it.ordinal }, plan.sortedBy { it.ordinal })
    }

    @Test
    fun `el mismo estado retinta siempre igual`() {
        val recogido = mapOf(TargetColor.RED to 10, TargetColor.BLUE to 2)
        assertEquals(
            StarObjectiveRetint.assign(objetivo, recogido, 6),
            StarObjectiveRetint.assign(objetivo, recogido, 6)
        )
    }

    @Test
    fun `el plan del tablero empareja celda con color en el mismo orden`() {
        val recogido = mapOf(TargetColor.RED to 10, TargetColor.BLUE to 2, TargetColor.PINK to 1)
        val celdas = listOf(Position(0, 0), Position(1, 0), Position(0, 1))

        val plan = StarObjectiveRetint.boardPlan(objetivo, recogido, celdas)

        assertEquals(3, plan.size)
        assertEquals(celdas.toSet(), plan.keys)
        assertEquals(StarObjectiveRetint.assign(objetivo, recogido, 3), celdas.map { plan[it] })
    }
}
