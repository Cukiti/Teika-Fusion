package com.cukiti.teikafusion.model

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException

// corruptionHandler: si el fichero de DataStore se corrompe (deserialización imposible), lo
// reemplaza por preferencias vacías en vez de lanzar CorruptionException en CADA acceso — sin esto,
// una corrupción deja la app en bucle de crash permanente. El progreso real se re-sincroniza de la
// nube. Es el patrón recomendado por Google para persistencia local a prueba de producción.
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "fruit_fusion_prefs",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Límite duro del nombre de jugador: **10 letras**. No es un capricho de datos, es de LAYOUT — el
 * nombre se pinta en el carné del perfil, en el ranking y en la cabecera de partida, todos sitios de
 * ancho cerrado, y a 20 caracteres se cortaba con puntos suspensivos o empujaba a sus vecinos.
 * Toda ruta que escriba un nombre (onboarding, ajustes, nombre de Google, merge de nube)
 * recorta a esta constante; [PreferencesManager.saveDisplayName] es el último cerrojo.
 */
const val MAX_DISPLAY_NAME_LENGTH = 10

/**
 * Lectura tolerante a fallos de E/S: si DataStore lanza [IOException] al leer del disco, emite
 * preferencias vacías en lugar de propagar la excepción a cada colector — de lo contrario un único
 * fallo de disco tumba TODA la app al arrancar (cada `xxxFlow` reventaría). Otros errores sí se
 * propagan porque son bugs reales, no condiciones de disco transitorias.
 */
private val Context.prefs: Flow<Preferences>
    get() = dataStore.data.catch { e ->
        if (e is IOException) emit(emptyPreferences()) else throw e
    }

class PreferencesManager(private val context: Context) {

    companion object {
        val HIGH_SCORE_KEY = intPreferencesKey("high_score")
        val MAX_TEIKAS_KEY = intPreferencesKey("max_teikas")
        val COINS_KEY = intPreferencesKey("coins") // DataStore key stays "coins" for backward compat
        val LIVES_KEY = intPreferencesKey("lives")
        val LIVES_NEXT_REGEN_KEY = longPreferencesKey("lives_next_regen_at")
        // Vidas INFINITAS por tiempo limitado (epoch ms de expiración). now < valor ⇒ activas: no se
        // gasta vida al jugar. Se ganan por racha (día 3/5/7) o se compran (packs caros de teikoins).
        val INFINITE_LIVES_UNTIL_KEY = longPreferencesKey("infinite_lives_until")
        val MAX_LEVEL_KEY = intPreferencesKey("max_level")
        /**
         * **P-227 · EL ÚLTIMO PUESTO CONOCIDO DEL RANKING.** 0 = todavía ninguno.
         *
         * Se escribe cuando el jugador abre el ranking y se lee en el carné del perfil, que es donde
         * ahora hay sitio para enseñarlo. Se guarda en vez de consultarse porque una consulta a
         * Firestore por cada visita a Inicio es exactamente lo que este proyecto ya tuvo que quitar
         * una vez (la pestaña XP del ranking: 50 documentos por apertura que nadie leía).
         */
        val LAST_RANK_KEY = intPreferencesKey("last_known_rank")
        /** P-437: cuándo se vio ese puesto (epoch ms). 0 = puesto de antes de que se guardara. */
        val LAST_RANK_AT_KEY = longPreferencesKey("last_known_rank_at")
        /** P-449: el jugador ya ha girado alguna ficha en la bandeja (se apaga el ↻ de los cuadros). */
        val ROTATE_LEARNED_KEY = booleanPreferencesKey("rotate_learned")
        // Cursor de Home: último nivel que el jugador tiene "seleccionado" (arte + nivel en Home).
        // 0 = sin elegir aún → la UI cae a la frontera (maxUnlockedLevel).
        val SELECTED_LEVEL_KEY = intPreferencesKey("selected_level")
        // Avisos de reenganche encendidos/apagados DESDE EL JUEGO (P-166). Es la segunda puerta: la
        // primera es el permiso del sistema, que el jugador concede en el overlay «¡Sin vidas!».
        val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        val SAVED_GAME_STATE_KEY = stringPreferencesKey("saved_game_state")
        // Cuándo se escribió la ranura de arriba (epoch ms). Es el árbitro cuando el mismo jugador
        // tiene un intento sin terminar en dos dispositivos: gana el más reciente (P-113).
        val SAVED_GAME_AT_KEY = longPreferencesKey("saved_game_state_at")
        // …y de qué NIVEL es (P-178). El arranque solo necesita saber si hay partida y de cuál, y
        // hasta ahora eso costaba deserializar el tablero entero para leerle un campo.
        val SAVED_GAME_LEVEL_KEY = intPreferencesKey("saved_game_state_level")
        val MUTE_KEY = booleanPreferencesKey("is_muted") // master mute (botón rápido 🔊/🔇)
        // Legado booleano on/off — solo se lee para migrar a volumen (slider) la 1ª vez.
        val MUSIC_ENABLED_KEY = booleanPreferencesKey("music_enabled")
        val SFX_ENABLED_KEY = booleanPreferencesKey("sfx_enabled")
        val MUSIC_VOLUME_KEY = floatPreferencesKey("music_volume")
        val SFX_VOLUME_KEY = floatPreferencesKey("sfx_volume")
        val VIBRATION_ENABLED_KEY = booleanPreferencesKey("vibration_enabled")
        // Inclinación (tilt): los Teikas ruedan al inclinar el teléfono. Default = activado.
        val TILT_ENABLED_KEY = booleanPreferencesKey("tilt_enabled")
        /** «Reducir efectos»: menos partículas, destellos y sacudidas (móviles flojos + sensibilidad
         *  al movimiento). Nullable a propósito: si nunca se tocó, manda el ajuste del SISTEMA. */
        val REDUCED_FX_KEY = booleanPreferencesKey("reduced_fx")
        // Cupo diario de anuncios de monedas (P-18): día, vistos ese día e instante del último.
        val AD_QUOTA_DAY_KEY = intPreferencesKey("ad_quota_day")
        val AD_QUOTA_COUNT_KEY = intPreferencesKey("ad_quota_count")
        val AD_QUOTA_LAST_MS_KEY = longPreferencesKey("ad_quota_last_ms")
        val TUTORIAL_DONE_KEY = booleanPreferencesKey("tutorial_done")
        /** Version of the legal documents accepted by this installation. Kept across logout. */
        val LEGAL_CONSENT_VERSION_KEY = stringPreferencesKey("legal_consent_version")
        // 2026-09-06 (P-215): la tienda REABRIÓ el 2026-09-06 y con ella cinco productos
        // comprables, así que la política y los términos vuelven a describir Google Play Billing
        // —qué se vende, qué marcas de compra viajan a la nube y que del pago no vemos nada—.
        // Esta subida SÍ amplía tratamientos, no solo retira: la pantalla legal tiene que volver a
        // salirle a TODOS los jugadores (la comparación es por igualdad estricta), y el fichero de
        // `public/` no vale de nada sin `firebase deploy --only hosting`.
        //
        // (2026-09-05: la anterior solo RETIRABA lo que ya no ocurría — el modo Infinito, borrado
        // el 2026-08-09, y unas compras que entonces estaban comentadas.)
        const val CURRENT_LEGAL_CONSENT_VERSION = "2026-09-06-v1"
        // true una vez que el jugador eligió cuenta (Google o Anónimo) en el primer arranque.
        val ACCOUNT_CHOICE_MADE_KEY = booleanPreferencesKey("account_choice_made")
        // Marca puente entre el connect de Google y el arranque tras reiniciar: true si el login entró
        // a una cuenta existente con progreso (colisión) → hay que ofrecer el diálogo de conflicto.
        val PENDING_CLOUD_CONFLICT_PROMPT_KEY = booleanPreferencesKey("pending_cloud_conflict_prompt")
        // (BORRADA 2026-09-24, P-282) `belt_swipe_hint_seen`: el amago del cinturón se fue con él.
        /**
         * **EL PAQUETE INICIAL YA SE OFRECIÓ** (P-210, 2026-09-07). Se enseña **una sola vez**, al
         * terminar el primer capítulo, y a partir de ahí vive solo en la tienda.
         *
         * Es LOCAL a propósito, no viaja en la nube: marca «a este jugador ya se le enseñó la
         * ventana», no «este jugador tiene el pack» —eso lo dice `starterPackGranted`, que sí viaja—.
         * Si viajara, reinstalar dejaría sin la única oferta a alguien que quizá nunca la llegó a
         * ver por haber cambiado de móvil antes de terminar el capítulo.
         */
        val STARTER_PACK_OFFERED_KEY = booleanPreferencesKey("starter_pack_offered")
        /**
         * **LAS CÁPSULAS** (2026-09-01): ids de [MechanicCapsule] que el jugador ya ha visto. Cada
         * mecánica se enseña UNA vez en toda la vida de la partida, así que el registro tiene que
         * sobrevivir a cerrar la app — si no, la primera celda caliente de cada sesión volvería a
         * parar el juego dos segundos.
         *
         * Es un conjunto de ids y no catorce booleanos a propósito: añadir una mecánica no puede
         * obligar a tocar este archivo ni a migrar nada.
         */
        val MECHANICS_SEEN_KEY = androidx.datastore.preferences.core.stringSetPreferencesKey("mechanics_seen")
        /**
         * El jugador ya vio el DESTELLO de la tarjeta «EL ABISMO» en ELIGE TU MUNDO. La tarjeta
         * solo existe tras superar el último nivel, y la primera vez que la ve hace un barrido de
         * luz; después aparece en reposo. Local a la instalación (es un aviso, no progreso).
         */
        val ABYSS_CARD_REVEAL_SEEN_KEY = booleanPreferencesKey("abyss_card_reveal_seen")
        /** P-369: «Reducir efectos» ya se activó solo por tirones en este aparato (no se repite). */
        val AUTO_REDUCED_FX_DONE_KEY = booleanPreferencesKey("auto_reduced_fx_done")
        /** Migración única: las cargas de bienvenida ya se repartieron a esta cuenta. */
        val COMPANION_CHARGES_SEEDED_KEY = booleanPreferencesKey("companion_charges_seeded")
        /** Compró «quitar anuncios» (producto NO consumible, permanente). */
        val ADS_REMOVED_KEY = booleanPreferencesKey("ads_removed")
        /**
         * **EL PAQUETE INICIAL YA SE ENTREGÓ** (2026-09-06).
         *
         * No es un «lo compró»: eso lo sabe Play y lo reenvía **en cada arranque**, que es como se
         * restauran las compras no consumibles al reinstalar. Esto es «su contenido ya está en la
         * cuenta». Sin esta marca, abrir el juego diez veces regalaría diez veces cinco mil
         * teikoins — el mismo reenvío que en «quitar anuncios» es inofensivo (encender un flag ya
         * encendido no hace nada) aquí sería una fuga.
         */
        val STARTER_PACK_GRANTED_KEY = booleanPreferencesKey("starter_pack_granted")
        /** El banco de bloques comprado con teikoins (2026-09-28). Viaja: es una compra. */
        val BLOCK_BANK_PURCHASED_KEY = booleanPreferencesKey("block_bank_purchased")
        /**
         * P-249 · la devolución del bloque Caramelo retirado ya se pagó. Viaja con la cuenta por el
         * mismo motivo que la de arriba: local, reinstalar volvería a abonar los 2.000 teikoins.
         */
        val CANDY_REFUNDED_KEY = booleanPreferencesKey("candy_refunded")
        /**
         * Packs de monedas cuya **primera compra** ya cobró el bono de bienvenida del 100 %
         * ([StorePacks.welcomeBonusFor]). Viaja con la cuenta: si fuera local, reinstalar volvería a
         * regalar el doble en todos los packs.
         */
        val COIN_BONUS_CLAIMED_KEY = stringPreferencesKey("coin_bonus_claimed")
        // Aviso "Guarda tu progreso" para anónimos: cuántas veces se mostró y en qué nivel la última vez
        // (para capar la frecuencia y no molestar).
        val LINK_NUDGE_COUNT_KEY = intPreferencesKey("link_nudge_count")
        val LINK_NUDGE_LAST_LEVEL_KEY = intPreferencesKey("link_nudge_last_level")
        val STREAK_KEY = intPreferencesKey("daily_streak")
        val LAST_PLAYED_DAY_KEY = longPreferencesKey("last_played_day")
        val DISPLAY_NAME_KEY = stringPreferencesKey("display_name")
        val UNLOCKED_COSMETICS_KEY = stringPreferencesKey("unlocked_cosmetics")
        val ACTIVE_COSMETIC_KEY = stringPreferencesKey("active_cosmetic")
        val AVATAR_KEY = stringPreferencesKey("avatar_type")
        val TEIKA_ABILITY_LOADOUT_KEY = stringPreferencesKey("teika_ability_loadout")
        val COMPANION_UPGRADES_KEY = stringPreferencesKey("companion_upgrades")
        val COMPANION_CHARGES_KEY = stringPreferencesKey("companion_charges")
        /** Los ≤3 compañeros que el jugador se lleva a la partida (Sala de Preparación). */
        val COMPANION_BELT_KEY = stringPreferencesKey("companion_belt")
        /** Ya se le repartió a ESTA instalación un cinturón de arranque. Ver [companionBeltSeededFlow]. */
        val COMPANION_BELT_SEEDED_KEY = booleanPreferencesKey("companion_belt_seeded")
        val CRYSTAL_INVENTORY_KEY = stringPreferencesKey("crystal_inventory")
        val TOTAL_XP_KEY = intPreferencesKey("total_xp")
        val CLAIMED_CHARACTERS_KEY = stringPreferencesKey("claimed_characters")
        val SELECTED_COMPANION_KEY = stringPreferencesKey("selected_companion")
        val LEVEL_STARS_KEY = stringPreferencesKey("level_stars")
        // Racha de VICTORIAS consecutivas de niveles (2026-07-20, referencia Adventure del video):
        // sube con cada nivel superado, se corta al perder. La mejor racha queda como logro.
        val WIN_STREAK_KEY = intPreferencesKey("win_streak")
        val BEST_WIN_STREAK_KEY = intPreferencesKey("best_win_streak")
        // Mejor racha de combo jamás lograda (para la sección de LOGROS del perfil).
        val BEST_COMBO_KEY = intPreferencesKey("best_combo")
        val MURAL_REWARDS_KEY = stringPreferencesKey("mural_rewards")
        val CRYSTAL_AWARDED_KEY = stringPreferencesKey("crystal_awarded_levels")
    }

    /** Estrellas (1-3) del mejor resultado por nivel: nivel → estrellas. */
    val levelStarsFlow: Flow<Map<Int, Int>> = context.prefs.map { prefs ->
        val json = prefs[LEVEL_STARS_KEY] ?: "{}"
        try { Json.decodeFromString<Map<Int, Int>>(json) } catch (_: Exception) { emptyMap() }
    }

    /** Sobrescribe el mapa COMPLETO de estrellas por nivel. Usado al restaurar desde la nube. */
    suspend fun saveLevelStarsAll(stars: Map<Int, Int>) {
        context.dataStore.edit { prefs -> prefs[LEVEL_STARS_KEY] = Json.encodeToString(stars) }
    }

    /** Guarda [stars] del [level] solo si superan lo ya guardado (best). Devuelve el mejor vigente. */
    suspend fun saveLevelStars(level: Int, stars: Int): Int {
        var best = stars
        context.dataStore.edit { prefs ->
            val json = prefs[LEVEL_STARS_KEY] ?: "{}"
            val current: Map<Int, Int> = try { Json.decodeFromString(json) } catch (_: Exception) { emptyMap() }
            val prev = current[level] ?: 0
            best = maxOf(prev, stars)
            if (best != prev) prefs[LEVEL_STARS_KEY] = Json.encodeToString(current + (level to best))
        }
        return best
    }

    /** Índices de capítulos cuyo mural YA pagó su recompensa de finalización (evita doble cobro). */
    val muralRewardsFlow: Flow<Set<Int>> = context.prefs.map { prefs ->
        val json = prefs[MURAL_REWARDS_KEY] ?: "[]"
        try { Json.decodeFromString<Set<Int>>(json) } catch (_: Exception) { emptySet() }
    }

    /** Sobrescribe el set COMPLETO de murales recompensados. Usado al restaurar desde la nube. */
    suspend fun saveMuralRewardsAll(chapters: Set<Int>) {
        context.dataStore.edit { prefs -> prefs[MURAL_REWARDS_KEY] = Json.encodeToString(chapters) }
    }

    /** Marca el mural del capítulo [chapter] como recompensado. Devuelve true si es NUEVO (pagar una vez). */
    suspend fun markMuralRewarded(chapter: Int): Boolean {
        var isNew = false
        context.dataStore.edit { prefs ->
            val json = prefs[MURAL_REWARDS_KEY] ?: "[]"
            val current: Set<Int> = try { Json.decodeFromString(json) } catch (_: Exception) { emptySet() }
            isNew = chapter !in current
            if (isNew) prefs[MURAL_REWARDS_KEY] = Json.encodeToString(current + chapter)
        }
        return isNew
    }

    /** Niveles cuyo cristal mágico de 1ª vez ya se otorgó. Se respalda en la nube para evitar
     *  re-otorgar cristales al rejugar tras una restauración. */
    val crystalAwardedFlow: Flow<Set<Int>> = context.prefs.map { prefs ->
        val json = prefs[CRYSTAL_AWARDED_KEY] ?: "[]"
        try { Json.decodeFromString<Set<Int>>(json) } catch (_: Exception) { emptySet() }
    }

    /** Sobrescribe el set COMPLETO de niveles con cristal otorgado. Usado al restaurar desde la nube. */
    suspend fun saveCrystalAwardedAll(levels: Set<Int>) {
        context.dataStore.edit { prefs -> prefs[CRYSTAL_AWARDED_KEY] = Json.encodeToString(levels) }
    }

    /** Marca el [level] como ya recompensado con cristales mágicos. Devuelve true SOLO la 1ª vez
     *  (al rejugar el nivel devuelve false → no se vuelven a otorgar cristales). */
    suspend fun markCrystalAwarded(level: Int): Boolean {
        var isNew = false
        context.dataStore.edit { prefs ->
            val json = prefs[CRYSTAL_AWARDED_KEY] ?: "[]"
            val current: Set<Int> = try { Json.decodeFromString(json) } catch (_: Exception) { emptySet() }
            isNew = level !in current
            if (isNew) prefs[CRYSTAL_AWARDED_KEY] = Json.encodeToString(current + level)
        }
        return isNew
    }

    val highScoreFlow: Flow<Int> = context.prefs.map { preferences ->
        preferences[HIGH_SCORE_KEY] ?: 0
    }

    val maxTeikasFlow: Flow<Int> = context.prefs.map { preferences ->
        preferences[MAX_TEIKAS_KEY] ?: 0
    }

    /** P-227: último puesto del ranking que se le vio a este jugador. 0 = ninguno todavía; -2 = «100+». */
    val lastKnownRankFlow: Flow<Int> = context.prefs.map { preferences ->
        preferences[LAST_RANK_KEY] ?: 0
    }

    /** P-437: cuándo se vio [lastKnownRankFlow]. */
    val lastKnownRankAtFlow: Flow<Long> = context.prefs.map { preferences ->
        preferences[LAST_RANK_AT_KEY] ?: 0L
    }

    val teikoinsFlow: Flow<Int> = context.prefs.map { preferences ->
        preferences[COINS_KEY] ?: 100
    }

    val livesFlow: Flow<Int> = context.prefs.map { preferences ->
        preferences[LIVES_KEY] ?: LivesRules.MAX
    }

    val livesNextRegenAtFlow: Flow<Long> = context.prefs.map { preferences ->
        preferences[LIVES_NEXT_REGEN_KEY] ?: 0L
    }

    /** Epoch ms hasta el que las vidas son INFINITAS (0 = nunca / expirado). */
    val infiniteLivesUntilFlow: Flow<Long> = context.prefs.map { preferences ->
        preferences[INFINITE_LIVES_UNTIL_KEY] ?: 0L
    }

    suspend fun saveInfiniteLivesUntil(until: Long) {
        context.dataStore.edit { preferences ->
            preferences[INFINITE_LIVES_UNTIL_KEY] = until.coerceAtLeast(0L)
        }
    }

    val maxLevelFlow: Flow<Int> = context.prefs.map { preferences ->
        preferences[MAX_LEVEL_KEY] ?: 1 // Empieza en el nivel 1
    }

    /** Cursor de Home. -1 = sin elegir (→ la UI usa la frontera), 0 = INFINITO, ≥1 = nivel. */
    val selectedLevelFlow: Flow<Int> = context.prefs.map { preferences ->
        preferences[SELECTED_LEVEL_KEY] ?: -1
    }

    val savedGameStateFlow: Flow<String?> = context.prefs.map { preferences ->
        preferences[SAVED_GAME_STATE_KEY]
    }

    /** Epoch ms del último guardado de la partida en curso. 0 = no hay ranura (o es anterior a v4). */
    val savedGameAtFlow: Flow<Long> = context.prefs.map { preferences ->
        preferences[SAVED_GAME_AT_KEY] ?: 0L
    }

    /**
     * Nivel de la partida guardada, **sin abrir el tablero** (P-178). `0` = no hay ranura, o la
     * escribió una versión anterior a esta clave (y entonces hay que parsear, como se hacía antes).
     */
    val savedGameLevelFlow: Flow<Int> = context.prefs.map { preferences ->
        preferences[SAVED_GAME_LEVEL_KEY] ?: 0
    }

    // Master mute (botón rápido 🔊/🔇): silencia todo sin perder los niveles de los sliders.
    val muteFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[MUTE_KEY] ?: false
    }

    // Volumen de música/efectos (0f..1f). Migración: si no hay valor de volumen pero existe el viejo
    // on/off, se traduce (off → 0f). Por defecto música 0.6 y efectos 1.0.
    val musicVolumeFlow: Flow<Float> = context.prefs.map { preferences ->
        preferences[MUSIC_VOLUME_KEY] ?: if (preferences[MUSIC_ENABLED_KEY] == false) 0f else 0.6f
    }

    val sfxVolumeFlow: Flow<Float> = context.prefs.map { preferences ->
        preferences[SFX_VOLUME_KEY] ?: if (preferences[SFX_ENABLED_KEY] == false) 0f else 1f
    }

    val vibrationEnabledFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[VIBRATION_ENABLED_KEY] ?: true
    }

    val tiltEnabledFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[TILT_ENABLED_KEY] ?: true
    }

    /**
     * ¿Quiere el jugador los avisos de reenganche? (P-166, 2026-09-04.)
     *
     * Por defecto **sí**, porque el permiso del sistema ya es la primera puerta y nadie llega aquí
     * sin haberla abierto a mano. Este interruptor es la segunda, la que se puede cerrar **dentro
     * del juego**: apagarlo cancela los tres avisos programados y deja de programarlos.
     */
    val notificationsEnabledFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[NOTIFICATIONS_ENABLED_KEY] ?: true
    }

    /**
     * `null` = el jugador NUNCA lo tocó → quien decide es el sistema (si tiene las animaciones
     * desactivadas en Accesibilidad, se arranca en modo reducido). Ver `GameViewModel`.
     */
    val reducedFxFlow: Flow<Boolean?> = context.prefs.map { preferences ->
        preferences[REDUCED_FX_KEY]
    }

    /** Estado del cupo diario de anuncios recompensados (ver `RewardedAdQuota`). */
    val adQuotaFlow: Flow<RewardedAdQuota.State> = context.prefs.map { preferences ->
        RewardedAdQuota.State(
            day = preferences[AD_QUOTA_DAY_KEY] ?: 0,
            watched = preferences[AD_QUOTA_COUNT_KEY] ?: 0,
            lastAtMs = preferences[AD_QUOTA_LAST_MS_KEY] ?: 0L
        )
    }

    val tutorialDoneFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[TUTORIAL_DONE_KEY] ?: false
    }

    val legalConsentVersionFlow: Flow<String?> = context.prefs.map { preferences ->
        preferences[LEGAL_CONSENT_VERSION_KEY]
    }

    val accountChoiceMadeFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[ACCOUNT_CHOICE_MADE_KEY] ?: false
    }

    /** Ids de cápsulas ya vistas. Vacío = jugador nuevo (o quien reinstaló). */
    val mechanicsSeenFlow: Flow<Set<String>> = context.prefs.map { preferences ->
        preferences[MECHANICS_SEEN_KEY] ?: emptySet()
    }

    val starterPackOfferedFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[STARTER_PACK_OFFERED_KEY] ?: false
    }

    val abyssCardRevealSeenFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[ABYSS_CARD_REVEAL_SEEN_KEY] ?: false
    }

    val autoReducedFxDoneFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[AUTO_REDUCED_FX_DONE_KEY] ?: false
    }

    val adsRemovedFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[ADS_REMOVED_KEY] ?: false
    }

    val starterPackGrantedFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[STARTER_PACK_GRANTED_KEY] ?: false
    }

    val blockBankPurchasedFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[BLOCK_BANK_PURCHASED_KEY] ?: false
    }

    val candyRefundedFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[CANDY_REFUNDED_KEY] ?: false
    }

    val coinBonusClaimedFlow: Flow<Set<String>> = context.prefs.map { preferences ->
        val json = preferences[COIN_BONUS_CLAIMED_KEY] ?: "[]"
        try { Json.decodeFromString<List<String>>(json).toSet() } catch (_: Exception) { emptySet() }
    }

    val streakFlow: Flow<Int> = context.prefs.map { preferences ->
        preferences[STREAK_KEY] ?: 0
    }

    val lastPlayedDayFlow: Flow<Long> = context.prefs.map { preferences ->
        preferences[LAST_PLAYED_DAY_KEY] ?: -1L
    }

    /** Restaura racha + último día jugado desde la nube (sin disparar la lógica de día nuevo). */
    suspend fun saveStreakState(streak: Int, lastPlayedDay: Long) {
        context.dataStore.edit { prefs ->
            prefs[STREAK_KEY] = streak
            prefs[LAST_PLAYED_DAY_KEY] = lastPlayedDay
        }
    }

    // Recorta también AL LEER: los nombres guardados antes de bajar el límite a
    // [MAX_DISPLAY_NAME_LENGTH] siguen en disco con hasta 20 caracteres y romperían el layout del
    // perfil/ranking hasta que el jugador se cambiara el nombre.
    val displayNameFlow: Flow<String> = context.prefs.map { preferences ->
        (preferences[DISPLAY_NAME_KEY] ?: "").take(MAX_DISPLAY_NAME_LENGTH)
    }

    val unlockedCosmeticsFlow: Flow<Set<String>> = context.prefs.map { prefs ->
        val json = prefs[UNLOCKED_COSMETICS_KEY] ?: "[]"
        try { kotlinx.serialization.json.Json.decodeFromString<List<String>>(json).toSet() }
        catch (_: Exception) { emptySet() }
    }

    val activeCosmeticFlow: Flow<String?> = context.prefs.map { prefs ->
        prefs[ACTIVE_COSMETIC_KEY]
    }

    val avatarFlow: Flow<TeikaType> = context.prefs.map { prefs ->
        val name = prefs[AVATAR_KEY]
        if (name != null) try { TeikaType.valueOf(name) } catch (_: Exception) { TeikaType.TEIKA_1 }
        else TeikaType.TEIKA_1
    }

    val totalXpFlow: Flow<Int> = context.prefs.map { prefs ->
        prefs[TOTAL_XP_KEY] ?: 0
    }

    val teikaAbilityLoadoutFlow: Flow<TeikaAbilityLoadout> = context.prefs.map { prefs ->
        val json = prefs[TEIKA_ABILITY_LOADOUT_KEY]
        if (json != null) {
            try { Json.decodeFromString(json) }
            catch (_: Exception) { TeikaAbilityLoadout() }
        } else TeikaAbilityLoadout()
    }

    val companionUpgradesFlow: Flow<CompanionAbilityUpgrades> = context.prefs.map { prefs ->
        val json = prefs[COMPANION_UPGRADES_KEY]
        if (json != null) {
            try { Json.decodeFromString(json) }
            catch (_: Exception) { CompanionAbilityUpgrades() }
        } else CompanionAbilityUpgrades()
    }

    /**
     * Cargas de habilidad disponibles por compañero. Es un CONSUMIBLE, no una preferencia: cada
     * lanzamiento lo decrementa (ver `GameViewModel.activateCompanionAbility`).
     */
    val companionChargesFlow: Flow<CompanionCharges> = context.prefs.map { prefs ->
        val json = prefs[COMPANION_CHARGES_KEY]
        if (json != null) {
            try { Json.decodeFromString(json) }
            catch (_: Exception) { CompanionCharges() }
        } else CompanionCharges()
    }

    /**
     * EL CINTURÓN: los compañeros equipados para la próxima partida. Es una ELECCIÓN del jugador
     * (no un consumible ni un ajuste del aparato), así que viaja a la nube como el compañero
     * seleccionado. Se sanea al cargarlo contra los personajes reclamados de ESTE dispositivo.
     */
    val companionBeltFlow: Flow<CompanionBelt> = context.prefs.map { prefs ->
        val json = prefs[COMPANION_BELT_KEY]
        if (json != null) {
            try { Json.decodeFromString(json) }
            catch (_: Exception) { CompanionBelt() }
        } else CompanionBelt()
    }

    val crystalInventoryFlow: Flow<CrystalInventory> = context.prefs.map { prefs ->
        val json = prefs[CRYSTAL_INVENTORY_KEY]
        if (json != null) {
            try { Json.decodeFromString(json) }
            catch (_: Exception) { CrystalInventory() }
        } else CrystalInventory()
    }

    // ── Named Characters ──────────────────────────────────────────────────────

    /**
     * Flow de IDs de personajes ya reclamados por el jugador (al tocar el banner de revelación).
     * Emite el Set actualizado cada vez que se reclama un nuevo personaje.
     */
    val claimedCharacterIdsFlow: Flow<Set<String>> = context.prefs.map { prefs ->
        val json = prefs[CLAIMED_CHARACTERS_KEY] ?: "[]"
        try { Json.decodeFromString<List<String>>(json).toSet() }
        catch (_: Exception) { emptySet() }
    }

    /**
     * Marca el personaje [id] como reclamado por el jugador.
     * Si ya estaba reclamado, es un no-op (retorna false).
     * Si es la primera vez, persiste y retorna true.
     */
    suspend fun claimCharacter(id: String): Boolean {
        var isNew = false
        context.dataStore.edit { prefs ->
            val json = prefs[CLAIMED_CHARACTERS_KEY] ?: "[]"
            val current: List<String> = try {
                Json.decodeFromString(json)
            } catch (_: Exception) { emptyList() }

            if (id !in current) {
                isNew = true
                prefs[CLAIMED_CHARACTERS_KEY] = Json.encodeToString(current + id)
            }
        }
        return isNew
    }

    // ── Selected companion ────────────────────────────────────────────────────

    val selectedCompanionFlow: Flow<String?> = context.prefs.map { prefs ->
        prefs[SELECTED_COMPANION_KEY]
    }

    suspend fun saveSelectedCompanion(id: String?) {
        context.dataStore.edit { prefs ->
            if (id == null) prefs.remove(SELECTED_COMPANION_KEY)
            else prefs[SELECTED_COMPANION_KEY] = id
        }
    }


    suspend fun saveTeikaAbilityLoadout(loadout: TeikaAbilityLoadout) {
        context.dataStore.edit { prefs ->
            prefs[TEIKA_ABILITY_LOADOUT_KEY] = Json.encodeToString(loadout)
        }
    }

    suspend fun saveCompanionUpgrades(upgrades: CompanionAbilityUpgrades) {
        context.dataStore.edit { prefs ->
            prefs[COMPANION_UPGRADES_KEY] = Json.encodeToString(upgrades)
        }
    }

    /**
     * Escribe el inventario de cargas COMPLETO. El cálculo (sumar, gastar, topar) vive en
     * [CompanionCharges]; aquí solo se persiste el resultado, para que no haya dos sitios decidiendo
     * cuántas cargas tiene el jugador.
     */
    suspend fun saveCompanionCharges(charges: CompanionCharges) {
        context.dataStore.edit { prefs ->
            prefs[COMPANION_CHARGES_KEY] = Json.encodeToString(charges)
        }
    }

    /**
     * ¿Ya se sembró el cinturón de arranque en este dispositivo?
     *
     * Sin esta bandera el relleno automático corría en CADA carga y **le devolvía compañeros que el
     * jugador acababa de sacar**: dejabas el cinturón en 2 a propósito, cerrabas la app y al volver
     * había un tercero puesto por el juego (reproducido en el móvil el 2026-08-29c). El relleno es
     * una MIGRACIÓN —para que quien nunca abra la Sala de Preparación no entre con la hoja de
     * habilidades en blanco—, no una regla permanente.
     */
    val companionBeltSeededFlow: Flow<Boolean> = context.prefs.map { prefs ->
        prefs[COMPANION_BELT_SEEDED_KEY] ?: false
    }

    suspend fun saveCompanionBeltSeeded(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[COMPANION_BELT_SEEDED_KEY] = value }
    }

    /** Escribe el cinturón completo. El cálculo (equipar, soltar, sanear) vive en [CompanionBelt]. */
    suspend fun saveCompanionBelt(belt: CompanionBelt) {
        context.dataStore.edit { prefs ->
            prefs[COMPANION_BELT_KEY] = Json.encodeToString(belt)
        }
    }

    suspend fun saveCrystalInventory(inventory: CrystalInventory) {
        context.dataStore.edit { prefs ->
            prefs[CRYSTAL_INVENTORY_KEY] = Json.encodeToString(inventory)
        }
    }

    suspend fun saveUnlockedCosmetics(cosmetics: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[UNLOCKED_COSMETICS_KEY] = Json.encodeToString(cosmetics.toList())
        }
    }

    suspend fun saveAvatar(type: TeikaType) {
        context.dataStore.edit { prefs -> prefs[AVATAR_KEY] = type.name }
    }

    suspend fun saveActiveCosmetic(key: String?) {
        context.dataStore.edit { prefs ->
            if (key != null) prefs[ACTIVE_COSMETIC_KEY] = key
            else prefs.remove(ACTIVE_COSMETIC_KEY)
        }
    }


    /** Guarda el nombre ya recortado a [MAX_DISPLAY_NAME_LENGTH]: éste es el último cerrojo, ninguna
     *  ruta (onboarding, ajustes, nombre de Google, merge de nube) puede saltárselo. */
    suspend fun saveDisplayName(name: String) {
        context.dataStore.edit { preferences ->
            preferences[DISPLAY_NAME_KEY] = name.trim().take(MAX_DISPLAY_NAME_LENGTH)
        }
    }

    /**
     * Comprueba si hoy es un día nuevo, actualiza la racha y devuelve (racha, esNuevoDia).
     * La regla vive en [DailyStreakRules] (testeada); aquí solo el I/O. La tabla de bonos de
     * teikoins por hito se RETIRÓ: era código muerto desde la economía tiburón (el llamador
     * la ignoraba) — la racha premia con XP y celebración, no con moneda.
     */
    suspend fun checkAndUpdateStreak(): Pair<Int, Boolean> {
        val today = java.time.LocalDate.now().toEpochDay()
        val prefs = context.prefs.first()
        val lastDay = prefs[LAST_PLAYED_DAY_KEY] ?: -1L
        val currentStreak = prefs[STREAK_KEY] ?: 0

        if (!DailyStreakRules.isNewDay(lastDay, today)) return currentStreak to false

        val newStreak = DailyStreakRules.advance(lastDay, today, currentStreak)
        context.dataStore.edit { p ->
            p[STREAK_KEY] = newStreak
            p[LAST_PLAYED_DAY_KEY] = today
        }
        return newStreak to true
    }

    /** Suma XP al total acumulado y devuelve el nuevo total. */
    suspend fun addXp(amount: Int): Int {
        var result = 0
        context.dataStore.edit { prefs ->
            val current = prefs[TOTAL_XP_KEY] ?: 0
            result = current + amount
            prefs[TOTAL_XP_KEY] = result
        }
        return result
    }

    suspend fun saveHighScore(score: Int) {
        context.dataStore.edit { preferences ->
            val currentScore = preferences[HIGH_SCORE_KEY] ?: 0
            if (score > currentScore) {
                preferences[HIGH_SCORE_KEY] = score
            }
        }
    }

    // ── Racha de VICTORIAS de niveles (2026-07-20) ────────────────────────────────────────────
    val winStreakFlow: Flow<Int> = context.prefs.map { it[WIN_STREAK_KEY] ?: 0 }
    val bestWinStreakFlow: Flow<Int> = context.prefs.map { it[BEST_WIN_STREAK_KEY] ?: 0 }

    /** Victoria de nivel: la racha sube y, si supera la mejor, la mejor la sigue. */
    suspend fun recordLevelWin() {
        context.dataStore.edit { prefs ->
            val next = (prefs[WIN_STREAK_KEY] ?: 0) + 1
            prefs[WIN_STREAK_KEY] = next
            if (next > (prefs[BEST_WIN_STREAK_KEY] ?: 0)) prefs[BEST_WIN_STREAK_KEY] = next
        }
    }

    /** Derrota en nivel: la racha se corta (la mejor queda como logro). */
    suspend fun recordLevelLoss() {
        context.dataStore.edit { prefs -> prefs[WIN_STREAK_KEY] = 0 }
    }

    /** Mejor combo histórico (solo sube). */
    val bestComboFlow: Flow<Int> = context.prefs.map { it[BEST_COMBO_KEY] ?: 0 }
    suspend fun saveBestCombo(streak: Int) {
        context.dataStore.edit { prefs ->
            if (streak > (prefs[BEST_COMBO_KEY] ?: 0)) prefs[BEST_COMBO_KEY] = streak
        }
    }

    /* (ERRADICADOS 2026-08-09, con el modo WALLBLOCK) `bestTideRowsFlow`, `saveBestTideRows`,
     * `purgeLegacyBestTideRows` e `infiniteDifficultyFlow`/`saveInfiniteDifficulty`. Sus claves
     * (`best_tide_rows`, `infinite_difficulty`) se quedan huérfanas en DataStore de los jugadores
     * que ya las tengan: no se borran a propósito — un DataStore no se reescribe para quitar dos
     * enteros que nadie lee. NO REUTILIZAR esos nombres de clave. */

    /**
     * P-227. A diferencia de [saveMaxTeikas], esto NO se queda con el mejor: el puesto del ranking no
     * es un récord, es dónde estás ahora. Guardar el mejor de la historia sería enseñar un puesto que
     * el jugador ya no ocupa, que es la única forma de que este número mienta.
     */
    suspend fun saveLastKnownRank(rank: Int, seenAtMs: Long) {
        context.dataStore.edit { preferences ->
            // «100+» (RANK_BEYOND) se guarda tal cual: el carné lo pinta; el resto de negativos es 0.
            preferences[LAST_RANK_KEY] =
                if (rank == LeaderboardEntry.RANK_BEYOND) rank else rank.coerceAtLeast(0)
            preferences[LAST_RANK_AT_KEY] = seenAtMs
        }
    }

    suspend fun saveMaxTeikas(teikas: Int) {
        context.dataStore.edit { preferences ->
            val currentMax = preferences[MAX_TEIKAS_KEY] ?: 0
            if (teikas > currentMax) {
                preferences[MAX_TEIKAS_KEY] = teikas
            }
        }
    }

    suspend fun saveTeikoins(teikoins: Int) {
        context.dataStore.edit { preferences ->
            preferences[COINS_KEY] = teikoins
        }
    }

    suspend fun saveLives(lives: Int, nextRegenAt: Long) {
        context.dataStore.edit { preferences ->
            preferences[LIVES_KEY] = lives
            preferences[LIVES_NEXT_REGEN_KEY] = nextRegenAt
        }
    }

    suspend fun saveMaxLevel(level: Int) {
        context.dataStore.edit { preferences ->
            val currentMax = preferences[MAX_LEVEL_KEY] ?: 1
            if (level > currentMax) {
                preferences[MAX_LEVEL_KEY] = level
            }
        }
    }

    /** Guarda el cursor de Home (nivel seleccionado por el jugador). */
    suspend fun saveSelectedLevel(level: Int) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_LEVEL_KEY] = level.coerceAtLeast(0)
        }
    }

    suspend fun forceSetMaxLevel(level: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_LEVEL_KEY] = level.coerceAtLeast(1)
        }
    }

    suspend fun saveTutorialDone() {
        context.dataStore.edit { preferences ->
            preferences[TUTORIAL_DONE_KEY] = true
        }
    }

    suspend fun saveLegalConsent() {
        context.dataStore.edit { preferences ->
            preferences[LEGAL_CONSENT_VERSION_KEY] = CURRENT_LEGAL_CONSENT_VERSION
        }
    }

    suspend fun saveAccountChoiceMade() {
        context.dataStore.edit { preferences ->
            preferences[ACCOUNT_CHOICE_MADE_KEY] = true
        }
    }

    /**
     * Marca una cápsula como vista. **Lee y escribe dentro del mismo `edit`**, que es lo que lo hace
     * seguro: dos cápsulas marcadas casi a la vez con un `value` leído antes se pisarían la una a la
     * otra y una de las dos volvería a salir.
     */
    /** P-449: ¿ya ha girado alguna ficha? */
    val rotateLearnedFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[ROTATE_LEARNED_KEY] ?: false
    }

    suspend fun saveRotateLearned() {
        context.dataStore.edit { preferences -> preferences[ROTATE_LEARNED_KEY] = true }
    }

    suspend fun saveMechanicSeen(id: String) {
        context.dataStore.edit { preferences ->
            preferences[MECHANICS_SEEN_KEY] = (preferences[MECHANICS_SEEN_KEY] ?: emptySet()) + id
        }
    }

    /** Olvida todas las cápsulas: el jugador vuelve a aprender el juego. Lo usa `resetProgress`. */
    suspend fun clearMechanicsSeen() {
        context.dataStore.edit { preferences -> preferences.remove(MECHANICS_SEEN_KEY) }
    }

    suspend fun saveStarterPackOffered(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[STARTER_PACK_OFFERED_KEY] = value
        }
    }

    suspend fun saveAbyssCardRevealSeen(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ABYSS_CARD_REVEAL_SEEN_KEY] = value
        }
    }

    suspend fun saveAutoReducedFxDone(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_REDUCED_FX_DONE_KEY] = value
        }
    }

    val companionChargesSeededFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[COMPANION_CHARGES_SEEDED_KEY] ?: false
    }

    suspend fun saveCompanionChargesSeeded(value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[COMPANION_CHARGES_SEEDED_KEY] = value
        }
    }

    /** «Quitar anuncios» es para siempre: esto solo enciende el flag, nunca lo apaga. */
    suspend fun saveAdsRemoved() {
        context.dataStore.edit { preferences ->
            preferences[ADS_REMOVED_KEY] = true
        }
    }

    /** El paquete inicial ya está entregado. Como el de arriba: solo enciende, nunca apaga. */
    suspend fun saveStarterPackGranted() {
        context.dataStore.edit { preferences ->
            preferences[STARTER_PACK_GRANTED_KEY] = true
        }
    }

    /** El banco de bloques ya está comprado con teikoins. Solo enciende, nunca apaga. */
    suspend fun saveBlockBankPurchased() {
        context.dataStore.edit { preferences ->
            preferences[BLOCK_BANK_PURCHASED_KEY] = true
        }
    }

    /** P-249: la devolución del Caramelo ya se pagó. Solo enciende, nunca apaga. */
    suspend fun saveCandyRefunded() {
        context.dataStore.edit { preferences ->
            preferences[CANDY_REFUNDED_KEY] = true
        }
    }

    /** Packs que ya cobraron su bono de bienvenida. Solo CRECE: un bono gastado no vuelve. */
    suspend fun saveCoinBonusClaimed(ids: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[COIN_BONUS_CLAIMED_KEY] = Json.encodeToString(ids.toList().sorted())
        }
    }

    /** Flag puente Google (connect → arranque tras reiniciar) para decidir si mostrar el conflicto. */
    val pendingCloudConflictPromptFlow: Flow<Boolean> = context.prefs.map { preferences ->
        preferences[PENDING_CLOUD_CONFLICT_PROMPT_KEY] ?: false
    }

    suspend fun savePendingCloudConflictPrompt(pending: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PENDING_CLOUD_CONFLICT_PROMPT_KEY] = pending
        }
    }

    // ── Aviso de vinculación para anónimos (capado) ───────────────────────────
    val linkNudgeCountFlow: Flow<Int> = context.prefs.map { preferences ->
        preferences[LINK_NUDGE_COUNT_KEY] ?: 0
    }

    val linkNudgeLastLevelFlow: Flow<Int> = context.prefs.map { preferences ->
        preferences[LINK_NUDGE_LAST_LEVEL_KEY] ?: 0
    }

    /** Registra que se mostró el aviso de vinculación en [level] (incrementa el contador). */
    suspend fun recordLinkNudgeShown(level: Int) {
        context.dataStore.edit { preferences ->
            preferences[LINK_NUDGE_COUNT_KEY] = (preferences[LINK_NUDGE_COUNT_KEY] ?: 0) + 1
            preferences[LINK_NUDGE_LAST_LEVEL_KEY] = level
        }
    }

    suspend fun saveMute(muted: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MUTE_KEY] = muted
        }
    }

    suspend fun saveMusicVolume(volume: Float) {
        context.dataStore.edit { preferences ->
            preferences[MUSIC_VOLUME_KEY] = volume
        }
    }

    suspend fun saveSfxVolume(volume: Float) {
        context.dataStore.edit { preferences ->
            preferences[SFX_VOLUME_KEY] = volume
        }
    }

    suspend fun saveVibrationEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VIBRATION_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATIONS_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveTiltEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TILT_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveReducedFx(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[REDUCED_FX_KEY] = enabled
        }
    }

    suspend fun saveAdQuota(state: RewardedAdQuota.State) {
        context.dataStore.edit { preferences ->
            preferences[AD_QUOTA_DAY_KEY] = state.day
            preferences[AD_QUOTA_COUNT_KEY] = state.watched
            preferences[AD_QUOTA_LAST_MS_KEY] = state.lastAtMs
        }
    }

    /**
     * Escribe (o borra) la ranura de partida en curso **con su marca de tiempo**. La marca es lo que
     * decide, al sincronizar, cuál de los dos tableros de la cuenta es el bueno (P-113); se pasa por
     * parámetro para que una restauración de nube pueda conservar la hora del guardado ORIGINAL en
     * vez de estampar la de la descarga, que la haría ganar siempre.
     */
    suspend fun saveGameState(
        stateJson: String?,
        savedAtMs: Long = System.currentTimeMillis(),
        level: Int = 0
    ) {
        context.dataStore.edit { preferences ->
            if (stateJson != null) {
                preferences[SAVED_GAME_STATE_KEY] = stateJson
                preferences[SAVED_GAME_AT_KEY] = savedAtMs
                // El nivel se escribe FUERA del blob a propósito (P-178): es lo único que el
                // arranque necesita saber, y leerlo aquí le ahorra deserializar el tablero entero.
                if (level > 0) preferences[SAVED_GAME_LEVEL_KEY] = level
                else preferences.remove(SAVED_GAME_LEVEL_KEY)
            } else {
                preferences.remove(SAVED_GAME_STATE_KEY)
                preferences.remove(SAVED_GAME_AT_KEY)
                preferences.remove(SAVED_GAME_LEVEL_KEY)
            }
        }
    }

    /** Borra record, niveles, XP y partida guardada. Conserva teikoins, power-ups y habilidades. */
    suspend fun resetProgressOnly() {
        context.dataStore.edit { prefs ->
            prefs.remove(HIGH_SCORE_KEY)
            prefs.remove(MAX_TEIKAS_KEY)
            prefs.remove(MAX_LEVEL_KEY)
            prefs.remove(SELECTED_LEVEL_KEY)
            prefs.remove(LEVEL_STARS_KEY)
            prefs.remove(MURAL_REWARDS_KEY)
            prefs.remove(SAVED_GAME_STATE_KEY)
            prefs.remove(SAVED_GAME_AT_KEY)
            prefs.remove(SAVED_GAME_LEVEL_KEY)
            prefs.remove(TOTAL_XP_KEY)
            // Progresión derivada del avance por niveles: compañeros desbloqueados, sus mejoras
            // de habilidad y los cristales mágicos. Un reset honesto los devuelve a cero.
            prefs.remove(CLAIMED_CHARACTERS_KEY)
            prefs.remove(COMPANION_UPGRADES_KEY)
            // P-433 (2026-10-01, decisión del usuario): los USOS de power-ups NO se borran. Se pagan
            // con teikoins, como los bloques, y la confirmación promete «tus monedas y compras se
            // conservan». Al volver a desbloquear cada power-up jugando, sus usos le esperan.
            prefs.remove(CRYSTAL_INVENTORY_KEY)
            prefs.remove(CRYSTAL_AWARDED_KEY)
            prefs.remove(SELECTED_COMPANION_KEY)
            prefs.remove(COMPANION_BELT_KEY)
            prefs.remove(COMPANION_BELT_SEEDED_KEY)
        }
    }

    suspend fun clearAll() {
        context.dataStore.edit { preferences ->
            // Account switching/deletion clears player data, but accepting the app documents is a
            // device-level decision and must not be requested again after every logout.
            val acceptedLegalVersion = preferences[LEGAL_CONSENT_VERSION_KEY]
            preferences.clear()
            acceptedLegalVersion?.let { preferences[LEGAL_CONSENT_VERSION_KEY] = it }
        }
    }
}
