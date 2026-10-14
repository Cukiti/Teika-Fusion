package com.cukiti.teikafusion.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.cukiti.teikafusion.model.*
import com.cukiti.teikafusion.ui.AutoBlockWorkshop
import com.cukiti.teikafusion.ui.theme.TeikaFusionTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class WorkshopScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun taller_automatico_y_construccion() {
        val sources = List(3) { Piece(shape = listOf(Position(0, 0), Position(1, 0), Position(0, 1), Position(1, 1))) }
        val bank = BlockBank(blocks = 4)
        val plan = requireNotNull(WorkshopPlanner.plan(bank, sources, 8, 8) { p, x, y -> p.shape.all { x + it.x < 8 && y + it.y < 8 } })
        val offer = WorkshopOffer(sources, bank, plan)
        var deliveries = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TeikaFusionTheme {
                Box(Modifier.fillMaxSize().background(Color(0xFF405384))) {
                    AutoBlockWorkshop(GameState(availablePieces = sources, blockBank = bank), false,
                        loadOffer = { offer }, onBuild = { offer }, onCommitted = {},
                        onDelivered = { pieces, starts ->
                            assertEquals(1, pieces.size); assertEquals(1, starts.size); deliveries++
                        }, onDismiss = {})
                }
            }
        }
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("CREAR").assertIsEnabled()
        compose.onNodeWithText("SUSTITUIR").assertDoesNotExist()
        compose.onRoot().captureRoboImage("src/test/screenshots/taller_automatico.png")
        compose.onNodeWithText("CREAR").performClick()
        compose.mainClock.advanceTimeBy(400)
        compose.onNodeWithText("CANCELAR").assertDoesNotExist()
        compose.onRoot().captureRoboImage("src/test/screenshots/taller_construccion.png")
        compose.mainClock.advanceTimeBy(1150)
        compose.onRoot().captureRoboImage("src/test/screenshots/fichona_revelacion.png")
        compose.mainClock.advanceTimeBy(2500)
        compose.runOnIdle { assertEquals(1, deliveries) }
    }
}
