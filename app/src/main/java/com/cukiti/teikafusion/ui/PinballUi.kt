package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import com.cukiti.teikafusion.model.*
import com.cukiti.teikafusion.viewmodel.TeikaBody
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.*

/** Bounded overlay: consumes only a charged body gesture; taps elsewhere reach the board. */
@Composable
fun PinballLayer(state: GameState,bodies: StateFlow<List<TeikaBody>>,step: Float,animationsOn: Boolean,
    begin: (String)->Boolean,cancel: ()->Unit,release: (String,Float,Float)->Unit,
    onConsumed: (String)->Unit,modifier: Modifier=Modifier,impacts: List<GameEvent.PinballImpact> = emptyList()) {
    if(!PinballRules.appliesTo(state.level)) return
    val snapshot by bodies.collectAsState()
    var aimed by remember(state.level) { mutableStateOf<TeikaBody?>(null) }
    var pull by remember(state.level) { mutableStateOf(Offset.Zero) }
    val beginNow by rememberUpdatedState(begin)
    val cancelNow by rememberUpdatedState(cancel)
    val releaseNow by rememberUpdatedState(release)
    val enabled=state.pinballCharged && state.pinballFlight==null && state.status==GameStatus.PLAYING
    DisposableEffect(state.level,state.status) { onDispose { aimed=null; cancelNow() } }
    val trail=remember(state.level) { mutableStateListOf<Offset>() }
    LaunchedEffect(snapshot,state.pinballFlight?.bodyId) {
        val b=snapshot.firstOrNull { it.id==state.pinballFlight?.bodyId }
        if(b==null || !animationsOn) trail.clear()
        else { val p=Offset(b.x,b.y); if(trail.lastOrNull()!=p) { trail.add(p); while(trail.size>10) trail.removeAt(0) } }
    }
    Canvas(modifier.pointerInput(enabled,state.level,step) {
        if(!enabled) return@pointerInput
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            val b=bodies.value.filter { it.frozenTurns==0 && !it.kinematic && it.bubble==0 }
                .minByOrNull { hypot(it.x*step-down.position.x,it.y*step-down.position.y) }
            if(b==null || hypot(b.x*step-down.position.x,b.y*step-down.position.y)>(b.radius+.18f)*step || !beginNow(b.id)) return@awaitEachGesture
            try {
                val held=awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                held.consume(); aimed=bodies.value.firstOrNull { it.id==b.id } ?: b; pull=Offset.Zero
                var released=false
                while(true) {
                    val event=awaitPointerEvent()
                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    if(change.isConsumed) break
                    pull=(change.position-down.position)/step
                    change.consume()
                    if(!change.pressed) { releaseNow(b.id,pull.x,pull.y); released=true; break }
                }
                if(!released) cancelNow()
            } finally { aimed=null; pull=Offset.Zero; cancelNow() }
        }
    }) {
        val green=Color(0xFF88FFD1)
        if(animationsOn) trail.zipWithNext().forEachIndexed { i,(a,b) ->
            drawLine(green.copy(alpha=(i+1f)/trail.size*.45f),a*step,b*step,step*.09f,StrokeCap.Round)
        }
        snapshot.forEach { body ->
            val active=body.id==state.pinballFlight?.bodyId
            val ready=enabled && body.frozenTurns==0 && !body.kinematic && body.bubble==0
            if(active || ready || aimed?.id==body.id) {
                val c=Offset(body.x,body.y)*step
                drawCircle(green.copy(alpha=if(active) .95f else .6f),(body.radius+.08f)*step,c,style=Stroke(step*.04f))
                if(active) repeat(state.pinballFlight!!.bouncesLeft) { i ->
                    val angle=-PI/2+i*PI/3
                    drawCircle(Color.White,step*.035f,c+Offset(cos(angle).toFloat(),sin(angle).toFloat())*(body.radius+.18f)*step)
                }
            }
        }
        aimed?.let { body ->
            val v=PinballRules.velocity(pull.x,pull.y)
            val origin=Offset(body.x,body.y)*step
            val pullLength=pull.getDistance()
            if(pullLength>body.radius) drawLine(green.copy(alpha=.5f),origin+pull/pullLength*body.radius*step,origin+pull*step,step*.045f,StrokeCap.Round)
            if(v!=null) {
                val direction=Offset(v.first,v.second)/PinballRules.SPEED
                // Show only the first unobstructed segment; later contacts remain physical.
                var length=0f
                for(i in 1..160) {
                    val p=Offset(body.x,body.y)+direction*(i*.05f)
                    val r=body.radius
                    if(p.x-r<0 || p.y-r<0 || p.x+r>state.gridW || p.y+r>state.gridH ||
                        (state.lavaActive && p.y-r<state.lavaRow) ||
                        (state.ninjaCutActive && ((body.x<4 && p.x+r>4) || (body.x>=4 && p.x-r<4))) ||
                        state.grid.any { (cell,c) -> (c !is CellContent.Empty || cell in state.gluttonDeadCells || cell==state.kingRestCell || cell==state.eggCell) &&
                            hypot(p.x-p.x.coerceIn(cell.x.toFloat(),cell.x+1f),p.y-p.y.coerceIn(cell.y.toFloat(),cell.y+1f))<r } ||
                        snapshot.any { it.id!=body.id && hypot(it.x-p.x,it.y-p.y)<it.radius+r }) break
                    length=i*.05f
                }
                val tip=origin+direction*length*step
                if(length>body.radius+.2f) {
                    drawLine(green,origin+direction*body.radius*step,tip,step*.035f,StrokeCap.Round)
                    drawCircle(Color.White,step*.065f,tip)
                    val across=Offset(-direction.y,direction.x)
                    drawLine(green,tip-direction*step*.22f+across*step*.14f,tip,step*.045f,StrokeCap.Round)
                    drawLine(green,tip-direction*step*.22f-across*step*.14f,tip,step*.045f,StrokeCap.Round)
                } else drawCircle(green,step*.05f,origin+direction*(body.radius+.12f)*step)
            }
        }
    }
    impacts.forEach { hit -> key(hit.id) {
        val progress=remember { Animatable(0f) }
        LaunchedEffect(hit.id) { Haptics.place(); progress.animateTo(1f,tween(if(animationsOn) 280 else 120)); onConsumed(hit.id) }
        Canvas(modifier) {
            val t=progress.value; val center=Offset(hit.x,hit.y)*step
            drawCircle(Color(0xFF88FFD1).copy(alpha=1-t),step*(if(animationsOn) .25f+t*.65f else .6f),center,style=Stroke(step*.045f*(1-t)))
            if(animationsOn) repeat(8) { i ->
                val angle=i*PI/4; val direction=Offset(cos(angle).toFloat(),sin(angle).toFloat())
                drawLine(Color.White.copy(alpha=1-t),center+direction*step*(.3f+t*.4f),center+direction*step*(.42f+t*.8f),step*.035f,StrokeCap.Round)
            }
        }
    } }
}
