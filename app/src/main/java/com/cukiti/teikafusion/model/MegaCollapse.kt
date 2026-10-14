package com.cukiti.teikafusion.model

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

/**
 * **EL COLAPSO ESTELAR** (2026-09-24): la ceremonia ÚNICA de la fusión T4+T4 → MEGA.
 *
 * Hasta hoy los T4 no tenían ceremonia —T1…T3 sí (`GameEvent.FusionCharge`)— y el MEGA aparecía en
 * el mismo frame en que se tocaban: la jugada más difícil del juego era la ÚNICA que no se veía
 * fundirse. Ahora dura [CHARGE_MS] y tiene tres actos sobre un único reloj `k` (0..1):
 *
 * 1. **Vacío** (0 → [GATHER_END]): el tablero se apaga y empieza a SUCCIONAR partículas.
 * 2. **Espiral** ([GATHER_END] → [IMPLODE_AT]): los dos T4 orbitan uno alrededor del otro cada vez
 *    más cerca y más rápido, y el centro de la pareja DERIVA al centro del tablero, que es de donde
 *    irrumpe el MEGA ([MegaFling]): así la singularidad y el nacimiento ocurren en el mismo punto.
 * 3. **Implosión** ([IMPLODE_AT] → 1): los cuerpos se comprimen a un punto que arde en blanco; al
 *    cerrar el reloj el ViewModel ejecuta la fusión real y el MEGA estalla.
 *
 * Funciones puras (sin Compose) para testearlas en `MegaCollapseTest`; el VFX y la capa de cuerpos
 * solo las evalúan por frame, sin asignar.
 */
object MegaCollapse {

    /** Lo que dura la ceremonia. Casa con el pico del SFX `snd_mega_collapse` (~1,0 s). */
    const val CHARGE_MS = 1100

    /** Fin del acto 1: el vacío ya se ha cerrado sobre el tablero. */
    const val GATHER_END = 0.22f

    /** Inicio del acto 3: a partir de aquí los cuerpos se comprimen. */
    const val IMPLODE_AT = 0.84f

    /** Vueltas completas que da la pareja en toda la ceremonia. */
    const val TURNS = 1.4f

    /** Oscuridad máxima del vacío (el resto del tablero se sigue leyendo al fondo). */
    const val SCRIM_MAX = 0.78f

    /** Número de motas succionadas: fijo para que el bucle de dibujo no asigne nada. */
    const val MOTES = 44

    /** Rayos de las grietas de luz que abre la singularidad. */
    const val CRACKS = 9

    private fun clamp01(x: Float) = x.coerceIn(0f, 1f)

    /** Smoothstep clásico, 0..1 → 0..1. */
    fun smooth(x: Float): Float {
        val t = clamp01(x)
        return t * t * (3f - 2f * t)
    }

    /**
     * Radio de la órbita (misma unidad que [r0]). Arranca en [r0] y se cierra ACELERANDO: casi no se
     * mueve durante el vacío y se desploma al final, que es como se lee una caída gravitatoria.
     */
    fun orbitRadius(k: Float, r0: Float): Float = r0 * (1f - clamp01(k).pow(1.8f))

    /** Ángulo de la órbita (radianes) sumado al de salida [a0]; la velocidad angular crece con k. */
    fun orbitAngle(k: Float, a0: Float): Float =
        a0 + TURNS * 2f * PI.toFloat() * clamp01(k).pow(2.2f)

    /** Centro de la espiral: deriva de [start] (punto de contacto) a [board] (centro del tablero). */
    fun center(k: Float, start: Float, board: Float): Float = start + (board - start) * smooth(k)

    /**
     * Escala del cuerpo sobre su tamaño normal: respira hinchándose en la espiral y se COMPRIME a
     * casi nada en la implosión.
     */
    fun bodyScale(k: Float): Float {
        val t = clamp01(k)
        return if (t < IMPLODE_AT) {
            1f + 0.10f * sin(PI.toFloat() * t / IMPLODE_AT)
        } else {
            val x = (t - IMPLODE_AT) / (1f - IMPLODE_AT)
            1f - 0.85f * x * x
        }
    }

    /** Alpha del cuerpo: entero hasta el último tramo de la implosión, invisible al cerrar. */
    fun bodyAlpha(k: Float): Float = 1f - smooth((k - 0.90f) / 0.10f)

    /** Giro propio del cuerpo (grados): arrastrado por la órbita, cada vez más rápido. */
    fun bodySpinDeg(k: Float): Float = 420f * clamp01(k).pow(2.4f)

    /** Oscuridad del vacío: entra en el acto 1, se sostiene y cede al final al fogonazo del MEGA. */
    fun scrim(k: Float): Float {
        val t = clamp01(k)
        val inEnv = smooth(t / GATHER_END)
        val outEnv = 1f - smooth((t - 0.94f) / 0.06f)
        return SCRIM_MAX * inEnv * outEnv
    }

    /**
     * Progreso radial (0 = en su sitio, 1 = tragada) de la mota [i]: cada una arranca con un
     * retraso distinto y cae acelerando. Devuelve 1 cuando ya ha llegado.
     */
    fun moteProgress(k: Float, i: Int): Float {
        // start ≤ 0.50 y dur ≤ 0.46: toda mota llega antes de k = 0.96, con la implosión encima.
        val start = (i * 0.618034f % 1f) * 0.50f
        val dur = 0.30f + ((i * 0.381966f) % 1f) * 0.16f
        val x = clamp01((k - start) / dur)
        return x * x
    }

    /** Intensidad del disco de acreción que rodea a la pareja (sube en la espiral, muere al cerrar). */
    fun accretion(k: Float): Float {
        val t = clamp01(k)
        return smooth((t - 0.10f) / 0.30f) * (1f - smooth((t - 0.93f) / 0.07f))
    }

    /** Grietas de luz: aparecen en la segunda mitad de la espiral y se vuelven cegadoras al cerrar. */
    fun cracks(k: Float): Float = smooth((k - 0.50f) / 0.40f)

    /** La singularidad: punto blanco que nace con las grietas y FLORECE en la implosión. */
    fun singularity(k: Float): Float {
        val t = clamp01(k)
        return 0.35f * smooth((t - 0.45f) / 0.35f) + 0.65f * smooth((t - IMPLODE_AT) / (1f - IMPLODE_AT))
    }
}
