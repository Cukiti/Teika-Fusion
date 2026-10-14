package com.cukiti.teikafusion.model

/**
 * **LA TIENDA VUELVE A ABRIR** (2026-09-06, decisión del usuario).
 *
 * Este fichero es la regla pura de **qué recibe el jugador por su dinero**. Vive en `model/` y no en
 * la pantalla ni en el ViewModel a propósito: es aritmética de economía, la parte que más caro se
 * paga si se equivoca, y así se puede probar sin Play, sin Android y sin UI.
 *
 * ## Qué se revierte, y con los ojos abiertos
 *
 * La pestaña de teikoins se apagó el **2026-08-01** y la decisión de economía del **2026-08-09**
 * («sin tienda de teikoins; los anuncios son la fuente») la respaldaba. El usuario la reabre hoy con
 * los cuatro productos ya dados de alta más uno nuevo. No es un trámite que estuviera pendiente: es
 * un cambio de rumbo, y queda escrito aquí para que dentro de tres meses nadie lo lea como un
 * descuido — igual que quedó escrito cuando se cerró.
 *
 * ## Las dos reglas del dinero
 *
 *  1. **El bono de bienvenida** ([welcomeBonusFor]): la **primera** compra de cada pack de monedas
 *     paga el doble. Es de una sola vez por pack y por cuenta, así que hace falta recordar cuáles ya
 *     lo cobraron ([bonusClaimed]) — y ese recuerdo **viaja con la cuenta**, no con el aparato: si
 *     fuera local, reinstalar volvería a regalar el doble.
 *  2. **El paquete inicial** ([STARTER_PACK_CONTENT]) es **no consumible y se entrega UNA vez**. Es
 *     la diferencia más peligrosa de todo el fichero: Play reenvía las compras no consumibles **en
 *     cada arranque** (así es como se restauran al reinstalar), así que sin la marca de entregado,
 *     abrir el juego diez veces regalaría cincuenta mil teikoins. Ver `starterPackGranted`.
 */
object StorePacks {

    /** Un pack de monedas de la tienda: su producto de Play y lo que da de base. */
    data class CoinPack(val productId: String, val coins: Int)

    /**
     * Los tres packs, del más pequeño al más grande. El **precio no está aquí** y no es un olvido:
     * lo pone Play (`ProductDetails`), que es la única fuente que conoce la moneda y el país del
     * jugador. Un precio escrito a mano en el código es un precio que miente en cuanto cambia el IVA.
     */
    /**
     * ⚠️ **NINGÚN ID DICE LA VERDAD, Y ES A PROPÓSITO.** `coins_250` entrega **3.000**, `coins_1200`
     * entrega **5.000** y `coins_5000` entrega **10.000** (2026-09-07, decisión del usuario; antes
     * fueron 600/1.200/5.000, y antes de eso 250/1.200/5.000).
     *
     * Los ids no se tocan porque **Play no deja renombrar ni reutilizar el id de un producto,
     * jamás**: cambiarlo significa crear otro, perder su historial y dejar tirados a los que ya lo
     * compraron. Así que la fuente de verdad de **cuánto entra** es esta lista, nunca el nombre del
     * id — y lo que se escribe en la Console (nombre y descripción) tiene que decir la cifra de
     * aquí. Lo fija `StorePacksTest`.
     *
     * ⚠️ **LA ESCALERA DE VALOR ESTÁ INVERTIDA** con los precios de hoy (0,99 / 1,99 / 6,99 $):
     * salen **3.030**, **2.513** y **1.431** teikoins por dólar, o sea que el pack más barato es el
     * mejor trato y el grande el peor. Como los tres son **consumibles** —se pueden repetir sin
     * límite—, a un jugador que eche cuentas solo le compensa el de 0,99 $. Queda escrito aquí para
     * que dentro de tres meses nadie lo lea como un descuido: se avisó y se decidió así. Para
     * enderezarla sin tocar precios harían falta ~7.000 y ~30.000 en los dos de arriba.
     */
    val COIN_PACKS = listOf(
        CoinPack("coins_250", 3000),
        CoinPack("coins_1200", 5000),
        CoinPack("coins_5000", 10000),
    )

    /** El producto del paquete inicial. Debe existir con este id EXACTO en Play Console. */
    const val STARTER_PACK_ID = "starter_pack"

    /** Y el de quitar anuncios, que no da monedas pero comparte pantalla con los que sí. */
    const val REMOVE_ADS_ID = "remove_ads"

    /** Monedas base de [productId], o `null` si no es un pack de monedas. */
    fun coinsFor(productId: String): Int? =
        COIN_PACKS.firstOrNull { it.productId == productId }?.coins

    // ── EL BONO DE BIENVENIDA ───────────────────────────────────────────────────────────────────

    /**
     * Cuántas monedas EXTRA regala [productId] si es su primera compra.
     *
     * El 100 % exacto: comprar 1200 entrega 2400. Es un multiplicador y no una cifra fija para que
     * cambiar el tamaño de un pack no obligue a acordarse de venir aquí a cambiar su regalo.
     */
    const val WELCOME_BONUS_MULTIPLIER = 1

    /**
     * Extra que corresponde a esta compra: el 100 % si el pack **todavía no** ha cobrado su bono, y
     * cero después. Devuelve 0 para cualquier producto que no sea un pack de monedas.
     *
     * @param claimed packs cuya primera compra ya se pagó con bono.
     */
    fun welcomeBonusFor(productId: String, claimed: Set<String>): Int {
        if (productId in claimed) return 0
        val coins = coinsFor(productId) ?: return 0
        return coins * WELCOME_BONUS_MULTIPLIER
    }

    /** Lo que se acredita de verdad por esta compra: base + bono. */
    fun totalCoinsFor(productId: String, claimed: Set<String>): Int =
        (coinsFor(productId) ?: 0) + welcomeBonusFor(productId, claimed)

    /** ¿Le queda a [productId] su bono de bienvenida? Lo usa la tienda para pintar el sello «×2». */
    fun bonusAvailable(productId: String, claimed: Set<String>): Boolean =
        coinsFor(productId) != null && productId !in claimed

    // ── EL PAQUETE INICIAL ──────────────────────────────────────────────────────────────────────

    /** Teikoins que trae el paquete inicial. */
    const val STARTER_COINS = 5000

    /** Y sus días de vidas infinitas. */
    const val STARTER_INFINITE_LIVES_DAYS = 7

    val STARTER_INFINITE_LIVES_MS: Long =
        STARTER_INFINITE_LIVES_DAYS * 24L * 60L * 60L * 1000L

    /**
     * Los dos bloques de pago del juego. **Se nombran por id y no se derivan de `BlockSkins`**
     * porque `BlockSkins` vive en `ui/` (es paleta y forma de dibujo) y esto es economía: el pack
     * tiene que poder probarse sin arrastrar Compose. `StorePacksTest` cruza las dos listas para
     * que no puedan separarse en silencio.
     */
    // P-249 (2026-09-23): el Caramelo se retiró y lo sustituye el CRISTAL — el pack sigue trayendo
    // dos bloques, y el Cristal llega ya comprado aunque su bioma no se haya descubierto todavía.
    val STARTER_SKIN_IDS = listOf("cristal", "fall3d")

    /** El compañero que regala: **Estela**, del Cielo Estelar. */
    const val STARTER_COMPANION_ID = "estrella"

    /**
     * **EL BANCO DE BLOQUES ES DE PAGO** (2026-09-28, decisión del usuario). Viene en el paquete inicial
     * o se desbloquea para siempre con teikoins. El dueño es `starterPackGranted || blockBankPurchased`
     * (las dos viajan en la nube), así que no hace falta un producto nuevo en Play.
     */
    const val BLOCK_BANK_PRICE = 8000

    /** Resumen legible del contenido, para la tarjeta de la tienda y para el reveal. */
    val STARTER_PACK_CONTENT: List<String> = listOf(
        "$STARTER_COINS teikoins",
        "2 bloques: Cristal y Cerámica",
        "$STARTER_INFINITE_LIVES_DAYS días de vidas infinitas",
        "Estela, la compañera del Cielo Estelar",
        "Fichona, para siempre",
    )
}
