package com.cukiti.teikafusion.model

import kotlin.math.sin

/**
 * **EL MAPA DE MUNDOS: dónde cae cada nivel** (2026-09-23, pedido del usuario: un mapa vertical tipo
 * Candy Crush con los 14 biomas unidos y un camino). Regla pura, sin Compose, para poder probarla.
 *
 * Cada capítulo es un TRAMO vertical: su fondo (arte pixelado o provisional) mide lo que mide, y
 * sus niveles se reparten de ABAJO (el primero, a la entrada del bioma) ARRIBA (el último, donde
 * empieza el siguiente) siguiendo una curva en S. Las coordenadas son fracciones del tramo
 * (`fx` del ancho, `fy` del alto, 0 = arriba), así que valen para cualquier pantalla.
 *
 * Las siete LECCIONES no van en el mapa (decisión del usuario): viven en «Cómo se juega».
 */
object WorldMapLayout {

    /** Alto del tramo por nivel, en anchos de pantalla (~80 dp por nivel en un móvil de 411 dp). */
    const val HEIGHT_PER_LEVEL = 0.197f

    /** Margen extra de un tramo SIN arte: el cartel del capítulo y un respiro arriba. */
    private const val PLACEHOLDER_PAD = 0.25f

    /** Franja vertical que ocupan los nodos dentro del tramo (el resto: entrada y salida). */
    private const val FY_BOTTOM = 0.93f
    private const val FY_TOP = 0.07f

    /** Amplitud de la S (fracción del ancho) y su centro. */
    private const val SWAY = 0.27f

    data class Node(val level: Int, val fx: Float, val fy: Float)

    /**
     * Proporción alto/ancho del tramo del capítulo [chapter]. Con arte manda el arte ([artAspect]);
     * sin él, la que da el número de niveles.
     */
    fun stripAspect(chapter: Int, artAspect: Float?): Float =
        artAspect ?: (LevelMap.chapters[chapter].levelCount * HEIGHT_PER_LEVEL + PLACEHOLDER_PAD)

    /** Los nodos del capítulo [chapter], del primero (abajo) al último (arriba). */
    fun nodes(chapter: Int): List<Node> {
        val count = LevelMap.chapters[chapter].levelCount
        val start = LevelMap.chapterStart[chapter]
        return (0 until count).map { i ->
            val t = if (count == 1) 0.5f else i.toFloat() / (count - 1)
            Node(
                level = start + i,
                // La fase cambia por capítulo: ningún tramo repite la S del anterior.
                fx = 0.5f + SWAY * sin(i * 0.85f + chapter * 1.3f),
                fy = FY_BOTTOM - t * (FY_BOTTOM - FY_TOP),
            )
        }
    }

    /**
     * Nivel en el que se abre el mapa: el elegido si es un nivel jugable, si no la frontera. Las
     * lecciones y el «juego terminado» (totalLevels + 1) caen en el nivel más cercano del mapa.
     */
    fun focusLevel(selectedLevel: Int, maxUnlockedLevel: Int): Int {
        val first = LevelMap.introLevels + 1
        val frontier = maxUnlockedLevel.coerceIn(first, LevelMap.totalLevels)
        return if (selectedLevel in first..frontier) selectedLevel else frontier
    }

    // ── P-258 · EL DESLIZADOR DE BIOMAS (2026-09-24) ────────────────────────────────────────────
    // Una columna con un punto por capítulo, en el mismo orden que el mapa: el primero ABAJO y el
    // último ARRIBA. Arrastrar el dedo por ella salta de bioma en bioma.

    /** Capítulo bajo el dedo. [fraction] es la altura en la columna: 0 = arriba (el último), 1 = abajo. */
    fun sliderChapter(fraction: Float): Int {
        val n = LevelMap.chapters.size
        return ((1f - fraction.coerceIn(0f, 1f)) * n).toInt().coerceIn(0, n - 1)
    }

    /** Centro del punto del capítulo [chapter] en la columna (misma escala que [sliderChapter]). */
    fun sliderFraction(chapter: Int): Float = 1f - (chapter + 0.5f) / LevelMap.chapters.size

    /**
     * El capítulo que se está VIENDO: el que ocupa el centro de la pantalla. El mapa es una lista al
     * revés (el capítulo 1 abajo), así que se cuenta desde el pie: [firstIndex] es el tramo más bajo
     * visible y [firstOffsetPx] lo que ya ha pasado de él por debajo del borde. [stripPx] da el alto
     * de cada tramo. Lo que queda por encima del último capítulo (el adelanto de El Abismo) cuenta
     * como el último.
     */
    fun chapterAtCenter(firstIndex: Int, firstOffsetPx: Int, viewportPx: Float, stripPx: (Int) -> Float): Int {
        val last = LevelMap.chapters.size - 1
        if (firstIndex > last) return last
        var c = firstIndex.coerceAtLeast(0)
        var remaining = firstOffsetPx + viewportPx / 2f
        while (c < last && remaining >= stripPx(c)) {
            remaining -= stripPx(c)
            c++
        }
        return c
    }
}
