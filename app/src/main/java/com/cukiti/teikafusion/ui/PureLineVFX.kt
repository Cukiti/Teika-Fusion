package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.model.TeikaType
import com.cukiti.teikafusion.viewmodel.TeikaBody
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs

/**
 * **LOS DOS EFECTOS DEL COLOR** (P-69 + P-70, 2026-08-30).
 *
 * Van juntos en un fichero porque cuentan la misma historia en dos tiempos: la Brocha **pinta** y la
 * Línea Pura **cobra**. Separados de `VFX.kt` a propósito — ese fichero ya pasa de 6.000 líneas y el
 * proyecto lleva tiempo sacando efectos nuevos a ficheros propios (`PlacePopVFX`, `StarRetintVFX`).
 *
 * ## Contrato de consumo
 *
 * Cada uno consume su evento **una sola vez** y nadie más lo mira. Es la regla más pisada de este
 * proyecto: un segundo `consumeEvent` sobre el mismo tipo corta la animación a un fotograma, y el
 * síntoma —«el efecto aparece y desaparece»— manda a buscar el bug al sitio equivocado.
 *
 * ## Rendimiento
 *
 * Un `Canvas` por evento y **el reloj se lee solo en DIBUJO**, nunca en el cuerpo del composable:
 * leerlo arriba recompondría el tablero entero en cada fotograma de la animación (la lección de
 * `project_perf_gamescreen_2026-07-31b`). Sin asignaciones dentro del bucle de dibujo: los colores
 * se resuelven una vez fuera.
 */

private const val BRUSH_SWEEP_MS = 420
private const val PURE_LINE_MS = 380

// ── P-100 · EL COMPÁS DE CADA MARCA AMBIENTAL ──────────────────────────────────────────────────
// Las marcas que viven todo el nivel —como la celda caliente y la portería— laten con el
// reloj COMPARTIDO del tablero (`biomeClockSec`, el mismo que los overlays por celda), no con un
// `rememberInfiniteTransition` propio: tres relojes independientes latían a destiempo y cada uno
// invalidaba su `Canvas` 60 veces por segundo por su cuenta, incluso en PAUSA. Aquí solo quedan sus
// periodos, que sí son distintos a propósito: cada marca dice una cosa distinta y compartir compás
// las habría fundido en un solo parpadeo.
private const val HOT_CELL_BEAT_SEC = 1.8f
private const val COLOR_HALO_BEAT_SEC = 2.6f

/**
 * Latido 0→1→0 de una marca ambiental, leído **en fase de dibujo**.
 *
 * Con [animationsOn] a `false` (pausa, «reducir efectos») devuelve el punto medio: la marca se queda
 * quieta y visible, que es lo que manda el bloque 20 —se congela la animación, no la información—.
 * Además `GameBoard` apaga ahí el propio reloj, así que ni siquiera se invalida el `Canvas`.
 */
internal fun ambientBeat(animationsOn: Boolean, periodSec: Float): Float =
    if (!animationsOn) 0.5f
    else 0.5f + 0.5f * kotlin.math.sin(
        (biomeClockSec.floatValue / periodSec) * 2f * Math.PI.toFloat()
    )

/**
 * **LA BROCHA** (P-70): una ola del color de la ficha barre las filas y columnas que acaba de
 * repintar.
 *
 * La ola viaja —no es un destello plano— porque lo que hay que comunicar es *dirección*: el color
 * salió de la ficha y se extendió por su cruz. Un flash uniforme diría «algo pasó aquí», que es
 * justo lo que no hace falta.
 */
@Composable
fun BrushSweepVFX(
    events: List<GameEvent.BrushSweep>,
    cellSize: Dp,
    gridW: Int,
    gridH: Int,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val skin = LocalBlockSkin.current

    events.forEach { event ->
        key(event.id) {
            val palette = remember(event.id) { skin.colorsFor(event.colorType.coerceAtLeast(0)) }
            val face = palette[0]
            val glow = palette.getOrElse(1) { palette[0] }
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(BRUSH_SWEEP_MS, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val t = clock.value
                    // La banda entra, se queda un instante y se va: el color YA está en el tablero,
                    // así que el efecto solo tiene que señalarlo, no sustituirlo.
                    val fade = if (t < 0.35f) t / 0.35f else 1f - (t - 0.35f) / 0.65f
                    if (fade <= 0f) return@Canvas
                    val boardW = gridW * cellPx
                    val boardH = gridH * cellPx
                    // Cabeza de la ola: recorre el tablero de un extremo al otro.
                    val headX = t * boardW
                    val headY = t * boardH

                    event.rows.forEach { row ->
                        val top = row * cellPx
                        drawRect(
                            color = face.copy(alpha = 0.22f * fade),
                            topLeft = Offset(0f, top),
                            size = Size(boardW, cellPx),
                        )
                        // El brillo que viaja: un tramo corto centrado en la cabeza.
                        val half = cellPx * 1.6f
                        val x0 = (headX - half).coerceIn(0f, boardW)
                        val x1 = (headX + half).coerceIn(0f, boardW)
                        if (x1 > x0) {
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        face.copy(alpha = 0f),
                                        glow.copy(alpha = 0.75f * fade),
                                        face.copy(alpha = 0f),
                                    ),
                                    startX = x0,
                                    endX = x1,
                                ),
                                topLeft = Offset(x0, top),
                                size = Size(x1 - x0, cellPx),
                            )
                        }
                    }
                    event.cols.forEach { col ->
                        val left = col * cellPx
                        drawRect(
                            color = face.copy(alpha = 0.22f * fade),
                            topLeft = Offset(left, 0f),
                            size = Size(cellPx, boardH),
                        )
                        val half = cellPx * 1.6f
                        val y0 = (headY - half).coerceIn(0f, boardH)
                        val y1 = (headY + half).coerceIn(0f, boardH)
                        if (y1 > y0) {
                            drawRect(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        face.copy(alpha = 0f),
                                        glow.copy(alpha = 0.75f * fade),
                                        face.copy(alpha = 0f),
                                    ),
                                    startY = y0,
                                    endY = y1,
                                ),
                                topLeft = Offset(left, y0),
                                size = Size(cellPx, y1 - y0),
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * **LÍNEA PURA** (P-69): la línea de un solo color se enciende de blanco justo antes de romperse.
 *
 * Dura menos que la Brocha y a propósito: cae encima del haz de rotura que ya existe
 * (`LineClearBeamVFX`), así que tiene que **anunciar** y quitarse de en medio, no competir. Lo que
 * de verdad celebra la jugada es el Teika de tier superior que sale a continuación; esto solo dice
 * «esta línea era pura, por eso».
 */
@Composable
fun PureLineVFX(
    events: List<GameEvent.PureLine>,
    cellSize: Dp,
    gridW: Int,
    gridH: Int,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }

    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(PURE_LINE_MS, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val t = clock.value
                    // Un latido: sube rápido y baja. `abs` en vez de dos ramas para no ramificar
                    // dentro del dibujo.
                    val pulse = 1f - abs(t * 2f - 1f)
                    if (pulse <= 0f) return@Canvas
                    val boardW = gridW * cellPx
                    val boardH = gridH * cellPx
                    // Se ensancha al latir: el borde desbordado es lo que la separa de una línea
                    // cualquiera a punto de romperse.
                    val grow = cellPx * 0.22f * pulse
                    if (event.alongX) {
                        val top = event.cell.y * cellPx - grow
                        drawRect(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0f),
                                    Color.White.copy(alpha = 0.85f * pulse),
                                    Color.White.copy(alpha = 0f),
                                ),
                                startY = top,
                                endY = top + cellPx + grow * 2f,
                            ),
                            topLeft = Offset(0f, top),
                            size = Size(boardW, cellPx + grow * 2f),
                        )
                    } else {
                        val left = event.cell.x * cellPx - grow
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.White.copy(alpha = 0f),
                                    Color.White.copy(alpha = 0.85f * pulse),
                                    Color.White.copy(alpha = 0f),
                                ),
                                startX = left,
                                endX = left + cellPx + grow * 2f,
                            ),
                            topLeft = Offset(left, 0f),
                            size = Size(cellPx + grow * 2f, boardH),
                        )
                    }
                }
            }
        }
    }
}

/* (RETIRADO 2026-09-22) `CrossSweepVFX`: el barrido de LA CRUZ. Lo sustituye el estallido de los
 * barriles (P-234), en `HouseRuleReworkVFX.kt`. */

/**
 * **EL REBOTE** (regla de la casa, 2026-09-05): la onda que empuja a los Teikas.
 *
 * La consecuencia de verdad la dibuja la física —los cuerpos salen despedidos y se ve—, así que esta
 * capa no tiene que contar el resultado: tiene que contar la **causa**, y sobre todo su
 * **dirección**. Sin ella el jugador ve cuerpos moviéndose solos justo cuando el tablero está lleno
 * de destellos de línea rota, y lo lee como que la física se ha vuelto loca en vez de como una regla
 * que él acaba de disparar.
 *
 * Por eso son **dos frentes que se separan** desde la línea hacia los dos lados, no un destello sobre
 * ella: la forma dice «de aquí hacia fuera», que es exactamente lo que le ha pasado a los cuerpos. Y
 * por eso los frentes se dibujan **a lo ancho del tablero** y no solo sobre el tramo roto — el
 * empujón alcanza a todos los cuerpos de esa banda, no solo a los que estaban encima de la madera.
 *
 * Espera el mismo `delayMs` que el haz y que el empujón físico: los tres son la misma jugada.
 */
@Composable
fun LineRecoilWaveVFX(
    events: List<GameEvent.LineRecoilWave>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }

    events.forEach { event ->
        key(event.id) {
            val total = event.delayMs + RECOIL_WAVE_MS
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(total.toFloat(), tween(total, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val now = clock.value
                if (now < event.delayMs) return@Canvas
                val t = ((now - event.delayMs) / RECOIL_WAVE_MS.toFloat()).coerceIn(0f, 1f)
                val alpha = (1f - t) * (0.45f + 0.35f * event.strength)
                if (alpha <= 0.01f) return@Canvas
                // Cuánto se han separado los dos frentes. Con la regla al máximo llegan más lejos,
                // que es la única forma que tiene el dial de capítulo de verse.
                val alcance = cellPx * (1.6f + 2.4f * event.strength) * t
                val grosor = cellPx * 0.16f * (1f - t * 0.5f)
                val centro = event.lineIndex * cellPx + cellPx / 2f
                val tinte = Color(0xFFBFE9FF)
                listOf(-1f, 1f).forEach { lado ->
                    val d = centro + lado * alcance
                    if (event.alongX) {
                        drawRect(
                            color = tinte.copy(alpha = alpha),
                            topLeft = Offset(0f, d - grosor / 2f),
                            size = Size(size.width, grosor),
                        )
                    } else {
                        drawRect(
                            color = tinte.copy(alpha = alpha),
                            topLeft = Offset(d - grosor / 2f, 0f),
                            size = Size(grosor, size.height),
                        )
                    }
                }
            }
        }
    }
}

/** Lo que tarda la onda del rebote en abrirse y apagarse. */
private const val RECOIL_WAVE_MS = 420

/* (RETIRADO 2026-09-22) `EchoMarksLayer`: las marcas de EL ECO (P-151). Lo sustituye LA IMPLOSIÓN. */

/* (RETIRADO 2026-09-22) `EchoPayoutVFX`: el cobro del eco. */

/* (RETIRADO 2026-09-22) `PrizedRowsLayer`: LA FILA PREMIADA (P-157a). La sustituye LA PIÑATA. */

/* (RETIRADO 2026-09-22) `ColorCellsLayer`: las celdas de color (P-157c). Las sustituye la vidriera
 * (P-244), dibujada en `HouseRuleReworkVFX.kt`. */

/* (RETIRADO 2026-09-22) `GoalMovedVFX`: la boca que se mudaba (LA PORTERÍA MÓVIL). La sustituyen
 * LOS PORTALES (P-243), en `HouseRuleReworkVFX4.kt`. */

/* (RETIRADO 2026-09-22) `PrizedRowPayoutVFX`: el cobro de la fila premiada. */

/* (RETIRADO 2026-09-22) `TeikaMagnetLayer`: el hilo entre parejas del imán pasivo (P-155). El imán
 * es ahora una ficha que el jugador coloca (P-240) y se dibuja en `HouseRuleReworkVFX.kt`. */

/**
 * **LAS CELDAS CALIENTES, EN REPOSO** (P-78): el anillo dorado que marca dónde vale la pena hacer
 * pasar una línea.
 *
 * Es la capa que hace que la mecánica exista: un premio que no se ve antes de jugarlo no cambia
 * ninguna decisión.
 *
 * **Se dibuja ENCIMA de los bloques**, y es deliberado aunque suene al revés: la celda caliente es
 * una marca del SUELO, no una propiedad del bloque que la tape, así que tiene que seguir viéndose
 * cuando la pisas. Una marca que desaparece al colocar encima dejaría al jugador sin poder planear
 * la línea justo cuando está construyéndola — que es el único momento en que la información sirve.
 *
 * Un **anillo**, deliberadamente distinto de la estrella del objetivo (`TargetStar`): confundir «esto
 * paga puntos» con «esto es lo que el nivel te pide» sería peor que no marcarlo.
 *
 * El latido se apaga con [animationsOn] (bloque 20: en PAUSA se congela la animación AMBIENTE) — el
 * anillo se queda, quieto.
 */
@Composable
fun HotCellsLayer(
    cells: Set<Position>,
    cellSize: Dp,
    animationsOn: Boolean,
    // P-293: las casillas con un bloque encima: ahí el anillo se retira al borde.
    covered: Set<Position> = emptySet(),
) {
    if (cells.isEmpty()) return
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    Canvas(modifier = Modifier.fillMaxSize()) {
        // El reloj se lee AQUÍ, en dibujo, y no en el cuerpo: leerlo arriba recompondría el tablero
        // entero en cada fotograma (project_perf_gamescreen_2026-07-31b). Y es el reloj COMPARTIDO
        // del tablero (P-100), no uno propio: lo avanza un único `withFrameNanos` que `GameBoard`
        // apaga en PAUSA, así que con las animaciones quietas esto no invalida nada.
        val beat = ambientBeat(animationsOn, HOT_CELL_BEAT_SEC)
        // Visto en dispositivo el 2026-08-30: con el oro solo, el anillo se leía **oliva** sobre el
        // azul oscuro del tablero y no decía «premio». Mismo remedio que el halo de los Teikas: un
        // contorno oscuro debajo para que el dorado se apoye en negro y no en el fondo del tablero,
        // y un núcleo claro que lo empuja hacia el amarillo brillante.
        val gold = Color(0xFFFFD45C)
        val goldHot = Color(0xFFFFF0B8)          // el punto caliente del centro
        val r = cellPx * (0.31f + 0.05f * beat)
        val alpha = 0.80f + 0.20f * beat
        cells.forEach { cell ->
            if (cell in covered) {
                drawCellEdgeMark(cell, cellPx, listOf(gold), alpha)
                return@forEach
            }
            val cxp = cell.x * cellPx + cellPx / 2f
            val cyp = cell.y * cellPx + cellPx / 2f
            val center = Offset(cxp, cyp)
            drawCircle(
                color = Color.Black.copy(alpha = 0.45f), radius = r, center = center,
                style = Stroke(width = cellPx * 0.15f),
            )
            drawCircle(
                color = gold.copy(alpha = alpha), radius = r, center = center,
                style = Stroke(width = cellPx * 0.095f),
            )
            // Relleno interior y núcleo: sin ellos el anillo se lee como un agujero, no como algo
            // que vale puntos.
            drawCircle(color = gold.copy(alpha = 0.20f * alpha), radius = r * 0.78f, center = center)
            drawCircle(color = goldHot.copy(alpha = 0.55f + 0.35f * beat), radius = r * 0.20f, center = center)
        }
    }
}

/* (RETIRADO 2026-09-22) `ConveyorLanesLayer`: la fila de LA CINTA. La sustituye el anillo de LA
 * NORIA (P-231), en `HouseRuleReworkVFX.kt`. */

/**
 * **EL COBRO** (P-78): el anillo revienta hacia fuera cuando la línea se lleva la celda.
 *
 * Corto (300 ms) porque cae dentro de la rotura de línea, que ya es el momento más cargado del
 * juego: tiene que decir «ese punto era tuyo» sin pelearse con el haz ni con las partículas.
 */
@Composable
fun HotCellPopVFX(
    events: List<GameEvent.HotCellPop>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(300, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val t = clock.value
                    val gold = Color(0xFFFFD45C)
                    val cxp = event.cell.x * cellPx + cellPx / 2f
                    val cyp = event.cell.y * cellPx + cellPx / 2f
                    drawCircle(
                        color = gold.copy(alpha = (1f - t) * 0.9f),
                        radius = cellPx * (0.30f + 0.55f * t),
                        center = Offset(cxp, cyp),
                        style = Stroke(width = cellPx * 0.10f * (1f - t)),
                    )
                }
            }
        }
    }
}

// (RETIRADO 2026-09-26) `GoalCellLayer` y `GoalDeliveryVFX` — el dibujo de LA PORTERÍA (P-81),
// borrada entera con la regla de nacimientos nueva.

/**
 * **LA CAMALEÓN ADOPTA COLOR** (P-72): las celdas recién estampadas laten una vez en el color que la
 * ficha acaba de tomar del tablero.
 *
 * Es el unico momento en que el jugador puede ver POR QUE salio ese color, asi que el destello va
 * sobre la huella —no sobre los vecinos— y dura poco: la informacion util es «este es tu color»,
 * y la razon la tiene delante en la madera que la rodea.
 */
@Composable
fun ChameleonAdoptVFX(
    events: List<GameEvent.ChameleonAdopt>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val skin = LocalBlockSkin.current

    events.forEach { event ->
        key(event.id) {
            val palette = remember(event.id) { skin.colorsFor(event.colorType.coerceAtLeast(0)) }
            val glow = palette.getOrElse(1) { palette[0] }
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(360, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val t = clock.value
                    val pulse = 1f - abs(t * 2f - 1f)
                    if (pulse <= 0f) return@Canvas
                    val inset = cellPx * 0.06f
                    event.cells.forEach { c ->
                        drawRect(
                            color = glow.copy(alpha = 0.55f * pulse),
                            topLeft = Offset(c.x * cellPx + inset, c.y * cellPx + inset),
                            size = Size(cellPx - inset * 2f, cellPx - inset * 2f),
                        )
                    }
                }
            }
        }
    }
}

/**
 * **LA FICHA ESPEJO** (P-147): el reflejo se anuncia con un destello que **crece desde el eje**.
 *
 * El problema que resuelve es de atención, no de estética: el jugador está mirando el dedo, y la
 * mitad de su jugada acaba de ocurrir **al otro lado del tablero**. Sin nada que le lleve la mirada
 * hasta allí, la mecánica se descubre dos turnos tarde, cuando el tablero ya no se parece a lo que
 * pasó. Es el mismo motivo por el que la corriente dibuja su barrido aunque no mueva un solo bloque.
 *
 * Crece desde el centro hacia fuera —y no un simple parpadeo en cada celda— porque lo que hay que
 * comunicar es **de dónde viene**: esto es un reflejo, no unos bloques que han salido de la nada.
 * Blanco frío y corto (380 ms): compite con el aterrizaje de la ficha, y perder ese duelo sería
 * peor que no dibujarse.
 */
@Composable
fun MirrorStampVFX(
    events: List<GameEvent.MirrorStamp>,
    cellSize: Dp,
    boardWidth: Int,
    onEventComplete: (String) -> Unit,
) {
    if (events.isEmpty()) return
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }

    events.forEach { event ->
        key(event.id) {
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                clock.animateTo(1f, tween(380, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Canvas(modifier = Modifier.fillMaxSize()) {
                val t = clock.value
                val pulse = 1f - abs(t * 2f - 1f)
                if (pulse <= 0f) return@Canvas
                val axisX = boardWidth * cellPx / 2f
                event.cells.forEach { c ->
                    val cx = c.x * cellPx + cellPx / 2f
                    val cy = c.y * cellPx + cellPx / 2f
                    // La celda se ilumina cuando la "onda" que sale del eje la alcanza: las de más
                    // cerca del centro primero. Es lo que hace que el destello se lea como algo que
                    // VIAJA desde el espejo, y no como celdas encendiéndose a la vez.
                    val dist = abs(cx - axisX) / (boardWidth * cellPx / 2f).coerceAtLeast(1f)
                    val local = ((t - dist * 0.35f) / 0.65f).coerceIn(0f, 1f)
                    if (local <= 0f) return@forEach
                    val fade = 1f - abs(local * 2f - 1f)
                    val inset = cellPx * (0.24f - 0.18f * local)
                    drawRect(
                        color = Color.White.copy(alpha = 0.60f * fade),
                        topLeft = Offset(c.x * cellPx + inset, c.y * cellPx + inset),
                        size = Size(cellPx - inset * 2f, cellPx - inset * 2f),
                    )
                }
            }
        }
    }
}

/**
 * **EL TESTARUDO** (P-71): aguanta o cede, y las dos cosas tienen que verse.
 *
 * Resistir dibuja una **sacudida lateral corta** con una grieta que se abre en el color del bloque:
 * el jugador tiene que entender que ese bloque decidio quedarse, no que el juego se olvido de el.
 * Ceder dibuja lo contrario — un estallido hacia fuera en su color, que es la recompensa de haberle
 * llevado lo que pedia.
 *
 * Los dos esperan su [GameEvent.StubbornResist.delayMs] para caer en el FOTOGRAMA DEL IMPACTO de la
 * linea, no en el de la deteccion: es la leccion de `NINJA_CUT_IMPACT_MS` y de la auditoria de
 * eventos de bioma del 2026-08-30.
 */
@Composable
fun StubbornVFX(
    resists: List<GameEvent.StubbornResist>,
    breaks: List<GameEvent.StubbornBreak>,
    cellSize: Dp,
    onEventComplete: (String) -> Unit,
) {
    if (resists.isEmpty() && breaks.isEmpty()) return
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val skin = LocalBlockSkin.current

    resists.forEach { event ->
        key(event.id) {
            val dark = remember(event.id) {
                skin.colorsFor(event.colorType.coerceAtLeast(0)).getOrElse(2) { Color.White }
            }
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                kotlinx.coroutines.delay(event.delayMs.toLong())
                clock.animateTo(1f, tween(420, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val t = clock.value
                    if (t <= 0f) return@Canvas
                    val decay = 1f - t
                    // Tres sacudidas que se apagan: "no me muevo de aqui".
                    val shake = kotlin.math.sin(t * 18f) * cellPx * 0.09f * decay
                    val left = event.cell.x * cellPx + shake
                    val top = event.cell.y * cellPx
                    drawRect(
                        color = dark.copy(alpha = 0.55f * decay),
                        topLeft = Offset(left, top),
                        size = Size(cellPx, cellPx),
                        style = Stroke(width = cellPx * 0.10f),
                    )
                    // La grieta: una diagonal que se abre desde el centro.
                    val half = cellPx * 0.32f * t
                    drawLine(
                        color = Color.White.copy(alpha = 0.75f * decay),
                        start = Offset(left + cellPx / 2f - half, top + cellPx / 2f - half),
                        end = Offset(left + cellPx / 2f + half, top + cellPx / 2f + half),
                        strokeWidth = cellPx * 0.07f,
                    )
                }
            }
        }
    }

    breaks.forEach { event ->
        key(event.id) {
            val face = remember(event.id) {
                skin.colorsFor(event.colorType.coerceAtLeast(0))[0]
            }
            val clock = remember(event.id) { Animatable(0f) }
            LaunchedEffect(event.id) {
                kotlinx.coroutines.delay(event.delayMs.toLong())
                clock.animateTo(1f, tween(360, easing = LinearEasing))
                onEventComplete(event.id)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val t = clock.value
                    if (t <= 0f) return@Canvas
                    drawCircle(
                        color = face.copy(alpha = (1f - t) * 0.9f),
                        radius = cellPx * (0.25f + 0.6f * t),
                        center = Offset(
                            event.cell.x * cellPx + cellPx / 2f,
                            event.cell.y * cellPx + cellPx / 2f,
                        ),
                        style = Stroke(width = cellPx * 0.12f * (1f - t)),
                    )
                }
            }
        }
    }
}
