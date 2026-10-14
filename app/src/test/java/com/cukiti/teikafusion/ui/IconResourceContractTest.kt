package com.cukiti.teikafusion.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Congela el catálogo de arte que sustituye los glifos tipográficos de la interfaz.
 *
 * Los originales viven en `art/` y no viajan con un clon del repositorio. Esta prueba valida la
 * parte que sí se distribuye: cada pieza interna tiene su WebP en `res/raw` y una referencia
 * estática `R.raw.*`. La referencia estática es importante porque permite al shrinker conservar
 * exactamente lo usado sin inflar `keep.xml` con comodines.
 */
class IconResourceContractTest {

    private val repoRoot: File by lazy {
        generateSequence(File("").absoluteFile) { it.parentFile }
            .first { File(it, "settings.gradle.kts").isFile }
    }

    private val abilityIcons = listOf(
        "ic_ability_cristal", "ic_ability_dormilona", "ic_ability_miedoso",
        "ic_ability_planta", "ic_ability_electrico", "ic_ability_fuego",
        "ic_ability_ninja", "ic_ability_hielo", "ic_ability_fantasma",
        "ic_ability_gloton", "ic_ability_toxico", "ic_ability_estrella",
        "ic_ability_rey", "ic_ability_galactico",
    )

    private val interfaceIcons = listOf(
        "ic_streak", "ic_trophy", "ic_map", "ic_teikas", "ic_medal", "ic_star",
        "ic_back", "ic_close", "ic_check", "ic_retry", "ic_edit", "ic_play",
        "ic_bullet", "ic_lock",
        "ic_defeat_no_space", "ic_defeat_lava", "ic_heart_broken",
        "ic_bell_on", "ic_bell_off", "ic_calm_mode",
        "ic_rank_gold", "ic_rank_silver", "ic_rank_bronze", "ic_leaderboard",
        "ic_block_skin", "ic_crystal",
    )

    private val murals = listOf(
        "mural_crystal", "mural_sleep", "mural_fear", "mural_garden", "mural_storm",
        "mural_volcano", "mural_ninja", "mural_glacier", "mural_ghost",
        "mural_glutton", "mural_toxic", "mural_star", "mural_king", "mural_galactic",
    )

    @Test
    fun `los recursos internos existen y estan cableados estaticamente`() {
        val expected = abilityIcons + interfaceIcons + murals
        val raw = File(repoRoot, "app/src/main/res/raw")
        val kotlinSource = File(repoRoot, "app/src/main/java/com/cukiti/teikafusion")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("\n") { it.readText() }

        expected.forEach { name ->
            val resource = File(raw, "$name.webp")
            assertTrue("Falta res/raw/$name.webp", resource.isFile && resource.length() > 0L)
            assertTrue("$name.webp no tiene referencia R.raw estatica", kotlinSource.contains("R.raw.$name"))
        }
    }

    @Test
    fun `los iconos de tienda corresponden a las recompensas vigentes`() {
        val raw = File(repoRoot, "app/src/main/res/raw")
        com.cukiti.teikafusion.model.StorePacks.COIN_PACKS.forEach { pack ->
            val name = "store_teikoins_${pack.coins}"
            assertTrue("Falta el icono de ${pack.coins} teikoins", File(raw, "$name.webp").isFile)
            val expected = com.cukiti.teikafusion.R.raw::class.java.getField(name).getInt(null)
            org.junit.Assert.assertEquals("Icono incorrecto para ${pack.productId}", expected, coinPackArtwork(pack.productId))
        }
        listOf("store_starter_pack", "store_remove_ads", "settings").forEach { name ->
            assertTrue("Falta $name.webp", File(raw, "$name.webp").isFile)
        }
        assertTrue("No deben empaquetarse PNG duplicados o pendientes", raw.listFiles()!!.none { it.extension == "png" })
    }
}
