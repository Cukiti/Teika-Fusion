package com.cukiti.teikafusion.model

/**
 * Reglas PURAS del Parto Real de Reynaldo (habilidad de compañero, rework 2026-07-17).
 *
 * Al castear, los Teikas del tablero paren un Teika de un tier INFERIOR (T2→T1, T3→T2, …). Solo
 * paren los que tienen descendiente (tier ≥ 2). El nivel de habilidad escala CUÁNTOS Teikas paren:
 *   · L1 → solo el de mayor tier (1)
 *   · L2 → la mitad, redondeando hacia arriba
 *   · L3 → todos los elegibles
 *
 * Extraído a `model/` para testear el reparto sin instanciar el ViewModel (patrón del repo).
 */
object BirthDecreeRules {

    /** Cuántos Teikas paren para [level] (1..3), dado [eligibleCount] cuerpos que pueden parir. */
    fun birthCount(eligibleCount: Int, level: Int): Int {
        if (eligibleCount <= 0) return 0
        return when (level.coerceIn(1, 3)) {
            1    -> 1
            2    -> (eligibleCount + 1) / 2   // mitad, redondeo arriba
            else -> eligibleCount
        }
    }
}
