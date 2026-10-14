package com.cukiti.teikafusion.ui

import androidx.compose.ui.geometry.Offset
import com.cukiti.teikafusion.model.Position
import kotlin.math.roundToInt

fun Offset.toGridPosition(cellSizePx: Float, boardOffsetPx: Offset): Position {
    val gridX = ((this.x - boardOffsetPx.x) / cellSizePx).roundToInt()
    val gridY = ((this.y - boardOffsetPx.y) / cellSizePx).roundToInt()
    return Position(gridX, gridY)
}
