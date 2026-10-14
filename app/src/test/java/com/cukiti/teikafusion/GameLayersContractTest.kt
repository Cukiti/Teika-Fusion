package com.cukiti.teikafusion

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * P-436 · Las ventanas de la partida van en su NIVEL, no en su orden de declaración.
 *
 * En la caja principal de `GameScreen` pinta encima lo que se declara después, y dos capas de juego
 * declaradas tras las ventanas se pintaron sobre ellas (la enseñanza sobre «lección completada», el
 * banco sobre «nivel completado»). Las ventanas llevan ahora `zIndex(GameLayers.WINDOW)` y la
 * presentación de personaje `zIndex(GameLayers.REVEAL)`.
 *
 * Si este test falla, una ventana de estado ha perdido su nivel: vuelve a poder quedar debajo de
 * cualquier capa de juego que se escriba más abajo en el archivo. No lo «arregles» quitando la
 * comprobación: devuélvele el `zIndex`.
 */
class GameLayersContractTest {

    private val source: String by lazy {
        listOf("src/main/java/com/cukiti/teikafusion/ui/GameScreen.kt", "app/src/main/java/com/cukiti/teikafusion/ui/GameScreen.kt")
            .map(::File).first { it.exists() }.readText()
    }

    private fun assertWindow(opening: String, what: String) {
        assertTrue(
            "$what debe abrirse dentro de Box(Modifier.fillMaxSize().zIndex(GameLayers.WINDOW))",
            source.contains("$opening Box(Modifier.fillMaxSize().zIndex(GameLayers.WINDOW)) {")
        )
    }

    @Test
    fun `las ventanas de estado van en el nivel WINDOW`() {
        assertWindow("if (showOverlay)", "Fin de nivel / derrota / revivir")
        assertWindow("if (gameState.status == com.cukiti.teikafusion.model.GameStatus.PAUSED)", "La pausa")
        assertWindow("if (showQuitDialog)", "«¿Salir del nivel?»")
    }

    @Test
    fun `la presentacion de personaje va por encima de las ventanas`() {
        assertTrue(source.contains(".zIndex(GameLayers.REVEAL)"))
        val window = Regex("""const val WINDOW = ([0-9.]+)f""").find(source)!!.groupValues[1].toFloat()
        val reveal = Regex("""const val REVEAL = ([0-9.]+)f""").find(source)!!.groupValues[1].toFloat()
        assertTrue("REVEAL ($reveal) tiene que estar por encima de WINDOW ($window)", reveal > window && window > 0f)
    }
}
