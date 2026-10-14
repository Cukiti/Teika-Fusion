package com.cukiti.teikafusion.model

/** Casillas del aviso visual; no decide ni ejecuta la colocación. */
internal fun placementConflictCells(
    s: GameState,
    piece: Piece,
    at: Position,
    blocked: Set<Position>,
    overlappable: Set<Position>,
    splitAtX: Int,
): List<Position> {
    val minX = piece.shape.minOf { at.x + it.x }
    val maxX = piece.shape.maxOf { at.x + it.x }
    val crossesCut = splitAtX > 0 && minX < splitAtX && maxX >= splitAtX
    return piece.shape.mapNotNull { off ->
        val p = Position(at.x + off.x, at.y + off.y)
        if (p.x !in 0 until s.gridW || p.y !in 0 until s.gridH) return@mapNotNull null
        val occupied = (s.grid[p]?.isOccupied == true) && p !in overlappable
        // (2026-09-22) Aquí se comprobaba el color de las celdas de color (P-157c), retiradas.
        val hit = occupied || p in blocked || p.y < s.lavaRow || crossesCut
        if (hit) p else null
    }
}
