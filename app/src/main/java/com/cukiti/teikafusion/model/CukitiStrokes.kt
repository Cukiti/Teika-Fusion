package com.cukiti.teikafusion.model

/**
 * LA PALABRA «CUKITI», UNA LÍNEA POR LETRA (P-374, 2026-09-30, diseño del usuario). Cada letra es
 * UNA sola línea continua (una polilínea): en la firma del estudio llega recta, arrastrándose desde
 * fuera de la pantalla, y se pliega articulación a articulación hasta tomar la forma de su letra
 * (ver `ui/CukitiLogoReveal.kt`). Aquí vive solo la geometría, pura.
 *
 * Unidades: la altura de una letra es 1; x crece a la derecha, y hacia abajo (como el Canvas).
 * Las coordenadas son la LÍNEA CENTRAL; el grosor lo pone quien dibuja ([STROKE]).
 *
 * ⚠ **El primer punto es la CABEZA** y el primer tramo marca por dónde entra la línea: llega recta
 * en esa dirección, con el resto del cuerpo detrás. Por eso el orden de los puntos elige el lado:
 * C por la izquierda, U por abajo, K por arriba a la izquierda, I por abajo, T por la derecha, I por
 * arriba. K y T no se pueden trazar de un tirón: su línea VUELVE sobre sí misma (decisión del usuario).
 */
object CukitiStrokes {

    /** Una letra: la polilínea en orden, cabeza primero. */
    class Letter(val xs: FloatArray, val ys: FloatArray) {
        val size: Int get() = xs.size
    }

    /** Grosor del trazo, en fracción de la altura de letra. */
    const val STROKE = 0.13f
    const val CHAMFER = 0.18f
    const val LETTER_WIDTH = 0.62f
    const val GAP = 0.26f

    val letters: List<Letter>
    /** Ancho total de la palabra en unidades de altura. */
    val width: Float

    init {
        val w = LETTER_WIDTH
        val c = CHAMFER
        val out = ArrayList<Letter>()
        var x = 0f
        fun letra(ancho: Float, vararg p: Float) {
            out += Letter(
                FloatArray(p.size / 2) { x + p[it * 2] },
                FloatArray(p.size / 2) { p[it * 2 + 1] }
            )
            x += ancho
        }
        // C — entra por la izquierda: cabeza arriba a la derecha, primer tramo hacia la izquierda.
        letra(w, w, 0f, c, 0f, 0f, c, 0f, 1f - c, c, 1f, w, 1f); x += GAP
        // U — entra por abajo: cabeza arriba a la izquierda, primer tramo hacia abajo.
        letra(w, 0f, 0f, 0f, 1f - c, c, 1f, w - c, 1f, w, 1f - c, w, 0f); x += GAP
        // K — entra por arriba a la izquierda: pata de abajo, vuelve por el brazo y baja por el palo.
        val yBrazo = 0.58f * (1f - 0.3f)   // el brazo superior a la altura donde nace la pata
        letra(w, w, 1f, w * 0.3f, yBrazo, w, 0f, 0f, 0.58f, 0f, 0f, 0f, 1f); x += GAP
        // I — entra por abajo (cabeza arriba, cuerpo hacia abajo).
        letra(0f, 0f, 0f, 0f, 1f); x += GAP
        // T — entra por la derecha: barra de izquierda a derecha, vuelve al centro y baja.
        letra(w, 0f, 0f, w, 0f, w / 2f, 0f, w / 2f, 1f); x += GAP
        // I — entra por arriba (cabeza abajo, cuerpo hacia arriba).
        letra(0f, 0f, 1f, 0f, 0f)
        letters = out
        width = x
    }
}
