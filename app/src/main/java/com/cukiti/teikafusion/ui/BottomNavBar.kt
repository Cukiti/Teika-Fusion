package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.cukiti.teikafusion.R

/**
 * Las TRES pestañas del menú (rediseño 2026-07-27). «H. Teika» desapareció: sus tarjetas de
 * habilidad viven ahora dentro de [InventoryScreen], que es a la vez la mochila de cristales.
 */
enum class NavTab { INVENTORY, HOME, SHOP }

/**
 * **Alto VISIBLE del navbar — fuente ÚNICA** (bloque 23, 2026-07-31).
 *
 * El navbar se dibuja en la CAPA 2, ENCIMA del contenido de las pestañas, así que cada pantalla
 * tiene que reservar este hueco abajo o sus últimos elementos quedan tapados. Ese número estaba
 * copiado a mano en cuatro sitios y con cuatro valores distintos —118 aquí, 132 en la tienda, 118 en
 * el inventario y **80** en Inicio—, de modo que el rediseño del navbar dejó a Inicio reservando
 * casi 40 dp de menos: la barra se comía el borde inferior de la tarjeta de juego. El comentario de
 * Inicio hablaba además de «los ~92 dp inferiores», un tercer número que ya no existía.
 *
 * Quien necesite el hueco usa esta constante. Si el alto cambia otra vez, cambia aquí y ya.
 * (El inset del sistema lo añade el propio navbar con `navigationBarsPadding`; las pantallas que
 * viven bajo `safeDrawingPadding` ya lo tienen descontado.)
 */
val NavBarHeight = 118.dp

// ── Colors ────────────────────────────────────────────────────────────────────
private val BarBg        = Color(0xFF1A2040)
private val BarTop       = Color(0xFF252D50)
private val ActiveTop    = Color(0xFFFF8C00)
private val ActiveBot    = Color(0xFFE65100)
private val LabelActive  = Color.White
private val LabelInactive= Color(0xFF8899CC)

@Composable
fun BottomNavBar(
    currentTab: NavTab,
    onTabSelected: (NavTab) -> Unit,
    modifier: Modifier = Modifier,
    /** P-269: hay un compañero que ya se puede mejorar → flecha verde en la pestaña de Inventario. */
    inventoryBadge: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(
                    color = Color(0xFF3D4A80),
                    start = Offset(0f, 0f),
                    end   = Offset(size.width, 0f),
                    strokeWidth = 1.5.dp.toPx()
                )
            }
            .background(
                Brush.verticalGradient(listOf(BarTop, BarBg))
            )
            .navigationBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NavBarHeight),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavTabItem(
                icon    = { mod -> Image(painterResource(R.drawable.invepowerup), null, mod, contentScale = ContentScale.Fit) },
                label   = "Inventario",
                selected = currentTab == NavTab.INVENTORY,
                badge    = inventoryBadge && currentTab != NavTab.INVENTORY,
                onClick  = { onTabSelected(NavTab.INVENTORY) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            NavTabItem(
                icon    = { mod -> Image(painterResource(R.drawable.homebutton), null, mod, contentScale = ContentScale.Fit) },
                label   = "Inicio",
                selected = currentTab == NavTab.HOME,
                onClick  = { onTabSelected(NavTab.HOME) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
            NavTabItem(
                icon    = { mod -> Box(modifier = mod, contentAlignment = Alignment.Center) { RawImage(R.raw.shop, modifier = Modifier.fillMaxSize(0.93f)) } },
                label   = "Tienda",
                selected = currentTab == NavTab.SHOP,
                onClick  = { onTabSelected(NavTab.SHOP) },
                modifier = Modifier.weight(1f).fillMaxHeight()
            )
        }
    }
}

@Composable
private fun NavTabItem(
    icon: @Composable (Modifier) -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Flecha verde de «hay una mejora lista», arriba a la derecha del icono ([UpgradeArrowBadge]). */
    badge: Boolean = false,
) {
    // Bounce spring on tap: briefly goes 1.1× then settles
    var tapped by remember { mutableStateOf(false) }
    val bounceScale by animateFloatAsState(
        targetValue   = if (tapped) 1.12f else 1f,
        animationSpec = spring(Spring.DampingRatioHighBouncy, Spring.StiffnessMediumLow),
        finishedListener = { tapped = false },
        label = "bounce"
    )
    // Inactive tabs fade to 70%
    val alpha by animateFloatAsState(
        targetValue   = if (selected) 1f else 0.70f,
        animationSpec = tween(150),
        label = "tabAlpha"
    )
    // Icon size: selected is bigger
    val iconSize by animateFloatAsState(
        targetValue   = if (selected) 74f else 62f,
        animationSpec = tween(150),
        label = "iconSize"
    )

    Box(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                role              = Role.Tab,
            ) {
                tapped = true
                onClick()
            }
            // La etiqueta ("Inicio", "Tienda"…) ya la lee el lector desde el Text de dentro; lo que
            // falta es el ESTADO: sin esto no hay forma de saber en qué pestaña estás.
            .semantics { this.selected = selected }
            .alpha(alpha),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Active pill background
            Box(
                modifier = Modifier
                    .size(width = 88.dp, height = 76.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .then(
                        if (selected) Modifier.background(
                            Brush.verticalGradient(listOf(ActiveTop, ActiveBot))
                        ) else Modifier
                    )
                    .graphicsLayer {
                        scaleX = if (selected) bounceScale else bounceScale * 0.9f
                        scaleY = if (selected) bounceScale else bounceScale * 0.9f
                    },
                contentAlignment = Alignment.Center
            ) {
                icon(Modifier.size(iconSize.dp))
                if (badge) {
                    // La misma flecha verde que las casillas del Inventario (antes, un punto rojo).
                    UpgradeArrowBadge(
                        active = true,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 4.dp)
                            .size(26.dp)
                            .semantics { contentDescription = "Tienes una mejora lista" }
                    )
                }
            }

            // Label
            Text(
                text       = label,
                fontSize   = 12.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal,
                color      = if (selected) LabelActive else LabelInactive,
                maxLines   = 1
            )
        }
    }
}
