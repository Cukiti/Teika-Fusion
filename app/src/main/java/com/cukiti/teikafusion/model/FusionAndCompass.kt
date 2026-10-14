package com.cukiti.teikafusion.model

/**
 * **P-232 · FICHAS QUE SE FUNDEN** (2026-09-22, elegida por el usuario para sustituir a EL DESCARTE).
 *
 * El Suika llevado a la bandeja: **arrastra una ficha encima de su gemela** —misma forma, en la
 * bandeja— y las dos se funden en una ficha el doble de grande. No cuesta turno, igual que no lo
 * costaba descartar.
 *
 * ## Qué decide el jugador
 *
 * Una ficha grande limpia mucho de una vez, pero cuesta más encajarla, y dos pequeñas son flexibles.
 * El Descarte te dejaba tirar lo que no querías; esto te deja convertir dos fichas cómodas en una
 * jugada grande, y el riesgo es tuyo.
 *
 * ## Las reglas de la fusión
 *
 *  - Solo gemelas de **hasta [MAX_SOURCE] casillas**: dos de cinco darían una de diez, que no es una
 *    ficha sino medio tablero.
 *  - La copia se pega **por el lado largo** ([fuse]): dos I3 tumbadas hacen un bloque 3×2, dos
 *    cuadrados un 2×4. La ficha se juega como sale, sin girar, como todas.
 *  - **No se deja fundir si el resultado no cabe en ningún sitio del tablero**: el aviso lo dice antes
 *    de soltar. Una fusión que te deja sin jugada no sería una apuesta, sería una trampa.
 *
 * ## El dial
 *
 * El repartidor trae gemelas pequeñas en cada reposición en la primera mitad del juego y una
 * reposición sí y otra no en la segunda.
 */
object PieceFusion {

    /** Tamaño máximo de cada gemela que se puede fundir. */
    const val MAX_SOURCE = 4

    fun appliesTo(level: Int): Boolean = false // Retired; kept only for old save/API compatibility.

    /** ¿Trae gemelas la reposición número [refillCount] de [level]? */
    fun dealsTwinsOn(level: Int, refillCount: Int): Boolean {
        if (!appliesTo(level)) return false
        return HouseRule.intensityFor(level) < 0.5f || refillCount % 2 == 0
    }


    /** ¿Se pueden fundir dos fichas? Gemelas (misma forma) y pequeñas. Ni herramientas ni imanes. */
    fun canFuse(a: Piece, b: Piece): Boolean =
        a.id != b.id && a.shape.size <= MAX_SOURCE &&
            MoldRule.normalized(a.shape) == MoldRule.normalized(b.shape) &&
            listOf(a, b).none { it.isMagnet || it.isWildcard || it.isStarWish || it.isBrush || it.isChameleon || it.isGrowing }

    /**
     * La forma fundida de dos copias de [shape]: la segunda se pega por debajo si la ficha es más
     * ancha que alta (o igual), y por la derecha si es más alta. Así el resultado crece por su lado
     * corto y queda compacto.
     */
    fun fuse(shape: List<Position>): List<Position> {
        val base = MoldRule.normalized(shape)
        val w = base.maxOf { it.x } + 1
        val h = base.maxOf { it.y } + 1
        val copy = if (w >= h) base.map { Position(it.x, it.y + h) } else base.map { Position(it.x + w, it.y) }
        return base + copy
    }

    /**
     * La ficha que sale de fundir [dragged] sobre [target]: la forma fundida, el color del destino y
     * las estrellas de las dos (cada máscara, en el orden de su mitad). Id nuevo: es otra ficha.
     */
    fun fused(dragged: Piece, target: Piece): Piece {
        val base = MoldRule.normalized(target.shape)
        val shape = fuse(target.shape)
        fun maskOf(p: Piece): List<TargetColor?> {
            if (p.targetMask.size != p.shape.size) return List(p.shape.size) { null }
            // La máscara va celda a celda contra la forma: se reordena igual que la forma normalizada.
            val norm = MoldRule.normalized(p.shape)
            val minX = p.shape.minOf { it.x }; val minY = p.shape.minOf { it.y }
            return norm.map { c -> p.targetMask[p.shape.indexOf(Position(c.x + minX, c.y + minY))] }
        }
        val mask = maskOf(target) + maskOf(dragged)
        return Piece(
            shape = shape,
            colorType = target.colorType,
            targetMask = if (mask.all { it == null }) emptyList() else mask,
        )
    }
}

/** P-232: ¿se puede soltar esta ficha sobre aquella? Lo que dice el aviso antes de soltar. */
enum class FuseCheck {
    /** No son gemelas fundibles (o el nivel no lleva la regla): no pasa nada. */
    NONE,
    /** Se funden. */
    OK,
    /** Son gemelas, pero lo que saldría no cabe en ningún sitio: no se deja. */
    NO_ROOM,
}

/**
 * **P-241 · LA BRÚJULA** (2026-09-22, elegida por el usuario para sustituir a LA INCLINACIÓN LIBRE).
 *
 * La gravedad de los **Teikas** gira 90° cada pocas colocaciones: abajo → izquierda → arriba →
 * derecha (o al revés, según el nivel). Una brújula en el tablero dice hacia dónde caen ahora y
 * cuántas colocaciones faltan para el giro; cuando gira, todos los cuerpos ruedan hacia la pared
 * nueva. **La madera no se mueve**: solo los cuerpos.
 *
 * ## Qué decide el jugador
 *
 * Prever dónde van a estar los cuerpos el turno que viene: colocar madera en el camino para
 * frenarlos, dejar un pasillo para que se junten dos gemelos, o aprovechar que la próxima pared es la
 * que tiene hueco. La inclinación libre le daba más fuerza al gesto; la brújula le da al tablero un
 * ritmo que se puede leer y usar.
 *
 * ## El dial
 *
 * Gira cada **cinco** colocaciones en el Cristal y cada **tres** en el Galáctico.
 */
object Compass {

    const val PERIOD_FIRST_CHAPTER = 5
    const val PERIOD_LAST_CHAPTER = 3

    /** Direcciones, en el orden de las agujas del reloj. */
    const val DOWN = 0
    const val LEFT = 1
    const val UP = 2
    const val RIGHT = 3

    fun appliesTo(level: Int): Boolean = HouseRule.isActive(level, HouseRule.COMPASS)

    fun periodFor(level: Int): Int =
        HouseRule.dial(level, PERIOD_FIRST_CHAPTER, PERIOD_LAST_CHAPTER)
            .coerceIn(PERIOD_LAST_CHAPTER, PERIOD_FIRST_CHAPTER)

    /** Sentido del giro en [level]: +1 horario, −1 antihorario. Fijo en el nivel, para poder preverlo. */
    fun stepFor(level: Int): Int = if (level % 2 == 0) 1 else -1

    /** Hacia dónde caen los Teikas tras [placements] colocaciones ([DOWN] fuera de la regla). */
    fun dirFor(level: Int, placements: Int): Int {
        if (!appliesTo(level)) return DOWN
        val turns = placements / periodFor(level)
        return (turns * stepFor(level)).mod(4)
    }

    /** ¿Gira con esta colocación? */
    fun turnsOn(level: Int, placements: Int): Boolean =
        appliesTo(level) && placements > 0 && placements % periodFor(level) == 0

    /** Colocaciones que faltan para el próximo giro (1 = el siguiente). */
    fun turnsUntil(level: Int, placements: Int): Int = periodFor(level).let { it - placements % it }

    /** El vector unitario de la gravedad en [dir], en coordenadas de tablero (y hacia abajo). */
    fun vectorOf(dir: Int): Pair<Float, Float> = when (dir.mod(4)) {
        DOWN -> 0f to 1f
        LEFT -> -1f to 0f
        UP -> 0f to -1f
        else -> 1f to 0f
    }
}
