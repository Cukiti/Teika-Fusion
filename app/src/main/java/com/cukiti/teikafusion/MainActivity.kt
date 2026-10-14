package com.cukiti.teikafusion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.cukiti.teikafusion.model.PreferencesManager
import com.cukiti.teikafusion.ui.AppNavigation
import com.cukiti.teikafusion.ui.SoundManager
import com.cukiti.teikafusion.ui.theme.TeikaFusionTheme
import com.cukiti.teikafusion.viewmodel.GameViewModel

/**
 * Techo del escalado de fuente del sistema. 1,3 = el texto se ve claramente más grande sin que los
 * huecos de alto fijo (HUD, chips, bandeja) dejen de contener su rótulo. Si algún día el layout deja
 * de tener alturas fijas, este tope debería subir o desaparecer.
 */
private const val MAX_FONT_SCALE = 1.3f

class MainActivity : ComponentActivity() {

    private val viewModel: GameViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val prefs = PreferencesManager(applicationContext)
                @Suppress("UNCHECKED_CAST")
                return GameViewModel(
                    prefs,
                    SoundManager(applicationContext),
                    applicationContext
                ) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.cukiti.teikafusion.ui.introLog("Activity.onCreate")
        // La música ya NO se arranca aquí: AppNavigation la dirige por contexto (navbar/menú/bioma).

        // El canal puede crearse sin interrumpir. El permiso de Android 13+ ya NO se pide al abrir la
        // app: debe solicitarse más adelante desde un contexto que explique su beneficio.
        NotificationHelper.ensureChannel(applicationContext)

        // Los botones físicos de volumen controlan el stream de MÚSICA (donde suenan BGM y SFX del
        // juego). Sin esto controlan el stream de TIMBRE por defecto: el jugador "sube el volumen"
        // pero la música del juego sigue igual (de ahí el "no se escucha nada aunque suba el volumen").
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC

        enableEdgeToEdge()

        // Cuenta: si Firebase conserva una sesión de Google, baja/fusiona/sube el progreso
        // desde Firestore; si no, se juega en local sin crear ninguna identidad.
        // Idempotente vía el ViewModel: NO se relanza si la Activity se recrea (cambio de config
        // al volver de segundo plano), evitando que reaparezca "Cargando tu cuenta".
        viewModel.ensureInitialAccountCheck()
        com.cukiti.teikafusion.ui.introLog("ViewModel creado")

        // Hide system bars for immersive mode
        hideSystemBars()

        // UMP es independiente de aceptar Términos/Privacidad. Se inicia solo cuando terminó el
        // onboarding completo y no hay una invitación de respaldo encima, para no apilar ventanas.
        lifecycleScope.launch {
            combine(
                viewModel.legalConsentAccepted,
                viewModel.showTutorial,
                viewModel.showPostTutorialBackupPrompt,
                viewModel.gameState.map {
                    it.status != com.cukiti.teikafusion.model.GameStatus.SPLASH
                }
            ) { legalAccepted, tutorialPending, backupPrompt, splashFinished ->
                legalAccepted && !tutorialPending && !backupPrompt && splashFinished
            }.filter { it }.first()
            ConsentManager.gather(this@MainActivity) { canRequestAds ->
                // `rewardedOfferedFlow`, no `adsOfferedFlow`: quien compró «Quitar anuncios»
                // TAMBIÉN necesita inventario, porque su vídeo voluntario de teikoins sigue vivo
                // (2026-09-06). `loadAd` ceba los dos formatos y el intersticial se descarta solo
                // dentro si la publicidad forzada está apagada para este jugador.
                if (canRequestAds && AdManager.rewardedOfferedFlow.value) {
                    AdManager.init(applicationContext)
                    AdManager.loadAd(applicationContext)
                }
            }
        }

        com.cukiti.teikafusion.ui.introLog("setContent")
        setContent {
            // TOPE DE ESCALADO DE FUENTE (P-10, 2026-07-31). El juego es un tablero de rejilla con
            // HUD, chips y bandeja de ALTO FIJO: con la fuente del sistema al 200 % los rótulos
            // reventaban su hueco o se cortaban ("Privacid…"), y el tablero perdía filas contra la
            // bandeja. Se respeta el ajuste del jugador hasta [MAX_FONT_SCALE] —que ya es texto
            // claramente más grande— y a partir de ahí se topa, en vez de romper el layout.
            //
            // No sustituye a la accesibilidad de verdad: los textos críticos ENCOGEN antes de
            // cortarse (`TextAutoSize`, patrón de la it. 49) y TalkBack lee la pantalla entera.
            val density = androidx.compose.ui.platform.LocalDensity.current
            val capped = androidx.compose.ui.unit.Density(
                density = density.density,
                fontScale = density.fontScale.coerceAtMost(MAX_FONT_SCALE)
            )
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalDensity provides capped
            ) {
                TeikaFusionTheme {
                    AppNavigation(viewModel = viewModel)
                }
            }
        }
    }

    private fun hideSystemBars() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Al recuperar el foco (volver de 2º plano / cerrar diálogos del sistema) re-ocultar las barras
        // para que el modo inmersivo no se "rompa" y la pantalla no parezca reiniciarse.
        if (hasFocus) {
            hideSystemBars()
            // Reanuda la música al volver a 1º plano — pero NO si el silencio era deliberado
            // (partida en pausa). Ver resumeBgmIfAllowed.
            viewModel.resumeBgmIfAllowed()
            // Está jugando de nuevo → el aviso de "energía llena" ya no aplica.
            viewModel.cancelEnergyNotification()
        }
    }

    override fun onResume() {
        super.onResume()
        // P-381: mantiene despierta la salida de los SFX mientras la app está delante.
        viewModel.soundManager.setAppForeground(true)
        // Compras: reconecta si Play se cayó y recoge las hechas fuera del juego (o mientras tanto).
        BillingManager.refresh()
    }

    override fun onPause() {
        super.onPause()
        // Guarda la partida y pausa si el usuario sale de la app
        viewModel.autoSaveAndPause()
        viewModel.soundManager.pauseBgm()   // pausa la música al irse a 2º plano
        viewModel.soundManager.setAppForeground(false)   // P-381: fuera el despertador de audio
        // Programa notificaciones de reenganche (energía llena, reto diario, racha en peligro).
        viewModel.scheduleReengagementNotifications()
        // Respaldo best-effort a Firestore para usuarios de nube. Sin sesión, no-op.
        viewModel.pushCloudSave()
    }

    override fun onDestroy() {
        super.onDestroy()
        // soundManager lifecycle is owned by GameViewModel.onCleared()
    }

}
