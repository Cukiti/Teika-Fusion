package com.cukiti.teikafusion.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
enum class GameStatus {
    SPLASH, HOME, MODE_SELECTION, PLAYING, PAUSED, LEVEL_COMPLETE, LEVEL_COMPLETE_PENDING,
    GAME_OVER, REVIVE_OFFER,
    STORE,
    // OBSOLETO: la pestaña «H. Teika» se fusionó con Items en la pantalla de Inventario (rediseño
    // 2026-07-27; el navbar es Inventario · Inicio · Tienda). Se conserva SOLO para no romper la
    // deserialización de saves antiguos; se normaliza a STORE, que es donde vive ahora ese contenido.
    SKILL_TREE,
    OBJECTIVES_REMINDER,
    SHOP,
    // OBSOLETO: la pestaña Colección / TeikaDex se eliminó (arte pesado, poca utilidad). Se
    // conserva SOLO para no romper la deserialización de saves antiguos; se normaliza a HOME.
    PROFILE,
    // OBSOLETO: el minijuego de ajedrez del Rey se eliminó por completo (el nivel 95 es ahora un
    // tablero normal con muros). Este valor se conserva SOLO para no romper la deserialización de
    // saves antiguos que pudieran tenerlo; ningún código lo produce ni lo consume.
    CHESS
}

/** Estados obsoletos de saves antiguos → pantalla segura actual. */
fun GameStatus.normalizeObsolete(): GameStatus = when (this) {
    GameStatus.MODE_SELECTION, GameStatus.CHESS, GameStatus.PROFILE -> GameStatus.HOME
    GameStatus.SKILL_TREE -> GameStatus.STORE   // «H. Teika» vive dentro del Inventario
    else -> this
}

// (RETIRADO 2026-09-23) `MiniBlackHole`: el agujero negro del Espacio Profundo, sustituido por la
// BRECHA EN EL CASCO (P-248), sustituida a su vez el 2026-09-25 por la LLUVIA DE METEORITOS
// (P-294, ver [MeteorShower]).

/**
 * Motor físico Suika: snapshot PERSISTIBLE de un cuerpo de Teika. Las velocidades no se
 * guardan (al cargar, los cuerpos despiertan en reposo y la gravedad los re-asienta).
 *
 * **El modo kinemático tampoco se guarda, y sigue sin guardarse a propósito.** Lo comparten CUATRO
 * mecánicas: el rapto de la raíz y Garra deben sobrevivir; la retención de la fusión
 * T3+T3 (150-380 ms) y la espera del tutorial vuelven dinámicas al cargar. Guardar el flag devolvería cuerpos retenidos sin
 * nadie que los suelte, así que lo que se restaura es **la mecánica, no la bandera**:
 * Garra restaura la retención desde [GameState.clawTrip], no desde esta bandera.
 * [GameState.plantAbduction] es persistente desde 2026-09-04 y `continueGame` repone el agarre
 * sobre el cuerpo que ella nombra. Todo lo demás vuelve dinámico, que es lo correcto.
 */
@Serializable
data class TeikaBodySave(
    val id: String,
    val type: TeikaType,
    val x: Float,
    val y: Float,
    val frozenTurns: Int = 0,
    /** Compatibilidad con guardados antiguos; ignorado por la física y las fusiones. */
    val colorType: Int = -1,
    /** **P-238 · LAS POMPAS**: líneas que le faltan a su pompa para reventar (0 = sin pompa). */
    val bubble: Int = 0,
)

/**
 * Estado inmutable que representa la totalidad del tablero y partida en un momento dado.
 * Diseñado para integrarse perfectamente con StateFlow y Jetpack Compose.
 */
@Serializable
data class GameState(
    // Mapa que asocia cada coordenada (Position) a su contenido actual.
    val grid: Map<Position, CellContent> = emptyMap(),
    // Las piezas actualmente disponibles para que el jugador elija en la "mano".
    val availablePieces: List<Piece> = emptyList(),
    /**
     * **P-75 · BANDEJA DE UNA, CON HORIZONTE**: las [HorizonTray.HORIZON] fichas que vienen después
     * de la que se está jugando. Vacío en los niveles normales (allí la bandeja ES el futuro).
     *
     * **Se guarda**, y ese es el punto: si el horizonte se resortease al retomar la partida, la
     * promesa que hace el nivel —«sabes lo que viene»— duraría hasta el primer guardado. Los saves
     * anteriores a P-75 no traen el campo y la cola se rellena sola al primer reparto.
     */
    val pieceHorizon: List<Piece> = emptyList(),
    // (RETIRADO 2026-08-09) Aquí vivía el estado de WALLBLOCK: `wallblockActivePiece/Position`,
    // `wallblockHeldPiece`, `wallblockHoldUsed`, `wallblockAttic` y `wallblockChain`. El modo se
    // eliminó del juego entero (decisión del usuario); `ignoreUnknownKeys` traga los guardados que
    // aún traigan estos campos.
    // Puntuación acumulada
    val score: Int = 0,
    // Nivel actual (Usado internamente para dificultad)
    val level: Int = 1,
    // Tipo de Teika objetivo para pasar de nivel
    val targetTeika: TeikaType = TeikaType.TEIKA_2,
    // Teikas creados por tipo en este nivel
    val teikasCreatedInLevel: Map<TeikaType, Int> = emptyMap(),
    // Teikas (Sandías máximas) formadas en total
    val teikasFormed: Int = 0,
    // Estado general del juego
    val status: GameStatus = GameStatus.SPLASH,
    // Eventos visuales efímeros (Game Feel)
    @Transient val events: List<GameEvent> = emptyList(),
    /**
     * **P-229 · LA FICHA CONDENADA** (2026-09-07b). Ids de las fichas de la bandeja que **ya no
     * tienen ni una colocación legal** en el tablero actual.
     *
     * Ocupa el sitio que dejó el reparador de bandeja, y no es casualidad: hasta hoy, una ficha que
     * se quedaba sin sitio **se cambiaba por otra**. Ahora que quedarse sin jugada vuelve a ser una
     * derrota, esa misma ficha es una muerte súbita que el jugador no ve venir — puede volverse
     * imposible dos turnos antes de que él lo note, mientras la bandeja la sigue enseñando entera.
     * Así que el juego deja de arreglarlo y pasa a **decirlo**: la ficha se apaga en la bandeja.
     *
     * Se calcula UNA vez por turno (`GameViewModel.markUnplayableTrayPieces`, al cerrar el turno y
     * tras los cambios de tablero que no son del jugador), nunca por frame: son 3 fichas × 64
     * celdas, barato una vez y caro sesenta veces por segundo.
     *
     * `@Transient` porque es una FOTO del tablero: al cargar una partida se recalcula en el primer
     * cierre de turno, y hasta entonces no enseñar el aviso es mejor que enseñarlo mal.
     */
    @Transient val unplayablePieceIds: Set<String> = emptySet(),
    /**
     * **P-228 · QUIÉN TE DEJÓ SIN SITIO** (2026-09-07b). El bioma cuyo castigo **quitó espacio
     * jugable en este turno**, o `null` si el tablero se llenó jugando.
     *
     * Sin esto, el cartel «SIN SALIDA» dice lo mismo tanto si te has atascado tú como si el Corte
     * Ninja acaba de partir el tablero en dos: la misma frase para un error propio y para un
     * castigo ajeno. Ahora que el reparador de bandeja ya no existe, lo segundo puede matar de
     * golpe, y una derrota que no se entiende se lee como una trampa.
     *
     * Se deduce comparando el hueco libre ANTES y DESPUÉS del tick de bioma, en un solo sitio, en
     * vez de cablear catorce mecánicas: la pregunta que importa no es cuál de ellas se disparó, sino
     * si el bioma te quitó sitio. Se reevalúa en cada tick, así que nunca queda obsoleto.
     */
    @Transient val lastSqueezeBiome: BiomeType? = null,
    // Combo de turnos consecutivos
    val comboStreak: Int = 0,
    // 1 = turno de gracia activo (el jugador rompió el combo pero lo perdonamos una vez)
    val comboGrace: Int = 0,
    // Teikoins (moneda del juego)
    @SerialName("coins") val teikoins: Int = 100,
    // Récord de puntuación
    val highScore: Int = 0,
    // Récord de Teikas formados
    val maxTeikas: Int = 0,
    // Nº de veces que el panel de fichas se ha repuesto (agotado y rellenado) en este nivel.
    // Es la UNIDAD DE CADENCIA de la Brocha (P-70), la Camaleón (P-72) y la ficha que crece (P-77):
    // por eso se persiste — si no, salir y entrar del nivel las farmearía.
    // (2026-09-06) El comentario que había aquí anunciaba «cada 5ª reposición, una ficha
    // COMPLETAMENTE aleatoria del catálogo». Esa regla no existía ya: su función `randomCatalogPiece`
    // llevaba meses con cero llamantes y se ha retirado con este mismo cambio.
    val trayRefillCount: Int = 0,
    /**
     * **EL PREMIO DE LA BANDEJA** (2026-09-26, decisión del usuario): la mayor cantidad de líneas
     * que rompió UNA colocación desde el último reparto. Al reponer decide el premio
     * ([TrayReward.forLines]) y vuelve a 0. Se guarda para que cerrar la app no se lo coma.
     */
    val trayBestLineClear: Int = 0,
    /** ¿La reposición de ESTE turno vino con premio? Lo lee la cápsula `TRAY_REWARD`. No se guarda. */
    @Transient val trayRewardJustDealt: Boolean = false,
    // (RETIRADO 2026-09-22) `discardsLeft` — los descartes de EL DESCARTE (P-146). `ignoreUnknownKeys`
    // traga los guardados que aún lo traigan.
    // (RETIRADO 2026-09-22) `repeaterShape` — la racha de EL REPETIDOR. `ignoreUnknownKeys` la traga.
    /**
     * **LA BANDEJA SE ACUERDA DE LA ANTERIOR** (2026-09-06): las formas del último reparto.
     *
     * `PieceTrayPlanner` las **penaliza**, no las veta, para que dos bandejas seguidas no se
     * parezcan. Antes de existir esto, el **77,8 %** de los repartos traía una forma de la bandeja
     * de antes: no era mala suerte, era que el reparto no tenía memoria ninguna.
     *
     * **Se persiste**: si fuera `@Transient`, la
     * primera bandeja después de CONTINUAR podría ser el calco de la que el jugador estaba mirando
     * al salir — justo el momento en que más se nota. Un guardado antiguo la trae vacía y el
     * siguiente reparto la llena; no hace falta migrar nada.
     */
    val lastTrayShapes: List<List<Position>> = emptyList(),
    // (RETIRADOS 2026-09-22) `echoRows`/`echoCols` — las marcas de EL ECO (P-151). `ignoreUnknownKeys`
    // traga los guardados que aún las traigan.
    // (RETIRADO 2026-09-22) `colorCells` — las celdas de color (P-157c). `ignoreUnknownKeys` traga
    // los guardados que aún lo traigan. Las sustituye la vidriera, justo debajo.
    /**
     * **P-244 · LA VIDRIERA**: los vidrios de la vidriera actual. Vacío en los niveles sin la regla.
     *
     * **Se serializa**, como [hotCells]: la vidriera cambia de sitio cada vez que estalla, así que
     * lo que hay que recordar al retomar no se puede derivar del número de nivel.
     */
    val glassCells: Set<Position> = emptySet(),
    /** Los vidrios de [glassCells] que ya ha agrietado una línea. Cuando están todos, estalla. */
    val glassCracked: Set<Position> = emptySet(),
    /** Cuántas vidrieras han estallado ya en esta ronda: la semilla de la siguiente. */
    val glassWave: Int = 0,
    /**
     * **P-240 · EL IMÁN**: colocaciones que le quedan al imán que hay en el tablero (0 = ninguno).
     * El imán en sí es el bloque con [CellContent.Wood.magnet]; esto es solo su reloj. Se serializa:
     * un imán puesto es algo que el jugador ha jugado, y salir y volver no se lo puede quitar.
     */
    val magnetTurnsLeft: Int = 0,
    /**
     * **P-235 · EL HUEVO**: la casilla del huevo actual, o `null` (sin regla, o sin sitio para el
     * siguiente). Se serializa: el huevo cambia de sitio al eclosionar y lleva sus grietas.
     */
    val eggCell: Position? = null,
    /** Grietas que lleva el huevo actual. Eclosiona al llegar a [TeikaEgg.cracksFor]. */
    val eggCracks: Int = 0,
    /** Cuántos huevos han eclosionado ya: la semilla del sitio del siguiente. */
    val eggWave: Int = 0,
    /**
     * **P-242 · LA PIÑATA**: dónde cuelga ahora (o `null`), cuántos golpes lleva, hacia dónde se
     * balancea (+1 derecha, −1 izquierda) y cuántas han reventado. Se serializa: la piñata se mueve
     * cada turno y lleva sus golpes, nada de eso se deriva del número de nivel.
     */
    val pinataCell: Position? = null,
    val pinataHits: Int = 0,
    val pinataDir: Int = 1,
    val pinataWave: Int = 0,
    /**
     * **P-233 · EL MOLDE**: su forma (con la esquina en 0,0), dónde está anclado y cuántos han
     * estallado. Se serializa: el molde se muda cuando algo lo tapa, así que no se deriva del nivel.
     */
    val moldShape: List<Position> = emptyList(),
    val moldAt: Position? = null,
    val moldWave: Int = 0,
    val replacementRulesVersion: Int = 0,
    val clawCharged: Boolean = false,
    val clawTrip: ClawTrip? = null,
    // Legacy Ascensor fields: read only by migration.
    val elevatorCharged: Boolean = false,
    val elevatorTrip: ElevatorTrip? = null,
    val trailCells: List<Position> = emptyList(), // Legacy Estela; migration clears it.
    val ballCharged: Boolean = false,
    val ballPending: Boolean = false,
    val forgeCharged: Boolean = false,
    val forgePending: Boolean = false,
    val reactorCharged: Boolean = false,
    val reactorPending: Boolean = false,
    val surfWave: Int = 0,
    /** Legacy Cometa fields; migration clears them without granting an orbital charge. */
    val cometMark: CometMark? = null,
    val cometWave: Int = 0,
    val reservePieces: List<Piece> = emptyList(),
    val reserveSwapRefill: Int = -1,
    val routes: List<RouteTrack> = emptyList(),
    val routeAbilityCompanionId: String? = null,
    val routeSkillEarned: Boolean = false,
    val routeFreeCompanionId: String? = null,
    /** Read-only compatibility for retired Deslizamiento. */
    val slidePendingCells: Set<Position> = emptySet(),
    val slideWave: Int = 0,
    val portalSwapCharged: Boolean = false,
    val portalSwapPending: Boolean = false,
    val turbineCharged: Boolean = false,
    val turbineGust: TurbineGust? = null,
    val orbitCharged: Boolean = false,
    val orbitPending: Boolean = false,
    val compactorCharged: Boolean = false,
    val compactorPending: Boolean = false,
    val relayCharged: Boolean = false,
    val relayPendingFootprint: Set<Position> = emptySet(),
    val relayPendingShot: RelayShot? = null,
    val relayProtectedCells: Set<Position> = emptySet(),
    /** Double-line collapse waiting for the preceding line animation to finish. */
    val avalanchePending: Boolean = false,
    val avalancheCharge: Int = 0,
    val constellationPoints: List<Position> = emptyList(),
    val constellationCovered: Set<Position> = emptySet(),
    val constellationWave: Int = 0,
    val trampolineCell: Position? = null,
    val trampolineCharged: Boolean = false,
    val pinballCharged: Boolean = false,
    val pinballFlight: PinballFlight? = null,
    val cutReserve: Piece? = null,
    val cutActiveId: String? = null,
    val cutRefill: Int = -1,
    /**
     * IDs de Teikas actualmente en animación (ocultos de la cuadrícula estática).
     *
     * **Transitorio (P-112, 2026-09-04).** Es una marca de «hay una animación corriendo sobre este
     * cuerpo», y las animaciones no sobreviven a un guardado: quien libera cada id es la corrutina
     * que lo bloqueó, y esa corrutina muere con la ronda. Persistirlo devolvía un Teika marcado
     * como ocupado **sin nada que lo liberara** — hoy no se notaba porque `continueGame` lo
     * limpiaba a mano, pero la corrección vivía en una línea del camino de carga en vez de en el
     * dato, así que cualquier ruta de restauración nueva heredaba cuerpos bloqueados para siempre.
     */
    @Transient
    val busyTeikaIds: Set<String> = emptySet(),
    // Motor físico Suika: cuerpos de Teika persistidos (se inyectan al guardar; ver
    // GameViewModel.withTeikaBodySaves / continueGame). Vacío en estado vivo.
    val teikaBodySaves: List<TeikaBodySave> = emptyList(),
    // Objetivo de estrellas para niveles con layout especial (null = modo libre)
    val levelObjective: LevelObjective? = null,
    // Targets recolectados hasta ahora en este nivel
    val targetsCollected: Map<TargetColor, Int> = emptyMap(),
    // Progreso de los objetivos que NO son de estrellas (2026-07-31b). Persistidos y con valor por
    // defecto 0: un save antiguo entra con `goal = STARS` y estos contadores no le afectan.
    /** Líneas rotas en este nivel — objetivo [LevelGoal.LINES]. */
    val linesClearedInLevel: Int = 0,
    /**
     * **La lección 7 ya se ha LANZADO** (P-87, 2026-09-02, decisión del usuario).
     *
     * La lección de habilidades se mide en tres líneas, y su tablero son tres filas a las que faltan
     * dos celdas: un jugador que no encontrara el tirador podía cerrarlas a mano y llevarse una
     * ventana que dice «¡HABILIDAD LANZADA!» **sin haber lanzado nada**. Era la única lección cuyo
     * título podía mentir. Ahora el contador de líneas no basta: hace falta además esta marca, que
     * solo pone el propio lanzamiento (`GameViewModel.completeAbilityLessonWhenCast`).
     *
     * `@Transient` como el resto del estado de ronda: si el jugador sale y vuelve, la lección le
     * vuelve a pedir el gesto — que es justo lo que la lección es.
     */
    @Transient val tutorialAbilityCast: Boolean = false,
    /** Fichas colocadas en este nivel (estadística; alimentaba el objetivo SUPERVIVENCIA, retirado). */
    val placementsInLevel: Int = 0,
    // (RETIRADO 2026-09-22, decisión del usuario) `requestMet` — la marca de EL PEDIDO (P-82).
    // `ignoreUnknownKeys` traga los guardados que aún la traigan.
    /**
     * **TURNOS RESTANTES** — (RETIRADOS 2026-09-23, decisión del usuario: no se pierde por turnos).
     * El campo se conserva por los guardados y queda SIEMPRE a 0, que es el centinela de «sin límite»
     * que el motor y la interfaz ya entendían (ver [movesTotal]).
     */
    val movesLeft: Int = 0,
    /**
     * Presupuesto con el que ARRANCÓ el nivel, y **centinela del sistema**: `0` = este nivel NO
     * tiene límite de turnos.
     *
     * Hace falta separado de [movesLeft] justamente para que `0` no sea ambiguo: un save anterior a
     * 2026-07-31c deserializa con ambos a 0 y debe jugarse **sin contador**, no nacer derrotado por
     * tener 0 movimientos. También es lo que vale en Infinito, que se juega contra el muro.
     */
    val movesTotal: Int = 0,
    // (RETIRADO 2026-09-23) `levelDeal` — la carta de EL TRATO (P-74). `ignoreUnknownKeys` traga
    // los guardados que aún la traigan.
    /**
     * **CELDAS CALIENTES** (P-78): las que quedan sin cobrar. Se siembran al empezar el nivel
     * ([HotCells.forLevel], deterministas) y se van gastando conforme las cruza una línea.
     *
     * **Se serializa** aunque su origen sea puro: lo que hay que recordar al retomar un guardado no
     * es dónde estaban, sino **cuáles quedan**.
     */
    val hotCells: Set<Position> = emptySet(),
    // (RETIRADO 2026-09-26) `goalCell` — LA PORTERÍA (P-81). `ignoreUnknownKeys` traga los guardados
    // que aún la traigan.
    /** Evita aplicar dos veces el bonus final por turnos sobrantes al resolver el nivel. */
    val levelTurnBonusAwarded: Boolean = false,
    // Pulso "¡faltan estrellas!" — el panel de objetivo se pone ROJO y VIBRA una vez cuando el
    // jugador forma un MEGA sin haber completado los objetivos. @Transient (no se guarda) y con
    // guarda de "una sola vez por nivel" en el VM ([objectiveNudgeLevel]).
    @Transient val objectiveNudgePulse: Int = 0,
    // P-263 (2026-09-24): el caso contrario — las ESTRELLAS ya están y solo falta el MEGA. Lo marca
    // la misma función que decide la victoria (`checkObjectiveCompletion`), así que no hay una segunda
    // copia de «¿está cumplido?». Sin él, un nivel así seguía sin fin y sin decir qué faltaba (bot:
    // L289, 200 colocaciones con todas las estrellas). Se GUARDA: al volver con CONTINUAR el aviso
    // sigue ahí, en vez de desaparecer hasta la próxima jugada.
    val megaPending: Boolean = false,
    // Nivel más alto desbloqueado (independiente del nivel que se está jugando ahora)
    val maxUnlockedLevel: Int = 1,
    // ── WALLBLOCK / «LA PRENSA» — MODO ELIMINADO (2026-08-09) ───────────────────
    // Decisión del usuario: el modo entero sale del juego. Con él se van sus campos —
    // `isInfiniteMode`, `infiniteTimeLeftMs`, `infiniteDifficulty`, `timeReviveUsed`, `tideHeight`,
    // `tideActive`, `infiniteElapsedSec`, `tideRowsTotal`, `pieceBag`, `wellSurgeUntilMs` y
    // `fallOrigins`— y todo lo que colgaba de ellos (muro, ático, catapulta, hitos de profundidad,
    // gravedad de tablero). El juego se queda SOLO con NIVELES, que es donde estaba la diversión.
    //
    // Los guardados ya escritos traen estos campos: `ignoreUnknownKeys` los ignora sin romper nada,
    // que es exactamente para lo que estaba puesto.
    // ── Companion system ────────────────────────────────────────────────────────
    // (companionAbilityUsed se RETIRÓ en la it. 33: era un campo de solo-escritura sin ningún
    //  lector; ignoreUnknownKeys tolera los guardados viejos que aún lo traigan.)
    // (RETIRADO 2026-08-27) `companionEnergy`: la barra 0..100 que se llenaba rompiendo líneas y
    // disparaba la habilidad sola. Sustituida por cargas consumibles fuera del tablero
    // (`CompanionCharges`, en DataStore + nube), porque el poder es del JUGADOR, no de la partida:
    // sobrevive al nivel. `ignoreUnknownKeys` tolera los guardados que aún traigan el campo.
    val companionPassiveEffect: String? = null, // "shield" (cristal) | "fire" (fuego)
    val companionPassiveCount: Int = 0,
    // ── Hielo / Glaciar: bloques congelados (transient)
    val frozenCells: Set<Position> = emptySet(),
    // (RETIRADO 2026-09-03, C-7) `gameOverSuspendedUntil`: la «ventana de gracia de 15 s sin
    //  game-over» del Glaciar. **Nunca existió**: el único sitio que lo escribía lo ponía a `0L`
    //  —o sea, lo apagaba— y jamás hubo una línea que lo encendiera, así que el `checkGameOver`
    //  que lo consultaba comparaba siempre contra cero. El campo era estado muerto y el comentario
    //  que lo acompañaba describía una mecánica viva: exactamente la trampa de `usesNewEngine`.
    //  Si alguna vez hace falta esa gracia, se implementa entera o no se escribe.
    // ── Hielo / Glaciar: hasta este instante el jugador puede SUPERPONER piezas sobre las
    //    celdas congeladas (las rompe y las reemplaza). Fuera de la ventana, colocación normal.
    @Transient val iceWindowUntil: Long = 0L,
    // ── Hielo / Glaciar (rework): el tablero queda congelado hasta que el jugador coloque
    //    TODAS las piezas que tenía en la bandeja al activar. Este contador lleva cuántas
    //    colocaciones faltan; al llegar a 0 el hielo se rompe.
    val iceFreezePlacements: Int = 0,
    // ── Galáctico / Singularidad: celdas Wood ocultas mientras viajan hacia el centro
    //    (el VFX SingularityCollapse las dibuja en tránsito; se revelan al asentarse).
    @Transient val hiddenWoodCells: Set<Position> = emptySet(),
    // ── Biomas temáticos ────────────────────────────────────────────────────────
    // Bioma actual del nivel (mecánica primaria + tema visual base).
    val biomePrimary: BiomeType = BiomeType.NONE,
    // (RETIRADO 2026-08-28) `biomeSecondary`: el bioma de la ventana anterior, cuya mecánica seguía
    // activa como 2ª capa. Ahora corre SOLO la del bioma actual — con dos a la vez el jugador no
    // sabía cuál le estaba castigando y el hint del popup solo describía una.
    // `ignoreUnknownKeys` tolera los guardados que aún traigan el campo.
    // 0f..1f: cuánto se ha transformado el tablero hacia el bioma SIGUIENTE (morph visual).
    val biomeTransition: Float = 0f,
    // Reloj compartido por las mecánicas de bioma: +1 por pieza colocada.
    val biomeTurnCounter: Int = 0,
    // Bosque del Sueño / "Maite": turnos restantes de sueño.
    //
    // (2026-09-07) Antes su castigo era «no puedes rotar». Al retirarse el giro del juego entero,
    // ese castigo se quedaba en nada, así que la Maite pasa a DORMIR UNA FICHA de la bandeja
    // ([drowsyPieceId]): sigue ahí, se lee y se puede planificar con ella, pero no se puede jugar
    // hasta que despierte. Mismo reloj, mismo aviso, mismo número de turnos.
    val drowsyTurns: Int = 0,
    // La ficha que la Maite tiene dormida, por id. Viaja con el guardado igual que [drowsyTurns]:
    // un castigo que se evapora al CONTINUAR no es un castigo.
    val drowsyPieceId: String? = null,
    /** P-353 · La ficha que la Maite ha MARCADO en el aviso (la dormirá en el golpe si sigue en la mano). */
    val drowsyMarkedId: String? = null,
    // Bosque del Sueño / "Maite" (idle-gated): turnos consecutivos SIN romper filas; a los
    // DROWSY_PERIOD entra la Maite. Romper una línea lo reinicia.
    val sleepIdleTurns: Int = 0,
    // Pasaje Encantado / Miedoso: los bloques huyen SIEMPRE al colocar; romper una línea les da
    // [fearCalmTurns] turnos de calma (no huyen) y después vuelven a huir.
    val fearCalmTurns: Int = 0,
    // Fuego/Volcán: nº de filas SUPERIORES cubiertas por lava (baja desde arriba). El tablero
    // jugable son las filas [lavaRow, GRID_SIZE). Sube 1 tras DOS fichas consecutivas sin romper
    // líneas; romper una línea reinicia la presión y hace retroceder la lava.
    val lavaRow: Int = 0,
    val volcanoDryTurns: Int = 0,
    // Ninja / Dojo en Sombras (rework 2026-07-16 · CORTE NINJA — mecánica ÚNICA del bioma, PESADA
    // solo primario). [ninjaIdleTurns] cuenta colocaciones SIN romper filas: a la 1ª algo se
    // aproxima (aviso), a la 2ª se intensifica, y a la 3ª un ninja PARTE el tablero en dos por la
    // columna central. Con el tablero partido los Teikas no pueden cruzar al otro lado (muro físico
    // en TeikaPhysicsWorld) y las fichas no pueden colocarse a caballo entre mitades. Se repara al
    // romper [ninjaCutLinesLeft] líneas (filas o columnas) — o AL INSTANTE si se forma un MEGA
    // (un MEGA de diámetro 5 no cabe en una mitad de 4: revienta la barrera).
    // [ninjaCutActive]/[ninjaCutLinesLeft] PERSISTEN a propósito (lección del congelado del
    // Glaciar, it. 37): guardar-y-salir no debe evaporar el castigo.
    val ninjaIdleTurns: Int = 0,
    val ninjaCutActive: Boolean = false,
    val ninjaCutLinesLeft: Int = 0,
    // P-254 (2026-09-24): colocaciones con el tablero partido. A [BiomeRhythm.punishmentTurns] (4) la
    // grieta se CIERRA SOLA: el castigo tiene fin aunque no llegues a romper las columnas. Persiste
    // con el corte, por lo mismo que él.
    val ninjaCutTurns: Int = 0,
    // P-254 · LA TREGUA (2026-09-24, decisión del usuario): tras cerrarse la grieta —sola, con columnas
    // o con un MEGA— la katana no vuelve a avisar durante estas colocaciones. Sin ella el tablero, ya lleno por el corte, volvía a partirse a las 3: cortes en bucle.
    val ninjaTruceTurns: Int = 0,
    // ── Cielo Estelar / Evento Astral (idle-gated, PESADO solo primario): tras STAR_ASTRO_IDLE_THRESHOLD
    //    turnos SIN actividad (clear/fusión), un astro (Sol/Luna) EMERGE desde ARRIBA como AVISO
    //    ([starAstroEmerged]); si el turno siguiente sigue ocioso, DISPARA. Actividad a tiempo → se retira.
    //    Tras disparo/retirada entra [starAstroCooldown]. Transient.
    val starAstroIdleCounter: Int = 0,
    val starAstroEmerged: Boolean = false,
    val starAstroBody: Int = 0,               // 0 = ninguno, 1 = Sol, 2 = Luna
    /**
     * El PRESAGIO ([StarAstroOmen]): qué astro viene, sorteado 50/50 al primer turno ocioso. La UI tiñe
     * el tablero de oro (Sol) o plata (Luna) mientras dura el aviso; al disparar sale ESTE astro.
     */
    val starAstroOmen: Int = 0,
    val starAstroTargetTeikaId: String? = null,
    val starAstroTargetCells: Set<Position> = emptySet(),
    /** Luna: maderas pre-marcadas al emerger (se roban solo esas si siguen válidas). */
    val starAstroMoonWoodCells: Set<Position> = emptySet(),
    val starAstroCooldown: Int = 0,
    // Glaciar / Escarcha que avanza (rework, idle-gated): celdas de madera cubiertas de escarcha —
    // no se rompen (el clear las salta) hasta derretirse. En cada turno OCIOSO la escarcha cubre
    // GLACIER_FROST_PER_TURN bloques más desde los bordes; romper una fila la derrite TODA.
    val glacierFrostCells: Set<Position> = emptySet(),
    val glacierIdleTurns: Int = 0,
    // Mansión Espectral · EL ESPEJO EMBRUJADO (P-246, 2026-09-23; sustituye a la niebla y el reflector).
    // [ghostIdleTurns] cuenta colocaciones sin romper líneas (el nombre se conserva: viaja en los
    // guardados). [mirrorSource] es la mitad que el espejo reflejará ([HauntedMirror.LEFT]…) desde que
    // se empaña, o [HauntedMirror.NONE]. Los fantasmas viven en la propia madera (`Wood.phantom`).
    // AMBOS SE GUARDAN (P-111): guardar y salir no disipa el aviso. `ghostFog` se retiró con la niebla.
    val ghostIdleTurns: Int = 0,
    val mirrorSource: Int = HauntedMirror.NONE,
    // Cocina Voraz / Glotón: presión por inactividad. [gluttonCounter] = colocaciones ociosas
    // (sin clear ni fusión). A GLUTTON_OPEN_AT abre la boca en [gluttonCorner]
    // (0=SI,1=SD,2=II,3=ID; -1 = cerrada). A GLUTTON_BITE_AT muerde el triángulo (sin estrellas
    // objetivo ni celdas de Teika). Dead cells = unplaceable + relleno fijo de líneas (como muros).
    // Clear o fusión resetean contador y cierran la boca. Ver GluttonBiomeRules.
    val gluttonCounter: Int = 0,
    val gluttonCorner: Int = -1,
    val gluttonDeadCells: Map<Position, Int> = emptyMap(),
    // Tóxico · EL VENENO ES UNA CAPA (2026-09-28): colocaciones seguidas sin romper líneas en las que el
    // veneno no alcanzó ningún bloque; al llegar a [ToxicBiomeRules.SPLASH_AFTER_TURNS] SALPICA. (El
    // nombre viene de la putrefacción a la que sustituyó; se conserva para no romper guardados.)
    val toxicDecayCounter: Int = 0,
    /** Tóxico: lo que el veneno alcanzará en la próxima colocación sin líneas (origen, destino). Lo
     *  calcula el tick con la regla real para que el aviso no sea una segunda copia de ella. */
    @Transient val toxicFront: List<Pair<Position, Position>> = emptyList(),
    // Trono Real · LA ESPADA DEL REY (P-294, 2026-09-25; sustituye al Jaque). La línea que el Rey
    // señala con su espada: [kingSwordAxis] ([KingSword.ROW]/[KingSword.COL], o [KingSword.NONE]) y su
    // índice [kingSwordLine]; [kingSwordIdle] colocaciones sin romper líneas. Se guardan: salir y volver
    // no baja la espada. (Los campos del Jaque —`chessKind`, `chessAt`…— se borraron con él; un
    // guardado viejo los trae y `ignoreUnknownKeys` los ignora.)
    val kingSwordAxis: Int = KingSword.NONE,
    val kingSwordLine: Int = -1,
    val kingSwordIdle: Int = 0,
    // P-329 · LAS PIEZAS DEL REY (2026-09-26): el mismo reloj ([kingSwordIdle]) alterna espada y pieza.
    // [kingPieceKind] ([KingPiece.ROOK]/[KingPiece.BISHOP]/[KingPiece.KNIGHT] o [KingPiece.NONE]) con su
    // objetivo ([kingPieceA], [kingPieceB]); [kingPieceBlack] su color; [kingPieceFromEnd] por qué
    // extremo del camino entra; [kingPieceNext] si el PRÓXIMO golpe es de pieza; [kingLaneCells] el
    // camino que la última pieza dejó bloqueado una colocación. Todo se guarda: salir y volver no
    // retira la pieza ni abre la calle.
    val kingPieceKind: Int = KingPiece.NONE,
    val kingPieceA: Int = -1,
    val kingPieceB: Int = -1,
    val kingPieceBlack: Boolean = false,
    val kingPieceFromEnd: Boolean = false,
    val kingPieceNext: Boolean = false,
    /** Piezas seguidas desde la última espada (2026-09-28: [KingPiece.PIECES_PER_SWORD] por espada). Se guarda. */
    val kingPiecesInRow: Int = 0,
    val kingLaneCells: Set<Position> = emptySet(),
    /** Colocaciones que le quedan a [kingLaneCells] (2026-09-27: dura [KingPiece.LANE_TURNS]). */
    val kingLaneTurns: Int = 0,
    // LA PIEZA SE QUEDA (2026-09-28): tras el golpe, la pieza [kingRestKind] (color [kingRestBlack])
    // sigue en [kingRestCell] y su calle ([kingLaneCells]) no caduca hasta que se rompa su fila o su
    // columna. Se guarda: salir y volver no la retira.
    val kingRestCell: Position? = null,
    val kingRestKind: Int = KingPiece.NONE,
    val kingRestBlack: Boolean = false,
    /**
     * P-349 · EL BANCO DE BLOQUES: bloques guardados, su color y los relojes de uso y enfriamiento.
     * Se GUARDA con la partida: salir y volver no vacía el banco ni reinicia el enfriamiento.
     */
    val blockBank: BlockBank = BlockBank(),
    /**
     * **POR QUÉ se perdió esta partida** (C-4). Lo lee la ventana de segunda oportunidad para
     * titularse de verdad y prometer lo que va a dar. Transitorio a propósito: al recargar un save
     * la oferta se replantea sola.
     */
    @Transient val defeatCause: DefeatCause = DefeatCause.SIN_ESPACIO,
    // Espacio Profundo · LLUVIA DE METEORITOS (P-294, 2026-09-25; sustituye a la Brecha). La esquina de
    // arriba a la izquierda del cráter 2×2 que el meteorito tiene fijado (null = ninguno). Se guarda:
    // salir no desvía el meteorito. (`breachSide`/`breachLine` se borraron con la Brecha.)
    val meteorAt: Position? = null,
    // P-319 · LLUVIA DOBLE: el SEGUNDO cráter, en el último tercio del capítulo (null = ninguno). Se guarda
    // por lo mismo que el primero.
    val meteorAt2: Position? = null,
    // ── ESPEJO PERSISTENTE DE TRES RELOJES QUE VIVEN EN EL VIEWMODEL (P-116, 2026-09-03) ────────
    // Estos tres NO se leen durante la partida: el motor trabaja con las variables del ViewModel
    // (`galacticIdleCounter`, `fearFleeCounter`, `coilFailCountingActive`), que se mutan a mitad de
    // `applyBiomeTick` y del cierre de turno. Aquí solo viajan al guardado y vuelven de él, igual
    // que [teikaBodySaves] hace con el mundo físico: el estampado ocurre en UN solo sitio
    // (`GameViewModel.withTeikaBodySaves`) y la restauración en otro (`continueGame`).
    //
    // Sin ellos, P-111 quedaba a tres biomas de estar completo: al retomar la partida
    // `resetRoundTransients` los ponía a cero y el Galáctico, el Miedoso y la Tormenta regalaban
    // turnos de gracia — la brecha volvía a necesitar la cuenta entera para abrirse, los
    // bloques miedosos reiniciaban su huida, y el circuito volvía a poder explorarse gratis.
    val galacticIdleSaved: Int = 0,
    val fearFleeSaved: Int = 0,
    val coilFailCountingSaved: Boolean = false,
    // Jardín / Secuestro vegetal: tras 3 turnos sin romper líneas, una raíz captura el Teika mayor.
    // El jugador debe romper 2 líneas antes de que llegue al borde en 6 turnos.
    val plantIdleTurns: Int = 0,
    /** P-353 · El borde por el que entrará la raíz y el Teika que ha marcado en el aviso (null = aún no). */
    val plantMarkEdge: PlantEdge? = null,
    val plantMarkTeikaId: String? = null,
    /**
     * **EL RAPTO SOBREVIVE AL GUARDADO** (2026-09-04, el cabo que dejó abierto P-111).
     *
     * Era el último castigo de bioma que se evaporaba al salir a Inicio y pulsar CONTINUAR: te
     * llevabas de vuelta el Teika que la raíz ya tenía medio fuera del tablero, gratis. Se quedó
     * fuera de P-111 porque el cuerpo raptado vive como `kinematic` y ese flag **no se guarda** — lo
     * comparten otras dos mecánicas efímeras (la retención de la fusión T3+T3 y la espera del
     * tutorial), así que guardarlo devolvería cuerpos congelados en el aire sin ceremonia que los
     * suelte.
     *
     * La salida no fue separar el flag: **el rapto ya sabe a quién agarra** (`teikaId`), así que
     * `continueGame` repone el agarre sobre ese cuerpo y solo sobre ese. Las otras dos vuelven
     * dinámicas, que es justo lo correcto para ellas.
     */
    val plantAbduction: PlantAbduction? = null,
    // (BORRADOS 2026-09-24) `captainChargeProgress` y `captainChargeSpent`: la carga del capitán se
    // fue con el rediseño de power-ups. Un guardado viejo los trae y `ignoreUnknownKeys` los ignora.
    /**
     * **DE QUÉ VERSIÓN VIENE ESTE GUARDADO** (P-171, 2026-09-04). `0` = de una anterior a esta regla.
     *
     * Hasta hoy la compatibilidad se sostenía solo sobre `ignoreUnknownKeys` y los valores por
     * defecto, y eso deja preguntas que **no se pueden responder, solo adivinar**. Está escrito en el
     * propio motor: los descartes de P-146 llegan a `0` desde un save viejo, y ese cero *«es
     * indistinguible de los he gastado todos»*, así que había que decidir en favor del jugador y a
     * veces regalarle uno. Con este número esa clase de caso se contesta exacto.
     *
     * Lo estampa `GameViewModel.withTeikaBodySaves`, que es el punto único por el que pasa **todo**
     * guardado, y solo debe subir cuando el significado de un campo cambie de forma que un save viejo
     * no pueda distinguirse de uno nuevo. No es un número de versión del juego.
     */
    val saveSchema: Int = 0,
    // Reynaldo / Corte de Súbditos: el Teika coronado REGENTE. Su próximo merge (o hasta
    // [regentExpiresAtMs]) suma [regentBonus] de score. Transitorio: no sobrevive a un save.
    @Transient val regentTeikaId: String? = null,
    @Transient val regentBonus: Int = 0,
    @Transient val regentExpiresAtMs: Long = 0L,
) {
    /** Las casillas que la espada del Rey tiene señaladas (vacío si no hay ninguna). */
    val kingSwordCells: List<Position> get() = KingSword.strikeCells(kingSwordAxis, kingSwordLine)

    /** P-329: el camino que la pieza del Rey tiene señalado (vacío si no hay ninguna). */
    val kingPieceCells: List<Position> get() = KingPiece.cells(kingPieceKind, kingPieceA, kingPieceB)

    /** Las casillas del cráter que el meteorito tiene fijado (vacío si no hay ninguno). */
    val meteorCells: List<Position> get() = listOfNotNull(meteorAt, meteorAt2).flatMap { MeteorShower.craterCells(it) }

    /**
     * Celdas que NINGUNA pieza puede ocupar: la SILUETA del nivel + rincones del Glotón + el huevo.
     * (La pieza del Jaque ocupaba su casilla; se fue con él el 2026-09-25.)
     *
     * La silueta ([LevelVariety.blockedCells], 2026-08-26f) se deriva del número de nivel, así que no
     * añade estado ni obliga a migrar guardados: un save viejo recupera su forma sola al cargarse.
     */
    val unplaceableCells: Set<Position> get() {
        var base = gluttonDeadCells.keys
        // P-235: el huevo ocupa su casilla — no se coloca encima (pero cuenta como llena en su línea).
        eggCell?.let { base = base + it }
        // P-329: el camino que arrasó la pieza del Rey, bloqueado una colocación.
        if (kingLaneCells.isNotEmpty()) base = base + kingLaneCells
        // La pieza del Rey que se quedó ocupa su casilla (y, como el huevo, cuenta como llena en su línea).
        kingRestCell?.let { base = base + it }
        return base
    }

    /**
     * **P-80 · NIVEL CON GRAVEDAD**: ¿en este nivel se asienta la madera hacia abajo?
     *
     * Se DERIVA del número de nivel ([BoardGravity.appliesTo]) en vez de guardarse: igual que la
     * silueta o el ciclo de figuras, así no añade campo al guardado ni obliga a migrar nada, y un
     * save viejo recupera su gravedad solo al cargarse.
     */
    val hasBoardGravity: Boolean get() = BoardGravity.appliesTo(level)

    // (RETIRADO 2026-09-22, decisión del usuario) `levelRequest` — EL PEDIDO (P-82).


    /** **P-233**: las casillas del molde, o vacío si no hay ninguno. */
    val moldCells: Set<Position>
        get() = moldAt?.let { MoldRule.cellsOf(moldShape, it) }.orEmpty()

    /** ¿Este nivel es la lección que enseña a lanzar una habilidad de compañero? */
    val hasTutorialAbilityLesson: Boolean
        get() = TutorialCampaign.spec(level)?.teachesAbility == true

    /**
     * **P-75 · BANDEJA DE UNA, CON HORIZONTE**: ¿este nivel reparte de una en una? Se deriva del
     * número de nivel, como la gravedad — sin campo nuevo que migrar.
     */
    val hasHorizonTray: Boolean get() = HorizonTray.appliesTo(level)

    /**
     * **P-79 · LA CORRIENTE**: ¿este nivel desplaza el tablero una columna cada
     * [BoardCurrent.PERIOD] colocaciones? Derivado del número de nivel, como la gravedad y el
     * horizonte — y excluyente con los dos: una regla de la casa por nivel.
     */
    val hasBoardCurrent: Boolean get() = BoardCurrent.appliesTo(level)

    /**
     * **P-231 · LA NORIA**: ¿gira el anillo del borde en este nivel? Derivado del número de nivel.
     *
     * (2026-09-22) Sustituye a `conveyorLanes`, la fila de LA CINTA. Como aquella, la noria tiene que
     * verse desde el turno 1 y todo el nivel: el jugador puede poner en el borde lo que quiere que
     * viaje, y para eso tiene que saber que el borde se mueve antes de que se mueva.
     */
    val hasWheel: Boolean get() = BoardCurrent.axisFor(level) == BoardCurrent.Axis.RING

    /**
     * Fichas JUGABLES que reparte este nivel a la vez: **una** con horizonte (P-75), **cinco o
     * cuatro** con la bandeja ampliada (P-142), y las tres de siempre en el resto.
     *
     * Las dos reglas no pueden coincidir —el catálogo reparte una sola por nivel— así que el orden
     * de las ramas no es una prioridad, es solo la forma de escribirlo.
     */
    val trayCapacity: Int get() = when {
        // A migrated cut can have a fourth conserved half: show it until this tray is used.
        CompactorRules.appliesTo(level) && availablePieces.size > 3 -> availablePieces.size
        hasHorizonTray -> HorizonTray.PLAYABLE
        else -> TrayFive.slotsFor(level) ?: HorizonTray.PLAYABLE_NORMAL
    }

    /**
     * **P-144 · LA REPOSICIÓN INSTANTÁNEA**: ¿se repone cada ficha al gastarla, sin esperar a que se
     * vacíe la bandeja?
     */
    val hasInstantRefill: Boolean get() = InstantRefill.appliesTo(level)

    /** **P-143 · LA COLA FORZADA**: ¿hay que jugar las fichas en orden en este nivel? */
    val hasForcedQueue: Boolean get() = ForcedQueue.appliesTo(level)

    /**
     * Cuántas casillas quedan LIBRES y jugables: ni madera, ni bloqueo de bioma, ni bajo la lava.
     *
     * Es la vara de [lastSqueezeBiome] (P-228): comparada antes y después del tick de bioma dice, de
     * una sola pieza, si el castigo de este turno **te ha quitado sitio**. No cuenta los cuerpos de
     * Teika a propósito — se mueven solos y rodando liberan celdas, así que meterlos aquí haría que
     * el número subiera y bajara sin que nadie hubiera hecho nada.
     */
    val playableFreeCells: Int
        get() {
            var free = 0
            for (y in lavaRow until gridH) {
                for (x in 0 until gridW) {
                    val p = Position(x, y)
                    if (grid[p]?.isOccupied == true) continue
                    if (p in unplaceableCells) continue
                    free++
                }
            }
            return free
        }

    /**
     * **BOSQUE DEL SUEÑO · ¿la Maite tiene dormida esta ficha?** (2026-09-07)
     *
     * Es la ÚNICA definición del sueño: la usan el motor (que rechaza la colocación), la detección
     * de derrota (lo que no se puede jugar no cuenta como salida) y la bandeja (que la apaga y la
     * hace cabecear). Una sola función porque las tres tienen que decir lo mismo — si el dibujo dice
     * «dormida» y el motor la deja caer, el castigo es un bug; si el motor la rechaza y la derrota
     * la cuenta como salida, el jugador se queda sin jugada y sin derrota.
     *
     * **Nunca duerme la última**: con una sola ficha en la bandeja, dormirla sería una congelación
     * —no habría nada que jugar, y la reposición clásica solo entra cuando la bandeja se vacía—.
     * La Maite se levanta si es lo único que te queda.
     */
    fun isPieceAsleep(pieceId: String): Boolean =
        drowsyTurns > 0 && drowsyPieceId == pieceId && availablePieces.size > 1


    /**
     * **P-147 · LA FICHA ESPEJO**: el eje por el que se refleja lo que coloques, o `null`.
     */
    val mirrorAxis: MirrorPiece.Axis? get() = MirrorPiece.axisFor(level)

    /**
     * Ancho y alto del tablero. **Vuelven a ser constantes** (2026-08-09): eran propiedades porque
     * WALLBLOCK jugaba en un pozo 12×16 mientras los niveles se quedaban en 8×8, y al eliminarse el
     * modo no queda más tablero que el cuadrado clásico. Se conservan como propiedades —y no se
     * sustituyen por [GRID_SIZE] en los ~200 sitios que las leen— porque `state.gridW` / `state.gridH`
     * es la forma correcta de preguntar por el tablero y cambiarla no aportaría nada.
     */
    val gridW: Int get() = GRID_SIZE

    /** @see gridW */
    val gridH: Int get() = GRID_SIZE

    companion object {
        /**
         * LADO del tablero. Desde que WALLBLOCK dejó de existir (2026-08-09) el juego tiene un solo
         * tablero y vuelve a ser cuadrado 8×8, así que esto es a la vez el ancho y el alto.
         */
        /** P-255: intentos para que la bandeja de apertura salga con una ficha de 3+ y ≤ 1 monominó. */
        private const val OPENING_REROLLS = 8

        const val GRID_SIZE = 8

        /**
         * Esquema de guardado actual (ver [saveSchema]). **1** = el primero que se numera
         * (2026-09-04); todo lo escrito antes se lee como `0`.
         */
        const val CURRENT_SAVE_SCHEMA = 1

        /**
         * Desde este esquema, `discardsLeft == 0` significa exactamente **cero descartes**, no «este
         * save es anterior a la regla». Ver [saveSchema] y la reposición de `continueGame`.
         */
        const val SCHEMA_DISCARDS_ARE_EXACT = 1

        /** Alias históricos del lado. Los usan biomas, Teikas y VFX. */
        const val GRID_W_LEVELS = GRID_SIZE
        const val GRID_W = GRID_SIZE
        const val GRID_H_LEVELS = GRID_SIZE

        // Cocina Voraz: aliases de [GluttonBiomeRules] (UI y ViewModel).
        const val GLUTTON_OPEN_AT = GluttonBiomeRules.OPEN_AT
        const val GLUTTON_BITE_AT = GluttonBiomeRules.BITE_AT
        const val GLUTTON_RECOVER_TURNS = GluttonBiomeRules.RECOVER_TURNS

        /** Triángulo de 3 celdas pegado a la esquina [corner] (0=SI,1=SD,2=II,3=ID): el rincón + sus
         *  2 vecinas ortogonales hacia el interior. Lo que el glotón devora de un mordisco. */
        fun gluttonCornerCells(corner: Int): List<Position> {
            val max = GRID_SIZE - 1
            return when (corner) {
                0 -> listOf(Position(0, 0), Position(1, 0), Position(0, 1))               // sup-izq
                1 -> listOf(Position(max, 0), Position(max - 1, 0), Position(max, 1))     // sup-der
                2 -> listOf(Position(0, max), Position(1, max), Position(0, max - 1))     // inf-izq
                else -> listOf(Position(max, max), Position(max - 1, max), Position(max, max - 1)) // inf-der
            }
        }

        /**
         * Crea el estado inicial de una partida nueva.
         */
        fun initial(
            level: Int = 1,
            score: Int = 0,
            teikoins: Int = 100,
            highScore: Int = 0,
            maxTeikas: Int = 0,
            companionId: String? = null,
            companionLevel: Int = 1,
        ): GameState {
            val tutorialSpec = TutorialCampaign.spec(level)
            val baseGrid = buildMap<Position, CellContent> {
                for (y in 0 until GRID_SIZE) {
                    for (x in 0 until GRID_SIZE) {
                        put(Position(x, y), CellContent.Empty)
                    }
                }
            }

            // ── Biomas temáticos del nivel ───────────────────────────────────────
            val biomePrimary    = if (tutorialSpec != null) BiomeType.NONE else BiomeRegistry.currentBiome(level)
            val biomeTransition = if (tutorialSpec != null) 0f else BiomeRegistry.transitionProgress(level)
            val activeBiomes    = if (tutorialSpec != null) emptySet() else BiomeRegistry.activeMechanics(level)
            val crystalActive   = BiomeType.CRYSTAL in activeBiomes
            val stormActive     = BiomeType.STORM in activeBiomes
            // Tóxico es mecánica PESADA (solo corre como primaria, ver applyBiomeTick) — sembrar veneno
            // también en su ventana como secundaria dejaba madera envenenada "fantasma" sin reloj de
            // putrefacción ni recarga durante todo el capítulo siguiente (STAR).
            val toxicActive     = biomePrimary == BiomeType.TOXIC

            val layout = LevelLayouts.get(level)
            // Dominó marks the existing layout without adding occupied cells —
            // se reparten entre las que la figura ya tiene, así que el presupuesto de turnos y la
            // densidad que calculó `BoardClutterCost` no se mueven.
            val startGrid = if (layout != null) {
                val g = baseGrid.toMutableMap()
                val biomeRng = kotlin.random.Random(level.toLong() * 99991L)
                layout.first.forEach { cell ->
                    // El director alterna vetas suaves, patrones cristalinos y corazas de clímax.
                    val isCrystal = crystalActive &&
                        biomeRng.nextFloat() < LevelDirector.crystalSeedChance(level)
                    val crystalArmor = if (!isCrystal) 0 else if (
                        biomeRng.nextFloat() < LevelDirector.crystalDoubleArmorChance(level)
                    ) 2 else 1
                    // Bioma Tóxico: ~30% de madera sin cristal ni estrella nace envenenada (ToxicBiomeRules).
                    val isPoisoned = toxicActive && ToxicBiomeRules.shouldSeedPoison(
                        isCrystal = isCrystal,
                        hasTarget = cell.targetColor != null,
                        roll = biomeRng.nextFloat(),
                        chance = LevelDirector.toxicSeedChance(level),
                        // Toda la figura inicial lleva estrella. Sin esta excepción el Pantano nacía
                        // siempre con CERO veneno y su identidad no aparecía hasta una recarga tardía.
                        allowTarget = true
                    )
                    g[Position(cell.x, cell.y)] = CellContent.Wood(
                        targetColor = cell.targetColor,
                        skipSpawnAnimation = false,
                        // Retired flag stays false; Domino arrows never seed over crystal armor.
                        stubborn = false,
                        // Fase D: patrón de color cosmético por nivel (no afecta el clear, solo el look).
                        colorType = if (tutorialSpec != null) {
                            TutorialCampaign.colorTypeFor(level, cell.x, cell.y)
                        } else {
                            LevelPalette.colorTypeFor(level, cell.x, cell.y)
                        },
                        armor = crystalArmor,
                        poisoned = isPoisoned
                    )
                }
                // Bioma Eléctrico: las bobinas no se siembran sueltas; pero si el LAYOUT trae filas YA
                // COMPLETAS (todas de madera) hay que energizarlas como circuito DESDE EL INICIO — si no,
                // al colocar la primera ficha esas filas se romperían en vez de volverse circuito. Cada
                // fila completa → 4 bobinas en columnas y orden ALEATORIOS y OCULTOS (prefiere madera lisa;
                // al convertir, limpia raíz/armadura → bobina limpia). Ver GameViewModel.energizeCircuitRows.
                if (stormActive) {
                    for (y in 0 until GRID_SIZE) {
                        val rowPos = (0 until GRID_SIZE).map { Position(it, y) }
                        if (!rowPos.all { g[it] is CellContent.Wood }) continue          // fila completa de pura madera
                        if (rowPos.any { (g[it] as CellContent.Wood).coil > 0 }) continue // ya es circuito
                        val coilCount = LevelDirector.stormCoilCount(level)
                        val chosen = rowPos.shuffled(biomeRng)
                            .sortedBy { val w = g[it] as CellContent.Wood; if (w.armor == 0) 0 else 1 }
                            .take(coilCount)
                        val order = (1..coilCount).shuffled(biomeRng)
                        chosen.forEachIndexed { i, p ->
                            val w = g[p] as CellContent.Wood
                            g[p] = w.copy(coil = order[i], coilLit = false, armor = 0)
                        }
                    }
                }
                g
            } else baseGrid

            val target = when {
                tutorialSpec != null -> tutorialSpec.targetTeika ?: TeikaType.TEIKA_1
                level == 1 -> TeikaType.TEIKA_2
                level == 2 -> TeikaType.TEIKA_3
                else -> TeikaType.TEIKA_4
            }

            val objectiveColors = layout?.second?.required?.keys?.toList() ?: emptyList()

            // (RETIRADO 2026-09-23) El presupuesto de TURNOS: ya no se pierde por turnos, así que
            // `movesLeft`/`movesTotal` se quedan en 0 («sin límite»).

            val initialBlocked = startGrid
                .filterValues { it.isOccupied }
                .keys
                .toSet()
            // P-75 · BANDEJA DE UNA, CON HORIZONTE: el nivel abre con la ficha jugable **y su
            // horizonte ya lleno**, así que se planifican las cuatro de una tirada. Se pide en UNA
            // llamada y no en cuatro a propósito: `plan` enumera todas las colocaciones del tablero
            // al entrar, y llamarla cuatro veces en el arranque es exactamente el coste que provocó
            // el ANR del 2026-08-24. Las gemelas se apagan aquí — son dos ranuras con la misma
            // forma, y con horizonte solo hay una ranura.
            val horizonLevel = HorizonTray.appliesTo(level)
            // P-142: la bandeja ampliada reparte cinco (o cuatro en los biomas altos) en vez de tres.
            // Va aquí y no leyendo `trayCapacity` porque el estado todavía no existe: se está
            // construyendo. Los dos caminos usan la MISMA fuente (`TrayFive.slotsFor`), así que no
            // hay dos verdades que puedan separarse.
            val plannedCount = if (horizonLevel) HorizonTray.PLAYABLE + HorizonTray.horizonFor(level)
                               else if(ReserveRules.appliesTo(level)) 4 else HorizonTray.PLAYABLE_NORMAL
            // P-255: el reparto es azar con pesos, pero el PRIMER turno no depende de cómo juegues —aún
            // no has jugado—, así que la bandeja de apertura se vuelve a sortear (semillas fijas: el
            // nivel abre siempre igual) si sus fichas no caben JUNTAS, si no trae ninguna de 3+ o si
            // trae más de un monominó. Sin lo primero el bot perdía el nivel 200 a las dos colocaciones:
            // cada ficha cabía sola, pero colocadas dos, la tercera ya no entraba.
            fun planOpening(attempt: Int) = PieceTrayPlanner.plan(
                boardSize = GRID_SIZE,
                boardHeight = GRID_SIZE,
                blockedCells = initialBlocked,
                count = plannedCount,
                level = level,
                twins = !horizonLevel && TwinTrayRules.appliesTo(level) && !PieceFusion.appliesTo(level),
                // P-232: las fichas que se funden necesitan una pareja de gemelas pequeñas.
                smallTwins = if (!horizonLevel && PieceFusion.dealsTwinsOn(level, 0)) PieceFusion.MAX_SOURCE else 0,
                random = kotlin.random.Random(level.toLong() * 104729L + attempt * 7919L),
            )
            fun isFairOpening(plan: List<PieceTrayPlanner.PlannedShape>) =
                plan.all { it.reservedCells.isNotEmpty() } &&
                    plan.take(if(ReserveRules.appliesTo(level)) 3 else plan.size).any { it.shape.size >= 3 } && plan.count { it.shape.size == 1 } <= 1 &&
                    PieceTrayPlanner.fitsTogether(plan.map { it.shape }, initialBlocked)
            val dealt = tutorialSpec?.let { listOfNotNull(it.piece) } ?: (
                (0 until OPENING_REROLLS).asSequence().map { planOpening(it) }.firstOrNull(::isFairOpening)
                    // (2026-09-26) El reparto ya garantiza que las fichas caben juntas; si ninguna tirada
                    // pasa la regla de la apertura justa (tablero de salida muy lleno), vale la primera.
                    ?: planOpening(0)
            ).map { planned ->
                Piece.generatePieceForShape(
                    shape = planned.shape,
                    level = level,
                    objectiveColors = objectiveColors,
                    crystalActive = crystalActive,
                    random = kotlin.random.Random(level.toLong() * 104729L + planned.reservedCells.hashCode())
                )
            }
            // P-234: el primer barril llega en la bandeja de apertura, para que la regla se vea (y
            // su cápsula lo señale) desde la primera jugada.
            val dealtWithBarrel = if (tutorialSpec == null && ChainReaction.appliesTo(level))
                ChainReaction.markBarrel(dealt) else dealt
            // P-233: el molde de apertura, y su forma en la bandeja de apertura (la ficha más pequeña
            // se convierte en ella: el molde está sobre casillas libres, así que cabe seguro).
            val mold0 = if (tutorialSpec != null || !MoldRule.appliesTo(level)) null else MoldRule.firstFit(
                level, 0,
                blocked = startGrid.filterValues { it.isOccupied }.keys +
                    HotCells.forLevel(level = level, occupied = startGrid.filterValues { it.isOccupied }.keys),
            )
            val moldShape0 = mold0?.shape.orEmpty()
            val moldAt0 = mold0?.at
            val dealtWithMold = if (moldAt0 == null || dealtWithBarrel.isEmpty()) dealtWithBarrel else {
                val idx = dealtWithBarrel.indices.minByOrNull { dealtWithBarrel[it].shape.size }!!
                dealtWithBarrel.toMutableList().also {
                    it[idx] = it[idx].copy(shape = moldShape0, targetMask = emptyList(), crystalMask = emptyList())
                }
            }
            // P-256: la bandeja de apertura también trae su bloque-Teika (uno de cada tres).
            val dealtWithSeeds = TeikaSeeds.mark(dealtWithMold, dealtBefore = 0, level = level)
            val pieces = if (horizonLevel && tutorialSpec == null) dealtWithSeeds.take(HorizonTray.PLAYABLE)
                         else if(ReserveRules.appliesTo(level)) dealtWithSeeds.take(3) else dealtWithSeeds
            val horizon = if (horizonLevel && tutorialSpec == null)
                dealtWithSeeds.drop(HorizonTray.PLAYABLE).take(HorizonTray.horizonFor(level)) else emptyList()

            return GameState(
                grid = startGrid,
                replacementRulesVersion = 12,
                reservePieces = if(ReserveRules.appliesTo(level)) dealtWithSeeds.drop(3) else emptyList(),
                routeAbilityCompanionId = null,
                routes = emptyList(),
                cometMark = null,
                constellationPoints = if (ConstellationRules.appliesTo(level)) ConstellationRules.points(level, 0, startGrid.filterValues { it is CellContent.Empty }.keys, startGrid.filterValues { it is CellContent.Wood }.keys) else emptyList(),
                trampolineCell = if (TrampolineRules.appliesTo(level)) TrampolineRules.cell(level, GRID_SIZE, GRID_SIZE, startGrid.filterValues { it is CellContent.Empty }.keys) else null,
                availablePieces = pieces,
                pieceHorizon = horizon,
                level = level,
                score = score,
                teikoins = teikoins,
                highScore = highScore,
                maxTeikas = maxTeikas,
                targetTeika = target,
                teikasFormed = 0,
                teikasCreatedInLevel = mapOf(
                    TeikaType.TEIKA_1 to 0,
                    TeikaType.TEIKA_2 to 0,
                    TeikaType.TEIKA_3 to 0,
                    TeikaType.TEIKA_4 to 0
                ),
                levelObjective = layout?.second,
                targetsCollected = emptyMap(),
                // Contadores de los objetivos nuevos: SIEMPRE a cero al empezar un nivel. Arrastrar
                // el de la partida anterior regalaría la victoria en el primer turno.
                linesClearedInLevel = 0,
                placementsInLevel = 0,
                hotCells = HotCells.forLevel(
                    level = level,
                    // (La PORTERÍA, que también contaba como ocupada, se retiró el 2026-09-26.)
                    occupied = startGrid.filterValues { it.isOccupied }.keys,
                    // (2026-09-22) Aquí se sumaban las CELDAS DE COLOR (P-157c), retiradas. La
                    // vidriera que las sustituye se siembra DESPUÉS y es ella la que esquiva a las
                    // calientes: una marca por celda, que es lo que esta línea defendía.
                ),
                levelTurnBonusAwarded = false,
                // La primera reposición ya sabe qué acaba de ver el jugador: sin esto, la bandeja
                // de apertura —la más mirada de todo el nivel— podía repetirse entera en el turno 4.
                lastTrayShapes = dealt.map { it.shape },
                // P-244: la primera vidriera. Esquiva las calientes: una marca de
                // vidrio encima de otra marca diría dos cosas en el mismo cuadro.
                // P-233: el primer molde.
                moldShape = moldShape0,
                moldAt = moldAt0,
                moldWave = mold0?.wave ?: 0,
                // P-242: la primera piñata.
                pinataCell = if (tutorialSpec != null) null else Pinata.cellFor(level, 0),
                pinataDir = Pinata.startDirFor(level, 0),
                // P-235: el primer huevo, en una casilla libre de la figura de salida.
                eggCell = if (tutorialSpec != null) null else TeikaEgg.cellFor(
                    level = level,
                    wave = 0,
                    blocked = startGrid.filterValues { it.isOccupied }.keys + HotCells.forLevel(
                        level = level,
                        occupied = startGrid.filterValues { it.isOccupied }.keys,
                    ),
                    // P-313: pegado a la madera de salida, no en medio del hueco.
                    wood = startGrid.filterValues { it is CellContent.Wood }.keys,
                ),
                glassCells = if (tutorialSpec != null) emptySet() else StainedGlass.windowFor(
                    level = level,
                    wave = 0,
                    avoid = HotCells.forLevel(
                        level = level,
                        occupied = startGrid.filterValues { it.isOccupied }.keys,
                    ),
                ),
                biomePrimary = biomePrimary,
                biomeTransition = biomeTransition,
                status = GameStatus.SPLASH
            )
        }
    }
    
    /**
     * Utilidad para obtener el contenido de una celda específica de manera segura.
     */
    fun getCell(x: Int, y: Int): CellContent {
        if (x !in 0 until gridW || y !in 0 until gridH) return CellContent.Empty
        return grid[Position(x, y)] ?: CellContent.Empty
    }
}
