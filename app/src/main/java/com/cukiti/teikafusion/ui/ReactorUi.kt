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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.*
import kotlin.math.floor

private val reactorGold=Color(0xFFFFCF72)
private val reactorHot=Color(0xFFFF885C)

@Composable
fun ReactorControls(charged: Boolean, aim: ReactorAim?, enabled: Boolean, confirmEnabled: Boolean,
    start: ()->Unit, cancel: ()->Unit, confirm: ()->Unit, modifier: Modifier=Modifier) {
    BackHandler(enabled=aim!=null,onBack=cancel)
    Column(modifier.width(196.dp).height(72.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Bottom) {
        if(aim!=null) Text(aim.feedback,color=Color.White,fontSize=11.sp,maxLines=1,
            modifier=Modifier.background(Color(0xDD111A2E),RoundedCornerShape(8.dp)).padding(horizontal=7.dp,vertical=3.dp))
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
            val active=if(aim==null) enabled else confirmEnabled
            val color=if(charged) reactorGold else Color(0xFF8792A7)
            Column(Modifier.size(if(aim==null) 72.dp else 108.dp,48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription=if(aim!=null) "Detonar grupo de color" else if(charged) "Reactor de color cargado" else "Reactor: limpia una línea para cargar" }
                .clickable(enabled=active,role=Role.Button,onClick=if(aim==null) start else confirm),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                Canvas(Modifier.size(34.dp,23.dp)) {
                    val c=Offset(size.width/2,size.height/2)
                    drawCircle(color.copy(alpha=if(active) .2f else .08f),size.height*.44f,c)
                    drawCircle(color.copy(alpha=if(active) 1f else .4f),size.height*.28f,c,style=Stroke(2.dp.toPx()))
                    repeat(3) { i ->
                        val angle=i*2.094f
                        val tip=c+Offset(kotlin.math.cos(angle),kotlin.math.sin(angle))*size.height*.43f
                        drawCircle(reactorHot,2.dp.toPx(),tip)
                    }
                }
                Text(if(aim==null) "REACTOR" else "DETONAR",color=color.copy(alpha=if(active) 1f else .55f),fontSize=9.sp,maxLines=1)
            }
            if(aim!=null) Box(Modifier.size(48.dp).background(Color(0xFF182239),RoundedCornerShape(14.dp))
                .semantics { contentDescription="Cancelar reactor" }.clickable(role=Role.Button,onClick=cancel),contentAlignment=Alignment.Center) {
                Text("×",fontSize=28.sp,color=Color.White)
            }
        }
    }
}


/** The highlighted group is the exact motor plan, with one connector per breadth-first edge. */
@Composable
fun ReactorAimLayer(aim: ReactorAim?, plan: ReactorPlan?, step: Float,
    select: (Position)->Unit, modifier: Modifier=Modifier) {
    val selectNow by rememberUpdatedState(select)
    if(aim==null) return
    Canvas(modifier.pointerInput(step) { detectTapGestures { p ->
        if(p.x>=0f && p.y>=0f && p.x<size.width && p.y<size.height)
            selectNow(Position(floor(p.x/step).toInt(),floor(p.y/step).toInt()))
    } }) {
        drawRect(Color(0xFF070D1B).copy(alpha=.28f))
        fun mid(p: Position)=Offset((p.x+.5f)*step,(p.y+.5f)*step)
        plan?.links?.forEach { (a,b) ->
            val delta=mid(b)-mid(a)
            val from=mid(a)+delta*.38f; val to=mid(b)-delta*.38f
            drawLine(reactorGold.copy(alpha=.20f),from,to,step*.20f)
            drawLine(reactorGold.copy(alpha=.95f),from,to,step*.035f)
        }
        plan?.cells?.forEach { p ->
            drawRoundRect(reactorGold.copy(alpha=.16f),Offset(p.x*step,p.y*step),Size(step,step))
            drawRoundRect(reactorGold,Offset((p.x+.04f)*step,(p.y+.04f)*step),Size(step*.92f,step*.92f),cornerRadius=androidx.compose.ui.geometry.CornerRadius(step*.09f),style=Stroke(step*.035f))
        }
        aim.origin?.let { p ->
            drawCircle(if(plan==null) Color(0xFFFF7C89) else Color.White,step*.065f,Offset((p.x+.16f)*step,(p.y+.16f)*step))
        }
    }
}

/** Observes the presentation clock; GameBoard's RulePulse layer remains the sole consumer. */
@Composable
fun ReactorHiddenBodies(events: List<GameEvent.RulePulse>, animationsOn: Boolean): Set<String> {
    val hidden=mutableSetOf<String>()
    events.filter { it.kind=="forge" || it.kind=="reactor" || it.kind=="wrecking_ball" }.forEach { event -> key(event.id) {
        val clock=remember { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(event.id) { clock.animateTo(1f,androidx.compose.animation.core.tween(event.durationMs,easing=androidx.compose.animation.core.LinearEasing)) }
        val ids by remember(event.id,animationsOn) { derivedStateOf {
            if(!animationsOn) emptySet() else {
                if(event.kind=="forge") {
                    if(clock.value*event.durationMs<ForgeRules.REVEAL_MS) event.bodyIds.toSet() else emptySet()
                } else {
                val seeds=event.wood.filter { it.second.teikaSeed }
                event.bodyIds.filterIndexed { i,_ ->
                    val position=seeds.getOrNull(i)?.first
                    val index=event.cells.indexOf(position)
                    clock.value*event.durationMs<(event.hitTimes.getOrNull(index) ?: 0)
                }.toSet()
                }
            }
        } }
        hidden.addAll(ids)
    } }
    return hidden
}

/** A seed's capsule hides its wood face; the rim exposes the color used by Reactor. */
@Composable
fun ReactorSeedColorLayer(state: GameState, cellSize: androidx.compose.ui.unit.Dp) {
    if(!ReactorRules.appliesTo(state.level)) return
    val step=with(androidx.compose.ui.platform.LocalDensity.current) { cellSize.toPx() }
    val skin=LocalBlockSkin.current
    Canvas(Modifier.fillMaxSize()) {
        state.grid.forEach { (p,c) ->
            if(c is CellContent.Wood && c.teikaSeed && p !in state.hiddenWoodCells)
                drawReactorSeedRim(p,step,skin.colorsFor(c.colorType).first())
        }
    }
}

internal fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReactorSeedRim(p: Position,step: Float,color: Color) {
    drawRoundRect(color,Offset((p.x+.075f)*step,(p.y+.075f)*step),Size(step*.85f,step*.85f),
        cornerRadius=androidx.compose.ui.geometry.CornerRadius(step*.09f),style=Stroke(step*.04f))
}
