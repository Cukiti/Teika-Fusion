package com.cukiti.teikafusion.ui

import com.cukiti.teikafusion.model.LevelMap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P-429: la moneda del botón de la vitrina sale solo si hay un bloque a la venta que YA puedes pagar,
 * con la misma cuenta que la vitrina ([showcaseEntries]).
 */
class BlockShowcaseBadgeTest {
    private val todoDescubierto = LevelMap.totalLevels + 1

    @Test
    fun `con dinero y bloques descubiertos sin comprar, hay moneda`() {
        assertTrue(hasAffordableBlock(Int.MAX_VALUE, todoDescubierto, emptySet(), BlockSkins.DEFAULT_ID))
    }

    @Test
    fun `sin dinero no hay moneda`() {
        assertFalse(hasAffordableBlock(0, todoDescubierto, emptySet(), BlockSkins.DEFAULT_ID))
    }

    @Test
    fun `con todo comprado no hay moneda`() {
        val todos = BlockSkins.all.map { it.id }.toSet()
        assertFalse(hasAffordableBlock(Int.MAX_VALUE, todoDescubierto, todos, BlockSkins.DEFAULT_ID))
    }

    @Test
    fun `un bloque mas caro de lo que tienes no cuenta`() {
        val masBarato = BlockSkins.all.filter { it.priceTeikoins > 0 }.minOf { it.priceTeikoins }
        assertFalse(hasAffordableBlock(masBarato - 1, todoDescubierto, emptySet(), BlockSkins.DEFAULT_ID))
    }
}
