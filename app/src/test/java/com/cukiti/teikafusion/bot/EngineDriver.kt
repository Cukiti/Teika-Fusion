package com.cukiti.teikafusion.bot

import androidx.test.core.app.ApplicationProvider
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.model.GameStatus
import com.cukiti.teikafusion.model.Piece
import com.cukiti.teikafusion.model.PreferencesManager
import com.cukiti.teikafusion.ui.SoundManager
import com.cukiti.teikafusion.viewmodel.GameViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain

/**
 * Conduce el [GameViewModel] REAL dentro de Robolectric, haciendo de «pantalla»: avanza el reloj
 * virtual de las corrutinas, mueve la física cuadro a cuadro (lo que en el juego hace `GameScreen`
 * con `withFrameNanos`) y consume cada evento como lo haría la capa de VFX.
 *
 * No hay ni una regla del juego copiada aquí: el bot juega contra el mismo motor, los mismos biomas
 * y las mismas reglas de la casa que el jugador. Lo que NO hace (y el informe lo dice): lanzar
 * habilidades de compañeros, aceptar revivir ni usar el banco de bloques. Inclina para juntar Teikas
 * y para desatascarse, y toca las bobinas de la Tormenta sin leer su número oculto.
 *
 * El reloj es mixto a propósito: el `Main` es virtual (animaciones y retardos del motor, instantáneos)
 * pero `prepareRound` corre en `Dispatchers.Default` con hilos reales, de ahí el `Thread.sleep(1)`
 * — solo mientras se carga el motor y arranca el nivel ([realThreads]). Por cuadro de juego no hace
 * falta y era buena parte del tiempo del bot (2026-09-28). Las esperas del arranque siguen FIJAS a
 * propósito: acortarlas a «hasta que cargue el perfil» dejaba corrutinas del arranque aterrizando a
 * mitad de partida, y la misma semilla ya no daba la misma partida.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EngineDriver {
    private val main = StandardTestDispatcher()
    val vm: GameViewModel

    /** Hay trabajo en hilos REALES que esperar (carga de DataStore, `prepareRound`): cada cuadro cede tiempo. */
    private var realThreads = true

    init {
        // (2026-09-28) Con un motor nuevo por partida, alguna corrutina de la partida ANTERIOR puede estar
        // todavía leyendo `Dispatchers.Main` desde un hilo real (`prepareRound` corre en Default) justo
        // cuando este motor lo cambia: «Dispatchers.Main is used concurrently with setting it», y la tanda
        // entera se caía (sonda del 2026-09-28, capítulo 1, partida 21). Se reintenta tras ceder un poco.
        var intentos = 0
        while (true) {
            try { Dispatchers.setMain(main); break } catch (e: IllegalStateException) {
                if (++intentos >= 50) throw e
                Thread.sleep(20)
            }
        }
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        vm = GameViewModel(PreferencesManager(ctx), SoundManager(ctx), ctx)
        // El motor mide sus esperas de juego con el reloj VIRTUAL del bot, no con el de pared.
        vm.clockMs = { CLOCK_BASE_MS + clockMs }
        waitFor(3_000) { false } // deja que termine la carga inicial desde DataStore
        vm.setMaxLevel(com.cukiti.teikafusion.model.LevelMap.totalLevels)
        vm.grantInfiniteLives(7L * 24 * 3600 * 1000)
        waitFor(1_000) { false }
        realThreads = false
    }

    val state: GameState get() = vm.gameState.value

    /**
     * **CENSO DE NACIMIENTOS** (2026-09-26): de qué tier nace cada Teika de un bloque-Teika, y por qué
     * vía un cuerpo aparece «ya grande» (fila pura, descarga de la Tormenta, o dos que se funden nada
     * más nacer). Se lee en los eventos antes de consumirlos: el bot no dibuja, así que nadie más los mira.
     */
    class BirthCensus {
        val seedByTier = IntArray(6)
        var bigWithPure = 0
        var bigFromStorm = 0
        var fusions = 0
        var fusionsAtBirth = 0
        // ── La bandeja (2026-09-26): cada reposición, medida al llegar ──
        var trays = 0
        var trayPieces = 0
        var trayCells = 0
        var trayNoFit = 0
        var rewardDouble = 0
        var rewardTriple = 0
        // ── Revisión completa (2026-09-26) ──
        /** Fichas de ≤ 3 casillas repartidas. */
        var traySmall = 0
        /** Bandejas que, al llegar, no cabían JUNTAS en ningún orden ni giro (P-314 lo garantiza al repartir). */
        var trayNotTogether = 0
        /**
         * Censo: cada evento visto, por su nombre (`MeteorImpact`, `PoisonRot`…), y unidades en minúscula
         * de lo que cada castigo deja en el tablero (`roca`, `lodo`, `oro`…). Cubre cualquier bioma o regla,
         * también los de mañana: un evento nuevo aparece aquí sin tocar el bot.
         */
        val events = HashMap<String, Int>()
        fun add(key: String, n: Int = 1) { if (n > 0) events[key] = (events[key] ?: 0) + n }
    }
    private var lastRefillCount = -1
    /** Hay una bandeja nueva por auditar en cuanto el tablero se asiente (ver [auditTray]). */
    private var pendingTrayAudit = false
    var census = BirthCensus()
        private set
    private var clockMs = 0L
    private val bornAt = HashMap<String, Long>()
    private val seenEvents = HashSet<String>()

    fun tick(ms: Long = FRAME_MS) {
        runBlocking { vm.onTeikaPhysicsFrame(ms / 1000f) }
        main.scheduler.advanceTimeBy(ms)
        main.scheduler.runCurrent()
        clockMs += ms
        vm.teikaBodies.value.forEach { bornAt.putIfAbsent(it.id, clockMs) }
        val s = state
        if (s.trayRefillCount != lastRefillCount) {
            if (lastRefillCount >= 0 && s.availablePieces.isNotEmpty()) {
                census.trays++
                s.availablePieces.forEach { p ->
                    census.trayPieces++
                    census.trayCells += p.shape.size
                    if (p.shape.size <= 3) census.traySmall++
                }
                pendingTrayAudit = true
            }
            lastRefillCount = s.trayRefillCount
        }
        val events = s.events
        val pureNow = events.any { it is com.cukiti.teikafusion.model.GameEvent.PureLine }
        val stormNow = events.any { it is com.cukiti.teikafusion.model.GameEvent.CoilDischarge }
        events.forEach { e ->
            if (!seenEvents.add(e.id)) return@forEach
            census.add(e::class.simpleName ?: "?")
            when (e) {
                is com.cukiti.teikafusion.model.GameEvent.MeteorImpact -> {
                    census.add("roca", e.rocks.size); census.add("metralla", e.shards.size)
                }
                is com.cukiti.teikafusion.model.GameEvent.RockCrack -> census.add("grieta")
                is com.cukiti.teikafusion.model.GameEvent.MoonReturn -> census.add("luna_devuelve", e.cells.size)
                is com.cukiti.teikafusion.model.GameEvent.PoisonSpread -> if (e.splash) census.add("salpicadura")
                is com.cukiti.teikafusion.model.GameEvent.KintsugiScar -> census.add("oro", e.cells.size)
                is com.cukiti.teikafusion.model.GameEvent.VineSprout -> census.add("enredadera", e.cells.size)
                is com.cukiti.teikafusion.model.GameEvent.PlantDrag -> if (e.lethal) census.add("raiz_se_lo_lleva")
                is com.cukiti.teikafusion.model.GameEvent.MegaTeikaFriendExpansion -> census.add("mega")
                else -> Unit
            }
            when (e) {
                is com.cukiti.teikafusion.model.GameEvent.TeikaSeedBurst -> {
                    census.seedByTier[e.tier.coerceIn(0, 5)]++
                    if (e.tier >= 2 && pureNow) census.bigWithPure++
                    if (e.tier >= 2 && stormNow) census.bigFromStorm++
                }
                is com.cukiti.teikafusion.model.GameEvent.TrayRewardDealt ->
                    if (e.reward >= com.cukiti.teikafusion.model.TrayReward.TRIPLE) census.rewardTriple++
                    else census.rewardDouble++
                is com.cukiti.teikafusion.model.GameEvent.FusionCharge -> {
                    census.fusions++
                    val young = listOf(e.aId, e.bId).any { id -> clockMs - (bornAt[id] ?: clockMs) <= BIRTH_FUSION_MS }
                    if (young) census.fusionsAtBirth++
                }
                else -> Unit
            }
        }
        events.forEach { vm.consumeEvent(it.id) }
        if (realThreads) Thread.sleep(1)
    }

    /**
     * **¿CABE LA BANDEJA ENTERA?** Se mira justo antes de la primera jugada de cada bandeja nueva, con el
     * tablero ya ASENTADO. Mirarlo al llegar la bandeja acusaba en falso: los Teikas recién nacidos aún
     * caían por el tablero, y el repartidor ya los cuenta donde se van a quedar (P-314).
     */
    fun auditTray() {
        if (!pendingTrayAudit) return
        pendingTrayAudit = false
        val s = state
        if (s.status != GameStatus.PLAYING || s.availablePieces.isEmpty()) return
        s.availablePieces.forEach { p -> if (!fitsAnywhere(p)) census.trayNoFit++ }
        if (!fitsTogether(s)) {
            census.trayNotTogether++
            if (System.getenv("TRAY_DBG") != null) {
                println("DBG bandeja no cabe junta L${s.level} reposicion=${s.trayRefillCount} fichas=" +
                    s.availablePieces.map { p -> p.shape.size.toString() + (if (p.isGrowing) "g" else "") + (if (p.isMagnet) "m" else "") })
                val teikas = vm.teikaOccupiedCellsForUi()
                for (y in 0 until 8) println("DBG " + (0 until 8).joinToString("") { x ->
                    val q = com.cukiti.teikafusion.model.Position(x, y)
                    when { q in teikas -> "T"; s.grid[q]?.isOccupied == true -> "#"; q in s.unplaceableCells -> "x"; else -> "." } })
                s.availablePieces.forEach { p -> println("DBG   " + p.shape) }
            }
        }
    }

    /**
     * ¿Caben TODAS las fichas de la bandeja a la vez, en algún orden, sitio y giro? Geometría pura sobre
     * el tablero que ve el jugador (madera, casillas vetadas, lava, cuerpos de Teika y el Corte Ninja), sin
     * contar las líneas que se romperían por el camino — la misma lectura prudente que el repartidor.
     * Con techo de nodos: si se agota, cuenta como que sí cabe (no se acusa sin prueba).
     */
    private fun fitsTogether(s: GameState): Boolean {
        val tray = s.availablePieces
        if (tray.size < 2) return true
        var blocked = 0L
        fun bit(x: Int, y: Int) = 1L shl (y * 8 + x)
        s.grid.forEach { (p, c) -> if (c.isOccupied && p.x in 0 until 8 && p.y in 0 until 8) blocked = blocked or bit(p.x, p.y) }
        (s.unplaceableCells + vm.teikaOccupiedCellsForUi()).forEach { p ->
            if (p.x in 0 until 8 && p.y in 0 until 8) blocked = blocked or bit(p.x, p.y)
        }
        for (y in 0 until s.lavaRow.coerceIn(0, 8)) for (x in 0 until 8) blocked = blocked or bit(x, y)
        val split = if (s.ninjaCutActive) com.cukiti.teikafusion.viewmodel.GameViewModel.NINJA_CUT_COL else -1
        val options = tray.map { piece ->
            val masks = ArrayList<Long>()
            var shape = piece
            repeat(4) {
                val w = shape.shape.maxOf { it.x } + 1
                val h = shape.shape.maxOf { it.y } + 1
                for (y in 0..8 - h) for (x in 0..8 - w) {
                    if (split > 0 && x < split && x + w - 1 >= split) continue
                    var m = 0L
                    shape.shape.forEach { c -> m = m or bit(x + c.x, y + c.y) }
                    if (m and blocked == 0L) masks += m
                }
                shape = shape.rotate()
            }
            masks.distinct().toLongArray()
        }.sortedBy { it.size }
        if (options.any { it.isEmpty() }) return false
        var nodes = 50_000
        fun go(i: Int, occ: Long): Boolean {
            if (i == options.size) return true
            for (m in options[i]) {
                if (--nodes < 0) return true
                if (m and occ == 0L && go(i + 1, occ or m)) return true
            }
            return false
        }
        return go(0, blocked)
    }

    /** ¿Cabe [piece] en alguna rotación y sitio del tablero de ahora? La misma pregunta que al soltar. */
    private fun fitsAnywhere(piece: Piece): Boolean {
        var p = piece
        repeat(4) {
            for (y in 0 until 8) for (x in 0 until 8) if (vm.canPlacePiece(p, x, y)) return true
            p = p.rotate()
        }
        return false
    }

    /** Avanza hasta que [cond] se cumpla o pasen [maxMs] virtuales. Devuelve si se cumplió. */
    fun waitFor(maxMs: Long, cond: () -> Boolean): Boolean {
        var t = 0L
        while (t < maxMs) {
            if (cond()) return true
            tick(); t += FRAME_MS
        }
        return cond()
    }

    /**
     * Arranca [level] y espera a que haya tablero y bandeja. Con [seed], el azar del motor
     * (`GameViewModel.rng`) queda sembrado: misma semilla, misma partida (P-287, A/B).
     */
    fun start(level: Int, seed: Long? = null): Boolean {
        vm.rng = if (seed == null) kotlin.random.Random.Default else kotlin.random.Random(seed)
        // Los ids de los cuerpos también: la física ordena y desempata por id (2026-09-28).
        if (seed != null) vm.seedTeikaBodyIds(seed)
        census = BirthCensus()
        lastRefillCount = -1
        pendingTrayAudit = false
        bornAt.clear()
        seenEvents.clear()
        vm.startGame(level)
        realThreads = true
        return waitFor(20_000) { state.status == GameStatus.PLAYING && state.level == level && state.availablePieces.isNotEmpty() }
            .also { realThreads = false }
    }

    /**
     * **¿Se ha quedado quieto el tablero?** El turno cerrado (`boardBusy` cubre colocación, cadena, tick
     * de bioma y reparto), ningún evento por dibujar y todos los cuerpos parados. Es lo que un jugador
     * mira antes de la siguiente jugada.
     */
    private fun quiet(): Boolean =
        !vm.boardBusy.value && state.events.isEmpty() &&
            vm.teikaBodies.value.all { it.kinematic || it.vx * it.vx + it.vy * it.vy < REST_SPEED_SQ }

    /**
     * Espera a que el tablero se asiente: al menos [minMs], y después hasta [QUIET_FRAMES] cuadros
     * seguidos de calma o [maxMs]. Antes era siempre [maxMs] fijo (1,5 s virtuales ≈ 94 cuadros de
     * física) aunque no se moviera nada.
     */
    fun settle(minMs: Long = MIN_SETTLE_MS, maxMs: Long = SETTLE_MS) {
        var t = 0L
        var calm = 0
        while (t < maxMs) {
            tick(); t += FRAME_MS
            calm = if (quiet()) calm + 1 else 0
            if (t >= minMs && calm >= QUIET_FRAMES) return
        }
    }

    /**
     * Coloca y deja que el tablero se resuelva: la cadena de líneas, los Teikas cayendo y
     * fusionándose, y el tic del bioma. Termina cuando vuelve a haber una jugada que pedir o la
     * partida se acaba.
     */
    fun place(piece: Piece, x: Int, y: Int) {
        // Se espera a que la colocación CUENTE (sin turnos desde el 2026-09-23, se mira el contador
        // de colocaciones, que es el que el motor sube al aceptar la ficha).
        val placedBefore = state.placementsInLevel
        // P-276: si la política eligió la ficha girada, se gira EN LA BANDEJA por la misma puerta
        // que el toque del jugador, y se coloca la ficha que el motor tiene de verdad.
        var inTray = state.availablePieces.firstOrNull { it.id == piece.id } ?: piece
        var turns = 0
        while (inTray.shape != piece.shape && turns < 4 && vm.rotatePieceInTray(piece.id)) {
            inTray = state.availablePieces.firstOrNull { it.id == piece.id } ?: break
            turns++
        }
        vm.placePiece(inTray, x, y)
        waitFor(3_000) { state.placementsInLevel != placedBefore || state.status != GameStatus.PLAYING }
        settle()
        waitFor(6_000) { state.status != GameStatus.PLAYING || state.availablePieces.isNotEmpty() }
    }

    companion object {
        const val FRAME_MS = 16L
        /** Origen del reloj virtual que ve el motor: lejos de 0, que algunos relojes usan como «nunca». */
        const val CLOCK_BASE_MS = 1_000_000L
        /** Una fusión en la que uno de los dos lleva menos de esto vivo cuenta como «al nacer». */
        const val BIRTH_FUSION_MS = 1_200L
        /** Tope de espera para que la física asiente y encadene fusiones tras cada colocación. */
        const val SETTLE_MS = 1_500L
        /** Espera mínima tras colocar: deja arrancar la cadena y las caídas aunque todo parezca quieto. */
        const val MIN_SETTLE_MS = 240L
        /** Cuadros seguidos en calma para dar el tablero por asentado. */
        const val QUIET_FRAMES = 6
        /** Velocidad² (casillas/s) por debajo de la cual un cuerpo cuenta como parado. */
        const val REST_SPEED_SQ = 0.01f
    }
}
