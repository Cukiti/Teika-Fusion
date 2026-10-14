package com.cukiti.teikafusion.model

/**
 * AFINIDAD — qué compañero contrarresta de verdad la mecánica de cada bioma.
 *
 * La tabla está **escrita a mano, una entrada a una**, y no derivada del capítulo en que aparece
 * cada personaje. Derivarla habría sido gratis pero mentiría: el compañero del Glaciar es Nieves, y
 * marcar a Nieves como «buena contra el hielo» es afinidad temática, no mecánica.
 *
 * Regla de la casa: una entrada solo entra aquí si el [razon] se puede leer contra la regla real del
 * bioma ([mechanicHint]) y la habilidad real del compañero ([COMPANION_ABILITIES]) y sale cierta. Si
 * hay que forzarla, no se pone — la Sala de Preparación enseña la mecánica del bioma y el jugador
 * decide solo. Un consejo falso es peor que ninguno.
 *
 * Lo vigila `CompanionAffinityTest`.
 */
data class CompanionAffinity(
    val companionId: String,
    /** Frase corta que se pinta bajo la tarjeta. Sin punto final: es una etiqueta, no una oración. */
    val razon: String
)

object CompanionAffinities {

    /**
     * Bioma → compañeros que lo contrarrestan, en orden de recomendación.
     *
     * El porqué de cada uno está en la propia razón; donde no sea evidente, va comentado.
     */
    private val table: Map<BiomeType, List<CompanionAffinity>> = mapOf(

        // El cristal se come UNA limpieza: cada bloque necesita dos pasadas. Ganan las habilidades
        // que pasan varias veces por el mismo sitio, no las que pegan un golpe grande.
        BiomeType.CRYSTAL to listOf(
            CompanionAffinity("miedoso", "sus pasadas rompen línea tras línea"),
            CompanionAffinity("cristal", "encadena varios reventones seguidos"),
            CompanionAffinity("dormilona", "sus filas llenas rematan varias líneas")
        ),

        // La siesta te quita la ROTACIÓN un turno. La mejor respuesta es no necesitarla.
        BiomeType.SLEEP to listOf(
            CompanionAffinity("estrella", "su bloque se derrama: no hay que girarlo"),
            CompanionAffinity("cristal", "coloca las fichas solas, girándolas él"),
            CompanionAffinity("dormilona", "rompe líneas sin colocar nada")
        ),

        // Los bloques HUYEN y quedan desperdigados; romper línea o fusionar los calma.
        BiomeType.FEAR to listOf(
            CompanionAffinity("miedoso", "reagrupa la madera que se ha dispersado"),
            CompanionAffinity("dormilona", "desploma el tablero y lo cierra por abajo"),
            CompanionAffinity("fantasma", "solidifica bloques justo donde falta")
        ),

        // La raíz secuestra tu Teika mayor: hay que romper 2 líneas a tiempo para rescatarlo.
        BiomeType.GARDEN to listOf(
            CompanionAffinity("dormilona", "hasta 3 filas de golpe: rescate inmediato"),
            CompanionAffinity("cristal", "encadena reventones para el rescate"),
            CompanionAffinity("rey", "te repone Teikas mientras uno está preso")
        ),

        // Cerrar la fila CON un Teika evita el circuito de bobinas entero.
        BiomeType.STORM to listOf(
            CompanionAffinity("rey", "más Teikas: cierras líneas sin bobinas"),
            CompanionAffinity("galactico", "pulveriza madera sin completar filas"),
            CompanionAffinity("planta", "rompe madera de un disparo, sin cerrar fila")
        ),

        // La lava sube con los turnos secos y RETROCEDE al romper filas.
        BiomeType.VOLCANO to listOf(
            CompanionAffinity("dormilona", "sus filas llenas hacen retroceder la lava"),
            CompanionAffinity("cristal", "encadena reventones y frena la subida"),
            CompanionAffinity("galactico", "limpia el rincón más denso de golpe")
        ),

        // El corte parte el tablero: 4 líneas lo reparan, o un MEGA al instante.
        BiomeType.NINJA to listOf(
            CompanionAffinity("dormilona", "3 filas de una vez, casi la reparación"),
            CompanionAffinity("rey", "material de fusión gratis para el MEGA"),
            CompanionAffinity("cristal", "encadena reventones hacia las 4 líneas")
        ),

        // Congela un Teika unos turnos: no fusiona ni ayuda a completar línea.
        BiomeType.GLACIER to listOf(
            CompanionAffinity("rey", "pare Teikas nuevos mientras uno está helado"),
            CompanionAffinity("fuego", "arrasa fila y columna: frena las oleadas"),
            CompanionAffinity("hielo", "perfora columnas sin depender del Teika")
        ),

        // El reflector se lleva un Teika o arranca las estrellas de los bloques.
        BiomeType.GHOST to listOf(
            CompanionAffinity("gloton", "se come las estrellas antes que la niebla"),
            CompanionAffinity("ninja", "parte los bloques con estrella: valen doble"),
            CompanionAffinity("cristal", "mantiene el ritmo que aleja la oscuridad")
        ),

        // La boca muerde un RINCÓN (3 celdas) tras dos descuidos.
        BiomeType.GLUTTON to listOf(
            CompanionAffinity("galactico", "vacía el rincón denso antes del mordisco"),
            CompanionAffinity("cristal", "encadena reventones y calma la boca"),
            CompanionAffinity("miedoso", "aprieta el tablero y rompe por el camino")
        ),

        // El veneno pudre la madera en lodo y salpica (P-322); limpiar veneno frena la recarga del charco.
        BiomeType.TOXIC to listOf(
            CompanionAffinity("toxico", "revienta el veneno y sanea las fichas nuevas"),
            CompanionAffinity("fuego", "arrasa filas y columnas antes de que caduquen"),
            CompanionAffinity("miedoso", "rompe muchas líneas en una sola pasada")
        ),

        // Sol y Luna bajan sin vuelta atrás: se llevan un Teika o los bloques con estrella.
        BiomeType.STAR to listOf(
            CompanionAffinity("gloton", "recoge las estrellas antes de que bajen"),
            CompanionAffinity("ninja", "cobra las estrellas por partida doble"),
            CompanionAffinity("dormilona", "rompe filas y reinicia la cuenta atrás")
        ),

        // LA ESPADA DEL REY (P-294): parte la línea con más bloques, sin puntos y perdiendo sus
        // estrellas; romper una línea que CRUCE la señalada la envaina. (2026-09-26: las razones de
        // antes hablaban de los muros del asedio, que ya no existen.)
        BiomeType.KING to listOf(
            CompanionAffinity("gloton", "se come las estrellas antes del tajo"),
            CompanionAffinity("ninja", "cobra doble lo que la espada te quitaría"),
            CompanionAffinity("miedoso", "rompe líneas: alguna cruzará la señalada")
        ),

        // LOS METEORITOS (P-294): fijan un cráter 2×2 y funden hasta 2 bloques en roca; romper una
        // línea que cruce el cráter lo desvía. P-319: metralla, roca que se agrieta y lluvia doble al final.
        BiomeType.GALACTIC to listOf(
            CompanionAffinity("miedoso", "rompe muchas líneas: alguna cruza el cráter"),
            CompanionAffinity("dormilona", "rompe varias líneas de golpe para desviarlo"),
            CompanionAffinity("fantasma", "cierra líneas colando bloques en los huecos")
        )
    )

    /**
     * Razón por la que [companionId] es bueno contra [biome], o `null` si no lo es.
     *
     * **La única función de esta tabla que usa la app**: la pinta la Sala de Preparación bajo cada
     * tarjeta de compañero (`InventoryScreen`).
     */
    fun reason(companionId: String, biome: BiomeType): String? =
        table[biome]?.firstOrNull { it.companionId == companionId }?.razon

    // ── Superficie de PRUEBA (P-124, 2026-09-03) ────────────────────────────────────────────────
    // Estas dos no las llama la app: existen para que `CompanionAffinityTest` pueda recorrer la
    // tabla entera y comprobar que ningún bioma con mecánica se queda sin consejo, que no hay
    // compañeros repetidos y que las razones caben en la tarjeta. Se marcan `internal` en vez de
    // borrarse porque ese test es justamente lo que impide que la tabla se llene de consejos falsos
    // —la regla de la casa dice que un consejo falso es peor que ninguno—, pero `internal` deja
    // claro que **no** son API de producción y que nadie debería empezar a llamarlas desde la UI.

    /** Compañeros afines a [biome] (vacío en los niveles de intro, que no tienen bioma). */
    internal fun forBiome(biome: BiomeType): List<CompanionAffinity> = table[biome].orEmpty()

    internal fun isAffine(companionId: String, biome: BiomeType): Boolean =
        reason(companionId, biome) != null
}
