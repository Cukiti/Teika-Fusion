package com.cukiti.teikafusion.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.ScaleFactor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.LottieCompositionFactory
import com.airbnb.lottie.compose.*
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.LoadingTips
import com.cukiti.teikafusion.model.NamedTeikaRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LA INTRO (P-372…P-375, 2026-09-30). Una sola escena en cuatro tiempos, pensada para que la carga
// real ocurra DEBAJO de algo que mirar y no alargue nada:
//
//   1. ESTUDIO   — sobre negro, como la splash nativa (P-375): palitos de colores forman «CUKITI»,
//                  el color se va al fondo y la palabra se acerca hasta desvanecerse ([CukitiLogoReveal]).
//                  4,0 s (1,3 s con «Reducir efectos»); un toque la salta. Acaba en NEGRO, y ahí se
//                  monta el menú y arranca la precarga (ver «EL NEGRO»).
//   2. CARGA     — la ilustración de la intro, la barra que recorre un Teika que CRECE al avanzar
//                  (T1 → MEGA) y consejos. Sin porcentaje: el número solo delata la espera.
//   3. TÍTULO    — la ilustración (que trae el título dibujado) con zoom lento, entra la música
//                  del menú y «Toca para jugar» a los 650 ms.
//   4. SALIDA    — la intro se funde (INTRO_LEAVE_MS) sobre el destino ya montado: Inicio, términos o lección 1.
//
// Duración real: ~7 s hasta poder tocar (firma 4,0 + negro 0,18 + carga ≥2,4 + título 0,65), medido el
// 2026-10-01. Es DECISIÓN DEL USUARIO que no se acorte (P-417 descartada). Solo existe en ARRANQUE EN FRÍO: volver de
// segundo plano no pasa por `GameStatus.SPLASH`, y girar el móvil conserva la fase (rememberSaveable).
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

private enum class IntroPhase { STUDIO, LOADING, TITLE, LEAVING }

/** Marca de proceso de la precarga de la intro: dura lo que la caché de Lottie que llena. */
private object IntroPreload {
    @Volatile var done: Boolean = false
}

/** Un peldaño de la escalera de fusión que recorre la barra: T1 → T2 → T3 → T4 → MEGA. */
private data class LoadingRung(val lottieResId: Int, val barColor: Color)

private val LoadingLadder = listOf(
    LoadingRung(R.raw.teika_1, Color(0xFFFF5252)),
    LoadingRung(R.raw.teika_2, Color(0xFF448AFF)),
    LoadingRung(R.raw.teika_3, Color(0xFF69F0AE)),
    LoadingRung(R.raw.teika_4, Color(0xFFE040FB)),
    LoadingRung(R.raw.mega_teika, Color(0xFFFFE57F))
)

/** La carga dura al menos esto (tiempo de animación): se ve crecer al Teika entero y se lee un consejo. */
private const val LOADING_MIN_MS = 2_000f
/** Tope de la espera de cuenta. Ver la nota «PERO CON TOPE» dentro de la fase de carga. */
private const val ACCOUNT_WAIT_MAX_MS = 8_000L
/** El toque no cuenta hasta que el logo ha caído: si no, un toque impaciente lo pisaba. */
private const val TITLE_PROMPT_DELAY_MS = 650f
/** P-418 (2026-10-01): a 2,6 s no cambiaba nunca, porque la carga dura ~2,4 s. A 1,3 s se leen dos. */
private const val TIP_MS = 1_300L
/** Negro sostenido entre la firma y la carga, después de montar el menú. */
private const val NEGRO_MS = 180f
/**
 * Fundido de salida de la intro. Lo aplica `AppNavigation` como `exit` de la capa del splash: la
 * intro avisa de que ha terminado AL TOCAR, el destino real (Inicio, los términos o el tutorial) se
 * monta debajo y la intro se desvanece encima de él. Antes se fundía ella sola y DESPUÉS avisaba, así
 * que el fundido siempre revelaba Inicio aunque el destino fuera otro (reporte del usuario, 2026-09-30).
 */
const val INTRO_LEAVE_MS = 380
/** La intro se queda quieta este rato antes de fundirse: deja pasar el fotograma pesado del destino. */
const val INTRO_LEAVE_DELAY_MS = 150

private val IntroTextShadow = TextStyle(
    shadow = Shadow(color = Color.Black.copy(alpha = 0.8f), offset = Offset(0f, 2f), blurRadius = 4f)
)

/**
 * @param onStudioSting la firma sonora del estudio (atraviesa la cortina de carga).
 * @param onTitleShown  entra la pantalla de título: es cuando arranca la música del menú.
 * @param onStart       el jugador toca para jugar (sonido + vibración, aún con la escena encima).
 * @param onStudioFinished la pantalla está en NEGRO tras la firma: es el momento de montar el menú
 *                      debajo (ver `introMenusReady` en `AppNavigation`), lo más pesado del arranque.
 * @param onComplete    el jugador ha tocado para jugar: se pasa YA a Inicio, los términos o el nivel 1;
 *                      la intro se funde encima (ver [INTRO_LEAVE_MS]).
 */
@Composable
fun SplashScreen(
    accountReady: Boolean,
    reducedFx: Boolean,
    onStudioSting: () -> Unit,
    onTitleShown: () -> Unit,
    onStart: () -> Unit,
    onStudioFinished: () -> Unit,
    onComplete: () -> Unit
) {
    // P-219 · girar el móvil no reinicia la intro: la fase y la precarga sobreviven a la recreación.
    var phaseName by rememberSaveable { mutableStateOf(IntroPhase.STUDIO.name) }
    val phase = IntroPhase.valueOf(phaseName)
    var studioMounted by remember { mutableStateOf(phase == IntroPhase.STUDIO) }
    // La firma acabó (o se saltó) y la pantalla está en negro: ver el efecto «EL NEGRO» de abajo.
    var enNegro by remember { mutableStateOf(false) }
    // La precarga llena la caché de Lottie, que vive en la MEMORIA DEL PROCESO: su marca tiene que
    // vivir lo mismo. Con `rememberSaveable` sobrevivía a que Android matara el proceso y, al volver
    // con la caché vacía, se saltaba la precarga (P-438, auditoría 2026-10-01). [IntroPreload] aguanta
    // un giro del móvil (mismo proceso) y se pierde con el proceso, como la caché.
    var precargaHecha by remember { mutableStateOf(IntroPreload.done) }
    // La precarga NO arranca con la app: compite con la firma, que es lo único que se ve. Arranca
    // en el negro (o ya, si la Activity se recrea en plena carga).
    var iniciarPrecarga by remember { mutableStateOf(phase != IntroPhase.STUDIO) }
    val precarga = remember { mutableFloatStateOf(if (precargaHecha) 1f else 0f) }
    // Lo que se VE en la barra: la acompasa el reloj de la intro (ver la fase de carga).
    val barra = remember { mutableFloatStateOf(0f) }
    var esperandoCuenta by remember { mutableStateOf(false) }
    var puedeEmpezar by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val accountReadyState = rememberUpdatedState(accountReady)
    val tips = remember { LoadingTips.sessionOrder() }

    // Los iconos del Inventario se decodifican ya, en segundo plano, para que montar el menú en el
    // negro no los decodifique en el hilo principal (ver [prewarmRawImages]).
    LaunchedEffect(Unit) {
        withContext(Dispatchers.Default) {
            prewarmRawImages(context, NamedTeikaRegistry.all.map { abilityIconRes(it.id) }, ABILITY_ICON_MAX_PX)
        }
        introLog("iconos precalentados")
    }

    // CARGA REAL: precalentamos todas las composiciones Lottie pesadas (Teikas + 14 compañeros) en
    // un hilo de IO. LottieCompositionFactory cachea por clave "rawRes_<id>", la misma que usa
    // rememberLottieComposition → sin parpadeo ni jank de primer frame durante la partida.
    LaunchedEffect(iniciarPrecarga) {
        if (!iniciarPrecarga || precargaHecha) return@LaunchedEffect
        val ids = buildList {
            addAll(LoadingLadder.map { it.lottieResId })
            addAll(NamedTeikaRegistry.all.map { it.lottieResId })
        }.distinct()
        val total = ids.size.coerceAtLeast(1)
        var loaded = 0
        for (id in ids) {
            withContext(Dispatchers.IO) {
                runCatching { LottieCompositionFactory.fromRawResSync(context, id).value }
            }
            loaded++
            precarga.floatValue = loaded.toFloat() / total
        }
        IntroPreload.done = true
        precargaHecha = true
        introLog("precarga Lottie hecha")
    }
    LaunchedEffect(accountReady) { if (accountReady) introLog("cuenta y perfil listos") }

    // Detector de tirones de la intro (solo debug): anota cada fotograma que tarda más de 30 ms.
    if (introLogOn) {
        val faseActual by rememberUpdatedState(phase)
        LaunchedEffect(Unit) {
            var antes = withFrameNanos { it }
            while (true) {
                val ahora = withFrameNanos { it }
                val ms = (ahora - antes) / 1_000_000
                if (ms > 30) introLog("tirón de $ms ms en $faseActual")
                antes = ahora
            }
        }
    }

    // ── EL NEGRO (2026-09-30, medido en un Galaxy A15) ────────────────────────────────────────────
    // Montar el menú bajo la intro congelaba la app ~1,2 s (debug) y, hecho durante la carga, se veía
    // la barra parada. Aquí la pantalla está en negro total y no se mueve nada: el tirón no se ve. Se
    // monta, se deja pasar el fotograma pesado, y la carga aparece desde el negro.
    LaunchedEffect(enNegro) {
        if (!enNegro) return@LaunchedEffect
        introLog("negro: se monta el menú")
        onStudioFinished()
        iniciarPrecarga = true
        repeat(3) { withFrameNanos { } }
        esperaAnimada(NEGRO_MS)
        studioMounted = false
        phaseName = IntroPhase.LOADING.name
    }

    LaunchedEffect(phase) {
        introLog("fase $phase")
        when (phase) {
            IntroPhase.STUDIO -> Unit   // la conduce [CukitiLogoReveal]
            IntroPhase.LOADING -> {
                // Esperar a que el chequeo/sincronización inicial de la cuenta termine ANTES de pasar
                // a HOME: la intro es la ÚNICA pantalla de carga, sin un segundo «Cargando tu cuenta»
                // encima ni un corte a HOME a medias.
                //
                // ⏱ **PERO CON TOPE** (2026-09-07). Con una conexión MALA —que el sistema da por
                // válida— la sincronización podía tardar más de un minuto sin error ni salida. El
                // ViewModel ya abre la puerta él solo (`STARTUP_SYNC_GRACE_MS`); este tope es por lo
                // que NO es la cuenta, y va en tiempo REAL. **Entrar tarde con datos locales siempre es
                // mejor que no entrar.**
                val desdeReal = System.currentTimeMillis()
                // El mínimo va en tiempo de ANIMACIÓN: la barra se ve entera, peldaño a peldaño
                // (T1 → MEGA), aunque la carga real acabe antes (pedido del usuario: «~2 s»).
                val minimo = if (reducedFx) LOADING_MIN_MS / 2f else LOADING_MIN_MS
                // Un solo bucle de fotogramas (con el mismo tope por paso que [avanzarReloj]): trocearlo
                // en llamadas cortas perdía un fotograma de cada cinco y la carga duraba el doble.
                var t = 0f
                var antes = withFrameNanos { it }
                while (true) {
                    val ahora = withFrameNanos { it }
                    t += ((ahora - antes) / 1_000_000f).coerceIn(0f, MAX_PASO_MS)
                    antes = ahora
                    val objetivo = 0.9f * minOf(precarga.floatValue, t / minimo)
                    if (objetivo > barra.floatValue) barra.floatValue = objetivo
                    // P-218 · el tramo que de verdad tarda —resolver la cuenta— tiene voz propia.
                    esperandoCuenta = precargaHecha && !accountReadyState.value && t >= minimo
                    val cuentaResuelta = accountReadyState.value ||
                        System.currentTimeMillis() - desdeReal >= ACCOUNT_WAIT_MAX_MS
                    if (precargaHecha && t >= minimo && cuentaResuelta) break
                }
                esperandoCuenta = false
                // Último tramo 90 → 100 %, también con el reloj de la intro.
                val desde = barra.floatValue
                avanzarReloj(mutableFloatStateOf(0f), 250f) { t ->
                    barra.floatValue = desde + (1f - desde) * (t / 250f)
                }
                esperaAnimada(150f)
                phaseName = IntroPhase.TITLE.name
            }
            IntroPhase.TITLE -> {
                onTitleShown()
                esperaAnimada(if (reducedFx) 250f else TITLE_PROMPT_DELAY_MS)
                puedeEmpezar = true
            }
            // Sin esperar: el fundido lo hace la capa de `AppNavigation` sobre el destino ya montado.
            IntroPhase.LEAVING -> onComplete()
        }
    }

    val onTap by rememberUpdatedState {
        when (IntroPhase.valueOf(phaseName)) {
            // Saltar la firma = ir directo al negro (la raíz ya es negra): el menú se monta ahí, no
            // encima de la carga.
            IntroPhase.STUDIO -> if (studioMounted) {
                studioMounted = false
                enNegro = true
            }
            IntroPhase.TITLE -> if (puedeEmpezar) {
                onStart()
                phaseName = IntroPhase.LEAVING.name
            }
            else -> Unit
        }
    }

    // Fundidos de la escena. Se leen en `graphicsLayer` (fase de dibujo): cero recomposición.
    val escena = animateFloatAsState(
        targetValue = if (phase == IntroPhase.STUDIO) 0f else 1f,
        animationSpec = tween(500), label = "introEscena"
    )
    val cargaUi = animateFloatAsState(
        targetValue = if (phase == IntroPhase.LOADING) 1f else 0f,
        animationSpec = tween(300), label = "introCargaUi"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Negro, como el final de la firma del estudio.
            .background(Color.Black)
            // Traga TODOS los toques: la intro cubre el Home (que sigue compuesto debajo), así que sin
            // esto las pulsaciones la atravesaban y disparaban Jugar/Continuar. El toque solo sirve
            // para saltar la firma del estudio o para empezar desde el título.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.type == PointerEventType.Release) onTap()
                    }
                }
            }
            // TalkBack: el detector de arriba no tiene semántica, así que con lector de pantalla no se
            // podía empezar. En el título, la pantalla entera es el botón «Jugar» (auditoría 2026-10-01).
            .semantics {
                if (phase == IntroPhase.TITLE && puedeEmpezar) onClick(label = "Jugar") { onTap(); true }
            },
        contentAlignment = Alignment.Center
    ) {
        // ── Escena: la ilustración de la intro, que TRAE el título «TEIKA FUSIÓN» dibujado ────────
        // Antes se le ponía encima el logo animado y se veían dos títulos (decisión del usuario: el
        // título de la ilustración ES el título). Se compone desde el primer frame aunque no se vea:
        // se decodifica fuera del hilo principal y se sube a la GPU (`prepareToDraw`) durante la
        // firma, así que no hay tirón al aparecer. El zoom lento pivota en el título para no sacarlo
        // de pantalla.
        val kenBurns = if (reducedFx || phase == IntroPhase.STUDIO) null else
            rememberInfiniteTransition(label = "introKenBurns").animateFloat(
                initialValue = 1f, targetValue = 1.03f,
                animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Reverse),
                label = "introKenBurnsScale"
            )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = escena.value }
                // Las franjas que deja el encaje en móviles alargados: el cielo de la propia
                // ilustración, arriba su azul del borde superior y abajo el del inferior.
                .background(
                    Brush.verticalGradient(
                        0f to IntroFraming.SKY_TOP, 0.5f to IntroFraming.SKY_TOP,
                        0.5f to IntroFraming.SKY_BOTTOM, 1f to IntroFraming.SKY_BOTTOM
                    )
                )
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = kenBurns?.value ?: 1f
                        scaleX = s; scaleY = s
                        transformOrigin = TransformOrigin(0.5f, 0.2f)
                    }
                    // Funde los bordes de la imagen con las franjas: sin costura visible.
                    .drawWithContent {
                        drawContent()
                        val r = IntroFraming.imageRect(size.width, size.height)
                        val fade = r.height * IntroFraming.EDGE_FADE
                        if (r.top > 0.5f) drawRect(
                            Brush.verticalGradient(
                                listOf(IntroFraming.SKY_TOP, Color.Transparent),
                                startY = r.top, endY = r.top + fade
                            ),
                            topLeft = Offset(0f, r.top), size = Size(size.width, fade)
                        )
                        if (r.bottom < size.height - 0.5f) drawRect(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, IntroFraming.SKY_BOTTOM),
                                startY = r.bottom - fade, endY = r.bottom
                            ),
                            topLeft = Offset(0f, r.bottom - fade), size = Size(size.width, fade)
                        )
                    }
            ) {
                RawImage(
                    resId = R.drawable.introteika,
                    contentDescription = "Teika Fusión",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = IntroFraming.Scale,
                    alignment = IntroFraming.Align,
                    async = true
                )
            }
            // Velo SOLO abajo: el título de arriba queda limpio y el texto de abajo tiene contraste.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.6f)
                        )
                    )
            )
        }

        // ── 3 · «Toca para jugar» ───────────────────────────────────────────────────────────────
        if (phase == IntroPhase.TITLE || phase == IntroPhase.LEAVING) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 88.dp),
                contentAlignment = Alignment.Center
            ) {
                TapPrompt(visible = puedeEmpezar && phase == IntroPhase.TITLE, reducedFx = reducedFx)
            }
        }

        // ── 2 · Barra de carga + consejos ───────────────────────────────────────────────────────
        if (phase == IntroPhase.LOADING || phase == IntroPhase.TITLE) {
            LoadingPanel(
                barra = barra,
                playing = phase == IntroPhase.LOADING,
                esperandoCuenta = esperandoCuenta,
                tips = tips,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .fillMaxWidth(0.8f)
                    .graphicsLayer { alpha = cargaUi.value }
            )
        }

        // ── 1 · Firma del estudio ───────────────────────────────────────────────────────────────
        if (studioMounted) {
            CukitiLogoReveal(
                reducedFx = reducedFx,
                onSting = onStudioSting,
                // Termina en negro total; la retira el efecto «EL NEGRO» cuando ya no hace falta.
                onDone = { enNegro = true }
            )
        }
    }
}

/**
 * P-373 · la barra de carga con alma: el Teika que la recorre CRECE al avanzar (T1 → MEGA, con un
 * pequeño «pop» en cada peldaño) y debajo rotan consejos. Sin porcentaje.
 */
/**
 * **ENCAJE DE LA ILUSTRACIÓN DE LA INTRO** (2026-09-30, pedido del usuario: «mejora la escala»).
 *
 * `introteika` es 9:16 (941×1672) y los móviles de hoy son 9:19,5–9:20. Con `ContentScale.Crop`
 * se ampliaba ×1,4 (×1,9 en QHD) para llenar el alto: recortaba los lados y se comía la «N» de
 * FUSIÓN (el título ocupa x 120…861 y en un A15 solo se veía 96…845), además de verse borrosa.
 *
 * Ahora la escala es la de Crop **con un techo**: nunca tanta que el título —con margen y contando
 * el zoom lento de la escena— se salga por los lados; y la imagen se desplaza en horizontal para
 * que el título quede centrado (no lo está en el dibujo). Si con ese techo no llega al alto, las
 * franjas de arriba y abajo se rellenan con el cielo de la propia imagen y sus bordes se funden.
 * En pantallas 9:16 o más anchas no cambia nada: ahí Crop ya enseña el título entero.
 */
internal object IntroFraming {
    private const val IMG_W = 941f
    private const val IMG_H = 1672f
    /** El título dibujado, contorno rosa incluido, en px de la imagen (medido en el original). */
    private const val TITLE_LEFT = 120f
    private const val TITLE_RIGHT = 861f
    private const val TITLE_MARGIN = 20f
    /** El zoom máximo de la escena (Ken Burns): también tiene que caber. */
    private const val KEN_BURNS_MAX = 1.03f
    private const val TITLE_CENTER = (TITLE_LEFT + TITLE_RIGHT) / 2f

    /** Colores medios de los bordes superior e inferior de la ilustración. */
    val SKY_TOP = Color(0xFF010337)
    val SKY_BOTTOM = Color(0xFF091362)
    /** Alto del fundido en cada borde, en fracción del alto de la imagen escalada. */
    const val EDGE_FADE = 0.04f

    /** Escala imagen→pantalla. Pura, para poder testearla. */
    fun scale(srcW: Float, srcH: Float, dstW: Float, dstH: Float): Float {
        if (srcW <= 0f || srcH <= 0f) return 1f
        val crop = maxOf(dstW / srcW, dstH / srcH)
        val fitWidth = dstW / srcW
        // Ventana visible mínima (px de imagen): el título con margen, ampliada por el zoom lento.
        val needed = (TITLE_RIGHT - TITLE_LEFT + 2 * TITLE_MARGIN) * KEN_BURNS_MAX * (srcW / IMG_W)
        val cap = dstW / needed
        return minOf(crop, maxOf(cap, fitWidth))
    }

    /** Posición x de la imagen escalada (≤ 0): centra el título sin enseñar nada fuera de la imagen. */
    fun offsetX(scaledW: Float, dstW: Float): Float {
        val overflow = scaledW - dstW
        if (overflow <= 0f) return -overflow / 2f
        val titleCenter = TITLE_CENTER / IMG_W * scaledW
        return (dstW / 2f - titleCenter).coerceIn(-overflow, 0f)
    }

    /** Dónde queda la imagen dentro de un hueco de [dstW]×[dstH] (lo usa el fundido de los bordes). */
    fun imageRect(dstW: Float, dstH: Float): androidx.compose.ui.geometry.Rect {
        val s = scale(IMG_W, IMG_H, dstW, dstH)
        val w = IMG_W * s
        val h = IMG_H * s
        val left = offsetX(w, dstW)
        val top = (dstH - h) / 2f
        return androidx.compose.ui.geometry.Rect(left, top, left + w, top + h)
    }

    val Scale = object : ContentScale {
        override fun computeScaleFactor(srcSize: Size, dstSize: Size): ScaleFactor {
            val s = scale(srcSize.width, srcSize.height, dstSize.width, dstSize.height)
            return ScaleFactor(s, s)
        }
    }

    val Align = object : Alignment {
        override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset =
            IntOffset(
                offsetX(size.width.toFloat(), space.width.toFloat()).roundToInt(),
                ((space.height - size.height) / 2f).roundToInt()
            )
    }
}

@Composable
private fun LoadingPanel(
    barra: FloatState,
    playing: Boolean,
    esperandoCuenta: Boolean,
    tips: List<String>,
    modifier: Modifier = Modifier
) {
    val peldano by remember {
        derivedStateOf { (barra.floatValue * LoadingLadder.size).toInt().coerceIn(0, LoadingLadder.lastIndex) }
    }
    val pop = remember { Animatable(1f) }
    LaunchedEffect(peldano) {
        if (peldano > 0) {
            pop.snapTo(1.3f)
            pop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        }
    }
    var tipIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(playing) {
        while (playing && tips.size > 1) {
            delay(TIP_MS)
            tipIndex = (tipIndex + 1) % tips.size
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        BoxWithConstraints(Modifier.fillMaxWidth().height(60.dp)) {
            val density = LocalDensity.current
            val maxWidthPx = with(density) { maxWidth.toPx() }
            val teikaSizePx = with(density) { 60.dp.toPx() }
            val composition by rememberLottieComposition(
                LottieCompositionSpec.RawRes(LoadingLadder[peldano].lottieResId)
            )
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                isPlaying = playing,
                modifier = Modifier
                    .size(60.dp)
                    // `offset { }` (lambda): el progreso se lee en LAYOUT, cero recomposición.
                    .offset {
                        IntOffset(x = ((maxWidthPx - teikaSizePx) * barra.floatValue).toInt(), y = 0)
                    }
                    .graphicsLayer {
                        scaleX = pop.value; scaleY = pop.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
            )
        }

        Spacer(Modifier.height(8.dp))

        // El relleno se DIBUJA, no se mide: el progreso se lee en la fase de dibujo.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.55f))
                .drawBehind {
                    val ancho = size.width * barra.floatValue.coerceIn(0f, 1f)
                    if (ancho > 0f) {
                        drawRoundRect(
                            color = LoadingLadder[peldano].barColor,
                            size = Size(ancho, size.height),
                            cornerRadius = CornerRadius(size.height / 2f)
                        )
                    }
                }
        )

        Spacer(Modifier.height(14.dp))

        val texto = if (esperandoCuenta) "Preparando tu cuenta…" else tips.getOrElse(tipIndex) { "" }
        AnimatedContent(
            targetState = texto,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "introConsejo"
        ) { t ->
            Text(
                text = t,
                fontSize = 14.sp,
                lineHeight = 19.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                style = IntroTextShadow,
                modifier = Modifier.fillMaxWidth().heightIn(min = 40.dp)
            )
        }
    }
}

@Composable
private fun TapPrompt(visible: Boolean, reducedFx: Boolean) {
    val late = if (reducedFx || !visible) null else rememberInfiniteTransition(label = "tocaLate")
        .animateFloat(
            initialValue = 1f, targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "tocaLateAlpha"
        )
    Box(Modifier.height(32.dp), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = visible, enter = fadeIn(tween(300)), exit = fadeOut(tween(150))) {
            Text(
                text = "TOCA PARA JUGAR",
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 3.sp,
                color = Color.White,
                style = IntroTextShadow,
                modifier = Modifier.graphicsLayer { alpha = late?.value ?: 1f }
            )
        }
    }
}

/**
 * Marcas del arranque para medir con `adb logcat -s Intro`: cada fase y cada tirón (>30 ms) con los ms
 * desde que arrancó el proceso. Siempre en debug; en release SOLO si se activa a mano en el aparato
 * con `adb shell setprop log.tag.Intro DEBUG` (así se midió la intro en un Galaxy A15, 2026-09-30).
 * Para un jugador normal está apagado y no cuesta nada.
 */
internal val introLogOn: Boolean by lazy {
    com.cukiti.teikafusion.BuildConfig.DEBUG || android.util.Log.isLoggable("Intro", android.util.Log.DEBUG)
}

internal fun introLog(que: String) {
    if (!introLogOn) return
    val t0 = com.cukiti.teikafusion.TeikaFusionApp.processStartMs
    val ms = if (t0 > 0L) android.os.SystemClock.elapsedRealtime() - t0 else -1L
    android.util.Log.i("Intro", "$que · +$ms ms")
}
