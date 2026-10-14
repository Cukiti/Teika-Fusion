package com.cukiti.teikafusion.model

/**
 * Flora / Cañón Floral (rework it.54): estado del modo PUNTERÍA (capullo-cañón). Tras la ceremonia
 * de la flor, el capullo aparece en el BORDE INFERIOR del tablero (sobre el panel de fichas) cargado
 * con [teikaType] (el Teika de mayor tier, enterrado por la flor). El jugador solo mueve la LÍNEA de
 * puntería — el capullo la sigue por el borde — y al soltar dispara. La fuerza es fija: el nivel de
 * la habilidad da el nº de REBOTES del proyectil (L1=2 / L2=4 / L3=6). `null` = no se está apuntando.
 */
data class FloraAimState(
    val teikaType: TeikaType
)
