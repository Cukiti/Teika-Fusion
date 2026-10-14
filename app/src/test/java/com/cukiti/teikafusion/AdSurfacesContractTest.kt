package com.cukiti.teikafusion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * CONTRATO DE LOS ANUNCIOS (bloque 17, 2026-07-31) — cuarto censo por fuente, tras los de eventos,
 * audio y preferencias.
 *
 * AdMob no se puede instanciar en la JVM, así que lo que se fija aquí son las tres reglas que la
 * auditoría encontró rotas y que ningún compilador vigila:
 *
 * 1. **Ningún id de bloque de anuncios vive en el código.** El banner tenía el id de PRUEBA de
 *    Google incrustado en `BannerAd.kt`, así que la versión publicada mostraba "Test Ad" y ese hueco
 *    no ingresaba nada. Los ids van en `res/values/admob.xml` **por buildType** — es lo que impide
 *    pulsar tus propios anuncios reales en desarrollo (tráfico inválido = cuenta suspendida).
 * 2. **Los dos source sets declaran los MISMOS nombres de recurso.** Añadir un id a `debug` y
 *    olvidarlo en `release` rompe la compilación de release… o peor, al revés.
 * 3. **Toda superficie de publicidad pasa por el interruptor.** Quien compró «Quitar anuncios» no
 *    puede ver un banner (ni perder los 50 dp que ocupa): el archivo que monta `BannerAdView` tiene
 *    que consultar `adsOfferedFlow`.
 * 4. **«Quitar anuncios» NO apaga el recompensado** (2026-09-06). Son dos preguntas distintas:
 *    `adsOfferedFlow` para la publicidad FORZADA (banner, intersticial) y `rewardedOfferedFlow` para
 *    la VOLUNTARIA. Mientras fueron la misma, pagar por quitar los anuncios te quitaba de paso el
 *    vídeo que da 150 teikoins —hasta 1.500 al día—, o sea que la compra te cobraba por perder algo.
 */
class AdSurfacesContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, AD_MANAGER).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }

    private fun kotlinSources(): List<File> =
        File(repoRoot, MAIN_SRC).walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    @Test
    fun `ningun id de bloque de anuncios esta escrito en el codigo`() {
        // Formato de los ids de AdMob: ca-app-pub-XXXXXXXX/YYYYYYY (bloque) o ~YYYYYYY (app).
        val idPattern = Regex("""ca-app-pub-\d+[/~]\d+""")
        val offenders = kotlinSources()
            .filter { idPattern.containsMatchIn(it.readText()) }
            .map { it.name }
        assertTrue(
            "Ids de AdMob incrustados en Kotlin (deben vivir en res/values/admob.xml): $offenders",
            offenders.isEmpty()
        )
    }

    @Test
    fun `debug y release declaran los mismos recursos de admob`() {
        val debug = adResourceNames("app/src/debug/res/values/admob.xml")
        val release = adResourceNames("app/src/release/res/values/admob.xml")
        assertTrue("No he podido leer los admob.xml", debug.isNotEmpty() && release.isNotEmpty())
        assertEquals("Los dos source sets deben declarar los mismos ids", debug, release)
        assertTrue("admob_app_id" in debug)
        assertTrue("admob_rewarded_unit_id" in debug)
        assertTrue("admob_banner_unit_id" in debug)
    }

    @Test
    fun `toda superficie de publicidad consulta el interruptor`() {
        val hosts = kotlinSources().filter {
            it.name != "BannerAd.kt" && it.readText().contains("BannerAdView(")
        }
        assertTrue("Nadie monta el banner: el test no está mirando donde debe", hosts.isNotEmpty())
        val ungated = hosts.filterNot { it.readText().contains("adsOfferedFlow") }.map { it.name }
        assertTrue(
            "Superficies de banner que NO miran AdManager.adsOfferedFlow: $ungated",
            ungated.isEmpty()
        )
    }

    @Test
    fun `quitar anuncios no apaga el recompensado`() {
        val adManager = File(repoRoot, AD_MANAGER).readText()

        // Los dos interruptores existen y NO son el mismo: el voluntario ignora la compra.
        assertTrue(
            "Falta el interruptor de la publicidad forzada (banner + intersticial)",
            adManager.contains("private val forcedAdsEnabled: Boolean get() = remoteAdsEnabled && !adsRemoved")
        )
        assertTrue(
            "El recompensado no puede mirar `adsRemoved`: es voluntario y sobrevive a la compra",
            adManager.contains("private val rewardedEnabled: Boolean get() = remoteAdsEnabled")
        )

        // Al comprar, `setAdsRemoved` solo puede soltar el intersticial. Si vuelve a soltar el
        // vídeo recompensado, quien paga se queda otra vez sin su forma gratuita de ganar teikoins.
        val setAdsRemoved = adManager.substringAfter("fun setAdsRemoved(").take(600)
        assertTrue(
            "setAdsRemoved vuelve a soltar el recompensado: eso castiga al que paga",
            !setAdsRemoved.contains("rewardedAd = null")
        )

        // Y la UI que OFRECE un recompensado pregunta por el flow correcto.
        val gameScreen = File(repoRoot, MAIN_SRC + "ui/GameScreen.kt").readText()
        assertTrue(
            "La oferta de revivir debe mirar rewardedOfferedFlow, no adsOfferedFlow",
            gameScreen.contains("adLoading = !adReady && hasInternet && rewardedOffered")
        )
        assertTrue(
            "Doblar monedas debe mirar rewardedOfferedFlow, no adsOfferedFlow",
            gameScreen.contains("doubleCoins = if (rewardedOffered && adReadyForDouble)")
        )
    }

    @Test
    fun `los tutoriales terminan antes de solicitar o mostrar anuncios`() {
        val gameScreen = File(repoRoot, MAIN_SRC + "ui/GameScreen.kt").readText()
        val mainActivity = File(repoRoot, MAIN_SRC + "MainActivity.kt").readText()
        val application = File(repoRoot, MAIN_SRC + "TeikaFusionApp.kt").readText()
        val viewModel = File(repoRoot, MAIN_SRC + "viewmodel/GameViewModel.kt").readText()

        assertTrue(gameScreen.contains("!com.cukiti.teikafusion.model.TutorialCampaign.isTutorialLevel(gameState.level)"))
        assertTrue(gameScreen.contains("bannerVisible = adsOffered && canRequestAds"))
        assertTrue(mainActivity.contains("legalAccepted && !tutorialPending && !backupPrompt"))
        assertTrue(mainActivity.contains("ConsentManager.gather"))
        assertTrue(mainActivity.contains("AdManager.init(applicationContext)"))
        assertTrue(!application.contains("AdManager.init(this)"))
        assertTrue(
            "GameViewModel no debe precargar anuncios antes de UMP",
            !viewModel.contains("com.cukiti.teikafusion.AdManager.loadAd(appContext)")
        )
    }

    private fun adResourceNames(relative: String): Set<String> {
        val file = File(repoRoot, relative)
        if (!file.isFile) return emptySet()
        return Regex("""<string name="([^"]+)"""").findAll(file.readText())
            .map { it.groupValues[1] }
            .toSet()
    }

    private companion object {
        const val MAIN_SRC = "app/src/main/java/com/cukiti/teikafusion/"
        const val AD_MANAGER = MAIN_SRC + "AdManager.kt"
    }
}
