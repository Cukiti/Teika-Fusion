package com.cukiti.teikafusion.model

/**
 * **FAMILIA D DE LAS REGLAS DE LA CASA: CÓMO SE COMPORTAN LOS CUERPOS** (2026-09-04, aprobada por el
 * usuario en el panel del catálogo).
 *
 * Es la familia más barata del catálogo y la única que es **puro game feel**: no cambia una sola
 * regla del juego. Los Teikas nacen igual, se funden igual, cierran líneas igual y estorban igual —
 * lo que cambia es **cómo se mueven**, y eso basta para que un nivel se sienta otro desde el primer
 * cuerpo que cae.
 *
 * Y esa baratura es exactamente la razón de que la familia exista. Un catálogo entero de reglas que
 * aprietan se lee como dificultad, no como variedad, y el encargo del usuario fue explícito: «no es
 * que vamos a volver ultra difícil». Estas cuatro son el contrapeso — el jugador nota que el nivel es
 * distinto **antes** de haber tenido que resolver nada.
 *
 * ## Lo que no tocan, y por qué
 *
 * **El tier sigue mandando sobre el tier.** [TeikaPhysicsProfiles] da a cada tamaño su
 * personalidad —un T1 responde a la inclinación y un T4 casi no—, y esta familia **multiplica** ese
 * perfil en vez de sustituirlo. Así un nivel ligero sigue teniendo la misma jerarquía interna: los
 * pequeños vuelan más que los grandes, solo que todos vuelan más. Sustituir el perfil habría
 * aplanado el tablero y perdido la lectura de tamaños, que es media mitad Suika del juego.
 */

/** Cómo se comportan los cuerpos en un nivel. [NORMAL] es el mundo de siempre. */
enum class TeikaFeel { NORMAL, HEAVY }

/**
 * Multiplicadores que un nivel aplica al perfil físico de **todos** sus cuerpos.
 *
 * Uno significa «como siempre», así que [TeikaFeelRules.modifierFor] devuelve el neutro en los ~24 de
 * cada 28 niveles que no llevan ninguna de estas reglas y el mundo físico no se entera de que existen.
 */
data class TeikaFeelModifier(
    /** Escala la gravedad vertical del mundo. <1 flota, >1 se desploma. */
    val gravity: Float = 1f,
    /** Escala la respuesta a la inclinación. Cuánto empuja el gesto del jugador. */
    val tilt: Float = 1f,
    /** Escala la resistencia a rodar. <1 rueda más lejos, >1 se para antes. */
    val drag: Float = 1f,
    /** Escala el rebote. */
    val restitution: Float = 1f,
) {
    val isNeutral: Boolean get() = gravity == 1f && tilt == 1f && drag == 1f && restitution == 1f

    companion object {
        val NEUTRAL = TeikaFeelModifier()
    }
}

object TeikaFeelRules {

    // (RETIRADO 2026-09-22) P-153 · TEIKAS LIGEROS. Los sustituyen LAS POMPAS (P-238): en vez de
    // flotar todos un poco, cada recién nacido sube en su pompa hasta que el jugador la revienta.
    // Ver [TeikaBubbles].

    /**
     * **P-154 · TEIKAS PESADOS**, hoy **DE PLOMO** (P-239): caen a plomo y casi no ruedan.
     *
     * Desde el 2026-09-22 el peso no es solo tacto: el golpe de
     * una caída larga aplasta el bloque de debajo — ver [LeadTeikas].
     *
     * **El dial:** más pesados según sube el bioma.
     */
    const val HEAVY_GRAVITY_FIRST = 1.35f
    const val HEAVY_GRAVITY_LAST = 1.70f

    /** El feel de [level]. */
    fun feelFor(level: Int): TeikaFeel = when (HouseRule.forLevel(level)) {
        HouseRule.TRAIL -> TeikaFeel.NORMAL
        else -> TeikaFeel.NORMAL
    }

    /**
     * Los multiplicadores de [level].
     *
     * Se interpolan en `Float` y no con [HouseRule.dial] —que redondea a entero— porque aquí el dial
     * mueve coeficientes físicos: un salto de 0,72 a 1 sería otro mundo, no un capítulo más duro.
     */
    fun modifierFor(level: Int): TeikaFeelModifier {
        val t = HouseRule.intensityFor(level)
        return when (feelFor(level)) {
            TeikaFeel.NORMAL -> TeikaFeelModifier.NEUTRAL
            TeikaFeel.HEAVY -> TeikaFeelModifier(
                gravity = HEAVY_GRAVITY_FIRST + (HEAVY_GRAVITY_LAST - HEAVY_GRAVITY_FIRST) * t,
                tilt = 0.55f,
                drag = 1.75f,
                // Casi sin rebote: cae y se queda. Nunca cero — un cuerpo que no rebota NADA se lee
                // como si se hubiera pegado al suelo, y este juego celebra el aterrizaje.
                restitution = 0.45f,
            )
        }
    }
}

/**
 * **P-240 · EL IMÁN ES UNA FICHA** (2026-09-22, rework pedido por el usuario: «una idea mejor de cómo
 * usar la mecánica imán dentro del juego»).
 *
 * El imán de antes (P-155) era una fuerza pasiva entre Teikas del mismo tier: se veía, pero el
 * jugador no decidía nada. Ahora el imán **llega en la bandeja** —una ficha de una casilla con su
 * herradura— y **el jugador elige dónde ponerlo**. Durante [TURNS] colocaciones atrae a TODOS los
 * Teikas que tenga a su alcance: ruedan hacia él, se apiñan a su alrededor y las parejas del mismo
 * tier se funden al tocarse. Después el imán se rompe, deja su casilla libre y los cuerpos caen.
 *
 * ## Qué decide el jugador
 *
 * Dónde juntar a los Teikas: al lado de su pareja para una fusión, lejos de donde necesita colocar
 * para abrir sitio, o encima de una estrella para que el MEGA la recoja. Una herramienta en la mano,
 * no una regla de ambiente.
 *
 * ## Lo que NO toca
 *
 *  - **Ni congelados ni raptados**: el Glaciar y la raíz del Jardín mandan sobre una regla de la casa.
 *  - **No fusiona por su cuenta**: solo acerca; el abrazo lo decide `processContactFusion`.
 *  - **La casilla del imán es madera normal** a efectos de líneas: si su línea se cierra, el imán se
 *    rompe antes de tiempo. Es la otra mitad de la decisión — ponerlo en una fila casi llena dura poco.
 *
 * ## El dial
 *
 * El alcance **encoge** de bioma en bioma: cinco celdas en el Cristal, tres y media en el Galáctico.
 * Al final hay que ponerlo cerca de verdad.
 */
object MagnetPiece {

    /** Colocaciones que dura el imán después de la suya. */
    const val TURNS = 3

    /** Cada cuántas reposiciones de bandeja llega un imán nuevo (si no hay otro en juego). */
    const val EVERY_REFILLS = 3

    const val RANGE_FIRST_CHAPTER = 5f
    const val RANGE_LAST_CHAPTER = 3.5f

    /**
     * Aceleración máxima del tirón, en celdas/s². **Mayor que la gravedad** (26) en todo el alcance
     * —ver el suelo de [pullAt]—: un imán que no levanta un cuerpo que tiene debajo no se lee como
     * imán. Al borde del alcance la diferencia es pequeña, así que el lejano sube despacio y se VE.
     */
    const val PULL_ACCEL = 46f

    fun appliesTo(level: Int): Boolean = false // Retired; kept only for old save/API compatibility.

    /** El alcance del imán en [level] (distancia entre el centro del imán y el del cuerpo). */
    fun rangeFor(level: Int): Float {
        if (!appliesTo(level)) return 0f
        val t = HouseRule.intensityFor(level)
        return RANGE_FIRST_CHAPTER + (RANGE_LAST_CHAPTER - RANGE_FIRST_CHAPTER) * t
    }

    /** ¿Toca repartir un imán en la reposición número [refillCount]? */
    fun dealsOnRefill(refillCount: Int, scale: Int = 1): Boolean =
        refillCount > 0 && refillCount % (EVERY_REFILLS * scale.coerceAtLeast(1)) == 0

    /**
     * Cuánto tira el imán a [distance] celdas con alcance [range]: **1 al lado, 0 al borde**.
     *
     * Lineal y no cuadrática, al revés que el imán viejo: aquí el jugador ha puesto el imán a
     * propósito y quiere ver que los cuerpos lejanos también se mueven, no solo los de al lado.
     * Con un suelo del 60 %: con [PULL_ACCEL] eso sigue ganándole a la gravedad al borde del
     * alcance, así que TODO cuerpo dentro del círculo se acerca, que es lo que el círculo promete.
     */
    fun pullAt(distance: Float, range: Float): Float {
        if (range <= 0f || distance >= range) return 0f
        val cerca = ((range - distance) / range).coerceIn(0f, 1f)
        return 0.6f + 0.4f * cerca
    }
}

/**
 * **P-239 · TEIKAS DE PLOMO** (2026-09-22, sustituye a TEIKAS PESADOS).
 *
 * Los pesados solo cambiaban cómo caían los cuerpos, y el jugador no tenía nada que hacer con eso.
 * Los de plomo caen igual de pesados —mismo [TeikaFeelModifier]— pero además **aplastan**: un Teika
 * que cae desde [dropCellsFor] casillas o más **rompe el bloque sobre el que aterriza**, y si llevaba
 * estrella, la recoge.
 *
 * La inclinación se convierte en un arma: ladear el móvil para dejar caer un cuerpo por el borde de
 * una pila, justo encima de la estrella que falta. Y cada bloque-Teika roto cría un Teika que cae (P-256)
 * — así que el sitio donde cierras esa línea decide también qué aplasta.
 *
 * ## Lo que no rompe
 *
 * Lo mismo que no rompe la presión de los cuerpos ([TeikaPushBreakRules]): bobinas, congelado, muros
 * del Rey, cúmulos, lava — y la ficha que el jugador acaba de poner (P-187), que no se la puede
 * reventar un cuerpo que ya estaba cayendo cuando la soltó.
 *
 * ## El dial
 *
 * Dos casillas de caída en el Cristal, **tres** en el Galáctico: al final hay que buscar la altura.
 */
object LeadTeikas {

    const val DROP_FIRST_CHAPTER = 2
    const val DROP_LAST_CHAPTER = 3

    /**
     * Qué fracción de la velocidad teórica de caída libre se exige. El damping lineal del mundo y el
     * rebote de la pila le roban algo de velocidad a una caída real; sin este margen, una caída de
     * dos casillas justas se quedaría a las puertas.
     */
    const val SPEED_MARGIN = 0.85f

    fun appliesTo(level: Int): Boolean = false // Retired: Estela never changes weight or crushes wood.

    /** Casillas de caída que hacen falta para aplastar en [level]. */
    fun dropCellsFor(level: Int): Int =
        HouseRule.dial(level, DROP_FIRST_CHAPTER, DROP_LAST_CHAPTER)
            .coerceIn(DROP_FIRST_CHAPTER, DROP_LAST_CHAPTER)

    /**
     * Velocidad vertical de impacto (celdas/s) a partir de la cual el golpe aplasta, o **0** si el
     * nivel no lleva la regla. Es la de una caída libre de [dropCellsFor] casillas con la gravedad
     * del nivel: v = √(2·g·h).
     */
    fun crushSpeedFor(level: Int, baseGravity: Float): Float {
        if (!appliesTo(level)) return 0f
        val g = baseGravity * TeikaFeelRules.modifierFor(level).gravity
        return kotlin.math.sqrt(2f * g * dropCellsFor(level)) * SPEED_MARGIN
    }
}

/* (RETIRADO 2026-09-22) `FreeTilt` — LA INCLINACIÓN LIBRE (P-156). La sustituye LA BRÚJULA (P-241):
 * en vez de amplificar el gesto, la gravedad de los cuerpos gira con un ritmo que se puede leer.
 * Ver [Compass]. */
