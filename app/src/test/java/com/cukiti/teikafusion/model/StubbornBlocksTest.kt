package com.cukiti.teikafusion.model

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contrato de BLOQUES TESTARUDOS (P-71). */
class StubbornBlocksTest {

    @Test
    fun `en una fila entera pide tres de su color`() {
        assertEquals(3, StubbornBlocks.requiredFor(8))
        assertTrue(StubbornBlocks.resists(sameColorInLine = 2, segmentLength = 8))
        assertFalse(StubbornBlocks.resists(sameColorInLine = 3, segmentLength = 8))
    }

    /**
     * **El Corte Ninja parte el tablero en tramos de cuatro**, y ahí pedir tres de cuatro sería
     * casi imposible. El umbral baja con el tramo: es la variante «fijo o proporcional» que la
     * propuesta dejaba abierta, resuelta como las dos cosas.
     */
    @Test
    fun `en media fila del corte ninja pide dos`() {
        assertEquals(2, StubbornBlocks.requiredFor(4))
        assertTrue(StubbornBlocks.resists(sameColorInLine = 1, segmentLength = 4))
        assertFalse(StubbornBlocks.resists(sameColorInLine = 2, segmentLength = 4))
    }

    @Test
    fun `el umbral nunca pasa de la mitad del tramo ni baja de uno`() {
        for (len in 1..12) {
            val need = StubbornBlocks.requiredFor(len)
            assertTrue("len=$len need=$need", need >= 1)
            assertTrue("len=$len need=$need supera StubbornBlocks.REQUIRED", need <= StubbornBlocks.REQUIRED)
            if (len >= 2) {
                assertTrue("len=$len need=$need pasa de la mitad", need <= Math.ceil(len / 2.0).toInt())
            }
        }
    }

    @Test
    fun `siembra solo en los niveles que le tocan`() {
        val cells = (0 until 20).map { LevelCell(it % 8, it / 8, TargetColor.RED) }
        for (level in 1..LevelMap.totalLevels) {
            val seeded = StubbornBlocks.seedFor(level, cells)
            assertEquals(
                "nivel $level: siembra y appliesTo no coinciden",
                StubbornBlocks.appliesTo(level),
                seeded.isNotEmpty()
            )
        }
    }

    @Test
    fun `siembra entre tres y cinco y siempre sobre celdas de la figura`() {
        val cells = (0 until 30).map { LevelCell(it % 8, it / 8, TargetColor.BLUE) }
        val figure = cells.mapTo(HashSet()) { Position(it.x, it.y) }
        for (level in 1..LevelMap.totalLevels) {
            if (!StubbornBlocks.appliesTo(level)) continue
            val seeded = StubbornBlocks.seedFor(level, cells)
            assertTrue(
                "nivel $level sembró ${seeded.size}",
                seeded.size in StubbornBlocks.MIN_BLOCKS..StubbornBlocks.MAX_BLOCKS
            )
            // Nunca inventa madera: si la añadiera, movería la densidad y el presupuesto de turnos.
            assertTrue("nivel $level siembra fuera de la figura", figure.containsAll(seeded))
        }
    }

    /**
     * **La figura ENTERA lleva estrella desde el 2026-08-01**, así que un filtro «solo celdas sin
     * objetivo» habría dejado la piscina vacía y la mecánica no existiría. Este test fija que la
     * siembra funciona precisamente en ese caso, que es el único real.
     */
    @Test
    fun `siembra aunque todas las celdas lleven estrella`() {
        val level = HouseRule.levelsWith(HouseRule.DOMINO).first()
        val todasConEstrella = (0 until 20).map { LevelCell(it % 8, it / 8, TargetColor.PINK) }
        assertFalse(StubbornBlocks.appliesTo(level))
        assertTrue(StubbornBlocks.seedFor(level, todasConEstrella).isEmpty())
    }

    @Test
    fun `es determinista`() {
        val cells = (0 until 20).map { LevelCell(it % 8, it / 8, TargetColor.RED) }
        for (level in 1..LevelMap.totalLevels step 13) {
            assertEquals(StubbornBlocks.seedFor(level, cells), StubbornBlocks.seedFor(level, cells))
        }
    }

    /**
     * **EL DIAL DE CAPÍTULO** (2026-09-03, pedido del usuario: «que cada mecánica se vuelva más
     * difícil según sube de bioma»). La regla no cambia —el testarudo sigue cediendo con tres bloques
     * de su color en la línea—; cambia cuántas veces hay que resolverla en el mismo nivel: tres en el
     * primer bioma, cinco en el último, sobre un tablero que además llega mucho más lleno.
     */
    @Test
    fun `se siembran mas testarudos de bioma en bioma`() {
        assertEquals(
            StubbornBlocks.MIN_BLOCKS,
            StubbornBlocks.seedCountFor(LevelMap.chapterStart.first())
        )
        assertEquals(
            StubbornBlocks.MAX_BLOCKS,
            StubbornBlocks.seedCountFor(LevelMap.chapterStart.last())
        )
        var previo = 0
        LevelMap.chapterStart.forEach { start ->
            val n = StubbornBlocks.seedCountFor(start)
            assertTrue("bajan los testarudos en el capítulo de $start", n >= previo)
            previo = n
        }
    }

    /**
     * **EL SOFTLOCK QUE ESTE TEST IMPIDE.** Si `clearableWood` dejara de tratar al testarudo como al
     * cristal, una línea en la que TODOS resistieran se daría por limpiada sin cambiar nada y
     * `resolveBoardChain` la volvería a ver completa en la pasada siguiente: bucle infinito.
     *
     * Desde P-446 (2026-10-01) la regla vive en UN sitio, `LineDetector.woodClearable`, que comparten
     * el motor y la vista previa. Se vigilan las dos mitades: que el modelo excluya al testarudo y que
     * `clearableWood` del motor pase por él (y no por una copia que pueda olvidarlo).
     */
    @Test
    fun `el testarudo no cuenta como algo que romper`() {
        val vm = File(repoRoot, "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt")
            .readText()
        val fn = vm.indexOf("fun clearableWood")
        assertTrue("no encuentro clearableWood", fn > 0)
        val body = vm.substring(fn, fn + 1400)
        assertTrue(
            "clearableWood ya no pasa por LineDetector.woodClearable: el testarudo puede volver a romperse en bucle",
            "LineDetector.woodClearable" in body
        )
        val model = File(repoRoot, "app/src/main/java/com/cukiti/teikafusion/model/LineDetector.kt").readText()
        val rule = model.indexOf("fun woodClearable")
        assertTrue("no encuentro LineDetector.woodClearable", rule > 0)
        assertTrue(
            "LineDetector.woodClearable ya no excluye al testarudo: una línea de puros testarudos hará bucle",
            "w.stubborn" in model.substring(rule, rule + 600)
        )
    }

    /** La Brocha no lo repinta y la pureza no lo cuenta: si no, dejaría de exigir nada. */
    @Test
    fun `ni la brocha lo repinta ni la pureza lo cuenta`() {
        val vm = File(repoRoot, "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt")
            .readText()
        val brush = vm.indexOf("// 2.6 LA BROCHA")
        val pure = vm.indexOf("// 2.65 LÍNEA PURA")
        val tint = vm.indexOf("// 2.7 NIVELES / tinte Block Blast")
        assertTrue("la Brocha repinta testarudos", "!w.stubborn" in vm.substring(brush, pure))
        assertTrue("la pureza cuenta testarudos", "w.stubborn" in vm.substring(pure, tint))
    }

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    private val repoRoot: File by lazy {
        var dir: File? = File("").absoluteFile
        while (dir != null && !File(dir, "app/src/main/java/com/cukiti/teikafusion/").isDirectory) {
            dir = dir.parentFile
        }
        requireNotNull(dir) { "No encuentro la raíz del repo desde ${File("").absolutePath}" }
    }
}
