package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La mini-partida del Inventario (2026-09-30): cada habilidad tiene su guion, se construye sin
 * romper y el tablero CAMBIA —la demo vieja pintaba madera quieta y ese era justo el fallo—.
 */
class AbilityDemoScriptTest {

    @Test fun `cada habilidad tiene guion`() {
        for (id in COMPANION_ABILITIES.keys) assertNotNull("sin guion: $id", abilityDemoScript(id))
        assertEquals(COMPANION_ABILITIES.keys, ABILITY_DEMO_SCRIPTS.keys)
    }

    private val levels = 1..COMPANION_ABILITY_MAX_LEVEL

    @Test fun `en cada guion y nivel el tablero cambia`() {
        for (id in ABILITY_DEMO_SCRIPTS.keys) for (lv in levels) {
            val tl = abilityDemoScript(id, lv)!!
            val first = tl.frames.first().tiles.toSet()
            val last = tl.frames.last().tiles.toSet()
            assertNotEquals("$id nv$lv: el tablero acaba igual que empieza", first, last)
            assertTrue("$id nv$lv: guion demasiado corto o largo (${tl.totalMs} ms)", tl.totalMs in 2_000..13_000)
        }
    }

    /** P-383: la demo sigue el nivel. Timoteo es la excepción declarada (sus pasadas no se cuentan). */
    @Test fun `la demo cambia con el nivel`() {
        for (id in ABILITY_DEMO_SCRIPTS.keys - "miedoso") {
            val l1 = abilityDemoScript(id, 1)!!.frames
            val l3 = abilityDemoScript(id, 3)!!.frames
            val l5 = abilityDemoScript(id, 5)!!.frames
            assertNotEquals("$id: el nivel 3 enseña lo mismo que el 1", l1, l3)
            assertNotEquals("$id: el nivel 5 enseña lo mismo que el 3", l3, l5)
        }
    }

    @Test fun `las fichas cuadradas nunca se pisan en reposo`() {
        for (id in ABILITY_DEMO_SCRIPTS.keys) for (lv in levels) {
            val tl = abilityDemoScript(id, lv)!!
            tl.frames.forEachIndexed { i, f ->
                val cells = f.tiles
                    .filter { !it.kind.round && it.size == 1f && it.y >= 0f && it.y <= 7f }
                    .map { it.x to it.y }
                assertEquals("$id nv$lv, foto $i: dos fichas en la misma casilla", cells.size, cells.toSet().size)
            }
        }
    }

    /** P-386: con eco el poder se juega dos veces; sin eco, una. */
    @Test fun `el eco repite la demo`() {
        for (id in ABILITY_DEMO_SCRIPTS.keys) for (lv in levels) {
            val once = ABILITY_DEMO_SCRIPTS.getValue(id)(lv).frames.size
            val shown = abilityDemoScript(id, lv)!!.frames
            val echo = AbilityScaling.echoChance(id, lv) > 0f
            assertEquals("$id nv$lv", if (echo) once * 2 else once, shown.size)
            assertEquals("$id nv$lv", if (echo) 1 else 0, shown.count { DemoFx.Echo in it.fx })
        }
    }

    /** P-389: una pasada con eco de cada 4 en el nivel 4 (25 %) y de cada 2 en el 5 (50 %); la primera. */
    @Test fun `el eco sale con su probabilidad`() {
        for (id in ABILITY_DEMO_SCRIPTS.keys) for (lv in levels) {
            val reel = abilityDemoReel(id, lv)!!
            val echoed = reel.passes.map { tl -> tl.frames.any { DemoFx.Echo in it.fx } }
            val expected = when (AbilityScaling.echoChance(id, lv)) {
                0.25f -> listOf(true, false, false, false)
                0.50f -> listOf(true, false)
                else -> listOf(false)
            }
            assertEquals("$id nv$lv", expected, echoed)
        }
    }

    @Test fun `frameAt salta los cortes secos`() {
        val tl = DemoTimeline(listOf(
            DemoFrame(emptyList(), 100),
            DemoFrame(emptyList(), 0),
            DemoFrame(emptyList(), 200),
        ))
        assertEquals(0, tl.frameAt(50))
        assertEquals(2, tl.frameAt(100))
        assertEquals(2, tl.frameAt(299))
    }
}
