package com.cukiti.teikafusion.ui

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/** Protección ligera contra regresar a un único panel saturado o perder el gate legal. */
class SettingsRedesignContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, APP_NAVIGATION).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo" }
    }

    @Test
    fun `ajustes principales delegan la cuenta al panel secundario`() {
        val source = File(repoRoot, OVERLAYS).readText()
        assertTrue(source.contains("onOpenMoreSettings"))
        assertTrue(source.contains("Más ajustes"))
        assertTrue(source.contains("fun MoreSettingsOverlay"))
        assertTrue(source.contains("Eliminar mi cuenta"))
    }

    @Test
    fun `el consentimiento legal tiene version y permanece fuera de la cuenta`() {
        val prefs = File(repoRoot, PREFERENCES).readText()
        val navigation = File(repoRoot, APP_NAVIGATION).readText()
        val legalOverlay = File(repoRoot, LEGAL_OVERLAY).readText()
        assertTrue(prefs.contains("LEGAL_CONSENT_VERSION_KEY"))
        assertTrue(prefs.contains("CURRENT_LEGAL_CONSENT_VERSION"))
        assertTrue(prefs.contains("acceptedLegalVersion"))
        assertTrue(navigation.contains("LegalConsentOverlay"))
        assertTrue(legalOverlay.contains("onAccept"))
        // Se fija el CONTRATO —los dos documentos alcanzables y un solo destino que acepta—, no su
        // redacción: el 2026-09-01 la pantalla se reescribió con textos más cortos («Privacidad»,
        // «Condiciones», «EMPEZAR A JUGAR») y clavar la copia exacta convertía cada cambio de tono en
        // un test rojo sin que nada del gate legal se hubiera movido.
        assertTrue(legalOverlay.contains("onOpenPrivacy"))
        assertTrue(legalOverlay.contains("onOpenTerms"))
        assertTrue(legalOverlay.contains("Privacidad"))
        assertTrue(legalOverlay.contains("Condiciones"))
        assertTrue(legalOverlay.contains("13 años"))
        assertTrue(legalOverlay.contains("JUGAR"))
        assertFalse(legalOverlay.contains("Switch("))
        // Y el decorado falso no vuelve: el «0» de mentira y la vista previa del tablero que la
        // propia ventana tapaba (el KDoc del fichero cuenta por qué, así que se busca el CÓDIGO).
        assertFalse(legalOverlay.contains("fun TutorialConsentBackdrop"))
        assertFalse(legalOverlay.contains("drawBlockTile("))
    }

    @Test
    fun `primer arranque no obliga a elegir cuenta ni duplica recordatorios`() {
        val navigation = File(repoRoot, APP_NAVIGATION).readText()
        val home = File(repoRoot, HOME).readText()
        val viewModel = File(repoRoot, VIEW_MODEL).readText()
        assertFalse(navigation.contains("AccountWelcomeScreen("))
        assertFalse(home.contains("SaveProgressDialog("))
        assertFalse(viewModel.contains("maybeQueueLinkNudge("))
        assertTrue(navigation.contains("PostTutorialBackupPrompt("))
        assertTrue(navigation.contains("accountReady = initialAccountSyncFinished && playerProfileLoaded"))
        assertFalse(
            navigation.contains("viewModel.dismissPostTutorialBackupPrompt()\n                    pgActivity")
        )
    }

    @Test
    fun `las lecciones omiten la presentacion regular de objetivos al rejugarlas`() {
        val gameScreen = File(repoRoot, GAME_SCREEN).readText()
        val objectiveReveal = File(repoRoot, OBJECTIVE_REVEAL).readText()
        val viewModel = File(repoRoot, VIEW_MODEL).readText()
        assertTrue(gameScreen.contains("roundStartToken > 0"))
        assertTrue(gameScreen.contains("!tutorialLevel"))
        assertTrue(objectiveReveal.contains("fun ObjectiveRevealOverlay("))
        assertTrue(viewModel.contains("scheduleTutorialReleaseFallback()"))
        assertFalse(viewModel.contains("scheduleTutorialFusionAssist()"))
        assertTrue(viewModel.contains("tutorialTiltObserved"))
    }

    @Test
    fun `el nivel cinco mide su hud antes del vuelo y nunca queda bloqueado invisible`() {
        val gameScreen = File(repoRoot, GAME_SCREEN).readText()
        val objectiveReveal = File(repoRoot, OBJECTIVE_REVEAL).readText()
        val viewModel = File(repoRoot, VIEW_MODEL).readText()
        // El corte llega hasta `skipTutorial` y no hasta `acceptLegalDocuments` (2026-09-04): entre
        // las dos hay ahora OTRA función, y SALTAR el tutorial sí termina en Inicio a propósito
        // —pedido del usuario: «que lo mande directamente al inicio listo para jugar el nivel 1»—.
        // Lo que esta prueba defiende es que TERMINAR la última lección encadene con el nivel
        // siguiente en vez de escupir al jugador al menú, y eso es cosa de `advanceTutorial` sola.
        val tutorialAdvance = viewModel.substringAfter("fun advanceTutorial()")
            .substringBefore("fun skipTutorial()")

        assertTrue(gameScreen.contains("var targetCounterRects by remember(gameState.level)"))
        assertTrue(gameScreen.contains("var megaCounterRect by remember(gameState.level)"))
        assertFalse(gameScreen.contains("targetCounterRects = emptyMap()"))
        assertTrue(objectiveReveal.contains("OBJECTIVE_DESTINATION_TIMEOUT_MS"))
        assertTrue(objectiveReveal.contains("latestDestinationsReady"))
        assertTrue(tutorialAdvance.contains("nextLevel()"))
        assertFalse(tutorialAdvance.contains("goHome()"))
    }

    @Test
    fun `el objetivo inicial se entrega al hud sin duplicados ni recomposicion por frame`() {
        val gameScreen = File(repoRoot, GAME_SCREEN).readText()
        val infoPanel = File(repoRoot, GAME_INFO_PANEL).readText()
        val objectiveReveal = File(repoRoot, OBJECTIVE_REVEAL).readText()
        val haptics = File(repoRoot, HAPTICS).readText()

        assertTrue(gameScreen.contains("objectivePresentationActive"))
        assertTrue(gameScreen.contains("onItemLanded ="))
        assertTrue(infoPanel.contains("revealedTargetColors"))
        assertTrue(infoPanel.contains("slot existe y se mide incluso cuando el dibujo no"))
        assertTrue(infoPanel.contains("if (revealed)"))
        assertTrue(objectiveReveal.contains("ObjectiveAuraStyle.COMET"))
        assertTrue(objectiveReveal.contains("ObjectiveAuraStyle.GRAVITY"))
        assertTrue(objectiveReveal.contains("timelineMs.value"))
        assertFalse(objectiveReveal.contains("val p = progress.value"))
        assertTrue(haptics.contains("fun objectiveLand(isMega: Boolean)"))
        assertTrue(gameScreen.contains("if (!reducedFx) Haptics.objectiveLand"))
    }

    @Test
    fun `la fusion conserva los lottie reales y el tutorial solo respira durante la espera`() {
        val gameBoard = File(repoRoot, GAME_BOARD).readText()
        val vfx = File(repoRoot, VFX).readText()

        assertTrue(gameBoard.contains("fusionCharges = fusionChargeEvents"))
        assertTrue(gameBoard.contains("tutorialIdleActive = tutorialStageReady"))
        assertTrue(gameBoard.contains("val tutorialIdleClock = remember { Animatable(0f) }"))
        assertFalse(gameBoard.contains("fusionChargeEvents.forEach { add(it.aId)"))
        assertFalse(vfx.contains("private fun FusionChargeOrb("))
    }

    @Test
    fun `jugar confirma objetivo y siguiente nivel permanece directo`() {
        val navigation = File(repoRoot, APP_NAVIGATION).readText()
        val gameScreen = File(repoRoot, GAME_SCREEN).readText()
        val overlays = File(repoRoot, OVERLAYS).readText()
        val objectiveReveal = File(repoRoot, OBJECTIVE_REVEAL).readText()
        assertTrue(navigation.contains("showLevelObjective = true"))
        assertTrue(navigation.contains("LevelObjectiveOverlay("))
        // 2026-08-24 (decisión del usuario): JUGAR NIVEL también presenta el objetivo. Era la ÚNICA
        // entrada que apagaba la ceremonia —o sea, la forma más habitual de empezar un nivel era la
        // única sin ella—, y el parámetro que la apagaba ya no existe.
        // Sin paréntesis de cierre a propósito: lo que este contrato vigila es que JUGAR arranque
        // el nivel SELECCIONADO y que ya no exista el interruptor que apagaba la ceremonia (el
        // `assertFalse("presentObjectives")` de abajo). Desde P-74 la llamada lleva además la carta
        // del TRATO, y clavar la firma exacta convertía este test en un freno a cualquier
        // parámetro nuevo en vez de en una guardia de la decisión de 2026-08-24.
        assertTrue(navigation.contains("viewModel.startGame(selectedLevel"))
        assertFalse(navigation.contains("presentObjectives"))
        assertFalse(gameScreen.contains("NextLevelBriefOverlay("))
        assertFalse(gameScreen.contains("pendingNextLevelBrief"))
        assertFalse(overlays.contains("fun TargetCollectionOverlay("))
        assertFalse(objectiveReveal.contains("targetRects.getValue"))
    }

    @Test
    fun `las lecciones no cubren la parte superior con mensajes flotantes`() {
        val tutorialGuide = File(repoRoot, TUTORIAL_GUIDE).readText()
        assertFalse(tutorialGuide.contains("val prompt ="))
        assertFalse(tutorialGuide.contains("promptAlpha"))
        assertFalse(tutorialGuide.contains("statusBarsPadding"))
        assertTrue(tutorialGuide.contains("LECCIÓN \$level DE"))
    }

    @Test
    fun `tablero bandeja arrastre y ghost comparten bloques sin margen externo`() {
        val gameBoard = File(repoRoot, GAME_BOARD).readText()
        val pieceView = File(repoRoot, PIECE_VIEW).readText()
        assertFalse(gameBoard.contains(".size(cellSize + 1.dp)"))
        assertTrue(gameBoard.contains(".size(cellSize)"))
        assertTrue(pieceView.contains("GAMEPLAY_TILE_GAP_FRACTION = 0f"))
        assertTrue(pieceView.contains("gapFraction = GAMEPLAY_TILE_GAP_FRACTION"))
    }

    private companion object {
        const val MAIN = "app/src/main/java/com/cukiti/teikafusion/"
        const val APP_NAVIGATION = MAIN + "ui/AppNavigation.kt"
        const val OVERLAYS = MAIN + "ui/Overlays.kt"
        const val PREFERENCES = MAIN + "model/PreferencesManager.kt"
        const val LEGAL_OVERLAY = MAIN + "ui/LegalConsentOverlay.kt"
        const val HOME = MAIN + "ui/HomeScreen.kt"
        const val VIEW_MODEL = MAIN + "viewmodel/GameViewModel.kt"
        const val GAME_SCREEN = MAIN + "ui/GameScreen.kt"
        const val GAME_INFO_PANEL = MAIN + "ui/GameInfoPanel.kt"
        const val HAPTICS = MAIN + "ui/Haptics.kt"
        const val OBJECTIVE_REVEAL = MAIN + "ui/ObjectiveRevealOverlay.kt"
        const val TUTORIAL_GUIDE = MAIN + "ui/TutorialGuideOverlay.kt"
        const val GAME_BOARD = MAIN + "ui/GameBoard.kt"
        const val VFX = MAIN + "ui/VFX.kt"
        const val PIECE_VIEW = MAIN + "ui/PieceView.kt"
    }
}
