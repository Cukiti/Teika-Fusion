package com.cukiti.teikafusion.ui

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.remember
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.LeaderboardEntry
import com.cukiti.teikafusion.model.PlayerRank
import com.cukiti.teikafusion.ui.theme.AppBackground
import com.cukiti.teikafusion.ui.theme.NeonOrange
import com.cukiti.teikafusion.ui.theme.NeonPurple
import kotlinx.coroutines.launch

// (RETIRADA 2026-08-09, decisión del usuario) La pestaña **XP** y su parámetro `xpEntries`. El
// ranking global vuelve a ser UNA sola tabla —la de NIVEL, que es la vara del juego— sin selector
// de pestañas. Con ella se fue también su consulta: 50 documentos de Firestore por cada apertura
// del ranking que ahora nadie lee (ver `refreshXpLeaderboard`, eliminado del ViewModel).
@Composable
fun LeaderboardScreen(
    entries: List<LeaderboardEntry>,
    isLoading: Boolean,
    isError: Boolean,
    currentUid: String?,
    onClose: () -> Unit,
    onRetry: () -> Unit = {},
    requiresLink: Boolean = false,
    onConnectGoogle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Atrás = cerrar el ranking (antes el atrás del sistema salía de la app con el overlay abierto).
    androidx.activity.compose.BackHandler { onClose() }
    Surface(
        modifier = modifier.fillMaxSize(),
        color = AppBackground
    ) {
        // El fondo de los menús (degradado + orbes), no un azul plano.
        PremiumBackground(phase = { 0f }, accent = Color(0xFFFFB84D), modifier = Modifier.fillMaxSize())
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            // ── Top bar ────────────────────────────────────────────────────────
            // BARRA DE TÍTULO (2026-09-07). El «volver» era un `TextButton` desnudo: una flecha
            // suelta flotando sobre el fondo, sin cuerpo, que no se leía como un control. Ahora es
            // una placa redonda —el mismo idioma que la ✕ de Ajustes y del perfil— y el rótulo lleva
            // debajo un filete que separa la cabecera de la tabla.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(top = 18.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.13f), CircleShape)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Button,
                            onClick = onClose
                        )
                        .semantics { contentDescription = "Volver" },
                    contentAlignment = Alignment.Center
                ) {
                    RawImage(R.raw.ic_back, Modifier.size(20.dp))
                }
                // La cinta de las ventanas de juego (GameDialog): el ranking es un sitio de gloria,
                // no una tabla de datos.
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    RibbonTitle("Ranking global", accent = Color(0xFFFFB84D), accentDeep = Color(0xFFB8560B))
                }
                RawImage(R.raw.ic_leaderboard, Modifier.size(30.dp))
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(NeonPurple.copy(alpha = 0.45f), Color.Transparent)
                        )
                    )
            )

            // ── Bloqueado: el ranking exige cuenta respaldada ──────────────────
            // Un anónimo no tiene sesión de nube (no la creamos solo para leer el ranking): en vez de
            // una lista vacía, le ofrecemos vincular para competir.
            if (requiresLink) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CloudBackupPrompt(
                        title = "Compite en el ranking global",
                        subtitle = "Respalda tu cuenta con Google para aparecer en la tabla y guardar tu progreso en la nube. Sin respaldo, tu partida es solo local.",
                        onConnectGoogle = onConnectGoogle
                    )
                }
                return@Surface
            }

            // ── Loading indicator ──────────────────────────────────────────────
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = NeonPurple)
                }
                return@Surface
            }

            // ── Error state ────────────────────────────────────────────────────
            if (isError) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Apagado en remoto (kill-switch) no es «sin conexión»: decirlo mandaba al
                        // jugador a revisar un internet que funcionaba (auditoría 2026-10-01).
                        val rc by com.cukiti.teikafusion.data.RemoteConfigManager.snapshot.collectAsState()
                        Text(
                            if (!rc.leaderboardEnabled) "El ranking está en mantenimiento.\nVuelve en un rato."
                            else "Sin conexión.\nVerifica tu internet e inténtalo de nuevo.",
                            color = Color.Gray,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier
                                .background(NeonPurple.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp)
                        ) {
                            Text("Reintentar", color = NeonPurple, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
                return@Surface
            }

            // ── Empty state ────────────────────────────────────────────────────
            if (entries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Sin datos todavía.\n¡Sé el primero en la tabla!",
                        color = Color.Gray,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                }
                return@Surface
            }

            // ── EL PODIO + la lista (2026-09-23, pedido del usuario: «muy minimalista, sin
            // animaciones ni efectos»). Los tres primeros suben a un podio —plata, oro, bronce, en
            // ese orden, como en cualquier podio de verdad—, el resto va en lista, y tu fila queda
            // FIJA abajo si no estás en el podio: lo primero que busca cualquiera en un ranking es
            // dónde está él.
            // P-436: con puestos compartidos («1, 1, 3») el podio son los TRES PRIMEROS de la tabla
            // ya ordenada, no «los de puesto 1..3» — si no, dos empatados en el 1 dejaban un hueco.
            val podium = entries.take(3)
            val rest = entries.drop(3)
            val me = entries.firstOrNull { it.uid == currentUid }
            val listState = rememberLazyListState()
            // La fila fija solo si la tuya NO se ve ya en la lista: repetida a la vista es ruido.
            val meVisible by remember(me?.uid) {
                derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == me?.uid } }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "podio") { Podium(podium, currentUid) }
                itemsIndexed(rest, key = { _, e -> e.uid }) { index, entry ->
                    LeaderboardRow(
                        entry = entry,
                        isCurrentUser = entry.uid == currentUid,
                        enterDelayMs = 450 + 60 * index.coerceAtMost(10),
                    )
                }
            }
            if (me != null && me !in podium && !meVisible) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF0E1330))))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    LeaderboardRow(entry = me, isCurrentUser = true, enterDelayMs = 0)
                }
            }
        }
    }
}

/** «Nv. N» con el número que ve el jugador; «MÁX» quien terminó el juego, «Tutorial» quien no salió. */
private fun rankLevelLabel(level: Int): String = when {
    level > com.cukiti.teikafusion.model.LevelMap.totalLevels -> "MÁX"
    com.cukiti.teikafusion.model.LevelMap.isLesson(level) -> "Tutorial"
    else -> "Nv. ${com.cukiti.teikafusion.model.LevelMap.displayNumber(level)}"
}

private val Gold = Color(0xFFFFD740)
private val GoldDeep = Color(0xFFB8860B)
private val Silver = Color(0xFFCFD8DC)
private val SilverDeep = Color(0xFF78909C)
private val Bronze = Color(0xFFFFAB76)
private val BronzeDeep = Color(0xFFA0522D)

/**
 * El podio: 2.º a la izquierda, 1.º en el centro y más alto, 3.º a la derecha. Los pedestales suben
 * uno tras otro (bronce, plata, oro: el suspense acaba en el primero), y cada avatar cae sobre el
 * suyo cuando su pedestal ha llegado.
 */
@Composable
private fun Podium(podium: List<LeaderboardEntry>, currentUid: String?) {
    val reduced = LocalReducedFx.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        for (place in listOf(2, 1, 3)) {
            // El HUECO del podio (alto, metal) va por posición; el NÚMERO que se pinta es el puesto,
            // que puede ser compartido.
            val entry = podium.getOrNull(place - 1)
            PodiumColumn(
                place = place,
                entry = entry,
                isMe = entry != null && entry.uid == currentUid,
                reduced = reduced,
                modifier = Modifier.weight(if (place == 1) 1.15f else 1f)
            )
        }
    }
}

@Composable
private fun PodiumColumn(place: Int, entry: LeaderboardEntry?, isMe: Boolean, reduced: Boolean, modifier: Modifier) {
    val (tone, deep) = when (place) {
        1 -> Gold to GoldDeep
        2 -> Silver to SilverDeep
        else -> Bronze to BronzeDeep
    }
    val pedestal = when (place) { 1 -> 118.dp; 2 -> 88.dp; else -> 66.dp }
    val rise = remember { Animatable(if (reduced) 1f else 0f) }
    val drop = remember { Animatable(if (reduced) 1f else 0f) }
    // Orden de subida: bronce → plata → oro.
    val order = when (place) { 3 -> 0; 2 -> 1; else -> 2 }
    LaunchedEffect(Unit) {
        if (reduced) return@LaunchedEffect
        kotlinx.coroutines.delay(120L + order * 160L)
        rise.animateTo(1f, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessMediumLow))
        drop.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
    }
    // Cuenta del nivel hacia arriba cuando el avatar aterriza.
    val shownLevel = remember { Animatable(if (reduced) (entry?.level ?: 0).toFloat() else 0f) }
    LaunchedEffect(entry?.level) {
        val target = (entry?.level ?: 0).toFloat()
        if (reduced) shownLevel.snapTo(target)
        else { kotlinx.coroutines.delay(420L + order * 160L); shownLevel.animateTo(target, tween(700)) }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Avatar (+ corona y rayos para el primero).
        Box(
            modifier = Modifier
                .size(if (place == 1) 78.dp else 60.dp)
                .graphicsLayer {
                    translationY = (1f - drop.value) * -70.dp.toPx()
                    alpha = drop.value.coerceIn(0f, 1f)
                },
            contentAlignment = Alignment.Center
        ) {
            if (place == 1) SpinningRays(Gold, reduced, Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize(0.86f)
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(tone.copy(alpha = 0.35f), deep.copy(alpha = 0.35f))))
                    .border(2.5.dp, if (isMe) NeonPurple else tone, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (entry != null) TeikaIcon(type = entry.avatarType, modifier = Modifier.fillMaxSize(0.8f))
                else Text("?", color = Color.White.copy(alpha = 0.4f), fontSize = 22.sp, fontWeight = FontWeight.Black)
            }
            // Cada puesto lleva su medalla colgando sobre el avatar.
            RawImage(
                when (place) { 1 -> R.raw.ic_rank_gold; 2 -> R.raw.ic_rank_silver; else -> R.raw.ic_rank_bronze },
                Modifier.align(Alignment.TopCenter).offset(y = (-14).dp).size(if (place == 1) 26.dp else 22.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            entry?.displayName ?: "—",
            color = if (isMe) Color.White else Color(0xFFE3E9FF),
            fontSize = if (place == 1) 13.sp else 12.sp,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.graphicsLayer { alpha = drop.value.coerceIn(0f, 1f) }
        )
        if (entry != null) {
            Text(
                // La cuenta sube SOLO entre niveles de verdad: arrancaba en 1 y pasaba por «Tutorial».
                rankLevelLabel(
                    shownLevel.value.toInt()
                        .coerceAtLeast(com.cukiti.teikafusion.model.LevelMap.introLevels + 1)
                        .coerceAtMost(entry.level.coerceAtLeast(1))
                ),
                color = tone,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier
                    .graphicsLayer { alpha = drop.value.coerceIn(0f, 1f) }
                    .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        // El pedestal: sube desde el suelo. Cara con degradado del metal, brillo arriba y el número.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestal)
                .graphicsLayer {
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    scaleY = rise.value.coerceAtLeast(0.001f)
                }
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(Brush.verticalGradient(listOf(tone, deep)))
                .drawBehind {
                    drawRect(Color.White.copy(alpha = 0.28f), size = Size(size.width, 5.dp.toPx()))
                }
                .semantics(mergeDescendants = true) {
                    contentDescription = if (entry == null) "Puesto $place libre"
                    else "Puesto ${entry.rank}: ${entry.displayName}, ${rankLevelLabel(entry.level)}"
                },
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                "${entry?.rank ?: place}",
                color = Color.White,
                fontSize = if (place == 1) 40.sp else 30.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.padding(top = 6.dp),
                style = androidx.compose.ui.text.TextStyle(
                    shadow = androidx.compose.ui.graphics.Shadow(deep, androidx.compose.ui.geometry.Offset(0f, 4f), 0f)
                )
            )
        }
    }
}

@Composable
private fun LeaderboardRow(
    entry: LeaderboardEntry,
    isCurrentUser: Boolean,
    /** Retardo de la entrada en cascada; 0 = aparece ya (tu fila fija). */
    enterDelayMs: Int,
) {
    val reduced = LocalReducedFx.current
    val enter = remember { Animatable(if (reduced || enterDelayMs == 0) 1f else 0f) }
    LaunchedEffect(entry.uid) {
        if (enter.value < 1f) {
            kotlinx.coroutines.delay(enterDelayMs.toLong())
            enter.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
        }
    }

    val rowBg = if (isCurrentUser) Brush.horizontalGradient(listOf(Color(0xFF3A2F7A), Color(0xFF2A3560)))
    else Brush.horizontalGradient(listOf(Color(0xFF1C2346), Color(0xFF161B36)))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                translationX = (1f - enter.value) * 60.dp.toPx()
                alpha = enter.value.coerceIn(0f, 1f)
            }
            .clip(RoundedCornerShape(14.dp))
            .background(rowBg)
            .then(
                if (isCurrentUser)
                    Modifier.border(1.5.dp, NeonPurple.copy(alpha = 0.8f), RoundedCornerShape(14.dp))
                else Modifier.border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            )
            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // El puesto en una chapa, no un «#12» suelto.
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
                .semantics {
                    contentDescription = when (entry.rank) {
                        LeaderboardEntry.UNKNOWN_RANK -> "Puesto sin calcular"
                        LeaderboardEntry.RANK_BEYOND -> "Más allá del puesto 100"
                        else -> "Puesto ${entry.rank}"
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.text.BasicText(
                LeaderboardEntry.rankLabel(entry.rank),
                maxLines = 1,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(
                    minFontSize = 8.sp, maxFontSize = 14.sp, stepSize = 0.5.sp
                ),
                style = androidx.compose.ui.text.TextStyle(color = Color(0xFFCDD5F0), fontWeight = FontWeight.Black)
            )
        }
        TeikaIcon(type = entry.avatarType, modifier = Modifier.size(34.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.displayName,
                color = if (isCurrentUser) Color.White else Color(0xFFCDD5F0),
                fontSize = 14.sp,
                fontWeight = if (isCurrentUser) FontWeight.ExtraBold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            // Rango de jugador por XP. Entradas antiguas sin XP caen en "Novato".
            val playerRank = PlayerRank.get(entry.totalXp)
            Text(
                playerRank.name,
                color = Color(playerRank.colorArgb),
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        if (isCurrentUser) {
            Text(
                "TÚ",
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .background(NeonPurple, RoundedCornerShape(50))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            )
        }
        Text(
            rankLevelLabel(entry.level),
            color = NeonOrange,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier
                .background(NeonOrange.copy(alpha = 0.12f), RoundedCornerShape(50))
                .padding(horizontal = 9.dp, vertical = 3.dp)
        )
    }
}
