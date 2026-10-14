package com.cukiti.teikafusion.ui

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * **TODO AUDIO DEL JUEGO TIENE LICENCIA REGISTRADA** (2026-09-25, pedido del usuario).
 *
 * `docs/audio/licencias/registro.json` apunta, por cada archivo de `res/raw`, de dónde sale y bajo qué
 * licencia. Este test impide las tres formas de que el registro mienta sin que nadie se entere:
 *
 *  1. un audio nuevo en `res/raw` sin entrada;
 *  2. un audio cambiado (su SHA-256 ya no es el registrado) — el origen o el procesado pueden ser otros;
 *  3. una entrada con un origen fuera de la lista permitida (ver el README del registro).
 *
 * Si falla por (1) o (2), no se «arregla» el test: se registra el audio con su licencia de verdad.
 */
class AudioLicenseRegistryTest {

    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    // 2026-09-29 (decisión del usuario): `stable-audio-open` SALE de la lista. Sus 34 efectos se retiraron del
    // juego y no se vuelve a usar; el test impide que entre uno sin querer. Ver el README del registro.
    private val allowedOrigins = setOf(
        "propio-ffmpeg", "elevenlabs", "elevenlabs-derivado", "kenney", "sonniss", "mezcla",
    )

    private fun sha256(f: File): String =
        MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    @Test
    fun `cada audio de res raw esta registrado con su hash y un origen permitido`() {
        val json = Json.parseToJsonElement(File(root, "docs/audio/licencias/registro.json").readText()).jsonObject
        val registry = json.getValue("entradas").jsonArray.map { it.jsonObject }.associate { e ->
            e.getValue("archivo").jsonPrimitive.content to e
                .filterValues { it is kotlinx.serialization.json.JsonPrimitive }
                .mapValues { it.value.jsonPrimitive.content }
        }
        val audio = File(root, "app/src/main/res/raw").listFiles()!!
            .filter { it.extension.lowercase() in setOf("ogg", "mp3", "wav") }
        assertTrue("El censo no encuentra audio: la ruta está mal", audio.size > 50)

        val problems = mutableListOf<String>()
        audio.forEach { f ->
            val e = registry[f.name]
            when {
                e == null -> problems += "${f.name}: sin entrada en el registro de licencias"
                e["sha256"] != sha256(f) -> problems += "${f.name}: cambió y su registro no"
                e["origen"] !in allowedOrigins -> problems += "${f.name}: origen «${e["origen"]}» no permitido"
            }
        }
        registry.keys.filter { name -> audio.none { it.name == name } }
            .forEach { problems += "$it: registrado pero ya no está en res/raw" }
        assertTrue("Registro de licencias de audio desfasado:\n" + problems.joinToString("\n"), problems.isEmpty())
    }
}
