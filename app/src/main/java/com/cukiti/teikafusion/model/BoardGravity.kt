package com.cukiti.teikafusion.model

/**
 * **P-80 · NIVEL CON GRAVEDAD** (2026-09-01, aprobada por el usuario).
 *
 * Un nivel por capítulo en el que la madera **se asienta hacia abajo**: la ficha que sueltas cae
 * hasta su suelo y, cada vez que una línea se rompe, lo que quedaba encima se desploma. En el resto
 * del juego la madera se queda exactamente donde la puso el jugador —block-blast puro—, así que
 * esto no es una mecánica de bioma: es **otra forma de jugar el mismo tablero**, y por eso sale
 * contada una vez por capítulo y no cada pocos niveles.
 *
 * Este objeto es la regla PURA (sin estado, sin Compose, sin coroutinas): quién cae, hasta dónde, y
 * en qué niveles. El motor ([com.cukiti.teikafusion.viewmodel.GameViewModel.settleBoardGravity]) le
 * pone la animación, el sonido y el reloj.
 *
 * ## Las dos decisiones del usuario, y por qué importan
 *
 *  1. **El Teika es SUELO.** La madera se para encima de un cuerpo en vez de atravesarlo. El motivo
 *     no es estético: toda celda de madera es terreno de la física
 *     (`GameViewModel.physicsTerrainCells`), así que dejar caer madera DENTRO de un cuerpo obliga al
 *     solver a expulsarlo, y un MEGA de 5 celdas de diámetro no tiene por dónde salir. Con el cuerpo
 *     como suelo, el derrumbe nunca crea una penetración que la física tenga que deshacer.
 *     Contrapartida aceptada: si el Teika rueda después, la torre se queda flotando hasta el
 *     siguiente asentamiento (que es el turno siguiente, no un frame perdido).
 *  2. **Se asienta tras COLOCAR y tras LIMPIAR.** Las dos, o el nivel enseñaría madera flotando
 *     desde el turno 1 hasta la primera línea. Como el motor asienta antes de cada pasada de
 *     [com.cukiti.teikafusion.viewmodel.GameViewModel.resolveLineClearPass], la ficha recién puesta
 *     cae ANTES de que se evalúen líneas: nunca se rompe una fila que el desplome iba a deshacer.
 *
 * ## Lo que NO se mueve
 *
 * Media docena de sistemas guardan celdas por [Position] (escarcha, congelado, cúmulo galáctico,
 * astro, pozos del Glotón, muros del Rey, lava, bobinas del circuito). Si la madera se moviera por
 * debajo de ellos, esos conjuntos apuntarían a celdas equivocadas y el estado empezaría a mentir.
 * Todos ellos son **ancla**: no caen y además hacen de suelo para lo que tienen encima. Las marcas
 * del TABLERO —celdas calientes (P-78), portería (P-81)— sí se quedan quietas a propósito: marcan
 * un sitio, no un bloque, así que la madera puede entrar y salir de ellas cayendo.
 */
object BoardGravity {

    /** Un bloque de madera que cae de [from] a [to] (misma columna, siempre hacia abajo). */
    data class Fall(val from: Position, val to: Position)

    /**
     * **HACIA DÓNDE SE ASIENTA LA MADERA** (2026-09-03).
     *
     * La gravedad nació mirando solo al suelo, y con una sola dirección era una regla y no una
     * familia. Las otras tres son la MISMA mecánica leída desde otro borde, y eso es exactamente lo
     * que el usuario pidió: que el jugador se encuentre el tablero comportándose de otra manera sin
     * que la dificultad suba.
     *
     * No hay tres solvers: [computeFalls] resuelve siempre «hacia abajo» y las otras direcciones son
     * un **cambio de coordenadas** de ida y vuelta ([toLocal] / [fromLocal]). Un solver probado con
     * cuatro lecturas vale más que cuatro solvers parecidos, que es el error que este proyecto ya
     * cometió una vez con el motor auto-pop.
     */
    enum class Pull { DOWN, UP, LEFT, RIGHT }

    /**
     * ¿[level] se juega con alguna gravedad?
     *
     * **El reparto lo decide [HouseRule]** (2026-09-03). Antes era «uno por capítulo», elegido con
     * un salto de 7 dentro del capítulo y esquivando a las demás reglas con una cadena de
     * negaciones. Hoy es una FAMILIA de entradas del catálogo de reglas de la casa: sale más a
     * menudo (el usuario pidió que TODOS los niveles lleven una regla) y la exclusión mutua deja de
     * ser una cadena para ser aritmética — el reparto devuelve una sola regla, así que dos no pueden
     * coincidir.
     */
    fun appliesTo(level: Int): Boolean = pullFor(level) != null

    /** La dirección de la gravedad de [level], o `null` si ese nivel no la lleva. */
    fun pullFor(level: Int): Pull? = when (HouseRule.forLevel(level)) {
        HouseRule.GRAVITY -> Pull.DOWN
        HouseRule.INVERTED_GRAVITY -> Pull.UP
        // El lado alterna con el número de nivel: la misma regla no se aprende de una vez y ya.
        HouseRule.SIDE_GRAVITY -> if (level % 2 == 0) Pull.RIGHT else Pull.LEFT
        else -> null
    }

    /** Coordenada de [p] en el tablero girado donde «abajo» es [pull]. Ver [Pull]. */
    private fun toLocal(p: Position, pull: Pull, width: Int, height: Int): Position = when (pull) {
        Pull.DOWN -> p
        Pull.UP -> Position(p.x, height - 1 - p.y)
        Pull.LEFT -> Position(p.y, width - 1 - p.x)
        Pull.RIGHT -> Position(p.y, p.x)
    }

    /** La vuelta de [toLocal]. */
    private fun fromLocal(p: Position, pull: Pull, width: Int, height: Int): Position = when (pull) {
        Pull.DOWN -> p
        Pull.UP -> Position(p.x, height - 1 - p.y)
        Pull.LEFT -> Position(width - 1 - p.y, p.x)
        Pull.RIGHT -> Position(p.y, p.x)
    }

    /** Ancho y alto del tablero girado: las diagonales intercambian los dos. */
    private fun localSize(pull: Pull, width: Int, height: Int): Pair<Int, Int> =
        if (pull == Pull.LEFT || pull == Pull.RIGHT) height to width else width to height

    /**
     * [computeFalls] en la dirección [pull].
     *
     * Gira el tablero, resuelve con el solver de siempre y deshace el giro. Los `Fall` que devuelve
     * están ya en coordenadas del tablero real, así que quien llama no se entera del cambio de ejes
     * — y por eso la animación, el sonido y el reloj del motor valen igual para las cuatro.
     */
    fun computeFalls(
        wood: Set<Position>,
        blocked: Set<Position>,
        width: Int,
        height: Int,
        pull: Pull
    ): List<Fall> {
        if (pull == Pull.DOWN) return computeFalls(wood, blocked, width, height)
        val (lw, lh) = localSize(pull, width, height)
        val falls = computeFalls(
            wood.mapTo(HashSet()) { toLocal(it, pull, width, height) },
            blocked.mapTo(HashSet()) { toLocal(it, pull, width, height) },
            lw, lh
        )
        return falls.map {
            Fall(fromLocal(it.from, pull, width, height), fromLocal(it.to, pull, width, height))
        }
    }

    /** Los niveles con gravedad de toda la campaña, en orden. Para tests y para el mapa. */
    fun levels(): List<Int> = (1..LevelMap.totalLevels).filter(::appliesTo)

    /**
     * Cada bloque de [wood] baja por SU columna hasta la primera celda que no puede pisar.
     *
     * [blocked] son las celdas intocables: obstáculos del tablero (muros, pozos, lava, cuerpos de
     * Teika) y la madera que hace de ancla. Una celda que esté en [wood] **y** en [blocked] se trata
     * como ancla: manda el bloqueo, porque es la que no puede moverse.
     *
     * El recorrido va de abajo arriba con un cursor de "primer hueco libre": así una columna con
     * huecos intercalados se compacta de una pasada y el orden vertical de los bloques se conserva
     * (el de arriba sigue arriba), que es lo que hace legible el desplome. Devuelve solo los que de
     * verdad cambian de celda.
     */
    fun computeFalls(
        wood: Set<Position>,
        blocked: Set<Position>,
        width: Int,
        height: Int
    ): List<Fall> {
        if (wood.isEmpty()) return emptyList()
        val out = ArrayList<Fall>()
        for (x in 0 until width) {
            var dropY = height - 1
            for (y in height - 1 downTo 0) {
                val p = Position(x, y)
                when {
                    p in blocked -> dropY = y - 1
                    p in wood -> {
                        if (dropY != y) out.add(Fall(p, Position(x, dropY)))
                        dropY--
                    }
                    // Hueco: lo rellenará la madera que venga de más arriba.
                }
            }
        }
        return out
    }

    /** Duración de la caída de [distance] celdas, en ms. Cuanto más alto, más tarda (y más pesa). */
    fun fallDurationMs(distance: Int): Int = 150 + distance.coerceAtLeast(0) * 60

    /**
     * Asienta la FIGURA de apertura de un nivel (`LevelLayouts.buildLevel`), antes de que exista
     * partida ninguna.
     *
     * Un nivel con gravedad no puede abrir con su figura flotando: la primera ficha que el jugador
     * suelte desplomaría el tablero entero de golpe, y la silueta que el generador curó no habría
     * significado nada. Abriendo ya asentado, lo que el jugador ve al levantar el telón es el
     * tablero con el que va a jugar.
     *
     * **Conserva el ORDEN de la lista.** El generador reparte los colores del objetivo en
     * round-robin sobre ella, así que reordenarla cambiaría el objetivo del nivel. Puede hacerlo
     * porque dentro de una columna la caída es una biyección que respeta el orden vertical: dos
     * celdas distintas nunca aterrizan en la misma.
     */
    fun settleFigure(
        figure: List<Pair<Int, Int>>,
        width: Int = GameState.GRID_SIZE,
        height: Int = GameState.GRID_SIZE,
        /** Hacia qué borde se asienta. Por defecto al suelo, que es la gravedad de siempre. */
        pull: Pull = Pull.DOWN
    ): List<Pair<Int, Int>> {
        if (figure.isEmpty()) return figure
        val cells = figure.mapTo(HashSet(figure.size)) { Position(it.first, it.second) }
        val moves = computeFalls(cells, emptySet(), width, height, pull)
        if (moves.isEmpty()) return figure
        val byOrigin = moves.associate { it.from to it.to }
        return figure.map { (x, y) ->
            byOrigin[Position(x, y)]?.let { it.x to it.y } ?: (x to y)
        }
    }
}
