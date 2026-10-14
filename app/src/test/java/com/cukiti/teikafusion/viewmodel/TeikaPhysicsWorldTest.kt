package com.cukiti.teikafusion.viewmodel

import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.model.TeikaType
import com.cukiti.teikafusion.model.TutorialCampaign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Invariantes del mundo físico de Teikas ([TeikaPhysicsWorld]) — la mitad Suika del motor.
 *
 * Fijan por test los contratos que el resto del juego asume sin comprobar:
 *  1. El tablero es un CONTENEDOR CERRADO: un cuerpo jamás sale de él, ni con la inclinación
 *     premium a tope (los comodines/bloqueos de colocación se derivan de estas posiciones).
 *  2. El REPORTE de contacto para fusión: una pareja igual EN REPOSO apoyada (solape 0) sigue
 *     reportándose gracias a [TeikaPhysicsWorld.CONTACT_REPORT_EPS] — si esto se rompe, una
 *     pareja puede quedar "tocándose para siempre" sin fusionar.
 *  3. La GRACIA post-nacimiento suprime el reporte (se ve el pop antes de fusionar), y
 *     [TeikaPhysicsWorld.load] la re-otorga al restaurar una partida (no se serializa): sin
 *     eso, la fusión disparaba en el primer frame tras cargar.
 *  4. Congelados (Glaciar): ni se mueven ni se reportan.
 *  5. Anti-atasco: un cuerpo cuyo centro queda dentro de una celda sólida se expulsa.
 *
 * Los pasos usan dt de frame a 60 Hz (el mundo trocea en substeps de FIXED_DT por su cuenta),
 * y relojes [nowMs] explícitos para que la gracia sea determinista.
 */
class TeikaPhysicsWorldTest {

    private val frameDt = 1f / 60f

    /** Avanza [frames] frames devolviendo la unión de todos los contactos reportados. */
    private fun TeikaPhysicsWorld.run(frames: Int, nowMs: Long): Set<Pair<String, String>> {
        val all = mutableSetOf<Pair<String, String>>()
        repeat(frames) { all += step(frameDt, nowMs) }
        return all
    }

    private fun assertNoBodyOverlap(bodies: List<TeikaBody>) {
        for (i in bodies.indices) for (j in i + 1 until bodies.size) {
            val a = bodies[i]
            val b = bodies[j]
            val dx = b.x - a.x
            val dy = b.y - a.y
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            assertTrue(
                "${a.id} y ${a.y} sigue incrustado en ${b.id} y ${b.y}",
                distance + 0.02f >= a.radius + b.radius
            )
        }
    }

    /** La escalera de L3 debe terminar en contacto real sin teletransportar ni forzar la pareja. */
    @Test
    fun tutorialStaircaseCarriesTheMovingT1IntoTheGoal() {
        val spec = TutorialCampaign.spec(3)!!
        val world = TeikaPhysicsWorld()
        world.setTerrain(spec.initialCells.map { Position(it.x, it.y) }.toSet())
        spec.initialTeikas.forEach { seed ->
            world.addBody(seed.type, seed.x, seed.y, id = seed.id, nowMs = 0L)
        }
        world.setHorizontalGravity(-TeikaPhysicsWorld.TILT_GRAVITY_MAX)

        val contacts = mutableSetOf<Pair<String, String>>()
        var nowMs = 1_000L
        repeat(1_200) {
            contacts += world.step(frameDt, nowMs)
            nowMs += 17L
        }

        assertTrue(
            "el T1 móvil debe recorrer los descansos hasta tocar el objetivo; cuerpos=${world.snapshot()}",
            ("tutorial_3_t1_goal" to "tutorial_3_t1_mover") in contacts
        )
    }

    // ── 1. Contenedor cerrado ─────────────────────────────────────────────────────

    @Test
    fun bodyNeverLeavesClosedContainerEvenAtMaxTilt() {
        val world = TeikaPhysicsWorld()
        val body = world.addBody(TeikaType.TEIKA_1, 4f, 1f, nowMs = 0L)
        world.setHorizontalGravity(TeikaPhysicsWorld.TILT_GRAVITY_MAX)
        repeat(600) {   // 10 s de caída + rodado contra la esquina inferior derecha
            world.step(frameDt, 1_000L)
            val b = world.snapshot().single()
            assertTrue("x fuera del tablero: ${b.x}", b.x in body.radius..(8f - body.radius))
            assertTrue("y fuera del tablero: ${b.y}", b.y in body.radius..(8f - body.radius))
        }
    }

    @Test
    fun playableBoundsOfKingSiegeConfineTheBody() {
        val world = TeikaPhysicsWorld()
        world.addBody(TeikaType.TEIKA_1, 0.5f, 0.5f, nowMs = 0L)
        // Asedio del Rey con profundidad 1: interior jugable [1..7] × [1..7].
        world.setPlayableBounds(left = 1f, top = 1f, right = 7f, bottom = 7f)
        world.run(120, nowMs = 1_000L)
        val b = world.snapshot().single()
        assertTrue("x dentro del muro: ${b.x}", b.x in (1f + b.radius)..(7f - b.radius))
        assertTrue("y dentro del muro: ${b.y}", b.y in (1f + b.radius)..(7f - b.radius))
    }

    // ── 2. Reporte de contacto para fusión ────────────────────────────────────────

    @Test
    fun restingEqualPairKeepsBeingReportedAfterGrace() {
        val world = TeikaPhysicsWorld()
        // Dos T1 tangentes (distancia exacta = suma de radios) apoyados en el suelo.
        world.addBody(TeikaType.TEIKA_1, 3.5f, 7.5f, id = "a", nowMs = 0L)
        world.addBody(TeikaType.TEIKA_1, 4.5f, 7.5f, id = "b", nowMs = 0L)
        val contacts = world.run(10, nowMs = 1_000L)   // gracia (300 ms) ya vencida
        assertTrue("la pareja en reposo debe reportarse", ("a" to "b") in contacts)
    }

    @Test
    fun differentTypesInContactAreNeverReported() {
        val world = TeikaPhysicsWorld()
        world.addBody(TeikaType.TEIKA_1, 3.5f, 7.5f, id = "a", nowMs = 0L)
        world.addBody(TeikaType.TEIKA_2, 5f, 7f, id = "b", nowMs = 0L)
        val contacts = world.run(30, nowMs = 1_000L)
        assertEquals(emptySet<Pair<String, String>>(), contacts)
    }

    // ── 3. Gracia post-nacimiento y post-carga ────────────────────────────────────

    @Test
    fun fusionGraceSuppressesContactReport() {
        val world = TeikaPhysicsWorld()
        world.addBody(TeikaType.TEIKA_1, 3.5f, 7.5f, id = "a", nowMs = 1_000L)
        world.addBody(TeikaType.TEIKA_1, 4.5f, 7.5f, id = "b", nowMs = 1_000L)
        // Dentro de la gracia (hasta 1300 ms): silencio.
        assertEquals(emptySet<Pair<String, String>>(), world.run(5, nowMs = 1_100L))
        // Pasada la gracia: la MISMA pareja se reporta (nada se perdió, solo se pospuso).
        assertTrue(("a" to "b") in world.run(5, nowMs = 2_000L))
    }

    @Test
    fun loadGrantsFusionGraceToRestoredBodies() {
        val world = TeikaPhysicsWorld()
        world.load(
            listOf(
                TeikaBody("a", TeikaType.TEIKA_1, 3.5f, 7.5f),
                TeikaBody("b", TeikaType.TEIKA_1, 4.5f, 7.5f)
            ),
            nowMs = 1_000L
        )
        // Frame 0 tras cargar: la pareja NO fusiona con el tablero aún apareciendo.
        assertEquals(emptySet<Pair<String, String>>(), world.run(5, nowMs = 1_050L))
        // La fusión legítima solo quedó pospuesta ~300 ms.
        assertTrue(("a" to "b") in world.run(5, nowMs = 2_000L))
    }

    // ── 4. Congelados (Glaciar) ───────────────────────────────────────────────────

    @Test
    fun frozenBodyNeitherFallsNorReportsFusion() {
        val world = TeikaPhysicsWorld()
        world.addBody(TeikaType.TEIKA_1, 3.5f, 3.5f, id = "ice", nowMs = 0L)
        world.setFrozenTurns("ice", 3)
        world.addBody(TeikaType.TEIKA_1, 4.5f, 3.5f, id = "warm", nowMs = 0L)
        val contacts = world.run(30, nowMs = 1_000L)
        val ice = world.snapshot().first { it.id == "ice" }
        assertEquals("el congelado no cae", 3.5f, ice.y, 1e-4f)
        assertFalse("el congelado no fusiona", contacts.any { "ice" in listOf(it.first, it.second) })
    }

    // ── 5. Anti-atasco contra terreno ─────────────────────────────────────────────

    @Test
    fun bodyEmbeddedInTerrainIsEjected() {
        val world = TeikaPhysicsWorld()
        // Madera estampada justo donde duerme el cuerpo (centro DENTRO de la celda sólida).
        world.setTerrain(setOf(Position(4, 4)))
        world.addBody(TeikaType.TEIKA_1, 4.5f, 4.5f, nowMs = 0L)
        world.run(90, nowMs = 1_000L)   // 1.5 s: la expulsión es racionada (EJECT_RATE), no un salto
        val b = world.snapshot().single()
        val centerCell = Position(kotlin.math.floor(b.x).toInt(), kotlin.math.floor(b.y).toInt())
        assertFalse("el centro debe salir de la celda sólida", centerCell == Position(4, 4))
        assertTrue(b.x in b.radius..(8f - b.radius) && b.y in b.radius..(8f - b.radius))
    }

    // ── Cuña cuerpo↔terreno: la geometría abre inmediatamente el hueco necesario ──

    @Test
    fun breakableTerrainStuckUnderABodyIsReportedOnTheFirstFrame() {
        // Reproduce teikabloque.jpeg: madera (con estrella) DEBAJO del círculo de un T3 apoyado en
        // el suelo. La expulsión normal empuja hacia el suelo (cuña sin salida) → el solape residual
        // demuestra que no cabe y reporta la celda sin esperar un umbral de presión.
        val world = TeikaPhysicsWorld()
        world.setTerrain(setOf(Position(4, 5)))
        world.addBody(TeikaType.TEIKA_3, 4.5f, 6.5f, nowMs = 0L)   // r=1.5, pisa (4,5) con pen 1.0
        world.step(frameDt, 1_000L)
        assertTrue(
            "la celda encajada debe reportarse en el primer frame",
            world.drainForcedTerrainBreaks().any { it.position == Position(4, 5) }
        )
    }

    @Test
    fun bodyRestingOnTopOfWoodNeverBreaksIt() {
        // El contacto de REPOSO se corrige entero cada substep → pen≈0. Si esto rompe, el suelo
        // de madera se desharía solo por sostener un Teika.
        val world = TeikaPhysicsWorld()
        world.setTerrain(setOf(Position(4, 7)))
        world.addBody(TeikaType.TEIKA_1, 4.5f, 6f, nowMs = 0L)   // cae y se posa sobre (4,7)
        world.run(180, nowMs = 1_000L)   // 3 s de reposo
        assertTrue(world.drainForcedTerrainBreaks().isEmpty())
    }

    @Test
    fun bodyPushedByAnotherBreaksTheBlockingCellWithoutPressureDelay() {
        val world = TeikaPhysicsWorld()
        val block = Position(5, 7)
        world.setTerrain(setOf(block), breakableCells = setOf(block))
        // La pared izquierda + el bloque dejan menos de dos diámetros: el cuerpo derecho necesita
        // la celda para que la pareja pueda separarse.
        world.setPlayableBounds(left = 3.2f, top = 0f, right = 8f, bottom = 8f)
        world.addBody(TeikaType.TEIKA_1, 3.7f, 7.5f, id = "pusher", nowMs = 0L)
        world.addBody(TeikaType.TEIKA_1, 4.5f, 7.5f, id = "pushed", nowMs = 0L)

        world.step(frameDt, 1_000L)

        val impact = world.drainForcedTerrainBreaks().single { it.position == block }
        assertTrue("el empuje debe dirigirse hacia el bloque", impact.pushX > 0.8f)

        // El ViewModel retirará el bloque al procesar el impacto. Hasta entonces, el latch impide
        // que los frames siguientes dupliquen objetivo, sonido o partículas para la misma celda.
        world.run(30, nowMs = 1_500L)
        assertTrue("una celda ya reportada no debe emitir otro evento", world.drainForcedTerrainBreaks().isEmpty())
    }

    @Test
    fun nearbyWoodDoesNotBreakWhenThePusherCanMoveAway() {
        val world = TeikaPhysicsWorld()
        val block = Position(5, 7)
        world.setTerrain(setOf(block), breakableCells = setOf(block))
        // Misma colisión inicial, pero sin pared a la izquierda: el solver puede apartar al primer
        // cuerpo y resolver la pareja sin pedirle espacio al bloque de la derecha.
        world.addBody(TeikaType.TEIKA_1, 3.7f, 7.5f, id = "free", nowMs = 0L)
        world.addBody(TeikaType.TEIKA_1, 4.5f, 7.5f, id = "nearWood", nowMs = 0L)

        world.step(frameDt, 1_000L)

        assertTrue("madera cercana no equivale a geometría irresoluble", world.drainForcedTerrainBreaks().isEmpty())
    }

    @Test
    fun hardTerrainNeverEntersTheForcedBreakQueue() {
        val world = TeikaPhysicsWorld()
        val hardBlock = Position(5, 7)
        world.setTerrain(setOf(hardBlock), breakableCells = emptySet())
        world.setPlayableBounds(left = 3.2f, top = 0f, right = 8f, bottom = 8f)
        world.addBody(TeikaType.TEIKA_1, 3.7f, 7.5f, id = "pusher", nowMs = 0L)
        world.addBody(TeikaType.TEIKA_1, 4.5f, 7.5f, id = "pushed", nowMs = 0L)

        world.run(120, nowMs = 1_000L)

        assertTrue(world.drainForcedTerrainBreaks().isEmpty())
    }

    // ── Reacción en cadena cuerpo↔cuerpo ─────────────────────────────────────────

    @Test
    fun threeDifferentTiersSeparateAsOneContactChain() {
        val world = TeikaPhysicsWorld()
        // T3 abajo, T2 incrustado en su zona superior y T1 encima: reproduce buggordo.jpeg.
        world.addBody(TeikaType.TEIKA_3, 4f, 6.5f, id = "t3", nowMs = 0L)
        world.addBody(TeikaType.TEIKA_2, 4f, 5.2f, id = "t2", nowMs = 0L)
        world.addBody(TeikaType.TEIKA_1, 4f, 4f, id = "t1", nowMs = 0L)

        world.run(240, nowMs = 2_000L)
        assertNoBodyOverlap(world.snapshot())
    }

    @Test
    fun chainResolutionIsIndependentOfInsertionOrder() {
        fun build(order: List<Triple<TeikaType, Float, String>>): TeikaPhysicsWorld {
            val world = TeikaPhysicsWorld()
            order.forEach { (type, y, id) ->
                world.addBody(type, 4f, y, id = id, nowMs = 0L)
            }
            world.run(180, nowMs = 2_000L)
            return world
        }

        val first = build(
            listOf(
                Triple(TeikaType.TEIKA_3, 6.5f, "t3"),
                Triple(TeikaType.TEIKA_2, 5.2f, "t2"),
                Triple(TeikaType.TEIKA_1, 4f, "t1")
            )
        ).snapshot().associateBy { it.id }
        val reversed = build(
            listOf(
                Triple(TeikaType.TEIKA_1, 4f, "t1"),
                Triple(TeikaType.TEIKA_2, 5.2f, "t2"),
                Triple(TeikaType.TEIKA_3, 6.5f, "t3")
            )
        ).snapshot().associateBy { it.id }

        first.keys.forEach { id ->
            assertEquals("x de $id depende del orden", first.getValue(id).x, reversed.getValue(id).x, 1e-3f)
            assertEquals("y de $id depende del orden", first.getValue(id).y, reversed.getValue(id).y, 1e-3f)
        }
    }

    @Test
    fun denseUnequalClusterSeparatesAfterTheFinalProjection() {
        val world = TeikaPhysicsWorld()
        // Disposición irregular inspirada en buggordo1: cuerpos de tamaños distintos nacen
        // comprimidos y deben transmitirse el empuje hasta liberar al pequeño.
        world.addBody(TeikaType.TEIKA_4, 2.9f, 5.8f, id = "t4", nowMs = 0L)
        world.addBody(TeikaType.TEIKA_3, 4.8f, 5.7f, id = "t3", nowMs = 0L)
        world.addBody(TeikaType.TEIKA_2, 5.0f, 4.0f, id = "t2", nowMs = 0L)
        world.addBody(TeikaType.TEIKA_1, 3.9f, 4.0f, id = "t1", nowMs = 0L)

        world.run(360, nowMs = 2_000L)
        assertNoBodyOverlap(world.snapshot())
    }

    @Test
    fun fusionResultStaysAtTheContactOriginEvenWhenOccupied() {
        val world = TeikaPhysicsWorld()
        world.addBody(TeikaType.TEIKA_4, 4f, 4f, id = "large", nowMs = 0L)

        val born = world.addFusionBody(
            TeikaType.TEIKA_3,
            x = 4f,
            y = 4f,
            id = "result",
            nowMs = 1_000L
        )

        assertEquals("la fusión no debe buscar un hueco remoto", 4f, born.x, 0f)
        assertEquals("la fusión no debe buscar un hueco remoto", 4f, born.y, 0f)
    }

    @Test
    fun fusionResultPushesNearbyBodiesAwayFromItsOrigin() {
        val world = TeikaPhysicsWorld()
        world.addBody(TeikaType.TEIKA_1, 5.2f, 4f, id = "neighbor", nowMs = 0L)

        world.addFusionBody(
            TeikaType.TEIKA_2,
            x = 4f,
            y = 4f,
            id = "result",
            nowMs = 1_000L
        )

        assertTrue(
            "el vecino a la derecha debe recibir impulso radial",
            world.getBody("neighbor")!!.vx > 0f
        )
    }

    @Test
    fun fusionOriginIsClampedOnlyByTheBoardBoundary() {
        val world = TeikaPhysicsWorld()
        val born = world.addFusionBody(
            TeikaType.TEIKA_4,
            x = 0f,
            y = 8f,
            id = "result",
            nowMs = 1_000L
        )

        assertEquals(2f, born.x, 0f)
        assertEquals(6f, born.y, 0f)
    }

    // ── La presión ya no elimina cuerpos ──────────────────────────────────────────
    // La presión de fronteras o cuerpos ya no elimina Teikas.

    @Test
    fun squeezedBodySurvivesIndefinitely() {
        val world = TeikaPhysicsWorld()
        world.addBody(TeikaType.TEIKA_2, 4f, 3.5f, id = "t2", nowMs = 0L)
        world.setPlayableBounds(left = 0f, top = 3f, right = 8f, bottom = 4f)

        world.run(600, nowMs = 1_000L)

        assertTrue("la presión no debe borrar el cuerpo", world.snapshot().any { it.id == "t2" })
    }

    // ── Corte Ninja: muro central vertical ────────────────────────────────────────

    @Test
    fun centerWallConfinesEachBodyToItsHalf() {
        val world = TeikaPhysicsWorld()
        world.addBody(TeikaType.TEIKA_1, 1.5f, 7.5f, id = "izq", nowMs = 0L)
        world.addBody(TeikaType.TEIKA_1, 6.5f, 7.5f, id = "der", nowMs = 0L)
        world.setCenterWall(4f)
        // Inclinación a tope hacia la derecha: el de la izquierda rueda contra el muro y NO cruza.
        world.setHorizontalGravity(TeikaPhysicsWorld.TILT_GRAVITY_MAX)
        repeat(600) {
            world.step(frameDt, 1_000L)
            val izq = world.snapshot().first { it.id == "izq" }
            assertTrue("izq cruzó el muro: x=${izq.x}", izq.x <= 4f - izq.radius + 1e-3f)
        }
        // Y hacia la izquierda: el de la derecha tampoco cruza.
        world.setHorizontalGravity(-TeikaPhysicsWorld.TILT_GRAVITY_MAX)
        repeat(600) {
            world.step(frameDt, 1_000L)
            val der = world.snapshot().first { it.id == "der" }
            assertTrue("der cruzó el muro: x=${der.x}", der.x >= 4f + der.radius - 1e-3f)
        }
    }

    @Test
    fun raisingCenterWallPushesStraddlingBodyIntoOneHalf() {
        val world = TeikaPhysicsWorld()
        // Cuerpo pisando la frontera (centro casi en x=4, del lado izquierdo).
        world.addBody(TeikaType.TEIKA_2, 3.9f, 7f, nowMs = 0L)
        world.setCenterWall(4f)
        world.run(30, nowMs = 1_000L)
        val b = world.snapshot().single()
        assertTrue("el cuerpo debe quedar ENTERO en su mitad: x=${b.x}", b.x <= 4f - b.radius + 1e-3f)
    }

    @Test
    fun removingCenterWallLetsBodiesCrossAgain() {
        val world = TeikaPhysicsWorld()
        world.addBody(TeikaType.TEIKA_1, 1.5f, 7.5f, id = "a", nowMs = 0L)
        world.setCenterWall(4f)
        world.setHorizontalGravity(TeikaPhysicsWorld.TILT_GRAVITY_MAX)
        world.run(240, nowMs = 1_000L)
        assertTrue(world.snapshot().single().x <= 4f - 0.5f + 1e-3f)
        // Reparación: el muro cae y el cuerpo rueda al otro lado.
        world.setCenterWall(null)
        world.run(600, nowMs = 2_000L)
        assertTrue("sin muro debe poder cruzar", world.snapshot().single().x > 4f)
    }

    // ── Cobertura de celdas (bloqueo de colocación / comodín) ─────────────────────

    @Test
    fun occupiedCellsMatchTheCirclesRealFootprint() {
        // T1 centrado en la celda (3,3): pisa exactamente esa celda.
        val t1 = listOf(TeikaBody("t1", TeikaType.TEIKA_1, 3.5f, 3.5f))
        assertEquals(setOf(Position(3, 3)), TeikaPhysicsWorld.occupiedCellsOf(t1).keys)
        // T2 (diámetro 2) centrado en el cruce (4,4): pisa el cuadrado 2×2 que lo rodea.
        val t2 = listOf(TeikaBody("t2", TeikaType.TEIKA_2, 4f, 4f))
        assertEquals(
            setOf(Position(3, 3), Position(4, 3), Position(3, 4), Position(4, 4)),
            TeikaPhysicsWorld.occupiedCellsOf(t2).keys
        )
    }
}
