package com.cukiti.teikafusion.ui

import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.BlockDiscovery
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.Position
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * **LA VITRINA DE BLOQUES** (P-250, 2026-09-23, decisión del usuario). Sustituye a la sección BLOQUES de
 * la tienda —que queda para el dinero real— y se abre desde el acceso rápido de la cabecera del mapa
 * (el de Inicio se retiró el 2026-09-23).
 * Estilo sacado de `art/ilustracion.png`: marco oscuro, tarjetas azules, candado.
 *
 * Cada tarjeta está en uno de cinco estados, y **el mismo botón** de abajo cambia de palabra sin
 * recolocar nada (la lección del Inventario):
 *  - **EN USO** / **TUYO** (equipar);
 *  - **DESCUBIERTO**: se ve animado y lleva precio;
 *  - **POR DESCUBRIR**: silueta con candado y la pista («Completa el Glaciar»);
 *  - **PRÓXIMAMENTE**: bloque de bioma que el juego todavía no trae (P-251 se hace por tandas).
 *
 * Al tocar una tarjeta, EL ESCENARIO de arriba enseña ESE bloque en un mini-tablero que rompe su
 * fila del medio en bucle con la rotura de verdad del juego (`BatchLineCellShatterVFX` con el bloque
 * puesto): lo que se compra es cómo se ve y cómo rompe, así que eso es lo que se enseña.
 *
 * **Sin scroll** (2026-09-23): escenario arriba, carrusel de una fila abajo y los «próximamente»
 * juntos en una tarjeta «+N». Todo cabe en la pantalla nada más abrir.
 *
 * ⛔ No es un `Dialog`: es una capa más del `Box` de `AppNavigation` (ver `ImmersiveWindowContractTest`).
 */
@Composable
fun BlockShowcaseOverlay(
    teikoins: Int,
    maxUnlockedLevel: Int,
    unlockedCosmetics: Set<String>,
    activeCosmetic: String?,
    animationsOn: Boolean,
    /** Compra. Devuelve si ocurrió de verdad: solo entonces el botón celebra con monedas. */
    onBuy: (String) -> Boolean,
    onEquip: (String) -> Unit,
    onUiClick: () -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }
    val scale = remember { Animatable(0.9f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium)) }
    // El destello del Cristal y las demás caras animadas corren con el reloj compartido del tablero.
    BiomeClockDriver(active = animationsOn)

    val equippedId = activeCosmetic?.takeIf { id -> BlockSkins.all.any { it.id == id } } ?: BlockSkins.DEFAULT_ID
    val entries = remember(maxUnlockedLevel, unlockedCosmetics, equippedId) {
        showcaseEntries(maxUnlockedLevel, unlockedCosmetics, equippedId)
    }
    var selectedId by remember { mutableStateOf(equippedId) }
    val carousel = remember(entries) { carouselEntries(entries) }
    val selected = carousel.firstOrNull { it.id == selectedId } ?: carousel.first()

    CoinBurstHost(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.78f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
    ) {
        // SIN SCROLL (2026-09-23, pedido del usuario: «que vea todo en pantalla desde que le da
        // click»). Antes eran nueve tarjetas en tres filas + la demo debajo, y la demo —lo que al
        // usuario le gustaba— quedaba bajo el pliegue. Ahora el orden se invierte:
        //  1. EL ESCENARIO arriba: la demo de rotura en grande, con su nombre, su pista y el botón.
        //  2. EL CARRUSEL abajo: UNA fila que se desliza de lado, y los «próximamente» juntos en una
        //     sola tarjeta «+N» (seis candados iguales eran ruido, no información).
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .safeDrawingPadding()
                .padding(horizontal = 14.dp, vertical = 16.dp)
                // La escala se lee en el DIBUJO (no en composición): el rebote de entrada no recompone
                // la vitrina entera en cada fotograma.
                .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .pointerInput(Unit) { detectTapGestures { } }
                // La rotura de la Cerámica deja caer los bloques «fuera de la pantalla»: en la vitrina
                // tienen que perderse en el borde de la ventana, no cruzar el mapa de detrás (auditoría
                // 2026-10-01, visto en el A15).
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.verticalGradient(listOf(ShowcaseRim, ShowcaseRimDark)), RoundedCornerShape(26.dp))
                .padding(2.5.dp)
                .background(Brush.verticalGradient(listOf(ShowcaseTop, ShowcaseBottom)), RoundedCornerShape(24.dp))
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ShowcaseHeader(teikoins = teikoins, onDismiss = { onUiClick(); onDismiss() })

            ShowcaseDetail(
                entry = selected,
                teikoins = teikoins,
                animationsOn = animationsOn,
                onBuy = onBuy,
                onEquip = onEquip,
            )

            val startIndex = remember { carousel.indexOfFirst { it.id == equippedId }.coerceAtLeast(0) }
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
            // P-428: el equipado abre CENTRADO, con tarjetas asomando a los dos lados: pegado a la
            // izquierda no se veía que había bloques antes. Se mide la fila primero y se recoloca una vez.
            val density = LocalDensity.current
            LaunchedEffect(Unit) {
                val viewport = snapshotFlow { listState.layoutInfo.viewportSize.width }.first { it > 0 }
                val tilePx = with(density) { SHOWCASE_TILE_WIDTH.toPx() }
                listState.scrollToItem(startIndex)
                listState.scrollBy(-(viewport - tilePx) / 2f)
            }
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp, vertical = 6.dp),
            ) {
                items(carousel, key = { it.id }) { entry ->
                    ShowcaseTile(
                        entry = entry,
                        selected = entry.id == selected.id,
                        modifier = Modifier.width(SHOWCASE_TILE_WIDTH),
                        onClick = { onUiClick(); selectedId = entry.id },
                    )
                }
            }
        }
    }
}

/** Ancho de una tarjeta del carrusel (también lo usa el centrado inicial, P-428). */
private val SHOWCASE_TILE_WIDTH = 84.dp

/** Id de la tarjeta que agrupa todos los bloques «próximamente» del carrusel. */
private const val SOON_GROUP_ID = "soon-group"

/**
 * Las entradas del carrusel: todas las de [showcaseEntries] menos las PRÓXIMAMENTE, que se funden en
 * una sola tarjeta al final con su recuento y la lista de dónde llegarán.
 */
internal fun carouselEntries(entries: List<ShowcaseEntry>): List<ShowcaseEntry> {
    val soon = entries.filter { it.state == ShowcaseState.SOON }
    if (soon.isEmpty()) return entries
    val group = ShowcaseEntry(
        id = SOON_GROUP_ID,
        name = "+${soon.size}",
        skin = null,
        state = ShowcaseState.SOON,
        hint = "${soon.size} bloques más llegarán con sus mundos: " + soon.joinToString(", ") { it.name } + ".",
    )
    return entries.filter { it.state != ShowcaseState.SOON } + group
}

/** Estado de una tarjeta de la vitrina. El orden es el de la «distancia» a poder usarla. */
internal enum class ShowcaseState { EQUIPPED, OWNED, FOR_SALE, LOCKED, SOON }

internal data class ShowcaseEntry(
    val id: String,
    val name: String,
    val skin: BlockSkin?,
    val state: ShowcaseState,
    val hint: String,
)

/**
 * Las tarjetas, en orden de vitrina: el Básico, la Cerámica, el Metal, el Neón y el Dorado (de pago, sin
 * bioma) y los siete de bioma en el orden del viaje. Pura sobre sus entradas para poder probarla sin pantalla.
 *
 * (2026-09-27) El METAL (P-285) no estaba en esta lista: existía como skin, con precio y sonido, pero
 * como no es de bioma no salía por `BlockDiscovery.slots` y nadie lo había añadido a los fijos — la
 * vitrina no lo enseñaba y no se podía comprar. Todo bloque de `BlockSkins.all` tiene que tener tarjeta.
 */
internal fun showcaseEntries(maxUnlockedLevel: Int, owned: Set<String>, equippedId: String): List<ShowcaseEntry> {
    fun stateOf(skin: BlockSkin): ShowcaseState = when {
        skin.id == equippedId -> ShowcaseState.EQUIPPED
        skin.priceTeikoins == 0 || skin.id in owned -> ShowcaseState.OWNED
        BlockDiscovery.isDiscovered(skin.id, maxUnlockedLevel) -> ShowcaseState.FOR_SALE
        else -> ShowcaseState.LOCKED
    }
    val fixed = listOf(BlockSkins.classico, BlockSkins.fall3d, BlockSkins.metal, BlockSkins.neon, BlockSkins.dorado).map {
        ShowcaseEntry(it.id, it.name, it, stateOf(it), it.description)
    }
    val biome = BlockDiscovery.slots.map { slot ->
        val skin = BlockSkins.all.firstOrNull { it.id == slot.skinId }
        if (skin == null) {
            ShowcaseEntry(slot.skinId, slot.name, null, ShowcaseState.SOON, "Llegará con ${slot.where}")
        } else {
            val st = stateOf(skin)
            ShowcaseEntry(skin.id, skin.name, skin, st, if (st == ShowcaseState.LOCKED) BlockDiscovery.hint(slot) else skin.description)
        }
    }
    return fixed + biome
}

@Composable
private fun ShowcaseHeader(teikoins: Int, onDismiss: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
            RawImage(R.raw.ic_block_skin, Modifier.size(34.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "BLOQUES", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black,
            letterSpacing = 1.5.sp, modifier = Modifier.weight(1f)
        )
        // Cuánto tienes, al lado de lo que cuesta: comprar a ciegas es la mitad de una compra mala.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                .padding(horizontal = 9.dp, vertical = 5.dp)
                .semantics(mergeDescendants = true) { contentDescription = "$teikoins teikoins" }
        ) {
            TeikoinIcon(size = 20.dp)
            Spacer(Modifier.width(4.dp))
            // El formato único de cifras (P-419): con millones, la cifra entera empujaba el título.
            Text(com.cukiti.teikafusion.model.AmountFormat.compact(teikoins), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(role = Role.Button) { onDismiss() }
                .semantics { contentDescription = "Cerrar" },
            contentAlignment = Alignment.Center
        ) { RawImage(R.raw.ic_close, Modifier.size(24.dp)) }
    }
}

@Composable
private fun ShowcaseTile(entry: ShowcaseEntry, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val border = when {
        selected -> ShowcaseSelect
        entry.state == ShowcaseState.EQUIPPED -> ShowcaseGreen.copy(alpha = 0.8f)
        entry.state == ShowcaseState.FOR_SALE -> ShowcaseGold.copy(alpha = 0.8f)
        else -> ShowcaseTileEdge
    }
    val dim = entry.state == ShowcaseState.LOCKED || entry.state == ShowcaseState.SOON
    val lift by animateFloatAsState(if (selected) 1f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "tileLift")
    Column(
        modifier = modifier
            .graphicsLayer {
                // La elegida sube y crece un poco: en un carrusel, la selección tiene que verse de lejos.
                val sc = 1f + 0.06f * lift
                scaleX = sc; scaleY = sc
                translationY = -4.dp.toPx() * lift
            }
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.verticalGradient(listOf(ShowcaseTileTop, ShowcaseTileBottom)))
            .border(if (selected) 2.5.dp else 1.5.dp, border, RoundedCornerShape(16.dp))
            .clickable(role = Role.Button) { onClick() }
            .semantics(mergeDescendants = true) {
                // La tarjeta «+N» se leía «más 6»: que diga qué son.
                contentDescription = if (entry.id == SOON_GROUP_ID) {
                    "${entry.name.removePrefix("+")} bloques más. ${stateWord(entry.state)}"
                } else {
                    "${entry.name}. ${stateWord(entry.state)}"
                }
            }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
            val skin = entry.skin
            when {
                skin != null -> MiniGemGrid(skin, cell = 21.dp, dim = dim)
                // La tarjeta de los próximamente: su recuento, grande.
                entry.id == SOON_GROUP_ID -> Text(
                    entry.name, color = Color.White.copy(alpha = 0.6f), fontSize = 22.sp, fontWeight = FontWeight.Black
                )
                else -> Box(
                    Modifier.size(40.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.07f))
                        .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                )
            }
            if (dim) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(20.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(50)),
                    contentAlignment = Alignment.Center
                ) { RawImage(R.raw.ic_lock, Modifier.size(12.dp)) }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            if (entry.id == SOON_GROUP_ID) "Más" else entry.name,
            color = if (dim) Color.White.copy(alpha = 0.55f) else Color.White,
            fontSize = 12.sp, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            // En la tarjeta pequeña «PRÓXIMAMENTE» no cabe: se corta. «PRONTO» dice lo mismo.
            if (entry.state == ShowcaseState.SOON) "PRONTO" else stateWord(entry.state),
            color = stateColor(entry.state), fontSize = 8.sp,
            fontWeight = FontWeight.Bold, maxLines = 1, letterSpacing = 0.4.sp
        )
    }
}

private fun stateWord(s: ShowcaseState) = when (s) {
    ShowcaseState.EQUIPPED -> "EN USO"
    ShowcaseState.OWNED -> "TUYO"
    ShowcaseState.FOR_SALE -> "DESCUBIERTO"
    ShowcaseState.LOCKED -> "POR DESCUBRIR"
    ShowcaseState.SOON -> "PRÓXIMAMENTE"
}

private fun stateColor(s: ShowcaseState) = when (s) {
    ShowcaseState.EQUIPPED -> ShowcaseGreen
    ShowcaseState.OWNED -> ShowcaseSelect
    ShowcaseState.FOR_SALE -> ShowcaseGold
    ShowcaseState.LOCKED, ShowcaseState.SOON -> Color.White.copy(alpha = 0.45f)
}

/** 2×2 bloques del skin con cuatro de sus colores: la paleta y la cara de un vistazo. */
@Composable
private fun MiniGemGrid(skin: BlockSkin, cell: Dp, dim: Boolean) {
    Column(Modifier.alpha(if (dim) 0.35f else 1f)) {
        for (y in 0 until 2) Row {
            for (x in 0 until 2) {
                val c = skin.colorsFor(listOf(0, 3, 2, 4)[y * 2 + x])
                Box(Modifier.size(cell).styledTile(skin.style, c[0], c[1], c[2], skin.cornerFraction, glintPhase = (x + y).toFloat()))
            }
        }
    }
}

/** Celda de la demo de rotura. Fija: el escenario mide siempre lo mismo, elijas el bloque que elijas. */
private val DEMO_CELL = 40.dp

/**
 * **EL ESCENARIO**: arriba del todo y con foco de luz detrás. Mide SIEMPRE lo mismo —demo, nombre,
 * dos líneas de pista y botón—, de modo que tocar otra tarjeta cambia lo que se ve pero no mueve el
 * carrusel de sitio bajo el dedo.
 */
@Composable
private fun ShowcaseDetail(
    entry: ShowcaseEntry,
    teikoins: Int,
    animationsOn: Boolean,
    onBuy: (String) -> Boolean,
    onEquip: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.28f), RoundedCornerShape(18.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val skin = entry.skin
        val visible = skin != null && entry.state != ShowcaseState.LOCKED
        Box(
            Modifier
                .fillMaxWidth()
                .height(DEMO_CELL * 3 + 16.dp)
                .drawBehind {
                    // Foco: la vitrina ilumina lo que enseña.
                    drawCircle(
                        Brush.radialGradient(
                            listOf(ShowcaseSelect.copy(alpha = 0.28f), Color.Transparent),
                            center = center, radius = size.height * 1.1f
                        ),
                        radius = size.height * 1.1f
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            if (visible && skin != null) {
                key(skin.id) { ShatterDemo(skin, animationsOn) }
            } else {
                // Lo que aún no tienes a la vista: su silueta con el candado, del mismo tamaño.
                Box(
                    Modifier.size(DEMO_CELL * 4, DEMO_CELL * 3).clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.5.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier.size(52.dp).background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) { RawImage(R.raw.ic_lock, Modifier.size(30.dp)) }
                }
            }
        }
        // ALTO FIJO (2026-09-24, reporte del usuario: «cuando selecciono un bloque cambia de tamaño la
        // ventana»). La etiqueta de estado es algo más alta que el nombre, y ocultarla con el bloque
        // en uso hacía crecer la ventana 6 dp al elegir otro. Ahora ocupa SIEMPRE su sitio.
        Row(
            modifier = Modifier.height(28.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                if (entry.id == SOON_GROUP_ID) "Próximamente" else entry.name,
                color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Black
            )
            // Equipado ya lo dice la marca verde de abajo: aquí sería decirlo dos veces. Se vuelve
            // invisible, pero NO se quita (ver el alto fijo de la fila).
            Text(
                stateWord(entry.state), color = stateColor(entry.state), fontSize = 10.sp, fontWeight = FontWeight.Black,
                letterSpacing = 0.5.sp,
                modifier = Modifier
                    .alpha(if (entry.state == ShowcaseState.EQUIPPED) 0f else 1f)
                    .background(stateColor(entry.state).copy(alpha = 0.14f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            )
        }
        // Dos líneas fijas: una pista corta y una larga ocupan lo mismo.
        Text(
            entry.hint, color = Color.White.copy(alpha = 0.72f), fontSize = 12.sp, fontWeight = FontWeight.Bold,
            lineHeight = 16.sp, textAlign = TextAlign.Center, maxLines = 2, minLines = 2,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth()
        )
        Box(Modifier.height(44.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (entry.state == ShowcaseState.EQUIPPED) {
                // El que llevas puesto no se compra ni se equipa: una marca, no un botón apagado
                // (el gris de un botón deshabilitado se leía como «no puedes»).
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(ShowcaseGreen.copy(alpha = 0.16f), RoundedCornerShape(50))
                        .border(1.5.dp, ShowcaseGreen.copy(alpha = 0.7f), RoundedCornerShape(50))
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    RawImage(R.raw.ic_check, Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("EN USO", color = ShowcaseGreen, fontSize = 15.sp, fontWeight = FontWeight.Black)
                }
            } else if (visible && skin != null) {
                val price = skin.priceTeikoins
                val canAfford = teikoins >= price
                // El MISMO botón en los tres estados: nada se recoloca al comprar ni al equipar.
                PriceButton(
                    price = price,
                    label = when (entry.state) {
                        ShowcaseState.EQUIPPED -> "EN USO"
                        ShowcaseState.OWNED -> "EQUIPAR"
                        else -> if (canAfford) null else "FALTAN ${price - teikoins}"
                    },
                    tone = when (entry.state) {
                        ShowcaseState.EQUIPPED -> ArcadeTone.GREEN
                        ShowcaseState.OWNED -> ArcadeTone.BLUE
                        else -> ArcadeTone.GOLD
                    },
                    modifier = Modifier.width(170.dp),
                    enabled = entry.state == ShowcaseState.OWNED || (entry.state == ShowcaseState.FOR_SALE && canAfford),
                    height = 40.dp,
                    fontSize = 15.sp,
                    // «Faltan N» sigue siendo un precio (P-209: un precio que no sabes de cuánto te
                    // queda es una pared): lleva su moneda aunque el botón esté apagado.
                    showCoin = entry.state == ShowcaseState.FOR_SALE,
                    contentDescription = when (entry.state) {
                        ShowcaseState.EQUIPPED -> "${skin.name} está en uso"
                        ShowcaseState.OWNED -> "Equipar el bloque ${skin.name}"
                        else -> if (canAfford) "Comprar el bloque ${skin.name} por $price teikoins"
                        else "El bloque ${skin.name} cuesta $price teikoins. Te faltan ${price - teikoins}"
                    },
                    onBuy = {
                        if (entry.state == ShowcaseState.OWNED) { onEquip(skin.id); false }
                        else onBuy(skin.id)
                    },
                )
            }
        }
    }
}

/**
 * Un tablerito de 4×3 con el bloque puesto que rompe su fila del medio en bucle, con la rotura REAL
 * del juego, y la vuelve a colocar con su firma de colocación (P-283). Sin vibración: es un escaparate.
 * Con «reducir efectos» se queda quieto: se ve la cara, no el espectáculo.
 */
@Composable
private fun ShatterDemo(skin: BlockSkin, animationsOn: Boolean) {
    val cell = DEMO_CELL
    val cols = 4; val rows = 3
    val events = remember { mutableStateListOf<GameEvent.LineCellShatter>() }
    // P-283: al volver, la fila ATERRIZA con la firma del bloque (la misma que al colocar en partida).
    val placed = remember { mutableStateListOf<GameEvent.PiecePlacedPop>() }
    var rowBroken by remember { mutableStateOf(false) }
    if (animationsOn) {
        LaunchedEffect(Unit) {
            delay(700)
            while (true) {
                rowBroken = true
                (0 until cols).forEach { x ->
                    events += GameEvent.LineCellShatter(position = Position(x, 1), colorType = (x + 1) % 5, delayMs = x * 70)
                }
                delay(1500)
                rowBroken = false
                placed += GameEvent.PiecePlacedPop(cells = (0 until cols).map { Position(it, 1) }, colorType = 3)
                delay(1700)
            }
        }
    }
    ProvideBlockSkin(activeCosmetic = skin.id) {
        Box(Modifier.size(cell * cols, cell * rows)) {
            for (y in 0 until rows) for (x in 0 until cols) {
                if (y == 1 && rowBroken) continue
                val c = skin.colorsFor((x * 2 + y * 3) % 5)
                Box(
                    Modifier.offset(cell * x, cell * y).size(cell)
                        .styledTile(skin.style, c[0], c[1], c[2], skin.cornerFraction, glintPhase = (x + y).toFloat())
                )
            }
            if (events.isNotEmpty()) {
                BatchLineCellShatterVFX(
                    events = events.toList(),
                    cellSize = cell,
                    onEventComplete = { id -> events.removeAll { it.id == id } },
                    haptics = false,
                )
            }
            if (placed.isNotEmpty()) {
                BatchPlacePopVFX(
                    events = placed.toList(),
                    cellSize = cell,
                    onEventComplete = { id -> placed.removeAll { it.id == id } }
                )
            }
        }
    }
}

/**
 * **El acceso rápido** (P-250): un botón con TU bloque equipado dibujado dentro —no un icono
 * genérico: el botón ya enseña lo que abre—. Vive en Inicio (bajo el avatar, flotando, sin empujar la
 * barra superior, que ya va justa) y en la cabecera del mapa.
 */
@Composable
fun BlockShowcaseButton(
    activeCosmetic: String?,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** P-429: hay un bloque DESCUBIERTO que ya puedes pagar ([hasAffordableBlock]). */
    badge: Boolean = false,
) {
    val skin = BlockSkins.byId(activeCosmetic)
    // La insignia sobresale de la esquina: va fuera del recorte del botón.
    Box(modifier.size(size)) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(ShowcaseTileTop, ShowcaseTileBottom)))
            .border(1.5.dp, ShowcaseSelect.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button
            ) { onClick() }
            .semantics {
                contentDescription = if (badge) "Bloques. Hay uno nuevo que puedes comprar" else "Bloques"
            },
        contentAlignment = Alignment.Center
    ) {
        val c = skin.colorsFor(3)
        Box(Modifier.size(size * 0.58f).styledTile(skin.style, c[0], c[1], c[2], skin.cornerFraction))
    }
    if (badge) {
        // Una moneda: «aquí hay algo que puedes comprar». (La flecha verde es para «mejorar».)
        Box(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)) { TeikoinIcon(size = 18.dp) }
    }
    }
}

/**
 * P-429: ¿hay en la vitrina un bloque DESCUBIERTO (a la venta) que ya te llega? Es la pregunta de la
 * moneda del botón de la vitrina; usa [showcaseEntries] para que botón y vitrina no discrepen.
 */
internal fun hasAffordableBlock(teikoins: Int, maxUnlockedLevel: Int, owned: Set<String>, equippedId: String): Boolean =
    showcaseEntries(maxUnlockedLevel, owned, equippedId).any {
        it.state == ShowcaseState.FOR_SALE && it.skin != null && teikoins >= it.skin.priceTeikoins
    }

private val ShowcaseRim = Color(0xFF3E6BD6)
private val ShowcaseRimDark = Color(0xFF1C2F6B)
private val ShowcaseTop = Color(0xFF1B2446)
private val ShowcaseBottom = Color(0xFF0E1430)
private val ShowcaseTileTop = Color(0xFF263262)
private val ShowcaseTileBottom = Color(0xFF161D3C)
private val ShowcaseTileEdge = Color(0xFF34427A)
private val ShowcaseSelect = Color(0xFF4DB8FF)
private val ShowcaseGreen = Color(0xFF6BE08A)
private val ShowcaseGold = Color(0xFFFFD764)
