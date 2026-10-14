package com.cukiti.teikafusion.model

data class LeaderboardEntry(
    val rank: Int,
    val uid: String,
    val displayName: String,
    val level: Int,
    val teikas: Int,
    val totalXp: Int = 0,
    val avatarType: TeikaType = TeikaType.TEIKA_1,
    /* (RETIRADO 2026-08-09, con el modo WALLBLOCK) `infiniteScore`: la marca del modo. El campo
     * sigue existiendo en los documentos de Firestore ya escritos — no se migra la colección — pero
     * el cliente ya no lo lee ni lo escribe. */
) {
    companion object {
        /** El puesto no se pudo calcular (fuera del top y sin respuesta del recuento). */
        const val UNKNOWN_RANK = -1

        /** Más allá del puesto 100: las reglas no dejan contar más (ver `rankForLevel`). Se pinta «100+». */
        const val RANK_BEYOND = -2

        /** Cómo se escribe un puesto: «7», «100+» o «—» si no se sabe. Una sola copia para tabla y carné. */
        fun rankLabel(rank: Int): String = when {
            rank == RANK_BEYOND -> "100+"
            rank > 0 -> "$rank"
            else -> "—"
        }
    }
}
