package com.cukiti.teikafusion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.cukiti.teikafusion.model.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun AutoBlockBankButton(bank: BlockBank, placements: Int, enabled: Boolean,
    onClick: () -> Unit, onPositioned: (Rect) -> Unit, modifier: Modifier = Modifier,
) {
    val locked = BlockBankRules.turnsLocked(bank, placements)
    Box(modifier.size(BANK_BUTTON_SIZE).onGloballyPositioned { onPositioned(it.boundsInWindow()) }
        .clip(RoundedCornerShape(12.dp)).background(Color(0xFF141A33))
        .border(1.5.dp, BankAccent.copy(alpha = if (enabled) 0.9f else 0.4f), RoundedCornerShape(12.dp))
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = if (locked > 0) "Fichona: vuelve en $locked turnos" else "Fichona. Reciclar fichas y crear una mega ficha" },
        contentAlignment = Alignment.Center,
    ) {
        if (locked > 0) Text("$locked", color = Color.White, fontWeight = FontWeight.Black)
        else Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val c = size.width / 3.4f
            repeat(3) { i ->
                drawRoundRect(BankAccent.copy(alpha = if (enabled) 0.8f else 0.3f),
                    Offset(i * c * 1.2f, (2 - i) * c * 0.65f), Size(c, c), CornerRadius(c * 0.2f))
            }
        }
    }
}

/** Construction uses a single clock, read by graphics layers, with no per-block coroutines. */
@Composable
fun AutoBlockWorkshop(
    state: GameState,
    reducedEffects: Boolean,
    loadOffer: suspend () -> WorkshopOffer,
    onBuild: suspend (WorkshopOffer) -> WorkshopOffer,
    onCommitted: (List<Piece>) -> Unit,
    onDelivered: (List<Piece>, Map<String, Rect>) -> Unit,
    onDismiss: () -> Unit,
) {
    var offer by remember { mutableStateOf<WorkshopOffer?>(null) }
    var working by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val clock = remember { Animatable(0f) }
    val bounds = remember { mutableMapOf<String, Rect>() }
    BackHandler { if (!working) onDismiss() }
    LaunchedEffect(state) { if (!working && !committed) offer = loadOffer() }
    val plan = offer?.plan
    BoxWithConstraints(Modifier.fillMaxSize().zIndex(GameLayers.WINDOW).background(Color.Black.copy(alpha = 0.76f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { if (!working) onDismiss() },
        contentAlignment = Alignment.Center) {
        val panelMax = (maxHeight - 24.dp).coerceAtLeast(100.dp)
        Column(Modifier.fillMaxWidth(0.9f).heightIn(max = panelMax)
            .then(if (committed) Modifier else Modifier.clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF231A3D), Color(0xFF120D24))))
                .border(2.dp, BankAccent.copy(alpha = 0.65f), RoundedCornerShape(24.dp)))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .verticalScroll(rememberScrollState()).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("FICHONA", color = BankAccent, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
            if (working && !committed) {
                Spacer(Modifier.height(8.dp))
                Text(if (committed) "Construyendo…" else "Preparando…",
                    color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(14.dp))
            if (!committed) Text(if (plan != null) "${plan.available} bloques" + if (plan.remainder > 0) " · sobran ${plan.remainder}" else ""
                else "${WorkshopPlanner.materialBudget(state.blockBank, state.availablePieces)} bloques",
                color = BankAccent, fontSize = 12.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(if (committed) 340.dp else 235.dp)
                .then(if (committed) Modifier else Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF0B1024))),
                contentAlignment = Alignment.Center) {
                val piece = plan?.pieces?.singleOrNull()
                if (piece == null) Text("✦", color = BankAccent.copy(alpha = 0.4f), fontSize = 48.sp)
                else WorkshopAssembly(piece, committed, reducedEffects, clock, bounds)
            }
            val notice = failure ?: offer?.refusal ?: if (offer == null) "Buscando fichas…" else null
            if (notice != null) {
                Spacer(Modifier.height(12.dp))
                Text(notice, color = Color(0xFFFFC857), fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(14.dp))
            if (!committed) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ArcadeButton("CANCELAR", ArcadeTone.NEUTRAL, enabled = !working,
                    onClick = onDismiss, modifier = Modifier.weight(1f), fontSize = 13.sp, height = 46.dp)
                ArcadeButton(if (working) "CREANDO…" else "CREAR", ArcadeTone.GREEN,
                    enabled = plan != null && !working,
                    onClick = {
                        val selected = offer ?: return@ArcadeButton
                        working = true
                        failure = null
                        scope.launch {
                            try {
                                val result = onBuild(selected)
                                offer = result
                                val built = result.plan
                                if (built == null) { failure = result.refusal; return@launch }
                                committed = true
                                onCommitted(built.pieces)
                                withFrameNanos { }
                                clock.animateTo(1f, tween(if (reducedEffects) 240 else 1900, easing = FastOutSlowInEasing))
                                onDelivered(built.pieces, bounds.toMap())
                            } finally { working = false }
                        }
                    }, modifier = Modifier.weight(1f), fontSize = 12.sp, height = 46.dp)
            }
            if (!committed) {
                Spacer(Modifier.height(10.dp))
                Text("1 turno · descanso ${BlockBankRules.COOLDOWN_TURNS} turnos",
                    color = BankAccent, fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(6.dp))
                Text("Fuera de un encaje, se rompe.",
                    color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun WorkshopAssembly(piece: Piece, committed: Boolean, reducedEffects: Boolean, clock: Animatable<Float, *>, bounds: MutableMap<String, Rect>) {
    val cell = minOf(285 / piece.width, 215 / piece.height, 46).dp
    Box(Modifier.size(cell * piece.width, cell * piece.height)
        .onGloballyPositioned { bounds[piece.id] = it.boundsInWindow() }
        .drawBehind {
            if (committed && !reducedEffects) {
                val phase = ((clock.value - 0.72f) / 0.28f).coerceIn(0f, 1f)
                val glow = kotlin.math.sin(phase * kotlin.math.PI).toFloat()
                val center = this.center
                repeat(12) { ray ->
                    val angle = ray * kotlin.math.PI * 2 / 12 + phase * 0.15
                    val direction = Offset(kotlin.math.cos(angle).toFloat(), kotlin.math.sin(angle).toFloat())
                    drawLine(Color(0xFFFFD16A).copy(alpha = glow * 0.55f), center + direction * size.maxDimension * 0.42f,
                        center + direction * size.maxDimension * (0.6f + phase * 0.25f), 5.dp.toPx())
                }
                val radius = size.maxDimension * (0.6f + phase * 0.25f)
                drawCircle(Brush.radialGradient(listOf(
                    BankAccent.copy(alpha = glow * 0.30f), Color(0xFFB57BFF).copy(alpha = glow * 0.20f),
                    Color(0xFFFFD16A).copy(alpha = glow * 0.10f), Color.Transparent), center, radius), radius)
            }
        }.graphicsLayer {
            val phase = ((clock.value - 0.72f) / 0.28f).coerceIn(0f, 1f)
            val reveal = if (committed && !reducedEffects) kotlin.math.sin(phase * kotlin.math.PI).toFloat() else 0f
            scaleX = 1f + reveal * 0.055f; scaleY = scaleX
        }) {
        piece.shape.forEachIndexed { index, position ->
            val block = remember(piece, index) { piece.copy(shape = listOf(Position(0, 0)),
                targetMask = listOf(piece.targetMask.getOrNull(index)),
                crystalMask = listOf(piece.crystalMask.getOrNull(index) == true),
                barrelMask = listOf(piece.barrelMask.getOrNull(index) == true),
                teikaMask = listOf(piece.teikaMask.getOrNull(index) == true)) }
            PieceView(block, isDragging = false, isQueuePiece = false, cellSize = cell,
                modifier = Modifier.offset(cell * position.x, cell * position.y).graphicsLayer {
                    val p = if (!committed) 1f else ((clock.value - index * 0.018f) / 0.44f).coerceIn(0f, 1f)
                    alpha = if (committed) p else 0.8f
                    scaleX = 0.5f + p * 0.5f; scaleY = scaleX
                    translationY = if (committed && !reducedEffects) (1f - p) * (if (index % 3 == 0) -180.dp.toPx() else 120.dp.toPx()) else 0f
                    rotationZ = if (committed && !reducedEffects) (1f - p) * (if (index % 2 == 0) -45f else 45f) else 0f
                    translationX = if (committed && !reducedEffects) (1f - p) * (if (index % 2 == 0) -240.dp.toPx() else 240.dp.toPx()) else 0f
                })
        }
    }
}

data class WorkshopDelivery(val pieces: List<Piece>, val starts: Map<String, Rect>)

@Composable
fun WorkshopDeliveryVFX(delivery: WorkshopDelivery, targets: Map<String, Offset>, cells: Map<String, Float>,
    rootOffset: Offset, reducedEffects: Boolean, onDone: () -> Unit,
) {
    val clock = remember(delivery) { Animatable(0f) }
    val density = LocalDensity.current
    val endpoints = remember(delivery) { mutableMapOf<String, Offset>() }
    val endCells = remember(delivery) { mutableMapOf<String, Float>() }
    LaunchedEffect(delivery) {
        // The new tray must measure once before flight; coordinates are plain maps, never frame state.
        repeat(3) { withFrameNanos { } }
        endpoints.putAll(targets); endCells.putAll(cells)
        clock.animateTo(1f, tween(if (reducedEffects) 140 else 320, easing = FastOutSlowInEasing))
        onDone()
    }
    delivery.pieces.forEach { piece ->
        val start = delivery.starts[piece.id] ?: return@forEach
        val cellDp = with(density) { (start.width / piece.width).toDp() }
        PieceView(piece, isDragging = false, isQueuePiece = false, cellSize = cellDp,
            modifier = Modifier.zIndex(GameLayers.WINDOW).offset {
                val end = endpoints[piece.id] ?: start.topLeft
                val p = if (reducedEffects) 1f else clock.value
                val at = start.topLeft + (end - start.topLeft) * p - rootOffset
                IntOffset(at.x.roundToInt(), at.y.roundToInt())
            }.graphicsLayer {
                transformOrigin = TransformOrigin(0f, 0f)
                val ratio = (endCells[piece.id] ?: (start.width / piece.width)) / (start.width / piece.width)
                scaleX = if (reducedEffects) ratio else 1f + (ratio - 1f) * clock.value; scaleY = scaleX
                if (reducedEffects) alpha = clock.value
            })
    }
}
