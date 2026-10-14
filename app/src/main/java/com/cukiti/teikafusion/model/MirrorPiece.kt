package com.cukiti.teikafusion.model

/**
 * **P-147 · LA FICHA ESPEJO** (2026-09-03, familia B del catálogo de reglas de la casa).
 *
 * Al colocar una ficha, su **reflejo** se estampa a la vez en el lado opuesto del tablero. Una
 * jugada, dos huellas. Es la regla que más cambia lo que el jugador está haciendo sin cambiar nada
 * de lo que ve en la bandeja: deja de colocar fichas y pasa a **componer simetrías**, y el sitio
 * bueno deja de ser el que le va bien a la ficha para ser el que le va bien a las dos mitades.
 *
 * ## La decisión que la hace jugable: el reflejo es MEJOR ESFUERZO
 *
 * Había dos formas de resolverlo y solo una funciona:
 *
 *  - **Exigir que quepan las dos** (la colocación es ilegal si el reflejo no cabe). Suena más limpio
 *    y es una trampa: en cuanto el tablero se llena de un lado, media rejilla deja de admitir nada y
 *    el jugador pierde por una regla que no puede ver venir. Peor aún, `PieceTrayPlanner` certifica
 *    que cada ficha tiene UNA colocación válida — si el espejo pudiera invalidarla, la garantía que
 *    sostiene toda la familia B dejaría de ser cierta.
 *  - **Estampar el reflejo donde quepa** (lo implementado). Las celdas libres del reflejo reciben
 *    madera; las ocupadas se saltan. La colocación sigue siendo legal exactamente cuando lo sería sin
 *    la regla, así que **nada de lo que el juego ya garantiza se rompe**, y el jugador puede leer el
 *    resultado antes de soltar porque la sombra se lo enseña.
 *
 * Que el reflejo se coma menos celdas cuanto más lleno esté su lado no es un defecto: es la
 * negociación de la mecánica. Al principio del nivel el espejo duplica todo lo que haces —bueno y
 * malo— y según el tablero se llena va perdiendo fuerza sola.
 *
 * ## Lo que el reflejo NO hace
 *
 * **No pisa nada.** Ni madera, ni cristal, ni un muro del Rey, ni un pozo del Glotón, ni una celda
 * bajo un cuerpo de Teika. Quien llama le pasa el conjunto de celdas intocables y esta regla solo
 * devuelve las libres — la misma disciplina que la gravedad y la corriente, y por el mismo motivo:
 * media docena de sistemas guardan celdas por [Position] y escribir encima de ellas dejaría el
 * estado mintiendo.
 *
 * **El dial:** en los primeros biomas el espejo es **horizontal** (refleja de izquierda a derecha) y
 * en los últimos refleja **también en vertical**, así que una jugada deja hasta cuatro huellas. Es la
 * misma regla contando más.
 */
object MirrorPiece {

    /** Cómo refleja un nivel. */
    enum class Axis {
        /** De izquierda a derecha. Una jugada, dos huellas. */
        HORIZONTAL,
        /** Y además de arriba abajo: una jugada, hasta cuatro huellas. */
        BOTH,
    }

    /** Fracción de la campaña a partir de la cual el espejo refleja también en vertical. */
    const val BOTH_AXES_FROM = 0.5f

    fun appliesTo(level: Int): Boolean = HouseRule.isActive(level, HouseRule.MIRROR)

    /** El eje de reflejo de [level], o `null` si el nivel no lleva la regla. */
    fun axisFor(level: Int): Axis? = when {
        !appliesTo(level) -> null
        HouseRule.intensityFor(level) >= BOTH_AXES_FROM -> Axis.BOTH
        else -> Axis.HORIZONTAL
    }

    /**
     * Las celdas donde caería el reflejo de [cells], sin contar las que ya están ocupadas.
     *
     * [blocked] son las celdas intocables: madera, obstáculos de bioma, celdas bajo un cuerpo… y
     * **las de la propia colocación**, que quien llama tiene que incluir — el reflejo de una ficha
     * que cruza el eje se solapa consigo mismo, y estampar dos veces la misma celda dejaría la
     * segunda pisando a la primera.
     *
     * El resultado es un conjunto: si el eje vertical y el horizontal mandan dos reflejos a la misma
     * celda, se estampa una vez.
     */
    fun reflectionsOf(
        cells: Collection<Position>,
        axis: Axis,
        blocked: Set<Position>,
        width: Int = GameState.GRID_SIZE,
        height: Int = GameState.GRID_SIZE,
    ): Set<Position> {
        if (cells.isEmpty()) return emptySet()
        val out = LinkedHashSet<Position>()
        val original = cells.toSet()
        cells.forEach { p ->
            val mirrored = buildList {
                add(Position(width - 1 - p.x, p.y))
                if (axis == Axis.BOTH) {
                    add(Position(p.x, height - 1 - p.y))
                    add(Position(width - 1 - p.x, height - 1 - p.y))
                }
            }
            mirrored.forEach { m ->
                if (m.x !in 0 until width || m.y !in 0 until height) return@forEach
                // Una ficha centrada se refleja sobre sí misma: esas celdas ya las escribe la
                // colocación normal, así que aquí no cuentan como reflejo.
                if (m in original) return@forEach
                if (m in blocked) return@forEach
                out.add(m)
            }
        }
        return out
    }
}
