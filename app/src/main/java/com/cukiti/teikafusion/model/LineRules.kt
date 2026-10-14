package com.cukiti.teikafusion.model

/**
 * **FAMILIA C DE LAS REGLAS DE LA CASA: LO QUE HACE LA LÍNEA AL ROMPERSE** (2026-09-04, aprobada por
 * el usuario en el panel del catálogo).
 *
 * Es la familia más potente del catálogo y también la de más riesgo, y las dos cosas por el mismo
 * motivo: tocan `resolveLineClearPass`, **el único motor vivo del juego**. Todo lo demás —los
 * biomas, las habilidades, las otras tres familias— pasa por ahí. Por eso las cuatro se escriben
 * como reglas PURAS que solo responden preguntas, y el motor las consulta en los cuatro puntos
 * exactos donde ya tomaba esas decisiones; ninguna abre un camino nuevo dentro del resolutor.
 *
 * ## La disciplina que comparten
 *
 * **Ninguna cambia qué es una línea completa.** Actúan DESPUÉS de que el motor haya decidido qué se
 * limpia: la reacción en cadena añade estallidos, el huevo se agrieta, el eco añade puntos y el
 * rebote empuja los cuerpos. (La cruz y la cosecha doble se retiraron el 2026-09-22.) Mantenerlas fuera de la detección es lo que impide que interactúen
 * con las seis cosas que ya la condicionan —bobinas de la Tormenta, cúmulo galáctico, corte del Dojo,
 * luna en vuelo, cristal y testarudos— y que cualquiera de ellas se convierta en un bucle infinito.
 *
 * La quinta de la familia, **UN SOLO EJE**, era la excepción: sí tocaba la detección, porque existía
 * justo para eso. Se retiró el **2026-09-05** a petición del usuario y la sustituyó [LineRecoil] —
 * ver el KDoc de esta última. Con ella se fue la última rama del resolutor que separaba «lo que se
 * limpia» de «lo que cuenta», así que `scoringSegs`/`scoringCols` volvieron a ser `clearSegs`/`fullCols`.
 */

/* (RETIRADO 2026-09-22) `CrossClear` — LA CRUZ (P-149). La sustituye REACCIÓN EN CADENA (P-234):
 * el barrido fijo del centro pasa a ser un estallido donde el jugador haya dejado sus barriles.
 * Ver [ChainReaction]. */

/* (RETIRADO 2026-09-22) `DoubleHarvest` — LA COSECHA DOBLE (P-150). La sustituye EL HUEVO (P-235):
 * en vez de repartir cuerpos donde caen las líneas, el cuerpo nace donde el jugador lo espera.
 * Ver [TeikaEgg]. */

/* (RETIRADO 2026-09-22) `LineEcho` — EL ECO (P-151). Lo sustituye LA IMPLOSIÓN (P-236), justo
 * debajo. */

/**
 * **P-236 · LA IMPLOSIÓN** (2026-09-22, elegida por el usuario para sustituir a EL ECO).
 *
 * La línea que se rompe **aspira**: la madera de las dos líneas vecinas da **un paso** hacia el
 * hueco que ha dejado. Si al entrar completa otra línea, esa también se rompe y vuelve a aspirar —
 * una sola jugada puede desmoronar medio tablero en cascada.
 *
 * El Eco premiaba el ORDEN en que rompías con puntos que nadie veía. La implosión premia lo mismo
 * con algo que se ve: dejar dos líneas vecinas casi completas es preparar una cascada.
 *
 * ## Las reglas del paso
 *
 *  - **Un paso y solo uno**, hacia la línea rota, a una casilla que la rotura dejó libre.
 *  - En una fila entra primero **el de arriba**; si arriba no hay madera que pueda moverse, el de
 *    abajo. En una columna, primero el de la izquierda. Una casilla recibe un bloque, nunca dos.
 *  - Lo anclado no se mueve (muros, pozos, lava, congelado, cuerpos de Teika, bobinas…): son las
 *    mismas anclas que la gravedad, y por lo mismo.
 *
 * ## El dial
 *
 * En los primeros biomas aspira cualquier línea; en los últimos, solo una rotura de **dos o más
 * líneas a la vez**. La cascada hay que ganársela.
 */
object LineImplosion {

    const val MIN_LINES_FIRST_CHAPTER = 1
    const val MIN_LINES_LAST_CHAPTER = 2

    fun appliesTo(level: Int): Boolean = HouseRule.isActive(level, HouseRule.IMPLOSION)

    /** Cuántas líneas a la vez hacen falta para que aspire en [level]. */
    fun minLinesFor(level: Int): Int =
        HouseRule.dial(level, MIN_LINES_FIRST_CHAPTER, MIN_LINES_LAST_CHAPTER)
            .coerceIn(MIN_LINES_FIRST_CHAPTER, MIN_LINES_LAST_CHAPTER)

    /** ¿Aspira una rotura de [lines] líneas en [level]? */
    fun triggers(level: Int, lines: Int): Boolean = appliesTo(level) && lines >= minLinesFor(level)

    /** Un bloque aspirado: de [from] a [to], siempre una casilla. */
    data class Pull(val from: Position, val to: Position)

    /**
     * Los pasos que da la madera hacia las líneas [rows] y [cols] recién rotas.
     *
     * [wood] es la madera que QUEDA tras la rotura; [blocked] todo lo que ocupa una casilla y no se
     * mueve (anclas, cuerpos); una casilla vacía de la línea es la que no está en ninguno de los dos.
     */
    fun pulls(
        rows: Set<Int>,
        cols: Set<Int>,
        wood: Set<Position>,
        blocked: Set<Position>,
        width: Int = GameState.GRID_SIZE,
        height: Int = GameState.GRID_SIZE,
    ): List<Pull> {
        val out = ArrayList<Pull>()
        val moved = HashSet<Position>()
        val filled = HashSet<Position>()
        fun libre(p: Position) = p !in wood && p !in blocked && p !in filled
        fun movible(p: Position) =
            p.x in 0 until width && p.y in 0 until height && p in wood && p !in blocked && p !in moved
        fun tryPull(target: Position, sources: List<Position>) {
            if (!libre(target)) return
            val src = sources.firstOrNull { movible(it) } ?: return
            out += Pull(src, target)
            moved += src
            filled += target
        }
        rows.sorted().forEach { y ->
            for (x in 0 until width) {
                // Una vecina que es TAMBIÉN línea rota está vacía: no tiene nada que dar.
                val arriba = Position(x, y - 1).takeIf { y - 1 !in rows }
                val abajo = Position(x, y + 1).takeIf { y + 1 !in rows }
                tryPull(Position(x, y), listOfNotNull(arriba, abajo))
            }
        }
        cols.sorted().forEach { x ->
            for (y in 0 until height) {
                val izq = Position(x - 1, y).takeIf { x - 1 !in cols }
                val der = Position(x + 1, y).takeIf { x + 1 !in cols }
                tryPull(Position(x, y), listOfNotNull(izq, der))
            }
        }
        return out
    }
}

/**
 * **EL REBOTE** (2026-09-05, elegido por el usuario para sustituir a UN SOLO EJE).
 *
 * La línea que revienta **empuja a todos los Teikas** perpendicularmente y hacia fuera: rompe una
 * fila y los cuerpos de arriba salen hacia arriba y los de abajo hacia abajo; rompe una columna y
 * salen a los lados. El empujón cae con la distancia, así que la línea que eliges mueve sobre todo
 * lo que tenía cerca.
 *
 * ## Por qué esta y no otra
 *
 * Sustituye a **UN SOLO EJE** (P-152), que hacía lo contrario: quitarle sentido a la mitad de las
 * líneas. Funcionaba, pero el jugador solo podía notarla como una resta —rompo y no me pagan— y no
 * había forma de enseñarle la parte buena sin un rótulo. Esta se explica sola en el primer rebote.
 *
 * Y aprovecha lo único que este juego tiene y ningún otro block-blast: **los Teikas son cuerpos
 * físicos de verdad**. En un nivel del rebote la línea deja de ser solo puntuación y pasa a ser la
 * herramienta con la que COLOCAS los cuerpos — los juntas para forzar una fusión que no llegaba, los
 * sacas de la esquina donde se habían dormido, los empujas hacia la portería. Es la primera regla de
 * la casa que le da al jugador **poder** sobre la física en vez de pedirle que la sufra.
 *
 * ## Lo que deliberadamente NO hace
 *
 *  - **No descongela ni libera.** El rebote respeta los cuerpos `static`: el congelado del Glaciar
 *    sigue congelado y el raptado por la planta sigue raptado. Una regla de la casa que deshiciera
 *    la mecánica del bioma por la puerta de atrás sería la vuelta de `biomeSecondary` disfrazada.
 *  - **No toca el presupuesto de turnos.** Ni lo pide ni lo devuelve, que es la condición que mató a
 *    la primera versión de UN SOLO EJE: el presupuesto es la curva medida en el playtest
 *    (2026-08-01b) y ninguna regla lo infla por la puerta de atrás.
 *  - **No cambia qué es una línea completa.** Actúa después de que el motor decida qué se limpia,
 *    como las otras tres de la familia. Ver el KDoc del fichero.
 *
 * **El dial:** el impulso sube de bioma en bioma. En el Cristal es un empujoncito que reordena el
 * vecindario; en el Galáctico es una sacudida que reparte el tablero.
 */
object LineRecoil {

    /** Impulso en celdas/s a distancia cero. Ver [TeikaPhysicsWorld.recoilFromLine]. */
    const val SPEED_FIRST_CHAPTER = 6
    const val SPEED_LAST_CHAPTER = 13

    @Suppress("UNUSED_PARAMETER")
    fun appliesTo(level: Int): Boolean = false // Retired; pure helpers remain for compatibility tests.

    /**
     * Fuerza del empujón en [level]. Fuera de sus niveles, **0** — y ese cero es lo que apaga la
     * regla en el motor sin una sola rama nueva: el resolutor pide siempre la velocidad y no empuja
     * cuando vale cero, igual que `recoilFromLine` sale de vacío.
     */
    fun speedFor(level: Int): Float {
        if (!appliesTo(level)) return 0f
        return HouseRule.dial(level, SPEED_FIRST_CHAPTER, SPEED_LAST_CHAPTER)
            .coerceIn(SPEED_FIRST_CHAPTER, SPEED_LAST_CHAPTER).toFloat()
    }

    /** Distancia (en casillas, desde el BORDE del cuerpo) a la que el empujón ya no llega. */
    const val RANGE = 4f

    /**
     * **EL EMPUJÓN DE UN CUERPO** (P-237, 2026-09-22): con signo (− hacia arriba/izquierda, + hacia
     * abajo/derecha), para un cuerpo de radio [radius] cuyo centro está en [center] (en el eje
     * perpendicular a la línea) cuando revienta la línea de coordenada [lineCoord] con [speed].
     *
     * Es la **fuente única**: la usa el mundo físico al empujar y el aviso del arrastre al dibujar
     * las flechas — si fueran dos cuentas, la flecha prometería un empujón distinto del que llega
     * (el bug de «dos copias de un dibujo» que este repo ya pagó).
     *
     *  - Cae con la distancia desde el borde del cuerpo, no desde su centro: un MEGA no se lleva un
     *    empujón de lejos por ser grande.
     *  - Los grandes se mueven menos con el mismo empujón (masa ∝ área), con tope por los dos lados.
     *  - Un cuerpo justo encima de la línea sale hacia arriba: es de donde la madera dejó de
     *    sostenerlo.
     */
    fun impulseFor(center: Float, radius: Float, lineCoord: Float, speed: Float): Float {
        if (speed <= 0f) return 0f
        val fuera = if (center <= lineCoord) -1f else 1f
        val distancia = (kotlin.math.abs(center - lineCoord) - radius).coerceAtLeast(0f)
        val caida = (1f - distancia / RANGE).coerceIn(0f, 1f)
        if (caida <= 0f) return 0f
        val masa = (1f / kotlin.math.sqrt(radius * radius)).coerceIn(0.45f, 1.75f)
        return fuera * speed * caida * masa
    }
}
