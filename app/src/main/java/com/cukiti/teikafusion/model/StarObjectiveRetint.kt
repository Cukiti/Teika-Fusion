package com.cukiti.teikafusion.model

/**
 * **NINGUNA ESTRELLA MUERTA** (2026-08-26, pedido del usuario).
 *
 * El objetivo de un nivel pide N estrellas de cada uno de hasta cuatro colores, y toda la figura de
 * salida nace marcada repartiendo esos colores en round-robin ([LevelLayouts]). El problema que
 * describió el usuario es el final de partida: en cuanto **un** color llega a su cuota, todos los
 * bloques de ese color que siguen en el tablero dejan de servir para nada. Siguen ahí, siguen
 * ocupando, siguen pintados como si fueran objetivo — pero romperlos ya no acerca la victoria. El
 * jugador ve estrellas por todas partes y un contador que no se mueve.
 *
 * La bandeja ya lo tenía resuelto desde hace tiempo: `planNextPieces` solo pinta las fichas nuevas
 * con los colores que **todavía faltan**. Lo que no estaba resuelto es lo que ya está en la pizarra
 * (y lo que ya está en la mano).
 *
 * ## La regla
 *
 * Cuando un color queda cubierto, sus estrellas vivas **se retintan** al color que más falta. No a
 * uno fijo: el reparto es proporcional al DÉFICIT, así que 12 estrellas huérfanas de un rojo ya
 * cubierto se dividen entre el azul al que le faltan 20 y el rosa al que le faltan 5 en la misma
 * proporción en la que hacen falta. Ninguna estrella se pierde y ningún color se sobrecarga.
 *
 * Consecuencia de balance, deliberada: recolectar se vuelve algo más fácil al final del nivel, y por
 * eso [BoardClutterCost.COLOR_MOVES] cobra menos por color de lo que costaría sin esta regla.
 *
 * El objeto es **puro**: no toca el estado, decide. El ViewModel aplica el plan y emite el evento
 * visual, y la UI anima la mutación.
 */
object StarObjectiveRetint {

    /** Colores del objetivo cuya cuota YA está cubierta: sus estrellas vivas no valen nada. */
    fun satisfiedColors(
        required: Map<TargetColor, Int>,
        collected: Map<TargetColor, Int>,
    ): Set<TargetColor> = required
        .filter { (color, needed) -> (collected[color] ?: 0) >= needed }
        .keys

    /** Lo que le falta a cada color del objetivo. Los cubiertos no aparecen. */
    fun deficits(
        required: Map<TargetColor, Int>,
        collected: Map<TargetColor, Int>,
    ): Map<TargetColor, Int> = required
        .mapValues { (color, needed) -> needed - (collected[color] ?: 0) }
        .filterValues { it > 0 }

    /**
     * A qué color va cada una de las [count] estrellas huérfanas, en orden.
     *
     * Se reparte por déficit: en cada vuelta se le da una al color al que MÁS le falta (descontando
     * lo ya asignado en esta misma tanda). Empate → gana el que antes salga en [TargetColor.entries],
     * así que dos partidas con el mismo estado retintan igual.
     *
     * Devuelve menos de [count] elementos cuando el objetivo se cubre por el camino: esas estrellas
     * ya no tienen destino útil y se quedan como están — el nivel está ganado de todas formas.
     */
    fun assign(
        required: Map<TargetColor, Int>,
        collected: Map<TargetColor, Int>,
        count: Int,
    ): List<TargetColor> {
        if (count <= 0) return emptyList()
        val pending = deficits(required, collected).toMutableMap()
        if (pending.isEmpty()) return emptyList()
        val out = ArrayList<TargetColor>(count)
        repeat(count) {
            val next = TargetColor.entries
                .filter { (pending[it] ?: 0) > 0 }
                .maxByOrNull { pending[it] ?: 0 }
                ?: return out
            out += next
            pending[next] = (pending[next] ?: 0) - 1
        }
        return out
    }

    /**
     * ¿Hay algo que retintar? Guarda barata para no recorrer el tablero en cada pasada de la cadena:
     * solo hay trabajo si el objetivo tiene a la vez algún color cubierto y algún color pendiente.
     */
    fun hasWork(
        required: Map<TargetColor, Int>,
        collected: Map<TargetColor, Int>,
    ): Boolean {
        if (required.size < 2) return false
        val satisfied = satisfiedColors(required, collected)
        return satisfied.isNotEmpty() && satisfied.size < required.size
    }

    /**
     * Plan completo para el tablero: qué celda pasa a qué color.
     *
     * [deadCells] son las posiciones —en orden estable— de la madera cuya estrella pertenece a un
     * color ya cubierto. Las celdas que se queden sin destino no aparecen en el mapa.
     */
    fun boardPlan(
        required: Map<TargetColor, Int>,
        collected: Map<TargetColor, Int>,
        deadCells: List<Position>,
    ): Map<Position, TargetColor> {
        val assignment = assign(required, collected, deadCells.size)
        if (assignment.isEmpty()) return emptyMap()
        return deadCells.take(assignment.size).mapIndexed { i, p -> p to assignment[i] }.toMap()
    }
}
