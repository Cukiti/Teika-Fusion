package com.cukiti.teikafusion.model

/**
 * **EL PREMIO DE LA BANDEJA** (2026-09-26, decisión del usuario).
 *
 * Una colocación que rompe 2+ líneas a la vez se celebra en la bandeja que llega después: brilla en
 * oro y suena (`GameEvent.TrayRewardDealt`, cápsula `TRAY_REWARD`).
 *
 * Hasta el mismo 2026-09-26 el premio también cambiaba el REPARTO: con premio la bandeja se re-sorteaba
 * hasta caber junta, y sin premio se repartía «suelta». Eso se retiró con el reparto nuevo
 * (`PieceTrayPlanner`): TODA bandeja cabe junta y trae lo más grande que quepa, así que el premio ya
 * solo es el anuncio de la jugada.
 *
 * **Desde P-316 (2026-09-27) vuelve a tocar el reparto**, pero solo el TAMAÑO: la bandeja premiada
 * compite entre los dos mayores tamaños que caben (`PieceTrayPlanner.plan(rewarded = true)`), así que
 * trae fichas un escalón más grandes que la normal. Y desde **P-310** el premio es de la FICHA: solo
 * cuentan las líneas que atraviesan un bloque de la ficha colocada ([linesWithPiece]); las que rompe
 * la cascada por su cuenta, un castigo de bioma o una regla de la casa no suman.
 *
 * Puro y sin Android: lo prueba `TrayRewardTest`.
 */
object TrayReward {

    /** Sin premio. */
    const val NONE = 0

    /** Doble línea. */
    const val DOUBLE = 2

    /** Triple o más. */
    const val TRIPLE = 3

    /** El premio que merece una colocación que rompió [lines] líneas a la vez. */
    fun forLines(lines: Int): Int = when {
        lines >= 3 -> TRIPLE
        lines == 2 -> DOUBLE
        else -> NONE
    }

    /** Cuántas de las [lines] rotas (cada una, sus casillas) llevan al menos una casilla de la ficha. */
    fun linesWithPiece(lines: List<Collection<Position>>, isPiece: (Position) -> Boolean): Int =
        lines.count { line -> line.any(isPiece) }
}
