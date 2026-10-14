package com.cukiti.teikafusion.model

/**
 * **¿UNA FILA COMPLETA DE LA TORMENTA SE ENERGIZA O SE ROMPE?** Fuente única (auditoría del tablero,
 * 2026-10-01) para el motor (`resolveLineClearPass`) y la vista previa del arrastre
 * (`previewClearLines`).
 *
 * Era «dos copias de un dibujo»: el motor pedía [LevelDirector.stormCoilCount] bloques lisos (4–7
 * según el nivel, P-352) y no energizaba nunca una fila cerrada con un Teika (2026-08-01); la vista
 * previa pedía 4 fijos y no miraba los Teikas. En un nivel de 5–7 bobinas, o cerrando con un Teika,
 * el aviso prometía un CIRCUITO y la fila se ROMPÍA.
 *
 * Una fila se energiza si: es ENTERA (las medias filas del corte ninja nunca), ningún Teika la
 * completa («los Teikas cortocircuitan la tormenta») y tiene al menos tantos bloques lisos como
 * bobinas lleva el circuito de este nivel.
 */
object StormCircuit {
    fun energizes(level: Int, wholeRow: Boolean, usesTeika: Boolean, plainWood: Int): Boolean =
        wholeRow && !usesTeika && plainWood >= LevelDirector.stormCoilCount(level)
}
