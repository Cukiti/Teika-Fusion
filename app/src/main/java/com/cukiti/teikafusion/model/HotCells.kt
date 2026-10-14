package com.cukiti.teikafusion.model

import kotlin.random.Random

/**
 * **CELDAS CALIENTES** (P-78, 2026-08-30) — geometría-objetivo que no le quita sitio a nadie.
 *
 * Tres o cuatro celdas marcadas al empezar el nivel. **Se colocan encima con total normalidad**: no
 * bloquean, no estorban y no cuestan un solo turno de presupuesto ([BoardClutterCost] no las cobra
 * porque no ocupan nada). Lo único que hacen es pagar: la línea que cruce una se lleva
 * [HOT_CELL_SCORE] puntos extra y la celda se apaga.
 *
 * ## Por qué existe
 *
 * El tablero no tenía un **dónde**. Todas las celdas valían lo mismo, así que la única pregunta de
 * cada turno era «¿dónde cabe esta ficha?». Con tres o cuatro puntos calientes, la pregunta pasa a
 * ser «¿por dónde hago pasar la línea?» — que es una decisión de ruta, no de encaje.
 *
 * ## Por qué NO es una silueta, ni un obstáculo, ni un Teika sembrado
 *
 * Porque todo eso ya se probó y se retiró el 2026-08-28: encoger el tablero acorrala a los Teikas.
 * Una celda caliente es la versión que **no quita ni un hueco** — es una marca pintada en el suelo.
 *
 * ## Por qué el premio son PUNTOS (decisión del usuario, 2026-08-30)
 *
 * Es la moneda menos enredada de las tres que se plantearon. No acorta el nivel (que es lo que haría
 * regalar estrellas del objetivo), no toca la condición de victoria —terreno de la decisión
 * congelada del 2026-08-01— y no pisa el premio de [PureLineRules], que ya es *tempo* hacia el MEGA.
 * Y los umbrales de 2★/3★ existen justamente para absorber puntos: [HOT_CELL_SCORE] × 4 celdas es
 * ~12 % de un umbral de 3★ típico, se nota sin decidir la calificación.
 *
 * ## Puro y determinista
 *
 * Las posiciones salen del número de nivel, como todo lo demás del director, así que un nivel ofrece
 * siempre las mismas. Pero **sí se serializan** (`GameState.hotCells`): se van gastando durante la
 * partida y hay que recordar cuáles quedan al retomar un guardado.
 */
object HotCells {

    /**
     * Lo que paga cruzar una celda caliente.
     *
     * **250 → 300 (P-159, 2026-09-04, aprobada por el usuario).** Es la otra mitad del racionamiento
     * de [appliesTo]: las calientes salían en el **100 %** de los niveles, así que habían dejado de
     * marcar nada — una marca que está siempre es mobiliario. Ahora salen en uno de cada tres y a
     * cambio pagan más, para que la decisión de ruta que proponen valga la pena cuando aparecen.
     *
     * **El primer intento fue 400 y lo tumbó su propio guardián de balance**, que es exactamente
     * para lo que estaba escrito: cuatro calientes a 400 son **1.600 sobre los 6.500 del umbral de
     * 3★ del nivel 8, o sea el 25 %**, y el contrato dice que el premio tiene que notarse sin
     * decidir la calificación (tope del 20 %). Con 300 se queda en el 18,5 %.
     *
     * Y ese tope manda sobre la intuición de «si sale tres veces menos, que pague tres veces más»:
     * compensar la rareza habría pedido 750 y habría convertido las calientes en la forma más rápida
     * de sacar tres estrellas. Lo que P-159 arregla es que **signifiquen** algo, no que rindan lo
     * mismo repartido en menos niveles.
     */
    const val HOT_CELL_SCORE = 300

    /**
     * Cada cuántos niveles se siembran calientes (P-159).
     *
     * Tres. No es un número al azar: con uno de cada tres, el jugador se encuentra una racha de dos
     * niveles sin ellas y a la tercera vuelven, que es la cadencia con la que una marca se **echa de
     * menos** en vez de darse por supuesta. Menos frecuente las convertiría en una rareza que no da
     * tiempo a aprender a usar.
     */
    const val EVERY_LEVELS = 3

    /**
     * ¿Siembra [level] celdas calientes?
     *
     * **Determinista y sin RNG**, como todo lo que decide un nivel en este juego: se deriva del
     * número, así que no se serializa y un guardado viejo recupera sus calientes al cargarse.
     *
     * Es independiente del catálogo de reglas de la casa a propósito: las calientes **no son** una
     * regla de la casa —no cambian cómo se juega el tablero, solo dónde conviene hacer pasar una
     * línea— así que no compiten por la única ranura de regla que tiene cada nivel. Lo que se
     * arregla aquí es que dejaran de significar algo por salir siempre.
     */
    fun appliesTo(level: Int): Boolean =
        level > LevelMap.introLevels &&
            !TutorialCampaign.isTutorialLevel(level) &&
            level % EVERY_LEVELS == 0

    /** Cuántas se siembran, según cuánto trabajo pide el nivel. */
    const val MIN_CELLS = 3
    const val MAX_CELLS = 4

    /**
     * Celdas calientes de [level] sobre un tablero [width]×[height].
     *
     * **Filas y columnas distintas, siempre.** Dos calientes en la misma fila las cobraría la misma
     * línea de un golpe, y entonces no son dos decisiones de ruta: son una. Repartirlas obliga a
     * planear varias líneas distintas, que es justo lo que la mecánica quiere.
     *
     * Se evitan las celdas ocupadas por la figura de salida cuando se puede — una caliente bajo un
     * bloque se ve peor— pero **no es un requisito**: si no hay suficientes libres, se usan las que
     * haya antes que devolver menos de [MIN_CELLS].
     */
    fun forLevel(
        level: Int,
        width: Int = GameState.GRID_SIZE,
        height: Int = GameState.GRID_SIZE,
        occupied: Set<Position> = emptySet(),
    ): Set<Position> {
        if (!appliesTo(level)) return emptySet()
        if (width <= 0 || height <= 0) return emptySet()
        val rng = Random(level.toLong() * 7919L)
        val count = (MIN_CELLS + rng.nextInt(MAX_CELLS - MIN_CELLS + 1))
            .coerceAtMost(minOf(width, height))

        val rows = (0 until height).shuffled(rng)
        val cols = (0 until width).shuffled(rng)
        // Una pasada prefiriendo celdas libres; si no salen suficientes, se repite admitiendo las
        // ocupadas. Dos pasadas y no una búsqueda: es determinista y O(filas).
        for (allowOccupied in listOf(false, true)) {
            val out = LinkedHashSet<Position>()
            for (i in 0 until minOf(rows.size, cols.size)) {
                if (out.size >= count) break
                val p = Position(cols[i], rows[i])
                if (!allowOccupied && p in occupied) continue
                out.add(p)
            }
            if (out.size >= count) return out
            if (allowOccupied) return out
        }
        return emptySet()
    }

    /** Puntos que paga limpiar [cells], de las cuales las que estén en [hot] son calientes. */
    fun scoreFor(cleared: Set<Position>, hot: Set<Position>): Int =
        collect(cleared, hot).size * HOT_CELL_SCORE

    /** Las calientes que esta limpieza se lleva por delante. */
    fun collect(cleared: Set<Position>, hot: Set<Position>): Set<Position> =
        if (hot.isEmpty()) emptySet() else hot.filterTo(HashSet()) { it in cleared }
}
