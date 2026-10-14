package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de la BANDEJA DE UNA, CON HORIZONTE (P-75). */
class HorizonTrayTest {

    private fun piece(size: Int) = Piece(
        shape = (0 until size).map { Position(it, 0) },
        colorType = 0
    )

    // ── Cuándo se reparte de una en una ────────────────────────────────────────────────────────

    @Test
    fun `lo reparte el catalogo, y ni la intro ni el tutorial`() {
        for (level in 1..LevelMap.totalLevels) {
            val esperado = HouseRule.forLevel(level) == HouseRule.HORIZON
            assertEquals("nivel $level", esperado, HorizonTray.appliesTo(level))
        }
        for (level in 1..LevelMap.introLevels) assertFalse(HorizonTray.appliesTo(level))
    }

    /**
     * **EL DIAL DE CAPÍTULO** (2026-09-03, pedido del usuario: «que cada mecánica se vuelva más
     * difícil según sube de bioma»). El horizonte enseña TRES fichas del futuro en el primer bioma y
     * **una** en el último: la misma regla, pero al final del juego repartir de una en una es de
     * verdad jugar sin saber qué viene. Nunca baja de 1 — sin horizonte esto sería otra mecánica.
     */
    @Test
    fun `el horizonte se acorta de bioma en bioma`() {
        assertEquals(
            HorizonTray.HORIZON_FIRST_CHAPTER,
            HorizonTray.horizonFor(LevelMap.chapterStart.first())
        )
        assertEquals(
            HorizonTray.HORIZON_LAST_CHAPTER,
            HorizonTray.horizonFor(LevelMap.chapterStart.last())
        )
        var previo = Int.MAX_VALUE
        LevelMap.chapterStart.forEach { start ->
            val h = HorizonTray.horizonFor(start)
            assertTrue("el horizonte crece en el capítulo de $start", h <= previo)
            assertTrue("horizonte $h imposible en $start", h >= 1)
            previo = h
        }
    }

    /**
     * Sale lo bastante como para APRENDER a jugarlo. Un nivel por capítulo lo convertiría en una
     * curiosidad que el jugador no llega a dominar nunca.
     *
     * (2026-09-03) El listón era «20 niveles» y ahora se mide contra el **reparto**: con un catálogo
     * de reglas que crece, a cada una le tocan `286 / nº de reglas` niveles, así que un número fijo
     * escrito a mano se quedaría corto en la siguiente familia que se añada. Lo que hay que
     * defender no es la cifra, es que el horizonte reciba **su parte** del reparto y no un resto.
     */
    @Test
    fun `sale lo bastante como para aprenderlo`() {
        val jugables = LevelMap.totalLevels - LevelMap.introLevels
        val suParte = jugables / HouseRule.entries.size
        assertTrue(
            "el horizonte sale ${HorizonTray.levels().size} veces, menos que su parte ($suParte)",
            HorizonTray.levels().size >= suParte
        )
        // (2026-09-04) Aquí se pedía además «al menos una vez por capítulo». Con el catálogo en 28
        // reglas eso ya no puede ser cierto para ninguna —a cada una le tocan ~10 de los 286
        // niveles— y tampoco hace falta: lo que hace aprendible una regla es salir lo bastante en el
        // conjunto del juego, no una cuota por bioma. Se cambia por el mínimo real: que aparezca en
        // varios capítulos distintos, o sería una rareza de un solo bioma.
        val capitulos = HorizonTray.levels().map(LevelMap::chapterIndexForLevel).distinct()
        assertTrue("el horizonte solo sale en ${capitulos.size} capítulos", capitulos.size >= 5)
    }

    /**
     * Ritmo propio: primo distinto del de las gemelas (5) y los testarudos (7), para no coincidir
     * siempre con las mismas mecánicas. Y hoy tampoco coincide con la gravedad (P-80): dos formas
     * distintas de romper las reglas de la casa en el mismo nivel serían ruido.
     */
    @Test
    fun `no cae nunca sobre un nivel con gravedad`() {
        val gravedad = BoardGravity.levels().toSet()
        HorizonTray.levels().forEach {
            assertFalse("nivel $it lleva las dos", it in gravedad)
        }
    }

    // ── La cola ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `avanzar saca la cabeza y mete la nueva por el final`() {
        val cola = listOf(piece(1), piece(2), piece(3))
        val nueva = piece(4)
        val (head, rest) = HorizonTray.advance(cola, listOf(nueva))
        assertEquals(cola[0], head)
        assertEquals(listOf(cola[1], cola[2], nueva), rest)
    }

    /** Lo que se enseña y lo que hay son la MISMA cosa, o el horizonte sería media promesa. */
    @Test
    fun `la cola nunca crece por encima del horizonte`() {
        val cola = List(HorizonTray.HORIZON) { piece(it + 1) }
        val (_, rest) = HorizonTray.advance(cola, listOf(piece(9), piece(9)))
        assertEquals(HorizonTray.HORIZON, rest.size)
    }

    /** Guardado anterior a P-75: sin cola, no hay cabeza — quien llama rellena entera. */
    @Test
    fun `una cola vacia no da cabeza`() {
        val (head, rest) = HorizonTray.advance(emptyList(), listOf(piece(2)))
        assertNull(head)
        assertEquals(1, rest.size)
    }

    // ── La apertura del nivel ──────────────────────────────────────────────────────────────────

    /** El nivel abre con UNA ficha jugable y el horizonte ya lleno: nada que descubrir a ciegas. */
    @Test
    fun `abre con una jugable y el horizonte completo`() {
        HorizonTray.levels().forEach { level ->
            val estado = GameState.initial(level = level)
            assertEquals("nivel $level", HorizonTray.PLAYABLE, estado.availablePieces.size)
            // El horizonte es tan largo como diga el dial de capítulo de la regla (2026-09-03):
            // tres fichas del futuro en el primer bioma, una en el último.
            assertEquals("nivel $level", HorizonTray.horizonFor(level), estado.pieceHorizon.size)
            assertTrue(estado.hasHorizonTray)
            assertEquals(HorizonTray.PLAYABLE, estado.trayCapacity)
        }
    }

    /**
     * **Y el reparto no se degrada por pedir cuatro.** El planificador reserva una colocación
     * distinta para cada ficha; pedirle cuatro en vez de tres podría hacerle caer al plan seguro
     * (todo monominós) en un tablero apretado, y entonces el nivel abriría con cuatro cuadraditos.
     */
    @Test
    fun `la apertura sigue siendo una bandeja de verdad`() {
        HorizonTray.levels().forEach { level ->
            val estado = GameState.initial(level = level)
            val tamanos = (estado.availablePieces + estado.pieceHorizon).map { it.shape.size }
            assertTrue(
                "nivel $level reparte $tamanos",
                tamanos.count { it == 1 } <= 1 && tamanos.any { it >= 3 }
            )
        }
    }

    /** Los niveles normales siguen exactamente igual: tres fichas y sin cola. */
    @Test
    fun `un nivel normal no tiene horizonte`() {
        val normal = (LevelMap.introLevels + 1..LevelMap.totalLevels)
            .first { !HorizonTray.appliesTo(it) && !TutorialCampaign.isTutorialLevel(it) }
        val estado = GameState.initial(level = normal)
        assertEquals(3, estado.availablePieces.size)
        assertTrue(estado.pieceHorizon.isEmpty())
        assertFalse(estado.hasHorizonTray)
    }

    // ── La cabeza se comprueba al salir (2026-09-25) ───────────────────────────────────────────

    /** Quien tiene jugada se queda con su ficha: la guarda no regala nada, ni pide otra. */
    @Test
    fun `la ficha que cabe no se cambia`() {
        val cabeza = piece(5)
        var pedidas = 0
        val elegida = HorizonTray.guardHead(cabeza, fits = { true }, candidate = { pedidas++; piece(1) })
        assertTrue(elegida === cabeza)
        assertEquals(0, pedidas)
    }

    /** La que no cabe se cambia por la primera nueva que sí: el L47 moría así a las 3 colocaciones. */
    @Test
    fun `la ficha sin sitio se cambia por la primera que cabe`() {
        val cabeza = piece(5)
        val nuevas = ArrayDeque(listOf(piece(4), piece(3), piece(2)))
        val elegida = HorizonTray.guardHead(
            cabeza, fits = { it.shape.size <= 3 }, candidate = { nuevas.removeFirst() })
        assertEquals(3, elegida.shape.size)
        assertEquals(1, nuevas.size)
    }

    /** Con el tablero lleno de verdad no hay rescate: se queda la que había y es derrota. */
    @Test
    fun `si nada cabe se queda la que habia`() {
        val cabeza = piece(5)
        var pedidas = 0
        val elegida = HorizonTray.guardHead(cabeza, fits = { false }, candidate = { pedidas++; piece(1) })
        assertTrue(elegida === cabeza)
        assertEquals(HorizonTray.HEAD_SWAP_ATTEMPTS, pedidas)
        assertTrue(HorizonTray.guardHead(cabeza, fits = { false }, candidate = { null }) === cabeza)
    }
}
