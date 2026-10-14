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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.*
import kotlin.math.floor

private val ballGold=Color(0xFFFFCF72)
private val ballHot=Color(0xFFFF885C)

@Composable
fun BallControls(charged: Boolean, aim: BallAim?, enabled: Boolean, confirmEnabled: Boolean,
    start: ()->Unit, cancel: ()->Unit, confirm: ()->Unit, modifier: Modifier=Modifier) {
    BackHandler(enabled=aim!=null,onBack=cancel)
    Column(modifier.width(196.dp).height(72.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
        if(aim!=null || !charged) Text(aim?.feedback ?: "Limpia una línea",color=Color.White,fontSize=11.sp,maxLines=1,
            modifier=Modifier.background(Color(0xDD111A2E),RoundedCornerShape(8.dp)).padding(horizontal=7.dp,vertical=3.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            val active=charged && (if(aim==null) enabled else confirmEnabled)
            val color=if(charged) ballGold else Color(0xFF8792A7)
            Column(Modifier.size(if(aim==null) 72.dp else 108.dp,48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription=if(aim!=null) "Lanzar bola demoledora" else if(charged) "Bola demoledora cargada" else "Bola: limpia una línea para cargar" }
                .clickable(enabled=active,role=Role.Button,onClick=if(aim==null) start else confirm),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Canvas(Modifier.size(34.dp,23.dp)) {
                    val c=Offset(size.width/2,size.height/2)
                    drawLine(color,c-Offset(8.dp.toPx(),9.dp.toPx()),c,2.dp.toPx())
                    drawCircle(color.copy(alpha=if(active) 1f else .4f),size.height*.27f,c+Offset(3.dp.toPx(),2.dp.toPx()))
                    drawCircle(Color.White.copy(alpha=.5f),size.height*.08f,c)

                }
                Text(if(aim==null) "BOLA" else "LANZAR",color=color.copy(alpha=if(active) 1f else .55f),fontSize=9.sp,maxLines=1)
            }
            if(aim!=null) Box(Modifier.size(48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription="Cancelar bola demoledora" }.clickable(role=Role.Button,onClick=cancel),contentAlignment=Alignment.Center) {
                Text("×",fontSize=28.sp,color=Color.White)
            }
        }
    }
}



/** Gesture drafts only select: releasing a pointer never fires the ball. */
@Composable
fun BallAimLayer(aim: BallAim?,plan: BallPlan?,step: Float,anchor: (Float,Float)->Unit,
    target: (Float,Float)->Unit,modifier: Modifier=Modifier) {
    val anchorNow by rememberUpdatedState(anchor)
    val targetNow by rememberUpdatedState(target)
    if(aim==null) return
    Canvas(modifier.pointerInput(step) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val origin=down.position
            anchorNow(origin.x/step,origin.y/step)
            down.consume()
            val complete=drag(down.id) { change->
                change.consume(); targetNow(change.position.x/step,change.position.y/step)
            }
            if(!complete) anchorNow(origin.x/step,origin.y/step)
        }
    }) {
        drawRect(Color(0xFF070D1B).copy(alpha=.28f))
        drawRoundRect(ballGold.copy(alpha=.6f),Offset(step*.03f,step*.03f),Size(size.width-step*.06f,size.height-step*.06f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(step*.22f),style=Stroke(step*.03f))
        plan?.let { p->
            p.cells.forEach { cell-> drawRoundRect(ballGold.copy(alpha=.17f),Offset(cell.x*step,cell.y*step),Size(step,step))
                drawRoundRect(ballGold,Offset((cell.x+.04f)*step,(cell.y+.04f)*step),Size(step*.92f,step*.92f),style=Stroke(step*.03f)) }
            p.obstacle?.let { cell-> drawRoundRect(Color(0xFFFF7C89),Offset(cell.x*step,cell.y*step),Size(step,step),style=Stroke(step*.045f)) }
        }
        val arc=plan?.arc ?: aim.arc
        arc?.let { a->
            val path=androidx.compose.ui.graphics.Path()
            repeat(65) { i-> val (x,y)=a.point(i/64f*a.endFraction)
                if(i==0) path.moveTo(x*step,y*step) else path.lineTo(x*step,y*step) }
            drawPath(path,ballGold.copy(alpha=.12f),style=Stroke(step))
            drawPath(path,ballGold.copy(alpha=.85f),style=Stroke(step*.035f))
            val (x,y)=a.point(a.endFraction)
            drawLine(ballGold.copy(alpha=.65f),Offset(a.x*step,a.y*step),Offset(x*step,y*step),step*.03f)
            drawBall(Offset(x*step,y*step),step*.5f,1f)
            drawCircle(Color.White,step*.13f,Offset(a.x*step,a.y*step))
        }
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBall(c: Offset,r: Float,alpha: Float) {
    drawCircle(Color.Black.copy(alpha=.3f*alpha),r*1.08f,c+Offset(r*.12f,r*.16f))
    drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Color(0xFFBED5E6).copy(alpha=alpha),Color(0xFF53647C).copy(alpha=alpha),Color(0xFF19253B).copy(alpha=alpha)),c-Offset(r*.3f,r*.35f),r*1.5f),r,c)
    drawCircle(Color(0xFFFFCF72).copy(alpha=.8f*alpha),r,c,style=Stroke(r*.08f))
    drawArc(Color.White.copy(alpha=.65f*alpha),210f,65f,false,c-Offset(r*.7f,r*.7f),Size(r*1.4f,r*1.4f),style=Stroke(r*.09f))
    repeat(3) { i-> val a=(i*120f+15f)*kotlin.math.PI.toFloat()/180f
        drawCircle(Color(0xFF1A263A).copy(alpha=alpha),r*.09f,c+Offset(kotlin.math.cos(a),kotlin.math.sin(a))*r*.7f) }
}
