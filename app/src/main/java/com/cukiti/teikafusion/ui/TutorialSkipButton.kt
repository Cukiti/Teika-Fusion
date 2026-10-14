package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * **SALTAR TUTORIAL — el botón** (rediseño 2026-09-04, pedido del usuario).
 *
 * Hasta hoy era un texto gris al 50 % bajo la barra superior: cumplía la función y no se leía como
 * un botón, así que el jugador que quería salir de las lecciones acababa usando el «❮» o el
 * engranaje —las dos puertas traseras que ahora están cerradas—. Si la única salida es esta, tiene
 * que **parecer** una salida.
 *
 * Lo que pidió el usuario, punto por punto:
 *
 *  - **Pegado al borde superior del tablero y a la derecha** — la banda que el usuario marcó sobre
 *    una captura. Comparte margen lateral con el tablero (16 dp), así que sus bordes derechos caen
 *    en la misma vertical. *(Corregido el 2026-09-04: colgaba bajo la barra superior y ahí pisaba
 *    visualmente el chip del anuncio recompensado.)*
 *  - **El icono típico de saltar** —el de «siguiente» de un reproductor de música: dos triángulos y
 *    una barra—. Se dibuja con [Canvas] y no con un glifo de fuente: «⏭» depende de la fuente de
 *    emoji del dispositivo y es exactamente el tipo de icono que en unos móviles sale y en otros
 *    no (le pasa a la moneda 🪙 del inventario).
 *  - **Volumen de verdad**: cara con degradado, brillo arriba, borde vivo y un **labio** debajo que
 *    es lo que le da el relieve. No es una sombra difusa: es el mismo truco de los botones de
 *    Clash/Candy, un cuerpo sólido más oscuro asomando por el pie.
 *  - **Camuflado con el fondo del juego**: la paleta sale de `AppBackground` (#2F54B5), que es lo
 *    que hay detrás. Está, no llama. *(Corregido el 2026-09-04: el primer intento se camufló contra
 *    el color del TABLERO y sobre el azul se leía como una pastilla oscura pegada encima.)*
 *  - **Animación de presionado**: la cara **baja hasta el labio** y se encoge un pelín; al soltar
 *    vuelve con un rebote corto. El hundido es el gesto, no un cambio de color.
 *
 * El toque se recoge con `pointerInput` en la pasada `Initial` y no con `clickable {}`: hace falta
 * el estado de PRESIONADO para animar el hundido, y la lección del bloque 24 sigue en pie.
 */
@Composable
fun TutorialSkipButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // MISMO GESTO QUE EL RESTO DE BOTONES DE ARCADE (2026-09-04). Aqui vivia el original: un bucle
    // de puntero en la pasada Initial que consumia el down. En esta pantalla funcionaba -no hay nada
    // que hacer scroll debajo-, pero era el mismo codigo que en el Inventario perdia toques y le
    // robaba el gesto a la rejilla. Una sola forma de pulsar en todo el juego. Ver rememberArcadeSink.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberArcadeSink(pressed, enabled = true)

    /**
     * Recorrido del hundido. Es tambien el alto del labio: al presionar, la cara lo tapa entero.
     * 5 dp y no 4: sobre el fondo camuflado el labio ES el relieve -no hay contraste de color que
     * ayude-, asi que tiene que verse desde lejos y su desaparicion tiene que notarse al pulsar.
     */
    val depth = 5.dp

    Box(
        modifier = modifier
            .semantics {
                role = Role.Button
                contentDescription = "Saltar el tutorial y jugar el primer nivel"
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            // El labio vive en el padding inferior: la caja mide cara + labio, y la cara se hunde
            // dentro de ella sin mover a nadie del layout.
            .padding(bottom = depth)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .defaultMinSize(minHeight = 34.dp)
                .graphicsLayer {
                    val sunk = press.value
                    translationY = depth.toPx() * sunk
                    val s = 1f - 0.035f * sunk
                    scaleX = s
                    scaleY = s
                }
                .drawBehind {
                    val sunk = press.value
                    val r = 15.dp.toPx()
                    val lip = depth.toPx()
                    // EL LABIO: el cuerpo del botón, asomando por debajo de la cara. Se dibuja
                    // desplazado hacia abajo y se come el hundido, así que al presionar desaparece.
                    drawRoundRect(
                        color = SKIP_LIP,
                        topLeft = Offset(0f, lip * (1f - sunk)),
                        size = Size(size.width, size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )
                    // LA CARA: degradado vertical, más claro arriba — la luz viene de arriba, como
                    // en el resto de la app.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to SKIP_FACE_TOP,
                            1f to SKIP_FACE_BOTTOM,
                        ),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )
                    // BRILLO superior: una banda finísima que separa la cara del aire.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.16f - 0.10f * sunk),
                            1f to Color.Transparent,
                        ),
                        size = Size(size.width, size.height * 0.45f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    )
                    // BORDE vivo: es lo que lo despega del fondo del bioma sin encender un acento.
                    drawRoundRect(
                        color = SKIP_EDGE.copy(alpha = 0.55f),
                        size = size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                        style = Stroke(width = 1.2.dp.toPx()),
                    )
                }
                .clip(RoundedCornerShape(15.dp))
                .padding(start = 13.dp, end = 11.dp)
        ) {
            Text(
                text = "SALTAR",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.1.sp,
            )
            Spacer(Modifier.width(7.dp))
            SkipGlyph(modifier = Modifier.size(width = 15.dp, height = 13.dp))
        }
    }
}

/**
 * El icono de «siguiente pista»: dos triángulos hacia la derecha y una barra vertical. Dibujado, no
 * tipografiado — ver el KDoc de [TutorialSkipButton].
 */
@Composable
private fun SkipGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val h = size.height
        val barW = h * 0.17f
        val triW = (size.width - barW) / 2f
        fun triangle(left: Float) = Path().apply {
            moveTo(left, 0f)
            lineTo(left + triW * 0.92f, h / 2f)
            lineTo(left, h)
            close()
        }
        drawPath(triangle(0f), color = Color.White)
        drawPath(triangle(triW), color = Color.White)
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(size.width - barW, 0f),
            size = Size(barW, h),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW * 0.4f, barW * 0.4f),
        )
    }
}

// ── PALETA CAMUFLADA CON EL FONDO (corregida el 2026-09-04, sobre captura del usuario) ──────────
// El primer intento se camufló contra el color del TABLERO (#1F2643) y el resultado fue justo lo
// contrario: sobre el azul del juego se leía como una pastilla oscura pegada encima, gemela del
// chip del anuncio que tenía al lado.
//
// La referencia correcta es `AppBackground` (#2F54B5), que es lo que hay DETRÁS del botón. La cara
// es ese mismo azul con un grado de luz arriba y uno de sombra abajo —lo justo para que se vea
// volumen, no para que se vea otro color—, y **el relieve lo dan el labio y el borde, no el
// contraste**: eso es lo que hace que parezca tallado en el fondo en vez de apoyado sobre él.
private val SKIP_FACE_TOP = Color(0xFF3E63C9)      // el fondo, un grado más claro (luz de arriba)
private val SKIP_FACE_BOTTOM = Color(0xFF2A4BA6)   // el fondo, un grado más oscuro
private val SKIP_LIP = Color(0xFF1B3372)        // el canto inferior: el mismo azul, en sombra
private val SKIP_EDGE = Color(0xFF93AAF2)          // filo claro, del mismo azul
