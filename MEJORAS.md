# MEJORAS.md — estado VIVO del pulido de Teika Fusion

Índice corto de lo que está vivo **hoy**. Lo que ya pasó no vive aquí:

- **Historial completo hasta 2026-09-22** (las 61 pasadas, P-1…P-244, auditorías, «Hecho»):
  `docs/historial/MEJORAS-hasta-2026-09-22.md` (~1 MB). **No se lee entero**: se consulta con
  `grep` cuando hace falta el porqué de algo (p. ej. `grep -n "P-229" docs/historial/*.md`).
- **Pasadas nuevas**: una entrada por pasada en `docs/historial/AAAA-MM.md` (el mes en curso), no
  aquí. Aquí solo se actualizan las secciones de abajo.
- **Alcance de cada bloque**: `docs/bloques/NN-*.md`. **Reglas de arquitectura**: `CLAUDE.md` (resumen) y `docs/claude/` (detalle).

**Regla de tamaño**: este fichero no pasa de ~300 líneas. Lo cerrado sale en la misma pasada en que
se cierra; si algo necesita más de 10 líneas, su detalle va al historial y aquí queda la línea.

Siguiente id libre: **P-439** (P-363…P-365 descartadas) (P-300 quedó sin usar).

**AUDITORÍA COMPLETA POR SECCIONES (2026-10-01, hecha)** — artifact https://claude.ai/artifact/9a1qf4DfoHFfAKTnz1TxyM
(27 secciones: pantallas, partida y sistemas; colección `secciones` = hallazgos, `revisado` = lo que el usuario ya leyó).
✅ Tanda 1 · pantallas (9/27). Arreglado de oficio: atrás con el carné de perfil abierto cerraba el juego. Propuestas
P-417…P-427 (intro corta, consejos, formato único de cifras, aviso al abandonar partida, barra del mapa, reglas en la
preparación, fila de power-ups, leyenda del Inventario, vitrina desde Inventario/Tienda, puesto del carné). Las dos sospechas «a verificar en el
móvil» (fondo del 103, mapa 95–101) eran FALSA ALARMA: la UI numera sin las 7 lecciones (verificado en el A15 el 01-10). ✅ Tanda 2 · partida (18/27): sin bugs nuevos arreglables de oficio; **bug de diseño a decidir**: empezar otro nivel con
una partida a medias cobra una vida y la borra sin avisar (P-420 ampliada). Propuestas P-428…P-432 (salida y pausa en el HUD,
contrato vista previa↔motor, fichas que no caben en gris, racha desde 2 victorias, «inclina el móvil» en SIN SALIDA).
✅ Tanda 3 · sistemas (27/27) — **AUDITORÍA COMPLETA**. Arreglado de oficio: errata de la pista del Volcán y la ficha de Play
(«no hay cronómetro» era falso: el magma del Volcán va con reloj real). Propuestas P-433…P-438 (pistas en dos capas, medidor de
llenado, poderes recomendados por bioma, capas de GameScreen en tres niveles, cristales explicados, revisar rememberSaveable).
**Decidido y HECHO el mismo día (sin playtest):** ✅ P-420 aviso antes de abandonar una partida al empezar otro nivel (la
regla C-6 no cambia) · ✅ P-418 consejos de carga cada 1,3 s · ✅ P-419 formato único de cifras (`AmountFormat`) ·
racha de victorias desde 2 · «Toca para jugar» con TalkBack · fuera el código muerto de monedas volando de Inicio ·
vitrina recortada a su ventana · ✅ P-438 marca de precarga de proceso · ✅ P-430 fichas que no caben en gris · ✅ P-436
ventanas en su nivel (`GameLayers`, `GameLayersContractTest`) · ✅ P-422 etiqueta de reglas que se despliega · ✅ P-423 fundido
en la fila de power-ups (P-422, P-423 y P-430 **probados por el usuario en el móvil, OK**). ❌ **P-417 (intro corta) DESCARTADA por el usuario: la intro se queda larga.** **Verificado en el A15 (debug):** aviso de abandono (sale con el nivel a medias, Cancelar no cobra)
y «86,9K» en Inicio e Inventario.

**ANÁLISIS A FONDO (flujo «Selección lista del artifacts»)** — 2026-10-01: ✅ Arranque e intro y ✅ Audio y vibración,
las dos en VERDE. Arreglado: la firma sonora y el despertador de audio cargan los primeros (`455b812`); la música se gobierna
desde un solo hilo, y el jingle y el fundido ya no se pisan (`11f558e`). ✅ **Probado por el usuario en el móvil
(2026-10-01): «está todo arreglado».** ✅ Inicio en VERDE (`97dbaf8`): números de nivel del jugador en el
próximo compañero y TalkBack, lluvia de fondo a 30 fps, «Copiar ID» sin ID, avatar elegido con TalkBack. **PEND playtest.** ✅ Mapa de mundos en VERDE (`866fe4f`): reloj a 30 fps en pantallas rápidas,
proporciones de los fondos leídas una vez, El Abismo sin latir con «Reducir efectos». **PEND playtest.** ✅ Preparación del nivel en VERDE (`67328c6`): objetivos legibles
con TalkBack («27 estrellas rojas»). ✅ Inventario en VERDE (`9b2f65c`): la ficha cobra lo que enseña con el %
remoto de mejora, y una mejora rechazada ya no se celebra; plurales. **PEND playtest.** ✅ P-425 (tocar cristales = para qué sirven) y P-426 (salir de la pestaña cierra la ficha) HECHAS. **PEND playtest.** ✅ Flecha verde de «mejora lista» en Lottie (`res/raw/ic_upgrade_arrow.json`, generador en `tools/lottie/`) sustituye al punto rojo en las casillas y la pestaña. **PEND playtest.** ✅ Vitrina de bloques en VERDE (`6d9ca66`): el motor cobra el precio del bloque y no se celebra una compra rechazada; rebote sin recomponer. **PEND playtest.** ✅ P-427 (vitrina también desde el Inventario), P-428 (carrusel centrado en el equipado) y P-429 (moneda en el botón si hay un bloque descubierto que puedes pagar) HECHAS. **PEND playtest.** ✅ Tienda en VERDE (`0d98b60`): la oferta del paquete inicial ya no deja comprar sin cuenta respaldada; aviso de respaldar arriba. **PEND playtest.** ✅ P-430 (sección ASPECTOS que abre la vitrina), P-431 (cifras de la tienda con separador de miles) HECHAS; P-432 (Restaurar compras) RETIRADA el mismo día por decisión del usuario: en Android Play devuelve las compras solo, así que se cambió por reconexión automática con Play (reintento creciente + `BillingManager.refresh()` en cada `onResume`). ✅ Ajustes en VERDE (`49f61b4`): «Restablecer progreso» coherente en memoria y nube; fuera tres ventanas muertas; borrar cuenta dice qué se borra. **PEND playtest.** ✅ P-433 (los usos de power-ups se CONSERVAN al restablecer, decisión del usuario), P-434 (Avisos bloqueados → ajustes del sistema) y P-435 (correo de contacto con ID, versión y móvil) HECHAS. **PEND playtest.** ✅ Clasificación y perfil en VERDE (`811653f`): fuera del top 50 el puesto es el real (hasta 100; «100+» después), no «#51» para todos. **PEND playtest.** ✅ P-436 (desempate por XP, puestos compartidos), P-437 («visto hace n días» en el carné) HECHAS; P-438 (`getMyRank`) HECHA y DESPLEGADA el 2026-10-01 con OK del usuario. ✅ HUD superior en VERDE (`a7d9974`): el vídeo de monedas se reintenta si no cargó y el cupo se renueva a medianoche con la partida abierta. **PEND playtest.** ✅ P-439 (salida en botón propio, marcador aparte), P-440 (icono de pausa ❚❚ en vez del engranaje) y P-441 (vídeo «cargando» con arco que gira) HECHAS. **PEND playtest.** ✅ Panel de objetivo en VERDE (`616906b`): el tablero ya no salta al formar el Mega; aviso rojo y destello sin recomponer. **PEND playtest.** ✅ P-442 (★ delante de la meta de puntos), P-443 (barrita de progreso bajo cada contador; el panel crece ~6 dp, también en el hueco de las lecciones) y P-444 («¡SOLO FALTA EL MEGA!» flota sobre su chip) HECHAS. **PEND playtest.** ✅ Tablero en VERDE (`bb2db6a`): la vista previa de la Tormenta usaba 4 bobinas fijas y no miraba Teikas (el motor, 4–7 según nivel y nunca con Teika) → `StormCircuit` fuente única + contrato. **PEND playtest.** ✅ P-445/P-446 (detector de líneas único `model/LineDetector` para motor y vista previa + test de equivalencia en 6000 tableros) y P-447 (decisión del usuario: los niveles «sólidos» —1 de cada 6— llevan una fila de acento cada tres; era eso, no la Cerámica) HECHAS. **PEND playtest.** ✅ Panel de fichas en VERDE (`0e40f97`): bug de la captura del usuario —ficha que se quedaba «levantada» a tamaño de tablero sobre su cuadro si el gesto se cortaba a medias— con `finally` en el gesto + efecto de rescate + contrato. **PEND playtest.** ✅ P-448 (la ficha sube 3,4 celdas sobre el dedo al salir de la bandeja y baja de forma continua a 1,6 en lo alto del tablero), P-449 (↻ en los cuadros hasta que el jugador gire una ficha; pref local `rotate_learned`) y P-450 (retorno de 280 ms con posición y tamaño sincronizados y rebote discreto; corregido 2026-10-07) HECHAS. **PEND playtest.** **PEND playtest.**

**PROPUESTAS PARA MÁS ADELANTE: arte con Photoshop (2026-10-01)** — Photoshop conectado por MCP (relleno y ampliación
generativos, quitar fondos, lotes con CSV, exportar). El usuario las aparca: primero, la ficha de Play.
- **P-413 · Material para redes**: el arte de la ficha adaptado a 1:1, 9:16 y 16:9 de una vez, para anunciar la salida.
- **P-414 · Imágenes del juego a su tamaño, a mano** (complementa P-377): murales, `settings.webp` e imágenes de
  tienda reescaladas y en WebP, revisando cada una a ojo.
- **P-415 · Arte de compañeros y biomas**: fondos limpios, color igualado entre los 14 biomas, variantes generativas.
- **P-416 · Variantes del icono**: sombras, brillo y versiones de temporada sobre `icon_1024.png`.

---

**INTRO PROFESIONAL (2026-09-30)** — elegidas las 4 propuestas; presupuesto «~3 s + toque». Detalle en `docs/historial/2026-09.md`.
- ✅ **P-372 · Pantalla de título** · ✅ **P-373 · Carga con alma** · ✅ **P-374 · Firma «CUKITI»** ·
  ✅ **P-375 · Arranque coherente** — HECHO: `SplashScreen.kt` en 4 fases (estudio → carga → título → salida).
  Firma a gusto del usuario (`CukitiLogoReveal`): UNA línea de color por letra entra arrastrándose desde su lado y se
  pliega hasta ser su letra (~2,5 s); el color se va al fondo, la palabra se acerca y se desvanece. Splash nativa AZUL con el icono (como antes, pedido del usuario):
  la firma funde solo el azul. Intro medida y pulida en un A15 (release): reloj propio (pausa con la pantalla
  apagada), CUKITI acaba en negro y ahí se monta el menú, carga ≥2 s, título único con barrido de luz. **PEND playtest** (y en release:
  sobre todo el fluir de los halos en gama baja). Firma sonora propia ✅ (`snd_intro_sting`, v1).

**PROPUESTAS ABIERTAS (2026-09-30, tras pulir la intro)** — presentadas; el usuario no eligió ninguna todavía.
- **P-376 · Música más ligera**: el APK release pesa 75 MB y 50 MB son las 15 `bgm_*` (~170 kbps, ~2,5 min). A ~112 kbps
  el APK bajaría ~20–25 MB. Coste: script + escucha del usuario antes/después.
- **P-377 · Imágenes a su tamaño**: como los `ic_ability_*` del Inventario (1024 px vistos a ~280 px): `settings.webp`
  1254 px/509 KB, 14 murales 1080×1920, `store_*`. Menos memoria y tirones al abrir pantallas, ~5 MB. Nada visible.
- **P-378 · Medir entrada a partida**: extender las marcas `Intro`/detector de tirones a mapa→nivel, victoria/derrota y
  vuelta a Inicio; medir en el A15 (con OK) y arreglar solo lo que salga.
- **P-379 · Primera experiencia**: medir tiempo hasta la primera ficha de un jugador nuevo (intro ~7 s + «Todo listo
  para jugar» + tutorial) y proponer recortes concretos.

**AUDITORÍA DE FLUIDEZ POR BIOMA (2026-09-29, en curso)** — por bloques con commit; de oficio solo lo invisible.
✅ Bloque 1 (skins + ficha fantasma + VFX comunes): caché de bloques en movimiento y halo del fantasma pre-desenfocado
(detalle en `docs/historial/2026-09.md`). ✅ Bloque 2 (Cristal, Sueño, Pasaje, Jardín): fondo del Cristal quieto, capas
propias en el tablero y la bandeja. ✅ Bloque 3 (Tormenta, Volcán, Dojo, Glaciar): magma y lava sin trabajo por
fotograma, overlays de celda cacheados. ✅ Bloque 4 (Mansión, Cocina, Tóxico): veneno sin 160 objetos por fotograma.
✅ Bloque 5 (Estelar, Trono, Espacio):
el Estelar ya no recompone el tablero a cada tick de física. **AUDITORÍA CERRADA** (5 commits, todo invisible).
**PEND playtest** con el contador. **Propuestas (cambian lo que se ve o el comportamiento → decide el usuario):**
**Fichona (2026-10-08)** — sustituye el taller automático: una mega ficha conectada de hasta 16 bloques en la mayor zona, reciclando la mano y conservando máscaras/sobrantes; poderes completos deben jugarse primero. Mantiene coste/peaje/20 turnos. Evento desde los bordes, revelación y entrega; arrastre pesado con chispas. Tablero activo después del evento: suelta inválida rompe/desvanece sin reembolso ni otro turno y repone bandeja; cancelaciones conservan. Verificado: 958 pruebas correctas y una omitida, capturas de fabricación/revelación, compilación Kotlin debug/release y lint vital release. **PEND playtest en dispositivo/equilibrio.**

- ✅ **P-368 · Ambiente de bioma a 30 fps en pantallas de 90/120 Hz** — HECHO 2026-09-30: `biomeClockSec` y el
  fondo (`BiomeBackdrop`, ya sin `rememberInfiniteTransition`) avanzan a pasos de 1/30 s si la pantalla va a más de
  60 Hz (`rememberAmbientStepNs`, `ambientNs`); a 60 Hz, igual que antes. **PEND playtest** (que el ambiente no se vea a saltos).
- ✅ **P-369 · «Reducir efectos» automático** — HECHO 2026-09-30: en partida, 6 s seguidos por debajo de 40 fps
  (tras 3 s de calentamiento; huecos >250 ms no cuentan) → lo activa, avisa arriba y no lo repite en ese aparato
  (`JankWatch`, `JankWatchDriver`, `onSustainedJank`, clave local `auto_reduced_fx_done`). **PEND playtest**.
- ✅ **P-370 · La rotura del cristal agrupada** — HECHO 2026-09-30: `BatchCrystalShatterVFX`, un lienzo y un reloj
  para todas las cáscaras (antes un Canvas a pantalla completa por casilla). Mismo dibujo. **PEND playtest**.
- ✅ **P-371 · Contador de FPS en Ajustes** — HECHO 2026-09-30: interruptor «Contador FPS» en Ajustes y en la pausa,
  visible directamente en debug y release (2026-10-07). En
  memoria (no se guarda), pintado encima de todo en `AppNavigation`.

**MÚSICAS INTEGRADAS (2026-10-11) — cuenta nueva ElevenLabs Creator.** Quince composiciones originales
big band/swing/ragtime, con identidad propia por bioma; el usuario aprueba composición y regreso natural de todas.
Menús ~2:29; biomas ~1:54–2:06. Se conservan íntegros al convertir las vistas previas aprobadas a OGG, sin
fragmentos, recortes o fundidos de reparación. [Entrega y verificaciones](docs/audio/integracion-musical-2026-10-11.md).
Los otros 131 recursos permanecen; Teikas conserva fusión transpuesta y sus cues. Quince recursos pasan prueba
de decodificación/encadenado en A15; usuario aprueba mezcla inicial de menús y partida de Cristal o Sueño.
**PEND:** mezcla individual de otros biomas y evidencia particular de promoción. Criterios Indie/uso individual confirmados por usuario;
[evaluación comercial](docs/audio/licencias/musica-creator-2026-10-11.md). Renovación anterior en `docs/historial/2026-09.md`.

**EFECTOS BASE v2 (2026-10-11):** [revisión por usuario/R3](docs/audio/efectos-base-revision-usuario-2026-10-11.md):
clic más suave/seco, colocar conserva carácter, fusionar más elástico/redondo. Limpiar se define para Básico, sólido pulido,
seco/compacto; las variantes siguen el bloque equipado. Quince fuentes completas del primer bloque conservadas.
Usuario aprueba los cuatro efectos: S02 R3 y S01/S03/S04 R4 con repetición/finales naturales y medidas reveladas.
[Cuatro WAV íntegros integrados](docs/audio/efectos-core-r4-integracion-2026-10-11.md), sin síntesis propia, recortes o nuevas generaciones.
**PEND:** escucha contextual Android y evidencia particular de promoción SFX. No extender aprobación final a otros materiales.

[Lista completa v2](docs/audio/lista-completa-audio-v2-2026-10-11.md): 82 efectos pendientes de 132 perceptibles;
15 músicas y 50 efectos integrados, silencio técnico excluido;82 pendientes ninguno en corrección. [Tanda grande:15 propuestas](docs/audio/efectos-tanda-grande-revision-2026-10-13.md): Morti/Chueta/Toño/Estela/Reynaldo/Galo, compra/cobro, desbloqueo/derrota/victoria/vida perdida/sin vidas/rango/racha diaria.21 fuentes completas,15 seleccionadas,6 retenidas técnicas sin rechazo humano; no aprobadas/integradas. Vida perdida R3 final−49,8dBFS señalado; Reynaldo firma de activación sin pop1300 certificado; Galo R2 máximo RMS1140 vs impacto nominal1040. Medidas/banderas conservadas, sin edición;148 assets/registro intactos. Revisor visible15 tarjetas,45 hashes/15frames/JS/32 rutas/30patrones180plays simulados correctos;11 unitarias pasan. Sin APK/instalación/prueba física nuevos; APK previo listo por preferencia vigente. Evidencia de promoción particular pendiente.

**Hecho hoy (sin playtest):** Trono (pieza se queda, 2 piezas por espada, parada en la diana, calma 0 → 63 %),
Tóxico capa de veneno (64 %), Dojo media fila con Teika, Volcán reloj de lava arreglado + calma 1 (67 %; la regla de
columnas se probó y se devolvió), Sueño/Pasaje/Glaciar un escalón más fáciles (79/75/75 %). **34 efectos de Stable
Audio Open integrados** (`4dca851`), elegidos por medición: **falta escucharlos** (primero kintsugi y rescate, muy cortos).
**Pendiente:** campaña completa del bot con 4 partidas (se canceló), **recompilar el AAB v27** (el de hoy no lleva
los efectos nuevos), y revisar cambios ajenos sin commit en `app/build.gradle.kts` y `gradle-wrapper.properties`.

**DECISIONES DEL USUARIO (2026-09-29, repreguntadas con su impacto)** — ✅ las tres hechas el mismo día:
- ✅ **P-362** · el dial de madera pasa a tabla (`CHAPTER_WOOD_BONUS`), mismos números (`a7ba98a`).
- ✅ **P-366** · el bot PARA los golpes y echa la pieza del Rey (`parryForNextPlacement`, zonas compartidas con el
  tick). Paradas por partida: Volcán 0 → 3,5, Espacio 1,6 → 2,9, Mansión 1,5 → 2,6, Dojo 0,9 → 1,7 (`56f3b99`).
- ✅ **P-367 · curva cerrada con el bot que para** (`CHAPTER_CALM`): Sueño 5/3, Mansión 2/2, Glaciar 2/0, Cocina
  1/0. Campaña final `campana-0929-p367b` (655 partidas, 0 anomalías, 71 %): **12 de 14 capítulos en ±5**;
  Estelar +6 y Tormenta +5 (sin ciclo) en el borde del ruido. Cristal 87, Sueño 83, Pasaje 76, Jardín 75,
  Tormenta 81, Volcán 73, Dojo 75, Glaciar 65, Mansión 68, Cocina 65, Tóxico 64, Estelar 66, Trono 62, Espacio 55.
  Reglas más duras con este bot: Espejo 40 %, Cola forzada 50 %. **PEND playtest** de la curva.
- **Descartadas**: Teikas T2 por condiciones, más estrellas en las fichas, perilla o retoque de las reglas de la casa.

✅ *Documento hecho (28-09):* el artifact «Dificultad de Teika Fusion» (https://claude.ai/artifact/JLQ9itYNiRDPPEw1t3k5aa) recoge todas las palancas, los 14 biomas y las 28 reglas con su %.

**Sonidos (2026-09-28):** reemplazar los 17 que no son de ElevenLabs y cubrir 16 momentos mudos o prestados, con
efectos de *Explorar* descargados con la Starter **antes del 13-10** (cancelación programada). Lista y búsquedas en
`docs/audio/pendientes-sonidos-2026-09-28.md`. **✅ Integrados** (Stable Audio Open vía Colab): 18 sustituidos + 16
nuevos cableados. **PEND playtest de oído**: elegidos por medición, no escuchados; los muy cortos (kintsugi 0,15 s,
rescate 0,12 s) son los primeros a revisar.

*Cabos sueltos:*
- **Consola**: añadir el banco a la descripción de `starter_pack` en Play.
- **Playtest pendiente**: reparto nuevo, Jardín con la raíz arreglada, Glaciar, escombro del Trono, dos
  bloques-Teika del Cristal, la sección de reglas de la pausa y las cápsulas nuevas.


---

## Convenciones

- **Idioma**: identificadores en inglés; comentarios, KDoc y texto visible en español.
- **Iteraciones grandes pero verificables**: varios frentes del MISMO bloque por pasada, cada uno
  verificado por separado. Lo que no quepa con calidad se anota para la siguiente.
- **Toda auditoría PROPONE**: mínimo 3 propuestas `P-nn` (qué / por qué ahora / coste / decisión),
  presentadas en el panel de selección múltiple. **No se implementan de oficio**: mecánica,
  balance y economía los decide el usuario. Un bug evidente no es propuesta: se arregla.
- **Al cerrar una propuesta, se anota EN SU LÍNEA** (y sale de «Abiertas»). El registro viejo llegó
  a dar por abiertas 32 propuestas de las que 16 estaban hechas porque solo se anotaba en la pasada.
- **Verificación** antes de cerrar: `:app:compileDebugKotlin` → `testDebugUnitTest` → `lint`.
- **No se lanza la app ni se hacen capturas** sin permiso explícito del usuario cada vez.
- **Balance** (tablero, puntaje, umbrales, economía) no se toca salvo bug sin ambigüedad.
- **Git (desde 2026-09-22)**: **cada pasada termina en commit**, con el árbol verde. Nunca más
  semanas de trabajo sin commitear: con el árbol commiteado, `git diff`/`git restore` vuelven a ser
  red de seguridad y no una amenaza. Sigue prohibido `git stash`/`checkout` sobre trabajo SIN
  commitear de otro agente (Codex/Gemini pueden estar trabajando en el mismo árbol).
- **Contrato de eventos**: exactamente un sitio consume cada evento (ver `CLAUDE.md`).

---

## Decisiones de diseño que NO se tocan

Ganan siempre, sobre cualquier plantilla, referencia de mercado o propuesta. El porqué de cada una
está en el historial y en la memoria del proyecto.

**Núcleo y derrota**
- **Se puede perder** (2026-09-07): fuera `replaceUnplaceableTrayPieces`; la ficha condenada se apaga en
  ámbar (P-229). **El giro volvió** (P-276, 2026-09-24): un toque en la bandeja, libre; el segundo dedo no.
  **Excepción: el HORIZONTE** (P-286, 2026-09-25): su única ficha se cambia al cerrar el turno si no
  cabe en ninguna rotación.
- **Derrota = 3 causas** (`BoardDeadEnd`): Tilt, Cast (solo con carga de un power-up **elegido para la
  partida** que `freesSpace`) o derrota real.
- **Un solo motor** (line-clear). Un Teika bloquea las casillas que pisa su círculo (el bloqueo de
  fila/columna del T4 y el MEGA ya no existe en el código; comprobado 2026-09-28).
- **Los Teikas nacen T1** (2026-09-26): solo una jugada de 2+ líneas que rompa 2+ bloques-Teika cría
  uno grande (el menor de los dos números, techo T3). Ninguna otra vía cría T2/T3. **La portería, borrada.**
- **El reparto es PREDECIBLE** (P-314, 2026-09-26): la bandeja cabe ENTERA (junto a lo que queda en la
  mano) y trae lo más grande que quepa; el tamaño depende SOLO del tablero (sin sesgos de capítulo, tipo
  de nivel, sabor, fundir ni reposición). Los Teikas cuentan donde se van a asentar. Única excepción: la
  pareja de gemelas pequeñas de los niveles de fundir.
- **Victoria = estrellas + MEGA**. EL PEDIDO se borró (2026-09-22): sin condición extra de criar Teikas.

**Biomas y variedad**
- **UNA sola mecánica ambiental por nivel** (la del bioma actual; `biomeSecondary` borrado). **Excepción
  (P-329, 2026-09-26, decisión del usuario): el Trono Real** alterna en el MISMO reloj la espada y una
  pieza de ajedrez (torre, alfil o caballo; nunca las dos a la vez, nunca rey/reina/peones).
- **Reloj de bioma**: aviso en la 2ª colocación, castigo en la 3ª, sin excepciones; solo romper
  LÍNEAS calma (la fusión no). **El reloj se CONGELA** (2026-09-23, `BiomeClock`): romper una línea en
  un turno de aviso no lo apaga ni lo avanza; solo romperla JUSTO en el turno de ejecutar lo cancela.
- **Todo evento de bioma en 3 fases** (telegrafía → impacto → consecuencia), tope 1200 ms.
- **UNA regla de la casa por nivel** (`HouseRule`), catálogo del rework 2026-09-22 (P-231…P-244).
  Las 13 reglas viejas no vuelven.
- **El pasaje huye en cada colocación y puede romper la línea que completas**: decidido.
- **No se pierde por turnos** (2026-09-23): sin límite de colocaciones; las únicas derrotas son quedarse
  sin sitio y la lava. Estrellas **por puntuación**; sin contador en pantalla. **El Trato, retirado.**

**Visual y feel**
- **T4+T4 = COLAPSO ESTELAR** (2026-09-24, levanta el viejo «MEGA sin flash, anillos ni rótulo»): ceremonia
  única de 1,1 s (espiral, vacío, singularidad, `snd_mega_collapse`) y nacimiento con fogonazo, ondas, rayos y
  «¡MEGA TEIKA!». El empuje físico sigue siendo el corazón. Reglas en `model/MegaCollapse.kt`.
- **Sin contador de combo persistente, sin multiplicador de combo, sin misiones diarias, sin confeti.**
  (Excepción pedida por el usuario: el confeti de 4 colores de la cápsula del bloque-Teika, P-285.)
- **Todos los bloques de la vitrina son CUADRADOS** (2026-09-23; silueta del Clásico, esquina ≤ ~0,14): la
  personalidad va en la CARA (dibujo plano, reposo animado, colocación, rotura y sonido). Los 5 colores se
  distinguen y la estrella/cronómetro se lee sobre cualquier cara; «reducir efectos» = cara estática.
- **Ghost solo si la ficha cabe** (nunca en rojo). **La madera no hace squash** al aterrizar.
- **⛔ Nada de `Dialog`/`Popup`/`AlertDialog`/`ModalBottomSheet`/`DropdownMenu`**: `ConfirmOverlay`.
- **Ajustes sin sliders**: todo ON/OFF.

**Economía y cuenta**
- Tienda reabierta (2026-09-06): 5 productos IAP; **precios solo desde Play**; bono 100 % solo en la
  primera compra; vidas fuera de la tienda (`LivesOverlay`).
- `remove_ads` quita lo forzado, **nunca el rewarded**.
- Google es el único login (Facebook no vuelve).

**Sistemas borrados**: ver `docs/claude/sistemas-borrados.md` (Infinito, giro con segundo dedo, reparador de bandeja,
auto-pop, barra de energía, hold, barco, power-ups…). No se reintroducen sin decisión nueva.

---

## Bloques de trabajo

Cada pasada elige UN bloque; **el 0 (bugs) manda siempre**. Alcance, archivos, invariantes y tests de
cada uno en `docs/bloques/`.

| # | Bloque | # | Bloque |
|---|---|---|---|
| 0 | Bugs transversales | 14 | Progresión del jugador |
| 1 | Pantalla de juego y HUD | 15 | Cristales |
| 2 | Núcleo del turno | 16 | Tienda, economía y Billing |
| 3 | Contenido de celda | 17 | Anuncios (AdMob) |
| 4 | Teikas y física | 18 | Vidas, racha y notificaciones |
| 5 | Bandeja e input | 19 | Cosméticos |
| 6 | Habilidades Teika | 20 | Animaciones |
| 7 | Mejoras de habilidades | 21 | VFX y contrato de eventos |
| 8 | Compañeros | 22 | Audio y háptica |
| 9 | Biomas | 23 | Pantallas y navegación |
| 10 | Submecánicas de bioma + reglas de la casa | 24 | Ajustes y accesibilidad |
| 11 | Modo Niveles | 25 | Online y persistencia |
| 12 | ~~Infinito~~ (borrado 2026-08-09) | 26 | Build, release y rendimiento |
| 13 | Progresión y estructura | | |

---

## Propuestas abiertas

Solo lo que espera una decisión o una medida. Lo cerrado hasta P-286 (con su detalle, cifras del bot y
commits) está en `docs/historial/2026-09.md`, sección «Propuestas cerradas sacadas de MEJORAS.md»; lo que
de ello falta jugar está en «Pendiente de PLAYTEST».

Registros de las pasadas del 25 al 28-09 (curva sin banco, rendimiento, decisiones del 27-09, fichas pequeñas,
pedidos del 25-09) sacados el 2026-09-28 a `docs/historial/2026-09.md`, sección «Sacado de MEJORAS.md (2026-09-28)».
Lo que de ellos falta jugar: `grep -n "PEND playtest" docs/historial/2026-09.md`.

## Bugs abiertos

Tres que encontró el detector de anomalías del bot (campaña 2026-09-26, con semilla):
- ✅ **B-1 · Madera dentro de un Teika** — arreglado 2026-09-27. Al nacer, el Teika esquiva los cuerpos
  desplazándose por la línea, pero no miraba la madera que SOBREVIVE al aplastado (blindada: cristal —en
  fichas de todos los biomas desde P-307— y roca; protegida: hielo, escarcha, cristal golpeado). Ahora la
  esquiva también, y si la línea entera está tapada busca el hueco libre más cercano. Jardín 62-76 con el
  bot, 45 partidas: cero anomalías (antes el 69 tenía una).
- ✅ **B-2 · «Atasco» en el nivel 14** — era del BOT, no del juego: guardaba toda la partida las jugadas
  que `placePiece` rechazó una vez (un Teika que rueda entre la comprobación y la colocación) y la clave
  ignoraba la rotación. Con una ficha en la mano se quedaba sin jugadas. Ahora el veto dura un turno.
- ✅ **B-3 · Las semillas no reproducían la partida** — la física usaba la hora del sistema para las
  gracias y la cadencia de fusiones (y las habilidades repartían fichas con `Random.Default`). Nuevo reloj
  de simulación en `TeikaPhysicsWorld` (`clockMs`, avanza con cada paso). Comprobado: el nivel 205 jugado
  dos veces con la misma semilla da la misma partida.
**AUDITORÍA DE ONBOARDING Y FEEDBACK (2026-09-27)** — tutorial, apertura de nivel y feedback de acciones.
Detalle en `docs/historial/2026-09.md`. Nuevo `TutorialLessonsPlayTest`: juega las 7 lecciones contra el
motor real (antes ningún test lo hacía).
- ✅ **B-6 · La lección 2 NO se podía terminar** — pedía un T2 con un solo bloque-Teika y desde el
  2026-09-26 eso da un T1: el jugador se quedaba atascado para siempre. Ahora pide un T1 («CREA UN TEIKA»,
  decisión del usuario); crecer fundiendo lo enseña la 3.
- ✅ **B-7 · El remate «Y así nace el Mega» de la lección 4 no salía nunca** (el MEGA cierra el nivel por la
  ceremonia, que no pasa por el remate). Borrado (decisión del usuario).
- ✅ **B-8 · El panel del nivel solo nombraba 3 de las 28 reglas de la casa** — ahora cada regla trae su
  palabra (`HouseRule.badge`).
- ✅ **B-9 · La lección 7 prometía «power-ups siempre bajo la bandeja»**, y el botón solo sale con los que
  se eligen antes del nivel. Nota nueva: «Elige tus power-ups antes de cada nivel y lánzalos con el rayo».
- ✅ **B-10 · Guardar en el banco cerraba el turno sin cápsulas**: un golpe de bioma en ese turno se
  quedaba sin explicar. Ahora pasa por `atTurnEnd`.
- ✅ **B-5 · El cartel «SIN SALIDA» tapaba el banco** — arreglado 2026-09-27 (reporte del usuario). El cartel
  solo se retiraba al coger una ficha, abrir los power-ups o cambiar el estado de la partida; tocar el
  botón del banco abría el taller o la selección DEBAJO del cartel. Ahora también lo retiran el taller, la
  selección del banco, la cinemática de un lanzamiento y el diálogo de salida (`interacting` en
  `GameScreen`). Vuelve a salir al intentar colocar otra vez con el tablero trancado. PEND playtest.

Deuda técnica menor, sin urgencia:
- **Contraste** — halo de la luna del bioma MIEDO bajo la cartera del HUD; confirmar en dispositivo.
- **Lint**: 38 avisos, casi todos subidas de dependencias y el orden del parámetro `modifier` (no se
  tocan sin decisión).
- `res/drawable-nodpi/tutorial_hand_teika.png` (0,8 MB) ya no la usa nadie; el shrinker la deja fuera del
  AAB, así que solo pesa en el repositorio.

**Tamaño del AAB (revisado 2026-09-25):** 46,8 MB, de los que **9,7 MB son el mapa de R8**
(`BUNDLE-METADATA/…/proguard.map`, sirve para desofuscar y Play no lo descarga al jugador). Lo que viaja:
música 15,4 MB (las tres pistas largas de 180 s —juego, Sueño, Cristal— son 8,4), código 8,4 MB, imágenes
11,4 MB (los 14 fondos pintados del mapa, ~4,3 MB, son lo único grande añadido desde el 2026-09-22). No
se coló nada: los recursos sin usar ya se quedan fuera. La cifra de 33,7 MB no tiene fecha ni build
conocidos, así que no se puede comparar pieza a pieza.

---

## Pendiente de PLAYTEST (lo que el código ya tiene y solo el usuario puede juzgar)

**2026-09-25 (noche): el usuario revisó TODO lo pendiente y está correcto** — incluidos P-288…P-295, la
espada del Rey y el meteorito con la mira. Lista vaciada. Sale de ese playtest lo de P-296…P-299 (abajo).

- **Auditoría de onboarding 2026-09-27** (B-6…B-10, P-354…P-357, todo aprobado por el usuario): la lección 2
  se termina y pide un T1; cápsulas nuevas «Toca una ficha para girarla» (al abrir el NIVEL 1), «Rompe lo
  marcado al caer: ¡parada!» (tras el primer golpe de un bioma con ciclo) y «Guardar cuesta un bloque» (al
  guardar por primera vez); la selección del banco dice el peaje; el panel del nivel nombra su regla de la
  casa; la pausa tiene «¿CÓMO FUNCIONA ESTE NIVEL?» (bioma, regla, banco y giro, `LevelRules`).
- **Decisiones 2026-09-27** (P-310, P-315, P-316, P-318, P-345, P-350): el banco aparece desde NIVEL 16 con
  su cápsula (y ya no en el Cristal); la bandeja tras una doble línea de la MISMA ficha brilla y trae fichas
  más grandes; con el tablero vacío salen también fichas de 6; el aviso de rotura sigue vivo con Teikas
  rodando.
- **P-296…P-299** (hechos el mismo día, falta jugarlos): botones de power-ups alineados, bloques que
  conservan su diseño al moverse, «∞» fijo en el chip de vidas, Inventario en dos paneles.
- ✅ **JUGADO Y APROBADO 2026-09-27** · **P-349b · El banco: más feedback, tocar para guardar e INDUCCIÓN** (2026-09-27, pedido del usuario):
  encima del banco la ficha se encoge y se inclina hacia él, un haz animado los une, la sombra del
  tablero se apaga, el botón crece y previsualiza latiendo las casillas que llenará con «+N» (o tiembla en
  rojo con LLENO / NO CABE / ESPERA). Tocar el banco VACÍO oscurece la pantalla y levanta las fichas de la
  bandeja para elegir (las que no se pueden, apagadas y con el motivo); el taller tiene «GUARDAR OTRA
  FICHA». Las dos vías terminan en la inducción: carga de luz, bloques que vuelan en curva a SU casilla
  acelerando, girando y con estela, remolino en el botón, destello por llegada, estallido y «+N». Jugar:
  que se lea «va al banco» antes de soltar y que la inducción no se sienta lenta (≈ 0,6 s + 60 ms/bloque).
- ✅ **JUGADO Y APROBADO 2026-09-27** · **P-349 · EL BANCO DE BLOQUES** (2026-09-27, diseño del usuario): arrastra una ficha al botón de abajo
  a la izquierda y el banco guarda sus bloques (cuenta como turno); tócalo y dibuja tu ficha en una rejilla
  de 3×3 (máx. 9 bloques, color de la última guardada) que entra en la bandeja o sustituye a una (la
  sustituida se descarta). Tras construir, 10 turnos de enfriamiento (botón bloqueado entero, con cuenta
  atrás). Es salida del cartel «SIN SALIDA». Jugar: el llenado bloque a bloque, el resalte verde/rojo al
  arrastrar encima, el taller (pintar/borrar), la sustitución y que el botón quede simétrico al de power-ups.
- **PLAYTEST 2026-09-27 (hecho el mismo día):** (1) la pieza de ajedrez cabe en SU casilla (antes ~2×2
  y fuera del tablero); (2) espada, pieza y meteorito aparecen sin corte (Lottie precargado y en fase,
  `BiomeLottie`); (3) sin el destello en diagonal de piezas y espada; (4) **P-347** Rey y Meteoritos siguen
  el reloj de la casa: romper en un aviso ya NO los cancela, solo en el turno del golpe; (5) **P-348**
  castigos de al menos 2 turnos: Maite 1 → 2, Glaciar 1 → 2, calle de la pieza 1 → 2. Jugar el Trono
  Real, el Espacio Profundo, el Sueño y el Glaciar. Ojo: P-348 endurece tres biomas que se habían
  suavizado por el bot (P-312, P-254) — si pesan demasiado, se compensa en el reloj, no en la duración.
- **B-4 · El bloque Metal no salía en la vitrina** ✅ arreglado 2026-09-27: existía (P-285) pero la
  vitrina solo listaba los fijos y los de bioma. Ahora va tras la Cerámica, a la venta desde el inicio
  (Legendario 6.000). Mirar su tarjeta, comprarlo, equiparlo y jugar una rotura con él.
- **P-333…P-340 (rendimiento de la partida, 2026-09-26)**: nada debería verse distinto; lo que se juzga
  es la FLUIDEZ al colocar, al romper líneas y en combos. Mirar además: el brillo del comodín en la
  bandeja, el desenfoque del tablero al abrir los power-ups, la ficha fantasmal y las llamas de Iñaki.
  Idealmente con el «Contador FPS» de Ajustes (P-371; visible también en release).
- **P-329**: Trono Real — un golpe sí y otro no, una pieza de ajedrez (torre, alfil o caballo) en vez de la
  espada: entrada, cámara lenta, esquirlas y la calle bloqueada una colocación. Mirar sobre todo el caballo
  en el aire y que la calle rayada se lea como «aquí no».
- **P-324**: Jardín — la raíz deja enredadera por donde arrastra, y brota de un borde si no hay Teika.
- **P-323**: Dojo — el kintsugi deja bloques de oro en la costura; la grieta dura más y vuelve antes.
- **P-322**: Pantano Tóxico — el veneno podrido queda como lodo y salpica a un vecino.
- **P-321**: Cielo Estelar — la Luna deja caer de vuelta lo que roba; un turno menos de respiro.
- **P-319**: Espacio Profundo — la metralla del meteorito, la roca que se agrieta y la lluvia doble del
  final del capítulo.
- **P-314**: fichas según el espacio (vacío → solo grandes), la bandeja cabe entera, los Teikas recién
  nacidos ya no engañan al reparto.
- Constelación y Avalancha conservan su renovación de presentación y juego. Bola demoledora, Pinball Teika y Compactadora sustituyen Dominó, Trampolín y Corte preciso. Puestos de nivel conservados; pendiente comprobar ritmo, hápticos, audio y rendimiento en móvil. Reactor de color sustituye Estela; Garra Teika sustituye Ascensor; Forja Teika sustituye Onda expansiva; las diez sustituciones acordadas están implementadas.
  Las catorce sustituciones están implementadas (14/14). Reserva permite un intercambio gratis
  por bandeja sin descartar; Portal doble permite intercambiar dos zonas 2×2. Rutas está retirada
  y conserva el uso de compañero ya ganado, sin recargar inventario. Retiradas independientes de poderes de ficha pendientes. Ver C06/C07/C08/C11.
- Relevo — Descarga: barrido automático con anchura real de la ficha/Fichona, vista previa, carriles detenidos por protecciones y expulsión de madera; conserva recorrido e IDs al guardar. Pendiente prueba física del ritmo/audio/hápticos. Turbina Teika sustituye Deslizamiento; Forja Teika sustituye Onda expansiva.
- Pinball Teika sustituye Trampolín: lanzamiento manual de un Teika real, seis rebotes/tres segundos, rotura protegida por materiales y encuentro/fusión normales. Carga y disparo persisten; pendiente prueba física de gesto, ritmo, sonido, hápticos y FPS. Ver C07/C08/C11/C21.
- Antes de cada subida a Play sigue valiendo el **ritual de release** de `CLAUDE.md` (build release en un
  móvil real: cápsulas, notificación, login Google con App Check, anuncio y compras reales).

---

## Pendiente de CONSOLA (fuera del código)

- **Vincular la app a su ficha de Play en AdMob** — hoy «publicación limitada». Es el bloqueo real.
- **Descripción de `starter_pack` en Play Console** (P-249): ya no trae el Caramelo. Texto nuevo en
  `store_assets/play_products.md` («los bloques Cristal y Cerámica»).
- **Descripción de `coins_1200` (la bolsa de 5.000)**: dice «los dos bloques de pago juntos» y ya hay más.
  Texto en `store_assets/play_products.md`.
- **Reactivar App Check en ENFORCED** al publicar, cuando haya tráfico verificado de Play Integrity.
- **Screenshots de la ficha**: hay 5 en `store_assets/screenshots/`; confirmar que están subidas.
- **Mirar `startup_ready` en Analytics** cuando haya jugadores.

---

## Siguiente prioridad sugerida

1. **Playtest sobre el release** de la lista de arriba. Sin él, cualquier pasada nueva apila más cambios
   sin validar sobre los que ya hay.
2. **Bloque 0 con lo que salga de ese playtest.**
3. **P-287, la curva por capítulos**, con lo que el playtest diga de la dificultad.
4. Si no hay reportes: auditoría de las **reglas de la casa** (ficha en `docs/bloques/10b-reglas-de-la-casa.md`):
   es el código más nuevo y el único sin ninguna auditoría posterior.

- Compactadora sustituye Corte preciso: carga por línea, apuntado con vista previa y compresión manual hasta primer obstáculo, sin turno/tick adicional ni autorrecarga. Conserva bloques y guardados antiguos. Pendiente prueba física de gesto, ritmo, audio, hápticos y FPS; C07/C11/C21.

- Portal doble sustituye Rutas: una línea carga el intercambio manual de dos zonas 2×2, con vista previa exacta y confirmación. Portales absorben/materializan bloques completos, respetan protecciones y no cobran otro turno ni se autorrecargan. Migración conserva recompensas antiguas ya ganadas. Pendiente prueba física de selección, ritmo, audio, hápticos y FPS; C07/C11/C21.

- Referencias visuales pendientes de revisión: 19 comparaciones de OverlayScreenshotTest y una de apoyos de Avalancha difieren de sus goldens; se observaron referencias anteriores al botón FPS y al título vigente de la lección 2. Las cuatro capturas de Portal doble sí están verificadas; no se han regenerado los goldens ajenos.

- Reactor de color sustituye Estela: una línea carga una reacción manual en grupo de tres o más bloques del mismo color; vista previa/confirmación, propagación ramificada, estrellas y semillas conservadas, sin otro turno ni autorrecarga. Selección y resultado guardado verificados con regresiones/capturas; pendiente prueba física de tacto, ritmo, audio, hápticos y FPS. Ver C07/C11/C21.

- Bola demoledora sustituye Dominó: carga por línea, arco manual de 90° desde un borde con vista previa/confirmación, rotura de madera y frenado completo ante protecciones. Conserva estrellas/semillas y resultado guardado, sin turno adicional ni autorrecarga. Pendiente prueba física de gesto, ritmo, audio, hápticos y FPS; C07/C11/C21.

- Garra Teika sustituye Ascensor: una línea carga recoger/transportar/soltar un cuerpo real, con selección y destino por radio, confirmación, cable/pinzas y aterrizaje. Sin turno extra ni destrucción; conserva interrupciones y migra viajes antiguos. Pendiente prueba física de gesto, ritmo, audio, hápticos y FPS; C08/C11/C21.

- Giro orbital sustituye Cometa: zona de 3×3, giro de 90° elegido, vista previa exacta, aro con agarre/giro/encaje y controles fijos. Carga por línea, conserva bloques y especiales; migración 10. Pendiente prueba física de gesto, ritmo, audio, hápticos y FPS; C07/C11/C21.

- Turbina Teika sustituye Deslizamiento: apuntado de cono, corriente física según masa durante 2 s, protecciones/pausa/guardado, controles fijos y efectos ligados al reloj del motor; migración 11. Pendiente validar gesto, audio, hápticos y FPS en Samsung; C08/C11/C21.

- Forja Teika sustituye Onda expansiva: carga por línea, cuatro maderas conectadas → un T1, selección/destino/vista previa/confirmación, vuelos al crisol y nacimiento con luz. Estrellas cobradas una vez, semillas y protecciones conservadas, controles fijos; migración 12. Pendiente validar ritmo, tacto, audio, hápticos y FPS en Samsung; C07/C08/C11/C21. Las diez sustituciones solicitadas ya tienen implementación.
