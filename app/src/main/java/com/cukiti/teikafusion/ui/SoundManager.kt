package com.cukiti.teikafusion.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.cukiti.teikafusion.R
import com.cukiti.teikafusion.model.BiomeType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Margen de sincronización conservado del playtest anterior. Los recursos actuales son OGG/WAV;
// no deducir su latencia del MP3 de origen. Recalibrar solo con un playtest del VFX en dispositivo.
private const val DEFAULT_IMPACT_DELAY_MS = 8L

/**
 * Gestor de Audio para Teika Fusion.
 *
 * SFX: muestras en res/raw, renovadas con ElevenLabs en fantasía moderna cálida, mágica
 *      y elegante (2026-09-13); trazabilidad en docs/audio/renovacion-resultados.json.
 *      Reproducidas con [SoundPool] para latencia mínima.
 *      - Colocar pieza y limpiar línea tienen variante por cada aspecto de bloque (12); el activo
 *        se fija con [setBlockSkin] (ver [placeSampleId] y [clearSampleId]).
 *      - La fusión es una sola muestra con el tono según el nivel del Teika ([MERGE_RATE]).
 *
 * BGM: dos MediaPlayer encadenados, dirigidos por contexto desde la navegación. El segundo queda
 *      preparado antes de que termine el primero para evitar el hueco de `isLooping` al reabrir OGG.
 */
class SoundManager(private val context: Context) {

    private var bgmPlayer: MediaPlayer? = null
    private var bgmNextPlayer: MediaPlayer? = null
    private var bgmPrepared = false
    @Volatile private var bgmPlaybackAllowed = true

    // Niveles de volumen INDEPENDIENTES (0f..1f) controlados con sliders desde Ajustes:
    //  · musicVolume → BGM (MediaPlayer en loop). Se mapea a la salida real con un techo [MUSIC_CEILING]
    //    para que ni al 100% sature por encima de los efectos.
    //  · sfxVolume   → efectos de sonido (SoundPool), multiplicado en cada disparo.
    // (La vibración vive en el objeto global [Haptics], fuera del audio.)
    var musicVolume: Float = 0.6f
        private set
    var sfxVolume: Float = 1f
        private set

    // Master mute (botón rápido 🔊/🔇 en home/pausa): silencia TODO sin perder los niveles elegidos.
    var masterMuted: Boolean = false
        private set

    // DUCKING (P-08, 2026-07-31): en los momentos GRANDES —el MEGA, un hito del muro, la derrota—
    // la BGM se apartaba de nadie y competía con el golpe. Este factor la agacha un instante y la
    // devuelve con una rampa (un corte seco de música se oye como un fallo). Es el truco estándar
    // del género: el clímax se oye porque lo demás se quita de en medio, no porque suene más fuerte.
    @Volatile private var duckFactor: Float = 1f

    /**
     * **EL JINGLE DE DERROTA** (2026-09-29, pedido del usuario): `snd_game_over` pasó de efecto de 1 s a una
     * frase musical de ~4 s. El duck normal va ligado al player que suena, y al perder la música cambia
     * (bioma → menús) casi a la vez: el player nuevo arrancaría a pleno encima del jingle. Este factor no
     * pertenece a ningún player, así que el que arranque durante el jingle ya nace bajo ([playGameOver]).
     */
    @Volatile private var jingleFactor: Float = 1f
    private var jingleJob: kotlinx.coroutines.Job? = null
    @Volatile private var jingleGeneration: Long = 0L
    private val GAME_OVER_JINGLE_MS = 4000L
    private val JINGLE_MUSIC_FACTOR = 0.2f
    private val JINGLE_RELEASE_MS = 800L
    private var duckJob: kotlinx.coroutines.Job? = null
    /**
     * Generación del ducking. Sin ella el agachado se QUEDABA PEGADO (auditoría de audio 2026-08-09):
     * `duckFactor` es un campo compartido y el que lo bajaba era una corrutina en otro hilo, así que
     * `releaseBgmPlayer()` podía restaurarlo a 1 **antes** de que la corrutina escribiera 0,40 y
     * muriera cancelada en el siguiente `delay`. A partir de ahí la música sonaba al 40 % para
     * siempre… hasta que otro golpe grande disparaba un duck que sí terminaba su rampa y la subía de
     * vuelta. Eso es lo que se oía como «la música se sube y se baja sola».
     *
     * Ahora cada duck lleva su número: solo el duck VIGENTE puede tocar el factor, y su `finally`
     * lo deja siempre en 1 pase lo que pase (cancelación incluida).
     */
    @Volatile private var duckGeneration: Long = 0L
    /**
     * Cuánto se agacha la música (fracción del volumen elegido).
     *
     * Subido de 0,55 a 0,68 (2026-08-29f). Con techo de música 0,11, bajar al 55 % era una caída de
     * 5,2 dB: demasiado para un efecto que se dispara varias veces por partida — se leía como que
     * la música se iba, no como que se apartaba un momento.
     */
    private val DUCK_DEPTH = 0.68f
    /** Cuánto se mantiene agachada antes de empezar a volver. */
    private val DUCK_HOLD_MS = 450L
    /**
     * Rampa de vuelta: pasos × ms (~500 ms). MUCHOS pasos pequeños a propósito — con 8 saltos de
     * 0,075 la vuelta se oía como una escalera, no como un fundido, y sobre una BGM con techo 0,11
     * cada escalón se nota. 25 pasos de 20 ms es un fundido continuo.
     */
    private val DUCK_STEPS = 25
    private val DUCK_STEP_MS = 20L

    /**
     * **FUNDIDO ENTRE PISTAS** (P-52, 2026-09-07). Tercer factor de la misma familia que
     * [duckFactor] y `focusFactor`: multiplica el volumen sin tocar el nivel elegido por el jugador.
     *
     * Existe porque cambiar de pista era `releaseBgmPlayer()` + `startCurrentMusic()`, o sea un
     * **corte seco**. Cuando todas las pistas caían al mismo archivo no se notaba, pero la Caverna
     * de Cristal y el Bosque del Sueño **ya tienen tema propio**: entrar a uno de sus niveles corta
     * la música de menús a mitad de compás. Se oye hoy, no el día que existan las otras doce.
     *
     * Siempre vuelve a 1: lo restaura [releaseBgmPlayer] y el `finally` del propio fundido. Un factor
     * que se quedara en 0 sería música muda para siempre, y este fichero ya tuvo ese bug con el duck.
     */
    @Volatile private var fadeFactor: Float = 1f
    private var musicFadeJob: kotlinx.coroutines.Job? = null
    @Volatile private var fadeGeneration: Long = 0L
    private val FADE_MS = 260L
    private val FADE_STEPS = 13

    // Volúmenes efectivos = nivel elegido salvo que el master mute esté activo.
    private val effectiveMusic: Float get() =
        if (masterMuted) 0f else musicVolume * MUSIC_CEILING * duckFactor * focusFactor * fadeFactor * jingleFactor
    private val effectiveSfx: Float get() = if (masterMuted) 0f else sfxVolume

    // ── Skin de bloque activo ─────────────────────────────────────────────────
    // El sonido de colocar/limpiar depende del skin: Clásico (snap seco tipo Block
    // Blast), Cristal (tin de vidrio + estallido de esquirlas, sintetizados el 2026-09-23) o
    // Cerámica (clac de porcelana + astillado al romper). El Caramelo se retiró (P-249). El ViewModel lo actualiza al cargar prefs, al equipar un
    // skin y al fusionar la cuenta en la nube.
    @Volatile private var activeSkinId: String? = null

    /** Fija el skin activo por su id de cosmético ("cristal" = Cristal, "fall3d" = Cerámica; null/otro = Clásico). */
    fun setBlockSkin(cosmeticId: String?) { activeSkinId = cosmeticId }

    // ── SoundPool (SFX basados en archivo) ────────────────────────────────────
    // maxStreams limita la polifonía: en combos masivos evita saturar el mezclador.
    //
    // Sube de 6 a 8 (2026-07-31, bloque 22). La cuenta del peor caso legítimo se pasaba del techo:
    // un clear de 4 líneas gasta 4 voces (base + 3 capas), y en ese MISMO instante suenan el combo,
    // la fusión que la cadena provoca y la aparición del Teika → 7. Con 8 streams el caso cabe y
    // sigue habiendo tope real contra la cadena infinita.
    private val MAX_CONCURRENT_SFX = 8

    // Prioridades de stream. Cuando SoundPool se queda sin voces libres, MATA la de menor prioridad
    // (a igualdad, la más vieja) — y con todo a prioridad 1, lo que se perdía era arbitrario: el
    // golpe base del clear podía morir para dejar sitio a un tic de UI. Tres escalones bastan.
    /** Decoración: capas de acorde, tics de UI, contadores. Lo primero que se sacrifica. */
    private val PRIO_DECOR = 0
    /** Por defecto: la mayoría de cues de juego. */
    private val PRIO_NORMAL = 1
    /** Nunca se debe perder: el golpe del clear, la fusión, el MEGA, derrota/victoria. */
    private val PRIO_KEY = 2

    // ── NIVELADO POR FAMILIAS (2026-07-31, decisión del usuario) ──────────────
    // Los volúmenes venían "cada uno a su aire" del batch de SFX de biomas y nunca se calibraron:
    // una mecánica de bioma podía tapar el golpe de la propia jugada. Se nivela por FAMILIAS, con
    // dos multiplicadores en un solo sitio para retocarlos tras el playtest:
    //   · los golpes del TABLERO (colocar, clear, fusión, MEGA, Teika, combo) quedan INTACTOS —
    //     son la jugada, y nada debe sonar por encima de ellos;
    //   · las mecánicas de BIOMA bajan un 15 %: son ambiente hostil, no la acción del jugador;
    //   · la UI baja un 20 %: es lo que más se repite y lo que menos cuenta.
    /** Multiplicador de la familia BIOMA (mecánicas ambientales en partida). */
    private val BIOME_MIX = 0.85f
    /** Multiplicador de la familia UI (navegación, contadores, economía). */
    private val UI_MIX = 0.80f

    // ── Clear en capas (ver [playClear]) ──────────────────────────────────────
    /** Techo de voces del clear. 4 deja 2 streams libres para el combo/fusión simultáneos. */
    private val MAX_CLEAR_LAYERS = 4
    /** Separación entre capas: lo bastante corto para oírse como UN sonido más rico, no como un eco. */
    private val CLEAR_LAYER_STAGGER_MS = 42L
    /** Intervalos de acorde mayor sobre la muestra base: fundamental, 3ª, 5ª, 8ª. */
    private val CLEAR_LAYER_RATE = floatArrayOf(1f, 1.26f, 1.5f, 2f)
    /** Volumen DECRECIENTE por capa → la energía total crece sublinealmente (no satura). */
    private val CLEAR_LAYER_VOLUME = floatArrayOf(1f, 0.80f, 0.66f, 0.55f)

    /**
     * Scope propio para el escalonado de las capas del clear (único SFX que necesita esperar).
     * Se CANCELA en [release] → sin corrutinas huérfanas sonando tras cerrar la partida.
     */
    private val sfxScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * HILO DE LA MÚSICA (análisis a fondo del audio, 2026-10-01). Los MediaPlayer de la BGM y sus campos
     * (`bgmPlayer`, `bgmNextPlayer`, `bgmPrepared`…) se tocan desde el hilo principal —navegación,
     * pausa, reanudar, fin del bucle, foco de audio—, pero el FUNDIDO entre pistas, el agachado, la
     * rampa del foco y el jingle corrían en `Dispatchers.Default` y el fundido llegaba a liberar y
     * crear players desde allí. Sin ningún candado, un `pauseBgm()` o un `resumeBgm()` a mitad de
     * fundido podía arrancar un player ya liberado o dejar dos sonando. Todo lo que toca la BGM corre
     * ahora en el hilo principal; los SFX siguen en `Default`.
     */
    private val musicThread = Dispatchers.Main.immediate

    private data class PendingPlay(
        val volume: Float,
        val rate: Float,
        val priority: Int,
        val generation: Long,
        /** Cuándo se PIDIÓ. Un SFX es feedback de algo que el jugador acaba de hacer: pasado su
         *  momento ya no corresponde a nada en pantalla y es mejor perderlo que soltarlo tarde. */
        val atMs: Long,
        /** Cue de la propia intro ([playIntroSting], [playIntroStart]): suena con la cortina puesta. */
        val bypassCurtain: Boolean = false,
        /** Capa intencionada sobre la misma muestra (acorde del clear): no corta a la anterior. */
        val layer: Boolean = false
    )

    /**
     * Cuánto puede esperar un disparo diferido antes de dejar de tener sentido.
     *
     * 250 ms cubre de sobra el único caso legítimo —las capas del clear, que van escalonadas 42 ms
     * (4 capas = 126 ms)— y descarta lo demás.
     */
    private val PENDING_TTL_MS = 250L

    /**
     * Los cues de la intro (los únicos que atraviesan la cortina) esperan más: la firma y el «toca para
     * jugar» suenan una vez por arranque y, si su muestra aún no ha cargado, perderlos es peor que oírlos
     * un poco tarde. Con su carga al principio de la cola casi nunca hace falta (2026-10-01).
     */
    private val INTRO_PENDING_TTL_MS = 1_500L

    /** Separación al soltar varios pendientes de la misma muestra. Ver [flushPendingSample]. */
    private val PENDING_FLUSH_STAGGER_MS = 28L

    /**
     * SoundPool carga en segundo plano. Conservamos el último disparo de cada muestra mientras
     * termina la carga para no perder el primer feedback de la partida. El mapa está acotado por
     * el número de muestras y se vacía al liberar el manager.
     */
    private val pendingPlays = java.util.concurrent.ConcurrentHashMap<
        Int,
        java.util.concurrent.ConcurrentLinkedDeque<PendingPlay>
    >()
    private val reportedPlaybackFailures = java.util.concurrent.ConcurrentHashMap.newKeySet<Int>()
    /**
     * Tope de disparos diferidos por muestra. Bajado de 8 a 3 (2026-08-29f): ocho copias del mismo
     * sample sonando a la vez son ocho veces la amplitud (+18 dB) y se comen las ocho voces del
     * pool de golpe. Ver [flushPendingSample], que además las escalona.
     */
    private val MAX_PENDING_PER_SAMPLE = 3
    @Volatile private var sfxGeneration = 0L
    @Volatile private var released = false

    // Debe existir ANTES de cualquier llamada a SoundPool.load: si el callback se instala en init,
    // una muestra muy pequeña puede completar la carga antes de que init llegue a ejecutarse.
    private val soundPool: SoundPool = SoundPool.Builder()
        // +1: la voz del despertador ([startKeepAlive]) no puede comerse una voz de juego.
        .setMaxStreams(MAX_CONCURRENT_SFX + 1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
        .also { pool ->
            pool.setOnLoadCompleteListener { _, sampleId, status ->
                if (status != 0) {
                    pendingPlays.remove(sampleId)
                    return@setOnLoadCompleteListener
                }
                readyIds.add(sampleId)
                flushPendingSample(sampleId)
                startKeepAlive()
            }
        }

    // Ids de muestra. SoundPool.load es asíncrono; sólo se reproducen cuando el
    // OnLoadCompleteListener las marca listas (evita play() silenciosos al arranque).
    private val readyIds = java.util.Collections.synchronizedSet(HashSet<Int>())

    // ── LO PRIMERO EN CARGAR: lo que suena en la INTRO (análisis a fondo, 2026-10-01) ──────────────
    // SoundPool carga de una en una, en el orden de estas líneas, y son ~130 muestras. La firma estaba
    // hacia la mitad y el despertador de la salida casi al final: en un arranque en frío del A15 la
    // firma podía pedirse (a los ~2,5 s) antes de estar cargada y perderse, y la salida de audio seguía
    // dormida justo durante la intro. Ahora van las tres primeras.
    /** 1 s de silencio digital en bucle: ver [startKeepAlive]. */
    private val keepAliveId         = soundPool.load(context, R.raw.snd_keepalive, 1)
    // Firma de la intro «CUKITI» (P-374, v1 elegida por el usuario el 2026-09-30).
    private val introStingId        = soundPool.load(context, R.raw.snd_intro_sting, 1)
    private val launchId            = soundPool.load(context, R.raw.snd_play_launch, 1)
    private val floraLaunchId       = soundPool.load(context, R.raw.snd_flora_launch, 1)

    private val placeClassicId = soundPool.load(context, R.raw.snd_place_classic, 1)
    private val placeCrystalId = soundPool.load(context, R.raw.snd_place_crystal, 1)
    private val placeCeramicId = soundPool.load(context, R.raw.snd_place_ceramic, 1)
    private val placeMetalId = soundPool.load(context, R.raw.snd_place_metal, 1)
    // P-411: el Dorado tiene su «ding» de tesoro. (El Neón sigue con el tin del Cristal al colocar.)
    private val placeGoldId = soundPool.load(context, R.raw.snd_place_gold, 1)
    private val placeVacioId = soundPool.load(context, R.raw.snd_place_void, 1)
    private val placeConstelacionId = soundPool.load(context, R.raw.snd_place_constellation, 1)
    private val placeEspectroId = soundPool.load(context, R.raw.snd_place_spectre, 1)
    private val placeWoodId    = soundPool.load(context, R.raw.snd_place_wood, 1)
    private val placeLavaId    = soundPool.load(context, R.raw.snd_place_lava, 1)
    private val placeIceId     = soundPool.load(context, R.raw.snd_place_ice, 1)
    private val clearClassicId = soundPool.load(context, R.raw.snd_clear_classic, 1)
    private val clearCrystalId = soundPool.load(context, R.raw.snd_clear_crystal, 1)
    private val clearCeramicId = soundPool.load(context, R.raw.snd_clear_ceramic, 1)
    private val clearMetalId = soundPool.load(context, R.raw.snd_clear_metal, 1)
    // P-411: el tubo de Neón se sobrecarga y se apaga; el Dorado suelta una lluvia de monedas.
    private val clearNeonId = soundPool.load(context, R.raw.snd_clear_neon, 1)
    private val clearGoldId = soundPool.load(context, R.raw.snd_clear_gold, 1)
    // P-284: la chapa del bloque-Teika metálico se abre al romperse (sintetizado con ffmpeg).
    private val teikaSeedBreakId = soundPool.load(context, R.raw.snd_teika_seed_break, 1)
    private val clearVacioId = soundPool.load(context, R.raw.snd_clear_void, 1)
    private val clearConstelacionId = soundPool.load(context, R.raw.snd_clear_constellation, 1)
    private val clearEspectroId = soundPool.load(context, R.raw.snd_clear_spectre, 1)
    private val clearWoodId    = soundPool.load(context, R.raw.snd_clear_wood, 1)
    private val clearLavaId    = soundPool.load(context, R.raw.snd_clear_lava, 1)
    private val clearIceId     = soundPool.load(context, R.raw.snd_clear_ice, 1)
    private val megaId         = soundPool.load(context, R.raw.snd_mega, 1)
    // T4+T4: la subida del colapso estelar que precede al MEGA (2026-09-24, ElevenLabs).
    private val megaCollapseId = soundPool.load(context, R.raw.snd_mega_collapse, 1)
    // Aparición de un Teika nivel 1 (al brotar tras completar línea/columna).
    private val teikaAppearId  = soundPool.load(context, R.raw.snd_teika_appear, 1)
    // Fusión = APARICIÓN del teika resultante. A petición, TODAS las fusiones usan el MISMO sonido
    // (snd_merge_2). El 4+4 además dispara megaId (limpieza del tablero) vía playLevelUp.
    private val mergeId = soundPool.load(context, R.raw.snd_merge_2, 1)

    // ── UI / economía ─────────────────────────────────────────────────────────
    /**
      * EL TIC DE UI. Sustituye a `snd_nav_tab.mp3` (2026-08-29d, playtest del usuario: «se escucha
      * un sonido raro cuando se está acabando, como de interferencia»).
      *
      * La muestra vieja **cortaba en seco con señal**: el clic útil duraba 90 ms pero el fichero
      * duraba 480 ms, y los otros 390 ms eran ruido de fondo que terminaba de golpe con la onda en
      * 1078 (4,3 % del pico). Un salto brusco a silencio es un *pop*, y eso es lo que se oía.
      *
      * Renovación v2 2026-10-11: WAV estéreo completo aprobado, 480 ms de contenedor;
      * intervalo sobre pico -40 dB de 5,4 a 120,7 ms. Usuario aprueba repetición a 48 ms.
      * Sin recortar ni fundir la fuente; no equivale a comprobar toda la mezcla Android.
      */
    private val uiClickId           = soundPool.load(context, R.raw.snd_ui_click, 1)
    // (RETIRADOS 2026-09-24, P-267) snd_crystal_defuse_tap / _defuse_done / _fuse: la fusión y la
    // desfusión de cristales se fueron con los tiers.
    private val teikoinGainId       = soundPool.load(context, R.raw.snd_teikoin_gain, 1)
    private val purchaseId          = soundPool.load(context, R.raw.snd_purchase, 1)
    private val lifeLostId          = soundPool.load(context, R.raw.snd_life_lost, 1)
    private val noLivesId           = soundPool.load(context, R.raw.snd_no_lives, 1)
    private val comboId             = soundPool.load(context, R.raw.snd_combo, 1)
    private val gameOverId          = soundPool.load(context, R.raw.snd_game_over, 1)
    private val irisTransitionId    = soundPool.load(context, R.raw.snd_iris_transition, 1)
    private val irisOpenId          = soundPool.load(context, R.raw.snd_iris_open, 1)

    // ── Habilidades de compañero (SFX dedicados) ──────────────────────────────
    // Renovadas con ElevenLabs el 2026-09-29 salvo Tajo (slash), Miasma y Eclipse, que el usuario conserva;
    // cada una suena a lo que hace (docs/audio/elevenlabs-2026-09-29-habilidades.json).
    // Toño / Miasma Venenoso: siseo ácido + burbujeo del gas corroyendo la madera.
    private val abilityMiasmaId     = soundPool.load(context, R.raw.snd_ability_miasma, 1)
    // Reynaldo / Parto Real: campanada regia + pop orgánico al expulsar al Teika hijo.
    private val abilityBirthId      = soundPool.load(context, R.raw.snd_ability_birth, 1)
    // Maite / Siesta: glissando onírico del desplome + golpe suave de la fila que aterriza.
    private val abilityDreamId      = soundPool.load(context, R.raw.snd_ability_dream, 1)
    // Timoteo / Puzzle Deslizante: losetas de madera que se deslizan solas y encajan con un clac.
    private val abilitySlideId      = soundPool.load(context, R.raw.snd_ability_slide, 1)
    // Rayco / Energía Cruzada: chisporroteo eléctrico al energizar los Teikas.
    private val abilityEnergizeId   = soundPool.load(context, R.raw.snd_ability_energize, 1)
    // Iñaki / Fichas en Llamas: llamarada que arrasa la fila/columna al colocar la ficha encendida.
    private val abilityFireId       = soundPool.load(context, R.raw.snd_ability_fire, 1)
    // Flora / Cañón Floral: flor floreciendo que carga el Teika.
    private val abilityBloomId      = soundPool.load(context, R.raw.snd_ability_bloom, 1)
    // Morti / Posesión: soplo espectral que atraviesa el tablero y se solidifica.
    private val abilityGhostId      = soundPool.load(context, R.raw.snd_ability_ghost, 1)
    // Nieves / Estalactitas: cristalización + caída + impacto de hielo (timeline ~1.5 s del VFX).
    private val abilityIcicleId     = soundPool.load(context, R.raw.snd_ability_icicle, 1)
    // Estas cinco habilidades tuvieron muestras de síntesis local (2026-08-29e).
    // Renovadas con ElevenLabs junto con el resto de efectos el 2026-09-13.
    private val abilityShieldId     = soundPool.load(context, R.raw.snd_ability_shield, 1)
    private val abilitySlashId      = soundPool.load(context, R.raw.snd_ability_slash, 1)
    private val abilityFeastId      = soundPool.load(context, R.raw.snd_ability_feast, 1)
    private val abilityWishId       = soundPool.load(context, R.raw.snd_ability_wish, 1)
    private val abilityEclipseId    = soundPool.load(context, R.raw.snd_ability_eclipse, 1)
    // Momentos que antes pedían prestado un SFX de habilidad (2026-09-29): al renovar las habilidades,
    // el usuario decidió separarlos para que un portal no suene como Timoteo. Son copias del sonido
    // que tenían; cada momento conserva su identidad aunque la habilidad cambie.
    /** Portales, implosión, corriente del tablero, construir desde el banco y brújula (ex-deslizamiento). */
    private val boardShiftId        = soundPool.load(context, R.raw.snd_board_shift, 1)
    /** Reacción en cadena de barriles (ex-llamarada de Iñaki). */
    private val barrelBlastId       = soundPool.load(context, R.raw.snd_barrel_blast, 1)
    /** Eclosión del huevo (ex-Parto Real). */
    private val eggHatchId          = soundPool.load(context, R.raw.snd_egg_hatch, 1)
    /** Colocar la pieza imán (ex-Energía Cruzada). */
    private val magnetPlaceId       = soundPool.load(context, R.raw.snd_magnet_place, 1)
    /** Recoger una estrella-objetivo. Ya no pide prestado el chime de teikoins. */
    private val targetCollectId     = soundPool.load(context, R.raw.snd_target_collect, 1)

    // ── Mecánicas de bioma en partida (SFX dedicados, distintos del stinger del mapa) ──
    // Dojo en Sombras: la katana PARTE el tablero en dos, y el kintsugi lo sella.
    private val ninjaSlashId  = soundPool.load(context, R.raw.snd_ninja_slash, 1)
    private val ninjaRepairId = soundPool.load(context, R.raw.snd_ninja_repair, 1)
    // Cielo Estelar: el astro emerge (aviso), el Sol INCINERA el Teika marcado, la Luna ROBA bloques.
    private val astroEmergeId = soundPool.load(context, R.raw.snd_astro_emerge, 1)
    private val sunStrikeId   = soundPool.load(context, R.raw.snd_sun_strike, 1)
    private val moonStealId   = soundPool.load(context, R.raw.snd_moon_steal, 1)
    // Glotón: la boca se abre (aviso) y muerde el rincón.
    private val gluttonOpenId = soundPool.load(context, R.raw.snd_glutton_open, 1)
    private val gluttonBiteId = soundPool.load(context, R.raw.snd_glutton_bite, 1)
    // Trono Real: el muro de piedra se levanta y se derrumba.
    private val kingWallRiseId  = soundPool.load(context, R.raw.snd_king_wall_rise, 1)
    private val kingWallBreakId = soundPool.load(context, R.raw.snd_king_wall_break, 1)
    // Mansión Fantasma: el cazador roba y la niebla desciende.
    private val ghostHuntId = soundPool.load(context, R.raw.snd_ghost_hunt, 1)
    private val ghostFogId  = soundPool.load(context, R.raw.snd_ghost_fog, 1)
    // Glaciar: oleada de escarcha que congela un Teika.
    private val glacierFreezeId = soundPool.load(context, R.raw.snd_glacier_freeze, 1)
    // Pantano Tóxico: el veneno se propaga por la madera.
    private val toxicSpreadId = soundPool.load(context, R.raw.snd_toxic_spread, 1)
    // 2026-09-29 · ElevenLabs (una generación por sonido): momentos que estaban mudos o sonaban con la
    // muestra de otro. Origen, prompt y licencia de cada una en docs/audio/licencias/registro.json.
    private val poisonWashId      = soundPool.load(context, R.raw.snd_poison_wash, 1)
    private val poisonSplashId    = soundPool.load(context, R.raw.snd_poison_splash, 1)
    private val kingPieceLandId   = soundPool.load(context, R.raw.snd_king_piece_land, 1)
    private val kingPieceEvictId  = soundPool.load(context, R.raw.snd_king_piece_evict, 1)
    private val rockCrackId       = soundPool.load(context, R.raw.snd_rock_crack, 1)
    private val leadCrushId       = soundPool.load(context, R.raw.snd_lead_crush, 1)
    private val abilityReadyId    = soundPool.load(context, R.raw.snd_ability_ready, 1)
    private val trayRewardId      = soundPool.load(context, R.raw.snd_tray_reward, 1)
    private val vineSproutId      = soundPool.load(context, R.raw.snd_vine_sprout, 1)
    private val kintsugiSealId    = soundPool.load(context, R.raw.snd_kintsugi_seal, 1)
    private val stubbornResistId  = soundPool.load(context, R.raw.snd_stubborn_resist, 1)
    private val stubbornBreakId   = soundPool.load(context, R.raw.snd_stubborn_break, 1)
    private val moldStampId       = soundPool.load(context, R.raw.snd_mold_stamp, 1)
    private val mirrorStampId     = soundPool.load(context, R.raw.snd_mirror_stamp, 1)
    private val plantRescueId     = soundPool.load(context, R.raw.snd_plant_rescue, 1)
    private val starRetintId      = soundPool.load(context, R.raw.snd_star_retint, 1)
    // Vacío Galáctico: aviso, colapso del agujero, y disipación inofensiva (counterplay).
    private val galacticWarnId     = soundPool.load(context, R.raw.snd_galactic_warn, 1)
    private val galacticCollapseId = soundPool.load(context, R.raw.snd_galactic_collapse, 1)
    private val galacticCancelId   = soundPool.load(context, R.raw.snd_galactic_cancel, 1)
    // Cueva de Cristal: la armadura se agrieta y se rompe al completar la línea.
    // P-85 (2026-08-30): los dos premios de la pasada de variedad que se cobraban MUDOS.
    private val hotCellId        = soundPool.load(context, R.raw.snd_hot_cell, 1)
    private val pureLineId       = soundPool.load(context, R.raw.snd_pure_line, 1)
    private val crystalCrackId   = soundPool.load(context, R.raw.snd_crystal_crack, 1)
    private val crystalShatterId = soundPool.load(context, R.raw.snd_crystal_shatter, 1)
    // Bosque del Miedo: los bloques huyen asustados al colocar cerca.
    private val fearScareId = soundPool.load(context, R.raw.snd_fear_scare, 1)
    // Jardín: la enredadera agarra/arrastra un Teika, y se repliega herida (counterplay).
    private val vineGrabId = soundPool.load(context, R.raw.snd_vine_grab, 1)
    private val vineHurtId = soundPool.load(context, R.raw.snd_vine_hurt, 1)
    // Tormenta: las bobinas se cargan, descargan en cadena, o hacen cortocircuito (fallo).
    private val coilChargeId    = soundPool.load(context, R.raw.snd_coil_charge, 1)
    private val coilDischargeId = soundPool.load(context, R.raw.snd_coil_discharge, 1)
    private val coilShortId     = soundPool.load(context, R.raw.snd_coil_short, 1)
    // Volcán: la lava avanza desde arriba y quema.
    private val lavaRetreatId = soundPool.load(context, R.raw.snd_lava_retreat, 1)
    private val sleepWakeId = soundPool.load(context, R.raw.snd_sleep_wake, 1)
    private val glacierThawId = soundPool.load(context, R.raw.snd_glacier_thaw, 1)
    private val gluttonRetreatId = soundPool.load(context, R.raw.snd_glutton_retreat, 1)

    private val lavaSurgeId = soundPool.load(context, R.raw.snd_lava_surge, 1)
    // Bosque del Sueño: la modorra desciende sobre el tablero.
    private val sleepDrowsyId = soundPool.load(context, R.raw.snd_sleep_drowsy, 1)
    // INFINITO / LA MAREA DE HORMIGÓN: la maquinaria despierta (primer aumento), cada losa que se
    // asienta, y la PRENSA que revienta lo que llega al techo. Suenan a estructura y a máquina, no
    // a agua: la amenaza tiene que enseñar su regla también por el oído.
    // (PODADO 2026-07-31, bloque 22) Aquí se cargaba `snd_connector_link`, el circuito de la FICHA
    //  CONECTORA. Las fichas especiales del Infinito se retiraron con el rework de hormigón: el
    //  cue no tenía un solo llamante desde entonces, pero la muestra seguía ocupando sitio en el
    //  APK y cargándose en el SoundPool en cada arranque. El mp3 está en `art/_retirados/`.
    // (PODADOS 2026-08-09, con el modo WALLBLOCK) `tideWarnId` (snd_tide_warn) y `tideRiseId`
    //  (snd_tide_rise): el despertar del muro y cada losa de hormigón asentándose.
    // ── LOS MOMENTOS QUE ESTABAN MUDOS (2026-07-31) ────────────────────────────
    // El censo del bloque 22 vigila que ningún cue sobre; no podía ver lo contrario — los momentos
    // del juego que NO tenían ninguno. Salieron diez, y varios de los gordos: ganar un nivel (la
    // recompensa que más se repite en 293 niveles) solo VIBRABA, y la regla de oro del Infinito
    // —la columna monocolor— tenía VFX y háptica pero ni un sonido. Muestras propias, generadas
    // para cada momento.
    private val levelVictoryId    = soundPool.load(context, R.raw.snd_level_victory, 1)
    private val perfectClearId    = soundPool.load(context, R.raw.snd_perfect_clear, 1)
    // (PODADOS 2026-08-09, con el modo) `monoBreakId` (snd_mono_break) y `rewardRowId`
    //  (snd_reward_row): la columna monocolor de WALLBLOCK y sus tres filas de premio.
    private val kingWarningId     = soundPool.load(context, R.raw.snd_king_warning, 1)
    private val comboGraceId      = soundPool.load(context, R.raw.snd_combo_grace, 1)
    private val characterUnlockId = soundPool.load(context, R.raw.snd_character_unlock, 1)
    private val rankUpId          = soundPool.load(context, R.raw.snd_rank_up, 1)
    private val muralCompleteId   = soundPool.load(context, R.raw.snd_mural_complete, 1)
    private val streakId          = soundPool.load(context, R.raw.snd_streak, 1)
    private val invalidId         = soundPool.load(context, R.raw.snd_invalid, 1)

    // ── Stingers temáticos por bioma (botones del mapa de niveles) ─────────────
    private val biomeIds: Map<BiomeType, Int> = mapOf(
        BiomeType.CRYSTAL  to soundPool.load(context, R.raw.snd_biome_crystal, 1),
        BiomeType.SLEEP    to soundPool.load(context, R.raw.snd_biome_sleep, 1),
        BiomeType.FEAR     to soundPool.load(context, R.raw.snd_biome_fear, 1),
        BiomeType.GARDEN   to soundPool.load(context, R.raw.snd_biome_garden, 1),
        BiomeType.STORM    to soundPool.load(context, R.raw.snd_biome_storm, 1),
        BiomeType.VOLCANO  to soundPool.load(context, R.raw.snd_biome_volcano, 1),
        BiomeType.NINJA    to soundPool.load(context, R.raw.snd_biome_ninja, 1),
        BiomeType.GLACIER  to soundPool.load(context, R.raw.snd_biome_glacier, 1),
        BiomeType.GHOST    to soundPool.load(context, R.raw.snd_biome_ghost, 1),
        BiomeType.GLUTTON  to soundPool.load(context, R.raw.snd_biome_glutton, 1),
        BiomeType.TOXIC    to soundPool.load(context, R.raw.snd_biome_toxic, 1),
        BiomeType.STAR     to soundPool.load(context, R.raw.snd_biome_star, 1),
        BiomeType.KING     to soundPool.load(context, R.raw.snd_biome_king, 1),
        BiomeType.GALACTIC to soundPool.load(context, R.raw.snd_biome_galactic, 1)
    )

    /**
     * **NADA SUENA DETRAS DE UNA CORTINA DE CARGA** (2026-09-04, pedido del usuario).
     *
     * En la pantalla de carga —y en el overlay de «Cargando tu cuenta»— se oian efectos de
     * recompensa y de logro sin que se viera la ventana que los produce: el arranque calcula la
     * racha diaria, otorga monedas, sube el XP y fusiona el progreso de la nube **con el splash
     * puesto**, y todas esas celebraciones estan gateadas por `!inSplash` en la UI… pero sus cues
     * no lo estaban. El resultado era un chime de monedas y una fanfarria saliendo de la nada
     * mientras la barra cargaba.
     *
     * La regla es la del usuario, literal: *si no se ve la ventana que hace el sonido, el sonido no
     * suena*. Se corta AQUI, en la unica puerta por la que sale un SFX, y antes de encolar en
     * [pendingPlays] — si no, el disparo sobreviviria a la cortina y se soltaria al terminar de
     * cargar la muestra, que es exactamente el sintoma que se reporto.
     *
     * Las celebraciones que SI tienen ventana (la racha, la subida de rango) no se pierden: se
     * disparan desde su propia ventana, ya en HOME. Ver `AppNavigation`.
     *
     * No toca la musica ([playMusic] no pasa por aqui): la navegacion ya la gobierna por `status`.
     */
    @Volatile
    var loadingCurtainUp: Boolean = false

    private fun play(
        soundId: Int,
        volume: Float = 1f,
        rate: Float = 1f,
        priority: Int = PRIO_NORMAL,
        bypassCurtain: Boolean = false,
        layer: Boolean = false
    ) {
        val vol = effectiveSfx * volume
        if (com.cukiti.teikafusion.BuildConfig.DEBUG && soundId == lavaSurgeId) {
            Log.d("LavaAudio", "request ready=${soundId in readyIds} released=$released curtain=$loadingCurtainUp muted=$masterMuted volume=$vol")
        }
        if (released || loadingCurtainUp && !bypassCurtain || vol <= 0.001f || soundId == 0) return
        if (soundId !in readyIds) {
            // Se conserva una cola corta por muestra. Es importante para clears y derrumbes, que
            // usan el mismo sample para el golpe principal y sus capas secundarias.
            val queue = pendingPlays.computeIfAbsent(soundId) {
                java.util.concurrent.ConcurrentLinkedDeque()
            }
            queue.addLast(PendingPlay(vol, rate, priority, sfxGeneration, SystemClock.uptimeMillis(), bypassCurtain, layer))
            while (queue.size > MAX_PENDING_PER_SAMPLE) queue.pollFirst()
            return
        }
        emitReadySample(soundId, vol, rate, priority, layer)
    }

    /**
     * Suelta los disparos que quedaron esperando a que la muestra terminara de cargar.
     *
     * **El bug que arregla (2026-08-29f):** esto hacía `queue.forEach { emit(...) }`, o sea que
     * disparaba hasta OCHO copias del mismo sample **en el mismo instante**. Ocho copias suman ocho
     * veces la amplitud (+18 dB): un golpe saturado y muy fuerte de la nada, además de comerse las
     * ocho voces del pool. Se dispara al terminar de cargar cada muestra —es decir, justo mientras
     * el jugador está tocando la app recién abierta— y encaja con lo reportado como «a veces el
     * volumen se sube solo».
     *
     * Ahora: se tiran los que ya han caducado ([PENDING_TTL_MS]) y los que sobreviven salen
     * ESCALONADOS, igual que las capas del clear.
     */
    private fun flushPendingSample(soundId: Int) {
        // La cortina también aquí (2026-09-04): entre que se construye el SoundManager y que la
        // navegación levanta la bandera hay una rendija, y un cue que se colara por ella quedaría
        // encolado y se soltaría al terminar de cargar su muestra — o sea, sobre la barra de carga,
        // que es exactamente el síntoma que [loadingCurtainUp] viene a matar.
        // Con la cortina puesta solo salen los cues de la propia intro (la firma del estudio se pide
        // a los ~300 ms del arranque en frío, casi siempre antes de que su muestra haya cargado).
        if (released || masterMuted || soundId !in readyIds) return
        val queue = pendingPlays.remove(soundId) ?: return
        val now = SystemClock.uptimeMillis()
        val alive = queue.filter {
            it.generation == sfxGeneration &&
                now - it.atMs <= (if (it.bypassCurtain) INTRO_PENDING_TTL_MS else PENDING_TTL_MS) &&
                (!loadingCurtainUp || it.bypassCurtain)
        }
        if (alive.isEmpty()) return
        emitReadySample(soundId, alive[0].volume, alive[0].rate, alive[0].priority, alive[0].layer)
        if (alive.size == 1) return
        val generation = sfxGeneration
        sfxScope.launch {
            for (i in 1 until alive.size) {
                delay(PENDING_FLUSH_STAGGER_MS)
                if (released || generation != sfxGeneration) return@launch
                val p = alive[i]
                emitReadySample(soundId, p.volume, p.rate, p.priority, p.layer)
            }
        }
    }

    // ── LIMITADOR DE POLIFONÍA (2026-08-29h) ──────────────────────────────────
    // Ocho voces sonando a la vez son ocho veces la amplitud. En el peor caso legítimo del juego
    // —un clear de 4 líneas (4 capas) + combo + fusión + nacimiento de Teika + estrella recogida—
    // la suma de amplitudes llega a 6,7: **+16,5 dB** sobre una voz sola. Eso es exactamente lo
    // que se oye como «está normal y de repente se sube el volumen», y no es un fallo de código:
    // es física, y ocurre justo en los mejores momentos de la partida, que es cuando más molesta.
    //
    // SoundPool no tiene bus ni limitador, así que se hace a mano: se lleva la cuenta de las voces
    // disparadas en los últimos [VOICE_WINDOW_MS] y, si la suma ya va alta, la voz nueva entra más
    // baja. El primero en llegar —que es siempre el golpe principal de la jugada, disparado con
    // PRIO_KEY antes que sus capas— entra a volumen completo; lo que se recorta es el adorno.

    /** Techo de suma de amplitudes simultáneas. 2,5 ≈ +8 dB sobre una voz sola: grande, no brutal. */
    private val VOICE_SUM_CEILING = 2.5f
    /** Ventana en la que dos disparos se consideran simultáneos. */
    private val VOICE_WINDOW_MS = 120L
    /** Suelo: por saturado que esté, una voz nunca baja de esta fracción de lo que pidió. */
    private val VOICE_MIN_FACTOR = 0.35f

    private class Voice(val atMs: Long, val volume: Float, val soundId: Int)

    private val activeVoices = ArrayDeque<Voice>()

    /** Volumen con el que la voz puede entrar sin que el conjunto se dispare. */
    private fun limitedVolume(soundId: Int, volume: Float, choked: Boolean): Float {
        val now = SystemClock.uptimeMillis()
        synchronized(activeVoices) {
            while (activeVoices.isNotEmpty() && now - activeVoices.first().atMs > VOICE_WINDOW_MS) {
                activeVoices.removeFirst()
            }
            // La copia anterior de esta muestra se acaba de cortar: ya no suena, no ocupa sitio.
            // Sin esto el segundo toque rápido entraría recortado por el limitador.
            if (choked) activeVoices.removeAll { it.soundId == soundId }
            var sum = 0f
            for (v in activeVoices) sum += v.volume
            val room = (VOICE_SUM_CEILING - sum).coerceAtLeast(0f)
            val out = volume.coerceAtMost(maxOf(room, volume * VOICE_MIN_FACTOR))
            activeVoices.addLast(Voice(now, out, soundId))
            return out
        }
    }

    // ── UNA VOZ POR MUESTRA (2026-09-30, reporte del usuario) ─────────────────
    // «La ficha no válida suena bajita, pero si la repito rápido se oye cada vez más fuerte.» Era
    // la suma de copias: `snd_invalid` dura 600 ms y cada toque abría un stream NUEVO encima de los
    // anteriores, que seguían sonando. Cuatro toques en medio segundo = cuatro copias a la vez =
    // cuatro veces la amplitud (+12 dB). El limitador no lo frenaba: su techo es ABSOLUTO (2,5) y
    // cuatro copias de un cue bajito (0,44) caben enteras debajo. Lo mismo con el clic de UI, el
    // «pop» de colocar o la fusión en cadena.
    //
    // Ahora es como un «choke» de caja de ritmos: al volver a disparar una muestra se corta la copia
    // que aún sonaba, así que repetir un sonido lo REINICIA y nunca lo sube. La única excepción son
    // las capas de acorde del clear ([playClear]), que apilan la misma muestra a propósito
    // (`layer = true`).
    private class LiveStream(val streamId: Int, val volume: Float)

    private val lastStreamBySample = java.util.concurrent.ConcurrentHashMap<Int, LiveStream>()

    // P-382 (2026-09-30) · el corte no es seco: un `stop()` a media onda deja un clic audible en
    // las muestras largas (fusión, combo). La copia vieja baja a cero en [CHOKE_FADE_STEPS] ×
    // [CHOKE_FADE_STEP_MS] (~30 ms) y solo entonces se para. Tan corto que no se oye como fundido,
    // solo quita el chasquido; y la copia nueva arranca YA, sin esperar a que la vieja se apague.
    private val CHOKE_FADE_STEPS = 5
    private val CHOKE_FADE_STEP_MS = 6L

    private fun fadeOutAndStop(stream: LiveStream) {
        sfxScope.launch {
            try {
                for (i in CHOKE_FADE_STEPS - 1 downTo 1) {
                    if (released) return@launch
                    val v = stream.volume * i / CHOKE_FADE_STEPS
                    soundPool.setVolume(stream.streamId, v, v)
                    delay(CHOKE_FADE_STEP_MS)
                }
                if (!released) soundPool.stop(stream.streamId)
            } catch (_: Exception) {
                // Pool liberado a mitad del fundido: no queda nada que parar.
            }
        }
    }

    private fun emitReadySample(soundId: Int, volume: Float, rate: Float, priority: Int, layer: Boolean = false): Int {
        if (released || masterMuted) return 0
        var choked = false
        if (!layer) {
            // setVolume()/stop() sobre un stream que ya terminó son inocuos: SoundPool ignora ids caducados.
            lastStreamBySample.remove(soundId)?.let { fadeOutAndStop(it); choked = true }
        }
        val vol = limitedVolume(soundId, volume, choked)
        try {
            val streamId = soundPool.play(soundId, vol, vol, priority, 0, rate.coerceIn(0.5f, 2f))
            if (!layer && streamId != 0) lastStreamBySample[soundId] = LiveStream(streamId, vol)
            if (com.cukiti.teikafusion.BuildConfig.DEBUG && soundId == lavaSurgeId) {
                Log.d("LavaAudio", "stream=$streamId volume=$vol priority=$priority")
            }
            if (streamId == 0 && reportedPlaybackFailures.add(soundId)) {
                Log.w("SoundManager", "SoundPool.play returned 0 for sample=$soundId")
            }
            return streamId
        } catch (_: Exception) {
            if (reportedPlaybackFailures.add(soundId)) {
                Log.w("SoundManager", "SoundPool.play failed for sample=$soundId")
            }
            return 0
        }
    }

    /** Invalida efectos diferidos de la ronda anterior sin destruir el SoundPool. */
    fun beginAudioContext() {
        sfxGeneration++
        pendingPlays.clear()
        synchronized(activeVoices) { activeVoices.clear() }
        lastStreamBySample.clear()
    }

    // ── FOCO DE AUDIO ─────────────────────────────────────────────────────────
    //
    // **El bug que arregla (2026-08-29i)**: el usuario oía que «la música se amplifica sola». No
    // era la app: era el SISTEMA. Otras apps del móvil (Instagram, WhatsApp) piden foco de audio
    // con `GAIN_TRANSIENT_MAY_DUCK` constantemente; Android entonces **agacha la reproducción de
    // este juego y se la devuelve de golpe** al soltarlo. Se vio en `dumpsys audio`:
    // `Notify on duck: true` y `AudioHardening background playback would be muted for
    // com.cukiti.teikafusion, level: partial` justo en los minutos del playtest.
    //
    // Teika Fusión no pedía foco —el único `AudioManager` del proyecto era `volumeControlStream`—,
    // así que ni se enteraba ni podía suavizarlo. Ahora lo pide y **gestiona el agachado él mismo**
    // con una rampa, en vez de sufrir el corte brusco del sistema.

    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    /** Cuánto se agacha la música cuando otra app pide paso. */
    private val FOCUS_DUCK = 0.35f
    private val FOCUS_RAMP_STEPS = 14
    private val FOCUS_RAMP_STEP_MS = 25L

    @Volatile private var focusFactor: Float = 1f
    private var focusJob: kotlinx.coroutines.Job? = null
    private var focusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false
    /** Pausado por pérdida de foco (no por el ciclo de vida): así se sabe si hay que volver. */
    private var pausedByFocus = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                rampFocusTo(1f)
                if (pausedByFocus) {
                    pausedByFocus = false
                    if (bgmPlaybackAllowed && bgmPrepared) {
                        try { bgmPlayer?.start() } catch (_: Exception) {}
                    }
                }
            }
            // Otra app suena un momento: nos agachamos NOSOTROS, con rampa.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> rampFocusTo(FOCUS_DUCK)
            // Una llamada, un vídeo: callamos del todo, pero recordamos que hay que volver.
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (bgmPrepared) {
                    try { bgmPlayer?.pause(); pausedByFocus = true } catch (_: Exception) {}
                }
            }
            // Pérdida definitiva: se suelta y no se vuelve solo.
            AudioManager.AUDIOFOCUS_LOSS -> {
                if (bgmPrepared) try { bgmPlayer?.pause() } catch (_: Exception) {}
                pausedByFocus = false
                abandonAudioFocus()
            }
        }
    }

    /** Rampa del factor de foco. Un salto seco es justo lo que se oía como «se subió sola». */
    private fun rampFocusTo(target: Float) {
        focusJob?.cancel()
        val from = focusFactor
        if (from == target) return
        focusJob = sfxScope.launch(musicThread) {
            try {
                for (i in 1..FOCUS_RAMP_STEPS) {
                    focusFactor = from + (target - from) * (i / FOCUS_RAMP_STEPS.toFloat())
                    applyBgmVolume()
                    delay(FOCUS_RAMP_STEP_MS)
                }
            } finally {
                focusFactor = target
                applyBgmVolume()
            }
        }
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            // false = queremos que nos AVISEN del duck para hacerlo nosotros con rampa, en vez de
            // que el sistema nos pause o nos corte el volumen de golpe.
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focusRequest = req
        hasAudioFocus = try {
            audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        } catch (_: Exception) { false }
    }

    private fun abandonAudioFocus() {
        val req = focusRequest ?: return
        try { audioManager.abandonAudioFocusRequest(req) } catch (_: Exception) {}
        focusRequest = null
        hasAudioFocus = false
        focusJob?.cancel()
        focusFactor = 1f
    }

    /**
     * Aparta la MÚSICA un momento para que el golpe se oiga (ducking). Lo llaman los propios cues
     * grandes, así que ningún call site tiene que acordarse. Reentrante: un segundo golpe reinicia
     * la cuenta en vez de dejar la música a medio subir.
     */
    /**
     * Instante (uptime) hasta el que el duck en vuelo debe seguir agachado. Un golpe nuevo
     * EXTIENDE este plazo en vez de reiniciar el duck entero — ver [duckMusic].
     */
    @Volatile private var duckHoldUntilMs: Long = 0L

    /**
     * Aparta la música un momento para que quepa un golpe grande.
     *
     * **El bug que arregla (2026-08-29f, reportado como «la música se sube y se baja sola»):** cada
     * llamada CANCELABA el duck en vuelo y empezaba uno nuevo desde el principio. Al terminar un
     * nivel se disparan hasta cuatro seguidos —victoria, subida de rango, mural completo, compañero
     * desbloqueado—, y un MEGA encadenado dispara varios más. Resultado: la música bajaba de golpe,
     * empezaba a subir, volvía a caer a mitad de rampa, subía otra vez… varios segundos de yoyó.
     * Cada duck es de 950 ms, así que cuatro seguidos son casi cuatro segundos de vaivén.
     *
     * Ahora los golpes seguidos **COALESCEN**: si ya hay un duck agachado, el golpe nuevo solo
     * EXTIENDE el tiempo que se queda abajo. Un único agachado que dura un poco más, y una sola
     * subida limpia al final. Y nunca se salta hacia abajo a mitad de rampa.
     */
    private fun duckMusic() {
        val player = bgmPlayer ?: return
        // Música inaudible (mute maestro o slider a cero): no hay nada que apartar, y agachar un
        // silencio solo servía para dejar `duckFactor` a medias si el player cambiaba entretanto.
        if (masterMuted || musicVolume <= 0f) return
        val now = SystemClock.uptimeMillis()

        // ¿Hay un duck vivo? Entonces esto NO es un duck nuevo: es el mismo momento grande que
        // sigue. Se alarga el plazo y se deja trabajar al que ya está corriendo.
        val alive = duckJob?.isActive == true
        if (alive) {
            duckHoldUntilMs = maxOf(duckHoldUntilMs, now + DUCK_HOLD_MS)
            return
        }

        duckHoldUntilMs = now + DUCK_HOLD_MS
        val generation = bgmGeneration
        duckJob?.cancel()
        val myDuck = ++duckGeneration
        duckJob = sfxScope.launch(musicThread) {
            try {
                if (bgmPlayer !== player || generation != bgmGeneration) return@launch
                duckFactor = DUCK_DEPTH
                applyBgmVolume(player)
                // Se mantiene abajo mientras sigan llegando golpes (cada uno alarga el plazo).
                while (true) {
                    val left = duckHoldUntilMs - SystemClock.uptimeMillis()
                    if (left <= 0L) break
                    if (bgmPlayer !== player || generation != bgmGeneration) return@launch
                    delay(left.coerceAtMost(DUCK_HOLD_MS))
                }
                for (i in 1..DUCK_STEPS) {
                    if (bgmPlayer !== player || generation != bgmGeneration) return@launch
                    // Un golpe durante la SUBIDA vuelve a alargar el plazo: se reinicia el agachado
                    // desde donde está, sin saltar hacia abajo.
                    if (duckHoldUntilMs > SystemClock.uptimeMillis()) {
                        duckFactor = minOf(duckFactor, DUCK_DEPTH)
                        applyBgmVolume(player)
                        while (duckHoldUntilMs > SystemClock.uptimeMillis()) {
                            if (bgmPlayer !== player || generation != bgmGeneration) return@launch
                            delay(DUCK_STEP_MS)
                        }
                    }
                    duckFactor = DUCK_DEPTH + (1f - DUCK_DEPTH) * (i / DUCK_STEPS.toFloat())
                    applyBgmVolume(player)
                    delay(DUCK_STEP_MS)
                }
            } finally {
                // Pase lo que pase —fin normal, cancelación por otro golpe, player liberado a media
                // rampa— la música NO puede quedarse agachada. Solo restaura el duck vigente: si otro
                // ya tomó el relevo, es él quien manda sobre el factor.
                if (myDuck == duckGeneration) {
                    duckFactor = 1f
                    applyBgmVolume()
                }
            }
        }
    }

    // ── API pública — SFX ─────────────────────────────────────────────────────

    /** Id de la muestra de COLOCAR según el skin activo. */
    private val placeSampleId: Int get() = when (activeSkinId) {
        "cristal" -> placeCrystalId  // Cristal: tin de vidrio
        "madera" -> placeWoodId      // Madera: toc de tabla
        "lava" -> placeLavaId        // Lava: golpe de roca que chisporrotea
        "hielo" -> placeIceId        // Hielo: cubito que tintinea al posarse
        "espectro" -> placeEspectroId  // Espectro: «bup» fantasmal que baja
        "constelacion" -> placeConstelacionId  // Constelación: campanita celeste
        "vacio" -> placeVacioId  // Vacío: «vum» grave que se hunde
        "metal" -> placeMetalId  // Metal: chapa que se asienta con un clic
        "neon_tubo" -> placeCrystalId  // Neón: el tin limpio del Cristal (P-411: ninguna toma propia convenció)
        "dorado" -> placeGoldId  // Dorado (P-411): «ding» de tesoro con destello
        "fall3d" -> placeCeramicId   // Cerámica: clac seco de porcelana
        else     -> placeClassicId
    }

    /** Id de la muestra de LIMPIAR LÍNEA según el skin activo. */
    private val clearSampleId: Int get() = when (activeSkinId) {
        "cristal" -> clearCrystalId  // Cristal: la gema estalla en esquirlas
        "madera" -> clearWoodId      // Madera: la tabla se astilla
        "lava" -> clearLavaId        // Lava: la roca revienta y sisea
        "hielo" -> clearIceId        // Hielo: crujido y tintineo de trozos
        "espectro" -> clearEspectroId  // Espectro: el fantasma se va con un «uuuh» que sube
        "constelacion" -> clearConstelacionId  // Constelación: arpegio de estrellas fugaces
        "vacio" -> clearVacioId  // Vacío: succión que sube y un golpe al cerrarse
        "metal" -> clearMetalId  // Metal: la chapa se dobla y saltan los remaches
        "neon_tubo" -> clearNeonId  // Neón (P-411): el tubo se sobrecarga y se apaga
        "dorado" -> clearGoldId  // Dorado (P-411): lluvia de monedas
        "fall3d" -> clearCeramicId   // Cerámica: porcelana que se astilla al romper
        else     -> clearClassicId
    }

    /** Small, distinct motifs use existing samples; the audio mixer still owns mute and gain. */
    fun playRuleCue(kind: String, size: Int = 1, lastHitMs: Int = 220, hitTimes: List<Int> = emptyList()) {
        val generation = sfxGeneration
        fun cue(sample: Int, volume: Float, rate: Float = 1f, delayMs: Long = 0) {
            if (delayMs == 0L) play(sample,volume,rate,priority = PRIO_DECOR)
            else sfxScope.launch { delay(delayMs); if (generation == sfxGeneration) play(sample,volume,rate,priority = PRIO_DECOR) }
        }
        when (kind) {
            "constellation" -> { cue(placeConstelacionId, volume = .45f); cue(clearConstelacionId,volume = .65f,delayMs = 180) }
            "forge" -> { cue(placeMetalId,volume=.42f,rate=.85f); cue(clearCrystalId,volume=.28f,rate=1.2f,delayMs=520); cue(placeCrystalId,volume=.32f,rate=1.15f,delayMs=820) }
            "forge_reduced" -> cue(placeMetalId,volume=.32f,rate=1.05f)
            "trampoline" -> { cue(placeClassicId,volume = .45f,rate = .8f); cue(teikaAppearId,volume = .5f,rate = 1.2f,delayMs = 55) }
            "cut" -> { cue(placeMetalId,volume = .35f); cue(clearCrystalId,volume = .4f,rate = 1.3f,delayMs = 110) }
            "surf" -> { cue(clearVacioId,volume=.35f,rate=.8f); cue(placeClassicId,volume=.30f,rate=1.2f,delayMs=140) }
            "orbit" -> { cue(placeMetalId,volume=.28f,rate=.72f); cue(placeCrystalId,volume=.35f,rate=1.05f,delayMs=820) }
            "orbit_reduced" -> cue(placeCrystalId,volume=.28f,rate=1.05f)
            "portal_swap" -> { cue(clearCrystalId,volume=.32f,rate=.75f); cue(placeCrystalId,volume=.38f,rate=1.25f,delayMs=780) }
            "portal_swap_reduced" -> cue(placeCrystalId,volume=.3f,rate=1.15f)
            "compactor" -> { cue(placeMetalId,volume=.35f,rate=.72f); cue(placeClassicId,volume=.45f,rate=.85f,delayMs=780) }
            "turbine" -> { cue(placeMetalId,volume=.28f,rate=.7f); cue(clearVacioId,volume=.38f,rate=.8f) }
            "pinball_launch" -> { cue(placeCrystalId,volume=.35f,rate=.85f); cue(clearVacioId,volume=.4f,rate=1.25f,delayMs=80) }
            "pinball_hit" -> { cue(placeMetalId,volume=.3f,rate=1.25f); cue(clearCrystalId,volume=.2f,rate=1.4f,delayMs=40) }
            "relay" -> { cue(placeCrystalId,volume=.25f,rate=.8f); cue(clearVacioId,volume=.55f,rate=1.15f,delayMs=240); cue(clearCrystalId,volume=.25f,rate=1.35f,delayMs=390) }
            "reserve_swap" -> { cue(placeClassicId,volume=.30f,rate=1.1f); cue(placeCrystalId,volume=.20f,rate=1.35f,delayMs=260) }
            "relay_charge" -> cue(placeCrystalId,volume=.22f,rate=1.4f)
            "claw" -> { cue(placeMetalId,volume=.32f,rate=.85f); cue(teikaAppearId,volume=.28f,rate=.9f,delayMs=240) }
            "claw_land" -> { cue(placeMetalId,volume=.30f,rate=1.15f); cue(placeCrystalId,volume=.25f,rate=1.3f,delayMs=70) }
            "wrecking_ball" -> { cue(placeMetalId,volume=.3f,rate=.7f)
                hitTimes.distinct().take(5).forEach { hit->cue(placeWoodId,volume=.2f,rate=.85f,delayMs=hit.toLong()) }
                cue(clearVacioId,volume=.4f,rate=.8f,delayMs=lastHitMs.toLong())
                cue(placeMetalId,volume=.2f,rate=.8f,delayMs=lastHitMs+100L) }
            "ball_reduced" -> cue(placeMetalId,volume=.3f,rate=.9f)
            "reactor" -> { cue(clearCrystalId,volume=.28f,rate=.8f); cue(clearVacioId,volume=.42f,rate=1.1f,delayMs=220); cue(placeCrystalId,volume=.22f,rate=1.3f,delayMs=lastHitMs.coerceIn(220,3700).toLong()) }
            "reactor_reduced" -> cue(clearCrystalId,volume=.3f,rate=1.2f)
            "cut_swap" -> cue(placeCrystalId,volume = .35f,rate = 1.1f)
        }
    }

    /** Clic al colocar una pieza (variante según el skin de bloque activo). */
    fun playPop() = play(placeSampleId)

    /** Aparición de un Teika nivel 1 (brota en el tablero al completar línea/columna). */
    fun playTeikaAppear() = play(teikaAppearId)

    /**
     * Limpiar línea(s). El sonido **crece en capas** con cuántas líneas caen a la vez: la voz base
     * suena siempre igual, y cada línea EXTRA añade una voz más aguda y más suave, escalonada unos
     * milisegundos → un arpegio ascendente que se convierte en acorde. Romper 4 de golpe no suena
     * "igual pero más fuerte": suena *más rico*.
     *
     * Las capas usan intervalos de acorde mayor (fundamental → 3ª → 5ª → 8ª) sobre la MISMA muestra,
     * cambiando el `rate` del SoundPool. Sin assets nuevos, y respeta el skin de bloque activo.
     *
     * Mezcla (evitar saturación): el volumen de cada capa DECRECE, así la energía total crece de
     * forma sublineal aunque se apilen voces. El techo de [MAX_CLEAR_LAYERS] deja margen de
     * polifonía dentro de [MAX_CONCURRENT_SFX] para el combo, que suele sonar en el mismo instante.
     *
     * @param lines nº de líneas limpiadas a la vez (1 = clear simple; se satura en [MAX_CLEAR_LAYERS]).
     */
    fun playClear(lines: Int = 1) {
        val id = clearSampleId
        val generation = sfxGeneration
        // La voz base es el GOLPE de la jugada: prioridad máxima, no puede caerse del mezclador.
        // Las capas de acorde son adorno → prioridad 0: si el pool se llena, que se pierdan ellas.
        play(id, priority = PRIO_KEY)
        val layers = lines.coerceIn(1, MAX_CLEAR_LAYERS)
        if (layers <= 1) return
        sfxScope.launch {
            for (i in 1 until layers) {
                delay(CLEAR_LAYER_STAGGER_MS)
                if (generation != sfxGeneration) return@launch
                play(id, volume = CLEAR_LAYER_VOLUME[i], rate = CLEAR_LAYER_RATE[i], priority = PRIO_DECOR, layer = true)
            }
        }
    }

    /**
     * Sincroniza el golpe del clear con el primer frame del VFX publicado por el tablero. El
     * ViewModel cambia el estado antes de que Compose pueda pintar ese frame; unos pocos ms de
     * margen evitan que el sonido se adelante visualmente sin retrasar la jugada perceptiblemente.
     */
    /**
     * El bloque-Teika abre su cápsula gashapon. Suena UNA vez por pasada aunque se rompan varios, y
     * con el mismo retraso que su VFX ([delayMs] = el aviso de la línea), para que el chasquido caiga
     * justo cuando la cápsula se abre y no antes; no depende del skin equipado.
     */
    fun playTeikaSeedBreak(delayMs: Long = 0L) {
        if (delayMs <= 0L) { play(teikaSeedBreakId, volume = 0.9f); return }
        val generation = sfxGeneration
        sfxScope.launch {
            delay(delayMs)
            if (generation == sfxGeneration) play(teikaSeedBreakId, volume = 0.9f)
        }
    }

    fun playClearAtImpact(lines: Int = 1, delayMs: Long = DEFAULT_IMPACT_DELAY_MS) {
        if (delayMs <= 0L) playClear(lines)
        else {
            val generation = sfxGeneration
            sfxScope.launch {
                delay(delayMs)
                if (generation == sfxGeneration) playClear(lines)
            }
        }
    }

    /**
     * Una estrella-objetivo se recoge en un reventón y vuela al panel.
     *
     * Hasta el 2026-08-29 pedía prestado `snd_teikoin_gain`, así que **recoger una estrella sonaba
     * a cobrar una moneda** — y son las dos economías distintas del juego. Ya tiene muestra propia:
     * un «tin» cristalino más agudo y más corto que la moneda.
     */
    fun playTargetCollect() = play(targetCollectId, volume = 0.85f * UI_MIX)

    /**
     * Fusión de Teikas = aparición del teika resultante. Una sola muestra (snd_merge_2, burbuja de
     * agua), pero el TONO depende del nivel de los Teikas que se funden (decisión del usuario,
     * 2026-09-29): cuanto más grande la burbuja, más grave — ver [MERGE_RATE]. Para el 4+4, además,
     * [playLevelUp] (snd_mega) anuncia la limpieza del tablero.
     */
    fun playMerge(level: Int = 1) =
        play(mergeId, rate = MERGE_RATE[level.coerceIn(1, MERGE_RATE.size) - 1], priority = PRIO_KEY)

    /** Tono de la fusión por nivel (T1…T4): ~5 semitonos del Teika pequeño al grande. */
    private val MERGE_RATE = floatArrayOf(1.15f, 1f, 0.87f, 0.76f)

    /** Explosión triunfal del Mega Teika (nivel 5) limpiando el tablero. */
    fun playLevelUp() { duckMusic(); play(megaId, priority = PRIO_KEY) }

    /** T4+T4: la carga del colapso estelar (~1,1 s de subida hasta la implosión). */
    fun playMegaCollapse() { duckMusic(); play(megaCollapseId, priority = PRIO_KEY) }

    /** Jingle de derrota al perder la partida (muestra real). */
    fun playGameOver() {
        play(gameOverId, priority = PRIO_KEY)
        // La música (la del bioma o la de menús que la sustituye) se aparta mientras dura el jingle y
        // vuelve con una rampa suave. Ver [jingleFactor].
        jingleJob?.cancel()
        // Generación, como el duck: el `finally` del jingle cancelado llega DESPUÉS de esta línea y,
        // sin ella, devolvía la música a tope en mitad del jingle nuevo (2026-10-01).
        val myJingle = ++jingleGeneration
        jingleFactor = JINGLE_MUSIC_FACTOR
        applyBgmVolume()
        jingleJob = sfxScope.launch(musicThread) {
            try {
                delay(GAME_OVER_JINGLE_MS - JINGLE_RELEASE_MS)
                val steps = (JINGLE_RELEASE_MS / DUCK_STEP_MS).toInt()
                for (i in 1..steps) {
                    jingleFactor = JINGLE_MUSIC_FACTOR + (1f - JINGLE_MUSIC_FACTOR) * (i / steps.toFloat())
                    applyBgmVolume()
                    delay(DUCK_STEP_MS)
                }
            } finally {
                if (myJingle == jingleGeneration) {
                    jingleFactor = 1f
                    applyBgmVolume()
                }
            }
        }
    }

    /** La transición del círculo (iris) al entrar y salir de un nivel. Suena al empezar a cerrarse. */
    fun playIrisTransition() = play(irisTransitionId, priority = PRIO_NORMAL)
    /** Apertura del círculo: se dispara cuando empieza a revelar la pantalla nueva. */
    fun playIrisOpen() = play(irisOpenId, priority = PRIO_NORMAL)

    // ── UI / economía ─────────────────────────────────────────────────────────

    /**
     * Tap en una pestaña del navbar inferior. Suena un pelín más grave que el clic genérico
     * ([playUiClick]) para que cambiar de pestaña no se confunda con pulsar un botón dentro de
     * ella: misma muestra, `rate` distinto — el mismo truco del combo y de las bobinas.
     */
    fun playNavTab() = play(uiClickId, volume = 0.82f * UI_MIX, rate = 0.92f, priority = PRIO_DECOR)

    /**
     * Clic genérico de UI. Se usa en todos los botones accionables de menú (atrás, ranking, perfil,
     * jugar, ajustes, biomas, niveles, equipar, comprar…).
     */
    fun playUiClick() = play(uiClickId, volume = 0.8f * UI_MIX, priority = PRIO_DECOR)

    /** El jugador consigue teikoins (recompensa de juego o compra en tienda). */
    fun playTeikoinGain() = play(teikoinGainId, volume = UI_MIX)

    /** Confirmación de GASTO de teikoins (cosméticos, vidas, mejoras de habilidad…). */
    fun playPurchase() = play(purchaseId, volume = UI_MIX)

    /** Botón JUGAR — lanzamiento de un nivel/partida. */
    fun playLaunch() = play(launchId)
    /** Lanzamiento de Flora: impulso propio, separado de la campana de inicio. */
    fun playFloraLaunch() = play(floraLaunchId)

    /**
     * INTRO (P-374) · la firma del estudio: suena al completarse «CUKITI» — un clic brillante de las
     * líneas encajando y un acorde que se abre mientras el color se va al fondo (`snd_intro_sting`,
     * ElevenLabs, v1 de tres, elegida por el usuario el 2026-09-30). Es el único cue, con
     * [playIntroStart], que atraviesa la cortina de carga — la cortina existe para callar las
     * recompensas que la sincronización otorga por detrás, no la propia intro.
     */
    fun playIntroSting() = play(introStingId, priority = PRIO_KEY, bypassCurtain = true)

    /** INTRO (P-372) · «Toca para jugar» en la pantalla de título. */
    fun playIntroStart() = play(launchId, volume = 0.9f, priority = PRIO_KEY, bypassCurtain = true)

    /** Se pierde una vida (corazón) al ser derrotado. */
    fun playLifeLost() = play(lifeLostId)

    /** Intento de jugar sin vidas (overlay "Sin vidas"). */
    fun playNoLives() = play(noLivesId)

    /**
     * Combo escalando: el mismo "ding" sube de tono cuanto mayor la racha [streak]
     * (rate de SoundPool), reforzando la sensación de combo creciente (refuerzo pavloviano).
     * Curva pronunciada en los primeros eslabones (donde más se nota) + techo alto para que
     * los combos largos sigan subiendo de tono perceptiblemente.
     */
    fun playCombo(streak: Int) =
        play(comboId, rate = (1f + (streak - 1) * 0.085f).coerceIn(1f, 2.2f))

    /**
     * Tic del COUNT-UP de puntuación en las pantallas de resultado: el mismo "tick" de UI sube de
     * tono con el progreso 0→1 del conteo (rate de SoundPool, mismo truco que el combo). Volumen
     * bajo — es un tictac de contador, no un golpe.
     */
    private var lastScoreTickAtMs = 0L

    fun playScoreTick(progress: Float) {
        // Los overlays llaman cada ~48 ms. Este margen mantiene el tic musical, pero evita que una
        // recomposición o un callback duplicado lo convierta en una ametralladora de samples.
        val now = SystemClock.uptimeMillis()
        if (now - lastScoreTickAtMs < 28L) return
        lastScoreTickAtMs = now
        play(
            // Comparte el WAV aprobado de UI. El contenedor de 480 ms no es la duración del
            // contacto (~115 ms sobre umbral); no asumir ausencia de solapamiento por su tamaño.
            uiClickId,
            volume = 0.35f * UI_MIX,
            rate = (1f + progress.coerceIn(0f, 1f) * 0.9f),
            priority = PRIO_DECOR
        )
    }

    /** Toño / Miasma Venenoso: siseo ácido del gas tóxico corroyendo el tablero. */
    fun playAbilityMiasma() = play(abilityMiasmaId, volume = 0.9f)

    /** Reynaldo / Parto Real: firma regia de nacimiento de T1 desde padres T2+. */
    fun playAbilityBirth() = play(abilityBirthId, volume = 0.9f)

    /** Maite / Siesta: glissando onírico del desplome + filas-bonus cayendo. */
    fun playAbilityDream() = play(abilityDreamId, volume = 0.9f)

    /** Timoteo / Puzzle Deslizante: la madera se desliza como un rompecabezas y encaja. */
    fun playAbilitySlide() = play(abilitySlideId, volume = 0.9f)

    /** Rayco / Energía Cruzada: chisporroteo eléctrico al energizar los Teikas. */
    fun playAbilityEnergize() = play(abilityEnergizeId, volume = 0.9f)

    /** Iñaki: señal compartida al prender la bandeja y al arrasar filas/columnas con fuego. */
    fun playAbilityFire() = play(abilityFireId, volume = 0.9f)

    /** Flora / Cañón Floral: flor floreciendo que carga el Teika en el cañón. */
    fun playAbilityBloom() = play(abilityBloomId, volume = 0.9f)

    /** Morti / Posesión: los bloques se vuelven espectrales, atraviesan y se solidifican. */
    fun playAbilityGhost() = play(abilityGhostId, volume = 0.9f)

    /** Nieves / Estalactitas: shimmer de cristalización + whoosh + shatter de hielo. */
    fun playAbilityIcicle() = play(abilityIcicleId, volume = 0.9f)

    /** Cristóbal / Escudo Cristalino: el blindaje se forma y escanea el tablero. */
    fun playAbilityShield() = play(abilityShieldId, volume = 0.9f)

    /** Kenji / Corte de Sombras: una ráfaga continua, con 3/5/7 tajos visuales según mejora. */
    fun playAbilitySlash() = play(abilitySlashId, volume = 0.9f)

    /** Chueta / El Banquete: la mandíbula se abre desde abajo, muerde y traga. */
    fun playAbilityFeast() = play(abilityFeastId, volume = 0.9f)

    /** Estela / Deseo Estelar: firma mágica al conceder la ficha en la bandeja. */
    fun playAbilityWish() = play(abilityWishId, volume = 0.9f)

    /** Galo / Eclipse Vivo: el eclipse cae sobre el rincón denso y lo pulveriza. */
    fun playAbilityEclipse() = play(abilityEclipseId, volume = 0.9f)

    /** Portales, implosión, corriente del tablero, construir desde el banco y brújula. */
    fun playBoardShift() = play(boardShiftId, volume = 0.9f)

    /** Reacción en cadena de barriles. */
    fun playBarrelBlast() = play(barrelBlastId, volume = 0.9f)

    /** El huevo eclosiona y suelta su Teika. */
    fun playEggHatch() = play(eggHatchId, volume = 0.9f)

    /** Se coloca la pieza imán. */
    fun playMagnetPlace() = play(magnetPlaceId, volume = 0.9f)

    // ── Mecánicas de bioma en partida ─────────────────────────────────────────

    /** Dojo: la katana parte el tablero en dos (tajo seco y decidido). */
    fun playNinjaSlash() = play(ninjaSlashId, volume = 0.95f * BIOME_MIX)

    /** Dojo: sello kintsugi dorado al reparar las dos mitades del tablero. */
    fun playNinjaRepair() = play(ninjaRepairId, volume = 0.9f * BIOME_MIX)

    /**
     * Cielo Estelar: el astro emerge tras la inactividad (aviso telegrafiado). El rate distingue el
     * cuerpo — Sol (1) grave y amenazante, Luna (2) algo más agudo y etéreo — sobre la misma muestra.
     */
    fun playAstroEmerge(body: Int) =
        play(astroEmergeId, volume = 0.8f * BIOME_MIX, rate = if (body == 2) 1.12f else 0.94f)

    /** Cielo Estelar: el Sol incinera el Teika marcado (láser que carga y estalla). */
    fun playSunStrike() = play(sunStrikeId, volume = 0.95f * BIOME_MIX)

    /** Cielo Estelar: la Luna se lleva los bloques marcados (levitación y absorción mágica). */
    fun playMoonSteal() = play(moonStealId, volume = 0.9f * BIOME_MIX)

    /** Glotón: la boca se abre en un rincón (aviso, gruñido hambriento). */
    fun playGluttonOpen() = play(gluttonOpenId, volume = 0.85f * BIOME_MIX)

    /** Glotón: el mordisco devora la esquina del tablero. */
    fun playGluttonBite() = play(gluttonBiteId, volume = 0.95f * BIOME_MIX)

    /** ESPADA DEL REY (P-294): la espada CAE y parte la línea (sample del antiguo cierre del asedio). */
    fun playSwordStrike() = play(kingWallRiseId, volume = 0.95f * BIOME_MIX)

    /** ESPADA DEL REY: el Rey ENVAINA (rompiste una línea que la cruzaba). Sample del antiguo derribo. */
    fun playSwordSheathe() = play(kingWallBreakId, volume = 0.95f * BIOME_MIX)
    /** Rotura seca de un bloque por impacto (el plomo de P-239). Muestra propia desde el 2026-09-28. */
    fun playStoneBreak() = play(leadCrushId, volume = 0.95f * BIOME_MIX)

    /** Trono · la pieza de ajedrez cae y SE QUEDA en el tablero (2026-09-28). */
    fun playKingPieceLand() = play(kingPieceLandId, volume = 0.95f * BIOME_MIX)

    /** Trono · el jugador rompe la fila o la columna de la pieza quieta y la ECHA. */
    fun playKingPieceEvict() = play(kingPieceEvictId, volume = 0.9f * BIOME_MIX)

    /** Mansión Fantasma: el reflector roba un Teika/estrellas (lamento espectral). */
    /** Espejo Embrujado (P-246): el DESTELLO del espejo al reflejar (sample de la antigua cacería). */
    fun playMirrorFlash() = play(ghostHuntId, volume = 0.9f * BIOME_MIX)

    /** Mansión Fantasma: la oscuridad/niebla desciende (susurro). */
    /** Espejo Embrujado: el cristal se EMPAÑA (primer aviso). Sample de la antigua niebla. */
    fun playMirrorMist() = play(ghostFogId, volume = 0.8f * BIOME_MIX)

    /** Glaciar: oleada de escarcha que congela un Teika. */
    fun playGlacierFreeze() = play(glacierFreezeId, volume = 0.9f * BIOME_MIX)

    /** Pantano Tóxico: el veneno se propaga por la madera (siseo corrosivo). */
    fun playToxicSpread() = play(toxicSpreadId, volume = 0.85f * BIOME_MIX)

    /** Tóxico · el veneno SALPICA a otra zona (2026-09-28). */
    fun playPoisonSplash() = play(poisonSplashId, volume = 0.9f * BIOME_MIX)

    /** Tóxico · una línea LAVA el veneno de sus bloques (una vez por pasada, no por bloque). */
    fun playPoisonWash() = play(poisonWashId, volume = 0.85f * BIOME_MIX)

    /** Jardín · castigo: brotan enredaderas. */
    fun playVineSprout() = play(vineSproutId, volume = 0.9f * BIOME_MIX)

    /** Jardín · el Teika se libera de la raíz. */
    fun playPlantRescue() = play(plantRescueId, volume = 0.9f * BIOME_MIX)

    /** Dojo · la grieta se cierra sola y deja oro (kintsugi). */
    fun playKintsugiSeal() = play(kintsugiSealId, volume = 0.9f * BIOME_MIX)

    /** Regla Testarudos · el bloque AGUANTA la línea / por fin CEDE (una vez por pasada). */
    fun playStubbornResist() = play(stubbornResistId, volume = 0.85f * BIOME_MIX)
    fun playStubbornBreak() = play(stubbornBreakId, volume = 0.85f * BIOME_MIX)

    /** Regla Molde · la ficha encaja en el molde. */
    fun playMoldStamp() = play(moldStampId, volume = 0.85f * BIOME_MIX)

    /** Regla Espejo · se estampa la copia reflejada. */
    fun playMirrorStamp() = play(mirrorStampId, volume = 0.85f * BIOME_MIX)

    /** Las estrellas del objetivo cambian de color. */
    fun playStarRetint() = play(starRetintId, volume = 0.8f * BIOME_MIX)

    /**
     * LLUVIA DE METEORITOS (P-294) · LA MIRA BUSCA BLANCO EN BUCLE (2026-09-30, pedido del usuario).
     * La mira puede pasarse minutos recorriendo el tablero hasta que el jugador coloque, así que su
     * radar (snd_galactic_warn, muestra pensada para repetirse sin corte) suena en bucle mientras
     * [active]. Lo gobierna el ViewModel desde el estado (hay mira y se está jugando), no un evento.
     * Al pasar a 2º plano se corta y al volver se reanuda si sigue haciendo falta ([pauseBgm]/[resumeBgm]).
     */
    fun setGalacticAimLoop(active: Boolean) {
        galacticAimWanted = active
        if (active) startGalacticAim() else stopGalacticAim()
    }

    private fun startGalacticAim() {
        if (galacticAimStream != 0 || released || masterMuted || galacticWarnId !in readyIds) return
        val vol = limitedVolume(galacticWarnId, effectiveSfx * 0.7f * BIOME_MIX, choked = false)
        galacticAimStream = try {
            soundPool.play(galacticWarnId, vol, vol, PRIO_DECOR, -1, 1f)
        } catch (_: Exception) { 0 }
    }

    private fun stopGalacticAim() {
        val stream = galacticAimStream
        galacticAimStream = 0
        if (stream != 0) try { soundPool.stop(stream) } catch (_: Exception) {}
    }

    @Volatile private var galacticAimWanted = false
    @Volatile private var galacticAimStream = 0

    // ── P-381 · LA SALIDA DE AUDIO NO SE DUERME (2026-09-30) ──────────────────
    // Android pone la salida de audio en reposo tras unos segundos sin nada sonando, y al despertar
    // muchos móviles meten una rampa anti-chasquido: el PRIMER efecto tras un silencio sale
    // recortado y más flojo que los siguientes («un toque suena bajo, varios seguidos no»). La
    // música no lo evita: va por MediaPlayer, a menudo por otra salida que la de SoundPool, y el
    // jugador puede tenerla a cero.
    //
    // Remedio estándar: una voz de SoundPool reproduce en bucle un segundo de silencio digital
    // (`snd_keepalive`, ceros puros) mientras la app está en primer plano, así la salida de los SFX
    // sigue despierta. No se oye nada, no pasa por el limitador y tiene voz propia (maxStreams + 1).
    // Prioridad por encima de todo para que SoundPool no la sacrifique cuando se llena el pool.
    private val PRIO_KEEPALIVE = PRIO_KEY + 1
    @Volatile private var appInForeground = true
    @Volatile private var keepAliveStream = 0

    // Una muestra tan pequeña puede terminar de cargar antes de que el constructor llegue hasta
    // aquí; entonces el listener vio estos campos sin inicializar y no arrancó. Se reintenta ya.
    init { startKeepAlive() }

    /** La Activity avisa de que pasa a 1º/2º plano: en 2º plano la salida puede dormirse. */
    fun setAppForeground(foreground: Boolean) {
        appInForeground = foreground
        if (foreground) startKeepAlive() else stopKeepAlive()
    }

    @Synchronized
    private fun startKeepAlive() {
        if (keepAliveStream != 0 || released || !appInForeground) return
        if (keepAliveId == 0 || keepAliveId !in readyIds) return
        keepAliveStream = try {
            // Volumen 1: la muestra ya es silencio, y un track a volumen 0 podría no contar como activo.
            soundPool.play(keepAliveId, 1f, 1f, PRIO_KEEPALIVE, -1, 1f)
        } catch (_: Exception) { 0 }
    }

    @Synchronized
    private fun stopKeepAlive() {
        val stream = keepAliveStream
        keepAliveStream = 0
        if (stream != 0) try { soundPool.stop(stream) } catch (_: Exception) {}
    }

    /** LLUVIA DE METEORITOS: el IMPACTO (sample del antiguo colapso del agujero). */
    fun playMeteorImpact() = play(galacticCollapseId, volume = 0.95f * BIOME_MIX)

    /** LLUVIA DE METEORITOS: el meteorito se DESVÍA (sample de la antigua cancelación). */
    fun playMeteorDeflect() = play(galacticCancelId, volume = 0.8f * BIOME_MIX)

    /** P-319 · LA ROCA SE AGRIETA y contagia a un vecino (sample del cristal que cruje, más grave). */
    fun playRockCrack() = play(rockCrackId, volume = 0.9f * BIOME_MIX)

    /** Cueva de Cristal: la armadura se agrieta (aún le queda capa). */
    fun playCrystalCrack() = play(crystalCrackId, volume = 0.85f * BIOME_MIX)

    /** Cueva de Cristal: la armadura se ROMPE del todo (última capa). */
    fun playCrystalShatter() = play(crystalShatterId, volume = 0.9f * BIOME_MIX)

    /** Bosque del Miedo: los bloques huyen asustados. */
    fun playFearScare() = play(fearScareId, volume = 0.85f * BIOME_MIX)

    /** Jardín: la enredadera agarra y arrastra un Teika. */
    fun playVineGrab() = play(vineGrabId, volume = 0.85f * BIOME_MIX)

    /** Jardín: la enredadera se repliega herida (el Teika rompió líneas: counterplay). */
    fun playVineHurt() = play(vineHurtId, volume = 0.85f * BIOME_MIX)

    /**
     * Tormenta: bobina que se enciende. El rate SUBE con el orden [order] (1..n) para un arpegio
     * ascendente conforme completas la secuencia — refuerza el progreso, como el combo.
     */
    fun playCoilCharge(order: Int = 1) =
        play(coilChargeId, volume = 0.8f * BIOME_MIX, rate = (1f + (order - 1) * 0.06f).coerceIn(1f, 1.6f))

    /** Tormenta: descarga en cadena de todas las bobinas (zap grande). */
    fun playCoilDischarge() = play(coilDischargeId, volume = 0.95f * BIOME_MIX)

    /** Tormenta: cortocircuito — tocaste fuera de orden, la secuencia se reinicia (fallo). */
    fun playCoilShort() = play(coilShortId, volume = 0.85f * BIOME_MIX)

    /** Volcán: la lava avanza desde arriba y quema la fila. */
    fun playLavaRetreat() = play(lavaRetreatId, volume = 0.8f * BIOME_MIX)
    fun playSleepWake() = play(sleepWakeId, volume = 0.8f * BIOME_MIX)
    fun playGlacierThaw() = play(glacierThawId, volume = 0.8f * BIOME_MIX)
    fun playGluttonRetreat() = play(gluttonRetreatId, volume = 0.8f * BIOME_MIX)

    fun playLavaSurge() = play(lavaSurgeId, volume = 0.9f * BIOME_MIX)

    /**
     * **TIEMPO BALA: el rótulo aterriza** (2026-09-04, pedido del usuario: «una animación dramática
     * con efectos de sonido»).
     *
     * El instante de enseñanza —las lecciones y las cápsulas de mecánica— era **mudo**: el juego se
     * ralentizaba, la pantalla se apagaba y una frase caía en el centro sin un solo sonido, que es
     * justo lo contrario de lo que la regla de la casa pide de un momento importante. Suena cuando
     * la frase entra (después del compás previo, ver [SlowMotionShot.leadInMs]), no al empezar la
     * ralentización: lo que se subraya es la explicación, no el frenazo.
     *
     * **REHECHO el 2026-09-04, oído por el usuario en el playtest: «no suene tan feo».** El primer
     * intento tomaba prestada `snd_pure_line` pitchada a 0,8× **con duck de música**, y las dos
     * decisiones estaban mal por el mismo motivo: es la muestra de un PREMIO, y ralentizarla no le
     * quita esa lectura —solo la enturbia—, mientras que apartar la música convierte un rótulo de
     * texto en un acontecimiento. Un cartel que explica una regla no es un premio ni un susto.
     *
     * Ahora suena **el tic de la propia app** (`snd_ui_click`, la voz con la que el juego responde a
     * cada toque), un poco más grave y a media voz, y **sin duck**: dice «ha llegado un mensaje» y
     * se quita de en medio. La ceremonia del instante ya la ponen la cámara lenta y el velo; el
     * sonido solo tiene que marcar la entrada de la frase.
     *
     * `PRIO_DECOR` y no `PRIO_KEY` por lo mismo: si en ese momento coincide algo del tablero, manda
     * el tablero.
     */
    fun playTeachingBeat() =
        play(uiClickId, volume = 0.62f * UI_MIX, rate = 0.9f, priority = PRIO_DECOR)

    // ── P-85 · LOS DOS PREMIOS QUE SE COBRABAN MUDOS (2026-08-30) ─────────────
    //
    // La regla de la casa desde el 2026-07-31 es que ningún momento en que se gana o se pierde algo
    // puede quedar mudo. La celda caliente (P-78) y la línea pura (P-69) entraron incumpliéndola: la
    // primera pagaba 250 puntos sin un solo cue, y la segunda engendraba un T2 en vez de un T1
    // sonando EXACTAMENTE igual que una línea normal — así el jugador no podía aprender la regla.
    //
    // Los dos van en PRIO_NORMAL y no en PRIO_KEY: son adorno SOBRE la jugada, no la jugada. Si hay
    // que sacrificar una voz, que se sacrifiquen ellos y nunca el golpe del clear.

    /** Celda caliente (P-78) cobrada: repique de premio. UNA vez por pasada, no una por celda. */
    fun playHotCell() = play(hotCellId, volume = 0.85f * BIOME_MIX)

    /**
     * El premio de la bandeja (2026-09-26): la reposición llega pensada para colocarse tras una doble
     * o triple línea. Reutiliza el repique de premio, más agudo, para que suene a «te lo has ganado»
     * sin confundirse con la celda caliente.
     */
    fun playTrayReward() = play(trayRewardId, volume = 0.8f * UI_MIX)

    /**
     * Confirma el Eco de habilidad: activateCompanionAbility conserva la carga y publica
     * AbilityEcho. Único productor actual; no anuncia una recarga general del compañero.
     * Usa su recurso dedicado snd_ability_ready, rate1, ganancia0,68 y prioridad clave, sin duck.
     */
    fun playAbilityReady() =
        play(abilityReadyId, volume = 0.85f * UI_MIX, priority = PRIO_KEY)

    /**
     * Línea pura (P-69): brillo que anuncia que ESA línea era de un solo color y por eso engendra un
     * tier más. Suena por debajo del clear (0,7) a propósito: su papel es teñir el momento, no
     * protagonizarlo — el protagonista es el Teika grande que sale detrás.
     */
    fun playPureLine() = play(pureLineId, volume = 0.7f * BIOME_MIX)

    // (ERRADICADOS 2026-08-09, con el modo) `playTideWarn`, `playTideRise` y `playCeilingCrush`.
    // (RETIRADO 2026-08-19) `snd_ceiling_crush`: al desaparecer la muerte por presión quedó sin
    // llamantes y pasó a `art/_retirados/` junto con las otras muestras históricas.

    // ── LOS DIEZ MOMENTOS QUE ESTABAN MUDOS (2026-07-31) ──────────────────────────────────────
    // Orden: primero los de PARTIDA, luego las celebraciones de meta. Todos con `duckMusic` los que
    // son un final (la música se aparta para el momento) y `PRIO_KEY` los que no se pueden perder.

    /**
     * **VICTORIA DE NIVEL.** Era el silencio más caro del juego: completar un nivel —la recompensa
     * que el jugador persigue 293 veces— solo VIBRABA (`Haptics.victory`). El overlay entraba con
     * su animación, sus estrellas y sus monedas, y el altavoz no decía nada.
     */
    fun playLevelVictory() { duckMusic(); play(levelVictoryId, priority = PRIO_KEY) }

    /** **¡PERFECTO!**: el tablero queda LIMPIO. Destello cristalino, corto (no pisa al clear). */
    fun playPerfectClear() = play(perfectClearId, volume = 0.9f, priority = PRIO_KEY)

    // (ERRADICADOS 2026-08-09, con el modo) `playMonoBreak` y `playRewardRow`.

    /**
     * **TRONO REAL · ESPADA DEL REY**: el Rey ALZA la espada (primer aviso). Sample del antiguo aviso de
     * asedio: el aviso es lo único que da margen a reaccionar, así que no puede ser mudo.
     */
    fun playSwordRaise() = play(kingWarningId, volume = 0.85f * BIOME_MIX)

    /** **⚠ GRACIA**: la racha de combo se salva por los pelos. Tic breve, no celebración. */
    fun playComboGrace() = play(comboGraceId, volume = 0.8f)

    /** **Ficha que NO cabe**: rechazo sordo y amable. Tenía háptica (`Haptics.invalid`) y nada más. */
    fun playInvalid() = play(invalidId, volume = 0.55f * UI_MIX, priority = PRIO_DECOR)

    /** **Compañero DESBLOQUEADO**: el momento de colección del juego, hasta hoy en silencio. */
    fun playCharacterUnlock() { duckMusic(); play(characterUnlockId, priority = PRIO_KEY) }

    /** **SUBIDA DE RANGO** (V-03): fanfarria corta. El overlay entraba mudo. */
    fun playRankUp() { duckMusic(); play(rankUpId, priority = PRIO_KEY) }

    /** **MURAL COMPLETO**: cierre de capítulo, la cinemática más grande fuera de la partida. */
    fun playMuralComplete() { duckMusic(); play(muralCompleteId, priority = PRIO_KEY) }

    /** **RACHA DIARIA**: el banner de «Día N 🔥». Celebración corta y cálida, sin moneda. */
    fun playStreak() = play(streakId, volume = 0.9f)

    // (ERRADICADOS 2026-08-09, con el modo) `playMilestone` y `playRecordBroken`: los hitos de
    // PROFUNDIDAD del muro. No gastaban mp3 propio (reusaban `megaId` y `purchaseId`).

    /** Bosque del Sueño: la modorra desciende (aviso onírico). */
    fun playSleepDrowsy() = play(sleepDrowsyId, volume = 0.85f * BIOME_MIX)

    private val gluttonSelectionId = soundPool.load(context, R.raw.snd_biome_glutton_selection, 1)
    private val starSelectionId = soundPool.load(context, R.raw.snd_biome_star_selection, 1)

    /** Selección tranquila de nivel; conserva los cues de Glotón/Estrella en partida. */
    fun playBiomeSelection(biome: BiomeType) {
        val id = when (biome) {
            BiomeType.GLUTTON -> gluttonSelectionId
            BiomeType.STAR -> starSelectionId
            else -> biomeIds[biome]
        }
        id?.let { play(it, volume = 0.85f * BIOME_MIX) }
    }

    /** Cue de bioma dentro de partida. */
    fun playBiome(biome: BiomeType) {
        biomeIds[biome]?.let { play(it, volume = 0.85f * BIOME_MIX) }
    }

    // ── BGM por contexto ───────────────────────────────────────────────────────
    // Referencias ESTÁTICAS (R.raw.*) para que shrinkResources del release no elimine los mp3.
    // Con getIdentifier("bgm_gameplay") el shrinker no detecta uso → el AAB de Play queda mudo.
    // Mientras falten pistas específicas, caen a bgm_gameplay para evitar pantallas mudas.
    /**
     * Pistas de música por contexto.
     *
     * Los 14 biomas tienen tema propio (renovados el 2026-09-29, estilo épico/híbrido); los menús
     * (LOFI) y la entrada de compatibilidad INFINITE usan bgm_gameplay.
     *
     * Detalle que hace esto seguro: [playMusic] compara el ARCHIVO resuelto, no el enum — mientras
     * todos caigan en `bgm_gameplay`, cambiar de bioma o entrar en partida **no corta el loop**.
     */
    enum class MusicTrack(val resId: Int = 0, val fallbackResId: Int = 0) {
        NONE,
        LOFI(fallbackResId = R.raw.bgm_gameplay),                    // Navbar (inicio/inventario/tienda)
        GAMEPLAY(resId = R.raw.bgm_gameplay),                        // Partida sin bioma conocido
        INFINITE(fallbackResId = R.raw.bgm_gameplay),                // «La Prensa»
        // Primer bioma con tema PROPIO (2026-08-29g). Al entrar a un nivel de la Caverna de
        // Cristal, `playMusic` ve que el archivo resuelto cambia y sustituye el tema de menús.
        BIOME_CRYSTAL(resId = R.raw.bgm_crystal),
        BIOME_SLEEP(resId = R.raw.bgm_sleep),
        BIOME_FEAR(resId = R.raw.bgm_fear),
        BIOME_GARDEN(resId = R.raw.bgm_garden),
        BIOME_STORM(resId = R.raw.bgm_storm),
        BIOME_VOLCANO(resId = R.raw.bgm_volcano),
        BIOME_NINJA(resId = R.raw.bgm_ninja),
        BIOME_GLACIER(resId = R.raw.bgm_glacier),
        BIOME_GHOST(resId = R.raw.bgm_ghost),
        BIOME_GLUTTON(resId = R.raw.bgm_glutton),
        BIOME_TOXIC(resId = R.raw.bgm_toxic),
        BIOME_STAR(resId = R.raw.bgm_star),
        BIOME_KING(resId = R.raw.bgm_king),
        BIOME_GALACTIC(resId = R.raw.bgm_galactic);

        companion object {
            /** Pista de un bioma. Un `when` exhaustivo: añadir un bioma 15 no compila hasta cablearlo. */
            fun forBiome(biome: BiomeType): MusicTrack = when (biome) {
                BiomeType.CRYSTAL  -> BIOME_CRYSTAL
                BiomeType.SLEEP    -> BIOME_SLEEP
                BiomeType.FEAR     -> BIOME_FEAR
                BiomeType.GARDEN   -> BIOME_GARDEN
                BiomeType.STORM    -> BIOME_STORM
                BiomeType.VOLCANO  -> BIOME_VOLCANO
                BiomeType.NINJA    -> BIOME_NINJA
                BiomeType.GLACIER  -> BIOME_GLACIER
                BiomeType.GHOST    -> BIOME_GHOST
                BiomeType.GLUTTON  -> BIOME_GLUTTON
                BiomeType.TOXIC    -> BIOME_TOXIC
                BiomeType.STAR     -> BIOME_STAR
                BiomeType.KING     -> BIOME_KING
                BiomeType.GALACTIC -> BIOME_GALACTIC
                BiomeType.NONE     -> GAMEPLAY   // intro / nivel sin bioma
            }
        }
    }

    private var currentMusic: MusicTrack = MusicTrack.NONE
    private var bgmGeneration: Long = 0L
    // Recurso raw que el MediaPlayer está reproduciendo realmente (0 = nada).
    // Varias pistas lógicas pueden resolver al mismo archivo (vía fallback), así que el
    // reinicio se decide por este resId y NO por el enum: cambiar de pista que cae al mismo
    // archivo deja el loop intacto (no se corta al navegar navbar→modos→compañero→partida).
    private var currentMusicResId: Int = 0
    // Techo de la música: el slider 0..1 mapea a 0..MUSIC_CEILING de salida real (evita que sature).
    // Bajado a 0.11 porque la BGM se oía demasiado fuerte: el antiguo 20% del slider (0.2×0.55≈0.11)
    // pasa a ser el nuevo 100%. Así el slider al máximo suena como antes sonaba al 20%.
    //
    // **Subido a 0.28 (2026-08-30), y NO es un cambio de gusto: repone 8 dB prestados.** El 0.11 se
    // afinó de oído mientras la BGM iba con `usage=GAME` por el camino de audio de juego de
    // Samsung, que le regalaba ~8-10 dB de realce —el mismo realce que se encendía y se apagaba
    // solo y sonaba como «la música se amplifica sola»—. Al pasar el player a `USAGE_MEDIA` ese
    // regalo desaparece, y con él la BGM se quedaba 8 dB por debajo de lo que el usuario venía
    // oyendo. 0.11 × 10^(8/20) ≈ 0.28 deja la sonoridad EXACTAMENTE donde estaba, pero puesta por
    // nosotros y estable, en vez de prestada por el móvil y parpadeante.
    //
    // Se conserva el techo del playtest previo; la mezcla con efectos requiere validación en
    // dispositivo. Quince composiciones completas aprobadas, convertidas sin recortes ni fundidos:
    // mediciones vigentes en docs/audio/integracion-musical-2026-10-11.json.
    private val MUSIC_CEILING = 0.28f

    /**
     * Cambia la BGM según el contexto. Si [track] YA está sonando, NO reinicia (navegar el navbar
     * mantiene la misma pista). Si falta una pista específica, usa su fallback configurado.
     */
    fun playMusic(track: MusicTrack) {
        val newResId = if (track == MusicTrack.NONE) 0 else resolveMusicRes(track)
        // Un mismo enum no implica que exista un player sano: MediaPlayer puede haber fallado o
        // haberse quedado inválido después de un error del sistema. En ese caso hay que reintentar.
        if (track == currentMusic && newResId != 0 && newResId == currentMusicResId && bgmPlayer != null) return
        currentMusic = track
        // Si el archivo resuelto es el mismo que ya suena (p. ej. dos pistas que caen al mismo
        // fallback) y hay un player vivo, NO recreamos nada: el loop continúa sin cortes.
        //
        // (2026-08-29f) Cuando un bioma estrene su propio tema, basta con rellenar su `resId`:
        // el archivo resuelto cambia, este return no aplica y la pista de menús se sustituye sola
        // por la del bioma. No hay que tocar nada más.
        if (newResId != 0 && newResId == currentMusicResId && bgmPlayer != null) return

        // P-52 · SI YA SUENA ALGO, SE FUNDE. Sin player vivo (arranque) o con la música inaudible
        // (mute maestro o slider a cero) no hay nada que fundir: se hace el cambio directo de
        // siempre, que además es el camino que menos puede romperse.
        val hayQueFundir = bgmPlayer != null && bgmPrepared && !masterMuted && musicVolume > 0f
        if (!hayQueFundir) {
            cambiarPistaYa(track)
            return
        }
        val generation = bgmGeneration
        musicFadeJob?.cancel()
        var myFade = ++fadeGeneration
        musicFadeJob = sfxScope.launch(musicThread) {
            try {
                // Bajar la que está sonando…
                for (i in 1..FADE_STEPS) {
                    if (generation != bgmGeneration) return@launch
                    fadeFactor = 1f - (i / FADE_STEPS.toFloat())
                    applyBgmVolume()
                    kotlinx.coroutines.delay(FADE_MS / FADE_STEPS)
                }
                if (generation != bgmGeneration) return@launch
                // …cambiar, y subir la nueva desde el silencio.
                //
                // ⚠️ EL ORDEN DE ESTAS TRES LÍNEAS ES LO QUE HACE QUE HAYA SUBIDA. `cambiarPistaYa`
                // llama a `releaseBgmPlayer`, que **cancela el fundido en vuelo** —o sea, ESTE— y
                // devuelve `fadeFactor` a 1. Así que primero se suelta el trabajo (para que no se
                // cancele a sí mismo) y solo DESPUÉS se pone el factor a 0, ya con el player nuevo
                // creado. Al revés, la pista nueva entraba a todo volumen y el fundido era medio.
                musicFadeJob = null
                cambiarPistaYa(track)
                // `cambiarPistaYa` invalidó los fundidos (generación nueva): la subida vuelve a ser EL
                // fundido vigente, y se registra para que otro cambio de pista pueda cancelarla.
                myFade = ++fadeGeneration
                musicFadeJob = coroutineContext[kotlinx.coroutines.Job]
                fadeFactor = 0f
                applyBgmVolume()
                val nuevaGeneracion = bgmGeneration
                for (i in 1..FADE_STEPS) {
                    if (nuevaGeneracion != bgmGeneration) return@launch
                    fadeFactor = i / FADE_STEPS.toFloat()
                    applyBgmVolume()
                    kotlinx.coroutines.delay(FADE_MS / FADE_STEPS)
                }
            } finally {
                // Pase lo que pase —cancelación incluida— el factor vuelve a 1. Es la diferencia
                // entre un fundido interrumpido y la música muda para el resto de la sesión. Solo el
                // fundido VIGENTE: el `finally` de uno cancelado llega tarde y pisaría al nuevo.
                if (myFade == fadeGeneration) {
                    fadeFactor = 1f
                    applyBgmVolume()
                }
            }
        }
    }

    /** El cambio de pista de toda la vida, sin fundido. Lo usan el arranque y el camino mudo. */
    private fun cambiarPistaYa(track: MusicTrack) {
        releaseBgmPlayer()
        if (track == MusicTrack.NONE) {
            abandonAudioFocus()
            return
        }
        requestAudioFocus()
        startCurrentMusic()
    }

    private fun startCurrentMusic() {
        val t = currentMusic
        if (t == MusicTrack.NONE) return
        val resId = resolveMusicRes(t)
        if (resId == 0) return  // sin pista ni fallback → silencio (sin crash)
        val generation = bgmGeneration
        val player = MediaPlayer()
        try {
            configureBgmPlayer(player, resId, generation)
            player.setOnPreparedListener { prepared ->
                if (released || generation != bgmGeneration || currentMusic != t) {
                    try { prepared.release() } catch (_: Exception) {}
                    return@setOnPreparedListener
                }
                bgmPlayer = prepared
                currentMusicResId = resId
                bgmPrepared = true
                applyBgmVolume(prepared)
                prepareNextBgm(prepared, resId, generation)
                if (!bgmPlaybackAllowed) return@setOnPreparedListener
                try { prepared.start() } catch (_: Exception) {
                    if (bgmPlayer === prepared) {
                        bgmPlayer = null
                        currentMusicResId = 0
                        bgmPrepared = false
                    }
                    try { prepared.release() } catch (_: Exception) {}
                }
            }
            bgmPlayer = player
            // Marcamos la pista mientras prepara para que playMusic no cree dos players si la UI
            // vuelve a emitir el mismo contexto durante la preparación asíncrona.
            currentMusicResId = resId
            player.prepareAsync()
        } catch (_: Exception) {
            if (bgmPlayer === player) bgmPlayer = null
            currentMusicResId = 0
            bgmPrepared = false
            try { player.release() } catch (_: Exception) {}
        }
    }

    /** Configuración común de cada mitad de la cadena de BGM. */
    private fun configureBgmPlayer(player: MediaPlayer, resId: Int, generation: Long) {
        player.setOnErrorListener { failed, _, _ ->
            val wasCurrent = bgmPlayer === failed
            val wasNext = bgmNextPlayer === failed
            if (wasCurrent) {
                bgmPlayer = null
                bgmPrepared = false
                currentMusicResId = 0
                bgmNextPlayer?.let { next -> try { next.release() } catch (_: Exception) {} }
                bgmNextPlayer = null
                duckJob?.cancel(); duckJob = null; duckGeneration++; duckFactor = 1f
            } else if (wasNext) {
                bgmNextPlayer = null
            }
            try { failed.release() } catch (_: Exception) {}
            true
        }
        player.setOnCompletionListener { completed ->
            handleBgmCompletion(completed, resId, generation)
        }
        // USAGE_MEDIA evita el realce variable que algunos fabricantes aplican a USAGE_GAME.
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
        )
        // `isLooping` vuelve a entrar por el mismo decoder y producía un hueco audible en ciertos
        // dispositivos. La repetición la gobierna `setNextMediaPlayer` con otro player preparado.
        player.isLooping = false
        player.setDataSource(context, Uri.parse("android.resource://${context.packageName}/$resId"))
    }

    /** Prepara la siguiente copia y la entrega al framework antes de que termine la actual. */
    private fun prepareNextBgm(current: MediaPlayer, resId: Int, generation: Long) {
        if (released || generation != bgmGeneration || bgmPlayer !== current) return
        bgmNextPlayer?.let { stale -> try { stale.release() } catch (_: Exception) {} }
        bgmNextPlayer = null
        val next = MediaPlayer()
        try {
            configureBgmPlayer(next, resId, generation)
            next.setOnPreparedListener { prepared ->
                if (released || generation != bgmGeneration || bgmPlayer !== current) {
                    try { prepared.release() } catch (_: Exception) {}
                    return@setOnPreparedListener
                }
                bgmNextPlayer = prepared
                applyBgmVolume(prepared)
                try {
                    current.setNextMediaPlayer(prepared)
                } catch (_: Exception) {
                    bgmNextPlayer = null
                    try { prepared.release() } catch (_: Exception) {}
                }
            }
            bgmNextPlayer = next
            next.prepareAsync()
        } catch (_: Exception) {
            if (bgmNextPlayer === next) bgmNextPlayer = null
            try { next.release() } catch (_: Exception) {}
        }
    }

    /** Promueve el player que el framework ya arrancó y prepara la copia del ciclo siguiente. */
    private fun handleBgmCompletion(completed: MediaPlayer, resId: Int, generation: Long) {
        if (released || generation != bgmGeneration || bgmPlayer !== completed) {
            try { completed.release() } catch (_: Exception) {}
            return
        }
        val successor = bgmNextPlayer
        if (successor == null) {
            // Recuperación excepcional si la copia no llegó a prepararse: conserva la música viva,
            // aunque este camino sí puede tener el pequeño hueco que la cadena evita normalmente.
            try {
                completed.seekTo(0)
                if (bgmPlaybackAllowed) completed.start()
                prepareNextBgm(completed, resId, generation)
            } catch (_: Exception) {
                releaseBgmPlayer()
                startCurrentMusic()
            }
            return
        }
        bgmPlayer = successor
        bgmNextPlayer = null
        bgmPrepared = true
        applyBgmVolume(successor)
        if (!bgmPlaybackAllowed) try { successor.pause() } catch (_: Exception) {}
        try { completed.setNextMediaPlayer(null) } catch (_: Exception) {}
        try { completed.release() } catch (_: Exception) {}
        prepareNextBgm(successor, resId, generation)
    }

    /** Reaplica el volumen efectivo de la música al player vivo (al mover el slider o el master mute). */
    private fun applyBgmVolume(player: MediaPlayer? = bgmPlayer) {
        val volume = effectiveMusic
        try { player?.setVolume(volume, volume) } catch (_: Exception) {}
        if (bgmNextPlayer !== player) {
            try { bgmNextPlayer?.setVolume(volume, volume) } catch (_: Exception) {}
        }
    }

    private fun resolveMusicRes(track: MusicTrack): Int {
        if (track.resId != 0) return track.resId
        return track.fallbackResId
    }

    private fun releaseBgmPlayer() {
        bgmGeneration++
        duckJob?.cancel()
        duckJob = null
        // Invalida cualquier duck en vuelo ANTES de restaurar el factor: si no, el que estuviera
        // corriendo en otro hilo podía volver a bajarlo justo después y dejar la música al 55 %.
        duckGeneration++
        duckFactor = 1f
        // Mismo motivo que el duck: un fundido en vuelo no puede dejar el factor a medias sobre el
        // player siguiente. Ver [fadeFactor].
        musicFadeJob?.cancel()
        musicFadeJob = null
        fadeGeneration++
        fadeFactor = 1f
        bgmPlayer?.let { p ->
            try { p.setNextMediaPlayer(null) } catch (_: Exception) {}
            try { p.stop() } catch (_: Exception) {}
            try { p.release() } catch (_: Exception) {}
        }
        bgmNextPlayer?.let { p ->
            try { p.release() } catch (_: Exception) {}
        }
        bgmPlayer = null
        bgmNextPlayer = null
        bgmPrepared = false
        currentMusicResId = 0
    }

    /** Pausa la música (Activity a 2º plano / juego en pausa). */
    fun pauseBgm() {
        stopGalacticAim()
        bgmPlaybackAllowed = false
        if (!bgmPrepared) return
        try { bgmPlayer?.pause() } catch (_: Exception) {}
    }

    /** Reanuda la música al volver a 1º plano (recrea el player si se había liberado). */
    fun resumeBgm() {
        if (galacticAimWanted) startGalacticAim()
        bgmPlaybackAllowed = true
        requestAudioFocus()
        pausedByFocus = false
        val player = bgmPlayer
        if (player == null) {
            startCurrentMusic()
            return
        }
        if (!bgmPrepared) return
        try {
            player.start()
        } catch (_: Exception) {
            // Un player que falló al reanudar no debe bloquear la misma pista para siempre.
            releaseBgmPlayer()
            startCurrentMusic()
        }
    }

    // ── Volúmenes / master mute ─────────────────────────────────────────────────

    /** Fija el volumen de la MÚSICA (0f..1f) y lo aplica al player vivo en tiempo real. */
    fun setMusicVolume(v: Float) {
        musicVolume = v.coerceIn(0f, 1f)
        applyBgmVolume()
    }

    /** Fija el volumen de los EFECTOS (0f..1f). Surte efecto en el siguiente disparo. */
    fun setSfxVolume(v: Float) {
        sfxVolume = v.coerceIn(0f, 1f)
        // El radar del meteorito suena EN BUCLE: sin esto se quedaba con el volumen de cuando empezó.
        val aim = galacticAimStream
        if (aim != 0) {
            val vol = effectiveSfx * 0.7f * BIOME_MIX
            try { soundPool.setVolume(aim, vol, vol) } catch (_: Exception) {}
        }
    }

    /** Master mute (botón rápido 🔊/🔇): silencia/restaura todo sin perder los niveles elegidos. */
    fun setMuted(muted: Boolean) {
        if (masterMuted == muted) return
        masterMuted = muted
        try {
            if (muted) soundPool.autoPause() else {
                soundPool.autoResume()
                readyIds.forEach { flushPendingSample(it) }
                if (galacticAimWanted) startGalacticAim()   // se pidió estando en silencio
            }
        } catch (_: Exception) {
            // El ViewModel puede recibir un último cambio de preferencias durante onCleared().
        }
        applyBgmVolume()
    }

    fun toggleMute(): Boolean {
        setMuted(!masterMuted)
        return masterMuted
    }

    // ── Ciclo de vida ─────────────────────────────────────────────────────────

    /** Libera SoundPool + ambos MediaPlayer de BGM, y corta las capas del clear pendientes. */
    fun release() {
        stopGalacticAim()
        stopKeepAlive()
        released = true
        bgmPlaybackAllowed = false
        bgmGeneration++
        pendingPlays.clear()
        sfxScope.cancel()   // capas escalonadas en vuelo: no deben disparar sobre un pool liberado
        soundPool.release()
        releaseBgmPlayer()
        abandonAudioFocus()
    }
}
