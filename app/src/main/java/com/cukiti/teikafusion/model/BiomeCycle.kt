package com.cukiti.teikafusion.model

/**
 * **P-353 · EL CASTIGO SIEMPRE LLEGA** (2026-09-27, opción A del usuario).
 *
 * Sustituye, en los nueve biomas con reloj (Sueño, Jardín, Volcán, Dojo, Mansión, Cocina Voraz, Cielo
 * Estelar, Trono Real y Espacio Profundo), al reloj que se congelaba ([BiomeClock]). La campaña del bot
 * con banco (572 partidas) midió que esos castigos casi nunca caían —de 0,1 a 0,6 por partida—: bastaba
 * romper CUALQUIER línea en el turno justo, y romper líneas es lo que el jugador hace de todos modos.
 *
 * Ahora:
 *  - **Ritmo fijo, pase lo que pase.** Cada bioma sigue un ciclo de [CYCLE] colocaciones: calma, aviso 1,
 *    aviso 2 y golpe. En el último tercio del capítulo el ciclo es de [CYCLE_LATE] (un turno menos de
 *    calma). Romper líneas NO lo frena.
 *  - **Mitigar**: los avisos señalan DÓNDE caerá, y el jugador puede preparar el tablero para que duela
 *    menos (vaciar la línea de la espada, el cráter, el rincón…). Eso vive en cada bioma.
 *  - **Parada perfecta**: romper una línea que pase por la zona amenazada JUSTO en el turno del golpe lo
 *    anula. Qué cuenta como «pasar por la zona» lo decide cada bioma.
 *
 * El contador de cada bioma cuenta colocaciones desde el último golpe (o parada): llega a [strikeAt] en
 * el turno del golpe y vuelve a cero.
 */
object BiomeCycle {

    /** Los nueve biomas que siguen el ciclo (y admiten la parada perfecta). */
    val BIOMES: Set<BiomeType> = setOf(
        BiomeType.SLEEP, BiomeType.GARDEN, BiomeType.VOLCANO, BiomeType.NINJA, BiomeType.GHOST,
        BiomeType.GLUTTON, BiomeType.STAR, BiomeType.KING, BiomeType.GALACTIC,
    )

    /** Colocaciones por ciclo: 2 de calma, aviso 1, aviso 2, golpe. */
    const val CYCLE = 5

    /** En el último tercio del capítulo: 1 de calma. */
    const val CYCLE_LATE = 4

    /** Fracción del capítulo a partir de la que el ciclo se acorta (el último tercio, como la lluvia doble). */
    const val LATE_FROM = 2.0 / 3.0

    /** ¿Está [level] en el último tercio de su capítulo? Sin azar: por su posición en el capítulo. */
    fun isLate(level: Int): Boolean {
        val chapter = LevelMap.chapterIndexForLevel(level)
        if (chapter < 0) return false
        val size = LevelMap.chapters[chapter].levelCount
        if (size <= 1) return false
        return LevelMap.localIndex(level).toDouble() / (size - 1) >= LATE_FROM
    }

    /**
     * **P-361 · LA CALMA DE CADA CAPÍTULO** (2026-09-28): la perilla de dificultad por capítulo que sustituye
     * al dial de madera, que medido con el bot casi no actuaba (al principio la madera ya está en su mínimo y
     * al final la figura es menor que el máximo). Colocaciones de CALMA antes de los dos avisos; el golpe cae
     * en `calma + 3`. Más calma → menos golpes → más fácil (un escalón movió el Espacio 26 puntos). Los dos
     * avisos no cambian nunca. Índice = capítulo de [LevelMap.chapters]; el Cristal no tiene ciclo.
     *
     * Valores de P-360 (medición final `campana-0928-final`, 71,3 %): 3 al principio del juego (Sueño,
     * Pasaje, Jardín), 2 en medio, 1 en Estelar y Trono, 2 en el Espacio.
     * 2026-09-28 (decisión del usuario): Volcán 2 → 1 — con la lava bajando de verdad (arreglo del reloj)
     * quedaba en el 80 % con objetivo 73 %. Trono 1 → 0 (decisión del usuario): 73 % con objetivo 57 %
     * tras la parada nueva de la pieza; ahora golpea cada 3 colocaciones en todo el capítulo.
     * Sueño 3 → 4 y Glaciar 2 → 3, con su último tercio +1 (decisión del usuario: «subir un poco»; Sueño
     * 76–77 % con objetivo 83, Glaciar 55–65 % con objetivo 69). El Glaciar lee este ciclo para su ola.
     */
    // P-367 (2026-09-29, primera campaña con el bot que PARA, P-366, `campana-0929-parada`): Sueño 67 %
    // (obj. 83) → 4 → 5 y su último tercio 2 → 3. Mansión 60 % (obj. 67), Glaciar 79 % (obj. 69) y Cocina
    // 73 % (obj. 64): medio escalón, solo en su último tercio (Mansión 1 → 2, Glaciar y Cocina 1 → 0).
    // Iteración 2 (`campana-0929-p367a`): Sueño 83 % y Mansión 68 %, en su sitio; Glaciar 76 % y Cocina
    // 70 % aún altos → su calma del principio baja un escalón (Glaciar 3 → 2, Cocina 2 → 1).
    val CHAPTER_CALM: IntArray = intArrayOf(2, 5, 3, 3, 2, 1, 2, 2, 2, 1, 2, 1, 0, 2)

    /** La calma en el último tercio de cada capítulo: un escalón menos (sin bajar de 0). */
    // Glaciar: último tercio 2 → 1 (decisión del usuario): con 3/2 subió al 76 % (objetivo 69).
    val CHAPTER_CALM_LATE: IntArray = intArrayOf(1, 3, 1, 1, 1, 0, 1, 0, 2, 0, 1, 0, 0, 1)

    /** Colocaciones de calma de [level] (2 fuera de los capítulos). */
    fun calmFor(level: Int): Int {
        val chapter = LevelMap.chapterIndexForLevel(level)
        if (chapter < 0) return CYCLE - 3
        val table = if (isLate(level)) CHAPTER_CALM_LATE else CHAPTER_CALM
        return table.getOrElse(chapter) { CYCLE - 3 }.coerceAtLeast(0)
    }

    /** Colocación del ciclo en que CAE el golpe: la calma, los dos avisos y el golpe. */
    fun strikeAt(level: Int): Int = calmFor(level) + 3

    /** Primer aviso: dos colocaciones antes del golpe. */
    fun warnAt(level: Int): Int = strikeAt(level) - 2

    /** El contador tras una colocación: siempre avanza. */
    fun advance(counter: Int): Int = counter + 1

    /** ¿Toca golpear con este contador (ya avanzado)? */
    fun isStrike(counter: Int, level: Int): Boolean = counter >= strikeAt(level)
}
