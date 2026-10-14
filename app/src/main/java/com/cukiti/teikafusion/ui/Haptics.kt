package com.cukiti.teikafusion.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View

/** Primitivas hápticas abstractas (independientes de la API). Se mapean a `VibrationEffect`. */
enum class HPrim { TICK, CLICK, LOW_TICK, QUICK_RISE, SLOW_RISE, QUICK_FALL, SPIN, THUD }

/** Un paso de una secuencia háptica: primitiva, intensidad (0..1) y delay previo (ms). */
private class HStep(val prim: HPrim, val scale: Float, val delayMs: Int = 0)

/**
 * Mecánicas de bioma con firma háptica propia (P-05). Una entrada por MOMENTO, no por bioma: el
 * aviso y el castigo de una misma mecánica se sienten distinto a propósito.
 */
enum class BiomeCue {
    CRYSTAL_CRACK, DROWSY,
    LAVA_SURGE,
    GLUTTON_OPEN, GLUTTON_BITE,
    METEOR_SPOTTED, METEOR_IMPACT, METEOR_DEFLECT,
    TOXIC_SPLASH,
    KING_WARNING, KING_STRIKE, KING_SHEATHE,
    PLANT_HURT,
    ASTRO_EMERGE, ASTRO_STRIKE, ASTRO_RETREAT
}

/**
 * Motor de vibración premium.
 *
 * Camino preferido (API 30+): `VibrationEffect.Composition` con primitivas (CLICK, TICK, RISE,
 * FALL, THUD, SPIN…) con `scale` 0..1 y delays NATIVOS → texturas y rampas reales, sin el jitter
 * de los `delay()` manuales. Fallback (API 26-29 o sin soporte de primitivas): waveform con
 * amplitudes si el equipo las controla, o un oneshot representativo.
 *
 * El escalado de intensidad (combo/fusión/línea) hace que la vibración crezca con la jugada
 * → sensación inmersiva. Respeta [enabled] (lo fija el ViewModel desde prefs). Requiere el permiso
 * `android.permission.VIBRATE`. Nunca crashea: cualquier fallo háptico se traga en silencio.
 */
object Haptics {
    @Volatile var enabled: Boolean = true

    private var vibrator: Vibrator? = null
    private var hasAmplitude = false
    private var supportsComposition = false
    private var primId: Map<HPrim, Int> = emptyMap()

    /** Inicializa el Vibrator y cachea capacidades UNA vez (llamar en Application.onCreate). */
    fun init(context: Context) {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator = v
        if (v == null || !v.hasVibrator()) return
        hasAmplitude = try { v.hasAmplitudeControl() } catch (_: Exception) { false }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Las constantes PRIMITIVE_* solo se tocan aquí, bajo guardas de API.
            val pairs = buildList {
                add(HPrim.CLICK      to VibrationEffect.Composition.PRIMITIVE_CLICK)
                add(HPrim.TICK       to VibrationEffect.Composition.PRIMITIVE_TICK)
                add(HPrim.QUICK_RISE to VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)
                add(HPrim.SLOW_RISE  to VibrationEffect.Composition.PRIMITIVE_SLOW_RISE)
                add(HPrim.QUICK_FALL to VibrationEffect.Composition.PRIMITIVE_QUICK_FALL)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(HPrim.LOW_TICK to VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
                    add(HPrim.SPIN     to VibrationEffect.Composition.PRIMITIVE_SPIN)
                    add(HPrim.THUD     to VibrationEffect.Composition.PRIMITIVE_THUD)
                }
            }
            val ids = pairs.map { it.second }.toIntArray()
            // WrongConstant es un FALSO POSITIVO aquí: cada id de [ids] sale de una constante
            // VibrationEffect.Composition.PRIMITIVE_* de la lista de arriba (bajo sus guardas de
            // API), pero lint no puede rastrearlas a través de List<Int> → IntArray → spread.
            @Suppress("WrongConstant")
            val sup = try { v.arePrimitivesSupported(*ids) } catch (_: Exception) { BooleanArray(ids.size) }
            primId = buildMap { pairs.forEachIndexed { i, (p, id) -> if (sup[i]) put(p, id) } }
            supportsComposition = primId.isNotEmpty()
        }
    }

    /** Legacy shim: vibración por constante de sistema (botones/UI no migrados). */
    fun perform(view: View, constant: Int) {
        if (enabled) view.performHapticFeedback(constant)
    }

    // ── Cues semánticos del juego ────────────────────────────────────────────────
    fun tap()         = play(listOf(HStep(HPrim.TICK, 0.45f)))
    fun place()       = play(listOf(HStep(HPrim.CLICK, 0.62f)))
    fun fichonaLift() = play(listOf(HStep(HPrim.SLOW_RISE, 0.60f), HStep(HPrim.THUD, 0.72f, 30)))
    fun fichonaDrag() = play(listOf(HStep(HPrim.LOW_TICK, 0.18f)))
    fun invalid()     = play(listOf(HStep(HPrim.THUD, 0.55f)))
    fun warning()     = play(listOf(HStep(HPrim.LOW_TICK, 0.60f), HStep(HPrim.LOW_TICK, 0.60f, 120)))
    /** El objetivo aterriza en el HUD: clic breve por estrella; subida + peso final para Mega. */
    fun objectiveLand(isMega: Boolean) = play(
        if (isMega) listOf(
            HStep(HPrim.QUICK_RISE, 0.68f),
            HStep(HPrim.THUD, 0.92f, 24),
        ) else listOf(HStep(HPrim.CLICK, 0.48f))
    )
    fun gameOver()    = play(listOf(HStep(HPrim.QUICK_FALL, 1f), HStep(HPrim.THUD, 0.85f, 10)))
    fun victory()     = play(listOf(HStep(HPrim.CLICK, 0.50f), HStep(HPrim.CLICK, 0.72f, 110), HStep(HPrim.CLICK, 1f, 110), HStep(HPrim.QUICK_RISE, 0.85f)))
    /** Fusión a MEGA: rampa que sube, gira y golpea (clímax). */
    /**
     * P-273 · el COLAPSO ESTELAR (T4+T4) en crescendo (decisión del usuario 2026-09-25): tres pulsos
     * cada vez más fuertes repartidos por los 1,1 s de la ceremonia. El golpe final no está aquí: es
     * [mega], que suena cuando el MEGA nace — así el crescendo desemboca en él.
     */
    fun megaCollapse() = play(listOf(
        HStep(HPrim.LOW_TICK, 0.35f, 250),
        HStep(HPrim.CLICK, 0.60f, 300),
        HStep(HPrim.THUD, 0.85f, 300),
    ))
    fun mega()        = play(listOf(HStep(HPrim.SLOW_RISE, 1f), HStep(HPrim.SPIN, 0.90f, 10), HStep(HPrim.THUD, 1f, 10)))
    /** Corte Ninja (Dojo): TAJO nítido de katana + el tablero cede en dos (disparada al impacto). */
    fun ninjaCut()    = play(listOf(HStep(HPrim.TICK, 1f), HStep(HPrim.THUD, 0.90f, 50)))
    /** Reparación kintsugi (Dojo): el oro sube por la costura y el sellado asienta con un clic. */
    fun ninjaRepair() = play(listOf(HStep(HPrim.SLOW_RISE, 0.60f), HStep(HPrim.CLICK, 0.85f, 30)))

    // ── P-01 (2026-07-31): tres momentos que tenían sonido y VFX propios y NINGUNA vibración.
    //    Son justo aquellos en los que el jugador GANA o PIERDE algo, que es cuando el móvil tiene
    //    que responder.

    /** Mansión Espectral: los fantasmas del espejo se DESVANECEN. Escalofrío: dos tics graves y flojos. */
    fun phantomFade() = play(listOf(HStep(HPrim.LOW_TICK, 0.28f), HStep(HPrim.LOW_TICK, 0.22f, 140)))
    /** Mansión Espectral: el espejo DESTELLA y suelta sus fantasmas. Tirón que sube y se hunde, dos veces. */
    fun mirrorFlash() = play(listOf(
        HStep(HPrim.SPIN, 0.55f), HStep(HPrim.QUICK_FALL, 0.85f, 40),
        HStep(HPrim.QUICK_FALL, 0.70f, 150)
    ))
    /* (ERRADICADAS 2026-08-09, con el modo WALLBLOCK) Las firmas hápticas del modo: `ceilingCrush`
     * (la prensa del techo), la CATAPULTA (`aimStep`, `slingRelease`), sus premios de color
     * (`monoBreak`, `rewardRowLand`) y los cinco rótulos hero del muro (`tideRecord`,
     * `tideMilestone`, `clutchEscape`, `tideAwaken`). */

    // ── FIRMAS DE BIOMA (P-05, 2026-07-31) ───────────────────────────────────────
    // Antes los 15 eventos de bioma se repartían DOS constantes del sistema (LONG_PRESS para todo
    // lo que amenaza, KEYBOARD_TAP para todo lo que alivia): el pisotón de un muro de piedra, el
    // mordisco del glotón y la modorra del bosque se sentían exactamente igual en la mano, mientras
    // cada compañero tenía su firma compuesta. Aquí cada mecánica suena a lo que es — misma idea que
    // [companionAbility], y con la misma degradación elegante en equipos sin primitivas.
    //
    // Regla de mezcla: los AVISOS son graves y flojos (algo se acerca), los CASTIGOS pegan
    // (THUD/CLICK a tope) y los COUNTERPLAY son ligeros y ascendentes (te lo has quitado de encima).
    fun biome(cue: BiomeCue) = play(
        when (cue) {
            // 🔷 Cristal: la cáscara se raja — dos tics secos y cristalinos.
            BiomeCue.CRYSTAL_CRACK -> listOf(HStep(HPrim.TICK, 0.55f), HStep(HPrim.TICK, 0.40f, 45))
            // 💤 Bosque del Sueño: la modorra CAE encima — rampa lenta que se apaga.
            BiomeCue.DROWSY -> listOf(HStep(HPrim.SLOW_RISE, 0.45f), HStep(HPrim.LOW_TICK, 0.35f, 60))
            // 🌋 Volcán: la lava se come una fila — golpe grave que se derrama y se apaga.
            //    Era el único castigo del juego MUDO también en la mano (P-65).
            BiomeCue.LAVA_SURGE ->
                listOf(HStep(HPrim.THUD, 0.85f), HStep(HPrim.LOW_TICK, 0.50f, 90), HStep(HPrim.QUICK_FALL, 0.35f, 80))
            // 😋 Glotón: la boca se abre (gruñido grave) / MUERDE (dos golpes de mandíbula).
            BiomeCue.GLUTTON_OPEN -> listOf(HStep(HPrim.LOW_TICK, 0.50f), HStep(HPrim.LOW_TICK, 0.60f, 110))
            BiomeCue.GLUTTON_BITE -> listOf(HStep(HPrim.THUD, 0.85f), HStep(HPrim.THUD, 0.60f, 70))
            // 🌌 Espacio Profundo: el agujero se arma (succión que sube) / se disipa (alivio corto).
            // Brecha en el Casco (P-248): la despresurización arrastra y SUELTA (golpe más fuerte que
            // cualquier aviso); la soldadura es un chasquido corto que sube.
            BiomeCue.METEOR_SPOTTED -> listOf(HStep(HPrim.LOW_TICK, 0.40f), HStep(HPrim.SLOW_RISE, 0.45f, 90))
            BiomeCue.METEOR_IMPACT -> listOf(HStep(HPrim.SLOW_RISE, 0.7f), HStep(HPrim.SPIN, 0.9f, 30), HStep(HPrim.THUD, 1f, 40), HStep(HPrim.QUICK_FALL, 0.8f, 60))
            BiomeCue.METEOR_DEFLECT -> listOf(HStep(HPrim.QUICK_RISE, 0.40f), HStep(HPrim.TICK, 0.30f, 50))
            // ☣️ Tóxico: el veneno salpica lejos — burbujeo grave escalonado.
            BiomeCue.TOXIC_SPLASH ->
                listOf(HStep(HPrim.LOW_TICK, 0.35f), HStep(HPrim.LOW_TICK, 0.45f, 80), HStep(HPrim.LOW_TICK, 0.55f, 80))
            // 👑 Trono Real: aviso de asedio / PISOTÓN de piedra / los muros se derrumban.
            BiomeCue.KING_WARNING -> listOf(HStep(HPrim.LOW_TICK, 0.45f), HStep(HPrim.LOW_TICK, 0.55f, 130))
            BiomeCue.KING_STRIKE -> listOf(HStep(HPrim.THUD, 1f), HStep(HPrim.LOW_TICK, 0.45f, 60))
            BiomeCue.KING_SHEATHE ->
                listOf(HStep(HPrim.CLICK, 0.80f), HStep(HPrim.TICK, 0.50f, 55), HStep(HPrim.TICK, 0.35f, 55))
            // 🌿 Jardín: la enredadera encaja el golpe de una línea rota — chasquido con retroceso.
            BiomeCue.PLANT_HURT -> listOf(HStep(HPrim.CLICK, 0.70f), HStep(HPrim.QUICK_FALL, 0.45f, 30))
            // 🌠 Cielo Estelar: el astro asoma (amenaza que crece) / DISPARA / se retira (alivio).
            BiomeCue.ASTRO_EMERGE -> listOf(HStep(HPrim.SLOW_RISE, 0.60f))
            BiomeCue.ASTRO_STRIKE -> listOf(HStep(HPrim.THUD, 1f), HStep(HPrim.QUICK_FALL, 0.60f, 40))
            BiomeCue.ASTRO_RETREAT -> listOf(HStep(HPrim.QUICK_FALL, 0.35f), HStep(HPrim.TICK, 0.30f, 40))
        }
    )

    // ── P-283 · BLOQUES PREMIUM: cada bloque de bioma se SIENTE distinto al colocarlo y al romperlo ──
    // Básico y Cerámica no tienen firma: siguen con `place()` y sin vibración propia al romper.

    /** ¿Tiene este estilo de bloque firma háptica propia? */
    fun hasBlockSignature(style: BlockTileStyle): Boolean =
        style != BlockTileStyle.BEVEL && style != BlockTileStyle.EXTRUDED && style != BlockTileStyle.JELLY

    /** Colocar una ficha con el bloque [style]. */
    fun blockPlace(style: BlockTileStyle) = play(
        when (style) {
            BlockTileStyle.FACETED -> listOf(HStep(HPrim.TICK, 0.70f))
            BlockTileStyle.WOOD -> listOf(HStep(HPrim.CLICK, 0.60f), HStep(HPrim.LOW_TICK, 0.30f, 40))
            BlockTileStyle.LAVA -> listOf(HStep(HPrim.THUD, 0.55f))
            BlockTileStyle.ICE -> listOf(HStep(HPrim.TICK, 0.50f), HStep(HPrim.TICK, 0.35f, 35))
            BlockTileStyle.SPECTRE -> listOf(HStep(HPrim.QUICK_RISE, 0.40f))
            BlockTileStyle.CONSTELLATION -> listOf(HStep(HPrim.TICK, 0.40f), HStep(HPrim.TICK, 0.30f, 60))
            BlockTileStyle.VOID -> listOf(HStep(HPrim.QUICK_FALL, 0.50f))
            BlockTileStyle.METAL -> listOf(HStep(HPrim.CLICK, 0.75f), HStep(HPrim.TICK, 0.40f, 30))
            // Neón: un zumbido corto de tubo que se enciende. Dorado: un clic macizo, más pesado que el metal.
            BlockTileStyle.NEON -> listOf(HStep(HPrim.TICK, 0.55f), HStep(HPrim.TICK, 0.30f, 25))
            BlockTileStyle.GOLD -> listOf(HStep(HPrim.CLICK, 0.85f), HStep(HPrim.LOW_TICK, 0.40f, 30))
            else -> listOf(HStep(HPrim.CLICK, 0.62f))
        }
    )

    /** Se rompe una línea hecha con el bloque [style]: una vez por pasada, al romper la primera casilla. */
    fun blockBreak(style: BlockTileStyle) = play(
        when (style) {
            BlockTileStyle.FACETED -> listOf(HStep(HPrim.TICK, 0.80f), HStep(HPrim.TICK, 0.50f, 50))
            BlockTileStyle.WOOD -> listOf(HStep(HPrim.CLICK, 0.80f), HStep(HPrim.LOW_TICK, 0.50f, 40))
            BlockTileStyle.LAVA -> listOf(HStep(HPrim.THUD, 0.85f), HStep(HPrim.QUICK_FALL, 0.40f, 60))
            BlockTileStyle.ICE -> listOf(HStep(HPrim.TICK, 0.90f), HStep(HPrim.TICK, 0.60f, 40), HStep(HPrim.TICK, 0.40f, 40))
            BlockTileStyle.SPECTRE -> listOf(HStep(HPrim.QUICK_RISE, 0.60f), HStep(HPrim.LOW_TICK, 0.30f, 80))
            BlockTileStyle.CONSTELLATION -> listOf(
                HStep(HPrim.TICK, 0.60f), HStep(HPrim.TICK, 0.50f, 60), HStep(HPrim.TICK, 0.40f, 60), HStep(HPrim.QUICK_RISE, 0.50f, 40)
            )
            BlockTileStyle.VOID -> listOf(HStep(HPrim.SLOW_RISE, 0.50f), HStep(HPrim.THUD, 0.90f, 10))
            BlockTileStyle.METAL -> listOf(HStep(HPrim.THUD, 0.70f), HStep(HPrim.TICK, 0.60f, 40), HStep(HPrim.TICK, 0.45f, 50))
            BlockTileStyle.NEON -> listOf(HStep(HPrim.QUICK_RISE, 0.50f), HStep(HPrim.TICK, 0.60f, 40))
            BlockTileStyle.GOLD -> listOf(HStep(HPrim.THUD, 0.80f), HStep(HPrim.TICK, 0.55f, 40), HStep(HPrim.TICK, 0.40f, 50))
            else -> listOf(HStep(HPrim.CLICK, 0.62f))
        }
    )

    /** Línea(s) completada(s): snap + barrido que crece con el nº de líneas. */
    fun lineClear(lines: Int) {
        val n = lines.coerceIn(1, 4)
        play(listOf(
            HStep(HPrim.QUICK_RISE, (0.45f + 0.16f * n).coerceAtMost(1f)),
            HStep(HPrim.CLICK,      (0.55f + 0.12f * n).coerceAtMost(1f))
        ))
    }

    /** Fusión de Teikas: golpe grave que sube por tier (1..4). */
    fun fusion(tier: Int) {
        val t = tier.coerceIn(1, 4)
        play(listOf(HStep(HPrim.THUD, (0.40f + 0.16f * t).coerceAtMost(1f))))
    }

    /** Combo encadenado: la fuerza escala con la racha → más combo = más intenso. */
    fun combo(streak: Int) {
        val sc = (streak.coerceAtLeast(2) / 10f).coerceIn(0.35f, 1f)
        play(listOf(HStep(HPrim.QUICK_RISE, sc), HStep(HPrim.CLICK, sc)))
    }

    /**
     * Habilidad de compañero: una "firma" háptica DISTINTA por personaje, casada con su fantasía
     * (delays nativos en la composición). Disparada UNA vez por casteo desde la cinemática.
     */
    fun companionAbility(companionId: String) = play(
        when (companionId) {
            // 🛡️ Cristal — escudo: taps cristalinos limpios que se asientan.
            "cristal"   -> listOf(HStep(HPrim.TICK, 0.40f), HStep(HPrim.TICK, 0.55f, 70), HStep(HPrim.CLICK, 0.60f, 70))
            // 💤 Dormilona — colapso onírico: caída lenta, pesada y sorda.
            "dormilona" -> listOf(HStep(HPrim.LOW_TICK, 0.30f), HStep(HPrim.SLOW_RISE, 0.50f, 40), HStep(HPrim.THUD, 0.60f, 20))
            // 💨 Miedoso — pánico: tics rápidos nerviosos + golpe final.
            "miedoso"   -> listOf(HStep(HPrim.TICK, 0.60f), HStep(HPrim.TICK, 0.60f, 45), HStep(HPrim.TICK, 0.70f, 45), HStep(HPrim.CLICK, 0.80f, 55))
            // 🌿 Planta — enredadera: la raíz crece y AGARRA.
            "planta"    -> listOf(HStep(HPrim.SLOW_RISE, 0.70f), HStep(HPrim.THUD, 0.65f, 10))
            // ⚡ Eléctrico — sobrecarga: zap staccato cortante.
            "electrico" -> listOf(HStep(HPrim.TICK, 0.70f), HStep(HPrim.TICK, 0.50f, 35), HStep(HPrim.TICK, 0.60f, 35), HStep(HPrim.CLICK, 0.95f, 40))
            // 🔥 Fuego — llamarada: rugido que sube y revienta.
            "fuego"     -> listOf(HStep(HPrim.SLOW_RISE, 1f), HStep(HPrim.THUD, 0.85f, 10))
            // 👥 Ninja — corte en cruz: dos tajos ultra-nítidos y limpios.
            "ninja"     -> listOf(HStep(HPrim.TICK, 0.95f), HStep(HPrim.TICK, 0.95f, 70))
            // ❄️ Hielo — glaciar: chasquido seco + cristalitos que se asientan.
            "hielo"     -> listOf(HStep(HPrim.CLICK, 0.90f), HStep(HPrim.LOW_TICK, 0.45f, 45), HStep(HPrim.LOW_TICK, 0.40f, 55))
            // 👻 Fantasma — poltergeist: vaivén etéreo (giro flotante).
            "fantasma"  -> listOf(HStep(HPrim.LOW_TICK, 0.30f), HStep(HPrim.SPIN, 0.55f, 30), HStep(HPrim.LOW_TICK, 0.30f, 40))
            // 😋 Glotón — devorar: ritmo de mordiscos graves.
            "gloton"    -> listOf(HStep(HPrim.THUD, 0.60f), HStep(HPrim.THUD, 0.70f, 95), HStep(HPrim.THUD, 0.80f, 95))
            // ☣️ Tóxico — infección: burbujeo que se expande y cuaja.
            "toxico"    -> listOf(HStep(HPrim.LOW_TICK, 0.40f), HStep(HPrim.LOW_TICK, 0.50f, 80), HStep(HPrim.LOW_TICK, 0.60f, 90), HStep(HPrim.THUD, 0.60f, 90))
            // 🌠 Estrella — lluvia de deseos: cascada brillante ascendente.
            "estrella"  -> listOf(HStep(HPrim.TICK, 0.40f), HStep(HPrim.TICK, 0.50f, 55), HStep(HPrim.TICK, 0.70f, 55), HStep(HPrim.QUICK_RISE, 0.85f, 30))
            // 👑 Rey — decreto: pisotón real autoritario.
            "rey"       -> listOf(HStep(HPrim.THUD, 1f), HStep(HPrim.CLICK, 0.80f, 90))
            // 💥 Galáctico — supernova: el clímax (sube, gira, estalla y colapsa).
            "galactico" -> listOf(HStep(HPrim.SLOW_RISE, 1f), HStep(HPrim.SPIN, 1f, 10), HStep(HPrim.THUD, 1f, 10), HStep(HPrim.QUICK_FALL, 0.85f, 10))
            else        -> listOf(HStep(HPrim.QUICK_RISE, 0.70f), HStep(HPrim.CLICK, 0.80f))
        }
    )

    // ── Render con degradación elegante ──────────────────────────────────────────
    private fun play(steps: List<HStep>) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supportsComposition) {
                val comp = VibrationEffect.startComposition()
                var added = false
                for (s in steps) {
                    val id = resolve(s.prim)
                    if (id >= 0) { comp.addPrimitive(id, s.scale.coerceIn(0f, 1f), s.delayMs); added = true }
                }
                if (added) v.vibrate(comp.compose())
            } else {
                playFallback(v, steps)
            }
        } catch (_: Exception) { /* nunca crashear por un háptico */ }
    }

    /** Devuelve la primitiva soportada más parecida (cadena de sustitución) o -1 si ninguna. */
    private fun resolve(prim: HPrim): Int {
        val chain = when (prim) {
            HPrim.TICK       -> listOf(HPrim.TICK, HPrim.CLICK)
            HPrim.CLICK      -> listOf(HPrim.CLICK, HPrim.TICK)
            HPrim.LOW_TICK   -> listOf(HPrim.LOW_TICK, HPrim.TICK, HPrim.CLICK)
            HPrim.QUICK_RISE -> listOf(HPrim.QUICK_RISE, HPrim.CLICK, HPrim.TICK)
            HPrim.SLOW_RISE  -> listOf(HPrim.SLOW_RISE, HPrim.QUICK_RISE, HPrim.CLICK)
            HPrim.QUICK_FALL -> listOf(HPrim.QUICK_FALL, HPrim.CLICK, HPrim.TICK)
            HPrim.SPIN       -> listOf(HPrim.SPIN, HPrim.QUICK_RISE, HPrim.CLICK)
            HPrim.THUD       -> listOf(HPrim.THUD, HPrim.CLICK, HPrim.TICK)
        }
        for (p in chain) primId[p]?.let { return it }
        return -1
    }

    private fun playFallback(v: Vibrator, steps: List<HStep>) {
        if (hasAmplitude) {
            val timings = ArrayList<Long>(steps.size * 2)
            val amps    = ArrayList<Int>(steps.size * 2)
            for (s in steps) {
                if (s.delayMs > 0) { timings.add(s.delayMs.toLong()); amps.add(0) }
                timings.add(durationFor(s.prim))
                amps.add((s.scale.coerceIn(0f, 1f) * 255f).toInt().coerceIn(1, 255))
            }
            if (timings.isNotEmpty()) {
                v.vibrate(VibrationEffect.createWaveform(timings.toLongArray(), amps.toIntArray(), -1))
            }
        } else {
            // Sin control de amplitud: un único pulso (se pierde el ritmo pero hay feedback).
            val total = steps.sumOf { it.delayMs.toLong() + durationFor(it.prim) }.coerceIn(10L, 400L)
            v.vibrate(VibrationEffect.createOneShot(total, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun durationFor(p: HPrim): Long = when (p) {
        HPrim.TICK, HPrim.LOW_TICK          -> 12L
        HPrim.CLICK                          -> 20L
        HPrim.THUD                           -> 36L
        HPrim.QUICK_RISE, HPrim.QUICK_FALL   -> 60L
        HPrim.SPIN                           -> 80L
        HPrim.SLOW_RISE                      -> 120L
    }
}
