package com.cukiti.teikafusion.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.cukiti.teikafusion.model.*
import com.cukiti.teikafusion.ui.*
import com.cukiti.teikafusion.ui.theme.TeikaFusionTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class ReplacementScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun new_board_marks_and_compactor_control() {
        val state = mutableStateOf(GameState.initial(HouseRule.levelsWith(HouseRule.DOMINO).first()).copy(status = GameStatus.PLAYING))
        val cutting = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TeikaFusionTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFF101A32)).padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(HouseRule.forLevel(state.value.level)?.badge ?: "", color = Color.White)
                    GameBoard(state.value, onBoardPositioned = { _, _ -> }, cellSize = 40.dp)
                    if (cutting.value) CompactorButton(true,false,true,{})
                }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.onRoot().captureRoboImage("src/test/screenshots/regla_domino.png")
        compose.runOnIdle { state.value = GameState.initial(HouseRule.levelsWith(HouseRule.CONSTELLATION).first()).copy(status = GameStatus.PLAYING) }
        compose.mainClock.advanceTimeBy(1000)
        compose.onRoot().captureRoboImage("src/test/screenshots/regla_constelacion.png")
        compose.runOnIdle { state.value = GameState.initial(HouseRule.levelsWith(HouseRule.TRAMPOLINE).first()).copy(status = GameStatus.PLAYING,pinballCharged = true) }
        compose.mainClock.advanceTimeBy(1000)
        compose.onRoot().captureRoboImage("src/test/screenshots/regla_pinball.png")
        compose.runOnIdle { state.value = GameState.initial(HouseRule.levelsWith(HouseRule.PRECISION_CUT).first()).copy(status = GameStatus.PLAYING); cutting.value = true }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithText("PRENSA").assertIsDisplayed()
        compose.onRoot().captureRoboImage("src/test/screenshots/regla_compactadora.png")
    }

    @Test fun rule_feedback_is_consumed_once_in_reduced_motion() {
        var consumed = 0
        val event = GameEvent.RulePulse("constellation", listOf(Position(0,0)))
        compose.mainClock.autoAdvance = false
        compose.setContent { Box(Modifier.size(320.dp)) { ReplacementRuleLayer(GameState(),40.dp,listOf(event),false,{ consumed++ }) } }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1,consumed) }
        compose.mainClock.advanceTimeBy(500)
        compose.runOnIdle { assertEquals(1,consumed) }
    }
}
