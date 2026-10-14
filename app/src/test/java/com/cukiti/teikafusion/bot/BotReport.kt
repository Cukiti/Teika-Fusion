package com.cukiti.teikafusion.bot

import com.cukiti.teikafusion.model.BiomeRhythm
import com.cukiti.teikafusion.model.BiomeType
import com.cukiti.teikafusion.model.ChapterDifficulty
import com.cukiti.teikafusion.model.LevelDirector
import com.cukiti.teikafusion.model.LevelMap
import java.io.File

/**
 * **EL INFORME DEL BOT** (2026-09-26, revisión completa): una fila por partida que se puede volver a leer
 * ([BotCsv]), y un resumen que da el VEREDICTO por bioma contra su objetivo ([BotReport.write]).
 *
 * Todo lo que el bot mide va en `levels.csv`, así que una campaña corrida por capítulos (cada uno en su
 * carpeta, ver `tools/bot_campana.sh`) se junta leyendo esos CSV y escribiendo un solo informe.
 */
data class BotResult(
    val level: Int, val run: Int, val biome: String, val rule: String, val goal: String,
    val outcome: String, val cause: String, val stars: Int,
    val movesUsed: Int, val movesTotal: Int, val lines: Int, val placements: Int, val realMs: Long,
    val progress: String = "",
    /** Puntos al acabar y las metas del nivel (sin compañero): para calibrar las estrellas (P-257). */
    val score: Int = 0, val guaranteed: Int = 0, val twoStar: Int = 0, val threeStar: Int = 0,
    /** Presión: llenado medio y pico del tablero (madera + cuerpos) tras cada colocación, 0..1. */
    val fillAvg: Double = 0.0, val fillPeak: Double = 0.0,
    /** Colocaciones tras las que el tablero quedó al 70 % o más. */
    val dangerTurns: Int = 0,
    /** Colocación en la que nació el primer MEGA (−1 = ninguno). */
    val megaAt: Int = -1,
    /** Reparto: bandejas, fichas, fichas de ≤ 3 casillas, que no caben solas, y bandejas que no caben JUNTAS. */
    val trays: Int = 0, val trayPieces: Int = 0, val traySmall: Int = 0, val trayNoFit: Int = 0,
    val trayNotTogether: Int = 0,
    /** Anomalías (posibles bugs) detectadas en la partida; el detalle va en `anomalias.csv`. */
    val anomalies: Int = 0,
    /** Censo: cuántas veces pasó cada cosa (`Evento=n;unidad=n`). */
    val census: Map<String, Int> = emptyMap(),
) {
    val won: Boolean get() = outcome == "VICTORIA"
    val chapter: Int get() = LevelMap.chapterIndexForLevel(level)
}

object BotCsv {
    val HEADER = listOf(
        "level", "run", "biome", "rule", "goal", "outcome", "cause", "stars", "moves_used", "moves_total",
        "lines", "placements", "real_ms", "progress", "score", "guaranteed", "two_star", "three_star",
        "fill_avg", "fill_peak", "danger_turns", "mega_at", "trays", "tray_pieces", "tray_small",
        "tray_nofit", "tray_not_together", "anomalies", "census",
    )

    fun header(): String = HEADER.joinToString(",")

    fun row(r: BotResult): String = listOf(
        r.level, r.run, r.biome, r.rule, r.goal, r.outcome, clean(r.cause), r.stars, r.movesUsed, r.movesTotal,
        r.lines, r.placements, r.realMs, clean(r.progress), r.score, r.guaranteed, r.twoStar, r.threeStar,
        "%.3f".format(java.util.Locale.ROOT, r.fillAvg), "%.3f".format(java.util.Locale.ROOT, r.fillPeak),
        r.dangerTurns, r.megaAt, r.trays, r.trayPieces, r.traySmall, r.trayNoFit, r.trayNotTogether, r.anomalies,
        r.census.entries.sortedBy { it.key }.joinToString(";") { "${it.key}=${it.value}" },
    ).joinToString(",")

    private fun clean(s: String) = s.replace(',', ';').replace('\n', ' ')

    /** Lee un `levels.csv` (también los de antes de 2026-09-26: las columnas que falten valen 0). */
    fun read(file: File): List<BotResult> {
        val lines = file.readLines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val cols = lines.first().split(",")
        return lines.drop(1).mapNotNull { line ->
            val v = line.split(",")
            fun s(name: String) = cols.indexOf(name).let { if (it in v.indices) v[it] else "" }
            fun i(name: String) = s(name).toIntOrNull() ?: 0
            fun d(name: String) = s(name).toDoubleOrNull() ?: 0.0
            val level = s("level").toIntOrNull() ?: return@mapNotNull null
            BotResult(
                level, i("run"), s("biome"), s("rule"), s("goal"), s("outcome"), s("cause"), i("stars"),
                i("moves_used"), i("moves_total"), i("lines"), i("placements"), s("real_ms").toLongOrNull() ?: 0L,
                s("progress"), i("score"), i("guaranteed"), i("two_star"), i("three_star"),
                d("fill_avg"), d("fill_peak"), i("danger_turns"), s("mega_at").toIntOrNull() ?: -1,
                i("trays"), i("tray_pieces"), i("tray_small"), i("tray_nofit"), i("tray_not_together"),
                i("anomalies"),
                s("census").split(";").filter { "=" in it }.associate { kv ->
                    kv.substringBefore("=") to (kv.substringAfter("=").toIntOrNull() ?: 0)
                },
            )
        }
    }
}

object BotReport {

    /** Margen (en puntos de victoria) dentro del cual un bioma está «en su sitio». */
    private const val OK_BAND = 5.0

    /**
     * Lo que se cuenta de cada bioma además de su impacto ([BiomeRhythm.phasesFor]): el contraataque y
     * las consecuencias. Los nombres en minúscula son unidades que suma `EngineDriver` (bloques, casillas).
     */
    private val BIOME_CENSUS: Map<BiomeType, List<String>> = mapOf(
        BiomeType.GALACTIC to listOf("MeteorSpotted", "MeteorDeflect", "roca", "metralla", "grieta"),
        BiomeType.STAR to listOf("AstroEmerge", "AstroRetreat", "luna_devuelve"),
        // 2026-09-28: el veneno es una capa — avanza (PoisonSpread), salpica y se lava (PoisonWash).
        BiomeType.TOXIC to listOf("PoisonSpread", "salpicadura", "PoisonWash"),
        BiomeType.NINJA to listOf("NinjaBoardRepair", "oro"),
        BiomeType.GARDEN to listOf("PlantHurt", "raiz_se_lo_lleva", "enredadera"),
        // P-329: el reloj del Rey alterna espada y pieza; la pieza tiene su propio golpe.
        BiomeType.KING to listOf("KingSwordRaise", "KingSwordSheathe", "KingPieceStrike", "KingPieceRaise", "KingPieceRetreat"),
    )

    /** Eventos de siempre (líneas, partículas, sacudidas…): no dicen nada de un bioma. */
    private val GENERIC = setOf(
        "ScreenShake", "Particles", "FloatingScore", "LineClearBeam", "TeikaSeedBurst", "TrayRewardDealt",
        "FusionCharge", "FusionBurst", "PureLine", "TargetCollected", "mega",
    )

    fun write(dir: File, results: List<BotResult>, anomalies: List<String>, base: List<BotResult>? = null) {
        dir.mkdirs()
        File(dir, "levels.csv").writeText(buildString {
            appendLine(BotCsv.header())
            results.forEach { appendLine(BotCsv.row(it)) }
        })
        File(dir, "anomalias.csv").writeText(buildString {
            appendLine(ANOMALY_HEADER)
            anomalies.forEach { appendLine(it) }
        })
        File(dir, "resumen.md").writeText(summary(results, anomalies, base))
        println("Informe: ${dir.absolutePath}")
    }

    const val ANOMALY_HEADER = "level,run,placement,type,detail,seed"

    private fun pct(a: Int, b: Int) = if (b == 0) "-" else "%.0f %%".format(100.0 * a / b)
    private fun winRate(rs: List<BotResult>) = if (rs.isEmpty()) 0.0 else 100.0 * rs.count { it.won } / rs.size

    fun summary(results: List<BotResult>, anomalies: List<String>, base: List<BotResult>?): String = buildString {
        appendLine("# Bot de niveles — revisión")
        appendLine()
        appendLine("${results.size} partidas · ${results.map { it.level }.distinct().size} niveles · " +
            "victoria global ${pct(results.count { it.won }, results.size)}. Jugador que planifica la bandeja entera, " +
            "inclina para juntar Teikas y desatascarse, descarga bobinas y funde gemelas. **Sin ayudas**: no usa " +
            "habilidades de compañeros ni acepta revivir (abajo, cuántas derrotas habría salvado una habilidad).")
        appendLine()
        appendLine("Ruido: con ~40 partidas por capítulo, ±7 puntos es azar. El veredicto usa una banda de ±${OK_BAND.toInt()}.")
        appendLine()

        // ── 1. Veredicto por bioma, en orden de capítulo ──
        appendLine("## Veredicto por bioma (contra la recta 85 % → 55 %)")
        appendLine()
        val baseByBiome = base?.groupBy { it.biome }
        appendLine("| Cap. | Bioma | Partidas | Victoria | Objetivo | Diferencia | Veredicto |" +
            (if (base != null) " Antes |" else "") + " ★ media | Llenado medio / pico | Turnos en peligro | MEGA | Derrotas por causa |")
        appendLine("|---|---|---|---|---|---|---|" + (if (base != null) "---|" else "") + "---|---|---|---|---|")
        results.groupBy { it.biome }.entries.sortedBy { (_, rs) -> rs.minOf { it.chapter } }.forEach { (biome, rs) ->
            val chapter = rs.minOf { it.chapter }
            val win = winRate(rs)
            val target = if (chapter >= 0) ChapterDifficulty.targetWinRate(chapter) else 90.0
            val diff = win - target
            val verdict = when {
                diff > OK_BAND -> "FÁCIL"
                diff < -OK_BAND -> "DIFÍCIL"
                else -> "en su sitio"
            }
            val wins = rs.filter { it.won }
            val causes = rs.filter { !it.won }
                .groupingBy { if (it.cause.isBlank()) it.outcome else "${it.outcome}:${it.cause.take(40)}" }.eachCount()
                .entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${it.value}" }
            val before = baseByBiome?.get(biome)?.let { "%.0f %%".format(winRate(it)) } ?: "-"
            appendLine("| ${chapter + 1} | $biome | ${rs.size} | %.0f %% | %.0f %% | %+.0f | $verdict |".format(win, target, diff) +
                (if (base != null) " $before |" else "") +
                " ${if (wins.isEmpty()) "-" else "%.2f".format(wins.map { it.stars }.average())} |" +
                " %.0f %% / %.0f %% |".format(rs.map { it.fillAvg }.average() * 100, rs.map { it.fillPeak }.average() * 100) +
                " %.1f |".format(rs.map { it.dangerTurns }.average()) +
                " ${pct(rs.count { it.megaAt >= 0 }, rs.size)} | $causes |")
        }
        appendLine()

        // ── 2. Censo de biomas: ¿se activa de verdad cada mecánica? ──
        appendLine("## Censo de biomas (media por partida)")
        appendLine()
        appendLine("Cuántas veces actuó cada bioma. Un bioma fácil con un impacto por partida cercano a 0 no es flojo: es que casi nunca llega a castigar.")
        appendLine()
        appendLine("| Bioma | Impacto | Lo demás |")
        appendLine("|---|---|---|")
        results.groupBy { it.biome }.entries.sortedBy { (_, rs) -> rs.minOf { it.chapter } }.forEach { (biome, rs) ->
            val type = runCatching { BiomeType.valueOf(biome) }.getOrNull() ?: return@forEach
            fun avg(key: String) = rs.sumOf { it.census[key] ?: 0 }.toDouble() / rs.size
            val impact = BiomeRhythm.phasesFor(type)?.impact
            val curated = BIOME_CENSUS[type]
            val extra = curated ?: run {
                // Sin lista propia: los eventos más frecuentes de ese bioma que no sean de siempre.
                rs.flatMap { it.census.keys }.distinct()
                    .filter { it !in GENERIC && it != impact && it.first().isUpperCase() }
                    .sortedByDescending { avg(it) }.take(4)
            }
            appendLine("| $biome | ${impact?.let { "$it %.2f".format(avg(it)) } ?: "-"} | " +
                extra.joinToString(" · ") { "$it %.2f".format(avg(it)) } + " |")
        }
        appendLine()

        // ── 3. Reglas de la casa y tipos de nivel ──
        appendLine("## Por regla de la casa")
        appendLine()
        appendLine("| Regla | Partidas | Victoria |")
        appendLine("|---|---|---|")
        results.groupBy { it.rule }.entries.sortedBy { winRate(it.value) }.forEach { (rule, rs) ->
            appendLine("| $rule | ${rs.size} | %.0f %% |".format(winRate(rs)))
        }
        appendLine()
        appendLine("## Por tipo de nivel")
        appendLine()
        appendLine("| Tipo | Partidas | Victoria | ★ media |")
        appendLine("|---|---|---|---|")
        results.groupBy { runCatching { LevelDirector.identityFor(it.level).kind.name }.getOrDefault("?") }
            .entries.sortedBy { winRate(it.value) }.forEach { (kind, rs) ->
                val wins = rs.filter { it.won }
                appendLine("| $kind | ${rs.size} | %.0f %% | ${if (wins.isEmpty()) "-" else "%.2f".format(wins.map { it.stars }.average())} |".format(winRate(rs)))
            }
        appendLine()

        // ── 4. El reparto ──
        val trays = results.sumOf { it.trays }
        val pieces = results.sumOf { it.trayPieces }
        appendLine("## Reparto")
        appendLine()
        appendLine("$trays bandejas, $pieces fichas. Fichas de ≤ 3 casillas: ${pct(results.sumOf { it.traySmall }, pieces)}. " +
            "Con el tablero ya asentado, antes de la primera jugada: fichas que no cabían ni solas " +
            "${pct(results.sumOf { it.trayNoFit }, pieces)}; bandejas que no cabían JUNTAS " +
            "${pct(results.sumOf { it.trayNotTogether }, trays)}. P-314 lo garantiza al repartir; lo que haya aquí " +
            "lo cambió algo DESPUÉS del reparto (una fusión de Teikas que el reparto no prevé, el bioma) o es un bug.")
        appendLine()

        // ── 5. Niveles extremos ──
        appendLine("## Niveles extremos")
        appendLine()
        val byLevel = results.groupBy { it.level }
        appendLine("**Nunca se ganan:** " + byLevel.filter { (_, rs) -> rs.none { it.won } }.keys.sorted().joinToString(", ").ifEmpty { "ninguno." })
        appendLine()
        appendLine("**Siempre 3★:** " + byLevel.filter { (_, rs) -> rs.all { it.won && it.stars == 3 } }.keys.sorted().joinToString(", ").ifEmpty { "ninguno." })
        appendLine()
        val rescue = results.count { it.cause.contains("solo con habilidad") }
        appendLine("**Derrotas que habría salvado una habilidad:** $rescue de ${results.count { !it.won }}.")
        appendLine()

        // ── 6. Anomalías ──
        appendLine("## Anomalías (posibles bugs)")
        appendLine()
        if (anomalies.isEmpty()) appendLine("Ninguna.") else {
            val byType = anomalies.groupingBy { it.split(",").getOrElse(3) { "?" } }.eachCount()
            appendLine(byType.entries.sortedByDescending { it.value }.joinToString(" · ") { "${it.key} ${it.value}" })
            appendLine()
            appendLine("Detalle en `anomalias.csv` (con la semilla para reproducir: `LEVEL_BOT=<nivel> LEVEL_BOT_TRACE=1`). Primeras 20:")
            appendLine()
            appendLine("```")
            appendLine(ANOMALY_HEADER)
            anomalies.take(20).forEach { appendLine(it) }
            appendLine("```")
        }
    }
}
