package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato del guard del turno (2026-08-24). Cubre los cuatro casos que importan: dos colocaciones
 * solapadas, el turno que se corta a medias, el `exit` de más y la mordida del perro guardián.
 */
class BoardBusyCounterTest {

    @Test
    fun unTurnoNormal_tomaYSueltaLosMandos() {
        val guard = BoardBusyCounter()
        assertFalse(guard.isBusy)

        assertTrue("entrar desde libre debe anunciar el cambio", guard.enter())
        assertTrue(guard.isBusy)

        assertTrue("salir al último debe anunciar el cambio", guard.exit())
        assertFalse(guard.isBusy)
    }

    @Test
    fun dosColocacionesSolapadas_soloLaUltimaDevuelveLosMandos() {
        val guard = BoardBusyCounter()
        guard.enter()
        // Segunda secuencia sobre el mismo turno (la cadena arranca dentro de la colocación, y una
        // habilidad automática puede entrar detrás). No vuelve a anunciar: ya estaba ocupado.
        assertFalse(guard.enter())
        assertEquals(2, guard.depth)

        // El primero en terminar NO puede devolver los mandos: el tablero sigue moviéndose.
        assertFalse(guard.exit())
        assertTrue(guard.isBusy)

        assertTrue(guard.exit())
        assertFalse(guard.isBusy)
    }

    @Test
    fun turnoFantasma_noPuedeSoltarLosMandosDeOtraColocacion() {
        val guard = BoardBusyCounter()
        guard.enter()   // colocación REAL en curso, con el tablero moviéndose

        // Turno fantasma: la ficha se rechaza en la re-validación dentro del mutex y no llega a
        // tomar nada. Si su `finally` soltara igual, le quitaría los mandos a la de arriba.
        // Por eso el ViewModel solo llama a `exit` si de verdad hizo `enter` (`busyHeld`).
        assertTrue("la colocación real sigue teniendo el tablero", guard.isBusy)
        assertEquals(1, guard.depth)
    }

    @Test
    fun exitHuerfano_noLiberaElTurnoDeOtro() {
        val guard = BoardBusyCounter()
        guard.enter()          // colocación real en curso
        guard.exit()           // su propio cierre
        assertFalse(guard.isBusy)
        // Un `finally` duplicado no debe dejar el contador en negativo (el siguiente turno se
        // quedaría sin poder marcar el tablero como ocupado).
        assertFalse(guard.exit())
        assertEquals(0, guard.depth)
        assertTrue("el turno siguiente tiene que poder tomar los mandos", guard.enter())
    }

    @Test
    fun perroGuardian_liberaYDejaInofensivosLosExitPendientes() {
        val guard = BoardBusyCounter()
        guard.enter()
        guard.enter()   // dos secuencias vivas cuando algo se cuelga

        assertTrue("el watchdog debe anunciar que libera", guard.forceRelease())
        assertFalse(guard.isBusy)
        assertEquals(0, guard.depth)

        // Las secuencias que seguían vivas terminan y llaman a su `finally`: ya no pueden hacer daño.
        assertFalse(guard.exit())
        assertFalse(guard.exit())
        assertFalse(guard.isBusy)

        // Y morder con el tablero libre no anuncia ningún cambio.
        assertFalse(guard.forceRelease())
    }
}
