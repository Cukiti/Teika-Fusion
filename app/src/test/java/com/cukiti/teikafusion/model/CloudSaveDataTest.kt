package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSaveDataTest {

    @Test
    fun fromJson_readsLegacyVersionOneSnapshot() {
        val legacyJson = """
            {
              "maxLevel": 18,
              "teikoins": 950,
              "highScore": 12000,
              "maxTeikas": 7,
              "totalXp": 340,
              "claimedCharacters": ["ninja"]
            }
        """.trimIndent()

        val save = CloudSaveData.fromJson(legacyJson)

        assertEquals(18, save?.maxLevel)
        assertEquals(950, save?.teikoins)
        assertEquals(setOf("ninja"), save?.claimedCharacters?.toSet())
        assertEquals(CompanionAbilityUpgrades(), save?.companionUpgrades)
        assertEquals(CrystalInventory(), save?.crystalInventory)
    }

    @Test
    fun mergedWith_keepsBestProgressAndRestorablePurchases() {
        val local = CloudSaveData(
            maxLevel = 4,
            teikoins = 150,
            highScore = 500,
            totalXp = 25,
            claimedCharacters = listOf("fuego"),
            companionUpgrades = CompanionAbilityUpgrades(
                levels = mapOf("ninja" to 2, "fuego" to 1)
            ),
            crystalInventory = CrystalInventory(counts = mapOf(1 to 2, 2 to 1)),
            teikaAbilityLoadout = TeikaAbilityLoadout(
                unlocked = setOf("ESTRELLA_LLUVIA"),
                equipped = mapOf("estrella" to "ESTRELLA_LLUVIA")
            ),
            unlockedCosmetics = listOf("skin_local"),
            activeCosmetic = "skin_local",
            avatarType = TeikaType.TEIKA_1.name,
            selectedCompanion = null,
            displayName = ""
        )
        val remote = CloudSaveData(
            maxLevel = 12,
            teikoins = 900,
            highScore = 2000,
            maxTeikas = 5,
            totalXp = 800,
            claimedCharacters = listOf("ninja", "fuego"),
            companionUpgrades = CompanionAbilityUpgrades(
                levels = mapOf("ninja" to 1, "hielo" to 3)
            ),
            crystalInventory = CrystalInventory(counts = mapOf(1 to 1, 3 to 4)),
            teikaAbilityLoadout = TeikaAbilityLoadout(),
            unlockedCosmetics = listOf("skin_cloud"),
            activeCosmetic = "skin_cloud",
            avatarType = TeikaType.TEIKA_4.name,
            selectedCompanion = "ninja",
            displayName = "TeikaPlayer",
            tutorialDone = true
        )

        val merged = local.mergedWith(remote)

        assertEquals(12, merged.maxLevel)
        assertEquals(900, merged.teikoins)
        assertEquals(2000, merged.highScore)
        assertEquals(5, merged.maxTeikas)
        assertEquals(800, merged.totalXp)
        assertEquals(setOf("fuego", "ninja"), merged.claimedCharacters.toSet())
        assertEquals(2, merged.companionUpgrades.levels["ninja"])
        assertEquals(1, merged.companionUpgrades.levels["fuego"])
        assertEquals(3, merged.companionUpgrades.levels["hielo"])
        // P-267: las dos mochilas viejas por tiers se convierten (cada una con el tope de SU nivel) y
        // gana la mayor. La remota vale 1 + 4×9 = 37, con el tope de lo que da llegar al nivel 12.
        val esperado = maxOf(
            local.crystalInventory.normalized(local.maxLevel).count(),
            minOf(37, CrystalEconomy.cumulativeBefore(12)),
        )
        assertEquals(esperado, merged.crystalInventory.count())
        assertTrue(merged.crystalInventory.legacyConverted)
        assertTrue(merged.crystalInventory.counts.isEmpty())
        assertEquals(setOf("ESTRELLA_LLUVIA"), merged.teikaAbilityLoadout.unlocked)
        assertEquals("ESTRELLA_LLUVIA", merged.teikaAbilityLoadout.equipped["estrella"])
        assertEquals(setOf("skin_local", "skin_cloud"), merged.unlockedCosmetics.toSet())
        assertEquals("skin_local", merged.activeCosmetic)
        assertEquals(TeikaType.TEIKA_4.name, merged.avatarType)
        assertEquals("ninja", merged.selectedCompanion)
        assertEquals("TeikaPlayer", merged.displayName)
        assertTrue(merged.tutorialDone)
    }

    @Test
    fun mergedWith_restoresSchemaV3Progress() {
        val local = CloudSaveData(
            levelStars = mapOf(1 to 3, 2 to 1),
            muralRewards = listOf(0),
            crystalAwardedLevels = listOf(1, 2),
            dailyStreak = 2,
            lastPlayedDay = 100L
        )
        val remote = CloudSaveData(
            levelStars = mapOf(2 to 3, 3 to 2),
            muralRewards = listOf(1),
            crystalAwardedLevels = listOf(2, 5),
            dailyStreak = 5,
            lastPlayedDay = 90L
        )

        val merged = local.mergedWith(remote)

        // Estrellas: el mejor por nivel; unión de niveles.
        assertEquals(3, merged.levelStars[1])
        assertEquals(3, merged.levelStars[2])
        assertEquals(2, merged.levelStars[3])
        // Flags anti-exploit: unión.
        assertEquals(setOf(0, 1), merged.muralRewards.toSet())
        assertEquals(setOf(1, 2, 5), merged.crystalAwardedLevels.toSet())
        // Racha: la mayor; último día jugado: el más reciente.
        assertEquals(5, merged.dailyStreak)
        assertEquals(100L, merged.lastPlayedDay)
    }

    /* (ERRADICADOS 2026-08-09, con el modo WALLBLOCK) `mergedWith_keepsBestInfiniteDepth` y
     * `fromJson_toleratesSavesWithoutInfiniteDepth`: el récord de PROFUNDIDAD ya no existe. Lo que
     * sí importa ahora es que los documentos ya escritos que lo traigan sigan decodificando. */

    /**
     * **UN DOCUMENTO CON `bestTideRows` NO PUEDE ROMPER LA NUBE** (2026-08-09). El campo se retiró
     * del esquema al eliminar WALLBLOCK, pero está escrito en la cuenta de todo el que jugó. Si
     * `fromJson` perdiera `ignoreUnknownKeys`, esos jugadores dejarían de restaurar progreso — y el
     * síntoma sería «he perdido mi cuenta», no una excepción visible.
     */
    @Test
    fun fromJson_toleratesRetiredWallblockFields() {
        val conCampoMuerto =
            """{"schemaVersion":3,"maxLevel":12,"teikoins":500,"bestTideRows":77}"""
        val decoded = CloudSaveData.fromJson(conCampoMuerto)
        assertEquals(12, decoded?.maxLevel)
        assertEquals(500, decoded?.teikoins)
    }

    // ── schema v4 (P-113 + P-114, 2026-09-04) ───────────────────────────────────────────────

    @Test
    fun `las vidas se fusionan por MINIMO, no por maximo`() {
        // Es la regla al revés que teikoins/cristales/cargas, y a propósito: aquí el máximo premia
        // gastar en un móvil y sincronizar desde el otro para recuperar la reserva.
        val local = CloudSaveData(lives = 1, livesNextRegenAtMs = 9_000L)
        val remote = CloudSaveData(lives = 5, livesNextRegenAtMs = 0L)

        val merged = local.mergedWith(remote)

        assertEquals(1, merged.lives)
        // Y la espera viaja EN PAREJA con la reserva que ha ganado: 1 vida con su temporizador,
        // no 1 vida con el "sin temporizador" del móvil que estaba lleno.
        assertEquals(9_000L, merged.livesNextRegenAtMs)
    }

    @Test
    fun `un documento de esquema viejo no rellena las vidas del aparato`() {
        // v3 no tenía el campo: entra por el valor por defecto (lleno). Si el mínimo no lo tolerara,
        // la primera sincronización tras actualizar regalaría vidas — justo el agujero que cierra.
        val v3 = CloudSaveData.fromJson("""{"maxLevel": 30, "teikoins": 400}""")!!
        val local = CloudSaveData(lives = 2, livesNextRegenAtMs = 1_500L)

        val merged = local.mergedWith(v3)

        assertEquals(2, merged.lives)
        assertEquals(1_500L, merged.livesNextRegenAtMs)
        assertEquals(LivesRules.MAX, v3.lives)
    }

    @Test
    fun `el perk de vidas infinitas se fusiona por MAXIMO`() {
        // Se compra con teikoins: una compra no se pierde por cambiar de móvil.
        val local = CloudSaveData(infiniteLivesUntilMs = 0L)
        val remote = CloudSaveData(infiniteLivesUntilMs = 5_000L)

        assertEquals(5_000L, local.mergedWith(remote).infiniteLivesUntilMs)
        assertEquals(5_000L, remote.mergedWith(local).infiniteLivesUntilMs)
    }

    @Test
    fun `del tablero gana el mas reciente, entero`() {
        // Un tablero no se fusiona campo a campo: o es uno o es el otro.
        val viejo = CloudSaveData(gameStateJson = "{viejo}", gameSavedAtMs = 100L)
        val nuevo = CloudSaveData(gameStateJson = "{nuevo}", gameSavedAtMs = 200L)

        assertEquals("{nuevo}", viejo.mergedWith(nuevo).gameStateJson)
        assertEquals(200L, viejo.mergedWith(nuevo).gameSavedAtMs)
        assertEquals("{nuevo}", nuevo.mergedWith(viejo).gameStateJson)
    }

    @Test
    fun `en empate de hora manda el tablero local`() {
        // El que el jugador tiene delante. Sin esta regla, dos guardados del mismo milisegundo
        // dependerían del orden de la fusión.
        val local = CloudSaveData(gameStateJson = "{local}", gameSavedAtMs = 100L)
        val remote = CloudSaveData(gameStateJson = "{remoto}", gameSavedAtMs = 100L)

        assertEquals("{local}", local.mergedWith(remote).gameStateJson)
    }

    @Test
    fun `una nube sin tablero no borra el intento local`() {
        val local = CloudSaveData(gameStateJson = "{local}", gameSavedAtMs = 100L)

        val merged = local.mergedWith(CloudSaveData())

        assertEquals("{local}", merged.gameStateJson)
        assertEquals(100L, merged.gameSavedAtMs)
    }

    @Test
    fun `con el pack de infinitas vigente las vidas van por MAXIMO`() {
        // P-161: `grantInfiniteLives` rellena el contador al conceder, así que el mínimo arrastraba
        // la reserva del móvil donde el jugador SÍ gastaba. Mientras el pack dura no se nota; el día
        // que expira aparecía a 1 — y es alguien que acaba de pagar.
        val conPack = CloudSaveData(lives = 5, infiniteLivesUntilMs = 10_000L)
        val gastado = CloudSaveData(lives = 1, livesNextRegenAtMs = 7_000L)

        val vigente = gastado.mergedWith(conPack, nowMs = 5_000L)
        assertEquals(5, vigente.lives)
        assertEquals(0L, vigente.livesNextRegenAtMs)   // la reserva llena no tiene temporizador

        // Expirado el pack, vuelve la regla anti-abuso.
        val caducado = gastado.mergedWith(conPack, nowMs = 20_000L)
        assertEquals(1, caducado.lives)
        assertEquals(7_000L, caducado.livesNextRegenAtMs)
    }
}
