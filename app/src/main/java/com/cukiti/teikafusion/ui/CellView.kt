package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.cukiti.teikafusion.model.CellContent
import com.cukiti.teikafusion.model.TeikaType
import com.cukiti.teikafusion.ui.theme.*
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import androidx.compose.runtime.getValue
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.TargetColor
import com.cukiti.teikafusion.model.WILDCARD_COLOR

// M-7: brush constante elevado a nivel de archivo — nunca se realoca
private val GHOST_VEIL_BRUSH = Brush.radialGradient(
    listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.14f), Color.Transparent)
)

// ── Bloque-COFRE dorado (estilo Block Blast, ref res/raw/blockblast.jpeg) ──────────────────────────
// Los bloques que llevan ESTRELLA/objetivo son ÚNICOS: siempre se pintan con esta paleta dorada
// (mismo bisel 3D del skin activo) con la gema incrustada sobre un resplandor interior, sin importar
// el color de la ficha de la que nacieron. Lectura instantánea de "esto vale puntos de objetivo".
// La comparten CellView (tablero) y PieceView (bandeja) → el cofre se ve igual antes y después de
// colocar. [base, claro, oscuro] como colorsFor().
internal val GoldChestColors = listOf(Color(0xFFE6A94B), Color(0xFFFFDF8E), Color(0xFF8F5A1D))

/**
 * **LA ESTRELLA VIAJA; EL COLOR MANDA** (2026-09-05, decisión del usuario).
 *
 * El bloque con estrella se pintaba SIEMPRE con [GoldChestColors] («bloque-cofre», estilo Block
 * Blast). Eso lo dejaba **fuera del sistema de color** a ojos del jugador: la Brocha le cambia el
 * `colorType` de verdad —el modelo nunca lo excluyó, solo excluye el cristal, la bobina, la
 * congelada y el testarudo— pero el dorado tapaba el cambio. Reporte del usuario: «los bloques que
 * tienen estrella no cambian de color y eso confunde al jugador».
 *
 * La regla elegida es la que menos excepciones tiene: **la estrella es una CARGA que el bloque lleva
 * encima, no un tipo de bloque aparte**. Un bloque, un color, sin excepciones — y la estrella se
 * sigue viendo porque se dibuja encima, con su propio arte.
 *
 * ## Por qué es una constante y no un `if` borrado
 *
 * El dorado se decidía en **diez sitios**: la celda en reposo, la ficha en la bandeja y ocho VFX
 * (caída, desplome, huida del Miedoso, arrastre de la corriente, rapto, recogida…). Borrarlo en unos
 * y no en otros produce exactamente el bug que se acaba de arreglar en la cáscara de cristal: **el
 * bloque cambiaría de aspecto por el mero hecho de moverse**. Con una sola constante, los diez
 * sitios no pueden discrepar, y volver atrás tras el playtest es cambiar `false` por `true`.
 */
internal const val STAR_BLOCK_OVERRIDES_COLOR = false

/**
 * La paleta con la que se pinta un bloque de madera: la de su color, salvo que la estrella mande
 * (ver [STAR_BLOCK_OVERRIDES_COLOR]). Única puerta para los diez sitios que pintan madera.
 *
 * [hasTarget] es «lleva estrella»; el llamante ya ha descartado el cristal y el comodín, que tienen
 * su propio dibujado y nunca pasaron por el dorado.
 */
internal fun woodPalette(skin: BlockSkin, colorType: Int, hasTarget: Boolean): List<Color> =
    if (hasTarget && STAR_BLOCK_OVERRIDES_COLOR) GoldChestColors
    else skin.colorsFor(colorType.coerceAtLeast(0))
// (RETIRADO 2026-09-03, P-132) `ConcreteColors`: la terracota del muro de la marea. Se fue con la
// bandera `concrete`, que ya no la ponía nadie desde que el modo Infinito se borró el 2026-08-09.
// Resplandor interior tras la gema (brush constante — sin alloc por recomposición).
internal val GoldChestGlowBrush = Brush.radialGradient(
    listOf(Color(0xD9FFF1B8), Color(0x33FFDF8E), Color(0x00FFDF8E))
)

// ── Reloj de animación COMPARTIDO por los overlays de bioma por-celda ──────────────────────────────
// Antes cada celda con efecto (frost/poison/coil/sleep/vine) creaba su PROPIO rememberInfiniteTransition
// → en tableros densos, decenas de relojes independientes. Ahora un ÚNICO frame-loop (BiomeClockDriver,
// montado por GameBoard) avanza este tiempo en SEGUNDOS y cada overlay deriva su onda leyéndolo en fase
// de DIBUJO (onDrawBehind). El tiempo es transitorio (no se persiste).
internal val biomeClockSec = mutableFloatStateOf(0f)
private var biomeClockStartNs = 0L

/**
 * Avanza [biomeClockSec] con un solo `withFrameNanos` mientras [active] (hay celdas de bioma animadas
 * en el tablero). Gated para no forzar 60fps en tableros sin efectos. El baseline se fija una vez, así
 * que activar/desactivar no produce saltos de fase.
 */
@Composable
fun BiomeClockDriver(active: Boolean) {
    val stepNs = rememberAmbientStepNs()
    LaunchedEffect(active, stepNs) {
        if (!active) return@LaunchedEffect
        if (biomeClockStartNs == 0L) biomeClockStartNs = withFrameNanos { it }
        biomeClockRunning.value = true
        try {
            while (true) withFrameNanos { now ->
                // P-368: en pantallas rápidas el reloj solo AVANZA a pasos de 1/30 s; entre paso y paso
                // no se escribe y nada de lo que lo lee se redibuja (ver [rememberAmbientStepNs]).
                val v = ambientNs(now - biomeClockStartNs, stepNs) / 1_000_000_000f
                if (v != biomeClockSec.floatValue) biomeClockSec.floatValue = v
            }
        } finally {
            // Al pararse (salir del tablero, «reducir efectos») lo dice: quien dibuja con el reloj
            // —el destello del Cristal— no debe quedarse con una fase congelada a medio camino.
            biomeClockRunning.value = false
        }
    }
}

/** Frecuencia del AMBIENTE en pantallas de más de 60 Hz (P-368). */
internal const val AMBIENT_FPS = 30

/**
 * **P-368 · El ambiente a 30 fps en pantallas de 90/120 Hz** (2026-09-30, decisión del usuario).
 *
 * El fondo del bioma y el reloj compartido [biomeClockSec] (skins animados, marcas y latidos de bioma)
 * se redibujaban a la tasa de la pantalla: a 120 Hz, cuatro veces lo que necesita una deriva de 9 s o un
 * latido de 2 s, y con cada uno se redibujaban el fondo a pantalla completa y todas las celdas con skin
 * animado. Devuelve el paso en ns al que deben avanzar esos relojes: 1/[AMBIENT_FPS] s si la pantalla va
 * a más de 60 Hz (a 90 Hz es 1 fotograma de cada 3; a 120 Hz, 1 de cada 4: sin tirones de ritmo) y 0 —
 * cada fotograma, como antes— a 60 Hz, donde bajar a 30 se notaría más. Lo que el JUGADOR mueve
 * (arrastre, roturas, física, VFX de eventos) no usa estos relojes y sigue a la tasa de la pantalla.
 */
@Composable
internal fun rememberAmbientStepNs(): Long {
    val view = androidx.compose.ui.platform.LocalView.current
    val hz = view.display?.refreshRate ?: 60f
    return remember(hz) { if (hz > 61f) 1_000_000_000L / AMBIENT_FPS else 0L }
}

/** [elapsedNs] redondeado hacia abajo al paso [stepNs] (0 = sin redondear). */
internal fun ambientNs(elapsedNs: Long, stepNs: Long): Long =
    if (stepNs > 0L) elapsedNs - elapsedNs % stepNs else elapsedNs

/** Pulso suave 0→1→0 con semiperiodo [halfPeriodSec] (equivalente a un tween con RepeatMode.Reverse). */
private fun biomePulse(timeSec: Float, halfPeriodSec: Float): Float =
    0.5f + 0.5f * kotlin.math.sin((Math.PI.toFloat() * timeSec / halfPeriodSec) - (Math.PI.toFloat() / 2f))

/**
 * Escala visual del Lottie de un Teika: compensa el padding interno del asset para que el círculo
 * DIBUJADO coincida con el círculo FÍSICO (la hitbox).
 *
 * **Es UNIFORME para los 5 tipos** ([TEIKA_VISUAL_SCALE]) y así debe seguir: el desajuste que
 * arrastraba el sistema era de escala, no de tier. Medido en los propios Lottie (`teika_1..4` +
 * mega): la bola del cuerpo es una elipse de 404 sobre un lienzo de 500, o sea llena 0,808 de su
 * caja, así que la compensación teórica es 1/0,808 ≈ 1,24; el valor vivo se bajó a **1,22** en
 * playtest (a 1,24 justo las caras ya se rozaban en reposo, y a 1,28 —el número de la primera
 * versión, calculado sobre un padding de 0,781 que ningún asset tenía— los cuerpos se veían
 * solapados: `estadoactual.jpeg`).
 *
 * El composable mide el DIÁMETRO físico (nivel × celda) y la física fusiona a dos iguales cuando sus
 * círculos son TANGENTES, así que con la escala bien calibrada las caras se besan justo al fusionar
 * en vez de hacerlo con un hueco. La capa "respira" alrededor del 100 %: en el pico de la
 * respiración las caras se aprietan un pelo = squash agradable; en reposo, tangentes.
 *
 * Solo afecta al DIBUJO (`graphicsLayer`): no cambia el layout, ni el touch, ni la física.
 * (Los valores por tier de la primera época —1,12-1,18, calibrados al pico de la respiración— están
 * muertos; el `when` de 5 ramas idénticas que quedó de ellos también.)
 */
internal const val TEIKA_VISUAL_SCALE = 1.22f

internal fun teikaVisualScale(@Suppress("UNUSED_PARAMETER") type: TeikaType): Float =
    TEIKA_VISUAL_SCALE

/** La madera es rígida: su entrada puede crecer hasta el límite de la celda, nunca atravesarlo. */
internal fun boundedCellScale(rawScale: Float, isWood: Boolean): Float =
    if (isWood) rawScale.coerceIn(0f, 1f) else rawScale


// ── INFINITO: bloque-CRONÓMETRO (carga la FIEBRE al romper su línea) ──────────
// Esfera de reloj dorada dibujada en Canvas (sin PNG): aro dorado #FFD23E con acento
// #FF6600, 4 marcas horarias y 2 agujas blancas. La usan la celda del tablero, la ficha
// de la bandeja (PieceView) y el vuelo hacia el timer (ChronoCollectVFX).
fun DrawScope.drawChronoBadge(center: Offset, radius: Float) {
    val gold = Color(0xFFFFD23E)
    val accent = Color(0xFFFF6600)
    // Corona (botón superior) + esfera.
    drawRoundRect(
        color = accent,
        topLeft = Offset(center.x - radius * 0.18f, center.y - radius * 1.28f),
        size = Size(radius * 0.36f, radius * 0.34f),
        cornerRadius = CornerRadius(radius * 0.1f)
    )
    drawCircle(color = accent, radius = radius * 1.06f, center = center)
    drawCircle(color = gold, radius = radius * 0.92f, center = center)
    drawCircle(color = Color(0xFF1F2643), radius = radius * 0.72f, center = center)
    // 4 marcas horarias.
    val tick = radius * 0.14f
    listOf(Offset(0f, -1f), Offset(0f, 1f), Offset(-1f, 0f), Offset(1f, 0f)).forEach { dir ->
        drawLine(
            color = gold,
            start = center + Offset(dir.x * radius * 0.62f, dir.y * radius * 0.62f),
            end = center + Offset(dir.x * (radius * 0.62f - tick), dir.y * (radius * 0.62f - tick)),
            strokeWidth = radius * 0.1f,
            cap = StrokeCap.Round
        )
    }
    // Agujas (blancas): minutero arriba, horario a las ~4.
    drawLine(
        color = Color.White,
        start = center, end = center + Offset(0f, -radius * 0.52f),
        strokeWidth = radius * 0.13f, cap = StrokeCap.Round
    )
    drawLine(
        color = Color.White,
        start = center, end = center + Offset(radius * 0.3f, radius * 0.22f),
        strokeWidth = radius * 0.13f, cap = StrokeCap.Round
    )
    drawCircle(color = Color.White, radius = radius * 0.08f, center = center)
}

/**
 * Insignia de cronómetro centrada sobre la CARA del bloque del skin activo (ver [drawChronoBadge]).
 *
 * Usa los mismos `starOffsetXFraction`/`starOffsetYFraction` que la estrella objetivo: el bisel o el
 * labio inferior de cada forma deja la cara por encima del centro geométrico de la celda, y antes el
 * reloj iba clavado al centro (+4% hacia abajo) → en el skin Derrumbe se veía pegado al labio oscuro.
 */
@Composable
fun ChronoBadge(modifier: Modifier = Modifier) {
    val skin = LocalBlockSkin.current
    val dx = skin.starOffsetXFraction
    val dy = skin.starOffsetYFraction
    Canvas(modifier = modifier) {
        drawChronoBadge(
            center = Offset(
                size.width / 2f + size.width * dx,
                size.height / 2f + size.height * dy
            ),
            radius = size.minDimension * 0.30f
        )
    }
}

// Estrella objetivo usando las imágenes PNG (target_red/blue/purple/pink).
@Composable
fun TargetStar(
    targetColor: TargetColor,
    modifier: Modifier = Modifier
) {
    val resId = when (targetColor) {
        TargetColor.RED    -> R.drawable.target_red
        TargetColor.BLUE   -> R.drawable.target_blue
        TargetColor.PURPLE -> R.drawable.target_purple
        TargetColor.PINK   -> R.drawable.target_pink
    }
    Image(
        painter = painterResource(id = resId),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

// Cache de composiciones Lottie por raw-res. rememberLottieComposition las parsea de forma
// ASÍNCRONA, así que devuelve null en el primer frame de CADA instancia nueva del icono. Eso
// producía un parpadeo (1 frame en blanco) en el hand-off estático→VFX de fusión: al ocultar
// el Teika estático y montar el icono volador, este aparecía vacío durante un frame. Cargando
// la composición de forma SÍNCRONA y reutilizándola, el icono nunca queda en blanco al re-montar.
private val teikaCompositionCache =
    java.util.concurrent.ConcurrentHashMap<Int, com.airbnb.lottie.LottieComposition>()

/** Los cinco Lottie de Teika, en el orden en que el juego los necesita. */
private val TEIKA_RAW_RES = intArrayOf(
    R.raw.teika_1, R.raw.teika_2, R.raw.teika_3, R.raw.teika_4, R.raw.mega_teika
)

/**
 * Parsea las cinco composiciones **fuera del hilo principal**, una sola vez por proceso.
 *
 * `fromRawResSync` hace justo lo que dice: parsea el JSON en el hilo que lo llama, y ese hilo es el
 * de COMPOSICIÓN. La primera vez que aparecía un Teika —o el icono del MEGA en el panel de JUGAR
 * NIVEL, o la presentación del objetivo— el árbol se quedaba esperando a un parseo de JSON en mitad
 * del fotograma. Llamando a esto al arrancar (en `Application`, sobre un hilo de fondo), toda
 * llamada posterior es un acierto de caché y el parpadeo que motivó `fromRawResSync` sigue cubierto.
 *
 * Es idempotente y no lanza: si algo falla, `TeikaIcon` reintenta como siempre.
 */
fun warmTeikaCompositions(context: android.content.Context) {
    val app = context.applicationContext
    for (resId in TEIKA_RAW_RES) {
        if (teikaCompositionCache.containsKey(resId)) continue
        runCatching {
            com.airbnb.lottie.LottieCompositionFactory.fromRawResSync(app, resId).value
        }.getOrNull()?.let { teikaCompositionCache[resId] = it }
    }
}

@Composable
fun TeikaIcon(
    type: TeikaType,
    modifier: Modifier = Modifier
) {
    val rawResId = when (type) {
        TeikaType.TEIKA_1 -> R.raw.teika_1
        TeikaType.TEIKA_2 -> R.raw.teika_2
        TeikaType.TEIKA_3 -> R.raw.teika_3
        TeikaType.TEIKA_4 -> R.raw.teika_4
        TeikaType.MEGA_TEIKA -> R.raw.mega_teika
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    // Composición ya parseada (no null en el primer frame) → sin parpadeo al aparecer el VFX.
    val composition = remember(rawResId) {
        teikaCompositionCache[rawResId] ?: run {
            val c = com.airbnb.lottie.LottieCompositionFactory
                .fromRawResSync(context, rawResId).value
            if (c != null) teikaCompositionCache[rawResId] = c
            c
        }
    }
    // LECTURA EN FASE DE DIBUJO (perf, 2026-07-31). Antes esto era `val progress by animate…`: leer
    // el State en el CUERPO del composable recompone `TeikaIcon` **en cada frame**… por cada Teika
    // del tablero. Con cuatro o cinco cuerpos vivos eso son cuatro o cinco recomposiciones por
    // frame durante toda la partida, y el pico llegaba justo al fusionar (nace un cuerpo, se monta
    // otro icono y el burst dibuja encima). Pasando el State como LAMBDA, Lottie lo lee al dibujar:
    // la animación sigue igual de fluida y el árbol de composición no se toca.
    //
    // P-341 (2026-09-27, decisión del usuario): A LOS FPS DE SU ANIMACIÓN. Los Lottie de los Teikas
    // están hechos a 24 fps, pero Lottie interpolaba y los repintaba a la tasa de la pantalla
    // (60/90/120 Hz) — con cinco cuerpos vivos, cientos de repintados por segundo de un dibujo que solo
    // tiene 24 fotogramas distintos. Con `useCompositionFrameRate` el progreso salta de fotograma en
    // fotograma y el `State` no cambia entre medias: 24 repintados por segundo y cuerpo. El MOVIMIENTO
    // del cuerpo no se toca: lo pone la física en `offset {}`, a la tasa de la pantalla.
    val progress = animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        useCompositionFrameRate = true
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress.value },
            modifier = Modifier.fillMaxSize(),
            // P-342 (2026-09-27, decisión del usuario): el cálculo de cada fotograma de Lottie (capas,
            // trazados, transformaciones) sale del hilo principal. Coste aceptado: alguna vez el dibujo
            // puede ir un fotograma por detrás de su progreso, imperceptible en un bucle de respiración.
            asyncUpdates = com.airbnb.lottie.AsyncUpdates.ENABLED
        )
    }
}

/**
 * Celda del tablero.
 *
 * @param shouldAnimate  true sólo la PRIMERA vez que esta celda entra a composición
 *                       (decidido por GameBoard, que mantiene el set de IDs ya animados).
 *                       Evita que Teikas existentes re-disparen su spawn al moverse o
 *                       cuando se limpian líneas cercanas.
 * @param delayMillis    Retraso antes de la animación de aparición (usado para la nueva
 *                       Teika que nace de una fusión, para que espere a que termine el VFX).
 * @param onAnimated     Callback invocado cuando la animación de spawn termina (o se salta).
 */
/**
 * Bioma Cristal: cáscara de vidrio que ENCIERRA el bloque interior (se ven 2 capas: el bloque
 * debajo + la carcasa de cristal encima).
 *
 * **LA CARCASA NO TIENE ESTADO AGRIETADO** (rework Cristal; el parámetro `cracked` se retiró el
 * 2026-09-05). El diseño es que se vea SIEMPRE intacta y que el primer clear la arranque de golpe
 * (`CrystalShatter`), sin pasar por un estado roto visible. El parámetro seguía existiendo, la celda
 * en reposo lo pasaba a `false`… y su réplica en `DrawScope` —la que dibuja el bloque **en vuelo**—
 * lo calculaba con `armor <= 1`, que en este bioma es SIEMPRE cierto: en el bioma Cristal el armor
 * vale 1. Resultado, el bug que reportó el usuario: **el bloque se veía agrietado mientras la
 * corriente lo arrastraba y volvía a estar intacto al aterrizar**.
 *
 * Quitar el parámetro es el arreglo, no pasarle `false`: mientras exista, la copia en vuelo y la
 * celda en reposo pueden volver a discrepar, y esa discrepancia es invisible en el código.
 */
@Composable
private fun CrystalOverlay(modifier: Modifier = Modifier) {
    // Cacheado por tamaño (auditoría de fluidez 2026-09-29): con un skin animado la capa de la celda se
    // regraba cada fotograma, y como Canvas esta carcasa rehacía dos degradados y un trazo cada vez, en
    // CADA bloque de cristal del tablero. Mismos degradados que [drawCrystalShell], su réplica en vuelo.
    Spacer(modifier.drawWithCache {
        val w = size.width; val h = size.height
        val r = CornerRadius(w * 0.18f, h * 0.18f)
        val veil = Brush.verticalGradient(listOf(Color(0x335FD8FF), Color(0x1A2A6CA8)))
        val sheen = Brush.linearGradient(
            listOf(Color(0xCCBFEFFF), Color(0x3380E0FF), Color(0x66FFFFFF)),
            start = Offset(0f, 0f), end = Offset(w, h)
        )
        val rim = Stroke(width = w * 0.055f)
        onDrawBehind {
            // 1. Velo escarchado frío: tiñe el bloque para que se lea "dentro" del vidrio (no aditivo).
            drawRoundRect(brush = veil, cornerRadius = r)
            // 2. Brillo cristalino diagonal (aditivo) — la superficie pulida de la carcasa.
            drawRoundRect(brush = sheen, cornerRadius = r, blendMode = BlendMode.Screen)
            // 3. Borde/canto de la carcasa: rim claro que define la cápsula como capa separada.
            drawRoundRect(color = Color(0xCCDFF7FF), cornerRadius = r, style = rim)
            // 4. Dos reflejos diagonales (facetas).
            drawLine(Color(0xE6FFFFFF), Offset(w * 0.16f, h * 0.10f), Offset(w * 0.40f, h * 0.34f), strokeWidth = w * 0.07f)
            drawLine(Color(0x80FFFFFF), Offset(w * 0.62f, h * 0.18f), Offset(w * 0.78f, h * 0.40f), strokeWidth = w * 0.04f)
        }
    })
}

/**
 * Bioma Eléctrico: bobina con su número de secuencia. CARGADA = anillo cian pulsante + chispas
 * (bloquea su línea); DESCARGADA ([lit]) = aura dorada estable. El número indica el orden de toque.
 * Si [seen] (descubierta antes pero apagada por un cortocircuito) el número se muestra ATENUADO en
 * cian → el circuito es un puzzle de deducción justo y no de memoria pura.
 */
@Composable
internal fun CoilOverlay(number: Int, lit: Boolean, seen: Boolean = false, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        // PERF: sweep + radiales (con `center` dependiente del tamaño) se construyen UNA vez por
        // tamaño/estado en el bloque de caché; el `pulse` se aplica vía el alpha del dibujo en
        // onDrawBehind → 0 asignaciones de Brush por frame.
        Spacer(Modifier.fillMaxSize().drawWithCache {
            val w = size.width
            val c = Offset(w / 2f, size.height / 2f)
            val ringR = w * 0.34f
            val ringW = w * 0.13f
            val innerR = ringR - ringW * 0.5f
            val metalBrush = Brush.sweepGradient(
                listOf(
                    Color(0xFFE9EEF5), Color(0xFF7C8696), Color(0xFFCED7E2),
                    Color(0xFF5A6472), Color(0xFFE9EEF5)
                ),
                center = c
            )
            // Colores base (alpha=1); el pulso se multiplica con el alpha del dibujo.
            val cyanHalo = Brush.radialGradient(
                listOf(Color(0xFF35E0FF), Color.Transparent), center = c, radius = w * 0.58f
            )
            val goldGlow = Brush.radialGradient(
                listOf(Color(0xFFFFE680), Color.Transparent), center = c, radius = w * 0.62f
            )
            val goldCore = Brush.radialGradient(
                listOf(Color(0xFFFFFBE8), Color(0xFFFFD24D), Color(0xFFE08A00)), center = c, radius = innerR
            )
            onDrawBehind {
                // Pulso 0→1→0 (semiperiodo 0.9s) del reloj de bioma compartido (fase de dibujo).
                val pulse = biomePulse(biomeClockSec.floatValue, 0.9f)
                // Anillo metálico (sweep gradient → brillo de metal pulido).
                drawCircle(brush = metalBrush, radius = ringR, center = c, style = Stroke(width = ringW))
                if (!lit) {
                    // Apagada (cargada): círculo oscuro + halo cian pulsante → bloquea su línea.
                    drawCircle(brush = cyanHalo, radius = w * 0.58f, center = c,
                        alpha = 0.30f + 0.25f * pulse, blendMode = BlendMode.Screen)
                    drawCircle(Color(0xFF09131A), radius = innerR, center = c)
                    drawCircle(Color(0xFF35E0FF).copy(alpha = 0.55f + 0.35f * pulse),
                        radius = innerR, center = c, style = Stroke(width = w * 0.04f))
                } else {
                    // Encendida (descargada): dorado eléctrico tipo bombillo, con bloom pulsante.
                    val glow = 0.7f + 0.3f * pulse
                    drawCircle(brush = goldGlow, radius = w * 0.62f, center = c,
                        alpha = 0.55f * glow, blendMode = BlendMode.Screen)
                    drawCircle(brush = goldCore, radius = innerR, center = c)
                }
            }
        })
        // El número permanece OCULTO hasta descubrirlo. Encendida → dorado sobre el bombillo; ya
        // descubierta pero apagada por cortocircuito ([seen]) → cian atenuado (deducción justa).
        if (number > 0 && (lit || seen)) {
            Text(
                text = number.toString(),
                color = if (lit) Color(0xFF5A3A00) else Color(0xFF9FE8FF).copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

/**
 * Bioma Tóxico: LÍQUIDO verde vivo sobre un bloque envenenado. Superficie ondulante (menisco), cuerpo
 * con gradiente, burbujas que suben y borde tóxico brillante. La animación es un bucle continuo.
 */
/**
 * P-324 · La ENREDADERA del Jardín: tallos verdes que abrazan el bloque, con hojas. Estática.
 */
@Composable
private fun VineOverlay(modifier: Modifier = Modifier) {
    Spacer(modifier.clip(RoundedCornerShape(percent = 18)).drawWithCache {
        val w = size.width; val h = size.height
        val stem = Color(0xFF2E7D32)
        val leaf = Color(0xFF7CD65A)
        onDrawBehind {
            drawRect(Color(0x552E7D32))
            drawLine(stem, Offset(0f, h * 0.20f), Offset(w, h * 0.80f), strokeWidth = w * 0.09f)
            drawLine(stem, Offset(w * 0.15f, h), Offset(w * 0.70f, 0f), strokeWidth = w * 0.07f)
            drawCircle(leaf, radius = w * 0.11f, center = Offset(w * 0.30f, h * 0.36f))
            drawCircle(leaf, radius = w * 0.10f, center = Offset(w * 0.74f, h * 0.64f))
            drawCircle(leaf, radius = w * 0.08f, center = Offset(w * 0.52f, h * 0.18f))
        }
    })
}

/**
 * P-322 · El LODO del Pantano Tóxico: fango verde oscuro que cubre el bloque podrido, con grumos y una
 * película aceitosa. Estático (sin reloj): es un bloque muerto, no una amenaza viva como el veneno.
 */
@Composable
private fun SludgeOverlay(modifier: Modifier = Modifier) {
    Spacer(modifier.clip(RoundedCornerShape(percent = 18)).drawWithCache {
        val w = size.width; val h = size.height
        val body = Brush.verticalGradient(
            0f to Color(0xF2566B3A), 0.55f to Color(0xF23F4F2A), 1f to Color(0xF22A3319),
        )
        val cr = CornerRadius(w * 0.18f, h * 0.18f)
        onDrawBehind {
            drawRoundRect(body, cornerRadius = cr)
            drawRoundRect(Color(0x33B6FF6E), topLeft = Offset(w * 0.12f, h * 0.10f),
                size = Size(w * 0.56f, h * 0.16f), cornerRadius = CornerRadius(w * 0.08f))
            drawCircle(Color(0xFF2A3319), radius = w * 0.12f, center = Offset(w * 0.32f, h * 0.55f))
            drawCircle(Color(0xFF2A3319), radius = w * 0.08f, center = Offset(w * 0.68f, h * 0.70f))
            drawCircle(Color(0x667CEA5F), radius = w * 0.05f, center = Offset(w * 0.62f, h * 0.38f))
        }
    })
}

@Composable
fun CellView(
    content: CellContent,
    modifier: Modifier = Modifier,
    shouldAnimate: Boolean = true,
    delayMillis: Int = 0,
    ghostly: Boolean = false,
    // Perf: cuando el tablero se llena de Teikas (comodines que no se consumen), GameBoard pasa false
    // para APAGAR el "respiro" idle de cada Teika → menos corutinas infinitas simultáneas. Es imperceptible
    // con muchos Teikas y ahorra trabajo en gama baja.
    breathe: Boolean = true,
    // P-251: desfase de esta celda en la onda del destello (el Cristal); el tablero pasa `x + y`.
    glintPhase: Float = 0f,
    onAnimated: () -> Unit = {}
) {
    val skin = LocalBlockSkin.current

    // Compute skip flag here so we can use it for the initial scale value.
    val skipAnimation = when (content) {
        is CellContent.Teika -> content.skipSpawnAnimation
        is CellContent.Wood  -> content.skipSpawnAnimation
        else                 -> true
    }

    // Start at 0f for cells that will play the spawn animation → eliminates the
    // one-frame full-size flash that occurred when scale started at 1f and then
    // LaunchedEffect snapped it to 0.15f on the next dispatch.
    val scale = remember {
        Animatable(if (shouldAnimate && !skipAnimation && content !is CellContent.Empty) 0f else 1f)
    }
    // Squash vertical del "aterrizaje" del spawn: mismo lenguaje que la ficha al caer (parón + aplastado).
    // 1f = sin deformar. Se usa junto a `scale` en el graphicsLayer de abajo.
    val squashY = remember { Animatable(1f) }

    // LaunchedEffect(Unit) fires exactly ONCE per composable instance.
    // A Teika that only moves (same key, new rootPosition) does NOT re-trigger this.
    LaunchedEffect(Unit) {
        if (content is CellContent.Empty || !shouldAnimate || skipAnimation) {
            if (scale.value != 1f) scale.snapTo(1f)
            onAnimated()
            return@LaunchedEffect
        }

        // scale is already 0f; wait for VFX if needed, then "aterriza":
        if (delayMillis > 0) {
            kotlinx.coroutines.delay(delayMillis.toLong())
        }

        scale.snapTo(0.15f)
        if (content is CellContent.Teika) {
            // Solo los TEIKAS hacen el "aterrizaje" con HIT-STOP: suben con overshoot, se congelan un
            // instante en el pico con un squash (aplastado) y asientan con rebote → mismo "parón" que la
            // ficha al tocar destino. La madera NO usa esto (se veía raro que la madera se aplastara).
            scale.animateTo(1.14f, tween(130, easing = FastOutSlowInEasing))
            launch {
                squashY.snapTo(0.84f)
                squashY.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            kotlinx.coroutines.delay(70)   // HIT-STOP: breve parón en el pico
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        } else {
            // Madera y demás: pop simple con rebote (sin squash), como antes.
            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        }
        onAnimated()
    }

    Box(
        modifier = modifier.graphicsLayer {
            val boundedScale = boundedCellScale(scale.value, content is CellContent.Wood)
            scaleX = boundedScale
            scaleY = boundedScale * squashY.value
        },
        contentAlignment = Alignment.Center
    ) {
        val showGhost = ghostly && content !is CellContent.Empty
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (showGhost) 0.36f else 1f),
            contentAlignment = Alignment.Center
        ) {
        when (content) {
            is CellContent.Empty -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(color = EmptyCellBase, shape = RoundedCornerShape(4.dp))
                )
            }
            is CellContent.Wood -> {
                val isWildcardCell = content.colorType == WILDCARD_COLOR
                val isCrystal = content.armor > 0
                // Cristal y comodín conservan su look propio (su cáscara/tile es más informativa
                // que cualquier paleta), así que nunca entran en la decisión del cofre.
                val isGoldChest = content.targetColor != null && !isCrystal && !isWildcardCell
                // P-71: el TESTARUDO conserva SU color aunque lleve estrella, y es la clave de la
                // mecánica — el color es literalmente lo que hay que llevarle.
                //
                // Desde 2026-09-05 esa excepción ya no es una excepción: **ningún** bloque con
                // estrella pierde su color (ver [STAR_BLOCK_OVERRIDES_COLOR]). Se deja el
                // `stubborn` escrito porque su razón es más fuerte que la regla general y tiene que
                // sobrevivir aunque algún día se vuelva a encender el dorado.
                val woodColors = when {
                    content.stubborn -> skin.colorsFor(content.colorType)
                    else -> woodPalette(skin, content.colorType, isGoldChest)
                }

                // Bioma Cristal: squash + rebote cuando la armadura baja (se agrieta).
                val crackBounce = remember { Animatable(1f) }
                var prevArmor by remember { mutableIntStateOf(content.armor) }
                LaunchedEffect(content.armor) {
                    if (content.armor != prevArmor) {
                        prevArmor = content.armor
                        crackBounce.snapTo(0.62f)
                        crackBounce.animateTo(
                            1f,
                            spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium)
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            if (isCrystal) {
                                // squash: al golpear se aplasta (ancho↑, alto↓) y rebota a 1.
                                scaleX = 2f - crackBounce.value
                                scaleY = crackBounce.value
                            }
                        }
                        .then(
                            if (isWildcardCell)
                                Modifier.wildcardTile(cornerFraction = 0.34f)
                            else
                                Modifier.styledTile(
                                    skin.style,
                                    base = woodColors[0], light = woodColors[1], dark = woodColors[2],
                                    cornerFraction = skin.cornerFraction,
                                    // La onda del destello cruza el tablero en diagonal.
                                    glintPhase = glintPhase,
                                    // El dibujo (veta, grietas, constelación…) viaja CON el bloque.
                                    variant = tileVariantOf(content.id),
                                )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // P-71: marco grueso con muescas — «este no se rompe con cualquiera». Se dibuja
                    // en el tono OSCURO de su propio color para que la marca y el dato que hay que
                    // leer (de qué color es) sean la misma cosa.
                    if (content.stubborn) {
                        val edge = skin.colorsFor(content.colorType).getOrElse(2) { Color.Black }
                        // Cacheado por tamaño: la celda se regraba cada fotograma con un skin animado.
                        Spacer(modifier = Modifier.fillMaxSize().drawWithCache {
                            val w = size.width
                            val h = size.height
                            val t = size.minDimension * 0.13f
                            val frame = Stroke(width = t)
                            // Cuatro muescas hacia dentro: la silueta dice "remachado".
                            val n = size.minDimension * 0.17f
                            val notches = arrayOf(
                                androidx.compose.ui.geometry.Offset(w / 2f, t),
                                androidx.compose.ui.geometry.Offset(w / 2f, h - t),
                                androidx.compose.ui.geometry.Offset(t, h / 2f),
                                androidx.compose.ui.geometry.Offset(w - t, h / 2f),
                            )
                            onDrawBehind {
                                drawRect(
                                    color = edge.copy(alpha = 0.95f),
                                    topLeft = androidx.compose.ui.geometry.Offset(t / 2f, t / 2f),
                                    size = androidx.compose.ui.geometry.Size(w - t, h - t),
                                    style = frame,
                                )
                                for (c in notches) {
                                    drawCircle(color = edge.copy(alpha = 0.95f), radius = n * 0.32f, center = c)
                                }
                            }
                        })
                    }
                    if (content.targetColor != null) {
                        // Cofre dorado: resplandor interior tras la gema (la joya "incrustada").
                        if (isGoldChest) {
                            Box(Modifier.matchParentSize().background(GoldChestGlowBrush))
                        }
                        TargetStar(
                            targetColor = content.targetColor,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = skin.starScale; scaleY = skin.starScale
                                    translationX = size.width * skin.starOffsetXFraction
                                    translationY = size.height * skin.starOffsetYFraction
                                }
                        )
                    }
                    // Bioma Cristal: facetas translúcidas + grietas cuando armor == 1.
                    if (isCrystal) {
                        // Rework Cristal (armor=1): la carcasa se ve SIEMPRE intacta — el 1er clear la
                        // arranca de golpe (CrystalShatter), sin pasar por un estado agrietado visible.
                        // P-294: el blindaje que trae un METEORITO se ve como roca, no como cristal.
                        if (content.rock) SpaceRockOverlay(
                            modifier = Modifier.fillMaxSize(),
                            // P-319: la grieta avisa de que esta roca va a contagiar a un vecino.
                            crackStage = com.cukiti.teikafusion.model.MeteorShower.crackStage(content),
                        )
                        else CrystalOverlay(modifier = Modifier.fillMaxSize())
                    }
                    // Bioma Eléctrico: bobina (apagada = negra+anillo metálico / encendida = bombillo).
                    if (content.coil > 0) {
                        CoilOverlay(number = content.coil, lit = content.coilLit, seen = content.coilSeen, modifier = Modifier.fillMaxSize())
                    }
                    // Bioma Tóxico: el veneno NO se pinta aquí desde el 2026-09-28 — lo pinta `ToxicLiquidLayer`
                    // sobre el tablero entero, como una sola masa (una celda no ve a sus vecinas).
                    // P-324 · Jardín: la enredadera que deja la raíz.
                    if (content.vine) {
                        VineOverlay(modifier = Modifier.fillMaxSize())
                    }
                    // P-323 · Dojo en Sombras: la cicatriz de oro que deja el kintsugi.
                    if (content.kintsugi) {
                        KintsugiOverlay(modifier = Modifier.fillMaxSize())
                    }
                    // P-322 · Pantano Tóxico: el LODO que deja un bloque podrido.
                    if (content.sludge) {
                        SludgeOverlay(modifier = Modifier.fillMaxSize())
                    }
                }
            }
            is CellContent.Teika -> {
                // A-2: breathing via Animatable pero con graphicsLayer para leer el estado
                // en la fase de DIBUJO (no en composición) → elimina recomposición por frame.
                val breathScale = remember { Animatable(1f) }
                LaunchedEffect(breathe) {
                    if (!breathe) { breathScale.snapTo(1f); return@LaunchedEffect }
                    val phase = ((content.id.hashCode() and 0x7FFFFFFF) % 2200).toLong()
                    kotlinx.coroutines.delay(phase)
                    while (true) {
                        breathScale.animateTo(1.045f, tween(2000, easing = FastOutSlowInEasing))
                        breathScale.animateTo(1.000f, tween(2000, easing = FastOutSlowInEasing))
                    }
                }
                val visualScale = teikaVisualScale(content.type)
                TeikaIcon(
                    type     = content.type,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val s = visualScale * breathScale.value
                            scaleX = s; scaleY = s
                        }
                )
            }
        }
        }
        // Velo fantasmal (Fantasma / Atravesar): tinte blanco neblinoso sobre la ficha.
        if (showGhost) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(if (content is CellContent.Teika) 8.dp else 4.dp))
                    .background(GHOST_VEIL_BRUSH)  // M-7: brush constante, sin alloc
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Recubrimientos en DrawScope (para dibujarlos sobre un bloque que CAE en el VFX
//  de gravedad, además de en la celda estática). Mismo look que CrystalOverlay /
//  ToxicLiquidLayer, dibujado en [topLeft]..[topLeft]+[size].
// ════════════════════════════════════════════════════════════════════════════

/**
 * Cáscara de cristal sobre el bloque en [topLeft]/[size] — réplica en `DrawScope` de
 * [CrystalOverlay] para el bloque **en vuelo** (caída, desplome, arrastre de la corriente).
 *
 * Como su original, **no tiene estado agrietado**: era justo la diferencia entre las dos copias lo
 * que producía el bug de 2026-09-05 (agrietado mientras se movía, intacto al posarse). Si algún día
 * vuelve a haber grietas, tienen que aparecer **aquí y en [CrystalOverlay] a la vez**, o el bloque
 * volverá a cambiar de aspecto por el mero hecho de moverse.
 */
fun DrawScope.drawCrystalShell(topLeft: Offset, size: Size) {
    val p = crystalShellPaint(size)
    translate(topLeft.x, topLeft.y) {
        val w = size.width; val h = size.height
        drawRoundRect(brush = p.veil, size = size, cornerRadius = p.corner)
        drawRoundRect(brush = p.sheen, size = size, cornerRadius = p.corner, blendMode = BlendMode.Screen)
        drawRoundRect(color = Color(0xCCDFF7FF), size = size, cornerRadius = p.corner, style = p.rim)
        drawLine(Color(0xE6FFFFFF), Offset(w * 0.16f, h * 0.10f), Offset(w * 0.40f, h * 0.34f), strokeWidth = w * 0.07f)
        drawLine(Color(0x80FFFFFF), Offset(w * 0.62f, h * 0.18f), Offset(w * 0.78f, h * 0.40f), strokeWidth = w * 0.04f)
    }
}

/**
 * Degradados y trazo de la carcasa, construidos UNA vez por tamaño (auditoría de fluidez 2026-09-29):
 * la carcasa viaja con el bloque que huye, cae o arrastra la corriente, y antes rehacía dos `Brush` y un
 * `Stroke` por bloque y por fotograma. Solo se usa desde dibujo (hilo principal).
 */
private class CrystalShellPaint(val size: Size, val veil: Brush, val sheen: Brush, val rim: Stroke, val corner: CornerRadius)

private var crystalShellCache: CrystalShellPaint? = null

private fun crystalShellPaint(size: Size): CrystalShellPaint {
    crystalShellCache?.takeIf { it.size == size }?.let { return it }
    val w = size.width; val h = size.height
    return CrystalShellPaint(
        size = size,
        veil = Brush.verticalGradient(listOf(Color(0x335FD8FF), Color(0x1A2A6CA8)), startY = 0f, endY = h),
        sheen = Brush.linearGradient(
            listOf(Color(0xCCBFEFFF), Color(0x3380E0FF), Color(0x66FFFFFF)),
            start = Offset(0f, 0f), end = Offset(w, h)
        ),
        rim = Stroke(width = w * 0.055f),
        corner = CornerRadius(w * 0.18f, h * 0.18f),
    ).also { crystalShellCache = it }
}

/** Capa tóxica (baba verde estática) sobre el bloque en [topLeft]/[size]. Versión simplificada de
 *  [ToxicLiquidLayer] (sin burbujas animadas) para el breve viaje de la caída por gravedad. */
fun DrawScope.drawPoisonLayer(topLeft: Offset, size: Size) {
    val p = poisonLayerPaint(size)
    translate(topLeft.x, topLeft.y) {
        val w = size.width; val h = size.height
        val baseLevel = h * 0.28f
        clipPath(p.clip) {
            drawRect(brush = p.goo, topLeft = Offset(0f, baseLevel), size = Size(w, h - baseLevel))
            drawLine(Color(0xFFC4FF93), Offset(0f, baseLevel), Offset(w, baseLevel), strokeWidth = h * 0.03f, cap = StrokeCap.Round)
        }
    }
}

/** Recorte y degradado de la baba, UNA vez por tamaño (antes un `Path` y un `Brush` por bloque y fotograma). */
private class PoisonLayerPaint(val size: Size, val clip: Path, val goo: Brush)

private var poisonLayerCache: PoisonLayerPaint? = null

private fun poisonLayerPaint(size: Size): PoisonLayerPaint {
    poisonLayerCache?.takeIf { it.size == size }?.let { return it }
    val w = size.width; val h = size.height
    val baseLevel = h * 0.28f
    return PoisonLayerPaint(
        size = size,
        clip = Path().apply { addRoundRect(RoundRect(0f, 0f, w, h, CornerRadius(w * 0.18f, h * 0.18f))) },
        goo = Brush.verticalGradient(
            0f to Color(0xCC7CEA5F), 0.45f to Color(0xCC3FA82E), 1f to Color(0xE61C6B1A),
            startY = baseLevel, endY = h
        ),
    ).also { poisonLayerCache = it }
}
