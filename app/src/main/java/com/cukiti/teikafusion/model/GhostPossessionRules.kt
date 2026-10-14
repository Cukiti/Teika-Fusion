package com.cukiti.teikafusion.model

/** Un bloque poseído por Morti viajando de [from] a [to] atravesando el tablero. */
data class PossessionMove(val from: Position, val to: Position)

/**
 * Morti / **POSESIÓN** — las reglas PURAS (rework 2026-08-27, sustituye a «Tembleque»).
 *
 * El Tembleque agitaba el tablero para que los Teikas rebotaran y rompieran madera al azar: no se
 * podía planear, así que no se sentía como un poder del jugador sino como un accidente afortunado.
 *
 * Posesión hace lo contrario: **resuelve el puzzle**. Morti posee unos cuantos bloques sueltos, los
 * vuelve translúcidos, los hace ATRAVESAR el tablero y los solidifica dentro de los huecos que
 * completan línea. Atravesar la materia es lo que hace un fantasma, y completar líneas es lo que el
 * jugador está intentando hacer: la fantasía y la utilidad apuntan al mismo sitio.
 *
 * Dos reglas que evitan que se dispare en el pie:
 *  1. **Nunca roba de una línea que está completando.** Un donante dentro de una línea elegida la
 *     desharía en el mismo movimiento.
 *  2. **Se lleva los bloques MÁS INÚTILES**: los de la fila y columna más vacías. Robar de una fila
 *     casi llena destruiría el trabajo del jugador para pagar el de Morti.
 */
object GhostPossessionRules {

    /**
     * Planifica la posesión.
     *
     * @param wood     celdas con madera MÓVIL (poseíble): ni congelada, ni bajo un Teika, ni muro.
     * @param blocked  celdas que cuentan como ocupadas pero nadie puede mover ni pisar.
     * @param maxMoves presupuesto de bloques a poseer (escala con el nivel de la habilidad).
     * @return los viajes a ejecutar, vacío si no hay ninguna línea alcanzable.
     */
    fun plan(
        wood: Set<Position>,
        blocked: Set<Position>,
        maxMoves: Int,
        gridSize: Int = GameState.GRID_SIZE
    ): List<PossessionMove> {
        if (maxMoves <= 0 || wood.isEmpty()) return emptyList()
        val full = wood + blocked

        // ── Líneas alcanzables: las que se completan con [maxMoves] bloques o menos ──
        // Se ordenan por huecos ASCENDENTE: primero las que están a un bloque de caer, que es donde
        // cada bloque poseído rinde más.
        val candidates = ArrayList<List<Position>>()
        for (y in 0 until gridSize) {
            val holes = (0 until gridSize).map { Position(it, y) }.filter { it !in full }
            if (holes.isNotEmpty() && holes.size <= maxMoves) candidates.add(holes)
        }
        for (x in 0 until gridSize) {
            val holes = (0 until gridSize).map { Position(x, it) }.filter { it !in full }
            if (holes.isNotEmpty() && holes.size <= maxMoves) candidates.add(holes)
        }
        if (candidates.isEmpty()) return emptyList()
        candidates.sortWith(compareBy({ it.size }, { it.first().y }, { it.first().x }))

        // «Inutilidad» de un bloque: cuanto MÁS VACÍAS estén su fila y su columna, menos trabajo
        // pierde el jugador si Morti se lo lleva.
        val rowFill = IntArray(gridSize)
        val colFill = IntArray(gridSize)
        for (p in full) {
            if (p.y in 0 until gridSize) rowFill[p.y]++
            if (p.x in 0 until gridSize) colFill[p.x]++
        }
        fun uselessness(p: Position) = rowFill[p.y] + colFill[p.x]

        // ── Elegir líneas (por presupuesto) y comprobar que hay donantes de sobra ──
        // Los donantes dependen de las líneas elegidas (no se roba de ellas) y las líneas elegidas
        // dependen de cuántos donantes hay: se resuelve quitando la última línea y reintentando.
        var chosen = ArrayList<List<Position>>()
        var budget = maxMoves
        val taken = HashSet<Position>()
        for (holes in candidates) {
            if (holes.size > budget) continue
            if (holes.any { it in taken }) continue   // otra línea ya cubre ese hueco
            chosen.add(holes)
            taken.addAll(holes)
            budget -= holes.size
            if (budget == 0) break
        }

        while (chosen.isNotEmpty()) {
            val protectedCells = HashSet<Position>()
            for (holes in chosen) {
                // La línea entera queda protegida, no solo sus huecos: sus bloques ya colocados son
                // justo los que la hacen valiosa.
                val h = holes.first()
                val isRow = holes.all { it.y == h.y }
                if (isRow) for (x in 0 until gridSize) protectedCells.add(Position(x, h.y))
                else for (y in 0 until gridSize) protectedCells.add(Position(h.x, y))
            }
            val destinations = chosen.flatten()
            val donors = wood.asSequence()
                .filter { it !in protectedCells }
                .sortedWith(compareBy({ uselessness(it) }, { -it.y }, { it.x }))
                .take(destinations.size)
                .toList()
            if (donors.size == destinations.size) {
                // Empareja en orden: el donante más inútil llena el primer hueco.
                return destinations.sortedWith(compareBy({ it.y }, { it.x }))
                    .mapIndexed { i, dest -> PossessionMove(donors[i], dest) }
            }
            // No hay madera suficiente fuera de las líneas elegidas → se cae la más cara.
            val trimmed = ArrayList(chosen)
            trimmed.removeAt(trimmed.size - 1)
            chosen = trimmed
        }
        return emptyList()
    }
}
