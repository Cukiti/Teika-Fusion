package com.cukiti.teikafusion.screenshots

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
class ReactorUiTest {
    @get:Rule val compose=createComposeRule()
    private val origin=Position(2,2)
    private fun initial()=GameState(level=HouseRule.levelsWith(HouseRule.TRAIL).first(),status=GameStatus.PLAYING,
        grid=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()+
            listOf(origin,Position(3,2),Position(4,2),Position(3,3),Position(3,4)).mapIndexed { i,p ->
                p to CellContent.Wood(id="reactor-visual-$i",colorType=2,skipSpawnAnimation=true,targetColor=if(i==0) TargetColor.RED else null,teikaSeed=i==4)
            }.toMap(),reactorCharged=true)
    @Test fun tap_previews_without_detonating_and_fixed_controls_confirm_once() {
        val s=initial(); val aim=mutableStateOf<ReactorAim?>(ReactorAim()); var fired=0
        compose.mainClock.autoAdvance=false
        compose.setContent { TeikaFusionTheme { Box(Modifier.size(320.dp,408.dp).background(Color(0xFF101A32))) {
            val current=aim.value
            val plan=current?.origin?.let { ReactorRules.plan(s,emptySet(),it) }
            GameBoard(s,{ _,_ -> },cellSize=40.dp,reactorAim=current,reactorPlan=plan,
                onReactorSelect={ p -> aim.value=ReactorAim(p,"5 bloques conectados") })
            ReactorControls(true,current,true,plan!=null,{}, { aim.value=null },{ fired++; aim.value=null },Modifier.align(Alignment.BottomCenter).testTag("controls"))
        } } }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithContentDescription("Detonar grupo de color").assertIsNotEnabled()
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onRoot().performTouchInput { click(Offset(width*2.1f/8,height*2.1f*40/408)) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(0,fired); assertEquals(origin,aim.value!!.origin) }
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onRoot().captureRoboImage("src/test/screenshots/reactor_previa.png")
        compose.onNodeWithContentDescription("Detonar grupo de color").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1,fired) }
    }
    @Test fun cancelled_touch_and_cancel_preserve_selection_without_detonation() {
        var taps=0; var fired=0; var cancelled=0
        compose.setContent { Box(Modifier.size(320.dp,408.dp).background(Color(0xFF101A32))) {
            ReactorAimLayer(ReactorAim(),null,100f,{ taps++ },Modifier.size(320.dp).testTag("aim"))
            ReactorControls(true,ReactorAim(),true,false,{}, { cancelled++ },{ fired++ },Modifier.align(Alignment.BottomCenter))
        } }
        compose.onNodeWithTag("aim").performTouchInput { down(center); cancel() }
        compose.onNodeWithContentDescription("Cancelar reactor").performClick()
        compose.runOnIdle { assertEquals(0,taps); assertEquals(0,fired); assertEquals(1,cancelled) }
    }
    private fun reaction(reduced:Boolean,at:Long,name:String) {
        val s=initial(); val plan=requireNotNull(ReactorRules.plan(s,emptySet(),origin))
        val pulse=GameEvent.RulePulse("reactor",plan.cells,points=listOf(origin),moves=plan.links,
            wood=plan.cells.map { it to s.grid.getValue(it) as CellContent.Wood },
            bodyIds=listOf("reactor-born"),durationMs=if(reduced) 120 else plan.durationMs,hitTimes=plan.cells.indices.map { if(reduced) 0 else plan.hit(it) })
        val events=listOf(pulse)+pulse.wood.filter { it.second.teikaSeed }.map { (p,_) -> GameEvent.TeikaSeedBurst(p,1,delayMs=if(reduced) 0 else plan.hit(plan.cells.indexOf(p))) }
        val state=mutableStateOf(s.copy(grid=s.grid.mapValues { CellContent.Empty },events=events,reactorCharged=false,reactorPending=true))
        val bodies=kotlinx.coroutines.flow.MutableStateFlow(listOf(com.cukiti.teikafusion.viewmodel.TeikaBody("reactor-born",TeikaType.TEIKA_1,3.5f,4.5f)))
        val consumed=mutableMapOf<String,Int>()
        compose.mainClock.autoAdvance=false
        compose.setContent { TeikaFusionTheme { CompositionLocalProvider(LocalReducedFx provides reduced) {
            GameBoard(state.value,{ _,_ -> },cellSize=40.dp,teikaBodies=bodies,
                onEventConsumed={ id -> consumed[id]=(consumed[id] ?: 0)+1; state.value=state.value.copy(events=state.value.events.filterNot { it.id==id }) })
        } } }
        compose.onRoot().assertWidthIsEqualTo(320.dp); compose.onRoot().assertHeightIsEqualTo(320.dp)
        compose.mainClock.advanceTimeBy(at)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        compose.mainClock.advanceTimeBy(1500)
        compose.runOnIdle { assertEquals(events.associate { it.id to 1 },consumed) }
    }
    @Test fun anticipation_preserves_star_and_seed()=reaction(false,160,"reactor_carga")
    @Test fun branching_reaction_shatters_from_origin()=reaction(false,290,"reactor_reaccion")
    @Test fun seed_opens_and_reveals_born_body()=reaction(false,430,"reactor_eclosion")
    @Test fun reduced_reaction_uses_static_confirmation()=reaction(true,64,"reactor_reducido")
}
