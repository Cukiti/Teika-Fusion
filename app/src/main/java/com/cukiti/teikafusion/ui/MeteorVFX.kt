package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.MeteorShower
import com.cukiti.teikafusion.model.Position
import kotlin.math.cos
import kotlin.math.sin

/**
 * **P-294 · LLUVIA DE METEORITOS** (2026-09-25) — lo que se ve del castigo del Espacio Profundo. Reglas
 * en [MeteorShower]; tres fases (tope 1200 ms):
 *
 *  1. **Telegrafía** — [MeteorLayer]: aviso 1, una MIRA recorre el tablero buscando; aviso 2, salta al
 *     cráter 2×2 y lo FIJA (y sus bloques tiemblan, `GameBoard.threatShakeCells`). La mira aparece con
 *     su barrido de radar ([MeteorSpottedVFX]). (Rediseño 2026-09-25, pedido del usuario: antes el
 *     meteorito Lottie flotaba en bucle durante los avisos y se le notaba reiniciarse.)
 *  2. **Impacto** — [MeteorImpactVFX]: la mira se cierra, el meteorito (Lottie `meteorito`) cae en
 *     diagonal, fogonazo, ondas de choque y esquirlas.
 *  3. **Consecuencia** — los bloques del cráter quedan en roca ([SpaceRockOverlay]) y los Teikas
 *     salen despedidos (lo hace el motor); si lo desviaste, [MeteorDeflectVFX].
 */

private val MeteorHot = Color(0xFFFF6A2B)
private val MeteorFire = Color(0xFFFFB23C)
private val MeteorTarget = Color(0xFFFF4D5E)

/** Lado del meteorito Lottie, en casillas. */
private const val METEOR_BOX_CELLS = 2.6f

/** Centro del cráter de [at], en píxeles. */
private fun craterCenter(at: Position, cellPx: Float) =
    Offset((at.x + MeteorShower.SIZE / 2f) * cellPx, (at.y + MeteorShower.SIZE / 2f) * cellPx)

/** De dónde viene: arriba a la izquierda, fuera del tablero (la estela del Lottie apunta ahí). */
private fun meteorOrigin(cellPx: Float) = Offset(-cellPx * 1.4f, -cellPx * 1.8f)

@Composable
private fun PlacedMeteor(center: Offset, scale: Float, alpha: Float, cellPx: Float) {
    val density = LocalDensity.current
    val boxPx = cellPx * METEOR_BOX_CELLS
    Box(
        Modifier
            .size(with(density) { boxPx.toDp() })
            .graphicsLayer {
                translationX = center.x - boxPx / 2f
                translationY = center.y - boxPx / 2f
                scaleX = scale; scaleY = scale
                this.alpha = alpha
            }
    ) {
        // Sin corte al aparecer: composición precargada y bucle en fase entre VFX y capa (BiomeLottie).
        BiomeLottie(R.raw.meteorito, Modifier.fillMaxSize())
    }
}

/**
 * Dónde está la mira que BARRE el tablero en el instante [t] (s): una curva de Lissajous que lo recorre
 * entero sin repetirse enseguida. Es la misma función para la capa y para su entrada, así que la mira
 * aparece justo donde luego sigue buscando.
 */
/**
 * P-319: desfase de la mira de cada cráter. Con la lluvia doble hay dos miras buscando a la vez, y con el
 * mismo recorrido irían una encima de la otra.
 */
private fun roamPhase(at: Position) = (at.x * 7 + at.y * 3) * 0.37f

private fun roamPoint(t: Float, w: Float, h: Float) = Offset(
    w / 2f + w * 0.32f * sin(t * 0.9f),
    h / 2f + h * 0.30f * sin(t * 1.3f + 0.7f),
)

/**
 * La MIRA: anillo, cuatro marcas hacia fuera, punto central y cuatro esquinas a [bracket] del centro.
 * [spin] gira las esquinas mientras busca; fijada, quietas.
 */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReticle(
    c: Offset,
    cellPx: Float,
    alpha: Float,
    bracket: Float,
    spin: Float,
    color: Color = MeteorTarget,
) {
    if (alpha <= 0f) return
    val r = cellPx * 0.62f
    val sw = cellPx * 0.07f
    drawCircle(Color.Black.copy(alpha = 0.35f * alpha), r, c, style = Stroke(sw * 1.8f))
    drawCircle(color.copy(alpha = alpha), r, c, style = Stroke(sw))
    for (i in 0 until 4) {
        val a = i * 1.5708f
        val d = Offset(cos(a), sin(a))
        drawLine(color.copy(alpha = alpha), c + d * (r * 0.55f), c + d * (r * 1.35f), sw)
    }
    drawCircle(color.copy(alpha = alpha), cellPx * 0.07f, c)
    // Las esquinas: cuatro «L» que se cierran sobre el blanco al fijarlo.
    val k = cellPx * 0.28f
    for (i in 0 until 4) {
        val a = spin + i * 1.5708f + 0.785f
        val corner = c + Offset(cos(a), sin(a)) * bracket
        val a1 = a + 2.356f
        val a2 = a - 2.356f
        drawLine(color.copy(alpha = alpha), corner, corner + Offset(cos(a1), sin(a1)) * k, sw, StrokeCap.Round)
        drawLine(color.copy(alpha = alpha), corner, corner + Offset(cos(a2), sin(a2)) * k, sw, StrokeCap.Round)
    }
}

/**
 * TELEGRAFÍA (rediseño 2026-09-25, pedido del usuario): aviso 1, una MIRA recorre el tablero buscando
 * —no dice aún dónde caerá—; aviso 2, salta al cráter y lo FIJA: sus esquinas se cierran, el cráter se
 * marca y sus bloques tiemblan. El meteorito solo aparece al caer ([MeteorImpactVFX]).
 */
@Composable
fun MeteorLayer(
    at: Position?,
    idleTurns: Int,
    threshold: Int,
    cellPx: Float,
    animationsOn: Boolean,
    hideReticle: Boolean,
    modifier: Modifier = Modifier,
) {
    if (at == null || cellPx <= 0f) return
    val locked = idleTurns >= threshold - 1
    val target = craterCenter(at, cellPx)
    // El salto de la mira al blanco: de donde esté buscando al cráter, con las esquinas cerrándose.
    val lock = remember(at) { Animatable(if (locked) 1f else 0f) }
    LaunchedEffect(at, locked) {
        if (locked) lock.animateTo(1f, tween(METEOR_LOCK_MS, easing = FastOutSlowInEasing)) else lock.snapTo(0f)
    }
    val dash = remember(cellPx) { PathEffect.dashPathEffect(floatArrayOf(cellPx * 0.22f, cellPx * 0.14f)) }
    val dashed = remember(cellPx) { Stroke(cellPx * 0.07f, pathEffect = dash) }
    // Capa propia (auditoría de fluidez 2026-09-29): la mira busca cada fotograma todo el aviso.
    Canvas(modifier.fillMaxSize().graphicsLayer()) {
        val t = if (animationsOn) biomeClockSec.floatValue else 0f
        val beat = ambientBeat(animationsOn, 0.7f)
        val k = lock.value
        if (k > 0f) {
            // El cráter marcado aparece al fijar.
            val side = MeteorShower.SIZE * cellPx
            val tl = Offset(at.x * cellPx, at.y * cellPx)
            drawRoundRect(MeteorTarget.copy(alpha = (0.14f + 0.14f * beat) * k), tl, Size(side, side), CornerRadius(cellPx * 0.3f))
            drawRoundRect(
                MeteorTarget.copy(alpha = 0.85f * k), tl, Size(side, side), CornerRadius(cellPx * 0.3f),
                style = dashed
            )
        }
        if (hideReticle) return@Canvas
        val searching = roamPoint(t + roamPhase(at), size.width, size.height)
        val c = searching + (target - searching) * k
        // Buscando: las esquinas abiertas y girando. Fijada: cerradas sobre el cráter y latiendo.
        val bracket = cellPx * (1.25f - 0.35f * k + 0.08f * beat * k)
        drawReticle(c, cellPx, alpha = 0.75f + 0.25f * k, bracket = bracket, spin = t * 2.2f * (1f - k))
    }
}

/** Lo que tarda la mira en saltar al cráter y fijarlo (aviso 2). */
private const val METEOR_LOCK_MS = 420

/** La MIRA aparece (aviso 1): un barrido de radar desde donde empieza a buscar. */
@Composable
fun MeteorSpottedVFX(event: GameEvent.MeteorSpotted, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(METEOR_SPOTTED_MS, easing = FastOutSlowInEasing))
        onComplete()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val c = roamPoint(biomeClockSec.floatValue + roamPhase(event.at), size.width, size.height)
        drawCircle(MeteorTarget, radius = cellPx * (0.4f + 3.2f * p), center = c, alpha = 0.55f * (1f - p),
            style = Stroke(cellPx * 0.08f))
        drawReticle(c, cellPx, alpha = p, bracket = cellPx * (2.2f - 0.95f * p), spin = p * 3f)
    }
}

/** Lo que dura la aparición de la mira (aviso). */
const val METEOR_SPOTTED_MS = 600

/**
 * IMPACTO: hasta el 45 % el meteorito cae en diagonal sobre el cráter; ahí el fogonazo, dos ondas de
 * choque, esquirlas de roca y polvo. El motor funde la roca y lanza los cuerpos en ese mismo instante.
 */
@Composable
fun MeteorImpactVFX(event: GameEvent.MeteorImpact, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }
    val p = progress.value
    val target = craterCenter(event.at, cellPx)
    val origin = meteorOrigin(cellPx)
    val fall = (p / 0.45f).coerceIn(0f, 1f)
    val after = ((p - 0.45f) / 0.55f).coerceIn(0f, 1f)
    Box(Modifier.fillMaxSize()) {
        // La mira fijada se cierra sobre el cráter mientras el meteorito cae, y se apaga con el golpe.
        Canvas(Modifier.fillMaxSize()) {
            drawReticle(target, cellPx, alpha = 1f - after * 2f, bracket = cellPx * (0.95f - 0.45f * fall), spin = 0f)
        }
        if (fall < 1f) {
            PlacedMeteor(origin + (target - origin) * (fall * fall), scale = 0.8f + 0.3f * fall, alpha = 1f, cellPx = cellPx)
        }
        Canvas(Modifier.fillMaxSize()) {
            if (after <= 0f) return@Canvas
            val flash = (1f - after / 0.35f).coerceIn(0f, 1f)
            drawCircle(
                Brush.radialGradient(listOf(Color.White, MeteorFire, Color.Transparent), target, cellPx * 2.4f),
                radius = cellPx * 2.4f, center = target, alpha = flash
            )
            // Dos ondas de choque.
            for (w in 0 until 2) {
                val k = (after - w * 0.15f).coerceIn(0f, 1f)
                drawCircle(MeteorHot, radius = cellPx * (0.6f + 3.4f * k), center = target,
                    alpha = 0.8f * (1f - k), style = Stroke(cellPx * 0.12f * (1f - k) + 1f))
            }
            // Esquirlas de roca que salen en abanico.
            for (i in 0 until 12) {
                val a = i * 0.5236f + event.at.x
                val d = cellPx * (0.4f + 3.2f * after)
                val c = target + Offset(cos(a), sin(a)) * d + Offset(0f, after * after * cellPx * 1.2f)
                drawRect(Color(0xFF6B5E7A), Offset(c.x - cellPx * 0.08f, c.y - cellPx * 0.08f),
                    Size(cellPx * 0.16f, cellPx * 0.16f), alpha = 1f - after)
            }
            // Polvo que queda flotando.
            drawCircle(Color(0xFF9D8FB0), radius = cellPx * (1.2f + after), center = target, alpha = 0.25f * (1f - after))
            // P-319 · METRALLA: cada fragmento vuela en arco hasta su casilla y aterriza al [SHARD_LAND]
            // (el motor lo pone en el tablero en ese mismo instante) con un destello.
            event.shards.forEach { cell ->
                val dest = Offset((cell.x + 0.5f) * cellPx, (cell.y + 0.5f) * cellPx)
                val fly = ((p - 0.45f) / (SHARD_LAND - 0.45f)).coerceIn(0f, 1f)
                if (fly < 1f) {
                    val arc = Offset(0f, -cellPx * 1.6f * 4f * fly * (1f - fly))
                    val c = target + (dest - target) * fly + arc
                    val half = cellPx * (0.18f + 0.14f * fly)
                    drawRoundRect(Color(0xFF4A4058), Offset(c.x - half, c.y - half), Size(half * 2, half * 2),
                        CornerRadius(half * 0.4f))
                    drawCircle(MeteorFire, radius = half * 0.55f, center = c, alpha = 0.9f)
                } else {
                    val land = ((p - SHARD_LAND) / (1f - SHARD_LAND)).coerceIn(0f, 1f)
                    drawCircle(MeteorHot, radius = cellPx * (0.3f + 0.6f * land), center = dest,
                        alpha = 0.8f * (1f - land), style = Stroke(cellPx * 0.08f))
                }
            }
        }
    }
}

/** P-319: fracción del impacto en la que aterriza la metralla (igual que `SHARD_LAND_PCT` del motor). */
private const val SHARD_LAND = 0.72f

/**
 * P-319 · LA ROCA SE AGRIETA (impacto de la grieta): una veta incandescente salta, en zigzag, de la roca al
 * bloque vecino; a la mitad lo alcanza (el motor lo funde ahí) y estalla en polvo de roca.
 */
@Composable
fun RockCrackVFX(event: GameEvent.RockCrack, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val a = Offset((event.from.x + 0.5f) * cellPx, (event.from.y + 0.5f) * cellPx)
        val b = Offset((event.to.x + 0.5f) * cellPx, (event.to.y + 0.5f) * cellPx)
        val grow = (p / 0.5f).coerceIn(0f, 1f)
        val fade = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
        // La veta: cuatro tramos en zigzag, perpendiculares a la dirección del contagio.
        val dir = b - a
        val normal = Offset(-dir.y, dir.x) * (0.12f / (dir.getDistance().coerceAtLeast(1f) / cellPx))
        var prev = a
        for (i in 1..4) {
            val k = i / 4f
            if (k > grow + 0.001f) break
            val zig = if (i == 4) Offset.Zero else normal * (if (i % 2 == 0) -1f else 1f)
            val next = a + dir * k + zig
            drawLine(MeteorHot, prev, next, cellPx * 0.07f, alpha = 1f - fade)
            drawLine(MeteorFire, prev, next, cellPx * 0.03f, alpha = 1f - fade)
            prev = next
        }
        if (fade > 0f) {
            drawCircle(MeteorFire, radius = cellPx * (0.2f + 0.5f * fade), center = b, alpha = 0.7f * (1f - fade))
            for (i in 0 until 6) {
                val ang = i * 1.047f
                val c = b + Offset(cos(ang), sin(ang)) * (cellPx * 0.6f * fade)
                drawRect(Color(0xFF6B5E7A), Offset(c.x - cellPx * 0.06f, c.y - cellPx * 0.06f),
                    Size(cellPx * 0.12f, cellPx * 0.12f), alpha = 1f - fade)
            }
        }
    }
}

/**
 * CONSECUENCIA buena: una línea cruzó el cráter y el meteorito se DESVÍA. La mira pierde el blanco: sus
 * esquinas salen despedidas girando, se vuelve azul y se apaga, con un anillo de alivio.
 */
@Composable
fun MeteorDeflectVFX(event: GameEvent.MeteorDeflect, cellPx: Float, onComplete: () -> Unit) {
    val progress = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        progress.animateTo(1f, tween(METEOR_DEFLECT_MS, easing = FastOutSlowInEasing))
        onComplete()
    }
    val relief = Color(0xFF9BE7FF)
    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val target = craterCenter(event.at, cellPx)
        drawReticle(target, cellPx, alpha = 1f - p, bracket = cellPx * (0.95f + 2.2f * p), spin = p * 4f, color = relief)
        for (i in 0 until 8) {
            val a = i * 0.785f
            val r = cellPx * (0.3f + 1.1f * p)
            drawCircle(relief, radius = cellPx * 0.06f * (1f - p), center = target + Offset(cos(a), sin(a)) * r, alpha = 1f - p)
        }
        drawCircle(relief, radius = cellPx * (0.5f + 1.2f * p), center = target, alpha = 0.5f * (1f - p), style = Stroke(cellPx * 0.08f))
    }
}

/** Lo que dura el desvío (consecuencia buena). */
const val METEOR_DEFLECT_MS = 700

/**
 * La cara de ROCA ESPACIAL de un bloque fundido (P-294): basalto violáceo con cráteres y vetas
 * incandescentes. Sustituye a la carcasa de cristal en los bloques blindados que trae un meteorito.
 */
@Composable
fun SpaceRockOverlay(
    modifier: Modifier = Modifier,
    /** P-319: la grieta que avisa del contagio (0 nada, 1 asoma, 2 brilla y contagia en la siguiente). */
    crackStage: Int = 0,
    animationsOn: Boolean = true,
) {
    Canvas(modifier) {
        drawSpaceRock(Offset.Zero, size)
        if (crackStage <= 0) return@Canvas
        val w = size.width
        fun at(fx: Float, fy: Float) = Offset(w * fx, size.height * fy)
        // La grieta atraviesa la roca de lado a lado: tenue al asomar, y latiendo en rojo vivo cuando va a saltar.
        val beat = if (crackStage >= 2 && animationsOn) 0.5f + 0.5f * sin(biomeClockSec.floatValue * 9f) else 0.6f
        val color = if (crackStage >= 2) MeteorTarget else MeteorFire
        val alpha = if (crackStage >= 2) 0.55f + 0.45f * beat else 0.55f
        // Los cuatro tramos de la grieta, sin lista por fotograma (late en cada roca a punto de contagiar).
        val cw = w * (if (crackStage >= 2) 0.07f else 0.045f)
        drawLine(color, at(0.08f, 0.50f), at(0.30f, 0.40f), cw, alpha = alpha)
        drawLine(color, at(0.30f, 0.40f), at(0.46f, 0.58f), cw, alpha = alpha)
        drawLine(color, at(0.46f, 0.58f), at(0.66f, 0.44f), cw, alpha = alpha)
        drawLine(color, at(0.66f, 0.44f), at(0.92f, 0.54f), cw, alpha = alpha)
        if (crackStage >= 2) drawCircle(color, radius = w * 0.42f, center = at(0.5f, 0.5f), alpha = 0.18f * beat)
    }
}

/** La cara de roca en un rectángulo cualquiera: la usan la casilla y el bloque que cae o se desliza. */
internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSpaceRock(tl: Offset, sz: Size) {
    val w = sz.width
    val h = sz.height
    val r = CornerRadius(w * 0.16f)
    fun at(fx: Float, fy: Float) = Offset(tl.x + w * fx, tl.y + h * fy)
    drawRoundRect(Color(0xFF4A4058), at(0.04f, 0.04f), Size(w * 0.92f, h * 0.92f), r, alpha = 0.92f)
    drawRoundRect(Color(0xFF6B5E7A), at(0.08f, 0.06f), Size(w * 0.84f, h * 0.42f), r, alpha = 0.55f)
    drawCircle(Color(0xFF2E2638), radius = w * 0.13f, center = at(0.32f, 0.36f))
    drawCircle(Color(0xFF2E2638), radius = w * 0.08f, center = at(0.68f, 0.62f))
    drawCircle(Color(0xFF2E2638), radius = w * 0.06f, center = at(0.40f, 0.72f))
    drawLine(MeteorHot, at(0.55f, 0.18f), at(0.78f, 0.40f), w * 0.04f, alpha = 0.85f)
    drawLine(MeteorFire, at(0.20f, 0.60f), at(0.30f, 0.84f), w * 0.03f, alpha = 0.75f)
}
