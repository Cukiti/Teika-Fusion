package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **LOS INDULTOS DE LA DERROTA** (C-7 y C-8, auditoría de la derrota 2026-09-03).
 *
 * `checkGameOver` puede decir «esto todavía no es derrota» y callarse. Cada vez que lo hace está
 * apostando a que al jugador le queda una salida — y si la apuesta es falsa, el resultado no es un
 * juego indulgente: es una partida **congelada sin un solo mensaje**. La ficha rebota, no sale ni el
 * aviso ni la oferta de revivir, y la única puerta es el menú de pausa.
 *
 * Los dos indultos que había estaban mal medidos:
 *
 *  - **C-7 · la bobina.** Perdonaba la derrota con solo existir una bobina en el tablero
 *    (`any { coil > 0 }`), pero descargarla exige que **STORM sea el bioma primario**: lo exigen
 *    tanto `GameViewModel.onBoardTap` como la capa táctil del tablero (`boardTapActive`). Una bobina
 *    sin Tormenta es adorno, y el indulto se volvía perpetuo. Esta prueba fija que los **tres**
 *    sitios midan lo mismo, que es justo lo que no pasaba.
 *  - **C-7 bis · la ventana de gracia que no existía.** `gameOverSuspendedUntil` solo se escribía
 *    para ponerlo a `0L`; nada lo encendía nunca. El campo era estado muerto y su comentario
 *    describía una mecánica viva — la trampa de `usesNewEngine`, otra vez. Aquí se comprueba que no
 *    ha vuelto a colarse.
 *
 * (C-8, la bandeja vacía, no se comprueba desde el código fuente sino desde la regla: se cubre en
 * `ensureTrayNotEmpty`, cuyo caso legítimo —la lección de la inclinación— vive en el tutorial.)
 */
class DefeatReprieveContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, MAIN_SRC + "model/GameEvent.kt").isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    private val viewModelSource: String by lazy {
        File(repoRoot, MAIN_SRC + "viewmodel/GameViewModel.kt").readText()
    }
    private val boardSource: String by lazy {
        File(repoRoot, MAIN_SRC + "ui/GameBoard.kt").readText()
    }

    @Test
    fun `el indulto de la bobina exige que la Tormenta sea el bioma primario`() {
        // El bloque que perdona la derrota por circuito sin descargar.
        val reprieve = viewModelSource
            .substringAfter("if (rulesState.biomePrimary ==", missingDelimiterValue = "")
            .substringBefore(") return false")
        assertTrue(
            "El indulto por bobina ya no comprueba el bioma primario. Sin esa condición vuelve a ser " +
                "perpetuo: una bobina que nadie puede tocar impide para siempre declarar la derrota " +
                "(C-7). Lo que lo hace resoluble es poder TOCARLO, y tocar exige STORM primario.",
            reprieve.contains("BiomeType.STORM") && reprieve.contains("coil > 0")
        )
    }

    @Test
    fun `los tres sitios que deciden si una bobina se puede tocar miden lo mismo`() {
        // 1. El despachador del toque.
        assertTrue(
            "GameViewModel.onBoardTap debe seguir exigiendo STORM para despachar a tapCoil.",
            viewModelSource.substringAfter("fun onBoardTap(").substringBefore("fun ")
                .contains("BiomeType.STORM")
        )
        // 2. tapCoil, que es quien acepta o rechaza el toque.
        assertTrue(
            "tapCoil debe seguir rechazando el toque fuera de la Tormenta.",
            viewModelSource.substringAfter("fun tapCoil(").substringBefore("// Bobinas de UNA fila")
                .contains("stormActive")
        )
        // 3. Y la capa táctil del tablero, que es la que existe o no existe bajo el dedo.
        assertTrue(
            "GameBoard solo monta la capa de toque con la Tormenta activa (boardTapActive).",
            boardSource.contains("val boardTapActive = stormActive")
        )
    }

    @Test
    fun `la ventana de gracia sin game-over no ha vuelto`() {
        val vivos = listOf(
            MAIN_SRC + "model/GameState.kt" to "declaración del campo",
            MAIN_SRC + "viewmodel/GameViewModel.kt" to "escritura o lectura"
        ).filter { (path, _) ->
            File(repoRoot, path).readText()
                .lineSequence()
                // El aviso escrito en el comentario del retiro sí puede nombrarla.
                .any { it.contains("gameOverSuspendedUntil") && !it.trimStart().startsWith("//") }
        }
        assertEquals(
            "`gameOverSuspendedUntil` volvió al código. Solo vale si alguien la ENCIENDE de verdad: " +
                "la versión anterior únicamente se ponía a 0L, así que suprimía la derrota jamás y " +
                "su comentario prometía una gracia de 15 s que no existía.",
            emptyList<Pair<String, String>>(),
            vivos
        )
    }

    private companion object {
        const val MAIN_SRC = "app/src/main/java/com/cukiti/teikafusion/"
    }
}
