package com.cukiti.teikafusion.viewmodel

import com.cukiti.teikafusion.model.MagnetPiece
import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.model.TeikaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * La física de la primera tanda del rework de reglas (2026-09-22), sobre el mundo REAL: el golpe de
 * plomo (P-239) y el imán que se coloca (P-240). Las reglas puras están en `FeelAndMarkRulesTest`;
 * esto comprueba que el integrador hace lo que ellas prometen.
 */
class HouseRulePhysicsTest {

    private fun run(world: TeikaPhysicsWorld, seconds: Float) {
        var t = 0f
        while (t < seconds) { world.step(1f / 60f, 0L); t += 1f / 60f }
    }

    /** Velocidad de caída libre de [cells] casillas con la gravedad del mundo, con margen. */
    private fun crushSpeedFor(cells: Float) = sqrt(2f * TeikaPhysicsWorld.GRAVITY * cells) * 0.85f

    @Test
    fun `una caida larga aplasta el bloque sobre el que aterriza`() {
        val world = TeikaPhysicsWorld()
        val bloque = Position(3, 7)
        world.setTerrain(setOf(bloque))
        world.setLeadCrushSpeed(crushSpeedFor(2f))
        world.addBody(TeikaType.TEIKA_1, x = 3.5f, y = 1.5f, nowMs = 0L)
        run(world, 1.5f)
        assertEquals(mapOf(bloque to TeikaType.TEIKA_1), world.drainLeadCrushes())
    }

    @Test
    fun `un cuerpo que se asienta desde poca altura no aplasta nada`() {
        val world = TeikaPhysicsWorld()
        val bloque = Position(3, 7)
        world.setTerrain(setOf(bloque))
        world.setLeadCrushSpeed(crushSpeedFor(2f))
        // Media casilla por encima del bloque.
        world.addBody(TeikaType.TEIKA_1, x = 3.5f, y = 6.0f, nowMs = 0L)
        run(world, 1.5f)
        assertTrue(world.drainLeadCrushes().isEmpty())
    }

    @Test
    fun `sin la regla ninguna caida aplasta`() {
        val world = TeikaPhysicsWorld()
        world.setTerrain(setOf(Position(3, 7)))
        world.addBody(TeikaType.TEIKA_1, x = 3.5f, y = 0.5f, nowMs = 0L)
        run(world, 1.5f)
        assertTrue(world.drainLeadCrushes().isEmpty())
    }

    /** Lo que no es rompible (protegido por bioma, ficha recién puesta) no se aplasta. */
    @Test
    fun `el plomo respeta el terreno no rompible`() {
        val world = TeikaPhysicsWorld()
        val bloque = Position(3, 7)
        world.setTerrain(setOf(bloque), breakableCells = emptySet())
        world.setLeadCrushSpeed(crushSpeedFor(2f))
        world.addBody(TeikaType.TEIKA_1, x = 3.5f, y = 0.5f, nowMs = 0L)
        run(world, 1.5f)
        assertTrue(world.drainLeadCrushes().isEmpty())
    }

    /** El imán levanta a un cuerpo que tiene debajo: tira más que la gravedad. */
    @Test
    fun `el iman atrae a un cuerpo hacia arriba`() {
        val world = TeikaPhysicsWorld()
        val body = world.addBody(TeikaType.TEIKA_1, x = 3.5f, y = 7.5f, nowMs = 0L)
        world.setMagnet(3.5f, 3.5f, MagnetPiece.RANGE_FIRST_CHAPTER)
        run(world, 3f)
        val after = world.getBody(body.id)!!
        assertTrue("el cuerpo no ha subido hacia el imán: y=${after.y}", after.y < 6f)
    }

    /** Un cuerpo fuera del alcance no se entera; al apagar el imán, todo vuelve a caer. */
    @Test
    fun `fuera del alcance o sin iman no hay tiron`() {
        val world = TeikaPhysicsWorld()
        val body = world.addBody(TeikaType.TEIKA_1, x = 0.5f, y = 7.5f, nowMs = 0L)
        world.setMagnet(7.5f, 0.5f, 2f)
        run(world, 2f)
        assertEquals(7.5f, world.getBody(body.id)!!.y, 0.05f)
        world.setMagnet(0f, 0f, 0f)
        run(world, 1f)
        assertEquals(7.5f, world.getBody(body.id)!!.y, 0.05f)
    }

    // ── P-238 · LAS POMPAS ──

    @Test
    fun `un teika en su pompa sube al techo y al reventarla cae`() {
        val world = TeikaPhysicsWorld()
        val body = world.addBody(TeikaType.TEIKA_1, x = 3.5f, y = 6.5f, nowMs = 0L)
        world.setBubble(body.id, 1)
        run(world, 3f)
        assertTrue("la pompa no ha subido: y=${world.getBody(body.id)!!.y}", world.getBody(body.id)!!.y < 1.2f)
        world.setBubble(body.id, 0)
        run(world, 3f)
        assertTrue("no ha vuelto a caer", world.getBody(body.id)!!.y > 6.8f)
    }

    // ── P-243 · LOS PORTALES ──

    @Test
    fun `un teika que rueda contra un portal sale por el otro`() {
        val world = TeikaPhysicsWorld()
        world.setPortals(leftTop = 6, rightTop = 3)
        val body = world.addBody(TeikaType.TEIKA_1, x = 1.5f, y = 7.5f, vx = -8f, nowMs = 0L)
        run(world, 0.5f)
        val after = world.getBody(body.id)!!
        assertTrue("no ha salido por la derecha: x=${after.x}", after.x > 5f)
        assertEquals(1, world.drainPortalHops().size)
    }

    @Test
    fun `un portal con la salida tapada no deja pasar`() {
        val world = TeikaPhysicsWorld()
        world.setPortals(leftTop = 6, rightTop = 3)
        world.setTerrain(setOf(Position(7, 3), Position(7, 4)))
        val body = world.addBody(TeikaType.TEIKA_1, x = 1.5f, y = 7.5f, vx = -8f, nowMs = 0L)
        run(world, 0.5f)
        assertTrue(world.getBody(body.id)!!.x < 2f)
        assertTrue(world.drainPortalHops().isEmpty())
    }

    @Test
    fun `un teika grande no cabe por el portal`() {
        val world = TeikaPhysicsWorld()
        world.setPortals(leftTop = 5, rightTop = 2)
        val body = world.addBody(TeikaType.TEIKA_3, x = 2f, y = 6.5f, vx = -8f, nowMs = 0L)
        run(world, 0.5f)
        assertTrue(world.drainPortalHops().isEmpty())
        assertTrue(world.getBody(body.id)!!.x < 3f)
    }

    // ── P-241 · LA BRÚJULA ──

    @Test
    fun `con la brujula a la derecha los teikas ruedan hacia esa pared`() {
        val world = TeikaPhysicsWorld()
        val body = world.addBody(TeikaType.TEIKA_1, x = 2.5f, y = 7.5f, nowMs = 0L)
        world.setGravityDirection(1f, 0f)
        run(world, 2f)
        assertTrue("no ha ido a la derecha: x=${world.getBody(body.id)!!.x}", world.getBody(body.id)!!.x > 7f)
        world.setGravityDirection(0f, -1f)
        run(world, 2f)
        assertTrue("no ha subido: y=${world.getBody(body.id)!!.y}", world.getBody(body.id)!!.y < 1f)
    }
}
