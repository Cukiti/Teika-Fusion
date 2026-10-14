package com.cukiti.teikafusion.model

/**
 * **P-246 · EL ESPEJO EMBRUJADO** — la mecánica de la Mansión Espectral desde el 2026-09-23 (decisión
 * del usuario; sustituye a la niebla y al reflector que robaba un Teika o estrellas).
 *
 * El espejo parte el tablero por la mitad. Tras los turnos ociosos de aviso copia EN ESPEJO la madera
 * de una mitad sobre los huecos libres de la otra, como **bloques fantasma**:
 *  - OCUPAN: no se puede colocar encima (estorban);
 *  - SÍ CUENTAN para completar líneas (a veces regalan una — ese es el truco del espejo);
 *  - se desvanecen solos a los [PHANTOM_TURNS] turnos.
 *
 * Aviso en dos tiempos, como todo bioma: se empaña (asoma el plano del espejo y la mitad que va a
 * reflejar) y después se ve el reflejo — exactamente en las casillas donde aparecerán los fantasmas.
 * Romper una línea a tiempo lo empaña y la cuenta vuelve a cero.
 */
object HauntedMirror {

    const val NONE = -1
    /** La mitad IZQUIERDA se refleja sobre la derecha. */
    const val LEFT = 0
    /** La DERECHA sobre la izquierda. */
    const val RIGHT = 1
    /** La de ARRIBA sobre la de abajo. */
    const val TOP = 2
    /** La de ABAJO sobre la de arriba. */
    const val BOTTOM = 3

    /** Turnos que dura un bloque fantasma antes de desvanecerse. */
    const val PHANTOM_TURNS = 2   // P-352: 2 → 3 · P-359: 3 → 2 (Mansión 47 %, objetivo 66 %)

    /**
     * **P-359 · TOPE DE FANTASMAS POR DESTELLO** (2026-09-28). El espejo copiaba TODA la madera de la mitad
     * más cargada: un destello podía meter 15–20 bloques en un tablero de 64, cada 5 colocaciones. Con el
     * bot sin banco la Mansión se quedó en el 43 % (objetivo 66 %) y perdía en la colocación 13 de mediana,
     * la más temprana de la campaña. Ahora caen como mucho estos, los más cercanos al eje del espejo.
     */
    const val MAX_PHANTOMS = 6

    /** ¿[p] está en la mitad que se refleja? */
    fun inSource(p: Position, source: Int, size: Int = GameState.GRID_SIZE): Boolean = when (source) {
        LEFT -> p.x < size / 2
        RIGHT -> p.x >= size / 2
        TOP -> p.y < size / 2
        BOTTOM -> p.y >= size / 2
        else -> false
    }

    /** Dónde cae el reflejo de [p]. */
    fun reflect(p: Position, source: Int, size: Int = GameState.GRID_SIZE): Position = when (source) {
        LEFT, RIGHT -> Position(size - 1 - p.x, p.y)
        else -> Position(p.x, size - 1 - p.y)
    }

    /**
     * Los fantasmas que dejaría el espejo: casilla destino → color de la madera reflejada. Solo en
     * huecos DE VERDAD libres ([free]); nunca reflejan fantasmas (un reflejo de un reflejo no existe)
     * y nunca copian estrellas, bobinas, armadura ni veneno: son madera lisa espectral.
     */
    fun phantomsFor(
        grid: Map<Position, CellContent>,
        source: Int,
        free: (Position) -> Boolean,
        size: Int = GameState.GRID_SIZE,
    ): Map<Position, Int> {
        if (source == NONE) return emptyMap()
        val out = LinkedHashMap<Position, Int>()
        for ((p, c) in grid) {
            val w = c as? CellContent.Wood ?: continue
            if (w.phantom > 0 || !inSource(p, source, size)) continue
            val t = reflect(p, source, size)
            if (free(t)) out[t] = w.colorType
        }
        if (out.size <= MAX_PHANTOMS) return out
        // Los más cercanos al eje (la costura del espejo), y a igualdad, en el orden del tablero.
        val half = size / 2.0
        fun axisDistance(p: Position): Double = when (source) {
            LEFT, RIGHT -> kotlin.math.abs(p.x + 0.5 - half)
            else -> kotlin.math.abs(p.y + 0.5 - half)
        }
        return out.entries.sortedWith(compareBy<Map.Entry<Position, Int>>({ axisDistance(it.key) }, { it.key.y }, { it.key.x }))
            .take(MAX_PHANTOMS)
            .associateTo(LinkedHashMap()) { it.key to it.value }
    }

    /**
     * La mitad que el espejo elige al empañarse: la que más fantasmas dejaría (el espejo busca
     * hacer daño… o regalar). Empate → la primera en el orden de [candidates], que el llamador baraja.
     */
    fun chooseSource(
        grid: Map<Position, CellContent>,
        free: (Position) -> Boolean,
        candidates: List<Int> = listOf(LEFT, RIGHT, TOP, BOTTOM),
    ): Int = candidates.maxByOrNull { phantomsFor(grid, it, free).size } ?: NONE

    /** El bloque fantasma del reflejo: madera lisa del color reflejado, con su cuenta atrás. */
    fun phantomWood(colorType: Int): CellContent.Wood =
        CellContent.Wood(colorType = colorType, phantom = PHANTOM_TURNS)
}
