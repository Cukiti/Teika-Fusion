package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La regla de «energiza o rompe» de la Tormenta, y su CONTRATO: el motor y la vista previa la
 * llaman a ella y a nadie más (eran dos copias que se separaron, ver [StormCircuit]).
 */
class StormCircuitTest {
    private val level = (1..LevelMap.totalLevels).first { LevelDirector.stormCoilCount(it) >= 5 }
    private val coils = LevelDirector.stormCoilCount(level)

    @Test
    fun `hacen falta tantos bloques lisos como bobinas`() {
        assertFalse(StormCircuit.energizes(level, wholeRow = true, usesTeika = false, plainWood = coils - 1))
        assertTrue(StormCircuit.energizes(level, wholeRow = true, usesTeika = false, plainWood = coils))
    }

    @Test
    fun `un Teika o una media fila no se energizan`() {
        assertFalse(StormCircuit.energizes(level, wholeRow = true, usesTeika = true, plainWood = 8))
        assertFalse(StormCircuit.energizes(level, wholeRow = false, usesTeika = false, plainWood = 8))
    }

    @Test
    fun `el motor y la vista previa usan la misma regla`() {
        var dir: File? = File("").absoluteFile
        val rel = "app/src/main/java/com/cukiti/teikafusion/viewmodel/GameViewModel.kt"
        while (dir != null && !File(dir, rel).isFile) dir = dir.parentFile
        val src = File(requireNotNull(dir), rel).readText()
        assertEquals(
            "resolveLineClearPass y previewClearLines deben preguntar a StormCircuit (y solo ellos)",
            2, Regex("""StormCircuit\.energizes\(""").findAll(src).count()
        )
        assertFalse("Vuelve a haber un umbral de bobinas escrito a mano", src.contains("plainWoodCountAfterStamp(it) >= 4"))
    }
}
