package com.cukiti.teikafusion.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.cukiti.teikafusion.ui.PowerUpButtonLayer
import com.cukiti.teikafusion.ui.AbilityEchoLabel
import androidx.compose.ui.draw.drawBehind
import com.cukiti.teikafusion.model.DefeatCause
import com.cukiti.teikafusion.ui.BlockTileStyle
import com.cukiti.teikafusion.ui.drawStyledTile
import com.cukiti.teikafusion.ui.styledTile
import com.cukiti.teikafusion.ui.teikaSeedTile
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.cukiti.teikafusion.ui.ConfirmOverlay
import com.cukiti.teikafusion.ui.PauseOverlay
import com.cukiti.teikafusion.ui.SettingsOverlay
import com.cukiti.teikafusion.ui.QuitDialogOverlay
import com.cukiti.teikafusion.ui.ReviveOverlay
import com.cukiti.teikafusion.ui.theme.TeikaFusionTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * **Capturas de las ventanas, sin emulador** (paso 4, 2026-09-22). Compose se renderiza en la JVM
 * (Robolectric, gráficos nativos) y Roborazzi compara contra la imagen guardada en
 * `app/src/test/screenshots/`. Sirve de REGRESIÓN VISUAL: si un cambio mueve un botón, corta un
 * texto o rompe un color de estas ventanas, `verifyRoborazziDebug` falla y enseña el diff.
 *
 * No sustituye al playtest (no hay animación, ni tacto, ni el móvil real): responde a «¿se sigue
 * viendo como se veía?», no a «¿se siente bien?».
 *
 * ```
 * ./gradlew.bat :app:recordRoborazziDebug   # (re)graba las imágenes de referencia
 * ./gradlew.bat :app:verifyRoborazziDebug   # compara y falla si algo cambió
 * ./gradlew.bat :app:compareRoborazziDebug  # genera el diff sin fallar
 * ```
 *
 * Móvil de referencia: 411×891 dp, xxhdpi (un Pixel 7). Las ventanas animan su entrada con un
 * resorte, así que el reloj se congela y se adelanta a mano hasta que están quietas.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class OverlayScreenshotTest {

    @get:Rule val compose = createComposeRule()

    /**
     * [tolerance]: fracción de píxeles que puede cambiar sin fallar. 0 salvo en las ventanas con
     * reloj de pared (`ReviveOverlay` cuenta con `System.currentTimeMillis()`: el anillo de la cuenta
     * atrás sale distinto en cada grabación). El margen cubre ese anillo, no un texto cortado.
     */
    private fun snap(name: String, tolerance: Float = 0f, content: @Composable () -> Unit) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TeikaFusionTheme {
                // Fondo de partida: las ventanas son translúcidas y se juzgan sobre el juego.
                Box(Modifier.fillMaxSize().background(Color(0xFF405384))) { content() }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage(
            "src/test/screenshots/$name.png",
            roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(
                compareOptions = com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions(changeThreshold = tolerance)
            )
        )
    }

    /** P-263: las estrellas ya están y solo falta el MEGA — el chip late y cuelga su etiqueta. */
    @Test fun objetivo_solo_falta_mega() = snap("objetivo_solo_falta_mega") {
        val blue = com.cukiti.teikafusion.model.TargetColor.BLUE
        val red = com.cukiti.teikafusion.model.TargetColor.entries.first { it != blue }
        androidx.compose.foundation.layout.Column(Modifier.fillMaxSize()) {
            com.cukiti.teikafusion.ui.GameInfoPanel(
                levelObjective = com.cukiti.teikafusion.model.LevelObjective(mapOf(blue to 28, red to 27)),
                targetsCollected = mapOf(blue to 32, red to 27),
                megaRequired = true,
                megaFormed = false,
                megaPending = true,
            )
            com.cukiti.teikafusion.ui.ScoreGoalBar(score = 18_600, level = 289)
        }
    }

    // ── P-282 · POWER-UPS: el selector del panel del nivel y el botón de partida. ──
    /**
     * Margen de las capturas con emblemas `ic_ability_*`. El bitmap es el mismo (la caché de `RawImage`
     * lo decodifica siempre al ancho de pantalla), pero Skia filtra distinto un bitmap que otro test ya
     * dibujó a otra escala: según el ORDEN de los tests, los emblemas repetidos salen con el borde un
     * poco más o menos nítido (medido 2026-09-25: solo los iconos compartidos, ~1 % del lienzo). Cubre
     * eso, no una casilla movida ni un texto cortado, que cambian mucho más.
     */
    private val ICON_TOLERANCE = 0.03f

    /**
     * Margen de las capturas de PARTÍCULAS (firma al colocar, rotura): cada evento nace con un id
     * aleatorio (`UUID`) y las partículas se siembran con él, así que su posición cambia entre
     * ejecuciones. Lo que estas capturas vigilan es que cada bloque tenga SU efecto, no dónde cae cada
     * chispa.
     */
    private val PARTICLE_TOLERANCE = 0.12f
    @Composable
    private fun ObjectivePanelWithPowerUps() {
        val roster = setOf("cristal", "dormilona", "miedoso", "planta", "electrico", "fuego", "ninja")
        com.cukiti.teikafusion.ui.LevelObjectiveOverlay(
            level = 130,
            objective = com.cukiti.teikafusion.model.LevelLayouts.get(130)?.second,
            reducedFx = true,
            onUiClick = {}, onPlay = {}, onDismiss = {},
            powerUps = com.cukiti.teikafusion.ui.PowerUpPickerModel(
                available = com.cukiti.teikafusion.ui.pickablePowerUps(roster),
                selected = listOf("fuego", "cristal"),
                charges = com.cukiti.teikafusion.model.CompanionCharges(
                    mapOf("cristal" to 3, "dormilona" to 1, "planta" to 2, "fuego" to 5)
                ),
                teikoins = 3_250,
                onToggle = {}, onBuy = { true },
            ),
        )
    }

    @Test fun objetivo_power_ups() = snap("objetivo_power_ups", tolerance = ICON_TOLERANCE) { ObjectivePanelWithPowerUps() }

    /** Móvil corto (360×640): el panel entra en modo compacto y JUGAR tiene que seguir en cuadro. */
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    @Test fun objetivo_power_ups_compacto() = snap("objetivo_power_ups_compacto", tolerance = ICON_TOLERANCE) { ObjectivePanelWithPowerUps() }

    /** El botón de partida desplegado, con el primer power-up resaltado (falta el segundo toque). */
    @Test fun power_ups_partida() {
        compose.setContent {
            TeikaFusionTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF405384))) {
                        PowerUpButtonLayer(
                            ids = listOf("fuego", "cristal", "planta"),
                            charges = com.cukiti.teikafusion.model.CompanionCharges(mapOf("fuego" to 5, "cristal" to 2)),
                            canCast = { true },
                            teikoins = 3_250,
                            bottomPx = 300f,
                            enabled = true,
                            onCast = {}, onBuy = {}, onUiClick = {}, onExpanded = {},
                        )
                }
            }
        }
        compose.onNodeWithContentDescription("Abrir power-ups").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithContentDescription("usos. Toca para elegir", substring = true, useUnmergedTree = true)[0].performClick()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/power_ups_partida.png")
    }

    /** P-270 · EL ECO: el rótulo a mitad de su subida, sobre el botón de power-ups. */
    @Test fun eco_power_up() = snap("eco_power_up") { Box(Modifier.fillMaxSize()) {
        PowerUpButtonLayer(
            ids = listOf("fuego", "cristal", "planta"),
            charges = com.cukiti.teikafusion.model.CompanionCharges(mapOf("fuego" to 5, "cristal" to 2)),
            canCast = { true }, teikoins = 3_250, bottomPx = 300f, enabled = true,
            onCast = {}, onBuy = {}, onUiClick = {}, onExpanded = {},
        )
        // El botón mide 46 dp y va a 14 dp del borde derecho y a 300 px del fondo (xxhdpi = 3 px/dp).
        AbilityEchoLabel(
            eventId = "eco", companionId = "fuego", frozenAt = 0.45f,
            anchorBottomEnd = androidx.compose.ui.geometry.Offset(1233f - 42f, 2673f - 300f),
            onDone = {},
        )
    } }

    /** La mini-partida de la ficha (2026-09-30) a mitad de su efecto: el eclipse ya sobre el rincón. */
    @Test fun demo_habilidad() {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TeikaFusionTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF405384))) {
                    com.cukiti.teikafusion.ui.AbilityDemoBoard(
                        companionId = "galactico", animate = true,
                        modifier = Modifier.padding(top = 200.dp, start = 24.dp, end = 24.dp),
                    )
                }
            }
        }
        compose.mainClock.advanceTimeBy(1_500)
        compose.onRoot().captureRoboImage(
            "src/test/screenshots/demo_habilidad.png",
            roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(
                compareOptions = com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions(changeThreshold = ICON_TOLERANCE)
            )
        )
    }

    // ── EL INVENTARIO (P-282): la vitrina estilo Candy, con un roster de media partida. ──
    private val invRoster = setOf("cristal", "dormilona", "miedoso", "planta", "electrico", "fuego", "ninja")
    @Test fun inventario() = snap("inventario", tolerance = ICON_TOLERANCE) {
        com.cukiti.teikafusion.ui.InventoryScreen(
            teikoins = 3_250,
            crystalInventory = com.cukiti.teikafusion.model.CrystalInventory(crystals = 84, legacyConverted = true),
            claimedCompanionIds = invRoster,
            companionUpgrades = com.cukiti.teikafusion.model.CompanionAbilityUpgrades(
                mapOf("cristal" to 3, "dormilona" to 2, "fuego" to 4)
            ),
            companionCharges = com.cukiti.teikafusion.model.CompanionCharges(
                mapOf("cristal" to 3, "dormilona" to 1, "planta" to 2, "fuego" to 5)
            ),
            selectedPowerUps = listOf("cristal", "fuego"),
            lives = 3,
            infiniteLivesUntil = 0L,
            selectedLevel = 130,
            onBuyCharge = { true }, onUpgradeAbility = { true },
            active = false,
        )
    }

    /** Cuenta rica con los 14 power-ups y vidas infinitas en marcha (la referencia inventariocandy). */
    @Test fun inventario_lleno() = snap("inventario_lleno", tolerance = ICON_TOLERANCE) {
        val todos = com.cukiti.teikafusion.model.NamedTeikaRegistry.all.map { it.id }.toSet()
        com.cukiti.teikafusion.ui.InventoryScreen(
            teikoins = 71_048,
            crystalInventory = com.cukiti.teikafusion.model.CrystalInventory(crystals = 961, legacyConverted = true),
            claimedCompanionIds = todos,
            companionUpgrades = com.cukiti.teikafusion.model.CompanionAbilityUpgrades(
                mapOf("cristal" to 3, "dormilona" to 3, "miedoso" to 2, "rey" to 1, "fuego" to 5)
            ),
            companionCharges = com.cukiti.teikafusion.model.CompanionCharges(todos.associateWith { 9 }),
            selectedPowerUps = listOf("rey", "fuego", "hielo"),
            lives = 5,
            // Vidas infinitas con 20:41:20 por delante, con el reloj fijo para que la captura no baile.
            infiniteLivesUntil = 1_000_000_000L + 74_480_000L,
            now = { 1_000_000_000L },
            selectedLevel = 155,
            onBuyCharge = { true }, onUpgradeAbility = { true },
            active = false,
        )
    }

    /** La ficha de un compañero a nivel 3 de 5 (sin controles de cinturón desde P-282). */
    @Test fun inventario_ficha() = snap("inventario_ficha") {
        com.cukiti.teikafusion.ui.AbilityPickerDialog(
            companionId = "fuego",
            teikoins = 3_250,
            companionUpgrades = com.cukiti.teikafusion.model.CompanionAbilityUpgrades(mapOf("fuego" to 3)),
            crystalInventory = com.cukiti.teikafusion.model.CrystalInventory(crystals = 12, legacyConverted = true),
            phase = { 0f },
            chargeCount = 5,
            onBuyCharge = { true },
            onUpgrade = {},
            onClose = {},
        )
    }

    @Test fun confirmar_normal() = snap("confirmar_normal") {
        ConfirmOverlay(
            title = "¿Salir del nivel?",
            message = "Perderás el progreso de este intento.",
            confirmLabel = "Salir",
            onConfirm = {}, onDismiss = {},
        )
    }

    @Test fun confirmar_peligro() = snap("confirmar_peligro") {
        ConfirmOverlay(
            title = "¿Borrar la cuenta?",
            message = "Esta acción no se puede deshacer.",
            confirmLabel = "Borrar",
            danger = true,
            onConfirm = {}, onDismiss = {},
        )
    }

    @Test fun salir() = snap("salir") { QuitDialogOverlay(onConfirmQuit = {}, onCancel = {}) }

    @Test fun pausa() = snap("pausa") {
        PauseOverlay(
            musicVolume = 1f, sfxVolume = 1f, vibrationEnabled = true, tiltEnabled = true,
            isMuted = false, reducedFx = false,
            onMusicVolume = {}, onSfxVolume = {}, onSfxPreview = {}, onToggleVibration = {},
            onToggleTilt = {}, onToggleMute = {}, onToggleReducedFx = {},
            onResume = {}, onRestart = {}, onGoHome = {}, restartCostsLife = true,
        )
    }

    /** Ajustes compactos solo con iconos (2026-09-23): efectos y vibración apagados para ver el tachado. */
    @Test fun ajustes() = snap("ajustes") {
        SettingsOverlay(
            musicVolume = 0.6f, sfxVolume = 0f, vibrationEnabled = false, tiltEnabled = true,
            isMuted = false, reducedFx = false, notificationsEnabled = false,
            onMusicVolume = {}, onSfxVolume = {}, onSfxPreview = {}, onToggleVibration = {},
            onToggleTilt = {}, onToggleMute = {}, onToggleReducedFx = {},
            onDismiss = {},
        )
    }

    @Test fun revivir_sin_espacio() = revive(DefeatCause.SIN_ESPACIO)
    @Test fun revivir_sin_turnos() = revive(DefeatCause.SIN_TURNOS)
    @Test fun revivir_lava() = revive(DefeatCause.LAVA)

    // ── P-293 (2026-09-25): marcas del suelo tapadas por un bloque (solo el borde) y la brújula y
    //    los portales POR FUERA del tablero. Caliente y portería libres a la izquierda, tapadas a la derecha. ──
    @Test fun marcas_borde() = snap("marcas_borde") {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            val cell = androidx.compose.ui.unit.Dp(40f)
            fun P(x: Int, y: Int) = com.cukiti.teikafusion.model.Position(x, y)
            androidx.compose.foundation.layout.Box(Modifier.size(cell * 8).background(Color(0xFF1F2643))) {
                // Dos «bloques» de madera donde van las marcas tapadas.
                listOf(P(6, 2), P(6, 5)).forEach { p ->
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .offset(cell * p.x, cell * p.y)
                            .size(cell)
                            .background(Color(0xFFE0A050))
                    )
                }
                com.cukiti.teikafusion.ui.HotCellsLayer(
                    cells = setOf(P(1, 2), P(6, 2)), cellSize = cell, animationsOn = false,
                    covered = setOf(P(6, 2)),
                )
                com.cukiti.teikafusion.ui.CompassLayer(
                    dir = com.cukiti.teikafusion.model.Compass.DOWN,
                    nextDir = com.cukiti.teikafusion.model.Compass.LEFT,
                    turnsUntil = 1, period = 4, cols = 8, rows = 8, cellSize = cell, animationsOn = false,
                )
                com.cukiti.teikafusion.ui.PortalsLayer(
                    rows = 1 to 4, grid = emptyMap(), cols = 8, cellSize = cell, animationsOn = false,
                )
            }
        }
    }

    // ── P-295 (2026-09-25): «¡ESTUVO CERCA!» con el marco de las ventanas de juego. ──
    @Test fun estuvo_cerca() = snap("estuvo_cerca") {
        com.cukiti.teikafusion.ui.GameOverOverlay(
            score = 7450, highScore = 12300, isNewRecord = false,
            onRestart = {}, onGoHome = {},
        )
    }

    // ── Presagio del Cielo Estelar (2026-09-23): el tablero anuncia Sol (oro) o Luna (noche). ──
    @Test fun presagio_sol() = omen(com.cukiti.teikafusion.model.StarAstroOmen.SUN, "presagio_sol")
    @Test fun presagio_luna() = omen(com.cukiti.teikafusion.model.StarAstroOmen.MOON, "presagio_luna")

    private fun omen(body: Int, name: String) = snap(name) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            val board = androidx.compose.ui.unit.Dp(360f)
            androidx.compose.foundation.layout.Box(
                Modifier.size(board).background(Color(0xFF1F2643))
            ) {
                com.cukiti.teikafusion.ui.StarOmenOverlay(omen = body, strength = 1f, modifier = Modifier.fillMaxSize())
            }
        }
    }

    // ── Volcán · EL MAGMA (2026-09-23): fila 3 ardiendo (70 % de calor) y fila 5 ya fundida. ──
    @Test fun magma() = snap("magma") {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            val board = androidx.compose.ui.unit.Dp(360f)
            val px = with(androidx.compose.ui.platform.LocalDensity.current) { board.toPx() } / 8f
            androidx.compose.foundation.layout.Box(Modifier.size(board).background(Color(0xFF1F2643))) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    // Madera de referencia en las filas 3 y 5 y lava en las dos de arriba.
                    for (x in 0 until 8) for (y in listOf(3, 5)) drawRect(
                        Color(0xFFC98B4A), topLeft = androidx.compose.ui.geometry.Offset(x * px + 3, y * px + 3),
                        size = androidx.compose.ui.geometry.Size(px - 6, px - 6)
                    )
                    drawRect(Color(0xFFC42E00), size = androidx.compose.ui.geometry.Size(size.width, px * 3))
                }
                val heat = androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0.7f) }
                com.cukiti.teikafusion.ui.MagmaLayer(
                    magmaCells = (0 until 8).map { com.cukiti.teikafusion.model.Position(it, 5) }.toSet(),
                    burningCells = (0 until 8).map { com.cukiti.teikafusion.model.Position(it, 3) }.toSet(),
                    heat = heat, cellPx = px,
                )
            }
        }
    }

    // ── Mansión · EL ESPEJO (P-246): segundo aviso (reflejo a la vista) + dos fantasmas ya puestos. ──
    @Test fun espejo() = snap("espejo") {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            val board = androidx.compose.ui.unit.Dp(360f)
            val px = with(androidx.compose.ui.platform.LocalDensity.current) { board.toPx() } / 8f
            fun P(x: Int, y: Int) = com.cukiti.teikafusion.model.Position(x, y)
            val grid = buildMap<com.cukiti.teikafusion.model.Position, com.cukiti.teikafusion.model.CellContent> {
                for (y in 0 until 8) for (x in 0 until 8) put(P(x, y), com.cukiti.teikafusion.model.CellContent.Empty)
                listOf(P(0, 2), P(1, 2), P(2, 5), P(3, 5), P(1, 6)).forEach { put(it, com.cukiti.teikafusion.model.CellContent.Wood(colorType = 2)) }
            }
            androidx.compose.foundation.layout.Box(Modifier.size(board).background(Color(0xFF1F2643))) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    grid.forEach { (p, c) -> if (c is com.cukiti.teikafusion.model.CellContent.Wood) drawRect(
                        Color(0xFF8F6BD8), topLeft = androidx.compose.ui.geometry.Offset(p.x * px + 3, p.y * px + 3),
                        size = androidx.compose.ui.geometry.Size(px - 6, px - 6)) }
                    listOf(P(6, 0), P(7, 0)).forEach { p -> drawRect(Color(0xFF8F6BD8),
                        topLeft = androidx.compose.ui.geometry.Offset(p.x * px + 3, p.y * px + 3),
                        size = androidx.compose.ui.geometry.Size(px - 6, px - 6)) }
                }
                com.cukiti.teikafusion.ui.PhantomCellsLayer(mapOf(P(6, 0) to 2, P(7, 0) to 1), px)
                com.cukiti.teikafusion.ui.MirrorOverlay(
                    grid = grid, source = com.cukiti.teikafusion.model.HauntedMirror.LEFT,
                    idleTurns = 2, threshold = 3, unplaceable = emptySet(), cellPx = px,
                )
            }
        }
    }

    // ── P-294 · Galáctico · METEORITO: aviso 2 — cráter marcado sobre (4,2)-(5,3), dos bloques dentro,
    //    y un bloque de ROCA ya fundida en (1,6). (El Lottie carga en IO y aquí puede no salir.) ──
    @Test fun meteorito() = snap("meteorito") {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            val board = androidx.compose.ui.unit.Dp(360f)
            val px = with(androidx.compose.ui.platform.LocalDensity.current) { board.toPx() } / 8f
            val cell = board / 8
            androidx.compose.foundation.layout.Box(Modifier.size(board).background(Color(0xFF1F2643))) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    listOf(4 to 2, 5 to 3, 2 to 3).forEach { (x, y) -> drawRect(Color(0xFF4DB8FF),
                        topLeft = androidx.compose.ui.geometry.Offset(x * px + 3, y * px + 3),
                        size = androidx.compose.ui.geometry.Size(px - 6, px - 6)) }
                }
                com.cukiti.teikafusion.ui.SpaceRockOverlay(Modifier.offset(cell * 1, cell * 6).size(cell))
                com.cukiti.teikafusion.ui.MeteorLayer(
                    at = com.cukiti.teikafusion.model.Position(4, 2),
                    idleTurns = 2, threshold = 3, cellPx = px, animationsOn = false, hideReticle = false,
                )
            }
        }
    }

    // ── P-294 · Trono Real · ESPADA DEL REY: aviso 2 — la hoja brilla sobre la fila 4. ──
    @Test fun espada_rey() = snap("espada_rey") {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            val board = androidx.compose.ui.unit.Dp(360f)
            val px = with(androidx.compose.ui.platform.LocalDensity.current) { board.toPx() } / 8f
            androidx.compose.foundation.layout.Box(Modifier.size(board).background(Color(0xFF1F2643))) {
                androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
                    listOf(0, 1, 3, 4, 6).forEach { x -> drawRect(Color(0xFF4DB8FF),
                        topLeft = androidx.compose.ui.geometry.Offset(x * px + 3, 4 * px + 3),
                        size = androidx.compose.ui.geometry.Size(px - 6, px - 6)) }
                }
                com.cukiti.teikafusion.ui.KingSwordLayer(
                    axis = com.cukiti.teikafusion.model.KingSword.ROW, line = 4,
                    idleTurns = 2, threshold = 3, cellPx = px, animationsOn = false, hideSword = false,
                )
            }
        }
    }

    // ── LA VITRINA DE BLOQUES (P-250): terminado el Bosque del Sueño (Cristal descubierto, sin
    // comprar), con la Cerámica comprada y el Básico puesto. Sin animación: la foto es de la rejilla. ──
    /** Mapa de mundos (2026-09-23): jugador en el nivel 9 de Cristal, estrellas variadas detrás. */
    @Test fun mapa() = snap("mapa") {
        val first = com.cukiti.teikafusion.model.LevelMap.introLevels + 1
        val frontier = first + 8
        com.cukiti.teikafusion.ui.WorldMapOverlay(
            maxUnlockedLevel = frontier,
            levelStars = (first until frontier).associateWith { (it % 3) + 1 },
            selectedLevel = frontier,
            avatarType = com.cukiti.teikafusion.model.TeikaType.TEIKA_1,
            onSelectLevel = {}, onDismiss = {},
        )
    }

    /** Mapa en la frontera de capítulo: Cristal terminado, primer nivel del Sueño; el Pasaje, tapado. */
    @Test fun mapa_frontera() = snap("mapa_frontera") {
        val sleepFirst = com.cukiti.teikafusion.model.LevelMap.chapterStart[1]
        com.cukiti.teikafusion.ui.WorldMapOverlay(
            maxUnlockedLevel = sleepFirst,
            levelStars = emptyMap(),
            selectedLevel = sleepFirst,
            avatarType = com.cukiti.teikafusion.model.TeikaType.TEIKA_2,
            onSelectLevel = {}, onDismiss = {},
        )
    }

    /** Más ajustes (2026-09-23): rejilla de 2×2 y zona de peligro plegada. Sin cuenta vinculada. */
    @Test fun mas_ajustes() = snap("mas_ajustes") {
        com.cukiti.teikafusion.ui.MoreSettingsOverlay(
            currentName = "Anderson C", onChangeName = {}, onPrivacyPolicy = {}, onPrivacyOptions = null,
            onTermsOfService = {}, onContact = {}, onShare = {}, onResetProgress = {}, onDeleteAccount = {},
            onDismiss = {},
        )
    }

    /** Ranking con podio (2026-09-23): seis jugadores, tú en el 5.º puesto (fila fija abajo). */
    @Test fun ranking() = snap("ranking") {
        val t = com.cukiti.teikafusion.model.TeikaType.entries
        val names = listOf("Andres", "Anderson C", "Dae nood", "Lucía", "Tú mismo", "Marco")
        val entries = names.mapIndexed { i, n ->
            com.cukiti.teikafusion.model.LeaderboardEntry(
                rank = i + 1, uid = "u$i", displayName = n, level = 300 - i * 37, teikas = 0,
                totalXp = 20_000 - i * 3_000, avatarType = t[i % t.size],
            )
        }
        com.cukiti.teikafusion.ui.LeaderboardScreen(
            entries = entries, isLoading = false, isError = false, currentUid = "u4", onClose = {},
        )
    }

    /** Cómo se juega (2026-09-23): tres por fila con el tablero de cada lección. Hechas 1-4. */
    @Test fun lecciones() = snap("lecciones") {
        com.cukiti.teikafusion.ui.LessonsPanel(maxUnlockedLevel = 5, onPlayLesson = {}, onDismiss = {})
    }

    @Test fun vitrina_bloques() = snap("vitrina_bloques") {
        val sleepLast = com.cukiti.teikafusion.model.LevelMap.chapterLastLevel(1)
        com.cukiti.teikafusion.ui.BlockShowcaseOverlay(
            teikoins = 1_200, maxUnlockedLevel = sleepLast + 1,
            unlockedCosmetics = setOf("fall3d"), activeCosmetic = null, animationsOn = false,
            onBuy = { true }, onEquip = {}, onUiClick = {}, onDismiss = {},
        )
    }

    // ── EL BLOQUE-TEIKA (P-256): arriba, casillas sueltas como en la bandeja; abajo, un tablero con dos
    // bloques puestos sobre madera. Reloj fijado a mano para ver las púas en su color. ──
    @Test fun bloque_teika() {
        com.cukiti.teikafusion.ui.biomeClockRunning.value = true
        com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 2.2f
        try {
            snap("bloque_teika") {
                val skin = com.cukiti.teikafusion.ui.BlockSkins.classico
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    androidx.compose.foundation.layout.Row(
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(androidx.compose.ui.unit.Dp(28f))
                    ) {
                        (0 until 3).forEach { c ->
                            val col = skin.colorsFor(c * 2)
                            Box(Modifier.size(androidx.compose.ui.unit.Dp(56f))
                                .styledTile(skin.style, col[0], col[1], col[2], skin.cornerFraction)) {
                                Box(Modifier.fillMaxSize().teikaSeedTile(animationsOn = true))
                            }
                        }
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.size(androidx.compose.ui.unit.Dp(40f)))
                    val cell = androidx.compose.ui.unit.Dp(48f)
                    val grid = buildMap<com.cukiti.teikafusion.model.Position, com.cukiti.teikafusion.model.CellContent> {
                        for (y in 0 until 4) for (x in 0 until 6) {
                            val p = com.cukiti.teikafusion.model.Position(x, y)
                            put(p, com.cukiti.teikafusion.model.CellContent.Wood(
                                colorType = (x + y) % 5,
                                teikaSeed = p == com.cukiti.teikafusion.model.Position(1, 1) || p == com.cukiti.teikafusion.model.Position(4, 2),
                            ))
                        }
                    }
                    Box(Modifier.size(cell * 6, cell * 4).background(Color(0xFF1F2643))) {
                        grid.forEach { (p, c) ->
                            val col = skin.colorsFor((c as com.cukiti.teikafusion.model.CellContent.Wood).colorType)
                            Box(Modifier.offset(cell * p.x, cell * p.y).size(cell)
                                .styledTile(skin.style, col[0], col[1], col[2], skin.cornerFraction))
                        }
                        com.cukiti.teikafusion.ui.TeikaSeedLayer(grid, cell, animationsOn = true)
                    }
                }
            }
        } finally {
            com.cukiti.teikafusion.ui.biomeClockRunning.value = false
            com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0f
        }
    }

    // ── Bloque CRISTAL (P-251): los cinco colores tallados y, abajo, un tablero con la onda del destello
    // congelada a media pasada (el reloj compartido se fija a mano: en la JVM nadie lo mueve). ──
    /** P-251 2/7 · el bloque MADERA: los cinco colores sueltos y un tablero con sus tres vetas. */
    @Test fun bloque_madera() = snap("bloque_madera") { SkinBoard(com.cukiti.teikafusion.ui.BlockSkins.madera) }

    /** P-251 3/7 · el bloque LAVA, con el reloj en marcha para que las grietas se vean latiendo. */
    @Test fun bloque_lava() {
        com.cukiti.teikafusion.ui.biomeClockRunning.value = true
        com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0.65f
        try {
            snap("bloque_lava") { SkinBoard(com.cukiti.teikafusion.ui.BlockSkins.lava) }
        } finally {
            com.cukiti.teikafusion.ui.biomeClockRunning.value = false
            com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0f
        }
    }

    /** P-251 4/7 · el bloque HIELO, con el reloj en un instante en que titilan algunos destellos. */
    @Test fun bloque_hielo() {
        com.cukiti.teikafusion.ui.biomeClockRunning.value = true
        com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0.3f
        try {
            snap("bloque_hielo") { SkinBoard(com.cukiti.teikafusion.ui.BlockSkins.hielo) }
        } finally {
            com.cukiti.teikafusion.ui.biomeClockRunning.value = false
            com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0f
        }
    }

    /** P-251 5/7 · el bloque ESPECTRO, en un instante en que algún fantasmita parpadea. */
    @Test fun bloque_espectro() {
        com.cukiti.teikafusion.ui.biomeClockRunning.value = true
        com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0.1f
        try {
            snap("bloque_espectro") { SkinBoard(com.cukiti.teikafusion.ui.BlockSkins.espectro) }
        } finally {
            com.cukiti.teikafusion.ui.biomeClockRunning.value = false
            com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0f
        }
    }

    /** P-251 6/7 · el bloque CONSTELACIÓN, con las estrellas a media titilación. */
    @Test fun bloque_constelacion() {
        com.cukiti.teikafusion.ui.biomeClockRunning.value = true
        com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0.9f
        try {
            snap("bloque_constelacion") { SkinBoard(com.cukiti.teikafusion.ui.BlockSkins.constelacion) }
        } finally {
            com.cukiti.teikafusion.ui.biomeClockRunning.value = false
            com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0f
        }
    }

    /** P-251 7/7 · el bloque VACÍO, con el brillo a media vuelta del anillo. */
    @Test fun bloque_vacio() {
        com.cukiti.teikafusion.ui.biomeClockRunning.value = true
        com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0.7f
        try {
            snap("bloque_vacio") { SkinBoard(com.cukiti.teikafusion.ui.BlockSkins.vacio) }
        } finally {
            com.cukiti.teikafusion.ui.biomeClockRunning.value = false
            com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0f
        }
    }

    /** P-283 · la firma de cada bloque de bioma al COLOCAR una ficha, a mitad de su animación. */
    @Test fun firma_colocar() {
        compose.mainClock.autoAdvance = false
        val skins = with(com.cukiti.teikafusion.ui.BlockSkins) { listOf(cristal, madera, lava, hielo, espectro, constelacion, vacio, metal) }
        val cells = listOf(com.cukiti.teikafusion.model.Position(1, 1), com.cukiti.teikafusion.model.Position(2, 1),
            com.cukiti.teikafusion.model.Position(3, 1), com.cukiti.teikafusion.model.Position(2, 2))
        val cell = androidx.compose.ui.unit.Dp(30f)
        compose.setContent {
            TeikaFusionTheme {
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxSize().background(Color(0xFF1F2643)),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    skins.forEach { skin ->
                        com.cukiti.teikafusion.ui.ProvideBlockSkin(skin.id) {
                            Box(Modifier.size(cell * 5, cell * 3.4f)) {
                                val c = skin.colorsFor(3)
                                cells.forEach { p ->
                                    Box(Modifier.offset(cell * p.x, cell * p.y).size(cell)
                                        .styledTile(skin.style, c[0], c[1], c[2], skin.cornerFraction))
                                }
                                val event = remember { com.cukiti.teikafusion.model.GameEvent.PiecePlacedPop(cells, 3) }
                                com.cukiti.teikafusion.ui.BatchPlacePopVFX(listOf(event), cell) {}
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(170)
        compose.onRoot().captureRoboImage("src/test/screenshots/firma_colocar.png",
            roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(
                compareOptions = com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions(changeThreshold = PARTICLE_TOLERANCE)))
    }

    /** P-412 · la firma al colocar del NEÓN (el tubo se enciende) y del DORADO (la gema destella). */
    @Test fun firma_neon_dorado() {
        compose.mainClock.autoAdvance = false
        val skins = with(com.cukiti.teikafusion.ui.BlockSkins) { listOf(neon, dorado) }
        val cells = listOf(com.cukiti.teikafusion.model.Position(1, 1), com.cukiti.teikafusion.model.Position(2, 1),
            com.cukiti.teikafusion.model.Position(3, 1), com.cukiti.teikafusion.model.Position(2, 2))
        val cell = androidx.compose.ui.unit.Dp(40f)
        compose.setContent {
            TeikaFusionTheme {
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxSize().background(Color(0xFF1F2643)),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    skins.forEach { skin ->
                        com.cukiti.teikafusion.ui.ProvideBlockSkin(skin.id) {
                            Box(Modifier.size(cell * 5, cell * 4f)) {
                                val c = skin.colorsFor(3)
                                cells.forEach { p ->
                                    Box(Modifier.offset(cell * p.x, cell * p.y).size(cell)
                                        .styledTile(skin.style, c[0], c[1], c[2], skin.cornerFraction))
                                }
                                val event = remember { com.cukiti.teikafusion.model.GameEvent.PiecePlacedPop(cells, 3) }
                                com.cukiti.teikafusion.ui.BatchPlacePopVFX(listOf(event), cell) {}
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(140)
        compose.onRoot().captureRoboImage("src/test/screenshots/firma_neon_dorado.png",
            roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(
                compareOptions = com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions(changeThreshold = PARTICLE_TOLERANCE)))
    }

    /** P-283 · la rotura de una línea de cada bloque de bioma, a mitad de su efecto. */
    @Test fun rotura_bloques() {
        compose.mainClock.autoAdvance = false
        val skins = with(com.cukiti.teikafusion.ui.BlockSkins) { listOf(cristal, madera, lava, hielo, espectro, constelacion, vacio, metal) }
        val cell = androidx.compose.ui.unit.Dp(30f)
        compose.setContent {
            TeikaFusionTheme {
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxSize().background(Color(0xFF1F2643)),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    skins.forEach { skin ->
                        com.cukiti.teikafusion.ui.ProvideBlockSkin(skin.id) {
                            Box(Modifier.size(cell * 7, cell * 3.4f)) {
                                val events = remember {
                                    (1..5).map { x ->
                                        com.cukiti.teikafusion.model.GameEvent.LineCellShatter(
                                            com.cukiti.teikafusion.model.Position(x, 1), colorType = x % 5, delayMs = (x - 1) * 25
                                        )
                                    }
                                }
                                com.cukiti.teikafusion.ui.BatchLineCellShatterVFX(events, cell, onEventComplete = {})
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(230)
        compose.onRoot().captureRoboImage("src/test/screenshots/rotura_bloques.png",
            roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(
                compareOptions = com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions(changeThreshold = PARTICLE_TOLERANCE)))
    }

    /** El bloque METAL (2026-09-25), con el brillo cruzando parte del tablero. */
    @Test fun bloque_metal() {
        com.cukiti.teikafusion.ui.biomeClockRunning.value = true
        com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0.5f
        try {
            snap("bloque_metal") { SkinBoard(com.cukiti.teikafusion.ui.BlockSkins.metal) }
        } finally {
            com.cukiti.teikafusion.ui.biomeClockRunning.value = false
            com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0f
        }
    }

    /**
     * P-410 · la rotura propia del NEÓN (tubo fundido + chispas) y del DORADO (gema que salta +
     * monedas). Cada casilla arranca 110 ms después que la anterior: en una sola foto se ven las fases.
     */
    @Test fun rotura_neon_dorado() {
        compose.mainClock.autoAdvance = false
        val skins = with(com.cukiti.teikafusion.ui.BlockSkins) { listOf(neon, dorado) }
        val cell = androidx.compose.ui.unit.Dp(40f)
        compose.setContent {
            TeikaFusionTheme {
                androidx.compose.foundation.layout.Column(
                    Modifier.fillMaxSize().background(Color(0xFF1F2643)),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    skins.forEach { skin ->
                        com.cukiti.teikafusion.ui.ProvideBlockSkin(skin.id) {
                            Box(Modifier.size(cell * 7, cell * 4f)) {
                                val events = remember {
                                    (1..5).map { x ->
                                        com.cukiti.teikafusion.model.GameEvent.LineCellShatter(
                                            com.cukiti.teikafusion.model.Position(x, 1), colorType = x % 5, delayMs = (5 - x) * 110
                                        )
                                    }
                                }
                                com.cukiti.teikafusion.ui.BatchLineCellShatterVFX(events, cell, onEventComplete = {}, haptics = false)
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(560)
        compose.onRoot().captureRoboImage("src/test/screenshots/rotura_neon_dorado.png",
            roborazziOptions = com.github.takahirom.roborazzi.RoborazziOptions(
                compareOptions = com.github.takahirom.roborazzi.RoborazziOptions.CompareOptions(changeThreshold = PARTICLE_TOLERANCE)))
    }

    /** P-408 · los bloques NEÓN y DORADO, inspirados en los block puzzle más populares. */
    @Test fun bloque_neon() = snap("bloque_neon") { SkinBoard(com.cukiti.teikafusion.ui.BlockSkins.neon) }

    @Test fun bloque_dorado() = snap("bloque_dorado") { SkinBoard(com.cukiti.teikafusion.ui.BlockSkins.dorado) }

    /** Los cinco colores sueltos y un tablero 8×8 de [skin], con la variante de cada celda. */
    @Composable
    private fun SkinBoard(skin: com.cukiti.teikafusion.ui.BlockSkin) {
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            val cell = androidx.compose.ui.unit.Dp(64f)
            androidx.compose.foundation.layout.Row {
                (0 until 5).forEach { c ->
                    val col = skin.colorsFor(c)
                    Box(Modifier.size(cell).styledTile(skin.style, col[0], col[1], col[2], skin.cornerFraction))
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(androidx.compose.ui.unit.Dp(24f)))
            val small = androidx.compose.ui.unit.Dp(44f)
            androidx.compose.foundation.layout.Column(Modifier.background(Color(0xFF1F2643))) {
                (0 until 8).forEach { y ->
                    androidx.compose.foundation.layout.Row {
                        (0 until 8).forEach { x ->
                            val col = skin.colorsFor((x * 3 + y * 2) % 5)
                            Box(
                                Modifier.size(small).styledTile(
                                    skin.style, col[0], col[1], col[2], skin.cornerFraction,
                                    glintPhase = (x + y).toFloat()
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    @Test fun bloque_cristal() {
        com.cukiti.teikafusion.ui.biomeClockRunning.value = true
        com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 1.344f   // la onda cruza la diagonal central
        try {
            snap("bloque_cristal") { CrystalBoard() }
        } finally {
            com.cukiti.teikafusion.ui.biomeClockRunning.value = false
            com.cukiti.teikafusion.ui.biomeClockSec.floatValue = 0f
        }
    }

    @Composable
    private fun CrystalBoard() {
        val skin = com.cukiti.teikafusion.ui.BlockSkins.cristal
        androidx.compose.foundation.layout.Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        ) {
            val cell = androidx.compose.ui.unit.Dp(64f)
            androidx.compose.foundation.layout.Row {
                (0 until 5).forEach { c ->
                    val col = skin.colorsFor(c)
                    Box(Modifier.size(cell).then(Modifier.styledTileNoGlint(col, skin.cornerFraction)))
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(androidx.compose.ui.unit.Dp(24f)))
            val small = androidx.compose.ui.unit.Dp(44f)
            androidx.compose.foundation.layout.Column(Modifier.background(Color(0xFF1F2643))) {
                (0 until 8).forEach { y ->
                    androidx.compose.foundation.layout.Row {
                        (0 until 8).forEach { x ->
                            val col = skin.colorsFor((x * 3 + y * 2) % 5)
                            Box(
                                Modifier.size(small).styledTile(
                                    skin.style, col[0], col[1], col[2], skin.cornerFraction,
                                    glintPhase = (x + y).toFloat()
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    /** La fila de muestra va con la versión inmediata (la de los efectos): sin destello. */
    private fun Modifier.styledTileNoGlint(col: List<Color>, corner: Float) = drawBehind {
        drawStyledTile(BlockTileStyle.FACETED, androidx.compose.ui.geometry.Offset.Zero, size, col[0], col[1], col[2], corner)
    }

    private fun revive(cause: DefeatCause) = snap("revivir_${cause.name.lowercase()}", tolerance = 0.02f) {
        ReviveOverlay(
            teikoins = 1_250, canWatchAd = true, adLoading = false, cause = cause,
            onReviveAd = {}, onReviveCoins = {}, onTimeout = {}, onDecline = {},
        )
    }
}
