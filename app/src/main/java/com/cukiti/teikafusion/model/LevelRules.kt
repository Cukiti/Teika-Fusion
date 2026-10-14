package com.cukiti.teikafusion.model

/**
 * **P-357 · LAS REGLAS DE ESTE NIVEL, EN LA PAUSA** (2026-09-27, decisión del usuario).
 *
 * El juego enseña cada mecánica una vez, con una cápsula de cinco palabras, y luego nunca más: quien se
 * la perdió, o vuelve tras una semana, no tenía dónde mirar. Y la regla completa de cada bioma
 * ([mechanicHint], al día con P-353: ritmo, mitigación y parada) existía en el código sin que ninguna
 * pantalla la enseñara.
 *
 * Esto NO reabre el cartel que el usuario retiró el 2026-08-26b (mucho texto ANTES de jugar): vive
 * plegado dentro de la pausa y solo lo abre quien lo busca.
 *
 * Puro y sin Android: lo prueba `LevelRulesTest`.
 */
object LevelRules {

    /** Una regla: su nombre corto y lo que hace. */
    data class Entry(val title: String, val text: String)

    /** Las reglas que están vivas en [level], de la más propia del nivel a la más general. */
    fun forLevel(
        level: Int,
        /** ¿Tiene el jugador el banco? Si no, la entrada dice cómo conseguirlo (2026-09-28). */
        bankOwned: Boolean = true,
    ): List<Entry> = buildList {
        if (TutorialCampaign.isTutorialLevel(level)) return@buildList
        val biome = BiomeRegistry.currentBiome(level)
        if (biome != BiomeType.NONE) add(Entry(biome.displayName.uppercase(), mechanicHint(biome)))
        HouseRule.forLevel(level)?.let { rule -> add(Entry(rule.badge, rule.capsule.label + ".")) }
        if (BlockBankRules.appliesTo(level) && !bankOwned) add(
            Entry(
                "FICHONA",
                "Recicla tus fichas y crea una mega ficha a medida. Viene en el Paquete inicial de la tienda, o se " +
                    "desbloquea para siempre por ${StorePacks.BLOCK_BANK_PRICE} teikoins tocando el candado.",
            )
        )
        if (BlockBankRules.appliesTo(level) && bankOwned) add(
            Entry(
                "FICHONA",
                "Recicla la bandeja disponible y crea una Fichona de hasta 16 bloques en la mayor zona libre. " +
                    "Conserva estrellas, semillas, cristales, barriles y sobrantes; usa primero las fichas con poderes completos. " +
                    "Crear consume un turno y activa el bioma; si no puede fabricarse, conservas fichas y materiales, pero el turno cuenta. " +
                    "El tablero sigue activo después del evento. Una soltada inválida rompe Fichona sin reembolso ni otro turno y repone la bandeja. " +
                    "Cada ficha aporta sus bloques menos uno (mínimo uno). " +
                    "Tras fabricar, descansa ${BlockBankRules.COOLDOWN_TURNS} turnos. Disponible con bandejas de tres huecos.",
            )
        )
        add(Entry("GIRAR", "Toca una ficha de la bandeja para girarla 90°. Es gratis y no gasta turno."))
    }
}
