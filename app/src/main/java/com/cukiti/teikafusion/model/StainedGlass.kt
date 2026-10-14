package com.cukiti.teikafusion.model

import kotlin.random.Random

/**
 * RETIRADO 2026-10-09: helper de compatibilidad; sin activación en campaña.
 * **P-244 · LA VIDRIERA** (2026-09-22, elegida por el usuario para sustituir a LAS CELDAS DE COLOR).
 *
 * Unas pocas casillas del tablero son **vidrio**. No bloquean nada —cualquier ficha cabe encima—,
 * así que la regla no puede rechazar una colocación: es justo lo que hacía mal su antecesora, que
 * devolvía la ficha a la bandeja sin aviso (auditoría del 2026-09-16).
 *
 * ## Qué hace
 *
 *  1. **Cada línea que cruza un vidrio lo AGRIETA.** Lo que cuenta es por dónde pasa la línea, no qué
 *     había encima: mismo criterio que las celdas calientes (P-78).
 *  2. **Cuando la vidriera entera está agrietada, ESTALLA**: cada vidrio revienta en cruz —él y sus
 *     cuatro vecinos— y se lleva la madera que pille, con sus estrellas. Es sitio y cosecha, no
 *     progreso: igual que el barrido de la vieja cruz, no cría Teika ni cuenta como línea.
 *  3. **Nace una vidriera nueva** en otras casillas y vuelta a empezar.
 *
 * ## Por qué así
 *
 * Es una decisión de RUTA, que es el tipo de decisión que más le falta a un tablero de 8×8: qué
 * líneas cierro primero para dejar la vidriera a una grieta, y cuándo la hago estallar para abrir
 * sitio en el peor momento del tablero.
 *
 * ## El dial
 *
 * Tres vidrios en el Cristal, **cinco** en el Galáctico. Más vidrios son más líneas que planear
 * antes de que estalle — y un estallido más grande cuando por fin lo hace, así que la regla aprieta
 * sin dejar de pagar.
 */
object StainedGlass {

    const val CELLS_FIRST_CHAPTER = 3
    const val CELLS_LAST_CHAPTER = 5

    fun appliesTo(level: Int): Boolean = false // Retired; Cometa owns this campaign slot.

    /** Cuántos vidrios tiene cada vidriera de [level]. */
    fun countFor(level: Int): Int =
        HouseRule.dial(level, CELLS_FIRST_CHAPTER, CELLS_LAST_CHAPTER)
            .coerceIn(CELLS_FIRST_CHAPTER, CELLS_LAST_CHAPTER)

    /**
     * La vidriera número [wave] de [level] (la primera es la 0).
     *
     * **Filas y columnas distintas**, como las calientes: dos vidrios en la misma fila los agrietaría
     * una sola línea y dejarían de ser dos decisiones. Determinista por nivel y oleada, así que
     * retomar una partida no la cambia.
     *
     * [avoid] son las casillas que no puede ocupar: la vidriera anterior —una nueva encima de la que
     * acaba de estallar no se leería como nueva— y las marcas que ya dicen otra cosa en ese cuadro.
     */
    fun windowFor(
        level: Int,
        wave: Int,
        width: Int = GameState.GRID_SIZE,
        height: Int = GameState.GRID_SIZE,
        avoid: Set<Position> = emptySet(),
    ): Set<Position> {
        if (!appliesTo(level) || width <= 0 || height <= 0) return emptySet()
        val count = countFor(level).coerceAtMost(minOf(width, height))
        // Varios intentos con semillas derivadas: con [avoid] grande un emparejamiento fila↔columna
        // puede caer mal, y el siguiente barajado casi siempre lo resuelve.
        repeat(8) { attempt ->
            val rng = Random(level.toLong() * 7919L + wave * 104729L + attempt * 31L)
            val rows = (0 until height).shuffled(rng)
            val cols = (0 until width).shuffled(rng)
            val out = LinkedHashSet<Position>()
            for (i in rows.indices) {
                if (out.size >= count) break
                val p = Position(cols[i % cols.size], rows[i])
                if (p !in avoid) out += p
            }
            if (out.size >= count) return out
        }
        return emptySet()
    }

    /**
     * Un guardado de ANTES de la vidriera, en un nivel que hoy la lleva, viene sin vidrios (ese
     * nivel era de las celdas de color). Se le siembra la vidriera al retomarlo: sin esto, la
     * partida a medias se jugaría sin la regla que su nivel anuncia.
     */
    fun seededFor(state: GameState): GameState =
        if (!appliesTo(state.level) || state.glassCells.isNotEmpty()) state
        else state.copy(
            glassCells = windowFor(
                level = state.level,
                wave = state.glassWave,
                avoid = state.hotCells,
            ),
            glassCracked = emptySet(),
        )

    /** Los vidrios de [glass] que agrieta esta pasada: los que cruza [lineCells] y seguían sanos. */
    fun crackedBy(glass: Set<Position>, cracked: Set<Position>, lineCells: Set<Position>): Set<Position> =
        glass.filterTo(LinkedHashSet()) { it in lineCells && it !in cracked }

    /** ¿Estalla la vidriera? Sí cuando no le queda un solo vidrio sano. */
    fun shatters(glass: Set<Position>, cracked: Set<Position>): Boolean =
        glass.isNotEmpty() && cracked.containsAll(glass)

    /**
     * Lo que se lleva el estallido: cada vidrio y sus cuatro vecinos, dentro del tablero.
     *
     * Una CRUZ y no un 3×3 a propósito: con cinco vidrios un 3×3 barrería 45 casillas, más de medio
     * tablero, y el estallido dejaría de ser una jugada para ser un reinicio.
     */
    fun shatterArea(glass: Set<Position>, width: Int = GameState.GRID_SIZE, height: Int = GameState.GRID_SIZE): Set<Position> {
        val out = LinkedHashSet<Position>()
        glass.forEach { g ->
            listOf(g, Position(g.x + 1, g.y), Position(g.x - 1, g.y), Position(g.x, g.y + 1), Position(g.x, g.y - 1))
                .filterTo(out) { it.x in 0 until width && it.y in 0 until height }
        }
        return out
    }
}
