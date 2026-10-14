package com.cukiti.teikafusion.model

/**
 * **P-75 · BANDEJA DE UNA, CON HORIZONTE** (2026-09-01, aprobada por el usuario).
 *
 * Uno de cada [EVERY] niveles reparte **una ficha a la vez** y enseña las [HORIZON] siguientes. Es
 * el mismo motor y el mismo tablero, pero la pregunta cambia de raíz: con tres fichas sobre la mesa
 * el jugador busca cuál de las tres encaja *ahora*; con una y un horizonte, tiene que preparar el
 * tablero para lo que **va a venir**. De tetris a ajedrez sin tocar una regla del tablero.
 *
 * ## Lo que hace que sea justo
 *
 * `PieceTrayPlanner` garantiza que existe una colocación para cada ficha **al repartirla**, no tres
 * turnos después: la ficha que hoy entra al final de la cola se planifica contra el tablero de hoy y
 * se juega contra otro. Por eso la cabeza de la cola se **comprueba al salir** ([guardHead], al
 * cerrar el turno en `GameViewModel.guardHorizonHead`) y, si ya no cabe en ninguna parte, se cambia
 * por una que sí. Es la
 * misma cautela con la que P-77 condiciona el crecimiento de una ficha, y viene de la misma lección
 * (P-53): una bandeja sin jugada es una derrota por culpa del repartidor, no del jugador.
 *
 * ## Lo que se apaga en estos niveles, y por qué
 *
 *  - **La ficha que crece (P-77)**: crece la que NO usas este turno, y con una sola ficha estás
 *    obligado a usarla. La apuesta no existiría.
 *  - **Las fichas gemelas (P-76)**: son dos ranuras con la misma forma. Con una ranura no hay pareja.
 *  - **La Brocha (P-70) y la Camaleón (P-72)** siguen, pero su cadencia se multiplica por
 *    [PLAYABLE_NORMAL]: se cuenta por reposiciones, y aquí se repone en CADA colocación en vez de
 *    cada tres. Sin ese ajuste saldrían el triple de a menudo y dejarían de ser un premio.
 */
object HorizonTray {

    /**
     * Cuántas fichas del futuro se enseñan en el **primer** capítulo, y en el **último**.
     *
     * Dial de capítulo de esta regla (2026-09-03, ver [HouseRule.intensityFor]). Tres es lo que cabe
     * en la banda sin encoger el tablero, y es lo que ve el jugador cuando conoce el horizonte por
     * primera vez: con tres fichas a la vista, repartir de una en una es un puzzle de orden, no de
     * suerte. En el último bioma solo ve **una**, y entonces la misma regla se convierte en lo que
     * de verdad promete su nombre — jugar mirando al frente sin saber qué viene detrás.
     *
     * Nunca baja de 1: sin horizonte no hay horizonte, solo una bandeja de una ficha a ciegas, y eso
     * es otra mecánica (y una peor).
     */
    const val HORIZON_FIRST_CHAPTER = 3
    const val HORIZON_LAST_CHAPTER = 1

    /** El máximo que la banda puede pintar. La UI reserva sitio para estos cuadros. */
    const val HORIZON = HORIZON_FIRST_CHAPTER

    /** Fichas del futuro que enseña [level]. Ver [HORIZON_FIRST_CHAPTER]. */
    fun horizonFor(level: Int): Int =
        HouseRule.dial(level, HORIZON_FIRST_CHAPTER, HORIZON_LAST_CHAPTER).coerceIn(1, HORIZON)

    /** Fichas jugables a la vez en un nivel con horizonte. */
    const val PLAYABLE = 1

    /** Y las de un nivel normal — los tres cuadros de siempre. */
    const val PLAYABLE_NORMAL = 3

    /**
     * ¿[level] reparte de una en una? La intro y el tutorial nunca: ahí se aprende a jugar.
     *
     * **Y una regla de la casa por nivel** (2026-09-02): si el nivel ya cambia las reglas con la
     * gravedad (P-80), el horizonte se aparta. La corriente (P-79) ya se apartaba de las dos, pero
     * entre estas dos no había nada escrito: hoy no coinciden en ninguno de los 293 niveles, pero
     * solo por cómo caen los números —`LevelMap.chapters` es la fuente de verdad de la progresión y
     * está pensada para cambiar, y en cuanto cambie el reparto por capítulos se mueve entero—. Manda
     * la gravedad porque es la más rara de las tres: hay **una por capítulo**, y quitarle la suya a
     * un capítulo lo deja sin ella; el horizonte pierde uno de sus veintiséis.
     */
    fun appliesTo(level: Int): Boolean = HouseRule.isActive(level, HouseRule.HORIZON)

    /** Los niveles con horizonte de toda la campaña, en orden. Para tests y censos. */
    fun levels(): List<Int> = (1..LevelMap.totalLevels).filter(::appliesTo)

    /** Cuántas fichas nuevas se prueban antes de rendirse: el tablero está lleno de verdad. */
    const val HEAD_SWAP_ATTEMPTS = 8

    /**
     * La ficha que se queda en la mano (2026-09-25): [head] si [fits]; si no, la primera de hasta
     * [HEAD_SWAP_ATTEMPTS] fichas de [candidate] que quepa; y si ninguna cabe, [head] — no cambiar
     * nada es la respuesta honesta a un tablero sin sitio. Nunca regala una ficha mejor a quien ya
     * tenía jugada.
     */
    fun guardHead(head: Piece, fits: (Piece) -> Boolean, candidate: () -> Piece?): Piece {
        if (fits(head)) return head
        repeat(HEAD_SWAP_ATTEMPTS) {
            val next = candidate() ?: return head
            if (fits(next)) return next
        }
        return head
    }

    /**
     * Saca la cabeza de la cola y mete [fresh] por el final.
     *
     * Devuelve `null` como cabeza si la cola estaba vacía (guardado anterior a P-75): quien llama
     * rellena entonces la cola entera. La cola nunca crece por encima de [HORIZON] — lo que se
     * enseña y lo que hay son la misma cosa, o el horizonte sería una promesa a medias.
     */
    fun advance(horizon: List<Piece>, fresh: List<Piece>, size: Int = HORIZON): Pair<Piece?, List<Piece>> {
        val head = horizon.firstOrNull()
        val rest = if (head == null) horizon else horizon.drop(1)
        return head to (rest + fresh).take(size.coerceIn(1, HORIZON))
    }
}
