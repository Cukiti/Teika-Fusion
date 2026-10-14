package com.cukiti.teikafusion.model

/**
 * Materiales que pueden ceder cuando una compresión física deja a un Teika sin espacio.
 *
 * El cristal ([CellContent.Wood.armor] > 0) sigue absorbiendo una limpieza de línea, pero no puede
 * mantener un cuerpo interpenetrado: en esta ruta geométrica cede junto con el bloque interior.
 * Bobinas y celdas protegidas por otra mecánica continúan siendo terreno duro.
 */
object TeikaPushBreakRules {
    fun canBreak(content: CellContent?, protectedByBiome: Boolean): Boolean =
        content is CellContent.Wood && content.coil == 0 && !protectedByBiome
}
