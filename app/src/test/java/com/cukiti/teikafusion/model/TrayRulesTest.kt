package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **FAMILIA B DE LAS REGLAS DE LA CASA** (2026-09-03): las siete que cambian el repartidor.
 *
 * Lo que estas pruebas defienden, por encima de cada regla suelta, es **la frontera que ninguna
 * cruza**: la bandeja siempre admite jugada. Es la lección que costó los 14 niveles del damero
 * (P-53) y la que sostiene toda la familia — una regla que reparte de otra manera puede quitar
 * elección, esconder información o cambiar el ritmo, pero nunca dejar al jugador sin salida.
 *
 * Y la segunda mitad del encargo: cada regla tiene un **dial de capítulo** que se mueve, y se mueve
 * en la dirección que su KDoc promete. Un dial que se calcula y no cambia nada es la trampa que este
 * proyecto ya conoce (`idleDelta`, P-118/P-119).
 */
class TrayRulesTest {

    private val primero = LevelMap.chapterStart.first()
    private val ultimo = LevelMap.chapterStart.last()

    private fun levelWith(rule: HouseRule): Int =
        HouseRule.levelsWith(rule).firstOrNull()
            ?: throw AssertionError("${rule.name} no le toca a ningún nivel")

    /** El nivel del capítulo [chapter] que lleva [rule], o `null` si a ese capítulo no le toca. */
    private fun levelWith(rule: HouseRule, chapter: Int): Int? =
        HouseRule.levelsWith(rule).firstOrNull { LevelMap.chapterIndexForLevel(it) == chapter }

    // ── P-142 · LA BANDEJA DE CINCO ────────────────────────────────────────────────────────────

    @Test
    fun `la bandeja ampliada esta retirada en toda la campana`() {
        (1..293).forEach { level -> assertFalse(TrayFive.appliesTo(level)); assertNull(TrayFive.slotsFor(level)) }
    }

    /** Fuera de sus niveles no existe: `slotsFor` devuelve `null` y la bandeja se queda en tres. */
    @Test
    fun `la bandeja ampliada no se cuela en los demas niveles`() {
        assertNull(TrayFive.slotsFor(levelWith(HouseRule.GRAVITY)))
        assertNull(TrayFive.slotsFor(1))
    }

    /** Y el estado lo refleja: `trayCapacity` es la fuente única de cuántos cuadros hay. */
    @Test
    fun `trayCapacity sigue a la regla del nivel`() {
        HouseRule.levelsWith(HouseRule.RESERVE).take(3).forEach { level ->
            assertEquals("nivel $level", 3, GameState.initial(level).trayCapacity)
        }
        HouseRule.levelsWith(HouseRule.HORIZON).take(2).forEach { level ->
            assertEquals("nivel $level", HorizonTray.PLAYABLE, GameState.initial(level).trayCapacity)
        }
    }

    /**
     * **Y la bandeja de cinco se reparte ENTERA.** El planificador reserva una colocación distinta
     * por ficha; pedirle cinco en vez de tres podría hacerle caer al plan seguro (todo monominós) en
     * un tablero apretado, y entonces la regla que promete más opciones daría cinco cuadraditos.
     */
    @Test
    fun `un nivel de cinco abre con sus cinco fichas`() {
        HouseRule.levelsWith(HouseRule.RESERVE).forEach { level ->
            val estado = GameState.initial(level)
            assertEquals("nivel $level", estado.trayCapacity, estado.availablePieces.size)
            assertTrue(
                "nivel $level abre con una bandeja de monominós",
                estado.availablePieces.any { it.shape.size >= 3 }
            )
        }
    }

    // ── P-143 · LA COLA FORZADA ────────────────────────────────────────────────────────────────

    @Test
    fun `la cola deja dos libres al principio y una al final`() {
        assertEquals(ForcedQueue.FREE_FIRST_CHAPTER, ForcedQueue.freeSlotsFor(primero))
        assertEquals(ForcedQueue.FREE_LAST_CHAPTER, ForcedQueue.freeSlotsFor(ultimo))
    }

    @Test
    fun `la cola bloquea las de atras y nunca la cabeza`() {
        val level = levelWith(HouseRule.FORCED_QUEUE)
        // La cabeza SIEMPRE se puede jugar: es lo que garantiza que la regla no deje sin salida.
        assertTrue(ForcedQueue.allows(level, 0))
        val libres = ForcedQueue.freeSlotsFor(level)
        assertFalse(ForcedQueue.allows(level, libres))
    }

    /**
     * Lo que cuenta como SALIDA al juzgar la derrota es lo mismo que el jugador puede jugar: la
     * cabeza de la cola, no la bandeja entera (bug del bot de niveles, 2026-09-22 — con la cabeza
     * sin sitio y la segunda con sitio, no había jugada ni derrota).
     */
    @Test
    fun `la salida que se juzga es la que la cola deja jugar`() {
        val level = levelWith(HouseRule.FORCED_QUEUE)
        val bandeja = listOf("cabeza", "segunda", "tercera")
        val jugables = ForcedQueue.playable(level, bandeja)
        assertEquals(bandeja.take(ForcedQueue.freeSlotsFor(level)), jugables)
        assertTrue("la cabeza siempre cuenta", "cabeza" in jugables)
        assertFalse("la última nunca cuenta con la cola activa", "tercera" in jugables)
        bandeja.indices.forEach { i -> assertEquals(ForcedQueue.allows(level, i), bandeja[i] in jugables) }
        // Fuera de sus niveles cuenta todo.
        assertEquals(bandeja, ForcedQueue.playable(levelWith(HouseRule.GRAVITY), bandeja))
    }

    /** Fuera de sus niveles no bloquea nada, ni siquiera la última de una bandeja de cinco. */
    @Test
    fun `la cola no toca los demas niveles`() {
        val otro = levelWith(HouseRule.GRAVITY)
        (0..4).forEach { assertTrue(ForcedQueue.allows(otro, it)) }
    }

    // ── P-145 · LA FICHA TAPADA ────────────────────────────────────────────────────────────────

    @Test
    fun `ninguna ficha llega tapada al sustituirla por rutas`() {
        (1..293).forEach { level ->
            assertFalse(FaceDownTray.appliesTo(level)); assertEquals(0,FaceDownTray.hiddenFor(level))
            (0..2).forEach { i -> assertFalse(FaceDownTray.isHidden(level,i,3,3)) }
        }
    }

    @Test
    fun `no se tapa nada fuera de sus niveles`() {
        val otro = levelWith(HouseRule.GRAVITY)
        assertFalse(FaceDownTray.isHidden(otro, index = 2, trayCount = 3, remaining = 3))
        assertEquals(0, FaceDownTray.hiddenFor(otro))
    }

    // ── P-144 · LA REPOSICIÓN INSTANTÁNEA ──────────────────────────────────────────────────────

    /** Y el estado lo anuncia, que es de donde el motor decide si repone cada turno. */
    @Test
    fun `el estado sabe si repone al momento`() {
        assertTrue(GameState.initial(levelWith(HouseRule.INSTANT_REFILL)).hasInstantRefill)
        assertFalse(GameState.initial(levelWith(HouseRule.GRAVITY)).hasInstantRefill)
    }

    // ── P-232 · FICHAS QUE SE FUNDEN ───────────────────────────────────────────────────────────

    /** Dos copias se pegan por el lado corto y el resultado es el doble de casillas, sin solaparse. */
    @Test
    fun `dos gemelas se funden en una ficha del doble`() {
        val i3 = listOf(Position(0, 0), Position(1, 0), Position(2, 0))
        val fundida = PieceFusion.fuse(i3)
        assertEquals(6, fundida.size)
        assertEquals(6, fundida.distinct().size)
        // Una I3 tumbada crece hacia abajo: un bloque 3×2.
        assertEquals(setOf(0, 1), fundida.map { it.y }.toSet())
        val vertical = listOf(Position(0, 0), Position(0, 1), Position(0, 2))
        // Una I3 de pie crece hacia la derecha: 2×3.
        assertEquals(setOf(0, 1), PieceFusion.fuse(vertical).map { it.x }.toSet())
    }

    /** Solo gemelas, solo pequeñas, y nunca herramientas. */
    @Test
    fun `solo se funden gemelas pequenas`() {
        val a = Piece(shape = listOf(Position(0, 0), Position(1, 0)))
        val b = Piece(shape = listOf(Position(0, 0), Position(1, 0)))
        val otra = Piece(shape = listOf(Position(0, 0), Position(0, 1)))
        val grande = Piece(shape = (0 until 5).map { Position(it, 0) })
        assertTrue(PieceFusion.canFuse(a, b))
        assertFalse(PieceFusion.canFuse(a, a))
        assertFalse(PieceFusion.canFuse(a, otra))
        assertFalse(PieceFusion.canFuse(grande, grande.copy(id = "x")))
        assertFalse(PieceFusion.canFuse(a, b.copy(isMagnet = true)))
    }

    /** La fundida conserva las estrellas de las dos mitades y el color del destino. */
    @Test
    fun `la ficha fundida conserva las estrellas de las dos`() {
        val estrella = TargetColor.entries.first()
        val a = Piece(shape = listOf(Position(0, 0), Position(1, 0)), colorType = 1, targetMask = listOf(estrella, null))
        val b = Piece(shape = listOf(Position(0, 0), Position(1, 0)), colorType = 3)
        val f = PieceFusion.fused(dragged = a, target = b)
        assertEquals(4, f.shape.size)
        assertEquals(3, f.colorType)
        assertEquals(1, f.targetMask.count { it != null })
    }

    @Test
    fun `compactadora abre con tres fichas sin herramientas de corte`() {
        HouseRule.levelsWith(HouseRule.PRECISION_CUT).forEach { level ->
            assertFalse(PieceFusion.appliesTo(level))
            assertEquals(3,GameState.initial(level).availablePieces.size)
            assertFalse(PrecisionCut.appliesTo(level))
        }
    }

    // ── P-233 · EL MOLDE ───────────────────────────────────────────────────────────────────────

    /** La forma del molde es del catálogo, de 3 a 5 casillas, con la esquina en (0,0). */
    @Test
    fun `la forma del molde es una ficha real de tres a cinco casillas`() {
        val level = levelWith(HouseRule.CONSTELLATION)
        (0 until 10).forEach { wave ->
            val forma = MoldRule.shapeFor(level, wave)
            assertTrue(forma.size in 3..5)
            assertTrue(Piece.SHAPES.any { MoldRule.normalized(it) == forma })
            assertEquals(0, forma.minOf { it.x }); assertEquals(0, forma.minOf { it.y })
        }
    }

    /** El molde se pone solo sobre casillas libres, y dentro del tablero. */
    @Test
    fun `el molde cae en casillas libres`() {
        val level = levelWith(HouseRule.CONSTELLATION)
        val forma = MoldRule.shapeFor(level, 0)
        val ocupadas = (0 until 8).map { Position(it, 0) }.toSet() + (0 until 8).map { Position(it, 1) }
        val at = MoldRule.placeFor(forma, level, 0, ocupadas)!!
        val celdas = MoldRule.cellsOf(forma, at)
        assertTrue(celdas.none { it in ocupadas })
        assertTrue(celdas.all { it.x in 0..7 && it.y in 0..7 })
        val todo = (0 until 8).flatMap { y -> (0 until 8).map { x -> Position(x, y) } }.toSet()
        assertNull(MoldRule.placeFor(forma, level, 0, todo))
    }

    /** Solo encaja la ficha que ocupa EXACTAMENTE sus casillas; y el estallido se lleva su contorno. */
    @Test
    fun `solo encaja la ficha exacta y el estallido lleva su contorno`() {
        val molde = setOf(Position(3, 3), Position(4, 3), Position(4, 4))
        assertTrue(MoldRule.matches(molde.toSet(), molde))
        assertFalse(MoldRule.matches(setOf(Position(3, 3), Position(4, 3)), molde))
        assertFalse(MoldRule.matches(molde + Position(5, 5), molde))
        assertFalse(MoldRule.matches(emptySet(), emptySet()))
        val area = MoldRule.blastArea(molde)
        assertTrue(Position(2, 2) in area && Position(5, 5) in area)
        assertFalse(Position(6, 3) in area)
    }

    /** Los niveles del molde abren con su molde y con su forma en la bandeja. */
    @Test
    fun `los niveles del molde abren con el molde y su ficha`() {
        HouseRule.levelsWith(HouseRule.CONSTELLATION).forEach { level ->
            val state = GameState.initial(level)
            assertNull(state.moldAt)
            assertEquals(3, state.constellationPoints.size)
            assertTrue(state.constellationPoints.all { state.grid[it] is CellContent.Empty })
        }
    }

    @Test
    fun `la forma del molde llega con su cadencia`() {
        HouseRule.levelsWith(HouseRule.CONSTELLATION).forEach { level ->
            assertFalse(MoldRule.appliesTo(level))
            assertFalse(MoldRule.dealsOnRefill(6, level))
        }
    }

    // ── P-147 · LA FICHA ESPEJO ────────────────────────────────────────────────────────────────

    @Test
    fun `el espejo pasa de un eje a dos segun sube el bioma`() {
        val temprano = levelWith(HouseRule.MIRROR, 0)
        val tarde = levelWith(HouseRule.MIRROR, LevelMap.chapters.size - 1)
        if (temprano != null) assertEquals(MirrorPiece.Axis.HORIZONTAL, MirrorPiece.axisFor(temprano))
        if (tarde != null) assertEquals(MirrorPiece.Axis.BOTH, MirrorPiece.axisFor(tarde))
        assertNull(MirrorPiece.axisFor(levelWith(HouseRule.GRAVITY)))
    }

    /** El reflejo cae al otro lado, y en el mismo sitio contado desde el borde contrario. */
    @Test
    fun `refleja al otro lado del tablero`() {
        val cells = listOf(Position(1, 3))
        val out = MirrorPiece.reflectionsOf(cells, MirrorPiece.Axis.HORIZONTAL, emptySet(), 8, 8)
        assertEquals(setOf(Position(6, 3)), out)
    }

    /** Con los dos ejes, una celda deja tres huellas más: enfrente, abajo y en diagonal. */
    @Test
    fun `con los dos ejes deja tres reflejos`() {
        val out = MirrorPiece.reflectionsOf(listOf(Position(1, 2)), MirrorPiece.Axis.BOTH, emptySet(), 8, 8)
        assertEquals(setOf(Position(6, 2), Position(1, 5), Position(6, 5)), out)
    }

    /**
     * **EL REFLEJO NO PISA NADA**, y esta es la propiedad de la que depende que la regla no rompa
     * el resto del juego: media docena de sistemas guardan celdas por [Position] (muros, pozos,
     * lava, cúmulo galáctico…) y escribir madera encima dejaría el estado apuntando a celdas que ya
     * no son lo que dicen.
     */
    @Test
    fun `el reflejo respeta lo ocupado`() {
        val out = MirrorPiece.reflectionsOf(
            cells = listOf(Position(0, 0), Position(1, 0)),
            axis = MirrorPiece.Axis.HORIZONTAL,
            blocked = setOf(Position(7, 0)),
            width = 8, height = 8,
        )
        assertEquals(setOf(Position(6, 0)), out)
    }

    /**
     * Una ficha que **cruza el eje** se refleja sobre sí misma. Esas celdas ya las escribe la
     * colocación normal, así que el reflejo no las devuelve: estamparlas dos veces dejaría la
     * segunda pisando a la primera y, peor, contaría como reflejo algo que el jugador ya había
     * puesto él.
     */
    @Test
    fun `una ficha centrada no se refleja sobre si misma`() {
        // En un tablero de 8, la columna 3 refleja en la 4 y viceversa: una ficha que ocupe las dos
        // es su propio espejo.
        val cells = listOf(Position(3, 0), Position(4, 0))
        val out = MirrorPiece.reflectionsOf(cells, MirrorPiece.Axis.HORIZONTAL, emptySet(), 8, 8)
        assertTrue("el reflejo se ha pisado a sí mismo: $out", out.isEmpty())
    }

    // ── La frontera de la familia ──────────────────────────────────────────────────────────────

    /**
     * **NINGUNA REGLA DE LA FAMILIA B DEJA AL JUGADOR SIN JUGADA AL ABRIR.**
     *
     * Se comprueba sobre TODOS sus niveles y no sobre una muestra: es la garantía que costó los 14
     * niveles del damero, y es exactamente el tipo de propiedad que se rompe en un nivel concreto y
     * pasa desapercibida en los demás.
     */
    @Test
    fun `todos los niveles de la familia B abren con bandeja jugable`() {
        val familia = setOf(
            HouseRule.RESERVE, HouseRule.FORCED_QUEUE, HouseRule.INSTANT_REFILL,
            HouseRule.ROUTES, HouseRule.PRECISION_CUT, HouseRule.MIRROR, HouseRule.CONSTELLATION,
        )
        (LevelMap.introLevels + 1..LevelMap.totalLevels)
            .filter { HouseRule.forLevel(it) in familia }
            .forEach { level ->
                val estado = GameState.initial(level)
                assertTrue("nivel $level abre sin fichas", estado.availablePieces.isNotEmpty())
                assertTrue(
                    "nivel $level abre con una bandeja sin pieza de 3+",
                    estado.availablePieces.any { it.shape.size >= 3 }
                )
                // Y la cabeza de la cola siempre es jugable, lleve la regla que lleve.
                assertTrue("nivel $level bloquea su primera ficha", ForcedQueue.allows(level, 0))
            }
    }
}
