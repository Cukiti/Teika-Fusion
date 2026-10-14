package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contrato de los `GameStatus` OBSOLETOS (bloque 23, 2026-07-31).
 *
 * `GameStatus` viaja DENTRO del save serializado. Por eso los cuatro valores de pantallas ya
 * eliminadas —`MODE_SELECTION`, `SKILL_TREE` (la pestaña «H. Teika»), `PROFILE` (la Colección) y
 * `CHESS` (el minijuego del Rey)— **no se pueden borrar del enum**: hacerlo rompería la
 * deserialización de cualquier partida guardada antes de su retirada. Se conservan y
 * [normalizeObsolete] los traduce a una pantalla viva al cargar.
 *
 * El bloque 23 no tenía NINGÚN test («Tests: ninguno (UI)»), pero esto no es UI: es lógica pura que
 * guarda la compatibilidad de los saves, y el modo de fallo es silencioso y feo — un jugador que
 * vuelve tras la actualización aterriza en un estado que ya no dibuja nada.
 */
class GameStatusNormalizationTest {

    /** Los cuatro que sobreviven solo por los saves. Si se añade otro, va aquí. */
    private val obsoletos = setOf(
        GameStatus.MODE_SELECTION,
        GameStatus.SKILL_TREE,
        GameStatus.PROFILE,
        GameStatus.CHESS
    )

    @Test
    fun `todo estado obsoleto aterriza en una pantalla viva`() {
        for (viejo in obsoletos) {
            val normalizado = viejo.normalizeObsolete()
            assertFalse(
                "$viejo se normaliza a $normalizado, que también está muerto",
                normalizado in obsoletos
            )
        }
    }

    @Test
    fun `cada obsoleto va a donde vive ahora su contenido`() {
        // MODE_SELECTION (el selector de modo murió), PROFILE (la Colección se eliminó) y CHESS
        // (el minijuego se eliminó) no tienen sucesor: a Inicio.
        assertEquals(GameStatus.HOME, GameStatus.MODE_SELECTION.normalizeObsolete())
        assertEquals(GameStatus.HOME, GameStatus.PROFILE.normalizeObsolete())
        assertEquals(GameStatus.HOME, GameStatus.CHESS.normalizeObsolete())
        // «H. Teika» SÍ tiene sucesor: sus tarjetas viven dentro de la pestaña de tienda/inventario.
        assertEquals(GameStatus.STORE, GameStatus.SKILL_TREE.normalizeObsolete())
    }

    @Test
    fun `ningun estado vivo se toca`() {
        // La normalización es para saves viejos. Si tocara un estado vivo, cargar una partida en
        // curso te sacaría de ella (p. ej. PLAYING → HOME perdería la partida al continuar).
        for (estado in GameStatus.entries) {
            if (estado in obsoletos) continue
            assertEquals("$estado NO debe normalizarse", estado, estado.normalizeObsolete())
        }
    }

    @Test
    fun `normalizar es idempotente`() {
        // Se llama en dos rutas de carga distintas (arranque y CONTINUAR): pasar dos veces por él
        // tiene que dar lo mismo.
        for (estado in GameStatus.entries) {
            val una = estado.normalizeObsolete()
            assertEquals(una, una.normalizeObsolete())
        }
    }

    @Test
    fun `el censo de obsoletos sigue cuadrando con el enum`() {
        // Red de seguridad: si alguien AÑADE un estado y lo deja sin decidir, este test no lo caza —
        // pero si BORRA uno de los obsoletos (lo que rompe los saves), el enum deja de compilar
        // contra esta lista. Aquí solo se fija que el enum no ha encogido por debajo de lo conocido.
        assertTrue(
            "El enum ha perdido estados: los obsoletos NO se borran (romperían saves)",
            GameStatus.entries.containsAll(obsoletos)
        )
    }
}
