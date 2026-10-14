package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.cukiti.teikafusion.model.BiomeType
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/** π como Float — el peso de la marea del morph es sin(π·f). */
private const val PI_F = 3.14159265f

/**
 * Fondo temático PREMIUM del nivel. Renderiza una ESCENA por bioma (no solo un velo): la
 * Caverna de Cristal dibuja un macizo de cristal facetado que se va AGRIETANDO conforme avanza
 * la ventana (transition 0→1, nv5→nv9), y el Bosque del Sueño (bioma siguiente) va INVADIENDO
 * desde abajo como una marea — "transformándose poco a poco" tal como pidió el diseño.
 *
 * Todo se dibuja translúcido sobre [AppBackground], de modo que la UI de juego sigue legible.
 *
 * El morph es **continuo por construcción**: en `transition = 1f` (último nivel de la ventana) el
 * bioma saliente está a 0 y el entrante entero, que es exactamente lo que se dibuja en el primer
 * nivel de la ventana siguiente con `transition = 0f`. Sin eso, el bioma viejo se esfumaba de golpe
 * al pasar de nivel. Ver [com.cukiti.teikafusion.model.BiomeRegistry.transitionProgress].
 *
 * @param primary  bioma actual de la ventana.
 * @param next     bioma de la ventana siguiente (destino del morph / invasor).
 * @param transition 0f en el primer nivel de la ventana → 1f exacto en el último.
 */
@Composable
fun BiomeBackdrop(
    primary: BiomeType,
    next: BiomeType,
    transition: Float,
    modifier: Modifier = Modifier,
    crackController: CrackController? = null
) {
    if (primary == BiomeType.NONE && next == BiomeType.NONE) return

    val f = transition.coerceIn(0f, 1f)

    // PAUSA: el fondo del bioma se congela en su fotograma (bloque 20, 2026-07-30). Es la animación
    // ambiente más constante del juego —corre en TODOS los niveles, siempre— y detrás del overlay
    // de pausa no la ve nadie. El fondo sigue dibujándose: solo se para el reloj.
    // Se congela en pausa Y con «reducir efectos» (P-12): es la animación ambiente más constante
    // del juego —corre en todos los niveles, siempre— y es puro decorado.
    val animationsOn = LocalAnimationsActive.current && !LocalReducedFx.current
    // ── LOS RELOJES SE LEEN AL DIBUJAR (perf 2026-09-26) ─────────────────────────────────────────
    // Se guardan los `State`, no sus valores. Antes esto eran dos `Float` sacados con `by` en el
    // CUERPO, y eso tenía dos costes a 60 fps en TODOS los niveles, siempre: este composable se
    // recomponía en cada fotograma, y como la lambda de `drawWithCache` capturaba los dos valores,
    // era una lambda nueva cada vez → la caché se invalidaba y se reconstruían todos los degradados
    // del fondo (velos y escena) en cada fotograma — justo lo que el comentario de PERF de abajo dice
    // que no pasa. Ahora la composición no se toca y la caché solo se rehace al cambiar de nivel.
    val phaseState: androidx.compose.runtime.FloatState
    val shimmerState: androidx.compose.runtime.FloatState
    if (animationsOn) {
        // P-368 (2026-09-30): un reloj propio en vez de `rememberInfiniteTransition`, que avanza —y hace
        // redibujar el fondo a pantalla completa— en CADA fotograma. Mismas ondas: `phase` 0→1 lineal en
        // 9 s y vuelta a empezar (deriva lenta de partículas); `shimmer` 0→1→0 lineal, 2,6 s cada sentido
        // (destellos y estrellas). En pantallas de 90/120 Hz avanzan a pasos de 1/30 s (ver
        // [rememberAmbientStepNs]); a 60 Hz, cada fotograma como antes.
        val phase = remember { mutableFloatStateOf(0f) }
        val shimmer = remember { mutableFloatStateOf(0f) }
        val stepNs = rememberAmbientStepNs()
        LaunchedEffect(stepNs) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                val ms = ambientNs(now - start, stepNs) / 1_000_000L
                val p = (ms % BACKDROP_PHASE_MS).toFloat() / BACKDROP_PHASE_MS
                val s = (ms % (BACKDROP_SHIMMER_MS * 2)).toFloat() / BACKDROP_SHIMMER_MS
                if (p != phase.floatValue) phase.floatValue = p
                val sv = if (s <= 1f) s else 2f - s
                if (sv != shimmer.floatValue) shimmer.floatValue = sv
            }
        }
        phaseState = phase
        shimmerState = shimmer
    } else {
        phaseState = remember { mutableFloatStateOf(0f) }
        // Valor de reposo: ni el destello máximo ni apagado del todo.
        shimmerState = remember { mutableFloatStateOf(0.5f) }
    }

    // Geometría estable (no se recalcula por frame).
    val crystal = remember { buildCrystalGeom() }
    val sleepGeom = remember { buildSleepGeom() }

    // Grietas interactivas: el cristal sólo reacciona al toque cuando la lámina de cristal es el bioma
    // ACTUAL (niveles 5-9). OJO: cristal es el PRIMER bioma, así que `next == CRYSTAL` solo es cierto en
    // los niveles 1-4 e infinito (donde aún NO hay cristal) → NO debe incluirse o saldrían grietas falsas.
    val crystalTouchable = primary == BiomeType.CRYSTAL
    // Si nos pasan un controlador externo (GameScreen) lo usamos para que TODA interacción (botones,
    // bandeja, fondo) pueda generar grietas; si no, uno interno con tap directo sobre el fondo.
    val controller = crackController ?: remember { CrackController() }
    val bursts = controller.bursts
    // Ciclo de vida de cada estallido: aparece → se mantiene unos segundos → se regenera (fade).
    bursts.forEach { b ->
        key(b.id) {
            LaunchedEffect(b.id) {
                b.appear.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
                delay(2600)
                b.alpha.animateTo(0f, tween(800))
                bursts.remove(b)
            }
        }
    }

    // Sólo cuando no hay controlador externo: tap directo sobre el fondo (modo standalone).
    val touchModifier = if (crackController == null && crystalTouchable) {
        Modifier.pointerInput(Unit) { detectTapGestures { pos -> controller.crack(pos) } }
    } else Modifier

    // PERF: todo lo dependiente del TAMAÑO + bioma + transición (f) se construye en el bloque de
    // CACHÉ. Como `f` es función pura del nivel (BiomeRegistry.transitionProgress) NO cambia por frame
    // → el caché corre solo al cambiar de nivel/tamaño, no a 60fps. Los valores ANIMADOS
    // (phase/shimmer/bursts) se leen en onDrawBehind (fase de DIBUJO) → 0 asignaciones de Brush/frame.
    // ── Pesos del morph. La CONTINUIDAD es lo que manda aquí: en f = 1 (último nivel de la ventana)
    //    este fotograma tiene que ser IDÉNTICO al que dibuja el primer nivel de la ventana siguiente
    //    con f = 0 → bioma saliente a 0, bioma entrante entero. Si no, al pasar de nivel el bioma
    //    viejo se esfuma de golpe y el velo del nuevo aparece de golpe: el corte duro que la
    //    transición existe para evitar.
    //
    //    `outWeight` cae despacio al principio y se desploma al final (f²): así el bioma en el que
    //    ESTÁS domina la mayor parte de su capítulo (a mitad sigue al 75 %) y solo cede el terreno
    //    en el tramo final. `f` a secas (lineal) es el peso del invasor, igual que antes.
    val outWeight = 1f - f * f
    val inWeight = f

    // Capa propia: el fondo se redibuja cada fotograma, y sin capa ese redibujo regrababa también a
    // sus hermanos sin capa (ver el comentario gemelo en la columna de GameScreen).
    Spacer(modifier.then(touchModifier).graphicsLayer().drawWithCache {
        val W = size.width; val H = size.height
        val veilCur = veilBrush(primary, outWeight)
        val sceneCur = buildSceneBrushes(primary, W, H, alpha = outWeight, t = f, rising = false, sleepGeom = sleepGeom)
        val invading = next != primary && f > 0.01f
        // Velo COMPLETO del invasor (arriba incluido). Antes solo había la marea de abajo, así que la
        // mitad alta de la pantalla no recibía NADA del bioma entrante y su color aparecía de golpe
        // al cambiar de nivel.
        val veilNext = if (invading) veilBrush(next, inWeight) else null
        // La MAREA se conserva —es la identidad del morph, el bioma nuevo subiendo desde abajo— pero
        // ahora es un refuerzo TRANSITORIO: nace y muere dentro de la transición (sin(πf) vale 0 en
        // los dos extremos), así no puede dejar un salto al cerrarse la ventana. El color de fondo
        // del invasor ya lo pone `veilNext`.
        val invaderVeil = if (invading) {
            val pb = biomePalette(next)
            val surge = sin(f * PI_F)
            val floodStart = (1f - f).coerceIn(0f, 1f) * 0.85f
            Brush.verticalGradient(
                0f to Color.Transparent,
                floodStart to Color.Transparent,
                1f to pb.bottom.copy(alpha = pb.bottom.alpha * surge)
            )
        } else null
        val sceneNext = if (invading) buildSceneBrushes(next, W, H, alpha = inWeight, t = 0f, rising = true, sleepGeom = sleepGeom) else null
        // Auditoría de fluidez (2026-09-29): el macizo del CRISTAL no usa los relojes (solo depende del
        // nivel), pero leerlos invalidaba el dibujo y el fondo entero, a pantalla completa, se repintaba
        // a 90/120 Hz sin cambiar un píxel en todos los niveles del Cristal. Solo se leen si alguna de
        // las escenas visibles anima; si no, el fondo se pinta una vez y queda quieto (las grietas por
        // toque siguen: leen sus propios Animatable).
        val usesClock = sceneAnimates(primary) || (invading && sceneAnimates(next))
        onDrawBehind {
            val phase = if (usesClock) phaseState.floatValue else 0f
            val shimmer = if (usesClock) shimmerState.floatValue else 0.5f
            // --- Capa del bioma ACTUAL: se desvanece HASTA CERO al cerrarse la ventana. ---
            drawRect(brush = veilCur)
            drawSceneAnim(primary, sceneCur, t = f, phase = phase, shimmer = shimmer,
                alpha = outWeight, crystal = crystal, sleepGeom = sleepGeom, rising = false)
            // --- Capa INVASORA del bioma siguiente, subiendo desde abajo como una marea. ---
            if (invading) {
                drawRect(brush = veilNext!!)
                drawRect(brush = invaderVeil!!)
                drawSceneAnim(next, sceneNext!!, t = 0f, phase = phase, shimmer = shimmer,
                    alpha = inWeight, crystal = crystal, sleepGeom = sleepGeom, rising = true)
            }
            // --- Grietas grandes por toque del jugador (encima de todo el cristal). ---
            bursts.forEach { b -> drawCrackBurst(b.center, b.appear.value, b.alpha.value, b.seed) }
        }
    })
}

/** Vuelta completa de la deriva lenta del fondo (`phase`). */
private const val BACKDROP_PHASE_MS = 9000L

/** Medio ciclo del brillo del fondo (`shimmer`, ida y vuelta). */
private const val BACKDROP_SHIMMER_MS = 2600L

/**
 * Controlador compartido de grietas. GameScreen crea uno y lo pasa a [BiomeBackdrop]; cualquier
 * interacción (botones de arriba, panel de fichas, fondo) puede llamar [crack] con la posición
 * (en coords del Box que contiene el backdrop) para abrir una grieta grande que luego se regenera.
 */
class CrackController {
    internal val bursts = mutableStateListOf<CrackBurst>()
    private var nextId = 0L
    /** Abre una grieta en [local] (coordenadas locales al backdrop). */
    fun crack(local: Offset) {
        val id = nextId++
        bursts.add(CrackBurst(id, local, (id * 2654435761L).toInt()))
    }
}

/** Estallido de grietas grande generado al interactuar con el cristal del fondo. */
internal class CrackBurst(val id: Long, val center: Offset, val seed: Int) {
    val appear = Animatable(0f)  // 0→1: las grietas brotan desde el punto de impacto.
    val alpha = Animatable(1f)   // 1→0: el cristal se regenera y la grieta se desvanece.
}

private fun DrawScope.drawCrackBurst(center: Offset, appear: Float, alpha: Float, seed: Int) {
    if (alpha <= 0.01f) return
    val rng = kotlin.random.Random(seed)
    val n = 8
    val maxLen = size.minDimension * 0.34f      // grande
    val w = size.minDimension * 0.007f

    // Destello de impacto (más fuerte al inicio, se apaga al crecer).
    drawCircle(Color.White.copyA(alpha * 0.30f * (1f - appear)), maxLen * 0.18f * appear + 6f, center)

    for (i in 0 until n) {
        val ang = (i / n.toFloat()) * 6.28318f + (rng.nextFloat() - 0.5f) * 0.5f
        val len = maxLen * (0.55f + rng.nextFloat() * 0.45f) * appear
        val midLen = len * (0.45f + rng.nextFloat() * 0.2f)
        val midAng = ang + (rng.nextFloat() - 0.5f) * 0.5f
        val mid = center + Offset(cos(midAng) * midLen, sin(midAng) * midLen)
        val end = mid + Offset(cos(ang) * (len - midLen), sin(ang) * (len - midLen))
        drawLine(Color(0xFF0A1830).copyA(alpha * 0.9f), center, mid, w)
        drawLine(Color(0xFF0A1830).copyA(alpha * 0.9f), mid, end, w * 0.65f)
        drawLine(Color.White.copyA(alpha * 0.35f), center, mid, w * 0.4f)
    }
}

// ----------------------------------------------------------------------------------------------
// Escenas por bioma
// ----------------------------------------------------------------------------------------------

/**
 * Brushes de una escena de bioma, CONSTRUIDOS UNA VEZ por tamaño/transición (bloque de caché de
 * [drawWithCache]) en vez de por frame. Slots nulos = no aplican a ese bioma.
 */
private class SceneBrushes(
    val pane: Brush? = null,        // cristal: lámina
    val hi1: Brush? = null,         // cristal: brillo superior
    val hi2: Brush? = null,         // cristal: brillo inferior
    val fog: List<Brush>? = null,   // sueño: montículos de niebla
    val nebula: Brush? = null,      // estrella/galáctico: nebulosa de fondo (radio fijo)
    val core: Brush? = null,        // galáctico: núcleo de la galaxia
    val glow: Brush? = null,        // genérico: resplandor de acento (radio fijo)
    val vignette: Brush? = null,    // fear: oscuridad que se cierra desde los bordes
    val mist: Brush? = null,        // fear: niebla fría baja
)

/** Brush del velo de gradiente vertical del bioma (alpha ya aplicado). Construido en caché. */
private fun veilBrush(biome: BiomeType, alpha: Float): Brush {
    val p = biomePalette(biome)
    return Brush.verticalGradient(
        listOf(p.top.copy(alpha = p.top.alpha * alpha), p.bottom.copy(alpha = p.bottom.alpha * alpha))
    )
}

/**
 * Construye los brushes de la escena de [biome]. Los efectos que ANTES respiraban variando el RADIO
 * del gradiente por frame (glow/nebulosa) ahora usan radio FIJO aquí y respiran por alpha en el
 * dibujo → el brush es invariante por frame y se cachea. La niebla del sueño pierde su micro-bob
 * vertical (imperceptible) para que su centro sea fijo y cacheable.
 */
private fun buildSceneBrushes(
    biome: BiomeType, W: Float, H: Float, alpha: Float, t: Float, rising: Boolean, sleepGeom: SleepGeom
): SceneBrushes {
    if (alpha <= 0.01f) return SceneBrushes()
    return when (biome) {
        BiomeType.CRYSTAL -> {
            val paneA = alpha * (1f - 0.55f * t)
            if (paneA <= 0.02f) SceneBrushes()
            else SceneBrushes(
                pane = Brush.verticalGradient(listOf(
                    Color(0x66BFEFFF).copyA(paneA), Color(0x33A0E4FF).copyA(paneA), Color(0x66BFEFFF).copyA(paneA))),
                hi1 = Brush.radialGradient(
                    listOf(Color(0x33FFFFFF).copyA(paneA), Color.Transparent),
                    center = Offset(W * 0.42f, H * 0.30f), radius = W * 0.7f),
                hi2 = Brush.radialGradient(
                    listOf(Color(0x2E80E0FF).copyA(paneA), Color.Transparent),
                    center = Offset(W * 0.55f, H * 0.80f), radius = W * 0.7f)
            )
        }
        BiomeType.SLEEP -> {
            val cy = H * (if (rising) 0.78f else 0.72f)
            SceneBrushes(fog = sleepGeom.fog.map { fog ->
                Brush.radialGradient(
                    listOf(Color(0x66C3B6FF).copyA(alpha * 0.5f), Color.Transparent),
                    center = Offset(fog.x * W, cy), radius = fog.r * W)
            })
        }
        BiomeType.STAR -> SceneBrushes(
            nebula = Brush.radialGradient(
                listOf(Color(0xFF3A4FA0).copyA(alpha * 0.20f), Color(0xFF6A4FB0).copyA(alpha * 0.10f), Color.Transparent),
                center = Offset(W * 0.42f, H * 0.38f), radius = W * 0.6f)
        )
        BiomeType.GALACTIC -> {
            val gc = Offset(W * 0.72f, H * 0.28f)
            SceneBrushes(
                nebula = Brush.radialGradient(
                    listOf(Color(0xFF5A2E9E).copyA(alpha * 0.22f), Color(0xFF241055).copyA(alpha * 0.14f), Color.Transparent),
                    center = Offset(W * 0.5f, H * 0.5f), radius = W * 0.85f),
                core = Brush.radialGradient(
                    listOf(Color(0xFFFFF0FF).copyA(alpha * 0.6f), Color(0xFFB58CFF).copyA(alpha * 0.25f), Color.Transparent),
                    center = gc, radius = W * 0.06f)
            )
        }
        BiomeType.FEAR -> SceneBrushes(
            // Oscuridad que se cierra desde los bordes (centro despejado para no oscurecer el tablero).
            vignette = Brush.radialGradient(
                listOf(Color.Transparent, Color.Transparent, Color(0xFF05070E).copyA(alpha * 0.80f)),
                center = Offset(W * 0.5f, H * 0.52f), radius = kotlin.math.max(W, H) * 0.72f),
            // Niebla fría baja (banda inferior).
            mist = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0x2E9FB0D0).copyA(alpha)),
                startY = H * 0.62f, endY = H)
        )
        BiomeType.GARDEN -> SceneBrushes(
            // Luz de dosel filtrándose desde arriba (radio fijo; respira por alpha en el dibujo).
            nebula = Brush.radialGradient(
                listOf(Color(0xFFBFFFA0).copyA(alpha * 0.16f), Color(0xFF7FD08A).copyA(alpha * 0.07f), Color.Transparent),
                center = Offset(W * 0.5f, -H * 0.04f), radius = W * 0.72f),
            // Maleza inferior (banda de gradiente).
            mist = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0x55102A18).copyA(alpha)),
                startY = H * 0.70f, endY = H)
        )
        BiomeType.STORM -> SceneBrushes(
            // Banco de nubes de tormenta en la parte superior (cielo oscuro que se aclara hacia abajo).
            nebula = Brush.verticalGradient(
                listOf(Color(0xFF0E1424).copyA(alpha * 0.88f), Color(0xFF161E30).copyA(alpha * 0.5f), Color.Transparent),
                startY = 0f, endY = H * 0.42f),
            // Resplandor del relámpago tras las nubes (radio fijo; alpha = intensidad del fogonazo).
            glow = Brush.radialGradient(
                listOf(Color(0xFFDCE8FF).copyA(alpha), Color(0xFF9FB4E0).copyA(alpha * 0.4f), Color.Transparent),
                center = Offset(W * 0.5f, H * 0.16f), radius = W * 0.5f),
            // Bruma baja (banda inferior).
            mist = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0x33202A40).copyA(alpha)),
                startY = H * 0.70f, endY = H)
        )
        BiomeType.VOLCANO -> SceneBrushes(
            // Cielo de ceniza con rescoldo en la parte alta.
            nebula = Brush.verticalGradient(
                listOf(Color(0xFF1A0A06).copyA(alpha * 0.75f), Color(0xFF3A130A).copyA(alpha * 0.45f), Color.Transparent),
                startY = 0f, endY = H * 0.55f),
            // Brillo de lava subiendo desde abajo (banda inferior; respira por alpha con el pulso).
            glow = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFFFF6A1E).copyA(alpha * 0.30f), Color(0xFFFFB040).copyA(alpha * 0.52f)),
                startY = H * 0.60f, endY = H)
        )
        BiomeType.GLACIER -> SceneBrushes(
            // Cielo helado nocturno (azul profundo arriba que se aclara hacia el centro).
            nebula = Brush.verticalGradient(
                listOf(Color(0xFF0C1A33).copyA(alpha * 0.72f), Color(0xFF13294D).copyA(alpha * 0.40f), Color.Transparent),
                startY = 0f, endY = H * 0.60f),
            // Suelo helado / neblina baja (banda inferior azul pálida).
            mist = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0x3380D0FF).copyA(alpha)),
                startY = H * 0.72f, endY = H)
        )
        BiomeType.NINJA -> SceneBrushes(
            // Cielo nocturno del dojo (negro azulado arriba).
            nebula = Brush.verticalGradient(
                listOf(Color(0xFF0A0E14).copyA(alpha * 0.82f), Color(0xFF12161E).copyA(alpha * 0.45f), Color.Transparent),
                startY = 0f, endY = H * 0.60f),
            // Halo de la luna llena (radio fijo; respira por alpha en el dibujo).
            glow = Brush.radialGradient(
                listOf(Color(0xFFEDE6D8).copyA(alpha * 0.5f), Color(0xFFB8C0C8).copyA(alpha * 0.16f), Color.Transparent),
                center = Offset(W * 0.74f, H * 0.16f), radius = W * 0.32f),
            // Bruma baja del suelo del dojo.
            mist = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0x33141A20).copyA(alpha)),
                startY = H * 0.70f, endY = H)
        )
        BiomeType.GHOST -> SceneBrushes(
            // Cielo espectral (morado profundo arriba).
            nebula = Brush.verticalGradient(
                listOf(Color(0xFF14091F).copyA(alpha * 0.80f), Color(0xFF1E1030).copyA(alpha * 0.45f), Color.Transparent),
                startY = 0f, endY = H * 0.60f),
            // Halo de la luna enfermiza (radio fijo; respira por alpha en el dibujo).
            glow = Brush.radialGradient(
                listOf(Color(0xFFD8CCEC).copyA(alpha * 0.42f), Color(0xFF8068A8).copyA(alpha * 0.14f), Color.Transparent),
                center = Offset(W * 0.78f, H * 0.13f), radius = W * 0.30f),
            // Bruma fría baja.
            mist = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0x33241838).copyA(alpha)),
                startY = H * 0.68f, endY = H)
        )
        BiomeType.TOXIC -> SceneBrushes(
            // Cielo tóxico turbio (verde-negro arriba).
            nebula = Brush.verticalGradient(
                listOf(Color(0xFF0E1A06).copyA(alpha * 0.78f), Color(0xFF18280A).copyA(alpha * 0.42f), Color.Transparent),
                startY = 0f, endY = H * 0.55f),
            // Brillo del charco tóxico subiendo desde abajo (banda inferior; respira por alpha con el pulso).
            glow = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFF6FE030).copyA(alpha * 0.22f), Color(0xFF9CFF50).copyA(alpha * 0.40f)),
                startY = H * 0.60f, endY = H)
        )
        BiomeType.GLUTTON -> SceneBrushes(
            // Ambiente cálido de cocina (marrón oscuro arriba).
            nebula = Brush.verticalGradient(
                listOf(Color(0xFF1C1008).copyA(alpha * 0.72f), Color(0xFF2E1A0A).copyA(alpha * 0.40f), Color.Transparent),
                startY = 0f, endY = H * 0.55f),
            // Resplandor del fogón subiendo desde abajo (banda inferior; respira por alpha con el titileo).
            glow = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFFFF8A30).copyA(alpha * 0.22f), Color(0xFFFFC060).copyA(alpha * 0.42f)),
                startY = H * 0.62f, endY = H)
        )
        BiomeType.KING -> SceneBrushes(
            // Salón regio (morado profundo arriba).
            nebula = Brush.verticalGradient(
                listOf(Color(0xFF1A0E22).copyA(alpha * 0.74f), Color(0xFF2A1230).copyA(alpha * 0.40f), Color.Transparent),
                startY = 0f, endY = H * 0.55f),
            // Alfombra/tarima (rojo regio → oro) subiendo desde abajo; respira por alpha con las velas.
            glow = Brush.verticalGradient(
                listOf(Color.Transparent, Color(0xFF8A1828).copyA(alpha * 0.20f), Color(0xFFD4A23A).copyA(alpha * 0.36f)),
                startY = H * 0.60f, endY = H)
        )
        else -> {
            val p = biomePalette(biome)
            SceneBrushes(glow = Brush.radialGradient(
                listOf(p.accent.copyA(alpha * 0.22f), Color.Transparent),
                center = Offset(W * 0.5f, H * 0.7f), radius = W * 0.55f))
        }
    }
}

/** Dibuja la escena del bioma usando los brushes ya cacheados [b] + los valores animados del frame. */
private fun DrawScope.drawSceneAnim(
    biome: BiomeType,
    b: SceneBrushes,
    t: Float,
    phase: Float,
    shimmer: Float,
    alpha: Float,
    crystal: List<CrackLine>,
    sleepGeom: SleepGeom,
    rising: Boolean = false
) {
    if (alpha <= 0.01f) return
    when (biome) {
        BiomeType.CRYSTAL -> drawCrystalSceneA(b, crystal, t, alpha)
        BiomeType.SLEEP   -> drawSleepSceneA(b, sleepGeom, phase, shimmer, alpha, rising)
        BiomeType.STAR    -> drawStarSceneA(b, shimmer, alpha)
        BiomeType.GALACTIC -> drawGalacticSceneA(b, shimmer, alpha)
        BiomeType.FEAR    -> drawFearSceneA(b, phase, shimmer, alpha)
        BiomeType.GARDEN  -> drawGardenSceneA(b, phase, shimmer, alpha)
        BiomeType.STORM   -> drawStormSceneA(b, phase, shimmer, alpha)
        BiomeType.VOLCANO -> drawVolcanoSceneA(b, phase, shimmer, alpha)
        BiomeType.GLACIER -> drawGlacierSceneA(b, phase, shimmer, alpha)
        BiomeType.NINJA   -> drawNinjaSceneA(b, phase, shimmer, alpha)
        BiomeType.GHOST   -> drawGhostSceneA(b, phase, shimmer, alpha)
        BiomeType.TOXIC   -> drawToxicSceneA(b, phase, shimmer, alpha)
        BiomeType.GLUTTON -> drawGluttonSceneA(b, phase, shimmer, alpha)
        BiomeType.KING    -> drawKingSceneA(b, phase, shimmer, alpha)
        else              -> drawGenericGlowA(b, shimmer, alpha)
    }
}

/**
 * Velocidad ENTERA de ciclo (nº de ciclos por vuelta de `phase`). `phase` es un diente de sierra 0→1
 * con salto 1→0 (RepeatMode.Restart); `frac(speed·phase + off)` SOLO es continua en ese salto si
 * `speed` es entero (porque `frac(N + off) = frac(off)`). Toda partícula/onda cíclica pasa su velocidad
 * por aquí → el campo completo hace un BUCLE PERFECTO, sin el tirón que daba cada 9 s. Mínimo 1 ciclo.
 */
private fun loopSpeed(desired: Float): Float = kotlin.math.max(1f, kotlin.math.round(desired))

/**
 * Macizo de cristal hecho de BLOQUES CUADRADOS COMPACTOS (mismo lenguaje visual que el cristal de
 * las celdas), ESTÁTICO (sin destellos móviles). Conforme avanza [t] (nivel sube, nv5→nv10) cada
 * bloque primero se AGRIETA y luego se desvanece — los de arriba caen antes, erosionando el macizo
 * de arriba hacia abajo hasta que el Dormilón queda al descubierto.
 */
private fun DrawScope.drawCrystalSceneA(b: SceneBrushes, cracks: List<CrackLine>, t: Float, alpha: Float) {
    val W = size.width; val H = size.height

    // UNA sola lámina de cristal que cubre TODO el fondo. Pierde presencia conforme se rompe
    // (t sube → más translúcida) para que el Dormilón vaya quedando al descubierto.
    val paneA = alpha * (1f - 0.55f * t)
    if (paneA > 0.02f && b.pane != null) {
        // Degradado frío parejo (arriba y abajo igual de cristalinos).
        drawRect(brush = b.pane)
        // Brillos cristalinos: uno arriba y otro abajo, para que la lámina lea en todo el fondo.
        b.hi1?.let { drawCircle(brush = it, radius = W * 0.7f, center = Offset(W * 0.42f, H * 0.30f)) }
        b.hi2?.let { drawCircle(brush = it, radius = W * 0.7f, center = Offset(W * 0.55f, H * 0.80f)) }
        // Reflejos diagonales nítidos (sheen del cristal, arriba y abajo).
        drawLine(Color.White.copyA(paneA * 0.45f), Offset(W * 0.10f, H * 0.05f), Offset(W * 0.30f, H * 0.26f), W * 0.012f)
        drawLine(Color.White.copyA(paneA * 0.35f), Offset(W * 0.60f, H * 0.72f), Offset(W * 0.80f, H * 0.90f), W * 0.011f)
    }

    // Red de grietas que CRECE con t (nv5 sin grietas → nv10 toda agrietada).
    cracks.forEach { c ->
        if (t < c.appearAt) return@forEach
        val grow = ((t - c.appearAt) / 0.15f).coerceIn(0f, 1f)
        drawGrownPolyline(c.points, grow, W, H, Color(0xFF0A1830).copyA(alpha * 0.9f), c.width * W)
        drawGrownPolyline(c.points, grow, W, H, Color.White.copyA(alpha * 0.28f), c.width * W * 0.4f, dy = 1f)
    }
}

/**
 * ¿La escena de [biome] usa los relojes `phase`/`shimmer`? Solo el Cristal no: su macizo depende del
 * nivel, no del tiempo. Si una escena nueva deja de animar, añádela aquí y su fondo dejará de repintarse.
 */
private fun sceneAnimates(biome: BiomeType): Boolean = biome != BiomeType.CRYSTAL

/**
 * Dibuja una polilínea (puntos normalizados 0..1) hasta una fracción [frac] de su longitud total.
 * Sin asignaciones: antes creaba una lista de puntos y un `FloatArray` por grieta en cada llamada.
 */
private fun DrawScope.drawGrownPolyline(
    pts: List<Offset>, frac: Float, W: Float, H: Float, color: Color, width: Float, dy: Float = 0f
) {
    if (pts.size < 2 || frac <= 0f) return
    var total = 0f
    for (i in 0 until pts.size - 1) total += segLength(pts[i], pts[i + 1], W, H)
    if (total <= 0f) return
    var budget = total * frac
    for (i in 0 until pts.size - 1) {
        val a = Offset(pts[i].x * W, pts[i].y * H + dy)
        val b = Offset(pts[i + 1].x * W, pts[i + 1].y * H + dy)
        val l = segLength(pts[i], pts[i + 1], W, H)
        if (budget >= l) {
            drawLine(color, a, b, width)
            budget -= l
        } else {
            val f = (budget / l).coerceIn(0f, 1f)
            drawLine(color, a, a + (b - a) * f, width)
            break
        }
    }
}

/** Longitud en píxeles del tramo p→q (coordenadas normalizadas); el desfase vertical no la cambia. */
private fun segLength(p: Offset, q: Offset, W: Float, H: Float): Float {
    val dx = (q.x - p.x) * W; val dy = (q.y - p.y) * H
    return kotlin.math.sqrt(dx * dx + dy * dy)
}

/** Bosque del sueño: niebla onírica baja + estrellitas + "Z" flotantes. */
private fun DrawScope.drawSleepSceneA(b: SceneBrushes, g: SleepGeom, phase: Float, shimmer: Float, alpha: Float, rising: Boolean) {
    val W = size.width; val H = size.height
    // Niebla en montículos cerca del suelo (centro fijo → brush cacheado; el micro-bob se omitió).
    val cy = H * (if (rising) 0.78f else 0.72f)
    b.fog?.let { fogBrushes ->
        g.fog.forEachIndexed { i, fog ->
            drawOval(
                brush = fogBrushes[i],
                topLeft = Offset(fog.x * W - fog.r * W, cy - fog.r * W * 0.45f),
                size = Size(fog.r * W * 2f, fog.r * W * 0.9f)
            )
        }
    }
    // Estrellas que titilan.
    g.stars.forEach { st ->
        val a = alpha * (0.3f + 0.7f * (0.5f + 0.5f * sin((shimmer + st.phase) * 6.28318f)))
        drawCircle(Color(0xFFEFE9FF).copyA(a), st.r * W, Offset(st.x * W, st.y * H))
    }
    // "Z" que ascienden.
    g.zs.forEach { z ->
        val prog = (phase * z.speed + z.phase) % 1f
        val zy = H * (if (rising) 0.95f else 0.85f) - prog * H * 0.5f
        val zx = z.x * W + sin(prog * 6.28318f) * W * 0.02f
        val s = z.size * W
        val a = alpha * (sin(prog * 3.14159f)).coerceIn(0f, 1f) * 0.8f
        drawZ(Offset(zx, zy), s, Color(0xFFD8CCFF).copyA(a))
    }
}

/**
 * Cielo Estelar: campo de estrellas que titilan + una nebulosa suave + un par de constelaciones.
 * Colocación DETERMINISTA por hash (sin Random/allocs por frame). Encaja con la mecánica de
 * gravedad estelar (la estrella que tira de la madera vive en el VFX del tablero, no aquí).
 */
private fun DrawScope.drawStarSceneA(b: SceneBrushes, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    // Hash determinista 0..1 a partir de un entero (sin asignaciones).
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }
    // Nebulosa fría de fondo (radio FIJO cacheado; respira por alpha en vez de por radio).
    b.nebula?.let {
        val breathe = 0.9f + 0.1f * (0.5f + 0.5f * sin(shimmer * 6.28318f))
        drawCircle(brush = it, radius = W * 0.6f, center = Offset(W * 0.42f, H * 0.38f), alpha = breathe)
    }
    // Campo de estrellas titilantes.
    val n = 46
    for (i in 0 until n) {
        val x = h(i, 12.9898f) * W
        val y = h(i, 78.233f) * H
        val baseR = (0.6f + h(i, 4.137f) * 1.8f) * (W / 360f)
        val tw = 0.35f + 0.65f * (0.5f + 0.5f * sin((shimmer + h(i, 7.19f)) * 6.28318f))
        val a = alpha * tw
        drawCircle(Color.White.copyA(a), baseR, Offset(x, y))
        // Destello en cruz para las más brillantes.
        if (h(i, 2.71f) > 0.78f) {
            val g = baseR * 3.2f
            drawLine(Color(0xFFFFF3C0).copyA(a * 0.7f), Offset(x - g, y), Offset(x + g, y), baseR * 0.5f, cap = StrokeCap.Round)
            drawLine(Color(0xFFFFF3C0).copyA(a * 0.7f), Offset(x, y - g), Offset(x, y + g), baseR * 0.5f, cap = StrokeCap.Round)
        }
    }
    // Dos constelaciones: polilíneas tenues uniendo algunas estrellas.
    val lineA = alpha * (0.18f + 0.12f * (0.5f + 0.5f * sin(shimmer * 6.28318f)))
    fun star(i: Int) = Offset(h(i, 12.9898f) * W, h(i, 78.233f) * H)
    val constA = intArrayOf(3, 9, 14, 21, 27)
    val constB = intArrayOf(33, 38, 41, 44)
    for (c in 0 until constA.size - 1) drawLine(Color(0xFFBFD0FF).copyA(lineA), star(constA[c]), star(constA[c + 1]), W * 0.0025f)
    for (c in 0 until constB.size - 1) drawLine(Color(0xFFBFD0FF).copyA(lineA), star(constB[c]), star(constB[c + 1]), W * 0.0025f)
}

/**
 * Espacio Profundo (nivel 100, climax): campo estelar denso + nebulosa púrpura/azul + una galaxia
 * espiral lejana girando despacio. Tono más oscuro y "profundo" que STAR para cerrar la progresión.
 */
private fun DrawScope.drawGalacticSceneA(b: SceneBrushes, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }
    // Nebulosa púrpura profunda (radio FIJO cacheado; respira por alpha en vez de por radio).
    b.nebula?.let {
        val breathe = 0.9f + 0.1f * (0.5f + 0.5f * sin(shimmer * 6.28318f))
        drawCircle(brush = it, radius = W * 0.85f, center = Offset(W * 0.5f, H * 0.5f), alpha = breathe)
    }
    // Campo estelar denso (más estrellas que STAR para sensación de profundidad).
    val n = 70
    for (i in 0 until n) {
        val x = h(i, 12.9898f) * W
        val y = h(i, 78.233f) * H
        val baseR = (0.4f + h(i, 4.137f) * 1.5f) * (W / 360f)
        val tw = 0.3f + 0.7f * (0.5f + 0.5f * sin((shimmer + h(i, 7.19f)) * 6.28318f))
        drawCircle(Color.White.copyA(alpha * tw), baseR, Offset(x, y))
    }
    // Galaxia espiral lejana girando despacio (2 brazos de puntos).
    val gc = Offset(W * 0.72f, H * 0.28f)
    val rot = shimmer * 6.28318f * 0.15f
    val arms = 2
    val pts = 26
    for (arm in 0 until arms) {
        val base = arm * (6.28318f / arms)
        for (k in 0 until pts) {
            val tt = k / pts.toFloat()
            val ang = base + rot + tt * 6.0f
            val rad = tt * W * 0.13f
            val px = gc.x + cos(ang) * rad
            val py = gc.y + sin(ang) * rad * 0.6f   // achatada (perspectiva)
            val a = alpha * (1f - tt) * 0.7f
            drawCircle(Color(0xFFD9C2FF).copyA(a), (W / 380f) * (1.4f - tt), Offset(px, py))
        }
    }
    // Núcleo brillante de la galaxia (brush cacheado).
    b.core?.let { drawCircle(brush = it, radius = W * 0.06f, center = gc) }
}

/**
 * Pasaje Encantado (FEAR): oscuridad que se cierra desde los bordes (viñeta), niebla fría baja, wisps
 * pálidos a la deriva y — el sello del bioma — PARES DE OJOS que acechan en la penumbra del perímetro:
 * aparecen, miran de reojo y parpadean antes de desvanecerse. Posiciones DETERMINISTAS por hash (sin
 * Random/allocs por frame); viñeta + niebla vienen cacheadas en [b]. Los ojos se sesgan a los BORDES
 * (no al centro) para que no queden tapados por el tablero y para reforzar el "acechan desde la sombra".
 */
private fun DrawScope.drawFearSceneA(b: SceneBrushes, phase: Float, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }

    // Niebla fría baja (banda cacheada).
    b.mist?.let { drawRect(brush = it) }

    // Wisps pálidos (fuegos fatuos): suben muy despacio y laten (env = 0 al nacer y al morir → sin saltos).
    for (i in 0 until 7) {
        val cyc = (phase * loopSpeed(0.4f + h(i, 5.3f) * 0.5f) + h(i, 2.1f)) % 1f
        val wx = (0.08f + h(i, 9.7f) * 0.84f) * W + sin((cyc + h(i, 1.3f)) * 6.28318f) * W * 0.03f
        val wy = H * (0.95f - cyc * 0.8f)
        val env = sin(cyc * 3.14159f).coerceIn(0f, 1f)
        val a = alpha * env * 0.55f
        val r = W * (0.006f + h(i, 7.7f) * 0.006f)
        drawCircle(Color(0xFFAFC4E0).copyA(a * 0.45f), r * 2.8f, Offset(wx, wy))   // halo
        drawCircle(Color(0xFFEAF2FF).copyA(a), r, Offset(wx, wy))                  // núcleo
    }

    // Pares de OJOS acechando desde los BORDES del pasaje.
    for (i in 0 until 7) {
        val speed = loopSpeed(0.16f + h(i, 3.1f) * 0.12f)
        val cyc = (phase * speed + h(i, 8.9f)) % 1f
        val present = sin(cyc * 3.14159f).coerceIn(0f, 1f)   // aparece (0→1) y se va (1→0)
        if (present < 0.06f) continue
        val along = h(i, 12.99f); val jit = h(i, 4.57f)
        val ex: Float; val ey: Float
        when (i % 4) {
            0    -> { ex = (0.10f + along * 0.80f) * W; ey = (0.04f + jit * 0.09f) * H }   // borde superior
            1    -> { ex = (0.10f + along * 0.80f) * W; ey = (0.87f + jit * 0.09f) * H }   // borde inferior
            2    -> { ex = (0.03f + jit * 0.08f) * W;   ey = (0.14f + along * 0.70f) * H }  // borde izquierdo
            else -> { ex = (0.89f + jit * 0.08f) * W;   ey = (0.14f + along * 0.70f) * H }  // borde derecho
        }
        val look = (h(i, 6.6f) - 0.5f)                       // mirada lateral (offset de pupila)
        val eyeR = W * (0.011f + h(i, 2.7f) * 0.007f)
        val gap = eyeR * (2.3f + h(i, 1.9f) * 1.2f)          // separación entre los dos ojos
        // Parpadeo: varios ciclos por vida; cierre rápido en el último 8% de cada ciclo.
        val bp = (cyc * (3f + i % 3) + h(i, 1.1f)) % 1f
        val open = (if (bp > 0.92f) kotlin.math.abs((bp - 0.92f) / 0.08f * 2f - 1f) else 1f).coerceIn(0.08f, 1f)
        val a = alpha * present
        val glow = if (i % 2 == 0) Color(0xFFE9DF92) else Color(0xFFB8E0E0)   // unos cálidos, otros fríos
        var s = -1
        while (s <= 1) {
            val cx = ex + s * gap
            drawCircle(glow.copyA(a * 0.20f), eyeR * 2.7f, Offset(cx, ey))     // halo del ojo
            drawOval(                                                          // globo (almendra, achatada al parpadear)
                color = glow.copyA(a * 0.92f),
                topLeft = Offset(cx - eyeR, ey - eyeR * open),
                size = Size(eyeR * 2f, eyeR * 2f * open)
            )
            if (open > 0.4f) drawCircle(                                       // pupila (mira de reojo)
                Color(0xFF14101A).copyA(a), eyeR * 0.40f, Offset(cx + look * eyeR * 0.7f, ey))
            s += 2
        }
    }

    // Viñeta de oscuridad que se cierra desde los bordes (cacheada); late muy leve con shimmer.
    b.vignette?.let {
        val breathe = 0.85f + 0.15f * (0.5f + 0.5f * sin(shimmer * 6.28318f))
        drawRect(brush = it, alpha = breathe)
    }
}

/**
 * Jardín (GARDEN): luz de dosel filtrándose desde arriba + haces verde-dorados, ENREDADERAS trepando
 * por los bordes laterales con hojas y una flor en la punta (eco de la mecánica de vides que atrapan
 * bloques), maleza inferior y esporas/luciérnagas que ascienden. Posiciones DETERMINISTAS por hash y
 * vides como polilíneas de drawLine (sin Path) → 0 asignaciones por frame; brushes cacheados en [b].
 */
private fun DrawScope.drawGardenSceneA(b: SceneBrushes, phase: Float, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }

    // 1) Luz de dosel filtrándose desde arriba (radial cacheado, respira por alpha).
    b.nebula?.let {
        val breathe = 0.85f + 0.15f * (0.5f + 0.5f * sin(shimmer * 6.28318f))
        drawCircle(brush = it, radius = W * 0.72f, center = Offset(W * 0.5f, -H * 0.04f), alpha = breathe)
    }

    // 2) Haces de luz verde-dorados cayendo entre las hojas (alpha titila suave; derivan con phase).
    val rayA = alpha * (0.04f + 0.045f * (0.5f + 0.5f * sin(shimmer * 6.28318f)))
    for (i in 0 until 4) {
        val rx = (0.16f + i * 0.22f) * W + sin((phase + i * 0.27f) * 6.28318f) * W * 0.012f
        drawLine(Color(0xFFD6FFAC).copyA(rayA), Offset(rx, -H * 0.02f),
            Offset(rx + W * 0.07f, H * 0.78f), strokeWidth = W * 0.055f, cap = StrokeCap.Round)
    }

    // 3) Maleza inferior (banda de gradiente cacheada).
    b.mist?.let { drawRect(brush = it) }

    // 4) Enredaderas trepando por los bordes laterales, con hojas + flor (polilínea ondulante).
    drawGardenVine(W * 0.05f, 0.86f, W * 0.05f, 11.3f, phase, alpha)
    drawGardenVine(W * 0.95f, 0.80f, W * 0.05f, 27.7f, phase, alpha)
    drawGardenVine(W * 0.15f, 0.52f, W * 0.04f, 41.1f, phase, alpha)

    // 5) Esporas/luciérnagas verde-doradas que suben y laten (env = 0 al nacer y al morir).
    for (i in 0 until 9) {
        val cyc = (phase * loopSpeed(0.5f + h(i, 5.3f) * 0.6f) + h(i, 2.1f)) % 1f
        val mx = (0.10f + h(i, 9.7f) * 0.80f) * W + sin((cyc + h(i, 1.3f)) * 6.28318f) * W * 0.025f
        val my = H * (0.96f - cyc * 0.9f)
        val env = sin(cyc * 3.14159f).coerceIn(0f, 1f)
        val a = alpha * env * 0.6f
        val r = W * (0.005f + h(i, 7.7f) * 0.005f)
        drawCircle(Color(0xFFCFFF8A).copyA(a * 0.4f), r * 3f, Offset(mx, my))   // halo
        drawCircle(Color(0xFFF2FFD0).copyA(a), r, Offset(mx, my))               // núcleo
    }
}

/**
 * Una enredadera que trepa desde el suelo ([baseX], H) hasta una altura [topFrac] del alto, curvándose
 * (rizo + vaivén con [sway]). Se dibuja como polilínea de segmentos de drawLine (sin Path) con hojas
 * alternadas y una flor en la punta → 0 asignaciones por frame.
 */
private fun DrawScope.drawGardenVine(baseX: Float, topFrac: Float, ampW: Float, seed: Float, sway: Float, alpha: Float) {
    val W = size.width; val H = size.height
    val vineDark = Color(0xFF2E5E25); val vineMid = Color(0xFF4E9C3A)
    val leafA = Color(0xFF9CE06A); val leafB = Color(0xFF5FA83C)
    val topY = H * (1f - topFrac)
    val segs = 16
    var prevX = baseX; var prevY = H
    for (s in 1..segs) {
        val tt = s / segs.toFloat()
        val y = H - (H - topY) * tt
        val curl = sin(tt * 3.2f * 6.28318f + seed) * ampW * (0.35f + 0.65f * tt)
        val swayX = sin(sway * 6.28318f + seed + tt * 2.2f) * W * 0.012f * tt
        val x = baseX + curl + swayX
        val wBase = W * 0.013f * (1.05f - tt * 0.7f)
        drawLine(vineDark.copy(alpha = vineDark.alpha * alpha), Offset(prevX, prevY), Offset(x, y), strokeWidth = wBase, cap = StrokeCap.Round)
        drawLine(vineMid.copy(alpha = vineMid.alpha * alpha * 0.9f), Offset(prevX, prevY), Offset(x, y), strokeWidth = wBase * 0.45f, cap = StrokeCap.Round)
        // Hoja cada 2 segmentos, alternando lado (óvalo alargado).
        if (s % 2 == 0 && tt < 0.96f) {
            val side = if (s % 4 == 0) 1f else -1f
            val lw = W * 0.026f * (1.1f - tt * 0.5f); val lh = lw * 0.6f
            val lx = x + side * lw * 0.7f
            drawOval(leafB.copy(alpha = leafB.alpha * alpha), topLeft = Offset(lx - lw / 2f, y - lh / 2f), size = Size(lw, lh))
            drawOval(leafA.copy(alpha = leafA.alpha * alpha * 0.7f), topLeft = Offset(lx - lw * 0.35f, y - lh * 0.35f), size = Size(lw * 0.7f, lh * 0.7f))
        }
        prevX = x; prevY = y
    }
    // Flor en la punta: 5 pétalos rosados + corazón dorado.
    val fx = prevX; val fy = prevY
    val fr = W * 0.015f
    for (p in 0 until 5) {
        // La flor gira UNA vuelta completa por ciclo de phase (sway·2π) → continua en el salto 1→0.
        val a = p * (6.28318f / 5f) + sway * 6.28318f
        drawCircle(Color(0xFFFFC7E6).copy(alpha = 0.85f * alpha), fr * 0.72f, Offset(fx + cos(a) * fr, fy + sin(a) * fr))
    }
    drawCircle(Color(0xFFFFE08A).copy(alpha = alpha), fr * 0.55f, Offset(fx, fy))
}

/**
 * Tormenta (STORM): banco de nubes oscuras arriba, RELÁMPAGOS temporizados (fogonazo + rayo dentado)
 * cada ~3s, lluvia diagonal cayendo y chispas eléctricas amarillas cerca de la base de las nubes (eco
 * de las bobinas que hay que descargar). Todo determinista por hash + líneas de drawLine (sin Path) →
 * 0 asignaciones por frame; nubes/resplandor/bruma vienen cacheados en [b].
 */
private fun DrawScope.drawStormSceneA(b: SceneBrushes, phase: Float, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }

    // Temporización del relámpago: 3 fogonazos por vuelta de phase (~3s aparte). El fogonazo brota al
    // inicio de cada ciclo y decae rápido, con un parpadeo veloz mientras dura (env=0 → sin saltos al wrap).
    val seg = (phase * 3f).toInt()
    val flashCycle = (phase * 3f) % 1f
    val decay = (1f - flashCycle * 7f).coerceAtLeast(0f)
    val flashI = (decay * (0.55f + 0.45f * sin(phase * 260f))).coerceIn(0f, 1f)

    // 1) Resplandor del relámpago tras las nubes (radial cacheado; alpha = intensidad del fogonazo).
    if (flashI > 0.02f) b.glow?.let {
        drawCircle(brush = it, radius = W * 0.5f, center = Offset(W * 0.5f, H * 0.16f), alpha = flashI * 0.7f)
    }

    // 2) Banco de nubes: gradiente cacheado + cúmulos translúcidos (Color plano → 0 allocs).
    b.nebula?.let { drawRect(brush = it) }
    val cloudCol = Color(0xFF141C2E)
    for (i in 0 until 6) {
        val cx = (0.06f + i * 0.18f) * W + sin((phase + i * 0.3f) * 6.28318f) * W * 0.008f
        val cy = H * (0.09f + h(i, 3.3f) * 0.06f)
        val cr = W * (0.10f + h(i, 6.1f) * 0.07f)
        drawCircle(cloudCol.copyA(alpha * 0.9f), cr, Offset(cx, cy))
        drawCircle(cloudCol.copyA(alpha * 0.7f), cr * 0.7f, Offset(cx + cr * 0.5f, cy + cr * 0.3f))
    }

    // 3) Rayo dentado mientras dura el fogonazo (polilínea con glow + núcleo).
    if (flashI > 0.05f) {
        val bx = (0.22f + h(seg, 7.3f) * 0.56f) * W
        val topY = H * 0.14f
        val botY = H * (0.42f + h(seg, 3.1f) * 0.22f)
        var px = bx; var py = topY
        val steps = 7
        for (k in 1..steps) {
            val tt = k / steps.toFloat()
            val nx = bx + (h(seg * 17 + k, 9.1f) - 0.5f) * W * 0.12f
            val ny = topY + (botY - topY) * tt
            drawLine(Color(0xFFBFD2FF).copyA(flashI * 0.5f), Offset(px, py), Offset(nx, ny), W * 0.016f, cap = StrokeCap.Round) // glow
            drawLine(Color.White.copyA(flashI), Offset(px, py), Offset(nx, ny), W * 0.005f, cap = StrokeCap.Round)               // núcleo
            px = nx; py = ny
        }
    }

    // 4) Lavado de fogonazo a pantalla completa (muy sutil).
    if (flashI > 0.02f) drawRect(Color(0xFFCEE2FF).copyA(alpha * flashI * 0.08f))

    // 5) Lluvia: estrías finas diagonales que caen (columnas deterministas, bucle hacia abajo con phase).
    val rainCol = Color(0xFFAEC2E0)
    for (i in 0 until 42) {
        val rx = h(i, 12.99f) * W
        val sp = 0.85f + h(i, 4.1f) * 0.6f
        val prog = (phase * loopSpeed(7f * sp) + h(i, 2.3f)) % 1f
        val ry = -H * 0.05f + prog * H * 1.05f
        val len = H * (0.035f + h(i, 7.7f) * 0.02f)
        val a = alpha * (0.14f + h(i, 5.9f) * 0.16f)
        drawLine(rainCol.copyA(a), Offset(rx, ry), Offset(rx - W * 0.012f, ry + len), W * 0.0035f, cap = StrokeCap.Round)
    }

    // 6) Bruma baja (banda cacheada).
    b.mist?.let { drawRect(brush = it) }

    // 7) Chispas eléctricas amarillas cerca de la base de las nubes (eco de las bobinas a descargar).
    for (i in 0 until 5) {
        val cyc = (phase * 2f + h(i, 5.3f)) % 1f
        val env = sin(cyc * 3.14159f).coerceIn(0f, 1f)
        if (env < 0.05f) continue
        val sxp = (0.15f + h(i, 9.7f) * 0.7f) * W
        val syp = (0.22f + h(i, 3.7f) * 0.12f) * H
        val a = alpha * env * 0.8f
        val sr = W * 0.01f
        drawLine(Color(0xFFFFF59A).copyA(a), Offset(sxp - sr, syp), Offset(sxp + sr, syp), W * 0.004f, cap = StrokeCap.Round)
        drawLine(Color(0xFFFFF59A).copyA(a), Offset(sxp, syp - sr), Offset(sxp, syp + sr), W * 0.004f, cap = StrokeCap.Round)
        drawCircle(Color.White.copyA(a), sr * 0.35f, Offset(sxp, syp))
    }
}

/**
 * Volcán (VOLCANO): cielo de ceniza con rescoldo, DOS volcanes en las esquinas inferiores con cráter
 * incandescente que late, un brillo de lava subiendo desde abajo (eco de la mecánica de lava que sube/
 * baja), BRASAS ascendentes que titilan (el sello) y humo/ceniza que sube lento. Relleno de las montañas
 * por columnas de drawLine (sin Path) + posiciones deterministas por hash → 0 asignaciones por frame.
 */
private fun DrawScope.drawVolcanoSceneA(b: SceneBrushes, phase: Float, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }
    val pulse = 0.5f + 0.5f * sin(shimmer * 6.28318f)   // latido lento de la lava

    // 1) Cielo de ceniza con rescoldo (gradiente cacheado).
    b.nebula?.let { drawRect(brush = it) }

    // 2) Volcanes en las esquinas inferiores + cráter incandescente (latido por pulse).
    drawVolcano(W * 0.16f, H * 0.80f, W * 0.22f, H, alpha, pulse)
    drawVolcano(W * 0.86f, H * 0.86f, W * 0.20f, H, alpha, pulse)

    // 3) Brillo de lava subiendo desde abajo (gradiente cacheado; respira con el pulso).
    b.glow?.let { drawRect(brush = it, alpha = 0.8f + 0.2f * pulse) }

    // 4) Brasas ascendentes que titilan — el sello del bioma (env=0 al nacer y al morir → sin saltos).
    for (i in 0 until 28) {
        val cyc = (phase * loopSpeed(0.8f + h(i, 5.3f) * 0.8f) + h(i, 2.1f)) % 1f
        val ex = (0.05f + h(i, 9.7f) * 0.9f) * W + sin((cyc + h(i, 1.3f)) * 6.28318f) * W * 0.03f
        val ey = H * (1.0f - cyc * 1.05f)
        val env = sin(cyc * 3.14159f).coerceIn(0f, 1f)
        val flick = 0.6f + 0.4f * sin((phase * 40f + i) * 6.28318f)
        val a = alpha * env * flick * 0.8f
        val r = W * (0.004f + h(i, 7.7f) * 0.005f)
        val col = if (h(i, 2.7f) > 0.5f) Color(0xFFFFC56A) else Color(0xFFFF7A2E)
        drawCircle(col.copyA(a * 0.4f), r * 2.6f, Offset(ex, ey))   // halo
        drawCircle(col.copyA(a), r, Offset(ex, ey))                 // núcleo
    }

    // 5) Humo/ceniza ascendente (círculos oscuros translúcidos, muy lentos).
    for (i in 0 until 5) {
        val cyc = (phase * loopSpeed(0.18f + h(i, 4.4f) * 0.12f) + h(i, 6.6f)) % 1f
        val sx = ((if (i % 2 == 0) 0.16f else 0.86f) + (h(i, 8.1f) - 0.5f) * 0.10f) * W
        val sy = H * (0.78f - cyc * 0.7f)
        val env = sin(cyc * 3.14159f).coerceIn(0f, 1f)
        val a = alpha * env * 0.16f
        val r = W * (0.06f + cyc * 0.08f)
        drawCircle(Color(0xFF20140E).copyA(a), r, Offset(sx, sy))
    }
}

/**
 * Una montaña-volcán: cuerpo triangular rellenado por COLUMNAS verticales de drawLine (sin Path → 0
 * allocs), con rim-light incandescente en las laderas y un cráter de círculos translúcidos que late.
 */
private fun DrawScope.drawVolcano(peakX: Float, peakY: Float, halfW: Float, baseY: Float, alpha: Float, pulse: Float) {
    val W = size.width
    val rock = Color(0xFF160C0A)
    val cols = 26
    val step = (2f * halfW) / cols
    for (c in 0..cols) {
        val u = c / cols.toFloat()
        val x = peakX - halfW + u * 2f * halfW
        val slopeY = peakY + kotlin.math.abs(x - peakX) / halfW * (baseY - peakY)
        drawLine(rock.copyA(alpha), Offset(x, slopeY), Offset(x, baseY), strokeWidth = step + 1f)
    }
    val glowA = alpha * (0.55f + 0.45f * pulse)
    // Rim-light de las laderas (rescoldo escurriendo por los flancos).
    drawLine(Color(0xFFFF6A2A).copyA(glowA * 0.8f), Offset(peakX - halfW, baseY), Offset(peakX, peakY), strokeWidth = W * 0.006f, cap = StrokeCap.Round)
    drawLine(Color(0xFFFF6A2A).copyA(glowA * 0.8f), Offset(peakX + halfW, baseY), Offset(peakX, peakY), strokeWidth = W * 0.006f, cap = StrokeCap.Round)
    // Cráter incandescente (círculos layered → 0 allocs).
    drawCircle(Color(0xFFFF6A1E).copyA(glowA * 0.5f), halfW * 0.55f, Offset(peakX, peakY))
    drawCircle(Color(0xFFFFA040).copyA(glowA * 0.7f), halfW * 0.30f, Offset(peakX, peakY))
    drawCircle(Color(0xFFFFE0A0).copyA(glowA), halfW * 0.13f, Offset(peakX, peakY))
}

/**
 * Glaciar (GLACIER): cielo helado nocturno con AURORA boreal ondulante, CARÁMBANOS colgando del borde
 * superior, una BRISA de nieve cayendo en diagonal (eco de la brisa helada que congela los bloques),
 * destellos de diamante que titilan y un suelo helado abajo. Auroras/carámbanos como líneas de drawLine
 * (sin Path) + nieve/destellos deterministas por hash → 0 asignaciones por frame; brushes cacheados en [b].
 */
private fun DrawScope.drawGlacierSceneA(b: SceneBrushes, phase: Float, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }

    // 1) Cielo helado (cacheado).
    b.nebula?.let { drawRect(brush = it) }

    // 2) Aurora boreal: dos cortinas onduladas (verde-cian) que se desplazan con phase.
    drawAurora(0.16f, 0.025f, Color(0xFF6BF0C0), phase, alpha, 1.7f, 0f)
    drawAurora(0.24f, 0.020f, Color(0xFF7AC8FF), phase, alpha, 2.3f, 0.5f)

    // 3) Carámbanos colgando del borde superior (relleno triangular por columnas → 0 allocs).
    val nIce = 9
    for (i in 0 until nIce) {
        val cx = (0.04f + i * (0.92f / (nIce - 1))) * W
        val halfW = W * (0.018f + h(i, 3.7f) * 0.012f)
        val len = H * (0.05f + h(i, 6.3f) * 0.10f)
        drawIcicle(cx, halfW, len, alpha, shimmer, i)
    }

    // 4) Brisa de nieve: copos cayendo en diagonal con deriva lateral por viento (el sello del bioma).
    for (i in 0 until 46) {
        val sp = 0.7f + h(i, 4.1f) * 0.6f
        val prog = (phase * loopSpeed(4f * sp) + h(i, 2.3f)) % 1f
        val baseX = h(i, 12.99f) * W
        val sway = sin((prog * 2f + h(i, 1.7f)) * 6.28318f) * W * 0.05f
        val sx = baseX + sway + prog * W * 0.10f
        val sy = -H * 0.03f + prog * H * 1.06f
        val r = W * (0.003f + h(i, 7.7f) * 0.004f)
        val a = alpha * (0.40f + h(i, 5.9f) * 0.5f)
        drawCircle(Color(0xFFEAF6FF).copyA(a), r, Offset(sx, sy))
    }

    // 5) Destellos de diamante (glints helados que titilan con shimmer).
    for (i in 0 until 7) {
        val tw = 0.5f + 0.5f * sin((shimmer + h(i, 7.19f)) * 6.28318f)
        if (tw < 0.55f) continue
        val gx = h(i, 12.9898f) * W
        val gy = (0.10f + h(i, 78.233f) * 0.7f) * H
        val a = alpha * (tw - 0.55f) / 0.45f
        val s = W * 0.012f
        drawLine(Color.White.copyA(a), Offset(gx - s, gy), Offset(gx + s, gy), W * 0.0025f, cap = StrokeCap.Round)
        drawLine(Color.White.copyA(a), Offset(gx, gy - s), Offset(gx, gy + s), W * 0.0025f, cap = StrokeCap.Round)
    }

    // 6) Suelo helado / neblina baja (cacheado).
    b.mist?.let { drawRect(brush = it) }
}

/**
 * Una cortina de aurora: polilínea ondulante (sin Path) dibujada como columnas verticales tenues que
 * cuelgan de una banda a [bandYFrac] del alto y se desplazan con [sway]. Color plano → 0 allocs/frame.
 */
private fun DrawScope.drawAurora(bandYFrac: Float, ampFrac: Float, col: Color, sway: Float, alpha: Float, freq: Float, off: Float) {
    val W = size.width; val H = size.height
    val bandY = bandYFrac * H; val amp = ampFrac * H
    val cols = 48
    for (c in 0..cols) {
        val u = c / cols.toFloat()
        val x = u * W
        val y = bandY + sin((u * freq + sway + off) * 6.28318f) * amp
        val edgeFade = sin(u * 3.14159f)   // tenue en los extremos
        val curtain = H * (0.10f + 0.05f * (0.5f + 0.5f * sin((u * 5f + sway * 2f) * 6.28318f)))
        val a = alpha * 0.10f * edgeFade
        if (a <= 0.002f) continue
        drawLine(col.copyA(a * 0.6f), Offset(x, y), Offset(x, y + curtain), strokeWidth = W * 0.020f)
        drawLine(col.copyA(a), Offset(x, y), Offset(x, y + curtain * 0.7f), strokeWidth = W * 0.008f)
    }
}

/**
 * Un carámbano colgando del borde superior: triángulo apuntando hacia abajo rellenado por COLUMNAS
 * verticales de drawLine (sin Path → 0 allocs), con un brillo central y un glint en la punta que titila.
 */
private fun DrawScope.drawIcicle(cx: Float, halfW: Float, len: Float, alpha: Float, shimmer: Float, seed: Int) {
    val ice = Color(0xFFBFE6FF)
    val cols = 8
    for (c in 0..cols) {
        val u = c / cols.toFloat()
        val x = cx - halfW + u * 2f * halfW
        val colLen = len * (1f - kotlin.math.abs(u - 0.5f) * 2f)   // punta en el centro
        if (colLen <= 0f) continue
        drawLine(ice.copyA(alpha * 0.5f), Offset(x, 0f), Offset(x, colLen), strokeWidth = (2f * halfW / cols) + 1f)
    }
    drawLine(Color.White.copyA(alpha * 0.6f), Offset(cx, 0f), Offset(cx, len * 0.85f), strokeWidth = halfW * 0.4f)
    val tw = 0.5f + 0.5f * sin((shimmer * 6.28318f) + seed)
    drawCircle(Color.White.copyA(alpha * tw * 0.7f), halfW * 0.3f, Offset(cx, len))
}

/**
 * Dojo en Sombras (NINJA): noche con LUNA llena pálida y su halo, BAMBÚES silueteados meciéndose en
 * los bordes, NEBLINA nocturna a la deriva cruzando despacio, PÉTALOS de cerezo que giran al caer y
 * un par de SHURIKENS lejanos girando (atmósfera; el shuriken real es el del ninja idle-gated).
 * Bambúes/pétalos/shurikens como líneas+óvalos de drawscope (sin Path) + deterministas por hash → 0 allocs/frame.
 */
private fun DrawScope.drawNinjaSceneA(b: SceneBrushes, phase: Float, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }

    // 1) Cielo nocturno (cacheado).
    b.nebula?.let { drawRect(brush = it) }

    // 2) Luna llena pálida con halo (halo cacheado en glow; cráteres con círculos planos → 0 allocs).
    val moon = Offset(W * 0.74f, H * 0.16f)
    val mr = W * 0.085f
    b.glow?.let {
        val breathe = 0.85f + 0.15f * (0.5f + 0.5f * sin(shimmer * 6.28318f))
        drawCircle(brush = it, radius = W * 0.32f, center = moon, alpha = breathe)
    }
    drawCircle(Color(0xFFF2ECDD).copyA(alpha * 0.9f), mr, moon)
    drawCircle(Color(0xFFD8D0BE).copyA(alpha * 0.5f), mr * 0.22f, moon + Offset(-mr * 0.3f, -mr * 0.2f))
    drawCircle(Color(0xFFD8D0BE).copyA(alpha * 0.45f), mr * 0.18f, moon + Offset(mr * 0.1f, -mr * 0.4f))
    drawCircle(Color(0xFFD8D0BE).copyA(alpha * 0.4f), mr * 0.15f, moon + Offset(mr * 0.35f, mr * 0.25f))

    // 3) Bambúes silueteados meciéndose en los bordes.
    drawBamboo(W * 0.06f, 0.62f, phase, alpha, 3.1f, 1f)
    drawBamboo(W * 0.13f, 0.48f, phase, alpha, 7.7f, 1f)
    drawBamboo(W * 0.94f, 0.66f, phase, alpha, 5.3f, -1f)
    drawBamboo(W * 0.88f, 0.42f, phase, alpha, 9.1f, -1f)

    // 4) Humo a la deriva cruzando despacio (puffs oscuros translúcidos).
    for (i in 0 until 5) {
        val drift = (phase * loopSpeed(0.25f + h(i, 4.4f) * 0.15f) + h(i, 6.6f)) % 1f
        val sx = (-0.1f + drift * 1.2f) * W
        val sy = H * (0.35f + h(i, 8.1f) * 0.45f)
        val env = sin(drift * 3.14159f).coerceIn(0f, 1f)
        val a = alpha * env * 0.12f
        val r = W * (0.10f + h(i, 3.3f) * 0.10f)
        drawCircle(Color(0xFF161C22).copyA(a), r, Offset(sx, sy))
        drawCircle(Color(0xFF161C22).copyA(a * 0.7f), r * 0.7f, Offset(sx + r * 0.5f, sy - r * 0.2f))
    }

    // 5) Pétalos de cerezo girando al caer (el ancho aparente oscila con la rotación → finge el giro).
    for (i in 0 until 18) {
        val sp = 0.6f + h(i, 4.1f) * 0.5f
        val prog = (phase * loopSpeed(3f * sp) + h(i, 2.3f)) % 1f
        val baseX = h(i, 12.99f) * W
        val sway = sin((prog * 2.5f + h(i, 1.7f)) * 6.28318f) * W * 0.06f
        val px = baseX + sway + prog * W * 0.06f
        val py = -H * 0.03f + prog * H * 1.06f
        val rot = (prog * 6f + h(i, 5.5f)) * 6.28318f
        val pr = W * (0.008f + h(i, 7.7f) * 0.005f)
        val a = alpha * (0.40f + h(i, 5.9f) * 0.45f)
        val pcol = if (h(i, 2.7f) > 0.5f) Color(0xFFFAD2DE) else Color(0xFFE7B7C6)
        val flip = kotlin.math.abs(cos(rot))
        drawOval(pcol.copyA(a), topLeft = Offset(px - pr * flip, py - pr * 1.4f), size = Size(pr * 2f * flip + 1f, pr * 2.8f))
    }

    // 6) Shurikens lejanos girando (siluetas tenues) — ambientación ninja (decorativo, sin mecánica).
    // Velocidad de giro ENTERA (×1 y ×2 vueltas por vuelta de phase) → rotación continua en el salto 1→0.
    drawShurikenSilhouette(Offset(W * 0.30f, H * 0.20f), W * 0.030f, phase * 6.28318f * 1f, alpha * 0.5f)
    drawShurikenSilhouette(Offset(W * 0.62f, H * 0.30f), W * 0.022f, -phase * 6.28318f * 2.0f + 1f, alpha * 0.4f)
}

/**
 * Un tallo de bambú silueteado que sube desde el suelo ([baseX], H) hasta [topFrac] del alto, con NUDOS
 * y hojas lanceoladas, meciéndose con [sway]. Líneas + óvalos (sin Path) → 0 allocs/frame.
 */
private fun DrawScope.drawBamboo(baseX: Float, topFrac: Float, sway: Float, alpha: Float, seed: Float, leafSide: Float) {
    val W = size.width; val H = size.height
    val stalk = Color(0xFF12231A); val stalkLit = Color(0xFF24412C); val leaf = Color(0xFF1C3A24)
    val topY = H * (1f - topFrac)
    val segs = 7
    val width = W * 0.018f
    var prevX = baseX; var prevY = H
    for (s in 1..segs) {
        val tt = s / segs.toFloat()
        val y = H - (H - topY) * tt
        val bend = sin(sway * 6.28318f + seed + tt * 1.5f) * W * 0.015f * tt
        val x = baseX + bend
        drawLine(stalk.copyA(alpha), Offset(prevX, prevY), Offset(x, y), strokeWidth = width * (1.1f - tt * 0.4f), cap = StrokeCap.Round)
        drawLine(stalkLit.copyA(alpha * 0.6f), Offset(prevX, prevY), Offset(x, y), strokeWidth = width * 0.3f, cap = StrokeCap.Round)
        drawLine(Color(0xFF0A140E).copyA(alpha), Offset(x - width * 0.6f, y), Offset(x + width * 0.6f, y), strokeWidth = width * 0.25f)
        if (s >= 3 && s % 2 == 1) {
            val lw = W * 0.05f; val lh = W * 0.014f
            val lx = x + leafSide * lw * 0.5f; val ly = y - lh * 0.3f
            drawOval(leaf.copyA(alpha * 0.9f), topLeft = Offset(lx - lw * 0.5f, ly - lh * 0.5f), size = Size(lw, lh))
        }
        prevX = x; prevY = y
    }
}

/** Silueta de shuriken girando: 4 puntas (líneas cruzadas rotadas) + cubo central con su agujero. */
private fun DrawScope.drawShurikenSilhouette(c: Offset, r: Float, rot: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val col = Color(0xFF0C0F14)
    for (k in 0 until 4) {
        val a = rot + k * (6.28318f / 4f)
        drawLine(col.copyA(alpha * 0.8f), c, c + Offset(cos(a) * r, sin(a) * r), strokeWidth = r * 0.5f, cap = StrokeCap.Round)
    }
    drawCircle(col.copyA(alpha * 0.9f), r * 0.35f, c)
    drawCircle(Color(0xFF1A1F28).copyA(alpha * 0.6f), r * 0.12f, c)
}

/**
 * Mansión Espectral (GHOST): cielo morado con LUNA enfermiza y su halo, una MANSIÓN distante silueteada
 * cuyas VENTANAS PARPADEAN on/off (eco directo del bloque poseído que parpadea visible/invisible),
 * FANTASMAS a la deriva (sesgados a los bordes) y MURCIÉLAGOS aleteando por el cielo. Mansión rellenada
 * por columnas de drawLine (sin Path) + posiciones deterministas por hash → 0 asignaciones por frame.
 */
private fun DrawScope.drawGhostSceneA(b: SceneBrushes, phase: Float, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }

    // 1) Cielo espectral (cacheado).
    b.nebula?.let { drawRect(brush = it) }

    // 2) Luna enfermiza con halo (cacheado) + cráteres tenues.
    val moon = Offset(W * 0.78f, H * 0.13f)
    b.glow?.let {
        val breathe = 0.85f + 0.15f * (0.5f + 0.5f * sin(shimmer * 6.28318f))
        drawCircle(brush = it, radius = W * 0.30f, center = moon, alpha = breathe)
    }
    drawCircle(Color(0xFFE8E0F0).copyA(alpha * 0.85f), W * 0.07f, moon)
    drawCircle(Color(0xFFC8BEDC).copyA(alpha * 0.40f), W * 0.016f, moon + Offset(-W * 0.016f, -W * 0.012f))
    drawCircle(Color(0xFFC8BEDC).copyA(alpha * 0.32f), W * 0.012f, moon + Offset(W * 0.018f, W * 0.016f))

    // 3) Mansión distante con ventanas que parpadean.
    drawHauntedMansion(phase, alpha)

    // 4) Fantasmas a la deriva (sesgados a los bordes para no quedar tras el tablero).
    for (i in 0 until 4) {
        val drift = (phase * loopSpeed(0.3f + h(i, 4.4f) * 0.2f) + h(i, 6.6f)) % 1f
        val edge = if (i % 2 == 0) 0.05f + h(i, 9.1f) * 0.10f else 0.85f + h(i, 9.1f) * 0.10f
        val gx = edge * W + sin((drift + h(i, 1.3f)) * 6.28318f) * W * 0.03f
        val gy = H * (0.30f + h(i, 8.1f) * 0.30f) - sin(drift * 3.14159f) * H * 0.06f
        val env = sin(drift * 3.14159f).coerceIn(0f, 1f)
        drawGhost(Offset(gx, gy), W * (0.038f + h(i, 3.3f) * 0.018f), alpha * env * 0.55f, phase + h(i, 2.2f))
    }

    // 5) Murciélagos aleteando por el cielo.
    for (i in 0 until 5) {
        val cross = (phase * loopSpeed(0.6f + h(i, 5.5f) * 0.5f) + h(i, 7.1f)) % 1f
        val dir = if (i % 2 == 0) 1f else -1f
        val bx = (if (dir > 0) -0.1f + cross * 1.2f else 1.1f - cross * 1.2f) * W
        val by = H * (0.10f + h(i, 8.8f) * 0.30f) + sin(cross * 6.28318f * 2f) * H * 0.03f
        val flap = sin((phase * 30f + i) * 6.28318f)
        drawBat(Offset(bx, by), W * (0.016f + h(i, 2.9f) * 0.01f), flap, alpha * 0.8f)
    }

    // 6) Bruma fría baja (cacheado).
    b.mist?.let { drawRect(brush = it) }
}

/**
 * Mansión distante silueteada en la franja superior (sobre el tablero): cuerpo + 3 espiras rellenados
 * por COLUMNAS verticales de drawLine (sin Path → 0 allocs) y VENTANAS que parpadean on/off con timing
 * determinista — el eco del bloque poseído que aparece y desaparece.
 */
private fun DrawScope.drawHauntedMansion(phase: Float, alpha: Float) {
    val W = size.width; val H = size.height
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }
    val left = 0.26f; val right = 0.74f
    val baseY = H * 0.205f
    val wall = Color(0xFF0B0712)
    fun roofTop(u: Float): Float {
        fun spire(cx: Float, w: Float, hgt: Float): Float { val d = (u - cx) / w; return hgt * kotlin.math.exp(-d * d) }
        var top = 0.165f
        top -= spire(0.18f, 0.10f, 0.050f)   // torre izquierda
        top -= spire(0.50f, 0.12f, 0.085f)   // espira central (la más alta)
        top -= spire(0.82f, 0.10f, 0.050f)   // torre derecha
        top -= 0.006f * (0.5f + 0.5f * sin(u * 40f))   // dientes del tejado
        return top * H
    }
    val cols = 80
    val colW = (right - left) * W / cols
    for (c in 0..cols) {
        val u = c / cols.toFloat()
        val x = (left + (right - left) * u) * W
        drawLine(wall.copyA(alpha * 0.95f), Offset(x, roofTop(u)), Offset(x, baseY), strokeWidth = colW + 1.5f)
    }
    // Niebla que sienta la base de la mansión.
    drawLine(Color(0xFF1A1230).copyA(alpha * 0.5f), Offset(left * W, baseY), Offset(right * W, baseY), strokeWidth = H * 0.02f, cap = StrokeCap.Round)
    // Ventanas que PARPADEAN (cada una a su ritmo: encendida la mayor parte del ciclo, parpadea apagada).
    val winCol = Color(0xFFFFE08A)
    for (tw in 0 until 3) {
        val txc = when (tw) { 0 -> 0.18f; 1 -> 0.50f; else -> 0.82f }
        val wx = (left + (right - left) * txc) * W
        val rows = if (tw == 1) 3 else 2
        for (rIdx in 0 until rows) {
            val wy = baseY - H * (0.018f + rIdx * 0.032f)
            val id = tw * 10 + rIdx
            val rate = loopSpeed(0.5f + h(id, 5.1f) * 1.8f)
            val bcyc = (phase * rate + h(id, 2.0f)) % 1f
            val lit = if (bcyc < 0.6f) 1f else 0.12f
            val warm = 0.82f + 0.18f * sin((phase * 40f + id) * 6.28318f)
            val a = alpha * lit * warm
            val ww = W * 0.012f; val wh = W * 0.018f
            drawCircle(winCol.copyA(a * 0.45f), ww * 1.7f, Offset(wx, wy))   // resplandor
            drawRect(winCol.copyA(a), topLeft = Offset(wx - ww / 2f, wy - wh / 2f), size = Size(ww, wh))
        }
    }
}

/** Un fantasma flotante: cabeza + cuerpo con festón inferior ondulado y dos ojos; bobea con [bob]. */
private fun DrawScope.drawGhost(c: Offset, r: Float, alpha: Float, bob: Float) {
    if (alpha <= 0.01f) return
    val body = Color(0xFFE0DAF2)
    val by = c.y + sin(bob * 6.28318f) * r * 0.15f
    drawCircle(body.copyA(alpha * 0.18f), r * 1.6f, Offset(c.x, by))              // halo
    drawCircle(body.copyA(alpha * 0.6f), r, Offset(c.x, by))                      // cabeza
    drawRect(body.copyA(alpha * 0.6f), topLeft = Offset(c.x - r, by), size = Size(r * 2f, r * 1.1f))   // cuerpo
    for (k in -1..1) drawCircle(body.copyA(alpha * 0.6f), r * 0.4f, Offset(c.x + k * r * 0.66f, by + r * 1.1f))  // festón
    val eo = r * 0.32f
    drawCircle(Color(0xFF2A2440).copyA(alpha * 0.9f), r * 0.16f, Offset(c.x - eo, by - r * 0.1f))
    drawCircle(Color(0xFF2A2440).copyA(alpha * 0.9f), r * 0.16f, Offset(c.x + eo, by - r * 0.1f))
}

/** Un murciélago: cuerpecito + dos alas en V (dos segmentos c/u) cuyas puntas suben/bajan con [flap]. */
private fun DrawScope.drawBat(c: Offset, r: Float, flap: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val col = Color(0xFF0A0610)
    val up = r * (0.5f + 0.4f * flap)
    drawCircle(col.copyA(alpha * 0.9f), r * 0.32f, c)
    val lMid = c + Offset(-r * 0.9f, -up); val lTip = c + Offset(-r * 1.7f, up * 0.4f)
    drawLine(col.copyA(alpha * 0.85f), c, lMid, strokeWidth = r * 0.34f, cap = StrokeCap.Round)
    drawLine(col.copyA(alpha * 0.85f), lMid, lTip, strokeWidth = r * 0.26f, cap = StrokeCap.Round)
    val rMid = c + Offset(r * 0.9f, -up); val rTip = c + Offset(r * 1.7f, up * 0.4f)
    drawLine(col.copyA(alpha * 0.85f), c, rMid, strokeWidth = r * 0.34f, cap = StrokeCap.Round)
    drawLine(col.copyA(alpha * 0.85f), rMid, rTip, strokeWidth = r * 0.26f, cap = StrokeCap.Round)
}

/**
 * Pantano Tóxico (TOXIC): cielo turbio verde, un BARRIL tóxico goteando en una esquina (el mural del
 * bioma) con símbolo de peligro que brilla, brillo del charco subiendo desde abajo, VAPOR verde, GOTEO
 * ácido desde el techo y — el sello — BURBUJAS que suben del charco y REVIENTAN arriba (eco de la
 * infección que se propaga). Burbujas/vapor/gotas deterministas por hash + fills → 0 asignaciones/frame.
 */
private fun DrawScope.drawToxicSceneA(b: SceneBrushes, phase: Float, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }
    val pulse = 0.5f + 0.5f * sin(shimmer * 6.28318f)

    // 1) Cielo tóxico (cacheado).
    b.nebula?.let { drawRect(brush = it) }

    // 2) Barril tóxico goteando en la esquina inferior izquierda.
    drawToxicBarrel(W * 0.12f, H * 0.86f, W * 0.085f, alpha, pulse)

    // 3) Brillo del charco tóxico (cacheado; respira con el pulso).
    b.glow?.let { drawRect(brush = it, alpha = 0.8f + 0.2f * pulse) }

    // 4) Vapor verde ascendente (gas translúcido).
    for (i in 0 until 5) {
        val cyc = (phase * loopSpeed(0.22f + h(i, 4.4f) * 0.14f) + h(i, 6.6f)) % 1f
        val vx = (0.12f + h(i, 9.7f) * 0.78f) * W + sin((cyc + h(i, 1.3f)) * 6.28318f) * W * 0.04f
        val vy = H * (0.92f - cyc * 0.7f)
        val env = sin(cyc * 3.14159f).coerceIn(0f, 1f)
        val r = W * (0.08f + cyc * 0.09f)
        drawCircle(Color(0xFF6CC828).copyA(alpha * env * 0.12f), r, Offset(vx, vy))
    }

    // 5) Burbujas tóxicas que suben del charco y revientan arriba (el sello del bioma).
    for (i in 0 until 22) {
        val sp = 0.7f + h(i, 4.1f) * 0.7f
        val cyc = (phase * loopSpeed(1.4f * sp) + h(i, 2.3f)) % 1f
        val bx = (0.06f + h(i, 12.99f) * 0.88f) * W + sin((cyc * 2f + h(i, 1.7f)) * 6.28318f) * W * 0.02f
        val by = H * (0.98f - cyc * 0.66f)
        val br = W * (0.006f + h(i, 7.7f) * 0.008f) * (0.4f + cyc * 0.6f)
        if (cyc > 0.9f) {
            val pop = (cyc - 0.9f) / 0.1f
            drawCircle(Color(0xFFB6FF6A).copyA(alpha * (1f - pop) * 0.55f), br * (1f + pop * 2.6f), Offset(bx, by))   // reventón
        } else {
            val a = alpha * 0.55f
            drawCircle(Color(0xFF7CD832).copyA(a * 0.30f), br * 1.5f, Offset(bx, by))                          // resplandor
            drawCircle(Color(0xFF8CE83C).copyA(a * 0.55f), br, Offset(bx, by))                                 // cuerpo
            drawCircle(Color(0xFFEFFFC0).copyA(a), br * 0.34f, Offset(bx - br * 0.3f, by - br * 0.3f))          // brillo
        }
    }

    // 6) Goteo ácido desde el borde superior (gotas alargadas cayendo).
    for (i in 0 until 6) {
        val cyc = (phase * 1f + h(i, 3.7f)) % 1f
        val dx = (0.1f + h(i, 9.1f) * 0.8f) * W
        val dy = cyc * H * 0.5f
        val a = alpha * sin(cyc * 3.14159f).coerceIn(0f, 1f) * 0.7f
        drawOval(Color(0xFFB6FF6A).copyA(a), topLeft = Offset(dx - W * 0.006f, dy), size = Size(W * 0.012f, W * 0.022f))
    }

    // 7) Motas tóxicas (chispas verdes que titilan).
    for (i in 0 until 6) {
        val tw = 0.5f + 0.5f * sin((shimmer + h(i, 7.19f)) * 6.28318f)
        if (tw < 0.5f) continue
        val mx = h(i, 12.9898f) * W; val my = (0.15f + h(i, 78.233f) * 0.6f) * H
        val a = alpha * (tw - 0.5f) / 0.5f * 0.8f
        val s = W * 0.008f
        drawLine(Color(0xFFC8FF60).copyA(a), Offset(mx - s, my), Offset(mx + s, my), W * 0.002f, cap = StrokeCap.Round)
        drawLine(Color(0xFFC8FF60).copyA(a), Offset(mx, my - s), Offset(mx, my + s), W * 0.002f, cap = StrokeCap.Round)
    }
}

/**
 * Un barril tóxico: cuerpo cilíndrico rellenado por COLUMNAS de drawLine (sin Path → 0 allocs) con
 * sombreado cilíndrico y aros, un TRIÁNGULO de peligro que brilla con [pulse] y una fuga goteando.
 */
private fun DrawScope.drawToxicBarrel(cx: Float, baseY: Float, halfW: Float, alpha: Float, pulse: Float) {
    val barrelTop = baseY - halfW * 2.4f
    val metal = Color(0xFF2A3A1A); val metalLit = Color(0xFF3E5626)
    val cols = 20
    for (c in 0..cols) {
        val u = c / cols.toFloat()
        val x = cx - halfW + u * 2f * halfW
        val shade = 1f - kotlin.math.abs(u - 0.5f) * 0.8f   // claro en el centro (cilindro)
        drawLine(metal.copyA(alpha * (0.7f + 0.3f * shade)), Offset(x, barrelTop), Offset(x, baseY), strokeWidth = (2f * halfW / cols) + 1f)
    }
    // Aros del barril.
    drawLine(metalLit.copyA(alpha * 0.8f), Offset(cx - halfW, barrelTop + halfW * 0.5f), Offset(cx + halfW, barrelTop + halfW * 0.5f), strokeWidth = halfW * 0.12f)
    drawLine(metalLit.copyA(alpha * 0.8f), Offset(cx - halfW, baseY - halfW * 0.5f), Offset(cx + halfW, baseY - halfW * 0.5f), strokeWidth = halfW * 0.12f)
    // Triángulo de peligro brillando.
    val sy = barrelTop + halfW * 1.2f; val sr = halfW * 0.5f
    val glow = Color(0xFFC8FF60).copyA(alpha * (0.6f + 0.4f * pulse))
    val top = Offset(cx, sy - sr); val bl = Offset(cx - sr * 0.9f, sy + sr * 0.6f); val brp = Offset(cx + sr * 0.9f, sy + sr * 0.6f)
    drawLine(glow, top, bl, strokeWidth = halfW * 0.08f, cap = StrokeCap.Round)
    drawLine(glow, bl, brp, strokeWidth = halfW * 0.08f, cap = StrokeCap.Round)
    drawLine(glow, brp, top, strokeWidth = halfW * 0.08f, cap = StrokeCap.Round)
    // Fuga goteando del borde inferior.
    drawCircle(Color(0xFF9CFF50).copyA(alpha * (0.5f + 0.5f * pulse)), halfW * 0.18f, Offset(cx + halfW * 0.6f, baseY))
}

/**
 * Cocina Voraz (GLUTTON): cocina cálida con UTENSILIOS colgando del techo que se mecen, una BOCA glotona
 * acechando en la penumbra que se abre y cierra (eco de la mordida que inutiliza un rincón), resplandor
 * del FOGÓN con LLAMAS en las esquinas, VAPOR de las ollas subiendo y migas/chispas cálidas flotando.
 * Utensilios/boca/llamas como líneas+óvalos (sin Path) + deterministas por hash → 0 asignaciones/frame.
 */
private fun DrawScope.drawGluttonSceneA(b: SceneBrushes, phase: Float, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }
    val flick = 0.5f + 0.5f * sin(shimmer * 6.28318f)

    // 1) Ambiente de cocina (cacheado).
    b.nebula?.let { drawRect(brush = it) }

    // 2) Utensilios colgando del techo que se mecen.
    drawUtensil(W * 0.06f, phase, alpha, 0)   // cucharón
    drawUtensil(W * 0.13f, phase, alpha, 1)   // tenedor
    drawUtensil(W * 0.90f, phase, alpha, 2)   // cuchillo
    drawUtensil(W * 0.95f, phase, alpha, 3)   // batidor

    // 3) Boca glotona acechando en la penumbra (se abre y cierra) — eco de la mordida.
    run {
        // Deriva y apertura con multiplicadores ENTEROS de phase (×1 y ×4) → bucle perfecto sin tirón.
        val mx = W * (0.22f + sin(phase * 6.28318f) * 0.04f)
        val open = 0.35f + 0.35f * (0.5f + 0.5f * sin(phase * 4f * 6.28318f))
        drawGluttonMaw(Offset(mx, H * 0.24f), W * 0.10f, open, alpha * 0.45f)
    }

    // 4) Resplandor del fogón (cacheado; respira con el titileo).
    b.glow?.let { drawRect(brush = it, alpha = 0.8f + 0.2f * flick) }

    // 5) Llamas del fogón en las esquinas inferiores.
    drawHearthFlames(W * 0.14f, H, W * 0.10f, phase, alpha)
    drawHearthFlames(W * 0.86f, H, W * 0.09f, phase, alpha)

    // 6) Vapor de las ollas subiendo (penachos blancos ondulantes).
    for (i in 0 until 6) {
        val cyc = (phase * loopSpeed(0.3f + h(i, 4.4f) * 0.2f) + h(i, 6.6f)) % 1f
        val sx = (0.18f + h(i, 9.7f) * 0.64f) * W + sin((cyc * 2f + h(i, 1.3f)) * 6.28318f) * W * 0.04f
        val sy = H * (0.86f - cyc * 0.78f)
        val env = sin(cyc * 3.14159f).coerceIn(0f, 1f)
        val r = W * (0.05f + cyc * 0.10f)
        drawCircle(Color(0xFFEDE2D2).copyA(alpha * env * 0.14f), r, Offset(sx, sy))
    }

    // 7) Migas/chispas cálidas flotando.
    for (i in 0 until 10) {
        val cyc = (phase * loopSpeed(0.5f + h(i, 5.3f) * 0.6f) + h(i, 2.1f)) % 1f
        val cx = (0.08f + h(i, 9.1f) * 0.84f) * W + sin((cyc + h(i, 1.7f)) * 6.28318f) * W * 0.03f
        val cy = H * (0.94f - cyc * 0.85f)
        val env = sin(cyc * 3.14159f).coerceIn(0f, 1f)
        val r = W * (0.004f + h(i, 7.7f) * 0.004f)
        drawCircle(Color(0xFFFFD090).copyA(alpha * env * 0.6f), r, Offset(cx, cy))
    }
}

/** Un utensilio colgando del techo (mango + cabeza según [kind]) que se balancea con [sway]. */
private fun DrawScope.drawUtensil(x: Float, sway: Float, alpha: Float, kind: Int) {
    val W = size.width; val H = size.height
    val col = Color(0xFF1A1410); val colLit = Color(0xFF3A3026)
    val hangLen = H * (0.10f + (kind % 2) * 0.03f)
    val swing = sin(sway * 6.28318f + kind * 1.6f) * 0.06f   // balanceo en radianes
    val ex = x + sin(swing) * hangLen
    val ey = cos(swing) * hangLen
    drawLine(col.copyA(alpha * 0.9f), Offset(x, 0f), Offset(ex, ey), strokeWidth = W * 0.010f, cap = StrokeCap.Round)
    drawLine(colLit.copyA(alpha * 0.5f), Offset(x, 0f), Offset(ex, ey), strokeWidth = W * 0.003f, cap = StrokeCap.Round)
    when (kind) {
        0 -> {   // cucharón: cuenco con hueco
            drawCircle(col.copyA(alpha * 0.9f), W * 0.030f, Offset(ex, ey))
            drawCircle(Color(0xFF2E2218).copyA(alpha * 0.7f), W * 0.020f, Offset(ex, ey))
        }
        1 -> {   // tenedor: 3 púas
            for (p in -1..1) drawLine(col.copyA(alpha * 0.9f), Offset(ex + p * W * 0.012f, ey), Offset(ex + p * W * 0.012f, ey + W * 0.04f), strokeWidth = W * 0.006f, cap = StrokeCap.Round)
        }
        2 -> {   // cuchillo: hoja afilada (relleno por columnas)
            val bw = W * 0.022f; val bl = W * 0.06f; val cols2 = 8
            for (c in 0..cols2) {
                val u = c / cols2.toFloat()
                drawLine(col.copyA(alpha * 0.85f), Offset(ex - bw / 2f + u * bw, ey), Offset(ex - bw / 2f + u * bw, ey + bl * (1f - u)), strokeWidth = (bw / cols2) + 1f)
            }
        }
        else -> { // batidor: alambres curvos
            for (p in -1..1) drawLine(col.copyA(alpha * 0.8f), Offset(ex, ey), Offset(ex + p * W * 0.016f, ey + W * 0.05f), strokeWidth = W * 0.004f, cap = StrokeCap.Round)
        }
    }
}

/**
 * La boca glotona acechando: maw oscura (óvalo cuya altura sube con [open]) con DIENTES en zigzag arriba
 * y abajo (polilínea de drawLine, sin Path) y dos ojos brillando encima. 0 asignaciones/frame.
 */
private fun DrawScope.drawGluttonMaw(c: Offset, r: Float, open: Float, alpha: Float) {
    if (alpha <= 0.01f) return
    val maw = Color(0xFF2A0E08); val tooth = Color(0xFFF2E6D0)
    val mh = r * open
    drawOval(maw.copyA(alpha * 0.8f), topLeft = Offset(c.x - r, c.y - mh * 0.5f), size = Size(r * 2f, mh))
    val n = 7; val toothH = mh * 0.32f
    // Dientes superiores (zigzag que baja hacia el centro de la boca).
    var px = c.x - r; var py = c.y - mh * 0.5f
    for (k in 0..n * 2) {
        val x = c.x - r + (k / (n * 2f)) * 2f * r
        val y = if (k % 2 == 0) c.y - mh * 0.5f else c.y - mh * 0.5f + toothH
        drawLine(tooth.copyA(alpha), Offset(px, py), Offset(x, y), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
        px = x; py = y
    }
    // Dientes inferiores (zigzag que sube hacia el centro).
    px = c.x - r; py = c.y + mh * 0.5f
    for (k in 0..n * 2) {
        val x = c.x - r + (k / (n * 2f)) * 2f * r
        val y = if (k % 2 == 0) c.y + mh * 0.5f else c.y + mh * 0.5f - toothH
        drawLine(tooth.copyA(alpha), Offset(px, py), Offset(x, y), strokeWidth = r * 0.05f, cap = StrokeCap.Round)
        px = x; py = y
    }
    // Ojos hambrientos encima de la boca.
    val eo = r * 0.55f; val eyY = c.y - mh * 0.5f - r * 0.5f
    drawCircle(Color(0xFFFFC24A).copyA(alpha), r * 0.14f, Offset(c.x - eo, eyY))
    drawCircle(Color(0xFFFFC24A).copyA(alpha), r * 0.14f, Offset(c.x + eo, eyY))
    drawCircle(Color(0xFF1A0A04).copyA(alpha), r * 0.06f, Offset(c.x - eo, eyY))
    drawCircle(Color(0xFF1A0A04).copyA(alpha), r * 0.06f, Offset(c.x + eo, eyY))
}

/** Llamas del fogón: dos triángulos apilados (naranja exterior + núcleo amarillo) rellenados por
 *  COLUMNAS de drawLine con titileo por columna (sin Path → 0 allocs/frame). */
private fun DrawScope.drawHearthFlames(cx: Float, baseY: Float, halfW: Float, phase: Float, alpha: Float) {
    val cols = 12
    val flick = 0.6f + 0.4f * sin((phase * 16f) * 6.28318f)
    val fh = halfW * 1.8f * (0.8f + 0.4f * flick)
    for (c in 0..cols) {
        val u = c / cols.toFloat()
        val x = cx - halfW + u * 2f * halfW
        val d = kotlin.math.abs(u - 0.5f) * 2f
        val colLen = fh * (1f - d) * (0.85f + 0.3f * sin((phase * 18f + c) * 6.28318f))
        if (colLen <= 0f) continue
        drawLine(Color(0xFFFF7A20).copyA(alpha * 0.6f), Offset(x, baseY), Offset(x, baseY - colLen), strokeWidth = (2f * halfW / cols) + 1f)
    }
    val cols2 = 6; val chw = halfW * 0.5f
    for (c in 0..cols2) {
        val u = c / cols2.toFloat()
        val x = cx - chw + u * 2f * chw
        val d = kotlin.math.abs(u - 0.5f) * 2f
        drawLine(Color(0xFFFFD060).copyA(alpha * 0.75f), Offset(x, baseY), Offset(x, baseY - fh * 0.7f * (1f - d)), strokeWidth = (2f * chw / cols2) + 1f)
    }
}

/**
 * Trono Real (KING): salón regio con dos COLUMNAS estriadas enmarcando los bordes, el TRONO al fondo
 * (respaldo alto con corona que brilla), ESTANDARTES colgando que ondean con su emblema dorado, un
 * CANDELABRO con velas que titilan, el resplandor de la alfombra y POLVO dorado flotando. Columnas/trono/
 * banderas rellenadas por columnas o filas de drawLine (sin Path) + deterministas → 0 asignaciones/frame.
 */
private fun DrawScope.drawKingSceneA(b: SceneBrushes, phase: Float, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    fun h(i: Int, k: Float): Float {
        val v = kotlin.math.sin(i * k) * 43758.547f
        return v - kotlin.math.floor(v)
    }
    val candle = 0.5f + 0.5f * sin(shimmer * 6.28318f)

    // 1) Salón regio (cacheado).
    b.nebula?.let { drawRect(brush = it) }

    // 2) Columnas estriadas en los bordes.
    drawColumn(W * 0.07f, W * 0.05f, alpha)
    drawColumn(W * 0.93f, W * 0.05f, alpha)

    // 3) Trono al fondo (respaldo alto centrado + corona que asoma sobre el tablero).
    drawThrone(W * 0.5f, alpha, candle)

    // 4) Estandartes colgando del techo (ondean con phase, emblema dorado).
    drawBanner(W * 0.26f, alpha, phase, Color(0xFF8E1B2E), 0)
    drawBanner(W * 0.74f, alpha, phase, Color(0xFF3A2E7A), 2)

    // 5) Candelabro colgante con velas titilando.
    drawChandelier(W * 0.5f, H * 0.11f, W * 0.11f, alpha, phase)

    // 6) Resplandor de la alfombra (cacheado; respira con las velas).
    b.glow?.let { drawRect(brush = it, alpha = 0.82f + 0.18f * candle) }

    // 7) Polvo dorado flotando (chispas con destello en cruz para las más brillantes).
    for (i in 0 until 14) {
        val cyc = (phase * loopSpeed(0.3f + h(i, 5.3f) * 0.4f) + h(i, 2.1f)) % 1f
        val gx = (0.08f + h(i, 9.1f) * 0.84f) * W + sin((cyc + h(i, 1.7f)) * 6.28318f) * W * 0.03f
        val gy = H * (0.92f - cyc * 0.8f)
        val env = sin(cyc * 3.14159f).coerceIn(0f, 1f)
        val tw = 0.5f + 0.5f * sin((phase * 10f + i) * 6.28318f)
        val a = alpha * env * tw * 0.7f
        val r = W * (0.0035f + h(i, 7.7f) * 0.004f)
        drawCircle(Color(0xFFFFE08A).copyA(a), r, Offset(gx, gy))
        if (h(i, 2.7f) > 0.7f) {
            val s = r * 3f
            drawLine(Color(0xFFFFF0C0).copyA(a * 0.7f), Offset(gx - s, gy), Offset(gx + s, gy), r * 0.6f, cap = StrokeCap.Round)
            drawLine(Color(0xFFFFF0C0).copyA(a * 0.7f), Offset(gx, gy - s), Offset(gx, gy + s), r * 0.6f, cap = StrokeCap.Round)
        }
    }
}

/** Una columna estriada: fuste rellenado por COLUMNAS de drawLine con sombreado cilíndrico, estrías y
 *  capitel/base dorados (sin Path → 0 allocs/frame). */
private fun DrawScope.drawColumn(cx: Float, halfW: Float, alpha: Float) {
    val H = size.height; val W = size.width
    val stone = Color(0xFF2A2238); val stoneLit = Color(0xFF44395E); val gold = Color(0xFFD4A23A)
    val cols = 14
    for (c in 0..cols) {
        val u = c / cols.toFloat()
        val x = cx - halfW + u * 2f * halfW
        val shade = 1f - kotlin.math.abs(u - 0.5f) * 0.7f
        drawLine(stone.copyA(alpha * (0.7f + 0.3f * shade)), Offset(x, 0f), Offset(x, H), strokeWidth = (2f * halfW / cols) + 1f)
    }
    for (f in -2..2) {
        val fx = cx + f * halfW * 0.35f
        drawLine(stoneLit.copyA(alpha * 0.4f), Offset(fx, 0f), Offset(fx, H), strokeWidth = W * 0.004f)
    }
    drawLine(gold.copyA(alpha * 0.8f), Offset(cx - halfW * 1.2f, H * 0.10f), Offset(cx + halfW * 1.2f, H * 0.10f), strokeWidth = H * 0.018f)
    drawLine(gold.copyA(alpha * 0.6f), Offset(cx - halfW * 1.2f, H * 0.90f), Offset(cx + halfW * 1.2f, H * 0.90f), strokeWidth = H * 0.016f)
}

/** El trono al fondo: respaldo alto centrado (relleno por columnas) con finiales dorados y una corona
 *  en lo alto (la parte que asoma por encima del tablero). */
private fun DrawScope.drawThrone(cx: Float, alpha: Float, candle: Float) {
    val H = size.height; val W = size.width
    val wood = Color(0xFF241018); val gold = Color(0xFFD4A23A)
    val backTop = H * 0.16f; val backBot = H * 0.52f; val hw = W * 0.06f
    val cols = 10
    for (c in 0..cols) {
        val u = c / cols.toFloat()
        val x = cx - hw + u * 2f * hw
        drawLine(wood.copyA(alpha * 0.85f), Offset(x, backTop), Offset(x, backBot), strokeWidth = (2f * hw / cols) + 1f)
    }
    drawCircle(gold.copyA(alpha * 0.7f), W * 0.012f, Offset(cx - hw, backTop))
    drawCircle(gold.copyA(alpha * 0.7f), W * 0.012f, Offset(cx + hw, backTop))
    drawCrown(Offset(cx, backTop - H * 0.02f), W * 0.05f, alpha * (0.7f + 0.3f * candle))
}

/** Una corona: banda + 3 puntas en V con gemas en las puntas + gema central. Sin arrays → 0 allocs. */
private fun DrawScope.drawCrown(c: Offset, r: Float, alpha: Float) {
    val gold = Color(0xFFFFD24A); val gem = Color(0xFFE0506A)
    val bandH = r * 0.4f
    drawLine(gold.copyA(alpha), Offset(c.x - r, c.y + bandH), Offset(c.x + r, c.y + bandH), strokeWidth = bandH, cap = StrokeCap.Round)
    for (k in -1..1) {
        val tx = c.x + k * r * 0.7f
        val tipY = c.y - r * 0.6f
        drawLine(gold.copyA(alpha), Offset(tx - r * 0.22f, c.y + bandH * 0.5f), Offset(tx, tipY), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
        drawLine(gold.copyA(alpha), Offset(tx + r * 0.22f, c.y + bandH * 0.5f), Offset(tx, tipY), strokeWidth = r * 0.12f, cap = StrokeCap.Round)
        drawCircle(gem.copyA(alpha), r * 0.10f, Offset(tx, tipY))
    }
    drawCircle(gem.copyA(alpha), r * 0.13f, Offset(c.x, c.y + bandH))
}

/** Un estandarte colgando del techo: tela rellenada por FILAS de drawLine (ondea con [sway], se afila
 *  hacia la punta) con un rombo dorado de emblema. Sin Path → 0 allocs/frame. */
private fun DrawScope.drawBanner(cx: Float, alpha: Float, sway: Float, col: Color, idx: Int) {
    val H = size.height; val W = size.width
    val len = H * (0.16f + (idx % 2) * 0.03f); val bw = W * 0.05f
    val cols = 9
    for (s in 0..cols) {
        val tt = s / cols.toFloat()
        val y = len * tt
        val wave = sin((sway * 2f + idx + tt * 2.5f) * 6.28318f) * W * 0.012f * tt
        val w = bw * (if (tt > 0.80f) (1f - (tt - 0.80f) / 0.20f * 0.9f) else 1f)
        drawLine(col.copyA(alpha * 0.85f), Offset(cx - w / 2f + wave, y), Offset(cx + w / 2f + wave, y), strokeWidth = (len / cols) + 1f)
    }
    val emY = len * 0.42f
    val emX = cx + sin((sway * 2f + idx + 0.42f * 2.5f) * 6.28318f) * W * 0.012f * 0.42f
    val gold = Color(0xFFFFD24A); val er = W * 0.013f
    drawLine(gold.copyA(alpha), Offset(emX, emY - er), Offset(emX + er, emY), strokeWidth = er * 0.45f, cap = StrokeCap.Round)
    drawLine(gold.copyA(alpha), Offset(emX + er, emY), Offset(emX, emY + er), strokeWidth = er * 0.45f, cap = StrokeCap.Round)
    drawLine(gold.copyA(alpha), Offset(emX, emY + er), Offset(emX - er, emY), strokeWidth = er * 0.45f, cap = StrokeCap.Round)
    drawLine(gold.copyA(alpha), Offset(emX - er, emY), Offset(emX, emY - er), strokeWidth = er * 0.45f, cap = StrokeCap.Round)
    drawCircle(gold.copyA(alpha), er * 0.4f, Offset(emX, emY))
}

/** Un candelabro colgante: cadena + barra con velas cuyas LLAMAS titilan con [phase]. 0 allocs/frame. */
private fun DrawScope.drawChandelier(cx: Float, cy: Float, halfW: Float, alpha: Float, phase: Float) {
    val W = size.width
    val gold = Color(0xFFC8962E); val goldLit = Color(0xFFFFD24A)
    drawLine(gold.copyA(alpha * 0.7f), Offset(cx, 0f), Offset(cx, cy), strokeWidth = W * 0.004f)
    drawLine(gold.copyA(alpha * 0.85f), Offset(cx - halfW, cy), Offset(cx + halfW, cy), strokeWidth = W * 0.008f)
    val n = 5
    for (k in 0 until n) {
        val u = (k + 0.5f) / n
        val vx = cx - halfW + u * 2f * halfW
        drawLine(goldLit.copyA(alpha * 0.8f), Offset(vx, cy), Offset(vx, cy - W * 0.02f), strokeWidth = W * 0.006f)
        val flick = 0.7f + 0.3f * sin((phase * 22f + k * 1.3f) * 6.28318f)
        val fy = cy - W * 0.02f; val fh = W * 0.022f * flick
        drawCircle(Color(0xFFFF9A30).copyA(alpha * 0.5f), W * 0.012f * flick, Offset(vx, fy - fh * 0.3f))
        drawOval(Color(0xFFFFE08A).copyA(alpha * 0.9f), topLeft = Offset(vx - W * 0.004f, fy - fh), size = Size(W * 0.008f, fh * 1.2f))
        drawCircle(Color.White.copyA(alpha * 0.7f), W * 0.0025f, Offset(vx, fy - fh * 0.2f))
    }
    drawCircle(goldLit.copyA(alpha * 0.8f), W * 0.010f, Offset(cx, cy + W * 0.015f))
}

/** Escena base para los biomas aún sin escena propia: un resplandor de acento que respira.
 *  Radio FIJO (brush cacheado); la respiración va por alpha en vez de por radio → 0 allocs/frame. */
private fun DrawScope.drawGenericGlowA(b: SceneBrushes, shimmer: Float, alpha: Float) {
    val W = size.width; val H = size.height
    b.glow?.let {
        val breathe = 0.82f + 0.18f * (0.5f + 0.5f * sin(shimmer * 6.28318f))
        drawCircle(brush = it, radius = W * 0.55f, center = Offset(W * 0.5f, H * 0.7f), alpha = breathe)
    }
}

private fun DrawScope.drawZ(center: Offset, s: Float, color: Color) {
    val h = s; val w = s * 0.8f
    val tl = Offset(center.x - w / 2, center.y - h / 2)
    val tr = Offset(center.x + w / 2, center.y - h / 2)
    val bl = Offset(center.x - w / 2, center.y + h / 2)
    val br = Offset(center.x + w / 2, center.y + h / 2)
    val sw = s * 0.13f
    drawLine(color, tl, tr, sw)
    drawLine(color, tr, bl, sw)
    drawLine(color, bl, br, sw)
}

private fun Color.copyA(a: Float) = copy(alpha = alpha * a)

// ----------------------------------------------------------------------------------------------
// Geometría
// ----------------------------------------------------------------------------------------------

/**
 * Una grieta de la lámina de cristal: polilínea en coords normalizadas (0..1 de ancho/alto).
 * @param appearAt valor de t a partir del cual la grieta empieza a dibujarse (y a crecer).
 * @param width    grosor en fracción de ancho.
 */
private data class CrackLine(val points: List<Offset>, val appearAt: Float, val width: Float)

private data class Fog(val x: Float, val r: Float, val phase: Float)
private data class Star(val x: Float, val y: Float, val r: Float, val phase: Float)
private data class Zzz(val x: Float, val size: Float, val speed: Float, val phase: Float)
private data class SleepGeom(val fog: List<Fog>, val stars: List<Star>, val zs: List<Zzz>)

/**
 * Red de grietas para la lámina de cristal a pantalla completa. Pocas fracturas principales que
 * cruzan toda la pantalla (aparecen temprano) + ramas más cortas que se suman conforme avanza t,
 * de modo que el único bloque de cristal se va resquebrajando entero nv5(t0)→nv10.
 */
private fun buildCrystalGeom(): List<CrackLine> {
    val rng = kotlin.random.Random(9051)
    fun jitterPath(x0: Float, y0: Float, x1: Float, y1: Float, segs: Int, j: Float): List<Offset> =
        (0..segs).map { i ->
            val u = i / segs.toFloat()
            val jx = if (i == 0 || i == segs) 0f else (rng.nextFloat() - 0.5f) * j
            val jy = if (i == 0 || i == segs) 0f else (rng.nextFloat() - 0.5f) * j
            Offset((x0 + (x1 - x0) * u + jx).coerceIn(0f, 1f), (y0 + (y1 - y0) * u + jy).coerceIn(0f, 1f))
        }

    val list = mutableListOf<CrackLine>()
    // Fracturas principales que atraviesan toda la lámina (de arriba a abajo) — aparecen primero.
    list += CrackLine(jitterPath(0.05f, 0.18f, 0.95f, 0.40f, 5, 0.06f), appearAt = 0.04f, width = 0.010f)
    list += CrackLine(jitterPath(0.20f, 0.02f, 0.55f, 0.98f, 7, 0.06f), appearAt = 0.12f, width = 0.009f)
    list += CrackLine(jitterPath(0.98f, 0.10f, 0.30f, 0.92f, 7, 0.06f), appearAt = 0.20f, width = 0.009f)
    // Dos fracturas que cruzan la MITAD INFERIOR (para que abajo también se resquebraje).
    list += CrackLine(jitterPath(0.04f, 0.70f, 0.92f, 0.88f, 5, 0.06f), appearAt = 0.16f, width = 0.009f)
    list += CrackLine(jitterPath(0.50f, 0.55f, 0.78f, 0.99f, 5, 0.05f), appearAt = 0.28f, width = 0.008f)
    // Ramas más cortas que se suman poco a poco hasta dejar todo agrietado.
    for (i in 0 until 10) {
        val sx = rng.nextFloat(); val sy = rng.nextFloat()
        val ang = rng.nextFloat() * 6.28318f
        val len = 0.18f + rng.nextFloat() * 0.22f
        val ex = (sx + kotlin.math.cos(ang) * len).coerceIn(0f, 1f)
        val ey = (sy + kotlin.math.sin(ang) * len).coerceIn(0f, 1f)
        list += CrackLine(jitterPath(sx, sy, ex, ey, 3, 0.04f), appearAt = 0.30f + i * 0.065f, width = 0.006f)
    }
    return list
}

private fun buildSleepGeom(): SleepGeom {
    val rng = kotlin.random.Random(4242)
    val fog = List(4) { Fog(0.15f + it * 0.24f + rng.nextFloat() * 0.05f, 0.20f + rng.nextFloat() * 0.12f, rng.nextFloat()) }
    val stars = List(16) { Star(rng.nextFloat(), rng.nextFloat() * 0.6f, 0.004f + rng.nextFloat() * 0.006f, rng.nextFloat()) }
    // speed ENTERA (1 o 2 ciclos por vuelta de phase) → las "Z" hacen bucle perfecto sin tirón.
    val zs = List(5) { Zzz(0.2f + rng.nextFloat() * 0.6f, 0.03f + rng.nextFloat() * 0.025f, 1f + kotlin.math.floor(rng.nextFloat() * 2f), rng.nextFloat()) }
    return SleepGeom(fog, stars, zs)
}

// ----------------------------------------------------------------------------------------------
// Paletas (14 biomas)
// ----------------------------------------------------------------------------------------------

private enum class MoteShape { CIRCLE, DIAMOND, SPARK }

private data class BiomePalette(val top: Color, val bottom: Color, val accent: Color, val shape: MoteShape)

private fun biomePalette(biome: BiomeType): BiomePalette = when (biome) {
    BiomeType.NONE     -> BiomePalette(Color(0x00000000), Color(0x00000000), Color(0x00000000), MoteShape.CIRCLE)
    BiomeType.CRYSTAL  -> BiomePalette(Color(0x3340C4FF), Color(0x55121A40), Color(0xCCBFEFFF), MoteShape.DIAMOND)
    BiomeType.SLEEP    -> BiomePalette(Color(0x33442C7A), Color(0x55101030), Color(0xCCC3B6FF), MoteShape.CIRCLE)
    BiomeType.FEAR     -> BiomePalette(Color(0x33203050), Color(0x66060810), Color(0xAA9FB0D0), MoteShape.CIRCLE)
    BiomeType.GARDEN   -> BiomePalette(Color(0x3340A050), Color(0x55102A18), Color(0xCCBFFFA0), MoteShape.CIRCLE)
    BiomeType.STORM    -> BiomePalette(Color(0x33304060), Color(0x66101524), Color(0xCCFFF59A), MoteShape.SPARK)
    BiomeType.VOLCANO  -> BiomePalette(Color(0x44C04020), Color(0x66200804), Color(0xDDFFB060), MoteShape.SPARK)
    BiomeType.NINJA    -> BiomePalette(Color(0x33202830), Color(0x66080A0C), Color(0xAAE0C0C0), MoteShape.CIRCLE)
    BiomeType.GLACIER  -> BiomePalette(Color(0x3380D0FF), Color(0x55102038), Color(0xDDDDF4FF), MoteShape.DIAMOND)
    BiomeType.GHOST    -> BiomePalette(Color(0x33503870), Color(0x66100818), Color(0xBBD0C0FF), MoteShape.SPARK)
    BiomeType.GLUTTON  -> BiomePalette(Color(0x33C08040), Color(0x55241404), Color(0xCCFFD090), MoteShape.CIRCLE)
    BiomeType.TOXIC    -> BiomePalette(Color(0x4060C030), Color(0x66102004), Color(0xCCC8FF60), MoteShape.SPARK)
    BiomeType.STAR     -> BiomePalette(Color(0x33203070), Color(0x66060818), Color(0xDDFFF0A0), MoteShape.SPARK)
    BiomeType.KING     -> BiomePalette(Color(0x44C0A030), Color(0x66201804), Color(0xEEFFE08A), MoteShape.SPARK)
    BiomeType.GALACTIC -> BiomePalette(Color(0x33402080), Color(0x66080418), Color(0xCCC0A0FF), MoteShape.SPARK)
}
