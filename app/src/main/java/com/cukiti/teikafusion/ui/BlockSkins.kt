package com.cukiti.teikafusion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Forma del bloque:
 *  - [JELLY]: cubo redondeado satinado 3D.
 *  - [BEVEL]: cuadrado con marco biselado + cuadrado central elevado (pirámide truncada,
 *    estilo Block Blast). Es el look CLÁSICO/predeterminado del juego.
 *  - [EXTRUDED]: ficha de PLÁSTICO MATE minimalista (spec "Block Blast limpio", 2026-07-21) — cara
 *    plana de color uniforme, esquinas apenas redondeadas, borde fino, volumen mínimo (luz superior +
 *    sombra inferior sutiles) y sombra proyectada muy suave. La forma propia del skin DERRUMBE.
 *  - [FACETED]: GEMA TALLADA (P-251, 2026-09-23) — silueta cuadrada del Clásico con el marco partido
 *    en ocho facetas de luz alterna y una mesa central con reflejo. En el tablero, un destello cruza
 *    las gemas en diagonal (`drawFacetedTile`). La forma del bloque CRISTAL.
 *
 * [JELLY] ya no la usa ningún bloque (el Caramelo, su único dueño, se retiró el 2026-09-23 — P-249):
 * se conserva porque el render redondeado sigue sirviendo a previews y a efectos antiguos.
 */
enum class BlockTileStyle { JELLY, BEVEL, EXTRUDED, FACETED, WOOD, LAVA, ICE, SPECTRE, CONSTELLATION, VOID, METAL, NEON, GOLD }

/**
 * Skin visual de los bloques (madera) del tablero y de la bandeja de piezas.
 *
 * Cada skin define una PALETA de 5 colores (uno por `colorType`), donde cada color es
 * un triple [base, light(highlight), dark(extrusión)], más el estilo del bloque:
 *  - [cornerFraction]: redondez de las esquinas (0 = cuadrado, ~0.5 = muy redondo).
 *
 * El skin activo se propaga por [LocalBlockSkin] y lo leen CellView (tablero) y PieceView (bandeja).
 */
data class BlockSkin(
    val id: String,
    val name: String,
    /** Descripción corta para la tarjeta de la Tienda (única fuente del texto). */
    val description: String,
    val priceTeikoins: Int,
    val palette: List<List<Color>>,
    val cornerFraction: Float = 0.34f,
    val style: BlockTileStyle = BlockTileStyle.JELLY
) {
    /** Colores [base, light, dark] para un `colorType` dado. */
    fun colorsFor(colorType: Int): List<Color> = palette[colorType % palette.size]

    // ── Ajuste de lo que se dibuja ENCIMA del bloque (estrella objetivo, cronómetro) ──────────
    // Cada forma deja la "cara" útil en un sitio distinto: el JELLY y el EXTRUDED tienen bisel/labio
    // inferior, así que su cara queda por encima del centro geométrico de la celda y el adorno debe
    // subir para verse centrado. El BEVEL tiene el cuadrado central perfectamente centrado.
    /** Escala de la estrella objetivo para encajar bien en la cara de este bloque. */
    val starScale: Float get() = when (style) {
        BlockTileStyle.BEVEL -> 1.32f
        // EXTRUDED (ficha mate plana): la cara ocupa toda la celda → estrella grande.
        BlockTileStyle.EXTRUDED -> 1.26f
        BlockTileStyle.JELLY -> 1.26f
        // FACETED: la mesa central es algo más grande que la del bisel → estrella algo menor.
        BlockTileStyle.FACETED -> 1.28f
        // WOOD: la cara es la tabla entera menos el bisel → como el bisel del Clásico.
        BlockTileStyle.WOOD -> 1.28f
        BlockTileStyle.LAVA -> 1.28f
        BlockTileStyle.ICE -> 1.28f
        BlockTileStyle.SPECTRE -> 1.28f
        BlockTileStyle.CONSTELLATION -> 1.28f
        BlockTileStyle.VOID -> 1.28f
        BlockTileStyle.METAL -> 1.28f
        // NEÓN: la cara es el hueco del tubo; DORADO: la gema, más grande que la cara del Básico.
        BlockTileStyle.NEON -> 1.20f
        BlockTileStyle.GOLD -> 1.26f
    }
    /** Desplazamiento vertical del adorno (fracción de la altura de la celda). */
    val starOffsetYFraction: Float get() = when (style) {
        BlockTileStyle.BEVEL -> 0f
        // EXTRUDED: cara PLANA a sangre (sin labio) → centro geométrico = centro de celda.
        BlockTileStyle.EXTRUDED -> 0f
        BlockTileStyle.JELLY -> -0.045f
        BlockTileStyle.FACETED -> 0f
        BlockTileStyle.WOOD -> 0f
        BlockTileStyle.LAVA -> 0f
        BlockTileStyle.ICE -> 0f
        BlockTileStyle.SPECTRE -> 0f
        BlockTileStyle.CONSTELLATION -> 0f
        BlockTileStyle.VOID -> 0f
        BlockTileStyle.METAL -> 0f
        BlockTileStyle.NEON -> 0f
        BlockTileStyle.GOLD -> 0f
    }
    /**
     * Desplazamiento HORIZONTAL del adorno (fracción del ancho de la celda). Es 0 en las tres formas
     * actuales porque todas son simétricas en X — existe para que una forma futura con volumen
     * lateral pueda recentrar la estrella/el cronómetro sin tocar cada renderer.
     */
    val starOffsetXFraction: Float get() = 0f
}

/**
 * **PRECIO POR GAMA DE CALIDAD** (P-283, decisión del usuario 2026-09-25): el precio de un bloque de
 * bioma lo pone lo PREMIUM que es —cara, reposo animado, firma al colocar, rotura, vibración y
 * sonido—, no el orden del viaje. Tres gamas; cada bloque se asignó a la suya al terminar su pasada
 * premium (ver MEJORAS P-283). La Cerámica no es de bioma y conserva su precio.
 */
object BlockPriceTier {
    const val ESTANDAR = 2500
    const val PREMIUM = 4000
    const val LEGENDARIO = 6000
    val all = setOf(ESTANDAR, PREMIUM, LEGENDARIO)
}

object BlockSkins {

    // Paleta vívida y saturada del look CLÁSICO (cuadrado biselado estilo Block Blast).
    private val classicPalette = listOf(
        listOf(Color(0xFFFF5A5A), Color(0xFFFF9E9E), Color(0xFFD6322F)), // Rojo
        listOf(Color(0xFFFFC83D), Color(0xFFFFE08A), Color(0xFFE0A010)), // Amarillo
        listOf(Color(0xFF7BD14A), Color(0xFFAEE885), Color(0xFF56A030)), // Verde lima
        listOf(Color(0xFF4A9BF5), Color(0xFF97C5FA), Color(0xFF2A6FD0)), // Azul
        listOf(Color(0xFFB05AE0), Color(0xFFD6A0F5), Color(0xFF7E2EB8))  // Morado
    )

    // Bloque NEÓN (P-408): tubos de luz de neón — tonos eléctricos para que brillen sobre la casilla
    // casi negra; el claro es el núcleo del tubo y el oscuro tiñe la noche de dentro.
    private val neonPalette = listOf(
        listOf(Color(0xFFFF3B6B), Color(0xFFFF9DB5), Color(0xFF7A0F2E)), // Neón rosa
        listOf(Color(0xFFFFE14A), Color(0xFFFFF3A8), Color(0xFF7A5E00)), // Neón amarillo
        listOf(Color(0xFF3DFF8E), Color(0xFFAFFFD0), Color(0xFF0B6B35)), // Neón verde
        listOf(Color(0xFF3DB8FF), Color(0xFFAEE3FF), Color(0xFF0B4A7A)), // Neón azul
        listOf(Color(0xFFC25CFF), Color(0xFFE6B8FF), Color(0xFF4E1880))  // Neón violeta
    )

    // Bloque DORADO (P-408): el marco es oro para todos; la paleta es la de la GEMA engastada, los
    // mismos cinco tonos que el Clásico para que el color de cada ficha se lea igual.
    private val goldGemPalette = listOf(
        listOf(Color(0xFFE8384F), Color(0xFFFF8FA0), Color(0xFF8F1225)), // Rubí
        // Ámbar y no topacio: un amarillo puro desaparece sobre el oro del marco.
        listOf(Color(0xFFFF9A1F), Color(0xFFFFD27A), Color(0xFFA34E00)), // Ámbar
        listOf(Color(0xFF2EC26B), Color(0xFF8FF0B8), Color(0xFF0F6E38)), // Esmeralda
        listOf(Color(0xFF2E7FE8), Color(0xFF9CC8FF), Color(0xFF123F8F)), // Zafiro
        listOf(Color(0xFF9A46E0), Color(0xFFD2A6FF), Color(0xFF531A8C))  // Amatista
    )

    // Skin DERRUMBE (decisión del usuario 2026-07-20): cubo 3D de tonos joya profundos con
    // extrusión oscura marcada. Su firma es la ROTURA: los bloques no revientan — se DESPRENDEN
    // enteros, saltan, giran y CAEN fuera del tablero con gravedad (ver BatchLineCellShatterVFX).
    private val fallPalette = listOf(
        listOf(Color(0xFFE8384F), Color(0xFFFF7D8E), Color(0xFF8F1225)), // Cereza
        listOf(Color(0xFFFFAA26), Color(0xFFFFD470), Color(0xFFA35F05)), // Ámbar
        listOf(Color(0xFF2EC26B), Color(0xFF7FE3A8), Color(0xFF0F6E38)), // Esmeralda
        listOf(Color(0xFF2E7FE8), Color(0xFF7FB5F7), Color(0xFF123F8F)), // Zafiro
        listOf(Color(0xFF9A46E0), Color(0xFFC98DF2), Color(0xFF531A8C))  // Amatista
    )

    // Bloque CRISTAL (P-251): tonos de gema LUMINOSOS — el claro casi blanco es el brillo de la faceta
    // y el oscuro, profundo, la faceta en sombra. Mismos cinco tonos que el Clásico para que el color
    // de cada ficha se lea igual al cambiar de bloque.
    private val crystalPalette = listOf(
        listOf(Color(0xFFF0426B), Color(0xFFFFA8C0), Color(0xFF9E1238)), // Rubí
        listOf(Color(0xFFFFB627), Color(0xFFFFE594), Color(0xFFAD6500)), // Topacio
        listOf(Color(0xFF1FCB8A), Color(0xFF94F2CD), Color(0xFF0A7049)), // Esmeralda
        listOf(Color(0xFF3B8DF2), Color(0xFFABD0FF), Color(0xFF184AA8)), // Zafiro
        listOf(Color(0xFFA35CF0), Color(0xFFDAB9FF), Color(0xFF5A1FA3))  // Amatista
    )

    // Bloque MADERA (P-251 2/7): pintura de tono MADERA sobre los cinco colores — algo más cálida y
    // apagada que el Clásico, como una caja pintada a mano; el oscuro es la veta que asoma.
    private val woodPalette = listOf(
        listOf(Color(0xFFE0533F), Color(0xFFF59A7E), Color(0xFF8A2A1C)), // Rojo teja
        listOf(Color(0xFFF2B33D), Color(0xFFFAD98A), Color(0xFF9A6512)), // Mostaza
        listOf(Color(0xFF6DB24A), Color(0xFFAED88C), Color(0xFF3B6B22)), // Verde huerta
        listOf(Color(0xFF3F8FD6), Color(0xFF94C3EE), Color(0xFF1E4F86)), // Azul añil
        listOf(Color(0xFF9A5CC8), Color(0xFFCDA6EA), Color(0xFF55287E))  // Morado ciruela
    )

    // Bloque LAVA (P-251 3/7): el color de la ficha es el MAGMA que asoma por las grietas — tonos
    // encendidos; el oscuro se mezcla con basalto para las placas de roca.
    private val lavaPalette = listOf(
        listOf(Color(0xFFFF4A2E), Color(0xFFFFA06A), Color(0xFF7A1A0E)), // Magma rojo
        listOf(Color(0xFFFFB21F), Color(0xFFFFE07A), Color(0xFF8A5200)), // Magma dorado
        listOf(Color(0xFF52D65A), Color(0xFFB2F59A), Color(0xFF1C6A24)), // Magma verde (azufre)
        listOf(Color(0xFF3AA2FF), Color(0xFFA6DCFF), Color(0xFF123E80)), // Magma azul
        listOf(Color(0xFFC35CFF), Color(0xFFE7B2FF), Color(0xFF55207F))  // Magma violeta
    )

    // Bloque HIELO (P-251 4/7): los cinco colores CONGELADOS — más fríos y más claros que el Clásico;
    // el oscuro es el núcleo del cubito, donde el color se concentra.
    private val icePalette = listOf(
        listOf(Color(0xFFF2607A), Color(0xFFFFC2CF), Color(0xFFA3283F)), // Frambuesa helada
        listOf(Color(0xFFF7C74A), Color(0xFFFFEBA8), Color(0xFFA67A0C)), // Limón helado
        listOf(Color(0xFF4CCB93), Color(0xFFB0F2D2), Color(0xFF16744A)), // Menta
        listOf(Color(0xFF4AA6F2), Color(0xFFB8DEFF), Color(0xFF1A5AA3)), // Glaciar
        listOf(Color(0xFFAC7BF0), Color(0xFFE0CCFF), Color(0xFF5E32A3))  // Lila helado
    )

    // Bloque ESPECTRO (P-251 5/7): el color de la ficha es la NIEBLA luminosa del fantasmita — tonos
    // de neón suave sobre un fondo nocturno; el oscuro es la noche teñida.
    private val spectrePalette = listOf(
        listOf(Color(0xFFFF5C8A), Color(0xFFFFB3C9), Color(0xFF8C1F45)), // Fantasma rosa
        listOf(Color(0xFFFFCB45), Color(0xFFFFEAA0), Color(0xFF8C6410)), // Fuego fatuo
        listOf(Color(0xFF4FE3A0), Color(0xFFB5F7D8), Color(0xFF17754B)), // Ectoplasma
        listOf(Color(0xFF5AB4FF), Color(0xFFBFE2FF), Color(0xFF1B4F8F)), // Alma azul
        listOf(Color(0xFFB982FF), Color(0xFFE3CCFF), Color(0xFF55298F))  // Espectro violeta
    )

    // Bloque CONSTELACIÓN (P-251 6/7): el color de la ficha es el cielo del crepúsculo que se funde
    // con la noche; el claro tiñe la nebulosa y las líneas.
    private val skyPalette = listOf(
        listOf(Color(0xFFF25A7A), Color(0xFFFFB0C4), Color(0xFF7A1F3E)), // Crepúsculo rosa
        listOf(Color(0xFFF5B83D), Color(0xFFFFE196), Color(0xFF7D5610)), // Atardecer
        listOf(Color(0xFF3FCB97), Color(0xFFA8F0D2), Color(0xFF136247)), // Aurora
        listOf(Color(0xFF4C8DF5), Color(0xFFB0CFFF), Color(0xFF1B3F8C)), // Noche azul
        listOf(Color(0xFFA56BF0), Color(0xFFDCC4FF), Color(0xFF4E2590))  // Nebulosa
    )

    // Bloque VACÍO (P-251 7/7): el color de la ficha es el ANILLO DE ACRECIÓN que gira alrededor del
    // agujero negro — tonos vivos para que brillen sobre el espacio casi negro.
    private val voidPalette = listOf(
        listOf(Color(0xFFFF4F6E), Color(0xFFFFA9BA), Color(0xFF6E1428)), // Anillo rojo
        listOf(Color(0xFFFFBE3B), Color(0xFFFFE39A), Color(0xFF6E4A08)), // Anillo dorado
        listOf(Color(0xFF3FE0A0), Color(0xFFABF7D6), Color(0xFF0F5E3E)), // Anillo verde
        listOf(Color(0xFF4A9CFF), Color(0xFFB2D6FF), Color(0xFF14397A)), // Anillo azul
        listOf(Color(0xFFB76CFF), Color(0xFFE2C6FF), Color(0xFF4A1C80))  // Anillo violeta
    )

    // Bloque METAL (2026-09-25): aluminio ANODIZADO — los cinco colores con el brillo del metal encima;
    // el oscuro es el canto de la chapa y el tono de los remaches.
    private val metalPalette = listOf(
        listOf(Color(0xFFE0485A), Color(0xFFFFA2AE), Color(0xFF6E1624)), // Anodizado rojo
        listOf(Color(0xFFE8B230), Color(0xFFFFE08C), Color(0xFF7A5406)), // Anodizado dorado
        listOf(Color(0xFF3FB27A), Color(0xFFA6EBC6), Color(0xFF145A38)), // Anodizado verde
        listOf(Color(0xFF3A86D8), Color(0xFFA9CEF5), Color(0xFF123F78)), // Anodizado azul
        listOf(Color(0xFF9A62D8), Color(0xFFD7BDF5), Color(0xFF45226E))  // Anodizado violeta
    )

    // ── id estables (no cambiar: se persisten en unlockedCosmetics/activeCosmetic) ──
    // "blockblast" = look CLÁSICO biselado, predeterminado y gratis.
    val classico = BlockSkin("blockblast", "Básico", "El bloque clásico del juego, gratis y siempre tuyo", 0, classicPalette, cornerFraction = 0.12f, style = BlockTileStyle.BEVEL)
    // Precios subidos el 2026-09-06 (decision del usuario): 600→2000 y 900→3000. Con la tienda
    // reabierta, los bloques dejan de ser un gasto de una tarde y pasan a ser una meta: son el
    // único cosmetico del juego y ahora compiten con el paquete inicial, que los trae los dos.
    // (RETIRADO 2026-09-23, P-249) `candy` · Caramelo: el único bloque redondeado. Su id sigue en
    // `unlockedCosmetics` como registro de compra (ver `CandyRetirement`); `byId("candy")` → Clásico.
    // Bloque de la Caverna de Cristal (P-251): se descubre al terminar el Bosque del Sueño. Lo trae
    // también el paquete inicial (`StorePacks.STARTER_SKIN_IDS`). Gama por calidad: ver BlockPriceTier (P-283).
    val cristal  = BlockSkin("cristal", "Cristal", "Gema tallada en ocho facetas; al romper salta en esquirlas", BlockPriceTier.ESTANDAR, crystalPalette, cornerFraction = 0.10f, style = BlockTileStyle.FACETED)
    // Bloque del Jardín (P-251 2/7): se descubre al terminar el Jardín. Gama por calidad: ver BlockPriceTier (P-283).
    val madera   = BlockSkin("madera", "Madera", "Tabla pintada a mano con sus vetas y sus clavos; al romper salta en astillas", BlockPriceTier.ESTANDAR, woodPalette, cornerFraction = 0.12f, style = BlockTileStyle.WOOD)
    // Bloque del Volcán (P-251 3/7): se descubre al terminar el Volcán. Gama por calidad: ver BlockPriceTier (P-283).
    val lava     = BlockSkin("lava", "Lava", "Roca recién enfriada con el magma latiendo en sus grietas; al romper suelta ascuas", BlockPriceTier.PREMIUM, lavaPalette, cornerFraction = 0.12f, style = BlockTileStyle.LAVA)
    // Bloque del Glaciar (P-251 4/7): se descubre al terminar el Glaciar. Gama por calidad: ver BlockPriceTier (P-283).
    val hielo    = BlockSkin("hielo", "Hielo", "Cubito escarchado del color de la ficha, con burbujas atrapadas; al romper, trozos de hielo y vaho", BlockPriceTier.PREMIUM, icePalette, cornerFraction = 0.14f, style = BlockTileStyle.ICE)
    // Bloque de la Cocina Voraz (P-251 5/7): se descubre al terminar la Cocina Voraz. Gama por calidad: ver BlockPriceTier (P-283).
    val espectro = BlockSkin("espectro", "Espectro", "Un fantasmita en cada ficha: la niebla respira y parpadea; al romper se deshace en volutas", BlockPriceTier.PREMIUM, spectrePalette, cornerFraction = 0.14f, style = BlockTileStyle.SPECTRE)
    // Bloque del Cielo Estelar (P-251 6/7): se descubre al terminar el Cielo Estelar. Gama por calidad: ver BlockPriceTier (P-283).
    val constelacion = BlockSkin("constelacion", "Constelación", "Un trozo de cielo con su constelación: las estrellas titilan y al romper salen fugaces", BlockPriceTier.LEGENDARIO, skyPalette, cornerFraction = 0.12f, style = BlockTileStyle.CONSTELLATION)
    // Bloque del Espacio Profundo (P-251 7/7): se descubre al terminar el Espacio Profundo. Gama por calidad: ver BlockPriceTier (P-283).
    val vacio    = BlockSkin("vacio", "Vacío", "Un agujero negro de bolsillo con su anillo de luz; al romper, el bloque implosiona", BlockPriceTier.LEGENDARIO, voidPalette, cornerFraction = 0.12f, style = BlockTileStyle.VOID)
    // Bloque METAL (2026-09-25): la placa del bloque-Teika metálico hecha skin, anodizada del color de la
    // ficha. No es de bioma: siempre a la vista en la vitrina, gama Legendaria.
    val metal    = BlockSkin("metal", "Metal", "Chapa de aluminio anodizado con sus remaches; al romper saltan los remaches", BlockPriceTier.LEGENDARIO, metalPalette, cornerFraction = 0.10f, style = BlockTileStyle.METAL)
    // Bloques NEÓN y DORADO (P-408, 2026-09-30, inspirados en los block puzzle más populares de la
    // tienda). No son de bioma: siempre a la vista. Neón en gama Premium, Dorado en Legendaria.
    // id «neon_tubo», NO «neon»: «neon» fue un bloque retirado el 2026-06-03 que aún puede estar en
    // guardados viejos — reutilizarlo regalaría este bloque a quien tuviera aquel (BlockSkinsTest).
    val neon     = BlockSkin("neon_tubo", "Neón", "Un tubo de luz de neón en cada ficha; brilla sobre la noche del tablero", BlockPriceTier.PREMIUM, neonPalette, cornerFraction = 0.12f, style = BlockTileStyle.NEON)
    val dorado   = BlockSkin("dorado", "Dorado", "Marco de oro macizo con una gema engastada del color de la ficha", BlockPriceTier.LEGENDARIO, goldGemPalette, cornerFraction = 0.12f, style = BlockTileStyle.GOLD)
    val fall3d   = BlockSkin("fall3d", "Cerámica", "Ficha mate limpia; al romper, los bloques se desprenden y caen fuera de la pantalla", 3000, fallPalette, cornerFraction = 0.09f, style = BlockTileStyle.EXTRUDED)

    /** Clásico (predeterminado) primero + el resto, en orden de tienda. */
    val all = listOf(classico, cristal, madera, lava, hielo, espectro, constelacion, vacio, metal, neon, dorado, fall3d)

    /** id del skin por defecto (cuando `activeCosmetic` es null). */
    const val DEFAULT_ID = "blockblast"

    fun byId(id: String?): BlockSkin = all.firstOrNull { it.id == id } ?: classico
}

/** Skin de bloque activo. Por defecto el Clásico; GameScreen lo provee según `activeCosmetic`. */
val LocalBlockSkin = staticCompositionLocalOf { BlockSkins.classico }

/** Provee el skin activo a [content]. Envolver el tablero/bandeja (GameScreen) y previews de tienda. */
@Composable
fun ProvideBlockSkin(activeCosmetic: String?, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalBlockSkin provides BlockSkins.byId(activeCosmetic),
        content = content
    )
}

/**
 * P-283 · VOLUMEN común de los bloques de bioma: luz en el canto de arriba y sombra en el de abajo,
 * dentro de la cara. Es lo que hace que un bloque plano (niebla, cielo, espacio, roca) se lea como una
 * PIEZA con grosor, igual que el bisel del Clásico. Se construye UNA vez por tamaño, con la geometría de
 * la cara (sus pinceles no se crean por fotograma), y se dibuja dentro del recorte de la cara.
 */
internal class TileDepth(private val oL: Float, private val oT: Float, private val bw: Float, private val bh: Float) {
    private val top = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.26f), androidx.compose.ui.graphics.Color.Transparent),
        startY = oT, endY = oT + bh * 0.16f
    )
    private val bottom = androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.34f)),
        startY = oT + bh * 0.80f, endY = oT + bh
    )

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, alpha: Float) = with(scope) {
        drawRect(top, androidx.compose.ui.geometry.Offset(oL, oT), androidx.compose.ui.geometry.Size(bw, bh * 0.16f), alpha = alpha)
        drawRect(bottom, androidx.compose.ui.geometry.Offset(oL, oT + bh * 0.80f), androidx.compose.ui.geometry.Size(bw, bh * 0.20f), alpha = alpha)
    }
}
