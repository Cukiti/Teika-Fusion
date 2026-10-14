package com.cukiti.teikafusion.model

/**
 * CADENCIA DEL ANUNCIO INTERSTICIAL (2026-08-09, decisión del usuario).
 *
 * El intersticial es el anuncio a pantalla completa que salta **solo**, sin que el jugador lo pida
 * (a diferencia del recompensado, que se ofrece a cambio de teikoins). Por eso lo peligroso no es
 * mostrarlo: es **dónde** y **cada cuánto**.
 *
 * **DÓNDE — nunca durante la partida.** La política de AdMob solo permite el formato en cortes
 * naturales («between levels in a game»); un anuncio que tapa el tablero con una ficha en la mano es
 * causa directa de suspensión de cuenta. Los dos cortes de este juego son *pasar de nivel* y *el
 * game over*, y son los únicos sitios desde los que se llama a esto.
 *
 * **CADA CUÁNTO — tres frenos que se aplican A LA VEZ**, y basta que uno diga no:
 *  · [MIN_LEVEL]: los primeros niveles quedan limpios. Es el tramo donde el jugador decide si se
 *    queda, y ahí un anuncio cuesta más de lo que ingresa.
 *  · [BREAKS_PER_AD]: uno de cada N cortes, no todos.
 *  · [MIN_GAP_MS]: hueco mínimo desde el ÚLTIMO anuncio a pantalla completa, **incluido el
 *    recompensado**. Sin esto, quien ve el vídeo de revivir y luego pierde se comía dos pantallazos
 *    seguidos en el peor momento posible del nivel.
 *
 * Reglas puras y sin Android: el estado (el contador de cortes) vive en el ViewModel y solo dura la
 * sesión —a propósito: un contador persistido haría que el primer corte tras abrir la app fuera casi
 * siempre anuncio—, pero la DECISIÓN se prueba en la JVM (`InterstitialCadenceTest`).
 */
object InterstitialCadence {

    /**
     * Por debajo de este nivel no hay intersticiales. OJO (auditoría 2026-10-01): se fijó cuando el
     * tutorial eran 4 niveles; hoy las lecciones llegan al 7 y no salen anuncios en ellas porque su
     * ventana de fin no pasa por el intersticial (`TutorialLevelCompleteOverlay`), no por esta cifra.
     */
    const val MIN_LEVEL = 6

    /** Cortes (nivel superado / game over) que hacen falta para que toque uno. */
    const val BREAKS_PER_AD = 3

    /** Hueco mínimo desde el último anuncio a pantalla completa, recompensado incluido. */
    const val MIN_GAP_MS = 90_000L

    /**
     * ¿Toca intersticial en este corte?
     *
     * @param breaks cortes acumulados desde el último intersticial (este incluido).
     * @param level nivel que el jugador acaba de terminar.
     * @param nowMs ahora.
     * @param lastFullScreenAdAtMs instante del último anuncio a pantalla completa de CUALQUIER tipo
     *   (0 = ninguno en esta sesión).
     */
    fun isDue(breaks: Int, level: Int, nowMs: Long, lastFullScreenAdAtMs: Long): Boolean =
        level >= MIN_LEVEL &&
            breaks >= BREAKS_PER_AD &&
            (lastFullScreenAdAtMs <= 0L || nowMs - lastFullScreenAdAtMs >= MIN_GAP_MS)
}
