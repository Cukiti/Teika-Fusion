package com.cukiti.teikafusion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.cukiti.teikafusion.model.GameEvent

/**
 * LOS EVENTOS, AGRUPADOS UNA SOLA VEZ.
 *
 * ## El problema que resuelve
 *
 * `GameBoard` y `GameScreen` tenían **68** bloques `remember(gameState.events) { …filterIsInstance… }`
 * distintos. Todos comparten la misma clave —la lista de eventos—, así que **cualquier** cambio en
 * ella invalidaba los 68 y disparaba 68 recorridos O(n) con sus 68 listas nuevas.
 *
 * En NIVELES apenas se notaba: los eventos llegan a cuentagotas. En WALLBLOCK sí, y por dos motivos a
 * la vez: una oleada de cascada añade **decenas** de `LineCellShatter` de golpe, y la cola se vacía
 * cada 32 ms con el flush de consumo. Es decir, la lista cambia constantemente **y** es larga
 * justamente en el momento de más carga gráfica — que es exactamente cuando el playtest reportaba los
 * tirones (2026-08-09).
 *
 * ## La solución
 *
 * Un `groupBy` por clase concreta **una vez por cambio de lista**, y después cada consumidor hace una
 * búsqueda en mapa. 68 recorridos pasan a 1.
 *
 * Agrupar por clase EXACTA es correcto aquí porque todos los eventos son `data class` hoja de la
 * sellada `GameEvent`: no hay jerarquías intermedias que un `filterIsInstance` recogiera y este mapa
 * no. Si alguna vez se añade un evento intermedio del que otros hereden, esto deja de servir para él
 * — por eso [of] documenta la condición en vez de esconderla.
 *
 * El orden dentro de cada tipo se conserva (`groupBy` respeta el orden de aparición), que es lo que
 * necesitan los consumidores que usan `firstOrNull` / `lastOrNull`.
 *
 * ## La misma lista si nada cambió (perf 2026-09-26)
 *
 * Con [previous], un tipo cuyos eventos son **los mismos objetos, en el mismo orden** que en la
 * agrupación anterior devuelve la lista ANTERIOR, no una copia igual. Importa porque Compose compara
 * las listas por identidad: cuando llega o se consume un evento de OTRO tipo, el VFX de un tipo que no
 * ha cambiado recibe la misma instancia y se salta la recomposición. Durante una rotura de líneas la
 * cola cambia cada 32 ms (el flush de consumo) y sin esto cada VFX vivo se recomponía en cada cambio.
 */
@Stable
class GameEventBuckets(events: List<GameEvent>, previous: GameEventBuckets? = null) {
    private val byType: Map<Class<out GameEvent>, List<GameEvent>> =
        events.groupBy<GameEvent, Class<out GameEvent>> { it.javaClass }.let { fresh ->
            if (previous == null) fresh
            else fresh.mapValues { (type, list) ->
                val old = previous.byType[type]
                if (old != null && old.sameInstancesAs(list)) old else list
            }
        }

    /** Eventos de la clase EXACTA [type], en orden de llegada. Lista vacía si no hay ninguno. */
    @Suppress("UNCHECKED_CAST")
    fun <T : GameEvent> of(type: Class<T>): List<T> =
        (byType[type] ?: emptyList<GameEvent>()) as List<T>
}

/** Azúcar para poder escribir `buckets.of<GameEvent.FusionBurst>()`. */
inline fun <reified T : GameEvent> GameEventBuckets.of(): List<T> = of(T::class.java)

/**
 * Agrupa la cola de eventos. Se llama UNA vez por composable de pantalla; todos los consumidores de
 * dentro leen de aquí en vez de recorrer la lista cada uno por su cuenta.
 */
@Composable
fun rememberEventBuckets(events: List<GameEvent>): GameEventBuckets {
    // La agrupación anterior, para reutilizar las listas que no cambian (ver [GameEventBuckets]).
    val last = remember { arrayOfNulls<GameEventBuckets>(1) }
    return remember(events) { GameEventBuckets(events, last[0]).also { last[0] = it } }
}

/** ¿Los mismos objetos, en el mismo orden? Identidad, no igualdad: es la que mira Compose. */
private fun List<GameEvent>.sameInstancesAs(other: List<GameEvent>): Boolean {
    if (size != other.size) return false
    for (i in indices) if (this[i] !== other[i]) return false
    return true
}
