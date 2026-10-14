package com.cukiti.teikafusion.model

/**
 * **FAMILIA B DE LAS REGLAS DE LA CASA: CÓMO TE LLEGAN LAS FICHAS** (2026-09-03, aprobada por el
 * usuario en el panel del catálogo).
 *
 * Las seis reglas de este fichero no tocan el tablero: cambian **el repartidor**. Es la familia que
 * más cambia la sensación de jugar sin mover una sola celda, y por eso viven juntas — todas pasan
 * por el mismo sitio (`PieceTrayPlanner` y el reparto de `GameViewModel`) y todas comparten la misma
 * frontera que nadie puede cruzar:
 *
 * ## LA LÍNEA QUE NINGUNA CRUZA
 *
 * **La bandeja siempre tiene que admitir una jugada.** No es una preferencia de diseño: es la
 * lección que costó los 14 niveles del damero (P-53). `PieceTrayPlanner` reserva una colocación
 * distinta para cada ficha que reparte y `LevelOpening.admitsMixedTray` lo certifica sobre los 293
 * niveles. Una regla de esta familia puede cambiar **cuántas** fichas ves, **en qué orden** las
 * juegas o **cuándo** llegan — nunca puede dejarte sin jugada, porque una derrota del repartidor no
 * es una derrota del jugador.
 *
 * Por eso ninguna de las seis filtra el catálogo de formas. La que más se acercaba (el repetidor, retirado)
 * repite una forma que el planificador **ya certificó** que cabe.
 *
 * ## Los diales de capítulo
 *
 * Cada una tiene el suyo, ligado a [HouseRule.intensityFor] — 0 en el primer bioma, 1 en el último.
 * Donde «más difícil» no significaba nada por sí solo (la reposición instantánea da MÁS opciones, no
 * menos), el dial mueve la magnitud que de verdad le corresponde a la mecánica en vez de inventarse
 * un castigo: ver el KDoc de cada objeto.
 *
 * (La séptima de la familia, **la ficha espejo**, vive en [MirrorPiece]: es la única que no toca el
 * repartidor sino la colocación.)
 */

/**
 * **P-142 · LA BANDEJA DE CINCO.**
 *
 * Cinco fichas a la vez en vez de tres. Suena a regalo y en parte lo es —más opciones—, pero cambia
 * el juego de sitio: con tres fichas se resuelve el turno, con cinco se resuelve **la bandeja**, y
 * eso es un problema distinto. Es la regla más «amable» del catálogo a propósito: si todos los
 * niveles apretaran, ninguno apretaría.
 *
 * **El dial va al revés que los demás y es el correcto para esta regla:** cinco cuadros en el primer
 * bioma, **cuatro** en el último. Nunca baja de cuatro — con tres dejaría de ser una regla y sería
 * un nivel normal, y un nivel al que le toca una regla que no se nota es exactamente lo que este
 * catálogo vino a quitar.
 */
object TrayFive {

    const val SLOTS_FIRST_CHAPTER = 5
    const val SLOTS_LAST_CHAPTER = 4

    /** El máximo que la banda de la bandeja puede pintar. La UI reserva sitio para estos cuadros. */
    const val MAX_SLOTS = SLOTS_FIRST_CHAPTER

    fun appliesTo(level: Int): Boolean = false // Retired; Reserva occupies its campaign ordinal.

    /** Cuántos cuadros reparte [level], o `null` si no lleva esta regla. */
    fun slotsFor(level: Int): Int? =
        if (!appliesTo(level)) null
        else HouseRule.dial(level, SLOTS_FIRST_CHAPTER, SLOTS_LAST_CHAPTER)
            .coerceIn(SLOTS_LAST_CHAPTER, MAX_SLOTS)
}

/**
 * **P-143 · LA COLA FORZADA.**
 *
 * Las fichas se juegan **en orden**: primero la de la izquierda, luego la siguiente. Ver las tres no
 * es lo mismo que poder elegir entre las tres, y esa diferencia es toda la mecánica — la bandeja
 * pasa de ser un menú a ser una cola, y planificar deja de ser «cuál me viene mejor ahora» para ser
 * «cómo dejo el tablero para la que viene detrás».
 *
 * **No corre riesgo de dejar sin jugada** y conviene entender por qué: `PieceTrayPlanner` reserva una
 * colocación distinta **para cada** ficha de la bandeja, así que la primera de la cola tiene la suya
 * garantizada igual que las demás. Lo que la cola quita es la elección, no la salida.
 *
 * **El dial:** en los biomas bajos las **dos** primeras se pueden jugar en cualquier orden —la cola
 * existe pero deja respirar—, y en los altos hay que ir de una en una.
 */
object ForcedQueue {

    const val FREE_FIRST_CHAPTER = 2
    const val FREE_LAST_CHAPTER = 1

    fun appliesTo(level: Int): Boolean = HouseRule.isActive(level, HouseRule.FORCED_QUEUE)

    /** Cuántas fichas de la cabeza se pueden jugar en cualquier orden, en [level]. */
    fun freeSlotsFor(level: Int): Int =
        HouseRule.dial(level, FREE_FIRST_CHAPTER, FREE_LAST_CHAPTER)
            .coerceIn(FREE_LAST_CHAPTER, FREE_FIRST_CHAPTER)

    /**
     * ¿Se puede jugar la ficha que ocupa el puesto [index] de la bandeja?
     *
     * [index] es su posición **entre las que quedan**, no su cuadro en pantalla: una ficha colocada
     * libera su sitio en la cola, así que la segunda pasa a ser la primera.
     */
    fun allows(level: Int, index: Int): Boolean =
        !appliesTo(level) || index < freeSlotsFor(level)

    /**
     * Las fichas de [tray] que la cola deja jugar AHORA, en su orden. Es la pregunta que tiene que
     * hacerse cualquiera que juzgue «¿queda jugada?».
     *
     * **Bug encontrado por el bot de niveles (2026-09-22):** la garantía de arriba («la cabeza tiene
     * su hueco reservado») vale **al repartir**, pero entre el reparto y la jugada los biomas mueven
     * el tablero —el cúmulo galáctico, la lava, los muros del Rey, el corte ninja—, igual que en C-2.
     * `hasAnyLegalPlacement` probaba TODA la bandeja, así que con la cabeza sin sitio y la segunda
     * con sitio, el juego no declaraba derrota y el jugador no podía jugar nada: soltaba la ficha y
     * no pasaba nada, sin cartel ni revivir. Salida solo por el menú de pausa.
     */
    fun <T> playable(level: Int, tray: List<T>): List<T> =
        if (!appliesTo(level)) tray else tray.take(freeSlotsFor(level))
}

/**
 * **P-144 · LA REPOSICIÓN INSTANTÁNEA.**
 *
 * La ficha que gastas se repone **al momento**, en vez de esperar a que la bandeja se vacíe. Cambia
 * el ritmo entero de la partida: desaparece el ciclo de tres turnos —«planifico las tres, las juego,
 * me reparten»— y el jugador siempre tiene la bandeja llena. Es la contraria exacta de la cola
 * forzada, y por eso son dos reglas y no una: una quita elección, la otra la renueva sin parar.
 *
 * (2026-09-26) El empujón de tamaño que subía con el capítulo se retiró: el tamaño de lo que entra lo
 * decide solo el tablero, y tiene que caber junto a lo que queda en la mano (`PieceTrayPlanner`).
 */
object InstantRefill {

    fun appliesTo(level: Int): Boolean = HouseRule.isActive(level, HouseRule.INSTANT_REFILL)

}

/**
 * **P-145 · LA FICHA TAPADA.**
 *
 * La última ficha de la bandeja llega **boca abajo** y se destapa al gastar una de las anteriores.
 * El jugador sigue teniendo tres, pero solo puede planificar con dos: la tercera es una promesa.
 *
 * Es deliberadamente lo contrario del horizonte (P-75), y por eso las dos merecen existir: el
 * horizonte te enseña el futuro y te quita el presente (una sola jugable); la tapada te deja el
 * presente y te esconde una parte. Las dos hacen la misma pregunta —«¿cuánto puedes planear?»— desde
 * los dos lados.
 *
 * **El dial:** una tapada en los primeros biomas, **dos** en los últimos. Nunca todas: una bandeja
 * entera a ciegas no es planificar peor, es no planificar, y además dejaría al jugador sin poder
 * saber si le queda jugada — que es la línea que esta familia no cruza.
 */
object FaceDownTray {

    const val HIDDEN_FIRST_CHAPTER = 1
    const val HIDDEN_LAST_CHAPTER = 2

    fun appliesTo(level: Int): Boolean = false // Retired; Rutas never hides playable pieces.

    /** Cuántas fichas del final de la bandeja llegan tapadas en [level]. */
    fun hiddenFor(level: Int): Int =
        if (!appliesTo(level)) 0
        else HouseRule.dial(level, HIDDEN_FIRST_CHAPTER, HIDDEN_LAST_CHAPTER)
            .coerceIn(HIDDEN_FIRST_CHAPTER, HIDDEN_LAST_CHAPTER)

    /**
     * ¿Está tapada la ficha del puesto [index] con [trayCount] fichas repartidas y [remaining] sin
     * jugar?
     *
     * Se tapan **las últimas**, y se destapan según se gastan las de delante: con tres fichas y una
     * tapada, la tercera se ve en cuanto queden dos. Así la promesa se cumple sola y el jugador nunca
     * llega a la última ficha sin haberla visto — llegar a jugar a ciegas sería otra mecánica.
     */
    fun isHidden(level: Int, index: Int, trayCount: Int, remaining: Int): Boolean {
        if (!appliesTo(level)) return false
        val hidden = hiddenFor(level).coerceAtMost(trayCount - 1).coerceAtLeast(0)
        if (hidden <= 0) return false
        // Cuántas siguen tapadas ahora mismo: una menos por cada ficha ya jugada de esta bandeja.
        val used = (trayCount - remaining).coerceAtLeast(0)
        val stillHidden = (hidden - used).coerceAtLeast(0)
        return index >= trayCount - stillHidden
    }
}

/* (RETIRADO 2026-09-22) `TrayDiscard` — EL DESCARTE (P-146). Lo sustituyen LAS FICHAS QUE SE
 * FUNDEN (P-232): en vez de tirar una ficha, dos gemelas se convierten en una grande. Ver
 * [PieceFusion]. */

/* (RETIRADO 2026-09-22) `RepeaterTray` — EL REPETIDOR (P-148). Lo sustituye EL MOLDE (P-233):
 * en vez de repetir una forma, el repartidor garantiza la forma que encaja en el molde. Ver
 * [MoldRule]. */
