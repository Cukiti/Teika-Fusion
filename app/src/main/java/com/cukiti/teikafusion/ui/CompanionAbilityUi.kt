package com.cukiti.teikafusion.ui

import com.cukiti.teikafusion.R
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.AbilityAura
import com.cukiti.teikafusion.model.AbilityRarity
import com.cukiti.teikafusion.model.COMPANION_ABILITIES
import com.cukiti.teikafusion.model.COMPANION_ABILITY_MAX_LEVEL
import com.cukiti.teikafusion.model.CompanionAbilityUpgrades
import com.cukiti.teikafusion.model.CrystalInventory
import com.cukiti.teikafusion.model.NamedTeikaRegistry
import com.cukiti.teikafusion.model.abilityRarityFor
import com.cukiti.teikafusion.model.companionAbilityLevelText
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// ════════════════════════════════════════════════════════════════════════════════
// Piezas de UI de las HABILIDADES DE COMPAÑERO.
//
// [AbilityPickerDialog] es la ficha del power-up (demo, mejora por aguante y usos). Al completar la
// mejora devuelve un [AbilityUpgradeCinema]; [InventoryScreen] lo sube a AppNavigation, que pinta
// [AbilityUpgradeReveal] por encima del navbar.
// ════════════════════════════════════════════════════════════════════════════════

private val Gold = Color(0xFFFFD700)
private val GoldDark = Color(0xFFFF8F00)

/** Duración de la inyección de cristales sobre el héroe (láseres + giro del anillo). */
internal const val ABILITY_INJECT_MS = 2000

/**
 * Snapshot inmutable al completar la mejora. Si el motor la acepta, la ficha se cierra y el padre
 * pinta con esto el reveal de «¡HABILIDAD MEJORADA!».
 */
internal data class AbilityUpgradeCinema(
    val companionId: String,
    val companionName: String,
    val abilityName: String,
    val fromLevel: Int,
    val toLevel: Int,
    /** Gemas que disparan láser en la cinemática: una por cristal gastado (desde P-267 hay un tipo). */
    val crystalTiers: List<Int>,
    val newEffectText: String,
    val rarity: AbilityRarity,
    val lottieResId: Int?
)

/**
 * Pips de nivel (1..MAX) + etiqueta "NIVEL X/MAX".
 *
 * **P-213 · LOS PIPS SON TAMBIÉN EL SELECTOR DEL FUTURO** (2026-09-06). Un panel de mejora de un
 * triple A enseña **el final del camino** —el nivel máximo, bloqueado— porque es lo que da ganas de
 * llegar; sin eso, el jugador de nivel 1 no tiene forma de saber qué hay al final. Pero la ventana
 * acababa de rehacerse para **quitar** información, así que añadir una columna fija con el nivel máx
 * habría deshecho justo lo que se acababa de hacer.
 *
 * La salida es que el futuro **no ocupa sitio nuevo**: los pips ya estaban ahí y ya representaban
 * los niveles, así que tocar un pip bloqueado cambia lo que enseña la columna derecha de la
 * comparación. Cero píxeles añadidos, y de paso el adorno pasa a ser un mando.
 *
 * [onPeek] nulo (la cinemática de mejora) = pips decorativos de siempre.
 */
@Composable
internal fun LevelPips(
    level: Int,
    /** Nivel que se está ojeando, para marcar su pip. */
    peekLevel: Int? = null,
    /** Recibe el nivel del pip tocado. Solo los BLOQUEADOS son pulsables: el pasado no se ojea. */
    onPeek: ((Int) -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(COMPANION_ABILITY_MAX_LEVEL) { i ->
            val pipLevel = i + 1
            val conseguido = i < level
            val ojeable = onPeek != null && pipLevel > level
            Box(
                Modifier
                    .size(width = 16.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            conseguido -> Gold
                            // El que se está ojeando se enciende a medias: se distingue del
                            // conseguido (que es oro pleno) sin dejar de decir «este es el que miras».
                            peekLevel == pipLevel -> Gold.copy(alpha = 0.55f)
                            else -> Color.White.copy(alpha = 0.16f)
                        }
                    )
                    .then(
                        if (ojeable) Modifier.clickable { onPeek(pipLevel) } else Modifier
                    )
                    .semantics {
                        contentDescription = when {
                            conseguido -> "Nivel $pipLevel conseguido"
                            ojeable -> "Nivel $pipLevel bloqueado. Toca para ver qué hace"
                            else -> "Nivel $pipLevel bloqueado"
                        }
                    }
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "NIVEL $level/$COMPANION_ABILITY_MAX_LEVEL",
            color = Gold.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * Panel de compra: ver habilidad + materiales + Mejorar / Cerrar.
 * Al mejorar devuelve [AbilityUpgradeCinema] y el padre cierra el diálogo e inicia la cinemática
 * sobre el héroe del inventario (no aquí).
 */
@Composable
internal fun AbilityPickerDialog(
    companionId: String,
    teikoins: Int,
    companionUpgrades: CompanionAbilityUpgrades,
    crystalInventory: CrystalInventory,
    /** `abilityUpgradeCostPercent` de Remote Config: la ficha cobra lo mismo que el motor. */
    upgradeCostPercent: Int = 100,
    phase: () -> Float,
    /** Cargas que quedan y cómo comprar una más. Ver [CompanionCharges]. */
    chargeCount: Int,
    /**
     * Compra un uso. Devuelve `true` si ocurrió de verdad — [PriceButton] solo celebra entonces, y
     * con el dedo mantenido esa diferencia importa: la última pulsación de una racha suele ser la
     * que el motor rechaza por llegar al tope.
     */
    onBuyCharge: () -> Boolean,
    onUpgrade: (AbilityUpgradeCinema) -> Unit,
    onClose: () -> Unit
) {
    val companion = remember(companionId) { NamedTeikaRegistry.byId(companionId) }
    val baseAbility = remember(companionId) { COMPANION_ABILITIES[companionId] }
    val level = companionUpgrades.levelOf(companionId)
    val maxed = companionUpgrades.isMaxed(companionId)
    val upgradeCost = companionUpgrades.costToNext(companionId, upgradeCostPercent)
    val crystalCost = remember(companionId, level) { companionUpgrades.crystalCostToNext(companionId) }
    val rarity = remember(companionId) { abilityRarityFor(companionId) }
    val name = companion?.name ?: "Compañero"
    val price = com.cukiti.teikafusion.model.CompanionChargeEconomy.priceOf(companionId)
    val atMax = chargeCount >= com.cukiti.teikafusion.model.CompanionCharges.MAX_PER_COMPANION
    // Una sola ficha de cristal (P-267). Se conserva la forma de lista porque la forja dispara un
    // láser por gema y el día que haya dos materiales no hay que rehacerla.
    val recipe = remember(crystalCost) {
        if (crystalCost > 0) mapOf(1 to crystalCost).entries.toList() else emptyList()
    }
    val hasCrystals = crystalInventory.canSpend(crystalCost)
    val canAffordCoins = teikoins >= upgradeCost
    val canUpgrade = !maxed && canAffordCoins && hasCrystals
    val dialogTabActive = LocalTabActive.current

    // P-213: qué nivel futuro se está ojeando. Se reinicia al SUBIR de nivel —y al cambiar de
    // compañero— porque lo que era «el final del camino» pasa a ser el escalón siguiente.
    var peekLevel by remember(companionId, level) { mutableStateOf<Int?>(null) }

    androidx.activity.compose.BackHandler(enabled = dialogTabActive) { onClose() }

    // ── LA FORJA: el reloj del aguante vive AQUÍ, no dentro del botón ───────────────────────────
    // Lo necesitan dos dibujos a la vez —el botón y los láseres que salen de los cristales— y la
    // regla de la casa es que un valor por fotograma se lee en DIBUJO, nunca en composición. Con el
    // `Animatable` izado, los dos lo leen en su `Canvas` y aguantar el dedo no recompone ni un nodo.
    val charge = remember { Animatable(0f) }

    // ── P-212 · EL RETRATO REACCIONA A LO QUE PUEDES COMPRAR (2026-09-06) ──────────────────────
    // Hasta hoy el Teika de la cabecera hacía su Lottie de siempre pasara lo que pasara: la ventana
    // sabía si podías mejorar y el personaje no se enteraba. Ahora **late cuando puedes** y se
    // apaga un poco cuando no te llega — el truco más barato que tienen los triple A para que un
    // menú se sienta vivo es que el personaje sepa lo que estás haciendo.
    //
    // **Dos saltitos y 1,1 s de pausa**, el mismo pulso que la flecha de mejora del Inventario.
    // ⛔ Nada de vaivén senoidal continuo: el movimiento constante se vuelve fondo a los diez
    // segundos, y este proyecto ya lo retiró una vez (el flotar del ninja).
    val reduced = LocalReducedFx.current
    val heartbeat = remember { Animatable(0f) }
    LaunchedEffect(canUpgrade, reduced, dialogTabActive) {
        if (!canUpgrade || reduced || !dialogTabActive) {
            heartbeat.snapTo(0f)
            return@LaunchedEffect
        }
        while (true) {
            repeat(2) {
                heartbeat.animateTo(1f, tween(190, easing = FastOutSlowInEasing))
                heartbeat.animateTo(0f, tween(230, easing = FastOutSlowInEasing))
            }
            kotlinx.coroutines.delay(1100)
        }
    }

    // Centros en coordenadas de la ventana: de dónde sale cada láser y a dónde llega. Son
    // `FloatArray` planos y NO estado: se escriben en LAYOUT y se leen en DIBUJO, así que
    // convertirlos en `State` recompondría la ventana cada vez que algo se mide.
    val sheetOrigin = remember { FloatArray(2) }
    val heroCenter = remember { FloatArray(2) }
    val gemCenters = remember(recipe.size) { FloatArray(recipe.size.coerceAtLeast(1) * 2) }

    // Host propio de la lluvia de teikoins: sin él, las monedas del botón de usos saldrían por
    // detrás de esta ventana en vez de por delante (ver [CoinBurstHost]).
    CoinBurstHost(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.74f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onClose() },
    ) {
        // El navbar y las barras del sistema se dibujan ENCIMA de esta ventana, que vive dentro de
        // la pestaña: sin descontar su alto, el pie quedaba tapado.
        BoxWithConstraints(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(bottom = NavBarHeight),
            contentAlignment = Alignment.Center
        ) {
            val pop = remember { Animatable(0.85f) }
            LaunchedEffect(companionId) {
                pop.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMedium))
            }
            val maxDialogHeight = maxHeight * 0.9f
            Box(
                modifier = Modifier
                    .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                    .fillMaxWidth(0.92f)
                    .heightIn(max = maxDialogHeight)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF2A356C), Color(0xFF12183A))))
                    .drawBehind {
                        val glow = 0.5f + 0.4f * (0.5f + 0.5f * sin(phase() * (2f * PI.toFloat()) * 0.6f))
                        drawRoundRect(
                            Brush.linearGradient(listOf(Gold.copy(alpha = glow), GoldDark.copy(alpha = glow * 0.5f))),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(24.dp.toPx(), 24.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                    // Traga el toque para que no cierre por el scrim de detrás.
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
                    .onGloballyPositioned {
                        val b = it.boundsInRoot()
                        sheetOrigin[0] = b.left
                        sheetOrigin[1] = b.top
                    }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Red de seguridad: con la letra del sistema muy grande el contenido puede
                        // pasarse del 90 % de alto. En uso normal no hay nada que desplazar.
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    // ── 1. CABECERA: quién, qué habilidad, y la X ───────────────────────────
                    // El retrato baja de 72 a 46 dp y el subtítulo «Tu compañero · Nivel X/3»
                    // desaparece: el nivel ya lo dicen los pips justo debajo, y decirlo dos veces
                    // era parte de lo que hacía la ventana pesada de leer.
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // El valor animado se lee en `graphicsLayer` —o sea, en DIBUJO— y nunca en
                        // el cuerpo del composable: latir no recompone ni un nodo de la ventana.
                        Box(
                            Modifier.graphicsLayer {
                                val h = heartbeat.value
                                // Y mientras se forja, el retrato se hincha con la carga: los
                                // láseres de los cristales le están entrando a él.
                                val c = charge.value
                                val sc = 1f + 0.10f * h + 0.18f * c
                                scaleX = sc
                                scaleY = sc
                                // Sin materiales, el personaje se apaga. Es un «todavía no» dicho
                                // sin una sola palabra más en una ventana que ya tiene bastantes.
                                alpha = if (canUpgrade || maxed) 1f else 0.55f
                            }
                        ) {
                            PremiumMedallion(accent = Gold, phase = phase, size = 46.dp, spin = true, glow = 1f) {
                                if (companion != null) DexLottieIcon(companion.lottieResId, Modifier.size(32.dp))
                                else RawImage(R.raw.ic_star, Modifier.size(24.dp))
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(
                                name,
                                color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Black,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            if (baseAbility != null) {
                                Text(
                                    baseAbility.name.uppercase(),
                                    color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Black,
                                    letterSpacing = 0.8.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // ⛔ El botón «Cerrar» de 44 dp a lo ancho se retiró (2026-09-06, pedido del
                        // usuario): se comía un renglón entero de la ventana para decir lo que una
                        // X en la esquina dice sin ocupar nada. Aquí arriba, además, está donde el
                        // jugador ya la busca.
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.White.copy(alpha = 0.10f))
                                .clickable(onClick = onClose)
                                .semantics { contentDescription = "Cerrar" },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    // ── 2. NIVEL Y RAREZA, en un solo renglón ───────────────────────────────
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LevelPips(
                            level = level,
                            peekLevel = peekLevel,
                            // Tocar el pip que ya se enseña lo apaga: el mando va y vuelve.
                            onPeek = { pip -> peekLevel = if (peekLevel == pip) null else pip },
                        )
                        Spacer(Modifier.weight(1f))
                        RarityPill(rarity)
                    }

                    // ── 2b. LA DEMO: el efecto real del poder, en bucle (P-269) ──────────────
                    // Sigue el nivel (P-383): el tuyo, o el pip que estés ojeando.
                    AbilityDemoBoard(companionId = companionId, animate = !reduced, level = peekLevel ?: level)


                    // ── 3. ANTES → DESPUÉS ──────────────────────────────────────────────────
                    if (baseAbility != null) {
                        // Por defecto la columna derecha es el escalón SIGUIENTE —lo que compras—;
                        // con un pip ojeado pasa a ser ese, marcado como lo que es: más adelante.
                        val shownLevel = (peekLevel ?: (level + 1)).coerceAtMost(COMPANION_ABILITY_MAX_LEVEL)
                        AbilityComparison(
                            currentEffect = companionAbilityLevelText(companionId, level),
                            nextEffect = if (maxed) null else companionAbilityLevelText(companionId, shownLevel),
                            nextLevel = shownLevel,
                            // Ojear un nivel que NO es el siguiente tiene que verse distinto o el
                            // jugador creerá que el botón de aguante le da ESE. Es la única forma en
                            // que enseñar el final del camino podría mentir, así que se marca.
                            isPeek = shownLevel > level + 1,
                        )
                    }

                    // ── 4. EL PRECIO, en una fila de fichas ─────────────────────────────────
                    if (!maxed) {
                        UpgradeCostRow(
                            recipe = recipe,
                            crystalInventory = crystalInventory,
                            cost = upgradeCost,
                            canAffordCoins = canAffordCoins,
                            sheetOrigin = sheetOrigin,
                            gemCenters = gemCenters,
                        )
                    }

                    // ── 5. EL AGUANTE ───────────────────────────────────────────────────────
                    if (canUpgrade && baseAbility != null) {
                        HoldToUpgradeButton(
                            label = "MANTÉN PULSADO PARA MEJORAR",
                            accent = Gold,
                            charge = charge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned {
                                    // El destino de los láseres es el botón: es lo que se está
                                    // forjando, y es donde el jugador tiene el dedo.
                                    //
                                    // Se guarda en coordenadas de RAÍZ y la resta del origen de la
                                    // ventana se hace AL DIBUJAR, no aquí. Restar en este punto
                                    // ataría el resultado al orden en que Compose entrega los
                                    // callbacks de layout: si el de la ventana llega después que
                                    // el de este botón, `sheetOrigin` todavía valdría 0 y los
                                    // láseres saldrían desplazados justo lo que mide el margen.
                                    val b = it.boundsInRoot()
                                    heroCenter[0] = b.center.x
                                    heroCenter[1] = b.center.y
                                },
                            onComplete = {
                                val to = (level + 1).coerceAtMost(COMPANION_ABILITY_MAX_LEVEL)
                                onUpgrade(
                                    AbilityUpgradeCinema(
                                        companionId = companionId,
                                        companionName = name,
                                        abilityName = baseAbility.name,
                                        fromLevel = level,
                                        toLevel = to,
                                        crystalTiers = recipe.map { it.key },
                                        newEffectText = companionAbilityLevelText(companionId, to),
                                        rarity = rarity,
                                        lottieResId = companion?.lottieResId
                                    )
                                )
                            }
                        )
                    } else {
                        // Ningún botón desaparece al agotarse su función: se queda en su sitio,
                        // apagado y diciendo QUÉ falta. Cargar algo que no se puede pagar sería una
                        // promesa falsa.
                        PremiumButton(
                            label = when {
                                maxed -> "NIVEL MÁXIMO"
                                !hasCrystals -> "Te faltan ${plural(crystalCost - crystalInventory.count(), "cristal", "cristales")}"
                                else -> "Te faltan ${plural(upgradeCost - teikoins, "teikoin", "teikoins")}"
                            },
                            enabled = false,
                            gradient = listOf(Color(0xFF3A4366), Color(0xFF262C49)),
                            phase = phase,
                            height = 46.dp,
                            textColor = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { }
                        )
                    }

                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))

                    // ── 6. LOS USOS, al pie y en UNA fila ───────────────────────────────────
                    // Antes eran un bloque con rótulo propio, medidor a lo ancho y el botón al
                    // lado. Es información de otra cosa —cuántas veces puedes lanzarla, no cuánto
                    // sube— así que va al pie, en un renglón, y deja de competir con la mejora.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            if (atMax) "USOS · MÁX" else "USOS",
                            color = if (atMax) Gold else Color(0xFF8894BB),
                            fontSize = 9.sp, fontWeight = FontWeight.Black, letterSpacing = 1.2.sp
                        )
                        ChargeMeter(
                            charges = chargeCount,
                            accent = Gold,
                            modifier = Modifier.weight(1f),
                            capsuleHeight = 10.dp,
                        )
                        PriceButton(
                            price = price,
                            label = if (atMax) "MÁX" else null,
                            showCoin = !atMax,
                            enabled = teikoins >= price && !atMax,
                            height = 30.dp,
                            fontSize = 11.sp,
                            depth = 3.dp,
                            modifier = Modifier.width(86.dp),
                            contentDescription = if (atMax) {
                                "$name ya tiene todos los usos"
                            } else {
                                "Comprar un uso por $price teikoins. " +
                                    "Mantén pulsado para comprar hasta tres seguidos"
                            },
                            repeatWhileHeld = true,
                            onBuy = onBuyCharge,
                        )
                    }
                }

                // ── LA FORJA, por encima de todo el contenido ───────────────────────────────
                // Los cristales alimentan al botón mientras el dedo aguanta. Es la MISMA imagen que
                // la cinemática de mejora (`pointOnCircleEdge` + los haces del héroe), y eso es
                // deliberado: lo que el jugador ve mientras paga tiene que ser lo que vea al cobrar.
                //
                // No come toques (`Canvas` sin `pointerInput`) y solo pide fotogramas mientras
                // `charge` se mueve: en reposo el valor es 0 y el `Canvas` sale por la primera línea.
                Canvas(Modifier.matchParentSize()) {
                    val c = charge.value
                    if (c <= 0.01f) return@Canvas
                    // Todo se midió en coordenadas de RAÍZ y se traduce a las de la ventana AQUÍ:
                    // el dibujo ocurre después de todo el layout, así que `sheetOrigin` ya es firme.
                    val ox = sheetOrigin[0]
                    val oy = sheetOrigin[1]
                    val hx = heroCenter[0] - ox
                    val hy = heroCenter[1] - oy
                    if (heroCenter[0] <= 0f && heroCenter[1] <= 0f) return@Canvas
                    for (i in 0 until gemCenters.size / 2) {
                        if (gemCenters[i * 2] <= 0f && gemCenters[i * 2 + 1] <= 0f) continue
                        val gx = gemCenters[i * 2] - ox
                        val gy = gemCenters[i * 2 + 1] - oy
                        // El haz CRECE desde la gema hacia el botón: a media carga va por la mitad
                        // del camino, así que el avance se lee sin mirar el relleno del botón.
                        val ex = gx + (hx - gx) * c
                        val ey = gy + (hy - gy) * c
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(Gold.copy(alpha = 0f), Gold.copy(alpha = 0.85f * c)),
                                start = Offset(gx, gy),
                                end = Offset(ex, ey),
                            ),
                            start = Offset(gx, gy),
                            end = Offset(ex, ey),
                            strokeWidth = (1.5f + 2.5f * c).dp.toPx(),
                            cap = StrokeCap.Round,
                        )
                        // La gema se enciende según se vacía en el haz.
                        drawCircle(
                            color = Gold.copy(alpha = 0.5f * c),
                            radius = (7f + 5f * c).dp.toPx(),
                            center = Offset(gx, gy),
                        )
                    }
                }
            }
        }
    }
}

/**
 * **ANTES → DESPUÉS** (2026-09-06, dirección elegida por el usuario).
 *
 * La ventana entera es una comparación: lo que la habilidad hace hoy, y lo que hará si pagas. Es la
 * forma en que un panel de mejora de un triple A responde la única pregunta que el jugador tiene
 * delante — *¿qué me llevo por esto?*— y sustituye a dos bloques sueltos que la contaban a medias:
 * un párrafo con el efecto actual y, más abajo, una caja dorada de «AL SUBIR».
 *
 * Al máximo no hay columna derecha: la flecha y el hueco desaparecen en vez de quedarse vacíos.
 */
@Composable
private fun AbilityComparison(
    currentEffect: String,
    nextEffect: String?,
    nextLevel: Int,
    /** El nivel enseñado NO es el que se compra ahora, sino uno más lejano (P-213). */
    isPeek: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF1E254A), Color(0xFF131938))))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "AHORA",
                color = Color(0xFF8894BB), fontSize = 9.sp,
                fontWeight = FontWeight.Black, letterSpacing = 1.1.sp
            )
            // Sin `maxLines`: el texto de una habilidad es la razón por la que se paga, y cortarlo
            // con puntos es justo donde no se puede cortar.
            Text(
                currentEffect,
                color = Color.White.copy(alpha = 0.82f), fontSize = 11.5.sp, lineHeight = 15.sp
            )
        }
        if (nextEffect != null) {
            Text(
                "→",
                color = Gold, fontSize = 18.sp, fontWeight = FontWeight.Black,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (isPeek) "NIVEL $nextLevel · MÁS ADELANTE" else "NIVEL $nextLevel",
                    // Apagado cuando es un vistazo: lo que se compra AHORA es lo único que va en oro
                    // pleno. Un futuro lejano pintado igual que la compra sería una promesa falsa.
                    color = if (isPeek) Gold.copy(alpha = 0.55f) else Gold,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 1.1.sp
                )
                Text(
                    nextEffect,
                    color = Gold.copy(alpha = if (isPeek) 0.6f else 0.95f),
                    fontSize = 11.5.sp, lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * **EL PRECIO, EN UNA FILA DE FICHAS** — cristales y teikoins.
 *
 * Sustituye al bloque «Materiales necesarios», que era un rótulo, una fila por cristal con su nombre
 * y su `3/2`, un filete y otra fila para el precio: seis renglones para decir lo que cabe en uno.
 * Cada ficha se pinta en **verde si te llega y en rojo si no**, así que la pregunta «¿puedo?» se
 * contesta por color antes de leer un solo número.
 *
 * Además cada gema **reporta su centro** ([gemCenters]): es de donde salen los láseres de la forja
 * mientras se aguanta el botón.
 */
@Composable
private fun UpgradeCostRow(
    recipe: List<Map.Entry<Int, Int>>,
    crystalInventory: CrystalInventory,
    cost: Int,
    canAffordCoins: Boolean,
    sheetOrigin: FloatArray,
    gemCenters: FloatArray,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        recipe.forEachIndexed { idx, (tier, amount) ->
            val have = crystalInventory.count()
            val enough = have >= amount
            val tint = if (enough) Color(0xFF7CFF9E) else Color(0xFFFF8A8A)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.12f))
                    .border(1.dp, tint.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 7.dp, vertical = 5.dp)
                    // Coordenadas de RAÍZ; la ventana las traduce a las suyas al dibujar. Ver la
                    // nota del botón de aguante: restar aquí dependería del orden de los callbacks.
                    .onGloballyPositioned {
                        val b = it.boundsInRoot()
                        if (idx * 2 + 1 < gemCenters.size) {
                            gemCenters[idx * 2] = b.center.x
                            gemCenters[idx * 2 + 1] = b.center.y
                        }
                    }
                    .clearAndSetSemantics {
                        contentDescription = "Cristales: tienes $have de $amount necesarios" +
                            (if (enough) "" else ". Te faltan ${amount - have}: se ganan superando niveles")
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CrystalGem(size = 17.dp)
                Text("$have/$amount", color = tint, fontSize = 11.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.weight(1f))
        val coinTint = if (canAffordCoins) Gold else Color(0xFFFF8A8A)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(coinTint.copy(alpha = 0.12f))
                .border(1.dp, coinTint.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 5.dp)
                .clearAndSetSemantics {
                    contentDescription = "Cuesta $cost teikoins" +
                        if (canAffordCoins) "" else ". No te alcanzan"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            RawImage(R.raw.teikoin, Modifier.size(15.dp))
            Text("$cost", color = coinTint, fontSize = 11.sp, fontWeight = FontWeight.Black)
        }
    }
}

/**
 * Punto en el borde del círculo [center]+[radius] en dirección a [toward].
 * Así el láser sale del borde del cristal / llega al anillo del Teika, no desde el centro.
 */
internal fun pointOnCircleEdge(center: Offset, toward: Offset, radius: Float): Offset {
    val dx = toward.x - center.x
    val dy = toward.y - center.y
    val len = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)
    return Offset(center.x + dx / len * radius, center.y + dy / len * radius)
}

/**
 * Láseres de las ranuras de cristales mágicos → borde del anillo del héroe.
 *
 * [sourcesRoot] / [targetRoot] en coordenadas root (centros). [sourceRadiusPx] / [targetRadiusPx]
 * proyectan el haz al borde en el ángulo de salida/llegada.
 * [progress] lo anima el padre (mismo clock que vibración + giro del anillo) → 0 recomposición.
 */
@Composable
internal fun CrystalInjectLasers(
    tiers: List<Int>,
    sourcesRoot: (Int) -> Offset?,
    targetRoot: () -> Offset,
    rootPos: () -> Offset,
    sourceRadiusPx: Float,
    targetRadiusPx: Float,
    progress: () -> Float
) {
    val colors = remember(tiers) { tiers.map { CRYSTAL_COLOR } }

    Canvas(Modifier.fillMaxSize()) {
        val t = progress()
        if (t >= 1f) return@Canvas
        val origin = rootPos()
        val toCenter = targetRoot() - origin

        // Destello en el núcleo del Teika al recibir energía.
        val impactPulse = (0.5f + 0.5f * sin(t * 36f)) * (1f - t * 0.35f)
        drawCircle(
            Color.White.copy(alpha = 0.28f * impactPulse),
            radius = targetRadiusPx * 0.55f + 14f * impactPulse,
            center = toCenter
        )
        drawCircle(
            Color.White.copy(alpha = 0.12f * impactPulse),
            radius = targetRadiusPx * 1.05f,
            center = toCenter,
            style = Stroke(width = 3f + 4f * impactPulse)
        )

        tiers.forEachIndexed { idx, tier ->
            val fromCenterRoot = sourcesRoot(tier) ?: return@forEachIndexed
            val fromCenter = fromCenterRoot - origin
            val color = colors.getOrElse(idx) { CRYSTAL_COLOR }

            // Borde del cristal en dirección al Teika; borde del anillo en dirección al cristal.
            val from = pointOnCircleEdge(fromCenter, toCenter, sourceRadiusPx)
            val to = pointOnCircleEdge(toCenter, fromCenter, targetRadiusPx)

            val stagger = (idx * 0.07f).coerceAtMost(0.22f)
            val localT = ((t - stagger) / (1f - stagger)).coerceIn(0f, 1f)
            if (localT <= 0f) return@forEachIndexed

            val beamPower = (sin(localT * PI.toFloat()).coerceAtLeast(0f)) * (1f - localT * 0.12f)
            // Curva suave hacia el héroe (ligero arco, no línea muerta).
            val mid = Offset(
                (from.x + to.x) * 0.5f,
                (from.y + to.y) * 0.5f - 22f * sin(localT * PI.toFloat())
            )

            fun beam(a: Color, w: Float) {
                drawLine(a, from, mid, strokeWidth = w, cap = StrokeCap.Round)
                drawLine(a, mid, to, strokeWidth = w, cap = StrokeCap.Round)
            }
            beam(color.copy(alpha = 0.20f * beamPower), 20f * beamPower)
            beam(color.copy(alpha = 0.55f * beamPower), 9f * beamPower + 1f)
            beam(color.copy(alpha = 0.92f * beamPower), 4.2f * beamPower + 1.2f)
            beam(Color.White.copy(alpha = 0.80f * beamPower), 1.6f)

            // Cargas viajando por el haz.
            repeat(3) { k ->
                val u = ((localT * 1.45f + k * 0.28f) % 1.05f).coerceIn(0f, 1f)
                val omu = 1f - u
                val px = omu * omu * from.x + 2f * omu * u * mid.x + u * u * to.x
                val py = omu * omu * from.y + 2f * omu * u * mid.y + u * u * to.y
                val packA = (1f - u) * beamPower
                drawCircle(color.copy(alpha = 0.9f * packA), radius = 5.5f + 3f * (1f - u), center = Offset(px, py))
                drawCircle(Color.White.copy(alpha = 0.55f * packA), radius = 2.2f, center = Offset(px, py))
            }

            // Flash en el BORDE del cristal (origen del láser).
            val srcFlash = (1f - (localT / 0.22f).coerceIn(0f, 1f)) * (if (localT < 0.4f) 1f else 0.4f)
            if (srcFlash > 0.02f) {
                drawCircle(color.copy(alpha = 0.65f * srcFlash), radius = 14f * srcFlash + 5f, center = from)
                drawCircle(Color.White.copy(alpha = 0.4f * srcFlash), radius = 5f * srcFlash + 2f, center = from)
            }
            // Impacto en el anillo del Teika.
            val hitFlash = (0.55f + 0.45f * sin(localT * 40f)) * beamPower
            drawCircle(color.copy(alpha = 0.55f * hitFlash), radius = 8f + 6f * hitFlash, center = to)
            drawCircle(Color.White.copy(alpha = 0.45f * hitFlash), radius = 3.5f, center = to)
        }
    }
}

/**
 * Reveal de desbloqueo a pantalla completa (tras la inyección en el héroe).
 *
 * **Sin ventana propia (arreglo 2026-07-28).** Antes vivía en un [Dialog] para dibujarse por encima
 * del BottomNavBar, y eso rompía la pantalla: la app corre en modo INMERSIVO
 * (`MainActivity.hideSystemBars()`), así que al abrirse una segunda ventana la Activity perdía el
 * foco, Android sacaba las barras del sistema, los insets cambiaban y TODO el contenido de debajo
 * (navbar de la app incluido) se empujaba; al pulsar Continuar, `onWindowFocusChanged(true)` las
 * volvía a ocultar y la pantalla "saltaba" de vuelta.
 *
 * Ahora se compone en la jerarquía normal de la Activity, como capa superior de `AppNavigation`
 * (después del navbar), que es lo que le da el z-order que buscaba el diálogo. Consume el toque de
 * fondo para que nada de debajo reaccione, y el atrás equivale a Continuar.
 */
@Composable
internal fun AbilityUpgradeReveal(
    cinema: AbilityUpgradeCinema,
    phase: () -> Float,
    onContinue: () -> Unit
) {
    val enter = remember { Animatable(0f) }
    LaunchedEffect(cinema.companionId, cinema.toLevel) {
        enter.snapTo(0f)
        enter.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
    }
    val rarityColor = Color(cinema.rarity.colorArgb)
    val aura = remember(cinema.toLevel) { AbilityAura.of(cinema.toLevel) }
    val auraColor = Color(aura.colorArgb)

    androidx.activity.compose.BackHandler(enabled = true) { onContinue() }

    run {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Traga los toques: el inventario se ve detrás, pero no se puede tocar.
                .pointerInput(Unit) { detectTapGestures { } }
                .safeDrawingPadding()
        ) {
            // VFX de fondo (clock solo en draw).
            //
            // El velo NEGRO 0,88 de antes borraba la pantalla: la mejora parecía ocurrir en otro
            // sitio, no en tu inventario. Ahora el fondo se sigue viendo bajo un velo TINTADO con
            // el color de la rareza y una viñeta que oscurece solo los bordes, así que el Teika
            // queda recortado en luz sobre su propia pantalla. El velo se dibuja aquí (no como
            // `background`) para que su fundido de entrada no recomponga nada.
            Canvas(Modifier.fillMaxSize()) {
                val t = enter.value
                val c = Offset(size.width / 2f, size.height * 0.30f)
                val m = size.minDimension

                // Velo + viñeta: transparente y teñido en el centro, oscuro y opaco en los bordes.
                val veil = (t / 0.22f).coerceIn(0f, 1f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            rarityColor.copy(alpha = 0.20f * veil),
                            Color(0xFF080C1E).copy(alpha = 0.52f * veil),
                            Color(0xFF04060F).copy(alpha = 0.86f * veil)
                        ),
                        center = c,
                        radius = m * 1.25f
                    )
                )

                val flash = (1f - (t / 0.18f).coerceIn(0f, 1f))
                if (flash > 0f) {
                    drawCircle(Color.White.copy(alpha = 0.55f * flash), radius = m * 0.55f * (1f - 0.3f * flash), center = c)
                    drawCircle(rarityColor.copy(alpha = 0.40f * flash), radius = m * 0.42f, center = c)
                }

                val ring = ((t - 0.08f) / 0.55f).coerceIn(0f, 1f)
                if (ring > 0f && ring < 1f) {
                    drawCircle(
                        rarityColor.copy(alpha = (1f - ring) * 0.85f),
                        radius = m * (0.12f + 0.38f * ring),
                        center = c,
                        style = Stroke(width = (1f - ring) * 14f + 2f)
                    )
                    val ring2 = ((t - 0.18f) / 0.55f).coerceIn(0f, 1f)
                    if (ring2 > 0f && ring2 < 1f) {
                        drawCircle(
                            Gold.copy(alpha = (1f - ring2) * 0.7f),
                            radius = m * (0.10f + 0.42f * ring2),
                            center = c,
                            style = Stroke(width = (1f - ring2) * 10f + 1.5f)
                        )
                    }
                }

                val rayT = ((t - 0.10f) / 0.50f).coerceIn(0f, 1f)
                if (rayT > 0f && rayT < 1f) {
                    val a = (1f - rayT) * 0.75f
                    repeat(14) { i ->
                        val ang = i * (2f * PI.toFloat() / 14f) + rayT * 0.4f
                        val r1 = m * (0.10f + 0.12f * rayT)
                        val r2 = r1 + m * 0.14f * (1f - rayT * 0.35f)
                        drawLine(
                            rarityColor.copy(alpha = a),
                            Offset(c.x + cos(ang) * r1, c.y + sin(ang) * r1),
                            Offset(c.x + cos(ang) * r2, c.y + sin(ang) * r2),
                            strokeWidth = 3.2f
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(0.08f))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.graphicsLayer {
                        val t = enter.value
                        val s = 0.55f + 0.57f * FastOutSlowInEasing.transform(t.coerceIn(0f, 1f))
                        scaleX = s
                        scaleY = s
                        translationY = (1f - t) * 36f
                        alpha = t.coerceIn(0f, 1f)
                    }
                ) {
                    PremiumMedallion(accent = auraColor, phase = phase, size = 176.dp, spin = true, glow = aura.glow) {
                        if (cinema.lottieResId != null) {
                            DexLottieIcon(cinema.lottieResId, Modifier.size(124.dp))
                        } else {
                            RawImage(R.raw.ic_star, Modifier.size(78.dp))
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                Column(
                    modifier = Modifier
                        .graphicsLayer {
                            val t = ((enter.value - 0.28f) / 0.55f).coerceIn(0f, 1f)
                            alpha = t
                            translationY = (1f - t) * 28f
                        }
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .clip(RoundedCornerShape(22.dp))
                        // Panel de VIDRIO: translúcido para que el velo tintado y el inventario se
                        // intuyan detrás, con brillo arriba y filo en gradiente (receta de ventana
                        // premium del proyecto). Los alphas se quedan altos donde va el texto.
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF2A356C).copy(alpha = 0.74f),
                                    Color(0xFF12183A).copy(alpha = 0.86f)
                                )
                            )
                        )
                        .drawBehind {
                            // Glossy: media tapa superior más clara, como cristal.
                            drawRoundRect(
                                brush = Brush.verticalGradient(
                                    listOf(Color.White.copy(alpha = 0.10f), Color.Transparent)
                                ),
                                size = androidx.compose.ui.geometry.Size(size.width, size.height * 0.42f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx(), 22.dp.toPx())
                            )
                            drawRoundRect(
                                brush = Brush.linearGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.55f),
                                        rarityColor.copy(alpha = 0.75f),
                                        rarityColor.copy(alpha = 0.25f)
                                    )
                                ),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                                style = Stroke(width = 1.4.dp.toPx())
                            )
                        }
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "✦ ¡HABILIDAD MEJORADA! ✦",
                        color = Gold,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        cinema.companionName,
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    RarityPill(cinema.rarity)
                    Text(
                        cinema.abilityName,
                        color = Color.White.copy(alpha = 0.88f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        textAlign = TextAlign.Center
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            "NIVEL ${cinema.fromLevel}",
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text("→", color = Gold, fontSize = 16.sp, fontWeight = FontWeight.Black)
                        Text(
                            "NIVEL ${cinema.toLevel}/$COMPANION_ABILITY_MAX_LEVEL",
                            color = Gold,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    LevelPips(cinema.toLevel)

                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.10f)))

                    Text(
                        "NUEVO EFECTO",
                        color = rarityColor.copy(alpha = 0.9f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.1.sp
                    )
                    Text(
                        cinema.newEffectText,
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Continuar fijo abajo: sin graphicsLayer alpha (el alpha 0 bloqueaba toques al inicio
                // y el navbar de la app ya no lo tapa porque este Dialog es otra ventana).
                PremiumButton(
                    label = "Continuar",
                    enabled = true,
                    gradient = listOf(Gold, GoldDark),
                    phase = phase,
                    height = 52.dp,
                    textColor = Color(0xFF1A1208),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 12.dp),
                    onClick = onContinue
                )
            }
        }
    }
}

/* (RETIRADA 2026-09-06) `AbilityUpgradeCard`: la ficha que apilaba nombre + rareza + pips + efecto
 * actual + caja «AL SUBIR» + bloque «Materiales necesarios» + botón. La sustituye el diálogo
 * ANTES → DESPUÉS (`AbilityComparison` + `UpgradeCostRow`), que dice lo mismo en un tercio del
 * alto. Se BORRA en vez de dejarla ahí: una función sin llamantes es como este repositorio acumula
 * motores fantasma — ya costó una limpieza con `isStuck()` y otra con `randomCatalogPiece`. */

/** Duración de la carga del botón de mejora (mantener pulsado). */
private const val UPGRADE_HOLD_MS = 1300

/**
 * Botón de MEJORAR con carga por MANTENER PULSADO.
 *
 * Mejorar es irreversible y cuesta el material de varias partidas: un toque suelto lo resolvía sin
 * ceremonia y sin margen de arrepentimiento. Aquí la energía se acumula mientras el dedo aguanta —
 * anillo que recorre el perímetro, relleno que avanza y temblor al final — y soltar antes lo vacía.
 *
 * Optimización: el reloj ([Animatable]) NO se lee nunca en composición. Va al `graphicsLayer` (para
 * el temblor) y al `Canvas` (para el anillo), así que cargar no recompone ni un nodo; y los objetos
 * de dibujo ([Path]/[PathMeasure]) son scratch remembered, cero asignaciones por fotograma.
 */
@Composable
private fun HoldToUpgradeButton(
    label: String,
    accent: Color,
    /**
     * El reloj de la carga, IZADO al diálogo (2026-09-06). Lo leen dos dibujos: este botón y los
     * láseres de la forja que salen de los cristales. Se pasa el `Animatable` en vez de un callback
     * de progreso porque un callback obligaría a escribir estado por fotograma — y el valor por
     * fotograma se lee en DIBUJO, nunca en composición.
     */
    charge: Animatable<Float, androidx.compose.animation.core.AnimationVector1D>,
    modifier: Modifier = Modifier,
    onComplete: () -> Unit
) {
    val flash = remember { Animatable(0f) }
    var pressed by remember { mutableStateOf(false) }
    var firing by remember { mutableStateOf(false) }
    val complete by rememberUpdatedState(onComplete)

    LaunchedEffect(pressed) {
        if (firing) return@LaunchedEffect
        if (pressed) {
            // Lo que falta de carga, a ritmo constante: soltar y volver a pulsar no penaliza.
            val remaining = ((1f - charge.value) * UPGRADE_HOLD_MS).toInt().coerceAtLeast(1)
            charge.animateTo(1f, tween(remaining, easing = LinearEasing))
            firing = true
            flash.snapTo(1f)
            complete()
            flash.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        } else {
            // Se escapa más rápido de lo que entra: no se puede "guardar" carga a plazos.
            charge.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
        }
    }

    // Scratch del anillo: se reutilizan en cada fotograma (nada de allocs en el draw).
    val ringPath = remember { Path() }
    val segPath = remember { Path() }
    val measure = remember { PathMeasure() }

    Box(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                val c = charge.value
                // El botón se tensa: crece un pelo y tiembla en el último tramo.
                val s = 1f + 0.035f * c
                scaleX = s
                scaleY = s
                val shake = ((c - 0.72f) / 0.28f).coerceIn(0f, 1f)
                translationX = sin(c * 90f) * 3.4f * shake
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(accent, GoldDark)))
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed = true
                    waitForUpOrCancellation()
                    pressed = false
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = "$label. Mantén pulsado para mejorar la habilidad"
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.matchParentSize()) {
            val c = charge.value
            val r = 14.dp.toPx()

            // (1) Relleno que avanza de izquierda a derecha por debajo del rótulo.
            if (c > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.30f),
                    size = androidx.compose.ui.geometry.Size(size.width * c, size.height),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            }

            // (2) Anillo que recorre el perímetro. El segmento se recorta del contorno real del
            //     botón, así la luz sigue las esquinas redondeadas en vez de cortar por la caja.
            if (c > 0f) {
                ringPath.reset()
                ringPath.addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        left = 1.5f, top = 1.5f,
                        right = size.width - 1.5f, bottom = size.height - 1.5f,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                    )
                )
                measure.setPath(ringPath, false)
                val total = measure.length
                segPath.reset()
                measure.getSegment(0f, total * c, segPath, true)
                drawPath(segPath, Color.White.copy(alpha = 0.35f), style = Stroke(width = 9f, cap = StrokeCap.Round))
                drawPath(segPath, Color.White.copy(alpha = 0.95f), style = Stroke(width = 3.4f, cap = StrokeCap.Round))

                // Chispa en la cabeza del anillo (dónde va la carga ahora mismo).
                val pos = measure.getPosition(total * c)
                drawCircle(Color.White.copy(alpha = 0.85f), radius = 7f, center = pos)
                drawCircle(accent.copy(alpha = 0.55f), radius = 13f, center = pos)
            }

            // (3) Fogonazo al completar.
            val f = flash.value
            if (f > 0f) {
                drawRoundRect(
                    color = Color.White.copy(alpha = (0.85f * f).coerceIn(0f, 1f)),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r)
                )
            }
        }
        Text(
            label,
            color = Color(0xFF1A1208),
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Pastilla con la rareza de la habilidad. */
@Composable
private fun RarityPill(rarity: AbilityRarity) {
    val color = Color(rarity.colorArgb)
    Box(
        Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            rarity.label.uppercase(),
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp
        )
    }
}
