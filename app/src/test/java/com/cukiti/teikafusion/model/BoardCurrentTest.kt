package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de LA CORRIENTE (P-79). */
class BoardCurrentTest {

    private val W = GameState.GRID_SIZE
    private val H = GameState.GRID_SIZE

    private fun row(y: Int, xs: List<Int>) = xs.mapTo(HashSet()) { Position(it, y) }

    private fun slides(
        wood: Set<Position>,
        anchors: Set<Position> = emptySet(),
        dir: Int = 1,
        splitAtX: Int = -1
    ) = BoardCurrent.computeSlides(wood, anchors, W, H, dir, splitAtX)

    /** La columna por la que parte el Corte Ninja: mitades de 4 (`GameViewModel.NINJA_CUT_COL`). */
    private val CUT = 4

    // ── Cuándo hay corriente ───────────────────────────────────────────────────────────────────

    /**
     * **El reparto lo decide [HouseRule]** (2026-09-03). Era «uno de cada trece»; hoy la corriente es
     * una entrada del catálogo de reglas de la casa, y lo que esta prueba fija es lo que de verdad
     * importa de ese reparto: que solo caiga en niveles jugables y que sea el catálogo quien lo diga.
     */
    @Test
    fun `la reparte el catalogo de reglas, sin intro ni tutorial`() {
        // La corriente es una FAMILIA de cuatro reglas —la de siempre, la vertical, la noria y la
        // marea—: todas empujan el tablero, todas usan el mismo solver, y `levels()` las devuelve
        // juntas porque el motor las trata igual.
        val familia = setOf(
            HouseRule.CURRENT, HouseRule.VERTICAL_CURRENT, HouseRule.TIDE
        )
        BoardCurrent.levels().forEach { level ->
            assertTrue("nivel $level", HouseRule.forLevel(level) in familia)
            assertTrue(level > LevelMap.introLevels)
            assertFalse(TutorialCampaign.isTutorialLevel(level))
        }
    }

    /**
     * **Una regla de la casa por nivel.** Gravedad, horizonte y corriente cambian las reglas del
     * tablero cada una a su manera; dos a la vez no son el doble de interesantes, son ilegibles.
     */
    @Test
    fun `nunca coincide con gravedad ni con horizonte`() {
        val gravedad = BoardGravity.levels().toSet()
        val horizonte = HorizonTray.levels().toSet()
        BoardCurrent.levels().forEach {
            assertFalse("nivel $it lleva gravedad también", it in gravedad)
            assertFalse("nivel $it lleva horizonte también", it in horizonte)
        }
    }

    @Test
    fun `avisa el turno antes de embestir`() {
        BoardCurrent.levels().forEach { level ->
            val p = BoardCurrent.periodFor(level)
            assertTrue("nivel $level", BoardCurrent.warnsOn(p - 1, level))
            assertTrue("nivel $level", BoardCurrent.firesOn(p, level))
            assertFalse("nivel $level", BoardCurrent.firesOn(p - 1, level))
            assertFalse("nivel $level", BoardCurrent.warnsOn(p, level))
            assertEquals("nivel $level", 1, BoardCurrent.turnsUntil(p - 1, level))
        }
    }

    /**
     * **EL DIAL DE CAPÍTULO** (2026-09-03, pedido del usuario: «que cada mecánica se vuelva más
     * difícil según sube de bioma»). La corriente embiste cada cuatro turnos en el primer bioma y
     * cada dos en el último, y **nunca menos de dos**: con periodo 1 el tablero se movería en cada
     * colocación y planificar contra ella dejaría de ser una habilidad para ser una tirada.
     */
    @Test
    fun `la embestida se acelera de bioma en bioma`() {
        assertEquals(
            BoardCurrent.PERIOD_FIRST_CHAPTER,
            BoardCurrent.periodFor(LevelMap.chapterStart.first())
        )
        assertEquals(
            BoardCurrent.PERIOD_LAST_CHAPTER,
            BoardCurrent.periodFor(LevelMap.chapterStart.last())
        )
        var previo = Int.MAX_VALUE
        LevelMap.chapterStart.forEach { start ->
            val p = BoardCurrent.periodFor(start)
            assertTrue("el periodo sube en el capítulo de $start", p <= previo)
            assertTrue("periodo $p ingobernable en $start", p >= 2)
            previo = p
        }
    }

    /** La colocación 0 no dispara nada: el nivel no puede empezar con una embestida. */
    @Test
    fun `no embiste antes de jugar`() {
        BoardCurrent.levels().forEach { assertFalse(BoardCurrent.firesOn(0, it)) }
    }

    /** El sentido es FIJO dentro de un nivel: planificar contra ella tiene que ser una habilidad. */
    @Test
    fun `el sentido es estable y alterna entre niveles`() {
        val a = BoardCurrent.directionFor(26)
        assertEquals(a, BoardCurrent.directionFor(26))
        assertEquals(-a, BoardCurrent.directionFor(27))
    }

    // ── Cómo empuja ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `un bloque suelto avanza una columna`() {
        val moves = slides(row(3, listOf(2)))
        assertEquals(listOf(BoardCurrent.Slide(Position(2, 3), Position(3, 3))), moves)
    }

    /** **Envuelve** (decisión del usuario): lo que sale por un lado entra por el otro. */
    @Test
    fun `lo que sale por un borde entra por el otro`() {
        val derecha = slides(row(1, listOf(W - 1)), dir = 1)
        assertEquals(Position(0, 1), derecha.single().to)
        val izquierda = slides(row(1, listOf(0)), dir = -1)
        assertEquals(Position(W - 1, 1), izquierda.single().to)
    }

    /** Y nada se pierde nunca: salen tantos bloques como entran. */
    @Test
    fun `la corriente conserva toda la madera`() {
        val wood = row(4, listOf(0, 1, 3, 6, 7))
        val moves = slides(wood)
        val despues = wood.toMutableSet()
        moves.forEach { despues.remove(it.from) }
        moves.forEach { despues.add(it.to) }
        assertEquals(wood.size, despues.size)
    }

    /** Una fila sin rocas rota ENTERA: es el caso que da nombre a la mecánica. */
    @Test
    fun `una fila sin anclas rota entera`() {
        val wood = row(0, listOf(0, 1, 2, 5))
        val moves = slides(wood)
        assertEquals(4, moves.size)
        moves.forEach { assertEquals((it.from.x + 1) % W, it.to.x) }
    }

    /** La madera pegada a una roca NO la atraviesa ni la empuja: se queda. */
    @Test
    fun `lo que topa con un ancla se queda`() {
        val ancla = setOf(Position(4, 2))
        val moves = slides(row(2, listOf(3)), ancla)
        assertTrue(moves.isEmpty())
    }

    /** Y lo de detrás se amontona contra ella, en vez de colarse dentro. */
    @Test
    fun `la fila se amontona detras del ancla`() {
        val ancla = setOf(Position(5, 6))
        // 2 · 3 · 4 pegados, con hueco en 1: solo el de más atrás tiene sitio.
        val moves = slides(row(6, listOf(2, 3, 4)), ancla).associate { it.from to it.to }
        assertTrue(moves.isEmpty())
        val conHueco = slides(row(6, listOf(2, 4)), ancla).associate { it.from to it.to }
        assertEquals(Position(3, 6), conHueco[Position(2, 6)])
        assertEquals(null, conHueco[Position(4, 6)])
    }

    /** Un ancla que además es madera (congelada, bobina) no se mueve: manda el ancla. */
    @Test
    fun `un ancla que es madera no viaja`() {
        val p = Position(3, 3)
        assertTrue(slides(setOf(p), setOf(p)).isEmpty())
    }

    /** Con un ancla en la fila, la corriente ya no puede dar la vuelta entera por ahí. */
    @Test
    fun `con ancla no hay vuelta completa`() {
        val ancla = setOf(Position(0, 5))
        val moves = slides(row(5, listOf(W - 1)), ancla)
        assertTrue("la madera atravesó el ancla envolviendo", moves.isEmpty())
    }

    /** Nada cambia de fila: la corriente es horizontal, y eso es lo que la hace legible. */
    @Test
    fun `nadie cambia de fila`() {
        val wood = buildSet {
            for (y in 0 until H) for (x in 0 until W step 2) add(Position(x, y))
        }
        slides(wood).forEach { assertEquals(it.from.y, it.to.y) }
    }

    /** Ningún destino se repite: dos bloques no pueden acabar en la misma celda. */
    @Test
    fun `los destinos no colisionan`() {
        val wood = buildSet {
            for (y in 0 until H) for (x in 0 until W) if ((x + y) % 3 != 0) add(Position(x, y))
        }
        val anchors = setOf(Position(4, 4), Position(1, 2))
        val moves = BoardCurrent.computeSlides(wood - anchors, anchors, W, H, 1)
        assertEquals(moves.size, moves.map { it.to }.distinct().size)
        val destinos = moves.map { it.to }.toSet()
        val quietos = (wood - anchors) - moves.map { it.from }.toSet()
        assertTrue("un bloque quieto recibió a otro encima", destinos.none { it in quietos })
    }

    // ── P-101 · el corte ninja es una roca ─────────────────────────────────────────────────────

    /**
     * **Nadie cruza el tajo.** El corte es una barrera que respetan la colocación, la física y el
     * motor de líneas; la corriente era el único sitio del juego donde algo la atravesaba.
     */
    @Test
    fun `con el tablero partido nadie cruza el tajo`() {
        // Un bloque pegado al tajo por la izquierda y otro pegado al borde derecho.
        val wood = row(3, listOf(CUT - 1, W - 1))
        val moves = slides(wood, dir = 1, splitAtX = CUT)
        assertTrue(
            "un bloque cruzó la katana: $moves",
            moves.none { (it.from.x < CUT) != (it.to.x < CUT) }
        )
        // El de la izquierda topa con el tajo y el de la derecha con el borde: nadie se mueve.
        assertTrue("algo se movió contra una pared: $moves", moves.isEmpty())
    }

    /** Cada mitad se desliza por su cuenta: la corriente sigue existiendo, solo que en dos ríos. */
    @Test
    fun `cada mitad se desliza por separado`() {
        val wood = row(2, listOf(0, CUT))
        val moves = slides(wood, dir = 1, splitAtX = CUT).associate { it.from to it.to }
        assertEquals(Position(1, 2), moves[Position(0, 2)])
        assertEquals(Position(CUT + 1, 2), moves[Position(CUT, 2)])
    }

    /**
     * **Y no hay vuelta completa mientras dura el corte.** Sin él, una fila limpia ROTA entera; con
     * el tablero partido, envolver significaría cruzar el tajo por el otro lado.
     */
    @Test
    fun `partido no envuelve`() {
        val wood = row(5, (0 until W).toList())
        val moves = slides(wood, dir = 1, splitAtX = CUT)
        assertTrue("alguien envolvió con el tablero partido", moves.none { BoardCurrent.wraps(it, W) })
        // La fila llena no tiene hueco en ninguna de las dos mitades: nadie se mueve.
        assertTrue(moves.isEmpty())
    }

    /** Sin corte, la fila limpia sigue rotando entera: el arreglo no toca el caso normal. */
    @Test
    fun `sin corte la fila limpia sigue rotando`() {
        val wood = row(5, (0 until W).toList())
        assertEquals(W, slides(wood, dir = 1).size)
        assertEquals(W, slides(wood, dir = 1, splitAtX = -1).size)
    }

    /** El que envuelve se reconoce para poder dibujarlo en dos tramos (sale y entra). */
    @Test
    fun `se detecta el arrastre que envuelve`() {
        val normal = BoardCurrent.Slide(Position(2, 0), Position(3, 0))
        val envuelve = BoardCurrent.Slide(Position(W - 1, 0), Position(0, 0))
        assertFalse(BoardCurrent.wraps(normal, W))
        assertTrue(BoardCurrent.wraps(envuelve, W))
    }

    // ── P-231 · LA NORIA ───────────────────────────────────────────────────────────────────────

    /** El anillo son las 28 casillas del borde, en orden y todas vecinas de la siguiente. */
    @Test
    fun `el anillo de la noria es el borde entero y cerrado`() {
        val ring = BoardCurrent.ringOf(8, 8)
        assertEquals(28, ring.size)
        (ring + ring.first()).zipWithNext().forEach { (a, b) ->
            assertEquals("el anillo salta de $a a $b", 1, kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y))
        }
    }

    /** Sin anclas, todo lo del borde avanza una casilla; el centro no se mueve. */
    @Test
    fun `la noria gira el borde y deja quieto el centro`() {
        val borde = setOf(Position(0, 0), Position(7, 0), Position(7, 7))
        val centro = Position(3, 3)
        val slides = BoardCurrent.computeRingSlides(borde + centro, emptySet(), 8, 8, direction = 1)
        assertEquals(3, slides.size)
        assertTrue(slides.none { it.from == centro })
        // En el sentido de las agujas: (0,0) → (1,0), (7,0) → (7,1), (7,7) → (6,7).
        assertTrue(BoardCurrent.Slide(Position(0, 0), Position(1, 0)) in slides)
        assertTrue(BoardCurrent.Slide(Position(7, 0), Position(7, 1)) in slides)
        assertTrue(BoardCurrent.Slide(Position(7, 7), Position(6, 7)) in slides)
        // Ninguno «envuelve»: en un anillo siempre se va a la casilla de al lado.
        assertTrue(slides.none { BoardCurrent.wraps(it, 8) })
    }

    /** Un ancla en el anillo es una roca: lo que empuja contra ella se queda. */
    @Test
    fun `la madera de la noria se atasca contra una roca`() {
        val roca = Position(2, 0)
        val slides = BoardCurrent.computeRingSlides(setOf(Position(1, 0)), setOf(roca), 8, 8, direction = 1)
        assertTrue(slides.isEmpty())
    }

    /** Con el tablero partido por el Dojo la noria no gira. */
    @Test
    fun `la noria partida por el corte no gira`() {
        assertTrue(BoardCurrent.computeRingSlides(setOf(Position(0, 0)), emptySet(), 8, 8, 1, splitAtX = 4).isEmpty())
    }

    @Test
    fun `Deslizamiento retira la noria y su corriente del anillo`() {
        HouseRule.levelsWith(HouseRule.SLIDE).forEach { level ->
            assertEquals(null, BoardCurrent.axisFor(level))
            assertFalse(GameState.initial(level).hasWheel)
        }
    }
}
