package com.cukiti.teikafusion.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.core.net.toUri
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.activity.compose.BackHandler
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.cukiti.teikafusion.model.GameStatus
import com.cukiti.teikafusion.ui.theme.AppBackground
import com.cukiti.teikafusion.viewmodel.GameViewModel

// NAVEGACIÓN SIN ANIMACIÓN (2026-07-28) — abrir y cerrar cualquier pantalla u overlay es un CORTE
// SECO. Las transiciones (slide/fade/scale) obligaban a componer la pantalla entrante mientras la
// saliente aún dibujaba: dos árboles pesados a la vez, justo en el peor instante. Cortar deja el
// primer fotograma de la pantalla nueva libre y el menú se siente instantáneo.
private val NavNoEnter = androidx.compose.animation.EnterTransition.None
private val NavNoExit  = androidx.compose.animation.ExitTransition.None

// ─────────────────────────────────────────────────────────────────────────────
// Zero-lag canvas host — todas las pestañas permanecen en memoria siempre.
// La pestaña activa tiene alpha=1 e input habilitado; las inactivas tienen
// alpha=0 (graphicsLayer salta el draw) e input bloqueado con pointerInput.
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun ZeroLagTabs(
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    /**
     * `true` mientras la partida está en pantalla. Esta capa de pestañas es la CAPA 1 y **nunca se
     * desmonta**: `GameScreen` se dibuja ENCIMA. Así que, sin esto, la pestaña activa del menú seguía
     * animando a 60 fps **por debajo del tablero**, robándole fotogramas a la física — que es lo más
     * crítico del juego. Jugando, ninguna pestaña cuenta como activa.
     */
    covered: Boolean = false,
    /**
     * Hay una capa **OPACA** encima (el tablero). Entonces el menú no solo deja de animar: deja de
     * **DIBUJARSE**. Ver el porqué en el cuerpo — es distinto de [covered], que también lo activan
     * overlays traslúcidos por los que Inicio se sigue viendo.
     */
    hiddenByOpaqueLayer: Boolean = false,
    /**
     * «Reducir efectos» (P-12). Se propaga a las pestañas por [LocalReducedFx]: el ajuste solo
     * llegaba a la partida, y desde WALLBLOCK el MENÚ también tiene una animación ambiente en bucle
     * (el muro que sube en la tarjeta de Home) que tiene que obedecerlo.
     */
    reducedFx: Boolean = false,
    tabs: List<@Composable () -> Unit>
) {
    Box(modifier) {
        tabs.forEachIndexed { index, screen ->
            // OJO: `isActive` gobierna alpha, zIndex y el bloqueo de input, y NO debe depender de
            // [covered]: si el menú se apagara de golpe al entrar al juego, se vería el fondo desnudo
            // durante los 300 ms del fundido de GameScreen. Lo único que [covered] apaga es el RELOJ
            // de las animaciones (ver `animationsActive`).
            val isActive = index == selectedIndex
            val animationsActive = isActive && !covered
            // ── LA PESTAÑA QUE NO SE VE, NO SE DIBUJA (2026-08-24) ───────────────────────────────
            //
            // `graphicsLayer { alpha = 0f }` **no salta el dibujo**, y el comentario de arriba decía
            // que sí. Un alpha de 0 marca la RenderNode como transparente para la GPU, pero el árbol
            // se sigue recorriendo y su display list se vuelve a grabar en CPU: cada `Canvas`, cada
            // Lottie y cada texto de las pestañas ocultas se pintaba igual, fotograma tras fotograma.
            //
            // Medido con `simpleperf` el 2026-08-24 **con el panel de JUGAR NIVEL abierto**: el
            // 99,9 % del hilo principal se iba en `performDraw` (ni composición ni layout), y entre
            // los símbolos vivos estaban `InventoryScreenKt.CompanionAbilityTile` y
            // `PremiumUiKt.PremiumMedallion` — o sea, la pestaña de INVENTARIO dibujándose entera
            // debajo de un panel que la tapaba.
            //
            // `drawWithContent` sin llamar a `drawContent()` corta el dibujo del subárbol de raíz,
            // sin tocar composición ni layout: la pestaña sigue VIVA en memoria (que es el sentido
            // de esta capa) y volver a ella sigue siendo instantáneo.
            val drawsThisFrame = isActive && !hiddenByOpaqueLayer
            // Cambio de pestaña INSTANTÁNEO: el fundido de 90 ms obligaba a dibujar dos pestañas
            // completas durante ~6 fotogramas justo al tocar el navbar (el peor momento posible).
            val alpha = if (isActive) 1f else 0f
            Box(
                Modifier
                    .fillMaxSize()
                    // zIndex(1) para el activo → Compose lo procesa PRIMERO en Main pass
                    // (mayor Z = primera prioridad en hit-testing y dispatch de eventos)
                    .zIndex(if (isActive) 1f else 0f)
                    .graphicsLayer { this.alpha = alpha }
                    .drawWithContent { if (drawsThisFrame) drawContent() }
                    // Las pestañas ocultas (alpha 0) y el menú TAPADO por la partida siguen
                    // COMPUESTOS (esa es la gracia del zero-lag), pero NO deben existir para el
                    // lector de pantalla: el blocker de abajo consume TOQUES, no semántica, así
                    // que TalkBack recorría las pestañas invisibles (eran 4 cuando se arregló en la
                    // it. 45; hoy son 3) — y, jugando, el menú entero por debajo del tablero.
                    .then(if (!animationsActive) Modifier.clearAndSetSemantics { } else Modifier)
            ) {
                // Las pestañas ocultas siguen COMPUESTAS (esa es la gracia del zero-lag), pero no
                // tienen por qué seguir ANIMANDO: sus brillos y latidos pedían un fotograma cada
                // 16 ms para dibujarse en una capa con alpha 0. Ver [LocalTabActive].
                CompositionLocalProvider(
                    LocalTabActive provides animationsActive,
                    LocalReducedFx provides reducedFx
                ) {
                    screen()
                }

                // Blocker para tabs INACTIVOS.
                // Al ser el ÚLTIMO hijo del Box, Main pass lo procesa ANTES que screen(),
                // consumiendo todos los eventos sin que lleguen al contenido invisible.
                // El tab ACTIVO no tiene blocker → su screen() recibe input normalmente.
                if (!isActive) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        awaitPointerEvent(PointerEventPass.Main)
                                            .changes
                                            .forEach { it.consume() }
                                    }
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun AppNavigation(viewModel: GameViewModel) {
    remember { introLog("AppNavigation compone"); 0 }
    // ── Recoger estados del ViewModel ─────────────────────────────────────────
    val gameState            by viewModel.gameState.collectAsState()
    val isMuted              by viewModel.isMuted.collectAsState()
    val reducedFx            by viewModel.reducedFx.collectAsState()
    val musicVolume          by viewModel.musicVolume.collectAsState()
    val sfxVolume            by viewModel.sfxVolume.collectAsState()
    val vibrationEnabled     by viewModel.vibrationEnabled.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val tiltEnabled          by viewModel.tiltEnabled.collectAsState()
    val streak               by viewModel.dailyStreak.collectAsState()
    val dailyStreakCelebration by viewModel.dailyStreakCelebration.collectAsState()
    val winStreak            by viewModel.winStreak.collectAsState()
    val showLeaderboard      by viewModel.showLeaderboard.collectAsState()
    val leaderboardEntries   by viewModel.leaderboardEntries.collectAsState()
    val leaderboardLoading   by viewModel.leaderboardLoading.collectAsState()
    val leaderboardError     by viewModel.leaderboardError.collectAsState()
    val isSavingName         by viewModel.isSavingName.collectAsState()
    val displayName          by viewModel.displayName.collectAsState()
    val playerProfileLoaded  by viewModel.playerProfileLoaded.collectAsState()
    val initialAccountSyncFinished by viewModel.initialAccountSyncFinished.collectAsState()
    val avatarType           by viewModel.avatarType.collectAsState()
    val currentUid           by viewModel.currentUid.collectAsState()
    val companionUpgrades    by viewModel.companionUpgrades.collectAsState()
    val remoteConfig         by viewModel.remoteConfig.collectAsState()
    val companionCharges     by viewModel.companionCharges.collectAsState()
    val companionBelt        by viewModel.companionBelt.collectAsState()
    val crystalInventory     by viewModel.crystalInventory.collectAsState()
    val abyssCardRevealSeen  by viewModel.abyssCardRevealSeen.collectAsState()
    val accountDeletion      by viewModel.accountDeletion.collectAsState()
    val updateRequired       by viewModel.updateRequired.collectAsState()
    val adsRemoved           by viewModel.adsRemoved.collectAsState()
    // La tienda reabierta (2026-09-06): si el paquete inicial ya se entregó, y qué packs de monedas
    // han gastado ya su bono de bienvenida. Los dos deciden cómo se pinta la tarjeta, no si se cobra
    // — eso lo decide el ViewModel, que es quien tiene la verdad.
    val starterPackGranted   by viewModel.starterPackGranted.collectAsState()
    val coinBonusClaimed     by viewModel.coinBonusClaimed.collectAsState()
    // Reveal de mejora de habilidad: lo dispara el Inventario y se pinta por encima del navbar.
    var abilityReveal by remember { mutableStateOf<AbilityUpgradeCinema?>(null) }
    val infiniteLivesUntil   by viewModel.infiniteLivesUntil.collectAsState()
    val purchaseReveal       by viewModel.purchaseReveal.collectAsState()
    val hasSavedGame         by viewModel.hasSavedGame.collectAsState()
    val savedGameLevel       by viewModel.savedGameLevel.collectAsState()
    val selectedLevel        by viewModel.selectedLevel.collectAsState()
    val playerId             by viewModel.playerId.collectAsState()
    val totalXp              by viewModel.totalXp.collectAsState()
    // P-227: el puesto del ranking que se le vio la última vez. Sale de DataStore, no de la red.
    val lastKnownRank        by viewModel.lastKnownRank.collectAsState()
    val lastKnownRankAt      by viewModel.lastKnownRankAt.collectAsState()
    val claimedCharacterIds  by viewModel.claimedCharacterIds.collectAsState()
    val selectedCompanionId  by viewModel.selectedCompanionId.collectAsState()
    val lives                by viewModel.lives.collectAsState()
    val livesNextRegenAt     by viewModel.livesNextRegenAt.collectAsState()
    val outOfLivesPrompt     by viewModel.outOfLivesPrompt.collectAsState()
    val levelStars           by viewModel.levelStars.collectAsState()
    val muralCelebration     by viewModel.muralCelebration.collectAsState()
    val rankUpCelebration    by viewModel.rankUpCelebration.collectAsState()
    val unlockedCosmetics    by viewModel.unlockedCosmetics.collectAsState()
    val activeCosmetic       by viewModel.activeCosmetic.collectAsState()
    // P-429: la moneda del botón de la vitrina (mapa e Inventario).
    val blocksBadge = remember(gameState.teikoins, gameState.maxUnlockedLevel, unlockedCosmetics, activeCosmetic) {
        hasAffordableBlock(
            gameState.teikoins, gameState.maxUnlockedLevel, unlockedCosmetics,
            activeCosmetic?.takeIf { id -> BlockSkins.all.any { it.id == id } } ?: BlockSkins.DEFAULT_ID
        )
    }
    // Activity para el login (Credential Manager exige Activity, no solo appContext).
    val pgActivity = androidx.activity.compose.LocalActivity.current

    val legalConsentAccepted by viewModel.legalConsentAccepted.collectAsState()
    val tutorialPending by viewModel.showTutorial.collectAsState()
    val welcomeGoogleId by com.cukiti.teikafusion.GoogleManager.googleId.collectAsState()
    // Cuenta respaldada = vinculada a Google. Solo entonces se puede comprar teikoins y
    // competir en el ranking (los anónimos juegan 100% local). Espeja isCloudAccount() del ViewModel.
    val isCloudAccount = welcomeGoogleId != null
    val leaderboardRequiresLink by viewModel.leaderboardRequiresLink.collectAsState()
    val accountSyncing by viewModel.accountSyncing.collectAsState()
    val restartRequested by viewModel.restartRequested.collectAsState()
    val accountLoadingKind by viewModel.accountLoadingKind.collectAsState()
    val cloudAccountConflict by viewModel.cloudAccountConflict.collectAsState()
    val accountNotice by viewModel.accountNotice.collectAsState()
    val perfNotice by viewModel.perfNotice.collectAsState()
    val showPostTutorialBackupPrompt by viewModel.showPostTutorialBackupPrompt.collectAsState()
    val showStarterPackOffer by viewModel.showStarterPackOffer.collectAsState()

    // Panel de AJUSTES: se hospeda aquí (no dentro de HomeScreen) para que sea un modal full-screen
    // POR ENCIMA del BottomNavBar → oscurece todo y bloquea toques detrás (antes el navbar quedaba
    // brillante y tocable sobre el panel). El engranaje de HomeScreen lo abre vía onOpenSettings.
    var showSettings by remember { mutableStateOf(false) }
    /** La ventana de VIDAS (2026-09-06), que se abre desde el corazón de Inicio. */
    var showLives by remember { mutableStateOf(false) }
    // P-250 · la vitrina de bloques: se abre desde Inicio y desde el mapa.
    var showBlockShowcase by remember { mutableStateOf(false) }
    var showMoreSettings by remember { mutableStateOf(false) }
    // Overlays del flujo de juego montados sobre Home: selector de mundos y confirmación de nivel.
    var showBiomePicker by remember { mutableStateOf(false) }
    var showLessons by remember { mutableStateOf(false) }
    var showLevelObjective by remember { mutableStateOf(false) }

    // Solo tienen sentido en HOME; al salir de la pestaña (o entrar a un juego) se cierran.
    val onHomeTab = gameState.status == GameStatus.HOME
    LaunchedEffect(onHomeTab) {
        if (!onHomeTab) {
            showBiomePicker = false
            showLevelObjective = false
        }
    }
    val settingsContext = androidx.compose.ui.platform.LocalContext.current
    val privacyPolicyUrl = androidx.compose.ui.res.stringResource(com.cukiti.teikafusion.R.string.privacy_policy_url)
    val termsOfServiceUrl = androidx.compose.ui.res.stringResource(com.cukiti.teikafusion.R.string.terms_of_service_url)

    // Reinicio del proceso (estilo Clash of Clans) tras login/logout/switch → recarga limpio.
    LaunchedEffect(restartRequested) {
        if (restartRequested) pgActivity?.let { restartApp(it) }
    }

    // ── Lógica de grupo de pantalla ───────────────────────────────────────────
    val inSplash         = gameState.status == GameStatus.SPLASH
    // P-372 · la intro llegó a su pantalla de título (ver [SplashScreen]). Solo decide la música.
    var introTitleShown by rememberSaveable { mutableStateOf(false) }
    // EL MENÚ NO SE MONTA DEBAJO DE LA FIRMA DEL ESTUDIO (2026-09-30). Las tres pestañas son lo más
    // caro de componer (decodifican sus imágenes en el hilo principal), y la splash nativa de Android
    // —azul con el icono— no se va hasta que la app dibuja su PRIMER fotograma: con el menú dentro,
    // el icono se quedaba en pantalla y la animación de CUKITI empezaba a trompicones. Se montan al
    // acabar la firma, con la pantalla en NEGRO (ver «EL NEGRO» en [SplashScreen]): ahí el tirón no se ve.
    var introMenusReady by rememberSaveable { mutableStateOf(false) }
    val mountMenus = !inSplash || introMenusReady
    // Las dos banderas son de ESTA intro: al acabar se bajan (2026-10-01, «la música suena en la
    // firma CUKITI»). Son `rememberSaveable` para que girar el móvil en el título no corte nada,
    // pero eso también las guarda cuando Android mata el proceso en 2º plano. Al volver, el
    // ViewModel es nuevo y la intro empieza otra vez en la firma, pero las banderas volvían a
    // `true`: entraba la música del menú encima de CUKITI y el menú se montaba debajo de ella
    // (el tirón que evita «EL NEGRO»). Si el proceso muere durante la intro, su fase
    // (`rememberSaveable` dentro de [SplashScreen]) vuelve con ellas y siguen cuadrando.
    LaunchedEffect(inSplash) {
        if (!inSplash) {
            introTitleShown = false
            introMenusReady = false
        }
    }

    // ── LA CORTINA DE CARGA MANDA SOBRE EL AUDIO (2026-09-04, pedido del usuario) ──────────────
    // «En la pantalla de carga se escuchan efectos de recompensa o de logro y no se está viendo la
    // ventana que hace el sonido». Y pasaba lo mismo al cargar una cuenta: la fusión con la nube
    // otorga monedas, sube el XP y desbloquea niveles con el overlay de «Cargando tu cuenta» puesto.
    //
    // Todas esas celebraciones YA estaban gateadas por `!inSplash` en la UI; lo que faltaba era
    // atar sus cues a la misma puerta. Se hace en el único sitio por el que sale un SFX
    // ([SoundManager.loadingCurtainUp]), así que vale también para los caminos que aún no se han
    // encontrado. Lo que tiene ventana —la racha, la subida de rango— suena desde su ventana, más
    // abajo, y por tanto no se pierde.
    val loadingCurtain = inSplash || accountSyncing || restartRequested
    LaunchedEffect(loadingCurtain) {
        viewModel.soundManager.loadingCurtainUp = loadingCurtain
    }
    // LA FANFARRIA DE LA RACHA: el arranque la apunta, su celebración la cobra. Las dos caras
    // (banner de «Día N» y cofre de los días de hito) viven en HOME, detrás de la cortina.
    val pendingStreakFanfare by viewModel.pendingStreakFanfare.collectAsState()
    LaunchedEffect(pendingStreakFanfare, loadingCurtain) {
        if (pendingStreakFanfare && !loadingCurtain) {
            viewModel.soundManager.playStreak()
            viewModel.consumeStreakFanfare()
        }
    }
    val inNavTabs        = gameState.status in setOf(
        GameStatus.HOME, GameStatus.STORE, GameStatus.SHOP
    )
    val inGame           = gameState.status !in setOf(
        GameStatus.SPLASH, GameStatus.HOME, GameStatus.STORE,
        GameStatus.SHOP, GameStatus.MODE_SELECTION
    )

    val showBottomNav    = inNavTabs

    // ── Pestaña activa (índice 0-2) ───────────────────────────────────────────
    // Empieza en HOME (índice 1) y se actualiza desde la nav bar o desde el ViewModel.
    var currentTabIndex by remember { mutableIntStateOf(1) }

    // Mapa de status a índice de pestaña
    val tabForStatus = remember {
        mapOf(
            GameStatus.STORE to 0,
            GameStatus.HOME  to 1,
            GameStatus.SHOP  to 2
        )
    }

    // Sincroniza la pestaña con el estado del ViewModel (navegación externa,
    // por ejemplo cuando HomeScreen pulsa "ir a Tienda").
    LaunchedEffect(gameState.status) {
        tabForStatus[gameState.status]?.let { idx ->
            if (currentTabIndex != idx) currentTabIndex = idx
        }
    }

    // ── Música por contexto — ÚNICA fuente de verdad ───────────────────────────
    // Toda la BGM se decide AQUÍ, keyed en gameState.status (re-evalúa en CADA cambio de pantalla).
    // El ViewModel NO arranca/para música por su cuenta (evita que pelee con la navegación).
    //   · Menú/meta (navbar + selección de modo + compañero) → LOFI, una sola pista continua que NO
    //     se reinicia al moverte entre estas zonas (playMusic ignora la misma pista resuelta).
    //   · Menús y meta (navbar, selección de modo, compañero) → LOFI, una sola pista continua.
    //   · PARTIDA real (PLAYING/PAUSED) → la pista del BIOMA (o la del Infinito). **P-07,
    //     2026-07-31**: hasta hoy era NONE («jugando no suena música»); el usuario decidió revertirlo
    //     y darle a cada bioma la suya. Mientras falten los mp3, las 15 pistas caen en `bgm_gameplay`
    //     y como [SoundManager.playMusic] compara el ARCHIVO resuelto, ni entrar en partida ni
    //     cambiar de bioma cortan el loop: el día que exista un asset propio, solo cambia su `resId`.
    //     En PAUSA la pista NO cambia — quien silencia es `pauseGame` (y `resumeBgmIfAllowed` decide
    //     si vuelve), así que la música no se reinicia al abrir y cerrar el menú de pausa.
    //   · Resto de estados de menú/transición → LOFI para que la música no se corte.
    //
    // ── Y TAMBIÉN DECIDE SI SUENA (fix 2026-09-05, reporte del usuario) ────────────────────────
    //
    // El bug: salir de un nivel por Ajustes → «Salir al inicio» dejaba **el juego mudo para el resto
    // de la sesión**, y solo volvía la música si el jugador entraba a una partida, abría la pausa y
    // le daba a CONTINUAR.
    //
    // La causa es que había DOS autoridades sobre el mismo reproductor y solo una se enteraba de la
    // navegación. `SoundManager.bgmPlaybackAllowed` es un pestillo: `pauseBgm()` lo cierra y **solo
    // `resumeBgm()` lo abre**. Abrir el menú de pausa llama a `pauseGame()` → `pauseBgm()`; salir al
    // inicio desde ahí cambia el status, así que este efecto pedía la pista de menús… y no pasaba
    // nada, por cualquiera de los dos caminos:
    //   · si la pista nueva resuelve al MISMO archivo (casi todos los biomas caen hoy en
    //     `bgm_gameplay`, igual que LOFI), `playMusic` retorna temprano a propósito para no cortar
    //     el loop — y el loop estaba pausado;
    //   · y si resuelve a otro archivo (Cristal, Sueño), el player nuevo se prepara pero su
    //     `onPrepared` comprueba el pestillo y **no arranca**.
    // De ahí que ni CONTINUAR ni REINICIAR lo arreglaran (no tocan el pestillo) y que solo lo
    // arreglara CONTINUAR desde la pausa, que es el único que llama a `resumeBgm()`.
    //
    // El arreglo es quitarle la segunda autoridad: quien decide la pista decide también si suena,
    // porque es el mismo sitio y ya sabe el `status`. `pauseGame`/`autoSaveAndPause` siguen llamando
    // a `pauseBgm()` por su cuenta —y está bien: son la respuesta inmediata al gesto—, pero ya no
    // son los únicos que pueden deshacerlo.
    LaunchedEffect(gameState.status, gameState.biomePrimary, introTitleShown) {
        val track = when (gameState.status) {
            // P-372 · la intro calla hasta su pantalla de título; ahí entra ya la música del menú,
            // la misma de Inicio, así que el paso a Inicio no la corta.
            GameStatus.SPLASH ->
                if (introTitleShown) SoundManager.MusicTrack.LOFI else SoundManager.MusicTrack.NONE
            GameStatus.PLAYING, GameStatus.PAUSED ->
                SoundManager.MusicTrack.forBiome(gameState.biomePrimary)
            else -> SoundManager.MusicTrack.LOFI   // HOME/STORE/SHOP/MODE/LEVEL_COMPLETE/…
        }
        viewModel.soundManager.playMusic(track)
        // El ÚNICO silencio deliberado de la BGM es la partida en pausa (y el 2º plano, que no pasa
        // por aquí porque el status no cambia al perder el foco). Todo lo demás debe sonar.
        if (gameState.status == GameStatus.PAUSED) viewModel.soundManager.pauseBgm()
        else if (track != SoundManager.MusicTrack.NONE) viewModel.soundManager.resumeBgm()
    }

    // Tab → ViewModel (para que otras partes del sistema sepan cuál tab está activa)
    //
    // [source] etiqueta CÓMO se llegó a la tienda. Sin él, `store_open` —el numerador del funnel de
    // conversión— no dice nada: no es lo mismo entrar por curiosidad desde el navbar que llegar
    // empujado por quedarse sin vidas. (Hasta 2026-07-31 el evento ni siquiera se disparaba: estaba
    // escrito y documentado como "numerador del funnel" y no lo llamaba nadie, así que la tasa de
    // conversión de la tienda era incalculable.)
    fun onTabSelected(tab: NavTab, source: String = "navbar") {
        val newIndex = when (tab) {
            NavTab.INVENTORY -> 0
            NavTab.HOME      -> 1
            NavTab.SHOP      -> 2
        }
        if (currentTabIndex != newIndex) {
            viewModel.soundManager.playNavTab()
            currentTabIndex = newIndex
            if (tab == NavTab.SHOP) com.cukiti.teikafusion.Analytics.trackStoreOpen(source)
            when (tab) {
                NavTab.INVENTORY -> viewModel.openStore()
                NavTab.HOME      -> viewModel.goHome()
                NavTab.SHOP      -> viewModel.openShop()
            }
        }
    }

    val currentNavTab = when (currentTabIndex) {
        0    -> NavTab.INVENTORY
        2    -> NavTab.SHOP
        else -> NavTab.HOME
    }

    // ── Raíz ─────────────────────────────────────────────────────────────────
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 1 — Las 3 pestañas de navegación: SIEMPRE EN MEMORIA
        // Se muestran/ocultan con alpha+pointerBlock sin destruir la composición.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        LaunchedEffect(mountMenus) { if (mountMenus) introLog("menú montado") }
        if (mountMenus) ZeroLagTabs(
            selectedIndex = currentTabIndex,
            modifier      = Modifier.fillMaxSize(),
            // Jugando, el tablero tapa el menú por completo: que no siga animando por debajo.
            //
            // Estos overlays tapan Inicio entero; suspendemos las animaciones que quedan detrás.
            covered       = inGame || showBiomePicker || showLevelObjective,
            // El tablero es un `Surface` OPACO a pantalla completa: jugando, el menú no se ve ni un
            // píxel. Los overlays de mundo/objetivo NO cuentan aquí — su scrim es traslúcido (0,76 y
            // 0,68) y detrás se sigue viendo Inicio, así que ese sí hay que dibujarlo.
            hiddenByOpaqueLayer = inGame,
            reducedFx     = reducedFx,
            tabs = listOf(
                // 0 — INVENTARIO (P-282: vitrina estilo Candy con monedas y power-ups)
                {
                    InventoryScreen(
                        teikoins = gameState.teikoins,
                        crystalInventory = crystalInventory,
                        claimedCompanionIds = claimedCharacterIds,
                        companionUpgrades = companionUpgrades,
                        companionCharges = companionCharges,
                        selectedPowerUps = companionBelt.slots,
                        lives = lives,
                        infiniteLivesUntil = infiniteLivesUntil,
                        selectedLevel = selectedLevel,
                        // La casilla ya suena al tocarla (onUiClick): aquí solo se abre.
                        onOpenLives = { showLives = true },
                        // (2026-09-25) El Inventario ya no tiene botón JUGAR: se juega desde Inicio.
                        onBuyCharge = { cid -> viewModel.buyCompanionCharge(cid) },
                        upgradeCostPercent = remoteConfig.abilityUpgradeCostPercent,
                        onUpgradeAbility = { cid -> viewModel.upgradeCompanionAbility(cid) },
                        onUiClick = { viewModel.soundManager.playUiClick() },
                        onAbilityUpgradeCinema = { abilityReveal = it },
                        // P-427: la vitrina también se abre desde aquí, donde se buscan «aspectos».
                        activeCosmetic = activeCosmetic,
                        blocksBadge = blocksBadge,
                        onOpenBlocks = { viewModel.soundManager.playUiClick(); showBlockShowcase = true },
                        active = currentTabIndex == 0
                    )
                },
                // 1 — HOME
                {
                    HomeScreen(
                        gameState            = gameState,
                        claimedCharacterIds  = claimedCharacterIds,
                        streak               = streak,
                        winStreak            = winStreak,
                        // Detrás de la cortina de carga no se celebra nada: el banner se descarta
                        // solo a los 3,5 s, así que tapado se consumía entero sin que nadie lo
                        // leyera (mismo motivo que el aviso de progreso restaurado).
                        dailyStreakCelebration = dailyStreakCelebration.takeIf { !loadingCurtain },
                        onConsumeStreakCelebration = { viewModel.consumeDailyStreakCelebration() },
                        lives                = lives,
                        infiniteLivesUntil   = infiniteLivesUntil,
                        avatarType           = avatarType,
                        displayName          = displayName,
                        totalXp              = totalXp,
                        globalRank           = lastKnownRank,
                        globalRankSeenAt     = lastKnownRankAt,
                        selectedLevel        = selectedLevel,
                        onOpenBiomePicker    = { viewModel.soundManager.playUiClick(); showBiomePicker = true },
                        onPlayPressed        = {
                            viewModel.soundManager.playUiClick()
                            showBiomePicker = false
                            showLevelObjective = true
                        },
                        onOpenLeaderboard    = { viewModel.openLeaderboard() },
                        onOpenShop           = { onTabSelected(NavTab.SHOP, source = "monedero") },
                        onAvatarSelected     = { viewModel.selectAvatar(it) },
                        hasSavedGame         = hasSavedGame,
                        savedGameLevel       = savedGameLevel,
                        onContinue           = {
                            viewModel.soundManager.playLaunch()
                            Iris.run { viewModel.continueGame() }
                        },
                        playerId             = playerId,
                        onOpenSettings       = { viewModel.soundManager.playUiClick(); showSettings = true },
                        onOpenLives          = { viewModel.soundManager.playUiClick(); showLives = true },
                        activeCosmetic       = activeCosmetic,
                        onUiClick            = { viewModel.soundManager.playUiClick() },
                        active               = currentTabIndex == 1
                    )
                },
                // 2 — TIENDA / GAME SHOP
                {
                    GameShopScreen(
                        teikoins          = gameState.teikoins,
                        // (2026-09-06) Las VIDAS ya no viven aquí: tienen su propia ventana, que se
                        // abre desde el corazón de Inicio. Ver [LivesOverlay].
                        // (Los params de compañeros se retiraron: la tienda ya no tiene sección de
                        //  compañeros — el modelo de adquisición es V-04, decisión pendiente.)
                        // Comprar teikoins (dinero real) exige cuenta respaldada: sin ella el saldo no
                        // sobrevive a un cambio de móvil, así que el jugador debe vincular primero.
                        isCloudAccount      = isCloudAccount,
                        adsRemoved          = adsRemoved,
                        starterPackGranted  = starterPackGranted,
                        coinBonusClaimed    = coinBonusClaimed,
                        onConnectGoogle     = { pgActivity?.let { viewModel.connectGoogle(it) } },
                        onUiClick           = { viewModel.soundManager.playUiClick() },
                        activeCosmetic      = activeCosmetic,
                        blocksBadge         = blocksBadge,
                        onOpenBlocks        = { viewModel.soundManager.playUiClick(); showBlockShowcase = true },
                        active              = currentTabIndex == 2
                    )
                }
            )
        )

        // Atrás en una pestaña que NO es Inicio → vuelve a Inicio (estándar del género); en Inicio,
        // el atrás del sistema sale de la app. Los overlays con su propio BackHandler (ajustes,
        // picker, ranking…) se componen DESPUÉS en este mismo Box, así que ganan cuando están vivos.
        BackHandler(enabled = showBottomNav && currentTabIndex != 1) {
            onTabSelected(NavTab.HOME)
        }

        // …y en los estados de DECISIÓN de la partida (fin de nivel, derrota, oferta de revivir,
        // recordatorio de objetivos) el atrás NO PUEDE CERRAR LA APP, que es lo que hacía: ahí no
        // hay pestañas (el handler de arriba está apagado) y GameScreen solo cubre PLAYING y PAUSED,
        // así que el botón atrás no lo consumía NADIE y el sistema mataba la app en plena
        // celebración de nivel o encima de la oferta de revivir. Es el mismo agujero que la it. 45
        // tapó en cinco overlays, dejado en los estados de juego.
        //
        // Se COME el gesto sin navegar a propósito: estas pantallas piden una decisión explícita
        // (siguiente nivel / salir / revivir) y elegir una por el jugador sería inventarme la
        // navegación — además de que una de ellas cuesta dinero. Mismo patrón que el onboarding de
        // nombre y la aceptación legal, que ya usan un BackHandler que traga y no hace nada.
        // GameScreen se compone DESPUÉS (CAPA 4), así que sus handlers de PLAYING/PAUSED mandan.
        BackHandler(enabled = inGame) { }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 2 — BottomNavBar: se desliza desde abajo al entrar/salir de nav
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        AnimatedVisibility(
            visible  = showBottomNav,
            enter    = NavNoEnter,
            exit     = NavNoExit,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            BottomNavBar(
                currentTab    = currentNavTab,
                onTabSelected = ::onTabSelected,
                // P-269: la flecha verde — se puede mejorar un power-up de los que LLEVAS (P-282: la
                // misma regla que las casillas del Inventario; con todos contaría siempre).
                inventoryBadge = companionBelt.slots.filter { it in claimedCharacterIds }.any {
                    companionUpgrades.canUpgradeNow(
                        it, gameState.teikoins, crystalInventory, remoteConfig.abilityUpgradeCostPercent
                    )
                },
            )
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 2b — Reveal de «¡HABILIDAD MEJORADA!» (viene del Inventario)
        //
        // Vive AQUÍ, y no dentro de la pestaña, por dos motivos que se pisaban entre sí: tiene que
        // dibujarse por encima del navbar (CAPA 2), y NO puede usar una ventana propia (`Dialog`)
        // — con la app en modo inmersivo, abrir una segunda ventana sacaba las barras del sistema
        // y empujaba todo el contenido de debajo hasta que se cerraba.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        abilityReveal?.let { cinema ->
            AbilityUpgradeReveal(
                cinema = cinema,
                phase = { 0f },
                onContinue = {
                    viewModel.soundManager.playUiClick()
                    abilityReveal = null
                }
            )
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 2c — PROGRESO RESTAURADO DESDE LA NUBE (P-24)
        //
        // La fusión gana el máximo EN SILENCIO, así que quien reinstala o cambia de móvil se
        // encuentra un juego "que parece avanzado" sin saber si de verdad es el suyo. Una banda
        // corta y automática lo dice y se va sola. Banda, no ventana: la app va en modo inmersivo.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Nada de avisos mientras carga: el splash es la ÚNICA pantalla de arranque. Además la
        // sincronización que ESCRIBE este aviso ocurre con el splash puesto, así que sin la puerta
        // el temporizador de 3,5 s se consumía entero tapado y el jugador no llegaba a leerlo.
        val cloudRestoreNotice by viewModel.cloudRestoreNotice.collectAsState()
        cloudRestoreNotice?.takeIf { !inSplash }?.let { text ->
            LaunchedEffect(text) {
                kotlinx.coroutines.delay(3_500)
                viewModel.consumeCloudRestoreNotice()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 74.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF10241C).copy(alpha = 0.94f))
                        .border(1.dp, Color(0xFF4CE0A6).copy(alpha = 0.7f), RoundedCornerShape(50))
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Text("☁  ", fontSize = 13.sp, color = Color(0xFF4CE0A6))
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA FINAL — Actualización obligatoria (Remote Config)
        //
        // Red de seguridad para el peor caso: una versión publicada con un bug que corrompe
        // guardados. Se levanta `min_supported_version_code` desde la consola y esta capa tapa el
        // juego entero. Por defecto vale 0, así que nunca aparece sola. Va la ÚLTIMA para que nada
        // (ni el navbar ni el reveal) quede por encima, y sin BackHandler que la cierre.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (updateRequired) {
            UpdateRequiredOverlay(
                message = viewModel.updateMessageText(),
                onOpenStore = {
                    val pkg = settingsContext.packageName.removeSuffix(".debug")
                    runCatching {
                        settingsContext.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                "market://details?id=$pkg".toUri()
                            )
                        )
                    }.onFailure {
                        settingsContext.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                "https://play.google.com/store/apps/details?id=$pkg".toUri()
                            )
                        )
                    }
                }
            )
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 3 — Flujo de juego montado sobre Home: picker de mundos + popup de objetivo.
        // Ambos son overlays semitransparentes (Home queda visible, oscurecido) — ya no hay
        // pantalla MODE_SELECTION. Solo tienen sentido en la pestaña HOME.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Las ilustraciones del mapa se decodifican ANTES de abrirlo, mientras se está en Inicio: si no, al
        // entrar rápido se veía el fondo liso del bioma hasta que llegaba la imagen (2026-09-30).
        if (onHomeTab) {
            com.cukiti.teikafusion.ui.WorldMapArtPrewarm(gameState.maxUnlockedLevel, selectedLevel)
        }
        AnimatedVisibility(
            visible  = onHomeTab && showBiomePicker,
            enter    = NavNoEnter,
            exit     = NavNoExit,
            modifier = Modifier.fillMaxSize()
        ) {
            // EL MAPA DE MUNDOS (2026-09-23) sustituye a la lista «Elige tu mundo» (BiomePickerOverlay,
            // borrada el 2026-09-25, P-259). `showBiomePicker` conserva el nombre: es la bandera del mapa.
            com.cukiti.teikafusion.ui.WorldMapOverlay(
                maxUnlockedLevel = gameState.maxUnlockedLevel,
                levelStars       = levelStars,
                selectedLevel    = selectedLevel,
                avatarType       = avatarType,
                abyssRevealPending = !abyssCardRevealSeen,
                onAbyssRevealShown = { viewModel.markAbyssCardRevealSeen() },
                onSelectLevel    = { level -> viewModel.setSelectedLevel(level) },
                onLevelTap       = { level -> viewModel.soundManager.playBiomeSelection(com.cukiti.teikafusion.model.LevelMap.biomeFor(level)) },
                onUiClick        = { viewModel.soundManager.playUiClick() },
                onOpenLessons    = { showLessons = true },
                activeCosmetic   = activeCosmetic,
                onOpenBlocks     = { viewModel.soundManager.playUiClick(); showBlockShowcase = true },
                blocksBadge      = blocksBadge,
                onDismiss        = { showBiomePicker = false }
            )
        }

        // ── LAS LECCIONES ────────────────────────────────────────────────────────────────────
        // Encima del picker a propósito: se abre DESDE él y cerrarlas devuelve al mapa de mundos,
        // que es de donde venías. Entrar a una lección cierra las dos capas — el tutorial se juega
        // a pantalla completa y dejar ventanas detrás sería volver a ellas al terminar.
        AnimatedVisibility(
            visible  = onHomeTab && showLessons,
            enter    = NavNoEnter,
            exit     = NavNoExit,
            modifier = Modifier.fillMaxSize()
        ) {
            com.cukiti.teikafusion.ui.LessonsPanel(
                maxUnlockedLevel = gameState.maxUnlockedLevel,
                onPlayLesson = { level ->
                    showLessons = false
                    showBiomePicker = false
                    Iris.run { viewModel.startGame(level) }
                },
                onUiClick = { viewModel.soundManager.playUiClick() },
                onDismiss = { showLessons = false }
            )
        }

        AnimatedVisibility(
            visible  = onHomeTab && showLevelObjective,
            enter    = NavNoEnter,
            exit     = NavNoExit,
            modifier = Modifier.fillMaxSize()
        ) {
            // Mientras el panel está a la vista, la ronda se va construyendo en un hilo de fondo.
            // Ese hueco es gratis y ahorra los ~236 ms que el toque de JUGAR NIVEL pasaba
            // construyéndola con la pantalla congelada (ver GameViewModel.prepareRound).
            LaunchedEffect(selectedLevel, selectedCompanionId, companionUpgrades) {
                viewModel.prepareRound(selectedLevel)
            }
            LevelObjectiveOverlay(
                level = selectedLevel,
                objective = remember(selectedLevel) {
                    com.cukiti.teikafusion.model.LevelLayouts.get(selectedLevel)?.second
                },
                reducedFx = reducedFx,
                onUiClick = { viewModel.soundManager.playUiClick() },
                onPlay = {
                    showLevelObjective = false
                    // La presentación de objetivos vuela TAMBIÉN desde aquí (2026-08-24, decisión
                    // del usuario). Antes esta era la única entrada que la apagaba —el panel ya
                    // enseñaba las estrellas, así que se consideró repetido—, y el resultado es que
                    // el camino MÁS habitual para empezar un nivel era justo el único sin ceremonia.
                    Iris.run { viewModel.startGame(selectedLevel) }
                },
                onDismiss = { showLevelObjective = false },
                // SALTAR TUTORIAL (2026-09-01): solo aparece mientras la campana sigue pendiente.
                // Quien ya la hizo y vuelve a un nivel de leccion no ve el boton — no tendria que
                // saltar nada.
                onSkipTutorial = if (tutorialPending &&
                    com.cukiti.teikafusion.model.TutorialCampaign.isTutorialLevel(selectedLevel)
                ) {
                    { showLevelObjective = false; viewModel.skipTutorial() }
                } else null,
                // P-282: LLEVA A LA PARTIDA. Fuera del tutorial y solo con algún compañero.
                powerUps = if (com.cukiti.teikafusion.model.TutorialCampaign.isTutorialLevel(selectedLevel)) null
                else PowerUpPickerModel(
                    available = pickablePowerUps(claimedCharacterIds),
                    selected = companionBelt.slots,
                    charges = companionCharges,
                    teikoins = gameState.teikoins,
                    onToggle = { viewModel.toggleMatchPowerUp(it) },
                    onBuy = { viewModel.buyCompanionCharge(it) },
                ),
            )
        }

        // Panel de dificultad del INFINITO «La Prensa» (partida nueva).
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 4 — GameScreen: overlay instantáneo (fade rápido)
        // Las pantallas de nav permanecen vivas debajo para retorno instantáneo.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        AnimatedVisibility(
            visible  = inGame,
            // Entrada al juego SIN transición: el fade+scale mantenía vivo el menú entero mientras
            // el tablero y sus Lotties montaban, y ese solape era el tirón de entrada a partida.
            enter    = NavNoEnter,
            exit     = NavNoExit,
            modifier = Modifier.fillMaxSize()
        ) {
            // PAUSA = las animaciones AMBIENTE del tablero se paran (bloque 20, 2026-07-30). El
            // GameScreen sigue compuesto debajo del overlay de pausa a propósito (volver debe ser
            // instantáneo), pero mientras el jugador está en los ajustes nada tiene que pedir
            // fotogramas. Ver [LocalAnimationsActive].
            CompositionLocalProvider(
                LocalAnimationsActive provides
                    (gameState.status != GameStatus.PAUSED),
                LocalReducedFx provides reducedFx
            ) {
                ProvideBlockSkin(activeCosmetic = activeCosmetic) {
                    GameScreen(viewModel = viewModel)
                }
            }
        }

        // El nivel del Rey (95) ahora es ajedrez integrado DENTRO de GameScreen (CAPA 4) —
        // ya no hay pantalla de ajedrez separada.

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 5 — Splash: cubre todo hasta que el juego está listo
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        AnimatedVisibility(
            visible  = inSplash,
            enter    = NavNoEnter,
            // La intro se funde ENCIMA del destino ya montado (Inicio, términos o tutorial): ver
            // [INTRO_LEAVE_MS]. Antes salía de golpe tras fundirse ella sola, y el fundido revelaba
            // Inicio un instante aunque el destino fuera otro.
            // El fundido espera [INTRO_LEAVE_DELAY_MS]: el primer fotograma del destino es pesado (todo
            // lo gateado por `!inSplash` se activa a la vez) y, arrancando con él, el fundido saltaba
            // a trompicones (77–133 ms, medido en release en un A15).
            exit     = androidx.compose.animation.fadeOut(
                androidx.compose.animation.core.tween(INTRO_LEAVE_MS, delayMillis = INTRO_LEAVE_DELAY_MS)
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            SplashScreen(
                // La cuenta puede resolverse antes que DataStore en un arranque local. Esperar
                // también el perfil garantiza que términos, tutorial y progreso ya tengan su valor
                // real cuando finishSplash decida entre Home y el nivel 1.
                accountReady = initialAccountSyncFinished && playerProfileLoaded,
                reducedFx = reducedFx,
                onStudioSting = { viewModel.soundManager.playIntroSting() },
                onTitleShown = { introTitleShown = true },
                onStudioFinished = { introMenusReady = true },
                onStart = {
                    viewModel.soundManager.playIntroStart()
                    Haptics.tap()
                },
                onComplete = { viewModel.finishSplash() }
            )
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 6 — Leaderboard: overlay de pantalla completa desde la derecha
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        AnimatedVisibility(
            visible  = showLeaderboard,
            enter    = NavNoEnter,
            exit     = NavNoExit,
            modifier = Modifier.fillMaxSize()
        ) {
            LeaderboardScreen(
                entries           = leaderboardEntries,
                isLoading         = leaderboardLoading,
                isError           = leaderboardError,
                currentUid        = currentUid,
                onClose           = { viewModel.closeLeaderboard() },
                onRetry           = { viewModel.openLeaderboard() },
                requiresLink      = leaderboardRequiresLink,
                onConnectGoogle   = { pgActivity?.let { viewModel.connectGoogle(it) } }
            )
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 7 — "Sin vidas": se intentó jugar un nivel con 0 vidas.
        // Encima de TODO (incluido Game Over) para que "Reintentar"/"Jugar" nunca
        // queden mudos: explica la regeneración y ofrece comprar o ir a la tienda.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (outOfLivesPrompt) {
            OutOfLivesOverlay(
                teikoins         = gameState.teikoins,
                livesNextRegenAt = livesNextRegenAt,
                onBuyLife        = { viewModel.buyLivesAndContinue(1) },
                onMoreLives      = {
                    viewModel.dismissOutOfLivesPrompt()
                    showLives = true
                },
                onDismiss        = { viewModel.dismissOutOfLivesPrompt() }
            )
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 8 — Onboarding obligatorio de identidad.
        // Espera la carga local y el primer intento de restauración cloud para no pedirle
        // nuevamente el nombre a un jugador que ya lo tenía guardado.
        // No se compone durante el tutorial; una sesión Google sin nombre se resuelve después.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Solo los usuarios con cuenta vinculada ponen nombre (ranking/nube). Los anónimos NUNCA ven
        // esta pantalla: juegan sin nombre. welcomeGoogleId != null ⇒ sesión de Google activa.
        if (
            !inSplash &&
            legalConsentAccepted &&
            !tutorialPending &&
            welcomeGoogleId != null &&
            playerProfileLoaded &&
            initialAccountSyncFinished &&
            displayName.isBlank()
        ) {
            PlayerNameOnboarding(
                isSaving = isSavingName,
                onSave = { viewModel.saveDisplayName(it) }
            )
        }

        // CAPA 8a — aceptación inicial de documentos. Se muestra antes de elegir cuenta y se
        // desbloquea sólo con la versión actual de términos y privacidad aceptada.
        if (
            !inSplash &&
            initialAccountSyncFinished &&
            !legalConsentAccepted
        ) {
            LegalConsentOverlay(
                onOpenPrivacy = {
                    runCatching {
                        settingsContext.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, privacyPolicyUrl.toUri())
                        )
                    }
                },
                onOpenTerms = {
                    runCatching {
                        settingsContext.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, termsOfServiceUrl.toUri())
                        )
                    }
                },
                onAccept = { viewModel.acceptLegalDocuments() }
            )
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 9 — Cinemática "MURAL COMPLETO": al superar el último nivel de un
        // bioma, encima de TODO. Premio único ya concedido por el ViewModel.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Las celebraciones globales pueden quedar pendientes mientras el ViewModel carga las
        // preferencias y sincroniza la cuenta. Nunca deben montarse encima del splash: el jugador
        // tiene que ver primero la pantalla de carga completa y, al salir, las celebraciones quedan
        // retenidas en sus StateFlow para aparecer normalmente.
        if (!inSplash) {
            muralCelebration?.let { celebration ->
                MuralCompleteOverlay(
                    chapterIndex = celebration.chapterIndex,
                    reward       = celebration.reward,
                    newBlockSkinId = celebration.newBlockSkinId,
                    onDismiss    = { viewModel.dismissMuralCelebration() }
                )
            }
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 9a — SUBIDA DE RANGO (V-03): encima de casi todo; toca o auto-cierra.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (!inSplash) {
            rankUpCelebration?.let { rank ->
                // El cue vive AQUÍ desde el 2026-09-04 y no en `awardXp`: el XP del primer juego del
                // día se otorga en el arranque, con el splash puesto, así que la fanfarria de rango
                // salía sola sobre la barra de carga mientras esta ventana esperaba a que el splash
                // se fuera. Suena la ventana, no el cálculo.
                LaunchedEffect(rank) { viewModel.soundManager.playRankUp() }
                RankUpOverlay(
                    rank = rank,
                    onDismiss = { viewModel.consumeRankUpCelebration() }
                )
            }
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 9a — VIDAS: todo el mecanismo en una ventana, sin scroll (2026-09-06).
        //
        // Vive AQUÍ y no dentro de `HomeScreen` por el mismo motivo que los ajustes: tiene que
        // dibujarse por encima del `BottomNavBar` y tragarse los toques de detrás. Dentro de la
        // pantalla quedaría por debajo de la barra y se podría cambiar de pestaña con la ventana
        // abierta.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (showLives) {
            LivesOverlay(
                lives              = lives,
                maxLives           = GameViewModel.LIVES_MAX,
                livesNextRegenAt   = livesNextRegenAt,
                infiniteLivesUntil = infiniteLivesUntil,
                teikoins           = gameState.teikoins,
                infinitePacks      = GameViewModel.INFINITE_LIVES_PACKS,
                onBuyLives         = { amount -> viewModel.buyLives(amount) },
                onBuyInfiniteLives = { hours -> viewModel.buyInfiniteLives(hours) },
                onDismiss          = { viewModel.soundManager.playUiClick(); showLives = false },
            )
        }

        // ── P-250 · LA VITRINA DE BLOQUES ─────────────────────────────────────────────────────
        // Encima del mapa a propósito: también se abre desde su cabecera, y cerrarla devuelve a él.
        if (showBlockShowcase) {
            BlockShowcaseOverlay(
                teikoins          = gameState.teikoins,
                maxUnlockedLevel  = gameState.maxUnlockedLevel,
                unlockedCosmetics = unlockedCosmetics,
                activeCosmetic    = activeCosmetic,
                animationsOn      = !reducedFx,
                onBuy             = { id -> viewModel.buyCosmetic(id) },
                onEquip           = { id -> viewModel.equipCosmetic(id) },
                onUiClick         = { viewModel.soundManager.playUiClick() },
                onDismiss         = { showBlockShowcase = false },
            )
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 9b — AJUSTES: modal full-screen POR ENCIMA del BottomNavBar (oscurece todo y bloquea
        // toques detrás). Antes vivía dentro de HomeScreen, por debajo del navbar.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (showSettings) {
            SettingsOverlay(
                musicVolume       = musicVolume,
                sfxVolume         = sfxVolume,
                vibrationEnabled  = vibrationEnabled,
                tiltEnabled       = tiltEnabled,
                isMuted           = isMuted,
                reducedFx         = reducedFx,
                notificationsEnabled = notificationsEnabled,
                onMusicVolume     = { viewModel.setMusicVolume(it) },
                onSfxVolume       = { viewModel.setSfxVolume(it) },
                onSfxPreview      = { viewModel.soundManager.playUiClick() },
                onToggleVibration = { viewModel.soundManager.playUiClick(); viewModel.toggleVibration() },
                onToggleTilt      = { viewModel.soundManager.playUiClick(); viewModel.toggleTilt() },
                onToggleMute      = { viewModel.soundManager.playUiClick(); viewModel.toggleMute() },
                onToggleReducedFx = { viewModel.soundManager.playUiClick(); viewModel.toggleReducedFx() },
                onToggleNotifications = { viewModel.soundManager.playUiClick(); viewModel.toggleNotifications() },
                onDismiss       = { viewModel.soundManager.playUiClick(); showSettings = false },
                onOpenMoreSettings = {
                    viewModel.soundManager.playUiClick()
                    showSettings = false
                    showMoreSettings = true
                },
                onConnectGoogle   = { pgActivity?.let { viewModel.connectGoogle(it) } },
                onLogOut          = { viewModel.logOut() },
            )
        }

        if (showMoreSettings) {
            // El SDK de UMP decide si este jugador necesita poder cambiar su consentimiento (solo
            // EEE/RU). Se consulta AQUÍ y no dentro del overlay porque el estado cambia cuando
            // `ConsentManager.gather` resuelve, y este `if` recompone al abrir el panel: así la fila
            // aparece en la primera apertura posterior al formulario, no en la siguiente sesión.
            val privacyOptionsAction: (() -> Unit)? = pgActivity
                ?.takeIf { com.cukiti.teikafusion.ConsentManager.isPrivacyOptionsRequired(settingsContext) }
                ?.let { activity -> { com.cukiti.teikafusion.ConsentManager.showPrivacyOptions(activity) } }
            MoreSettingsOverlay(
                currentName = displayName,
                onChangeName = { viewModel.saveDisplayName(it) },
                onPrivacyPolicy = {
                    runCatching {
                        settingsContext.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, privacyPolicyUrl.toUri())
                        )
                    }
                },
                onPrivacyOptions = privacyOptionsAction,
                onTermsOfService = {
                    runCatching {
                        settingsContext.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, termsOfServiceUrl.toUri())
                        )
                    }
                },
                onContact = {
                    runCatching {
                        settingsContext.startActivity(
                            android.content.Intent(
                                android.content.Intent.ACTION_SENDTO,
                                "mailto:andersonkayle02@gmail.com".toUri()
                            ).apply {
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Teika Fusion — contacto")
                                // P-435: soporte necesita saber QUIÉN escribe y con QUÉ versión; el
                                // jugador escribe debajo. Sin esto, la primera respuesta era pedirlo.
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "\n\n\n— No borres estas líneas, nos ayudan a ayudarte —\n" +
                                        "ID de jugador: ${playerId ?: "sin ID"}\n" +
                                        "Versión: ${com.cukiti.teikafusion.BuildConfig.VERSION_NAME} " +
                                        "(${com.cukiti.teikafusion.BuildConfig.VERSION_CODE})\n" +
                                        "Móvil: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · " +
                                        "Android ${android.os.Build.VERSION.RELEASE}"
                                )
                            }
                        )
                    }
                },
                onShare = {
                    runCatching {
                        settingsContext.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "Estoy jugando Teika Fusion 🎮 ¿Puedes superar mi puntuación?"
                                )
                            }.let { android.content.Intent.createChooser(it, "Compartir Teika Fusion") }
                        )
                    }
                },
                onResetProgress = { viewModel.resetProgress() },
                onDeleteAccount = { viewModel.deleteAccount() },
                onDismiss = {
                    viewModel.soundManager.playUiClick()
                    showMoreSettings = false
                }
            )
        }

        // Resultado del borrado de cuenta. El caso DELETED no se muestra: la app se reinicia sola
        // al flujo inicial local, que ya es la confirmación. Los demás SÍ hay que decirlos —
        // un borrado a medias que el jugador cree completo es exactamente lo que Play penaliza.
        accountDeletion?.let { outcome ->
            if (outcome != com.cukiti.teikafusion.viewmodel.AccountDeletionOutcome.DELETED) {
                // Ventana en la jerarquía normal, NO `AlertDialog`: en modo inmersivo una ventana del
                // sistema devuelve las barras y desplaza el layout entero (decisión congelada).
                ConfirmOverlay(
                    title = "No se pudo completar",
                    message = when (outcome) {
                        com.cukiti.teikafusion.viewmodel.AccountDeletionOutcome.OFFLINE ->
                            "Necesitas conexión a internet para eliminar tu cuenta. " +
                                "No se ha borrado nada; inténtalo de nuevo con datos o wifi."
                        com.cukiti.teikafusion.viewmodel.AccountDeletionOutcome.REQUIRES_RECENT_LOGIN ->
                            "Tus datos ya se eliminaron, pero por seguridad hace falta un " +
                                "inicio de sesión reciente para borrar la cuenta de acceso. " +
                                "Vuelve a entrar con Google y repite «Eliminar mi cuenta»."
                        else ->
                            "Hubo un problema al eliminar tu cuenta y no se ha borrado nada. " +
                                "Inténtalo de nuevo en unos minutos."
                    },
                    confirmLabel = "Entendido",
                    dismissLabel = null,   // un aviso, no una decisión: una sola salida
                    onConfirm = { viewModel.consumeAccountDeletionOutcome() },
                    onDismiss = { viewModel.consumeAccountDeletionOutcome() }
                )
            }
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 9c — Única invitación automática a guardar: al terminar el tutorial 4.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (showPostTutorialBackupPrompt && onHomeTab && !showSettings) {
            PostTutorialBackupPrompt(
                onConnectGoogle = {
                    // Mantener la invitación activa bajo Credential Manager bloquea UMP hasta que
                    // termine esta decisión. Si cancela, puede intentarlo de nuevo o tocar Ahora no;
                    // si conecta, el reinicio limpio elimina este estado transitorio.
                    pgActivity?.let { viewModel.connectGoogle(it) }
                },
                onDismiss = { viewModel.dismissPostTutorialBackupPrompt() }
            )
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 9d — La ÚNICA oferta automática del paquete inicial (P-210): al cerrar el capítulo 1.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Va DEBAJO de la carga de cuenta y por encima del resto, pero espera a que el jugador esté
        // en Inicio: el capítulo se cierra dentro de la partida, y encadenar la cinemática del mural
        // con una ventana de compra sería cobrarle la celebración. Cuando llega a Inicio, la fiesta
        // ya pasó.
        if (showStarterPackOffer && onHomeTab && !showSettings && !inSplash) {
            val starterProduct by com.cukiti.teikafusion.BillingManager.products.collectAsState()
            StarterPackOffer(
                product = starterProduct.firstOrNull {
                    it.productId == com.cukiti.teikafusion.model.StorePacks.STARTER_PACK_ID
                },
                onBuy = {
                    val p = starterProduct.firstOrNull {
                        it.productId == com.cukiti.teikafusion.model.StorePacks.STARTER_PACK_ID
                    }
                    if (p != null) pgActivity?.let { act -> com.cukiti.teikafusion.BillingManager.purchase(act, p) }
                    viewModel.dismissStarterPackOffer()
                },
                onDismiss = { viewModel.dismissStarterPackOffer() },
                isCloudAccount = isCloudAccount,
                onConnectGoogle = {
                    viewModel.soundManager.playUiClick()
                    viewModel.dismissStarterPackOffer()
                    pgActivity?.let { viewModel.connectGoogle(it) }
                },
            )
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 10 — Carga de cuenta: cubre TODO mientras se sincroniza con la nube o se reinicia
        // (login/logout/switch). Lo más alto del stack para que el jugador nunca vea estado a medias.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // Suprimido mientras el splash está visible: durante el arranque la ÚNICA pantalla de carga es
        // el splash (que ya espera a que la cuenta cargue), evitando ver dos pantallas de carga encima.
        // Este overlay queda para operaciones fuera del splash: cerrar sesión / vincular cuenta.
        if ((accountSyncing || restartRequested) && !inSplash) {
            when (accountLoadingKind) {
                com.cukiti.teikafusion.viewmodel.AccountLoadingKind.LOGGING_OUT ->
                    AccountLoadingOverlay(
                        message = "Cerrando sesión…",
                        subtitle = "Guardando tu progreso de forma segura"
                    )
                else ->
                    AccountLoadingOverlay(
                        message = "Cargando tu cuenta…",
                        subtitle = "Sincronizando tu progreso"
                    )
            }
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 11 — Conflicto de cuenta al vincular: el jugador elige conservar la nube
        // (cuenta anterior) o su progreso local. Encima de TODO, incluida la carga de cuenta.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        cloudAccountConflict?.let { conflict ->
            CloudAccountConflictDialog(
                conflict = conflict,
                onUseCloud = { viewModel.resolveCloudAccountConflict(useCloudAccount = true) },
                onKeepLocal = { viewModel.resolveCloudAccountConflict(useCloudAccount = false) }
            )
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 13 — Revelación ÉPICA de compra: al comprar cualquier artículo (o al ganar vidas
        // infinitas por hito de racha), un cofre dorado revela lo conseguido + el bonus sorpresa.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        if (!inSplash) {
            purchaseReveal?.let { reveal ->
                PurchaseRevealOverlay(reveal = reveal, onDismiss = { viewModel.consumePurchaseReveal() })
            }
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 14 — Aviso de cuenta (banda superior, NO bloquea): fallos de vinculación que antes
        // eran mudos. Lo último del stack para que se vea también sobre las superficies de cuenta y Ajustes.
        // Pero NO sobre el splash: al ser lo último del stack se dibujaba ENCIMA de la pantalla de
        // carga, y estos avisos nacen justo en la sincronización inicial. Espera a que el splash se
        // quite; el valor se conserva en el StateFlow, así que se lee entero al llegar a HOME.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        accountNotice?.takeIf { !inSplash }?.let { notice ->
            AccountNoticeBanner(message = notice, onDismiss = { viewModel.consumeAccountNotice() })
        }
        // P-369: «Reducir efectos» se activó solo porque la partida iba a tirones. Misma banda, no bloquea.
        if (accountNotice == null) perfNotice?.let { notice ->
            AccountNoticeBanner(message = notice, onDismiss = { viewModel.consumePerfNotice() })
        }

        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        // CAPA 15 — EL IRIS (2026-09-23): círculo negro al entrar y salir de un nivel. Encima de
        // TODO a propósito: tiene que tapar también los avisos y ventanas mientras la pantalla cambia.
        // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
        IrisOverlay(
            reducedFx = reducedFx,
            onStart = { viewModel.soundManager.playIrisTransition() },
            onOpen = { viewModel.soundManager.playIrisOpen() }
        )

        // CAPA 16 — P-371: el contador de FPS de debug/release, encima de TODO (también del
        // iris) para medir cualquier pantalla. Mismo sitio que tenía en la partida.
        if (fpsOverlayAvailable && fpsOverlayOn.value) {
            FpsOverlay(modifier = Modifier.align(Alignment.TopStart).padding(top = 90.dp, start = 10.dp))
        }
    }
}

/**
 * Reinicia el proceso de la app (estilo Clash of Clans): relanza el launcher activity con la tarea
 * limpia y mata el proceso, de modo que el ViewModel y todos los singletons se recreen desde cero y
 * carguen la cuenta correcta. Se usa tras login, logout o cambio de cuenta.
 */
private fun restartApp(activity: android.app.Activity) {
    val intent = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
    intent?.addFlags(
        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK or
            android.content.Intent.FLAG_ACTIVITY_NEW_TASK
    )
    activity.startActivity(intent)
    activity.finishAffinity()
    Runtime.getRuntime().exit(0)
}
