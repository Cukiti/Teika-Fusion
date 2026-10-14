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
class TurbineUiTest {
    @get:Rule val compose=createComposeRule()
    private val beam=TurbineBeam(0f,5.5f,1f,0f)
    private fun initial()=GameState(level=HouseRule.levelsWith(HouseRule.SLIDE).first(),status=GameStatus.PLAYING,
        grid=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()+mapOf(
            Position(3,2) to CellContent.Wood(id="wind-star",targetColor=TargetColor.RED),
            Position(4,2) to CellContent.Wood(id="wind-wood",colorType=2),
            Position(5,2) to CellContent.Wood(id="wind-seed",teikaSeed=true,colorType=2)),turbineCharged=true)
    @Test fun drag_and_release_only_aim_and_explicit_confirmation_fires_once() {
        val s=initial(); val aim=mutableStateOf<TurbineAim?>(TurbineAim()); var fired=0; val show=mutableStateOf(true)
        compose.mainClock.autoAdvance=false
        compose.setContent { if(show.value) TeikaFusionTheme { Box(Modifier.size(320.dp,408.dp).background(Color(0xFF101A32))) {
            TurbineLayer(s,aim.value,remember { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) },with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() },false,
                { x,y -> aim.value=TurbineAim(TurbineRules.anchor(x,y,8,8)) },
                { x,y -> aim.value=aim.value!!.copy(beam=TurbineRules.aim(aim.value!!.beam!!,x,y,8,8),feedback="Listo para soplar") },Modifier.size(320.dp))
            TurbineControls(true,aim.value,null,true,aim.value?.beam!=null,{}, { aim.value=null },{ fired++; aim.value=null },Modifier.align(Alignment.BottomCenter).testTag("controls"))
        } } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithContentDescription("Confirmar ráfaga").assertIsNotEnabled()
        compose.onRoot().performTouchInput { down(Offset(2f,height*220f/408)); moveTo(Offset(width*.4f,height*220f/408)); up() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(0,fired); assertNotNull(aim.value!!.beam) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onNodeWithContentDescription("Confirmar ráfaga").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1,fired); show.value=false }; compose.mainClock.advanceTimeBy(1500)
    }
    @Test fun invalid_cancel_and_uncharged_states_are_accessible_and_fixed_size() {
        var fired=0; var cancelled=0
        compose.setContent { TurbineControls(true,TurbineAim(beam,"Sin Teikas alcanzables"),null,true,false,{}, { cancelled++ },{ fired++ },Modifier.testTag("controls")) }
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onNodeWithContentDescription("Confirmar ráfaga").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Cancelar turbina").performClick()
        compose.runOnIdle { assertEquals(0,fired); assertEquals(1,cancelled) }
    }
    @Test fun charge_hint_disabled() {
        compose.setContent { TurbineControls(false,null,null,false,false,{},{},{}) }
        compose.onNodeWithContentDescription("Turbina: limpia una línea para cargar").assertIsNotEnabled()
    }
    @Test fun finished_wind_waits_for_its_chain_with_clear_feedback() {
        compose.setContent { TurbineControls(false,null,TurbineGust(beam,2000f,chainPending=true),false,false,{},{},{}) }
        compose.onNodeWithText("Resolviendo").assertIsDisplayed()
        compose.onNodeWithContentDescription("Turbina: limpia una línea para cargar").assertIsNotEnabled()
    }
    private fun frame(name: String,elapsed: Float=0f,preview: Boolean=false,blocked: Boolean=false,reduced: Boolean=false,ninja: Boolean=false) {
        val s=initial().let { if(blocked) it.copy(grid=it.grid+(0..7).associate { y -> Position(1,y) to CellContent.Wood(colorType=1) }) else it }.copy(ninjaCutActive=ninja)
        val show=mutableStateOf(true)
        val bodies=kotlinx.coroutines.flow.MutableStateFlow(listOf(
            com.cukiti.teikafusion.viewmodel.TeikaBody("small",TeikaType.TEIKA_1,3f,5.5f),
            com.cukiti.teikafusion.viewmodel.TeikaBody("heavy",TeikaType.TEIKA_2,5f,6.4f),
            com.cukiti.teikafusion.viewmodel.TeikaBody("sheltered",TeikaType.TEIKA_1,4f,1f)))
        val gust=if(preview) null else TurbineGust(beam,elapsed)
        val aim=if(preview) TurbineAim(beam,if(blocked) "Sin Teikas alcanzables" else "Listo para soplar") else null
        compose.mainClock.autoAdvance=false
        compose.setContent { if(show.value) TeikaFusionTheme { CompositionLocalProvider(LocalReducedFx provides reduced) {
            Box(Modifier.size(320.dp,408.dp).background(Color(0xFF101A32))) {
                GameBoard(s.copy(turbineGust=gust),{ _,_-> },cellSize=40.dp,teikaBodies=bodies,turbineAim=aim)
                TurbineControls(preview,aim,gust,true,!blocked,{},{},{},Modifier.align(Alignment.BottomCenter))
            }
        } } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        compose.runOnIdle { show.value=false }; compose.mainClock.advanceTimeBy(1500)
    }
    @Test fun preview()=frame("turbina_previa",preview=true)
    @Test fun blocked_air()=frame("turbina_bloqueada",preview=true,blocked=true)
    @Test fun spin_up()=frame("turbina_arranque",180f)
    @Test fun full_gust()=frame("turbina_corriente",720f)
    @Test fun deceleration()=frame("turbina_frenado",1900f)
    @Test fun reduced_gust()=frame("turbina_reducida",720f,reduced=true)
    @Test fun ninja_stops_air()=frame("turbina_ninja",720f,ninja=true)
}
