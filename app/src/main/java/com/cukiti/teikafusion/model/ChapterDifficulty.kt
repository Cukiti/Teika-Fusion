package com.cukiti.teikafusion.model

/**
 * **P-287 · LA CURVA POR CAPÍTULO** (2026-09-25, decisión del usuario).
 *
 * El bot midió la campaña entera (572 partidas): todos los capítulos entre el 60 % y el 82 % de
 * victorias, sin orden —el Ninja, el séptimo, era el más fácil—. El juego no se endurecía al avanzar.
 * El usuario eligió una curva LINEAL de victorias del bot, **~85 % en el primer capítulo → ~55 % en el
 * último** ([targetWinRate]). Ninguna perilla toca las reglas de los biomas ni su reloj (decisión
 * congelada): actúan sobre el REPARTO y el TABLERO INICIAL. La perilla principal hoy es la calma por
 * capítulo ([BiomeCycle.CHAPTER_CALM]); aquí queda la madera inicial.
 *
 * **P-362 · Del dial a una tabla** (2026-09-29, decisión del usuario). Hasta aquí cada capítulo tenía
 * un «punto de dial» (P-309, −1…1,6) que se interpolaba entre −4 y +6 casillas y se recortaba a ±12.
 * Tras seis iteraciones de calibración casi todos los capítulos estaban en un extremo, así que la
 * interpolación ya no aportaba nada y el nombre engañaba. Se sustituye por la tabla de casillas que
 * producía (mismos números: cero cambio de juego). Las iteraciones del dial están en
 * `docs/historial/2026-09.md` (`grep -n "CHAPTER_DIAL"`).
 */
object ChapterDifficulty {

    /** Límite de [CHAPTER_WOOD_BONUS]: más allá, el tope de [LevelLayout] (11..56) se come el ajuste. */
    const val WOOD_BONUS_LIMIT = 12

    /** Madera de la intro y del tutorial: el lado amable (lo que daba el dial en 0). */
    const val INTRO_WOOD_BONUS = -4

    /**
     * **MADERA INICIAL POR CAPÍTULO**, en el orden de [LevelMap.chapters]: casillas de más (o de menos)
     * en el MÁXIMO de madera que trae el nivel. Solo actúa cuando la figura del nivel es mayor que ese
     * máximo; con figuras pequeñas (p. ej. el Espacio) no recorta nada.
     *
     * Valores de la iteración 6 del dial (2026-09-28, P-360: campañas `campana-0928e` y `-0928f`,
     * 1.144 partidas), convertidos a casillas.
     */
    val CHAPTER_WOOD_BONUS: IntArray = intArrayOf(
        -12, // Caverna de Cristal   70 %
        -12, // Bosque del Sueño     76 %
        -12, // Pasaje Encantado     74 %
        -1,  // Jardín               85 %
        1,   // Tormenta             77 %
        12,  // Volcán               82 %
        12,  // Dojo en Sombras      77 %
        -12, // Glaciar              58 %
        12,  // Mansión Espectral    69 %
        12,  // Cocina Voraz         71 %
        -1,  // Pantano Tóxico       71 %
        12,  // Cielo Estelar
        12,  // Trono Real           69 % con calle 5
        11,  // Espacio Profundo     43 % (el ajuste ya no recorta su figura)
    )

    /** Victorias del bot que se buscan en el capítulo [chapter]: recta de 85 % a 55 %. */
    fun targetWinRate(chapter: Int): Double {
        val last = (LevelMap.chapters.size - 1).coerceAtLeast(1)
        return 85.0 - 30.0 * chapter.coerceIn(0, last) / last
    }

    /** Casillas de madera de más (o de menos) de [level]: las de su capítulo; la intro, [INTRO_WOOD_BONUS]. */
    fun densityBonus(level: Int): Int {
        val chapter = LevelMap.chapterIndexForLevel(level)
        if (chapter < 0) return INTRO_WOOD_BONUS
        return CHAPTER_WOOD_BONUS.getOrElse(chapter) { WOOD_BONUS_LIMIT }
            .coerceIn(-WOOD_BONUS_LIMIT, WOOD_BONUS_LIMIT)
    }
}
