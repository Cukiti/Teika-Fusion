package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

import java.util.UUID

/**
 * Representa una pieza (polyominó) que el jugador puede arrastrar.
 * Consta de una lista de posiciones relativas a su esquina superior izquierda (0,0).
 */
@Serializable
data class Piece(
    val id: String = UUID.randomUUID().toString(),
    val shape: List<Position>,
    val colorType: Int = 0,
    val isWildcard: Boolean = false,
    // targetMask[i] corresponde a shape[i] — null = sin target en ese bloque
    val targetMask: List<TargetColor?> = emptyList(),
    // Bioma Cristal: crystalMask[i]=true → al colocar nace Wood con armor=1 (1 clear quita el
    // cristal; el 2.º rompe el bloque). placePiece estampa armor=1, no 2.
    // (Se RETIRARON coilMask, chronoMask, special, cutCursed, inkCursed, shadowCursed —
    //  `ignoreUnknownKeys` traga guardados viejos que aún los traigan.)
    val crystalMask: List<Boolean> = emptyList(),
    // P-234 · REACCIÓN EN CADENA: barrelMask[i]=true → esa casilla nace como BARRIL (Wood.barrel).
    val barrelMask: List<Boolean> = emptyList(),
    // P-256 · BLOQUE-TEIKA: teikaMask[i]=true → esa casilla nace como bloque-Teika (Wood.teikaSeed) y,
    // al romperla una línea, suelta un Teika. Uno por bandeja (ver [TeikaSeeds]).
    val teikaMask: List<Boolean> = emptyList(),
    // Estrella / Deseo Estelar: ficha-estrella. Al colocarla se derrama rellenando vacío conexo.
    val isStarWish: Boolean = false,
    /**
     * **LA BROCHA** (P-70): al colocarse, esta ficha REPINTA de su color toda fila y columna que
     * ocupa, complete línea o no.
     *
     * Existe para que [PureLineRules] sea jugable. El color de un bloque no lo leía **ninguna**
     * regla del motor (auditoría de variedad, 2026-08-30): era un canal de cinco estados en cada
     * celda, ya pintado y sin usar. La Línea Pura le da premio, y la Brocha es la herramienta que
     * convierte ese premio en un plan en vez de en una lotería.
     */
    val isBrush: Boolean = false,
    /**
     * **LA CAMALEÓN** (P-72): la ficha sale GRIS de la bandeja y adopta al aterrizar el color de la
     * madera con la que más contacto hace.
     *
     * Es la tercera pata del sistema de color, y la que convierte colocar en una decisión doble:
     * deja de ser «dónde cabe» y pasa a ser «dónde cabe **y de qué color quiero que salga**». Sirve
     * a [PureLineRules.isPure] igual que [isBrush], pero desde el otro lado — la Brocha impone su
     * color al tablero, la Camaleón deja que el tablero le imponga el suyo.
     *
     * Nunca convive con [isBrush] en la misma ficha: una brocha sin color no tiene nada que pintar.
     */
    val isChameleon: Boolean = false,
    /**
     * **LA FICHA QUE CRECE** (P-77): cada turno que pasa sin usarla gana un bloque, hasta
     * [GrowingPiece.MAX_CELLS]. Ver `GrowingPiece` para por qué crece sola y no alimentándola.
     */
    val isGrowing: Boolean = false,
    /**
     * **EL IMÁN** (P-240): una ficha de una casilla que, al colocarse, atrae a los Teikas durante
     * [MagnetPiece.TURNS] colocaciones. La casilla que ocupa se estampa como madera marcada con
     * [CellContent.Wood.magnet], que es lo que la sigue si algo la mueve.
     */
    val isMagnet: Boolean = false,
    /** P-349: ficha DIBUJADA por el jugador con el banco de bloques (lleva su propio marco). */
    val isHandmade: Boolean = false,
    /** Fichona: fabricated connected piece, fragile on an explicit invalid drag release. */
    val isFichona: Boolean = false,
) {
    val width: Int  = (shape.maxOfOrNull { it.x } ?: 0) + 1
    val height: Int = (shape.maxOfOrNull { it.y } ?: 0) + 1

    fun rotate(): Piece {
        val rotated = shape.map { Position(-it.y, it.x) }
        val minX = rotated.minOf { it.x }
        val minY = rotated.minOf { it.y }
        val normalizedWithIndex = rotated
            .mapIndexed { i, p -> Pair(Position(p.x - minX, p.y - minY), i) }
            .sortedWith(compareBy({ it.first.y }, { it.first.x }))
        val newShape = normalizedWithIndex.map { it.first }
        val newMask  = if (targetMask.size == shape.size) normalizedWithIndex.map { targetMask[it.second] }
                       else emptyList()
        val newCrystal = if (crystalMask.size == shape.size) normalizedWithIndex.map { crystalMask[it.second] }
                       else emptyList()
        val newBarrel = if (barrelMask.size == shape.size) normalizedWithIndex.map { barrelMask[it.second] }
                       else emptyList()
        val newTeika = if (teikaMask.size == shape.size) normalizedWithIndex.map { teikaMask[it.second] }
                       else emptyList()
        return this.copy(shape = newShape, targetMask = newMask, crystalMask = newCrystal, barrelMask = newBarrel,
            teikaMask = newTeika)
    }

    companion object {
        /** Bioma Cristal: tamaño máximo de una ficha que puede traer cristal (2026-09-26). */
        const val CRYSTAL_MAX_PIECE_CELLS = 4

        // ── Catálogo de formas ───────────────────────────────────────────────────────────
        // 25 formas distintas girando (`PieceTrayPlanner` las canoniza). El comentario que había
        // aquí decía «solo piezas cómodas (≤4 bloques) y las rectangulares grandes», y dejó de ser
        // verdad con los pentominós; desde P-207 (2026-09-06) el tramo grande tiene también formas
        // con recovecos. El reparto real lo decide el planificador, no este orden.
        internal val SHAPES = listOf(
            // ─ 1 bloque
            listOf(Position(0,0)),
            // ─ 2 bloques
            listOf(Position(0,0), Position(1,0)),
            listOf(Position(0,0), Position(0,1)),
            // ─ 3 bloques — líneas
            listOf(Position(0,0), Position(1,0), Position(2,0)),
            listOf(Position(0,0), Position(0,1), Position(0,2)),
            // ─ 3 bloques — ángulos
            listOf(Position(0,0), Position(1,0), Position(1,1)),
            listOf(Position(1,0), Position(0,1), Position(1,1)),
            listOf(Position(0,0), Position(0,1), Position(1,1)),
            listOf(Position(1,0), Position(1,1), Position(0,1)),
            // ─ 4 bloques — líneas
            listOf(Position(0,0), Position(1,0), Position(2,0), Position(3,0)),
            listOf(Position(0,0), Position(0,1), Position(0,2), Position(0,3)),
            // ─ 4 bloques — cuadrado 2x2
            listOf(Position(0,0), Position(1,0), Position(0,1), Position(1,1)),
            // ─ 4 bloques — L y J
            listOf(Position(0,0), Position(0,1), Position(0,2), Position(1,2)),
            listOf(Position(1,0), Position(1,1), Position(1,2), Position(0,2)),
            listOf(Position(0,0), Position(1,0), Position(2,0), Position(2,1)),
            listOf(Position(0,0), Position(1,0), Position(2,0), Position(0,1)),
            // ─ 4 bloques — S y Z
            listOf(Position(1,0), Position(2,0), Position(0,1), Position(1,1)),
            listOf(Position(0,0), Position(1,0), Position(1,1), Position(2,1)),
            // ─ 4 bloques — T
            listOf(Position(1,0), Position(0,1), Position(1,1), Position(2,1)),
            listOf(Position(0,0), Position(0,1), Position(1,1), Position(0,2)),
            // ─ 5 bloques — nuevas fichas
            listOf(Position(0,0), Position(1,0), Position(2,0), Position(3,0), Position(4,0)), // I5
            listOf(Position(0,0), Position(0,1), Position(0,2), Position(0,3), Position(1,3)), // L5
            listOf(Position(0,0), Position(2,0), Position(0,1), Position(1,1), Position(2,1)), // U
            listOf(Position(1,0), Position(0,1), Position(1,1), Position(2,1), Position(1,2)), // cruz
            listOf(Position(0,0), Position(0,1), Position(1,1), Position(1,2), Position(2,2)), // W
            // ─ P-207 (2026-09-06) · EL TRAMO GRANDE TENÍA UNA SOLA FICHA POR TAMAÑO ───────────
            // Con el suelo de tamaño nuevo la bandeja pide más a menudo fichas de 5 a 8 celdas, y
            // ahí el catálogo se estrechaba de golpe: **una sola forma de 6 celdas, una de 8 y
            // NINGUNA de 7**, así que pedir ese tamaño era pedir esa ficha exacta — el 2×3 y el 4×2
            // eran, entre los dos, el 40 % de todo lo repartido. Las cinco de abajo son las clásicas
            // que faltaban, todas con recovecos (ninguna es barra ni bloque macizo, así que entran
            // en el sabor JAGGED y no tocan los sabores BARS ni CHUNKY).
            listOf(Position(0,0), Position(0,1), Position(0,2), Position(1,2), Position(2,2)), // V5 (esquina 3×3)
            listOf(Position(0,0), Position(1,0), Position(2,0), Position(1,1), Position(1,2)), // T5
            listOf(Position(0,0), Position(1,0), Position(0,1), Position(1,1), Position(0,2)), // P5
            listOf(Position(0,0), Position(1,0), Position(2,0), Position(3,0),
                   Position(0,1), Position(0,2)),                                          // L6 (brazo largo)
            listOf(Position(0,0), Position(1,0), Position(0,1), Position(1,1),
                   Position(0,2), Position(1,2), Position(2,2)),                           // P7 (2×3 con pie)
            listOf(Position(0,0), Position(0,1), Position(1,1), Position(2,1),
                   Position(0,2), Position(1,2), Position(2,2)),                           // L7 (3×3 con muesca)
            // ─ Rectangulares grandes (solo con espacio en tablero)
            listOf(Position(0,0), Position(1,0), Position(0,1), Position(1,1),
                   Position(0,2), Position(1,2)),                                          // 2x3
            listOf(Position(0,0), Position(1,0), Position(2,0),
                   Position(0,1), Position(1,1), Position(2,1)),                           // 3x2
            listOf(Position(0,0), Position(1,0), Position(2,0),
                   Position(0,1), Position(1,1), Position(2,1),
                   Position(0,2), Position(1,2), Position(2,2)),                           // 3x3
            listOf(Position(0,0), Position(1,0), Position(2,0), Position(3,0),
                   Position(0,1), Position(1,1), Position(2,1), Position(3,1)),             // 4x2
            listOf(Position(0,0), Position(1,0), Position(0,1), Position(1,1),
                   Position(0,2), Position(1,2), Position(0,3), Position(1,3))              // 2x4
        )

        // Formas fáciles (≤4 bloques) para nivel 1-2 y fallback de tablero lleno
        private val EASY_SHAPES = SHAPES.filter { it.size <= 4 }

        /** Comprueba si al menos UNA posición del tablero puede alojar esta forma. */
        private fun fitsOnGrid(shape: List<Position>, grid: Map<Position, CellContent>): Boolean {
            val w = (shape.maxOfOrNull { it.x } ?: 0) + 1
            val h = (shape.maxOfOrNull { it.y } ?: 0) + 1
            for (startY in 0..(GameState.GRID_SIZE - h)) {
                for (startX in 0..(GameState.GRID_SIZE - w)) {
                    if (shape.all { pos ->
                            val cell = grid[Position(startX + pos.x, startY + pos.y)]
                            cell == null || cell == CellContent.Empty
                        }) return true
                }
            }
            return false
        }

        /**
         * Genera una pieza aleatoria.
         *
         * Todas las formas del catálogo tienen igual probabilidad. La pieza sale rotada
         * aleatoriamente (de las rotaciones que caben en el tablero) para que el jugador
         * tenga que pensar cómo colocarla.
         *
         * **Esto NO es el reparto de la bandeja**, y conviene no confundirlos: la bandeja la reparte
         * [PieceTrayPlanner], que reserva un hueco distinto para cada ficha y mira el tablero
         * ENTERO. Esta función la usan las habilidades que generan fichas a medida (Dormilón,
         * Escudo Cristalino).
         *
         * **Ni «nunca sale una pieza que no pueda colocarse»** — decía eso y era mentira desde que
         * el motor físico existe (2026-09-06): [fitsOnGrid] solo mira `grid`, así que no ve los
         * CUERPOS de Teika, ni la lava, ni los muros del Rey, ni el Corte Ninja, ni las celdas de
         * color. Sus dos llamantes revalidan con `canAutoPlace` antes de colocar nada, que es donde
         * de verdad está la garantía; quien añada un tercero tiene que hacer lo mismo.
         *
         * @param objectiveColors Colores de estrella del objetivo activo. Cuando no están vacíos,
         *   TODAS las celdas de la pieza llevarán estrella (100% cobertura).
         * @param grid Tablero actual. Se usa para filtrar formas y rotaciones que no caben.
         */
        fun generateRandomPiece(
            level: Int = 1,
            objectiveColors: List<TargetColor> = emptyList(),
            grid: Map<Position, CellContent>? = null,
            crystalActive: Boolean = false,
            random: Random = Random.Default
        ): Piece {
            // Pool unificado: todas las formas con igual peso, restringido a fáciles en nivel 1-2
            val basePool = if (level <= 2) EASY_SHAPES else SHAPES

            // Solo conservar formas que quepan en el tablero en al menos una rotación
            val validShapes = if (grid != null) {
                basePool.filter { shape ->
                    var s = shape
                    (0 until 4).any {
                        val fits = fitsOnGrid(s, grid)
                        s = Piece(shape = s, colorType = 0).rotate().shape
                        fits
                    }
                }.ifEmpty { EASY_SHAPES }
            } else {
                basePool
            }

            val chosenShape = validShapes.random(random)

            // Recopilar las rotaciones de esa forma que caben en el tablero y elegir una al azar
            val validRotations = mutableListOf<List<Position>>()
            var rotatedShape = chosenShape
            repeat(4) {
                if (grid == null || fitsOnGrid(rotatedShape, grid)) {
                    validRotations.add(rotatedShape)
                }
                rotatedShape = Piece(shape = rotatedShape, colorType = 0).rotate().shape
            }

            val finalShape = validRotations.ifEmpty { listOf(chosenShape) }.random(random)

            return generatePieceForShape(
                shape = finalShape,
                level = level,
                objectiveColors = objectiveColors,
                crystalActive = crystalActive,
                random = random
            )
        }

        internal fun generatePieceForShape(
            shape: List<Position>,
            level: Int,
            objectiveColors: List<TargetColor>,
            crystalActive: Boolean,
            random: Random = Random.Default,
            /**
             * Colores distintos de madera. 5 por defecto; el mutador PUREZA del Infinito lo baja a 3
             * para que la columna monocolor deje de ser lotería y pase a ser un plan (I-3).
             */
            colorCount: Int = 5
        ): Piece {
            val base = Piece(
                shape = shape,
                colorType = random.nextInt(0, colorCount.coerceIn(2, 5))   // 7 = comodín, aparte
            )
            val withTargets = run {
                if (objectiveColors.isEmpty()) return@run base
                val targetChance = when {
                    level >= 30 -> 0.80
                    level >= 20 -> 0.65
                    level >= 10 -> 0.50
                    else        -> 0.40
                }
                if (random.nextDouble() > targetChance) return@run base
                val color = objectiveColors.random(random)
                val mask  = shape.indices.map { color }
                base.copy(targetMask = mask)
            }
            // Bioma Eléctrico: las piezas NUNCA traen bobinas (coilMask retirado). Las bobinas
            // aparecen al completar una fila (GameViewModel.energizeCircuitRows / tapCoil).

            // Bioma Cristal: en niveles de cristal, algunas fichas traen celdas con crystalMask
            // → al colocar, Wood.armor = 1 (un clear quita el cristal).
            val withCrystal = run {
                if (!crystalActive) return@run withTargets
                // (2026-09-26, decisión del usuario) Las fichas GRANDES no traen cristal: una ficha
                // de 5+ casillas que además aguanta una limpieza tapona el tablero (con P-301 el
                // bot bajó el Cristal de 69 % a 58 %). El reparto de tamaños no se toca.
                if (withTargets.shape.size > CRYSTAL_MAX_PIECE_CELLS) return@run withTargets
                if (random.nextDouble() > LevelDirector.crystalPieceChance(level)) return@run withTargets
                val n = withTargets.shape.size
                val count = if (n <= 2) 1 else random.nextInt(1, 3)
                val idxs = withTargets.shape.indices.shuffled(random).take(count).toSet()
                withTargets.copy(crystalMask = withTargets.shape.indices.map { it in idxs })
            }
            return withCrystal
        }
    }
}
