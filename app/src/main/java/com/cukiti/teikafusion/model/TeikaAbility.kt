package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable

/**
 * IDs de las 2 habilidades Teika base (Estela / Reynaldo). No hay variantes de pago:
 * V-02 podado 2026-07-29 (`buyTeikaAbility` / `equipTeikaAbility` retirados).
 */
enum class TeikaAbilityId {
    ESTRELLA_LLUVIA,
    REY_DECRETO
}

data class TeikaAbilityDef(
    val id: TeikaAbilityId,
    val companionId: String,
    val name: String,
    val shortName: String,
    val description: String,
    val icon: String,
    val activation: AbilityActivation
)

val TEIKA_ABILITY_DEFINITIONS: List<TeikaAbilityDef> = listOf(
    TeikaAbilityDef(
        id = TeikaAbilityId.ESTRELLA_LLUVIA,
        companionId = "estrella",
        name = "Deseo Estelar",
        shortName = "Deseo",
        description = "Pone en la bandeja un bloque tocado por las estrellas; al soltarlo se derrama como líquido y llena el espacio vacío conexo hasta toparse con muros o bloques.",
        icon = "🌠",
        activation = AbilityActivation.INSTANT
    ),
    TeikaAbilityDef(
        id = TeikaAbilityId.REY_DECRETO,
        companionId = "rey",
        name = "Parto Real",
        shortName = "Parto",
        description = "Tus Teikas paren un T1, empujándolo hacia fuera como material de fusión. Solo paren los de tier 2 o más.",
        icon = "👑",
        activation = AbilityActivation.INSTANT
    )
)

val TEIKA_ABILITY_DEF_MAP: Map<TeikaAbilityId, TeikaAbilityDef> =
    TEIKA_ABILITY_DEFINITIONS.associateBy { it.id }

/**
 * Snapshot de loadout que viaja en cloud/prefs por compatibilidad con saves antiguos
 * (campos `unlocked` / `equipped` de la era de variantes). **En partida solo hay 1 habilidad
 * por compañero** (la de [TEIKA_ABILITY_DEFINITIONS]); [abilityFor] ignora el mapa de equipo.
 */
@Serializable
data class TeikaAbilityLoadout(
    val unlocked: Set<String> = emptySet(),
    val equipped: Map<String, String> = emptyMap()
) {
    fun abilitiesFor(companionId: String): List<TeikaAbilityDef> =
        TEIKA_ABILITY_DEFINITIONS.filter { it.companionId == companionId }

    /**
     * Única habilidad del compañero. CONTRATO: solo llamar con compañeros en
     * [TEIKA_ABILITY_DEFINITIONS] (hoy "estrella" y "rey").
     */
    fun abilityFor(companionId: String): TeikaAbilityDef =
        abilitiesFor(companionId).first()

    /** @deprecated Usar [abilityFor]. Alias de compatibilidad con call sites viejos. */
    fun equippedFor(companionId: String): TeikaAbilityDef = abilityFor(companionId)
}
