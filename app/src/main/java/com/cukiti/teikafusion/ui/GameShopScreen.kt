package com.cukiti.teikafusion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.billingclient.api.ProductDetails
import com.cukiti.teikafusion.BillingManager
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.StorePacks

/**
 * **LA TIENDA** — reabierta y rediseñada el 2026-09-06 (decisión del usuario).
 *
 * ## Lo que cambia, y por qué queda escrito
 *
 * La pestaña de teikoins se apagó el 2026-08-01 y la decisión de economía del 2026-08-09 («sin
 * tienda de teikoins; los anuncios son la fuente») la respaldaba. **El usuario la reabre**, con los
 * cuatro productos que ya existían en Play Console más el **paquete inicial**. No es un trámite
 * pendiente que por fin se completa: es un cambio de rumbo, y se anota igual de claro que se anotó
 * cuando se cerró.
 *
 * Y **las vidas se han ido de aquí** ([LivesOverlay]). Vivían en una pestaña «Recargas» mezcladas
 * con los bloques, que es el sitio equivocado por dos motivos: quien se queda sin vidas mira el
 * corazón de Inicio, no el carrito, y seis tarjetas apiladas obligaban a desplazarse justo cuando
 * el jugador tiene prisa. Con ellas fuera, la tienda deja de necesitar pestañas: **es una sola
 * pantalla**, y una fila de pestañas de un solo botón no informaba de nada.
 *
 * ## La gramática
 *
 * Es la de la «Vitrina» del Inventario (2026-09-04) — era **P-181** y estaba anotada esperando a que
 * el usuario viera aquella pantalla en el móvil. Todo lo que se pulsa tiene canto ([ArcadeButton]);
 * ninguna cantidad va suelta (píldora con su icono); halo radial detrás del arte; rótulos de sección
 * centrados entre filetes; **ningún botón desaparece al agotarse su función** —se queda apagado con
 * otra palabra—; y **la compra con teikoins se celebra** con la misma lluvia de monedas que la hoja
 * de habilidades ([CoinBurstLayer]).
 *
 * ## Lo que NO se ha reintroducido
 *
 * Los brillos infinitos que se retiraron el 2026-07-28 (latidos de borde, barridos de luz, aureolas
 * pulsando). Aquello eran ~10 animaciones simultáneas repintando degradados y el scroll iba a
 * tirones. Lo de aquí es **respuesta al toque**: el hundido del botón y las monedas de la compra,
 * que empiezan y terminan. La tienda en reposo sigue sin pedir un solo fotograma.
 */
@Composable
fun GameShopScreen(
    teikoins: Int,
    // (P-250, 2026-09-23) Los BLOQUES salieron de aquí a su vitrina (`BlockShowcaseOverlay`). P-430
    // (2026-10-01): la tienda vuelve a tener una puerta a ella —«ASPECTOS»—, porque es donde el
    // jugador los busca; la vitrina sigue siendo una sola (también se abre desde el mapa y el Inventario).
    /**
     * Comprar con dinero real exige cuenta respaldada: sin ella el saldo no sobrevive a un cambio de
     * móvil.
     */
    isCloudAccount: Boolean = true,
    adsRemoved: Boolean = false,
    /** El paquete inicial ya está entregado: la tarjeta pasa a «YA ES TUYO». */
    starterPackGranted: Boolean = false,
    /** Packs cuya primera compra ya cobró el bono del 100 %: sin sello «×2». */
    coinBonusClaimed: Set<String> = emptySet(),
    onConnectGoogle: () -> Unit = {},
    onUiClick: () -> Unit = {},
    /** P-430: el bloque equipado, la moneda de «puedes comprar uno» y cómo abrir la vitrina. */
    activeCosmetic: String? = null,
    blocksBadge: Boolean = false,
    onOpenBlocks: () -> Unit = {},
    modifier: Modifier = Modifier,
    active: Boolean = true
) {
    val activity = LocalActivity.current
    val products by BillingManager.products.collectAsState()

    // Sin lluvia de teikoins: aquí solo se compra con dinero real, que ya tiene su ceremonia (el
    // reveal épico). La lluvia se fue con los bloques a su vitrina (P-250).
    Box(modifier = modifier.fillMaxSize()) {
        val ambientPhase = rememberAmbientPhase(active)
        PremiumBackground(
            phase = { ambientPhase.value },
            accent = ShopNeon,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            ShopTopBar(teikoins)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val starter = products.firstOrNull { it.productId == StorePacks.STARTER_PACK_ID }
                val coinProducts = StorePacks.COIN_PACKS
                    .mapNotNull { pack -> products.firstOrNull { it.productId == pack.productId }?.let { pack to it } }
                val removeAds = products.firstOrNull { it.productId == BillingManager.REMOVE_ADS }

                // ── SIN CUENTA RESPALDADA: el aviso va ARRIBA y una sola vez ─────────────────
                // (Auditoría 2026-10-01.) Vivía dentro de TEIKOINS, así que el paquete inicial y
                // Quitar anuncios —encima y debajo— salían con el botón apagado sin decir por qué, y
                // si Play no servía las monedas no había aviso en ninguna parte.
                val algoQueComprar = (starter != null && !starterPackGranted) ||
                    coinProducts.isNotEmpty() || (removeAds != null && !adsRemoved)
                if (!isCloudAccount && algoQueComprar) {
                    CloudBackupPrompt(
                        title = "Respalda tu cuenta para comprar",
                        subtitle = "Lo que compres se guarda en tu cuenta. " +
                            "Conéctala y no perderás nada al cambiar de móvil.",
                        onConnectGoogle = { onUiClick(); onConnectGoogle() },
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // ── EL PAQUETE INICIAL ───────────────────────────────────────────────────────
                if (starter != null || starterPackGranted) {
                    SectionRule("PAQUETE INICIAL")
                    StarterPackCard(
                        product = starter,
                        granted = starterPackGranted,
                        enabled = isCloudAccount,
                        onBuy = { activity?.let { BillingManager.purchase(it, starter!!) } },
                    )
                }

                // ── TEIKOINS (dinero real) ───────────────────────────────────────────────────
                // Sin cuenta se VEN (qué hay y cuánto cuesta) con el botón apagado, como las demás
                // tarjetas: el aviso de arriba dice por qué.
                if (coinProducts.isNotEmpty()) {
                    SectionRule("TEIKOINS")
                    coinProducts.forEach { (pack, product) ->
                        CoinPackCard(
                            pack = pack,
                            product = product,
                            bonusAvailable = StorePacks.bonusAvailable(pack.productId, coinBonusClaimed),
                            enabled = isCloudAccount,
                            onBuy = { activity?.let { BillingManager.purchase(it, product) } },
                        )
                    }
                }

                // P-221 · EL HUECO SE EXPLICA. `GameShopScreen` pinta EXACTAMENTE lo que Play
                // devuelve, así que un producto que no llega no da error ni deja hueco: la sección
                // entera desaparece y el jugador cree que el juego está roto (le pasó al propio
                // usuario el 2026-09-07). Una línea sobria, sin dramatismo y sin botón: no se puede
                // hacer nada al respecto desde aquí, solo saber que no es cosa suya.
                if (coinProducts.isEmpty() && starter == null && !starterPackGranted) {
                    SectionRule("TEIKOINS")
                    Text(
                        "Las compras no están disponibles ahora mismo.",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 17.sp,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                }

                // ── ASPECTOS (P-430): la puerta a la vitrina de bloques ─────────────────────
                SectionRule("ASPECTOS")
                BlocksDoorCard(
                    activeCosmetic = activeCosmetic,
                    badge = blocksBadge,
                    onOpen = onOpenBlocks,
                )

                // ── SIN ANUNCIOS ─────────────────────────────────────────────────────────────
                if (removeAds != null || adsRemoved) {
                    SectionRule("SIN ANUNCIOS")
                    RemoveAdsCard(
                        product = removeAds,
                        comprado = adsRemoved,
                        enabled = isCloudAccount,
                        onBuy = { activity?.let { BillingManager.purchase(it, removeAds!!) } },
                    )
                }

                // El navbar se dibuja encima: sin este hueco tapa la última tarjeta.
                Spacer(Modifier.height(NavBarHeight + 12.dp))
            }
        }
    }
}

// ── Paleta ───────────────────────────────────────────────────────────────────────────────────

private val ShopGold = Color(0xFFFFC93C)
private val ShopGoldDeep = Color(0xFFE2A116)
private val ShopNeon = Color(0xFF4DB8FF)
private val ShopPurple = Color(0xFFBB6EE8)
private val ShopGreen = Color(0xFF3ED598)
private val CardTop = Color(0xFF243063)
private val CardBottom = Color(0xFF141A33)

// ── Cabecera ─────────────────────────────────────────────────────────────────────────────────

@Composable
private fun ShopTopBar(teikoins: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        RawImage(
            resId = R.raw.tiendatitulo,
            contentDescription = "Tienda",
            modifier = Modifier.height(60.dp)
        )
        // Ninguna cantidad va suelta (regla 1 de la vitrina): el saldo vive en su píldora con su
        // icono. El icono es mudo, así que la descripción va en el chip entero.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.horizontalGradient(listOf(ShopGold.copy(alpha = 0.18f), Color.Black.copy(alpha = 0.4f))))
                .border(1.4.dp, ShopGold.copy(alpha = 0.65f), RoundedCornerShape(16.dp))
                .padding(horizontal = 13.dp, vertical = 8.dp)
                .clearAndSetSemantics { contentDescription = "Saldo: $teikoins teikoins" }
        ) {
            RawImage(R.raw.teikoin, Modifier.size(19.dp))
            // El formato único de cifras (P-419): con cifras grandes empujaba el rótulo de la tienda.
            Text(
                com.cukiti.teikafusion.model.AmountFormat.compact(teikoins),
                fontSize = 15.sp, fontWeight = FontWeight.Black, color = ShopGold
            )
        }
    }
}

// ── Piezas comunes ───────────────────────────────────────────────────────────────────────────

/**
 * El armazón de toda tarjeta de la tienda: marco, degradado y el **banderín del nombre montado sobre
 * el borde superior**, que es el rasgo que define la vitrina (regla 2). El banderín necesita salirse
 * de la caja, así que la tarjeta reserva su medio alto arriba con `padding` en vez de dejar que lo
 * recorte el `clip`.
 */
@Composable
private fun ShopCard(
    title: String,
    accent: Color,
    modifier: Modifier = Modifier,
    badge: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth().padding(top = 11.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
                .border(1.4.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(18.dp))
                .padding(start = 14.dp, end = 14.dp, top = 16.dp, bottom = 12.dp),
            content = content,
        )
        // El banderín se acota al 62 % del ancho: con el sello de oferta en la esquina, un título
        // largo se le echaría encima. Recortar con puntos suspensivos es mejor que solapar dos
        // rótulos — el nombre del producto se lee igual en el cuerpo de la tarjeta.
        NameBanner(
            name = title,
            accent = accent,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // TOPE, no ancho fijo: con `fillMaxWidth` una cinta que dice «Chisel» mediría lo
                // mismo que una que dice «Paquete inicial», y el banderín se lee mal cuando sobra
                // cinta a los lados del nombre. Así crece con el texto y se corta antes de llegar al
                // sello de oferta.
                .widthIn(max = 190.dp)
                .offset(y = (-11).dp)
        )
        // El sello de oferta va en la esquina, montado sobre el borde como el banderín: es un
        // segundo rótulo, no un texto dentro del cuerpo.
        badge?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = (-9).dp, x = (-6).dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Brush.horizontalGradient(listOf(ShopGold, ShopGoldDeep)))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(it, color = Color(0xFF3A2400), fontSize = 10.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

/**
 * **NINGÚN TEXTO DE LA TIENDA SE CORTA** (2026-09-06, reporte del usuario: «hay textos que se
 * cortan y se colocan puntos, no se ve completa la información»).
 *
 * Tenía razón y la causa era estructural, no un `maxLines` mal puesto. Las tarjetas metían el arte,
 * **el texto** y el botón en la MISMA fila, así que al texto le quedaba lo que sobraba: con un
 * preview de 90 dp y un botón de 104, la descripción de un bloque se quedaba en ~130 dp de ancho.
 * Ahí no cabe «Ficha mate limpia; al romper, los bloques se desprenden y caen fuera de la pantalla»
 * ni con tres líneas, así que salía recortada con puntos suspensivos — y en una tienda el texto
 * cortado es justo la información por la que el jugador decide si paga.
 *
 * La regla que siguen ahora las cuatro tarjetas:
 *
 *  1. **Arriba, una fila con lo que tiene tamaño fijo**: el arte y el botón.
 *  2. **Debajo, el texto a ANCHO COMPLETO de la tarjeta**, sin `maxLines` y sin `Ellipsis`.
 *
 * Una tarjeta crece hacia abajo si su texto es largo, que es lo que debe pasar: la tienda scrollea.
 * Lo que no puede pasar es que una frase se corte en un sitio donde se decide una compra.
 */

/** Una cantidad con su icono. Regla 1: ninguna cifra vive suelta sobre el fondo. */
@Composable
private fun CoinPill(amount: Int, accent: Color = ShopGold, big: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.16f))
            .border(1.dp, accent.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
            .clearAndSetSemantics { contentDescription = "$amount teikoins" }
    ) {
        RawImage(R.raw.teikoin, Modifier.size(if (big) 18.dp else 14.dp))
        Text(
            com.cukiti.teikafusion.model.AmountFormat.full(amount),
            color = accent,
            fontSize = if (big) 17.sp else 13.sp,
            fontWeight = FontWeight.Black
        )
    }
}

/** El precio que pone Play, o un guion si el catálogo todavía no ha llegado. */
private fun ProductDetails?.priceLabel(): String =
    this?.oneTimePurchaseOfferDetails?.formattedPrice ?: "—"

// ── Paquete inicial ──────────────────────────────────────────────────────────────────────────

@Composable
private fun StarterPackCard(
    product: ProductDetails?,
    granted: Boolean,
    enabled: Boolean,
    onBuy: () -> Unit,
) {
    ShopCard(title = "Paquete inicial", accent = ShopGold, badge = if (granted) null else "SOLO UNA VEZ") {
        // El arte va CENTRADO y solo: lo que anuncia este pack es su lista, y la lista necesita el
        // ancho entero de la tarjeta — «Estela, la compañera del Cielo Estelar» no cabe en una
        // columna que comparte fila con un icono de 62 dp.
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(62.dp)
                // Halo radial detrás del arte (regla 3): ningún icono se apoya sobre el fondo pelado.
                .drawBehind { drawPortraitRays(ShopGold) },
            contentAlignment = Alignment.Center
        ) {
            RawImage(R.raw.store_starter_pack, Modifier.size(62.dp))
        }
        Spacer(Modifier.height(10.dp))
        StorePacks.STARTER_PACK_CONTENT.forEach { linea ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                RawImage(R.raw.ic_bullet, Modifier.size(12.dp))
                // Sin `maxLines`: si una línea del pack crece, envuelve. Nunca se corta.
                Text(
                    linea,
                    color = Color.White.copy(alpha = 0.88f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // Regla: el botón NO desaparece al agotarse su función — se queda apagado con otra palabra.
        ArcadeButton(
            label = if (granted) "YA ES TUYO" else product.priceLabel(),
            tone = if (granted) ArcadeTone.GREEN else ArcadeTone.GOLD,
            modifier = Modifier.fillMaxWidth(),
            enabled = !granted && enabled && product != null,
            height = 38.dp,
            fontSize = 15.sp,
            contentDescription = if (granted) {
                "El paquete inicial ya es tuyo"
            } else {
                "Comprar el paquete inicial por ${product.priceLabel()}"
            },
            onClick = onBuy,
        )
    }
}

// Product IDs are stable in Play; resource names describe the current base rewards.
internal fun coinPackArtwork(productId: String): Int = when (productId) {
    "coins_250" -> R.raw.store_teikoins_3000
    "coins_1200" -> R.raw.store_teikoins_5000
    "coins_5000" -> R.raw.store_teikoins_10000
    else -> R.raw.teikoin
}

// ── Packs de monedas ─────────────────────────────────────────────────────────────────────────

@Composable
private fun CoinPackCard(
    pack: StorePacks.CoinPack,
    product: ProductDetails,
    bonusAvailable: Boolean,
    /** Sin cuenta respaldada no se compra (el aviso va arriba de la tienda). */
    enabled: Boolean = true,
    onBuy: () -> Unit,
) {
    val total = pack.coins + if (bonusAvailable) pack.coins else 0
    // El color va por PRODUCTO, no por cantidad. Iba por `pack.coins` y el 2026-09-06, al subir el
    // pack pequeño de 250 a 600 monedas, se cayó al `else`: dos tarjetas doradas seguidas y la
    // escalera de color —azul, violeta, oro— deshecha en silencio. El id no cambia nunca; la
    // cantidad sí.
    val accent = when (pack.productId) {
        "coins_250" -> ShopNeon
        "coins_1200" -> ShopPurple
        else -> ShopGold
    }
    ShopCard(
        title = "${com.cukiti.teikafusion.model.AmountFormat.full(pack.coins)} teikoins",
        accent = accent,
        badge = if (bonusAvailable) "×2 DE REGALO" else null,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .drawBehind { drawPortraitRays(accent) },
                contentAlignment = Alignment.Center
            ) {
                RawImage(coinPackArtwork(pack.productId), Modifier.size(52.dp))
            }
            // Lo que ENTRA de verdad, en su píldora. Con bono el número grande es el TOTAL, y la
            // línea de abajo explica de dónde sale: a un jugador al que le llegan 2400 por un pack
            // de 1200 hay que decirle por qué, o lo lee como un error de la tienda.
            Box(Modifier.weight(1f)) { CoinPill(amount = total, accent = accent, big = true) }
            ArcadeButton(
                label = product.priceLabel(),
                tone = ArcadeTone.GOLD,
                modifier = Modifier.width(104.dp),
                enabled = enabled,
                height = 34.dp,
                fontSize = 13.sp,
                contentDescription = "Comprar $total teikoins por ${product.priceLabel()}" +
                    if (bonusAvailable) ". Incluye ${pack.coins} de regalo por ser la primera compra" else "",
                onClick = onBuy,
            )
        }
        Spacer(Modifier.height(8.dp))
        // A ancho completo: «1200 + 1200 de regalo · primera compra» no cabe en la columna estrecha
        // que le quedaba entre el icono y el botón, y era justo la frase que vende el pack.
        Text(
            if (bonusAvailable) "${com.cukiti.teikafusion.model.AmountFormat.full(pack.coins)} + " +
                "${com.cukiti.teikafusion.model.AmountFormat.full(pack.coins)} de regalo · solo en tu primera compra de este paquete"
            else "Recarga de teikoins para gastar en el juego",
            color = if (bonusAvailable) ShopGold else Color.White.copy(alpha = 0.5f),
            fontSize = 11.5.sp,
            fontWeight = if (bonusAvailable) FontWeight.Black else FontWeight.Bold,
            lineHeight = 15.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Aspectos (P-430) ─────────────────────────────────────────────────────────────────────────

/** La puerta a la vitrina: tu bloque equipado, para qué es y un botón. Se pagan con teikoins. */
@Composable
private fun BlocksDoorCard(activeCosmetic: String?, badge: Boolean, onOpen: () -> Unit) {
    ShopCard(title = "Bloques", accent = ShopNeon, badge = if (badge) "¡PUEDES COMPRAR UNO!" else null) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(52.dp).drawBehind { drawPortraitRays(ShopNeon) }, contentAlignment = Alignment.Center) {
                BlockShowcaseButton(activeCosmetic = activeCosmetic, size = 46.dp, onClick = onOpen)
            }
            Text(
                "Cambia el aspecto de tus bloques: cada uno tiene su forma de romper. Se pagan con teikoins.",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(10.dp))
        ArcadeButton(
            label = "VER BLOQUES",
            tone = ArcadeTone.BLUE,
            modifier = Modifier.fillMaxWidth(),
            height = 36.dp,
            fontSize = 14.sp,
            contentDescription = if (badge) "Ver los bloques. Hay uno nuevo que puedes comprar" else "Ver los bloques",
            onClick = onOpen,
        )
    }
}

// ── Sin anuncios ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RemoveAdsCard(
    product: ProductDetails?,
    comprado: Boolean,
    enabled: Boolean,
    onBuy: () -> Unit,
) {
    ShopCard(title = "Quitar anuncios", accent = ShopGreen, badge = if (comprado) null else "PAGO ÚNICO") {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .drawBehind { drawPortraitRays(ShopGreen) },
                contentAlignment = Alignment.Center
            ) {
                RawImage(R.raw.store_remove_ads, Modifier.size(52.dp))
            }
            Spacer(Modifier.weight(1f))
            ArcadeButton(
                label = if (comprado) "TUYO" else product.priceLabel(),
                tone = if (comprado) ArcadeTone.GREEN else ArcadeTone.GOLD,
                modifier = Modifier.width(104.dp),
                enabled = !comprado && enabled && product != null,
                height = 34.dp,
                fontSize = 13.sp,
                contentDescription = if (comprado) {
                    "Ya has quitado los anuncios"
                } else {
                    "Quitar los anuncios para siempre por ${product.priceLabel()}"
                },
                onClick = onBuy,
            )
        }
        Spacer(Modifier.height(8.dp))
        // El texto dice EXACTAMENTE lo que se va y lo que se queda (2026-09-06). Prometía «no verás
        // un anuncio nunca más» y desde hoy es falso a propósito: el vídeo voluntario de teikoins
        // sobrevive a la compra, porque lo que se vende es no ser interrumpido, no perder la forma
        // gratuita de ganar monedas. Un rótulo que promete de más se cobra en devoluciones.
        Text(
            if (comprado) "Se acabaron el banner y los anuncios entre niveles. Los vídeos que dan " +
                "teikoins siguen ahí cuando tú los pidas."
            else "Se van el banner y los anuncios entre niveles; los vídeos que dan teikoins se " +
                "quedan, solo si tú los pides. Pago único, se restaura al cambiar de móvil.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 16.sp,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * **LA OFERTA DEL PAQUETE INICIAL** (P-210, 2026-09-07, decisión del usuario).
 *
 * Se enseña **una sola vez**, al cerrar el primer capítulo, y **sin cuenta atrás**: si el jugador la
 * cierra, el pack sigue en la tienda para siempre. Se eligió así frente al reloj de 24 h del género
 * —que vende más— para no meterle prisa a alguien que lleva veinte niveles jugados.
 *
 * Reutiliza [StorePacks.STARTER_PACK_CONTENT], la MISMA lista que pinta la tarjeta de la tienda: dos
 * sitios que describen el mismo producto tienen que leer la misma fuente, o el día que cambie el
 * contenido del pack uno de los dos mentirá.
 */
@Composable
fun StarterPackOffer(
    product: ProductDetails?,
    onBuy: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * Comprar con dinero real exige cuenta respaldada (la misma regla que la tienda). Sin ella, el
     * botón de precio pasa a «Conectar con Google»: la oferta salta al cerrar el capítulo 1, y casi
     * todos los jugadores llegan ahí sin haber vinculado nada (auditoría 2026-10-01).
     */
    isCloudAccount: Boolean = true,
    onConnectGoogle: () -> Unit = {},
) {
    BackHandler(enabled = true) { onDismiss() }
    val cardScale = remember { Animatable(0.86f) }
    LaunchedEffect(Unit) { cardScale.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 340f)) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.62f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button
            ) { onDismiss() }
            .semantics { contentDescription = "Cerrar" }
            .safeDrawingPadding()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .graphicsLayer { scaleX = cardScale.value; scaleY = cardScale.value }
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                // Absorbe los toques de dentro: si no, pulsar la tarjeta la cerraría.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
                .clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(CardTop, CardBottom)))
                .border(1.6.dp, ShopGold.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(22.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .drawBehind { drawPortraitRays(ShopGold) },
                contentAlignment = Alignment.Center
            ) {
                RawImage(R.raw.store_starter_pack, Modifier.size(72.dp))
            }
            Text(
                text = "Paquete inicial",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Has terminado tu primer capítulo. Esto es lo único que te ofreceremos así: si lo cierras, se queda en la tienda.",
                color = Color(0xFFBBC5E8),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(4.dp))
            StorePacks.STARTER_PACK_CONTENT.forEach { linea ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    RawImage(R.raw.ic_bullet, Modifier.size(12.dp))
                    Text(
                        linea,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 17.sp,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            if (isCloudAccount) {
                ArcadeButton(
                    label = product.priceLabel(),
                    tone = ArcadeTone.GOLD,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = product != null,
                    height = 46.dp,
                    fontSize = 16.sp,
                    contentDescription = "Comprar el paquete inicial por ${product.priceLabel()}",
                    onClick = onBuy,
                )
            } else {
                Text(
                    "Para comprar, respalda antes tu cuenta: así no lo pierdes al cambiar de móvil. " +
                        "El paquete te espera en la tienda.",
                    color = Color(0xFFBBC5E8), fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.Center
                )
                ArcadeButton(
                    label = "CONECTAR CON GOOGLE",
                    tone = ArcadeTone.BLUE,
                    modifier = Modifier.fillMaxWidth(),
                    height = 46.dp,
                    fontSize = 15.sp,
                    contentDescription = "Conectar tu cuenta de Google para poder comprar",
                    onClick = onConnectGoogle,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button
                    ) { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Text("Ahora no", color = Color(0xFF8892B8), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
