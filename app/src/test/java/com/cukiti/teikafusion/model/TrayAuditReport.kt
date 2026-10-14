package com.cukiti.teikafusion.model

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * **AUDITORÍA DEL REPARTO** (2026-09-24): ¿salen fichas demasiado pequeñas o demasiado grandes en
 * algún nivel? Llama al planificador REAL con los parámetros REALES de cada nivel (reposición
 * instantánea, bandeja de cinco, gemelas) sobre tres tableros tipo, y escribe la distribución de
 * tamaños por nivel en `app/build/reports/tray-audit/`. (Desde el 2026-09-26 el tamaño solo depende
 * del tablero: sin sesgos de nivel, sabor ni capítulo.)
 *
 * Opt-in, como el bot: `TRAY_AUDIT=1 ./gradlew.bat :app:testDebugUnitTest --tests "*TrayAuditReport"`.
 * Mide el REPARTO, no la partida: los tableros son sintéticos (vacío, medio lleno por abajo, apretado).
 */
class TrayAuditReport {

    private enum class Board(val label: String) { EMPTY("vacío"), HALF("medio"), TIGHT("apretado") }

    /** Tablero sintético con la madera apilada abajo y huecos, como queda de verdad al jugar. */
    private fun blockedFor(board: Board, rnd: Random): Set<Position> {
        val rows = when (board) { Board.EMPTY -> 0; Board.HALF -> 4; Board.TIGHT -> 6 }
        val out = HashSet<Position>()
        for (y in GameState.GRID_SIZE - rows until GameState.GRID_SIZE)
            for (x in 0 until GameState.GRID_SIZE)
                if (rnd.nextFloat() < 0.8f) out += Position(x, y)
        return out
    }

    @Test
    fun informe() {
        assumeTrue("Opt-in: TRAY_AUDIT=1", System.getenv("TRAY_AUDIT") != null)
        val draws = (System.getenv("TRAY_AUDIT_DRAWS") ?: "300").toInt()
        val csv = StringBuilder("level,chapter,biome,instant,slots,twins,board,mean,p12,p3,p4,p5,p6plus,p89,nofit,maxsize\n")
        val total = LevelMap.totalLevels
        for (level in 1..total) {
            val instant = InstantRefill.appliesTo(level)
            val slots = if (HorizonTray.appliesTo(level)) HorizonTray.PLAYABLE + HorizonTray.horizonFor(level)
                else TrayFive.slotsFor(level) ?: HorizonTray.PLAYABLE_NORMAL
            val twins = TwinTrayRules.appliesTo(level)
            val chapter = LevelMap.chapterIndexForLevel(level)
            val biome = if (chapter >= 0) LevelMap.chapters[chapter].biome.name else "INTRO"
            for (board in Board.values()) {
                val sizes = IntArray(10)
                var nofit = 0
                var n = 0
                val rnd = Random(level * 31 + board.ordinal)
                repeat(draws) {
                    val blocked = blockedFor(board, rnd)
                    val plan = PieceTrayPlanner.plan(
                        boardSize = GameState.GRID_SIZE, blockedCells = blocked,
                        count = if (instant) 1 else slots, level = level,
                        random = rnd,
                        twins = twins && !PieceFusion.appliesTo(level),
                        smallTwins = if (PieceFusion.dealsTwinsOn(level, 0)) PieceFusion.MAX_SOURCE else 0,
                    )
                    plan.forEach {
                        sizes[it.shape.size.coerceAtMost(9)]++
                        if (it.reservedCells.isEmpty()) nofit++
                        n++
                    }
                }
                fun pct(v: Int) = "%.1f".format(100.0 * v / n.coerceAtLeast(1))
                val mean = (1..9).sumOf { it * sizes[it] }.toDouble() / n.coerceAtLeast(1)
                csv.append("$level,$chapter,$biome,$instant,$slots,$twins,${board.label},")
                    .append("%.2f".format(mean)).append(',')
                    .append(pct(sizes[1] + sizes[2])).append(',').append(pct(sizes[3])).append(',')
                    .append(pct(sizes[4])).append(',').append(pct(sizes[5])).append(',')
                    .append(pct((6..9).sumOf { sizes[it] })).append(',').append(pct(sizes[8] + sizes[9])).append(',').append(pct(nofit)).append(',')
                    .append((9 downTo 1).first { sizes[it] > 0 }).append('\n')
            }
        }
        val dir = File("build/reports/tray-audit").apply { mkdirs() }
        File(dir, "tray-audit.csv").writeText(csv.toString())
        println("TRAY_AUDIT -> ${File(dir, "tray-audit.csv").absolutePath}")
    }
}
