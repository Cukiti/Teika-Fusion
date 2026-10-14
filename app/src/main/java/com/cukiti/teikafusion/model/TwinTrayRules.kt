package com.cukiti.teikafusion.model

/**
 * Retirado de la campaña: appliesTo siempre devuelve false. Helpers puros dormidos para compatibilidad.
 *
 * **FICHAS GEMELAS** (P-76, 2026-08-30) — dos de las tres ranuras traen la MISMA forma.
 *
 * Suena a nada y cambia la partida entera: con una pareja idéntica en la bandeja, el tablero deja de
 * resolverse pieza a pieza y hay que **construir simetrías**; el hueco impar, que en una bandeja
 * normal se tapa con la siguiente ficha que venga, aquí se queda ahí mirándote.
 *
 * ## Gemelas EXACTAS, no espejadas
 *
 * La propuesta dejaba abierta la variante espejada y se descartó al implementarla: una pareja
 * espejada es, en la práctica, **otra forma que colocar**, así que diluye justo el efecto que la
 * ficha busca. Dos veces la misma pieza es la única versión que obliga a pensar en pares.
 *
 * ## Cada 5 niveles, y el 5 no es un número redondo cualquiera
 *
 * (Histórico: las gemelas ocupaban el puesto que ahora usa [HouseRule.RELAY], y los sabores —cinco desde
 * el 2026-09-25— se barajan por capítulo, así que esta cuenta de ciclos ya no decide nada.)
 *
 * Los otros dos ciclos del juego miden **4** (los retos centrales de [LevelDirector]) y **6** (los
 * sabores de bandeja de [LevelVariety]). Con cadencia 4, las gemelas caerían **siempre en el mismo
 * tipo de nivel**; con 6, siempre en el mismo sabor. El 5 es coprimo con los dos: mcm(5,4) = 20 y
 * mcm(5,6) = 30, así que la combinación no se repite dentro de ningún capítulo.
 *
 * ## La garantía de bandeja jugable manda sobre esto
 *
 * Gemelar NO puede romper lo que `PieceTrayPlanner` promete: que existe una colocación real para
 * cada ficha, que haya como mucho un monominó y al menos una pieza de 3+ celdas
 * ([LevelOpening.admitsMixedTray]). Por eso [allows] veta las parejas que dejarían la bandeja
 * indefendible, y el planificador **deja el plan como estaba** si no encuentra un segundo hueco para
 * la forma: un nivel sin gemelas es un nivel normal, y eso es infinitamente mejor que un nivel
 * imposible por culpa del repartidor (el fallo que costó los 14 niveles del damero, P-53).
 */
object TwinTrayRules {

    /** ¿Reparte [level] fichas gemelas? La intro y el tutorial nunca: allí se enseñan las reglas. */
    fun appliesTo(level: Int): Boolean = false // Retired; Relevo keeps this campaign slot.

    /**
     * ¿Se puede duplicar la forma de tamaño [twinSize] en una bandeja cuyos tamaños son [sizes],
     * sustituyendo la ficha del índice [replaced]?
     *
     * Las dos condiciones son las mismas que defiende el planificador, comprobadas sobre la bandeja
     * **resultante**:
     *
     *  - **Como mucho un monominó.** Gemelar una pieza de una celda daría dos, y una bandeja de
     *    monominós es la que gasta tres turnos para colocar tres bloques.
     *  - **Al menos una pieza de 3+ celdas.** Es la garantía de apertura, demostrada con un trominó
     *    + un dominó + una suelta, y la vigila `LevelOpeningTest` en los 293 niveles.
     */
    fun allows(sizes: List<Int>, twinSize: Int, replaced: Int): Boolean {
        if (replaced !in sizes.indices) return false
        if (twinSize <= 1) return false
        val after = sizes.toMutableList().also { it[replaced] = twinSize }
        if (after.count { it == 1 } > 1) return false
        return after.any { it >= 3 }
    }

    /**
     * A qué ficha de la bandeja se le copia la forma: se prefiere gemelar **la más grande**.
     *
     * Es la pareja que más cambia el problema — dos bloques macizos iguales obligan a partir el
     * tablero en dos mitades simétricas, mientras que dos dominós se colocan casi solos. Devuelve
     * los índices en el orden en que conviene intentarlo, para que el planificador pueda bajar al
     * siguiente si la geometría no admite el primero.
     */
    fun preferenceOrder(sizes: List<Int>): List<Int> =
        sizes.indices.sortedByDescending { sizes[it] }

    /**
     * Y el orden que le toca a [level]: **la pequeña primero en los biomas bajos, la grande en los
     * altos**.
     *
     * Es el dial de capítulo de esta regla (2026-09-03, ver [HouseRule.intensityFor]), y es el único
     * que no mueve un número sino una preferencia — porque aquí el número no da de sí: las ranuras
     * son tres, gemelar dos es la mecánica y gemelar las tres dejaría un nivel sin elección.
     *
     * La diferencia entre las dos puntas es real y se nota al jugar: **dos dominós se colocan casi
     * solos**, mientras que dos bloques macizos iguales obligan a partir el tablero en dos mitades
     * simétricas. Misma regla, el doble de problema.
     */
    fun preferenceOrderFor(level: Int, sizes: List<Int>): List<Int> =
        if (HouseRule.intensityFor(level) >= 0.5f) preferenceOrder(sizes)
        else sizes.indices.sortedBy { sizes[it] }.filter { sizes[it] > 1 }
}
