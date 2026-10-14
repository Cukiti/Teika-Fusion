package com.cukiti.teikafusion

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **UN PERMISO PELIGROSO QUE NADIE PIDE ES UNA FUNCIÓN MUERTA** (P-168, 2026-09-04).
 *
 * Este test nace de un fallo real y caro: el juego declaraba `POST_NOTIFICATIONS` en el manifiesto,
 * creaba su canal, tenía **tres avisos de reenganche** escritos y su `WorkManager` programándolos en
 * cada `onPause`… y en todo el repositorio **no había una sola petición del permiso**. En Android
 * 13+ eso es denegado por defecto, así que `NotificationHelper.show` volvía sin hacer nada: el
 * sistema entero llevaba meses mudo en la mayoría de los móviles, y también se tragaba las
 * notificaciones remotas.
 *
 * Lo peor es que **nada fallaba**. Compilaba, los tests pasaban, `lint` callaba y WorkManager
 * reportaba `Result.success()` —porque el worker sí corre; lo que no hace es publicar—. Es
 * exactamente la especie que este repositorio caza con *contract tests* que leen el código fuente:
 * eventos sin productor, recursos leídos por nombre, `Dialog` prohibidos, progreso que no viaja a la
 * nube. Este es el mismo animal, un piso más abajo.
 *
 * La regla: **si el manifiesto declara un permiso de nivel peligroso, alguna fuente Kotlin tiene que
 * pedirlo en runtime.** Declarar sin pedir es la firma del bug.
 */
class PermissionContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, MANIFEST).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    /**
     * Permisos de nivel **peligroso** (los que exigen consentimiento en runtime desde Android 6, y
     * `POST_NOTIFICATIONS` desde el 13). Es una lista curada a mano a propósito: el nivel de
     * protección no vive en ningún fichero del proyecto, así que no hay de dónde derivarlo. Si algún
     * día se añade uno que no esté aquí, añádelo — el coste de olvidarlo es que este test lo deje
     * pasar, que es justo lo que ya ocurrió sin el test.
     */
    private val dangerous = setOf(
        "POST_NOTIFICATIONS",
        "CAMERA", "RECORD_AUDIO",
        "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION",
        "READ_CONTACTS", "WRITE_CONTACTS", "GET_ACCOUNTS",
        "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE",
        "READ_MEDIA_IMAGES", "READ_MEDIA_VIDEO", "READ_MEDIA_AUDIO",
        "READ_CALENDAR", "WRITE_CALENDAR",
        "CALL_PHONE", "READ_PHONE_STATE", "SEND_SMS", "READ_SMS", "RECEIVE_SMS",
        "BODY_SENSORS", "ACTIVITY_RECOGNITION",
        "BLUETOOTH_CONNECT", "BLUETOOTH_SCAN", "NEARBY_WIFI_DEVICES"
    )

    @Test
    fun `todo permiso peligroso del manifiesto lo pide alguien en runtime`() {
        val manifest = File(repoRoot, MANIFEST).readText()
        val declared = Regex("""uses-permission android:name="android\.permission\.(\w+)"""")
            .findAll(manifest)
            .map { it.groupValues[1] }
            .toList()
        assertTrue("Parseo del manifiesto roto (${declared.size} permisos)", declared.size >= 3)

        // Fuentes que piden permisos: las que saben lanzar la petición del sistema. Se busca el par
        // (fichero que pide, permiso nombrado en él) para que nombrarlo en un `checkSelfPermission`
        // suelto —que es lo que había— no cuente como pedirlo.
        val askers = File(repoRoot, MAIN_SRC).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .map { it.readText() }
            .filter { it.contains("RequestPermission") || it.contains("requestPermissions") }
            .toList()

        val declaredDangerous = declared.filter { it in dangerous }
        val neverAsked = declaredDangerous.filterNot { perm ->
            askers.any { it.contains("permission.$perm") }
        }
        assertTrue(
            "Permisos PELIGROSOS declarados que nadie pide en runtime: $neverAsked — en Android " +
                "moderno eso es 'denegado por defecto', así que la función que dependa de ellos está " +
                "muerta en el móvil del jugador aunque todo compile y los tests pasen. O se pide, o " +
                "se quita del manifiesto.",
            neverAsked.isEmpty()
        )
    }

    private companion object {
        const val MANIFEST = "app/src/main/AndroidManifest.xml"
        const val MAIN_SRC = "app/src/main/java"
    }
}
