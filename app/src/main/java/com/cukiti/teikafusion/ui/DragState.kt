package com.cukiti.teikafusion.ui

import com.cukiti.teikafusion.model.Piece
import com.cukiti.teikafusion.model.Position

sealed class DragState {
    data object Idle : DragState()
    data class Dragging(
        val piece: Piece,
        val ghostGridPosition: Position?,
        val isValidPlacement: Boolean,
        // NIVELES: filas/columnas que se COMPLETARÍAN si la pieza se suelta aquí (preview premium
        // de rotura — flores giratorias + glow arcoíris con combo). Vacías si no rompe nada.
        val clearRows: List<Int> = emptyList(),
        val clearCols: List<Int> = emptyList(),
        // CORTE NINJA: cada fila del preview con su TRAMO horizontal real (mitad izquierda, mitad
        // derecha o fila entera). El VFX dibuja EXACTAMENTE estos tramos — antes se enmascaraba "la
        // otra mitad" adivinando por la X del ghost y el aviso podía encenderse en la mitad
        // equivocada (bugtablero 2026-07-20). Sin corte: tramo = fila entera.
        val clearRowSpans: List<Pair<Int, IntRange>> = emptyList(),
        // TORMENTA/VOLCÁN: filas que al completarse NO se rompen sino que se ENERGIZAN en un circuito
        // de bobinas (hay ≥4 madera lisa). Se telegrafían con un aviso ELÉCTRICO distinto del de rotura
        // — el jugador ve que "esto se va a volver bobinas", no que va a romper. (Las columnas nunca
        // energizan, sólo las filas → no hace falta energizeCols.)
        val energizeRows: List<Int> = emptyList(),
        // DESEO ESTELAR: celdas que el bloque-estrella LLENARÍA al soltarse aquí (expansión líquida
        // desde la celda soltada, acotada por muros/bloques y un tope por nivel). Sombra de preview.
        val starFillCells: List<Position> = emptyList(),
        /**
         * **P-16**: celdas concretas que IMPIDEN soltar aquí (madera, muro, cuerpo de Teika, la otra
         * mitad del corte ninja). El ghost sigue sin pintarse en rojo cuando no cabe —decisión
         * congelada, estilo Block Blast—; lo que faltaba era el PORQUÉ: la sombra desaparecía en
         * silencio y el jugador no sabía si era por un cuerpo, por el muro o por medio bloque. Se
         * marcan SOLO las celdas que chocan, nunca la silueta entera.
         */
        val blockedCells: List<Position> = emptyList(),
        /**
         * **P-80 · NIVEL CON GRAVEDAD**: dónde acaban DE VERDAD los bloques de la ficha al soltarla
         * aquí, una vez el tablero se asienta. Vacío en los niveles normales (la ficha se queda
         * donde la sueltas y la silueta ya dice la verdad).
         *
         * Sin esto la sombra mentiría en el único nivel del capítulo en que se mueve la madera: el
         * jugador apuntaría a una fila que la caída va a deshacer. Ojo, no es la silueta de la ficha
         * desplazada en bloque —cada bloque cae por SU columna—, así que son celdas sueltas.
         */
        val landingCells: List<Position> = emptyList(),
        /**
         * **P-147 · LA FICHA ESPEJO**: dónde caería el REFLEJO si sueltas aquí. Vacío en todos los
         * demás niveles.
         *
         * Sin esto la regla sería una trampa: la mitad de tu jugada ocurre al otro lado del tablero
         * y no la ves hasta que ya la has hecho. El reflejo es «mejor esfuerzo» —solo pisa celdas
         * libres— así que **cuántas celdas coge depende de cómo esté ese lado**, y eso es
         * exactamente lo que el jugador necesita saber antes de decidir. Se calcula con la MISMA
         * regla que lo estampa ([MirrorPiece.reflectionsOf]), que es lo que hace que lo que ves sea
         * lo que sale.
         */
        val mirrorCells: List<Position> = emptyList(),
        /**
         * **P-236 · LA IMPLOSIÓN**: los bloques que las líneas de esta colocación aspirarían (origen →
         * destino). Lo calcula el ViewModel con la MISMA regla y las mismas anclas que el paso real.
         * Vacío fuera de la regla o si la colocación no rompe lo bastante.
         */
        val implosionPulls: List<Pair<Position, Position>> = emptyList()
        // (PODADO 2026-07-31, bloque 5) Aquí vivían `ghostStompCells` / `ghostStompLimit`, de la
        // FICHA FANTASMA del Infinito: se retiró con los pactos, su productor los rellenaba con
        // `emptyList()`/0, y las tres capas de UI que los leían no podían dibujarse nunca.
    ) : DragState()
}
