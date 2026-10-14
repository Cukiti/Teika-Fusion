package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.COMPANION_ABILITIES
import com.cukiti.teikafusion.model.COMPANION_ABILITY_MAX_LEVEL
import com.cukiti.teikafusion.model.CompanionAbilityUpgrades
import com.cukiti.teikafusion.model.CompanionCharges
import com.cukiti.teikafusion.model.CrystalInventory
import com.cukiti.teikafusion.model.LevelMap
import com.cukiti.teikafusion.model.LivesRules
import com.cukiti.teikafusion.model.NamedTeika
import com.cukiti.teikafusion.model.NamedTeikaRegistry
import com.cukiti.teikafusion.model.abilityRarityFor
import com.cukiti.teikafusion.model.companionAbilityEnabled
import com.cukiti.teikafusion.ui.theme.AppBackground
import kotlinx.coroutines.delay

/** Alto del rótulo ilustrado de la cabecera (arte del usuario, 2026-09-02). */
private val INVENTORY_TITLE_HEIGHT = 38.dp

private val InvPanel = Color(0xFF151C3B)
private val InvTile = Color(0xFF1F2850)
private val InvTileDeep = Color(0xFF121833)
private val InvGold = Color(0xFFFFC93C)

/**
 * **EL INVENTARIO, ESTILO CANDY CRUSH** — rediseño del 2026-09-24 (P-282, decisión del usuario;
 * referencia `art/inventariocandy.jpg`).
 *
 * Hasta hoy esta pestaña era la Sala de Preparación: un cinturón de tres ranuras que se llenaba
 * arrastrando, una banca y la cinta de capitán. La elección de qué te llevas a la partida se mudó al
 * panel del nivel («LLEVA A LA PARTIDA», ver [MatchPowerUpPicker]), y aquí queda lo que el jugador
 * TIENE, de un vistazo, en casillas con icono y cantidad:
 *
 *  1. **Las tres monedas del juego**: vidas (con cuenta atrás si son infinitas), teikoins y cristales.
 *  2. **Los 14 power-ups**: el emblema de la habilidad, sus usos, su nivel y —con la flecha verde— si
 *     ya se puede mejorar. Los que aún no tienes salen en silueta con el nivel en el que se unen.
 *  3. **Tocar un power-up abre su FICHA** ([AbilityPickerDialog]): los cinco niveles, MEJORAR y
 *     comprar usos. No hay arrastres ni ranuras. (Fuera de la rejilla, las vidas abren su ventana
 *     y los cristales despliegan para qué sirven, P-425.) Salir de la pestaña cierra la ficha (P-426).
 *
 * ⛔ Ni `Dialog` ni `Popup` (ver [ConfirmOverlay] e `ImmersiveWindowContractTest`). Lo único que se
 * mueve solo —la cuenta atrás de las vidas y la flecha verde— va gateado por [active].
 */
@Composable
internal fun InventoryScreen(
    teikoins: Int,
    crystalInventory: CrystalInventory,
    claimedCompanionIds: Set<String>,
    companionUpgrades: CompanionAbilityUpgrades,
    companionCharges: CompanionCharges,
    /**
     * Los power-ups elegidos para la próxima partida. Solo ELLOS llevan la flecha verde de «mejora
     * lista»: con una cuenta avanzada todo es mejorable, y el punto en las catorce casillas dejaba de
     * avisar de nada (la misma regla que tenía el cinturón, P-269).
     */
    selectedPowerUps: List<String> = emptyList(),
    lives: Int,
    /** Fin de las vidas infinitas (epoch ms); 0 o pasado = no hay. */
    infiniteLivesUntil: Long,
    /** Nivel que se va a jugar: da el color del panel. */
    selectedLevel: Int,
    /** `abilityUpgradeCostPercent` de Remote Config: el precio que se enseña es el que se cobra. */
    upgradeCostPercent: Int = 100,
    /** Compra UNA carga. Devuelve si ocurrió de verdad (solo entonces se celebra). */
    onBuyCharge: (String) -> Boolean,
    /** Mejora. Devuelve si ocurrió de verdad: solo entonces hay cinemática de «¡mejorada!». */
    onUpgradeAbility: (String) -> Boolean,
    /** P-427: el bloque equipado (lo dibuja el botón de la vitrina) y cómo abrirla. */
    activeCosmetic: String? = null,
    blocksBadge: Boolean = false,
    onOpenBlocks: () -> Unit = {},
    /** La casilla de vidas abre la ventana de vidas, como el corazón de Inicio. */
    onOpenLives: () -> Unit = {},
    /** Terminaron los láseres: el padre pinta el reveal por encima del navbar. */
    onAbilityUpgradeCinema: (AbilityUpgradeCinema) -> Unit = {},
    onUiClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    active: Boolean = true,
    /** Reloj inyectable: las capturas lo fijan para que la cuenta atrás salga siempre igual. */
    now: () -> Long = { System.currentTimeMillis() },
) {
    val accent = remember(selectedLevel) { levelAccentColor(selectedLevel) }
    val all = remember { NamedTeikaRegistry.all.filter { companionAbilityEnabled(it.id) } }
    val ownedCount = all.count { it.id in claimedCompanionIds }
    var pickerId by remember { mutableStateOf<String?>(null) }
    // P-425: tocar los cristales despliega para qué sirven (se pliega con otro toque).
    var crystalHint by remember { mutableStateOf(false) }
    // P-426: al salir de la pestaña se cierra la ficha y la nota: al volver, la vitrina limpia.
    LaunchedEffect(active) {
        if (!active) { pickerId = null; crystalHint = false }
    }

    // La cuenta atrás de las vidas infinitas: un tic por segundo, y solo con la pestaña a la vista.
    var clock by remember { mutableLongStateOf(now()) }
    val infinite = infiniteLivesUntil > clock
    LaunchedEffect(active, infinite) {
        clock = now()
        if (!active || !infinite) return@LaunchedEffect
        while (true) {
            delay(1_000)
            clock = now()
        }
    }

    CoinBurstHost(
        modifier = modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 14.dp)
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp)) {
                Image(
                    painter = painterResource(id = R.drawable.inventario_titulo),
                    contentDescription = "Inventario",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(INVENTORY_TITLE_HEIGHT)
                )
                // P-427: la vitrina de bloques, también aquí (antes solo en la cabecera del mapa).
                BlockShowcaseButton(
                    activeCosmetic = activeCosmetic,
                    size = INVENTORY_TITLE_HEIGHT,
                    onClick = onOpenBlocks,
                    badge = blocksBadge,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }

            // (2026-09-25, pedido del usuario) Dos paneles en vez de una vitrina: arriba, FIJA, la
            // cartera (vidas, teikoins, cristales); debajo, los power-ups en su propio panel, y solo
            // él hace scroll. Fuera el botón JUGAR del pie y el texto de ayuda de abajo.

            // ── LA CARTERA: vidas, teikoins, cristales ──────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), InvPanel)))
                    .border(1.5.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(22.dp))
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                WalletChip(
                    value = if (infinite) formatClock(infiniteLivesUntil - clock) else "$lives/${LivesRules.MAX}",
                    valueColor = if (infinite) InvGold else Color.White,
                    description = if (infinite) "Vidas infinitas: quedan ${formatClock(infiniteLivesUntil - clock)}"
                        else "${plural(lives, "vida", "vidas")} de ${LivesRules.MAX}. Abrir vidas",
                    onClick = { onUiClick(); onOpenLives() },
                    modifier = Modifier.weight(1f),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        RawImage(R.raw.heartful, Modifier.size(30.dp))
                        if (infinite) {
                            Text("∞", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black,
                                modifier = Modifier.offset(y = (-1).dp))
                        }
                    }
                }
                WalletChip(
                    value = compactAmount(rememberCountUp(teikoins)),
                    valueColor = InvGold,
                    description = plural(teikoins, "teikoin", "teikoins"),
                    modifier = Modifier.weight(1f),
                ) { TeikoinIcon(size = 30.dp) }
                WalletChip(
                    value = compactAmount(rememberCountUp(crystalInventory.count())),
                    valueColor = CRYSTAL_COLOR,
                    description = plural(crystalInventory.count(), "cristal", "cristales") +
                        ". Toca para ver para qué sirven",
                    onClick = { onUiClick(); crystalHint = !crystalHint },
                    modifier = Modifier.weight(1f),
                ) { CrystalGem(size = 28.dp) }
            }
            if (crystalHint) {
                // Fiel a [com.cukiti.teikafusion.model.CrystalEconomy]: caen fijos al superar un
                // nivel la primera vez, +1 con 3★, extra con el mural, y no se compran.
                Text(
                    "Los cristales mejoran tus power-ups. Se ganan al superar un nivel por primera vez " +
                        "(+1 con 3★, y extra al completar un mural); no se compran.",
                    color = CRYSTAL_COLOR, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center, lineHeight = 14.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { crystalHint = false }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                )
            }
            Spacer(Modifier.height(10.dp))

            // ── LOS POWER-UPS: su propio panel; solo aquí se hace scroll ─────────────────────────
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp))
                    .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.22f), InvPanel, InvTileDeep)))
                    .border(1.5.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(26.dp))
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "POWER-UPS", color = accent, fontSize = 13.sp,
                        fontWeight = FontWeight.Black, letterSpacing = 1.6.sp
                    )
                    Text(
                        "  $ownedCount/${all.size}", color = Color(0xFF8894BB),
                        fontSize = 12.sp, fontWeight = FontWeight.Black
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    items(all, key = { it.id }) { companion ->
                        if (companion.id in claimedCompanionIds) {
                            PowerUpInventoryTile(
                                companion = companion,
                                count = companionCharges.countOf(companion.id),
                                level = companionUpgrades.levelOf(companion.id),
                                upgradeReady = companion.id in selectedPowerUps &&
                                    companionUpgrades.canUpgradeNow(companion.id, teikoins, crystalInventory, upgradeCostPercent),
                                active = active,
                                onClick = { onUiClick(); pickerId = companion.id },
                            )
                        } else {
                            LockedPowerUpTile(companion)
                        }
                    }
                }
            }
            Spacer(Modifier.height(NavBarHeight + 10.dp))
        }

        // ── LA FICHA ────────────────────────────────────────────────────────────────────────
        pickerId?.let { id ->
            AbilityPickerDialog(
                companionId = id,
                teikoins = teikoins,
                companionUpgrades = companionUpgrades,
                crystalInventory = crystalInventory,
                upgradeCostPercent = upgradeCostPercent,
                phase = { 0f },
                chargeCount = companionCharges.countOf(id),
                onBuyCharge = { onBuyCharge(id) },
                onUpgrade = { cinema ->
                    // Si el motor la rechaza (el saldo cambió por detrás), la ficha se queda abierta
                    // y se repinta con lo que falta: celebrar algo que no ocurrió sería mentir.
                    if (onUpgradeAbility(id)) {
                        onAbilityUpgradeCinema(cinema)
                        pickerId = null
                    }
                },
                onClose = { onUiClick(); pickerId = null }
            )
        }
    }
}

/** «20:41:20» — horas sin tope, como la casilla de vidas de Candy Crush. */
private fun formatClock(ms: Long): String {
    val total = (ms.coerceAtLeast(0L) / 1000L)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%d:%02d:%02d".format(h, m, s)
}

/** «1 cristal» / «3 cristales»: TalkBack lee estas descripciones tal cual. */
internal fun plural(n: Int, one: String, many: String): String = "$n ${if (n == 1) one else many}"

/** Cifras que caben en una casilla de cuatro por fila (P-419: el formato único de [AmountFormat]). */
private fun compactAmount(value: Int): String = com.cukiti.teikafusion.model.AmountFormat.compact(value)

/**
 * Una moneda de la cartera: icono a la izquierda, cifra en su pastilla a la derecha. Horizontal y
 * bajo (2026-09-25): las tres caben en una fila fija sobre el panel de power-ups.
 */
@Composable
private fun WalletChip(
    value: String,
    valueColor: Color,
    description: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    icon: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(InvTile, InvTileDeep)))
            .border(1.2.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { icon() }
        Box(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(50))
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

/**
 * Una casilla de la vitrina: el icono arriba y la cifra en su píldora abajo — la gramática de la
 * referencia. [onClick] `null` = casilla informativa.
 */
@Composable
private fun InventoryTile(
    value: String,
    valueColor: Color,
    description: String,
    modifier: Modifier = Modifier,
    border: Color = Color.White.copy(alpha = 0.12f),
    onClick: (() -> Unit)? = null,
    overlay: @Composable () -> Unit = {},
    icon: @Composable () -> Unit,
) {
    Box(modifier.fillMaxWidth().aspectRatio(0.84f)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(InvTile, InvTileDeep)))
                .border(1.2.dp, border, RoundedCornerShape(16.dp))
                .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
                .semantics(mergeDescendants = true) { contentDescription = description }
                .padding(horizontal = 5.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { icon() }
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                    .padding(vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(value, color = valueColor, fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1)
            }
        }
        overlay()
    }
}

/** Un power-up que ya tienes: emblema, usos, nivel y la flecha verde si se puede mejorar. */
@Composable
private fun PowerUpInventoryTile(
    companion: NamedTeika,
    count: Int,
    level: Int,
    upgradeReady: Boolean,
    active: Boolean,
    onClick: () -> Unit,
) {
    val rarity = remember(companion.id) { Color(abilityRarityFor(companion.id).colorArgb) }
    val name = remember(companion.id) { COMPANION_ABILITIES[companion.id]?.name ?: companion.name }
    InventoryTile(
        value = "×$count",
        valueColor = if (count > 0) InvGold else Color.White.copy(alpha = 0.4f),
        description = "$name, ${plural(count, "uso", "usos")}, nivel $level" +
            if (upgradeReady) ", se puede mejorar" else "",
        border = rarity.copy(alpha = 0.55f),
        onClick = onClick,
        overlay = {
            Box(Modifier.fillMaxSize()) {
                LevelBadge(level, rarity, Modifier.align(Alignment.TopStart).padding(4.dp))
                if (upgradeReady) {
                    UpgradeArrowBadge(
                        active,
                        Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-8).dp).size(30.dp)
                    )
                }
            }
        }
    ) {
        RawImage(
            abilityIconRes(companion.id),
            Modifier.fillMaxSize(0.92f).graphicsLayer { alpha = if (count > 0) 1f else 0.55f },
            decodeMaxPx = ABILITY_ICON_MAX_PX
        )
    }
}

/** Un power-up que aún no tienes: silueta, candado y el nivel en el que se une. */
@Composable
private fun LockedPowerUpTile(companion: NamedTeika) {
    val levelLabel = remember(companion.id) {
        "${LevelMap.displayWord(companion.unlockLevel)} ${LevelMap.displayNumber(companion.unlockLevel)}"
    }
    InventoryTile(
        value = "Nivel ${LevelMap.displayNumber(companion.unlockLevel)}",
        valueColor = Color.White.copy(alpha = 0.55f),
        description = "Power-up bloqueado: se une en el ${levelLabel.lowercase()}",
    ) {
        Box(contentAlignment = Alignment.Center) {
            RawImage(
                abilityIconRes(companion.id),
                Modifier.size(52.dp).graphicsLayer { alpha = 0.16f },
                decodeMaxPx = ABILITY_ICON_MAX_PX
            )
            RawImage(R.raw.ic_lock, Modifier.size(22.dp))
        }
    }
}

/**
 * **LA INSIGNIA DE NIVEL**: «NV 3» en el color de su rareza (dorada al máximo). Sustituyó a los cinco
 * puntos (captura inventarioactual): a 6 dp y gris sobre gris en los comunes no se leían.
 */
@Composable
private fun LevelBadge(level: Int, color: Color, modifier: Modifier = Modifier) {
    val maxed = level >= COMPANION_ABILITY_MAX_LEVEL
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(
                Brush.verticalGradient(
                    if (maxed) listOf(Color(0xFFFFE27A), Color(0xFFF0A81C))
                    else listOf(lerp(color, Color.White, 0.15f), lerp(color, Color(0xFF0A0E22), 0.35f))
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.9f), RoundedCornerShape(50))
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (maxed) "MÁX" else "NV $level",
            color = if (maxed) Color(0xFF3A2400) else Color.White,
            fontSize = 9.sp, fontWeight = FontWeight.Black, maxLines = 1
        )
    }
}

/**
 * **LA FLECHA DE «MEJORA LISTA»** (2026-10-01, pedido del usuario: sustituye al punto rojo ▲).
 * Lottie vectorial `res/raw/ic_upgrade_arrow.json`, generado por `tools/lottie/gen_upgrade_arrow.py`:
 * se agacha, salta con un destello y suelta chispas, y descansa ~1 s (nunca un vaivén continuo).
 * La usan las casillas del Inventario y la pestaña de la barra de abajo.
 *
 * Sin [active] (pestaña oculta) o con «Reducir efectos» se queda quieta en el fotograma 0, que es
 * la pose de reposo: la flecha se sigue viendo, solo deja de moverse.
 */
@Composable
internal fun UpgradeArrowBadge(active: Boolean, modifier: Modifier = Modifier) {
    val playing = active && !LocalReducedFx.current
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.ic_upgrade_arrow))
    val progress = animateLottieCompositionAsState(
        composition,
        isPlaying = playing,
        iterations = LottieConstants.IterateForever,
    )
    // La lectura va en lambda: ocurre en el dibujo, no en composición.
    LottieAnimation(
        composition = composition,
        progress = { if (playing) progress.value else 0f },
        modifier = modifier,
    )
}
