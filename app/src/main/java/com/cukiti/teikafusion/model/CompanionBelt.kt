package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable

/**
 * LOS POWER-UPS DE LA PARTIDA (antes «EL CINTURÓN») — los compañeros cuyo poder el jugador se lleva
 * al nivel.
 *
 * Hasta [MAX_SLOTS] huecos. Lo que no está elegido **no aparece** en el botón de power-ups de la
 * pantalla de juego: es una restricción de verdad, no un atajo de favoritos. Tres de catorce, así que
 * elegir tiene coste de oportunidad.
 *
 * **Rediseño 2026-09-24 (decisión del usuario, estilo Candy Crush):** la elección se hace en el panel
 * previo al nivel (`LevelObjectiveOverlay`), ya no en la Sala de Preparación del Inventario, y se
 * recuerda de un nivel a otro. Elegir es gratis: la carga solo se gasta al lanzar. La cinta de
 * CAPITÁN y su carga ganada jugando se borraron ese día. El tipo conserva su nombre y su clave de
 * guardado para que el cinturón de siempre sea, sin migración, la primera selección.
 *
 * El orden de [slots] es el orden de los huecos y se conserva. El primero sigue siendo el compañero
 * que entra en el perfil de estrellas del nivel (`selectedCompanionId`). Persiste en DataStore y
 * **viaja a la nube** ([CloudSaveData]): es una elección del jugador, no un ajuste del aparato.
 */
@Serializable
data class CompanionBelt(
    val slots: List<String> = emptyList()
) {

    val size: Int get() = slots.size

    val isFull: Boolean get() = slots.size >= MAX_SLOTS

    val isEmpty: Boolean get() = slots.isEmpty()

    fun isEquipped(companionId: String): Boolean = companionId in slots

    /**
     * Equipa [companionId] en la primera ranura libre. Devuelve `null` si no cabe o si ya estaba —
     * igual que [CompanionCharges.minus], para que el call site no pueda «equipar» y quedarse tan
     * ancho sin haber cambiado nada.
     */
    fun equip(companionId: String): CompanionBelt? {
        if (isFull || isEquipped(companionId)) return null
        return copy(slots = slots + companionId)
    }

    /** Suelta [companionId]. Si no estaba equipado, devuelve el mismo cinturón. */
    fun unequip(companionId: String): CompanionBelt {
        if (!isEquipped(companionId)) return this
        return copy(slots = slots - companionId)
    }

    /** Suelta lo que hubiera en la ranura [index] (0-based). Fuera de rango no hace nada. */
    fun clearSlot(index: Int): CompanionBelt {
        if (index !in slots.indices) return this
        return copy(slots = slots.filterIndexed { i, _ -> i != index })
    }

    /**
     * Limpia el cinturón contra la realidad: fuera los ids desconocidos, los no reclamados y los
     * duplicados, y recorte a [MAX_SLOTS].
     *
     * Hace falta porque un cinturón puede llegar de la nube desde otro dispositivo con más progreso
     * (compañeros que aquí todavía no están reclamados) o de un save viejo. Un id fantasma en el
     * cinturón sería una ranura gastada en alguien que no existe.
     */
    fun sanitized(claimed: Set<String>): CompanionBelt {
        val clean = slots
            .distinct()
            .filter { it in claimed && NamedTeikaRegistry.byId(it) != null && companionAbilityEnabled(it) }
            .take(MAX_SLOTS)
        return if (clean == slots) this else copy(slots = clean)
    }

    /**
     * Rellena las ranuras libres con los primeros [candidates] que quepan (sin repetir).
     *
     * Es la red de seguridad para el jugador que nunca abre el Inventario: sin esto, un cinturón
     * vacío dejaría la hoja de habilidades de la partida vacía y parecería que el juego perdió los
     * compañeros. El orden de los candidatos lo decide quien llama — no es asunto del modelo.
     */
    fun filledFrom(candidates: List<String>): CompanionBelt {
        if (isFull) return this
        val acc = slots.toMutableList()
        for (id in candidates) {
            if (acc.size >= MAX_SLOTS) break
            if (id !in acc) acc += id
        }
        return if (acc.size == slots.size) this else copy(slots = acc)
    }

    companion object {
        /**
         * Tres. Suficiente para combinar (un abridor, un remate y un seguro) y pocas para que
         * dejar fuera a alguien duela — que es lo que hace que el jugador se pare a decidir.
         */
        const val MAX_SLOTS = 3
    }
}
