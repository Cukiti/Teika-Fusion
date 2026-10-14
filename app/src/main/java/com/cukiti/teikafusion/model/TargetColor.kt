package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable

@Serializable
enum class TargetColor(
    val hex: Long,
    val label: String
) {
    RED(   0xFFE53935, "Rojo"),
    BLUE(  0xFF2196F3, "Azul"),
    PURPLE(0xFF9C27B0, "Morado"),
    PINK(  0xFFFF4081, "Rosa")
}
