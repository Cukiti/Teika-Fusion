package com.cukiti.teikafusion.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.R
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ¿La pestaña que contiene a este composable está **a la vista**?
 *
 * El host de pestañas (`ZeroLagTabs`, en `AppNavigation`) mantiene las **cuatro** pantallas compuestas
 * a la vez, siempre — es deliberado, para que cambiar de pestaña sea instantáneo. El precio oculto es
 * que **las animaciones infinitas de las pantallas que NO estás viendo siguen corriendo**:
 * piden un fotograma cada 16 ms, recomponen y vuelven a dibujar en una capa con `alpha = 0`. Estando
 * en Inicio, el juego animaba a 60 fps los brillos de la tienda y del inventario.
 * En un menú, que es donde el móvil debería estar en reposo.
 *
 * Con esto, cada animación decorativa se salta su `rememberInfiniteTransition` cuando su pestaña está
 * oculta y se queda en su valor de reposo. El estado de la pantalla **se conserva igual** (sigue
 * compuesta): lo único que se detiene es el reloj.
 *
 * `staticCompositionLocalOf` a propósito: cambia una vez por cambio de pestaña, y entonces sí
 * queremos que se reevalúe el subárbol entero (es cuando las animaciones deben arrancar).
 */
val LocalTabActive = staticCompositionLocalOf { true }

/**
 * **UN SOLO LATIDO PARA LAS VIDAS INFINITAS** (P-126, 2026-09-04).
 *
 * `infiniteLivesUntil` es un **instante fijo**, así que compararlo una vez en el cuerpo de un
 * composable da una respuesta que se congela: cuando el pack caduca nada recompone al padre y la
 * pantalla sigue anunciando vidas infinitas que ya no existen. Hace falta un reloj.
 *
 * Ese reloj estaba escrito **tres veces** —Inicio, la tarjeta de la tienda y la cuenta atrás—, y no
 * por capricho: el arreglo nació en Inicio y hubo que repetirlo en la tienda meses después, cuando
 * el mismo bug apareció ahí (auditoría de pantallas, 2026-09-03) justo en la pantalla donde el
 * jugador iría a comprar más. Un cuarto sitio que pregunte por las infinitas volvería a tropezar,
 * así que el reloj vive aquí, junto a [LocalTabActive], y devuelve **el tiempo que queda**: de ahí
 * salen tanto el «¿hay infinitas ahora?» como el texto de la cuenta atrás.
 *
 * Coste cero en el caso normal: el bucle solo corre mientras queden infinitas por delante **y** la
 * pestaña esté a la vista (mismo criterio que toda animación ambiente).
 */
@Composable
fun rememberInfiniteLivesRemaining(untilMs: Long, active: Boolean = true): Long {
    val tabActive = LocalTabActive.current
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(untilMs, active, tabActive) {
        nowMs = System.currentTimeMillis()
        while (active && tabActive && untilMs > nowMs) {
            kotlinx.coroutines.delay(1_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    return (untilMs - nowMs).coerceAtLeast(0L)
}

/**
 * **CUÁNTO FALTA PARA LA PRÓXIMA VIDA** (P-167, 2026-09-04), en milisegundos y a la baja.
 *
 * Hermano del de arriba y por el mismo motivo: este `while` estaba escrito dos veces —la cuenta
 * atrás de la tienda y la del overlay «¡Sin vidas!»—, y **no eran iguales**. La de la tienda
 * recalculaba al entrar porque ahí se arregló un bug con su comentario al lado («al regenerarse una
 * vida el valor del `remember` es del objetivo anterior y se mostraba 1 s antes del primer delay»);
 * la del overlay se quedó con la versión vieja y llegaba tarde. Un reloj, una corrección.
 */
@Composable
fun rememberLivesRegenRemaining(nextRegenAtMs: Long, active: Boolean = true): Long {
    val tabActive = LocalTabActive.current
    var remainingMs by remember { mutableLongStateOf(nextRegenAtMs - System.currentTimeMillis()) }
    LaunchedEffect(nextRegenAtMs, active, tabActive) {
        remainingMs = nextRegenAtMs - System.currentTimeMillis()   // recalcular YA, no tras el delay
        while (active && tabActive && remainingMs > 0) {
            kotlinx.coroutines.delay(1_000L)
            remainingMs = nextRegenAtMs - System.currentTimeMillis()
        }
    }
    return remainingMs.coerceAtLeast(0L)
}

/** ¿Hay vidas infinitas AHORA? El mismo reloj de [rememberInfiniteLivesRemaining], en booleano. */
@Composable
fun rememberInfiniteLivesActive(untilMs: Long, active: Boolean = true): Boolean =
    rememberInfiniteLivesRemaining(untilMs, active) > 0L

/**
 * ¿La PARTIDA está corriendo, o está en PAUSA?
 *
 * Hermano de [LocalTabActive] para la pantalla de juego, y por el mismo motivo (auditoría del
 * bloque 20, 2026-07-30): `AppNavigation` mantiene `GameScreen` compuesto durante `PAUSED` —el
 * overlay de pausa se dibuja ENCIMA del tablero, que sigue ahí— así que el fondo del bioma, el
 * reloj de los efectos por celda, las viñetas de amenaza y el aura de carga **seguían animando a
 * 60 fps mientras el jugador leía los ajustes**. La pausa puede durar minutos: es el peor sitio
 * donde dejar el móvil pidiendo fotogramas.
 *
 * Solo gobierna animación AMBIENTE (decorativa y en bucle). Los VFX de evento no lo miran: son
 * cortos, y uno a medias en el momento de pausar debe terminar su gesto, no congelarse.
 *
 * `staticCompositionLocalOf`: cambia al pausar/reanudar, y ahí sí interesa reevaluar el subárbol.
 */
val LocalAnimationsActive = staticCompositionLocalOf { true }

/**
 * **REDUCIR EFECTOS** (ajuste del jugador, P-12). `true` = el espectáculo baja el volumen: sin
 * sacudidas grandes, sin brillos de ambiente, menos partículas. Dos públicos distintos con la misma
 * necesidad — móviles justos de potencia y personas con sensibilidad al movimiento — y arranca
 * respetando el ajuste de accesibilidad del sistema (ver `GameViewModel.reducedFx`).
 *
 * No apaga la INFORMACIÓN: lo que enseña una regla (el beam de la línea, el aviso de un bioma, el
 * telegraph de un castigo) se sigue viendo. Solo se recorta el adorno.
 */
val LocalReducedFx = staticCompositionLocalOf { false }

/** Parte de las partículas que sobrevive con «Reducir efectos» encendido. */
const val REDUCED_FX_PARTICLE_FACTOR = 0.4f

/**
 * Cuántas partículas dibujar de las [full] que pide el efecto (P-67, 2026-08-30).
 *
 * ## Por qué existe
 *
 * «Reducir efectos» llevaba desde su implementación **sin llegar a los biomas**: solo lo miraban
 * `FusionChargeVFX`, `TeikaPushBreakVFX`, los fondos de ambiente y el docking de la bandeja. Los 14
 * biomas —que son lo que de verdad se dibuja durante una partida— costaban exactamente lo mismo en
 * un móvil justo que en uno potente, y el ajuste no cumplía ninguna de sus dos promesas: ni bajar la
 * carga ni bajar el movimiento para quien lo tiene activado por sensibilidad.
 *
 * ## Cómo se usa
 *
 * En el CUERPO del composable (no dentro del `DrawScope`, donde no se puede llamar a un
 * `@Composable`), y el bucle de dibujo recorre el resultado:
 *
 * ```
 * val sparks = fxCount(12)          // 12 normalmente, 5 con efectos reducidos
 * Canvas { for (i in 0 until sparks) { … } }
 * ```
 *
 * **Nunca devuelve 0**: un efecto sin ninguna partícula deja de comunicar lo que comunicaba, y el
 * ajuste recorta el adorno, no la información. Por eso mismo tampoco se aplica a lo que ENSEÑA una
 * regla (el aviso de un bioma, el telegraph de un castigo, el rayo que marca a una víctima).
 */
@Composable
fun fxCount(full: Int): Int =
    if (LocalReducedFx.current) {
        kotlin.math.ceil(full * REDUCED_FX_PARTICLE_FACTOR).toInt().coerceAtLeast(1)
    } else full

/**
 * ¿Está el jugador navegando con **exploración táctil** (TalkBack y compañía)?
 *
 * Sirve para montar el camino de juego accesible —seleccionar ficha y luego celda (P-13)— SOLO
 * cuando hace falta: ese camino añade una nodo semántico por celda del tablero, y con el lector
 * apagado sería peso muerto en cada recomposición de la partida.
 */
@Composable
fun rememberTouchExplorationEnabled(): Boolean {
    val context = androidx.compose.ui.platform.LocalContext.current
    val manager = remember(context) {
        context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
            as? android.view.accessibility.AccessibilityManager
    }
    var enabled by remember { mutableStateOf(manager?.isTouchExplorationEnabled == true) }
    DisposableEffect(manager) {
        val m = manager ?: return@DisposableEffect onDispose { }
        val listener = android.view.accessibility.AccessibilityManager
            .TouchExplorationStateChangeListener { enabled = it }
        m.addTouchExplorationStateChangeListener(listener)
        enabled = m.isTouchExplorationEnabled
        onDispose { m.removeTouchExplorationStateChangeListener(listener) }
    }
    return enabled
}

/**
 * Icono Lottie de un personaje Teika (selector de compañero, overlay de objetivo…).
 *
 * [active] `false` CONGELA la animación (no solo la oculta): un Lottie fuera de vista sigue
 * interpolando keyframes e invalidando el draw cada frame. Por defecto `true`.
 */
@Composable
internal fun DexLottieIcon(resId: Int, modifier: Modifier = Modifier, active: Boolean = true) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    val progress = animateLottieCompositionAsState(
        composition,
        isPlaying = active,
        iterations = LottieConstants.IterateForever
    )
    // `progress` se pasa como lambda: la lectura ocurre en el draw, no en composición.
    LottieAnimation(composition = composition, progress = { progress.value }, modifier = modifier)
}

/**
 * Caché de bitmaps de `res/raw`, **compartida por todo el proceso**.
 *
 * Antes cada `RawImage` guardaba su bitmap en un `remember(resId)`, que es **por instancia de
 * composable**. Como el mismo icono se monta en muchos sitios (la moneda aparece en 17), el juego
 * tenía en memoria **varias copias idénticas** del mismo bitmap a la vez — cinco de la moneda solo
 * en la pantalla de la tienda. Y al ser un `remember`, cada entrada en composición **volvía a
 * decodificar**, y lo hacía en el hilo de composición: un título de 1080 px son 2,5 MB de decode
 * síncrono justo mientras se dibuja el primer fotograma de la pantalla.
 *
 * Ahora se decodifica **una vez por recurso** y todos los sitios comparten el mismo bitmap. La caché
 * es un [LruCache] limitado a 1/8 de la memoria de la app (el reparto estándar de Android para
 * bitmaps), así que bajo presión de memoria se vacía sola en vez de crecer sin techo.
 */
private object RawImageCache {

    private val cache = object : LruCache<Int, ImageBitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(4 * 1024)
    ) {
        /** El tamaño se mide en KB del bitmap decodificado (ARGB_8888 = 4 bytes por píxel). */
        override fun sizeOf(key: Int, value: ImageBitmap): Int =
            (value.width * value.height * 4 / 1024).coerceAtLeast(1)
    }

    /** Lo que YA está en caché, sin decodificar nada. Para pintar en el primer frame si toca. */
    fun peek(resId: Int): ImageBitmap? = cache.get(resId)

    fun get(context: Context, resId: Int, maxPx: Int): ImageBitmap? {
        cache.get(resId)?.let { return it }
        val decoded = decode(context, resId, maxPx) ?: return null
        cache.put(resId, decoded)
        return decoded
    }

    /**
     * Suelta los bitmaps cacheados. Lo llama [com.cukiti.teikafusion.TeikaFusionApp.onTrimMemory]
     * cuando el sistema anda escaso: una caché que solo crece hasta su techo, sin ceder terreno bajo
     * presión, es justo lo que empuja a que te maten el proceso en un móvil de gama baja. Los
     * composables que estén dibujando su bitmap ahora mismo lo tienen referenciado, así que no se
     * les cae de las manos; simplemente se volverá a decodificar la próxima vez que se pida.
     */
    fun trim() {
        cache.evictAll()
    }

    /**
     * Decodifica submuestreando: ningún bitmap se carga a más de [maxPx] por lado.
     *
     * Es una **red de seguridad**, no un ahorro para los assets de hoy: tras la it. 13 el mayor mide
     * 1080 px, así que con el ancho de pantalla como techo `inSampleSize` sale 1 y no se degrada
     * nada. Lo que evita es la trampa que estaba puesta: el día que alguien meta un PNG de 4000 px en
     * `res/raw`, `BitmapFactory` ya no se lo tragará entero (64 MB) — lo bajará al tamaño útil.
     */
    private fun decode(context: Context, resId: Int, maxPx: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.resources.openRawResource(resId).use {
            BitmapFactory.decodeStream(it, null, bounds)
        }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / sample > maxPx || bounds.outHeight / sample > maxPx) {
            sample *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.resources.openRawResource(resId).use {
            BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
        }
    }
}

/**
 * Decodifica por adelantado (llámese FUERA del hilo principal) imágenes de `res/raw` que una pantalla
 * montará más tarde, con el mismo techo que usará su [RawImage]. Así montar la pantalla solo encuentra
 * bitmaps en caché: el Inventario decodificaba 14 iconos en el hilo principal al montarse (~0,6 s
 * congelado en un Galaxy A15, release, 2026-09-30).
 */
fun prewarmRawImages(context: Context, ids: List<Int>, decodeMaxPx: Int = Int.MAX_VALUE) {
    val maxPx = context.resources.displayMetrics.widthPixels.coerceAtLeast(1).coerceAtMost(decodeMaxPx)
    ids.forEach { runCatching { RawImageCache.get(context, it, maxPx) } }
}

/** Suelta los bitmaps de `res/raw` cacheados. Se llama cuando el sistema pide memoria. */
fun trimRawImageCache() = RawImageCache.trim()

/**
 * Imagen desde `res/raw`. El bitmap se decodifica una sola vez y se comparte entre todos los sitios
 * que pidan el mismo recurso (ver [RawImageCache]).
 *
 * [contentDescription] describe la imagen para el lector de pantalla. Se deja en `null` (por
 * defecto) cuando la imagen es **decorativa** o cuando su significado ya lo dice un texto contiguo
 * — por ejemplo el icono de moneda junto al número, donde la descripción va en el chip entero.
 */
@Composable
fun RawImage(
    resId: Int,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    contentDescription: String? = null,
    async: Boolean = false,
    /** Dónde cae la imagen escalada dentro del hueco (la intro la desplaza para centrar su título). */
    alignment: Alignment = Alignment.Center,
    /**
     * Techo propio para imágenes que siempre se ven pequeñas (p. ej. [ABILITY_ICON_MAX_PX]). La caché
     * es por recurso: todos los sitios que pidan el mismo recurso deben pasar el mismo techo.
     */
    decodeMaxPx: Int = Int.MAX_VALUE
) {
    val context = LocalContext.current
    // Techo de decodificación: una imagen nunca necesita más píxeles que el ancho de la pantalla,
    // ni siquiera los títulos, que se dibujan a fillMaxWidth.
    val maxPx = context.resources.displayMetrics.widthPixels.coerceAtLeast(1).coerceAtMost(decodeMaxPx)

    if (!async) {
        val bitmap = remember(resId, maxPx) { RawImageCache.get(context, resId, maxPx) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                modifier = modifier,
                alignment = alignment,
                contentScale = contentScale
            )
        }
        return
    }

    // Ruta ASÍNCRONA (ilustraciones grandes): decodificar un PNG de ~1080 px es trabajo síncrono
    // pesado; hacerlo en el hilo de composición al entrar en pantalla planta un tirón. Aquí se
    // decodifica fuera del hilo principal y la imagen entra con un fundido corto.
    // Si ya estaba en caché se pinta en el PRIMER frame, sin fundido ni salto.
    val cached = RawImageCache.peek(resId)
    var bitmap by remember(resId) { mutableStateOf(cached) }
    LaunchedEffect(resId, maxPx) {
        if (bitmap == null) {
            // `prepareToDraw` sube el bitmap a la GPU YA, en segundo plano: si no, se subía en el
            // primer fotograma que lo dibujaba (111 ms de tirón al aparecer la carga, medido en un A15).
            bitmap = withContext(Dispatchers.Default) {
                RawImageCache.get(context, resId, maxPx)?.also { it.prepareToDraw() }
            }
        }
    }
    val ready = bitmap
    if (ready != null) {
        val fade = remember(resId) { Animatable(if (cached != null) 1f else 0f) }
        LaunchedEffect(ready) { fade.animateTo(1f, tween(180)) }
        Image(
            bitmap = ready,
            contentDescription = contentDescription,
            modifier = modifier.graphicsLayer { alpha = fade.value },
            alignment = alignment,
            contentScale = contentScale
        )
    }
}


/**
 * **LA MONEDA DEL JUEGO, DIBUJADA** (2026-09-04, pedido del usuario: «en el inventario, arriba a la
 * derecha, el icono de la moneda no se está viendo»).
 *
 * Casi toda la app pintaba los teikoins con el **emoji 🪙**. Ese carácter es de Unicode 13 (2020) y
 * lo dibuja la fuente de emoji del sistema: en los móviles cuya fuente no lo trae no sale nada —ni
 * siquiera el cuadradito— y el chip queda como un número suelto sin explicación. Es exactamente lo
 * que se reportó del Inventario, y valía para el crisol, la hoja de habilidades y las etiquetas de
 * precio.
 *
 * La barra superior de la partida y la tienda ya usaban el arte real (`R.raw.teikoin`) «para que sea
 * consistente con el resto de la app»; esto es ese mismo criterio, en un solo sitio, para que no
 * vuelva a haber dos monedas distintas según la pantalla.
 *
 * El bitmap se decodifica una vez y se comparte ([RawImage] / `RawImageCache`), así que repetirlo en
 * una lista no cuesta memoria.
 */
@Composable
fun TeikoinIcon(size: Dp = 14.dp, modifier: Modifier = Modifier) {
    RawImage(resId = R.raw.teikoin, modifier = modifier.size(size))
}

/**
 * Cápsula «icono + cifra» de teikoins. El nodo entero se anuncia como una sola cosa: sin esto el
 * lector de pantalla leería un número sin contexto, que es el mismo problema que tenía la barra
 * superior. Ver [TeikoinIcon].
 */
@Composable
fun TeikoinAmount(
    amount: Int,
    color: Color,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight = FontWeight.Bold,
    iconSize: Dp = 14.dp,
    gap: Dp = 4.dp,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = "$amount Teikoins"
        }
    ) {
        TeikoinIcon(size = iconSize)
        Spacer(Modifier.width(gap))
        Text(
            text = "$amount",
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * El BITMAP crudo de un recurso de `res/raw`, para dibujarlo a mano dentro de un `Canvas`.
 *
 * [RawImage] pinta *un* nodo de composición por imagen, y eso es justo lo que no sirve cuando hay
 * que dibujar la misma moneda doce veces por fotograma con su propia rotación y su propio giro (ver
 * `CoinBurstLayer`): serían doce nodos animándose por frame en vez de un solo `drawImage` repetido
 * dentro de una lambda de dibujo. Comparte la MISMA caché ([RawImageCache]), así que pedir aquí el
 * bitmap no vuelve a decodificar nada de lo que ya pintó la pantalla.
 *
 * Devuelve `null` mientras no haya bitmap: el llamante simplemente no dibuja.
 */
@Composable
internal fun rememberRawImageBitmap(resId: Int): ImageBitmap? {
    val context = LocalContext.current
    val maxPx = context.resources.displayMetrics.widthPixels.coerceAtLeast(1)
    return remember(resId, maxPx) { RawImageCache.get(context, resId, maxPx) }
}
