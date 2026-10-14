package com.cukiti.teikafusion.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **LA FICHA NO SE QUEDA LEVANTADA** (auditoría 2026-10-02, captura del usuario: una ficha de la
 * bandeja se quedó flotando a tamaño de tablero encima de su cuadro).
 *
 * La ficha arrastrada se pinta mientras `draggedPieceLocal` no es nulo, y solo las ramas de «soltar»
 * del gesto lo vaciaban. Si la corrutina del gesto se cancelaba a mitad (una ventana encima, el nodo
 * desmontado), ninguna corría. Este contrato fija las dos redes: el `finally` del gesto y el efecto
 * que suelta la ficha si sale de la bandeja o la partida deja de estar en juego.
 */
class TrayDragContractTest {
    private val src: String by lazy {
        var dir: File? = File("").absoluteFile
        val rel = "app/src/main/java/com/cukiti/teikafusion/ui/GameScreen.kt"
        while (dir != null && !File(dir, rel).isFile) dir = dir.parentFile
        File(requireNotNull(dir), rel).readText().replace("\r\n", "\n")
    }

    @Test
    fun `el gesto devuelve la ficha aunque se corte a medias`() {
        val fin = src.indexOf("} finally {\n                                        // Las ramas normales ya la soltaron")
        assertTrue("Falta el finally del gesto de la bandeja", fin > 0)
        val body = src.substring(fin, fin + 900)
        assertTrue("El finally no vacía draggedPieceLocal", "draggedPieceLocal = null" in body)
        assertTrue("El finally no avisa al motor", "viewModel.onDragCancel()" in body)
    }

    @Test
    fun `una ficha que sale de la bandeja no se queda en la mano`() {
        assertTrue(
            "Falta la segunda red (LaunchedEffect sobre draggedPieceLocal y la bandeja)",
            "LaunchedEffect(draggedPieceLocal?.id, gameState.availablePieces, gameState.status)" in src
        )
    }
}
