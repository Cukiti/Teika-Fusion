package com.cukiti.teikafusion.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.RoundedCorner
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.activity.compose.BackHandler
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import com.cukiti.teikafusion.AdManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import com.cukiti.teikafusion.model.LevelObjective
import com.cukiti.teikafusion.model.Piece
import com.cukiti.teikafusion.model.TargetColor
import com.cukiti.teikafusion.viewmodel.GameViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.model.lavaActive
import com.cukiti.teikafusion.model.Position
import kotlinx.coroutines.flow.first
import com.cukiti.teikafusion.ui.theme.DarkSurface
import com.cukiti.teikafusion.ui.theme.NeonBlue
import com.cukiti.teikafusion.ui.theme.NeonPurple
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI

import kotlinx.coroutines.delay
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cukiti.teikafusion.model.WILDCARD_COLOR

/**
 * Eventos de bioma cuyo DUEÑO es su propio VFX en `GameBoard`: la animación los consume en su
 * `onComplete`. La háptica de [GameScreen] los mira pero NO los consume — hacerlo los borraría del
 * estado en el siguiente flush (`EVENT_FLUSH_MS` = 32 ms) y la animación se cortaría a un frame.
 *
 * Mantener esta lista sincronizada con los VFX de `GameBoard` que llaman a `onEventConsumed`, y
 * **solo con eventos que estén en `biomeFxEvents`**: esta función se consulta únicamente dentro de
 * ese bucle, así que una entrada que no aparezca allí no protege nada (lo fija
 * `GameEventContractTest`). Por eso salió `CeilingCrush` — la prensa del techo del Infinito no es un
 * evento de bioma, no pasa por aquí, y su VFX (`CeilingCrushVFX`) ya es su único consumidor.
 */
private fun vfxOwnedBiomeEvent(ev: com.cukiti.teikafusion.model.GameEvent): Boolean = when (ev) {
    is com.cukiti.teikafusion.model.GameEvent.CrystalCrack,         // CrystalCrackVFX (360 ms)
    is com.cukiti.teikafusion.model.GameEvent.DrowsyOnset,          // DrowsyOnsetVFX (~1,1 s)
    is com.cukiti.teikafusion.model.GameEvent.LavaSurge,            // LavaSurgeVFX (620 ms)
    is com.cukiti.teikafusion.model.GameEvent.PoisonSpread,         // PoisonSpreadVFX (2026-09-28)
    is com.cukiti.teikafusion.model.GameEvent.PlantHurt,            // PlantHurtVFX
    is com.cukiti.teikafusion.model.GameEvent.AstroStrike,          // AstroStrikeVFX
    is com.cukiti.teikafusion.model.GameEvent.AstroRetreat,         // AstroRetreatVFX
    is com.cukiti.teikafusion.model.GameEvent.GluttonBite,          // GluttonBiteVFX (640 ms)
    is com.cukiti.teikafusion.model.GameEvent.MeteorSpotted,        // MeteorSpottedVFX (P-294)
    is com.cukiti.teikafusion.model.GameEvent.MeteorImpact,         // MeteorImpactVFX (P-294)
    is com.cukiti.teikafusion.model.GameEvent.MeteorDeflect,        // MeteorDeflectVFX (P-294)
    is com.cukiti.teikafusion.model.GameEvent.RockCrack,            // RockCrackVFX (P-319)
    is com.cukiti.teikafusion.model.GameEvent.MoonReturn,           // MoonReturnVFX (P-321)
    is com.cukiti.teikafusion.model.GameEvent.KintsugiScar,         // KintsugiScarVFX (P-323)
    is com.cukiti.teikafusion.model.GameEvent.VineSprout,           // VineSproutVFX (P-324)
    is com.cukiti.teikafusion.model.GameEvent.KingSwordRaise,       // KingSwordRaiseVFX (P-294)
    is com.cukiti.teikafusion.model.GameEvent.KingSwordStrike,      // KingSwordStrikeVFX (P-294)
    is com.cukiti.teikafusion.model.GameEvent.KingSwordSheathe,     // KingSwordSheatheVFX (P-294)
    is com.cukiti.teikafusion.model.GameEvent.KingPieceRaise,       // KingPieceRaiseVFX (P-329)
    is com.cukiti.teikafusion.model.GameEvent.KingPieceStrike,      // KingPieceStrikeVFX (P-329)
    is com.cukiti.teikafusion.model.GameEvent.KingPieceRetreat,     // KingPieceRetreatVFX (P-329)
    is com.cukiti.teikafusion.model.GameEvent.NinjaBoardSlash,     // NinjaBoardSlashVFX (950 ms)
    is com.cukiti.teikafusion.model.GameEvent.NinjaBoardRepair,     // NinjaBoardRepairVFX (1100 ms)
    is com.cukiti.teikafusion.model.GameEvent.MirrorFlash,          // MirrorFlashVFX (P-246)
    is com.cukiti.teikafusion.model.GameEvent.PhantomFade,          // PhantomFadeVFX (P-246)
        -> true
    else -> false
}

@Composable
private fun CharacterRevealIcon(resId: Int, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    val progress by animateLottieCompositionAsState(composition, iterations = LottieConstants.IterateForever)
    LottieAnimation(composition = composition, progress = { progress }, modifier = modifier)
}

// B-4: data classes elevadas fuera del composable — evitan redescriptor de clase en cada recomposición
private data class FlyingCoinEntry(val id: Int, val start: Offset, val end: Offset)

/** Entregas del objetivo inicial que ya llegaron visualmente a su slot real del HUD. */
private data class ObjectiveRevealHudState(
    val token: Int,
    val revealedColors: Set<TargetColor> = emptySet(),
    val megaRevealed: Boolean = false,
)

/**
 * Aire (en CELDAS) entre el borde inferior de la ficha arrastrada y el dedo. La ficha se levanta
 * BIEN por encima de la mano (petición de playtest) para que ni el dedo ni la palma tapen la ficha
 * ni la fila objetivo, pero sigue siendo proporcional: con la elevación fija de 248 dp de antes,
 * una ficha de 1 de alto se iba 5,6 celdas arriba y la última fila quedaba fuera del alcance del
 * pulgar en pantallas cortas.
 */
// Sube de 2,2 a 3,4 (playtest 2026-07-27: «que suba hacia delante mucho más»). Con 2,2 la mano
// seguía cubriendo la fila justo debajo de la ficha, que es precisamente la que se está mirando
// para decidir el encaje. Sigue siendo PROPORCIONAL al alto de la ficha, y el seguimiento la topa
// contra el borde superior de la pantalla (`coerceAtLeast(rootOffset.y)`), así que en pantallas
// cortas no se sale ni deja de alcanzarse la última fila.
internal const val FINGER_LIFT_CELLS = 3.4f

/**
 * **P-449 · LA PISTA DEL GIRO.** Un ↻ pequeño y quieto en la esquina del cuadro: «tócala y gira». Solo
 * hasta que el jugador gire una ficha (después sobra). Quieto a propósito: es un dato, y en la bandeja
 * lo que se mueve se lee como una ficha. El lector ya ofrece la acción «Rotar» en la propia ficha.
 */
@Composable
private fun RotateHintBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text("↻", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
}

/**
 * **P-448 · LO QUE SUBE LA FICHA SOBRE EL DEDO, SEGÚN DÓNDE ESTÉ EL DEDO** (2026-10-02).
 *
 * Con la elevación fija de [FINGER_LIFT_CELLS] (medido en el A15: ~570 px), apuntar a la fila de
 * arriba del tablero obligaba a estirar el dedo casi hasta el borde. Ahora la ficha va alta cuando el
 * dedo sale de la bandeja —ahí es donde taparía lo que apunta— y baja de forma continua hasta
 * [FINGER_LIFT_MIN_CELLS] cuando el dedo llega a lo alto del tablero. Devuelve CELDAS de hueco entre
 * el dedo y el borde inferior de la ficha. Pura, para poder probarla sin pantalla.
 */
internal fun liftGapCells(fingerY: Float, boardTopY: Float, trayTopY: Float): Float {
    if (trayTopY <= boardTopY) return FINGER_LIFT_CELLS
    val t = ((fingerY - boardTopY) / (trayTopY - boardTopY)).coerceIn(0f, 1f)
    return FINGER_LIFT_MIN_CELLS + (FINGER_LIFT_CELLS - FINGER_LIFT_MIN_CELLS) * t
}

/** Hueco mínimo (con el dedo en lo alto del tablero): la ficha sigue sin quedar bajo la yema. */
internal const val FINGER_LIFT_MIN_CELLS = 1.6f


/**
 * Ventana para considerar que un combo épico llega ENCADENADO al anterior. Dentro de ella, la
 * celebración suelta sus pasadas a pantalla completa (flash y scrim): cuestan tres capas por frame
 * sobre un tablero que está resolviendo su cascada, y a la segunda seguida ya no comunican nada.
 */
private const val EPIC_CHAIN_WINDOW_MS = 2_600L

/**
 * Alto del banner de AdMob (`AdSize.BANNER` = 320×50). Se declara aquí porque lo usan DOS sitios que
 * tienen que decir exactamente lo mismo: el hueco reservado al final de la columna de juego y el
 * banner real, que va anclado al borde inferior de la pantalla y fuera del flujo vertical.
 */
private val BANNER_HEIGHT = 50.dp

@Composable
fun GameScreen(
    viewModel: GameViewModel,
    modifier: Modifier = Modifier
) {
    // M-1: collectAsStateWithLifecycle detiene la colección cuando la app está en background
    val gameState          by viewModel.gameState.collectAsState()
    // La cola de eventos agrupada UNA vez por cambio, en lugar de un `filterIsInstance` por
    // consumidor (perf 2026-08-09). Ver [GameEventBuckets].
    val eventBuckets = rememberEventBuckets(gameState.events)
    // P-283: el estilo del bloque equipado, para la vibración propia al colocar y al romper.
    val placeSkinStyle = LocalBlockSkin.current.style
    val dragState          by viewModel.dragState.collectAsState()
    // P-449: mientras el jugador no haya girado ninguna ficha, cada cuadro lleva un ↻.
    val rotateLearned      by viewModel.rotateLearned.collectAsState()
    val musicVolume        by viewModel.musicVolume.collectAsState()
    val sfxVolume          by viewModel.sfxVolume.collectAsState()
    val vibrationEnabled   by viewModel.vibrationEnabled.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val tiltEnabled        by viewModel.tiltEnabled.collectAsState()
    val isMuted            by viewModel.isMuted.collectAsState()
    // P-13: camino de juego accesible (seleccionar ficha → elegir celda). Solo se monta con el
    // lector de pantalla encendido; con el dedo normal no cuesta ni un nodo.
    // ¿Se le ofrece publicidad FORZADA a este jugador? (compra «Quitar anuncios» + kill-switch
    // remoto.) Solo manda sobre el BANNER: es lo único forzado que hay en esta pantalla.
    val adsOffered by AdManager.adsOfferedFlow.collectAsState()
    // ¿Y un RECOMPENSADO? Es otra pregunta desde el 2026-09-06: el vídeo voluntario sobrevive a
    // «Quitar anuncios», porque lo que se compra es no ser interrumpido, no perder la forma
    // gratuita de ganar teikoins. Lo usan revivir y doblar monedas.
    val rewardedOffered by AdManager.rewardedOfferedFlow.collectAsState()
    val canRequestAds by com.cukiti.teikafusion.ConsentManager.canRequestAds.collectAsState()
    // Cupo de anuncios de monedas (P-18/P-19): usos restantes y enfriamiento, para que el botón
    // diga la verdad. El segundero solo corre mientras HAY enfriamiento — cero coste el resto.
    val adQuota by viewModel.adQuota.collectAsState()
    // El banner no debe competir con el panel de victoria ni quedar visible detrás de su scrim.
    // También libera su hueco reservado durante la transición al siguiente nivel.
    val bannerVisible = adsOffered && canRequestAds &&
        !com.cukiti.teikafusion.model.TutorialCampaign.isTutorialLevel(gameState.level) &&
        gameState.status != com.cukiti.teikafusion.model.GameStatus.LEVEL_COMPLETE
    // (El segundero del enfriamiento vive en [AdCooldownTicker], junto a la barra superior: aquí
    //  arriba recomponía la pantalla ENTERA una vez por segundo mientras durara la espera.)
    val touchExploration = rememberTouchExplorationEnabled()
    val a11ySelectedPieceId by viewModel.a11ySelectedPieceId.collectAsState()
    val reducedFx          by viewModel.reducedFx.collectAsState()
    val consecutiveWins    by viewModel.consecutiveWins.collectAsState()
    val tutorialStage      by viewModel.tutorialStage.collectAsState()
    val tutorialTiltDirection by viewModel.tutorialTiltDirection.collectAsState()
    val tutorialStageReady by viewModel.tutorialStageReady.collectAsState()
    val selectedCompanionId    by viewModel.selectedCompanionId.collectAsState()
    val companionUpgrades      by viewModel.companionUpgrades.collectAsState()
    val lastCrystalDrop        by viewModel.lastCrystalDrop.collectAsState()
    val companionCinematic     by viewModel.companionCinematic.collectAsState()
    val roundStartToken        by viewModel.roundStartToken.collectAsState()
    val companionCharges       by viewModel.companionCharges.collectAsState()
    val claimedCompanionIds    by viewModel.claimedCharacterIds.collectAsState()
    val companionBelt          by viewModel.companionBelt.collectAsState()

    // Habilidades activables (2026-08-28): hoja que sube desde BAJO LA BANDEJA siguiendo el dedo.
    // Vive aquí y no en un Dialog porque la app es INMERSIVA: una segunda ventana saca las barras
    // del sistema y empuja el contenido. `boosterProgress` (0..1) lo publica la propia hoja y sirve
    // para difuminar el tablero en sincronía con el gesto, en vez de con un salto.
    //
    // Perf (2026-09-26): el valor animado NO se lee en el cuerpo de esta pantalla. Cada fotograma de
    // la apertura recomponía GameScreen entera; ahora el desenfoque lo lee la capa gráfica del
    // tablero y el resto solo necesita saber SI la hoja está abierta (`derivedStateOf`: un booleano
    // que cambia dos veces por apertura, no sesenta).
    val boosterProgress = remember { mutableFloatStateOf(0f) }
    val boosterOpen by remember { derivedStateOf { boosterProgress.floatValue > 0.01f } }
    val boosterInteracting by remember { derivedStateOf { boosterProgress.floatValue > 0.02f } }

    // Fuego / Llamarada Infernal: la bandeja y la pieza arrastrada arden en llamas reales.
    val fireActive = gameState.companionPassiveEffect == "fire" && gameState.companionPassiveCount > 0
    
    val density = LocalDensity.current
    val view = LocalView.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var rootOffset by remember { mutableStateOf(Offset.Zero) }
    var boardOffsetPx by remember { mutableStateOf(Offset.Zero) }
    // TIEMPO BALA (2026-09-01): dónde están las dos piezas de HUD que un instante de enseñanza puede
    // querer enfocar. Se miden, no se estiman — ver `slowMotionFocusRect`.
    var goalBarRectWindow by remember { mutableStateOf(Rect.Zero) }
    var abilityHandleRect by remember { mutableStateOf(Rect.Zero) }
    // El PANEL DE OBJETIVOS y el MEDALLON de turnos, por separado: cada leccion senala el suyo y la
    // otra mitad del HUD ni siquiera esta en pantalla. Ver `slowMotionFocusRects`.
    var objectivePanelRectWindow by remember { mutableStateOf(Rect.Zero) }
    var movesMedallionRectWindow by remember { mutableStateOf(Rect.Zero) }
    // Sube en uno con cada suelte que la leccion guiada rechaza. Alimenta el aviso «aqui no».
    var tutorialMisplaceToken by remember(gameState.level) { mutableIntStateOf(0) }
    // P-202: la píldora de corrección sirve para las dos jugadas equivocadas de una lección —soltar
    // fuera de la casilla. Hubo una segunda —tocar la ficha para rotarla— hasta que el giro se
    // retiró del juego (2026-09-07); el mensaje sigue siendo variable por si vuelve a haber dos.
    var tutorialMisplaceMessage by remember(gameState.level) { mutableStateOf(TUTORIAL_MISPLACE_TEXT) }
    var trayOffsetPx by remember { mutableStateOf(Offset.Zero) }
    // Las mediciones pertenecen al HUD del nivel actual. Reiniciarlas dentro de un
    // LaunchedEffect(level) dejaba una carrera: el HUD nuevo podía publicar sus rects y el effect
    // las borraba DESPUÉS, sin que onGloballyPositioned tuviera por qué volver a ejecutarse. El
    // ObjectiveReveal quedaba invisible esperando destinos y bloqueaba todos los toques.
    var targetCounterRects by remember(gameState.level) {
        mutableStateOf(mapOf<com.cukiti.teikafusion.model.TargetColor, androidx.compose.ui.geometry.Rect>())
    }
    var megaCounterRect by remember(gameState.level) { mutableStateOf(Rect.Zero) }
    // Conteo MOSTRADO del objetivo: va por detrás del real (gameState.targetsCollected). El número solo
    // baja cuando la ESTRELLA aterriza en su contador (onComplete de TargetCollectVFX) → premium/sincronía.
    // Se reconcilia con el real cuando no quedan estrellas en vuelo (por si alguna no animó).
    var displayedTargets by remember(gameState.level) { mutableStateOf(gameState.targetsCollected) }

    val tutorialLevel = com.cukiti.teikafusion.model.TutorialCampaign
        .isTutorialLevel(gameState.level)
    val tutorialSpecNow = com.cukiti.teikafusion.model.TutorialCampaign.spec(gameState.level)
    // P-82: los TIPOS de los cuerpos vivos. `teikaBodies` cambia cada frame, pero el pedido solo
    // mira los tiers, asi que se derivan y la insignia solo recompone cuando de verdad cambian.
    val teikaBodiesState = viewModel.teikaBodies.collectAsState()
    val liveTeikaTypes by remember {
        androidx.compose.runtime.derivedStateOf { teikaBodiesState.value.map { it.type } }
    }
    val tutorialPendingNow by viewModel.showTutorial.collectAsState()
    // El tutorial esconde el HUD... salvo en las lecciones que lo ENSEnAN (2026-09-01). La 5 abre el
    // panel de objetivo y la 6 la barra de meta con su medallon: no se puede explicar un marcador
    // que no esta en pantalla.
    val showObjectivePanel = !tutorialLevel || tutorialSpecNow?.showsObjective == true
    val showGoalBar = !tutorialLevel || tutorialSpecNow?.showsMoves == true
    val currentLevelObjective = gameState.levelObjective
    val currentMegaRequired = com.cukiti.teikafusion.model.ChapterArc.requiresMega(gameState.level)
    // P-263: el momento en que las estrellas están y solo falta el MEGA se NOTA en la mano, una vez.
    LaunchedEffect(gameState.megaPending) {
        if (gameState.megaPending) Haptics.warning()
    }
    val objectivePresentationActive =
        roundStartToken > 0 &&
            !tutorialLevel &&
            gameState.status == com.cukiti.teikafusion.model.GameStatus.PLAYING &&
            currentLevelObjective != null
    var objectiveRevealHudState by remember(gameState.level) {
        mutableStateOf(ObjectiveRevealHudState(token = roundStartToken))
    }
    var objectiveLandingImpactToken by remember(gameState.level) { mutableIntStateOf(0) }
    var objectiveLandingColor by remember(gameState.level) { mutableStateOf<TargetColor?>(null) }
    var objectiveLandingIsMega by remember(gameState.level) { mutableStateOf(false) }
    // Un token nuevo en el mismo nivel (reintento) invalida sincrónicamente las entregas del
    // intento anterior; no hace falta un effect tardío que deje visible el HUD durante un frame.
    val currentRevealHudState = if (objectiveRevealHudState.token == roundStartToken) {
        objectiveRevealHudState
    } else {
        ObjectiveRevealHudState(token = roundStartToken)
    }
    val revealedObjectiveColors = if (objectivePresentationActive) {
        currentRevealHudState.revealedColors
    } else {
        currentLevelObjective?.required?.keys.orEmpty()
    }
    val megaObjectiveRevealed = !objectivePresentationActive || currentRevealHudState.megaRevealed

    // Estado local para animación fluida de la pieza voladora
    var draggedPieceLocal by remember { mutableStateOf<Piece?>(null) }
    // P-276: último giro hecho en la bandeja (id de la ficha, contador). Lo escribe el toque seco y lo
    // lee el cuadro de esa ficha para dar su cuarto de vuelta; el contador hace que dos giros
    // seguidos de la misma ficha sean dos señales distintas.
    var trayRotateTick by remember { mutableStateOf<Pair<String, Int>?>(null) }
    // Posición (esquina superior izquierda, coords de ventana) de la ficha voladora. Estado PLANO,
    // NO Animatable: durante el arrastre se escribe DIRECTO desde el callback del gesto. Antes cada
    // muestra del dedo hacía `launch { dragOffset.snapTo(...) }` — un despacho de corrutina + la
    // cancelación interna del Animatable por cada evento táctil, y eso es lo que hacía que la ficha
    // se sintiera pesada y con retraso. Se lee solo dentro de `.offset { }` (fase de layout), así
    // que mover el dedo no recompone nada.
    val dragPos = remember { mutableStateOf(Offset.Zero) }
    // Animación en curso de la ficha voladora (recogida hacia el dedo o vuelta a la bandeja). El
    // primer movimiento del dedo la cancela: manda el dedo, no la animación.
    var dragAnimJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    // Recogida fluida: la pieza voladora crece de su tamaño en la bandeja al tamaño de
    // celda del tablero con un pequeño overshoot (como Block Blast). 1f = tamaño completo.
    val grabScale = remember { Animatable(1f) }
    var grabScaleJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val fichonaTilt = remember { mutableFloatStateOf(0f) }
    val returnScale = remember { mutableFloatStateOf(1f) }
    var returnActive by remember { mutableStateOf(false) }
    var pieceOriginalPos by remember { mutableStateOf(Offset.Zero) }
    // Posiciones (en coordenadas de ventana) de las piezas de la bandeja.
    // Solo se consultan dentro de gestos/clicks, NUNCA durante la composición →
    // mapas planos (no-snapshot) para que escribirlos desde onGloballyPositioned en
    // cada layout no dispare una recomposición de todo GameScreen (≈1400 líneas).
    // (El agarre ya NO usa el centro de la ficha: se elige por ranura. Aquí solo queda la esquina
    //  superior izquierda, que es el origen del vuelo de recogida y del clon de CrystalFlight.)
    val pieceTopLefts = remember { mutableMapOf<String, Offset>() }

    var showOverlay by remember { mutableStateOf(false) }
    var showQuitDialog by remember { mutableStateOf(false) }
    // "No hay espacio" CENTRADO en el tablero — derrota real: el jugador soltó una ficha sobre el
    // tablero atascado sin encaje posible en ninguna rotación. Se muestra ~1.1 s y luego se ofrece
    // revivir (pantalla de cristal). Ver el handler de suelte inválido más abajo.
    var noSpaceCenterVisible by remember { mutableStateOf(false) }
    // C-6: reiniciar destruye el intento guardado, y eso ahora cuesta una vida. La ventana de pausa
    // lo dice, y solo cuando de verdad hay intento que destruir (desde la primera colocación).
    val hasSavedGameNow by viewModel.hasSavedGame.collectAsState()
    // P-105 · FASE 1 del tablero atascado: el cartel «SIN SALIDA» con la salida que de verdad hay.
    // `null` = no hay atasco (o el jugador ya lo despachó tocando algo).
    var deadEnd by remember { mutableStateOf<com.cukiti.teikafusion.model.BoardDeadEnd?>(null) }
    // Taller, entrega transitoria y geometría medida del botón/bandeja.
    var bankWorkshopOpen by remember { mutableStateOf(false) }
    var workshopHeldIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var workshopDelivery by remember { mutableStateOf<WorkshopDelivery?>(null) }
    DisposableEffect(bankWorkshopOpen, workshopDelivery != null) {
        viewModel.setWorkshopActive(bankWorkshopOpen || workshopDelivery != null)
        onDispose { viewModel.setWorkshopActive(false) }
    }
    LaunchedEffect(gameState.status) {
        if (gameState.status != com.cukiti.teikafusion.model.GameStatus.PLAYING) {
            bankWorkshopOpen = false
            workshopHeldIds = emptySet()
            workshopDelivery = null
        }
    }
    // El banco es de pago (2026-09-28): quién lo tiene, y la oferta abierta al tocar el candado.
    val bankOwnedNow by viewModel.blockBankOwned.collectAsState()
    var bankOfferOpen by remember { mutableStateOf(false) }
    val bankRectRef = remember { arrayOf(Rect.Zero) }
    val trayPieceCellPx = remember { mutableMapOf<String, Float>() }

    // El botón de rendirse entra con retraso: el dedo que acaba de soltar la ficha está justo encima.
    var surrenderVisible by remember { mutableStateOf(false) }
    // Los objetivos regulares se presentan automáticamente dentro de la partida sin texto ni botón.
    var trayRectWindow     by remember { mutableStateOf(Rect.Zero) }
    // P-201: el cuadro de CADA ficha, por id. Lo consume `slowMotionFocusRects` para que una
    // cápsula que dice «esa ficha» señale esa y no la banda entera.
    //
    // Mapa PLANO (no-snapshot) por el mismo motivo que [pieceTopLefts], y aquí importa el doble:
    // `onGloballyPositioned` corre en CADA pasada de layout de la bandeja, así que un
    // `mutableStateMapOf` recompondría GameScreen entero a cada relayout. Se lee solo cuando un
    // shot pide su rectángulo —muchos turnos después de que la bandeja se haya medido—, así que no
    // hace falta que la lectura sea observable. Un id sin medir cae a la bandeja entera.
    //
    // Solo crece dentro del nivel (tres entradas por reposición, un `Rect` cada una) y se limpia al
    // cambiar de nivel.
    val trayPieceRectsWindow = remember { mutableMapOf<String, Rect>() }
    // Alto real de la raiz en px. Con `trayRectWindow` y `rootOffset` da la banda de abajo que la
    // celebracion epica tiene PROHIBIDO apagar (ver EpicComboVFX.protectedBottomPx).
    var rootHeightPx       by remember { mutableFloatStateOf(0f) }
    // Rect real de la única ficha guiada. El clon del tutorial parte exactamente de aquí, de modo
    // que mano y ficha compartan trayectoria en vez de que la mano viaje sola desde el centro del panel.
    var tutorialPieceRectWindow by remember { mutableStateOf(Rect.Zero) }
    LaunchedEffect(gameState.level) {
        tutorialPieceRectWindow = Rect.Zero
        trayPieceRectsWindow.clear()
    }

    // Flying coin animation — B-7: SnapshotStateList evita crear nueva lista en cada remoción
    val flyingCoinEntries = remember { androidx.compose.runtime.snapshots.SnapshotStateList<FlyingCoinEntry>() }
    var coinCounterRect   by remember { mutableStateOf(Rect.Zero) }
    // Rect del marcador de score (top bar) para que el "+score" vuele hacia él.
    var scoreCounterRect  by remember { mutableStateOf(Rect.Zero) }
    var flyingCoinNextId  by remember { mutableIntStateOf(0) }
    var prevTeikoins      by remember { mutableIntStateOf(gameState.teikoins) }
    // (El disparo del vuelo de la moneda vive DENTRO de BoxWithConstraints, donde existe el tamaño
    //  real de celda — antes usaba una celda hardcodeada de 33 dp y el origen caía lejos del tablero.)

    // BackHandler: pausa → reanudar; jugando → diálogo de salida.
    BackHandler(enabled = gameState.status == com.cukiti.teikafusion.model.GameStatus.PAUSED) {
        viewModel.resumeGame()
    }
    // Con el diálogo de salida ABIERTO, el atrás lo CANCELA en vez de volver a pedirlo. Antes este
    // handler seguía activo (el estado sigue siendo PLAYING mientras se pregunta), así que atrás
    // reasignaba `showQuitDialog = true` y no pasaba nada: la única salida era el botón. Regla del
    // bloque 23: todo overlay sale por donde entró.
    // ── EL TUTORIAL NO TIENE PUERTA TRASERA (2026-09-04, pedido del usuario) ──────────────────
    // Mientras la campaña sigue pendiente, una lección tenía tres salidas que se saltaban el guion:
    // el «❮», el engranaje (que abría el panel de pausa entero: Continuar, Reiniciar, Salir) y el
    // atrás del sistema. Las dos primeras ya no se pintan (`showExits`); esta se traga el atrás.
    //
    // No es un callejón: la salida existe, se llama SALTAR TUTORIAL, está arriba a la derecha del
    // tablero, y además deja la cuenta coherente — marca la campaña por vista y desbloquea el primer
    // nivel de verdad, cosa que abandonar por el «❮» no hacía.
    val tutorialLocked = tutorialLevel && tutorialPendingNow
    BackHandler(enabled = tutorialLocked) { /* la lección se sale por su botón, no por atrás */ }
    BackHandler(
        enabled = !tutorialLocked &&
            gameState.status == com.cukiti.teikafusion.model.GameStatus.PLAYING
    ) {
        showQuitDialog = !showQuitDialog
    }

    val pgsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {}

    // (El "banner de personaje pendiente" se retiró: su flow era constante null desde que el
    //  desbloqueo pasó a auto-claim con reveal épico — el pulso que vivía aquí nunca corría.)

    // ── Character reveal overlay ───────────────────────────────────────────────
    var revealedCharacter by remember { mutableStateOf<com.cukiti.teikafusion.model.NamedTeika?>(null) }
    val revealAlpha = remember { Animatable(0f) }
    val revealScale = remember { Animatable(0.7f) }
    val revealSparkleAngle = remember { Animatable(0f) }

    // Observa CharacterRevealed events del game state (memoizado: solo re-filtra
    // cuando cambia la lista de eventos, no en cada recomposición por drag/score).
    val currentCharacterReveal = remember(eventBuckets) {
        eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.CharacterRevealed>().firstOrNull()
    }

    LaunchedEffect(currentCharacterReveal?.id) {
        if (currentCharacterReveal != null) {
            val character = com.cukiti.teikafusion.model.NamedTeikaRegistry.byId(currentCharacterReveal.characterId)
            if (character != null) {
                revealedCharacter = character
                revealScale.snapTo(0.7f)
                revealAlpha.snapTo(0f)
                launch { revealSparkleAngle.animateTo(720f, androidx.compose.animation.core.tween(3500)) }
                launch { revealScale.animateTo(1f, androidx.compose.animation.core.tween(400, easing = FastOutSlowInEasing)) }
                revealAlpha.animateTo(1f, androidx.compose.animation.core.tween(300))
                // El overlay permanece hasta que el jugador lo cierre (no auto-dismiss)
            }
            viewModel.consumeEvent(currentCharacterReveal.id)
        }
    }

    // ── Inclinación premium: los Teikas ruedan hacia donde el jugador inclina el teléfono ──
    // Solo escucha el sensor con la partida ACTIVA (batería); al pausar/salir reporta 0.
    TeikaTiltEffect(
        enabled = tiltEnabled &&
            gameState.status == com.cukiti.teikafusion.model.GameStatus.PLAYING
    ) { rawX ->
        viewModel.onDeviceTilt(rawX)
    }

    // ── Motor físico Suika: driver de simulación clavado al RELOJ DE FRAMES del render ──
    // withFrameNanos entrega el timestamp de cada frame de Compose (60/90/120 Hz reales):
    // el paso de física y la publicación del snapshot ocurren en fase de animación del MISMO
    // frame que los dibuja → movimiento fluido, sin el drift/judder de un delay(16) aparte.
    //
    // GATED por PLAYING (rendimiento, 2026-07-31b): el bucle era `LaunchedEffect(Unit)`, o sea que
    // seguía pidiendo un frame al Choreographer mientras GameScreen estuviera compuesto — en PAUSA,
    // con el overlay de fin de nivel, en la oferta de revivir y en el game over. El ViewModel salía
    // por la primera línea (`status != PLAYING`), así que no hacía nada… salvo impedir que la app se
    // quedara quieta: `withFrameNanos` mantiene viva la suscripción al reloj de frames y con ella el
    // ciclo de dibujo. Ahora, fuera de la partida, el reloj se suelta.
    //
    // El `lastNs` vive DENTRO del efecto a propósito: al reanudar arranca de cero y el primer dt es
    // 1/60 en vez del hueco entero de la pausa (que la física habría integrado como un salto).
    val physicsRunning = gameState.status == com.cukiti.teikafusion.model.GameStatus.PLAYING && !bankWorkshopOpen && workshopDelivery == null
    LaunchedEffect(physicsRunning) {
        if (!physicsRunning) return@LaunchedEffect
        var lastNs = 0L
        while (true) {
            val dt = androidx.compose.runtime.withFrameNanos { now ->
                val d = if (lastNs == 0L) 1f / 60f else (now - lastNs) / 1_000_000_000f
                lastNs = now
                d
            }
            viewModel.onTeikaPhysicsFrame(dt)
        }
    }

    val scoreScale = remember { Animatable(1f) }
    LaunchedEffect(gameState.score) {
        if (gameState.score > 0) {
            scoreScale.snapTo(1.5f)
            scoreScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )
        }
    }

    // Combo: solo háptica aquí; el VFX visual (×N + palabra) sale en el centro del tablero.
    // Premium: UNA composición cuya fuerza escala con la racha (sin delays manuales con jitter).
    LaunchedEffect(gameState.comboStreak) {
        if (gameState.comboStreak > 1) Haptics.combo(gameState.comboStreak)
    }
    LaunchedEffect(gameState.teikasFormed) {
        if (gameState.teikasFormed > 0) Haptics.fusion(2)
    }

    // FUSIÓN por TIER: la escalera háptica del plan (§13.7) — un T2 es un toque medio y un T3/T4 un
    // golpe pesado. NO se consume el evento aquí: de eso se encarga la capa de VFX que dibuja el
    // burst. Solo se observa (contrato de eventos: un único `consumeEvent` por tipo).
    //
    // MANDA EL TIER MÁS ALTO del lote (P-02, 2026-07-31). Con `firstOrNull` una cascada T2→T3→T4
    // llegada en el mismo lote de estado solo hacía sonar el T2 —el más flojo de los tres—, así que
    // la fusión más grande de la cadena se sentía como la más pequeña. Mismo criterio que la
    // sacudida en `GameBoard`: si varios caben en un frame, gana el grande.
    val currentFusion = remember(eventBuckets) {
        eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.FusionBurst>()
            .maxByOrNull { it.tier }
    }
    LaunchedEffect(currentFusion?.id) {
        val tier = currentFusion?.tier ?: return@LaunchedEffect
        Haptics.fusion(tier)
    }

    // (RETIRADO 2026-08-09, con el modo) El BEAT DE ENTRADA del tablero colgaba del reloj de la
    // run de WALLBLOCK (`infiniteElapsedSec`). Los niveles arrancan con su propia coreografía.
    val runIntro = remember { Animatable(1f) }

    // ChainPulse haptic: línea(s) múltiples simultáneas → cue de barrido (sin delay manual).
    val currentChainPulse = remember(eventBuckets) {
        eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.ChainPulse>().firstOrNull()
    }
    LaunchedEffect(currentChainPulse?.id) {
        if (currentChainPulse != null) {
            // Con un bloque premium la rotura ya vibra con su firma (BatchLineCellShatterVFX, P-283):
            // sumarle el barrido serían dos vibraciones por una jugada.
            if (!Haptics.hasBlockSignature(placeSkinStyle)) Haptics.lineClear(2)
            // La onda de choque visual se eliminó; aquí consumimos el evento para no dejarlo huérfano.
            viewModel.consumeEvent(currentChainPulse.id)
        }
    }

    // MEGA Teika: clímax háptico (rampa que sube, gira y golpea) cuando se forma la MEGA.
    val currentMega = remember(eventBuckets) {
        eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.MegaTeikaFriendExpansion>().firstOrNull()
    }
    LaunchedEffect(currentMega?.id) {
        if (currentMega != null) Haptics.mega()
    }

    // Derrota / victoria: cierre háptico (caída+golpe vs. arpegio ascendente) Y SONORO.
    // La victoria solo VIBRABA hasta 2026-07-31: el overlay entraba con sus estrellas y sus monedas
    // y el altavoz no decía nada, en la recompensa que el jugador persigue 293 veces. (La derrota sí
    // sonaba: `playGameOver` lo dispara el ViewModel al entrar en GAME_OVER.)
    LaunchedEffect(gameState.status) {
        when (gameState.status) {
            com.cukiti.teikafusion.model.GameStatus.GAME_OVER      -> Haptics.gameOver()
            com.cukiti.teikafusion.model.GameStatus.LEVEL_COMPLETE -> {
                Haptics.victory()
                viewModel.soundManager.playLevelVictory()
            }
            else -> {}
        }
    }

    // Habilidad de compañero: firma háptica temática por personaje, UNA vez por casteo. Sin
    // cinemática de personaje, la háptica dispara de inmediato → coincide con el VFX del tablero.
    LaunchedEffect(companionCinematic) {
        val id = companionCinematic ?: return@LaunchedEffect
        Haptics.companionAbility(id)
    }


    // Háptica de los eventos de bioma (+ consumo de los que NO tienen VFX propio, para que no se
    // acumulen). El "rebote" del cristal (squash de la celda al bajar `armor`) sigue viniendo del
    // ESTADO en CellView; lo que se le añadió en P-64 son las grietas, que sí son un evento.
    //
    // CONTRATO DE EVENTOS: exactamente UN sitio puede consumir un evento dado. Los que tienen su
    // propio VFX (que los consume en su onComplete) están en [vfxOwnedBiomeEvent] y aquí SOLO se
    // usan para la háptica: consumirlos aquí los borraría del estado en el siguiente flush (32 ms)
    // y su animación se cortaría a un frame.
    val biomeFxEvents = remember(gameState.events) {
        gameState.events.filter {
            it is com.cukiti.teikafusion.model.GameEvent.CrystalCrack ||
            it is com.cukiti.teikafusion.model.GameEvent.DrowsyOnset ||
            it is com.cukiti.teikafusion.model.GameEvent.LavaSurge ||
            it is com.cukiti.teikafusion.model.GameEvent.GluttonOpen ||
            it is com.cukiti.teikafusion.model.GameEvent.GluttonBite ||
            it is com.cukiti.teikafusion.model.GameEvent.MeteorSpotted ||
            it is com.cukiti.teikafusion.model.GameEvent.MeteorImpact ||
            it is com.cukiti.teikafusion.model.GameEvent.MeteorDeflect ||
            it is com.cukiti.teikafusion.model.GameEvent.RockCrack ||
            it is com.cukiti.teikafusion.model.GameEvent.MoonReturn ||
            it is com.cukiti.teikafusion.model.GameEvent.KintsugiScar ||
            it is com.cukiti.teikafusion.model.GameEvent.VineSprout ||
            // Tóxico: solo la SALPICADURA vibra (el avance normal llega a decenas de bloques a la vez).
            (it is com.cukiti.teikafusion.model.GameEvent.PoisonSpread && it.splash) ||
            it is com.cukiti.teikafusion.model.GameEvent.KingSwordRaise ||
            it is com.cukiti.teikafusion.model.GameEvent.KingSwordStrike ||
            it is com.cukiti.teikafusion.model.GameEvent.KingSwordSheathe ||
            it is com.cukiti.teikafusion.model.GameEvent.KingPieceRaise ||
            it is com.cukiti.teikafusion.model.GameEvent.KingPieceStrike ||
            it is com.cukiti.teikafusion.model.GameEvent.KingPieceRetreat ||
            it is com.cukiti.teikafusion.model.GameEvent.PlantHurt ||
            it is com.cukiti.teikafusion.model.GameEvent.AstroEmerge ||
            it is com.cukiti.teikafusion.model.GameEvent.AstroRetreat ||
            it is com.cukiti.teikafusion.model.GameEvent.AstroStrike ||
            it is com.cukiti.teikafusion.model.GameEvent.NinjaBoardSlash ||
            it is com.cukiti.teikafusion.model.GameEvent.NinjaBoardRepair ||
            // P-01: la Mansión Espectral no puede tener un castigo MUDO en las manos. Desde el Espejo
            // (P-246): el destello y el desvanecerse de los fantasmas vibran.
            it is com.cukiti.teikafusion.model.GameEvent.MirrorFlash ||
            it is com.cukiti.teikafusion.model.GameEvent.PhantomFade
        }
    }
    // Ids ya "hapticados". La lista de eventos de bioma contiene ahora eventos de LARGA VIDA (los de
    // VFX propio sobreviven hasta que su animación termina), así que no basta con reaccionar al
    // PRIMERO: se recorre la lista entera y cada evento vibra —y se consume— exactamente una vez.
    // Se poda con la propia lista → no crece sin límite durante la partida.
    val biomeHapticDone = remember { mutableSetOf<String>() }
    // La clave se construye una vez por cambio de la cola, no en cada recomposición de la pantalla
    // (antes era un `joinToString` que montaba una cadena nueva en cada pasada).
    val biomeFxIds = remember(biomeFxEvents) { biomeFxEvents.map { it.id } }
    LaunchedEffect(biomeFxIds) {
        for (ev in biomeFxEvents) {
            if (!biomeHapticDone.add(ev.id)) continue    // ya atendido en una pasada anterior
            when (ev) {
                is com.cukiti.teikafusion.model.GameEvent.CrystalCrack -> Haptics.biome(BiomeCue.CRYSTAL_CRACK)
                // Bosque del Sueño / Maite: pulso largo al entrar la Maite (te duerme una ficha).
                is com.cukiti.teikafusion.model.GameEvent.DrowsyOnset -> Haptics.biome(BiomeCue.DROWSY)
                // Volcán: la lava se come una fila (P-65 — antes no vibraba ni se veía nada).
                is com.cukiti.teikafusion.model.GameEvent.LavaSurge -> Haptics.biome(BiomeCue.LAVA_SURGE)
                // Glotón / Cocina Voraz: abre la boca en un rincón / cierra la dentellada.
                is com.cukiti.teikafusion.model.GameEvent.GluttonOpen -> Haptics.biome(BiomeCue.GLUTTON_OPEN)
                is com.cukiti.teikafusion.model.GameEvent.GluttonBite -> Haptics.biome(BiomeCue.GLUTTON_BITE)
                // Espacio Profundo · Meteoritos: asoma (aviso), impacto (golpe) o desvío (alivio).
                is com.cukiti.teikafusion.model.GameEvent.MeteorSpotted -> Haptics.biome(BiomeCue.METEOR_SPOTTED)
                is com.cukiti.teikafusion.model.GameEvent.MeteorImpact -> Haptics.biome(BiomeCue.METEOR_IMPACT)
                is com.cukiti.teikafusion.model.GameEvent.MeteorDeflect -> Haptics.biome(BiomeCue.METEOR_DEFLECT)
                // P-319: la roca se agrieta y contagia a un vecino (golpe seco, menor que el impacto).
                is com.cukiti.teikafusion.model.GameEvent.RockCrack -> Haptics.biome(BiomeCue.CRYSTAL_CRACK)
                // P-321: los bloques que robó la Luna aterrizan de vuelta.
                is com.cukiti.teikafusion.model.GameEvent.MoonReturn -> Haptics.biome(BiomeCue.ASTRO_RETREAT)
                // P-323: el oro del kintsugi cuaja en la costura.
                is com.cukiti.teikafusion.model.GameEvent.KintsugiScar -> Haptics.ninjaRepair()
                // P-324: la enredadera brota (golpe blando, como el daño a la planta).
                is com.cukiti.teikafusion.model.GameEvent.VineSprout -> Haptics.biome(BiomeCue.PLANT_HURT)
                // Tóxico: el veneno SALPICA a otra zona del tablero.
                is com.cukiti.teikafusion.model.GameEvent.PoisonSpread -> Haptics.biome(BiomeCue.TOXIC_SPLASH)
                // Trono Real · ESPADA DEL REY: la alza (aviso), cae (golpe) o la envaina (alivio).
                is com.cukiti.teikafusion.model.GameEvent.KingSwordRaise -> Haptics.biome(BiomeCue.KING_WARNING)
                is com.cukiti.teikafusion.model.GameEvent.KingSwordStrike -> Haptics.biome(BiomeCue.KING_STRIKE)
                is com.cukiti.teikafusion.model.GameEvent.KingSwordSheathe -> Haptics.biome(BiomeCue.KING_SHEATHE)
                // P-329 · la pieza entra (aviso), arrasa (golpe) o se retira (alivio).
                is com.cukiti.teikafusion.model.GameEvent.KingPieceRaise -> Haptics.biome(BiomeCue.KING_WARNING)
                is com.cukiti.teikafusion.model.GameEvent.KingPieceStrike -> Haptics.biome(BiomeCue.KING_STRIKE)
                is com.cukiti.teikafusion.model.GameEvent.KingPieceRetreat -> Haptics.biome(BiomeCue.KING_SHEATHE)
                // Cielo Estelar / Astro: emerge (amenaza), retira (alivio), disparo (golpe).
                is com.cukiti.teikafusion.model.GameEvent.AstroEmerge -> Haptics.biome(BiomeCue.ASTRO_EMERGE)
                is com.cukiti.teikafusion.model.GameEvent.AstroRetreat -> Haptics.biome(BiomeCue.ASTRO_RETREAT)
                is com.cukiti.teikafusion.model.GameEvent.AstroStrike -> Haptics.biome(BiomeCue.ASTRO_STRIKE)
                // Jardín / planta dañada: golpe seco al encajar el impacto de una línea rota.
                is com.cukiti.teikafusion.model.GameEvent.PlantHurt -> Haptics.biome(BiomeCue.PLANT_HURT)
                // Dojo / Corte Ninja: la vibración se retrasa al IMPACTO de la hoja / al ENCAJE de
                // las mitades (mismos instantes que sus ScreenShake) — en un launch hijo para no
                // retrasar la háptica de los demás eventos de esta pasada.
                is com.cukiti.teikafusion.model.GameEvent.NinjaBoardSlash ->
                    launch { delay(GameViewModel.NINJA_CUT_IMPACT_MS); Haptics.ninjaCut() }
                is com.cukiti.teikafusion.model.GameEvent.NinjaBoardRepair ->
                    launch { delay(GameViewModel.NINJA_REPAIR_SNAP_MS); Haptics.ninjaRepair() }
                // Mansión Espectral: la niebla es un escalofrío; el robo, un tirón que se hunde.
                is com.cukiti.teikafusion.model.GameEvent.MirrorFlash -> Haptics.mirrorFlash()
                is com.cukiti.teikafusion.model.GameEvent.PhantomFade -> Haptics.phantomFade()
                else -> {}
            }
            // Consumo: SOLO los que no tienen VFX propio. Los de [vfxOwnedBiomeEvent] los consume su
            // animación en GameBoard (onComplete) — tocarlos aquí los cortaría a un frame.
            if (!vfxOwnedBiomeEvent(ev)) viewModel.consumeEvent(ev.id)
        }
        biomeHapticDone.retainAll(biomeFxEvents.mapTo(HashSet()) { it.id })
    }

    // Detecta el cruce de umbral de 10K una sola vez por sesión (no usar booleano como key)
    val hasUnlockedScore10K = remember { mutableStateOf(false) }
    LaunchedEffect(gameState.score) {
        if (!hasUnlockedScore10K.value && gameState.score >= 10000) {
            hasUnlockedScore10K.value = true
        }
    }

    LaunchedEffect(gameState.status) {
        if (gameState.status == com.cukiti.teikafusion.model.GameStatus.LEVEL_COMPLETE ||
            gameState.status == com.cukiti.teikafusion.model.GameStatus.GAME_OVER ||
            gameState.status == com.cukiti.teikafusion.model.GameStatus.REVIVE_OFFER) {
            // 1500 ms: lo que dura el BARRIDO del tablero (dominó de 1,1 s) más aire para leerlo.
            delay(1500)
            showOverlay = true
        } else {
            showOverlay = false
        }
    }

    // Al arrancar el barrido del tablero (el jugador rechazó revivir) se oculta cualquier overlay
    // abierto para que se vea la destrucción. El GameOverOverlay reaparece por el efecto de status.
    //
    // El evento se CONSUME tras atenderlo. Antes no lo consumía nadie: se quedaba en la cola los 10 s
    // del recolector de huérfanos, y cada cambio de `gameState.events` re-ejecuta los ~45
    // `remember(gameState.events) { filterIsInstance… }` del tablero y de esta pantalla. Basura que se
    // paga en cada recomposición.
    // ── LOS CALLBACKS DEL TABLERO, ESTABLES (perf 2026-09-26) ──────────────────────────────────────
    // El de consumo leía `gameState`, así que era una lambda NUEVA en cada estado —y el estado cambia
    // con cada evento que entra o se consume—. `GameBoard` se la pasa a sus ~80 capas de VFX
    // envuelta en `{ id -> onEventConsumed?.invoke(id) }`, de modo que ninguna podía saltarse la
    // recomposición aunque su lista de eventos no hubiera cambiado. Con la lectura por
    // `rememberUpdatedState` la lambda es la misma instancia toda la partida y lee el estado vivo.
    val gameStateRef = rememberUpdatedState(gameState)
    val onBoardEventConsumed: (String) -> Unit = remember(viewModel) {
        { eventId ->
            val st = gameStateRef.value
            val isMegaTeika = st.events.any {
                it.id == eventId && it is com.cukiti.teikafusion.model.GameEvent.MegaTeikaFriendExpansion
            }
            viewModel.consumeEvent(eventId)
            if (isMegaTeika && st.status == com.cukiti.teikafusion.model.GameStatus.LEVEL_COMPLETE_PENDING) {
                viewModel.confirmLevelComplete()
            } else if (isMegaTeika) {
                // Mega formado SIN completar el nivel → el panel de objetivo se pone
                // ROJO y VIBRA (aviso en sitio, no intrusivo; una sola vez por nivel).
                viewModel.nudgeObjectives()
            }
        }
    }
    val onRevealWood: (Set<Position>, Boolean) -> Unit = remember(viewModel) {
        { cells, pop -> viewModel.revealHiddenWood(cells, pop) }
    }

    val boardWipeEvent = remember(gameState.events) {
        gameState.events.firstOrNull { it is com.cukiti.teikafusion.model.GameEvent.BoardWipe }
    }
    LaunchedEffect(boardWipeEvent?.id) {
        if (boardWipeEvent != null) {
            showOverlay = false
            viewModel.consumeEvent(boardWipeEvent.id)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                // B-2: guard para no disparar recomposición si la posición no cambió
                val newOffset = coords.localToWindow(Offset.Zero)
                if (newOffset != rootOffset) rootOffset = newOffset
                val newHeight = coords.size.height.toFloat()
                if (newHeight != rootHeightPx) rootHeightPx = newHeight
            }
    ) {
        val screenWidth = maxWidth
        // Tablero estilo Block Blast (ver res/raw/tableroblock.jpeg): NO ocupa el ancho completo —
        // deja un margen de aire a cada lado (16 dp, el mismo que ya usaba la bandeja de fichas).
        // Además del look de referencia, ese aire da sitio a que las mitades FLOTEN cuando el
        // Corte Ninja parte el tablero en dos (bioma Dojo en Sombras).
        val boardCols = gameState.gridW
        val boardRows = gameState.gridH
        val cellSize = (screenWidth - 32.dp) / boardCols
        val cellSizePx = with(density) { cellSize.toPx() }
        // Offset Y: la pieza flota por encima del dedo (recogida tipo Block Blast) para que el dedo
        // nunca tape la ficha ni la celda objetivo. Durante el arrastre el seguimiento es RÍGIDO
        // (1:1, sin inercia ni retraso) manteniendo siempre este margen.
        //
        // La elevación es PROPORCIONAL a la ficha, no fija: el borde INFERIOR de la ficha queda
        // siempre [FINGER_LIFT_CELLS] celdas por encima del dedo. Con los 248 dp fijos de antes una
        // ficha de 1 de alto se levantaba 5,6 celdas y para alcanzar la última fila el dedo tenía
        // que bajar por debajo del banner — en pantallas cortas las filas de abajo eran
        // literalmente inalcanzables.
        fun liftForPiece(piece: Piece): Float = (piece.height + FINGER_LIFT_CELLS) * cellSizePx
        // P-448: la misma cuenta, con el hueco según la altura del dedo (ver [liftGapCells]).
        fun liftForPieceAt(piece: Piece, fingerY: Float): Float =
            (piece.height + liftGapCells(fingerY, boardOffsetPx.y, trayRectWindow.top)) * cellSizePx

        // Teikoins ganados → moneda volando desde el tablero hasta la cartera. Vive aquí (y no
        // arriba) porque necesita el tamaño REAL de celda: antes asumía celdas de 33 dp y el
        // origen del vuelo caía lejos del centro del tablero. Coordenadas locales al root
        // (descontando rootOffset), como el resto de VFX de vuelo.
        LaunchedEffect(gameState.teikoins) {
            val gained = gameState.teikoins - prevTeikoins
            prevTeikoins = gameState.teikoins
            if (gained > 0 && coinCounterRect != Rect.Zero && boardOffsetPx != Offset.Zero) {
                val from = Offset(
                    boardOffsetPx.x + boardCols / 2f * cellSizePx - rootOffset.x,
                    boardOffsetPx.y + boardRows / 4f * cellSizePx - rootOffset.y
                )
                val to = Offset(
                    coinCounterRect.center.x - rootOffset.x,
                    coinCounterRect.center.y - rootOffset.y
                )
                flyingCoinEntries.add(FlyingCoinEntry(flyingCoinNextId++, from, to))
            }
        }

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = com.cukiti.teikafusion.ui.theme.AppBackground
        ) {
          // Controlador de grietas del cristal: cualquier toque (botones, bandeja, fondo) abre grieta.
          val crackController = remember { CrackController() }
          // Solo cuando el cristal es el bioma ACTUAL (niveles 5-9). Cristal es el PRIMER bioma, así que
          // `nextBiome == CRYSTAL` solo es cierto en niveles 1-4 e infinito (level 0), donde NO hay cristal
          // todavía → incluirlo hacía aparecer grietas falsas al tocar. Ver BiomeBackdrop.crystalTouchable.
          val crystalCrackActive = gameState.biomePrimary == com.cukiti.teikafusion.model.BiomeType.CRYSTAL
          Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // Observador GLOBAL: ve el down de cada gesto en la fase Initial y abre grieta
                    // SIN consumir, así botones/bandeja siguen funcionando con el mismo toque.
                    if (crystalCrackActive) Modifier.pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            crackController.crack(down.position)
                        }
                    } else Modifier
                )
          ) {
            // Fondo temático del bioma (velo translúcido) detrás de toda la UI de juego.
            BiomeBackdrop(
                primary = gameState.biomePrimary,
                next = com.cukiti.teikafusion.model.BiomeRegistry.nextBiome(gameState.level),
                transition = gameState.biomeTransition,
                modifier = Modifier.fillMaxSize(),
                crackController = crackController
            )

            // ── Marco arcoíris del combo: abraza el BORDE físico de la pantalla (edge-to-edge),
            //    enmarcando la cámara/notch. Va AQUÍ (sobre el fondo del bioma y DEBAJO de la
            //    Column: HUD, tablero, bandeja y botones) → el tablero opaco y los botones lo tapan,
            //    así NO pinta sobre la grilla (sin delineado) ni oculta información.
            //    Radio de esquina = el redondeo REAL del display (API 31+) → esquinas premium que
            //    calzan con la curva del móvil; fallback redondeado en versiones/equipos sin dato.
            val screenCornerPx = remember(view) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val insets = view.rootWindowInsets
                    val radii = intArrayOf(
                        RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT,
                        RoundedCorner.POSITION_BOTTOM_LEFT, RoundedCorner.POSITION_BOTTOM_RIGHT
                    ).map { insets?.getRoundedCorner(it)?.radius ?: 0 }
                    radii.max().toFloat()
                } else 0f
            }
            ComboBoardGlowVFX(
                comboStreak = gameState.comboStreak,
                screenCornerPx = screenCornerPx,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    // CAPA PROPIA (perf 2026-09-26). El fondo del bioma y el marco del combo, que van
                    // DEBAJO, se redibujan en cada fotograma; sin una capa aquí, cada uno de esos
                    // redibujos volvía a grabar también el HUD, la bandeja y todo lo que no tuviera
                    // capa propia (la regla de Compose: se regraba la capa ENTERA que contiene al que
                    // cambia). Sin alfa ni efectos, así que no hay búfer aparte: las mezclas de color
                    // de dentro siguen pintando sobre el fondo igual que antes.
                    .graphicsLayer()
                    .safeDrawingPadding()
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // Top Bar
                AdCooldownTicker(adQuota) { adQuotaLeft, adCooldownSec ->
                GameTopBar(
                    // P-90: las lecciones no califican, así que no enseñan marcador. Ver `showScore`.
                    showScore  = !tutorialLevel,
                    score      = gameState.score,
                    highScore  = gameState.highScore,
                    teikoins   = gameState.teikoins,
                    scoreScale = { scoreScale.value },
                    // Sin salidas laterales durante una lección pendiente: ver [tutorialLocked].
                    showExits  = !tutorialLocked,
                    onBack     = { viewModel.soundManager.playUiClick(); showQuitDialog = true },
                    onSettings = { viewModel.soundManager.playUiClick(); viewModel.pauseGame() },
                    onAdClick  = {
                        viewModel.soundManager.playUiClick()
                        // El cupo se comprueba AQUÍ además de en el botón: entre que la UI se
                        // dibuja y el dedo llega puede haberse agotado (otra pantalla, otro día).
                        if (AdManager.isReadyFlow.value && viewModel.canWatchCoinAd()) {
                            val activity = context as? Activity
                            if (activity != null) AdManager.showRewardedAd(
                                activity,
                                onRewarded = { coins ->
                                    viewModel.grantTeikoins(coins)
                                    // Gasta cupo SOLO si de verdad se ganó la recompensa: un
                                    // anuncio abandonado a los 2 s no puede costarle un uso.
                                    viewModel.consumeCoinAdQuota()
                                },
                                placement = "topbar_monedas"
                            )
                        } else AdManager.loadAd(context)
                    },
                    adQuotaLeft = adQuotaLeft,
                    cooldownLeftSec = adCooldownSec,
                    adRewardCoins = AdManager.rewardCoins,
                    onCoinCounterPositioned = { rect -> if (coinCounterRect != rect) coinCounterRect = rect },
                    onScoreCounterPositioned = { rect -> if (scoreCounterRect != rect) scoreCounterRect = rect }
                )
                }

                // (SALTAR TUTORIAL se dibuja más abajo, pegado al borde superior del tablero —
                //  ver el bloque marcado con el mismo título justo antes del tablero.)
                Spacer(modifier = Modifier.height(8.dp))

                // (El retrato/botón de habilidad se dibuja centrado en la parte BAJA del panel de
                //  fichas — ver "Área Inferior" más abajo. Aquí ya no ocupa una fila propia.)

                // P-89: durante las SIETE LECCIONES este bloque reserva SIEMPRE el mismo alto y
                // centra dentro lo que toque. Cada lección enseña una cosa (la 5 las estrellas, la 6
                // el medallón), así que sin esto el bloque se sustituía por otro de distinto alto al
                // pasar de una a otra y el tablero saltaba con la bandeja detrás. Ver
                // [tutorialHudSlotHeight].
                val tutorialHudSlot = tutorialHudSlotHeight()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (tutorialLevel) Modifier.heightIn(min = tutorialHudSlot)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                  Column(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalAlignment = Alignment.CenterHorizontally
                  ) {
                    // Objetivos compactos: icono arriba, cantidad debajo; estrellas y Mega comparten
                    // una sola fila estable. Los turnos siguen centrados sobre la barra de meta.
                    if (showObjectivePanel) {
                        GameInfoPanel(
                            levelObjective    = currentLevelObjective,
                            targetsCollected  = displayedTargets,
                            megaRequired      = currentMegaRequired,
                            megaFormed        = (gameState.teikasCreatedInLevel[
                                com.cukiti.teikafusion.model.TeikaType.MEGA_TEIKA
                            ] ?: 0) > 0,
                            megaPending       = gameState.megaPending,
                            nudgePulse        = gameState.objectiveNudgePulse,
                            objectivePresentationActive = objectivePresentationActive,
                            revealedTargetColors = revealedObjectiveColors,
                            megaObjectiveRevealed = megaObjectiveRevealed,
                            landingImpactToken = objectiveLandingImpactToken,
                            landingImpactColor = objectiveLandingColor,
                            landingImpactIsMega = objectiveLandingIsMega,
                            onCounterPositioned = { color, rect ->
                                if (targetCounterRects[color] != rect) {
                                    targetCounterRects = targetCounterRects + (color to rect)
                                }
                            },
                            onMegaPositioned = { rect ->
                                if (megaCounterRect != rect) megaCounterRect = rect
                            },
                            onPanelPositioned = { rect ->
                                if (objectivePanelRectWindow != rect) objectivePanelRectWindow = rect
                            }
                        )
                    }
                    // Barra de progreso hacia la meta de 3★ (estilo Block Blast Adventure) con el
                    // medallón de TURNOS encima, en su centro exacto.
                    if (currentLevelObjective != null && showGoalBar) {
                        Spacer(modifier = Modifier.height(6.dp))
                        ScoreGoalBar(
                            modifier = Modifier.onGloballyPositioned {
                                val r = it.boundsInWindow()
                                if (goalBarRectWindow != r) goalBarRectWindow = r
                            },
                            score      = gameState.score,
                            level      = gameState.level,
                            movesLeft  = gameState.movesLeft,
                            movesTotal = gameState.movesTotal,
                            companionId = selectedCompanionId,
                            companionLevel = selectedCompanionId
                                ?.let { companionUpgrades.levelOf(it) }
                                ?: 1,
                            showScoreGoal = !tutorialLevel,
                            onMovesPositioned = { rect ->
                                if (movesMedallionRectWindow != rect) movesMedallionRectWindow = rect
                            },
                        )
                    }
                  }
                }

                // ── SALTAR TUTORIAL (2026-09-01; rediseñado y recolocado el 2026-09-04) ─────────
                // **PEGADO AL BORDE SUPERIOR DEL TABLERO Y A LA DERECHA** (sitio marcado por el
                // usuario sobre una captura). Antes colgaba justo debajo de la barra superior y ahí
                // pisaba visualmente el chip del anuncio recompensado: dos pastillas oscuras casi
                // tocándose, y ninguna de las dos se leía. Aquí la banda está vacía en todas las
                // lecciones, comparte margen lateral con el tablero (16 dp, así que su borde derecho
                // cae justo sobre el del tablero) y no compite por el dedo con la zona de juego.
                //
                // Va DESPUÉS de la ranura de HUD y no dentro: la ranura tiene alto reservado y
                // contenido centrado (P-89), y meter aquí algo alineado a la derecha se le pondría
                // encima al panel de objetivos de la lección 5. Como el botón existe en las SIETE
                // lecciones, su alto es constante y el tablero sigue sin saltar entre una y otra.
                //
                // Desde que es la ÚNICA salida de una lección pendiente —el «❮», el engranaje y el
                // atrás del sistema están cerrados, ver [tutorialLocked]— tiene que leerse como un
                // botón de verdad: relieve, icono de «siguiente» y hundido de arcade al presionar.
                // El diseño vive en [TutorialSkipButton].
                if (tutorialLocked) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TutorialSkipButton(
                            onClick = {
                                viewModel.soundManager.playUiClick()
                                viewModel.skipTutorial()
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Tablero (con margen lateral estilo Block Blast, CENTRADO).
                // SIN clipToBounds: el grid ya se recorta solo (Box interno de GameBoard con
                // .clipToBounds), y los VFX del Box EXTERNO (FloatingScore, ComboText, Glotón) DEBEN
                // poder salir hacia arriba. Con el recorte aquí, el "+score" flotante se cortaba por la
                // mitad superior al subir (quedaba solo la mitad inferior). El ghost/selección dibujan
                // siempre dentro del tablero, así que no necesitan este recorte.
                // El Box interior mide EXACTAMENTE cellSize×8: las capas matchParentSize (ghost,
                // previews, selección de compañero) comparten origen (0,0) con el grid real.
                // Corte Ninja (Dojo): ninjaBoardSplit parte VISUALMENTE todo el conjunto (tablero +
                // previews + ghost) en dos mitades flotantes — con coste cero cuando no hay corte.
                val ninjaSplitProgress = rememberNinjaSplitProgress(gameState.ninjaCutActive)
                val ninjaSplitVisible by remember {
                    derivedStateOf { ninjaSplitProgress.value > 0.001f }
                }
                val ninjaFloatClock = rememberNinjaFloatClock(ninjaSplitVisible)
                // (RETIRADO 2026-08-27) La rotación del tablero entero (`viewModel.mortiTilt`) era
                // el «Tembleque» de Morti agitando la coctelera. Su habilidad es POSESIÓN, que mueve
                // bloques concretos y no toca el tablero como objeto.
                // DIFUMINADO del tablero con el panel de habilidades abierto: el jugador sigue
                // viendo su tablero (decide MIRÁNDOLO) pero fuera de foco, así el cajón manda.
                // `Modifier.blur` es API 31+ y el minSdk es 26 → por debajo se queda solo el scrim
                // del panel, que es el mismo recurso pero sin desenfoque real.
                // El difuminado SIGUE al gesto (no es un salto): se deriva del recorrido de la hoja.
                // Es lo mismo que hace `Modifier.blur` (borde rectangular: recorte + `Clamp`), pero
                // leído en la CAPA: el recorrido de la hoja cambia cada fotograma y `Modifier.blur`
                // necesitaba el radio en composición. El recorte solo se enciende mientras desenfoca,
                // porque los VFX del tablero (el «+score» que sube, el MEGA) dibujan por fuera.
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                  Box(
                      Modifier
                          .width(cellSize * boardCols)
                          .graphicsLayer {
                              val r = 4.dp.toPx() * boosterProgress.floatValue
                              if (Build.VERSION.SDK_INT >= 31 && r > 0f) {
                                  renderEffect = androidx.compose.ui.graphics.BlurEffect(
                                      r, r, androidx.compose.ui.graphics.TileMode.Clamp
                                  )
                                  clip = true
                              } else {
                                  renderEffect = null
                                  clip = false
                              }
                          }
                          .ninjaBoardSplit(
                              progress = { ninjaSplitProgress.value },
                              clock = { ninjaFloatClock.value }
                          )
                  ) {
                    GameBoard(
                        gameState = gameState,
                        teikaBodies = viewModel.teikaBodies,
                        energizedTeikaIds = viewModel.energizedTeikaIds,
                        magmaHeat = viewModel.magmaHeat,
                        tutorialStageReady = tutorialStageReady,
                        onBoardPositioned = { offset, _ ->
                            if (boardOffsetPx != offset) boardOffsetPx = offset
                        },
                        onRevealWood = onRevealWood,
                        onEventConsumed = onBoardEventConsumed,
                        onCoilTap = { pos -> viewModel.onBoardTap(pos) },
                        clawAim = viewModel.clawAim.collectAsState().value,
                        clawPlan = viewModel.previewClaw(),
                        onClawSelect = viewModel::selectClawBody,
                        onClawTarget = viewModel::aimClaw,
                        ballAim = viewModel.ballAim.collectAsState().value,
                        ballPlan = viewModel.previewBall(),
                        onBallAnchor = viewModel::selectBallAnchor,
                        onBallAim = viewModel::aimBall,
                        forgeAim = viewModel.forgeAim.collectAsState().value,
                        forgePlan = viewModel.previewForge(),
                        onForgeSelect = viewModel::selectForgeCell,
                        reactorAim = viewModel.reactorAim.collectAsState().value,
                        reactorPlan = viewModel.previewReactor(),
                        onReactorSelect = viewModel::selectReactorGroup,
                        turbineAim = viewModel.turbineAim.collectAsState().value,
                        onTurbineAnchor = viewModel::selectTurbineAnchor,
                        onTurbineAim = viewModel::aimTurbine,
                        orbitAim = viewModel.orbitAim.collectAsState().value,
                        orbitPlan = viewModel.previewOrbit(),
                        onOrbitSelect = viewModel::selectOrbitZone,
                        portalSwapAim = viewModel.portalSwapAim.collectAsState().value,
                        portalSwapPlan = viewModel.previewPortalSwap(),
                        onPortalSwapSelect = viewModel::selectPortalSwapZone,
                        compactorAiming = viewModel.compactorAiming.collectAsState().value,
                        onCompactorPreview = viewModel::previewCompactor,
                        onCompactorCancel = viewModel::cancelCompactorAim,
                        onCompactorRelease = { x,y,d -> viewModel.releaseCompactor(x,y,d) },
                        onPinballBegin = viewModel::beginPinballAim,
                        onPinballCancel = viewModel::cancelPinballAim,
                        onPinballRelease = viewModel::releasePinball,
                        cellSize = cellSize,
                        comboStreak = gameState.comboStreak,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // (2026-08-08g) LA CATAPULTA ya no vive aquí. Estaba montada con
                    // `matchParentSize()` sobre el Box del tablero, así que sus límites terminaban en
                    // el borde inferior del pozo — y la horquilla se dibuja POR DEBAJO. Dibujar fuera
                    // de los límites funciona; recibir toques, no: el radio de agarre caía entero
                    // fuera y ningún toque llegaba nunca al gesto. Ahora vive en la FRANJA DE TIRO
                    // (más abajo, junto a la bandeja), donde la horquilla sí está dentro de sus
                    // propios límites, y pinta la guía sobre el pozo con `y` negativa.
                    // Agrandar este Box no era opción: hay una decena de capas con `matchParentSize`
                    // colgando de él y todas se habrían estirado.

                    // (ERRADICADO 2026-07-26) Aquí vivía el FILO dorado de la ¡FIEBRE! del Infinito.
                    // La Fiebre se retiró con las fichas especiales, así que el bloque se quedó
                    // vacío: un `if (isInfiniteMode) { }` que no dibujaba nada.

                    // Celdas CERRADAS que ya no son visibles/interactuables: lava del Volcán (filas
                    // superiores [0,lavaRow)) + mordisco del Glotón. El aviso de rotura se recorta a la
                    // zona todavía visible → el jugador ve que el tablero se cierra pero puede romper.
                    val previewCoveredCells = remember(
                        gameState.lavaRow, gameState.lavaActive, gameState.gluttonDeadCells
                    ) {
                        buildSet {
                            if (gameState.lavaActive && gameState.lavaRow > 0) {
                                val top = gameState.lavaRow.coerceAtMost(boardRows)
                                for (yy in 0 until top) for (xx in 0 until boardCols) add(Position(xx, yy))
                            }
                            addAll(gameState.gluttonDeadCells.keys)
                        }
                    }
                    // Celdas con información propia (cofre-estrella, cristal, comodín, hielo) y
                    // celdas ocupadas por CUERPOS de Teika: el PINTADO SÓLIDO del preview no las
                    // recolorea — conservan su lectura. Los cuerpos en particular: esta capa se
                    // dibuja ENCIMA de ellos y un bloque opaco TAPABA al Teika que completaba la
                    // fila como comodín (bugfix 2026-07-20). Se recalcula con cada paso del drag
                    // (dragState) porque los cuerpos ruedan mientras arrastras.
                    val previewSpecialCells = remember(
                        gameState.grid, gameState.frozenCells, gameState.glacierFrostCells, dragState
                    ) {
                        buildSet {
                            gameState.grid.forEach { (p, c) ->
                                val w = c as? com.cukiti.teikafusion.model.CellContent.Wood
                                    ?: return@forEach
                                // Comodín arcoíris y bobina numerada: su DIBUJO es la información
                                // (el color del comodín significa "vale para cualquiera"; el número
                                // de la bobina es el puzzle). Recolorearlos sería mentir.
                                if (w.colorType == WILDCARD_COLOR || w.coil > 0) add(p)
                            }
                            addAll(gameState.frozenCells)
                            addAll(gameState.glacierFrostCells)
                            addAll(viewModel.teikaOccupiedCellsForUi())
                        }
                    }
                    // (Cristal, cronómetro, veneno y gema de objetivo NO necesitan tratamiento
                    //  especial: el aviso de rotura tiñe de forma translúcida en vez de repintar el
                    //  bloque, así que se siguen viendo a través. Ver LineClearPreviewVFX.)
                    // Corte Ninja: la rotura por MITADES ya viene resuelta del VM — cada fila del
                    // preview trae su TRAMO exacto (clearRowSpans), así que aquí no se adivina nada
                    // (la máscara por la X del ghost encendía la mitad equivocada — bugtablero).
                    Box(Modifier.matchParentSize()) {
                        run {
                            LineClearPreviewLayer(
                                dragState = dragState,
                                comboStreak = gameState.comboStreak,
                                cellSizePx = cellSizePx,
                                coveredCells = previewCoveredCells,
                                specialCells = previewSpecialCells,
                                modifier = Modifier.matchParentSize()
                            )

                            RelayDragPreview(gameState,dragState,cellSizePx,viewModel.teikaOccupiedCellsForUi(),Modifier.matchParentSize())
                            // TORMENTA/VOLCÁN: filas que se ENERGIZARÍAN en circuito (no rompen).
                            CircuitEnergizePreviewLayer(
                                dragState = dragState,
                                cellSizePx = cellSizePx,
                                coveredCells = previewCoveredCells,
                                modifier = Modifier.matchParentSize()
                            )
                        }
                        // P-244 / P-240: qué vidrios agrietaría esta ficha (y qué se llevaría el
                        // estallido), y a qué Teikas alcanzaría el imán si lo sueltas aquí.
                        HouseRuleDragPreviewLayer(
                            dragState = dragState,
                            glass = gameState.glassCells,
                            cracked = gameState.glassCracked,
                            magnetRange = com.cukiti.teikafusion.model.MagnetPiece.rangeFor(gameState.level),
                            bodiesFlow = viewModel.teikaBodies,
                            cellSizePx = cellSizePx,
                            modifier = Modifier.matchParentSize(),
                            grid = gameState.grid,
                            barrelsActive = com.cukiti.teikafusion.model.ChainReaction.appliesTo(gameState.level),
                            egg = gameState.eggCell,
                            eggCracks = gameState.eggCracks,
                            eggNeeded = com.cukiti.teikafusion.model.TeikaEgg.cracksFor(gameState.level),
                            eggHatchType = com.cukiti.teikafusion.model.TeikaEgg.hatchTypeFor(gameState.level),
                            wheelTurnsNow = gameState.hasWheel && com.cukiti.teikafusion.model.BoardCurrent
                                .turnsUntil(gameState.placementsInLevel, gameState.level) == 1 &&
                                !gameState.ninjaCutActive,
                            wheelDirection = com.cukiti.teikafusion.model.BoardCurrent
                                .directionFor(gameState.level, gameState.placementsInLevel + 1),
                            cols = boardCols,
                            rows = boardRows,
                            recoilSpeed = com.cukiti.teikafusion.model.LineRecoil.speedFor(gameState.level),
                            pinata = gameState.pinataCell,
                            pinataHits = gameState.pinataHits,
                            pinataNeeded = com.cukiti.teikafusion.model.Pinata.hitsFor(gameState.level),
                            moldCells = gameState.moldCells,
                            portals = com.cukiti.teikafusion.model.Portals.rowsFor(gameState.level, gameState.placementsInLevel),
                        )
                        GhostPreviewLayer(
                            dragState = dragState,
                            cellSizePx = cellSizePx,
                            boardCols = boardCols,
                            gridRows = boardRows,
                            modifier = Modifier.matchParentSize()
                        )
                        // Flora / Cañón Floral: modo puntería — arrastra para apuntar, suelta para disparar.
                        val floraAim by viewModel.floraAim.collectAsState()
                        floraAim?.let { aim ->
                            FloraAimLayer(
                                aim = aim,
                                cellPx = cellSizePx,
                                onLaunch = { dx, dy, cx -> viewModel.launchFloraTeika(dx, dy, cx) },
                                modifier = Modifier.matchParentSize()
                            )
                        }
                        // ── REJILLA ACCESIBLE (P-13) ──────────────────────────────────────────
                        // Con una ficha seleccionada por el lector de pantalla, cada casilla se
                        // convierte en un botón que dice si la ficha cabe ahí y la coloca al doble
                        // toque. Solo existe con exploración táctil ENCENDIDA y con ficha elegida:
                        // el resto del tiempo no hay ni un nodo de más.
                        if (touchExploration && a11ySelectedPieceId != null) {
                            A11yBoardGrid(
                                rows = boardRows,
                                cols = boardCols,
                                canPlaceAt = { x, y -> viewModel.a11yCanPlaceSelectedAt(x, y) },
                                onPlaceAt = { x, y -> viewModel.a11yPlaceSelectedAt(x, y) },
                                modifier = Modifier.matchParentSize()
                            )
                        }
                    }

                  }
                }

                // Separación tablero ↔ panel de fichas. Sube a 24 dp (2026-07-25): quitado el botón
                // de habilidad sobra sitio abajo, y con 8 dp las fichas quedaban tan pegadas al
                // tablero que al arrastrar desde la bandeja el dedo ya tapaba la primera fila. El
                // espacio flexible restante se sigue acumulando ABAJO (ver el weight tras la bandeja).
                Spacer(modifier = Modifier.height(24.dp))

                // Área Inferior: Piezas (Sin Hold)
                // C-3: referencia estable para pointerInput(Unit) — no cancela el gesto al cambiar piezas
                val availablePiecesRef = rememberUpdatedState(gameState.availablePieces)
                val gameStatusRef      = rememberUpdatedState(gameState.status)
                // Segunda red de la ficha en la mano (auditoría 2026-10-02): si la que llevas ya no está
                // en la bandeja, o la partida deja de estar en juego, no puede quedarse flotando
                // encima de todo. (La primera red es el `finally` del gesto, más abajo.)
                LaunchedEffect(draggedPieceLocal?.id, gameState.availablePieces, gameState.status) {
                    val held = draggedPieceLocal ?: return@LaunchedEffect
                    val gone = gameState.availablePieces.none { it.id == held.id }
                    if (gone || gameState.status != com.cukiti.teikafusion.model.GameStatus.PLAYING) {
                        dragAnimJob?.cancel()
                        dragAnimJob = null
                        viewModel.onDragCancel()
                        draggedPieceLocal = null
                    }
                }
                // Mientras el tablero resuelve el turno los mandos están MUDOS (ver
                // GameViewModel.boardBusy). Ref estable porque se lee dentro de pointerInput(Unit).
                val boardBusy by viewModel.boardBusy.collectAsState()
                val boardBusyRef       = rememberUpdatedState(boardBusy)
                // ── TRES CUADROS FIJOS (2026-08-26) ─────────────────────────────────────────────
                // La bandeja repartía el ancho entre las fichas que QUEDABAN, así que colocar una
                // hacía crecer y moverse a las otras dos en mitad del turno, y como el modelo borra
                // la usada de una lista, las de detrás se corrían un puesto. Ahora los huecos son
                // tres, siempre, y cada ficha se queda en el suyo hasta que se juega (ver
                // [rememberTraySlots]). El hueco vacío se queda ahí, apagado: es el contador de
                // «cuántas te faltan por colocar».
                // ── P-75 · BANDEJA DE UNA, CON HORIZONTE ────────────────────────────────────────
                // Uno de cada 11 niveles reparte UNA ficha y enseña las tres siguientes. La banda es
                // la misma: el cuadro jugable se queda con ~42 % del ancho y las tres miniaturas se
                // reparten el resto. Nada de robarle alto al tablero por enseñar el futuro.
                val horizonMode  = gameState.hasHorizonTray
                // Cuántas fichas del futuro enseña ESTE nivel: es el dial de capítulo de la regla
                // (2026-09-03) — tres en el primer bioma, una en el último. La banda reserva sitio
                // para las que de verdad va a pintar, o el cuadro jugable quedaría descentrado.
                val horizonCount = if (horizonMode)
                    com.cukiti.teikafusion.model.HorizonTray.horizonFor(gameState.level) else 0
                val horizonQueue = gameState.pieceHorizon
                // (2026-09-03) Los cuadros ya no son «uno o tres»: `trayCapacity` es la fuente
                // única y cubre también la BANDEJA DE CINCO (P-142), que reparte cinco cuadros en
                // los primeros biomas y cuatro en los últimos.
                val trayCapacity = gameState.trayCapacity
                val reserveReplacement=eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.RulePulse>()
                    .firstOrNull { it.kind=="reserve_swap" }?.let { ev -> ev.originalPiece?.id?.let { old -> ev.activePieceId?.let { old to it } } }
                val traySlots    = rememberTraySlots(
                    gameState.availablePieces, gameState.level, slotCount = trayCapacity, replacement = reserveReplacement
                )
                // Ref estable del reparto por huecos: se lee DENTRO de `pointerInput(Unit)`, cuya
                // corrutina no debe cancelarse al cambiar la bandeja (C-3).
                val traySlotsRef = rememberUpdatedState(traySlots)
                // Las fichas que este nivel no deja levantar: las que la COLA aún no ofrece (P-143)
                // y las que vienen TAPADAS (P-145). Se calcula aquí, junto al reparto por huecos, y
                // viaja al gesto por `rememberUpdatedState` porque `pointerInput(Unit)` no se
                // recompone nunca: capturarlo por valor dejaría el agarre calibrado para la bandeja
                // anterior (la lección de C-3).
                val blockedTrayIds = remember(traySlots, gameState.availablePieces, gameState.level) {
                    buildSet {
                        traySlots.forEachIndexed { idx, p ->
                            if (p == null) return@forEachIndexed
                            val queueIdx = gameState.availablePieces.indexOfFirst { it.id == p.id }
                            val locked = !com.cukiti.teikafusion.model.ForcedQueue
                                .allows(gameState.level, queueIdx)
                            val hidden = com.cukiti.teikafusion.model.FaceDownTray.isHidden(
                                level = gameState.level,
                                index = idx,
                                trayCount = gameState.trayCapacity,
                                remaining = gameState.availablePieces.size,
                            )
                            if (locked || hidden) add(p.id)
                        }
                    }
                }
                val blockedTrayIdsRef = rememberUpdatedState(blockedTrayIds)
                // P-232 · FICHAS QUE SE FUNDEN: el cuadro sobre el que está pasando una gemela y si se
                // fundirían (verde) o no cabrían (rojo). Lo escribe el gesto; lo pinta el cuadro.
                var fuseHover by remember(gameState.level) {
                    mutableStateOf<Pair<String, com.cukiti.teikafusion.model.FuseCheck>?>(null)
                }
                val fuseRuleRef = rememberUpdatedState(
                    com.cukiti.teikafusion.model.PieceFusion.appliesTo(gameState.level)
                )
                // La ficha recién fundida late en su cuadro. ÚNICA recogida del evento.
                val fusedEvent = eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.PiecesFused>().firstOrNull()
                val trayPieces   = gameState.availablePieces
                val numSlots     = trayCapacity
                // Ref estable para el gesto (`pointerInput(Unit)`, que no se recompone).
                val horizonModeRef = rememberUpdatedState(horizonMode)
                // ── LA BANDEJA NO PARPADEA (2026-08-24) ─────────────────────────────────────────
                // Cada hueco nace con `alpha = 0` y entra con un muelle (`entrance`, más abajo). Eso
                // está bien para una ficha NUEVA, pero el estado vive DENTRO del `key(piece.id)`: si
                // el hueco se recompone por cualquier motivo con la MISMA ficha, la animación vuelve
                // a empezar desde invisible y el jugador ve la bandeja **desaparecer y volver con las
                // mismas fichas** — el síntoma reportado, que en el playtest coincidió con una fusión
                // T2+T2→T3.
                //
                // Este registro vive en la BANDEJA, así que sobrevive a que un hueco se destruya y se
                // vuelva a crear: una ficha que ya hizo su entrada aparece entera, sin animación.
                // Mismo patrón que el tablero, que lleva su set de ids ya animados en GameBoard.
                val trayEntered = remember(gameState.level) { mutableSetOf<String>() }
                // Poda: solo se recuerdan las fichas que siguen en la bandeja (los ids son UUID, así
                // que uno que se va no vuelve). Va en un efecto para no mutar durante la composición.
                LaunchedEffect(trayPieces) {
                    trayEntered.retainAll(trayPieces.mapTo(mutableSetOf()) { it.id })
                }
                // ── EL PREMIO DE LA BANDEJA (2026-09-26) ────────────────────────────────────────
                // Consume `TrayRewardDealt` (y es el ÚNICO sitio que lo hace): la bandeja que llega
                // pensada para colocarse —porque una jugada rompió 2+ líneas— brilla en oro y suena.
                // Un premio que no se ve no enseña a jugar mejor. El brillo se lee en el DIBUJO de
                // cada cuadro (`drawWithContent`), no en la composición.
                val rewardEvent = eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.TrayRewardDealt>().firstOrNull()
                var rewardIds by remember(gameState.level) { mutableStateOf<Set<String>>(emptySet()) }
                val rewardGlow = remember { androidx.compose.animation.core.Animatable(0f) }
                LaunchedEffect(rewardEvent?.id) {
                    val ev = rewardEvent ?: return@LaunchedEffect
                    viewModel.consumeEvent(ev.id)
                    rewardIds = ev.pieceIds.toSet()
                    viewModel.soundManager.playTrayReward()
                    rewardGlow.snapTo(1f)
                    rewardGlow.animateTo(0f, androidx.compose.animation.core.tween(TRAY_REWARD_GLOW_MS))
                    rewardIds = emptySet()
                }
                // Ancho REAL del slot: la fila interna separa las fichas con `spacedBy(TRAY_GAP)`,
                // así que ese aire hay que descontarlo antes de repartir. Sin esto el slot se
                // sobreestimaba y las fichas anchas se dimensionaban contra un hueco que no existe.
                // El aire entre cuadros sube a 10 dp: con 6 los tres marcos se leían como una sola
                // barra: la separación ES el mensaje («son tres fichas, no una fila de bloques»).
                val trayGap      = 10.dp
                // Pesos de la fila. Sin horizonte son tres cuadros iguales; con horizonte, un cuadro
                // jugable grande y tres miniaturas — y estos números son los MISMOS que usa el
                // gesto para saber qué se está agarrando (ver `playableFraction`), porque el reparto
                // real lo hace el `weight` del Row y adivinarlo por otro lado es cómo se desalinea
                // el agarre del dibujo.
                val playWeight    = if (horizonMode) 1.5f else 1f
                val horizonWeight = 0.7f
                val reserveMode = com.cukiti.teikafusion.model.ReserveRules.appliesTo(gameState.level)
                val reserveModeRef = rememberUpdatedState(reserveMode)
                val reserveWeight = if(reserveMode) .7f else 0f
                val trayBoxes     = numSlots + horizonCount + if(reserveMode) 1 else 0
                val totalWeight   = playWeight * numSlots + horizonWeight * horizonCount + reserveWeight
                val trayUsable   = screenWidth - 32.dp - trayGap * (trayBoxes - 1)
                val slotWidth    = trayUsable * (playWeight / totalWeight)
                val horizonSlotWidth = trayUsable * (horizonWeight / totalWeight)
                // Los dos valores que el GESTO necesita para saber dónde acaba el cuadro jugable.
                // Van por `rememberUpdatedState` porque `pointerInput(Unit)` no se recompone nunca:
                // capturarlos por valor dejaría el agarre calibrado para el nivel anterior.
                val playRatioRef = rememberUpdatedState(playWeight / totalWeight)
                val trayGapsRef  = rememberUpdatedState(trayGap * (trayBoxes - 1))
                // (2026-07-25) Ya no se reserva BANDA para el botón de habilidad.
                //
                // Hueco CUADRADO (2026-08-26): antes eran `cellSize * 4` de alto —unos 165 dp contra
                // 105 de ancho— y ese alto no lo aprovechaba nadie, porque el ancho ya era el límite
                // que decidía el tamaño de toda ficha de 3+ columnas. Un cuadrado dibuja el marco
                // premium sin encoger ni una ficha y devuelve ~60 dp de aire a la pantalla.
                // El ALTO de la banda no depende del modo: sale de los tres cuadros de siempre. Con
                // horizonte el cuadro jugable es más ancho, y dejar que el alto lo siguiera habría
                // subido la bandeja ~20 dp — o sea, le habría quitado sitio al tablero en 26 niveles
                // por enseñar el futuro. La banda mide lo mismo siempre; lo que cambia es el reparto.
                val slotHeight = ((screenWidth - 32.dp - trayGap * 2) / 3).coerceIn(84.dp, 132.dp)

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Mientras el tablero actúa, la bandeja se APAGA un punto. Bloquear el toque sin
                    // decirlo se lee como "se ha colgado"; con la bandeja atenuada se lee como
                    // "espera, que esto todavía está pasando".
                    val trayAlpha by androidx.compose.animation.core.animateFloatAsState(
                        // 0,55 → 0,80 (2026-08-26c). El mensaje «espera, esto todavia esta pasando»
                        // se sigue leyendo, pero deja de MULTIPLICARSE con lo que se dibuje encima:
                        // con el scrim del combo epico (hasta 0,60 de negro en la orilla de abajo) la
                        // bandeja se quedaba en torno al 22 % y el jugador la daba por desaparecida.
                        // Ninguna atenuacion de estado deberia depender de que nadie mas pinte encima.
                        targetValue = if (boardBusy) 0.80f else 1f,
                        animationSpec = androidx.compose.animation.core.tween(160),
                        label = "trayBusy"
                    )
                    // Cola de piezas (Bottom Area - Ancho completo, 3 slots)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(slotHeight)
                            .graphicsLayer { alpha = trayAlpha }
                        .onGloballyPositioned {
                            trayOffsetPx   = it.localToWindow(Offset.Zero)
                            trayRectWindow = it.boundsInWindow()
                        }
                        .pointerInput(Unit) {
                            // C-3: clave Unit — la corrutina de gesto NUNCA se cancela al cambiar piezas.
                            // Los valores actuales se leen de las refs estables dentro del gesto.
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                if (gameStatusRef.value != com.cukiti.teikafusion.model.GameStatus.PLAYING) return@awaitEachGesture
                                // No se agarra ficha mientras el tablero resuelve el turno: la fila
                                // rompiéndose y su cadena son UNA función con principio y final.
                                // Colocar encima la atropellaba (el motor encadenaba una cadena
                                // nueva sobre la anterior a medio animar).
                                if (boardBusyRef.value) return@awaitEachGesture
                                val pieces = availablePiecesRef.value
                                if (pieces.isEmpty()) return@awaitEachGesture

                                // Touch down local to this Box, convert to global
                                val globalTouch = trayOffsetPx + down.position

                                // La ficha se elige por RANURA, no por la ficha con el centro más
                                // cercano. Dos motivos:
                                //   • antes, un toque en cualquier punto muerto del panel levantaba
                                //     la ficha más próxima aunque estuviera a media pantalla;
                                //   • los centros venían de `onGloballyPositioned`, que se mide en
                                //     la fase de LAYOUT: mientras corre la animación de entrada
                                //     (escala 0,55→1 + traslación) la ficha se VE en otro sitio del
                                //     que dice su centro, y el agarre no coincidía con el dibujo.
                                // La ranura es geometría pura del Row (anchos iguales + `trayGap`),
                                // inmune a cualquier transformación de dibujo. `size` es el tamaño
                                // ACTUAL del panel, no un valor capturado.
                                // La ranura se calcula sobre los TRES huecos fijos, no sobre las
                                // fichas que queden: desde 2026-08-26 la fila no se re-reparte al
                                // colocar, así que dividir por `pieces.size` apuntaría a un hueco
                                // que ya no está donde el dedo lo ve. Un hueco vacío devuelve null y
                                // el gesto se cae solo.
                                // P-75: con horizonte solo se agarra el PRIMER cuadro. Las tres
                                // miniaturas de la derecha son informacion, no fichas: tocarlas no
                                // levanta nada (y no se roba la ficha jugable desde el otro lado de
                                // la banda, que se leeria como que la ficha "salta" al dedo).
                                val slotIndex = if (horizonModeRef.value) {
                                    val playPx =
                                        (size.width - trayGapsRef.value.toPx()) * playRatioRef.value
                                    if (down.position.x <= playPx) 0 else -1
                                } else if(reserveModeRef.value) {
                                    com.cukiti.teikafusion.model.ReserveRules.slotIndex(down.position.x,size.width.toFloat(),trayGap.toPx(),playRatioRef.value,traySlotsRef.value.size)
                                } else {
                                    // El paso sale de los cuadros REALES de este nivel: con la
                                    // bandeja de cinco, dividir entre tres desalineaba el agarre
                                    // media ficha (el mismo fallo que documenta `playableFraction`).
                                    val slots = traySlotsRef.value.size.coerceAtLeast(1)
                                    val slotStride = (size.width + trayGap.toPx()) / slots
                                    if (slotStride <= 0f) 0
                                    else (down.position.x / slotStride).toInt().coerceIn(0, slots - 1)
                                }
                                val closestPiece: Piece? =
                                    if (slotIndex < 0) null
                                    else traySlotsRef.value.getOrNull(slotIndex)
                                        // P-143 y P-145: lo que la cola no deja jugar todavía, y lo
                                        // que viene tapado, no se levantan. El motor ya los rechaza,
                                        // pero dejar que el dedo los arrastre y luego no pasara nada
                                        // sería peor que no responder: parecería que el juego falla.
                                        ?.takeIf { it.id !in blockedTrayIdsRef.value }

                                if (closestPiece != null) {
                                    val piece = closestPiece // smart cast local — elimina todos los !!
                                    down.consume()
                                    if (piece.isFichona) Haptics.fichonaLift() else Haptics.tap()
                                    fichonaTilt.floatValue = 0f
                                    draggedPieceLocal = piece
                                    returnScale.floatValue = 1f
                                    returnActive = false
                                    // (Auditoría 2026-10-02, captura del usuario: una ficha se quedó
                                    // «levantada», a tamaño de tablero, encima de su cuadro.) Si la
                                    // corrutina del gesto se cancela A MITAD del arrastre —aparece una
                                    // ventana encima, el nodo se desmonta…— ninguna rama de «soltar» de
                                    // abajo llega a correr y `draggedPieceLocal` se quedaba puesto para
                                    // siempre. El `finally` la devuelve a su cuadro pase lo que pase.
                                    var returningToTray = false
                                    try {
                                    pieceOriginalPos = pieceTopLefts[piece.id] ?: Offset.Zero

                                    // Elevación PROPORCIONAL: el borde inferior de la ficha queda
                                    // siempre a FINGER_LIFT_CELLS celdas del dedo (ver liftForPiece).
                                    // Sigue siendo `var` por herencia de P-15 (la ficha podía ROTAR
                                    // sin soltarse). El giro volvió en 2026-09-24 (P-276) pero solo
                                    // por el toque seco en la bandeja: dentro del arrastre no cambia.
                                    var draggedPiece: Piece = piece
                                    var pieceWidthPx = piece.width * cellSizePx
                                    var liftPx = liftForPiece(piece)
                                    var currentFingerPos = globalTouch

                                    val elevatedPos = Offset(
                                        currentFingerPos.x - (pieceWidthPx / 2f),
                                        currentFingerPos.y - liftPx
                                    )

                                    dragAnimJob?.cancel()
                                    dragPos.value = pieceOriginalPos
                                    dragAnimJob = coroutineScope.launch {
                                        // Salto a la mano: sale de su hueco en la bandeja y sube al dedo.
                                        // Muelle RÁPIDO (antes 500) — con el muelle lento la ficha llegaba
                                        // tarde a la mano y arrancaba el arrastre por detrás del dedo.
                                        animate(
                                            typeConverter = Offset.VectorConverter,
                                            initialValue = pieceOriginalPos,
                                            targetValue = elevatedPos,
                                            // 900 → 1600 y algo más amortiguado: el salto a la mano
                                            // dura ~la mitad. La ficha ya está bajo control del dedo
                                            // cuando el jugador empieza a moverla, en vez de ir
                                            // alcanzándolo durante el primer tramo del arrastre.
                                            animationSpec = if (piece.isFichona) tween(220, easing = FastOutSlowInEasing) else spring(dampingRatio = 0.82f, stiffness = 1600f)
                                        ) { value, _ -> dragPos.value = value }
                                    }
                                    grabScaleJob?.cancel()
                                    grabScaleJob = coroutineScope.launch {
                                        // Aumento de escala INMEDIATO: en el instante exacto del toque la
                                        // ficha salta a tamaño-tablero (con un pelín de overshoot) y se
                                        // asienta. Feedback claro de "activa y lista" sin crecer poco a poco.
                                        grabScale.snapTo(if (piece.isFichona) 0.94f else 1.16f)
                                        grabScale.animateTo(1f, spring(dampingRatio = if (piece.isFichona) 0.92f else 0.6f, stiffness = if (piece.isFichona) 220f else 420f))
                                    }
                                    viewModel.onDragStart(piece)

                                    // Continúa arrastrando
                                    var hasMoved = false
                                    var explicitRelease = false
                                    var lastHeavyFeedback = 0L
                                    val startPos = globalTouch
                                    // Última celda que se le pasó al ViewModel. El seguimiento VISUAL
                                    // es 1:1 (se escribe `dragPos` en cada muestra del dedo), pero el
                                    // cálculo del ghost —aterrizaje del pozo, validez, preview de
                                    // rotura— solo depende de la CELDA, así que repetirlo en cada
                                    // muestra táctil (hasta 120/s) era trabajo tirado en el mismo
                                    // hilo que mueve la ficha: eso es lo que se sentía como pesadez.
                                    var lastGhostCell: Position? = null
                                    // ── Bucle de arrastre (manual, no `drag(id)`) ────────────────
                                    // Se recorría el evento entero en vez de solo el puntero
                                    // arrastrado para ver el SEGUNDO DEDO: un toque con otro dedo
                                    // mientras arrastrabas ROTABA la ficha en la mano (P-15). Esa
                                    // puerta no volvió con el giro (P-276, 2026-09-24): se gira con
                                    // un toque seco en la bandeja. El bucle sigue siendo manual —el
                                    // resto del gesto depende de él— pero no mira los dedos de más.
                                    var dragging = true
                                    while (dragging) {
                                        val ev = awaitPointerEvent()
                                        val change = ev.changes.firstOrNull { it.id == down.id }
                                        // Dedo levantado (o cancelado) → fin del arrastre.
                                        if (change == null || !change.pressed) { explicitRelease = change != null; dragging = false; continue }
                                        change.consume()
                                        if (draggedPieceLocal != null) {
                                            // Seguimiento RÍGIDO 1:1: la ficha se mantiene a una
                                            // distancia FIJA por encima del dedo y lo sigue sin inercia,
                                            // arrastre ni retraso. Solo se limita por arriba para no
                                            // salirse del tablero.
                                            val currentFingerPos = trayOffsetPx + change.position
                                            // Umbral de "ya es arrastre, no un tap" de 10 a 5 px: el
                                            // dedo entra en modo colocación antes y la ficha responde
                                            // desde el primer milímetro.
                                            if ((currentFingerPos - startPos).getDistance() > 5f) {
                                                hasMoved = true
                                            }
                                            // P-448: la elevación se recalcula con la altura del
                                            // dedo — alta al salir de la bandeja, baja arriba del todo.
                                            liftPx = liftForPieceAt(draggedPiece, currentFingerPos.y)
                                            val targetPos = Offset(
                                                currentFingerPos.x - (pieceWidthPx / 2f),
                                                (currentFingerPos.y - liftPx).coerceAtLeast(rootOffset.y)
                                            )

                                            // En cuanto el dedo manda, la animación de recogida sobra:
                                            // si siguiera corriendo pelearía con el seguimiento.
                                            if (dragAnimJob != null) {
                                                dragAnimJob?.cancel()
                                                dragAnimJob = null
                                            }
                                            // Escritura DIRECTA, sin corrutina de por medio: la ficha
                                            // se mueve en el mismo evento táctil que la mueve.
                                            if (piece.isFichona) {
                                                val movement = targetPos - dragPos.value
                                                fichonaTilt.floatValue = (fichonaTilt.floatValue * 0.65f + movement.x * 0.10f).coerceIn(-3f, 3f)
                                                val now = android.os.SystemClock.uptimeMillis()
                                                if (hasMoved && movement.getDistance() > 3f && now - lastHeavyFeedback > 180L) {
                                                    Haptics.fichonaDrag(); lastHeavyFeedback = now
                                                }
                                            }
                                            dragPos.value = targetPos

                                            // El ViewModel solo se entera cuando la ficha CAMBIA de
                                            // celda. Es la misma conversión que hace `onDrag` por
                                            // dentro, así que el resultado es idéntico — lo único
                                            // que desaparece es el recálculo redundante entre celda
                                            // y celda (y la comparación de estado que arrastraba).
                                            val cellNow = targetPos.toGridPosition(cellSizePx, boardOffsetPx)
                                            if (cellNow != lastGhostCell) {
                                                lastGhostCell = cellNow
                                                viewModel.onDrag(targetPos, boardOffsetPx, cellSizePx)
                                            }

                                            if (fuseRuleRef.value) {
                                                val sobre = if (trayRectWindow.contains(currentFingerPos)) {
                                                    val slots = traySlotsRef.value.size.coerceAtLeast(1)
                                                    val stride = (size.width + trayGap.toPx()) / slots
                                                    val idx = ((currentFingerPos.x - trayOffsetPx.x) / stride)
                                                        .toInt().coerceIn(0, slots - 1)
                                                    traySlotsRef.value.getOrNull(idx)?.takeIf { it.id != piece.id }
                                                } else null
                                                if (sobre?.id != fuseHover?.first) {
                                                    fuseHover = sobre?.let { t ->
                                                        viewModel.canFusePieces(piece.id, t.id)
                                                            .takeIf { it != com.cukiti.teikafusion.model.FuseCheck.NONE }
                                                            ?.let { t.id to it }
                                                    }
                                                }
                                            }

                                        }
                                    }

                                    // onDragEnd / soltar dedo

                                    // Muestra "No hay espacio" + (tras ~1.1 s) la oferta de revivir si
                                    // el tablero está REALMENTE atascado (`evaluateDeadEnd`: ninguna
                                    // ficha de la bandeja cabe en ningún sitio en ninguna de sus
                                    // cuatro rotaciones — P-276). Devuelve true si disparó. Se invoca SOLO al
                                    // intentar COLOCAR la ficha, o sea al soltarla sin encaje.
                                    //
                                    // (P-105, 2026-09-02) DOS FASES. Antes esto era un booleano y
                                    // todo atasco iba derecho a la oferta de revivir; peor, el
                                    // booleano se apagaba en cuanto el jugador tuviera carga de
                                    // CUALQUIER compañero reclamado, con lo que un tablero lleno con
                                    // una carga inútil en el bolsillo no producía NADA (C-1). Ahora
                                    // el motor responde con la salida que hay:
                                    //   · hay salida (inclinar / lanzar / comprar) → cartel SIN
                                    //     SALIDA, tablero visible, sin ventana ni cuenta atrás;
                                    //   · no hay ninguna → el aviso de siempre y, a ~1,1 s, la
                                    //     oferta, que ahora sabe titularse «SIN ESPACIO».
                                    val triggerStuckPromptIfNeeded = {
                                        if (noSpaceCenterVisible || deadEnd != null) false
                                        else when (val out = viewModel.evaluateDeadEnd()) {
                                            null -> false
                                            com.cukiti.teikafusion.model.BoardDeadEnd.None -> {
                                                noSpaceCenterVisible = true
                                                Haptics.warning()   // "sin espacio": aviso grave
                                                // Precargar el anuncio del revive AHORA (instante de
                                                // detectar el atasco), no cuando la oferta ya está en
                                                // pantalla: gana ~1.1 s de carga y evita el botón
                                                // "Cargando anuncio…" girando al abrirse el overlay.
                                                if (!AdManager.isReadyFlow.value) AdManager.loadAd(context)
                                                coroutineScope.launch {
                                                    kotlinx.coroutines.delay(1100)
                                                    noSpaceCenterVisible = false
                                                    viewModel.triggerNoSpaceRevive()
                                                }
                                                true
                                            }
                                            else -> {
                                                deadEnd = out
                                                surrenderVisible = false
                                                Haptics.warning()
                                                viewModel.soundManager.playInvalid()
                                                true
                                            }
                                        }
                                    }

                                    if (!explicitRelease) {
                                        dragAnimJob?.cancel(); dragAnimJob = null
                                        grabScaleJob?.cancel()
                                        dragPos.value = pieceOriginalPos
                                        viewModel.onDragCancel()
                                        draggedPieceLocal = null
                                    } else if (!hasMoved) {
                                        // TOQUE SECO SOBRE LA FICHA = GIRARLA 90° (P-276, vuelve el
                                        // 2026-09-24 por decisión del usuario). Se deshace el agarre,
                                        // la ficha vuelve a su cuadro y el motor la gira. Si el motor
                                        // se niega (dormida, tutorial) no suena nada: un tic celebra
                                        // una acción, y ahí no ha pasado ninguna.
                                        dragAnimJob?.cancel()
                                        dragAnimJob = null
                                        dragPos.value = pieceOriginalPos
                                        viewModel.onDragCancel()
                                        draggedPieceLocal = null
                                        if (viewModel.rotatePieceInTray(piece.id)) {
                                            trayRotateTick = piece.id to ((trayRotateTick?.second ?: 0) + 1)
                                            viewModel.soundManager.playUiClick()
                                        }
                                    } else if (piece.isFichona) {
                                        val held = dragState as? DragState.Dragging
                                        val landing = held?.ghostGridPosition
                                        dragAnimJob?.cancel(); dragAnimJob = null
                                        grabScaleJob?.cancel()
                                        viewModel.onDragCancel()
                                        viewModel.releaseFichona(piece, landing, dragPos.value.x, dragPos.value.y, cellSizePx)
                                        draggedPieceLocal = null
                                    } else {
                                        // P-232: soltada sobre su gemela → se funden. Manda sobre la
                                        // colocación: el dedo está en la bandeja, y la sombra que
                                        // pudiera quedar en la fila de abajo del tablero no es lo que
                                        // el jugador está apuntando.
                                        val fuse = fuseHover
                                        fuseHover = null
                                        val fundida = fuse != null &&
                                            fuse.second == com.cukiti.teikafusion.model.FuseCheck.OK &&
                                            viewModel.fusePieces(piece.id, fuse.first)
                                        if (fundida) {
                                            dragAnimJob?.cancel()
                                            dragAnimJob = null
                                            viewModel.onDragCancel()
                                            draggedPieceLocal = null
                                            Haptics.place()
                                        }
                                        val currentDragState = if (fundida) null else dragState
                                        if (currentDragState is DragState.Dragging && currentDragState.isValidPlacement && currentDragState.ghostGridPosition != null) {
                                            val landing = currentDragState.ghostGridPosition
                                            run {
                                                // Colocación instantánea, cero animaciones de caída
                                                viewModel.onDragEnd()
                                                draggedPieceLocal = null
                                                // colocación válida: "encaje" sólido, con la firma del bloque (P-283)
                                                Haptics.blockPlace(placeSkinStyle)
                                            }
                                        } else if (draggedPieceLocal != null) {
                                            // Regreso a la cola rápido si es inválido
                                            dragAnimJob?.cancel()
                                            returningToTray = true
                                            grabScaleJob?.cancel()
                                            dragAnimJob = coroutineScope.launch {
                                                // P-450: posición y tamaño aterrizan juntos en 280 ms.
                                                // El muelle de Offset esperaba su umbral subpíxel con la
                                                // ficha a tamaño de tablero; después encogía de golpe.
                                                val destination = pieceTopLefts[piece.id] ?: pieceOriginalPos
                                                val startScale = grabScale.value
                                                val start = dragPos.value + Offset(
                                                    piece.width * cellSizePx * (1f - startScale) / 2f,
                                                    piece.height * cellSizePx * (1f - startScale) / 2f,
                                                )
                                                val endScale = (trayPieceCellPx[piece.id] ?: cellSizePx) / cellSizePx
                                                grabScale.snapTo(1f)
                                                returnScale.floatValue = startScale
                                                dragPos.value = start
                                                returnActive = true
                                                animate(
                                                    initialValue = 0f,
                                                    targetValue = 1f,
                                                    animationSpec = tween(280, easing = androidx.compose.animation.core.CubicBezierEasing(0.2f, 0.8f, 0.3f, 1.06f))
                                                ) { progress, _ ->
                                                    dragPos.value = start + (destination - start) * progress
                                                    returnScale.floatValue = startScale + (endScale - startScale) * progress
                                                }
                                                viewModel.onDragCancel()
                                                draggedPieceLocal = null
                                            }
                                            Haptics.invalid()   // colocación inválida: rebote sordo
                                            // …y su sonido, que faltaba: el rechazo era el único
                                            // gesto del juego que solo se sentía y no se oía.
                                            viewModel.soundManager.playInvalid()

                                            // ¿La ficha CABIA y lo que la rechaza es la leccion?
                                            // Entonces no es un atasco, es una correccion: sale el
                                            // aviso del tutorial y NO se ofrece revivir. Sin esto, la
                                            // jugada equivocada del tutorial no producia nada en
                                            // absoluto (grabacion `analisistutorial`).
                                            val guidedCell = (dragState as? DragState.Dragging)
                                                ?.ghostGridPosition
                                            val tutorialMiss = guidedCell != null &&
                                                viewModel.isTutorialMisplacement(
                                                    piece, guidedCell.x, guidedCell.y
                                                )
                                            if (tutorialMiss) {
                                                tutorialMisplaceMessage = TUTORIAL_MISPLACE_TEXT
                                                tutorialMisplaceToken++
                                            } else {
                                                // Derrota real: ficha arrastrada y soltada SIN colocación
                                                // válida. NO se exige soltar DENTRO del tablero: si estás
                                                // atascado da igual dónde sueltes; si no,
                                                // `evaluateDeadEnd` devuelve null y la ficha vuelve a
                                                // la bandeja sin más.
                                                triggerStuckPromptIfNeeded()
                                            }
                                        }
                                    }
                                    } finally {
                                        // Las ramas normales ya la soltaron (o la vuelta animada lo
                                        // hará al terminar). Solo queda el corte a medias.
                                        if (!returningToTray && draggedPieceLocal?.id == piece.id) {
                                            dragAnimJob?.cancel()
                                            dragAnimJob = null
                                            dragPos.value = pieceOriginalPos
                                            fuseHover = null
                                            viewModel.onDragCancel()
                                            draggedPieceLocal = null
                                        }
                                    }
                                }
                            }
                        }
                ) {
                    // Bosque del Sueño / Maite: "z" de sueño flotando en el fondo del panel de fichas.
                    if (gameState.drowsyTurns > 0) {
                        DrowsyZzzBackdrop(modifier = Modifier.matchParentSize())
                    }
                    // P-63: y la FICHA DORMIDA se adormece — pierde color y cabecea mientras la
                    // Maite ronda. La regla vive en la bandeja, así que el aviso tiene que estar en
                    // la bandeja: antes solo había una "z" de fondo y el jugador perdía sin más.
                    //
                    // (2026-09-07) La animación se creaba para las TRES fichas porque el castigo era
                    // global («no puedes rotar»). Ahora la Maite duerme UNA, así que la transición
                    // se sigue creando una sola vez —es la misma para todas— pero cada hueco la
                    // aplica solo si le toca (ver `pieceAsleep`).
                    val traySleepy = gameState.drowsyTurns > 0
                    val traySleep by animateFloatAsState(
                        targetValue = if (traySleepy) 1f else 0f,
                        animationSpec = tween(420, easing = FastOutSlowInEasing),
                        label = "traySleep"
                    )
                    // El cabeceo es AMBIENTE: se congela en pausa como el resto (bloque 20).
                    val trayNodAnimated = traySleepy && LocalAnimationsActive.current
                    val trayNod = if (trayNodAnimated) {
                        val nodTr = rememberInfiniteTransition(label = "trayNod")
                        nodTr.animateFloat(
                            -1f, 1f,
                            infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                            label = "trayNodV"
                        )
                    } else null
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(trayGap)) {
                        traySlots.forEachIndexed { pieceIdx, slotPiece ->
                            // El MARCO del hueco es del hueco, no de la ficha: se dibuja siempre,
                            // tenga o no ficha dentro, y no entra en el `key(piece.id)` para que no
                            // se re-cree —ni reanime— cada vez que cambia lo que contiene.
                            val piece = slotPiece
                            if (piece == null) {
                                Box(modifier = Modifier.weight(playWeight).fillMaxHeight()) {
                                    TraySlotFrame(filled = false, accent = NeonBlue)
                                }
                                return@forEachIndexed
                            }
                            key(piece.id) {
                                // ── P-143 · LA COLA FORZADA / P-145 · LA FICHA TAPADA ─────────
                                // Las dos son reglas de la casa de la familia B y las dos se
                                // resuelven aquí porque **son información**: el motor ya rechaza
                                // una ficha bloqueada, pero rechazarla sin avisar sería un juego
                                // que no responde. La posición en la COLA es la de la ficha entre
                                // las que quedan, no su cuadro: una ficha colocada deja su hueco
                                // vacío y la siguiente pasa a ser la primera de la cola.
                                val queueIdx = trayPieces.indexOfFirst { it.id == piece.id }
                                val queueLocked = !com.cukiti.teikafusion.model.ForcedQueue
                                    .allows(gameState.level, queueIdx)
                                // ── Bosque del Sueño: LA FICHA DORMIDA (2026-09-07) ───────────
                                // Misma familia que la cola forzada: una ficha que está pero no se
                                // puede jugar. El motor ya la rechaza (`GameState.isPieceAsleep`);
                                // esto es lo que hace que el rechazo no se lea como un fallo.
                                val pieceAsleep = gameState.isPieceAsleep(piece.id)
                                // ── P-229 · LA FICHA CONDENADA ────────────────────────────────
                                // Esta no es que no la dejen jugar: es que **ya no cabe en ninguna
                                // parte**. Antes el juego la cambiaba por otra a tus espaldas; ahora
                                // que quedarse sin jugada es una derrota de verdad, se avisa. El
                                // cálculo NO se hace aquí (sería 3×64 celdas en cada recomposición):
                                // llega hecho del cierre de turno, en `unplayablePieceIds`.
                                val pieceDoomed = piece.id in gameState.unplayablePieceIds
                                val faceDown = com.cukiti.teikafusion.model.FaceDownTray.isHidden(
                                    level = gameState.level,
                                    index = pieceIdx,
                                    trayCount = trayCapacity,
                                    remaining = trayPieces.size,
                                )
                                // Tamaño por CUADRADO ENVOLVENTE: invariante al giro, así rotar una
                                // ficha en la bandeja ya no le cambia la escala ni la saca del marco
                                // (ver [trayCellSize]).
                                val pieceCellSize = trayCellSize(slotWidth, slotHeight, piece)
                                // PERF: el glow pulsante del comodín se crea SOLO si la ficha es
                                // comodín. Antes se instanciaba un InfiniteTransition por CADA ficha
                                // (los 3 slots), lo que mantenía a la app pidiendo frames a 60fps en
                                // reposo (costo de batería/FPS aunque el jugador no haga nada). Ahora
                                // las fichas normales no animan nada → bandeja quieta sin combo.
                                val wildcardGlow = if (piece.isWildcard) {
                                    rememberInfiniteTransition(label = "wActive").animateFloat(
                                        initialValue = 0.3f, targetValue = 0.85f,
                                        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
                                        label = "wActiveAlpha"
                                    )
                                } else null
                                // ── P-77 · LA FICHA QUE CRECE ────────────────────────────────
                                // Consume `PieceGrew` (y es el ÚNICO sitio que lo hace): la ranura
                                // da un golpe de escala cuando su ficha gana un bloque. La forma ya
                                // cambia sola en pantalla; esto es lo que hace que el jugador MIRE
                                // en el instante en que cambia, en vez de descubrirlo dos turnos
                                // después al ir a colocarla.
                                val growPop = remember { Animatable(1f) }
                                // ── P-276 · EL GIRO: un cuarto de vuelta con rebote ──────────
                                // La forma ya llega girada del motor; esto la hace ENTRAR desde
                                // −90° para que se lea como un giro y no como un cambio de ficha.
                                // Se ignora la señal que ya estaba al montar el cuadro.
                                val rotateSpin = remember { Animatable(0f) }
                                val rotateTickAtMount = remember { trayRotateTick }
                                LaunchedEffect(trayRotateTick) {
                                    val tick = trayRotateTick ?: return@LaunchedEffect
                                    if (tick == rotateTickAtMount || tick.first != piece.id) return@LaunchedEffect
                                    rotateSpin.snapTo(-90f)
                                    rotateSpin.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 700f))
                                }
                                val grewEvent = remember(eventBuckets, pieceIdx) {
                                    eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.PieceGrew>()
                                        .firstOrNull { ev -> ev.slot == pieceIdx }
                                }
                                LaunchedEffect(grewEvent?.id) {
                                    val ev = grewEvent ?: return@LaunchedEffect
                                    growPop.snapTo(1.28f)
                                    growPop.animateTo(
                                        1f, spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessLow
                                        )
                                    )
                                    viewModel.consumeEvent(ev.id)
                                }
                                // (RETIRADO 2026-09-07) Aquí vivía el fundido de `TrayPieceSwapped`,
                                // el cue del reparador de bandeja (P-94). El reparador se fue —una
                                // bandeja sin jugada vuelve a ser una derrota—, y con él su evento.
                                val wildcardTransformScale = remember { Animatable(1f) }
                                LaunchedEffect(piece.isWildcard) {
                                    if (piece.isWildcard) {
                                        wildcardTransformScale.snapTo(1.35f)
                                        wildcardTransformScale.animateTo(
                                            1f, spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessLow
                                            )
                                        )
                                    }
                                }
                                // Entrada de ficha: cada pieza NUEVA (id nuevo al rellenar la bandeja)
                                // entra con un pop con rebote desde abajo → la bandeja "respira" y se
                                // siente viva a medida que cambian las opciones (estilo Block Blast).
                                // graphicsLayer = solo dibujo → no altera el layout ni el hit-test del drag.
                                //
                                // La entrada corre UNA sola vez por ficha (ver [trayEntered]): si el
                                // hueco se recompone con la misma ficha, aparece ya entera. Sin esto
                                // la bandeja parpadeaba entera y volvía con las mismas fichas.
                                // (2026-09-07) Aquí se preguntaba además si la ficha era un RELEVO
                                // del reparador para darle un fundido en vez del pop. Ya no hay
                                // relevos: toda ficha que entra en la bandeja es una ficha nueva.
                                val firstEntrance = remember(piece.id) { trayEntered.add(piece.id) }
                                val entrance = remember(piece.id) {
                                    Animatable(if (firstEntrance) 0f else 1f)
                                }
                                LaunchedEffect(piece.id) {
                                    if (!firstEntrance) return@LaunchedEffect
                                    entrance.animateTo(
                                        1f, spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .weight(playWeight)
                                        .fillMaxHeight()
                                        // P-201: el cuadro de esta ficha, para que un shot pueda
                                        // señalarla sola. Se mide el HUECO y no la ficha: el foco
                                        // tiene que enmarcar algo del tamaño de un cuadro, y una
                                        // ficha de una celda dejaría un círculo diminuto.
                                        .onGloballyPositioned {
                                            trayPieceRectsWindow[piece.id] = it.boundsInWindow()
                                            trayPieceCellPx[piece.id] = with(density) { pieceCellSize.toPx() }
                                        }
                                ) {
                                // El marco NO entra en la capa gráfica de la ficha: si entrara, el
                                // pop de entrada (escala 0,55→1) encogería también el cuadro y los
                                // tres huecos "respirarían" a destiempo. El cuadro está quieto; lo
                                // que salta es lo que hay dentro.
                                val rewarded = piece.id in rewardIds
                                TraySlotFrame(
                                    modifier = if (!rewarded) Modifier else Modifier.drawWithContent {
                                        drawContent()
                                        val a = rewardGlow.value
                                        if (a > 0f) drawRoundRect(
                                            color = TrayRewardGold.copy(alpha = a),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(20.dp.toPx()),
                                            style = Stroke(width = (2.dp + 3.dp * a).toPx()),
                                        )
                                    },
                                    filled = true,
                                    // P-229: la ficha que ya no cabe tiñe su CUADRO de ámbar —el
                                    // mismo oro del cartel «SIN SALIDA» que está anunciando—. El
                                    // marco y no la ficha: la forma hay que poder leerla entera
                                    // para decidir si aún se puede abrir hueco para ella.
                                    accent = when {
                                        pieceDoomed -> Color(0xFFFFC857)
                                        // P-349: la ficha fabricada en el taller lleva el turquesa del banco.
                                        piece.isHandmade -> BankAccent
                                        rewarded -> TrayRewardGold
                                        piece.isWildcard -> NeonBlue
                                        else -> NeonPurple
                                    },
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            // ENTRADA SECA (2026-08-26d). La ficha repuesta nacía con
                                            // `alpha = 0` y subía con el muelle, así que la bandeja
                                            // volvía DESVANECIÉNDOSE — justo la lectura que el usuario
                                            // reportaba («el panel se va y vuelve»). Ahora nace
                                            // VISIBLE y solo hace el pop de escala: la reposición se
                                            // lee como un golpe. El juice se conserva entero; lo que
                                            // se va es el fundido, que es lo que se leía como ausencia.
                                            val e = entrance.value
                                            val sc = wildcardTransformScale.value * growPop.value *
                                                (0.72f + 0.28f * e)
                                            scaleX = sc
                                            scaleY = sc
                                            translationY = (1f - e) * size.height * 0.14f
                                            alpha = 1f
                                            rotationZ = rotateSpin.value
                                            // ── Bosque del Sueño (P-63): la ficha se DUERME ──
                                            // Se lee aquí, en la capa de dibujo, no en el cuerpo del
                                            // composable: el cabeceo va a ritmo de frame y leerlo
                                            // arriba recompondría los tres huecos 60 veces por segundo.
                                            // La ficha que la COLA no deja jugar todavía se apaga:
                                            // sigue ahí y se lee entera —hay que poder planificar
                                            // con ella, esa es la mecánica— pero no se ofrece.
                                            if (queueLocked) alpha *= 0.38f
                                            // P-229: la condenada se apaga solo un poco. No es que
                                            // no la dejen jugar —eso es la cola y la Maite, y se
                                            // apagan de verdad—: es que no cabe. Tiene que seguir
                                            // legible para poder planificar cómo abrirle hueco.
                                            // P-430 (2026-10-01): y además pasa a GRIS (ver
                                            // `doomedGrayscale` más abajo), como en los block puzzle
                                            // populares: la forma se lee, el color ya no la ofrece.
                                            if (pieceDoomed) alpha *= 0.74f
                                            // (2026-09-07) La Maite ya no apaga la bandeja ENTERA:
                                            // duerme UNA ficha, y solo esa cabecea. El booleano se
                                            // captura arriba (cambia una vez por turno); el valor
                                            // animado se lee aquí, en dibujo.
                                            val sleep = if (pieceAsleep) traySleep else 0f
                                            if (sleep > 0.001f) {
                                                alpha *= (1f - 0.42f * sleep)
                                                rotationZ += (trayNod?.value ?: 0f) * 3.5f * sleep
                                                // Se vence un poco, como quien da cabezadas.
                                                translationY += size.height * 0.03f * sleep
                                            }
                                        }
                                        .then(if (pieceDoomed) Modifier.doomedGrayscale() else Modifier)
                                        .then(
                                            // El bloque-ESTRELLA (Deseo Estelar) NO lleva el marco/fondo azul
                                            // de comodín — se veía como un panel raro. Solo la estrella líquida.
                                            //
                                            // El latido se lee AL DIBUJAR (perf 2026-09-26). Antes eran un
                                            // `border` y un `background` construidos con `wildcardGlow.value`
                                            // en composición: con un comodín en la bandeja, la pantalla
                                            // ENTERA se recomponía a 60 fps (este hueco no tiene ámbito de
                                            // recomposición propio). Mismo dibujo, en el mismo orden: fondo
                                            // bajo la ficha, marco por dentro del borde y encima.
                                            if (piece.isWildcard && !piece.isStarWish) Modifier.drawWithContent {
                                                val g = wildcardGlow?.value ?: 0f
                                                val r = 20.dp.toPx()
                                                drawRoundRect(
                                                    color = NeonBlue.copy(alpha = g * 0.20f),
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(r),
                                                )
                                                drawContent()
                                                val w = (1f + g * 1.5f).dp.toPx()
                                                val half = w / 2f
                                                drawRoundRect(
                                                    color = NeonBlue.copy(alpha = g),
                                                    topLeft = Offset(half, half),
                                                    size = Size(size.width - w, size.height - w),
                                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                                                        (r - half).coerceAtLeast(0f)
                                                    ),
                                                    style = Stroke(width = w),
                                                )
                                            }
                                            else Modifier
                                        )
                                        .clip(TraySlotShape)
                                        // La bandeja es un lienzo de bloques dibujados: sin esto el lector
                                        // de pantalla no anuncia nada de lo que hay para jugar.
                                        //
                                        // P-13: y además tiene que poder JUGARSE. Con exploración
                                        // táctil el arrastre es imposible (el lector se queda el
                                        // gesto), así que aquí la ficha se SELECCIONA con un doble
                                        // toque y luego se elige la celda en el tablero.
                                        .then(
                                            if (touchExploration) Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                                role = androidx.compose.ui.semantics.Role.Button
                                            ) { viewModel.a11ySelectPiece(piece.id) }
                                            else Modifier
                                        )
                                        .semantics(mergeDescendants = true) {
                                            // El hueco es fijo (1 de 3), así que se anuncia el HUECO
                                            // y aparte cuántas fichas quedan: con el reparto por
                                            // ranuras, "ficha 2 de 2" describía mal una bandeja en
                                            // la que la ficha 2 sigue en el cuadro del medio.
                                            val slot = if (horizonMode) "Ficha para colocar, "
                                                else "Hueco ${pieceIdx + 1} de $TRAY_SLOTS, " +
                                                "quedan ${trayPieces.size} fichas"
                                            val shape = when {
                                                piece.isStarWish -> "bloque del Deseo Estelar, se derrama al soltarlo"
                                                piece.isWildcard -> "ficha comodín de ${piece.shape.size} bloques"
                                                else -> "${piece.shape.size} bloques, ${piece.width} de ancho por ${piece.height} de alto"
                                            }
                                            // P-229 / Maite: lo que el marco ámbar y el cabeceo dicen
                                            // en visual hay que decirlo también en voz — si no, el
                                            // aviso existe solo para quien puede verlo.
                                            val estado = when {
                                                pieceDoomed -> ". Ya no cabe en el tablero"
                                                pieceAsleep -> ". Dormida por la Maite"
                                                piece.id == gameState.drowsyMarkedId -> ". La Maite la ha marcado: juégala antes de que se duerma"
                                                else -> ""
                                            }
                                            contentDescription = when {
                                                !touchExploration -> "$slot: $shape$estado"
                                                a11ySelectedPieceId == piece.id ->
                                                    "$slot: $shape$estado. Seleccionada. Elige una casilla del tablero"
                                                else -> "$slot: $shape$estado. Toca dos veces para seleccionarla"
                                            }
                                            // P-276: la acción «Rotar» vuelve con el giro. Solo se
                                            // ofrece cuando el motor la acepta (no dormida, no en el
                                            // tutorial): una acción que no hace nada es la clase de
                                            // mentira que la accesibilidad no perdona.
                                            if (!pieceAsleep && !com.cukiti.teikafusion.model.TutorialCampaign
                                                    .isTutorialLevel(gameState.level)
                                            ) {
                                                customActions = listOf(
                                                    CustomAccessibilityAction("Rotar") {
                                                        viewModel.rotatePieceInTray(piece.id).also { ok ->
                                                            if (ok) trayRotateTick =
                                                                piece.id to ((trayRotateTick?.second ?: 0) + 1)
                                                        }
                                                    }
                                                ) + if(reserveMode && viewModel.canSwapReserve(piece.id)) listOf(
                                                    CustomAccessibilityAction("Intercambiar con reserva") {
                                                        viewModel.swapReserve(piece.id); true
                                                    }
                                                ) else emptyList()
                                            }
                                        }
                                        .clipToBounds(),
                                    // CENTRADA en su cuadro (2026-08-26). Con `TopCenter` la ficha
                                    // se pegaba al borde de arriba y cada una quedaba a una altura
                                    // distinta según su alto: tres fichas desalineadas dentro de tres
                                    // marcos iguales. El centro es lo que hace que se lean simétricas.
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (faceDown) {
                                        // ── P-145 · EL DORSO ──────────────────────────────────
                                        // La ficha existe y ocupa su cuadro; lo que no se sabe es
                                        // QUÉ es. Se dibuja un dorso y no un hueco vacío a
                                        // propósito: un cuadro vacío diría «te quedan dos fichas»,
                                        // y le quedan tres — una de ellas sin destapar.
                                        TrayFaceDownBack(modifier = Modifier.fillMaxSize())
                                        return@Box
                                    }
                                    val reserveSwapEvent=eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.RulePulse>().firstOrNull { it.kind=="reserve_swap" && it.activePieceId==piece.id }
                                    PieceView(
                                        modifier = reserveArrivalModifier(reserveSwapEvent,LocalAnimationsActive.current && !reducedFx),
                                        piece = piece,
                                        isDragging = draggedPieceLocal?.id == piece.id || piece.id in workshopHeldIds,
                                        isQueuePiece = true,
                                        cellSize = pieceCellSize,
                                        // P-233: la ficha que encaja en el molde lleva su sello.
                                        moldMatch = gameState.moldAt != null &&
                                            com.cukiti.teikafusion.model.MoldRule.normalized(piece.shape) == gameState.moldShape,
                                        onPositioned = { center, topLeft ->
                                            pieceTopLefts[piece.id] = topLeft
                                            if (piece.id == com.cukiti.teikafusion.model.TutorialCampaign
                                                    .spec(gameState.level)?.piece?.id
                                            ) {
                                                tutorialPieceRectWindow = Rect(
                                                    offset = topLeft,
                                                    size = Size(
                                                        width = (center.x - topLeft.x) * 2f,
                                                        height = (center.y - topLeft.y) * 2f
                                                    )
                                                )
                                            }
                                        },
                                        fiery = fireActive
                                    )
                                    eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.RulePulse>().firstOrNull { it.kind=="reserve_swap" && it.activePieceId==piece.id }?.let { ev ->
                                        ReserveSwapGlow(ev,Modifier.matchParentSize(),animationsOn=LocalAnimationsActive.current && !reducedFx)
                                    }
                                    if(com.cukiti.teikafusion.model.RelayRules.appliesTo(gameState.level) && gameState.relayCharged)
                                        RelayTrayGlow(Modifier.matchParentSize(),animationsOn=LocalAnimationsActive.current && !reducedFx)
                                    if(reserveMode) ReserveExchangeButton(
                                        enabled=dragState is DragState.Idle && !boardBusy && viewModel.canSwapReserve(piece.id),
                                        onClick={ viewModel.swapReserve(piece.id) },
                                        modifier=Modifier.align(Alignment.BottomEnd).padding(3.dp))
                                    // P-449: el ↻ de «tócala y gira», hasta que el jugador gire una.
                                    // Solo en las fichas que cambian al girar (no en el cuadrado), y no
                                    // donde no se puede girar (tutorial, dormida).
                                    val turns = remember(piece.shape) { piece.rotate().shape != piece.shape }
                                    if (!rotateLearned && turns && !pieceAsleep &&
                                        !com.cukiti.teikafusion.model.TutorialCampaign.isTutorialLevel(gameState.level)
                                    ) {
                                        RotateHintBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
                                    }
                                    // P-353: la ficha que la Maite ha marcado en el aviso.
                                    if (piece.id == gameState.drowsyMarkedId && !pieceAsleep) {
                                        DrowsyMarkBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
                                    }
                                    // P-232: el aviso antes de soltar y el latido después.
                                    fuseHover?.takeIf { it.first == piece.id }?.let { (_, check) ->
                                        FuseTargetGlow(
                                            ok = check == com.cukiti.teikafusion.model.FuseCheck.OK,
                                            modifier = Modifier.matchParentSize(),
                                        )
                                    }
                                    fusedEvent?.takeIf { it.fusedId == piece.id }?.let { ev ->
                                        PiecesFusedPulse(
                                            event = ev,
                                            onComplete = { viewModel.consumeEvent(ev.id) },
                                            modifier = Modifier.matchParentSize(),
                                        )
                                    }
                                }
                                }   // cierra la caja del HUECO (marco + ficha)
                            }
                        }
                        if(reserveMode) ReserveSlot(gameState.reservePieces.firstOrNull(),
                            ready=gameState.reserveSwapRefill!=gameState.trayRefillCount,
                            slotWidth=trayUsable*(reserveWeight/totalWeight),
                            modifier=Modifier.weight(reserveWeight).fillMaxHeight(),
                            animationsOn=LocalAnimationsActive.current && !reducedFx,
                            waiting=(gameState.reservePieces.size-1).coerceAtLeast(0))
                        // ── P-75 · EL HORIZONTE ──────────────────────────────────────────────
                        // Las tres que vienen, a la derecha de la jugable, más pequeñas y apagadas.
                        // No se agarran (ver el gesto) y no llevan marco de comodín ni animación de
                        // entrada: son un dato, y un dato que se mueve se lee como una ficha.
                        if (horizonMode) {
                            repeat(horizonCount) { idx ->
                                val upcoming = horizonQueue.getOrNull(idx)
                                HorizonSlot(
                                    piece = upcoming,
                                    slotWidth = horizonSlotWidth,
                                    slotHeight = slotHeight,
                                    isNext = idx == 0,
                                    accent = NeonBlue,
                                    modifier = Modifier
                                        .weight(horizonWeight)
                                        .fillMaxHeight()
                                        .semantics {
                                            contentDescription = upcoming?.let {
                                                "Ficha ${idx + 1} de las que vienen: " +
                                                    "${it.width} por ${it.height}"
                                            } ?: "Hueco del horizonte vacío"
                                        }
                                )
                            }
                        }
                    }

                    // (RETIRADO 2026-07-25: aquí vivía el BOTÓN DE HABILIDAD — el retrato del
                    //  compañero con su aro de energía. Las habilidades pasaron a ser PASIVAS: se
                    //  disparan solas al llenarse la energía (ver GameViewModel.addCompanionEnergy)
                    //  y lo que avisa de que viene es el AURA DE CARGA del tablero, teñida del bioma
                    //  del compañero. Sin botón hay más aire abajo, que es lo que aprovecha el
                    //  descolgado del panel de fichas.)

                    // Iñaki / Fichas en Llamas: las llamas prenden SOBRE el panel de fichas — es aquí
                    // donde las fichas se incendian (en el tablero no ha pasado nada todavía).
                    val trayIgnite = eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.TrayIgnite>()
                        .firstOrNull()
                    trayIgnite?.let { ev ->
                        key(ev.id) {
                            TrayIgniteVFX(
                                count = ev.count,
                                modifier = Modifier.matchParentSize(),
                                onComplete = { viewModel.consumeEvent(ev.id) }
                            )
                        }
                    }
                }

                // (ERRADICADO 2026-08-01, pedido del usuario) Aquí vivía el botón ↩ de DESHACER
                // (P-14): devolvía el turno una vez por nivel. Con los turnos contados y recortados,
                // devolver uno hace mentir al presupuesto que el nivel anuncia.
                } // End of Area Inferior Row

                // Empuja el panel de fichas hacia ARRIBA: todo el espacio sobrante se acumula aquí abajo.
                Spacer(modifier = Modifier.weight(1f))

                // HUECO RESERVADO del banner. El banner real va anclado al fondo del Box; este
                // spacer evita que la bandeja se dibuje encima del anuncio.
                // Con «Quitar anuncios» comprado (o el kill-switch remoto apagado) NO hay banner:
                // ni se dibuja ni se le reserva sitio. Pagar por quitarlos tiene que DEVOLVER esos
                // 50 dp de pantalla al tablero — si no, el jugador paga y sigue viendo el hueco.
                val bannerClearance = if (bannerVisible) BANNER_HEIGHT else 0.dp
                Spacer(modifier = Modifier.height(bannerClearance))
            }

            // BANNER DE ANUNCIOS — anuncio de PRUEBA de AdMob, ANCLADO al borde inferior de la
            // pantalla. Fuera del flujo vertical: pase lo que pase con el alto del contenido, el
            // anuncio se ve entero y siempre en el mismo sitio.
            if (bannerVisible) {
                BannerAdView(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .height(BANNER_HEIGHT)
                        .fillMaxWidth()
                )
            }
          } // Box envoltorio del fondo de bioma
        }

        // (El marco arcoíris del combo se dibuja MÁS ABAJO en el árbol, detrás de la UI de juego
        //  — sobre el fondo del bioma y por DEBAJO del tablero y los botones — para que el tablero
        //  opaco y los botones lo tapen y no oculte información ni delinee la grilla.)

        // (Las partículas de combo a pantalla completa se eliminaron a petición del usuario.)

        // Pieza voladora animada (Renderizada en la capa superior absoluta)
        val flyingPiece = draggedPieceLocal
        if (flyingPiece?.isFichona == true) {
            Canvas(Modifier.fillMaxSize()) {
                // Physics invalidates draw only; the guide uses the same live placement rules.
                teikaBodiesState.value
                val guide = viewModel.fichonaGuide(flyingPiece)
                if (guide != null) flyingPiece.shape.forEach { block ->
                    val at = boardOffsetPx - rootOffset + Offset((guide.x + block.x) * cellSizePx, (guide.y + block.y) * cellSizePx)
                    drawRect(BankAccent.copy(alpha = 0.11f), at, androidx.compose.ui.geometry.Size(cellSizePx, cellSizePx))
                    drawRect(BankAccent.copy(alpha = 0.55f), at, androidx.compose.ui.geometry.Size(cellSizePx, cellSizePx),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
                }
            }
            FichonaDragVFX(flyingPiece, dragPos, rootOffset, cellSizePx, reducedFx)
        }
        if (flyingPiece != null) {
            // Sin sombra proyectada bajo la ficha levantada (eliminada a petición): la pieza
            // sube limpia, sólo con el punch de escala de recogida.
            PieceView(
                piece = flyingPiece,
                isDragging = false,
                isQueuePiece = false,
                cellSize = cellSize,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (dragPos.value.x - rootOffset.x).roundToInt(),
                            (dragPos.value.y - rootOffset.y).roundToInt()
                        )
                    }
                    .graphicsLayer {
                        if (returnActive) transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f)
                        val sc = grabScale.value * returnScale.floatValue
                        scaleX = sc
                        scaleY = sc
                        rotationZ = if (flyingPiece.isFichona && !reducedFx) fichonaTilt.floatValue else 0f
                    },
                fiery = fireActive
            )
        }

        eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.FichonaShattered>().forEach { event ->
            key(event.id) {
                FichonaShatterVFX(event, rootOffset, reducedFx) { viewModel.consumeEvent(event.id) }
            }
        }

        // Flying coin animations (coins earned → counter)
        flyingCoinEntries.forEach { entry ->
            key(entry.id) {
                FlyingGameCoin(
                    start = entry.start,
                    end = entry.end,
                    onFinished = { flyingCoinEntries.removeAll { it.id == entry.id } }
                )
            }
        }

        // ── ANUNCIO INTERSTICIAL (2026-08-09) ─────────────────────────────────
        // Ejecuta [action] DESPUÉS del anuncio, o directamente si no toca / no hay inventario.
        //
        // Solo se llama desde CORTES naturales —pasar de nivel y salir del game over—, jamás con la
        // partida en curso: un intersticial encima del tablero incumple la política de AdMob
        // («between levels in a game») y es causa directa de suspensión de la cuenta. Los tres topes
        // (nivel mínimo, 1 de cada N cortes, hueco desde el último anuncio a pantalla completa) los
        // decide `InterstitialCadence` a través de `viewModel.onSessionBreak()`.
        //
        // La acción va en el `onClosed` y no antes: si se ejecutara ya, el jugador vería la pantalla
        // siguiente moviéndose por debajo del anuncio al cerrarlo.
        val runAfterInterstitial: (String, () -> Unit) -> Unit = { placement, action ->
            val activity = context as? Activity
            val due = viewModel.onSessionBreak()
            val shown = due && activity != null && AdManager.showInterstitialAd(activity, placement) {
                viewModel.onInterstitialShown()
                action()
            }
            if (!shown) action()
        }

        // Overlays de estado
        // P-436 · las VENTANAS van en su nivel ([GameLayers.WINDOW]): ninguna capa de juego declarada
        // después puede volver a pintarse encima (pasó con la enseñanza y con el banco).
        if (showOverlay) Box(Modifier.fillMaxSize().zIndex(GameLayers.WINDOW)) {
            if (gameState.status == com.cukiti.teikafusion.model.GameStatus.LEVEL_COMPLETE) {
                if (com.cukiti.teikafusion.model.TutorialCampaign.isTutorialLevel(gameState.level)) {
                    TutorialLevelCompleteOverlay(
                        level = gameState.level,
                        onContinue = {
                            viewModel.soundManager.playUiClick()
                            viewModel.advanceTutorial()
                        }
                    )
                } else {
                    val pendingDoubleCoins by viewModel.pendingDoubleCoins.collectAsState()
                    val adReadyForDouble by AdManager.isReadyFlow.collectAsState()
                    val siguienteNivel = gameState.level + 1
                    LaunchedEffect(siguienteNivel) {
                        viewModel.prepareNextLevelRound()
                    }
                    LevelCompleteOverlay(
                    level      = gameState.level,
                    score      = gameState.score,
                    highScore  = gameState.highScore,
                    movesLeft  = gameState.movesLeft,
                    movesTotal = gameState.movesTotal,
                    companionId = selectedCompanionId,
                    companionLevel = selectedCompanionId
                        ?.let { companionUpgrades.levelOf(it) }
                        ?: 1,
                    consecutiveWins = consecutiveWins,
                    onNextLevel = {
                        viewModel.soundManager.playUiClick(); viewModel.consumeCrystalDrop()
                        runAfterInterstitial("intersticial_nivel") { viewModel.nextLevel() }
                    },
                    reducedFx = reducedFx,
                    onGoHome    = {
                        viewModel.soundManager.playUiClick(); viewModel.consumeCrystalDrop()
                        Iris.run { viewModel.goHome() }
                    },
                    crystalsEarned = lastCrystalDrop,
                    onScoreTick = { viewModel.soundManager.playScoreTick(it) },
                    // P-20: duplicar la recompensa viendo un anuncio. Solo si hay algo que doblar,
                    // hay anuncio cargado y a este jugador se le ofrecen anuncios.
                    doubleCoins = if (rewardedOffered && adReadyForDouble) pendingDoubleCoins else 0,
                    onDoubleCoins = if (rewardedOffered && adReadyForDouble && pendingDoubleCoins > 0) {
                        {
                            val activity = context as? Activity
                            if (activity != null) AdManager.showRewardedAd(
                                activity,
                                // La recompensa se cobra SOLO si el anuncio se completó.
                                onRewarded = { viewModel.claimDoubledLevelCoins() },
                                placement = "nivel_x2"
                            )
                        }
                    } else null
                    )
                }
            } else if (gameState.status == com.cukiti.teikafusion.model.GameStatus.GAME_OVER) {
                GameOverOverlay(
                    score = gameState.score,
                    highScore = gameState.highScore,
                    isNewRecord = gameState.score > 0 && gameState.score >= gameState.highScore,
                    onRestart = { runAfterInterstitial("intersticial_derrota") { viewModel.restartGame() } },
                    onGoHome = { runAfterInterstitial("intersticial_derrota") { Iris.run { viewModel.goHome() } } },
                    onShare = {
                        try {
                            val shareText = "¡Logré ${gameState.score} puntos y ${gameState.teikasFormed} Mega Teikas en Teika Fusion! 🎮\n¿Puedes superarme?"
                            val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(android.content.Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(android.content.Intent.createChooser(sendIntent, "Compartir puntuación"))
                        } catch (_: Exception) {}
                    },
                    onViewLeaderboard = { viewModel.openLeaderboard() },
                    onScoreTick = { viewModel.soundManager.playScoreTick(it) }
                )
            } else if (gameState.status == com.cukiti.teikafusion.model.GameStatus.REVIVE_OFFER) {
                // Mientras el auto-juego de revivir corre, no se muestra ningún overlay: se ve el
                // tablero despejando líneas solo.
                val isAutoReviving by viewModel.isAutoReviving.collectAsState()
                // Mientras se ve nuestro anuncio recompensado, ocultar el overlay para que su cuenta
                // atrás de 10 s no expire durante el anuncio y dispare la derrota.
                val rewardedAdActive by viewModel.rewardedAdActive.collectAsState()
                if (!isAutoReviving && !rewardedAdActive) {
                    // Pantalla de cristal semitransparente directamente sobre el tablero atascado (aún
                    // visible). El aviso "No hay espacio" ya se mostró centrado en el tablero antes de
                    // entrar aquí, así que no hay intro previa.
                    val adReady by AdManager.isReadyFlow.collectAsState()
                    // (Aquí se ramificaba la oferta de revive POR TIEMPO agotado (⏱ +30 s). Murió
                    //  con «La Prensa»: el reloj ya no mata, así que el único revive que existe es
                    //  el de FALTA DE ESPACIO — un solo camino, sin icono ni copy de reloj.)
                    // Internet validado: habilita la ruta del anuncio. Se evalúa una vez al abrir.
                    val hasInternet = remember { com.cukiti.teikafusion.util.NetworkUtil.isOnline(context) }
                    // Compuerta: sin forma de revivir (sin internet y sin monedas) → directo a la
                    // animación de derrota.
                    LaunchedEffect(Unit) {
                        if ((!hasInternet || !rewardedOffered) &&
                            gameState.teikoins < com.cukiti.teikafusion.model.LevelBudget.REVIVE_COST_TEIKOINS
                        ) viewModel.declineRevive()
                    }
                    // Si hay internet pero el anuncio aún no cargó, dispara una carga para que el
                    // botón "Cargando anuncio…" se resuelva en vez de quedar girando indefinidamente.
                    LaunchedEffect(adReady, hasInternet, rewardedOffered) {
                        if (!adReady && hasInternet && rewardedOffered) AdManager.loadAd(context)
                    }
                    ReviveOverlay(
                        teikoins = gameState.teikoins,
                        canWatchAd = adReady,
                        // Estado "cargando": hay internet y el anuncio aún se está cargando. La
                        // condición existe porque esto giraba ETERNAMENTE cuando no había ningún
                        // vídeo que fuera a cargar nunca; hoy la pregunta correcta es
                        // `rewardedOffered` (kill-switch remoto y consentimiento), porque haber
                        // comprado «Quitar anuncios» ya no apaga el recompensado.
                        adLoading = !adReady && hasInternet && rewardedOffered,
                        cause = gameState.defeatCause,
                        onReviveAd = {
                            val activity = context as? Activity
                            if (activity != null) {
                                // Marcar el anuncio como activo ANTES de mostrarlo: así el onPause que
                                // provoca abrir la activity del anuncio no descuenta vida, y el overlay
                                // (con su temporizador) se oculta mientras dura el anuncio.
                                viewModel.setRewardedAdActive(true)
                                // El callback de recompensa se dispara mientras el anuncio AÚN está en
                                // pantalla. Si revivimos ahí, el barrido de líneas ocurre tapado por el
                                // anuncio y, al cerrarlo, ya terminó. Diferimos reviveGame() a onClosed
                                // para que el jugador VEA romperse las filas al volver y sienta la
                                // satisfacción.
                                var earnedReward = false
                                AdManager.showRewardedAd(
                                    activity,
                                    onRewarded = { earnedReward = true },
                                    onClosed = {
                                        viewModel.setRewardedAdActive(false)
                                        // Revive por ESPACIO: barrido clásico de filas.
                                        if (earnedReward) viewModel.reviveGame(spendCoins = false)
                                    },
                                    placement = "revive"
                                )
                            }
                        },
                        onReviveCoins = { viewModel.reviveGame(spendCoins = true) },
                        onTimeout = { viewModel.declineRevive() },
                        onDecline = { viewModel.declineRevive() }
                    )
                }
            }
        }

        // ── HABILIDADES: tirador bajo la bandeja + hoja que sube ─────────────────────
        // Va aquí, sobre el tablero pero DEBAJO de pausa/derrota: es un control de partida, no un
        // modal de sistema. El tirador se ancla al hueco QUE HAY BAJO LA BANDEJA (medido, no
        // estimado) y se retira mientras el jugador arrastra una ficha: una habilidad que remueve el
        // tablero entero con una pieza en el aire deja al jugador soltando a ciegas.
        val handleBottomPx = remember(trayRectWindow, rootOffset, rootHeightPx) {
            if (trayRectWindow == Rect.Zero || rootHeightPx <= 0f) 0f
            else (rootHeightPx - (trayRectWindow.bottom - rootOffset.y) - with(density) { 46.dp.toPx() })
                .coerceAtLeast(with(density) { 8.dp.toPx() })
        }
        // LECCION 7: se le PRESTA a Cristobal con una carga, porque el jugador todavia no lo tiene
        // desbloqueado ni lo lleva en el cinturon — los dos requisitos que la hoja pide y que, por
        // definicion, no puede cumplir aun. Ver `GameViewModel.isTutorialAbilityCast`.
        val tutorialAbilityId = tutorialSpecNow?.abilityCompanionId
        // P-282 (2026-09-24): el BOTÓN DE POWER-UPS sustituye al tirador y a la hoja que subía. Muestra
        // los tres power-ups elegidos en el panel del nivel, en el orden de sus huecos. Si no se
        // eligió ninguno no hay botón: no se cuelan los 14 por la puerta de atrás.
        // ── P-349 · EL BANCO DE BLOQUES ─────────────────────────────────────────────────────────────
        // Abajo a la IZQUIERDA, espejo del botón de power-ups: misma altura de centro y el mismo margen
        // al borde (14 dp), así la fila de abajo es simétrica y la de power-ups, que se abre hacia la
        // izquierda, no llega a taparlo (`art/holdespacio.jpeg`).
        // Con una VENTANA DE ESTADO puesta (nivel completado, derrota, revivir) los dos botones de abajo
        // se retiran (2026-10-01, captura del usuario: el banco se veía entero ENCIMA del velo de
        // «NIVEL 208 COMPLETADO»). Se declaran después de esas ventanas, así que se pintaban sobre
        // ellas — el mismo fallo que ya tuvo la capa de enseñanza (ver `teachingLayerVisible`).
        val stateWindowUp = showOverlay
        if (com.cukiti.teikafusion.model.BlockBankRules.appliesTo(gameState.level) && !stateWindowUp) {
            val powerBottomPx = (handleBottomPx - with(density) { 12.dp.toPx() })
                .coerceAtLeast(with(density) { 6.dp.toPx() })
            // El botón de power-ups centra su cuadro de 46 dp 19 dp por encima de `bottomPx` (ver
            // `PowerUpButtonLayer`): el banco pone su borde inferior 4 dp por debajo para compartir centro.
            val bankBottomDp = (with(density) { powerBottomPx.toDp() } - 4.dp).coerceAtLeast(4.dp)
            val bankEnabled = gameState.status == com.cukiti.teikafusion.model.GameStatus.PLAYING &&
                companionCinematic == null
            val boardBusyNow by viewModel.boardBusy.collectAsState()
            if (!bankOwnedNow) BlockBankLockedButton(
                onClick = { viewModel.soundManager.playUiClick(); bankOfferOpen = true },
                onPositioned = { r -> bankRectRef[0] = r },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 14.dp, bottom = bankBottomDp)
            ) else AutoBlockBankButton(
                bank = gameState.blockBank,
                placements = gameState.placementsInLevel,
                enabled = bankEnabled && !boardBusyNow && viewModel.canBuildFromBank(),
                onClick = { viewModel.soundManager.playUiClick(); bankWorkshopOpen = true },
                onPositioned = { r -> bankRectRef[0] = r },
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 14.dp, bottom = bankBottomDp)
            )
        }
        if (!stateWindowUp && com.cukiti.teikafusion.model.ClawRules.appliesTo(gameState.level)) {
            val clawAim by viewModel.clawAim.collectAsState()
            val clawBusy by viewModel.boardBusy.collectAsState()
            ClawControls(gameState.clawCharged,clawAim,
                enabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && !clawBusy && companionCinematic==null && !bankWorkshopOpen,
                confirmEnabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && viewModel.previewClaw()!=null,
                start={ viewModel.beginClawAim() },cancel=viewModel::cancelClawAim,confirm={ viewModel.confirmClaw() },
                modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=(with(density) { handleBottomPx.toDp() }-16.dp).coerceAtLeast(4.dp)),transporting=gameState.clawTrip!=null)
        }
        if (!stateWindowUp && com.cukiti.teikafusion.model.WreckingBallRules.appliesTo(gameState.level)) {
            val ballAim by viewModel.ballAim.collectAsState()
            val ballBusy by viewModel.boardBusy.collectAsState()
            BallControls(gameState.ballCharged,ballAim,
                enabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && gameState.ballCharged &&
                    !ballBusy && companionCinematic==null && !bankWorkshopOpen,
                confirmEnabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && viewModel.previewBall()!=null,
                start={ viewModel.beginBallAim() },cancel=viewModel::cancelBallAim,
                confirm={ viewModel.confirmBall() },
                modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=(with(density) { handleBottomPx.toDp() }-16.dp).coerceAtLeast(4.dp)))
        }
        if (!stateWindowUp && com.cukiti.teikafusion.model.ForgeRules.appliesTo(gameState.level)) {
            val aim by viewModel.forgeAim.collectAsState()
            val busy by viewModel.boardBusy.collectAsState()
            ForgeControls(gameState.forgeCharged,aim,
                enabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && gameState.forgeCharged && !busy && companionCinematic==null && !bankWorkshopOpen,
                confirmEnabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && viewModel.previewForge()!=null,
                start={ viewModel.beginForgeAim() },cancel=viewModel::cancelForgeAim,
                changeStage=viewModel::toggleForgeStage,confirm={ viewModel.confirmForge() },
                modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=(with(density) { handleBottomPx.toDp() }-16.dp).coerceAtLeast(4.dp)))
        }
        if (!stateWindowUp && com.cukiti.teikafusion.model.ReactorRules.appliesTo(gameState.level)) {
            val reactorAim by viewModel.reactorAim.collectAsState()
            val reactorBusy by viewModel.boardBusy.collectAsState()
            ReactorControls(gameState.reactorCharged,reactorAim,
                enabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && gameState.reactorCharged &&
                    !reactorBusy && companionCinematic==null && !bankWorkshopOpen,
                confirmEnabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && viewModel.previewReactor()!=null,
                start={ viewModel.beginReactorAim() },cancel=viewModel::cancelReactorAim,
                confirm={ viewModel.confirmReactor() },
                modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=(with(density) { handleBottomPx.toDp() }-16.dp).coerceAtLeast(4.dp)))
        }
        if (!stateWindowUp && com.cukiti.teikafusion.model.TurbineRules.appliesTo(gameState.level)) {
            val aim by viewModel.turbineAim.collectAsState()
            val busy by viewModel.boardBusy.collectAsState()
            TurbineControls(gameState.turbineCharged,aim,gameState.turbineGust,
                enabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && gameState.turbineCharged && !busy && companionCinematic==null && !bankWorkshopOpen,
                confirmEnabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && viewModel.previewTurbine()!=null,
                start={ viewModel.beginTurbineAim() },cancel=viewModel::cancelTurbineAim,
                confirm={ viewModel.confirmTurbine() },
                modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=(with(density) { handleBottomPx.toDp() }-16.dp).coerceAtLeast(4.dp)))
        }
        if (!stateWindowUp && com.cukiti.teikafusion.model.OrbitRules.appliesTo(gameState.level)) {
            val aim by viewModel.orbitAim.collectAsState()
            val busy by viewModel.boardBusy.collectAsState()
            OrbitControls(gameState.orbitCharged,aim,
                enabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && gameState.orbitCharged && !busy && companionCinematic==null && !bankWorkshopOpen,
                confirmEnabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && viewModel.previewOrbit()!=null,
                start={ viewModel.beginOrbitAim() },cancel=viewModel::cancelOrbitAim,
                confirm={ viewModel.confirmOrbit() },toggle=viewModel::toggleOrbitDirection,
                modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=(with(density) { handleBottomPx.toDp() }-16.dp).coerceAtLeast(4.dp)))
        }
        if (!stateWindowUp && com.cukiti.teikafusion.model.PortalSwapRules.appliesTo(gameState.level)) {
            val portalAim by viewModel.portalSwapAim.collectAsState()
            val portalBusy by viewModel.boardBusy.collectAsState()
            PortalSwapControls(gameState.portalSwapCharged,portalAim,
                enabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && gameState.portalSwapCharged &&
                    !portalBusy && companionCinematic==null && !bankWorkshopOpen,
                confirmEnabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING && viewModel.previewPortalSwap()!=null,
                start={ viewModel.beginPortalSwapAim() },cancel=viewModel::cancelPortalSwapAim,
                confirm={ viewModel.confirmPortalSwap() },
                modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=(with(density) { handleBottomPx.toDp() }-16.dp).coerceAtLeast(4.dp)))
        }
        if (!stateWindowUp && com.cukiti.teikafusion.model.CompactorRules.appliesTo(gameState.level)) {
            val compactAim by viewModel.compactorAiming.collectAsState()
            val compactBusy by viewModel.boardBusy.collectAsState()
            CompactorButton(gameState.compactorCharged,compactAim,
                enabled=gameState.status==com.cukiti.teikafusion.model.GameStatus.PLAYING &&
                    (compactAim || (gameState.compactorCharged && !compactBusy && companionCinematic==null && !bankWorkshopOpen)),
                onClick={ if(compactAim) viewModel.cancelCompactorAim() else viewModel.beginCompactorAim() },
                modifier=Modifier.align(Alignment.BottomCenter).padding(bottom=(with(density) { handleBottomPx.toDp() }-16.dp).coerceAtLeast(4.dp)))
        }
        if (!stateWindowUp) PowerUpButtonLayer(
            ids = when {
                tutorialAbilityId != null -> listOf(tutorialAbilityId)
                // En el TUTORIAL el botón solo existe en la lección que lo enseña.
                tutorialLevel -> emptyList()
                else -> companionBelt.slots.filter { it in claimedCompanionIds }
            },
            charges = if (tutorialAbilityId != null)
                com.cukiti.teikafusion.model.CompanionCharges(mapOf(tutorialAbilityId to 1))
            else gameState.routeFreeCompanionId?.let { companionCharges.plus(it) } ?: companionCharges,
            freeCompanionId=gameState.routeFreeCompanionId,
            canCast = { viewModel.canCastCompanionAbility(it) },
            teikoins = gameState.teikoins,
            // Un poco POR DEBAJO de la bandeja (2026-09-25, captura del usuario `partidaboton`): con el
            // borde superior justo en el de la bandeja, el botón chocaba con la tercera ranura.
            bottomPx = (handleBottomPx - with(density) { 12.dp.toPx() })
                .coerceAtLeast(with(density) { 6.dp.toPx() }),
            enabled = gameState.status == com.cukiti.teikafusion.model.GameStatus.PLAYING &&
                dragState !is DragState.Dragging &&
                companionCinematic == null,
            onCast = { viewModel.activateCompanionAbility(it) },
            onBuy = { viewModel.buyCompanionCharge(it) },
            onUiClick = { viewModel.soundManager.playUiClick() },
            onExpanded = { boosterProgress.floatValue = it },
            onButtonPositioned = { r -> if (abilityHandleRect != r) abilityHandleRect = r }
        )
        // P-270 · EL ECO: el lanzamiento no gastó la carga. Único consumidor de `AbilityEcho`.
        val currentEcho = remember(eventBuckets) {
            eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.AbilityEcho>().firstOrNull()
        }
        if (currentEcho != null) {
            AbilityEchoLabel(
                eventId = currentEcho.id,
                companionId = currentEcho.companionId,
                anchorBottomEnd = if (abilityHandleRect == Rect.Zero) Offset.Zero
                    else abilityHandleRect.bottomRight - rootOffset,
                onDone = { viewModel.consumeEvent(currentEcho.id) },
            )
        }

        // ── EL BANCO DE PAGO (2026-09-28): la oferta del candado ─────────────────────────────────────
        if (bankOfferOpen && !bankOwnedNow) {
            ConfirmOverlay(
                title = "FICHONA",
                message = "Crea Fichona: una mega ficha a medida de la mayor zona libre. " +
                    "Viene en el Paquete inicial de la tienda, o desbloquéalo para siempre:",
                confirmLabel = "AHORA NO",
                onConfirm = { viewModel.soundManager.playUiClick(); bankOfferOpen = false },
                onDismiss = { bankOfferOpen = false },
                dismissLabel = null,
            ) {
                PriceButton(
                    price = com.cukiti.teikafusion.model.StorePacks.BLOCK_BANK_PRICE,
                    onBuy = { viewModel.buyBlockBank().also { ok -> if (ok) bankOfferOpen = false } },
                    enabled = gameState.teikoins >= com.cukiti.teikafusion.model.StorePacks.BLOCK_BANK_PRICE,
                    height = 44.dp,
                    fontSize = 15.sp,
                    contentDescription = "Desbloquear Fichona por " +
                        "${com.cukiti.teikafusion.model.StorePacks.BLOCK_BANK_PRICE} teikoins",
                )
            }
        }
        if (bankWorkshopOpen && gameState.status == com.cukiti.teikafusion.model.GameStatus.PLAYING) {
            AutoBlockWorkshop(
                state = gameState,
                reducedEffects = reducedFx,
                loadOffer = { viewModel.workshopOffer() },
                onBuild = { offer -> viewModel.craftWorkshop(offer) },
                onCommitted = { pieces -> workshopHeldIds = pieces.mapTo(mutableSetOf()) { it.id }; Haptics.place() },
                onDelivered = { pieces, starts ->
                    viewModel.soundManager.playTrayReward()
                    Haptics.objectiveLand(isMega = true)
                    workshopDelivery = WorkshopDelivery(pieces, starts)
                    bankWorkshopOpen = false
                },
                onDismiss = { viewModel.soundManager.playUiClick(); bankWorkshopOpen = false },
            )
        }
        workshopDelivery?.let { delivery ->
            // La entrega todavía posee las fichas: bloquea toques hasta que aparezcan en la bandeja.
            Box(Modifier.fillMaxSize().zIndex(GameLayers.WINDOW).clickable(
                interactionSource = remember { MutableInteractionSource() }, indication = null,
            ) {})
            WorkshopDeliveryVFX(delivery, pieceTopLefts, trayPieceCellPx, rootOffset, reducedFx) {
                workshopDelivery = null
                workshopHeldIds = emptySet()
            }
        }
        // ── TIEMPO BALA (2026-09-01) ────────────────────────────────────────────────
        // Va SOBRE el juego y las habilidades, y DEBAJO de pausa/derrota: es una capa de enseñanza,
        // no un modal de sistema. No traga toques (ver `SlowMotionOverlay`), así que en la lección
        // de habilidades el jugador puede tocar el tirador que el propio foco le está señalando.
        val slowMotionShot by viewModel.slowMotion.collectAsState()
        // La capa de ensenanza NO puede convivir con una ventana de estado (2026-09-01, grabacion
        // `analisistutorial`): se declara «debajo de pausa/derrota» pero se dibuja despues de ellas,
        // asi que su velo, su aro de foco y su rotulo caian ENCIMA del panel de «leccion completada»
        // —el aro cruzando el borde de la ventana y el rotulo tapandole el titulo—. El reloj de la
        // victoria ya espera a que el instante termine (`awaitSlowMotionIdle`); esto es el cinturon:
        // en cuanto hay ventana, la ensenanza se retira.
        val teachingLayerVisible = slowMotionShot != null &&
            gameState.status != com.cukiti.teikafusion.model.GameStatus.LEVEL_COMPLETE &&
            gameState.status != com.cukiti.teikafusion.model.GameStatus.GAME_OVER &&
            gameState.status != com.cukiti.teikafusion.model.GameStatus.PAUSED
        SlowMotionOverlay(
            shot = slowMotionShot.takeIf { teachingLayerVisible },
            focusRects = slowMotionShot?.takeIf { teachingLayerVisible }?.let { shot ->
                slowMotionFocusRects(
                    shot = shot,
                    boardOffset = boardOffsetPx,
                    cellSizePx = cellSizePx,
                    boardCols = boardCols,
                    boardRows = boardRows,
                    trayRect = trayRectWindow,
                    trayPieceRects = trayPieceRectsWindow,
                    abilityTabRect = abilityHandleRect,
                    goalRect = goalBarRectWindow,
                    objectivePanelRect = objectivePanelRectWindow,
                    movesRect = movesMedallionRectWindow,
                    bankRect = bankRectRef[0],
                    rootOffset = rootOffset
                )
            }.orEmpty()
        )

        if (gameState.status == com.cukiti.teikafusion.model.GameStatus.PAUSED) Box(Modifier.fillMaxSize().zIndex(GameLayers.WINDOW)) {
            PauseOverlay(
                musicVolume = musicVolume,
                sfxVolume = sfxVolume,
                vibrationEnabled = vibrationEnabled,
                tiltEnabled = tiltEnabled,
                isMuted = isMuted,
                reducedFx = reducedFx,
                notificationsEnabled = notificationsEnabled,
                onMusicVolume = { viewModel.setMusicVolume(it) },
                onSfxVolume = { viewModel.setSfxVolume(it) },
                onSfxPreview = { viewModel.soundManager.playUiClick() },
                onToggleVibration = { viewModel.soundManager.playUiClick(); viewModel.toggleVibration() },
                onToggleTilt = { viewModel.soundManager.playUiClick(); viewModel.toggleTilt() },
                onToggleMute = { viewModel.soundManager.playUiClick(); viewModel.toggleMute() },
                onToggleReducedFx = { viewModel.soundManager.playUiClick(); viewModel.toggleReducedFx() },
                onToggleNotifications = { viewModel.soundManager.playUiClick(); viewModel.toggleNotifications() },
                onResume = { viewModel.soundManager.playUiClick(); viewModel.resumeGame() },
                onRestart = { viewModel.soundManager.playUiClick(); viewModel.restartGame() },
                onGoHome = { viewModel.soundManager.playUiClick(); Iris.run { viewModel.goHome() } },
                restartCostsLife = hasSavedGameNow && !tutorialLevel,
                levelRules = remember(gameState.level, bankOwnedNow) { com.cukiti.teikafusion.model.LevelRules.forLevel(gameState.level, bankOwnedNow) },
                onDismiss = { viewModel.resumeGame() }
            )
        }

        if (com.cukiti.teikafusion.model.TutorialCampaign.isTutorialLevel(gameState.level) &&
            gameState.status == com.cukiti.teikafusion.model.GameStatus.PLAYING
        ) {
            // La FICHA GUIADA de la lección: de ella dependen las dos señales del overlay.
            val guidedPieceId = com.cukiti.teikafusion.model.TutorialCampaign
                .spec(gameState.level)?.piece?.id
            ProgressiveTutorialOverlay(
                level = gameState.level,
                // «Colocada» = la ficha guiada YA NO ESTÁ EN LA BANDEJA (2026-09-04). Antes era «la
                // bandeja está vacía», que es lo mismo solo mientras nadie reponga: cualquier
                // camino que reparta fichas (la habilidad de la lección 7, la red anti-atasco)
                // devolvía la sombra del destino a un tablero donde ya no había nada que colocar
                // ahí. La pregunta correcta es por la ficha, no por el recuento.
                piecePlaced = guidedPieceId == null ||
                    gameState.availablePieces.none { it.id == guidedPieceId },
                pieceGrabbed = draggedPieceLocal?.id == guidedPieceId,
                tutorialStage = tutorialStage,
                tiltDirection = tutorialTiltDirection,
                stageReady = tutorialStageReady,
                positions = TutorialPositions(
                    rootOffset = rootOffset,
                    board      = Rect(offset = boardOffsetPx, size = Size(cellSizePx * 8f, cellSizePx * 8f)),
                    tray       = trayRectWindow,
                    piece      = tutorialPieceRectWindow,
                    // Para que la pildora de progreso no se siente encima del tirador de
                    // habilidades, que es justo el boton que resuelve la leccion 7.
                    abilityHandle = abilityHandleRect
                ),
                misplaceToken = tutorialMisplaceToken,
                misplaceMessage = tutorialMisplaceMessage,
                // La pildora de progreso se retira mientras hay un instante de ensenanza: las dos
                // viven bajo la bandeja y en el video se pisaban.
                //
                // …y tambien con la HOJA de habilidades ABIERTA (2026-09-02): al abrirse, el tirador
                // sale de la composicion y su rectangulo se queda con el ultimo valor medido, asi que
                // la pildora se plantaba encima de la tarjeta de Cristobal y le tapaba justo la linea
                // que explica que hace. Se ve en la grabacion `analisistutorial` en el minuto 1:24.
                teachingShotActive = slowMotionShot != null || boosterOpen,
            )
        }
        
        // ── Character reveal overlay ──────────────────────────────────────────
        val capturedReveal = revealedCharacter
        if (capturedReveal != null && revealAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(GameLayers.REVEAL)
                    .background(Color.Black.copy(alpha = 0.85f * revealAlpha.value))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            coroutineScope.launch {
                                revealAlpha.animateTo(0f, androidx.compose.animation.core.tween(300))
                                revealedCharacter = null
                                revealSparkleAngle.snapTo(0f)
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Sparkles Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(revealAlpha.value)
                ) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val angleRad = Math.toRadians(revealSparkleAngle.value.toDouble())
                    val sparkleColors = listOf(
                        Color(0xFFFFD700), Color(0xFF4DB8FF), Color(0xFFB57EDC),
                        Color(0xFF69F0AE), Color(0xFFFF6B9D), Color(0xFFFFFFFF)
                    )
                    val rings = listOf(130f, 190f, 250f)
                    rings.forEachIndexed { ringIdx, radius ->
                        val count = 8 + ringIdx * 4
                        repeat(count) { i ->
                            val theta = angleRad + Math.toRadians((360.0 / count) * i + ringIdx * 22.5)
                            val sparkX = cx + (radius * kotlin.math.cos(theta)).toFloat()
                            val sparkY = cy + (radius * kotlin.math.sin(theta)).toFloat()
                            val sparkColor = sparkleColors[(i + ringIdx) % sparkleColors.size]
                            val sparkSize = if (i % 3 == 0) 6f else 4f
                            drawCircle(
                                color = sparkColor.copy(alpha = 0.8f),
                                radius = sparkSize,
                                center = androidx.compose.ui.geometry.Offset(sparkX, sparkY)
                            )
                            // Rayito saliendo del centro
                            val lineStart = androidx.compose.ui.geometry.Offset(
                                cx + ((radius - 18f) * kotlin.math.cos(theta)).toFloat(),
                                cy + ((radius - 18f) * kotlin.math.sin(theta)).toFloat()
                            )
                            drawLine(
                                color = sparkColor.copy(alpha = 0.35f),
                                start = lineStart,
                                end = androidx.compose.ui.geometry.Offset(sparkX, sparkY),
                                strokeWidth = 1.5f
                            )
                        }
                    }
                }

                // Tarjeta central
                Column(
                    modifier = Modifier
                        .scale(revealScale.value)
                        .alpha(revealAlpha.value)
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1F2B5E), Color(0xFF0D1230))
                            )
                        )
                        .border(
                            2.dp,
                            Brush.horizontalGradient(
                                listOf(Color(0xFF4DB8FF), Color(0xFFB57EDC), Color(0xFF4DB8FF))
                            ),
                            RoundedCornerShape(24.dp)
                        )
                        .padding(horizontal = 28.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "✨ ¡NUEVO PERSONAJE! ✨",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFFFFD700),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        letterSpacing = 1.sp
                    )

                    // Avatar grande del personaje
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White.copy(alpha = 0.07f)),
                        contentAlignment = Alignment.Center
                    ) {
                        CharacterRevealIcon(
                            resId    = capturedReveal.lottieResId,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }

                    // Nombre del personaje
                    Text(
                        text = capturedReveal.name.uppercase(),
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 3.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    // Descripción
                    Text(
                        text = "\"${capturedReveal.description}\"",
                        fontSize = 12.sp,
                        color = Color(0xFF8899CC),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Botón de cerrar
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF4DB8FF), Color(0xFFB57EDC))
                                )
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                coroutineScope.launch {
                                    revealAlpha.animateTo(0f, androidx.compose.animation.core.tween(300))
                                    revealedCharacter = null
                                    revealSparkleAngle.snapTo(0f)
                                }
                            }
                            .padding(horizontal = 28.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "🎉 ¡GENIAL!",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Combo ÉPICO — combos altos (tier ≥ EPIC_COMBO_TIER) reciben la celebración a pantalla
        // completa (god-rays + slow-mo + texto gigante), escalando progresivamente hasta TEIKA MAX.
        //
        // Dos reglas de flujo:
        //  · SIEMPRE el ÚLTIMO combo: cada nuevo évento épico SUSTITUYE al anterior al instante
        //    (key(id) reinicia la cinemática) — encadenar ×7, ×8, ×9… se ve como una escalada
        //    continua donde el rótulo vivo es siempre el combo más reciente, incluso con el tier
        //    saturado en 9 (el ×N sigue creciendo y la celebración sigue escalando con la racha).
        //    El id-gate (`lastHandledEpicId`) garantiza que cada évento se decide UNA sola vez,
        //    así que el techo no re-dispara en bucle el mismo évento ("se repiten y no dejan de
        //    salir" venía de re-lanzar por recomposición, no por évento nuevo).
        //  · Nunca por ENCIMA del panel de fin de nivel ("se quedan al frente del panel completado"):
        //    con un overlay terminal (LEVEL_COMPLETE/GAME_OVER/REVIVE_OFFER) no se dibuja y el évento
        //    en curso se retira al instante.
        val epicOverlayUp = gameState.status == com.cukiti.teikafusion.model.GameStatus.LEVEL_COMPLETE ||
            gameState.status == com.cukiti.teikafusion.model.GameStatus.GAME_OVER ||
            gameState.status == com.cukiti.teikafusion.model.GameStatus.REVIVE_OFFER

        val currentEpicCombo = remember(eventBuckets) {
            eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.ComboText>()
                .lastOrNull { it.tier >= EPIC_COMBO_TIER }
        }
        var lastHandledEpicId by remember { mutableStateOf<String?>(null) }
        // Instante del último épico: si el siguiente llega pisándole los talones, se pinta en
        // versión ligera (sin flash ni scrim a pantalla completa). Ver EpicComboVFX(chained).
        var lastEpicAtMs by remember { mutableLongStateOf(0L) }
        var epicChained by remember { mutableStateOf(false) }
        var epicToRender by remember { mutableStateOf<com.cukiti.teikafusion.model.GameEvent.ComboText?>(null) }

        // Decide UNA vez por évento (por id): el nuevo combo reemplaza al que estuviera en pantalla.
        LaunchedEffect(currentEpicCombo?.id) {
            val e = currentEpicCombo ?: return@LaunchedEffect
            if (e.id == lastHandledEpicId) return@LaunchedEffect
            lastHandledEpicId = e.id
            if (!epicOverlayUp) {
                // El anterior aún vivo se consume YA: su cinemática muere al cambiar el key(id).
                epicToRender?.let { prev -> if (prev.id != e.id) viewModel.consumeEvent(prev.id) }
                val now = System.currentTimeMillis()
                epicChained = now - lastEpicAtMs < EPIC_CHAIN_WINDOW_MS
                lastEpicAtMs = now
                epicToRender = e
            } else {
                viewModel.consumeEvent(e.id)   // panel terminal al frente: sin cinemática
            }
        }

        // Si un panel terminal aparece con una cinemática épica en curso, se retira al instante.
        LaunchedEffect(epicOverlayUp) {
            if (epicOverlayUp) {
                epicToRender?.let { viewModel.consumeEvent(it.id) }
                epicToRender = null
            }
        }

        if (!epicOverlayUp) {
            epicToRender?.let { ec ->
                key(ec.id) {
                    EpicComboVFX(
                        tier = ec.tier,
                        streak = ec.streak,
                        chained = epicChained,
                        // La bandeja queda FUERA del apagon: es un mando, no decorado. Se mide de
                        // verdad (rect de la bandeja en coordenadas de la raiz) en vez de estimar
                        // una banda fija, porque el alto del panel cambia con la pantalla.
                        protectedBottomPx = if (trayRectWindow == Rect.Zero || rootHeightPx <= 0f) 0f
                            else (rootHeightPx - (trayRectWindow.top - rootOffset.y))
                                .coerceIn(0f, rootHeightPx * 0.5f),
                        onComplete = {
                            viewModel.consumeEvent(ec.id)
                            epicToRender = null
                        }
                    )
                }
            }
        }

        // (La cinemática de entrada del personaje se retiró: al castear una habilidad NO se
        // muestra el retrato gigante ni el banner — solo corre el VFX temático sobre el tablero.)

        // P-369: si la partida va a tirones SOSTENIDOS (6 s por debajo de 40 fps), «Reducir efectos» se
        // activa solo, una vez por aparato. Solo mide jugando: ni en pausa ni fuera del tablero.
        val autoReducedFxDone by viewModel.autoReducedFxDone.collectAsState()
        JankWatchDriver(
            active = gameState.status == com.cukiti.teikafusion.model.GameStatus.PLAYING &&
                !reducedFx && !autoReducedFxDone,
            onJank = viewModel::onSustainedJank,
        )

        // (P-371, 2026-09-30) El contador de FPS ya no se monta aquí: lo pinta `AppNavigation` encima de
        // todo cuando se enciende desde Ajustes (ver [fpsOverlayOn]).

        // Cristal / Escudo: piezas que vuelan SOLAS de la bandeja a su mejor hueco.
        val crystalFlights = eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.CrystalFlight>()
        if (crystalFlights.isNotEmpty() && boardOffsetPx != Offset.Zero) {
            Box(modifier = Modifier.fillMaxSize()) {
                crystalFlights.forEach { ev ->
                    key(ev.id) {
                        CrystalFlightVfx(
                            event         = ev,
                            boardOffsetPx = boardOffsetPx,
                            rootOffset    = rootOffset,
                            cellSizePx    = cellSizePx,
                            cellSize      = cellSize,
                            trayTopLeft   = pieceTopLefts[ev.pieceId],
                            onComplete    = { viewModel.consumeEvent(ev.id) }
                        )
                    }
                }
            }
        }

        // ── Skin DERRUMBE: rotura a pantalla COMPLETA, por encima de la bandeja ───────────────────
        // Los cubos se desprenden y caen hasta salir del juego por abajo (pedido 2026-07-20). Montado
        // aquí —y no en GameBoard— para que la caída pase POR ENCIMA de la bandeja de fichas y del
        // resto de la UI en vez de meterse detrás. El Box interior se desplaza a la esquina del
        // tablero, así que el VFX conserva su sistema de coordenadas (celda 0,0 = origen).
        // Contrato de un solo consumidor: GameBoard NO monta el shatter con este skin.
        if (LocalBlockSkin.current.id == BlockSkins.fall3d.id && boardOffsetPx != Offset.Zero) {
            val fallShatterEvents = eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.LineCellShatter>()
            if (fallShatterEvents.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (boardOffsetPx.x - rootOffset.x).roundToInt(),
                                    (boardOffsetPx.y - rootOffset.y).roundToInt()
                                )
                            }
                            // Tamaño EXACTO del tablero: el Canvas del VFX es fillMaxSize y su
                            // origen debe caer en la celda (0,0), no en la esquina de la pantalla.
                            .width(cellSize * boardCols)
                            .height(cellSize * boardRows)
                    ) {
                        BatchLineCellShatterVFX(
                            events = fallShatterEvents,
                            cellSize = cellSize,
                            onEventComplete = { id -> viewModel.consumeEvent(id) }
                        )
                    }
                }
            }
        }

        // TargetCollect fly-to-counter overlay (full-screen, above everything)
        val targetEvents = eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.TargetCollected>()
        // Sin estrellas en vuelo → el conteo mostrado se pone al día con el real (rescata cualquiera que
        // no haya animado, p. ej. si el tablero aún no tenía posición cuando reventó).
        LaunchedEffect(targetEvents.isEmpty(), gameState.targetsCollected) {
            if (targetEvents.isEmpty() && displayedTargets != gameState.targetsCollected) {
                displayedTargets = gameState.targetsCollected
            }
        }
        if (targetEvents.isNotEmpty() && boardOffsetPx != Offset.Zero) {
            Box(modifier = Modifier.fillMaxSize()) {
                targetEvents.forEach { event ->
                    key(event.id) {
                        val fromPx = Offset(
                            boardOffsetPx.x + (event.fromGridPos.x + 0.5f) * cellSizePx - rootOffset.x,
                            boardOffsetPx.y + (event.fromGridPos.y + 0.5f) * cellSizePx - rootOffset.y
                        )
                        val counterRect = targetCounterRects[event.color]
                        val toPx = if (counterRect != null) {
                            Offset(counterRect.center.x - rootOffset.x, counterRect.center.y - rootOffset.y)
                        } else fromPx

                        TargetCollectVFX(
                            fromPx      = fromPx,
                            toPx        = toPx,
                            targetColor = event.color,
                            cellSizePx  = cellSizePx,
                            delayMs     = event.delayMs,
                            onComplete  = {
                                // La estrella llegó al panel → AHORA baja el número (1 por estrella),
                                // sin pasarse del real. Dispara la animación premium del chip.
                                val real = gameState.targetsCollected[event.color] ?: 0
                                val cur  = displayedTargets[event.color] ?: 0
                                if (cur < real) {
                                    displayedTargets = displayedTargets + (event.color to (cur + 1))
                                    // RECOGER UNA ESTRELLA VUELVE A SONAR (2026-08-09). `playTargetCollect`
                                    // solo lo llamaba el adelantamiento de rival de WALLBLOCK, y al irse el
                                    // modo se quedó sin llamante — es decir, el objetivo del juego entero
                                    // aterrizaba en el contador en silencio. Aquí, al ATERRIZAR, que es
                                    // donde el jugador ve subir el número.
                                    viewModel.soundManager.playTargetCollect()
                                }
                                viewModel.consumeEvent(event.id)
                            },
                            modifier    = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // ── ESTRELLAS QUE MUTAN ──────────────────────────────────────────────────────────────────
        // Un color del objetivo quedó cubierto y sus estrellas huérfanas del tablero se retintaron al
        // que más falta (GameViewModel.withStarRetint). El estado YA está cambiado: esto solo avisa
        // de que ha pasado. Consumidor ÚNICO del evento.
        val retintEvents = eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.StarRetint>()
        if (retintEvents.isNotEmpty() && boardOffsetPx != Offset.Zero) {
            Box(modifier = Modifier.fillMaxSize()) {
                retintEvents.forEach { event ->
                    key(event.id) {
                        StarRetintVFX(
                            cells = event.cells,
                            boardOffsetPx = Offset(
                                boardOffsetPx.x - rootOffset.x,
                                boardOffsetPx.y - rootOffset.y
                            ),
                            cellSizePx = cellSizePx,
                            reducedFx = reducedFx,
                            onComplete = { viewModel.consumeEvent(event.id) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // TODOS LOS MODOS: el "+score" (no-combo) vuela en arco HASTA el marcador del top bar
        // (recolección dopaminérgica). El count-up + kick del marcador ya corren al cambiar el
        // score, ~concurrentes con el aterrizaje del número.
        // PROPIEDAD: esta pantalla es dueña del "+score" NO-combo; el de combo lo dibuja el tablero en
        // su propia zona (ver el reparto explicado en GameBoard). Un solo consumidor por evento.
        val scoreFlyAll = remember(eventBuckets) {
            eventBuckets.of<com.cukiti.teikafusion.model.GameEvent.FloatingScore>()
                .filter { !it.isCombo }
        }
        // P-90: sin marcador en pantalla estas cifras **no tienen adónde volar**, así que en el
        // tutorial no se lanza ninguna y se retiran todas. No es un detalle estético: el `if` de
        // abajo exige `scoreCounterRect != Rect.Zero`, de modo que con el marcador apagado los
        // eventos se quedarían en la cola hasta que los barriera el recolector de huérfanos (10 s),
        // que es exactamente el fallo sistémico que documenta CLAUDE.md. Siguen teniendo UN SOLO
        // consumidor: la lista de retirada de aquí abajo.
        val scoreFlyEvents = if (tutorialLevel) emptyList() else scoreFlyAll.takeLast(1)
        // Solo vuela el más reciente: los anteriores se retiran de la cola. Esta limpieza la hacía
        // antes GameBoard, que de paso se llevaba por delante el que SÍ estaba volando.
        val staleScoreFlyIds = remember(scoreFlyAll, tutorialLevel) {
            if (tutorialLevel) scoreFlyAll.map { it.id } else scoreFlyAll.dropLast(1).map { it.id }
        }
        LaunchedEffect(staleScoreFlyIds) {
            staleScoreFlyIds.forEach { viewModel.consumeEvent(it) }
        }
        if (scoreFlyEvents.isNotEmpty() && scoreCounterRect != Rect.Zero && boardOffsetPx != Offset.Zero) {
            Box(modifier = Modifier.fillMaxSize()) {
                scoreFlyEvents.forEach { event ->
                    key(event.id) {
                        val fromPx = Offset(
                            boardOffsetPx.x + (event.position.x + 0.5f) * cellSizePx - rootOffset.x,
                            boardOffsetPx.y + (event.position.y + 0.5f) * cellSizePx - rootOffset.y
                        )
                        val toPx = Offset(
                            scoreCounterRect.center.x - rootOffset.x,
                            scoreCounterRect.center.y - rootOffset.y
                        )
                        ScoreFlyVFX(
                            text       = "+${event.score}",
                            fromPx     = fromPx,
                            toPx       = toPx,
                            color      = Color(0xFF40C4FF),
                            onComplete = { viewModel.consumeEvent(event.id) },
                            modifier   = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        // (PODADO 2026-07-31, bloque 5) Aquí vivía el contador "2/3" de la FICHA FANTASMA. La
        // ficha murió con los pactos del Infinito: `ghostOverlappableCells` devuelve siempre vacío y
        // el único productor de `ghostStompCells` los rellenaba con `emptyList()`. Chip que ningún
        // jugador podía ver.

        if (showQuitDialog) Box(Modifier.fillMaxSize().zIndex(GameLayers.WINDOW)) {
            QuitDialogOverlay(
                onConfirmQuit  = {
                    viewModel.soundManager.playUiClick()
                    showQuitDialog = false
                    Iris.run { viewModel.quitGame() }
                },
                onCancel = { viewModel.soundManager.playUiClick(); showQuitDialog = false }
            )
        }



        // "No hay espacio" — derrota real al soltar una ficha sin encaje posible en ninguna rotación.
        // BANDA horizontal traslúcida que DESLIZA cruzando el centro del tablero (referencia Block
        // Blast) + viñeta roja latiendo en los bordes (NoSpaceBoardChill). Sin apagón global: el
        // resto del tablero queda iluminado — el jugador puede comprobar que, en efecto, no cabía
        // nada — y encadena de forma natural con el scrim de la oferta de revivir, ~1,1 s después.
        AnimatedVisibility(
            visible = noSpaceCenterVisible && boardOffsetPx != Offset.Zero,
            enter = fadeIn(tween(200)),
            exit  = fadeOut(tween(300))
        ) {
            val boardSizePx = cellSizePx * boardCols
            val boardSizeDp = with(density) { boardSizePx.toDp() }
            val boardHeightDp = with(density) { (cellSizePx * boardRows).toDp() }
            val boardLeft   = (boardOffsetPx.x - rootOffset.x).roundToInt()
            val boardTop    = (boardOffsetPx.y - rootOffset.y).roundToInt()
            // La banda entra deslizando desde la izquierda y asienta con un muelle suave.
            val bandSlide = remember { Animatable(-0.35f) }
            LaunchedEffect(Unit) {
                bandSlide.animateTo(0f, spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium))
            }
            Box(
                modifier = Modifier
                    .offset { IntOffset(boardLeft, boardTop) }
                    .width(boardSizeDp)
                    .height(boardHeightDp),
                contentAlignment = Alignment.Center
            ) {
                NoSpaceBoardChill(modifier = Modifier.matchParentSize())
                Box(
                    modifier = Modifier
                        .offset { IntOffset((bandSlide.value * boardSizePx).roundToInt(), 0) }
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0x001A1030), Color(0xE61A1030),
                                    Color(0xE61A1030), Color(0x001A1030)
                                )
                            )
                        )
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay espacio", color = Color.White.copy(alpha = 0.95f),
                        fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
                }
            }
        }

        // ── FASE 1 del atasco: el cartel «SIN SALIDA» sobre el tablero (P-105) ──────────────
        // Vive aquí, junto al aviso de «No hay espacio», porque es su hermano: el mismo instante del
        // juego contado de las dos maneras en que puede acabar.
        run {
            val out = deadEnd
            // Mientras el cartel está en pantalla se REPREGUNTA al motor cada 250 ms. No se puede
            // atar a un `LaunchedEffect(teikaBodies)`: los cuerpos cambian CADA FRAME (la física
            // corre con `withFrameNanos`) y eso reiniciaría el efecto sesenta veces por segundo. Un
            // sondeo cada cuarto de segundo cubre las tres formas de desatascarse —un Teika que
            // rueda, una habilidad que abre hueco, un castigo de bioma que pasa— y cuesta nada.
            LaunchedEffect(out) {
                surrenderVisible = false
                if (out == null) return@LaunchedEffect
                // El botón de rendirse entra tarde: el dedo que acaba de soltar la ficha está justo
                // encima, y una rendición accidental es la peor forma de perder un nivel.
                var elapsed = 0L
                while (true) {
                    kotlinx.coroutines.delay(250)
                    elapsed += 250
                    if (viewModel.evaluateDeadEnd() == null) { deadEnd = null; return@LaunchedEffect }
                    if (elapsed >= 2200 && !surrenderVisible) surrenderVisible = true
                }
            }
            // Y cualquier INTERACCIÓN lo despacha: es un consejo, no un modal. Coger (o girar) una
            // ficha, abrir los power-ups, tocar el BANCO (su taller o la selección: el cartel se pintaba
            // encima y tapaba lo que el jugador acababa de abrir), la cinemática de un lanzamiento, el
            // diálogo de salida o pausar lo retiran; vuelve a salir si intenta colocar otra vez y el
            // tablero sigue trancado.
            val interacting = dragState is DragState.Dragging ||
                boosterInteracting ||
                bankWorkshopOpen || workshopDelivery != null || bankOfferOpen ||
                companionCinematic != null ||
                showQuitDialog ||
                gameState.status != com.cukiti.teikafusion.model.GameStatus.PLAYING
            LaunchedEffect(interacting) {
                if (interacting) { deadEnd = null; surrenderVisible = false }
            }
            AnimatedVisibility(
                visible = out != null && !interacting && boardOffsetPx != Offset.Zero,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(220))
            ) {
                val shown = remember(out) { out }
                if (shown != null) {
                    val companionId = when (shown) {
                        is com.cukiti.teikafusion.model.BoardDeadEnd.Cast -> shown.companionId
                        is com.cukiti.teikafusion.model.BoardDeadEnd.Buy -> shown.companionId
                        else -> null
                    }
                    val boardLeft = (boardOffsetPx.x - rootOffset.x).roundToInt()
                    val boardTop  = (boardOffsetPx.y - rootOffset.y).roundToInt()
                    DeadEndPanel(
                        deadEnd = shown,
                        abilityName = companionId?.let { viewModel.companionAbilityName(it) }.orEmpty(),
                        squeezedBy = gameState.lastSqueezeBiome
                            ?.takeIf { it != com.cukiti.teikafusion.model.BiomeType.NONE },
                        surrenderVisible = surrenderVisible,
                        onAct = {
                            viewModel.soundManager.playUiClick()
                            when (shown) {
                                is com.cukiti.teikafusion.model.BoardDeadEnd.Cast -> {
                                    deadEnd = null
                                    surrenderVisible = false
                                    viewModel.activateCompanionAbility(shown.companionId)
                                }
                                is com.cukiti.teikafusion.model.BoardDeadEnd.Buy -> {
                                    // Comprar no lanza: la carga entra y el cartel se replantea solo
                                    // como «LANZAR». Gastar teikoins y una carga en el mismo toque
                                    // sería cobrarle dos decisiones por un botón.
                                    if (viewModel.buyCompanionCharge(shown.companionId)) {
                                        deadEnd = viewModel.evaluateDeadEnd()
                                    }
                                }
                                is com.cukiti.teikafusion.model.BoardDeadEnd.Hold -> {
                                    deadEnd = null
                                    surrenderVisible = false
                                    bankWorkshopOpen = true
                                }
                                else -> Unit
                            }
                        },
                        onSurrender = {
                            viewModel.soundManager.playUiClick()
                            deadEnd = null
                            surrenderVisible = false
                            if (!AdManager.isReadyFlow.value) AdManager.loadAd(context)
                            viewModel.triggerNoSpaceRevive()
                        },
                        modifier = Modifier
                            .offset { IntOffset(boardLeft, boardTop) }
                            .width(with(density) { (cellSizePx * boardCols).toDp() })
                            .height(with(density) { (cellSizePx * boardRows).toDp() })
                    )
                }
            }
        }

        // Revive 2A: si la única obstrucción eran Teikas físicos, esta capa cuenta la consolidación
        // asistida. Es el único consumidor del evento; el VM conserva la autoridad sobre los cuerpos.
        val reviveTeikaRescues = eventBuckets
            .of<com.cukiti.teikafusion.model.GameEvent.ReviveTeikaRescue>()
        if (reviveTeikaRescues.isNotEmpty() && boardOffsetPx != Offset.Zero) {
            Box(modifier = Modifier.fillMaxSize()) {
                reviveTeikaRescues.forEach { event ->
                    key(event.id) {
                        ReviveTeikaRescueVFX(
                            event = event,
                            boardOrigin = Offset(
                                boardOffsetPx.x - rootOffset.x,
                                boardOffsetPx.y - rootOffset.y,
                            ),
                            cellSizePx = cellSizePx,
                            reducedFx = reducedFx,
                            onComplete = { viewModel.consumeEvent(event.id) },
                        )
                    }
                }
            }
        }

        // Inicio regular sin ventana ni botón: los objetivos aparecen en el centro y vuelan al HUD.
        // Los cuatro niveles tutoriales conservan exclusivamente su guía visual propia.
        LaunchedEffect(roundStartToken, tutorialLevel, gameState.levelObjective) {
            if (roundStartToken > 0 && (tutorialLevel || gameState.levelObjective == null)) {
                viewModel.consumeRoundStartPresentation(roundStartToken)
            }
        }
        if (objectivePresentationActive) {
            ObjectiveRevealOverlay(
                objective = currentLevelObjective,
                megaRequired = currentMegaRequired,
                targetRects = targetCounterRects,
                megaRect = megaCounterRect,
                rootOffset = rootOffset,
                reducedFx = reducedFx,
                onItemLanded = { target ->
                    val token = roundStartToken
                    if (token > 0) {
                        val previous = objectiveRevealHudState
                            .takeIf { it.token == token }
                            ?: ObjectiveRevealHudState(token)
                        objectiveRevealHudState = when (target) {
                            is ObjectiveRevealTarget.Star -> previous.copy(
                                revealedColors = previous.revealedColors + target.color
                            )
                            ObjectiveRevealTarget.Mega -> previous.copy(megaRevealed = true)
                        }
                        objectiveLandingColor = (target as? ObjectiveRevealTarget.Star)?.color
                        objectiveLandingIsMega = target == ObjectiveRevealTarget.Mega
                        objectiveLandingImpactToken++
                        if (!reducedFx) Haptics.objectiveLand(target == ObjectiveRevealTarget.Mega)
                    }
                },
                onFinished = {
                    // También cubre el watchdog: antes de quitar el bloqueador, el HUD completo
                    // vuelve a ser visible incluso si algún destino no pudo medirse.
                    objectiveRevealHudState = ObjectiveRevealHudState(
                        token = roundStartToken,
                        revealedColors = currentLevelObjective.required.keys,
                        megaRevealed = currentMegaRequired,
                    )
                    viewModel.consumeRoundStartPresentation(roundStartToken)
                }
            )
        }

    }
}


/* (BORRADO 2026-07-25) Aquí vivía CompanionPortraitButton: el retrato circular del compañero
 * con su anillo de energía y el rótulo «¡YA!». Las habilidades pasaron a ser PASIVAS y se
 * disparan solas al llenarse la barra, así que el botón dejó de tener a quién servir. Quien avisa
 * ahora es CompanionChargeAura, sobre el propio tablero y con el bioma del compañero. */

/**
 * El SEGUNDERO del enfriamiento del anuncio de monedas (P-18/P-19), en su propio ámbito de
 * recomposición (perf 2026-09-26). Vivía en el cuerpo de [GameScreen]: mientras durara la espera, el
 * tic de cada segundo recomponía la pantalla entera —tablero, bandeja y ~80 capas de VFX— solo para
 * cambiar una cifra de la barra superior. Ahora el tic recompone este envoltorio y lo que pinta.
 */
@Composable
private fun AdCooldownTicker(
    adQuota: com.cukiti.teikafusion.model.RewardedAdQuota.State,
    content: @Composable (adQuotaLeft: Int, adCooldownSec: Int) -> Unit,
) {
    var adNowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(adQuota) {
        while (com.cukiti.teikafusion.model.RewardedAdQuota.cooldownLeftMs(adQuota, System.currentTimeMillis()) > 0) {
            adNowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
        adNowMs = System.currentTimeMillis()
        // Cupo AGOTADO: el día nuevo lo devuelve, pero nadie volvía a mirar el reloj y el botón
        // seguía en «—» pasada la medianoche hasta salir de la partida. Un vistazo por minuto basta.
        while (com.cukiti.teikafusion.model.RewardedAdQuota.remaining(adQuota, adNowMs) <= 0) {
            kotlinx.coroutines.delay(60_000)
            adNowMs = System.currentTimeMillis()
        }
    }
    val adCooldownSec = ((com.cukiti.teikafusion.model.RewardedAdQuota
        .cooldownLeftMs(adQuota, adNowMs) + 999) / 1000).toInt()
    val adQuotaLeft = com.cukiti.teikafusion.model.RewardedAdQuota.remaining(adQuota, adNowMs)
    content(adQuotaLeft, adCooldownSec)
}

@Composable
private fun FlyingGameCoin(start: Offset, end: Offset, onFinished: () -> Unit) {
    val anim = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        anim.animateTo(1f, androidx.compose.animation.core.tween(700, easing = androidx.compose.animation.core.FastOutSlowInEasing))
        onFinished()
    }
    val t = anim.value
    val cx = androidx.compose.ui.util.lerp(start.x, end.x, t)
    val arc = sin(t * PI.toFloat()) * 120f
    val cy = androidx.compose.ui.util.lerp(start.y, end.y, t) - arc
    val alpha = when {
        t < 0.08f -> t / 0.08f
        t > 0.80f -> (1f - t) / 0.20f
        else -> 1f
    }
    val scale = 1f + sin(t * PI.toFloat()) * 0.45f
    val halfSizePx = with(LocalDensity.current) { 10.dp.toPx() }
    // La moneda que vuela al contador es el ARTE de la moneda, no el emoji 🪙: en los móviles cuya
    // fuente de emoji no trae ese carácter (Unicode 13) no se dibujaba nada y el vuelo era invisible.
    // Ver [TeikoinIcon].
    TeikoinIcon(
        size = 20.dp,
        modifier = Modifier
            .absoluteOffset { IntOffset((cx - halfSizePx).roundToInt(), (cy - halfSizePx).roundToInt()) }
            .scale(scale)
            .alpha(alpha)
    )
}

/* (PODADO 2026-07-30) Aquí vivía `CompanionSelectionOverlay`: el velo con la instrucción («Toca
 * dos Teikas del mismo tipo…») que se comía los toques del tablero mientras el jugador elegía
 * objetivo. Ninguna habilidad abría ya ese modo — ver la nota de poda en GameViewModel. */

// ── Bosque del Sueño / Maite: "z" de sueño flotando en el fondo del panel de fichas ──

private data class ZParticle(
    val x: Float, val size: Float, val cycles: Int, val phaseOff: Float, val sway: Float
)

/**
 * Fondo animado del panel de fichas mientras dura la Maite: varias "z" de sueño que ascienden
 * suavemente. Se dibuja DETRÁS de las fichas (sin velo, las fichas quedan intactas).
 *
 * Bucle PERFECTO: cada "z" recorre un número ENTERO de ciclos ([cycles]) por periodo maestro, así
 * `prog` es continuo en el salto 1→0 del reloj (y el alfa con sin(prog·π) llega a 0 en los extremos),
 * sin parpadeos ni saltos al reiniciar.
 */
@Composable
private fun DrowsyZzzBackdrop(modifier: Modifier = Modifier) {
    val anim = rememberInfiniteTransition(label = "drowsy")
    val phase = anim.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3600, easing = androidx.compose.animation.core.LinearEasing), RepeatMode.Restart
        ),
        label = "zphase"
    )
    val zs = remember {
        val rng = kotlin.random.Random(31)
        List(6) {
            ZParticle(
                x = 0.12f + rng.nextFloat() * 0.76f,
                size = 0.18f + rng.nextFloat() * 0.18f,
                cycles = 1 + rng.nextInt(2),   // 1 ó 2 ciclos enteros → bucle perfecto
                phaseOff = rng.nextFloat(),
                sway = rng.nextFloat()
            )
        }
    }
    // Capa propia (auditoría de fluidez 2026-09-29): las «z» animan cada fotograma durante todo el sueño
    // y, sin capa, arrastraban a regrabar las tres fichas de la bandeja con ellas.
    androidx.compose.foundation.Canvas(modifier.graphicsLayer()) {
        zs.forEach { z ->
            val prog = (phase.value * z.cycles + z.phaseOff) % 1f
            val zy = size.height * (1f - prog)
            val zx = (z.x + kotlin.math.sin((prog + z.sway) * 6.28318f) * 0.05f) * size.width
            val s = z.size * size.height
            val a = kotlin.math.sin(prog * Math.PI).toFloat().coerceIn(0f, 1f) * 0.85f
            drawSleepZ(Offset(zx, zy), s, Color(0xFFD8CCFF).copy(alpha = a))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSleepZ(center: Offset, s: Float, color: Color) {
    val w = s * 0.8f
    val tl = Offset(center.x - w / 2f, center.y - s / 2f)
    val tr = Offset(center.x + w / 2f, center.y - s / 2f)
    val bl = Offset(center.x - w / 2f, center.y + s / 2f)
    val br = Offset(center.x + w / 2f, center.y + s / 2f)
    val sw = s * 0.14f
    drawLine(color, tl, tr, sw)
    drawLine(color, tr, bl, sw)
    drawLine(color, bl, br, sw)
}

// ── Flora / Cañón Floral (rework it.54): capa de PUNTERÍA del capullo-cañón ──────────────────
// Tras la ceremonia de la flor, el CAPULLO aparece en el borde inferior del tablero (pegado al
// panel de fichas) cargado con el Teika enterrado. El jugador solo mueve la LÍNEA de puntería
// (arrastrando sobre el tablero) — el capullo la sigue por el borde — y al soltar dispara. La
// fuerza es fija: el nivel da el nº de rebotes (física balística, ver launchFloraTeika).
@Composable
private fun FloraAimLayer(
    aim: com.cukiti.teikafusion.model.FloraAimState,
    cellPx: Float,
    onLaunch: (Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val grid = GameState.GRID_SIZE
    val boardPx = grid * cellPx
    // Capullo FIJO en el centro del borde inferior (feedback playtest r3): el arrastre ya NO lo
    // desplaza — solo GIRA la flecha de puntería a izquierda/derecha alrededor del capullo; al
    // soltar el dedo, dispara desde el centro con ese ángulo.
    val cocoonX = grid / 2f
    var touch by remember(aim) { mutableStateOf<Offset?>(null) }
    Canvas(
        modifier = modifier.pointerInput(aim) {
            detectDragGestures(
                onDragStart = { pos -> touch = pos },
                onDrag = { change, _ ->
                    touch = change.position
                    change.consume()
                },
                onDragEnd = {
                    val t = touch ?: return@detectDragGestures
                    val ox = cocoonX * cellPx
                    val dx = t.x - ox
                    val dy = t.y - boardPx
                    // Solo dispara apuntando HACIA el tablero (arrastre mínimo hacia arriba).
                    if (dy < -cellPx * 0.4f) onLaunch(dx, dy, cocoonX)
                }
            )
        }
    ) {
        val petal = Color(0xFFFF7EB0)
        val petalDeep = Color(0xFFE91E63)
        val leaf = Color(0xFF66BB6A)
        val gold = Color(0xFFFFD54F)
        val origin = Offset(cocoonX * cellPx, boardPx - cellPx * 0.10f)
        // Dirección de la guía (por defecto, recta hacia arriba).
        val tRaw = touch
        var dx = (tRaw?.x ?: origin.x) - origin.x
        var dy = ((tRaw?.y ?: (origin.y - cellPx * 4f)) - origin.y).coerceAtMost(-cellPx * 0.5f)
        val len = kotlin.math.hypot(dx, dy)
        dx /= len; dy /= len
        // Línea de puntería PUNTEADA desde el capullo hacia el tablero + flecha en la punta.
        val guideLen = boardPx * 0.85f
        val end = Offset(origin.x + dx * guideLen, origin.y + dy * guideLen)
        drawLine(
            leaf.copy(alpha = 0.28f), origin, end, strokeWidth = cellPx * 0.20f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            Color.White.copy(alpha = 0.85f), origin, end, strokeWidth = cellPx * 0.07f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                floatArrayOf(cellPx * 0.32f, cellPx * 0.26f), 0f
            )
        )
        // Flecha en la punta.
        val perp = Offset(-dy, dx)
        val tip = end
        val back = Offset(end.x - dx * cellPx * 0.5f, end.y - dy * cellPx * 0.5f)
        drawLine(Color.White.copy(alpha = 0.9f), tip,
            Offset(back.x + perp.x * cellPx * 0.28f, back.y + perp.y * cellPx * 0.28f),
            strokeWidth = cellPx * 0.08f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.9f), tip,
            Offset(back.x - perp.x * cellPx * 0.28f, back.y - perp.y * cellPx * 0.28f),
            strokeWidth = cellPx * 0.08f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // CAPULLO en el borde (pegado al panel de fichas): cáliz verde + pétalos cerrados + brillo.
        val bud = origin
        drawCircle(
            Brush.radialGradient(
                listOf(gold.copy(alpha = 0.35f), Color.Transparent), bud, cellPx * 1.1f
            ),
            radius = cellPx * 1.1f, center = bud
        )
        // Tallo hacia la bandeja.
        drawLine(leaf, Offset(bud.x, bud.y + cellPx * 0.65f), Offset(bud.x, bud.y + cellPx * 0.15f),
            strokeWidth = cellPx * 0.11f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        // Cáliz (hojas de la base).
        drawCircle(leaf, cellPx * 0.20f, Offset(bud.x - cellPx * 0.16f, bud.y + cellPx * 0.18f))
        drawCircle(leaf, cellPx * 0.20f, Offset(bud.x + cellPx * 0.16f, bud.y + cellPx * 0.18f))
        // Pétalos CERRADOS convergiendo hacia arriba (el capullo guarda el Teika).
        drawCircle(petalDeep, cellPx * 0.30f, Offset(bud.x, bud.y - cellPx * 0.02f))
        drawCircle(petal, cellPx * 0.26f, Offset(bud.x - cellPx * 0.13f, bud.y - cellPx * 0.14f))
        drawCircle(petal, cellPx * 0.26f, Offset(bud.x + cellPx * 0.13f, bud.y - cellPx * 0.14f))
        drawCircle(petal.copy(alpha = 0.95f), cellPx * 0.22f, Offset(bud.x, bud.y - cellPx * 0.26f))
        drawCircle(Color.White.copy(alpha = 0.35f), cellPx * 0.10f, Offset(bud.x - cellPx * 0.06f, bud.y - cellPx * 0.30f))
    }
}

// ── Mystery Teika cell highlight ──────────────────────────────────────────────




/**
 * **P-13 — el tablero como rejilla de botones.** Una capa transparente de `rows × cols` casillas,
 * cada una anunciada con su coordenada y con si la ficha seleccionada CABE ahí; el doble toque la
 * coloca. Es el otro medio gesto del camino accesible (el primero es elegir ficha en la bandeja).
 *
 * Se monta solo con el lector encendido y con una ficha ya elegida, así que su coste en la partida
 * normal es exactamente cero.
 */
/**
 * **CARTEL «SIN SALIDA»** (P-105, 2026-09-02) — la fase 1 del tablero atascado.
 *
 * No es una ventana: es un aviso **sobre el tablero**, que sigue viéndose entero detrás. Ese es el
 * punto — el jugador tiene que poder comprobar por sí mismo que, en efecto, no cabía nada, y en el
 * mismo golpe de vista leer qué puede hacer. Antes de esto, un tablero lleno con una carga en el
 * bolsillo no producía **nada**: la ficha rebotaba con el sonido de inválido y el juego se callaba.
 *
 * Enseña **una sola** salida, la más barata que exista ([BoardDeadEnd] ya viene ordenado):
 * inclinar (gratis) → lanzar (una carga) → comprar (teikoins). El botón de **rendirse** aparece con
 * retraso a propósito: el dedo que acaba de soltar la ficha está justo encima, y una rendición
 * accidental es la peor forma de perder un nivel.
 *
 * Se retira solo en cuanto el jugador **toca cualquier cosa** de la partida (el propio panel, coger
 * una ficha, abrir la hoja de habilidades, pausar) — es un consejo, no un modal — y vuelve a salir
 * si intenta colocar otra vez y el tablero sigue trancado.
 */
@Composable
private fun DeadEndPanel(
    deadEnd: com.cukiti.teikafusion.model.BoardDeadEnd,
    abilityName: String,
    /** P-228: el bioma que quitó sitio en este turno, o `null` si el tablero se llenó jugando. */
    squeezedBy: com.cukiti.teikafusion.model.BiomeType?,
    surrenderVisible: Boolean,
    onAct: () -> Unit,
    onSurrender: () -> Unit,
    modifier: Modifier = Modifier
) {
    // P-295 (2026-09-25, playtest): era un rectángulo oscuro con texto y botones de sistema. Ahora
    // tiene cara propia —sigue sin ser una ventana: el tablero se ve detrás, que es el punto—: una
    // cinta dorada con el título, un marco que late, el dibujo de la salida (un móvil que se inclina,
    // el rayo del power-up o la moneda) y botones de arcade.
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        enter.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow))
    }
    val reduced = LocalReducedFx.current
    val loop = rememberInfiniteTransition(label = "deadEnd")
    val pulse by loop.animateFloat(
        0f, 1f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "deadEndPulse"
    )
    val gold = Color(0xFFFFC857)
    val (accent, accentDeep) = when (deadEnd) {
        is com.cukiti.teikafusion.model.BoardDeadEnd.Cast -> Color(0xFF5BE38A) to Color(0xFF1C7A42)
        is com.cukiti.teikafusion.model.BoardDeadEnd.Buy -> Color(0xFFC89BFF) to Color(0xFF5B2E99)
        is com.cukiti.teikafusion.model.BoardDeadEnd.Hold -> BankAccent to Color(0xFF1C7A6A)
        else -> gold to Color(0xFF9A5B00)
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        NoSpaceBoardChill(modifier = Modifier.matchParentSize())
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = 0.86f + 0.14f * enter.value
                    scaleY = 0.86f + 0.14f * enter.value
                    alpha = enter.value
                }
                .fillMaxWidth(0.92f)
                .padding(top = 16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xF2231A3D), Color(0xF2120D24))))
                    .drawBehind {
                        val a = if (reduced) 0.7f else 0.45f + 0.45f * pulse
                        drawRoundRect(
                            accent.copy(alpha = a),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(22.dp.toPx()),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx())
                        )
                    }
                    .padding(start = 18.dp, end = 18.dp, top = 30.dp, bottom = 16.dp)
            ) {
                DeadEndGlyph(deadEnd = deadEnd, accent = accent, reduced = reduced)
                Spacer(Modifier.height(8.dp))
                // ── P-228 · QUIÉN TE HA DEJADO SIN SITIO ──────────────────────────────────────
                // La misma frase para dos cosas distintas: haberte llenado el tablero tú, y que el
                // castigo del bioma te lo haya llenado a ti. Cuando el tick de bioma ha quitado hueco
                // jugable en este turno se dice con nombre ([GameState.lastSqueezeBiome]).
                Text(
                    squeezedBy?.let { "${it.displayName} te ha dejado sin sitio." }
                        ?: "Ninguna ficha cabe en el tablero.",
                    color = Color.White.copy(alpha = 0.70f),
                    fontSize = 13.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(6.dp))
                when (deadEnd) {
                    // La única salida gratis: los Teikas son cuerpos, no celdas — ruedan.
                    com.cukiti.teikafusion.model.BoardDeadEnd.Tilt -> {
                        Text(
                            "Inclina el móvil: rodando un Teika se abre el hueco que falta.",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 21.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    is com.cukiti.teikafusion.model.BoardDeadEnd.Cast -> {
                        Text(
                            "$abilityName puede abrirte hueco.",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        ArcadeButton(
                            label = "LANZAR",
                            tone = ArcadeTone.GREEN,
                            onClick = onAct,
                            modifier = Modifier.fillMaxWidth(),
                            height = 50.dp,
                            fontSize = 18.sp,
                            depth = 6.dp,
                            contentDescription = "Lanzar $abilityName",
                        )
                    }
                    is com.cukiti.teikafusion.model.BoardDeadEnd.Buy -> {
                        Text(
                            "No te quedan cargas de $abilityName, pero puedes comprar una.",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 21.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        ArcadeButton(
                            label = "COMPRAR  ${deadEnd.price}",
                            tone = ArcadeTone.GOLD,
                            onClick = onAct,
                            modifier = Modifier.fillMaxWidth(),
                            height = 50.dp,
                            fontSize = 18.sp,
                            depth = 6.dp,
                            contentDescription = "Comprar una carga de $abilityName por ${deadEnd.price} Teikoins",
                            leading = { RawImage(com.cukiti.teikafusion.R.raw.teikoin, Modifier.size(20.dp)) },
                        )
                    }
                    // `None` no llega aquí: sin salida ninguna el flujo va directo a la fase 2.
                    // Fichona puede fabricar una forma compatible.
                    is com.cukiti.teikafusion.model.BoardDeadEnd.Hold -> {
                        Text(
                            "Crea una Fichona a medida del mayor hueco.",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            lineHeight = 21.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        ArcadeButton(
                            label = "CREAR FICHONA",
                            tone = ArcadeTone.GREEN,
                            onClick = onAct,
                            modifier = Modifier.fillMaxWidth(),
                            height = 50.dp,
                            fontSize = 18.sp,
                            depth = 6.dp,
                            contentDescription = "Abrir el banco de bloques",
                        )
                    }
                    com.cukiti.teikafusion.model.BoardDeadEnd.None -> Unit
                }
                // El botón de rendirse entra tarde y se va con el primer toque (ver KDoc).
                AnimatedVisibility(
                    visible = surrenderVisible,
                    enter = fadeIn(tween(320)),
                    exit = fadeOut(tween(160))
                ) {
                    TextButton(
                        onClick = onSurrender,
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Text(
                            "· rendirse ·",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    }
                }
            }
            // La cinta del título, montada sobre el borde de arriba.
            Box(
                modifier = Modifier
                    .offset(y = (-16).dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(accent, accentDeep)))
                    .border(2.dp, Color(0xFF120D24), RoundedCornerShape(12.dp))
                    .padding(horizontal = 22.dp, vertical = 6.dp)
            ) {
                Text(
                    "SIN SALIDA",
                    color = Color(0xFF1A1030),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp
                )
            }
        }
    }
}

/**
 * El dibujo de la salida del cartel «SIN SALIDA» (P-295): un móvil que se balancea (inclinar), un rayo
 * que late (lanzar un power-up) o una moneda que gira (comprarlo). Con «reducir efectos», quieto.
 */
@Composable
private fun DeadEndGlyph(
    deadEnd: com.cukiti.teikafusion.model.BoardDeadEnd,
    accent: Color,
    reduced: Boolean,
) {
    val loop = rememberInfiniteTransition(label = "deadEndGlyph")
    val swing by loop.animateFloat(
        -1f, 1f, infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "swing"
    )
    val s = if (reduced) 0f else swing
    Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(accent.copy(alpha = 0.16f), radius = size.minDimension / 2f)
            drawCircle(
                accent.copy(alpha = 0.55f), radius = size.minDimension / 2f - 1.dp.toPx(),
                style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx())
            )
        }
        when (deadEnd) {
            com.cukiti.teikafusion.model.BoardDeadEnd.Tilt -> Canvas(
                Modifier.size(34.dp).graphicsLayer { rotationZ = 22f * s }
            ) {
                // El móvil: cuerpo, pantalla y una bolita que rueda hacia el lado bajo.
                val w = size.width * 0.56f
                val h = size.height * 0.92f
                val l = (size.width - w) / 2f
                val t = (size.height - h) / 2f
                drawRoundRect(
                    Color.White, Offset(l, t), androidx.compose.ui.geometry.Size(w, h),
                    androidx.compose.ui.geometry.CornerRadius(w * 0.22f)
                )
                drawRoundRect(
                    Color(0xFF1A1030), Offset(l + w * 0.12f, t + h * 0.10f),
                    androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.76f),
                    androidx.compose.ui.geometry.CornerRadius(w * 0.10f)
                )
                drawCircle(
                    accent, radius = w * 0.17f,
                    center = Offset(size.width / 2f + w * 0.22f * s, t + h * 0.66f)
                )
            }
            is com.cukiti.teikafusion.model.BoardDeadEnd.Cast -> Canvas(
                Modifier.size(30.dp).graphicsLayer {
                    val k = 1f + 0.10f * s
                    scaleX = k; scaleY = k
                }
            ) {
                val w = size.width; val h = size.height
                val bolt = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.60f, 0f); lineTo(w * 0.14f, h * 0.56f); lineTo(w * 0.46f, h * 0.56f)
                    lineTo(w * 0.36f, h); lineTo(w * 0.86f, h * 0.40f); lineTo(w * 0.54f, h * 0.40f); close()
                }
                drawPath(bolt, accent)
            }
            is com.cukiti.teikafusion.model.BoardDeadEnd.Buy -> RawImage(
                com.cukiti.teikafusion.R.raw.teikoin,
                Modifier.size(34.dp).graphicsLayer { rotationY = 40f * s }
            )
            is com.cukiti.teikafusion.model.BoardDeadEnd.Hold -> BankDeadEndGlyph(accent, (s + 1f) / 2f)
            com.cukiti.teikafusion.model.BoardDeadEnd.None -> Unit
        }
    }
}

@Composable
private fun A11yBoardGrid(
    rows: Int,
    cols: Int,
    canPlaceAt: (Int, Int) -> Boolean,
    onPlaceAt: (Int, Int) -> Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        for (y in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                for (x in 0 until cols) {
                    val fits = canPlaceAt(x, y)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                enabled = fits,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = androidx.compose.ui.semantics.Role.Button
                            ) { onPlaceAt(x, y) }
                            .semantics {
                                contentDescription = if (fits)
                                    "Fila ${y + 1}, columna ${x + 1}. Cabe. Toca dos veces para colocar"
                                else
                                    "Fila ${y + 1}, columna ${x + 1}. No cabe"
                            }
                    )
                }
            }
        }
    }
}

/* (ERRADICADO 2026-08-09, con el modo) `InfiniteBannerSlot` y `topInfiniteBanner`: la COLA DE
 * RÓTULOS que arbitraba los seis anuncios grandes de WALLBLOCK (hito de muro, récord de
 * profundidad, despertar de la marea, escape del clutch, cambio de fase y rival superado) para que
 * no se pintaran superpuestos. Sin esos rótulos no hay nada que arbitrar. */

/** El oro del premio de la bandeja (2026-09-26). */
private val TrayRewardGold = Color(0xFFFFD54F)

/** Lo que dura el brillo del premio de la bandeja: lo bastante para verse, sin tapar la jugada. */
private const val TRAY_REWARD_GLOW_MS = 1_100

/**
 * P-430 · LA FICHA QUE NO CABE, EN GRIS (2026-10-01, elegida por el usuario tras la auditoría). Pinta
 * el contenido en una capa con saturación 0. Solo se aplica a la ficha condenada (P-229), que cambia
 * como mucho una vez por turno: la capa extra no cuesta nada en reposo.
 */
private fun Modifier.doomedGrayscale(): Modifier = drawWithCache {
    val paint = androidx.compose.ui.graphics.Paint().apply {
        colorFilter = androidx.compose.ui.graphics.ColorFilter.colorMatrix(
            androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(0f) }
        )
    }
    onDrawWithContent {
        drawIntoCanvas { canvas ->
            canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, size), paint)
            drawContent()
            canvas.restore()
        }
    }
}

/**
 * P-436 · LOS NIVELES DE LA PANTALLA DE PARTIDA (2026-10-01, elegida por el usuario tras la auditoría).
 *
 * La caja principal de [GameScreen] tiene decenas de capas, y en un `Box` pinta encima la que se
 * declara DESPUÉS. Dos veces una capa de juego declarada tras las ventanas se pintó sobre ellas (la
 * capa de enseñanza sobre «lección completada»; el banco sobre «nivel completado»). Con `zIndex` el
 * orden ya no depende de dónde se escriba cada capa:
 *  - 0 (por defecto): todo lo de partida — tablero, bandeja, banco, power-ups, VFX, avisos.
 *  - [WINDOW]: las ventanas de estado — fin de nivel, derrota, revivir, pausa y «¿Salir del nivel?».
 *  - [REVEAL]: la presentación de un personaje, que es una celebración modal por encima de todo.
 * Lo vigila `GameLayersContractTest`.
 */
internal object GameLayers {
    const val WINDOW = 10f
    const val REVEAL = 20f
}
