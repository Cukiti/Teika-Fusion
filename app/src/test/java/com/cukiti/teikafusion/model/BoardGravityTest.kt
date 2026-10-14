package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato del NIVEL CON GRAVEDAD (P-80). */
class BoardGravityTest {

    private val W = GameState.GRID_SIZE
    private val H = GameState.GRID_SIZE

    private fun falls(wood: Set<Position>, blocked: Set<Position> = emptySet()) =
        BoardGravity.computeFalls(wood, blocked, W, H)

    // ── Cuándo hay gravedad ────────────────────────────────────────────────────────────────────

    /**
     * **Al menos uno por capítulo** (2026-09-03).
     *
     * Era «exactamente uno»: la gravedad se repartía sola, con su propio salto dentro del capítulo.
     * Hoy es una entrada del catálogo de reglas de la casa ([HouseRule]) y sale varias veces por
     * bioma, porque el usuario pidió que **todos** los niveles lleven una regla. Lo que esta prueba
     * sigue defendiendo es lo que importaba de la versión vieja: que **ningún capítulo se quede sin
     * ella**. Un bioma entero sin gravedad sería un bioma al que le falta una de las lecturas del
     * tablero.
     */
    @Test
    fun `ningun capitulo se queda sin gravedad`() {
        val chapters = BoardGravity.levels().map(LevelMap::chapterIndexForLevel).toSet()
        assertEquals(LevelMap.chapters.indices.toSet(), chapters)
    }

    /** La intro y el tutorial enseñan a jugar; cambiarles las reglas del tablero sería una trampa. */
    @Test
    fun `la intro y el tutorial nunca llevan gravedad`() {
        for (level in 1..LevelMap.introLevels) assertFalse(BoardGravity.appliesTo(level))
        for (level in 1..LevelMap.totalLevels) {
            if (TutorialCampaign.isTutorialLevel(level)) assertFalse(BoardGravity.appliesTo(level))
        }
    }

    /**
     * (RETIRADO 2026-09-03) **`nunca es el que abre ni el que cierra el capitulo`.**
     *
     * La gravedad se apartaba del primer y del último nivel del capítulo —el primero presenta el
     * bioma, el último cierra el mural— porque salía UNA vez por capítulo y podía permitirse elegir
     * el hueco. Desde que **todos** los niveles llevan una regla de la casa (decisión del usuario,
     * 2026-09-03) ese margen no existe: si el primer nivel de un bioma no puede llevar regla, hay
     * 14 niveles sin regla, y era justo lo contrario lo que se pidió.
     *
     * Se deja escrito porque la razón sigue siendo buena y puede volver como una decisión propia
     * —«el nivel que presenta el bioma no lleva regla»— si el playtest dice que dos cosas nuevas a
     * la vez son demasiado. Lo que no puede es sobrevivir como un test que afirma algo falso.
     *
     * Lo que sí se conserva abajo: la gravedad no cae siempre en el mismo hueco del capítulo.
     */
    /** Determinista: sin RNG y sin serializar nada, el mismo nivel responde siempre lo mismo. */
    @Test
    fun `la eleccion es estable entre llamadas`() {
        assertEquals(BoardGravity.levels(), BoardGravity.levels())
    }

    /** Y no se amontonan todos en la misma posición del capítulo (era el patrón que rompió P-84). */
    @Test
    fun `no cae siempre en el mismo hueco del capitulo`() {
        val locals = BoardGravity.levels().map(LevelMap::localIndex).distinct()
        assertTrue("solo ${locals.size} posiciones distintas", locals.size >= 4)
    }

    // ── Cómo cae ───────────────────────────────────────────────────────────────────────────────

    @Test
    fun `un bloque suelto cae hasta el fondo de su columna`() {
        val moves = falls(setOf(Position(3, 0)))
        assertEquals(listOf(BoardGravity.Fall(Position(3, 0), Position(3, H - 1))), moves)
    }

    @Test
    fun `lo que ya esta apoyado no se mueve`() {
        assertTrue(falls(setOf(Position(3, H - 1))).isEmpty())
    }

    /**
     * **El orden vertical se conserva.** Una columna con huecos intercalados se compacta contra el
     * fondo sin que los bloques se adelanten entre sí: el de arriba sigue arriba. Sin esto el
     * desplome sería ilegible (y con estrellas de objetivo encima, injusto).
     */
    @Test
    fun `una columna con huecos se compacta manteniendo el orden`() {
        val wood = setOf(Position(0, 1), Position(0, 4), Position(0, 6))
        val destinos = falls(wood).associate { it.from to it.to }
        assertEquals(Position(0, H - 1), destinos[Position(0, 6)])
        assertEquals(Position(0, H - 2), destinos[Position(0, 4)])
        assertEquals(Position(0, H - 3), destinos[Position(0, 1)])
    }

    /** Un ancla frena a la madera de encima y no se mueve ella: es suelo, no obstáculo que caiga. */
    @Test
    fun `un ancla hace de suelo y ella no cae`() {
        val ancla = Position(2, 5)
        val moves = falls(wood = setOf(Position(2, 0)), blocked = setOf(ancla))
        assertEquals(listOf(BoardGravity.Fall(Position(2, 0), Position(2, 4))), moves)
    }

    /**
     * **El Teika es suelo** (decisión del usuario): la madera se para encima del cuerpo en vez de
     * meterse dentro. Si atravesara, el solver de la física tendría que expulsar el cuerpo.
     */
    @Test
    fun `la madera se apoya en el cuerpo de un Teika`() {
        // Un T2 ocupa 2x2; aquí, las cuatro celdas de abajo de la columna 4-5.
        val cuerpo = setOf(
            Position(4, 6), Position(5, 6), Position(4, 7), Position(5, 7)
        )
        val moves = falls(wood = setOf(Position(4, 1)), blocked = cuerpo)
        assertEquals(listOf(BoardGravity.Fall(Position(4, 1), Position(4, 5))), moves)
    }

    /** Una celda que es madera Y ancla a la vez manda como ancla: es la que no se puede mover. */
    @Test
    fun `ancla y madera a la vez no cae`() {
        val p = Position(1, 2)
        assertTrue(falls(wood = setOf(p), blocked = setOf(p)).isEmpty())
    }

    /** Cada bloque cae por SU columna: una ficha puede llegar abajo desarmada, y eso es la mecánica. */
    @Test
    fun `una ficha sobre terreno desigual se desarma`() {
        val suelo = setOf(Position(0, H - 1))
        val ficha = setOf(Position(0, 3), Position(1, 3))
        val destinos = falls(ficha, suelo).associate { it.from to it.to }
        assertEquals(Position(0, H - 2), destinos[Position(0, 3)])
        assertEquals(Position(1, H - 1), destinos[Position(1, 3)])
    }

    /** Nada sale del tablero ni sube: la gravedad solo puede bajar por la misma columna. */
    @Test
    fun `todo destino baja y respeta el tablero`() {
        val wood = buildSet {
            for (x in 0 until W) for (y in 0 until H step 3) add(Position(x, y))
        }
        falls(wood).forEach {
            assertEquals(it.from.x, it.to.x)
            assertTrue(it.to.y > it.from.y)
            assertTrue(it.to.y in 0 until H)
        }
    }

    /** Un tablero ya asentado no genera ni una caída: la segunda pasada del motor es un no-op. */
    @Test
    fun `asentar dos veces no mueve nada la segunda`() {
        val wood = mutableSetOf(Position(0, 0), Position(0, 3), Position(0, 5), Position(1, 2))
        val primera = falls(wood)
        assertTrue(primera.isNotEmpty())
        val asentado = wood.toMutableSet()
        primera.forEach { asentado.remove(it.from) }
        primera.forEach { asentado.add(it.to) }
        assertTrue(falls(asentado).isEmpty())
    }

    /** La duración escala con la caída: un bloque que viene de arriba tarda más y pesa más. */
    @Test
    fun `cuanto mas alto mas tarda`() {
        assertTrue(BoardGravity.fallDurationMs(5) > BoardGravity.fallDurationMs(1))
        assertTrue(BoardGravity.fallDurationMs(0) > 0)
    }

    // ── La apertura del nivel ──────────────────────────────────────────────────────────────────

    /**
     * **El nivel abre YA ASENTADO.** Si abriera con la figura flotando, la primera ficha del jugador
     * desplomaría el tablero entero de un golpe y la silueta curada no habría significado nada.
     */
    @Test
    fun `los niveles con gravedad abren con la figura asentada`() {
        // (2026-09-03) La gravedad son cuatro direcciones, así que «asentada» ya no quiere decir
        // «pegada al fondo»: cada nivel amontona su figura contra el borde hacia el que de verdad
        // tira. La comprobación es la misma leída en el eje que toque — ninguna celda de la figura
        // puede tener un hueco entre ella y ese borde.
        BoardGravity.levels().forEach { level ->
            val cells = LevelLayouts.get(level)?.first ?: return@forEach
            val pull = BoardGravity.pullFor(level) ?: return@forEach
            val ocupadas = cells.mapTo(HashSet()) { Position(it.x, it.y) }
            val ultima = GameState.GRID_SIZE - 1
            ocupadas.forEach { p ->
                val siguiente = when (pull) {
                    BoardGravity.Pull.DOWN -> if (p.y < ultima) Position(p.x, p.y + 1) else null
                    BoardGravity.Pull.UP -> if (p.y > 0) Position(p.x, p.y - 1) else null
                    BoardGravity.Pull.RIGHT -> if (p.x < ultima) Position(p.x + 1, p.y) else null
                    BoardGravity.Pull.LEFT -> if (p.x > 0) Position(p.x - 1, p.y) else null
                }
                if (siguiente != null) {
                    assertTrue(
                        "nivel $level ($pull): hueco entre (${p.x},${p.y}) y su borde",
                        siguiente in ocupadas
                    )
                }
            }
        }
    }

    /**
     * Y sigue respetando la HOLGURA DE LÍNEA: asentar amontona la figura contra el fondo, que es
     * exactamente la forma de volver a regalar líneas de salida (la regla de 2026-08-26).
     */
    @Test
    fun `asentar la apertura no reabre las lineas regaladas`() {
        BoardGravity.levels().forEach { level ->
            val cells = LevelLayouts.get(level)?.first ?: return@forEach
            val rows = IntArray(GameState.GRID_SIZE)
            val cols = IntArray(GameState.GRID_SIZE)
            cells.forEach { rows[it.y]++; cols[it.x]++ }
            assertTrue(
                "nivel $level abre con una línea de ${maxOf(rows.max(), cols.max())}",
                maxOf(rows.max(), cols.max()) <= LevelOpening.MAX_LINE_FILL
            )
        }
    }

    // ── EL SUELO QUE SE MUEVE (bug del playtest, 2026-09-05) ───────────────────────────────────

    /**
     * **UN TEIKA ES SUELO, Y EL SUELO SE MUEVE.**
     *
     * El solver siempre supo hacer esto —la madera cuelga de lo que tenga debajo— pero la gravedad se
     * resolvia SOLO por turnos, dentro de la cadena, mientras que su suelo son cuerpos fisicos que
     * ruedan entre turno y turno. Si el Teika se iba de debajo de una pila, la madera se quedaba
     * flotando hasta la colocacion siguiente.
     *
     * Esta prueba fija la mitad pura: quitado el apoyo, el solver DEVUELVE la caida. Que alguien la
     * llame en ese momento lo defiende `el frame de fisica re-asienta cuando el suelo de Teika se
     * mueve`, aqui debajo — el fallo no estaba en la regla, estaba en quien la invoca.
     */
    @Test
    fun `la madera cae cuando el Teika deja de sostenerla`() {
        val madera = setOf(Position(3, 4), Position(3, 5))
        val teika = setOf(Position(3, 6))          // el cuerpo, haciendo de suelo en la columna 3

        // Con el cuerpo debajo, la pila se apoya en el y no se mueve mas alla de el.
        val conCuerpo = BoardGravity.computeFalls(madera, teika, W, H)
        conCuerpo.forEach {
            assertTrue("la madera ha atravesado al Teika", it.to.y < 6)
        }

        // El cuerpo rueda y se va: ahora no hay nada debajo y la pila baja hasta el fondo.
        val sinCuerpo = BoardGravity.computeFalls(madera, emptySet(), W, H)
        assertTrue("sin apoyo, la madera tenia que caer", sinCuerpo.isNotEmpty())
        val destinos = sinCuerpo.map { it.to.y }.toSet() + (madera.map { it.y }.toSet() -
            sinCuerpo.map { it.from.y }.toSet())
        assertTrue("la pila no ha llegado al suelo: $destinos", destinos.contains(H - 1))
    }

    /**
     * **Y ALGUIEN TIENE QUE LLAMARLA CUANDO EL CUERPO SE APARTA.**
     *
     * El bug no era del solver sino de las puertas: la fusion re-lanzaba la cadena (dos cuerpos pasan
     * a ser uno) y el deslizamiento de la corriente tambien, pero un cuerpo que simplemente **se
     * mueve** no fusiona ni desaparece, asi que no pasaba por ninguna. Este contrato exige que el
     * frame de fisica siga siendo esa puerta.
     */
    @Test
    fun `el frame de fisica re-asienta cuando el suelo de Teika se mueve`() {
        val vm = java.io.File(repoRoot, VIEW_MODEL).readText()
        // La LLAMADA dentro del frame, no la declaracion: comprobar solo que el nombre existe es lo
        // que hacia pasar en verde un saboteo que quitaba la llamada y dejaba la funcion muerta.
        val frameIni = vm.indexOf("suspend fun onTeikaPhysicsFrame(")
        assertTrue("no encuentro onTeikaPhysicsFrame: el parseo esta roto", frameIni > 0)
        val frameFin = vm.indexOf(NEXT_FUN, frameIni)
        val frame = vm.substring(frameIni, if (frameFin < 0) vm.length else frameFin)
        assertTrue(
            "el frame de fisica ya no comprueba si el suelo de Teika se ha movido: la madera " +
                "apoyada en un cuerpo volvera a quedarse flotando cuando el cuerpo ruede",
            "settleGravityIfTeikaFloorMoved(" in frame
        )
        val inicio = vm.indexOf("private fun settleGravityIfTeikaFloorMoved(")
        assertTrue("no encuentro la funcion: el parseo esta roto", inicio > 0)
        val fin = vm.indexOf("\n    private fun resetTeikaFloorWatch", inicio)
        val cuerpo = vm.substring(inicio, if (fin < 0) vm.length else fin)
        // La CADENA entera, no solo la gravedad: la madera que cae puede completar una linea, y esa
        // linea es del jugador igual que cualquier otra.
        assertTrue(
            "re-asienta sin relanzar la cadena: una linea completada por la caida se quedaria sin " +
                "romper hasta la colocacion siguiente",
            "resolveBoardChain(" in cuerpo
        )
        // Y la comprobacion por frame no puede volver a pedir el conjunto de celdas: eso es el
        // anti-patron que ya pago este motor (ver la cache de physicsTerrainCells).
        val antesDeQuietud = cuerpo.substringBefore("val ahora = teikaBlockedCells()")
        assertFalse(
            "teikaBlockedCells() ha vuelto a la ruta de CADA frame: es un conjunto nuevo 60 veces " +
                "por segundo, que es justo lo que la cache del terreno vino a quitar",
            "teikaBlockedCells()" in antesDeQuietud
        )
    }

    private val repoRoot: java.io.File by lazy {
        var dir: java.io.File? = java.io.File("").absoluteFile
        while (dir != null && !java.io.File(dir, VIEW_MODEL).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raiz del repo" }
    }

    /** Asentar no puede dejar el tablero en los huesos: sigue siendo un nivel, no una pizarra. */
    @Test
    fun `la apertura conserva cuerpo`() {
        BoardGravity.levels().forEach { level ->
            val cells = LevelLayouts.get(level)?.first ?: return@forEach
            assertTrue("nivel $level abre con solo ${cells.size} bloques", cells.size >= 10)
        }
    }

    private companion object {
        const val VIEW_MODEL = "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt"
        /** Fin de una funcion de la clase = la declaracion de la siguiente. */
        const val NEXT_FUN = "\n    private fun "
    }
}
