package com.cukiti.teikafusion.model

/**
 * **TIEMPO BALA** (2026-09-01, pedido del usuario: «cámara lenta y slow motion en los momentos donde
 * lo necesite para que el jugador entienda de manera más eficiente»).
 *
 * Un *shot* es un instante de enseñanza: el juego se ralentiza, todo se apaga menos lo que hay que
 * mirar, y una frase de cuatro o cinco palabras nombra lo que está pasando. No es un cartel: el
 * jugador ve la mecánica ocurriendo, solo que despacio.
 *
 * Lo usan las dos mitades del aprendizaje:
 *
 *  - Las **lecciones** del tutorial, donde el shot remata el momento clave de cada una.
 *  - Las **cápsulas** ([MechanicCapsule]), que enseñan el resto de mecánicas la primera vez que
 *    aparecen de verdad en una partida.
 *
 * Reglas de la casa que respeta por construcción:
 *
 *  - **Nunca es un párrafo.** [label] son cuatro o cinco palabras; el resto lo cuenta el tablero.
 *  - **Nunca bloquea el juego más de [MAX_MS]**, que es el mismo tope que se le puso a los eventos
 *    de bioma el 2026-08-30: en cuanto una explicación se hace larga, deja de explicar.
 *  - **Se apaga con «Reducir efectos»** ([forReducedFx]): quien pidió menos espectáculo recibe el
 *    rótulo y nada más, sin ralentizar ni oscurecer.
 */
data class SlowMotionShot(
    /** Identidad estable: el mismo momento del mismo tipo comparte id (para no repetirlo). */
    val id: String,
    /** Cuatro o cinco palabras. Es un nombre, no una explicación. */
    val label: String,
    /** Qué se mira. */
    val focus: SlowMotionFocus = SlowMotionFocus.BOARD,
    /** Celdas iluminadas cuando [focus] es [SlowMotionFocus.BOARD]. Vacío = el tablero entero. */
    val cells: Set<Position> = emptySet(),
    /**
     * **QUÉ FICHA SEÑALA** cuando [focus] es [SlowMotionFocus.TRAY]. Vacío = la bandeja entera.
     *
     * Es exactamente lo mismo que [cells] hace con el tablero, y existe por el mismo motivo (P-201,
     * 2026-09-05, reportado por el usuario: «cuando el tutorial está dando info sobre una ficha no
     * la señala, señala el panel de fichas completo»).
     *
     * El problema no era cosmético: media docena de cápsulas dicen literalmente **«ESA ficha»** —
     * «Esa ficha viene tapada», «En orden: primero esa», «Si no la usas, crece»— mientras el foco
     * iluminaba los tres cuadros a la vez. Un rótulo que dice «esa» señalando tres es peor que no
     * señalar: el jugador se queda buscando cuál.
     *
     * Se identifican por **id de ficha y no por índice de cuadro**: una ficha colocada deja su hueco
     * vacío y las demás no se recolocan, así que el índice del cuadro y la posición en
     * `availablePieces` no son el mismo número (es el mismo motivo por el que la cola forzada
     * calcula su `queueIdx` aparte).
     */
    val trayPieceIds: Set<String> = emptySet(),
    /** Multiplicador del tiempo de la física. 1 = normal. */
    val factor: Float = 0.22f,
    /**
     * Cuánto dura el instante. **Sube a 2,4 s por defecto el 2026-09-01** (pedido del usuario: «que
     * le dé tiempo de leer»): con 1,4 s la frase apenas se leía una vez, y una explicación que hay
     * que releer no explica — solo interrumpe.
     */
    val durationMs: Int = 2400,
    /**
     * **EL COMPÁS ANTES DE LA FRASE** (2026-09-04, pedido del usuario).
     *
     * Milisegundos que el instante corre **ralentizado y con el foco puesto, pero sin rótulo**. El
     * jugador ve primero la mecánica ocurriendo y solo después lee cómo se llama.
     *
     * El fallo que arregla se vio en la lección 2: el shot se dispara con la línea rompiéndose, así
     * que la ventanita con «Cada fila te deja un Teika» aparecía en el mismo fotograma **encima** de
     * los dos Teikas naciendo y fundiéndose — el jugador leía la frase sin llegar a ver lo que la
     * frase describe. Un rótulo que tapa su propio ejemplo no explica nada.
     *
     * 0 = como siempre (el rótulo entra con el shot). Nunca se come el tiempo de lectura: el compás
     * es ADEMÁS de [durationMs], no dentro.
     */
    val leadInMs: Int = 0,
) {
    init {
        require(factor > 0f) { "Un shot con factor 0 congelaría la física para siempre" }
        require(leadInMs >= 0) { "El compás previo no puede ser negativo" }
    }

    /** Lo que dura el instante ENTERO: el compás mudo más el tiempo de lectura. */
    val totalMs: Int get() = leadInMs + durationMs

    /**
     * La versión de este shot para quien juega con «Reducir efectos»: sin ralentizar ni oscurecer…
     * **pero con el mismo tiempo de lectura**. Recortarlo a la mitad, como hacía hasta el
     * 2026-09-01, castigaba con menos tiempo para leer justo a quien ya había pedido menos
     * espectáculo. Lo que sobra ahí es la parafernalia, no la frase.
     */
    fun forReducedFx(): SlowMotionShot = copy(factor = 1f)

    companion object {
        /**
         * Tope duro. **2000 → 3600 ms el 2026-09-01**, con motivo: el tope se heredó del de 1200 ms
         * de los eventos de bioma (P-62), y ahí medía otra cosa — un castigo ambiental tiene que
         * verse RÁPIDO porque el jugador ya sabe lo que es. Un instante de enseñanza tiene que
         * poder LEERSE, y leer seis palabras en un móvil lleva más de dos segundos.
         */
        const val MAX_MS = 3600
    }
}

/** Dónde está lo que el shot quiere que mires. */
enum class SlowMotionFocus {
    /** Celdas del tablero ([SlowMotionShot.cells]); vacío = el tablero entero. */
    BOARD,
    /** La bandeja de fichas — o solo las de [SlowMotionShot.trayPieceIds], si las declara. */
    TRAY,
    /** El tirador de habilidades, bajo la bandeja. */
    ABILITY_TAB,
    /** La barra de meta / estrellas del objetivo, arriba. */
    GOAL,
    /** El medallón de turnos. */
    MOVES,
    /** El botón del banco de bloques, abajo a la izquierda (P-350). */
    BANK,
    /** Sin foco: solo se ralentiza y se rotula (la pantalla entera es lo que hay que mirar). */
    NONE,
}
