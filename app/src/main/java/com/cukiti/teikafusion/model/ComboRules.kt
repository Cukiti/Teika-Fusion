package com.cukiti.teikafusion.model

/**
 * Reglas PURAS de la racha de combo, extraídas de `GameViewModel` para poder testearlas.
 *
 * El combo se alimenta de **actividad útil**, que es de dos tipos y ambos valen igual:
 *  · romper líneas (`resolveLineClearPass`) — la mitad "Block Blast" del juego;
 *  · fusionar Teikas (`processContactFusion`) — la mitad "Suika".
 *
 * Cada colocación de ficha CORTA la racha… salvo que quede una ventana de gracia sin gastar, que
 * la mantiene viva un turno más. Así, encadenar exige actividad sostenida pero perdona un turno de
 * preparación.
 *
 * Histórico (no repetir el error): la tabla de escalones vivía inline dentro de la fusión, que
 * durante la migración de motor quedó como la ÚNICA vía que tocaba la racha. Al volver el
 * line-clear como motor único, romper líneas dejó de dar combo y nadie se dio cuenta porque la
 * regla no estaba en un sitio con nombre ni tenía tests. De ahí este archivo.
 */
object ComboRules {

    /** Escalón visual máximo de [GameEvent.ComboText] (palabra/color: ¡Bien! → LEGENDARIO). */
    const val MAX_TIER = 9

    /**
     * Escalón visual a partir de la racha: 0 = sin combo (racha de 1 o ninguna), 1..[MAX_TIER].
     * Solo se muestra `ComboText` cuando el escalón es > 0 — el primer eslabón no es un combo.
     */
    fun tierOf(streak: Int): Int = (streak - 1).coerceIn(0, MAX_TIER)

    /**
     * Actividad útil (línea rota o fusión): la racha sube UN escalón y se DEVUELVE la gracia.
     *
     * Devolver la gracia es imprescindible: si no, tras gastarla una vez el flag se quedaba pegado
     * y la racha se cortaba en turnos alternos aunque el jugador rompiera línea en todos.
     *
     * Sube un escalón por *turno con actividad*, no por línea: romper 4 líneas de golpe ya se premia
     * con puntos, sacudida y `ChainPulse`; el combo mide **constancia**, no tamaño.
     */
    fun onActivity(streak: Int): ComboState = ComboState(streak = streak + 1, grace = 0)

    /**
     * Cierre de un turno **SIN actividad** (ni línea rota ni fusión). Es el ÚNICO punto donde la
     * racha puede morir:
     *  · con gracia sin gastar → la racha SOBREVIVE y se gasta la gracia ([saved] = true → "⚠ GRACIA").
     *  · con la gracia ya gastada (o sin racha) → se corta a cero.
     *
     * Histórico (bug 2026-07-20, el que esto arregla): esto se evaluaba en `placePiece`, **antes**
     * de resolver la cadena, con `onPlacement`. Secuencia que lo rompía: racha 4 → turno ocioso
     * (gracia gastada, sale el aviso, racha sigue en 4) → turno siguiente el jugador SÍ rompe línea,
     * pero al colocar `onPlacement(4, grace=1)` ya había cortado la racha a 0, así que la línea
     * contaba desde cero. El jugador veía el aviso de gracia y aun así perdía la racha al reanudar.
     * La decisión ahora se toma con el turno CERRADO, cuando ya se sabe si hubo actividad.
     */
    fun onTurnEndWithoutActivity(streak: Int, grace: Int): ComboEnd =
        if (streak > 0 && grace == 0) ComboEnd(ComboState(streak = streak, grace = 1), saved = true)
        else ComboEnd(ComboState(streak = 0, grace = 0), saved = false)
}

/** Resultado de cerrar un turno sin actividad: nuevo estado + si la gracia salvó la racha. */
data class ComboEnd(val state: ComboState, val saved: Boolean)

/** Par (racha, gracia) resultante de aplicar una regla de [ComboRules]. */
data class ComboState(val streak: Int, val grace: Int)
