package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **EL COLOR DE LA FICHA DEJA DE SER AZAR PURO** (P-206, 2026-09-06).
 *
 * La cifra que motiva el fichero: con cinco colores al azar y tres ranuras, la probabilidad de que
 * la bandeja **no traiga** un color concreto es `(4/5)³ = 51 %`. En un nivel de celdas de color
 * (P-157c) eso significa que más de la mitad de las bandejas no podían tocar la celda.
 */
class TrayColorRulesTest {

    private val paleta = (0 until 5).toList()

    @Test
    fun `sin nada que corregir la bandeja se queda como estaba`() {
        val colors = listOf(0, 1, 2)
        assertEquals(colors, TrayColorRules.assign(colors = colors, palette = paleta))
    }

    @Test
    fun `el color que el tablero exige aparece en la bandeja`() {
        val out = TrayColorRules.assign(
            colors = listOf(0, 0, 1),
            demanded = listOf(4),
            palette = paleta,
        )
        assertTrue("El color exigido (4) no llegó: $out", out.contains(4))
    }

    @Test
    fun `si el color exigido ya estaba no se toca nada`() {
        val colors = listOf(0, 4, 1)
        assertEquals(colors, TrayColorRules.assign(colors = colors, demanded = listOf(4), palette = paleta))
    }

    @Test
    fun `cuando hay varios colores exigidos entra el mas pedido`() {
        val out = TrayColorRules.assign(
            colors = listOf(0, 1, 2),
            demanded = listOf(3, 4, 4),
            palette = paleta,
        )
        assertTrue("Debería entrar el 4, que lo piden dos celdas: $out", out.contains(4))
    }

    @Test
    fun `un color exigido fuera de la paleta se ignora`() {
        val colors = listOf(0, 1, 2)
        assertEquals(colors, TrayColorRules.assign(colors = colors, demanded = listOf(9), palette = paleta))
    }

    @Test
    fun `ningun color monopoliza la bandeja`() {
        val out = TrayColorRules.assign(colors = listOf(2, 2, 2), palette = paleta)
        val cuentas = out.groupingBy { it }.eachCount()
        assertTrue("Bandeja monocolor: $out", cuentas.values.max() <= TrayColorRules.maxSameColor(3))
    }

    @Test
    fun `dos de tres del mismo color si esta permitido`() {
        // El límite es «no monopolizar», no «todos distintos»: dos iguales dan una decisión de color
        // que tres distintos no dan.
        val colors = listOf(2, 2, 3)
        assertEquals(colors, TrayColorRules.assign(colors = colors, palette = paleta))
    }

    @Test
    fun `al romper el monopolio se prefiere lo que el tablero pide`() {
        val out = TrayColorRules.assign(
            colors = listOf(1, 1, 1),
            demanded = listOf(3),
            palette = paleta,
        )
        assertTrue("El color exigido debería aprovechar el cambio: $out", out.contains(3))
    }

    @Test
    fun `a igualdad se prefiere un color que el jugador no tenga ya delante`() {
        // `avoid` son las fichas que siguen en la bandeja: es contra ellas contra las que el jugador
        // compara lo que entra.
        val out = TrayColorRules.assign(
            colors = listOf(1, 1, 1),
            palette = listOf(0, 1, 2),
            avoid = listOf(0),
            pick = { 0 },
        )
        assertTrue("Debería haber elegido el 2, no el 0 que ya tiene: $out", out.contains(2))
    }

    @Test
    fun `con un solo color en la paleta no hay nada que repartir`() {
        val colors = listOf(3, 3, 3)
        assertEquals(colors, TrayColorRules.assign(colors = colors, palette = listOf(3)))
    }

    @Test
    fun `una bandeja de una o dos fichas no tiene regla de monopolio`() {
        assertEquals(1, TrayColorRules.maxSameColor(1))
        assertEquals(2, TrayColorRules.maxSameColor(2))
        assertEquals(2, TrayColorRules.maxSameColor(3))
        assertEquals(3, TrayColorRules.maxSameColor(5))
        val dos = listOf(2, 2)
        assertEquals(dos, TrayColorRules.assign(colors = dos, palette = paleta))
    }

    @Test
    fun `la bandeja vacia no revienta`() {
        assertEquals(emptyList<Int>(), TrayColorRules.assign(colors = emptyList(), palette = paleta))
    }

    @Test
    fun `el resultado conserva el numero de ranuras`() {
        listOf(1, 2, 3, 5).forEach { n ->
            val out = TrayColorRules.assign(
                colors = List(n) { 0 },
                demanded = listOf(4),
                palette = paleta,
            )
            assertEquals("Con $n ranuras", n, out.size)
            assertTrue("Color fuera de paleta con $n ranuras: $out", out.all { it in paleta })
        }
    }
}
