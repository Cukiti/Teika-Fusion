package com.cukiti.teikafusion.bot

import com.cukiti.teikafusion.model.GameStatus
import com.cukiti.teikafusion.model.HouseRule
import com.cukiti.teikafusion.model.LevelMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.random.Random

/**
 * **El bot de niveles** (paso 4 del flujo de trabajo, 2026-09-22): juega los niveles con el motor
 * REAL y deja un informe de dificultad medida — victorias, estrellas, turnos gastados y por qué se
 * pierde — por nivel y por bioma. Convierte «el presupuesto de turnos sale del playtest» en datos, y
 * deja el playtest humano para las SENSACIONES.
 *
 * No corre con la suite normal (tarda minutos). Se lanza a propósito:
 *
 * ```
 * LEVEL_BOT=8-60 LEVEL_BOT_RUNS=3 ./gradlew.bat :app:testDebugUnitTest --tests "*LevelBotTest"
 * ```
 *
 * `LEVEL_BOT` acepta `8-60`, `12`, una lista `14,75,86`, un capítulo `c7` (1..14) o `all`. Informe en
 * `app/build/reports/level-bot/` o en `LEVEL_BOT_OUT`: `levels.csv` (una fila por partida, con presión,
 * reparto y censo de biomas), `anomalias.csv` (posibles bugs, con semilla), `derrotas/` (el tablero de cada
 * derrota) y `resumen.md` (veredicto por bioma contra su objetivo, ver [BotReport]).
 *
 * **Campaña entera** (2026-09-26): `tools/bot_campana.sh` corre un capítulo por JVM y después
 * `LEVEL_BOT_MERGE=<carpeta>` junta todos los `levels.csv` de sus subcarpetas en un solo informe.
 * `LEVEL_BOT_BASE=<levels.csv viejo>` añade la columna «Antes» para comparar (A/B).
 * `LEVEL_BOT_FROM_RUN=2` salta las partidas anteriores: completa una pasada cortada sin repetir lo
 * jugado (cada partida usa la misma semilla del jugador, `nivel·1000 + partida`).
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LevelBotTest {


    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `el bot juega los niveles pedidos y escribe el informe`() {
        val spec = System.getenv("LEVEL_BOT")
        assumeTrue("Bot de niveles desactivado (LEVEL_BOT no definido)", !spec.isNullOrBlank())
        val levels = parseLevels(spec!!)
        val runs = System.getenv("LEVEL_BOT_RUNS")?.toIntOrNull() ?: 1
        val fromRun = System.getenv("LEVEL_BOT_FROM_RUN")?.toIntOrNull()?.coerceIn(1, runs) ?: 1

        val results = ArrayList<BotResult>()
        val anomalies = ArrayList<String>()
        val out = outDir()
        // Una fila por partida EN CUANTO termina: una pasada larga que se corta (memoria, cierre)
        // conserva lo jugado. El resumen agregado se escribe al final.
        val live = out.apply { mkdirs() }.resolve("levels-en-curso.csv")
        live.writeText(BotCsv.header() + "\n")
        val liveAnomalies = out.resolve("anomalias-en-curso.csv")
        liveAnomalies.writeText(BotReport.ANOMALY_HEADER + "\n")
        // Censo de nacimientos (2026-09-26): una fila por partida, ver [EngineDriver.BirthCensus].
        val births = live.resolveSibling("nacimientos.csv")
        births.writeText("level,run,biome,seed_t1,seed_t2,seed_t3,big_con_pura,big_tormenta,fusiones,fusiones_al_nacer,bandejas,fichas,casillas,no_caben,premio_doble,premio_triple\n")
        for (run in fromRun..runs) for (level in levels) {
            // UN MOTOR NUEVO POR PARTIDA (2026-09-27, rematando B-3). Con uno solo para toda la tanda, el
            // reloj de la física, los relojes de fusión, las cachés y los acumuladores del ViewModel pasaban
            // de una partida a la siguiente: el mismo nivel con la misma semilla daba otra partida según qué
            // se hubiera jugado antes, y una anomalía de la campaña no se podía reproducir sola.
            val driver = EngineDriver()
            val found = ArrayList<String>()
            results += try {
                play(driver, level, run, GreedyPolicy(Random(level * 1_000 + run)), found, out)
            } catch (t: Throwable) {
                // Una excepción del motor no tumba la campaña: se anota como anomalía y se sigue.
                found += anomaly(level, run, driver.state.placementsInLevel, "EXCEPCION", "${t::class.simpleName}: ${t.message}")
                runCatching { driver.vm.goHome(); driver.waitFor(500) { false } }
                BotResult(level, run, LevelMap.biomeFor(level).name, HouseRule.forLevel(level)?.name ?: "-", "?",
                    "EXCEPCION", "${t::class.simpleName}", 0, 0, 0, 0, 0, 0, anomalies = 1)
            }
            anomalies += found
            found.forEach { liveAnomalies.appendText(it + "\n") }
            val r = results.last()
            live.appendText(BotCsv.row(r) + "\n")
            driver.census.let { c ->
                births.appendText("${r.level},${r.run},${r.biome},${c.seedByTier[1]},${c.seedByTier[2]},${c.seedByTier[3]}," +
                    "${c.bigWithPure},${c.bigFromStorm},${c.fusions},${c.fusionsAtBirth}," +
                    "${c.trays},${c.trayPieces},${c.trayCells},${c.trayNoFit},${c.rewardDouble},${c.rewardTriple}\n")
            }
            // Sin turnos desde el 2026-09-23: la medida de esfuerzo son las colocaciones.
            println("L%03d #%d %-9s %-8s %s %s★ %d colocaciones, %d líneas, %s (%d ms)".format(
                r.level, r.run, r.biome, r.outcome, r.cause, r.stars, r.placements, r.lines, r.progress, r.realMs))
        }
        BotReport.write(out, results, anomalies, base())
    }

    /**
     * **Junta una campaña corrida por capítulos** (`tools/bot_campana.sh`): lee el `levels.csv` y el
     * `anomalias.csv` de cada subcarpeta de `LEVEL_BOT_MERGE` y escribe un solo informe en ella.
     */
    @Test
    fun `informe de la campana`() {
        val root = System.getenv("LEVEL_BOT_MERGE")
        assumeTrue("Fusión desactivada (LEVEL_BOT_MERGE no definido)", !root.isNullOrBlank())
        val dir = File(root!!)
        val parts = dir.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }
        val results = parts.flatMap { part -> part.resolve("levels.csv").takeIf { it.isFile }?.let(BotCsv::read).orEmpty() }
        val anomalies = parts.flatMap { part ->
            part.resolve("anomalias.csv").takeIf { it.isFile }?.readLines()?.drop(1)?.filter { it.isNotBlank() }.orEmpty()
        }
        assumeTrue("No hay partidas que juntar en $dir", results.isNotEmpty())
        BotReport.write(dir, results.sortedWith(compareBy({ it.level }, { it.run })), anomalies, base())
    }

    private fun outDir(): File = File(System.getenv("LEVEL_BOT_OUT")?.takeIf { it.isNotBlank() } ?: "build/reports/level-bot")

    private fun base(): List<BotResult>? =
        System.getenv("LEVEL_BOT_BASE")?.takeIf { it.isNotBlank() }?.let { File(it) }?.takeIf { it.isFile }?.let(BotCsv::read)

    private fun anomaly(level: Int, run: Int, placement: Int, type: String, detail: String) =
        "$level,$run,$placement,$type,${detail.replace(',', ';').replace('\n', ' ').take(200)},${level * 1_000L + run + 500_000L}"

    /**
     * **EL DETECTOR DE BUGS**: lo que nunca debería pasar con la partida en juego. Se mira tras cada
     * colocación, cuando el tablero ya se asentó. Cada fallo es una fila de `anomalias.csv`.
     */
    private fun invariants(driver: EngineDriver, level: Int, run: Int, placement: Int): List<String> {
        val s = driver.state
        if (s.status != GameStatus.PLAYING) return emptyList()
        val out = ArrayList<String>()
        if (s.availablePieces.isEmpty() && !com.cukiti.teikafusion.model.TutorialCampaign.isTutorialLevel(level)) {
            out += anomaly(level, run, placement, "BANDEJA_VACIA", "jugando y sin fichas")
        }
        s.grid.keys.firstOrNull { it.x !in 0 until 8 || it.y !in 0 until 8 }?.let {
            out += anomaly(level, run, placement, "CELDA_FUERA", "casilla $it")
        }
        // Madera DENTRO de un cuerpo: el centro del Teika cae en una casilla con madera. El resolvedor no
        // tiene por dónde expulsarlo (ver CLAUDE.md, «Teikas are floor»).
        driver.vm.teikaBodies.value.filter { !it.kinematic }.forEach { b ->
            val cell = com.cukiti.teikafusion.model.Position(b.x.toInt().coerceIn(0, 7), b.y.toInt().coerceIn(0, 7))
            if (s.grid[cell] is com.cukiti.teikafusion.model.CellContent.Wood) {
                out += anomaly(level, run, placement, "MADERA_EN_TEIKA", "${b.type} con el centro en $cell")
            }
        }
        return out
    }

    /** Tablero lleno, 0..1: madera y lo demás que ocupa, más las casillas que pisan los cuerpos. */
    private fun fill(driver: EngineDriver): Double {
        val s = driver.state
        val occupied = s.grid.filter { it.value !is com.cukiti.teikafusion.model.CellContent.Empty }.keys +
            driver.vm.teikaOccupiedCellsForUi()
        return occupied.count { it.x in 0 until 8 && it.y in 0 until 8 } / 64.0
    }

    private fun play(
        driver: EngineDriver, level: Int, run: Int, policy: GreedyPolicy,
        anomalies: MutableList<String>, out: File,
    ): BotResult {
        val t0 = System.currentTimeMillis()
        val rule = HouseRule.forLevel(level)?.name ?: "-"
        val biome = LevelMap.biomeFor(level).name
        // La partida (level, run) usa siempre la misma semilla del motor: repetible y comparable en A/B.
        if (!driver.start(level, seed = level * 1_000L + run + 500_000L)) {
            return BotResult(level, run, biome, rule, "?", "NO_ARRANCA", "status=${driver.state.status}", 0, 0, 0, 0, 0,
                System.currentTimeMillis() - t0)
        }
        val goal = driver.state.levelObjective?.goal?.name ?: "?"
        if (System.getenv("LEVEL_BOT_TRACE") != null) {
            print(dump(driver.state))
            println("   ¿bandeja inicial resoluble (orden y sitio libres, con líneas)? " + solvable(driver.state))
        }
        val total = driver.state.movesTotal
        var placements = 0
        var tilts = 0
        var coilRounds = 0
        var fusions = 0
        var stuckRetries = 0
        val refused = HashSet<Triple<String, Int, Int>>()
        var fillSum = 0.0
        var fillPeak = 0.0
        var dangerTurns = 0
        var megaAt = -1
        val refusals = ArrayList<String>()
        var outcome = "TIMEOUT"
        var cause = ""
        while (placements < MAX_PLACEMENTS) {
            val s = driver.state
            when (s.status) {
                GameStatus.LEVEL_COMPLETE_PENDING, GameStatus.LEVEL_COMPLETE -> { outcome = "VICTORIA"; break }
                GameStatus.REVIVE_OFFER, GameStatus.GAME_OVER -> { outcome = "DERROTA"; cause = s.defeatCause.name; break }
                GameStatus.PLAYING -> Unit
                else -> { outcome = "OTRO"; cause = s.status.name; break }
            }
            // Tormenta: una bobina bloquea su fila y su columna, así que un jugador descarga el
            // circuito en cuanto aparece. Si hubo algo que tocar, se vuelve a mirar el tablero.
            if (s.biomePrimary == com.cukiti.teikafusion.model.BiomeType.STORM && coilRounds < MAX_COIL_ROUNDS) {
                if (dischargeCoils(driver) > 0) { coilRounds++; continue }
            }
            // LAS FICHAS QUE SE FUNDEN: un jugador funde dos gemelas cuando el plan con la ficha grande
            // es mejor que con las dos pequeñas. No gasta turno, así que se vuelve a mirar el tablero.
            if (fuseIfBetter(driver, policy, refused)) { fusions++; continue }
            driver.auditTray()
            // P-366: si la próxima colocación es el golpe del bioma, el bot busca la parada (y echa la pieza del Rey).
            val move = policy.choose(s, refused, driver.vm.teikaOccupiedCellsForUi(), driver.vm.parryForNextPlacement()) { p, x, y -> driver.vm.canPlacePiece(p, x, y) }
            if (move == null) {
                // Lo mismo que hace GameScreen al soltar una ficha que no cabe: preguntar al motor.
                when (val dead = driver.vm.evaluateDeadEnd()) {
                    null -> {
                        // El motor dice que SÍ hay jugada: con Teikas rodando, el hueco puede abrirse entre
                        // el barrido del bot y la pregunta (el «atasco» del nivel 103, 2026-09-27). Un
                        // jugador esperaría un momento y volvería a mirar; el bot, igual, unas veces.
                        if (stuckRetries++ < MAX_STUCK_RETRIES) {
                            driver.waitFor(700) { false }
                            refused.clear()
                            continue
                        }
                        outcome = "ATASCO"; cause = "canPlacePiece=no en todo, evaluateDeadEnd=hay jugada (bandeja ${s.availablePieces.map { it.shape.size }})"; break
                    }
                    com.cukiti.teikafusion.model.BoardDeadEnd.None -> {
                        if (System.getenv("LEVEL_BOT_TRACE") != null) println("   SIN SALIDA:\n" + dump(s, driver.vm.teikaOccupiedCellsForUi()))
                        driver.vm.triggerNoSpaceRevive()
                        driver.waitFor(3_000) { driver.state.status != GameStatus.PLAYING }
                    }
                    com.cukiti.teikafusion.model.BoardDeadEnd.Tilt -> {
                        // Un jugador inclinaría el móvil: a un lado, al otro, y de vuelta a plano.
                        if (System.getenv("LEVEL_BOT_TRACE") != null && tilts == 0) println("   ATASCO (inclinar):\n" + dump(s, driver.vm.teikaOccupiedCellsForUi()))
                        if (tilts++ >= MAX_TILTS) { outcome = "DERROTA"; cause = "SIN_ESPACIO(inclinar no basta)"; break }
                        val side = if (tilts % 2 == 1) 6f else -6f
                        driver.vm.onDeviceTilt(side); driver.waitFor(900) { false }
                        driver.vm.onDeviceTilt(0f); driver.waitFor(600) { false }
                    }
                    // P-349 · EL BANCO: el bot NO lo usa (2026-09-28, decisión del usuario; antes era opcional
                    // con `LEVEL_BOT_BANK`). Mide la dificultad sin él: si la única salida es el banco, pierde.
                    is com.cukiti.teikafusion.model.BoardDeadEnd.Hold -> { outcome = "DERROTA"; cause = "SIN_ESPACIO(solo con banco)"; break }
                    else -> { outcome = "DERROTA"; cause = "SIN_ESPACIO(solo con habilidad: $dead)"; break }
                }
                continue
            }
            val placedBefore = s.placementsInLevel
            if (System.getenv("LEVEL_BOT_TRACE") != null) {
                println(dump(s, driver.vm.teikaOccupiedCellsForUi()))
                println("   → coloca ficha de ${move.piece.shape.size}c en (${move.x},${move.y})")
            }
            driver.place(move.piece, move.x, move.y)
            if (driver.state.status == GameStatus.PLAYING && driver.state.placementsInLevel == placedBefore &&
                driver.state.availablePieces.any { it.id == move.piece.id }
            ) {
                // canPlacePiece dijo que sí y placePiece no la colocó: en el juego, el fantasma
                // prometería un hueco donde la ficha rebota. Se anota y se prueba otra jugada.
                refused += Triple(refusalKeyOf(move.piece), move.x, move.y)
                refusals += "${move.piece.shape.size}c@(${move.x},${move.y})"
                if (refused.size >= MAX_REFUSALS) { outcome = "RECHAZO"; cause = "canPlace=sí, placePiece=no: ${refusals.take(3)}"; break }
                continue
            }
            placements++
            stuckRetries = 0
            // B-2 (2026-09-27): un rechazo vale para ESTE tablero. Se guardaban toda la partida, así que
            // una jugada rechazada una vez —un Teika que rodó justo entre la comprobación y la
            // colocación— quedaba vetada para siempre: con una sola ficha en la mano el bot se quedaba sin
            // jugadas mientras el motor, con razón, decía que había una («ATASCO» del nivel 14).
            refused.clear()
            // Presión, MEGA y el detector de bugs, con el tablero ya asentado.
            val f = fill(driver)
            fillSum += f
            if (f > fillPeak) fillPeak = f
            if (f >= DANGER_FILL) dangerTurns++
            if (megaAt < 0 && (driver.census.events["mega"] ?: 0) > 0) megaAt = placements
            anomalies += invariants(driver, level, run, placements)
            // El tope de bobinas es POR TURNO (evita un bucle si un toque no descarga), no por partida:
            // con un tope global el bot dejaba de tocar el circuito y el tablero quedaba sin derrota
            // —el circuito es una salida— y sin jugada (L96, revisión del 2026-09-23).
            coilRounds = 0
            herdTeikas(driver)
        }
        val end = driver.state
        if (outcome in setOf("ATASCO", "RECHAZO", "TIMEOUT", "OTRO")) {
            anomalies += anomaly(level, run, placements, outcome, cause.ifBlank { end.status.name })
        }
        // LA FOTO DE LA DERROTA: el tablero con el que se perdió, para ver de qué.
        if (outcome != "VICTORIA") {
            val dir = out.resolve("derrotas").apply { mkdirs() }
            dir.resolve("L%03d_p%d.txt".format(level, run)).writeText(
                "$outcome $cause · semilla ${level * 1_000L + run + 500_000L}\n" +
                    dump(end, driver.vm.teikaOccupiedCellsForUi())
            )
        }
        // Las estrellas de ESTA partida, con la misma regla y el mismo compañero que usa el juego al
        // ganar. Leer `levelStars` daba el MEJOR guardado: desde la 2.ª partida de un nivel repetía
        // las de la 1.ª (2026-09-24: L92 con 12 200 puntos salía 1★).
        val stars = if (outcome == "VICTORIA") {
            val companion = driver.vm.selectedCompanionId.value
            com.cukiti.teikafusion.model.LevelLayouts.starsFor(
                level, end.score, end.movesLeft, end.movesTotal,
                companionId = companion,
                companionLevel = companion?.let { driver.vm.companionUpgrades.value.levelOf(it) } ?: 1,
            ).also {
                driver.vm.confirmLevelComplete()
                driver.waitFor(1_000) { false }
            }
        } else 0
        val c = driver.census
        val result = BotResult(level, run, biome, rule, goal, outcome, cause, stars,
            (total - end.movesLeft).coerceAtLeast(0), total, end.linesClearedInLevel, placements,
            System.currentTimeMillis() - t0,
            score = end.score,
            guaranteed = com.cukiti.teikafusion.model.LevelLayouts.guaranteedScore(level),
            twoStar = com.cukiti.teikafusion.model.LevelLayouts.twoStarScore(level),
            threeStar = com.cukiti.teikafusion.model.LevelLayouts.threeStarScore(level),
            fillAvg = if (placements > 0) fillSum / placements else 0.0,
            fillPeak = fillPeak, dangerTurns = dangerTurns, megaAt = megaAt,
            trays = c.trays, trayPieces = c.trayPieces, traySmall = c.traySmall, trayNoFit = c.trayNoFit,
            trayNotTogether = c.trayNotTogether,
            anomalies = anomalies.count { it.startsWith("$level,$run,") },
            census = HashMap(c.events),
            progress = end.levelObjective?.let { o -> "${o.goal}:" + o.required.entries.joinToString("+") { (c, n) -> "${end.targetsCollected[c] ?: 0}/$n" } + if (o.amount > 0) " amount=${o.amount}" else "" }.orEmpty() + (if (fusions > 0) " fusiones=$fusions" else ""))
        driver.vm.goHome()
        driver.waitFor(500) { false }
        return result
    }

    /**
     * Descarga UNA fila de bobinas como lo haría una persona: sin leer el número oculto. Solo usa lo
     * que la pantalla enseña —cuántas lleva encendidas y los números ya descubiertos (`coilSeen`)— y
     * prueba las demás; un cortocircuito apaga la fila pero lo aprendido se queda. Devuelve cuántas tocó.
     */
    /**
     * Funde la pareja de gemelas cuyo plan supera al de la bandeja sin tocar, si la hay. Pregunta al
     * motor (`canFusePieces`) si la fusión es legal —misma regla que el aviso de la UI— y la hace
     * con `fusePieces`, el mismo camino que el gesto de arrastrar una ficha sobre su gemela.
     */
    private fun fuseIfBetter(driver: EngineDriver, policy: GreedyPolicy, refused: Set<Triple<String, Int, Int>>): Boolean {
        val s = driver.state
        if (!com.cukiti.teikafusion.model.PieceFusion.appliesTo(s.level)) return false
        val canPlace: (com.cukiti.teikafusion.model.Piece, Int, Int) -> Boolean = { p, x, y -> driver.vm.canPlacePiece(p, x, y) }
        val base = policy.choose(s, refused, driver.vm.teikaOccupiedCellsForUi(), canPlace = canPlace)?.score ?: Int.MIN_VALUE
        var best: Pair<String, String>? = null
        var bestScore = base
        val tray = s.availablePieces
        for (a in tray) for (b in tray) {
            if (a.id == b.id || !com.cukiti.teikafusion.model.PieceFusion.canFuse(a, b)) continue
            if (driver.vm.canFusePieces(a.id, b.id) != com.cukiti.teikafusion.model.FuseCheck.OK) continue
            val fused = com.cukiti.teikafusion.model.PieceFusion.fused(a, b)
            val hypo = s.copy(availablePieces = tray.map { if (it.id == b.id) fused else it }.filter { it.id != a.id })
            val score = policy.choose(hypo, refused, driver.vm.teikaOccupiedCellsForUi(), canPlace = canPlace)?.score ?: continue
            if (score > bestScore) { bestScore = score; best = a.id to b.id }
        }
        val (a, b) = best ?: return false
        return driver.vm.fusePieces(a, b).also { if (it) driver.waitFor(300) { false } }
    }

    private fun dischargeCoils(driver: EngineDriver): Int {
        fun coils(y: Int) = (0 until 8).mapNotNull { x ->
            val p = com.cukiti.teikafusion.model.Position(x, y)
            (driver.state.grid[p] as? com.cukiti.teikafusion.model.CellContent.Wood)?.takeIf { it.coil > 0 }?.let { p to it }
        }
        val row = (0 until 8).firstOrNull { coils(it).isNotEmpty() } ?: return 0
        var taps = 0
        val failed = HashMap<Int, MutableSet<com.cukiti.teikafusion.model.Position>>() // paso → probadas y falladas
        repeat(MAX_COIL_TAPS) {
            if (driver.state.status != GameStatus.PLAYING) return taps
            val here = coils(row)
            if (here.isEmpty()) return taps // descargada
            val step = here.count { it.second.coilLit } + 1
            val known = here.firstOrNull { !it.second.coilLit && it.second.coilSeen && it.second.coil == step }
            val tried = failed.getOrPut(step) { HashSet() }
            val pick = known?.first
                ?: here.firstOrNull { !it.second.coilLit && !it.second.coilSeen && it.first !in tried }?.first
                ?: return taps
            val litBefore = step - 1
            driver.vm.onBoardTap(pick); taps++
            driver.waitFor(700) { false }
            if (coils(row).count { it.second.coilLit } <= litBefore && coils(row).isNotEmpty()) tried += pick
        }
        return taps
    }

    /**
     * Buscar el MEGA como un jugador: la victoria lo exige y los Teikas solo se fusionan al tocarse.
     * Si hay dos del mismo tamaño, inclina el móvil hacia el lado donde ya hay más masa para que se
     * junten, y vuelve a plano. Inclinar no gasta turno.
     */
    private fun herdTeikas(driver: EngineDriver) {
        if (driver.state.status != GameStatus.PLAYING) return
        val bodies = driver.vm.teikaBodies.value.filter { !it.kinematic && it.frozenTurns == 0 }
        val pairs = bodies.groupBy { it.type }.filter { (type, same) ->
            same.size >= 2 && type != com.cukiti.teikafusion.model.TeikaType.MEGA_TEIKA
        }
        if (pairs.isEmpty()) return
        val mass = bodies.sumOf { (it.x - 4f).toDouble() * it.type.level }
        val side = if (mass >= 0) -6f else 6f   // convención Android: +X = lado IZQUIERDO abajo
        // Inclinado hasta que se funda una pareja (o [HERD_TILT_MS]); plano hasta que se asiente.
        val fusionsBefore = driver.census.fusions
        driver.vm.onDeviceTilt(side); driver.waitFor(HERD_TILT_MS) { driver.census.fusions > fusionsBefore }
        driver.vm.onDeviceTilt(0f); driver.settle(minMs = 0L, maxMs = HERD_SETTLE_MS)
    }

    /**
     * Búsqueda exhaustiva: ¿existe ALGÚN orden y sitio para colocar todas las fichas de la bandeja,
     * rompiendo las líneas que se completen? Geometría pura (sin Teikas ni biomas): si dice que no,
     * ni el mejor jugador del mundo podía con esa bandeja.
     */
    private fun solvable(s: com.cukiti.teikafusion.model.GameState): Boolean {
        val filled = s.grid.filter { it.value !is com.cukiti.teikafusion.model.CellContent.Empty }.keys.toMutableSet()
        filled += s.unplaceableCells
        fun go(filled: Set<com.cukiti.teikafusion.model.Position>, left: List<com.cukiti.teikafusion.model.Piece>): Boolean {
            if (left.isEmpty()) return true
            val order = com.cukiti.teikafusion.model.ForcedQueue.playable(s.level, left)
            for (piece in order) for (y in 0 until 8) for (x in 0 until 8) {
                val cells = piece.shape.map { com.cukiti.teikafusion.model.Position(x + it.x, y + it.y) }
                if (cells.any { it.x !in 0 until 8 || it.y !in 0 until 8 || it in filled }) continue
                val after = filled.toMutableSet().apply { addAll(cells) }
                val rows = (0 until 8).filter { yy -> (0 until 8).all { com.cukiti.teikafusion.model.Position(it, yy) in after } }
                val cols = (0 until 8).filter { xx -> (0 until 8).all { com.cukiti.teikafusion.model.Position(xx, it) in after } }
                after.removeAll { it.y in rows || it.x in cols }
                if (go(after, left - piece)) return true
            }
            return false
        }
        return go(filled, s.availablePieces)
    }

    /** Tablero y bandeja en texto: # madera, * estrella, T cuerpo de Teika, ~ lava, . libre. */
    /** Tablero en texto: `*` estrella, `#` madera, `T` cuerpo de Teika, `~` lava, `x` vetada, `E` huevo. */
    private fun dump(s: com.cukiti.teikafusion.model.GameState, teikas: Set<com.cukiti.teikafusion.model.Position> = emptySet()): String = buildString {
        appendLine("── L${s.level} ${s.biomePrimary} · colocaciones=${s.placementsInLevel} · regla=${com.cukiti.teikafusion.model.HouseRule.forLevel(s.level)} · lava hasta fila ${s.lavaRow} (activa=${s.lavaRow > 0})")
        for (y in 0 until 8) {
            append("   ")
            for (x in 0 until 8) {
                val p = com.cukiti.teikafusion.model.Position(x, y)
                val c = s.grid[p]
                append(when {
                    y < s.lavaRow -> '~'
                    p == s.eggCell -> 'E'
                    p in teikas -> 'T'
                    c is com.cukiti.teikafusion.model.CellContent.Wood && c.targetColor != null -> '*'
                    c is com.cukiti.teikafusion.model.CellContent.Wood -> '#'
                    c != null && c !is com.cukiti.teikafusion.model.CellContent.Empty -> '?'
                    p in s.unplaceableCells -> 'x'
                    else -> '.'
                })
            }
            appendLine()
        }
        s.availablePieces.forEachIndexed { i, p ->
            val w = p.shape.maxOf { it.x } + 1; val h = p.shape.maxOf { it.y } + 1
            appendLine("   ficha $i (${p.shape.size}c, ${w}x$h${if (s.isPieceAsleep(p.id)) ", dormida" else ""}): " +
                (0 until h).joinToString("/") { yy -> (0 until w).joinToString("") { xx -> if (p.shape.any { it.x == xx && it.y == yy }) "#" else "." } })
        }
    }

    private fun parseLevels(spec: String): List<Int> {
        val first = com.cukiti.teikafusion.model.TutorialCampaign.LAST_LEVEL + 1
        return when {
            spec.equals("all", ignoreCase = true) -> (first..LevelMap.totalLevels).toList()
            // `c7` = el capítulo 7 (1..14), para correr la campaña por capítulos.
            spec.startsWith("c", ignoreCase = true) -> spec.drop(1).toInt().let { chapter ->
                (first..LevelMap.totalLevels).filter { LevelMap.chapterIndexForLevel(it) == chapter - 1 }
            }
            "-" in spec -> spec.split("-").let { (a, b) -> (a.trim().toInt()..b.trim().toInt()).toList() }
            else -> spec.split(",").map { it.trim().toInt() }
        }
    }



    private companion object {
        const val MAX_PLACEMENTS = 200
        /** A partir de este llenado el tablero está «en peligro». */
        const val DANGER_FILL = 0.70
        const val MAX_TILTS = 4
        /** Veces que el bot espera y vuelve a mirar cuando el motor dice que hay jugada y él no la ve. */
        const val MAX_STUCK_RETRIES = 4
        const val MAX_REFUSALS = 5
        const val MAX_COIL_TAPS = 30
        const val MAX_COIL_ROUNDS = 12
        const val HERD_TILT_MS = 800L
        const val HERD_SETTLE_MS = 500L
    }
}
