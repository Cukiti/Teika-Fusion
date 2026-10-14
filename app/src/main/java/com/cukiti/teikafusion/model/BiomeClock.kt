package com.cukiti.teikafusion.model

/**
 * **EL RELOJ QUE SE CONGELA** (decisión del usuario, 2026-09-23). Regla única de lo que le pasa al
 * contador ocioso de un bioma cuando el jugador rompe una línea.
 *
 * Antes, romper una línea en CUALQUIER turno ponía el reloj a cero, así que el aviso se apagaba en
 * cuanto el jugador limpiaba algo y la mecánica casi nunca llegaba. Ahora:
 *
 *  - **Turno de AVISO + línea rota → el reloj se CONGELA**: ni avanza ni se apaga. El aviso sigue
 *    encendido y, al siguiente turno sin línea, continúa donde se quedó.
 *  - **Turno de EJECUTAR + línea rota → se CANCELA**: vuelve a cero. Es el único momento en que
 *    romper una línea apaga la mecánica — el jugador tiene que salvarse justo a tiempo.
 *  - **Sin línea → avanza** un paso, como siempre.
 *
 * ```
 * Volcán        sin línea → aviso 1 · rompe fila → sigue en aviso 1 · sin línea → aviso 2
 *               ¿rompe fila? sí → se apaga (0) · no → LA LAVA BAJA
 * ```
 *
 * Vale para todos los biomas que avisan (Sueño, Jardín, Volcán, Ninja, Mansión, Cocina Voraz, Cielo
 * Estelar, Trono Real, Espacio Profundo). No toca a los que no se cancelan con líneas por decisión
 * propia (Glaciar, Tóxico) ni a los que no tienen reloj (Cristal, Tormenta, Pasaje).
 */
object BiomeClock {

    /**
     * ¿Es ESTE turno el de ejecutar? El contador ejecuta cuando llega a [executeAt]; si el turno
     * contara, llegaría ahora.
     */
    fun isExecutionTurn(idle: Int, executeAt: Int): Boolean = idle + 1 >= executeAt

    /** El contador tras un turno en que SÍ se rompió línea: cancela en el de ejecutar, congela en los demás. */
    fun onLineCleared(idle: Int, executeAt: Int): Int =
        if (isExecutionTurn(idle, executeAt)) 0 else idle

    /** El contador tras un turno cualquiera. */
    fun advance(idle: Int, lineCleared: Boolean, executeAt: Int): Int =
        if (lineCleared) onLineCleared(idle, executeAt) else idle + 1
}
