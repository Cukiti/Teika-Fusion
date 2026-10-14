package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import com.cukiti.teikafusion.ui.theme.AppBackground
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.R
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Conjunto de componentes premium (estilo AAA) compartidos por las pantallas de Habilidades e Items.
 *
 * Filosofía de RENDIMIENTO: una sola animación continua por pantalla (un [Animatable] "phase" alimentado
 * por [rememberAmbientPhase]) gobierna TODO el movimiento ambiental. Los componentes leen `phase()` SOLO
 * dentro de lambdas de dibujo (Canvas / drawBehind / graphicsLayer) → se REDIBUJAN sin RECOMPONER, y la
 * animación se DETIENE cuando la pestaña no está activa (no quema frames en segundo plano).
 */

// Esencia del juego: el azul de fondo de TODOS los menús. Atado a theme.AppBackground para que el
// fondo premium (Tienda, Habilidades Teika, etc.) siga SIEMPRE el azul global y no se desincronice.
private val GameBg     = AppBackground
private val GameBgDeep = lerp(AppBackground, Color(0xFF0A1130), 0.42f)

/**
 * Reloj ambiental compartido — **CONGELADO desde 2026-07-28**.
 *
 * Era un bucle infinito que invalidaba el dibujo a 60 fps mientras el Inventario o la Tienda
 * estuvieran en pantalla: orbes respirando, partículas subiendo, aros girando y barridos de luz,
 * todos a la vez, todo el rato. Nada de eso era información — solo movimiento de fondo — y era el
 * motivo de que ambas pantallas fueran a tirones al hacer scroll o abrir un panel.
 *
 * Ahora devuelve una fase FIJA y no lanza corrutina: los componentes premium siguen leyendo
 * `phase()` (misma API, cero cambios en las llamadas) pero dibujan un fotograma estático. El look
 * glossy se conserva entero; lo que desaparece es el repintado continuo.
 */
@Composable
fun rememberAmbientPhase(@Suppress("UNUSED_PARAMETER") active: Boolean): Animatable<Float, *> =
    remember { Animatable(0f) }

/**
 * Fondo de menú con la ESENCIA del juego: el mismo azul [GameBg] (#405384) de todos los menús, con
 * un realce premium sutil (orbes en la misma gama + viñeta). ESTÁTICO: se dibuja una vez y la capa
 * queda cacheada. Las partículas ascendentes se retiraron con el congelado de la fase ambiental.
 */
@Composable
fun PremiumBackground(
    @Suppress("UNUSED_PARAMETER") phase: () -> Float,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val baseBrush = remember { Brush.verticalGradient(listOf(GameBg, GameBgDeep)) }
    val orbs = remember {
        listOf(
            Triple(0.16f, 0.14f, 1.05f),
            Triple(0.88f, 0.26f, 1.30f),
            Triple(0.24f, 0.74f, 1.10f),
            Triple(0.84f, 0.90f, 0.85f)
        )
    }
    Canvas(modifier) {
        drawRect(baseBrush)
        orbs.forEachIndexed { i, (fx, fy, fr) ->
            val c = Offset(size.width * fx, size.height * fy)
            val rad = size.minDimension * 0.5f * fr
            val col = if (i % 2 == 0) accent else Color(0xFF6E84C8)
            drawCircle(
                brush = Brush.radialGradient(listOf(col.copy(alpha = 0.14f), Color.Transparent), center = c, radius = rad),
                radius = rad, center = c, alpha = 0.8f
            )
        }
        // Viñeta inferior sutil para asentar el contenido.
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFF1F2643).copy(alpha = 0.35f)),
                startY = size.height * 0.55f, endY = size.height
            )
        )
    }
}

// (appearItem / rememberStaggerProgress BORRADOS 2026-07-28: la entrada escalonada de tarjetas
//  animaba alpha + desplazamiento + escala de CADA tarjeta con un retardo creciente, así que abrir
//  el Inventario o la Tienda arrancaba con medio segundo de animación antes de poder tocar nada.
//  El contenido ahora está ahí desde el primer fotograma.)

/**
 * Botón premium: degradado, brillo superior glossy y rebote al pulsar. El barrido de luz periódico
 * (sheen) se retiró con el congelado ambiental — pedía un fotograma cada 16 ms por cada botón en
 * pantalla. El rebote al PULSAR se conserva: eso sí es respuesta al jugador, no decoración.
 */
@Composable
fun PremiumButton(
    label: String,
    enabled: Boolean,
    gradient: List<Color>,
    @Suppress("UNUSED_PARAMETER") phase: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 38.dp,
    textColor: Color = Color(0xFF10173A)
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.91f else 1f, spring(), label = "btnScale")
    val shape = RoundedCornerShape(13.dp)
    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .height(height)
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(gradient)
                else Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.07f), Color.White.copy(alpha = 0.04f)))
            )
            .drawBehind {
                if (!enabled) return@drawBehind
                // Brillo glossy en la mitad superior (estático, da volumen de cristal).
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.30f), Color.Transparent),
                        endY = size.height * 0.55f
                    ),
                    size = Size(size.width, size.height * 0.55f)
                )
            }
            .clickable(enabled = enabled, interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) textColor else Color.White.copy(alpha = 0.42f),
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}

/**
 * Medallón premium para iconos: halo radial + cápsula interior + corona de marcas.
 *
 * Por defecto la corona es ESTÁTICA (la mochila puede tener 14 medallones a la vez).
 * [ringSpinRad] permite girar SOLO el anillo/corona (p. ej. inyección de cristales en el héroe):
 * se lee dentro del Canvas → 0 recomposición por frame. El Lottie del contenido no gira.
 */
@Composable
fun PremiumMedallion(
    accent: Color,
    @Suppress("UNUSED_PARAMETER") phase: () -> Float,
    size: Dp,
    modifier: Modifier = Modifier,
    spin: Boolean = false,
    glow: Float = 1f,
    /** Ángulo del anillo en radianes; leer Animatable aquí, no en composition. */
    ringSpinRad: () -> Float = { 0f },
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val rOuter = this.size.minDimension / 2f
            val spinA = ringSpinRad()
            // Halo difuso.
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.34f * glow), Color.Transparent),
                    center = c, radius = rOuter
                ),
                radius = rOuter, center = c, alpha = 0.9f
            )
            // Cápsula interior.
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF222C57), Color(0xFF141A38)), center = c, radius = rOuter * 0.72f),
                radius = rOuter * 0.72f, center = c
            )
            // Aro de la cápsula (también gira levemente vía dash visual con spin).
            drawCircle(accent.copy(alpha = 0.55f), rOuter * 0.72f, c, style = Stroke(width = rOuter * 0.05f))
            // Corona de marcas: fija en mochila; con ringSpinRad gira en el héroe al inyectar.
            if (spin || spinA != 0f) {
                val marks = if (spin) 16 else 12
                repeat(marks) { i ->
                    val a = i * (2.0 * PI / marks) + spinA
                    val r1 = rOuter * 0.82f
                    val r2 = rOuter * 0.94f
                    drawLine(
                        accent.copy(alpha = if (spinA != 0f) 0.85f else 0.55f),
                        Offset(c.x + cos(a).toFloat() * r1, c.y + sin(a).toFloat() * r1),
                        Offset(c.x + cos(a).toFloat() * r2, c.y + sin(a).toFloat() * r2),
                        strokeWidth = rOuter * 0.035f, cap = StrokeCap.Round
                    )
                }
                // Arco brillante que recorre el anillo cuando está girando (inyección).
                if (spinA != 0f) {
                    val sweep = 70f
                    val start = Math.toDegrees(spinA.toDouble()).toFloat()
                    drawArc(
                        color = accent.copy(alpha = 0.95f),
                        startAngle = start,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - rOuter * 0.88f, c.y - rOuter * 0.88f),
                        size = Size(rOuter * 1.76f, rOuter * 1.76f),
                        style = Stroke(width = rOuter * 0.07f, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = Color.White.copy(alpha = 0.55f),
                        startAngle = start + 8f,
                        sweepAngle = sweep * 0.45f,
                        useCenter = false,
                        topLeft = Offset(c.x - rOuter * 0.88f, c.y - rOuter * 0.88f),
                        size = Size(rOuter * 1.76f, rOuter * 1.76f),
                        style = Stroke(width = rOuter * 0.03f, cap = StrokeCap.Round)
                    )
                }
            }
        }
        content()
    }
}

/**
 * Cuenta animada (count-up): anima progresivamente el valor mostrado desde el anterior hasta
 * [target] cada vez que cambia, tanto al subir como al bajar. Reutilizable en cualquier contador
 * de teikoins de la app (Fase 2: count-up en todos lados).
 */
@Composable
fun rememberCountUp(target: Int, durationMs: Int = 600): Int {
    val anim = remember { Animatable(target.toFloat()) }
    LaunchedEffect(target) {
        anim.animateTo(target.toFloat(), tween(durationMs, easing = FastOutSlowInEasing))
    }
    return (anim.value + 0.5f).toInt()
}

/** Cartera premium reutilizable: cápsula con degradado + borde dorado y moneda. */
@Composable
fun PremiumWalletPill(teikoins: Int, modifier: Modifier = Modifier) {
    val display = rememberCountUp(teikoins)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF313A6B), Color(0xFF222A52))))
            .border(
                1.dp,
                Brush.horizontalGradient(listOf(Color(0xFFFFE08A), Color(0xFFFF9D2E))),
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 13.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RawImage(R.raw.teikoin, Modifier.size(18.dp))
        Box(Modifier.size(6.dp))
        Text("$display", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFFFFD700))
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  BOTONES DE ARCADE (2026-09-04) — el rediseño del Inventario
// ════════════════════════════════════════════════════════════════════════════════
//
// Pedido del usuario: «los botones tienen que tener buen diseño visualmente premium con un feedback
// de juego triple A», con sus capturas de referencia delante. Todas las pantallas de ese juego
// comparten el mismo botón: cuerpo sólido con **labio inferior**, cara con degradado y brillo
// arriba, y al pulsar la cara BAJA hasta tapar el labio. No es una sombra difusa ni un cambio de
// alpha: es un objeto con canto, y el hundido es el gesto.
//
// El primero de la casa fue el de SALTAR TUTORIAL. Aquí se saca a componente porque el Inventario
// necesita cuatro más, y el gesto —que tiene su miga: hay que soltar DENTRO para que dispare— no
// puede acabar copiado en cinco sitios.

/** Los cuatro papeles de un botón, que es lo que decide su color. */
enum class ArcadeTone { GREEN, GOLD, RED, BLUE, NEUTRAL }

private data class ArcadeSkin(
    val faceTop: Color, val faceBottom: Color, val lip: Color, val text: Color,
)

private fun skinOf(tone: ArcadeTone, enabled: Boolean): ArcadeSkin = when {
    !enabled -> ArcadeSkin(Color(0xFF39406B), Color(0xFF2B3157), Color(0xFF191E38), Color(0xFF7C87AE))
    tone == ArcadeTone.GREEN -> ArcadeSkin(Color(0xFF6BE08A), Color(0xFF34A85A), Color(0xFF1B6234), Color(0xFF07240F))
    tone == ArcadeTone.GOLD -> ArcadeSkin(Color(0xFFFFD764), Color(0xFFE2A116), Color(0xFF8A5B06), Color(0xFF3A2400))
    tone == ArcadeTone.RED -> ArcadeSkin(Color(0xFFFF8080), Color(0xFFD64545), Color(0xFF7E2020), Color(0xFF320A0A))
    tone == ArcadeTone.BLUE -> ArcadeSkin(Color(0xFF6FC8FF), Color(0xFF2E7FD1), Color(0xFF14497D), Color(0xFF042038))
    else -> ArcadeSkin(Color(0xFF4A5590), Color(0xFF333B66), Color(0xFF1B2040), Color(0xFFE3E9FF))
}

/**
 * **EL GESTO, REESCRITO** (2026-09-04, reporte del usuario: «algunas veces no funcionan los
 * botones»). Ahora se apoya en `clickable` + `interactionSource`, y merece la pena escribir por qué,
 * porque la versión anterior parecía más lista y era justo la culpable:
 *
 * Escuchaba el puntero **en la pasada `Initial`** y consumía el «down» ahí mismo. Eso tiene dos
 * consecuencias que en una pantalla quieta no se ven y en una rejilla sí:
 *
 *  · **Le robaba el gesto al scroll.** Cada tarjeta de LA BANCA lleva dos botones en su mitad baja;
 *    empezar el dedo ahí consumía el evento antes de que la rejilla lo viera, así que **arrastrar
 *    desde un botón no desplazaba la lista**.
 *  · **Y perdía toques.** Si un padre acababa quedándose el puntero, el bucle interno dejaba de ver
 *    su propio `down.id`, salía por el `break` con `fired = false` y **no pasaba nada**: ni sonido,
 *    ni acción, ni pista. Es exactamente el síntoma que reportó el usuario.
 *
 * `clickable` no es «lo fácil», es lo correcto: trae el arbitraje que esto necesitaba —si el dedo se
 * va, gana el scroll y el botón se levanta sin disparar; si no se mueve, dispara— y da el estado de
 * PRESIONADO por `interactionSource`, que era lo único que el invento aportaba. También arregla el
 * anidamiento: dentro de una ranura o de una tarjeta que ya son pulsables, el hijo tiene prioridad
 * sin que nadie consuma nada a mano.
 *
 * El hundido se lee **en dibujo**, nunca en el cuerpo del composable (regla de la casa: un valor por
 * frame leído en recomposición no difiere nada).
 */
@Composable
fun rememberArcadeSink(pressed: Boolean, enabled: Boolean): Animatable<Float, *> {
    val reduced = LocalReducedFx.current
    val press = remember { Animatable(0f) }
    LaunchedEffect(pressed, enabled, reduced) {
        val target = if (pressed && enabled) 1f else 0f
        if (reduced) press.snapTo(target)
        else press.animateTo(target, tween(if (target > 0f) 70 else 150, easing = FastOutSlowInEasing))
    }
    return press
}

/**
 * **MANTENER PULSADO REPITE** (2026-09-05, pedido del usuario: «si el jugador presiona el botón de
 * comprar usos que se realicen muchas veces […] se tiene que ver cómo se presionan varias veces
 * hasta que el jugador deje de presionar»).
 *
 * Cuatro cuidados, y ninguno es opcional:
 *
 *  · **El primer disparo lo sigue dando `clickable`**, no este bucle. Así un toque corto se comporta
 *    exactamente igual que antes —con el mismo arbitraje contra el scroll de la rejilla— y el
 *    auto-disparo solo entra cuando el dedo se queda ([FIRST_REPEAT_MS]).
 *  · **Se VE**. Con el dedo quieto el hundido también está quieto, así que cada repetición levanta
 *    la cara un instante y la vuelve a hundir ([HoldLatch.lift]): sin eso el botón dispara tres
 *    veces y parece que no ha pasado nada.
 *  · **SE CORTA A LAS TRES** ([MAX_FIRES_PER_PRESS], P-185, decisión del usuario 2026-09-05). Ver
 *    abajo: es un cerrojo de economía, no un detalle de ritmo.
 *  · Al soltar, `clickable` lanza su `onClick` de siempre. Si el bucle ya disparó, ese último se
 *    **descarta** ([HoldLatch.fired]): soltar no puede cobrar un uso de más.
 *
 * **EL TOPE POR PULSACIÓN** — el porqué, que es lo que importa. Sin él, ir de 0 a 9 usos de un
 * compañero legendario son **8.100 teikoins en un segundo y medio** sin ninguna confirmación. Los
 * teikoins **no se compran con dinero real**: recuperarlos son varias sesiones o varios anuncios, y
 * este era el único gesto del juego capaz de vaciar la cartera sin soltar el dedo. Con el tope, una
 * pulsación cuesta como mucho tres compras y para llegar a nueve hay que **decidirlo tres veces**.
 *
 * Se prefirió a pedir confirmación con [ConfirmOverlay] porque una ventana a media compra rompe el
 * gesto que el jugador está haciendo; el botón levantándose bajo el dedo dice lo mismo sin
 * interrumpir nada. Y como el botón que deja de responder es el bug de UX clásico de este proyecto,
 * el corte **se anuncia**: la cara se queda ARRIBA aunque el dedo siga abajo ([HoldLatch.locked]) y
 * suena una háptica de aviso. No es «se ha roto», es «suéltalo y vuelve a pulsar».
 *
 * (**La aceleración se retiró con el tope.** Iba de 240 a 110 ms para que comprar nueve usos no
 * fuera una espera; con tres disparos por pulsación no llegaba a notarse y dejaba dos constantes
 * diciendo algo que ya no pasaba. Cadencia fija: tres golpes que se cuentan solos.)
 */
private const val FIRST_REPEAT_MS = 380L
private const val REPEAT_MS = 250L

/**
 * Disparos automáticos que admite UNA pulsación mantenida. Tres: bastantes para que mantener el dedo
 * valga la pena, pocos para que ninguna pulsación sola vacíe la cartera. Ver el KDoc de arriba.
 */
private const val MAX_FIRES_PER_PRESS = 3

/**
 * Estado del auto-disparo. Es un objeto plano y NO `State`: se lee y escribe desde corrutinas y
 * desde el `onClick`, nunca desde el cuerpo del composable, así que no debe provocar recomposición.
 * Los únicos valores animados son [lift] y [locked], y los dos se leen en DIBUJO como manda la casa.
 */
private class HoldLatch {
    var fired = false
    val lift = Animatable(0f)
    /** 1 = tope alcanzado: la cara se queda arriba aunque el dedo siga abajo. */
    val locked = Animatable(0f)
}

/**
 * Botón con canto, cara y hundido. [leading] es el hueco para un icono —la moneda de un precio, la
 * flecha de una mejora—, que en las referencias del usuario va SIEMPRE dentro del botón y nunca
 * suelto al lado.
 *
 * [repeatWhileHeld] enciende el auto-disparo descrito arriba. Déjalo apagado en todo lo que no sea
 * idempotente o acumulable: EQUIPAR repetido nueve veces no significa nada.
 */
@Composable
fun ArcadeButton(
    label: String,
    tone: ArcadeTone,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 34.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    depth: Dp = 4.dp,
    contentDescription: String? = null,
    repeatWhileHeld: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press = rememberArcadeSink(pressed, enabled)
    val skin = skinOf(tone, enabled)
    val shape = RoundedCornerShape(12.dp)

    val hold = remember { HoldLatch() }
    val liveClick = rememberUpdatedState(onClick)
    if (repeatWhileHeld) {
        LaunchedEffect(pressed, enabled) {
            if (!pressed || !enabled) return@LaunchedEffect
            // El cerrojo se suelta al EMPEZAR la siguiente pulsación, nunca al soltar la anterior, y
            // esa diferencia se ve: soltando, `press` tarda 150 ms en bajar de 1 a 0, así que
            // quitar el cerrojo en ese momento devolvería el hundido a 1 durante ese descenso — la
            // cara daría un golpe hacia abajo justo al levantar el dedo. Manteniéndolo hasta el
            // siguiente `down`, el botón se queda arriba y ya está.
            hold.locked.snapTo(0f)
            hold.fired = false
            delay(FIRST_REPEAT_MS)
            repeat(MAX_FIRES_PER_PRESS) {
                hold.fired = true
                liveClick.value()
                // El re-golpe: la cara sube un pelo y vuelve a caer. Va en su propia corrutina para
                // que el ritmo del disparo no dependa de lo que tarde la animación.
                launch {
                    hold.lift.snapTo(0.8f)
                    hold.lift.animateTo(0f, tween(110, easing = FastOutSlowInEasing))
                }
                delay(REPEAT_MS)
            }
            // TOPE (P-185): el botón se levanta bajo el dedo y avisa. Un botón que simplemente deja
            // de responder es el bug de UX clásico —el jugador toca, no pasa nada y culpa al juego—,
            // así que aquí el «ya no» tiene forma y se siente.
            Haptics.warning()
            hold.locked.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
        }
    }

    Box(
        modifier = modifier
            .semantics {
                role = Role.Button
                contentDescription?.let { this.contentDescription = it }
            }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = {
                    // Soltar tras un auto-disparo NO cobra otra vez: el bucle ya hizo el trabajo.
                    if (hold.fired) hold.fired = false else onClick()
                },
            )
            // El labio ocupa el padding inferior: la caja mide cara + canto y la cara se hunde
            // dentro sin mover a nadie del layout. Va DESPUÉS del clickable a propósito, para que
            // el canto también sea zona táctil: es parte del botón, no un margen.
            .padding(bottom = depth)
    ) {
        Box(
            modifier = Modifier
                .height(height)
                .fillMaxWidth()
                .graphicsLayer {
                    // `locked` gana al hundido: con el tope alcanzado la cara sube del todo aunque
                    // el dedo siga apoyado, que es el aviso de «suelta y vuelve a pulsar».
                    val sunk = ((press.value - hold.lift.value) * (1f - hold.locked.value))
                        .coerceIn(0f, 1f)
                    translationY = depth.toPx() * sunk
                    val sc = 1f - 0.03f * sunk
                    scaleX = sc; scaleY = sc
                }
                .drawBehind {
                    // `locked` gana al hundido: con el tope alcanzado la cara sube del todo aunque
                    // el dedo siga apoyado, que es el aviso de «suelta y vuelve a pulsar».
                    val sunk = ((press.value - hold.lift.value) * (1f - hold.locked.value))
                        .coerceIn(0f, 1f)
                    val r = 12.dp.toPx()
                    val lip = depth.toPx()
                    val corner = androidx.compose.ui.geometry.CornerRadius(r, r)
                    // EL CANTO, asomando por el pie. Se come el hundido: al pulsar desaparece.
                    drawRoundRect(
                        color = skin.lip,
                        topLeft = Offset(0f, lip * (1f - sunk)),
                        size = size,
                        cornerRadius = corner,
                    )
                    drawRoundRect(
                        brush = Brush.verticalGradient(0f to skin.faceTop, 1f to skin.faceBottom),
                        size = size,
                        cornerRadius = corner,
                    )
                    // Brillo glossy en la mitad de arriba: la luz viene de arriba en toda la app.
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.28f - 0.18f * sunk),
                            1f to Color.Transparent,
                        ),
                        size = Size(size.width, size.height * 0.5f),
                        cornerRadius = corner,
                    )
                }
                .clip(shape)
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leading != null) {
                    leading()
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    label,
                    color = skin.text,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    // Si no cabe, ENCOGE en vez de recortarse. El usuario vio «DESEQUIPA» a medias
                    // en la tarjeta: un botón cuyo verbo está cortado no dice lo que hace.
                    autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                        minFontSize = 7.sp,
                        maxFontSize = fontSize,
                    ),
                )
            }
        }
    }
}

/**
 * **EL MEDIDOR DE USOS** — cápsulas, no un número suelto.
 *
 * Pedido del usuario (2026-09-04): «el texto de cantidad de usos se ve muy poco profesional». Tenía
 * razón, y la referencia lo dice a gritos: en sus capturas **ninguna cantidad va suelta**, todas
 * viven dentro de una píldora o de un medidor. Un `×3` en texto plano es de las cosas que delatan
 * que una pantalla no está terminada.
 *
 * **UN HUECO POR USO, Y EL ANCHO NO CAMBIA NUNCA** (2026-09-05, reporte del usuario: «dice ×9 cuando
 * está full y como que la carta crece y empuja a las otras»). La versión anterior pintaba CINCO
 * cápsulas y, al pasar de cinco, **añadía** una píldora «×N» al lado. Esa píldora es ancho nuevo:
 * aparecía a los 6 usos y movía todo lo que tuviera al lado —en la ranura del cinturón, que centra
 * su contenido, desplazaba el medidor entero—. Un medidor que cambia de tamaño según lo que mide es
 * justo lo que hace que una pantalla parezca a medio hacer.
 *
 * Ahora hay **tantos huecos como usos caben** ([CompanionCharges.MAX_PER_COMPANION] = 9) y se
 * reparten por `weight(1f)` dentro del ancho que le den, así que:
 *
 *  · el medidor mide **exactamente lo mismo** con 0 usos que con 9 — no puede empujar a nadie;
 *  · la FORMA es la cantidad: no hace falta ninguna cifra, ni suelta ni en píldora;
 *  · nunca desborda su caja, porque no tiene ancho propio que desbordar.
 *
 * Al llenarse, el riel entero se dora: es el «ya no puedes comprar más» dicho con el mismo lenguaje
 * que el resto (ver el botón en estado MÁX de la tarjeta de LA BANCA).
 */
@Composable
fun ChargeMeter(
    charges: Int,
    accent: Color,
    modifier: Modifier = Modifier,
    capsules: Int = com.cukiti.teikafusion.model.CompanionCharges.MAX_PER_COMPANION,
    capsuleHeight: Dp = 11.dp,
    gap: Dp = 2.dp,
    /**
     * **EL USO QUE SE ESTÁ GANANDO** (P-186, 2026-09-05): 0..1 de llenado del **siguiente** hueco
     * vacío. Lo usa la carga del capitán, que se gana jugando (líneas + fusiones).
     *
     * Antes esa barra vivía dentro de la insignia del número, y para no contradecirse tenía que
     * mentir: con la barra llena y cero cargas, la insignia pintaba «1». Aquí no hace falta ningún
     * apaño — el hueco a medio llenar **es** «te falta esto», y el hueco lleno **es** un uso.
     * Se llena de izquierda a derecha porque el medidor es horizontal: el depósito que subía es la
     * metáfora de una insignia redonda, no la de un riel.
     */
    partial: Float = 0f,
) {
    val full = charges >= capsules
    Row(
        // El riel añade exactamente 4 dp de alto (2 arriba, 2 abajo). Está medido: la ranura del
        // cinturón mide 160 dp y su contenido no tiene ni un dp de sobra.
        modifier = modifier
            .height(capsuleHeight + 4.dp)
            .clip(RoundedCornerShape(50))
            // EL RIEL. Sin él las cápsulas apagadas flotan sobre el fondo de la tarjeta y el medidor
            // no se lee como una sola cosa; con él, el hueco vacío tiene dónde estar.
            .background(Color(0xFF0C1024).copy(alpha = 0.62f))
            .drawBehind {
                if (!full) return@drawBehind
                val r = size.height / 2f
                drawRoundRect(
                    color = accent.copy(alpha = 0.85f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r, r),
                    style = Stroke(width = 1.4.dp.toPx()),
                )
            }
            .padding(horizontal = 3.dp, vertical = 2.dp)
            .semantics {
                contentDescription = when {
                    charges == 0 -> "Sin usos"
                    full -> "$charges usos, al máximo"
                    else -> "$charges usos de $capsules"
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(gap)
    ) {
        val fill = partial.coerceIn(0f, 1f)
        repeat(capsules) { i ->
            val on = i < charges
            // El hueco que se está ganando es SIEMPRE el primero vacío: así el medidor se lee de
            // izquierda a derecha sin excepciones y el progreso continúa la fila, no la interrumpe.
            val growing = !on && i == charges && fill > 0f
            Box(
                Modifier
                    .weight(1f)
                    .height(capsuleHeight)
                    .clip(RoundedCornerShape(50))
                    // La capsula VACIA tenia el mismo azul que el fondo de la tarjeta (#2A3158
                    // sobre #273159): en la captura del usuario no se distinguian, asi que un
                    // companero con 3 usos parecia tener un medidor de 3 y no de 5. Hueco oscuro
                    // con filo claro: ahora se ve el sitio vacio, que es la mitad de la
                    // informacion que da un medidor.
                    .background(if (on) accent else Color(0xFF11162E))
                    .border(
                        1.dp,
                        Color.White.copy(alpha = if (on) 0f else if (growing) 0.30f else 0.14f),
                        RoundedCornerShape(50)
                    )
                    .drawBehind {
                        if (growing) {
                            drawRect(
                                color = accent.copy(alpha = 0.60f),
                                size = Size(size.width * fill, size.height),
                            )
                            return@drawBehind
                        }
                        if (!on) return@drawBehind
                        drawRect(
                            Brush.verticalGradient(
                                listOf(Color.White.copy(alpha = 0.45f), Color.Transparent)
                            ),
                            size = Size(size.width, size.height * 0.45f)
                        )
                    }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════════
//  LA LLUVIA DE TEIKOINS (2026-09-05) — la compra se VE
// ════════════════════════════════════════════════════════════════════════════════
//
// Pedido del usuario: «que cuando compre que salgan teikoins alrededor del botón, las monedas pueden
// dar vueltas y esparcirse por el alrededor del botón».
//
// Tres decisiones que valen la pena escribir, porque las tres se pueden hacer mal:
//
//  1. **La capa vive en la RAÍZ de la pantalla, no dentro del botón.** La tarjeta de LA BANCA está
//     recortada (`clip(RoundedCornerShape(18.dp))`) y la rejilla también: una moneda dibujada dentro
//     del botón se cortaría en el borde de su tarjeta a los 20 dp de vuelo. Por eso el botón solo
//     REPORTA su centro en coordenadas de raíz y quien dibuja es la pantalla.
//  2. **Un solo `Canvas` y un solo reloj** para las doce monedas. La alternativa —doce composables
//     animándose— son doce nodos recomponiendo por fotograma en una pantalla que ya tiene catorce
//     tarjetas. Aquí el progreso se lee DENTRO de la lambda de dibujo: se redibuja sin recomponer.
//  3. **Es la moneda de verdad** (`R.raw.teikoin`), no un círculo dorado ni el emoji 🪙. Misma
//     lección que [TeikoinIcon]: dos monedas distintas según la pantalla es justo lo que rompe la
//     ilusión de que todo esto es un solo juego.

/** Una explosión de monedas pendiente de dibujar. [origin] va en coordenadas de la RAÍZ. */
@Immutable
data class CoinBurst(val id: Long, val origin: Offset, val coins: Int = 12)

/** Una moneda de la explosión. Se sortea UNA vez por explosión, nunca por fotograma. */
private class CoinParticle(
    val angle: Float,
    /** Alcance en dp. */
    val reach: Float,
    /** Vueltas por vida (signo = sentido del giro). */
    val spin: Float,
    val tilt: Float,
    val sizeDp: Float,
    /** Fracción del ciclo que dura esta moneda: las que mueren antes escalonan el final. */
    val life: Float,
)

private const val COIN_BURST_MS = 900
/** Cuánto tiran las monedas hacia abajo al final del arco, en dp. */
private const val COIN_BURST_GRAVITY_DP = 74f

/**
 * Dibuja la explosión [burst] y avisa por [onFinished] cuando termina (para que el padre la
 * descarte). Con «reducir efectos» no dibuja nada y termina en el acto: la compra ya sonó y ya se
 * ve en el medidor, así que el jugador no se pierde información.
 *
 * El [modifier] debe darle el mismo sistema de coordenadas en el que se midió `burst.origin` —en la
 * práctica, `fillMaxSize()` sobre la caja raíz de la pantalla.
 */
@Composable
fun CoinBurstLayer(
    burst: CoinBurst?,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduced = LocalReducedFx.current
    val coin = rememberRawImageBitmap(R.raw.teikoin)
    val clock = remember { Animatable(1f) }
    val finish = rememberUpdatedState(onFinished)

    val particles: List<CoinParticle> = remember(burst?.id) {
        val b = burst ?: return@remember emptyList()
        val rnd = Random(b.id)
        List(b.coins) { i ->
            // Abanico hacia ARRIBA (−90°), repartido y con un pellizco de azar: un reparto
            // perfectamente regular se lee como una rueda dentada, y uno del todo aleatorio deja
            // huecos. El abanico abre ~200°, así que también salen monedas hacia los lados.
            val spread = 3.5f / b.coins
            val base = -PI.toFloat() / 2f + (i - (b.coins - 1) / 2f) * spread
            CoinParticle(
                angle = base + (rnd.nextFloat() - 0.5f) * 0.30f,
                reach = 46f + rnd.nextFloat() * 44f,
                spin = (1.4f + rnd.nextFloat() * 1.6f) * (if (rnd.nextBoolean()) 1f else -1f),
                tilt = rnd.nextFloat() * 40f - 20f,
                sizeDp = 13f + rnd.nextFloat() * 8f,
                life = 0.70f + rnd.nextFloat() * 0.30f,
            )
        }
    }

    LaunchedEffect(burst?.id, reduced) {
        val b = burst ?: return@LaunchedEffect
        if (reduced) { finish.value(); return@LaunchedEffect }
        clock.snapTo(0f)
        clock.animateTo(1f, tween(COIN_BURST_MS, easing = LinearEasing))
        finish.value()
    }

    if (burst == null || coin == null || particles.isEmpty() || reduced) return

    val ox = burst.origin.x
    val oy = burst.origin.y
    Canvas(modifier) {
        val p = clock.value            // ← se lee en DIBUJO, nunca en el cuerpo del composable
        if (p >= 1f) return@Canvas

        // EL FOGONAZO: un aro dorado que se abre desde el botón. Es el «ha pasado algo» que ancla la
        // explosión a su origen; sin él las monedas parecen venir de ninguna parte.
        val ring = (p / 0.34f).coerceAtMost(1f)
        if (ring < 1f) {
            drawCircle(
                color = Color(0xFFFFD764).copy(alpha = 0.55f * (1f - ring)),
                radius = 10.dp.toPx() + 34.dp.toPx() * ring,
                center = Offset(ox, oy),
                style = Stroke(width = 2.5.dp.toPx() * (1f - ring * 0.7f)),
            )
        }

        val canvas = drawContext.canvas
        val gravity = COIN_BURST_GRAVITY_DP.dp.toPx()
        particles.forEach { c ->
            val lt = p / c.life
            if (lt >= 1f) return@forEach
            // Sale disparada y frena (ease-out) mientras la gravedad la va bajando: el arco de
            // moneda de siempre, que es lo que hace que se lea como algo con peso.
            val ease = 1f - (1f - lt) * (1f - lt)
            val reach = c.reach.dp.toPx()
            val x = ox + cos(c.angle) * reach * ease
            val y = oy + sin(c.angle) * reach * ease + gravity * lt * lt
            val alpha = if (lt < 0.70f) 1f else 1f - (lt - 0.70f) / 0.30f
            val side = c.sizeDp.dp.toPx() * (0.72f + 0.28f * (1f - lt))
            // EL GIRO: la moneda es un disco, así que girar es aplastar su ancho, no rotarla en
            // plano. Se le deja un mínimo de grosor para que nunca desaparezca del todo.
            val flip = cos(lt * c.spin * 2f * PI.toFloat())
            val squash = abs(flip).coerceAtLeast(0.16f) * (if (flip < 0f) -1f else 1f)

            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(c.tilt + lt * 46f * (if (c.spin > 0f) 1f else -1f))
            canvas.scale(squash, 1f)
            val half = (side / 2f).roundToInt()
            drawImage(
                image = coin,
                dstOffset = IntOffset(-half, -half),
                dstSize = IntSize(half * 2, half * 2),
                alpha = alpha.coerceIn(0f, 1f),
            )
            canvas.restore()
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════════════════════
//  LA GRAMÁTICA DE LA VITRINA, COMPARTIDA (2026-09-06)
//
//  Estas tres piezas nacieron privadas dentro de `InventoryScreen` con el rediseño «Vitrina»
//  (2026-09-04). Hoy la Tienda adopta la misma gramática por petición del usuario —era **P-181**,
//  que quedó anotada aquel día y esperaba a que él viera el Inventario en el móvil—, así que suben
//  aquí en vez de copiarse: dos copias de un mismo dibujo es el bug con nombre propio de este
//  repositorio, y un banderín que se retoca en una pantalla y no en la otra es exactamente eso.
// ═════════════════════════════════════════════════════════════════════════════════════════════
/**
 * El banderín del nombre: una cinta que se apoya SOBRE el borde superior de la tarjeta y sobresale.
 * Es el rasgo que más define la vitrina de la referencia — el nombre nunca vive dentro de la caja.
 */
@Composable
fun NameBanner(name: String, accent: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(9.dp))
            .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.72f))))
            .border(1.4.dp, Color(0xFF10142A).copy(alpha = 0.55f), RoundedCornerShape(9.dp))
            .padding(horizontal = 12.dp, vertical = 3.dp)
    ) {
        Text(
            name.uppercase(),
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Los RAYOS del retrato: el resplandor radial de la referencia, con los rayos finos que salen del
 * centro. Estático y de una sola pasada — se dibuja, no se anima: hay hasta catorce tarjetas.
 */
fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPortraitRays(accent: Color) {
    val r = size.minDimension * 0.62f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.30f),
                accent.copy(alpha = 0.18f),
                Color.Transparent,
            ),
            center = center,
            radius = r,
        ),
        radius = r,
    )
    repeat(12) { i ->
        val a = (i * 30f) * (kotlin.math.PI / 180f).toFloat()
        val inner = r * 0.42f
        val outer = r * (if (i % 2 == 0) 0.98f else 0.72f)
        drawLine(
            color = Color.White.copy(alpha = 0.16f),
            start = Offset(center.x + kotlin.math.cos(a) * inner, center.y + kotlin.math.sin(a) * inner),
            end = Offset(center.x + kotlin.math.cos(a) * outer, center.y + kotlin.math.sin(a) * outer),
            strokeWidth = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

/**
 * **EL RÓTULO DE SECCIÓN** (rediseño 2026-09-04, sobre el render elegido).
 *
 * Centrado y entre dos filetes que se desvanecen hacia los lados. Antes los dos rótulos de esta
 * pantalla —EL CINTURÓN y LA BANCA— eran texto pegado al margen izquierdo, y con eso la pantalla
 * no tenía jerarquía: todo empezaba en la misma vertical, así que todo parecía lo mismo. En la
 * referencia del usuario cada bloque se anuncia igual, y es de las cosas que más barato compran
 * sensación de orden.
 *
 * [trailing] es el hueco del contador («2/3» en el cinturón), que va pegado al título y no suelto
 * al final de la fila: es parte del rótulo, no otra información.
 */
@Composable
fun SectionRule(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.22f))
                    )
                )
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                title,
                color = Color(0xFFCBD6F5),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.6.sp,
            )
            trailing?.invoke()
        }
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )
    }
}
