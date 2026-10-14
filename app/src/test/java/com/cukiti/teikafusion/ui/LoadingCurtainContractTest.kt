package com.cukiti.teikafusion.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **NADA SUENA NI SE CELEBRA DETRÁS DE UNA CORTINA DE CARGA** (2026-09-04, pedido del usuario).
 *
 * El usuario desinstaló el juego, lo volvió a instalar y lo primero que oyó fue esto: *«en la
 * pantalla de carga, mientras carga la barra, se escuchan efectos de sonido de recompensa o de logro
 * y no se está viendo la ventana que hace el sonido; es muy poco profesional. También pasa cuando un
 * jugador carga su cuenta»*.
 *
 * La causa no era un cue suelto: el **arranque del ViewModel** hace medio juego —calcula la racha
 * diaria, otorga sus monedas, suma el XP del día (que puede subir de rango), fusiona el progreso de
 * la nube y sincroniza murales pendientes— y todo eso ocurre **con el splash puesto**. Sus ventanas
 * ya estaban gateadas por `!inSplash`; sus sonidos no. Y peor: `SoundManager` **encola** los
 * disparos que llegan antes de que la muestra termine de cargar, así que además salían tarde y
 * amontonados, justo mientras la barra iba por la mitad.
 *
 * La regla que se fija aquí es la del usuario, literal: **si no se ve la ventana que hace el sonido,
 * el sonido no suena**. Y su corolario, que es igual de importante y viene de los MOMENTOS MUDOS: lo
 * que SÍ tiene ventana no se pierde — se dispara desde su ventana.
 *
 * Es de fuente porque `SoundManager` no se puede instanciar en la JVM (SoundPool es de Android) y
 * porque el resto son relaciones de Compose, sin una función pura que interrogar.
 */
class LoadingCurtainContractTest {

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, SOUND_MANAGER).isFile) dir = dir.parentFile
        requireNotNull(dir) { "No encuentro la raíz del repo" }
    }

    private fun read(path: String) = File(repoRoot, path).readText()

    @Test
    fun `la puerta del audio se cierra en el unico sitio por el que sale un SFX`() {
        val sm = read(SOUND_MANAGER)
        assertTrue(sm.contains("var loadingCurtainUp: Boolean = false"))
        // En `play()` y ANTES de encolar en `pendingPlays`: si se colara la cola, el disparo
        // sobreviviría a la cortina y se soltaría al terminar de cargar la muestra — que es
        // exactamente el síntoma reportado.
        val guard = sm.indexOf("if (released || loadingCurtainUp")
        val queue = sm.indexOf("pendingPlays.computeIfAbsent", guard)
        assertTrue("la guarda no está en play()", guard > 0)
        assertTrue("la guarda debe preceder a la cola de pendientes", guard < queue)
    }

    @Test
    fun `solo la propia intro atraviesa la cortina`() {
        // P-372/P-374 (2026-09-30): la firma del estudio y el «Toca para jugar» suenan con la cortina
        // puesta porque SU ventana es la intro, que se está viendo — es la misma regla, no una
        // excepción. Nada más puede usar la puerta: si otro cue la cruza, vuelve el síntoma de arriba.
        val sm = read(SOUND_MANAGER)
        val cruces = Regex("bypassCurtain = true").findAll(sm).map { m ->
            sm.lastIndexOf("fun ", m.range.first).let { sm.substring(it, sm.indexOf('(', it)) }
        }.toList()
        assertTrue(
            "solo playIntroSting/playIntroStart pueden cruzar la cortina: $cruces",
            cruces.sorted() == listOf("fun playIntroStart", "fun playIntroSting")
        )
        // Y lo que se encola con la cortina puesta solo sale si era de la intro.
        assertTrue(sm.contains("(!loadingCurtainUp || it.bypassCurtain)"))
    }

    @Test
    fun `la cortina cubre el splash y tambien la carga de cuenta`() {
        val nav = read(APP_NAV)
        // «También pasa cuando un jugador carga su cuenta»: la fusión con la nube otorga monedas,
        // desbloquea niveles y sube el XP con el overlay de «Cargando tu cuenta» puesto.
        assertTrue(
            nav.contains("val loadingCurtain = inSplash || accountSyncing || restartRequested")
        )
        assertTrue(nav.contains("viewModel.soundManager.loadingCurtainUp = loadingCurtain"))
        // Y el banner de racha tampoco puede consumir sus 3,5 s tapado por el splash.
        assertTrue(nav.contains("dailyStreakCelebration.takeIf { !loadingCurtain }"))
    }

    @Test
    fun `lo que tiene ventana suena desde su ventana`() {
        val vm = read(VIEW_MODEL)
        val nav = read(APP_NAV)
        // RANGO: el cue vivía en `awardXp`, que corre en el arranque (XP del primer juego del día).
        assertFalse("playRankUp no puede dispararse desde el cálculo", vm.contains("soundManager.playRankUp()"))
        val rank = nav.indexOf("rankUpCelebration?.let { rank ->")
        val rankCue = nav.indexOf("playRankUp()", rank)
        val rankEnd = nav.indexOf("RankUpOverlay(", rank)
        assertTrue("el cue de rango debe vivir en su overlay", rankCue in 1 until rankEnd)

        // RACHA: se calcula en el arranque y se celebra en HOME, con dos caras (banner de «Día N» y
        // cofre de los días de hito). El arranque la apunta; la UI la cobra cuando se ve.
        assertFalse("playStreak no puede dispararse desde el cálculo", vm.contains("soundManager.playStreak()"))
        assertTrue(vm.contains("val pendingStreakFanfare: StateFlow<Boolean>"))
        assertTrue(nav.contains("viewModel.soundManager.playStreak()"))
        assertTrue(nav.contains("viewModel.consumeStreakFanfare()"))
    }

    @Test
    fun `las monedas que se conceden sin ventana no hacen ruido`() {
        val vm = read(VIEW_MODEL)
        // El chime de teikoins es el sonido de recompensa que se oía sobre la barra de carga: lo
        // disparaban la racha del arranque y el barrido de murales pendientes (restaurar de nube),
        // que no enseñan nada. Las concesiones CON ventana —premio de nivel, mural completo, anuncio
        // visto— siguen sonando, así que el parámetro tiene que seguir siendo opt-in.
        assertTrue(vm.contains("fun grantTeikoins(amount: Int, silent: Boolean = false)"))
        assertTrue(vm.contains("if (amount > 0 && !silent) soundManager.playTeikoinGain()"))
        assertTrue(vm.contains("grantTeikoins(streakCoins, silent = true)"))
        assertTrue(
            vm.contains("grantTeikoins(MURAL_REWARD_BASE + i * MURAL_REWARD_PER_CHAPTER, silent = true)")
        )
    }

    @Test
    fun `la moneda del juego se dibuja, nunca se tipografia`() {
        // Lo que el usuario reportó del Inventario: «el icono de la moneda no se está viendo». 🪙 es
        // de Unicode 13 (2020) y lo dibuja la fuente de emoji del sistema — donde no está, no sale
        // nada y el chip queda como un número suelto. La barra de partida y la tienda ya usaban el
        // arte real; esto extiende ese criterio a toda la app y lo congela.
        val shared = read(SHARED_UI)
        assertTrue(shared.contains("fun TeikoinIcon("))
        assertTrue(shared.contains("fun TeikoinAmount("))
        assertTrue(shared.contains("R.raw.teikoin"))

        val coin = "🪙"   // 🪙
        val inString = Regex("\"[^\"\\n]*$coin")
        File(repoRoot, MAIN_SRC).walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { f ->
                assertFalse(
                    "${f.name} vuelve a pintar la moneda con el emoji: usa TeikoinIcon/TeikoinAmount",
                    inString.containsMatchIn(f.readText())
                )
            }
    }

    private companion object {
        const val SOUND_MANAGER = "app/src/main/java/com/cukiti/teikafusion/ui/SoundManager.kt"
        const val APP_NAV = "app/src/main/java/com/cukiti/teikafusion/ui/AppNavigation.kt"
        const val SHARED_UI = "app/src/main/java/com/cukiti/teikafusion/ui/SharedUi.kt"
        const val VIEW_MODEL = "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt"
        const val MAIN_SRC = "app/src/main/java/com/cukiti/teikafusion"
    }
}
