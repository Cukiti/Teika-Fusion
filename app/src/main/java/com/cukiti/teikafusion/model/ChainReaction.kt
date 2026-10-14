package com.cukiti.teikafusion.model

/**
 * **P-234 · REACCIÓN EN CADENA** (2026-09-22, elegida por el usuario para sustituir a LA CRUZ).
 *
 * Algunas fichas traen un **BARRIL** en una de sus casillas. El barril es madera normal —cabe donde
 * cabe la ficha y cuenta para las líneas—, pero cuando una línea lo rompe **estalla**: se lleva el
 * 3×3 que tiene alrededor. Y si en ese 3×3 hay otro barril, estalla también, y así en cadena.
 *
 * ## Qué decide el jugador
 *
 * **Dónde deja los barriles.** Es el jugador quien los coloca, así que es él quien construye la
 * mecha: dos barriles a una casilla de distancia son una cadena esperando; un barril junto a la
 * estrella que no llega es la forma de llevársela sin cerrar su fila. La Cruz barría siempre el
 * mismo centro; esto barre donde el jugador lo haya preparado.
 *
 * ## Lo que NO es
 *
 * Como el barrido de la Cruz al que sustituye, **el estallido no es una línea**: se lleva la madera y
 * cobra las estrellas que pille, pero no cría Teika ni cuenta para el objetivo. Y respeta lo que una
 * línea respeta: ni cristal con coraza, ni bobinas, ni muros, ni cúmulos (lo decide el motor con el
 * mismo predicado con el que limpia una línea).
 *
 * ## El dial
 *
 * Un barril cada **dos** reposiciones en el Cristal, cada **cuatro** en el Galáctico: la misma regla
 * con menos mecha, así que cada barril hay que colocarlo mejor.
 */
object ChainReaction {

    const val EVERY_REFILLS_FIRST_CHAPTER = 2
    const val EVERY_REFILLS_LAST_CHAPTER = 4

    @Suppress("UNUSED_PARAMETER")
    fun appliesTo(level: Int): Boolean = false // Retired; pure helpers remain for compatibility tests.

    /** Cada cuántas reposiciones llega una ficha con barril en [level]. */
    fun everyRefillsFor(level: Int): Int =
        HouseRule.dial(level, EVERY_REFILLS_FIRST_CHAPTER, EVERY_REFILLS_LAST_CHAPTER)
            .coerceIn(EVERY_REFILLS_FIRST_CHAPTER, EVERY_REFILLS_LAST_CHAPTER)

    /** ¿Trae barril la reposición número [refillCount]? */
    fun dealsOnRefill(refillCount: Int, level: Int, scale: Int = 1): Boolean =
        appliesTo(level) && refillCount > 0 &&
            refillCount % (everyRefillsFor(level) * scale.coerceAtLeast(1)) == 0

    /**
     * Pone el barril en la ficha que más lo aprovecha de [pieces]: la MÁS GRANDE sin herramienta
     * (una ficha grande deja el barril rodeado de su propia madera, que es la mecha más fácil de
     * leer). La casilla del barril es la del medio de su forma. Devuelve [pieces] tal cual si no
     * hay candidata.
     */
    fun markBarrel(pieces: List<Piece>): List<Piece> {
        if (pieces.any { it.barrelMask.any { b -> b } }) return pieces
        val idx = pieces.indices
            .filter { val p = pieces[it]; !p.isMagnet && !p.isWildcard && !p.isStarWish && p.shape.size >= 2 }
            .maxByOrNull { pieces[it].shape.size } ?: return pieces
        val piece = pieces[idx]
        val middle = piece.shape.size / 2
        return pieces.toMutableList().also {
            it[idx] = piece.copy(barrelMask = piece.shape.indices.map { i -> i == middle })
        }
    }

    /** El 3×3 que se lleva un barril que estalla en [center], recortado al tablero. */
    fun blastArea(center: Position, width: Int = GameState.GRID_SIZE, height: Int = GameState.GRID_SIZE): Set<Position> {
        val out = LinkedHashSet<Position>(9)
        for (dy in -1..1) for (dx in -1..1) {
            val p = Position(center.x + dx, center.y + dy)
            if (p.x in 0 until width && p.y in 0 until height) out += p
        }
        return out
    }

    /**
     * Una reacción en cadena: qué barriles estallan y en qué ORDEN (oleadas), y qué casillas barre.
     *
     * [seeds] son los barriles que la línea ha roto; [barrels] todos los del tablero. Cada oleada
     * son los barriles que alcanzó la anterior. El orden no es decoración: el VFX estalla oleada a
     * oleada, y es lo que hace que el jugador VEA la cadena en vez de un único fogonazo.
     */
    data class Chain(val waves: List<Set<Position>>, val area: Set<Position>)

    fun resolve(
        seeds: Set<Position>,
        barrels: Set<Position>,
        width: Int = GameState.GRID_SIZE,
        height: Int = GameState.GRID_SIZE,
    ): Chain {
        if (seeds.isEmpty()) return Chain(emptyList(), emptySet())
        val waves = ArrayList<Set<Position>>()
        val detonated = HashSet<Position>()
        val area = LinkedHashSet<Position>()
        var current: Set<Position> = seeds
        // Tope de oleadas: el tablero tiene 64 casillas y cada oleada detona al menos un barril
        // nuevo, así que el bucle termina solo; el tope es un seguro, no una regla.
        var guard = 0
        while (current.isNotEmpty() && guard++ < 64) {
            waves += current
            detonated += current
            val reached = LinkedHashSet<Position>()
            current.forEach { reached += blastArea(it, width, height) }
            area += reached
            current = reached.filterTo(LinkedHashSet()) { it in barrels && it !in detonated }
        }
        return Chain(waves, area)
    }
}
