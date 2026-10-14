package com.cukiti.teikafusion.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **UNA INTERACCIÓN, UN SONIDO** (2026-09-05) — el cuarto de la familia de contratos de audio,
 * detrás de `SoundCueContractTest`.
 *
 * El usuario reportó que `snd_ui_click` sonaba **dos veces** al equipar o desequipar un compañero y
 * al entrar a nivel desde la preparación, y pidió expresamente «sigue cazando dónde los efectos de
 * sonido se escuchen repetidas veces cuando el jugador hace una sola interacción». Eso es trabajo
 * del build, no del oído: esta prueba lo hace en **todo el catálogo**, no solo con el tic.
 *
 * No era un fallo del `SoundPool`: eran **dos capas creyendo que el sonido era suyo**.
 *
 * ```
 * onToggle = {
 *     onUiClick()                       // ← la PANTALLA suena
 *     onEquipCompanion(companion.id)    // ← y el VIEWMODEL vuelve a sonar dentro
 * }
 * ```
 *
 * ## La regla de la casa que fija esta prueba
 *
 * **El sonido pertenece al VERBO, no al botón.** Lo pone el `GameViewModel`, por dos motivos que no
 * son de estilo:
 *
 *  1. **Solo el verbo sabe si pasó algo.** `unequipCompanion` sale sin sonar si el compañero no
 *     estaba en el cinturón; `promoteToCaptain`, si ya era capitán. Sonar en el botón hace ruido
 *     también cuando no ocurre nada, que es peor que no sonar.
 *  2. **Un verbo, un sitio.** Equipar se toca desde la tarjeta de LA BANCA y desde la ranura del
 *     cinturón; si el sonido viviera en el botón habría que acordarse en los dos, y en el tercero
 *     que venga.
 *
 * Sonar desde la pantalla sigue siendo correcto para lo que **no** tiene verbo detrás: abrir una
 * ficha, cerrar un panel, pedir una confirmación. Nadie más los va a sonar.
 *
 * ## Cómo lo comprueba
 *
 * `SoundManager` es Android puro y esto son composables, así que —como el resto de la familia— lee
 * el CÓDIGO FUENTE. Cuatro pasos:
 *
 *  1. **Qué suena cada verbo**: por cada función de `GameViewModel`, los `soundManager.play*()` que
 *     contiene. Hoy son ~48 verbos con sonido, no solo los del tic.
 *  2. **Con qué nombre lo llama la UI**, leyendo el cableado real
 *     (`onPromoteCaptain = { cid -> viewModel.promoteToCaptain(cid) }`). *Primera trampa*: el nombre
 *     del parámetro **no** se deriva del verbo — `promoteToCaptain` se cablea como
 *     `onPromoteCaptain`, no como `onPromoteToCaptain`, así que adivinarlo dejaba pasar justo el
 *     caso que hay que cazar.
 *  3. **Qué suena cada parámetro por su cuenta** (`onUiClick = { soundManager.playUiClick() }`), para
 *     saber qué cue dispara una pantalla cuando llama a uno de ellos.
 *  4. **La ventana de cada disparo**: la línea que suena algo y las que hagan falta hasta cerrar sus
 *     llaves. Si el mismo cue lo dispara la pantalla **y** un verbo llamado desde esa ventana, es un
 *     doble. *Segunda trampa*: trocear el archivo entero por llaves no vale —las plantillas de texto
 *     (`"Ranura ${'$'}{index + 1}"`) meten las suyas y descuadran el recuento—; contar el balance
 *     desde la línea del disparo sí, porque una plantilla abre y cierra en la misma línea.
 *
 * Se compara **cue contra cue**, no «suena algo y además llama a un verbo»: una pantalla puede sonar
 * su propio efecto y llamar a un verbo que suena otro distinto, y eso es correcto (dos cosas pasan,
 * dos cosas suenan).
 *
 * Es una heurística, no un analizador: puede no ver un caso raro, pero coge exactamente la forma que
 * produjo el bug y se mantiene sola. **Se verificó reintroduciendo el doble a propósito** — sin esa
 * comprobación, un contrato así puede llevar meses pasando en vacío. Si algún día un doble es
 * DELIBERADO, decláralo en [permitidos] con su motivo; no se tolera en silencio.
 */
class SingleSoundCueContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, VIEWMODEL).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    /**
     * Dobles aceptados a propósito, como `Fichero.kt:playCue`.
     *
     * Vacío hoy. Sirve además de válvula para un falso positivo: un nombre de parámetro genérico
     * (`onClose`, `onDismiss`) puede estar cableado a un verbo que suena en UNA pantalla y significar
     * otra cosa en otra.
     */
    private val permitidos = emptySet<String>()

    private val uiFiles: List<File> by lazy {
        File(repoRoot, UI_SRC).walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "SoundManager.kt" }
            .toList()
    }

    /** Paso 1 — verbo del ViewModel → cues que dispara él mismo. */
    private val verbCues: Map<String, Set<String>> by lazy {
        val out = LinkedHashMap<String, MutableSet<String>>()
        var current: String? = null
        for (line in File(repoRoot, VIEWMODEL).readText().lineSequence()) {
            Regex("""^\s{4}(?:private |internal )?fun (\w+)\(""").find(line)
                ?.let { current = it.groupValues[1] }
            val verb = current ?: continue
            Regex("""soundManager\.(play\w+)\(""").findAll(line).forEach {
                out.getOrPut(verb) { LinkedHashSet() } += it.groupValues[1]
            }
        }
        out
    }

    private val allUiText: String by lazy { uiFiles.joinToString("\n") { it.readText() } }

    /** Paso 2 — parámetro de UI → cues del verbo al que está cableado. */
    private val paramVerbCues: Map<String, Set<String>> by lazy {
        val out = LinkedHashMap<String, MutableSet<String>>()
        Regex("""(\w+)\s*=\s*\{[^{}]{0,160}?viewModel\.(\w+)\(""").findAll(allUiText).forEach { m ->
            verbCues[m.groupValues[2]]?.let { out.getOrPut(m.groupValues[1]) { LinkedHashSet() } += it }
        }
        out
    }

    /** Paso 3 — parámetro de UI → cues que dispara él mismo, sin pasar por un verbo. */
    private val paramOwnCues: Map<String, Set<String>> by lazy {
        val out = LinkedHashMap<String, MutableSet<String>>()
        Regex("""(\w+)\s*=\s*\{[^{}]{0,160}?soundManager\.(play\w+)\(""").findAll(allUiText).forEach { m ->
            out.getOrPut(m.groupValues[1]) { LinkedHashSet() } += m.groupValues[2]
        }
        out
    }

    @Test
    fun `el censo encuentra los verbos que suenan y como se cablean`() {
        // Red de seguridad del parseo: sin esto, la prueba de abajo pasaría en vacío.
        assertTrue(
            "Solo he encontrado ${verbCues.size} verbos con sonido: el parseo está roto",
            verbCues.size >= 20
        )
        assertTrue("equipCompanion" in verbCues)
        assertTrue("buyCompanionCharge" in verbCues)
        // Y que el paso 2 encuentra de verdad el cableado, que es lo que hace útil a la prueba.
        // (Hasta el 2026-09-24 los anclas eran `onPromoteCaptain` / `onEquipCompanion`, que se fueron
        // con el cinturón del Inventario; `buyCompanionCharge` se cablea como `onBuyCharge`.)
        assertTrue(
            "No he encontrado el nombre con el que la UI llama a los verbos: ${paramVerbCues.keys}",
            "onBuyCharge" in paramVerbCues && "onUpgradeAbility" in paramVerbCues
        )
        assertTrue(
            "No he encontrado los parámetros que suenan por su cuenta: ${paramOwnCues.keys}",
            "onUiClick" in paramOwnCues
        )
    }

    @Test
    fun `ninguna pantalla dispara dos veces el mismo sonido`() {
        val offenders = mutableListOf<String>()
        for (file in uiFiles) {
            val lines = file.readText().lines()
            lines.forEachIndexed { i, line ->
                // Lo que ESTA línea suena por su cuenta: una llamada `.playX()` directa, o un
                // parámetro que sabemos que suena.
                val direct = LinkedHashSet<String>()
                Regex("""\.(play\w+)\(""").findAll(line).forEach { direct += it.groupValues[1] }
                paramOwnCues.forEach { (param, cues) ->
                    if (Regex("""\b$param\(""").containsMatchIn(line)) direct += cues
                }
                if (direct.isEmpty()) return@forEachIndexed

                // Lo que suena, DENTRO de la misma lambda, por culpa de un verbo del ViewModel.
                val blob = lambdaWindow(lines, i)
                val viaVerb = LinkedHashSet<String>()
                verbCues.forEach { (verb, cues) ->
                    if (Regex("""\b$verb\(""").containsMatchIn(blob)) viaVerb += cues
                }
                paramVerbCues.forEach { (param, cues) ->
                    if (Regex("""\b$param\(""").containsMatchIn(blob)) viaVerb += cues
                }

                (direct intersect viaVerb)
                    .map { "${file.name}:$it" }
                    .filterNot { it in permitidos }
                    .forEach { offenders += "$it (línea ${i + 1})" }
            }
        }
        assertTrue(
            "SONIDO DUPLICADO (la pantalla lo dispara y el verbo del ViewModel vuelve a dispararlo): " +
                "$offenders — quita el disparo de la PANTALLA: el sonido es del VERBO, no del botón",
            offenders.isEmpty()
        )
    }

    /**
     * La línea [i] y las siguientes hasta que se cierran las llaves que abre. Si la lambda cabe en
     * una línea —el caso habitual de un manejador de toque— la ventana es esa línea sola, y por eso
     * no se contagia de la línea de al lado.
     */
    private fun lambdaWindow(lines: List<String>, i: Int): String {
        var balance = lines[i].count { it == '{' } - lines[i].count { it == '}' }
        val window = StringBuilder(lines[i])
        var j = i + 1
        while (balance > 0 && j < lines.size && j < i + MAX_WINDOW_LINES) {
            val next = lines[j]
            window.append('\n').append(next)
            balance += next.count { it == '{' } - next.count { it == '}' }
            j++
        }
        return window.toString()
    }

    private companion object {
        const val VIEWMODEL = "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt"
        const val UI_SRC = "app/src/main/java/com/cukiti/teikafusion/ui"
        /** Techo de la ventana: un manejador de toque más largo que esto ya no es un manejador. */
        const val MAX_WINDOW_LINES = 12
    }
}
