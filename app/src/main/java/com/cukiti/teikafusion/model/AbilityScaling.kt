package com.cukiti.teikafusion.model

/**
 * **CUÁNTO HACE CADA HABILIDAD EN CADA NIVEL** (P-270, 2026-09-24).
 *
 * Fuente única: antes cada habilidad llevaba su `when (level) { 1 -> …; 2 -> …; else -> … }` suelto
 * en el ViewModel, con un `coerceIn(1, 3)` delante. Con cinco niveles eso eran doce sitios que podían
 * discrepar de los textos de [COMPANION_ABILITY_LEVEL_TEXTS]; aquí están juntos y un test los mira.
 *
 * **Los niveles 1–3 son exactamente los de antes.** El 4 y el 5 van por encima: más de lo mismo donde
 * hay margen, y **eco** ([echoChance]) donde el 3 ya lo abarcaba todo («TODOS», «TODO el tablero»).
 */
object AbilityScaling {

    private fun pick(level: Int, vararg values: Int): Int =
        values[(level.coerceIn(1, COMPANION_ABILITY_MAX_LEVEL) - 1).coerceAtMost(values.size - 1)]

    /** Cristal: reventones que encadena la auto-colocación. */
    fun cristalChains(level: Int) = pick(level, 1, 2, 3, 4, 5)

    /** Dormilona: filas llenas que caen. */
    fun dormilonaRows(level: Int) = pick(level, 1, 2, 3, 4, 5)

    /** Miedoso: pasadas del rompecabezas. */
    fun miedosoPasses(level: Int) = pick(level, 4, 7, 12, 16, 20)

    /** Planta: rebotes del disparo. */
    fun plantaBounces(level: Int) = pick(level, 2, 4, 6, 8, 10)

    /** Hielo: carámbanos (columnas perforadas). */
    fun hieloIcicles(level: Int) = pick(level, 3, 4, 5, 6, 7)

    /** Fantasma: bloques que posee. */
    fun fantasmaMoves(level: Int) = pick(level, 3, 5, 8, 10, 12)

    /** Estrella: casillas que llena el Deseo Estelar. */
    fun estrellaCap(level: Int) = pick(level, 8, 12, 18, 24, 30)

    /** Glotón: bloques con estrella que se traga (MAX_VALUE = todos). */
    fun glotonBites(level: Int) = pick(level, 4, 8, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)

    /** Ninja: bloques con estrella que parte (MAX_VALUE = todos) y tajos que se ven. */
    fun ninjaVictims(level: Int) = pick(level, 4, 8, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)
    fun ninjaSlashes(level: Int) = pick(level, 3, 5, 7, 7, 7)

    /** Fuego: fichas que se prenden (MAX_VALUE = toda la bandeja). */
    fun fuegoPieces(level: Int) = pick(level, 1, 2, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)

    /** Eléctrico: Teikas que energiza (MAX_VALUE = todos). */
    fun electricoTargets(level: Int) = pick(level, 1, 2, Int.MAX_VALUE, Int.MAX_VALUE, Int.MAX_VALUE)

    /**
     * Nivel «de fuerza» para las reglas que solo conocen 1..3 (Tóxico, Rey, Galáctico): del 3 en
     * adelante la habilidad ya lo hace todo; lo que ganan el 4 y el 5 es el eco.
     */
    fun cappedAt3(level: Int) = level.coerceIn(1, 3)

    /** Compañeros cuyo nivel 3 ya lo abarca todo: en el 4 y el 5 ganan eco. */
    private val ECHO_IDS = setOf("fuego", "electrico", "toxico", "gloton", "rey", "ninja", "galactico")

    /**
     * **ECO**: probabilidad de que usar la habilidad NO gaste la carga. 25 % en el nivel 4, 50 % en
     * el 5, solo para las habilidades que ya lo hacían todo en el 3 ([ECHO_IDS]).
     */
    fun echoChance(companionId: String, level: Int): Float {
        if (companionId !in ECHO_IDS) return 0f
        return when (level.coerceIn(1, COMPANION_ABILITY_MAX_LEVEL)) {
            4 -> 0.25f
            5 -> 0.50f
            else -> 0f
        }
    }
}
