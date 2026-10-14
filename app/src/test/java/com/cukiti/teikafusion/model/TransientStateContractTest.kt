package com.cukiti.teikafusion.model

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **¿QUÉ SE PIERDE AL SALIR Y VOLVER, Y POR QUÉ?** (P-170, 2026-09-04).
 *
 * `@Transient` es la decisión más silenciosa de este motor: se escribe una vez, no rompe nada, y su
 * consecuencia solo aparece el día que un jugador sale a Inicio y pulsa CONTINUAR. El historial lo
 * dice todo:
 *
 *  · **P-111** (2026-09-03) encontró **35 campos** que no debían serlo. Mientras CONTINUAR no existía
 *    eran inofensivos; el día que se cableó, el botón pasó a ser un «anular castigo» —muros del Rey,
 *    pozos del Glotón, escarcha, niebla, agujero negro y todos los contadores ociosos a cero—.
 *  · **P-112** (2026-09-04) encontró uno al revés: `busyTeikaIds` se guardaba y no debía, así que un
 *    round-trip devolvía cuerpos marcados como ocupados sin nada que los liberase.
 *  · El **rapto de la raíz** fue el último cabo de P-111, cerrado ese mismo día.
 *
 * Tres hallazgos de la misma especie en dos días. Que un dato NO sobreviva es una decisión legítima
 * —los eventos son cola de un solo uso, los instantes de reloj de pared no significan nada tras una
 * ausencia—, pero **tiene que estar escrita**, que es exactamente lo que `CloudSchemaCoverageTest` ya
 * le exige a los datos de la nube. Este test es esa misma regla, un piso más abajo.
 *
 * Si añades un `@Transient` a `GameState`, escribe aquí su motivo en una línea. Si no sabes cuál es,
 * probablemente el campo no deba ser transitorio.
 */
class TransientStateContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, STATE).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    /** Campos de `GameState` que NO sobreviven a un guardado, con su motivo. */
    private val transientReasons = mapOf(
        "events" to "cola de cues de un solo uso: sus animaciones no existen al cargar",
        // 2026-09-28: el aviso del Tóxico es una FOTO del tablero; se recalcula al cerrar el primer turno.
        "toxicFront" to "foto del tablero: el tick lo recalcula con la regla real al cerrar el turno",
        // P-229 / P-228 (2026-09-07b): los dos son FOTOS del tablero de este turno, no datos del
        // jugador. Se recalculan enteros en el primer cierre de turno tras cargar, y hasta entonces
        // no enseñar el aviso es mejor que enseñarlo con el tablero de antes de la ausencia.
        "unplayablePieceIds" to "foto del tablero: se recalcula al cerrar el primer turno",
        "lastSqueezeBiome" to "culpable del turno en curso: el tick siguiente lo reevalúa entero",
        "busyTeikaIds" to "marca de animación en curso (P-112): al cargar no corre ninguna",
        "tutorialAbilityCast" to "la lección vuelve a pedir el gesto, que es lo que la lección ES",
        "objectiveNudgePulse" to "pulso de UI: se vuelve a calcular si sigue habiendo motivo",
        "iceWindowUntil" to "instante de reloj de pared: no significa nada tras una ausencia",
        "hiddenWoodCells" to "madera en vuelo del colapso: sin el VFX no hay a dónde devolverla",
        "defeatCause" to "se recalcula al detectar la derrota, no se hereda",
        "regentTeikaId" to "el reinado dura una fusión o un instante de reloj (ver regentExpiresAtMs)",
        "regentBonus" to "acompaña a regentTeikaId",
        "regentExpiresAtMs" to "instante de reloj de pared: no significa nada tras una ausencia",
        "trayRewardJustDealt" to "aviso de un turno para la cápsula del premio: al cargar ya pasó"
    )

    @Test
    fun `todo campo transitorio de GameState tiene su motivo escrito`() {
        val source = File(repoRoot, STATE).readText()
        // Solo el cuerpo del data class: fuera las clases auxiliares y el companion.
        val body = source.substringAfter("data class GameState(").substringBefore("\n) {")

        // `@Transient` puede ir en la misma línea que el campo o en la anterior.
        val fields = Regex("""@Transient\s+(?:/\*\*.*?\*/\s+)?val (\w+)""", RegexOption.DOT_MATCHES_ALL)
            .findAll(body)
            .map { it.groupValues[1] }
            .toList()
        assertTrue(
            "Solo he encontrado ${fields.size} campos @Transient: el parseo está roto",
            fields.size >= 8
        )

        val undocumented = fields.filterNot { it in transientReasons }
        assertTrue(
            "Campos @Transient de GameState sin motivo escrito: $undocumented — que un dato no " +
                "sobreviva a CONTINUAR es una decisión legítima, pero es la que más caro se paga en " +
                "silencio (P-111 encontró 35 mal marcados). Añádelo a `transientReasons` con su " +
                "porqué, o quítale el @Transient.",
            undocumented.isEmpty()
        )

        // Y al revés: un motivo escrito para un campo que ya no es transitorio miente igual que un
        // pendiente falso. Es lo que pasó con el rapto de la raíz, que dejó de serlo el 2026-09-04.
        val stale = transientReasons.keys.filterNot { it in fields }
        assertTrue(
            "Motivos escritos para campos que YA NO son @Transient: $stale — bórralos, o el " +
                "siguiente que lea esta lista creerá que ese dato se pierde y no se pierde.",
            stale.isEmpty()
        )
    }

    private companion object {
        const val STATE = "app/src/main/java/com/cukiti/teikafusion/model/GameState.kt"
    }
}
