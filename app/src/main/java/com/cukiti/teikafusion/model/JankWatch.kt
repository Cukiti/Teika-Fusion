package com.cukiti.teikafusion.model

/**
 * **P-369 · «Reducir efectos» automático si hay tirones SOSTENIDOS** (2026-09-30, decisión del usuario).
 *
 * Recibe la duración de cada fotograma de la partida y dice cuándo el juego lleva un rato claramente
 * por debajo de lo jugable. Es la regla pura (sin Android) para poder probarla; quien la alimenta es
 * `JankWatchDriver` y quien actúa, `GameViewModel.onSustainedJank`.
 *
 * - Ventanas de [WINDOW_NS] (1 s). Una ventana es MALA si su media baja de [BAD_FPS] fps.
 * - Salta tras [BAD_WINDOWS] ventanas malas SEGUIDAS (6 s): un pico suelto —una rotura gorda, una
 *   cascada— no basta; tiene que ser el juego entero yendo a tirones.
 * - Las primeras [WARMUP_WINDOWS] ventanas no cuentan: al entrar al nivel se cargan recursos y el
 *   JIT compila, y eso no dice nada del aparato.
 * - Un hueco de más de [GAP_NS] entre fotogramas (volver de segundo plano, un diálogo del sistema)
 *   no es un tirón del juego: reinicia la ventana en curso sin contarla.
 */
class JankWatch {
    private var accumNs = 0L
    private var frames = 0
    private var windows = 0
    private var badStreak = 0
    private var fired = false

    /** Añade un fotograma de [frameNs]. Devuelve `true` UNA vez, cuando los tirones son sostenidos. */
    fun onFrame(frameNs: Long): Boolean {
        if (fired || frameNs <= 0L) return false
        if (frameNs > GAP_NS) {
            accumNs = 0L; frames = 0
            return false
        }
        accumNs += frameNs
        frames++
        if (accumNs < WINDOW_NS) return false
        val fps = frames * 1_000_000_000.0 / accumNs
        accumNs = 0L; frames = 0
        windows++
        if (windows <= WARMUP_WINDOWS) return false
        badStreak = if (fps < BAD_FPS) badStreak + 1 else 0
        if (badStreak >= BAD_WINDOWS) {
            fired = true
            return true
        }
        return false
    }

    companion object {
        const val WINDOW_NS = 1_000_000_000L
        const val BAD_FPS = 40.0
        const val BAD_WINDOWS = 6
        const val WARMUP_WINDOWS = 3
        const val GAP_NS = 250_000_000L
    }
}
