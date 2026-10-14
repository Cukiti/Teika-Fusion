package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

import java.util.UUID

/**
 * **EL COMODÍN** (P-133, 2026-09-03): el `colorType` que significa «este bloque vale como cualquier
 * color».
 *
 * Era un **7 a pelo** repetido en diez sitios —el motor y ocho puntos de la UI— y eso es frágil por
 * una razón concreta: la paleta de `BlockSkins` tiene **cinco** colores y pinta con
 * `colorsFor(colorType % 5)`, así que un comodín que llegue al dibujo sin filtrar **no falla: sale
 * pintado del color 2**. Solo funciona porque cada uno de esos diez sitios comprueba el 7 antes.
 *
 * Con el color convertido en regla —testarudos, Línea Pura, Brocha y Camaleón— un comodín mal
 * pintado ya no es un detalle estético: es una jugada mal leída.
 *
 * Vale 7 y no 5 a propósito: deja hueco para colores nuevos sin tocar guardados, porque el valor
 * **se serializa** dentro de cada bloque.
 */
const val WILDCARD_COLOR = 7

/** Colores reales de bloque, sin contar el comodín. Los produce `Piece.random`. */
const val REAL_COLOR_COUNT = 5

/**
 * Define el contenido posible de cualquier celda en la cuadrícula.
 * @JsonClassDiscriminator uses "kind" to avoid clash with CellContent.Teika.type field.
 */
@Serializable
// kotlinx.serialization 1.11 exige opt-in explícito para @JsonClassDiscriminator. El discriminador
// "kind" es parte del FORMATO DE GUARDADO — cambiarlo invalida los saves existentes.
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@JsonClassDiscriminator("kind")
sealed class CellContent {
    val isOccupied: Boolean get() = this !is Empty

    /** Celda vacía, lista para recibir piezas. */
    @Serializable
    data object Empty : CellContent()
    
    /**
     * Un bloque básico de madera colocado por el jugador.
     * (En la it. 34 se RETIRÓ `preplaced` — marcador del motor auto-pop erradicado que ya no
     *  consumía nadie; `ignoreUnknownKeys` traga los guardados viejos que aún lo traigan.)
     */
    @Serializable
    data class Wood(
        // NO retirar: es la `key()` de Compose de la celda en GameBoard y el registro `animatedIds`
        // que garantiza que el pop de spawn suene UNA vez por bloque. Keyear por Position rompería
        // el pop al reponer madera sobre una celda recién limpiada (misma identidad de composable).
        val id: String = UUID.randomUUID().toString(),
        val colorType: Int = 0,
        val skipSpawnAnimation: Boolean = false,
        val targetColor: TargetColor? = null,
        // ── Bioma Cristal: golpes restantes para destruirse. 0 = madera normal.
        //    >0 = bloque cristal; al limpiar su línea NO desaparece: pierde 1 de armadura
        //    (se agrieta + rebota) y sobrevive como obstáculo hasta llegar a 0.
        val armor: Int = 0,
        // ── Bioma Eléctrico: número OCULTO de orden en el circuito de su fila (0 = sin bobina;
        //    1..4 = posición en la secuencia). Aparecen al completar una fila (energizeCircuitRows).
        //    Una bobina (coil>0) bloquea la limpieza de su fila y columna. El jugador debe ADIVINAR
        //    la secuencia tocándolas en orden: el toque correcto la enciende (coilLit=true, revela su
        //    número); fuera de orden = cortocircuito (se apagan). Al encender todas las de la fila, el
        //    circuito se descarga (rompe la fila + cadena a filas adyacentes). Ver GameViewModel.tapCoil.
        val coil: Int = 0,
        val coilLit: Boolean = false,
        // ── Bioma Eléctrico: el número de esta bobina YA fue descubierto alguna vez (toque correcto).
        //    Una vez visto, su número se sigue mostrando (atenuado) aunque un cortocircuito la apague,
        //    para que el circuito sea un puzzle de DEDUCCIÓN justo y no de memoria pura.
        val coilSeen: Boolean = false,
        // ── Bioma Tóxico: bloque envenenado. Cada DECAY_TURNS colocaciones se pudre sin puntos y queda
        //    como LODO (`sludge`, P-322), salpicando veneno a un vecino. Romperlo en una línea lo salva.
        //    Si quedan pocas minas, un charco central recarga (Recarga). Ver applyBiomeTick TOXIC.
        val poisoned: Boolean = false,
        // ── Bioma Volcán: MAGMA (2026-09-23). La fila bajo la lava arde y, si el jugador se queda quieto
        //    [VolcanoMagma.HEAT_MS], se funde. Una línea con magma se rompe SIN Teika ni puntos (las
        //    estrellas sí se cobran). Default false: los guardados viejos cargan igual.
        val magma: Boolean = false,
        // ── Mansión Espectral · EL ESPEJO (P-246, 2026-09-23): turnos que le quedan a un BLOQUE FANTASMA
        //    (0 = madera real). Ocupa y cuenta para líneas; se desvanece al llegar a 0. Ver HauntedMirror.
        val phantom: Int = 0,
        // ── Espacio Profundo · ROCA ESPACIAL (P-294, 2026-09-25): el bloque que fundió un meteorito. Va
        //    siempre con `armor` ≥ 1 (una línea le arranca la roca y deja la madera); esta marca solo
        //    cambia su cara de cristal a roca. Se apaga cuando el blindaje llega a 0. Ver MeteorShower.
        val rock: Boolean = false,
        // ── Espacio Profundo · LA ROCA QUE SE AGRIETA (2026-09-26): colocaciones que lleva viva esta roca.
        //    A las `MeteorShower.CRACK_TURNS` contagia a un bloque vecino y vuelve a 0. Solo cuenta con
        //    `rock`. Default 0: los guardados viejos cargan igual.
        val rockAge: Int = 0,
        // ── Pantano Tóxico · LODO (P-322, 2026-09-26): lo que deja un bloque envenenado al pudrirse. Ocupa
        //    su casilla como madera normal (una línea lo rompe) pero ya no lleva estrella ni se puede volver
        //    a envenenar. Default false: los guardados viejos cargan igual.
        val sludge: Boolean = false,
        // ── Dojo en Sombras · CICATRIZ DE ORO (P-323, 2026-09-26): bloque que el kintsugi deja en la costura
        //    al cerrarse la grieta. Madera normal con vetas de oro (solo cambia la cara). Default false.
        val kintsugi: Boolean = false,
        // ── Jardín · ENREDADERA (P-324, 2026-09-26): lo que deja la raíz por donde arrastra, y lo que brota
        //    de un borde si no hay Teika que agarrar. Madera normal (una línea la rompe) sin estrella; solo
        //    cambia la cara. Default false.
        val vine: Boolean = false,
        // (Se RETIRÓ `chrono` — el sistema de cronómetros del Infinito, y `concrete` — el bloque de
        //  HORMIGÓN que empujaba la marea. Los dos murieron con el modo el 2026-08-09; `concrete`
        //  sobrevivió hasta P-132 (2026-09-03) como bandera que **nadie ponía nunca a true**, con
        //  catorce guardas consultándola, un color propio en `CellView` y un comentario que
        //  describía la marea en presente. `ignoreUnknownKeys` traga los guardados viejos que aún
        //  traigan cualquiera de las dos claves.)
        // ── P-71 · TESTARUDO: solo se rompe si la línea que lo limpia trae suficientes bloques de
        //    SU MISMO `colorType` (ver `StubbornBlocks`). Si no, aguanta y se agrieta. Es la primera
        //    regla del juego que convierte el color en un OBSTÁCULO además de en un premio.
        val stubborn: Boolean = false,
        /** Direction of the Domino arrow: right/down/left/up; -1 means ordinary wood. */
        val dominoDir: Int = -1,
        // ── P-240 · EL IMÁN: este bloque es el imán que el jugador colocó. La marca viaja CON el
        //    bloque (y no como una posición en el estado) porque el Pasaje Encantado puede moverlo:
        //    el imán tiene que seguir tirando desde donde está, no desde donde se puso.
        val magnet: Boolean = false,
        // ── P-234 · REACCIÓN EN CADENA: este bloque es un BARRIL. Cuando una línea lo rompe,
        //    estalla en 3×3 y detona los barriles que alcance. Viaja con el bloque, como el imán.
        val barrel: Boolean = false,
        // ── P-256 · BLOQUE-TEIKA: cuando una línea rompe este bloque, suelta un Teika (su tier crece
        //    con las líneas de la pasada — ver TeikaSeeds.tierFor). Default false: guardados viejos igual.
        val teikaSeed: Boolean = false
    ) : CellContent()
    
    /**
     * Un Teika producto de una fusión.
     * Los Teikas grandes (ej. 2x2) ocuparán múltiples celdas en el GameState,
     * pero todas referenciarán la misma instancia de `Teika` (mismo ID).
     * @param rootPosition La coordenada superior izquierda (origen) del Teika para el renderizado.
     */
    @Serializable
    data class Teika(
        val type: TeikaType,
        val id: String = UUID.randomUUID().toString(),
        val rootPosition: Position,
        val skipSpawnAnimation: Boolean = false
    ) : CellContent()
}
