package com.cukiti.teikafusion.model

/**
 * Snapshot de los objetivos que una habilidad instantánea puede usar en este momento.
 *
 * La carga de una habilidad no implica que deba consumirse inmediatamente: algunas habilidades
 * necesitan que exista madera o un Teika concreto. Estas reglas son puras para que el ViewModel
 * pueda decidir cuándo programar un lanzamiento sin producir eventos visuales falsos.
 */
data class InstantAbilityTargets(
    val hasDrowsyWork: Boolean = false,
    val hasMovableWood: Boolean = false,
    val hasTeika: Boolean = false,
    // (RETIRADO 2026-08-27) `hasMovableTeika`: solo lo leía el «Tembleque» de Morti, que necesitaba
    // un Teika suelto que agitar. POSESIÓN mueve madera, así que el campo se quedó sin lector.
    val hasWoodColumn: Boolean = false,
    val hasStarWood: Boolean = false,
    /** Morti / POSESIÓN: hay al menos una línea que sus bloques pueden cerrar. */
    val hasPossessionPlan: Boolean = false,
    val hasBirthableTeika: Boolean = false,
    val hasGalacticWork: Boolean = false,
    val hasToxicWork: Boolean = false
)

/** Regla común de disponibilidad para habilidades instantáneas. */
object InstantAbilityTargetRules {
    fun canExecute(effectKey: String, targets: InstantAbilityTargets): Boolean = when (effectKey) {
        "dormilona" -> targets.hasDrowsyWork
        "miedoso" -> targets.hasMovableWood
        "electrico", "planta" -> targets.hasTeika
        // POSESIÓN (2026-08-27): antes pedía un Teika móvil que agitar (Tembleque). Ahora mueve
        // MADERA, y solo tiene sentido si de verdad hay una línea que cerrar: encender su tarjeta
        // sin plan sería cobrar una carga por un paseo de fantasmas.
        "fantasma" -> targets.hasPossessionPlan
        "hielo" -> targets.hasWoodColumn
        // EL BANQUETE (2026-08-27): Chueta come BLOQUES CON ESTRELLA, no filas de madera. Antes
        // pedía `hasGluttonRows` (había filas densas que devorar) y con el rework eso habría
        // encendido su tarjeta en un tablero lleno de madera sin una sola estrella: el jugador
        // gastaba la carga y no pasaba nada.
        "gloton" -> targets.hasStarWood
        "ninja" -> targets.hasStarWood
        "REY_DECRETO", "rey" -> targets.hasBirthableTeika
        "galactico" -> targets.hasGalacticWork
        "toxico" -> targets.hasToxicWork
        // Deseo Estelar genera una ficha en la bandeja y no necesita un objetivo del tablero.
        "ESTRELLA_LLUVIA", "estrella" -> true
        // Una habilidad nueva debe seguir siendo utilizable hasta que declare explícitamente su
        // requisito; la validación definitiva continúa en el executor del ViewModel.
        else -> true
    }
}
