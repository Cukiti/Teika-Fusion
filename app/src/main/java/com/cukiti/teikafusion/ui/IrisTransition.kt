package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineStart
import kotlin.math.hypot

/**
 * **EL IRIS** (2026-09-23, pedido del usuario): al entrar o salir de un nivel, un círculo negro se
 * cierra sobre la pantalla, el cambio ocurre a oscuras y el círculo se vuelve a abrir sobre la
 * pantalla nueva — la transición clásica de dibujos animados.
 *
 * El orden es lo que importa: **cerrar → cambiar → abrir**. La navegación de la app cambia de
 * pantalla en cuanto cambia `GameStatus`, así que quien sale o entra no llama al ViewModel directo
 * sino a [Iris.run], que lo retrasa hasta que la pantalla está negra. De paso esconde el montaje del
 * tablero (sus Lottie), que era el tirón de entrada a partida.
 *
 * Es un objeto único, como [biomeClockSec]: lo pide GameScreen y lo pide Inicio, y pasarlo por
 * parámetro atravesaría medio árbol. El dibujo vive en [IrisOverlay], montado una vez encima de todo
 * en `AppNavigation`, que es quien le presta su corrutina.
 *
 * Con «reducir efectos», o si aún no hay overlay montado, la acción se ejecuta al instante: el iris
 * es decoración y nunca puede ser la razón de que un botón no haga nada.
 */
object Iris {
    /** 1 = abierto del todo (invisible); 0 = cerrado (pantalla negra). */
    internal val progress = Animatable(1f)
    internal var scope: CoroutineScope? = null
    internal var reduced = false
    /**
     * El sonido del iris (2026-09-29, pedido del usuario): lo presta [IrisOverlay] desde `AppNavigation`,
     * que tiene el `SoundManager`. Suena al empezar a cerrarse; con «reducir efectos» no hay iris ni sonido.
     */
    internal var onStart: (() -> Unit)? = null
    internal var onOpen: (() -> Unit)? = null
    private var busy = false

    fun run(action: () -> Unit) {
        // Con una transición en marcha, un segundo pedido es un doble toque (JUGAR dos veces): se
        // ignora. Ejecutarlo arrancaría el nivel dos veces.
        if (busy) return
        val s = scope
        if (s == null || !s.isActive || reduced) { action(); return }
        busy = true
        onStart?.invoke()
        s.launch(start = CoroutineStart.UNDISPATCHED) {
            var done = false
            try {
                progress.animateTo(0f, tween(IRIS_CLOSE_MS, easing = FastOutSlowInEasing))
                done = true
                action()
                // Deja que la pantalla nueva se componga y pinte un par de veces antes de enseñarla.
                repeat(3) { withFrameNanos { } }
                delay(IRIS_HOLD_MS)
                currentCoroutineContext().ensureActive()
                if (scope === s && !reduced) onOpen?.invoke()
                progress.animateTo(1f, tween(IRIS_OPEN_MS, easing = FastOutSlowInEasing))
            } finally {
                // Si algo cancela la animación, el botón tiene que haber hecho su trabajo igualmente.
                try {
                    if (!done) action()
                } finally {
                    try {
                        withContext(NonCancellable) { progress.snapTo(1f) }
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }
}

private const val IRIS_CLOSE_MS = 380
private const val IRIS_HOLD_MS = 90L
private const val IRIS_OPEN_MS = 480

/**
 * El dibujo del iris: negro con un agujero redondo en el centro, con un filo de luz en el borde.
 * Solo existe mientras la transición está en marcha, y mientras tanto se come los toques (nadie
 * puede pulsar un botón que se está apagando).
 */
@Composable
fun IrisOverlay(reducedFx: Boolean, onStart: () -> Unit = {}, onOpen: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    DisposableEffect(scope) {
        Iris.scope = scope
        onDispose {
            if (Iris.scope === scope) {
                Iris.scope = null
                Iris.onStart = null
                Iris.onOpen = null
            }
        }
    }
    Iris.reduced = reducedFx
    Iris.onStart = onStart
    Iris.onOpen = onOpen
    val active by remember { derivedStateOf { Iris.progress.value < 1f } }
    if (!active) return
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitPointerEvent()
                    down.changes.forEach { it.consume() }
                }
            }
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                // Capa propia: el agujero se recorta con BlendMode.Clear sobre el negro de ESTA capa,
                // no sobre lo que hay debajo.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val k = Iris.progress.value
            val maxR = hypot(size.width, size.height) / 2f * 1.04f
            val r = maxR * k
            drawRect(Color(0xFF05060F))
            if (r > 0.5f) {
                drawCircle(Color.Transparent, radius = r, blendMode = BlendMode.Clear)
                // Filo: un anillo fino que brilla más cuanto más cerrado está el círculo.
                drawCircle(
                    Color(0xFF8C6BFF), radius = r,
                    style = Stroke(width = 6f + 10f * (1f - k)), alpha = 0.55f * (1f - k)
                )
            }
        }
    }
}
