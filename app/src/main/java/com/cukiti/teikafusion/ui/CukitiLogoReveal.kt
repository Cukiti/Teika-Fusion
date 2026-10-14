package com.cukiti.teikafusion.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import com.cukiti.teikafusion.model.CukitiStrokes
import com.cukiti.teikafusion.ui.theme.AppBackground
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// P-374 · LA FIRMA DEL ESTUDIO (2026-09-30, diseño del usuario, en sus palabras: «un palito por
// letra… irán llegando arrastrándose y se formará la letra de ese solo palito»).
//   0. Empalme con la splash nativa (azul con el icono): SOLO el azul se funde a negro en
//      [HANDIN_MS], mientras las líneas ya entran por los bordes. El icono NO se vuelve a pintar: la
//      splash del sistema se retira con su propia animación y su tamaño no coincide con el nuestro,
//      así que el jugador veía el icono DOS veces (reporte del usuario, 2026-09-30).
//   1. Sobre negro, SEIS líneas rectas de colores —una por letra— entran lentamente, a la vez y
//      desde lados distintos, arrastrándose y un poco estiradas; al llegar recuperan su largo.
//   2. Cada línea se PLIEGA articulación por articulación (como una regla plegable: al doblarse una
//      junta, todo lo que va detrás gira con ella) hasta ser su letra. K y T se doblan sobre sí mismas.
//   3. Palabra completa → destello + firma sonora. Las letras pierden el color (a blanco) y ese color
//      se DERRAMA al fondo: cada letra suelta un halo de su color que crece y se vuelve por el lado
//      por el que entró su línea, así el negro se llena de los seis colores sin mezclarse en gris.
//   4. La palabra se acerca a cámara hasta desvanecerse y los colores del fondo se apagan hasta NEGRO
//      TOTAL (pedido del usuario: «que dure hasta que la pantalla se oscurezca totalmente»). La carga
//      aparece después, desde el negro: nunca se mezclan las dos escenas.
//
// El tiempo es el RELOJ DE LA INTRO ([avanzarReloj]): se pausa con la pantalla apagada y no salta
// tras un tirón. La firma sonora cuelga de él, no de un `delay()`.
//
// Es un logo: idéntico en cada arranque (nada de azar). Se dibuja en `drawWithCache` leyendo el reloj
// en la fase de dibujo: cero recomposición, y los Path/Stroke/pinceles se crean una vez por tamaño.
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

/** Los tiempos de la firma, en ms. «Reducir efectos» usa [Reducida]: sin vuelo, pliegue ni acercamiento. */
private class RevealTimeline(
    val arriveDur: Float,       // lo que tarda cada línea en llegar arrastrándose
    val arriveStagger: Float,   // desfase entre letras («casi a la vez»)
    val foldStart: Float,       // empieza a doblarse la primera articulación
    val foldStep: Float,        // desfase entre articulaciones sucesivas
    val foldDur: Float,         // lo que tarda en doblarse cada articulación
    val formEnd: Float,         // la palabra ya está entera
    val whitenStart: Float, val whitenEnd: Float,
    val bloomStart: Float, val bloomEnd: Float,
    val zoomStart: Float, val zoomEnd: Float, val zoomTo: Float,
    val darkStart: Float,       // los colores del fondo empiezan a apagarse
    val total: Float,
    val moves: Boolean
) {
    companion object {
        val Completa = RevealTimeline(
            arriveDur = 1_300f, arriveStagger = 60f,
            foldStart = 1_150f, foldStep = 200f, foldDur = 520f, formEnd = 2_500f,
            whitenStart = 2_550f, whitenEnd = 2_950f,
            bloomStart = 2_550f, bloomEnd = 3_150f,
            zoomStart = 2_950f, zoomEnd = 3_650f, zoomTo = 2.4f,
            darkStart = 3_300f, total = 4_000f, moves = true
        )
        val Reducida = RevealTimeline(
            arriveDur = 400f, arriveStagger = 20f,
            foldStart = 0f, foldStep = 0f, foldDur = 1f, formEnd = 500f,
            whitenStart = 600f, whitenEnd = 900f,
            bloomStart = 600f, bloomEnd = 950f,
            zoomStart = 900f, zoomEnd = 1_150f, zoomTo = 1f,
            darkStart = 950f, total = 1_300f, moves = false
        )
    }
}

/** Lo que tarda el azul de la splash nativa en fundirse a negro. */
private const val HANDIN_MS = 250f

/** Un color por letra (C U K I T I), de la familia Teika. */
private val LetterColors = listOf(
    Color(0xFFFF5252), Color(0xFF448AFF), Color(0xFF69F0AE),
    Color(0xFFFFE57F), Color(0xFFE040FB), Color(0xFF18FFFF)
)

/** Una letra ya en píxeles: sus tramos, sus giros y el recorrido de su cabeza. */
private class LetterGeo(segs: Int) {
    val len = FloatArray(segs)     // largo de cada tramo
    val turn = FloatArray(segs)    // giro en la articulación que abre cada tramo (el 0 no gira)
    var beta = 0f                  // dirección del cuerpo recto: la del primer tramo
    var hx = 0f; var hy = 0f       // dónde acaba la cabeza
    var sx = 0f; var sy = 0f       // dónde empieza la cabeza (fuera de pantalla)
    var cx = 0f; var cy = 0f       // centro de la letra (origen de su halo)
    var bx = 0f; var by = 0f       // hacia dónde se va el halo: el lado por el que entró la línea
    val path = Path()
    var halo: Brush? = null
}

@Composable
fun CukitiLogoReveal(
    reducedFx: Boolean,
    onSting: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tl = if (reducedFx) RevealTimeline.Reducida else RevealTimeline.Completa
    val letters = CukitiStrokes.letters
    val time = remember { mutableFloatStateOf(0f) }

    LaunchedEffect(tl) {
        // El reloj arranca con fotogramas ya estables. El primer frame de la app es el más caro
        // (se compone todo el árbol): un `tween` que empezara ahí saltaría de golpe unos cientos de
        // ms y las líneas aparecerían ya a medio camino.
        withFrameNanos { }
        introLog("primer frame de la firma")
        withFrameNanos { }
        introLog("CUKITI arranca reloj")
        var sonada = false
        avanzarReloj(time, tl.total) { t ->
            if (!sonada && t >= tl.formEnd - 40f) {
                sonada = true
                introLog("firma sonora")
                onSting()
            }
        }
        onDone()
    }

    Spacer(
        modifier
            .fillMaxSize()
            .drawWithCache {
                // ── Geometría en píxeles (solo cambia con el tamaño) ──────────────────────────
                val letterH = min(size.width * 0.8f / CukitiStrokes.width, size.height * 0.11f)
                val strokePx = letterH * CukitiStrokes.STROKE
                val ox = (size.width - CukitiStrokes.width * letterH) / 2f
                val oy = (size.height - letterH) / 2f
                val wordCenter = Offset(size.width / 2f, size.height / 2f)
                val haloR = max(size.width, size.height) * 0.55f
                val core = Stroke(width = strokePx, cap = StrokeCap.Round, join = StrokeJoin.Round)
                val glow = Stroke(width = strokePx * 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)

                val geo = letters.mapIndexed { l, letra ->
                    val segs = letra.size - 1
                    val g = LetterGeo(segs)
                    var prev = 0f
                    for (j in 0 until segs) {
                        val dx = (letra.xs[j + 1] - letra.xs[j]) * letterH
                        val dy = (letra.ys[j + 1] - letra.ys[j]) * letterH
                        g.len[j] = hypot(dx, dy)
                        val a = atan2(dy, dx)
                        if (j == 0) g.beta = a else g.turn[j] = wrap(a - prev)
                        prev = a
                    }
                    g.hx = ox + letra.xs[0] * letterH; g.hy = oy + letra.ys[0] * letterH
                    // El cuerpo va DETRÁS de la cabeza, en la dirección del primer tramo: la cabeza
                    // arranca JUSTO fuera del borde por ese lado y cruza la pantalla arrastrando la
                    // línea. Más lejos, la línea tardaba hasta 0,7 s en asomar y se veía un negro
                    // vacío entre el icono de la splash y la llegada.
                    val lejos = distanciaAlBorde(g.hx, g.hy, cos(g.beta), sin(g.beta), size.width, size.height) +
                        strokePx * 2f
                    g.sx = g.hx + cos(g.beta) * lejos; g.sy = g.hy + sin(g.beta) * lejos
                    g.cx = ox + (letra.xs.min() + letra.xs.max()) / 2f * letterH
                    g.cy = oy + letterH / 2f
                    val huida = max(size.width, size.height) * 0.4f
                    g.bx = cos(g.beta) * huida; g.by = sin(g.beta) * huida
                    val c = LetterColors[l % LetterColors.size]
                    g.halo = Brush.radialGradient(
                        colors = listOf(c.copy(alpha = 0.6f), c.copy(alpha = 0.2f), Color.Transparent),
                        center = Offset(g.cx, g.cy),
                        radius = haloR
                    )
                    g
                }

                onDrawBehind {
                    val t = time.floatValue
                    drawRect(Color.Black)

                    // ── 0 · Empalme con la splash nativa: solo su azul ───────────────────────
                    val splash = 1f - smooth(ramp(t, 0f, HANDIN_MS))
                    if (splash > 0f) drawRect(AppBackground, alpha = splash)

                    // ── 3 · El color se va al fondo ───────────────────────────────────────────
                    val bloom = easeOutCubic(ramp(t, tl.bloomStart, tl.bloomEnd))
                    // Y al final se apagan hasta negro total.
                    val apagado = 1f - smooth(ramp(t, tl.darkStart, tl.total))
                    if (bloom > 0f && apagado > 0f) {
                        for (g in geo) {
                            withTransform({
                                translate(g.bx * bloom, g.by * bloom)
                                scale(bloom, bloom, Offset(g.cx, g.cy))
                            }) {
                                drawCircle(
                                    brush = g.halo!!, radius = haloR, center = Offset(g.cx, g.cy),
                                    alpha = bloom * apagado, blendMode = BlendMode.Screen
                                )
                            }
                        }
                    }

                    // ── 1·2·4 · Las líneas ────────────────────────────────────────────────────
                    val whiten = smooth(ramp(t, tl.whitenStart, tl.whitenEnd))
                    val zoomP = ramp(t, tl.zoomStart, tl.zoomEnd)
                    val zoom = 1f + (tl.zoomTo - 1f) * zoomP * zoomP          // acelera hacia cámara
                    val flash = bump(t, tl.formEnd - 40f, tl.formEnd + 220f)   // la palabra se completa
                    val wordAlpha = 1f - smooth(ramp(t, tl.zoomStart + 200f, tl.zoomEnd))
                    if (wordAlpha <= 0f) return@onDrawBehind
                    val pulse = zoom * (1f + 0.04f * flash)

                    withTransform({ scale(pulse, pulse, wordCenter) }) {
                        geo.forEachIndexed { l, g ->
                            val a0 = l * tl.arriveStagger
                            val llegada = ramp(t, a0, a0 + tl.arriveDur)
                            if (llegada <= 0f) return@forEachIndexed
                            val e = if (tl.moves) easeOutCubic(llegada) else 1f
                            val aparece = if (tl.moves) 1f else smooth(llegada)
                            // Estirada mientras se arrastra; al llegar recupera su largo exacto.
                            val estira = 1f + 0.25f * (1f - e)
                            var x = g.sx + (g.hx - g.sx) * e
                            var y = g.sy + (g.hy - g.sy) * e
                            var ang = g.beta
                            g.path.reset()
                            g.path.moveTo(x, y)
                            for (j in g.len.indices) {
                                if (j > 0) {
                                    val f0 = tl.foldStart + (j - 1) * tl.foldStep
                                    val f = if (tl.moves) smooth(ramp(t, f0, f0 + tl.foldDur)) else 1f
                                    ang += g.turn[j] * f
                                }
                                x += cos(ang) * g.len[j] * estira
                                y += sin(ang) * g.len[j] * estira
                                g.path.lineTo(x, y)
                            }
                            val color = lerp(LetterColors[l % LetterColors.size], Color.White, whiten)
                            drawPath(g.path, color, alpha = (0.18f + 0.35f * flash) * wordAlpha * aparece, style = glow)
                            drawPath(g.path, color, alpha = wordAlpha * aparece, style = core)
                        }
                    }
                }
            }
    )
}

/** Cuánto hay desde (x, y) hasta salir del rectángulo [ancho]×[alto] en la dirección (dx, dy). */
private fun distanciaAlBorde(x: Float, y: Float, dx: Float, dy: Float, ancho: Float, alto: Float): Float {
    val tx = when {
        dx > 1e-4f -> (ancho - x) / dx
        dx < -1e-4f -> -x / dx
        else -> Float.MAX_VALUE
    }
    val ty = when {
        dy > 1e-4f -> (alto - y) / dy
        dy < -1e-4f -> -y / dy
        else -> Float.MAX_VALUE
    }
    return min(tx, ty)
}

/** Ángulo a (-π, π]: cada articulación gira por el camino corto. */
private fun wrap(a: Float): Float {
    var x = a
    val dosPi = 2f * PI.toFloat()
    while (x > PI) x -= dosPi
    while (x <= -PI) x += dosPi
    return x
}

private fun ramp(t: Float, from: Float, to: Float): Float = ((t - from) / (to - from)).coerceIn(0f, 1f)
private fun smooth(x: Float): Float = x * x * (3f - 2f * x)
private fun easeOutCubic(x: Float): Float { val u = 1f - x; return 1f - u * u * u }
/** 0 → 1 → 0 entre [from] y [to], con el pico al primer cuarto. */
private fun bump(t: Float, from: Float, to: Float): Float {
    val x = ramp(t, from, to)
    return if (x < 0.25f) x / 0.25f else 1f - (x - 0.25f) / 0.75f
}
