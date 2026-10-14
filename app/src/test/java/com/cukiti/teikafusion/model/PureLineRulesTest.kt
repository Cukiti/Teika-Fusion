package com.cukiti.teikafusion.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de LÍNEA PURA (P-69) y LA BROCHA (P-70). */
class PureLineRulesTest {

    private fun row(y: Int, w: Int = GameState.GRID_SIZE) = (0 until w).map { Position(it, y) }

    @Test
    fun `una linea de un solo color es pura`() {
        val cells = row(0)
        assertTrue(PureLineRules.isPure(cells) { 2 })
    }

    @Test
    fun `un solo bloque de otro color la rompe`() {
        val cells = row(0)
        assertFalse(PureLineRules.isPure(cells) { if (it.x == 5) 3 else 2 })
    }

    /**
     * Los rellenos sin color —cuerpo de Teika comodín, muro del Rey, lava, pozo del Glotón— son
     * NEUTROS. Cerrar una línea apoyándose en un Teika ya es caro; que además te negara la pureza
     * sería castigar dos veces la misma jugada, que es justo el razonamiento por el que una fila
     * cerrada con Teika tampoco se energiza en la Tormenta.
     */
    @Test
    fun `los rellenos sin color no rompen la pureza`() {
        val cells = row(0)
        assertTrue(PureLineRules.isPure(cells) { if (it.x in 3..4) null else 1 })
    }

    /** Una línea completada ENTERA por muro y lava no la ha compuesto nadie: no se premia. */
    @Test
    fun `una linea sin nada de color no es pura`() {
        assertFalse(PureLineRules.isPure(row(0)) { null })
    }

    @Test
    fun `la brocha pinta la cruz completa de su huella`() {
        val footprint = listOf(Position(2, 3), Position(3, 3))
        val painted = PureLineRules.brushCells(footprint, 8, 8)
        // Su fila entera…
        (0 until 8).forEach { assertTrue("falta ($it,3)", Position(it, 3) in painted) }
        // …y sus dos columnas enteras.
        (0 until 8).forEach { assertTrue("falta (2,$it)", Position(2, it) in painted) }
        (0 until 8).forEach { assertTrue("falta (3,$it)", Position(3, it) in painted) }
        // Y nada más: 8 + 8 + 8 − 2 solapes de la fila con cada columna.
        assertEquals(22, painted.size)
        assertFalse(Position(5, 5) in painted)
    }

    @Test
    fun `una brocha sin huella no pinta nada`() {
        assertTrue(PureLineRules.brushCells(emptyList(), 8, 8).isEmpty())
    }

    /**
     * La cadencia va contra `trayRefillCount`, que se PERSISTE: el jugador no puede farmear brochas
     * saliendo y volviendo a entrar al nivel.
     */
    @Test
    fun `la brocha llega cada N reposiciones y nunca en la cuenta cero`() {
        assertFalse(PureLineRules.brushOnRefill(0))
        val hits = (1..30).filter { PureLineRules.brushOnRefill(it) }
        assertEquals((1..30).filter { it % PureLineRules.BRUSH_EVERY_REFILLS == 0 }, hits)
    }

    /** La brocha va a la ficha MÁS PEQUEÑA: es la que se puede colocar donde de verdad hace falta. */
    @Test
    fun `la brocha se la lleva la ficha mas pequena`() {
        assertEquals(1, PureLineRules.brushTargetIndex(listOf(4, 1, 3)))
        assertEquals(0, PureLineRules.brushTargetIndex(listOf(2, 2, 2)))
        assertEquals(-1, PureLineRules.brushTargetIndex(emptyList()))
    }

    /**
     * **El orden dentro del turno es la regla, no un detalle de implementación.**
     *
     * El tinte de §2.7 repinta con el color de la ficha toda línea que quede completa. Si la pureza
     * se midiera después, CUALQUIER línea cerrada por el jugador sería pura siempre y el premio se
     * regalaría solo. Y la brocha tiene que ir antes de medir, porque su único fin es crear líneas
     * puras. Este test lee el fuente y comprueba que los tres bloques siguen en ese orden.
     */
    @Test
    fun `la brocha pinta antes de medir y la pureza se mide antes del tinte`() {
        val vm = File(repoRoot, "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt")
            .readText()
        val brush = vm.indexOf("// 2.6 LA BROCHA")
        val pure = vm.indexOf("// 2.65 LÍNEA PURA")
        val tint = vm.indexOf("// 2.7 NIVELES / tinte Block Blast")
        assertTrue("no encuentro el bloque de la brocha", brush > 0)
        assertTrue("no encuentro la medición de pureza", pure > 0)
        assertTrue("no encuentro el tinte", tint > 0)
        assertTrue("la brocha dejó de ir antes de medir la pureza", brush < pure)
        assertTrue("la pureza se mide DESPUÉS del tinte: se regala sola", pure < tint)
    }

    /**
     * **LA MEDICIÓN DE PUREZA SE GASTA AL USARLA** (regresión encontrada el 2026-08-30, mismo día).
     *
     * `resolveLineClearPass` tiene **dos** llamantes, y el segundo —`autoClearWalledLines`, el
     * derribo automático del asedio del Rey— corre FUERA del turno: heredaba la medición de la
     * colocación anterior y podía engendrar un T2 por una fila que el jugador no había compuesto.
     * Lo mismo pasaba con las pasadas siguientes de una cadena cuando una fila pura no se limpiaba
     * en la suya (circuito de la Tormenta, bloqueo galáctico).
     *
     * El invariante vive en el ViewModel, así que se fija leyendo el fuente: los dos `clear()` tienen
     * que estar DENTRO de `resolveLineClearPass`, después de engendrar los Teikas.
     */
    @Test
    fun `la medicion de pureza se consume dentro de la pasada de limpieza`() {
        val vm = File(repoRoot, "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt")
            .readText()
        val passStart = vm.indexOf("private fun resolveLineClearPass")
        val measure = vm.indexOf("// 2.65 LÍNEA PURA")
        val read = vm.indexOf("in pureRowsThisTurn", passStart)
        val consumed = vm.indexOf("pureRowsThisTurn.clear()", passStart)
        assertTrue("no encuentro la pasada de limpieza", passStart > 0)
        assertTrue("no encuentro la lectura de la pureza", read > passStart)
        assertTrue(
            "la pasada de limpieza ya no vacia la medicion: el asedio del Rey volvera a heredarla",
            consumed > passStart
        )
        // El clear encontrado es el de la PASADA, no el de la medicion de §2.65 (que va despues).
        assertTrue("ese clear es el de la medicion, no el de la pasada", consumed < measure)
        // Y se gasta DESPUES de leerla: al reves, nunca habria linea pura.
        assertTrue("la medicion se vacia antes de leerse", read < consumed)
    }

    // ── LA CAMALEÓN (P-72) ───────────────────────────────────────────────────────────────────────

    @Test
    fun `la camaleon adopta el color que mas toca`() {
        assertEquals(2, PureLineRules.chameleonColorFor(listOf(2, 2, 3), boardFallback = 0))
        assertEquals(3, PureLineRules.chameleonColorFor(listOf(3), boardFallback = 0))
    }

    /**
     * Sin vecinos —la ficha cae en campo abierto— hereda el color DOMINANTE del tablero. Tenía que
     * caer en algún lado y ese es el que más ayuda a cerrar una línea pura.
     */
    @Test
    fun `sin vecinos hereda el dominante del tablero`() {
        assertEquals(4, PureLineRules.chameleonColorFor(emptyList(), boardFallback = 4))
    }

    /**
     * **El desempate va al dominante del tablero**, no al último color jugado (las dos opciones que
     * la propuesta dejaba abiertas). Es una regla que el jugador puede VER en pantalla; «el último
     * que jugaste» le obligaría a recordar algo que no está delante.
     */
    @Test
    fun `el empate lo rompe el dominante del tablero si esta empatado`() {
        assertEquals(1, PureLineRules.chameleonColorFor(listOf(1, 2), boardFallback = 1))
        assertEquals(2, PureLineRules.chameleonColorFor(listOf(1, 2), boardFallback = 2))
        // Si el dominante no está entre los empatados, gana el menor: determinista, sin azar en una
        // decisión que el jugador está intentando planear.
        assertEquals(1, PureLineRules.chameleonColorFor(listOf(1, 2), boardFallback = 4))
    }

    /** Cadencias distintas (3 y 4) para que las dos herramientas no caigan siempre juntas. */
    @Test
    fun `brocha y camaleon tienen cadencias distintas`() {
        assertNotEquals(
            PureLineRules.BRUSH_EVERY_REFILLS,
            PureLineRules.CHAMELEON_EVERY_REFILLS
        )
        val juntas = (1..24).count {
            PureLineRules.brushOnRefill(it) && PureLineRules.chameleonOnRefill(it)
        }
        assertEquals("coinciden más de lo previsto en 24 reposiciones", 2, juntas)
    }

    /** La Camaleón va a la ficha MÁS GRANDE: es la que toca más madera y da información real. */
    @Test
    fun `la camaleon se la lleva la ficha mas grande`() {
        assertEquals(1, PureLineRules.chameleonTargetIndex(listOf(2, 6, 3)))
        assertEquals(-1, PureLineRules.chameleonTargetIndex(emptyList()))
    }

    /**
     * **Nunca las dos en la misma ficha.** Una brocha sin color no tiene nada que pintar; en una
     * bandeja de una sola pieza las dos apuntarían a ella y manda la Brocha.
     */
    @Test
    fun `brocha y camaleon detectan cuando irian a la misma ficha`() {
        assertTrue(PureLineRules.brushAndChameleonCollide(listOf(4)))
        assertTrue(PureLineRules.brushAndChameleonCollide(listOf(3, 3, 3)))
        assertFalse(PureLineRules.brushAndChameleonCollide(listOf(1, 4, 3)))
    }

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "app/src/main/java/com/cukiti/teikafusion/").isDirectory) {
            dir = dir.parentFile
        }
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }
}
