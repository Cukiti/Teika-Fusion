package com.cukiti.teikafusion

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **LO QUE EL SHRINKER NO VE** (P-26, bloque 26, 2026-07-31).
 *
 * Un recurso resuelto por NOMBRE (`Resources.getIdentifier`) es invisible para el shrinker de
 * release: no hay ninguna referencia `R.algo.x` que seguir, así que lo borra del AAB. El resultado
 * es siempre el mismo y siempre caro: **debug funciona, release no** — y no hay forma de verlo sin
 * publicar.
 *
 * Ya ha pasado **tres veces** en este proyecto:
 *  · `default_web_client_id` → el login de Google moría en release;
 *  · `google_crash_reporting_api_key` → Crashlytics;
 *  · `abyss_teaser` → el adelanto de «El Abismo» no estaba dentro del AAB (2026-07-31), y como el
 *    código interpretaba "recurso ausente" como "aún no se ha añadido el vídeo", fallaba en
 *    silencio. (Ese teaser se eliminó del juego, vídeo incluido; queda como aviso.)
 *
 * Cazar esto a mano no funciona. Esta prueba lee el fuente, saca **todos** los nombres que se
 * resuelven por `getIdentifier` y exige que estén en `res/raw/keep.xml`.
 */
class ShrinkerKeepContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, KEEP).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    @Test
    fun `todo recurso resuelto por nombre esta protegido en keep punto xml`() {
        val keep = File(repoRoot, KEEP).readText()
        val sources = File(repoRoot, MAIN_SRC).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        // getIdentifier("nombre", "tipo", …) — el nombre y el tipo son los dos primeros literales.
        val pattern = Regex("""getIdentifier\(\s*"([^"]+)"\s*,\s*"([^"]+)"""")
        val referenced = sources.flatMap { file ->
            pattern.findAll(file.readText()).map { it.groupValues[1] to it.groupValues[2] }
        }.distinct()

        assertTrue(
            "No he encontrado ningún getIdentifier: el parseo está roto (o desapareció el patrón)",
            referenced.isNotEmpty()
        )

        val unprotected = referenced.filterNot { (name, type) ->
            // Vale la entrada exacta (@string/project_id) o un comodín que la cubra (@raw/snd_*).
            keep.contains("@$type/$name") ||
                Regex("""@$type/(\w*)\*""").findAll(keep).any { m ->
                    name.startsWith(m.groupValues[1])
                }
        }
        assertTrue(
            "Recursos que el shrinker BORRARÁ del release (se resuelven por nombre y no están en " +
                "res/raw/keep.xml): $unprotected",
            unprotected.isEmpty()
        )
    }

    @Test
    fun `el keep protege lo que ya se perdio una vez`() {
        // Red de seguridad explícita: estas entradas costaron un bug de producción cada una.
        val keep = File(repoRoot, KEEP).readText()
        listOf("default_web_client_id", "google_crash_reporting_api_key").forEach {
            assertTrue("Falta $it en keep.xml — ya se perdió una vez", keep.contains(it))
        }
    }

    /**
     * **Y EL NOMBRE DE UNA CLASE, TAMBIÉN** (2026-09-04).
     *
     * Hermano del de arriba, un piso más abajo: donde aquel protege recursos resueltos por nombre,
     * este protege **clases comparadas por nombre**. Es el mismo fallo con otra cara —debug funciona,
     * release no, y no hay forma de verlo sin publicar—, y ya ha ocurrido una vez:
     * `MechanicCapsule.biomeJustStruck` compara `javaClass.simpleName` contra el literal que declara
     * `BiomeRhythm` ("KingWallRise", "GluttonBite"…). R8 renombra la clase —medido en el mapping del
     * AAB real: `GameEvent$KingWallRise -> og1`—, así que la comparación era falsa **siempre** en la
     * app publicada y cierta **siempre** en el móvil de desarrollo. Las ocho cápsulas que explican el
     * castigo de cada bioma no salían nunca.
     *
     * Si añades una comparación de `simpleName` en otro sitio, decláralo aquí con el tipo cuyo
     * nombre comparas y añade su `-keepnames` — o R8 te lo romperá sin decir nada.
     */
    @Test
    fun `toda clase comparada por su nombre sobrevive a R8`() {
        val rules = File(repoRoot, RULES).readText()
        val sources = File(repoRoot, MAIN_SRC).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .toList()

        // Comparación, no log: `simpleName ==` o `== algo.simpleName`.
        val comparison = Regex("""simpleName\s*==|==\s*[\w.]+\.simpleName""")
        val comparing = sources.filter { comparison.containsMatchIn(it.readText()) }.map { it.name }

        val undeclared = comparing.filterNot { it in nameComparedTypes }
        assertTrue(
            "Ficheros que comparan un `simpleName` en runtime y no están declarados: $undeclared — " +
                "R8 renombra esas clases en release y la comparación deja de ser cierta PARA " +
                "SIEMPRE, solo en la app publicada. Declara aquí el tipo y protégelo con -keepnames.",
            undeclared.isEmpty()
        )

        val unprotected = nameComparedTypes
            .filterKeys { it in comparing }
            .values.distinct()
            .filterNot { type -> Regex("""-keepnames class .*\b$type\b""").containsMatchIn(rules) }
        assertTrue(
            "Tipos cuyo nombre se compara en runtime y NO tienen -keepnames en proguard-rules.pro: " +
                "$unprotected",
            unprotected.isEmpty()
        )
    }

    /**
     * Fichero → tipo cuyo `simpleName` compara. Ver el test de arriba.
     *
     * **Hoy está vacío, y esa es la buena noticia** (P-176, 2026-09-04): el único que había
     * —`MechanicCapsule`, comparando el nombre del evento de impacto— dejó de compararlo y ahora
     * pregunta por el TIPO (`BiomeRhythm.isImpact`), que el compilador comprueba y R8 no puede
     * romper. El test se queda montado porque la trampa sigue siendo fácil de pisar: en cuanto
     * alguien vuelva a comparar un `simpleName`, saltará pidiendo su `-keepnames`.
     */
    private val nameComparedTypes = emptyMap<String, String>()

    private companion object {
        const val RULES = "app/proguard-rules.pro"
        const val MAIN_SRC = "app/src/main/java/com/cukiti/teikafusion/"
        const val KEEP = "app/src/main/res/raw/keep.xml"
    }
}
