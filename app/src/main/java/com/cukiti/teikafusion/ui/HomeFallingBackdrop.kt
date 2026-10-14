package com.cukiti.teikafusion.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import com.cukiti.teikafusion.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * **LA LLUVIA DE INICIO** (2026-09-23, pedido del usuario): de fondo, en bucle infinito, Teikas y
 * fichas caen despacio en tres capas de profundidad (lejos = pequeño, lento y tenue). Sustituye a
 * los tres Teikas que flotaban quietos en las esquinas.
 *
 * Coste: los sprites se pintan UNA vez en bitmaps (Teikas desde su Lottie, fichas con el bloque
 * equipado) y por frame solo se trasladan y giran — cero asignaciones en el bucle de dibujo. El
 * reloj se lee en fase de DIBUJO, así que el frame no recompone nada. Se para con la pestaña fuera
 * de vista ([LocalTabActive]) y con «reducir efectos» ([LocalAnimationsActive]): ahí se queda un
 * fotograma quieto, que sigue decorando.
 */
@Composable
fun HomeFallingBackdrop(skin: BlockSkin, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val spritePx = SPRITE_DP * density.density
    val sprites by produceState<FallingSprites?>(null, skin, spritePx) {
        value = withContext(Dispatchers.Default) { buildSprites(context, skin, spritePx, density) }
    }
    val items = remember { buildItems() }
    val clock = remember { mutableFloatStateOf(0f) }
    val live = LocalTabActive.current && LocalAnimationsActive.current

    // A 30 fps en pantallas de 90/120 Hz (análisis a fondo de Inicio, 2026-10-01), como el fondo de los
    // biomas (P-368): es decorado y redibujaba Inicio entero a la tasa de la pantalla. A 60 Hz, igual.
    val stepNs = rememberAmbientStepNs()
    LaunchedEffect(live, stepNs) {
        if (!live) return@LaunchedEffect
        val base = clock.floatValue
        val start = withFrameNanos { it }
        while (true) withFrameNanos { now ->
            val t = base + ambientNs(now - start, stepNs) / 1_000_000_000f
            if (t != clock.floatValue) clock.floatValue = t
        }
    }

    Canvas(modifier) {
        val s = sprites ?: return@Canvas
        val t = clock.floatValue
        val w = size.width
        val h = size.height
        for (item in items) {
            val img = if (item.teika) s.teikas[item.variant % s.teikas.size] else s.pieces[item.variant % s.pieces.size]
            val px = w * item.sizeFrac
            val dw = px * img.width / s.maxSide
            val dh = px * img.height / s.maxSide
            val travel = h + dh * 2f
            val y = ((item.phase * travel + t * item.speed * h) % travel) - dh
            val x = w * item.x + sin(t * item.swayFreq + item.phase * 6.28f) * px * 0.25f
            rotate(degrees = item.rot0 + t * item.spin, pivot = Offset(x, y + dh / 2f)) {
                drawImage(
                    img,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(img.width, img.height),
                    dstOffset = IntOffset((x - dw / 2f).roundToInt(), y.roundToInt()),
                    dstSize = IntSize(dw.roundToInt(), dh.roundToInt()),
                    alpha = item.alpha
                )
            }
        }
    }
}

/** Lado del sprite pintado; se escala al dibujar (la capa cercana es la más grande). */
private const val SPRITE_DP = 96f

private class FallingSprites(val teikas: List<ImageBitmap>, val pieces: List<ImageBitmap>, val maxSide: Float)

private class FallingItem(
    val teika: Boolean,
    val variant: Int,
    val x: Float,
    val phase: Float,
    /** Alturas de pantalla por segundo. */
    val speed: Float,
    /** Tamaño del sprite como fracción del ancho de pantalla. */
    val sizeFrac: Float,
    val alpha: Float,
    val spin: Float,
    val rot0: Float,
    val swayFreq: Float,
)

/** Tres capas (lejos → cerca), ya ordenadas para dibujar de fondo a frente. Semilla fija: siempre la misma lluvia. */
private fun buildItems(): List<FallingItem> {
    val rnd = Random(20260923)
    data class Layer(val count: Int, val speed: Float, val size: Float, val alpha: Float)
    val layers = listOf(
        Layer(7, 0.030f, 0.085f, 0.22f),
        Layer(5, 0.048f, 0.125f, 0.34f),
        Layer(3, 0.070f, 0.170f, 0.48f),
    )
    return buildList {
        layers.forEach { l ->
            repeat(l.count) { i ->
                val teika = (i + size) % 2 == 0
                add(
                    FallingItem(
                        teika = teika,
                        variant = rnd.nextInt(16),
                        // Repartidos por franjas para que no se amontonen en una columna.
                        x = (i + 0.2f + rnd.nextFloat() * 0.6f) / l.count,
                        phase = rnd.nextFloat(),
                        speed = l.speed * (0.85f + rnd.nextFloat() * 0.3f),
                        sizeFrac = l.size * (0.85f + rnd.nextFloat() * 0.3f),
                        alpha = l.alpha,
                        spin = (if (rnd.nextBoolean()) 1f else -1f) * (if (teika) 12f else 22f) * (0.5f + rnd.nextFloat()),
                        rot0 = rnd.nextFloat() * 360f,
                        swayFreq = 0.4f + rnd.nextFloat() * 0.5f,
                    )
                )
            }
        }
    }
}

/** Formas de ficha que caen (celdas en una rejilla de 3×3). */
private val FALLING_SHAPES: List<List<Pair<Int, Int>>> = listOf(
    listOf(0 to 0, 1 to 0, 0 to 1, 1 to 1),          // cuadrado
    listOf(0 to 0, 0 to 1, 0 to 2, 1 to 2),          // L
    listOf(0 to 0, 1 to 0, 2 to 0, 1 to 1),          // T
    listOf(1 to 0, 2 to 0, 0 to 1, 1 to 1),          // S
    listOf(0 to 0, 1 to 0, 2 to 0),                  // línea de 3
    listOf(0 to 0),                                  // monominó
)

private fun buildSprites(context: Context, skin: BlockSkin, px: Float, density: Density): FallingSprites {
    val side = px.roundToInt().coerceAtLeast(16)
    val teikas = listOf(R.raw.teika_1, R.raw.teika_2, R.raw.teika_3, R.raw.teika_4).mapNotNull { res ->
        val comp = com.airbnb.lottie.LottieCompositionFactory.fromRawResSync(context, res).value ?: return@mapNotNull null
        val bmp = android.graphics.Bitmap.createBitmap(side, side, android.graphics.Bitmap.Config.ARGB_8888)
        com.airbnb.lottie.LottieDrawable().apply {
            setComposition(comp)
            progress = 0.25f
            setBounds(0, 0, side, side)
            draw(android.graphics.Canvas(bmp))
        }
        bmp.asImageBitmap()
    }
    val cell = side / 3f
    val pieces = FALLING_SHAPES.flatMapIndexed { si, shape ->
        // Dos colores por forma: la lluvia no es monocroma.
        listOf(si, si + 2).map { colorType ->
            val cols = shape.maxOf { it.first } + 1
            val rows = shape.maxOf { it.second } + 1
            val w = (cols * cell).roundToInt(); val h = (rows * cell).roundToInt()
            val img = ImageBitmap(w, h)
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, androidx.compose.ui.graphics.Canvas(img), Size(w.toFloat(), h.toFloat())) {
                shape.forEach { (cx, cy) ->
                    drawSkinnedBlock(skin, colorType, Offset(cx * cell, cy * cell), Size(cell, cell))
                }
            }
            img
        }
    }
    return FallingSprites(teikas.ifEmpty { pieces }, pieces, side.toFloat())
}
