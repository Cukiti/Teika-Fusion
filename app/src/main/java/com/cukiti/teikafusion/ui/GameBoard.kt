package com.cukiti.teikafusion.ui

import com.cukiti.teikafusion.model.CompactorPlan

import androidx.compose.animation.core.*
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.cukiti.teikafusion.model.CellContent
import com.cukiti.teikafusion.model.GameEvent
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.model.MegaCollapse
import com.cukiti.teikafusion.model.lavaActive
import com.cukiti.teikafusion.model.Piece
import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.model.PlantAbduction
import com.cukiti.teikafusion.model.PlantEdge
import com.cukiti.teikafusion.model.TeikaType
import com.cukiti.teikafusion.viewmodel.TeikaBody
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import androidx.compose.ui.geometry.CornerRadius
import com.cukiti.teikafusion.ui.theme.EmptyCellBase
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Radio de las esquinas del panel del tablero. Fuente ÚNICA: lo usan el fondo, el borde, el clip y
 * cualquier marco que tenga que seguir su contorno (ver [DangerVignette]). Tenerlo duplicado como
 * literal es lo que dejó el marco de peligro con esquinas rectas dentro de un tablero redondeado.
 */
private val BOARD_CORNER = 12.dp

/**
 * GRAVEDAD VISIBLE (INFINITO) — memoria de en qué fila se dibujó cada bloque la composición anterior.
 *
 * `BoardGravity` reapila las columnas de golpe y `CellView` se posiciona con un `offset` de rejilla,
 * así que sin esto los bloques SALTABAN a su nueva fila: en el único modo cuya identidad es la
 * gravedad, la madera no caía. Guardando la fila previa por `id`, el bloque nace dibujado donde
 * estaba y CAE hasta donde le toca.
 *
 * Campos normales (no `State`): se leen y escriben durante la composición igual que el set
 * `animatedIds` de al lado, y por eso no invalidan nada — esto es contabilidad del DIBUJO, no estado
 * del juego.
 */
/** Cuánto se achata un bloque al aterrizar (0,14 = 14 % de su alto). */
private const val LANDING_SQUASH = 0.14f

/**
 * EL GOLPE: el bloque llega, se aplasta contra el suelo y se recompone con un muelle.
 *
 * Es lo que le da PESO a la caída — sin esto los bloques llegan a su casilla y se quedan quietos,
 * que es exactamente lo que se lee como "se han teletransportado, solo que despacio". El muelle es
 * rápido y poco elástico a propósito: madera cayendo, no goma.
 *
 * Solo lo usa el pozo del Infinito. En niveles la madera no cae y su regla de "la madera no hace
 * squash al posarse" sigue en pie.
 */
private suspend fun playLandingSquash(impact: Animatable<Float, AnimationVector1D>) {
    impact.snapTo(1f)
    impact.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 1500f))
}
/* (ERRADICADO 2026-08-09, con el modo WALLBLOCK) `BoardFallTracker`: la memoria de filas que
 * animaba la GRAVEDAD VISIBLE del pozo (caída continua, escalonado de cascada, golpe de aterrizaje
 * y desplazamiento sub-celda de lo que cabalgaba el muro). En NIVELES la madera no se mueve nunca:
 * se queda donde la puso el jugador, que es la regla block-blast de siempre. */

@Composable
fun GameBoard(
    gameState: GameState,
    onBoardPositioned: (Offset, Float) -> Unit,
    onEventConsumed: ((String) -> Unit)? = null,
    // Revelado dirigido por el VFX: celdas de hiddenWoodCells que acaban de ENCAJAR su animación
    // (WoodFall / BlockFlee / fila-bonus de Maite). El Boolean = sonido de encaje (pop).
    onRevealWood: ((Set<Position>, Boolean) -> Unit)? = null,
    onCoilTap: (Position) -> Unit = {},
    clawAim: com.cukiti.teikafusion.model.ClawAim? = null,
    clawPlan: com.cukiti.teikafusion.model.ClawTrip? = null,
    onClawSelect: (Float,Float)->Unit = { _,_-> },
    onClawTarget: (Float,Float)->Unit = { _,_-> },
    ballAim: com.cukiti.teikafusion.model.BallAim? = null,
    ballPlan: com.cukiti.teikafusion.model.BallPlan? = null,
    onBallAnchor: (Float,Float)->Unit = { _,_-> },
    onBallAim: (Float,Float)->Unit = { _,_-> },
    forgeAim: com.cukiti.teikafusion.model.ForgeAim? = null,
    forgePlan: com.cukiti.teikafusion.model.ForgePlan? = null,
    onForgeSelect: (Position)->Unit = {},
    reactorAim: com.cukiti.teikafusion.model.ReactorAim? = null,
    reactorPlan: com.cukiti.teikafusion.model.ReactorPlan? = null,
    onReactorSelect: (Position)->Unit = {},
    turbineAim: com.cukiti.teikafusion.model.TurbineAim? = null,
    onTurbineAnchor: (Float,Float)->Unit = { _,_-> },
    onTurbineAim: (Float,Float)->Unit = { _,_-> },
    orbitAim: com.cukiti.teikafusion.model.OrbitAim? = null,
    orbitPlan: com.cukiti.teikafusion.model.OrbitPlan? = null,
    onOrbitSelect: (Position)->Unit = {},
    portalSwapAim: com.cukiti.teikafusion.model.PortalSwapAim? = null,
    portalSwapPlan: com.cukiti.teikafusion.model.PortalSwapPlan? = null,
    onPortalSwapSelect: (Position)->Unit = {},
    compactorAiming: Boolean = false,
    onCompactorPreview: (Int,Int,Int)->CompactorPlan? = { _,_,_ -> null },
    onCompactorCancel: ()->Unit = {},
    onCompactorRelease: (Int,Int,Int)->Unit = { _,_,_ -> },
    onPinballBegin: (String)->Boolean = { false },
    onPinballCancel: ()->Unit = {},
    onPinballRelease: (String,Float,Float)->Unit = { _,_,_ -> },
    cellSize: Dp = 40.dp,
    comboStreak: Int = 0,
    // Motor físico Suika: snapshot por frame de los cuerpos de Teika (GameViewModel.teikaBodies).
    teikaBodies: StateFlow<List<TeikaBody>> = remember { MutableStateFlow(emptyList()) },
    // Rayco / Energía Cruzada: ids de los Teikas energizados (aura eléctrica persistente hasta descargar).
    energizedTeikaIds: StateFlow<Set<String>> = remember { MutableStateFlow(emptySet()) },
    // Volcán · EL MAGMA: calor (0..1) de la fila que arde bajo la lava (GameViewModel.magmaHeat).
    magmaHeat: StateFlow<Float> = remember { MutableStateFlow(0f) },
    // Tutorial 4: activa la respiración visual sólo mientras la etapa espera una inclinación.
    tutorialStageReady: Boolean = false,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val totalCellSize = cellSize
    val boardCols = gameState.gridW
    val boardRows = gameState.gridH
    val boardW = totalCellSize * boardCols
    val boardH = totalCellSize * boardRows
    // LA COLA DE EVENTOS, AGRUPADA UNA SOLA VEZ (perf 2026-08-09). Este tablero tenía ~47 bloques
    // `remember(gameState.events) { …filterIsInstance… }` con la MISMA clave: cualquier cambio en la
    // cola los invalidaba todos y disparaba 47 recorridos O(n). Ahora es un `groupBy` y 47 búsquedas
    // en mapa. Ver [GameEventBuckets] para por qué agrupar por clase exacta es correcto aquí.
    val eventBuckets = rememberEventBuckets(gameState.events)
    val rulePulses=eventBuckets.of<GameEvent.RulePulse>()
    val pinballImpacts=eventBuckets.of<GameEvent.PinballImpact>()
    // Volcán · EL MAGMA (2026-09-23): la fila que arde bajo la lava y los bloques ya fundidos.
    val magmaRow: Int? = if (gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.VOLCANO)
        com.cukiti.teikafusion.model.VolcanoMagma.burningRow(gameState.lavaActive, gameState.lavaRow) else null
    val burningCells = remember(gameState.grid, magmaRow) {
        magmaRow?.let { com.cukiti.teikafusion.model.VolcanoMagma.forgeable(it, gameState.grid) } ?: emptySet()
    }
    val magmaCells = remember(gameState.grid) {
        gameState.grid.filter { (_, c) -> c is CellContent.Wood && c.magma }.keys
    }
    val magmaHeatState = magmaHeat.collectAsState()
    // P-294 · AVISO 2 de la ESPADA y del METEORITO: los bloques de la línea señalada (o del cráter
    // marcado) tiemblan el turno antes del golpe.
    val threatShakeCells = remember(
        gameState.kingSwordAxis, gameState.kingSwordLine, gameState.kingSwordIdle,
        gameState.kingPieceKind, gameState.kingPieceA, gameState.kingPieceB,
        gameState.meteorAt, gameState.meteorAt2, gameState.galacticIdleSaved, gameState.level, gameState.biomePrimary,
    ) {
        when (gameState.biomePrimary) {
            // P-329: lo que amenace —espada o pieza, nunca las dos— tiembla en el aviso 2.
            com.cukiti.teikafusion.model.BiomeType.KING ->
                if (gameState.kingSwordIdle >= com.cukiti.teikafusion.model.LevelDirector.swordThreshold(gameState.level) - 1)
                    (gameState.kingSwordCells + gameState.kingPieceCells).toSet() else emptySet()
            com.cukiti.teikafusion.model.BiomeType.GALACTIC ->
                if (gameState.galacticIdleSaved >= com.cukiti.teikafusion.model.LevelDirector.meteorThreshold(gameState.level) - 1)
                    gameState.meteorCells.toSet() else emptySet()
            else -> emptySet()
        }
    }
    // Mansión Espectral · EL ESPEJO (P-246): los bloques fantasma y sus turnos restantes.
    val phantomCells = remember(gameState.grid) {
        gameState.grid.mapNotNull { (p, c) -> (c as? CellContent.Wood)?.takeIf { it.phantom > 0 }?.let { p to it.phantom } }.toMap()
    }
    val plantRescueEvents = eventBuckets.of<GameEvent.PlantRescue>()
    // Set de IDs que ya reprodujeron su animación de spawn.
    // Al estar fuera de CellView y sobrevivir recomposiciones, garantiza que un Teika
    // existente NUNCA repita su animación aunque su composable se re-cree.
    val animatedIds = remember { mutableSetOf<String>() }


    // «Reducir efectos» (P-12): el jugador pidió menos espectáculo. Ver [LocalReducedFx].
    val reducedFx = LocalReducedFx.current

    // Sacudida 2D: rebote vertical dominante + leve balanceo horizontal, con decaimiento
    // elástico (estilo Block Blast). La línea destruida "empuja" el tablero hacia arriba
    // y rebota. La intensidad escala con el nº de líneas (la emite el ViewModel).
    val shakeX = remember { Animatable(0f) }
    val shakeY = remember { Animatable(0f) }
    // C-4/C-5: todos los filterIsInstance y conteos pesados cacheados con remember
    //
    // MANDA LA MÁS FUERTE (2026-07-31). Antes se cogía la PRIMERA de la cola: como un mismo lote de
    // estado trae varias sacudidas (el avance de la lava emite dos seguidas; un MEGA en cascada emite
    // su 8f junto a las 1,3f/3,6f de las fusiones que lo formaron), la primera se consumía, el flush
    // de 32 ms la borraba y la SIGUIENTE —normalmente la floja— re-lanzaba el efecto cancelando la
    // anterior. Resultado: el golpe del MEGA, la sacudida más grande del juego, se cortaba a dos
    // fotogramas y quedaba un temblor de línea normal. Con el máximo, la cola no puede degradar el
    // golpe: las demás salen sin dibujarse (mismo patrón que comboTexts/perfectEvents más abajo).
    val shakeEvents = eventBuckets.of<GameEvent.ScreenShake>()
    val shakeEvent = remember(shakeEvents) { shakeEvents.maxByOrNull { it.intensity } }
    shakeEvents.forEach { if (it !== shakeEvent) onEventConsumed?.invoke(it.id) }
    LaunchedEffect(shakeEvent?.id) {
        if (shakeEvent == null) return@LaunchedEffect
        onEventConsumed?.invoke(shakeEvent.id)
        // «Reducir efectos»: la sacudida es lo que más molesta con sensibilidad al movimiento, pero
        // NO se apaga del todo — es la única señal de "eso ha pegado fuerte". Se queda en un tercio.
        val ampMul = if (reducedFx) 0.33f else 1f
        val amp = (shakeEvent.intensity * 6f * ampMul).coerceIn(3f * ampMul, 13f * ampMul)
        try {
            kotlinx.coroutines.coroutineScope {
                launch {
                    shakeY.snapTo(0f)
                    shakeY.animateTo(-amp,         spring(dampingRatio = 0.16f, stiffness = 2400f))
                    shakeY.animateTo(amp * 0.55f,  spring(dampingRatio = 0.22f, stiffness = 1700f))
                    shakeY.animateTo(0f,           spring(dampingRatio = 0.5f,  stiffness = 1100f))
                }
                launch {
                    shakeX.snapTo(0f)
                    shakeX.animateTo(amp * 0.45f,  spring(dampingRatio = 0.20f, stiffness = 2000f))
                    shakeX.animateTo(-amp * 0.28f, spring(dampingRatio = 0.28f, stiffness = 1500f))
                    shakeX.animateTo(0f,           spring(dampingRatio = 0.5f,  stiffness = 1000f))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                shakeX.snapTo(0f); shakeY.snapTo(0f)
            }
        }
    }

    // (Aquí vivía el "empuje" por saltos de celda: el muro ganaba una fila de golpe y una animación
    //  de 170/430 ms disimulaba el salto. Ya no hace falta ni sería correcto — el muro tiene una
    //  altura CONTINUA y los bloques que toca cuelgan de ella directamente, así que no hay ningún
    //  salto que disimular. Ver el bloque de BLOQUES más abajo y TideWall.)

    // ── #8 Tablero Viviente: tracking de llenado ──────────────────────
    // C-5: remember para no iterar 64 celdas en cada recomposición de animación
    //
    val filledCells = remember(gameState.grid) {
        gameState.grid.values.count { it !is CellContent.Empty }
    }
    val fillFraction = filledCells / (boardCols * boardRows).coerceAtLeast(1).toFloat()
    val dangerTarget = when {
        fillFraction > 0.80f -> 1.0f
        fillFraction > 0.65f -> 0.60f
        fillFraction > 0.40f -> 0.22f
        else -> 0f
    }
    // Se guarda el `State` y NO su valor (perf 2026-09-26): leído en el cuerpo, cada uno de los 900 ms
    // del fundido recomponía el TABLERO ENTERO —64 celdas y ~80 capas de VFX— solo para pasarle un
    // número a las viñetas. Ahora las viñetas lo leen al dibujar y aquí solo se pregunta SI hay peligro.
    val animatedDangerState = animateFloatAsState(
        targetValue = dangerTarget,
        animationSpec = tween(900),
        label = "danger"
    )
    val dangerShown by remember { derivedStateOf { animatedDangerState.value > 0f } }
    val dangerNow: () -> Float = remember { { animatedDangerState.value } }
    // Lava: bioma Volcán (Niveles) O peligro de la fase FRENESÍ (Infinito). Fuente única.
    val volcanoActive = gameState.lavaActive

    // Reloj de animación COMPARTIDO de los overlays de bioma por-celda (poison/coil):
    // un único frame-loop en vez de un rememberInfiniteTransition por celda. Solo corre cuando hay
    // celdas con efecto animado, para no forzar 60fps en tableros sin biomas.
    // (perf 2026-08-09) WALLBLOCK no tiene biomas: nunca hay madera envenenada ni bobinas, así que
    // este escaneo del tablero entero solo podía devolver `false` — una pasada por las 192 celdas en
    // cada cambio de rejilla para nada. Se corta en seco por modo.
    val hasBiomeFx = remember(gameState.grid) {
        gameState.grid.values.any { c -> c is CellContent.Wood && (c.poisoned || c.coil > 0) }
    }
    // (RETIRADO 2026-08-27) El AURA DE CARGA teñía el tablero entero según lo llena que estuviera la
    // barra del compañero. Se fue con la barra: sin auto-disparo no hay "carga" que anunciar, y era
    // una de las tres capas simultáneas de VFX que impedían leer qué estaba pasando en el tablero.
    // PAUSA: el reloj compartido de los efectos por celda también se para (bloque 20, 2026-07-30).
    // Es un frame-loop: dejarlo vivo detrás del overlay de pausa era pedir 60 fps para nadie.
    val animationsOn = LocalAnimationsActive.current
    // ── P-100 · UN SOLO RELOJ AMBIENTAL PARA EL TABLERO (2026-09-02) ──────────────────────────────
    // (2026-09-03) La cinta también es una marca del tablero con animación ambiente: sin ella en
    // esta cuenta, los galones se quedarían quietos en un nivel que no tuviera ninguna otra.
    val hasBoardMarks = gameState.hotCells.isNotEmpty() ||
        gameState.hasWheel || gameState.eggCell != null ||
        com.cukiti.teikafusion.model.ChainReaction.appliesTo(gameState.level) ||
        // P-242: la piñata se balancea con el reloj compartido.
        gameState.pinataCell != null ||
        // P-233 el molde late; P-238 las pompas tiemblan; P-243 los portales giran.
        gameState.moldAt != null ||
        com.cukiti.teikafusion.model.TeikaBubbles.appliesTo(gameState.level) ||
        com.cukiti.teikafusion.model.Portals.appliesTo(gameState.level) ||
        // P-241 la brújula late y avisa del giro.
        com.cukiti.teikafusion.model.Compass.appliesTo(gameState.level) ||
        // P-244 la vidriera late, y P-240 el campo del imán y su aviso de arrastre corren con él.
        gameState.glassCells.isNotEmpty() ||
        com.cukiti.teikafusion.model.MagnetPiece.appliesTo(gameState.level) ||
        // El magma late y la fila bajo la lava arde con el reloj compartido.
        burningCells.isNotEmpty() || magmaCells.isNotEmpty() ||
        // El espejo respira y los fantasmas titilan.
        phantomCells.isNotEmpty() || gameState.mirrorSource != com.cukiti.teikafusion.model.HauntedMirror.NONE ||
        // P-294: la espada alzada y el meteorito en camino laten; lo que amenazan tiembla.
        gameState.kingSwordAxis != com.cukiti.teikafusion.model.KingSword.NONE || gameState.meteorAt != null ||
        // P-329: la pieza del Rey y su camino laten igual.
        gameState.kingPieceKind != com.cukiti.teikafusion.model.KingPiece.NONE ||
        gameState.kingRestCell != null ||
        // Presagio del Cielo Estelar: el cielo dorado/plateado respira con el reloj compartido.
        gameState.starAstroOmen != com.cukiti.teikafusion.model.StarAstroOmen.NONE ||
        // P-251 3/7: el magma de las grietas de la Lava late con el mismo reloj.
        LocalBlockSkin.current.style == BlockTileStyle.LAVA ||
        // P-251 5/7: la niebla del Espectro respira y sus ojos parpadean con el mismo reloj.
        LocalBlockSkin.current.style == BlockTileStyle.SPECTRE ||
        // P-251 6/7: las estrellas de la Constelación titilan con el mismo reloj.
        LocalBlockSkin.current.style == BlockTileStyle.CONSTELLATION ||
        // (Cristal, Hielo, Vacío y Metal ya no están: sus brillos se quitaron el 2026-09-30 y son
        // estáticos, así que no necesitan el reloj.)
        // P-256: las púas de los bloques-Teika giran por el arcoíris con él (tablero y bandeja).
        com.cukiti.teikafusion.model.TeikaSeeds.appliesTo(gameState.level)
    BiomeClockDriver(active = (hasBiomeFx || hasBoardMarks) && animationsOn)

    // Glotón / Cocina Voraz: los rincones devorados se PINTAN como pozos oscuros (ver GluttonBiomeOverlay).
    // Antes el tablero se componía OFFSCREEN para que un BlendMode.Clear perforara agujeros reales, pero
    // alternar la estrategia de composición justo al morder hacía desaparecer el panel de fichas. Ya no se
    // usa capa offscreen ni blend → el modifier del tablero es ESTABLE y la bandeja no parpadea.
    // Panel del tablero estilo Block Blast (tableroblock.jpeg): esquinas redondeadas + borde sutil.
    // Sin clip: los VFX del Box externo deben poder salir del tablero (el fondo redondeado es solo
    // pintura; las celdas de las esquinas apenas rozan la curvatura).
    val boardShape = androidx.compose.foundation.shape.RoundedCornerShape(BOARD_CORNER)
    val boardBgTarget = com.cukiti.teikafusion.ui.theme.BoardBackground
    val boardBg by androidx.compose.animation.animateColorAsState(
        targetValue = boardBgTarget,
        animationSpec = androidx.compose.animation.core.tween(1400),
        label = "boardPhaseTint"
    )
    Box(
        modifier = modifier
            // ── EL PANEL MIDE EL TABLERO, PASE LO QUE PASE (2026-08-26e) ─────────────────────────
            // Este Box es el que PINTA el panel oscuro del tablero (`background` + `border`, justo
            // abajo), y hasta hoy no tenía alto propio: le llegaba un `fillMaxWidth()` desde
            // GameScreen y nada más. Un `Box` mide lo que mida su hijo MÁS ALTO, y el alto que este
            // recibe como máximo es **todo el hueco que le queda a la Column** — o sea media
            // pantalla.
            //
            // Consecuencia, reportada por el usuario y medida sobre `art/bugpanel.mp4`: de la
            // sesentena de capas de VFX que cuelgan de aquí, muchas se declaran con
            // `Modifier.fillMaxSize()` (explosión del MEGA, texto de combo, bloques que huyen,
            // partículas…). En reposo el hijo más alto es el Box del tablero y el panel mide
            // `boardH`; **en cuanto una de esas capas se monta, se estira hasta el hueco entero y el
            // panel con ella** — pinta por encima de la bandeja y la empuja fuera de la Column. En
            // el vídeo se ve exacto: en t≈25,7 s el panel llega hasta el anuncio, la bandeja
            // desaparece 0,4 s y vuelve **con las mismas tres fichas**. Medido: un rectángulo
            // uniforme de (30,37,65) —`BoardBackground`— de borde a borde donde estaba la bandeja.
            //
            // Con el alto fijo, esas capas reciben `maxHeight = boardH` y ya no pueden estirar nada.
            // Siguen pudiendo DIBUJAR fuera (este Box no recorta), que es lo que necesitan los
            // bloques despedidos por el MEGA y los textos que suben por encima del borde.
            //
            // El comentario de GameScreen sobre «agrandar este Box no era opción» describía este
            // mismo peligro desde el otro lado: la decena de capas con `matchParentSize` colgadas de
            // aquí se estiraban con él. Ahora no hay nada que estirar.
            .height(boardH)
            // La sacudida va en graphicsLayer (fase de DIBUJO), no en `offset` (fase de LAYOUT).
            // Con offset, cada frame de shake re-colocaba el Box interno → su
            // onGloballyPositioned reportaba un boardOffsetPx nuevo ~60 veces por sacudida:
            // recomposición de toda GameScreen y, peor, el mapeo dedo→celda del arrastre se
            // desplazaba hasta ±13 px justo mientras el jugador estaba colocando.
            .graphicsLayer {
                translationX = shakeX.value
                translationY = shakeY.value
            }
            .background(boardBg, boardShape)
            .border(1.5.dp, Color(0xFF141A33), boardShape)
    ) {
        // Bioma Eléctrico: el tablero escucha toques para encender bobinas en orden.
        val stormActive = gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.STORM
        // (2026-08-08) WALLBLOCK sale de aquí: el toque del tablero rompía un grupo a mano
        // (`tapWallblockGroup`), que contradice la regla del modo —solo el bloque de impacto rompe,
        // nunca un toque— y además le disputaba el dedo a la catapulta, que es quien apunta.
        val boardTapActive = stormActive
        val cellPxForTap = with(density) { totalCellSize.toPx() }
        Box(
            modifier = Modifier
                .width(boardW)
                .height(boardH)
                // Recorte con la MISMA forma redondeada del panel: los overlays de borde (lava del
                // volcán, vignettes de peligro, escarcha) seguían la esquina RECTA del clip y
                // asomaban picos fuera de la curva del tablero (esquinastablero.jpeg).
                .clip(boardShape)
                .onGloballyPositioned { coordinates ->
                    val offset = coordinates.localToWindow(Offset.Zero)
                    with(density) { onBoardPositioned(offset, totalCellSize.toPx()) }
                }
                .then(
                    if (boardTapActive) Modifier.pointerInput(cellPxForTap) {
                        detectTapGestures { off ->
                            val gx = (off.x / cellPxForTap).toInt()
                            val gy = (off.y / cellPxForTap).toInt()
                            if (gx in 0 until boardCols && gy in 0 until boardRows) {
                                val position = Position(gx, gy)
                                onCoilTap(position)
                            }
                        }
                    } else Modifier
                )
        ) {
            // Capa de bloques + VFX anclados a celdas (las vignettes de peligro quedan FUERA).
            Box(Modifier.matchParentSize()) {
            // (2026-08-28) La SILUETA del nivel se retiró: el tablero es siempre un 8×8 completo.
            val levelVoidCells = emptySet<com.cukiti.teikafusion.model.Position>()
            // (2026-09-22) Aquí iban LAS CELDAS DE COLOR, retiradas. La vidriera que las sustituye se
            // dibuja ENCIMA de los bloques, junto a las celdas calientes — ver más abajo.
            // ── P-233 · EL MOLDE ───────────────────────────────────────────────
            // Debajo de los bloques: vive en casillas libres, y si algo lo tapa se muda solo.
            MoldLayer(
                cells = gameState.moldCells,
                cellSize = totalCellSize,
                animationsOn = animationsOn,
            )
            // (2026-09-22) Aquí iban LA FILA PREMIADA y LA MARCA DEL ECO, retiradas. La piñata que
            // sustituye a la primera se dibuja ENCIMA de los bloques: cuelga por delante del tablero.
            // ── P-231 · LA NORIA ───────────────────────────────────────────────
            // Debajo de los bloques, como la cinta a la que sustituye: es la vía por la que viaja lo
            // que pongas en el borde, y taparla con esos bloques es la lectura correcta.
            if (gameState.hasWheel) {
                WheelRingLayer(
                    cols = boardCols,
                    rows = boardRows,
                    direction = com.cukiti.teikafusion.model.BoardCurrent
                        .directionFor(gameState.level, gameState.placementsInLevel),
                    turnsUntil = com.cukiti.teikafusion.model.BoardCurrent
                        .turnsUntil(gameState.placementsInLevel, gameState.level),
                    period = com.cukiti.teikafusion.model.BoardCurrent.periodFor(gameState.level),
                    cellSize = totalCellSize,
                    animationsOn = animationsOn,
                )
            }
            // ── El LIENZO: celdas vacías ───────────────────────────────────────
            // Van aparte de los bloques y NO viajan con el empuje del muro: la textura de la
            // rejilla tiene que quedarse pegada a sus líneas pase lo que pase.
            // Un solo lienzo para todas las casillas vacías (perf 2026-09-26). Eran hasta 64 `CellView`
            // —cada uno con dos `Animatable`, un efecto y tres cajas— para pintar un rectángulo
            // redondeado quieto, y en cada colocación y en cada rotura se destruían unos y se creaban
            // otros justo en el fotograma más cargado. Mismo dibujo, mismos píxeles: ver
            // [EmptyCellsLayer]. (La silueta del nivel se retiró el 2026-08-28: no hay celdas muertas.)
            EmptyCellsLayer(
                grid = gameState.grid,
                hidden = gameState.hiddenWoodCells,
                cols = boardCols,
                rows = boardRows,
                cellSize = totalCellSize,
            )

            // ── Grid lines ────────────────────────────────────────────────────
            // ORDEN (fix bug1/bug2, 2026-07-24): la rejilla se dibuja AQUÍ, sobre el lienzo vacío y
            // DEBAJO del muro y de los bloques. Estaba al final de la pila y pintaba sus líneas
            // ENCIMA de todo: los bloques del jugador salían cuadriculados (bug2) y el muro de
            // hormigón —que es una MASA MACIZA, esa es toda su identidad— aparecía con la rejilla
            // del tablero tatuada por dentro (bug1). La rejilla es la textura del hueco vacío; en
            // cuanto una celda se llena, deja de existir.
            // Más visibles: color claro con buen contraste sobre el tablero (#1F2643) + grosor en dp
            // (el 0.5f raw quedaba sub-píxel/invisible en pantallas densas).
            // ── LA SILUETA DEL NIVEL (2026-08-26f) ────────────────────────────
            // Las celdas muertas se PERFORAN sobre el panel, con la misma receta que los agujeros
            // del Glotón: un hueco de verdad, no una celda pintada de otro color. Así el jugador lee
            // «aquí no hay tablero» sin necesidad de que nadie se lo explique, que es justo lo que
            // esta variedad tiene que comunicar.
            if (levelVoidCells.isNotEmpty()) {
                // Capa propia (auditoría de fluidez 2026-09-29): dibujo estático que, sin capa, se
                // regrababa en cada fotograma en que animase cualquier hermano de este Box.
                Canvas(modifier = Modifier.width(boardW).height(boardH).graphicsLayer()) {
                    val step = totalCellSize.toPx()
                    levelVoidCells.forEach { p ->
                        val left = step * p.x
                        val top = step * p.y
                        // Sombra interior: el borde del agujero se hunde. Sin esto el recorte se lee
                        // plano y parece un bloque negro en vez de una ausencia.
                        drawRoundRect(
                            color = Color(0xFF0B1024),
                            topLeft = Offset(left + step * 0.04f, top + step * 0.04f),
                            size = Size(step * 0.92f, step * 0.92f),
                            cornerRadius = CornerRadius(step * 0.22f, step * 0.22f),
                        )
                        drawRoundRect(
                            color = Color(0xFF161C36),
                            topLeft = Offset(left + step * 0.10f, top + step * 0.12f),
                            size = Size(step * 0.80f, step * 0.76f),
                            cornerRadius = CornerRadius(step * 0.18f, step * 0.18f),
                        )
                    }
                }
            }

            // Capa propia: la rejilla no cambia nunca, que no se regrabe con cada VFX hermano.
            Canvas(modifier = Modifier.width(boardW).height(boardH).graphicsLayer()) {
                val step = totalCellSize.toPx()
                val lineColor = Color(0xFF4A5A95)
                val lineW = 1.3.dp.toPx()
                for (i in 1 until boardCols) {
                    drawLine(lineColor, Offset(step * i, 0f), Offset(step * i, size.height), strokeWidth = lineW)
                }
                for (i in 1 until boardRows) {
                    drawLine(lineColor, Offset(0f, step * i), Offset(size.width, step * i), strokeWidth = lineW)
                }
            }

            // ── Los BLOQUES ────────────────────────────────────────────────────
            // La regla del muro: solo empuja lo que TOCA.
            //   · `pushedCells` = la pila contigua apoyada en la pared. Viaja pegada a ella, con
            //     desplazamiento CONTINUO de sub-celda (la fracción de `tideHeight`), así que sube
            //     y baja sin un solo salto. Cuando la fracción completa una celda, la rejilla se
            //     pone al día y el desplazamiento vuelve a cero: el relevo es invisible.
            //   · El resto se queda QUIETO hasta que la pila que sube lo alcanza — y de ahí sale
            //     la compactación: los huecos se cierran por empuje, no por gravedad.
            // El hormigón NO se dibuja aquí: la pared entera la pinta TideFrontLayer como masa
            // continua (si se pintara además celda a celda, se vería la rejilla dentro del muro).
            BoardBlockCells(
                grid = gameState.grid,
                hidden = gameState.hiddenWoodCells,
                cols = boardCols,
                rows = boardRows,
                cellSize = totalCellSize,
                burningCells = burningCells,
                threatShakeCells = threatShakeCells,
                animationsOn = animationsOn,
                magmaHeatState = magmaHeatState,
                animatedIds = animatedIds,
            )

            // ── Volcán · EL MAGMA: fila que arde (telegrafía) y roca fundida (consecuencia). La roca
            //    no se apaga con «reducir efectos»: es información del tablero, no adorno.
            // ── Mansión Espectral · EL ESPEJO: fantasmas (consecuencia) y aviso del espejo (telegrafía).
            PhantomCellsLayer(
                phantoms = phantomCells,
                cellPx = with(density) { totalCellSize.toPx() },
                modifier = Modifier.width(boardW).height(boardH)
            )
            if (gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.GHOST) {
                MirrorOverlay(
                    grid = gameState.grid,
                    source = gameState.mirrorSource,
                    idleTurns = gameState.ghostIdleTurns,
                    threshold = com.cukiti.teikafusion.model.LevelDirector.mirrorThreshold(gameState.level),
                    unplaceable = gameState.unplaceableCells,
                    cellPx = with(density) { totalCellSize.toPx() },
                    modifier = Modifier.width(boardW).height(boardH)
                )
            }
            MagmaLayer(
                magmaCells = magmaCells,
                burningCells = if (animationsOn) burningCells else emptySet(),
                heat = magmaHeatState,
                cellPx = with(density) { totalCellSize.toPx() },
                modifier = Modifier.width(boardW).height(boardH)
            )

            // ── M-02 · Sheen RETIRADO (2026-07-31b, decisión del usuario) ─────────────────────
            // El brillo que cruzaba los bloques quietos cada 7 s se va. Venía de la referencia de
            // mercado (Block Blast) y era adorno puro: no enseñaba ninguna regla, y por eso ya era
            // lo primero que apagaba «reducir efectos». Con él se van un escaneo del grid por
            // recomposición, un `Path` de silueta por cambio de tablero y —lo peor— un `Path` nuevo
            // POR FRAME dentro del propio draw durante los 620 ms de cada pasada.
            // `BlockSheenSweep` se poda entero de VFX.kt: un composable sin llamante es el fósil que
            // este proyecto ya ha tenido que barrer tres veces.

            // (La rejilla ya NO se dibuja aquí: subió por encima del lienzo y por DEBAJO del muro y
            //  de los bloques — ver el bloque "Grid lines" más arriba. Pintarla la última tatuaba
            //  las líneas sobre la madera y sobre el hormigón macizo.)

            // ── Bioma Tóxico · EL VENENO ES UNA CAPA (2026-09-28): una sola masa de líquido sobre los
            //    bloques envenenados, con la lengua que asoma hacia lo que alcanzará (telegrafía). Las
            //    casillas que el avance está llenando las pinta su VFX hasta que termina (su ÚNICO
            //    consumidor es PoisonSpreadVFX, más abajo; aquí solo se mira a dónde va).
            val poisonSpreadEvents = eventBuckets.of<GameEvent.PoisonSpread>()
            if (gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.TOXIC ||
                gameState.grid.values.any { it is CellContent.Wood && it.poisoned }
            ) {
                val spreadingTo = poisonSpreadEvents.mapTo(HashSet()) { it.to }
                val poisonedCells = remember(gameState.grid, gameState.hiddenWoodCells, spreadingTo) {
                    gameState.grid.filter { (p, c) ->
                        c is CellContent.Wood && c.poisoned && p !in gameState.hiddenWoodCells && p !in spreadingTo
                    }.keys
                }
                ToxicLiquidLayer(
                    poisoned = poisonedCells,
                    front = gameState.toxicFront,
                    splashSoon = gameState.toxicFront.isEmpty() &&
                        gameState.toxicDecayCounter >= com.cukiti.teikafusion.model.LevelDirector.toxicSplashAfter(gameState.level) - 1,
                    cellPx = with(density) { totalCellSize.toPx() },
                    animationsOn = animationsOn,
                    modifier = Modifier.size(totalCellSize * GameState.GRID_SIZE)
                )
            }

            // ── Teikas ────────────────────────────────────────────────────────
            // Motor físico: los Teikas se dibujan como CUERPOS en TeikaPhysicsBodiesLayer (fuera
            // del Box recortado, más abajo). Aquí solo quedan los eventos de planta que la
            // abducción del Jardín sigue usando.
            val activePlantDragEvents = eventBuckets.of<GameEvent.PlantDrag>()
            val activePlantHurtEvents = eventBuckets.of<GameEvent.PlantHurt>()

            // Prune animatedIds — cacheado para no crear buildSet en cada recomposición
            val currentGridIds = remember(gameState.grid) {
                buildSet<String> {
                    gameState.grid.values.forEach { cell ->
                        if (cell is CellContent.Wood) add(cell.id)
                    }
                }
            }
            animatedIds.retainAll(currentGridIds)

            gameState.plantAbduction?.let { abduction ->
                PlantAbductionOverlay(
                    abduction = abduction,
                    cellSize = totalCellSize,
                    isMoving = activePlantDragEvents.any { it.teikaId == abduction.teikaId },
                )
            }

            // (Bioma Glaciar: la congelación de Teikas se retiró con el rework Escarcha — la escarcha
            //  sobre bloques se dibuja junto al hielo de habilidad, ver overlay de frozenCells.)

            // ── Bioma Fuego/Volcán: lava cubriendo las filas superiores (baja desde arriba) ──
            if (volcanoActive) {
                // AVISO EN DOS TIEMPOS (2026-09-23, pedido del usuario): nada al empezar, la cresta
                // asoma a dos colocaciones secas del descenso y hierve a una. La fase la decide
                // [lavaWarnPhase] — la misma que gobierna la viñeta, para que no cuenten distinto.
                val warn = lavaWarnPhase(gameState)
                LavaOverlay(
                    lavaRow = gameState.lavaRow,
                    cellSize = totalCellSize,
                    modifier = Modifier.width(boardW).height(boardH),
                    crestVisible = warn >= com.cukiti.teikafusion.model.VOLCANO_WARN_CREST,
                    imminent = warn >= com.cukiti.teikafusion.model.VOLCANO_WARN_BOIL
                )
            }

            // ── Cielo Estelar: EL PRESAGIO (2026-09-23) — el tablero se tiñe de oro (Sol) o de noche
            //    (Luna) durante el aviso, para que el jugador sepa qué astro viene. Se apaga cuando el
            //    astro asoma: desde ahí manda `AstroEventOverlay`.
            if (gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.STAR &&
                !gameState.starAstroEmerged && animationsOn
            ) {
                StarOmenOverlay(
                    omen = gameState.starAstroOmen,
                    strength = starOmenStrength(
                        gameState.starAstroIdleCounter,
                        com.cukiti.teikafusion.model.LevelDirector.starIdleThreshold(gameState.level)
                    ),
                    modifier = Modifier.width(boardW).height(boardH)
                )
            }

            // (Bioma Glotón: agujeros + boca se dibujan FUERA del Box interno, tras las celdas, para
            //  que el BlendMode.Clear perfore el tablero. Ver bloque al final del Box externo.)

            // C-4: listas de eventos pre-filtradas — calculadas UNA VEZ por cambio de eventos
            val particleEvents     = eventBuckets.of<GameEvent.Particles>()
            val megaTeikaEvents    = eventBuckets.of<GameEvent.MegaTeikaFriendExpansion>()
            val shatterEvents      = eventBuckets.of<GameEvent.LineCellShatter>()
            val lineBeamEvents     = eventBuckets.of<GameEvent.LineClearBeam>()
            val fusionBurstEvents  = eventBuckets.of<GameEvent.FusionBurst>()
            val crystalShatterEvents = eventBuckets.of<GameEvent.CrystalShatter>()
            val blockFleeEvents      = eventBuckets.of<GameEvent.BlockFlee>()
            val blockTrembleEvents   = eventBuckets.of<GameEvent.BlockTremble>()
            val coilChargedEvents    = eventBuckets.of<GameEvent.CoilCharged>()
            val coilShortEvents      = eventBuckets.of<GameEvent.CoilShortCircuit>()
            val coilDischargeEvents  = eventBuckets.of<GameEvent.CoilDischarge>()
            val glacierColdWaveEvents = eventBuckets.of<GameEvent.GlacierColdWave>()
            val starBurstEvents      = eventBuckets.of<GameEvent.StarBurst>()
            val poisonWashEvents     = eventBuckets.of<GameEvent.PoisonWash>()
            // P-70 / P-69: la Brocha pinta y la Línea Pura cobra. Un solo consumidor cada uno.
            val brushSweepEvents     = eventBuckets.of<GameEvent.BrushSweep>()
            val pureLineEvents       = eventBuckets.of<GameEvent.PureLine>()

            BrushSweepVFX(
                events = brushSweepEvents,
                cellSize = totalCellSize,
                gridW = gameState.gridW,
                gridH = gameState.gridH,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            PureLineVFX(
                events = pureLineEvents,
                cellSize = totalCellSize,
                gridW = gameState.gridW,
                gridH = gameState.gridH,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // P-71: el testarudo que aguanta y el que por fin cede.
            ReplacementRuleLayer(gameState, totalCellSize, rulePulses.filterNot { it.kind.startsWith("compactor") || it.kind.startsWith("orbit") || it.kind.startsWith("portal_swap") }, animationsOn && !reducedFx,
                onConsumed = { id -> onEventConsumed?.invoke(id) },bodies = teikaBodies)
            StubbornVFX(
                resists = eventBuckets.of<GameEvent.StubbornResist>(),
                breaks = eventBuckets.of<GameEvent.StubbornBreak>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // ── FAMILIA C (2026-09-04 · rework 2026-09-22) ─────────────────────
            // P-234: los barriles puestos y la reacción en cadena.
            BarrelLayer(
                grid = gameState.grid,
                cellSize = totalCellSize,
                animationsOn = animationsOn,
            )
            // P-256: los bloques-Teika puestos y su estallido al romperse (del que nace el Teika).
            TeikaSeedLayer(
                grid = gameState.grid,
                cellSize = totalCellSize,
                animationsOn = animationsOn,
                hidden = gameState.hiddenWoodCells,
            )
            ReactorSeedColorLayer(gameState,totalCellSize)
            TeikaSeedBurstVFX(
                events = eventBuckets.of<GameEvent.TeikaSeedBurst>(),
                cellSize = totalCellSize,
                animationsOn = animationsOn && !reducedFx,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            BarrelBlastVFX(
                events = eventBuckets.of<GameEvent.BarrelBlast>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // P-235: el huevo, su grieta y su eclosión.
            EggLayer(
                egg = gameState.eggCell,
                cracks = gameState.eggCracks,
                needed = com.cukiti.teikafusion.model.TeikaEgg.cracksFor(gameState.level),
                hatchType = com.cukiti.teikafusion.model.TeikaEgg.hatchTypeFor(gameState.level),
                cols = boardCols,
                rows = boardRows,
                cellSize = totalCellSize,
                animationsOn = animationsOn,
            )
            EggCrackVFX(
                events = eventBuckets.of<GameEvent.EggCrack>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            EggHatchVFX(
                events = eventBuckets.of<GameEvent.EggHatch>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // ── P-242 · LA PIÑATA: colgada, su golpe y su reventón ────────────
            PinataLayer(
                cell = gameState.pinataCell,
                hits = gameState.pinataHits,
                needed = com.cukiti.teikafusion.model.Pinata.hitsFor(gameState.level),
                dir = gameState.pinataDir,
                cols = boardCols,
                cellSize = totalCellSize,
                animationsOn = animationsOn,
            )
            PinataHitVFX(
                events = eventBuckets.of<GameEvent.PinataHit>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            PinataBurstVFX(
                events = eventBuckets.of<GameEvent.PinataBurst>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // ── P-236 · LA IMPLOSIÓN: la succión sobre la línea rota ──────────
            LineImplosionVFX(
                events = eventBuckets.of<GameEvent.LineImplosion>(),
                cols = boardCols,
                rows = boardRows,
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // ── P-233 · EL MOLDE: estalla, o se muda ──────────────────────────
            MoldStampVFX(
                events = eventBuckets.of<GameEvent.MoldStamp>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            MoldMovedVFX(
                events = eventBuckets.of<GameEvent.MoldMoved>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // ── P-241 · LA BRÚJULA: el giro (el indicador fijo va POR FUERA, en el Box externo) ──
            CompassTurnVFX(
                events = eventBuckets.of<GameEvent.CompassTurn>(),
                cols = boardCols,
                rows = boardRows,
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // ── P-243 · LOS PORTALES: cada paso y cada mudanza (las bocas, POR FUERA: Box externo) ──
            PortalHopVFX(
                events = eventBuckets.of<GameEvent.PortalHop>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            PortalsMovedVFX(
                events = eventBuckets.of<GameEvent.PortalsMoved>(),
                cols = boardCols,
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // EL REBOTE (2026-09-05): los dos frentes que se abren desde la línea rota. Dibuja la
            // CAUSA del empujón; la consecuencia la enseñan los propios cuerpos al salir volando.
            LineRecoilWaveVFX(
                events = eventBuckets.of<GameEvent.LineRecoilWave>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // P-237: el golpe en CADA cuerpo que la línea empujó.
            RecoilPushVFX(
                events = eventBuckets.of<GameEvent.RecoilPush>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // P-147: el reflejo, que cae lejos del dedo y hay que ir a buscarlo.
            MirrorStampVFX(
                events = eventBuckets.of<GameEvent.MirrorStamp>(),
                cellSize = totalCellSize,
                boardWidth = boardCols,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // P-72: el latido de la ficha que acaba de adoptar color.
            ChameleonAdoptVFX(
                events = eventBuckets.of<GameEvent.ChameleonAdopt>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // ── P-240 · EL IMÁN ES UNA FICHA ───────────────────────────────────
            // La herradura puesta, su campo, un hilo a cada Teika que atrae y sus turnos; más la
            // onda al ponerlo y la rotura al acabar.
            MagnetLayer(
                grid = gameState.grid,
                turnsLeft = gameState.magnetTurnsLeft,
                range = com.cukiti.teikafusion.model.MagnetPiece.rangeFor(gameState.level),
                bodiesFlow = teikaBodies,
                cellSize = totalCellSize,
                animationsOn = animationsOn,
                hidden = gameState.hiddenWoodCells,
            )
            MagnetPlacedVFX(
                events = eventBuckets.of<GameEvent.MagnetPlaced>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            MagnetBreakVFX(
                events = eventBuckets.of<GameEvent.MagnetBreak>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // ── P-239 · TEIKAS DE PLOMO ─────────────────────────────────────────
            // El cuerpo armado y el bloque al que apunta, ANTES del golpe; y el golpe.
            LeadFallLayer(
                bodiesFlow = teikaBodies,
                grid = gameState.grid,
                crushSpeed = com.cukiti.teikafusion.model.LeadTeikas.crushSpeedFor(
                    gameState.level, com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld.GRAVITY
                ),
                cellSize = totalCellSize,
            )
            LeadCrushVFX(
                events = eventBuckets.of<GameEvent.LeadCrush>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // ── P-244 · LA VIDRIERA ─────────────────────────────────────────────
            // Encima de los bloques, como la celda caliente: es una marca del suelo.
            StainedGlassLayer(
                glass = gameState.glassCells,
                cracked = gameState.glassCracked,
                cellSize = totalCellSize,
                animationsOn = animationsOn,
                covered = remember(gameState.grid, gameState.glassCells) {
                    gameState.glassCells.filterTo(HashSet()) { gameState.grid[it]?.isOccupied == true }
                },
            )
            GlassCrackVFX(
                events = eventBuckets.of<GameEvent.GlassCrack>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            GlassShatterVFX(
                events = eventBuckets.of<GameEvent.GlassShatter>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // P-78: el anillo dorado en reposo + el reventón al cobrarlo.
            HotCellsLayer(
                cells = gameState.hotCells,
                cellSize = totalCellSize,
                animationsOn = animationsOn,
                covered = remember(gameState.grid, gameState.hotCells) {
                    gameState.hotCells.filterTo(HashSet()) { gameState.grid[it]?.isOccupied == true }
                },
            )
            HotCellPopVFX(
                events = eventBuckets.of<GameEvent.HotCellPop>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // (RETIRADO 2026-09-26) Aquí se dibujaba LA PORTERÍA (P-81) y su entrega.

            // ── Partículas ─────────────────────────────────────────────────────
            // PERF: TODAS las ráfagas en UN solo Canvas/reloj (antes era un ParticleExplosion
            // full-screen + Animatable por evento → tirones al emitirse muchas a la vez).
            BatchParticleExplosionVFX(
                events = particleEvents,
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )

            // ── Confeti de COLOCACIÓN: chispas por el contorno de la ficha recién posada ──
            // Vale para los tres skins (es feedback de "colocaste", no identidad de skin).
            val placePopEvents = eventBuckets.of<GameEvent.PiecePlacedPop>()
            BatchPlacePopVFX(
                events = placePopEvents,
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )

            // (VFX de fusión/empuje por celdas ELIMINADOS: el motor físico anima los cuerpos
            //  directamente — la fusión es contacto + pop en TeikaPhysicsBodiesLayer.)

            activePlantDragEvents.forEach { event ->
                key(event.id) {
                    PlantDragVFX(
                        event = event,
                        cellSize = totalCellSize,
                        startDelayMillis = if (activePlantHurtEvents.any { it.teikaId == event.teikaId }) 220 else 0,
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Bioma Jardín: la planta recibe daño al romper una línea (aún sujeta al Teika) ──
            activePlantHurtEvents.forEach { event ->
                key(event.id) {
                    PlantHurtVFX(
                        event = event,
                        cellSize = totalCellSize,
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // (El rótulo de combo de tiers 1-4 [ComboTextVFX] se dibuja en el Box EXTERNO, después de
            //  TeikaPhysicsBodiesLayer: aquí dentro quedaba DEBAJO de los cuerpos de Teika y un cuerpo
            //  en el centro tapaba el "¡BIEN!/¡GENIAL!". Ver el bloque junto a los FloatingScore.)

            // ── Mega Teika — irrumpe llenando el tablero y EMPUJA todo fuera (todos los modos) ──
            megaTeikaEvents.forEach { event ->
                key(event.id) {
                    InfiniteMegaTeikaExplosionVFX(
                        event      = event,
                        cellSize   = totalCellSize,
                        reducedFx  = reducedFx,
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Line cell shatter (rotura premium dominó tipo Block Blast) ────
            // Skin DERRUMBE: se monta en el Box EXTERNO (sin clip) para que los cubos
            // desprendidos CAIGAN fuera del borde del tablero sin recortarse. Un solo
            // consumidor siempre: aquí O afuera, nunca ambos.
            if (shatterEvents.isNotEmpty() && LocalBlockSkin.current.id != BlockSkins.fall3d.id) {
                BatchLineCellShatterVFX(
                    events = shatterEvents,
                    cellSize = totalCellSize,
                    onEventComplete = { id -> onEventConsumed?.invoke(id) }
                )
            }

            // ── Haz de luz cromático que barre cada fila/columna completada (ENCIMA de los cubos) ──
            lineBeamEvents.forEach { event ->
                key(event.id) {
                    LineClearBeamVFX(
                        event = event,
                        cellSize = totalCellSize,
                        boardCols = boardCols,
                        boardRows = boardRows,
                        wallTopRow = { boardRows.toFloat() },
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Bioma Cristal: estallido de la cáscara de vidrio al romperse de verdad ──
            // P-370: todas las cáscaras en UN lienzo y un reloj (antes, un Canvas por casilla).
            BatchCrystalShatterVFX(
                events = crystalShatterEvents,
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) },
            )

            // ── Bioma Cristal: el golpe que la armadura AGUANTA (grietas, no estallido) ──
            eventBuckets.of<GameEvent.CrystalCrack>().forEach { event ->
                key(event.id) {
                    CrystalCrackVFX(
                        event = event,
                        cellSize = totalCellSize,
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Bioma Volcán: la ERUPCIÓN — frente, salpicadura y brasas al comerse una fila ──
            eventBuckets.of<GameEvent.LavaSurge>().forEach { event ->
                key(event.id) {
                    LavaSurgeVFX(
                        event = event,
                        cellSize = totalCellSize,
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Mansión Espectral · EL ESPEJO: destello (impacto) y fantasmas que se desvanecen ──
            eventBuckets.of<GameEvent.MirrorFlash>().forEach { event ->
                key(event.id) {
                    MirrorFlashVFX(event, with(density) { totalCellSize.toPx() }) { onEventConsumed?.invoke(event.id) }
                }
            }
            eventBuckets.of<GameEvent.PhantomFade>().forEach { event ->
                key(event.id) {
                    PhantomFadeVFX(event, with(density) { totalCellSize.toPx() }) { onEventConsumed?.invoke(event.id) }
                }
            }

            // ── Volcán · EL MAGMA: el temple — la fila que ardía se funde (impacto) ──
            eventBuckets.of<GameEvent.MagmaForge>().forEach { event ->
                key(event.id) {
                    MagmaForgeVFX(
                        event = event,
                        cellPx = with(density) { totalCellSize.toPx() },
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Bosque del Sueño: ENTRA LA MAITE — sopla y adormece el tablero ──
            eventBuckets.of<GameEvent.DrowsyOnset>().forEach { event ->
                key(event.id) {
                    DrowsyOnsetVFX(
                        event = event,
                        cellPx = with(density) { totalCellSize.toPx() },
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Clímax de fusión de Teikas: onda de choque mate + destello de tier ──
            fusionBurstEvents.forEach { event ->
                key(event.id) {
                    FusionBurstVFX(
                        event = event,
                        cellSize = totalCellSize,
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Bioma Miedoso: bloques huyendo + temblores ────────────────────
            blockFleeEvents.forEach { event ->
                key(event.id) {
                    BlockFleeVFX(
                        event = event,
                        cellSize = totalCellSize,
                        onComplete = {
                            // Revelar la celda destino EN el frame en que el vuelo encaja — el
                            // grid y el VFX (que sigue dibujando el reposo) se solapan idénticos.
                            onRevealWood?.invoke(setOf(event.to), false)
                            onEventConsumed?.invoke(event.id)
                        }
                    )
                }
            }
            blockTrembleEvents.forEach { event ->
                key(event.id) {
                    BlockTrembleVFX(
                        event = event,
                        cellSize = totalCellSize,
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Maite / desplome: bloques cayendo con gravedad (ease-in + squash) ──
            val woodFallEvents = eventBuckets.of<GameEvent.WoodFall>()
            woodFallEvents.forEach { event ->
                key(event.id) {
                    WoodFallVFX(
                        event = event,
                        cellSize = totalCellSize,
                        reducedEffects = reducedFx && (com.cukiti.teikafusion.model.AvalancheRules.appliesTo(gameState.level) || com.cukiti.teikafusion.model.CompactorRules.appliesTo(gameState.level) || event.portalOrigin!=null || event.orbitCenter!=null),
                        onComplete = {
                            // Revelar al ENCAJAR la caída (mismo reloj que la animación) — sin
                            // hueco de frames contra el bucle temporizado del ViewModel.
                            onRevealWood?.invoke(setOf(event.to), false)
                            onEventConsumed?.invoke(event.id)
                        }
                    )
                }
            }

            // ── P-79 · LA CORRIENTE: el barrido que cruza el tablero ──────────
            // Va DENTRO del Box recortado, como los arrastres que acompaña: las estelas nacen y
            // mueren fuera del borde y es el `clip(boardShape)` quien las corta con la curva.
            val currentFlowEvents = eventBuckets.of<GameEvent.BoardCurrentFlow>()
            currentFlowEvents.forEach { event ->
                key(event.id) {
                    // P-231: la noria gira alrededor del marco; su barrido no cruza el tablero.
                    if (event.ring) WheelFlowVFX(
                        event = event,
                        cols = boardCols,
                        rows = boardRows,
                        cellSize = totalCellSize,
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    ) else BoardCurrentFlowVFX(
                        event = event,
                        cellSize = totalCellSize,
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Bioma Eléctrico: descargas / cortocircuito / cadena ────────────
            coilChargedEvents.forEach { event ->
                key(event.id) {
                    CoilChargedVFX(event = event, cellSize = totalCellSize, onComplete = { onEventConsumed?.invoke(event.id) })
                }
            }
            coilShortEvents.forEach { event ->
                key(event.id) {
                    CoilShortCircuitVFX(event = event, cellSize = totalCellSize, onComplete = { onEventConsumed?.invoke(event.id) })
                }
            }
            coilDischargeEvents.forEach { event ->
                key(event.id) {
                    CoilDischargeVFX(event = event, cellSize = totalCellSize, onComplete = { onEventConsumed?.invoke(event.id) })
                }
            }


            // ── Glaciar (rework): oleada de frío que barre el tablero y congela un Teika ──
            glacierColdWaveEvents.forEach { event ->
                key(event.id) {
                    GlacierColdWaveVFX(event = event, cellSize = totalCellSize, onComplete = { onEventConsumed?.invoke(event.id) })
                }
            }

            // ── Bioma Tóxico: el veneno fluye a la casilla vecina (o salpica lejos) ─────────
            poisonSpreadEvents.forEach { event ->
                key(event.id) {
                    PoisonSpreadVFX(event = event, cellSize = totalCellSize, onComplete = { onEventConsumed?.invoke(event.id) })
                }
            }
            // ── Bioma Tóxico: una línea lava el veneno de un bloque (queda al descubierto) ──
            poisonWashEvents.forEach { event ->
                key(event.id) {
                    PoisonWashVFX(event = event, cellSize = totalCellSize, onComplete = { onEventConsumed?.invoke(event.id) })
                }
            }

            // ── Block / star burst con física sobre la celda destruida ─────────
            starBurstEvents.forEach { event ->
                key(event.id) {
                    val center = Offset(
                        x = with(density) { (totalCellSize * event.position.x + totalCellSize / 2f).toPx() },
                        y = with(density) { (totalCellSize * event.position.y + totalCellSize / 2f).toPx() }
                    )
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (event.colorType >= 0) {
                            // Cubitos 3D del color del bloque (estilo Block Blast).
                            BlockBurstExplosion(
                                center    = center,
                                colorType = event.colorType,
                                intensity = event.intensity,
                                delayMs   = event.delayMs,
                                onComplete = { onEventConsumed?.invoke(event.id) }
                            )
                        } else {
                            // Fallback: estrellas/gemas doradas (combos, derrota).
                            StarParticleExplosion(
                                center    = center,
                                isGem     = event.isGem,
                                intensity = event.intensity,
                                delayMs   = event.delayMs,
                                onComplete = { onEventConsumed?.invoke(event.id) }
                            )
                        }
                    }
                }
            }

            // (El marco arcoíris del combo y las partículas se dibujan a nivel de PANTALLA
            //  completa en GameScreen → el marco abraza el borde del teléfono, no el tablero,
            //  y las partículas pueden salir por toda la pantalla sin recorte.)

            // (El aviso "⚠ GRACIA" [ComboGraceVFX] también se dibuja en el Box EXTERNO: aquí quedaba
            //  debajo de los Teikas — una alarma que un cuerpo podía tapar — y su subida de 80 px se
            //  recortaba contra el clipToBounds cuando la ficha se colocaba en las filas altas.)

            // ── Hielo sobre bloques: habilidad Hielo (frozenCells) + ESCARCHA del bioma Glaciar
            //    (glacierFrostCells, rework): mismo dibujo de placa helada — ninguna de las dos
            //    deja romper el bloque, así que comparten lenguaje visual.
            val icedCells = gameState.frozenCells + gameState.glacierFrostCells
            if (icedCells.isNotEmpty()) {
                // Capa propia: dibujo estático que dura turnos (escarcha del Glaciar) y, sin capa, se
                // regrababa cada vez que animaba un hermano.
                Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
                    val step = size.width / boardCols
                    val r = androidx.compose.ui.geometry.CornerRadius(step * 0.18f, step * 0.18f)
                    icedCells.forEach { fp ->
                        val tl = Offset(fp.x * step, fp.y * step)
                        drawRoundRect(
                            color = Color(0xFF80D8FF).copy(alpha = 0.45f),
                            topLeft = tl, size = Size(step, step), cornerRadius = r
                        )
                        drawRoundRect(
                            color = Color(0xFFE1F5FE).copy(alpha = 0.92f),
                            topLeft = tl, size = Size(step, step), cornerRadius = r,
                            style = Stroke(width = step * 0.07f)
                        )
                        // Escarcha (cruz sutil)
                        val c = Offset(tl.x + step / 2f, tl.y + step / 2f)
                        drawLine(Color.White.copy(alpha = 0.85f), Offset(c.x - step * 0.18f, c.y), Offset(c.x + step * 0.18f, c.y), strokeWidth = step * 0.05f)
                        drawLine(Color.White.copy(alpha = 0.85f), Offset(c.x, c.y - step * 0.18f), Offset(c.x, c.y + step * 0.18f), strokeWidth = step * 0.05f)
                    }
                }
            }

            // ── VFX de habilidades de compañero (CompanionAbilityFired) ───
            val companionEvents  = eventBuckets.of<GameEvent.CompanionAbilityFired>()
            companionEvents.forEach { event ->
                key(event.id) {
                    val cxPx = with(density) { (totalCellSize * event.position.x + totalCellSize / 2f).toPx() }
                    val cyPx = with(density) { (totalCellSize * event.position.y + totalCellSize / 2f).toPx() }
                    val cellPx = with(density) { totalCellSize.toPx() }
                    CompanionAbilityVFX(
                        companionId = event.companionId,
                        center = Offset(cxPx, cyPx),
                        cellPx = cellPx,
                        axis = event.axis,
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Energía Cruzada (Rayco): aura eléctrica persistente sobre los Teikas energizados ──
            // (El glow gemelo de la vieja «Sobrecarga» se podó con su habilidad el 2026-07-30;
            //  OverloadChargeGlow sigue vivo porque es el aura de ESTA.)
            val energizedIds by energizedTeikaIds.collectAsState()
            if (energizedIds.isNotEmpty()) {
                // Sin `by` (auditoría de fluidez 2026-09-29): leer los cuerpos aquí suscribía este bloque
                // del tablero a CADA tick de física mientras hubiera Teikas energizados.
                val bodiesE = teikaBodies.collectAsState()
                OverloadChargeGlow(bodiesState = bodiesE, selectedIds = energizedIds, cellPx = with(density) { totalCellSize.toPx() })
            }
            val overloadBeamEvents = eventBuckets.of<GameEvent.OverloadBeam>()
            overloadBeamEvents.forEach { event ->
                key(event.id) {
                    OverloadBeamVFX(event = event, cellPx = with(density) { totalCellSize.toPx() }, onComplete = { onEventConsumed?.invoke(event.id) })
                }
            }
            // ── Cruz de Fuego (Iñaki) ──
            val fireCrossEvents = eventBuckets.of<GameEvent.FireCross>()
            fireCrossEvents.forEach { event ->
                key(event.id) { FireCrossVFX(event, with(density) { totalCellSize.toPx() }) { onEventConsumed?.invoke(event.id) } }
            }
            // ── Estalactitas (Nieves) ──
            val icicleEvents = eventBuckets.of<GameEvent.IcicleDrop>()
            icicleEvents.forEach { event ->
                key(event.id) { IcicleDropVFX(event, with(density) { totalCellSize.toPx() }) { onEventConsumed?.invoke(event.id) } }
            }
            // ── Miasma Venenoso (Toño) ──
            val miasmaEvents = eventBuckets.of<GameEvent.PoisonMiasma>()
            miasmaEvents.forEach { event ->
                key(event.id) { PoisonMiasmaVFX(event, with(density) { totalCellSize.toPx() }) { onEventConsumed?.invoke(event.id) } }
            }
            // ── Parto Real (Reynaldo) ──
            val birthEvents = eventBuckets.of<GameEvent.TeikaBirth>()
            birthEvents.forEach { event ->
                key(event.id) { TeikaBirthVFX(event, with(density) { totalCellSize.toPx() }) { onEventConsumed?.invoke(event.id) } }
            }
            // (Siesta / filas-bonus de Maite: ahora son WoodFall independientes — ver arriba.)
            // ── Cañón Floral (Flora): CEREMONIA — flor que se abre, Teika que salta, entierro ──
            val floraCeremonyEvents = eventBuckets.of<GameEvent.FloraCeremony>()
            floraCeremonyEvents.forEach { event ->
                key(event.id) {
                    FloraCeremonyVFX(event, totalCellSize, with(density) { totalCellSize.toPx() }) {
                        onEventConsumed?.invoke(event.id)
                    }
                }
            }
            // ── Corte de Sombras (Kenji): tajos que cruzan la pantalla + bloques partidos ──
            val shadowCutEvents = eventBuckets.of<GameEvent.ShadowBladeCut>()
            shadowCutEvents.forEach { event ->
                key(event.id) { ShadowBladeCutVFX(event, with(density) { totalCellSize.toPx() }) { onEventConsumed?.invoke(event.id) } }
            }
            // (PODADO 2026-07-31) Colector de `Dematerialize` (Morti / Desvanecer): la habilidad de
            //  Morti es el Tembleque desde las its. 52-54 y nadie emitía ya el evento.

            // ── Timoteo / PUZZLE DESLIZANTE: la rejilla de rompecabezas antes de las losetas ──
            val puzzleGridEvents = eventBuckets.of<GameEvent.PuzzleGridReveal>()
            puzzleGridEvents.forEach { event ->
                key(event.id) {
                    PuzzleGridRevealVFX(
                        event = event,
                        cellPx = with(density) { totalCellSize.toPx() },
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Cristóbal / ESCUDO CRISTALINO: blindaje + escaneo antes de que vuelen las fichas ──
            val aegisEvents = eventBuckets.of<GameEvent.CrystalAegis>()
            aegisEvents.forEach { event ->
                key(event.id) {
                    CrystalAegisVFX(
                        event = event,
                        cellPx = with(density) { totalCellSize.toPx() },
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Rayco / ENERGÍA CRUZADA: el rayo que energiza cada Teika elegido ──
            val energizeEvents = eventBuckets.of<GameEvent.EnergizeStrike>()
            energizeEvents.forEach { event ->
                key(event.id) {
                    EnergizeStrikeVFX(
                        event = event,
                        cellPx = with(density) { totalCellSize.toPx() },
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Maite / SIESTA: ola de sueño bajando + nube que pare las filas-bonus ──
            val drowsyEvents = eventBuckets.of<GameEvent.DrowsySpell>()
            drowsyEvents.forEach { event ->
                key(event.id) {
                    DrowsySpellVFX(
                        event = event,
                        cellPx = with(density) { totalCellSize.toPx() },
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Galo / ECLIPSE VIVO: noche + corona, lente, implosión y vuelta de la luz ──
            val eclipseEvents = eventBuckets.of<GameEvent.LivingEclipse>()
            eclipseEvents.forEach { event ->
                key(event.id) {
                    LivingEclipseVFX(
                        event = event,
                        cellPx = with(density) { totalCellSize.toPx() },
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Morti / POSESIÓN: bloques espectrales atravesando el tablero hasta su hueco ──
            val possessionEvents = eventBuckets.of<GameEvent.GhostPossession>()
            possessionEvents.forEach { event ->
                key(event.id) {
                    GhostPossessionVFX(
                        event = event,
                        cellPx = with(density) { totalCellSize.toPx() },
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // ── Chueta / EL BANQUETE: fauces en el borde inferior + estrellas cayendo dentro ──
            val gluttonFeastEvents = eventBuckets.of<GameEvent.GluttonFeast>()
            gluttonFeastEvents.forEach { event ->
                key(event.id) {
                    GluttonFeastVFX(
                        event = event,
                        cellPx = with(density) { totalCellSize.toPx() },
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }

            // (Bioma Glotón: dentellada/retirada se dibujan FUERA del Box interno — ver final del Box externo.)

            // (P-294, 2026-09-25) La BRECHA vivía aquí; el METEORITO que la sustituye va en el Box
            // EXTERNO, junto a la espada del Rey: entra desde fuera del tablero.

            // ── Cielo Estelar / Evento Astral: los overlays del Sol/Luna viven en el Box EXTERNO (tras
            //    TeikaPhysicsBodiesLayer) — el láser/halo debe pintarse SOBRE los Teikas y el astro
            //    entra/sale por ENCIMA del borde del tablero (el clip de este Box lo recortaba). ──


            }   // ← cierre de la capa de bloques/VFX anclados a celdas (ver apertura arriba)

            // ── #8 Tablero Viviente: vignette de peligro ──────────────────
            // Solo se monta (y solo entonces anima su pulso) cuando hay peligro real,
            // evitando una infiniteTransition corriendo siempre a 60 fps.
            if (dangerShown && animationsOn) {
                DangerVignette(
                    intensity = dangerNow,
                    fillFraction = fillFraction,
                    boardCorner = BOARD_CORNER
                )
            }
            // (RETIRADA 2026-07-25, petición del usuario tras `art/puntosrojos.jpeg`: la marquesina
            //  de bombillas del perímetro con el tablero >80 % lleno. Con el tablero apretado el ojo
            //  ya tiene la vignette roja y el marco encendido; los puntitos corriendo por el borde
            //  eran ruido justo en el momento en que hay que leer huecos. `BoardEdgeMarquee` se
            //  borró con ella.)
            val plantThreatActive = (gameState.plantIdleTurns > 0 || gameState.plantAbduction != null) &&
                animationsOn
            if (plantThreatActive) {
                PlantThreatVignette(
                    idleTurns = gameState.plantIdleTurns,
                    abduction = gameState.plantAbduction,
                    redDanger = dangerNow
                )
            }
            if (volcanoActive && gameState.lavaRow == 0 && animationsOn &&
                lavaWarnPhase(gameState) == com.cukiti.teikafusion.model.VOLCANO_WARN_BOIL
            ) {
                VolcanoThreatVignette(
                    lavaRow = gameState.lavaRow,
                    dryTurns = gameState.volcanoDryTurns,
                    redDanger = dangerNow
                )
            }
            // Glaciar: la oleada de frío ahora SE VE VENIR (patrón de telegrafía por estado, como la
            // planta y el volcán). La escarcha repta desde los bordes con cada turno ocioso; antes la
            // congelación del Teika caía sin ningún aviso — el único castigo de bioma sin telegrafía.
            val glacierActive =
                gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.GLACIER
            if (glacierActive && gameState.glacierIdleTurns > 0 && animationsOn) {
                GlacierThreatVignette(
                    idleTurns = gameState.glacierIdleTurns,
                    freezeAfter = com.cukiti.teikafusion.model.LevelDirector.glacierWaveEvery(gameState.level),
                    redDanger = dangerNow
                )
            }
            // Bosque del Sueño: PÁRPADOS PESADOS (P-68). Era el ÚLTIMO bioma sin telegrafía — la
            // Maite caía a pelo y el jugador perdía la rotación sin haber visto venir nada. Mismo
            // patrón por estado que la planta, el volcán y el glaciar: `sleepIdleTurns` existía en
            // `GameState` desde siempre y NADIE lo leía en la UI.
            val sleepActive =
                gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.SLEEP
            if (sleepActive && gameState.sleepIdleTurns > 0 && animationsOn) {
                DrowsyDroopOverlay(idleTurns = gameState.sleepIdleTurns, level = gameState.level)
            }
        }
        // ── Motor físico Suika: cuerpos circulares de Teika ───────────────────
        // Viven en el Box EXTERNO (sin clipToBounds del tablero) a propósito: una pila alta puede
        // SOBRESALIR por el borde superior del tablero sin recortarse (sin derrota por
        // desborde). PERO por los LADOS y por ABAJO sí se recorta (fix4/fix5): la "burbuja"
        // visual ([TEIKA_VISUAL_SCALE] = 1,22) desborda el radio físico y un cuerpo apoyado en un
        // borde se dibujaba FUERA del panel, flotando sobre la bandeja/el margen — parecía un bug
        // de física siendo solo dibujo. El clip deja el techo abierto (top = -1 tablero).
        // El origen (0,0) coincide con el del Box interno. El cuerpo raptado
        // por la planta (Jardín) se OCULTA: su viaje lo dibujan PlantDrag/PlantAbduction.
        Box(
            Modifier
                .width(boardW)
                .height(boardH)
                .drawWithContent {
                    clipRect(0f, -size.height, size.width, size.height) {
                        this@drawWithContent.drawContent()
                    }
                }
        ) {
            // Ceremonia de fusión: los cuerpos reales conservan su key/Lottie y viajan al centro.
            // El VFX externo sólo aporta atmósfera; no monta copias que reinicien la animación.
            val fusionChargeEvents = eventBuckets.of<GameEvent.FusionCharge>()
            // Colapso estelar (T4+T4): su vacío va DEBAJO de los cuerpos para no apagar a los T4
            // que orbitan. No consume: lo hace su capa de frente, más abajo.
            if (!reducedFx) fusionChargeEvents.forEach { event ->
                if (event.tier >= 4) key(event.id) {
                    MegaCollapseVFX(event, totalCellSize, front = false)
                }
            }
            TeikaPhysicsBodiesLayer(
                bodiesFlow = teikaBodies,
                cellSize = totalCellSize,
                hiddenIds = ReactorHiddenBodies(eventBuckets.of<GameEvent.RulePulse>(),animationsOn && !reducedFx) + setOfNotNull(gameState.plantAbduction?.teikaId) +
                    plantRescueEvents.map { it.teikaId },
                fusionCharges = fusionChargeEvents,
                pinballImpacts = pinballImpacts,
                tutorialLessonFour = gameState.level ==
                    com.cukiti.teikafusion.model.TutorialCampaign.LAST_LEVEL,
                tutorialIdleActive = tutorialStageReady,
                reducedFx = reducedFx
            )
            plantRescueEvents.forEach { event ->
                key(event.id) {
                    PlantRescueVFX(event, teikaBodies, totalCellSize) {
                        onEventConsumed?.invoke(event.id)
                    }
                }
            }
            // ── Glaciar (rework): bloque de HIELO persistente ENCIMA de cada Teika congelado ──
            // Va DESPUÉS de TeikaPhysicsBodiesLayer (mismo origen 0,0) para que el hielo se pinte
            // SOBRE el cuerpo — si no, el Teika tapaba la escarcha y parecía no estar congelado.
            FrozenBodiesOverlay(
                bodiesFlow = teikaBodies,
                cellSize = totalCellSize
            )
            // ── P-238 · LAS POMPAS: encima del cuerpo, como el hielo ──────────
            BubbleLayer(
                bodiesFlow = teikaBodies,
                cellSize = totalCellSize,
                animationsOn = animationsOn,
            )
            BubblePopVFX(
                events = eventBuckets.of<GameEvent.BubblePop>(),
                cellSize = totalCellSize,
                onEventComplete = { id -> onEventConsumed?.invoke(id) }
            )
            // ── Tormenta: el NÚMERO de la bobina, por encima del cuerpo que la tape ──────────
            // Mismo motivo que el hielo de arriba, y con peor consecuencia: un número tapado no
            // se ve feo, deja la fila IMPOSIBLE de descargar. Ver [CoilsOverBodiesOverlay].
            CoilsOverBodiesOverlay(
                grid = gameState.grid,
                bodiesFlow = teikaBodies,
                cellSize = totalCellSize
            )
            // ── Atmósfera de anticipación: foco + punto de contacto sobre los cuerpos reales ──
            fusionChargeEvents.forEach { event ->
                key(event.id) {
                    if (event.tier >= 4 && !reducedFx) {
                        // T4+T4: el colapso estelar pinta su propio vacío, espiral y singularidad.
                        MegaCollapseVFX(event, totalCellSize, front = true) {
                            onEventConsumed?.invoke(event.id)
                        }
                    } else {
                        FusionChargeVFX(event, totalCellSize, reducedFx) {
                            onEventConsumed?.invoke(event.id)
                        }
                    }
                }
            }
        }
        // ── P-293 · LO QUE VA POR FUERA DE LAS CASILLAS ────────────────────────
        // En el Box externo, que no recorta: la barra de la brújula y las bocas de los portales se
        // dibujan pegadas al borde del tablero, sin tapar ni una casilla. Mismo tamaño que el tablero,
        // así que (0,0) sigue siendo la esquina de la casilla (0,0).
        Box(Modifier.width(boardW).height(boardH)) {
            if (com.cukiti.teikafusion.model.Compass.appliesTo(gameState.level)) {
                val p = gameState.placementsInLevel
                CompassLayer(
                    dir = com.cukiti.teikafusion.model.Compass.dirFor(gameState.level, p),
                    nextDir = com.cukiti.teikafusion.model.Compass.dirFor(
                        gameState.level, p + com.cukiti.teikafusion.model.Compass.turnsUntil(gameState.level, p)),
                    turnsUntil = com.cukiti.teikafusion.model.Compass.turnsUntil(gameState.level, p),
                    period = com.cukiti.teikafusion.model.Compass.periodFor(gameState.level),
                    cols = boardCols,
                    rows = boardRows,
                    cellSize = totalCellSize,
                    animationsOn = animationsOn,
                )
            }
            PortalsLayer(
                rows = com.cukiti.teikafusion.model.Portals.rowsFor(gameState.level, gameState.placementsInLevel),
                grid = gameState.grid,
                cols = boardCols,
                cellSize = totalCellSize,
                animationsOn = animationsOn,
            )
        }
        // ── Skin DERRUMBE: su rotura NO se monta aquí ─────────────────────────
        // Rotura forzada por un Teika: encima de los cuerpos para que el impacto y las esquirlas
        // no queden ocultos justo cuando el cuerpo entra al hueco liberado.
        eventBuckets.of<GameEvent.TeikaPushBreak>().forEach { event ->
            key(event.id) {
                TeikaPushBreakVFX(
                    event = event,
                    cellSize = totalCellSize,
                    reducedFx = reducedFx,
                    onComplete = { onEventConsumed?.invoke(event.id) }
                )
            }
        }
        // Los cubos desprendidos caen hasta SALIR de la pantalla por abajo, así que tienen que
        // pasar por encima de la bandeja de fichas y del resto de la interfaz. Cualquier Box de
        // GameBoard queda por DEBAJO de la bandeja (es un hermano posterior en la Column de
        // GameScreen), de modo que el cubo se metía detrás de ella a mitad de caída. El mount vive
        // en la capa full-screen de GameScreen, desplazada a la esquina del tablero.
        // Contrato de un solo consumidor: aquí O allí, nunca ambos (ver el `if` de shatterEvents).
        // ── Cielo Estelar / Evento Astral: Sol/Luna BAJAN DESDE ARRIBA + neblina + víctima en vivo ──
        // Box EXTERNO (sin clip) a propósito: el astro desciende/asciende por FUERA del borde superior
        // del tablero sin recortarse, y va DESPUÉS de TeikaPhysicsBodiesLayer para que el láser, el halo
        // de la víctima y la explosión se pinten SOBRE los cuerpos de Teika (antes quedaban debajo).
        // ── P-353 · LAS MARCAS DEL AVISO: qué se llevará la raíz y qué quemará o robará el astro ──
        // Sin marca no hay nada que mitigar: el castigo llega siempre, así que se enseña a quién va.
        val markCellPx = with(density) { totalCellSize.toPx() }
        val plantMark = gameState.plantMarkEdge
        if (gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.GARDEN &&
            plantMark != null && gameState.plantAbduction == null
        ) {
            Box(Modifier.width(boardW).height(boardH)) {
                GardenMarkOverlay(plantMark, gameState.plantMarkTeikaId, teikaBodies, markCellPx, animationsOn)
            }
        }
        if (gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.STAR && !gameState.starAstroEmerged &&
            (gameState.starAstroTargetTeikaId != null || gameState.starAstroMoonWoodCells.isNotEmpty())
        ) {
            Box(Modifier.width(boardW).height(boardH)) {
                StarMarkOverlay(
                    gameState.starAstroTargetTeikaId, gameState.starAstroMoonWoodCells,
                    teikaBodies, markCellPx, animationsOn,
                )
            }
        }
        if (gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.STAR) {
            // teikaBodies cambia cada tick de física: se pasa el `State` y lo lee `AstroEventOverlay` al
            // DIBUJAR (auditoría de fluidez 2026-09-29). Antes se leía aquí con `by` y todo este bloque del
            // tablero se recomponía a cada tick en TODOS los niveles del Estelar, con el astro fuera o no.
            val teikaBodiesNow = teikaBodies.collectAsState()
            val cellPxAstro = with(density) { totalCellSize.toPx() }
            // (Pips de amenaza idle/emerge/cooldown RETIRADOS a pedido — los dos círculos que se
            // llenaban turno a turno arriba del tablero.)
            if (gameState.starAstroEmerged && gameState.starAstroBody != 0) {
                AstroEventOverlay(
                    body = gameState.starAstroBody,
                    targetCells = gameState.starAstroTargetCells,
                    cellPx = cellPxAstro,
                    targetTeikaId = gameState.starAstroTargetTeikaId,
                    teikaBodies = teikaBodiesNow,
                    moonWoodCells = gameState.starAstroMoonWoodCells
                )
            }
        }
        val astroRetreatEvents = eventBuckets.of<GameEvent.AstroRetreat>()
        astroRetreatEvents.forEach { event ->
            key(event.id) {
                AstroRetreatVFX(
                    event = event,
                    cellPx = with(density) { totalCellSize.toPx() },
                    onComplete = { onEventConsumed?.invoke(event.id) }
                )
            }
        }
        // P-323: la cicatriz de oro del kintsugi cuaja en la costura (fuera de la capa del corte, que se
        // desmonta en cuanto la reparación termina).
        eventBuckets.of<GameEvent.KintsugiScar>().forEach { event ->
            key(event.id) {
                KintsugiScarVFX(event, with(density) { totalCellSize.toPx() }) { onEventConsumed?.invoke(event.id) }
            }
        }
        // P-324: la enredadera del Jardín brota (camino de la raíz o invasión desde un borde).
        eventBuckets.of<GameEvent.VineSprout>().forEach { event ->
            key(event.id) {
                VineSproutVFX(event, with(density) { totalCellSize.toPx() }) { onEventConsumed?.invoke(event.id) }
            }
        }
        // P-321: los bloques que robó la Luna vuelven a caer, sin estrella.
        eventBuckets.of<GameEvent.MoonReturn>().forEach { event ->
            key(event.id) {
                MoonReturnVFX(event, with(density) { totalCellSize.toPx() }) { onEventConsumed?.invoke(event.id) }
            }
        }
        val astroStrikeEvents = eventBuckets.of<GameEvent.AstroStrike>()
        astroStrikeEvents.forEach { event ->
            key(event.id) {
                AstroStrikeVFX(
                    event = event,
                    cellPx = with(density) { totalCellSize.toPx() },
                    onComplete = { onEventConsumed?.invoke(event.id) }
                )
            }
        }
        // (La niebla y el reflector de la Mansión se retiraron el 2026-09-23 con el Espejo Embrujado,
        //  que se dibuja en el Box interno junto al magma: ver MirrorOverlay / PhantomCellsLayer.)
        // ── Dojo en Sombras / CORTE NINJA: aviso creciente (1ª/2ª colocación ociosa), tajo del
        //    ninja (NinjaBoardSlash) y reparación kintsugi (NinjaBoardRepair). La SEPARACIÓN física
        //    de las mitades la dibuja el modifier ninjaBoardSplit del Box externo (ver arriba).
        if (gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.NINJA &&
            (gameState.ninjaIdleTurns > 0 || gameState.ninjaCutActive ||
                gameState.events.any {
                    it is GameEvent.NinjaBoardSlash || it is GameEvent.NinjaBoardRepair
                })
        ) {
            NinjaCutOverlay(
                gameState = gameState,
                cellSize = totalCellSize,
                onEventConsumed = onEventConsumed
            )
        }
        // (ERRADICADO 2026-08-08) Aquí se montaba `TideCrushOverlay`, la PRENSA del techo. Con el
        // ático nada se aplasta contra el borde: lo que sale de la vista se guarda y puede volver.

        if(rulePulses.any { it.kind.startsWith("compactor") || it.kind.startsWith("orbit") || it.kind.startsWith("portal_swap") }) Box(Modifier.width(boardW).height(boardH)) {
            ReplacementRuleLayer(gameState,totalCellSize,rulePulses.filter { it.kind.startsWith("compactor") || it.kind.startsWith("orbit") || it.kind.startsWith("portal_swap") },animationsOn && !reducedFx,{ onEventConsumed?.invoke(it) })
        }
        ClawLayer(clawAim,clawPlan,gameState.clawTrip,with(density) { totalCellSize.toPx() },onClawSelect,onClawTarget,Modifier.width(boardW).height(boardH))
        BallAimLayer(ballAim,ballPlan,with(density) { totalCellSize.toPx() },onBallAnchor,onBallAim,Modifier.width(boardW).height(boardH))
        ForgeAimLayer(forgeAim,forgePlan,with(density) { totalCellSize.toPx() },onForgeSelect,Modifier.width(boardW).height(boardH))
        ReactorAimLayer(reactorAim,reactorPlan,with(density) { totalCellSize.toPx() },onReactorSelect,Modifier.width(boardW).height(boardH))
        TurbineLayer(gameState,turbineAim,teikaBodies,with(density) { totalCellSize.toPx() },reducedFx || !animationsOn,onTurbineAnchor,onTurbineAim,Modifier.width(boardW).height(boardH))
        OrbitAimLayer(gameState,orbitAim,orbitPlan,totalCellSize,with(density) { totalCellSize.toPx() },onOrbitSelect,Modifier.width(boardW).height(boardH))
        PortalSwapAimLayer(gameState,portalSwapAim,portalSwapPlan,totalCellSize,with(density) { totalCellSize.toPx() },onPortalSwapSelect,Modifier.width(boardW).height(boardH))
        CompactorAimLayer(compactorAiming,with(density) { totalCellSize.toPx() },onCompactorPreview,onCompactorCancel,onCompactorRelease,Modifier.width(boardW).height(boardH))
        PinballLayer(gameState,teikaBodies,with(density) { totalCellSize.toPx() },animationsOn && !reducedFx,
            onPinballBegin,onPinballCancel,onPinballRelease,{ onEventConsumed?.invoke(it) },Modifier.matchParentSize(),pinballImpacts)

        // ── Combo text banner (tiers 1-4) — centrado en el tablero, SOBRE los cuerpos de Teika ──
        // Vivía en el Box interno, compuesto ANTES de TeikaPhysicsBodiesLayer: cualquier Teika
        // cerca del centro tapaba el "¡BIEN!/¡GENIAL!" (bug de playtest — los tiers épicos
        // ≥ EPIC_COMBO_TIER se veían bien porque los dibuja GameScreen a pantalla completa).
        // El rótulo es texto que el jugador debe LEER: va encima de los cuerpos, como el resto de
        // rótulos del Box externo. El Box fija el tamaño del tablero para que el centrado
        // (fillMaxSize + Center dentro del VFX) sea idéntico al que tenía en el Box interno
        // (mismo origen (0,0)); de paso, el glow y los anillos ya no se recortan contra el clip.
        val comboTexts = remember(eventBuckets) {
            eventBuckets.of<GameEvent.ComboText>().filter { it.tier < EPIC_COMBO_TIER }
        }
        val visibleCombo = comboTexts.takeLast(1)
        visibleCombo.forEach { event ->
            key(event.id) {
                Box(modifier = Modifier.width(boardW).height(boardH)) {
                    ComboTextVFX(
                        event = event,
                        cellSize = totalCellSize,
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }
        }
        (comboTexts - visibleCombo.toSet()).forEach { onEventConsumed?.invoke(it.id) }

        // ── P-353 · ¡PARADA! — el castigo no cayó. ÚNICO consumidor del evento. ──
        val parryEvents = eventBuckets.of<GameEvent.BiomeParry>()
        parryEvents.lastOrNull()?.let { event ->
            key(event.id) {
                Box(modifier = Modifier.width(boardW).height(boardH)) {
                    BiomeParryVFX(event) { onEventConsumed?.invoke(event.id) }
                }
            }
        }
        // Solo se dibuja la última; las anteriores (dos biomas no pueden parar a la vez, pero por si acaso) salen.
        parryEvents.dropLast(1).forEach { onEventConsumed?.invoke(it.id) }

        // ── ¡PERFECTO! — tablero limpio tras el clear. ÚNICO consumidor del evento. ──
        //
        // MONO-FOCO DEL CENTRO DEL TABLERO (P-04, 2026-07-31). «¡PERFECTO!» y la palabra de combo se
        // dibujan en el MISMO punto —dead-center del tablero— y casi siempre nacen juntos: dejar el
        // tablero limpio implica un clear, y ese clear trae su «¡GENIAL! ×3». Se pintaban
        // superpuestos y no se leía ninguno. La ley es la misma que ya rige la cola del Infinito
        // ([topInfiniteBanner]): **el combo es la respuesta inmediata a la jugada y NUNCA espera**;
        // el rótulo grande cede y entra cuando el combo se ha ido. Cuenta cualquier `ComboText`,
        // también los épicos ≥ EPIC_COMBO_TIER (los pinta GameScreen a pantalla completa, pero
        // tapan igual el centro). El evento NO se consume mientras espera: sigue en la cola.
        val comboOnScreen = eventBuckets.of<GameEvent.ComboText>().isNotEmpty()
        val perfectEvents = eventBuckets.of<GameEvent.PerfectClear>()
        // Solo el último se dibuja; los anteriores salen de la cola sin pintarse. Ojo: el que espera
        // su turno NO entra aquí — descartarlo por estar en cola sería perder la celebración.
        val perfectTop = perfectEvents.lastOrNull()
        perfectEvents.forEach { if (it !== perfectTop) onEventConsumed?.invoke(it.id) }
        if (!comboOnScreen) perfectTop?.let { event ->
            key(event.id) {
                Box(modifier = Modifier.width(boardW).height(boardH)) {
                    PerfectClearVFX(
                        event = event,
                        cellSize = totalCellSize,
                        onComplete = { onEventConsumed?.invoke(event.id) }
                    )
                }
            }
        }

        // (El "borde fever" de puntos marchando por el perímetro se probó y el usuario lo
        //  RETIRÓ el mismo día (2026-07-20) — no readoptar. El combo ya se comunica con la
        //  palabra + tono ascendente + marco arcoíris de pantalla.)

        // ── Aviso de gracia de combo — misma familia: una ALARMA que ningún Teika debe tapar. ──
        // Además, al subir (offsetY hasta -80px) desde una ficha colocada en las filas altas, el
        // clipToBounds del Box interno le recortaba el texto; aquí sube libre.
        val comboGraceEvents = eventBuckets.of<GameEvent.ComboGrace>()
        comboGraceEvents.forEach { event ->
            key(event.id) {
                Box(modifier = Modifier.offset(
                    x = totalCellSize * event.position.x,
                    y = totalCellSize * event.position.y
                )) {
                    ComboGraceVFX(event = event) { onEventConsumed?.invoke(event.id) }
                }
            }
        }

        // ── Textos flotantes de puntuación (solo el más reciente) ─────────────
        // Se renderizan en el Box EXTERNO (sin clipToBounds) — antes vivían dentro del Box interno
        // recortado y, al flotar hacia arriba (offsetY hasta -150px), el borde superior del tablero
        // recortaba el cuerpo del número. El origen (0,0) coincide con el del Box interno, así que las
        // mismas coordenadas posX/posY siguen siendo válidas.
        //
        // REPARTO DE PROPIEDAD (contrato de eventos: UN solo sitio consume un evento dado).
        // El tablero es dueño de los `FloatingScore` **de combo**; el "+score" normal (no-combo) es de
        // GameScreen, que lo hace VOLAR hasta el marcador. Antes esto filtraba TODOS los FloatingScore
        // y consumía de golpe los que no fueran el último — incluidos los no-combo, que GameScreen
        // estaba animando en ese mismo instante. Bastaba una fusión sin racha seguida de otra con
        // racha (110 ms después, la cadencia de la it. 8) para que convivieran los dos tipos en la
        // cola: el tablero mataba el vuelo del "+score" a un frame. Es decir, la recolección al
        // marcador desaparecía **justo durante los combos**, que es cuando más importa.
        val comboScores = remember(eventBuckets) {
            eventBuckets.of<GameEvent.FloatingScore>().filter { it.isCombo }
        }
        val visibleScores = comboScores.takeLast(1)
        visibleScores.forEach { event ->
            key(event.id) {
                // Zona INFERIOR propia: el ×N + palabra de elogio ocupan el centro,
                // así que la puntuación del combo vive más abajo y no se solapan.
                val posX = totalCellSize * (boardCols / 2)
                val posY = totalCellSize * ((boardRows / 2) + 3).coerceAtMost(boardRows - 1)
                Box(modifier = Modifier.offset(x = posX, y = posY)) {
                    FloatingScoreAnim(event = event) { onEventConsumed?.invoke(event.id) }
                }
            }
        }
        // Solo se dibuja el combo más reciente: los anteriores se retiran de la cola (sin dibujarse).
        (comboScores - visibleScores.toSet()).forEach { onEventConsumed?.invoke(it.id) }

        // ── Bioma Glotón (Cocina Voraz): se dibuja al final del Box EXTERNO, ENCIMA de las celdas, para
        //    que el BlendMode.Clear de los agujeros perfore el tablero y deje ver el fondo del juego. ──
        val gluttonCellPx = with(density) { totalCellSize.toPx() }
        if (gameState.gluttonDeadCells.isNotEmpty() || gameState.gluttonCorner in 0..3 ||
            gameState.gluttonCounter > 0
        ) {
            GluttonBiomeOverlay(
                deadCells = gameState.gluttonDeadCells,
                openCorner = gameState.gluttonCorner,
                idleCounter = gameState.gluttonCounter,
                toxicAlsoActive = gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.TOXIC,
                cellSize = totalCellSize,
                modifier = Modifier.size(totalCellSize * GameState.GRID_SIZE)
            )
        }
        // ── P-294 · Trono Real: LA ESPADA DEL REY — alzada (aviso 1), la hoja que brilla sobre la línea
        //    (aviso 2), el golpe (impacto) y la envainada. Espacio Profundo: EL METEORITO — asoma
        //    (aviso 1), el cráter marcado (aviso 2), el impacto y el desvío. En el Box externo: la
        //    espada asoma por el borde y el meteorito entra desde fuera del tablero. ──
        val swordStrikeEvents = eventBuckets.of<GameEvent.KingSwordStrike>()
        val swordRaiseEvents = eventBuckets.of<GameEvent.KingSwordRaise>()
        if (gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.KING) {
            KingSwordLayer(
                axis = gameState.kingSwordAxis,
                line = gameState.kingSwordLine,
                idleTurns = gameState.kingSwordIdle,
                threshold = com.cukiti.teikafusion.model.LevelDirector.swordThreshold(gameState.level),
                cellPx = gluttonCellPx,
                animationsOn = animationsOn,
                // Mientras sube o cae, la espada la dibuja su VFX.
                hideSword = swordRaiseEvents.isNotEmpty() || swordStrikeEvents.isNotEmpty(),
                modifier = Modifier.size(totalCellSize * GameState.GRID_SIZE)
            )
        }
        swordRaiseEvents.forEach { event ->
            key(event.id) { KingSwordRaiseVFX(event, gluttonCellPx) { onEventConsumed?.invoke(event.id) } }
        }
        swordStrikeEvents.forEach { event ->
            key(event.id) { KingSwordStrikeVFX(event, gluttonCellPx) { onEventConsumed?.invoke(event.id) } }
        }
        eventBuckets.of<GameEvent.KingSwordSheathe>().forEach { event ->
            key(event.id) { KingSwordSheatheVFX(event, gluttonCellPx) { onEventConsumed?.invoke(event.id) } }
        }
        // ── P-329 · Trono Real: LAS PIEZAS DEL REY — la calle bloqueada (consecuencia, estado), la pieza
        //    en su puesto (avisos), su entrada, el golpe a cámara lenta y la retirada. ──
        val pieceStrikeEvents = eventBuckets.of<GameEvent.KingPieceStrike>()
        val pieceRaiseEvents = eventBuckets.of<GameEvent.KingPieceRaise>()
        if (gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.KING) {
            // La calle se pinta cuando el golpe ya ha caído: antes, el camino lo dibuja el VFX.
            if (pieceStrikeEvents.isEmpty()) {
                KingLaneLayer(
                    cells = gameState.kingLaneCells,
                    black = if (gameState.kingRestCell != null) gameState.kingRestBlack else gameState.kingPieceBlack,
                    cellPx = gluttonCellPx,
                    modifier = Modifier.size(totalCellSize * GameState.GRID_SIZE)
                )
                // La pieza que se quedó y lo que ataca (mientras cae, la dibuja su VFX).
                KingRestLayer(
                    kind = gameState.kingRestKind,
                    at = gameState.kingRestCell,
                    black = gameState.kingRestBlack,
                    lane = gameState.kingLaneCells,
                    cellPx = gluttonCellPx,
                    animationsOn = animationsOn,
                    modifier = Modifier.size(totalCellSize * GameState.GRID_SIZE)
                )
            }
            KingPieceLayer(
                kind = gameState.kingPieceKind,
                a = gameState.kingPieceA,
                b = gameState.kingPieceB,
                black = gameState.kingPieceBlack,
                fromEnd = gameState.kingPieceFromEnd,
                idleTurns = gameState.kingSwordIdle,
                threshold = com.cukiti.teikafusion.model.LevelDirector.swordThreshold(gameState.level),
                cellPx = gluttonCellPx,
                animationsOn = animationsOn,
                // Mientras entra, la pieza la dibuja su VFX.
                hidePiece = pieceRaiseEvents.isNotEmpty(),
                modifier = Modifier.size(totalCellSize * GameState.GRID_SIZE)
            )
        }
        pieceRaiseEvents.forEach { event ->
            key(event.id) { KingPieceRaiseVFX(event, gluttonCellPx) { onEventConsumed?.invoke(event.id) } }
        }
        pieceStrikeEvents.forEach { event ->
            key(event.id) { KingPieceStrikeVFX(event, gluttonCellPx) { onEventConsumed?.invoke(event.id) } }
        }
        eventBuckets.of<GameEvent.KingPieceRetreat>().forEach { event ->
            key(event.id) { KingPieceRetreatVFX(event, gluttonCellPx) { onEventConsumed?.invoke(event.id) } }
        }
        val meteorImpactEvents = eventBuckets.of<GameEvent.MeteorImpact>()
        val meteorSpottedEvents = eventBuckets.of<GameEvent.MeteorSpotted>()
        if (gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.GALACTIC) {
            // P-319 · LLUVIA DOBLE: una mira por cráter.
            listOfNotNull(gameState.meteorAt, gameState.meteorAt2).forEach { crater ->
                key(crater) {
                    MeteorLayer(
                        at = crater,
                        idleTurns = gameState.galacticIdleSaved,
                        threshold = com.cukiti.teikafusion.model.LevelDirector.meteorThreshold(gameState.level),
                        cellPx = gluttonCellPx,
                        animationsOn = animationsOn,
                        // La mira la dibuja su VFX mientras aparece y mientras el meteorito cae.
                        hideReticle = meteorSpottedEvents.isNotEmpty() || meteorImpactEvents.isNotEmpty(),
                        modifier = Modifier.size(totalCellSize * GameState.GRID_SIZE)
                    )
                }
            }
        }
        meteorSpottedEvents.forEach { event ->
            key(event.id) { MeteorSpottedVFX(event, gluttonCellPx) { onEventConsumed?.invoke(event.id) } }
        }
        meteorImpactEvents.forEach { event ->
            key(event.id) { MeteorImpactVFX(event, gluttonCellPx) { onEventConsumed?.invoke(event.id) } }
        }
        eventBuckets.of<GameEvent.MeteorDeflect>().forEach { event ->
            key(event.id) { MeteorDeflectVFX(event, gluttonCellPx) { onEventConsumed?.invoke(event.id) } }
        }
        eventBuckets.of<GameEvent.RockCrack>().forEach { event ->
            key(event.id) { RockCrackVFX(event, gluttonCellPx) { onEventConsumed?.invoke(event.id) } }
        }
        val gluttonBiteEvents = eventBuckets.of<GameEvent.GluttonBite>()
        gluttonBiteEvents.forEach { event ->
            key(event.id) {
                GluttonBiteVFX(event = event, cellPx = gluttonCellPx, onComplete = { onEventConsumed?.invoke(event.id) })
            }
        }
        val gluttonRetreatEvents = eventBuckets.of<GameEvent.GluttonRetreat>()
        gluttonRetreatEvents.forEach { event ->
            key(event.id) {
                GluttonRetreatVFX(event = event, cellPx = gluttonCellPx, onComplete = { onEventConsumed?.invoke(event.id) })
            }
        }
    }
}

/**
 * **LAS CASILLAS VACÍAS, EN UN SOLO LIENZO** (perf 2026-09-26).
 *
 * Pinta exactamente lo que pintaba un `CellView(CellContent.Empty)` por casilla —el fondo
 * [EmptyCellBase] con esquinas de 4 dp, en la celda colocada con `offset(Dp)`/`size(Dp)`— y con el
 * mismo redondeo a píxel entero que hacía el layout, para que ni un píxel cambie. Una casilla vacía
 * nunca animaba (su `CellView` nacía a escala 1), así que no se pierde nada.
 *
 * Se salta la recomposición mientras el tablero y las ocultas sean las MISMAS instancias —o sea,
 * cuando lo que cambia es la cola de eventos, que durante una rotura cambia cada 32 ms.
 */
@Composable
private fun EmptyCellsLayer(
    grid: Map<Position, CellContent>,
    hidden: Set<Position>,
    cols: Int,
    rows: Int,
    cellSize: Dp,
) {
    // Capa propia (auditoría de fluidez 2026-09-29): sin ella, cada fotograma en que animaba un
    // hermano del Box del tablero (magma, fantasmas, cualquier VFX) regrababa estas 64 casillas
    // —64 `Position` nuevas y 64 búsquedas en el mapa— aunque la rejilla no hubiera cambiado.
    Canvas(modifier = Modifier.width(cellSize * cols).height(cellSize * rows).graphicsLayer()) {
        val side = cellSize.roundToPx().toFloat()
        val corner = CornerRadius(4.dp.toPx())
        for (y in 0 until rows) {
            for (x in 0 until cols) {
                val p = Position(x, y)
                val c = grid[p] ?: CellContent.Empty
                // Singularidad: las Wood que viajan hacia el centro se ocultan (el VFX las dibuja en
                // tránsito) y se muestran vacías hasta asentarse.
                val shownEmpty = c is CellContent.Empty || (c is CellContent.Wood && p in hidden)
                if (!shownEmpty) continue
                drawRoundRect(
                    color = EmptyCellBase,
                    topLeft = Offset(
                        (cellSize * x).roundToPx().toFloat(),
                        (cellSize * y).roundToPx().toFloat()
                    ),
                    size = Size(side, side),
                    cornerRadius = corner,
                )
            }
        }
    }
}

/**
 * **LOS BLOQUES DEL TABLERO**, en su propio ámbito de recomposición (perf 2026-09-26).
 *
 * Era una función local de [GameBoard], y una función local no se puede saltar: cualquier cambio de
 * estado —un evento que entra, uno que se consume— volvía a recorrer las 64 casillas. Ahora solo se
 * recorre cuando cambia algo de lo que recibe (el tablero, las ocultas, las que arden o tiemblan).
 *
 * La regla del muro: solo empuja lo que TOCA. El hormigón NO se dibuja aquí: la pared entera la pinta
 * TideFrontLayer como masa continua (si se pintara además celda a celda, se vería la rejilla dentro).
 */
@Composable
private fun BoardBlockCells(
    grid: Map<Position, CellContent>,
    hidden: Set<Position>,
    cols: Int,
    rows: Int,
    cellSize: Dp,
    burningCells: Set<Position>,
    threatShakeCells: Set<Position>,
    animationsOn: Boolean,
    magmaHeatState: androidx.compose.runtime.State<Float>,
    animatedIds: MutableSet<String>,
) {
    for (y in 0 until rows) {
        for (x in 0 until cols) {
            val rawContent = grid[Position(x, y)] ?: CellContent.Empty
            val isHidden = rawContent is CellContent.Wood && Position(x, y) in hidden
            if (rawContent is CellContent.Wood && !isHidden) {
                val cellId = rawContent.id
                key(cellId) {
                    // Modifier MEMORIZADO: sin esto se construye uno nuevo en cada
                    // composición, `CellView` deja de ser saltable y las 64 celdas se
                    // recomponen sin motivo.
                    // Volcán: los bloques de la fila que ARDE tiemblan, más cuanto más calor
                    // llevan. Calor y reloj se leen en el `graphicsLayer` (fase de dibujo):
                    // el temblor no recompone nada.
                    val burning = animationsOn && Position(x, y) in burningCells
                    // P-294: lo que la espada o el meteorito van a golpear tiembla en el aviso 2.
                    val threatened = animationsOn && Position(x, y) in threatShakeCells
                    val cellModifier = remember(x, y, cellSize, burning, threatened) {
                        val base = Modifier
                            .offset(x = cellSize * x, y = cellSize * y)
                            .size(cellSize)
                        if (threatened) base.graphicsLayer {
                            val t = biomeClockSec.floatValue
                            translationX = kotlin.math.sin(t * 34f + x * 1.3f + y * 0.9f) * size.width * 0.03f
                            translationY = kotlin.math.sin(t * 27f + x * 0.7f + y * 1.9f) * size.width * 0.02f
                        } else if (!burning) base else base.graphicsLayer {
                            val h = magmaHeatState.value
                            val t = biomeClockSec.floatValue
                            val amp = size.width * (0.012f + 0.05f * h * h)
                            translationX = kotlin.math.sin(t * 38f + x * 1.7f) * amp
                            translationY = kotlin.math.sin(t * 29f + x * 2.3f) * amp * 0.5f
                        }
                    }
                    CellView(
                        content = rawContent,
                        shouldAnimate = cellId !in animatedIds,
                        onAnimated = { animatedIds.add(cellId) },
                        modifier = cellModifier,
                        glintPhase = (x + y).toFloat()
                    )
                }
            }
        }
    }
}

/**
 * Vignette roja de "tablero peligroso". Su latido (infiniteTransition) solo existe mientras
 * este composable está montado — es decir, solo cuando hay peligro — para no mantener una
 * animación infinita activa durante el juego normal.
 *
 * GEOMETRÍA (fix 2026-07-26): se dibuja como **rectángulo REDONDEADO con el radio real del panel**
 * ([boardCorner]) y con el trazo metido hacia dentro (`inset = grosor/2`), no como un `drawRect` de
 * esquinas rectas. Antes el marco rojo era cuadrado dentro de un tablero redondeado y encima el
 * `clip(boardShape)` le comía las esquinas: se veía un borde partido y descentrado justo en el
 * momento de más tensión de la partida. Ahora sigue exactamente el mismo contorno que el borde del
 * tablero, simétrico por los cuatro lados.
 */
@Composable
private fun DangerVignette(intensity: () -> Float, fillFraction: Float, boardCorner: Dp) {
    val pulseTransition = rememberInfiniteTransition(label = "dangerPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.55f,
        targetValue  = 1.0f,
        animationSpec = infiniteRepeatable(
            animation  = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dangerPulse"
    )
    Canvas(modifier = Modifier.fillMaxSize()) {
        val effective = intensity() * if (fillFraction > 0.80f) pulse else 0.85f
        if (effective <= 0.004f) return@Canvas
        val cornerPx = boardCorner.toPx()
        // Halo interior primero (ancho y tenue), marco nítido después: el degradado no se come el
        // filo, y el filo no queda flotando sobre nada.
        if (fillFraction > 0.65f) {
            val innerW = effective * 26f
            val inset = innerW / 2f
            drawRoundRect(
                color = Color(0xFFDD0000).copy(alpha = effective * 0.20f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - innerW, size.height - innerW),
                cornerRadius = CornerRadius(
                    (cornerPx - inset).coerceAtLeast(0f), (cornerPx - inset).coerceAtLeast(0f)
                ),
                style = Stroke(width = innerW)
            )
        }
        val strokeW = 2.5.dp.toPx() + effective * 3.5.dp.toPx()
        val inset = strokeW / 2f
        drawRoundRect(
            color = Color(0xFFFF3B20).copy(alpha = effective * 0.92f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeW, size.height - strokeW),
            cornerRadius = CornerRadius(
                (cornerPx - inset).coerceAtLeast(0f), (cornerPx - inset).coerceAtLeast(0f)
            ),
            style = Stroke(width = strokeW)
        )
    }
}

/** Fase del aviso de lava para [gs], con el umbral de SU nivel (ver [com.cukiti.teikafusion.model.volcanoWarningPhase]). */
private fun lavaWarnPhase(gs: com.cukiti.teikafusion.model.GameState): Int {
    val threshold = if (gs.lavaRow > 0)
        com.cukiti.teikafusion.model.LevelDirector.volcanoSurfacedThreshold(gs.level)
    else com.cukiti.teikafusion.model.LevelDirector.volcanoFirstThreshold(gs.level)
    return com.cukiti.teikafusion.model.volcanoWarningPhase(gs.lavaRow, gs.volcanoDryTurns, threshold)
}

@Composable
private fun VolcanoThreatVignette(
    lavaRow: Int,
    dryTurns: Int,
    redDanger: () -> Float
) {
    val transition = rememberInfiniteTransition(label = "volcanoThreat")
    val pulse by transition.animateFloat(
        initialValue = 0.58f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(720, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "volcanoThreatPulse"
    )
    val drift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1900, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "volcanoThreatDrift"
    )

    // PERF: la geometría (pressure/heatDepth/borderWidth) y el brush de calor (colores fijos) se
    // cachean por lavaRow/dryTurns/tamaño; un único Path se reutiliza para llamas y ondas. La
    // intensidad (pulse) y el movimiento (drift) se leen en onDrawBehind. Overlay TRANSITORIO (solo
    // durante el peligro de lava) → antes asignaba 1 brush de calor + 11 Path por frame.
    // Capa propia (auditoría de fluidez 2026-09-29): anima cada fotograma mientras dura el aviso.
    Spacer(Modifier.fillMaxSize().graphicsLayer().drawWithCache {
        val turnPressure = if (dryTurns > 0) 0.30f else 0f
        val pressure = (0.34f + turnPressure + lavaRow.toFloat() / GameState.GRID_SIZE * 0.40f)
            .coerceIn(0.34f, 1f)
        val borderWidth = size.minDimension * (0.014f + pressure * 0.018f)
        val heatDepth = size.height * (0.10f + pressure * 0.055f)
        // Brush del velo de calor superior: colores fijos (la intensidad se aplica con el alpha del
        // dibujo) y geometría dependiente de pressure (no de la animación) → cacheado.
        val heatBrush = androidx.compose.ui.graphics.Brush.verticalGradient(
            colors = listOf(
                Color(0xFFFF6A16).copy(alpha = 0.42f),
                Color(0xFFFFB12B).copy(alpha = 0.16f),
                Color.Transparent
            ),
            startY = 0f, endY = heatDepth
        )
        val path = androidx.compose.ui.graphics.Path()
        onDrawBehind {
            val redPriority = if (redDanger() > 0.6f) 0.34f else 1f
            val intensity = pressure * pulse * redPriority

            drawRect(brush = heatBrush, size = Size(size.width, heatDepth), alpha = intensity)

            drawLine(
                color = Color(0xFFFFC04D).copy(alpha = intensity * 0.88f),
                start = Offset(borderWidth, borderWidth * 0.75f),
                end = Offset(size.width - borderWidth, borderWidth * 0.75f),
                strokeWidth = borderWidth * 0.42f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            repeat(7) { index ->
                val laneX = size.width * (index + 0.55f) / 7f
                val sway = kotlin.math.sin(
                    drift * kotlin.math.PI.toFloat() * 2f + index * 0.83f
                ) * size.width * 0.018f
                val flameHeight = heatDepth * (0.42f + (index % 3) * 0.12f) * intensity
                path.reset()
                path.moveTo(laneX - borderWidth * 0.55f, 0f)
                path.cubicTo(
                    laneX - borderWidth * 0.25f + sway, flameHeight * 0.35f,
                    laneX + borderWidth * 0.45f - sway, flameHeight * 0.66f,
                    laneX + sway, flameHeight
                )
                path.cubicTo(
                    laneX - borderWidth * 0.20f, flameHeight * 0.70f,
                    laneX + borderWidth * 0.60f, flameHeight * 0.32f,
                    laneX + borderWidth * 0.55f, 0f
                )
                path.close()
                drawPath(
                    path = path,
                    brush = androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(
                            Color(0xFFFF3D00).copy(alpha = intensity * 0.54f),
                            Color(0xFFFFC400).copy(alpha = intensity * 0.74f),
                            Color.Transparent
                        ),
                        startY = 0f, endY = flameHeight
                    )
                )
            }

            repeat(10) { index ->
                val cycle = (drift + index * 0.137f) % 1f
                val x = size.width * ((index * 0.173f + 0.08f) % 1f) +
                    kotlin.math.sin(cycle * kotlin.math.PI.toFloat() * 2f + index) *
                        size.width * 0.012f
                val y = heatDepth * 0.12f + cycle * heatDepth * 1.35f
                val envelope = kotlin.math.sin(cycle * kotlin.math.PI.toFloat())
                    .coerceAtLeast(0f)
                drawCircle(
                    color = if (index % 3 == 0) {
                        Color(0xFFFFF2A1).copy(alpha = envelope * intensity)
                    } else {
                        Color(0xFFFF7A18).copy(alpha = envelope * intensity * 0.9f)
                    },
                    radius = borderWidth * (0.10f + index % 3 * 0.035f),
                    center = Offset(x.coerceIn(0f, size.width), y)
                )
            }

            repeat(4) { index ->
                val y = heatDepth * (0.42f + index * 0.19f)
                path.reset()
                path.moveTo(size.width * 0.12f, y)
                path.cubicTo(
                    size.width * 0.31f, y - borderWidth * (0.45f + pulse * 0.25f),
                    size.width * 0.40f, y + borderWidth * 0.50f,
                    size.width * 0.55f, y
                )
                path.cubicTo(
                    size.width * 0.68f, y - borderWidth * 0.42f,
                    size.width * 0.76f, y + borderWidth * 0.36f,
                    size.width * 0.88f, y
                )
                drawPath(
                    path,
                    Color(0xFFFFD27A).copy(alpha = intensity * (0.20f - index * 0.025f)),
                    style = Stroke(borderWidth * 0.12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }
        }
    })
}

/**
 * Glaciar: telegrafía de la OLEADA DE FRÍO. La escarcha repta desde los bordes con cada turno
 * ocioso ([idleTurns] = glacierIdleTurns del estado) y pulsa más urgente el turno ANTES de que la
 * oleada congele un Teika (GLACIER_FREEZE_AFTER). Romper una fila la disipa (el contador vuelve a
 * 0 y el overlay se desmonta). Solo se monta con presión real → su infiniteTransition no corre en
 * frío. PERF: un solo Path reutilizado para los carámbanos; sin Brush por frame (colores planos).
 */
@Composable
private fun GlacierThreatVignette(
    idleTurns: Int,
    freezeAfter: Int,
    redDanger: () -> Float
) {
    val imminent = idleTurns >= freezeAfter - 1
    val transition = rememberInfiniteTransition(label = "glacierThreat")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (imminent) 760 else 1250, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "glacierThreatPulse"
    )
    // PERF: un solo Path reutilizado para todos los carámbanos del borde (nunca uno por frame).
    val spikePath = remember { androidx.compose.ui.graphics.Path() }
    // Capa propia: anima cada fotograma mientras dura el aviso.
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val warning = (idleTurns.toFloat() / freezeAfter).coerceIn(0f, 0.85f)
        val redPriority = if (redDanger() > 0.6f) 0.35f else 1f
        val intensity = warning * pulse * redPriority
        val border = size.minDimension * (0.018f + intensity * 0.025f)
        // Doble marco helado (mismo esquema que la planta, en paleta de hielo).
        drawRect(
            Color(0xFF81D4FA).copy(alpha = intensity * 0.78f),
            topLeft = Offset(border / 2f, border / 2f),
            size = Size(size.width - border, size.height - border),
            style = Stroke(border)
        )
        drawRect(
            Color(0xFFE1F5FE).copy(alpha = intensity * 0.20f),
            topLeft = Offset(border * 1.5f, border * 1.5f),
            size = Size(size.width - border * 3f, size.height - border * 3f),
            style = Stroke(border * 1.8f)
        )
        // Carámbanos colgando del marco: crecen con la presión (más largos cuanto más cerca la
        // oleada). Posiciones deterministas — sin random, sin allocs.
        repeat(6) { index ->
            val t = (index + 0.5f) / 6f
            val len = border * (2.2f + (index % 3) * 0.9f) * (0.4f + warning)
            val topX = size.width * t
            spikePath.reset()
            spikePath.moveTo(topX - border * 0.45f, border)
            spikePath.lineTo(topX + border * 0.45f, border)
            spikePath.lineTo(topX, border + len)
            spikePath.close()
            drawPath(spikePath, Color(0xFFB3E5FC).copy(alpha = intensity * 0.85f))
            // Y su gemelo subiendo desde abajo, desfasado media celda.
            val botX = size.width * ((t + 0.083f) % 1f)
            spikePath.reset()
            spikePath.moveTo(botX - border * 0.45f, size.height - border)
            spikePath.lineTo(botX + border * 0.45f, size.height - border)
            spikePath.lineTo(botX, size.height - border - len * 0.8f)
            spikePath.close()
            drawPath(spikePath, Color(0xFFB3E5FC).copy(alpha = intensity * 0.65f))
        }
        // Destellos de cristal en las esquinas (la escarcha "agarra" primero ahí).
        repeat(4) { corner ->
            val cx = if (corner % 2 == 0) border * 2.2f else size.width - border * 2.2f
            val cy = if (corner < 2) border * 2.2f else size.height - border * 2.2f
            drawCircle(
                Color.White.copy(alpha = intensity * 0.55f),
                radius = border * (0.5f + pulse * 0.3f),
                center = Offset(cx, cy)
            )
        }
    }
}

/**
 * Bosque del Sueño: telegrafía de la MODORRA — **párpados pesados** (P-68, 2026-08-30).
 *
 * Era el último bioma sin ningún aviso: la Maite entraba a pelo y el jugador perdía la rotación sin
 * haber visto venir nada. `sleepIdleTurns` llevaba en `GameState` desde siempre **sin un solo lector
 * en la UI** — el mismo patrón que ya arreglaron la planta, el volcán y el glaciar.
 *
 * Dos párpados índigo que bajan desde arriba y suben desde abajo conforme se acumulan turnos
 * ociosos, y **parpadean** —se cierran del todo un instante— el turno anterior a que caiga. Se
 * dibuja solo con rectángulos y un degradado cacheado: sin Path, sin Brush por frame.
 */
@Composable
private fun DrowsyDroopOverlay(idleTurns: Int, level: Int) {
    val threshold = remember(level) {
        com.cukiti.teikafusion.model.LevelDirector.idleThreshold(
            com.cukiti.teikafusion.model.BiomeRhythm
                .idleBase(com.cukiti.teikafusion.model.BiomeType.SLEEP),
            level
        )
    }
    // El último turno antes de la Maite: los párpados llegan a cerrarse del todo un instante.
    val imminent = idleTurns >= threshold - 1
    // Cuánto han bajado ya (0..1). Con umbral 2 el primer turno ocioso ya se nota.
    val droop = (idleTurns.toFloat() / threshold.coerceAtLeast(1)).coerceIn(0f, 1f)

    val transition = rememberInfiniteTransition(label = "drowsyDroop")
    val blink by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (imminent) 1400 else 2400, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "drowsyBlink"
    )
    val lid = Color(0xFF3B3F8F)
    // Capa propia: el parpadeo anima cada fotograma y, sin capa, arrastraba a regrabar a sus hermanos.
    Canvas(modifier = Modifier.fillMaxSize().graphicsLayer()) {
        // El parpadeo SOLO aprieta cuando es inminente: antes es un vaivén suave de sueño.
        val close = droop * (0.55f + 0.45f * blink) * (if (imminent) 1f else 0.72f)
        val lidH = size.height * 0.30f * close
        if (lidH <= 0.5f) return@Canvas
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(lid.copy(alpha = 0.72f * close), lid.copy(alpha = 0f)),
                startY = 0f, endY = lidH
            ),
            topLeft = Offset(0f, 0f), size = Size(size.width, lidH)
        )
        drawRect(
            androidx.compose.ui.graphics.Brush.verticalGradient(
                listOf(lid.copy(alpha = 0f), lid.copy(alpha = 0.72f * close)),
                startY = size.height - lidH, endY = size.height
            ),
            topLeft = Offset(0f, size.height - lidH), size = Size(size.width, lidH)
        )
        // Borde blando del párpado: sin él se lee como una barra, no como un ojo cerrándose.
        drawLine(
            lid.copy(alpha = 0.45f * close),
            Offset(0f, lidH), Offset(size.width, lidH), strokeWidth = size.height * 0.006f
        )
        drawLine(
            lid.copy(alpha = 0.45f * close),
            Offset(0f, size.height - lidH), Offset(size.width, size.height - lidH),
            strokeWidth = size.height * 0.006f
        )
    }
}

@Composable
private fun PlantThreatVignette(
    idleTurns: Int,
    abduction: PlantAbduction?,
    redDanger: () -> Float
) {
    val transition = rememberInfiniteTransition(label = "plantThreat")
    val pulse by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(if (abduction == null) 1250 else 760, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "plantThreat"
    )
    // PERF: un solo Path reutilizado para las 4 espigas del borde (antes 4 Path/frame).
    val edgePath = remember { androidx.compose.ui.graphics.Path() }
    // Capa propia: el latido anima cada fotograma y, sin capa, arrastraba a regrabar a sus hermanos.
    Canvas(Modifier.fillMaxSize().graphicsLayer()) {
        val warning = if (abduction != null) 1f else (idleTurns / 3f).coerceIn(0f, 0.8f)
        val redPriority = if (redDanger() > 0.6f) 0.35f else 1f
        val intensity = warning * pulse * redPriority
        val border = size.minDimension * (0.018f + intensity * 0.025f)
        drawRect(
            Color(0xFF2F7D32).copy(alpha = intensity * 0.78f),
            topLeft = Offset(border / 2f, border / 2f),
            size = Size(size.width - border, size.height - border),
            style = Stroke(border)
        )
        drawRect(
            Color(0xFF8BC34A).copy(alpha = intensity * 0.18f),
            topLeft = Offset(border * 1.5f, border * 1.5f),
            size = Size(size.width - border * 3f, size.height - border * 3f),
            style = Stroke(border * 1.8f)
        )

        val edge = abduction?.edge
        repeat(4) { index ->
            val selected = edge?.ordinal == index
            val alpha = intensity * if (selected) 1f else 0.38f
            val path = edgePath
            path.reset()
            when (PlantEdge.entries[index]) {
                PlantEdge.TOP -> {
                    path.moveTo(size.width * 0.22f, 0f)
                    path.cubicTo(size.width * 0.30f, border * 3f, size.width * 0.38f, border, size.width * 0.46f, border * 4f)
                }
                PlantEdge.RIGHT -> {
                    path.moveTo(size.width, size.height * 0.22f)
                    path.cubicTo(size.width - border * 3f, size.height * 0.30f, size.width - border, size.height * 0.38f, size.width - border * 4f, size.height * 0.46f)
                }
                PlantEdge.BOTTOM -> {
                    path.moveTo(size.width * 0.78f, size.height)
                    path.cubicTo(size.width * 0.70f, size.height - border * 3f, size.width * 0.62f, size.height - border, size.width * 0.54f, size.height - border * 4f)
                }
                PlantEdge.LEFT -> {
                    path.moveTo(0f, size.height * 0.78f)
                    path.cubicTo(border * 3f, size.height * 0.70f, border, size.height * 0.62f, border * 4f, size.height * 0.54f)
                }
            }
            drawPath(
                path,
                Color(0xFF66BB4A).copy(alpha = alpha),
                style = Stroke(border * if (selected) 0.9f else 0.55f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }
}

/** Trono Real: diámetro UNIFORME (en celdas) de un Teika posado sobre un bloque del muro. */
private const val KING_CAPTURED_DIAM = 0.9f




/**
 * Quién hay en el tablero y de qué tier. Las anclas sólo se incluyen para cuerpos kinematic del
 * tutorial 4: cambian por ETAPA, no por frame, y permiten interpolar el aparcado sin suscribir la
 * composición a las posiciones de todos los cuerpos físicos.
 */
private data class TeikaSlot(
    val id: String,
    val type: TeikaType,
    val tutorialAnchorX: Float?,
    val tutorialAnchorY: Float?
)

/** Orden de pintado estable: el GRANDE detrás, el PEQUEÑO delante; desempate por id. */
private val TeikaSlotOrder =
    compareByDescending<TeikaSlot> { it.type.level }.thenBy { it.id }

/**
 * Motor físico Suika: capa que dibuja cada cuerpo de Teika (círculo en coordenadas continuas
 * de celda) con su Lottie. Cada Lottie conserva su identidad por `key(id)` → moverse no reinicia
 * la animación. El tamaño del composable es el diámetro (nivel × celda); `teikaVisualScale` da el
 * desborde "burbuja" para que las caras se toquen.
 *
 * **La composición NO depende de la posición** (rendimiento, 2026-07-31b). Antes esta capa hacía
 * `val bodies by bodiesFlow.collectAsState()` en el CUERPO: como el snapshot trae objetos nuevos
 * cada frame de física, la capa entera se recomponía a 60 fps, reordenaba la lista (una asignación
 * más por frame) y volvía a construir la cadena de modifiers de cada Lottie. El `offset {}` parecía
 * un lectura diferida, pero leía la `body` capturada en la recomposición: la posición se movía
 * *porque* se recomponía, no gracias al layout.
 *
 * Ahora la composición se ata al **roster** (ids + tiers), que solo cambia cuando nace, muere o
 * fusiona un cuerpo, y cada Lottie lee **su propia** posición dentro de `offset {}` — fase de
 * LAYOUT, sin recomponer. Es el mismo principio que fija la pasada de rendimiento del 2026-07-31.
 */
@Composable
private fun TeikaPhysicsBodiesLayer(
    bodiesFlow: StateFlow<List<TeikaBody>>,
    cellSize: Dp,
    hiddenIds: Set<String> = emptySet(),
    fusionCharges: List<GameEvent.FusionCharge> = emptyList(),
    pinballImpacts: List<GameEvent.PinballImpact> = emptyList(),
    tutorialLessonFour: Boolean = false,
    tutorialIdleActive: Boolean = false,
    reducedFx: Boolean = false
) {
    // Sin `by`: leer aquí el estado por-frame suscribiría la COMPOSICIÓN a 60 fps.
    val bodiesState = bodiesFlow.collectAsState()
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }

    // ── SIN SOMBRA DE CONTACTO ────────────────────────────────────────────────────────────────
    // La elipse oscura bajo cada cuerpo se RETIRÓ (2026-07-28): ensuciaba el tablero y se leía como
    // mancha, no como apoyo. El z-order por tier de abajo ya da la lectura de "apoyado", y quitarla
    // ahorra una pasada de Canvas por frame sobre todo el tablero.

    // ── Z-ORDER ESTABLE POR TIER ──────────────────────────────────────────────────────────────
    // Antes se dibujaba en el orden CRUDO de la lista física, que no garantiza nada: un T1 podía
    // pintarse sobre un T4 y al revés en frames distintos, y el cerebro leía "encaramado" en vez de
    // "apoyado" (bug3). Regla: el GRANDE detrás, el PEQUEÑO delante — que es como se ven dos bolas
    // apoyadas de verdad. Desempate por `id` para que el orden no baile entre frames cuando dos
    // cuerpos comparten tier.
    //
    // `derivedStateOf` compara por igualdad ESTRUCTURAL: mientras el roster salga igual —el caso
    // normal, frame tras frame— no invalida a nadie aunque las posiciones hayan cambiado.
    val roster by remember(bodiesState, tutorialLessonFour) {
        derivedStateOf {
            bodiesState.value
                .map { body ->
                    val tutorialKinematic = tutorialLessonFour && body.kinematic
                    TeikaSlot(
                        id = body.id,
                        type = body.type,
                        tutorialAnchorX = body.x.takeIf { tutorialKinematic },
                        tutorialAnchorY = body.y.takeIf { tutorialKinematic }
                    )
                }
                .sortedWith(TeikaSlotOrder)
        }
    }
    val chargeByBodyId = remember(fusionCharges) {
        buildMap<String, GameEvent.FusionCharge> {
            fusionCharges.forEach { event ->
                put(event.aId, event)
                put(event.bId, event)
            }
        }
    }
    // Un solo reloj ambiental para toda la pareja. Lee su valor en layout/draw, por lo que no
    // recompone la capa a 60 fps; además sólo corre durante la espera real del tutorial 4.
    val tutorialIdleClock = remember { Animatable(0f) }
    LaunchedEffect(tutorialIdleActive, reducedFx) {
        tutorialIdleClock.snapTo(0f)
        if (tutorialIdleActive && !reducedFx) {
            while (true) {
                tutorialIdleClock.animateTo(1f, tween(2_200, easing = LinearEasing))
                tutorialIdleClock.snapTo(0f)
            }
        }
    }
    val dockingSpec: AnimationSpec<Float> = remember(reducedFx) {
        if (reducedFx) snap() else tween(320, easing = FastOutSlowInEasing)
    }
    roster.forEach { slot ->
        if (slot.id in hiddenIds) return@forEach
        key(slot.id) {
            val hit=pinballImpacts.lastOrNull { it.bodyId==slot.id }
            val squash=remember { Animatable(0f) }
            LaunchedEffect(hit?.id,reducedFx) {
                if(hit!=null && !reducedFx) { squash.snapTo(1f); squash.animateTo(0f,tween(220)) }
            }
            val charge = chargeByBodyId[slot.id]
            val chargeProgress = remember(charge?.id) { Animatable(0f) }
            // T4+T4 → MEGA: el COLAPSO ESTELAR orbita en espiral en vez de acercarse en línea
            // recta. Su reloj es LINEAL porque las curvas viven en [MegaCollapse].
            val collapse = charge != null && charge.tier >= 4 && !reducedFx
            LaunchedEffect(charge?.id) {
                if (charge != null) {
                    chargeProgress.animateTo(
                        1f,
                        tween(
                            charge.durationMs,
                            easing = if (collapse) LinearEasing else FastOutSlowInEasing
                        )
                    )
                }
            }
            val dockX = animateFloatAsState(
                targetValue = slot.tutorialAnchorX ?: 0f,
                animationSpec = dockingSpec,
                label = "tutorialDockX_${slot.id}"
            )
            val dockY = animateFloatAsState(
                targetValue = slot.tutorialAnchorY ?: 0f,
                animationSpec = dockingSpec,
                label = "tutorialDockY_${slot.id}"
            )
            // Aparición "pop": el cuerpo nace pequeño y asienta con rebote elástico (una vez
            // por id — moverse no la re-dispara porque el key/instancia se conserva).
            val spawnScale = remember { Animatable(0.15f) }
            LaunchedEffect(Unit) {
                spawnScale.animateTo(
                    1f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)
                )
            }
            val radius = slot.type.level * 0.5f
            TeikaIcon(
                type = slot.type,
                modifier = Modifier
                    .offset {
                        // Búsqueda lineal sobre ≤15 cuerpos, en fase de layout: más barato que
                        // recomponer la capa, y sin asignar nada.
                        val b = bodiesState.value.firstOrNull { it.id == slot.id }
                            ?: return@offset IntOffset.Zero
                        val chargeNow = charge
                        val baseX: Float
                        val baseY: Float
                        if (chargeNow != null) {
                            val startsAtA = chargeNow.aId == slot.id
                            val originX = if (startsAtA) chargeNow.ax else chargeNow.bx
                            val originY = if (startsAtA) chargeNow.ay else chargeNow.by
                            val k = chargeProgress.value
                            if (collapse) {
                                // Órbita alrededor de un centro que DERIVA al del tablero, de
                                // donde irrumpe el MEGA. Trigonometría en layout, sin asignar.
                                val half = GameState.GRID_SIZE / 2f
                                val ox = originX - chargeNow.cx
                                val oy = originY - chargeNow.cy
                                val r = MegaCollapse.orbitRadius(k, kotlin.math.hypot(ox, oy))
                                val ang = MegaCollapse.orbitAngle(k, kotlin.math.atan2(oy, ox))
                                baseX = MegaCollapse.center(k, chargeNow.cx, half) + r * kotlin.math.cos(ang)
                                baseY = MegaCollapse.center(k, chargeNow.cy, half) + r * kotlin.math.sin(ang)
                            } else {
                                baseX = originX + (chargeNow.cx - originX) * k
                                baseY = originY + (chargeNow.cy - originY) * k
                            }
                        } else if (slot.tutorialAnchorX != null && slot.tutorialAnchorY != null) {
                            baseX = dockX.value
                            baseY = dockY.value
                        } else {
                            baseX = b.x
                            baseY = b.y
                        }
                        val idling = tutorialIdleActive && !reducedFx && chargeNow == null &&
                            slot.tutorialAnchorX != null
                        val phase = ((slot.id.hashCode() and Int.MAX_VALUE) % 1_000) / 1_000f
                        val idleWave = if (idling) {
                            kotlin.math.sin((tutorialIdleClock.value + phase) * 2f * Math.PI).toFloat()
                        } else 0f
                        val idleLift = idleWave * 0.018f * slot.type.level
                        IntOffset(
                            ((baseX - radius) * cellPx).roundToInt(),
                            ((baseY - radius + idleLift) * cellPx).roundToInt()
                        )
                    }
                    .size(cellSize * slot.type.level)
                    .graphicsLayer {
                        // El diámetro de layout sigue siendo el radio físico; la compensación del
                        // padding interno del Lottie vive en un único contrato visual compartido.
                        val chargeP = if (charge != null) chargeProgress.value else 0f
                        val idling = tutorialIdleActive && !reducedFx && charge == null &&
                            slot.tutorialAnchorX != null
                        val phase = ((slot.id.hashCode() and Int.MAX_VALUE) % 1_000) / 1_000f
                        val idleWave = if (idling) {
                            kotlin.math.sin((tutorialIdleClock.value + phase) * 2f * Math.PI).toFloat()
                        } else 0f
                        val breath = if (idling) 1f + (idleWave + 1f) * 0.006f else 1f
                        val chargeScale = if (collapse) MegaCollapse.bodyScale(chargeP)
                            else 1f + chargeP * if (reducedFx) 0.04f else 0.14f
                        val s = teikaVisualScale(slot.type) * spawnScale.value * breath * chargeScale
                        scaleX = s*(1f+squash.value*.13f)
                        scaleY = s*(1f-squash.value*.13f)
                        rotationZ = if (collapse) MegaCollapse.bodySpinDeg(chargeP) else 0f
                        alpha = when {
                            collapse -> MegaCollapse.bodyAlpha(chargeP)
                            charge != null -> (1f - ((chargeP - 0.78f) / 0.22f)).coerceIn(0f, 1f)
                            else -> 1f
                        }
                    }
            )
        }
    }
}

/** Un cuerpo congelado: lo que la composición del hielo necesita (la posición va en el layout). */
private data class FrozenSlot(val id: String, val type: TeikaType, val turnsLeft: Int)

/**
 * Bioma Glaciar (motor físico): bloque de hielo encerrando cada CUERPO congelado,
 * anclado a su posición continua.
 *
 * Mismo arreglo que [TeikaPhysicsBodiesLayer] (2026-07-31b): la composición se ata al roster de
 * congelados —que cambia por TURNO, no por frame— y cada bloque de hielo lee su posición dentro de
 * `offset {}`. Su KDoc anterior admitía el problema («se recompone solo esta capa a ~60 fps»): un
 * cuerpo congelado ni siquiera se mueve, así que era trabajo por frame para pintar algo QUIETO.
 */
@Composable
private fun FrozenBodiesOverlay(
    bodiesFlow: StateFlow<List<TeikaBody>>,
    cellSize: Dp
) {
    val bodiesState = bodiesFlow.collectAsState()
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    val frozen by remember(bodiesState) {
        derivedStateOf {
            bodiesState.value.mapNotNull { b ->
                if (b.frozenTurns > 0) FrozenSlot(b.id, b.type, b.frozenTurns) else null
            }
        }
    }
    frozen.forEach { slot ->
        key("ice_${slot.id}") {
            val radius = slot.type.level * 0.5f
            FrozenTeikaOverlay(
                modifier = Modifier
                    .offset {
                        val b = bodiesState.value.firstOrNull { it.id == slot.id }
                            ?: return@offset IntOffset.Zero
                        IntOffset(
                            ((b.x - radius) * cellPx).roundToInt(),
                            ((b.y - radius) * cellPx).roundToInt()
                        )
                    }
                    .size(cellSize * slot.type.level),
                // M-03: el hielo se agrieta según lo que le queda de deshielo (fuente única del VM).
                turnsLeft = slot.turnsLeft,
                turnsTotal = com.cukiti.teikafusion.viewmodel.GameViewModel.GLACIER_FROZEN_TURNS
            )
        }
    }
}

/**
 * **EL NÚMERO DE LA BOBINA, POR ENCIMA DEL CUERPO** (bug de playtest, 2026-09-03).
 *
 * Reporte: «el Teika está por encima de los bloques con bobinas; abajo estaban completas del 1 al 4,
 * pero al darle a la que faltaba decía que estaba mal y se reiniciaba el conteo».
 *
 * El circuito se resuelve tocando las bobinas **en orden ascendente**, y el orden esperado se
 * calcula sobre el GRID: la bobina más baja sin encender de la fila, esté o no a la vista. Un Teika
 * que rueda hasta posarse sobre la fila **tapa su número** —los cuerpos se dibujan mucho después que
 * las celdas— y a partir de ahí el jugador está resolviendo un puzzle al que le falta una pista:
 * toca las visibles, todas fallan por orden, cada fallo CORTOCIRCUITA la fila (borrando el 1-2-3-4
 * que ya llevaba) y encima le cuesta un turno. La fila no se puede descargar nunca, así que esa
 * línea no se puede completar: es un atasco sin salida que además desangra el presupuesto.
 *
 * La celda tapada **sí era pulsable** —el toque va por coordenadas de rejilla y esta capa no
 * intercepta punteros—, así que lo único que faltaba era VERLA. Por eso la corrección es de dibujo.
 *
 * Es el mismo problema que [FrozenBodiesOverlay] ya resolvió para el hielo («si no, el Teika tapaba
 * la escarcha y parecía no estar congelado») y se arregla igual: repintando encima. Solo se redibujan
 * las bobinas realmente tapadas, que son como mucho un puñado.
 */
@Composable
private fun CoilsOverBodiesOverlay(
    grid: Map<Position, CellContent>,
    bodiesFlow: StateFlow<List<TeikaBody>>,
    cellSize: Dp
) {
    // Posiciones con bobina: cambian solo cuando cambia el tablero, no cada frame.
    val coils = remember(grid) {
        grid.mapNotNull { (p, c) ->
            (c as? CellContent.Wood)?.takeIf { it.coil > 0 }?.let { p to it }
        }
    }
    if (coils.isEmpty()) return

    val bodiesState = bodiesFlow.collectAsState()
    val density = LocalDensity.current
    val cellPx = with(density) { cellSize.toPx() }
    // `derivedStateOf`: los cuerpos cambian 60 veces por segundo, pero el conjunto de bobinas
    // TAPADAS solo cambia cuando uno cruza una frontera de celda. Sin esto, esta capa recompondría
    // cada frame por una respuesta que casi siempre es la misma.
    val covered by remember(coils, bodiesState) {
        derivedStateOf {
            val occupied = com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld
                .occupiedCellsOf(bodiesState.value).keys
            coils.filter { it.first in occupied }
        }
    }

    covered.forEach { (pos, wood) ->
        key("coil_over_body_${pos.x}_${pos.y}") {
            CoilOverlay(
                number = wood.coil,
                lit = wood.coilLit,
                seen = wood.coilSeen,
                modifier = Modifier
                    .offset {
                        IntOffset((pos.x * cellPx).roundToInt(), (pos.y * cellPx).roundToInt())
                    }
                    .size(cellSize)
            )
        }
    }
}
