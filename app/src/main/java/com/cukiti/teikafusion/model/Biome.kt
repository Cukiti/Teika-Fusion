package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable

/**
 * Bioma temático de un nivel. Cada bioma se asocia a un Teika con nombre y aporta:
 *  - un tema visual (paleta / overlay / partículas en [GameScreen]).
 *  - una mecánica AMBIENTAL que dificulta el tablero (distinta de la habilidad del compañero).
 *
 * Las ventanas de bioma son espejo de los niveles de desbloqueo de [NamedTeikaRegistry].
 * Los niveles 1–4 no tienen bioma ([NONE]).
 */
@Serializable
enum class BiomeType(val displayName: String) {
    NONE("—"),
    CRYSTAL("Caverna de Cristal"),
    SLEEP("Bosque del Sueño"),
    FEAR("Pasaje Encantado"),
    GARDEN("Jardín"),
    STORM("Tormenta"),
    VOLCANO("Volcán"),
    NINJA("Dojo en Sombras"),
    GLACIER("Glaciar"),
    GHOST("Mansión Espectral"),
    GLUTTON("Cocina Voraz"),
    TOXIC("Pantano Tóxico"),
    STAR("Cielo Estelar"),
    KING("Trono Real"),
    GALACTIC("Espacio Profundo")
}

/**
 * Turnos ociosos base del bioma, para intercalarlos en el texto de [mechanicHint].
 *
 * Los números de las pistas estaban ESCRITOS A MANO («si pasas 3 colocaciones…»), así que cambiar el
 * compás de un bioma dejaba a su pista mintiendo — y CLAUDE.md pide justo lo contrario: que la pista
 * describa la regla REAL. Ahora salen de [BiomeRhythm], que es quien decide el compás, y no pueden
 * separarse de él. Lo vigila `BiomePhaseContractTest`.
 */
private fun idle(biome: BiomeType): Int = BiomeRhythm.idleBase(biome)

/** P-353: el ritmo de los biomas que siempre llegan, con las cifras reales. */
/** «Cada 5 colocaciones (4 al final del capítulo)», con los números del capítulo del bioma (P-361). */
private fun cada(biome: BiomeType): String {
    val chapter = LevelMap.chapters.indexOfFirst { it.biome == biome }
    val calm = BiomeCycle.CHAPTER_CALM.getOrElse(chapter) { BiomeCycle.CYCLE - 3 } + 3
    val late = BiomeCycle.CHAPTER_CALM_LATE.getOrElse(chapter) { 1 }.coerceAtLeast(0) + 3
    return if (calm == late) "Cada $calm colocaciones" else "Cada $calm colocaciones ($late al final del capítulo)"
}

/** «1 turno» / «2 turnos»: las pistas se escriben con la cifra real, que puede ser 1. */
private fun turnos(n: Int): String = if (n == 1) "1 turno" else "$n turnos"

/**
 * Pista corta de la MECÁNICA AMBIENTAL del bioma, para la carta de intro del nivel. Cada texto refleja
 * la regla real implementada en [GameViewModel] (applyBiomeTick / placePiece), no un adorno.
 */
fun mechanicHint(biome: BiomeType): String = when (biome) {
    BiomeType.CRYSTAL  -> "El cristal absorbe una limpieza: la primera arranca el cristal y deja el bloque; la segunda rompe el bloque."
    BiomeType.SLEEP    -> "${cada(BiomeType.SLEEP)}, la Maite llega. Dos turnos antes MARCA tu ficha más grande: juégala a tiempo y se dormirá otra, solo 1 turno; si no, la marcada se duerme ${turnos(BiomeRhythm.punishmentTurns(BiomeType.SLEEP))}. PARADA: coloca la ficha marcada rompiendo una línea justo en el turno en que llega."
    BiomeType.FEAR     -> "Cada $FEAR_EVERY colocaciones, el bloque más alejado de tu ficha huye a una casilla vecina libre. Tus líneas se rompen antes del susto, pero romperlas no lo detiene. Si no existe un movimiento seguro, los bloques permanecen en su sitio."
    BiomeType.GARDEN   -> "${cada(BiomeType.GARDEN)}, una raíz ataca. Dos turnos antes MARCA el borde por el que entra y el Teika que se llevará: lo arrastra hacia el borde dejando ENREDADERA (rescátalo rompiendo 2 líneas). Sin Teika, la enredadera invade los huecos de esa franja del borde: llénala tú antes. PARADA: rompe una línea que toque al Teika marcado (o la franja) justo en el turno del ataque."
    BiomeType.STORM    -> "Completar una fila la convierte en un circuito de bobinas: tócalas en " +
        "orden para descargarla. Si cierras la fila con un TEIKA, no hay bobinas: se rompe y ya."
    BiomeType.VOLCANO  -> "${cada(BiomeType.VOLCANO)}, la lava baja una fila, rompas o no. Cada línea que rompas la hace retroceder ${VOLCANO_RETREAT_PER_LINE} fila al instante, y la fila de debajo ARDE: si te quedas quieto unos segundos se vuelve MAGMA, y una línea con magma se rompe sin darte Teika ni puntos. PARADA: rompe DOS líneas justo en el turno en que baja."
    BiomeType.NINJA    -> "${cada(BiomeType.NINJA)}, la katana cae y PARTE el tablero en dos: los Teikas no pueden cruzar, ninguna ficha puede pisar ambas mitades y cada fila se rompe por MITADES, pero solo si un Teika forma parte de esa media fila (las columnas, como siempre). Cada media fila o columna que rompas acerca la reparación —o forma un MEGA TEIKA y la reventarás—; si no, la grieta se cierra sola a las ${BiomeRhythm.punishmentTurns(BiomeType.NINJA)} colocaciones y deja ${NinjaKintsugi.SCARS} bloques de oro en la costura. PARADA: rompe una columna del centro justo en el turno del tajo."
    BiomeType.GLACIER  -> "${cada(BiomeType.GLACIER)} llega una oleada de frío —rompas filas o no— y congela a TODOS los Teikas del tablero: quedan ${turnos(BiomeRhythm.punishmentTurns(BiomeType.GLACIER))} como obstáculos helados: no se puede colocar encima y no fusionan, pero SÍ ayudan a completar líneas. Se deshielan solos — aprovecha para fusionar ANTES de que llegue la ola."
    BiomeType.GHOST    -> "${cada(BiomeType.GHOST)}, el espejo embrujado DESTELLA: se empaña dos turnos antes marcando qué mitad copiará, y la refleja sobre los huecos de la otra con BLOQUES FANTASMA, que estorban pero cuentan para las líneas y se desvanecen a los ${HauntedMirror.PHANTOM_TURNS} turnos. Menos madera en esa mitad, menos fantasmas. PARADA: rompe una línea entera DENTRO de la mitad marcada justo en el turno del destello."
    BiomeType.GLUTTON  -> "${cada(BiomeType.GLUTTON)}, una boca muerde un rincón. Se abre un turno antes enseñándolo: se traga la madera del rincón (y sus estrellas, que cuentan) y deja pozos que tardan ${LevelDirector.gluttonRecoverTurns(1)} colocaciones en digerirse. Vacía el rincón para que no se coma nada. PARADA: rompe una línea que pase por el rincón justo en el turno del mordisco."
    BiomeType.TOXIC    -> "El veneno es una capa que se EXTIENDE: cada colocación sin romper líneas avanza a los bloques vecinos de los envenenados. Si no alcanza ninguno, tras ${ToxicBiomeRules.SPLASH_AFTER_TURNS} colocaciones seguidas sin romper SALPICA ${ToxicBiomeRules.SPLASH_COUNT} bloques en otra zona. Una línea con veneno se rompe igual, pero a los bloques envenenados solo les LAVA el veneno: quedan al descubierto y se rompen limpios en la siguiente."
    BiomeType.STAR     -> "${cada(BiomeType.STAR)}, baja un astro y el tablero avisa de cuál: se DORA si es el Sol, se hace de NOCHE si es la Luna. Un turno antes MARCA su presa: el Sol, tu Teika más grande (si ya no está, calcina un bloque); la Luna, las estrellas que robará (cóbralas antes y no se lleva nada). PARADA: rompe una línea que pase por lo marcado justo en el turno en que baja."
    BiomeType.KING     -> "${cada(BiomeType.KING)}, el Rey golpea. Dos turnos antes ALZA la espada sobre la fila o columna con más bloques; cae con la hoja ancha: parte ESA línea —sus bloques se rompen sin puntos— y la de al lado queda como ESCOMBRO de roca (hacen falta dos líneas para romperlo), y los Teikas salen lanzados. Por cada espada entran ${KingPiece.PIECES_PER_SWORD} piezas de ajedrez; cada una (torre, alfil o caballo) arrasa su camino y SE QUEDA: bloquea lo que ataca como en el ajedrez (su calle, o sus saltos en L) hasta que rompas su fila o su columna — su casilla cuenta como llena. Mientras siga ahí, los golpes son de espada. Vacía lo marcado y cortará aire. PARADA: rompe una de las líneas de la espada (o una que cruce la diana, la casilla donde caerá la pieza) justo en el turno del golpe."
    BiomeType.GALACTIC -> "${cada(BiomeType.GALACTIC)}, cae un METEORITO. Dos turnos antes una MIRA busca blanco y luego FIJA un cráter de 2×2: hasta ${MeteorShower.MAX_ROCKS} bloques del cráter se funden en ROCA —cuentan para las líneas, pero necesitan DOS para romperse—, salen ${MeteorShower.SHARDS} fragmentos a los huecos de alrededor y los Teikas salen despedidos. Una roca que dura ${MeteorShower.CRACK_TURNS} colocaciones se agrieta y contagia a un vecino. Vacía el cráter y no fundirá nada. PARADA: rompe una línea que cruce el cráter justo en el turno del impacto."
    BiomeType.NONE     -> ""
}

/** Un capítulo del viaje: un bioma con su nº de niveles (= nº de cuadritos de su mural en el mapa). */
data class LevelChapter(val biome: BiomeType, val levelCount: Int)

/**
 * FUENTE DE VERDAD del progreso. De aquí derivan: el total de niveles, las ventanas de bioma
 * ([BiomeRegistry.phases]), los niveles de desbloqueo de compañeros ([NamedTeikaRegistry]) y los
 * murales y el mapa de mundos (`ui/WorldMap.kt`).
 *
 * Estructura AAA estilo Block Blast: 14 capítulos (1 bioma c/u) de ~21 niveles → ~300 niveles que
 * "se sienten muchos". Subir/bajar niveles = ajustar [chapters] (y el mural respectivo). Los
 * primeros [introLevels] niveles no tienen bioma (tutorial suave), igual que antes.
 */
object LevelMap {
    /**
     * Niveles 1..introLevels sin bioma (arranque amable). **Son las lecciones del tutorial**
     * ([TutorialCampaign]), y por eso los dos números tienen que moverse juntos.
     *
     * 4 → **7** el 2026-09-01, al ampliar el tutorial: faltaban por enseñar el OBJETIVO (cómo se
     * gana un nivel), los TURNOS (el recurso con el que se juega) y las HABILIDADES de compañero.
     * El total del juego no se mueve —sigue en 293— porque el capítulo del Cristal cede esos tres:
     * su mural son 22 cuadritos y sigue cuadrando (15 + 7).
     */
    const val introLevels = 7

    // El nº de niveles de cada capítulo = nº de cuadritos de su mural pixelart (ver muralArt en
    // BiomeAdventureMap.kt). CRYSTAL lleva los de intro menos porque su mural los absorbe.
    val chapters: List<LevelChapter> = listOf(
        LevelChapter(BiomeType.CRYSTAL,  15),   // gema (mural 22 = 15 + 7 intro)
        LevelChapter(BiomeType.SLEEP,    21),   // luna creciente
        LevelChapter(BiomeType.FEAR,     19),   // murciélago
        LevelChapter(BiomeType.GARDEN,   18),   // flor
        LevelChapter(BiomeType.STORM,    21),   // nube + rayo
        LevelChapter(BiomeType.VOLCANO,  22),   // volcán
        LevelChapter(BiomeType.NINJA,    22),   // máscara ninja
        LevelChapter(BiomeType.GLACIER,  21),   // copo de nieve
        LevelChapter(BiomeType.GHOST,    21),   // fantasma
        LevelChapter(BiomeType.GLUTTON,  20),   // hamburguesa
        LevelChapter(BiomeType.TOXIC,    22),   // barril tóxico
        LevelChapter(BiomeType.STAR,     22),   // estrella
        LevelChapter(BiomeType.KING,     21),   // corona
        LevelChapter(BiomeType.GALACTIC, 21)    // planeta con anillo
    )

    /** Nivel (1-based) en que ARRANCA cada capítulo (tras los niveles de intro). */
    val chapterStart: List<Int> = run {
        var acc = introLevels + 1
        chapters.map { ch -> acc.also { acc += ch.levelCount } }
    }

    /** Total de niveles del juego = intro + suma de capítulos. */
    val totalLevels: Int = introLevels + chapters.sumOf { it.levelCount }

    /**
     * **EL NÚMERO QUE SE LE ENSEÑA AL JUGADOR** (2026-09-02, decisión del usuario tras la grabación
     * `analisistutorial`).
     *
     * `level` es la casilla real del mapa y **no se toca**: de él dependen los biomas, los
     * desbloqueos de compañeros y los murales. Pero las siete primeras casillas son las LECCIONES,
     * y el jugador nunca las ve como niveles — las juega como «LECCIÓN 3 DE 7». Con la numeración
     * cruda, su primer nivel de verdad se presentaba como **«NIVEL 8 COMPLETADO»** y el inicio le
     * decía «NIVEL 10» tras dos partidas: el primer número de su vida en el juego era un 8 sin
     * explicación.
     *
     * Así que **solo la presentación resta**: una lección conserva su propio número (1-7, y se
     * rotula «LECCIÓN»), y un nivel se muestra descontando el tutorial. Todo lo que persiste,
     * puntúa o decide reglas sigue usando `level` a secas.
     */
    fun isLesson(level: Int): Boolean = level <= introLevels

    /** Ver [isLesson]: el número mostrado. Para una lección es el suyo; para un nivel, sin tutorial. */
    fun displayNumber(level: Int): Int =
        if (isLesson(level)) level.coerceAtLeast(1) else level - introLevels

    /** «LECCIÓN» o «NIVEL», el sustantivo que acompaña a [displayNumber]. */
    fun displayWord(level: Int): String = if (isLesson(level)) "LECCIÓN" else "NIVEL"

    /** Índice del capítulo que contiene [level], o -1 si está en los niveles de intro. */
    fun chapterIndexForLevel(level: Int): Int {
        if (level <= introLevels) return -1
        for (i in chapters.indices.reversed()) if (level >= chapterStart[i]) return i
        return -1
    }

    /** Bioma del nivel ([BiomeType.NONE] en los niveles de intro). */
    fun biomeFor(level: Int): BiomeType =
        chapterIndexForLevel(level).let { if (it < 0) BiomeType.NONE else chapters[it].biome }

    /** Índice 0-based del nivel dentro de su capítulo (0 = primero del bioma). */
    fun localIndex(level: Int): Int {
        val i = chapterIndexForLevel(level)
        return if (i < 0) 0 else level - chapterStart[i]
    }

    /** Último nivel (1-based) del capítulo [i]. */
    fun chapterLastLevel(i: Int): Int = chapterStart[i] + chapters[i].levelCount - 1

    /** Si [level] es el ÚLTIMO de su capítulo → al superarlo el mural queda completo. -1 si no aplica. */
    fun finaleChapterForLevel(level: Int): Int {
        val i = chapterIndexForLevel(level)
        return if (i >= 0 && level == chapterLastLevel(i)) i else -1
    }
}

/**
 * Un mural de bioma recién COMPLETADO (último nivel del capítulo superado). Lo emite el ViewModel
 * para que la UI dispare la cinemática de celebración una sola vez (la recompensa ya se concedió).
 */
data class MuralCelebration(
    val chapterIndex: Int,
    val biome: BiomeType,
    val reward: Int,
    /** P-250: el bloque que este capítulo descubre (ver [BlockDiscovery]), o `null` si no guarda ninguno. */
    val newBlockSkinId: String? = null,
)

/**
 * Decide qué bioma corre en cada nivel y cómo se mezclan visualmente.
 *
 * Regla de mecánicas (pedido del diseño): en cada nivel corren la mecánica del bioma ACTUAL
 * y la del bioma ANTERIOR — nunca tres biomas a la vez. Ver [activeMechanics].
 *
 * Regla visual: el tablero del nivel arranca como el bioma actual y se va transformando hacia
 * el SIGUIENTE bioma conforme avanzan los niveles de la ventana, hasta completar la transición
 * en el primer nivel de la ventana siguiente. Ver [transitionProgress] + [nextBiome].
 */
object BiomeRegistry {

    /**
     * (nivel de desbloqueo, bioma) — DERIVADO de [LevelMap]. Coincide por construcción con los
     * niveles de desbloqueo de [NamedTeikaRegistry] (ambos leen [LevelMap.chapterStart]).
     */
    private val phases: List<Pair<Int, BiomeType>> =
        LevelMap.chapters.mapIndexed { i, ch -> LevelMap.chapterStart[i] to ch.biome }

    private val starts: List<Int> = phases.map { it.first }

    /** Bioma cuya ventana contiene [level]. [BiomeType.NONE] para niveles 1–4. */
    fun currentBiome(level: Int): BiomeType = LevelMap.biomeFor(level)

    /**
     * Bioma de la ventana SIGUIENTE — destino del morph visual.
     * Si [level] ya está en la última ventana, devuelve el actual (sin morph).
     */
    fun nextBiome(level: Int): BiomeType {
        for ((lvl, biome) in phases) {
            if (level < lvl) return biome
        }
        return currentBiome(level)
    }

    /**
     * Bioma de la ventana ANTERIOR — su mecánica sigue activa como segunda capa.
     * [BiomeType.NONE] si el nivel está en la primera ventana (o antes).
     */
    fun previousBiome(level: Int): BiomeType {
        val current = currentBiome(level)
        if (current == BiomeType.NONE) return BiomeType.NONE
        var prev = BiomeType.NONE
        for ((lvl, biome) in phases) {
            if (biome == current) break
            if (level >= lvl) prev = biome
        }
        return prev
    }

    /**
     * Mecánicas activas en [level] = bioma actual + bioma anterior (sin NONE, sin duplicados).
     * Máximo 2 — nunca se ligan 3 biomas.
     */
    /**
     * Mecánicas ambientales activas en [level]: **solo la del bioma actual** (2026-08-28, decisión
     * del usuario).
     *
     * Hasta hoy corrían DOS: la del bioma actual y la del anterior, que seguía viva como segunda
     * capa. Dos mecánicas ambientales a la vez hacen que ninguna se lea: el jugador no sabe cuál de
     * las dos le está castigando, y el hint del popup solo describe una. Un bioma, una mecánica.
     *
     * [previousBiome] sigue existiendo — lo usan la transición VISUAL y el cálculo de presupuesto —,
     * pero ya no aporta mecánica.
     */
    fun activeMechanics(level: Int): List<BiomeType> =
        listOf(currentBiome(level)).filter { it != BiomeType.NONE }

    /**
     * Progreso 0f..1f de la transformación visual del nivel hacia [nextBiome].
     *  - 0f en el primer nivel de la ventana (bioma actual al 100%).
     *  - **1f exacto en el ÚLTIMO nivel de la ventana** (bioma siguiente al 100%).
     *  - 0f también en la última ventana (no hay siguiente al cual transformarse).
     *
     * El 1f exacto es lo que hace la transición **continua**: en el último nivel de la ventana el
     * fondo ya es el bioma siguiente al 100 %, que es exactamente lo que dibuja el primer nivel de
     * esa ventana con progreso 0f. Antes se normalizaba contra la ventana ENTERA, así que el último
     * nivel se quedaba en ~0,94 y el bioma saliente —que además nunca bajaba del 60 % de opacidad—
     * **desaparecía de golpe** al pasar de nivel. Ver [BiomeBackdrop].
     */
    fun transitionProgress(level: Int): Float {
        val curStart = starts.lastOrNull { level >= it } ?: return 0f
        val nextStart = starts.firstOrNull { it > level } ?: return 0f
        // Se normaliza contra el ÚLTIMO nivel de la ventana (nextStart - 1), no contra nextStart.
        val span = (nextStart - 1 - curStart).toFloat()
        if (span <= 0f) return 0f   // ventana de un solo nivel: no hay recorrido que interpolar
        return ((level - curStart) / span).coerceIn(0f, 1f)
    }
}
