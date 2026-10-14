package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable
import kotlin.math.abs

/** Desplazamiento visual restante: regreso suave con un único rebote hacia el interior. */
internal fun plantRescueOffsetFraction(progress: Float): Float {
    val t = progress.coerceIn(0f, 1f)
    val u = 1f - t
    return u * u * u - 0.12f * kotlin.math.sin(kotlin.math.PI * t).toFloat()
}

@Serializable
enum class PlantEdge {
    TOP, RIGHT, BOTTOM, LEFT
}

@Serializable
data class PlantAbduction(
    val teikaId: String,
    val teikaType: TeikaType,
    val edge: PlantEdge,
    val currentRoot: Position,
    val linesBroken: Int = 0,
    val turnsElapsed: Int = 0,
    val maxTurns: Int = 6
)

/**
 * **DÓNDE ESTÁ EL CUERPO RAPTADO**: el centro del círculo, en unidades de celda, para el footprint
 * que ocupa ahora mismo la raíz.
 *
 * Vive aquí y no en el ViewModel porque desde 2026-09-04 lo leen **dos** caminos —el arrastre de
 * cada turno y la restauración del guardado (`continueGame`)—, y si discreparan el Teika volvería
 * de un CONTINUAR pegado a una raíz que está en otro sitio.
 */
fun PlantAbduction.bodyCenterX(root: Position = currentRoot): Float =
    root.x + teikaType.width * 0.5f

fun PlantAbduction.bodyCenterY(root: Position = currentRoot): Float =
    root.y + teikaType.height * 0.5f

fun PlantAbduction.exitRoot(): Position = when (edge) {
    PlantEdge.TOP -> Position(currentRoot.x, -teikaType.height)
    PlantEdge.RIGHT -> Position(GameState.GRID_SIZE, currentRoot.y)
    PlantEdge.BOTTOM -> Position(currentRoot.x, GameState.GRID_SIZE)
    PlantEdge.LEFT -> Position(-teikaType.width, currentRoot.y)
}

fun PlantAbduction.nextRoot(): Position {
    val target = exitRoot()
    val turnsLeft = (maxTurns - turnsElapsed).coerceAtLeast(1)

    fun step(current: Int, destination: Int): Int {
        val distance = abs(destination - current)
        if (distance == 0) return current
        val amount = (distance + turnsLeft - 1) / turnsLeft
        return current + if (destination > current) amount else -amount
    }

    return Position(
        x = if (edge == PlantEdge.LEFT || edge == PlantEdge.RIGHT) step(currentRoot.x, target.x) else currentRoot.x,
        y = if (edge == PlantEdge.TOP || edge == PlantEdge.BOTTOM) step(currentRoot.y, target.y) else currentRoot.y
    )
}

fun plantFootprint(root: Position, type: TeikaType): Set<Position> = buildSet {
    for (dy in 0 until type.height) {
        for (dx in 0 until type.width) {
            add(Position(root.x + dx, root.y + dy))
        }
    }
}

fun PlantAbduction.isFullyOutside(root: Position = currentRoot): Boolean =
    plantFootprint(root, teikaType).none {
        it.x in 0 until GameState.GRID_SIZE && it.y in 0 until GameState.GRID_SIZE
    }
