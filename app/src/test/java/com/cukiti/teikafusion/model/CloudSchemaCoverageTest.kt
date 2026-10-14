package com.cukiti.teikafusion.model

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **¿SE QUEDA ALGO DEL JUGADOR FUERA DE LA NUBE?** (P-22, bloque 25, 2026-07-31).
 *
 * El esquema de nube se queda atrás **en silencio**: añades un dato del jugador, lo persistes en
 * DataStore, todo funciona, sincroniza… y el fallo solo aparece el día que alguien cambia de móvil.
 * Así se descubrió que el **récord de profundidad del Infinito** volvía a 0 al reinstalar mientras
 * esa misma marca seguía publicada en el ranking.
 *
 * Esta prueba cruza los `save*` de `PreferencesManager` con los campos de `CloudSaveData` y falla si
 * aparece uno nuevo que nadie ha clasificado. La clasificación es explícita a propósito: **decidir
 * que algo NO viaja es una decisión legítima** (los ajustes son del dispositivo, no de la cuenta),
 * pero tiene que estar escrita, no olvidada.
 */
class CloudSchemaCoverageTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, PREFS).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    /**
     * Cosas que se guardan en el dispositivo y **no deben** viajar a la cuenta, con su motivo.
     * Si añades una entrada aquí, di por qué en una línea.
     */
    private val localOnly = mapOf(
        // Ajustes: son del APARATO. Un móvil en silencio no tiene por qué silenciar el otro.
        "saveMusicVolume" to "ajuste del dispositivo",
        "saveSfxVolume" to "ajuste del dispositivo",
        "saveMute" to "ajuste del dispositivo",
        "saveVibrationEnabled" to "ajuste del dispositivo",
        "saveTiltEnabled" to "ajuste del dispositivo",
        "saveReducedFx" to "ajuste del dispositivo (y puede venir del sistema)",
        // Avisos de reenganche (P-166): es del APARATO, como el resto de ajustes — y además su
        // primera puerta, el permiso del sistema, ya es por dispositivo y no viaja a ningún sitio.
        "saveNotificationsEnabled" to "ajuste del dispositivo",
        // (`saveGameState` ya NO es local: viaja desde el esquema v4 — ver `aliases`.)
        "saveInfiniteDifficulty" to "elección de la última partida, local",
        // Anti-abuso / ritmo: viajar los haría reseteables cambiando de aparato.
        "saveAdQuota" to "cupo diario de anuncios: local a propósito",
        // (Las VIDAS y su perk de infinitas dejaron de ser locales en el esquema v4 — ver `aliases`.
        // Eran justo lo contrario de anti-abuso: reinstalar devolvía la reserva llena.)
        // Onboarding y avisos: son de esta instalación.
        "saveLegalConsent" to "aceptación de documentos: local a la instalación",
        "saveAccountChoiceMade" to "onboarding de cuenta",
        "savePendingCloudConflictPrompt" to "aviso pendiente de esta instalación",
        // P-210 · LA OFERTA del paquete inicial ya se enseñó. Es local a propósito, y la distinción
        // con `starterPackGranted` —que SÍ viaja— es todo el asunto: aquella dice «este jugador TIENE
        // el pack» y esta dice «a esta instalación ya se le enseñó la ventana». Si viajara, alguien
        // que cambia de móvil antes de cerrar el capítulo 1 se quedaría sin ver nunca la única
        // oferta que hay.
        "saveStarterPackOffered" to "oferta ya enseñada en este aparato",
        // P-227 (2026-09-07b): el puesto del ranking NO es un dato del jugador, es una FOTO del
        // servidor —el servidor ya lo sabe y lo recalcula—. Se guarda solo para que el carné del
        // perfil pueda enseñarlo sin pedirle a Firestore una consulta por cada visita a Inicio, y se
        // reescribe cada vez que el jugador abre el ranking. Viajar a la nube no le añadiría nada:
        // en el móvil nuevo se rellena solo en cuanto mire la tabla.
        "saveLastKnownRank" to "foto del ranking: la reescribe el servidor en cuanto se mira",
        // P-449: «ya sabe girar fichas». Aprendizaje de ESTA instalación, como las cápsulas.
        "saveRotateLearned" to "pista del giro ya aprendida en este aparato",
        // LAS CAPSULAS (2026-09-01): que mecanicas se le han explicado ya. Misma familia que el
        // aviso de arriba — es aprendizaje de ESTA instalacion, no un dato de orgullo. Si algun dia
        // molesta que un movil nuevo vuelva a explicar la gravedad, sube a CloudSaveData como union
        // de conjuntos (nunca como sustitucion: perderia lo aprendido en el otro aparato).
        "saveMechanicSeen" to "mecanicas ya explicadas en este aparato",
        "saveAbyssCardRevealSeen" to "destello de estreno visto en este aparato",
        // P-369: «Reducir efectos» ya se activó solo porque ESTE aparato iba a tirones. Es del
        // hardware: otro móvil con la misma cuenta puede ir perfectamente.
        "saveAutoReducedFxDone" to "rendimiento de este aparato",
        // Migración local: marca que a ESTA instalación ya se le repartieron las cargas de
        // bienvenida. Las cargas SÍ viajan (companionCharges); la bandera no, porque si viajara,
        // un dispositivo nuevo se saltaría su propia siembra y arrancaría sin usos.
        "saveCompanionChargesSeeded" to "migración local de cargas de bienvenida",
        // Misma razón que la de arriba: marca que a ESTA instalación ya se le sembró un cinturón de
        // arranque. El cinturón SÍ viaja (companionBelt); la bandera no, porque si viajara, un
        // dispositivo nuevo se saltaría su propia siembra y entraría con la hoja de habilidades
        // vacía sin haberlo decidido el jugador.
        "saveCompanionBeltSeeded" to "migración local del cinturón de arranque",
        "saveNotificationsScheduled" to "programación local de notificaciones",
        "savePlayerId" to "identidad del dispositivo",
        "saveSelectedLevel" to "cursor del mapa: comodidad local"
    )

    /** Campos de `CloudSaveData` que cubren un `save*` con otro nombre. */
    private val aliases = mapOf(
        "saveMaxLevel" to "maxLevel",
        "saveTeikoins" to "teikoins",
        "saveHighScore" to "highScore",
        "saveMaxTeikas" to "maxTeikas",
        "saveBestCombo" to "bestCombo",
        "saveLevelStars" to "levelStars",
        "saveLevelStarsAll" to "levelStars",
        "saveMuralRewardsAll" to "muralRewards",
        "saveCrystalAwardedAll" to "crystalAwardedLevels",
        "saveStreakState" to "dailyStreak",
        "saveTutorialDone" to "tutorialDone",
        "saveDisplayName" to "displayName",
        "saveAvatar" to "avatarType",
        "saveActiveCosmetic" to "activeCosmetic",
        "saveUnlockedCosmetics" to "unlockedCosmetics",
        "saveSelectedCompanion" to "selectedCompanion",
        "saveCrystalInventory" to "crystalInventory",
        "saveCompanionUpgrades" to "companionUpgrades",
        "saveTeikaAbilityLoadout" to "teikaAbilityLoadout",
        "saveAdsRemoved" to "adsRemoved",
        // schema v4 (2026-09-04): las vidas y el intento en curso pasan a ser de la CUENTA.
        "saveLives" to "lives",
        "saveInfiniteLivesUntil" to "infiniteLivesUntilMs",
        "saveGameState" to "gameStateJson",
        "claimCharacter" to "claimedCharacters",
        "addXp" to "totalXp",
        "markCrystalAwarded" to "crystalAwardedLevels",
        "markMuralRewarded" to "muralRewards"
    )

    @Test
    fun `todo lo que se persiste esta clasificado, viaja a la nube o es local a proposito`() {
        val prefsSource = File(repoRoot, PREFS).readText()
        val cloudSource = File(repoRoot, CLOUD).readText()

        val savers = Regex("""suspend fun (save\w+|claimCharacter|addXp|mark\w+)\(""")
            .findAll(prefsSource)
            .map { it.groupValues[1] }
            .distinct()
            .toList()
        assertTrue("Solo he encontrado ${savers.size} `save*`: el parseo está roto", savers.size >= 20)

        val unclassified = savers.filter { saver ->
            if (saver in localOnly) return@filter false
            val field = aliases[saver] ?: saver.removePrefix("save").replaceFirstChar { it.lowercase() }
            !Regex("""val $field\b""").containsMatchIn(cloudSource)
        }
        assertTrue(
            "Progreso que NO viaja a la nube y NADIE ha clasificado: $unclassified — o se añade a " +
                "CloudSaveData (campo + mergedWith + applyCloudData) o se declara local con su motivo",
            unclassified.isEmpty()
        )
    }

    @Test
    fun `todo campo de la nube se fusiona`() {
        // Un campo que se guarda pero no se fusiona es peor que no tenerlo: al sincronizar desde el
        // dispositivo con MENOS progreso, pisa el bueno.
        val cloudSource = File(repoRoot, CLOUD).readText()
        val fields = Regex("""^\s{4}val (\w+):""", RegexOption.MULTILINE)
            .findAll(cloudSource.substringBefore("fun toJson"))
            .map { it.groupValues[1] }
            .filterNot { it == "schemaVersion" }
            .toList()
        assertTrue("Parseo de campos roto (${fields.size})", fields.size >= 15)
        val mergeBlock = cloudSource.substringAfter("fun mergedWith").substringBefore("companion object")
        val notMerged = fields.filterNot { Regex("""\b$it\s*=""").containsMatchIn(mergeBlock) }
        assertTrue("Campos de CloudSaveData que `mergedWith` ignora: $notMerged", notMerged.isEmpty())
    }

    /**
     * **Y TAMBIÉN LA BAJADA** (P-164, 2026-09-04).
     *
     * Las dos pruebas de arriba vigilan la SUBIDA: que el dato tenga campo y que la fusión lo
     * respete. Ahí se acababa el contrato, y esa es justo la mitad menos útil: un campo que viaja,
     * se fusiona y **nadie escribe al restaurar** es indistinguible de no tenerlo, y falla igual —el
     * día que alguien cambia de móvil, que es el único día en que esto se prueba de verdad—.
     *
     * Salió de implementar el esquema v4: las vidas y el tablero había que acordarse de aplicarlos
     * a mano, sin nada que avisara si se olvidaban.
     */
    @Test
    fun `todo campo de la nube se APLICA al restaurar`() {
        val cloudSource = File(repoRoot, CLOUD).readText()
        val vmSource = File(repoRoot, VIEWMODEL).readText()

        val fields = Regex("""^\s{4}val (\w+):""", RegexOption.MULTILINE)
            .findAll(cloudSource.substringBefore("fun toJson"))
            .map { it.groupValues[1] }
            .toList()
        assertTrue("Parseo de campos roto (${fields.size})", fields.size >= 15)

        val notApplied = fields
            .filterNot { it in applyExceptions }
            .filterNot { vmSource.contains("merged.$it") }
        assertTrue(
            "Campos de CloudSaveData que la restauración NO aplica: $notApplied — o se leen como " +
                "`merged.<campo>` en applyCloudData (o en lo que llame desde ahí), o se declaran " +
                "excepción con su motivo en `applyExceptions`",
            notApplied.isEmpty()
        )
    }

    /** Campos que NO se aplican al restaurar, con su motivo. */
    private val applyExceptions = mapOf(
        "schemaVersion" to "llega aparte, como parámetro `cloudSchema` de applyCloudData",
        "updatedAtEpochMs" to "metadato de la sincronización: no es progreso del jugador"
    )

    private companion object {
        const val PREFS = "app/src/main/java/com/cukiti/teikafusion/model/PreferencesManager.kt"
        const val CLOUD = "app/src/main/java/com/cukiti/teikafusion/model/CloudSaveData.kt"
        const val VIEWMODEL = "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt"
    }
}
