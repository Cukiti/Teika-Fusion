package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable

@Serializable
enum class TeikaType(val level: Int, val width: Int, val height: Int) {
    // Los cuerpos T1–T4 CONSERVAN su tamaño en el tablero. Como COMODINES, las celdas que tocan
    // cuentan como "llenas" para completar una fila/columna, pero NO se limpian con ella (solo se
    // rompe la madera). MEGA conserva el footprint lógico 5×5 para su expansión y saves heredados,
    // pero T4+T4 no deja un cuerpo persistente. Ver teikaWildcardCells / resolveLineClearPass.
    TEIKA_1(1, 1, 1),
    TEIKA_2(2, 2, 2),
    TEIKA_3(3, 3, 3),
    TEIKA_4(4, 4, 4),
    MEGA_TEIKA(5, 5, 5);

    fun nextEvolution(): TeikaType? = entries.find { it.level == this.level + 1 }
    fun previousEvolution(): TeikaType? = entries.find { it.level == this.level - 1 }
}
