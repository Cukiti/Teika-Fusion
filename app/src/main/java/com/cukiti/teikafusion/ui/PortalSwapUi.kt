package com.cukiti.teikafusion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.*
import kotlin.math.floor
import kotlin.math.roundToInt

private val portalBlue=Color(0xFF6EE5FF)
private val portalPurple=Color(0xFFD0A0FF)

@Composable
fun PortalSwapControls(charged: Boolean, aim: PortalSwapAim?, enabled: Boolean, confirmEnabled: Boolean,
    start: ()->Unit, cancel: ()->Unit, confirm: ()->Unit, modifier: Modifier=Modifier) {
    BackHandler(enabled=aim!=null,onBack=cancel)
    Column(modifier.width(196.dp).height(72.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
        if(aim!=null) Text(aim.feedback,color=Color.White,fontSize=11.sp,maxLines=1,
            modifier=Modifier.background(Color(0xDD111A2E),RoundedCornerShape(8.dp)).padding(horizontal=7.dp,vertical=3.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            val active=if(aim==null) enabled else confirmEnabled
            val color=if(charged) portalBlue else Color(0xFF8792A7)
            Column(Modifier.size(if(aim==null) 72.dp else 108.dp,48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription=if(aim!=null) "Confirmar intercambio de zonas" else if(charged) "Portal doble cargado" else "Portal doble: limpia una línea para cargar" }
                .clickable(enabled=active,role=Role.Button,onClick=if(aim==null) start else confirm),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Canvas(Modifier.size(34.dp,23.dp)) {
                    drawOval(color.copy(alpha=if(active) 1f else .4f),Offset(0f,size.height*.08f),Size(size.width*.35f,size.height*.84f),style=Stroke(2.dp.toPx()))
                    drawOval(portalPurple.copy(alpha=if(active) 1f else .4f),Offset(size.width*.65f,size.height*.08f),Size(size.width*.35f,size.height*.84f),style=Stroke(2.dp.toPx()))
                    drawLine(color,Offset(size.width*.37f,size.height*.5f),Offset(size.width*.63f,size.height*.5f),1.5.dp.toPx())
                }
                Text(if(aim==null) "PORTAL" else "INTERCAMBIAR",color=color.copy(alpha=if(active) 1f else .55f),fontSize=9.sp,maxLines=1)
            }
            if(aim!=null) Box(Modifier.size(48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription="Cancelar portal doble" }.clickable(role=Role.Button,onClick=cancel),contentAlignment=Alignment.Center) {
                Text("×",fontSize=28.sp,color=Color.White)
            }
        }
    }
}

/** Exact post-swap contents. Selection only; no event or reward ownership. */
@Composable
fun PortalSwapAimLayer(state: GameState, aim: PortalSwapAim?, plan: PortalSwapPlan?, cellSize: androidx.compose.ui.unit.Dp,
    step: Float, select: (Position)->Unit, modifier: Modifier=Modifier) {
    val selectNow by rememberUpdatedState(select)
    if(aim==null) return
    val seedGeometry=remember(step) { SeedGeometry(step) }
    Box(modifier.pointerInput(step) { detectTapGestures { p ->
        if(p.x>=0f && p.y>=0f && p.x<size.width && p.y<size.height)
            selectNow(Position(floor(p.x/step).toInt(),floor(p.y/step).toInt()))
    } }) {
        Canvas(Modifier.matchParentSize()) { drawRect(Color(0xFF070D1B).copy(alpha=.30f)) }
        val preview=plan?.let { PortalSwapRules.apply(state.grid,it) }
        preview?.let { grid -> plan.cells.forEach { p -> key(p) {
            val w=grid[p] as? CellContent.Wood
            Box(Modifier.offset { IntOffset((p.x*step).roundToInt(),(p.y*step).roundToInt()) }.size(cellSize).background(Color(0xFF182239))) {
                if(w!=null) CellView(w.copy(skipSpawnAnimation=true),Modifier.fillMaxSize(),shouldAnimate=false,breathe=false)
                if(w?.teikaSeed==true) Canvas(Modifier.matchParentSize()) {
                    translate(step/2f,step/2f) { drawSeed(seedGeometry,step,0f,live=false) }
                }
            }
        } } }
        Canvas(Modifier.matchParentSize()) {
            listOfNotNull(aim.first?.let { it to portalBlue },aim.second?.let { it to portalPurple }).forEach { (a,color) ->
                drawRoundRect(color.copy(alpha=.12f),Offset(a.x*step,a.y*step),Size(step*2,step*2))
                drawRoundRect(color,Offset(a.x*step+step*.025f,a.y*step+step*.025f),Size(step*1.95f,step*1.95f),style=Stroke(step*.045f))
                // The corner indicator makes the anchor of each 2×2 footprint explicit.
                drawCircle(color,step*.07f,Offset((a.x+.12f)*step,(a.y+.12f)*step))
            }
        }
    }
}
