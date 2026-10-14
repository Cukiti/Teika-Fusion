package com.cukiti.teikafusion.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.cukiti.teikafusion.model.*
import com.cukiti.teikafusion.ui.*
import com.cukiti.teikafusion.ui.theme.TeikaFusionTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
class LineReplacementScreenshotTest {
    @get:Rule val compose = createComposeRule()

    @Test fun collapse_feedback() {
        val grid: MutableMap<Position,CellContent> = (0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap().toMutableMap()
        for (i in 0..7) if (i != 4) {
            grid[Position(i,4)] = CellContent.Wood(colorType = 1)
            grid[Position(4,i)] = CellContent.Wood(colorType = 1)
        }
        grid[Position(3,3)] = CellContent.Wood(colorType = 2,targetColor = TargetColor.RED)
        val state = mutableStateOf(GameState(level = HouseRule.levelsWith(HouseRule.SHOCKWAVE).first(),grid = grid,status = GameStatus.PLAYING))
        val pulses = mutableStateOf(emptyList<GameEvent.RulePulse>())
        val drag = mutableStateOf<DragState>(DragState.Dragging(Piece(shape = listOf(Position(0,0))),Position(4,4),true,
            clearRows = listOf(4),clearCols = listOf(4),clearRowSpans = listOf(4 to 0..7)))
        compose.mainClock.autoAdvance = false
        compose.setContent {
            TeikaFusionTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFF101A32)).padding(20.dp),horizontalAlignment = Alignment.CenterHorizontally,verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text(HouseRule.forLevel(state.value.level)?.badge.orEmpty(),color = Color.White)
                    Box(Modifier.size(320.dp)) {
                        GameBoard(state.value,onBoardPositioned = { _, _ -> },cellSize = 40.dp)
                        ReplacementRuleLayer(state.value,40.dp,pulses.value,true,{})
                    }
                }
            }
        }
        compose.mainClock.advanceTimeBy(1000)
        compose.mainClock.advanceTimeBy(400)
        compose.runOnIdle {
            val wood = CellContent.Wood(colorType = 2,targetColor = TargetColor.BLUE)
            state.value = state.value.copy(level = HouseRule.levelsWith(HouseRule.AVALANCHE).first(),
                grid = grid.mapValues { CellContent.Empty } + (Position(0,7) to wood) + (Position(1,7) to CellContent.Wood(colorType = 2)),hiddenWoodCells = setOf(Position(0,7)),
                events = listOf(GameEvent.WoodFall(from = Position(0,0),to = Position(0,7),colorType = 2,wood = wood,targetColor = TargetColor.BLUE,durationMs = 570)))
            pulses.value = listOf(GameEvent.RulePulse("avalanche",listOf(Position(0,0))))
        }
        compose.mainClock.advanceTimeBy(250)
        compose.onRoot().captureRoboImage("src/test/screenshots/regla_avalancha.png")
    }
}
