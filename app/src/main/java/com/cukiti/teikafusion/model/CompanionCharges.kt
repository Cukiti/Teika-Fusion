package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable

/**
 * INVENTARIO DE CARGAS de habilidad de compañero — una cuenta por compañero.
 *
 * Sustituye a la barra de energía (`AbilityCharge` + `GameState.companionEnergy`, retiradas
 * 2026-08-27). Aquel modelo llenaba una barra rompiendo líneas y **disparaba la habilidad sola** al
 * llenarse: el jugador no decidía nada, así que la habilidad se leía como un evento ambiental más
 * del bioma y no como un poder suyo. Ese fue el motivo real de que las habilidades no divirtieran.
 *
 * Modelo nuevo, estilo Candy Crush: las cargas **se tienen en cantidades y se gastan al usarlas**.
 * Se obtienen por progresión (ver [CompanionChargeEconomy]) o comprándolas con teikoins, y el
 * jugador elige cuál lanza y cuándo desde el panel lateral de la pantalla de juego.
 *
 * Las cargas viven en DataStore y se respaldan en la nube ([CloudSaveData]). La fusión cloud es por
 * MÁXIMO por compañero, igual que los teikoins y los cristales: en un conflicto entre dos
 * dispositivos el jugador nunca pierde carga.
 */
@Serializable
data class CompanionCharges(
    val counts: Map<String, Int> = emptyMap()
) {

    /** Cargas disponibles de [companionId]. Siempre dentro de rango, aunque el mapa venga sucio. */
    fun countOf(companionId: String): Int =
        (counts[companionId] ?: 0).coerceIn(0, MAX_PER_COMPANION)

    /** ¿Puede lanzarse ahora mismo la habilidad de [companionId]? */
    fun has(companionId: String): Boolean = countOf(companionId) > 0

    /** Total de cargas de todos los compañeros — lo pinta el badge de la pestaña lateral. */
    val total: Int get() = counts.keys.sumOf { countOf(it) }

    /** Suma [amount] cargas a [companionId], topando en [MAX_PER_COMPANION]. */
    fun plus(companionId: String, amount: Int = 1): CompanionCharges {
        if (amount <= 0) return this
        val next = (countOf(companionId) + amount).coerceAtMost(MAX_PER_COMPANION)
        return copy(counts = counts + (companionId to next))
    }

    /**
     * Gasta [amount] cargas de [companionId]. Devuelve `null` si no hay suficientes — así el call
     * site no puede lanzar la habilidad y "olvidarse" de cobrarla: o cobra y lanza, o ninguna cosa.
     */
    fun minus(companionId: String, amount: Int = 1): CompanionCharges? {
        val current = countOf(companionId)
        if (amount <= 0 || current < amount) return null
        return copy(counts = counts + (companionId to current - amount))
    }

    companion object {
        /**
         * Tope por compañero. Un solo dígito a propósito: el badge del panel nunca necesita dos
         * cifras, y acumular diez lanzamientos de un poder legendario rompería cualquier nivel.
         */
        const val MAX_PER_COMPANION = 9
    }
}

/**
 * De dónde salen las cargas. Todo es DETERMINISTA — ni una fuente aleatoria — porque una economía
 * que el jugador no puede predecir no se puede planificar, y planificar es justo lo que queremos
 * que haga con estos poderes.
 */
object CompanionChargeEconomy {

    /**
     * Cargas de regalo al reclamar por primera vez a un compañero. Sin esto el jugador desbloquea
     * un personaje y no puede probar su habilidad hasta vaya a saber cuándo: la primera impresión
     * de un compañero nuevo tiene que ser usarlo.
     */
    const val WELCOME_CHARGES = 1

    /**
     * Cargas que otorga terminar un nivel, según las estrellas conseguidas. Solo el 3★ paga: es el
     * único resultado que exige jugar bien, y ata el poder al dominio del juego en vez de al tiempo
     * invertido. Las paga el compañero del capítulo en curso (ver `NamedTeikaRegistry`).
     */
    fun rewardForStars(stars: Int): Int = if (stars >= 3) 1 else 0

    /**
     * Precio de UNA carga en teikoins, por rareza del compañero.
     *
     * **Calibración:** ~100 teikoins salen de un anuncio y ~80 + nivel/2 de terminar un nivel (+50
     * más con 3★), así que un poder legendario cuesta unas cuantas partidas o unos cuantos vídeos.
     *
     * ⚠️ **La premisa de este precio cambió el 2026-09-06.** Este texto decía que las cargas eran
     * «la única cosa que se compra con teikoins ahora que no hay tienda de monedas» y que no había
     * pay-to-win «porque los teikoins no se compran con dinero real». **Las dos cosas dejaron de ser
     * ciertas ese día**: la tienda reabrió y `coins_250` / `coins_1200` / `coins_5000` venden
     * teikoins por dinero, así que existe un camino de dinero real → teikoins → cargas.
     *
     * Lo que de verdad sostiene el equilibrio hoy es otra cosa, y conviene tenerlo escrito porque es
     * lo que hay que proteger si alguien vuelve a tocar estos números:
     *
     *  · **[CompanionCharges.MAX_PER_COMPANION] = 9.** Se puede pagar por tener cargas antes, no por
     *    tener más de las que cabe acumular: el techo es el mismo para todo el mundo.
     *  · **Los compañeros se desbloquean JUGANDO**, nunca pagando — completar su nivel y es tuyo.
     *  · **El muro de la progresión son los CRISTALES**, que no se compran con nada (ver
     *    `COMPANION_ABILITY_RARITY_COIN_MULT`). Subir una habilidad de nivel exige cristales, y ese
     *    camino sigue cerrado al dinero.
     *
     * O sea: pagar **acelera**, no desbloquea ni sube el techo. Si algún día una carga se pudiera
     * comprar sin tope, o un cristal saliera de la tienda, esa frase deja de valer.
     */
    fun priceOf(companionId: String): Int = when (abilityRarityFor(companionId)) {
        AbilityRarity.COMUN      -> 250
        AbilityRarity.RARO       -> 400
        AbilityRarity.EPICO      -> 650
        AbilityRarity.LEGENDARIO -> 900
    }
}
