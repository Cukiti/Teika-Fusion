package com.cukiti.teikafusion.model

/**
 * Modelo de la RECOMPENSA revelada tras una compra (o hito de racha). Lo emite el ViewModel y lo
 * dibuja el overlay épico [com.cukiti.teikafusion.ui.PurchaseRevealOverlay]: destellos, cofre y
 * cartas de premio con animación dramática. Toda compra de la tienda pasa por aquí para que el
 * jugador SIENTA lo que consiguió; los packs grandes añaden un [RewardItem] sorpresa (`bonus`).
 */
enum class RewardKind { TEIKOINS, LIVES, INFINITE_LIVES, CRYSTAL, BLOCK_SKIN }

/** Pack comprable de vidas infinitas: [hours] horas por [cost] teikoins. */
data class InfiniteLivesPack(val hours: Int, val cost: Int)

/** Un ítem concreto conseguido (define su icono y etiqueta en el reveal). */
data class RewardItem(
    val kind: RewardKind,
    val label: String,
    val amount: Int = 0,          // teikoins, vidas, u horas (INFINITE_LIVES) o nº de cristales
    val skinId: String? = null,   // solo BLOCK_SKIN: qué skin desbloqueó
    val bonus: Boolean = false    // premio SORPRESA (se resalta aparte "¡BONUS!")
)

/**
 * Paquete de premios a revelar con la animación épica. `id` distingue instancias (para relanzar el
 * overlay aunque el contenido se repita). `accentArgb` tiñe el espectáculo con el color del premio
 * principal.
 */
data class PurchaseReveal(
    val id: Long,
    val title: String,
    val accentArgb: Long,
    val items: List<RewardItem>
)
