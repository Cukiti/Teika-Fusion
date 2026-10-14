package com.cukiti.teikafusion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.Piece

/**
 * **LA BANDEJA: TRES CUADROS** (2026-08-26, pedido del usuario: «tres cuadros visualmente premium,
 * separados, que le indiquen al jugador que tiene tres fichas a colocar; que la ficha quepa dentro
 * del cuadrado, simétrica, contando también los giros»).
 *
 * Tres cosas que la bandeja no hacía y ahora hace:
 *
 * 1. **Los tres huecos existen siempre.** Antes la fila se dimensionaba con
 *    `trayPieces.size.coerceAtLeast(1)`: al colocar una ficha la bandeja pasaba a repartir el ancho
 *    entre DOS, así que las que quedaban **crecían y se movían de sitio** en mitad del turno. Ahora
 *    hay [TRAY_SLOTS] cuadros fijos y una ficha colocada deja su cuadro vacío, no reordena la fila.
 * 2. **Cada ficha se queda en SU cuadro** ([rememberTraySlots]). El modelo borra la ficha usada de
 *    una lista, y en una lista todo lo que viene detrás se corre un puesto; el jugador veía saltar
 *    las fichas que no había tocado.
 * 3. **El tamaño lo fija el CUADRADO ENVOLVENTE de la ficha, no su ancho y su alto**
 *    ([trayCellSize]). Una I5 tumbada mide 5×1 y de pie 1×5: midiendo por separado, rotarla en la
 *    bandeja le cambiaba la escala y la sacaba del hueco. Midiendo por su lado mayor, una ficha ocupa
 *    el mismo cuadrado en las cuatro orientaciones y girarla ya no la mueve ni un píxel.
 */
const val TRAY_SLOTS = 3

/**
 * **P-94**: lo que dura el fundido con el que entra una ficha que SUSTITUYE a otra que ya no cabía.
 *
 * 260 ms: lo justo para leerse como un relevo y no como un parpadeo, y por debajo del pop de
 * reposición (que es un muelle) para que las dos entradas no se confundan.
 */
const val TRAY_SWAP_FADE_MS = 260

/**
 * Reparte [pieces] en [TRAY_SLOTS] huecos **estables**: una ficha no cambia de cuadro mientras viva.
 *
 * Las nuevas ocupan los huecos libres por orden; las que desaparecen (colocadas) liberan el suyo.
 * [resetKey] vacía el reparto — se le pasa el nivel, para que una ronda nueva no herede huecos.
 */
@Composable
fun rememberTraySlots(
    pieces: List<Piece>,
    resetKey: Any?,
    /**
     * Cuántos cuadros tiene la bandeja. Tres de siempre; **uno** en los niveles con horizonte
     * (P-75), donde solo hay una ficha jugable y el futuro se enseña aparte.
     */
    slotCount: Int = TRAY_SLOTS,
    replacement: Pair<String,String>? = null,
): List<Piece?> {
    val slots = remember(resetKey, slotCount) { arrayOfNulls<String>(slotCount) }
    // El reparto se recalcula en la COMPOSICIÓN (hace falta para pintar ya, no un frame después),
    // pero la escritura del registro va aparte para no mutar estado compartido a mitad de árbol.
    val resolved = resolveTraySlots(slots.toList(),pieces,replacement)
    val snapshot = resolved.map { it?.id }
    LaunchedEffect(snapshot) {
        for (i in 0 until slotCount) slots[i] = snapshot[i]
    }
    return resolved.toList()
}

/**
 * Lado de celda con el que se dibuja [piece] dentro de un hueco de [slotWidth]×[slotHeight].
 *
 * Se mide contra el **cuadrado envolvente** (`max(ancho, alto)`), que es invariante al giro: la
 * ficha cabe entera en las cuatro orientaciones y rotarla no le cambia el tamaño. El tope
 * [MAX_TRAY_CELL] evita que un monominó se dibuje del tamaño de medio cuadro.
 */
fun trayCellSize(slotWidth: Dp, slotHeight: Dp, piece: Piece): Dp {
    val span = maxOf(piece.width, piece.height).coerceAtLeast(1)
    val usable = minOf(slotWidth, slotHeight) * TRAY_FIT_RATIO
    return minOf(usable / span, MAX_TRAY_CELL)
}

/** Parte del hueco que puede ocupar la ficha. El resto es el aire que separa ficha y marco. */
const val TRAY_FIT_RATIO = 0.84f

/** Ninguna celda de bandeja pasa de aquí: una ficha de un bloque no debe llenar el cuadro. */
val MAX_TRAY_CELL = 32.dp

/** Redondeo del cuadro. Se comparte con el recorte de la ficha para que nada se salga del marco. */
val TraySlotShape = RoundedCornerShape(20.dp)

private val SlotTop = Color(0xFF283158)
private val SlotBottom = Color(0xFF161C36)
private val SlotEmptyTop = Color(0xFF1C2342)
private val SlotEmptyBottom = Color(0xFF141930)

/**
 * El cuadro premium: relieve de cristal oscuro con luz arriba, sombra abajo y un halo interior del
 * color de acento cuando tiene ficha. Vacío se apaga y hunde — así se lee de un vistazo cuántas
 * fichas quedan por colocar sin necesidad de un contador.
 *
 * Todo el relieve se dibuja en [drawBehind] (fase de DIBUJO): el marco no recompone cuando cambia la
 * ficha que lleva dentro.
 */
@Composable
fun TraySlotFrame(
    filled: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val top = if (filled) SlotTop else SlotEmptyTop
    val bottom = if (filled) SlotBottom else SlotEmptyBottom
    val glow = if (filled) accent.copy(alpha = 0.20f) else Color.Transparent
    Box(
        modifier = modifier
            .clip(TraySlotShape)
            .background(Brush.verticalGradient(listOf(top, bottom)))
            .drawBehind {
                // Luz superior: una banda corta que sugiere una superficie curvada, no un degradado
                // plano de arriba abajo (que es lo que hace que un panel parezca de cartón).
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.White.copy(alpha = 0.07f), Color.Transparent),
                        startY = 0f,
                        endY = size.height * 0.42f,
                    ),
                    size = Size(size.width, size.height * 0.42f),
                )
                if (glow.alpha > 0f) {
                    // Halo interior: nace del borde inferior y sube, así el cuadro con ficha "tiene
                    // algo encendido dentro" sin necesidad de un borde de neón.
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.Transparent, glow),
                            startY = size.height * 0.45f,
                            endY = size.height,
                        ),
                        topLeft = Offset(0f, size.height * 0.45f),
                        size = Size(size.width, size.height * 0.55f),
                    )
                }
            }
            .border(
                width = if (filled) 1.5.dp else 1.dp,
                color = if (filled) accent.copy(alpha = 0.42f) else Color.White.copy(alpha = 0.07f),
                shape = TraySlotShape,
            )
            .fillMaxSize()
    )
}

/* (RETIRADO 2026-09-22) `DiscardChip`: el botón de EL DESCARTE (P-146). Lo sustituyen LAS FICHAS
 * QUE SE FUNDEN (P-232), que no necesitan botón: se suelta una gemela sobre la otra. */


/**
 * **P-145 · EL DORSO DE LA FICHA TAPADA.**
 *
 * La ficha está ahí y se destapará; lo que el jugador no sabe todavía es **qué** es. Se dibuja un
 * dorso y no un cuadro vacío, y la diferencia importa: un cuadro vacío dice «te quedan dos fichas»,
 * y le quedan tres — una de ellas sin ver. El contador de la bandeja tiene que seguir siendo cierto.
 *
 * Un rombo con una interrogación implícita —dos trazos cruzados— en vez de un icono: el vocabulario
 * de marcas de este juego evita los símbolos con significado prestado (ver
 * `docs/vocabulario-marcas-tablero.md`), y un «?» dibujado a mano en un cuadro de 60 dp se lee como
 * un error de carga.
 *
 * No anima: es un dato en reposo, y un dato que se mueve se lee como una ficha (la misma decisión
 * que el horizonte de P-75).
 */
@Composable
fun TrayFaceDownBack(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawBehind {
            val side = minOf(size.width, size.height) * 0.42f
            val cx = size.width / 2f
            val cy = size.height / 2f
            val stroke = side * 0.16f
            // El rombo: la silueta neutra de «hay una ficha aquí».
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(cx, cy - side / 2f)
                lineTo(cx + side / 2f, cy)
                lineTo(cx, cy + side / 2f)
                lineTo(cx - side / 2f, cy)
                close()
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.16f),
            )
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.34f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke * 0.5f),
            )
            // Los dos trazos cruzados del centro: lo que hace que el rombo se lea como «tapada» y
            // no como una pieza más de la bandeja.
            listOf(-1f, 1f).forEach { dir ->
                drawLine(
                    color = Color.White.copy(alpha = 0.30f),
                    start = Offset(cx - side * 0.16f, cy - side * 0.16f * dir),
                    end = Offset(cx + side * 0.16f, cy + side * 0.16f * dir),
                    strokeWidth = stroke * 0.42f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                )
            }
        }
    )
}

/**
 * **P-75 · EL HORIZONTE**: uno de los cuadros pequeños que enseñan lo que VIENE.
 *
 * No se puede agarrar y se dibuja apagado a propósito: la bandeja tiene una sola ficha jugable y
 * todo lo demás es información. El primero del horizonte va más vivo que los otros dos — es el que
 * llega el turno que viene, y esa jerarquía es la que hace que el jugador planifique a un turno
 * antes que a tres.
 */
@Composable
fun HorizonSlot(
    piece: Piece?,
    slotWidth: Dp,
    slotHeight: Dp,
    isNext: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        TraySlotFrame(filled = piece != null, accent = accent)
        if (piece == null) return@Box
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (isNext) 0.92f else 0.58f },
            contentAlignment = Alignment.Center
        ) {
            PieceView(
                piece = piece,
                isDragging = false,
                isQueuePiece = true,
                cellSize = trayCellSize(slotWidth, slotHeight, piece)
            )
        }
    }
}

/** Preserve a replacement's original slot even when earlier slots are already empty. */
fun resolveTraySlots(slotIds: List<String?>, pieces: List<Piece>, replacement: Pair<String,String>? = null): List<Piece?> {
    val byId=pieces.associateBy { it.id }
    val taken=HashSet<String>()
    val resolved=arrayOfNulls<Piece>(slotIds.size)
    slotIds.forEachIndexed { index,id ->
        val incoming=if(id!=null && id==replacement?.first) replacement.second else id
        val piece=byId[incoming]
        if(piece!=null && taken.add(piece.id)) resolved[index]=piece
    }
    var cursor=0
    for(piece in pieces) {
        if(piece.id in taken) continue
        while(cursor<resolved.size && resolved[cursor]!=null) cursor++
        if(cursor>=resolved.size) break
        resolved[cursor]=piece; taken.add(piece.id)
    }
    return resolved.toList()
}
