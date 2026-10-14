package com.cukiti.teikafusion.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.R

/**
 * **TODA COMPRA CON TEIKOINS SE CELEBRA IGUAL** (2026-09-06, pedido del usuario: «quiero que todos
 * los botones que sean para comprar algo con teikoins hagan esa animación para mantener el sistema
 * premium del juego»).
 *
 * La lluvia de monedas nació el 2026-09-04 en **un solo botón** —el de comprar usos de la hoja de
 * habilidades— y estaba cableada a mano en esa pantalla: el botón medía su centro, se lo pasaba al
 * padre por el `onClick`, el padre lo traducía a coordenadas de su caja raíz y dibujaba las
 * explosiones. Copiar eso en los otros seis sitios donde se gastan teikoins habría sido copiar seis
 * veces el mismo cableado, y ese es el bug con nombre propio de este repositorio: **dos copias de un
 * mismo dibujo**, que se retocan por separado hasta que dejan de parecerse.
 *
 * Así que la celebración deja de ser cosa de cada pantalla y pasa a ser del sistema:
 *
 *  - [CoinBurstHost] envuelve una pantalla, ofrece el emisor por [LocalCoinBurst] y dibuja las
 *    explosiones **en su propia raíz** — que es donde tienen que vivir, porque las tarjetas y las
 *    rejillas recortan y una moneda pintada dentro se cortaría a los pocos dp de vuelo.
 *  - [PriceButton] es el botón dorado del dinero: mide su centro solo y dispara la celebración él
 *    mismo, sin que la pantalla tenga que saber nada.
 *
 * Un botón de precio dentro de una pantalla **sin** host no se rompe: el local es `null` y
 * simplemente no hay monedas. Es la degradación correcta — la compra ya suena y ya se ve en el
 * marcador, así que nunca se pierde información.
 */
val LocalCoinBurst = staticCompositionLocalOf<((Offset) -> Unit)?> { null }

/** Explosiones que pueden solaparse. Con el dedo mantenido llega una compra cada ~250 ms. */
private const val MAX_COIN_BURSTS = 3

/**
 * Espacio mínimo entre dos celebraciones. A una compra cada 250 ms, una explosión (y su háptica)
 * por compra es un zumbido continuo, no una respuesta; cada ~150 ms basta para que la fuente no se
 * corte y para que cada golpe se sienta como un golpe.
 */
private const val COIN_BURST_MIN_GAP_MS = 150L

/**
 * Envuelve una pantalla para que sus [PriceButton] puedan celebrar. Ponlo en la caja más externa:
 * el origen de las monedas se traduce a **sus** coordenadas, así que cuanto más arriba esté, menos
 * puede recortar nadie el vuelo.
 */
@Composable
fun CoinBurstHost(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val bursts = remember { mutableStateListOf<CoinBurst>() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var seq by remember { mutableLongStateOf(0L) }
    var lastMs by remember { mutableLongStateOf(0L) }

    val emit: (Offset) -> Unit = remember {
        { center ->
            val now = android.os.SystemClock.uptimeMillis()
            if (now - lastMs >= COIN_BURST_MIN_GAP_MS) {
                lastMs = now
                Haptics.tap()
                seq += 1
                if (bursts.size >= MAX_COIN_BURSTS) bursts.removeAt(0)
                bursts.add(CoinBurst(id = seq, origin = center - rootOrigin))
            }
        }
    }

    Box(
        modifier = modifier.onGloballyPositioned { rootOrigin = it.boundsInRoot().topLeft }
    ) {
        CompositionLocalProvider(LocalCoinBurst provides emit) { content() }
        // Por encima del contenido y sin `pointerInput`: dibujar fuera no es tocar fuera.
        bursts.forEach { b ->
            key(b.id) {
                CoinBurstLayer(
                    burst = b,
                    onFinished = { bursts.remove(b) },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/**
 * **EL BOTÓN DEL DINERO.** Un [ArcadeButton] dorado con la moneda dentro que, al comprar, suelta la
 * lluvia de teikoins desde su propio centro.
 *
 * @param onBuy devuelve **`true` si la compra ocurrió de verdad**. Es lo único que este botón exige
 *   a quien lo usa, y no es un capricho: una explosión de monedas por una compra que el motor
 *   rechazó —sin saldo, o al tope— es peor que no tener explosión, porque miente. El ViewModel es
 *   quien manda, no el `enabled` de la UI.
 * @param repeatWhileHeld auto-disparo con el dedo mantenido, con su tope de tres por pulsación (ver
 *   [ArcadeButton]). Solo para lo acumulable.
 */
@Composable
fun PriceButton(
    price: Int,
    onBuy: () -> Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** Texto alternativo: cuando la función se agota, el botón **se queda** y cambia de palabra. */
    label: String? = null,
    tone: ArcadeTone = ArcadeTone.GOLD,
    height: Dp = 34.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 13.sp,
    depth: Dp = 4.dp,
    contentDescription: String? = null,
    repeatWhileHeld: Boolean = false,
    /** Icono de la moneda dentro del botón. Se apaga cuando el botón ya no muestra un precio. */
    showCoin: Boolean = label == null,
) {
    val burst = LocalCoinBurst.current
    val center = remember { FloatArray(2) }
    ArcadeButton(
        label = label ?: "$price",
        tone = tone,
        modifier = modifier.onGloballyPositioned {
            val b = it.boundsInRoot()
            center[0] = b.center.x
            center[1] = b.center.y
        },
        enabled = enabled,
        height = height,
        fontSize = fontSize,
        depth = depth,
        contentDescription = contentDescription,
        repeatWhileHeld = repeatWhileHeld,
        leading = if (!showCoin) null else {
            { RawImage(R.raw.teikoin, Modifier.size((fontSize.value * 0.95f).dp)) }
        },
        onClick = {
            // La compra manda: solo si el motor dice que sí se celebra.
            if (onBuy()) burst?.invoke(Offset(center[0], center[1]))
        },
    )
}
