package com.cukiti.teikafusion.model

import kotlin.math.roundToInt

/**
 * Dificultad efectiva de un nivel. El perfil es puro: no modifica el tablero y se puede usar tanto
 * al crear una partida como al calcular la calificación final.
 *
 * (El modo WALLBLOCK quedaba fuera de este modelo; se eliminó del juego el 2026-08-09.) No tiene turnos, estrellas,
 * biomas ni compañeros.
 */
data class LevelChallengeProfile(
    val level: Int,
    val movesTotal: Int,
    val biomeTax: Int,
    val companionCredit: Int,
    val expectedLines: Int,
    val guaranteedScore: Int,
    val twoStarScore: Int,
    val threeStarScore: Int
)

/** Coste de presión ambiental por bioma. Es una palanca de balance, no una nueva mecánica. */
object BiomeDifficultyProfile {
    private const val MEASURED_BASELINE_TAX = 2f

    private fun baseTax(biome: BiomeType): Float = when (biome) {
        BiomeType.NONE -> 0f
        BiomeType.CRYSTAL -> 2f
        BiomeType.SLEEP -> 1f
        BiomeType.FEAR -> 2f
        BiomeType.GARDEN -> 2f
        BiomeType.STORM -> 3f
        BiomeType.VOLCANO -> 3f
        BiomeType.NINJA -> 2f
        BiomeType.GLACIER -> 2f
        BiomeType.GHOST -> 2f
        BiomeType.GLUTTON -> 1f
        BiomeType.TOXIC -> 3f
        BiomeType.STAR -> 3f
        BiomeType.KING -> 3f
        BiomeType.GALACTIC -> 4f
    }

    /** Turnos adicionales sobre la curva de playtest ya existente. */
    /**
     * (2026-08-28) Se retiró el sumando del bioma SECUNDARIO (`baseTax(secondary) * 0.35f`): ya no
     * corre su mecánica, así que cobrar turnos por ella sería cobrar por una presión que no existe.
     */
    fun extraMoves(level: Int, primary: BiomeType): Int {
        if (primary == BiomeType.NONE) return 0
        val active = baseTax(primary)
        val arcAdjustment = when (ChapterArc.actFor(level)) {
            ChapterAct.PRESENTACION -> -1f
            ChapterAct.DESARROLLO -> 0f
            ChapterAct.CLIMAX -> 1f
        }
        return (active + arcAdjustment - MEASURED_BASELINE_TAX)
            .roundToInt()
            .coerceIn(0, 6)
    }
}

/** Ayuda teórica máxima del compañero para la calificación. Se acredita, no regala puntuación. */
object CompanionChallengeModifier {
    private val baseCredit = mapOf(
        "cristal" to 3, "dormilona" to 3, "miedoso" to 2, "planta" to 1,
        "electrico" to 3, "fuego" to 3, "ninja" to 3, "hielo" to 2,
        "fantasma" to 2, "gloton" to 3, "toxico" to 3, "estrella" to 2,
        "rey" to 2, "galactico" to 3
    )

    fun expectedTurnCredit(companionId: String?, companionLevel: Int): Int {
        if (companionId == null || !companionAbilityEnabled(companionId)) return 0
        val base = baseCredit[companionId] ?: 0
        return (base * (1f + (companionLevel.coerceIn(1, 3) - 1) * 0.25f))
            .roundToInt()
            .coerceIn(0, 4)
    }
}

/** Calificación final del nivel. Los turnos sobrantes son puntos, no una barrera binaria. */
object LevelRating {
    /**
     * **1,08 → 0,90 (2026-09-24, P-257, decisión del usuario, medido con el bot).** Con los
     * bloques-Teika la «renta garantizada» ya no está garantizada: el Teika nace de un bloque y no de
     * cada línea, y el bot gana niveles con entre 0,71× y 2,1× de esa cifra (39 victorias, mediana
     * 1,19×). Con 1,08 la 2★ quedaba pegada a la 3★ (1,30) y el reparto era 15·9·15; con 0,90 es
     * 9·15·15: una victoria justa sigue siendo 1★, una victoria normal ya es 2★.
     *
     * **3★: 1,30 → 1,45 (2026-09-25, P-266).** Con el giro de vuelta el bot gana el 71 % y el 60 % de
     * esas victorias eran 3★ (572 partidas): la 3★ era lo normal. Con 1,45 las mismas puntuaciones dan
     * un 48 %. No más: un jugador con power-ups y revivir puntúa por encima del bot.
     */
    private const val TWO_STAR_MARGIN = 0.90f
    private const val THREE_STAR_MARGIN = 1.45f
    private const val SPARE_MOVE_RATIO = 0.35f
    private const val SPARE_SCORE_CAP_RATIO = 0.12f

    fun profile(
        level: Int,
        companionId: String? = null,
        companionLevel: Int = 1
    ): LevelChallengeProfile {
        val layout = LevelLayouts.get(level)
        val objective = layout?.second
        val primary = LevelMap.biomeFor(level)
        val moves = LevelBudget.movesFor(
            level = level,
            objective = objective,
            hasBiome = primary != BiomeType.NONE,
            figureSize = layout?.first?.size ?: 0,
            initialCells = layout?.first ?: emptyList(),
            biomePrimary = primary,
            companionId = companionId,
            companionLevel = companionLevel
        )
        val expectedLines = LevelLayouts.expectedLines(level)
        val guaranteed = LevelLayouts.guaranteedScore(level)
        val credit = CompanionChallengeModifier.expectedTurnCredit(companionId, companionLevel)
        val effectiveGuaranteed = (guaranteed - credit * moveValue(guaranteed, moves)).coerceAtLeast(1)
        return LevelChallengeProfile(
            level = level,
            movesTotal = moves,
            biomeTax = BiomeDifficultyProfile.extraMoves(level, primary),
            companionCredit = credit,
            expectedLines = expectedLines,
            guaranteedScore = guaranteed,
            twoStarScore = (effectiveGuaranteed * TWO_STAR_MARGIN).roundToInt(),
            threeStarScore = (effectiveGuaranteed * THREE_STAR_MARGIN).roundToInt()
        )
    }

    fun moveValue(guaranteedScore: Int, movesTotal: Int): Int =
        (guaranteedScore.toFloat() / movesTotal.coerceAtLeast(1) * SPARE_MOVE_RATIO)
            .roundToInt()
            .coerceAtLeast(25)

    fun spareMoveBonus(
        movesLeft: Int,
        movesTotal: Int,
        companionCredit: Int = 0,
        level: Int = 1,
        threeStarTarget: Int? = null
    ): Int {
        if (movesTotal <= 0) return 0
        val profile = profile(level)
        val effectiveLeft = (movesLeft + companionCredit).coerceAtLeast(0)
        val raw = effectiveLeft * moveValue(profile.guaranteedScore, movesTotal)
        val cap = ((threeStarTarget ?: profile.threeStarScore) * SPARE_SCORE_CAP_RATIO).roundToInt()
        return raw.coerceAtMost(cap).coerceAtLeast(0)
    }

    fun performanceScore(
        level: Int,
        score: Int,
        movesLeft: Int,
        movesTotal: Int,
        companionId: String? = null,
        companionLevel: Int = 1
    ): Int {
        val profile = profile(level, companionId, companionLevel)
        return score + spareMoveBonus(
            movesLeft = movesLeft,
            movesTotal = movesTotal,
            level = level,
            threeStarTarget = profile.threeStarScore
        )
    }

    fun starsFor(
        level: Int,
        score: Int,
        movesLeft: Int,
        movesTotal: Int,
        companionId: String? = null,
        companionLevel: Int = 1
    ): Int {
        val profile = profile(level, companionId, companionLevel)
        val performance = performanceScore(level, score, movesLeft, movesTotal, companionId, companionLevel)
        return when {
            performance >= profile.threeStarScore -> 3
            performance >= profile.twoStarScore -> 2
            else -> 1
        }
    }
}
