package com.cukiti.teikafusion.ui

import com.cukiti.teikafusion.model.TutorialCampaign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contrato del PULIDO DEL TUTORIAL del 2026-09-01 (grabación `analisistutorial`).
 *
 * Cada aserción de aquí abajo corresponde a un fallo que se vio en el vídeo. No fijan estética ni
 * redacción —eso cambia— sino la relación que hacía que el tutorial se sintiera roto: quién se
 * dibuja encima de quién, quién espera a quién y qué pasa cuando el jugador NO hace lo que se le
 * pide. Son de fuente porque estas piezas son Compose puro y no hay una sola función pura que
 * interrogar.
 */
class TutorialPolishContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, GAME_SCREEN).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo" }
    }

    private fun read(path: String) = File(repoRoot, path).readText()

    @Test
    fun `la ventana de victoria espera al instante de ensenanza`() {
        val vm = read(VIEW_MODEL)
        // El reloj de la victoria no puede abrirse con la explicación todavía encima: en el vídeo el
        // aro del foco cruzaba por delante del panel de «lección completada» y el rótulo le tapaba el
        // título.
        assertTrue(vm.contains("private suspend fun awaitSlowMotionIdle()"))
        assertTrue(vm.contains("LEVEL_COMPLETE_AFTER_LESSON_MS"))
        // Y en este ORDEN: primero el remate de la lección, después la espera, y solo entonces la
        // ventana. Invertirlo devuelve el solape exacto del vídeo.
        val schedule = vm.indexOf("private fun scheduleLevelCompleteConfirm(")
        val idle = vm.indexOf("awaitSlowMotionIdle()", schedule)
        val confirm = vm.indexOf("confirmLevelComplete()", idle)
        assertTrue("awaitSlowMotionIdle no precede a confirmLevelComplete", idle in 1 until confirm)
    }

    @Test
    fun `la capa de ensenanza se retira cuando hay una ventana de estado`() {
        val gameScreen = read(GAME_SCREEN)
        assertTrue(gameScreen.contains("val teachingLayerVisible"))
        assertTrue(gameScreen.contains("shot = slowMotionShot.takeIf { teachingLayerVisible }"))
    }

    @Test
    fun `el instante de ensenanza traga los toques salvo lo que senala`() {
        val overlay = read(SLOW_MOTION)
        assertTrue(overlay.contains("PointerEventPass.Initial"))
        assertTrue(overlay.contains("changedToDownIgnoreConsumed()"))
        // La excepción: lo que está dentro del foco sigue vivo (la lección 7 señala un botón que hay
        // que tocar). Sin ella el foco apuntaría a algo que no responde.
        assertTrue(overlay.contains("liveFocusRects"))
        // Y nunca con `clickable {}` — regla del bloque 24.
        assertFalse(overlay.contains(".clickable("))
    }

    @Test
    fun `cada foco de HUD apunta a su propia pieza`() {
        val overlay = read(SLOW_MOTION)
        // GOAL y MOVES compartían el rectángulo de la barra de puntos. En la lección que enseña el
        // objetivo esa barra está oculta, así que el foco llegaba vacío y el shot oscurecía la
        // pantalla sin iluminar nada.
        assertTrue(overlay.contains("objectivePanelRect"))
        assertTrue(overlay.contains("movesRect"))
        assertTrue(overlay.contains("SlowMotionFocus.GOAL -> firstMeasured(objectivePanelRect, goalRect)"))
        assertTrue(overlay.contains("SlowMotionFocus.MOVES -> firstMeasured(movesRect, goalRect)"))

        val gameScreen = read(GAME_SCREEN)
        assertTrue(gameScreen.contains("onPanelPositioned"))
        assertTrue(gameScreen.contains("onMovesPositioned"))
    }

    @Test
    fun `la jugada fuera de la casilla guiada se rechaza en la sombra y se explica`() {
        val vm = read(VIEW_MODEL)
        val gameScreen = read(GAME_SCREEN)
        // La sombra deja de mentir: si la lección no admite ahí, no se pinta como válida.
        val preview = vm.indexOf("private fun refreshDragPreview(")
        val previewEnd = vm.indexOf("fun conflictCells", preview)
        assertTrue(
            "refreshDragPreview no consulta la lección guiada",
            vm.substring(preview, previewEnd).contains("isCorrectPlacement")
        )
        assertTrue(vm.contains("fun isTutorialMisplacement("))
        // …y el suelte rechazado dice por qué, en vez de ofrecer revivir.
        assertTrue(gameScreen.contains("viewModel.isTutorialMisplacement("))
        assertTrue(gameScreen.contains("tutorialMisplaceToken++"))
        assertTrue(read(TUTORIAL_OVERLAY).contains("misplaceToken"))
    }

    @Test
    fun `la pildora de progreso nunca se sienta sobre el tirador de habilidades`() {
        val overlay = read(TUTORIAL_OVERLAY)
        // Las dos se anclan al hueco bajo la bandeja y en la lección 7 la píldora tapaba el único
        // botón que resuelve la lección.
        assertTrue(overlay.contains("positions.abilityHandle"))
        assertTrue(overlay.contains("if (handleLive && progressTopPx < handle.bottom) return@BoxWithConstraints"))
        assertTrue(read(GAME_SCREEN).contains("abilityHandle = abilityHandleRect"))
    }

    @Test
    fun `el titulo de la leccion completada va centrado y cierra con su propia regla`() {
        val overlays = read(OVERLAYS)
        assertTrue(overlays.contains("text = spec.completionTitle"))
        assertTrue(overlays.contains("text = spec.completionNote"))
        // «¡INCLINACIÓN / DOMINADA!» partía en dos y se alineaba a la izquierda dentro de una ventana
        // centrada.
        assertFalse(
            overlays.contains("Text(spec.completionTitle, color = Color.White, fontSize = 26.sp")
        )
        // Y la despedida de cada lección habla de ESA lección: la última felicitaba por la fusión.
        assertFalse(overlays.contains("\"Ya dominas la fusión\""))
    }

    @Test
    fun `cada leccion declara su frase de cierre y ninguna repite la de otra`() {
        val notes = (TutorialCampaign.FIRST_LEVEL..TutorialCampaign.LAST_LEVEL).map { level ->
            val note = TutorialCampaign.spec(level)!!.completionNote
            assertTrue("lección $level sin frase de cierre", note.isNotBlank())
            assertFalse("lección $level: la frase no puede ser un número de lección", note.contains("Lección"))
            note
        }
        assertEquals("hay frases de cierre repetidas", notes.size, notes.toSet().size)
    }

    @Test
    fun `el contador del objetivo no cambia de alto al marcar el check`() {
        val panel = read(GAME_INFO_PANEL)
        // El «✓» es más alto que un dígito: sin caja de línea fija, el chip crecía y empujaba el
        // tablero y la bandeja hacia abajo justo al ganar.
        assertTrue(panel.contains("LineHeightStyle.Trim.None"))
        // El factor pasó a constante compartida con `tutorialHudSlotHeight` (P-89): la caja de línea
        // del contador es justo lo que el hueco del tutorial tiene que reservar.
        assertTrue(panel.contains("lineHeight = textSize * OBJECTIVE_TEXT_LINE_FACTOR"))
        assertTrue(panel.contains("const val OBJECTIVE_TEXT_LINE_FACTOR = 1.25f"))
    }

    @Test
    fun `la leccion de habilidades solo se aprueba lanzando`() {
        val vm = read(VIEW_MODEL)
        val state = read(GAME_STATE)
        // P-87: su tablero son tres filas a las que faltan dos celdas, asi que el hito de LINEAS era
        // alcanzable a mano y despedia al jugador con «¡HABILIDAD LANZADA!» sin haber lanzado nada.
        assertTrue(state.contains("val tutorialAbilityCast: Boolean = false"))
        assertTrue(vm.contains("tutorialAbilityCast = true"))
        assertTrue(vm.contains("!tutorialSpec.teachesAbility || state.tutorialAbilityCast"))
        // Y la marca la pone el LANZAMIENTO, no la cadena del tablero.
        val cast = vm.indexOf("private fun completeAbilityLessonWhenCast()")
        val mark = vm.indexOf("tutorialAbilityCast = true", cast)
        val nextFun = vm.indexOf("fun canCastCompanionAbility", cast)
        assertTrue("la marca no vive en completeAbilityLessonWhenCast", mark in 1 until nextFun)
    }

    @Test
    fun `el numero de nivel que se muestra descuenta las lecciones`() {
        // P-92: el primer nivel de verdad se anunciaba como «NIVEL 8 COMPLETADO».
        assertTrue(read(OVERLAYS).contains("LevelMap.displayNumber(level)} COMPLETADO"))
        val home = read(HOME_SCREEN)
        assertTrue(home.contains("displayWord(selectedLevel)"))
        assertTrue(home.contains("displayNumber(selectedLevel)"))
        // Ningun sitio visible puede quedarse con el numero crudo. Se buscan las formas EXACTAS que
        // regresaron (con su comilla o su espacio de cierre): «NIVEL $level/3» es el nivel de una
        // habilidad de companero, no una casilla del mapa, y no entra aqui.
        val rawForms = listOf(
            "NIVEL \$level\"", "NIVEL \$level ", "NIVEL \$selectedLevel\"", "NIVEL \$selectedLevel "
        )
        for (path in listOf(OVERLAYS, HOME_SCREEN, LEVEL_OBJECTIVE, ADVENTURE_MAP, INVENTORY)) {
            val src = read(path)
            rawForms.forEach { form ->
                assertFalse("$path muestra el nivel crudo: $form", src.contains(form))
            }
        }
    }

    @Test
    fun `la entrada del Escudo Cristalino usa el color de Cristobal y no traga el tablero`() {
        val vfx = read(VFX)
        // P-88: las facetas iban en un azul hielo casi blanco al 16 % sobre un tablero negro, asi que
        // salian GRISES y parecian un fallo de dibujo. El tono esta muestreado de la propia carta de
        // «¡NUEVO PERSONAJE!» de Cristobal.
        assertTrue(vfx.contains("private val CRYSTAL_TEAL = Color(0xFF70DED5)"))
        assertFalse(vfx.contains("private val CRYSTAL_FACE = Color(0xFFBFE9FF)"))
        // Y enmarcaban el tablero en vez de comerselo: un tercio del lado por cada borde.
        val aegis = vfx.indexOf("fun CrystalAegisVFX(")
        val aegisEnd = vfx.indexOf("private val CRYSTAL_TEAL", aegis)
        val body = vfx.substring(aegis, aegisEnd)
        assertFalse("las cunas vuelven a tragarse el tablero", body.contains("0.34f * ease"))
        assertTrue(body.contains("0.15f * ease"))
        // El escudo se repliega en vez de cortarse en seco al terminar el evento.
        assertFalse(body.contains("0f, 1f) * 0.55f"))
        // Una cuna por columna: alineadas con la rejilla, no siete sueltas sobre ocho.
        assertTrue(body.contains("GameState.GRID_SIZE"))
    }

    @Test
    fun `el bloque superior reserva el mismo alto en las siete lecciones`() {
        // P-89: la leccion 5 ensena el panel de estrellas y la 6 la barra con el medallon, nunca las
        // dos. Sin hueco reservado, el bloque se sustituia por otro de distinto alto y el tablero
        // saltaba con la bandeja detras.
        val panel = read(GAME_INFO_PANEL)
        assertTrue(panel.contains("fun tutorialHudSlotHeight(): Dp"))
        // Y se CALCULA de las mismas medidas que usa el panel: dos copias del numero se separan.
        assertTrue(panel.contains("OBJECTIVE_STAR_WIDE - OBJECTIVE_CHIP_OVERLAP"))
        assertTrue(panel.contains("OBJECTIVE_TEXT_WIDE * OBJECTIVE_TEXT_LINE_FACTOR"))
        assertFalse("las medidas del chip vuelven a estar sueltas", panel.contains("if (chipCount >= 4) 40.dp else 52.dp"))

        val gameScreen = read(GAME_SCREEN)
        assertTrue(gameScreen.contains("val tutorialHudSlot = tutorialHudSlotHeight()"))
        assertTrue(gameScreen.contains("Modifier.heightIn(min = tutorialHudSlot)"))
    }

    @Test
    fun `la pildora de progreso se retira con la hoja de habilidades abierta`() {
        // Al abrirse la hoja el tirador sale de la composicion y su rectangulo se congela, asi que la
        // pildora se plantaba sobre la tarjeta de Cristobal (grabacion `analisistutorial`, 1:24).
        // (perf 2026-09-26) El umbral se deriva con `derivedStateOf` para no recomponer la pantalla
        // en cada fotograma de la apertura; la regla es la misma.
        val gameScreen = read(GAME_SCREEN)
        assertTrue(
            gameScreen.contains("val boosterOpen by remember { derivedStateOf { boosterProgress.floatValue > 0.01f } }")
        )
        assertTrue(gameScreen.contains("teachingShotActive = slowMotionShot != null || boosterOpen"))
    }

    @Test
    fun `las lecciones no ensenan marcador ni hacen volar cifras`() {
        val topBar = read(TOP_BAR)
        val gameScreen = read(GAME_SCREEN)
        // P-90: la campana declara que los tutoriales no califican —no dan estrellas, no tocan el
        // record, no cobran fusion— y el jugador veia 3.500 puntos subiendo mientras aprendia a
        // arrastrar una ficha.
        assertTrue(topBar.contains("showScore: Boolean = true"))
        assertTrue(topBar.contains("if (showScore) {"))
        assertTrue(gameScreen.contains("showScore  = !tutorialLevel"))
        // Pero la CAPSULA se queda: es tambien la salida de la partida.
        assertTrue(topBar.contains("\"Salir de la partida\""))
        // Y sin marcador las cifras no tienen adonde volar: se retiran TODAS, con un solo consumidor.
        assertTrue(
            gameScreen.contains("if (tutorialLevel) emptyList() else scoreFlyAll.takeLast(1)")
        )
        assertTrue(
            gameScreen.contains("if (tutorialLevel) scoreFlyAll.map { it.id } else scoreFlyAll.dropLast(1).map { it.id }")
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // EL LOTE DEL 2026-09-04 (reinstalación + grabación del flujo del tutorial). Cada prueba de
    // aquí abajo corresponde a algo que el usuario vio roto al instalar el juego de cero.
    // ─────────────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `una leccion pendiente no tiene puerta trasera`() {
        val gameScreen = read(GAME_SCREEN)
        val topBar = read(TOP_BAR)
        // Tres salidas se saltaban el guion: el «❮», el engranaje (que abría el panel de pausa
        // ENTERO —Continuar, Reiniciar, Salir al inicio— desde dentro de la primera lección) y el
        // atrás del sistema. La única salida es SALTAR TUTORIAL, que además deja la cuenta
        // coherente. Reabrir cualquiera de las tres devuelve el bug del vídeo.
        assertTrue(gameScreen.contains("val tutorialLocked = tutorialLevel && tutorialPendingNow"))
        assertTrue(gameScreen.contains("BackHandler(enabled = tutorialLocked)"))
        assertTrue(gameScreen.contains("showExits  = !tutorialLocked"))
        assertTrue(topBar.contains("showExits: Boolean = true"))
        assertTrue(topBar.contains("if (!showExits) {"))
        assertTrue(topBar.contains("if (showExits) {"))
        // Y el diálogo de salida NO puede seguir armado durante la lección.
        assertTrue(gameScreen.contains("enabled = !tutorialLocked &&"))
    }

    @Test
    fun `saltar el tutorial abre el primer nivel de verdad y sale al inicio`() {
        val vm = read(VIEW_MODEL)
        // Sin comentarios: el porqué del arreglo cita justamente la llamada que ya no debe existir.
        val skip = vm.substringAfter("fun skipTutorial()")
            .substringBefore("fun acceptLegalDocuments()")
            .lineSequence()
            .filterNot { it.trimStart().startsWith("//") || it.trimStart().startsWith("*") }
            .joinToString(System.lineSeparator())
        // El bug de la reinstalación: esto llamaba a `startGame(LAST_LEVEL + 1)` y la primera guarda
        // de `startGame` es `if (level > maxUnlockedLevel) return`. Con la frontera en 1 —o sea, en
        // una instalación limpia, que es EL caso para el que existe el botón— saltar no hacía nada.
        assertTrue("saltar el tutorial debe mover la frontera", skip.contains("prefs.saveMaxLevel(newMax)"))
        assertFalse("saltar el tutorial no entra a jugar", skip.contains("startGame("))
        // Y termina en INICIO, con el cursor en el primer nivel de verdad (pedido del usuario).
        assertTrue(skip.contains("setSelectedLevel(firstRealLevel)"))
        assertTrue(skip.contains("goHome()"))
    }

    @Test
    fun `las lecciones que no ensenan Teikas no los crian`() {
        // La 1 rompe cuatro filas de golpe y dejaba al jugador con cuatro cuerpos rodando antes de
        // que nadie le hubiera dicho qué son: spoiler de la lección 2, que es la que los presenta.
        // La 6 enseña el medallón de turnos y un cuerpo naciendo compite con el único dato que hay
        // que mirar.
        assertFalse(TutorialCampaign.spec(1)!!.breedsTeikas)
        assertFalse(TutorialCampaign.spec(6)!!.breedsTeikas)
        // La 2 es justo la que enseña que la línea CRÍA: si esta se apagara, la lección se queda sin
        // lección (y sin su TEIKA_2, que es su condición de victoria).
        assertTrue(TutorialCampaign.spec(2)!!.breedsTeikas)
        assertTrue("fuera del tutorial nunca se corta", TutorialCampaign.breedsTeikas(120))
        // Y se corta en la ÚNICA puerta de nacimiento, no en cada llamante.
        val vm = read(VIEW_MODEL)
        val spawn = vm.indexOf("private fun spawnLineClearTeika(")
        val gate = vm.indexOf("TutorialCampaign.breedsTeikas(nivel)", spawn)
        val end = vm.indexOf("private fun migrateGridTeikas(", spawn)
        assertTrue("la guarda no vive dentro de spawnLineClearTeika", gate in 1 until end)
    }

    @Test
    fun `la sombra del destino se retira al agarrar la ficha y al colocarla`() {
        val overlay = read(TUTORIAL_OVERLAY)
        val gameScreen = read(GAME_SCREEN)
        // Se quedaba pintada encima de la ficha durante todo el aterrizaje, con el fantasma real del
        // arrastre debajo: dos capas cian sobre las mismas celdas.
        assertTrue(overlay.contains("!piecePlaced && !pieceGrabbed"))
        // Y «colocada» pasa a preguntar por la FICHA GUIADA y no por el recuento de la bandeja:
        // cualquier camino que reponga fichas devolvía la sombra a un destino que ya no existía.
        assertTrue(gameScreen.contains("val guidedPieceId ="))
        assertTrue(gameScreen.contains("gameState.availablePieces.none { it.id == guidedPieceId }"))
        assertFalse(gameScreen.contains("piecePlaced = gameState.availablePieces.isEmpty()"))
    }

    @Test
    fun `el rotulo del instante espera a que la mecanica se vea`() {
        val overlay = read(SLOW_MOTION)
        val vm = read(VIEW_MODEL)
        // El shot de la lección 2 se dispara con la línea rompiéndose, así que el rótulo caía en el
        // mismo fotograma ENCIMA de los dos Teikas naciendo y fundiéndose.
        val leccionDos = TutorialCampaign.spec(2)!!.payoffShot!!
        assertTrue("la lección 2 necesita su compás previo", leccionDos.leadInMs > 0)
        // …y a 0,22 el shot solo movía 0,6 s de física: la fusión ocurría FUERA del instante.
        assertTrue("la fusión tiene que caber dentro del instante", leccionDos.factor >= 0.4f)
        assertTrue(
            "el instante entero sigue bajo el tope",
            leccionDos.totalMs <= com.cukiti.teikafusion.model.SlowMotionShot.MAX_MS
        )
        // El compás lo respetan las dos mitades: el reloj y el dibujo.
        assertTrue(vm.contains("if (lead > 0) kotlinx.coroutines.delay(lead.toLong())"))
        assertTrue(overlay.contains("live.leadInMs"))
        // Y el instante de enseñanza deja de ser mudo (regla de los MOMENTOS MUDOS).
        assertTrue(vm.contains("soundManager.playTeachingBeat()"))
    }



    /**
     * **P-201 · «ESA FICHA» TIENE QUE SEÑALAR A ESA FICHA** (2026-09-05, reportado por el usuario:
     * «cuando el tutorial está dando info sobre una ficha no la señala, señala el panel completo»).
     *
     * Media docena de cápsulas nombran una ficha concreta —«Esa ficha viene tapada», «En orden:
     * primero esa», «Si no la usas, crece»— y el foco iluminaba los tres cuadros. Es el mismo fallo
     * que GOAL y MOVES ya tuvieron con la barra de puntos, en la bandeja.
     */
    @Test
    fun `una capsula que nombra una ficha señala esa ficha`() {
        val capsules = read(CAPSULE)
        // La cápsula resuelve QUÉ ficha, igual que ya resolvía qué celdas del tablero.
        assertTrue(capsules.contains("private fun trayPieceIdsFor("))
        val block = capsules.substringAfter("private fun trayPieceIdsFor(")
            .substringBefore("private fun cellsFor(")
        listOf("BRUSH", "CHAMELEON", "GROWING", "WILDCARD", "RESERVE", "FORCED_QUEUE", "RELAY")
            .forEach { assertTrue("$it no señala su ficha", block.contains(it)) }

        val overlay = read(SLOW_MOTION)
        assertTrue(overlay.contains("trayPieceRects"))
        // Y si la ficha no está medida, se cae a la bandeja entera: nunca a un velo sin foco.
        assertTrue(overlay.contains("if (pedidas.isEmpty()) toRoot(trayRect)"))

        // El rectángulo de cada hueco se mide de verdad en la bandeja.
        assertTrue(read(GAME_SCREEN).contains("trayPieceRectsWindow[piece.id] = it.boundsInWindow()"))
    }

    /**
     * **P-203 · NINGÚN FOCO DE HUD PUEDE QUEDARSE MUDO.** GOAL y MOVES ya caían a un contenedor
     * medido; el TIRADOR no, y es el que enseña la lección 7. Sin rectángulo, el shot oscurece la
     * pantalla entera y no ilumina nada.
     */
    @Test
    fun `el foco del tirador cae a la bandeja si aun no se ha medido`() {
        val overlay = read(SLOW_MOTION)
        assertTrue(
            overlay.contains("SlowMotionFocus.ABILITY_TAB -> firstMeasured(abilityTabRect, trayRect)")
        )
    }

    /**
     * **EL GIRO VUELVE, POR UNA SOLA PUERTA** (P-276, 2026-09-24, decisión del usuario).
     *
     * Historia de este test: nació como P-202 (la ficha guiada de una lección no rota, porque la
     * sombra del destino y el clon de la mano usan la huella CANÓNICA y `isCorrectPlacement` compara
     * **id y origen, no forma**); el 2026-09-07 el giro se retiró del juego entero y pasó a defender
     * que no volviera. Ahora vuelve **libre e ilimitado** y el contrato fija cómo:
     *
     * - Una puerta: el toque seco en la bandeja (`rotatePieceInTray`) y su gemela accesible, la
     *   acción «Rotar». El segundo dedo durante el arrastre (P-15) NO vuelve.
     * - El tutorial sigue sin girar — por el mismo motivo que P-202.
     * - **La detección de jugada prueba las cuatro rotaciones.** Con el giro en manos del jugador,
     *   mirar solo la orientación repartida declararía perdida una partida que no lo está: la forma
     *   ruidosa del mismo error que antes era silencioso.
     */
    @Test
    fun `el giro vuelve por el toque en la bandeja y la derrota lo cuenta`() {
        val vm = read(VIEW_MODEL)
        val screen = read(GAME_SCREEN)

        // La puerta que vuelve, con su gemela accesible.
        assertTrue(vm.contains("fun rotatePieceInTray("))
        assertTrue(screen.contains("viewModel.rotatePieceInTray("))
        assertTrue(screen.contains("CustomAccessibilityAction(\"Rotar\")"))
        // La que no vuelve.
        assertFalse(vm.contains("fun rotateDraggedPiece("))
        assertFalse(screen.contains("viewModel.rotateDraggedPiece("))

        // El tutorial no gira.
        val rotate = vm.substringAfter("fun rotatePieceInTray(").substringBefore("\n    }\n")
        assertTrue(rotate.contains("TutorialCampaign.isTutorialLevel"))
        assertTrue(rotate.contains("isPieceAsleep"))

        // Quien pregunta «¿queda jugada?» o «¿esta ficha ya no cabe?» prueba las rotaciones.
        val legal = vm.substringAfter("private fun hasAnyLegalPlacement(").substringBefore("\n    }\n")
        assertTrue(legal.contains("allRotations("))
        val doomed = vm.substringAfter("private fun markUnplayableTrayPieces(").substringBefore("\n    }\n")
        assertTrue(doomed.contains("allRotations("))

        // La píldora de corrección sigue siendo un parámetro (la otra causa de rechazo sigue viva).
        assertTrue(read(TUTORIAL_OVERLAY).contains("misplaceMessage"))
    }

    /**
     * **QUEDARSE SIN JUGADA VUELVE A SER UNA DERROTA** (2026-09-07, decisión del usuario).
     *
     * `replaceUnplaceableTrayPieces` cambiaba, al cerrar cada turno, toda ficha que se hubiera
     * quedado sin una sola colocación legal por otra que sí cabía. Con eso el jugador **no podía
     * perder por atasco**: el juego se reparaba antes. Se retiró en todos los casos, incluido el
     * Corte Ninja, que era el motivo por el que nació.
     *
     * Lo que este contrato fija es que la red no vuelva a colarse por la puerta de atrás, y que lo
     * que SÍ se queda siga en su sitio: `ensureTrayNotEmpty` —una bandeja vacía no es una derrota,
     * es una congelación sin mensaje (C-8)— y los dos indultos de `BoardDeadEnd`.
     */
    @Test
    fun `la bandeja sin jugada no se repara sola`() {
        val vm = read(VIEW_MODEL)
        assertFalse(vm.contains("fun replaceUnplaceableTrayPieces("))
        assertFalse(vm.contains("replaceUnplaceableTrayPieces()"))
        // El evento del reparador se fue con él: un evento sin productor es un fósil.
        assertFalse(read(GAME_EVENT).contains("data class TrayPieceSwapped("))
        assertFalse(read(GAME_SCREEN).contains("GameEvent.TrayPieceSwapped"))
        // Y lo que no se toca.
        assertTrue(vm.contains("ensureTrayNotEmpty()"))
        assertTrue(vm.contains("BoardDeadEnd"))
    }

    private companion object {
        const val GAME_SCREEN = "app/src/main/java/com/cukiti/teikafusion/ui/GameScreen.kt"
        const val OVERLAYS = "app/src/main/java/com/cukiti/teikafusion/ui/Overlays.kt"
        const val SLOW_MOTION = "app/src/main/java/com/cukiti/teikafusion/ui/SlowMotionOverlay.kt"
        const val TUTORIAL_OVERLAY =
            "app/src/main/java/com/cukiti/teikafusion/ui/TutorialGuideOverlay.kt"
        const val GAME_INFO_PANEL = "app/src/main/java/com/cukiti/teikafusion/ui/GameInfoPanel.kt"
        const val VFX = "app/src/main/java/com/cukiti/teikafusion/ui/VFX.kt"
        const val TOP_BAR = "app/src/main/java/com/cukiti/teikafusion/ui/GameTopBar.kt"
        const val VIEW_MODEL =
            "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt"
        const val GAME_STATE = "app/src/main/java/com/cukiti/teikafusion/model/GameState.kt"
        const val HOME_SCREEN = "app/src/main/java/com/cukiti/teikafusion/ui/HomeScreen.kt"
        const val LEVEL_OBJECTIVE =
            "app/src/main/java/com/cukiti/teikafusion/ui/LevelObjectiveOverlay.kt"
        const val ADVENTURE_MAP =
            "app/src/main/java/com/cukiti/teikafusion/ui/BiomeAdventureMap.kt"
        const val INVENTORY = "app/src/main/java/com/cukiti/teikafusion/ui/InventoryScreen.kt"
        const val CAPSULE = "app/src/main/java/com/cukiti/teikafusion/model/MechanicCapsule.kt"
        const val TUTORIAL_CAMPAIGN =
            "app/src/main/java/com/cukiti/teikafusion/model/TutorialCampaign.kt"
        const val GAME_EVENT = "app/src/main/java/com/cukiti/teikafusion/model/GameEvent.kt"
    }
}
