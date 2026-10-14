package com.cukiti.teikafusion.model

/**
 * **P-256 · LOS BLOQUES-TEIKA** (decisión del usuario, 2026-09-23). El Teika ya no nace de cualquier
 * línea rota: llega en la bandeja, **dentro de un bloque especial** de una ficha, y nace cuando una
 * línea rompe ESE bloque. Una fila sin bloque-Teika limpia y puntúa, pero no cría nada.
 *
 * ## Todas las fichas
 *
 * De cada [EVERY] fichas repartidas en el nivel, **una** lleva un bloque-Teika. Historia del dial: una de
 * cada tres → una de cada dos (el bot midió que el MEGA no llegaba, 2026-09-23) → **TODAS** (decisión
 * del usuario, 2026-09-24: cada ficha de la bandeja trae su bloque-Teika). Las herramientas y las fichas
 * especiales siguen sin llevarlo ([canCarry]). Se cuenta sobre el
 * total repartido (colocadas + las que hay en la mano), así que vale igual para la bandeja de tres,
 * la de cinco o la de horizonte, que reponen de una en una. Cuál de las dos lo lleva se deriva del
 * nivel y del grupo: fijo por nivel, como el resto de diales, pero no siempre la misma ranura.
 *
 * ## Crece con la jugada — y SOLO con la jugada (2026-09-26, decisión del usuario)
 *
 * Todo Teika nace **T1**. La única forma de que nazca grande es una jugada que rompa **2, 3 o 4 líneas
 * a la vez** llevándose **varios bloques-Teika**: hacen falta las dos cosas, y el tamaño es el MENOR
 * de los dos números, con techo en T3 ([tierFor]). Nace UNO grande por pasada; el resto, T1.
 *
 * Se retiraron el resto de caminos a un Teika grande: la fila pura ya no sube tier (ahora paga el
 * doble de puntos, `LineScoring`), el huevo nace T1, Parto Real pare T1, la portería se borró y el
 * rescate de revivir no crea un tier que no hubiera. Nunca nace un T4 ni un MEGA: se ganan fundiendo.
 */
object TeikaSeeds {

    /** Una ficha de cada tantas repartidas lleva bloque-Teika: 1 = todas (usuario, 2026-09-24). */
    const val EVERY = 1

    /**
     * ¿Se reparten bloques-Teika en [level]? Fuera del tutorial, siempre. En el tutorial los pone cada
     * lección a mano (la 2 enseña el bloque); el resto de lecciones no los necesita.
     */
    fun appliesTo(level: Int): Boolean = !TutorialCampaign.isTutorialLevel(level)

    /** Ranura (0 until EVERY) del grupo [group] que lleva el bloque. Derivada, no aleatoria. */
    fun slotFor(level: Int, group: Int): Int =
        Math.floorMod(level * 7 + group * 5 + (group / 3), EVERY)

    /** ¿Puede esta ficha llevar un bloque-Teika? Las herramientas y los especiales no. */
    fun canCarry(piece: Piece): Boolean =
        piece.shape.isNotEmpty() && !piece.isWildcard && !piece.isMagnet && !piece.isGrowing &&
            !piece.isStarWish && !piece.isBrush && !piece.isChameleon && piece.teikaMask.none { it }

    /**
     * La casilla de [piece] que lleva el bloque: la más central que no tenga ya estrella, cristal ni
     * barril (cada casilla dice UNA cosa); si todas llevan estrella, la más central de ellas (el
     * bloque la sustituye, ver [mark]). -1 si no hay ninguna.
     */
    fun cellFor(piece: Piece): Int {
        val cx = piece.shape.map { it.x }.average()
        val cy = piece.shape.map { it.y }.average()
        val noCrystalNorBarrel = piece.shape.indices.filter { i ->
            piece.crystalMask.getOrNull(i) != true && piece.barrelMask.getOrNull(i) != true
        }
        val central = compareBy<Int> { i ->
            val p = piece.shape[i]
            (p.x - cx) * (p.x - cx) + (p.y - cy) * (p.y - cy)
        }
        // Primero una casilla sin nada. Si no la hay —una ficha de estrellas las lleva en TODAS sus
        // casillas (`Piece.kt`)—, el bloque SUSTITUYE la estrella de la más central (2026-09-24, el
        // usuario vio que las fichas con estrellas nunca traían bloque-Teika). Un monominó con
        // estrella se queda sin bloque: quitarle la única estrella la convertiría en otra ficha.
        noCrystalNorBarrel.filter { piece.targetMask.getOrNull(it) == null }.minWithOrNull(central)?.let { return it }
        if (piece.shape.size < 2) return -1
        return noCrystalNorBarrel.minWithOrNull(central) ?: -1
    }

    /**
     * **P-361 · DOS BLOQUES EN LA FICHA GRANDE DEL PRINCIPIO** (2026-09-28). En los capítulos hasta
     * [TWO_SEEDS_LAST_CHAPTER] (el Cristal), una ficha de [TWO_SEEDS_MIN_SIZE]+ casillas lleva dos
     * bloques-Teika. La regla de nacimientos no cambia ([tierFor]): una jugada que rompa 2+ líneas llevándose
     * los dos cría un T2. Solo hace que pase más a menudo, y la masa del MEGA llega antes del atasco. El
     * Cristal era el techo del juego base con el MEGA obligatorio: 70 % con objetivo 85 %.
     */
    const val TWO_SEEDS_LAST_CHAPTER = 0
    const val TWO_SEEDS_MIN_SIZE = 5

    fun carriesTwo(piece: Piece, level: Int): Boolean =
        piece.shape.size >= TWO_SEEDS_MIN_SIZE &&
            LevelMap.chapterIndexForLevel(level) in 0..TWO_SEEDS_LAST_CHAPTER

    /** La segunda casilla más central (sin cristal ni barril, y mejor sin estrella), distinta de [first]. */
    fun secondCellFor(piece: Piece, first: Int): Int {
        val cx = piece.shape.map { it.x }.average()
        val cy = piece.shape.map { it.y }.average()
        val free = piece.shape.indices.filter { i ->
            i != first && piece.crystalMask.getOrNull(i) != true && piece.barrelMask.getOrNull(i) != true
        }
        val central = compareBy<Int> { i ->
            val p = piece.shape[i]
            (p.x - cx) * (p.x - cx) + (p.y - cy) * (p.y - cy)
        }
        return free.filter { piece.targetMask.getOrNull(it) == null }.minWithOrNull(central)
            ?: free.minWithOrNull(central) ?: -1
    }

    /**
     * Marca los bloques-Teika de un reparto. [dealtBefore] = fichas ya repartidas en el nivel antes de
     * este lote. Si la ficha de la ranura no puede llevarlo, lo lleva la siguiente del mismo grupo que
     * esté en este lote; si ninguna puede, ese grupo se queda sin bloque (antes que ponerlo en una
     * herramienta).
     */
    fun mark(pieces: List<Piece>, dealtBefore: Int, level: Int): List<Piece> {
        if (!appliesTo(level) || pieces.isEmpty()) return pieces
        val out = pieces.toMutableList()
        pieces.indices.groupBy { (dealtBefore + it) / EVERY }.forEach { (group, indices) ->
            val slot = slotFor(level, group)
            // La ranura del grupo tiene que caer en ESTE lote: si ya se repartió en uno anterior, el
            // bloque de este grupo ya se decidió allí (un grupo partido en dos lotes no gana dos).
            if (indices.none { (dealtBefore + it) % EVERY == slot }) return@forEach
            // Lo lleva la ficha de la ranura o, si no puede, la siguiente del grupo en este lote.
            val carrier = indices
                .filter { (dealtBefore + it) % EVERY >= slot }
                .firstOrNull { canCarry(out[it]) && cellFor(out[it]) >= 0 }
                ?: return@forEach
            val piece = out[carrier]
            val cell = cellFor(piece)
            // P-361: en los primeros capítulos, la ficha grande lleva un SEGUNDO bloque (ver [carriesTwo]).
            val second = if (carriesTwo(piece, level)) secondCellFor(piece, cell) else -1
            val seeds = setOf(cell, second).filter { it >= 0 }.toSet()
            out[carrier] = piece.copy(
                teikaMask = piece.shape.indices.map { it in seeds },
                // Cada casilla dice UNA cosa: si el bloque cae sobre una estrella, la sustituye.
                targetMask = if (seeds.none { piece.targetMask.getOrNull(it) != null }) piece.targetMask
                             else piece.targetMask.mapIndexed { i, c -> if (i in seeds) null else c },
            )
        }
        return out
    }

    /**
     * **Los tiers de una pasada** que rompió [linesInPass] líneas y [seedCount] bloques-Teika.
     * Uno grande ([tierFor]) y el resto T1 (P-292: el premio es de la jugada, no de cada bloque).
     */
    fun tiersForPass(linesInPass: Int, seedCount: Int): List<TeikaType> {
        if (seedCount <= 0) return emptyList()
        val big = tierFor(linesInPass, seedCount)
        return List(seedCount) { i -> if (i == 0) big else TeikaType.TEIKA_1 }
    }

    /**
     * Tamaño del Teika grande de una pasada: el MENOR entre líneas rotas y bloques-Teika rotos, con
     * techo en T3. Una sola línea, o un solo bloque, da siempre T1.
     *
     * 2 líneas y 3 bloques → T2 · 3 líneas y 2 bloques → T2 · 3 líneas y 3 bloques → T3 ·
     * 4 líneas y 4 bloques → T3.
     */
    fun tierFor(linesInPass: Int, seedCount: Int): TeikaType =
        when (minOf(linesInPass, seedCount).coerceAtMost(MAX_TIER)) {
            3 -> TeikaType.TEIKA_3
            2 -> TeikaType.TEIKA_2
            else -> TeikaType.TEIKA_1
        }

    /** El tier más alto con el que puede nacer un Teika. T4 y MEGA solo se ganan fundiendo. */
    const val MAX_TIER = 3
}
