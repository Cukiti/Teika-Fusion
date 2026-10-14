package com.cukiti.teikafusion.screenshots

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
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
@Config(sdk=[35],qualifiers="w411dp-h891dp-xxhdpi")
class CompactorUiTest {
    @get:Rule val compose=createComposeRule()
    private fun initial(): GameState {
        val grid=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()
        return GameState(level=HouseRule.levelsWith(HouseRule.PRECISION_CUT).first(),status=GameStatus.PLAYING,grid=grid+mapOf(
            Position(1,3) to CellContent.Wood(targetColor=TargetColor.RED),Position(3,3) to CellContent.Wood(colorType=2),
            Position(6,3) to CellContent.Wood(armor=1)),compactorCharged=true)
    }
    @Test fun swipe_previews_destinations_and_releases_selected_row() {
        val s=initial(); val shots=mutableListOf<Triple<Int,Int,Int>>()
        compose.mainClock.autoAdvance=false
        compose.setContent { TeikaFusionTheme { Box(Modifier.size(320.dp,390.dp)) {
            Box(Modifier.size(320.dp)) {
                GameBoard(s,{ _,_ -> },cellSize=40.dp)
                CompactorAimLayer(true,with(LocalDensity.current) { 40.dp.toPx() },
                    { x,y,d -> CompactorRules.plan(s,emptySet(),x,y,d) },{},
                    { x,y,d -> shots.add(Triple(x,y,d)) },Modifier.fillMaxSize().testTag("aim"))
            }
            CompactorButton(true,true,true,{},Modifier.align(Alignment.BottomCenter))
        } } }
        compose.mainClock.advanceTimeBy(1000)
        val node=compose.onNodeWithTag("aim")
        node.performTouchInput { down(Offset(width*2.5f/8,height*3.5f/8)); moveBy(Offset(width/4f,0f)) }
        compose.mainClock.advanceTimeBy(32)
        compose.onRoot().captureRoboImage("src/test/screenshots/compactadora_apuntado.png")
        node.performTouchInput { up() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(listOf(Triple(2,3,0)),shots) }
    }
    @Test fun tap_cancel_and_release_outside_do_not_activate() {
        var cancelled=0; var fired=0; val s=initial()
        compose.setContent { CompactorAimLayer(true,with(LocalDensity.current) { 40.dp.toPx() },
            { x,y,d -> CompactorRules.plan(s,emptySet(),x,y,d) },{ cancelled++ },{ _,_,_ -> fired++ },Modifier.size(320.dp).testTag("aim")) }
        val node=compose.onNodeWithTag("aim")
        node.performTouchInput { click(center) }
        node.performTouchInput { down(Offset(width*2.5f/8,height*3.5f/8)); moveBy(Offset(width/4f,0f)); cancel() }
        node.performTouchInput { down(Offset(width*2.5f/8,height*3.5f/8)); moveTo(Offset(width+20f,height*3.5f/8)); up() }
        compose.runOnIdle { assertEquals(0,fired); assertEquals(3,cancelled) }
    }
    private fun press(reduced:Boolean) {
        val initial=initial()
        val plan=requireNotNull(CompactorRules.plan(initial,emptySet(),2,3,0))
        val grid=initial.grid.toMutableMap()
        val carried=plan.moves.map { it to (grid.getValue(it.first) as CellContent.Wood) }
        carried.forEach { (m,_) -> grid[m.first]=CellContent.Empty }
        carried.forEach { (m,w) -> grid[m.second]=w.copy(skipSpawnAnimation=true) }
        val pulse=GameEvent.RulePulse("compactor",plan.cells,moves=plan.moves,direction=0,hitTimes=listOf(if(reduced) 0 else 800),durationMs=if(reduced) 120 else 1000)
        val events=listOf(pulse)+carried.map { (m,w) -> GameEvent.WoodFall(m.first,m.second,w.colorType,
            targetColor=w.targetColor,wood=w,armor=w.armor,delayMs=if(reduced) 0 else 180,durationMs=if(reduced) 120 else 620,slide=true) }
        val state=mutableStateOf(initial.copy(grid=grid,events=events,compactorCharged=false,compactorPending=true,
            hiddenWoodCells=carried.mapTo(HashSet()) { it.first.second }))
        var consumed=0
        compose.mainClock.autoAdvance=false
        compose.setContent { TeikaFusionTheme {
            androidx.compose.runtime.CompositionLocalProvider(LocalReducedFx provides reduced) {
                GameBoard(state.value,{ _,_ -> },cellSize=40.dp,
                    onRevealWood={ cells,_ -> state.value=state.value.copy(hiddenWoodCells=state.value.hiddenWoodCells-cells) },
                    onEventConsumed={ id -> if(id==pulse.id) consumed++; state.value=state.value.copy(events=state.value.events.filterNot { it.id==id }) })
            }
        } }
        compose.onRoot().assertWidthIsEqualTo(320.dp)
        compose.onRoot().assertHeightIsEqualTo(320.dp)
        compose.mainClock.advanceTimeBy(if(reduced) 64 else 560)
        compose.onRoot().captureRoboImage("src/test/screenshots/compactadora_${if(reduced) "reducida" else "compresion"}.png")
        compose.mainClock.advanceTimeBy(1300)
        compose.runOnIdle { assertEquals(1,consumed) }
    }
    @Test fun press_compression_preserves_stars_and_crystal()=press(false)
    @Test fun reduced_press_keeps_the_confirmation()=press(true)
}
