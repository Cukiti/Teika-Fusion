package com.cukiti.teikafusion.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
class ForgeUiTest {
    @get:Rule val compose=createComposeRule()
    private val cells=listOf(Position(1,2),Position(2,2),Position(2,3),Position(3,3))
    private val output=Position(5,4)
    private fun initial()=GameState(level=HouseRule.levelsWith(HouseRule.SHOCKWAVE).first(),status=GameStatus.PLAYING,
        grid=(0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()+
            cells.mapIndexed { i,p -> p to CellContent.Wood(id="forge-visual-$i",colorType=i,skipSpawnAnimation=true,targetColor=if(i==0) TargetColor.RED else null) }.toMap()+
            (Position(6,6) to CellContent.Wood(teikaSeed=true,skipSpawnAnimation=true)),forgeCharged=true)
    @Test fun tap_selects_only_then_stage_and_explicit_confirmation_with_fixed_controls() {
        val aim=mutableStateOf<ForgeAim?>(ForgeAim()); var fired=0; var taps=0
        val show=mutableStateOf(true)
        compose.mainClock.autoAdvance=false
        compose.setContent { if(show.value) Box(Modifier.size(320.dp,408.dp)) {
            ForgeAimLayer(aim.value,null,with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() },
                { p -> taps++; aim.value=aim.value!!.copy(cells=listOf(p)) },Modifier.size(320.dp).testTag("aim"))
            ForgeControls(true,aim.value,true,aim.value?.destination!=null,{}, { aim.value=null },
                { aim.value=aim.value!!.copy(choosingDestination=true) },{ fired++; aim.value=null },Modifier.align(Alignment.BottomCenter).testTag("controls"))
        } }
        compose.mainClock.advanceTimeBy(32)
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onNodeWithContentDescription("Forjar un Teika").assertIsNotEnabled()
        compose.onNodeWithTag("aim").performTouchInput { click(Offset(width*1.5f/8,height*2.5f/8)) }
        compose.mainClock.advanceTimeBy(64)
        compose.runOnIdle { assertEquals(1,taps); assertEquals(0,fired); assertEquals(cells.first(),aim.value!!.cells.single()); aim.value=ForgeAim(cells) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithContentDescription("Elegir destino de la forja").performClick()
        compose.runOnIdle { assertTrue(aim.value!!.choosingDestination); aim.value=aim.value!!.copy(destination=output) }
        compose.mainClock.advanceTimeBy(64)
        compose.onNodeWithTag("controls").assertWidthIsEqualTo(196.dp).assertHeightIsEqualTo(72.dp)
        compose.onNodeWithContentDescription("Forjar un Teika").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1,fired); show.value=false }; compose.mainClock.advanceTimeBy(1500)
    }
    @Test fun cancelled_pointer_never_selects_and_cancel_remains_available() {
        var selected=0; var cancelled=0
        compose.setContent { Box(Modifier.size(320.dp,408.dp)) {
            ForgeAimLayer(ForgeAim(),null,100f,{ selected++ },Modifier.size(320.dp).testTag("aim"))
            ForgeControls(true,ForgeAim(),true,false,{}, { cancelled++ },{},{},Modifier.align(Alignment.BottomCenter))
        } }
        compose.onNodeWithTag("aim").performTouchInput { down(center); cancel() }
        compose.onNodeWithContentDescription("Cancelar forja").performClick()
        compose.runOnIdle { assertEquals(0,selected); assertEquals(1,cancelled) }
    }
    private fun scene(name: String, at: Long=32, preview: Boolean=false, blocked: Boolean=false, reduced: Boolean=false, animations: Boolean=true, destination: Position=output) {
        val output=destination
        val s=initial().let { if(blocked) it.copy(grid=it.grid+(output to CellContent.Wood(armor=1,skipSpawnAnimation=true))) else it }
        val state=mutableStateOf(s); val show=mutableStateOf(true)
        val aim=mutableStateOf<ForgeAim?>(null); val consumed=mutableMapOf<String,Int>()
        val bodies=MutableStateFlow(emptyList<TeikaBody>())
        val pulse=GameEvent.RulePulse("forge",cells,points=listOf(output),wood=cells.map { it to (s.grid[it] as CellContent.Wood) },
            bodyIds=listOf("forged"),hitTimes=cells.indices.map { if(reduced) 0 else 240+it*70 },durationMs=if(reduced) 120 else ForgeRules.DURATION_MS)
        compose.mainClock.autoAdvance=false
        compose.setContent { if(show.value) TeikaFusionTheme { CompositionLocalProvider(LocalReducedFx provides reduced,LocalAnimationsActive provides animations) {
            Box(Modifier.size(320.dp,408.dp).background(Color(0xFF101A32))) {
                GameBoard(state.value,{ _,_ -> },cellSize=40.dp,teikaBodies=bodies,
                    forgeAim=aim.value,forgePlan=if(blocked || aim.value==null) null else ForgePlan(cells,output),
                    onEventConsumed={ id -> consumed[id]=(consumed[id] ?: 0)+1; state.value=state.value.copy(events=state.value.events.filterNot { it.id==id }) })
                ForgeControls(state.value.forgeCharged,aim.value,!state.value.forgePending,preview && !blocked,{},{},{},{},Modifier.align(Alignment.BottomCenter))
            }
        } } }
        compose.mainClock.advanceTimeBy(1000)
        compose.runOnIdle {
            if(preview) aim.value=ForgeAim(cells,true,output,if(blocked) "Destino bloqueado" else "Listo para forjar")
            else {
                bodies.value=listOf(TeikaBody("forged",TeikaType.TEIKA_1,output.x+.5f,output.y+.5f))
                state.value=s.copy(grid=s.grid.mapValues { (p,c) -> if(p in cells) CellContent.Empty else c },events=listOf(pulse),forgeCharged=false,forgePending=true)
            }
        }
        compose.mainClock.advanceTimeBy(at)
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png")
        if(!preview) { compose.mainClock.advanceTimeBy(1500); compose.runOnIdle { assertEquals(mapOf(pulse.id to 1),consumed) } }
        compose.runOnIdle { show.value=false }; compose.mainClock.advanceTimeBy(1500)
    }
    @Test fun exact_materials_and_full_output_preview()=scene("forja_previa",preview=true)
    @Test fun blocked_output_preserves_short_feedback_and_fixed_panel()=scene("forja_bloqueada",preview=true,blocked=true)
    @Test fun wood_arrives_in_staggered_heavy_flights()=scene("forja_materiales",at=350)
    @Test fun crucible_heats_and_compresses_material()=scene("forja_calor",at=680)
    @Test fun real_body_reveal_and_acquisition_ring()=scene("forja_nacimiento",at=900)
    @Test fun reduced_effect_retains_real_body_and_static_confirmation()=scene("forja_reducida",at=64,reduced=true)
    @Test fun animations_off_retains_same_body_and_consumes_event_once()=scene("forja_sin_animaciones",at=64,animations=false)
    @Test fun edge_output_keeps_crucible_and_sparks_inside_board()=scene("forja_borde",at=740,destination=Position(7,7))
}
