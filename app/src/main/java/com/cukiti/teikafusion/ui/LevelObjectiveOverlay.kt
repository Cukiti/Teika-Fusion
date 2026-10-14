package com.cukiti.teikafusion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.ChapterArc
import com.cukiti.teikafusion.model.LevelDirector
import com.cukiti.teikafusion.model.LevelObjective
import com.cukiti.teikafusion.model.LevelVariety
import com.cukiti.teikafusion.model.TargetColor
import com.cukiti.teikafusion.model.TeikaType
import com.cukiti.teikafusion.model.TutorialCampaign

private val ObjectivePanel = Color(0xFF11182F)
private val ObjectivePanelDeep = Color(0xFF080D1E)
private val ObjectiveGold = Color(0xFFFFD45C)

/**
 * Confirmación previa al nivel abierta exclusivamente desde JUGAR en Inicio.
 *
 * Vive en la jerarquía Compose (sin Dialog/Popup), por lo que conserva la ventana inmersiva. La
 * partida aún no existe mientras esta pantalla está abierta: elegir compañero o volver atrás no
 * consume vidas, no registra un intento y no dispara la presentación automática del HUD.
 *
 * ## Rediseño 2026-08-26 (pedido del usuario), afinado el 26b
 *
 * De arriba abajo y **sin nada que leer que no haga falta**:
 *
 *  1. **Qué nivel es** → el número, grande, con su bioma, su nombre y su regla de la casa (que se
 *     toca para leerla, P-422). (El medallón de TURNOS se retiró el 2026-09-23.)
 *  2. **Qué me pide** → las estrellas por color y el MEGA.
 *  3. **Qué me llevo** → «LLEVAS A LA PARTIDA», los tres power-ups (P-282). (Las cartas del TRATO,
 *     P-74, ya no están.)
 *
 * (El punto 0 era **el ARCO de estrellas**, tres estrellas huecas en arcoíris abriendo el panel. Se
 * retiró el 2026-08-30 a petición del usuario: ocupaba ~72 dp para no decir nada que el jugador
 * pueda usar —cuánto cuesta cada estrella se ve DENTRO del nivel, en la barra de meta— y ese espacio
 * hacía falta para que el **botón de JUGAR** dejara de verse pequeño. `StarArc` y su `buildStar` se
 * borraron con él en vez de quedarse como código muerto.)
 *
 * (El punto 4, **con quién juego**, era el carrusel de compañeros. Se retiró el 2026-08-28 porque no
 * decidía nada —se podían lanzar los 14 igualmente— y esa elección vive ahora en el cinturón de la
 * Sala de Preparación, donde sí tiene consecuencias. `CompanionCarousel` y `CompanionSelectSheet`
 * se borraron el 2026-08-29 al quedarse sin un solo call site.)
 *
 * **Sin scroll (26b, decisión del usuario).** La ventana entera cabe en la ventana: las partes fijas
 * miden lo que miden y **el hueco del carrusel se lo queda el objetivo**, en vez de empujar el botón
 * de JUGAR fuera de cuadro. Por debajo de [COMPACT_HEIGHT] el panel entra además en modo compacto
 * (arco, número y estrellas más pequeños).
 *
 * **Lo que se quitó, y por qué**: el cartel con la regla del bioma. Existía —recuperaba
 * `mechanicHint`, que llevaba meses sin lector— pero el usuario lo retiró el mismo día: era **mucho
 * texto antes de jugar**, y la gracia de un bioma nuevo es descubrir su regla jugando. Hoy
 * `mechanicHint` se lee dentro de la partida, en «¿Cómo funciona este nivel?» de la pausa ([LevelRules]).
 *
 * ## Rendimiento
 *
 * Dos cosas se pagan aquí y se cuidan:
 *
 *  - **El reloj de apertura se lee SOLO en dibujo** (`drawBehind` y `graphicsLayer`). Leerlo en el
 *    cuerpo recompondría el panel entero —carrusel incluido— en los 340 ms de la animación.
 *  - **Los turnos se calculan UNA vez** (`remember`). Costaban 218 ms por la recursión del suelo
 *    monótono de [LevelBudget], que reconstruía 289 niveles enteros; hoy son ~2 ms.
 */
@Composable
fun LevelObjectiveOverlay(
    level: Int,
    objective: LevelObjective?,
    reducedFx: Boolean,
    // (RETIRADO 2026-08-29) Con `onSelectCompanion` se van `claimedCharacterIds` y
    // `selectedCompanionId`: los tres alimentaban al carrusel y ninguno se leía ya.
    // (RETIRADO 2026-08-29) `onSelectCompanion` seguía en la firma un año después de que el
    // carrusel se fuera de este panel: el padre le pasaba un callback que nadie llamaba. Elegir
    // compañero se hace ahora en la Sala de Preparación (pestaña de Inventario), donde el cinturón
    // le da consecuencias.
    onUiClick: () -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
    /**
     * **SALTAR EL TUTORIAL** (2026-09-01, decisión del usuario: saltable desde la primera lección).
     * `null` fuera del tutorial. Vive aquí, en el panel de apertura de la lección, y no sobre el
     * tablero: es una decisión que se toma ANTES de jugar, y ponerla encima del juego habría
     * añadido un botón permanente al único sitio de la app donde el dedo ya está ocupado.
     */
    onSkipTutorial: (() -> Unit)? = null,
    /**
     * **LLEVA A LA PARTIDA** (P-282, 2026-09-24): el selector de power-ups. `null` = sin selector
     * (tutorial, o el jugador aún no tiene ningún compañero). Ver [MatchPowerUpPicker].
     */
    powerUps: PowerUpPickerModel? = null,
) {
    val tutorialSpec = remember(level) { TutorialCampaign.spec(level) }
    val isTutorial = tutorialSpec != null
    val accent = levelAccentColor(level)
    val entry = remember(reducedFx) { Animatable(if (reducedFx) 1f else 0f) }
    var playCommitted by remember(level) { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!reducedFx) {
            entry.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 340,
                    easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
                ),
            )
        }
    }
    BackHandler { onDismiss() }

    val density = LocalDensity.current
    val panelLift = with(density) { 18.dp.toPx() }
    val dismissSource = remember { MutableInteractionSource() }
    val panelSource = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black, alpha = 0.80f * entry.value) }
            .clickable(
                interactionSource = dismissSource,
                indication = null,
                onClick = onDismiss,
            )
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Por debajo de este alto el panel se APRIETA: sin scroll, la única forma de que quepa todo
        // en una pantalla corta es que las partes fijas midan menos.
        val compact = maxHeight < COMPACT_HEIGHT
        // El panel ocupa el alto disponible (así el carrusel tiene sitio y nada necesita scroll),
        // pero con un techo: en una tablet, estirarlo hasta el borde convertiría el carrusel en un
        // mural y el botón de JUGAR en una franja.
        // Solo crece con el selector de power-ups dentro: el tutorial y quien aún no tiene compañeros
        // conservan el panel de siempre, sin un hueco vacío en medio.
        val hasPicker = !isTutorial && powerUps != null && powerUps.available.isNotEmpty()
        val panelHeight = minOf(maxHeight - 24.dp, if (hasPicker) MAX_PANEL_HEIGHT_WITH_PICKER else MAX_PANEL_HEIGHT)
        // P-288 (2026-09-25, playtest): el panel mide lo que su contenido, con [panelHeight] de techo.
        // Antes tenía ese alto FIJO y lo rellenaba con dos huecos elásticos (bajo el objetivo y sobre
        // JUGAR) que en un móvil normal eran ~130 dp de nada.
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = panelHeight)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val t = entry.value
                    alpha = t
                    scaleX = 0.94f + 0.06f * t
                    scaleY = 0.94f + 0.06f * t
                    translationY = panelLift * (1f - t)
                }
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accent.copy(alpha = 0.36f),
                            ObjectivePanel,
                            ObjectivePanelDeep,
                        )
                    )
                )
                .border(1.5.dp, accent.copy(alpha = 0.78f), RoundedCornerShape(30.dp))
                // Intercepta el toque para que el fondo sólo cierre fuera de la tarjeta.
                .clickable(interactionSource = panelSource, indication = null) { }
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LevelHeader(
                level = level,
                isTutorial = isTutorial,
                accent = accent,
                compact = compact,
                // (RETIRADO 2026-09-23) El MEDALLÓN de turnos: ya no se pierde por turnos.
            )

            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))

            if (isTutorial) {
                TutorialObjectiveCard(
                    title = tutorialSpec?.title.orEmpty(),
                    instruction = tutorialSpec?.instruction.orEmpty(),
                    requiresMega = tutorialSpec?.requiresMega == true,
                    accent = accent,
                )
            } else {
                RegularObjectiveCard(
                    objective = objective,
                    requiresMega = ChapterArc.requiresMega(level),
                    accent = accent,
                    compact = compact,
                )

                // (RETIRADO 2026-08-28) El CARRUSEL de compañeros vivía aquí y no decidía nada: se
                // podían lanzar los 14. Vuelve una elección aquí el 2026-09-24 (P-282), y esta SÍ
                // decide: solo los tres elegidos aparecen en el botón de power-ups de la partida.
                if (hasPicker && powerUps != null) {
                    Spacer(Modifier.height(if (compact) 12.dp else 18.dp))
                    MatchPowerUpPicker(
                        available = powerUps.available,
                        selected = powerUps.selected,
                        charges = powerUps.charges,
                        teikoins = powerUps.teikoins,
                        accent = accent,
                        compact = compact,
                        onToggle = powerUps.onToggle,
                        onBuy = powerUps.onBuy,
                        onUiClick = onUiClick,
                    )
                }
            }

            Spacer(Modifier.height(if (compact) 12.dp else 16.dp))
            PlayLevelButton(
                level = level,
                accent = accent,
                reducedFx = reducedFx,
                enabled = !playCommitted,
                compact = compact,
                onClick = {
                    if (!playCommitted) {
                        playCommitted = true
                        onPlay()
                    }
                },
            )
            if (onSkipTutorial != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "SALTAR TUTORIAL",
                    color = Color.White.copy(alpha = 0.55f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) {
                            onUiClick()
                            onSkipTutorial()
                        }
                        .padding(vertical = 10.dp)
                        .semantics { contentDescription = "Saltar el tutorial y jugar el primer nivel" },
                )
            }
        }

        // La × va SUELTA en la esquina del panel, no dentro de la fila de la cabecera: ahí competía
        // por el sitio con el medallón de turnos y obligaba a encoger uno de los dos.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.30f))
                .clickable(role = Role.Button) {
                    onUiClick()
                    onDismiss()
                }
                .semantics { contentDescription = "Cerrar objetivo del nivel" },
            contentAlignment = Alignment.Center,
        ) {
            Text("×", color = Color.White.copy(alpha = 0.85f), fontSize = 23.sp, fontWeight = FontWeight.Bold)
        }
        }
    }
}

/** Alto de panel por debajo del cual todo se aprieta para que quepa sin scroll. */
private val COMPACT_HEIGHT: Dp = 620.dp

/** Techo del panel: en pantallas muy altas deja de crecer en vez de estirarse hasta el borde. */
// Historia del número, porque cada cambio tuvo su motivo medido:
//   760 → 470 (2026-08-28) al retirarse el CARRUSEL de compañeros, para el que se había dimensionado.
//   470 → 545 → 580 (P-74, 2026-08-30) para alojar la fila de cartas del TRATO: contado sobre el
//     alto fijo real, quedaban 17 dp de holgura y la fila nueva empujaba el botón fuera de cuadro.
//   580 → 540 (2026-08-30, pedido del usuario) al RETIRARSE el arco de estrellas: se liberan ~72 dp
//     y el botón de JUGAR se lleva 18 de ellos, así que el panel encoge con el contenido.
private val MAX_PANEL_HEIGHT: Dp = 540.dp

/** Con el selector «LLEVA A LA PARTIDA» dentro (P-282, 2026-09-24): +~185 dp a tamaño normal. */
private val MAX_PANEL_HEIGHT_WITH_PICKER: Dp = 720.dp

/** Lo que el panel necesita para pintar el selector de power-ups. */
class PowerUpPickerModel(
    val available: List<String>,
    val selected: List<String>,
    val charges: com.cukiti.teikafusion.model.CompanionCharges,
    val teikoins: Int,
    val onToggle: (String) -> Unit,
    val onBuy: (String) -> Boolean,
)

@Composable
private fun LevelHeader(
    level: Int,
    isTutorial: Boolean,
    accent: Color,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isTutorial) "LECCIÓN" else "NIVEL",
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.4.sp,
            )
            Text(
                // P-92: el número mostrado descuenta las siete lecciones. Una lección conserva el
                // suyo (ya se rotula «LECCIÓN» justo encima).
                text = "${com.cukiti.teikafusion.model.LevelMap.displayNumber(level)}",
                color = Color.White,
                fontSize = if (compact) 34.sp else 42.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp,
            )
            Text(
                text = if (isTutorial) "APRENDE JUGANDO" else levelBiomeLabel(level).uppercase(),
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.1.sp,
                maxLines = 1,
            )
            // ── P-83 · EL NIVEL TIENE NOMBRE (2026-08-30) ────────────────────────────────────────
            // `LevelDirector` calculaba un nombre para cada uno de los 293 niveles («Precisión II»)
            // y lo tiraba: ni `title` ni `brief` tenían un solo lector en la UI. Aquí se pinta el
            // nombre A SECAS — dos palabras, en la línea de debajo del bioma.
            //
            // **Solo el nombre, nunca el `brief`.** El cartel con la regla del bioma existió en este
            // panel y lo retiró el usuario el mismo día (26b): era mucho texto antes de jugar y la
            // gracia de un bioma es descubrir su regla jugando. Esto no reabre esa puerta — le pone
            // nombre propio a cada nivel sin explicar nada.
            //
            // En el TUTORIAL no sale: esos niveles ya se presentan con «LECCIÓN / APRENDE JUGANDO» y
            // su propia tarjeta didáctica.
            // P-80 · NIVEL CON GRAVEDAD: el aviso va PEGADO al nombre, en una sola palabra. Es el
            // único sitio donde el jugador puede enterarse antes de colocar la primera ficha, y
            // meterle un cartel aparte reabriría el «mucho texto antes de jugar» que el usuario
            // retiró en la 26b. Una palabra basta: la primera ficha explica el resto.
            if (!isTutorial) {
                // P-422 (2026-10-01): la etiqueta se puede TOCAR y despliega qué significa. Antes
                // «PRECISIÓN III · IMPLOSIÓN» no se explicaba hasta abrir la pausa dentro del nivel.
                var reglasAbiertas by remember(level) { mutableStateOf(false) }
                val explicacion = remember(level) {
                    buildList {
                        add(LevelDirector.identityFor(level).brief)
                        com.cukiti.teikafusion.model.HouseRule.forLevel(level)?.let { add(it.capsule.label + ".") }
                    }.filter { it.isNotBlank() }.joinToString(" ")
                }
                Text(
                    modifier = Modifier
                        .clickable(
                            enabled = explicacion.isNotBlank(),
                            role = androidx.compose.ui.semantics.Role.Button,
                            onClickLabel = if (reglasAbiertas) "Ocultar las reglas" else "Ver qué significa"
                        ) { reglasAbiertas = !reglasAbiertas },
                    text = remember(level) {
                        // El nombre del nivel (P-83) y, pegadas, las reglas de la casa que cambian
                        // en ESTE nivel: una palabra cada una. Es el único sitio donde el jugador
                        // puede enterarse antes de colocar la primera ficha, y un cartel aparte
                        // reabriría el «mucho texto antes de jugar» que se retiró en la 26b.
                        // (2026-09-27) La palabra la da el catálogo ([HouseRule.badge]): antes solo se
                        // nombraban la gravedad, el horizonte y la corriente, y 25 reglas entraban mudas.
                        val marcas = buildList {
                            add(LevelDirector.identityFor(level).name)
                            com.cukiti.teikafusion.model.HouseRule.forLevel(level)?.let { add(it.badge) }
                        }
                        marcas.joinToString(" · ")
                    }.uppercase() + if (explicacion.isNotBlank()) "  ⓘ" else "",
                    color = accent.copy(alpha = 0.92f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.6.sp,
                    maxLines = 1,
                )
                androidx.compose.animation.AnimatedVisibility(visible = reglasAbiertas) {
                    Text(
                        text = explicacion,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, accent: Color) {
    Text(
        text = text,
        color = accent,
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = 2.sp,
    )
}

@Composable
private fun TutorialObjectiveCard(
    title: String,
    instruction: String,
    requiresMega: Boolean,
    accent: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = 0.09f))
            .border(1.dp, accent.copy(alpha = 0.34f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("OBJETIVO DIDÁCTICO", color = ObjectiveGold, fontSize = 11.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(7.dp))
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            instruction,
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        if (requiresMega) {
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeikaIcon(TeikaType.MEGA_TEIKA, Modifier.size(48.dp))
                Spacer(Modifier.width(8.dp))
                Text("FORMA UN MEGA TEIKA", color = accent, fontSize = 13.sp, fontWeight = FontWeight.Black)
            }
        }
    }
}

@Composable
private fun RegularObjectiveCard(
    objective: LevelObjective?,
    requiresMega: Boolean,
    accent: Color,
    compact: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.05f))
                )
            )
            .border(1.dp, accent.copy(alpha = 0.38f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = if (compact) 10.dp else 13.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SectionLabel("TU OBJETIVO", ObjectiveGold)
        Spacer(Modifier.height(if (compact) 6.dp else 9.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = if (compact) 38.dp else 46.dp
            TargetColor.entries.forEach { color ->
                val amount = objective?.required?.get(color) ?: return@forEach
                // TalkBack decía «por 27, por 27, por 28» sin el color (análisis a fondo, 2026-10-01).
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "$amount estrellas ${targetColorNamePlural(color)}"
                    }
                ) {
                    TargetStar(color, Modifier.size(icon))
                    Text("×$amount", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
            if (requiresMega) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clearAndSetSemantics { contentDescription = "Formar un Mega Teika" }
                ) {
                    TeikaIcon(TeikaType.MEGA_TEIKA, Modifier.size(icon + 4.dp))
                    Text("MEGA", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
            }
        }
        if (!compact) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (requiresMega) "Recolecta las estrellas y forma un Mega Teika"
                       else "Recolecta todas las estrellas",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun PlayLevelButton(
    level: Int,
    accent: Color,
    reducedFx: Boolean,
    enabled: Boolean,
    compact: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = tween(if (reducedFx) 0 else 110),
        label = "playLevelPress",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (compact) 66.dp else 76.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.65f
            }
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(listOf(ObjectiveGold, accent)))
            .border(2.dp, Color.White.copy(alpha = 0.62f), RoundedCornerShape(24.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .semantics {
                contentDescription =
                    "Jugar ${com.cukiti.teikafusion.model.LevelMap.displayWord(level).lowercase()} ${com.cukiti.teikafusion.model.LevelMap.displayNumber(level)}"
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            RawImage(R.raw.ic_play, Modifier.size(if (compact) 20.dp else 23.dp))
            Text(
                text = "JUGAR ${com.cukiti.teikafusion.model.LevelMap.displayWord(level)} ${com.cukiti.teikafusion.model.LevelMap.displayNumber(level)}",
                color = ObjectivePanelDeep,
                fontSize = if (compact) 20.sp else 23.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp,
            )
        }
    }
}
