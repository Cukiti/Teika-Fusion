package com.cukiti.teikafusion.model

/**
 * **QUE CADA NIVEL SEA OTRO NIVEL** (2026-08-26f, pedido del usuario: «del 5 al 22 todos los niveles
 * del Cristal son iguales; no quiero solo subir la dificultad»).
 *
 * ## El diagnóstico que lo motiva
 *
 * [LevelDirector] reparte a cada nivel uno de **cuatro** tipos (PATRÓN, IMPULSO, PRECISIÓN,
 * PRESIÓN) y los cicla. El capítulo Cristal tiene 18 niveles, así que da **cuatro vueltas al mismo
 * ciclo**: el volcado del día muestra que los niveles 6, 10, 14 y 18 son *numéricamente el mismo
 * nivel* —mismo cristal (42 %), mismas fichas (46 %), misma coraza doble (6 %), mismo sesgo de
 * bandeja—. Y los tres diales que distinguen un tipo de otro (densidad, tamaño de ficha, umbral
 * ocioso) son **dificultad**, no identidad: suben o bajan el listón, no cambian el problema.
 *
 * Lo único que de verdad cambiaba entre esos cuatro niveles era la figura de salida
 * ([ChapterShapePool], que sí funciona) y +19 estrellas de objetivo.
 *
 * ## Lo que hace este archivo
 *
 * Le da a cada nivel un **perfil de variedad**, derivado solo del número de nivel —igual que
 * [LevelDirector], así que **no se serializa y ninguna partida guardada necesita migración**—: la
 * [StarPalette], cómo se REPARTEN los colores del objetivo sobre la figura. Cambia qué líneas dan qué
 * color, y por tanto en qué orden hay que romper.
 *
 * ## Lo que se RETIRÓ
 *
 *  - (2026-08-28, decisión del usuario) **`BoardShape`** —siluetas de celdas muertas (dos islas,
 *    embudo, diagonal…)— y **`seededTeika`** —un Teika ya puesto al empezar—, con su cobro en el
 *    presupuesto de turnos ([BoardClutterCost]).
 *  - (P-318, 2026-09-27, decisión del usuario) **`TrayFlavor`**, el sabor de bandeja (barras,
 *    macizas, recovecos, una grande). Desde el reparto predecible (P-314) el tamaño y la forma salen
 *    solo del tablero y nadie lo leía: se seguía asignando por nivel sin efecto.
 *
 * ## Lo que este archivo NO hace
 *
 * **No sube la dificultad**, que es la condición que puso el usuario. La paleta es NEUTRA por
 * construcción: cambia cómo se juega el nivel, no cuánto cuesta.
 */
object LevelVariety {

    /** Cómo se reparten los colores del objetivo sobre las celdas de la figura. */
    enum class StarPalette {
        /** Round-robin celda a celda, el de siempre: los colores quedan mezclados. */
        SHUFFLED,
        /** Bandas horizontales: cada franja de filas es de un color. */
        ROWS,
        /** Bandas verticales: cada franja de columnas es de un color. */
        COLUMNS,
        /** Por cuadrantes: cada esquina del tablero, un color. */
        QUADRANTS,
        /** Anillos: el borde de un color, el interior de otro. */
        RINGS,
    }

    private val PALETTES = StarPalette.entries.toList()   // 5

    /** Perfil completo de [level]. Puro y determinista: mismo nivel, mismo perfil, siempre. */
    fun profileFor(level: Int): Profile {
        if (level <= LevelMap.introLevels) {
            // La intro enseña las reglas: los colores, mezclados como siempre.
            return Profile(StarPalette.SHUFFLED)
        }
        return Profile(palette = PALETTES[level % PALETTES.size])
    }

    /**
     * El perfil del nivel. Tuvo cuatro caras: `shape` y `seededTeika` se retiraron el 2026-08-28 y el
     * sabor de bandeja (`tray`) con P-318. Queda la paleta, que es NEUTRA: cambia *cómo* se juega el
     * nivel sin cambiar cuánto cuesta.
     */
    data class Profile(
        val palette: StarPalette,
    )


    /** La silueta de una forma concreta. Separado de [blockedCells] para poder probarlo suelto. */
    /**
     * Color que le toca a la celda ([x],[y]) de la figura, según la paleta del nivel.
     *
     * **La figura ENTERA sigue llevando estrella** — esa decisión del 2026-08-01 no se toca: el
     * jugador tiene que poder leer el objetivo completo al levantarse el telón. Lo que cambia es
     * **dónde está cada color**, y eso sí cambia el puzzle: con [StarPalette.ROWS] una fila entera te
     * da un solo color, con [StarPalette.QUADRANTS] hay que ir a buscar la esquina que falta. Mismo
     * número de estrellas, distinto orden de juego, cero dificultad añadida.
     *
     * [index] es la posición de la celda en la lista de la figura: lo usa [StarPalette.SHUFFLED] para
     * conservar exactamente el round-robin de siempre.
     */
    fun colorFor(
        palette: StarPalette,
        colors: List<TargetColor>,
        index: Int,
        x: Int,
        y: Int,
        boardSize: Int = GameState.GRID_SIZE,
    ): TargetColor {
        if (colors.isEmpty()) return TargetColor.RED
        if (colors.size == 1) return colors[0]
        val n = colors.size
        val band = (boardSize + n - 1) / n
        val slot = when (palette) {
            StarPalette.SHUFFLED -> index
            StarPalette.ROWS -> y / band
            StarPalette.COLUMNS -> x / band
            StarPalette.QUADRANTS -> {
                val half = boardSize / 2
                (if (x >= half) 1 else 0) + (if (y >= half) 2 else 0)
            }
            StarPalette.RINGS -> {
                val d = minOf(x, y, boardSize - 1 - x, boardSize - 1 - y)
                d
            }
        }
        return colors[((slot % n) + n) % n]
    }

    /**
     * Reparte los colores del objetivo sobre las celdas de la figura, **garantizando que ninguno se
     * quede sin representación**.
     *
     * La garantía importa y ya la vigilaban dos pruebas antes de que esto existiera: si un color del
     * objetivo no aparece en el tablero, el jugador solo puede conseguirlo de las fichas que le
     * caigan, o sea por suerte. Las paletas geométricas lo rompen con facilidad —[StarPalette.ROWS]
     * sobre una figura que solo ocupa tres filas reparte tres colores de los cuatro—, así que
     * después de pintar se hace una **reparación**: cada color ausente se lleva una celda del color
     * más repartido, empezando por el final de la lista para no tocar la cabeza de la figura.
     *
     * Determinista: mismas celdas y mismos colores, mismo resultado.
     */
    fun applyPalette(
        palette: StarPalette,
        colors: List<TargetColor>,
        cells: List<Pair<Int, Int>>,
        boardSize: Int = GameState.GRID_SIZE,
    ): List<TargetColor> {
        if (colors.isEmpty() || cells.isEmpty()) return cells.map { TargetColor.RED }
        val out = cells.mapIndexed { i, (x, y) ->
            colorFor(palette, colors, i, x, y, boardSize)
        }.toMutableList()
        if (cells.size < colors.size) return out

        val counts = HashMap<TargetColor, Int>()
        out.forEach { counts[it] = (counts[it] ?: 0) + 1 }
        for (color in colors) {
            if ((counts[color] ?: 0) > 0) continue
            // Le quitamos una celda al color con más presencia (y que pueda permitírselo).
            val donor = colors
                .filter { (counts[it] ?: 0) > 1 }
                .maxByOrNull { counts[it] ?: 0 } ?: continue
            val idx = out.indexOfLast { it == donor }
            if (idx < 0) continue
            out[idx] = color
            counts[donor] = (counts[donor] ?: 1) - 1
            counts[color] = 1
        }
        return out
    }



}
