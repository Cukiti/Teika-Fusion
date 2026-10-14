package com.cukiti.teikafusion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.COMPANION_ABILITIES
import com.cukiti.teikafusion.model.CompanionBelt
import com.cukiti.teikafusion.model.CompanionChargeEconomy
import com.cukiti.teikafusion.model.CompanionCharges
import com.cukiti.teikafusion.model.NamedTeikaRegistry
import com.cukiti.teikafusion.model.abilityRarityFor
import com.cukiti.teikafusion.model.companionAbilityEnabled
import kotlin.math.roundToInt

/**
 * **POWER-UPS ESTILO CANDY CRUSH** (P-282, rediseño del 2026-09-24, decisión del usuario).
 *
 * Dos piezas, las dos sin `Dialog` ni `Popup` (ver `ImmersiveWindowContractTest`):
 *
 *  1. [MatchPowerUpPicker] — en el panel previo al nivel: tres huecos «LLEVA A LA PARTIDA» y una
 *     tira con todos los desbloqueados. Elegir es GRATIS; la carga solo se gasta al lanzar.
 *  2. [PowerUpButtonLayer] — en partida: un botón cuadrado bajo la bandeja, a la derecha, que
 *     despliega los tres elegidos. Primer toque = resalta y dice qué hace; segundo toque = lanza.
 *     Una carga cuesta teikoins: un toque de más no puede gastarla.
 *
 * Los iconos son los emblemas `ic_ability_*` ([abilityIconRes]), que hasta este rediseño solo se
 * veían un segundo al lanzar la habilidad, gigantes sobre el tablero.
 */

private val PowerUpNight = Color(0xFF141A33)
private val PowerUpNightDeep = Color(0xFF0B1024)
private val PowerUpGold = Color(0xFFFFC93C)
private val PowerUpMuted = Color(0xFF8894BB)

/** Nombre de la habilidad de [id], para las etiquetas. */
private fun powerUpName(id: String): String = COMPANION_ABILITIES[id]?.name ?: "Poder"

/**
 * El emblema de un power-up con su cantidad en la esquina. [dimmed] = sin usos (en gris).
 * [ringColor] dibuja el filo (rareza, selección o resaltado); `null` = sin filo.
 */
@Composable
internal fun PowerUpToken(
    id: String,
    count: Int,
    size: Dp,
    modifier: Modifier = Modifier,
    dimmed: Boolean = count <= 0,
    ringColor: Color? = null,
    showCount: Boolean = true,
) {
    val rarityColor = remember(id) { Color(abilityRarityFor(id).colorArgb) }
    Box(modifier.size(size)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(size * 0.26f))
                .background(
                    Brush.verticalGradient(
                        listOf(rarityColor.copy(alpha = if (dimmed) 0.10f else 0.30f), PowerUpNightDeep)
                    )
                )
                .border(
                    width = if (ringColor != null) 2.dp else 1.dp,
                    color = ringColor ?: rarityColor.copy(alpha = if (dimmed) 0.25f else 0.55f),
                    shape = RoundedCornerShape(size * 0.26f)
                ),
            contentAlignment = Alignment.Center
        ) {
            RawImage(
                resId = abilityIconRes(id),
                modifier = Modifier
                    .size(size * 0.80f)
                    .alpha(if (dimmed) 0.35f else 1f),
                contentDescription = null,
                decodeMaxPx = ABILITY_ICON_MAX_PX,
            )
        }
        if (showCount) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size((size.value * 0.38f).coerceIn(15f, 20f).dp)
                    .clip(CircleShape)
                    .background(if (count > 0) PowerUpGold else Color(0xFF3A4260))
                    .border(1.dp, PowerUpNightDeep, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (count > 0) "$count" else "+",
                    color = if (count > 0) Color(0xFF10142A) else Color.White,
                    fontSize = 10.sp, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 1 · El selector del panel del nivel
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Los power-ups que se pueden elegir: los compañeros reclamados con habilidad activa, en el orden del
 * registro (el orden en que se desbloquean). Vacío = el jugador aún no tiene ninguno.
 */
internal fun pickablePowerUps(claimedIds: Set<String>): List<String> =
    NamedTeikaRegistry.all.map { it.id }
        .filter { it in claimedIds && companionAbilityEnabled(it) }

/**
 * «LLEVA A LA PARTIDA» — tres huecos y la tira de desbloqueados.
 *
 *  · Tocar uno de la tira lo mete (o lo saca si ya estaba). Tocar un hueco lleno lo saca.
 *  · Sin usos, no entra: la línea de abajo ofrece comprar uno ahí mismo (y al comprarlo entra).
 *  · Con los tres huecos llenos, la línea de abajo lo dice en vez de sacar a nadie por su cuenta.
 *
 * La línea de abajo tiene ALTO FIJO a propósito: el aviso de compra aparece y desaparece sin
 * empujar el botón de JUGAR, que es lo que el dedo tiene debajo.
 */
@Composable
internal fun MatchPowerUpPicker(
    available: List<String>,
    selected: List<String>,
    charges: CompanionCharges,
    teikoins: Int,
    accent: Color,
    compact: Boolean,
    onToggle: (String) -> Unit,
    onBuy: (String) -> Boolean,
    onUiClick: () -> Unit,
) {
    if (available.isEmpty()) return
    var pendingBuy by remember { mutableStateOf<String?>(null) }
    var fullNotice by remember { mutableStateOf(false) }
    val slotSize = if (compact) 50.dp else 58.dp
    val stripSize = if (compact) 42.dp else 48.dp

    // P-288 (2026-09-25, playtest): lo que LLEVAS y lo que PUEDES ELEGIR se confundían —los dos con
    // el mismo marco dorado, uno encima del otro—. Ahora son dos zonas: los tres huecos viven en su
    // propia bandeja dorada con el contador «2/3» y una ✕ en cada elegido; la tira va debajo, con su
    // rótulo y sin dorado, y marca con ✓ verde (y apagado) lo que ya va en la bandeja.
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(PowerUpGold.copy(alpha = 0.17f), PowerUpNightDeep.copy(alpha = 0.88f))
                    )
                )
                .border(1.5.dp, PowerUpGold.copy(alpha = 0.70f), RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "LLEVAS A LA PARTIDA",
                    color = PowerUpGold,
                    fontSize = if (compact) 11.sp else 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.4.sp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${selected.size}/${CompanionBelt.MAX_SLOTS}",
                    color = PowerUpNightDeep,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(PowerUpGold)
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                )
            }
            Spacer(Modifier.height(if (compact) 6.dp else 8.dp))

            // Los tres huecos.
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                for (i in 0 until CompanionBelt.MAX_SLOTS) {
                    val id = selected.getOrNull(i)
                    if (id != null) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(slotSize * 0.26f))
                                .clickable(role = Role.Button) {
                                    pendingBuy = null; fullNotice = false
                                    onToggle(id)
                                }
                                .semantics { contentDescription = "Quitar ${powerUpName(id)} de la partida" }
                        ) {
                            PowerUpToken(id = id, count = charges.countOf(id), size = slotSize, ringColor = PowerUpGold)
                            // La ✕: tocar el hueco lo saca, y ahora se ve.
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(PowerUpNightDeep.copy(alpha = 0.85f))
                                    .border(1.dp, Color.White.copy(alpha = 0.45f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("✕", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                            }
                        }
                    } else {
                        Box(
                            Modifier
                                .size(slotSize)
                                .clip(RoundedCornerShape(slotSize * 0.26f))
                                .background(Color.White.copy(alpha = 0.04f))
                                .border(1.dp, PowerUpGold.copy(alpha = 0.30f), RoundedCornerShape(slotSize * 0.26f))
                                .semantics { contentDescription = "Hueco libre de power-up" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("+", color = PowerUpGold.copy(alpha = 0.45f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(if (compact) 8.dp else 12.dp))

        // La tira de desbloqueados: lo que PUEDES elegir.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "TUS POWER-UPS",
                color = accent.copy(alpha = 0.95f),
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
            )
            Text(
                " · toca para añadir",
                color = PowerUpMuted,
                fontSize = if (compact) 10.sp else 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        // P-423 (2026-10-01): con más power-ups de los que caben, el borde derecho se FUNDE para que se
        // lea que la fila sigue. Solo mientras queda algo por la derecha; al final, borde limpio.
        val stripState = androidx.compose.foundation.lazy.rememberLazyListState()
        LazyRow(
            state = stripState,
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer(compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen)
                .drawWithContent {
                    drawContent()
                    if (stripState.canScrollForward) drawRect(
                        brush = Brush.horizontalGradient(
                            0.84f to Color.Black, 1f to Color.Transparent,
                            startX = 0f, endX = size.width
                        ),
                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                    )
                },
            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
        ) {
            items(available, key = { it }) { id ->
                val isIn = id in selected
                val count = charges.countOf(id)
                Box {
                PowerUpToken(
                    id = id,
                    count = count,
                    size = stripSize,
                    ringColor = if (id == pendingBuy) Color.White else null,
                    modifier = Modifier
                        .graphicsLayer { alpha = if (isIn) 0.40f else 1f }
                        .clip(RoundedCornerShape(stripSize * 0.26f))
                        .clickable(role = Role.Button) {
                            fullNotice = false
                            when {
                                isIn -> { pendingBuy = null; onToggle(id) }
                                count <= 0 -> { onUiClick(); pendingBuy = if (pendingBuy == id) null else id }
                                selected.size >= CompanionBelt.MAX_SLOTS -> {
                                    onUiClick(); pendingBuy = null; fullNotice = true
                                }
                                else -> { pendingBuy = null; onToggle(id) }
                            }
                        }
                        .semantics {
                            contentDescription = "${powerUpName(id)}, $count usos" +
                                if (isIn) ", en la partida" else ""
                        }
                )
                // ✓ verde: este ya va en la bandeja de arriba.
                if (isIn) {
                    Box(
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(2.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2FBF71))
                            .border(1.dp, PowerUpNightDeep, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("✓", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                    }
                }
                }
            }
        }
        Spacer(Modifier.height(if (compact) 3.dp else 5.dp))

        // La línea de abajo: pista, aviso de huecos llenos u oferta de compra. Alto fijo.
        Box(
            Modifier.fillMaxWidth().height(if (compact) 30.dp else 34.dp),
            contentAlignment = Alignment.Center
        ) {
            val buyId = pendingBuy
            when {
                buyId != null -> PowerUpBuyRow(
                    id = buyId,
                    charges = charges,
                    teikoins = teikoins,
                    onBuy = {
                        if (onBuy(buyId)) {
                            pendingBuy = null
                            if (selected.size < CompanionBelt.MAX_SLOTS && buyId !in selected) onToggle(buyId)
                        }
                    }
                )
                fullNotice -> Text(
                    "Ya llevas tres: toca uno de arriba para quitarlo",
                    color = PowerUpGold.copy(alpha = 0.9f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                else -> Text(
                    "Elegir es gratis · el uso se gasta al lanzarlo",
                    color = PowerUpMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** «Sin usos de X · +1 por N» — la compra de un uso sin salir del panel. */
@Composable
private fun PowerUpBuyRow(
    id: String,
    charges: CompanionCharges,
    teikoins: Int,
    onBuy: () -> Unit,
) {
    val price = CompanionChargeEconomy.priceOf(id)
    val atMax = charges.countOf(id) >= CompanionCharges.MAX_PER_COMPANION
    val canAfford = teikoins >= price && !atMax
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Sin usos de ${powerUpName(id)}",
            color = Color.White.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 170.dp)
        )
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(if (canAfford) PowerUpGold else Color.White.copy(alpha = 0.08f))
                .clickable(enabled = canAfford, role = Role.Button) { onBuy() }
                .padding(horizontal = 10.dp, vertical = 5.dp)
                .semantics { contentDescription = "Comprar un uso de ${powerUpName(id)} por $price teikoins" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "+1",
                color = if (canAfford) Color(0xFF10142A) else PowerUpMuted,
                fontSize = 12.sp, fontWeight = FontWeight.Black
            )
            TeikoinAmount(
                amount = price,
                color = if (canAfford) Color(0xFF10142A) else PowerUpMuted,
                fontSize = 11.sp, iconSize = 12.dp, gap = 2.dp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2 · El botón de partida
// ─────────────────────────────────────────────────────────────────────────────

/** Lado del botón cuadrado de power-ups. */
private val POWER_BUTTON_SIZE = 46.dp

/** P-290: cuánto más abajo que antes va el conjunto botón + fila (lejos de la bandeja). */
private val POWER_ROW_DROP = 4.dp

/** Alto de la fila abierta (fichas de 44 dp + 6 dp de margen arriba y abajo). */
private val POWER_ROW_HEIGHT = 56.dp

/**
 * Lo que se descuenta del margen inferior: la caja mide [POWER_ROW_HEIGHT] y el botón va centrado en
 * ella (media diferencia por debajo), más [POWER_ROW_DROP] para bajar el conjunto.
 */
private val POWER_ROW_DROP_ROOM = (POWER_ROW_HEIGHT - POWER_BUTTON_SIZE) / 2 + POWER_ROW_DROP

/**
 * El botón de POWER-UPS de la partida y lo que despliega.
 *
 * Se ancla bajo la bandeja, a la derecha ([bottomPx] = distancia al fondo de la pantalla). Tocarlo
 * despliega hacia la izquierda los power-ups de la partida ([ids], ya en el orden de los huecos).
 * Tocar uno lo RESALTA y dice qué hace encima de la fila; tocar el mismo otra vez lo lanza. Tocar
 * fuera lo recoge. Sin usos, la etiqueta ofrece comprar uno ahí mismo, como hacía la hoja.
 *
 * [onExpanded] publica 0/1 animado: `GameScreen` lo usa para desenfocar un poco el tablero, retirar
 * el consejo de atasco y la píldora del tutorial — lo que antes hacía el recorrido de la hoja.
 */
@Composable
fun BoxScope.PowerUpButtonLayer(
    ids: List<String>,
    charges: CompanionCharges,
    canCast: (String) -> Boolean,
    teikoins: Int,
    bottomPx: Float,
    enabled: Boolean,
    onCast: (String) -> Unit,
    onBuy: (String) -> Unit,
    onUiClick: () -> Unit,
    onExpanded: (Float) -> Unit,
    onButtonPositioned: (androidx.compose.ui.geometry.Rect) -> Unit = {},
    freeCompanionId: String? = null,
) {
    if (ids.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    var armed by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(enabled) { if (!enabled) { expanded = false; armed = null } }
    val open by animateFloatAsState(if (expanded) 1f else 0f, tween(180), label = "powerUpOpen")
    LaunchedEffect(open) { onExpanded(open) }
    val density = LocalDensity.current
    val bottomDp = with(density) { bottomPx.toDp() }

    // Tocar fuera recoge. Solo existe con la fila abierta: cerrada no roba ni un toque al tablero.
    if (expanded) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { expanded = false; armed = null }
        )
    }

    Column(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            // La caja de abajo es más alta que el botón (sitio para la fila, que baja de su borde
            // superior): se descuenta para que el botón quede donde siempre.
            .padding(end = 14.dp, bottom = (bottomDp - POWER_ROW_DROP_ROOM).coerceAtLeast(0.dp))
            .alpha(if (enabled) 1f else 0.35f),
        horizontalAlignment = Alignment.End
    ) {
        // La etiqueta del resaltado: nombre y qué hace, o por qué no se puede.
        val armedId = armed
        AnimatedVisibility(visible = expanded && armedId != null, enter = fadeIn(tween(120)), exit = fadeOut(tween(90))) {
            if (armedId != null) {
                Column(horizontalAlignment=Alignment.End) {
                if(armedId==freeCompanionId) Text("USO GRATIS · ESTA PARTIDA",color=Color(0xFF9BE8E2),fontSize=11.sp)
                PowerUpArmedLabel(
                    id = armedId,
                    count = charges.countOf(armedId),
                    castable = canCast(armedId),
                    teikoins = teikoins,
                    onBuy = { onBuy(armedId) }
                )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // P-290 (2026-09-25, playtest): antes era un `Row` centrado; la fila abierta (56 dp) es más
        // alta que el botón (46 dp), así que al abrir la fila crecía y el botón SUBÍA 5 dp. Ahora la
        // caja tiene alto FIJO (el de la fila): el botón no se mueve al abrir. Y los dos van
        // CENTRADOS en ella (captura `botonhabilidades`: alineada por arriba, la fila quedaba 10 dp
        // más baja que el botón); el conjunto baja [POWER_ROW_DROP] para no acercarse a la bandeja.
        Box(Modifier.height(POWER_ROW_HEIGHT)) {
            // La versión sin ámbito: dentro de la `Column` de fuera se resolvería la de `ColumnScope`.
            androidx.compose.animation.AnimatedVisibility(
                visible = expanded,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = POWER_BUTTON_SIZE),
                enter = fadeIn(tween(140)) + expandHorizontally(tween(180), expandFrom = Alignment.End),
                exit = fadeOut(tween(100)) + shrinkHorizontally(tween(140), shrinkTowards = Alignment.End)
            ) {
                Row(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PowerUpNight.copy(alpha = 0.94f))
                        .border(1.dp, PowerUpGold.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ids.forEach { id ->
                        val count = charges.countOf(id)
                        val isArmed = id == armed
                        PowerUpToken(
                            id = id,
                            count = count,
                            size = 44.dp,
                            dimmed = count <= 0 || !canCast(id),
                            ringColor = if (isArmed) PowerUpGold else null,
                            modifier = Modifier
                                .graphicsLayer {
                                    val s = if (isArmed) 1.08f else 1f
                                    scaleX = s; scaleY = s
                                }
                                .clip(RoundedCornerShape(11.dp))
                                .clickable(role = Role.Button) {
                                    if (armed == id && count > 0 && canCast(id)) {
                                        onCast(id)
                                        armed = null
                                        expanded = false
                                    } else {
                                        onUiClick()
                                        armed = id
                                    }
                                }
                                .semantics {
                                    contentDescription = if (isArmed) "Lanzar ${powerUpName(id)}"
                                    else "${powerUpName(id)}, $count usos. Toca para elegir"
                                }
                        )
                    }
                }
            }
            PowerUpButton(
                total = ids.sumOf { charges.countOf(it) },
                open = expanded,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .onGloballyPositioned { onButtonPositioned(it.boundsInWindow()) }
                    .clickable(enabled = enabled, role = Role.Button) {
                        onUiClick()
                        expanded = !expanded
                        armed = null
                    }
                    .semantics { contentDescription = if (expanded) "Cerrar power-ups" else "Abrir power-ups" }
            )
        }
    }
}

/** El botón cuadrado: un rayo dibujado y el total de usos de la partida. */
@Composable
private fun PowerUpButton(total: Int, open: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(POWER_BUTTON_SIZE)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        if (open) listOf(PowerUpGold, Color(0xFFE39A1E))
                        else listOf(Color(0xFF2A3466), PowerUpNight)
                    )
                )
                .border(1.5.dp, PowerUpGold.copy(alpha = if (open) 1f else 0.7f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            val boltColor = if (open) Color(0xFF10142A) else PowerUpGold
            Canvas(Modifier.size(24.dp)) {
                val w = size.width; val h = size.height
                val bolt = Path().apply {
                    moveTo(w * 0.60f, 0f)
                    lineTo(w * 0.14f, h * 0.56f)
                    lineTo(w * 0.46f, h * 0.56f)
                    lineTo(w * 0.36f, h)
                    lineTo(w * 0.86f, h * 0.40f)
                    lineTo(w * 0.54f, h * 0.40f)
                    close()
                }
                drawPath(bolt, boltColor)
            }
        }
        if (!open && total > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset((5 * density).roundToInt(), (-5 * density).roundToInt()) }
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE5484D))
                    .border(1.dp, PowerUpNightDeep, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("$total", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/** Encima de la fila: qué hace el resaltado y el gesto que falta, o por qué no se puede. */
@Composable
private fun PowerUpArmedLabel(
    id: String,
    count: Int,
    castable: Boolean,
    teikoins: Int,
    onBuy: () -> Unit,
) {
    val price = CompanionChargeEconomy.priceOf(id)
    val atMax = count >= CompanionCharges.MAX_PER_COMPANION
    Column(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PowerUpNight.copy(alpha = 0.96f))
            .border(1.dp, PowerUpGold.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
            .padding(horizontal = 11.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.End
    ) {
        Text(
            powerUpName(id),
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        when {
            count <= 0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Sin usos", color = PowerUpMuted, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(8.dp))
                val canAfford = teikoins >= price && !atMax
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (canAfford) PowerUpGold else Color.White.copy(alpha = 0.08f))
                        .clickable(enabled = canAfford, role = Role.Button) { onBuy() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                        .semantics { contentDescription = "Comprar un uso de ${powerUpName(id)} por $price teikoins" },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("+1", color = if (canAfford) Color(0xFF10142A) else PowerUpMuted, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    TeikoinAmount(
                        amount = price,
                        color = if (canAfford) Color(0xFF10142A) else PowerUpMuted,
                        fontSize = 10.sp, iconSize = 11.dp, gap = 2.dp,
                    )
                }
            }
            !castable -> Text("Ahora mismo no hay dónde aplicarlo", color = PowerUpMuted, fontSize = 11.sp)
            else -> Text("Toca otra vez para lanzarlo", color = PowerUpGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 3 · El rótulo del ECO
// ─────────────────────────────────────────────────────────────────────────────

/**
 * «¡ECO! · no gastó el uso» — sube desde el botón de power-ups y se desvanece (1,1 s). Es el único
 * consumidor de [com.cukiti.teikafusion.model.GameEvent.AbilityEcho]: llama a [onDone] al terminar.
 *
 * [anchorBottomEnd] = esquina inferior derecha del botón en coordenadas de ESTA capa (px). Si aún no
 * se ha medido (cero), el rótulo sale sobre la esquina inferior derecha de la capa.
 */
@Composable
fun BoxScope.AbilityEchoLabel(
    eventId: String,
    companionId: String,
    anchorBottomEnd: Offset,
    onDone: () -> Unit,
    /** Solo para capturas: congela la animación en ese punto (0..1) y no llama a [onDone]. */
    frozenAt: Float? = null,
) {
    val t = remember(eventId) { androidx.compose.animation.core.Animatable(frozenAt ?: 0f) }
    LaunchedEffect(eventId) {
        if (frozenAt != null) return@LaunchedEffect
        t.animateTo(1f, tween(1100, easing = androidx.compose.animation.core.LinearEasing))
        onDone()
    }
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .graphicsLayer {
                val p = t.value
                val rise = 36.dp.toPx() * androidx.compose.animation.core.FastOutSlowInEasing.transform(p.coerceAtMost(0.6f) / 0.6f)
                val w = size.width
                val h = size.height
                val base = if (anchorBottomEnd == Offset.Zero) Offset(Float.NaN, Float.NaN) else anchorBottomEnd
                translationX = if (base.x.isNaN()) 0f else base.x - w
                translationY = if (base.y.isNaN()) 0f else base.y - h - 58.dp.toPx() - rise
                alpha = when {
                    p < 0.12f -> p / 0.12f
                    p > 0.75f -> 1f - (p - 0.75f) / 0.25f
                    else -> 1f
                }
                val s = 0.85f + 0.15f * (p / 0.18f).coerceAtMost(1f)
                scaleX = s; scaleY = s
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.verticalGradient(listOf(PowerUpGold, Color(0xFFE39A1E))))
            .border(1.5.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
            .semantics { contentDescription = "Eco: ${powerUpName(companionId)} no gastó el uso" },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("¡ECO!", color = Color(0xFF10142A), fontSize = 15.sp, fontWeight = FontWeight.Black)
        Text("no gastó el uso", color = Color(0xFF10142A).copy(alpha = 0.8f), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

// (La demo del minitablero —P-269— se mudó a `AbilityDemoBoard.kt` el 2026-09-30, rehecha como
//  mini-partida guionizada: ver `model/AbilityDemo.kt`.)
