package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de LAS CÁPSULAS (2026-09-01). */
class MechanicCapsuleTest {

    /**
     * **Los catorce biomas tienen cápsula.** Es el invariante que impide que un bioma se quede mudo:
     * si mañana entra uno nuevo en `BiomeType`, esta prueba falla hasta que se le escriba la suya.
     */
    @Test
    fun `todos los biomas tienen capsula menos NONE`() {
        BiomeType.entries.forEach { biome ->
            val capsula = MechanicCapsule.forBiome(biome)
            if (biome == BiomeType.NONE) assertTrue("NONE no debe tener", capsula == null)
            else assertNotNull("$biome sin cápsula", capsula)
        }
    }

    /** Y cada bioma tiene la SUYA: catorce cápsulas distintas, no una compartida. */
    @Test
    fun `ningun bioma comparte capsula con otro`() {
        val capsulas = BiomeType.entries.mapNotNull { MechanicCapsule.forBiome(it) }
        assertEquals(capsulas.size, capsulas.distinct().size)
    }

    /** Los ids **se persisten**: dos iguales harían que una mecánica no se enseñara nunca. */
    @Test
    fun `los ids son unicos`() {
        val ids = MechanicCapsule.entries.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    /**
     * **NINGUNA CÁPSULA FÓSIL** (2026-09-02).
     *
     * `PURE_LINE` se declaró con el resto y **no se cableó**: ni `atRoundStart` ni `atTurnEnd` ni
     * `forBiome` la nombraban, así que la Línea Pura —el premio que le da sentido a los cinco
     * colores, y la razón de ser de la Brocha y la Camaleón— era la única mecánica del sistema que
     * el juego no explicaba nunca. Se descubrió leyendo, que es exactamente lo que un contrato
     * escrito evita.
     *
     * Se lee el fuente porque no hay forma de preguntarle a un `enum` quién lo construye — mismo
     * recurso que `GameEventContractTest` con los eventos huérfanos. Un emisor es cualquier mención
     * dentro del `companion object`, que es donde viven las tres funciones que reparten cápsulas.
     */
    @Test
    fun `toda capsula tiene quien la emita`() {
        val fuente = capsuleSource
        val emisores = fuente.substringAfter("companion object {")
        assertTrue("no encuentro el companion object de MechanicCapsule", emisores.isNotBlank())
        // (2026-09-03) Hay un segundo emisor, y no se puede leer del fuente: las cápsulas de las
        // reglas de la casa se reparten por TABLA (`HouseRule.capsule`), no nombrándolas una a una.
        // Eso es justo lo que arregló el censo paralelo que dejó pasar el bug de P-128 — pero deja
        // sus nombres fuera del `companion object`, así que el contrato tiene que preguntarle
        // también al catálogo. La garantía no se afloja: `HouseRuleContractTest` exige que cada
        // regla tenga cápsula, y ésta que cada cápsula tenga regla o mención.
        val porTabla = HouseRule.entries.mapTo(HashSet()) { it.capsule }
        MechanicCapsule.entries.forEach { capsula ->
            assertTrue(
                "${capsula.name} está declarada pero nadie la emite: cápsula fósil",
                capsula in porTabla || Regex("\\b${capsula.name}\\b").containsMatchIn(emisores)
            )
        }
    }

    /** El fuente de `MechanicCapsule.kt`, buscado desde el directorio de trabajo del test. */
    private val capsuleSource: String by lazy {
        val relativa = "app/src/main/java/com/cukiti/teikafusion/model/MechanicCapsule.kt"
        var dir: java.io.File? = java.io.File("").absoluteFile
        while (dir != null && !java.io.File(dir, relativa).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${java.io.File("").absolutePath}" }
        java.io.File(dir, relativa).readText()
    }

    /**
     * **Cuatro o cinco palabras, nunca un párrafo.** Es la regla que separa esto del cartel que el
     * usuario retiró el 2026-08-26b: aquí se nombra lo que pasa, no se explica.
     */
    @Test
    fun `ninguna capsula es un parrafo`() {
        MechanicCapsule.entries.forEach {
            val palabras = it.label.trim().split(" ").size
            assertTrue("${it.id}: $palabras palabras", palabras <= 6)
            assertTrue("${it.id} vacía", it.label.isNotBlank())
        }
    }

    /**
     * **Y los instantes del TUTORIAL cumplen la misma regla.** El primer intento no los medía y en la
     * grabación `tutorialexample` se vio el resultado: «Esto es lo que pide el nivel» se partía en
     * dos líneas y la píldora de progreso se comía la segunda.
     */
    @Test
    fun `ningun instante del tutorial es un parrafo`() {
        for (level in TutorialCampaign.FIRST_LEVEL..TutorialCampaign.LAST_LEVEL) {
            val spec = TutorialCampaign.spec(level)!!
            listOfNotNull(spec.openingShot, spec.payoffShot).forEach { shot ->
                val palabras = shot.label.trim().split(" ").size
                assertTrue("lección $level, \"${shot.label}\": $palabras palabras", palabras <= 6)
                assertTrue("lección $level pasa del tope", shot.durationMs <= SlowMotionShot.MAX_MS)
            }
        }
    }

    /** Una lección con remate declara CUÁNDO se dispara, y una sin él no declara ninguno. */
    @Test
    fun `el remate y su momento van juntos`() {
        for (level in TutorialCampaign.FIRST_LEVEL..TutorialCampaign.LAST_LEVEL) {
            val spec = TutorialCampaign.spec(level)!!
            val tieneRemate = spec.payoffShot != null
            val tieneMomento = spec.payoffOn != TutorialCampaign.Payoff.NONE
            assertEquals("lección $level", tieneRemate, tieneMomento)
        }
    }

    /** Y ninguna pasa del tope de tiempo: una explicación larga deja de explicar (P-62). */
    @Test
    fun `ninguna capsula pasa del tope`() {
        MechanicCapsule.entries.forEach {
            assertTrue("${it.id}", it.durationMs <= SlowMotionShot.MAX_MS)
            assertTrue("${it.id} congelaría la física", it.factor > 0f)
        }
    }

    // ── Cuándo salen ───────────────────────────────────────────────────────────────────────────

    /** El tutorial ya tiene sus lecciones: encadenar cápsulas ahí sería ruido sobre ruido. */
    @Test
    fun `en el tutorial no sale ninguna capsula`() {
        for (level in TutorialCampaign.FIRST_LEVEL..TutorialCampaign.LAST_LEVEL) {
            val state = GameState.initial(level)
            assertTrue(MechanicCapsule.atRoundStart(state).isEmpty())
            assertTrue(MechanicCapsule.atTurnEnd(state).isEmpty())
        }
    }

    /** Una regla de la casa se anuncia al ABRIR: el jugador decide su primera jugada con ella. */
    /**
     * **TODA REGLA DE LA CASA SE ANUNCIA AL ABRIR SU NIVEL** (2026-09-03).
     *
     * Eran tres pruebas sueltas —gravedad, horizonte, corriente— escritas una por mecánica, que es
     * el patrón que dejó pasar el bug de P-128: cubren lo que alguien se acordó de escribir. Ahora
     * se recorre el catálogo entero, así que **una regla nueva sin cápsula no llega a una build**.
     *
     * Es la condición que hace jugable «una regla en cada nivel»: un tablero que se comporta de otra
     * manera y no dice cuál no es variedad, es un tablero que se porta raro.
     */
    @Test
    fun `toda regla de la casa se anuncia al abrir su nivel`() {
        HouseRule.entries.forEach { rule ->
            // Las GEMELAS son la excepción declarada: viven en la bandeja y solo cuando el
            // repartidor ha conseguido duplicar de verdad una forma, así que su cápsula sale al
            // cerrar el turno. Ver el KDoc de `MechanicCapsule.atRoundStart`.
            // El IMÁN (P-240) tampoco: es una ficha, y su cápsula sale cuando el repartidor la trae.
            // Y FUNDIR (P-232): su cápsula señala las dos gemelas, así que sale cuando están.
            val level = HouseRule.levelsWith(rule).firstOrNull()
                ?: throw AssertionError("${rule.name} no le toca a ningún nivel del mapa")
            val capsulas = MechanicCapsule.atRoundStart(GameState.initial(level))
            assertTrue(
                "${rule.name} (nivel $level) no anuncia su cápsula ${rule.capsule.name}",
                rule.capsule in capsulas
            )
        }
    }

    /**
     * **Y ningún nivel anuncia una regla que no lleva.** Era `un nivel sin nada especial no pide
     * capsulas de regla`, y esa prueba ya no puede escribirse: desde el 2026-09-03 **todos** los
     * niveles llevan una regla de la casa, por decisión del usuario, así que un nivel «sin nada
     * especial» no existe. Lo que sigue en pie —y es lo que de verdad protegía— es que se anuncie
     * exactamente UNA, la suya: dos cápsulas de regla en el mismo tablero significarían que el
     * reparto ha dejado de ser excluyente.
     */
    @Test
    fun `cada nivel anuncia una sola regla, la suya`() {
        val deReglas = HouseRule.entries.mapTo(HashSet()) { it.capsule }
        for (level in LevelMap.introLevels + 1..LevelMap.totalLevels) {
            if (TutorialCampaign.isTutorialLevel(level)) continue
            val capsulas = MechanicCapsule.atRoundStart(GameState.initial(level))
                .filter { it in deReglas }
            val suya = HouseRule.forLevel(level)
            val esperadas =
                if (suya == null) emptyList()
                else listOf(suya.capsule)
            assertEquals("nivel $level", esperadas, capsulas)
        }
    }

    /**
     * **EL IMÁN SE ENSEÑA CUANDO LLEGA** (P-240): la cápsula sale al cerrar el turno en que el
     * repartidor lo trae, y señala ESA ficha de la bandeja — «pon el imán» sin decir cuál sería
     * mirar tres cuadros buscándolo.
     */
    @Test
    fun `el iman se explica cuando llega a la bandeja y senala su ficha`() {
        val state = GameState.initial(HouseRule.levelsWith(HouseRule.TRAMPOLINE).first())
        assertTrue(MechanicCapsule.TRAMPOLINE in MechanicCapsule.atRoundStart(state))
        assertEquals(state.grid.keys, MechanicCapsule.TRAMPOLINE.shot(state).cells)
    }

    /**
     * **El bioma solo se enseña cuando GOLPEA**, no al abrir el nivel. Es la diferencia entre
     * mostrar y contar, y es exactamente lo que respeta la decisión congelada del 2026-08-26b.
     */
    @Test
    fun `el bioma no se explica hasta que golpea`() {
        val level = LevelMap.chapterStart[1] + 2
        val state = GameState.initial(level)
        val biomeCapsule = MechanicCapsule.forBiome(state.biomePrimary)
        assertNotNull(biomeCapsule)
        assertTrue("se explica sin haber golpeado", biomeCapsule !in MechanicCapsule.atTurnEnd(state))

        // Con su evento de IMPACTO en la cola (el mismo nombre que declara `BiomeRhythm`), sí.
        val impacto = BiomeRhythm.phasesFor(state.biomePrimary)!!.impact
        assertEquals("DrowsyOnset", impacto)
        val golpeado = state.copy(events = listOf(GameEvent.DrowsyOnset(0)))
        assertTrue(biomeCapsule in MechanicCapsule.atTurnEnd(golpeado))
    }

    /** P-354: el giro se enseña al abrir el primer nivel de verdad, y el primero de la cola. */
    @Test
    fun `el giro se ensena al abrir el primer nivel`() {
        val capsulas = MechanicCapsule.atRoundStart(GameState.initial(LevelMap.introLevels + 1))
        assertEquals(MechanicCapsule.ROTATE, capsulas.first())
    }

    /** P-355: tras el primer golpe de un bioma con ciclo, la parada; los que no tienen ciclo, no. */
    @Test
    fun `la parada se ensena tras el golpe de un bioma con ciclo`() {
        val sueno = GameState.initial(LevelMap.chapterStart[1] + 2)
        val golpeado = sueno.copy(events = listOf(GameEvent.DrowsyOnset(0)))
        val capsulas = MechanicCapsule.atTurnEnd(golpeado)
        assertTrue(MechanicCapsule.BIOME_PARRY in capsulas)
        // Detrás de la del bioma: primero se nombra lo que ha caído, luego cómo pararlo.
        assertTrue(capsulas.indexOf(MechanicCapsule.BIOME_SLEEP) < capsulas.indexOf(MechanicCapsule.BIOME_PARRY))
        assertTrue(MechanicCapsule.BIOME_PARRY !in MechanicCapsule.atTurnEnd(sueno))
        val cristal = GameState.initial(LevelMap.chapterStart[0] + 2)
        assertTrue(MechanicCapsule.BIOME_PARRY !in MechanicCapsule.atTurnEnd(cristal.copy(events = listOf(GameEvent.CrystalCrack(Position(0, 0), remainingArmor = 0)))))
    }

    /** P-356: el peaje del banco se explica el turno en que se guarda. */
    @Test
    fun `el peaje del banco se explica al guardar`() {
        val state = GameState.initial(LevelMap.chapterStart[1] + 2)
        assertTrue(MechanicCapsule.BANK_TOLL !in MechanicCapsule.atTurnEnd(state))
        assertTrue(MechanicCapsule.BANK_TOLL in MechanicCapsule.atTurnEnd(state, bankDepositThisTurn = true))
    }

    /** Las herramientas de ficha se enseñan cuando aparecen en la bandeja, no antes. */
    @Test
    fun `la brocha se explica cuando llega a la bandeja`() {
        val level = LevelMap.introLevels + 1
        val base = GameState.initial(level)
        assertTrue(MechanicCapsule.BRUSH !in MechanicCapsule.atTurnEnd(base))
        val conBrocha = base.copy(
            availablePieces = base.availablePieces.mapIndexed { i, p ->
                if (i == 0) p.copy(isBrush = true) else p
            }
        )
        assertTrue(MechanicCapsule.BRUSH in MechanicCapsule.atTurnEnd(conBrocha))
    }

    /**
     * **La línea pura se explica el turno en que se cobra**, y no antes: es el premio, no una regla
     * del tablero. La señal llega desde fuera (`pureLineThisTurn`) porque cuando el turno se cierra
     * su evento ya se lo ha llevado el VFX — preguntárselo a la cola daría `false` casi siempre.
     */
    @Test
    fun `la linea pura se explica el turno que se rompe`() {
        val level = LevelMap.introLevels + 1
        val base = GameState.initial(level)
        assertTrue(MechanicCapsule.PURE_LINE !in MechanicCapsule.atTurnEnd(base))
        assertTrue(
            MechanicCapsule.PURE_LINE in
                MechanicCapsule.atTurnEnd(base, pureLineThisTurn = true)
        )
        // Y va la PRIMERA: es un momento, y las herramientas de la bandeja siguen ahí mañana.
        assertEquals(
            MechanicCapsule.PURE_LINE,
            MechanicCapsule.atTurnEnd(base, teikasOnBoard = true, pureLineThisTurn = true).first()
        )
    }

    /**
     * **NINGÚN RÓTULO PUEDE MENTIR SOBRE SU MECÁNICA** (2026-09-02).
     *
     * Los catorce rótulos de bioma se escribieron de una tacada el 1 de septiembre y **tres no
     * describían la regla implementada** — que es exactamente lo que CLAUDE.md prohíbe para
     * `mechanicHint` y vale igual aquí, porque la cápsula es lo único que el jugador lee cuando el
     * bioma le golpea por primera vez:
     *
     *  - **Volcán**: decía que la lava «sube» y `advanceLavaFromTop` la trae desde arriba (el
     *    terreno físico bloquea las filas `0 until lavaRow`). El jugador defendería el lado
     *    equivocado del tablero.
     *  - **Glaciar**: decía «escarcha», una mecánica **retirada en el rework de julio**
     *    (`glacierFrostCells` se construye vacío). Lo que queda es la oleada que congela un Teika.
     *  - **Mansión**: decía «roba tus estrellas», que es el caso de RESPALDO. La cacería busca
     *    primero un Teika (`GhostHunt(kind = 1)`).
     *
     * Se fijan como PROPIEDADES y no como cadenas exactas: el texto se puede pulir, la regla no.
     */
    @Test
    fun `los rotulos de bioma dicen la regla real`() {
        val volcan = MechanicCapsule.BIOME_VOLCANO.label.lowercase()
        assertTrue("el Volcán vuelve a decir que la lava sube", "sube" !in volcan)
        assertTrue("el Volcán tiene que decir que la lava BAJA", "baja" in volcan)

        val glaciar = MechanicCapsule.BIOME_GLACIER.label.lowercase()
        assertTrue("la escarcha sobre madera no existe desde el rework de julio", "escarcha" !in glaciar)
        assertTrue("el Glaciar congela un TEIKA", "teika" in glaciar)
    }

    /** El rótulo de la Mansión cuenta la regla del Espejo (P-246), no la cacería retirada. */
    @Test
    fun `la mansion habla del espejo`() {
        val rotulo = MechanicCapsule.BIOME_GHOST.labelFor(GameState.initial(LevelMap.introLevels + 1)).lowercase()
        assertTrue("el rótulo de la Mansión no habla del espejo: $rotulo", "espejo" in rotulo)
        assertTrue("la cacería ya no roba nada: $rotulo", "roba" !in rotulo)
        // Cumple la misma regla de longitud que el resto de rótulos.
        assertTrue(rotulo.trim().split(" ").size <= 6)
    }

    /**
     * **P-96 · qué se puede encolar y qué no.** Cuando dos mecánicas nuevas caen en la misma tanda
     * solo se enseña una y la otra espera al turno siguiente — pero un MOMENTO (la línea pura, el
     * golpe de un bioma) no se puede guardar: enseñarlo dos turnos después señalaría un tablero en
     * el que ya no queda nada de lo que se cuenta. Lo demás sigue ahí y espera sin mentir.
     */
    @Test
    fun `los momentos son la linea pura, el premio de la bandeja y los catorce biomas`() {
        val momentos = MechanicCapsule.entries.filter { it.isMoment }.toSet()
        val biomas = BiomeType.entries.mapNotNull { MechanicCapsule.forBiome(it) }.toSet()
        assertEquals(biomas + MechanicCapsule.PURE_LINE + MechanicCapsule.TRAY_REWARD, momentos)
        // Y las reglas del nivel NO lo son: siguen en el tablero el turno que viene.
        listOf(
            MechanicCapsule.GRAVITY, MechanicCapsule.HORIZON, MechanicCapsule.CURRENT,
            MechanicCapsule.HOT_CELL, MechanicCapsule.DOMINO,
            MechanicCapsule.BRUSH, MechanicCapsule.CHAMELEON,
            MechanicCapsule.GROWING, MechanicCapsule.RELAY, MechanicCapsule.WILDCARD,
            MechanicCapsule.TEIKA_ROLE,
        ).forEach { assertTrue("${it.name} no debería ser un momento", !it.isMoment) }
    }

    /** Las gemelas se detectan por FORMA, no por id: son dos ranuras con la misma huella. */
    @Test
    fun `las gemelas se detectan por forma`() {
        val level = LevelMap.introLevels + 1
        val base = GameState.initial(level)
        val forma = listOf(Position(0, 0), Position(1, 0))
        val gemelas = base.copy(
            availablePieces = listOf(
                Piece(id = "a", shape = forma, colorType = 0),
                Piece(id = "b", shape = forma.map { Position(it.x + 3, it.y + 2) }, colorType = 1),
                Piece(id = "c", shape = listOf(Position(0, 0)), colorType = 2),
            )
        )
        assertTrue(MechanicCapsule.RELAY !in MechanicCapsule.atTurnEnd(gemelas))
    }
}
