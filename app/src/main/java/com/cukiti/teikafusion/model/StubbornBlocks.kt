package com.cukiti.teikafusion.model

import kotlin.math.ceil
import kotlin.random.Random

/**
 * **BLOQUES TESTARUDOS** (P-71, 2026-08-30) — la madera que exige que le hables en su color.
 *
 * Un bloque testarudo **solo se rompe si la línea que lo limpia trae suficientes bloques de SU
 * propio color**. Si no, aguanta: la línea se lleva todo lo demás y él se queda ahí, agrietándose.
 *
 * ## Qué añade, y qué NO
 *
 * No pide más trabajo, pide **otro orden**. Un nivel con testarudos exige las mismas líneas que uno
 * sin ellos; lo que cambia es que ya no da igual con qué color las cierras. Es la primera regla del
 * juego que convierte el color de un bloque en un **obstáculo** además de en un premio, y cierra el
 * triángulo con [PureLineRules]: la Línea Pura paga por el color, la Brocha y la Camaleón lo
 * fabrican, y el testarudo lo **exige**.
 *
 * ## El umbral, y por qué no es un número redondo a secas
 *
 * `min(`[REQUIRED]`, mitad del tramo)`. Tres de su color en una fila entera de ocho es un objetivo
 * real y legible («necesita tres de los suyos»). Pero el **Corte Ninja** parte el tablero en tramos
 * de cuatro, y ahí pedir tres de cuatro sería casi imposible: en media fila baja a dos. Es la
 * variante que la propuesta dejaba abierta —fijo o proporcional— resuelta como las dos cosas: fijo
 * donde se puede, proporcional donde el tramo no da.
 *
 * **El propio testarudo no cuenta para su umbral.** Si contara, un testarudo rodeado de dos de su
 * color se abriría solo, y la mecánica pide que le *lleves* el color, no que ya lo tenga.
 *
 * ## Lo que deliberadamente NO le afecta
 *
 *  - **La Brocha no lo repinta.** Igual que no repinta cristal ni bobinas: solo pinta madera que se
 *    va a romper. Si la Brocha pudiera cambiarle el color, el testarudo dejaría de exigir nada — le
 *    bastaría al jugador repintar el tablero entero de un color y todos se abrirían a la vez.
 *  - **No cuenta para la pureza.** Mismo criterio que la madera bloqueada: no se rompe con la línea
 *    (o no siempre), así que no es parte de lo que el jugador ha compuesto.
 *
 * ## Pero la Camaleón SÍ lo lee, y es deliberado
 *
 * Un testarudo sí cuenta como vecino para el color que adopta una Camaleón. No es una incoherencia
 * con lo de arriba: son dos preguntas distintas. La pureza pregunta «¿es de un solo color lo que el
 * jugador ha compuesto?», y un bloque que puede no romperse no forma parte de eso. La Camaleón
 * pregunta «¿de qué color hay más aquí?», y para eso un testarudo es información tan válida como
 * cualquier otra madera — de hecho es la más útil, porque **posar la Camaleón junto a un testarudo
 * es la forma de fabricar el color que ese testarudo exige**. Ese es el juego que las tres reglas de
 * color hacen posible junto a esta.
 *
 * ## El bloqueo que hereda del cristal, y que es lo que evita el softlock
 *
 * `clearableWood` lo trata como al cristal: **no cuenta como «hay algo que romper»**. Así una línea
 * formada SOLO por testarudos no se considera completa y no entra en la pasada de limpieza. Sin eso,
 * una línea en la que todos resisten se limpiaría sin cambiar nada y `resolveBoardChain` la volvería
 * a ver completa en la pasada siguiente: bucle infinito. El cristal ya resolvió este problema por
 * este camino y aquí se copia tal cual en vez de inventar otro.
 */
object StubbornBlocks {

    /** Bloques del mismo color que hacen falta en la línea. Ver el KDoc de la clase. */
    const val REQUIRED = 3

    /**
     * Cuántos se siembran. El mínimo es el dial de capítulo (2026-09-03, ver
     * [HouseRule.intensityFor]): tres testarudos en el Cristal y cinco en el Galáctico, sobre un
     * tablero que además llega mucho más lleno. La regla no cambia — sigue cediendo con tres bloques
     * de su color en la línea —, cambia cuántas veces hay que resolverla en el mismo nivel.
     */
    const val MIN_BLOCKS = 3
    const val MAX_BLOCKS = 5

    /** Cuántos testarudos siembra [level], antes del jitter de su RNG. */
    fun seedCountFor(level: Int): Int =
        HouseRule.dial(level, MIN_BLOCKS, MAX_BLOCKS).coerceIn(MIN_BLOCKS, MAX_BLOCKS)

    /**
     * ¿Siembra [level] bloques testarudos? La intro y el tutorial nunca.
     *
     * **Y tampoco un nivel que ya tenga otra mecánica de variedad** (P-128, 2026-09-03). Esa regla
     * ya existía —Horizonte esquiva a Gravedad, y Corriente esquiva a las dos, con tests que lo
     * vigilan por su nombre— pero el testarudo se había quedado fuera de la cadena, así que
     * **seis niveles llevaban dos mecánicas encima**: 77, 154 y 231 con Horizonte, y 91, 182 y 273
     * con Corriente.
     *
     * El par malo era el primero: el testarudo **exige que le lleves su color** y el horizonte deja
     * **una sola ficha, sin elegir**. La mecánica que pide elegir color junto a la que quita la
     * elección, y encima en el tramo medio, donde el color aún no se domina.
     *
     * Cede el testarudo y no los otros porque la cadena ya tenía una jerarquía implícita y esta la
     * respeta: **gana la mecánica más rara**. Gravedad sale una vez por capítulo, Horizonte 1 de
     * cada 11, Corriente 1 de cada 13 — y el testarudo, 1 de cada 7, es el más frecuente de los
     * cuatro. Se queda sin seis de sus ~41 apariciones y no se nota; al revés, Horizonte perdería
     * tres de sus veintitantas.
     */
    fun appliesTo(level: Int): Boolean = false // Retired; kept only for old save/API compatibility.

    /**
     * Cuántos bloques de su color necesita un testarudo para ceder, en un tramo de [segmentLength]
     * celdas. Nunca más de la mitad del tramo: ver el KDoc de la clase (Corte Ninja).
     */
    fun requiredFor(segmentLength: Int): Int {
        if (segmentLength <= 1) return 1
        val half = ceil(segmentLength / 2.0).toInt()
        return minOf(REQUIRED, half).coerceAtLeast(1)
    }

    /**
     * ¿RESISTE el testarudo?
     *
     * @param sameColorInLine cuántas celdas de la línea, **sin contarlo a él**, son madera rompible
     *   de su mismo color.
     */
    fun resists(sameColorInLine: Int, segmentLength: Int): Boolean =
        sameColorInLine < requiredFor(segmentLength)

    /**
     * Qué celdas de la figura de salida nacen testarudas, en [level].
     *
     * Se reparten entre las celdas que YA tiene la figura —no se añade madera nueva— para no tocar
     * el presupuesto de turnos ni la densidad que [BoardClutterCost] ya calculó. Deterministas, como
     * todo lo que define un nivel.
     *
     * **Llevan estrella, y no hay alternativa:** desde el 2026-08-01 la figura ENTERA lleva estrella,
     * así que filtrar las celdas con objetivo dejaría la piscina vacía y la mecánica no existiría.
     * Es asumible porque el contrajuego es claro y siempre está disponible —llévale tres de su
     * color— y porque son 3-5 bloques contra las ~60 estrellas que pide un nivel: retrasan una
     * estrella, no la niegan.
     */
    fun seedFor(level: Int, cells: List<LevelCell>): Set<Position> {
        if (!appliesTo(level)) return emptySet()
        val pool = cells
        if (pool.isEmpty()) return emptySet()
        val rng = Random(level.toLong() * 65521L)
        val count = seedCountFor(level).coerceAtMost(pool.size)
        return pool.shuffled(rng).take(count).mapTo(HashSet()) { Position(it.x, it.y) }
    }
}
