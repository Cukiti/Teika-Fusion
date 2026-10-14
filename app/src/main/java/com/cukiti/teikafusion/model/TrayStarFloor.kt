package com.cukiti.teikafusion.model

/**
 * **EL SUELO DE ESTRELLAS DE LA BANDEJA** (2026-09-06, decisión del usuario).
 *
 * Mientras el objetivo del nivel siga abierto, lo que el jugador tiene delante lleva **al menos una
 * ficha con estrellas**. Si el reparto ya trajo alguna —lo normal—, esta regla no toca nada.
 *
 * ## El problema que resuelve
 *
 * Las estrellas de la bandeja salen de una tirada **independiente por ficha** (40 % a 80 % según el
 * nivel, ver `Piece.generatePieceForShape`). Independiente quiere decir que nada impedía una racha
 * seca: al 40 %, una bandeja entera sin una sola estrella sale el **21,6 %** de las veces y **dos
 * bandejas seguidas el 4,7 %** — en una partida de treinta turnos, eso se ve.
 *
 * Y la figura del tablero no cubre el objetivo por sí sola: un nivel pide entre 30 y 110 estrellas
 * ([LevelLayouts.BASE_REQUIRED_STARS], [LevelLayouts.MAX_REQUIRED_STARS] y el bonus por color) y la
 * figura trae entre 16 y 56 celdas, todas marcadas desde el 2026-08-01. El resto **tiene** que
 * llegar por la bandeja. Una racha seca no es «mala suerte»: es un tramo de la partida en el que
 * jugar bien no acerca el objetivo, que es justo lo que un puzzle no puede permitirse.
 *
 * ## Lo que NO hace
 *
 * **No sube la media**, que es la condición con la que se aprobó: solo levanta el suelo. Por eso
 * marca la ficha **más pequeña** que puede llevarlas — el mínimo de estrellas que arregla la racha —
 * y por eso no se toca la probabilidad por ficha, que se queda exactamente donde estaba.
 */
object TrayStarFloor {

    /**
     * Índice de la ficha del reparto que debe llevar las estrellas, o **-1** si no hay que tocar
     * nada (el objetivo está cumplido, ya hay estrellas a la vista, o ninguna ficha puede llevarlas).
     *
     * @param objectiveOpen ¿le queda al jugador algún color por recoger?
     * @param starsAlreadyVisible ¿alguna ficha —repartida ahora o ya en la mano— trae estrellas?
     * @param sizes celdas de cada ficha del reparto. La más pequeña gana: ver «Lo que NO hace».
     * @param blocked fichas que **no pueden** llevar máscara. Son dos, y las dos por lo mismo:
     *   **la ficha que crece** (P-77), cuya máscara va celda a celda contra una forma que le cambia
     *   cada turno —`markGrowing` la limpia por eso— y el **Deseo Estelar**, que ya es una ficha de
     *   estrella con su propia regla al derramarse.
     * @param discouraged fichas que preferimos dejar limpias si hay alternativa: la **Brocha**
     *   (P-70) y la **Camaleón** (P-72) son un premio y se leen mejor sin nada encima. Valen como
     *   último recurso: antes una herramienta con estrellas que una bandeja muda.
     */
    fun targetIndex(
        objectiveOpen: Boolean,
        starsAlreadyVisible: Boolean,
        sizes: List<Int>,
        blocked: List<Boolean> = emptyList(),
        discouraged: List<Boolean> = emptyList(),
    ): Int {
        if (!objectiveOpen || starsAlreadyVisible || sizes.isEmpty()) return -1
        fun isBlocked(i: Int) = blocked.getOrElse(i) { false }
        fun isDiscouraged(i: Int) = discouraged.getOrElse(i) { false }
        val preferred = sizes.indices
            .filter { !isBlocked(it) && !isDiscouraged(it) }
            .minByOrNull { sizes[it] }
        if (preferred != null) return preferred
        return sizes.indices.filter { !isBlocked(it) }.minByOrNull { sizes[it] } ?: -1
    }
}
