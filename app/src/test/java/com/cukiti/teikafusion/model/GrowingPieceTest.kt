package com.cukiti.teikafusion.model

import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de LA FICHA QUE CRECE (P-77). */
class GrowingPieceTest {

    @Test
    fun `crece exactamente un bloque y para en el tope`() {
        var shape = listOf(Position(0, 0))
        for (esperado in 2..GrowingPiece.MAX_CELLS) {
            shape = GrowingPiece.grow(shape)!!
            assertEquals(esperado, shape.size)
        }
        assertFalse(GrowingPiece.canGrow(shape))
        assertNull("creció por encima del tope", GrowingPiece.grow(shape))
    }

    /**
     * **Determinista a propósito.** El jugador tiene que poder PREVER en qué se va a convertir su
     * ficha: si el bloque nuevo saliera al azar, guardarla no sería una apuesta sino una lotería.
     */
    @Test
    fun `la misma forma crece siempre igual`() {
        val shape = listOf(Position(0, 0), Position(1, 0), Position(1, 1))
        assertEquals(GrowingPiece.grow(shape), GrowingPiece.grow(shape))
    }

    /** Sigue siendo un polinomio conexo y pegado a la esquina, como todo el catálogo. */
    @Test
    fun `lo que sale es una forma valida y normalizada`() {
        var shape = listOf(Position(0, 0), Position(0, 1))
        repeat(3) {
            shape = GrowingPiece.grow(shape) ?: return@repeat
            assertEquals("no está normalizada en X", 0, shape.minOf { it.x })
            assertEquals("no está normalizada en Y", 0, shape.minOf { it.y })
            assertEquals("tiene celdas repetidas", shape.size, shape.distinct().size)
            assertTrue("dejó de ser conexa", esConexa(shape))
        }
    }

    /** Cadencia propia, distinta de la Brocha (3) y la Camaleón (4). */
    @Test
    fun `la cadencia no coincide con las otras herramientas`() {
        assertTrue(GrowingPiece.EVERY_REFILLS != PureLineRules.BRUSH_EVERY_REFILLS)
        assertTrue(GrowingPiece.EVERY_REFILLS != PureLineRules.CHAMELEON_EVERY_REFILLS)
        assertFalse(GrowingPiece.onRefill(0))
        assertTrue(GrowingPiece.onRefill(GrowingPiece.EVERY_REFILLS))
    }

    /**
     * Va a la ficha **más pequeña**: es donde la apuesta dura más turnos. Una de dos celdas puede
     * crecer tres veces; una de cuatro, solo una.
     */
    @Test
    fun `se la lleva la ficha mas pequena`() {
        assertEquals(2, GrowingPiece.targetIndex(listOf(4, 3, 1)))
        assertEquals(-1, GrowingPiece.targetIndex(emptyList()))
    }

    /**
     * **EL PELIGRO REAL DE ESTA MECÁNICA.** `PieceTrayPlanner` garantiza que existe una colocación
     * para cada ficha **al repartirla**, no dos turnos después. Una ficha que engorda puede dejar de
     * caber, y una bandeja sin jugada es una derrota por culpa del repartidor (P-53).
     *
     * El crecimiento es condicional: el motor solo lo aplica si `shapeFitsAnywhere` dice que la
     * forma nueva sigue siendo jugable. Aquí se comprueba que ese comprobador **dice que no** cuando
     * de verdad no cabe.
     */
    @Test
    fun `el comprobador de encaje detecta cuando la forma nueva ya no cabe`() {
        val todo = (0 until 8).flatMap { y -> (0 until 8).map { x -> Position(x, y) } }
        // Tablero lleno menos DOS celdas sueltas y separadas: cabe un monominó, no un dominó.
        val libres = setOf(Position(0, 0), Position(7, 7))
        val ocupado = todo.toSet() - libres

        assertTrue(
            PieceTrayPlanner.shapeFitsAnywhere(listOf(Position(0, 0)), 8, 8, ocupado)
        )
        assertFalse(
            "un dominó no puede caber en dos huecos separados",
            PieceTrayPlanner.shapeFitsAnywhere(
                listOf(Position(0, 0), Position(1, 0)), 8, 8, ocupado
            )
        )
    }

    /** Y que prueba las CUATRO rotaciones: una I3 vertical cabe donde la horizontal no. */
    @Test
    fun `el comprobador prueba las rotaciones`() {
        val todo = (0 until 8).flatMap { y -> (0 until 8).map { x -> Position(x, y) } }
        // Solo la columna 0 está libre: una I3 solo entra de pie.
        val libres = (0 until 8).map { Position(0, it) }.toSet()
        val ocupado = todo.toSet() - libres
        val i3horizontal = listOf(Position(0, 0), Position(1, 0), Position(2, 0))
        assertTrue(
            "no probó la rotación: la I3 cabe de pie en la columna libre",
            PieceTrayPlanner.shapeFitsAnywhere(i3horizontal, 8, 8, ocupado)
        )
    }

    /**
     * El motor tiene que **condicionar** el crecimiento a ese comprobador. El invariante vive en el
     * ViewModel, así que se fija leyendo el fuente: si alguien quita la comprobación, la ficha podrá
     * crecer hasta no caber y el nivel se perderá por culpa del repartidor.
     */
    @Test
    fun `el motor no deja crecer una ficha que dejaria de caber`() {
        val vm = File(repoRoot, "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt")
            .readText()
        val bloque = vm.indexOf("P-77 · LA FICHA QUE CRECE")
        assertTrue("no encuentro el bloque de crecimiento", bloque > 0)
        val cuerpo = vm.substring(bloque, bloque + 2200)
        assertTrue(
            "el crecimiento ya no comprueba que la forma nueva quepa",
            "shapeFitsAnywhere" in cuerpo
        )
        assertTrue("no se salta el crecimiento cuando no cabe", "if (!cabe)" in cuerpo)
    }

    private fun esConexa(shape: List<Position>): Boolean {
        val restantes = shape.toMutableSet()
        val pila = ArrayDeque<Position>().apply { add(restantes.first()) }
        restantes.remove(pila.first())
        while (pila.isNotEmpty()) {
            val p = pila.removeLast()
            listOf(
                Position(p.x + 1, p.y), Position(p.x - 1, p.y),
                Position(p.x, p.y + 1), Position(p.x, p.y - 1),
            ).forEach { if (restantes.remove(it)) pila.add(it) }
        }
        return restantes.isEmpty()
    }

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "app/src/main/java/com/cukiti/teikafusion/").isDirectory) {
            dir = dir.parentFile
        }
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }
}
