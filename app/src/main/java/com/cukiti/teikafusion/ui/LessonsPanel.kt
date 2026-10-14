package com.cukiti.teikafusion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.TutorialCampaign

/**
 * **LAS LECCIONES, FUERA DE LA CAVERNA DE CRISTAL** (pedido del usuario, 2026-09-03).
 *
 * Hasta ahora la única puerta al tutorial eran los siete primeros tiles del primer capítulo: para
 * repasar «cómo funcionaban los turnos» había que volver al mapa, entrar en la Caverna de Cristal y
 * buscarlos entre sus niveles, como si fueran contenido del bioma. No lo son —son la escuela del
 * juego— y encima ensuciaban el mural de un capítulo con siete cuadros que no cuentan su historia.
 *
 * Ahora viven detrás del **«?»** de la ventana «Elige tu mundo», que es donde el jugador ya va
 * cuando quiere orientarse. Los niveles 1-7 **siguen existiendo en [com.cukiti.teikafusion.model.LevelMap]
 * exactamente igual**: solo dejan de pintarse en el mural. Esa es la decisión importante — renumerar
 * la campaña habría arrastrado estrellas guardadas, murales, desbloqueos de compañeros y el ranking
 * por nivel, y no hacía falta para lo que se pedía.
 */
@Composable
fun LessonsPanel(
    /** Lección más alta jugable. Las de más allá se enseñan bloqueadas, no ocultas. */
    maxUnlockedLevel: Int,
    onPlayLesson: (Int) -> Unit,
    onUiClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    BackHandler(enabled = true) { onDismiss() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF1A2140), Color(0xFF0C1128))))
                .border(1.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(24.dp))
                // Traga el toque para que pulsar DENTRO no cierre el panel.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "CÓMO SE JUEGA",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.4.sp,
                        color = Color.White
                    )
                    Text(
                        "Son gratis: no gastan vidas.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button
                        ) { onUiClick(); onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    RawImage(R.raw.ic_close, Modifier.size(18.dp))
                }
            }

            // TRES POR FILA (2026-09-23, pedido del usuario: «se siente minimalista… ventanas de 3 por
            // línea con una preview del tutorial de fondo y el título»). Cada tarjeta enseña EL
            // TABLERO REAL con el que empieza su lección —celdas, hueco de la ficha guiada y Teikas—,
            // dibujado desde `TutorialCampaign.spec`: si una lección cambia, su miniatura cambia sola.
            // La frase larga de instrucción sale de la tarjeta (queda para el lector de pantalla).
            val lessons = (TutorialCampaign.FIRST_LEVEL..TutorialCampaign.LAST_LEVEL).toList()
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                lessons.chunked(3).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Una fila corta (la 7.ª lección sola) se CENTRA con huecos a los lados, y
                        // sus tarjetas miden lo mismo que las de arriba.
                        val gap = (3 - row.size) / 2f
                        if (gap > 0f) Spacer(Modifier.weight(gap))
                        row.forEach { level ->
                            val spec = TutorialCampaign.spec(level)
                            LessonCard(
                                number = level - TutorialCampaign.FIRST_LEVEL + 1,
                                spec = spec,
                                unlocked = level <= maxUnlockedLevel,
                                done = level < maxUnlockedLevel,
                                modifier = Modifier.weight(1f),
                                onClick = { onUiClick(); onPlayLesson(level) }
                            )
                        }
                        if (gap > 0f) Spacer(Modifier.weight(gap))
                    }
                }
            }
        }
    }
}

/**
 * Una lección: su tablero de fondo, el número en una chapa, el título sobre un degradado y, si ya
 * la hiciste, un check. Bloqueada = miniatura apagada con candado (se enseña, no se esconde).
 */
@Composable
private fun LessonCard(
    number: Int,
    spec: TutorialCampaign.Spec?,
    unlocked: Boolean,
    done: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && unlocked) 0.93f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lessonPress"
    )
    val shape = RoundedCornerShape(16.dp)
    val title = spec?.title ?: "LECCIÓN"
    Box(
        modifier = modifier
            .aspectRatio(0.78f)
            .scale(scale)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF26305E), Color(0xFF141A36))))
            .border(
                1.5.dp,
                if (done) Color(0xFF6BE08A).copy(alpha = 0.7f)
                else if (unlocked) Color(0xFF4DB8FF).copy(alpha = 0.6f)
                else Color.White.copy(alpha = 0.10f),
                shape
            )
            .then(
                if (unlocked) Modifier.clickable(
                    interactionSource = source, indication = null, role = Role.Button, onClick = onClick
                ) else Modifier
            )
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    append("Lección $number: $title. ")
                    append(spec?.instruction.orEmpty())
                    if (done) append(" Completada.") else if (!unlocked) append(" Bloqueada.")
                }
            }
    ) {
        // El tablero de la lección, a sangre en la parte de arriba.
        if (spec != null) {
            LessonPreview(
                spec = spec,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .padding(6.dp)
                    .alpha(if (unlocked) 1f else 0.3f)
            )
        }
        // Degradado del pie: el título se lee sobre cualquier tablero.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF00C1128))))
        )
        androidx.compose.foundation.text.BasicText(
            text = title,
            maxLines = 2,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 6.dp, vertical = 7.dp),
            autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                minFontSize = 7.sp, maxFontSize = 11.sp, stepSize = 0.5.sp
            ),
            style = androidx.compose.ui.text.TextStyle(
                color = Color.White.copy(alpha = if (unlocked) 1f else 0.5f),
                fontWeight = FontWeight.Black,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                lineHeight = 12.sp,
            )
        )
        // La chapa del número.
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(5.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(
                    if (unlocked) Brush.linearGradient(listOf(Color(0xFF4DB8FF), Color(0xFF7B2FBE)))
                    else Brush.linearGradient(listOf(Color(0xFF39406A), Color(0xFF2A3050)))
                )
                .border(1.5.dp, Color(0xFF0C1128), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("$number", fontSize = 12.sp, fontWeight = FontWeight.Black, color = Color.White)
        }
        when {
            done -> Box(
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(22.dp).clip(CircleShape)
                    .background(Color(0xFF1B6234)).border(1.5.dp, Color(0xFF6BE08A), CircleShape),
                contentAlignment = Alignment.Center
            ) { RawImage(R.raw.ic_check, Modifier.size(13.dp)) }
            !unlocked -> Box(
                Modifier.align(Alignment.Center).offset(y = (-10).dp).size(34.dp)
                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) { RawImage(R.raw.ic_lock, Modifier.size(18.dp)) }
            else -> Box(
                // Disponible y sin hacer: el «play» invita.
                Modifier.align(Alignment.Center).offset(y = (-10).dp).size(34.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                    .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) { RawImage(R.raw.ic_play, Modifier.size(15.dp)) }
        }
    }
}

/**
 * La miniatura: la madera inicial de la lección con el bloque equipado, el hueco donde va la ficha
 * guiada (borde brillante) y sus Teikas de partida. Estática: siete tarjetas quietas, cero frames.
 */
@Composable
private fun LessonPreview(spec: TutorialCampaign.Spec, modifier: Modifier) {
    val skin = LocalBlockSkin.current
    Canvas(modifier) {
        val n = com.cukiti.teikafusion.model.GameState.GRID_SIZE
        val cell = size.minDimension / n
        drawRoundRect(Color(0xFF0E1330), cornerRadius = androidx.compose.ui.geometry.CornerRadius(cell * 0.6f))
        spec.initialCells.forEach { c ->
            // Madera: color plano si la lección lo pide; si no, la paleta reflejada de siempre.
            val colorType = spec.flatColorType
                ?: ((minOf(c.x, n - 1 - c.x) + minOf(c.y, n - 1 - c.y)) % 5)
            drawSkinnedBlock(skin, colorType, Offset(c.x * cell, c.y * cell), Size(cell, cell))
        }
        spec.gap.forEach { p ->
            val tl = Offset(p.x * cell + cell * 0.08f, p.y * cell + cell * 0.08f)
            val sz = Size(cell * 0.84f, cell * 0.84f)
            val r = androidx.compose.ui.geometry.CornerRadius(cell * 0.18f)
            drawRoundRect(Color.White.copy(alpha = 0.10f), tl, sz, r)
            drawRoundRect(Color(0xFF5CFFB0), tl, sz, r, style = Stroke(width = cell * 0.12f))
        }
        spec.initialTeikas.forEach { t ->
            val d = (t.type.ordinal + 1).coerceAtMost(5) * cell
            val center = Offset(t.x * cell, t.y * cell)
            drawCircle(tierColor(t.type), radius = d / 2f, center = center)
            drawCircle(Color(0xFF16263C), radius = d / 2f, center = center, style = Stroke(width = cell * 0.10f))
            val eye = d * 0.09f
            drawCircle(Color(0xFF16263C), eye, Offset(center.x - d * 0.16f, center.y - d * 0.04f))
            drawCircle(Color(0xFF16263C), eye, Offset(center.x + d * 0.16f, center.y - d * 0.04f))
        }
    }
}

/**
 * **EL BOTÓN «?»**: anillo de arcoíris girando, halo de neón y un latido suave.
 *
 * Es el único elemento del picker que se anima solo, y eso es a propósito: tiene que encontrarse sin
 * buscarlo, porque la alternativa —no encontrar nunca cómo repasar las reglas— es la que deja al
 * jugador atascado. El arcoíris gira **una vuelta cada 3,6 s** (lento: un giro rápido se lee como
 * «cargando», no como «premium») y el halo respira en contrafase para que el brillo no lata a la vez
 * que el borde y quede plano.
 *
 * PERF: los dos brushes se construyen en `drawWithCache` —una vez por tamaño, no por frame— y la
 * animación entra solo por el ángulo y el alfa, que se leen en el dibujo. Y el bucle **solo corre si
 * la pestaña está a la vista** ([LocalTabActive]): el picker vive dentro de Inicio y, sin este gate,
 * seguiría pidiendo un fotograma cada 16 ms con el jugador en la Tienda o jugando.
 */
@Composable
fun LessonsHelpButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = LocalTabActive.current
    val transition = rememberInfiniteTransition(label = "lessonsHelp")
    val spin by if (active) transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3600, easing = LinearEasing), RepeatMode.Restart),
        label = "lessonsSpin"
    ) else remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    val glow by if (active) transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Reverse),
        label = "lessonsGlow"
    ) else remember { androidx.compose.runtime.mutableFloatStateOf(0.7f) }

    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(
        if (pressed) 0.92f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "lessonsPress"
    )

    Box(
        modifier = modifier
            .size(40.dp)
            .scale(press)
            .semantics { contentDescription = "Cómo se juega: repasar las lecciones" }
            .drawBehind {
                // Halo de neón: se dibuja FUERA del anillo, así que va en drawBehind y no recorta.
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF7B2FBE).copy(alpha = 0.55f * glow),
                            Color(0xFF4DB8FF).copy(alpha = 0.22f * glow),
                            Color.Transparent
                        ),
                        center = center,
                        radius = size.minDimension * 0.78f
                    ),
                    radius = size.minDimension * 0.78f
                )
            }
            .clip(CircleShape)
            .background(Color(0xFF141A33))
            .drawWithCache {
                val stroke = size.minDimension * 0.09f
                // Arcoíris completo: el barrido empieza y acaba en el MISMO rojo para que la vuelta
                // no tenga costura visible al girar.
                val mid = Offset(size.width / 2f, size.height / 2f)
                val rainbow = Brush.sweepGradient(
                    listOf(
                        Color(0xFFFF3D6E), Color(0xFFFFB300), Color(0xFF5CFFB0),
                        Color(0xFF4DB8FF), Color(0xFF7B2FBE), Color(0xFFFF3D6E)
                    ),
                    center = mid
                )
                onDrawBehind {
                    rotate(spin) {
                        drawCircle(
                            brush = rainbow,
                            radius = (size.minDimension - stroke) / 2f,
                            style = Stroke(width = stroke)
                        )
                    }
                    // Realce interior: da profundidad de botón en vez de aro plano.
                    drawCircle(
                        brush = Brush.verticalGradient(
                            listOf(Color.White.copy(alpha = 0.10f), Color.Transparent),
                            startY = 0f, endY = size.height * 0.6f
                        ),
                        radius = (size.minDimension - stroke * 2.2f) / 2f
                    )
                }
            }
            .clickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "?",
            fontSize = 19.sp,
            fontWeight = FontWeight.Black,
            color = Color.White
        )
    }
}
