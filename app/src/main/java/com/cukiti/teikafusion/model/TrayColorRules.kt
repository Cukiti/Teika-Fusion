package com.cukiti.teikafusion.model

/**
 * **EL COLOR DE LA FICHA DEJA DE SER AZAR PURO** (P-206, 2026-09-06, aprobada por el usuario).
 *
 * `Piece.generatePieceForShape` sacaba el color de un `random.nextInt(0, 5)` y ahí se acababa la
 * historia. Eso estuvo bien mientras el color no significó nada —y no significó nada durante meses,
 * como midió la auditoría de variedad (ver el KDoc de [PureLineRules])—, pero desde entonces le han
 * salido **cuatro** mecánicas encima:
 *
 *  - **Línea Pura** (P-69): una línea de un solo color engendra un Teika un tier por encima.
 *  - **La Brocha** (P-70) y **la Camaleón** (P-72), que existen para poder planificar el color.
 *  - **Las celdas de color** (P-157c): celdas que **rechazan** cualquier ficha que no traiga su
 *    color exacto.
 *
 * Con cinco colores al azar y tres ranuras, la probabilidad de que la bandeja **no traiga** un color
 * concreto es `(4/5)³ = 51 %`. O sea que en un nivel de celdas de color, más de la mitad de las
 * bandejas no podían tocar la celda — y la Línea Pura era una lotería en los niveles que la piden.
 *
 * ## Las dos reglas, y ninguna más
 *
 *  1. **El color que el tablero EXIGE aparece.** Si hay celdas de color y ninguna ficha del reparto
 *     trae un color que alguna de ellas pida, una ranura cambia al más pedido.
 *  2. **Ningún color monopoliza la bandeja.** Como mucho [maxSameColor] fichas comparten color: dos
 *     de tres. Una bandeja monocolor no da a elegir nada, y con la Línea Pura encima es además la
 *     que menos se puede jugar.
 *
 * ## Lo que NO hace, y por qué
 *
 * **No toca dónde cabe nada.** El color no condiciona la colocación salvo en las celdas de color, y
 * el planificador ya las excluye de sus reservas (P-157c), así que repintar una ficha recién
 * repartida no puede invalidar la garantía de encaje. Por eso esto es un post-pase y no una
 * restricción de la búsqueda: el mismo criterio con el que las fichas gemelas (P-76) se quedaron
 * fuera del backtracking que costó el ANR del 2026-08-24.
 *
 * **No guarda memoria entre bandejas.** El `avoid` que recibe son los colores que el jugador
 * **todavía tiene en la mano**, que es lo que va a ver al lado de lo que entre. Un campo persistido
 * más solo por el color no se gana su sitio en el guardado: la regla 2 ya impide la monotonía dentro
 * de la bandeja, que es donde se nota.
 */
object TrayColorRules {

    /** Cuántas fichas de una bandeja de [count] pueden compartir color. Dos de tres. */
    fun maxSameColor(count: Int): Int = if (count <= 2) count else (count + 1) / 2

    /**
     * Devuelve el color de cada ranura después de aplicar las dos reglas. Si no hay nada que
     * corregir, devuelve [colors] tal cual.
     *
     * @param colors color que el generador le dio a cada ficha del reparto.
     * @param demanded colores que exigen las celdas de color del tablero. Puede repetir: el más
     *   repetido es el más urgente.
     * @param palette colores de madera disponibles. Con menos de dos no hay nada que repartir.
     * @param avoid colores que el jugador ya tiene delante. Es una **preferencia** para desempatar,
     *   nunca un veto.
     * @param pick sorteo (`n` → `0 until n`). Se inyecta para que la regla sea determinista en test.
     */
    fun assign(
        colors: List<Int>,
        demanded: List<Int> = emptyList(),
        palette: List<Int>,
        avoid: List<Int> = emptyList(),
        pick: (Int) -> Int = { 0 },
    ): List<Int> {
        if (colors.isEmpty() || palette.size < 2) return colors
        val out = colors.toMutableList()
        val cap = maxSameColor(out.size)

        // ── Regla 1 · el color que el tablero exige tiene que estar ─────────────────────────────
        val urgentes = demanded
            .filter { it in palette }
            .groupingBy { it }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<Int, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        if (urgentes.isNotEmpty() && out.none { it in urgentes }) {
            // Se cambia la ficha del color MÁS repetido: así la regla 1 no le crea trabajo a la 2.
            val cuentas = out.groupingBy { it }.eachCount()
            val dominante = cuentas.maxByOrNull { it.value }?.key
            val slot = out.indices.lastOrNull { out[it] == dominante } ?: 0
            out[slot] = urgentes.first()
        }

        // ── Regla 2 · ningún color monopoliza ───────────────────────────────────────────────────
        // El guard es una red, no el caso normal: cada vuelta baja en uno la cuenta del dominante,
        // así que como mucho hay tantas vueltas como fichas.
        var guard = out.size + 1
        while (guard-- > 0) {
            val cuentas = out.groupingBy { it }.eachCount()
            val dominante = cuentas.maxByOrNull { it.value } ?: break
            if (dominante.value <= cap) break
            val slot = out.indices.lastOrNull { out[it] == dominante.key } ?: break
            val alternativas = palette.filter { it != dominante.key && (cuentas[it] ?: 0) < cap }
            if (alternativas.isEmpty()) break
            // Se prefiere lo que el tablero pide, luego lo que el jugador NO tiene ya delante.
            val preferidas = alternativas.filter { it in urgentes }
                .ifEmpty { alternativas.filterNot { it in avoid } }
                .ifEmpty { alternativas }
            out[slot] = preferidas[pick(preferidas.size).coerceIn(0, preferidas.size - 1)]
        }
        return out
    }
}
