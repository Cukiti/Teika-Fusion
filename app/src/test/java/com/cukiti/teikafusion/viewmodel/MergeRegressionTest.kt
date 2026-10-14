package com.cukiti.teikafusion.viewmodel

import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.model.TeikaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regresiones de helpers de fusión/empuje en **grid** (pre-física continua).
 *
 * El juego ya fusiona Teikas con [TeikaPhysicsWorld]; estas funciones ya no viven en main.
 * Se conservan aquí como algoritmos puros + tests, para no perder el contrato de:
 *  - punto de encuentro visual de fusión adyacente
 *  - cascada de empujes en una dirección
 *  - empuje/muerte por avance de lava
 *
 * Si se reintroduce lógica de grid-push, estos tests son la especificación.
 */

// ── Helpers bajo test (antes en main/viewmodel; hoy solo usados por este archivo) ──

data class VisualMeeting(val x: Float, val y: Float)

/**
 * Punto visual donde dos Teikas se encuentran al fusionar.
 * Adyacentes (pathSize == 1): punto medio fraccionario entre orígenes.
 * Con ruta más larga: se mantiene el [gridMeeting] del pathfinder.
 */
internal fun resolveVisualMeeting(
    from1: Position,
    from2: Position,
    pathSize: Int,
    gridMeeting: Position
): VisualMeeting {
    if (pathSize == 1) {
        return VisualMeeting(
            x = (from1.x + from2.x) / 2f,
            y = (from1.y + from2.y) / 2f
        )
    }
    return VisualMeeting(gridMeeting.x.toFloat(), gridMeeting.y.toFloat())
}

/** Si la lista está vacía, usa [fallback]; si no, devuelve this. */
internal fun <T> List<T>.withFallback(fallback: () -> List<T>): List<T> =
    if (isEmpty()) fallback() else this

/**
 * Tras liberar un pasillo de fusión: espera corta, o larga si hay absorción gemela
 * (animación de dos Teikas → uno) para no solapar el siguiente movimiento.
 */
internal fun corridorReleaseDelayAfterCompletion(hasTwinAbsorption: Boolean): Long =
    if (hasTwinAbsorption) 2_850L else 50L

data class PushCascadeBody(
    val id: String,
    val type: TeikaType,
    val cell: Position
)

/**
 * Empuja el Teika [startId] en [delta]. Si hay una cadena de Teikas en esa dirección,
 * todos avanzan un paso. Devuelve destinos por id, o null si el último no cabe
 * (borde del tablero, lava, u ocupación).
 */
internal fun planPushCascade(
    startId: String,
    delta: Position,
    bodies: List<PushCascadeBody>,
    boardSize: Int,
    lavaRow: Int = 0
): Map<String, Position>? {
    val byId = bodies.associateBy { it.id }
    val byCell = bodies.associateBy { it.cell }
    val start = byId[startId] ?: return null

    val chain = mutableListOf<PushCascadeBody>()
    var current = start
    val visited = mutableSetOf<String>()
    while (current.id !in visited) {
        visited.add(current.id)
        chain.add(current)
        val nextCell = Position(current.cell.x + delta.x, current.cell.y + delta.y)
        val next = byCell[nextCell] ?: break
        current = next
    }

    val last = chain.last()
    val lastDest = Position(last.cell.x + delta.x, last.cell.y + delta.y)
    if (lastDest.x !in 0 until boardSize || lastDest.y !in 0 until boardSize) return null
    if (lastDest.y < lavaRow) return null
    // Destino final debe estar libre (no otro Teika fuera de la cadena).
    val chainIds = chain.map { it.id }.toSet()
    val occupant = byCell[lastDest]
    if (occupant != null && occupant.id !in chainIds) return null

    return chain.associate { body ->
        body.id to Position(body.cell.x + delta.x, body.cell.y + delta.y)
    }
}

/** Retardo tipo dominó por profundidad de cascada (tope 220 ms). */
internal fun pushCascadeDelayMillis(depth: Int): Int =
    (depth * 55).coerceIn(0, 220)

data class LavaPushPlan(
    val destinations: Map<String, Position>,
    val killedIds: Set<String>
)

/**
 * Al avanzar la lava [rowsToAdvance] filas, cada Teika baja esa cantidad.
 * Si cae fuera del tablero, muere ([killedIds]); si no, va a [destinations].
 */
internal fun planLavaPushDown(
    bodies: List<PushCascadeBody>,
    newLavaRow: Int,
    rowsToAdvance: Int,
    boardSize: Int
): LavaPushPlan {
    // newLavaRow se conserva en la firma por compatibilidad con el contrato original;
    // el empuje depende de rowsToAdvance (todos bajan en bloque).
    @Suppress("UNUSED_PARAMETER")
    val _lava = newLavaRow

    val destinations = LinkedHashMap<String, Position>()
    val killed = LinkedHashSet<String>()
    for (body in bodies) {
        val newY = body.cell.y + rowsToAdvance
        if (newY >= boardSize) {
            killed.add(body.id)
        } else {
            destinations[body.id] = Position(body.cell.x, newY)
        }
    }
    return LavaPushPlan(destinations, killed)
}

// ── Tests ────────────────────────────────────────────────────────────────────

class MergeRegressionTest {

    @Test
    fun adjacentHorizontalTeikasMeetAtFractionalMidpoint() {
        val meeting = resolveVisualMeeting(
            from1 = Position(2, 3),
            from2 = Position(3, 3),
            pathSize = 1,
            gridMeeting = Position(2, 3)
        )

        assertEquals(2.5f, meeting.x, 0f)
        assertEquals(3f, meeting.y, 0f)
    }

    @Test
    fun adjacentVerticalTeikasMeetAtFractionalMidpoint() {
        val meeting = resolveVisualMeeting(
            from1 = Position(4, 5),
            from2 = Position(4, 6),
            pathSize = 1,
            gridMeeting = Position(4, 5)
        )

        assertEquals(4f, meeting.x, 0f)
        assertEquals(5.5f, meeting.y, 0f)
    }

    @Test
    fun routedFusionKeepsGridMeeting() {
        val meeting = resolveVisualMeeting(
            from1 = Position(0, 0),
            from2 = Position(4, 4),
            pathSize = 4,
            gridMeeting = Position(1, 3)
        )

        assertEquals(1f, meeting.x, 0f)
        assertEquals(3f, meeting.y, 0f)
    }

    @Test
    fun emptyRasterPathUsesFallback() {
        val fallback = listOf(Position(1, 1), Position(2, 1))

        assertEquals(fallback, emptyList<Position>().withFallback { fallback })
    }

    @Test
    fun nonEmptyRasterPathDoesNotUseFallback() {
        val path = listOf(Position(3, 3))

        assertEquals(path, path.withFallback { error("fallback must not run") })
    }

    @Test
    fun corridorWaitsUntilTwinAbsorptionFinishes() {
        assertEquals(50L, corridorReleaseDelayAfterCompletion(hasTwinAbsorption = false))
        assertEquals(2_850L, corridorReleaseDelayAfterCompletion(hasTwinAbsorption = true))
    }

    @Test
    fun pushCascadeMovesBlockingTeikaInSameDirection() {
        val bodies = listOf(
            PushCascadeBody("a", TeikaType.TEIKA_1, Position(1, 3)),
            PushCascadeBody("b", TeikaType.TEIKA_1, Position(2, 3))
        )

        val plan = planPushCascade(
            startId = "a",
            delta = Position(1, 0),
            bodies = bodies,
            boardSize = 8
        )

        assertEquals(Position(2, 3), plan?.get("a"))
        assertEquals(Position(3, 3), plan?.get("b"))
    }

    @Test
    fun pushCascadeFailsWhenLastTeikaCannotFit() {
        val bodies = listOf(
            PushCascadeBody("a", TeikaType.TEIKA_1, Position(6, 3)),
            PushCascadeBody("b", TeikaType.TEIKA_1, Position(7, 3))
        )

        val plan = planPushCascade(
            startId = "a",
            delta = Position(1, 0),
            bodies = bodies,
            boardSize = 8
        )

        assertNull(plan)
    }

    @Test
    fun pushCascadeRespectsLavaRows() {
        val bodies = listOf(PushCascadeBody("a", TeikaType.TEIKA_1, Position(2, 2)))

        val plan = planPushCascade(
            startId = "a",
            delta = Position(0, -1),
            bodies = bodies,
            boardSize = 8,
            lavaRow = 2
        )

        assertNull(plan)
    }

    @Test
    fun pushCascadeAddsDominoDelayPerDepth() {
        assertEquals(0, pushCascadeDelayMillis(0))
        assertEquals(55, pushCascadeDelayMillis(1))
        assertEquals(220, pushCascadeDelayMillis(99))
    }

    @Test
    fun lavaPushesEveryTeikaDownByAdvanceAmount() {
        val plan = planLavaPushDown(
            bodies = listOf(
                PushCascadeBody("top", TeikaType.TEIKA_1, Position(1, 1)),
                PushCascadeBody("middle", TeikaType.TEIKA_1, Position(3, 4))
            ),
            newLavaRow = 1,
            rowsToAdvance = 1,
            boardSize = 8
        )

        assertEquals(Position(1, 2), plan.destinations["top"])
        assertEquals(Position(3, 5), plan.destinations["middle"])
        assertEquals(emptySet<String>(), plan.killedIds)
    }

    @Test
    fun lavaKillsOnlyTeikaPushedBeyondBottomEdge() {
        val plan = planLavaPushDown(
            bodies = listOf(
                PushCascadeBody("upper", TeikaType.TEIKA_1, Position(2, 6)),
                PushCascadeBody("bottom", TeikaType.TEIKA_1, Position(2, 7))
            ),
            newLavaRow = 1,
            rowsToAdvance = 1,
            boardSize = 8
        )

        assertEquals(Position(2, 7), plan.destinations["upper"])
        assertEquals(setOf("bottom"), plan.killedIds)
    }
}
