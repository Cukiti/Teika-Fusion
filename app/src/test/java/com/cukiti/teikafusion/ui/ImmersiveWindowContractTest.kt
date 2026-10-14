package com.cukiti.teikafusion.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **LA DECISIÓN CONGELADA MÁS FÁCIL DE ROMPER, automatizada** (bloque 23, 2026-07-31) — hermano de
 * `GameEventContractTest`, `SoundCueContractTest` y `ShrinkerKeepContractTest`.
 *
 * Teika Fusion corre en **modo inmersivo** (`enableEdgeToEdge` + barras ocultas). Cualquier
 * composable que abra una **VENTANA propia del sistema** —`Dialog`, `Popup`, `AlertDialog`,
 * `ModalBottomSheet`, `DropdownMenu`— hace reaparecer las barras de estado y navegación mientras
 * está en pantalla, y al recuperarlas **el layout entero se desplaza de golpe**; al cerrarse, vuelve
 * a saltar. Por eso todos los overlays del juego viven en la jerarquía normal.
 *
 * Es una regla que el compilador no puede defender y que ya se rompió: la auditoría del bloque 23
 * encontró **cinco `AlertDialog` vivos** (los cuatro de Ajustes —cambiar nombre, cerrar sesión,
 * restablecer progreso, eliminar cuenta— y el resultado del borrado de cuenta), todos en la pantalla
 * que más se abre. Se sustituyeron por [ConfirmOverlay]. Este test existe para que no vuelvan una
 * tercera vez: en una app inmersiva, el "diálogo estándar" es siempre lo primero que alguien añade.
 *
 * Como el resto de censos del repo, lee el CÓDIGO FUENTE (estos componentes no se pueden instanciar
 * en la JVM). Si algún día hace falta una ventana real, la excepción se documenta AQUÍ, no en el
 * sitio donde se cuela.
 */
class ImmersiveWindowContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, APP_NAVIGATION).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    private val sources: List<File> by lazy {
        File(repoRoot, MAIN_SRC).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()
    }

    @Test
    fun `el censo encuentra las fuentes`() {
        // Red de seguridad del propio parseo: si se rompe, el resto pasaría en vacío.
        assertTrue("Solo he encontrado ${sources.size} fuentes: el parseo está roto", sources.size >= 30)
    }

    @Test
    fun `ninguna pantalla abre una ventana del sistema`() {
        val offenders = mutableListOf<String>()
        for (file in sources) {
            file.readLines().forEachIndexed { i, line ->
                // Solo código: los comentarios (y este mismo contrato) nombran los componentes.
                val code = line.substringBefore("//").trim()
                if (code.isEmpty() || code.startsWith("*")) return@forEachIndexed
                for (banned in BANNED) {
                    // Llamada o import: las dos formas de meterlo en el árbol. `\b` a propósito, para
                    // cazar también la llamada CUALIFICADA (`androidx.compose.material3.AlertDialog(`),
                    // que es como se cuelan la mitad de las cosas en este repo. No da falso positivo
                    // con `QuitDialogOverlay(` ni `showDialog(`: ahí no hay frontera de palabra.
                    if (Regex("""\b$banned\s*\(""").containsMatchIn(code) ||
                        Regex("""^import .*\.$banned$""").containsMatchIn(code)
                    ) {
                        offenders += "${file.name}:${i + 1}  $banned"
                    }
                }
            }
        }
        assertTrue(
            "VENTANAS DEL SISTEMA en una app INMERSIVA (sacan las barras y EMPUJAN el layout; " +
                "usa ConfirmOverlay o un Box en la jerarquía normal): $offenders",
            offenders.isEmpty()
        )
    }

    private companion object {
        const val MAIN_SRC = "app/src/main/java/com/cukiti/teikafusion/"
        const val APP_NAVIGATION = MAIN_SRC + "ui/AppNavigation.kt"

        /** Todo lo que Compose renderiza en una ventana aparte del contenido de la Activity. */
        val BANNED = listOf(
            "AlertDialog", "BasicAlertDialog", "Dialog", "Popup",
            "ModalBottomSheet", "DropdownMenu", "ExposedDropdownMenuBox"
        )
    }
}
