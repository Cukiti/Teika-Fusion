package com.cukiti.teikafusion.model

import kotlin.math.abs

/** Un paso de un bloque de madera dentro del rompecabezas deslizante. */
data class SlideMove(val from: Position, val to: Position)

/**
 * Timoteo / Puzzle Deslizante: las reglas PURAS del rompecabezas.
 *
 * La habilidad no teletransporta nada: el tablero se resuelve solo, como una loseta deslizante.
 * En cada PASADA cada bloque de madera da UN paso hacia el centro de masa del resto de la madera,
 * si la celda destino está libre. Repitiendo pasadas la madera se va cerrando sobre sí misma hasta
 * quedar "juntita" (sin huecos interiores) — y las filas/columnas que se completen por el camino
 * las rompe el motor de forma progresiva, pasada a pasada.
 *
 * El orden importa: se mueve primero el bloque MÁS CERCANO al centro. Los del núcleo ya están
 * pegados y no encuentran hueco (no se mueven), así que los de fuera van rellenando por detrás
 * en vez de amontonarse contra ellos. Sin ese orden la compactación se atasca en el borde.
 */
object SlidePuzzleRules {

    /**
     * Calcula UNA pasada del rompecabezas. [wood] son las celdas con madera móvil, [blocked] las
     * que nadie puede pisar (Teikas, muro del Rey, celdas capturadas, madera congelada…).
     * Devuelve los pasos de esta pasada — vacío cuando ya no queda nada que apretar.
     */
    fun computePass(
        wood: Set<Position>,
        blocked: Set<Position>,
        gridSize: Int = GameState.GRID_SIZE
    ): List<SlideMove> {
        if (wood.size < 2) return emptyList()

        // Centro de masa de la madera: el punto sobre el que se cierra el rompecabezas.
        val cx = wood.sumOf { it.x }.toFloat() / wood.size
        val cy = wood.sumOf { it.y }.toFloat() / wood.size

        fun dist2(p: Position): Float {
            val dx = p.x - cx; val dy = p.y - cy
            return dx * dx + dy * dy
        }

        val occupied = wood.toMutableSet()
        val moves = mutableListOf<SlideMove>()

        // Ocupación viva por fila/columna (madera + obstáculos que también cuentan para completar
        // una línea NO se conocen aquí; con la madera basta): guía el sesgo "forma filas".
        val rowFill = IntArray(gridSize)
        val colFill = IntArray(gridSize)
        (wood + blocked).forEach { p ->
            if (p.y in 0 until gridSize) rowFill[p.y]++
            if (p.x in 0 until gridSize) colFill[p.x]++
        }

        // Del núcleo hacia fuera (ver KDoc): los de dentro no encuentran hueco y hacen de ancla.
        wood.sortedBy { dist2(it) }.forEach { p ->
            val dx = cx - p.x
            val dy = cy - p.y
            val stepX = Position(p.x + if (dx > 0) 1 else -1, p.y)
            val stepY = Position(p.x, p.y + if (dy > 0) 1 else -1)
            // Primero el eje donde está más lejos del centro; el otro queda de alternativa…
            val byAxis = if (abs(dx) >= abs(dy)) listOf(stepX, stepY) else listOf(stepY, stepX)
            // …salvo que el paso alternativo aterrice en una línea claramente MÁS llena: el jugador
            // debe VER filas armándose solas, así que ante huecos parejos gana la línea que está a
            // punto de completarse (sesgo estable: solo desempata con ventaja clara de ≥2 celdas).
            fun lineScore(to: Position): Int =
                (if (to.y in 0 until gridSize) rowFill[to.y] else 0) +
                (if (to.x in 0 until gridSize) colFill[to.x] else 0)
            val candidates =
                if (byAxis.size == 2 && lineScore(byAxis[1]) >= lineScore(byAxis[0]) + 2)
                    listOf(byAxis[1], byAxis[0])
                else byAxis

            for (to in candidates) {
                // Un eje sin desvío que recorrer no es un paso hacia el centro, es un temblor.
                if (to.x == p.x && to.y == p.y) continue
                if (to.x == p.x && abs(dy) < 0.5f) continue
                if (to.y == p.y && abs(dx) < 0.5f) continue
                if (to.x !in 0 until gridSize || to.y !in 0 until gridSize) continue
                if (to in occupied || to in blocked) continue
                occupied.remove(p)
                occupied.add(to)
                rowFill[p.y]--; colFill[p.x]--
                rowFill[to.y]++; colFill[to.x]++
                moves.add(SlideMove(p, to))
                break
            }
        }
        return moves
    }
}
