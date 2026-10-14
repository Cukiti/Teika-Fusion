package com.cukiti.teikafusion.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.model.*

@Composable
fun ReserveSlot(piece: Piece?, ready: Boolean, slotWidth: Dp, modifier: Modifier=Modifier, animationsOn: Boolean=true, waiting: Int=0) {
    val color=Color(0xFF9BE8E2)
    Column(modifier.fillMaxHeight().background(Color(0xFF172B3B),RoundedCornerShape(18.dp))
        .border(1.dp,color.copy(alpha=if(ready) .8f else .3f),RoundedCornerShape(18.dp)).padding(4.dp)
        .semantics { contentDescription="Reserva. " + if(ready) "Intercambio disponible" else "Intercambio usado en esta bandeja" },
        horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceEvenly) {
        Text("RESERVA",color=color,fontSize=9.sp,maxLines=1)
        Box(Modifier.fillMaxWidth().weight(1f),contentAlignment=Alignment.Center) {
            AnimatedContent(piece,transitionSpec={
                if(animationsOn) (slideInHorizontally(tween(420)) { -it }+fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(300)) { it }+fadeOut(tween(180)))
                else fadeIn(tween(120)) togetherWith fadeOut(tween(120))
            },label="reserveExchange") { held ->
                held?.let { PieceView(piece=it,isDragging=false,cellSize=(slotWidth-12.dp)/maxOf(it.width,it.height,3)) }
            }
        }
        Text(if(ready) "↔ 1" else "✓",color=color.copy(alpha=if(ready) 1f else .45f),fontSize=14.sp)
        if(waiting>0) Text("+$waiting",color=color,fontSize=9.sp)
    }
}

@Composable
fun ReserveExchangeButton(enabled: Boolean,onClick: () -> Unit,modifier: Modifier=Modifier) {
    Box(modifier.requiredSize(36.dp).background(Color(0xFF143C45).copy(alpha=if(enabled) .96f else .5f),RoundedCornerShape(12.dp))
        .border(1.dp,Color(0xFF9BE8E2).copy(alpha=if(enabled) .7f else .2f),RoundedCornerShape(12.dp))
        .semantics { contentDescription="Intercambiar esta ficha con la reserva" }
        .clickable(enabled=enabled,role=Role.Button,onClick=onClick),contentAlignment=Alignment.Center) {
        Text("↔",color=Color(0xFFBDFCF3).copy(alpha=if(enabled) 1f else .3f),fontSize=20.sp)
    }
}

/** Draw/layout layers animate only; RulePulse is consumed once on the board. */
@Composable
fun reserveArrivalModifier(event: GameEvent.RulePulse?,animationsOn: Boolean): Modifier {
    val progress=remember(event?.id) { Animatable(if(event==null || !animationsOn) 1f else 0f) }
    LaunchedEffect(event?.id,animationsOn) { if(event!=null) progress.animateTo(1f,tween(if(animationsOn) event.durationMs else 120)) }
    return Modifier.graphicsLayer {
        alpha=if(animationsOn) .3f+progress.value*.7f else 1f
        translationX=if(animationsOn) size.width*.45f*(1-progress.value) else 0f
        scaleX=if(animationsOn) .85f+progress.value*.15f else 1f; scaleY=scaleX
    }
}

@Composable
fun ReserveSwapGlow(event: GameEvent.RulePulse,modifier: Modifier=Modifier,animationsOn: Boolean=true) {
    val p=remember(event.id) { Animatable(0f) }
    LaunchedEffect(event.id,animationsOn) { p.animateTo(1f,tween(if(animationsOn) event.durationMs else 120)) }
    androidx.compose.foundation.Canvas(modifier) {
        val color=Color(0xFF9BE8E2); val t=p.value
        if(animationsOn) {
            val x=size.width*(1-t)
            drawLine(color.copy(alpha=(1-t)*.55f),androidx.compose.ui.geometry.Offset(x,0f),androidx.compose.ui.geometry.Offset(x,size.height),size.width*.06f)
            repeat(4) { n -> drawCircle(color.copy(alpha=(1-t)*.7f),size.width*.018f,androidx.compose.ui.geometry.Offset(x,size.height*(.2f+n*.2f))) }
        } else drawRoundRect(color.copy(alpha=(1-t)*.6f),style=androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
}
