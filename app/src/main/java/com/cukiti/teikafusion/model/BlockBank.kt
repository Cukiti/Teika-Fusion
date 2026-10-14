package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable

/**
 * Material sobrante del taller automático. Conserva las mismas claves de los guardados anteriores.
 * El lote usa los bloques almacenados más N-1 (mínimo uno) por ficha reciclada. No tiene el límite
 * del banco manual de nueve: los sobrantes se conservan íntegros. El enfriamiento sigue en turnos.
 */
@Serializable
data class BlockBank(
    /** Material sobrante; el taller automático no lo trunca a nueve. */
    val blocks: Int = 0,
    /** Color del material de los guardados anteriores; las fichas recicladas conservan el suyo. */
    val colorType: Int = 0,
    /** Clave conservada para leer partidas del antiguo banco manual. */
    val usableFrom: Int = 0,
    /** Colocación hasta la que el banco está bloqueado tras construir (enfriamiento). */
    val lockedUntil: Int = 0,
)

object BlockBankRules {
    /**
     * Turnos de enfriamiento tras construir una ficha. 10 → 20 el 2026-09-27 (P-351, opción C del
     * usuario): con 10, el bot rescataba con el banco el 37 % de las partidas y la curva se aplanó
     * (52 % → 86 % de victorias).
     */
    const val COOLDOWN_TURNS = 20

    /**
     * **EL PEAJE** (P-351, 2026-09-27): guardar una ficha de N bloques deja N − 1 en el banco (nunca
     * menos de uno). Lo que cuesta vaciar la bandeja: el bloque que se pierde se DESHACE en el vuelo.
     */
    fun blocksFor(pieceSize: Int): Int = (pieceSize - 1).coerceAtLeast(1)

    /**
     * **Desde el capítulo 2** (P-350, 2026-09-27, decisión del usuario): el Cristal entero se juega sin
     * banco, como Block Blast puro, y el banco llega con el Sueño y su cápsula ([MechanicCapsule.BLOCK_BANK]).
     * Hasta hoy existía en todos los niveles menos las lecciones, sin nada que lo explicara.
     */
    fun appliesTo(level: Int): Boolean =
        !TutorialCampaign.isTutorialLevel(level) && level >= FIRST_LEVEL

    /** Primer nivel con banco: el arranque del capítulo 2 (el jugador lo ve como NIVEL 16). */
    val FIRST_LEVEL: Int get() = LevelMap.chapterStart[1]

    /** Turnos que faltan para que el banco vuelva a abrirse; 0 si ya está libre. */
    fun turnsLocked(bank: BlockBank, placements: Int): Int =
        (bank.lockedUntil - placements).coerceAtLeast(0)

    fun isLocked(bank: BlockBank, placements: Int): Boolean = turnsLocked(bank, placements) > 0

}
