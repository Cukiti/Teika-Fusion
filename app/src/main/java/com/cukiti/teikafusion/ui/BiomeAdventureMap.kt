package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.BiomeRegistry
import com.cukiti.teikafusion.model.BiomeType
import com.cukiti.teikafusion.model.LevelMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════════════
//  Mapa de niveles = un CUADRO pixelart enmarcado que se va dibujando bloque a
//  bloque (1 nivel = 1 cuadrito). Empieza vacío; al superar un nivel se "enciende"
//  el cuadrito correspondiente con su color, hasta completar la figura del bioma.
//  El jugador toca cualquier cuadrito ya jugable para seleccionarlo (foco + se
//  eleva); el botón inferior adopta su color y su nombre, y al pulsarlo lo juega.
// ════════════════════════════════════════════════════════════════════════════

/** Un bloque del mosaico: posición en la rejilla de la figura + color base. */
data class AdventureBlock(val col: Int, val row: Int, val color: Color)

/**
 * Una "aventura" temática. [blocks] está EN ORDEN DE LLENADO (serpiente): el bloque i se
 * completa al pasar el nivel i de la aventura. [cols]×[rows] = tamaño de la rejilla de la figura.
 */
data class BiomeAdventure(
    val name: String,
    val accent: Color,
    val cols: Int,
    val rows: Int,
    val blocks: List<AdventureBlock>,
    val muralResId: Int
) {
    val levelCount: Int get() = blocks.size
}

// ── Tono compartido para los cuadritos aún vacíos (bloqueados) ─────────────────
private val GHOST_SLATE = Color(0xFF8EA0C8)  // gris-azul desaturado (mezcla hacia él)
private val GHOST_DEEP  = Color(0xFF28324F)  // sombra fría del bisel fantasma

// ── Color de acento por bioma (provisional para los murales placeholder de la Fase 1) ──────────
internal fun accentFor(biome: BiomeType): Color = when (biome) {
    BiomeType.CRYSTAL  -> Color(0xFF4DB8FF)
    BiomeType.SLEEP    -> Color(0xFFB39DFF)
    BiomeType.FEAR     -> Color(0xFF8A7CB8)
    BiomeType.GARDEN   -> Color(0xFF5BD16A)
    BiomeType.STORM    -> Color(0xFFFFD24D)
    BiomeType.VOLCANO  -> Color(0xFFFF7A1A)
    BiomeType.NINJA    -> Color(0xFF7A8AA0)
    BiomeType.GLACIER  -> Color(0xFF7FE3FF)
    BiomeType.GHOST    -> Color(0xFFB0A0FF)
    BiomeType.GLUTTON  -> Color(0xFFFFA24D)
    BiomeType.TOXIC    -> Color(0xFF8BD64B)
    BiomeType.STAR     -> Color(0xFFFFD23E)
    BiomeType.KING     -> Color(0xFFFFC83C)
    BiomeType.GALACTIC -> Color(0xFFB48CFF)
    BiomeType.NONE     -> Color(0xFF8EA0C8)
}

/** Fondo vertical final de cada capítulo. Referencias estáticas para que R8 pueda conservarlas. */
private fun muralResFor(biome: BiomeType): Int = when (biome) {
    BiomeType.CRYSTAL  -> R.raw.mural_crystal
    BiomeType.SLEEP    -> R.raw.mural_sleep
    BiomeType.FEAR     -> R.raw.mural_fear
    BiomeType.GARDEN   -> R.raw.mural_garden
    BiomeType.STORM    -> R.raw.mural_storm
    BiomeType.VOLCANO  -> R.raw.mural_volcano
    BiomeType.NINJA    -> R.raw.mural_ninja
    BiomeType.GLACIER  -> R.raw.mural_glacier
    BiomeType.GHOST    -> R.raw.mural_ghost
    BiomeType.GLUTTON  -> R.raw.mural_glutton
    BiomeType.TOXIC    -> R.raw.mural_toxic
    BiomeType.STAR     -> R.raw.mural_star
    BiomeType.KING     -> R.raw.mural_king
    BiomeType.GALACTIC -> R.raw.mural_galactic
    BiomeType.NONE     -> error("El bioma NONE no tiene mural")
}

/**
 * Construye un mural a partir de arte ASCII (fila superior primero) + leyenda de colores. El orden
 * de LLENADO es en serpiente de ABAJO hacia ARRIBA (boustrophedon) → la figura "se dibuja" desde su
 * base conforme se superan niveles. Red de seguridad: si el nº de celdas no coincide con [count]
 * (nº de niveles del capítulo), se recorta arriba / se añade una base para que el mapa siga cubriendo
 * exactamente 1..totalLevels (los íconos están diseñados para coincidir, así que no debería saltar).
 */
private fun figureFromAscii(
    name: String,
    accent: Color,
    art: List<String>,
    legend: Map<Char, Color>,
    count: Int,
    muralResId: Int
): BiomeAdventure {
    val rows = art.size
    val cols = art.maxOf { it.length }
    val raw = mutableListOf<AdventureBlock>()
    var dir = 1
    for (r in rows - 1 downTo 0) {
        val line = art[r]
        val order = if (dir > 0) (0 until cols) else (cols - 1 downTo 0)
        for (c in order) {
            val ch = line.getOrNull(c) ?: ' '
            val color = legend[ch]
            if (ch != ' ' && ch != '.' && color != null) raw.add(AdventureBlock(c, r, color))
        }
        dir = -dir
    }
    return when {
        raw.size == count -> BiomeAdventure(name, accent, cols, rows, raw, muralResId)
        raw.size > count  -> {
            val cut = raw.take(count)
            BiomeAdventure(name, accent, cols, (cut.maxOfOrNull { it.row } ?: (rows - 1)) + 1, cut, muralResId)
        }
        else -> {
            val need = count - raw.size
            val pad = (0 until need).map { i -> AdventureBlock(i % cols, rows + i / cols, accent) }
            BiomeAdventure(name, accent, cols, rows + (need + cols - 1) / cols, pad + raw, muralResId)
        }
    }
}

// ── Arte pixelart de cada bioma (multicolor). El nº de celdas = nº de niveles del capítulo. ───────
private fun muralArt(biome: BiomeType): Pair<List<String>, Map<Char, Color>> = when (biome) {
    // Gema facetada. Era de 22 celdas —los 15 niveles del capítulo MÁS los 7 del tutorial, que
    // vivían aquí— y se redibujó a 15 al sacar las lecciones del mural (2026-09-03). No bastaba con
    // dejar de pintar sus siete cuadros: el recorrido del mural va de ABAJO ARRIBA, así que esos
    // siete eran la PUNTA y el cuerpo bajo de la gema, y ocultarlos la habría dejado sin punta.
    BiomeType.CRYSTAL -> listOf(   // gema facetada (15)
        ".CCC.",
        "CCCCC",
        ".BBB.",
        ".BBB.",
        "..D.."
    ) to mapOf('C' to Color(0xFF9CECFF), 'B' to Color(0xFF4DB8FF), 'D' to Color(0xFF2E6FE0))
    BiomeType.SLEEP -> listOf(     // luna creciente + estrella (21)
        ".MMM.S",
        "MMMM..",
        "MMM...",
        "MMM...",
        "MMMM..",
        ".MMM.."
    ) to mapOf('M' to Color(0xFFD8D2F0), 'S' to Color(0xFFFFF3B0))
    BiomeType.FEAR -> listOf(      // murciélago (19)
        "B.....B",
        "BB...BB",
        "BBBBBBB",
        ".BBBBB.",
        "...B..."
    ) to mapOf('B' to Color(0xFF7E5BB0))
    BiomeType.GARDEN -> listOf(    // flor (18)
        ".YYY.",
        "YYBYY",
        ".YYY.",
        "..G..",
        ".GGG.",
        ".GGG."
    ) to mapOf('Y' to Color(0xFFFFD24D), 'B' to Color(0xFFC97A2E), 'G' to Color(0xFF5BD16A))
    BiomeType.STORM -> listOf(     // nube + rayo (21)
        ".WWWW.",
        "WWWWWW",
        "...LL.",
        "..LL..",
        ".LLL..",
        ".LL...",
        "LL...."
    ) to mapOf('W' to Color(0xFF9FB0C8), 'L' to Color(0xFFFFD24D))
    BiomeType.VOLCANO -> listOf(   // volcán + lava (22)
        "..OO..",
        ".MOOM.",
        ".MMMM.",
        "MMMMMM",
        "MMMMMM"
    ) to mapOf('O' to Color(0xFFFF7A1A), 'M' to Color(0xFF6E4A2E))
    BiomeType.NINJA -> listOf(     // máscara ninja + cinta (22)
        ".KKKKK.",
        ".RRRRR.",
        "KWWKWWK",
        ".KKKKK."
    ) to mapOf('K' to Color(0xFF2A2A33), 'R' to Color(0xFFD23B3B), 'W' to Color(0xFFE8E8F0))
    BiomeType.GLACIER -> listOf(   // copo de nieve (21)
        "...A...",
        ".A.A.A.",
        "..ABA..",
        "ABBBBBA",
        "..ABA..",
        ".A.A.A.",
        "...A..."
    ) to mapOf('A' to Color(0xFFCFF2FF), 'B' to Color(0xFF7FD3F0))
    BiomeType.GHOST -> listOf(     // fantasma (21)
        ".GGG.",
        "GGGGG",
        "GEGEG",
        "GGGGG",
        "G.G.G"
    ) to mapOf('G' to Color(0xFFD8D0F0), 'E' to Color(0xFF2A2A4A))
    BiomeType.GLUTTON -> listOf(   // hamburguesa (20)
        ".BBBB.",
        "SSSSSS",
        "MMMMMM",
        ".BBBB."
    ) to mapOf('B' to Color(0xFFE6B860), 'S' to Color(0xFF6CC04A), 'M' to Color(0xFF7A4A2E))
    BiomeType.TOXIC -> listOf(     // barril tóxico (22)
        ".GGGG.",
        "YYYYYY",
        "YKKKKY",
        ".YYYY.",
        "..YY.."
    ) to mapOf('G' to Color(0xFF8BD64B), 'Y' to Color(0xFFEFC44A), 'K' to Color(0xFF2A2A22))
    BiomeType.STAR -> listOf(      // estrella de 5 puntas (22)
        "...S...",
        "..SSS..",
        "SSSSSSS",
        ".SSSSS.",
        "..S.S..",
        ".SS.SS."
    ) to mapOf('S' to Color(0xFFFFD23E))
    BiomeType.KING -> listOf(      // corona (21)
        "K.K.K",
        "KKKKK",
        "KJKJK",
        "KKKKK",
        ".KKK."
    ) to mapOf('K' to Color(0xFFFFC83C), 'J' to Color(0xFFFF4D6D))
    BiomeType.GALACTIC -> listOf(  // planeta con anillo (21)
        "..PP...",
        ".PPPPP.",
        "RPPPPPR",
        ".PPPPP.",
        "...PP.."
    ) to mapOf('P' to Color(0xFF9B6BE0), 'R' to Color(0xFFC9A8F0))
    BiomeType.NONE -> listOf("XX", "XX") to mapOf('X' to Color(0xFF8EA0C8))
}

// ── Murales: uno por capítulo de LevelMap. El primero absorbe los niveles de intro (1..introLevels)
//    para que el mapa cubra exactamente 1..totalLevels. ─────────────────────────────────────────
// El primer mural YA NO se come los niveles de intro (2026-09-03): las siete lecciones salieron de
// la Caverna de Cristal y ahora viven detrás del «?» de «Elige tu mundo» (ver [LessonsPanel]). Cada
// aventura vale exactamente los niveles de SU capítulo.
private val ADVENTURES: List<BiomeAdventure> = LevelMap.chapters.map { ch ->
    val (art, legend) = muralArt(ch.biome)
    figureFromAscii(
        name = ch.biome.displayName.uppercase(),
        accent = accentFor(ch.biome),
        art = art,
        legend = legend,
        count = ch.levelCount,
        muralResId = muralResFor(ch.biome)
    )
}

/**
 * Coordenadas normalizadas de los niveles sobre el camino pintado. El índice cero queda abajo y el
 * último arriba. La onda sigue el recorrido en S que comparten los catorce fondos sin alterar el
 * orden real de [BiomeAdventure.blocks].
 */
private fun muralNodePositions(count: Int): List<Offset> = List(count) { index ->
    val progress = if (count <= 1) 0f else index.toFloat() / (count - 1).toFloat()
    val x = 0.5f - 0.22f * sin(progress * Math.PI * 5.0).toFloat()
    val y = 0.91f - progress * 0.82f
    Offset(x, y)
}

/** Primer nivel de cada aventura: se DERIVA de [LevelMap.chapterStart] (una sola fuente). */
private val ADVENTURE_START: List<Int> = LevelMap.chapterStart

/**
 * Nombre mostrado del nivel: el bioma real del juego. Las casillas sin bioma son **las lecciones**
 * del tutorial, y así se nombran (P-92, 2026-09-02): llamarlas «NIVEL n» era lo que hacía que el
 * primer nivel de verdad quedara desplazado siete puestos sin explicación.
 */
private fun levelTitle(level: Int): String {
    val name = BiomeRegistry.currentBiome(level).displayName
    return if (name == "—") "LECCIÓN $level" else name.uppercase()
}

// ── Helpers públicos para teñir otros overlays (intro de nivel, victoria) con el bioma. ──────────
/** Color de acento del bioma del nivel [level]. */
fun levelAccentColor(level: Int): Color = accentFor(LevelMap.biomeFor(level))
/** Etiqueta del bioma del nivel [level] (o "NIVEL n" en intro). */
fun levelBiomeLabel(level: Int): String = levelTitle(level)

// (BORRADO 2026-09-25, P-259) `BiomeAdventureView` —la vista de niveles de un bioma que usaba la lista
// «Elige tu mundo»— con sus flechas, su fila de estrellas, `AdventureMosaic` y `AdventureNode`. El mapa de
// mundos (`WorldMap.kt`) la sustituyó.

/**
 * Marco 3D estilo "cuadro": bisel exterior metálico (luz arriba-izq, sombra abajo-der), tinte de
 * acento del bioma y panel interior hundido y oscuro donde se dibuja la figura. Cuando [complete],
 * el marco se vuelve DORADO y estampa un sello "✓" en la esquina (el cuadro quedó terminado).
 */
@Composable
private fun PictureFrame(accent: Color, complete: Boolean = false, content: @Composable BoxScope.() -> Unit) {
    // Marco terminado → bisel dorado; en curso → acento del bioma.
    val frameTint = if (complete) lerp(accent, Color(0xFFFFC83C), 0.7f) else accent
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(6.dp)
            .clip(RoundedCornerShape(22.dp))
            // Bisel del marco: degradado diagonal claro→oscuro = relieve.
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(frameTint, Color.White, 0.30f),
                        lerp(frameTint, Color.Black, 0.42f)
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(900f, 900f)
                )
            )
            .border(
                BorderStroke(if (complete) 2.dp else 1.5.dp, Color.White.copy(alpha = if (complete) 0.5f else 0.30f)),
                RoundedCornerShape(22.dp)
            )
            .padding(11.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(13.dp))
                // Panel interior hundido: fondo oscuro con viñeta para resaltar la figura.
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF141B30), Color(0xFF0C1124)))
                )
                .border(BorderStroke(1.dp, Color.Black.copy(alpha = 0.55f)), RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
            content = content
        )

        // Sello dorado de cuadro terminado, en la esquina superior derecha del marco.
        if (complete) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(34.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.verticalGradient(listOf(Color(0xFFFFE27A), Color(0xFFFFB020))))
                    .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.85f)), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "✓", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF3A2A00))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Cinemática de MURAL COMPLETO — se dispara al superar el último nivel de un
//  capítulo. El mural entero aparece a TODO color, un destello recorre los bloques
//  uno a uno (cada cuadrito "glinta"), rayos giran detrás y cae la recompensa.
// ════════════════════════════════════════════════════════════════════════════

private val MURAL_GOLD = Color(0xFFFFD23E)

@Composable
fun MuralCompleteOverlay(
    chapterIndex: Int,
    reward: Int,
    /** P-250: el bloque que este capítulo acaba de descubrir, o `null`. */
    newBlockSkinId: String? = null,
    onDismiss: () -> Unit
) {
    val adv = ADVENTURES.getOrNull(chapterIndex) ?: return

    // Atrás = cerrar la cinemática (el premio ya lo concedió el ViewModel; antes atrás la ignoraba).
    BackHandler { onDismiss() }

    // Entrada con rebote (escala + alpha).
    val appear = remember(chapterIndex) { Animatable(0f) }
    LaunchedEffect(chapterIndex) {
        appear.snapTo(0f)
        appear.animateTo(
            1f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    val loop = rememberInfiniteTransition(label = "muralCelebrate")
    val sweep by loop.animateFloat(
        initialValue = -0.35f, targetValue = 1.35f,
        animationSpec = infiniteRepeatable(tween(1700, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    val twinkle by loop.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "twinkle"
    )
    val rayRot by loop.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "rays"
    )

    val a = appear.value.coerceIn(0f, 1f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070F).copy(alpha = 0.88f * a))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        val showcaseHeight = minOf(maxHeight * 0.56f, maxWidth * 0.64f * (16f / 9f))
        val showcaseWidth = showcaseHeight * (9f / 16f)
        // ── Rayos de gloria girando detrás del mural ──────────────────────────
        Canvas(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 0.45f * a }) {
            val c = Offset(size.width / 2f, size.height * 0.40f)
            val maxR = size.width + size.height
            val rays = 18
            for (i in 0 until rays) {
                val ang = ((rayRot + i * 360f / rays) * Math.PI / 180.0)
                val half = (Math.PI / rays) * 0.42
                val p1 = Offset(c.x + (cos(ang - half) * maxR).toFloat(), c.y + (sin(ang - half) * maxR).toFloat())
                val p2 = Offset(c.x + (cos(ang + half) * maxR).toFloat(), c.y + (sin(ang + half) * maxR).toFloat())
                val ray = Path().apply { moveTo(c.x, c.y); lineTo(p1.x, p1.y); lineTo(p2.x, p2.y); close() }
                drawPath(ray, color = adv.accent.copy(alpha = if (i % 2 == 0) 0.16f else 0.07f))
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .graphicsLayer { alpha = a; scaleX = 0.72f + 0.28f * a; scaleY = 0.72f + 0.28f * a },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "¡MURAL COMPLETO!",
                fontSize = 26.sp,
                fontWeight = FontWeight.Black,
                color = MURAL_GOLD,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = adv.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = adv.accent,
                letterSpacing = 3.sp
            )

            Spacer(Modifier.height(18.dp))

            // ── El mural enmarcado, a todo color, con el destello recorriéndolo ──
            Box(
                modifier = Modifier.size(showcaseWidth, showcaseHeight)
            ) {
                PictureFrame(accent = adv.accent) {
                    MuralShowcase(adventure = adv, sweep = sweep, twinkle = twinkle)
                }
            }

            Spacer(Modifier.height(18.dp))

            // ── Recompensa ────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF3A2E0A), Color(0xFF5A4410))))
                    .border(BorderStroke(1.5.dp, MURAL_GOLD.copy(alpha = 0.7f)), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Arte real de la moneda, no el emoji: ver [TeikoinIcon].
                TeikoinIcon(size = 22.dp)
                Text(
                    text = "+$reward",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MURAL_GOLD
                )
                Text(
                    text = "TEIKOINS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MURAL_GOLD.copy(alpha = 0.75f),
                    letterSpacing = 1.sp
                )
            }

            // ── P-250 · El bloque que guardaba este bioma ─────────────────────
            // Se anuncia AQUÍ, en el momento en que ocurre: descubrirlo es el premio del capítulo, y
            // la vitrina solo tiene sentido si el jugador sabe que ya puede ir a por él.
            val newSkin = newBlockSkinId?.let { id -> BlockSkins.all.firstOrNull { it.id == id } }
            if (newSkin != null) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFF12305C), Color(0xFF1C4A8A))))
                        .border(BorderStroke(1.5.dp, Color(0xFF4DB8FF).copy(alpha = 0.7f)), RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val c = newSkin.colorsFor(3)
                    Box(Modifier.size(30.dp).styledTile(newSkin.style, c[0], c[1], c[2], newSkin.cornerFraction))
                    Column {
                        Text(
                            text = "NUEVO BLOQUE DESCUBIERTO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF9FD8FF),
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${newSkin.name} · ya está en la vitrina",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.height(22.dp))

            // ── Botón continuar ───────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(lerp(adv.accent, Color.White, 0.22f), adv.accent, lerp(adv.accent, Color.Black, 0.30f))
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                    .padding(horizontal = 46.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "¡GENIAL!",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/** Fondo final a todo color, con su ruta completa y un destello de celebración. */
@Composable
private fun MuralShowcase(adventure: BiomeAdventure, sweep: Float, twinkle: Float) {
    val positions = remember(adventure.levelCount) { muralNodePositions(adventure.levelCount) }
    Box(modifier = Modifier.fillMaxSize()) {
        RawImage(
            resId = adventure.muralResId,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
            contentDescription = null,
            async = true
        )
        Canvas(modifier = Modifier.matchParentSize()) {
            val nodeRadius = minOf(size.width, size.height) * 0.032f
            positions.zipWithNext().forEach { (from, to) ->
                val p1 = Offset(size.width * from.x, size.height * from.y)
                val p2 = Offset(size.width * to.x, size.height * to.y)
                drawLine(Color.Black.copy(alpha = 0.72f), p1, p2, nodeRadius * 1.35f, StrokeCap.Round)
                drawLine(adventure.accent.copy(alpha = 0.95f), p1, p2, nodeRadius * 0.62f, StrokeCap.Round)
            }
            positions.forEachIndexed { index, point ->
                val center = Offset(size.width * point.x, size.height * point.y)
                val color = adventure.blocks[index].color
                drawCircle(Color.Black.copy(alpha = 0.72f), nodeRadius * 1.32f, center)
                drawCircle(color, nodeRadius, center)

                val phase = (twinkle + index * 0.137f) % 1f
                val sparkle = 1f - abs(phase - 0.5f) * 2f
                if (index % 4 == 0 && sparkle > 0.55f) {
                    val arm = nodeRadius * 1.35f * sparkle
                    val alpha = (sparkle - 0.55f) / 0.45f
                    drawLine(Color.White.copy(alpha = 0.85f * alpha), Offset(center.x - arm, center.y), Offset(center.x + arm, center.y), nodeRadius * 0.18f, StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.85f * alpha), Offset(center.x, center.y - arm), Offset(center.x, center.y + arm), nodeRadius * 0.18f, StrokeCap.Round)
                }
            }

            // Barrido diagonal sobre la ilustración completa.
            val travel = size.width + size.height
            val x = sweep * travel
            drawLine(
                color = Color.White.copy(alpha = 0.25f),
                start = Offset(x - size.height, size.height),
                end = Offset(x, 0f),
                strokeWidth = nodeRadius * 1.25f,
                cap = StrokeCap.Round
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Tira de progreso del mural — para la pantalla de victoria. Muestra cuánto falta
//  para completar el mural del bioma actual y engancha con la cinemática (Fase E):
//  "¡1 nivel para completar el mural!" empuja a seguir jugando el capítulo.
// ════════════════════════════════════════════════════════════════════════════
@Composable
fun MuralProgressStrip(level: Int, modifier: Modifier = Modifier) {
    val chIdx = LevelMap.chapterIndexForLevel(level)
    if (chIdx < 0) return                              // niveles de intro: sin mural
    val adv = ADVENTURES.getOrNull(chIdx) ?: return
    val start = ADVENTURE_START[chIdx]
    val total = adv.levelCount
    val done = (level - start + 1).coerceIn(0, total)  // este nivel recién superado ya cuenta
    val accent = adv.accent

    // Revelado del cuadrito recién ganado: el mural "se rellena" bloque a bloque (estilo Block Blast).
    val reveal = remember(level) { Animatable(0f) }
    LaunchedEffect(level) {
        reveal.snapTo(0f)
        delay(320)                       // tras la entrada de la tarjeta + las estrellas
        reveal.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "MURAL · ${adv.name}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(8.dp))
        // El cuadro pixelart REAL: superados a color, el recién ganado BROTA (escala+alpha+color desde
        // fantasma), el resto en silueta. La rejilla se centra dentro del alto fijo (sin desbordar).
        Canvas(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .height(104.dp)
        ) {
            val cell = minOf(size.width / adv.cols, size.height / adv.rows)
            val boardW = cell * adv.cols; val boardH = cell * adv.rows
            val ox = (size.width - boardW) / 2f; val oy = (size.height - boardH) / 2f
            adv.blocks.forEachIndexed { i, b ->
                val lvl = start + i
                val isNew = lvl == level
                val unlocked = lvl <= level
                val ghost = lerp(b.color, GHOST_SLATE, 0.82f)
                val base = when {
                    !unlocked -> ghost
                    isNew     -> lerp(ghost, b.color, reveal.value)
                    else      -> b.color
                }
                val a  = when { !unlocked -> 0.32f; isNew -> 0.32f + 0.68f * reveal.value; else -> 1f }
                val sc = if (isNew) 0.35f + 0.65f * reveal.value else 1f   // pop del recién ganado
                val s = cell * sc
                val inset = (cell - s) / 2f
                drawBlockTile(
                    topLeft = Offset(ox + cell * b.col + inset, oy + cell * b.row + inset),
                    size = Size(s, s),
                    base = base,
                    light = lerp(base, Color.White, 0.40f),
                    dark = lerp(base, Color.Black, 0.34f),
                    cornerFraction = 0.12f,
                    alpha = a,
                    gapFraction = 0.04f
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        val (msg, hot) = when {
            done >= total      -> "¡MURAL COMPLETO! 🎉" to true
            done == total - 1  -> "¡1 nivel para completar el mural!" to true
            else               -> "$done / $total" to false
        }
        Text(
            text = msg,
            fontSize = if (hot) 13.sp else 12.sp,
            fontWeight = if (hot) FontWeight.ExtraBold else FontWeight.Bold,
            color = if (hot) accent else Color.White.copy(alpha = 0.6f)
        )
    }
}

// (BORRADO 2026-09-25, P-259) El PICKER DE BIOMAS «Elige tu mundo» —`BiomePickerOverlay` con su lista
// de murales (`BiomeListCard`, `LockedBiomeCard`, `MuralThumbnail`)— lo sustituyó el MAPA DE MUNDOS
// (`WorldMap.kt`, 2026-09-23) y ya no se abría desde ningún sitio. Queda `ComingSoonBiomeCard`, que usa
// el mapa.

/**
 * Adelanto del PRÓXIMO mundo (El Abismo) como cinta "PRÓXIMAMENTE" al final del mapa — puro hype.
 * No es jugable. Icono placeholder dibujado en Canvas (tentáculo + onda bioluminiscente) hasta que
 * llegue el arte final; tema azul abisal + cian.
 *
 * Es la RECOMPENSA de terminar el juego: la tarjeta no existe en la lista hasta que el jugador
 * supera el último nivel (lo decide quien la compone), y la primera vez que la ve hace un barrido
 * de luz cian de estreno.
 *
 * @param flash reproducir el destello de estreno (solo la primera vez).
 * @param onFlashShown se llama al terminar el destello para que no vuelva a salir.
 */
@Composable
internal fun ComingSoonBiomeCard(flash: Boolean = false, onFlashShown: () -> Unit = {}) {
    val abyssDeep = Color(0xFF0A2540)
    val abyssCyan = Color(0xFF22D3EE)
    // Latido suave del acento cian (respira) para que la tarjeta llame la atención sin ser molesta.
    // Con «Reducir efectos» el borde no late (análisis a fondo del mapa, 2026-10-01): era la única
    // animación del mapa que seguía corriendo con los efectos reducidos.
    val pulse = if (LocalReducedFx.current) 0.55f else rememberInfiniteTransition(label = "abyssPulse").animateFloat(
        0.35f, 0.75f,
        infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "abyssPulseA"
    ).value
    // Destello de ESTRENO: una banda de luz cian cruza la tarjeta UNA sola vez, la primera vez que
    // el jugador la ve tras terminar el juego. Al acabar avisa para que no vuelva a salir.
    val reveal = remember { Animatable(0f) }
    LaunchedEffect(flash) {
        if (!flash) return@LaunchedEffect
        reveal.snapTo(0f)
        reveal.animateTo(1f, tween(1100, easing = LinearEasing))
        reveal.snapTo(0f)
        onFlashShown()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "El Abismo. Nuevo mundo próximamente"
            }
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF0B1D33), abyssDeep)))
            .border(1.5.dp, abyssCyan.copy(alpha = pulse), RoundedCornerShape(18.dp))
            // El barrido se lee en la fase de DIBUJO (no recompone) y solo vive 1,1 s una vez en la
            // partida, así que el Brush por frame de este único elemento no pesa.
            .drawWithContent {
                drawContent()
                val a = reveal.value
                if (a > 0f) {
                    val band = size.width * 0.32f
                    val x = -band + a * (size.width + band * 2f)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, abyssCyan.copy(alpha = 0.5f), Color.Transparent),
                            startX = x - band,
                            endX = x + band
                        )
                    )
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Miniatura: onda + tentáculo abisal, bioluminiscente.
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF0C2036), Color(0xFF06111F))))
                .border(1.dp, abyssCyan.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                val w = size.width; val h = size.height
                // Ondas del mar (dos senoidales tenues).
                for (k in 0 until 2) {
                    val path = Path()
                    val baseY = h * (0.42f + k * 0.14f)
                    val amp = h * 0.06f
                    path.moveTo(0f, baseY)
                    var x = 0f
                    while (x <= w) {
                        path.lineTo(x, baseY + sin((x / w * 2f + k) * 2f * Math.PI.toFloat()) * amp)
                        x += 3f
                    }
                    drawPath(path, abyssCyan.copy(alpha = 0.5f - k * 0.18f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
                }
                // Tentáculo: curva que sube desde abajo con ventosas (círculos).
                val tx = w * 0.5f
                for (i in 0..6) {
                    val t = i / 6f
                    val cx = tx + sin(t * 3f) * w * 0.16f
                    val cy = h * (0.92f - t * 0.55f)
                    drawCircle(abyssCyan.copy(alpha = 0.30f + pulse * 0.4f), radius = (1f - t) * w * 0.06f + 1.5f, center = Offset(cx, cy))
                }
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("EL ABISMO", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
                // Cinta "PRÓXIMAMENTE".
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(Brush.horizontalGradient(listOf(abyssCyan, Color(0xFF3B82F6))))
                        .padding(horizontal = 7.dp, vertical = 1.dp)
                ) {
                    Text("PRÓXIMAMENTE", fontSize = 9.sp, fontWeight = FontWeight.Black, color = Color(0xFF04121F), letterSpacing = 0.5.sp)
                }
            }
            Text(
                "Cuando el mar suba… tus Teikas dejarán de caer.",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = abyssCyan.copy(alpha = 0.85f)
            )
        }
    }
}
