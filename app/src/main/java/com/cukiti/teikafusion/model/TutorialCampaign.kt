package com.cukiti.teikafusion.model

/**
 * Fuente única de los **siete** niveles de aprendizaje.
 *
 * La campaña es deliberadamente progresiva: cada nivel enseña una sola relación nueva y termina
 * cuando esa relación se demuestra. Los dos primeros combinan una ficha guiada con madera reflejada.
 * El tercero convierte la inclinación en un recorrido legible por escalones y el cuarto divide la
 * cadena final en tres encuentros, de modo que nunca amontona cuatro cuerpos gigantes.
 *
 * ## La ampliación del 2026-09-01 (pedido del usuario)
 *
 * Con cuatro lecciones el jugador salía sabiendo MOVER, pero no sabiendo **jugar**: nadie le había
 * dicho cómo se gana un nivel, con qué recurso se juega ni que tiene compañeros. Las tres nuevas
 * cierran justo ese hueco, en el orden en que hacen falta:
 *
 *  5. **El objetivo** — las estrellas del tablero son lo que el nivel pide, y romper su línea las
 *     recoge. Es la regla que convierte «colocar fichas» en «ganar un nivel».
 *  6. **El sitio** — la única derrota es que una ficha no quepa; romper filas deja sitio. (Hasta
 *     el 2026-09-23 enseñaba los TURNOS, que se retiraron del juego.)
 *  7. **La habilidad** — Cristóbal, que es justo el compañero que se desbloquea al terminar el
 *     capítulo siguiente: la lección es una promesa que se cumple enseguida.
 *
 * Cada lección lleva además sus **instantes de cámara lenta** ([SlowMotionShot]): el juego se
 * ralentiza sobre el momento que enseña y lo nombra en cuatro palabras. No son carteles — el
 * jugador ve la mecánica ocurriendo, solo que despacio.
 *
 * **Se puede saltar desde la primera** (decisión del usuario): quien ya sabe jugar no se come siete
 * lecciones. Ver `GameViewModel.skipTutorial`.
 */
object TutorialCampaign {
    const val FIRST_LEVEL = 1
    const val LAST_LEVEL = 7
    const val T1_REQUIRED_FOR_MEGA = 16

    /** El compañero que enseña la lección 7. Ver el KDoc de la clase. */
    const val ABILITY_COMPANION = "cristal"

    /** Cuándo se dispara el instante de cámara lenta que remata una lección. */
    enum class Payoff {
        /** En cuanto la lección rompe su primera línea — el shot corre CON la animación. */
        LINE_CLEAR,
        /** Al cumplirse el objetivo de la lección. */
        COMPLETION,
        /** La lección no tiene remate propio. */
        NONE,
    }

    /** Cuerpo físico inicial descrito en el dominio, sin acoplar el modelo al motor de física. */
    data class SeedTeika(
        val id: String,
        val type: TeikaType,
        val x: Float,
        val y: Float
    )

    data class Spec(
        val level: Int,
        val title: String,
        val instruction: String,
        val initialCells: List<LevelCell>,
        val requiredLines: Int,
        val targetTeika: TeikaType?,
        val completionTitle: String,
        /**
         * La frase que cierra la lección en su ventana de victoria.
         *
         * Existe desde el 2026-09-01 porque hasta hoy el subtítulo se calculaba en la UI con un
         * `if (level < LAST) "Lección N completada" else "Ya dominas la fusión"`: la última lección
         * enseña HABILIDADES y despedía al jugador felicitándole por la fusión, que es lo que
         * enseñaba la cuarta. Se vio en la grabación `analisistutorial`. Y de paso, «Lección 3
         * completada» no dice nada: repetir la regla recién aprendida sí.
         */
        val completionNote: String,
        val targetOrigin: Position? = null,
        val piece: Piece? = null,
        val initialTeikas: List<SeedTeika> = emptyList(),
        /** Cantidad de encuentros físicos dirigidos que componen la lección. */
        val tiltStages: Int = 0,
        /** -1 = izquierda, +1 = derecha, 0 = la lección no enseña inclinación. */
        val initialTiltDirection: Int = 0,
        /**
         * Estrellas que pide la lección. `null` = la lección se mide en líneas ([requiredLines]),
         * que es como funcionaban las cuatro originales.
         */
        val requiredStars: Map<TargetColor, Int>? = null,
        /** ¿Se enseña el panel de objetivo (estrellas + MEGA)? Solo donde se está explicando. */
        val showsObjective: Boolean = false,
        /** ¿Se enseña la barra de meta con el medallón de turnos? */
        val showsMoves: Boolean = false,
        /** Compañero cuya habilidad enseña la lección, o `null`. */
        val abilityCompanionId: String? = null,
        /**
         * ¿Las líneas que rompe esta lección **crían un Teika**? (2026-09-04, pedido del usuario.)
         *
         * De serie sí: es la regla del juego y la lección 2 existe justamente para enseñarla. Pero
         * dos lecciones la desactivan, y las dos por el mismo motivo — el cuerpo que nace se lleva
         * la mirada de lo que se está explicando:
         *
         *  - **La 1** enseña a arrastrar y a llenar una fila, y su cruz central rompe CUATRO de
         *    golpe: el jugador terminaba la primera lección de su vida con cuatro Teikas rodando por
         *    el tablero sin que nadie le hubiera dicho qué son. Es un *spoiler* de la lección
         *    siguiente, que es la que los presenta.
         *  - **La 6** enseña que el tablero es el límite. Repite una jugada que el jugador ya sabe
         *    hacer para que el foco quede en la idea; un cuerpo naciendo justo entonces competiría.
         */
        val breedsTeikas: Boolean = true,
        /**
         * Color ÚNICO de la madera de la lección, o `null` para la paleta reflejada de siempre.
         *
         * Las cuatro primeras lecciones dibujan figuras 2D y su paleta espejada se lee como un
         * patrón bonito. Las tres nuevas son FILAS, y sobre una fila esa misma paleta se convierte en
         * un arcoíris de siete colores que compite por la mirada justo con lo que la lección quiere
         * enseñar — la estrella, el medallón, la habilidad. Se vio en la grabación `tutorialexample`.
         */
        val flatColorType: Int? = null,
        /** Instante de cámara lenta al abrir la lección. */
        val openingShot: SlowMotionShot? = null,
        /** Instante de cámara lenta que la remata, y cuándo se dispara. */
        val payoffShot: SlowMotionShot? = null,
        val payoffOn: Payoff = Payoff.NONE,
    ) {
        init {
            require((targetOrigin == null) == (piece == null)) {
                "La ficha guiada y su origen deben declararse juntos"
            }
            require(tiltStages >= 0) { "La cantidad de etapas físicas no puede ser negativa" }
            require((tiltStages == 0) == (initialTiltDirection == 0)) {
                "Una lección de inclinación debe declarar dirección y etapas juntas"
            }
            require(initialTiltDirection in -1..1) { "Dirección de inclinación inválida" }
        }

        val gap: Set<Position> = if (piece == null || targetOrigin == null) emptySet() else {
            piece.shape
                .map { Position(targetOrigin.x + it.x, targetOrigin.y + it.y) }
                .toSet()
        }

        val teachesDrag: Boolean get() = piece != null
        val teachesTilt: Boolean get() = tiltStages > 0
        val teachesAbility: Boolean get() = abilityCompanionId != null
        val requiresMega: Boolean get() = targetTeika == TeikaType.MEGA_TEIKA
        val completionTeika: TeikaType get() = targetTeika ?: TeikaType.TEIKA_1
    }

    private fun piece(level: Int, shape: List<Position>) = Piece(
        id = "tutorial_piece_$level",
        shape = shape,
        colorType = (level - 1).coerceIn(0, 4)
    )

    /** Cruz centrada: una ficha 2×2 completa a la vez las dos filas y las dos columnas centrales. */
    private fun centralCross(): List<LevelCell> = buildList {
        for (x in 0 until GameState.GRID_SIZE) {
            if (x !in 3..4) {
                add(LevelCell(x, 3))
                add(LevelCell(x, 4))
            }
        }
        for (y in 0 until GameState.GRID_SIZE) {
            if (y !in 3..4) {
                add(LevelCell(3, y))
                add(LevelCell(4, y))
            }
        }
    }

    /** Portal horizontal reflejado: la ficha 4×2 cierra exactamente dos líneas. */
    private fun mirroredPortal(): List<LevelCell> = buildList {
        for (y in 3..4) {
            add(LevelCell(0, y))
            add(LevelCell(1, y))
            add(LevelCell(6, y))
            add(LevelCell(7, y))
        }
        addAll(
            listOf(
                LevelCell(1, 2), LevelCell(6, 2),
                LevelCell(1, 5), LevelCell(6, 5)
            )
        )
    }

    /**
     * Escalera descendente de derecha a izquierda. Cada descanso tiene dos bloques de ancho: el T1
     * puede leerse rodando, cayendo y asentándose antes del siguiente escalón, en vez de atravesar
     * una diagonal de esquinas demasiado rápido.
     */
    private fun tiltStaircase(): List<LevelCell> = buildList {
        for (step in 0 until 5) {
            val left = 6 - step
            val y = 2 + step
            add(LevelCell(left, y))
            add(LevelCell(left + 1, y))
        }
        // Podio final: mantiene al Teika objetivo entero y visible, fuera de la esquina redondeada.
        add(LevelCell(0, 7))
        add(LevelCell(1, 7))
    }

    /**
     * **Leccion 5 - el objetivo.** Una fila a la que le faltan dos celdas, con cuatro de sus bloques
     * marcados con estrella. Cerrarla rompe la fila y las cuatro estrellas vuelan al contador: la
     * relacion "rompo linea -> recojo objetivo" queda demostrada en una sola colocacion, que es
     * exactamente lo que el jugador necesita entender y nunca se le habia dicho.
     */
    private fun starRow(): List<LevelCell> = buildList {
        for (x in 0..5) {
            add(LevelCell(x, 4, targetColor = if (x in 1..4) TargetColor.BLUE else null))
        }
    }

    /**
     * **Leccion 6 - deja sitio.** Misma forma, abajo del todo: la leccion no ensena una geometria
     * nueva, ensena la UNICA derrota que queda desde el 2026-09-23 (sin turnos): que una ficha no
     * quepa. Cerrar la fila y ver el hueco que deja es la respuesta.
     */
    private fun movesRow(): List<LevelCell> = buildList {
        for (x in 0..5) add(LevelCell(x, 7))
    }

    /**
     * **Leccion 7 - la habilidad.** Tres filas a las que les faltan dos celdas cada una. El Escudo
     * Cristalino coloca fichas solo hasta encadenar tres reventones: con este tablero encuentra
     * exactamente los tres, asi que el jugador ve el poder hacer TODO el trabajo de una tacada.
     */
    private fun abilityBoard(): List<LevelCell> = buildList {
        for (y in 5..7) for (x in 0..5) add(LevelCell(x, y))
    }

    private val specs = listOf(
        Spec(
            level = 1,
            title = "ARRASTRA Y COMPLETA",
            instruction = "Sigue la mano y arrastra la ficha al centro.",
            initialCells = centralCross(),
            requiredLines = 4,
            targetTeika = null,
            completionTitle = "¡LÍNEAS COMPLETAS!",
            completionNote = "Llena una fila entera y se rompe sola.",
            // Sin Teikas: los presenta la lección 2. Ver [Spec.breedsTeikas].
            breedsTeikas = false,
            payoffOn = Payoff.LINE_CLEAR,
            payoffShot = SlowMotionShot(
                id = "tut1_line",
                label = "Llena la fila y desaparece",
                focus = SlowMotionFocus.BOARD,
                // Primero se VE la fila irse; el rótulo la nombra después. Ver `leadInMs`.
                leadInMs = 700,
                durationMs = 2400,
            ),
            targetOrigin = Position(3, 3),
            piece = piece(
                level = 1,
                shape = listOf(Position(0, 0), Position(1, 0), Position(0, 1), Position(1, 1))
            )
        ),
        Spec(
            level = 2,
            // (2026-09-27, bug de la auditoría de onboarding; decisión del usuario) Pedía un T2 y era
            // IMPOSIBLE de terminar: desde el 2026-09-26 un Teika solo nace T2 con 2+ líneas Y 2+
            // bloques-Teika (`TeikaSeeds.tierFor`), y esta ficha lleva uno. Nacía un T1 y la lección no
            // se cerraba nunca. Ahora enseña la regla de verdad —todo Teika nace T1— y crecer fundiendo
            // lo enseña la 3. Lo fija `TutorialLessonsPlayTest`, que juega las siete contra el motor.
            title = "CREA UN TEIKA",
            instruction = "Encaja la ficha en el portal central.",
            initialCells = mirroredPortal(),
            requiredLines = 2,
            targetTeika = TeikaType.TEIKA_1,
            completionTitle = "¡TU PRIMER TEIKA!",
            completionNote = "Rompe la línea del bloque-Teika y suelta un Teika.",
            payoffOn = Payoff.LINE_CLEAR,
            payoffShot = SlowMotionShot(
                id = "tut2_teika",
                label = "El bloque suelta un Teika",
                focus = SlowMotionFocus.BOARD,
                // ── EL MOMENTO QUE SE ESTABA PERDIENDO (2026-09-04, pedido del usuario) ─────────
                // Esta lección rompe DOS filas y del bloque-Teika nace el Teika que la cierra (un T1
                // desde el 2026-09-27; la fusión la enseña la lección 3).
                // El rótulo entraba en el mismo fotograma que la rotura, tapándola.
                //
                // Dos cambios y los dos por lo mismo:
                //  · `leadInMs` — un segundo entero de ver nacer los cuerpos ANTES de la frase.
                //  · `factor` más alto (0,45 en vez de 0,22) — a 0,22 los 2,8 s del shot solo movían
                //    0,6 s de física: los Teikas apenas se rozaban y la fusión ocurría FUERA del
                //    instante, ya a velocidad normal. Ralentizar tanto que el ejemplo no llegue a
                //    ocurrir es no enseñarlo.
                factor = 0.45f,
                leadInMs = 1000,
                durationMs = 2500,
            ),
            targetOrigin = Position(2, 3),
            piece = piece(
                level = 2,
                shape = buildList {
                    for (y in 0..1) for (x in 0..3) add(Position(x, y))
                }
            ).let { p -> p.copy(teikaMask = p.shape.indices.map { it == TeikaSeeds.cellFor(p) }) }
        ),
        Spec(
            level = 3,
            title = "DOMINA LA INCLINACIÓN",
            instruction = "Inclina a la izquierda y acompaña al Teika por la escalera.",
            initialCells = tiltStaircase(),
            requiredLines = 0,
            targetTeika = TeikaType.TEIKA_2,
            completionTitle = "¡INCLINACIÓN DOMINADA!",
            completionNote = "Inclina el móvil y los Teikas ruedan.",
            openingShot = SlowMotionShot(
                id = "tut3_tilt",
                label = "Inclina el móvil y ruedan",
                focus = SlowMotionFocus.BOARD,
                durationMs = 2700,
            ),
            payoffOn = Payoff.COMPLETION,
            payoffShot = SlowMotionShot(
                id = "tut3_merge",
                label = "Junta dos iguales y crecen",
                focus = SlowMotionFocus.BOARD,
                durationMs = 2700,
            ),
            initialTeikas = listOf(
                SeedTeika("tutorial_3_t1_mover", TeikaType.TEIKA_1, 6.5f, 1.5f),
                SeedTeika("tutorial_3_t1_goal", TeikaType.TEIKA_1, 0.5f, 6.5f)
            ),
            tiltStages = 1,
            initialTiltDirection = -1
        ),
        Spec(
            level = 4,
            title = "LA ESCALERA FINAL",
            instruction = "Completa tres fusiones guiadas hasta formar el Mega.",
            initialCells = emptyList(),
            requiredLines = 0,
            targetTeika = TeikaType.MEGA_TEIKA,
            completionTitle = "¡MEGA TEIKA!",
            completionNote = "Dos Teikas iguales se funden en uno mayor.",
            // (RETIRADO 2026-09-27, decisión del usuario) El remate «Y así nace el Mega» (COMPLETION) no
            // salía NUNCA: el MEGA cumple el objetivo, el nivel lo cierra la ceremonia del colapso
            // (`GameScreen` → `confirmLevelComplete`) y ese camino no pasa por `tutorialPayoffOn`. La
            // ceremonia ya rotula «¡MEGA TEIKA!».
            initialTeikas = listOf(
                // El de la izquierda queda apoyado contra la pared; el de la derecha debe alcanzarlo.
                SeedTeika("tutorial_4_t2_goal", TeikaType.TEIKA_2, 1.0f, 7.0f),
                SeedTeika("tutorial_4_t2_mover", TeikaType.TEIKA_2, 7.0f, 7.0f)
            ),
            tiltStages = 3,
            initialTiltDirection = -1
        ),
        Spec(
            level = 5,
            flatColorType = 2,
            title = "RECOGE LAS ESTRELLAS",
            instruction = "Cierra la fila: sus estrellas son tu objetivo.",
            initialCells = starRow(),
            requiredLines = 0,
            requiredStars = mapOf(TargetColor.BLUE to 4),
            targetTeika = null,
            completionTitle = "¡OBJETIVO CUMPLIDO!",
            completionNote = "Las estrellas del tablero son lo que pide el nivel.",
            showsObjective = true,
            openingShot = SlowMotionShot(
                id = "tut5_goal",
                label = "Esto te pide el nivel",
                focus = SlowMotionFocus.GOAL,
                factor = 1f,
                durationMs = 2800,
            ),
            payoffOn = Payoff.LINE_CLEAR,
            payoffShot = SlowMotionShot(
                id = "tut5_collect",
                label = "Rompe la fila y son tuyas",
                focus = SlowMotionFocus.BOARD,
                cells = (0..7).mapTo(HashSet()) { Position(it, 4) },
                // Las estrellas echan a volar y ENTONCES se dice de qué van.
                leadInMs = 700,
                durationMs = 2500,
            ),
            targetOrigin = Position(6, 4),
            piece = piece(level = 5, shape = listOf(Position(0, 0), Position(1, 0)))
        ),
        Spec(
            level = 6,
            flatColorType = 3,
            title = "DEJA SITIO",
            instruction = "Cierra la fila: romperla te deja sitio.",
            initialCells = movesRow(),
            requiredLines = 1,
            targetTeika = null,
            completionTitle = "¡SITIO LIBRE!",
            completionNote = "Si una ficha no cabe, se acaba el nivel: rompe filas para dejar sitio.",
            // Sin Teikas: aquí se mira el hueco. Ver [Spec.breedsTeikas].
            breedsTeikas = false,
            openingShot = SlowMotionShot(
                id = "tut6_room",
                label = "Si no cabe, se acaba",
                focus = SlowMotionFocus.BOARD,
                factor = 1f,
                durationMs = 2800,
            ),
            payoffOn = Payoff.LINE_CLEAR,
            payoffShot = SlowMotionShot(
                id = "tut6_free",
                label = "Romper filas deja sitio",
                focus = SlowMotionFocus.BOARD,
                leadInMs = 700,
                durationMs = 2500,
            ),
            targetOrigin = Position(6, 7),
            piece = piece(level = 6, shape = listOf(Position(0, 0), Position(1, 0)))
        ),
        Spec(
            level = 7,
            flatColorType = 0,
            title = "TU COMPAÑERO",
            instruction = "Toca el botón del rayo y lanza a Cristóbal.",
            initialCells = abilityBoard(),
            requiredLines = 3,
            targetTeika = null,
            completionTitle = "¡HABILIDAD LANZADA!",
            // (2026-09-27) Decía «Tus power-ups están siempre bajo la bandeja», y no es verdad: el botón
            // solo sale con los que se eligen en el panel del nivel (P-282, `CompanionBelt`), y los
            // primeros niveles no traen ninguno. La nota dice ahora de dónde salen.
            completionNote = "Elige tus power-ups antes de cada nivel y lánzalos con el rayo.",
            abilityCompanionId = ABILITY_COMPANION,
            openingShot = SlowMotionShot(
                id = "tut7_tab",
                label = "Tus power-ups viven aquí",
                focus = SlowMotionFocus.ABILITY_TAB,
                factor = 1f,
                durationMs = 3000,
            ),
            payoffOn = Payoff.LINE_CLEAR,
            payoffShot = SlowMotionShot(
                id = "tut7_cast",
                label = "Cristóbal juega por ti",
                focus = SlowMotionFocus.BOARD,
                leadInMs = 700,
                durationMs = 2600,
                factor = 0.2f,
            ),
        )
    ).associateBy { it.level }

    fun isTutorialLevel(level: Int): Boolean = level in FIRST_LEVEL..LAST_LEVEL

    fun spec(level: Int): Spec? = specs[level]

    fun requiresMega(level: Int): Boolean = spec(level)?.requiresMega == true

    /**
     * ¿Puede nacer un Teika de una línea rota en [level]?
     *
     * Fuera del tutorial, SIEMPRE. Dentro, lo decide la lección ([Spec.breedsTeikas]).
     */
    fun breedsTeikas(level: Int): Boolean = spec(level)?.breedsTeikas ?: true

    /** Tier que debe resultar de cada encuentro de la cuarta lección. */
    fun lessonFourExpectedResult(stage: Int): TeikaType? = when (stage) {
        0 -> TeikaType.TEIKA_3
        1 -> TeikaType.TEIKA_4
        2 -> TeikaType.MEGA_TEIKA
        else -> null
    }

    /**
     * Compañero de práctica que aparece después de una fusión intermedia. El resultado recién creado
     * aporta la primera mitad; esta función declara exclusivamente la segunda, sin inventar fusiones.
     */
    fun lessonFourPartnerAfter(result: TeikaType): TeikaType? = when (result) {
        TeikaType.TEIKA_3 -> TeikaType.TEIKA_3
        TeikaType.TEIKA_4 -> TeikaType.TEIKA_4
        else -> null
    }

    /**
     * Paleta reflejada en ambos ejes: la geometría y el color conservan la misma simetría.
     *
     * Salvo en las lecciones que declaran [Spec.flatColorType], que se pintan de un solo color — ver
     * ahí por qué.
     */
    fun colorTypeFor(level: Int, x: Int, y: Int): Int {
        spec(level)?.flatColorType?.let { return it }
        val mirrorX = minOf(x, GameState.GRID_SIZE - 1 - x)
        val mirrorY = minOf(y, GameState.GRID_SIZE - 1 - y)
        return ((level - 1) + mirrorX + mirrorY) % 5
    }

    /** Los usuarios que ya llegaron al nivel 5 no deben recibir el tutorial tras actualizar. */
    fun isAlreadyCompleted(savedTutorialDone: Boolean, maxUnlockedLevel: Int): Boolean =
        savedTutorialDone || maxUnlockedLevel > LAST_LEVEL

    /**
     * Objetivo de la lección. Las que se miden en líneas siguen sin estrellas ni calificación —el
     * contador es un hito interno—; la quinta pide ESTRELLAS de verdad, porque enseñar el objetivo
     * con un objetivo de mentira no enseñaría nada.
     */
    fun layout(level: Int): Pair<List<LevelCell>, LevelObjective>? = spec(level)?.let { spec ->
        val stars = spec.requiredStars
        spec.initialCells to if (stars != null) {
            LevelObjective(required = stars, goal = LevelGoal.STARS)
        } else {
            LevelObjective(required = emptyMap(), goal = LevelGoal.LINES, amount = spec.requiredLines)
        }
    }

    /**
     * ¿Se admite esta colocación en una lección?
     *
     * Solo las lecciones con **ficha guiada** dirigen dónde se coloca. Las demás no guian nada, así
     * que admiten cualquier jugada legal: devolver `false` para ellas —lo que hacía hasta el
     * 2026-09-01— dejaba la lección 7 sin salida, porque la habilidad reparte fichas y **todas** las
     * colocaciones del jugador se rechazaban en silencio. Se vio en la grabación `tutorialexample`:
     * el jugador colocando la misma pieza una y otra vez con el marcador clavado en 0.
     */
    /* ── (RETIRADO 2026-09-07) `locksRotation` ───────────────────────────────────────────────────
     *
     * P-202 impedía que la ficha guiada de una lección rotara: un toque seco la giraba, y entonces
     * la sombra del destino, el clon que lleva la mano y la comprobación de la lección hablaban de
     * tres formas distintas. El giro se retiró del juego y **volvió el 2026-09-24 (P-276)**; la
     * puerta que lo cierra en las lecciones vive ahora en `GameViewModel.rotatePieceInTray`, que se
     * niega en todo nivel de tutorial. Tiene que seguir así: [isCorrectPlacement] compara **id y
     * origen, no forma**, y eso solo es correcto mientras la ficha no pueda cambiar de forma en la mano. */

    fun isCorrectPlacement(level: Int, piece: Piece, x: Int, y: Int): Boolean {
        val spec = spec(level) ?: return true
        if (!spec.teachesDrag) return true
        val expectedPiece = spec.piece ?: return true
        val target = spec.targetOrigin ?: return true
        return piece.id == expectedPiece.id && x == target.x && y == target.y
    }
}
