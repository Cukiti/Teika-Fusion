package com.cukiti.teikafusion.model

/**
 * TOPE DIARIO DE ANUNCIOS RECOMPENSADOS (P-18).
 *
 * El botón de monedas de la barra no tenía límite ni enfriamiento: se podían encadenar anuncios y
 * farmear teikoins sin pasar nunca por la tienda — justo lo contrario de la economía "tiburón" del
 * juego, y además el patrón que AdMob penaliza (usuarios que ven 40 anuncios seguidos ensucian el
 * eCPM y disparan alertas de tráfico inválido).
 *
 * Dos frenos, complementarios:
 *  · **[DAILY_LIMIT] al día**, que protege la economía;
 *  · **[COOLDOWN_MS] entre uno y otro**, que evita el encadenado compulsivo aunque queden usos.
 *
 * Puro y sin Android: la cuenta y el día viven en DataStore, pero la REGLA se prueba en la JVM.
 * **Los números son ECONOMÍA (decisión del usuario)**: aquí solo está el mecanismo.
 */
object RewardedAdQuota {

    /**
     * Anuncios de monedas por día. El revive NO gasta cupo: es una salida, no una fuente.
     *
     * **Hoy son 6** (8 → 6 el 2026-09-24, P-268: ver el comentario de la constante). Lo que sigue es
     * el porqué de los **8 del 2026-09-07** (P-208, decisión del usuario: «el recomendado, que no agobie pero
     * que la app genere bien en anuncios»). Historia del número: 5 → 10 el 2026-08-09, cuando **no
     * había tienda de teikoins** y el anuncio era la única fuente de moneda; esa premisa murió al
     * reabrir la tienda el 2026-09-06.
     *
     * Por qué 8 y no 5 ni 10, con [AdManager.REWARD_COINS] = 150:
     *  · **1.200 teikoins al día** de techo. El pack de entrada da 3.000 por 0,99 $, así que hacen
     *    falta unos dos días y medio de anuncios para igualarlo: la tienda sigue teniendo sentido y
     *    el que no paga tampoco se queda seco.
     *  · **Ocho impresiones diarias por jugador es un inventario sano**, y no es el número que
     *    agobia: el recompensado **lo pide el jugador**. Lo que se siente pesado es la publicidad
     *    que salta sola, y esa la gobierna `InterstitialCadence`, no este tope.
     *  · [COOLDOWN_MS] sigue evitando el encadenado compulsivo, que es lo que ensucia el eCPM y
     *    dispara las alertas de tráfico inválido de AdMob.
     */
    // **8 → 6 el 2026-09-24** (P-268, decisión del usuario), con el anuncio a 100: 600 al día, que
    // sigue siendo una ayuda pero ya no rinde más que jugar ~4 niveles.
    const val DAILY_LIMIT = 6

    /** Espera mínima entre dos anuncios de monedas. */
    const val COOLDOWN_MS = 45_000L

    /** Día del calendario al que pertenece [epochMs] (UTC-independiente: usa la zona del sistema). */
    fun dayKey(epochMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Int =
        java.time.Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().toEpochDay().toInt()

    /** Estado del cupo tal como lo guarda DataStore. */
    data class State(
        /** Día del último anuncio contabilizado (ver [dayKey]). */
        val day: Int = 0,
        /** Anuncios vistos ESE día. */
        val watched: Int = 0,
        /** Instante del último anuncio (para el enfriamiento). */
        val lastAtMs: Long = 0L
    )

    /** Usos que le quedan hoy. */
    fun remaining(state: State, nowMs: Long): Int {
        val today = dayKey(nowMs)
        val watchedToday = if (state.day == today) state.watched else 0
        return (DAILY_LIMIT - watchedToday).coerceAtLeast(0)
    }

    /** Milisegundos que faltan para poder ver otro (0 = ya). */
    fun cooldownLeftMs(state: State, nowMs: Long): Long =
        (state.lastAtMs + COOLDOWN_MS - nowMs).coerceAtLeast(0L)

    /** ¿Se le puede ofrecer AHORA un anuncio de monedas? */
    fun canWatch(state: State, nowMs: Long): Boolean =
        remaining(state, nowMs) > 0 && cooldownLeftMs(state, nowMs) == 0L

    /** Estado tras consumir un anuncio. Cambiar de día reinicia la cuenta. */
    fun consume(state: State, nowMs: Long): State {
        val today = dayKey(nowMs)
        val watchedToday = if (state.day == today) state.watched else 0
        return State(day = today, watched = watchedToday + 1, lastAtMs = nowMs)
    }
}
