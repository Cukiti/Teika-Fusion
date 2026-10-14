package com.cukiti.teikafusion.bot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * El informe del bot se junta LEYENDO los `levels.csv` de cada capítulo: si una columna se escribe y no se
 * lee igual, la campaña entera mide mal sin que nadie lo note. Corre en la suite normal (sin Robolectric).
 */
class BotReportTest {

    private val sample = BotResult(
        level = 293, run = 2, biome = "GALACTIC", rule = "INVERTED_GRAVITY", goal = "STARS",
        outcome = "DERROTA", cause = "SIN_ESPACIO(inclinar, no basta)", stars = 0,
        movesUsed = 0, movesTotal = 0, lines = 12, placements = 31, realMs = 9_000,
        progress = "STARS:3/28", score = 4_000, guaranteed = 9_200, twoStar = 8_280, threeStar = 13_340,
        fillAvg = 0.512, fillPeak = 0.84, dangerTurns = 6, megaAt = -1,
        trays = 11, trayPieces = 33, traySmall = 2, trayNoFit = 1, trayNotTogether = 1, anomalies = 0,
        census = mapOf("MeteorImpact" to 2, "metralla" to 4, "roca" to 3),
    )

    @Test
    fun `una fila escrita se lee igual`() {
        val f = File.createTempFile("levels", ".csv").apply { deleteOnExit() }
        f.writeText(BotCsv.header() + "\n" + BotCsv.row(sample) + "\n")
        val back = BotCsv.read(f).single()
        // La causa pierde sus comas (el CSV no las admite): es lo único que cambia.
        assertEquals(sample.copy(cause = sample.cause.replace(',', ';')), back)
    }

    @Test
    fun `un csv viejo sin las columnas nuevas se lee con ceros`() {
        val f = File.createTempFile("viejo", ".csv").apply { deleteOnExit() }
        f.writeText("level,run,biome,rule,goal,outcome,cause,stars\n8,1,CRYSTAL,MOLD,STARS,VICTORIA,,3\n")
        val r = BotCsv.read(f).single()
        assertEquals(8, r.level)
        assertTrue(r.won)
        assertEquals(-1, r.megaAt)
        assertTrue(r.census.isEmpty())
    }

    @Test
    fun `el resumen da el veredicto contra el objetivo`() {
        val facil = (1..10).map { sample.copy(run = it, outcome = "VICTORIA", stars = 3) }
        val md = BotReport.summary(facil, emptyList(), base = null)
        assertTrue(md.contains("| GALACTIC | 10 | 100 %"))
        assertTrue(md.contains("FÁCIL"))
    }
}
