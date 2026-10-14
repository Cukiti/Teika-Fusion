package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class AmountFormatTest {

    @Test
    fun `hasta 9999 la cifra entera`() {
        assertEquals("0", AmountFormat.compact(0))
        assertEquals("9999", AmountFormat.compact(9_999))
    }

    @Test
    fun `abreviatura espanola con coma y mayuscula`() {
        assertEquals("86,9K", AmountFormat.compact(86_908))
        assertEquals("10K", AmountFormat.compact(10_000))
        assertEquals("123K", AmountFormat.compact(123_456))
        assertEquals("1,2M", AmountFormat.compact(1_240_000))
        assertEquals("2M", AmountFormat.compact(2_000_000))
    }

    @Test
    fun `no depende del idioma del movil`() {
        val previo = Locale.getDefault()
        try {
            Locale.setDefault(Locale.US)
            assertEquals("86,9K", AmountFormat.compact(86_908))
        } finally {
            Locale.setDefault(previo)
        }
    }

    /** P-431: la cifra entera de la tienda. Cuatro cifras sin separar; de cinco en adelante, con punto. */
    @Test
    fun `full separa los miles a la espanola`() {
        assertEquals("600", AmountFormat.full(600))
        assertEquals("5000", AmountFormat.full(5_000))
        assertEquals("10.000", AmountFormat.full(10_000))
        assertEquals("123.456", AmountFormat.full(123_456))
        assertEquals("1.250.000", AmountFormat.full(1_250_000))
        assertEquals("-10.000", AmountFormat.full(-10_000))
    }
}
