package com.cukiti.teikafusion.ui

import com.cukiti.teikafusion.model.Piece
import com.cukiti.teikafusion.model.Position
import org.junit.Assert.*
import org.junit.Test

class ReserveTraySlotsTest {
    private fun piece(id: String)=Piece(id=id,shape=listOf(Position(0,0)))
    @Test fun `exchanging the last piece preserves its slot despite two earlier empty slots`() {
        val held=piece("held")
        val result=resolveTraySlots(listOf(null,null,"last"),listOf(held),"last" to "held")
        assertEquals(listOf(null,null,held),result)
        assertEquals(result,resolveTraySlots(result.map { it?.id },listOf(held)))
    }
    @Test fun `exchanging the third slot preserves the second and does not fill the first`() {
        val other=piece("other"); val held=piece("held")
        assertEquals(listOf(null,other,held),resolveTraySlots(listOf(null,"other","last"),listOf(other,held),"last" to "held"))
    }
    @Test fun `ordinary placement keeps remaining slots and refill still fills empty slots`() {
        val a=piece("a"); val b=piece("b"); val c=piece("c")
        assertEquals(listOf(null,b,c),resolveTraySlots(listOf("a","b","c"),listOf(b,c)))
        assertEquals(listOf(a,b,c),resolveTraySlots(listOf(null,null,null),listOf(a,b,c)))
    }
}
