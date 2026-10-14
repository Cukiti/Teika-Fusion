package com.cukiti.teikafusion.model

import kotlin.math.roundToInt

/**
 * **REPARTO DEL POOL DE FIGURAS POR CAPÍTULO** (2026-07-31b) — segundo frente del diagnóstico «los
 * 20 y pico niveles del Cristal se sienten iguales».
 *
 * ## Lo que hacía antes
 *
 * La figura del tablero salía de la posición GLOBAL: `shapeId = (t*99) + 1 ± 4`, con
 * `t = (nivel-1)/292`. Como un capítulo ocupa el 7 % de esa recta, en los 18 niveles del Cristal el
 * índice base recorría **de 2 a 8**: con el jitter de ±4, el bioma entero dibujaba sobre unas **12
 * figuras de las 100**, repetidas, y encima las 12 más simples —el pool está ordenado de fácil a
 * difícil—. Rotarlas o espejarlas no arregla nada: en un tablero 8×8 casi no cambia cómo se juegan.
 *
 * ## Lo que hace ahora
 *
 * Cada capítulo recibe una **ventana** de [WINDOW] figuras del pool, y la recorre entera con un
 * paso ([STRIDE]) **coprimo con la ventana**. Eso da dos garantías a la vez:
 *
 *  - **Dentro del capítulo**: como `mcd(STRIDE, WINDOW) = 1`, el recorrido no se repite hasta pasar
 *    por las [WINDOW] figuras. Ningún capítulo llega a 40 niveles, así que **todos los niveles de un
 *    bioma tienen una figura distinta**, y consecutivos caen lejos en el pool (no es «la de antes
 *    con un bloque más»).
 *  - **Entre capítulos**: la ventana AVANZA con el índice del capítulo, de las figuras simples a las
 *    complejas, así que el juego sigue endureciéndose de bioma en bioma. Las ventanas de capítulos
 *    vecinos se solapan a propósito —el salto entre biomas no debe ser un muro—, pero el
 *    desplazamiento inicial ([chapterOffset]) hace que dos capítulos solapados no empiecen por la
 *    misma figura.
 *
 * Todo es aritmética pura y determinista: el mismo nivel da siempre la misma figura (los niveles no
 * pueden bailar entre partidas) y se puede fijar por test.
 */
object ChapterShapePool {

    /** Figuras curadas disponibles (`s01..s100` en `LevelLayouts`). */
    const val POOL_SIZE = 100

    /** Cuántas figuras del pool ve un capítulo. Más que su nº de niveles → nunca repite. */
    const val WINDOW = 40

    /**
     * Paso del recorrido dentro de la ventana. **Coprimo con [WINDOW]** (mcd(13,40)=1): recorre la
     * ventana entera antes de repetir. Además es grande, así que dos niveles seguidos caen lejos en
     * el pool y se notan distintos de verdad.
     */
    const val STRIDE = 13

    /** Primera figura (0-based) de la ventana del capítulo [chapterIndex] de [chapterCount]. */
    fun windowStart(chapterIndex: Int, chapterCount: Int): Int {
        if (chapterIndex <= 0 || chapterCount <= 1) return 0
        val last = (chapterCount - 1).toFloat()
        val span = (POOL_SIZE - WINDOW).toFloat()
        return (chapterIndex.coerceAtMost(chapterCount - 1) / last * span).roundToInt()
            .coerceIn(0, POOL_SIZE - WINDOW)
    }

    /** Desplazamiento inicial dentro de la ventana: evita que capítulos solapados abran igual. */
    fun chapterOffset(chapterIndex: Int): Int =
        if (chapterIndex <= 0) 0 else (chapterIndex * 7) % WINDOW

    /**
     * Id de figura (**1..100**, como espera `LevelLayouts.getShape`) para [level].
     *
     * Los niveles de intro (sin capítulo) conservan el comportamiento de siempre: las primeras
     * figuras del pool, en orden, que es justo el arranque amable que se quiere ahí.
     */
    fun shapeIdFor(level: Int): Int {
        val chapter = LevelMap.chapterIndexForLevel(level)
        if (chapter < 0) return level.coerceIn(1, POOL_SIZE)
        val start = windowStart(chapter, LevelMap.chapters.size)
        // Un nivel reparado toma una figura de RESERVA: las posiciones del recorrido que ningún nivel
        // del capítulo usa (la ventana tiene 40 y el capítulo, como mucho, 22).
        val position = REPAIRED_LEVELS[level]?.let { LevelMap.chapters[chapter].levelCount + it }
            ?: LevelMap.localIndex(level)
        val step = (chapterOffset(chapter) + position * STRIDE) % WINDOW
        return start + step + 1
    }

    /**
     * **NIVELES REPARADOS** (2026-09-26, bug): nivel → figura de reserva nº k de su capítulo.
     *
     * Con su figura, recortada a la densidad de su nivel, el tablero de salida quedaba en un patrón
     * (enrejado en diagonal, damero de bloques 2×2) donde solo cabían fichas de 1-2 casillas y no se
     * cerraba ninguna línea: el bot los perdía SIEMPRE en las primeras jugadas (144 y 194 en la 5.ª,
     * 281 en la 11.ª), aunque las mismas figuras en otros niveles se ganan. Detectado con el bot
     * (`LevelBotTest`, semilla fija); `ChapterShapePoolTest` vigila que la reserva no repita figura.
     */
    val REPAIRED_LEVELS: Map<Int, Int> = mapOf(
        151 to 0, // nivel 144 · Glaciar
        201 to 0, // nivel 194 · Cocina Voraz
        288 to 1, // nivel 281 · Espacio Profundo
    )
}
