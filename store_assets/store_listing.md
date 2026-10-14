# Teika Fusion — Ficha de Google Play

> **Reescrita el 2026-09-03.** La versión anterior (mayo 2026) describía un juego que ya no existe:
> tablero 10×10, power-ups (bomba / ascenso / comodín), minijuego de pelea contra un boss, Hold
> piece, combo con multiplicador y tabla de Play Games. Nada de eso está en la app — unos se
> eliminaron y otros se rechazaron a propósito. **Todo lo que hay aquí abajo está verificado contra
> el código.** Antes de tocar una línea, comprobar que la función sigue viva.

---

## Nombre de la app

`Teika Fusion` *(12 de 30 caracteres)*

## Descripción corta (máx. 80)

```
🧩 Puzzle de bloques: rompe líneas y fusiona Teikas hasta el Mega Teika ✨
```
*(73 caracteres contando los emojis como Play · 2026-10-01: con emojis y sin «MEGA» a secas, que no se
entendía; pedido del usuario: «más vivo y sin abreviar palabras»)*

## Descripción completa (máx. 4000)

> **Reescrita el 2026-10-01**, verificada contra el código: los Teikas nacen de los bloques-Teika (no de
> cualquier línea), los biomas golpean con su reloj y se PARAN (P-347/P-366), el Rey ya no amuralla
> (espada + piezas de ajedrez), banco de bloques (P-349), 28 reglas de la casa, 12 aspectos de bloque.
> Segunda versión el mismo día (pedido del usuario): emojis en cada sección y nada abreviado ni en jerga —
> «8 por 8 casillas», «Teika de nivel 1» en vez de «T1». Sin «gratis», «n.º 1» ni «el mejor» (política
> de metadatos de Play). 2705 caracteres.

```
🧩 Teika Fusion junta dos juegos en uno: colocar fichas en un tablero de 8 por 8 casillas y ver cómo unas criaturas redondas, los Teikas, ruedan, chocan y se fusionan con física de verdad.

✨ CÓMO SE JUEGA

🟦 Arrastra las fichas al tablero y completa filas o columnas para romperlas.
🥚 Algunas fichas traen bloques Teika. Si rompes una línea que lleva uno, de ella nace un Teika: una criatura redonda que cae, rueda y busca a los suyos.
💞 Cuando dos Teikas del mismo tamaño se tocan, se fusionan en uno más grande. Un Teika de nivel 1 más otro de nivel 1 forman uno de nivel 2, y así hasta llegar al Mega Teika, el más grande de todos.
⭐ Cada nivel te pone un objetivo, como recoger estrellas o formar un Mega Teika, y tienes un número de turnos contado para conseguirlo.
🧘 No hay cuenta atrás: puedes pensar cada ficha con calma. Solo en el Volcán conviene no dormirse, porque el magma se calienta si te quedas quieto.

🗺️ 293 NIVELES EN 14 MUNDOS

💎 Caverna de Cristal · 🌙 Bosque del Sueño · 🦇 Pasaje Encantado · 🌸 Jardín · ⛈️ Tormenta · 🌋 Volcán · 🥷 Dojo en Sombras · ❄️ Glaciar · 👻 Mansión Espectral · 🍔 Cocina Voraz · ☢️ Pantano Tóxico · 🌟 Cielo Estelar · 👑 Trono Real · 🪐 Espacio Profundo

Cada mundo tiene su propia amenaza:
🌋 En el Volcán, la lava baja fila a fila.
🥷 En el Dojo en Sombras, una katana parte el tablero en dos mitades.
👑 En el Trono Real, el Rey alza su espada y suelta piezas de ajedrez que bloquean su camino.
🪐 En el Espacio Profundo, los meteoritos convierten tus bloques en roca.
🛡️ Todas las amenazas avisan antes de golpear. Si rompes la línea justa en el turno justo, paras el golpe.

🦸 14 COMPAÑEROS CON PODERES

Cristóbal, Maite, Timoteo, Flora, Rayco, Iñaki, Kenji, Nieves, Morti, Chueta, Toño, Estela, Reynaldo y Galo.
🔓 Los desbloqueas jugando, y cada uno cambia el tablero a su manera.
⚡ Antes de cada nivel eliges a tres y, durante la partida, los lanzas cuando más falta te hagan.

🏦 EL BANCO DE BLOQUES

¿Tienes una ficha que no cabe en ningún sitio? Guárdala en el banco. Con los bloques que vayas ahorrando podrás dibujar tu propia ficha y meterla en la bandeja.

📱 INCLINA EL TELÉFONO

Los Teikas responden a la gravedad. Inclina el teléfono para hacerlos rodar hasta donde los necesitas.

🎁 Y ADEMÁS

🎲 28 reglas especiales que le dan un giro distinto a cada nivel.
🎨 12 aspectos para tus bloques, del Básico al Dorado.
🔥 Racha diaria con recompensas por volver a jugar.
🏆 Clasificación mundial para compararte con jugadores de todo el mundo.
🎓 Siete lecciones para aprender a jugar, que puedes saltar si ya sabes.
☁️ Puedes jugar sin cuenta, o vincular tu cuenta de Google para guardar tu progreso en la nube y no perderlo al cambiar de teléfono.

🧩 Coloca. Rompe. Fusiona. ¿Llegarás al Mega Teika?
```

## Categoría

Juegos → Rompecabezas

## Clasificación de contenido

**Contenido:** Para todos (PEGI 3 / E). Sin violencia, sin texto de usuario visible salvo el nombre
del jugador en la clasificación (limitado a 10 caracteres).

**Público objetivo: 13+** — es una pregunta DISTINTA de la clasificación de contenido y no se puede
responder «todas las edades». Lo fija la política de privacidad («destinada a personas de 13 años o
más»), y declarar público infantil cambiaría las reglas de AdMob y de Analytics (Families Policy).
Las dos respuestas conviven: contenido apto para todos, audiencia declarada 13+.

## Etiquetas / palabras clave

puzzle, fusión, merge, bloques, poliominós, líneas, físicas, casual, sin conexión, niveles

## Declaraciones obligatorias de la Play Console

| Declaración | Valor | Por qué |
|---|---|---|
| **¿Contiene anuncios?** | **SÍ** | AdMob: banner, recompensado e intersticial (ids reales de release) |
| **¿Compras dentro de la app?** | **SÍ** *(desde el 2026-09-06)* | La tienda reabrió y hay **cinco productos alcanzables**, cada uno con su `BillingManager.purchase()` en `GameShopScreen.kt`: `starter_pack` y `remove_ads` (no consumibles) y `coins_250` / `coins_1200` / `coins_5000` (consumibles). **Rango de precios a declarar: 0,99 $ – 6,99 $** |
| **Anuncios recompensados** | 150 teikoins, tope 10/día, enfriamiento 45 s | `AdManager.REWARD_COINS` |
| **Público objetivo** | 13+ recomendado | Hay anuncios y clasificación online. Con público infantil cambian las reglas de AdMob y de Analytics |
| **Política de privacidad** | https://teika-fusion.web.app/privacy-policy | **Publicada y verificada en vivo el 2026-09-07**: describe Google Play Billing y las marcas de compra que viajan a la nube. Comprobado descargando la URL, no solo por el «Deploy complete» |
| **Términos** | https://teika-fusion.web.app/terms | **Publicados y verificados en vivo el 2026-09-07**: la sección 3 cubre los tres tipos de producto |
| **Borrado de cuenta** | En la app: Ajustes → Zona de peligro → Eliminar mi cuenta | Las tres rutas que pide Play están: in-app, web (`/borrar-cuenta`) y correo |

### Seguridad de los datos (formulario) — lo que realmente se recoge

- **Identificadores**: UID de Firebase, y nombre + correo **solo si el jugador vincula Google**.
- **Actividad en la app**: eventos de Firebase Analytics, progreso y marcas del ranking.
- **Diagnóstico**: informes de Crashlytics (dispositivo, sistema, contexto del fallo).
- **Publicidad**: identificador publicitario vía AdMob, con consentimiento UMP en EEE/Reino Unido.
- **Compras**: marcas de sí/no de lo comprado (`adsRemoved`, `starterPackGranted`, `coinBonusClaimed`)
  para restaurarlo en otro dispositivo. **Ningún dato de pago llega a la app**: lo procesa Google Play.
- **NO se pide**: ubicación, contactos, fotos, mensajes, archivos, calendario ni salud.
- Los datos se cifran en tránsito y el jugador puede borrarlos desde la app.

---

## Estado de los assets

| Asset | Tamaño | Archivo | Estado |
|---|---|---|---|
| Ícono | 512×512 PNG | `store_assets/icon_512.png` | ✅ pulido 2026-10-01 · ⬜ subir a Play (Play aún muestra el Teika rojo) |
| Gráfico destacado | 1024×500 PNG | `store_assets/feature_graphic_1024x500.png` (de `tools/icono_derivados.py`) | ✅ rehecho 2026-10-01 · ⬜ subir a Play |
| **Capturas de teléfono** | mín. 2 · recomendado 8 | `store_assets/screenshots/play_1…6.png` (1080×1920, de `tools/ficha_capturas.py`) | ✅ **rehechas el 2026-10-01** del A15, bloque Cerámica, sin el banner de anuncios · ⬜ subir a Play |
| Política de privacidad | URL | `public/privacy-policy.html` | ✅ publicada y al día (5 sep 2026) |
| Términos | URL | `public/terms.html` | ✅ publicados |
| Borrado de cuenta | URL | `public/borrar-cuenta.html` | ✅ publicado |
| Vídeo promocional | YouTube | — | ⬜ opcional |

### Guion de capturas sugerido (8)

Cada una debe enseñar **una** cosa y ser legible en miniatura:

1. **El tablero en calma** — una ficha arrastrándose con su sombra, dos o tres Teikas abajo.
2. **La línea rompiéndose** — el haz recorriendo la fila en el momento del destello.
3. **Una fusión** — dos Teikas tocándose y el resultado naciendo.
4. **El MEGA TEIKA** — llenando el tablero y despidiendo bloques por los bordes.
5. **Un bioma hostil reconocible** — Volcán con la lava alta, o Trono Real con el asedio cerrado.
6. **Selección de nivel** — el mural de un capítulo, que enseña de golpe cuánto juego hay.
7. **La Sala de Preparación** — el cinturón de tres compañeros, para vender la decisión.
8. **La clasificación mundial** — con puestos y rangos poblados.

*(Regla del proyecto: las capturas las toma el usuario en dispositivo. No lanzar la app para esto.)*

---

## ⛔ EL BLOQUEO REAL: 12 verificadores × 14 días (visto en la Console, 2026-09-07)

Antes que ninguna otra cosa de esta lista. El panel de Teika Fusion en Play Console dice:

> **Cómo solicitar acceso a producción**
> ✓ ~~Publica una versión de prueba cerrada~~ *(hecho)*
> ○ Ten al menos **12 verificadores** que acepten participar en la prueba cerrada —
>   *actualmente, **2** verificadores aceptaron participar*
> ○ Ejecuta la prueba cerrada con un mínimo de 12 verificadores durante al menos **14 días**
>
> *(el botón «Solicitar acceso a producción» está DESHABILITADO)*

Es el requisito de Google para cuentas de desarrollador **personales**: no se puede ni *solicitar*
producción sin pasar por ahí. Y el reloj de los 14 días **no corre**: empieza cuando haya 12
verificadores aceptados. Con 2 hoy, faltan **10 personas** y luego dos semanas enteras.

**Esto reordena todo lo demás.** Las capturas, App Check, el `app-ads.txt` y el resto no son el
camino crítico: se pueden hacer durante esas dos semanas. Lo único urgente es **reunir a los diez
verificadores que faltan**, porque cada día que pasa sin ellos es un día que no cuenta.

*(Nota: la prueba cerrada YA está publicada, y eso explica que Play sirva los cinco productos de
pago dentro de la app — verificado en el móvil el 2026-09-07.)*

---

## Pasos que quedan para publicar

*(Reescritos el 2026-09-06 — P-215. La versión anterior era del 2026-09-05 y ya mentía en dos
puntos: daba la app por **sin compras** —la tienda reabrió al día siguiente— y pedía publicar el
mensaje UMP, que al revisar la consola el 2026-09-06 **ya estaba publicado**.)*

1. ~~**Los cinco productos en Play Console.**~~ **HECHO Y VERIFICADO en la Console el 2026-09-07.**
   Los cinco existen, **Activos**, con las cantidades nuevas ya en el nombre —«Compra 3000 Teikoins»,
   «Compra de 5000 Teikoins», «Compra de 10000 Teikoins»— y con su ícono puesto. Comprobado además en
   la opción de compra del paquete inicial: **tipo «Comprar»**, **multi-cantidad DESMARCADA** (con
   ella, comprar tres de golpe acreditaría uno), **contenido digital**, categoría fiscal «Ventas de
   apps digitales», 173 países.

   Se corrigió la **descripción del paquete inicial**, que decía «5.000 teikoins + 7 días de vidas +
   Compañero estela» y **se dejaba fuera los dos bloques de pago** — un tercio de lo que contiene el
   producto que más convierte. Ahora es la aprobada de `play_products.md`.

   ⚠️ **CORRECCIÓN A ESTE DOCUMENTO:** las versiones anteriores pedían «confirmar el tipo
   (consumible / no consumible)» en la Console. **Eso no existe.** En Play Billing un producto único
   no se marca como consumible en ningún sitio: lo decide **la app**, según llame a `consumeAsync`
   (consumible) o a `acknowledgePurchase` (no consumible). `BillingManager` ya lo tiene resuelto con
   su conjunto `NON_CONSUMABLE = {starter_pack, remove_ads}`. No hay nada que tocar en la Console.

2. ~~**Desplegar la política y los términos.**~~ **HECHO el 2026-09-07** (`firebase deploy --only
   hosting`) y **verificado descargando las dos URLs**, no solo por el «Deploy complete» — que es la
   lección de P-199. `CURRENT_LEGAL_CONSENT_VERSION` está en `2026-09-06-v1`, así que la pantalla
   legal le volverá a salir a **todos** los jugadores existentes en cuanto se publique el build.
3. ~~**Capturas.**~~ **HECHAS el 2026-09-07**: cinco, en `store_assets/screenshots/`, tomadas en un
   Samsung A15 real y listas para subir.

   ⚠️ **Este móvil da 1080×2340, y Play RECHAZA eso**: exige que el lado largo no pase del doble del
   corto, y 2340 > 2160. Las capturas en crudo de cualquier móvil moderno —casi todos son más
   alargados que 2:1— **no valen**. Están recortadas a 1080×1800 (y la de partida a 1080×1900), en
   PNG de 24 bits sin alfa. Quien saque más tiene que recortarlas igual.

   Se descartaron dos cosas a propósito: **el banner de anuncio de prueba** que sale en el build de
   debug (una captura de tienda con un «This is a test ad» dentro es un rechazo cantado) y **la
   pantalla de la tienda**, que vende monetización en vez de juego y además enseñaba un precio.

   El orden es: partida → el mundo del Cielo Estelar → los catorce mundos → los compañeros → Inicio.
   Primero lo que se juega, luego cuánto hay, luego lo que se colecciona.
4. ~~**Comprobar el SHA-1 de Play App Signing en Firebase.**~~ **VERIFICADO EN LAS DOS CONSOLAS el
   2026-09-07, y estaba bien.** Las cuatro huellas SHA-1 de Firebase quedan identificadas:

   | Huella SHA-1 | Qué es |
   |---|---|
   | `2d:00:e0:30:c7:ad:5a:39:…:d1:ee:29` | **CLAVE DE FIRMA DE PLAY** — la que re-firma el AAB ✅ |
   | `6a:ab:ec:a2:e9:fd:7a:82:…:42:14:08` | Clave de **subida** (Play Console → Firma de apps) ✅ |
   | `0a:21:8e:c4:a3:42:69:8d:…:ca:00:7c` | Keystore de **debug** ✅ |
   | `55:ef:a2:d8:83:5e:d3:d3:…:99:6a:c6` | Sin identificar — no es ninguna de las tres de arriba; muy probablemente un keystore de debug de otra máquina. Inofensiva, pero se puede quitar |

   El SHA-256 de la clave de firma —`29:3b:aa:6e:14:7d:cd:50:…:1e:17:16:85`, el que aparece en el
   JSON de Digital Asset Links de Play— **también está registrado**. O sea que **«Continuar con
   Google» no se va a romper en producción**: el temor que arrastraba este documento desde el
   2026-09-06 era infundado.

   ⚠️ Nota de método: el SHA-1 de la clave de firma **no se puede leer en la página** de Play, solo
   copiarse al portapapeles. Se obtuvo pegándolo en el campo «Agregar huella digital» de Firebase
   **y cancelando sin guardar**. La versión anterior de este documento, además, llamaba «clave de
   subida» a `55:ef:a2:d8…`, y no lo es.

5. **Vincular la app a la ficha de Play desde AdMob.** Es el bloqueo real que quedó de la revisión
   del 2026-09-06: sin ese enlace la cuenta está en **«publicación limitada»** y los anuncios no
   sirven a pleno. Se resuelve cuando la ficha existe.
6. **App Check: son TRES sitios, no uno.** Está en `UNENFORCED` para que el login funcionara.
   Al publicar hay que activar (a) el enforcement de **Firestore**, (b) el de **Auth** y (c)
   redesplegar las Cloud Functions con `ENFORCE_APP_CHECK` en `true` —
   `functions/index.js:38`, hoy `defineBoolean('ENFORCE_APP_CHECK', { default: false })`.
   Después, comprobar que Auth y Firestore siguen respondiendo.
7. ~~**`app-ads.txt`.**~~ **CONFIGURADO DEL TODO el 2026-09-07**, por los dos lados que hacen falta:
   · el fichero **se sirve** en `https://teika-fusion.web.app/app-ads.txt` (HTTP 200, `text/plain`,
     verificado descargándolo) con el publisher real `pub-5852895823468093`;
   · y el **sitio web ya está declarado en la ficha de Play** (Configuración de la tienda → Detalles
     de contacto), que estaba **vacío** — sin eso el rastreador de AdMob no tiene dónde buscar y el
     fichero no autoriza a nadie. Publicado con «Guardar y publicar».

   Falta solo esperar: AdMob rastrea el dominio por su cuenta y tarda hasta ~24 h en marcarlo como
   encontrado (AdMob → Apps → app-ads.txt).

8. **Subir el AAB a Pruebas internas primero** — `./gradlew.bat bundleRelease` genera
   `app/build/outputs/bundle/release/app-release.aab` (~28 MB, firmado con la clave real). Ahí se
   prueban **las cuatro cosas que no existen en debug**: el login de Google, los anuncios reales,
   **una compra de verdad de cada tipo** (consumible y no consumible, y que reinstalar restaure el
   pack inicial sin volver a entregarlo) y **la pantalla de términos**, que la volverán a ver TODOS
   los jugadores existentes: `CURRENT_LEGAL_CONSENT_VERSION` es `2026-09-06-v1` y se compara por
   igualdad estricta. Guardar el `mapping.txt` junto al AAB.

**Ya NO hace falta** (estaba en versiones viejas de este documento y está hecho): ids reales de
AdMob —los cuatro lo son desde el 2026-08-09—; **el mensaje de consentimiento UMP**, publicado y
verificado en la consola el 2026-09-06 (el código lleva cableado desde antes: `gather()` al
arrancar, `canRequestAds` gateando las tres superficies y la fila de privacidad en Ajustes solo
donde la región la exige); **política, términos y borrado de cuenta redactados y al día**; y crear
productos de Play Games, porque **Play Games no está integrado en la app**: no hay ni logros ni tabla
suya. La clasificación es propia, sobre Firestore.

> ⚠️ **Un fichero en `public/` no es un fichero publicado.** Es lo que dejó cinco semanas una política
> caduca en producción. Cuando cambie el texto legal se mueven **tres** cosas a la vez: el fichero,
> `CURRENT_LEGAL_CONSENT_VERSION` y `firebase deploy --only hosting`.
