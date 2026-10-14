package com.cukiti.teikafusion

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **CONSTRUIR LA RONDA NO PUEDE COSTAR UN FOTOGRAMA** (P-173, 2026-09-04).
 *
 * `GameState.initial` es la función más cara del juego: siembra el tablero del nivel, su regla de la
 * casa, la bandeja de apertura y el reparto de mecánicas. Medida en el ANR del 2026-08-24: ~26 ms en
 * JVM, ~100 ms en un móvil. Llamarla dentro de un `onClick` es exactamente el tirón que el jugador
 * lee como «la app se ha quedado pillada» — y el juego tiene **cuatro** botones que empiezan ronda.
 *
 * Por eso desde P-54 la ronda se **adelanta** mientras hay una ventana en pantalla y el jugador está
 * mirando otra cosa: el panel de JUGAR NIVEL, la ventana de victoria, la pausa y la derrota. Las
 * llamadas directas que quedan son respaldos legítimos —si lo adelantado no vale, hay que construir
 * ahora— y el arranque del ViewModel, que no es una ronda sino el estado vacío del SPLASH.
 *
 * Esta prueba existe porque el fallo ya se coló una vez **después** de tener la solución:
 * `prepareRound` llevaba semanas escrito cuando `nextLevel` seguía construyendo la ronda dentro del
 * botón, y nadie lo vio hasta la 44ª pasada. Nada avisa de esto: compila, pasa los tests y solo se
 * nota con el móvil en la mano.
 *
 * Si añades un camino nuevo que empiece ronda, declara aquí su función y di por qué puede construir
 * en el sitio — o, mejor, adelántala como los otros cuatro.
 */
class RoundBuildContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, VIEWMODEL).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    /** Función que llama a `GameState.initial` → por qué se le permite. */
    private val allowedCallers = mapOf(
        "_gameState" to "arranque del ViewModel: el estado vacío del SPLASH, no una ronda",
        "prepareRoundInternal" to "ES el adelanto: construye fuera del hilo principal y a destiempo",
        "startGame" to "respaldo si lo adelantado no vale (otro compañero, otro trato)",
        "nextLevel" to "respaldo si lo adelantado no vale",
        "resetProgress" to "borrón de progreso: vuelve al estado vacío, no empieza ronda"
    )

    @Test
    fun `nadie construye la ronda por su cuenta`() {
        val source = File(repoRoot, VIEWMODEL).readText()
        val lines = source.lines()

        val callers = lines.mapIndexedNotNull { index, line ->
            if (!line.contains("GameState.initial(")) null
            else {
                // La función que contiene la llamada: la declaración `fun`/`val` más cercana hacia
                // arriba en el margen del cuerpo de la clase.
                val owner = (index downTo 0).firstNotNullOfOrNull { i ->
                    Regex("""^\s{0,4}(?:private |internal )?(?:fun|val) (\w+)""")
                        .find(lines[i])?.groupValues?.get(1)
                }
                owner ?: "«no he sabido de quién es la línea ${index + 1}»"
            }
        }.distinct()

        assertTrue("No he encontrado ninguna llamada: el parseo está roto", callers.isNotEmpty())

        val undeclared = callers.filterNot { it in allowedCallers }
        assertTrue(
            "Funciones que construyen la ronda y no están declaradas: $undeclared — `GameState." +
                "initial` cuesta ~100 ms en un móvil, así que dentro de un `onClick` es un tirón " +
                "visible. Adelántala con `prepareRound` mientras haya una ventana en pantalla, o " +
                "declara aquí por qué este camino puede construir en el sitio.",
            undeclared.isEmpty()
        )

        // Y al revés: un permiso escrito para una función que ya no llama miente igual que un
        // pendiente falso. Es lo que pasó con las reglas de R8 que apuntaban a un paquete vacío.
        val stale = allowedCallers.keys.filterNot { it in callers }
        assertTrue("Permisos escritos para funciones que ya no construyen la ronda: $stale", stale.isEmpty())
    }

    private companion object {
        const val VIEWMODEL = "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt"
    }
}
