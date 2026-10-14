package com.cukiti.teikafusion.model

/**
 * **EL RELOJ DE CADA BIOMA** — fuente única de cuántos turnos ociosos aguanta el jugador antes de
 * que la mecánica ambiental le castigue.
 *
 * Los biomas con reloj ejecutan al tercer turno ocioso. Pasaje Encantado es una excepción
 * explícita desde 2026-09-16: intenta mover un bloque en cada colocación, desde la primera.
 * Cristal y Tormenta responden a acciones, sin contador de ociosidad.
 *
 * ## Lo que REINICIA la cuenta: romper líneas, y solo eso
 *
 * El umbral no significaba nada mientras cualquier fusión lo pusiera a cero, porque los Teikas son
 * **cuerpos físicos que se funden solos**: dos T1 rodando entre turnos se tocan y fusionan sin que
 * el jugador haga nada, y cada una de esas fusiones espontáneas regalaba un turno al reloj. Con
 * varios cuerpos vivos el contador podía no llegar nunca a 3, y biomas enteros no se veían jamás.
 *
 * Desde el 2026-09-05 (decisión del usuario) **solo romper una línea calma el bioma** — que ya era
 * la regla del Galáctico y del Miedoso, así que no es vocabulario nuevo, es extenderlo.
 *
 * Y desde el 2026-09-23 (también del usuario) **el reloj se CONGELA** ([BiomeClock]): una línea rota en
 * un turno de aviso ya no lo reinicia — lo deja donde estaba—, y solo romperla en el turno de
 * ejecutar cancela la mecánica. El título de abajo sigue siendo verdad: romper líneas es lo único
 * que calma; lo que cambió es CUÁNDO. Se
 * implementa en `GameViewModel`: la fusión ya no toca `activityWeightSinceTick`, que es de donde
 * sale el `boardActive` que leen todas las secciones de `applyBiomeTick`.
 *
 * ## Sin escalado por nivel
 *
 * Los turnos son **los mismos en todos los niveles del capítulo**. Antes se modulaban por el acto
 * del capítulo ([ChapterArc], ±1) y por el tipo de nivel ([LevelDirector]), y el resultado era que
 * el mismo bioma castigaba en el turno 5 o en el 2 según dónde estuvieras: imposible de aprender.
 * La progresión de un capítulo vive ahora en el TABLERO —densidad inicial, bandeja, objetivo—, no en
 * el reloj.
 *
 * ⚠️ **Nadie debe escribir un umbral de bioma a mano.** Se pide con [idleBase] (lo que compara el
 * contador de la mecánica) o se consulta con [executeTurn] (el turno en que de verdad duele).
 *
 * ## Y las TRES FASES
 *
 * Un bioma se disfruta si tiene **telegrafía → impacto → consecuencia**. Cada uno declara sus dos
 * señales en [phasesFor] y `BiomePhaseContractTest` comprueba contra el código fuente que existen y
 * que la UI las dibuja. Un bioma nuevo que no las declare no pasa la prueba.
 */
object BiomeRhythm {

    /** Turno ocioso en que CAE el castigo, para la regla de la casa. */
    const val EJECUTA = 3

    /**
     * Turnos ociosos que compara el CONTADOR de la mecánica.
     *
     * Ojo: no siempre coincide con [executeTurn]. Dos biomas tienen el castigo en DOS tiempos —el
     * contador arma un aviso concreto y el golpe llega al turno siguiente—, así que su base es
     * `EJECUTA - 1`. Para saber cuándo duele de verdad, usa [executeTurn].
     *
     * 0 = el bioma no tiene reloj de ociosidad (castiga por lo que HACES, no por lo que dejas de hacer).
     */
    fun idleBase(biome: BiomeType): Int = when (biome) {
        // ── Un solo tiempo: el contador llega y el castigo cae ──
        BiomeType.SLEEP -> EJECUTA        // entra la Maite (te duerme una ficha de la bandeja)
        BiomeType.VOLCANO -> EJECUTA      // baja la primera fila de lava
        BiomeType.NINJA -> EJECUTA        // la katana parte el tablero
        BiomeType.GLACIER -> EJECUTA      // la oleada congela un Teika
        // El Tóxico NO sigue este contador: el veneno se EXTIENDE en cada colocación sin líneas y salpica a
        // las `ToxicBiomeRules.SPLASH_AFTER_TURNS` sin avanzar (2026-09-28, decisión del usuario). Esta
        // entrada solo lo cuenta entre los que tienen reloj para los tests de contrato.
        BiomeType.TOXIC -> EJECUTA
        BiomeType.STAR -> EJECUTA         // el astro baja y dispara
        // Estos dos avisan con un evento propio en EJECUTA-1 (el espejo se empaña / la espada brilla) pero golpean aquí.
        BiomeType.GHOST -> EJECUTA
        BiomeType.KING -> EJECUTA

        // Los meteoritos (P-294): la mira recorre, fija el cráter y el meteorito CAE aquí, como el resto.
        BiomeType.GALACTIC -> EJECUTA
        // El Glotón también golpea en EJECUTA: su contador ARMA un turno antes (la boca) y el golpe
        // cae al siguiente, así que su base es EJECUTA-1 para acabar en el MISMO tercero.
        BiomeType.GLUTTON -> EJECUTA - 1  // abre la boca aquí, MUERDE al siguiente

        // Las dos que tenían compás propio, alineadas con la casa el 2026-09-05 (ver el KDoc).
        BiomeType.FEAR -> 1               // activo desde la primera colocación, incluso tras limpiar
        BiomeType.GARDEN -> EJECUTA       // la raíz agarra aquí; el arrastre hasta el borde es aparte

        // ── Sin reloj: reaccionan a lo que el jugador HACE ──
        BiomeType.CRYSTAL -> 0            // la armadura reacciona a tus limpiezas
        BiomeType.STORM -> 0              // las bobinas nacen al COMPLETAR una fila
        BiomeType.NONE -> 0
    }

    /**
     * Turno ocioso en que el jugador **recibe el golpe**, contando colocaciones desde el principio de
     * la partida sin romper ninguna línea. Es el número que importa para balancear.
     */
    fun executeTurn(biome: BiomeType): Int = when (biome) {
        BiomeType.GLUTTON -> idleBase(biome) + 1
        else -> idleBase(biome)
    }

    /** Turnos de AVISO antes del golpe. 0 si el bioma no tiene reloj. */
    fun warningTurns(biome: BiomeType): Int =
        if (idleBase(biome) == 0) 0 else executeTurn(biome) - 1

    /**
     * Cuánto DURA el castigo, en turnos, para las mecánicas que no son instantáneas. 0 = instantáneo
     * o no aplica. Fijo en todo el capítulo, igual que el reloj.
     */
    fun punishmentTurns(biome: BiomeType): Int = when (biome) {
        // Turnos que la Maite tiene dormida una ficha. 2 → 1 el 2026-09-26 (P-312, decisión del usuario:
        // el bot dejaba el Sueño en el 67 % con un objetivo del 83 %).
        // 1 → 2 el 2026-09-27 (decisión del usuario: todo castigo dura al menos dos turnos).
        BiomeType.SLEEP -> 2
        // P-359 (2026-09-28): 3 → 2. Con la ola cada 3 colocaciones y 3 turnos de hielo, los Teikas no se
        // descongelaban NUNCA: no había fusiones ni MEGA (bot: Glaciar 19 % con objetivo 69 %).
        BiomeType.GLACIER -> 2    // turnos congelado (P-254: 2 → 1; 2026-09-27: 1 → 2; P-352: 2 → 3; P-359: 3 → 2)
        BiomeType.NINJA -> 4      // colocaciones hasta que la grieta se cierra sola (P-254: 4 → 3; P-323: 3 → 4; P-352: 4 → 5; P-359: 5 → 4)
        else -> 0
    }

    /** Las dos señales visuales de cada bioma. Vigiladas por `BiomePhaseContractTest`. */
    fun phasesFor(biome: BiomeType): BiomePhases? = when (biome) {
        // La cáscara de vidrio se VE sobre el bloque desde que nace: ese es el aviso permanente.
        BiomeType.CRYSTAL -> BiomePhases("CrystalOverlay", "CrystalCrack")
        // Párpados pesados en el tablero desde el primer turno ocioso.
        BiomeType.SLEEP -> BiomePhases("DrowsyDroopOverlay", "DrowsyOnset")
        // Huida desde el primer turno; el temblor puede acompañar otros sustos visuales.
        BiomeType.FEAR -> BiomePhases("BlockTremble", "BlockFlee")
        // La raíz asoma por el borde antes de llevarse al Teika.
        BiomeType.GARDEN -> BiomePhases("PlantAbductionOverlay", "PlantDrag")
        // La bobina se carga a la vista; si no la descargas, cortocircuita.
        BiomeType.STORM -> BiomePhases("CoilCharged", "CoilShortCircuit")
        // La cresta ARDE dos turnos antes de que la lava se coma la fila.
        BiomeType.VOLCANO -> BiomePhases("LavaOverlay", "LavaSurge")
        // La katana asoma y crece con cada turno ocioso.
        BiomeType.NINJA -> BiomePhases("NinjaCutOverlay", "NinjaBoardSlash")
        // La escarcha repta desde los bordes.
        BiomeType.GLACIER -> BiomePhases("GlacierThreatVignette", "GlacierColdWave")
        // El espejo se empaña y enseña su reflejo antes de destellar (P-246).
        BiomeType.GHOST -> BiomePhases("MirrorOverlay", "MirrorFlash")
        // La boca se abre en un rincón; el mordisco es el turno siguiente.
        BiomeType.GLUTTON -> BiomePhases("GluttonOpen", "GluttonBite")
        // La lengua de líquido asoma hacia los bloques que el veneno alcanzará; luego FLUYE hasta ellos.
        BiomeType.TOXIC -> BiomePhases("ToxicLiquidLayer", "PoisonSpread")
        // El astro EMERGE y marca a su presa antes de disparar.
        BiomeType.STAR -> BiomePhases("AstroEmerge", "AstroStrike")
        // El Rey alza la espada y la hoja brilla sobre la línea antes de caer (P-294).
        BiomeType.KING -> BiomePhases("KingSwordLayer", "KingSwordStrike")
        // El meteorito asoma a lo lejos y marca su cráter antes del impacto (P-294).
        BiomeType.GALACTIC -> BiomePhases("MeteorLayer", "MeteorImpact")

        BiomeType.NONE -> null
    }

    /**
     * **¿ESTE EVENTO ES EL IMPACTO DE ESTE BIOMA?** (P-176, 2026-09-04.)
     *
     * Lo mismo que dice [phasesFor] con una cadena, pero preguntado con el tipo. Hasta hoy quien lo
     * necesitaba —`MechanicCapsule.biomeJustStruck`— lo resolvía comparando
     * `evento.javaClass.simpleName` contra esa cadena, y eso **es un bug en release**: R8 renombra la
     * clase y la comparación deja de ser cierta para siempre. Estuvo así meses, y el precio fueron
     * las ocho cápsulas que explican el castigo de cada bioma: salían en el móvil de desarrollo y no
     * salían en el de nadie más (ver la 45ª pasada de MEJORAS.md).
     *
     * Un `is` no se puede renombrar: lo comprueba el compilador y sobrevive a cualquier ofuscación.
     * La cadena de [phasesFor] se queda porque es el CENSO —lo que `BiomePhaseContractTest` cruza
     * contra el código para que ningún bioma se quede sin telegrafía ni sin impacto—, y que las dos
     * digan lo mismo lo vigila ese mismo test.
     */
    fun isImpact(biome: BiomeType, event: GameEvent): Boolean = when (biome) {
        BiomeType.CRYSTAL -> event is GameEvent.CrystalCrack
        BiomeType.SLEEP -> event is GameEvent.DrowsyOnset
        BiomeType.FEAR -> event is GameEvent.BlockFlee
        BiomeType.GARDEN -> event is GameEvent.PlantDrag
        BiomeType.STORM -> event is GameEvent.CoilShortCircuit
        BiomeType.VOLCANO -> event is GameEvent.LavaSurge
        BiomeType.NINJA -> event is GameEvent.NinjaBoardSlash
        BiomeType.GLACIER -> event is GameEvent.GlacierColdWave
        BiomeType.GHOST -> event is GameEvent.MirrorFlash
        BiomeType.GLUTTON -> event is GameEvent.GluttonBite
        BiomeType.TOXIC -> event is GameEvent.PoisonSpread
        BiomeType.STAR -> event is GameEvent.AstroStrike
        // P-329: el Rey alterna espada y pieza de ajedrez en el mismo reloj; las dos son su golpe.
        BiomeType.KING -> event is GameEvent.KingSwordStrike || event is GameEvent.KingPieceStrike
        BiomeType.GALACTIC -> event is GameEvent.MeteorImpact
        BiomeType.NONE -> false
    }
}

/**
 * Las dos señales visuales obligatorias de un bioma.
 *
 * [telegraph] e [impact] son **nombres de símbolos reales** del código: o un `GameEvent` (y entonces
 * su VFX lo consume) o un composable de overlay dirigido por estado. `BiomePhaseContractTest` los
 * busca en `GameEvent.kt` y en `ui/`, así que no pueden quedarse en buenas intenciones.
 */
data class BiomePhases(
    /** Qué ve el jugador ANTES del castigo, con tiempo de reaccionar. */
    val telegraph: String,
    /** Qué ve en el INSTANTE del castigo. */
    val impact: String
)
