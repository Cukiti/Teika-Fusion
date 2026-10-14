package com.cukiti.teikafusion.model

/**
 * PUNTERÍA AUTOMÁTICA del Cañón Floral (rework de habilidades pasivas, 2026-07-25).
 *
 * Flora era la única habilidad que, tras entrar, seguía pidiendo una acción al jugador: mover la
 * línea de puntería del capullo y soltar para disparar. Con las habilidades convertidas en pasivas
 * que se disparan solas, esa línea se quedaba colgada esperando un gesto que ya no toca dar — así
 * que el capullo apunta él mismo.
 *
 * A dónde: al **rincón de madera más denso**, que es lo que el jugador habría elegido. La regla se
 * saca aquí, pura, porque tiene dos decisiones finas que conviene poder probar sin arrancar el juego:
 *
 *  1. El capullo se coloca bajo la **columna con más madera**, no bajo el centro de masas: disparar
 *     desde debajo del montón es lo que garantiza que el proyectil lo atraviese entero.
 *  2. Si el tiro sale casi VERTICAL, se le mete un ángulo mínimo ([MIN_TILT]) hacia el lado con más
 *     madera. Un disparo perfectamente vertical rebota arriba y abajo por el mismo pasillo y
 *     desperdicia los rebotes; con un grado de inclinación, recorre el tablero.
 */
object FloraAutoAim {

    /** Inclinación mínima horizontal del disparo (en unidades de dirección ya normalizada). */
    const val MIN_TILT = 0.35f

    /** Componente vertical del disparo: siempre hacia ARRIBA (el capullo vive en el borde bajo). */
    private const val UP = -1f

    /**
     * @param cocoonX celda x (con decimales) donde se planta el capullo.
     * @param dirX dirección horizontal del disparo (positiva = a la derecha).
     * @param dirY dirección vertical (siempre negativa: hacia arriba).
     */
    data class Aim(val cocoonX: Float, val dirX: Float, val dirY: Float)

    /**
     * Puntería para el tablero dado. [wood] son las celdas con madera del jugador. Sin madera
     * dispara al centro, en vertical: no hay nada mejor que hacer y el tiro sigue siendo legible.
     */
    fun aim(wood: Collection<Position>, gridSize: Int = GameState.GRID_SIZE): Aim {
        val mid = gridSize / 2f
        if (wood.isEmpty()) return normalize(mid, MIN_TILT, UP)

        // Columna más cargada = de dónde se dispara. Empate → la más cercana al centro, que deja
        // más tablero por delante del proyectil.
        val perCol = IntArray(gridSize)
        for (p in wood) if (p.x in 0 until gridSize) perCol[p.x]++
        var bestCol = 0
        for (x in 1 until gridSize) {
            val better = perCol[x] > perCol[bestCol] ||
                (perCol[x] == perCol[bestCol] &&
                    kotlin.math.abs(x - mid) < kotlin.math.abs(bestCol - mid))
            if (better) bestCol = x
        }
        val cocoonX = bestCol + 0.5f

        // Centro de masas de la madera: hacia ahí apunta el cañón.
        var sumX = 0f
        var sumY = 0f
        for (p in wood) { sumX += p.x + 0.5f; sumY += p.y + 0.5f }
        val cx = sumX / wood.size
        val cy = sumY / wood.size

        var dx = cx - cocoonX
        // El objetivo SIEMPRE queda por encima del capullo (que está en el borde inferior); se fuerza
        // por si la masa de madera está pegada al suelo y el vector saldría plano o hacia abajo.
        val dy = kotlin.math.min(cy - gridSize, -0.5f)

        // Tiro casi vertical → se inclina hacia el lado que más madera tiene, para que los rebotes
        // barran tablero en vez de repetir el mismo pasillo.
        if (kotlin.math.abs(dx) < 0.5f) {
            var left = 0
            var right = 0
            for (p in wood) if (p.x + 0.5f < cocoonX) left++ else right++
            dx = if (right >= left) 1f else -1f
        }
        return normalize(cocoonX, dx, dy)
    }

    private fun normalize(cocoonX: Float, dx: Float, dy: Float): Aim {
        val len = kotlin.math.hypot(dx, dy).coerceAtLeast(1e-4f)
        var nx = dx / len
        var ny = dy / len
        if (kotlin.math.abs(nx) < MIN_TILT) {
            nx = MIN_TILT * (if (nx < 0f) -1f else 1f)
            ny = -kotlin.math.sqrt((1f - nx * nx).coerceAtLeast(0f))
        }
        return Aim(cocoonX, nx, ny)
    }
}
