package com.cukiti.teikafusion.model

/** Un rango de jugador: nombre visible, color de identidad y su ventana de XP. */
data class RankInfo(
    val name: String,
    val colorArgb: Long,
    val minXp: Int,
    val maxXp: Int   // -1 = sin techo (último rango)
)

/**
 * Los 6 rangos de jugador (inventario canónico) y sus umbrales de XP.
 *
 * El XP solo SUBE (prefs.addXp es aditivo; el merge cloud toma el máximo) y hoy alimenta dos
 * superficies: la insignia de rango del perfil (HomeScreen) y el rango junto al nombre en el
 * ranking global (LeaderboardScreen, vía el campo `xp` del documento de Firestore).
 * Los umbrales son BALANCE: no retocarlos sin decisión del usuario.
 */
object PlayerRank {
    val ALL = listOf(
        RankInfo("Novato",        0xFF9E9E9EL, 0,       500),
        RankInfo("Explorador",    0xFF4DB8FFL, 500,     1_500),
        RankInfo("Fusionador",    0xFF4ECC6AL, 1_500,   3_500),
        RankInfo("Experto",       0xFFCC66FFL, 3_500,   7_000),
        RankInfo("Maestro",       0xFFFFD700L, 7_000,   15_000),
        RankInfo("Leyenda Teika", 0xFFFF6B00L, 15_000,  -1),
    )

    fun get(totalXp: Int): RankInfo =
        ALL.lastOrNull { totalXp >= it.minXp } ?: ALL.first()

    /** Avance 0..1 dentro del rango actual. El último rango devuelve siempre 1 (sin techo). */
    fun progressFraction(totalXp: Int): Float {
        val rank = get(totalXp)
        if (rank.maxXp == -1) return 1f
        val range = (rank.maxXp - rank.minXp).toFloat()
        return ((totalXp - rank.minXp) / range).coerceIn(0f, 1f)
    }

    /** XP que falta para el siguiente rango, o null si ya es el último. */
    fun xpToNext(totalXp: Int): Int? {
        val rank = get(totalXp)
        return if (rank.maxXp == -1) null else rank.maxXp - totalXp
    }

    fun nextRank(totalXp: Int): RankInfo? =
        ALL.firstOrNull { it.minXp > get(totalXp).minXp }

    /**
     * Si al pasar de [oldXp] a [newXp] se cruza al menos un umbral de rango, devuelve el rango
     * **resultante** (el más alto alcanzado). Null si no hay subida. Puro — unit-testeable.
     */
    fun rankAfterXpGain(oldXp: Int, newXp: Int): RankInfo? {
        if (newXp <= oldXp) return null
        val oldRank = get(oldXp)
        val newRank = get(newXp)
        return if (newRank.minXp > oldRank.minXp) newRank else null
    }
}

/** Reglas puras de las estadísticas acumuladas del perfil. */
object PlayerStats {
    /**
     * Mejor "cosecha": el mayor número de Teikas nacidos en UNA partida. [created] es el contador
     * real de nacimientos por tipo de la partida que acaba de cerrarse. Solo sube — una partida
     * floja nunca puede borrar la marca anterior.
     */
    fun bestHarvest(created: Map<TeikaType, Int>, currentMax: Int): Int =
        maxOf(currentMax, created.values.sumOf { it.coerceAtLeast(0) })
}

object XpAward {
    /** Al completar un nivel (colector de LEVEL_COMPLETE). */
    fun levelComplete(level: Int) = 50 + level * 10
    /** Al nacer un MEGA por fusión (resolveLineClearPass). */
    const val MEGA_TEIKA        = 150
    /** Al nacer un TEIKA_4 por fusión. */
    const val TEIKA_4           = 30
    /** Al alcanzar exactamente 5 fusiones en la misma cadena. */
    const val COMBO_5_PLUS      = 20
    /** Primera partida del día (checkAndUpdateStreak → isNewDay). */
    const val FIRST_GAME_OF_DAY = 10
    /**
     * Batir el récord personal de puntuación (una vez por ronda al superarlo) o el de profundidad
     * del Infinito (TideRecordBroken). V-03 2026-07-29.
     */
    const val PERSONAL_RECORD   = 100
}
