package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable

/**
 * Representa una coordenada en la cuadrícula del juego (8x8).
 */
@Serializable
data class Position(val x: Int, val y: Int)
