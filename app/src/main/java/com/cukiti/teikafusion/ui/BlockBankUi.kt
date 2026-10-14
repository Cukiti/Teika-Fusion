package com.cukiti.teikafusion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Acento del banco: turquesa, distinto del oro del premio y del violeta de las fichas. */
val BankAccent = Color(0xFF5BE3C8)
private val BankNight = Color(0xFF141A33)

/** Lado del botón: el mismo que el de power-ups, para que la fila de abajo sea simétrica. */
val BANK_BUTTON_SIZE = 46.dp

/** El dibujo del cartel «SIN SALIDA» cuando la salida es el banco: una rejilla que late. */
@Composable
internal fun BankDeadEndGlyph(accent: Color, pulse: Float) {
    Canvas(Modifier.size(30.dp)) {
        val n = 3
        val gap = size.width * 0.08f
        val c = (size.width - gap * (n - 1)) / n
        for (i in 0 until n * n) {
            val x = i % n; val y = i / n
            val on = (x + y) % 2 == 0
            drawRoundRect(
                accent.copy(alpha = if (on) 0.55f + 0.4f * pulse else 0.18f),
                Offset(x * (c + gap), y * (c + gap)), Size(c, c), CornerRadius(c * 0.22f)
            )
        }
    }
}

/**
 * **EL BANCO BLOQUEADO** (2026-09-28, decisión del usuario: el banco es de pago). Mismo sitio, tamaño y
 * marco que [AutoBlockBankButton], apagado y con un candado: se ve que existe y qué es, y tocarlo abre la
 * oferta (paquete inicial o [com.cukiti.teikafusion.model.StorePacks.BLOCK_BANK_PRICE] teikoins).
 */
@Composable
fun BlockBankLockedButton(
    onClick: () -> Unit,
    onPositioned: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(BANK_BUTTON_SIZE)
            .onGloballyPositioned { onPositioned(it.boundsInWindow()) }
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF223055), BankNight)))
            .border(1.5.dp, BankAccent.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Fichona bloqueada. Toca para ver cómo conseguirla" },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize().padding(12.dp)) {
            val w = size.width
            val h = size.height
            // El arco del candado y su cuerpo, en el color del banco apagado.
            drawArc(
                color = BankAccent.copy(alpha = 0.8f),
                startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(w * 0.25f, h * 0.05f), size = Size(w * 0.5f, h * 0.55f),
                style = Stroke(width = w * 0.11f, cap = StrokeCap.Round),
            )
            drawRoundRect(
                color = BankAccent.copy(alpha = 0.8f),
                topLeft = Offset(w * 0.15f, h * 0.38f), size = Size(w * 0.7f, h * 0.55f),
                cornerRadius = CornerRadius(w * 0.1f),
            )
            drawCircle(BankNight, radius = w * 0.07f, center = Offset(w * 0.5f, h * 0.63f))
        }
    }
}
