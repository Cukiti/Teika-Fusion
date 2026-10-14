package com.cukiti.teikafusion.model

import java.util.UUID

/**
 * Cola de cues de **un solo uso** para la UI (VFX/SFX/háptica).
 *
 * **El contrato**: el ViewModel añade el evento dentro de la actualización de estado; **exactamente
 * un** sitio de la UI lo recoge, reproduce su animación y llama a `consumeEvent(id)`. Un segundo
 * consumidor sobre el mismo tipo borra el evento del estado antes de que la animación arranque y la
 * corta a un frame — es el bug recurrente de este proyecto (it. 2 y it. 19). Cuando dos capas se
 * reparten un mismo tipo, el reparto tiene que ser **explícito y disjunto** (ver `ComboText`, partido
 * por tier entre `GameBoard` y `GameScreen`, o `FloatingScore`, partido por `isCombo`).
 *
 * **Todo lo que hay directamente dentro de esta clase sellada ES un evento** (extiende `GameEvent()`).
 * Los tipos que solo son **carga útil** —los `List<...>` que viajan *dentro* de un evento— van
 * **anidados dentro del evento que los lleva**, nunca sueltos aquí: `AstroStrike.AstroLift`,
 * `MegaTeikaFriendExpansion.FlungBlock`… Esos tipos **no se emiten ni se consumen** por su cuenta, y
 * tenerlos sueltos entre los eventos ya provocó una auditoría entera persiguiendo fantasmas
 * (it. 20): parecían eventos sin consumidor y no lo eran.
 */
sealed class GameEvent {
    data class PinballImpact(val x: Float, val y: Float, val bodyId: String) : GameEvent()
    /** A committed rule action; visuals consume it once and never award gameplay rewards. */
    data class RulePulse(val kind: String, val cells: List<Position>,
        val points: List<Position> = emptyList(),
        val wood: List<Pair<Position, CellContent.Wood>> = emptyList(),
        val hitTimes: List<Int> = emptyList(), val durationMs: Int = 480,
        val originalPiece: Piece? = null, val activePieceId: String? = null,
        val separator: Pair<Int,Int>? = null, val direction: Int = 1, val bodyId: String? = null, val bodyIds: List<String> = emptyList(),
        val moves: List<Pair<Position,Position>> = emptyList(), val ballArc: BallArc? = null, val clawLanding: ClawTrip? = null) : GameEvent()
    val id: String = UUID.randomUUID().toString()
    /** Instante de creación (ms). Permite recolectar eventos huérfanos que nunca se consumieron. */
    val createdAt: Long = System.currentTimeMillis()

    data class FloatingScore(
        val score: Int,
        val position: Position,
        val isCombo: Boolean = false,
        val comboStreak: Int = 1
    ) : GameEvent()

    data class ScreenShake(
        val intensity: Float = 1f
    ) : GameEvent()

    /** Partículas de explosión. colorType = -1 → Teika, otro valor → color de bloque. */
    data class Particles(
        val position: Position,
        val colorType: Int = -1
    ) : GameEvent()

    // (MergeAnimation y PushAnimation ELIMINADOS: la fusión y los empujes por celdas eran del
    //  motor de grid; con el motor físico Suika los cuerpos se animan solos y la fusión es
    //  contacto + pop. Ver TeikaPhysicsWorld / GameViewModel.processContactFusion.)

    /**
     * Combo text banner (NICE → LEGENDARY). tier 1–9 controla la palabra/color;
     * [streak] es el multiplicador real (×N) que se muestra como protagonista en el centro.
     */
    data class ComboText(
        val tier: Int,
        val streak: Int,
        val position: Position
    ) : GameEvent()

    /** Shockwave de multi-line clear (≥ 2 líneas simultáneas). */
    data class ChainPulse(
        val center: Position,
        val linesCleared: Int
    ) : GameEvent()

    /**
     * ¡PERFECTO!: el clear dejó el tablero SIN MADERA (referencia Block Blast, 2026-07-20).
     * Celebración dorada centrada en el tablero + [bonus] de puntos ya sumado por el motor
     * (en Infinito además paga tiempo vía TimeGain). [delayMs] espera el telegraph del clear.
     */
    data class PerfectClear(
        val bonus: Int,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * Clímax de fusión de Teikas: dos iguales se tocaron y nació el siguiente tier en [center].
     * VFX MATE (sin partículas ni glow láser): onda de choque en anillo + destello del color del
     * [tier], escalado por tier (T2 sutil … T4 momentazo). El "clunk"/squash lo da el rebote de
     * aparición del recién nacido.
     */
    data class FusionBurst(
        val center: Position,
        val tier: Int
    ) : GameEvent()

    /**
     * **LA CEREMONIA DE LA FUSIÓN** (generalizada a todos los tiers el 2026-07-31).
     *
     * Antes solo la tenían los T3: el resto de fusiones ocurría en UN frame —los dos cuerpos
     * desaparecían y aparecía el burst—, así que el jugador veía el resultado pero **nunca veía
     * fundirse nada**, que es justo el momento jugoso del juego. Ahora toda pareja hace su
     * acercamiento: los dos orbes viajan al centro, crecen y florecen en un punto incandescente
     * antes de nacer el nuevo. Escala con el tier ([tier]): rápido y limpio abajo, momentazo arriba.
     *
     * Anticipación de la fusión. Durante
     * [durationMs] el ViewModel RETIENE los dos T3 ([aId]/[bId] quedan kinematic y OCULTOS en la
     * capa de cuerpos) y este evento pinta el momentazo: los dos orbes se acercan y crecen hacia
     * [cx],[cy], un scrim radial enfoca (pseudo cámara lenta) y un punto de contacto
     * blanco-incandescente florece en el centro. Al terminar, el ViewModel ejecuta la fusión real
     * (nace el T4 con su FusionBurst + shake). Coordenadas en CELDAS (centros continuos de la física).
     */
    data class FusionCharge(
        val aId: String,
        val bId: String,
        val ax: Float, val ay: Float,
        val bx: Float, val by: Float,
        val cx: Float, val cy: Float,
        val durationMs: Int,
        /**
         * Tier de los DOS cuerpos que se están fundiendo (1..4). Manda el tamaño de los orbes y
         * **cuánto drama** lleva la ceremonia: un T1+T1 es un acercamiento rápido y limpio; un
         * T3+T3 es el momentazo con scrim y cámara lenta. El **4** no sube el volumen: cambia de
         * coreografía — es el COLAPSO ESTELAR que precede al MEGA ([MegaCollapse], `MegaCollapseVFX`).
         */
        val tier: Int = 3
    ) : GameEvent()

    /**
     * Un target fue recolectado (limpiado del grid).
     * fromGridPos = celda de origen. delayMs sincroniza el vuelo con la animación de shatter.
     */
    data class TargetCollected(
        val color: com.cukiti.teikafusion.model.TargetColor,
        val fromGridPos: Position,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * **Estrellas huérfanas que MUTAN** (2026-08-26): un color del objetivo acaba de quedar cubierto
     * y las estrellas suyas que seguían en el tablero se han retintado al color que más falta
     * ([StarObjectiveRetint]). La UI pinta la mutación —la estrella se apaga, gira y vuelve con el
     * color nuevo— celda a celda con un escalonado.
     *
     * `cells` viaja ya emparejado con su color de destino y en orden estable.
     */
    data class StarRetint(
        val cells: List<Retinted>,
    ) : GameEvent() {
        /** Carga útil: una celda y el color al que pasa. No se emite ni se consume por su cuenta. */
        data class Retinted(val cell: Position, val color: TargetColor)
    }

    /**
     * Revivir cuando solo los Teikas bloquean el tablero: los cuerpos visibles se condensan sin
     * puntos/XP en dos iguales separados. La UI es dueña de esta coreografía y consume el evento.
     */
    data class ReviveTeikaRescue(
        val bodies: List<BodyVisual>,
        val finalType: TeikaType,
        val finalAx: Float,
        val finalAy: Float,
        val finalBx: Float,
        val finalBy: Float,
        val durationMs: Int = 1_350,
    ) : GameEvent() {
        data class BodyVisual(
            val type: TeikaType,
            val x: Float,
            val y: Float,
        )
    }

    /**
     * CONFETI DE COLOCACIÓN (2026-07-20, referencia art/videoblock.mp4): al posar una ficha en el
     * tablero estalla un puñado corto de cuadraditos y círculos por el CONTORNO de la huella recién
     * colocada. Salen disparados ligeramente hacia fuera y se apagan por transparencia en ~380 ms.
     *
     * Un solo evento por colocación (no uno por celda): así el VFX dibuja toda la ráfaga en un
     * Canvas con un reloj único y la huella se lee como una pieza, no como N celdas sueltas.
     * Vale para los tres skins — es feedback de "colocaste", no identidad de skin.
     *
     * @param cells     celdas que ocupa la ficha (en coordenadas de tablero).
     * @param colorType color de la ficha; tiñe las partículas con la paleta del skin activo.
     */
    data class PiecePlacedPop(
        val cells: List<Position>,
        val colorType: Int
    ) : GameEvent()

    /**
     * Rotura premium de una celda al limpiar línea/columna (efecto dominó).
     * La celda se mantiene visible (renderizada por el VFX) hasta su `delayMs`,
     * luego flash blanco + pop + cubitos pequeños saliendo alrededor.
     * Reemplaza la combinación legacy LineClearGlow + Particles + StarBurst.
     */
    /**
     * **P-256 · UN BLOQUE-TEIKA ESTALLA**: sus púas de cristal salen disparadas y del centro nace el
     * Teika de tier [tier] (el cuerpo lo crea el motor a la vez; esto es el estallido que lo presenta).
     */
    data class TeikaSeedBurst(val position: Position, val tier: Int, val delayMs: Int = 0) : GameEvent()

    /**
     * **EL PREMIO DE LA BANDEJA** (2026-09-26): la reposición llegó pensada para colocarse porque una
     * jugada rompió [reward] líneas a la vez ([TrayReward]). La bandeja brilla y suena al entrar —
     * un premio que no se ve no enseña nada. [pieceIds] son las fichas premiadas.
     */
    data class TrayRewardDealt(val reward: Int, val pieceIds: List<String>) : GameEvent()

    /** Window coordinates capture the explicit release; only the VFX consumes this transient cue. */
    data class FichonaShattered(val piece: Piece, val originX: Float, val originY: Float,
        val cellSizePx: Float) : GameEvent()


    /**
     * **P-353 · ¡PARADA!** El jugador rompió una línea por la zona amenazada JUSTO en el turno del golpe
     * y el castigo de [biome] no cayó. Lo consume `BiomeParryVFX` en el tablero.
     */
    data class BiomeParry(val biome: BiomeType) : GameEvent()

    data class LineCellShatter(
        val position: Position,
        val colorType: Int,
        val delayMs: Int = 0,
        /**
         * Bloque-ESTRELLA (cofre dorado): si no es null, el VFX dibuja el bloque en espera como
         * COFRE con su estrella a TAMAÑO COMPLETO (igual que CellView) hasta el instante de
         * reventar — sin esto la estrella "desaparecía/encogía" durante la espera del dominó y
         * el vuelo de TargetCollected arrancaba de la nada.
         */
        val targetColor: TargetColor? = null,
        /**
         * Clear con COMBO activo: durante la espera del telegraph la celda luce un glint dorado
         * pequeño (marca de "esta celda contribuye al combo", referencia Block Blast). Solo un
         * sprite barato sobre la textura del bloque, no geometría nueva.
         */
        val bonusSparkle: Boolean = false,
        /** FOGONAZO BLANCO sobre la celda justo antes de reventar: el golpe de la rotura. */
        val whiteFlash: Boolean = true
    ) : GameEvent()

    /**
     * Haz de luz que barre una fila/columna completa al destruirse (estilo Block Blast).
     * Núcleo blanco brillante + borde cromático (aberración arcoíris) + puntas que
     * sobresalen de los bordes + flash de toda la línea. Se dibuja ENCIMA del
     * LineCellShatter (cubos 3D) para una destrucción rica y celebratoria.
     *
     * @param isRow      true → barrido horizontal (fila); false → vertical (columna).
     * @param lineIndex  índice de la fila (y) o columna (x) destruida.
     * @param colorType  color dominante de la línea (tinta el borde cromático).
     */
    data class LineClearBeam(
        val isRow: Boolean,
        val lineIndex: Int,
        val colorType: Int,
        val delayMs: Int = 0,
        /** Tramo barrido a lo largo de la línea (MEDIA FILA del corte ninja): celda inicial… */
        val spanStart: Int = 0,
        /** …y cuántas celdas cubre. Por defecto, la línea entera. */
        val spanCount: Int = 8
    ) : GameEvent()

    /**
     * Pequeña explosión de cubitos/estrellas con física (salto + gravedad + decay).
     * Solo usado para efectos no relacionados a limpiar líneas (combos, derrota).
     */
    data class StarBurst(
        val position: Position,
        val colorType: Int = -1,
        val isGem: Boolean = false,
        val intensity: Float = 1f,
        val delayMs: Int = 0     // efecto dominó: retraso antes de empezar la animación
    ) : GameEvent()

    /**
     * Un turno sin line-clear pero con comboStreak activo:
     * el combo sobrevive esta vez (ventana de gracia de 1 turno).
     */
    data class ComboGrace(
        val comboStreak: Int,
        val position: Position
    ) : GameEvent()

    // (ERRADICADO 2026-07-26) Aquí vivía `MegaFuryShift`, el rótulo «EL MURO SE ENFADA» del
    //  rubber-band del Infinito. Su disparador era la FIEBRE, que se retiró con «La Prensa»: el
    //  evento llevaba dos reworks sin poder emitirse. Ver GameViewModel (Furia del Mega).

    /**
     * El tablero está siendo "limpiado" por la secuencia de derrota.
     * Marca el inicio de la fase BOARD_WIPE — UI puede oscurecer/preparar overlay.
     */
    data class BoardWipe(val totalCells: Int) : GameEvent()

    /**
     * T4+T4 fusionados (o JACKPOT de pop ≥22): un MEGA TEIKA irrumpe llenando TODO el tablero y
     * EMPUJA cuanto había —bloques y los demás Teikas— fuera de los bordes, limpiándolo (rework
     * 2026-07-09; desde el 2026-09-24 precedido por el COLAPSO ESTELAR —`FusionCharge` de tier 4— y
     * celebrado con fogonazo, ondas, rayos y rótulo). El estado ya dejó el grid
     * vacío y borró los cuerpos; este evento lleva la FOTO de lo que había para que el VFX lo dibuje
     * saliendo despedido con el mismo "pop" de aparición de un Teika.
     *
     * La coreografía es una OLEADA DE EMPUJE ACOPLADA AL CRECIMIENTO (2026-07-25): cada bloque/Teika
     * de la foto arranca su vuelo cuando el radio del Mega lo alcanza, no después de que el Mega haya
     * terminado de crecer. Tiempos y fórmulas: [MegaFling].
     */
    data class MegaTeikaFriendExpansion(
        val boardCenter: Position = Position(GameState.GRID_SIZE / 2, GameState.GRID_SIZE / 2),
        val flungBlocks: List<FlungBlock> = emptyList(),
        val flungTeikas: List<FlungTeika> = emptyList(),
        /**
         * Bloques CON ESTRELLA: el Mega no los despide, los ROMPE en el sitio (2026-08-09).
         *
         * Volaban fuera del tablero con todo lo demás y su estrella se iba con ellos: la jugada más
         * difícil del nivel te BORRABA el objetivo en vez de completarlo. Ahora cada uno estalla
         * cuando el radio del Mega lo alcanza y su estrella sale volando al panel por la vía normal
         * (`TargetCollected`, con el mismo retardo que su rotura).
         */
        val brokenBlocks: List<FlungBlock> = emptyList()
    ) : GameEvent() {
        /** Bloque de madera que había en [pos] al limpiar; el VFX lo lanza fuera del tablero. */
        data class FlungBlock(
            val pos: Position,
            val colorType: Int,
            val targetColor: TargetColor? = null,
            val isCrystal: Boolean = false
        )
        /** Cuerpo de Teika (centro en celdas + tier) que había al limpiar; sale despedido igual. */
        data class FlungTeika(val x: Float, val y: Float, val type: TeikaType)
    }

    /**
     * El jugador tocó el banner de un personaje nombrado y activó la secuencia
     * de revelación épica. La UI debe mostrar el overlay de revelación completo.
     *
     * @param characterId ID del personaje recién reclamado (para consumir y no repetir).
     */
    data class CharacterRevealed(
        val characterId: String
    ) : GameEvent()

    /** Companion ability was activated — UI plays feedback animation. */
    /**
     * **EL ECO** (P-270, rótulo pendiente de P-269, hecho 2026-09-25): el lanzamiento de [companionId]
     * NO gastó su carga (niveles 4–5, ver `AbilityScaling.echoChance`). Hasta hoy solo sonaba y vibraba;
     * un premio que no se ve se confunde con un fallo del contador. Lo pinta `AbilityEchoLabel`
     * junto al botón de power-ups.
     */
    data class AbilityEcho(val companionId: String) : GameEvent()

    data class CompanionAbilityFired(
        val companionId: String,
        val position: Position = Position(4, 4),
        val axis: Int = -1                        // Fuego: 0 = fila, 1 = columna; -1 = no aplica
    ) : GameEvent()

    /**
     * Espacio Profundo · LLUVIA DE METEORITOS (P-294, aviso 1): un meteorito asoma a lo lejos con su
     * estela. Ya sabe dónde caerá ([at], esquina del cráter), pero el cráter no se marca hasta el aviso 2.
     */
    data class MeteorSpotted(val at: Position) : GameEvent()

    /**
     * Espacio Profundo · LLUVIA DE METEORITOS (impacto): el meteorito cae en el cráter de [at]. [rocks]
     * = los bloques que se funden en roca (el estado ya los tiene blindados) y [pushedTeikaIds] los
     * cuerpos que el impacto lanza lejos.
     */
    data class MeteorImpact(
        val at: Position,
        val rocks: List<Position>,
        val pushedTeikaIds: List<String> = emptyList(),
        val durationMillis: Int = 1000,
        /** P-319 · METRALLA: casillas vacías donde caen los fragmentos de roca (el estado los pone al aterrizar). */
        val shards: List<Position> = emptyList(),
    ) : GameEvent()

    /**
     * Cielo Estelar · LA LUNA DEVUELVE LO QUE ROBA (P-321): los bloques robados vuelven a caer como madera
     * sin estrella en [cells], con los colores [colorTypes]. El estado los pone al aterrizar.
     */
    data class MoonReturn(
        val cells: List<Position>,
        val colorTypes: List<Int>,
        val durationMillis: Int = 650,
    ) : GameEvent()

    /**
     * Espacio Profundo · LA ROCA SE AGRIETA (P-319): la roca de [from] lleva [MeteorShower.CRACK_TURNS]
     * colocaciones en el tablero y contagia al bloque de [to] (el estado lo funde a mitad del VFX).
     */
    data class RockCrack(val from: Position, val to: Position, val durationMillis: Int = 700) : GameEvent()

    /** Espacio Profundo · el jugador rompió una línea que cruzaba el cráter: el meteorito se DESVÍA. */
    data class MeteorDeflect(val at: Position) : GameEvent()

    /**
     * Cristal / Escudo Cristalino: una pieza vuela SUAVEMENTE desde su hueco de la bandeja
     * ([pieceId]) hasta su mejor posición del tablero ([targetCells]) antes de estamparse.
     * La UI dibuja la pieza en tránsito (arco + crecimiento + estela cristalina); el
     * ViewModel la coloca de verdad justo al aterrizar.
     */
    data class CrystalFlight(
        val pieceId: String,
        val targetCells: List<Position>,
        val colorType: Int,
        val isWildcard: Boolean = false,
        val durationMs: Int = 440,
        // Cristal: nº de cuartos de giro que la pieza "rota" en vuelo para encajar mejor.
        val rotationTurns: Int = 0,
        // Kenji / Doble Sombra: el CLON vuela con AURA NINJA (resplandor índigo + estelas de
        // velocidad) en vez de la estela cristalina cian del Cristal.
        val ninja: Boolean = false
    ) : GameEvent()

    /**
     * Kenji / CORTE DE SOMBRAS: [slashes] tajos de katana cruzan la PANTALLA entera y parten por la
     * mitad cada bloque con estrella de [cells]. La UI dibuja los tajos (destellos que barren el
     * tablero en diagonal, escalonados) y, en cada víctima, las dos mitades separándose.
     */
    data class ShadowBladeCut(
        val cells: List<Position>,
        val slashes: Int
    ) : GameEvent()

    /**
     * Glotón / Devorar: una criatura recorre el tablero siguiendo [path] (en orden) y se
     * "come" los bloques a su paso. La UI dibuja al glotón viajando por la ruta mientras el
     * ViewModel rompe cada celda sincronizado con su llegada.
     */
    /**
     * Timoteo / **PUZZLE DESLIZANTE** — la entrada (rework 2026-08-27).
     *
     * Sus pasadas ya se animaban (losetas deslizando), pero el arranque era el destello genérico de
     * compañero: nada decía «esto es un rompecabezas». Ahora una **rejilla de rompecabezas** se
     * dibuja sobre el tablero con sus pestañas y muescas, tiembla (Timoteo es el miedoso: le tiembla
     * el pulso) y se desvanece dejando paso a las losetas.
     */
    data class PuzzleGridReveal(
        val durationMillis: Int
    ) : GameEvent()

    /**
     * Cristóbal / **ESCUDO CRISTALINO** — la entrada (rework 2026-08-27).
     *
     * Su efecto (las fichas volando solas a su hueco, `CrystalFlight`) ya era bueno; lo que faltaba
     * era el principio: las fichas empezaban a volar sin que nada anunciara a Cristóbal, así que
     * parecía que el juego se colocaba las piezas solo. Dos tiempos:
     *
     *  1. **Cristalización** ([growMs]) — facetas de cristal crecen desde los bordes hacia dentro y
     *     refractan la luz: el tablero se blinda.
     *  2. **Escaneo** ([scanMs]) — un barrido de luz recorre el tablero y **destella en los huecos**
     *     que va a rellenar. Es el momento «ya sé dónde va esto», y es lo que hace que las fichas
     *     que vuelan después se lean como decisión suya y no como magia del motor.
     */
    data class CrystalAegis(
        val growMs: Int,
        val scanMs: Int,
        val durationMillis: Int
    ) : GameEvent()

    /**
     * Rayco / **ENERGÍA CRUZADA** — el momento de energizar (rework 2026-08-27).
     *
     * Antes el casteo eran unas partículas y una sacudida: el aura persistente de los Teikas
     * energizados aparecía sin más, así que no se veía a Rayco hacer nada. Ahora **cae un rayo sobre
     * cada Teika elegido**, escalonado, y el aura se enciende con el impacto.
     *
     * Las posiciones van en coordenadas de celda con decimales porque los Teikas son cuerpos
     * físicos: viven FUERA del grid y casi nunca están centrados en una celda.
     */
    data class EnergizeStrike(
        val targets: List<StrikeTarget>,
        val boltMs: Int,
        val stepMs: Int,
        val durationMillis: Int
    ) : GameEvent() {
        /** Un Teika alcanzado por el rayo: dónde está y cómo de grande es. */
        data class StrikeTarget(val x: Float, val y: Float, val radius: Float)
    }

    /**
     * Maite / **SIESTA** — la ceremonia (rework 2026-08-27).
     *
     * Su mecánica es gravedad: el tablero se desploma y caen filas-bonus. Funciona, pero **no se
     * parecía en nada a una siesta** — era un desplome con un destello genérico encima, y el jugador
     * no entendía qué pintaba Maite ahí. Estos son los tiempos que le dan la fantasía:
     *
     *  1. **Bostezo** ([yawnMs]) — una ola de sueño baja por el tablero; detrás de ella la madera se
     *     "afloja" (se ladea y se apaga). Es la anticipación: por eso el tablero se va a caer.
     *  2. **Desplome** — lo animan los `WoodFall` de siempre, ahora retrasados hasta que el sueño
     *     ha llegado abajo.
     *  3. **Nube de sueño** ([cloudMs]) — arriba se forma una nube de la que nacen las filas-bonus.
     *
     * [collapseCells] son las celdas que se van a desplomar (la ola las va durmiendo a su paso) y
     * [cloudColumns] las columnas donde nacerán bloques, para que la nube se dibuje sobre ellas.
     */
    data class DrowsySpell(
        val collapseCells: List<Position>,
        val cloudColumns: List<Int>,
        val yawnMs: Int,
        val cloudAtMs: Int,
        val cloudMs: Int,
        val durationMillis: Int
    ) : GameEvent()

    /**
     * Galo / **ECLIPSE VIVO** — la ceremonia (rework 2026-08-27).
     *
     * Antes la habilidad se anunciaba con el destello genérico de compañero y la madera reventaba:
     * pasaba algo, pero no se leía un eclipse. Ahora son cuatro tiempos sobre [cells]:
     *
     *  1. **Oscurecimiento** ([darkenMs]) — un disco negro cruza y va apagando el tablero; el borde
     *     enciende su corona.
     *  2. **Lente** ([lensMs]) — la zona se deforma hacia [center]: anillos de lente gravitacional
     *     que anuncian dónde va a doler.
     *  3. **Implosión** ([implodeMs]) — todo espirala hacia dentro y desaparece. Aquí caen los
     *     `LineCellShatter`, escalonados desde el centro.
     *  4. **Vuelta de la luz** ([restoreMs]) — destello con aberración cromática y el tablero
     *     recupera su color.
     */
    data class LivingEclipse(
        val cells: List<Position>,
        val center: Position,
        val darkenMs: Int = 620,
        val lensMs: Int = 420,
        val implodeMs: Int = 700,
        val restoreMs: Int = 380
    ) : GameEvent() {
        /** Cuándo empieza a pulverizarse la madera: al cerrarse la lente. */
        val impactAtMs: Int get() = darkenMs + lensMs
        val totalMs: Int get() = darkenMs + lensMs + implodeMs + restoreMs
    }

    /**
     * Morti / **POSESIÓN** (habilidad, rework 2026-08-27). Los bloques [steps] se vuelven
     * espectrales, ATRAVIESAN el tablero y se solidifican en el hueco que completa línea.
     *
     * Los tiempos viajan en el evento porque el ViewModel los necesita para saber cuándo revelar la
     * madera aterrizada: el bloque existe en el grid desde el primer frame, pero está oculto hasta
     * que el ojo lo ve encajar (`hiddenWoodCells`), y solo entonces la línea revienta.
     */
    data class GhostPossession(
        val steps: List<PossessionStep>,
        val liftMs: Int,
        val flightMs: Int,
        val stepMs: Int,
        val durationMillis: Int
    ) : GameEvent() {
        /** Un bloque poseído: de dónde sale, dónde encaja y de qué color es. */
        data class PossessionStep(
            val from: Position,
            val to: Position,
            val colorType: Int,
            val targetColor: TargetColor? = null
        )
    }

    // (PODADO 2026-08-27) `GluttonRun`: la criatura que recorría el tablero en zigzag comiendo la
    // madera de las filas más llenas. Murió con «Devorar» al convertirse en EL BANQUETE — lo cazó
    // `GameEventContractTest` en la misma pasada, que es exactamente para lo que está.

    /**
     * Chueta / **EL BANQUETE** (habilidad, rework 2026-08-27). El glotón asoma por el BORDE INFERIOR
     * del tablero y abre las fauces; los bloques con estrella [cells] se despegan uno a uno y caen
     * en arco a su boca. Cada uno que traga paga su estrella y sus puntos.
     *
     * La coreografía es el efecto, así que sus tiempos viajan en el evento y el ViewModel los usa
     * para escalonar los `TargetCollected`: el contador sube AL TRAGAR cada bloque, no todos de
     * golpe al final. [openMs] es la anticipación (la boca abriéndose antes de que caiga el primero)
     * y [stepMs] el hueco entre bocado y bocado.
     */
    data class GluttonFeast(
        val cells: List<Position>,
        val openMs: Int,
        val stepMs: Int,
        val durationMillis: Int
    ) : GameEvent()


    // ── Biomas temáticos ──────────────────────────────────────────────────────

    /**
     * Bioma Cristal: un bloque cristal recibió un clear pero NO se rompió — se agrieta y
     * rebota. La UI hace squash/bounce + chispas de cristal. [remainingArmor] = armadura
     * tras el golpe (0 cuando se romperá en el siguiente clear).
     */
    data class CrystalCrack(
        val position: Position,
        val remainingArmor: Int,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * Bioma Cristal: la cáscara de cristal se ROMPE de verdad (último clear, armor 1→0) y deja
     * ver el bloque interior que se destruye debajo. La UI dibuja esquirlas de vidrio saliendo +
     * un destello de revelación, sincronizado con el [LineCellShatter] del bloque interior.
     * [colorType] = color del bloque revelado (tinta sutil de las esquirlas).
     */
    data class CrystalShatter(
        val position: Position,
        val colorType: Int,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * Un Teika comprimido necesitó esta celda para caber y rompió el bloque con su empuje. A
     * diferencia del clear, [crystalArmor] > 0 no absorbe el golpe: vidrio y madera salen juntos.
     * El vector normalizado [pushX]/[pushY] dirige la compresión y las esquirlas del VFX.
     */
    data class TeikaPushBreak(
        val position: Position,
        val colorType: Int,
        val targetColor: TargetColor? = null,
        val crystalArmor: Int = 0,
        val pushX: Float = 0f,
        val pushY: Float = -1f
    ) : GameEvent()

    /**
     * Bioma Miedoso: un bloque "se asustó" por la pieza recién colocada y HUYE una celda
     * alejándose. El grid ya lo movió a [to] (oculto en hiddenWoodCells durante el viaje); la UI
     * dibuja el bloque escabulléndose de [from] a [to] + polvo en el origen.
     */
    data class BlockFlee(
        val from: Position,
        val to: Position,
        val colorType: Int,
        // El bloque viaja CON sus decoraciones (estrella-objetivo / baba tóxica): el VFX de vuelo
        // las dibuja para que no "desaparezcan" durante los ~280 ms del deslizamiento.
        val targetColor: TargetColor? = null,
        val poisoned: Boolean = false,
        /** Bloque de CRISTAL (armor > 0): el vuelo dibuja su carcasa, como la celda en reposo. */
        val armor: Int = 0,
        val delayMs: Int = 0,
        /** El bloque ENTERO que vuela (2026-09-25): su variante de dibujo, bloque-Teika, roca… Ver WoodFall. */
        val wood: CellContent.Wood? = null,
    ) : GameEvent()

    /**
     * Bioma Miedoso: un bloque acorralado se asustó pero NO pudo moverse (sin hueco) → tiembla en
     * su sitio. La UI dibuja un temblor/marcas nerviosas sobre la celda (el bloque sigue visible).
     */
    data class BlockTremble(
        val position: Position
    ) : GameEvent()

    /** Liberación visual; el cuerpo físico ya está libre y la UI sigue su posición real. */
    data class PlantRescue(
        val teikaId: String,
        val teikaType: TeikaType,
        val fromX: Float,
        val fromY: Float,
        val landingX: Float,
        val landingY: Float,
        val edge: PlantEdge,
        val rootsBefore: Int,
        val durationMillis: Int,
    ) : GameEvent()

    data class PlantDrag(
        val teikaId: String,
        val teikaType: TeikaType,
        val from: Position,
        val to: Position,
        val edge: PlantEdge,
        val linesBroken: Int,
        val isInitialCapture: Boolean = false,
        val lethal: Boolean = false,
        val durationMillis: Int = 850
    ) : GameEvent()

    /**
     * Bioma Jardín: el jugador rompió una línea mientras la planta arrastraba un Teika → la planta
     * RECIBE DAÑO (aún no lo suelta del todo). La UI sacude el agarre, hace destellar el tallo y
     * salpica savia/hojas en el punto donde la enredadera sujeta al Teika ([root]). [progress] es la
     * fracción de rescate ya conseguida (linesBroken/GARDEN_RESCUE_LINES) → escala la intensidad del
     * golpe. Da el feedback de "seguí rompiendo líneas para liberarlo".
     */
    data class PlantHurt(
        val teikaId: String,
        val teikaType: TeikaType,
        val root: Position,
        val edge: PlantEdge,
        val progress: Float
    ) : GameEvent()

    /**
     * **Volcán: la lava BAJA una fila** (P-65, auditoría de eventos de bioma 2026-08-30).
     *
     * Hasta ahora el único castigo del juego que llegaba **en silencio visual**: la superficie se
     * deslizaba con un `tween(620)` limpio y ya está — sin ola, sin salpicadura, sin nada que dijera
     * «te acaba de comer una fila». `LavaOverlay` dibuja el estado (dónde está la lava); esto es el
     * MOMENTO (que acaba de moverse), que es lo que el bioma no tenía.
     *
     * [fromRow] → [toRow] en filas de rejilla; [burnedBodies] = Teikas que la marea se llevó por
     * delante, para que la ola reviente más fuerte cuando además se ha comido algo.
     */
    data class LavaSurge(
        val fromRow: Int,
        val toRow: Int,
        val burnedBodies: Int = 0
    ) : GameEvent()

    /**
     * Volcán · EL MAGMA (2026-09-23): los bloques de [cells] —la fila que ardía bajo la lava— se
     * acaban de fundir porque el jugador se quedó quieto [VolcanoMagma.HEAT_MS]. El VFX es el
     * fogonazo del temple: la madera se pone al rojo blanco y se enfría en roca con grietas de lava.
     */
    data class MagmaForge(val cells: List<Position>) : GameEvent()

    /**
     * Glaciar (rework): OLEADA DE FRÍO que congela un Teika. Una ráfaga de escarcha barre el tablero
     * y converge en el Teika de esquina-sup-izq [targetCell] (tamaño [level]×[level] celdas), que
     * queda encerrado en hielo (el bloque de hielo persistente lo dibuja FrozenBodiesOverlay durante
     * sus turnos de congelación). El barrido telegrafía la oleada "preparándose" antes del impacto.
     */
    data class GlacierColdWave(val targetCell: Position, val level: Int) : GameEvent()


    /**
     * Bioma Eléctrico: una bobina se descargó correctamente (toque en orden). La UI dibuja una
     * descarga eléctrica en [position] + número [number]. Avanza la secuencia.
     */
    data class CoilCharged(
        val position: Position,
        val number: Int
    ) : GameEvent()

    /**
     * Bioma Eléctrico: el jugador tocó una bobina fuera de orden → CORTOCIRCUITO. Todas las bobinas
     * vuelven a cargarse y la secuencia se reinicia. La UI da chispas/zumbido en [position].
     */
    data class CoilShortCircuit(
        val position: Position
    ) : GameEvent()

    /**
     * Bioma Eléctrico: TODA la secuencia se descargó (todas las bobinas encendidas). La UI dibuja
     * un rayo en cadena recorriendo [positions] en orden — esas líneas ya se pueden romper.
     */
    data class CoilDischarge(
        val positions: List<Position>
    ) : GameEvent()

    /** Bosque del Sueño / "Maite": entra la Maite y **duerme una ficha de la bandeja** durante
     *  [turns] turnos (hasta el 2026-09-07 el castigo era no poder rotar; desde P-276 el giro
     *  volvió, pero la ficha dormida tampoco gira). La UI da un aviso háptico + "z" de sueño en el panel de fichas, y la ficha dormida
     *  pierde color y cabecea. */
    data class DrowsyOnset(
        val turns: Int
    ) : GameEvent()

    /**
     * Bioma Glotón (Cocina Voraz): tras GLUTTON_OPEN_AT colocaciones ociosas (sin clear ni fusión),
     * el glotón ABRE la boca en la esquina [corner] (0=SI,1=SD,2=II,3=ID). La siguiente ociosa muerde.
     * UI: fauces + texto de counterplay.
     */
    data class GluttonOpen(
        val corner: Int
    ) : GameEvent()

    /**
     * Bioma Glotón: MORDIÓ [corner] y devoró [cells] (sin estrellas objetivo ni Teikas).
     * Esas celdas quedan unplaceable y rellenan líneas como fijas hasta digiérese.
     */
    data class GluttonBite(
        val corner: Int,
        val cells: List<Position>
    ) : GameEvent()

    /**
     * Bioma Glotón: clear o fusión a tiempo con la boca abierta en [corner] → se RETIRA
     * (cierra y se hunde). Premio visual por reaccionar antes del mordisco.
     */
    data class GluttonRetreat(
        val corner: Int
    ) : GameEvent()

    /**
     * Bioma Tóxico · EL VENENO ES UNA CAPA (2026-09-28, impacto): el veneno de [from] llega a [to].
     * Avance normal = el líquido FLUYE de la casilla a su vecina; [splash] = salpicadura lejana (una
     * gota que vuela hasta otra zona del tablero).
     */
    data class PoisonSpread(
        val from: Position,
        val to: Position,
        val delayMs: Int = 0,
        val splash: Boolean = false,
    ) : GameEvent()

    /**
     * Bioma Tóxico (consecuencia buena): una línea LAVÓ el veneno de [cell]; el bloque queda al
     * descubierto, sin romperse. [delayMs] = el telegraph de la línea.
     */
    data class PoisonWash(
        val cell: Position,
        val delayMs: Int = 0,
    ) : GameEvent()

    /**
     * Trono Real · LA ESPADA DEL REY (P-294, aviso 1): el Rey ALZA la espada y señala la línea
     * [axis]/[line] (ver [KingSword]). En el aviso 2 la hoja brilla sobre ella (lo pinta el estado).
     */
    data class KingSwordRaise(val axis: Int, val line: Int) : GameEvent()

    /**
     * Trono Real · LA ESPADA DEL REY (impacto): la espada CAE y parte la línea. [cuts] = los bloques
     * partidos, sin puntos (el estado los oculta mientras saltan y los vacía al final) y
     * [pushedTeikaIds] los cuerpos que el golpe lanza hacia arriba.
     */
    data class KingSwordStrike(
        val axis: Int,
        val line: Int,
        val cuts: List<SwordCut>,
        val pushedTeikaIds: List<String> = emptyList(),
        val durationMillis: Int = 1000
    ) : GameEvent() {
        /** **CARGA ÚTIL**: un bloque partido (con lo que hay que dibujar de él). */
        data class SwordCut(
            val cell: Position,
            val colorType: Int,
            val targetColor: TargetColor? = null,
        )
    }

    /** Trono Real · el jugador rompió una línea que cruzaba la señalada: el Rey ENVAINA la espada. */
    data class KingSwordSheathe(val axis: Int, val line: Int) : GameEvent()

    /**
     * Trono Real · LAS PIEZAS DEL REY (P-329, aviso 1): una pieza de ajedrez ([KingPiece.ROOK],
     * [KingPiece.BISHOP] o [KingPiece.KNIGHT]) entra y señala su camino [kind]/[a]/[b]. En el aviso 2
     * el camino arde (lo pinta el estado).
     */
    data class KingPieceRaise(
        val kind: Int,
        val a: Int,
        val b: Int,
        val black: Boolean,
        val fromEnd: Boolean,
    ) : GameEvent()

    /**
     * Trono Real · LAS PIEZAS DEL REY (impacto): la pieza ARRASA su camino a cámara lenta. [cuts] = los
     * bloques rotos, sin puntos (ocultos mientras vuelan sus esquirlas, vaciados al final),
     * [pushedTeikaIds] los cuerpos que salen lanzados y [blocked] si el camino queda bloqueado una
     * colocación (no lo queda si eso dejara la bandeja sin jugada).
     */
    data class KingPieceStrike(
        val kind: Int,
        val a: Int,
        val b: Int,
        val black: Boolean,
        val fromEnd: Boolean,
        val cuts: List<KingSwordStrike.SwordCut>,
        val pushedTeikaIds: List<String> = emptyList(),
        val blocked: Boolean = true,
        val durationMillis: Int = 1000,
        /** 2026-09-28: la pieza se QUEDA en su casilla de llegada (el VFX no la saca del tablero). */
        val stays: Boolean = false,
    ) : GameEvent()

    /**
     * Trono Real · la pieza se RETIRA: el jugador rompió una línea que pisaba su camino (aviso), o la
     * fila o columna de la pieza que se había quedado ([at], su casilla; `null` si aún avisaba).
     */
    data class KingPieceRetreat(
        val kind: Int,
        val a: Int,
        val b: Int,
        val black: Boolean,
        val fromEnd: Boolean,
        val at: Position? = null,
    ) : GameEvent()

    /**
     * Cielo Estelar / Evento Astral: un astro (1=Sol, 2=Luna) EMERGE desde ARRIBA y marca a su
     * víctima. Un turno de contrajuego: romper filas o fusionar lo retira ([AstroRetreat]).
     */
    data class AstroEmerge(
        val body: Int,
        val targetCells: List<Position> = emptyList()
    ) : GameEvent()

    /**
     * Cielo Estelar / Evento Astral: el jugador hizo actividad a tiempo → el astro se RETIRA
     * sin disparar (contrajuego exitoso).
     */
    data class AstroRetreat(val body: Int) : GameEvent()

    /**
     * Cielo Estelar / Evento Astral: el astro [body] (1=Sol, 2=Luna) DISPARA.
     * Sol → combustión del Teika (cuerpo se retira a mitad de la explosión).
     * Luna → madera en [hiddenWoodCells] mientras [lifts] la dibuja despegando → viaje → absorción;
     * al terminar el VFX el ViewModel vacía esas celdas de verdad.
     */
    data class AstroStrike(
        val body: Int,
        val cells: List<Position>,
        val lifts: List<AstroLift> = emptyList(),
        val durationMillis: Int = 1100
    ) : GameEvent() {
        /**
         * **CARGA ÚTIL, no un evento**: un objeto que el Sol/Luna se lleva. El grid/cuerpos ya están
         * vacíos (o ocultos); el VFX lee [lifts] para dibujar el viaje premium (levitación / combustión)
         * **fiel al bloque del tablero** (skin, cofre dorado, estrella-objetivo de color, cristal, veneno).
         * Nunca se emite ni se consume por su cuenta.
         */
        data class AstroLift(
            val cell: Position,
            /** Color de madera 0..6; irrelevante si [isTeika]. */
            val colorType: Int = 0,
            val isTeika: Boolean = false,
            val teikaLevel: Int = 1,
            /**
             * Bloque-ESTRELLA (objetivo del nivel). Si no es null, el VFX pinta cofre dorado + el PNG
             * de [targetColor] (rojo/azul/morado/rosa), igual que [com.cukiti.teikafusion.ui.CellView].
             * [isStar] se mantiene por compatibilidad (= targetColor != null).
             */
            val isStar: Boolean = false,
            val targetColor: TargetColor? = null,
            /** Cáscara de cristal (armadura) — el VFX dibuja el overlay cian si > 0. */
            val armor: Int = 0,
            /** Baba tóxica — viaja con el bloque si estaba envenenado. */
            val poisoned: Boolean = false,
            /** Centro continuo del Teika (unidades de celda); para madera = cell+0.5. */
            val cx: Float = cell.x + 0.5f,
            val cy: Float = cell.y + 0.5f
        )
    }

    /**
     * Mansión Espectral · EL ESPEJO EMBRUJADO (P-246, impacto): el espejo DESTELLA y los [phantoms]
     * —casilla → color reflejado— se materializan como bloques fantasma. [source] es la mitad
     * reflejada ([HauntedMirror.LEFT]…): el VFX barre desde el eje hacia ese lado. El estado ya los
     * tiene; el VFX dibuja el destello y el reflejo «saliendo» del espejo hasta su casilla.
     */
    data class MirrorFlash(val source: Int, val phantoms: Map<Position, Int>) : GameEvent()

    /**
     * Mansión Espectral · EL ESPEJO (consecuencia): los bloques fantasma de [cells] cumplieron sus
     * turnos y se desvanecen. El estado ya los vació; el VFX dibuja cómo se deshacen en bruma.
     */
    data class PhantomFade(val cells: List<Position>, val colors: List<Int>) : GameEvent()

    /**
     * Dojo en Sombras / CORTE NINJA: el ninja cae sobre el tablero y lo PARTE en dos por la columna
     * central — tajo de katana en vertical, las mitades se separan y quedan flotando. Mientras dura,
     * los Teikas no cruzan (muro físico) y las fichas no pisan ambas mitades. [linesToRepair] =
     * líneas (filas o columnas) que faltan para que el tablero se repare.
     */
    data class NinjaBoardSlash(
        val linesToRepair: Int
    ) : GameEvent()

    // (ERRADICADOS 2026-08-09, con el modo WALLBLOCK entero) Aquí vivían los eventos del modo:
    //  `TideAwaken`, `TideMilestoneReached`, `TideRecordBroken`, `TideClutchEscape`,
    //  `RewardRowFall`, `WellEventStarted`, `MonoRowCleared`, `MonoColumnBreak`,
    //  `WallblockGroupBreak`, `WallblockCollapse`, `WallblockBlockLaunch` e `InfinitePhaseShift`,
    //  además de los que ya estaban podados de reworks anteriores (`TideRise`, `TideFrozen`,
    //  `TideRowsRose`, `TideWallBreach`, `CeilingCrush`, `WallblockChain`, `WallblockAtticReturn`,
    //  `WallblockCascade` y las fichas especiales del Infinito). Sus VFX se fueron con ellos.

    /**
     * Dojo en Sombras / CORTE NINJA: el tablero se REPARA — las mitades vuelven a encajar y la
     * grieta se sella con un destello DORADO (kintsugi: la reparación japonesa con oro).
     * [byMega] = la reparación la provocó la irrupción de un MEGA (revienta la barrera al instante).
     */
    data class NinjaBoardRepair(
        val byMega: Boolean = false
    ) : GameEvent()

    /**
     * Jardín · LA ENREDADERA (P-324): brota en [cells] — por donde la raíz arrastró a un Teika, o desde el
     * borde [edge] si no había Teika que agarrar. El estado la pone al brotar, a mitad del VFX.
     */
    data class VineSprout(
        val cells: List<Position>,
        val edge: PlantEdge,
        val durationMillis: Int = 650,
    ) : GameEvent()

    /**
     * Dojo en Sombras · CICATRIZ DE ORO (P-323): tras sellarse la costura, la laca de oro gotea y cuaja en
     * [cells] (casillas vacías de la costura). El estado pone los bloques al cuajar.
     */
    data class KintsugiScar(val cells: List<Position>, val durationMillis: Int = 600) : GameEvent()

    // ── Habilidades de compañero (rework 2026-07-17) ───────────────────────────

    /**
     * Nieves / Estalactitas: un carámbano cae del techo sobre la columna [col] y la perfora de arriba
     * abajo. [delayMs] escalona una columna tras otra. La UI dibuja el hielo cayendo + escarcha.
     */
    data class IcicleDrop(
        val col: Int,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * Iñaki / Fichas en Llamas: las fichas de la BANDEJA se incendian al activar la pasiva. La UI
     * dibuja las llamas prendiendo SOBRE el panel de fichas (no en el tablero — ahí no pasó nada):
     * lenguas de fuego que suben desde la base del panel + chispas, dando la impresión de que fue
     * ahí donde las fichas se encendieron. [count] = nº de fichas encendidas (escala la intensidad).
     */
    data class TrayIgnite(
        val count: Int
    ) : GameEvent()

    /**
     * Rayco / Sobrecarga: una corriente/rayo une por la fuerza dos Teikas ([from]→[to]) del mismo
     * [tier]. La UI dibuja el arco eléctrico serpenteante entre ambos justo antes de que se fusionen.
     */
    data class OverloadBeam(
        val from: Position,
        val to: Position,
        val tier: Int
    ) : GameEvent()

    /**
     * Iñaki / Cruz de Fuego: el fuego prende la fila y la columna centrales que se cruzan en [center].
     * La UI dibuja las dos lenguas de fuego propagándose desde el centro hacia los bordes.
     */
    data class FireCross(
        val center: Position
    ) : GameEvent()



    /**
     * Flora / Cañón Floral (rework it.54): la CEREMONIA de la flor. Una flor CERRADA nace en el
     * CENTRO del tablero y se abre lenta y dramática ([openMs]); el Teika de MAYOR tier salta
     * sobre ella encogiéndose en vuelo ([jumpMs], desde ([fromX],[fromY]) en celdas); la flor se
     * cierra con él dentro y se ENTIERRA en el tablero ([closeMs], hundimiento + tierra). Después
     * aparece el capullo-cañón en el borde del panel de fichas (FloraAimLayer). El cuerpo físico
     * se retira al empezar el salto — este evento dibuja TODO el viaje.
     */
    data class FloraCeremony(
        val teikaType: TeikaType,
        val fromX: Float,
        val fromY: Float,
        val openMs: Int = 1300,
        val jumpMs: Int = 520,
        val closeMs: Int = 1000
    ) : GameEvent()

    /**
     * Un bloque de madera CAE por gravedad de [from] a [to] (misma columna): acelera (ease-in),
     * aterriza con squash y se asienta. [durationMs] escala con la distancia (más caída = más
     * tiempo, como la gravedad de los Teikas). Usado por el desplome y las filas-bonus de Maite.
     * La celda destino viaja oculta (hiddenWoodCells) hasta que el bloque encaja.
     *
     * Reloj (ms absolutos desde que el VFX se compone): si [appearAtMs] ≥ 0 el bloque se
     * MATERIALIZA en [from] en ese instante (pop + flash) y flota ahí; en [delayMs] arranca la
     * caída de [durationMs]. Con [appearAtMs] = -1 es visible desde el frame 0 (desplome).
     *
     * [slide] = true (Timoteo / Puzzle Deslizante): el bloque NO cae — se DESLIZA como una loseta
     * (interpola X e Y, ease-in-out, sin squash ni polvo; la estrella viaja rígida con el bloque).
     */
    data class WoodFall(
        val from: Position,
        val to: Position,
        val colorType: Int,
        val targetColor: TargetColor? = null,
        val poisoned: Boolean = false,
        /** Bloque de CRISTAL (armor > 0): el vuelo dibuja su carcasa, como la celda en reposo. */
        val armor: Int = 0,
        val delayMs: Int = 0,
        val durationMs: Int = 260,
        val appearAtMs: Int = -1,
        val slide: Boolean = false,
        /** Portal footprints: absorb into the source centre and emerge at the destination. */
        val portalOrigin: Position? = null,
        val portalDestination: Position? = null,
        /**
         * El bloque ENTERO que cae o se desliza (2026-09-25, playtest: «los bloques pierden su diseño
         * al moverse»). Con él el vuelo dibuja lo mismo que la casilla en reposo —la variante de su
         * cara, la cápsula del bloque-Teika, la roca, el marco del testarudo— y no solo color, estrella,
         * veneno y cristal. `null` en los eventos que no vienen de un bloque real.
         */
        val wood: CellContent.Wood? = null,
        val orbitCenter: Position? = null,
        val orbitClockwise: Boolean = true,
    ) : GameEvent()

    // (MaiteBonusRow ERRADICADO en playtest r3: la fila-bonus rígida que caía entera se reemplazó
    //  por bloques WoodFall independientes que caen por su columna — ver rama "dormilona".)

    /**
     * Reynaldo / Parto Real: del Teika en [position] sale un Teika hijo de nivel [childLevel]
     * empujado hacia fuera. La UI dibuja el destello dorado de coronación + las chispas del hijo
     * emergiendo. [delayMs] escalona los partos cuando varios Teikas paren a la vez.
     */
    /**
     * Reynaldo / **PARTO REAL** — la ceremonia (rework 2026-08-27).
     *
     * Los cuatro tiempos viajan en el evento porque el ViewModel los necesita: el hijo **no existe
     * como cuerpo físico hasta que la extrusión termina**. Antes nacía en el primer frame y la
     * animación se dibujaba encima de un Teika que ya estaba ahí, así que el parto no se veía —
     * aparecía. El estado nunca cambia antes que su animación.
     *
     * [dir] es hacia dónde sale el hijo (−1 izquierda, +1 derecha), para que la extrusión apunte al
     * mismo sitio por el que la física lo va a empujar.
     */
    data class TeikaBirth(
        val position: Position,
        val childLevel: Int,
        val delayMs: Int = 0,
        val dir: Float = 1f,
        val crownMs: Int = 420,
        val ringMs: Int = 260,
        val extrudeMs: Int = 620,
        val detachMs: Int = 300
    ) : GameEvent() {
        /** Cuándo el hijo debe existir de verdad: al terminar la extrusión. */
        val spawnAtMs: Int get() = delayMs + crownMs + ringMs + extrudeMs
        val totalMs: Int get() = delayMs + crownMs + ringMs + extrudeMs + detachMs
    }

    /**
     * Toño / Miasma Venenoso: un miasma tóxico se esparce desde [center] cubriendo el tablero y se
     * concentra sobre las [cells] marcadas (la madera que va a corroerse) antes de que TODO reviente
     * casi a la vez. La UI dibuja la niebla verde creciendo con esporas ácidas remolineando.
     */
    data class PoisonMiasma(
        val cells: List<Position>,
        val center: Position
    ) : GameEvent()

    // (PODADO 2026-07-31, bloque 21) Aquí vivía `Dematerialize`, el «Desvanecer» de Morti. La
    //  habilidad de Morti pasó a ser el TEMBLEQUE en los reworks de las its. 52-54 y desde entonces
    //  el evento no tenía un solo productor: su VFX (`DematerializeVFX`) y su colector en GameBoard
    //  seguían montados para nada. Mismo fósil que los 4 que podó la it. 63.


    /* (P-44, 2026-08-01) Aquí vivía `ScorePopup`: el "+200"/"+4000" flotando en el punto de una
     * fusión, con su `ScorePopupVFX`. Estaba emitido **solo en el modo infinito**… que desde el
     * 2026-07-25 no tiene Teikas, así que no podía fusionarse nada y el evento se quedó sin un solo
     * productor (lo cazó `GameEventContractTest`). Si se quiere el número en el sitio de la fusión
     * también en NIVELES —donde sí hay fusiones—, es una decisión de diseño, no una restauración:
     * hoy allí la cifra vuela al marcador con [FloatingScore] y solo puede haber una en vuelo. */

    /* (ERRADICADO 2026-08-09, con el modo) Aquí vivía `ScoreMilestone`: «acabas de dejar atrás a
     * otro jugador del ranking por PROFUNDIDAD». Era la victoria social de un modo sin final, y sin
     * ese modo no hay profundidad que comparar. */

    /**
     * **LA BROCHA** (P-70): la ficha acaba de repintar de su color estas filas y columnas.
     *
     * Lo dispara `placePiece` §2.6 y lo consume **`BrushSweepVFX`, y solo él** (contrato de un solo
     * consumidor: un segundo `consumeEvent` sobre el mismo tipo corta la animación a un fotograma —
     * es el bug recurrente del proyecto).
     */
    data class BrushSweep(
        val rows: Set<Int>,
        val cols: Set<Int>,
        val colorType: Int
    ) : GameEvent()

    /**
     * **LÍNEA PURA** (P-69): esta línea era de un solo color y por eso engendra un tier por encima.
     * [cell] es su origen y [alongX] su orientación. Lo consume **`PureLineVFX`, y solo él**.
     */
    data class PureLine(
        val cell: Position,
        val alongX: Boolean
    ) : GameEvent()

    /**
     * **CELDA CALIENTE** (P-78): una línea acabó de cruzar esta celda y se llevó su premio.
     * Lo consume **`HotCellPopVFX`, y solo él**.
     */
    data class HotCellPop(val cell: Position) : GameEvent()

    /**
     * **LA CAMALEÓN** (P-72): la ficha acaba de adoptar [colorType] al aterrizar sobre [cells].
     * Lo consume **`ChameleonAdoptVFX`, y solo él**.
     */
    /**
     * **BLOQUE TESTARUDO** (P-71) que AGUANTA: la línea no traía bastantes de su color.
     * [have] / [need] es lo que le faltaba. Lo consume **`StubbornVFX`, y solo él**.
     */
    /**
     * **LA FICHA QUE CRECE** (P-77): la ficha [slot] de la bandeja acaba de ganar un bloque y ahora
     * mide [size]. Lo consume **`GrowingPieceVFX`, y solo él**.
     */
    data class PieceGrew(val slot: Int, val size: Int) : GameEvent()

    /* (RETIRADO 2026-09-07) `TrayPieceSwapped`: el cue del reparador de bandeja (P-94). Se fue con
     * su productor —`replaceUnplaceableTrayPieces`, que cambiaba la ficha sin colocación posible por
     * otra que sí cabía—, retirado por decisión del usuario para que quedarse sin jugada vuelva a
     * ser una derrota. Un evento sin productor es un FÓSIL y `GameEventContractTest` lo caza, así
     * que la declaración se borra con él. */

    data class StubbornResist(
        val cell: Position,
        val colorType: Int,
        val have: Int,
        val need: Int,
        val delayMs: Int = 0
    ) : GameEvent()

    /** **BLOQUE TESTARUDO** (P-71) que por fin CEDE. Lo consume **`StubbornVFX`, y solo él**. */
    data class StubbornBreak(
        val cell: Position,
        val colorType: Int,
        val delayMs: Int = 0
    ) : GameEvent()

    data class ChameleonAdopt(
        val cells: Set<Position>,
        val colorType: Int
    ) : GameEvent()

    /**
     * **P-147 · LA FICHA ESPEJO**: las celdas donde acaba de caer el reflejo de la ficha colocada.
     *
     * El evento existe porque el reflejo aparece **lejos del dedo**: el jugador está mirando dónde ha
     * soltado la ficha y la mitad de su jugada ocurre al otro lado del tablero. Sin un destello que
     * lo lleve hasta allí, la mecánica se descubre tarde y mal — es el mismo motivo por el que la
     * corriente tiene su barrido aunque no mueva ningún bloque.
     */
    data class MirrorStamp(
        val cells: Set<Position>
    ) : GameEvent()

    /**
     * **P-232 · FICHAS QUE SE FUNDEN**: dos gemelas de la bandeja acaban de fundirse en la ficha
     * [fusedId]. El VFX la hace latir en su cuadro: la bandeja cambia sola, pero que ha sido una
     * FUSIÓN —y no un reparto— hay que decirlo.
     */
    data class PiecesFused(
        val fusedId: String,
        val size: Int
    ) : GameEvent()

    /**
     * **P-241 · LA BRÚJULA GIRA**: la gravedad de los Teikas pasa de [from] a [to] ([Compass.DOWN]…).
     */
    data class CompassTurn(
        val from: Int,
        val to: Int
    ) : GameEvent()

    /**
     * **P-234 · REACCIÓN EN CADENA**: los barriles que estallan, oleada a oleada ([waves]: la primera
     * son los que rompió la línea, cada siguiente los que alcanzó la anterior) y todo lo que se
     * llevan ([area]). El VFX estalla por oleadas para que se VEA la cadena. Llega con el [delayMs]
     * del haz: la línea y su primer estallido son la misma jugada.
     */
    data class BarrelBlast(
        val waves: List<Set<Position>>,
        val area: Set<Position>,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * **P-235 · EL HUEVO**: una línea lo ha cruzado y lleva ya [cracks] de [needed] grietas. Llega
     * con el haz de la línea que lo raja.
     */
    data class EggCrack(
        val cell: Position,
        val cracks: Int,
        val needed: Int,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * **P-235 · EL HUEVO ECLOSIONA** en [cell] y sale un [type]; [next] es dónde aparece el siguiente
     * (o `null` si no hay sitio). El cuerpo lo dibuja la física; esto es la cáscara que se abre.
     */
    data class EggHatch(
        val cell: Position,
        val type: TeikaType,
        val next: Position?,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * **P-244 · LA VIDRIERA**: una línea acaba de agrietar estos vidrios. Llega con el [delayMs] del
     * haz, porque es la MISMA línea la que los raja: tienen que verse a la vez.
     */
    data class GlassCrack(
        val cells: Set<Position>,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * **P-244 · LA VIDRIERA ESTALLA**: [glass] eran sus vidrios, [cleared] la madera que se lleva la
     * cruz de cada uno y [next] los vidrios de la vidriera que nace. El VFX vuela de los viejos a
     * los nuevos para que el jugador vea ADÓNDE se ha ido la vidriera, no solo que ha desaparecido.
     */
    data class GlassShatter(
        val glass: Set<Position>,
        val cleared: Set<Position>,
        val next: Set<Position>,
        val delayMs: Int = 0
    ) : GameEvent()

    /** **P-240 · EL IMÁN**: acaba de colocarse en [position]. La onda enseña su alcance ([range]). */
    data class MagnetPlaced(
        val position: Position,
        val range: Float
    ) : GameEvent()

    /**
     * **P-240 · EL IMÁN SE ROMPE** en [position]: se le acabaron las colocaciones ([expired]) o su
     * línea se lo llevó antes de tiempo. En los dos casos los cuerpos quedan sueltos y caen.
     */
    data class MagnetBreak(
        val position: Position,
        val expired: Boolean
    ) : GameEvent()

    /**
     * **P-239 · TEIKAS DE PLOMO**: un cuerpo de [teikaType] ha caído desde lo bastante alto para
     * aplastar el bloque de [position]. La rotura del bloque la dibuja [TeikaPushBreak]; esto es el
     * GOLPE: polvo, cráter y el cuerpo que se hunde un instante.
     */
    data class LeadCrush(
        val position: Position,
        val teikaType: TeikaType
    ) : GameEvent()

    /**
     * **P-151 · EL ECO**: una línea marcada acaba de cobrarse doble.
     *
     * [isRow] y [lineIndex] dicen cuál, para que el destello salga sobre la línea que de verdad pagó.
     * Sin este acuse el eco sería invisible: los puntos suben en el marcador y nada dice por qué —
     * y una recompensa que no se ve no enseña a encadenar, que es exactamente lo que la regla pide.
     */
    /**
     * **P-155 · EL NACIMIENTO TARDÍO**: una línea acaba de ganarse un Teika que llegará en
     * [turnsLeft] turnos.
     *
     * Sin este aviso la regla se leería como un fallo: el jugador rompe su fila, no nace nada, y dos
     * turnos después le aparece un cuerpo sin motivo aparente. El anuncio es lo que convierte la
     * espera en una decisión — hay que dejarle sitio a algo que todavía no está.
     */
    /**
     * **P-157a · LA FILA PREMIADA**: la fila marcada acaba de pagar el triple.
     *
     * Lleva el mismo acuse que el eco y por el mismo motivo: sin él, el marcador sube y nada dice
     * por qué. La diferencia es que esta marca estaba ahí desde el turno 1, así que el destello no
     * enseña una regla nueva — confirma que el plan salió.
     */
    /**
     * **P-233 · EL MOLDE ESTALLA**: la ficha encajó en [cells]; [cleared] es lo que se lleva el
     * estallido y [next] las casillas del molde siguiente (vacío si no cupo en ningún sitio).
     */
    data class MoldStamp(
        val cells: Set<Position>,
        val cleared: Set<Position>,
        val next: Set<Position>
    ) : GameEvent()

    /** **P-233**: el molde estaba tapado y se ha mudado de [from] a [to]. */
    data class MoldMoved(
        val from: Set<Position>,
        val to: Set<Position>
    ) : GameEvent()

    /**
     * **P-238 · LAS POMPAS**: la pompa del cuerpo [teikaId] ha recibido un golpe en ([atX], [atY]);
     * [popped] si ha reventado (el cuerpo cae) o si solo se ha agrietado (le queda otra).
     */
    data class BubblePop(
        val teikaId: String,
        val atX: Float,
        val atY: Float,
        val popped: Boolean,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * **P-243 · LOS PORTALES**: un cuerpo de [teikaType] entró por ([fromX], [fromY]) y salió por
     * ([toX], [toY]), en casillas. El paso en sí ya lo hizo la física; esto es el remolino.
     */
    data class PortalHop(
        val teikaType: TeikaType,
        val fromX: Float,
        val fromY: Float,
        val toX: Float,
        val toY: Float
    ) : GameEvent()

    /** **P-243**: los portales se han mudado (filas de arriba de cada uno, antes y ahora). */
    data class PortalsMoved(
        val fromLeft: Int,
        val fromRight: Int,
        val toLeft: Int,
        val toRight: Int
    ) : GameEvent()

    /**
     * **P-242 · LA PIÑATA**: una línea le ha dado. Lleva [hits] de [needed] golpes. Llega con el haz.
     */
    data class PinataHit(
        val cell: Position,
        val hits: Int,
        val needed: Int,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * **P-242 · LA PIÑATA REVIENTA** en [cell]: los T1 caen en [drops] (los cuerpos los dibuja la
     * física; esto es la piñata abriéndose y la lluvia de confites que los acompaña hasta su sitio) y
     * la siguiente cuelga en [next].
     */
    data class PinataBurst(
        val cell: Position,
        val drops: List<Position>,
        val next: Position?,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * **EL REBOTE** (regla de la casa, 2026-09-05): la línea que acaba de reventar ha empujado a los
     * cuerpos, y esto es la onda que lo cuenta.
     *
     * Llega **con el impacto**, no con la detección: sale con el mismo `delayMs` que el resto del
     * pase (`LINE_CLEAR_TELEGRAPH_MS`), porque el empujón físico se aplica ahí y una onda que se
     * adelanta al golpe rompe la relación causa-efecto de toda la ceremonia.
     *
     * [alongX] dice si la línea era una fila (la onda se abre en vertical) o una columna, y
     * [lineIndex] cuál, para dibujarla encima de la que de verdad empujó. [strength] es 0..1 (el dial
     * de capítulo ya normalizado) y solo gradúa cuánto se abre y cuánto brilla.
     */
    data class LineRecoilWave(
        val alongX: Boolean,
        val lineIndex: Int,
        val strength: Float,
        val delayMs: Int = 0
    ) : GameEvent()

    /**
     * **P-237 · EL REBOTE, en cada cuerpo**: dónde estaba cada Teika empujado ([at], centro en
     * casillas), hacia dónde salió ([dirX]/[dirY], unitario) y con qué fuerza relativa ([power]
     * 0..1). La onda de [LineRecoilWave] dice que la línea empujó; esto dice A QUIÉN — sin ello, un
     * cuerpo que sale disparado a tres casillas de la línea se lee como física rara.
     */
    data class RecoilPush(
        val pushes: List<Push>
    ) : GameEvent() {
        data class Push(val id: String, val atX: Float, val atY: Float, val dirX: Float, val dirY: Float, val power: Float)
    }

    /**
     * **P-236 · LA IMPLOSIÓN**: las líneas [rows]/[cols] recién rotas han aspirado la madera de
     * [pulls] (origen → destino, una casilla). Los bloques los arrastra `WoodFall` en modo deslizar;
     * esto es la succión que se ve converger sobre la línea.
     */
    data class LineImplosion(
        val rows: Set<Int>,
        val cols: Set<Int>,
        val pulls: List<Pair<Position, Position>>
    ) : GameEvent()

    // (RETIRADO 2026-09-26) `GoalDelivery` — la entrega de LA PORTERÍA (P-81), borrada entera.

    /**
     * **P-79 · LA CORRIENTE**: el tablero se desliza una columna hacia [direction] (`+1` derecha,
     * `-1` izquierda).
     *
     * Un solo evento para las dos fases del contrato. Con [warning] = true es el **aviso**: la
     * embestida llega en la colocación siguiente y la corriente se insinúa, floja, cruzando el
     * tablero. Con `false` es la **embestida**: el barrido va a fondo y coincide con los `WoodFall`
     * en modo `slide` que arrastran cada bloque. La consecuencia —el tablero movido— la enseña el
     * propio tablero.
     *
     * Hace falta como evento propio y no basta con los arrastres: una fila atascada contra una roca
     * no mueve nada, y sin este barrido la corriente sería invisible justo en el turno en que más
     * castiga.
     */
    data class BoardCurrentFlow(
        val direction: Int,
        val warning: Boolean,
        val durationMs: Int = 620,
        /**
         * ¿Empuja en VERTICAL? (2026-09-03) La corriente es una familia de cuatro reglas de la casa
         * y el barrido tiene que ir por el eje que de verdad mueve los bloques: una estela horizontal
         * sobre una corriente que baja sería el juego mintiendo sobre su propia mecánica.
         */
        val vertical: Boolean = false,
        /**
         * **P-231 · LA NORIA**: el giro es del anillo del borde, no del tablero. El barrido tiene que
         * dibujarse dando la vuelta al marco — una estela que cruzara el tablero enseñaría que se
         * mueve lo que no se mueve. (Sustituye a `lanes`, la fila de LA CINTA, retirada el 2026-09-22.)
         */
        val ring: Boolean = false
    ) : GameEvent()

}

