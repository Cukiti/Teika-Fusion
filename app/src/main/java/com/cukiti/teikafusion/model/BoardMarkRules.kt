package com.cukiti.teikafusion.model

import kotlin.random.Random

/**
 * **FAMILIA E DE LAS REGLAS DE LA CASA: LAS MARCAS DEL TABLERO** (2026-09-04, aprobada por el usuario
 * en el panel del catálogo).
 *
 * Las tres reaprovechan un vocabulario que el juego ya tiene —celdas marcadas que dicen «aquí pasa
 * algo»— y le añaden lo que le faltaba: **que las marcas signifiquen cosas distintas**. Hasta ahora
 * el tablero solo sabía decir dos: «esto paga» (la caliente, P-78) y «trae un Teika aquí» (la
 * portería, P-81), y las dos salían en casi todos los niveles, así que habían dejado de marcar nada.
 *
 * ## La regla que las tres respetan
 *
 * **Una marca por celda.** Es una decisión que este proyecto ya tuvo que arreglar dos veces —el
 * testarudo que se convertía en bobina (P-131) y la caliente que caía encima de la portería
 * (2026-09-02)— y las dos veces por lo mismo: dos marcas en el mismo cuadro son dos mensajes
 * contradictorios en el mismo sitio. Aquí se cumple por construcción, porque el catálogo reparte una
 * sola regla por nivel y estas tres son entradas distintas del catálogo.
 */

/* (RETIRADO 2026-09-22) `PrizedRow` — LA FILA PREMIADA (P-157a). La sustituye LA PIÑATA (P-242):
 * en vez de una fila que paga más puntos, un premio que se ve y que cae sobre el tablero. Ver
 * [Pinata]. */

/* (RETIRADO 2026-09-22) `MovingGoal` — LA PORTERÍA MÓVIL (P-157b). La sustituyen LOS PORTALES
 * (P-243), ver [Portals]. Su recorrido del borde se mudó a [BoardCurrent.ringOf], que lo usa la
 * noria. */

/* (RETIRADO 2026-09-22) `ColorCells` — LAS CELDAS DE COLOR (P-157c): casillas que solo aceptaban
 * madera de su color. Era la única regla del catálogo que rechazaba una colocación, y el aviso de
 * rechazo no la contaba: la ficha volvía a la bandeja en silencio (auditoría del 2026-09-16). La
 * sustituye LA VIDRIERA, que no bloquea nada — ver [StainedGlass]. */
