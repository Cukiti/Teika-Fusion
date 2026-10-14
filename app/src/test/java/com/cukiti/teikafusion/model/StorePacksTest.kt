package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **LA ARITMÉTICA DEL DINERO** (2026-09-06, con la tienda reabierta).
 *
 * Es la parte del juego que más caro se paga si se equivoca, y la única que el jugador nota al
 * instante: un bono que se cobra dos veces regala monedas para siempre, y uno que no se cobra nunca
 * hace que el sello «×2» de la tienda sea publicidad falsa.
 *
 * Los dos invariantes que defiende este fichero:
 *
 *  1. **El bono de bienvenida es exactamente el 100 %, y una sola vez por pack.**
 *  2. **El paquete inicial dice la verdad**: lo que promete la tarjeta es lo que entrega el motor.
 */
class StorePacksTest {

    @Test
    fun `el bono de bienvenida es el 100 por cien exacto`() {
        StorePacks.COIN_PACKS.forEach { pack ->
            assertEquals(
                "El pack de ${pack.coins} no dobla",
                pack.coins,
                StorePacks.welcomeBonusFor(pack.productId, emptySet())
            )
            assertEquals(
                pack.coins * 2,
                StorePacks.totalCoinsFor(pack.productId, emptySet())
            )
        }
    }

    @Test
    fun `el bono se cobra UNA vez por pack`() {
        val pack = StorePacks.COIN_PACKS.first()
        assertTrue(StorePacks.bonusAvailable(pack.productId, emptySet()))
        val claimed = setOf(pack.productId)
        assertFalse(StorePacks.bonusAvailable(pack.productId, claimed))
        assertEquals(0, StorePacks.welcomeBonusFor(pack.productId, claimed))
        assertEquals(pack.coins, StorePacks.totalCoinsFor(pack.productId, claimed))
    }

    @Test
    fun `gastar el bono de un pack no gasta el de los otros`() {
        // El bono es POR PACK: quien compre el de 250 tiene que seguir teniendo el del de 5000.
        val gastado = setOf(StorePacks.COIN_PACKS.first().productId)
        StorePacks.COIN_PACKS.drop(1).forEach { pack ->
            assertTrue(
                "El pack de ${pack.coins} perdió su bono al comprar otro",
                StorePacks.bonusAvailable(pack.productId, gastado)
            )
        }
    }

    @Test
    fun `un producto que no es pack de monedas no cobra bono`() {
        // «Quitar anuncios» y el paquete inicial pasan por el mismo camino y no dan monedas por sí
        // solos: si `welcomeBonusFor` les respondiera algo, se acreditarían monedas de la nada.
        listOf(StorePacks.REMOVE_ADS_ID, StorePacks.STARTER_PACK_ID, "producto_inventado").forEach { id ->
            assertEquals(0, StorePacks.welcomeBonusFor(id, emptySet()))
            assertEquals(0, StorePacks.totalCoinsFor(id, emptySet()))
            assertFalse(StorePacks.bonusAvailable(id, emptySet()))
            assertEquals(null, StorePacks.coinsFor(id))
        }
    }

    @Test
    fun `los ids de los packs son los que estan dados de alta en Play`() {
        // Un id mal escrito no falla: el producto simplemente no aparece nunca en la tienda, y eso
        // es indistinguible de «Play todavía no ha respondido». Por eso se fijan aquí.
        assertEquals(
            listOf("coins_250", "coins_1200", "coins_5000"),
            StorePacks.COIN_PACKS.map { it.productId }
        )
        assertEquals("starter_pack", StorePacks.STARTER_PACK_ID)
        assertEquals("remove_ads", StorePacks.REMOVE_ADS_ID)
    }

    @Test
    fun `ningun id dice lo que entrega, y las cifras son las decididas`() {
        // Los tres ids MIENTEN a propósito y no se pueden arreglar: Play no deja renombrar ni
        // reutilizar el id de un producto. Lo que manda es la cifra de `COIN_PACKS`, y lo que se
        // escribe en Play Console tiene que decir ESTA cifra, no la del id.
        //
        // Si alguien "corrige" alguna leyendo el nombre del id, esto se pone rojo y cuenta por qué.
        assertEquals(3000, StorePacks.coinsFor("coins_250"))
        assertEquals(5000, StorePacks.coinsFor("coins_1200"))
        assertEquals(10000, StorePacks.coinsFor("coins_5000"))
        // El pack de entrada tiene que cubrir un rescate entero: pagar para salvar la partida en la
        // que estás y descubrir DESPUÉS que no te alcanza es una devolución y una estrella.
        assertTrue(
            "El pack pequeño tiene que cubrir un rescate entero",
            StorePacks.coinsFor("coins_250")!! >= LevelBudget.REVIVE_COST_TEIKOINS
        )
    }

    @Test
    fun `los packs van de menos a mas y ninguno se repite`() {
        val monedas = StorePacks.COIN_PACKS.map { it.coins }
        assertEquals("Los packs deben ir de menos a más", monedas.sorted(), monedas)
        assertEquals("Hay dos packs con el mismo id",
            StorePacks.COIN_PACKS.size,
            StorePacks.COIN_PACKS.map { it.productId }.distinct().size)
    }

    // ── EL PAQUETE INICIAL ──────────────────────────────────────────────────────────────────────

    @Test
    fun `los bloques que promete el paquete inicial existen y son de pago`() {
        // El pack los nombra por id porque `BlockSkins` vive en `ui/` y esto es economía. Este cruce
        // es lo que impide que las dos listas se separen en silencio: renombrar un skin o volverlo
        // gratis dejaría al paquete prometiendo algo que no puede entregar.
        StorePacks.STARTER_SKIN_IDS.forEach { id ->
            val skin = com.cukiti.teikafusion.ui.BlockSkins.all.firstOrNull { it.id == id }
            assertTrue("El paquete inicial promete un bloque que no existe: $id", skin != null)
            assertTrue(
                "El paquete inicial regala un bloque que ya era gratis: $id",
                (skin?.priceTeikoins ?: 0) > 0
            )
        }
        // Y lo que promete, por su NOMBRE: «los bloques Cristal y Cerámica» (texto de Play, P-249). Hasta
        // el bloque Madera (P-251 2/7, 2026-09-25) esto exigía que fueran TODOS los de pago, porque la
        // descripción vieja decía «los dos bloques de pago»; con los bloques de bioma eso deja de ser
        // verdad a propósito, y el pack sigue trayendo exactamente estos dos.
        assertEquals(listOf("cristal", "fall3d"), StorePacks.STARTER_SKIN_IDS.sorted())
    }

    @Test
    fun `el companero que regala el paquete inicial existe`() {
        val estela = NamedTeikaRegistry.all.firstOrNull { it.id == StorePacks.STARTER_COMPANION_ID }
        assertTrue("El paquete inicial regala un compañero inexistente", estela != null)
        assertEquals("Estela", estela?.name)
    }

    @Test
    fun `la semana de vidas infinitas son siete dias de verdad`() {
        assertEquals(7, StorePacks.STARTER_INFINITE_LIVES_DAYS)
        assertEquals(7L * 24 * 60 * 60 * 1000, StorePacks.STARTER_INFINITE_LIVES_MS)
        // Y es MÁS que el pack más largo que se compra con teikoins: si no, el paquete de dinero
        // real ofrecería menos que lo que ya se puede conseguir jugando.
        val masLargoEnTeikoins = com.cukiti.teikafusion.viewmodel.GameViewModel.INFINITE_LIVES_PACKS
            .maxOf { it.hours }
        assertTrue(
            "La semana del paquete inicial no supera al pack de $masLargoEnTeikoins h",
            StorePacks.STARTER_INFINITE_LIVES_DAYS * 24 > masLargoEnTeikoins
        )
    }

    @Test
    fun `el resumen de la tarjeta menciona las cinco cosas que entrega`() {
        // La tarjeta de la tienda se pinta con esta lista. Si alguien añade contenido al pack y no
        // lo escribe aquí, el jugador paga por algo que no le anunciaron — y al revés es peor.
        val texto = StorePacks.STARTER_PACK_CONTENT.joinToString(" ").lowercase()
        assertEquals(5, StorePacks.STARTER_PACK_CONTENT.size)
        assertTrue("Falta el importe en teikoins", texto.contains("${StorePacks.STARTER_COINS}"))
        assertTrue("Faltan los bloques", texto.contains("bloque"))
        assertTrue("Faltan los días de vidas infinitas", texto.contains("vidas infinitas"))
        assertTrue("Falta Estela", texto.contains("estela"))
        // 2026-09-28: el banco de bloques es de pago y viene en el paquete.
        assertTrue("Falta Fichona", texto.contains("fichona"))
    }
}
