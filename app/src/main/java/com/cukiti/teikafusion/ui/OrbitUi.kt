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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.cukiti.teikafusion.model.*
import kotlin.math.*

private val orbitCyan=Color(0xFF8CEBFF)

@Composable
fun OrbitControls(charged: Boolean,aim: OrbitAim?,enabled: Boolean,confirmEnabled: Boolean,
    start: ()->Unit,cancel: ()->Unit,confirm: ()->Unit,toggle: ()->Unit,modifier: Modifier=Modifier) {
    BackHandler(enabled=aim!=null,onBack=cancel)
    Column(modifier.width(196.dp).height(72.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
        if(aim!=null) Text(aim.feedback,color=Color.White,fontSize=11.sp,maxLines=1,
            modifier=Modifier.background(Color(0xDD111A2E),RoundedCornerShape(8.dp)).padding(horizontal=7.dp,vertical=3.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            if(aim!=null) Box(Modifier.size(48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription=if(aim.clockwise) "Giro a la derecha: cambiar dirección" else "Giro a la izquierda: cambiar dirección" }
                .clickable(role=Role.Button,onClick=toggle),contentAlignment=Alignment.Center) {
                Text(if(aim.clockwise) "↻" else "↺",fontSize=28.sp,color=orbitCyan)
            }
            val active=if(aim==null) enabled else confirmEnabled
            Column(Modifier.size(72.dp,48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription=if(aim!=null) "Confirmar giro orbital" else if(charged) "Giro orbital cargado" else "Giro orbital: limpia una línea para cargar" }
                .clickable(enabled=active,role=Role.Button,onClick=if(aim==null) start else confirm),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Canvas(Modifier.size(28.dp,23.dp)) {
                    val color=if(charged) orbitCyan else Color(0xFF8792A7)
                    drawCircle(color.copy(alpha=if(active) 1f else .4f),size.height*.39f,center,style=Stroke(2.dp.toPx()))
                    drawCircle(color,size.height*.095f,center+Offset(size.height*.39f,0f))
                }
                Text(if(aim==null) "ÓRBITA" else "GIRAR",color=orbitCyan.copy(alpha=if(active) 1f else .5f),fontSize=10.sp,maxLines=1)
            }
            if(aim!=null) Box(Modifier.size(48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription="Cancelar giro orbital" }.clickable(role=Role.Button,onClick=cancel),contentAlignment=Alignment.Center) {
                Text("×",fontSize=28.sp,color=Color.White)
            }
        }
    }
}

/** Preview contains the exact committed cells. Releasing a finger never spends the charge. */
@Composable
fun OrbitAimLayer(state: GameState,aim: OrbitAim?,plan: OrbitPlan?,cellSize: Dp,step: Float,
    select: (Position)->Unit,modifier: Modifier=Modifier) {
    if(aim==null) return
    val selectNow by rememberUpdatedState(select)
    val geo=remember(step) { SeedGeometry(step) }
    Box(modifier.pointerInput(step,state.gridW,state.gridH) {
        fun pick(p: Offset) {
            if(p.x>=0f && p.y>=0f && p.x<size.width && p.y<size.height)
                selectNow(Position(floor(p.x/step).toInt().coerceIn(1,state.gridW-2),floor(p.y/step).toInt().coerceIn(1,state.gridH-2)))
        }
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false); down.consume(); pick(down.position)
            do {
                val event=awaitPointerEvent()
                event.changes.forEach { it.consume() }
                val change=event.changes.firstOrNull { it.id==down.id }
                if(change!=null && change.pressed) pick(change.position)
            } while(event.changes.any { it.pressed })
        }
    }) {
        Canvas(Modifier.matchParentSize()) { drawRect(Color(0xFF070D1B).copy(alpha=.30f)) }
        val preview=plan?.let { OrbitRules.apply(state.grid,it) }
        preview?.let { grid -> plan.cells.forEach { p -> key(p) {
            val w=grid[p] as? CellContent.Wood
            Box(Modifier.offset { IntOffset((p.x*step).roundToInt(),(p.y*step).roundToInt()) }.size(cellSize).background(Color(0xFF182239))) {
                if(w!=null) CellView(w.copy(skipSpawnAnimation=true),Modifier.fillMaxSize(),shouldAnimate=false,breathe=false)
                if(w?.teikaSeed==true) Canvas(Modifier.matchParentSize()) { translate(step/2f,step/2f) { drawSeed(geo,step,0f,live=false) } }
            }
        } } }
        Canvas(Modifier.matchParentSize()) {
            aim.center?.let { c ->
                val color=if(plan!=null) orbitCyan else Color(0xFFFF7E89)
                val center=Offset((c.x+.5f)*step,(c.y+.5f)*step)
                drawRoundRect(color.copy(alpha=.1f),Offset((c.x-1)*step,(c.y-1)*step),Size(step*3,step*3))
                drawRoundRect(color,Offset((c.x-1)*step+step*.025f,(c.y-1)*step+step*.025f),Size(step*2.95f,step*2.95f),style=Stroke(step*.035f))
                drawArc(color,if(aim.clockwise) -80f else -10f,if(aim.clockwise) 70f else -70f,false,
                    center-Offset(step*1.38f,step*1.38f),Size(step*2.76f,step*2.76f),style=Stroke(step*.055f))
                val a=(if(aim.clockwise) -10f else -80f)*PI.toFloat()/180f
                val tip=center+Offset(cos(a),sin(a))*step*1.38f
                drawCircle(color,step*.07f,tip)
            }
        }
    }
}
