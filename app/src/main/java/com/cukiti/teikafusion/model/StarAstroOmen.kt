package com.cukiti.teikafusion.model

import kotlin.math.abs

/**
 * **El presagio del Cielo Estelar** (2026-09-23, pedido del usuario).
 *
 * Dos quejas del playtest: «el jugador no sabe si va a salir el Sol o la Luna» y «la Luna sale
 * demasiado». La segunda no era el dado —el sorteo ya era 50/50— sino que se tiraba en el turno del
 * disparo y **solo entre los astros posibles**: el Sol necesita un Teika libre, la Luna una estrella,
 * y como casi siempre hay estrellas y muchas veces no hay Teikas, ganaba la Luna por eliminación.
 *
 * Ahora el astro se sortea **al primer turno ocioso**, 50/50 puro, y ese es el presagio: el tablero
 * se pone dorado o plateado durante el aviso y dispara ESE astro en el tercero. El Sol ya no depende
 * de que haya Teikas: sin ninguno, calcina el bloque más cercano al centro ([sunWoodTarget]).
 */
object StarAstroOmen {

    const val NONE = 0
    const val SUN = 1
    const val MOON = 2

    /**
     * El presagio tras el tick: se sortea al entrar en racha ociosa, se mantiene mientras dura, y se
     * borra si la actividad la rompe ([idleAfterTick] = 0) — un astro que no llega a salir no se
     * queda anunciado.
     */
    fun next(currentOmen: Int, idleAfterTick: Int, flip: () -> Boolean): Int = when {
        idleAfterTick <= 0 -> NONE
        currentOmen != NONE -> currentOmen
        else -> if (flip()) SUN else MOON
    }

    /**
     * El bloque que calcina el Sol cuando no hay Teika: la madera más cercana al centro, **sin
     * estrella** si hay alguna — el Sol es la amenaza a tus cuerpos; las estrellas son de la Luna.
     * Solo si no queda otra cosa se lleva una con estrella. Nulo si el tablero no tiene madera.
     */
    fun sunWoodTarget(wood: Collection<Position>, stars: Set<Position>, size: Int = GameState.GRID_SIZE): Position? {
        val c = (size - 1) / 2f
        val byCenter = compareBy<Position>({ abs(it.x - c) + abs(it.y - c) }, { it.y }, { it.x })
        return wood.filter { it !in stars }.minWithOrNull(byCenter) ?: wood.minWithOrNull(byCenter)
    }

    /**
     * **P-321 · LA LUNA DEVUELVE LO QUE ROBA** (2026-09-26, decisión del usuario). Los bloques que la Luna
     * se lleva vuelven a caer como madera SIN estrella en casillas vacías al azar: pierdes la estrella y
     * además te llena el tablero (antes, el robo abría hueco y el castigo casi ayudaba).
     *
     * Dónde caen: [count] casillas vacías que no estén en [unusable] (Teikas, lava, huevo, las casillas
     * que la Luna acaba de vaciar…) y que no CIERREN una fila o columna — sería un regalo. [filled] cuenta
     * como lleno además de la madera (los Teikas, que son comodín). Puede devolver menos si no caben.
     */
    fun moonReturnCells(
        grid: Map<Position, CellContent>,
        count: Int,
        unusable: Set<Position>,
        filled: Set<Position>,
        random: kotlin.random.Random,
        size: Int = GameState.GRID_SIZE,
    ): List<Position> {
        if (count <= 0) return emptyList()
        val full = HashSet<Position>(filled).apply { grid.forEach { (p, c) -> if (c.isOccupied) add(p) } }
        val candidates = (0 until size).flatMap { y -> (0 until size).map { x -> Position(x, y) } }
            .filter { it !in full && it !in unusable }
            .shuffled(random)
        val chosen = ArrayList<Position>(count)
        for (p in candidates) {
            if (chosen.size == count) break
            full += p
            val closesRow = (0 until size).all { x -> Position(x, p.y) in full }
            val closesCol = (0 until size).all { y -> Position(p.x, y) in full }
            if (closesRow || closesCol) { full -= p; continue }
            chosen += p
        }
        return chosen
    }
}
