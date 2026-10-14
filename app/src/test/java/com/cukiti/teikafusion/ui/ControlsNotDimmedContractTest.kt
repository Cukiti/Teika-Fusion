package com.cukiti.teikafusion.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **UNA CELEBRACIÓN NO PUEDE APAGAR LOS MANDOS** (2026-08-26c) — hermano de
 * `ImmersiveWindowContractTest`, `GameEventContractTest` y `SoundCueContractTest`.
 *
 * Bug reportado por el usuario: *«cuando hago un combo largo el panel de fichas se quita»*. No se
 * quitaba — se apagaba, por **dos atenuaciones que se multiplicaban**:
 *
 *  1. `boardBusy` atenúa la bandeja mientras el tablero resuelve el turno, y en una cascada larga eso
 *     son varios segundos (`resolveBoardChain` espera 360 ms por pasada).
 *  2. `EpicComboVFX` —que solo entra a partir de `EPIC_COMBO_TIER`, o sea **solo en combos largos**—
 *     pinta un scrim radial `Transparent → negro` a pantalla completa. En el CENTRO es transparente,
 *     pero en las **orillas llega a negro puro**… y la bandeja vive en la orilla de abajo.
 *
 * 0,55 × (1 − 0,60) ≈ **0,22**: la bandeja al 22 % durante ~1,5 s se lee como «ha desaparecido».
 *
 * Lo que fija esta prueba es la lección, no los números: **el apagón a pantalla completa tiene que
 * respetar la banda de mandos** (`protectedBottomPx`), y **la atenuación de estado no puede ser tan
 * agresiva** como para que cualquier cosa que se pinte encima la remate. Las dos son reglas que el
 * compilador no puede defender y que se rompen solas en cuanto alguien añada el siguiente efecto a
 * pantalla completa.
 */
class ControlsNotDimmedContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, GAME_SCREEN).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    private val gameScreen: String by lazy { File(repoRoot, GAME_SCREEN).readText() }
    private val vfx: String by lazy { File(repoRoot, VFX).readText() }

    @Test
    fun `el censo encuentra las fuentes`() {
        assertTrue("GameScreen.kt vacío: el parseo está roto", gameScreen.length > 10_000)
        assertTrue("VFX.kt vacío: el parseo está roto", vfx.length > 10_000)
    }

    @Test
    fun `el scrim del combo epico respeta la banda de mandos`() {
        assertTrue(
            "EpicComboVFX ha perdido `protectedBottomPx`: su scrim vuelve a llegar a negro puro " +
                "sobre la bandeja de fichas",
            vfx.contains("protectedBottomPx")
        )
        assertTrue(
            "El scrim ya no se recorta: sin `clipRect` vuelve a pintar el apagón sobre los mandos",
            vfx.contains("clipRect(bottom = featherTop)")
        )
        assertTrue(
            "GameScreen ya no le pasa la banda protegida a EpicComboVFX: el parámetro existe pero " +
                "vale 0, que es el comportamiento antiguo",
            // `[\s\S]` y no `(?:.|\n)`: los fuentes van con finales de línea de Windows y en Java
            // `.` tampoco casa `\r`, así que el salto entre líneas rompía la búsqueda.
            Regex("""EpicComboVFX\([\s\S]*?protectedBottomPx\s*=""").containsMatchIn(gameScreen)
        )
    }

    @Test
    fun `la bandeja no se atenua por debajo del minimo legible`() {
        // El valor exacto es un dial de playtest; lo que NO puede volver a pasar es que baje tanto
        // que cualquier capa dibujada encima la borre. 0,7 es el suelo acordado.
        val match = Regex("""targetValue = if \(boardBusy\) (0\.\d+)f else 1f""").find(gameScreen)
        assertTrue(
            "No encuentro la atenuación de la bandeja por `boardBusy` en GameScreen: si se ha " +
                "renombrado, actualiza esta prueba en vez de borrarla",
            match != null
        )
        val alpha = match!!.groupValues[1].toFloat()
        assertTrue(
            "La bandeja se atenúa a $alpha con el tablero ocupado: por debajo de $MIN_TRAY_ALPHA " +
                "cualquier efecto a pantalla completa la deja invisible (bug del combo largo)",
            alpha >= MIN_TRAY_ALPHA
        )
    }

    private companion object {
        const val GAME_SCREEN = "app/src/main/java/com/cukiti/teikafusion/ui/GameScreen.kt"
        const val VFX = "app/src/main/java/com/cukiti/teikafusion/ui/VFX.kt"
        const val MIN_TRAY_ALPHA = 0.7f
    }
}
