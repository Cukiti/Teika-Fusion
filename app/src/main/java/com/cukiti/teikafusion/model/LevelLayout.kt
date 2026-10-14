package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/** Una celda pre-colocada al inicio de un nivel. */
@Serializable
data class LevelCell(
    val x: Int,
    val y: Int,
    val targetColor: TargetColor? = null
)

/**
 * Los cuatro sabores de nivel (2026-07-31b, tercer frente del diagnóstico «todos los niveles se
 * sienten iguales»).
 *
 * Hasta hoy el juego tenía **un solo tipo de objetivo**: `LevelObjective` era literalmente
 * `Map<TargetColor,Int>` y los 293 niveles pedían lo mismo —recoge N estrellas— con el único cambio
 * de cuántas. Repetir la misma tarea más veces no es progresión, y era la causa más profunda de las
 * tres: por debajo del bioma, de la figura y de la mecánica, **la pregunta que el nivel te hace
 * nunca cambiaba**.
 */
enum class LevelGoal {
    /** Recoge N estrellas de cada color (el de siempre). */
    STARS,
    /** Rompe N líneas: pone el foco en la mitad Block Blast. */
    LINES,
    /** Deja el tablero con como mucho N bloques: hay que VACIAR, no farmear. */
    CLEAR,
    /**
     * **OBSOLETO (2026-07-31c).** «Aguanta N colocaciones» dejó de tener sentido en cuanto los
     * TURNOS pasaron a ser el recurso escaso ([LevelBudget]): pedía justo lo contrario que el nuevo
     * límite de movimientos. Ningún nivel lo genera ya.
     *
     * **No se borra del enum a propósito**: viaja dentro del `GameState` serializado y quitarlo
     * reventaría la carga de cualquier partida guardada con él (mismo motivo que
     * `GameStatus.CHESS`). El motor lo sigue tratando por si aparece en un save viejo.
     */
    SURVIVE
}

/**
 * Lo que el jugador debe cumplir para completar el nivel.
 *
 * **Compatible con saves antiguos a propósito**: en vez de convertir esto en una jerarquía sellada
 * —que habría cambiado la forma serializada y **invalidado todas las partidas guardadas**— se añaden
 * campos con valor por defecto. Un save viejo deserializa con `goal = STARS` y `amount = 0`, o sea
 * exactamente el comportamiento anterior.
 *
 * @param required estrellas por color. Solo manda con [LevelGoal.STARS]; en el resto va vacío y las
 *   estrellas del tablero pasan a ser puntuación y color, no la condición de victoria.
 * @param amount   líneas a romper · bloques máximos que pueden quedar · colocaciones a aguantar.
 */
@Serializable
data class LevelObjective(
    val required: Map<TargetColor, Int>,
    val goal: LevelGoal = LevelGoal.STARS,
    val amount: Int = 0
) {
    val total: Int get() = required.values.sum()
}

/**
 * Variedad cosmética por nivel (Fase D, estilo Candy Crush): cada nivel pinta sus bloques
 * PRE-COLOCADOS con un PATRÓN de colores distinto (sólido, rayas, tablero, diagonales, arcoíris).
 * Es puramente visual — `colorType` solo elige la paleta de render; las líneas se limpian igual sin
 * importar el color. Así cada tablero "se ve" diferente sin tocar la dificultad (no es reto, es sabor).
 */
object LevelPalette {
    /** colorType (0-4; nunca 7 = comodín) del bloque pre-colocado en (x,y) del [level]. */
    fun colorTypeFor(level: Int, x: Int, y: Int): Int {
        val a = level % 5
        val b = (level + 2) % 5
        val c = (level + 4) % 5
        return when (level % 6) {
            // P-447 (2026-10-01, decisión del usuario): el «sólido temático» pintaba TODOS los bloques
            // iniciales de un mismo color y el tablero salía plano (el nivel 282 en Cerámica, todo
            // verde). Ahora su color domina en dos de cada tres filas y la tercera lleva un segundo
            // tono: sigue leyéndose como «el nivel verde», pero con relieve. Solo aspecto.
            0 -> if (y % 3 == 2) c else a           // casi sólido: franjas de acento
            1 -> if (y % 2 == 0) a else b           // rayas horizontales
            2 -> if (x % 2 == 0) a else b           // rayas verticales
            3 -> if ((x + y) % 2 == 0) a else b     // tablero
            4 -> intArrayOf(a, b, c)[(x + y) % 3]   // diagonales (3 colores)
            else -> ((x * 4 / 7) + a) % 5           // arcoíris por columna (desfasado por nivel)
        }.coerceIn(0, 4)
    }
}

/**
 * Generador procedural de niveles (1..[LevelMap.totalLevels]) para cuadrícula 8×8. Reaprovecha un
 * POOL de 100 formas curadas por dificultad global; ver [buildLevel]. La estética varía por nivel
 * vía [LevelPalette]; la calificación 1-3★ por [starsFor].
 */
object LevelLayouts {

    @Synchronized
    fun get(level: Int): Pair<List<LevelCell>, LevelObjective>? {
        if (level !in 1..LevelMap.totalLevels) return null
        TutorialCampaign.layout(level)?.let { return it }
        cache[level]?.let { return it }
        val built = buildLevel(level)
        // Caché pequeña: se llama desde COMPOSICIÓN (el popup de objetivo pide el nivel para pintar
        // sus estrellas), así que sin esto cada recomposición reconstruía el nivel entero — RNG,
        // elección de figura, transformación y siembra de celdas — y eso se notaba como un tirón al
        // abrir la ventana (playtest 2026-08-09).
        //
        // Es seguro porque `buildLevel` es DETERMINISTA: su RNG va sembrado con el número de nivel,
        // así que dos llamadas con el mismo nivel ya devolvían lo mismo. Aquí solo se deja de pagar
        // dos veces por la misma respuesta.
        if (cache.size >= CACHE_MAX) cache.remove(cache.keys.first())
        cache[level] = built
        return built
    }

    /**
     * Unos pocos niveles bastan: el jugador se mueve entre el actual y sus vecinos inmediatos.
     *
     * [get] es `@Synchronized` desde el 2026-08-24: la ronda se **precalcula en un hilo de fondo**
     * (`GameViewModel.prepareRound`) mientras el panel de JUGAR NIVEL está abierto, y la UI sigue
     * pidiendo niveles a la vez (la barra de meta, el propio panel). Dos hilos escribiendo un
     * `LinkedHashMap` es corrupción silenciosa, no una carrera benigna.
     */
    private const val CACHE_MAX = 8
    private val cache = LinkedHashMap<Int, Pair<List<LevelCell>, LevelObjective>>()

    // ── UMBRALES DE ESTRELLA (recalibrados 2026-08-01, pedido del usuario: «que sea más difícil
    //    conseguir las 3 estrellas, pero que un buen jugador pueda») ────────────────────────────
    //
    // Antes eran dos rectas a ojo —2★ = 200+nivel·10, 3★ = 500+nivel·30— y estaban MUY por debajo de
    // lo que el propio juego paga. Las cuentas reales del motor:
    //
    //   · Una línea vale 100 puntos (`LineScoring.LINE`), con multiplicador de simultaneidad
    //     ×1,5/×2/×3 al cerrar 2/3/4 segmentos a la vez. Un jugador competente promedia ≈ ×1,25.
    //   · Cada línea engendra un TEIKA_1 (`spawnLineClearTeika`), y esos T1 acaban fusionándose:
    //     n T1 → n/2 fusiones de 100 + n/4 de 200 + n/8 de 300 + n/16 de 400 ≈ **162 pts por línea**
    //     amortizados (en NIVELES la fusión paga `tier·100`).
    //
    // O sea que cada línea rota vale ≈ 100·1,25 + 162 ≈ [POINTS_PER_LINE] puntos. Con el umbral
    // viejo, el nivel 20 pedía 1.100 para 3★ y romper las 16 líneas del MEGA ya daba ~4.600: las tres
    // estrellas se regalaban en todos los niveles.
    //
    // Ahora el umbral se deriva del TRABAJO QUE EL NIVEL PIDE ([expectedLines]) en vez de una recta
    // sobre el número de nivel: 2★ = cumplir el objetivo justo; 3★ = un 45 % más de trabajo, que es
    // lo que da de sí el presupuesto de turnos si no se desperdicia ninguna ficha y se encadenan
    // roturas múltiples. Es plano a partir de ~nivel 55, y debe serlo: `MAX_REQUIRED_STARS` topa a
    // propósito el trabajo de un nivel para que ninguno dure más que un jefe. La dificultad tardía
    // la ponen el tablero, el bioma y los turnos, no un número de puntos que sube para siempre.

    // ── LA RENTA GARANTIZADA (2026-08-01) ────────────────────────────────────────────────────────
    //
    // El umbral ya no sale de «puntos por línea × esfuerzo». Sale de sumar **lo que el motor paga de
    // verdad por ganar el nivel**, y pedir un margen encima. Antes se estimaba a ojo y el resultado
    // fue el reportado por el usuario: «un jugador llega fácilmente a los 6000» con el umbral en
    // 6.612 — o sea, las tres estrellas se regalaban.
    //
    // Lo que cobra, SIEMPRE, quien gana un nivel de N líneas:
    //
    //   · **Líneas**: N × [LINE_SCORE] (100, `LineScoring.LINE`). Es el suelo: sin bonus de
    //     simultaneidad, que es justo lo que separa a quien juega bien.
    //   · **La cadena de fusión hasta el MEGA**: en niveles una fusión paga `tier·100`, así que N
    //     Teikas T1 rinden N/2·100 + N/4·200 + N/8·300 + N/16·400 = **N × 162,5** ([FUSION_PER_LINE]).
    //     No es una estimación: es la suma cerrada de la escalera.
    //   · **El bonus del MEGA** ([MEGA_BONUS]), que ahora es obligatorio y por tanto renta fija.
    //
    // Con 16 líneas eso son 16×262,5 + 800 = **5.000 puntos solo por ganar**. El umbral de 3★ se
    // pone en 1,6 veces esa renta ([MARGIN_BASE]) → 8.000 en el bioma CRISTAL, que es lo que pidió
    // el usuario, y hay que SACAR 3.000 más jugando bien: roturas simultáneas (×1,5/×2/×3), líneas
    // por encima del mínimo y ¡PERFECTO!.
    //
    // Y escala por dos vías a la vez: la renta crece con las líneas que pide el nivel, y el margen
    // exigido crece con el progreso ([MARGIN_GROWTH]).

    // ── P-160 · POR QUÉ LOS NIVELES CON GRAVEDAD ABREN MÁS VACÍOS, Y POR QUÉ NO SE ARREGLA AQUÍ
    //    (2026-09-04, medido) ───────────────────────────────────────────────────────────────────
    //
    // El hecho: **23,4 celdas de media frente a 29,7** en el resto del mapa. La propuesta aprobada
    // era subirles el tope de relleno para compensar. **Se probó y no hace nada**: con un bonus de 6
    // y con uno de 16 la media sale idéntica, 24,4.
    //
    // El motivo es que `maxFill` solo **recorta**, nunca añade: si la figura del pool ya trae menos
    // celdas que el tope, subir el tope no cambia una sola celda. Y lo que de verdad vacía estos
    // niveles es otra cosa — el punto fijo de asentar↔aflojar. Amontonar la figura contra un borde
    // llena líneas de salida, `ensureLineSlack` las afloja QUITANDO celdas, y la cuenta converge:
    // una figura asentada tiene todas sus celdas apretadas en pocas filas, así que el tope de
    // [LevelOpening.MAX_LINE_FILL] la muerde mucho antes que a una figura repartida.
    //
    // Compensarlo de verdad exigiría **relajarle el tope de línea a estos niveles**, y eso es
    // exactamente lo que la holgura existe para impedir: con 7 de 8, un dominó cierra la fila y el
    // nivel abre regalando líneas. No se toca. Queda escrito el número y la causa, que es lo que
    // hace falta si algún día se decide pagar ese precio.

    /** Lo que paga un segmento entero. Espejo de `LineScoring.LINE` — misma cifra, otro módulo. */
    const val LINE_SCORE = 100

    /**
     * Puntos de FUSIÓN amortizados por cada línea rota. Cada línea engendra un T1
     * (`spawnLineClearTeika`) y en niveles una fusión paga `tier·100`; la escalera entera de N T1
     * hasta el MEGA suma N·(100/2 + 200/4 + 300/8 + 400/16) = N·162,5.
     */
    const val FUSION_PER_LINE = 162.5f

    /**
     * Bonus por formar el MEGA TEIKA en niveles.
     *
     * **Era `threeStarScore / 2` y ese era el bug de balance** (2026-08-01): desde que el MEGA es
     * obligatorio, ganar el nivel regalaba la MITAD de la barra de 3★, y subir el umbral subía el
     * regalo con él — imposible de arreglar tocando el umbral. Ahora es una cifra FIJA y modesta:
     * el MEGA sigue celebrándose, pero no decide la calificación.
     */
    const val MEGA_BONUS = 800

    /**
     * Margen sobre la renta garantizada que exigen las 3★ al empezar el juego.
     *
     * **1,6 → 1,30 (2026-08-01, recalibrado con playtest).** El 1,6 asumía que jugar bien añadiría
     * un 60 % sobre la renta; cinco partidas reales dieron entre **0,83 y 1,19**. Un umbral que
     * nadie puede alcanzar no gradúa nada: convierte las 3★ en decoración. Se baja a 1,30 y a
     * cambio se sube lo que paga la habilidad (bonus de simultaneidad y ¡PERFECTO!), para que ese
     * 30 % sea un objetivo real y no otro número inventado.
     */
    const val MARGIN_BASE = 1.30f

    /** Cuánto crece ese margen del primer nivel al último: 1,30 → 1,55. */
    const val MARGIN_GROWTH = 0.25f

    /**
     * Fracción del presupuesto de turnos que hay que dejar SIN GASTAR para merecer la 2ª estrella
     * (2026-08-01, pedido del usuario).
     *
     * Las 2★ ya no se compran con puntos: se ganan con holgura. Terminar el nivel en el último turno
     * es sobrevivir; terminarlo con margen es dominarlo, y esa diferencia no la contaba nada.
     */
    const val TWO_STAR_MOVES_LEFT = 0.10f

    /**
     * Líneas que el nivel EXIGE de verdad: las de su objetivo de estrellas ([LevelBudget
     * .STARS_PER_LINE] estrellas por línea) y el suelo de 16 que impone el MEGA TEIKA, obligatorio
     * en todo el mapa. Con [MAX_REQUIRED_STARS] = 56 los dos coinciden en 16 desde el nivel ~20.
     *
     * Cerrado y barato **a propósito**: no construye el nivel (`buildLevel` genera figura, colores y
     * estrellas). La barra de meta del HUD lo llama en cada recomposición.
     */
    fun expectedLines(level: Int): Int {
        val objLines = kotlin.math.ceil(
            requiredStarsForLevel(level) / LevelBudget.STARS_PER_LINE
        ).toInt()
        val megaLines = if (ChapterArc.requiresMega(level)) LevelBudget.T1_PER_MEGA else 0
        return maxOf(objLines, megaLines).coerceAtLeast(3)
    }

    /**
     * Puntos que el motor paga SOLO POR GANAR un nivel de [level] líneas exigidas: el suelo de las
     * líneas, la cadena de fusión que engendran y el bonus del MEGA. Ver la nota de arriba.
     *
     * Es la cifra contra la que se mide todo: un umbral por debajo de esto se regala.
     */
    fun guaranteedScore(level: Int): Int =
        (expectedLines(level) * (LINE_SCORE + FUSION_PER_LINE)).roundToInt() + MEGA_BONUS

    /** Margen sobre la renta garantizada que exige la 3ª estrella en [level]: 1,6 → 2,1. */
    fun threeStarMargin(level: Int): Float {
        val t = ((level - 1).toFloat() / (LevelMap.totalLevels - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        return MARGIN_BASE + t * MARGIN_GROWTH
    }

    /** Umbral de puntuación para 3★ del nivel (el que llena la barra de meta del HUD). */
    fun threeStarScore(
        level: Int,
        companionId: String? = null,
        companionLevel: Int = 1
    ): Int = LevelRating.profile(level, companionId, companionLevel).threeStarScore

    /** Umbral gradual de rendimiento para 2★. Ya no depende de conservar un porcentaje fijo. */
    fun twoStarScore(
        level: Int,
        companionId: String? = null,
        companionLevel: Int = 1
    ): Int = LevelRating.profile(level, companionId, companionLevel).twoStarScore

    /**
     * Turnos que hay que dejar SIN GASTAR para merecer la 2ª estrella. Mínimo 1: un nivel corto no
     * puede dar la estrella por terminar exactamente en el último turno.
     */
    fun twoStarMovesLeft(level: Int): Int =
        kotlin.math.ceil(LevelBudget.movesForLevel(level) * 0.08f).toInt()
            .coerceAtLeast(1)

    /**
     * Bonus de puntuación por formar un MEGA TEIKA en modo NIVELES. FUENTE ÚNICA (balance).
     * Cifra fija: ver [MEGA_BONUS] para por qué dejó de escalar con el umbral.
     * En INFINITO (sin estrellas) el bonus sigue siendo 5000 plano.
     */
    fun megaTeikaScore(level: Int): Int = MEGA_BONUS

    /**
     * Calificación 1-3★ del nivel. **POR PUNTUACIÓN** (2026-09-23, decisión del usuario, al retirar
     * los turnos): 1★ al completar, 2★ y 3★ al llegar a [twoStarScore] / [threeStarScore]. Con
     * `movesTotal = 0` el bonus por turnos sobrantes vale 0 y solo cuentan los puntos.
     *
     * FUENTE ÚNICA del criterio: la usan el overlay de victoria, el mural del mapa, la barra de meta
     * del HUD y el popup de objetivo. Antes cada UI llevaba su copia de la fórmula.
     *
     * [movesTotal] a 0 = nivel sin límite de turnos (no debería pasar en niveles): entonces la 2★ no
     * puede exigir holgura y se concede, para no dejar el nivel topado a 1★ para siempre.
     */
    fun starsFor(
        level: Int,
        score: Int,
        movesLeft: Int,
        movesTotal: Int,
        companionId: String? = null,
        companionLevel: Int = 1
    ): Int = LevelRating.starsFor(
        level = level,
        score = score,
        movesLeft = movesLeft,
        movesTotal = movesTotal,
        companionId = companionId,
        companionLevel = companionLevel
    )

    private fun buildLevel(level: Int): Pair<List<LevelCell>, LevelObjective> {
        val rng   = kotlin.random.Random(level.toLong() * 31337L)
        // Dificultad global 0f (primer nivel) .. 1f (último). Define el "perfil" del nivel sin
        // necesitar 300 formas a mano: reaprovecha el POOL de 100 formas curadas (s01..s100).
        val t = ((level - 1).toFloat() / (LevelMap.totalLevels - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        // FIGURA: la elige [ChapterShapePool], que reparte el pool POR CAPÍTULO.
        //
        // Antes salía de la posición GLOBAL —`(t*99)+1 ± 4`— y como un capítulo ocupa el 7 % de esa
        // recta, un bioma entero dibujaba sobre unas 12 figuras de las 100, repetidas y todas de la
        // misma dificultad. Ese era el motivo de que «los 20 niveles del Cristal se sientan iguales»
        // (diagnóstico del usuario, 2026-07-31). Ahora cada capítulo recorre una ventana de 40
        // figuras con paso coprimo: **ningún nivel de un bioma repite figura** y las ventanas siguen
        // avanzando de bioma en bioma para que el juego se endurezca.
        val shapeId  = ChapterShapePool.shapeIdFor(level)
        val rawShape = transformShape(
            getShape(shapeId).map { (x, y) -> x.coerceIn(0, 7) to y.coerceIn(0, 7) }.distinct(),
            rng
        )
        // Densidad máxima: temprano más vacío, tarde más lleno (siempre jugable, ≥16 huecos).
        // El director alterna tableros abiertos, compactos y de precisión dentro de CADA capítulo.
        // La figura sigue viniendo del pool curado; solo cambia cuánto de ella queda expuesto.
        // (P-160: aquí se probó un bonus de relleno para los niveles con gravedad y se retiró — no
        //  movía una sola celda. Ver el bloque de arriba para la medición y la causa real.)
        val maxFill = (16 + t * 40f).toInt()
            .plus(LevelDirector.identityFor(level).layoutDensityDelta)
            // P-287: el capítulo mueve también el máximo (menos madera al principio, más al final).
            .plus(ChapterDifficulty.densityBonus(level))
            .coerceIn(11, 56)
        // Recorte que CONSERVA LA FIGURA. Antes era `shuffled(rng).take(maxFill)`: una figura curada
        // que no cupiera se convertía en confeti —celdas sueltas repartidas al azar por el tablero—,
        // o sea que el trabajo de las 100 formas se tiraba justo en los casos que más lo necesitan.
        // Se nota más desde que cada capítulo recorre una ventana ancha del pool (figuras grandes
        // llegan antes), así que ahora se conservan las celdas más CERCANAS AL CENTRO de la figura:
        // queda un cuerpo compacto y reconocible en vez de una nube de puntos.
        val trimmed = if (rawShape.size > maxFill) {
            val cx = rawShape.sumOf { it.first }.toFloat() / rawShape.size
            val cy = rawShape.sumOf { it.second }.toFloat() / rawShape.size
            rawShape
                .sortedBy { (x, y) ->
                    val dx = x - cx; val dy = y - cy
                    dx * dx + dy * dy
                }
                .take(maxFill)
                .sortedBy { it.first * 8 + it.second }
        } else rawShape

        // ── GARANTÍA DE APERTURA (2026-08-24, pedido del usuario) ────────────────────────────────
        // Ni la figura curada ni el recorte por `maxFill` miraban si lo que queda LIBRE admite una
        // jugada. Resultado: 14 niveles abrían sin sitio para NINGUNA ficha de 3 celdas, y los cuatro
        // del **damero** (269/272/286/289) ni siquiera para un dominó — media pizarra libre y ni una
        // celda libre tocando a otra. Con la bandeja reducida a monominós y ~110 estrellas que
        // recoger, no eran niveles difíciles: eran niveles imposibles.
        //
        // [LevelOpening] impone el mínimo —que exista una bandeja mixta posible— y, si no lo hay,
        // abre un claro de 3×2 mordiendo el borde de la figura. Los otros 279 niveles no se tocan:
        // un tablero apretado sigue siendo apretado.
        // ── HOLGURA DE LÍNEA (2026-08-26, pedido del usuario) ────────────────────────────────────
        // Ni la figura curada ni `maxFill` miraban cuántas celdas caían en la MISMA fila o columna.
        // El censo del día: 150 de los 293 niveles abrían con una línea YA hecha —3,79 de media, 9
        // en el peor—, así que la primera ficha, puesta donde fuera, las rompía sola y regalaba sus
        // Teikas y sus estrellas. El nivel denso salía MÁS fácil que el vacío.
        //
        // [LevelOpening.ensureLineSlack] deja como mucho [LevelOpening.MAX_LINE_FILL] celdas por
        // línea: cerrar una fila de salida cuesta ahora dos colocaciones del jugador. Va ANTES de la
        // garantía de apertura porque solo QUITA celdas, y quitar puede romper la bandeja mixta:
        // quien tiene la última palabra sobre si el nivel se puede jugar es `ensurePlayableOpening`.
        // ── P-80 · NIVEL CON GRAVEDAD (2026-09-01) ───────────────────────────────────────────────
        // Un nivel con gravedad no puede abrir con la figura flotando: la primera ficha que el
        // jugador soltara desplomaría el tablero entero de una vez, y la silueta curada no habría
        // significado nada. Se asienta AQUÍ, en el generador, para que el nivel abra ya con el
        // tablero que va a jugarse — y para que el panel de apertura y la ronda cuenten los mismos
        // turnos (P-86: el presupuesto se deriva de este mismo tablero).
        //
        // Asentar amontona la figura contra el fondo y eso vuelve a llenar líneas de salida, que es
        // justo lo que la HOLGURA DE LÍNEA vino a impedir. Por eso se alternan: asentar, aflojar,
        // asentar… hasta que aflojar ya no quita nada. Converge siempre porque aflojar solo QUITA
        // celdas; el guard es una red, no el caso normal.
        // (2026-08-28) Entre la holgura y la garantía se tallaba la SILUETA del nivel (celdas
        // muertas de `BoardShape`). Retirada por decisión del usuario: el tablero es siempre un 8×8.
        //
        // (2026-09-03) **El asentamiento pasa a ser lo ÚLTIMO**, y ese orden es el arreglo de un bug
        // que llevaba aquí desde P-80 sin dar la cara. Aflojar (`ensureLineSlack`) y abrir un claro
        // (`ensurePlayableOpening`) solo QUITAN celdas, y quitarle una celda a una figura ya asentada
        // deja un hueco justo debajo de la que estaba encima: la figura vuelve a flotar. Con una sola
        // dirección de gravedad y catorce niveles, ninguno llegó a caer en el caso; con cuatro
        // direcciones y muchos más niveles, el nivel 179 lo enseñó al primer intento.
        //
        // La alternación de P-80 se conserva —asentar amontona y eso puede volver a regalar líneas de
        // salida, que es lo que la HOLGURA vino a impedir— pero ahora cierra con el asentamiento.
        // Converge siempre porque cada vuelta solo quita celdas; el guard es una red, no el caso
        // normal.
        val pull = BoardGravity.pullFor(level)
        var shape = LevelOpening.ensurePlayableOpening(
            LevelOpening.ensureLineSlack(trimmed, rng), rng
        )
        if (pull != null) {
            // El guard es el nº de celdas del tablero y no un 4: la alternación **tiene** que llegar
            // al punto fijo, porque salir por el guard significa entregar una figura que cumple una
            // de las dos reglas y no la otra —flotando, o regalando una línea de salida—, y las dos
            // están defendidas por su test. Termina seguro: cada vuelta que no rompe quita al menos
            // una celda, así que como mucho hay tantas vueltas como celdas.
            var guard = GameState.GRID_SIZE * GameState.GRID_SIZE
            while (guard-- > 0) {
                val settled = BoardGravity.settleFigure(shape, pull = pull)
                val relaxed = LevelOpening.ensurePlayableOpening(
                    LevelOpening.ensureLineSlack(settled, rng), rng
                )
                shape = settled
                if (relaxed.size == settled.size) break
                shape = relaxed
            }
        }

        val numColors = objectiveColorCount(level)
        val allColors       = TargetColor.entries.shuffled(rng)
            .take((numColors + 1).coerceAtMost(TargetColor.entries.size))
        val objectiveColors = allColors.take(numColors)
        val totalStars = requiredStarsForLevel(level)
        val starsByColor = objectiveColors.mapIndexed { index, color ->
            val base = totalStars / objectiveColors.size
            val extra = if (index < totalStars % objectiveColors.size) 1 else 0
            color to base + extra
        }.toMap()
        // ── ESTRELLAS EN LA FIGURA: **TODAS** (2026-08-01, pedido del usuario) ───────────────────
        // Historia corta: primero era una tirada por celda (10-45 %), luego un cupo repartido
        // (25-45 %). Las dos versiones dejaban el mismo problema en la mesa — **el jugador no puede
        // ver al empezar dónde está su objetivo**. Con menos de la mitad de la figura marcada, el
        // grueso de las estrellas llegaba en las fichas de la bandeja (`Piece.generateRandomPiece`),
        // o sea al azar y a mitad de partida: imposible planificar, que es justo lo que un puzzle
        // debe premiar.
        //
        // Ahora la figura ENTERA lleva estrella. Al levantar el telón el tablero enseña el objetivo
        // completo y el nivel se puede leer como un problema cerrado: «esto es lo que hay que
        // romper». Los colores del objetivo se siguen repartiendo en round-robin, así que ninguno se
        // queda sin representación y el reparto es exacto, no una tirada de dados.
        //
        // El coste de balance es intencionado y ya está pagado: recoger estrellas es más fácil, y a
        // cambio los umbrales de 2★/3★ subieron (ver [POINTS_PER_LINE]) — lo difícil pasa a ser
        // PUNTUAR bien, no encontrar el objetivo.
        // ── LA PALETA (2026-08-26f) ──────────────────────────────────────────────────────────────
        // La figura ENTERA sigue llevando estrella (decisión del 2026-08-01, intacta): el jugador
        // tiene que leer el objetivo completo al levantarse el telón. Lo que cambia por nivel es
        // DÓNDE está cada color ([LevelVariety.StarPalette]): en bandas, por cuadrantes, en anillos…
        // Mismo número de estrellas, distinto orden de juego, cero dificultad añadida.
        val palette = LevelVariety.profileFor(level).palette
        val painted = LevelVariety.applyPalette(palette, objectiveColors, shape)
        val cells = shape.mapIndexed { i, (x, y) -> LevelCell(x, y, painted[i]) }
        return cells to objectiveFor(level, starsByColor)
    }

    /**
     * **QUÉ TE PIDE EL NIVEL: SIEMPRE LO MISMO** (2026-08-01, decisión del usuario).
     *
     * `TURNOS + ESTRELLAS + MEGA TEIKA`. Ni un sabor más.
     *
     * Entre el 2026-07-31b y hoy el generador repartía tres objetivos distintos por acto de capítulo
     * (ESTRELLAS en la presentación, LÍNEAS/DESPEJE alternándose en el desarrollo, DESPEJE estricto
     * en el clímax). La idea era variedad; el resultado en pantalla fue otro: cada sabor necesitaba
     * su propio chip en el HUD —`GoalChip`, `MegaChip`, `MovesChip`— y entre ellos se empujaban y
     * descuadraban la zona de objetivos nivel a nivel.
     *
     * La variedad se queda donde no cuesta legibilidad: la figura de partida ([ChapterShapePool]),
     * la mecánica del bioma, el arco de capítulo ([ChapterArc]) y el presupuesto de turnos
     * ([LevelBudget]). La PREGUNTA es siempre la misma y el HUD puede ser siempre el mismo.
     *
     * [LevelGoal.LINES] / [LevelGoal.CLEAR] / [LevelGoal.SURVIVE] siguen vivos en el enum y en el
     * motor porque viajan dentro de partidas guardadas — igual que `GameStatus.CHESS`—, pero **ya
     * ningún nivel los genera**.
     */
    private fun objectiveFor(
        level: Int,
        starsByColor: Map<TargetColor, Int>
    ): LevelObjective = LevelObjective(starsByColor)

    /**
     * Líneas a romper: de 6 al principio a [MAX_LINES_GOAL] al final.
     *
     * El techo es el mismo criterio que [MAX_REQUIRED_STARS]: **ningún nivel normal debe pedir más
     * trabajo que un nivel-jefe**. El jefe pide 16 líneas (las del MEGA) *y además* formarlo, así que
     * un nivel de líneas que pidiera 26 duraría más que el clímax de su propio capítulo y el bioma
     * terminaría cuesta abajo.
     */
    const val MAX_LINES_GOAL = 16

    fun linesGoalFor(t: Float): Int =
        (6 + t * 20f).roundToInt().coerceIn(3, MAX_LINES_GOAL)

    /**
     * Bloques que pueden QUEDAR en el tablero. Se mide contra el tamaño de la figura de partida:
     * hay que dejarlo casi limpio, pero nunca se exige el cero absoluto —eso dependería de que la
     * bandeja reparta la pieza exacta y sería una derrota por suerte—.
     */
    fun clearGoalFor(figureSize: Int): Int = (figureSize / 4).coerceIn(2, 8)

    // (`surviveGoalFor` se retiró con el objetivo SUPERVIVENCIA el 2026-07-31c — ver [LevelGoal].)

    /**
     * **TOPE DE ESTRELLAS** (2026-07-31c). La curva `18 + t·240` llegaba a **258 estrellas** en los
     * últimos niveles: se diseñó cuando los niveles no tenían fin: sin límite de turnos, pedir 258
     * solo significaba «tardas más». Con los TURNOS CONTADOS ([LevelBudget]) eso se traduce en un
     * presupuesto de **267 movimientos** —el nivel 150 pedía 140 estrellas y salían 156 turnos—, y un
     * nivel de 250 jugadas no es un nivel: es una sesión.
     *
     * El tope lo fija la propia geometría del juego: cada línea deja ~3,5 estrellas
     * ([LevelBudget.STARS_PER_LINE]), así que 56 estrellas ≈ **16 líneas**, exactamente el mismo
     * trabajo que impone el MEGA TEIKA.
     *
     * **40 → 56 → 70 (2026-08-01, pedido del usuario).** El paso a 56 fue gratis: era justo donde el
     * objetivo empataba con el suelo de 16 líneas del MEGA. **70 es el primer valor que MANDA sobre
     * el MEGA** — 20 líneas —, así que el nivel deja de cerrarse solo por formar el Mega y hay que ir
     * de verdad a por las estrellas. Cada 3,5 estrellas de más suman una línea, y una línea ~2 turnos.
     *
     * **Es un dial de balance**: subirlo alarga los niveles tardíos, bajarlo los acorta.
     */
    const val MAX_REQUIRED_STARS = 70

    /**
     * Estrellas que pide el PRIMER nivel con bioma. Es la base de la curva y, por tanto, lo que
     * gobierna todo el principio del juego.
     *
     * **18 → 30 (2026-08-01).** Subir solo [MAX_REQUIRED_STARS] no cambiaba nada donde se juega: en
     * el bioma CRISTAL la curva pedía 21-35 estrellas y el tope ni se rozaba, así que el «aumenta el
     * objetivo» no se notaba hasta el nivel 60 largo. Y sale GRATIS en turnos: con 30-47 estrellas
     * las líneas exigidas siguen por debajo de las 16 que impone el MEGA, que es quien manda el
     * presupuesto hasta el nivel ~33.
     */
    const val BASE_REQUIRED_STARS = 30

    /**
     * Estrellas EXTRA por cada color del objetivo (2026-08-01, pedido del usuario: «auméntale 10
     * estrellas por tipo de estrella»). Solo en los niveles con bioma: los 4 de intro son el
     * tutorial y ahí un color son 10 estrellas más sobre 12-30, que es duplicarlo.
     *
     * Se suma **después del tope** [MAX_REQUIRED_STARS] a propósito: si entrara antes, el tope se lo
     * tragaría justo en los niveles donde más se nota (a partir del ~50 la curva ya está topada) y
     * el aumento no existiría donde el jugador pasa la mayor parte del juego.
     *
     * **Lo que cuesta y quién lo paga:** cada 3,5 estrellas son una línea
     * ([LevelBudget.STARS_PER_LINE]), así que con 4 colores son +40 estrellas ≈ +12 líneas. Desde el
     * anclaje al playtest (2026-08-01b) eso **ya no infla el presupuesto de turnos**: los niveles del
     * mapa cobran la curva medida ([LevelBudget.MOVES_AT_START]→[LevelBudget.MOVES_AT_END]) porque el
     * tablero lleno cierra varias líneas por ficha. Lo que sí sube es el UMBRAL de 3★, que se deriva
     * de [expectedLines] — por eso llama a la MISMA función que construye el objetivo.
     */
    const val STARS_PER_COLOR_BONUS = 10

    /**
     * Colores distintos que pide el objetivo de [level]. FUENTE ÚNICA: la usa `buildLevel` para
     * repartir las estrellas y [requiredStarsForLevel] para cobrar el bonus por color. Vivía suelta
     * dentro de `buildLevel`, y con el bonus de arriba eso habría dejado el presupuesto calculando
     * sobre un número de colores distinto del que el nivel acaba teniendo.
     */
    fun objectiveColorCount(level: Int): Int {
        val t = ((level - 1).toFloat() / (LevelMap.totalLevels - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        return when {
            t < 0.03f -> 1   // ~niveles 1-12: un color (arranque amable)
            t < 0.12f -> 2   // ~13-40
            t < 0.45f -> 3   // ~41-138
            else      -> 4
        }
    }

    private fun requiredStarsForLevel(level: Int): Int {
        val t = ((level - 1).toFloat() / (LevelMap.totalLevels - 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        val base = (BASE_REQUIRED_STARS + t * 240f).coerceAtMost(MAX_REQUIRED_STARS.toFloat())
        if (level <= LevelMap.introLevels) {
            // Rampa suave dentro de los niveles de intro (sin bioma) — evita el salto brusco
            // de "15 fijo" a la curva general justo al terminar la intro.
            val introT = (level - 1) / (LevelMap.introLevels - 1).toFloat()
            return (12 + introT * (base - 12)).toInt()
        }
        return base.toInt() + STARS_PER_COLOR_BONUS * objectiveColorCount(level)
    }

    /** Rota/espeja una forma en la rejilla 8×8 (coords 0-7) → frescura sin dibujar formas nuevas. */
    private fun transformShape(shape: List<Pair<Int, Int>>, rng: kotlin.random.Random): List<Pair<Int, Int>> =
        when (rng.nextInt(6)) {
            0 -> shape
            1 -> shape.map { (x, y) -> (7 - x) to y }
            2 -> shape.map { (x, y) -> x to (7 - y) }
            3 -> shape.map { (x, y) -> y to x }
            4 -> shape.map { (x, y) -> (7 - y) to x }
            else -> shape.map { (x, y) -> y to (7 - x) }
        }.distinct()

    // ── Router ────────────────────────────────────────────────────────────────
    private fun getShape(level: Int): List<Pair<Int, Int>> = when (level) {
        1   -> s01();  2   -> s02();  3   -> s03();  4   -> s04();  5   -> s05()
        6   -> s06();  7   -> s07();  8   -> s08();  9   -> s09(); 10   -> s10()
        11  -> s11(); 12   -> s12(); 13   -> s13(); 14   -> s14(); 15   -> s15()
        16  -> s16(); 17   -> s17(); 18   -> s18(); 19   -> s19(); 20   -> s20()
        21  -> s21(); 22   -> s22(); 23   -> s23(); 24   -> s24(); 25   -> s25()
        26  -> s26(); 27   -> s27(); 28   -> s28(); 29   -> s29(); 30   -> s30()
        31  -> s31(); 32   -> s32(); 33   -> s33(); 34   -> s34(); 35   -> s35()
        36  -> s36(); 37   -> s37(); 38   -> s38(); 39   -> s39(); 40   -> s40()
        41  -> s41(); 42   -> s42(); 43   -> s43(); 44   -> s44(); 45   -> s45()
        46  -> s46(); 47   -> s47(); 48   -> s48(); 49   -> s49(); 50   -> s50()
        51  -> s51(); 52   -> s52(); 53   -> s53(); 54   -> s54(); 55   -> s55()
        56  -> s56(); 57   -> s57(); 58   -> s58(); 59   -> s59(); 60   -> s60()
        61  -> s61(); 62   -> s62(); 63   -> s63(); 64   -> s64(); 65   -> s65()
        66  -> s66(); 67   -> s67(); 68   -> s68(); 69   -> s69(); 70   -> s70()
        71  -> s71(); 72   -> s72(); 73   -> s73(); 74   -> s74(); 75   -> s75()
        76  -> s76(); 77   -> s77(); 78   -> s78(); 79   -> s79(); 80   -> s80()
        81  -> s81(); 82   -> s82(); 83   -> s83(); 84   -> s84(); 85   -> s85()
        86  -> s86(); 87   -> s87(); 88   -> s88(); 89   -> s89(); 90   -> s90()
        91  -> s91(); 92   -> s92(); 93   -> s93(); 94   -> s94(); 95   -> s95()
        96  -> s96(); 97   -> s97(); 98   -> s98(); 99   -> s99(); 100  -> s100()
        else -> s01()
    }

    // ── Helpers (8×8 grid, coords 0-7) ───────────────────────────────────────
    private fun fill(x1: Int, y1: Int, x2: Int, y2: Int) =
        (y1..y2).flatMap { y -> (x1..x2).map { x -> x to y } }

    private fun box(x1: Int, y1: Int, x2: Int, y2: Int) = buildSet<Pair<Int, Int>> {
        for (x in x1..x2) { add(x to y1); add(x to y2) }
        for (y in y1 + 1 until y2) { add(x1 to y); add(x2 to y) }
    }.toList()

    /** Banda horizontal: todas las columnas 0-7 para filas y1..y2. */
    private fun hBand(y1: Int, y2: Int) =
        (y1..y2).flatMap { y -> (0..7).map { x -> x to y } }

    /** Banda vertical: todas las filas 0-7 para columnas x1..x2. */
    private fun vBand(x1: Int, x2: Int) =
        (x1..x2).flatMap { x -> (0..7).map { y -> x to y } }

    // 7 segmentos adaptados a 8×8 (cols 1-6, filas 0-7)
    //   ST ─────── fila 0
    //   STL│   │STR filas 1-3
    //   SM ─────── fila 4
    //   SBL│   │SBR filas 5-7 (solapa SB en fila 7)
    //   SB ─────── fila 7
    private val ST  = (1..6).map { x -> x to 0 }
    private val STL = (1..3).flatMap { y -> listOf(0 to y, 1 to y) }
    private val STR = (1..3).flatMap { y -> listOf(6 to y, 7 to y) }
    private val SM  = (1..6).map { x -> x to 4 }
    private val SBL = (5..7).flatMap { y -> listOf(0 to y, 1 to y) }
    private val SBR = (5..7).flatMap { y -> listOf(6 to y, 7 to y) }
    private val SB  = (1..6).map { x -> x to 7 }
    private fun seg(vararg p: List<Pair<Int, Int>>) = p.flatMap { it }.distinct()

    // ══════════════════════════════════════════════════════════════════════════
    //  GRUPO A — Muy fácil (1-10): formas simples, ~12-22 celdas
    // ══════════════════════════════════════════════════════════════════════════

    /** 1 — Cruz 2-wide centrada. 20 celdas. */
    private fun s01() = buildSet<Pair<Int, Int>> {
        for (x in 1..6) { add(x to 3); add(x to 4) }
        for (y in 1..6) { add(3 to y); add(4 to y) }
    }.toList()

    /** 2 — Cuatro bloques 2×2 en las esquinas. 16 celdas. */
    private fun s02(): List<Pair<Int, Int>> =
        fill(0, 0, 1, 1) + fill(6, 0, 7, 1) + fill(0, 6, 1, 7) + fill(6, 6, 7, 7)

    /** 3 — Bloque sólido 4×4 en el centro. 16 celdas. */
    private fun s03() = fill(2, 2, 5, 5)

    /** 4 — Dos barras horizontales completas: filas 0 y 7. 16 celdas. */
    private fun s04(): List<Pair<Int, Int>> = hBand(0, 0) + hBand(7, 7)

    /** 5 — Dos barras verticales completas: cols 0 y 7. 16 celdas. */
    private fun s05(): List<Pair<Int, Int>> = vBand(0, 0) + vBand(7, 7)

    /** 6 — Letra "I": serifas anchas + tallo 2-wide. 20 celdas. */
    private fun s06() = buildSet<Pair<Int, Int>> {
        for (x in 2..5) { add(x to 0); add(x to 7) }   // serifas
        for (y in 1..6) { add(3 to y); add(4 to y) }    // tallo
    }.toList()

    /** 7 — Letra "T": barra superior + tallo 2-wide. 20 celdas. */
    private fun s07() = buildSet<Pair<Int, Int>> {
        for (x in 0..7) add(x to 0)
        for (y in 1..6) { add(3 to y); add(4 to y) }
    }.toList()

    /** 8 — Letra "L": columna izquierda + base inferior. 22 celdas. */
    private fun s08() = buildSet<Pair<Int, Int>> {
        for (y in 0..5) { add(1 to y); add(2 to y) }
        for (x in 1..6) { add(x to 6); add(x to 7) }
    }.toList()

    /** 9 — Cuatro almohadillas 2×2 en los centros de borde. 16 celdas. */
    private fun s09() = buildSet<Pair<Int, Int>> {
        add(3 to 0); add(4 to 0); add(3 to 1); add(4 to 1)   // top
        add(3 to 6); add(4 to 6); add(3 to 7); add(4 to 7)   // bottom
        add(0 to 3); add(1 to 3); add(0 to 4); add(1 to 4)   // left
        add(6 to 3); add(7 to 3); add(6 to 4); add(7 to 4)   // right
    }.toList()

    /** 10 — Flecha ↑ grande centrada. 20 celdas. */
    private fun s10() = buildSet<Pair<Int, Int>> {
        add(3 to 0); add(4 to 0)
        add(2 to 1); add(3 to 1); add(4 to 1); add(5 to 1)
        add(1 to 2); add(6 to 2)
        for (y in 2..7) { add(3 to y); add(4 to y) }
    }.toList()

    // ══════════════════════════════════════════════════════════════════════════
    //  GRUPO B — Fácil (11-20): letras outline ~18-28 celdas
    // ══════════════════════════════════════════════════════════════════════════

    /** 11 — Letra "H": dos columnas + travesaño central. 24 celdas. */
    private fun s11() = buildSet<Pair<Int, Int>> {
        for (y in 0..7) { add(1 to y); add(6 to y) }
        for (x in 2..5) { add(x to 3); add(x to 4) }
    }.toList()

    /** 12 — Letra "U": dos columnas + base. 22 celdas. */
    private fun s12() = buildSet<Pair<Int, Int>> {
        for (y in 0..5) { add(1 to y); add(6 to y) }
        for (x in 1..6) { add(x to 6); add(x to 7) }
    }.toList()

    /** 13 — Letra "V": dos brazos diagonales 2-wide convergentes. 18 celdas. */
    private fun s13() = buildSet<Pair<Int, Int>> {
        add(0 to 0); add(1 to 0); add(6 to 0); add(7 to 0)
        add(1 to 1); add(2 to 1); add(5 to 1); add(6 to 1)
        add(2 to 2); add(3 to 2); add(4 to 2); add(5 to 2)
        add(3 to 3); add(4 to 3)
        add(3 to 4); add(4 to 4)
    }.toList()

    /** 14 — Letra "C": barra izq + top + bottom. 26 celdas. */
    private fun s14() = buildSet<Pair<Int, Int>> {
        for (y in 0..7) { add(1 to y); add(2 to y) }
        for (x in 3..7) { add(x to 0); add(x to 1) }
        for (x in 3..7) { add(x to 6); add(x to 7) }
    }.toList()

    /** 15 — Letra "Y": dos brazos diagonales + tallo. 24 celdas. */
    private fun s15() = buildSet<Pair<Int, Int>> {
        add(0 to 0); add(1 to 0); add(1 to 1); add(2 to 1)
        add(2 to 2); add(3 to 2); add(3 to 3); add(4 to 3)
        add(7 to 0); add(6 to 0); add(6 to 1); add(5 to 1)
        add(5 to 2); add(4 to 2)
        for (y in 3..7) { add(3 to y); add(4 to y) }
    }.toList()

    /** 16 — Letra "X": dos diagonales 2-wide. 26 celdas. */
    private fun s16() = buildSet<Pair<Int, Int>> {
        for (i in 0..7) {
            add(i to i)
            if (i > 0) add((i - 1) to i)
            add((7 - i) to i)
            // (7-i+1) se sale de la rejilla (x=8) solo en i=0, no en i=7 — el guard iba al revés.
            if (i > 0) add((7 - i + 1) to i)
        }
    }.toList()

    /** 17 — Letra "O": contorno ovalado 2-wide (sin esquinas). 24 celdas. */
    private fun s17() = buildSet<Pair<Int, Int>> {
        for (x in 1..6) { add(x to 0); add(x to 7) }
        for (y in 1..6) { add(0 to y); add(7 to y) }
    }.toList()

    /** 18 — Letra "E": barra izq + 3 barras horizontales. 28 celdas. */
    private fun s18() = buildSet<Pair<Int, Int>> {
        for (y in 0..7) { add(1 to y); add(2 to y) }
        for (x in 3..7) { add(x to 0); add(x to 1) }
        for (x in 3..6) { add(x to 3); add(x to 4) }
        for (x in 3..7) { add(x to 6); add(x to 7) }
    }.toList()

    /** 19 — Letra "Z": top + diagonal 2-wide + bottom. 28 celdas. */
    private fun s19() = buildSet<Pair<Int, Int>> {
        for (x in 0..7) { add(x to 0); add(x to 1) }
        for (x in 0..7) { add(x to 6); add(x to 7) }
        for (s in 0..5) {
            val x = (5 - s).coerceIn(0, 7)
            add(x to s + 1)
            add((x + 1).coerceAtMost(7) to s + 1)
        }
    }.toList()

    /** 20 — Letra "A" (sin travesaño): pico + dos patas. 24 celdas. */
    private fun s20() = buildSet<Pair<Int, Int>> {
        add(3 to 0); add(4 to 0)
        add(2 to 1); add(3 to 1); add(4 to 1); add(5 to 1)
        add(1 to 2); add(2 to 2); add(5 to 2); add(6 to 2)
        for (y in 3..7) { add(1 to y); add(2 to y); add(5 to y); add(6 to y) }
    }.toList()

    // ══════════════════════════════════════════════════════════════════════════
    //  GRUPO C — Medio-fácil (21-30): letras complejas, ~24-34 celdas
    // ══════════════════════════════════════════════════════════════════════════

    /** 21 — Letra "B": barra izq + dos bumps. 32 celdas. */
    private fun s21() = buildSet<Pair<Int, Int>> {
        for (y in 0..7) { add(1 to y); add(2 to y) }
        for (x in 3..6) { add(x to 0); add(x to 1) }
        for (y in 2..3) { add(6 to y); add(7 to y) }
        for (x in 3..5) { add(x to 3); add(x to 4) }
        for (y in 5..6) { add(6 to y); add(7 to y) }
        for (x in 3..6) { add(x to 6); add(x to 7) }
    }.toList()

    /** 22 — Letra "S" (5-segmento): top+TL+mid+BR+bot. 30 celdas. */
    private fun s22() = seg(ST, STL, SM, SBR, SB)

    /** 23 — "M" + "W" espejado: simetría vertical perfecta. 36 celdas. */
    private fun s23() = buildSet<Pair<Int, Int>> {
        for (y in 0..3) { add(0 to y); add(1 to y); add(6 to y); add(7 to y) }
        add(2 to 1); add(3 to 2); add(4 to 2); add(5 to 1)
        for (y in 4..7) { add(0 to y); add(1 to y); add(6 to y); add(7 to y) }
        add(2 to 6); add(3 to 5); add(4 to 5); add(5 to 6)
    }.toList()

    /** 24 — Rombo outline grande, simetría 4-vías. 28 celdas. */
    private fun s24() = buildSet<Pair<Int, Int>> {
        for (s in 0..3) {
            add(3 - s to s); add(4 + s to s)
            add(s to 3 + s); add(7 - s to 3 + s)
        }
        add(0 to 3); add(0 to 4); add(7 to 3); add(7 to 4)
    }.toList()

    /** 25 — Cuatro "V" apuntando al centro desde los 4 bordes. 32 celdas. */
    private fun s25() = buildSet<Pair<Int, Int>> {
        for (i in 0..3) {
            add(i to i); add(7 - i to i)                 // top V
            add(i to 7 - i); add(7 - i to 7 - i)         // bottom V
        }
    }.toList()

    /** 26 — Chevrones horizontales enfrentados. 28 celdas. */
    private fun s26() = buildSet<Pair<Int, Int>> {
        for (i in 0..3) {
            add(i to 3 - i); add(i to 4 + i)             // left chevron opening right
            add(7 - i to 3 - i); add(7 - i to 4 + i)     // right chevron opening left
        }
    }.toList()

    /** 27 — Cruz de San Andrés: diagonales completas 2-wide. 28 celdas. */
    private fun s27() = buildSet<Pair<Int, Int>> {
        for (i in 0..7) {
            add(i to i)
            if (i < 7) add((i + 1) to i)
            add((7 - i) to i)
            if (i > 0) add((7 - i + 1).coerceAtMost(7) to i)
        }
    }.toList()

    /** 28 — Cuatro flechas apuntando hacia el centro. 32 celdas. */
    private fun s28() = buildSet<Pair<Int, Int>> {
        // Flecha arriba (↓)
        add(3 to 0); add(4 to 0)
        for (y in 1..3) { add(3 to y); add(4 to y) }
        add(2 to 1); add(5 to 1)
        // Flecha abajo (↑)
        add(3 to 7); add(4 to 7)
        for (y in 4..6) { add(3 to y); add(4 to y) }
        add(2 to 6); add(5 to 6)
        // Flecha izquierda (→)
        add(0 to 3); add(0 to 4)
        for (x in 1..3) { add(x to 3); add(x to 4) }
        add(1 to 2); add(1 to 5)
        // Flecha derecha (←)
        add(7 to 3); add(7 to 4)
        for (x in 4..6) { add(x to 3); add(x to 4) }
        add(6 to 2); add(6 to 5)
    }.toList()

    /** 29 — Cuatro flechas saliendo del centro. 32 celdas. */
    private fun s29() = buildSet<Pair<Int, Int>> {
        for (x in 0..7) { add(x to 3); add(x to 4) }
        for (y in 0..7) { add(3 to y); add(4 to y) }
        // puntas de flecha
        add(1 to 1); add(6 to 1); add(1 to 6); add(6 to 6)
    }.toList()

    /** 30 — Letra "F": barra izq + top + mid (sin bottom). 26 celdas. */
    private fun s30() = buildSet<Pair<Int, Int>> {
        for (y in 0..7) { add(1 to y); add(2 to y) }
        for (x in 3..7) { add(x to 0); add(x to 1) }
        for (x in 3..6) { add(x to 3); add(x to 4) }
    }.toList()

    // ══════════════════════════════════════════════════════════════════════════
    //  GRUPO D — Dígitos 7-segmento (31-40): ~14-40 celdas
    // ══════════════════════════════════════════════════════════════════════════

    /** 31 — Dígito "1": tallo + serifa + base. 18 celdas. */
    private fun s31() = buildSet<Pair<Int, Int>> {
        add(2 to 0)
        for (y in 0..7) { add(3 to y); add(4 to y) }
        for (x in 1..6) add(x to 7)
    }.toList()

    /** 32 — Dígito "7": top + STR + SBR + gancho. 20 celdas. */
    private fun s32() = buildSet<Pair<Int, Int>> {
        addAll(ST); addAll(STR); addAll(SBR)
        add(3 to 4); add(2 to 5)
    }.toList()

    /** 33 — Dígito "4": TL + TR + mid + BR. 24 celdas. */
    private fun s33() = seg(STL, STR, SM, SBR)

    /** 34 — Dígito "2": top + TR + mid + BL + bot. 30 celdas. */
    private fun s34() = seg(ST, STR, SM, SBL, SB)

    /** 35 — Dígito "3": top + TR + mid + BR + bot. 30 celdas. */
    private fun s35() = seg(ST, STR, SM, SBR, SB)

    /** 36 — Dígito "5": top + TL + mid + BR + bot. 30 celdas. */
    private fun s36() = seg(ST, STL, SM, SBR, SB)

    /** 37 — Dígito "6": top + TL + mid + BL + BR + bot. 36 celdas. */
    private fun s37() = seg(ST, STL, SM, SBL, SBR, SB)

    /** 38 — Dígito "9": top + TL + TR + mid + BR + bot. 36 celdas. */
    private fun s38() = seg(ST, STL, STR, SM, SBR, SB)

    /** 39 — Dígito "0": todos menos mid + diagonal interior. 34 celdas. */
    private fun s39() = buildSet<Pair<Int, Int>> {
        addAll(seg(ST, STL, STR, SBL, SBR, SB))
        add(2 to 2); add(3 to 3); add(4 to 5); add(5 to 6)
    }.toList()

    /** 40 — Dígito "8": los 7 segmentos completos. 40 celdas. */
    private fun s40() = seg(ST, STL, STR, SM, SBL, SBR, SB)

    // ══════════════════════════════════════════════════════════════════════════
    //  GRUPO E — Medio (41-50): geométricas, ~28-40 celdas
    // ══════════════════════════════════════════════════════════════════════════

    /** 41 — Letra "A" con travesaño. 30 celdas. */
    private fun s41() = buildSet<Pair<Int, Int>> {
        add(3 to 0); add(4 to 0)
        add(2 to 1); add(3 to 1); add(4 to 1); add(5 to 1)
        add(1 to 2); add(2 to 2); add(5 to 2); add(6 to 2)
        for (y in 3..7) { add(1 to y); add(2 to y); add(5 to y); add(6 to y) }
        for (x in 3..4) { add(x to 4) }
    }.toList()

    /** 42 — Reloj de arena: dos triángulos vértice a vértice. 36 celdas. */
    private fun s42() = buildSet<Pair<Int, Int>> {
        for (i in 0..3) {
            for (x in i..(7 - i)) add(x to i)
        }
        for (i in 0..3) {
            for (x in i..(7 - i)) add(x to (7 - i))
        }
    }.toList()

    /** 43 — Rombo/diamante: ancho en el centro, estrecho en los extremos. 40 celdas. */
    private fun s43() = buildSet<Pair<Int, Int>> {
        for (i in 0..3) {
            for (x in (3 - i)..(4 + i)) { add(x to i); add(x to (7 - i)) }
        }
    }.toList()

    /** 44 — Zigzag horizontal doble simétrico. 24 celdas. */
    private fun s44() = buildSet<Pair<Int, Int>> {
        val ys = listOf(0, 2, 4, 6, 4, 2, 0, 2)
        ys.forEachIndexed { x, y -> add(x to y); add(x to (7 - y)) }
    }.toList()

    /** 45 — Cuatro "U" en los bordes formando marco cóncavo. 36 celdas. */
    private fun s45() = buildSet<Pair<Int, Int>> {
        for (x in 2..5) { add(x to 0); add(x to 1) }
        add(2 to 2); add(3 to 2); add(4 to 2); add(5 to 2)
        for (x in 2..5) { add(x to 6); add(x to 7) }
        add(2 to 5); add(3 to 5); add(4 to 5); add(5 to 5)
        for (y in 2..5) { add(0 to y); add(1 to y) }
        add(2 to 2); add(2 to 3); add(2 to 4); add(2 to 5)
        for (y in 2..5) { add(6 to y); add(7 to y) }
        add(5 to 2); add(5 to 3); add(5 to 4); add(5 to 5)
    }.toList()

    /** 46 — Número "8" 7-segmento grande + marco exterior. 44 celdas. */
    private fun s46() = buildSet<Pair<Int, Int>> {
        addAll(seg(ST, STL, STR, SM, SBL, SBR, SB))
        add(0 to 0); add(7 to 0); add(0 to 4); add(7 to 4); add(0 to 7); add(7 to 7)
    }.toList()

    /** 47 — "+" 2-wide ocupando todo el tablero. 28 celdas. */
    private fun s47() = buildSet<Pair<Int, Int>> {
        for (x in 0..7) { add(x to 3); add(x to 4) }
        for (y in 0..7) { add(3 to y); add(4 to y) }
    }.toList()

    /** 48 — Cuatro "L" en espejo en las esquinas. 40 celdas. */
    private fun s48() = buildSet<Pair<Int, Int>> {
        // TL
        for (y in 0..3) { add(0 to y); add(1 to y) }
        add(2 to 3); add(3 to 3)
        // TR
        for (y in 0..3) { add(6 to y); add(7 to y) }
        add(4 to 3); add(5 to 3)
        // BL
        for (y in 4..7) { add(0 to y); add(1 to y) }
        add(2 to 4); add(3 to 4)
        // BR
        for (y in 4..7) { add(6 to y); add(7 to y) }
        add(4 to 4); add(5 to 4)
    }.toList()

    /** 49 — Cuatro bloques 3×3 en las esquinas. 36 celdas. */
    private fun s49(): List<Pair<Int, Int>> =
        fill(0, 0, 2, 2) + fill(5, 0, 7, 2) + fill(0, 5, 2, 7) + fill(5, 5, 7, 7)

    /** 50 — Letra "M" completa. 30 celdas. */
    private fun s50() = buildSet<Pair<Int, Int>> {
        for (y in 0..7) { add(0 to y); add(1 to y); add(6 to y); add(7 to y) }
        add(2 to 1); add(3 to 2); add(4 to 2); add(5 to 1)
    }.toList()

    // ══════════════════════════════════════════════════════════════════════════
    //  GRUPO F — Medio-fuerte (51-60): anillos y patrones, ~32-48 celdas
    // ══════════════════════════════════════════════════════════════════════════

    /** 51 — Marco 8×8 + marco interior 4×4 + núcleo 2×2. 36 celdas. */
    private fun s51() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        addAll(box(2, 2, 5, 5))
        add(3 to 3); add(4 to 3); add(3 to 4); add(4 to 4)
    }.toList()

    /** 52 — Dos marcos concéntricos (sin núcleo). 40 celdas. */
    private fun s52() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        addAll(box(2, 2, 5, 5))
    }.toList()

    /** 53 — Cruz griega 3-wide: brazos más anchos que s47. 40 celdas. */
    private fun s53() = buildSet<Pair<Int, Int>> {
        for (x in 0..7) { add(x to 2); add(x to 3); add(x to 4); add(x to 5) }
        for (y in 0..1) { add(2 to y); add(3 to y); add(4 to y); add(5 to y) }
        for (y in 6..7) { add(2 to y); add(3 to y); add(4 to y); add(5 to y) }
    }.toList()

    /** 54 — Anillo oval interior 6×6, borde 1-wide. 24 celdas. */
    private fun s54() = buildSet<Pair<Int, Int>> {
        for (x in 2..5) { add(x to 1); add(x to 6) }
        for (y in 2..5) { add(1 to y); add(6 to y) }
        add(2 to 2); add(5 to 2); add(2 to 5); add(5 to 5)
    }.toList()

    /** 55 — Marco exterior completo 1-wide. 28 celdas. */
    private fun s55() = box(0, 0, 7, 7)

    /** 56 — Marco exterior + interior + núcleo sólido. 40 celdas. */
    private fun s56() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        addAll(box(2, 2, 5, 5))
        addAll(fill(3, 3, 4, 4))
    }.toList()

    /** 57 — "8" 7-seg + extensiones en bordes exteriores. 44 celdas. */
    private fun s57() = buildSet<Pair<Int, Int>> {
        addAll(seg(ST, STL, STR, SM, SBL, SBR, SB))
        for (x in 0..7) { add(x to 0); add(x to 7) }
        add(0 to 4); add(7 to 4)
    }.toList()

    /** 58 — Cuatro bloques 2×2 esquina + marco interior. 32 celdas. */
    private fun s58() = buildSet<Pair<Int, Int>> {
        fill(0, 0, 1, 1).forEach(::add)
        fill(6, 0, 7, 1).forEach(::add)
        fill(0, 6, 1, 7).forEach(::add)
        fill(6, 6, 7, 7).forEach(::add)
        addAll(box(2, 2, 5, 5))
    }.toList()

    /** 59 — Marco exterior + cruz interior 2-wide. 48 celdas. */
    private fun s59() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        for (x in 1..6) { add(x to 3); add(x to 4) }
        for (y in 1..6) { add(3 to y); add(4 to y) }
    }.toList()

    /** 60 — Escalera sólida: triángulo recto en esquina superior-izquierda. 36 celdas. */
    private fun s60() = buildSet<Pair<Int, Int>> {
        for (i in 0..7) for (x in 0..(7 - i)) add(x to i)
    }.toList()

    // ══════════════════════════════════════════════════════════════════════════
    //  GRUPO G — Fuerte (61-75): densos y mixtos, ~40-52 celdas
    // ══════════════════════════════════════════════════════════════════════════

    /** 61 — Cuatro bloques 3×3 separados por cruz vacía. 36 celdas. */
    private fun s61(): List<Pair<Int, Int>> =
        fill(0, 0, 2, 2) + fill(5, 0, 7, 2) + fill(0, 5, 2, 7) + fill(5, 5, 7, 7)

    /** 62 — Octágono: tablero lleno sin las 3 celdas de cada esquina. 52 celdas. */
    private fun s62(): List<Pair<Int, Int>> {
        val cut = setOf(0 to 0, 1 to 0, 0 to 1, 6 to 0, 7 to 0, 7 to 1,
                        0 to 6, 0 to 7, 1 to 7, 7 to 6, 6 to 7, 7 to 7)
        return fill(0, 0, 7, 7).filter { it !in cut }
    }

    /** 63 — Columnas pares: franjas verticales alternadas. 40 celdas. */
    private fun s63(): List<Pair<Int, Int>> =
        (0..7).filter { it % 2 == 0 }.flatMap { x -> (0..7).map { y -> x to y } }

    /** 64 — Filas pares: franjas horizontales alternadas. 40 celdas. */
    private fun s64(): List<Pair<Int, Int>> =
        (0..7).filter { it % 2 == 0 }.flatMap { y -> (0..7).map { x -> x to y } }

    /** 65 — Bandas de 2 filas alternas llenas. 48 celdas. */
    private fun s65(): List<Pair<Int, Int>> =
        (0..7).filter { it % 4 < 2 }.flatMap { y -> (0..7).map { x -> x to y } }

    /** 66 — Tablero ajedrez 4×4: cuadrantes alternos. 32 celdas. */
    private fun s66() = buildSet<Pair<Int, Int>> {
        for (y in 0..7) for (x in 0..7) {
            if ((x / 4 + y / 4) % 2 == 0) add(x to y)
        }
    }.toList()

    /** 67 — Diagonales paralelas separadas +3. 40 celdas. */
    private fun s67() = buildSet<Pair<Int, Int>> {
        for (offset in listOf(0, 3, 6, 1)) {
            for (i in 0..7) add((i + offset) % 8 to i)
        }
    }.toList()

    /** 68 — Mosaico de "X" en los cuatro cuadrantes 4×4. 48 celdas. */
    private fun s68() = buildSet<Pair<Int, Int>> {
        for (qx in 0..1) for (qy in 0..1) {
            val ox = qx * 4; val oy = qy * 4
            for (i in 0..3) {
                add((ox + i) to (oy + i))
                add((ox + 3 - i) to (oy + i))
            }
        }
    }.toList()

    /** 69 — "H" en cada cuadrante 4×4: dos columnas externas + travesaño. 48 celdas. */
    private fun s69() = buildSet<Pair<Int, Int>> {
        for (qx in 0..1) for (qy in 0..1) {
            val ox = qx * 4; val oy = qy * 4
            for (y in 0..3) { add(ox to oy + y); add((ox + 3) to oy + y) }  // legs
            add((ox + 1) to (oy + 1)); add((ox + 2) to (oy + 1))             // crossbar top
            add((ox + 1) to (oy + 2)); add((ox + 2) to (oy + 2))             // crossbar bot
        }
    }.toList()

    /** 70 — Estrella 8 puntas: núcleo 4×4 + brazos cardinales + puntas diagonales. 44 celdas. */
    private fun s70() = buildSet<Pair<Int, Int>> {
        fill(2, 2, 5, 5).forEach(::add)
        for (y in 0..1) { add(3 to y); add(4 to y) }
        for (y in 6..7) { add(3 to y); add(4 to y) }
        for (x in 0..1) { add(x to 3); add(x to 4) }
        for (x in 6..7) { add(x to 3); add(x to 4) }
        add(0 to 0); add(1 to 1); add(6 to 1); add(7 to 0)
        add(0 to 7); add(1 to 6); add(6 to 6); add(7 to 7)
    }.toList()

    /** 71 — Cruz céltica: marco exterior + marco interior + ejes 2-wide. 52 celdas. */
    private fun s71() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        addAll(box(2, 2, 5, 5))
        for (x in 0..7) { add(x to 3); add(x to 4) }
        for (y in 0..7) { add(3 to y); add(4 to y) }
    }.toList()

    /** 72 — Marco exterior + interior + diagonales completas. 52 celdas. */
    private fun s72() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        addAll(box(2, 2, 5, 5))
        for (i in 0..7) { add(i to i); add(7 - i to i) }
    }.toList()

    /** 73 — Laberinto simétrico: espirales desde las 4 esquinas. 48 celdas. */
    private fun s73() = buildSet<Pair<Int, Int>> {
        for (i in 0..3) {
            add(i to 0); add(0 to i)
            add(7 - i to 0); add(7 to i)
            add(0 to 7 - i); add(i to 7)
            add(7 to 7 - i); add(7 - i to 7)
            add(i to 7 - i); add(7 - i to i)
        }
    }.toList()

    /** 74 — Cruces en cada cuadrante 4×4. 40 celdas. */
    private fun s74() = buildSet<Pair<Int, Int>> {
        for (qx in 0..1) for (qy in 0..1) {
            val cx = qx * 4 + 1; val cy = qy * 4 + 1
            for (x in qx * 4..(qx * 4 + 3)) { add(x to cy); add(x to cy + 1) }
            for (y in qy * 4..(qy * 4 + 3)) { add(cx to y); add(cx + 1 to y) }
        }
    }.toList()

    /** 75 — Tablero ajedrez 2×2: bloques 2×2 alternados. 32 celdas. */
    private fun s75() = buildSet<Pair<Int, Int>> {
        for (y in 0..7) for (x in 0..7) {
            if ((x / 2 + y / 2) % 2 == 0) add(x to y)
        }
    }.toList()

    // ══════════════════════════════════════════════════════════════════════════
    //  GRUPO H — Muy fuerte (76-85): capas múltiples, ~50-58 celdas
    // ══════════════════════════════════════════════════════════════════════════

    /** 76 — Esquinas "X" + ejes centrales + marco exterior. 56 celdas. */
    private fun s76() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        for (x in 0..7) { add(x to 3); add(x to 4) }
        for (y in 0..7) { add(3 to y); add(4 to y) }
        for (i in 0..2) { add(i to i); add(7 - i to i); add(i to 7 - i); add(7 - i to 7 - i) }
    }.toList()

    /** 77 — Marco exterior + interior + núcleo + diagonales de esquinas. 52 celdas. */
    private fun s77() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        addAll(box(2, 2, 5, 5))
        addAll(box(3, 3, 4, 4))
        for (i in 1..2) { add(i to i); add(7 - i to i); add(i to 7 - i); add(7 - i to 7 - i) }
    }.toList()

    /** 78 — Diagonales "X" completas + bordes horizontales y verticales. 52 celdas. */
    private fun s78() = buildSet<Pair<Int, Int>> {
        for (i in 0..7) { add(i to i); add(7 - i to i) }
        for (x in 0..7) { add(x to 0); add(x to 7) }
        for (y in 0..7) { add(0 to y); add(7 to y) }
    }.toList()

    /** 79 — Diagonales + marco exterior + puntos flotantes centrales. 52 celdas. */
    private fun s79() = buildSet<Pair<Int, Int>> {
        for (i in 0..7) { add(i to i); add(7 - i to i) }
        addAll(box(0, 0, 7, 7))
        add(1 to 1); add(6 to 1); add(1 to 6); add(6 to 6)
    }.toList()

    /** 80 — Tablero ajedrez clásico: celdas (x+y) par. 32 celdas. */
    private fun s80() = buildSet<Pair<Int, Int>> {
        for (y in 0..7) for (x in 0..7) if ((x + y) % 2 == 0) add(x to y)
    }.toList()

    // ══════════════════════════════════════════════════════════════════════════
    //  GRUPO I — Extremo (81-90): casi lleno, ~52-60 celdas
    // ══════════════════════════════════════════════════════════════════════════

    /** 81 — Tablero ajedrez invertido: celdas (x+y) impar. 32 celdas. */
    private fun s81() = buildSet<Pair<Int, Int>> {
        for (y in 0..7) for (x in 0..7) if ((x + y) % 2 == 1) add(x to y)
    }.toList()

    /** 82 — Anillo interior + ejes + puntas esquina. 52 celdas. */
    private fun s82() = buildSet<Pair<Int, Int>> {
        addAll(box(1, 1, 6, 6))
        for (x in 0..7) { add(x to 3); add(x to 4) }
        for (y in 0..7) { add(3 to y); add(4 to y) }
        for (i in 0..1) { add(i to i); add(7 - i to i); add(i to 7 - i); add(7 - i to 7 - i) }
    }.toList()

    /** 83 — Rombo doble: externo + interno + ejes cardinales. 56 celdas. */
    private fun s83() = buildSet<Pair<Int, Int>> {
        for (s in 0..3) { add(3 - s to s); add(4 + s to s); add(s to 3 + s); add(7 - s to 3 + s) }
        add(0 to 3); add(0 to 4); add(7 to 3); add(7 to 4)
        for (s in 0..1) { add(3 - s to s + 2); add(4 + s to s + 2); add(s + 2 to 3 + s); add(5 - s to 3 + s) }
        fill(3, 3, 4, 4).forEach(::add)
        for (x in 0..7) { add(x to 3); add(x to 4) }
        for (y in 0..7) { add(3 to y); add(4 to y) }
    }.toList()

    /** 84 — Cuadrante repetido ×4 en espejo: patrón 15 puntos por cuadrante. 56 celdas. */
    private fun s84() = buildSet<Pair<Int, Int>> {
        val q = listOf(
            0 to 0, 1 to 0, 2 to 0, 3 to 0,
            0 to 1, 3 to 1,
            0 to 2, 1 to 2, 2 to 2, 3 to 2,
            0 to 3, 3 to 3,
            1 to 1, 2 to 1, 1 to 3
        )
        for ((qx, qy) in q) {
            add(qx to qy); add(7 - qx to qy)
            add(qx to 7 - qy); add(7 - qx to 7 - qy)
        }
    }.toList()

    /** 85 — Estrella 8 puntas 2-wide: diagonales + ejes. 52 celdas. */
    private fun s85() = buildSet<Pair<Int, Int>> {
        for (i in 0..7) { add(i to i); add(7 - i to i) }
        for (x in 0..7) { add(x to 3); add(x to 4) }
        for (y in 0..7) { add(3 to y); add(4 to y) }
    }.toList()

    /** 86 — Marco + 4 T interiores apuntando al centro. 56 celdas. */
    private fun s86() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        for (x in 2..5) add(x to 1)
        for (y in 1..3) { add(3 to y); add(4 to y) }
        for (x in 2..5) add(x to 6)
        for (y in 4..6) { add(3 to y); add(4 to y) }
        for (y in 2..5) add(1 to y)
        for (x in 1..3) { add(x to 3); add(x to 4) }
        for (y in 2..5) add(6 to y)
        for (x in 4..6) { add(x to 3); add(x to 4) }
    }.toList()

    /** 87 — Patrón complejo: tablero casi lleno con patrón periódico. 56 celdas. */
    private fun s87() = buildSet<Pair<Int, Int>> {
        for (i in 0..7) { add(i to i); add(7 - i to i) }
        for (y in 0..7) for (x in 0..7) if ((x + y) % 4 < 2) add(x to y)
        for (x in 0..7) { add(x to 3); add(x to 4) }
    }.toList()

    /** 88 — Marco + cruces en 4 lados + eje central. 56 celdas. */
    private fun s88() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        for (x in 2..5) { add(x to 2); add(x to 5) }
        add(3 to 1); add(4 to 1); add(3 to 6); add(4 to 6)
        for (y in 2..5) { add(2 to y); add(5 to y) }
        add(1 to 3); add(1 to 4); add(6 to 3); add(6 to 4)
        for (x in 0..7) { add(x to 3); add(x to 4) }
        for (y in 0..7) { add(3 to y); add(4 to y) }
    }.toList()

    /** 89 — Diana: marcos concéntricos + ejes. 52 celdas. */
    private fun s89() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        addAll(box(2, 2, 5, 5))
        addAll(fill(3, 3, 4, 4))
        for (x in 0..7) { add(x to 3); add(x to 4) }
        for (y in 0..7) { add(3 to y); add(4 to y) }
    }.toList()

    /** 90 — Pirámide + base M: triángulo superior + estructura inferior. 56 celdas. */
    private fun s90() = buildSet<Pair<Int, Int>> {
        for (i in 0..3) for (x in i..(7 - i)) add(x to i)
        for (y in 4..7) { add(0 to y); add(1 to y); add(6 to y); add(7 to y) }
        add(2 to 5); add(3 to 6); add(4 to 6); add(5 to 5)
        for (x in 0..7) add(x to 7)
    }.toList()

    // ══════════════════════════════════════════════════════════════════════════
    //  GRUPO J — Máximo (91-100): 58-64 celdas
    // ══════════════════════════════════════════════════════════════════════════

    /** 91 — Copo de nieve: ejes + diagonales + brazos secundarios. 56 celdas. */
    private fun s91() = buildSet<Pair<Int, Int>> {
        for (x in 0..7) { add(x to 3); add(x to 4) }
        for (y in 0..7) { add(3 to y); add(4 to y) }
        for (i in 0..7) { add(i to i); add(7 - i to i) }
        for (x in 0..7) { add(x to 1); add(x to 6) }
        for (y in 0..7) { add(1 to y); add(6 to y) }
    }.toList()

    /** 92 — Mandala: anillo externo + ejes + diagonales + anillo interno + centro. 58 celdas. */
    private fun s92() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        for (x in 0..7) { add(x to 3); add(x to 4) }
        for (y in 0..7) { add(3 to y); add(4 to y) }
        for (i in 0..7) { add(i to i); add(7 - i to i) }
        addAll(box(2, 2, 5, 5))
        addAll(fill(3, 3, 4, 4))
    }.toList()

    /** 93 — Laberinto: 3 marcos + ejes + diagonales. 60 celdas. */
    private fun s93() = buildSet<Pair<Int, Int>> {
        addAll(box(0, 0, 7, 7))
        addAll(box(2, 2, 5, 5))
        addAll(box(3, 3, 4, 4))
        for (x in 0..7) { add(x to 3); add(x to 4) }
        for (y in 0..7) { add(3 to y); add(4 to y) }
        for (i in 0..7) { add(i to i); add(7 - i to i) }
    }.toList()

    /** 94 — Tablero casi lleno: solo se respiran 4 celdas internas aisladas. 60 celdas. */
    private fun s94(): List<Pair<Int, Int>> {
        val gaps = setOf(2 to 2, 5 to 2, 2 to 5, 5 to 5)
        return fill(0, 0, 7, 7).filter { it !in gaps }
    }

    /** 95 — Mosaico: filas alternas llenas y semi-llenas. 56 celdas. */
    private fun s95() = buildSet<Pair<Int, Int>> {
        for (y in 0..7) for (x in 0..7) {
            if (y % 4 < 2 || x % 4 < 2) add(x to y)
        }
    }.toList()

    /** 96 — Tablero lleno menos las 4 esquinas simples. 60 celdas. */
    private fun s96(): List<Pair<Int, Int>> {
        val corners = setOf(0 to 0, 7 to 0, 0 to 7, 7 to 7)
        return fill(0, 0, 7, 7).filter { it !in corners }
    }

    /** 97 — Tablero lleno menos la diagonal secundaria. 57 celdas. */
    private fun s97(): List<Pair<Int, Int>> {
        val anti = (0..7).map { i -> (7 - i) to i }.toSet()
        return fill(0, 0, 7, 7).filter { it !in anti }
    }

    /** 98 — Tablero lleno menos el eje central vertical. 56 celdas. */
    private fun s98(): List<Pair<Int, Int>> {
        val center = (0..7).flatMap { y -> listOf(3 to y, 4 to y) }.toSet()
        return fill(0, 0, 7, 7).filter { it !in center }
    }

    /** 99 — Tablero lleno menos la diagonal principal. 56 celdas. */
    private fun s99(): List<Pair<Int, Int>> {
        val diag = (0..7).map { i -> i to i }.toSet()
        return fill(0, 0, 7, 7).filter { it !in diag }
    }

    /** 100 — Bloque monolítico: las 64 celdas. Simetría total. */
    private fun s100() = fill(0, 0, 7, 7)
}
