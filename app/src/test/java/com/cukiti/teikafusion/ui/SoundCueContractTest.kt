package com.cukiti.teikafusion.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * EL CENSO DEL AUDIO, automatizado (bloque 22, 2026-07-31) — hermano de `GameEventContractTest`.
 *
 * El catálogo de sonidos sufre la misma enfermedad que la cola de eventos: cuando se retira una
 * mecánica, su `play*` y su `.mp3` **sobreviven**. Y aquí el fósil no es solo confuso, **pesa**: la
 * muestra sigue empaquetada en el APK y se carga en el `SoundPool` en cada arranque. Esta pasada
 * encontró `playConnectorLink` (ficha CONECTORA, muerta desde el rework de hormigón).
 *
 * `SoundManager` no se puede instanciar en la JVM (SoundPool/MediaPlayer son de Android), así que
 * —como el censo de eventos— estas pruebas leen el CÓDIGO FUENTE y el directorio de recursos:
 *
 * 1. Todo `fun play*` tiene al menos un llamante fuera de `SoundManager.kt`.
 * 2. Toda muestra `snd_*` de `res/raw` está referenciada por `SoundManager` (nada muerto en el APK).
 */
class SoundCueContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, SOUND_MANAGER).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    private val soundManagerSource: String by lazy { File(repoRoot, SOUND_MANAGER).readText() }

    private val cues: List<String> by lazy {
        Regex("""fun (play[A-Za-z0-9_]+)\(""").findAll(soundManagerSource)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
    }

    private val callerSources: String by lazy {
        File(repoRoot, MAIN_SRC).walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "SoundManager.kt" }
            .joinToString("\n") { it.readText() }
    }

    @Test
    fun `el censo encuentra el catalogo de cues`() {
        // Red de seguridad del propio parseo: si se rompe, el resto pasaría en vacío.
        assertTrue("Solo he encontrado ${cues.size} cues: el parseo está roto", cues.size >= 50)
        assertTrue("playClear" in cues)
        assertTrue("playMusic" in cues)
    }

    @Test
    fun `todo cue de sonido tiene quien lo dispare`() {
        val orphans = cues.filter { cue -> !Regex("""\.$cue\(""").containsMatchIn(callerSources) }
        assertTrue(
            "CUES FÓSILES (nadie los llama — o se cablean o se podan, con su .mp3): $orphans",
            orphans.isEmpty()
        )
    }

    @Test
    fun `toda muestra de res raw esta en el catalogo`() {
        val raw = File(repoRoot, RAW_DIR)
        assertTrue("No encuentro $RAW_DIR", raw.isDirectory)
        val samples = raw.listFiles()
            ?.filter { it.isFile && it.nameWithoutExtension.startsWith("snd_") }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
        assertTrue("No hay muestras snd_* en res/raw: el test no está mirando donde debe", samples.isNotEmpty())
        val unused = samples.filterNot { name ->
            Regex("""R\.raw\.$name\b""").containsMatchIn(soundManagerSource)
        }
        assertTrue(
            "MUESTRAS MUERTAS en el APK (nadie las carga; moverlas a art/_retirados/): $unused",
            unused.isEmpty()
        )
    }

    @Test
    fun `SoundPool instala el listener antes de cargar muestras`() {
        val listener = soundManagerSource.indexOf("setOnLoadCompleteListener")
        val firstLoad = soundManagerSource.indexOf("soundPool.load")
        assertTrue("El listener de SoundPool debe existir", listener >= 0)
        assertTrue("El listener debe instalarse antes del primer load", listener < firstLoad)
        assertTrue("Los disparos durante la carga deben quedar pendientes", soundManagerSource.contains("pendingPlays"))
        assertTrue("La cola debe conservar capas del mismo sample", soundManagerSource.contains("ConcurrentLinkedDeque"))
        assertTrue("La ronda debe poder invalidar audio diferido", soundManagerSource.contains("beginAudioContext"))
    }

    @Test
    fun `la musica puede recuperarse despues de un player fallido`() {
        assertTrue(soundManagerSource.contains("setOnErrorListener"))
        assertTrue(soundManagerSource.contains("prepareAsync()"))
        assertTrue(soundManagerSource.contains("currentMusicResId = 0"))
        assertTrue(
            "playMusic no debe salir solo por coincidir el enum si no hay player vivo",
            soundManagerSource.contains("bgmPlayer != null")
        )
    }

    @Test
    fun `la musica encadena un player preparado para repetir sin hueco`() {
        assertTrue(soundManagerSource.contains("setNextMediaPlayer(prepared)"))
        assertTrue(soundManagerSource.contains("prepareNextBgm(prepared, resId, generation)"))
        assertTrue(soundManagerSource.contains("player.isLooping = false"))
        assertTrue("No debe volver el loop interno con hueco", !soundManagerSource.contains("player.isLooping = true"))
    }

    @Test
    fun `cada bioma tiene musica propia instalada`() {
        val expected = mapOf(
            "CRYSTAL" to "bgm_crystal",
            "SLEEP" to "bgm_sleep",
            "FEAR" to "bgm_fear",
            "GARDEN" to "bgm_garden",
            "STORM" to "bgm_storm",
            "VOLCANO" to "bgm_volcano",
            "NINJA" to "bgm_ninja",
            "GLACIER" to "bgm_glacier",
            "GHOST" to "bgm_ghost",
            "GLUTTON" to "bgm_glutton",
            "TOXIC" to "bgm_toxic",
            "STAR" to "bgm_star",
            "KING" to "bgm_king",
            "GALACTIC" to "bgm_galactic",
        )
        val raw = File(repoRoot, RAW_DIR)
        val missingRoutes = expected.filterNot { (biome, resource) ->
            Regex("""BIOME_$biome\(resId\s*=\s*R\.raw\.$resource\)""")
                .containsMatchIn(soundManagerSource)
        }.keys
        val missingFiles = expected.values.filterNot { File(raw, "$it.ogg").isFile }
        assertTrue("Biomas sin recurso directo: $missingRoutes", missingRoutes.isEmpty())
        assertTrue("Músicas temáticas ausentes: $missingFiles", missingFiles.isEmpty())
    }

    @Test
    fun `el tic de puntuacion tiene proteccion contra duplicados`() {
        assertTrue(soundManagerSource.contains("lastScoreTickAtMs"))
        assertTrue(soundManagerSource.contains("SystemClock.uptimeMillis()"))
    }

    private companion object {
        const val MAIN_SRC = "app/src/main/java/com/cukiti/teikafusion/"
        const val SOUND_MANAGER = MAIN_SRC + "ui/SoundManager.kt"
        const val RAW_DIR = "app/src/main/res/raw"
    }
}
