package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import com.cukiti.teikafusion.model.GameState

/** Morado de la ficha FANTASMA: los bloques que va a desintegrar al posarse. */

@Composable
fun GhostPreviewLayer(
    dragState: DragState,
    cellSizePx: Float,
    boardCols: Int = GameState.GRID_W_LEVELS,
    // FILAS del tablero: 8 en niveles, 18 en el pozo del Infinito (2026-07-26). Sin esto el ghost
    // se recortaba a partir de la fila 8 y la sombra de aterrizaje desaparecía justo en el fondo
    // del pozo, que es donde más falta hace.
    gridRows: Int = GameState.GRID_H_LEVELS,
    /**
     * INFINITO: desplazamiento sub-celda con el que la sombra CABALGA la cara del muro. Los bloques
     * apoyados en la pared se dibujan con ese mismo desfase, así que la ficha que va a posarse sobre
     * ellos tiene que subir en tiempo real con ella o se ve hundida en el ladrillo. Es una lambda a
     * propósito: se lee en fase de DIBUJO (la fracción cambia cada frame) y no recompone nada.
     */
    rideOffsetPx: () -> Float = { 0f },
    modifier: Modifier = Modifier
) {
    // hoverProgress debe estar fuera del guard para que remember sea siempre llamado.
    val hoverProgress = remember { Animatable(0f) }
    val draggingState = dragState as? DragState.Dragging
    LaunchedEffect(draggingState?.ghostGridPosition, draggingState?.isValidPlacement) {
        hoverProgress.snapTo(0f)
        if (draggingState != null && draggingState.isValidPlacement) {
            hoverProgress.animateTo(1f, tween(800, easing = LinearEasing))
        }
    }

    // Guard temprano — DESPUÉS de todos los hooks remember/LaunchedEffect.
    // Colocación INVÁLIDA → SIN sombra (estilo Block Blast: el ghost solo aparece cuando la
    // ficha de verdad cabe).
    if (draggingState == null || cellSizePx <= 0f || draggingState.ghostGridPosition == null ||
        !draggingState.isValidPlacement
    ) {
        // …pero SÍ se dice por qué (P-16): se marcan las celdas concretas que chocan. Sin esto la
        // sombra desaparecía en silencio y el jugador no sabía si le estorbaba un bloque, el muro,
        // un cuerpo de Teika o la otra mitad del corte ninja.
        val conflicts = draggingState?.blockedCells.orEmpty()
        if (conflicts.isNotEmpty() && cellSizePx > 0f) {
            ConflictCellsLayer(
                cells = conflicts,
                cellSizePx = cellSizePx,
                boardCols = boardCols,
                gridRows = gridRows,
                modifier = modifier
            )
        } else {
            Spacer(modifier = modifier.fillMaxSize())
        }
        return
    }

    // La InfiniteTransition solo existe cuando hay drag activo (composable solo se monta aquí)
    GhostActiveLayer(
        dragState = draggingState,
        cellSizePx = cellSizePx,
        hoverProgress = hoverProgress,
        boardCols = boardCols,
        gridRows = gridRows,
        modifier = modifier.graphicsLayer { translationY = rideOffsetPx() }
    )
}

/**
 * **P-16 — por qué no cabe.** Marca en rojo APAGADO solo las celdas que chocan con la ficha, sin
 * dibujar su silueta: el ghost rojo completo está descartado (decisión congelada, estilo Block
 * Blast). Es un aro fino sobre la celda culpable, nada más — informa sin gritar, y desaparece en
 * cuanto el jugador mueve el dedo a un sitio donde la ficha entra.
 */
@Composable
private fun ConflictCellsLayer(
    cells: List<com.cukiti.teikafusion.model.Position>,
    cellSizePx: Float,
    boardCols: Int,
    gridRows: Int,
    modifier: Modifier
) {
    val tint = Color(0xFFFF6B7A)
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val corner = CornerRadius(6.dp.toPx())
                val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = cellSizePx * 0.07f)
                onDrawBehind {
                    cells.forEach { p ->
                        if (p.x in 0 until boardCols && p.y in 0 until gridRows) {
                            val gx = p.x * cellSizePx
                            val gy = p.y * cellSizePx
                            drawRoundRect(
                                color = tint.copy(alpha = 0.16f),
                                topLeft = Offset(gx, gy),
                                size = Size(cellSizePx, cellSizePx),
                                cornerRadius = corner
                            )
                            drawRoundRect(
                                color = tint.copy(alpha = 0.75f),
                                topLeft = Offset(gx, gy),
                                size = Size(cellSizePx, cellSizePx),
                                cornerRadius = corner,
                                style = stroke
                            )
                        }
                    }
                }
            }
    )
}

// (PODADO 2026-07-31, bloque 5) Aquí vivía `GhostStompLayer`, la capa que marcaba los bloques que
// la ficha FANTASMA se comía. La ficha murió con los pactos del Infinito y su único productor
// rellenaba las celdas con `emptyList()`: efecto completo, cero jugadores capaces de verlo.

@Composable
private fun GhostActiveLayer(
    dragState: DragState.Dragging,
    cellSizePx: Float,
    hoverProgress: Animatable<Float, AnimationVector1D>,
    boardCols: Int,
    gridRows: Int,
    modifier: Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue  = 0.55f,
        animationSpec = infiniteRepeatable(
            animation  = tween(500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val gridPos = dragState.ghostGridPosition ?: return
    val piece   = dragState.piece
    val isValid = dragState.isValidPlacement

    val skin       = LocalBlockSkin.current
    val pieceColor = skin.colorsFor(piece.colorType)[0]
    val warmColor  = Color(0xFFFF6B00)
    val coldFill   = if (isValid) pieceColor.copy(alpha = 0.35f) else Color(0xFFE53935).copy(alpha = 0.35f)
    val coldGlow   = if (isValid) Color.White.copy(alpha = 0.7f) else Color.Red.copy(alpha = 0.8f)

    // El HALO de cada casilla es el mismo trazo desenfocado (BlurMaskFilter OUTER) de siempre, pero
    // rasterizado UNA vez en una máscara que luego se pinta teñida (auditoría de fluidez 2026-09-29).
    // Antes el desenfoque se recalculaba por casilla y por fotograma —el pulso redibuja la capa sin
    // parar— y, como el calentamiento se leía en composición, cada fotograma de sus 800 ms recomponía
    // la capa y creaba además un Paint nativo nuevo.
    val density = LocalDensity.current
    val glow = remember(cellSizePx, density) { buildGhostGlow(cellSizePx, density) }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val cornerRadius = CornerRadius(6.dp.toPx())

                onDrawBehind {
                    // (PODADO 2026-07-31) Aquí se marcaban los bloques que la FICHA FANTASMA iba a
                    // desintegrar. La ficha se fue con los pactos del Infinito.

                    // Deseo Estelar: sombra de la expansión LÍQUIDA — pinta todas las celdas que la
                    // estrella llenaría (charco iridiscente translúcido) en vez del footprint de 1 celda.
                    val starCells = dragState.starFillCells
                    if (starCells.isNotEmpty()) {
                        val starTint = glow.tint(Color(0xFF7CF5FF))
                        starCells.forEach { p ->
                            if (p.x in 0 until boardCols && p.y in 0 until gridRows) {
                                val cellX = p.x * cellSizePx
                                val cellY = p.y * cellSizePx
                                val offset = Offset(cellX, cellY)
                                val sz = Size(cellSizePx, cellSizePx)
                                drawRoundRect(color = Color(0xFF7CF5FF).copy(alpha = 0.20f + pulseAlpha * 0.20f), topLeft = offset, size = sz, cornerRadius = cornerRadius)
                                drawRoundRect(color = Color(0xFFB388FF).copy(alpha = 0.14f), topLeft = offset, size = sz, cornerRadius = cornerRadius)
                                drawGhostGlow(glow, cellX, cellY, starTint)
                            }
                        }
                        return@onDrawBehind
                    }

                    // El calentamiento se lee AQUÍ, en dibujo: anima sin recomponer nada.
                    val hp = hoverProgress.value
                    val ghostColor = if (isValid) lerp(coldFill, warmColor.copy(alpha = 0.55f), hp) else coldFill
                    val glowColor  = if (isValid) lerp(coldGlow, warmColor.copy(alpha = (0.9f + hp * 0.1f).coerceAtMost(1f)), hp) else coldGlow
                    val glowTint = glow.tint(glowColor)

                    // P-80 · NIVEL CON GRAVEDAD: la sombra se pinta donde los bloques van a
                    // ATERRIZAR, no bajo el dedo. Y no es la silueta desplazada en bloque: cada
                    // bloque cae por su columna, así que la ficha puede llegar abajo desarmada —
                    // verlo antes de soltar es justo lo que hace jugable el nivel.
                    val cells = dragState.landingCells.ifEmpty {
                        piece.shape.map {
                            com.cukiti.teikafusion.model.Position(gridPos.x + it.x, gridPos.y + it.y)
                        }
                    }
                    // ── P-147 · EL REFLEJO, ANTES DE SOLTAR (2026-09-04) ────────────────────────
                    // La mitad de la jugada ocurre al otro lado del tablero. Sin verlo antes, la
                    // regla es una trampa: el jugador descubre lo que ha hecho cuando ya no puede
                    // deshacerlo. Se dibuja **más tenue que la sombra principal** a propósito —es
                    // consecuencia, no la ficha— y solo cuando la colocación es válida.
                    if (isValid) {
                        dragState.mirrorCells.forEach { m ->
                            if (m.x in 0 until boardCols && m.y in 0 until gridRows) {
                                drawSkinnedBlock(
                                    skin, piece.colorType,
                                    Offset(m.x * cellSizePx, m.y * cellSizePx),
                                    Size(cellSizePx, cellSizePx),
                                    alpha = 0.16f + pulseAlpha * 0.10f,
                                )
                            }
                        }
                    }
                    cells.forEach { landed ->
                        val px = landed.x
                        val py = landed.y
                        if (px in 0 until boardCols && py in 0 until gridRows) {
                            val cellX  = px * cellSizePx
                            val cellY  = py * cellSizePx
                            val offset = Offset(cellX, cellY)
                            val sz     = Size(cellSizePx, cellSizePx)
                            // P24i: el ghost dibuja el MISMO bloque biselado/jelly del skin activo
                            // (translúcido) en vez de un rectángulo plano — coherencia con la celda.
                            if (isValid) {
                                drawSkinnedBlock(skin, piece.colorType, offset, sz, alpha = 0.30f + pulseAlpha * 0.18f)
                            } else {
                                drawRoundRect(color = ghostColor, topLeft = offset, size = sz, cornerRadius = cornerRadius)
                            }
                            if (isValid) drawGhostGlow(glow, cellX, cellY, glowTint)
                        }
                    }
                }
            }
    )
}

/**
 * Halo de la sombra ya desenfocado: el trazo de 2 dp con desenfoque OUTER de 12 dp y esquinas de
 * 6 dp, en blanco, con [pad] de margen para que el desenfoque quepa entero. Se pinta teñido del color
 * del halo ([tint]), que es exactamente lo que hacía el `Paint` coloreado sobre el trazo.
 */
private class GhostGlow(val mask: ImageBitmap?, val pad: Float) {
    private var lastColor = Color.Unspecified
    private var lastFilter: ColorFilter? = null

    /** El filtro del color pedido; se reutiliza mientras el color no cambie. */
    fun tint(color: Color): ColorFilter {
        val cached = lastFilter
        if (cached != null && color == lastColor) return cached
        return ColorFilter.tint(color, BlendMode.SrcIn).also { lastFilter = it; lastColor = color }
    }
}

private fun buildGhostGlow(cellSizePx: Float, density: Density): GhostGlow {
    if (cellSizePx <= 0f) return GhostGlow(null, 0f)
    val blur = with(density) { 12.dp.toPx() }
    val strokeW = with(density) { 2.dp.toPx() }
    val corner = with(density) { 6.dp.toPx() }
    // El desenfoque OUTER llega a ~1,75 × radio: con 2 × radio + trazo no se corta nunca.
    val pad = ceil(blur * 2f + strokeW)
    val side = ceil(cellSizePx + pad * 2f).toInt()
    val bitmap = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
    val paint = android.graphics.Paint().apply {
        style       = android.graphics.Paint.Style.STROKE
        strokeWidth = strokeW
        isAntiAlias = true
        color       = android.graphics.Color.WHITE
        maskFilter  = android.graphics.BlurMaskFilter(blur, android.graphics.BlurMaskFilter.Blur.OUTER)
    }
    android.graphics.Canvas(bitmap).drawRoundRect(pad, pad, pad + cellSizePx, pad + cellSizePx, corner, corner, paint)
    return GhostGlow(bitmap.asImageBitmap(), pad)
}

private fun DrawScope.drawGhostGlow(glow: GhostGlow, cellX: Float, cellY: Float, tint: ColorFilter) {
    val mask = glow.mask ?: return
    drawImage(mask, topLeft = Offset(cellX - glow.pad, cellY - glow.pad), colorFilter = tint)
}
