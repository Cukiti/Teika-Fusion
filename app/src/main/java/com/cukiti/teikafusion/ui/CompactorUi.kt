package com.cukiti.teikafusion.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.*
import kotlin.math.*

@Composable
fun CompactorButton(charged: Boolean, aiming: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier=Modifier) {
    val color=if(aiming) Color(0xFFFFD788) else if(charged) Color(0xFFFFB862) else Color(0xFF8792A7)
    Column(modifier.size(64.dp,48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
        .semantics { contentDescription=if(aiming) "Cancelar compactadora" else if(charged) "Compactadora cargada: desliza sobre una fila o columna" else "Compactadora: limpia una línea para cargar" }
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
        Canvas(Modifier.size(28.dp,20.dp)) {
            drawLine(color,Offset(size.width*.12f,size.height*.1f),Offset(size.width*.88f,size.height*.1f),3.dp.toPx(),StrokeCap.Round)
            drawLine(color,Offset(size.width*.5f,size.height*.1f),Offset(size.width*.5f,size.height*.5f),3.dp.toPx())
            drawRoundRect(color,Offset(size.width*.15f,size.height*.5f),Size(size.width*.7f,size.height*.25f))
            repeat(3) { drawRect(color.copy(alpha=.55f),Offset(size.width*(.16f+it*.26f),size.height*.83f),Size(size.width*.18f,size.height*.12f)) }
        }
        Text(if(aiming) "CANCELAR" else "PRENSA",color=color,fontSize=9.sp)
    }
}

/** Aiming freezes the board in the VM. Preview and commit use the same pure plan. */
@Composable
fun CompactorAimLayer(aiming: Boolean, step: Float, preview: (Int,Int,Int)->CompactorPlan?,
    cancel: ()->Unit, release: (Int,Int,Int)->Unit, modifier: Modifier=Modifier) {
    var plan by remember(aiming) { mutableStateOf<CompactorPlan?>(null) }
    var selected by remember(aiming) { mutableStateOf(Offset.Zero) }
    val previewNow by rememberUpdatedState(preview)
    val cancelNow by rememberUpdatedState(cancel)
    val releaseNow by rememberUpdatedState(release)
    DisposableEffect(Unit) { onDispose { cancelNow() } }
    if(!aiming) return
    Canvas(modifier.pointerInput(aiming,step) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            down.consume()
            var fired=false
            try {
                while(true) {
                    val event=awaitPointerEvent()
                    val c=event.changes.firstOrNull { it.id==down.id } ?: break
                    if(c.isConsumed) break
                    val delta=c.position-down.position
                    val d=if(abs(delta.x)>=abs(delta.y)) { if(delta.x>=0) 0 else 2 } else { if(delta.y>=0) 1 else 3 }
                    val x=(down.position.x/step).toInt(); val y=(down.position.y/step).toInt()
                    selected=Offset((x+.5f)*step,(y+.5f)*step)
                    plan=if(delta.getDistance()>=step*.35f) previewNow(x,y,d) else null
                    c.consume()
                    if(!c.pressed) {
                        if(c.position.x in 0f..size.width.toFloat() && c.position.y in 0f..size.height.toFloat() && plan?.moves?.isNotEmpty()==true) { fired=true; releaseNow(x,y,d) }
                        break
                    }
                }
            } finally { plan=null; if(!fired) cancelNow() }
        }
    }) {
        drawRect(Color(0xFF090C18).copy(alpha=.20f))
        plan?.let { p ->
            val color=if(p.moves.isEmpty()) Color(0xFFEF8B83) else Color(0xFFFFCF84)
            if(p.moves.isEmpty()) {
                drawCircle(color,step*.27f,selected,style=Stroke(step*.04f))
                drawLine(color,selected-Offset(step*.17f,step*.17f),selected+Offset(step*.17f,step*.17f),step*.04f)
            }
            p.cells.forEach { cell -> drawRect(color.copy(alpha=.11f),Offset(cell.x*step,cell.y*step),Size(step,step)) }
            p.moves.forEach { (a,b) ->
                val from=Offset((a.x+.5f)*step,(a.y+.5f)*step); val to=Offset((b.x+.5f)*step,(b.y+.5f)*step)
                drawLine(color.copy(alpha=.7f),from,to,step*.035f,StrokeCap.Round)
                drawRoundRect(color.copy(alpha=.8f),Offset((b.x+.1f)*step,(b.y+.1f)*step),Size(step*.8f,step*.8f),style=Stroke(step*.04f))
                drawCircle(color,step*.065f,to)
            }
        }
    }
}
