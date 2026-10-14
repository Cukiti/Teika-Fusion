package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Progreso del jugador que se respalda en la nube vía Play Games Saved Games (Snapshots →
 * carpeta oculta drive.appdata de su cuenta Google). Sobrevive reinstalaciones / cambio de móvil.
 *
 * Los campos acumulativos se fusionan por máximo/unión. Para selecciones y cosméticos activos se
 * conserva la elección local, salvo que el dispositivo sea nuevo y todavía no tenga una.
 */
@Serializable
data class CloudSaveData(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val updatedAtEpochMs: Long = 0L,
    val maxLevel: Int = 1,
    val teikoins: Int = 0,
    val highScore: Int = 0,
    val maxTeikas: Int = 0,
    val totalXp: Int = 0,
    val claimedCharacters: List<String> = emptyList(),
    val teikaAbilityLoadout: TeikaAbilityLoadout = TeikaAbilityLoadout(),
    val companionUpgrades: CompanionAbilityUpgrades = CompanionAbilityUpgrades(),
    /**
     * Cargas de habilidad por compañero (2026-08-27). Es un consumible comprado con teikoins y
     * ganado con 3★, así que **sí es dato de cuenta**: perderlo al cambiar de móvil sería perder
     * algo que el jugador pagó.
     */
    val companionCharges: CompanionCharges = CompanionCharges(),
    /**
     * EL CINTURÓN: los ≤3 compañeros equipados para la partida (2026-08-29). Es una elección, no un
     * acumulable, así que se fusiona como [selectedCompanion]: **manda el local** y la nube solo
     * rellena si este dispositivo todavía no ha elegido. Fusionarlo por unión daría cinturones de
     * más de tres, y por máximo no significa nada.
     */
    val companionBelt: CompanionBelt = CompanionBelt(),
    val crystalInventory: CrystalInventory = CrystalInventory(),
    val unlockedCosmetics: List<String> = emptyList(),
    val activeCosmetic: String? = null,
    val avatarType: String? = null,
    val selectedCompanion: String? = null,
    val displayName: String = "",
    val tutorialDone: Boolean = false,
    // schema v3: progreso que antes no se respaldaba (se perdía al restaurar en otro dispositivo).
    val levelStars: Map<Int, Int> = emptyMap(),          // nivel → mejores estrellas (1-3)
    val muralRewards: List<Int> = emptyList(),           // capítulos cuyo mural ya pagó (anti doble-cobro)
    val crystalAwardedLevels: List<Int> = emptyList(),   // niveles con cristal ya otorgado (anti re-grant)
    val dailyStreak: Int = 0,
    val lastPlayedDay: Long = -1L,
    /**
     * Compró «quitar anuncios». Viaja en la nube por comodidad, pero **no es la fuente de verdad**:
     * esa es Google Play, que devuelve la compra al reinstalar (ver
     * `BillingManager.processPendingPurchases`). Aquí solo evita que el jugador vea anuncios
     * durante los segundos que Play tarda en responder en un dispositivo nuevo.
     */
    val adsRemoved: Boolean = false,
    /**
     * **EL PAQUETE INICIAL YA SE ENTREGÓ** (2026-09-06). Igual que [adsRemoved], Play es quien sabe
     * si el jugador lo COMPRÓ; esto dice si su contenido —cinco mil teikoins, dos bloques, siete
     * días de vidas infinitas y Estela— ya está en la cuenta.
     *
     * **Tiene que viajar.** Si fuera local, reinstalar volvería a entregarlo entero: Play reenvía
     * las compras no consumibles en cada arranque, y esa es exactamente la puerta que esta marca
     * cierra.
     */
    val starterPackGranted: Boolean = false,
    /**
     * P-249 · **LA DEVOLUCIÓN DEL CARAMELO YA SE PAGÓ** (2026-09-23). El bloque se retiró y quien lo
     * tenía recupera 2.000 teikoins una vez ([CandyRetirement]). Viaja por lo mismo que
     * [starterPackGranted]: «candy» sigue en [unlockedCosmetics] como registro, así que sin esta
     * marca cada instalación nueva volvería a pagar la devolución.
     */
    val candyRefunded: Boolean = false,
    /**
     * **EL BANCO DE BLOQUES COMPRADO CON TEIKOINS** (2026-09-28). Es una compra: si fuera local, cambiar
     * de móvil lo perdería. El paquete inicial también lo da, pero eso ya lo dice [starterPackGranted].
     */
    val blockBankPurchased: Boolean = false,
    /**
     * Packs de monedas cuya primera compra ya cobró el **bono de bienvenida** del 100 %
     * ([StorePacks.welcomeBonusFor]). Viaja por el mismo motivo que la marca de arriba: local, un
     * dispositivo nuevo volvería a doblar los tres packs.
     */
    val coinBonusClaimed: List<String> = emptyList(),
    /* (RETIRADO 2026-08-09, con el modo WALLBLOCK) `bestTideRows`: el récord de profundidad. Los
     * documentos ya escritos en Firestore lo conservan y `ignoreUnknownKeys` los sigue leyendo sin
     * romperse; simplemente ya no significa nada. */
    /**
     * **MEJOR COMBO** conseguido: un número de orgullo que el jugador ve en su perfil y que, al
     * reinstalar, volvía a 0 aunque su cuenta tuviera el resto del progreso intacto. Lo cazó
     * `CloudSchemaCoverageTest` el mismo día en que se añadió la prueba.
     */
    val bestCombo: Int = 0,
    // ── schema v4 (P-113 + P-114, 2026-09-04): la CUENTA, no el aparato ──────────────────────
    /**
     * **LAS VIDAS SON DE LA CUENTA** (P-114). Hasta v3 eran del dispositivo, y eso abría una puerta
     * trasera evidente: reinstalar —o instalar en un segundo móvil— devolvía la reserva llena
     * conservando todo el progreso, así que el freno económico del juego se saltaba gratis.
     *
     * Se fusionan **por MÍNIMO**, al revés que teikoins, cristales o cargas. Ahí el máximo protege
     * al jugador de perder algo que pagó; aquí el máximo premiaría justo lo contrario —gastar en un
     * móvil y sincronizar desde el otro—. Por eso el valor por defecto es [LivesRules.MAX]: un
     * documento de esquema viejo, sin este campo, entra como «lleno» y **nunca** baja las vidas del
     * dispositivo que sí las tiene contadas.
     */
    val lives: Int = LivesRules.MAX,
    /**
     * Instante (epoch ms) en que regenera la siguiente vida. Viaja pegado a [lives] y se fusiona por
     * MÁXIMO, que es el coherente con el mínimo de arriba: si dos dispositivos discrepan, gana la
     * espera más larga. Con la reserva llena vale 0 (no hay temporizador que restaurar).
     */
    val livesNextRegenAtMs: Long = 0L,
    /**
     * Vidas INFINITAS hasta este instante (0 = expirado). Sube a la cuenta con las vidas, por
     * MÁXIMO: es un perk que se gana con la racha o **se compra con teikoins**, y una compra no se
     * pierde por cambiar de móvil. (Hasta v3 era local «porque seguía a las vidas», que ya no son
     * locales — el motivo se movió con ellas.)
     */
    val infiniteLivesUntilMs: Long = 0L,
    /**
     * **EL INTENTO EN CURSO** (P-113): el `GameState` serializado de la partida sin terminar.
     *
     * Con CONTINUAR cableado, el jugador ya espera retomar donde lo dejó; que eso funcione en un
     * móvil y no en el otro es incoherente. No se fusiona por máximo —un tablero es un objeto
     * entero y mezclar dos no significa nada—: **gana el más reciente**, y por eso lleva su propia
     * marca de tiempo [gameSavedAtMs] en vez de fiarse de [updatedAtEpochMs], que es la de la
     * sincronización y no la del guardado.
     *
     * `null` = no hay intento (o el que había excedía [MAX_GAME_STATE_JSON]: un documento de
     * Firestore tiene 1 MB de tope y el progreso de la cuenta pesa más que una partida suelta, así
     * que ante la duda se sacrifica el tablero, no el progreso).
     */
    val gameStateJson: String? = null,
    /** Epoch ms del guardado de [gameStateJson]. Es el árbitro del conflicto entre dos tableros. */
    val gameSavedAtMs: Long = 0L
) {
    fun toJson(): String = JSON.encodeToString(this)

    /**
     * Fusiona con lo que hay en la nube. [nowMs] solo lo usan las vidas — ver [lives] y P-161: con el
     * perk de infinitas vigente la regla se invierte, así que hace falta saber qué hora es. Se pasa
     * por parámetro para que la fusión siga siendo comprobable sin depender del reloj de la máquina.
     */
    fun mergedWith(remote: CloudSaveData, nowMs: Long = System.currentTimeMillis()): CloudSaveData {
        val mergedInfiniteLives = maxOf(infiniteLivesUntilMs, remote.infiniteLivesUntilMs)
        // P-161 · CON EL PACK VIGENTE, LAS VIDAS VAN POR MÁXIMO.
        // `grantInfiniteLives` rellena el contador al conceder el perk, así que el mínimo arrastraba
        // la reserva del móvil donde el jugador SÍ estaba gastando: mientras el pack dura no se nota
        // —no se descuenta vida— y el día que expira aparece a 1. Es justo el escenario de alguien
        // que acaba de pagar, así que ahí manda el máximo. Sin pack, el mínimo (ver [lives]).
        val livesPerkActive = mergedInfiniteLives > nowMs
        val mergedLives =
            if (livesPerkActive) maxOf(lives, remote.lives) else minOf(lives, remote.lives)
        return copy(
            schemaVersion = maxOf(schemaVersion, remote.schemaVersion, CURRENT_SCHEMA_VERSION),
            updatedAtEpochMs = maxOf(updatedAtEpochMs, remote.updatedAtEpochMs),
            maxLevel = maxOf(maxLevel, remote.maxLevel),
            teikoins = maxOf(teikoins, remote.teikoins),
            highScore = maxOf(highScore, remote.highScore),
            maxTeikas = maxOf(maxTeikas, remote.maxTeikas),
            totalXp = maxOf(totalXp, remote.totalXp),
            claimedCharacters = (claimedCharacters + remote.claimedCharacters).distinct().sorted(),
            teikaAbilityLoadout = TeikaAbilityLoadout(
                unlocked = teikaAbilityLoadout.unlocked + remote.teikaAbilityLoadout.unlocked,
                equipped = remote.teikaAbilityLoadout.equipped + teikaAbilityLoadout.equipped
            ),
            companionUpgrades = CompanionAbilityUpgrades(
                levels = mergeIntMaps(companionUpgrades.levels, remote.companionUpgrades.levels)
            ),
            // Por MÁXIMO por compañero, igual que teikoins y cristales: ante un conflicto entre dos
            // dispositivos el jugador nunca pierde una carga que ya había pagado.
            companionCharges = CompanionCharges(
                counts = mergeIntMaps(companionCharges.counts, remote.companionCharges.counts)
            ),
            // La elección local manda; la nube solo cubre el cinturón vacío de un dispositivo nuevo.
            companionBelt = if (companionBelt.isEmpty) remote.companionBelt else companionBelt,
            // P-267: cristal único. Cada lado se convierte ANTES de fusionar (una mochila vieja por tiers
            // y una nueva no se pueden comparar) y gana el máximo, como los teikoins.
            crystalInventory = CrystalInventory(
                crystals = maxOf(
                    crystalInventory.normalized(maxLevel).crystals,
                    remote.crystalInventory.normalized(remote.maxLevel).crystals,
                ),
                legacyConverted = true,
            ),
            unlockedCosmetics = (unlockedCosmetics + remote.unlockedCosmetics).distinct().sorted(),
            activeCosmetic = activeCosmetic ?: remote.activeCosmetic,
            avatarType = avatarType
                ?.takeUnless { it == TeikaType.TEIKA_1.name }
                ?: remote.avatarType
                ?: avatarType,
            selectedCompanion = selectedCompanion ?: remote.selectedCompanion,
            displayName = displayName.ifBlank { remote.displayName },
            tutorialDone = tutorialDone || remote.tutorialDone,
            levelStars = mergeIntKeyMaps(levelStars, remote.levelStars),
            muralRewards = (muralRewards + remote.muralRewards).distinct().sorted(),
            crystalAwardedLevels = (crystalAwardedLevels + remote.crystalAwardedLevels).distinct().sorted(),
            dailyStreak = maxOf(dailyStreak, remote.dailyStreak),
            lastPlayedDay = maxOf(lastPlayedDay, remote.lastPlayedDay),
            // Una compra nunca se pierde en una fusión: basta con tenerla en un lado.
            adsRemoved = adsRemoved || remote.adsRemoved,
            // Las dos marcas del dinero se fusionan como [adsRemoved]: una compra entregada no se
            // pierde en una fusión, y un bono ya cobrado no vuelve a estar disponible por cambiar de
            // móvil. Por eso son OR y UNIÓN, nunca «manda el local».
            starterPackGranted = starterPackGranted || remote.starterPackGranted,
            candyRefunded = candyRefunded || remote.candyRefunded,
            blockBankPurchased = blockBankPurchased || remote.blockBankPurchased,
            coinBonusClaimed = (coinBonusClaimed + remote.coinBonusClaimed).distinct().sorted(),
            bestCombo = maxOf(bestCombo, remote.bestCombo),
            // Las vidas van EN PAREJA: el mínimo de reserva con la espera que le corresponde, no con la
            // del otro dispositivo. Tomarlos por separado podía dar «1 vida y regeneración a 0», que la
            // UI lee como reserva llena parada.
            lives = mergedLives,
            livesNextRegenAtMs = if (mergedLives == lives) livesNextRegenAtMs else remote.livesNextRegenAtMs,
            infiniteLivesUntilMs = mergedInfiniteLives,
            // El tablero entero del lado más reciente. En empate manda el local: es el que el jugador
            // tiene delante.
            gameStateJson = if (remote.gameSavedAtMs > gameSavedAtMs) remote.gameStateJson else gameStateJson,
            gameSavedAtMs = maxOf(gameSavedAtMs, remote.gameSavedAtMs)
            )
    }

    companion object {
        /** v4 (2026-09-04): vidas, su regeneración, el perk de infinitas y la partida en curso. */
        const val CURRENT_SCHEMA_VERSION = 4

        /**
         * Tope del tablero serializado que se sube (256 KB). Un `GameState` ronda las decenas de KB,
         * pero el documento entero de Firestore tiene 1 MB: si un guardado se dispara, se sube el
         * progreso sin él antes que arriesgar la escritura completa.
         */
        const val MAX_GAME_STATE_JSON = 256 * 1024
        private val JSON = Json { ignoreUnknownKeys = true }

        fun fromJson(raw: String): CloudSaveData? =
            try { JSON.decodeFromString<CloudSaveData>(raw) } catch (_: Exception) { null }

        private fun mergeIntMaps(local: Map<String, Int>, remote: Map<String, Int>): Map<String, Int> =
            (local.keys + remote.keys).associateWith { key ->
                maxOf(local[key] ?: 0, remote[key] ?: 0)
            }

        private fun mergeIntKeyMaps(local: Map<Int, Int>, remote: Map<Int, Int>): Map<Int, Int> =
            (local.keys + remote.keys).associateWith { key ->
                maxOf(local[key] ?: 0, remote[key] ?: 0)
            }
    }
}
