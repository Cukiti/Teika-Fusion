package com.cukiti.teikafusion.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
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
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=[35],qualifiers="w411dp-h891dp-xxhdpi")
class PremiumRulesScreenshotTest {
    @get:Rule val compose=createComposeRule()
    private fun emptyGrid(): Map<Position,CellContent> = (0..7).flatMap { y -> (0..7).map { x -> Position(x,y) to CellContent.Empty } }.toMap()
    private fun render(rule: HouseRule,state: GameState,event: GameEvent.RulePulse,at: Long,name: String,animations: Boolean=true,drag: DragState?=null) {
        var consumed=0
        val show=androidx.compose.runtime.mutableStateOf(rule!=HouseRule.TRAMPOLINE)
        val bodyFlow=kotlinx.coroutines.flow.MutableStateFlow(state.clawTrip?.let { trip ->
            val (x,y)=ClawRules.position(trip)
            listOf(com.cukiti.teikafusion.viewmodel.TeikaBody(trip.bodyId,TeikaType.TEIKA_1,x,y,kinematic=true))
        } ?: state.teikaBodySaves.map { com.cukiti.teikafusion.viewmodel.TeikaBody(it.id,it.type,it.x,it.y) })
        compose.mainClock.autoAdvance=false
        compose.setContent {
            TeikaFusionTheme {
                Column(Modifier.fillMaxSize().background(Color(0xFF101A32)).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
                    Text(rule.badge,color=Color.White)
                    Box(Modifier.size(320.dp)) {
                        GameBoard(state.copy(level=0,events=if(show.value) state.events else emptyList()),onBoardPositioned={ _,_ -> },cellSize=40.dp,
                            teikaBodies=bodyFlow)
                        ReplacementRuleLayer(state,40.dp,if(show.value) listOf(event) else emptyList(),animations,{ consumed++ },bodyFlow)
                        if(rule==HouseRule.TRAMPOLINE && show.value) PinballLayer(state,bodyFlow,
                            with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() },animations,
                            { false },{}, { _,_,_ -> },{},Modifier.fillMaxSize(),state.events.filterIsInstance<GameEvent.PinballImpact>())
                        if(drag!=null) RelayDragPreview(state,drag,with(androidx.compose.ui.platform.LocalDensity.current) { 40.dp.toPx() },emptySet(),Modifier.fillMaxSize())
                    }
                }
            }
        }
        if(rule==HouseRule.TRAMPOLINE) { compose.mainClock.advanceTimeBy(1000); compose.runOnIdle { show.value=true } }
        compose.mainClock.advanceTimeBy(at)
        compose.onRoot().captureRoboImage("src/test/screenshots/premium_$name.png")
        compose.mainClock.advanceTimeBy(2500)
        compose.runOnIdle { assertEquals(1,consumed) }
    }
    @Test fun constellation_cut_and_shutdown() {
        val points=listOf(Position(0,1),Position(6,3),Position(2,6))
        val trace=ConstellationRules.trace(points)
        val woods=trace.distinct().mapIndexed { i,p -> p to CellContent.Wood(colorType=i%4,targetColor=if(i%4==0) TargetColor.RED else null) }
        val event=GameEvent.RulePulse("constellation",trace,points=points,wood=woods,hitTimes=trace.indices.map { RuleMotion.constellationHit(it,trace.size) },durationMs=RuleMotion.CONSTELLATION_MS)
        render(HouseRule.CONSTELLATION,GameState(level=HouseRule.levelsWith(HouseRule.CONSTELLATION).first(),grid=emptyGrid(),constellationPoints=listOf(Position(1,0),Position(3,0),Position(5,0))),event,400,"constelacion")
    }
    private fun pinballState(charged: Boolean,flight: Boolean=false,impact: Boolean=false)=GameState(
        level=HouseRule.levelsWith(HouseRule.TRAMPOLINE).first(),grid=emptyGrid()+(Position(5,3) to CellContent.Wood(armor=1)),
        status=GameStatus.PLAYING,pinballCharged=charged,
        teikaBodySaves=listOf(TeikaBodySave("pinball",TeikaType.TEIKA_1,3.5f,3.5f),TeikaBodySave("other",TeikaType.TEIKA_2,2f,6f)),
        pinballFlight=if(flight) PinballFlight("pinball",-10f,0f,5,2.6f) else null,
        events=if(impact) listOf(GameEvent.PinballImpact(3.5f,3.5f,"pinball")) else emptyList())
    @Test fun pinball_charge_marks_available_real_bodies() {
        render(HouseRule.TRAMPOLINE,pinballState(true),GameEvent.RulePulse("pinball_charge",emptyList(),points=(0..7).map { Position(it,2) },durationMs=420),200,"pinball_carga")
    }
    @Test fun pinball_real_impact_ring_and_rebound_budget() {
        render(HouseRule.TRAMPOLINE,pinballState(false,true,true),GameEvent.RulePulse("pinball_launch",emptyList(),durationMs=300),100,"pinball_impacto")
    }
    @Test fun pinball_reduced_impact_retains_readability() {
        render(HouseRule.TRAMPOLINE,pinballState(false,true,true),GameEvent.RulePulse("pinball_launch",emptyList(),durationMs=120),50,"pinball_reducido",false)
    }
    @Test fun avalanche_charged_supports_and_falling_wood() {
        val wood=CellContent.Wood(colorType=3,targetColor=TargetColor.BLUE)
        val state=GameState(level=HouseRule.levelsWith(HouseRule.AVALANCHE).first(),grid=emptyGrid()+(Position(0,7) to wood)+(Position(3,1) to CellContent.Wood(colorType=1)),hiddenWoodCells=setOf(Position(0,7)),avalancheCharge=0,
            events=listOf(GameEvent.WoodFall(from=Position(0,0),to=Position(0,7),colorType=3,wood=wood,targetColor=TargetColor.BLUE,durationMs=570,delayMs=70)))
        render(HouseRule.AVALANCHE,state,GameEvent.RulePulse("avalanche",listOf(Position(0,0)),durationMs=720),300,"avalancha")
    }
    @Test fun avalanche_support_charge_before_collapse() {
        val state=GameState(level=HouseRule.levelsWith(HouseRule.AVALANCHE).first(),grid=emptyGrid()+(Position(1,2) to CellContent.Wood(colorType=1))+(Position(3,3) to CellContent.Wood(colorType=2)),avalancheCharge=1)
        render(HouseRule.AVALANCHE,state,GameEvent.RulePulse("avalanche_charge",(0..7).map { Position(it,7) },durationMs=360),250,"apoyos")
    }
    @Test fun cut_laser_and_visible_reserve() {
        val original=Piece(shape=listOf(Position(0,0),Position(1,0),Position(0,1),Position(1,1)),targetMask=listOf(TargetColor.RED,null,null,TargetColor.BLUE))
        val (active,reserve)=requireNotNull(PrecisionCut.split(original,1))
        val event=GameEvent.RulePulse("cut",emptyList(),durationMs=RuleMotion.CUT_MS,originalPiece=original,activePieceId=active.id,separator=PrecisionCut.separator(original,1))
        compose.mainClock.autoAdvance=false
        compose.setContent { TeikaFusionTheme {
            Column(Modifier.fillMaxSize().background(Color(0xFF101A32)).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Text("Corte preciso",color=Color.White)
                Box(Modifier.size(160.dp),contentAlignment=Alignment.Center) { PrecisionCutReveal(event,active,40.dp,Modifier.matchParentSize()) }
                PrecisionCutControls(false,reserve,{},{})
            }
        } }
        compose.mainClock.advanceTimeBy(200)
        compose.onRoot().captureRoboImage("src/test/screenshots/premium_corte.png")
    }
    @Test fun surf_wave_and_real_passenger() {
        val state=GameState(level=HouseRule.levelsWith(HouseRule.SURF).first(),grid=emptyGrid(),surfWave=1,
            teikaBodySaves=listOf(TeikaBodySave("surfer",TeikaType.TEIKA_1,3.5f,4.5f)))
        render(HouseRule.SURF,state,GameEvent.RulePulse("surf",(0..7).map { Position(it,5) },direction=1,bodyIds=listOf("surfer"),durationMs=1100),480,"surf_ola")
    }
    @Test fun surf_reduced() {
        val state=GameState(level=HouseRule.levelsWith(HouseRule.SURF).first(),grid=emptyGrid())
        render(HouseRule.SURF,state,GameEvent.RulePulse("surf",(0..7).map { Position(it,5) },direction=1,durationMs=120),50,"surf_reducido",false)
    }
    private fun relayDischargeSnapshot(at: Long,name: String) {
        val footprint=(2..4).map { Position(1,it) }.toSet()
        val shield=Position(5,3)
        val targets=(2..4).flatMap { y -> (3..7).map { Position(it,y) } }.filter { it!=shield }
        val board=emptyGrid()+targets.associateWith { CellContent.Wood(colorType=1,targetColor=TargetColor.RED,skipSpawnAnimation=true) }+
            (shield to CellContent.Wood(armor=1,skipSpawnAnimation=true))
        val shot=RelayRules.plan(footprint,board,emptySet())
        val cells=shot.targets.map { it.cell }
        val wood=cells.map { it to (board.getValue(it) as CellContent.Wood) }
        val state=GameState(level=HouseRule.levelsWith(HouseRule.RELAY).first(),grid=board+cells.associateWith { CellContent.Empty }+
            footprint.associateWith { CellContent.Wood(colorType=2,skipSpawnAnimation=true) })
        render(HouseRule.RELAY,state,GameEvent.RulePulse("relay",cells,points=footprint.toList(),wood=wood,
            hitTimes=cells.map { RelayRules.hit(shot,it) },durationMs=RelayRules.DURATION_MS,direction=shot.direction,
            moves=shot.paths.filter { it.size>1 }.map { it.first() to it.last() }),at,name)
    }
    @Test fun relay_contour_sweeps_star_without_touching_crystal() = relayDischargeSnapshot(390,"relevo_barrido")
    @Test fun relay_discharge_expels_blocks_downwind() = relayDischargeSnapshot(590,"relevo_expulsion")
    @Test fun relay_discharge_compresses_before_firing() = relayDischargeSnapshot(150,"relevo_preparacion")
    @Test fun relay_preview_remains_visible_when_placement_also_clears_a_line() {
        val line=(0..6).associate { Position(it,5) to CellContent.Wood(colorType=1,skipSpawnAnimation=true) }
        val targets=(1..4).associate { Position(7,it) to CellContent.Wood(colorType=2,skipSpawnAnimation=true) }
        val state=GameState(level=HouseRule.levelsWith(HouseRule.RELAY).first(),grid=emptyGrid()+line+targets,relayCharged=true)
        val drag=DragState.Dragging(Piece(shape=listOf(Position(0,0))),Position(7,5),true,clearRowSpans=listOf(5 to (0..7)))
        render(HouseRule.RELAY,state,GameEvent.RulePulse("relay_charge",emptyList(),durationMs=520),50,"relevo_preview_linea",drag=drag)
    }
    @Test fun relay_reduced_protection_and_footprint() {
        val footprint=listOf(Position(3,3),Position(4,3))
        val state=GameState(level=HouseRule.levelsWith(HouseRule.RELAY).first(),grid=emptyGrid()+footprint.associateWith { CellContent.Wood(colorType=2,skipSpawnAnimation=true) }+(Position(4,4) to CellContent.Wood(armor=1,skipSpawnAnimation=true)))
        render(HouseRule.RELAY,state,GameEvent.RulePulse("relay",listOf(Position(3,2)),points=footprint,durationMs=120),50,"relevo_reducido",false)
    }
    @Test fun relay_charge_is_a_fixed_tray_overlay() {
        val piece=Piece(shape=listOf(Position(0,0),Position(1,0),Position(0,1)),targetMask=listOf(TargetColor.RED,null,null))
        compose.mainClock.autoAdvance=false
        compose.setContent { TeikaFusionTheme {
            Column(Modifier.fillMaxSize().background(Color(0xFF101A32)).padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Text("RELEVO · PRÓXIMA FICHA",color=Color.White)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    repeat(3) { i -> Box(Modifier.size(100.dp).background(Color(0xFF263653)),contentAlignment=Alignment.Center) {
                        PieceView(piece.copy(colorType=i),false,cellSize=26.dp)
                        RelayTrayGlow(Modifier.matchParentSize())
                    } }
                }
            }
        } }
        compose.mainClock.advanceTimeBy(400)
        compose.onRoot().captureRoboImage("src/test/screenshots/premium_relevo_bandeja.png")
    }
    @Test fun reserve_fixed_tray_with_distinct_stored_piece() {
        val piece=Piece(shape=listOf(Position(0,0),Position(1,0),Position(0,1)),targetMask=listOf(TargetColor.RED,null,null))
        compose.mainClock.autoAdvance=false
        compose.setContent { TeikaFusionTheme {
            Column(Modifier.fillMaxSize().background(Color(0xFF101A32)).padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Text("RESERVA · UN CAMBIO POR BANDEJA",color=Color.White)
                Row(Modifier.width(360.dp).height(112.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    repeat(3) { index -> Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF243651)),contentAlignment=Alignment.Center) {
                        PieceView(piece.copy(colorType=index),false,cellSize=21.dp)
                        ReserveExchangeButton(true,{},Modifier.align(Alignment.BottomEnd).padding(3.dp))
                    } }
                    ReserveSlot(piece.copy(shape=listOf(Position(0,0),Position(1,0),Position(2,0),Position(2,1)),targetMask=emptyList(),colorType=3),true,64.dp,Modifier.weight(.7f))
                }
            }
        } }
        compose.mainClock.advanceTimeBy(400)
        compose.onRoot().captureRoboImage("src/test/screenshots/premium_reserva_bandeja.png")
    }

}
