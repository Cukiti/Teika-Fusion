package com.cukiti.teikafusion.model

import kotlin.random.Random

/**
 * CONSEJOS DE LA PANTALLA DE CARGA (P-373). La carga dura poco, así que cada consejo tiene que
 * leerse de un vistazo: una idea, dos líneas como mucho en un móvil estrecho ([MAX_CHARS]).
 *
 * ⚠ Deben describir reglas REALES del juego, igual que `mechanicHint()`: si cambia una mecánica
 * y su consejo miente, se borra el consejo. Nada de biomas concretos (cambian con el capítulo) ni de
 * cifras de economía.
 */
object LoadingTips {

    /** Por encima de esto el consejo ya no cabe en dos líneas bajo la barra. */
    const val MAX_CHARS = 90

    val all: List<String> = listOf(
        "Dos Teikas iguales que se tocan se funden en uno más grande.",
        "Los Teikas cuentan como comodín para completar líneas.",
        "Los Teikas nacen al romper bloques Teika en una línea.",
        "Rompe 2 líneas con 2 bloques Teika a la vez y el Teika puede nacer más grande.",
        "Fusiona hasta el MEGA Teika y limpiarás el tablero de golpe.",
        "Los Teikas grandes ocupan mucho sitio: fúndelos antes de que te encierren.",
        "Cada bioma avisa antes de actuar. Mira el tablero durante el aviso.",
        "Puedes llevar hasta 3 compañeros por partida. Lánzalos desde el rayo.",
        "Cuando ya no cabe ninguna pieza, un compañero puede abrirte hueco.",
        "Los Teikas ruedan y caen: piensa dónde terminarán antes de soltar la pieza."
    )

    /** Orden de una sesión de carga: barajado, sin repetir hasta agotarlos. */
    fun sessionOrder(random: Random = Random.Default): List<String> = all.shuffled(random)
}
