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
class PortalSwapUiTest {
    @get:Rule val compose=createComposeRule()
    private val a=Position(0,1)
    private val b=Position(5,4)
    private fun initial()=GameState(level=HouseRule.levelsWith(HouseRule.ROUTES).first(),status=GameStatus.PLAYING,
        grid=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()+mapOf(
            a to CellContent.Wood(id="portal-star",targetColor=TargetColor.RED),
            Position(1,2) to CellContent.Wood(id="portal-seed",teikaSeed=true,colorType=2),
            b to CellContent.Wood(id="portal-crystal",armor=1,colorType=3)),portalSwapCharged=true)

    @Test fun two_taps_preview_without_firing_then_confirm_once() {
        val s=initial(); val aim=mutableStateOf<PortalSwapAim?>(PortalSwapAim()); var fired=0
        compose.mainClock.autoAdvance=false
        compose.setContent { TeikaFusionTheme { Box(Modifier.size(320.dp,408.dp).background(Color(0xFF101A32))) {
            val current=aim.value
            val plan=current?.let { p -> p.first?.let { first -> p.second?.let { PortalSwapRules.plan(s,emptySet(),first,it) } } }
            GameBoard(s,{ _,_ -> },cellSize=40.dp,portalSwapAim=current,portalSwapPlan=plan,
                onPortalSwapSelect={ p -> aim.value=if(current?.first==null) PortalSwapAim(first=p,feedback="Elige otra zona de 2×2") else current.copy(second=p,feedback="Vista previa del intercambio") })
            PortalSwapControls(true,current,true,plan!=null,{}, { aim.value=null },{ fired++; aim.value=null },Modifier.align(Alignment.BottomCenter))
        } } }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithContentDescription("Confirmar intercambio de zonas").assertIsNotEnabled()
        compose.onRoot().performTouchInput { click(Offset(width*.05f,height*60f/408)) }
        compose.mainClock.advanceTimeBy(32)
        compose.onRoot().performTouchInput { click(Offset(width*5.1f/8,height*4.1f*40/408)) }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(0,fired); assertEquals(a,aim.value!!.first); assertEquals(b,aim.value!!.second) }
        compose.onRoot().captureRoboImage("src/test/screenshots/portal_doble_previa.png")
        compose.onNodeWithContentDescription("Confirmar intercambio de zonas").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1,fired) }
    }
    @Test fun cancelled_touch_and_cancel_button_do_not_fire() {
        val s=initial(); var taps=0; var fired=0; var cancelled=0
        compose.setContent { Box(Modifier.size(320.dp,408.dp).background(Color(0xFF101A32))) {
            PortalSwapAimLayer(s,PortalSwapAim(),null,40.dp,100f,{ taps++ },Modifier.size(320.dp).testTag("aim"))
            PortalSwapControls(true,PortalSwapAim(),true,false,{}, { cancelled++ },{ fired++ },Modifier.align(Alignment.BottomCenter))
        } }
        compose.onNodeWithTag("aim").performTouchInput { down(center); cancel() }
        compose.onNodeWithContentDescription("Cancelar portal doble").performClick()
        compose.runOnIdle { assertEquals(0,taps); assertEquals(0,fired); assertEquals(1,cancelled) }
    }
    private fun transfer(reduced:Boolean,at:Long,name:String) {
        val s=initial(); val plan=requireNotNull(PortalSwapRules.plan(s,emptySet(),a,b))
        val pulse=GameEvent.RulePulse("portal_swap",plan.cells,points=listOf(a,b),moves=plan.moves,
            durationMs=if(reduced) 120 else 960,hitTimes=listOf(if(reduced) 0 else 780))
        val events=listOf(pulse)+plan.moves.map { (from,to) ->
            val w=s.grid[from] as CellContent.Wood
            val forward=from in PortalSwapRules.cells(a)
            GameEvent.WoodFall(from,to,w.colorType,wood=w,targetColor=w.targetColor,armor=w.armor,slide=true,
                portalOrigin=if(forward) a else b,portalDestination=if(forward) b else a,
                delayMs=if(reduced) 0 else 160,durationMs=if(reduced) 120 else 620)
        }
        val state=mutableStateOf(s.copy(grid=PortalSwapRules.apply(s.grid,plan),events=events,portalSwapCharged=false,
            portalSwapPending=true,hiddenWoodCells=plan.moves.mapTo(HashSet()) { it.second }))
        val consumed=mutableMapOf<String,Int>()
        compose.mainClock.autoAdvance=false
        compose.setContent { TeikaFusionTheme { CompositionLocalProvider(LocalReducedFx provides reduced) {
            GameBoard(state.value,{ _,_ -> },cellSize=40.dp,
                onRevealWood={ cells,_ -> state.value=state.value.copy(hiddenWoodCells=state.value.hiddenWoodCells-cells) },
                onEventConsumed={ id -> consumed[id]=(consumed[id] ?: 0)+1; state.value=state.value.copy(events=state.value.events.filterNot { it.id==id }) })
        } } }
        compose.onRoot().assertWidthIsEqualTo(320.dp); compose.onRoot().assertHeightIsEqualTo(320.dp)
        compose.mainClock.advanceTimeBy(at)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        compose.mainClock.advanceTimeBy(1300)
        compose.runOnIdle { assertEquals(events.size,consumed.size); assertTrue(consumed.values.all { it==1 }); assertTrue(state.value.hiddenWoodCells.isEmpty()) }
    }
    @Test fun absorption_preserves_star_and_seed()=transfer(false,360,"portal_doble_absorcion")
    @Test fun materialization_preserves_star_seed_and_crystal()=transfer(false,660,"portal_doble_aparicion")
    @Test fun reduced_transfer_keeps_static_confirmation()=transfer(true,64,"portal_doble_reducido")
}
