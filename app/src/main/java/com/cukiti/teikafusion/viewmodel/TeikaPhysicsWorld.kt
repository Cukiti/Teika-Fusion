package com.cukiti.teikafusion.viewmodel

import com.cukiti.teikafusion.model.TurbineRules
import com.cukiti.teikafusion.model.TurbineBeam
import com.cukiti.teikafusion.model.TurbineVelocity
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.model.PinballFlight
import com.cukiti.teikafusion.model.PinballRules
import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.model.MagnetPiece
import com.cukiti.teikafusion.model.Portals
import com.cukiti.teikafusion.model.TeikaBubbles
import com.cukiti.teikafusion.model.TeikaFeelModifier
import com.cukiti.teikafusion.model.TeikaPhysicsProfile
import com.cukiti.teikafusion.model.TeikaPhysicsProfiles
import com.cukiti.teikafusion.model.TeikaType
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * Snapshot inmutable de un Teika como CUERPO FÍSICO circular (motor Suika).
 * Los Teikas ya NO viven en el grid: caen, ruedan y se fusionan al contacto en una capa
 * continua sobre el tablero. [x]/[y] es el CENTRO del círculo en unidades de celda
 * (origen = esquina superior-izquierda del tablero; una celda = 1.0).
 *
 * @param frozenTurns Glaciar: >0 = congelado (estático, no fusiona) los próximos N turnos.
 * @param kinematic   Jardín: la raíz lo arrastra — la física no lo integra ni lo empuja.
 */
data class TeikaBody(
    val id: String,
    val type: TeikaType,
    val x: Float,
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val frozenTurns: Int = 0,
    val kinematic: Boolean = false,
    /** **P-238 · LAS POMPAS**: golpes de línea que le faltan a su pompa (0 = sin pompa). */
    val bubble: Int = 0,
) {
    /** Diámetro = nivel en celdas; Mega sólo puede llegar aquí desde un save heredado. */
    val radius: Float get() = type.level * 0.5f
}

/**
 * Celda rompible que impide resolver una compresión geométrica entre Teikas. [pushX]/[pushY]
 * describen hacia dónde el cuerpo estaba empujando el bloque y alimentan el VFX direccional.
 */
data class ForcedTerrainBreak(
    val position: Position,
    val pushX: Float,
    val pushY: Float
)

/**
 * Mundo físico de los Teikas: integrador propio de cuerpos rígidos circulares
 * (gravedad + restitución + fricción) con colisión círculo↔círculo y círculo↔celda
 * ocupada del grid (terreno). El tablero es un CONTENEDOR CERRADO por los 4 lados:
 * un Teika jamás sale de él, ni por desborde ni por empuje.
 *
 * Todos los métodos públicos están sincronizados: el bucle de simulación (60 fps) y la
 * lógica de juego (bajo gameMutex) tocan el mundo desde corutinas distintas.
 */
class TeikaPhysicsWorld(private val gridSize: Int = GameState.GRID_SIZE) {

    companion object {
        // ── Constantes de feel (tunables) — unidades: celdas y segundos ──────────────
        /** Gravedad hacia abajo. ~3 celdas de caída en el primer medio segundo. */
        const val GRAVITY = 26f
        /** Por debajo de esta velocidad normal de impacto no hay rebote (mata el jitter). */
        const val BOUNCE_MIN_SPEED = 2.2f
        /**
         * Conservación tangencial en un IMPACTO real (1 = hielo, <1 frena el rodado). Es lo que
         * evita que un Teika recién caído patine por el tablero.
         *
         * **Solo se aplica en impactos** (|vn| ≥ [BOUNCE_MIN_SPEED]) y **una vez por substep**
         * (pasada 0). Antes se aplicaba en las TRES pasadas de colisión de cada substep, es decir
         * seis veces por frame: 0,94⁶ ≈ 0,69, o sea que un cuerpo perdía el 31 % de su velocidad
         * lateral CADA FRAME solo por estar apoyado. Con el teléfono inclinado eso dejaba la
         * velocidad terminal en ~0,3 celdas/s — el "no se mueven" del bug de inclinación.
         */
        const val FRICTION_TANGENTIAL = 0.94f
        /**
         * Conservación tangencial en un CONTACTO DE REPOSO (apoyado, sin golpe). Es la fricción de
         * RODADURA, y tiene que ser muchísimo más suave que la de impacto: un Teika apoyado sobre
         * otro o sobre madera debe poder RODAR cuesta abajo cuando el jugador inclina, que es la
         * mecánica entera. Aplicada una vez por substep, retiene ~0,16 por segundo, así que sin
         * inclinación el cuerpo sigue asentándose solo (con la ayuda de [LINEAR_DAMPING]).
         */
        const val FRICTION_RESTING = 0.985f
        /** Amortiguación lineal por segundo (resistencia del aire). */
        const val LINEAR_DAMPING = 0.30f
        /** Impulso vertical del "pop" al nacer de una fusión. */
        const val FUSION_POP_IMPULSE = 7.5f
        /** Impulso vertical al nacer de un reventón de cluster. */
        const val SPAWN_POP_IMPULSE = 5f
        /** Gracia post-nacimiento: el recién nacido no fusiona (se ve el pop). */
        const val FUSION_GRACE_MS = 300L
        /** Velocidad máxima (evita túneles a través del terreno). */
        const val MAX_SPEED = 34f
        /** Paso fijo de integración (2 substeps por frame de 60 fps). */
        const val FIXED_DT = 1f / 120f
        /** Penetración a partir de la cual la corrección se RACIONA (expulsión suave) en vez de
         *  aplicarse entera: cuando un bloque aparece DEBAJO/ENCIMA de un cuerpo, este se desliza
         *  fuera a [EJECT_RATE] en lugar de teletransportarse (el "salto" se veía como bug). */
        const val DEEP_PENETRATION = 0.18f
        /** Margen MÁS ALLÁ de la tangencia dentro del cual dos cuerpos IGUALES siguen contando
         *  como "en contacto" para la fusión. Dos cuerpos en reposo apoyados uno contra otro
         *  quedan separados a distancia exacta rSum (solape 0); sin este margen jamás volvían a
         *  reportarse y una pareja igual podía quedar "tocándose para siempre" sin fusionar
         *  (p. ej. si su primer contacto cayó dentro de la gracia post-nacimiento). */
        const val CONTACT_REPORT_EPS = 0.04f
        /** Velocidad máxima de expulsión de una penetración profunda (celdas/s). */
        const val EJECT_RATE = 9f
        /**
         * Segundos que se simulan para saber dónde se asientan los cuerpos ([predictRestingCells]).
         * Una caída de todo el tablero dura ~0,8 s; el resto cubre el rebote, el rodado y las pompas.
         */
        const val REST_PREDICTION_SEC = 2.5f
        /** Margen que filtra el error numérico de una tangencia: superarlo significa que no cabe. */
        const val FORCED_TERRAIN_PENETRATION_EPS = 0.04f
        /**
         * Solapamiento mínimo círculo↔celda para considerarla "ocupada" por el Teika
         * (colocación bloqueada / comodín / obstáculo de la gravedad de madera). Margen
         * ESTRECHO a propósito: la madera no puede aterrizar en celdas que el círculo pisa
         * de verdad (con 0.12 se veía al bloque "dentro" de la burbuja del Teika).
         *
         * **ATADO A [FORCED_TERRAIN_PENETRATION_EPS], y no es cosmético** (bug `bugbloque.jpeg`,
         * 2026-09-05). Los dos números miden lo mismo desde lados opuestos:
         *
         *  · aquí se decide **dónde NO puede colocar** el jugador: la celda se bloquea si el círculo
         *    la pisa más allá de `r − OCCUPY_OVERLAP`;
         *  · y en `detectForcedTerrainBreaks` se decide **qué madera revienta la física**: la celda
         *    se rompe si el círculo la penetra más allá de `r − FORCED_TERRAIN_PENETRATION_EPS`.
         *
         * Con 0,06 aquí y 0,04 allí quedaba una **franja muerta de 0,02 celdas** en la que el juego
         * DEJABA colocar y la física destruía el bloque en el fotograma siguiente: el jugador
         * gastaba la ficha, veía la animación de rotura, no se completaba ninguna línea y no había
         * nada que hubiera podido hacer distinto. Es el síntoma exacto que se reportó — «me dejó
         * colocarla y al colocarla hizo una animación de romperse sin que se rompa la fila».
         *
         * Igualándolos, la franja desaparece: **todo lo que la física está dispuesta a romper es
         * terreno donde el jugador no podía colocar**. Se deriva en vez de copiarse para que no
         * puedan volver a separarse sin que alguien lo note.
         */
        const val OCCUPY_OVERLAP = FORCED_TERRAIN_PENETRATION_EPS
        /** Solape residual mínimo para considerar que un cuerpo sigue empujando al otro. */
        const val FORCED_BODY_OVERLAP_EPS = 0.015f
        /** ── ANTI-ENCARAMADO (2026-07-24, decisión del usuario) ───────────────────────────
         *  Un cuerpo apoyado JUSTO en la cima de otro está en equilibrio perfecto: la separación
         *  empuja por la línea de centros, que ahí es vertical, así que no hay ninguna componente
         *  lateral que rompa la simetría y la torre se queda de pie para siempre. En un Suika de
         *  verdad esa pila se deshace sola. Estas constantes meten el desempate: mientras el
         *  contacto sea CASI vertical, el de arriba recibe una aceleración lateral suave hacia el
         *  lado al que ya está descentrado (y, en el empate exacto, hacia un lado estable derivado
         *  de su id) hasta que rueda al hueco. */
        /** |componente horizontal| de la normal por debajo de la cual el contacto se considera
         *  "cima" (≈ dentro de 20° de la vertical). Por encima ya hay pendiente y rueda solo. */
        const val APEX_SLIP_NX = 0.35f
        /** Aceleración lateral del desempate (celdas/s²). Suave: desliza, no dispara. */
        const val APEX_SLIP_ACCEL = 7f
        /** Por encima de esta velocidad lateral el cuerpo YA se está yendo: no se le empuja más. */
        const val APEX_SLIP_MAX_VX = 1.4f
        /** Iteraciones del solucionador de contactos por substep. */
        const val BODY_SOLVER_PASSES = 6
        /**
         * Pasadas de estabilización FINAL. El solucionador normal trabaja con todos los contactos
         * del mismo snapshot (bueno para que una cadena sea determinista), pero una corrección de
         * terreno o de pared puede crear un nuevo solape después de esa foto. Estas pasadas son la
         * fase de proyección: vuelven a medir después de cada corrección y no terminan mientras
         * quede una penetración resoluble. Sin esta segunda fase, una pila densa podía quedar
         * visualmente estable pero con el T1 todavía incrustado entre dos cuerpos grandes.
         */
        const val FINAL_POSITION_SOLVER_PASSES = 10
        /** Margen más allá del radio para encontrar el bloque que cierra la dirección de escape. */
        const val FORCED_TERRAIN_REACH = 0.10f
        /** Inclinación (premium): gravedad HORIZONTAL máxima con el teléfono inclinado a tope
         *  (celdas/s²). Los Teikas ruedan hacia el lado que el jugador inclina.
         *
         *  Baja de 18 a 12 (playtest 2026-07-27: «se mueven muy rápido»). 18 era el 69 % de
         *  [GRAVITY] y, sumado a la curva de respuesta que exageraba los ángulos pequeños, un ladeo
         *  de muñeca mandaba los Teikas de pared a pared. Con 12 el tope sigue siendo un empuje
         *  claro (≈46 % de la gravedad vertical) pero la aceleración lateral ya no compite con la
         *  caída, que es lo que rompía la lectura de la escena. */
        const val TILT_GRAVITY_MAX = 12f
        /** ── RESISTENCIA A LA RODADURA de la inclinación ─────────────────────────────────────
         *  Lo que hacía que los Teikas «volaran» no era solo la aceleración: es que NADA la
         *  limitaba. Un cuerpo rodando 8 celdas con 9 celdas/s² llega a ~12 celdas/s, y el único
         *  tope era [MAX_SPEED] (34), pensado para evitar túneles, no para el feel.
         *
         *  Esto es un arrastre CUADRÁTICO (`-k·v·|v|`), o sea el rozamiento de rodadura de verdad:
         *  no recorta nada de golpe —un tope duro se ve como un frenazo— sino que hace emerger una
         *  VELOCIDAD TERMINAL, `v = √(g/k)`. Con k = 0,20: un ladeo cómodo de 25° (g ≈ 4,8) rueda a
         *  ~4,9 celdas/s, y el tope de inclinación (g = 12) a ~7,7. El tablero (8 celdas) se cruza
         *  en ~1,6 s en vez de en un latigazo, y el jugador puede CORREGIR a media rodada.
         *
         *  Solo actúa con la inclinación fuera de la zona muerta: sin ella el mundo se comporta
         *  exactamente igual que antes (pops de fusión, abducción y balística intactos). */
        const val TILT_ROLL_DRAG = 0.20f
        /** ── Modo BATIDO (Timoteo / Tembleque) ───────────────────────────────────────────
         *  Mientras el tablero se agita, los cuerpos rebotan VIVOS (restitución alta en vez de la
         *  de reposo estilo Suika) y cada golpe fuerte contra la madera rompible la PARTE; la
         *  lógica de juego resuelve después cristal, estrellas y feedback. */
        const val RAMPAGE_RESTITUTION = 0.82f
        /** Velocidad normal de impacto mínima para que un golpe en modo batido rompa la celda. */
        const val RAMPAGE_BREAK_SPEED = 3f
        /** Zona muerta de la inclinación (fracción de g): el teléfono en la mano/mesa con un
         *  ángulo pequeño NO mueve los Teikas (sin derrape fantasma).
         *  La regla viva es [com.cukiti.teikafusion.model.TiltRules.DEADZONE]; esto se conserva
         *  como el valor histórico de referencia. */
        const val TILT_DEADZONE = 0.08f
        /** ── ASISTENCIA DE INCLINACIÓN (rework 2026-07-25b tras playtest) ─────────────────────
         *  Un cuerpo encajado en una hendidura de la pila puede quedarse quieto con el teléfono bien
         *  inclinado: la gravedad lateral entra, pero el reparto del apoyo la anula.
         *
         *  La primera versión de esto daba UN IMPULSO seco (3,2 celdas/s)
         *  cada vez que el cuerpo llevaba 150 ms parado. Era justo el bug que reportó el usuario:
         *  salto → frenada → salto, "como si tuviera lag", y encima más visible en pilas, donde el
         *  cuerpo de arriba recibía el suyo y el de abajo el suyo, descoordinados.
         *
         *  Ahora la ayuda es una ACELERACIÓN CONTINUA que RAMPEA: cuanto más lleva atascado, más
         *  empuja, hasta [TILT_ASSIST_MAX]. Al moverse decae igual de suave. Sin discontinuidades,
         *  así que no hay tirón que ver. */
        /** ── ROZAMIENTO ESTÁTICO (stiction) ──────────────────────────────────────────────────
         *  Contrapartida obligatoria de haber aligerado [FRICTION_RESTING]: con la rodadura suave,
         *  una inclinación de apenas 5° —la que tiene el móvil por el mero hecho de sostenerlo— ya
         *  bastaba para que los Teikas empezaran a derrapar solos por el tablero. Un objeto real
         *  apoyado no se mueve hasta que la fuerza lateral vence su rozamiento estático; eso es lo
         *  que falta aquí. Por debajo de [TILT_STICTION_G] un cuerpo prácticamente quieto se queda
         *  quieto. Es el equivalente físico de la zona muerta del sensor, y hace falta además de
         *  ella: la zona muerta filtra el ruido, esto filtra el ÁNGULO DE SUJECIÓN. */
        /** Gravedad lateral (celdas/s²) por debajo de la cual un cuerpo apoyado NO se despega.
         *  Baja de 4 a 2,5 al hacerse LINEAL la respuesta del sensor: con la curva vieja este umbral
         *  se cruzaba ya a ~8° (de ahí que el tablero se moviera con solo sostener el móvil); ahora
         *  hacen falta ~15° de intención real, que es donde debía estar desde el principio. */
        const val TILT_STICTION_G = 2.5f
        /** |vx| por debajo de la cual el cuerpo cuenta como "en reposo" para el rozamiento estático. */
        const val TILT_STICTION_VX = 0.6f
        /** Retención por substep dentro de la stiction: mata la fluencia sin congelar el cuerpo. */
        const val TILT_STICTION_DAMP = 0.90f
        /** Gravedad lateral (celdas/s²) a partir de la cual la inclinación cuenta como FRANCA.
         *  Reescalado con [TILT_GRAVITY_MAX] (18 → 12) y la respuesta lineal: 4 son ahora ~23°. */
        const val TILT_ASSIST_MIN_G = 4f
        /** Por debajo de esta |vx| (celdas/s) el cuerpo se considera PARADO en horizontal. */
        const val TILT_ASSIST_STALL_VX = 0.55f
        /** Segundos atascado para que la ayuda llegue a su tope (rampa lineal desde 0). */
        const val TILT_ASSIST_RAMP_SEC = 0.35f
        /** Aceleración lateral máxima de la ayuda (celdas/s²). Suma a la gravedad de inclinación.
         *  De 11 a 6: la ayuda es para DESATASCAR, no para acelerar; con 11 sobre la gravedad nueva
         *  el cuerpo liberado salía disparado y se volvía a ver el "salto" que este sistema vino a
         *  quitar. Sigue pasando el arrastre de rodadura, así que no dispara la velocidad terminal. */
        const val TILT_ASSIST_MAX = 6f
        /** Cuánto más rápido DECAE la rampa al soltarse el cuerpo (evita que siga empujando). */
        const val TILT_ASSIST_DECAY = 3f

        /** Alcance adicional de la onda con la que un resultado de fusión reclama su origen. */
        const val FUSION_PUSH_RANGE = 0.35f

        // (2026-09-22) `RECOIL_RANGE` se mudó a [LineRecoil.RANGE]: la cuenta del empujón es ahora
        // una función pura que comparten la física y el aviso del arrastre.
        /** Frenado por substep de un cuerpo pegado al imán (P-240): se queda colgado, no rebota. */
        const val MAGNET_CLING_DAMP = 0.9f
        /** P-243: holgura con la que un cuerpo «toca» la boca de un portal. */
        const val PORTAL_REACH = 0.06f
        /** P-243: margen vertical: el cuerpo puede asomar un poco por encima/debajo de la boca. */
        const val PORTAL_SLACK = 0.15f
        /** P-243: tras cruzar, el cuerpo no puede volver a entrar durante este tiempo (s). */
        const val PORTAL_COOLDOWN = 0.45f

        /**
         * Celdas que cada círculo SOLAPA de forma significativa (≥ [OCCUPY_OVERLAP]).
         * Compartido entre el mundo (colocación/comodín) y la UI (hint de cluster).
         */
        fun occupiedCellsOf(bodies: List<TeikaBody>, gridSize: Int = GameState.GRID_SIZE): Map<Position, String> {
            val out = HashMap<Position, String>()
            for (b in bodies) {
                val rr = b.radius - OCCUPY_OVERLAP
                if (rr <= 0f) continue
                val minX = floor(b.x - rr).toInt().coerceAtLeast(0)
                val maxX = floor(b.x + rr).toInt().coerceAtMost(gridSize - 1)
                val minY = floor(b.y - rr).toInt().coerceAtLeast(0)
                val maxY = floor(b.y + rr).toInt().coerceAtMost(gridSize - 1)
                for (cy in minY..maxY) for (cx in minX..maxX) {
                    // Punto de la celda más cercano al centro; solapa si queda dentro del radio reducido.
                    val nx = b.x.coerceIn(cx.toFloat(), cx + 1f)
                    val ny = b.y.coerceIn(cy.toFloat(), cy + 1f)
                    val dx = b.x - nx; val dy = b.y - ny
                    if (dx * dx + dy * dy < rr * rr) out.putIfAbsent(Position(cx, cy), b.id)
                }
            }
            return out
        }
    }

    /** Estado interno mutable (solo dentro del mundo; hacia fuera siempre snapshots). */
    private class Body(
        val id: String,
        var type: TeikaType,
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        var frozenTurns: Int = 0,
        var kinematic: Boolean = false,
        var graceUntilMs: Long = 0L,
        /**
         * Segundos acumulados PARADO en horizontal con la inclinación franca. Alimenta la rampa de
         * la ASISTENCIA DE INCLINACIÓN (ver [TILT_ASSIST_MAX]): sube mientras el cuerpo sigue
         * clavado y baja cuando rueda, así la ayuda entra y sale de forma continua.
         */
        var tiltStallSec: Float = 0f,
        /** P-238: golpes que le faltan a su pompa. Con pompa, el cuerpo SUBE en vez de caer. */
        var bubble: Int = 0,
        /** P-243: segundos que le quedan sin poder volver a entrar en un portal (evita el ping-pong). */
        var portalCooldown: Float = 0f
    ) {
        val r: Float get() = type.level * 0.5f
        val mass: Float get() = r * r          // masa ∝ área → los grandes apartan a los chicos
        val static: Boolean get() = frozenTurns > 0 || kinematic
    }

    /** Contacto geométrico de un substep. Se calcula antes de aplicar las correcciones. */
    private data class BodyContact(
        val a: Body,
        val b: Body,
        val nx: Float,
        val ny: Float,
        val overlap: Float
    )

    private val bodies = ArrayList<Body>()
    private var terrain: Set<Position> = emptySet()
    private var breakableTerrain: Set<Position> = emptySet()
    // Espejo empaquetado (x,y → Long) de [terrain] para el test de pertenencia del bucle de física:
    // el escaneo de celdas del anti-atasco / espacio forzado consulta MILES de celdas por frame y
    // la mayoría NO son terreno — con `Position(cx,cy) in terrain` cada consulta fallida asignaba un
    // Position. Con el espejo, solo se crea Position cuando la celda de verdad es terreno y penetra.
    private var terrainPacked = HashSet<Long>()
    private var breakableTerrainPacked = HashSet<Long>()
    // Scratch reutilizables del bucle: evitan asignar un Set nuevo por frame/substep (el step corre
    // CADA frame). Se limpian al inicio de cada uso; el bucle de física es mono-hilo (un único
    // coroutine de frame), así que compartirlos entre substeps es seguro.
    private val contactsScratch = LinkedHashSet<Pair<String, String>>()
    private var accumulator = 0f
    // Cola direccional + latch: una obstrucción se reporta UNA vez y no vuelve a entrar cada frame
    // mientras el ViewModel compromete la retirada de la celda bajo gameMutex.
    private val pendingForcedBreaks = LinkedHashMap<Position, ForcedTerrainBreak>()
    private val latchedForcedBreaks = HashSet<Position>()
    // Inclinación del dispositivo → gravedad horizontal (celdas/s², + = derecha).
    private var gravityX = 0f
    // Timoteo / Tembleque: modo batido — rebotes vivos + los golpes fuertes rompen terreno.
    private var rampage = false
    // Contenedor jugable (Trono Real / muros): por defecto el tablero completo [0, gridSize].
    // Con asedio del Rey se estrecha al interior (p. ej. [1, 7] con depth 1) para que los cuerpos
    // no se queden "dentro" de las celdas-muro aunque el terreno aún no se haya refrescado.
    private var boundLeft = 0f
    private var boundTop = 0f
    private var boundRight = gridSize.toFloat()
    private var boundBottom = gridSize.toFloat()
    // Corte Ninja (Dojo en Sombras): muro VERTICAL invisible en x = centerWallX (null = sin corte).
    // Con el tablero partido, cada cuerpo queda confinado en su mitad según su centro — un Teika
    // jamás cruza al otro lado. Lo deriva GameViewModel.onTeikaPhysicsFrame del estado cada frame.
    private var centerWallX: Float? = null
    // Flora / Cañón (rework it.54): proyectil BALÍSTICO — bola de billar sin gravedad ni damping
    // (velocidad constante [ballisticSpeed]) que cuenta REBOTES en paredes/terreno/cuerpos. Al
    // agotar [ballisticBouncesLeft] vuelve a la física normal y se frena solo.
    private var ballisticId: String? = null
    private var ballisticBouncesLeft = 0
    private var ballisticSpeed = 0f
    private var ballisticBouncedThisSubstep = false
    private var pinballSecondsLeft = 0f
    private val pinballBreakable = HashSet<Position>()
    private val pinballImpacts = LinkedHashSet<Position>()
    private val pinballContacts = ArrayList<Triple<Float,Float,String>>()
    @Synchronized fun drainPinballContacts() = pinballContacts.toList().also { pinballContacts.clear() }

    @Synchronized fun setPinballBreakable(cells: Set<Position>) { pinballBreakable.clear(); pinballBreakable.addAll(cells) }
    @Synchronized fun drainPinballImpacts(): List<Position> = pinballImpacts.toList().also { pinballImpacts.clear() }
    @Synchronized fun pinballSnapshot(): PinballFlight? {
        if(pinballSecondsLeft<=0f) return null
        val body=bodies.find { it.id==ballisticId } ?: return null
        return PinballFlight(body.id,body.vx,body.vy,ballisticBouncesLeft,pinballSecondsLeft)
    }
    @Synchronized fun saveSnapshot(): Pair<PinballFlight?,List<TeikaBody>> = pinballSnapshot() to snapshotUnlocked()
    @Synchronized fun launchPinball(flight: PinballFlight): Boolean {
        if(!PinballRules.valid(flight) || ballisticId!=null) return false
        val body=bodies.find { it.id==flight.bodyId } ?: return false
        if(body.static || body.bubble>0 || body.type.level !in 1..4) return false
        body.vx=flight.vx; body.vy=flight.vy
        ballisticId=body.id; ballisticSpeed=PinballRules.SPEED
        ballisticBouncesLeft=flight.bouncesLeft; pinballSecondsLeft=flight.secondsLeft
        return true
    }
    private fun finishPinball() { pinballSecondsLeft=0f; ballisticId=null; ballisticBouncesLeft=0 }
    // ── Gestión de cuerpos ───────────────────────────────────────────────────────────

    @Synchronized
    fun setTerrain(cells: Set<Position>, breakableCells: Set<Position> = cells) {
        // IDENTIDAD, no contenido: el llamante (GameViewModel.physicsTerrainCells) cachea el Set y
        // devuelve EL MISMO objeto mientras el tablero no cambia. Sin esta guarda se reconstruía el
        // espejo empaquetado —un HashSet nuevo con ~40-64 Long boxeados— en CADA frame de física,
        // 60 veces por segundo toda la partida, justo la basura por frame que la pasada de
        // rendimiento 2026-07-31 vino a quitar del bucle. Comparar referencias es O(1) y no puede
        // dar falso positivo: la caché crea un Set nuevo en cuanto cambia una celda.
        if (cells === terrain && breakableCells === breakableTerrain) return
        terrain = cells
        breakableTerrain = breakableCells
        val packed = HashSet<Long>(cells.size * 2)
        for (p in cells) packed.add(packCell(p.x, p.y))
        terrainPacked = packed
        val breakablePacked = HashSet<Long>(breakableCells.size * 2)
        for (p in breakableCells) breakablePacked.add(packCell(p.x, p.y))
        breakableTerrainPacked = breakablePacked
        latchedForcedBreaks.retainAll(breakableCells)
        pendingForcedBreaks.keys.retainAll(breakableCells)
    }

    /** Empaqueta una celda (x,y) en un Long para pertenencia sin asignar Position. */
    private fun packCell(x: Int, y: Int): Long = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)

    /**
     * Define el rectángulo jugable en unidades de celda (bordes exteriores del área libre).
     * Hoy solo la lava del Volcán estrecha el techo (top = lavaRow); el asedio del Trono Real, que
     * estrechaba los cuatro lados, se retiró el 2026-09-23.
     */
    @Synchronized
    fun setPlayableBounds(left: Float, top: Float, right: Float, bottom: Float) {
        boundLeft = left.coerceAtLeast(0f)
        boundTop = top.coerceAtLeast(0f)
        boundRight = right.coerceAtMost(gridSize.toFloat()).coerceAtLeast(boundLeft + 0.01f)
        boundBottom = bottom.coerceAtMost(gridSize.toFloat()).coerceAtLeast(boundTop + 0.01f)
    }

    /** Corte Ninja: levanta (x en celdas) o retira (null) el muro vertical central. */
    @Synchronized
    fun setCenterWall(x: Float?) { centerWallX = x }

    /** Reposiciona un cuerpo (p. ej. empuje al cerrar muros del Rey). Anula velocidad residual. */
    @Synchronized
    fun setBodyPosition(id: String, x: Float, y: Float): Boolean {
        val b = bodies.find { it.id == id } ?: return false
        val minX = boundLeft + b.r
        val maxX = boundRight - b.r
        val minY = boundTop + b.r
        val maxY = boundBottom - b.r
        b.x = x.coerceIn(minX.coerceAtMost(maxX), maxX.coerceAtLeast(minX))
        b.y = y.coerceIn(minY.coerceAtMost(maxY), maxY.coerceAtLeast(minY))
        b.vx = 0f
        b.vy = 0f
        return true
    }

    /** Inclinación premium: gravedad horizontal (celdas/s², + = hacia la derecha). */
    @Synchronized
    fun setHorizontalGravity(gx: Float) {
        gravityX = gx.coerceIn(-TILT_GRAVITY_MAX, TILT_GRAVITY_MAX)
    }

    /**
     * **EL TACTO DE LOS CUERPOS DE ESTE NIVEL** (familia D de las reglas de la casa, 2026-09-04).
     *
     * Multiplicadores que el nivel aplica al perfil físico de TODOS sus cuerpos: ligeros que flotan
     * y ruedan lejos, pesados que caen a plomo. Es la única puerta por la que un nivel puede tocar
     * el mundo físico, y multiplica el perfil por tier en vez de sustituirlo — así la jerarquía de
     * tamaños se conserva (los pequeños siguen respondiendo más que los grandes), que es media mitad
     * Suika del juego. Ver [TeikaFeelRules].
     *
     * El neutro es el mundo de siempre, y es lo que reciben los ~24 de cada 28 niveles que no llevan
     * ninguna de estas reglas: el sim no se entera de que la familia existe.
     */
    @Synchronized
    fun setBodyFeel(modifier: TeikaFeelModifier) {
        bodyFeel = modifier
    }

    @Volatile
    private var bodyFeel: TeikaFeelModifier = TeikaFeelModifier.NEUTRAL

    /**
     * **EL IMÁN ES UNA FICHA** (P-240, 2026-09-22).
     *
     * Centro del imán en coordenadas de celda y su alcance; **alcance cero apaga la regla**, y es lo
     * que reciben todos los niveles sin imán y los turnos en que no hay ninguno puesto: la pasada del
     * imán ni siquiera entra al bucle. Ver [MagnetPiece].
     */
    @Synchronized
    fun setMagnet(x: Float, y: Float, range: Float) {
        magnetX = x
        magnetY = y
        magnetRange = range.coerceAtLeast(0f)
    }

    @Volatile
    private var magnetRange = 0f
    private var magnetX = 0f
    private var magnetY = 0f

    /**
     * **TEIKAS DE PLOMO** (P-239, 2026-09-22): velocidad vertical de impacto a partir de la cual un
     * cuerpo que cae sobre una celda rompible la APLASTA. Cero apaga la regla. Ver [LeadTeikas].
     *
     * El mundo solo detecta el golpe y lo encola; qué se rompe de verdad (estrella, cristal,
     * protecciones de bioma) lo decide la lógica de juego al drenar [drainLeadCrushes].
     */
    @Synchronized
    fun setLeadCrushSpeed(speed: Float) {
        leadCrushSpeed = speed.coerceAtLeast(0f)
    }

    /**
     * **LOS PORTALES** (P-243, 2026-09-22): fila de arriba de la boca izquierda y de la derecha, o -1
     * si no hay portales. Cada boca mide [Portals.SPAN] casillas. Una boca cuya salida está tapada
     * por terreno no deja salir (se comprueba al cruzar, contra el terreno de ese momento).
     */
    @Synchronized
    fun setPortals(leftTop: Int, rightTop: Int) {
        portalLeftTop = leftTop
        portalRightTop = rightTop
    }

    private var portalLeftTop = -1
    private var portalRightTop = -1
    private val pendingPortalHops = ArrayList<PortalHopInfo>()

    /** Un paso por un portal: el tipo del cuerpo y de dónde a dónde (en casillas). */
    data class PortalHopInfo(val type: TeikaType, val fromX: Float, val fromY: Float, val toX: Float, val toY: Float)

    /** Los pasos por portal desde el último drenaje, para que la lógica de juego los dibuje. */
    @Synchronized
    fun drainPortalHops(): List<PortalHopInfo> {
        if (pendingPortalHops.isEmpty()) return emptyList()
        val out = ArrayList(pendingPortalHops)
        pendingPortalHops.clear()
        return out
    }

    /** P-238: pone (o quita, con 0) la pompa de un cuerpo. */
    @Synchronized
    fun setBubble(id: String, hits: Int): Boolean {
        val b = bodies.find { it.id == id } ?: return false
        b.bubble = hits.coerceAtLeast(0)
        return true
    }

    /**
     * **LA BRÚJULA** (P-241, 2026-09-22): hacia dónde tira la gravedad de los cuerpos, como vector
     * unitario (0,1) = abajo, el mundo de siempre. Ver [com.cukiti.teikafusion.model.Compass].
     */
    @Synchronized
    fun setGravityDirection(dx: Float, dy: Float) {
        gravityDirX = dx
        gravityDirY = dy
    }

    private var gravityDirX = 0f
    private var gravityDirY = 1f

    @Volatile
    private var leadCrushSpeed = 0f
    private val pendingLeadCrushes = LinkedHashMap<Position, TeikaType>()

    /** Celdas aplastadas por una caída de plomo desde el último drenaje, con el tier que cayó. */
    @Synchronized
    fun drainLeadCrushes(): Map<Position, TeikaType> {
        if (pendingLeadCrushes.isEmpty()) return emptyMap()
        val out = LinkedHashMap(pendingLeadCrushes)
        pendingLeadCrushes.clear()
        return out
    }

    /** El perfil de [type] con el tacto del nivel ya aplicado. Fuente única del sim. */
    private fun profileOf(type: TeikaType): TeikaPhysicsProfile {
        val base = TeikaPhysicsProfiles.of(type)
        val f = bodyFeel
        if (f.isNeutral) return base
        return base.copy(
            tiltResponse = base.tiltResponse * f.tilt,
            rollingDrag = base.rollingDrag * f.drag,
            restitution = (base.restitution * f.restitution).coerceIn(0f, 0.85f),
        )
    }

    /** Timoteo / Tembleque: activa o apaga el modo BATIDO (rebotes vivos + rotura por impacto). */
    @Synchronized
    /**
     * ⚠ **SIN NINGÚN LLAMANTE QUE LO ENCIENDA desde 2026-08-27.** El modo batido existía para el
     * «Tembleque» de Morti (cuerpos rebotando vivos que rompían la madera que golpeaban); su
     * habilidad es POSESIÓN y no toca la física. Con él quedan inalcanzables [RAMPAGE_RESTITUTION],
     * [RAMPAGE_BREAK_SPEED], la rama de rotura forzada del solver y, aguas arriba,
     * `drainForcedTerrainBreaks`/`breakForcedWood` del ViewModel.
     *
     * Se conserva —en vez de podarlo— porque es maquinaria de FÍSICA con tests propios y podarla es
     * una decisión aparte del rework de habilidades. Si no vuelve una habilidad que agite el
     * tablero, este bloque debe morir: un fósil hace creer al siguiente que la mecánica existe.
     */
    fun setRampage(active: Boolean) { rampage = active }

    /**
     * De dónde sale el id de cada cuerpo nuevo. El orden de resolución de choques ordena los cuerpos
     * POR ID y desempata con su `hashCode`, así que con ids al azar la misma partida sembrada rodaba
     * distinto cada vez (2026-09-28: el bot de niveles no se repetía). La app usa UUID al azar, como
     * siempre; el bot lo siembra con [seedBodyIds].
     */
    private var newBodyId: () -> String = { java.util.UUID.randomUUID().toString() }

    /** Ids de cuerpo repetibles a partir de [seed] (mismo formato UUID). Solo para el bot de niveles. */
    @Synchronized
    fun seedBodyIds(seed: Long) {
        val r = kotlin.random.Random(seed)
        newBodyId = { java.util.UUID(r.nextLong(), r.nextLong()).toString() }
    }

    @Synchronized
    fun addBody(
        type: TeikaType,
        x: Float,
        y: Float,
        vx: Float = 0f,
        vy: Float = 0f,
        id: String = newBodyId(),
        nowMs: Long = clockMs,
    ): TeikaBody {
        val b = Body(
            id = id, type = type,
            x = x.coerceIn(type.level * 0.5f, gridSize - type.level * 0.5f),
            y = y.coerceIn(type.level * 0.5f, gridSize - type.level * 0.5f),
            vx = vx, vy = vy,
            graceUntilMs = nowMs + FUSION_GRACE_MS
        )
        bodies.add(b)
        return b.snapshot()
    }

    /**
     * Crea el resultado de una fusión EN su origen y hace que reclame físicamente ese espacio.
     *
     * A diferencia del antiguo buscador de huecos, nunca recorre el tablero ni teletransporta el
     * resultado a una posición libre. El centro sólo se ajusta si el nuevo radio atravesaría el
     * borde exterior; después, una onda radial empuja los cuerpos móviles cercanos. La separación
     * final y la apertura geométrica de madera siguen perteneciendo al integrador normal.
     */
    @Synchronized
    fun addFusionBody(
        type: TeikaType,
        x: Float,
        y: Float,
        vy: Float = -FUSION_POP_IMPULSE,
        id: String = newBodyId(),
        nowMs: Long = clockMs,
    ): TeikaBody {
        val radius = type.level * 0.5f
        val born = Body(
            id = id,
            type = type,
            x = x.coerceIn(radius, gridSize - radius),
            y = y.coerceIn(radius, gridSize - radius),
            vx = 0f,
            vy = vy,
            graceUntilMs = nowMs + FUSION_GRACE_MS
        )
        bodies.add(born)

        val profile = TeikaPhysicsProfiles.of(type)
        if (profile.fusionPushSpeed > 0f) {
            for (other in bodies) {
                if (other === born || other.static) continue
                var dx = other.x - born.x
                var dy = other.y - born.y
                val d2 = dx * dx + dy * dy
                val contactDistance = born.r + other.r
                val reach = contactDistance + FUSION_PUSH_RANGE
                if (d2 >= reach * reach) continue

                val distance = sqrt(d2)
                if (distance > 1e-4f) {
                    dx /= distance
                    dy /= distance
                } else {
                    // Superposición exacta: dirección estable derivada de ids, nunca aleatoria por frame.
                    when ((born.id.hashCode() xor other.id.hashCode()) and 3) {
                        0 -> { dx = -1f; dy = 0f }
                        1 -> { dx = 1f; dy = 0f }
                        2 -> { dx = 0f; dy = -1f }
                        else -> { dx = 0f; dy = 1f }
                    }
                }

                val proximity = if (distance <= contactDistance) 1f
                    else ((reach - distance) / FUSION_PUSH_RANGE).coerceIn(0f, 1f)
                val massFactor = sqrt(born.mass / other.mass).coerceIn(0.45f, 1.75f)
                val impulse = profile.fusionPushSpeed * proximity * massFactor
                other.vx = (other.vx + dx * impulse).coerceIn(-MAX_SPEED, MAX_SPEED)
                other.vy = (other.vy + dy * impulse).coerceIn(-MAX_SPEED, MAX_SPEED)
                other.tiltStallSec = 0f
            }
        }
        return born.snapshot()
    }

    @Synchronized fun removeBody(id: String): Boolean {
        if (id == ballisticId) ballisticId = null
        return bodies.removeAll { it.id == id }
    }

    @Synchronized fun getBody(id: String): TeikaBody? = bodies.find { it.id == id }?.snapshot()

    private var turbineBeam: TurbineBeam?=null
    private var turbineElapsed=0f
    private var turbineObstacles: Set<Position> = emptySet()
    private var turbineWidth=8
    private var turbineHeight=8
    private var turbineSplit=false
    @Synchronized fun setTurbine(beam: TurbineBeam?,elapsed: Float,obstacles: Set<Position>,width: Int,height: Int,split: Boolean) {
        turbineBeam=beam; turbineElapsed=elapsed; turbineObstacles=obstacles
        turbineWidth=width; turbineHeight=height; turbineSplit=split
    }
    /** Restore motion only: never thaw or release a protected body. */
    @Synchronized fun restoreTurbineVelocities(saved: List<TurbineVelocity>) {
        saved.forEach { v -> bodies.find { it.id==v.id && !it.static && it.bubble==0 }?.let { b ->
            if(v.vx.isFinite() && v.vy.isFinite()) {
                b.vx=v.vx.coerceIn(-MAX_SPEED,MAX_SPEED); b.vy=v.vy.coerceIn(-MAX_SPEED,MAX_SPEED)
                val speed=sqrt(b.vx*b.vx+b.vy*b.vy)
                if(speed>MAX_SPEED) { b.vx*=MAX_SPEED/speed; b.vy*=MAX_SPEED/speed }
            }
        } }
    }

    @Synchronized fun clear() {
        turbineBeam=null; turbineObstacles=emptySet(); turbineElapsed=0f
        bodies.clear(); accumulator = 0f
        pendingForcedBreaks.clear(); latchedForcedBreaks.clear()
        pendingLeadCrushes.clear()
        pendingPortalHops.clear()
        portalLeftTop = -1; portalRightTop = -1
        gravityDirX = 0f; gravityDirY = 1f
        magnetRange = 0f
        ballisticId = null; ballisticBouncesLeft = 0
        pinballSecondsLeft=0f; pinballImpacts.clear(); pinballBreakable.clear(); pinballContacts.clear()
    }

    @Synchronized fun isEmpty(): Boolean = bodies.isEmpty()

    /** Reemplaza todos los cuerpos (carga de partida guardada / migración). */
    @Synchronized
    fun load(saved: List<TeikaBody>, nowMs: Long = clockMs) {
        bodies.clear()
        saved.forEach { s ->
            // La gracia post-nacimiento no se serializa (TeikaBodySave es posición+tipo): sin
            // re-otorgarla aquí, una pareja igual que quedó en contacto al guardar (dentro de su
            // gracia o del espaciado de cadencia) fusionaba en el PRIMER frame tras cargar, con el
            // tablero aún apareciendo. La fusión legítima solo se pospone esos ~300 ms, ya visible.
            bodies.add(Body(
                id = s.id, type = s.type, x = s.x, y = s.y, vx = s.vx, vy = s.vy,
                frozenTurns = s.frozenTurns, kinematic = s.kinematic,
                graceUntilMs = nowMs + FUSION_GRACE_MS,
                bubble = s.bubble,
            ))
        }
    }

    /** Cambia el tier de un cuerpo in-place (degradación Ninja, decreto T1→T2). */
    @Synchronized
    fun setBodyType(id: String, type: TeikaType): Boolean {
        val b = bodies.find { it.id == id } ?: return false
        b.type = type
        b.x = b.x.coerceIn(b.r, gridSize - b.r)
        return true
    }

    /**
     * Flora / Cañón: lanza el cuerpo [id] con velocidad ([vx],[vy]) (celdas/s, capada a [MAX_SPEED]).
     * Lo despierta (quita congelación/kinematic) y le quita la gracia de fusión. El resto (rebotes en
     * paredes/cuerpos, fricción hasta frenar) lo hace el integrador normal.
     */
    @Synchronized
    fun trampolineBounce(id: String, vx: Float, vy: Float): Boolean {
        val b = bodies.find { it.id == id } ?: return false
        if (b.frozenTurns > 0 || b.kinematic || b.vy <= 0f) return false
        b.vx = vx.coerceIn(-MAX_SPEED, MAX_SPEED)
        b.vy = vy.coerceIn(-MAX_SPEED, MAX_SPEED)
        return true
    }

    /** A line wave gives motion, never unfreezes a captured/icy passenger or forces a fusion. */
    @Synchronized
    fun surfPush(id: String, direction: Int): Boolean {
        val b=bodies.find { it.id==id } ?: return false
        if(b.frozenTurns>0 || b.kinematic || b.bubble>0) return false
        b.vx=(if(direction>=0) 6f else -6f)
        b.vy=minOf(b.vy,-1.8f)
        return true
    }

    @Synchronized
    fun launchBody(id: String, vx: Float, vy: Float): Boolean {
        val b = bodies.find { it.id == id } ?: return false
        b.frozenTurns = 0
        b.kinematic = false
        b.graceUntilMs = 0L
        b.vx = vx.coerceIn(-MAX_SPEED, MAX_SPEED)
        b.vy = vy.coerceIn(-MAX_SPEED, MAX_SPEED)
        return true
    }

    /**
     * Flora / Cañón: arma (o desarma con `id = null`) el modo BALÍSTICO del cuerpo [id]: mantiene
     * su velocidad en [speed] (sin gravedad ni damping) y rebota elástico en paredes, terreno y
     * cuerpos, descontando [bounces]. Al agotarse, el cuerpo vuelve a la física normal.
     */
    @Synchronized
    fun setBallistic(id: String?, bounces: Int = 0, speed: Float = 0f) {
        pinballSecondsLeft=0f
        ballisticId = id
        ballisticBouncesLeft = bounces
        ballisticSpeed = speed.coerceIn(0f, MAX_SPEED)
    }

    /** Rebotes que le quedan al proyectil balístico (0 = sin proyectil o presupuesto agotado). */
    @Synchronized
    fun ballisticBouncesLeft(): Int = if (ballisticId != null) ballisticBouncesLeft else 0

    /** Un rebote REAL del proyectil: descuenta presupuesto (máx. 1 por substep — un choque de
     *  esquina toca 2 celdas el mismo substep y no debe costar doble). Al llegar a 0, desarma. */
    private fun onBallisticBounce(b: Body) {
        if (b.id != ballisticId || ballisticBouncedThisSubstep) return
        if(pinballSecondsLeft>0f) pinballContacts.add(Triple(b.x,b.y,b.id))
        ballisticBouncedThisSubstep = true
        ballisticBouncesLeft--
        if (ballisticBouncesLeft <= 0) ballisticId = null
    }

    /**
     * **EL REBOTE** (regla de la casa, 2026-09-05): la línea que revienta EMPUJA a los cuerpos.
     *
     * Todos los cuerpos libres salen despedidos **perpendicularmente** y en dirección contraria a la
     * línea rota: los que están por encima de una fila suben, los de debajo bajan; con una columna,
     * a izquierda y derecha. Es lo que convierte romper una línea en una herramienta de COLOCACIÓN —
     * eliges qué fila rompes según dónde quieras que acaben los cuerpos.
     *
     * Tres decisiones que no son casuales:
     *
     *  · **No despierta a nadie.** A diferencia de [launchBody] (el cañón de Flora, que es una
     *    habilidad dirigida), el rebote respeta `static`: un cuerpo congelado por el Glaciar sigue
     *    congelado y uno raptado por la planta sigue raptado. Una regla de la casa no puede deshacer
     *    la mecánica del bioma por la puerta de atrás.
     *  · **Cae con la distancia**, como la onda de la fusión: el cuerpo que estaba encima de la
     *    línea sale disparado y el del otro extremo del tablero apenas se entera. Sin esto, romper
     *    una fila barajaría el tablero entero y la elección de QUÉ fila romper no significaría nada.
     *  · **Suma a la velocidad, no la sustituye**, y se capa a [MAX_SPEED]. Dos líneas de una misma
     *    cadena empujan dos veces, que es lo que hace que una cadena se sienta como una cadena.
     *
     * @param alongX `true` si la línea rota es una FILA (empuja en vertical), `false` si es columna.
     * @param lineCoord la `y` de la fila o la `x` de la columna, en celdas (centro = índice + 0,5).
     * @param speed impulso en celdas/s a distancia cero. Lo dicta el dial de capítulo de la regla.
     * @return cuántos cuerpos se han movido de verdad (0 = no hubo rebote que enseñar).
     */
    @Synchronized
    fun recoilFromLine(
        alongX: Boolean,
        lineCoord: Float,
        speed: Float,
        /** P-237: si se pasa, se apunta aquí a QUIÉN se empujó, para que el VFX lo enseñe. */
        pushesOut: MutableList<com.cukiti.teikafusion.model.GameEvent.RecoilPush.Push>? = null,
    ): Int {
        if (speed <= 0f) return 0
        var movidos = 0
        for (b in bodies) {
            if (b.static) continue
            val centro = if (alongX) b.y else b.x
            // La cuenta es la de [LineRecoil.impulseFor], la MISMA que dibuja las flechas del aviso
            // del arrastre: si fueran dos, la flecha prometería un empujón distinto del que llega.
            val impulso = com.cukiti.teikafusion.model.LineRecoil.impulseFor(centro, b.r, lineCoord, speed)
            if (impulso == 0f) continue
            if (alongX) b.vy = (b.vy + impulso).coerceIn(-MAX_SPEED, MAX_SPEED)
            else b.vx = (b.vx + impulso).coerceIn(-MAX_SPEED, MAX_SPEED)
            b.tiltStallSec = 0f
            b.graceUntilMs = 0L
            movidos++
            pushesOut?.add(com.cukiti.teikafusion.model.GameEvent.RecoilPush.Push(
                id = b.id, atX = b.x, atY = b.y,
                dirX = if (alongX) 0f else kotlin.math.sign(impulso),
                dirY = if (alongX) kotlin.math.sign(impulso) else 0f,
                power = (kotlin.math.abs(impulso) / speed).coerceIn(0f, 1f),
            ))
        }
        return movidos
    }

    /** Glaciar: fija los turnos de congelación (0 = descongelar). */
    @Synchronized
    fun setFrozenTurns(id: String, turns: Int): Boolean {
        val b = bodies.find { it.id == id } ?: return false
        b.frozenTurns = turns.coerceAtLeast(0)
        return true
    }

    /** Jardín: modo kinemático (la raíz lo arrastra; la física lo ignora). */
    @Synchronized
    fun setKinematic(id: String, kinematic: Boolean, x: Float? = null, y: Float? = null): Boolean {
        val b = bodies.find { it.id == id } ?: return false
        b.kinematic = kinematic
        if (x != null) b.x = x
        if (y != null) b.y = y
        if (kinematic) {
            b.vx = 0f; b.vy = 0f
        } else {
            // Jardín: al SOLTAR el cuerpo (rescate/expiración del rapto) la raíz suele haberlo dejado
            // solapado con un gemelo del mismo tier que le puso en el camino. Sin gracia, el frame
            // siguiente lo fusionaría de golpe ("la raíz se lo lleva y se fusionan al irse la raíz").
            // Con la gracia post-liberación la física tiene ese margen para EJECT-separarlos: si de
            // verdad quedan encima el uno del otro, se apartan antes de que la fusión vuelva a habilitarse.
            b.graceUntilMs = clockMs + FUSION_GRACE_MS
        }
        return true
    }

    @Synchronized
    fun snapshot(): List<TeikaBody> = bodies.map { it.snapshot() }

    /**
     * Celdas del grid que cada círculo SOLAPA de forma significativa (≥ [OCCUPY_OVERLAP])
     * → bloquean colocación de piezas y cuentan como comodín en clusters.
     * Si varios cuerpos tocan la misma celda gana el primero (irrelevante para el uso).
     */
    @Synchronized
    fun occupiedCells(): Map<Position, String> = occupiedCellsOf(snapshotUnlocked(), gridSize)

    private fun snapshotUnlocked(): List<TeikaBody> = bodies.map { it.snapshot() }

    /**
     * **DÓNDE SE VAN A QUEDAR** (2026-09-26, decisión del usuario). Las celdas que ocuparán los cuerpos
     * cuando terminen de caer, rodar o subir en su pompa, sin que el jugador incline.
     *
     * La bandeja se reparte justo después de la cadena, y ahí los Teikas que acaban de nacer siguen
     * flotando donde estaba la línea: el reparto prometía un hueco que el Teika iba a ocupar un segundo
     * después. Aquí se simula una COPIA del mundo —mismo contenedor, muro, gravedad, tacto, imán y
     * portales, con [terrain] como madera— durante [seconds] y se leen sus celdas. El mundo real no se
     * toca. Las fusiones no se simulan (solo se reportan), así que la lectura es la prudente: dos
     * cuerpos que se fundirían siguen contando como dos.
     */
    @Synchronized
    fun predictRestingCells(terrain: Set<Position>, seconds: Float = REST_PREDICTION_SEC): Map<Position, String> {
        if (bodies.isEmpty()) return emptyMap()
        val ghost = TeikaPhysicsWorld(gridSize)
        ghost.setTerrain(terrain)
        ghost.boundLeft = boundLeft; ghost.boundTop = boundTop
        ghost.boundRight = boundRight; ghost.boundBottom = boundBottom
        ghost.centerWallX = centerWallX
        ghost.gravityDirX = gravityDirX; ghost.gravityDirY = gravityDirY
        ghost.bodyFeel = bodyFeel
        ghost.magnetRange = magnetRange; ghost.magnetX = magnetX; ghost.magnetY = magnetY
        ghost.portalLeftTop = portalLeftTop; ghost.portalRightTop = portalRightTop
        ghost.rampage = rampage
        bodies.forEach { b ->
            ghost.bodies.add(Body(
                id = b.id, type = b.type, x = b.x, y = b.y, vx = b.vx, vy = b.vy,
                frozenTurns = b.frozenTurns, kinematic = b.kinematic,
                graceUntilMs = Long.MAX_VALUE, bubble = b.bubble,
            ))
        }
        val chunk = FIXED_DT * 8
        var t = 0f
        while (t < seconds) {
            ghost.step(chunk, 0L)
            t += chunk
        }
        return occupiedCellsOf(ghost.snapshotUnlocked(), gridSize)
    }

    /**
     * Celdas rompibles que impidieron resolver una compresión desde el último drenaje. La lógica de
     * juego conoce el material, objetivos y VFX; el mundo sólo garantiza geometría y dirección.
     */
    @Synchronized
    fun drainForcedTerrainBreaks(): List<ForcedTerrainBreak> {
        if (pendingForcedBreaks.isEmpty()) return emptyList()
        val out = pendingForcedBreaks.values.toList()
        pendingForcedBreaks.clear()
        return out
    }

    // ── Simulación ───────────────────────────────────────────────────────────────────

    /**
     * Avanza la simulación [dtSec] segundos (troceados en substeps de [FIXED_DT]) y devuelve
     * los pares de cuerpos del MISMO tipo que están en contacto (candidatos a fusión).
     * Los congelados/kinemáticos y los recién nacidos (gracia) no se reportan.
     */
    /**
     * B-3 (2026-09-27): el RELOJ DE SIMULACIÓN, en ms. Avanza con los pasos de física ([advanceClock])
     * y no con la hora del sistema. Las gracias de fusión y la cadencia de fusiones iban con
     * `System.currentTimeMillis()` mientras el resto del motor corre con el reloj de fotogramas: el bot,
     * que avanza un reloj virtual, veía fusionar en fotogramas distintos con la MISMA semilla según lo
     * rápido que fuera la máquina. En el juego es equivalente —un fotograma de física es un fotograma—
     * y además se para en pausa, que es lo correcto para una gracia de «recién nacido».
     */
    @Volatile
    var clockMs: Long = 0L
        private set
    private var clockExactMs = 0.0

    @Synchronized
    fun advanceClock(dtSec: Float) {
        clockExactMs += dtSec * 1000.0
        clockMs = clockExactMs.toLong()
    }

    @Synchronized
    fun step(dtSec: Float, nowMs: Long): List<Pair<String, String>> {
        if (bodies.isEmpty()) { accumulator = 0f; return emptyList() }
        accumulator = (accumulator + dtSec).coerceAtMost(FIXED_DT * 8)
        val contacts = contactsScratch.also { it.clear() }
        while (accumulator >= FIXED_DT) {
            accumulator -= FIXED_DT
            substep(FIXED_DT, nowMs, contacts)
        }
        return if (contacts.isEmpty()) emptyList() else contacts.toList()
    }

    private fun substep(dt: Float, nowMs: Long, contacts: MutableSet<Pair<String, String>>) {
        ballisticBouncedThisSubstep = false
        if(pinballSecondsLeft>0f) {
            pinballSecondsLeft-=dt
            val body=bodies.find { it.id==ballisticId }
            if(pinballSecondsLeft<=0f || body==null || body.static || body.bubble>0) finishPinball()
        }
        turbineElapsed+=dt*1000f
        // 1. Integración (gravedad vertical + inclinación horizontal + damping + posición).
        for (b in bodies) {
            if (b.static) continue
            if (b.id == ballisticId) {
                // Proyectil balístico: sin gravedad ni damping — velocidad constante renormalizada
                // (los impulsos de choque no le roban energía mientras le queden rebotes).
                val sp = sqrt(b.vx * b.vx + b.vy * b.vy)
                if (sp > 1e-3f && ballisticSpeed > 0f) {
                    val s = ballisticSpeed / sp; b.vx *= s; b.vy *= s
                }
                b.x += b.vx * dt
                b.y += b.vy * dt
                continue
            }
            val wind=turbineBeam?.takeIf { beam -> b.bubble==0 && turbineElapsed<TurbineRules.DURATION_MS &&
                TurbineRules.reaches(beam,b.x,b.y,turbineObstacles,turbineWidth,turbineHeight,turbineSplit) }
            val profile = profileOf(b.type)
            val lateralGravity = gravityX * profile.tiltResponse
            // La gravedad vertical la escala el TACTO DEL NIVEL (familia D): en un nivel ligero los
            // cuerpos flotan y en uno pesado se desploman. Sigue siendo la misma para todos los
            // tiers dentro del nivel, que es lo que mantiene legible la lectura de tamaños.
            // P-238 · LAS POMPAS: un cuerpo en su pompa SUBE, despacio, hasta el techo del contenedor.
            if (b.bubble > 0) b.vy -= (TeikaBubbles.LIFT - GRAVITY * bodyFeel.gravity) * dt
            else {
                // P-241 · LA BRÚJULA: la gravedad puede apuntar a cualquiera de las cuatro paredes.
                val g = GRAVITY * bodyFeel.gravity * dt
                b.vx += gravityDirX * g
                b.vy += gravityDirY * g
            }
            b.vx += lateralGravity * dt
            // ASISTENCIA DE INCLINACIÓN: acompaña a la gravedad lateral cuando el cuerpo lleva rato
            // clavado pese al teléfono ladeado. Va AQUÍ, sumada a la gravedad y escalada por dt, para
            // que sea indistinguible de ella — un impulso aparte se veía como un tirón (ver KDoc de
            // TILT_ASSIST_MAX). El contador se actualizó al final del substep anterior.
            if (b.tiltStallSec > 0f && abs(lateralGravity) >= TILT_ASSIST_MIN_G) {
                val ramp = (b.tiltStallSec / TILT_ASSIST_RAMP_SEC).coerceIn(0f, 1f)
                b.vx += (if (lateralGravity > 0f) 1f else -1f) *
                    TILT_ASSIST_MAX * profile.tiltResponse * ramp * dt
            }
            // RESISTENCIA A LA RODADURA: arrastre cuadrático que fija la velocidad terminal del
            // rodado por inclinación (√(g/k)) en vez de dejar que el cuerpo acelere hasta MAX_SPEED.
            // Es una FUERZA, no un tope: no hay frenazo que ver, solo un cuerpo que deja de ganar
            // velocidad. Solo con la inclinación activa → sin ella el mundo es idéntico al de antes.
            if (lateralGravity != 0f && b.vx != 0f) {
                b.vx -= TILT_ROLL_DRAG * profile.rollingDrag * b.vx * abs(b.vx) * dt
            }
            // ROZAMIENTO ESTÁTICO: con el móvil casi plano, un cuerpo prácticamente quieto no se
            // pone a deslizar solo. Sin esto, la fricción de rodadura suave convertía el ángulo de
            // sujeción del teléfono en una corriente que arrastraba el tablero entero.
            if (wind==null && abs(lateralGravity) < TILT_STICTION_G && abs(b.vx) < TILT_STICTION_VX) {
                b.vx *= TILT_STICTION_DAMP
            }
            wind?.let { beam ->
                    val along=b.vx*beam.dx+b.vy*beam.dy
                    val mass=b.r*b.r
                    val gain=(TurbineRules.FORCE/mass*TurbineRules.envelope(turbineElapsed)*dt)
                        .coerceAtMost((TurbineRules.SPEED-along).coerceAtLeast(0f))
                    b.vx+=gain*beam.dx; b.vy+=gain*beam.dy
            }
            val damp = (1f - LINEAR_DAMPING * dt).coerceAtLeast(0f)
            b.vx *= damp; b.vy *= damp
            val sp2 = b.vx * b.vx + b.vy * b.vy
            if (sp2 > MAX_SPEED * MAX_SPEED) {
                val s = MAX_SPEED / sqrt(sp2); b.vx *= s; b.vy *= s
            }
            b.x += b.vx * dt
            b.y += b.vy * dt
        }
        // 1a. LOS PORTALES (P-243): el cuerpo que empuja contra una boca abierta sale por la otra.
        if (portalLeftTop >= 0 && portalRightTop >= 0) {
            for (b in bodies) {
                if (b.static || b.id == ballisticId) continue
                if (b.portalCooldown > 0f) { b.portalCooldown -= dt; continue }
                if (!Portals.fits(b.type)) continue
                val inLeft = b.x - b.r <= boundLeft + PORTAL_REACH && b.vx < 0f &&
                    b.y - b.r >= portalLeftTop - PORTAL_SLACK && b.y + b.r <= portalLeftTop + Portals.SPAN + PORTAL_SLACK
                val inRight = b.x + b.r >= boundRight - PORTAL_REACH && b.vx > 0f &&
                    b.y - b.r >= portalRightTop - PORTAL_SLACK && b.y + b.r <= portalRightTop + Portals.SPAN + PORTAL_SLACK
                if (!inLeft && !inRight) continue
                val offset = b.y - (if (inLeft) portalLeftTop else portalRightTop)
                val ny = ((if (inLeft) portalRightTop else portalLeftTop) + offset)
                    .coerceIn(boundTop + b.r, boundBottom - b.r)
                val nx = if (inLeft) boundRight - b.r - PORTAL_REACH * 2f else boundLeft + b.r + PORTAL_REACH * 2f
                if (circleHitsTerrain(nx, ny, b.r)) continue   // salida tapada: la boca está cerrada
                pendingPortalHops.add(PortalHopInfo(b.type, b.x, b.y, nx, ny))
                b.x = nx
                b.y = ny
                b.portalCooldown = PORTAL_COOLDOWN
            }
        }
        // 1b. EL IMÁN (P-240): todo cuerpo al alcance del imán colocado tira hacia él.
        //
        //     Va DESPUÉS de la integración y ANTES del solver a propósito: es una fuerza más —se suma
        //     a la velocidad como la gravedad—, así que el solver la resuelve en el mismo substep y el
        //     jugador nunca ve una interpenetración que el imán haya causado. O(n), y se salta entero
        //     con un `if` cuando no hay imán puesto, que es casi siempre.
        if (magnetRange > 0f) {
            for (b in bodies) {
                if (b.static || b.id == ballisticId) continue
                val dx = magnetX - b.x
                val dy = magnetY - b.y
                val d2 = dx * dx + dy * dy
                if (d2 < 1e-6f) continue
                val d = sqrt(d2)
                // PEGADO al imán: el cuerpo ya ha llegado y se queda colgado de él. Se anula la
                // gravedad y se frena, en vez de seguir tirando: seguir tirando lo aplastaría contra
                // la casilla (y dispararía la válvula de rotura por compresión), y soltarlo lo haría
                // caer y volver a subir en bucle, que se ve como un cuerpo que tiembla.
                if (d - b.r <= 0.55f) {
                    b.vy -= GRAVITY * bodyFeel.gravity * dt
                    b.vx *= MAGNET_CLING_DAMP
                    b.vy *= MAGNET_CLING_DAMP
                    continue
                }
                val pull = MagnetPiece.pullAt(d, magnetRange)
                if (pull <= 0f) continue
                val accel = MagnetPiece.PULL_ACCEL * pull * dt
                b.vx += dx / d * accel
                b.vy += dy / d * accel
            }
        }
        // 2. Resolución de colisiones. La respuesta de velocidad se calcula una vez en un orden
        //    estable; la posición se corrige con un solucionador JACOBI (todos los contactos leen
        //    el mismo snapshot y sus desplazamientos se aplican juntos). Así una cadena T1→T2→T3
        //    transmite la corrección por todo el componente y el resultado no depende del orden en
        //    que los cuerpos llegaron a la lista.
        //    La FRICCIÓN y el desempate de cima solo se aplican en la pasada 0: son efectos por
        //    unidad de TIEMPO, no por iteración del solver. Aplicarlos en las tres pasadas los
        //    elevaba al cubo (y al sexto con los dos substeps del frame), que es lo que ahogaba el
        //    movimiento lateral con el teléfono inclinado. Las pasadas 1 y 2 solo separan.
        val stableBodies = bodies.sortedBy { it.id }
        repeat(BODY_SOLVER_PASSES) { pass ->
            val firstPass = pass == 0
            if (firstPass) for (i in stableBodies.indices) for (j in i + 1 until stableBodies.size) {
                collideBodies(
                    stableBodies[i], stableBodies[j],
                    reportTo = if (firstPass) contacts else null,
                    nowMs = nowMs,
                    applyDamping = true,
                    resolvePosition = false
                )
            }
            resolveBodyContactGraph(dt)
            for (b in stableBodies) {
                if (b.kinematic) continue
                collideWalls(b, applyDamping = firstPass)
                collideTerrain(b, applyDamping = firstPass)
            }
        }
        // 2b. ASISTENCIA DE INCLINACIÓN: se actualiza el contador de "atascado" con la velocidad ya
        //     resuelta (aquí `vx` es la real de después del reparto de apoyos). La ayuda en sí la
        //     aplica la INTEGRACIÓN del substep siguiente, sumada a la gravedad lateral y rampeada:
        //     así entra y sale de forma continua en vez de a empujones (bug de "cortes" reportado).
        for (b in bodies) {
            val lateralGravity = gravityX * profileOf(b.type).tiltResponse
            if (b.static || b.id == ballisticId) {
                b.tiltStallSec = 0f
            } else if (abs(lateralGravity) >= TILT_ASSIST_MIN_G) {
                if (abs(b.vx) >= TILT_ASSIST_STALL_VX) {
                    // Ya rueda: la ayuda se retira DEGRADANDO, no de golpe.
                    b.tiltStallSec = (b.tiltStallSec - TILT_ASSIST_DECAY * dt).coerceAtLeast(0f)
                } else {
                    b.tiltStallSec = (b.tiltStallSec + dt).coerceAtMost(TILT_ASSIST_RAMP_SEC)
                }
            } else {
                b.tiltStallSec = (b.tiltStallSec - TILT_ASSIST_DECAY * dt).coerceAtLeast(0f)
            }
        }
        // 3. Anti-atasco: si el CENTRO de un cuerpo sigue dentro de una celda sólida (dos
        //    celdas opuestas pueden ANULAR sus empujes entre sí — tira y afloja), sube
        //    racionado: arriba es siempre la salida natural de una pila. Determinista →
        //    rompe el empate en vez de quedarse superpuesto para siempre.
        for (b in bodies) {
            if (b.static) continue
            val ccx = floor(b.x).toInt()
            val ccy = floor(b.y).toInt()
            if (packCell(ccx, ccy) in terrainPacked) {
                b.y -= EJECT_RATE * dt
                if (b.vy > 0f) b.vy = 0f
            }
        }
        // Fase final: ahora que también terminó el anti-atasco de terreno, se vuelve a proyectar
        // el conjunto completo. Es lo que convierte la reacción local en una reacción en cadena
        // real: si el T4 desplaza al T3 y el T3 al T1, la corrección sigue recorriendo el componente
        // hasta que todos quedan separados o el tablero demuestra que no existe espacio físico.
        stabilizeBodyPositions()
        // 3b. Si la geometría sigue comprimida contra terreno rompible, abre el hueco ya.
        detectForcedTerrainBreaks()
    }

    /**
     * Detecta penetración cuerpo↔terreno o una cadena cuerpo↔cuerpo sin espacio de separación.
     * Un apoyo normal queda tangente y no entra; una compresión irresoluble reporta la celda en el
     * primer substep, sin acumular tiempo ni fuerza.
     */
    private fun detectForcedTerrainBreaks() {
        if (bodies.isEmpty() || breakableTerrainPacked.isEmpty()) return
        for (a in bodies) {
            if (a.static) continue
            // ── Cuña cuerpo↔TERRENO (sin necesitar un segundo cuerpo) ───────────────────────
            // Si tras las pasadas de resolución de ESTE substep el círculo SIGUE penetrando una
            // celda sólida, la expulsión está bloqueada (p. ej. madera nacida DEBAJO del cuerpo
            // por un movedor de bioma/habilidad, o restaurada así de un save: el empuje normal
            // apunta al suelo y el suelo lo devuelve — teikabloque.jpeg). Ese solape RESIDUAL es
            // una obstrucción real: se reporta ya para que la lógica quite el bloque y acredite su
            // estrella. El contacto de REPOSO se corrige entero cada substep y queda en pen≈0.
            val minX = floor(a.x - a.r).toInt()
            val maxX = floor(a.x + a.r).toInt()
            val minY = floor(a.y - a.r).toInt()
            val maxY = floor(a.y + a.r).toInt()
            for (cy in minY..maxY) for (cx in minX..maxX) {
                if (packCell(cx, cy) !in breakableTerrainPacked) continue
                val nearestX = a.x.coerceIn(cx.toFloat(), cx + 1f)
                val nearestY = a.y.coerceIn(cy.toFloat(), cy + 1f)
                val towardX = nearestX - a.x
                val towardY = nearestY - a.y
                val distance = sqrt(towardX * towardX + towardY * towardY)
                if (a.r - distance <= FORCED_TERRAIN_PENETRATION_EPS) continue
                val fallbackX = (cx + 0.5f) - a.x
                val fallbackY = (cy + 0.5f) - a.y
                queueForcedTerrainBreak(
                    cx,
                    cy,
                    if (distance > 1e-4f) towardX else if (abs(a.vx) > 1e-3f) a.vx else fallbackX,
                    if (distance > 1e-4f) towardY else if (abs(a.vy) > 1e-3f) a.vy else fallbackY
                )
            }
        }
        collectBodyContacts().forEach { contact ->
            if (contact.overlap <= FORCED_BODY_OVERLAP_EPS) return@forEach
            if (!contact.a.static) detectBlockingBreakable(contact.a, -contact.nx, -contact.ny)
            if (!contact.b.static) detectBlockingBreakable(contact.b, contact.nx, contact.ny)
        }
    }

    private fun detectBlockingBreakable(body: Body, pushX: Float, pushY: Float) {
        val len = sqrt(pushX * pushX + pushY * pushY)
        if (len <= 1e-4f) return
        val dirX = pushX / len
        val dirY = pushY / len
        val reach = body.r + FORCED_TERRAIN_REACH
        val minX = floor(body.x - reach).toInt()
        val maxX = floor(body.x + reach).toInt()
        val minY = floor(body.y - reach).toInt()
        val maxY = floor(body.y + reach).toInt()
        for (cy in minY..maxY) for (cx in minX..maxX) {
            if (packCell(cx, cy) !in breakableTerrainPacked) continue
            val nearestX = body.x.coerceIn(cx.toFloat(), cx + 1f)
            val nearestY = body.y.coerceIn(cy.toFloat(), cy + 1f)
            val towardX = nearestX - body.x
            val towardY = nearestY - body.y
            if (towardX * towardX + towardY * towardY > reach * reach) continue
            if (towardX * dirX + towardY * dirY <= 1e-4f) continue
            queueForcedTerrainBreak(cx, cy, dirX, dirY)
        }
    }

    private fun queueForcedTerrainBreak(cellX: Int, cellY: Int, pushX: Float, pushY: Float) {
        if (packCell(cellX, cellY) !in breakableTerrainPacked) return
        val position = Position(cellX, cellY)
        if (!latchedForcedBreaks.add(position)) return
        val len = sqrt(pushX * pushX + pushY * pushY)
        val nx = if (len > 1e-4f) pushX / len else 0f
        val ny = if (len > 1e-4f) pushY / len else -1f
        pendingForcedBreaks[position] = ForcedTerrainBreak(position, nx, ny)
    }

    /**
     * Fricción tangencial que toca a un contacto con velocidad normal de impacto [vn]: la dura de
     * IMPACTO si es un golpe de verdad, la de RODADURA si el cuerpo solo está apoyado. Sin esta
     * distinción, un Teika en reposo recibía fricción de choque cada substep y no había inclinación
     * que lo moviera.
     */
    private fun frictionFor(vn: Float): Float =
        if (abs(vn) >= BOUNCE_MIN_SPEED) FRICTION_TANGENTIAL else FRICTION_RESTING

    private fun collideWalls(b: Body, applyDamping: Boolean = true) {
        // CONTENEDOR CERRADO al área jugable ([boundLeft..boundRight] × [boundTop..boundBottom]):
        // por defecto el tablero entero; con muros del Rey, el interior del asedio.
        // Un Teika jamás sale de esa caja, ni por desborde ni por empuje.
        // Proyectil balístico (Flora): rebota ELÁSTICO (e=1, sin fricción) y descuenta rebote.
        val ball = b.id == ballisticId
        val minX = boundLeft + b.r
        val maxX = boundRight - b.r
        val minY = boundTop + b.r
        val maxY = boundBottom - b.r
        if (b.x < minX) {
            b.x = minX
            if (b.vx < 0f) { b.vx = -b.vx * (if (ball) 1f else bounce(b.vx, b.type)); if (ball) onBallisticBounce(b) }
        }
        if (b.x > maxX) {
            b.x = maxX
            if (b.vx > 0f) { b.vx = -b.vx * (if (ball) 1f else bounce(b.vx, b.type)); if (ball) onBallisticBounce(b) }
        }
        if (b.y < minY) {
            b.y = minY
            if (b.vy < 0f) {
                if (ball) { b.vy = -b.vy; onBallisticBounce(b) } else b.vy = 0f
            }
        }
        if (b.y > maxY) {
            b.y = maxY
            if (b.vy > 0f) {
                val impact = b.vy
                b.vy = -b.vy * (if (ball) 1f else bounce(b.vy, b.type))
                // Rozamiento contra el SUELO. Con `applyDamping` (pasada 0) y separando golpe de
                // reposo: apoyado en el fondo, un Teika tiene que poder rodar con la inclinación.
                if (ball) onBallisticBounce(b)
                else if (applyDamping) b.vx *= frictionFor(impact)
            }
        }
        // Corte Ninja: muro central vertical. El lado se decide por el CENTRO del cuerpo — al
        // levantarse el muro, un cuerpo que pisaba la frontera se expulsa entero a su mitad (el
        // empujón del tajo). Un T4 (r=2) en una mitad de 4 queda encajado (minX==maxX): se acepta
        // — sigue en juego como comodín; el MEGA (r=2.5) nunca convive con el muro (su irrupción
        // repara el tablero al instante, ver processContactFusion).
        val wall = centerWallX
        if (wall != null) {
            if (b.x < wall) {
                val wallMaxX = wall - b.r
                if (b.x > wallMaxX) {
                    b.x = wallMaxX.coerceAtLeast(minX.coerceAtMost(wallMaxX))
                    if (b.vx > 0f) { b.vx = -b.vx * (if (ball) 1f else bounce(b.vx, b.type)); if (ball) onBallisticBounce(b) }
                }
            } else {
                val wallMinX = wall + b.r
                if (b.x < wallMinX) {
                    b.x = wallMinX.coerceAtMost(maxX.coerceAtLeast(wallMinX))
                    if (b.vx < 0f) { b.vx = -b.vx * (if (ball) 1f else bounce(b.vx, b.type)); if (ball) onBallisticBounce(b) }
                }
            }
        }
    }

    private fun bounce(impactSpeed: Float, type: TeikaType): Float = when {
        kotlin.math.abs(impactSpeed) < BOUNCE_MIN_SPEED -> 0f
        rampage -> RAMPAGE_RESTITUTION   // batido: los cuerpos salen despedidos, no se asientan
        else -> profileOf(type).restitution
    }

    /** En un contacto mixto manda el cuerpo menos elástico: el pesado amortigua al ligero. */
    private fun bounce(impactSpeed: Float, a: Body, b: Body): Float = when {
        kotlin.math.abs(impactSpeed) < BOUNCE_MIN_SPEED -> 0f
        rampage -> RAMPAGE_RESTITUTION
        else -> minOf(
            profileOf(a.type).restitution,
            profileOf(b.type).restitution
        )
    }

    /** ¿Un círculo en ([x], [y]) de radio [r] pisa alguna celda de terreno? Para la salida de un portal. */
    private fun circleHitsTerrain(x: Float, y: Float, r: Float): Boolean {
        val minX = floor(x - r).toInt(); val maxX = floor(x + r).toInt()
        val minY = floor(y - r).toInt(); val maxY = floor(y + r).toInt()
        for (cy in minY..maxY) for (cx in minX..maxX) {
            if (packCell(cx, cy) !in terrainPacked) continue
            val nx = x.coerceIn(cx.toFloat(), cx + 1f)
            val ny = y.coerceIn(cy.toFloat(), cy + 1f)
            val dx = x - nx; val dy = y - ny
            if (dx * dx + dy * dy < r * r - 1e-4f) return true
        }
        return false
    }

    private fun collideTerrain(b: Body, applyDamping: Boolean = true) {
        val minX = floor(b.x - b.r).toInt()
        val maxX = floor(b.x + b.r).toInt()
        val minY = floor(b.y - b.r).toInt()
        val maxY = floor(b.y + b.r).toInt()
        for (cy in minY..maxY) for (cx in minX..maxX) {
            if (packCell(cx, cy) !in terrainPacked) continue
            resolveCircleAabb(b, cx.toFloat(), cy.toFloat(), applyDamping)
        }
    }

    /** Colisión círculo ↔ celda [cellX,cellY]..+1 (AABB estático de masa infinita). */
    private fun resolveCircleAabb(b: Body, cellX: Float, cellY: Float, applyDamping: Boolean = true) {
        val nxP = b.x.coerceIn(cellX, cellX + 1f)
        val nyP = b.y.coerceIn(cellY, cellY + 1f)
        var dx = b.x - nxP
        var dy = b.y - nyP
        val d2 = dx * dx + dy * dy
        if (d2 >= b.r * b.r) return

        var pen: Float
        if (d2 > 1e-9f) {
            val d = sqrt(d2)
            pen = b.r - d
            dx /= d; dy /= d
        } else {
            // Centro DENTRO de la celda (una pieza se estampó encima): expulsar por el eje de
            // menor penetración, con preferencia hacia ARRIBA (lo natural al construir debajo).
            val left = b.x - cellX; val right = cellX + 1f - b.x
            val up = b.y - cellY;  val down = cellY + 1f - b.y
            val m = minOf(left, right, up * 0.6f, down)   // sesgo: salir por arriba es más barato
            when (m) {
                left       -> { dx = -1f; dy = 0f; pen = left + b.r }
                right      -> { dx = 1f;  dy = 0f; pen = right + b.r }
                down       -> { dx = 0f;  dy = 1f; pen = down + b.r }
                else       -> { dx = 0f;  dy = -1f; pen = up + b.r }
            }
        }
        // Penetración PROFUNDA (un bloque acaba de materializarse solapando el cuerpo): salir
        // RACIONADO a EJECT_RATE — deslizamiento suave, no el teletransporte/"salto" de antes.
        // El contacto normal de reposo (pen pequeña) sí se corrige entero (evita hundimiento).
        val applied = if (pen > DEEP_PENETRATION) minOf(pen, EJECT_RATE * FIXED_DT) else pen
        b.x += dx * applied
        b.y += dy * applied
        // Componente normal de la velocidad: reflejar con restitución; tangencial: fricción.
        // Balístico: reflexión ELÁSTICA (e=1, sin fricción) + descuento de rebote.
        val vn = b.vx * dx + b.vy * dy
        if (vn < 0f) {
            val ball = b.id == ballisticId
            if(ball && pinballSecondsLeft>0f) {
                val cell=Position(cellX.toInt(),cellY.toInt())
                if(cell in pinballBreakable) {
                    pinballImpacts.add(cell)
                    terrainPacked.remove(packCell(cell.x,cell.y))
                    breakableTerrainPacked.remove(packCell(cell.x,cell.y))
                }
            }
            // Batido (Timoteo): un golpe fuerte contra la celda la marca para ROMPER — la lógica
            // de juego decide si de verdad cede (la madera de bioma dura no). El rebote sigue.
            if (rampage && !ball && !b.static && -vn >= RAMPAGE_BREAK_SPEED) {
                queueForcedTerrainBreak(cellX.toInt(), cellY.toInt(), -dx, -dy)
            }
            // TEIKAS DE PLOMO (P-239): golpe DESDE ARRIBA (la normal apunta hacia el cuerpo, que está
            // encima: dy < 0) lo bastante rápido. Solo celdas rompibles — la ficha recién puesta
            // (P-187) y la madera protegida por bioma no lo son — y un solo golpe por celda y drenaje.
            if (leadCrushSpeed > 0f && !ball && !b.static && dy < -0.7f && -vn >= leadCrushSpeed) {
                val cx = cellX.toInt(); val cy = cellY.toInt()
                if (packCell(cx, cy) in breakableTerrainPacked) {
                    pendingLeadCrushes.putIfAbsent(Position(cx, cy), b.type)
                }
            }
            val e = if (ball) 1f else bounce(vn, b.type)
            // Fricción SOLO en la pasada 0 (es un efecto por tiempo, no por iteración del solver) y
            // separando golpe de reposo: un Teika apoyado contra madera tiene que rodar al inclinar.
            val fric = if (ball || !applyDamping) 1f else frictionFor(vn)
            val tx = b.vx - vn * dx
            val ty = b.vy - vn * dy
            b.vx = tx * fric - vn * e * dx
            b.vy = ty * fric - vn * e * dy
            if (ball) onBallisticBounce(b)
        }
    }

    /**
     * Lee todos los solapes del componente cuerpo↔cuerpo y aplica sus correcciones a la vez.
     *
     * Un solucionador Gauss-Seidel modifica el primer cuerpo antes de calcular la segunda pareja;
     * en una pila eso hace que el resultado dependa de la posición de cada elemento en [bodies].
     * Aquí cada contacto calcula su desplazamiento desde el mismo estado y luego los desplazamientos
     * se acumulan por id. Es una corrección de posición, no un segundo impulso: la conservación de
     * velocidad sigue viviendo en [collideBodies].
     */
    private fun resolveBodyContactGraph(dt: Float) {
        val contacts = collectBodyContacts()
        if (contacts.isEmpty()) return

        val dx = HashMap<String, Float>(contacts.size * 2)
        val dy = HashMap<String, Float>(contacts.size * 2)
        val deepBudget = EJECT_RATE * dt / BODY_SOLVER_PASSES

        for (contact in contacts) {
            val invA = if (contact.a.static) 0f else 1f / contact.a.mass
            val invB = if (contact.b.static) 0f else 1f / contact.b.mass
            val invSum = invA + invB
            if (invSum <= 0f) continue

            // Los solapes recién materializados se expulsan progresivamente. El presupuesto se
            // reparte entre las iteraciones para conservar la misma velocidad total de EJECT_RATE
            // que tenía el motor anterior, pero sin empujar una pareja varias veces por orden.
            val applied = if (contact.overlap > DEEP_PENETRATION) {
                minOf(contact.overlap, deepBudget)
            } else {
                // Un pequeño factor de relajación evita que tres contactos compartiendo un cuerpo
                // sobrecorrijan el mismo centro en direcciones distintas.
                contact.overlap * 0.86f
            }
            val shareA = if (invA > 0f && invB > 0f) {
                if (contact.overlap > DEEP_PENETRATION) 0.5f else invA / invSum
            } else if (invA > 0f) 1f else 0f
            val shareB = if (invA > 0f && invB > 0f) {
                if (contact.overlap > DEEP_PENETRATION) 0.5f else invB / invSum
            } else if (invB > 0f) 1f else 0f

            if (shareA > 0f) {
                dx[contact.a.id] = (dx[contact.a.id] ?: 0f) - contact.nx * applied * shareA
                dy[contact.a.id] = (dy[contact.a.id] ?: 0f) - contact.ny * applied * shareA
            }
            if (shareB > 0f) {
                dx[contact.b.id] = (dx[contact.b.id] ?: 0f) + contact.nx * applied * shareB
                dy[contact.b.id] = (dy[contact.b.id] ?: 0f) + contact.ny * applied * shareB
            }
        }

        // La aplicación separada de las sumas es lo que hace que un T2 conectado a T1 y T3
        // reciba una única corrección resultante, en lugar de tres empujes secuenciales.
        for (body in bodies) {
            if (body.static) continue
            body.x += dx[body.id] ?: 0f
            body.y += dy[body.id] ?: 0f
        }
    }

    /**
     * Proyección de posición posterior a la resolución normal. A diferencia de
     * [resolveBodyContactGraph], aquí no se raciona una penetración profunda: si un cuerpo ya
     * terminó incrustado, permanecer así no es una animación suave sino un estado inválido. La
     * aparición segura de fusiones evita la mayoría de estos casos; esta fase cubre saves antiguos,
     * arrastres kinematic y cambios de bioma que introducen una frontera nueva.
     */
    private fun stabilizeBodyPositions() {
        repeat(FINAL_POSITION_SOLVER_PASSES) {
            val contacts = collectBodyContacts()
            if (contacts.isEmpty()) return
            for (contact in contacts) {
                val invA = if (contact.a.static) 0f else 1f / contact.a.mass
                val invB = if (contact.b.static) 0f else 1f / contact.b.mass
                val invSum = invA + invB
                if (invSum <= 0f) continue

                val shareA = if (invA > 0f && invB > 0f) invA / invSum else if (invA > 0f) 1f else 0f
                val shareB = if (invA > 0f && invB > 0f) invB / invSum else if (invB > 0f) 1f else 0f
                if (shareA > 0f) {
                    contact.a.x -= contact.nx * contact.overlap * shareA
                    contact.a.y -= contact.ny * contact.overlap * shareA
                }
                if (shareB > 0f) {
                    contact.b.x += contact.nx * contact.overlap * shareB
                    contact.b.y += contact.ny * contact.overlap * shareB
                }
            }
            // Una proyección de borde/terreno puede volver a introducir un contacto corporal; la
            // siguiente pasada lo ve con las posiciones nuevas. Sin este orden el solver podía
            // terminar con una pareja separada y otra recién incrustada por el mismo empuje.
            val ordered = bodies.sortedBy { it.id }
            for (body in ordered) {
                if (body.kinematic) continue
                collideWalls(body, applyDamping = false)
                collideTerrain(body, applyDamping = false)
            }
        }
    }

    /** Contactos ordenados de forma estable para que el solver sea reproducible. */
    private fun collectBodyContacts(): List<BodyContact> {
        val ordered = bodies.sortedBy { it.id }
        val out = ArrayList<BodyContact>()
        for (i in ordered.indices) for (j in i + 1 until ordered.size) {
            val a = ordered[i]
            val b = ordered[j]
            if (a.kinematic && b.kinematic) continue

            var dx = b.x - a.x
            var dy = b.y - a.y
            val d2 = dx * dx + dy * dy
            val rSum = a.r + b.r
            if (d2 >= rSum * rSum) continue

            val d = sqrt(d2)
            if (d > 1e-9f) {
                dx /= d
                dy /= d
            } else {
                // Empate exacto determinista: el id menor queda abajo en el eje de separación.
                dx = 0f
                dy = -1f
            }
            out += BodyContact(a, b, dx, dy, rSum - d)
        }
        return out.sortedWith(
            compareByDescending<BodyContact> { it.overlap }
                .thenBy { it.a.id }
                .thenBy { it.b.id }
        )
    }

    private fun collideBodies(
        a: Body,
        b: Body,
        reportTo: MutableSet<Pair<String, String>>?,
        nowMs: Long,
        applyDamping: Boolean = true,
        resolvePosition: Boolean = true
    ) {
        if (a.kinematic && b.kinematic) return
        var dx = b.x - a.x
        var dy = b.y - a.y
        val rSum = a.r + b.r
        val d2 = dx * dx + dy * dy
        val reportR = rSum + CONTACT_REPORT_EPS
        if (d2 >= reportR * reportR) return
        // Pinball ends at a real body contact, before ordinary fusion/impulse evaluation.
        if(pinballSecondsLeft>0f && (a.id==ballisticId || b.id==ballisticId) && d2<=rSum*rSum) finishPinball()

        // Contacto de dos IGUALES → candidatos a fusión (el ViewModel decide y ejecuta).
        // Se reporta con margen [CONTACT_REPORT_EPS] más allá de la tangencia: una pareja en
        // REPOSO apoyada (solape 0 tras la separación) sigue contando como contacto, así una
        // fusión que se perdió en su momento (gracia post-nacimiento, congelación del Glaciar,
        // cadena en curso) se reintenta cada frame en cuanto los gates se abren.
        if (reportTo != null && a.type == b.type && !a.static && !b.static &&
            nowMs >= a.graceUntilMs && nowMs >= b.graceUntilMs
        ) {
            reportTo.add(if (a.id < b.id) a.id to b.id else b.id to a.id)
        }

        // Tangentes/rozando sin solape real: solo reporte de fusión, sin respuesta física.
        if (d2 >= rSum * rSum) return

        val d = sqrt(d2)
        if (d > 1e-9f) { dx /= d; dy /= d } else { dx = 0f; dy = -1f }
        val overlap = rSum - d

        // Corrección posicional. El solver de la cadena la desactiva aquí y la aplica de forma
        // simultánea para todos los contactos conectados.
        //  • Solape NORMAL (reposo): corrección COMPLETA repartida por masa inversa (física creíble).
        //  • Solape PROFUNDO (recién nacido de fusión, apretón por inclinación): racionado
        //    (EJECT_RATE, deslizamiento suave) y repartido A PARTES IGUALES entre los dos lados
        //    móviles. Con reparto por masa, el ligero absorbía ~todo el empuje; si estaba
        //    bloqueado contra una pared/bloques, la pared se lo devolvía cada substep y el par
        //    NUNCA convergía (quedaban uno encima del otro). Con 50/50 el lado libre siempre
        //    cede y la separación converge aunque el otro esté acorralado.
        val invA = if (a.static) 0f else 1f / a.mass
        val invB = if (b.static) 0f else 1f / b.mass
        val invSum = invA + invB
        if (invSum <= 0f) return
        if (resolvePosition) {
            val shareA: Float
            val shareB: Float
            val applied: Float
            if (overlap > DEEP_PENETRATION) {
                applied = minOf(overlap, EJECT_RATE * FIXED_DT)
                shareA = if (invA > 0f && invB > 0f) 0.5f else if (invA > 0f) 1f else 0f
                shareB = if (invA > 0f && invB > 0f) 0.5f else if (invB > 0f) 1f else 0f
            } else {
                applied = overlap
                shareA = invA / invSum
                shareB = invB / invSum
            }
            a.x -= dx * applied * shareA
            a.y -= dy * applied * shareA
            b.x += dx * applied * shareB
            b.y += dy * applied * shareB
        }

        // ── ANTI-ENCARAMADO: desempate en la cima ────────────────────────────────────────────
        // Con el contacto casi vertical (|dx| pequeño) el de arriba está en equilibrio perfecto: la
        // separación empuja por la línea de centros y no hay componente lateral que rompa la
        // simetría, así que la torre aguanta indefinidamente y se lee como "un Teika flotando
        // encima de otro" (bug3). Se le da una aceleración lateral suave hacia el lado al que ya
        // está descentrado; en el empate exacto, hacia un lado ESTABLE derivado de su id (constante
        // frame a frame: si se sorteara, temblaría en el sitio sin decidirse).
        // El proyectil balístico (Flora) queda fuera: su trayectoria es la mecánica.
        //
        // Dos correcciones del 2026-07-25b, las dos por el bug de "movimiento a cortes en pilas":
        //  · **Manda la INCLINACIÓN si la hay.** El desempate empujaba siempre hacia el lado al que
        //    el cuerpo ya estaba descentrado, aunque el jugador estuviera inclinando al CONTRARIO:
        //    el de arriba recibía a la vez la gravedad lateral hacia un lado y el desempate hacia el
        //    otro, y se quedaba pelándose consigo mismo encima del de abajo. Con inclinación franca
        //    el desempate va HACIA donde cae la pendiente, que además es lo físicamente correcto.
        //  · **Corte SUAVE en vez de binario.** El gate `|vx| < APEX_SLIP_MAX_VX` encendía y apagaba
        //    la ayuda de golpe: el cuerpo aceleraba, cruzaba el umbral, se quedaba sin empuje,
        //    frenaba, volvía a entrar… un ciclo a ~10 Hz que se ve exactamente como un tartamudeo.
        //    Ahora decae de forma continua hasta anularse en el umbral.
        // Se aplica solo en la pasada 0: es una aceleración por tiempo, no por iteración.
        if (applyDamping && !a.static && !b.static && dx > -APEX_SLIP_NX && dx < APEX_SLIP_NX &&
            a.id != ballisticId && b.id != ballisticId
        ) {
            val upper = if (a.y <= b.y) a else b
            val lower = if (a.y <= b.y) b else a
            val fade = (1f - abs(upper.vx) / APEX_SLIP_MAX_VX).coerceIn(0f, 1f)
            if (fade > 0f) {
                val dir = if (abs(gravityX) >= TILT_ASSIST_MIN_G) {
                    if (gravityX > 0f) 1f else -1f
                } else {
                    val off = upper.x - lower.x
                    when {
                        off > 1e-4f -> 1f
                        off < -1e-4f -> -1f
                        // Empate exacto y sin inclinación: lado ESTABLE derivado del id (constante
                        // frame a frame — si se sorteara, temblaría en el sitio sin decidirse).
                        else -> if (upper.id.hashCode() and 1 == 0) -1f else 1f
                    }
                }
                upper.vx += dir * APEX_SLIP_ACCEL * fade * FIXED_DT
            }
        }

        // Impulso: solo si se acercan. Si uno es el proyectil balístico (Flora), el choque es
        // elástico para él (mantiene su velocidad — la renormaliza el integrador) y cuenta rebote.
        val rvx = b.vx - a.vx
        val rvy = b.vy - a.vy
        val vn = rvx * dx + rvy * dy
        if (vn < 0f) {
            val ballHit = a.id == ballisticId || b.id == ballisticId
            val e = if (ballHit) 1f else bounce(vn, a, b)
            val jImp = -(1f + e) * vn / invSum
            a.vx -= jImp * dx * invA; a.vy -= jImp * dy * invA
            b.vx += jImp * dx * invB; b.vy += jImp * dy * invB
            if (ballHit) onBallisticBounce(if (a.id == ballisticId) a else b)
            // Fricción tangencial del contacto: acopla los dos cuerpos (lo que hace que una pila
            // ruede JUNTA en vez de que el de arriba patine sobre el de abajo). Solo en la pasada 0
            // y con el coeficiente de reposo cuando no hay golpe — con el de impacto aplicado tres
            // veces por substep, la pila se agarrotaba y la inclinación no la movía.
            if (applyDamping && !ballHit) {
                val tvx = rvx - vn * dx
                val tvy = rvy - vn * dy
                val f = (1f - frictionFor(vn)) / invSum
                a.vx += tvx * f * invA; a.vy += tvy * f * invA
                b.vx -= tvx * f * invB; b.vy -= tvy * f * invB
            }
        }
    }

    private fun Body.snapshot() = TeikaBody(
        id, type, x, y, vx, vy, frozenTurns, kinematic, bubble
    )
}
