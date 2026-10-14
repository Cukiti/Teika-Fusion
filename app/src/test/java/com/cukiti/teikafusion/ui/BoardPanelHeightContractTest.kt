package com.cukiti.teikafusion.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **EL PANEL DEL TABLERO MIDE EL TABLERO** (2026-08-26e) — hermano de `ImmersiveWindowContractTest`,
 * `GameEventContractTest` y `ControlsNotDimmedContractTest`.
 *
 * El `Box` de `GameBoard` que pinta el panel oscuro (`background(boardBg, boardShape)`) recibía solo
 * un `fillMaxWidth()` desde `GameScreen`. Un `Box` mide lo que mida su hijo **más alto**, y el máximo
 * que este recibe es todo el hueco que le queda a la `Column`: media pantalla.
 *
 * De la sesentena de capas de VFX que cuelgan de ese Box, muchas se declaran con
 * `Modifier.fillMaxSize()`. En reposo el hijo más alto era el tablero y el panel medía `boardH`; en
 * cuanto una de esas capas se montaba —explosión del MEGA, texto de combo, bloques huyendo—, se
 * estiraba hasta el hueco entero **y el panel con ella**, tapando la bandeja de fichas y
 * empujándola fuera de la `Column`.
 *
 * Lo reportó el usuario («el tablero se agranda hacia abajo tapando el panel de fichas») y se
 * confirmó midiendo `art/bugpanel.mp4`: en t≈25,7 s el panel llega hasta el anuncio y donde estaba
 * la bandeja hay un rectángulo **uniforme** de (30,37,65) —`BoardBackground`— de borde a borde,
 * durante 0,4 s; después la bandeja vuelve con las MISMAS tres fichas.
 *
 * Esta prueba fija la única línea que lo impide. Es exactamente el tipo de invariante que el
 * compilador no puede defender: quitar un `.height(...)` no rompe nada… hasta que se monta la
 * siguiente capa a pantalla completa.
 */
class BoardPanelHeightContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, GAME_BOARD).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    private val source: String by lazy { File(repoRoot, GAME_BOARD).readText() }

    @Test
    fun `el censo encuentra la fuente`() {
        assertTrue("GameBoard.kt vacío: el parseo está roto", source.length > 10_000)
        assertTrue(
            "No encuentro el panel del tablero (`background(boardBg, boardShape)`): si se ha " +
                "renombrado, actualiza esta prueba en vez de borrarla",
            source.contains("background(boardBg, boardShape)")
        )
    }

    @Test
    fun `el Box que pinta el panel tiene alto propio`() {
        // La cadena de modificadores del panel: desde `modifier = modifier` hasta el `background`.
        val chain = source.substringBefore("background(boardBg, boardShape)")
            .substringAfterLast("modifier = modifier")
        assertTrue(
            "El Box que pinta el panel del tablero ha perdido su `.height(boardH)`. Sin alto propio " +
                "vuelve a medir lo que mida su hijo más alto, y cualquier capa de VFX con " +
                "`fillMaxSize()` lo estira hasta tapar la bandeja de fichas (bug del 2026-08-26e).",
            chain.contains(".height(boardH)")
        )
    }

    @Test
    fun `las capas del tablero siguen pudiendo dibujar fuera`() {
        // El arreglo es de TAMAÑO, no de recorte: los bloques que el MEGA despide y los textos que
        // suben por encima del borde tienen que seguir saliéndose. Si alguien "remata" el arreglo
        // añadiendo un clip a este Box, se lleva por delante media docena de efectos.
        val chain = source.substringBefore("background(boardBg, boardShape)")
            .substringAfterLast("modifier = modifier")
        assertTrue(
            "El Box del panel ha ganado un recorte: los VFX que dibujan fuera del tablero " +
                "(bloques despedidos por el MEGA, textos flotantes) quedarían cortados.",
            !chain.contains("clipToBounds") && !chain.contains(".clip(")
        )
    }

    private companion object {
        const val GAME_BOARD = "app/src/main/java/com/cukiti/teikafusion/ui/GameBoard.kt"
    }
}
