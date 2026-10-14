package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * EL CENSO DE LOS AJUSTES (bloque 24, 2026-07-31) — tercero de la familia, tras
 * `GameEventContractTest` y `SoundCueContractTest`.
 *
 * Este proyecto tiene una enfermedad documentada: **el sistema completo e invisible**. El rango del
 * jugador se calculaba, se persistía y viajaba a la nube sin un solo píxel en pantalla (it. 35); la
 * racha diaria igual (it. 41); y esta misma pasada encontró el **mute maestro sin ningún botón** —
 * un guardado con `is_muted = true` dejaba el juego mudo para siempre.
 *
 * `PreferencesManager` es Android puro (DataStore), así que la prueba lee el FUENTE y fija el
 * mínimo verificable sin dispositivo: **toda clave de preferencias se lee Y se escribe**. Una que
 * solo se escribe es un ajuste que no hace nada; una que solo se lee, un ajuste que nadie puede
 * cambiar. Las excepciones se declaran aquí con su motivo, no se toleran en silencio.
 */
class PreferencesKeysContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, PREFS).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    private val source: String by lazy { File(repoRoot, PREFS).readText() }

    private val keys: List<String> by lazy {
        Regex("""val (\w+_KEY) = \w+PreferencesKey""").findAll(source).map { it.groupValues[1] }.toList()
    }

    @Test
    fun `el censo encuentra las claves`() {
        assertTrue("Solo ${keys.size} claves: el parseo está roto", keys.size >= 40)
        assertTrue("MUTE_KEY" in keys)
        assertTrue("VIBRATION_ENABLED_KEY" in keys)
    }

    @Test
    fun `toda clave se lee y se escribe`() {
        // LEGADO A PROPÓSITO: los dos on/off viejos de audio ya no se escriben — los sustituyeron los
        // sliders de volumen. Se siguen LEYENDO solo para migrar a quien tenga el audio apagado desde
        // una versión antigua (`?: if (preferences[MUSIC_ENABLED_KEY] == false) 0f else 0.6f`).
        val legacyReadOnly = setOf("MUSIC_ENABLED_KEY", "SFX_ENABLED_KEY")

        val suspicious = keys.filterNot { it in legacyReadOnly }.filter { key ->
            // 1 = la declaración. Con lectura y escritura tienen que ser al menos 3 apariciones.
            Regex("""\b$key\b""").findAll(source).count() < 3
        }
        assertTrue(
            "AJUSTES FANTASMA (clave que solo se lee o solo se escribe): $suspicious",
            suspicious.isEmpty()
        )
    }

    @Test
    fun `el nombre visible se recorta al leer y al escribir`() {
        // Regresión de la it. 49 y del rediseño de perfil: un nombre largo que entra por la nube o
        // por el proveedor rompe el layout del ranking. El cerrojo tiene que estar en LOS DOS lados.
        assertEquals(10, MAX_DISPLAY_NAME_LENGTH)
        val clamps = Regex("""take\(MAX_DISPLAY_NAME_LENGTH\)""").findAll(source).count()
        assertTrue("Falta el recorte del nombre en lectura o en escritura (encontrados: $clamps)", clamps >= 2)
    }

    private companion object {
        const val PREFS = "app/src/main/java/com/cukiti/teikafusion/model/PreferencesManager.kt"
    }
}
