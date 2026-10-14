package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Reglas de la racha de combo.
 *
 * Los dos tests que de verdad importan:
 *  · [romperLineasEnTurnosConsecutivosEncadena] — la racha se quedó sin alimentarse del line-clear
 *    durante la migración de motor y nadie lo detectó.
 *  · [tras_la_gracia_romper_linea_CONTINUA_la_racha_no_la_reinicia] — el bug del playtest 2026-07-20:
 *    la racha se cortaba en la colocación siguiente a la gracia, así que al volver a romper línea el
 *    contador arrancaba de cero.
 *
 * Modelo del turno (idéntico al de `GameViewModel.placePiece`):
 *   1. colocar ficha → NO toca la racha;
 *   2. la cadena rompe línea / fusiona → [ComboRules.onActivity];
 *   3. al cerrar el turno, si NO hubo actividad → [ComboRules.onTurnEndWithoutActivity].
 */
class ComboRulesTest {

    /** Un turno completo: coloca, opcionalmente rompe, y cierra. Espeja placePiece. */
    private fun turn(state: ComboState, activity: Boolean): Pair<ComboState, Boolean> =
        if (activity) ComboRules.onActivity(state.streak) to false
        else ComboRules.onTurnEndWithoutActivity(state.streak, state.grace)
            .let { it.state to it.saved }

    // ── Escalones visuales ───────────────────────────────────────────────────────

    @Test
    fun `el primer eslabon no es todavia un combo`() {
        assertEquals(0, ComboRules.tierOf(0))
        assertEquals(0, ComboRules.tierOf(1))
    }

    @Test
    fun `el escalon sube con la racha y hace techo en MAX_TIER`() {
        assertEquals(1, ComboRules.tierOf(2))
        assertEquals(8, ComboRules.tierOf(9))
        assertEquals(ComboRules.MAX_TIER, ComboRules.tierOf(10))
        assertEquals(ComboRules.MAX_TIER, ComboRules.tierOf(50))   // no se desborda
    }

    // ── Ciclo de vida de la racha ────────────────────────────────────────────────

    @Test
    fun `la actividad util sube un escalon y devuelve la gracia`() {
        val c = ComboRules.onActivity(streak = 3)
        assertEquals(4, c.streak)
        assertEquals(0, c.grace)
    }

    @Test
    fun `un turno flojo sin racha no arranca ninguna ni anuncia gracia`() {
        val end = ComboRules.onTurnEndWithoutActivity(streak = 0, grace = 0)
        assertEquals(0, end.state.streak)
        assertEquals(0, end.state.grace)
        assertEquals(false, end.saved)
    }

    @Test
    fun `un turno flojo con racha viva la mantiene gastando la gracia y avisa`() {
        val end = ComboRules.onTurnEndWithoutActivity(streak = 3, grace = 0)
        assertEquals(3, end.state.streak)
        assertEquals(1, end.state.grace)   // gracia gastada
        assertEquals(true, end.saved)      // "⚠ GRACIA ×3"
    }

    @Test
    fun `un segundo turno flojo seguido corta la racha y no avisa`() {
        val end = ComboRules.onTurnEndWithoutActivity(streak = 3, grace = 1)
        assertEquals(0, end.state.streak)
        assertEquals(0, end.state.grace)
        assertEquals(false, end.saved)     // no salvó: murió
    }

    // ── Regresión: el bucle real de la partida ───────────────────────────────────

    /**
     * REGRESIÓN (B-02): romper línea en turnos consecutivos DEBE encadenar. Antes el line-clear no
     * tocaba la racha, así que esto se quedaba clavado en 0 partida tras partida.
     */
    @Test
    fun romperLineasEnTurnosConsecutivosEncadena() {
        var st = ComboState(0, 0)
        repeat(4) { st = turn(st, activity = true).first }
        assertEquals(4, st.streak)
        assertEquals(3, ComboRules.tierOf(st.streak))   // escalón > 0 → ComboText + playCombo
    }

    /**
     * **EL TEST DEL BUG (playtest 2026-07-20).** El jugador encadena 4, falla UN turno (sale el aviso
     * de gracia) y al turno siguiente vuelve a romper línea: la racha debe CONTINUAR en 5, no
     * reiniciarse. Antes, `onPlacement` la cortaba a 0 en esa colocación —antes de que la cadena
     * corriera— así que la línea contaba desde cero y el aviso de gracia quedaba en mentira.
     */
    @Test
    fun `tras la gracia romper linea CONTINUA la racha no la reinicia`() {
        var st = ComboState(0, 0)
        repeat(4) { st = turn(st, activity = true).first }
        assertEquals(4, st.streak)

        // Turno flojo: la gracia lo salva y se anuncia.
        val (afterIdle, saved) = turn(st, activity = false)
        st = afterIdle
        assertEquals(true, saved)
        assertEquals(4, st.streak)

        // Turno siguiente CON línea rota: continúa desde 4 → 5. Aquí es donde volvía a 1.
        st = turn(st, activity = true).first
        assertEquals(5, st.streak)
        assertEquals(0, st.grace)   // la actividad devuelve la gracia
    }

    /** La gracia perdona UN turno flojo, no dos. */
    @Test
    fun laGraciaPerdonaUnSoloTurnoFlojo() {
        var st = ComboState(0, 0)
        repeat(2) { st = turn(st, activity = true).first }
        assertEquals(2, st.streak)

        st = turn(st, activity = false).first
        assertEquals(2, st.streak)   // sobrevive

        st = turn(st, activity = false).first
        assertEquals(0, st.streak)   // segundo flojo seguido: se corta
    }

    /**
     * Tras gastar la gracia, romper línea la DEVUELVE. Sin esto la racha se cortaba en turnos
     * alternos aunque el jugador rompiera línea en todos (el flag se quedaba pegado en 1).
     */
    @Test
    fun laActividadDevuelveLaGraciaGastada() {
        var st = ComboState(streak = 2, grace = 1)   // gracia ya gastada en un turno flojo previo

        st = turn(st, activity = true).first
        assertEquals(3, st.streak)
        assertEquals(0, st.grace)

        // Y por tanto el siguiente turno flojo NO corta la racha (vuelve a haber gracia).
        val (after, saved) = turn(st, activity = false)
        assertEquals(3, after.streak)
        assertEquals(true, saved)
    }

    /** Un turno CON actividad nunca anuncia gracia (encadenando perfecto no salta la alarma). */
    @Test
    fun unTurnoConActividadNoAnunciaGracia() {
        val st = ComboState(streak = 2, grace = 0)
        assertEquals(false, turn(st, activity = true).second)
    }
}
