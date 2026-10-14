package com.cukiti.teikafusion.model

/**
 * **P-79 · LA CORRIENTE** (2026-09-01, aprobada por el usuario; variante: **envuelve**).
 *
 * Cada [PERIOD] colocaciones, el tablero **se desplaza una columna**: lo que sale por un lado entra
 * por el otro. El tablero sigue siendo un 8×8 completo y nadie queda acorralado, pero **tu plan
 * caduca** — y planificar a dos turnos es justo lo que este juego premia.
 *
 * ## Envuelve, no tira (decisión del usuario)
 *
 * Nada se pierde por el borde. Es lo más justo y lo más puzzle: puedes contar con que tus bloques
 * siguen ahí. Y evita el choque de raíz con la decisión congelada de que **el objetivo es siempre
 * estrellas**: las estrellas viajan dentro de la madera, así que una corriente que tirase lo que sale
 * podría borrar progreso del objetivo sin que el jugador pudiera hacer nada.
 *
 * ## Lo que la corriente NO mueve: las anclas
 *
 * Era la trampa que la propuesta señalaba —«¿qué hacen las celdas fijas de bioma?»— y la respuesta
 * es que **son la roca del río**, no la carga:
 *
 *  - La lava y los pozos del Glotón **no se pueden mover**: no son celdas, son consecuencias de
 *    `lavaRow` y de una esquina. (Los muros del Rey, que tampoco se movían, se retiraron el
 *    2026-09-23; la pieza del Jaque es su propia casilla y no viaja con la madera.)
 *  - El resto (escarcha, congelado, cúmulo galáctico, astro, bobinas) vive en conjuntos indexados
 *    por [Position]: moverles la madera de debajo dejaría el estado apuntando a celdas equivocadas.
 *  - Y los **cuerpos de Teika** tampoco: la madera se atasca contra ellos igual que se apoya en
 *    ellos con la gravedad (P-80). A cambio los cuerpos **ruedan** con la corriente — un empujón
 *    horizontal, que es lo que ya hace el sensor de inclinación.
 *
 * Así que la corriente empuja, y **lo que topa con una roca se queda atascado**. Se lee solo.
 */
object BoardCurrent {

    /** Un bloque que la corriente arrastra de [from] a [to] (misma fila). */
    data class Slide(val from: Position, val to: Position)

    /**
     * Colocaciones entre embestida y embestida en el **primer** capítulo, y en el **último**.
     *
     * Es el dial de capítulo de esta regla (2026-09-03, ver [HouseRule.intensityFor]): la corriente
     * empuja cada cuatro turnos en el Cristal y cada dos en el Galáctico. La mecánica es la misma —
     * el jugador ya sabe leerla— pero le queda la mitad de margen para planear contra ella.
     *
     * No baja de 2 a propósito: con periodo 1 el tablero se movería en **cada** colocación y
     * planificar dejaría de ser posible; sería una tirada de dados, no una habilidad.
     */
    const val PERIOD_FIRST_CHAPTER = 4
    const val PERIOD_LAST_CHAPTER = 2

    /** Colocaciones entre embestidas en [level]. Ver [PERIOD_FIRST_CHAPTER]. */
    fun periodFor(level: Int): Int =
        HouseRule.dial(level, PERIOD_FIRST_CHAPTER, PERIOD_LAST_CHAPTER).coerceAtLeast(2)

    /**
     * ¿[level] se juega con corriente?
     *
     * **Una regla de la casa por nivel**: si el nivel ya cambia las reglas con la gravedad (P-80) o
     * con el horizonte (P-75), la corriente se aparta. Dos formas distintas de romper el tablero a
     * la vez no son el doble de interesantes: son ilegibles.
     */
    fun appliesTo(level: Int): Boolean = axisFor(level) != null

    /**
     * **POR QUÉ EJE EMPUJA** (2026-09-03).
     *
     * Como la gravedad, la corriente pasó de ser una regla a ser una familia, y por el mismo camino:
     * un solo solver ([computeSlides]) leído desde otros ejes. La corriente vertical es la
     * horizontal con el tablero traspuesto; la noria (antes la cinta) es la misma fila desenrollada sobre el anillo del borde; la marea
     * es la horizontal cambiando de sentido en cada embestida.
     */
    enum class Axis { HORIZONTAL, VERTICAL, RING }

    /** El eje de la corriente de [level], o `null` si el nivel no lleva ninguna. */
    fun axisFor(level: Int): Axis? = when (HouseRule.forLevel(level)) {
        HouseRule.CURRENT, HouseRule.TIDE -> Axis.HORIZONTAL
        HouseRule.VERTICAL_CURRENT -> Axis.VERTICAL
        else -> null
    }

    /** Los niveles con corriente (de cualquier forma) de toda la campaña, en orden. */
    fun levels(): List<Int> = (1..LevelMap.totalLevels).filter(::appliesTo)

    /**
     * Hacia dónde fluye en [level]: `+1` derecha (o abajo), `-1` izquierda (o arriba). Alterna por
     * nivel, y es fijo dentro de la partida: la corriente tiene que ser **predecible** para que
     * planificar contra ella sea una habilidad y no una tirada.
     */
    fun directionFor(level: Int): Int = if (level % 2 == 0) 1 else -1

    /**
     * **LA MAREA** (2026-09-03): el sentido cambia en cada embestida.
     *
     * Sigue siendo predecible —el jugador cuenta las embestidas igual que cuenta los turnos— pero
     * deja de poder resolverse con un plan único: lo que la marea se lleva a la derecha, vuelve. Se
     * deriva de cuántas embestidas van, no de un campo guardado, así que no añade estado.
     */
    fun directionFor(level: Int, placements: Int): Int {
        val base = directionFor(level)
        if (HouseRule.forLevel(level) != HouseRule.TIDE) return base
        val surges = placements / periodFor(level)
        return if (surges % 2 == 0) base else -base
    }

    /**
     * El sentido de la **próxima** embestida, para el turno de AVISO.
     *
     * ## El bug que arregla (2026-09-04)
     *
     * El aviso llamaba a [directionFor] con las colocaciones de ESE turno, y la marea cambia de
     * sentido contando embestidas: en la colocación 3 van 0 embestidas (sentido `+`) y en la 4 —la
     * que de verdad embiste— va 1 (sentido `−`). O sea que **la telegrafía anunciaba exactamente el
     * lado contrario al que luego empujaba**, que es peor que no avisar: el jugador colocaba
     * defendiéndose del lado equivocado.
     *
     * Solo pasaba con la marea. La corriente normal, la vertical y la noria tienen sentido fijo, así
     * que el aviso acertaba por no depender del turno.
     */
    fun warningDirectionFor(level: Int, placements: Int): Int =
        directionFor(level, placements + 1)

    /**
     * **P-231 · LA NORIA** (2026-09-22, sustituye a LA CINTA): el anillo de las 28 casillas del
     * borde, en el orden en que gira. Sentido `+1` = el de las agujas del reloj.
     *
     * La cinta movía una fila y el jugador podía usarla; la noria mueve el BORDE entero, que es por
     * donde pasan todas las filas y todas las columnas: lo que pongas en el borde viaja, da la vuelta
     * a la esquina y puede cerrarte una línea que no era la suya. Misma herramienta, mucho más
     * alcance. El centro del tablero no se mueve nunca.
     */
    fun ringOf(width: Int = GameState.GRID_SIZE, height: Int = GameState.GRID_SIZE): List<Position> {
        if (width <= 0 || height <= 0) return emptyList()
        if (width == 1 || height == 1) {
            return (0 until width).flatMap { x -> (0 until height).map { y -> Position(x, y) } }
        }
        val out = ArrayList<Position>(2 * (width + height) - 4)
        for (x in 0 until width) out.add(Position(x, 0))
        for (y in 1 until height) out.add(Position(width - 1, y))
        for (x in width - 2 downTo 0) out.add(Position(x, height - 1))
        for (y in height - 2 downTo 1) out.add(Position(0, y))
        return out
    }

    /** ¿Esta colocación (1-based, ya contada) dispara la embestida en [level]? */
    fun firesOn(placements: Int, level: Int): Boolean =
        placements > 0 && placements % periodFor(level) == 0

    /** ¿Y esta deja la SIGUIENTE en la embestida? Es el turno de aviso. */
    fun warnsOn(placements: Int, level: Int): Boolean =
        periodFor(level).let { placements % it == it - 1 }

    /** Colocaciones que faltan para la próxima embestida (1 = la siguiente). */
    fun turnsUntil(placements: Int, level: Int): Int =
        periodFor(level).let { it - (placements % it) }

    /**
     * Adónde va cada bloque de [wood] cuando la corriente empuja hacia [direction].
     *
     * [anchors] son las celdas que no se mueven **y frenan** a la madera que empuja contra ellas.
     * Una celda que esté en [wood] y en [anchors] se trata como ancla: manda el bloqueo.
     *
     * Dos regímenes, y la diferencia es la que hace que la mecánica se entienda mirándola:
     *
     *  - **Fila sin anclas** → el contenido entero **rota** una celda. Siempre es legal (es una
     *    permutación) y es el caso que da su nombre a la mecánica: el tablero se desliza.
     *  - **Fila con anclas** → las anclas parten la fila en tramos, y dentro de cada tramo la madera
     *    empuja hacia el final: la que tiene hueco delante avanza, la que está pegada a la roca se
     *    queda, y la de detrás se amontona contra ella. Nadie atraviesa nada.
     */
    /**
     * [computeSlides] en el eje y los carriles que le tocan a [level].
     *
     * Es la única puerta que usa el motor: así los cuatro sabores de corriente —la de siempre, la
     * vertical, la noria y la marea— entran por el mismo sitio y ninguno necesita su propio bucle.
     *
     *  - **Vertical**: se traspone el tablero, se resuelve con el solver de filas y se destraspone.
     *    El corte ninja no viaja: es una pared VERTICAL, y un deslizamiento vertical nunca la cruza
     *    (tampoco al envolver, porque envuelve arriba-abajo). Por eso [splitAtX] no se propaga.
     *  - **Noria**: solo la madera del anillo del borde, desenrollado en una fila circular. El centro
     *    del tablero ni se consulta, así que se queda exactamente donde está. Ver [computeRingSlides].
     */
    fun computeSlidesFor(
        level: Int,
        wood: Set<Position>,
        anchors: Set<Position>,
        width: Int,
        height: Int,
        placements: Int,
        splitAtX: Int = -1,
    ): List<Slide> {
        val axis = axisFor(level) ?: return emptyList()
        val direction = directionFor(level, placements)
        if (axis == Axis.RING) return computeRingSlides(wood, anchors, width, height, direction, splitAtX)
        val moving = wood
        if (moving.isEmpty()) return emptyList()
        if (axis == Axis.HORIZONTAL) {
            return computeSlides(moving, anchors, width, height, direction, splitAtX)
        }
        fun flip(p: Position) = Position(p.y, p.x)
        return computeSlides(
            moving.mapTo(HashSet(), ::flip),
            anchors.mapTo(HashSet(), ::flip),
            height, width, direction, splitAtX = -1
        ).map { Slide(flip(it.from), flip(it.to)) }
    }

    fun computeSlides(
        wood: Set<Position>,
        anchors: Set<Position>,
        width: Int,
        height: Int,
        direction: Int,
        /**
         * **P-101 · EL CORTE NINJA ES UNA ROCA** (2026-09-02, decisión del usuario).
         *
         * Columna a partir de la cual empieza la mitad derecha del tablero partido, o `-1` sin
         * corte. El tajo es una barrera que respetan la colocación (`canPlacePieceInternal`), la
         * física (`setCenterWall`) y el motor de líneas (que parte las filas en tramos) — y la
         * corriente era **el único sitio del juego donde algo la cruzaba**: la madera se deslizaba a
         * través de la katana y, como la corriente envuelve, podía además salir por un borde y
         * reaparecer al otro lado del tajo.
         *
         * Se resuelve sin tocar el algoritmo: cada mitad se calcula por separado y **lo que hay
         * fuera de la mitad cuenta como ancla**, así que el paseo por la fila se para en el tajo
         * igual que se para en un muro del Rey. De paso desaparece el envolvente mientras dura el
         * corte, que es lo correcto: con el tablero partido no hay vuelta completa que dar.
         */
        splitAtX: Int = -1
    ): List<Slide> {
        if (wood.isEmpty() || width <= 1) return emptyList()
        val step = if (direction >= 0) 1 else -1
        val out = ArrayList<Slide>()
        val halves = if (splitAtX in 1 until width) listOf(0 until splitAtX, splitAtX until width)
                     else listOf(0 until width)
        for (y in 0 until height) for (half in halves) {
            val isAnchor = BooleanArray(width) { it !in half || Position(it, y) in anchors }
            val occupied = BooleanArray(width) { Position(it, y) in wood && !isAnchor[it] }
            if (occupied.none { it }) continue

            if (isAnchor.none { it }) {
                // Fila limpia: rota entera. No hay conflicto posible — el destino de cada bloque lo
                // deja libre el que va delante, que se mueve en el mismo instante.
                for (x in 0 until width) {
                    if (occupied[x]) out += Slide(Position(x, y), Position((x + step + width) % width, y))
                }
                continue
            }

            // Con anclas: cada tramo empuja contra la roca que tiene delante.
            val visited = BooleanArray(width)
            for (start in 0 until width) {
                if (isAnchor[start] || visited[start]) continue
                // Solo se arranca por el PRINCIPIO del tramo (el de detrás, contracorriente, es roca).
                if (!isAnchor[(start - step + width) % width]) continue
                val segment = ArrayList<Int>(width)
                var cursor = start
                while (!isAnchor[cursor]) {
                    segment += cursor
                    visited[cursor] = true
                    cursor = (cursor + step + width) % width
                }
                // De delante hacia atrás: así el hueco que deja uno lo puede ocupar el siguiente.
                for (i in segment.size - 2 downTo 0) {
                    val from = segment[i]
                    val to = segment[i + 1]
                    if (occupied[from] && !occupied[to]) {
                        occupied[from] = false
                        occupied[to] = true
                        out += Slide(Position(from, y), Position(to, y))
                    }
                }
            }
        }
        return out
    }

    /**
     * **LA NORIA**: adónde va cada bloque del anillo al girar una casilla hacia [direction].
     *
     * Se resuelve con el MISMO solver de filas, desenrollando el anillo en una fila de 28: el solver
     * ya trata la fila como circular (un tramo entre anclas da la vuelta por el final), así que el
     * anillo sale gratis — la madera rota entera si no hay anclas y se amontona contra la roca si
     * las hay. Las anclas del anillo (muros, lava, congelado…) son rocas igual que en la corriente.
     *
     * **Con el tablero partido por el Dojo la noria no gira**: el tajo corta el anillo arriba y abajo,
     * y una noria partida en dos no es una noria. Se para hasta que el corte se repare.
     */
    fun computeRingSlides(
        wood: Set<Position>,
        anchors: Set<Position>,
        width: Int,
        height: Int,
        direction: Int,
        splitAtX: Int = -1,
    ): List<Slide> {
        if (splitAtX > 0) return emptyList()
        val ring = ringOf(width, height)
        if (ring.size < 2) return emptyList()
        val index = HashMap<Position, Int>(ring.size * 2)
        ring.forEachIndexed { i, p -> index[p] = i }
        val movingRow = wood.mapNotNullTo(HashSet()) { p -> index[p]?.let { Position(it, 0) } }
        if (movingRow.isEmpty()) return emptyList()
        val anchorRow = anchors.mapNotNullTo(HashSet()) { p -> index[p]?.let { Position(it, 0) } }
        return computeSlides(movingRow, anchorRow, ring.size, 1, direction)
            .map { Slide(ring[it.from.x], ring[it.to.x]) }
    }

    /** ¿Este arrastre cruza el borde del tablero? Se dibuja en dos tramos (sale y entra). */
    fun wraps(slide: Slide, width: Int): Boolean =
        kotlin.math.abs(slide.to.x - slide.from.x) > 1 ||
            kotlin.math.abs(slide.to.y - slide.from.y) > 1 ||
            width == 1
}
