package com.cukiti.teikafusion.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.cukiti.teikafusion.model.Position

/**
 * **P-293 · LAS MARCAS DEL SUELO NO TAPAN EL BLOQUE** (2026-09-25, decisión del usuario).
 *
 * La portería, las celdas calientes y la vidriera se dibujan ENCIMA de los bloques porque son marcas
 * del suelo: dicen algo de la casilla, no de lo que hay en ella. Con un bloque encima, su icono en el
 * centro tapaba justo la cara del bloque (su estrella, su cronómetro, su color). Ahora, con madera
 * encima, la marca se retira al BORDE de la casilla: un contorno fino en su color, con una sombra
 * oscura debajo para que se lea sobre cualquier skin. En una casilla libre siguen como siempre: ahí
 * no tapan nada.
 *
 * [colors] con un solo color dibuja el contorno entero; con cuatro, un lado de cada color (arriba,
 * derecha, abajo, izquierda) — la vidriera.
 */
internal fun DrawScope.drawCellEdgeMark(
    cell: Position,
    cellPx: Float,
    colors: List<Color>,
    alpha: Float,
) {
    val inset = cellPx * 0.035f
    val sw = cellPx * 0.07f
    val l = cell.x * cellPx + inset
    val t = cell.y * cellPx + inset
    val side = cellPx - inset * 2f
    val corner = CornerRadius(cellPx * 0.16f)
    drawRoundRect(
        Color.Black.copy(alpha = 0.40f * alpha), Offset(l, t), Size(side, side), corner,
        style = Stroke(sw * 1.7f),
    )
    if (colors.size < 4) {
        drawRoundRect(colors.first().copy(alpha = alpha), Offset(l, t), Size(side, side), corner, style = Stroke(sw))
        return
    }
    val r = l + side
    val b = t + side
    val k = cellPx * 0.12f // los lados no llegan a la esquina: así se ve que son cuatro vidrios
    drawLine(colors[0].copy(alpha = alpha), Offset(l + k, t), Offset(r - k, t), sw, StrokeCap.Round)
    drawLine(colors[1].copy(alpha = alpha), Offset(r, t + k), Offset(r, b - k), sw, StrokeCap.Round)
    drawLine(colors[2].copy(alpha = alpha), Offset(r - k, b), Offset(l + k, b), sw, StrokeCap.Round)
    drawLine(colors[3].copy(alpha = alpha), Offset(l, b - k), Offset(l, t + k), sw, StrokeCap.Round)
}
