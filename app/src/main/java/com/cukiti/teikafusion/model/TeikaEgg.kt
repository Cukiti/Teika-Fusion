package com.cukiti.teikafusion.model

import kotlin.random.Random

/**
 * RETIRADO 2026-10-09: helper de compatibilidad; Garra Teika ocupa sus niveles.
 *
 * **P-235 · EL HUEVO** (2026-09-22, elegida por el usuario para sustituir a LA COSECHA DOBLE).
 *
 * Hay un **huevo** en una casilla del tablero. No se puede poner nada encima, pero **cuenta como
 * casilla llena** para cerrar su fila y su columna. Cada línea que lo cruza lo **agrieta**, y a la
 * última grieta **eclosiona**: nace un Teika justo ahí. Después aparece otro huevo en otra casilla libre.
 *
 * (2026-09-26, decisión del usuario) Nace **T1**: nacía T2 (T3 en la segunda mitad), y la regla nueva
 * dice que solo una jugada de 2+ líneas cría Teikas grandes ([TeikaSeeds.tierFor]).
 *
 * ## Qué decide el jugador
 *
 * **Dónde va a nacer.** El jugador ve el huevo desde el turno 1 y sabe qué saldrá de él, así que
 * puede preparar el sitio: dejarle al lado un Teika del mismo tamaño para que se fundan nada más
 * nacer, o despejar el hueco para que el recién nacido no le tape la jugada. La Cosecha Doble
 * repartía cuerpos donde caían las líneas; aquí el cuerpo nace donde el jugador lo ha esperado.
 *
 * ## Por qué cuenta como lleno
 *
 * Un huevo que partiera la línea en dos la haría imposible de cerrar, y la regla se convertiría en
 * un castigo. Contándolo como lleno, cruzarlo es **más fácil** que no cruzarlo — que es la forma de
 * que el jugador vaya a buscarlo en vez de rodearlo. Mismo trato que dan a sus casillas los muros
 * del Rey y los pozos del Glotón.
 *
 * ## El dial
 *
 * Dos grietas en los primeros biomas, **tres** en los últimos; y lo que sale sube de T2 a T3 a mitad
 * del juego. Más trabajo, más premio.
 */
object TeikaEgg {

    const val CRACKS_FIRST_CHAPTER = 2
    const val CRACKS_LAST_CHAPTER = 3

    fun appliesTo(level: Int): Boolean = false // Retired: Garra Teika owns this campaign slot.

    /** Grietas que hacen falta para que eclosione en [level]. */
    fun cracksFor(level: Int): Int =
        HouseRule.dial(level, CRACKS_FIRST_CHAPTER, CRACKS_LAST_CHAPTER)
            .coerceIn(CRACKS_FIRST_CHAPTER, CRACKS_LAST_CHAPTER)

    /** Qué nace del huevo: siempre un T1 (2026-09-26, ver el KDoc de la clase). */
    @Suppress("UNUSED_PARAMETER")
    fun hatchTypeFor(level: Int): TeikaType = TeikaType.TEIKA_1

    /**
     * Dónde pone el huevo número [wave] de [level], o `null` si no hay sitio.
     *
     * Solo en casillas LIBRES de [blocked] (madera, cuerpos, marcas de bioma) y lejos del borde: un
     * huevo en la fila 0 o la 7 no deja sitio al Teika que va a nacer, y el recién nacido aplastaría
     * media fila al asentarse. Determinista por nivel y oleada.
     *
     * **P-313 · PEGADO A LA MADERA** (2026-09-26, decisión del usuario): entre las casillas libres, el
     * huevo elige una de las que tienen MÁS bloques de [wood] alrededor (las 8 vecinas). Antes caía al
     * azar y casi siempre en medio del hueco grande —el único sitio de la ficha grande de la bandeja—:
     * el bot perdió así el nivel 106 en la octava jugada. Pegado a los bloques sigue estorbando (es su
     * regla), pero no se come el espacio abierto. Sin [wood], como antes: al azar.
     */
    fun cellFor(
        level: Int,
        wave: Int,
        blocked: Set<Position>,
        width: Int = GameState.GRID_SIZE,
        height: Int = GameState.GRID_SIZE,
        wood: Set<Position> = emptySet(),
    ): Position? {
        if (!appliesTo(level) || width < 3 || height < 3) return null
        val candidates = (1 until height - 1).flatMap { y -> (1 until width - 1).map { x -> Position(x, y) } }
            .filter { it !in blocked }
        if (candidates.isEmpty()) return null
        val rng = Random(level.toLong() * 6007L + wave * 92821L)
        if (wood.isEmpty()) return candidates[rng.nextInt(candidates.size)]
        fun woodAround(p: Position) = (-1..1).sumOf { dy ->
            (-1..1).count { dx -> (dx != 0 || dy != 0) && Position(p.x + dx, p.y + dy) in wood }
        }
        val best = candidates.maxOf(::woodAround)
        val snug = candidates.filter { woodAround(it) == best }
        return snug[rng.nextInt(snug.size)]
    }

    /** ¿Cruza esta pasada el huevo? Sí si alguna de las casillas que limpia es la suya. */
    fun crossedBy(egg: Position?, lineCells: Set<Position>): Boolean = egg != null && egg in lineCells
}

/** Siembras para guardados de antes de una regla nueva. Ver [StainedGlass.seededFor]. */
object GameStateSeeds {
    /**
     * Un guardado de un nivel que hoy lleva huevo pero se guardó sin él (era de la cosecha doble):
     * se le pone el primero en una casilla libre. Sin esto la partida se jugaría sin la regla.
     */
    fun eggSeededFor(state: GameState): GameState {
        if (!TeikaEgg.appliesTo(state.level) || state.eggCell != null) return state
        val cell = TeikaEgg.cellFor(
            level = state.level,
            wave = state.eggWave,
            blocked = state.grid.filterValues { it.isOccupied }.keys + state.unplaceableCells +
                state.hotCells,
            wood = state.grid.filterValues { it is CellContent.Wood }.keys,
        )
        return state.copy(eggCell = cell, eggCracks = 0)
    }

    /** Lo mismo con la piñata, para los niveles que eran de la fila premiada. */
    fun pinataSeededFor(state: GameState): GameState {
        if (!Pinata.appliesTo(state.level) || state.pinataCell != null) return state
        return state.copy(
            pinataCell = Pinata.cellFor(state.level, state.pinataWave),
            pinataDir = Pinata.startDirFor(state.level, state.pinataWave),
            pinataHits = 0,
        )
    }

    /** Y el molde, para los niveles que eran del repetidor. */
    fun moldSeededFor(state: GameState): GameState {
        if (!MoldRule.appliesTo(state.level) || state.moldAt != null) return state
        val fit = MoldRule.firstFit(
            state.level, state.moldWave,
            blocked = state.grid.filterValues { it.isOccupied }.keys + state.unplaceableCells +
                state.hotCells,
        ) ?: return state
        return state.copy(moldShape = fit.shape, moldAt = fit.at, moldWave = fit.wave)
    }
}
