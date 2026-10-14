package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.model.Position
import kotlin.math.PI
import kotlin.math.sin

/**
 * NIVELES — preview de rotura estilo BLOCK BLAST (ver res/raw/blockblast.jpeg): mientras el
 * jugador arrastra una ficha sobre una posición que COMPLETA fila(s)/columna(s), cada línea
 * "se enciende" con un BARRIDO que nace en la huella de la ficha y corre hacia los extremos:
 *  • TINTE translúcido del COLOR DE LA FICHA sobre cada bloque — nunca un repintado opaco: el
 *    bloque real se sigue viendo debajo, así que conserva su relieve 3D y la estrella, el
 *    cronómetro o la cáscara de cristal se leen a través (ver la nota del rework 2026-07-20b).
 *  • Un destello claro viaja en el frente del barrido, celda a celda.
 *  • Bordes NEÓN a lo largo de la línea, que entran con el barrido: núcleo blanco + halo del color.
 * TODO el dibujo queda CONTENIDO dentro del tablero (ver res/raw/lineasobrepasa.png — la versión
 * anterior sangraba por los bordes): sin sangrado, remates planos (Butt) y clipToBounds de red.
 * Soporta cualquier combinación de filas + columnas simultáneas.
 *
 * PERF (60 fps): solo se monta cuando hay líneas que romper (0 costo el resto del drag). Un único
 * reloj (InfiniteTransition) leído DENTRO de onDrawBehind → invalida solo la fase de dibujo, sin
 * recomposición. El halo son 3 trazos concéntricos (sin BlurMaskFilter ni Paint de framework —
 * mucho más barato en gama baja) y la geometría se cachea en drawWithCache; 0 allocs/frame.
 */
/** Duración del barrido del tinte de punta a punta de la línea. */
private const val WIPE_MS = 220
/**
 * Ancho del frente del barrido, en celdas: cuántas celdas hay a la vez a medio encender. >1 suaviza
 * el avance (si fuese 1 las celdas se encenderían de golpe, una a una, y se leería escalonado).
 */
private const val WIPE_FEATHER = 1.7f

@Composable
fun LineClearPreviewLayer(
    dragState: DragState,
    comboStreak: Int,
    cellSizePx: Float,
    modifier: Modifier = Modifier,
    // Celdas CERRADAS/no visibles (lava del Volcán, mordisco del Glotón): el aviso de rotura NO se
    // dibuja sobre ellas — sólo llega hasta donde el tablero sigue visible e interactuable.
    coveredCells: Set<Position> = emptySet(),
    // Celdas que NO se tiñen: su dibujo ES la información (comodín arcoíris, bobina numerada, hielo)
    // o hay un CUERPO de Teika debajo, al que esta capa —que va encima— ensuciaría.
    specialCells: Set<Position> = emptySet()
) {
    val dragging = dragState as? DragState.Dragging ?: return
    if (!dragging.isValidPlacement || cellSizePx <= 0f) return
    if (dragging.clearRowSpans.isEmpty() && dragging.clearCols.isEmpty()) return
    // Origen del BARRIDO: el centro de la huella de la ficha. El tinte nace donde el jugador acaba
    // de posar y corre hacia los extremos de cada línea.
    val gp = dragging.ghostGridPosition ?: return
    val shape = dragging.piece.shape
    val originX = gp.x + (shape.sumOf { it.x }.toFloat() / shape.size) + 0.5f
    val originY = gp.y + (shape.sumOf { it.y }.toFloat() / shape.size) + 0.5f
    LineClearPreviewActive(
        rowSpans = dragging.clearRowSpans,
        cols = dragging.clearCols,
        colorType = dragging.piece.colorType,
        boosted = comboStreak >= 2,
        cellSizePx = cellSizePx,
        coveredCells = coveredCells,
        specialCells = specialCells,
        originX = originX,
        originY = originY,
        modifier = modifier
    )
}

/**
 * TORMENTA/VOLCÁN — telegrafiado de ENERGIZACIÓN: mientras el jugador arrastra una ficha sobre una
 * posición que COMPLETARÍA una fila con ≥4 madera lisa, esa fila NO se rompe: se convierte en un
 * circuito de bobinas. Este aviso lo deja claro con un velo ELÉCTRICO cian + bordes de rayo
 * parpadeantes (distinto del preview de rotura del color de la ficha), para que el jugador entienda
 * que va a crear un puzzle de bobinas, no a limpiar la línea. Sólo filas (las columnas nunca energizan).
 *
 * PERF: mismo patrón que LineClearPreviewLayer — sólo se monta cuando hay filas que energizar, reloj
 * único leído dentro de onDrawBehind, geometría cacheada en drawWithCache, 0 allocs/frame.
 */
@Composable
fun CircuitEnergizePreviewLayer(
    dragState: DragState,
    cellSizePx: Float,
    modifier: Modifier = Modifier,
    coveredCells: Set<Position> = emptySet()
) {
    val dragging = dragState as? DragState.Dragging ?: return
    if (!dragging.isValidPlacement || cellSizePx <= 0f) return
    if (dragging.energizeRows.isEmpty()) return

    val transition = rememberInfiniteTransition(label = "energizePreview")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart),
        label = "energizePreviewClock"
    )
    val rows = dragging.energizeRows

    // Paleta eléctrica: cian brillante + blanco (rayo).
    val spark = Color(0xFF4DE8FF)
    Spacer(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .drawWithCache {
                val grid = GameState.GRID_SIZE
                val cell = cellSizePx
                val boardPx = cell * grid
                val twoPi = (2.0 * PI).toFloat()

                val haloW = 9.dp.toPx()
                val inset = haloW * 0.5f
                fun clampEdge(v: Float): Float = v.coerceIn(inset, boardPx - inset)
                // Recorte a la parte visible de cada fila (lava/glotón no dibujan sobre lo cerrado).
                val lineRects = ArrayList<Rect>(rows.size)
                val edges = ArrayList<FloatArray>(rows.size * 2)
                rows.forEach { y ->
                    var minX = -1; var maxX = -1
                    for (x in 0 until grid) if (Position(x, y) !in coveredCells) { if (minX < 0) minX = x; maxX = x }
                    if (minX < 0) return@forEach
                    val left = minX * cell; val right = (maxX + 1) * cell
                    lineRects.add(Rect(left, y * cell, right, (y + 1) * cell))
                    edges.add(floatArrayOf(left, clampEdge(y * cell), right, clampEdge(y * cell)))
                    edges.add(floatArrayOf(left, clampEdge((y + 1) * cell), right, clampEdge((y + 1) * cell)))
                }
                val midW = 4.5.dp.toPx()
                val coreW = 2.dp.toPx()
                val veilRadius = CornerRadius(6.dp.toPx())

                onDrawBehind {
                    val t = clock
                    // Parpadeo rápido y algo irregular (dos senos) → sensación de chispa eléctrica.
                    val flicker = 0.5f + 0.5f * sin(t * twoPi * 3f) * (0.7f + 0.3f * sin(t * twoPi * 7f))

                    lineRects.forEach { r ->
                        drawRoundRect(
                            color = spark,
                            topLeft = Offset(r.left, r.top),
                            size = Size(r.width, r.height),
                            cornerRadius = veilRadius,
                            alpha = 0.20f + 0.16f * flicker
                        )
                    }
                    val haloAlpha = 0.18f + 0.20f * flicker
                    val midAlpha = 0.36f + 0.24f * flicker
                    edges.forEach { e ->
                        val a = Offset(e[0], e[1])
                        val b = Offset(e[2], e[3])
                        drawLine(spark, a, b, strokeWidth = haloW, cap = StrokeCap.Butt, alpha = haloAlpha)
                        drawLine(spark, a, b, strokeWidth = midW, cap = StrokeCap.Butt, alpha = midAlpha)
                        drawLine(Color.White, a, b, strokeWidth = coreW, cap = StrokeCap.Butt, alpha = 0.7f + 0.3f * flicker)
                    }
                }
            }
    )
}

@Composable
private fun LineClearPreviewActive(
    rowSpans: List<Pair<Int, IntRange>>,
    cols: List<Int>,
    colorType: Int,
    boosted: Boolean,
    cellSizePx: Float,
    coveredCells: Set<Position>,
    specialCells: Set<Position>,
    originX: Float,
    originY: Float,
    modifier: Modifier
) {
    // Color de la FICHA según el skin activo: [base, claro, oscuro].
    val skin = LocalBlockSkin.current
    val palette = skin.colorsFor(colorType)
    val base = palette[0]
    val light = palette[1]
    val dark = palette[2]

    // Reloj único 0..1 (1.8 s). Solo funciones sin(t·2π·k) con k entero → continuo al reiniciar.
    val transition = rememberInfiniteTransition(label = "lineClearPreview")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "lineClearPreviewClock"
    )

    // BARRIDO: el tinte no entra de golpe — corre desde la huella de la ficha hacia los extremos de
    // cada línea. Se reinicia cuando cambia la jugada (otra línea, otra posición de la ficha).
    val wipe = remember { Animatable(0f) }
    LaunchedEffect(rowSpans, cols, originX.toInt(), originY.toInt()) {
        wipe.snapTo(0f)
        wipe.animateTo(1f, tween(WIPE_MS, easing = FastOutSlowInEasing))
    }

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()   // red de seguridad: NADA se dibuja fuera del tablero
            .drawWithCache {
                val grid = GameState.GRID_SIZE
                val cell = cellSizePx
                val boardPx = cell * grid
                val twoPi = (2.0 * PI).toFloat()

                // ── Rects de línea + bordes neón, RECORTADOS a la parte VISIBLE de cada línea ──
                // Las celdas cubiertas (lava/mordisco) se excluyen: el aviso sólo se dibuja sobre el tramo
                // contiguo del tablero que sigue visible (para lava/glotón las cubiertas están en un
                // extremo, así que el recorte deja un único tramo limpio).
                val gridN = grid
                val haloW = 9.dp.toPx()
                val inset = haloW * 0.5f
                fun clampEdge(v: Float): Float = v.coerceIn(inset, boardPx - inset)
                // Cada borde: [x0, y0, x1, y1]. Las coordenadas perpendiculares al perímetro se retraen
                // medio trazo hacia dentro para que el halo no cruce el borde del tablero.
                val lineRects = ArrayList<Rect>(rowSpans.size + cols.size)
                val edges = ArrayList<FloatArray>((rowSpans.size + cols.size) * 2)
                // Cada fila llega con su TRAMO exacto (con el Corte Ninja: solo la mitad que se
                // completa) — el aviso se dibuja SOLO sobre ese tramo, menos las celdas cubiertas.
                rowSpans.forEach { (y, span) ->
                    var minX = -1; var maxX = -1
                    for (x in span) if (Position(x, y) !in coveredCells) { if (minX < 0) minX = x; maxX = x }
                    if (minX < 0) return@forEach
                    val left = minX * cell; val right = (maxX + 1) * cell
                    lineRects.add(Rect(left, y * cell, right, (y + 1) * cell))
                    edges.add(floatArrayOf(left, clampEdge(y * cell), right, clampEdge(y * cell)))
                    edges.add(floatArrayOf(left, clampEdge((y + 1) * cell), right, clampEdge((y + 1) * cell)))
                }
                cols.forEach { x ->
                    var minY = -1; var maxY = -1
                    for (y in 0 until gridN) if (Position(x, y) !in coveredCells) { if (minY < 0) minY = y; maxY = y }
                    if (minY < 0) return@forEach
                    val top = minY * cell; val bottom = (maxY + 1) * cell
                    lineRects.add(Rect(x * cell, top, (x + 1) * cell, bottom))
                    edges.add(floatArrayOf(clampEdge(x * cell), top, clampEdge(x * cell), bottom))
                    edges.add(floatArrayOf(clampEdge((x + 1) * cell), top, clampEdge((x + 1) * cell), bottom))
                }

                // ── TINTE CON RELIEVE + BARRIDO (rework 2026-07-20b) ───────────────────────────
                // Antes se REPINTABA cada celda con un bloque opaco del color de la ficha. Eso
                // aplastaba el relieve 3D y la sombra propia del bloque —la fila se leía como una
                // pegatina pegada encima del tablero—, tapaba estrella/cronómetro/cristal (había que
                // repintarlos a mano detrás) y entraba de golpe, sin transición.
                //
                // Ahora NO se dibuja ningún bloque: el bloque real del tablero se sigue viendo y
                // encima cae un TINTE translúcido en dos pasadas (un velo oscuro que neutraliza el
                // tono original + el color de la ficha). Los brillos del bisel y el labio son mucho
                // más claros que el tinte, así que atraviesan y el volumen sobrevive; los adornos se
                // ven a través, así que sobra todo el parche de redibujarlos.
                //
                // El tinte además BARRE: cada celda guarda su distancia (en celdas) al centro de la
                // huella de la ficha, y el frente avanza con `wipe`. Dedup: una celda que está en
                // fila y columna a la vez se tiñe una sola vez.
                val tintPositions = LinkedHashSet<Position>().apply {
                    rowSpans.forEach { (y, span) ->
                        for (x in span) {
                            val p = Position(x, y)
                            if (p !in coveredCells && p !in specialCells) add(p)
                        }
                    }
                    cols.forEach { x ->
                        for (y in 0 until gridN) {
                            val p = Position(x, y)
                            if (p !in coveredCells && p !in specialCells) add(p)
                        }
                    }
                }
                // [x_px, y_px, distancia_al_origen] por celda — geometría fija, 0 allocs por frame.
                val tintCells = ArrayList<FloatArray>(tintPositions.size)
                var maxDist = 1f
                tintPositions.forEach { p ->
                    // Distancia Chebyshev: en una fila manda |dx| y en una columna |dy|, así que el
                    // frente recorre la línea sin diagonales raras cuando se completan ambas.
                    val d = maxOf(
                        kotlin.math.abs(p.x + 0.5f - originX),
                        kotlin.math.abs(p.y + 0.5f - originY)
                    )
                    if (d > maxDist) maxDist = d
                    tintCells.add(floatArrayOf(p.x * cell, p.y * cell, d))
                }
                val cellSizeFull = Size(cell, cell)
                val tintRadius = CornerRadius(cell * 0.16f, cell * 0.16f)

                val midW = 4.5.dp.toPx()
                val coreW = 2.dp.toPx()
                val veilRadius = CornerRadius(6.dp.toPx())

                onDrawBehind {
                    val t = clock   // lectura del reloj: invalida SOLO el dibujo
                    val pulse = 0.5f + 0.5f * sin(t * twoPi * 2f)
                    val glowBoost = if (boosted) 1.15f else 1f

                    // 0) TINTE con BARRIDO: el frente avanza desde la huella de la ficha hacia los
                    //    extremos. `amount` es cuánto ha prendido ya cada celda (0..1) y `edge` marca
                    //    la celda que el frente está encendiendo justo ahora (destello blanco).
                    val front = wipe.value * (maxDist + WIPE_FEATHER)
                    for (i in tintCells.indices) {
                        val c = tintCells[i]
                        val amount = ((front - c[2]) / WIPE_FEATHER).coerceIn(0f, 1f)
                        if (amount <= 0f) continue
                        val tl = Offset(c[0], c[1])
                        // Pasada 1: velo oscuro. Neutraliza el tono original del bloque para que el
                        // color de la ficha se imponga sin necesidad de taparlo del todo.
                        drawRoundRect(
                            color = Color.Black,
                            topLeft = tl, size = cellSizeFull, cornerRadius = tintRadius,
                            alpha = 0.26f * amount
                        )
                        // Pasada 2: color de la ficha. Translúcido a propósito: los brillos del bisel
                        // y el labio son mucho más claros que el tinte y lo atraviesan → el bloque
                        // conserva su volumen y la estrella/el cronómetro se siguen leyendo.
                        drawRoundRect(
                            color = base,
                            topLeft = tl, size = cellSizeFull, cornerRadius = tintRadius,
                            alpha = (0.62f + 0.06f * pulse) * amount
                        )
                        // Frente del barrido: destello claro en la celda que se está encendiendo.
                        val edge = amount * (1f - amount) * 4f      // pico en amount = 0.5
                        if (edge > 0.02f) {
                            drawRoundRect(
                                color = light,
                                topLeft = tl, size = cellSizeFull, cornerRadius = tintRadius,
                                alpha = 0.42f * edge
                            )
                        }
                    }

                    // 1) VELO tenue sobre la línea entera: su trabajo ahora es sólo unificar las
                    //    celdas ESPECIALES (comodín, bobina, hielo, cuerpo de Teika), que no reciben
                    //    tinte. Muy bajo, para no volver a ensuciar el relieve de las que sí lo tienen.
                    lineRects.forEach { r ->
                        drawRoundRect(
                            color = base,
                            topLeft = Offset(r.left, r.top),
                            size = Size(r.width, r.height),
                            cornerRadius = veilRadius,
                            alpha = (0.10f + 0.05f * pulse) * wipe.value
                        )
                    }

                    // 2) Bordes NEÓN por capas (halo ancho → medio → núcleo blanco), remate PLANO
                    //    para no sobrepasar los extremos de la línea. Entran con el barrido, no antes:
                    //    el marco encendiéndose a la vez que el tinte es lo que vende el "se carga".
                    val haloAlpha = (0.16f + 0.12f * pulse) * glowBoost * wipe.value
                    val midAlpha = (0.34f + 0.18f * pulse) * glowBoost * wipe.value
                    val coreAlpha = (0.78f + 0.22f * pulse) * wipe.value
                    edges.forEach { e ->
                        val a = Offset(e[0], e[1])
                        val b = Offset(e[2], e[3])
                        drawLine(light, a, b, strokeWidth = haloW, cap = StrokeCap.Butt, alpha = haloAlpha)
                        drawLine(light, a, b, strokeWidth = midW, cap = StrokeCap.Butt, alpha = midAlpha)
                        drawLine(Color.White, a, b, strokeWidth = coreW, cap = StrokeCap.Butt, alpha = coreAlpha)
                    }
                }
            }
    )
}
