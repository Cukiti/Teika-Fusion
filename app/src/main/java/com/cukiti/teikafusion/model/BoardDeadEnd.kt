package com.cukiti.teikafusion.model

/**
 * **EL TABLERO NO ADMITE NINGUNA FICHA — ¿y ahora qué?** (P-105, 2026-09-02).
 *
 * Antes esto era un booleano (`GameViewModel.isStuck()`) y la respuesta a «no cabe nada» era
 * siempre la misma: la oferta de revivir. Eso escondía tres situaciones que no se parecen en nada —
 * un tablero que se desatasca inclinando el móvil, uno que se abre lanzando la habilidad que ya
 * llevas, y uno de verdad terminado— y, peor, la puerta que las separaba estaba mal medida: bastaba
 * con tener carga de CUALQUIER compañero reclamado para que el motor decidiera «esto todavía no es
 * derrota» y se quedara callado **para siempre**, aunque esa habilidad no pudiera abrir un solo
 * hueco ni el jugador la llevara en el cinturón (ver C-1 en MEJORAS.md).
 *
 * Ahora el motor responde con **qué salida hay**, y la pantalla la enseña. Las salidas van de la
 * más barata a la más cara, que es también el orden en que se ofrecen:
 *
 *  1. [Tilt] — hay colocación legal si un Teika rueda. Gratis: es un tablero que hay que mover.
 *  2. [Cast] — llevas en el cinturón una carga de una habilidad que **libera celdas**
 *     ([CompanionAbilityDef.freesSpace]) y que puede actuar sobre este tablero.
 *  3. [Buy] — esa habilidad la llevas pero sin carga, y te llegan los teikoins para comprar una.
 *  4. [None] — no hay nada. Esto sí es derrota: la ventana de «SIN ESPACIO».
 */
sealed interface BoardDeadEnd {

    /**
     * No cabe nada **ahora mismo**, pero sí cabría si un cuerpo se apartara. Los Teikas no son
     * celdas del tablero: son cuerpos que ruedan con la inclinación, y un T1 a caballo entre celdas
     * pisa hasta 2×2. Declarar derrota sin contar con eso era un falso positivo — el tablero estaba
     * vivo y el juego lo daba por muerto.
     */
    data object Tilt : BoardDeadEnd

    /** Lanza [companionId]: está en el cinturón, tiene carga y su efecto abre hueco. */
    data class Cast(val companionId: String) : BoardDeadEnd

    /** Compra una carga de [companionId] por [price] teikoins: la llevas, pero está a cero. */
    data class Buy(val companionId: String, val price: Int) : BoardDeadEnd

    /** Fichona puede proponer una forma legal sin perder anotaciones; el turno puede cambiar el tablero.
     * [build] se conserva en el tipo, pero el depósito individual ya no tiene productor activo.
     */
    data class Hold(val build: Boolean) : BoardDeadEnd

    /** Sin salida real. Rendirse o pagar la segunda oportunidad. */
    data object None : BoardDeadEnd
}

/**
 * **POR QUÉ has perdido** (C-4, 2026-09-02). La ventana de segunda oportunidad titulaba siempre
 * «¡Sin Movimientos!» y prometía «recupera 5 movimientos» en los tres casos — también cuando lo que
 * te falta no son turnos sino sitio, donde el texto sencillamente mentía. Es la pantalla que decide
 * si el jugador paga: tiene que decirle qué le ha pasado.
 *
 * No se guarda (`@Transient` en `GameState`): al recargar un save la oferta se replantea sola.
 */
enum class DefeatCause {
    /** Se agotó el presupuesto de turnos del nivel. Revivir devuelve movimientos. */
    SIN_TURNOS,
    /** Ninguna ficha cabe y no queda ninguna salida. Revivir despeja el tablero. */
    SIN_ESPACIO,
    /** La lava del Volcán cubrió el tablero entero. */
    LAVA;

    /** Motivo que viaja a analytics (`Analytics.trackLevelFail`). */
    val analyticsReason: String
        get() = when (this) {
            SIN_TURNOS -> "sin_turnos"
            SIN_ESPACIO -> "tablero_atascado"
            LAVA -> "lava_volcan"
        }
}
