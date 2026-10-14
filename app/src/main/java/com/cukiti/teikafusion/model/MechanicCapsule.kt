package com.cukiti.teikafusion.model

/**
 * **LAS CÁPSULAS** (2026-09-01, pedido del usuario: «que el jugador sepa qué hace cada cosa en el
 * tablero»).
 *
 * El tutorial enseña a JUGAR. Todo lo demás —las herramientas de ficha, las celdas marcadas, las
 * reglas de la casa, los catorce biomas— se enseña **la primera vez que aparece de verdad en una
 * partida**: el juego se ralentiza dos segundos sobre lo que está pasando, lo nombra en cuatro
 * palabras y sigue. Una vez por mecánica, en toda la vida de la partida.
 *
 * ## Por qué así y no en el tutorial
 *
 * Meter las veinticinco en el arranque serían quince lecciones que nadie termina, y encima el
 * jugador las olvidaría antes de encontrárselas. Enseñar en el momento del uso es lo que hace que se
 * recuerde: el ejemplo y la regla llegan juntos.
 *
 * ## La decisión delicada: los biomas
 *
 * El 2026-08-26b el usuario **retiró** el cartel que contaba la regla del bioma antes de jugar: era
 * mucho texto y la gracia es descubrirla. La cápsula de bioma **no lo reabre** y la diferencia
 * importa: no se cuenta ANTES, se muestra CUANDO PASA —el propio castigo, a cámara lenta, con una
 * línea de cuatro palabras—, así que el jugador entiende qué acaba de ocurrirle en vez de perder el
 * nivel sin saber por qué. Decisión del usuario del 2026-09-01: «sí, pero mostrando, no contando».
 *
 * ## Reglas de la casa que cumple por construcción
 *
 *  - **Una cápsula por turno como mucho.** Dos explicaciones seguidas dejan de explicar.
 *  - **Nunca en el tutorial.** Ahí ya hay lecciones; encadenarlas sería ruido sobre ruido.
 *  - **Nunca dos veces.** El «visto» se persiste ([PreferencesManager.MECHANICS_SEEN_KEY]).
 */
enum class MechanicCapsule(
    /** Id estable: **se persiste**, así que renombrarlo hace que la cápsula se vuelva a ver. */
    val id: String,
    /** Cuatro o cinco palabras. Nombra lo que está pasando; no lo explica. */
    val label: String,
    val focus: SlowMotionFocus,
    /** 1 = sin ralentizar (las de HUD y bandeja: ahí no hay física que ralentizar). */
    val factor: Float = 0.25f,
    /** 2,4 s: lo que cuesta leer cinco palabras en un móvil sin releerlas (ver [SlowMotionShot]). */
    val durationMs: Int = 2400,
) {
    DOMINO("wrecking_ball", "Limpia líneas: apunta y lanza bola", SlowMotionFocus.BOARD),
    CONSTELLATION("constellation", "Cubre los tres puntos luminosos", SlowMotionFocus.BOARD, factor = 1f),
    TRAMPOLINE("pinball", "Carga, apunta y lanza un Teika", SlowMotionFocus.BOARD, factor = 1f),
    PRECISION_CUT("compactor", "Carga, apunta y compacta bloques", SlowMotionFocus.BOARD, factor = 1f),
    // ── Reglas de la casa del nivel (P-75, P-79, P-80) ──────────────────────────────────────────
    GRAVITY("gravity", "Aquí la madera se asienta", SlowMotionFocus.BOARD),
    HORIZON("horizon", "Una ficha, y las que vienen", SlowMotionFocus.TRAY, factor = 1f),
    CURRENT("current", "El tablero se desliza solo", SlowMotionFocus.BOARD),

    // ── Familia A de las reglas de la casa (2026-09-03) ──────────────────────────────────────────
    // El rótulo tiene que decir HACIA DÓNDE, porque es lo único que las distingue de su hermana
    // fundacional y es exactamente la información sobre la que el jugador va a decidir su jugada.
    SIDE_GRAVITY("side_gravity", "La madera se pega al lado", SlowMotionFocus.BOARD),
    INVERTED_GRAVITY("inverted_gravity", "Aquí la madera cae hacia arriba", SlowMotionFocus.BOARD),
    VERTICAL_CURRENT("vertical_current", "La corriente empuja en vertical", SlowMotionFocus.BOARD),
    SLIDE("turbine", "Carga, apunta y empuja los Teikas", SlowMotionFocus.BOARD, factor = 1f),
    TIDE("tide", "La marea cambia de sentido", SlowMotionFocus.BOARD),

    // ── Familia B de las reglas de la casa (2026-09-03) ──────────────────────────────────────────
    // Todas miran a la BANDEJA (`factor = 1f`: ahí no hay física que ralentizar), salvo el espejo,
    // que es lo único de la familia que ocurre en el tablero.
    RESERVE("reserve", "Intercambia una ficha por bandeja", SlowMotionFocus.TRAY, factor = 1f),
    FORCED_QUEUE("forced_queue", "En orden: primero esa", SlowMotionFocus.TRAY, factor = 1f),
    INSTANT_REFILL("instant_refill", "Cada ficha se repone al momento", SlowMotionFocus.TRAY, factor = 1f),
    ROUTES("portal_swap", "Limpia una línea: intercambia zonas 2×2", SlowMotionFocus.BOARD, factor = 1f),
    // Señala las dos gemelas: «suelta una sobre la otra» sin decir cuáles no se podría hacer.
    MIRROR("mirror", "Tu ficha se refleja enfrente", SlowMotionFocus.BOARD),

    // ── Familia C de las reglas de la casa (2026-09-04) ──────────────────────────────────────────
    // Las cuatro miran al TABLERO: lo que cambian ocurre en el instante en que una línea revienta.
    // Manual Forja occupies the former Onda/Barril slot; teach it without slowing normal physics.
    SHOCKWAVE("forge", "Transforma cuatro maderas en un Teika", SlowMotionFocus.BOARD, factor = 1f),
    ELEVATOR("teika_claw", "Carga, recoge y traslada un Teika", SlowMotionFocus.BOARD, factor = 1f),
    IMPLOSION("implosion", "Romper aspira a las vecinas", SlowMotionFocus.BOARD),
    AVALANCHE("avalanche", "Limpia dos líneas: cae la madera", SlowMotionFocus.BOARD),

    // ── Familias D y E de las reglas de la casa (2026-09-04) ─────────────────────────────────────
    // Las de D se ralentizan (`factor` por defecto): lo que enseñan es MOVIMIENTO, y a cámara lenta
    // se ve. Las de E son marcas quietas y no hay física que ralentizar.
    BUBBLES("bubbles", "Nacen en pompas: rómpelas", SlowMotionFocus.BOARD),
    // (2026-09-22) Rework: ids NUEVOS a propósito — el id se persiste, y quien ya vio las cápsulas de
    // «pesan» y «se buscan» tiene que ver la regla nueva, que es otra.
    TRAIL("reactor_color", "Limpia líneas: detona grupos de color", SlowMotionFocus.BOARD, factor = 1f),
    // El imán es una FICHA: su cápsula mira a la bandeja y señala esa ficha (ver [trayPieceIdsFor]),
    // y sale cuando llega de verdad, no al abrir el nivel (ver [atRoundStart]).
    COMPASS("compass", "La gravedad gira: mira la brújula", SlowMotionFocus.BOARD),
    SURF("surf", "Limpia su apoyo: surfea con Teikas", SlowMotionFocus.BOARD, factor = 1f),
    PORTALS("portals", "Los portales unen las paredes", SlowMotionFocus.BOARD),
    COMET("orbit", "Carga y gira una zona 3×3", SlowMotionFocus.BOARD, factor = 1f),

    // ── Marcas del tablero (P-71, P-78, P-81) ───────────────────────────────────────────────────
    HOT_CELL("hot_cell", "Celda caliente: rómpela y paga", SlowMotionFocus.BOARD, factor = 1f),
    // (RETIRADO 2026-09-26) GOAL_CELL — la cápsula de LA PORTERÍA (P-81), borrada entera.

    // ── Herramientas de ficha (P-70, P-72, P-76, P-77) ──────────────────────────────────────────
    BRUSH("brush", "La brocha pinta fila y columna", SlowMotionFocus.TRAY, factor = 1f),
    CHAMELEON("chameleon", "Adopta el color que más toque", SlowMotionFocus.TRAY, factor = 1f),
    GROWING("growing", "Si no la usas, crece", SlowMotionFocus.TRAY, factor = 1f),
    RELAY("relay_discharge", "Carga tu ficha: apunta y barre", SlowMotionFocus.TRAY, factor = 1f),

    // ── Los CÍRCULOS: qué hace un Teika ahí parado ─────────────────────────────────────────
    // El tutorial enseña que NACEN de una línea y que se FUNDEN. Lo que no dice —y el jugador no
    // puede adivinar— es su doble papel en el tablero: estorban donde están y a la vez cuentan como
    // celda llena para cerrar una línea. Es la regla que une las dos mitades del juego.
    TEIKA_ROLE("teika_role", "El Teika también cierra líneas", SlowMotionFocus.BOARD),

    // (RETIRADO 2026-09-22, decisión del usuario) REQUEST — la cápsula de EL PEDIDO (P-82).

    // ── El comodín ────────────────────────────────────────────────────────────
    WILDCARD("wildcard", "Comodín: vale de cualquier color", SlowMotionFocus.TRAY, factor = 1f),

    // ── EL PREMIO DE LA BANDEJA (2026-09-26): tras una doble o triple línea, fichas que caben ────────
    // P-316 (2026-09-27): el premio vuelve a tocar el reparto — la bandeja trae fichas más grandes.
    TRAY_REWARD("tray_reward", "Doble línea: fichas más grandes", SlowMotionFocus.TRAY, factor = 1f),

    // ── EL BANCO DE BLOQUES (P-350, 2026-09-27): llega con el capítulo 2 y se enseña al abrir ──────
    BLOCK_BANK("block_bank", "Crea Fichona con tus fichas", SlowMotionFocus.BANK, factor = 1f),
    // P-356 (2026-09-27): guardar una ficha de N bloques deja N − 1 y gasta el turno (P-351). Sin
    // decirlo, el «+3» de una ficha de 4 se leía como un fallo. Sale la primera vez que se guarda.
    BANK_TOLL("bank_toll", "Guardar cuesta un bloque", SlowMotionFocus.BANK, factor = 1f),

    // ── EL GIRO (P-354, 2026-09-27): volvió el 2026-09-24 (P-276) y nada lo enseñaba ─────────────
    // Las lecciones lo tienen cerrado (comparan la forma canónica), así que el primer nivel de verdad
    // es el primer sitio donde se puede enseñar.
    ROTATE("rotate", "Toca una ficha para girarla", SlowMotionFocus.TRAY, factor = 1f),

    // ── LA PARADA (P-355, 2026-09-27): el contrajuego de P-353, que solo contaba la pista del bioma ─
    // Sale tras el primer golpe de un bioma con ciclo ([BiomeCycle.BIOMES]): el jugador acaba de ver
    // lo que cae, y esto le dice cómo pararlo la próxima vez.
    BIOME_PARRY("biome_parry", "Rompe lo marcado al caer: ¡parada!", SlowMotionFocus.BOARD, factor = 1f),

    // ── Recompensa de color (P-69) ──────────────────────────────────────────────────────────────
    PURE_LINE("pure_line", "Línea de un color: paga doble", SlowMotionFocus.BOARD),

    // ── Los catorce biomas: se muestran CUANDO GOLPEAN ──────────────────────────────────────────
    BIOME_CRYSTAL("biome_crystal", "El cristal aguanta dos limpiezas", SlowMotionFocus.BOARD),
    BIOME_SLEEP("biome_sleep", "Dormida: esa ficha no se juega", SlowMotionFocus.BOARD),
    BIOME_FEAR("biome_fear", "Los bloques huyen del susto", SlowMotionFocus.BOARD),
    BIOME_GARDEN("biome_garden", "La raíz se lleva tu Teika", SlowMotionFocus.BOARD),
    BIOME_STORM("biome_storm", "Enciende las bobinas en orden", SlowMotionFocus.BOARD),
    // (2026-09-02) Decía «La lava SUBE si no rompes» y la lava **baja**: `advanceLavaFromTop` la trae
    // desde el borde de arriba y `physicsTerrainCells` bloquea las filas `0 until lavaRow`. El propio
    // `mechanicHint` del bioma lo dice bien desde siempre («La lava baja sola…»). Un jugador que se
    // creyera la cápsula defendería el lado del tablero equivocado.
    // P-353: ya no se frena rompiendo cualquier fila; baja una vez por ciclo.
    BIOME_VOLCANO("biome_volcano", "La lava baja cada pocos turnos", SlowMotionFocus.BOARD),
    BIOME_NINJA("biome_ninja", "El corte parte el tablero", SlowMotionFocus.BOARD),
    // (2026-09-02) Decía «La escarcha congela lo que toca», y **la escarcha ya no existe**: el rework
    // de julio la retiró de la madera —`glacierFrostCells` se construye vacío y así se declara en el
    // propio tick del bioma— y dejó una sola cosa, la OLEADA DE FRÍO que congela **un Teika** dos
    // turnos. La cápsula nombraba una mecánica muerta y mandaba a mirar los bloques.
    BIOME_GLACIER("biome_glacier", "El frío congela a tus Teikas", SlowMotionFocus.BOARD),
    // El Espejo Embrujado (P-246, 2026-09-23) sustituye a la cacería: ya no roba, REFLEJA.
    BIOME_GHOST("biome_ghost", "El espejo refleja tus bloques", SlowMotionFocus.BOARD),
    BIOME_GLUTTON("biome_glutton", "El pozo se come el rincón", SlowMotionFocus.BOARD),
    BIOME_TOXIC("biome_toxic", "El veneno se extiende", SlowMotionFocus.BOARD),
    BIOME_STAR("biome_star", "El astro dispara a lo marcado", SlowMotionFocus.BOARD),
    // La espada del Rey (P-294, 2026-09-25) sustituye al Jaque.
    BIOME_KING("biome_king", "El Rey golpea", SlowMotionFocus.BOARD),
    // La lluvia de meteoritos (P-294, 2026-09-25) sustituye a la Brecha en el casco.
    BIOME_GALACTIC("biome_galactic", "El meteorito funde en roca", SlowMotionFocus.BOARD);

    /**
     * **¿Esto es un MOMENTO o algo que sigue ahí?** (P-96, 2026-09-02)
     *
     * Un momento —la línea pura, el golpe de un bioma— se explica en el turno en que pasa o no se
     * explica: enseñarlo dos turnos después sería señalar un tablero en el que ya no queda nada de
     * lo que se cuenta. El resto (las reglas del nivel, las marcas del tablero, las herramientas de
     * la bandeja) **sigue estando ahí** el turno siguiente, así que su cápsula puede esperar.
     *
     * Es lo que decide qué se encola cuando dos mecánicas nuevas caen en la misma tanda: los
     * momentos tienen prioridad y **no se guardan**; lo demás espera al cierre del turno siguiente.
     */
    val isMoment: Boolean
        get() = this == PURE_LINE || this == TRAY_REWARD || BiomeType.entries.any { forBiome(it) == this }

    /**
     * El rótulo, resuelto contra lo que ACABA de pasar. Hoy todas las cápsulas dicen siempre lo mismo
     * (la única que variaba era la cacería fantasma, retirada el 2026-09-23 con el Espejo Embrujado);
     * se conserva el punto de entrada para la próxima mecánica de dos caras.
     */
    @Suppress("UNUSED_PARAMETER")
    fun labelFor(state: GameState): String = label

    /** El instante de cámara lenta de esta cápsula, con su foco ya resuelto contra [state]. */
    fun shot(state: GameState): SlowMotionShot = SlowMotionShot(
        id = "capsule_$id",
        label = labelFor(state),
        focus = focus,
        cells = cellsFor(state),
        trayPieceIds = trayPieceIdsFor(state),
        factor = factor,
        durationMs = durationMs,
    )

    /**
     * **QUÉ FICHA SEÑALA** esta cápsula, o vacío si habla de la bandeja entera (P-201, 2026-09-05).
     *
     * Es el hermano de [cellsFor] para la bandeja, y sigue su misma regla: **si el rótulo dice
     * «esa», hay que decir cuál**. Media docena de estas cápsulas nombran una ficha concreta y el
     * foco iluminaba los tres cuadros; el jugador leía «Esa ficha viene tapada» buscando cuál de
     * las tres.
     *
     * Las dos que NO devuelven nada lo hacen a propósito, porque de verdad hablan del conjunto:
     * la bandeja de cinco (cuántos cuadros hay) y la reposición instantánea (cuándo se rellenan).
     */
    private fun trayPieceIdsFor(state: GameState): Set<String> {
        fun idsOf(predicate: (Piece) -> Boolean): Set<String> =
            state.availablePieces.filter(predicate).mapTo(HashSet()) { it.id }
        return when (this) {
            BRUSH -> idsOf { it.isBrush }
            CHAMELEON -> idsOf { it.isChameleon }
            GROWING -> idsOf { it.isGrowing }
            WILDCARD -> idsOf { it.isWildcard }
            // Reserva señala las fichas activas que el jugador puede intercambiar.
            RESERVE -> state.availablePieces.mapTo(HashSet()) { it.id }
            // «En orden: primero esa» — pues esa: la cabeza de la cola.
            FORCED_QUEUE -> state.availablePieces.firstOrNull()?.let { setOf(it.id) }.orEmpty()
            RELAY -> state.availablePieces.mapTo(HashSet()) { it.id }
            PRECISION_CUT -> emptySet()
            else -> emptySet()
        }
    }

    private fun cellsFor(state: GameState): Set<Position> = when (this) {
        HOT_CELL -> state.hotCells
        // El molde: sus casillas, que es donde hay que encajar la ficha.
        DOMINO -> state.grid.filterValues { it is CellContent.Wood }.keys
        CONSTELLATION -> state.constellationPoints.toSet()
        PRECISION_CUT -> state.grid.keys
        TRAMPOLINE -> state.grid.keys
        ELEVATOR -> setOf(ElevatorRules.platform(state.level))
        TRAIL -> state.grid.filterValues { it is CellContent.Wood }.keys
        // Los portales: sus bocas en las dos paredes.
        PORTALS -> Portals.rowsFor(state.level, state.placementsInLevel)
            ?.let { Portals.cellsOf(it, state.gridW).let { (a, b) -> a + b } }.orEmpty()
        // Deslizamiento enseña la limpieza y sus filas vecinas en el tablero completo.
        ROUTES -> state.grid.keys
        SLIDE -> emptySet()
        // Surf teaches support clearing across the board; Cometa highlights its charge node.
        SURF -> emptySet()
        COMET -> emptySet()
        // El resto ilumina el tablero entero: lo que enseñan no vive en tres celdas concretas.
        else -> emptySet()
    }

    companion object {
        /** La cápsula del bioma [biome], o `null` si ese bioma no tiene una (NONE). */
        fun forBiome(biome: BiomeType): MechanicCapsule? = when (biome) {
            BiomeType.CRYSTAL -> BIOME_CRYSTAL
            BiomeType.SLEEP -> BIOME_SLEEP
            BiomeType.FEAR -> BIOME_FEAR
            BiomeType.GARDEN -> BIOME_GARDEN
            BiomeType.STORM -> BIOME_STORM
            BiomeType.VOLCANO -> BIOME_VOLCANO
            BiomeType.NINJA -> BIOME_NINJA
            BiomeType.GLACIER -> BIOME_GLACIER
            BiomeType.GHOST -> BIOME_GHOST
            BiomeType.GLUTTON -> BIOME_GLUTTON
            BiomeType.TOXIC -> BIOME_TOXIC
            BiomeType.STAR -> BIOME_STAR
            BiomeType.KING -> BIOME_KING
            BiomeType.GALACTIC -> BIOME_GALACTIC
            BiomeType.NONE -> null
        }

        /**
         * Qué cápsulas pide el tablero **al abrirse la ronda**: las reglas de la casa y las marcas,
         * que están a la vista desde el turno 1 y sobre las que el jugador va a decidir su primera
         * jugada. Enseñarlas después sería tarde.
         */
        fun atRoundStart(
            state: GameState,
            /** ¿Tiene el jugador el banco? (de pago desde el 2026-09-28). Sin él no se enseña. */
            bankOwned: Boolean = true,
        ): List<MechanicCapsule> = buildList {
            if (TutorialCampaign.isTutorialLevel(state.level)) return@buildList
            // El GIRO primero (P-354): es la herramienta de TODOS los turnos, y el primer nivel de verdad
            // es donde el jugador lo necesita antes de su primera jugada.
            if (state.availablePieces.isNotEmpty()) add(ROTATE)
            // ── LA REGLA DE LA CASA DEL NIVEL (2026-09-03) ──────────────────────────────────────
            // Eran tres preguntas sueltas (`hasBoardGravity`, `hasHorizonTray`, `hasBoardCurrent`) y
            // con un catálogo de reglas que crece eso se convierte en un censo paralelo que alguien
            // olvidará ampliar — exactamente el fallo de P-128. Ahora se le pregunta al catálogo, que
            // ya sabe qué cápsula enseña cada regla.
            //
            // Relevo has a deterministic rule even before charging; teach it on level entry.
            val suya = HouseRule.forLevel(state.level)
            suya?.let { add(it.capsule) }
            if (state.hotCells.isNotEmpty()) add(HOT_CELL)
            // El banco está a la vista desde el turno 1 y es una salida del «SIN SALIDA»: hay que
            // saber que existe antes de necesitarlo.
            if (bankOwned && BlockBankRules.appliesTo(state.level)) add(BLOCK_BANK)
        }

        /**
         * Y cuáles pide **al cerrarse un turno**: las herramientas que acaban de aparecer en la
         * bandeja y el bioma, que solo se enseña cuando de verdad ha golpeado — es la diferencia
         * entre mostrar y contar.
         */
        fun atTurnEnd(
            state: GameState,
            /**
             * ¿Hay algún cuerpo de Teika en el tablero? Se pasa desde fuera porque los cuerpos viven
             * en el motor físico y no en [GameState] — son física continua, no celdas.
             */
            teikasOnBoard: Boolean = false,
            /**
             * ¿Este turno ha roto alguna **línea pura** (P-69)? Se pasa desde fuera, como
             * [teikasOnBoard], y por un motivo parecido: cuando esta función corre, el evento
             * `PureLine` ya puede haberlo consumido su VFX —la cadena se resuelve cientos de
             * milisegundos antes de que el turno se cierre—, así que preguntárselo a la cola daría
             * `false` casi siempre. El ViewModel lo apunta al romperla y lo gasta aquí.
             */
            pureLineThisTurn: Boolean = false,
            /** ¿Este turno se GUARDÓ una ficha en el banco? (P-356). Parámetro heredado; el taller automático no lo activa. */
            bankDepositThisTurn: Boolean = false,
        ): List<MechanicCapsule> = buildList {
            if (TutorialCampaign.isTutorialLevel(state.level)) return@buildList
            // ── Lo que ACABA de pasar, primero ──────────────────────────────────────────────────
            // La línea pura (P-69) es el premio que le da sentido a los cinco colores, y era la
            // ÚNICA cápsula del enum sin un solo emisor: se declaró y no se cableó (2026-09-02). El
            // jugador aprendía la Brocha —«pinta fila y columna»— y la Camaleón —«adopta el color»—
            // sin que nada le dijera nunca PARA QUÉ sirve el color.
            //
            // Va la primera porque es un MOMENTO y no se puede volver a mirar: las herramientas se
            // quedan en la bandeja y los cuerpos en el tablero, así que su cápsula sigue esperando
            // al turno siguiente.
            if (pureLineThisTurn) add(PURE_LINE)
            // El premio de la bandeja también es un MOMENTO: se explica el turno en que llega.
            if (state.trayRewardJustDealt) add(TRAY_REWARD)
            if (teikasOnBoard) add(TEIKA_ROLE)
            if (state.availablePieces.any { it.isWildcard }) add(WILDCARD)
            if (state.availablePieces.any { it.isBrush }) add(BRUSH)
            if (state.availablePieces.any { it.isChameleon }) add(CHAMELEON)
            if (state.availablePieces.any { it.isGrowing }) add(GROWING)
            if (bankDepositThisTurn) add(BANK_TOLL)
            if (biomeJustStruck(state)) {
                forBiome(state.biomePrimary)?.let(::add)
                // P-355: detrás de la cápsula del golpe (que es un MOMENTO y va antes), la de la
                // parada, que espera en la cola al turno siguiente.
                if (state.biomePrimary in BiomeCycle.BIOMES) add(BIOME_PARRY)
            }
        }

        /**
         * ¿El bioma acaba de ejecutar su castigo?
         *
         * Se lo pregunta a [BiomeRhythm.isImpact], que sabe qué evento es el impacto de cada bioma —
         * la misma tabla que vigila `BiomePhaseContractTest`, así que un bioma nuevo no puede
         * quedarse sin cápsula sin que nadie se entere.
         *
         * **Antes se preguntaba por el NOMBRE de la clase del evento** (`javaClass.simpleName`), y
         * eso funcionaba en debug y era falso para siempre en release, porque R8 renombra. Ocho
         * cápsulas de bioma llevaban meses sin salir en la app publicada. Ver P-176.
         */
        private fun biomeJustStruck(state: GameState): Boolean =
            state.events.any { BiomeRhythm.isImpact(state.biomePrimary, it) }
    }
}
