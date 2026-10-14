package com.cukiti.teikafusion.model

/**
 * **LA MINI-PARTIDA DE CADA HABILIDAD** (pedido 2026-09-30): el guion que el minitablero del
 * Inventario reproduce en bucle para que el jugador VEA qué hace el poder sin leer nada.
 *
 * Sustituye a la demo de P-269, que pintaba madera quieta y encima el VFX de partida centrado: el
 * tablero nunca cambiaba —nada se rompía, caía ni se movía— y la animación no contaba la regla.
 *
 * Un guion es una lista de [DemoFrame]: fotos del tablero. Entre una foto y la siguiente cada
 * [DemoTile] se reconoce por su `id`: si sigue, se desliza (posición, tamaño y color); si ya no está,
 * **revienta**; si es nueva, **brota**. Los [DemoFx] son los adornos que cuentan el «porqué» (la
 * línea que destella, el carámbano, el disparo…). Es un guion a mano, NO el motor: no hay que
 * mantenerlo en paralelo a `GameViewModel`, solo que cuente la misma historia que `mechanicHint`.
 *
 * Coordenadas en CASILLAS del 8×8 (0..8). Las fichas cuadradas se anclan por su esquina superior
 * izquierda; las redondas ([DemoTileKind.round]) por su CENTRO.
 */
enum class DemoTileKind(val round: Boolean = false) {
    WOOD, STAR, CRYSTAL, FIRE, GHOST, TOXIC, GOLD, SLEEP, PLACED,
    TEIKA_1(true), TEIKA_2(true), TEIKA_3(true), FLOWER(true)
}

data class DemoTile(
    val id: Int,
    val x: Float,
    val y: Float,
    val kind: DemoTileKind,
    /** Ancho en casillas (las mitades del Ninja miden 0,5) o diámetro si es redonda. */
    val size: Float = 1f,
    val scale: Float = 1f,
)

/** Tono de un adorno: la UI lo traduce a color. */
enum class FxTone { WHITE, GOLD, FIRE, ELECTRIC, ICE, GHOST, TOXIC, CRYSTAL, NATURE, SLEEP }

sealed interface DemoFx {
    data class RowFlash(val row: Int, val tone: FxTone = FxTone.WHITE) : DemoFx
    data class ColFlash(val col: Int, val tone: FxTone = FxTone.WHITE) : DemoFx
    /** Aro que late alrededor de un punto (Teika energizado). */
    data class Ring(val cx: Float, val cy: Float, val r: Float, val tone: FxTone) : DemoFx
    /** Línea de puntería punteada. */
    data class Aim(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : DemoFx
    /** Bala que recorre un tramo dejando estela. */
    data class Shot(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val tone: FxTone) : DemoFx
    /** Tajo de katana que se traza de punta a punta. */
    data class Slash(val x0: Float, val y0: Float, val x1: Float, val y1: Float) : DemoFx
    /** Carámbano que baja por una columna (la punta va de `y0` a `y1`). */
    data class Icicle(val col: Int, val y0: Float, val y1: Float) : DemoFx
    /** Las fauces de Chueta asomando por debajo, de `open0` a `open1` (0 = cerrada). */
    data class Mouth(val cx: Float, val open0: Float, val open1: Float) : DemoFx
    data class Eclipse(
        val cx0: Float, val cy0: Float, val cx1: Float, val cy1: Float,
        val r0: Float, val r1: Float, val a0: Float = 1f, val a1: Float = 1f,
    ) : DemoFx
    /** Niebla tóxica sobre todo el tablero. */
    data class Fog(val a0: Float, val a1: Float) : DemoFx
    /** Dos estrellitas que suben: «doble estrella». */
    data class Sparkle(val x: Float, val y: Float) : DemoFx
    /** «¡ECO!»: aro dorado mientras el tablero se rehace para la segunda pasada (niveles con eco). */
    data object Echo : DemoFx
}

/** Una foto del tablero; [ms] es lo que tarda en llegar a ella desde la anterior (0 = corte seco). */
data class DemoFrame(val tiles: List<DemoTile>, val ms: Int, val fx: List<DemoFx> = emptyList())

/**
 * El guion ya digerido para dibujar sin buscar nada por fotograma: por cada paso, las parejas
 * (antes, después) de cada ficha, con las que revientan primero para que queden debajo.
 */
class DemoTimeline(val frames: List<DemoFrame>) {
    init { require(frames.isNotEmpty()) }

    val starts: IntArray = IntArray(frames.size).also { s ->
        var acc = 0
        for (i in frames.indices) { s[i] = acc; acc += frames[i].ms }
    }
    val totalMs: Int = frames.sumOf { it.ms }

    /** `before[i][k]` / `after[i][k]`: la ficha k del paso i (una de las dos puede faltar). */
    val before: List<Array<DemoTile?>>
    val after: List<Array<DemoTile?>>

    init {
        val b = ArrayList<Array<DemoTile?>>(frames.size)
        val a = ArrayList<Array<DemoTile?>>(frames.size)
        for (i in frames.indices) {
            val next = frames[i].tiles
            val prev = if (i == 0) next else frames[i - 1].tiles
            val nextIds = next.mapTo(HashSet()) { it.id }
            val prevById = prev.associateBy { it.id }
            val gone = prev.filter { it.id !in nextIds }
            val bb = ArrayList<DemoTile?>(gone.size + next.size)
            val aa = ArrayList<DemoTile?>(gone.size + next.size)
            for (t in gone) { bb += t; aa += null }
            for (t in next) { bb += prevById[t.id]; aa += t }
            b += bb.toTypedArray()
            a += aa.toTypedArray()
        }
        before = b
        after = a
    }

    /** Paso que suena en el instante [t] (ms desde el principio). */
    fun frameAt(t: Int): Int {
        var i = frames.size - 1
        while (i > 0 && starts[i] > t) i--
        return i
    }
}

/** Constructor de guiones: se muta el tablero y se saca una foto con [frame]. */
class DemoScriptBuilder {
    private val tiles = LinkedHashMap<Int, DemoTile>()
    private var nextId = 1
    val frames = ArrayList<DemoFrame>()

    /** Tablero desde 8 renglones: `W` madera, `S` madera con estrella, `.` vacío. */
    fun grid(vararg rows: String) {
        require(rows.size == 8)
        rows.forEachIndexed { y, row ->
            require(row.length == 8) { "renglón $y: «$row»" }
            row.forEachIndexed { x, c ->
                when (c) {
                    'W' -> add(DemoTileKind.WOOD, x.toFloat(), y.toFloat())
                    'S' -> add(DemoTileKind.STAR, x.toFloat(), y.toFloat())
                    '.' -> Unit
                    else -> error("carácter «$c»")
                }
            }
        }
    }

    fun add(kind: DemoTileKind, x: Float, y: Float, size: Float = 1f, scale: Float = 1f): Int {
        val id = nextId++
        tiles[id] = DemoTile(id, x, y, kind, size, scale)
        return id
    }

    fun tile(id: Int): DemoTile = tiles.getValue(id)

    /** Ficha cuadrada que ocupa la casilla (x, y) ahora mismo. */
    fun at(x: Int, y: Int): Int = tiles.values.first {
        !it.kind.round && it.size == 1f && it.x == x.toFloat() && it.y == y.toFloat()
    }.id

    fun set(id: Int, x: Float = tile(id).x, y: Float = tile(id).y, kind: DemoTileKind = tile(id).kind,
            size: Float = tile(id).size, scale: Float = tile(id).scale) {
        tiles[id] = tile(id).copy(x = x, y = y, kind = kind, size = size, scale = scale)
    }

    fun move(id: Int, x: Int, y: Int) = set(id, x = x.toFloat(), y = y.toFloat())
    fun slide(fromX: Int, fromY: Int, toX: Int, toY: Int) = move(at(fromX, fromY), toX, toY)
    fun remove(id: Int) { tiles.remove(id) }

    private fun squares() = tiles.values.filter { !it.kind.round }
    fun clearRow(y: Int) = squares().filter { it.y == y.toFloat() }.forEach { remove(it.id) }
    fun clearCol(x: Int) = squares().filter { it.x == x.toFloat() }.forEach { remove(it.id) }
    fun ids(filter: (DemoTile) -> Boolean): List<Int> = tiles.values.filter(filter).map { it.id }

    /** Cada columna se desploma hasta el fondo conservando el orden. */
    fun gravity() {
        for (x in 0 until 8) {
            val col = squares().filter { it.x == x.toFloat() && it.y >= 0f }.sortedByDescending { it.y }
            col.forEachIndexed { i, t -> set(t.id, y = (7 - i).toFloat()) }
        }
    }

    /** Altura libre más baja de la columna [x] (la casilla donde aterrizaría algo que cae). */
    fun landingRow(x: Int): Int {
        val top = squares().filter { it.x == x.toFloat() && it.y >= 0f }.minOfOrNull { it.y } ?: 8f
        return top.toInt() - 1
    }

    fun atOrNull(x: Int, y: Int): Int? = tiles.values.firstOrNull {
        !it.kind.round && it.size == 1f && it.x == x.toFloat() && it.y == y.toFloat()
    }?.id

    /** Filas con sus 8 casillas ocupadas por fichas cuadradas o Teikas (comodines de línea). */
    fun fullRows(): List<Int> = (0 until 8).filter { y ->
        (0 until 8).all { x -> atOrNull(x, y) != null || roundCovers(x, y) }
    }

    private fun roundCovers(x: Int, y: Int) = tiles.values.any {
        it.kind.round && it.kind != DemoTileKind.FLOWER &&
            kotlin.math.abs(it.x - (x + 0.5f)) < it.size / 2f && kotlin.math.abs(it.y - (y + 0.5f)) < it.size / 2f
    }

    /** Columnas ordenadas de más a menos madera (a igualdad, de izquierda a derecha). */
    fun heaviestColumns(): List<Int> = (0 until 8).sortedWith(
        compareByDescending<Int> { x -> squares().count { it.x == x.toFloat() && it.y >= 0f } }.thenBy { it }
    )

    /**
     * Relleno por anchura desde (x, y) por las casillas vacías, hasta [cap] casillas contando la de
     * salida. Devuelve las oleadas SIN la casilla de salida.
     */
    fun flood(x: Int, y: Int, cap: Int): List<List<Pair<Int, Int>>> {
        val seen = hashSetOf(x to y)
        var layer = listOf(x to y)
        var count = 1
        val out = ArrayList<List<Pair<Int, Int>>>()
        while (layer.isNotEmpty() && count < cap) {
            val next = ArrayList<Pair<Int, Int>>()
            loop@ for ((cx, cy) in layer) for ((dx, dy) in listOf(0 to -1, -1 to 0, 1 to 0, 0 to 1)) {
                val n = (cx + dx) to (cy + dy)
                if (n.first !in 0..7 || n.second !in 0..7 || n in seen || atOrNull(n.first, n.second) != null) continue
                seen += n
                next += n
                count++
                if (count >= cap) break@loop
            }
            if (next.isNotEmpty()) out += next
            layer = next
        }
        return out
    }

    fun frame(ms: Int, vararg fx: DemoFx) {
        frames += DemoFrame(tiles.values.toList(), ms, fx.toList())
    }

    fun hold(ms: Int) = frame(ms)

    /** Destello de las filas llenas y reventón. */
    fun clearFullRows(tone: FxTone = FxTone.WHITE) {
        val rows = fullRows()
        if (rows.isEmpty()) return
        val fx = rows.map { DemoFx.RowFlash(it, tone) }.toTypedArray()
        frame(300, *fx)
        rows.forEach { clearRow(it) }
        frame(320)
    }
}

private fun script(block: DemoScriptBuilder.() -> Unit): DemoTimeline =
    DemoTimeline(DemoScriptBuilder().apply(block).frames)

/**
 * Guion de la demo de [companionId] al nivel [level] (1..5); `null` si no tiene.
 *
 * **ECO** (P-386): en los niveles con eco el poder se juega DOS veces seguidas — el tablero se
 * rehace con un aro dorado y sin fundido a negro — porque eso es el eco: el uso no se gastó y se
 * puede volver a lanzar. Antes era un marco dorado que no decía nada por sí solo.
 */
fun abilityDemoScript(companionId: String, level: Int = 1): DemoTimeline? {
    val lv = level.coerceIn(1, COMPANION_ABILITY_MAX_LEVEL)
    val once = ABILITY_DEMO_SCRIPTS[companionId]?.invoke(lv) ?: return null
    if (AbilityScaling.echoChance(companionId, lv) <= 0f) return once
    return DemoTimeline(withEchoReplay(once.frames))
}

/**
 * **La tira completa del bucle** (P-389): el eco sale con SU probabilidad. En el nivel 4 (25 %) una
 * pasada de cada cuatro repite el poder; en el 5 (50 %), una de cada dos. La pasada con eco va
 * PRIMERO para que quien ojea ese nivel la vea sin esperar: el reparto se nota en las siguientes.
 */
fun abilityDemoReel(companionId: String, level: Int = 1): DemoReel? {
    val lv = level.coerceIn(1, COMPANION_ABILITY_MAX_LEVEL)
    val once = ABILITY_DEMO_SCRIPTS[companionId]?.invoke(lv) ?: return null
    val chance = AbilityScaling.echoChance(companionId, lv)
    if (chance <= 0f) return DemoReel(listOf(once))
    val passes = kotlin.math.round(1f / chance).toInt().coerceAtLeast(1)
    return DemoReel(listOf(DemoTimeline(withEchoReplay(once.frames))) + List(passes - 1) { once })
}

/** Pausa entre pasada y pasada: el tablero se funde a negro y vuelve a su foto inicial. */
const val DEMO_LOOP_GAP_MS = 700

/**
 * **EL RELOJ DE LA TIRA** (P-402): qué pasada suena, en qué instante de su guion, con cuánto fundido
 * y cuándo late el eco — todo a partir del reloj crudo de la UI. Vivía suelto en la UI sin test.
 *
 * Todas las consultas son aritmética sobre arrays precalculados: se llaman desde el DIBUJO, así que
 * no asignan nada.
 */
class DemoReel(val passes: List<DemoTimeline>, val gapMs: Int = DEMO_LOOP_GAP_MS) {
    init { require(passes.isNotEmpty()) }

    val size: Int get() = passes.size

    /** Inicio de cada pasada dentro del ciclo (cada una dura su guion más el fundido). */
    private val passStarts = LongArray(passes.size).also { st ->
        var acc = 0L
        for (i in passes.indices) { st[i] = acc; acc += passes[i].totalMs + gapMs }
    }

    val cycleMs: Long = passStarts.last() + passes.last().totalMs + gapMs

    /** Ventana de los aros del eco en el ciclo: siempre en la pasada 1, que empieza en 0. */
    val echoStartMs: Long
    val echoLenMs: Long

    init {
        val first = passes.first()
        val i = if (passes.size > 1) first.frames.indexOfFirst { DemoFx.Echo in it.fx } else -1
        echoStartMs = if (i < 0) -1L else first.starts[i].toLong()
        echoLenMs = if (i < 0) 0L else first.frames[i].ms.toLong()
    }

    fun passAt(clockMs: Long): Int {
        val t = clockMs % cycleMs
        var pass = passes.lastIndex
        while (pass > 0 && passStarts[pass] > t) pass--
        return pass
    }

    /** Milisegundos transcurridos dentro de la pasada [pass] (incluido su fundido final). */
    fun timeInPass(clockMs: Long, pass: Int): Int = (clockMs % cycleMs - passStarts[pass]).toInt()

    /**
     * Instante del GUION que se enseña: durante el fundido de salida se queda en la foto final, y
     * durante el de entrada, en la inicial.
     */
    fun sceneTime(clockMs: Long, pass: Int): Int {
        val t = timeInPass(clockMs, pass)
        val total = passes[pass].totalMs
        return when {
            t < total -> t
            t < total + gapMs / 2 -> total - 1
            else -> 0
        }
    }

    /** Opacidad del tablero: 1 durante el guion, baja a 0 y vuelve a 1 en el fundido. */
    fun fade(clockMs: Long, pass: Int): Float {
        val t = timeInPass(clockMs, pass)
        val total = passes[pass].totalMs
        val half = gapMs / 2
        return when {
            t < total -> 1f
            t < total + half -> 1f - (t - total) / half.toFloat()
            else -> ((t - total - half) / half.toFloat()).coerceAtMost(1f)
        }
    }

    /** Latido del contador (0..1..0) mientras suenan los aros del eco; 0 fuera de ellos. */
    fun echoBeat(clockMs: Long): Float {
        if (echoStartMs < 0) return 0f
        val t = clockMs % cycleMs - echoStartMs
        if (t < 0 || t >= echoLenMs) return 0f
        return kotlin.math.sin(t / echoLenMs.toFloat() * kotlin.math.PI.toFloat())
    }
}

/** Separación de ids de la segunda pasada: así sus fichas BROTAN en vez de deslizarse desde las de la primera. */
private const val ECHO_ID_OFFSET = 100_000

internal fun withEchoReplay(frames: List<DemoFrame>): List<DemoFrame> {
    val first = frames.toMutableList()
    // La espera final se acorta: el eco llega enseguida, que es lo que lo distingue del bucle.
    first[first.lastIndex] = first.last().copy(ms = minOf(first.last().ms, 450))
    val second = frames.mapIndexed { i, f ->
        val tiles = f.tiles.map { it.copy(id = it.id + ECHO_ID_OFFSET) }
        if (i == 0) DemoFrame(tiles, 650, listOf(DemoFx.Echo)) else f.copy(tiles = tiles)
    }
    return first + second
}

/**
 * Un guion por compañero de [COMPANION_ABILITIES] (`AbilityDemoScriptTest` vigila que no falte
 * ninguno). **Sigue el nivel** (P-383): las cantidades salen de [AbilityScaling], la misma fuente que
 * el motor, así que el nivel 5 enseña exactamente cuánto más hace que el 1. Timoteo es la excepción:
 * sus «pasadas» no se ven como algo contable en un 8×8.
 */
val ABILITY_DEMO_SCRIPTS: Map<String, (Int) -> DemoTimeline> = mapOf(

    // Cristal: saca piezas y las coloca solas donde revientan, encadenando N reventones.
    "cristal" to { level -> script {
        grid(
            "........",
            "........",
            "........",
            "WWW..WWW",
            "..WWWWWW",
            "WWWWW..W",
            "WW..WWWW",
            "WWWWWW..",
        )
        // Hueco de cada fila, de arriba abajo: así la pieza siguiente cae por filas ya reventadas.
        val gaps = listOf(3 to 3, 0 to 4, 5 to 5, 2 to 6, 6 to 7)
        frame(700)
        for (k in 0 until AbilityScaling.cristalChains(level)) {
            val (gx, row) = gaps[k]
            val a = add(DemoTileKind.CRYSTAL, gx.toFloat(), -2f)
            val b = add(DemoTileKind.CRYSTAL, gx + 1f, -2f)
            frame(0)
            move(a, gx, row); move(b, gx + 1, row)
            frame(400)
            frame(240, DemoFx.RowFlash(row, FxTone.CRYSTAL))
            clearRow(row)
            frame(240)
        }
        hold(900)
    } },

    // Maite: el tablero se desploma y caen N filas llenas que, al aterrizar, rematan N líneas.
    "dormilona" to { level -> script {
        grid(
            "........",
            "W.....W.",
            "..W..W..",
            ".W..W...",
            "W.....W.",
            "....W..W",
            "..W.....",
            "........",
        )
        frame(700)
        gravity()
        frame(520)
        hold(200)
        repeat(AbilityScaling.dormilonaRows(level)) {
            val drops = (0 until 8).map { x -> x to add(DemoTileKind.SLEEP, x.toFloat(), -1.5f) }
            frame(0)
            // El aterrizaje no cuenta las propias fichas que caen (están arriba, y < 0).
            for ((x, id) in drops) move(id, x, landingRow(x))
            frame(420)
            hold(80)
        }
        clearFullRows(FxTone.SLEEP)
        hold(900)
    } },

    // Timoteo: la madera se desliza como un rompecabezas y rompe las líneas que va completando.
    "miedoso" to { _ -> script {
        grid(
            "........",
            "........",
            "........",
            "..W.....",
            "W....W..",
            ".WW..W.W",
            "WW.W.WW.",
            "WWW.W.WW",
        )
        frame(700)
        slide(3, 6, 3, 7); slide(5, 6, 5, 7)
        frame(260)
        frame(280, DemoFx.RowFlash(7))
        clearRow(7)
        frame(280)
        slide(2, 5, 2, 6); slide(5, 5, 5, 6); slide(7, 5, 7, 6)
        frame(240)
        slide(5, 4, 5, 5); slide(2, 3, 2, 4)
        frame(240)
        slide(5, 5, 4, 5); slide(2, 4, 2, 5)
        frame(240)
        slide(4, 5, 4, 6); slide(2, 5, 3, 5)
        frame(240)
        slide(3, 5, 3, 6)
        frame(240)
        frame(280, DemoFx.RowFlash(6))
        clearRow(6)
        frame(300)
        hold(900)
    } },

    // Pradera: la flor entierra al Teika, renace como cañón abajo y el disparo rebota N veces.
    "planta" to { level -> script {
        grid(
            "..W..W..",
            ".WW.WWW.",
            "WWW.WWWW",
            "W.WWW.WW",
            ".WW.WWW.",
            "........",
            "........",
            "........",
        )
        val teika = add(DemoTileKind.TEIKA_3, 4f, 6f, size = 1.8f)
        frame(700)
        set(teika, kind = DemoTileKind.FLOWER, size = 1.4f)
        frame(420)
        set(teika, y = 7.45f, size = 0.9f)
        frame(420)
        // Trayectoria con rebotes en las cuatro paredes; el último tramo frena a medio camino.
        val bounces = AbilityScaling.plantaBounces(level)
        var px = 4f; var py = 7.4f
        val norm = kotlin.math.sqrt(1f + 1.3f * 1.3f)
        var dx = 1f / norm; var dy = -1.3f / norm
        val segs = ArrayList<FloatArray>()
        for (s in 0..bounces) {
            val tx = if (dx > 0) (8f - px) / dx else (0f - px) / dx
            val ty = if (dy > 0) (8f - py) / dy else (0f - py) / dy
            var t = minOf(tx, ty)
            if (s == bounces) t *= 0.5f
            val ex = px + dx * t; val ey = py + dy * t
            segs += floatArrayOf(px, py, ex, ey)
            if (tx < ty) dx = -dx else dy = -dy
            px = ex; py = ey
        }
        val first = segs.first()
        frame(650, DemoFx.Aim(first[0], first[1], first[2], first[3]))
        for (seg in segs) {
            frame(200, DemoFx.Shot(seg[0], seg[1], seg[2], seg[3], FxTone.NATURE))
            val steps = 40
            for (k in 0..steps) {
                val f = k / steps.toFloat()
                val cx = (seg[0] + (seg[2] - seg[0]) * f).toInt()
                val cy = (seg[1] + (seg[3] - seg[1]) * f).toInt()
                if (cx in 0..7 && cy in 0..7) atOrNull(cx, cy)?.let { remove(it) }
            }
        }
        frame(260)
        hold(900)
    } },

    // Rayco: completa una línea que cruza Teikas energizados; sus columnas se autocompletan y rompen.
    "electrico" to { level -> script {
        grid(
            "W...W.W.",
            "W...WW..",
            ".W..W.W.",
            "WW.W.WWW",
            "W.WWW.W.",
            ".WW.WW.W",
            "W.WWWW.W",
            "WW.WW.WW",
        )
        // Por tier: primero el T2, luego los T1. El tercero no está en la línea: solo se ve cargarse.
        class Target(val cx: Float, val cy: Float, val col: Int?, val sources: List<IntArray>)
        val targets = listOf(
            Target(5.5f, 4.5f, 5, listOf(intArrayOf(6, 0, 5, 0), intArrayOf(6, 2, 5, 2), intArrayOf(4, 7, 5, 7))),
            Target(1.5f, 4.5f, 1, listOf(intArrayOf(0, 0, 1, 0), intArrayOf(0, 1, 1, 1), intArrayOf(0, 6, 1, 6))),
            Target(3.5f, 1.5f, null, emptyList()),
        )
        add(DemoTileKind.TEIKA_2, 5.5f, 4.5f, size = 0.95f)
        add(DemoTileKind.TEIKA_1, 1.5f, 4.5f, size = 0.95f)
        add(DemoTileKind.TEIKA_1, 3.5f, 1.5f, size = 0.95f)
        val lit = targets.take(AbilityScaling.electricoTargets(level).coerceAtMost(targets.size))
        val rings = lit.map { DemoFx.Ring(it.cx, it.cy, 0.65f, FxTone.ELECTRIC) }.toTypedArray()
        val p = add(DemoTileKind.PLACED, 7f, -2f)
        frame(700, *rings)
        move(p, 7, 4)
        frame(420, *rings)
        frame(300, *rings, DemoFx.RowFlash(4, FxTone.ELECTRIC))
        clearRow(4)
        frame(260, *rings)
        val cols = lit.mapNotNull { it.col }
        if (cols.isNotEmpty()) {
            for (t in lit) for (s in t.sources) slide(s[0], s[1], s[2], s[3])
            val colFx = cols.map { DemoFx.ColFlash(it, FxTone.ELECTRIC) }.toTypedArray()
            frame(420, *rings, *colFx)
            frame(300, *rings, *colFx)
            cols.forEach { clearCol(it) }
            frame(300)
        }
        hold(900)
    } },

    // Iñaki: cada ficha en llamas arrasa sus filas y columnas en las cuatro direcciones.
    "fuego" to { level -> script {
        grid(
            "..W.....",
            ".W...W..",
            "WW.W.WW.",
            "W.WW.W.W",
            "WWW..WWW",
            ".WWW.WWW",
            "W..WWW.W",
            "WWWWW.WW",
        )
        frame(700)
        // Nivel 1: una ficha; 2: dos; 3+: toda la bandeja (tres).
        val pieces = listOf(listOf(3 to 4, 4 to 4), listOf(1 to 6), listOf(6 to 1))
        for (cells in pieces.take(AbilityScaling.fuegoPieces(level).coerceAtMost(pieces.size))) {
            val fire = cells.map { (x, _) -> add(DemoTileKind.FIRE, x.toFloat(), -2f) }
            frame(0)
            fire.zip(cells).forEach { (id, c) -> move(id, c.first, c.second) }
            frame(380)
            val rows = cells.map { it.second }.toSet()
            val cols = cells.map { it.first }.toSet()
            val flash = (rows.map { DemoFx.RowFlash(it, FxTone.FIRE) } + cols.map { DemoFx.ColFlash(it, FxTone.FIRE) }).toTypedArray()
            frame(240, *flash)
            // El fuego se abre desde la ficha hacia fuera, anillo a anillo.
            for (d in intArrayOf(2, 4, 6, 8)) {
                ids { t ->
                    !t.kind.round && t.kind != DemoTileKind.FIRE && (
                        (t.y.toInt() in rows && cols.minOf { kotlin.math.abs(t.x - it) } <= d) ||
                        (t.x.toInt() in cols && rows.minOf { kotlin.math.abs(t.y - it) } <= d))
                }.forEach { remove(it) }
                frame(100, *flash)
            }
            fire.forEach { remove(it) }
            frame(220)
        }
        hold(900)
    } },

    // Kenji: N tajos de katana parten por la mitad los bloques con estrella → doble estrella.
    "ninja" to { level -> script {
        grid(
            "..W..S..",
            ".S.WS.W.",
            "WW.S.WS.",
            "S.WW.W.W",
            "WW.S.WW.",
            "W.WWW.SW",
            "WSW.WWW.",
            "WWWWS.WW",
        )
        frame(700)
        val slashes = listOf(
            DemoFx.Slash(-0.5f, 1.5f, 8.5f, 5.5f),
            DemoFx.Slash(8.5f, 1.8f, -0.5f, 7.5f),
            DemoFx.Slash(-0.5f, 4.2f, 8.5f, 3.4f),
            DemoFx.Slash(1f, -0.5f, 5f, 8.5f),
            DemoFx.Slash(7.5f, -0.5f, 2.5f, 8.5f),
            DemoFx.Slash(-0.5f, 7f, 8.5f, 0.5f),
            DemoFx.Slash(-0.5f, 0.8f, 8.5f, 2.4f),
        )
        slashes.take(AbilityScaling.ninjaSlashes(level)).forEach { frame(170, it) }
        val stars = ids { it.kind == DemoTileKind.STAR }.map { tile(it) }
            .take(AbilityScaling.ninjaVictims(level))
        val halves = stars.map { s ->
            remove(s.id)
            Triple(s, add(DemoTileKind.STAR, s.x, s.y, size = 0.5f), add(DemoTileKind.STAR, s.x + 0.5f, s.y, size = 0.5f))
        }
        frame(0)
        for ((s, l, r) in halves) {
            set(l, x = s.x - 0.35f, y = s.y + 0.3f)
            set(r, x = s.x + 0.85f, y = s.y + 0.3f)
        }
        val sparkles = stars.map { DemoFx.Sparkle(it.x, it.y) }.toTypedArray()
        frame(380, *sparkles)
        for ((_, l, r) in halves) { remove(l); remove(r) }
        frame(280, *sparkles)
        hold(900)
    } },

    // Nieves: N carámbanos caen sobre las columnas más llenas y las perforan de arriba abajo.
    "hielo" to { level -> script {
        grid(
            "........",
            "....W...",
            ".W..W...",
            ".W.WW.W.",
            "WW.WW.WW",
            ".WWWW.W.",
            "WWW.WWWW",
            "WWWWW.WW",
        )
        val cols = heaviestColumns().take(AbilityScaling.hieloIcicles(level))
        frame(700)
        frame(300, *cols.map { DemoFx.Icicle(it, -1.5f, 0f) }.toTypedArray())
        for (row in 0 until 8) {
            for (c in cols) atOrNull(c, row)?.let { remove(it) }
            frame(85, *cols.map { DemoFx.Icicle(it, row.toFloat(), row + 1f) }.toTypedArray())
        }
        frame(260, *cols.map { DemoFx.Icicle(it, 8f, 9.5f) }.toTypedArray())
        hold(900)
    } },

    // Morti: N bloques sueltos se vuelven espectrales, atraviesan el tablero y cierran líneas.
    "fantasma" to { level -> script {
        grid(
            ".W..W.W.",
            "W..W...W",
            "..W..WW.",
            ".W..W..W",
            "W.W.WW.W",
            "WW.WW.W.",
            ".WW.W.WW",
            "W.WW.W.W",
        )
        // Sueltos de abajo arriba → huecos de abajo arriba: las filas se van cerrando en orden.
        val loose = listOf(1 to 3, 4 to 3, 7 to 3, 2 to 2, 5 to 2, 6 to 2, 0 to 1, 3 to 1, 7 to 1, 1 to 0, 4 to 0, 6 to 0)
        val gaps = listOf(1 to 7, 4 to 7, 6 to 7, 0 to 6, 3 to 6, 5 to 6, 2 to 5, 5 to 5, 7 to 5, 1 to 4, 3 to 4, 6 to 4)
        val n = AbilityScaling.fantasmaMoves(level).coerceAtMost(loose.size)
        val ghosts = loose.take(n).map { (x, y) -> at(x, y) }
        frame(700)
        ghosts.forEach { set(it, kind = DemoTileKind.GHOST) }
        frame(380)
        ghosts.zip(gaps).forEach { (id, g) -> move(id, g.first, g.second) }
        frame(650)
        ghosts.forEach { set(it, kind = DemoTileKind.WOOD) }
        frame(240)
        clearFullRows(FxTone.GHOST)
        hold(900)
    } },

    // Chueta: asoma por debajo y los bloques con estrella (N, o todos) caen a su boca.
    "gloton" to { level -> script {
        grid(
            "..S..W.S",
            ".WW.S.W.",
            "S.WW..WS",
            "WS.W.WSW",
            "WW.S.WW.",
            "W.S....W",
            "W......W",
            "........",
        )
        frame(600)
        frame(360, DemoFx.Mouth(4f, 0f, 1f))
        // Los más cercanos a la boca primero.
        val stars = ids { it.kind == DemoTileKind.STAR }.map { tile(it) }
            .sortedWith(compareByDescending<DemoTile> { it.y }.thenBy { it.x })
            .take(AbilityScaling.glotonBites(level)).map { it.id }
        for (s in stars) set(s, x = 3.5f, y = 7.3f, scale = 0.45f)
        frame(560, DemoFx.Mouth(4f, 1f, 1f))
        stars.forEach { remove(it) }
        frame(260, DemoFx.Mouth(4f, 1f, 0.8f), DemoFx.Sparkle(3.5f, 6.8f))
        frame(360, DemoFx.Mouth(4f, 0.8f, 0f), DemoFx.Sparkle(3.5f, 6.8f))
        hold(800)
    } },

    // Toño: el miasma corroe las columnas más pesadas (o todo el tablero) hasta que revientan.
    "toxico" to { level -> script {
        grid(
            "........",
            "..W...W.",
            ".WW.W.W.",
            "W.WWW.WW",
            "WW.WWW.W",
            "W.WW.WWW",
            "WWW.WW.W",
            "WW.WWWWW",
        )
        val cols = heaviestColumns().take(
            when (AbilityScaling.cappedAt3(level)) { 1 -> 4; 2 -> 6; else -> 8 }
        ).map { it.toFloat() }.toSet()
        frame(700)
        frame(420, DemoFx.Fog(0f, 0.35f))
        val hit = ids { it.kind == DemoTileKind.WOOD && it.x in cols }
        hit.forEach { set(it, kind = DemoTileKind.TOXIC) }
        frame(700, DemoFx.Fog(0.35f, 0.35f))
        frame(260, DemoFx.Fog(0.35f, 0.45f))
        hit.forEach { remove(it) }
        frame(360, DemoFx.Fog(0.45f, 0f))
        hold(900)
    } },

    // Estela: el bloque tocado por las estrellas se derrama y llena hasta N casillas del hueco.
    "estrella" to { level -> script {
        grid(
            "........",
            "....W...",
            "WWWW.WWW",
            "W......W",
            "W......W",
            "W......W",
            "W......W",
            "W.....WW",
        )
        val g = add(DemoTileKind.GOLD, 4f, -2f)
        frame(700)
        move(g, 4, 2)
        frame(420)
        for (wave in flood(4, 2, AbilityScaling.estrellaCap(level))) {
            for ((x, y) in wave) add(DemoTileKind.GOLD, x.toFloat(), y.toFloat())
            frame(110)
        }
        hold(200)
        clearFullRows(FxTone.GOLD)
        hold(900)
    } },

    // Rey: de cada Teika de tier 2 o más (el mayor / la mitad / todos) sale un T1 empujado fuera.
    "rey" to { level -> script {
        grid(
            "........",
            "........",
            "........",
            "........",
            "........",
            "........",
            "W.....WW",
            "WW.WWWWW",
        )
        // Padres de mayor a menor tier, cada uno con el sitio adonde empuja a su cría.
        class Parent(val kind: DemoTileKind, val x: Float, val y: Float, val size: Float, val tx: Float, val ty: Float)
        val parents = listOf(
            Parent(DemoTileKind.TEIKA_3, 2.3f, 3.6f, 2.0f, 3.9f, 5.4f),
            Parent(DemoTileKind.TEIKA_2, 5.8f, 2.6f, 1.5f, 6.9f, 4.3f),
            Parent(DemoTileKind.TEIKA_2, 1.3f, 1.1f, 1.5f, 3.0f, 0.6f),
        )
        val bodies = parents.map { add(it.kind, it.x, it.y, size = it.size) }
        frame(700)
        val n = when (AbilityScaling.cappedAt3(level)) { 1 -> 1; 2 -> 2; else -> 3 }
        val mothers = parents.indices.take(n)
        for (i in mothers) set(bodies[i], size = parents[i].size * 1.12f)
        frame(260)
        val kids = mothers.map { i -> add(DemoTileKind.TEIKA_1, parents[i].x, parents[i].y, size = 0.3f) }
        frame(0)
        for ((k, i) in mothers.withIndex()) {
            set(bodies[i], size = parents[i].size)
            set(kids[k], x = parents[i].tx, y = parents[i].ty, size = 0.95f)
        }
        frame(520)
        hold(1100)
    } },

    // Galo: un eclipse cubre el rincón de madera más denso (3×3, 4×4, 5×5) y lo pulveriza.
    "galactico" to { level -> script {
        grid(
            "........",
            "..W.....",
            ".W..W...",
            "W..WWWWW",
            ".W.WWWWW",
            "..WWWWWW",
            "W..WWWWW",
            "WW.WWWWW",
        )
        val strength = AbilityScaling.cappedAt3(level)
        val n = when (strength) { 1 -> 3; 2 -> 4; else -> 5 }
        val c = 8f - n / 2f
        val r = n * 0.72f
        frame(700)
        frame(620, DemoFx.Eclipse(1.5f, 1.5f, c, c, r * 0.6f, r * 0.6f, 0f, 1f))
        frame(360, DemoFx.Eclipse(c, c, c, c, r * 0.6f, r))
        ids { !it.kind.round && it.x >= 8f - n && it.y >= 8f - n }.forEach { remove(it) }
        // En el nivel 3 el cielo, además, devuelve un Teika.
        if (strength >= 3) add(DemoTileKind.TEIKA_1, c, c, size = 0.95f)
        frame(420, DemoFx.Eclipse(c, c, c, c, r, r * 1.12f, 1f, 0f))
        hold(900)
    } },
)
