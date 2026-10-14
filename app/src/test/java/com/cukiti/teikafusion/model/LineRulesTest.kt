package com.cukiti.teikafusion.model

import com.cukiti.teikafusion.viewmodel.TeikaPhysicsWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **FAMILIA C DE LAS REGLAS DE LA CASA** (2026-09-04): las cuatro que tocan `resolveLineClearPass`.
 *
 * Es la familia de más riesgo del catálogo, porque el resolutor de líneas es el **único motor vivo**
 * del juego: por ahí pasan los catorce biomas, las habilidades y las otras tres familias. Lo que
 * estas pruebas defienden no es cada regla suelta, sino las dos disciplinas que las hacen seguras:
 *
 *  1. **Ninguna infla el progreso.** El estallido de los barriles limpia, pero **no cuenta como
 *     línea**: no crían Teika, no puntúan y no avanzan el objetivo. Si alguna llegara a
 *     contar, el MEGA —que impone el suelo de 16 líneas de todo el mapa— saldría a mitad de precio
 *     sin que nadie lo hubiera decidido.
 *  2. **Ninguna toca el presupuesto de turnos.** Es la curva medida en el playtest (2026-08-01b) y
 *     no algo que una mecánica pueda inflar por la puerta de atrás — la primera versión de
 *     «un solo eje» lo intentó y por eso se rediseñó (y acabó retirada; ver [LineRecoil]).
 */
class LineRulesTest {

    private val primero = LevelMap.chapterStart.first()
    private val ultimo = LevelMap.chapterStart.last()

    private fun levelWith(rule: HouseRule): Int =
        HouseRule.levelsWith(rule).firstOrNull()
            ?: throw AssertionError("${rule.name} no le toca a ningún nivel")

    private fun levelWith(rule: HouseRule, chapter: Int): Int? =
        HouseRule.levelsWith(rule).firstOrNull { LevelMap.chapterIndexForLevel(it) == chapter }

    // ── P-234 · REACCIÓN EN CADENA ─────────────────────────────────────────────────────────────

    @Test fun `retired barrels are never dealt in any campaign level`() {
        (1..LevelMap.totalLevels).forEach { level ->
            assertFalse(ChainReaction.appliesTo(level))
            assertFalse(ChainReaction.dealsOnRefill(10, level))
        }
    }

    /** El barril va en la ficha más grande, en UNA casilla, y nunca en herramientas. */
    @Test
    fun `el barril se pone en una casilla de la ficha mas grande`() {
        val chica = Piece(shape = listOf(Position(0, 0), Position(1, 0)))
        val grande = Piece(shape = listOf(Position(0, 0), Position(1, 0), Position(2, 0), Position(1, 1)))
        val iman = Piece(shape = (0..4).map { Position(it, 0) }, isMagnet = true)
        val marcadas = ChainReaction.markBarrel(listOf(chica, grande, iman))
        assertTrue(marcadas[0].barrelMask.none { it })
        assertEquals(1, marcadas[1].barrelMask.count { it })
        assertTrue(marcadas[2].barrelMask.none { it })
        // Y no añade un segundo barril si ya hay uno.
        assertEquals(marcadas, ChainReaction.markBarrel(marcadas))
    }

    /** Un barril se lleva su 3×3 (recortado al tablero). */
    @Test
    fun `el barril estalla en tres por tres`() {
        assertEquals(9, ChainReaction.blastArea(Position(4, 4)).size)
        assertEquals(4, ChainReaction.blastArea(Position(0, 0)).size)
    }

    /**
     * **LA CADENA, por oleadas.** Un barril alcanzado por otro estalla en la oleada siguiente; uno
     * fuera del alcance no estalla; y un barril nunca estalla dos veces.
     */
    @Test
    fun `la reaccion en cadena sigue a los barriles alcanzados`() {
        val a = Position(1, 1); val b = Position(2, 2); val c = Position(3, 3); val lejos = Position(7, 7)
        val chain = ChainReaction.resolve(setOf(a), setOf(a, b, c, lejos))
        assertEquals(listOf(setOf(a), setOf(b), setOf(c)), chain.waves)
        assertFalse(lejos in chain.area)
        assertTrue(Position(4, 4) in chain.area)
        assertTrue(ChainReaction.resolve(emptySet(), setOf(a)).waves.isEmpty())
    }

    /** El barril viaja con la ficha si la ficha gira (la que coloca el juego, Cristal o pistas). */
    @Test
    fun `el barril sigue a su casilla cuando la ficha gira`() {
        val piece = Piece(
            shape = listOf(Position(0, 0), Position(1, 0), Position(2, 0)),
            barrelMask = listOf(false, false, true),
        )
        val girada = piece.rotate()
        assertEquals(1, girada.barrelMask.count { it })
        val barril = girada.shape[girada.barrelMask.indexOf(true)]
        // La casilla (2,0) de una fila de tres, girada 90°, cae en el extremo de la columna.
        assertTrue(barril == Position(0, 2) || barril == Position(0, 0))
    }

    // ── P-235 · EL HUEVO ───────────────────────────────────────────────────────────────────────

    @Test
    fun `el huevo pide mas grietas al final y siempre da un T1`() {
        // (2026-09-26) Nace T1 en todo el juego: solo una jugada de 2+ líneas cría Teikas grandes.
        levelWith(HouseRule.ELEVATOR, 0)?.let {
            assertEquals(TeikaEgg.CRACKS_FIRST_CHAPTER, TeikaEgg.cracksFor(it))
            assertEquals(TeikaType.TEIKA_1, TeikaEgg.hatchTypeFor(it))
        }
        levelWith(HouseRule.ELEVATOR, LevelMap.chapters.size - 1)?.let {
            assertEquals(TeikaEgg.CRACKS_LAST_CHAPTER, TeikaEgg.cracksFor(it))
            assertEquals(TeikaType.TEIKA_1, TeikaEgg.hatchTypeFor(it))
        }
    }

    @Test fun `Ascensor retira el huevo sin ocupar la plataforma`() {
        HouseRule.levelsWith(HouseRule.ELEVATOR).forEach { level ->
            val state=GameState.initial(level)
            assertNull(state.eggCell)
            assertFalse(TeikaEgg.appliesTo(level))
            assertNull(TeikaEgg.cellFor(level,0,emptySet()))
            assertFalse(ElevatorRules.platform(level) in state.unplaceableCells)
        }
    }

    @Test
    fun `una linea que cruza el huevo lo agrieta`() {
        val egg = Position(3, 3)
        assertTrue(TeikaEgg.crossedBy(egg, (0 until 8).map { Position(it, 3) }.toSet()))
        assertFalse(TeikaEgg.crossedBy(egg, (0 until 8).map { Position(it, 4) }.toSet()))
        assertFalse(TeikaEgg.crossedBy(null, setOf(egg)))
    }

    // ── P-236 · LA IMPLOSIÓN ───────────────────────────────────────────────────────────────────

    @Test
    fun `la implosion pide mas lineas a la vez al final`() {
        levelWith(HouseRule.IMPLOSION, 0)?.let {
            assertEquals(LineImplosion.MIN_LINES_FIRST_CHAPTER, LineImplosion.minLinesFor(it))
            assertTrue(LineImplosion.triggers(it, 1))
        }
        levelWith(HouseRule.IMPLOSION, LevelMap.chapters.size - 1)?.let {
            assertEquals(LineImplosion.MIN_LINES_LAST_CHAPTER, LineImplosion.minLinesFor(it))
            assertFalse(LineImplosion.triggers(it, 1))
            assertTrue(LineImplosion.triggers(it, 2))
        }
        assertFalse(LineImplosion.triggers(levelWith(HouseRule.GRAVITY), 4))
    }

    /** Un paso hacia la línea; arriba tiene prioridad; una casilla recibe un solo bloque. */
    @Test
    fun `la fila rota aspira un paso a sus vecinas`() {
        val arriba = Position(2, 3)
        val abajo = Position(2, 5)
        val soloAbajo = Position(5, 5)
        val pulls = LineImplosion.pulls(
            rows = setOf(4), cols = emptySet(),
            wood = setOf(arriba, abajo, soloAbajo), blocked = emptySet(),
        )
        assertTrue(LineImplosion.Pull(arriba, Position(2, 4)) in pulls)
        assertTrue(LineImplosion.Pull(soloAbajo, Position(5, 4)) in pulls)
        assertFalse("dos bloques en la misma casilla", pulls.any { it.from == abajo })
        assertEquals(pulls.size, pulls.map { it.to }.distinct().size)
    }

    /** Lo anclado no se mueve, y a una casilla ocupada no se entra. */
    @Test
    fun `la implosion respeta anclas y casillas ocupadas`() {
        val ancla = Position(1, 3)
        val ocupada = Position(3, 4)
        val pulls = LineImplosion.pulls(
            rows = setOf(4), cols = emptySet(),
            wood = setOf(ancla, Position(3, 3), ocupada), blocked = setOf(ancla),
        )
        assertTrue(pulls.none { it.from == ancla })
        assertTrue(pulls.none { it.to == ocupada })
    }

    /** Una columna aspira de izquierda y derecha, con la izquierda primero. */
    @Test
    fun `la columna rota aspira en horizontal`() {
        val pulls = LineImplosion.pulls(
            rows = emptySet(), cols = setOf(4),
            wood = setOf(Position(3, 0), Position(5, 0), Position(5, 2)), blocked = emptySet(),
        )
        assertTrue(LineImplosion.Pull(Position(3, 0), Position(4, 0)) in pulls)
        assertTrue(LineImplosion.Pull(Position(5, 2), Position(4, 2)) in pulls)
        assertTrue(pulls.none { it.from == Position(5, 0) })
    }

    // ── P-237 · EL EMPUJÓN, FUENTE ÚNICA ───────────────────────────────────────────────────────

    /** La cuenta pura del empujón: hacia fuera, cae con la distancia, y los grandes se mueven menos. */
    @Test
    fun `el impulso del rebote sale hacia fuera y cae con la distancia`() {
        assertTrue(LineRecoil.impulseFor(center = 2.5f, radius = 0.5f, lineCoord = 4.5f, speed = 10f) < 0f)
        assertTrue(LineRecoil.impulseFor(center = 6.5f, radius = 0.5f, lineCoord = 4.5f, speed = 10f) > 0f)
        val cerca = kotlin.math.abs(LineRecoil.impulseFor(3.5f, 0.5f, 4.5f, 10f))
        val lejos = kotlin.math.abs(LineRecoil.impulseFor(7.5f, 0.5f, 4.5f, 10f))
        assertTrue(cerca > lejos)
        val chico = kotlin.math.abs(LineRecoil.impulseFor(3.5f, 0.5f, 4.5f, 10f))
        val grande = kotlin.math.abs(LineRecoil.impulseFor(3.5f, 1.5f, 4.5f, 10f))
        assertTrue("el grande se mueve lo mismo que el chico", grande < chico)
        assertEquals(0f, LineRecoil.impulseFor(0.5f, 0.5f, 7.5f, 10f), 0.0001f)
        assertEquals(0f, LineRecoil.impulseFor(3.5f, 0.5f, 4.5f, 0f), 0.0001f)
    }

    /** La física empuja exactamente lo que dice la cuenta pura — lo que dibuja la flecha del aviso. */
    @Test
    fun `la fisica empuja lo mismo que promete el aviso`() {
        val world = TeikaPhysicsWorld()
        val body = world.addBody(TeikaType.TEIKA_1, 4f, 2.5f)
        val antes = world.getBody(body.id)!!.vy
        val pushes = ArrayList<GameEvent.RecoilPush.Push>()
        world.recoilFromLine(alongX = true, lineCoord = 4.5f, speed = 10f, pushesOut = pushes)
        val esperado = LineRecoil.impulseFor(2.5f, 0.5f, 4.5f, 10f)
        assertEquals(antes + esperado, world.getBody(body.id)!!.vy, 0.0001f)
        assertEquals(1, pushes.size)
        assertEquals(-1f, pushes.first().dirY, 0.0001f)
    }

    // ── EL REBOTE (2026-09-05, sustituye a UN SOLO EJE) ────────────────────────────────────────

    /**
     * **FUERA DE SUS NIVELES, CERO — y ese cero es la regla.**
     *
     * El resolutor no pregunta «¿es un nivel de rebote?»: pide siempre la velocidad y no empuja si
     * vale cero. Si [LineRecoil.speedFor] devolviera algo distinto de cero en un nivel cualquiera,
     * los 293 niveles del juego empezarían a mover los cuerpos al romper línea sin que nadie lo haya
     * pedido — por eso este es el primer test de la regla y no una comprobación de trámite.
     */
    @Test fun `retired recoil never adds velocity in any campaign level`() {
        (1..LevelMap.totalLevels).forEach { level ->
            assertFalse(LineRecoil.appliesTo(level))
            assertEquals(0f, LineRecoil.speedFor(level), .0001f)
        }
    }

    /**
     * **NO DESPIERTA A NADIE.** El rebote respeta los cuerpos `static`: el congelado del Glaciar
     * sigue congelado y el raptado por la planta sigue raptado. Es la frontera que separa una regla
     * de la casa de un `biomeSecondary` disfrazado, y por eso se afirma sobre el mundo físico de
     * verdad y no sobre la regla pura.
     */
    @Test
    fun `el rebote no mueve cuerpos congelados ni raptados`() {
        val world = TeikaPhysicsWorld()
        val libre = world.addBody(TeikaType.TEIKA_1, 4f, 2f)
        val congelado = world.addBody(TeikaType.TEIKA_1, 2f, 2f)
        val raptado = world.addBody(TeikaType.TEIKA_1, 6f, 2f)
        world.setFrozenTurns(congelado.id, 3)
        world.setKinematic(raptado.id, true)

        val movidos = world.recoilFromLine(alongX = true, lineCoord = 4.5f, speed = 10f)

        assertEquals("solo debería haberse movido el cuerpo libre", 1, movidos)
        assertEquals(0f, world.getBody(congelado.id)!!.vy, 0.0001f)
        assertEquals(0f, world.getBody(raptado.id)!!.vy, 0.0001f)
        assertTrue("el cuerpo libre no ha salido despedido", world.getBody(libre.id)!!.vy != 0f)
    }

    /**
     * **EMPUJA HACIA FUERA, Y CAE CON LA DISTANCIA.** Las dos mitades importan: la dirección es lo
     * que convierte la regla en una herramienta (eliges qué fila rompes según dónde quieras que
     * acaben los cuerpos), y la caída es lo que impide que romper una fila baraje el tablero entero
     * y esa elección deje de significar nada.
     */
    @Test
    fun `el rebote empuja hacia fuera y pierde fuerza con la distancia`() {
        val world = TeikaPhysicsWorld()
        val arriba = world.addBody(TeikaType.TEIKA_1, 4f, 2.5f)
        val abajo = world.addBody(TeikaType.TEIKA_1, 4f, 6.5f)
        world.recoilFromLine(alongX = true, lineCoord = 4.5f, speed = 10f)
        assertTrue("el de arriba debería salir HACIA ARRIBA", world.getBody(arriba.id)!!.vy < 0f)
        assertTrue("el de abajo debería salir HACIA ABAJO", world.getBody(abajo.id)!!.vy > 0f)

        val otro = TeikaPhysicsWorld()
        val cerca = otro.addBody(TeikaType.TEIKA_1, 4f, 3.5f)
        val lejos = otro.addBody(TeikaType.TEIKA_1, 6f, 7.5f)
        otro.recoilFromLine(alongX = true, lineCoord = 4.5f, speed = 10f)
        val vCerca = kotlin.math.abs(otro.getBody(cerca.id)!!.vy)
        val vLejos = kotlin.math.abs(otro.getBody(lejos.id)!!.vy)
        assertTrue("el cuerpo cercano debería recibir más impulso", vCerca > vLejos)
    }

    /** Y una columna empuja a los lados, no arriba y abajo. */
    @Test
    fun `una columna rota empuja en horizontal`() {
        val world = TeikaPhysicsWorld()
        val izq = world.addBody(TeikaType.TEIKA_1, 2.5f, 4f)
        val der = world.addBody(TeikaType.TEIKA_1, 6.5f, 4f)
        world.recoilFromLine(alongX = false, lineCoord = 4.5f, speed = 10f)
        assertTrue(world.getBody(izq.id)!!.vx < 0f)
        assertTrue(world.getBody(der.id)!!.vx > 0f)
        assertEquals("una columna no debe empujar en vertical", 0f, world.getBody(izq.id)!!.vy, 0.0001f)
    }

    // ── La disciplina de la familia ────────────────────────────────────────────────────────────

    /**
     * **NINGUNA REGLA DE LA FAMILIA C TOCA EL PRESUPUESTO DE TURNOS.**
     *
     * Es la curva medida en el playtest (2026-08-01b, `MOVES_AT_START` → `MOVES_AT_END`) y una
     * decisión congelada del usuario. La primera versión de «un solo eje» le sumaba 10 turnos para
     * compensar que el eje muerto no limpiara; los tests del presupuesto la tumbaron el mismo día y
     * la regla se rediseñó para no necesitarlo. Este test es el que impide que vuelva a colarse.
     */
    @Test
    fun `la familia C no infla el presupuesto de turnos`() {
        val familia = setOf(
            HouseRule.SHOCKWAVE, HouseRule.ELEVATOR, HouseRule.IMPLOSION, HouseRule.AVALANCHE
        )
        val jugables = (LevelMap.introLevels + 1 until LevelMap.totalLevels).toList()
        val salto = { level: Int ->
            kotlin.math.abs(LevelBudget.movesForLevel(level) - LevelBudget.movesForLevel(level + 1))
        }
        // El listón se CALIBRA con el propio mapa: el presupuesto ya varía de un nivel a otro porque
        // depende de la figura de salida, así que un número escrito a mano solo mediría esa varianza.
        // Lo que este test defiende es que las cuatro reglas no añadan un escalón MAYOR del que el
        // juego ya tiene sin ellas — que es exactamente lo que hacía la primera versión del eje
        // único, con sus +10 turnos.
        val maxSinFamilia = jugables
            .filter { HouseRule.forLevel(it) !in familia && HouseRule.forLevel(it + 1) !in familia }
            .maxOfOrNull(salto) ?: 0
        jugables.filter { HouseRule.forLevel(it) in familia }.forEach { level ->
            assertTrue(
                "el nivel $level da un salto de ${salto(level)} turnos, mayor que el máximo del " +
                    "mapa sin la familia C ($maxSinFamilia)",
                salto(level) <= maxSinFamilia
            )
        }
    }
}
