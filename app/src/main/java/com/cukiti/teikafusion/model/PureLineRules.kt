package com.cukiti.teikafusion.model

/**
 * **LÍNEA PURA y LA BROCHA** (P-69 + P-70, 2026-08-30) — las reglas puras del color.
 *
 * ## El hallazgo que las motiva
 *
 * La auditoría de variedad midió que **`colorType` no significaba NADA**. Los cinco colores de
 * madera solo llegaban a `BlockSkins`, `ParticleSystem` y los VFX: ninguna regla del motor leía el
 * color de un bloque. Un canal de información de cinco estados, en cada una de las 64 celdas, ya
 * pintado y sin usar.
 *
 * Y peor: el juego **mentía** con él. El tinte de `placePiece` §2.7 repinta las líneas completas con
 * el color de la ficha justo antes de romperlas, así que el color se reescribía en el instante en
 * que el jugador podría estar mirándolo.
 *
 * ## Las dos reglas, que se necesitan mutuamente
 *
 *  - **Línea Pura** ([isPure]): una línea cuya madera sea toda del mismo color engendra su Teika
 *    **un tier por encima** — un T2 en vez de un T1. Nunca castiga: quien no vea la regla juega
 *    exactamente igual que antes.
 *  - **La Brocha** ([brushCells]): una ficha que REPINTA de su color la fila y la columna que ocupa,
 *    complete línea o no. Es lo que convierte la Línea Pura en un plan en vez de en una lotería.
 *
 * Sin la Brocha, la pureza dependería de qué colores te reparta el azar. Con ella, colocar deja de
 * ser «dónde cabe» y pasa a ser «dónde cabe **y de qué color quiero que salga**».
 *
 * ## El orden es carga útil
 *
 * Dentro del turno tiene que ocurrir exactamente esto, y en este orden:
 *
 * ```
 * estampar la ficha  →  BROCHA (repinta)  →  medir la PUREZA  →  tinte §2.7  →  cadena
 * ```
 *
 * La pureza se mide **antes del tinte** y no es un detalle: el tinte repinta del color de la ficha
 * toda línea que quede completa, así que medida después, **cualquier línea cerrada por el jugador
 * sería pura siempre** y la regla se regalaría sola. Y la Brocha va **antes** de medir, porque su
 * único propósito es crear líneas puras.
 *
 * ## Qué cuenta y qué no
 *
 * Solo cuenta la **madera que de verdad se va a romper**. Los rellenos sin color —el cuerpo de un
 * Teika comodín, el muro del Rey, la lava, el pozo del Glotón— son **neutros**: ni aportan color ni
 * rompen la pureza. Cerrar una línea apoyándote en un Teika ya es caro; que además te negara la
 * pureza sería castigar dos veces la misma jugada (el mismo razonamiento por el que una fila
 * cerrada con Teika no se energiza en la Tormenta).
 *
 * La madera **bloqueada** —cristal con coraza, bobina sin encender, congelada, hormigón— tampoco
 * cuenta: no se rompe con la línea, así que no es parte de lo que el jugador ha compuesto.
 *
 * ## Lo que NO toca
 *
 * **La puntuación no cambia.** Un T2 nacido de una línea pura se salta la fusión T1+T1, o sea que
 * el jugador *renuncia* a esos 100 puntos de fusión a cambio de tempo hacia el MEGA. El premio es
 * ritmo, no puntos, y por eso los umbrales de 2★/3★ siguen valiendo lo que valían.
 */
object PureLineRules {

    /** Cada cuántas reposiciones de bandeja aparece una Brocha. */
    const val BRUSH_EVERY_REFILLS = 3

    /**
     * Cada cuántas reposiciones aparece una **CAMALEÓN** (P-72).
     *
     * 4 y no 3: con la misma cadencia que la Brocha caerían siempre en la misma bandeja y el jugador
     * tendría dos herramientas de color a la vez o ninguna. Con 3 y 4 coinciden una de cada doce, y
     * ni siquiera entonces se pisan — la Brocha va a la ficha más pequeña y la Camaleón a la más
     * grande, y [brushAndChameleonCollide] cubre el único caso en que serían la misma.
     */
    const val CHAMELEON_EVERY_REFILLS = 4

    /**
     * ¿Es PURA esta línea?
     *
     * @param colorAt color de la madera rompible de una celda, o `null` si esa celda no aporta color
     *   (vacía, relleno neutro, o madera bloqueada que no se va a romper).
     *
     * Devuelve `false` si no hay ni una celda con color: una línea que se completa entera con muro y
     * lava no la ha compuesto nadie, y premiarla sería premiar al bioma.
     */
    fun isPure(cells: List<Position>, colorAt: (Position) -> Int?): Boolean {
        var color: Int? = null
        for (cell in cells) {
            val c = colorAt(cell) ?: continue
            if (color == null) color = c
            else if (color != c) return false
        }
        return color != null
    }

    /**
     * Celdas que repinta una Brocha colocada con esta [footprint]: **toda fila y toda columna** que
     * la ficha ocupa, dentro del tablero.
     *
     * En cruz y no solo en la dirección larga: una brocha que solo pintara su fila obligaría a
     * plantearse la orientación de la ficha antes que el color, y el color es lo que esta mecánica
     * quiere poner en el centro de la decisión.
     */
    fun brushCells(footprint: Collection<Position>, width: Int, height: Int): Set<Position> {
        if (footprint.isEmpty()) return emptySet()
        val rows = footprint.map { it.y }.toSet()
        val cols = footprint.map { it.x }.toSet()
        val out = HashSet<Position>(rows.size * width + cols.size * height)
        for (y in rows) for (x in 0 until width) out.add(Position(x, y))
        for (x in cols) for (y in 0 until height) out.add(Position(x, y))
        return out
    }

    // (RETIRADO 2026-09-23) `spawnTypeFor`: el tier del Teika de una línea pura. Desde P-256 las líneas
    // no crían; el bloque-Teika sí, y la pureza le sube un tier en [TeikaSeeds.tierFor].

    /**
     * ¿Toca Brocha en esta reposición de bandeja?
     *
     * La cuenta de reposiciones ya existía (`GameState.trayRefillCount`) y se persiste, así que la
     * cadencia sobrevive a guardar y retomar la partida.
     */
    fun brushOnRefill(refillCount: Int, every: Int = BRUSH_EVERY_REFILLS): Boolean =
        refillCount > 0 && every > 0 && refillCount % every == 0

    /**
     * A qué ficha de la bandeja se le da la Brocha: **la más pequeña**.
     *
     * Una brocha sobre un bloque de seis celdas es una herramienta que casi no se puede colocar
     * donde hace falta; sobre una pieza pequeña, el jugador elige de verdad la fila y la columna que
     * quiere pintar. Devuelve el índice, o -1 si no hay fichas.
     */
    fun brushTargetIndex(sizes: List<Int>): Int {
        if (sizes.isEmpty()) return -1
        var best = 0
        for (i in sizes.indices) if (sizes[i] < sizes[best]) best = i
        return best
    }

    // ── LA CAMALEÓN (P-72) ───────────────────────────────────────────────────────────────────────

    /** ¿Toca Camaleón en esta reposición? Mismo contador persistido que la Brocha. */
    fun chameleonOnRefill(refillCount: Int, every: Int = CHAMELEON_EVERY_REFILLS): Boolean =
        refillCount > 0 && every > 0 && refillCount % every == 0

    /**
     * A qué ficha se le da la Camaleón: **la más grande**.
     *
     * Es la contraria de la Brocha, y no por simetría: una camaleón grande toca MUCHA madera al
     * aterrizar, así que la decisión «¿dónde la pongo para que salga del color que quiero?» tiene
     * información de verdad detrás. Sobre un monominó, el color lo decidiría un solo vecino.
     */
    fun chameleonTargetIndex(sizes: List<Int>): Int {
        if (sizes.isEmpty()) return -1
        var best = 0
        for (i in sizes.indices) if (sizes[i] > sizes[best]) best = i
        return best
    }

    /** Las dos herramientas no pueden caer en la MISMA ficha: una brocha sin color no pinta nada. */
    fun brushAndChameleonCollide(sizes: List<Int>): Boolean =
        sizes.isNotEmpty() && brushTargetIndex(sizes) == chameleonTargetIndex(sizes)

    /**
     * Color que adopta una Camaleón al aterrizar: **el que más toca**.
     *
     * @param neighbourColors color de cada celda de madera rompible que la huella toca (cada vecino
     *   una entrada; una celda tocada por dos bloques de la ficha cuenta una vez, eso lo resuelve el
     *   llamante). Los vecinos sin color —cuerpo de Teika, muro, lava, cristal con coraza— no entran.
     * @param boardFallback color dominante del tablero, para cuando la ficha no toca nada o hay
     *   empate.
     *
     * **El desempate va al color dominante del TABLERO**, no al último que jugaste (las dos opciones
     * que la propuesta dejaba abiertas). El motivo es que la Camaleón existe para servir a la Línea
     * Pura, y en un empate el color que más ayuda a cerrar una línea de un solo color es el que más
     * abunda. Además es una regla que el jugador puede VER en el tablero, mientras que «el último
     * que jugaste» le obligaría a recordar algo que no está en pantalla.
     */
    fun chameleonColorFor(neighbourColors: List<Int>, boardFallback: Int): Int {
        if (neighbourColors.isEmpty()) return boardFallback
        val counts = HashMap<Int, Int>()
        neighbourColors.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val top = counts.values.max()
        val winners = counts.filterValues { it == top }.keys
        return if (winners.size == 1) winners.first()
        else if (boardFallback in winners) boardFallback
        // Empate que el tablero tampoco rompe: el más bajo, por determinismo puro (mismo tablero,
        // mismo resultado — nada de azar en una decisión que el jugador está intentando planear).
        else winners.min()
    }
}
