package com.cukiti.teikafusion.model

/**
 * **P-249 · SE RETIRA EL BLOQUE CARAMELO** (decisión del usuario, 2026-09-23). Era el único bloque
 * redondeado y le quitaba a las fichas el toque cuadrado del género: todos los bloques del juego son
 * ahora cuadrados y la personalidad va en la cara (ver `BlockSkins`).
 *
 * Quien lo tenía **recupera lo que pagó** ([REFUND] teikoins, una sola vez) y, si lo llevaba puesto,
 * vuelve al Clásico. La regla es pura para poder probarla sin ViewModel ni DataStore.
 *
 * ## Por qué «candy» NO se borra de los comprados
 *
 * La nube fusiona `unlockedCosmetics` por UNIÓN: borrarlo aquí no sirve, el otro móvil (o la copia de
 * la nube) lo devolvería en la siguiente sincronización. Se queda como registro de compra — nadie lo
 * enseña ni lo equipa, `BlockSkins.byId` ya no lo conoce— y lo que decide si se paga es la marca
 * [refunded], que viaja en la nube (`CloudSaveData.candyRefunded`, fusión por OR). Así reinstalar o
 * cambiar de móvil no vuelve a cobrar la devolución.
 */
object CandyRetirement {

    const val CANDY_ID = "candy"

    /** Lo que costaba en la tienda (2.000 desde el 2026-09-06): se devuelve entero. */
    const val REFUND = 2_000

    /**
     * @param refund teikoins a abonar AHORA (0 si no toca).
     * @param active el bloque equipado tras la migración (`null` = el Clásico).
     * @param refunded la marca a guardar: `true` en cuanto se ha pagado (o ya lo estaba).
     */
    data class Outcome(val refund: Int, val active: String?, val refunded: Boolean)

    fun apply(unlocked: Set<String>, active: String?, refunded: Boolean): Outcome {
        val pay = CANDY_ID in unlocked && !refunded
        return Outcome(
            refund = if (pay) REFUND else 0,
            active = if (active == CANDY_ID) null else active,
            refunded = refunded || pay,
        )
    }
}
