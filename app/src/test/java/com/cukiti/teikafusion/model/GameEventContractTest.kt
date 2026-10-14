package com.cukiti.teikafusion.model

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * El CENSO DE EVENTOS, automatizado (bloque 21, 2026-07-31).
 *
 * Hasta ahora este censo se hacía **a mano con grep** en cada auditoría del bloque (its. 44 y 63), y
 * entre auditoría y auditoría el árbol acumulaba fósiles: la it. 63 podó cuatro efectos completos que
 * ningún jugador podía ver, y esta pasada dos más (`Dematerialize`, `TideFrozen`). El coste de un
 * fósil no es el peso: es que el siguiente que lea `ui/VFX.kt` creerá que esa mecánica existe.
 *
 * Estas pruebas leen el CÓDIGO FUENTE (no hay forma de preguntarle a una `sealed class` quién la
 * construye) y fijan tres invariantes del contrato:
 *
 * 1. **Sin fósiles**: todo evento declarado tiene al menos un PRODUCTOR (alguien lo construye fuera
 *    de su propia declaración).
 * 2. **Sin huérfanos**: todo evento declarado se nombra en la UI, que es quien lo dibuja y lo
 *    consume. Un evento que nadie mira se queda en la cola hasta que lo barre el recolector de
 *    huérfanos (10 s) — trabajo del motor que no se ve.
 * 3. **La lista blanca de dobles consumidores no miente**: `vfxOwnedBiomeEvent` solo se consulta
 *    dentro del bucle de `biomeFxEvents`, así que una entrada que no esté también ahí no protege
 *    nada (y hace creer que sí). El doble consumidor es EL bug recurrente del proyecto.
 *
 * Si un evento nace y su VFX aún no está cableado, la prueba falla **a propósito**: se cablea o se
 * borra el evento, no se deja a medias.
 */
class GameEventContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, RELATIVE_EVENT_FILE).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    private val eventSource: String by lazy { File(repoRoot, RELATIVE_EVENT_FILE).readText() }

    private fun sources(vararg relativeDirs: String): List<File> =
        relativeDirs.flatMap { rel ->
            File(repoRoot, rel).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
        }

    /**
     * Subclases DIRECTAS de `GameEvent` = lo que está declarado con 4 espacios de sangría dentro de
     * la clase sellada. Las cargas útiles (`AstroLift`, `KingTeikaShove`…) van anidadas dentro del
     * evento que las lleva, con más sangría, y por eso no entran aquí — que es justo el criterio que
     * la it. 20 tuvo que descubrir persiguiendo "eventos" que no lo eran.
     */
    private val declaredEvents: List<String> by lazy {
        Regex("""^ {4}(?:data class|data object|class) (\w+)""", RegexOption.MULTILINE)
            .findAll(eventSource)
            .map { it.groupValues[1] }
            .toList()
    }

    @Test
    fun `el censo encuentra los eventos declarados`() {
        // Red de seguridad de la propia prueba: si el parseo se rompe (cambia la sangría, se parte
        // el archivo), todo lo demás pasaría en vacío sin que nadie se entere.
        assertTrue(
            "El censo solo ha encontrado ${declaredEvents.size} eventos: el parseo está roto",
            declaredEvents.size >= 60
        )
        assertTrue("FusionBurst" in declaredEvents)
        assertTrue("LineClearBeam" in declaredEvents)
        // Cargas útiles: NUNCA deben contarse como eventos (no se emiten ni se consumen solas).
        assertTrue("AstroLift" !in declaredEvents)
        assertTrue("KingTeikaShove" !in declaredEvents)
        assertTrue("GalacticTeikaShove" !in declaredEvents)
    }

    /**
     * LA JERARQUÍA DE EVENTOS TIENE QUE SER PLANA.
     *
     * `GameEventBuckets` agrupa la cola **por clase EXACTA** para que las ~68 lecturas de la UI sean
     * búsquedas en mapa en vez de 68 recorridos O(n) (perf 2026-08-09). Eso solo es equivalente a
     * `filterIsInstance` mientras ningún evento herede de otro: el día que alguien declare
     * `data class X(...) : GameEvent.Y()`, quien pida `of<Y>()` dejaría de ver las `X` **sin un solo
     * error** — el VFX simplemente no se dibujaría. Fallo mudo, y por eso vive aquí.
     */
    @Test
    fun `ningun evento hereda de otro evento`() {
        val nested = Regex(""":\s*GameEvent\.[A-Z]\w*\(""").findAll(eventSource)
            .map { it.value.trim() }
            .toList()
        assertTrue(
            "Un evento hereda de otro: eso rompe el agrupado por clase exacta de GameEventBuckets " +
                "y deja consumidores ciegos sin dar error. Encontrado: $nested",
            nested.isEmpty()
        )
    }

    @Test
    fun `todo evento tiene al menos un productor`() {
        val producerSources = sources(MAIN_SRC + "viewmodel", MAIN_SRC + "model")
            .filterNot { it.name == "GameEvent.kt" }
            .joinToString("\n") { it.readText() }
        val fossils = declaredEvents.filter { name ->
            !Regex("""GameEvent\.$name\b""").containsMatchIn(producerSources)
        }
        assertTrue(
            "FÓSILES (evento declarado que nadie construye — o se cablea o se poda): $fossils",
            fossils.isEmpty()
        )
    }

    @Test
    fun `todo evento se mira desde la UI`() {
        val uiSources = sources(MAIN_SRC + "ui").joinToString("\n") { it.readText() }
        val orphans = declaredEvents.filter { name ->
            !Regex("""GameEvent\.$name\b""").containsMatchIn(uiSources)
        }
        assertTrue(
            "HUÉRFANOS (evento que se emite y nadie dibuja ni consume): $orphans",
            orphans.isEmpty()
        )
    }

    @Test
    fun `la lista de eventos con VFX propio es un subconjunto de los que pasan por la haptica`() {
        val screen = File(repoRoot, MAIN_SRC + "ui/GameScreen.kt").readText()
        val owned = eventNamesIn(screen, "private fun vfxOwnedBiomeEvent", "\n}")
        val haptic = eventNamesIn(screen, "val biomeFxEvents", "\n    }")

        assertTrue("No he podido leer vfxOwnedBiomeEvent", owned.isNotEmpty())
        assertTrue("No he podido leer biomeFxEvents", haptic.isNotEmpty())
        // Los dos bloques hablan de eventos que existen.
        assertTrue((owned + haptic).all { it in declaredEvents })
        // Una entrada "protegida" que la háptica ni mira es una rama muerta que aparenta seguridad.
        val deadBranches = owned - haptic
        assertTrue(
            "Entradas de vfxOwnedBiomeEvent que no están en biomeFxEvents (no protegen nada): $deadBranches",
            deadBranches.isEmpty()
        )
    }

    /**
     * **UN SOLO SITIO RECOGE CADA EVENTO** (2026-09-02, bloque 1).
     *
     * El doble consumidor es EL bug recurrente de este proyecto —lo dice CLAUDE.md y lo repiten las
     * convenciones de MEJORAS—: dos sitios se llevan el mismo evento, el primero que termina llama a
     * `consumeEvent` y el segundo se queda con la animación cortada a un fotograma. El síntoma
     * («el efecto aparece y desaparece») manda a buscar el fallo al sitio equivocado, y la ficha del
     * bloque 1 avisa de que `GameScreen.kt` es el candidato natural a cometerlo otra vez.
     *
     * Hasta hoy la regla vivía en **prosa**: cada reparto legítimo está explicado en un comentario
     * («PROPIEDAD: esta pantalla es dueña del "+score" NO-combo»), pero nada impedía que el próximo
     * efecto añadiera una segunda recogida sin enterarse. Esto lo mide: **quién RECOGE** un evento
     * de la cola (`eventBuckets.of<…>()` / `filterIsInstance<…>`), que es el paso previo obligatorio
     * a consumirlo. Con una sola recogida no puede haber dos consumidores.
     *
     * Los seis repartos de hoy están en la lista blanca, cada uno con su motivo. Si mañana uno deja
     * de repartirse, la prueba también avisa: una lista blanca que miente es peor que no tenerla.
     */
    @Test
    fun `cada evento se recoge en UN SOLO sitio de la UI`() {
        val pickup = Regex("""(?:filterIsInstance|\bof)<(?:com\.cukiti\.teikafusion\.model\.)?GameEvent\.(\w+)>""")
        val sites = mutableMapOf<String, MutableList<String>>()
        sources(MAIN_SRC + "ui")
            // La infraestructura de la cola queda fuera: su KDoc usa `of<GameEvent.FusionBurst>()`
            // como ejemplo y no recoge nada.
            .filterNot { it.name == "GameEventBuckets.kt" }
            .forEach { file ->
                file.readLines().forEachIndexed { i, line ->
                    val t = line.trimStart()
                    if (t.startsWith("//") || t.startsWith("*")) return@forEachIndexed
                    pickup.findAll(line).forEach { m ->
                        sites.getOrPut(m.groupValues[1]) { mutableListOf() }.add("${file.name}:${i + 1}")
                    }
                }
            }
        assertTrue(
            "El censo solo ha encontrado ${sites.size} eventos recogidos: el parseo está roto",
            sites.size >= 60
        )
        val unexpected = sites.filterValues { it.size > 1 }.keys - LEGIT_SPLIT_PICKUPS
        assertTrue(
            "Estos eventos se recogen en VARIOS sitios sin estar en la lista blanca — es el patrón " +
                "del doble consumidor: " +
                unexpected.associateWith { sites[it] },
            unexpected.isEmpty()
        )
        val stale = LEGIT_SPLIT_PICKUPS.filter { (sites[it]?.size ?: 0) <= 1 }
        assertTrue(
            "Entradas de la lista blanca que ya NO se reparten (sobran, y hacen creer que hay un " +
                "reparto donde no lo hay): $stale",
            stale.isEmpty()
        )
    }

    @Test
    fun `retired cut has no screen observer and compactor keeps a single effect owner`() {
        val screen=File(repoRoot,MAIN_SRC+"ui/GameScreen.kt").readText()
        assertTrue(!screen.contains("val cutVisual =") && !screen.contains("PrecisionCutControls("))
        val board=File(repoRoot,MAIN_SRC+"ui/GameBoard.kt").readText()
        assertTrue(board.contains("rulePulses.filterNot { it.kind.startsWith(\"compactor\") || it.kind.startsWith(\"orbit\") || it.kind.startsWith(\"portal_swap\") }"))
        assertTrue(board.contains("rulePulses.filter { it.kind.startsWith(\"compactor\") || it.kind.startsWith(\"orbit\") || it.kind.startsWith(\"portal_swap\") }"))
        assertFalse(screen.contains("RoutesDragPreview("))
        assertFalse(screen.contains("CometDragPreview("))
        val orbit=File(repoRoot,MAIN_SRC+"ui/OrbitUi.kt").readText()
        assertFalse(orbit.contains("consumeEvent") || orbit.contains("onConsumed"))
        val portal=File(repoRoot,MAIN_SRC+"ui/PortalSwapUi.kt").readText()
        assertFalse(portal.contains("consumeEvent") || portal.contains("onConsumed"))
        val reactor=File(repoRoot,MAIN_SRC+"ui/ReactorUi.kt").readText()
        assertFalse(reactor.contains("consumeEvent") || reactor.contains("onConsumed"))
        assertTrue(board.contains("ReactorHiddenBodies("))
        val ball=File(repoRoot,MAIN_SRC+"ui/WreckingBallUi.kt").readText()
        assertFalse(ball.contains("consumeEvent") || ball.contains("onConsumed"))
        assertFalse(screen.contains("DominoDragPreview("))
        assertFalse(screen.contains("trail_charge") || screen.contains("RoutesDragPreview("))
        val gesture=File(repoRoot,MAIN_SRC+"ui/CompactorUi.kt").readText()
        assertTrue(!gesture.contains("consumeEvent") && !gesture.contains("onConsumed"))
        val ui=File(repoRoot,MAIN_SRC+"ui/ReplacementRuleUi.kt").readText()
        val reveal=ui.substringAfter("fun PrecisionCutReveal(").substringBefore("private fun RuleBodyWake(")
        assertTrue(reveal.contains("originalPiece"))
        assertTrue(!reveal.contains("consumeEvent") && !reveal.contains("onConsumed"))
        val owner=ui.substringAfter("fun ReplacementRuleLayer(").substringBefore("fun PrecisionCutControls(")
        assertTrue(Regex("onConsumed\\(event.id\\)").findAll(owner).count()==1)
    }

    /** Nombres `GameEvent.X` que aparecen en el trozo de fuente entre [start] y el siguiente [end]. */
    private fun eventNamesIn(source: String, start: String, end: String): Set<String> {
        val from = source.indexOf(start)
        if (from < 0) return emptySet()
        val to = source.indexOf(end, from)
        val block = source.substring(from, if (to < 0) source.length else to)
        return Regex("""GameEvent\.(\w+)""").findAll(block).map { it.groupValues[1] }.toSet()
    }

    private companion object {
        const val MAIN_SRC = "app/src/main/java/com/cukiti/teikafusion/"
        const val RELATIVE_EVENT_FILE = MAIN_SRC + "model/GameEvent.kt"

        /**
         * Los ÚNICOS eventos que legítimamente se recogen en más de un sitio, y por qué. Cada uno
         * tiene el reparto escrito también en el código que lo hace; esto es el censo que impide
         * que la lista crezca sola.
         *
         *  - **`ComboText`** — el tablero pinta los tiers normales y `GameScreen` los ÉPICOS
         *    (`tier >= EPIC_COMBO_TIER`, a pantalla completa); la tercera recogida solo PREGUNTA si
         *    hay alguno en pantalla para que el rótulo grande le ceda el turno, y no consume.
         *  - **`FloatingScore`** — repartido por `isCombo`: el del combo lo dibuja el tablero en su
         *    zona, el suelto vuela hacia el marcador desde la pantalla.
         *  - **`FusionBurst`** y **`MegaTeikaFriendExpansion`** — `GameScreen` los mira **solo para
         *    la háptica** (manda el tier más alto de la cascada, P-02) y no los consume; quien los
         *    dibuja y los consume es el tablero.
         *  - **`LineCellShatter`** — mutuamente excluyentes por SKIN: con «Caída 3D» lo monta la
         *    pantalla para que los cubos caigan por encima de la bandeja; con el resto, el tablero.
         *  - **`RulePulse`** — GameScreen solo observa el corte para dibujar la pieza original en
         *    la bandeja; GameBoard/ReplacementRuleLayer posee el reloj y la retirada. La prueba
         *    específica de abajo impide que ese observador adquiera consumo.
         *  (`SingularityCollapse` salió de aquí el 2026-09-23 con el agujero negro: la Brecha en el
         *  Casco, P-248, tiene un solo consumidor por evento.)
         */
        val LEGIT_SPLIT_PICKUPS = setOf(
            "ComboText",
            "FloatingScore",
            "FusionBurst",
            "MegaTeikaFriendExpansion",
            "LineCellShatter",
            "RulePulse",
        )
    }
}
