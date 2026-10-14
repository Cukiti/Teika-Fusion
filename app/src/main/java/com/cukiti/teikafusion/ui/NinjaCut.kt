package com.cukiti.teikafusion.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.viewmodel.GameViewModel
import kotlin.math.PI
import kotlin.math.sin

// ── Dojo en Sombras / CORTE NINJA ─────────────────────────────────────────────────────────────────
// La mecánica única del bioma (rework 2026-07-16): tras 1-2 colocaciones sin romper filas el aviso
// crece (una KATANA asoma más y más), y a la 3ª la hoja cae y PARTE el tablero en dos por la
// columna central. SIN personaje ninja — la espada es la única presencia. Este archivo dibuja:
//  · [NinjaCutThreatOverlay] — el aviso creciente, POR ESTADO (ninjaIdleTurns 1→2): la katana
//    emerge del borde superior turno a turno; transición fluida sin cortes, montado solo mientras
//    hay amenaza (patrón GlacierThreatVignette).
//  · [NinjaBoardSlashVFX] — el tajo: la katana cae en picado, slash blanco de borde a borde por
//    la columna central, chispas de acero. Un solo consumidor del evento.
//  · [NinjaBoardRepairVFX] — la reparación KINTSUGI: la grieta se sella con un destello DORADO
//    (la reparación japonesa con oro). Un solo consumidor del evento.
//  · [Modifier.ninjaBoardSplit] — la SEPARACIÓN física de las mitades: dibuja el contenido dos
//    veces (clip por mitad + translate + micro-rotación) con vaivén de flotación. Lo aplica
//    GameScreen al Box que envuelve tablero + capas de preview, así TODO se parte coherente.

private val DOJO_NIGHT = Color(0xFF0E0B1C)
private val DOJO_VIOLET = Color(0xFFB39DDB)
private val KATANA_STEEL = Color(0xFFEFFBFF)
private val KINTSUGI_GOLD = Color(0xFFFFD54F)
private val KINTSUGI_DEEP = Color(0xFFB8860B)

/**
 * Separación visual de las mitades del tablero. [progress] 0 = tablero entero (coste cero: un solo
 * drawContent), 1 = mitades separadas flotando. [clock] = reloj continuo en radianes para el vaivén
 * (solo se lee en fase de DIBUJO — cero recomposiciones). El contenido se dibuja DOS veces (una por
 * mitad, con clip), cada mitad trasladada hacia su lado + vaivén vertical y micro-rotación opuestos:
 * las mitades se sienten SUELTAS, flotando en la pantalla. Entre ambas queda el abismo oscuro.
 */
fun Modifier.ninjaBoardSplit(progress: () -> Float, clock: () -> Float): Modifier =
    drawWithContent {
        val sep = progress()
        if (sep <= 0.001f) {
            drawContent()
            return@drawWithContent
        }
        val w = size.width
        val h = size.height
        val wallX = w / 2f
        val sepPx = sep * w * 0.030f          // desplazamiento de cada mitad (~10 dp en un tablero real)
        val t = clock()
        // Vaivén suave de flotación: fases distintas por mitad → nunca se mueven en bloque.
        val bobL = sin(t) * h * 0.006f * sep
        val bobR = sin(t + 2.1f) * h * 0.006f * sep
        val rotL = (-0.5f + sin(t * 0.7f) * 0.18f) * sep
        val rotR = (0.5f + sin(t * 0.7f + 1.3f) * 0.18f) * sep

        // Abismo bajo la grieta (se ve entre las mitades separadas).
        drawRect(
            brush = Brush.horizontalGradient(
                0f to Color.Transparent,
                0.5f to Color(0xE6050308),
                1f to Color.Transparent,
                startX = wallX - sepPx * 2.4f,
                endX = wallX + sepPx * 2.4f
            ),
            topLeft = Offset(wallX - sepPx * 2.4f, 0f),
            size = Size(sepPx * 4.8f, h)
        )

        // Mitad IZQUIERDA: trasladada a la izquierda + flotación + micro-rotación (pivote en su base).
        translate(left = -sepPx, top = bobL) {
            rotate(degrees = rotL, pivot = Offset(wallX * 0.5f, h)) {
                clipRect(0f, -h * 0.5f, wallX, h * 1.5f) {
                    this@drawWithContent.drawContent()
                }
            }
        }
        // Mitad DERECHA: espejo.
        translate(left = sepPx, top = bobR) {
            rotate(degrees = rotR, pivot = Offset(wallX * 1.5f, h)) {
                clipRect(wallX, -h * 0.5f, w, h * 1.5f) {
                    this@drawWithContent.drawContent()
                }
            }
        }
    }

/**
 * Estado de animación de la separación, para que GameScreen lo cablee al modifier. La separación
 * arranca RETRASADA ~45% del tajo (el slash debe verse cruzar ANTES de que el tablero ceda) y con
 * un spring violento con rebote; la reparación vuelve suave y asienta con un spring corto.
 */
@Composable
fun rememberNinjaSplitProgress(cutActive: Boolean): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(cutActive) {
        if (cutActive) {
            // El tajo cae primero (NinjaBoardSlashVFX); el tablero cede justo tras el paso de la hoja.
            kotlinx.coroutines.delay((GameViewModel.NINJA_CUT_SLASH_MS * 0.45f).toLong())
            progress.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = 260f))
        } else if (progress.value > 0f) {
            // Reparación: las mitades se atraen y ENCAJAN (asentado con rebote mínimo).
            progress.animateTo(0f, spring(dampingRatio = 0.58f, stiffness = 240f, visibilityThreshold = 0.001f))
        }
    }
    return remember { derivedStateOf { progress.value } }
}

/**
 * Reloj continuo (radianes) para el vaivén de flotación. Solo corre mientras hay separación.
 * MONÓTONO a propósito (patrón LavaOverlay): un bucle 0→2π con Restart hacía SALTAR las sinusoides
 * de período no múltiplo (sin(t·0.7) pasaba de -0.95 a 0 cada 4.2 s → tirón visible en las mitades
 * flotantes). Aquí avanza lineal ~70 min y el punto de reciclado cae en un múltiplo entero de TODOS
 * los períodos usados (1.0 y 0.7 ciclos/2π · 1000 vueltas) → costura invisible.
 */
@Composable
fun rememberNinjaFloatClock(active: Boolean): State<Float> {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            clock.snapTo(0f)
            clock.animateTo(
                targetValue = (2.0 * PI).toFloat() * 1000f,
                animationSpec = tween(4_200_000, easing = LinearEasing)
            )
        }
    }
    return remember { derivedStateOf { clock.value } }
}

/**
 * Host de los VFX del Corte Ninja dentro del tablero (lo monta GameBoard solo en bioma Dojo con
 * amenaza, corte o eventos vivos). Contrato de eventos: SOLO este composable consume
 * [GameEvent.NinjaBoardSlash] y [GameEvent.NinjaBoardRepair].
 */
@Composable
fun NinjaCutOverlay(
    gameState: GameState,
    cellSize: Dp,
    onEventConsumed: ((String) -> Unit)?
) {
    val density = LocalDensity.current
    val boardPx = with(density) { (cellSize * GameState.GRID_SIZE).toPx() }

    Box(Modifier.size(cellSize * GameState.GRID_SIZE)) {
        // 1) Aviso creciente (1ª y 2ª colocación ociosa) — por estado, fluido, sin cortes.
        if (!gameState.ninjaCutActive && gameState.ninjaIdleTurns > 0) {
            NinjaCutThreatOverlay(idleTurns = gameState.ninjaIdleTurns)
        }

        // 2) Tablero partido: pips de progreso de reparación sobre la grieta — TANTOS PIPS COMO
        //    LÍNEAS PIDA ESTE NIVEL (P-188, 2026-09-05). Antes eran cuatro fijos.
        if (gameState.ninjaCutActive) {
            NinjaCutRepairPips(
                linesLeft = gameState.ninjaCutLinesLeft,
                total = com.cukiti.teikafusion.model.LevelDirector.ninjaRepairLines(gameState.level),
                boardPx = boardPx,
                healed = gameState.ninjaCutTurns.toFloat() /
                    com.cukiti.teikafusion.model.LevelDirector.ninjaCutHealTurns(gameState.level).coerceAtLeast(1),
            )
        }

        // 3) El TAJO (una vez, al activarse).
        val slashEvents = remember(gameState.events) {
            gameState.events.filterIsInstance<GameEvent.NinjaBoardSlash>()
        }
        slashEvents.forEach { event ->
            key(event.id) {
                NinjaBoardSlashVFX(boardPx = boardPx) { onEventConsumed?.invoke(event.id) }
            }
        }

        // 4) La REPARACIÓN kintsugi (una vez, al romper la 4ª línea o formar un MEGA).
        val repairEvents = remember(gameState.events) {
            gameState.events.filterIsInstance<GameEvent.NinjaBoardRepair>()
        }
        repairEvents.forEach { event ->
            key(event.id) {
                NinjaBoardRepairVFX(boardPx = boardPx) { onEventConsumed?.invoke(event.id) }
            }
        }
    }
}

/**
 * Aviso creciente del corte: "algo se aproxima". Nivel 1 (1ª colocación ociosa): la noche del dojo
 * se cierne — viñeta violácea desde arriba, la PUNTA de una katana asoma sobre el borde superior y
 * una línea de corte TENUE titila en la columna central (telegrafía de DÓNDE cortará). Nivel 2: la
 * hoja baja media espada, un destello recorre el filo y la línea pulsa más rápido. Sin personaje
 * ninja — solo la katana emergiendo turno a turno (rework 2026-07-16).
 * TRANSICIÓN FLUIDA: un único composable siempre montado mientras hay amenaza; el nivel es un
 * animateFloatAsState (900 ms) — pasar de 1 a 2 nunca corta la animación.
 */
@Composable
private fun NinjaCutThreatOverlay(idleTurns: Int) {
    val threat by animateFloatAsState(
        targetValue = (idleTurns.coerceIn(0, 2)) / 2f,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "ninjaThreat"
    )
    // Pulso continuo (solo corre mientras el overlay está montado = solo con amenaza viva).
    val pulseT by rememberInfiniteTransition(label = "ninjaThreatPulse").animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "ninjaThreatClock"
    )
    // Capa propia (auditoría de fluidez 2026-09-29): anima cada fotograma mientras dura la amenaza.
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        if (threat <= 0.005f) return@Canvas
        val w = size.width
        val h = size.height
        // El pulso late más rápido cuanto mayor la amenaza (la fase escala con threat).
        val pulse = 0.5f + 0.5f * sin(pulseT * (1f + threat * 1.4f))

        // Viñeta nocturna que se cierne desde arriba (el dojo se oscurece).
        drawRect(
            brush = Brush.verticalGradient(
                0f to DOJO_NIGHT.copy(alpha = 0.34f * threat * (0.8f + pulse * 0.2f)),
                0.45f to DOJO_NIGHT.copy(alpha = 0.10f * threat),
                1f to Color.Transparent
            )
        )

        // Línea de corte telegrafiada: tenue y punteada en la columna central, más viva con amenaza.
        val wallX = w / 2f
        val dashLen = h / 14f
        val lineAlpha = (0.16f + 0.34f * threat) * (0.55f + pulse * 0.45f)
        var y = dashLen * 0.5f
        var i = 0
        while (y < h) {
            drawLine(
                color = if (i % 2 == 0) DOJO_VIOLET.copy(alpha = lineAlpha)
                        else KATANA_STEEL.copy(alpha = lineAlpha * 0.8f),
                start = Offset(wallX, y),
                end = Offset(wallX, (y + dashLen * 0.62f).coerceAtMost(h)),
                strokeWidth = w * (0.004f + 0.004f * threat),
                cap = StrokeCap.Round
            )
            y += dashLen
            i++
        }

        // KATANA asomando desde el borde superior, punta hacia abajo sobre la línea de corte:
        // con amenaza 0.5 (1er turno ocioso) apenas asoma la punta; con 1.0 (2º turno) media hoja
        // fuera, con un destello recorriendo el filo. Vaivén sutil: la hoja "respira" mientras baja.
        val peek = threat * threat   // asoma poco al principio, decidido al final
        val bladeLen = h * 0.26f
        val cx = wallX
        val tipY = -bladeLen * 0.12f + peek * bladeLen * 0.62f + sin(pulseT * 0.9f) * h * 0.004f
        if (tipY > 0f) {
            val bladeW = w * 0.011f
            val guardY = tipY - bladeLen
            val bladeA = (0.55f + 0.45f * threat)
            // Hoja: acero con degradado (más brillante hacia la punta) + filo blanco.
            drawLine(
                brush = Brush.verticalGradient(
                    0f to KATANA_STEEL.copy(alpha = bladeA * 0.55f),
                    1f to KATANA_STEEL.copy(alpha = bladeA),
                    startY = guardY, endY = tipY
                ),
                start = Offset(cx, guardY),
                end = Offset(cx, tipY),
                strokeWidth = bladeW, cap = StrokeCap.Round
            )
            drawLine(
                Color.White.copy(alpha = bladeA * 0.75f),
                Offset(cx - bladeW * 0.22f, guardY + bladeLen * 0.12f),
                Offset(cx - bladeW * 0.22f, tipY - bladeW * 0.6f),
                strokeWidth = bladeW * 0.3f, cap = StrokeCap.Round
            )
            // Tsuba (guarda) y empuñadura con vendado, por encima del borde.
            drawLine(
                Color(0xFF241B3A).copy(alpha = 0.95f * threat),
                Offset(cx - bladeW * 2.2f, guardY),
                Offset(cx + bladeW * 2.2f, guardY),
                strokeWidth = bladeW * 0.9f, cap = StrokeCap.Round
            )
            val handleTop = guardY - bladeLen * 0.30f
            drawLine(
                Color(0xFF141024).copy(alpha = 0.9f * threat),
                Offset(cx, guardY),
                Offset(cx, handleTop),
                strokeWidth = bladeW * 1.5f, cap = StrokeCap.Round
            )
            var wy = guardY - bladeLen * 0.05f
            while (wy > handleTop) {
                drawLine(
                    DOJO_VIOLET.copy(alpha = 0.55f * threat),
                    Offset(cx - bladeW * 0.75f, wy),
                    Offset(cx + bladeW * 0.75f, wy - bladeLen * 0.025f),
                    strokeWidth = bladeW * 0.28f
                )
                wy -= bladeLen * 0.06f
            }
            // Destello recorriendo el filo (solo cerca del nivel 2 — la hoja "canta").
            val gleam = (threat - 0.4f).coerceAtLeast(0f) / 0.6f
            if (gleam > 0f) {
                val gPos = (pulseT / (2f * PI.toFloat()) * (1f + threat)) % 1f
                val gy = guardY + gPos * (tipY - guardY)
                drawCircle(
                    Color.White.copy(alpha = gleam * (0.4f + pulse * 0.6f) * 0.9f),
                    bladeW * 0.9f, Offset(cx, gy)
                )
                drawCircle(
                    KATANA_STEEL.copy(alpha = gleam * pulse * 0.4f),
                    bladeW * 2.2f, Offset(cx, gy)
                )
            }
        }
    }
}

/**
 * EL TAJO: la katana cae como un rayo por la columna central, deja un slash blanco de borde a
 * borde y estallan chispas de acero. Dura [GameViewModel.NINJA_CUT_SLASH_MS]; la separación de las
 * mitades (ninjaBoardSplit) arranca al ~45% — la hoja corta ANTES de que el tablero ceda.
 */
@Composable
private fun NinjaBoardSlashVFX(boardPx: Float, onComplete: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(GameViewModel.NINJA_CUT_SLASH_MS, easing = LinearEasing))
        onComplete()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        val w = size.width
        val h = size.height
        val wallX = w / 2f

        // Fase 1 (0 → 0.4): la KATANA cae en picado por la línea central con estela de velocidad
        // (sin personaje ninja — solo la hoja, punta por delante).
        if (p < 0.5f) {
            val fall = (p / 0.4f).coerceAtMost(1f)
            val ny = -h * 0.12f + fall * fall * h * 1.1f   // punta de la hoja (aceleración de caída)
            val bladeW = w * 0.013f
            // Estela de velocidad (motion blur vertical, acero desvaneciéndose).
            drawLine(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to KATANA_STEEL.copy(alpha = 0.55f),
                    startY = ny - h * 0.26f, endY = ny
                ),
                start = Offset(wallX, (ny - h * 0.26f)),
                end = Offset(wallX, ny),
                strokeWidth = bladeW * 1.6f, cap = StrokeCap.Round
            )
            if (ny < h * 1.05f) {
                val bladeLen = h * 0.16f
                val guardY = ny - bladeLen
                // Hoja (acero) + filo blanco.
                drawLine(
                    KATANA_STEEL, Offset(wallX, guardY), Offset(wallX, ny),
                    strokeWidth = bladeW, cap = StrokeCap.Round
                )
                drawLine(
                    Color.White.copy(alpha = 0.85f),
                    Offset(wallX - bladeW * 0.22f, guardY + bladeLen * 0.15f),
                    Offset(wallX - bladeW * 0.22f, ny - bladeW * 0.6f),
                    strokeWidth = bladeW * 0.3f, cap = StrokeCap.Round
                )
                // Tsuba + empuñadura detrás.
                drawLine(
                    Color(0xFF241B3A),
                    Offset(wallX - bladeW * 2f, guardY), Offset(wallX + bladeW * 2f, guardY),
                    strokeWidth = bladeW * 0.85f, cap = StrokeCap.Round
                )
                drawLine(
                    Color(0xFF141024),
                    Offset(wallX, guardY), Offset(wallX, guardY - bladeLen * 0.34f),
                    strokeWidth = bladeW * 1.4f, cap = StrokeCap.Round
                )
            }
        }

        // Fase 2 (0.35 → 0.75): SLASH — hoja de luz blanca de borde a borde, ancho que respira y
        // se apaga. El flash pico coincide con el arranque de la separación (45%).
        if (p in 0.35f..0.8f) {
            val sp = ((p - 0.35f) / 0.45f).coerceIn(0f, 1f)
            val flash = sin(sp * PI.toFloat())   // 0 → 1 → 0
            val coreW = w * 0.012f * (0.4f + flash)
            drawLine(
                Color.White.copy(alpha = 0.95f * flash),
                Offset(wallX, 0f), Offset(wallX, h),
                strokeWidth = coreW, cap = StrokeCap.Round
            )
            drawLine(
                KATANA_STEEL.copy(alpha = 0.45f * flash),
                Offset(wallX, 0f), Offset(wallX, h),
                strokeWidth = coreW * 3.4f, cap = StrokeCap.Round
            )
            drawLine(
                DOJO_VIOLET.copy(alpha = 0.25f * flash),
                Offset(wallX, 0f), Offset(wallX, h),
                strokeWidth = coreW * 7f, cap = StrokeCap.Round
            )
        }

        // Fase 3 (0.4 → 1): CHISPAS de acero saliendo de la grieta, con gravedad.
        // (El poof de humo del ninja se retiró junto con el personaje — solo queda la hoja.)
        if (p > 0.4f) {
            val cp = (p - 0.4f) / 0.6f
            for (k in 0 until 14) {
                val seed = k * 0x9E3779B9.toInt()
                val side = if (k % 2 == 0) -1f else 1f
                val oy = ((seed ushr 8) and 0xFF) / 255f * h
                val speed = 0.5f + ((seed ushr 16) and 0xFF) / 255f
                val sx = wallX + side * cp * w * 0.10f * speed
                val sy = oy + cp * cp * h * 0.06f
                val a = (1f - cp) * 0.9f
                drawCircle(
                    if (k % 3 == 0) KATANA_STEEL.copy(alpha = a) else Color(0xFFFFC24D).copy(alpha = a),
                    w * 0.004f * (1.6f - cp), Offset(sx, sy)
                )
            }
        }
    }
}

/**
 * Pips de reparación sobre la grieta: cuántas líneas faltan (se encienden doradas al romper).
 *
 * **[total] LO PONE EL NIVEL** (P-188, 2026-09-05). Antes se leía de
 * `GameViewModel.NINJA_CUT_REPAIR_LINES` (ya podada), una constante de 4 — pero el corte se repara con **3, 4 o
 * 5** líneas según el tipo de nivel (`LevelDirector.ninjaRepairLines`), así que el medidor mentía en
 * dos de los tres casos, y de la peor manera:
 *
 *  · en un nivel de **5**, `done = i < 4 − linesLeft` sale negativo las dos primeras líneas: el
 *    jugador rompía dos y **no se encendía ni un pip**. Idéntico al reporte del Jardín — un
 *    contrajuego que avanza sin decirlo se lee como un contrajuego que no funciona.
 *  · en un nivel de **3**, sobraba un pip que no se encendía nunca: pedía una línea de más.
 */
@Composable
private fun NinjaCutRepairPips(
    linesLeft: Int,
    total: Int,
    boardPx: Float,
    /**
     * P-254 · LA GRIETA SE CIERRA SOLA: fracción del plazo ya cumplida (0 → 1). El oro del kintsugi
     * sube por la costura desde abajo, un tramo por colocación: el jugador VE que el castigo acaba.
     */
    healed: Float,
) {
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val wallX = w / 2f
        val k = healed.coerceIn(0f, 1f)
        if (k > 0f) {
            drawLine(
                KINTSUGI_DEEP.copy(alpha = 0.8f), Offset(wallX, h), Offset(wallX, h * (1f - k)),
                strokeWidth = w * 0.012f, cap = StrokeCap.Round
            )
            drawLine(
                KINTSUGI_GOLD.copy(alpha = 0.9f), Offset(wallX, h), Offset(wallX, h * (1f - k)),
                strokeWidth = w * 0.006f, cap = StrokeCap.Round
            )
        }
        val r = w * 0.011f
        val gap = r * 3.2f
        val topY = r * 4f
        for (i in 0 until total) {
            val done = i < total - linesLeft
            val c = Offset(wallX, topY + i * gap)
            if (done) {
                drawCircle(KINTSUGI_GOLD, r, c)
                drawCircle(Color.White.copy(alpha = 0.55f), r * 0.45f, Offset(c.x - r * 0.25f, c.y - r * 0.25f))
            } else {
                drawCircle(Color(0xFF141024).copy(alpha = 0.85f), r, c)
                drawCircle(DOJO_VIOLET.copy(alpha = 0.7f), r, c, style = Stroke(width = r * 0.34f))
            }
        }
    }
}

/**
 * REPARACIÓN KINTSUGI: mientras las mitades encajan (lo anima ninjaBoardSplit al apagarse el
 * estado), la costura central se sella con ORO — un río dorado que crece de abajo arriba, brilla al
 * máximo cuando el tablero asienta y se desvanece dejando un destello. Chispas doradas ascienden.
 */
@Composable
private fun NinjaBoardRepairVFX(boardPx: Float, onComplete: () -> Unit) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(1100, easing = FastOutSlowInEasing))
        onComplete()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        val w = size.width
        val h = size.height
        val wallX = w / 2f
        // El oro sube desde la base hasta sellar la grieta entera (0 → 0.55).
        val fill = (p / 0.55f).coerceAtMost(1f)
        // Luego el sello brilla y se desvanece (0.55 → 1).
        val fade = if (p > 0.55f) 1f - (p - 0.55f) / 0.45f else 1f
        val glow = if (p in 0.5f..0.75f) sin((p - 0.5f) / 0.25f * PI.toFloat()) else 0f

        val topY = h * (1f - fill)
        // Río de oro (con ondulación leve, como laca vertida).
        val path = Path()
        var yy = h
        path.moveTo(wallX, h)
        while (yy > topY) {
            path.lineTo(wallX + sin(yy / h * 22f) * w * 0.004f, yy)
            yy -= h / 40f
        }
        drawPath(path, KINTSUGI_DEEP.copy(alpha = 0.85f * fade), style = Stroke(width = w * 0.014f, cap = StrokeCap.Round))
        drawPath(path, KINTSUGI_GOLD.copy(alpha = 0.95f * fade), style = Stroke(width = w * 0.007f, cap = StrokeCap.Round))
        // Destello del sellado completo.
        if (glow > 0f) {
            drawLine(
                Color.White.copy(alpha = 0.75f * glow),
                Offset(wallX, 0f), Offset(wallX, h),
                strokeWidth = w * 0.006f, cap = StrokeCap.Round
            )
            drawCircle(
                KINTSUGI_GOLD.copy(alpha = 0.35f * glow),
                w * 0.16f, Offset(wallX, h * 0.5f)
            )
        }
        // Motas doradas ascendiendo a lo largo de la costura.
        for (k in 0 until 10) {
            val seed = k * 0x27D4EB2F
            val phase = ((seed ushr 6) and 0xFF) / 255f
            val mp = (p * 1.4f + phase) % 1f
            val my = h - mp * h
            if (my < topY) continue
            val mx = wallX + sin((mp + k) * 9f) * w * 0.018f
            drawCircle(
                KINTSUGI_GOLD.copy(alpha = (1f - mp) * 0.8f * fade),
                w * 0.0045f, Offset(mx, my)
            )
        }
    }
}

/**
 * P-323 · LA CICATRIZ DE ORO: tras sellarse la costura, gotas de laca dorada caen por ella y cuajan en
 * cada casilla de [event.cells] (el motor pone el bloque al 60 %, al cuajar), con un destello.
 */
@Composable
fun KintsugiScarVFX(event: GameEvent.KintsugiScar, cellPx: Float, onComplete: () -> Unit) {
    val t = remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        t.animateTo(1f, tween(event.durationMillis, easing = LinearEasing))
        onComplete()
    }
    Canvas(Modifier.fillMaxSize()) {
        val p = t.value
        event.cells.forEach { cell ->
            val dest = Offset((cell.x + 0.5f) * cellPx, (cell.y + 0.5f) * cellPx)
            val drop = (p / 0.6f).coerceIn(0f, 1f)
            if (drop < 1f) {
                // La gota baja por la costura y se ensancha al llegar.
                val y = dest.y * (drop * drop)
                val r = cellPx * (0.08f + 0.22f * drop)
                drawLine(KINTSUGI_DEEP.copy(alpha = 0.6f), Offset(dest.x, 0f), Offset(dest.x, y), strokeWidth = cellPx * 0.05f)
                drawCircle(KINTSUGI_GOLD, radius = r, center = Offset(dest.x, y))
            } else {
                val k = ((p - 0.6f) / 0.4f).coerceIn(0f, 1f)
                drawCircle(KINTSUGI_GOLD.copy(alpha = 0.8f * (1f - k)), radius = cellPx * (0.4f + 0.5f * k),
                    center = dest, style = Stroke(width = cellPx * 0.07f))
                drawCircle(Color.White.copy(alpha = 0.5f * (1f - k)), radius = cellPx * 0.25f, center = dest)
            }
        }
    }
}

/**
 * P-323: la cara del bloque de oro que deja el kintsugi — vetas doradas que cruzan la madera, como una
 * cerámica reparada. Estática.
 */
@Composable
fun KintsugiOverlay(modifier: Modifier = Modifier) {
    // Cacheado por tamaño (auditoría de fluidez 2026-09-29): con un skin animado la capa de la celda se
    // regraba cada fotograma y este dibujo, siendo un Canvas, rehacía su Path y sus dos trazos cada vez.
    Spacer(modifier.drawWithCache {
        val w = size.width
        val h = size.height
        val veta = Path().apply {
            moveTo(w * 0.10f, h * 0.30f)
            lineTo(w * 0.38f, h * 0.44f)
            lineTo(w * 0.52f, h * 0.30f)
            lineTo(w * 0.90f, h * 0.62f)
            moveTo(w * 0.38f, h * 0.44f)
            lineTo(w * 0.30f, h * 0.88f)
        }
        val deep = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        val gold = Stroke(width = w * 0.05f, cap = StrokeCap.Round)
        onDrawBehind {
            drawPath(veta, KINTSUGI_DEEP, style = deep)
            drawPath(veta, KINTSUGI_GOLD, style = gold)
        }
    })
}
