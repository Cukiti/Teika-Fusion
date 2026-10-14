package com.cukiti.teikafusion.model

/**
 * **LAS REGLAS DE LA CASA** (2026-09-03, pedido del usuario: «que cada vez que el jugador juegue un
 * nivel sienta diferencia»).
 *
 * Una regla de la casa es una mecánica **de nivel, no de bioma**: vale en cualquier capítulo, convive
 * con la mecánica ambiental del bioma sin tocarla y cambia *cómo* se juega el tablero, no cuánto
 * cuesta. La gravedad (P-80), el horizonte (P-75), la corriente (P-79), los testarudos (P-71) y las
 * gemelas (P-76) fueron las cinco primeras; este fichero es la casa donde viven todas.
 *
 * ## El problema que resuelve
 *
 * Cada una se repartía con **su propio módulo** —7, 11, 13, uno por capítulo, 5— y esquivaba a las
 * demás con una cadena de negaciones encadenadas que ya había fallado una vez (P-128: seis niveles
 * con dos mecánicas encima porque un eslabón se quedó suelto). Medido el 2026-09-03 sobre los 293
 * niveles, ese reparto dejaba **154 de 286 niveles sin ninguna regla**: el 54 % del juego se jugaba a
 * tablero pelado, y esa es exactamente la sensación que el usuario reportó.
 *
 * Y había un segundo agujero, este invisible: **ninguna regla escalaba**. El índice de capítulo solo
 * se usaba para elegir *qué nivel* llevaba la regla, nunca *con qué fuerza*. La corriente embestía
 * cada 4 turnos igual en el Cristal que en el Galáctico y el horizonte enseñaba las mismas 3 fichas
 * futuras en el bioma 1 y en el 14.
 *
 * ## Cómo reparte
 *
 * **Exactamente una regla por nivel** (decisión del usuario, 2026-09-03), y la exclusión mutua deja
 * de ser una cadena de negaciones para ser una consecuencia de la aritmética: si el reparto devuelve
 * UNA entrada del enum, no hay forma de que dos mecánicas caigan en el mismo nivel. Es la tabla única
 * que pedía P-130.
 *
 * El recorrido es `(capítulo · [CHAPTER_STEP] + posición · [LEVEL_STEP]) % nº de reglas`, con los dos
 * pasos **coprimos con el tamaño del catálogo**. Eso da las tres propiedades que hacen falta:
 *
 *  - **Determinista.** Mismo nivel → misma regla, siempre. Se DERIVA del número de nivel y **no se
 *    serializa**, así que ningún guardado migra (misma decisión que [LevelDirector] y [LevelVariety]).
 *  - **No repite dentro del capítulo** mientras el catálogo sea mayor que el capítulo más largo (22
 *    niveles). Con [LEVEL_STEP] coprimo, las 22 primeras posiciones caen en 22 reglas distintas.
 *  - **Dos capítulos vecinos no abren igual**, porque [CHAPTER_STEP] también es coprimo y los 14
 *    capítulos arrancan en 14 puntos distintos del catálogo.
 *
 * ## Cómo escala
 *
 * [intensityFor] devuelve **0f en el primer capítulo y 1f en el último**, y cada regla traduce ese
 * número a su propio dial: la corriente embiste cada 4 turnos y acaba embistiendo cada 2, el
 * horizonte enseña 3 fichas del futuro y acaba enseñando 1, los testarudos van de 3 bloques a 5. La
 * misma regla, más apretada — que es lo que el usuario pidió: «cada vez que el jugador pase al
 * siguiente bioma cada una de las mecánicas se volverán más difíciles».
 *
 * El dial es **por capítulo y no por nivel** a propósito: dentro de un bioma la regla se comporta
 * igual en sus 22 niveles, así que el jugador puede aprenderla. Es la misma razón por la que los
 * turnos de aviso del bioma son FIJOS desde el 2026-08-30.
 *
 * ## La regla de oro de este fichero
 *
 * **Una entrada del enum es una mecánica que EXISTE.** Nada de declarar el catálogo entero y cablear
 * la mitad: un nivel al que le toque una regla que no hace nada es un nivel peor que el de hoy, y es
 * la trampa que este proyecto ya conoce (`usesNewEngine`). Lo vigila `HouseRuleContractTest`, que
 * exige un consumidor real para cada entrada.
 */
enum class HouseRule(
    /**
     * **La cápsula que la enseña.** No es decoración: es lo que convierte «una regla en cada nivel»
     * en algo jugable en vez de desconcertante. Una mecánica que cambia el tablero y no se nombra la
     * primera vez que aparece no es variedad, es un tablero que se porta raro.
     *
     * Que el enlace viva aquí —y no en un `when` suelto— es lo que permite a `HouseRuleContractTest`
     * exigir que **toda** regla del catálogo tenga quien la explique, igual que
     * `GameEventContractTest` exige un productor para cada evento.
     */
    val capsule: MechanicCapsule,
    /**
     * **Su nombre en el panel del nivel** (2026-09-27, auditoría de onboarding): una o dos palabras
     * pegadas al nombre del nivel, el único sitio donde el jugador se entera ANTES de colocar. Hasta hoy
     * el panel solo sabía nombrar la gravedad, el horizonte y la corriente: las otras 25 reglas entraban
     * sin anunciarse. Vive aquí para que una regla nueva no pueda nacer sin nombre.
     */
    val badge: String,
) {
    // ── Las cinco fundacionales (P-71, P-75, P-76, P-79, P-80) ──────────────────────────────────
    GRAVITY(MechanicCapsule.GRAVITY, "GRAVEDAD"),
    HORIZON(MechanicCapsule.HORIZON, "HORIZONTE"),
    CURRENT(MechanicCapsule.CURRENT, "CORRIENTE"),
    // 2026-10-09: replacement in the former Testarudos ordinal; do not reorder.
    DOMINO(MechanicCapsule.DOMINO, "BOLA DEMOLEDORA"),
    RELAY(MechanicCapsule.RELAY, "RELEVO"),

    // ── Familia A · el tablero se mueve (2026-09-03) ────────────────────────────────────────────
    // Las tres primeras son la MISMA mecánica que sus fundacionales leída desde otro borde: comparten
    // solver y solo cambian de ejes. Ver [BoardGravity.Pull] y [BoardCurrent.Axis].
    SIDE_GRAVITY(MechanicCapsule.SIDE_GRAVITY, "GRAVEDAD LATERAL"),
    INVERTED_GRAVITY(MechanicCapsule.INVERTED_GRAVITY, "GRAVEDAD INVERSA"),
    VERTICAL_CURRENT(MechanicCapsule.VERTICAL_CURRENT, "CORRIENTE VERTICAL"),
    // Replaces Noria in the same ordinal; moves adjacent rows after a real horizontal clear.
    SLIDE(MechanicCapsule.SLIDE, "TURBINA TEIKA"),
    TIDE(MechanicCapsule.TIDE, "MAREA"),

    // ── Familia B · cómo te llegan las fichas (2026-09-03) ──────────────────────────────────────
    // Ninguna toca el tablero: cambian el REPARTIDOR. Las seis primeras viven en `TrayRules.kt`;
    // la ficha espejo es la excepción y vive en [MirrorPiece], porque no toca el reparto sino la
    // colocación. Todas comparten la frontera que ninguna cruza: la bandeja siempre admite jugada.
    RESERVE(MechanicCapsule.RESERVE, "RESERVA"),
    FORCED_QUEUE(MechanicCapsule.FORCED_QUEUE, "EN ORDEN"),
    INSTANT_REFILL(MechanicCapsule.INSTANT_REFILL, "REPOSICIÓN"),
    ROUTES(MechanicCapsule.ROUTES, "PORTAL DOBLE"),
    // Compactadora acts on the board; keep its former tray-rule ordinal for saved levels.
    PRECISION_CUT(MechanicCapsule.PRECISION_CUT, "COMPACTADORA"),
    MIRROR(MechanicCapsule.MIRROR, "ESPEJO"),
    // Constelación replaces Molde in its existing campaign slot.
    CONSTELLATION(MechanicCapsule.CONSTELLATION, "CONSTELACIÓN"),

    // ── Familia C · lo que hace la línea al romperse (2026-09-04) ───────────────────────────────
    // La familia de más riesgo: es la única que toca `resolveLineClearPass`, el único motor vivo.
    // Las cuatro actúan DESPUÉS de que el motor haya decidido qué se limpia: la cruz añade un
    // barrido, la cosecha añade cuerpos, el eco añade puntos y el rebote empuja los cuerpos.
    // Ninguna cambia qué cuenta como línea — UN SOLO EJE lo hacía y por eso se retiró el
    // 2026-09-05 (petición del usuario); la sustituye [AVALANCHE]. Ver `LineRules.kt`.
    // Forja Teika replaces Onda/Barril without changing ordinal 17; Avalancha keeps Retroceso's slot.
    // [ChainReaction] y [LineRecoil] permanecen como helpers inactivos de compatibilidad.
    SHOCKWAVE(MechanicCapsule.SHOCKWAVE, "FORJA TEIKA"),
    ELEVATOR(MechanicCapsule.ELEVATOR, "GARRA TEIKA"),
    // (2026-09-22) LA IMPLOSIÓN sustituye a EL ECO. Ver [LineImplosion].
    IMPLOSION(MechanicCapsule.IMPLOSION, "IMPLOSIÓN"),
    AVALANCHE(MechanicCapsule.AVALANCHE, "AVALANCHA"),

    // ── Familia D · cómo se comportan los cuerpos (2026-09-04) ──────────────────────────────────
    // Puro game feel: no cambian una sola regla del juego, solo cómo se mueven los Teikas. Son el
    // contrapeso del catálogo — hacen falta niveles donde el jugador se sienta MÁS poderoso, o el
    // conjunto se lee como dificultad en vez de como variedad. Ver `TeikaFeelRules.kt`.
    // (2026-09-05, petición del usuario) EL NACIMIENTO TARDÍO salió del catálogo y lo sustituye
    // [MAGNET]: era la regla más cara del juego —cola serializada, buffer con tres productores y dos
    // tests de contrato— y su efecto era un contorno punteado esperando. El imán cuesta una fuerza en
    // el integrador y su efecto ES lo que se dibuja. Con ella se fueron `PendingBirth`,
    // `GameState.pendingBirths` y el evento `BirthAnnounced`.
    // (2026-09-22, petición del usuario) Rework de dos de ellas: los PESADOS pasan a ser DE PLOMO
    // `LEAD_TEIKAS` —el golpe de una caída larga aplasta el bloque de debajo— y el IMÁN deja de ser
    // una fuerza pasiva entre parejas para ser una FICHA que el jugador coloca ([MagnetPiece]).
    // (2026-09-22) LAS POMPAS sustituyen a TEIKAS LIGEROS. Ver [TeikaBubbles].
    BUBBLES(MechanicCapsule.BUBBLES, "POMPAS"),
    TRAIL(MechanicCapsule.TRAIL, "REACTOR DE COLOR"),
    // Trampolín replaces the magnet piece; no magnetic power is dealt anymore.
    TRAMPOLINE(MechanicCapsule.TRAMPOLINE, "PINBALL TEIKA"),
    // (2026-09-22) LA BRÚJULA sustituye a LA INCLINACIÓN LIBRE. Ver [Compass].
    COMPASS(MechanicCapsule.COMPASS, "BRÚJULA"),

    // ── Familia E · las marcas del tablero (2026-09-04) ─────────────────────────────────────────
    // Reaprovechan el vocabulario de «celda marcada» que ya existe y le dan significados nuevos.
    // Ver `BoardMarkRules.kt`.
    // Surf replaces Piñata in the same slot; a cleared support launches real passengers.
    SURF(MechanicCapsule.SURF, "SURF"),
    // (2026-09-22) LOS PORTALES sustituyen a LA PORTERÍA MÓVIL. Ver [Portals].
    PORTALS(MechanicCapsule.PORTALS, "PORTALES"),
    // Giro orbital replaces Cometa/Vidriera; keep COMET ordinal for saved campaigns.
    COMET(MechanicCapsule.COMET, "GIRO ORBITAL"),
    ;

    companion object {

        /**
         * Paso que aporta el capítulo. **Coprimo con el tamaño del catálogo**: los 14 capítulos
         * arrancan en 14 puntos distintos, así que dos biomas seguidos nunca abren igual.
         */
        const val CHAPTER_STEP = 13

        /**
         * Paso que aporta el nivel. **Coprimo con el tamaño del catálogo**, y grande, por lo mismo
         * que [ChapterShapePool.STRIDE]: dos niveles seguidos caen lejos en la lista y se notan
         * distintos de verdad.
         *
         * ## Si añades una regla y `HouseRuleContractTest` se pone rojo, es aquí
         *
         * Las garantías del reparto —no repetir dentro del capítulo, que dos niveles seguidos nunca
         * compartan regla, que dos capítulos vecinos no abran igual— son propiedades **aritméticas**
         * de estos dos pasos contra el tamaño del catálogo, y el tamaño cambia cada vez que se añade
         * una mecánica. Cuando cambie, puede que estos dos números dejen de valer: el test dice
         * exactamente qué propiedad se ha roto y basta con elegir otros dos. Es a propósito que lo
         * diga un test y no un comentario — la propiedad se comprueba en los 293 niveles de verdad.
         */
        // Ha cambiado dos veces al crecer el catálogo, que es exactamente lo que este KDoc avisaba:
        // 9 con 17 reglas → 10 con 21 (9 comparte el divisor 3 con 21) → **9 otra vez con 28**, donde
        // 10 comparte el 2. Nueve es coprimo con 28 y su suma con [CHAPTER_STEP] no es múltiplo del
        // catálogo, que son las dos propiedades que `HouseRuleContractTest` exige.
        const val LEVEL_STEP = 9

        /**
         * El capítulo más largo del juego. Mientras el catálogo tenga al menos estas entradas,
         * ningún capítulo repite una regla — lo afirma `HouseRuleContractTest`, que lo mide contra
         * [LevelMap] en vez de contra una constante escrita a mano.
         */
        val longestChapter: Int get() = LevelMap.chapters.maxOf { it.levelCount }

        /**
         * La regla de la casa de [level], o `null` si el nivel no lleva ninguna.
         *
         * **Los niveles de intro y el tutorial nunca llevan.** Ahí se está enseñando a jugar, y una
         * regla que cambia las reglas antes de conocerlas no es variedad: es ruido. Es el mismo
         * criterio con el que [LevelVariety] les niega sabor de bandeja y [LevelDeal] les niega el
         * trato.
         */
        fun forLevel(level: Int): HouseRule? {
            if (level <= LevelMap.introLevels) return null
            if (TutorialCampaign.isTutorialLevel(level)) return null
            val chapter = LevelMap.chapterIndexForLevel(level)
            if (chapter < 0) return null
            // El paso del nivel usa su número ABSOLUTO, no su posición dentro del capítulo. Con la
            // posición local, el salto de capítulo reiniciaba el recorrido a cero y el último nivel
            // de un bioma podía compartir regla con el primero del siguiente — justo la costura
            // donde más se nota, porque el jugador acaba de cambiar de mundo y espera cambio.
            val slot = (chapter * CHAPTER_STEP + level * LEVEL_STEP).mod(entries.size)
            return entries[slot]
        }

        /** ¿Se juega [level] con la regla [rule]? El atajo que usan los `appliesTo` de cada regla. */
        fun isActive(level: Int, rule: HouseRule): Boolean = forLevel(level) == rule

        /** Los niveles que llevan [rule] en toda la campaña, en orden. Para tests y censos. */
        fun levelsWith(rule: HouseRule): List<Int> =
            (1..LevelMap.totalLevels).filter { isActive(it, rule) }

        /**
         * **Cuánto aprieta una regla en [level]: 0f en el primer capítulo, 1f en el último.**
         *
         * Cada regla lo traduce a su propio dial (ver el KDoc de la clase). Los niveles sin capítulo
         * —intro y tutorial— devuelven 0f: la versión más amable de todo.
         *
         * Es **lineal a propósito**. Una curva con acelerón la haría imposible de balancear regla a
         * regla, y lo que el usuario pidió es que suba de bioma en bioma, no que explote al final.
         */
        fun intensityFor(level: Int): Float {
            val chapter = LevelMap.chapterIndexForLevel(level)
            if (chapter < 0) return 0f
            val last = (LevelMap.chapters.size - 1).coerceAtLeast(1)
            return (chapter.toFloat() / last).coerceIn(0f, 1f)
        }

        /**
         * Interpola el dial de una regla entre su valor [atFirstChapter] y el de [atLastChapter].
         *
         * Sirve para las dos direcciones: la corriente va de 4 turnos a 2 (baja) y los testarudos de
         * 3 bloques a 5 (sube). Redondea, así que el resultado siempre es un entero jugable.
         */
        fun dial(level: Int, atFirstChapter: Int, atLastChapter: Int): Int {
            val t = intensityFor(level)
            return Math.round(atFirstChapter + (atLastChapter - atFirstChapter) * t)
        }
    }
}
