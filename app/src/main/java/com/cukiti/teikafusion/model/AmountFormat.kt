package com.cukiti.teikafusion.model

import java.util.Locale

/**
 * P-419 · UN SOLO FORMATO DE CIFRAS (2026-10-01, auditoría por secciones).
 *
 * El mismo saldo salía de tres maneras: «9999+» en Inicio, «86.9K» en el Inventario y «86908» en la
 * partida. Y las abreviaturas usaban el idioma DEL MÓVIL (`String.format` sin locale): en un teléfono
 * en inglés el juego, que es solo en español, escribía «86.9K» con punto decimal.
 *
 * Regla: hasta 9.999 la cifra entera; luego una abreviatura española con coma decimal y la letra en
 * mayúscula — 86.908 → «86,9K», 123.456 → «123K», 1.250.000 → «1,2M». Cabe en las casillas estrechas
 * (cartera de Inicio, Inventario) y se lee igual en todas las pantallas.
 */
object AmountFormat {
    /**
     * P-431 · La cifra ENTERA con separador de miles, para donde hay sitio y la cantidad exacta
     * importa (la tienda: lo que compras). Norma española: con cuatro cifras no se separa («5000»),
     * a partir de cinco sí, con punto («10.000», «1.250.000»).
     */
    fun full(value: Int): String {
        if (value in -9_999..9_999) return value.toString()
        val digits = kotlin.math.abs(value.toLong()).toString()
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        return if (value < 0) "-$grouped" else grouped
    }

    private val ES: Locale = Locale.forLanguageTag("es-ES")

    fun compact(value: Int): String = when {
        value >= 1_000_000 -> String.format(ES, "%.1fM", value / 1_000_000f).replace(",0M", "M")
        value >= 100_000 -> "${value / 1000}K"
        value >= 10_000 -> String.format(ES, "%.1fK", value / 1000f).replace(",0K", "K")
        else -> value.toString()
    }
}
