package com.cukiti.teikafusion.model

/**
 * **P-436 · EL ORDEN DE LA TABLA** (2026-10-01). Firestore devuelve el top por NIVEL, y entre
 * empatados en el mismo nivel el orden era arbitrario: el 1.º, el 2.º y el 3.º podían tener el mismo
 * nivel y cambiar de sitio de una visita a otra.
 *
 * Ahora, puro y con test:
 *  - **Orden**: nivel (de más a menos), luego XP (de más a menos), luego uid (para que sea estable).
 *  - **Puesto**: el de cualquier clasificación deportiva — empatados en NIVEL comparten puesto, y el
 *    siguiente salta («1, 2, 2, 4»). El XP ordena dentro del empate, pero no rompe el puesto: el
 *    puesto mide el nivel, que es la vara del juego, y así casa con el recuento de fuera del top
 *    (`LeaderboardRepository.rankForLevel`: cuántos tienen MÁS nivel, + 1).
 */
object LeaderboardRanking {
    fun rank(entries: List<LeaderboardEntry>): List<LeaderboardEntry> {
        val sorted = entries.sortedWith(
            compareByDescending<LeaderboardEntry> { it.level }
                .thenByDescending { it.totalXp }
                .thenBy { it.uid }
        )
        var rank = 0
        var prevLevel: Int? = null
        return sorted.mapIndexed { index, e ->
            if (e.level != prevLevel) {
                rank = index + 1
                prevLevel = e.level
            }
            e.copy(rank = rank)
        }
    }
}
