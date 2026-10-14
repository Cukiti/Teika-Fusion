package com.cukiti.teikafusion.screenshots

import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.cukiti.teikafusion.model.*
import com.cukiti.teikafusion.ui.*
import com.cukiti.teikafusion.ui.theme.TeikaFusionTheme
import com.cukiti.teikafusion.viewmodel.TeikaBody
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],qualifiers="w411dp-h891dp-xxhdpi")
class PinballGestureTest {
    @get:Rule val compose=createComposeRule()
    private fun render(releases: MutableList<Pair<Float,Float>>,cancellations: ()->Unit) {
        val grid=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()+
            (Position(6,3) to CellContent.Wood(armor=1))
        val state=GameState(level=HouseRule.levelsWith(HouseRule.TRAMPOLINE).first(),status=GameStatus.PLAYING,grid=grid,pinballCharged=true)
        val bodies=MutableStateFlow(listOf(TeikaBody("aim",TeikaType.TEIKA_1,3.5f,3.5f)))
        compose.mainClock.autoAdvance=false
        compose.setContent {
            TeikaFusionTheme { Box(Modifier.size(320.dp)) {
                GameBoard(state.copy(level=0),{ _,_ -> },teikaBodies=bodies,cellSize=40.dp)
                PinballLayer(state,bodies,with(LocalDensity.current) { 40.dp.toPx() },true,
                    { true },cancellations,{ _,x,y -> releases.add(x to y) },{},Modifier.fillMaxSize().testTag("pinball"))
            } }
        }
        compose.mainClock.advanceTimeBy(1000)
    }
    @Test fun long_press_aim_shows_first_segment_and_releases_opposite_pull() {
        val releases=mutableListOf<Pair<Float,Float>>()
        render(releases,{})
        val node=compose.onNodeWithTag("pinball")
        node.performTouchInput { down(Offset(width*3.5f/8,height*3.5f/8)) }
        compose.mainClock.advanceTimeBy(600)
        node.performTouchInput { moveBy(Offset(-width/8f,0f)) }
        compose.mainClock.advanceTimeBy(32)
        compose.onRoot().captureRoboImage("src/test/screenshots/premium_pinball_apuntado.png")
        node.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(1,releases.size); assertTrue(releases.single().first<-.9f); assertEquals(0f,releases.single().second,.001f) }
    }
    @Test fun short_tap_and_cancel_never_fire() {
        val releases=mutableListOf<Pair<Float,Float>>(); var cancelled=0
        render(releases,{ cancelled++ })
        val node=compose.onNodeWithTag("pinball")
        node.performTouchInput { down(Offset(width*3.5f/8,height*3.5f/8)); advanceEventTime(80); up() }
        compose.mainClock.advanceTimeBy(100)
        node.performTouchInput { down(Offset(width*3.5f/8,height*3.5f/8)) }
        compose.mainClock.advanceTimeBy(600)
        node.performTouchInput { cancel() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertTrue(releases.isEmpty()); assertTrue(cancelled>=2) }
    }
}
