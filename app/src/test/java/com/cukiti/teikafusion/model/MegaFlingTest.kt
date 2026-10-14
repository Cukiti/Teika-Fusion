package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Coreografía del MEGA TEIKA (rework 2026-07-25, `docs/plan-mega-teika-aparicion.md`).
 *
 * Lo que se blinda aquí es el CONTRATO de feel, no los números finos: **el empuje va acoplado al
 * crecimiento**. Nunca puede volver el patrón viejo de "crecer entero → asentar → entonces empujar",
 * que dejaba al Mega hinchándose encima de un tablero lleno y quieto.
 */
class MegaFlingTest {

    /** Tablero de referencia: 8×8 celdas de 100 px. */
    private val cellPx = 100f
    private val boardPx = cellPx * GameState.GRID_SIZE
    private val halfBoardPx = boardPx / 2f
    private val slackPx = MegaFling.CONTACT_SLACK_CELLS * cellPx

    private fun hitOfCell(x: Int, y: Int): Float {
        val ox = (x + 0.5f) * cellPx
        val oy = (y + 0.5f) * cellPx
        return MegaFling.hitTimeMs(hypot(ox - halfBoardPx, oy - halfBoardPx), halfBoardPx, slackPx)
    }

    // ── Crecimiento ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `el Mega arranca en su escala de pop y llena el tablero al final del crecimiento`() {
        assertEquals(MegaFling.START_SCALE, MegaFling.growScale(0f), 0.001f)
        assertEquals(1f, MegaFling.growScale(MegaFling.GROW_MS), 0.001f)
        assertEquals(1f, MegaFling.growScale(MegaFling.GROW_MS * 3f), 0.001f)
        // EaseOutCubic: a mitad de camino ya ha hecho MÁS de la mitad (irrumpe, no se arrastra).
        assertTrue(MegaFling.growScale(MegaFling.GROW_MS / 2f) > 0.75f)
    }

    // ── Contacto: el radio es lo que dispara a cada item ──────────────────────────────────────

    @Test
    fun `los bloques del centro salen en los primeros frames`() {
        // Requisito de aceptación §7.1: a los 200 ms el centro YA se mueve.
        val centro = hitOfCell(GameState.GRID_SIZE / 2, GameState.GRID_SIZE / 2)
        assertEquals(0f, centro, 0.001f)
        assertTrue(MegaFling.itemProgress(200f, centro) > 0.1f)
    }

    @Test
    fun `las esquinas salen despues que el centro, pero antes de acabar el crecimiento`() {
        val centro  = hitOfCell(GameState.GRID_SIZE / 2, GameState.GRID_SIZE / 2)
        val borde   = hitOfCell(0, GameState.GRID_SIZE / 2)
        val esquina = hitOfCell(0, 0)
        assertTrue(centro < borde)
        assertTrue(borde < esquina)
        // Nadie espera al asentamiento: el radio alcanza hasta la esquina dentro del crecimiento.
        assertTrue(esquina < MegaFling.GROW_MS)
    }

    @Test
    fun `al acabar el crecimiento el tablero visual ya esta limpio o en vuelo`() {
        // §7.3: ≥90 % de los items con progreso > 0.3 cuando el Mega llena el tablero.
        var enVuelo = 0
        var total = 0
        for (y in 0 until GameState.GRID_SIZE) for (x in 0 until GameState.GRID_SIZE) {
            total++
            if (MegaFling.itemProgress(MegaFling.GROW_MS, hitOfCell(x, y)) > 0.3f) enVuelo++
        }
        assertTrue("$enVuelo/$total en vuelo", enVuelo >= (total * 0.9f).toInt())
    }

    // ── Vuelo del item ────────────────────────────────────────────────────────────────────────

    @Test
    fun `el item no se mueve antes de ser alcanzado y termina su vuelo tras PUSH_MS`() {
        val hit = 300f
        assertEquals(0f, MegaFling.itemProgress(0f, hit), 0.0001f)
        assertEquals(0f, MegaFling.itemProgress(hit, hit), 0.0001f)
        assertTrue(MegaFling.itemProgress(hit + MegaFling.PUSH_MS / 2f, hit) > 0.3f)
        assertEquals(1f, MegaFling.itemProgress(hit + MegaFling.PUSH_MS, hit), 0.0001f)
        assertEquals(1f, MegaFling.itemProgress(hit + MegaFling.PUSH_MS * 2f, hit), 0.0001f)
    }

    @Test
    fun `el reloj total cubre el vuelo completo del ultimo item alcanzado`() {
        val ultimo = hitOfCell(0, 0)
        assertTrue(ultimo + MegaFling.PUSH_MS <= MegaFling.CLOCK_END_MS)
    }

    @Test
    fun `el item se desvanece solo en el ultimo tramo del vuelo`() {
        assertEquals(1f, MegaFling.itemFade(0f), 0.0001f)
        assertEquals(1f, MegaFling.itemFade(0.5f), 0.0001f)
        assertTrue(MegaFling.itemFade(0.8f) < 1f)
        assertEquals(0f, MegaFling.itemFade(1f), 0.0001f)
    }

    // ── Ritmo del modo ────────────────────────────────────────────────────────────────────────

    @Test
    fun `Infinito devuelve el control antes que Niveles`() {
        assertTrue(MegaFling.HOLD_INFINITE_MS < MegaFling.HOLD_LEVELS_MS)
        val totalInfinito = MegaFling.GROW_MS + MegaFling.HOLD_INFINITE_MS + MegaFling.FADE_MS
        assertTrue("Infinito no puede sentirse como una espera de 3 s", totalInfinito < 1600f)
    }
}
