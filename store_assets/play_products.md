# Productos de pago — alta en Google Play Console

> **Escrito el 2026-09-06 (P-215).** Todo lo de aquí está **verificado contra el código**:
> `BillingManager.kt` (los ids), `model/StorePacks.kt` (lo que se entrega) y
> `GameViewModel.INFINITE_LIVES_PACKS` / `LevelBudget.REVIVE_COST_TEIKOINS` / `ui/BlockSkins.kt`
> (los precios en teikoins con los que se calibra). Si cambia cualquiera de esos, esta hoja miente.

**Los ids son inmutables.** Play no deja borrar un producto ni reutilizar su id jamás. Un id mal
escrito es un producto muerto para siempre, y en la app se manifiesta como **una tarjeta que no
aparece, sin ningún error a la vista**: `GameShopScreen` pinta lo que Play le devuelve.

Según el KDoc de `StorePacks`, **cuatro ya estaban dados de alta** (los tres de monedas y quitar
anuncios): esos hay que **verificarlos y activarlos**, no crearlos. El único **nuevo** es
`starter_pack`.

---

## Los cinco productos

### 1. `starter_pack` — **creado el 2026-09-06**

| Campo | Valor |
|---|---|
| **ID del producto** | `starter_pack` |
| **Nombre** (≤55) | `Paquete Inicial · 5.000 teikoins + 7 días de vidas` *(50)* |
| **Descripción** (≤200) | `Cinco mil teikoins, los bloques Cristal y Cerámica, siete días de vidas infinitas y Estela, la compañera del Cielo Estelar. Se compra una sola vez y vuelve contigo si reinstalas.` *(178)* · ⚠️ **PENDIENTE de cambiar en Console** (P-249, 2026-09-23): antes decía «los dos bloques de pago», que dejará de ser verdad con los bloques de bioma |
| **Tipo** | Producto único · **NO consumible** (compra única, no se puede volver a comprar) |
| **Precio** | **2,99 $** · ✅ **puesto en Console** (2026-09-06) |
| **Estado** | Activo |

*Contenido real (`StorePacks.STARTER_PACK_CONTENT`):* 5.000 teikoins · los bloques **Cristal** y
**Cerámica** (el Caramelo se retiró el 2026-09-23, P-249) · 7 días de vidas infinitas · la compañera **Estela**.

### 2. `remove_ads` — verificar

| Campo | Valor |
|---|---|
| **ID del producto** | `remove_ads` |
| **Nombre** (≤55) | `Quitar los anuncios para siempre` *(32)* |
| **Descripción** (≤200) | `Se acabaron los banners y los anuncios entre niveles. Los vídeos que dan teikoins siguen ahí, solo si tú los pides. Pago único: se restaura al reinstalar o al cambiar de móvil.` *(177)* |
| **Tipo** | Producto único · **NO consumible** |
| **Precio** | **4,99 $** · ✅ **puesto en Console** (2026-09-06) |

> ✅ **Resuelto el 2026-09-06:** los recompensados **sobreviven a la compra**. Antes
> `setAdsRemoved(true)` soltaba también el vídeo voluntario, así que pagar te quitaba de paso hasta
> 1.500 teikoins diarios gratis. Ahora `AdManager` tiene dos interruptores —`forcedAdsEnabled` para
> banner e intersticial, `rewardedEnabled` para el vídeo— y lo vigila `AdSurfacesContractTest`. La
> descripción de arriba **puede** prometerlo, y lo hace: es el argumento de venta que faltaba.

### 3. `coins_250` — **entrega 3.000 monedas, no 250**

| Campo | Valor |
|---|---|
| **ID del producto** | `coins_250` |
| **Nombre** (≤55) | `3.000 teikoins · el puñado` *(26)* |
| **Descripción** (≤200) | `Tres mil teikoins: seis rescates, un bloque de pago con cambio de sobra, o un día entero de vidas infinitas. Tu primera compra de este pack viene con el doble: 6.000.` *(166)* |
| **Tipo** | Producto único · **consumible** (se puede volver a comprar) |
| **Precio** | **0,99 $** · ✅ **puesto en Console** (2026-09-06) |

> **NINGÚN id dice lo que entrega, y es deliberado** (decisión del usuario, 2026-09-07). No se
> puede arreglar: **Play no deja renombrar ni reutilizar el id de un producto, jamás**, y crear otro
> significaría perder su historial y dejar tirados a los que ya lo compraron. La fuente de verdad de
> cuánto entra es `StorePacks.COIN_PACKS`, nunca el nombre del id — lo fija `StorePacksTest`. **El
> nombre y la descripción que se escriben en la Console deben decir 600**, que es lo que el jugador
> recibe; la tarjeta de la app ya lo hace sola (`ShopCard(title = "${pack.coins} Teikoins")`).

### 4. `coins_1200` — **entrega 5.000 monedas, no 1.200**

| Campo | Valor |
|---|---|
| **ID del producto** | `coins_1200` |
| **Nombre** (≤55) | `5.000 teikoins · la bolsa` *(25)* |
| **Descripción** (≤200) | `Cinco mil teikoins: los dos bloques de pago juntos, o dos días de vidas infinitas, o diez rescates. Tu primera compra de este pack viene con el doble: 10.000.` *(158)* |
| **Tipo** | Producto único · **consumible** |
| **Precio** | **1,99 $** · ✅ **puesto en Console** (2026-09-06) |

### 5. `coins_5000` — **entrega 10.000 monedas, no 5.000**

| Campo | Valor |
|---|---|
| **ID del producto** | `coins_5000` |
| **Nombre** (≤55) | `10.000 teikoins · el cofre` *(26)* |
| **Descripción** (≤200) | `Diez mil teikoins, el pack grande: para no volver a pensar en monedas en una buena temporada. Tu primera compra de este pack viene con el doble: 20.000.` *(152)* |
| **Tipo** | Producto único · **consumible** |
| **Precio** | **6,99 $** · ✅ **puesto en Console** (2026-09-06) |

---

## Por qué esos precios

**Lo que vale un teikoin, medido en el propio juego.** Esto es lo que decide si un precio parece
justo o un robo: el jugador no compara con otros juegos, compara con lo que ya conoce.

| Cosa | Cuesta | Dónde vive |
|---|---|---|
| Revivir un nivel | 500 | `LevelBudget.REVIVE_COST_TEIKOINS` |
| Bloque **Cristal** | 2.500 (Estándar) | `ui/BlockSkins.kt` (el Caramelo, 2.000, se retiró y se devuelve — P-249) |
| Bloque **Madera** | 2.500 (Estándar) | `ui/BlockSkins.kt` (P-251 2/7, 2026-09-25; se descubre al terminar el Jardín) |
| Bloque **Lava** | 4.000 (Premium) | `ui/BlockSkins.kt` (P-251 3/7, 2026-09-25; se descubre al terminar el Volcán) |
| Bloque **Hielo** | 4.000 (Premium) | `ui/BlockSkins.kt` (P-251 4/7, 2026-09-25; se descubre al terminar el Glaciar) |
| Bloque **Espectro** | 4.000 (Premium) | `ui/BlockSkins.kt` (P-251 5/7, 2026-09-25; se descubre al terminar la Cocina Voraz) |
| Bloque **Constelación** | 6.000 (Legendario) | `ui/BlockSkins.kt` (P-251 6/7, 2026-09-25; se descubre al terminar el Cielo Estelar) |
| Bloque **Vacío** | 6.000 (Legendario) | `ui/BlockSkins.kt` (P-251 7/7, 2026-09-25; se descubre al terminar el Espacio Profundo) |
| Bloque **Cerámica** | 3.000 | `ui/BlockSkins.kt` |
| Vidas infinitas 1 h / 6 h / 24 h | 200 / 900 / 2.500 | `GameViewModel.INFINITE_LIVES_PACKS` |
| **Gratis: un anuncio** | +150, tope 10/día = **1.500/día** | `AdManager.REWARD_COINS` |

**La escalera de valor** (monedas base, sin el bono de primera compra):

La regla es una sola: **cada peldaño tiene que dar más teikoins por euro que el anterior.** Si uno de
abajo da más, nadie sube — y como los packs de monedas son **consumibles**, ese peldaño se puede
repetir sin límite, así que no es un desajuste de escaparate sino una fuga. El **bono de bienvenida**
(`StorePacks.WELCOME_BONUS_MULTIPLIER`) dobla además la primera compra de cada pack, y el sello «×2»
hace el trabajo de convencer las tres primeras veces.

**⚠️ ESTADO ACTUAL: LA ESCALERA ESTÁ INVERTIDA** (cantidades del 2026-09-07, precios del 2026-09-06):

| Pack | Precio | Entrega | Teikoins por dólar | |
|---|---|---|---|---|
| `coins_250` | 0,99 $ | 3.000 | **3.030** | el más barato es el **mejor** trato |
| `coins_1200` | 1,99 $ | 5.000 | 2.513 | |
| `coins_5000` | 6,99 $ | 10.000 | **1.431** | el más caro es el **peor** trato |

Está avisado y decidido así, pero conviene tenerlo escrito con las cuentas delante: **cada peldaño da
menos por dólar que el anterior**, justo al revés de lo que empuja a subir. Y como los tres son
**consumibles** —se repiten sin límite—, a un jugador que eche cuentas solo le compensa comprar el de
0,99 $ una y otra vez. Los otros dos existen para quien no las eche.

También cambió la generosidad en bruto: 3.000 teikoins por 0,99 $ son **seis rescates** o **un día
entero de vidas infinitas**, cuando el pack de entrada anterior (600) no daba ni para dos rescates.
Todo lo que se paga con teikoins acaba de abaratarse cinco veces, y el techo diario de anuncios
—1.200 con el cupo nuevo de 8— tarda dos días y medio en igualar el pack más barato.

**Para enderezarla sin tocar un solo precio** haría falta subir los dos de arriba a ~**7.000** y
~**30.000** (3.518 y 4.292 por dólar). Es un cambio de dos cifras en `StorePacks.COIN_PACKS`.

**El paquete inicial a 2,99 $, y por qué tan barato.** Su contenido vale, en la moneda del propio
juego, unos **27.500 teikoins**: 5.000 + 5.000 de los dos bloques + 17.500 de los siete días de vidas
infinitas (7 × 2.500), más una compañera. Puesto en teikoins por dólar: el paquete inicial ronda los
**9.200**, y el cofre grande —el mejor pack de monedas— se queda en **715**. Ese desnivel **es el
producto**: el paquete inicial no está
para hacer caja, está para convertir a alguien que nunca ha pagado en alguien que ya ha pagado una
vez —el salto más difícil de toda la monetización—, y la conversión de un pack inicial se cae en
picado por encima de los 3 €. Se compra sin pensarlo, que es literalmente lo que se le pide.

**Lo que hay que aceptar al ponerlo ahí:** quien compre el paquete inicial y después mire el cofre de
5.000 a 6,99 $ va a notar la diferencia. Es el precio de usar el pack como gancho, y es lo normal
del género — pero conviene decidirlo con los ojos abiertos. Si prefieres que no chirríe, **4,99 $**
es el otro punto razonable para el pack inicial, y vende bastante menos.

---

## Economía: las dos cuestiones que abrió esta hoja, resueltas

1. ~~**El pack de 250 no llega para un rescate.**~~ **RESUELTA el 2026-09-06:** el usuario subió el
   pack a **600 monedas**. Revivir cuesta 500, así que ahora el pack pequeño cubre un rescate entero
   y sobra cambio. El id se queda en `coins_250` porque Play no deja cambiarlo.
2. ~~**Quitar anuncios apaga también los recompensados.**~~ **RESUELTA el 2026-09-06:** el vídeo
   voluntario sigue funcionando después de comprar. Lo que se vende es no ser interrumpido, no
   perder la forma gratuita de ganar teikoins.

---

## El resto de campos de la Console, para los cinco

- **Ícono**: 512×512 PNG de 32 bits. Opcional, pero sin él la tienda de Play enseña un hueco gris.
  Prompts abajo.
- **Precios**: usa una **plantilla de precios** por producto en vez de país por país, y deja marcado
  que Play ajuste los precios locales automáticamente.
- **Países/regiones**: todos, salvo que quieras excluir alguno.
- **Categoría fiscal**: *contenido digital estándar* (los teikoins son bienes digitales).
- **Compra múltiple** (`multi-quantity`): **desactivada** en los tres consumibles. La app acredita
  una compra por evento (`BillingManager` lee `purchase.products.firstOrNull`), no la cantidad — con
  compra múltiple activada, comprar 3 packs de golpe acreditaría **uno**.
- **Estado**: Activo. Un producto en borrador no lo devuelve `queryProductDetails` y su tarjeta no se
  pinta.
- **Traducciones**: si la ficha se publica en más idiomas, cada producto lleva las suyas.

### Dos trampas de la primera vez

1. **Los productos no existen para la app hasta que hay un AAB en un canal.** Sube el
   `app-release.aab` a **Pruebas internas** antes de esperar que la tienda se pinte, y dale unas
   horas: la propagación no es inmediata. Una tienda vacía la primera tarde es normal.
2. **Prueba sin pagar de verdad**: Play Console → *Configuración → Prueba de licencias* → añade tu
   cuenta de Google. Compra, verifica que la moneda entra, y **reinstala** para comprobar que el
   paquete inicial se restaura **sin volver a entregar** su contenido (`starterPackGranted`).

---

## Prompts para los íconos (512×512)

⚠️ **Corregidos el 2026-09-06 tras abrir el arte del juego.** La primera versión pedía «monedas
doradas con una espiral estampada» y describía un estilo sobrio sobre azul noche. **Las dos cosas
eran falsas.** El teikoin es una **moneda dorada con cara sonriente** —dos ojos negros, boca abierta,
borde estriado, dos destellos— y el estilo del juego es **pegatina de dibujos animados**: contorno
azul marino grueso, filo blanco por fuera, color plano saturado y destellos amarillos de cuatro
puntas.

### Sube SIEMPRE la referencia

En **`art/_referencias/`** están los PNG listos para arrastrar al generador:

- **`teikoin.png`** — para los cuatro iconos que llevan monedas. **Sin esto el generador inventa una
  moneda genérica** y el icono deja de ser de este juego.
- **`corazon_lleno.png`** — para el paquete inicial, que incluye vidas infinitas.
- **`icono_anuncio.png`** — para `remove_ads`, y de ahí sale también la estrella violeta.

Y añade al final del prompt:

> La imagen de referencia contiene **la moneda de este juego**. Reprodúcela exactamente como está:
> misma forma, mismo dorado, mismo borde estriado y **la misma cara sonriente**. No la rediseñes, no
> la estilices y no la sustituyas por una moneda genérica con símbolo. El resto del icono es nuevo.

### Estilo base — pégalo delante de cada uno

> Icono de producto para juego móvil casual, **estilo pegatina de dibujos animados**. Composición
> centrada, un solo grupo de objetos legible en miniatura. **Contorno azul marino muy oscuro, grueso
> y uniforme, con un filo blanco fino por fuera.** Color plano y saturado con degradado suave y
> brillo alto arriba. Formas gruesas y redondeadas. **Destellos amarillos de cuatro puntas**
> alrededor. El conjunto va apoyado sobre una **placa cuadrada de esquinas muy redondeadas**, de
> color liso con borde exterior más oscuro. 512×512, sin texto, sin letras, sin números, sin marca
> de agua.

### Y el objeto de cada producto

1. **`starter_pack`** — «Un cofre del tesoro abierto de par en par, visto de tres cuartos,
   desbordando **las monedas sonrientes de la referencia**; sobre ellas flotan, más pequeños, un
   corazón rojo brillante con el símbolo de infinito y una estrella violeta de cinco puntas. Rayos
   de luz dorada saliendo del cofre. Placa dorada. Sensación de regalo enorme y de una sola vez.»
   · **Ref:** `teikoin.png` + `corazon_lleno.png`.
2. **`remove_ads`** — «Una tira de película con un botón de reproducción, desintegrándose en
   partículas de luz por una esquina, con un aura verde de alivio. Placa verde.» · **Ref:**
   `icono_anuncio.png`. *No uses un prohibido rojo: lo que vende esta compra es calma, no negación.*
3. **`coins_250`** — «Un montoncito de cinco o seis **monedas de la referencia**, apiladas y
   ligeramente abiertas en abanico para que se cuenten de un vistazo, con la de delante enseñando su
   cara. Placa azul. Se tiene que leer como *un puñado*, no como un tesoro.» · **Ref:**
   `teikoin.png`.
4. **`coins_1200`** — «Una bolsa de tela abierta, panzuda, atada con un cordón, rebosando **las
   monedas de la referencia**; unas cuantas caen por el borde congeladas en el aire. Placa violeta.
   El doble de abundante que un montoncito, sin llegar a cofre.» · **Ref:** `teikoin.png`.
5. **`coins_5000`** — «Un cofre de madera y oro entreabierto del que sale una montaña de **las
   monedas de la referencia**, desbordando por los dos lados hasta llenar el encuadre. Placa dorada
   con destellos violetas y azules en los bordes. Máxima abundancia.» · **Ref:** `teikoin.png`.

*(Que sea **la misma moneda** en los cuatro es lo que los hace leerse como una escalera de un mismo
juego, y no como cuatro imágenes de banco de imágenes.)*
