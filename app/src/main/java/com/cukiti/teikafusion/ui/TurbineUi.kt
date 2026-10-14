package com.cukiti.teikafusion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.cukiti.teikafusion.model.*
import com.cukiti.teikafusion.viewmodel.TeikaBody
import kotlin.math.*
import kotlinx.coroutines.flow.StateFlow

private val windCyan=Color(0xFF9AF6FF)

@Composable
fun TurbineControls(charged: Boolean,aim: TurbineAim?,gust: TurbineGust?,enabled: Boolean,confirmEnabled: Boolean,
    start: ()->Unit,cancel: ()->Unit,confirm: ()->Unit,modifier: Modifier=Modifier) {
    BackHandler(enabled=aim!=null,onBack=cancel)
    Column(modifier.width(196.dp).height(72.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
        val feedback=aim?.feedback ?: if(gust!=null) { if(gust.elapsedMs>=TurbineRules.DURATION_MS) "Resolviendo" else "Corriente activa" } else null
        if(feedback!=null) Text(feedback,color=Color.White,fontSize=11.sp,maxLines=1,
            modifier=Modifier.background(Color(0xDD111A2E),RoundedCornerShape(8.dp)).padding(horizontal=7.dp,vertical=3.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            val active=if(aim==null) enabled else confirmEnabled
            Column(Modifier.size(88.dp,48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription=if(aim!=null) "Confirmar ráfaga" else if(charged) "Turbina cargada" else "Turbina: limpia una línea para cargar" }
                .clickable(enabled=active,role=Role.Button,onClick=if(aim==null) start else confirm),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Canvas(Modifier.size(28.dp,23.dp)) {
                    val color=if(charged || gust!=null) windCyan else Color(0xFF8792A7)
                    drawCircle(color,size.height*.4f,center,style=Stroke(1.8.dp.toPx()))
                    repeat(3) { i -> rotate(i*120f,pivot=center) {
                        drawOval(color.copy(alpha=.7f),center+Offset(-size.height*.06f,-size.height*.34f),Size(size.height*.16f,size.height*.28f))
                    } }
                    drawCircle(Color(0xFF182239),size.height*.095f,center)
                    gust?.let { drawArc(windCyan,-90f,360f*(1-it.elapsedMs/TurbineRules.DURATION_MS).coerceIn(0f,1f),false,center-Offset(size.height*.48f,size.height*.48f),Size(size.height*.96f,size.height*.96f),style=Stroke(2.dp.toPx())) }
                }
                Text(if(aim==null) "TURBINA" else "SOPLAR",color=windCyan.copy(alpha=if(active || gust!=null) 1f else .5f),fontSize=10.sp,maxLines=1)
            }
            if(aim!=null) Box(Modifier.size(48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription="Cancelar turbina" }.clickable(role=Role.Button,onClick=cancel),contentAlignment=Alignment.Center) {
                Text("×",fontSize=28.sp,color=Color.White)
            }
        }
    }
}

/** Physics and drawing share the ray limits. Animation uses the saved simulation clock, never wall time. */
@Composable
fun TurbineLayer(state: GameState,aim: TurbineAim?,bodiesFlow: StateFlow<List<TeikaBody>>,step: Float,reduced: Boolean,
    anchor: (Float,Float)->Unit,direction: (Float,Float)->Unit,modifier: Modifier=Modifier) {
    val gust=state.turbineGust
    if(aim==null && gust==null) return
    val bodies by bodiesFlow.collectAsState()
    val anchorNow by rememberUpdatedState(anchor); val directionNow by rememberUpdatedState(direction)
    val beam=aim?.beam ?: gust?.beam
    val obstacles=remember(state.grid,state.unplaceableCells,state.frozenCells,state.glacierFrostCells,state.starAstroTargetCells,
        state.starAstroMoonWoodCells,state.hiddenWoodCells,state.gluttonDeadCells,state.lavaActive,state.lavaRow) { TurbineRules.blocked(state) }
    val rays=remember(beam,obstacles,state.ninjaCutActive,state.gridW,state.gridH) {
        beam?.let { b -> (0..30).map { i ->
            val a=atan2(b.dy,b.dx)+(i/30f-.5f)*PI.toFloat()/3
            val dx=cos(a); val dy=sin(a)
            Triple(dx,dy,TurbineRules.rayLength(b,dx,dy,obstacles,state.gridW,state.gridH,state.ninjaCutActive))
        } }.orEmpty()
    }
    val hit=bodies.filter { !it.kinematic && it.frozenTurns==0 && it.bubble==0 && beam!=null &&
        TurbineRules.reaches(beam,it.x,it.y,obstacles,state.gridW,state.gridH,state.ninjaCutActive) }
    val input=if(aim!=null) Modifier.pointerInput(step) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false); down.consume()
            anchorNow(down.position.x/step,down.position.y/step)
            do {
                val event=awaitPointerEvent(); event.changes.forEach { it.consume() }
                event.changes.firstOrNull { it.id==down.id && it.pressed }?.let { directionNow(it.position.x/step,it.position.y/step) }
            } while(event.changes.any { it.pressed })
        }
    } else Modifier
    Canvas(modifier.then(input)) {
        if(aim!=null) drawRect(Color(0xFF070D1B).copy(alpha=.20f))
        if(beam==null) {
            drawRoundRect(windCyan.copy(alpha=.5f),Offset(step*.02f,step*.02f),Size(size.width-step*.04f,size.height-step*.04f),style=Stroke(step*.035f))
            return@Canvas
        }
        val origin=Offset(beam.x*step,beam.y*step)
        val color=if(aim!=null && hit.isEmpty()) Color(0xFFFF8A91) else windCyan
        val power=if(gust==null) .7f else TurbineRules.envelope(gust.elapsedMs)
        val sector=Path().apply {
            moveTo(origin.x,origin.y)
            rays.forEach { (dx,dy,length) -> lineTo(origin.x+dx*length*step,origin.y+dy*length*step) }
            close()
        }
        drawPath(sector,color.copy(alpha=if(aim!=null) .10f else .07f*power))
        rays.forEachIndexed { i,(dx,dy,length) ->
            val dir=Offset(dx,dy)
            if(i==0 || i==30 || (aim!=null && i==15)) drawLine(color.copy(alpha=.45f),origin,origin+dir*length*step,step*.018f)
            if(gust!=null && i%3==0 && length>.1f) repeat(if(reduced) 1 else 3) { n ->
                val travel=if(reduced) length*.55f else ((gust.elapsedMs/480f+i*.071f+n/3f)%1f)*length
                val tail=(travel-.35f).coerceAtLeast(0f)
                drawLine(color.copy(alpha=power*(if(reduced) .25f else .5f)),origin+dir*tail*step,origin+dir*travel*step,step*.022f,StrokeCap.Round)
            }
        }
        hit.forEach { b ->
            val at=Offset(b.x*step,b.y*step)
            drawCircle(color.copy(alpha=if(aim!=null) .8f else .32f*power),(b.radius+.07f)*step,at,style=Stroke(step*.026f))
            if(gust!=null && !reduced) {
                val d=Offset(beam.dx,beam.dy)
                repeat(2) { n -> drawLine(color.copy(alpha=.25f*power),at-d*step*(b.radius+.1f+n*.17f),at-d*step*(b.radius+.3f+n*.17f),step*.025f,StrokeCap.Round) }
            }
        }
        // Rigid housing, counter-rotating light ring, three balanced blades. No screen shake loop.
        val inward=TurbineRules.anchor(beam.x,beam.y,state.gridW,state.gridH)
        val hub=origin+Offset(inward.dx,inward.dy)*step*.42f
        drawCircle(Color.Black.copy(alpha=.25f),step*.38f,hub+Offset(0f,step*.025f))
        drawCircle(Brush.radialGradient(listOf(Color(0xFF486980),Color(0xFF08111F)),hub-Offset(step*.08f,step*.08f),step*.44f),step*.32f,hub)
        drawCircle(Color(0xFF455B72),step*.28f,hub,style=Stroke(step*.06f))
        drawCircle(color.copy(alpha=.7f),step*.31f,hub,style=Stroke(step*.025f))
        val spin=if(gust==null || reduced) 0f else gust.elapsedMs*.9f
        repeat(3) { n -> rotate(spin+n*120f,pivot=hub) {
            drawOval(color.copy(alpha=.8f),hub+Offset(-step*.07f,-step*.23f),Size(step*.14f,step*.19f))
        } }
        drawCircle(Color(0xFF8195AA),step*.075f,hub)
        gust?.let { drawArc(color,-90f,360f*(1-it.elapsedMs/TurbineRules.DURATION_MS).coerceIn(0f,1f),false,hub-Offset(step*.37f,step*.37f),Size(step*.74f,step*.74f),style=Stroke(step*.035f)) }
    }
}
