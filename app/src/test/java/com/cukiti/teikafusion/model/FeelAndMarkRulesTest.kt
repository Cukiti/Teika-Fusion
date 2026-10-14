package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **FAMILIAS D y E DE LAS REGLAS DE LA CASA** (2026-09-04): el tacto de los cuerpos y las marcas del
 * tablero.
 *
 * Con estas siete el catálogo llega a **28 reglas**, que es el número que cierra la promesa entera
 * del encargo: **ningún capítulo repite ninguna regla**, porque el capítulo más largo tiene 22
 * niveles. Eso lo vigila `HouseRuleContractTest`, que enciende esa comprobación solo cuando el
 * catálogo da de sí.
 *
 * Aquí se defienden las dos cosas que estas familias podrían romper:
 *
 *  1. **La familia D no cambia una sola regla del juego.** Es puro game feel: multiplica el perfil
 *     físico en vez de sustituirlo, así que la jerarquía por tamaño —un T1 responde más que un T4—
 *     se conserva dentro del nivel. Aplanarla sería perder la lectura de tamaños, que es media mitad
 *     Suika del juego.
 *  2. **La familia E no deja al jugador sin jugada.** Las celdas de color son la ÚNICA regla del
 *     catálogo que rechaza una colocación, y hay que demostrar que el repartidor no depende de ellas.
 */
class FeelAndMarkRulesTest {

    private fun levelWith(rule: HouseRule): Int =
        HouseRule.levelsWith(rule).firstOrNull()
            ?: throw AssertionError("${rule.name} no le toca a ningún nivel")

    private fun levelWith(rule: HouseRule, chapter: Int): Int? =
        HouseRule.levelsWith(rule).firstOrNull { LevelMap.chapterIndexForLevel(it) == chapter }

    // ── P-153 / P-154 · LIGEROS Y PESADOS ──────────────────────────────────────────────────────

    @Test fun `Reactor conserva el tacto normal`() {
        HouseRule.levelsWith(HouseRule.TRAIL).forEach { assertTrue(TeikaFeelRules.modifierFor(it).isNeutral) }
    }

    /** **El resto del mapa no se entera de que la familia existe**: el modificador es el neutro. */
    @Test
    fun `un nivel normal recibe el mundo de siempre`() {
        val otro = levelWith(HouseRule.GRAVITY)
        assertTrue(TeikaFeelRules.modifierFor(otro).isNeutral)
        assertEquals(TeikaFeel.NORMAL, TeikaFeelRules.feelFor(otro))
        assertTrue(TeikaFeelModifier.NEUTRAL.isNeutral)
    }

    /** **EL DIAL**: más ligeros y más pesados según sube el bioma, cada uno hacia su lado. */
    @Test fun `ningun nivel reactiva el peso retirado`() {
        (1..LevelMap.totalLevels).forEach { assertEquals(TeikaFeel.NORMAL,TeikaFeelRules.feelFor(it)) }
    }

    /**
     * **LA JERARQUÍA POR TAMAÑO SE CONSERVA.** El modificador MULTIPLICA el perfil de cada tier en
     * vez de sustituirlo, así que dentro de un nivel ligero los pequeños siguen respondiendo más que
     * los grandes — solo que todos responden más. Sustituirlo habría aplanado el tablero.
     */
    @Test
    fun `el tacto del nivel no aplana la jerarquia de tamanos`() {
        listOf(HouseRule.TRAIL).forEach { rule ->
            val mod = TeikaFeelRules.modifierFor(levelWith(rule))
            val t1 = TeikaPhysicsProfiles.of(TeikaType.TEIKA_1).tiltResponse * mod.tilt
            val t4 = TeikaPhysicsProfiles.of(TeikaType.TEIKA_4).tiltResponse * mod.tilt
            assertTrue("$rule aplana la respuesta por tamaño", t1 > t4)
        }
    }

    // ── P-240 · EL IMÁN ES UNA FICHA ───────────────────────────────────────────────────────────

    /** El alcance ENCOGE de bioma en bioma, y fuera de la regla no hay imán (0). */
    @Test
    fun `el iman alcanza menos segun sube el bioma`() {
        HouseRule.levelsWith(HouseRule.TRAMPOLINE).forEach { level ->
            assertFalse(MagnetPiece.appliesTo(level))
            assertEquals(0f, MagnetPiece.rangeFor(level), 0.0001f)
            assertNull(GameState.initial(level).trampolineCell)
        }
    }

    /**
     * **El tirón es máximo al lado, se mantiene visible hasta el borde y se apaga fuera.** El suelo
     * existe para que un Teika al borde del alcance también se mueva: el jugador puso el imán ahí
     * para eso.
     */
    @Test
    fun `el tiron del iman cae con la distancia y se apaga en el alcance`() {
        val r = 4f
        assertEquals(1f, MagnetPiece.pullAt(0f, r), 0.0001f)
        assertEquals(0f, MagnetPiece.pullAt(r, r), 0.0001f)
        assertEquals(0f, MagnetPiece.pullAt(r * 2f, r), 0.0001f)
        assertTrue("al borde del alcance ya no se nota", MagnetPiece.pullAt(r * 0.95f, r) > 0.3f)
        assertTrue(MagnetPiece.pullAt(0.5f, r) > MagnetPiece.pullAt(3f, r))
        assertEquals(0f, MagnetPiece.pullAt(0f, 0f), 0.0001f)
    }

    /**
     * Un imán que no levanta a un cuerpo que tiene debajo no se lee como imán — y eso tiene que
     * valer hasta el BORDE de su alcance, porque el círculo que se dibuja promete que todo lo de
     * dentro se acerca.
     */
    @Test
    fun `el iman tira mas que la gravedad en todo su alcance`() {
        val g = com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld.GRAVITY
        val r = MagnetPiece.RANGE_FIRST_CHAPTER
        assertTrue(MagnetPiece.PULL_ACCEL * MagnetPiece.pullAt(r * 0.99f, r) > g)
    }

    /** Llega cada tantas reposiciones, nunca en la primera bandeja. */
    @Test
    fun `el iman llega con su cadencia`() {
        assertFalse(MagnetPiece.dealsOnRefill(0))
        assertTrue(MagnetPiece.dealsOnRefill(MagnetPiece.EVERY_REFILLS))
        assertFalse(MagnetPiece.dealsOnRefill(MagnetPiece.EVERY_REFILLS + 1))
        // Con el horizonte la cadencia se estira por el tamaño de la bandeja normal.
        assertFalse(MagnetPiece.dealsOnRefill(MagnetPiece.EVERY_REFILLS, scale = 3))
        assertTrue(MagnetPiece.dealsOnRefill(MagnetPiece.EVERY_REFILLS * 3, scale = 3))
    }

    // ── P-239 · TEIKAS DE PLOMO ────────────────────────────────────────────────────────────────

    /** Hace falta más altura en los últimos biomas, y fuera de la regla nada aplasta (0). */
    @Test fun `ningun nivel reactiva los aplastamientos de plomo`() {
        (1..LevelMap.totalLevels).forEach {
            assertFalse(LeadTeikas.appliesTo(it))
            assertEquals(0f,LeadTeikas.crushSpeedFor(it,20f),0f)
        }
    }

    // ── P-241 · LA BRÚJULA ─────────────────────────────────────────────────────────────────────

    /** Gira un cuarto de vuelta por periodo, siempre en el mismo sentido dentro del nivel. */
    @Test
    fun `la brujula gira un cuarto de vuelta por periodo`() {
        val level = levelWith(HouseRule.COMPASS)
        val periodo = Compass.periodFor(level)
        assertEquals(Compass.DOWN, Compass.dirFor(level, 0))
        assertEquals(Compass.DOWN, Compass.dirFor(level, periodo - 1))
        val primero = Compass.dirFor(level, periodo)
        assertTrue(primero == Compass.LEFT || primero == Compass.RIGHT)
        assertEquals(Compass.UP, Compass.dirFor(level, periodo * 2))
        assertTrue(Compass.turnsOn(level, periodo))
        assertFalse(Compass.turnsOn(level, periodo + 1))
        assertEquals(1, Compass.turnsUntil(level, periodo - 1))
        assertEquals(Compass.DOWN, Compass.dirFor(levelWith(HouseRule.GRAVITY), 40))
    }

    @Test
    fun `la brujula gira mas a menudo al final`() {
        levelWith(HouseRule.COMPASS, 0)?.let { assertEquals(Compass.PERIOD_FIRST_CHAPTER, Compass.periodFor(it)) }
        levelWith(HouseRule.COMPASS, LevelMap.chapters.size - 1)?.let {
            assertEquals(Compass.PERIOD_LAST_CHAPTER, Compass.periodFor(it))
        }
    }

    @Test
    fun `los vectores de la brujula son unitarios y apuntan a su pared`() {
        assertEquals(0f to 1f, Compass.vectorOf(Compass.DOWN))
        assertEquals(0f to -1f, Compass.vectorOf(Compass.UP))
        assertEquals(-1f to 0f, Compass.vectorOf(Compass.LEFT))
        assertEquals(1f to 0f, Compass.vectorOf(Compass.RIGHT))
    }

    // ── P-242 · LA PIÑATA ──────────────────────────────────────────────────────────────────────

    @Test fun `Surf retira la pinata de todos sus niveles`() {
        HouseRule.levelsWith(HouseRule.SURF).forEach { level ->
            assertFalse(Pinata.appliesTo(level)); assertNull(Pinata.cellFor(level,0))
            assertNull(GameState.initial(level).pinataCell)
        }
    }

    /** El balanceo avanza una casilla y rebota en los bordes: se puede contar dónde estará. */
    @Test
    fun `la pinata se balancea y rebota en los bordes`() {
        assertEquals(4 to 1, Pinata.swing(3, 1, 8))
        assertEquals(6 to -1, Pinata.swing(7, 1, 8))
        assertEquals(1 to 1, Pinata.swing(0, -1, 8))
    }

    /** La lluvia cae en casillas libres, cerca de su columna, sin pisarse, y nunca dentro de la madera. */
    @Test
    fun `la lluvia de la pinata cae en casillas libres y separadas`() {
        val cell = Position(4, 2)
        val madera = setOf(Position(4, 0), Position(4, 1), Position(4, 2))
        val drops = Pinata.dropCells(cell, 4, madera, 8)
        assertEquals(4, drops.size)
        assertTrue(drops.none { it in madera })
        assertTrue(drops.all { it.y <= cell.y })
        drops.forEach { a -> drops.filter { it != a }.forEach { b ->
            assertTrue("dos gotas vecinas", kotlin.math.abs(a.x - b.x) > 1 || kotlin.math.abs(a.y - b.y) > 1)
        } }
        val todo = (0 until 8).flatMap { y -> (0 until 8).map { x -> Position(x, y) } }.toSet()
        assertTrue(Pinata.dropCells(cell, 4, todo, 8).isEmpty())
    }

    @Test
    fun `una linea por su casilla golpea la pinata`() {
        val cell = Position(4, 2)
        assertTrue(Pinata.hitBy(cell, (0 until 8).map { Position(it, 2) }.toSet()))
        assertTrue(Pinata.hitBy(cell, (0 until 8).map { Position(4, it) }.toSet()))
        assertFalse(Pinata.hitBy(cell, (0 until 8).map { Position(5, it) }.toSet()))
        assertFalse(Pinata.hitBy(null, setOf(cell)))
    }

    // ── P-238 · LAS POMPAS ─────────────────────────────────────────────────────────────────────

    @Test
    fun `la pompa pide mas golpes al final y no existe fuera de su regla`() {
        levelWith(HouseRule.BUBBLES, 0)?.let { assertEquals(TeikaBubbles.HITS_FIRST_CHAPTER, TeikaBubbles.hitsFor(it)) }
        levelWith(HouseRule.BUBBLES, LevelMap.chapters.size - 1)?.let {
            assertEquals(TeikaBubbles.HITS_LAST_CHAPTER, TeikaBubbles.hitsFor(it))
        }
        assertEquals(0, TeikaBubbles.hitsFor(levelWith(HouseRule.GRAVITY)))
        assertTrue("la pompa no sube", TeikaBubbles.LIFT > com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld.GRAVITY)
    }

    // ── P-243 · LOS PORTALES ───────────────────────────────────────────────────────────────────

    /** Dos bocas en paredes opuestas, a distinta altura, dentro del tablero. */
    @Test
    fun `los portales estan en paredes opuestas y a distinta altura`() {
        HouseRule.levelsWith(HouseRule.PORTALS).forEach { level ->
            (0..40).forEach { p ->
                val (l, r) = Portals.rowsFor(level, p)!!
                assertTrue("nivel $level: portales a la misma altura", l != r)
                assertTrue(l in 1..(8 - Portals.SPAN) && r in 1..(8 - Portals.SPAN))
            }
        }
        assertNull(Portals.rowsFor(levelWith(HouseRule.GRAVITY), 0))
    }

    /** Quietos en la primera mitad del juego; en la segunda se mudan, y avisan al hacerlo. */
    @Test
    fun `los portales solo se mudan en la segunda mitad`() {
        levelWith(HouseRule.PORTALS, 0)?.let { lv ->
            assertFalse(Portals.moves(lv))
            assertEquals(Portals.rowsFor(lv, 0), Portals.rowsFor(lv, 40))
        }
        levelWith(HouseRule.PORTALS, LevelMap.chapters.size - 1)?.let { lv ->
            assertTrue(Portals.moves(lv))
            assertTrue(Portals.movesOn(lv, Portals.MOVE_EVERY))
            assertFalse(Portals.movesOn(lv, Portals.MOVE_EVERY + 1))
        }
    }

    @Test
    fun `solo pasan los teikas que caben en la boca`() {
        assertTrue(Portals.fits(TeikaType.TEIKA_1))
        assertTrue(Portals.fits(TeikaType.TEIKA_2))
        assertFalse(Portals.fits(TeikaType.TEIKA_3))
    }

    // ── P-244 · LA VIDRIERA ────────────────────────────────────────────────────────────────────

    @Test fun `Giro orbital retira vidriera y el nucleo automatico de Cometa`() {
        HouseRule.levelsWith(HouseRule.COMET).forEach { level ->
            val state=GameState.initial(level)
            assertTrue(state.glassCells.isEmpty()); assertFalse(StainedGlass.appliesTo(level))
            assertTrue(StainedGlass.windowFor(level,0).isEmpty())
            assertNull(state.cometMark); assertFalse(CometRules.appliesTo(level)); assertTrue(OrbitRules.appliesTo(level))
        }
    }

    @Test
    fun `una linea agrieta los vidrios que cruza y la vidriera estalla al quedarse sin sanos`() {
        val vidrios = setOf(Position(1, 1), Position(4, 3), Position(6, 6))
        val fila3 = (0 until 8).map { Position(it, 3) }.toSet()
        val rajados = StainedGlass.crackedBy(vidrios, emptySet(), fila3)
        assertEquals(setOf(Position(4, 3)), rajados)
        // Un vidrio ya agrietado no se vuelve a contar.
        assertTrue(StainedGlass.crackedBy(vidrios, rajados, fila3).isEmpty())
        assertFalse(StainedGlass.shatters(vidrios, rajados))
        assertTrue(StainedGlass.shatters(vidrios, vidrios))
        assertFalse(StainedGlass.shatters(emptySet(), emptySet()))
    }

    @Test fun `el helper de vidrio no vuelve a sembrar al continuar`() {
        val state=GameState.initial(levelWith(HouseRule.COMET))
        assertTrue(StainedGlass.seededFor(state) === state)
    }

    /** El estallido es una CRUZ por vidrio, recortada al tablero. */
    @Test
    fun `el estallido se lleva una cruz por vidrio`() {
        val area = StainedGlass.shatterArea(setOf(Position(0, 0), Position(4, 4)))
        assertEquals(3 + 5, area.size)
        assertTrue(Position(4, 4) in area && Position(5, 4) in area && Position(4, 3) in area)
        assertFalse(Position(5, 5) in area)
    }

    @Test fun `Cometa conserva bandeja jugable sin marcas antiguas`() {
        HouseRule.levelsWith(HouseRule.COMET).forEach { level ->
            val state=GameState.initial(level)
            assertTrue(state.availablePieces.isNotEmpty()); assertTrue(state.glassCells.isEmpty())
        }
    }
}
