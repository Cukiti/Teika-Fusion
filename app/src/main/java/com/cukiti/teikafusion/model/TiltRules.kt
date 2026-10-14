package com.cukiti.teikafusion.model

/**
 * INCLINACIÓN de los Teikas — reglas puras del sensor (fix `nivelbug`, D-TILT 2026-07-25).
 *
 * El bug reportado: inclinar el teléfono en NIVELES no movía los Teikas aunque tuvieran hueco al
 * lado. Tres causas, y las tres viven aquí o en sus constantes:
 *
 *  1. **El eje del sensor no es el eje de la pantalla.** El sensor da su X en el marco del
 *     dispositivo; lo que el jugador entiende por "izquierda" es la X de la PANTALLA. Coinciden solo
 *     con la pantalla en su rotación natural. La app está bloqueada en vertical, así que hoy
 *     coinciden — pero era una bomba de relojería y el remapeo cuesta cuatro líneas ([screenX]).
 *  2. **Zona muerta demasiado ancha** (0,08 g ≈ 4,6°) y **filtro paso-bajo demasiado lento**
 *     (α = 0,18: ~10 muestras para llegar al 85 % del valor real). Entre las dos, una inclinación
 *     corta se comía casi entera antes de llegar a la física.
 *  3. **Respuesta lineal**: tras reescalar la zona muerta, inclinar 25° daba una fracción tan
 *     pequeña de la gravedad lateral que la fricción de contacto se la comía. [RESPONSE_EXP] curva
 *     la respuesta para que el tramo útil (20–40°) empuje de verdad.
 *
 * Criterio de aceptación del brief: inclinar ≥25° con hueco lateral mueve un T1/T2 ≥0,3 celdas en
 * ≤0,5 s, y el teléfono plano sobre la mesa no produce deriva.
 */
object TiltRules {

    /** Zona muerta en fracción de g (≈2,3°). Antes 0,08 — se comía el tramo útil. */
    const val DEADZONE = 0.04f

    /** Coeficiente del paso-bajo por muestra. Antes 0,18: demasiada inercia para un gesto corto. */
    const val LOW_PASS_ALPHA = 0.30f

    /**
     * Exponente de la curva de respuesta. **1,0 = respuesta PROPORCIONAL al ángulo real.**
     *
     * Estuvo en 0,75 para que 20–40° vencieran la fricción de contacto de entonces. Esa fricción ya
     * no existe (se aplicaba seis veces por frame; ver [TeikaPhysicsWorld.FRICTION_TANGENTIAL]), y
     * la curva se quedó como un multiplicador fantasma: con 0,75, inclinar 8° ya daba la mitad del
     * empuje de inclinar 30°, así que el más mínimo ladeo lanzaba los Teikas de un lado a otro.
     *
     * El sensor YA entrega la componente horizontal de la gravedad (`g·sin θ`). Escalarla lineal es
     * lo físicamente correcto y es lo que hace que el gesto tenga sentido: el doble de ángulo, el
     * doble de empuje. La velocidad la limita ahora la resistencia a la rodadura del mundo
     * (`TILT_ROLL_DRAG`), no una curva que falsea la entrada.
     */
    const val RESPONSE_EXP = 1.0f

    /**
     * X del sensor en el marco de la PANTALLA, según la rotación del display en grados
     * (0 · 90 · 180 · 270, tal cual los devuelve `Display.rotation` traducido).
     *
     * @param x componente X del sensor de gravedad (marco del dispositivo).
     * @param y componente Y del sensor de gravedad (marco del dispositivo).
     */
    fun screenX(x: Float, y: Float, rotationDeg: Int): Float = when (rotationDeg) {
        90 -> -y
        180 -> -x
        270 -> y
        else -> x
    }

    /**
     * Convierte la lectura de gravedad ya en marco de pantalla ([screenGravityX], m/s², convención
     * Android: lado IZQUIERDO abajo → +X) en la gravedad HORIZONTAL del mundo físico, en celdas/s².
     *
     * Signo: sensor +X (izquierda abajo) → los cuerpos ruedan a la IZQUIERDA (−x del mundo).
     * La zona muerta se REESCALA (no se resta a secas): así no hay salto al cruzar el umbral.
     */
    fun gravityX(
        screenGravityX: Float,
        max: Float,
        /**
         * **P-156 · LA INCLINACIÓN LIBRE** (2026-09-04): cuánto multiplica el nivel la respuesta del
         * gesto, y con qué zona muerta. Los valores por defecto son el mundo de siempre, así que los
         * ~27 de cada 28 niveles que no llevan la regla entran por el mismo camino de antes.
         *
         * El resultado se sigue acotando a [max]: la regla hace que el tablero obedezca antes, no
         * que los cuerpos salgan disparados a una velocidad que el sim no sabe resolver.
         */
        response: Float = 1f,
        deadzone: Float = DEADZONE,
    ): Float {
        val norm = (-screenGravityX / 9.81f).coerceIn(-1f, 1f)
        val mag = kotlin.math.abs(norm)
        if (mag < deadzone) return 0f
        val t = (mag - deadzone) / (1f - deadzone).coerceAtLeast(0.0001f)
        val curved = Math.pow(t.toDouble(), RESPONSE_EXP.toDouble()).toFloat()
        return (curved * response).coerceIn(0f, 1f) * max * kotlin.math.sign(norm)
    }
}
