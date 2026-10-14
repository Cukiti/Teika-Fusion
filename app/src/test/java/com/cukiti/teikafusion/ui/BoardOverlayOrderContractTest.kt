package com.cukiti.teikafusion.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **LO QUE EL CUERPO NO PUEDE TAPAR** (bug de playtest, 2026-09-03).
 *
 * Los Teikas son cuerpos físicos y se dibujan en su propia capa, muy por debajo de las celdas en el
 * orden del `Box`: **todo lo que pinte `CellView` queda debajo de ellos**. Casi siempre da igual —un
 * cuerpo encima de madera lisa se lee bien— pero hay dos cosas que un cuerpo NO puede tapar, y las
 * dos se descubrieron por separado y de la peor forma:
 *
 *  1. **La escarcha del Glaciar**: el Teika tapaba su propio hielo y parecía no estar congelado.
 *     Se arregló repintándola después (`FrozenBodiesOverlay`).
 *  2. **El número de la bobina de la Tormenta**: peor, porque no es estética. El circuito se
 *     resuelve tocando las bobinas en orden ascendente y el orden esperado se calcula sobre el
 *     grid, así que una bobina tapada deja la fila **imposible de descargar**: el jugador toca las
 *     visibles, todas fallan, cada fallo cortocircuita lo que llevaba y encima le cuesta un turno.
 *     Se arregló igual (`CoilsOverBodiesOverlay`).
 *
 * Dos veces el mismo fallo es un patrón, no una casualidad. Este test lee el código y fija el orden:
 * si alguien reordena el `Box` del tablero y devuelve cualquiera de estas capas por debajo de los
 * cuerpos, cae aquí y no en el móvil de un jugador.
 */
class BoardOverlayOrderContractTest {

    /** Capas que DEBEN pintarse después de los cuerpos, con el porqué en el mensaje del fallo. */
    private val sobreLosCuerpos = mapOf(
        "FrozenBodiesOverlay" to
            "el hielo del Glaciar: sin él, un Teika congelado parece libre",
        "CoilsOverBodiesOverlay" to
            "el número de la bobina de la Tormenta: sin él, la fila no se puede descargar NUNCA"
    )

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, GAME_BOARD).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo" }
    }

    private val source: String by lazy { File(repoRoot, GAME_BOARD).readText() }

    @Test
    fun `las capas que no se pueden tapar se dibujan despues de los cuerpos`() {
        val cuerpos = source.indexOf("TeikaPhysicsBodiesLayer(\n")
            .takeIf { it >= 0 }
            ?: source.indexOf("TeikaPhysicsBodiesLayer(")
        assertTrue(
            "No encuentro la llamada a TeikaPhysicsBodiesLayer: si se renombró, actualiza este test.",
            cuerpos >= 0
        )
        sobreLosCuerpos.forEach { (capa, porque) ->
            val donde = source.indexOf("$capa(")
            assertTrue("No encuentro la llamada a $capa — ¿se retiró la capa?", donde >= 0)
            assertTrue(
                "«$capa» se dibuja ANTES que los cuerpos de Teika, así que los cuerpos la tapan. " +
                    "Eso rompe $porque.",
                donde > cuerpos
            )
        }
    }

    /**
     * La lava no puede invadir la fila jugable: su cresta se ancla a `boundaryY` y ese nombre es el
     * contrato. Reporte del mismo playtest: «la bajada de la lava tapa demasiado los bloques de
     * arriba». Sumaban ~0,62 celdas por debajo de la línea entre el hinchado del aviso, la
     * oscilación centrada y el trazo del brillo.
     */
    @Test
    fun `la lava se ancla a una linea dura y no a la superficie hinchada`() {
        val vfx = File(repoRoot, VFX).readText()
        val lava = vfx.substring(vfx.indexOf("fun LavaOverlay("))
            .let { it.substring(0, minOf(it.length, 9000)) }
        assertTrue(
            "LavaOverlay ya no declara `boundaryY`: era la línea que impide que la lava se dibuje " +
                "sobre la primera fila jugable.",
            lava.contains("val boundaryY")
        )
        assertTrue(
            "La cresta debe anclarse restando el vano de la onda y el grosor del brillo, para que " +
                "su punto más bajo aterrice justo en la línea.",
            lava.contains("val crestY = boundaryY - waveSpan - glowHalf")
        )
    }

    private companion object {
        const val GAME_BOARD = "app/src/main/java/com/cukiti/teikafusion/ui/GameBoard.kt"
        const val VFX = "app/src/main/java/com/cukiti/teikafusion/ui/VFX.kt"
    }
}
