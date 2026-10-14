package com.cukiti.teikafusion.model

import kotlin.random.Random

/**
 * **P-233 · EL MOLDE** (2026-09-22, elegida por el usuario para sustituir a EL REPETIDOR).
 *
 * En el tablero hay un **molde**: la silueta de una forma dibujada sobre casillas libres. Si colocas
 * **exactamente esa ficha exactamente ahí** —misma forma, misma orientación, mismas casillas—, al
 * cerrar el turno el molde **estalla**: se lleva la ficha y todo lo que la rodea (las casillas de
 * alrededor, en diagonal incluidas), con sus estrellas. Luego aparece otro molde con otra forma.
 *
 * El repartidor **garantiza** que la forma llegue: cada pocas reposiciones una ficha de la bandeja
 * sale con la forma del molde. El Repetidor repetía una forma para que el jugador la tesela; el
 * molde le pide colocar UNA en su sitio exacto — un encaje de puzle con premio.
 *
 * ## Lo que no hace
 *
 * No bloquea: el molde es una marca del suelo y cualquier ficha puede pisarlo. Si algo lo tapa, al
 * cerrar el turno se muda a otro hueco donde quepa su forma. Y el estallido, como el de los barriles,
 * no es una línea: no cría Teika ni cuenta para el objetivo.
 *
 * ## El dial
 *
 * La forma llega cada **dos** reposiciones en el Cristal y cada **tres** en el Galáctico.
 */
object MoldRule {

    const val EVERY_REFILLS_FIRST_CHAPTER = 2
    const val EVERY_REFILLS_LAST_CHAPTER = 3

    fun appliesTo(level: Int): Boolean = false // Retired; kept only for old save/API compatibility.

    fun everyRefillsFor(level: Int): Int =
        HouseRule.dial(level, EVERY_REFILLS_FIRST_CHAPTER, EVERY_REFILLS_LAST_CHAPTER)
            .coerceIn(EVERY_REFILLS_FIRST_CHAPTER, EVERY_REFILLS_LAST_CHAPTER)

    fun dealsOnRefill(refillCount: Int, level: Int, scale: Int = 1): Boolean =
        appliesTo(level) && refillCount > 0 &&
            refillCount % (everyRefillsFor(level) * scale.coerceAtLeast(1)) == 0

    /**
     * La forma del molde número [wave]: una del catálogo de 3 a 5 casillas. Ni monominós ni dominós
     * (encajarlos no es un reto) ni gigantes (no cabrían en un tablero a medio llenar).
     */
    fun shapeFor(level: Int, wave: Int): List<Position> {
        val pool = Piece.SHAPES.filter { it.size in 3..5 }
        return normalized(pool[(level * 7 + wave * 13).mod(pool.size)])
    }

    /** Las casillas del molde de forma [shape] anclado en [at]. */
    fun cellsOf(shape: List<Position>, at: Position): Set<Position> =
        shape.mapTo(LinkedHashSet()) { Position(at.x + it.x, at.y + it.y) }

    /**
     * Dónde cabe el molde de forma [shape], o `null` si no cabe en ningún sitio libre. Entre todos los
     * sitios posibles elige uno fijo por nivel y oleada, así que retomar una partida no lo mueve.
     */
    fun placeFor(
        shape: List<Position>,
        level: Int,
        wave: Int,
        blocked: Set<Position>,
        width: Int = GameState.GRID_SIZE,
        height: Int = GameState.GRID_SIZE,
    ): Position? {
        if (shape.isEmpty()) return null
        val w = shape.maxOf { it.x } + 1
        val h = shape.maxOf { it.y } + 1
        val options = (0..height - h).flatMap { y -> (0..width - w).map { x -> Position(x, y) } }
            .filter { at -> cellsOf(shape, at).none { it in blocked } }
        if (options.isEmpty()) return null
        return options[Random(level.toLong() * 3319L + wave * 7717L).nextInt(options.size)]
    }

    /** Un molde ya decidido: su forma, dónde va y de qué oleada salió. */
    data class Placement(val shape: List<Position>, val at: Position, val wave: Int)

    /**
     * El primer molde que CABE a partir de la oleada [wave]: si la forma que toca no entra en ningún
     * hueco (un tablero de salida apretado), se prueba la siguiente del catálogo. Así un tablero lleno
     * no deja el nivel sin molde; solo uno sin hueco para ninguna forma de 3 a 5 casillas.
     */
    fun firstFit(
        level: Int,
        wave: Int,
        blocked: Set<Position>,
        width: Int = GameState.GRID_SIZE,
        height: Int = GameState.GRID_SIZE,
    ): Placement? {
        for (w in wave until wave + 12) {
            val shape = shapeFor(level, w)
            placeFor(shape, level, w, blocked, width, height)?.let { return Placement(shape, it, w) }
        }
        return null
    }

    /** ¿Encaja esta colocación en el molde? Las casillas de la ficha son exactamente las del molde. */
    fun matches(footprint: Set<Position>, moldCells: Set<Position>): Boolean =
        moldCells.isNotEmpty() && footprint == moldCells

    /** Lo que se lleva el estallido: el molde y todas las casillas que lo tocan, diagonales incluidas. */
    fun blastArea(cells: Set<Position>, width: Int = GameState.GRID_SIZE, height: Int = GameState.GRID_SIZE): Set<Position> {
        val out = LinkedHashSet<Position>()
        cells.forEach { c ->
            for (dy in -1..1) for (dx in -1..1) {
                val p = Position(c.x + dx, c.y + dy)
                if (p.x in 0 until width && p.y in 0 until height) out += p
            }
        }
        return out
    }

    /** La forma con su esquina en (0,0): es como la guarda el catálogo y como se compara. */
    fun normalized(shape: List<Position>): List<Position> {
        val minX = shape.minOf { it.x }
        val minY = shape.minOf { it.y }
        return shape.map { Position(it.x - minX, it.y - minY) }.sortedWith(compareBy({ it.y }, { it.x }))
    }
}

/**
 * **P-238 · LAS POMPAS** (2026-09-22, elegida por el usuario para sustituir a TEIKAS LIGEROS).
 *
 * Cada Teika que nace (de un bloque-Teika roto, desde P-256) nace **dentro de una pompa** y sube flotando hasta el techo. Ahí
 * se queda, sin tapar la zona donde estás jugando, hasta que una línea que pasa por él **revienta la
 * pompa** — entonces cae con todo su peso.
 *
 * ## Qué decide el jugador
 *
 * **Cuándo y dónde soltarlos.** Los cuerpos dejan de caer donde nacen para esperar arriba, así que el
 * jugador elige qué pompa reventar y sobre qué columna: para juntar una pareja, o para no taparse el
 * hueco que necesita. Los ligeros solo cambiaban cómo flotaban; esto le da la llave al jugador.
 *
 * ## El dial
 *
 * Una línea revienta la pompa en los primeros biomas; en la segunda mitad del juego la pompa es doble
 * y hacen falta **dos**.
 */
object TeikaBubbles {

    const val HITS_FIRST_CHAPTER = 1
    const val HITS_LAST_CHAPTER = 2

    /**
     * Empuje hacia arriba de una pompa, en celdas/s². Algo más que la gravedad (26) para que suba
     * sin prisa: la pompa tiene que VERSE subir, no salir disparada.
     */
    const val LIFT = 34f

    fun appliesTo(level: Int): Boolean = HouseRule.isActive(level, HouseRule.BUBBLES)

    /** Líneas que hacen falta para reventar una pompa en [level] (0 = el nivel no lleva pompas). */
    fun hitsFor(level: Int): Int =
        if (!appliesTo(level)) 0
        else HouseRule.dial(level, HITS_FIRST_CHAPTER, HITS_LAST_CHAPTER)
            .coerceIn(HITS_FIRST_CHAPTER, HITS_LAST_CHAPTER)
}

/**
 * **P-243 · LOS PORTALES** (2026-09-22, elegida por el usuario para sustituir a LA PORTERÍA MÓVIL).
 *
 * Dos portales, uno en la pared de la izquierda y otro en la de la derecha, a distinta altura. Un
 * Teika que **rueda contra un portal** entra por él y sale por el otro con la misma velocidad, como
 * en un túnel. Así el jugador puede llevar un cuerpo de una punta a la otra del tablero inclinando el
 * móvil, o mandarlo junto a su pareja.
 *
 * ## Lo que tiene que caber
 *
 *  - El portal mide **dos casillas**: los Teikas de 1 y 2 pasan; los grandes, no.
 *  - **Un portal tapado no deja salir.** Si hay madera delante de la salida, el portal se cierra y la
 *    pared vuelve a ser pared: un cuerpo nunca aparece dentro de un bloque.
 *
 * ## El dial
 *
 * Quietos en la primera mitad del juego; en la segunda, se **mudan** cada [MOVE_EVERY] colocaciones.
 */
object Portals {

    /** Altura de un portal en casillas. */
    const val SPAN = 2
    const val MOVE_EVERY = 4

    fun appliesTo(level: Int): Boolean = HouseRule.isActive(level, HouseRule.PORTALS)

    /** ¿Se mudan los portales en [level]? */
    fun moves(level: Int): Boolean = appliesTo(level) && HouseRule.intensityFor(level) >= 0.5f

    /**
     * Fila de arriba de cada portal (izquierdo, derecho) tras [placements] colocaciones, o `null`.
     * Nunca a la misma altura: un túnel recto de una pared a otra no llevaría a ningún sitio nuevo.
     */
    fun rowsFor(level: Int, placements: Int, height: Int = GameState.GRID_SIZE): Pair<Int, Int>? {
        if (!appliesTo(level)) return null
        val stop = if (moves(level)) placements / MOVE_EVERY else 0
        val span = (height - SPAN - 1).coerceAtLeast(1)     // filas 1..(height-SPAN-1)
        val left = 1 + (level * 5 + stop * 3).mod(span)
        var right = 1 + (level * 3 + stop * 5 + 2).mod(span)
        if (right == left) right = 1 + (right).mod(span)
        return left to right
    }

    /** ¿Se mudan con esta colocación? Es el turno en que se avisa. */
    fun movesOn(level: Int, placements: Int): Boolean =
        moves(level) && placements > 0 && placements % MOVE_EVERY == 0

    /** Las casillas del borde que ocupa cada portal: su salida tiene que estar libre para abrirse. */
    fun cellsOf(rows: Pair<Int, Int>, width: Int = GameState.GRID_SIZE): Pair<Set<Position>, Set<Position>> =
        (0 until SPAN).mapTo(LinkedHashSet()) { Position(0, rows.first + it) } to
            (0 until SPAN).mapTo(LinkedHashSet()) { Position(width - 1, rows.second + it) }

    /** ¿Puede pasar un Teika de este tamaño? Solo los que caben en la boca. */
    fun fits(type: TeikaType): Boolean = type.level <= SPAN
}
