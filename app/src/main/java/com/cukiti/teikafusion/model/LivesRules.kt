package com.cukiti.teikafusion.model

/**
 * **LAS VIDAS**: el tope y el ritmo de regeneración, en un sitio que puedan leer los dos lados.
 *
 * Vivían en el `companion object` de `GameViewModel`, y `PreferencesManager` —que es quien decide
 * con cuántas vidas arranca una instalación nueva— no puede importar el ViewModel sin invertir la
 * dependencia. Así que el valor por defecto estaba escrito como un **`5` a pelo** (P-136,
 * 2026-09-03): funcionaba porque coincidía, y habría dejado de funcionar en silencio el día que
 * alguien subiera el tope, con el jugador nuevo arrancando a 5 mientras el resto del juego
 * consideraba «lleno» otra cifra.
 *
 * `GameViewModel.LIVES_MAX` y `LIVES_REGEN_MS` siguen existiendo como alias para no tocar sus ~20
 * usos internos ni el de la tienda; lo que ya no existe es una segunda copia del número.
 */
object LivesRules {

    /** Vidas que caben. Es también con cuántas arranca una instalación nueva. */
    const val MAX = 5

    /** Cada cuánto se repone una vida. */
    const val REGEN_MS = 30 * 60 * 1000L

    /**
     * Estado de las vidas tras aplicar el tiempo transcurrido.
     *
     * [changed] existe para que el llamante sepa si tiene que **persistir**: sin él habría que
     * comparar los dos campos a mano en los dos sitios, que es justo la clase de duplicación que
     * esta extracción vino a quitar.
     */
    data class Regen(val lives: Int, val nextRegenAt: Long, val changed: Boolean)

    /**
     * **CUÁNTAS VIDAS HAN VUELTO** desde [nextRegenAt] hasta [now].
     *
     * Esta aritmética estaba escrita **dos veces palabra por palabra** —al cargar la partida y en el
     * bucle que comprueba cada 30 s—, guarda incluida. Dos copias de una regla con reloj de pared es
     * el sitio perfecto para que una se arregle y la otra no: de hecho la guarda que llevan **nació
     * de un bug** (ver abajo), y hubo que ponerla en las dos.
     *
     * ## La guarda que parece de más y no lo es
     *
     * `now >= nextRegenAt` **no** es redundante con el resto. Sin ella, reabrir la app con el
     * temporizador aún corriendo regeneraba una vida gratis: la resta da negativo, la división
     * también, `coerceAtLeast(0)` la sube a 0 y el `+ 1` devuelve la vida que el jugador acababa de
     * perder. El `+ 1` es correcto —cuando el instante ya venció, esa primera vida ES una de las
     * que toca devolver—, pero solo si de verdad venció.
     *
     * Corolario útil: con el reloj del móvil movido **hacia atrás** no se regenera nada en vez de
     * regenerarse de más. Hacia delante sí se puede forzar (es una economía local y sin servidor,
     * y las vidas no viajan a la nube — ver P-114), pero eso es una decisión asumida, no un
     * descuido de esta función.
     *
     * @return el estado ya aplicado; [Regen.changed] es `false` cuando no tocaba reponer nada y el
     *         llamante puede saltarse la escritura en disco.
     */
    fun regenerate(lives: Int, nextRegenAt: Long, now: Long): Regen {
        if (lives >= MAX || nextRegenAt <= 0L || now < nextRegenAt) {
            return Regen(lives, nextRegenAt, changed = false)
        }
        // El tope de MAX no cambia ningún resultado —si hicieran falta MAX o más, las vidas quedan
        // llenas y el instante se pone a 0— y evita que un reloj disparatado desborde el Int.
        val regenCount = ((now - nextRegenAt) / REGEN_MS).coerceIn(0L, MAX.toLong()).toInt() + 1
        val newLives = (lives + regenCount).coerceAtMost(MAX)
        val newNextRegen = if (newLives >= MAX) 0L else nextRegenAt + regenCount * REGEN_MS
        return Regen(newLives, newNextRegen, changed = true)
    }
}
