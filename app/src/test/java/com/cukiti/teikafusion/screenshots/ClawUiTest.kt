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
@Config(sdk=[35],qualifiers="w400dp-h900dp-xxhdpi")
class ClawUiTest {
    @get:Rule val compose=createComposeRule()
    private fun state()=GameState(level=HouseRule.levelsWith(HouseRule.ELEVATOR).first(),status=GameStatus.PLAYING,
        grid=(0..7).flatMap { y->(0..7).map { x->Position(x,y) to if(y==4 && x in 2..5) CellContent.Wood(colorType=x%3,skipSpawnAnimation=true) else CellContent.Empty } }.toMap(),clawCharged=true)
    @Test fun drag_only_previews_and_confirmation_keeps_controls_fixed() {
        var fired=0; val aim=mutableStateOf<ClawAim?>(ClawAim()); val t=ClawTrip("load",1.5f,6.5f,6.5f,2.5f,.5f)
        compose.setContent { Box(Modifier.size(320.dp,408.dp)) {
            ClawLayer(aim.value,if(aim.value?.x!=null) t else null,null,40.dp.value, { _,_->aim.value=ClawAim("load") },
                { x,y->aim.value=aim.value?.copy(x=x,y=y,feedback="Listo para soltar") },Modifier.size(320.dp).testTag("aim"))
            ClawControls(true,aim.value,true,aim.value?.x!=null,{}, { aim.value=null },{ fired++ },Modifier.align(Alignment.BottomCenter).testTag("controls"))
        } }
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onNodeWithContentDescription("Soltar Teika con la garra").assertIsNotEnabled()
        compose.onNodeWithTag("aim").performTouchInput { down(Offset(width*.2f,height*.8f)); moveTo(Offset(width*.8f,height*.3f),300); up() }
        compose.runOnIdle { assertEquals(0,fired); assertNotNull(aim.value?.x) }
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onNodeWithContentDescription("Soltar Teika con la garra").performClick()
        compose.runOnIdle { assertEquals(1,fired) }
    }
    @Test fun uncharged_control_is_disabled() {
        var starts=0
        compose.setContent { ClawControls(false,null,true,false,{ starts++ },{}, {},Modifier.testTag("controls")) }
        compose.onNodeWithText("Limpia una línea").assertIsDisplayed()
        compose.onNodeWithContentDescription("Garra: limpia una línea para cargar").assertIsNotEnabled()
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.runOnIdle { assertEquals(0,starts) }
    }
    @Test fun cancelled_gesture_discards_target_and_cancel_button_never_fires() {
        var reset=0; var fired=0; var cancelled=0
        compose.setContent { Box(Modifier.size(320.dp,408.dp)) {
            ClawLayer(ClawAim("load"),null,null,40f,{ _,_-> },{ x,_->if(!x.isFinite()) reset++ },Modifier.size(320.dp).testTag("aim"))
            ClawControls(true,ClawAim("load"),true,false,{}, { cancelled++ },{ fired++ },Modifier.align(Alignment.BottomCenter))
        } }
        compose.onNodeWithTag("aim").performTouchInput { down(center); moveTo(center+Offset(30f,0f)); cancel() }
        compose.onNodeWithContentDescription("Cancelar garra").performClick()
        compose.runOnIdle { assertEquals(1,reset); assertEquals(0,fired); assertEquals(1,cancelled) }
    }
    @Test fun transporting_control_announces_busy_state_without_offering_another_charge() {
        compose.setContent { ClawControls(false,null,true,false,{}, {},{},Modifier.testTag("controls"),transporting=true) }
        compose.onNodeWithText("Trasladando").assertIsDisplayed()
        compose.onNodeWithContentDescription("Garra trasladando Teika").assertIsNotEnabled()
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
    }
    @Test fun landing_uses_exact_destination_and_one_event_consumer() {
        val base=state(); val t=ClawTrip("load",1.5f,6.5f,6.25f,2.75f,.5f,1f)
        val state=mutableStateOf(base.copy(clawCharged=false)); val show=mutableStateOf(true); var consumed=0
        val pulse=GameEvent.RulePulse("claw_land",emptyList(),clawLanding=t,durationMs=240)
        val bodies=kotlinx.coroutines.flow.MutableStateFlow(listOf(com.cukiti.teikafusion.viewmodel.TeikaBody(t.bodyId,TeikaType.TEIKA_1,t.toX,t.toY)))
        compose.mainClock.autoAdvance=false
        compose.setContent { TeikaFusionTheme { if(show.value) GameBoard(state.value,{ _,_-> },cellSize=40.dp,teikaBodies=bodies,
            onEventConsumed={ id-> if(id==pulse.id) consumed++; state.value=state.value.copy(events=state.value.events.filterNot { it.id==id }) }) } }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle { state.value=state.value.copy(events=listOf(pulse)) }
        compose.mainClock.advanceTimeBy(100)
        compose.onRoot().captureRoboImage("src/test/screenshots/garra_aterrizaje.png")
        compose.mainClock.advanceTimeBy(700)
        compose.runOnIdle { assertEquals(1,consumed); assertTrue(state.value.events.isEmpty()); show.value=false }
        compose.mainClock.advanceTimeBy(1500)
    }
    private fun capture(name:String,p:Float?=null,reduced:Boolean=false,invalid:Boolean=false) {
        val base=state(); val t=ClawTrip("load",1.5f,6.5f,6.5f,2.5f,.5f,p ?: 0f)
        val (x,y)=if(p==null) t.fromX to t.fromY else ClawRules.position(t)
        val bodies=kotlinx.coroutines.flow.MutableStateFlow(listOf(com.cukiti.teikafusion.viewmodel.TeikaBody(t.bodyId,TeikaType.TEIKA_1,x,y,kinematic=p!=null)))
        val aim=if(p==null) ClawAim(t.bodyId,if(invalid) 3.5f else t.toX,if(invalid) 4.5f else t.toY,if(invalid) "Busca un hueco libre" else "Listo para soltar",fromX=t.fromX,fromY=t.fromY) else null
        val show=mutableStateOf(true)
        compose.mainClock.autoAdvance=false
        compose.setContent { TeikaFusionTheme { CompositionLocalProvider(LocalReducedFx provides reduced) {
            if(show.value) Box(Modifier.size(320.dp,408.dp).background(Color(0xFF101A32))) {
                GameBoard(base.copy(clawTrip=if(p!=null) t else null),{ _,_-> },cellSize=40.dp,teikaBodies=bodies,
                    clawAim=aim,clawPlan=if(p==null && !invalid) t else null)
                ClawControls(p==null,aim,false,p==null && !invalid,{}, {},{},Modifier.align(Alignment.BottomCenter),transporting=p!=null)
            }
        } } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        compose.runOnIdle { show.value=false }
        compose.mainClock.advanceTimeBy(1500)
    }
    @Test fun preview()=capture("garra_previa")
    @Test fun blocked_destination()=capture("garra_bloqueada",invalid=true)
    @Test fun pickup()=capture("garra_recogida",.18f)
    @Test fun suspended_traverse()=capture("garra_traslado",.54f)
    @Test fun release()=capture("garra_caida",.9f)
    @Test fun reduced_transport()=capture("garra_reducida",.54f,true)
}
