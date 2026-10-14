package com.cukiti.teikafusion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.model.TutorialCampaign
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Guía visual no invasiva de las cuatro lecciones.
 *
 * No instala detectores táctiles ni oscurece el juego: el tablero real conserva el control. En las
 * lecciones de arrastre, una copia exacta de la ficha y la mano comparten el mismo progreso y la
 * misma elevación relativa que el gesto real; al empezar la física queda una señal breve de inclinación.
 */
@Composable
fun ProgressiveTutorialOverlay(
    level: Int,
    piecePlaced: Boolean,
    pieceGrabbed: Boolean,
    tutorialStage: Int,
    tiltDirection: Int,
    stageReady: Boolean,
    positions: TutorialPositions,
    /**
     * **Hay algo más importante que en qué lección vas.** La píldora de progreso se retira mientras
     * dura.
     *
     * Son dos casos y los dos se vieron en vídeo. Un **instante de enseñanza** (TIEMPO BALA): vive en
     * la misma banda de debajo de la bandeja y en `tutorialexample` la píldora se comía la segunda
     * línea del rótulo. Y la **hoja de habilidades abierta** (`analisistutorial`, 1:24): al abrirse,
     * el tirador sale de la composición y su rectángulo se congela, así que la píldora se plantaba
     * sobre la tarjeta de Cristóbal tapando justo la línea que explica qué hace.
     */
    teachingShotActive: Boolean = false,
    /**
     * Sube en uno cada vez que el jugador suelta la ficha guiada FUERA de su casilla.
     *
     * Hasta el 2026-09-01 esa jugada no producía nada: la sombra se pintaba válida, sonaba la
     * háptica de encaje correcto y la ficha volvía a la bandeja sin una palabra. En la grabación
     * `analisistutorial` es la pregunta que no tenía respuesta — «¿y si el jugador no hace lo que el
     * tutorial le dice?». Ahora la colocación se rechaza en la propia sombra y esto dispara el
     * porqué. Ver `GameViewModel.isTutorialMisplacement`.
     */
    misplaceToken: Int = 0,
    /**
     * Qué dice la píldora de corrección. Hoy solo hay una forma de decir «eso no» —soltar la ficha
     * fuera de su casilla—, pero el mensaje sigue siendo un parámetro y no una constante: hasta el
     * 2026-09-07 hubo una segunda (tocar la ficha para rotarla, P-202) y la lección que la escribió
     * sigue en pie —cada rechazo distinto necesita su frase, o el gesto se queda mudo y parece que
     * el juego se ha colgado—.
     */
    misplaceMessage: String = TUTORIAL_MISPLACE_TEXT,
) {
    val spec = TutorialCampaign.spec(level) ?: return
    val density = LocalDensity.current
    val reducedMotion = LocalReducedFx.current
    val guidedPiece = spec.piece
    val targetOrigin = spec.targetOrigin
    // ── LA SOMBRA SE VA CUANDO LA FICHA SE MUEVE (2026-09-04, pedido del usuario) ──────────────
    // «El cuadro sombra que avisa dónde tiene que ir la ficha no se quita cuando el jugador la
    // coloca; se ve muy feo que se quede mientras la está colocando.»
    //
    // Dos condiciones nuevas, una por cada mitad del problema:
    //
    //  · `!pieceGrabbed` — en cuanto el jugador levanta la ficha guiada, el trabajo de la sombra lo
    //    hace el FANTASMA real del arrastre, que dice lo mismo y además sigue al dedo. Las dos
    //    capas cian encima de las mismas celdas es lo que se veía sucio, y además la sombra queda
    //    debajo de la ficha durante todo el aterrizaje.
    //  · `!piecePlaced` — que ahora significa «la ficha guiada ya no está en la bandeja» y no «la
    //    bandeja está vacía»: cualquier camino que reponga fichas (una habilidad, una red
    //    anti-atasco) devolvía la sombra a un tablero donde ya no había nada que colocar ahí.
    //    Ver `GameScreen`.
    val showTargetGuide = guidedPiece != null && targetOrigin != null &&
        !piecePlaced && !pieceGrabbed
    val showMimic = showTargetGuide
    val showTiltGuide = spec.teachesTilt && !showTargetGuide && stageReady && tiltDirection != 0

    val entry = remember(level) { Animatable(if (reducedMotion) 1f else 0f) }
    val glow = remember(level) { Animatable(0.42f) }
    val demoPhase = remember(level) { Animatable(0f) }
    val dragAlpha = remember(level) { Animatable(0f) }
    val tilt = remember(level, tutorialStage, tiltDirection) { Animatable(0f) }
    val nudge = remember(level) { Animatable(0f) }

    LaunchedEffect(misplaceToken) {
        if (misplaceToken <= 0) return@LaunchedEffect
        nudge.snapTo(1f)
        // Se queda quieto lo justo para leerse y se va solo: un aviso de corrección no puede pedir
        // que lo cierren, porque quien lo ha provocado ya está intentando la jugada otra vez.
        kotlinx.coroutines.delay(1500L)
        nudge.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(level, reducedMotion) {
        if (reducedMotion) entry.snapTo(1f)
        else entry.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
    }

    LaunchedEffect(level, showTargetGuide, reducedMotion) {
        if (reducedMotion || !showTargetGuide) {
            glow.snapTo(0.72f)
            return@LaunchedEffect
        }
        while (true) {
            glow.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
            glow.animateTo(0.38f, tween(650, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(level, showMimic, reducedMotion) {
        if (!showMimic || reducedMotion) {
            dragAlpha.snapTo(0f)
            demoPhase.snapTo(0f)
            return@LaunchedEffect
        }
        // Si la ficha real acaba de regresar tras un intento inválido, dejamos que se asiente antes
        // de reiniciar la demostración. `pieceGrabbed` permanece true durante esos 150 ms de retorno.
        delay(260L)
        while (true) {
            demoPhase.snapTo(0f)
            dragAlpha.snapTo(0f)
            dragAlpha.animateTo(1f, tween(180, easing = FastOutSlowInEasing))
            // 0→1: la mano se acerca; 1→2: recoge y eleva; 2→3: ambos viajan como grupo rígido.
            demoPhase.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            demoPhase.animateTo(2f, tween(220, easing = FastOutSlowInEasing))
            demoPhase.animateTo(3f, tween(880, easing = FastOutSlowInEasing))
            delay(420L)
            dragAlpha.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
            delay(240L)
        }
    }

    LaunchedEffect(level, tutorialStage, tiltDirection, showTiltGuide, reducedMotion) {
        if (!showTiltGuide || reducedMotion) {
            tilt.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            tilt.animateTo(tiltDirection.toFloat(), tween(620, easing = FastOutSlowInEasing))
            delay(160L)
            tilt.animateTo(0f, tween(500, easing = FastOutSlowInEasing))
            delay(260L)
        }
    }

    BackHandler(enabled = true) { }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val rootOffset = positions.rootOffset
        fun Rect.local(): Rect = Rect(
            left - rootOffset.x,
            top - rootOffset.y,
            right - rootOffset.x,
            bottom - rootOffset.y
        )
        fun Rect.orFallback(fallback: Rect): Rect =
            if (width < 1f || height < 1f) fallback else this

        val fallbackBoardSize = minOf(screenWidthPx - 24f, screenHeightPx * 0.46f)
        val fallbackBoard = Rect(
            left = (screenWidthPx - fallbackBoardSize) / 2f,
            top = screenHeightPx * 0.26f,
            right = (screenWidthPx + fallbackBoardSize) / 2f,
            bottom = screenHeightPx * 0.26f + fallbackBoardSize
        )
        val board = positions.board.local().orFallback(fallbackBoard)
        val tray = positions.tray.local().orFallback(
            Rect(screenWidthPx * 0.18f, screenHeightPx * 0.78f, screenWidthPx * 0.82f, screenHeightPx * 0.96f)
        )
        val boardCellPx = board.width / GameState.GRID_SIZE

        if (showTargetGuide) {
            val guidedPiece = requireNotNull(guidedPiece)
            val targetOrigin = requireNotNull(targetOrigin)
            val targetLeft = board.left + targetOrigin.x * boardCellPx
            val targetTop = board.top + targetOrigin.y * boardCellPx
            val targetWidth = guidedPiece.width * boardCellPx
            val targetHeight = guidedPiece.height * boardCellPx
            val targetCenter = Offset(targetLeft + targetWidth / 2f, targetTop + targetHeight / 2f)
            val fallbackSourceCell = boardCellPx * 0.58f
            val fallbackPieceRect = Rect(
                offset = Offset(
                    tray.center.x - guidedPiece.width * fallbackSourceCell / 2f,
                    tray.center.y - guidedPiece.height * fallbackSourceCell / 2f
                ),
                size = Size(
                    guidedPiece.width * fallbackSourceCell,
                    guidedPiece.height * fallbackSourceCell
                )
            )
            val sourceRect = positions.piece.local().orFallback(fallbackPieceRect)
            val sourceCenter = sourceRect.center
            val sourceCell = (sourceRect.width / guidedPiece.width).coerceAtLeast(1f)

            // Destino exacto y sutil: sin scrim ni recorte que oscurezca el resto de la pantalla.
            Canvas(modifier = Modifier.fillMaxSize().alpha(entry.value)) {
                guidedPiece.shape.forEach { relative ->
                    val cellTopLeft = Offset(
                        targetLeft + relative.x * boardCellPx,
                        targetTop + relative.y * boardCellPx
                    )
                    drawRoundRect(
                        color = Color(0xFF55E6FF).copy(alpha = 0.10f + glow.value * 0.14f),
                        topLeft = cellTopLeft + Offset(2f, 2f),
                        size = Size(boardCellPx - 4f, boardCellPx - 4f),
                        cornerRadius = CornerRadius(boardCellPx * 0.16f)
                    )
                    drawRoundRect(
                        color = Color(0xFF55E6FF).copy(alpha = 0.55f + glow.value * 0.30f),
                        topLeft = cellTopLeft + Offset(2f, 2f),
                        size = Size(boardCellPx - 4f, boardCellPx - 4f),
                        cornerRadius = CornerRadius(boardCellPx * 0.16f),
                        style = Stroke(width = with(density) { 1.6.dp.toPx() })
                    )
                }
            }

            if (showMimic) {
                fun mix(from: Offset, to: Offset, amount: Float) = Offset(
                    from.x + (to.x - from.x) * amount,
                    from.y + (to.y - from.y) * amount
                )

                val phase = demoPhase.value
                val approach = phase.coerceIn(0f, 1f)
                val pickup = (phase - 1f).coerceIn(0f, 1f)
                val travel = (phase - 2f).coerceIn(0f, 1f)
                // Copia EXACTA de liftForPiece(): durante el transporte la separación no cambia ni un
                // píxel. La mano ya no persigue a una ficha que se escapa hacia arriba.
                val fingerToPieceCenter =
                    (guidedPiece.height / 2f + FINGER_LIFT_CELLS) * boardCellPx
                val heldPieceStart = Offset(sourceCenter.x, sourceCenter.y - fingerToPieceCenter)
                val fingerTarget = Offset(targetCenter.x, targetCenter.y + fingerToPieceCenter)
                val approachStart = sourceCenter + Offset(boardCellPx * 1.15f, boardCellPx * 1.25f)
                val fingerCenter = when {
                    phase < 1f -> mix(approachStart, sourceCenter, approach)
                    phase < 2f -> sourceCenter
                    else -> mix(sourceCenter, fingerTarget, travel)
                }
                val pieceCenter = when {
                    phase < 1f -> sourceCenter
                    phase < 2f -> mix(sourceCenter, heldPieceStart, pickup)
                    else -> mix(heldPieceStart, targetCenter, travel)
                }
                val sourceScale = (sourceCell / boardCellPx).coerceIn(0.2f, 1f)
                val guideScale = sourceScale + (1f - sourceScale) * pickup
                val boardCellDp = with(density) { boardCellPx.toDp() }

                // El clon nace exactamente encima de la ficha real al tocarla, sube hasta la altura
                // de juego y desde ahí comparte una traslación rígida con la yema.
                PieceView(
                    piece = guidedPiece,
                    isDragging = false,
                    isQueuePiece = false,
                    cellSize = boardCellDp,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (pieceCenter.x - targetWidth / 2f).roundToInt(),
                                (pieceCenter.y - targetHeight / 2f).roundToInt()
                            )
                        }
                        .graphicsLayer {
                            scaleX = guideScale
                            scaleY = guideScale
                            alpha = if (phase < 1f) 0f
                                else dragAlpha.value * entry.value * 0.94f
                        }
                )

                val handSize = 132.dp
                val handSizePx = with(density) { handSize.toPx() }
                Image(
                    painter = painterResource(R.drawable.tutorial_hand_teika_v2),
                    contentDescription = "La mano recoge la ficha y la lleva agarrada hasta el centro",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(handSize)
                        .offset {
                            // Ancla calibrada a la yema de la ilustración: 14 % X, 9 % Y.
                            IntOffset(
                                (fingerCenter.x - handSizePx * 0.14f).roundToInt(),
                                (fingerCenter.y - handSizePx * 0.09f).roundToInt()
                            )
                        }
                        .alpha(dragAlpha.value * entry.value)
                )
            }
        }

        if (showTiltGuide) {
            val guideWidth = 148.dp
            val guideHeight = 104.dp
            val guideWidthPx = with(density) { guideWidth.toPx() }
            val guideHeightPx = with(density) { guideHeight.toPx() }
            Canvas(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (tray.center.x - guideWidthPx / 2f).roundToInt(),
                            (tray.center.y - guideHeightPx / 2f).roundToInt()
                        )
                    }
                    .size(width = guideWidth, height = guideHeight)
                    .graphicsLayer {
                        rotationZ = tilt.value * 10f
                        alpha = entry.value * 0.94f
                    }
            ) {
                val cyan = Color(0xFF55E6FF)
                val phoneWidth = size.width * 0.38f
                val phoneHeight = size.height * 0.84f
                val phoneTopLeft = Offset((size.width - phoneWidth) / 2f, (size.height - phoneHeight) / 2f)
                drawRoundRect(
                    color = Color(0xFF171D3B).copy(alpha = 0.86f),
                    topLeft = phoneTopLeft,
                    size = Size(phoneWidth, phoneHeight),
                    cornerRadius = CornerRadius(phoneWidth * 0.18f)
                )
                drawRoundRect(
                    color = cyan,
                    topLeft = phoneTopLeft,
                    size = Size(phoneWidth, phoneHeight),
                    cornerRadius = CornerRadius(phoneWidth * 0.18f),
                    style = Stroke(width = 2.4f)
                )
                drawCircle(
                    color = cyan.copy(alpha = 0.8f),
                    radius = 3.2f,
                    center = Offset(size.width / 2f, phoneTopLeft.y + phoneHeight * 0.88f)
                )
                val arrowY = size.height / 2f
                val stroke = 4.2f
                if (tiltDirection < 0) {
                    val tipX = 4f
                    drawLine(cyan, Offset(phoneTopLeft.x - 8f, arrowY), Offset(tipX, arrowY), strokeWidth = stroke)
                    drawLine(cyan, Offset(tipX, arrowY), Offset(16f, arrowY - 10f), strokeWidth = stroke)
                    drawLine(cyan, Offset(tipX, arrowY), Offset(16f, arrowY + 10f), strokeWidth = stroke)
                } else {
                    val tipX = size.width - 4f
                    drawLine(cyan, Offset(phoneTopLeft.x + phoneWidth + 8f, arrowY), Offset(tipX, arrowY), strokeWidth = stroke)
                    drawLine(cyan, Offset(tipX, arrowY), Offset(size.width - 16f, arrowY - 10f), strokeWidth = stroke)
                    drawLine(cyan, Offset(tipX, arrowY), Offset(size.width - 16f, arrowY + 10f), strokeWidth = stroke)
                }
            }
        }

        // ── Aviso de jugada equivocada ────────────────────────────────────────────────────────────
        // Cae sobre la casilla marcada, que es adonde el jugador tiene que mirar. No pide que lo
        // cierren y no bloquea nada: la mano guía sigue corriendo por debajo.
        if (nudge.value > 0.001f && targetOrigin != null && guidedPiece != null) {
            val nudgeCenterX = board.left + (targetOrigin.x + guidedPiece.width / 2f) * boardCellPx
            val nudgeBottom = board.top + targetOrigin.y * boardCellPx - boardCellPx * 0.35f
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(0, (nudgeBottom - with(density) { 46.dp.toPx() }).roundToInt())
                    }
                    .graphicsLayer {
                        alpha = nudge.value
                        translationX = nudgeCenterX - size.width / 2f
                        // Entra con un empujón hacia abajo: se lee como «no, ahí no».
                        translationY = (1f - nudge.value) * -12f
                    }
                    .background(Color(0xF2141A38), RoundedCornerShape(50))
                    .border(1.5.dp, Color(0xFFFF6B7A).copy(alpha = 0.72f), RoundedCornerShape(50))
                    .padding(horizontal = 16.dp, vertical = 9.dp)
            ) {
                Text(
                    text = misplaceMessage,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.3.sp
                )
            }
        }

        // Progreso persistente bajo la bandeja. Se calcula desde su rectángulo REAL y se limita al
        // borde seguro para que también encaje en teléfonos cortos o con navegación de tres botones.
        if (teachingShotActive) return@BoxWithConstraints
        val progressGapPx = with(density) { 10.dp.toPx() }
        val progressHeightPx = with(density) { 48.dp.toPx() }
        val safeBottomPx = screenHeightPx - with(density) { 12.dp.toPx() }
        // ── LA PÍLDORA SE APARTA DEL TIRADOR (2026-09-01, grabación `analisistutorial`) ───────────
        // Las dos cosas se anclan al mismo sitio —el hueco bajo la bandeja— y en la lección 7 la
        // píldora caía JUSTO encima del botón de habilidades: el tutorial acababa de señalarlo con
        // un foco, el foco se apagaba, y el botón que el jugador tenía que tocar desaparecía debajo
        // del rótulo que le decía en qué lección estaba. Aquí se le pide sitio POR DEBAJO del
        // tirador; si ese sitio no existe (teléfono corto), la píldora se calla — enterarse de la
        // lección importa menos que ver el único botón que la resuelve.
        val handle = positions.abilityHandle.local()
        val handleLive = handle.height >= 1f && spec.teachesAbility
        val desiredTopPx = if (handleLive) maxOf(tray.bottom + progressGapPx, handle.bottom + progressGapPx)
            else tray.bottom + progressGapPx
        val progressTopPx = desiredTopPx
            .coerceAtMost(safeBottomPx - progressHeightPx)
            .coerceAtLeast(tray.bottom)
        // Si al recortar contra el borde seguro la píldora volviera a montarse sobre el tirador, no
        // se dibuja.
        if (handleLive && progressTopPx < handle.bottom) return@BoxWithConstraints
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = with(density) { progressTopPx.toDp() })
                .alpha(entry.value)
                .background(Color(0xFF11172F).copy(alpha = 0.88f), RoundedCornerShape(50))
                .border(1.5.dp, Color(0xFF55E6FF).copy(alpha = 0.58f), RoundedCornerShape(50))
                .padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LECCIÓN $level DE ${TutorialCampaign.LAST_LEVEL}",
                color = Color(0xFF8DEEFF),
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.9.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(TutorialCampaign.LAST_LEVEL) { index ->
                    // Tres estados y no dos: HECHA, la de AHORA y las que faltan. Con `<= level`
                    // todas en morado, la última lección se leía como «ya has terminado» estando
                    // dentro de ella.
                    val current = index + 1 == level
                    Box(
                        Modifier
                            .size(if (current) 12.dp else 8.dp)
                            .background(
                                when {
                                    current -> Color(0xFF8DEEFF)
                                    index + 1 < level -> Color(0xFFA477FF)
                                    else -> Color.White.copy(alpha = 0.22f)
                                },
                                CircleShape
                            )
                    )
                }
            }
        }
    }
}

/** Lo que dice la píldora cuando la ficha se suelta fuera de la casilla marcada. */
const val TUTORIAL_MISPLACE_TEXT = "Aquí no — sigue la mano"

/* (RETIRADO 2026-09-07) `TUTORIAL_NO_ROTATE_TEXT`: la píldora que salía al tocar la ficha guiada
 * para rotarla (P-202). Se fue con el giro — ninguna ficha gira ya en ningún nivel. */
