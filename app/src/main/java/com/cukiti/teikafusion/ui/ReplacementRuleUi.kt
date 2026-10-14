package com.cukiti.teikafusion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.GenericShape
import kotlin.math.*
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect


/** Each committed effect owns one clock; snapshots preserve the block's real skin until impact. */
@Composable
fun ReplacementRuleLayer(state: GameState, cellSize: Dp, events: List<GameEvent.RulePulse>, animationsOn: Boolean, onConsumed: (String) -> Unit, bodies: kotlinx.coroutines.flow.StateFlow<List<com.cukiti.teikafusion.viewmodel.TeikaBody>>? = null) {
    val step = with(LocalDensity.current) { cellSize.toPx() }
    val blockSkin=LocalBlockSkin.current
    val seedGeometry=remember(step) { SeedGeometry(step) }
    val charged = remember { Animatable(0f) }
    LaunchedEffect(state.constellationCovered,state.trampolineCharged,state.avalancheCharge,state.clawCharged,state.reactorCharged) {
        charged.snapTo(0f); charged.animateTo(1f,tween(if (animationsOn) 240 else 80))
    }
    val oldConstellation = events.any { it.kind == "constellation" }
    Canvas(Modifier.fillMaxSize()) {
        fun center(p: Position) = Offset((p.x+.5f)*step,(p.y+.5f)*step)
        fun glow(mid: Offset, color: Color, radius: Float, alpha: Float = 1f) {
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = .35f*alpha),Color.Transparent),mid,radius),radius,mid)
        }
        fun star(mid: Offset, radius: Float, color: Color) {
            val path = Path()
            repeat(10) { i -> val angle = -PI/2+i*PI/5; val r = if (i%2==0) radius else radius*.42f
                val x = mid.x+cos(angle).toFloat()*r; val y = mid.y+sin(angle).toFloat()*r
                if (i==0) path.moveTo(x,y) else path.lineTo(x,y)
            }; path.close(); drawPath(path,color)
        }
        if (ConstellationRules.appliesTo(state.level) && !oldConstellation) {
            ConstellationRules.trace(state.constellationPoints).zipWithNext().forEach { (a,b) ->
                drawLine(Color(0xFF9673D4).copy(alpha=.35f),center(a),center(b),step*.025f)
            }
            state.constellationPoints.forEach { p ->
                val done = p in state.constellationCovered; val mid = center(p)
                val color = if (done) Color(0xFFFFE8AD) else Color(0xFFC6AEFF)
                glow(mid,color,step*(if (done) .65f else .3f),if (done) .7f+charged.value*.3f else .3f)
                drawCircle(Color(0xFF171126).copy(alpha=.7f),step*.18f,mid)
                star(mid,step*(if(done) .15f else .10f),color)
                drawCircle(color.copy(alpha=.55f),step*.23f,mid,style=Stroke(step*.022f))
            }
        }

        if(SurfRules.appliesTo(state.level) && events.none { it.kind=="surf" }) {
            val direction=SurfRules.direction(state.level,state.surfWave)
            val y=(state.gridH-.15f)*step
            repeat(3) { n ->
                val x=(state.gridW*.5f+direction*(n-1)*.35f)*step
                drawLine(Color(0xFF83DAFF).copy(alpha=.55f),Offset(x-direction*step*.1f,y-step*.10f),Offset(x,y),step*.035f,StrokeCap.Round)
                drawLine(Color(0xFF83DAFF).copy(alpha=.55f),Offset(x-direction*step*.1f,y+step*.10f),Offset(x,y),step*.035f,StrokeCap.Round)
            }
        }
        if (TrampolineRules.appliesTo(state.level)) state.trampolineCell?.let { p ->
            val mid=center(p); val blocked=state.grid[p] is CellContent.Wood || p in state.unplaceableCells || (state.lavaActive && p.y < state.lavaRow)
            val color=if(blocked) Color(0xFF89919F) else if(state.trampolineCharged) Color(0xFF74FFBB) else Color(0xFF638C9F)
            if(state.trampolineCharged && !blocked) glow(mid,color,step*.65f)
            drawRoundRect(Color(0xFF10192A),Offset((p.x+.07f)*step,(p.y+.67f)*step),Size(step*.86f,step*.28f))
            repeat(3) { i -> val x=(p.x+.22f+i*.28f)*step
                drawLine(color,Offset(x,(p.y+.76f)*step),Offset(x+step*.12f,(p.y+.76f)*step),step*.07f,StrokeCap.Round)
            }
            val tip=mid+Offset(if(TrampolineRules.horizontalSpeed(state.level)>0) step*.17f else -step*.17f,-step*.25f)
            drawLine(color,mid+Offset(0f,step*.13f),tip,step*.04f,StrokeCap.Round)
            if(blocked) drawLine(color,mid+Offset(-step*.12f,-step*.18f),mid+Offset(step*.12f,step*.06f),step*.04f)
        }
    }
    events.forEach { event -> key(event.id) {
        val progress=remember { Animatable(0f) }
        val duration=event.durationMs
        if (animationsOn && event.kind == "trampoline" && bodies != null) RuleBodyWake(event,bodies,step) { 1f-progress.value }
        if(animationsOn && event.kind=="surf" && bodies!=null) event.bodyIds.take(6).forEach { id -> key(id) {
            RuleBodyWake(event,bodies,step,bodyId=id,color=Color(0xFF83DAFF)) { 1f-progress.value }
        } }
        LaunchedEffect(event.id) {
            coroutineScope {
                launch {
                    if(animationsOn) kotlinx.coroutines.delay(event.hitTimes.firstOrNull()?.toLong() ?: 0L)
                    if(event.kind in listOf("forge","orbit","claw_land","wrecking_ball","reactor","portal_swap","compactor","constellation","trampoline","surf","relay","reserve_swap")) Haptics.place() else Haptics.tap()
                }
                progress.animateTo(1f,tween(duration,easing=androidx.compose.animation.core.LinearEasing))
            }
            onConsumed(event.id)
        }
        if(event.kind=="forge") ForgePulseVisual(event,{ progress.value*duration },step,cellSize,animationsOn)
        if (animationsOn && event.kind!="forge") event.wood.forEach { (p,w) -> key(w.id) {
            val index=event.cells.indexOf(p).coerceAtLeast(0)
            val hit=event.hitTimes.getOrNull(index) ?: 180
            val starAttached by remember(event.id,w.id) { derivedStateOf { event.kind!="relay" || progress.value*duration<hit } }
            CellView(w.copy(skipSpawnAnimation=true,targetColor=if(starAttached) w.targetColor else null),Modifier.offset { IntOffset((p.x*step).roundToInt(),(p.y*step).roundToInt()) }.size(cellSize)
                .graphicsLayer {
                    val elapsed=progress.value*duration
                    alpha=if(elapsed<hit) 1f else 0f
                    if(event.kind=="relay") {
                        val f=((elapsed-hit)/320f).coerceIn(0f,1f)
                        val d=DominoRules.steps.getOrElse(event.direction) { Position(1,0) }
                        alpha=1f-f*f
                        translationX=d.x*step*f*(1.5f+f*2f)
                        translationY=d.y*step*f*(1.5f+f*2f)
                        rotationZ=(if((p.x+p.y)%2==0) 1 else -1)*f*28f
                        scaleX=1f-f*.45f; scaleY=scaleX
                    }
                    if(event.kind=="reactor") {
                        val f=((elapsed-hit+150)/150f).coerceIn(0f,1f)
                        scaleX=1f+f*f*.08f; scaleY=scaleX
                    }

                },shouldAnimate=false,breathe=false)
        } }
        Canvas(Modifier.fillMaxSize()) {
            val t=progress.value; val elapsed=t*duration
            if(event.kind=="forge") return@Canvas
            fun mid(p: Position)=Offset((p.x+.5f)*step,(p.y+.5f)*step)
            val color=when(event.kind) { "wrecking_ball","ball_charge"->Color(0xFFFFCF72); "forge_charge"->Color(0xFFFFCE75); "avalanche","avalanche_charge"->Color(0xFFFFB371); "trampoline","trampoline_charge"->Color(0xFF74FFBB); "reactor","reactor_charge"->Color(0xFFFFCF72); "claw_land","claw_charge"->Color(0xFF7FFFE0); "surf"->Color(0xFF83DAFF); "turbine_charge"->Color(0xFF9AF6FF); "relay","relay_charge"->Color(0xFFFFB3DB); "reserve_swap"->Color(0xFF8AF5E0); else->Color(0xFFD4C0FF) }
            if (!animationsOn) {
                if(event.kind=="claw_land") event.clawLanding?.let { landing ->
                    drawCircle(color.copy(alpha=(1-t)*.6f),landing.radius*step,Offset(landing.toX*step,landing.toY*step),style=Stroke(step*.035f))
                    return@Canvas
                }
                (event.points + event.cells).distinct().take(64).forEach { p ->
                    drawRoundRect(color.copy(alpha=(1-t)*.6f),Offset(p.x*step,p.y*step),Size(step,step),style=Stroke(step*.035f))
                }
                return@Canvas
            }
            if(event.kind=="orbit" || event.kind=="orbit_charge") {
                val color=Color(0xFF8CEBFF)
                if(event.kind=="orbit_charge") event.cells.take(16).forEach { p ->
                    drawCircle(color.copy(alpha=(1f-progress.value)*.55f),step*.36f,mid(p),style=Stroke(step*.035f))
                } else event.points.firstOrNull()?.let { c ->
                    val center=mid(c)
                    val fade=if(elapsed<820f) 1f else (1f-(elapsed-820f)/240f).coerceIn(0f,1f)
                    if(animationsOn) {
                        drawRect(Color(0xFF050B18).copy(alpha=.22f*fade))
                        val angle=((elapsed-180f)/640f).coerceIn(0f,1f).let { it*it*(3-2*it) }*90f*event.direction
                        drawCircle(Color(0xFF17293D).copy(alpha=.85f*fade),step*1.44f,center,style=Stroke(step*.105f))
                        drawCircle(color.copy(alpha=.65f*fade),step*1.44f,center,style=Stroke(step*.025f))
                        repeat(4) { i ->
                            val a=(angle+i*90f)*PI.toFloat()/180f
                            val pin=center+Offset(cos(a),sin(a))*step*1.44f
                            drawCircle(color.copy(alpha=fade),step*.07f,pin)
                            drawArc(color.copy(alpha=.7f*fade),angle+i*90f-22f,15f,false,
                                center-Offset(step*1.44f,step*1.44f),Size(step*2.88f,step*2.88f),style=Stroke(step*.045f))
                        }
                    }
                    if(!animationsOn || elapsed>=820f) drawRoundRect(color.copy(alpha=.6f*fade),
                        Offset((c.x-1)*step,(c.y-1)*step),Size(step*3,step*3),style=Stroke(step*.035f))
                }
                return@Canvas
            }
            if(event.kind=="reactor" || event.kind=="wrecking_ball") {
                if(animationsOn) event.wood.filter { it.second.teikaSeed }.forEach { (p,w) ->
                    val hit=event.hitTimes.getOrNull(event.cells.indexOf(p)) ?: 0
                    if(elapsed<hit) {
                        translate(mid(p).x,mid(p).y) { drawSeed(seedGeometry,step,0f,live=false) }
                        drawReactorSeedRim(p,step,blockSkin.colorsFor(w.colorType).first())
                    }
                }
            }
            if(event.kind=="wrecking_ball") event.ballArc?.let { arc->
                val end=180f+sqrt(arc.endFraction)*620f
                val swept=if(elapsed<180f) 0f else if(elapsed<=end) (((elapsed-180f)/620f).coerceIn(0f,1f).let { it*it }).coerceAtMost(arc.endFraction)
                    else (arc.endFraction-((elapsed-end)/320f).coerceIn(0f,1f)*.16f).coerceAtLeast(0f)
                val (x,y)=arc.point(swept)
                val c=Offset(x*step,y*step); val pivot=Offset(arc.x*step,arc.y*step)
                val fade=if(elapsed<180f) (elapsed/180f).coerceIn(0f,1f) else 1f-((elapsed-end-120f)/200f).coerceIn(0f,1f)
                // The taut linked chain and offset shadow make the load visibly heavy.
                drawLine(Color.Black.copy(alpha=.55f*fade),pivot+Offset(2f,3f),c+Offset(2f,3f),step*.075f)
                val links=(arc.radius*5).roundToInt().coerceIn(5,20)
                repeat(links) { i-> val node=pivot+(c-pivot)*(i+.5f)/links.toFloat()
                    drawOval(Color(0xFFBCCBDD).copy(alpha=.9f*fade),node-Offset(step*.065f,step*.045f),Size(step*.13f,step*.09f),style=Stroke(step*.025f)) }
                if(elapsed in 180f..end) repeat(4) { n->
                    val (tx,ty)=arc.point((swept-(n+1)*.02f).coerceAtLeast(0f))
                    drawCircle(color.copy(alpha=.04f*(4-n)),step*.48f,Offset(tx*step,ty*step)) }
                drawBall(c,step*.5f*(if(elapsed<180f) .85f+.15f*elapsed/180f else 1f),fade)
                event.cells.forEachIndexed { i,p->
                    val age=((elapsed-(event.hitTimes.getOrNull(i) ?: 180))/260f)
                    if(age in 0f..1f) {
                        val m=mid(p)
                        drawCircle(color.copy(alpha=(1-age)*.8f),step*(.20f+age*.55f),m,style=Stroke(step*.04f))
                        repeat(4) { n-> val angle=(n*PI/2+i*.7).toFloat()
                            val fragment=m+Offset(cos(angle),sin(angle))*step*(.12f+age*.75f)
                            drawCircle(color.copy(alpha=(1-age)*.85f),step*.045f,fragment) }
                    }
                }
                if(elapsed>end && arc.endFraction<1f) {
                    drawCircle(Color.White.copy(alpha=(1-(elapsed-end)/320f).coerceIn(0f,1f)),step*.6f,c,style=Stroke(step*.06f))
                }
            }
            if(event.kind=="ball_charge") event.cells.take(16).forEach { p->
                drawRoundRect(color.copy(alpha=(1-t)*.65f),Offset(p.x*step,p.y*step),Size(step,step),style=Stroke(step*.045f)) }
            if(event.kind=="portal_swap" && event.points.size==2) {
                val opening=(elapsed/160f).coerceIn(0f,1f)
                val fade=1f-((elapsed-780f)/180f).coerceIn(0f,1f)
                event.points.forEachIndexed { index,a ->
                    val tint=if(index==0) Color(0xFF6EE5FF) else Color(0xFFD0A0FF)
                    val c=Offset((a.x+1f)*step,(a.y+1f)*step)
                    val radius=step*.86f*FastOutSlowInEasing.transform(opening)
                    drawCircle(Brush.radialGradient(listOf(tint.copy(alpha=.22f*fade),Color.Transparent),c,step*1.15f),step*1.15f,c)
                    // The portal has a hollow centre, so the travelling stars and seeds stay readable.
                    drawCircle(tint.copy(alpha=.30f*fade),radius,c,style=Stroke(step*.12f))
                    drawCircle(tint.copy(alpha=.90f*fade),radius,c,style=Stroke(step*.035f))
                    repeat(3) { n ->
                        drawArc(Color.White.copy(alpha=.7f*fade),elapsed*.38f*(if(index==0) 1 else -1)+n*120f,48f,false,
                            c-Offset(radius,radius),Size(radius*2,radius*2),style=Stroke(step*.025f))
                    }
                    repeat(10) { n ->
                        val angle=(n*36f+elapsed*.18f)*(PI.toFloat()/180f)
                        val r=radius+step*(.055f+.075f*sin(elapsed*.012f+n))
                        drawCircle(tint.copy(alpha=.75f*fade),step*.022f,c+Offset(cos(angle),sin(angle))*r)
                    }
                    if(elapsed>=780f) {
                        val landing=((elapsed-780f)/180f).coerceIn(0f,1f)
                        drawRoundRect(tint.copy(alpha=(1-landing)*.75f),Offset(a.x*step,a.y*step),Size(step*2,step*2),style=Stroke(step*.055f))
                    }
                }
            }
            if(event.kind=="portal_swap_charge") event.cells.take(16).forEach { p ->
                drawRoundRect(Color(0xFF6EE5FF).copy(alpha=(1-t)*.65f),Offset(p.x*step,p.y*step),Size(step,step),style=Stroke(step*.045f))
            }
            if(event.kind=="compactor" && event.cells.isNotEmpty()) {
                val axis=if(event.direction%2==0) Offset(1f,0f) else Offset(0f,1f)
                val sign=if(event.direction<2) 1f else -1f
                val entry=mid(event.cells.first())-axis*sign*step*.7f
                // Compression follows the rearmost moved block, without entering a stationary body.
                val rear=event.moves.minByOrNull { (a,_) -> event.cells.indexOf(a) }
                val from=rear?.let { mid(it.first)-axis*sign*step*.53f } ?: entry
                val to=rear?.let { mid(it.second)-axis*sign*step*.53f } ?: entry
                val fp=((elapsed-180f)/620f).coerceIn(0f,1f)
                val drive=fp*fp*(3f-2f*fp) // exactly the WoodFall slide curve
                val retreat=FastOutSlowInEasing.transform(((elapsed-800f)/200f).coerceIn(0f,1f))
                val at=if(elapsed<180f) entry+(from-entry)*FastOutSlowInEasing.transform(elapsed/180f)
                    else from+(to-from)*drive-axis*sign*step*.35f*retreat
                val fade=1f-retreat
                val across=Offset(-axis.y,axis.x)
                drawLine(Color(0xFF101724).copy(alpha=fade),entry,at,step*.23f,StrokeCap.Round)
                drawLine(Color(0xFF8F9AAF).copy(alpha=fade),entry,at,step*.10f,StrokeCap.Round)
                drawLine(Color(0xFF202C43).copy(alpha=fade),at-across*step*.47f,at+across*step*.47f,step*.24f,StrokeCap.Round)
                drawLine(Color(0xFFFFC774).copy(alpha=fade),at-across*step*.40f,at+across*step*.40f,step*.08f,StrokeCap.Round)
                if(elapsed in 650f..920f) {
                    val hit=((elapsed-650f)/270f).coerceIn(0f,1f)
                    repeat(6) { n ->
                        val side=if(n%2==0) 1f else -1f
                        val start=to+across*side*step*.38f
                        val delta=across*side*step*(.15f+hit*.25f)+axis*sign*step*(n/2f-1f)*.08f
                        drawLine(Color(0xFFFFDF9C).copy(alpha=1f-hit),start+delta*.5f,start+delta,step*.025f,StrokeCap.Round)
                    }
                }
            }
            if(event.kind=="compactor_charge") event.cells.take(16).forEach { p ->
                drawRoundRect(Color(0xFFFFC774).copy(alpha=(1-t)*.65f),Offset(p.x*step,p.y*step),Size(step,step),style=Stroke(step*.05f))
            }
            if(event.kind=="constellation") {
                val fade=if(t>.78f) ((1-t)/.22f).coerceIn(0f,1f) else 1f
                val trace=event.cells
                trace.zipWithNext().forEachIndexed { i,(a,b) ->
                    val lit=elapsed>=RuleMotion.constellationHit(i,trace.size)
                    drawLine(color.copy(alpha=fade*(if(lit) .95f else .25f)),mid(a),mid(b),step*(if(lit) .055f else .022f),StrokeCap.Round)
                    if(lit) drawLine(color.copy(alpha=fade*.15f),mid(a),mid(b),step*.22f,StrokeCap.Round)
                }
                event.points.forEach { p ->
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE8AD).copy(alpha=.65f*fade),Color.Transparent),mid(p),step*.75f),step*.75f,mid(p))
                    drawCircle(Color(0xFFFFF0CA).copy(alpha=fade),step*.12f,mid(p))
                    // Four rays leave the star center visible instead of covering the whole board.
                    repeat(4) { n -> val angle=n*PI/2
                        val ray=Offset(cos(angle).toFloat(),sin(angle).toFloat())*step*(.22f+.15f*sin(t*PI).toFloat())
                        drawLine(color.copy(alpha=fade*.7f),mid(p)-ray,mid(p)+ray,step*.02f)
                    }
                }
                if(trace.isNotEmpty() && elapsed in 180f..600f) {
                    val at=((elapsed-180)/420*(trace.size-1)).coerceIn(0f,(trace.size-1).toFloat())
                    val a=mid(trace[at.toInt()]); val b=mid(trace[(at.toInt()+1).coerceAtMost(trace.lastIndex)])
                    drawCircle(Color.White,step*.10f,a+(b-a)*(at-at.toInt()))
                }
            }
            if(event.kind=="surf") {
                val launch=((elapsed-180)/600).coerceIn(0f,1f)
                val fade=if(t>.8f) (1-t)/.2f else 1f
                val spans=event.cells.groupBy { it.y }.values.flatMap { row ->
                    if(state.ninjaCutActive) row.groupBy { it.x<4 }.values else listOf(row)
                }
                spans.take(16).forEach { cells ->
                    val row=cells.first().y; val left=cells.minOf { it.x }*step; val right=(cells.maxOf { it.x }+1)*step
                    val floor=row*step
                    val front=if(event.direction>0) left+(right-left)*launch else right-(right-left)*launch
                    val wave=Path().apply {
                        moveTo(left,floor)
                        repeat(33) { i -> val x=left+(right-left)*i/32f; val offset=(x-front)/step
                            val swell=exp(-offset*offset*2)*step*.22f*fade
                            lineTo(x,floor-swell)
                        }
                    }
                    drawPath(wave,color.copy(alpha=.25f*fade),style=Stroke(step*.18f,cap=StrokeCap.Round))
                    drawPath(wave,Color(0xFFE6FAFF).copy(alpha=.85f*fade),style=Stroke(step*.04f,cap=StrokeCap.Round))
                    if(elapsed>=180) repeat(6) { n ->
                        val x=front-event.direction*n*step*.12f
                        if(x in left..right) drawCircle(color.copy(alpha=fade*(1-n/7f)),step*.025f,Offset(x,floor-step*(.12f+.06f*sin(t*12+n))))
                    }
                }
            }

            if(event.kind=="relay_charge") {
                val target=Offset(size.width*.5f,size.height-step*.08f)
                event.cells.distinct().filterIndexed { i,_ -> i%2==0 }.take(12).forEachIndexed { i,p ->
                    val f=((t-i*.025f)/.7f).coerceIn(0f,1f); val eased=f*f
                    val point=mid(p)+(target-mid(p))*eased
                    drawCircle(color.copy(alpha=(1-f)*.65f),step*.045f,point)
                    drawCircle(color.copy(alpha=(1-f)*.12f),step*.16f,point)
                }
            }
            if(event.kind=="turbine_charge") {
                val target=Offset(size.width*.5f,size.height-step*.08f)
                event.cells.distinct().filterIndexed { i,_ -> i%2==0 }.take(8).forEachIndexed { i,p ->
                    val f=((t-i*.03f)/.7f).coerceIn(0f,1f)
                    drawCircle(color.copy(alpha=(1-f)*.7f),step*.05f,mid(p)+(target-mid(p))*f*f)
                }
            }
            if(event.kind=="pinball_charge" && event.points.isNotEmpty()) {
                val start=Offset(event.points.map { it.x+.5f }.average().toFloat()*step,
                    event.points.map { it.y+.5f }.average().toFloat()*step)
                bodies?.value.orEmpty().filter { it.frozenTurns==0 && !it.kinematic && it.bubble==0 }.take(6).forEach { body ->
                    val destination=Offset(body.x*step,body.y*step)
                    drawLine(Color(0xFF88FFD1).copy(alpha=(1-t)*.2f),start,destination,step*.025f,StrokeCap.Round)
                    repeat(3) { i ->
                        val f=(t*1.2f-i*.08f).coerceIn(0f,1f)
                        drawCircle(Color(0xFF88FFD1).copy(alpha=1-t),step*(.055f-i*.01f),start+(destination-start)*f)
                    }
                }
            }
            if(event.kind=="relay") {
                val fade=if(t>.8f) (1-t)/.2f else 1f
                event.points.take(16).forEach { p ->
                    val f=(elapsed/180).coerceIn(0f,1f)
                    drawRoundRect(color.copy(alpha=fade*.35f),Offset((p.x+.06f)*step,(p.y+.06f)*step),Size(step*.88f,step*.88f),style=Stroke(step*(.04f+.02f*f)))
                    if(elapsed<240f) {
                        val charge=elapsed/240f
                        drawCircle(color.copy(alpha=.55f*charge),step*(.68f-.34f*charge),mid(p),style=Stroke(step*.045f))
                        drawCircle(Color.White.copy(alpha=.65f*charge),step*.055f,mid(p))
                    }
                }
                event.cells.forEachIndexed { i,p ->
                    val at=event.hitTimes.getOrNull(i) ?: RelayRules.hit(i)
                    val f=((elapsed-at+80)/160).coerceIn(0f,1f)
                    drawRoundRect(color.copy(alpha=fade*(1-f)*.8f),Offset((p.x+.04f)*step,(p.y+.04f)*step),Size(step*.92f,step*.92f),style=Stroke(step*.04f))
                }
                drawRelayAir(event.moves,event.direction,step,(elapsed-240f)/50f,fade)
            }

            if(event.kind=="reactor") {
                val energy=Color(0xFFFFCF72)
                event.moves.forEach { (a,b) ->
                    val index=event.cells.indexOf(b)
                    val hit=event.hitTimes.getOrNull(index) ?: 0
                    val f=((elapsed-hit+100)/100f).coerceIn(0f,1f)
                    val fade=(1-(elapsed-hit).coerceAtLeast(0f)/240f).coerceIn(0f,1f)
                    if(animationsOn && f>0f && fade>0f) {
                        val end=mid(a)+(mid(b)-mid(a))*f
                        drawLine(energy.copy(alpha=fade*.2f),mid(a),end,step*.22f,StrokeCap.Round)
                        drawLine(Color.White.copy(alpha=fade*.9f),mid(a),end,step*.04f,StrokeCap.Round)
                        drawCircle(energy,step*.07f,end)
                    }
                }
                event.cells.forEachIndexed { i,p ->
                    val hit=event.hitTimes.getOrNull(i) ?: 0
                    val charge=((elapsed-hit+180)/180f).coerceIn(0f,1f)
                    val age=elapsed-hit
                    if(animationsOn && age<0f) {
                        drawRoundRect(energy.copy(alpha=.32f*charge*charge),Offset(p.x*step,p.y*step),Size(step,step))
                        drawRoundRect(energy.copy(alpha=charge),Offset((p.x+.06f)*step,(p.y+.06f)*step),Size(step*.88f,step*.88f),style=Stroke(step*.035f))
                    }
                    if(age in 0f..280f) {
                        val f=age/280f; val fade=(1-f)*(1-f)
                        drawCircle(energy.copy(alpha=fade*.7f),step*(.18f+if(animationsOn) f*.55f else 0f),mid(p),style=Stroke(step*.065f*(1-f)))
                        drawCircle(Color.White.copy(alpha=fade*.75f),step*.30f*(1-f),mid(p))
                        if(animationsOn) repeat(4) { n ->
                            val angle=(n*PI/2+i*.35).toFloat()
                            val at=mid(p)+Offset(cos(angle)*step*f*.65f,sin(angle)*step*f*.65f+step*f*f*.22f)
                            val c=blockSkin.colorsFor(event.wood.getOrNull(i)?.second?.colorType ?: 0).first()
                            val width=step*.14f*(1-f*.6f)
                            drawRoundRect(c.copy(alpha=fade),at-Offset(width/2,width/2),Size(width,width))
                        }
                    }
                }
            }
            if(event.kind=="reactor_charge") event.cells.take(16).forEach { p ->
                drawRoundRect(Color(0xFFFFCF72).copy(alpha=(1-t)*.7f),Offset(p.x*step,p.y*step),Size(step,step),style=Stroke(step*.045f))
            }
            if(event.kind=="claw_charge") event.cells.take(16).forEach { p ->
                drawRoundRect(color.copy(alpha=(1-t)*.7f),Offset(p.x*step,p.y*step),Size(step,step),style=Stroke(step*.045f))
            }
            if(event.kind=="claw_land") event.clawLanding?.let { landing ->
                val c=Offset(landing.toX*step,landing.toY*step)
                drawCircle(color.copy(alpha=(1-t)*.7f),step*(landing.radius+t*.55f),c,style=Stroke(step*.035f))
                repeat(6) { i ->
                    val a=i*PI.toFloat()/3; val d=Offset(cos(a),sin(a))
                    drawLine(color.copy(alpha=1-t),c+d*step*(landing.radius+t*.4f),c+d*step*(landing.radius+t*.4f+.1f),step*.025f,StrokeCap.Round)
                }
            }
            if(event.kind.startsWith("trampoline")) event.cells.firstOrNull()?.let { p ->
                val y=(p.y+.76f)*step; val x=(p.x+.5f)*step
                val springT=(elapsed/660).coerceIn(0f,1f)
                val flex=sin(springT*PI.toFloat()*3)*step*.21f*(1-springT)
                val spring=Path().apply { moveTo(x-step*.36f,y); quadraticTo(x,y+flex,x+step*.36f,y) }
                drawPath(spring,color,style=Stroke(step*.065f,cap=StrokeCap.Round))
                if(event.kind=="trampoline_charge") event.points.take(8).forEach { from ->
                    val start=mid(from); val end=mid(p); val f=t.coerceIn(0f,1f)
                    drawCircle(color.copy(alpha=1-t),step*.04f,start+(end-start)*f)
                }
            }
            event.cells.distinct().take(64).forEach { p ->
                val index=event.cells.indexOf(p); val hit=event.hitTimes.getOrNull(index) ?: 0
                val age=elapsed-hit
                if(age in 0f..190f && !event.kind.startsWith("cut")) {
                    val f=age/190; val center=mid(p)
                    repeat(if(animationsOn) 4 else 1) { n ->
                        val angle=(n*PI/2+index*.35).toFloat()
                        val drift=if(animationsOn) Offset(cos(angle),sin(angle))*step*f*.4f else Offset.Zero
                        drawCircle(color.copy(alpha=(1-f)*.8f),step*.025f*(1-f*.5f),center+drift)
                    }
                }
            }
        }
    } }
}

/** A fixed overlay: charging never changes the tray's measured size or consumes a board event. */
@Composable
fun RelayTrayGlow(modifier: Modifier=Modifier,animationsOn: Boolean=true) {
    val reveal=remember { Animatable(0f) }
    LaunchedEffect(Unit) { reveal.animateTo(1f,tween(if(animationsOn) 380 else 80)) }
    val flow=if(animationsOn) androidx.compose.animation.core.rememberInfiniteTransition(label="relay_ready")
        .animateFloat(0f,1f,androidx.compose.animation.core.infiniteRepeatable(tween(1300,easing=androidx.compose.animation.core.LinearEasing)),label="relay_current") else null
    Canvas(modifier) {
        val color=Color(0xFFFFB3DB); val edge=minOf(size.width,size.height)*.035f
        val inset=edge*1.5f
        val alpha=.45f+reveal.value*.35f
        drawRoundRect(color.copy(alpha=alpha*.18f),Offset(inset,inset),Size(size.width-inset*2,size.height-inset*2),
            cornerRadius=androidx.compose.ui.geometry.CornerRadius(edge*4),style=Stroke(edge*2))
        drawRoundRect(color.copy(alpha=alpha),Offset(inset,inset),Size(size.width-inset*2,size.height-inset*2),
            cornerRadius=androidx.compose.ui.geometry.CornerRadius(edge*4),style=Stroke(edge*.5f))
        repeat(3) { n ->
            val f=((flow?.value ?: .5f)+n/3f)%1f
            val x=inset+(size.width-inset*2)*f
            drawLine(color.copy(alpha=alpha*.55f),Offset(x-edge*3,size.height-inset),Offset(x,size.height-inset),edge*.7f,StrokeCap.Round)
            drawCircle(Color.White.copy(alpha=alpha),edge*.6f,Offset(x,size.height-inset))
        }
    }
}

/** The engine freezes this same shot before normal clears; no surprise redirection after release. */
@Composable
fun RelayDragPreview(state: GameState,dragState: DragState,step: Float,blockedBodies: Set<Position>,modifier: Modifier=Modifier) {
    if(!RelayRules.appliesTo(state.level) || !state.relayCharged) return
    val drag=dragState as? DragState.Dragging ?: return
    val at=drag.ghostGridPosition ?: return
    if(!drag.isValidPlacement) return
    val footprint=(drag.piece.shape.map { Position(at.x+it.x,at.y+it.y) }+drag.starFillCells).toSet()
    val blocked=state.unplaceableCells+state.gluttonDeadCells.keys+state.hiddenWoodCells+state.frozenCells+state.glacierFrostCells+state.starAstroTargetCells+state.starAstroMoonWoodCells+blockedBodies+
        state.grid.keys.filter { state.lavaActive && it.y<state.lavaRow }+
        drag.energizeRows.flatMap { y -> (0 until state.gridW).map { Position(it,y) } }
    val cleared=drag.clearRowSpans.flatMap { (y,xs) -> xs.map { Position(it,y) } }.toSet()+
        drag.clearCols.flatMap { x -> (0 until state.gridH).map { Position(x,it) } }
    val shot=RelayRules.plan(footprint,RelayRules.afterAnnouncedClear(state.grid,cleared,blocked),blocked,state.gridW,state.gridH,if(state.ninjaCutActive) 4 else -1)
    Canvas(modifier) {
        drawRelayAir(shot.paths.filter { it.size>1 }.map { it.first() to it.last() },shot.direction,step,8f,.28f)
        shot.targets.forEach { target -> val p=target.cell
        drawRoundRect(Color(0xFFFFB3DB).copy(alpha=.8f),Offset((p.x+.08f)*step,(p.y+.08f)*step),Size(step*.84f,step*.84f),style=Stroke(step*.035f))
    } }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRelayAir(
    lanes: List<Pair<Position,Position>>,direction: Int,step: Float,distance: Float,alpha: Float,
) {
    if(distance<0) return
    val d=DominoRules.steps.getOrElse(direction) { Position(1,0) }
    val delta=Offset(d.x.toFloat(),d.y.toFloat()); val across=Offset(-delta.y,delta.x)
    val color=Color(0xFFFFB3DB)
    for((a,b) in lanes.take(8)) {
        val length=(kotlin.math.abs(a.x-b.x)+kotlin.math.abs(a.y-b.y)).toFloat()
        val start=Offset((a.x+.5f)*step,(a.y+.5f)*step)
        val head=start+delta*step*distance.coerceIn(.5f,length+.45f)
        drawLine(color.copy(alpha=alpha*.13f),start,head,step*.88f,StrokeCap.Butt)
        drawLine(color.copy(alpha=alpha*.25f),head-across*step*.48f,head+across*step*.48f,step*.20f,StrokeCap.Round)
        drawLine(Color.White.copy(alpha=alpha*.9f),head-across*step*.45f,head+across*step*.45f,step*.035f,StrokeCap.Round)
        repeat(3) { n ->
            val center=head-delta*step*(.3f+n*.22f)+across*step*((n-1)*.23f)
            drawLine(color.copy(alpha=alpha*(.7f-n*.15f)),center-delta*step*.55f,center,step*.035f,StrokeCap.Round)
        }
    }
}

@Composable
fun PrecisionCutControls(canCut: Boolean, reserve: Piece?, onCut: () -> Unit, onSwap: () -> Unit, modifier: Modifier = Modifier, choices: Int = 1, onChoose: () -> Unit = {}) {
    val reveal = remember { Animatable(1f) }
    LaunchedEffect(reserve?.id) {
        if (reserve != null) { reveal.snapTo(.92f); reveal.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) }
    }
    Row(modifier.graphicsLayer { scaleX = reveal.value; scaleY = reveal.value }.heightIn(min = 40.dp).background(Color(0xF0121930), RoundedCornerShape(10.dp)).padding(6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        if (reserve != null) {
            Box(Modifier.size(28.dp).clickable(role=Role.Button,onClickLabel="Elegir la mitad reservada",onClick=onSwap),contentAlignment=Alignment.Center) {
                PieceView(reserve,isDragging=false,cellSize=28.dp/maxOf(reserve.width,reserve.height))
            }
            Text("Cambiar",color=Color(0xFFBFEFFF),fontSize=10.sp,
                modifier=Modifier.clickable(role=Role.Button,onClickLabel="Elegir la otra mitad",onClick=onSwap))
        } else if (canCut) {
            if(choices>1) Text("↔",color=Color(0xFFBFEFFF),fontSize=16.sp,
                modifier=Modifier.clickable(role=Role.Button,onClickLabel="Ver el otro corte",onClick=onChoose).padding(3.dp))
            Text("Cortar", color = Color(0xFFBFEFFF), fontSize = 11.sp,
            modifier = Modifier.clickable(role = Role.Button, onClickLabel = "Dividir ficha en dos mitades", onClick = onCut))
        }
    }
}

/** Shows the exact partition before the player commits the free cut. */
@Composable
fun PrecisionCutGuide(piece: Piece, cellSize: Dp, modifier: Modifier = Modifier, choice: Int = 0) {
    val cut = remember(piece.shape,choice) { PrecisionCut.separator(piece,choice) } ?: return
    Canvas(modifier.requiredSize(cellSize * piece.width, cellSize * piece.height)) {
        val step = size.width / piece.width
        val (axis, boundary) = cut
        val a = if (axis == 0) Offset(boundary * step, 0f) else Offset(0f, boundary * step)
        val b = if (axis == 0) Offset(boundary * step, size.height) else Offset(size.width, boundary * step)
        drawLine(Color(0xFF071525), a, b, step * .11f)
        drawLine(Color(0xFFBFEFFF), a, b, step * .055f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(step * .12f, step * .08f)))
    }
}

/** Observes the board-owned event; it never consumes it or awards anything. */
@Composable
fun PrecisionCutReveal(event: GameEvent.RulePulse, current: Piece, cellSize: Dp, modifier: Modifier = Modifier, animationsOn: Boolean = true) {
    val original=event.originalPiece ?: return
    val t=remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) { t.animateTo(1f,tween(if(animationsOn) event.durationMs else 120, easing=FastOutSlowInEasing)) }
    BoxWithConstraints(modifier,contentAlignment=Alignment.Center) {
        val oldStep=minOf(cellSize,maxWidth/(original.width+1),maxHeight/(original.height+1))
        val step=with(LocalDensity.current) { oldStep.toPx() }
        if(event.kind=="cut_swap") {
            PieceView(original,isDragging=false,cellSize=oldStep,modifier=Modifier.graphicsLayer { if(animationsOn) translationX=-size.width*t.value*.4f; alpha=1-t.value })
            PieceView(current,isDragging=false,cellSize=cellSize,modifier=Modifier.graphicsLayer { if(animationsOn) translationX=size.width*(1-t.value)*.4f; alpha=t.value })
        } else {
            val (axis,boundary)=event.separator ?: return@BoxWithConstraints
            repeat(2) { half ->
                val clip=remember(original.shape,event.separator,half) { GenericShape { size,_ ->
                    val cut=if(axis==0) size.width*boundary/original.width else size.height*boundary/original.height
                    val left=if(axis==0 && half==1) cut else 0f
                    val top=if(axis==1 && half==1) cut else 0f
                    val right=if(axis==0 && half==0) cut else size.width
                    val bottom=if(axis==1 && half==0) cut else size.height
                    moveTo(left,top); lineTo(right,top); lineTo(right,bottom); lineTo(left,bottom); close()
                } }
                PieceView(original,isDragging=false,cellSize=oldStep,modifier=Modifier.graphicsLayer {
                    shape=clip; this.clip=true
                    val separation=((t.value-.25f)/.45f).coerceIn(0f,1f)
                    val shift=step*.3f*separation*(if(half==0) -1 else 1)
                    if(animationsOn) { if(axis==0) translationX=shift else translationY=shift; rotationZ=separation*(if(half==0) -2f else 2f) }
                    alpha=if(t.value<.65f) 1f else ((1-t.value)/.35f).coerceIn(0f,1f)
                })
            }
            PieceView(current,isDragging=false,cellSize=cellSize,modifier=Modifier.graphicsLayer { alpha=((t.value-.65f)/.35f).coerceIn(0f,1f) })
            Canvas(Modifier.size(oldStep*original.width,oldStep*original.height)) {
                val progress=if(animationsOn) (t.value/.5f).coerceIn(0f,1f) else 1f
                val from=if(axis==0) Offset(boundary*step,0f) else Offset(0f,boundary*step)
                val end=if(axis==0) Offset(boundary*step,size.height*progress) else Offset(size.width*progress,boundary*step)
                val alpha=(1-t.value).coerceIn(0f,1f)
                drawLine(Color(0xFF70E3FF).copy(alpha=alpha*.2f),from,end,step*.20f,StrokeCap.Round)
                drawLine(Color.White.copy(alpha=alpha),from,end,step*.035f,StrokeCap.Round)
                if(animationsOn) repeat(6) { i ->
                    val angle=i*PI/3; val drift=Offset(cos(angle).toFloat(),sin(angle).toFloat())*step*.14f
                    drawCircle(Color(0xFF70E3FF).copy(alpha=alpha),step*.025f,end+drift)
                }
            }
        }
    }
}

/** Follows committed physics snapshots, never predicts a landing or moves a body. */
@Composable
private fun RuleBodyWake(event: GameEvent.RulePulse, bodies: kotlinx.coroutines.flow.StateFlow<List<com.cukiti.teikafusion.viewmodel.TeikaBody>>, step: Float, bodyId: String? = event.bodyId, color: Color = Color(0xFF74FFBB), opacity: () -> Float) {
    val trail=remember(event.id) { androidx.compose.runtime.mutableStateListOf<Offset>() }
    val landing=remember(event.id) { mutableStateOf<Offset?>(null) }
    val flash=remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id) {
        var previous: com.cukiti.teikafusion.viewmodel.TeikaBody? = null
        bodies.collect { snapshot ->
            val body=snapshot.firstOrNull { it.id==bodyId } ?: return@collect
            val point=Offset(body.x*step,body.y*step)
            if (trail.lastOrNull()?.let { (it-point).getDistance() > step*.04f } != false) {
                trail.add(point); if(trail.size>10) trail.removeAt(0)
            }
            if (landing.value==null && previous?.vy?.let { it>1f }==true && body.vy<=.4f) {
                landing.value=Offset(body.x*step,(body.y+body.radius)*step)
                launch { flash.snapTo(1f); flash.animateTo(0f,tween(200)) }
            }
            previous=body
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        trail.zipWithNext().forEachIndexed { i,(a,b) -> drawLine(color.copy(alpha=(.05f+.3f*i/10f)*opacity()),a,b,step*.025f,StrokeCap.Round) }
        landing.value?.let { p -> drawCircle(color.copy(alpha=flash.value*.65f),step*(.14f+(1-flash.value)*.4f),p,style=Stroke(step*.035f)) }
    }
}
