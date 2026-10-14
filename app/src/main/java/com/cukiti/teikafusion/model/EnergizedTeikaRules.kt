package com.cukiti.teikafusion.model

/**
 * Reglas PURAS del Energizado de Rayco (habilidad de compañero, rework 2026-07-17).
 *
 * Al castear, los Teikas del tablero quedan ENERGIZADOS. Cuando el jugador completa una línea que un
 * Teika energizado ocupa (como comodín), ese Teika DESCARGA: sus OTRAS líneas (las filas/columnas que
 * su cuerpo también ocupa y que aún no están completas) se autocompletan atrayendo madera del propio
 * tablero, y se rompen junto con la original. Un T2 (2×2) que remata una fila rompe además su otra
 * fila y sus dos columnas.
 *
 * Este objeto decide SOLO la geometría (¿participa? ¿qué líneas autocompletar?). El relleno con
 * madera atraída lo hace el ViewModel (necesita el grid). Extraído a `model/` para testear sin él.
 */
object EnergizedTeikaRules {

    /** Resultado: si el Teika participa en una línea completa y qué líneas perpendiculares rematar. */
    data class Discharge(
        val participates: Boolean,
        val targetRows: List<Int>,
        val targetCols: List<Int>
    )

    /**
     * @param occupied celdas que ocupa el cuerpo del Teika.
     * @param rowComplete ¿está completa la fila [y]?  @param colComplete ¿está completa la columna [x]?
     */
    fun dischargeFor(
        occupied: Set<Position>,
        rowComplete: (Int) -> Boolean,
        colComplete: (Int) -> Boolean
    ): Discharge {
        if (occupied.isEmpty()) return Discharge(false, emptyList(), emptyList())
        val rows = occupied.map { it.y }.distinct().sorted()
        val cols = occupied.map { it.x }.distinct().sorted()
        val participates = rows.any(rowComplete) || cols.any(colComplete)
        if (!participates) return Discharge(false, emptyList(), emptyList())
        // Autocompletar SUS líneas que aún no están completas.
        return Discharge(
            participates = true,
            targetRows = rows.filterNot(rowComplete),
            targetCols = cols.filterNot(colComplete)
        )
    }
}
