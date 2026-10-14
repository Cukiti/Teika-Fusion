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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import com.cukiti.teikafusion.model.*
import kotlin.math.*

private val forgeGold=Color(0xFFFFCE75)
private val forgeHeat=Color(0xFFFF8251)

@Composable
fun ForgeControls(charged: Boolean, aim: ForgeAim?, enabled: Boolean, confirmEnabled: Boolean,
    start: ()->Unit, cancel: ()->Unit, changeStage: ()->Unit, confirm: ()->Unit, modifier: Modifier=Modifier) {
    BackHandler(enabled=aim!=null,onBack=cancel)
    Column(modifier.width(196.dp).height(72.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
        if(aim!=null) Text(aim.feedback,color=Color.White,fontSize=11.sp,maxLines=1,
            modifier=Modifier.background(Color(0xED111A2E),RoundedCornerShape(8.dp)).padding(horizontal=6.dp,vertical=3.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            val active=if(aim==null) enabled else confirmEnabled
            val tint=if(charged) forgeGold else Color(0xFF8792A7)
            Column(Modifier.size(if(aim==null) 80.dp else 88.dp,48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription=if(aim!=null) "Forjar un Teika" else if(charged) "Forja Teika cargada" else "Forja: limpia una línea para cargar" }
                .clickable(enabled=active,role=Role.Button,onClick=if(aim==null) start else confirm),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Canvas(Modifier.size(28.dp,23.dp)) {
                    val c=Offset(size.width/2,size.height/2)
                    drawArc(tint.copy(alpha=if(active) 1f else .4f),20f,140f,false,c-Offset(8.dp.toPx(),8.dp.toPx()),Size(16.dp.toPx(),16.dp.toPx()),style=Stroke(3.dp.toPx()))
                    drawCircle(forgeHeat.copy(alpha=if(active) 1f else .4f),3.dp.toPx(),c)
                }
                Text(if(aim==null) "FORJA" else "FORJAR",color=tint.copy(alpha=if(active) 1f else .55f),fontSize=9.sp,maxLines=1)
            }
            if(aim!=null) {
                Box(Modifier.size(46.dp,48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                    .semantics { contentDescription=if(aim.choosingDestination) "Cambiar madera de la forja" else "Elegir destino de la forja" }
                    .clickable(enabled=aim.choosingDestination || aim.cells.size==4,role=Role.Button,onClick=changeStage),contentAlignment=Alignment.Center) {
                    Text(if(aim.choosingDestination) "MADERA" else "DESTINO",fontSize=8.sp,color=forgeGold.copy(alpha=if(aim.choosingDestination || aim.cells.size==4) 1f else .45f),maxLines=1)
                }
                Box(Modifier.size(46.dp,48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                    .semantics { contentDescription="Cancelar forja" }.clickable(role=Role.Button,onClick=cancel),contentAlignment=Alignment.Center) {
                    Text("×",fontSize=28.sp,color=Color.White)
                }
            }
        }
    }
}

@Composable
fun ForgeAimLayer(aim: ForgeAim?, plan: ForgePlan?, step: Float, select: (Position)->Unit, modifier: Modifier=Modifier) {
    if(aim==null) return
    val selectNow by rememberUpdatedState(select)
    Canvas(modifier.pointerInput(step) { detectTapGestures { p ->
        if(p.x>=0 && p.y>=0 && p.x<size.width && p.y<size.height)
            selectNow(Position(floor(p.x/step).toInt(),floor(p.y/step).toInt()))
    } }) {
        drawRect(Color(0xFF070D1B).copy(alpha=.24f))
        val tint=if(aim.cells.size==4 && !ForgeRules.connected(aim.cells)) Color(0xFFFF778A) else forgeGold
        aim.cells.forEachIndexed { i,p ->
            val c=Offset((p.x+.5f)*step,(p.y+.5f)*step)
            drawRoundRect(tint.copy(alpha=.2f),Offset((p.x+.05f)*step,(p.y+.05f)*step),Size(step*.9f,step*.9f),CornerRadius(step*.08f))
            drawRoundRect(tint,Offset((p.x+.05f)*step,(p.y+.05f)*step),Size(step*.9f,step*.9f),CornerRadius(step*.08f),style=Stroke(step*.035f))
            repeat(i+1) { n -> drawCircle(tint,step*.025f,c+Offset((n-i*.5f)*step*.09f,step*.31f)) }
        }
        aim.destination?.let { p ->
            val c=Offset((p.x+.5f)*step,(p.y+.5f)*step)
            val color=if(plan!=null) Color(0xFF8CFFE0) else Color(0xFFFF778A)
            drawCircle(color.copy(alpha=.20f),step*.5f,c)
            drawCircle(color,step*.5f,c,style=Stroke(step*.035f))
            if(plan!=null) {
                aim.cells.forEach { q -> drawLine(color.copy(alpha=.3f),Offset((q.x+.5f)*step,(q.y+.5f)*step),c,step*.02f) }
                drawCircle(color,step*.04f,c+Offset(-step*.14f,-step*.08f))
                drawCircle(color,step*.04f,c+Offset(step*.14f,-step*.08f))
                drawArc(color,20f,140f,false,c+Offset(-step*.14f,0f),Size(step*.28f,step*.2f),style=Stroke(step*.025f))
            } else {
                drawLine(color,c-Offset(step*.14f,step*.14f),c+Offset(step*.14f,step*.14f),step*.035f)
                drawLine(color,c+Offset(-step*.14f,step*.14f),c+Offset(step*.14f,-step*.14f),step*.035f)
            }
        }
    }
}

/** Four staggered flights feed a metal crucible; heat builds, then the real body is revealed. */
@Composable
fun ForgePulseVisual(event: GameEvent.RulePulse, elapsed: ()->Float, step: Float, cellSize: Dp, animationsOn: Boolean) {
    val target=event.points.firstOrNull() ?: return
    val center=Offset((target.x+.5f)*step,(target.y+.5f)*step)
    Box(Modifier.fillMaxSize().clipToBounds()) {
    if(animationsOn) event.wood.forEachIndexed { i,(p,w) -> key(w.id) {
        CellView(w.copy(skipSpawnAnimation=true),Modifier.offset { IntOffset((p.x*step).roundToInt(),(p.y*step).roundToInt()) }.size(cellSize)
            .graphicsLayer {
                val time=elapsed()
                val start=160f+i*70
                val t=((time-start)/370f).coerceIn(0f,1f)
                val ease=t*t*(3-2*t)
                translationX=(target.x-p.x)*step*ease
                translationY=(target.y-p.y)*step*ease-sin(t*PI.toFloat())*step*min(.6f,min(p.y,target.y).toFloat())
                rotationZ=sin(t*PI.toFloat())*(if(i%2==0) 1 else -1)*18f
                scaleX=1f-ease*.8f; scaleY=scaleX
                alpha=if(t<.85f) 1f else ((1-t)/.15f).coerceIn(0f,1f)
            },shouldAnimate=false,breathe=false)
    } }
    Canvas(Modifier.fillMaxSize()) {
        val time=elapsed(); val fade=if(animationsOn) (1f-((time-940f)/260f).coerceIn(0f,1f)) else 1f-time/event.durationMs
        if(!animationsOn) {
            drawCircle(forgeGold.copy(alpha=.7f*fade),step*.5f,center,style=Stroke(step*.045f))
            return@Canvas
        }
        val inT=(time/160f).coerceIn(0f,1f)
        val heat=((time-240f)/480f).coerceIn(0f,1f)
        val ignition=((time-720f)/100f).coerceIn(0f,1f)
        drawRect(Color(0xFF050B18).copy(alpha=.23f*fade*inT))
        drawCircle(Brush.radialGradient(listOf(forgeHeat.copy(alpha=(.12f+heat*.45f)*fade),Color.Transparent),center,step*1.1f),step*1.1f,center)
        // The half-cell rim fits even an edge output; its mechanical shutters never overshoot.
        drawCircle(Color(0xFF1C2940).copy(alpha=fade*inT),step*.42f,center,style=Stroke(step*.10f))
        drawCircle(forgeGold.copy(alpha=fade*inT),step*.42f,center,style=Stroke(step*.025f))
        repeat(4) { i ->
            val a=(45+i*90)*PI.toFloat()/180f
            val d=Offset(cos(a),sin(a))
            drawLine(Color(0xFFABC1D5).copy(alpha=fade*inT),center+d*step*.30f,center+d*step*.43f,step*.055f,StrokeCap.Round)
        }
        if(time<ForgeRules.REVEAL_MS) {
            drawCircle(forgeHeat.copy(alpha=heat*fade),step*(.08f+.3f*heat),center)
            drawCircle(Color.White.copy(alpha=ignition*.9f),step*.40f,center)
            repeat(12) { i ->
                val age=((time-240f+i*31f)%220f)/220f
                val a=(i*2.399f)
                val d=Offset(cos(a),sin(a))
                val tip=center+d*step*(.38f+age*.55f)
                drawLine(forgeGold.copy(alpha=heat*(1-age)),tip-d*step*.075f,tip,step*.022f,StrokeCap.Round)
            }
        } else {
            val t=((time-ForgeRules.REVEAL_MS)/380f).coerceIn(0f,1f)
            drawCircle(forgeGold.copy(alpha=(1-t)*.85f),step*(.5f+t*.6f),center,style=Stroke(step*.035f))
            repeat(8) { i ->
                val a=i*PI.toFloat()/4; val d=Offset(cos(a),sin(a))
                drawLine(forgeGold.copy(alpha=1-t),center+d*step*(.55f+t*.45f),center+d*step*(.7f+t*.45f),step*.025f,StrokeCap.Round)
            }
        }
    }
    }
}
