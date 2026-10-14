package com.cukiti.teikafusion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp

// ════════════════════════════════════════════════════════════════════════════════
// EL CRISTAL — su gema. Desde el 2026-09-24 (P-267) hay un solo cristal: se fueron con los tiers la
// fila de fusión 3 → 1, su VFX, la ventana de desfusión, el selector de cantidad y el crisol que los
// alojaba. Queda el dibujo, que es el icono del material en todas partes.
// ════════════════════════════════════════════════════════════════════════════════

/** El color del cristal: violeta, distinto del oro de los teikoins para que nunca se confundan. */
val CRYSTAL_COLOR = Color(0xFFB983FF)

/** Gema de cristal facetada (Canvas). */
@Composable
fun CrystalGem(size: Dp, modifier: Modifier = Modifier) {
    val base = CRYSTAL_COLOR
    val light = lerp(base, Color.White, 0.55f)
    val dark = lerp(base, Color(0xFF0A0E22), 0.5f)
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val cx = w / 2f
        val cy = w / 2f
        val r = w / 2f * 0.92f
        val hx = r * 0.86f
        val pts = listOf(
            Offset(cx, cy - r),
            Offset(cx + hx, cy - r * 0.5f),
            Offset(cx + hx, cy + r * 0.5f),
            Offset(cx, cy + r),
            Offset(cx - hx, cy + r * 0.5f),
            Offset(cx - hx, cy - r * 0.5f)
        )
        val body = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
            close()
        }
        drawCircle(base.copy(alpha = 0.16f), radius = r * 1.18f, center = Offset(cx, cy))
        drawPath(body, brush = Brush.verticalGradient(listOf(light, base, dark)))
        pts.forEach { p ->
            drawLine(Color.White.copy(alpha = 0.12f), Offset(cx, cy), p, strokeWidth = w * 0.012f)
        }
        val table = Path().apply {
            moveTo(pts[5].x, pts[5].y)
            lineTo(pts[0].x, pts[0].y)
            lineTo(pts[1].x, pts[1].y)
            lineTo(cx, cy)
            close()
        }
        drawPath(table, color = Color.White.copy(alpha = 0.16f))
        drawPath(body, color = Color.White.copy(alpha = 0.55f), style = Stroke(width = w * 0.03f))
        drawCircle(Color.White.copy(alpha = 0.85f), radius = w * 0.05f, center = Offset(cx - hx * 0.42f, cy - r * 0.4f))
    }
}
