package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.SlowMotionFocus
import com.cukiti.teikafusion.model.SlowMotionShot

/**
 * **TIEMPO BALA — la capa que se ve.**
 *
 * Apaga la pantalla menos el trozo que hay que mirar, le pone un aro vivo alrededor y escribe cuatro
 * palabras. La cámara lenta de verdad la hace el ViewModel dividiendo el `dt` de la física; esto es
 * lo que dirige la MIRADA, que es la mitad del trabajo: sin foco, ralentizar solo hace que el
 * jugador espere.
 *
 * Detalles que importan:
 *
 *  - **TRAGA TOQUES, menos el que está señalando** (2026-09-01, grabación `analisistutorial`).
 *    Hasta hoy no bloqueaba nada, y se veía: en la lección de los turnos el jugador colocaba fichas
 *    mientras el rótulo le explicaba el medallón, así que la explicación pasaba de largo y el
 *    tablero cambiaba por debajo de su propio foco. Si el juego se para para enseñar algo, se para
 *    de verdad — eso es lo que separa un tutorial premium de un cartel.
 *
 *    La excepción es exacta: lo que está DENTRO de un [focusRects] sigue vivo, porque el foco de la
 *    lección 7 señala justamente el tirador que hay que tocar. Y el gesto decide su destino en el
 *    primer contacto, no fotograma a fotograma: un arrastre que nace en el tirador puede salirse de
 *    su rectángulo sin que se le corte la mano a mitad.
 *
 *    Se traga con `pointerInput` en la pasada `Initial` y no con `clickable {}` — la lección del
 *    bloque 24 sigue en pie: para tragar un toque, nunca `clickable {}`.
 *  - **El agujero se recorta de verdad** (`BlendMode.Clear` sobre una capa offscreen), no se simula
 *    con cuatro rectángulos: el borde redondeado tiene que coincidir con el del tablero o se ve el
 *    truco.
 *  - **El rótulo va al CENTRO** de la pantalla (2026-09-01, pedido del usuario). Pegado a un borde
 *    quedaba fuera de donde el ojo está mirando — que es el trozo iluminado.
 *
 * `focusRects` va en coordenadas de ESTA capa (raíz de la pantalla). Vacío = sin foco: solo se
 * oscurece y se rotula.
 */
@Composable
fun SlowMotionOverlay(
    shot: SlowMotionShot?,
    /**
     * Los trozos de pantalla que se iluminan. **Varios y no uno**: tres celdas calientes repartidas
     * por el tablero, metidas en un solo rectángulo envolvente, iluminan medio tablero — que es
     * exactamente lo mismo que no enfocar nada. Vacío = solo se oscurece y se rotula.
     */
    focusRects: List<Rect>,
    modifier: Modifier = Modifier
) {
    val reduced = LocalReducedFx.current
    val presence = remember { Animatable(0f) }
    LaunchedEffect(shot?.id) {
        if (shot == null) {
            presence.animateTo(0f, tween(160, easing = FastOutSlowInEasing))
        } else {
            presence.animateTo(1f, tween(if (reduced) 90 else 200, easing = FastOutSlowInEasing))
        }
    }
    // ── EL COMPÁS ANTES DE LA FRASE (2026-09-04, pedido del usuario) ───────────────────────────
    // El velo y el foco entran con el shot; el RÓTULO llega [SlowMotionShot.leadInMs] después. En la
    // lección 2 el shot se dispara con la línea rompiéndose, así que la ventanita con «cada fila te
    // deja un Teika» aterrizaba en el mismo fotograma encima de los dos Teikas naciendo y
    // fundiéndose: el jugador leía la frase sin llegar a ver lo que describe.
    //
    // La entrada es la DRAMÁTICA de la casa: sube desde abajo, crece pasándose un pelín y se posa.
    // Con «Reducir efectos» entra sin ceremonia pero respeta el mismo compás — lo que sobra ahí es
    // la parafernalia, no el orden en que se cuentan las cosas.
    val labelIn = remember { Animatable(0f) }
    LaunchedEffect(shot?.id) {
        val live = shot
        if (live == null) {
            labelIn.animateTo(0f, tween(140, easing = FastOutSlowInEasing))
            return@LaunchedEffect
        }
        labelIn.snapTo(0f)
        if (live.leadInMs > 0) kotlinx.coroutines.delay(live.leadInMs.toLong())
        if (reduced) labelIn.snapTo(1f)
        else labelIn.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    }
    // Se sigue dibujando mientras la salida se desvanece: cortar en seco al terminar el shot dejaba
    // un salto de luz en el frame siguiente.
    //
    // Y para eso hace falta RECORDAR el último shot (2026-09-01): con `shot ?: return` a secas, el
    // fotograma en que el shot se apagaba ya no dibujaba nada y la salida animada no llegaba a
    // ocurrir jamás — el velo desaparecía de golpe, que es justo lo que este bloque decía evitar.
    val remembered = remember { mutableStateOf<SlowMotionShot?>(null) }
    if (shot != null) remembered.value = shot
    if (shot == null && presence.value <= 0.001f) return
    val current = shot ?: remembered.value ?: return

    val density = LocalDensity.current
    // Se leen vivos dentro del detector de toques; ver su comentario.
    val liveFocusRects = rememberUpdatedState(focusRects)
    val pulse = if (reduced) 1f else {
        val tr = rememberInfiniteTransition(label = "slowMoPulse")
        val v by tr.animateFloat(
            initialValue = 0.86f, targetValue = 1f,
            animationSpec = infiniteRepeatable(
                tween(760, easing = LinearEasing), RepeatMode.Reverse
            ),
            label = "slowMoPulseV"
        )
        v
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            // Ver el KDoc: mientras el shot está VIVO (no en su desvanecido), la capa se traga todo
            // menos lo que ella misma está señalando.
            .then(
                if (shot == null) Modifier
                // La clave es solo el id del shot: los rectangulos se leen VIVOS desde el gesto
                // (`rememberUpdatedState`), porque son una lista nueva en cada recomposicion y
                // ponerlos de clave reiniciaria el detector —y con el, la memoria de que gesto
                // estaba en curso— varias veces por segundo.
                else Modifier.pointerInput(shot.id) {
                    awaitPointerEventScope {
                        var passThrough = false
                        var seenFirstEvent = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.none { it.pressed }) {
                                passThrough = false
                                seenFirstEvent = true
                                continue
                            }
                            val newGesture = event.changes.any { it.changedToDownIgnoreConsumed() }
                            when {
                                newGesture -> {
                                    val live = liveFocusRects.value
                                    passThrough = event.changes
                                        .filter { it.pressed }
                                        .any { ch -> live.any { it.contains(ch.position) } }
                                }
                                // El shot ha entrado con el DEDO YA APOYADO: ese gesto es anterior a
                                // la explicacion y se le deja terminar. Cortarlo aqui congelaria una
                                // ficha en el aire a media mano, que es peor que la interrupcion que
                                // este bloqueo viene a evitar.
                                !seenFirstEvent -> passThrough = true
                            }
                            seenFirstEvent = true
                            if (!passThrough) event.changes.forEach { it.consume() }
                        }
                    }
                }
            )
    ) {
        val padPx = with(density) { 6.dp.toPx() }
        val cornerPx = with(density) { 18.dp.toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    val p = presence.value
                    if (p <= 0.001f) return@drawWithContent
                    drawRect(Color(0xFF04070F).copy(alpha = 0.66f * p))
                    if (focusRects.isEmpty()) return@drawWithContent
                    // El agujero entra creciendo un pelín: se lee como una lupa posándose.
                    val grow = 1f + (1f - p) * 0.06f
                    // PRIMERO todos los recortes y DESPUÉS todos los aros: con dos focos pegados, un
                    // recorte posterior se comería el aro del anterior.
                    val holes = focusRects.map { r ->
                        val hw = (r.width / 2f + padPx) * grow
                        val hh = (r.height / 2f + padPx) * grow
                        Rect(r.center.x - hw, r.center.y - hh, r.center.x + hw, r.center.y + hh)
                    }
                    holes.forEach { hole ->
                        drawRoundRect(
                            color = Color.Black,
                            topLeft = hole.topLeft,
                            size = hole.size,
                            cornerRadius = CornerRadius(cornerPx),
                            blendMode = BlendMode.Clear
                        )
                    }
                    holes.forEach { hole ->
                        // Aro vivo: es lo que dice «mira AQUÍ» sin escribirlo.
                        drawRoundRect(
                            brush = Brush.linearGradient(
                                listOf(Color(0xFF7CF5FF), Color(0xFFB388FF)),
                                start = hole.topLeft,
                                end = Offset(hole.right, hole.bottom)
                            ),
                            topLeft = hole.topLeft,
                            size = hole.size,
                            cornerRadius = CornerRadius(cornerPx),
                            style = Stroke(width = 3.dp.toPx() * pulse),
                            alpha = p * pulse
                        )
                        drawRoundRect(
                            color = Color(0xFF7CF5FF).copy(alpha = 0.16f * p * pulse),
                            topLeft = Offset(hole.left - padPx, hole.top - padPx),
                            size = Size(hole.width + padPx * 2, hole.height + padPx * 2),
                            cornerRadius = CornerRadius(cornerPx + padPx),
                            style = Stroke(width = 10.dp.toPx())
                        )
                    }
                }
        ) {}

        // **EL RÓTULO VA AL CENTRO** (2026-09-01, pedido del usuario: «que le dé tiempo de leer y que
        // esté en el medio de la pantalla»).
        //
        // Antes huía del foco —arriba o abajo según dónde estuviera— y acababa pegado a un borde,
        // encima de la bandeja o de la barra superior, que es justo donde el ojo NO está: el jugador
        // mira el trozo iluminado y la frase le pasaba por la periferia. En el centro se lee sin
        // buscarla, y como el juego está ralentizado, tapar un trozo del tablero durante esos
        // segundos no cuesta nada — lo que hay que entender en ese momento es la frase.
        Text(
            text = current.label.uppercase(),
            color = Color.White,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 22.dp)
                .clip(RoundedCornerShape(22.dp))
                // Panel sólido y con borde: cae sobre el único trozo de pantalla que NO está
                // oscurecido (el foco), así que tiene que traer su propio fondo o se pierde encima.
                .background(Color(0xF20A0F24))
                .border(1.5.dp, Color(0xFF7CF5FF).copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(horizontal = 22.dp, vertical = 16.dp)
                .graphicsLayer {
                    val enter = labelIn.value
                    alpha = presence.value * enter
                    // Entra creciendo y subiendo: se posa, no aparece. El muelle le da el pasadito
                    // que hace que se lea como un golpe y no como un fundido.
                    val sc = 0.86f + 0.14f * enter
                    scaleX = sc
                    scaleY = sc
                    translationY = 26.dp.toPx() * (1f - enter)
                }
                .semantics {
                    liveRegion = LiveRegionMode.Polite
                    contentDescription = current.label
                }
        )
    }
}

/**
 * Traduce el foco de un [SlowMotionShot] a un rectángulo de la pantalla, en coordenadas de la RAÍZ.
 *
 * Todos los rectángulos de entrada vienen en coordenadas de VENTANA (que es lo que devuelve
 * `boundsInWindow`), y aquí se les resta [rootOffset] de una vez: mezclar los dos sistemas es el
 * error clásico de este archivo y produce un foco desplazado justo lo que mide la barra de estado.
 *
 * Devuelve `null` cuando no hay nada que iluminar (foco [SlowMotionFocus.NONE], o el elemento
 * todavía no se ha medido): el overlay entonces solo oscurece y rotula, que es un estado válido.
 */
fun slowMotionFocusRects(
    shot: SlowMotionShot,
    boardOffset: Offset,
    cellSizePx: Float,
    boardCols: Int,
    boardRows: Int,
    trayRect: Rect,
    /**
     * **El rectángulo de CADA ficha de la bandeja**, por id (P-201, 2026-09-05).
     *
     * Con esto un shot puede señalar la ficha de la que habla en vez de los tres cuadros. Si llega
     * vacío —o el id que pide el shot todavía no se ha medido— se cae a [trayRect], que es el
     * comportamiento de siempre: es preferible iluminar la bandeja entera a no iluminar nada.
     */
    trayPieceRects: Map<String, Rect> = emptyMap(),
    abilityTabRect: Rect,
    goalRect: Rect,
    /**
     * El PANEL DE OBJETIVOS (las estrellas), que es lo que mira [SlowMotionFocus.GOAL].
     *
     * Antes GOAL y MOVES compartían [goalRect], que es la barra de puntos. En la lección 5 —la que
     * enseña justamente el objetivo— esa barra está OCULTA (`showsMoves = false`), así que el
     * rectángulo llegaba en `Rect.Zero` y el shot «esto te pide el nivel» acababa oscureciendo la
     * pantalla entera **sin iluminar nada**: se veía en la grabación `analisistutorial`.
     */
    objectivePanelRect: Rect = Rect.Zero,
    /**
     * El MEDALLÓN de turnos, que es lo que mira [SlowMotionFocus.MOVES]. Iluminar la barra entera
     * señalaba de paso el marcador de puntos, que en esa lección no significa nada.
     */
    movesRect: Rect = Rect.Zero,
    /** El botón del BANCO (P-350), que es lo que mira [SlowMotionFocus.BANK]. */
    bankRect: Rect = Rect.Zero,
    rootOffset: Offset,
): List<Rect> {
    fun toRoot(r: Rect): List<Rect> =
        if (r == Rect.Zero) emptyList() else listOf(r.translate(-rootOffset.x, -rootOffset.y))

    /** El primero que esté medido: cada foco cae hacia el contenedor que lo contiene. */
    fun firstMeasured(vararg candidates: Rect): List<Rect> =
        toRoot(candidates.firstOrNull { it != Rect.Zero } ?: Rect.Zero)

    return when (shot.focus) {
        SlowMotionFocus.NONE -> emptyList()
        SlowMotionFocus.TRAY -> {
            // «Esa ficha» tiene que señalar a ESA ficha. Mismo criterio que las celdas del tablero:
            // un foco que abarca todo cuando el rótulo nombra una sola cosa no señala, despista.
            val pedidas = shot.trayPieceIds.mapNotNull { trayPieceRects[it] }.filter { it != Rect.Zero }
            if (pedidas.isEmpty()) toRoot(trayRect)
            else pedidas.map { it.translate(-rootOffset.x, -rootOffset.y) }
        }
        // Cae a la BANDEJA si el tirador aún no se ha medido (P-203). Es exactamente el fallo que
        // GOAL y MOVES ya tuvieron: sin rectángulo, el shot oscurece la pantalla y no ilumina nada,
        // y le pasa justo a la lección 7 —la que enseña el tirador— si su shot de apertura llega
        // antes que el primer layout del panel. El tirador vive pegado bajo la bandeja, así que
        // señalar la bandeja es impreciso pero nunca es un velo mudo.
        SlowMotionFocus.ABILITY_TAB -> firstMeasured(abilityTabRect, trayRect)
        SlowMotionFocus.GOAL -> firstMeasured(objectivePanelRect, goalRect)
        SlowMotionFocus.MOVES -> firstMeasured(movesRect, goalRect)
        // Sin medir cae a la bandeja, su vecina: impreciso, nunca un velo mudo (ver ABILITY_TAB).
        SlowMotionFocus.BANK -> firstMeasured(bankRect, trayRect)
        SlowMotionFocus.BOARD -> {
            if (cellSizePx <= 0f || boardOffset == Offset.Zero) return emptyList()
            fun cellRect(x0: Int, y0: Int, x1: Int, y1: Int) = Rect(
                left = boardOffset.x + x0 * cellSizePx - rootOffset.x,
                top = boardOffset.y + y0 * cellSizePx - rootOffset.y,
                right = boardOffset.x + (x1 + 1) * cellSizePx - rootOffset.x,
                bottom = boardOffset.y + (y1 + 1) * cellSizePx - rootOffset.y,
            )
            if (shot.cells.isEmpty()) return listOf(cellRect(0, 0, boardCols - 1, boardRows - 1))
            // Se agrupa por TRAMOS HORIZONTALES: una fila de celdas marcadas sale como un solo foco
            // limpio, y tres celdas sueltas repartidas por el tablero salen como tres focos — que es
            // lo que de verdad hay que mirar. Un único rectángulo envolvente ilumina medio tablero.
            buildList {
                shot.cells.groupBy { it.y }.toSortedMap().forEach { (y, cells) ->
                    val xs = cells.map { it.x }.sorted()
                    var runStart = xs.first()
                    var prev = xs.first()
                    xs.drop(1).forEach { x ->
                        if (x != prev + 1) {
                            add(cellRect(runStart, y, prev, y))
                            runStart = x
                        }
                        prev = x
                    }
                    add(cellRect(runStart, y, prev, y))
                }
            }
        }
    }
}
