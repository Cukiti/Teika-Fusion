package com.cukiti.teikafusion.ui

import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.WindowInsets
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.zIndex
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.BiomeType
import com.cukiti.teikafusion.model.LevelMap
import com.cukiti.teikafusion.model.TeikaType
import com.cukiti.teikafusion.model.WorldMapLayout
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.sin

/**
 * **EL MAPA DE MUNDOS** (2026-09-23, pedido del usuario; referencia `art/candycrushlevel.webp` y
 * `art/pixelartmapa.png`). Sustituye a la lista «Elige tu mundo»: un scroll vertical con los 14
 * biomas UNIDOS, del primero (abajo) al último (arriba), y un camino con un nodo por nivel.
 *
 * - **Fondo pintado continuo** (2026-09-24): cada bioma es una franja pintada de 1024 px generada en
 *   Photoshop como UNA cadena (el borde de arriba de un bioma es exactamente el de abajo del
 *   siguiente). [PlaceholderStrip] queda como fondo mientras la franja se decodifica.
 * - **Lo que cambia con el jugador es CÓDIGO**, nunca arte: camino, nodos, estrellas, candados y
 *   tu Teika. Por eso los números nunca pueden salir mal (el fallo de la imagen de referencia).
 * - **Estados de nodo**: superado (dorado + estrellas), el actual (late, con tu Teika botando
 *   encima), bloqueado (apagado). Los capítulos no alcanzados van tapados por una niebla con candado.
 * - **La apertura**: el mapa se «asoma» de frente — llega inclinado hacia atrás y se endereza —, y
 *   se abre ya centrado en el nivel elegido (o en tu frontera).
 *
 * Contrato con quien lo abre, el mismo que tenía el selector: tocar un nivel lo ELIGE
 * ([onSelectLevel]) y cierra; se juega con ¡JUGAR! desde Inicio.
 *
 * ⛔ No es un `Dialog`: es una capa más del `Box` de `AppNavigation`.
 */
@Composable
fun WorldMapOverlay(
    maxUnlockedLevel: Int,
    levelStars: Map<Int, Int>,
    selectedLevel: Int,
    avatarType: TeikaType,
    onSelectLevel: (Int) -> Unit,
    onLevelTap: (Int) -> Unit = {},
    onUiClick: () -> Unit = {},
    onOpenLessons: () -> Unit = {},
    activeCosmetic: String? = null,
    onOpenBlocks: () -> Unit = {},
    /** P-429: moneda en el botón de la vitrina (hay un bloque descubierto que puedes pagar). */
    blocksBadge: Boolean = false,
    /** Terminaste el juego y aún no has visto el destello de «El Abismo» (ver [ComingSoonBiomeCard]). */
    abyssRevealPending: Boolean = false,
    onAbyssRevealShown: () -> Unit = {},
    onDismiss: () -> Unit,
) {
    BackHandler(enabled = true) { onDismiss() }
    val reduced = LocalReducedFx.current
    val frontier = maxUnlockedLevel.coerceIn(LevelMap.introLevels + 1, LevelMap.totalLevels + 1)
    val focus = WorldMapLayout.focusLevel(selectedLevel, maxUnlockedLevel)

    val context = LocalContext.current

    // Un solo reloj para todo el mapa (latido del nodo actual, bote del Teika, destellos), leído
    // en fase de dibujo. Parado con «reducir efectos».
    val clock = remember { mutableFloatStateOf(0f) }
    // A 30 fps en pantallas de 90/120 Hz (análisis a fondo del mapa, 2026-10-01), como el fondo de los
    // biomas (P-368): el latido, el bote y los destellos son decorado y repintaban el mapa entero a la
    // tasa de la pantalla. A 60 Hz, cada fotograma como antes.
    val stepNs = rememberAmbientStepNs()
    if (!reduced) {
        LaunchedEffect(stepNs) {
            val start = withFrameNanos { it }
            while (true) withFrameNanos { now ->
                val t = ambientNs(now - start, stepNs) / 1_000_000_000f
                if (t != clock.floatValue) clock.floatValue = t
            }
        }
    }

    // LA APERTURA: el mapa se asoma de frente (inclinado hacia atrás → derecho).
    val open = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { open.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow)) }

    // La lista NACE en el capítulo del foco (2026-09-30): antes arrancaba en el capítulo 1 y un fotograma
    // después saltaba al tuyo, así que los primeros biomas se componían para nada y lanzaban sus
    // decodificaciones de ~9 MB justo cuando hacía falta la del capítulo que se va a ver.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = LevelMap.chapterIndexForLevel(focus).coerceIn(0, LevelMap.chapters.lastIndex)
    )
    val scope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF070A18))
    ) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val viewportPx = constraints.maxHeight.toFloat()
        // Proporción de cada tramo leída de la CABECERA del WebP (inJustDecodeBounds): no decodifica
        // ningún píxel. Los fondos se cargan de verdad uno a uno, al verse (ver [ChapterStrip]).
        // Una vez por PROCESO (análisis a fondo, 2026-10-01): antes se releían las 14 cabeceras en el hilo
        // principal cada vez que se abría el mapa, justo en el fotograma de la apertura.
        val aspects = remember { MapStripAspects.get(context) }

        // Scroll a un nivel: su capítulo, con el nodo en el centro de la pantalla. Con la lista al
        // revés (capítulo 1 abajo) el desplazamiento se mide desde el pie del tramo.
        suspend fun scrollTo(level: Int, animated: Boolean) {
            val c = LevelMap.chapterIndexForLevel(level).coerceAtLeast(0)
            val node = WorldMapLayout.nodes(c).firstOrNull { it.level == level } ?: return
            val stripPx = widthPx * aspects[c]
            val fromBottom = (1f - node.fy) * stripPx
            val offset = (fromBottom - viewportPx / 2f).roundToInt()
            // P-289: un salto largo animado recorría todos los biomas de en medio —componiéndolos y
            // pidiendo sus fondos— y se notaba a tirones. Si el destino queda a más de un bioma, se
            // salta en seco al de al lado y solo se anima el último tramo.
            if (animated && kotlin.math.abs(listState.firstVisibleItemIndex - c) > 1) {
                val near = if (listState.firstVisibleItemIndex < c) c - 1 else c + 1
                listState.scrollToItem(near.coerceIn(0, LevelMap.chapters.lastIndex), 0)
            }
            if (animated) listState.animateScrollToItem(c, offset.coerceAtLeast(0))
            else listState.scrollToItem(c, offset.coerceAtLeast(0))
        }
        LaunchedEffect(Unit) { scrollTo(focus, animated = false) }

        LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val k = open.value
                    cameraDistance = 14f * density.density
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    rotationX = 32f * (1f - k)
                    val sc = 0.8f + 0.2f * k
                    scaleX = sc; scaleY = sc
                    alpha = k.coerceIn(0f, 1f)
                },
        ) {
            items(LevelMap.chapters.size, key = { it }) { c ->
                ChapterStrip(
                    chapter = c,
                    aspect = aspects[c],
                    artRes = mapArtRes(LevelMap.chapters[c].biome),
                    frontier = frontier,
                    selectedLevel = selectedLevel,
                    levelStars = levelStars,
                    avatarType = avatarType,
                    clock = clock,
                    onNodeTap = { level ->
                        onLevelTap(level)
                        onSelectLevel(level)
                        onDismiss()
                    },
                )
            }
            // En la CIMA del mapa, por encima del último bioma: el adelanto de El Abismo. Solo existe
            // al terminar el juego (la frontera llega a totalLevels + 1) — es la recompensa.
            if (frontier > LevelMap.totalLevels) {
                item(key = "abismo") {
                    Box(Modifier.fillMaxWidth().padding(top = 90.dp, bottom = 24.dp, start = 16.dp, end = 16.dp)) {
                        ComingSoonBiomeCard(flash = abyssRevealPending, onFlashShown = onAbyssRevealShown)
                    }
                }
            }
        }

        // Cabecera: cerrar, título y los dos accesos que ya tenía el selector (lecciones y bloques).
        // La franja del notch / barra de estado va SÓLIDA: con solo el degradado, un nivel que
        // pasaba por detrás asomaba entero por encima de la cabecera (captura art/mapaestado.jpeg).
        Box(
            Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.safeDrawing)
                .background(Color(0xFF070A18))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .background(Brush.verticalGradient(listOf(Color(0xFF070A18), Color(0xCC070A18), Color.Transparent)))
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.10f))
                    .clickable(role = Role.Button) { onUiClick(); onDismiss() }
                    .semantics { contentDescription = "Cerrar el mapa" },
                contentAlignment = Alignment.Center
            ) { RawImage(R.raw.ic_close, Modifier.size(20.dp)) }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                RibbonTitle("Mapa", accent = Color(0xFF4DB8FF), accentDeep = Color(0xFF2A4FB8))
            }
            BlockShowcaseButton(activeCosmetic = activeCosmetic, size = 40.dp, onClick = onOpenBlocks, badge = blocksBadge)
            LessonsHelpButton(onClick = { onUiClick(); onOpenLessons() })
        }

        // P-258 · El capítulo que se está viendo (el del centro de la pantalla). Derivado: solo cambia
        // al pasar de un bioma a otro, no en cada píxel de scroll.
        val viewedChapter by remember(widthPx, viewportPx) {
            derivedStateOf {
                WorldMapLayout.chapterAtCenter(
                    listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, viewportPx
                ) { widthPx * aspects[it] }
            }
        }
        val myLevel = frontier.coerceAtMost(LevelMap.totalLevels)
        val myChapter = LevelMap.chapterIndexForLevel(myLevel).coerceAtLeast(0)

        // Botón derecho · «SUBIR A MI BIOMA»: tu Teika en un botón redondo; te lleva a tu nivel. La
        // flechita dice hacia dónde queda (arriba o abajo) y desaparece cuando ya estás en tu bioma.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF6FC8FF), Color(0xFF2E7FD1))))
                    .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .clickable(role = Role.Button) {
                        onUiClick()
                        scope.launch { scrollTo(myLevel, animated = true) }
                    }
                    .semantics { contentDescription = "Ir a mi nivel" },
                contentAlignment = Alignment.Center
            ) { TeikaIcon(type = avatarType, modifier = Modifier.size(44.dp)) }
            AnimatedVisibility(
                visible = myChapter != viewedChapter,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)
            ) {
                MapArrowBadge(up = myChapter > viewedChapter, fill = Color(0xFFFFD34D))
            }
        }

        // Botón izquierdo · «BAJAR AL NIVEL 1»: vuelve a la entrada del mapa. Solo aparece cuando
        // ya no estás en el primer bioma (allí no llevaría a ningún sitio).
        AnimatedVisibility(
            visible = viewedChapter > 0,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .safeDrawingPadding()
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF8FE39A), Color(0xFF2F9E55))))
                    .border(3.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .clickable(role = Role.Button) {
                        onUiClick()
                        scope.launch { scrollTo(LevelMap.chapterStart[0], animated = true) }
                    }
                    .semantics { contentDescription = "Bajar al nivel 1" },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "1",
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black,
                        style = TextStyle(shadow = Shadow(Color(0xAA000000), Offset(0f, 2f), 0f))
                    )
                    MapChevron(up = false, color = Color.White, modifier = Modifier.size(width = 18.dp, height = 10.dp))
                }
            }
        }

        // Lado derecho, a media altura · EL DESLIZADOR DE BIOMAS: arrastra el dedo para saltar.
        BiomeSlider(
            viewedChapter = { viewedChapter },
            myChapter = myChapter,
            frontier = frontier,
            onGrab = onUiClick,
            onJump = { c -> scope.launch { listState.scrollToItem(c, 0) } },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .height((LevelMap.chapters.size * 21).dp)
        )
    }
}

/**
 * **P-258 · El deslizador de biomas** (pedido del usuario, 2026-09-24): una columna con un punto por
 * capítulo, en el orden del mapa (el primero abajo). Tocar o arrastrar salta a la ENTRADA del bioma
 * que queda bajo el dedo, con un toque háptico al cambiar; mientras arrastras, su cartel sale al lado.
 * El punto grande es el bioma que ves; el anillo dorado, el tuyo; los que aún no has alcanzado van
 * apagados (se puede mirar, la niebla con candado sigue allí).
 */
@Composable
private fun BiomeSlider(
    viewedChapter: () -> Int,
    myChapter: Int,
    frontier: Int,
    onGrab: () -> Unit,
    onJump: (Int) -> Unit,
    modifier: Modifier,
) {
    val accents = remember { LevelMap.chapters.map { accentFor(it.biome) } }
    var dragging by remember { mutableIntStateOf(-1) }
    val grab by rememberUpdatedState(onGrab)
    val jump by rememberUpdatedState(onJump)
    BoxWithConstraints(modifier) {
        val trackH = maxHeight
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(30.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(15.dp))
                .background(Color(0x99070A18))
                .border(1.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(15.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        grab()
                        var c = WorldMapLayout.sliderChapter(down.position.y / size.height)
                        dragging = c
                        jump(c)
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            change.consume()
                            val next = WorldMapLayout.sliderChapter(change.position.y / size.height)
                            if (next != c) {
                                c = next
                                dragging = c
                                Haptics.tap()
                                jump(c)
                            }
                        }
                        dragging = -1
                    }
                }
                .drawWithCache {
                    val ring = Stroke(2.dp.toPx())
                    val goldRing = Stroke(2.5.dp.toPx())
                    val small = 4.5.dp.toPx()
                    val big = 7.5.dp.toPx()
                    val cx = size.width / 2f
                    onDrawBehind {
                        val seen = if (dragging >= 0) dragging else viewedChapter()
                        drawLine(
                            Color.White.copy(alpha = 0.18f),
                            Offset(cx, WorldMapLayout.sliderFraction(accents.lastIndex) * size.height),
                            Offset(cx, WorldMapLayout.sliderFraction(0) * size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                        accents.forEachIndexed { c, accent ->
                            val center = Offset(cx, WorldMapLayout.sliderFraction(c) * size.height)
                            val reached = LevelMap.chapterStart[c] <= frontier
                            val r = if (c == seen) big else small
                            if (c == myChapter) drawCircle(Color(0xFFFFD34D), radius = r + 3.dp.toPx(), center = center, style = goldRing)
                            drawCircle(if (reached) accent else accent.copy(alpha = 0.30f), radius = r, center = center)
                            if (c == seen) drawCircle(Color.White, radius = r, center = center, style = ring)
                        }
                    }
                }
                .semantics { contentDescription = "Deslizador de biomas: arrastra para saltar de mundo" }
        )
        // El cartel del bioma bajo el dedo, a la izquierda de la columna.
        if (dragging >= 0) {
            val chapter = dragging
            val reached = LevelMap.chapterStart[chapter] <= frontier
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-38).dp, y = trackH * WorldMapLayout.sliderFraction(chapter) - 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!reached) RawImage(R.raw.ic_lock, Modifier.size(18.dp).padding(end = 2.dp))
                ChapterSign(
                    chapter = chapter,
                    name = LevelMap.chapters[chapter].biome.displayName,
                    accent = accents[chapter],
                    modifier = Modifier
                )
            }
        }
    }
}

/** La flechita del botón de tu bioma: dice si tu nivel queda por encima o por debajo de lo que ves. */
@Composable
private fun MapArrowBadge(up: Boolean, fill: Color) {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(fill)
            .border(2.dp, Color(0xFF10142A), CircleShape),
        contentAlignment = Alignment.Center
    ) { MapChevron(up = up, color = Color(0xFF10142A), modifier = Modifier.size(width = 12.dp, height = 8.dp)) }
}

/** Un chevrón dibujado (sin icono en `res/raw`): ▲ o ▼ de trazo grueso. */
@Composable
private fun MapChevron(up: Boolean, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.5.dp.toPx()
        val (tipY, baseY) = if (up) stroke / 2 to h - stroke / 2 else h - stroke / 2 to stroke / 2
        drawLine(color, Offset(stroke / 2, baseY), Offset(w / 2, tipY), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(w / 2, tipY), Offset(w - stroke / 2, baseY), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

/**
 * El fondo pintado de cada bioma (Photoshop + Firefly, cadena continua: `tools/ps_cadena_bioma.jsx`).
 * Referencias ESTÁTICAS a propósito: R8 conserva lo que ve referenciado, y un recurso buscado por
 * nombre desaparece en release (ver `ShrinkerKeepContractTest`).
 */
/** Fundido de una ilustración del mapa que llega después del primer fotograma. */
private const val MAP_ART_FADE_MS = 200

/**
 * **Precarga del mapa** (2026-09-30, playtest: al entrar rápido en el mapa se veía el fondo liso y la
 * ilustración aparecía después). Mientras el jugador está en Inicio, decodifica en segundo plano la
 * ilustración del capítulo en el que se abrirá el mapa y la de sus vecinos: al abrirlo ya están en
 * [MapArtCache] y se pintan en el primer fotograma. Caben de sobra en su presupuesto (~4 biomas).
 */
@Composable
fun WorldMapArtPrewarm(maxUnlockedLevel: Int, selectedLevel: Int) {
    val context = LocalContext.current.applicationContext
    val chapter = LevelMap.chapterIndexForLevel(WorldMapLayout.focusLevel(selectedLevel, maxUnlockedLevel))
    LaunchedEffect(chapter) {
        withContext(Dispatchers.IO) {
            listOf(chapter, chapter + 1, chapter - 1)
                .filter { it in LevelMap.chapters.indices }
                .mapNotNull { mapArtRes(LevelMap.chapters[it].biome) }
                .forEach { MapArtCache.load(context, it) }
        }
    }
}

private fun mapArtRes(biome: BiomeType): Int? = when (biome) {
    BiomeType.CRYSTAL -> R.drawable.mapa_01_crystal
    BiomeType.SLEEP -> R.drawable.mapa_02_sleep
    BiomeType.FEAR -> R.drawable.mapa_03_fear
    BiomeType.GARDEN -> R.drawable.mapa_04_garden
    BiomeType.STORM -> R.drawable.mapa_05_storm
    BiomeType.VOLCANO -> R.drawable.mapa_06_volcano
    BiomeType.NINJA -> R.drawable.mapa_07_ninja
    BiomeType.GLACIER -> R.drawable.mapa_08_glacier
    BiomeType.GHOST -> R.drawable.mapa_09_ghost
    BiomeType.GLUTTON -> R.drawable.mapa_10_glutton
    BiomeType.TOXIC -> R.drawable.mapa_11_toxic
    BiomeType.STAR -> R.drawable.mapa_12_star
    BiomeType.KING -> R.drawable.mapa_13_king
    BiomeType.GALACTIC -> R.drawable.mapa_14_galactic
    BiomeType.NONE -> null
}

/** Tamaño del nodo de un nivel. El actual es algo mayor. */
private val NODE = 46.dp
private val NODE_CURRENT = 56.dp

/**
 * Un tramo del mapa: el fondo del bioma, sus destellos, el camino, los nodos, el cartel del
 * capítulo en la entrada y, si aún no has llegado, la niebla con candado.
 */
@Composable
private fun ChapterStrip(
    chapter: Int,
    aspect: Float,
    artRes: Int?,
    frontier: Int,
    selectedLevel: Int,
    levelStars: Map<Int, Int>,
    avatarType: TeikaType,
    clock: State<Float>,
    onNodeTap: (Int) -> Unit,
) {
    val biome = LevelMap.chapters[chapter].biome
    val accent = accentFor(biome)
    val nodes = remember(chapter) { WorldMapLayout.nodes(chapter) }
    val locked = LevelMap.chapterStart[chapter] > frontier
    // El fondo se decodifica SOLO cuando el tramo entra en pantalla (LazyColumn compone lo visible)
    // y fuera del hilo principal. RGB_565: la mitad de memoria que ARGB y sin alfa que perder; los 14
    // a 1024 px en ARGB serían ~250 MB. Mientras carga, el fondo provisional del bioma.
    // P-289: en LOSAS y con memoria (ver [MapArtCache]). Si ya está en la caché se pinta en el primer
    // fotograma, sin pasar por el provisional; si no, se decodifica en IO. Después se precargan los
    // biomas de al lado: al deslizar hasta ellos, ya están.
    val context = LocalContext.current
    val art by produceState(artRes?.let { MapArtCache.peek(it) }, artRes) {
        val res = artRes ?: return@produceState
        if (value == null) value = withContext(Dispatchers.IO) { MapArtCache.load(context, res) }
        withContext(Dispatchers.IO) {
            listOf(chapter - 1, chapter + 1)
                .filter { it in LevelMap.chapters.indices }
                .mapNotNull { mapArtRes(LevelMap.chapters[it].biome) }
                .forEach { MapArtCache.load(context, it) }
        }
    }
    // Si la ilustración ya estaba en memoria se pinta entera desde el primer fotograma. Si llega tarde
    // (bioma lejano al deslizar rápido) entra con un fundido corto SOBRE el fondo provisional, en vez de
    // saltar de golpe del fondo liso a la imagen (playtest 2026-09-30: «se ve el fondo y no la imagen»).
    val reveal = remember(artRes) { Animatable(if (art != null) 1f else 0f) }
    LaunchedEffect(art != null) {
        if (art != null && reveal.value < 1f) reveal.animateTo(1f, tween(MAP_ART_FADE_MS))
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val w = maxWidth
        val h = w * aspect
        Box(Modifier.fillMaxWidth().height(h)) {
            val tiles = art
            // El provisional se queda DEBAJO hasta que la imagen es opaca del todo.
            if (tiles == null || reveal.value < 1f) PlaceholderStrip(accent, Modifier.fillMaxSize())
            if (tiles != null) {
                // Una capa propia: el fondo se graba una vez y los destellos no lo arrastran. El alfa del
                // fundido va en la capa (se lee al dibujar, no recompone).
                Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = reveal.value }) {
                    val sy = size.height / tiles.height
                    var y = 0f
                    tiles.tiles.forEach { tile ->
                        val th = tile.height * sy
                        drawImage(
                            tile,
                            dstOffset = IntOffset(0, y.roundToInt()),
                            dstSize = IntSize(size.width.roundToInt(), (y + th).roundToInt() - y.roundToInt()),
                            filterQuality = FilterQuality.Medium,
                        )
                        y += th
                    }
                }
            }
            // Capa propia: se redibujan cada fotograma y, sin ella, obligaban a regrabar la lista
            // ENTERA del mapa (fondos, caminos, nodos y textos) sesenta veces por segundo.
            Sparkles(accent, chapter, clock, Modifier.fillMaxSize().graphicsLayer())
            TrailPath(nodes, frontier, accent, Modifier.fillMaxSize())

            nodes.forEachIndexed { idx, n ->
                val current = n.level == frontier
                // El Teika se pone al lado CONTRARIO al que sigue el camino: encima del nodo tapaba
                // al siguiente cuando quedaba justo arriba.
                val next = nodes.getOrNull(idx + 1)
                val avatarSide = if (next == null || next.fx >= n.fx) -1 else 1
                val size = if (current) NODE_CURRENT else NODE
                Box(
                    Modifier
                        .offset(x = w * n.fx - size / 2, y = h * n.fy - size / 2)
                        // El actual, al frente: si no, el nodo siguiente tapaba al Teika que bota encima.
                        .zIndex(if (current) 1f else 0f)
                ) {
                    LevelNode(
                        level = n.level,
                        state = when {
                            n.level < frontier -> NodeState.DONE
                            current -> NodeState.CURRENT
                            else -> NodeState.LOCKED
                        },
                        stars = levelStars[n.level] ?: 0,
                        selected = n.level == selectedLevel,
                        accent = accent,
                        size = size,
                        avatarType = avatarType,
                        avatarSide = avatarSide,
                        clock = clock,
                        onTap = { onNodeTap(n.level) },
                    )
                }
            }

            // Cartel del capítulo, en la entrada del bioma (abajo del tramo).
            ChapterSign(
                chapter = chapter,
                name = biome.displayName,
                accent = accent,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
            )

            if (locked) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xCC070A18), Color(0xB3070A18), Color(0xCC070A18))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(64.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.55f))
                                .border(2.dp, accent.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) { RawImage(R.raw.ic_lock, Modifier.size(34.dp)) }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            biome.displayName.uppercase(),
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 16.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp
                        )
                        if (chapter > 0) {
                            Text(
                                "Supera ${LevelMap.chapters[chapter - 1].biome.displayName} para entrar",
                                color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class NodeState { DONE, CURRENT, LOCKED }

/**
 * Un nivel del camino: moneda con canto (la cara sobre un labio oscuro), el número que ve el
 * jugador y, debajo, sus estrellas. El actual late y lleva tu Teika botando encima.
 */
@Composable
private fun LevelNode(
    level: Int,
    state: NodeState,
    stars: Int,
    selected: Boolean,
    accent: Color,
    size: Dp,
    avatarType: TeikaType,
    /** -1 = el Teika a la izquierda del nodo, 1 = a la derecha. */
    avatarSide: Int,
    clock: State<Float>,
    onTap: () -> Unit,
) {
    val (faceTop, faceBottom, lip) = when (state) {
        // Superados en el color de SU bioma (cada mundo se ve distinto; quince dorados seguidos eran
        // una pared monótona) y el actual en ORO: es el único especial de la pantalla.
        NodeState.DONE -> Triple(accent, accent.copy(red = accent.red * 0.55f, green = accent.green * 0.55f, blue = accent.blue * 0.65f), Color(0xFF14204A))
        NodeState.CURRENT -> Triple(Color(0xFFFFE27A), Color(0xFFF0A81C), Color(0xFF8A5B06))
        NodeState.LOCKED -> Triple(Color(0xFF4A5378), Color(0xFF30375A), Color(0xFF191E38))
    }
    val press = remember { MutableInteractionSource() }
    val pressed by press.collectIsPressedAsState()
    val enabled = state != NodeState.LOCKED
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(size)
                .graphicsLayer {
                    val beat = if (state == NodeState.CURRENT) 1f + 0.06f * sin(clock.value * 4f) else 1f
                    val sc = beat * if (pressed && enabled) 0.9f else 1f
                    scaleX = sc; scaleY = sc
                }
                .drawBehind {
                    val r = this.size.minDimension / 2f
                    // Anillo que se expande: el actual llama la atención desde lejos.
                    if (state == NodeState.CURRENT) {
                        val k = (clock.value * 0.8f) % 1f
                        drawCircle(Color.White, radius = r * (1f + 0.6f * k), alpha = 0.5f * (1f - k), style = Stroke(3.dp.toPx()))
                    }
                    if (selected) drawCircle(Color.White, radius = r + 4.dp.toPx(), style = Stroke(3.dp.toPx()))
                    // Canto, cara y brillo.
                    drawCircle(lip, radius = r, center = Offset(center.x, center.y + 4.dp.toPx()))
                    drawCircle(Brush.verticalGradient(listOf(faceTop, faceBottom)), radius = r)
                    drawCircle(Color.Black.copy(alpha = 0.35f), radius = r, style = Stroke(2.dp.toPx()))
                    // Anillo blanco: separa el nodo de CUALQUIER fondo (los superados de Cristal son
                    // azules sobre una cueva azul y se perdían).
                    drawCircle(
                        Color.White.copy(alpha = if (state == NodeState.LOCKED) 0.35f else 0.9f),
                        radius = r - 1.dp.toPx(), style = Stroke(2.5.dp.toPx())
                    )
                    drawCircle(Color.White.copy(alpha = 0.30f), radius = r * 0.55f, center = Offset(center.x - r * 0.2f, center.y - r * 0.35f))
                }
                .clip(CircleShape)
                .then(
                    if (enabled) Modifier.clickable(interactionSource = press, indication = null, role = Role.Button, onClick = onTap)
                    else Modifier
                )
                .semantics {
                    contentDescription = "Nivel ${LevelMap.displayNumber(level)}" + when (state) {
                        NodeState.DONE -> ", $stars estrellas"
                        NodeState.CURRENT -> ", tu siguiente nivel"
                        NodeState.LOCKED -> ", bloqueado"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${LevelMap.displayNumber(level)}",
                color = if (state == NodeState.LOCKED) Color.White.copy(alpha = 0.45f) else Color.White,
                fontWeight = FontWeight.Black,
                fontSize = if (state == NodeState.CURRENT) 19.sp else 16.sp,
                style = TextStyle(shadow = Shadow(Color(0xAA000000), Offset(0f, 2f), 0f))
            )
        }
        // Estrellas colgando del nodo superado.
        if (state == NodeState.DONE) {
            Row(
                Modifier.align(Alignment.BottomCenter).offset(y = 14.dp),
                horizontalArrangement = Arrangement.spacedBy((-2).dp)
            ) {
                repeat(3) { i ->
                    RawImage(
                        R.raw.ic_star,
                        Modifier.size(14.dp).graphicsLayer { alpha = if (i < stars) 1f else 0.25f }
                    )
                }
            }
        }
        // Tu Teika encima del actual, botando.
        if (state == NodeState.CURRENT) {
            Box(
                Modifier
                    .align(if (avatarSide < 0) Alignment.CenterStart else Alignment.CenterEnd)
                    .offset(x = (avatarSide * 48).dp, y = (-18).dp)
                    .graphicsLayer {
                        val b = kotlin.math.abs(sin(clock.value * 3.2f))
                        translationY = -10.dp.toPx() * b
                        scaleY = 1f - 0.06f * (1f - b); scaleX = 1f + 0.04f * (1f - b)
                    }
            ) { TeikaIcon(type = avatarType, modifier = Modifier.size(44.dp)) }
        }
    }
}

/**
 * El camino entre nodos: curvas en S de un nodo al siguiente, con borde oscuro y traza de tierra.
 * Lo recorrido brilla en dorado; lo que falta, apagado y a trazos. Geometría cacheada por tamaño.
 */
@Composable
private fun TrailPath(nodes: List<WorldMapLayout.Node>, frontier: Int, accent: Color, modifier: Modifier) {
    Canvas(
        modifier.drawWithCache {
            val segments = nodes.zipWithNext().map { (a, b) ->
                val p0 = Offset(a.fx * size.width, a.fy * size.height)
                val p1 = Offset(b.fx * size.width, b.fy * size.height)
                val midY = (p0.y + p1.y) / 2f
                Path().apply {
                    moveTo(p0.x, p0.y)
                    cubicTo(p0.x, midY, p1.x, midY, p1.x, p1.y)
                } to (b.level <= frontier)
            }
            val outline = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round)
            val body = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
            val dash = Stroke(
                width = 4.dp.toPx(), cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 9.dp.toPx()))
            )
            onDrawBehind {
                segments.forEach { (p, _) -> drawPath(p, Color(0xB3070A18), style = outline) }
                segments.forEach { (p, walked) ->
                    drawPath(p, if (walked) Color(0xFFE8C77A) else Color(0xFF4A5378), style = body)
                }
                segments.forEach { (p, walked) ->
                    drawPath(p, if (walked) Color(0xFFFFF3C4) else accent.copy(alpha = 0.35f), style = dash)
                }
            }
        }
    ) {}
}

/**
 * Destellos que suben despacio por el tramo: la cueva respira sin pedir sprites. Posiciones fijas
 * por capítulo (semilla), solo cambia la fase con el reloj.
 */
@Composable
private fun Sparkles(accent: Color, chapter: Int, clock: State<Float>, modifier: Modifier) {
    val seeds = remember(chapter) {
        val rnd = kotlin.random.Random(chapter * 7919 + 13)
        List(22) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.4f + rnd.nextFloat(), rnd.nextFloat() * 6.28f) }
    }
    Canvas(modifier) {
        val t = clock.value
        val px = size.width / 180f   // un «píxel» del arte
        seeds.forEach { (x, y, speed, phase) ->
            val yy = ((y - t * 0.012f * speed) % 1f + 1f) % 1f
            val tw = 0.5f + 0.5f * sin(t * 2.2f * speed + phase)
            val s = px * (if (tw > 0.8f) 2f else 1f)
            drawRect(
                if (tw > 0.8f) Color.White else accent,
                topLeft = Offset(x * size.width, yy * size.height),
                size = Size(s, s),
                alpha = 0.25f + 0.6f * tw
            )
        }
    }
}

/**
 * Fondo PROVISIONAL de un bioma sin arte: degradado de su color, paredes oscuras a los lados y un
 * grano de píxeles, todo a la rejilla de 180 px para que no desentone con los tramos pixelados.
 */
@Composable
private fun PlaceholderStrip(accent: Color, modifier: Modifier) {
    Box(
        modifier.drawWithCache {
            val px = size.width / 180f
            val rnd = kotlin.random.Random(accent.hashCode())
            val grains = List(420) {
                Offset((rnd.nextInt(180) * px), (rnd.nextFloat() * size.height / px).toInt() * px)
            }
            val base = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.30f).compositeOver(Color(0xFF0B1024)), Color(0xFF0B1024), accent.copy(alpha = 0.22f).compositeOver(Color(0xFF0B1024)))
            )
            val wall = Brush.horizontalGradient(
                0f to Color(0xE6050814), 0.22f to Color.Transparent, 0.78f to Color.Transparent, 1f to Color(0xE6050814)
            )
            onDrawBehind {
                drawRect(base)
                grains.forEachIndexed { i, o ->
                    drawRect(accent, topLeft = o, size = Size(px * (1 + i % 2), px * (1 + i % 2)), alpha = 0.18f)
                }
                drawRect(wall)
            }
        }
    )
}

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f
    )
}

/** El cartel de entrada de cada capítulo: «CAPÍTULO N · NOMBRE» en una placa del color del bioma. */
@Composable
private fun ChapterSign(chapter: Int, name: String, accent: Color, modifier: Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.7f))))
            .border(2.dp, Color(0xFF10142A), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "CAP. ${chapter + 1}",
            color = Color(0xFF10142A), fontSize = 11.sp, fontWeight = FontWeight.Black
        )
        Spacer(Modifier.width(6.dp))
        Text(
            name.uppercase(),
            color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Black, letterSpacing = 0.8.sp,
            style = TextStyle(shadow = Shadow(Color(0x99000000), Offset(0f, 2f), 0f))
        )
    }
}

/** Proporción alto/ancho de cada tramo del mapa, leída de la cabecera del WebP una sola vez. */
private object MapStripAspects {
    @Volatile private var cached: List<Float>? = null

    fun get(context: android.content.Context): List<Float> = cached ?: synchronized(this) {
        cached ?: LevelMap.chapters.indices.map { c ->
            val res = mapArtRes(LevelMap.chapters[c].biome)
            val aspect = res?.let {
                val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeResource(context.resources, it, o)
                if (o.outWidth > 0) o.outHeight.toFloat() / o.outWidth else null
            }
            WorldMapLayout.stripAspect(c, aspect)
        }.also { cached = it }
    }
}
