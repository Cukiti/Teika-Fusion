package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **TODA HABILIDAD QUE ROMPE, RECOGE SU ESTRELLA** (P-121, 2026-09-03) — hermano de
 * `GameEventContractTest`, `SoundCueContractTest` e `ImmersiveWindowContractTest`.
 *
 * La regla es de la it. 52 y su consecuencia es dura: **el objetivo de TODOS los niveles son
 * estrellas**. Una habilidad que destruya un bloque con estrella sin recogerla no comete un fallo
 * cosmético — deja el nivel imposible de cerrar, y el jugador no tiene forma de saber por qué se le
 * quedó el contador corto. Es un bug silencioso, caro y difícil de reproducir.
 *
 * Hasta hoy la regla se sostenía sobre **ocho emisiones de `TargetCollected` escritas a mano** y
 * ninguna prueba. Este censo la fija. No comprueba el comportamiento (los efectos viven dentro de la
 * corrutina del ViewModel y no se pueden instanciar en la JVM): comprueba que **cada habilidad
 * declara cómo recoge, y que su código hace lo que declaró**. Si alguien añade la habilidad 15, o le
 * quita la emisión a una de las seis que la tienen, esto cae antes de llegar a una build.
 *
 * El censo se verificó a mano rama por rama el 2026-09-03.
 */
class AbilityStarCollectionContractTest {

    /** Cómo llega la estrella al contador cuando esta habilidad toca el tablero. */
    private enum class Via {
        /** Emite su propio `TargetCollected`: destruye madera fuera del motor de líneas. */
        PROPIA,

        /** Rompe líneas DE VERDAD, así que las cobra `resolveBoardChain` como una jugada normal. */
        MOTOR,

        /** No rompe al lanzarse: arma algo que romperá después, y allí se recoge. */
        DIFERIDA,

        /** No destruye madera en ningún momento — no hay estrella que recoger. */
        NO_ROMPE
    }

    private val censo: Map<String, Via> = mapOf(
        // Destruyen madera por su cuenta → emisión propia, obligatoria.
        "hielo"     to Via.PROPIA,    // los carámbanos perforan la columna entera
        "gloton"    to Via.PROPIA,    // Chueta se traga los bloques con estrella
        "ninja"     to Via.PROPIA,    // los tajos parten los bloques con estrella (DOBLE)
        "galactico" to Via.PROPIA,    // el eclipse pulveriza el rincón más denso
        "fuego"     to Via.PROPIA,    // `fireChainClear` arrasa filas y columnas
        "toxico"    to Via.PROPIA,    // el miasma corroe toda la madera a la vez

        // Completan o desploman líneas reales → las cobra el motor, como cualquier colocación.
        "dormilona" to Via.MOTOR,     // el tablero se desploma y rematan las filas que caen
        "miedoso"   to Via.MOTOR,     // la madera se desliza y rompe lo que va completando
        "fantasma"  to Via.MOTOR,     // los bloques poseídos encajan en los huecos que cierran línea
        "cristal"   to Via.MOTOR,     // PASSIVE_PIECES: coloca fichas solas, revienta por el motor

        // Arman ahora y rompen luego; la recogida vive en el código del disparo.
        "electrico" to Via.DIFERIDA,  // energiza Teikas; rompe al completarse una línea que ocupan
        "planta"    to Via.DIFERIDA,  // entierra el Teika y arma el cañón (`autoFireFloraCannon`)

        // No destruyen nada.
        "estrella"  to Via.NO_ROMPE,  // RELLENA el hueco conexo con un bloque nuevo
        "rey"       to Via.NO_ROMPE   // PARE un Teika de tier inferior
    )

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, VIEWMODEL).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo" }
    }

    private val source: String by lazy { File(repoRoot, VIEWMODEL).readText() }

    @Test
    fun `el censo cubre exactamente las catorce habilidades`() {
        assertEquals(
            "Habilidad nueva o retirada: declara en el censo CÓMO recoge su estrella.",
            COMPANION_ABILITIES.keys.sorted(), censo.keys.sorted()
        )
    }

    @Test
    fun `cada habilidad que rompe por su cuenta emite TargetCollected en su rama`() {
        censo.filterValues { it == Via.PROPIA }.keys.forEach { id ->
            val rama = ramaDe(id)
            assertTrue(
                "«$id» declara que recoge por su cuenta, pero su rama no emite TargetCollected: " +
                    "o la emisión se perdió, o su Via cambió y el censo no se enteró.",
                rama.contains("TargetCollected")
            )
        }
    }

    @Test
    fun `cada habilidad que delega en el motor lo invoca de verdad`() {
        // `cristal` queda fuera: es PASSIVE_PIECES y no tiene rama propia en el `when` — coloca
        // fichas por el camino normal, así que la cadena la dispara la colocación.
        censo.filterValues { it == Via.MOTOR }.keys.filterNot { it == "cristal" }.forEach { id ->
            val rama = ramaDe(id)
            assertTrue(
                "«$id» declara que sus roturas las cobra el motor, pero su rama no llama a la " +
                    "cadena: las líneas que complete se quedarían sin romper hasta la siguiente " +
                    "colocación del jugador.",
                rama.contains("resolveBoardChain") || rama.contains("revealHiddenWood")
            )
        }
    }

    @Test
    fun `ninguna habilidad que no rompe promete liberar espacio`() {
        // Cruce con el otro contrato: `freesSpace` es lo que `BoardDeadEnd` mira para indultar una
        // derrota. Una habilidad que no destruye nada y aun así dijera que libera espacio le
        // ofrecería al jugador una salida que no existe, y perdería la carga por nada.
        censo.filterValues { it == Via.NO_ROMPE }.keys.forEach { id ->
            val def = requireNotNull(COMPANION_ABILITIES[id]) { "sin definición: $id" }
            assertTrue(
                "«$id» no destruye madera y aun así declara freesSpace: el indulto de la derrota " +
                    "se la ofrecería al jugador y no abriría un solo hueco.",
                !def.freesSpace
            )
        }
    }

    /**
     * Texto de la rama de [id] dentro del `when` de `executeInstantAbility`, desde su marcador hasta
     * el de la siguiente rama. Acepta el alias heredado (`TeikaAbilityId.X.name, "id" ->`) que aún
     * llevan Deseo Estelar y Parto Real por compatibilidad de guardados.
     */
    private fun ramaDe(id: String): String {
        val marcador = Regex("""^\s+(?:TeikaAbilityId\.\w+\.name, )?"$id" -> \{""", RegexOption.MULTILINE)
        val inicio = marcador.find(source)
            ?: error("No encuentro la rama de «$id» en el despachador de habilidades.")
        val siguiente = Regex("""^\s+(?:TeikaAbilityId\.\w+\.name, )?"[a-z]+" -> \{""", RegexOption.MULTILINE)
            .find(source, inicio.range.last)
        return source.substring(inicio.range.last, siguiente?.range?.first ?: source.length)
    }

    private companion object {
        const val VIEWMODEL = "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt"
    }
}
