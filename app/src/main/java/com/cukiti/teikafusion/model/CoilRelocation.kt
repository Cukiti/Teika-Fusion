package com.cukiti.teikafusion.model

/**
 * **LA BOBINA NO SE CANCELA: SE MUDA.**
 *
 * Regla pura de la Tormenta para cuando algo reclama la celda en la que vive una bobina. Nació de
 * un atasco de física (2026-09-05): un Teika que NACE —de una fusión, de una línea, de una
 * habilidad— aplasta la madera que su círculo pisa (`GameViewModel.crushWoodUnderCircle`) para no
 * quedar nunca superpuesto a bloques… salvo la bobina, que se saltaba porque «es obstáculo duro».
 *
 * El problema es que la bobina es **terreno NO rompible**: `TeikaPushBreakRules.canBreak` la
 * excluye, y ese conjunto es justo el que usa la red anti-encaje de la física
 * (`TeikaPhysicsWorld.detectForcedTerrainBreaks`) para desatascar un cuerpo que penetra terreno
 * del que no puede salir. O sea: el único sitio del tablero donde un cuerpo podía quedarse
 * encajado **para siempre**, porque la salida de emergencia estaba desactivada precisamente ahí.
 * Y encima en la fila que bloquea su propia fila y sus 3-6 columnas hasta que la descargues.
 *
 * La salida no es romper la bobina —eso regala el puzzle— ni dejar el cuerpo encajado. Es la misma
 * que ya se eligió en P-131 cuando el testarudo y la bobina se disputaban una celda:
 *
 * > La bobina no se cancela — se coloca en otra madera lisa de la fila.
 *
 * El puzzle sobrevive entero: `GameViewModel.tapCoil` deduce el siguiente número con
 * `min { !coilLit }` de la fila, así que le da igual en qué columna esté cada bobina. Lo que NO
 * puede perderse es el número, y por eso se muda con su `coilLit`/`coilSeen` puestos: lo que el
 * jugador ya dedujo sigue deducido.
 *
 * Si la fila no tiene ni una madera lisa libre, la bobina **se pierde** (la devuelve como `null` y
 * el llamante rompe la celda como madera normal). Es seguro: la secuencia se lee por mínimo no
 * encendido, así que una fila 1-2-4-5 se resuelve igual que una 1-2-3-4. Perder un número es una
 * fila más fácil; dejar un Teika encajado es un nivel imposible.
 */
object CoilRelocation {

    /**
     * Madera LISA de la fila [row]: la que no lleva ya una marca encima y por tanto puede acoger la
     * bobina. Mismo criterio que `plainWoodInRow` en la energización (`resolveLineClearPass`) — si
     * los dos divergieran, la mudanza podría poner una bobina donde el bioma nunca la habría puesto.
     *
     * [blocked] son las celdas que el llamante ya ha reclamado (el círculo del Teika que nace, el
     * hielo del Glaciar): mudarse ahí sería mudarse al mismo problema.
     */
    fun plainWoodInRow(
        grid: Map<Position, CellContent>,
        row: Int,
        width: Int,
        blocked: Set<Position> = emptySet(),
    ): List<Position> = (0 until width).map { Position(it, row) }.filter { p ->
        if (p in blocked) return@filter false
        val w = grid[p] as? CellContent.Wood ?: return@filter false
        w.coil == 0 && w.armor == 0 && !w.stubborn && w.colorType != WILDCARD_COLOR
    }

    /**
     * A dónde se muda la bobina de [from], o `null` si la fila no tiene sitio.
     *
     * Elige la madera lisa **más cercana** a la celda de origen (y la de menor x al empatar, para
     * que sea determinista y testeable): la bobina se aparta lo justo, así que el jugador la ve
     * moverse un paso en vez de aparecer al otro lado del tablero.
     */
    fun destinationFor(
        grid: Map<Position, CellContent>,
        from: Position,
        width: Int,
        blocked: Set<Position> = emptySet(),
    ): Position? = plainWoodInRow(grid, from.y, width, blocked)
        .minWithOrNull(compareBy({ kotlin.math.abs(it.x - from.x) }, { it.x }))
}
