package com.cukiti.teikafusion.model

/**
 * Reglas puras del bioma Pantano Tóxico. Sin estado ni Android — unit-testeable.
 *
 * **EL VENENO ES UNA CAPA** (mini-rework del 2026-09-28, decisión del usuario). Se retiran la
 * putrefacción a compás fijo, el lodo como castigo y la recarga del charco central: medido con el
 * bot, pudrir y salpicar hacía el capítulo MÁS fácil (más bloques envenenados = más puntos al romperlos).
 *
 *  - **Se extiende**: cada colocación SIN romper línea, el veneno avanza un paso de BFS, uniforme, a
 *    todos los bloques vecinos (4 direcciones) de los envenenados — de bloque a bloque, nunca a la ficha
 *    recién colocada ([spreadStep]).
 *  - **Salpica**: si no alcanza ningún bloque nuevo, tras [SPLASH_AFTER_TURNS] colocaciones seguidas sin
 *    romper líneas salpica [SPLASH_COUNT] bloques en otras zonas del tablero, lejos del veneno
 *    ([splashTargets]), para seguir invadiendo.
 *  - **Se lava**: una línea con bloques envenenados SÍ se rompe, pero en ellos solo QUITA el veneno —el
 *    bloque de debajo queda al descubierto, sin romperse— y se rompe limpio en la siguiente limpieza
 *    (patrón de la coraza del cristal). El motor marca esos bloques para no romperlos dos veces en la
 *    misma jugada.
 */
object ToxicBiomeRules {

    /** Colocaciones seguidas sin romper líneas (y sin avance) antes de que el veneno salpique. */
    const val SPLASH_AFTER_TURNS = 2

    /** Bloques que envenena cada salpicadura. */
    const val SPLASH_COUNT = 3

    /** Distancia mínima (en casillas, Chebyshev) de la salpicadura al veneno que ya hay: «otra zona». */
    const val SPLASH_MIN_DISTANCE = 3

    /** Color de partículas al lavar veneno (índice de paleta verde lima clásica). */
    const val POISON_CLEAR_PARTICLE_COLOR = 2

    private fun neighbours(p: Position) = listOf(
        Position(p.x, p.y - 1), Position(p.x + 1, p.y), Position(p.x, p.y + 1), Position(p.x - 1, p.y),
    )

    /**
     * Un paso de la expansión: cada bloque que [canPoison] admita y que toque (4 direcciones) un
     * envenenado, con el envenenado del que le llega (el primero en orden de lectura, para que el VFX
     * dibuje el líquido fluyendo de una casilla concreta). Determinista: sin azar.
     * @return pares (origen, destino), un destino una sola vez.
     */
    fun spreadStep(
        poisoned: Set<Position>,
        canPoison: (Position) -> Boolean,
    ): List<Pair<Position, Position>> {
        val out = LinkedHashMap<Position, Position>()
        for (from in poisoned.sortedWith(compareBy({ it.y }, { it.x }))) {
            for (to in neighbours(from)) {
                if (to in poisoned || to in out) continue
                if (canPoison(to)) out[to] = from
            }
        }
        return out.map { (to, from) -> from to to }
    }

    /**
     * ¿Toca salpicar? El contador [stall] cuenta colocaciones seguidas sin romper líneas en las que la
     * expansión no alcanzó ningún bloque.
     */
    fun shouldSplash(stall: Int, after: Int = SPLASH_AFTER_TURNS): Boolean = stall >= after

    /**
     * Dónde cae la salpicadura: [count] bloques de [candidates] a [minDistance] o más (Chebyshev) de
     * todo el veneno de [poisoned] y entre sí; si no hay tantos tan lejos, los más lejanos que haya.
     */
    fun splashTargets(
        poisoned: Set<Position>,
        candidates: List<Position>,
        random: kotlin.random.Random,
        count: Int = SPLASH_COUNT,
        minDistance: Int = SPLASH_MIN_DISTANCE,
    ): List<Position> {
        val chosen = ArrayList<Position>(count)
        fun dist(p: Position): Int {
            val all = poisoned + chosen
            if (all.isEmpty()) return Int.MAX_VALUE
            return all.minOf { maxOf(kotlin.math.abs(it.x - p.x), kotlin.math.abs(it.y - p.y)) }
        }
        val pool = candidates.shuffled(random).toMutableList()
        while (chosen.size < count && pool.isNotEmpty()) {
            val far = pool.filter { dist(it) >= minDistance }
            val pick = far.firstOrNull() ?: pool.maxByOrNull { dist(it) } ?: break
            chosen += pick
            pool.remove(pick)
        }
        return chosen
    }

    /** El veneno más cercano a [target] (de ahí sale la gota de la salpicadura), o `null` si no hay. */
    fun nearestSource(poisoned: Set<Position>, target: Position): Position? =
        poisoned.minByOrNull { maxOf(kotlin.math.abs(it.x - target.x), kotlin.math.abs(it.y - target.y)) }

    /**
     * ¿Puede envenenarse este bloque?
     * Excluye: ya envenenados, lodo (de guardados viejos), armadura, bobina, lava, casillas cerradas,
     * la ficha de este turno y lo que tapa un Teika. Las estrellas SÍ (2026-09-23).
     */
    fun isSpreadCandidate(
        wood: CellContent.Wood,
        pos: Position,
        lavaRow: Int,
        unplaceable: Set<Position>,
        placedThisTurn: Set<Position>,
        teikaOccupied: Set<Position>
    ): Boolean {
        if (wood.poisoned || wood.sludge) return false
        if (wood.armor > 0 || wood.coil > 0) return false
        if (pos.y < lavaRow) return false
        if (pos in unplaceable || pos in placedThisTurn || pos in teikaOccupied) return false
        return true
    }

    /**
     * Siembra de layout. Por compatibilidad [allowTarget] es false: los llamantes históricos nunca
     * envenenan estrellas. El director de niveles puede activarlo: lavar el veneno no rompe el bloque,
     * así que la estrella sigue ahí para la siguiente línea.
     */
    fun shouldSeedPoison(
        isCrystal: Boolean,
        hasTarget: Boolean,
        roll: Float,
        chance: Float = 0.30f,
        allowTarget: Boolean = false
    ): Boolean = !isCrystal && (!hasTarget || allowTarget) && roll < chance
}
