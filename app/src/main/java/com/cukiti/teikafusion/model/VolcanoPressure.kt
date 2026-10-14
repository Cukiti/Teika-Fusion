package com.cukiti.teikafusion.model

/** Turnos secos antes del PRIMER descenso de lava (calibración 2026-07-28: era 2). */
const val VOLCANO_DRY_TURNS_TO_ADVANCE = 3

/**
 * Turnos secos entre descensos una vez la lava YA está en el tablero.
 *
 * **Vuelve a 1 (2026-08-30, playtest del usuario: «cuando la lava baje y el jugador realice una
 * jugada de turno tiene que bajar; si ya está abajo no tiene por qué esperar dos turnos»).**
 *
 * Estuvo en 1, se subió a 2 el 2026-07-28 y ahora vuelve a 1 porque *cambió el juego debajo*: en
 * julio corrían DOS mecánicas ambientales a la vez (`biomePrimary` + `biomeSecondary`), así que en
 * el Volcán la lava era solo una de las dos presiones y bajar cada turno resultaba asfixiante. La
 * variedad del 2026-08-28 **borró `biomeSecondary`**: hoy en el Volcán no hay nada más que lava, y
 * con el umbral en 2 el capítulo tenía la mitad de presión sin nada que la compensara.
 *
 * El contrajuego sigue intacto y es el mismo de siempre: romper una fila reinicia la cuenta Y hace
 * retroceder la lava [VOLCANO_RETREAT_PER_LINE] filas por línea ([retreatLava]). Y los primeros niveles del capítulo siguen
 * siendo amables porque `ChapterArc` suma +1 al umbral durante la presentación.
 */
const val VOLCANO_DRY_TURNS_SURFACED = 1

data class VolcanoPressureStep(
    val dryTurns: Int,
    val advanceLava: Boolean
)

/**
 * Presión del volcán. Romper filas hace retroceder la lava (retreatLava) y, desde el reloj que se
 * congela (2026-09-23, [BiomeClock]), en un turno de AVISO deja la cuenta como estaba; solo romper en el
 * turno en que la lava iba a bajar la pone a cero.
 * Umbral dinámico según [lavaRow]:
 *  - Sin lava aún ([lavaRow] == 0): [VOLCANO_DRY_TURNS_TO_ADVANCE] turnos de gracia antes del primer descenso.
 *  - Con lava ya presente ([lavaRow] > 0): baja cada [VOLCANO_DRY_TURNS_SURFACED] turnos secos.
 *
 * Es una ESCALERA a propósito (3 → 1): el volcán te regala tres turnos para conocerlo y a partir de
 * que asoma no vuelve a soltarte. Esa aceleración es la firma rítmica del bioma — ver el porqué del
 * vaivén 1 → 2 → 1 en el KDoc de [VOLCANO_DRY_TURNS_SURFACED].
 */
fun nextVolcanoPressure(
    currentDryTurns: Int,
    linesCleared: Int,
    lavaRow: Int = 0,
    firstThreshold: Int = VOLCANO_DRY_TURNS_TO_ADVANCE,
    surfacedThreshold: Int = VOLCANO_DRY_TURNS_SURFACED
): VolcanoPressureStep {
    val threshold = if (lavaRow > 0) surfacedThreshold else firstThreshold
    if (linesCleared > 0) {
        return VolcanoPressureStep(dryTurns = BiomeClock.onLineCleared(currentDryTurns.coerceAtLeast(0), threshold), advanceLava = false)
    }
    val next = currentDryTurns.coerceAtLeast(0) + 1
    return if (next >= threshold) {
        VolcanoPressureStep(dryTurns = 0, advanceLava = true)
    } else {
        VolcanoPressureStep(dryTurns = next, advanceLava = false)
    }
}

/**
 * **Fase del aviso de la lava** (pedido del usuario, 2026-09-23): el volcán se anuncia en DOS
 * tiempos y ejecuta al tercero, como el resto de biomas —
 *
 *  - [VOLCANO_WARN_NONE]: tablero limpio. Antes la cresta ardía en el techo desde el primer
 *    fotograma del nivel, así que el aviso no avisaba de nada: era decorado.
 *  - [VOLCANO_WARN_CREST]: a dos colocaciones secas del descenso asoma la cresta.
 *  - [VOLCANO_WARN_BOIL]: a una, hierve (la «segunda animación» que ya existía) y entra la viñeta.
 *
 * Con la lava YA dentro el umbral es 1 y cada turno seco la baja, así que hierve siempre: es la
 * verdad del bioma en ese punto. Una sola función para la cresta y la viñeta — dos copias de la
 * misma decisión acabarían contando turnos distintos.
 */
fun volcanoWarningPhase(lavaRow: Int, dryTurns: Int, threshold: Int): Int {
    if (lavaRow > 0) return VOLCANO_WARN_BOIL
    if (dryTurns <= 0) return VOLCANO_WARN_NONE
    return when (threshold - dryTurns) {
        1 -> VOLCANO_WARN_BOIL
        2 -> VOLCANO_WARN_CREST
        else -> if (threshold - dryTurns <= 0) VOLCANO_WARN_BOIL else VOLCANO_WARN_NONE
    }
}

const val VOLCANO_WARN_NONE = 0
const val VOLCANO_WARN_CREST = 1
const val VOLCANO_WARN_BOIL = 2

/**
 * Filas que la lava RETROCEDE por cada línea rota. **1 → 2 (2026-09-24, P-262, decisión del usuario):**
 * con la lava bajando en cada turno seco y retrocediendo solo una por línea, una vez dentro no se iba
 * nunca (bot: Volcán 9 %). Con dos, una buena jugada la saca de verdad.
 */
// P-352 (2026-09-27): 2 → 1. Endurece el Volcán sin tocar su reloj de avisos (decisión congelada):
// empujar la lava cuesta el doble de líneas.
const val VOLCANO_RETREAT_PER_LINE = 1

fun retreatLava(currentLavaRow: Int, linesCleared: Int): Int =
    (currentLavaRow.coerceAtLeast(0) - linesCleared.coerceAtLeast(0) * VOLCANO_RETREAT_PER_LINE).coerceAtLeast(0)
