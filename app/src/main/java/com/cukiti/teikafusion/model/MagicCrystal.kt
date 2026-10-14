package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable

/**
 * **EL CRISTAL** — el material que sube de nivel las habilidades de los compañeros.
 *
 * ## Un solo cristal (P-267, 2026-09-24, decisión del usuario)
 *
 * Hasta hoy había cuatro tiers (I–IV), fusión 3:1, desfusión 2:1 y un crisol para gestionarlo. La
 * simulación de la campaña con las cifras reales dio que caían **~10.000 cristales** y que TODAS las
 * mejoras juntas costaban **682**: sobraban ~15×. Los tiers y la fusión no planteaban ninguna decisión
 * y el freno real acababa siendo el teikoin — que se vende. Ahora:
 *
 *  - **Un número.** Sin tiers, sin fusión, sin crisol.
 *  - **Cae fijo, sin azar**: [CrystalEconomy.perLevel] por nivel superado la PRIMERA vez (crece con el
 *    capítulo), +[CrystalEconomy.THREE_STAR_BONUS] con 3★ y +[CrystalEconomy.MURAL_BONUS] al completar
 *    el mural de un capítulo.
 *  - **Escaso a propósito (~0,7×)**: en toda la campaña cae ~0,7 veces lo que cuesta mejorar a los 14
 *    compañeros al máximo. Hay que ELEGIR a quién — con un cinturón de tres, esa es la decisión.
 *  - **No se compra con nada.** Pagar acelera (teikoins, cargas), no sube el techo.
 */
object CrystalEconomy {

    /** Cristal extra por superar un nivel con 3★ (solo la primera vez que se supera). */
    const val THREE_STAR_BONUS = 1

    /** Cristales por completar el mural de un capítulo (su último nivel). */
    const val MURAL_BONUS = 5

    /**
     * Lo que valía cada tier viejo al convertirlo, en cristales nuevos: la escalera de la fusión 3:1
     * (1, 3, 9, 27). Solo se usa una vez, en [CrystalInventory.normalized].
     */
    private val LEGACY_WEIGHT = mapOf(1 to 1, 2 to 3, 3 to 9, 4 to 27)

    /**
     * Cristales por superar [level] por primera vez. Por tramos de capítulo: 2 en los cuatro primeros,
     * 3 en los cuatro siguientes, 4 en los cuatro siguientes y 5 en los dos últimos. Las lecciones dan 1.
     */
    fun perLevel(level: Int): Int {
        val chapter = LevelMap.chapterIndexForLevel(level)
        if (chapter < 0) return 1
        return 2 + chapter / 4
    }

    /** El drop de superar [level] por primera vez con [stars] estrellas. */
    fun dropFor(level: Int, stars: Int): Int =
        perLevel(level) + if (stars >= 3) THREE_STAR_BONUS else 0

    /**
     * Lo que un jugador sin 3★ ni murales habría recibido con este sistema al llegar a [maxUnlockedLevel]:
     * el TOPE de la conversión de los cristales viejos. Sin él, quien acumuló cientos con el sistema
     * de antes (que sobraba 15×) empezaría con la escasez nueva ya rota.
     */
    fun cumulativeBefore(maxUnlockedLevel: Int): Int =
        (1 until maxUnlockedLevel.coerceIn(1, LevelMap.totalLevels + 1)).sumOf { perLevel(it) }

    /** Valor en cristales nuevos de un inventario viejo por tiers. */
    fun legacyValue(counts: Map<Int, Int>): Int =
        counts.entries.sumOf { (tier, n) -> (LEGACY_WEIGHT[tier] ?: 0) * n.coerceAtLeast(0) }
}

/**
 * La mochila de cristales. [crystals] es lo que cuenta; [counts] es el inventario VIEJO por tiers y
 * solo se conserva para convertirlo una vez ([normalized]) — sigue en el formato serializado para que
 * un guardado de antes del 2026-09-24 no pierda nada al leerse.
 */
@Serializable
data class CrystalInventory(
    /** LEGADO: tier (1..4) → cantidad, del sistema anterior. Vacío tras la conversión. */
    val counts: Map<Int, Int> = emptyMap(),
    val crystals: Int = 0,
    /** ¿Ya se convirtió [counts]? Evita convertir dos veces (y que la nube resucite los tiers). */
    val legacyConverted: Boolean = false,
) {
    fun count(): Int = crystals

    fun add(amount: Int): CrystalInventory =
        if (amount <= 0) this else copy(crystals = crystals + amount)

    fun canSpend(amount: Int): Boolean = crystals >= amount

    fun spend(amount: Int): CrystalInventory =
        copy(crystals = (crystals - amount.coerceAtLeast(0)).coerceAtLeast(0))

    /**
     * Convierte UNA vez el inventario viejo por tiers: su valor ([CrystalEconomy.legacyValue]) con el
     * tope de lo que el sistema nuevo habría dado hasta [maxUnlockedLevel]. Idempotente.
     */
    fun normalized(maxUnlockedLevel: Int): CrystalInventory {
        if (legacyConverted) return this
        val converted = minOf(
            CrystalEconomy.legacyValue(counts),
            CrystalEconomy.cumulativeBefore(maxUnlockedLevel),
        )
        return CrystalInventory(counts = emptyMap(), crystals = crystals + converted, legacyConverted = true)
    }
}
