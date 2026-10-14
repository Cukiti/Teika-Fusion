package com.cukiti.teikafusion.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.LevelObjective
import com.cukiti.teikafusion.model.TargetColor
import com.cukiti.teikafusion.model.TeikaType
import com.cukiti.teikafusion.ui.theme.DarkSurface

// ── LAS MEDIDAS DEL PANEL DE OBJETIVOS, EN UN SOLO SITIO ─────────────────────────────────────
// Estaban repartidas por el cuerpo de [GameInfoPanel] y de [TargetChip]. Se juntan aquí porque
// [tutorialHudSlotHeight] tiene que poder reservar EXACTAMENTE lo que el panel mide (P-89), y dos
// copias del mismo número se separan a la primera.
private val OBJECTIVE_STAR_WIDE = 52.dp
private val OBJECTIVE_STAR_TIGHT = 40.dp
private val OBJECTIVE_TEXT_WIDE = 22.sp
private val OBJECTIVE_TEXT_TIGHT = 18.sp
/** El contador se monta un pelo sobre la estrella (`spacedBy(-2.dp)`). */
private val OBJECTIVE_CHIP_OVERLAP = 2.dp
private val OBJECTIVE_PANEL_VPAD = 6.dp
/** P-443: la barrita de progreso bajo cada contador (y su hueco). La cuenta el alto de las lecciones. */
private val OBJECTIVE_BAR_HEIGHT = 4.dp
/** Relleno sobre la barra; la columna del chip ya resta [OBJECTIVE_CHIP_OVERLAP]: quedan 2 dp de aire. */
private val OBJECTIVE_BAR_GAP = 4.dp
/** Caja de línea del contador, clavada para que el «✓» no cambie el alto. Ver [TargetChip]. */
private const val OBJECTIVE_TEXT_LINE_FACTOR = 1.25f

/**
 * **El alto que el HUD reserva durante las SIETE LECCIONES** (P-89, 2026-09-02, grabación
 * `analisistutorial`).
 *
 * Cada lección enseña UNA cosa —decisión de la 22ª pasada, y sigue en pie—, así que la 5 dibuja el
 * panel de estrellas y la 6 la barra con el medallón, nunca las dos. El efecto secundario era que al
 * pasar de una a otra el bloque superior se sustituía por otro de distinto alto y **el tablero
 * entero saltaba**, con la bandeja detrás: exactamente lo que la regla de la casa prohíbe (la info
 * contextual NUNCA empuja layout).
 *
 * La solución no es enseñar las dos cosas a la vez, es **reservar el hueco**: el bloque mide siempre
 * lo que mide el más alto de los dos (el panel de estrellas con un chip) y dentro se centra lo que
 * la lección toque. Así el tablero está en el mismo píxel en las siete lecciones.
 *
 * Se calcula, no se escribe a mano: `.toDp()` sobre el `sp` del contador sigue la escala de fuente
 * del sistema, así que el hueco tampoco se queda corto con el tamaño de letra al máximo (1,3).
 */
@Composable
fun tutorialHudSlotHeight(): Dp {
    val density = LocalDensity.current
    val counter = with(density) { (OBJECTIVE_TEXT_WIDE * OBJECTIVE_TEXT_LINE_FACTOR).toDp() }
    return OBJECTIVE_STAR_WIDE - OBJECTIVE_CHIP_OVERLAP + counter +
        OBJECTIVE_BAR_GAP - OBJECTIVE_CHIP_OVERLAP + OBJECTIVE_BAR_HEIGHT + OBJECTIVE_PANEL_VPAD * 2
}

@Composable
fun GameInfoPanel(
    levelObjective: LevelObjective?,
    targetsCollected: Map<TargetColor, Int>,
    megaRequired: Boolean = false,
    megaFormed: Boolean = false,
    /** P-263: las estrellas ya están y solo falta el MEGA → su chip late y lo dice. */
    megaPending: Boolean = false,
    // Cada incremento = un aviso "¡faltan estrellas!" (Mega formado sin completar el nivel). El panel
    // se tiñe de ROJO y hace una sacudida con vibración. Se dispara una sola vez por nivel (VM).
    nudgePulse: Int = 0,
    objectivePresentationActive: Boolean = false,
    revealedTargetColors: Set<TargetColor> = emptySet(),
    megaObjectiveRevealed: Boolean = true,
    landingImpactToken: Int = 0,
    landingImpactColor: TargetColor? = null,
    landingImpactIsMega: Boolean = false,
    onCounterPositioned: ((TargetColor, androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onMegaPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    /**
     * El panel ENTERO, para que el TIEMPO BALA pueda iluminarlo ([SlowMotionFocus.GOAL]). La lección
     * que enseña el objetivo señalaba la barra de puntos, que en esa lección ni siquiera se dibuja.
     */
    onPanelPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    if (levelObjective == null) return

    // ── Aviso "faltan estrellas": rojo + sacudida + vibración ──────────────────────────────────
    // shake = desplazamiento horizontal amortiguado (vaivén decreciente); redPulse = intensidad del
    // teñido/borde rojo (1→0). Ambos arrancan al cambiar [nudgePulse] (>0 evita el disparo inicial).
    val shake    = remember { Animatable(0f) }
    val redPulse = remember { Animatable(0f) }
    val landingImpact = remember { Animatable(0f) }
    val effectsOn = LocalAnimationsActive.current && !LocalReducedFx.current
    val view = androidx.compose.ui.platform.LocalView.current
    LaunchedEffect(nudgePulse) {
        if (nudgePulse <= 0) return@LaunchedEffect
        // Vibración de "algo va mal" (dos toques secos) — háptica premium ya usada en el proyecto.
        com.cukiti.teikafusion.ui.Haptics.perform(
            view, android.view.HapticFeedbackConstants.LONG_PRESS
        )
        launch {
            redPulse.snapTo(1f)
            redPulse.animateTo(0f, tween(1100, easing = FastOutSlowInEasing))
        }
        launch {
            // Vaivén: 6 medios ciclos con amplitud decreciente (16→0 dp), rápido y "nervioso".
            val amps = listOf(16f, -13f, 10f, -7f, 4f, 0f)
            for (a in amps) shake.animateTo(a, tween(55, easing = FastOutSlowInEasing))
        }
    }
    LaunchedEffect(landingImpactToken, effectsOn) {
        if (landingImpactToken <= 0 || !effectsOn) {
            landingImpact.snapTo(0f)
            return@LaunchedEffect
        }
        landingImpact.snapTo(1f)
        landingImpact.animateTo(0f, tween(310, easing = FastOutSlowInEasing))
    }
    // (Auditoría 2026-10-01) El rojo del aviso se lee al DIBUJAR: leído aquí, cada fotograma de su
    // 1,1 s recomponía el panel entero (y sus chips).
    val landingAccent = if (landingImpactIsMega) Color(0xFFFFD54F)
    else landingImpactColor?.let(::targetChipColor) ?: Color.White

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { onPanelPositioned?.invoke(it.boundsInWindow()) }
            .graphicsLayer {
                val impact = landingImpact.value
                translationX = shake.value
                translationY = impact * 2.5f
                val s = 1f + impact * if (landingImpactIsMega) 0.035f else 0.022f
                scaleX = s
                scaleY = s
            }
            .padding(horizontal = 8.dp)
            .drawWithCache {
                val radius = 10.dp.toPx()
                val stroke = 2.dp.toPx()
                onDrawWithContent {
                    // Fondo y borde del aviso «faltan estrellas»: el borde late dos veces en rojo
                    // dentro del decaimiento para que «vibre» también en color.
                    val redInt = redPulse.value
                    val redBlink = if (redInt > 0.01f)
                        kotlin.math.abs(kotlin.math.sin(redInt * Math.PI * 4).toFloat()) * redInt
                    else 0f
                    drawRoundRect(
                        color = lerp(DarkSurface, Color(0xFF7A1020), 0.55f * redInt),
                        cornerRadius = CornerRadius(radius, radius)
                    )
                    if (redBlink > 0f) {
                        drawRoundRect(
                            color = Color(0xFFFF3B4E).copy(alpha = redBlink),
                            topLeft = Offset(stroke / 2f, stroke / 2f),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = stroke)
                        )
                    }
                    drawContent()
                    val impact = landingImpact.value
                    if (impact > 0f) {
                        drawRoundRect(
                            color = landingAccent.copy(alpha = impact * 0.34f),
                            cornerRadius = CornerRadius(radius, radius),
                            style = Stroke(width = (2f + impact * 5f).dp.toPx())
                        )
                        drawRoundRect(
                            color = Color.White.copy(alpha = impact * impact * 0.12f),
                            cornerRadius = CornerRadius(radius, radius)
                        )
                    }
                }
            }
            .padding(horizontal = 12.dp, vertical = OBJECTIVE_PANEL_VPAD),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Icono arriba + cantidad restante debajo: ocupa menos ancho y se lee de un vistazo.
        val chipCount   = levelObjective.required.size + if (megaRequired) 1 else 0
        val starSize    = if (chipCount >= 4) OBJECTIVE_STAR_TIGHT else OBJECTIVE_STAR_WIDE
        val textSize    = if (chipCount >= 4) OBJECTIVE_TEXT_TIGHT else OBJECTIVE_TEXT_WIDE
        val chipSpacing = if (chipCount >= 4) 7.dp else 14.dp
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(chipSpacing)
        ) {
            levelObjective.required.forEach { (color, needed) ->
                val got = targetsCollected.getOrDefault(color, 0).coerceAtMost(needed)
                key(color) {
                    TargetChip(
                        color = color,
                        needed = needed,
                        got = got,
                        starSize = starSize,
                        textSize = textSize,
                        revealed = !objectivePresentationActive || color in revealedTargetColors,
                        onCounterPositioned = onCounterPositioned
                    )
                }
            }
            if (megaRequired) {
                MegaObjectiveChip(
                    formed = megaFormed,
                    pending = megaPending && !megaFormed,
                    iconSize = starSize,
                    textSize = textSize,
                    revealed = !objectivePresentationActive || megaObjectiveRevealed,
                    onPositioned = onMegaPositioned,
                )
                // (P-444, 2026-10-01) La etiqueta «¡SOLO FALTA EL MEGA!» ya no va aquí, como un
                // elemento más de la fila: al aparecer empujaba los chips de lado. Ahora flota sobre
                // el propio chip del Mega (ver [MegaObjectiveChip]).
            }
        }
    }
}

/**
 * Barra de progreso hacia la META de puntuación del nivel (inspirada en Block Blast Adventure). Se
 * llena con el score hacia el umbral de 3★ ([com.cukiti.teikafusion.model.LevelLayouts.threeStarScore]),
 * con pips en 2★ y 3★ que se encienden al alcanzarlos. Solo en NIVELES.
 *
 * **EL MEDALLÓN DE TURNOS VA ENCIMA, CENTRADO** (2026-08-01, pedido del usuario). Los turnos eran
 * un chip más en la fila de objetivos y empujaban a los demás; aquí no pueden descuadrar nada
 * —flotan sobre el centro exacto de la barra— y quedan justo donde el ojo ya está mirando: los dos
 * recursos del nivel, «cuánto llevo» y «cuánto me queda», en un solo golpe de vista.
 *
 * Acabado premium: cápsula oscura, halo tintado y un aro que se vacía como un reloj. **Solo el
 * número, sin rótulo** (2026-08-01) — ver [MovesMedallion] para el detalle de sus efectos.
 */
@Composable
fun ScoreGoalBar(
    score: Int,
    level: Int,
    /** Turnos que quedan (2026-07-31c). `movesTotal <= 0` = nivel sin límite → no se pinta. */
    movesLeft: Int = 0,
    movesTotal: Int = 0,
    companionId: String? = null,
    companionLevel: Int = 1,
    /**
     * ¿Se pinta la barra de PUNTOS hacia las 3★? En la lección de los turnos no: ahí la barra
     * anunciaba una meta de 3843 puntos que ese tablero no puede dar ni pretende — la lección
     * enseña el medallón, y todo lo demás en pantalla le compite por la mirada.
     */
    showScoreGoal: Boolean = true,
    /** El MEDALLÓN de turnos, para que el TIEMPO BALA lo ilumine solo a él y no la barra entera. */
    onMovesPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Umbral desde la fuente única (LevelLayouts) — antes esta barra llevaba su propia copia.
    //
    // **UN SOLO PIP** (2026-08-01). Aquí había otro para las 2★, cuando eran un umbral de puntos
    // menor. Desde que la 2ª estrella se paga con TURNOS SOBRANTES y no con puntuación
    // ([LevelLayouts.starsFor]), pintar su marca en una barra de puntos señalaría un sitio que ya no
    // significa nada. La condición de la 2★ vive donde se mide: el medallón de turnos.
    val goal3 = com.cukiti.teikafusion.model.LevelLayouts
        .threeStarScore(level, companionId, companionLevel)
        .coerceAtLeast(1)
    val anim by animateFloatAsState(
        targetValue = (score.toFloat() / goal3).coerceIn(0f, 1f),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "scoreGoal"
    )
    val track   = Color(0xFF20263F)
    val litGold = Color(0xFFFFD54F)
    val dimPip  = Color(0xFF55608A)
    val showMoves = movesTotal > 0

    // El medallón sobresale por arriba y por abajo de la barra: el Box reserva su alto para que no
    // se recorte ni empuje al tablero (regla del proyecto: la info contextual NUNCA empuja layout).
    Box(
        modifier = modifier
            .fillMaxWidth()
            // 46dp = el alto exacto del medallón: el Box le reserva sitio para que ni se recorte ni
            // empuje al tablero (regla del proyecto: la info contextual NUNCA empuja layout).
            .height(if (showMoves) 46.dp else 18.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                // La barra es un Canvas + un "320/890" crudo: un solo nodo con la meta explicada.
                .semantics(mergeDescendants = true) {
                    contentDescription =
                        "Progreso hacia las tres estrellas: $score de $goal3 puntos"
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (showScoreGoal) Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
            ) {
                val w = size.width; val h = size.height; val r = h / 2f
                drawRoundRect(track, size = Size(w, h), cornerRadius = CornerRadius(r, r))
                val fw = w * anim
                if (fw > 1f) {
                    drawRoundRect(
                        brush = Brush.horizontalGradient(listOf(Color(0xFF4DB8FF), Color(0xFF69F0AE))),
                        size = Size(fw.coerceAtLeast(h), h),
                        cornerRadius = CornerRadius(r, r)
                    )
                }
                // Pip de 3★ al final de la barra: dorado al alcanzarlo, apagado si no.
                run {
                    val lit = anim >= 0.999f
                    val center = Offset(w - r, h / 2f)
                    drawCircle(if (lit) litGold else dimPip, radius = h * 0.42f, center = center)
                    drawCircle(Color.White.copy(alpha = if (lit) 0.85f else 0.3f), radius = h * 0.16f, center = center)
                }
            }
            // P-442: la meta es la de la 3.ª ESTRELLA, y una cifra sola no lo decía.
            if (showScoreGoal) Text(
                text = "★",
                color = if (anim >= 0.999f) litGold else litGold.copy(alpha = 0.6f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
            if (showScoreGoal) Text(
                text = "$score/$goal3",
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
        }
        if (showMoves) MovesMedallion(
            left = movesLeft,
            total = movesTotal,
            onPositioned = onMovesPositioned
        )
    }
}

/**
 * **TURNOS RESTANTES**, el medallón que corona la barra de meta. Ver [ScoreGoalBar] para el porqué
 * de que viva aquí y no como un chip más del panel de objetivos.
 *
 * **SOLO EL NÚMERO** (2026-08-01, pedido del usuario). El rótulo «TURNOS» iba a 6sp para caber: a
 * ese tamaño no se lee, así que no informaba a nadie y solo le robaba sitio al único dato que
 * importa. Lo que antes decía la palabra ahora lo dice la FORMA — un aro que se vacía es un
 * contador que se agota, y eso se entiende sin leer. Para TalkBack sigue estando entero en el
 * `contentDescription`: quitar texto de la pantalla no es quitarlo de la accesibilidad.
 *
 * ## Las animaciones son del GASTO, no del reloj
 *
 * Nada late por su cuenta. Cada efecto lo dispara que el jugador consuma un turno, y muere:
 *
 *  · **El aro se vacía** — arco de progreso `left/total` con un tween de 420 ms. Es el efecto
 *    principal y el que sustituye al rótulo.
 *  · **Golpe del número** — un muelle 1 → 1,22 → 1 en el fotograma en que cambia la cifra.
 *  · **Onda del halo** — un anillo que se expande hacia fuera y se desvanece, para que el gasto se
 *    vea también de reojo, sin mirar el número.
 *  · **Brillo del aro** — un flash blanco sobre el trazo que decae en 300 ms.
 *
 * Deliberadamente **sin latido continuo** cuando quedan pocos turnos: el HUD ya tiene el pulso rojo
 * del aviso de objetivos y dos cosas palpitando a la vez no dicen ninguna (lección del bloque 21).
 * La escasez se comunica por TINTE —ámbar bajo 6, rojo bajo 3— y porque el aro está casi vacío.
 *
 * Respeta [LocalAnimationsActive] (en PAUSA no se pide un fotograma) y [LocalReducedFx] (sin
 * efectos, el número salta al valor y ya). Perf: todo el medallón es UN `drawWithCache` — los
 * `Brush` se construyen al cambiar el tamaño o el tinte, no en cada fotograma, y las animaciones se
 * leen dentro del bloque de DIBUJO, así que gastar un turno no recompone nada.
 */
@Composable
private fun MovesMedallion(
    left: Int,
    total: Int,
    onPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val tinte = when {
        left <= 2 -> Color(0xFFFF3B4E)
        left <= 5 -> Color(0xFFFFB300)
        else      -> Color(0xFFFFD54F)
    }
    val tinteAnim by animateColorAsState(tinte, tween(300), label = "movesTint")
    val efectosOn = LocalAnimationsActive.current && !LocalReducedFx.current

    // Fracción del aro. Se anima siempre (no es un bucle: es una transición de estado), pero con
    // efectos reducidos salta de golpe.
    val fraccion by animateFloatAsState(
        targetValue = if (total > 0) (left.toFloat() / total).coerceIn(0f, 1f) else 0f,
        animationSpec = if (efectosOn) tween(420, easing = FastOutSlowInEasing) else tween(0),
        label = "movesArc"
    )

    // Un solo Animatable manda los tres efectos del gasto: 0 = reposo, 1 = justo gastado. Uno solo
    // en vez de tres evita tres corrutinas por turno y los mantiene sincronizados por construcción.
    val golpe = remember { Animatable(0f) }
    var primerValor by remember { mutableStateOf(true) }
    LaunchedEffect(left) {
        // El primer valor no es un gasto: es el nivel abriendo. Animarlo dispararía la onda al
        // levantar el telón, cuando el jugador todavía no ha hecho nada.
        if (primerValor) { primerValor = false; return@LaunchedEffect }
        if (!efectosOn) return@LaunchedEffect
        golpe.snapTo(1f)
        golpe.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
    }

    Box(
        modifier = Modifier
            .size(46.dp)
            .onGloballyPositioned { onPositioned?.invoke(it.boundsInWindow()) }
            .semantics(mergeDescendants = true) {
                // El número está solo en pantalla; aquí va la frase completa.
                val queda = if (left == 1) "Te queda 1 turno de $total." else "Te quedan $left turnos de $total."
                contentDescription = if (left <= 5) "¡Atención! $queda" else queda
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithCache {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val rAro = r * 0.82f
                    val grosor = r * 0.13f
                    val cuerpo = Brush.verticalGradient(
                        colors = listOf(Color(0xFF232C52), Color(0xFF0B1024)),
                        startY = c.y - rAro,
                        endY = c.y + rAro
                    )
                    // Barrido del aro: el degradado va del tinte a una versión clara del mismo, así
                    // el arco tiene volumen sin necesidad de un shader por fotograma.
                    val arco = Brush.sweepGradient(
                        colors = listOf(tinteAnim, lerp(tinteAnim, Color.White, 0.55f), tinteAnim),
                        center = c
                    )
                    val esquina = Size(rAro * 2f, rAro * 2f)
                    val topLeft = Offset(c.x - rAro, c.y - rAro)

                    onDrawBehind {
                        val g = golpe.value

                        // 1) Onda del gasto: anillo que se expande y se apaga.
                        if (g > 0f) {
                            drawCircle(
                                color = tinteAnim.copy(alpha = 0.34f * g),
                                radius = r * (0.85f + (1f - g) * 0.55f),
                                center = c,
                                style = Stroke(width = grosor * (0.4f + g * 0.8f))
                            )
                        }

                        // 2) Halo fijo que despega el medallón del tablero de detrás.
                        drawCircle(tinteAnim.copy(alpha = 0.16f), radius = r, center = c)

                        // 3) Cuerpo oscuro: el número tiene que leerse sobre cualquier bioma.
                        drawCircle(brush = cuerpo, radius = rAro, center = c)

                        // 4) Carril del aro (lo ya gastado) + arco de lo que queda, desde arriba y
                        //    en sentido horario: se vacía como un reloj.
                        drawCircle(
                            color = Color.White.copy(alpha = 0.07f),
                            radius = rAro, center = c, style = Stroke(width = grosor)
                        )
                        if (fraccion > 0f) {
                            drawArc(
                                brush = arco,
                                startAngle = -90f,
                                sweepAngle = 360f * fraccion,
                                useCenter = false,
                                topLeft = topLeft,
                                size = esquina,
                                style = Stroke(width = grosor, cap = StrokeCap.Round)
                            )
                        }

                        // 5) Brillo del aro al gastar, encima del arco.
                        if (g > 0f) {
                            drawCircle(
                                color = Color.White.copy(alpha = 0.5f * g * g),
                                radius = rAro, center = c, style = Stroke(width = grosor * 0.55f)
                            )
                        }
                    }
                }
        )
        Text(
            text = "$left",
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            color = tinteAnim,
            // El golpe se lee en la CAPA GRÁFICA, no en el cuerpo del composable: gastar un turno
            // reescala el texto ya medido en vez de recomponerlo y volver a medirlo.
            modifier = Modifier.graphicsLayer {
                val s = 1f + golpe.value * 0.22f
                scaleX = s
                scaleY = s
            }
        )
    }
}

/** Color premium de cada objetivo (para el destello/anillo al recibir una estrella). */
private fun targetChipColor(tc: TargetColor): Color = when (tc) {
    TargetColor.RED    -> Color(0xFFFF5A5A)
    TargetColor.BLUE   -> Color(0xFF4DB8FF)
    TargetColor.PURPLE -> Color(0xFFB57EDC)
    TargetColor.PINK   -> Color(0xFFFF8FD0)
}

/** Nombre en español del color del objetivo (accesibilidad del chip). */
private fun targetColorName(tc: TargetColor): String = when (tc) {
    TargetColor.RED    -> "roja"
    TargetColor.BLUE   -> "azul"
    TargetColor.PURPLE -> "morada"
    TargetColor.PINK   -> "rosa"
}

/** El mismo color en plural («27 estrellas rojas»): lo usa también el panel del nivel. */
internal fun targetColorNamePlural(tc: TargetColor): String = when (tc) {
    TargetColor.RED    -> "rojas"
    TargetColor.BLUE   -> "azules"
    TargetColor.PURPLE -> "moradas"
    TargetColor.PINK   -> "rosas"
}

/**
 * Chip de un objetivo: estrella + contador. Cuando una estrella ATERRIZA aquí (sube [got], que ahora
 * refleja las estrellas YA llegadas), se reproduce una animación PREMIUM de "absorción": la estrella del
 * chip pulsa (absorbe la que llega), el número hace un pop de escala + destello del color del objetivo,
 * y un anillo del color se expande y se desvanece detrás.
 */
@Composable
private fun TargetChip(
    color: TargetColor,
    needed: Int,
    got: Int,
    starSize: Dp,
    textSize: TextUnit,
    revealed: Boolean,
    onCounterPositioned: ((TargetColor, androidx.compose.ui.geometry.Rect) -> Unit)?,
) {
    val remaining = (needed - got).coerceAtLeast(0)
    val done      = remaining == 0
    val accent    = targetChipColor(color)

    val punch     = remember { Animatable(1f) }   // pop de escala del número
    val starPulse = remember { Animatable(1f) }   // la estrella del chip "absorbe" la que llega
    val flash     = remember { Animatable(0f) }   // destello de color del número + anillo (1→0)
    val revealImpact = remember { Animatable(0f) }
    val effectsOn = LocalAnimationsActive.current && !LocalReducedFx.current
    var prevGot by remember { mutableIntStateOf(got) }
    var wasRevealed by remember { mutableStateOf(revealed) }
    LaunchedEffect(got) {
        if (got > prevGot) {
            prevGot = got
            launch {
                punch.snapTo(1f); punch.animateTo(1.30f, tween(90))
                punch.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
            }
            launch {
                starPulse.snapTo(1.30f)
                starPulse.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            }
            launch { flash.snapTo(1f); flash.animateTo(0f, tween(440, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
        } else {
            prevGot = got
            // got NO aumentó → normalmente es el reset al pasar de nivel. Si el jugador tocó
            // "siguiente nivel" mientras el destello aún corría, este LaunchedEffect se reinicia y
            // cancela la corrutina del flash a medias, dejando el anillo CONGELADO (círculo pegado
            // en el panel de objetivos). Devolvemos todo a reposo para que no quede nada dibujado.
            flash.snapTo(0f)
            punch.snapTo(1f)
            starPulse.snapTo(1f)
        }
    }
    LaunchedEffect(revealed, effectsOn) {
        if (revealed && !wasRevealed && effectsOn) {
            revealImpact.snapTo(1f)
            revealImpact.animateTo(0f, tween(360, easing = FastOutSlowInEasing))
        } else if (!revealed || !effectsOn) {
            revealImpact.snapTo(0f)
        }
        wasRevealed = revealed
    }

    val counterIconSize = with(LocalDensity.current) { textSize.toDp() }
    val counterLineHeight = with(LocalDensity.current) {
        (textSize * OBJECTIVE_TEXT_LINE_FACTOR).toDp()
    }
    Box(contentAlignment = Alignment.Center) {
        // Los dos impactos se leen en dibujo: ni la llegada inicial ni una estrella recogida
        // recomponen el chip en cada frame.
        Canvas(modifier = Modifier.matchParentSize()) {
            val f = flash.value
            if (f > 0.01f) {
                val base = size.minDimension
                val r = base * (0.45f + (1f - f) * 0.55f)
                drawCircle(accent.copy(alpha = 0.22f * f), r * 0.9f, center)
                drawCircle(accent.copy(alpha = 0.6f * f), r, center, style = Stroke(width = base * 0.06f))
            }
            val reveal = revealImpact.value
            if (reveal > 0f) {
                val base = size.minDimension
                val radius = base * (0.48f + (1f - reveal) * 0.68f)
                drawCircle(accent.copy(alpha = 0.28f * reveal), radius, center)
                drawCircle(
                    Color.White.copy(alpha = 0.70f * reveal),
                    radius,
                    center,
                    style = Stroke(width = base * 0.055f * reveal.coerceAtLeast(0.25f))
                )
            }
        }
        val accessibilityModifier = if (revealed) {
            Modifier.semantics(mergeDescendants = true) {
                contentDescription = if (done)
                    "Objetivo de estrella ${targetColorName(color)} completado"
                else
                    if (remaining == 1) "Objetivo: falta 1 estrella de color ${targetColorName(color)}"
                    else "Objetivo: faltan $remaining estrellas de color ${targetColorName(color)}"
            }
        } else Modifier.clearAndSetSemantics { }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(-OBJECTIVE_CHIP_OVERLAP),
            modifier = Modifier
                .alpha(if (done) 0.62f else 1f)
                .graphicsLayer {
                    val impact = revealImpact.value
                    alpha = if (revealed) 1f else 0f
                    val s = 1f + impact * 0.14f
                    scaleX = s
                    scaleY = s
                }
                .then(accessibilityModifier)
        ) {
            // El slot existe y se mide incluso cuando el dibujo no: así el vuelo conoce su destino
            // sin mostrar una copia previa ni mover el panel cuando aterriza.
            Box(
                modifier = Modifier
                    .size(starSize)
                    .onGloballyPositioned { coords ->
                        onCounterPositioned?.invoke(color, coords.boundsInWindow())
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (revealed) {
                    TargetStar(
                        targetColor = color,
                        modifier = Modifier
                            .matchParentSize()
                            .graphicsLayer {
                                scaleX = starPulse.value
                                scaleY = starPulse.value
                            }
                    )
                }
            }
            // ALTO CLAVADO (2026-09-01, grabación `analisistutorial`). Al cumplirse el objetivo este
            // texto pasa de un dígito a «✓», y las métricas del palómetro del check son más altas que
            // las de una cifra: el chip crecía, con él el panel, y el tablero entero y la bandeja
            // daban un salto hacia abajo justo en el instante de la victoria. Con `trim = None` la
            // caja de línea mide EXACTAMENTE `lineHeight` sea cual sea el glifo, así que la marca
            // entra sin mover un píxel — regla de la casa: la info contextual NUNCA empuja layout.
            Box(
                modifier = Modifier
                    .height(counterLineHeight)
                    .graphicsLayer { scaleX = punch.value; scaleY = punch.value },
                contentAlignment = Alignment.Center,
            ) {
                if (done) {
                    RawImage(R.raw.ic_check, Modifier.size(counterIconSize))
                } else {
                    // El destello de color al recoger: la cifra blanca y, encima, la misma en el color
                    // del objetivo con su alfa en CAPA. Antes se leía `flash.value` en composición y
                    // cada estrella recogida recomponía el chip 440 ms por fotograma.
                    CounterText("$remaining", textSize, Color.White)
                    CounterText("$remaining", textSize, accent, Modifier.graphicsLayer { alpha = flash.value })
                }
            }
            ObjectiveProgressBar(
                fraction = if (needed > 0) got.toFloat() / needed else 1f,
                color = accent,
                width = starSize * 0.8f,
            )
        }
    }
}

/**
 * **P-443 · LA BARRITA DEL CONTADOR.** Con 27–34 estrellas por color, una cifra que baja de 31 a 30
 * no dice cuánto falta de un vistazo; una barra que se llena sí. Fina, del color del objetivo y del
 * mismo ancho para todos los chips. El llenado se anima y se lee en DIBUJO (no recompone el chip).
 * La usan las estrellas y el Mega (que va de vacío a lleno al formarse), para que la fila no se
 * desalinee.
 */
@Composable
private fun ObjectiveProgressBar(fraction: Float, color: Color, width: Dp) {
    val fill = animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "objectiveBar"
    )
    Canvas(
        Modifier
            .padding(top = OBJECTIVE_BAR_GAP)
            .size(width = width, height = OBJECTIVE_BAR_HEIGHT)
    ) {
        val r = CornerRadius(size.height / 2f, size.height / 2f)
        drawRoundRect(Color.White.copy(alpha = 0.14f), cornerRadius = r)
        val w = size.width * fill.value
        if (w > 0.5f) drawRoundRect(color, size = Size(w.coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}

/**
 * La cifra de un contador del panel, con la caja de línea CLAVADA ([OBJECTIVE_TEXT_LINE_FACTOR] y
 * `trim = None`): la usan las estrellas y el Mega, para que el «✓» de cualquiera de los dos entre sin
 * mover un píxel. Una sola copia (antes el Mega no la tenía y el panel saltaba al formarlo).
 */
@Composable
private fun CounterText(text: String, textSize: TextUnit, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontSize = textSize,
        lineHeight = textSize * OBJECTIVE_TEXT_LINE_FACTOR,
        fontWeight = FontWeight.Black,
        color = color,
        modifier = modifier,
        style = androidx.compose.ui.text.TextStyle(
            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.None
            )
        ),
    )
}

/** Condición de victoria Mega, siempre visible junto a las estrellas y con cantidad debajo. */
@Composable
private fun MegaObjectiveChip(
    formed: Boolean,
    /** P-263: solo falta el MEGA — late con halo dorado (la etiqueta va a su lado, en el panel). */
    pending: Boolean,
    iconSize: Dp,
    textSize: TextUnit,
    revealed: Boolean,
    onPositioned: ((androidx.compose.ui.geometry.Rect) -> Unit)?,
) {
    val pulse = remember { Animatable(1f) }
    val statusIconSize = with(LocalDensity.current) { textSize.toDp() }
    val counterLineHeight = with(LocalDensity.current) { (textSize * OBJECTIVE_TEXT_LINE_FACTOR).toDp() }
    val revealImpact = remember { Animatable(0f) }
    val effectsOn = LocalAnimationsActive.current && !LocalReducedFx.current
    var previous by remember { mutableStateOf(formed) }
    var wasRevealed by remember { mutableStateOf(revealed) }
    LaunchedEffect(formed) {
        if (formed && !previous) {
            pulse.snapTo(1.28f)
            pulse.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
        previous = formed
    }
    LaunchedEffect(revealed, effectsOn) {
        if (revealed && !wasRevealed && effectsOn) {
            revealImpact.snapTo(1f)
            revealImpact.animateTo(0f, tween(440, easing = FastOutSlowInEasing))
        } else if (!revealed || !effectsOn) {
            revealImpact.snapTo(0f)
        }
        wasRevealed = revealed
    }
    // P-444: la etiqueta flota sobre la cifra del chip mientras solo falta el Mega (en ese momento
    // la cifra «1» no dice nada que la etiqueta no diga mejor).
    val showPendingLabel = pending && !formed && revealed
    val accessibilityModifier = if (revealed) {
        Modifier.semantics(mergeDescendants = true) {
            contentDescription = buildString {
                append(
                    when {
                        formed -> "Objetivo Mega Teika completado"
                        pending -> "Estrellas completadas: solo falta formar un Mega Teika"
                        else -> "Objetivo: falta formar un Mega Teika"
                    }
                )
            }
        }
    } else Modifier.clearAndSetSemantics { }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(-OBJECTIVE_CHIP_OVERLAP),
        modifier = Modifier
            .alpha(if (formed) 0.62f else 1f)
            .graphicsLayer {
                val impact = revealImpact.value
                alpha = if (revealed) 1f else 0f
                translationY = impact * 2f
                val s = 1f + impact * 0.18f
                scaleX = s
                scaleY = s
            }
            .then(accessibilityModifier)
    ) {
        // Igual que las estrellas: slot medible estable, Lottie desmontado mientras no es visible.
        Box(
            modifier = Modifier
                .size(iconSize)
                .onGloballyPositioned { onPositioned?.invoke(it.boundsInWindow()) }
                // P-263: halo dorado que late mientras solo falta el MEGA. Reloj compartido, leído
                // en dibujo; quieto con «reducir efectos».
                .drawBehind {
                    if (!pending) return@drawBehind
                    val beat = if (effectsOn) 0.5f + 0.5f * kotlin.math.sin(biomeClockSec.floatValue * 5f) else 1f
                    drawCircle(
                        Color(0xFFFFD54F).copy(alpha = 0.25f + 0.35f * beat),
                        radius = size.minDimension * (0.55f + 0.12f * beat)
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            if (revealed) {
                TeikaIcon(
                    type = TeikaType.MEGA_TEIKA,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value }
                )
            }
        }
        // ALTO CLAVADO, como el de las estrellas (auditoría 2026-10-01): sin él, el «1» y el «✓»
        // medían distinto y al formar el Mega el panel —y el tablero detrás— daban un salto.
        Box(
            modifier = Modifier
                .height(counterLineHeight)
                .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
            contentAlignment = Alignment.Center,
        ) {
            if (formed) RawImage(R.raw.ic_check, Modifier.size(statusIconSize))
            else if (showPendingLabel) {
                // Más ancha que el chip: se sale por los lados sin empujar nada (los vecinos, en ese
                // momento, ya están todos con su ✓).
                Text(
                    text = "¡SOLO FALTA\nEL MEGA!",
                    fontSize = 10.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF1A1030),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    softWrap = false,
                    modifier = Modifier
                        .wrapContentSize(unbounded = true)
                        .background(Color(0xFFFFD54F), RoundedCornerShape(7.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            else CounterText("1", textSize, Color.White)
        }
        ObjectiveProgressBar(
            fraction = if (formed) 1f else 0f,
            color = Color(0xFFFFD54F),
            width = iconSize * 0.8f,
        )
    }
}
