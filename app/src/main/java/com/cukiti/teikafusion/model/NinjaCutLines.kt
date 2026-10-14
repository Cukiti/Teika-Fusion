package com.cukiti.teikafusion.model

/**
 * **Qué filas pueden completarse con el tablero partido** (Dojo en Sombras).
 *
 * Historia: hasta el 2026-09-23 cada fila partida se rompía por MITADES; ese día (pedido del usuario)
 * el corte pasó a no dejar completar **ninguna** fila, solo columnas. El bot midió el coste: Ninja al
 * 18 %, y ni curar la grieta antes ni una tregua tras curarla lo movieron (22 %) — con medio juego sin
 * filas el tablero se ahogaba. **2026-09-24 (P-254, pedido del usuario): vuelven las MEDIAS FILAS**:
 * con el corte, cada mitad (4 casillas) se rompe por su cuenta al completarse. Las columnas siguen
 * enteras dentro de su mitad; media fila y columna cuentan igual para reparar la grieta.
 *
 * Es la ÚNICA definición: la usan la resolución de líneas, la vista previa, la pureza de color y el
 * tinte. Cuatro copias de esta lista son cuatro sitios que pueden discrepar.
 */
object NinjaCutLines {

    /** Tramos de fila que pueden completarse: la fila entera, o sus dos mitades con el tablero partido. */
    fun rowSpans(cutActive: Boolean, width: Int): List<IntRange> =
        if (cutActive) listOf(0 until width / 2, width / 2 until width) else listOf(0 until width)

    /**
     * **Las medias filas, solo con un Teika** (2026-09-28, decisión del usuario): con el tablero
     * partido, una media fila completa se rompe únicamente si un Teika forma parte de ella (su cuerpo
     * cubre alguna de sus casillas). Las columnas no cambian. Sin corte, cualquier fila vale.
     */
    fun halfRowAllowed(cutActive: Boolean, cells: Collection<Position>, teikaCells: Set<Position>): Boolean =
        !cutActive || cells.any { it in teikaCells }
}
