package com.cukiti.teikafusion.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * **P-289 · LOS FONDOS DEL MAPA, EN LOSAS Y CON MEMORIA** (2026-09-25, playtest: «que se sienta mucho
 * más fluido al deslizar y al pulsar los botones»).
 *
 * Los 14 fondos pintados miden 1024 × 3.072–4.480 px. Dos problemas medidos en el código:
 *
 *  1. **Más altos que una textura.** Muchas GPU de gama media no aceptan texturas de más de 4.096 px:
 *     un fondo de 4.480 no se puede subir entero y Android lo trocea o lo dibuja por software cada
 *     fotograma. Aquí se decodifica en LOSAS de [TILE] px con `BitmapRegionDecoder`: cada una cabe.
 *  2. **Se decodificaban cada vez.** El `produceState` de cada tramo moría al salir de pantalla, así que
 *     volver a un bioma lo decodificaba otra vez (9 MB, en IO) y enseñaba el fondo provisional
 *     mientras tanto. Ahora las losas viven en una LRU de [BUDGET_BYTES] (~4 biomas), y quien pinta
 *     un bioma pide también el de al lado: al llegar, ya está.
 *
 * RGB_565 como antes: la mitad de memoria que ARGB y los fondos no tienen alfa.
 */
object MapArtCache {

    /** Alto de cada losa en píxeles del arte: por debajo de cualquier límite de textura. */
    const val TILE = 1024

    /** Techo de memoria de la caché (~4 biomas de 9 MB en RGB_565). */
    private const val BUDGET_BYTES = 40 * 1024 * 1024

    /** Las losas de un fondo, de arriba abajo, y su alto total en píxeles del arte. */
    class Tiles(val tiles: List<ImageBitmap>, val width: Int, val height: Int)

    private val cache = object : LruCache<Int, Tiles>(BUDGET_BYTES) {
        override fun sizeOf(key: Int, value: Tiles): Int =
            value.tiles.sumOf { it.asAndroidBitmap().byteCount }
    }

    /** Lo que ya está en memoria, sin decodificar nada (para pintar al instante al volver). */
    fun peek(res: Int): Tiles? = cache.get(res)

    /**
     * Las losas de [res], decodificándolas si hace falta. Llamar FUERA del hilo principal. Dos
     * llamadas a la vez con el mismo recurso pueden decodificar dos veces: se queda la última, y no
     * compensa un candado para un caso que solo cuesta un poco de trabajo repetido.
     */
    fun load(context: Context, res: Int): Tiles? {
        cache.get(res)?.let { return it }
        val tiles = runCatching { decode(context, res) }.getOrNull() ?: return null
        cache.put(res, tiles)
        return tiles
    }

    private fun decode(context: Context, res: Int): Tiles? {
        context.resources.openRawResource(res).use { input ->
            val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                BitmapRegionDecoder.newInstance(input)
            } else {
                @Suppress("DEPRECATION")
                BitmapRegionDecoder.newInstance(input, false)
            } ?: return null
            try {
                val w = decoder.width
                val h = decoder.height
                val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.RGB_565 }
                val out = ArrayList<ImageBitmap>((h + TILE - 1) / TILE)
                var top = 0
                while (top < h) {
                    val bottom = minOf(top + TILE, h)
                    val bmp = decoder.decodeRegion(Rect(0, top, w, bottom), opts) ?: return null
                    out += bmp.asImageBitmap()
                    top = bottom
                }
                return Tiles(out, w, h)
            } finally {
                decoder.recycle()
            }
        }
    }
}
