package com.cukiti.teikafusion.ui


import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.Piece
import com.cukiti.teikafusion.model.Position
import kotlin.math.sin

// Paleta jelly/plástico premium — tonos saturados y joyeros, con cara satinada.
// Cada entrada es [base, light(highlight superior), dark(borde/extrusión)].
val pieceColors = listOf(
    listOf(Color(0xFF4A90E8), Color(0xFF96C8F8), Color(0xFF2660C4)), // Azul zafiro
    listOf(Color(0xFF52BE60), Color(0xFFA0DCA8), Color(0xFF2A8C3A)), // Verde esmeralda
    listOf(Color(0xFFE8AA28), Color(0xFFF8D870), Color(0xFFBB7A08)), // Ámbar dorado
    listOf(Color(0xFF9B5EE0), Color(0xFFCEA8F8), Color(0xFF6030B8)), // Amatista
    listOf(Color(0xFFE86058), Color(0xFFF5A098), Color(0xFFBB3830)), // Rubí coral
    listOf(Color(0xFFDCCC30), Color(0xFFF5E678), Color(0xFFB8A010)), // Topacio
    listOf(Color(0xFF30B8A8), Color(0xFF88DCD4), Color(0xFF0E8878))  // Aguamarina
)

val WILDCARD_GRADIENT = listOf(
    Color(0xFFFF69B4), Color(0xFF9C27B0), Color(0xFF2196F3),
    Color(0xFF4CAF50), Color(0xFFFF9800)
)

/**
 * Contrato geométrico del bloque jugable: su silueta exterior ocupa exactamente una celda.
 * Dos celdas vecinas comparten límite, sin canal de fondo y sin invadir la celda contigua.
 */
internal const val GAMEPLAY_TILE_GAP_FRACTION = 0f

// ─────────────────────────────────────────────────────────────────────────────
// Bloque 3D mate (sin brillo glossy ni sombra exterior — eliminados a petición):
//   • Bevel 3D asimétrico: borde inferior grueso da profundidad real (rim).
//   • Cara con gradiente satinado + vignette interna para efecto hundido.
//   • Sin gloss/especular blanco, sin sombra de elevación, sin íconos.
// ─────────────────────────────────────────────────────────────────────────────
fun DrawScope.drawBlockTile(
    topLeft: Offset,
    size: Size,
    base: Color,
    light: Color,
    dark: Color,
    cornerFraction: Float = 0.34f,
    alpha: Float = 1f,
    gapFraction: Float = 0.04f
) = drawCachedTile(
    BlockTileStyle.JELLY, topLeft, size, base, light, dark, cornerFraction, gapFraction,
    0, alpha, build = ::jellyRecipe,
)

/**
 * Receta del bloque jelly, construida UNA vez en el origen (ver [drawCachedTile]): antes eran tres
 * degradados nuevos por casilla y fotograma en la rotura y en la ficha fantasma.
 */
private fun jellyRecipe(k: TileKey): TileRecipe? {
    val base = k.base; val light = k.light; val dark = k.dark
    val w = k.size.width
    val h = k.size.height
    val minDim = minOf(w, h)

    val gap = minDim * k.gapFraction
    val tl = Offset(gap, gap)
    val bw = w - gap * 2f
    val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val sz = Size(bw, bh)
    val blockMin = minOf(bw, bh)

    val r = blockMin * k.cornerFraction
    val corner = CornerRadius(r, r)
    val top = tl.y
    val left = tl.x

    // 2) Rim / extrusión 3D — forma completa con gradiente oscuro, más profundo abajo.
    val rimBrush = Brush.verticalGradient(
        0f    to lerp(dark, base, 0.20f),
        0.30f to dark,
        1f    to lerp(dark, Color.Black, 0.58f),
        startY = top, endY = top + bh
    )

    // 3) Cara principal — inset asimétrico: borde inferior grueso para efecto 3D.
    val bevelT = blockMin * 0.055f
    val bevelS = blockMin * 0.060f
    val bevelB = blockMin * 0.155f   // borde inferior más grueso = profundidad visible
    val faceTL = Offset(left + bevelS, top + bevelT)
    val faceW  = bw - bevelS * 2f
    val faceH  = bh - bevelT - bevelB
    if (faceW <= 0f || faceH <= 0f) {
        return TileRecipe { scope, a -> scope.drawRoundRect(brush = rimBrush, topLeft = tl, size = sz, cornerRadius = corner, alpha = a) }
    }
    val faceSize   = Size(faceW, faceH)
    val faceR      = (r - bevelS).coerceAtLeast(blockMin * 0.10f)
    val faceCorner = CornerRadius(faceR, faceR)
    val faceTop    = faceTL.y
    val faceBot    = faceTop + faceH
    val faceLeft   = faceTL.x

    // Gradiente satinado: blanco-claro → highlight → base → sombra progresiva
    val faceBrush = Brush.verticalGradient(
        0f    to lerp(light, Color.White, 0.22f),
        0.18f to light,
        0.50f to base,
        0.82f to lerp(base, dark, 0.44f),
        1f    to lerp(base, dark, 0.70f),
        startY = faceTop, endY = faceBot
    )

    // 4) Vignette interna — oscurece los bordes de la cara (sensación de hundimiento suave).
    val vignette = Brush.radialGradient(
        0.40f to Color.Transparent,
        1f    to dark.copy(alpha = 0.28f),
        center = Offset(faceLeft + faceW * 0.50f, faceTop + faceH * 0.68f),
        radius = maxOf(faceW, faceH) * 0.82f
    )

    return TileRecipe { scope, a ->
        scope.drawRoundRect(brush = rimBrush, topLeft = tl, size = sz, cornerRadius = corner, alpha = a)
        scope.drawRoundRect(brush = faceBrush, topLeft = faceTL, size = faceSize, cornerRadius = faceCorner, alpha = a)
        scope.drawRoundRect(brush = vignette, topLeft = faceTL, size = faceSize, cornerRadius = faceCorner, alpha = a)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Bloque MATE-MINIMALISTA del skin Derrumbe (rework 2026-07-21, spec del usuario "Block Blast limpio").
//
// Filosofía: ficha de PLÁSTICO MATE premium (juego de mesa moderno), no cubo 3D. Superficie lisa y de
// color uniforme, esquinas apenas redondeadas (~9%), borde FINO un pelín más oscuro (nunca negro),
// volumen MÍNIMO por luz superior + sombra inferior sutiles, y una sombra proyectada muy suave que solo
// despega la ficha del tablero. PROHIBIDO: bisel marcado, glint/brillo especular, cristal, degradados
// fuertes, contorno negro, sombras intensas. Cara PLANA a sangre → todo lo que va encima queda centrado
// (sin labio ni desplazamiento lateral), así que estrella-objetivo y cronómetro no necesitan corrección.
// ─────────────────────────────────────────────────────────────────────────────

/** Fracción de la altura del bloque que ocupa el labio oscuro inferior del CUBO EN CAÍDA (rotura). */
const val EXTRUDED_LIP_FRACTION = 0.16f

fun DrawScope.drawExtrudedTile(
    topLeft: Offset,
    size: Size,
    base: Color,
    light: Color,
    dark: Color,
    cornerFraction: Float = 0.09f,
    alpha: Float = 1f,
    gapFraction: Float = 0.045f
) = drawCachedTile(
    BlockTileStyle.EXTRUDED, topLeft, size, base, light, dark, cornerFraction, gapFraction,
    0, alpha, build = ::extrudedRecipe,
)

/** Receta del bloque mate, construida UNA vez en el origen (ver [drawCachedTile]). */
private fun extrudedRecipe(k: TileKey): TileRecipe? {
    val base = k.base; val light = k.light; val dark = k.dark
    val w = k.size.width; val h = k.size.height
    val minDim = minOf(w, h)
    val gap = minDim * k.gapFraction
    val oL = gap; val oT = gap
    val bw = w - gap * 2f; val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val blockMin = minOf(bw, bh)
    // Esquinas apenas redondeadas (6-10% de la spec): ni cuadrado rígido ni círculo.
    val r = blockMin * k.cornerFraction.coerceIn(0.06f, 0.12f)
    val corner = CornerRadius(r, r)

    // 1) SOMBRA PROYECTADA muy suave: dos capas translúcidas desplazadas hacia abajo (sin blur nativo
    //    para no asignar Paint en la ruta de VFX a 60fps). Solo separa la ficha del fondo — pequeña,
    //    difusa, baja opacidad; jamás una sombra dura ni grande.
    val shOff = blockMin * 0.055f
    val shSpread = blockMin * 0.02f
    val shadow1TL = Offset(oL - shSpread, oT + shOff)
    val shadow1Size = Size(bw + shSpread * 2f, bh)
    val shadow1Corner = CornerRadius(r + shSpread, r + shSpread)
    val shadow2TL = Offset(oL, oT + shOff * 0.6f)
    val bodyTL = Offset(oL, oT)
    val bodySize = Size(bw, bh)

    // 2) CUERPO plástico mate: color uniforme con un degradado APENAS perceptible — luz superior muy
    //    suave y sombra inferior igual de sutil. Una sola "fuente de luz" arriba. Nada de brillos.
    val bodyBrush = Brush.verticalGradient(
        0f to lerp(base, light, 0.16f),
        0.5f to base,
        1f to lerp(base, dark, 0.18f),
        startY = oT, endY = oT + bh
    )

    // 3) BORDE FINO: define la silueta y separa fichas contiguas. Un pelín más oscuro que el color
    //    base, NUNCA negro ni grueso.
    val edge = lerp(base, dark, 0.42f)
    val edgeStroke = Stroke(width = (blockMin * 0.035f).coerceAtLeast(1f))

    return TileRecipe { scope, a ->
        scope.drawRoundRect(Color.Black.copy(alpha = 0.05f * a), shadow1TL, shadow1Size, shadow1Corner)
        scope.drawRoundRect(Color.Black.copy(alpha = 0.06f * a), shadow2TL, bodySize, corner)
        scope.drawRoundRect(brush = bodyBrush, topLeft = bodyTL, size = bodySize, cornerRadius = corner, alpha = a)
        scope.drawRoundRect(
            color = edge.copy(alpha = 0.85f * a),
            topLeft = bodyTL, size = bodySize, cornerRadius = corner,
            style = edgeStroke
        )
    }
}

/**
 * Variante SIN ASIGNACIONES del bloque plano-3D, para el cubo que CAE en la rotura del Derrumbe.
 *
 * [drawExtrudedTile] construye un `Brush.verticalGradient` en cada llamada; el cubo en caída se
 * redibuja 60 veces por segundo y puede haber 8-16 a la vez (una línea entera), así que ahí el
 * gradiente costaba ~1000 Brush/s tirados a la basura. En un cubo que gira y cruza la pantalla en
 * medio segundo el degradado es invisible: dos rectángulos planos se ven idénticos y no asignan nada.
 */
fun DrawScope.drawFallingCube(
    topLeft: Offset,
    size: Size,
    base: Color,
    light: Color,
    dark: Color,
    cornerFraction: Float,
    alpha: Float = 1f
) {
    val w = size.width; val h = size.height
    if (w <= 0f || h <= 0f) return
    val r = minOf(w, h) * cornerFraction.coerceIn(0.08f, 0.30f)
    val corner = CornerRadius(r, r)
    // Cuerpo oscuro (lo que asoma abajo es el labio 3D) + cara plana encima.
    drawRoundRect(dark.copy(alpha = alpha), topLeft, size, corner)
    val faceH = h * (1f - EXTRUDED_LIP_FRACTION)
    drawRoundRect(base.copy(alpha = alpha), topLeft, Size(w, faceH), corner)
    // Glint superior: la única pista de volumen que se aprecia mientras gira.
    drawRoundRect(
        light.copy(alpha = 0.45f * alpha),
        Offset(topLeft.x + w * 0.12f, topLeft.y + faceH * 0.10f),
        Size(w * 0.50f, faceH * 0.16f),
        CornerRadius(faceH * 0.08f, faceH * 0.08f)
    )
}

/**
 * Bloque con la forma del estilo pedido — despacho ÚNICO para que todos los renderers (celda,
 * bandeja, VFX en movimiento, previews) pinten IGUAL cada skin sin repetir el if por estilo.
 */
fun DrawScope.drawStyledTile(
    style: BlockTileStyle,
    topLeft: Offset,
    size: Size,
    base: Color,
    light: Color,
    dark: Color,
    cornerFraction: Float,
    alpha: Float = 1f,
    /** El dibujo de las caras con variantes (veta, grietas, constelación…). Ver [tileVariantOf]. */
    variant: Int = 0,
) = when (style) {
    BlockTileStyle.BEVEL -> drawBevelTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
    BlockTileStyle.EXTRUDED -> drawExtrudedTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
    BlockTileStyle.JELLY -> drawBlockTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
    BlockTileStyle.FACETED -> drawFacetedTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
    BlockTileStyle.WOOD -> drawWoodTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION,
        grainVariant = variant,
    )
    BlockTileStyle.LAVA -> drawLavaTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION,
        crackVariant = variant,
    )
    BlockTileStyle.ICE -> drawIceTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION,
        frostVariant = variant,
    )
    BlockTileStyle.SPECTRE -> drawSpectreTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION,
        mistVariant = variant,
    )
    BlockTileStyle.CONSTELLATION -> drawConstellationTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION,
        skyVariant = variant,
    )
    BlockTileStyle.VOID -> drawVoidTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION,
        swirlVariant = variant,
    )
    BlockTileStyle.METAL -> drawMetalTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
    BlockTileStyle.NEON -> drawNeonTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
    BlockTileStyle.GOLD -> drawGoldTile(
        topLeft, size, base, light, dark, cornerFraction, alpha,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
}

/**
 * La variante de dibujo de un bloque del tablero, ESTABLE: sale de su id, que el bloque conserva al
 * caer, deslizarse o huir. Mismo rango que tenía la posición (0..15).
 */
fun tileVariantOf(woodId: String): Int = (woodId.hashCode() and 0x7fffffff) % 16

/**
 * Versión Modifier (cacheada por tamaño) del despacho por estilo. [glintPhase] solo lo usan los
 * estilos con destello (la gema del Cristal): el desfase de esta celda en la onda de luz.
 */
fun Modifier.styledTile(
    style: BlockTileStyle,
    base: Color,
    light: Color,
    dark: Color,
    cornerFraction: Float,
    glintPhase: Float = 0f,
    /**
     * El dibujo de las caras con variantes. (2026-09-25) Antes era [glintPhase] = x + y de la casilla,
     * así que un bloque que se movía CAMBIABA de veta, grietas o constelación al llegar. En el tablero
     * se pasa [tileVariantOf] del id del bloque: viaja con él.
     */
    variant: Int = glintPhase.toInt(),
): Modifier = when (style) {
    BlockTileStyle.BEVEL -> bevelTile(
        base, light, dark, cornerFraction,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
    BlockTileStyle.JELLY -> blockTile(
        base, light, dark, cornerFraction,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
    BlockTileStyle.EXTRUDED -> drawBehind {
        drawExtrudedTile(
            Offset.Zero, size, base, light, dark, cornerFraction,
            gapFraction = GAMEPLAY_TILE_GAP_FRACTION
        )
    }
    BlockTileStyle.FACETED -> facetedTile(
        base, light, dark, cornerFraction,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
    // La madera usa el desfase de la celda para elegir veta: el tablero no parece un estampado.
    BlockTileStyle.WOOD -> woodTile(
        base, light, dark, cornerFraction,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION, grainVariant = variant
    )
    // La lava parte la roca y desfasa el latido con la misma posición.
    BlockTileStyle.LAVA -> lavaTile(
        base, light, dark, cornerFraction,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION, crackVariant = variant
    )
    // El hielo mueve las burbujas con la posición.
    BlockTileStyle.ICE -> iceTile(
        base, light, dark, cornerFraction,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION, frostVariant = variant
    )
    // El espectro desfasa respiración y parpadeo con la posición: cada fantasmita a su ritmo.
    BlockTileStyle.SPECTRE -> spectreTile(
        base, light, dark, cornerFraction,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION, mistVariant = variant
    )
    // La constelación elige dibujo y ritmo con la posición: el tablero parece un mapa del cielo.
    BlockTileStyle.CONSTELLATION -> constellationTile(
        base, light, dark, cornerFraction,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION, skyVariant = variant
    )
    // El vacío inclina el anillo con la posición.
    BlockTileStyle.VOID -> voidTile(
        base, light, dark, cornerFraction,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION, swirlVariant = variant
    )
    BlockTileStyle.METAL -> metalTile(
        base, light, dark, cornerFraction,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
    BlockTileStyle.NEON -> neonTile(
        base, light, dark, cornerFraction,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
    BlockTileStyle.GOLD -> goldTile(
        base, light, dark, cornerFraction,
        gapFraction = GAMEPLAY_TILE_GAP_FRACTION
    )
}

/**
 * Dibuja UN bloque con la forma Y los colores del [skin] activo (JELLY o BEVEL), reusando el mismo
 * render que CellView/PieceView. Lo usan los VFX que dibujan madera en movimiento (empuje del Rey,
 * colapso galáctico, expansión del Mega) y el ghost, para que un bloque se vea IDÉNTICO en bandeja,
 * arrastre y celda (P24h — sin cambio de skin).
 */
fun DrawScope.drawSkinnedBlock(
    skin: BlockSkin,
    colorType: Int,
    topLeft: Offset,
    size: Size,
    alpha: Float = 1f
) {
    val c = skin.colorsFor(colorType)
    drawStyledTile(skin.style, topLeft, size, c[0], c[1], c[2], skin.cornerFraction, alpha)
}

fun DrawScope.drawWildcardTile(
    topLeft: Offset,
    size: Size,
    cornerFraction: Float = 0.34f,
    alpha: Float = 1f,
    gapFraction: Float = GAMEPLAY_TILE_GAP_FRACTION
) {
    val w = size.width
    val h = size.height
    if (w <= 0f || h <= 0f) return
    val minDim = minOf(w, h)
    val gap = minDim * gapFraction
    val tl = Offset(topLeft.x + gap, topLeft.y + gap)
    val bw = w - gap * 2f
    val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return
    val sz = Size(bw, bh)
    val blockMin = minOf(bw, bh)
    val r = blockMin * cornerFraction
    val corner = CornerRadius(r, r)
    drawRoundRect(
        brush = Brush.linearGradient(
            WILDCARD_GRADIENT,
            start = tl,
            end = Offset(tl.x + bw, tl.y + bh)
        ),
        topLeft = tl, size = sz, cornerRadius = corner, alpha = alpha
    )
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.68f),
            1f to Color.White.copy(alpha = 0f),
            startY = tl.y, endY = tl.y + bh * 0.52f
        ),
        topLeft = tl, size = sz, cornerRadius = corner, alpha = alpha
    )
    drawRoundRect(
        color = Color.White.copy(alpha = 0.55f * alpha),
        topLeft = tl, size = sz, cornerRadius = corner,
        style = Stroke(width = blockMin * 0.05f)
    )
}

/**
 * Pinta un bloque jelly 3D. Usa drawWithCache para que los 7 Brush se creen UNA VEZ
 * cuando cambia el tamaño o los colores, NO en cada frame de dibujo.
 */
fun Modifier.blockTile(base: Color, light: Color, dark: Color, cornerFraction: Float = 0.34f, gapFraction: Float = 0.04f): Modifier =
    this.drawWithCache {
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@drawWithCache onDrawBehind {}
        val minDim = minOf(w, h)
        val gap = minDim * gapFraction
        val bw = w - gap * 2f; val bh = h - gap * 2f
        if (bw <= 0f || bh <= 0f) return@drawWithCache onDrawBehind {}
        val blockMin = minOf(bw, bh)
        val r = blockMin * cornerFraction
        val corner = CornerRadius(r, r)
        val top = gap; val left = gap

        // Rim / extrusión 3D
        val rimBrush = Brush.verticalGradient(
            0f to lerp(dark, base, 0.20f), 0.30f to dark, 1f to lerp(dark, Color.Black, 0.58f),
            startY = top, endY = top + bh
        )
        val rimTL = Offset(left, top); val rimSz = Size(bw, bh)

        // Cara (inset asimétrico)
        val bevelT = blockMin * 0.055f; val bevelS = blockMin * 0.060f; val bevelB = blockMin * 0.155f
        val faceLeft = left + bevelS; val faceTopY = top + bevelT
        val faceW = bw - bevelS * 2f; val faceH = bh - bevelT - bevelB
        if (faceW <= 0f || faceH <= 0f) return@drawWithCache onDrawBehind {}
        val faceR = (r - bevelS).coerceAtLeast(blockMin * 0.10f)
        val faceCorner = CornerRadius(faceR, faceR)
        val faceTL = Offset(faceLeft, faceTopY); val faceSz = Size(faceW, faceH)
        val faceBotY = faceTopY + faceH
        val faceBrush = Brush.verticalGradient(
            0f to lerp(light, Color.White, 0.22f), 0.18f to light,
            0.50f to base, 0.82f to lerp(base, dark, 0.44f), 1f to lerp(base, dark, 0.70f),
            startY = faceTopY, endY = faceBotY
        )

        // Vignette interna
        val vignetteBrush = Brush.radialGradient(
            0.40f to Color.Transparent, 1f to dark.copy(alpha = 0.28f),
            center = Offset(faceLeft + faceW * 0.50f, faceTopY + faceH * 0.68f),
            radius = maxOf(faceW, faceH) * 0.82f
        )

        onDrawBehind {
            drawRoundRect(brush = rimBrush,    topLeft = rimTL,    size = rimSz,    cornerRadius = corner)
            drawRoundRect(brush = faceBrush,   topLeft = faceTL,   size = faceSz,   cornerRadius = faceCorner)
            drawRoundRect(brush = vignetteBrush, topLeft = faceTL, size = faceSz,   cornerRadius = faceCorner)
        }
    }

// ─────────────────────────────────────────────────────────────────────────────
// Bloque BISEL estilo Block Blast:
//   • Cuadrado (esquinas apenas redondeadas) con marco de 4 trapecios biselados.
//   • Cara central elevada (cuadrado plano) → efecto "pirámide truncada".
//   • Top/izq más claros, der/abajo más oscuros = luz isométrica clásica.
// ─────────────────────────────────────────────────────────────────────────────
private fun quadPath(
    x0: Float, y0: Float, x1: Float, y1: Float,
    x2: Float, y2: Float, x3: Float, y3: Float
) = Path().apply {
    moveTo(x0, y0); lineTo(x1, y1); lineTo(x2, y2); lineTo(x3, y3); close()
}

/** Dibuja un bloque bisel (Block Blast) dentro de [size] en [topLeft]. */
fun DrawScope.drawBevelTile(
    topLeft: Offset,
    size: Size,
    base: Color,
    light: Color,
    dark: Color,
    cornerFraction: Float = 0.12f,
    alpha: Float = 1f,
    gapFraction: Float = 0.05f,
    insetFraction: Float = 0.26f
) = drawCachedTile(
    BlockTileStyle.BEVEL, topLeft, size, base, light, dark, cornerFraction, gapFraction,
    0, alpha, extra = insetFraction, build = ::bevelRecipe,
)

/**
 * Receta del bisel, construida UNA vez en el origen (ver [drawCachedTile]): antes eran cinco `Path`
 * y un degradado nuevos por casilla y fotograma. [TileKey.extra] es el hundido (`insetFraction`).
 */
private fun bevelRecipe(k: TileKey): TileRecipe? {
    val base = k.base; val light = k.light; val dark = k.dark
    val w = k.size.width; val h = k.size.height
    val minDim = minOf(w, h)
    val gap = minDim * k.gapFraction
    val oL = gap; val oT = gap
    val bw = w - gap * 2f; val bh = h - gap * 2f
    if (bw <= 0f || bh <= 0f) return null
    val oR = oL + bw; val oB = oT + bh
    val blockMin = minOf(bw, bh)
    val r = blockMin * k.cornerFraction
    val corner = CornerRadius(r, r)
    val inset = blockMin * k.extra
    val iL = oL + inset; val iT = oT + inset; val iR = oR - inset; val iB = oB - inset

    // P-407 (2026-09-30): el bisel con el CONTRASTE de los block puzzle más populares. Antes las cuatro
    // facetas casi no se distinguían y la cara era plana: el bloque se leía «plano» a tamaño de bandeja.
    // Ahora la luz viene clara de arriba a la izquierda (arriba casi blanca, abajo hundida), hay un
    // contorno oscuro que separa bloques vecinos, la cara lleva degradado y un reflejo en su esquina.
    val topC    = lerp(light, Color.White, 0.25f)
    val leftC   = lerp(base, light, 0.45f)
    val rightC  = lerp(base, dark, 0.55f)
    val botC    = lerp(dark, Color.Black, 0.12f)
    val outlineC = lerp(dark, Color.Black, 0.35f)
    val glintC  = lerp(light, Color.White, 0.55f)

    val clip      = Path().apply { addRoundRect(RoundRect(Rect(oL, oT, oR, oB), corner)) }
    val topPath   = quadPath(oL, oT, oR, oT, iR, iT, iL, iT)
    val botPath   = quadPath(oL, oB, oR, oB, iR, iB, iL, iB)
    val leftPath  = quadPath(oL, oT, oL, oB, iL, iB, iL, iT)
    val rightPath = quadPath(oR, oT, oR, oB, iR, iB, iR, iT)
    val bodyTL = Offset(oL, oT); val bodySize = Size(bw, bh)
    val centerTL = Offset(iL, iT); val centerSz = Size(iR - iL, iB - iT)
    val centerCorner = CornerRadius(r * 0.5f, r * 0.5f)
    val centerBrush = Brush.verticalGradient(
        listOf(lerp(base, light, 0.35f), lerp(base, dark, 0.25f)), startY = iT, endY = iB
    )
    // Reflejo: triángulo en la esquina superior izquierda de la cara.
    val face = minOf(iR - iL, iB - iT)
    val g = face * 0.10f
    val leg = face * 0.36f
    val glintPath = Path().apply {
        moveTo(iL + g, iT + g); lineTo(iL + g + leg, iT + g); lineTo(iL + g, iT + g + leg); close()
    }
    // El trazo va centrado en el borde y la mitad de fuera la recorta el clip: se pide el doble.
    val outline = Stroke(width = blockMin * 0.05f)

    return TileRecipe { scope, a ->
        scope.clipPath(clip) {
            drawRoundRect(base.copy(alpha = a), bodyTL, bodySize, corner)
            drawPath(topPath, topC.copy(alpha = a))    // arriba
            drawPath(botPath, botC.copy(alpha = a))    // abajo
            drawPath(leftPath, leftC.copy(alpha = a))  // izq
            drawPath(rightPath, rightC.copy(alpha = a)) // der
            drawRoundRect(
                brush = centerBrush, topLeft = centerTL, size = centerSz,
                cornerRadius = centerCorner, alpha = a
            )
            drawPath(glintPath, glintC.copy(alpha = 0.85f * a))
            drawRoundRect(outlineC.copy(alpha = a), bodyTL, bodySize, corner, style = outline)
        }
    }
}

/**
 * Versión Modifier del bisel (bandeja, vitrina). Usa la MISMA receta que la ruta inmediata
 * ([bevelRecipe]): antes era una copia del dibujo a mano y cada retoque había que hacerlo dos veces.
 */
fun Modifier.bevelTile(base: Color, light: Color, dark: Color, cornerFraction: Float = 0.12f, gapFraction: Float = 0.05f, insetFraction: Float = 0.26f): Modifier =
    this.drawWithCache {
        val recipe = if (size.width <= 0f || size.height <= 0f) null else bevelRecipe(
            TileKey(BlockTileStyle.BEVEL, size, cornerFraction, gapFraction, 0, base, light, dark, insetFraction)
        )
        onDrawBehind { recipe?.draw(this, 1f) }
    }

fun Modifier.wildcardTile(
    cornerFraction: Float = 0.34f,
    gapFraction: Float = GAMEPLAY_TILE_GAP_FRACTION
): Modifier =
    this.drawWithCache {
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@drawWithCache onDrawBehind {}
        val minDim = minOf(w, h)
        val gap = minDim * gapFraction
        val tl = Offset(gap, gap)
        val bw = w - gap * 2f; val bh = h - gap * 2f
        if (bw <= 0f || bh <= 0f) return@drawWithCache onDrawBehind {}
        val sz = Size(bw, bh)
        val blockMin = minOf(bw, bh)
        val r = blockMin * cornerFraction
        val corner = CornerRadius(r, r)
        val gradBrush  = Brush.linearGradient(WILDCARD_GRADIENT, start = tl, end = Offset(tl.x + bw, tl.y + bh))
        val glossBrush = Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.68f), Color.Transparent), startY = tl.y, endY = tl.y + bh * 0.52f)
        val borderStroke = Stroke(width = blockMin * 0.05f)
        val borderColor = Color.White.copy(alpha = 0.55f)
        onDrawBehind {
            drawRoundRect(brush = gradBrush,  topLeft = tl, size = sz, cornerRadius = corner)
            drawRoundRect(brush = glossBrush, topLeft = tl, size = sz, cornerRadius = corner)
            drawRoundRect(color = borderColor, topLeft = tl, size = sz, cornerRadius = corner, style = borderStroke)
        }
    }

@Composable
fun PieceView(
    piece: Piece,
    isDragging: Boolean,
    isQueuePiece: Boolean = true,
    cellSize: Dp = 30.dp,
    onPositioned: ((Offset, Offset) -> Unit)? = null,
    modifier: Modifier = Modifier,
    ghostly: Boolean = false,
    fiery: Boolean = false,
    /** P-233: esta ficha tiene la forma del molde del tablero: lleva su sello de plata. */
    moldMatch: Boolean = false,
) {
    var pieceTopLeft by remember { mutableStateOf(Offset.Zero) }

    val skin = LocalBlockSkin.current

    val alpha = if (isQueuePiece && isDragging) 0f else 1f

    // Respiración espectral: la opacidad pulsa suavemente cuando la pieza es fantasmal.
    val ghostPulse = remember { Animatable(1f) }
    LaunchedEffect(ghostly) {
        if (ghostly) {
            while (true) {
                ghostPulse.animateTo(0.72f, tween(950, easing = FastOutSlowInEasing))
                ghostPulse.animateTo(1f, tween(950, easing = FastOutSlowInEasing))
            }
        } else ghostPulse.snapTo(1f)
    }

    Box(
        // OJO — TopStart es OBLIGATORIO, no cosmético. Cada celda se coloca con
        // `.offset(x = cellSize * pos.x, y = cellSize * pos.y)`, coordenadas RELATIVAS a la
        // esquina superior izquierda de la huella. Con `Alignment.Center` el Box centraba
        // primero cada celda (de 1×1) dentro de la huella (W×H) y DESPUÉS le sumaba el offset:
        // toda la ficha salía desplazada ((W-1)/2, (H-1)/2) celdas hacia abajo-derecha.
        // Consecuencias reales (2026-07-24): en la bandeja las fichas de 3-4 de ancho se salían
        // del slot y el clipToBounds les comía media columna (panelfichas.jpeg), y en el arrastre
        // la ficha se pintaba ~1 celda a la derecha de donde de verdad se iba a colocar → la
        // sombra no coincidía con la ficha y en los bordes no aparecía (fichasbug.jpeg).
        contentAlignment = Alignment.TopStart,
        modifier = modifier
            .size(
                width = cellSize * piece.width,
                height = cellSize * piece.height
            )
            // El pulso se lee en la CAPA, no en composición (perf 2026-09-26): la ficha fantasmal
            // recomponía entera en cada fotograma de su respiración. Mismo resultado que
            // `Modifier.alpha`, que recorta cuando el alfa no es 1.
            .graphicsLayer {
                val a = if (ghostly) alpha * ghostPulse.value else alpha
                this.alpha = a
                clip = a != 1f
            }
            .onGloballyPositioned { coordinates ->
                if (isQueuePiece) {
                    val tl = coordinates.localToWindow(Offset.Zero)
                    pieceTopLeft = tl
                    val center = coordinates.localToWindow(
                        Offset(coordinates.size.width / 2f, coordinates.size.height / 2f)
                    )
                    onPositioned?.invoke(center, tl)
                }
            }
    ) {
        piece.shape.forEachIndexed { index, pos ->
            val target = piece.targetMask.getOrNull(index)
            val isCrystal = piece.crystalMask.getOrNull(index) == true

            Box(
                modifier = Modifier
                    .size(cellSize)
                    .offset(x = cellSize * pos.x, y = cellSize * pos.y)
                    .then(
                        when {
                            ghostly -> Modifier
                                // La sombra es una guía, no una segunda ficha: solo contorno para
                                // que los bloques reales y la marea sigan viéndose a través de ella.
                                .border(
                                    width = 1.6.dp,
                                    color = Color(0xFFF1F7FF).copy(alpha = 0.92f),
                                    shape = RoundedCornerShape(percent = 18)
                                )
                                .clip(RoundedCornerShape(percent = 24))
                            // DESEO ESTELAR (rework 2026-08-27): es un BLOQUE NORMAL, con el skin
                            // equipado y su misma silueta. Antes se pintaba con forma de estrella
                            // líquida y el jugador no sabía qué iba a colocar: parecía otra cosa,
                            // no una ficha. Lo que la distingue ahora es su FIRMA ANIMADA encima
                            // (ver StellarWishTile), no su forma.
                            piece.isStarWish -> {
                                val c = skin.colorsFor(piece.colorType)
                                Modifier.styledTile(
                                    skin.style,
                                    base = c[0], light = c[1], dark = c[2],
                                    cornerFraction = skin.cornerFraction
                                )
                            }
                            // EL BLOQUE-TEIKA: esa casilla NO es madera — es la placa con la cápsula que
                            // pinta `teikaSeedTile` encima. Pintar aquí el skin haría asomar la madera
                            // por las esquinas con los bloques redondeados.
                            piece.teikaMask.getOrNull(index) == true -> Modifier
                            piece.isWildcard -> Modifier.wildcardTile(cornerFraction = 0.34f)
                            // LA CAMALEÓN (P-72): sale GRIS porque todavía NO tiene color — lo
                            // adopta al aterrizar. Pintarla de un color cualquiera sería mentir
                            // sobre lo único que la hace especial.
                            piece.isChameleon -> Modifier.styledTile(
                                skin.style,
                                base = ChameleonBase, light = ChameleonLight, dark = ChameleonDark,
                                cornerFraction = skin.cornerFraction
                            )
                            else -> {
                                // La ficha de la bandeja se pinta EXACTAMENTE como se va a pintar
                                // en el tablero: misma puerta (`woodPalette`), así que el bloque no
                                // puede cambiar de aspecto al estamparse.
                                val c = woodPalette(skin, piece.colorType, target != null && !isCrystal)
                                Modifier.styledTile(
                                    skin.style,
                                    base = c[0], light = c[1], dark = c[2],
                                    cornerFraction = skin.cornerFraction
                                )
                            }
                        }
                    )
            ) {
                if (piece.isStarWish && !ghostly) {
                    // Firma del Deseo Estelar SOBRE el bloque normal: nácar, palpitación y
                    // constelación. La forma es la de siempre; lo especial es cómo se comporta.
                    StellarWishTile(modifier = Modifier.matchParentSize())
                }
                // LA BROCHA (P-70): mismo bloque, mismo color — lo que la delata es la CRUZ que
                // late encima, que es literalmente lo que va a pintar al soltarla. Misma receta
                // que el Deseo Estelar: la ficha especial no cambia de forma (el jugador tiene que
                // poder juzgar dónde cabe), cambia de firma.
                if (piece.isBrush && !ghostly) {
                    BrushTile(modifier = Modifier.matchParentSize())
                }
                // Firma de la Camaleón: tres puntos que ciclan por la paleta del skin. Dice «esto
                // va a coger un color», que es exactamente la regla, sin necesidad de un rótulo.
                if (piece.isChameleon && !ghostly) {
                    ChameleonTile(modifier = Modifier.matchParentSize(), skin = skin)
                }
                // LA FICHA QUE CRECE (P-77): un brote que late. Tiene que verse ANTES de decidir si
                // la guardas — si solo se notara al crecer, la apuesta no existiría.
                if (piece.isGrowing && !ghostly) {
                    GrowingTile(modifier = Modifier.matchParentSize())
                }
                // EL IMÁN (P-240): la herradura sobre el bloque. Es lo que la distingue de un
                // monominó cualquiera en la bandeja, y lo que su cápsula señala.
                if (piece.isMagnet && !ghostly) {
                    MagnetTile(modifier = Modifier.matchParentSize())
                }
                // EL BARRIL (P-234): en la casilla que lo lleva, el mismo barril que tendrá puesto.
                if (piece.barrelMask.getOrNull(index) == true && !ghostly) {
                    BarrelTile(modifier = Modifier.matchParentSize())
                }
                // EL BLOQUE-TEIKA (P-256 · cápsula gashapon desde el 2026-09-25): la cápsula de premio con
                // su Teika dentro. Sustituye a la madera de esta casilla (ver el `when` de arriba).
                if (piece.teikaMask.getOrNull(index) == true && !ghostly) {
                    Box(Modifier.matchParentSize().teikaSeedTile(LocalAnimationsActive.current))
                }
                if (moldMatch && !ghostly) {
                    MoldTile(modifier = Modifier.matchParentSize())
                }
                // Brasa interna (Fuego / Llamarada): tinte naranja incandescente por celda.
                if (fiery && !ghostly) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFFFE082).copy(alpha = 0.55f), Color(0xFFFF6D00).copy(alpha = 0.30f))
                                ),
                                shape = RoundedCornerShape(percent = 24)
                            )
                    )
                }
                // Bioma Cristal: cáscara de vidrio que encapsula la celda (igual que en el tablero).
                if (isCrystal && !ghostly) {
                    CrystalShell(modifier = Modifier.matchParentSize())
                }
                if (target != null) {
                    // Cofre dorado: resplandor interior tras la gema (igual que en el tablero).
                    if (!isCrystal && !piece.isWildcard && !ghostly) {
                        Box(Modifier.matchParentSize().background(GoldChestGlowBrush))
                    }
                    TargetStar(
                        targetColor = target,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = skin.starScale; scaleY = skin.starScale
                                translationX = size.width * skin.starOffsetXFraction
                                translationY = size.height * skin.starOffsetYFraction
                            }
                    )
                }
            }
        }
        // (CutSlashOverlay / cutCursed retirados 2026-07-29 — mecánica ZAN I-11 nunca cableada.)
        // WALLBLOCK: la sombra debe seguir siendo una silueta hueca. No añadir humo ni velo
        // interior aquí: ocultaría los bloques reales que el jugador necesita leer debajo.
        // Llamas reales lamiendo la pieza (Fuego / Llamarada Infernal).
        if (fiery && !ghostly) {
            FieryPieceFlames(shape = piece.shape, cellSize = cellSize, modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun CrystalShell(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width; val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val r = CornerRadius(w * 0.22f, h * 0.22f)
        // Velo escarchado frío.
        drawRoundRect(
            brush = Brush.verticalGradient(listOf(Color(0x335FD8FF), Color(0x1A2A6CA8))),
            cornerRadius = r
        )
        // Brillo cristalino diagonal (aditivo).
        drawRoundRect(
            brush = Brush.linearGradient(
                listOf(Color(0xCCBFEFFF), Color(0x3380E0FF), Color(0x66FFFFFF)),
                start = Offset(0f, 0f), end = Offset(w, h)
            ),
            cornerRadius = r,
            blendMode = androidx.compose.ui.graphics.BlendMode.Screen
        )
        // Canto de la carcasa.
        drawRoundRect(color = Color(0xCCDFF7FF), cornerRadius = r, style = Stroke(width = w * 0.06f))
        // Facetas (reflejos diagonales).
        drawLine(Color(0xE6FFFFFF), Offset(w * 0.16f, h * 0.10f), Offset(w * 0.40f, h * 0.34f), strokeWidth = w * 0.07f)
        drawLine(Color(0x80FFFFFF), Offset(w * 0.62f, h * 0.18f), Offset(w * 0.78f, h * 0.40f), strokeWidth = w * 0.04f)
    }
}

/**
 * Llamas reales que lamen una pieza infernal (Fuego / Llamarada). Por cada celda de [shape]
 * suben lenguas de fuego (rojo→naranja→amarillo→núcleo) con flicker + brasas ascendentes.
 * El Canvas no clipea → el fuego sobresale por encima de la ficha como fuego real.
 */
@Composable
fun FieryPieceFlames(
    shape: List<Position>,
    cellSize: Dp,
    modifier: Modifier = Modifier
) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(1f, infiniteRepeatable(tween(900, easing = LinearEasing)))
    }
    val cellPx = with(LocalDensity.current) { cellSize.toPx() }
    val orange = Color(0xFFFF6D00)
    val yellow = Color(0xFFFFD54F)
    val core   = Color(0xFFFFF8E1)
    // UN Path reutilizado (reset + curvas) en vez de tres nuevos por lengua y fotograma.
    val flamePath = remember { Path() }

    Canvas(modifier = modifier) {
        // El reloj se lee AQUÍ, al dibujar (perf 2026-09-26): leído en el cuerpo recomponía la
        // capa de llamas en cada fotograma mientras la bandeja ardía.
        val t = clock.value
        shape.forEach { cell ->
            val cx = (cell.x + 0.5f) * cellPx
            val topY = cell.y * cellPx
            // 2 lenguas por celda, desfasadas.
            for (s in 0 until 2) {
                val seed = cell.x * 1.3f + cell.y * 2.1f + s * 3.7f
                val flick = (sin((t * 6.28f + seed).toDouble()).toFloat() * 0.5f + 0.5f)
                val h = cellPx * (0.6f + 0.55f * flick)
                val w = cellPx * 0.28f
                val bx = cx + (if (s == 0) -cellPx * 0.18f else cellPx * 0.18f)
                fun flame(scale: Float): Path {
                    val hh = h * scale; val ww = w * scale
                    val sway = sin((t * 6.28f + seed + 1f).toDouble()).toFloat() * ww * 0.4f
                    return flamePath.apply {
                        reset()
                        moveTo(bx - ww, topY)
                        cubicTo(bx - ww * 0.6f, topY - hh * 0.45f, bx - ww * 0.5f + sway, topY - hh * 0.8f, bx + sway, topY - hh)
                        cubicTo(bx + ww * 0.5f + sway, topY - hh * 0.8f, bx + ww * 0.6f, topY - hh * 0.45f, bx + ww, topY)
                        close()
                    }
                }
                drawPath(flame(1f), orange.copy(alpha = 0.85f))
                drawPath(flame(0.62f), yellow.copy(alpha = 0.9f))
                drawPath(flame(0.3f), core.copy(alpha = 0.95f))
            }
            // Brasa ascendente.
            val ep = (t + cell.x * 0.17f) % 1f
            drawCircle(core.copy(alpha = (1f - ep) * 0.8f), cellPx * 0.05f,
                Offset(cx + sin((ep * 8f).toDouble()).toFloat() * cellPx * 0.2f, topY - ep * cellPx * 1.1f))
        }
    }
}

/**
 * **DESEO ESTELAR** — la firma animada del bloque de Estela (rework 2026-08-27).
 *
 * Antes esto era `LiquidStarTile`: una estrella de 5 puntas líquida que REEMPLAZABA al tile del
 * bloque. El jugador veía una estrella en la bandeja y no sabía que era una ficha colocable — se
 * confundía con los bloques-objetivo del nivel, que también llevan estrella y significan otra cosa.
 *
 * Ahora el bloque es un bloque normal, con el skin equipado, y lo especial es cómo se COMPORTA. Se
 * dibuja encima, en este orden y todo dentro de un solo `Canvas`:
 *
 *  1. **Junta viva** — el bisel del bloque respira en un degradado cian→violeta→oro. El interior
 *     queda mate: así se lee "es un bloque normal, pero pasa algo con él".
 *  2. **Nácar** — un barrido holográfico diagonal cruza la cara cada ~2,4 s.
 *  3. **Palpitación** — el conjunto late suave (±3 %), no el bloque: la ficha no cambia de tamaño,
 *     solo su brillo. Un bloque que se hincha en la bandeja se lee como "no me cabe".
 *  4. **Constelación** — 3 estrellitas diminutas orbitando el perímetro, muy lentas.
 *
 * Ojo con [M-02]: el sheen de TODOS los bloques se retiró por fps. Aquí es un bloque **único** y
 * solo mientras está en la bandeja, así que el coste es un tile, no ochenta.
 */
/** Gris neutro de la Camaleón: un bloque SIN color, que es literalmente lo que es. */
private val ChameleonBase  = androidx.compose.ui.graphics.Color(0xFF8A90A6)
private val ChameleonLight = androidx.compose.ui.graphics.Color(0xFFBFC5D8)
private val ChameleonDark  = androidx.compose.ui.graphics.Color(0xFF5A6076)

/**
 * **FIRMA DE LA CAMALEÓN** (P-72): tres puntos que van ciclando por la paleta del skin.
 *
 * El bloque está gris —no tiene color todavía— y los puntos enseñan los colores que PODRÍA adoptar.
 * Misma receta que la Brocha y el Deseo Estelar: la ficha especial no cambia de forma (hay que poder
 * juzgar dónde cabe), cambia de firma.
 */
@Composable
fun ChameleonTile(modifier: Modifier = Modifier, skin: BlockSkin) {
    val phase by rememberInfiniteTransition(label = "chameleon").animateFloat(
        initialValue = 0f, targetValue = 5f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "chameleonPhase"
    )
    Canvas(modifier = modifier) {
        val r = size.minDimension * 0.085f
        val gap = size.minDimension * 0.24f
        val cx = size.width / 2f
        val cy = size.height / 2f
        // El desfase entre los tres puntos hace que el ciclo se lea como un recorrido, no como un
        // parpadeo: en cada instante enseñan tres colores distintos de la paleta.
        for (i in 0 until 3) {
            val idx = ((phase.toInt() + i) % 5)
            val c = skin.colorsFor(idx)[0]
            drawCircle(
                color = c.copy(alpha = 0.92f),
                radius = r,
                center = androidx.compose.ui.geometry.Offset(cx + (i - 1) * gap, cy),
            )
        }
    }
}

/**
 * **FIRMA DE LA FICHA QUE CRECE** (P-77): un brote verde que late hacia fuera.
 *
 * Va sobre el bloque, sin cambiarle la forma ni el color: lo que la distingue es que **promete algo
 * futuro**, y la flecha hacia fuera es el dibujo más corto de «esto va a ser más grande». Igual que
 * la Brocha y la Camaleón: la ficha especial no cambia de silueta, cambia de firma.
 */
@Composable
fun GrowingTile(modifier: Modifier = Modifier) {
    val phase by rememberInfiniteTransition(label = "growing").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "growingPhase"
    )
    Canvas(modifier = modifier) {
        val beat = 0.5f + 0.5f * kotlin.math.sin(phase * 2f * Math.PI.toFloat())
        val brote = androidx.compose.ui.graphics.Color(0xFF9BDD6E)
        val cx = size.width / 2f
        val cy = size.height / 2f
        val h = size.minDimension * (0.20f + 0.06f * beat)
        val w = size.minDimension * 0.13f
        val alpha = 0.70f + 0.30f * beat
        // Un signo «más» que respira: dos barras cruzadas, sin más adorno.
        drawRect(
            color = brote.copy(alpha = alpha),
            topLeft = androidx.compose.ui.geometry.Offset(cx - w / 2f, cy - h),
            size = androidx.compose.ui.geometry.Size(w, h * 2f),
        )
        drawRect(
            color = brote.copy(alpha = alpha),
            topLeft = androidx.compose.ui.geometry.Offset(cx - h, cy - w / 2f),
            size = androidx.compose.ui.geometry.Size(h * 2f, w),
        )
    }
}

/**
 * **FIRMA DE LA BROCHA** (P-70): una cruz blanca que late sobre el bloque.
 *
 * Es un dibujo literal de la regla —la ficha pinta su fila y su columna—, así que el jugador puede
 * deducir qué hace sin que nadie se lo explique. Misma decisión que tomó el Deseo Estelar el
 * 2026-08-27: la ficha especial **no cambia de forma** (hay que poder juzgar dónde cabe), cambia de
 * firma.
 *
 * Un solo reloj para las dos barras: dos `rememberInfiniteTransition` independientes se separan con
 * el tiempo y la cruz late descuadrada.
 */
/**
 * **EL IMÁN EN LA BANDEJA** (P-240): la herradura roja sobre el bloque y un anillo que late hacia
 * fuera — lo que hará en el tablero, dicho en pequeño. El pulso se lee en DIBUJO (sin `by`).
 */
@Composable
fun MagnetTile(modifier: Modifier = Modifier) {
    val phase = rememberInfiniteTransition(label = "magnet").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "magnetPhase"
    )
    Canvas(modifier = modifier) {
        val s = size.minDimension
        val c = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
        val p = phase.value
        drawCircle(
            color = androidx.compose.ui.graphics.Color(0xFFFF6B81).copy(alpha = 0.6f * (1f - p)),
            radius = s * (0.30f + 0.28f * p),
            center = c,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.05f),
        )
        drawHorseshoe(
            c.x, c.y, s,
            androidx.compose.ui.graphics.drawscope.Stroke(width = s * 0.16f),
        )
    }
}

/** **EL BARRIL EN LA BANDEJA** (P-234): con la mecha latiendo, leída en DIBUJO. */
@Composable
fun BarrelTile(modifier: Modifier = Modifier) {
    val phase = rememberInfiniteTransition(label = "barrel").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)), label = "barrelPhase"
    )
    Canvas(modifier = modifier) {
        val spark = 0.5f + 0.5f * kotlin.math.sin(phase.value * 2f * Math.PI.toFloat())
        drawBarrel(size.width / 2f, size.height / 2f, size.minDimension, spark)
    }
}

@Composable
fun BrushTile(modifier: Modifier = Modifier) {
    val phase by rememberInfiniteTransition(label = "brush").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "brushPhase"
    )
    Canvas(modifier = modifier) {
        val beat = 0.5f + 0.5f * kotlin.math.sin(phase * 2f * Math.PI.toFloat())
        val alpha = 0.42f + 0.34f * beat
        val thickness = size.minDimension * (0.17f + 0.03f * beat)
        // Horizontal y vertical, centradas: la cruz que va a pintar.
        drawRect(
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = alpha),
            topLeft = androidx.compose.ui.geometry.Offset(0f, (size.height - thickness) / 2f),
            size = androidx.compose.ui.geometry.Size(size.width, thickness),
        )
        drawRect(
            color = androidx.compose.ui.graphics.Color.White.copy(alpha = alpha),
            topLeft = androidx.compose.ui.geometry.Offset((size.width - thickness) / 2f, 0f),
            size = androidx.compose.ui.geometry.Size(thickness, size.height),
        )
    }
}

@Composable
fun StellarWishTile(modifier: Modifier = Modifier) {
    val phase by rememberInfiniteTransition(label = "stellarWish").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "stellarWishPhase"
    )
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val corner = androidx.compose.ui.geometry.CornerRadius(size.minDimension * 0.22f)
        val tau = 2f * Math.PI.toFloat()
        // Latido compartido por todas las capas: una sola fuente de ritmo, así nada va a destiempo.
        val beat = 0.5f + 0.5f * kotlin.math.sin(phase * tau)

        // ── 1. Junta viva: el borde recorre el degradado estelar ──
        val edge = Brush.sweepGradient(
            listOf(
                Color(0xFF7CF5FF), Color(0xFFB388FF), Color(0xFFFFC93C),
                Color(0xFFB388FF), Color(0xFF7CF5FF)
            ),
            Offset(w / 2f, h / 2f)
        )
        drawRoundRect(
            brush = edge,
            cornerRadius = corner,
            style = Stroke(width = size.minDimension * (0.07f + 0.02f * beat)),
            alpha = 0.75f + 0.25f * beat
        )

        // ── 2. Nácar: barrido diagonal que cruza la cara ──
        // La banda va de fuera a fuera, así que entra y sale limpia por las esquinas.
        val sweep = phase * 2.6f - 0.8f
        val bandW = w * 0.55f
        drawRoundRect(
            brush = Brush.linearGradient(
                0f to Color.Transparent,
                0.45f to Color.White.copy(alpha = 0.34f),
                0.5f to Color(0xFFE0F7FF).copy(alpha = 0.46f),
                0.55f to Color.White.copy(alpha = 0.34f),
                1f to Color.Transparent,
                start = Offset(sweep * (w + bandW) - bandW, 0f),
                end = Offset(sweep * (w + bandW), h)
            ),
            cornerRadius = corner
        )

        // ── 3. Palpitación: halo interior que respira, sin tocar el tamaño del bloque ──
        drawRoundRect(
            brush = Brush.radialGradient(
                listOf(
                    Color(0xFFFFF3C4).copy(alpha = 0.10f + 0.14f * beat),
                    Color.Transparent
                ),
                center = Offset(w / 2f, h / 2f),
                radius = size.minDimension * 0.62f
            ),
            cornerRadius = corner
        )

        // ── 4. Constelación: 3 chispas orbitando el perímetro ──
        val orbitR = size.minDimension * 0.42f
        for (i in 0 until 3) {
            val a = phase * tau * 0.5f + i * (tau / 3f)
            val p = Offset(
                w / 2f + kotlin.math.cos(a) * orbitR,
                h / 2f + kotlin.math.sin(a) * orbitR * 0.92f
            )
            val twinkle = 0.45f + 0.55f * kotlin.math.sin(phase * tau * 2f + i * 2.1f).let { it * it }
            drawCircle(Color.White.copy(alpha = 0.9f * twinkle), size.minDimension * 0.045f, p)
            drawCircle(Color(0xFFFFC93C).copy(alpha = 0.5f * twinkle), size.minDimension * 0.085f, p)
        }
    }
}


