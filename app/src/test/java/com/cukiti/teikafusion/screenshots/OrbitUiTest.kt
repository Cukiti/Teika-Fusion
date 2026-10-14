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
class OrbitUiTest {
    @get:Rule val compose=createComposeRule()
    private val c=Position(2,2)
    private fun initial()=GameState(level=HouseRule.levelsWith(HouseRule.COMET).first(),status=GameStatus.PLAYING,
        grid=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()+mapOf(
            Position(1,1) to CellContent.Wood(id="orbit-star",targetColor=TargetColor.RED),
            Position(3,2) to CellContent.Wood(id="orbit-seed",teikaSeed=true,colorType=2),
            Position(1,3) to CellContent.Wood(id="orbit-wood",colorType=3)),orbitCharged=true)
    @Test fun drag_and_direction_preview_never_fire_until_confirmation_and_panel_stays_fixed() {
        val s=initial(); val aim=mutableStateOf<OrbitAim?>(OrbitAim()); var fired=0; val show=mutableStateOf(true)
        compose.mainClock.autoAdvance=false
        compose.setContent { if(show.value) TeikaFusionTheme { Box(Modifier.size(320.dp,408.dp).background(Color(0xFF101A32))) {
            val a=aim.value
            val plan=a?.center?.let { OrbitRules.plan(s,emptySet(),it,a.clockwise) }
            GameBoard(s,{ _,_ -> },cellSize=40.dp,orbitAim=a,orbitPlan=plan,
                onOrbitSelect={ center -> aim.value=a!!.copy(center=center,feedback="Listo para girar") })
            OrbitControls(true,a,true,plan!=null,{}, { aim.value=null },{ fired++; aim.value=null },
                { aim.value=a!!.copy(clockwise=!a.clockwise) },Modifier.align(Alignment.BottomCenter).testTag("controls"))
        } } }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithContentDescription("Confirmar giro orbital").assertIsNotEnabled()
        compose.onRoot().performTouchInput { down(Offset(width*1.5f/8,height*1.5f*40/408)); moveTo(Offset(width*2.5f/8,height*2.5f*40/408)); up() }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertEquals(0,fired); assertEquals(c,aim.value!!.center) }
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onNodeWithContentDescription("Giro a la derecha: cambiar dirección").performClick()
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { assertFalse(aim.value!!.clockwise); assertEquals(0,fired) }
        compose.onNodeWithContentDescription("Confirmar giro orbital").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1,fired); show.value=false }; compose.mainClock.advanceTimeBy(1500)
    }
    @Test fun invalid_preview_and_cancel_preserve_confirmation_control_dimensions() {
        var fired=0; var cancelled=0
        compose.setContent { OrbitControls(true,OrbitAim(c,feedback="Zona protegida"),true,false,{}, { cancelled++ },{ fired++ },{},Modifier.testTag("controls")) }
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onNodeWithContentDescription("Confirmar giro orbital").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Cancelar giro orbital").performClick()
        compose.runOnIdle { assertEquals(0,fired); assertEquals(1,cancelled) }
    }
    @Test fun uncharged_button_is_disabled_and_accessible() {
        compose.setContent { OrbitControls(false,null,false,false,{},{},{},{},Modifier.testTag("controls")) }
        compose.onNodeWithContentDescription("Giro orbital: limpia una línea para cargar").assertIsNotEnabled()
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
    }
    private fun frame(name: String,at: Long=0,reduced: Boolean=false,preview: Boolean=false,blocked: Boolean=false,dense: Boolean=false,edge: Boolean=false) {
        val center=if(edge) Position(1,1) else c
        val c=center
        val base=initial()
        val s=if(dense) base.copy(grid=base.grid+OrbitRules.cells(c).associateWith { CellContent.Wood(id="dense-${it.x}-${it.y}",colorType=(it.x+it.y*3)%4) }) else base; val plan=requireNotNull(OrbitRules.plan(s,emptySet(),c,true))
        val pulse=GameEvent.RulePulse("orbit",plan.cells,points=listOf(c),direction=1,durationMs=if(reduced) 120 else 1060,hitTimes=listOf(if(reduced) 0 else 820))
        val events=listOf(pulse)+plan.moves.map { (from,to) ->
            val w=s.grid[from] as CellContent.Wood
            GameEvent.WoodFall(from,to,w.colorType,wood=w,targetColor=w.targetColor,slide=true,orbitCenter=c,
                delayMs=if(reduced) 0 else 180,durationMs=if(reduced) 120 else 640)
        }
        val state=mutableStateOf(s)
        val aim=mutableStateOf<OrbitAim?>(null); val show=mutableStateOf(true); val consumed=mutableMapOf<String,Int>()
        compose.mainClock.autoAdvance=false
        compose.setContent { if(show.value) TeikaFusionTheme { CompositionLocalProvider(LocalReducedFx provides reduced) {
            Box(Modifier.size(320.dp,408.dp).background(Color(0xFF101A32))) {
                GameBoard(state.value,{ _,_ -> },cellSize=40.dp,orbitAim=aim.value,orbitPlan=if(blocked) null else if(aim.value!=null) plan else null,
                    onRevealWood={ cells,_ -> state.value=state.value.copy(hiddenWoodCells=state.value.hiddenWoodCells-cells) },
                    onEventConsumed={ id -> consumed[id]=(consumed[id] ?: 0)+1; state.value=state.value.copy(events=state.value.events.filterNot { it.id==id }) })
                OrbitControls(!state.value.orbitPending,aim.value,!state.value.orbitPending,preview && !blocked,{},{},{},{},Modifier.align(Alignment.BottomCenter))
            }
        } } }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle {
            if(preview) aim.value=OrbitAim(c,feedback=if(blocked) "Zona protegida" else "Listo para girar")
            else state.value=s.copy(grid=OrbitRules.apply(s.grid,plan),events=events,orbitCharged=false,orbitPending=true,hiddenWoodCells=plan.moves.mapTo(HashSet()) { it.second })
        }
        compose.mainClock.advanceTimeBy(if(preview) 32 else at)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        if(!preview) {
            compose.mainClock.advanceTimeBy(1400)
            compose.runOnIdle { assertEquals(events.size,consumed.size); assertTrue(consumed.values.all { it==1 }); assertTrue(state.value.hiddenWoodCells.isEmpty()) }
        }
        compose.runOnIdle { show.value=false }; compose.mainClock.advanceTimeBy(1500)
    }
    @Test fun preview()=frame("orbita_previa",preview=true)
    @Test fun protected_zone()=frame("orbita_bloqueada",preview=true,blocked=true)
    @Test fun rigid_lift()=frame("orbita_agarre",120)
    @Test fun halfway_rotation()=frame("orbita_giro",500)
    @Test fun dense_rotation()=frame("orbita_giro_llena",500,dense=true)
    @Test fun edge_rotation()=frame("orbita_giro_borde",500,dense=true,edge=true)
    @Test fun landing()=frame("orbita_encaje",860)
    @Test fun reduced_confirmation()=frame("orbita_reducida",64,reduced=true)
}
