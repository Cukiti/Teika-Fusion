package com.cukiti.teikafusion.viewmodel

import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.model.TeikaType
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * INCLINACIÓN CON TEIKAS APILADOS — regresión del bug reportado por el usuario (2026-07-25b):
 * *"cuando un Teika está encima de otro tienen como cortes de movimiento, como si tuvieran lag"*.
 *
 * Se reproduce una PILA (que es lo que hay en un tablero cargado, ver `art/nivelbug.jpeg`) con el
 * teléfono ladeado, y se mide el desplazamiento POR FRAME. Las dos averías se comprueban aparte:
 *
 *  · **que se mueva** — la fricción de impacto se aplicaba en las 3 pasadas del solver × 2 substeps
 *    (0,94⁶ ≈ 0,69 por frame), lo que dejaba la velocidad terminal en ~0,3 celdas/s;
 *  · **que se mueva SIN saltos** — la ayuda al desatasco era un impulso seco de 3,2 celdas/s cada
 *    150 ms, y el desempate de cima se encendía y apagaba con un umbral binario de |vx|. Las dos
 *    cosas metían discontinuidades en la velocidad: exactamente los "cortes" reportados.
 *
 * OJO al escenario: una torre de dos Teikas IGUALES **no es estable a propósito** (el anti-encaramado
 * la derriba, ver `APEX_SLIP_*`), así que no sirve para medir. Lo que se monta aquí es una pila
 * apoyada de verdad, y lo que se mide es el conjunto.
 *
 * El motor es Kotlin puro, así que esto corre en la JVM sin dispositivo.
 */
class TeikaTiltPhysicsTest {

    private val FRAME = 1f / 60f
    /** 25° de inclinación con la curva de respuesta real (ver TiltRules): ~9 celdas/s². */
    private val TILT_25 = 9f
    private val G = GameState.GRID_SIZE.toFloat()

    /** Suelo sólido: la fila de abajo del tablero, para que la pila tenga sobre qué apoyarse. */
    private fun floorRow(): Set<Position> =
        (0 until GameState.GRID_SIZE).map { Position(it, GameState.GRID_SIZE - 1) }.toSet()

    /**
     * Pila asentada: dos T1 juntos en el suelo y un tercero ENCIMA, en el valle entre ambos. Se deja
     * caer un rato sin inclinación para que los contactos sean reales y no impuestos por las
     * coordenadas iniciales.
     */
    private fun pileWorld(): TeikaPhysicsWorld {
        val w = TeikaPhysicsWorld()
        w.setTerrain(floorRow())
        val floorY = G - 1f - 0.5f
        w.addBody(TeikaType.TEIKA_1, x = 3.5f, y = floorY, id = "base-izq")
        w.addBody(TeikaType.TEIKA_1, x = 4.5f, y = floorY, id = "base-der")
        w.addBody(TeikaType.TEIKA_1, x = 4.0f, y = floorY - 1.0f, id = "encima")
        repeat(45) { w.step(FRAME, 0L) }
        return w
    }

    private fun xOf(w: TeikaPhysicsWorld, id: String): Float = w.snapshot().first { it.id == id }.x

    /** Centro de masas horizontal de la pila: métrica robusta a que un cuerpo ruede y otro no. */
    private fun centerX(w: TeikaPhysicsWorld): Float = w.snapshot().map { it.x }.average().toFloat()

    /** Desplazamiento horizontal de [id] en cada uno de [frames] frames consecutivos. */
    private fun perFrameDx(w: TeikaPhysicsWorld, id: String, frames: Int): List<Float> {
        val out = ArrayList<Float>(frames)
        var prev = xOf(w, id)
        repeat(frames) {
            w.step(FRAME, 0L)
            val now = xOf(w, id)
            out.add(now - prev)
            prev = now
        }
        return out
    }

    @Test
    fun `la pila se mueve de verdad al inclinar`() {
        val w = pileWorld()
        val c0 = centerX(w)
        w.setHorizontalGravity(-TILT_25)          // inclinar a la IZQUIERDA
        repeat(30) { w.step(FRAME, 0L) }          // medio segundo
        val moved = c0 - centerX(w)
        // Criterio del brief de inclinación: ≥0,3 celdas en ≤0,5 s. Con la fricción vieja (0,94⁶
        // por frame) la velocidad terminal era ~0,3 celdas/s y aquí salían ~0,1.
        assertTrue("la pila apenas se movió: $moved celdas en 0,5 s", moved >= 0.3f)
    }

    @Test
    fun `ningun cuerpo de la pila se mueve a saltos`() {
        val w = pileWorld()
        w.setHorizontalGravity(-TILT_25)
        repeat(20) { w.step(FRAME, 0L) }          // el arranque desde parado sí acelera marcado
        listOf("base-izq", "base-der", "encima").forEach { id ->
            val dx = perFrameDx(w, id, 60).map { kotlin.math.abs(it) }
            val media = dx.average().toFloat()
            if (media < 0.002f) return@forEach     // ese cuerpo está encajado: nada que medir
            val pico = dx.max()
            // El impulso seco de 3,2 celdas/s metía un frame de ~0,05 celdas entre frames de ~0,003.
            // Un movimiento continuo se queda muy por debajo de 4× la media.
            assertTrue("$id salta: pico=$pico media=$media (${pico / media}×)", pico <= media * 4f)
        }
    }

    @Test
    fun `la velocidad no cambia de signo sola bajo inclinacion constante`() {
        // Un tirón lateral se delata como un cambio brusco de velocidad. Con la inclinación fija a
        // un lado, ningún cuerpo de la pila debería ACELERAR hacia el otro lado varias veces.
        val w = pileWorld()
        w.setHorizontalGravity(-TILT_25)
        repeat(30) { w.step(FRAME, 0L) }
        val dx = perFrameDx(w, "base-izq", 90)
        val cambios = dx.zipWithNext().count { (a, b) ->
            kotlin.math.abs(a) > 1e-4f && kotlin.math.abs(b) > 1e-4f && (a < 0f) != (b < 0f)
        }
        assertTrue("la velocidad se invierte $cambios veces: hay tirones", cambios <= 2)
    }

    @Test
    fun `un T1 apoyado sobre un T2 tambien responde a la inclinacion`() {
        val w = TeikaPhysicsWorld()
        w.setTerrain(floorRow())
        w.addBody(TeikaType.TEIKA_2, x = 4f, y = G - 1f - 1f, id = "grande")
        w.addBody(TeikaType.TEIKA_1, x = 4f, y = G - 1f - 2.5f, id = "chico")
        repeat(45) { w.step(FRAME, 0L) }
        val x0 = xOf(w, "chico")
        w.setHorizontalGravity(-TILT_25)
        repeat(45) { w.step(FRAME, 0L) }
        assertTrue("el de encima no se movió: ${x0 - xOf(w, "chico")}", x0 - xOf(w, "chico") > 0.2f)
    }

    @Test
    fun `un cuerpo suelto en el suelo rueda al inclinar`() {
        val w = TeikaPhysicsWorld()
        w.setTerrain(floorRow())
        w.addBody(TeikaType.TEIKA_1, x = G / 2f, y = G - 1f - 0.5f, id = "solo")
        repeat(20) { w.step(FRAME, 0L) }
        val x0 = xOf(w, "solo")
        w.setHorizontalGravity(TILT_25)           // a la DERECHA
        repeat(30) { w.step(FRAME, 0L) }
        assertTrue("no rodó: ${xOf(w, "solo") - x0}", xOf(w, "solo") - x0 >= 0.3f)
    }

    @Test
    fun `la inclinacion mueve hacia el lado que toca`() {
        val w = pileWorld()
        val c0 = centerX(w)
        w.setHorizontalGravity(TILT_25)
        repeat(30) { w.step(FRAME, 0L) }
        assertTrue("se fue al lado contrario", centerX(w) > c0)
    }

    @Test
    fun `sin inclinacion la pila no deriva`() {
        val w = pileWorld()
        val c0 = centerX(w)
        repeat(150) { w.step(FRAME, 0L) }         // 2,5 s de mesa plana
        // El anti-encaramado puede deshacer la torre (es su función), pero el conjunto no puede
        // irse a ningún lado: sin inclinación no hay pendiente.
        assertTrue("deriva en plano: ${centerX(w) - c0}", kotlin.math.abs(centerX(w) - c0) < 0.6f)
    }

    @Test
    fun `el cuerpo se frena al soltar la inclinacion`() {
        val w = TeikaPhysicsWorld()
        w.setTerrain(floorRow())
        w.addBody(TeikaType.TEIKA_1, x = G / 2f, y = G - 1f - 0.5f, id = "solo")
        repeat(20) { w.step(FRAME, 0L) }
        w.setHorizontalGravity(-TILT_25)
        repeat(40) { w.step(FRAME, 0L) }
        w.setHorizontalGravity(0f)
        repeat(150) { w.step(FRAME, 0L) }         // 2,5 s en plano
        val residual = perFrameDx(w, "solo", 12).map { kotlin.math.abs(it) }.average()
        assertTrue("sigue deslizándose sin inclinación: $residual/frame", residual < 0.01f)
    }

    @Test
    fun `la ayuda de inclinacion no se dispara con el telefono casi plano`() {
        // Por debajo de TILT_ASSIST_MIN_G no debe haber ninguna ayuda: un ángulo de sujeción normal
        // no puede mover el tablero solo.
        val w = TeikaPhysicsWorld()
        w.setTerrain(floorRow())
        w.addBody(TeikaType.TEIKA_1, x = G / 2f, y = G - 1f - 0.5f, id = "solo")
        repeat(20) { w.step(FRAME, 0L) }
        val x0 = xOf(w, "solo")
        w.setHorizontalGravity(-1.5f)             // inclinación de sujeción, no de juego
        repeat(60) { w.step(FRAME, 0L) }
        assertTrue("derrapa con el móvil casi plano: ${x0 - xOf(w, "solo")}",
            kotlin.math.abs(x0 - xOf(w, "solo")) < 0.35f)
    }

    @Test
    fun `un T1 responde mas rapido a la inclinacion que un T4`() {
        fun displacement(type: TeikaType): Float {
            val world = TeikaPhysicsWorld()
            world.addBody(type, x = G / 2f, y = 2f, id = "body", nowMs = 0L)
            world.setHorizontalGravity(TILT_25)
            repeat(12) { world.step(FRAME, 1_000L) }
            return xOf(world, "body") - G / 2f
        }

        val agile = displacement(TeikaType.TEIKA_1)
        val veryHeavy = displacement(TeikaType.TEIKA_4)
        assertTrue(
            "el perfil ágil debe ganar desplazamiento: T1=$agile, T4=$veryHeavy",
            agile > veryHeavy * 1.2f
        )
    }
}
