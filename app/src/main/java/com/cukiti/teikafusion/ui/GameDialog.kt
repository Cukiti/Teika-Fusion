package com.cukiti.teikafusion.ui

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * **LA VENTANA DE JUEGO** (2026-09-23, pedido del usuario: los avisos «tienen que verse más premium,
 * como un juego triple A»). Un solo marco para las ventanas que interrumpen la partida —confirmar,
 * salir, revivir—, para que todas hablen el mismo idioma:
 *
 * - **La cinta**: el título no vive dentro de la caja, va en una cinta con colas plegadas que
 *   CABALGA el borde superior (el mismo gesto que el banderín de la vitrina, [NameBanner]).
 * - **El icono con rayos**: un resplandor con rayos que gira despacio detrás del dibujo. Con
 *   «reducir efectos» se queda quieto.
 * - **La entrada**: la tarjeta cae desde arriba con muelle, y la cinta y el icono saltan detrás con
 *   un pequeño retardo — tres golpes en vez de uno.
 * - Botones: [ArcadeButton] (canto + hundido), el botón de la casa.
 *
 * Reglas que hereda de [ConfirmOverlay] y no se negocian: nada de `Dialog` (app inmersiva), el
 * scrim y la tarjeta se comen el toque con `detectTapGestures` (sin semántica fantasma), e
 * `imePadding` para que el teclado no tape un campo de texto.
 *
 * @param accent color de la cinta y del borde; [accentDeep] su sombra.
 * @param onOutsideTap `null` = tocar fuera no cierra (revivir: la cuenta atrás decide).
 */
@Composable
fun GameDialogFrame(
    title: String,
    accent: Color,
    accentDeep: Color,
    onOutsideTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
    scrimAlpha: Float = 0.74f,
    icon: (@Composable BoxScope.() -> Unit)? = null,
    iconSize: Dp = 92.dp,
    /** Si no es null, una ✕ en la esquina de la tarjeta (paneles que se pueden cerrar sin decidir). */
    onClose: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val reduced = LocalReducedFx.current
    val drop = remember { Animatable(if (reduced) 1f else 0f) }
    val ribbon = remember { Animatable(if (reduced) 1f else 0f) }
    val iconPop = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        launch { drop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow)) }
        launch { delay(110); ribbon.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
        launch { delay(190); iconPop.animateTo(1f, spring(0.45f, Spring.StiffnessMediumLow)) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = scrimAlpha * 0.75f), Color.Black.copy(alpha = scrimAlpha))
                )
            )
            .pointerInput(onOutsideTap) { detectTapGestures { onOutsideTap?.invoke() } }
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = modifier
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp)
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.88f)
                .graphicsLayer {
                    val k = drop.value
                    translationY = (1f - k) * -60.dp.toPx()
                    scaleX = 0.85f + 0.15f * k; scaleY = 0.85f + 0.15f * k
                    alpha = k.coerceIn(0f, 1f)
                }
                // Traga el toque para que no llegue al scrim, sin añadir semántica.
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.TopCenter
        ) {
            val shape = RoundedCornerShape(26.dp)
            Column(
                modifier = Modifier
                    // Deja sitio arriba para la mitad de la cinta que asoma por fuera.
                    .padding(top = 22.dp)
                    .fillMaxWidth()
                    // Halo del color de la ventana: la tarjeta brilla sobre el tablero. Sombra con la
                    // forma de la tarjeta (un degradado en rectángulo dejaba una caja oscura detrás).
                    .shadow(24.dp, shape, ambientColor = accent, spotColor = accent)
                    .background(Brush.verticalGradient(listOf(Color(0xFF2C3466), Color(0xFF1A1F40))), shape)
                    .border(3.dp, Brush.verticalGradient(listOf(accent, accentDeep)), shape)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = 36.dp, bottom = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (icon != null) {
                    Box(
                        modifier = Modifier
                            .size(iconSize)
                            .graphicsLayer {
                                val k = iconPop.value
                                scaleX = k; scaleY = k
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        SpinningRays(accent, reduced, Modifier.fillMaxSize())
                        icon()
                    }
                    Spacer(Modifier.height(10.dp))
                }
                content()
            }
            if (onClose != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 30.dp, end = 8.dp)
                        .size(32.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(Color.White.copy(alpha = 0.10f))
                        .clickable(role = androidx.compose.ui.semantics.Role.Button) { onClose() }
                        .semantics { contentDescription = "Cerrar" },
                    contentAlignment = Alignment.Center
                ) { RawImage(com.cukiti.teikafusion.R.raw.ic_close, Modifier.size(14.dp)) }
            }
            RibbonTitle(
                title = title,
                accent = accent,
                accentDeep = accentDeep,
                modifier = Modifier.graphicsLayer {
                    val k = ribbon.value
                    scaleX = 0.6f + 0.4f * k; scaleY = 0.6f + 0.4f * k
                    alpha = k.coerceIn(0f, 1f)
                }
            )
        }
    }
}

/**
 * La cinta del título: cuerpo con degradado, colas plegadas a los lados (un tono más oscuro, por
 * detrás) y el texto en blanco con sombra dura, que es lo que lo hace leerse «de juego».
 */
@Composable
internal fun RibbonTitle(title: String, accent: Color, accentDeep: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .widthIn(min = 180.dp, max = 300.dp)
            .height(46.dp)
            .drawBehind {
                val w = size.width; val h = size.height
                val tail = h * 0.55f
                val drop = h * 0.22f
                // Colas: por detrás y un poco más abajo, con la muesca en V.
                val left = Path().apply {
                    moveTo(-tail * 0.2f, drop); lineTo(tail, drop); lineTo(tail, h + drop)
                    lineTo(-tail * 0.2f, h + drop); lineTo(tail * 0.35f, drop + h / 2f); close()
                }
                val right = Path().apply {
                    moveTo(w + tail * 0.2f, drop); lineTo(w - tail, drop); lineTo(w - tail, h + drop)
                    lineTo(w + tail * 0.2f, h + drop); lineTo(w - tail * 0.35f, drop + h / 2f); close()
                }
                drawPath(left, accentDeep)
                drawPath(right, accentDeep)
                // Cuerpo.
                val bodyL = tail * 0.55f; val bodyR = w - tail * 0.55f
                drawRoundRect(
                    Brush.verticalGradient(listOf(accent, accentDeep)),
                    topLeft = Offset(bodyL, 0f),
                    size = androidx.compose.ui.geometry.Size(bodyR - bodyL, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.22f, h * 0.22f)
                )
                // Brillo superior.
                drawRoundRect(
                    Color.White.copy(alpha = 0.22f),
                    topLeft = Offset(bodyL + 4f, 3f),
                    size = androidx.compose.ui.geometry.Size(bodyR - bodyL - 8f, h * 0.38f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h * 0.18f, h * 0.18f)
                )
            }
            .padding(horizontal = 34.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title.uppercase(),
            maxLines = 1,
            modifier = Modifier.semantics { heading() },
            textAlign = TextAlign.Center,
            style = TextStyle(
                color = Color.White,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                shadow = Shadow(Color(0xAA000000), Offset(0f, 3f), 0f),
            ),
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                minFontSize = 12.sp, maxFontSize = 20.sp, stepSize = 0.5.sp
            ),
        )
    }
}

/** Resplandor con rayos detrás del icono. Gira en capa (rotationZ), así que el frame no recompone. */
@Composable
internal fun SpinningRays(accent: Color, reduced: Boolean, modifier: Modifier) {
    val rotation = if (reduced) null else rememberInfiniteTransition(label = "rays").animateFloat(
        0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "raysRot"
    )
    Canvas(
        modifier.graphicsLayer {
            rotationZ = rotation?.value ?: 0f
            // Los rayos se salen del hueco del icono a propósito: el resplandor no es una caja.
            scaleX = 1.45f; scaleY = 1.45f
        }
    ) {
        val r = size.minDimension / 2f
        drawCircle(
            Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.12f), Color.Transparent)),
            radius = r
        )
        val rays = 12
        for (i in 0 until rays) {
            val a0 = (i * 360f / rays) * PI.toFloat() / 180f
            val a1 = a0 + (360f / rays / 2.4f) * PI.toFloat() / 180f
            val p = Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + cos(a0) * r, center.y + sin(a0) * r)
                lineTo(center.x + cos(a1) * r, center.y + sin(a1) * r)
                close()
            }
            drawPath(p, Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), center, r))
        }
    }
}
