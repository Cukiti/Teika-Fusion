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
class WreckingBallUiTest {
    @get:Rule val compose=createComposeRule()
    private val origin=Position(2,1)
    private fun initial()=GameState(level=HouseRule.levelsWith(HouseRule.DOMINO).first(),status=GameStatus.PLAYING,
        grid=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()+
            listOf(origin,Position(3,2),Position(3,3),Position(3,4)).mapIndexed { i,p ->
                p to CellContent.Wood(id="ball-visual-$i",colorType=2,skipSpawnAnimation=true,targetColor=if(i==0) TargetColor.RED else null,teikaSeed=i==3)
            }.toMap(),ballCharged=true)
    @Test fun drag_previews_without_launching_and_fixed_controls_confirm_once() {
        val s=initial(); val aim=mutableStateOf<BallAim?>(BallAim()); var fired=0
        compose.mainClock.autoAdvance=false
        compose.setContent { TeikaFusionTheme { Box(Modifier.size(320.dp,408.dp).background(Color(0xFF101A32))) {
            val current=aim.value
            val plan=current?.arc?.let { WreckingBallRules.plan(s,emptySet(),it) }?.takeIf { it.cells.isNotEmpty() }
            GameBoard(s,{ _,_ -> },cellSize=40.dp,ballAim=current,ballPlan=plan,
                onBallAnchor={ x,y-> WreckingBallRules.anchor(s,x,y)?.let { (p,side)-> aim.value=BallAim(anchor=p,side=side) } },
                onBallAim={ x,y-> val a=aim.value!!; aim.value=a.copy(arc=WreckingBallRules.arc(s,a.anchor!!,a.side,x,y),feedback="4 bloques") })
            BallControls(true,current,true,plan!=null,{}, { aim.value=null },{ fired++; aim.value=null },Modifier.align(Alignment.BottomCenter).testTag("controls"))
        } } }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithContentDescription("Lanzar bola demoledora").assertIsNotEnabled()
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onRoot().performTouchInput { down(Offset(width*.02f,height*3.5f*40/408)); moveTo(Offset(width*3.5f/8,height*3.5f*40/408),400); up() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(0,fired); assertEquals(Position(0,3),aim.value!!.anchor) }
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onRoot().captureRoboImage("src/test/screenshots/ball_previa.png")
        compose.onNodeWithContentDescription("Lanzar bola demoledora").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1,fired) }
    }
    @Test fun uncharged_control_explains_loading_without_resizing_or_starting() {
        var starts=0
        compose.setContent { BallControls(false,null,true,false,{ starts++ },{}, {},Modifier.testTag("controls")) }
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onNodeWithText("Limpia una línea").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bola: limpia una línea para cargar").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(0,starts) }
    }
    @Test fun cancelled_touch_and_cancel_preserve_selection_without_detonation() {
        var taps=0; var fired=0; var cancelled=0
        compose.setContent { Box(Modifier.size(320.dp,408.dp).background(Color(0xFF101A32))) {
            BallAimLayer(BallAim(),null,100f,{ _,_-> taps++ },{ _,_-> },Modifier.size(320.dp).testTag("aim"))
            BallControls(true,BallAim(),true,false,{}, { cancelled++ },{ fired++ },Modifier.align(Alignment.BottomCenter))
        } }
        compose.onNodeWithTag("aim").performTouchInput { down(center); cancel() }
        compose.onNodeWithContentDescription("Cancelar bola demoledora").performClick()
        compose.runOnIdle { assertTrue(taps in 1..2); assertEquals(0,fired); assertEquals(1,cancelled) }
    }
    private fun reaction(reduced:Boolean,at:Long,name:String,obstacle:Boolean=false) {
        val base=initial(); val s=if(obstacle) base.copy(grid=base.grid+(Position(3,3) to CellContent.Wood(id="ball-anchor",armor=1,colorType=1,skipSpawnAnimation=true))) else base; val plan=requireNotNull(WreckingBallRules.plan(s,emptySet(),WreckingBallRules.arc(s,Position(0,3),0,3.5f,3.5f)!!))
        val pulse=GameEvent.RulePulse("wrecking_ball",plan.cells,ballArc=plan.arc,
            wood=plan.cells.map { it to s.grid.getValue(it) as CellContent.Wood },
            bodyIds=if(obstacle) emptyList() else listOf("ball-born"),durationMs=if(reduced) 120 else plan.durationMs,hitTimes=plan.cells.indices.map { if(reduced) 0 else plan.hit(it) })
        val events=listOf(pulse)+pulse.wood.filter { it.second.teikaSeed }.map { (p,_) -> GameEvent.TeikaSeedBurst(p,1,delayMs=if(reduced) 0 else plan.hit(plan.cells.indexOf(p))) }
        val state=mutableStateOf(s.copy(grid=s.grid.mapValues { if(it.key in plan.cells) CellContent.Empty else it.value },events=events,ballCharged=false,ballPending=true))
        val bodies=kotlinx.coroutines.flow.MutableStateFlow(if(obstacle) emptyList() else listOf(com.cukiti.teikafusion.viewmodel.TeikaBody("ball-born",TeikaType.TEIKA_1,3.5f,4.5f)))
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
    @Test fun anticipation_preserves_star_and_seed()=reaction(false,160,"ball_carga")
    @Test fun heavy_swing_shatters_along_arc()=reaction(false,550,"ball_barrido")
    @Test fun seed_opens_and_reveals_born_body()=reaction(false,900,"ball_eclosion")
    @Test fun protected_impact_recoils_without_breaking_anchor()=reaction(false,650,"ball_choque",true)
    @Test fun reduced_reaction_uses_static_confirmation()=reaction(true,64,"ball_reducido")
}
