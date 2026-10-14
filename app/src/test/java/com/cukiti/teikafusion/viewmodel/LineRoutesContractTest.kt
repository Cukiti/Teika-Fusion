package com.cukiti.teikafusion.viewmodel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **LAS DOS RUTAS QUE ROMPEN LÍNEAS PASAN POR LAS MISMAS REGLAS** (2026-09-22, decisión del usuario).
 *
 * El juego rompe filas por dos caminos: el pase normal (`resolveLineClearPass`) y la descarga del
 * circuito de la Tormenta (`detonateCircuitRows`). Las reglas de la casa de línea —vidriera, huevo,
 * piñata, pompas, barriles, rebote, implosión— se escribieron dentro del primero, y durante un día
 * la descarga rompía filas sin agrietar un vidrio ni golpear una piñata: la misma jugada valía
 * distinto según el camino.
 *
 * Ahora viven en funciones compartidas, y este test lee el código de las dos rutas y exige que las
 * dos las llamen. Si mañana alguien añade una regla de línea en una sola ruta, o una tercera ruta
 * que rompa filas, aquí es donde tiene que acordarse.
 */
class LineRoutesContractTest {

    private val source: String by lazy {
        var dir: File? = File("").absoluteFile
        val rel = "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt"
        while (dir != null && !File(dir, rel).isFile) dir = dir.parentFile
        File(requireNotNull(dir) { "No encuentro la raíz del repo" }, rel).readText()
    }

    /** El cuerpo de la función [name], de su declaración a la siguiente `private`/`fun` de su nivel. */
    private fun bodyOf(name: String): String {
        val start = source.indexOf("fun $name(")
        assertTrue("no encuentro $name", start >= 0)
        val next = Regex("""\n    (private |internal |suspend |fun |/\*\*)""").find(source, start + 10)
        return source.substring(start, next?.range?.first ?: source.length)
    }

    private val shared = listOf(
        "applyLineHouseRulesBeforeClear(",
        "applyLineHouseRulesAfterClear(",
        "applyLineRecoil(",
        "LineImplosion.triggers(",
    )

    @Test
    fun `el pase normal y la descarga de la tormenta usan las mismas reglas de linea`() {
        listOf("resolveLineClearPass", "detonateCircuitRows").forEach { ruta ->
            val body = bodyOf(ruta)
            shared.forEach { llamada ->
                assertTrue("$ruta no llama a $llamada: esa regla no valdría al romper por esa ruta", llamada in body)
            }
        }
    }
}
