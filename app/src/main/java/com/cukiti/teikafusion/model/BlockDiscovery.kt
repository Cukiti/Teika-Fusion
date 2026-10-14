package com.cukiti.teikafusion.model

/**
 * **P-250 · LOS BLOQUES SE DESCUBREN PASANDO BIOMAS** (decisión del usuario, 2026-09-23).
 *
 * Cada bloque de bioma se **descubre** al terminar el capítulo que lo guarda, y solo entonces **se
 * puede comprar** con teikoins, como hasta ahora. Son siete, uno cada dos biomas (P-251).
 *
 * «Descubierto» **no se guarda**: se deduce del nivel máximo desbloqueado, que ya viaja en la nube.
 * Así no hay una clave más que pueda desincronizarse — si pasaste el Glaciar, descubriste el Hielo,
 * en este móvil y en cualquier otro. Lo que sí se guarda es la COMPRA (`unlockedCosmetics`).
 *
 * Los ids se nombran aquí y no se derivan de `BlockSkins` porque `BlockSkins` vive en `ui/` (paleta y
 * dibujo) y esto es progresión: tiene que poder probarse sin Compose. `BlockDiscoveryTest` cruza las
 * dos listas.
 */
object BlockDiscovery {

    /**
     * Un bloque de bioma: su id persistido, su nombre, el capítulo que lo guarda y ese capítulo con
     * su artículo, para la pista («Completa **el Glaciar**»).
     */
    data class Slot(val skinId: String, val name: String, val biome: BiomeType, val where: String)

    /** Los siete, en el orden de la vitrina (= el del viaje). */
    val slots: List<Slot> = listOf(
        Slot("cristal", "Cristal", BiomeType.SLEEP, "el Bosque del Sueño"),
        Slot("madera", "Madera", BiomeType.GARDEN, "el Jardín"),
        Slot("lava", "Lava", BiomeType.VOLCANO, "el Volcán"),
        Slot("hielo", "Hielo", BiomeType.GLACIER, "el Glaciar"),
        Slot("espectro", "Espectro", BiomeType.GLUTTON, "la Cocina Voraz"),
        Slot("constelacion", "Constelación", BiomeType.STAR, "el Cielo Estelar"),
        Slot("vacio", "Vacío", BiomeType.GALACTIC, "el Espacio Profundo"),
    )

    fun slotFor(skinId: String): Slot? = slots.firstOrNull { it.skinId == skinId }

    /** Último nivel del capítulo de [biome]; -1 si no es un capítulo. */
    private fun lastLevelOf(biome: BiomeType): Int {
        val i = LevelMap.chapters.indexOfFirst { it.biome == biome }
        return if (i < 0) -1 else LevelMap.chapterLastLevel(i)
    }

    /**
     * ¿Está descubierto [skinId] con [maxUnlockedLevel]? Un bloque que no es de bioma (el Clásico, la
     * Cerámica) no se descubre: está siempre a la vista. `maxUnlockedLevel` es el primer nivel SIN
     * jugar, así que el capítulo está terminado cuando su último nivel queda por debajo.
     */
    fun isDiscovered(skinId: String, maxUnlockedLevel: Int): Boolean {
        val slot = slotFor(skinId) ?: return true
        val last = lastLevelOf(slot.biome)
        return last in 1 until maxUnlockedLevel
    }

    /**
     * El bloque que se descubre al terminar el nivel [completedLevel], o `null`. Lo usa la celebración
     * de mural completo para anunciarlo en el momento en que ocurre.
     */
    fun discoveredBy(completedLevel: Int): Slot? =
        slots.firstOrNull { lastLevelOf(it.biome) == completedLevel }

    /** Texto de la pista de un bloque sin descubrir: «Completa el Glaciar». */
    fun hint(slot: Slot): String = "Completa ${slot.where}"
}
