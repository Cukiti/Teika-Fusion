package com.cukiti.teikafusion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.*
import kotlin.math.*

private val clawMint=Color(0xFF7FFFE0)
@Composable
fun ClawControls(charged: Boolean, aim: ClawAim?, enabled: Boolean, confirmEnabled: Boolean,
    start: ()->Unit, cancel: ()->Unit, confirm: ()->Unit, modifier: Modifier=Modifier, transporting: Boolean=false) {
    BackHandler(enabled=aim!=null,onBack=cancel)
    Column(modifier.width(196.dp).height(72.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
        if(aim!=null || !charged) Text(aim?.feedback ?: if(transporting) "Trasladando" else "Limpia una línea",color=Color.White,fontSize=11.sp,maxLines=1,
            modifier=Modifier.background(Color(0xDD111A2E),RoundedCornerShape(8.dp)).padding(horizontal=7.dp,vertical=3.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            val active=charged && if(aim==null) enabled else confirmEnabled
            val color=if(active) clawMint else Color(0xFF8792A7)
            Column(Modifier.size(if(aim==null) 72.dp else 108.dp,48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription=if(transporting) "Garra trasladando Teika" else if(aim!=null) "Soltar Teika con la garra" else if(charged) "Garra Teika cargada" else "Garra: limpia una línea para cargar" }
                .clickable(enabled=active,role=Role.Button,onClick=if(aim==null) start else confirm),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Canvas(Modifier.size(34.dp,23.dp)) {
                    val c=Offset(size.width/2,size.height*.4f)
                    drawLine(color,Offset(c.x,0f),c,2.dp.toPx())
                    for(sign in listOf(-1,1)) {
                        val elbow=c+Offset(sign*8.dp.toPx(),4.dp.toPx()); val tip=c+Offset(sign*4.dp.toPx(),10.dp.toPx())
                        drawLine(color,c,elbow,2.dp.toPx(),StrokeCap.Round); drawLine(color,elbow,tip,2.dp.toPx(),StrokeCap.Round)
                    }
                }
                Text(if(aim==null) "GARRA" else "SOLTAR",color=color,fontSize=9.sp,maxLines=1)
            }
            if(aim!=null) Box(Modifier.size(48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription="Cancelar garra" }.clickable(role=Role.Button,onClick=cancel),contentAlignment=Alignment.Center) {
                Text("×",fontSize=28.sp,color=Color.White)
            }
        }
    }
}

@Composable
fun ClawLayer(aim: ClawAim?, plan: ClawTrip?, trip: ClawTrip?, step: Float,
    select: (Float,Float)->Unit, target: (Float,Float)->Unit, modifier: Modifier=Modifier) {
    val selectNow by rememberUpdatedState(select); val targetNow by rememberUpdatedState(target)
    if(aim==null && trip==null) return
    val reduced=LocalReducedFx.current
    Canvas(if(aim==null) modifier else modifier.pointerInput(step) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            selectNow(down.position.x/step,down.position.y/step); down.consume()
            val complete=drag(down.id) { change -> change.consume(); targetNow(change.position.x/step,change.position.y/step) }
            if(!complete) targetNow(Float.NaN,Float.NaN)
        }
    }) {
        drawRect(Color(0xFF060F20).copy(alpha=if(aim!=null) .28f else .12f))
        val preview=plan
        if(aim?.fromX!=null && aim.fromY!=null) {
            drawCircle(clawMint,aim.radius*step,Offset(aim.fromX*step,aim.fromY*step),style=Stroke(step*.04f))
        }
        if(aim!=null && aim.x!=null && aim.y!=null) {
            val c=Offset(aim.x*step,aim.y*step); val color=if(preview!=null) clawMint else Color(0xFFFF7F93)
            val r=aim.radius*step
            drawCircle(color.copy(alpha=.15f),r,c); drawCircle(color,r,c,style=Stroke(step*.035f))
            drawLine(color,c-Offset(step*.18f,0f),c+Offset(step*.18f,0f),step*.025f)
            drawLine(color,c-Offset(0f,step*.18f),c+Offset(0f,step*.18f),step*.025f)
        }
        if(aim!=null) preview?.let {
            val from=Offset(it.fromX*step,it.fromY*step); val to=Offset(it.toX*step,it.toY*step)
            drawCircle(clawMint,it.radius*step,from,style=Stroke(step*.04f))
            drawLine(clawMint.copy(alpha=.45f),from,to,step*.025f)
        }
        trip?.let { t ->
            val (x,y)=ClawRules.position(t); val c=Offset(x*step,y*step); val r=t.radius*step
            if(reduced) { drawCircle(clawMint,r*1.1f,c,style=Stroke(step*.035f)); return@let }
            val close=(t.progress/.16f).coerceIn(0f,1f)*(1-((t.progress-.8f)/.12f).coerceIn(0f,1f))
            val sway=sin(t.progress*PI.toFloat()*4)*step*.08f*if(t.progress in .25f.. .8f) 1f else 0f
            val head=Offset(c.x+sway,(c.y-r-step*.26f).coerceAtLeast(step*.12f))
            // A suspended load: cable, metal hub, closing jaws and a projected landing shadow.
            val shadow=Offset(t.toX*step,t.toY*step)
            drawOval(Color.Black.copy(alpha=.24f),shadow-Offset(r,r*.22f),Size(r*2,r*.44f))
            drawCircle(Brush.radialGradient(listOf(clawMint.copy(alpha=.2f),Color.Transparent),head,step*.8f),step*.8f,head)
            drawLine(Color(0xFF344E68),Offset(head.x,0f),head,step*.065f)
            drawLine(clawMint.copy(alpha=.7f),Offset(head.x-step*.015f,0f),head-Offset(step*.015f,0f),step*.018f)
            drawRoundRect(Color(0xFF263D54),head-Offset(step*.22f,step*.12f),Size(step*.44f,step*.24f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(step*.06f))
            drawCircle(clawMint,step*.045f,head)
            for(sign in listOf(-1,1)) {
                val elbow=c+Offset(sign*(r+step*(.25f-.15f*close)), -r*.3f)
                val tip=c+Offset(sign*(r*(1.18f-.24f*close)),r*.32f)
                drawLine(Color(0xFF9BB9CE),head,elbow,step*.07f,StrokeCap.Round)
                drawLine(clawMint,elbow,tip,step*.045f,StrokeCap.Round)
                drawCircle(Color(0xFF17283E),step*.055f,elbow)
                drawLine(clawMint,tip,tip+Offset(-sign*step*.1f,step*.05f),step*.04f,StrokeCap.Round)
            }
        }
    }
}
