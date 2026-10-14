package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de la MUDANZA DE LA BOBINA (2026-09-05).
 *
 * El atasco que la trajo: un Teika que nace sobre una bobina se quedaba encajado para siempre,
 * porque la bobina es terreno NO rompible y la red anti-encaje de la física solo actúa sobre el
 * terreno rompible. Ver el KDoc de [CoilRelocation].
 */
class CoilRelocationTest {

    private val W = GameState.GRID_SIZE

    private fun madera(
        color: Int = 1,
        coil: Int = 0,
        armor: Int = 0,
        stubborn: Boolean = false,
    ) = CellContent.Wood(colorType = color, coil = coil, armor = armor, stubborn = stubborn)

    /** Fila [y] llena de madera lisa, con una bobina [coil] en la columna [coilX]. */
    private fun filaConBobina(y: Int, coilX: Int, coil: Int = 1): MutableMap<Position, CellContent> {
        val g = mutableMapOf<Position, CellContent>()
        for (x in 0 until W) g[Position(x, y)] = madera(coil = if (x == coilX) coil else 0)
        return g
    }

    // ── A dónde se muda ────────────────────────────────────────────────────────────────────────

    /** Se aparta lo justo: la madera lisa más cercana de SU fila, no la primera que aparezca. */
    @Test
    fun `se muda a la madera lisa mas cercana de su fila`() {
        val g = filaConBobina(y = 4, coilX = 5)
        val destino = CoilRelocation.destinationFor(
            grid = g, from = Position(5, 4), width = W,
            blocked = setOf(Position(5, 4)),
        )
        assertTrue("debe quedarse pegada al origen", destino == Position(4, 4) || destino == Position(6, 4))
    }

    /** Nunca sale de su fila: el número ES el puesto en la secuencia de ESA fila. */
    @Test
    fun `no se muda a otra fila`() {
        val g = mutableMapOf<Position, CellContent>()
        g[Position(3, 2)] = madera(coil = 2)           // la bobina, sola en su fila
        for (x in 0 until W) g[Position(x, 5)] = madera()  // fila entera de madera lisa, pero es OTRA
        val destino = CoilRelocation.destinationFor(
            grid = g, from = Position(3, 2), width = W,
            blocked = setOf(Position(3, 2)),
        )
        assertNull("sin sitio en su fila, no hay mudanza", destino)
    }

    /** Las celdas que el cuerpo reclama no son destino: mudarse ahí sería el mismo atasco. */
    @Test
    fun `no se muda a una celda reclamada por el cuerpo`() {
        val g = filaConBobina(y = 0, coilX = 3)
        val reclamadas = setOf(Position(2, 0), Position(3, 0), Position(4, 0))
        val destino = CoilRelocation.destinationFor(
            grid = g, from = Position(3, 0), width = W, blocked = reclamadas,
        )
        // x=1 y x=5 están a la misma distancia (2); el desempate por x menor la deja en (1,0).
        assertEquals(Position(1, 0), destino)
    }

    // ── Qué NO puede acoger una bobina (una marca por celda) ───────────────────────────────────

    /**
     * Mismo criterio que la energización (`plainWoodInRow` en `resolveLineClearPass`): si divergieran,
     * la mudanza pondría una bobina donde el bioma nunca la habría puesto — que es exactamente el
     * bug de las DOS marcas en la misma celda que P-131 tuvo que arreglar.
     */
    @Test
    fun `no acogen bobina el cristal, el testarudo, el comodin ni otra bobina`() {
        val y = 1
        val g = mutableMapOf<Position, CellContent>()
        g[Position(0, y)] = madera(coil = 1)                         // la que se muda
        g[Position(1, y)] = madera(armor = 1)                        // cristal
        g[Position(2, y)] = madera(stubborn = true)                  // testarudo
        g[Position(3, y)] = madera(color = WILDCARD_COLOR)           // comodín
        g[Position(4, y)] = madera(coil = 2)                         // ya es bobina
        g[Position(5, y)] = CellContent.Empty                        // hueco
        g[Position(6, y)] = madera()                                 // ← la única válida
        val libres = CoilRelocation.plainWoodInRow(g, y, W, blocked = setOf(Position(0, y)))
        assertEquals(listOf(Position(6, y)), libres)
    }

    /** Determinista: la misma entrada da siempre el mismo destino (se prueba y se reproduce). */
    @Test
    fun `el destino es determinista`() {
        val g = filaConBobina(y = 7, coilX = 0)
        val bloqueadas = setOf(Position(0, 7))
        val primera = CoilRelocation.destinationFor(g, Position(0, 7), W, bloqueadas)
        repeat(20) {
            assertEquals(primera, CoilRelocation.destinationFor(g, Position(0, 7), W, bloqueadas))
        }
        assertEquals(Position(1, 7), primera)
    }
}
