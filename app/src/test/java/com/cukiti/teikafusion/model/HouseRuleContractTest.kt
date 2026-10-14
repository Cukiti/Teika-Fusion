package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **EL SISTEMA INMUNITARIO DEL CATÁLOGO DE REGLAS DE LA CASA** (2026-09-03).
 *
 * [HouseRule] es la tabla única que reparte una mecánica de nivel a cada uno de los 293 niveles.
 * Sustituye a cinco módulos independientes que se repartían con su propio módulo (7, 11, 13, 5, uno
 * por capítulo) y se esquivaban entre sí con una cadena de negaciones — la cadena que ya falló una
 * vez (P-128: seis niveles con dos mecánicas encima porque a un eslabón le faltaba un `&&`).
 *
 * Este test defiende las reglas que el compilador no puede:
 *
 *  1. **Exactamente una regla por nivel jugable.** Es la ley de la casa («dos formas distintas de
 *     romper el tablero a la vez no son el doble de interesantes, son ilegibles»), y con la tabla
 *     única deja de ser una promesa para ser aritmética. Aquí se comprueba que sigue siéndolo.
 *  2. **Ni la intro ni el tutorial.** Ahí se aprende a jugar.
 *  3. **Ninguna regla fósil.** Toda entrada del catálogo le toca a algún nivel de verdad y tiene una
 *     cápsula que la enseña. Una regla declarada y no repartida sería `usesNewEngine` otra vez.
 *  4. **Sin repeticiones dentro del capítulo** en cuanto el catálogo supere al capítulo más largo.
 *     Mientras no lo supere, al menos **dos niveles seguidos nunca comparten regla**.
 *  5. **El dial de capítulo sube.** Es la mitad del encargo del usuario que no se ve en el reparto:
 *     «cada vez que el jugador pase al siguiente bioma, cada una de las mecánicas se volverá más
 *     difícil».
 */
class HouseRuleContractTest {

    private val jugables: List<Int> =
        (1..LevelMap.totalLevels).filter {
            it > LevelMap.introLevels && !TutorialCampaign.isTutorialLevel(it)
        }

    // ── 1 y 2 · el reparto ─────────────────────────────────────────────────────────────────────

    /**
     * **TODOS los niveles llevan una regla** (decisión del usuario, 2026-09-03: «que cada vez que el
     * jugador juegue un nivel sienta diferencia»).
     *
     * El censo del día que motivó el encargo: 154 de 286 niveles —el 54 %— se jugaban a tablero
     * pelado, porque las cinco reglas que existían se repartían por módulos primos y dejaban huecos
     * enormes entre ellas.
     */
    @Test
    fun `todos los niveles jugables llevan exactamente una regla`() {
        jugables.forEach { level ->
            assertNotNull("nivel $level sin regla de la casa", HouseRule.forLevel(level))
        }
        assertEquals(
            "la campaña ha cambiado de tamaño y el censo no lo sabe",
            LevelMap.totalLevels - LevelMap.introLevels,
            jugables.size
        )
    }

    /** Y la intro y el tutorial, ninguna: allí se enseñan las reglas básicas. */
    @Test
    fun `la intro y el tutorial no llevan regla`() {
        for (level in 1..LevelMap.introLevels) assertNull(HouseRule.forLevel(level))
        for (level in 1..LevelMap.totalLevels) {
            if (TutorialCampaign.isTutorialLevel(level)) assertNull(HouseRule.forLevel(level))
        }
    }

    /**
     * **LA EXCLUSIÓN MUTUA, MEDIDA SOBRE EL JUEGO ENTERO.**
     *
     * Es lo que sustituye a `VarietyMechanicMatrixTest`: en vez de comprobar par a par que ninguna
     * mecánica coincide con otra —lo que obliga a acordarse de añadir el par nuevo cada vez, que es
     * exactamente cómo se coló P-128—, se comprueba que **cada nivel activa una sola** de las cinco
     * fundacionales. Con la tabla única no puede ser de otra manera, y este test es quien lo afirma.
     */
    @Test
    fun `ningun nivel activa dos mecanicas de variedad`() {
        jugables.forEach { level ->
            val activas = listOf(
                "gravedad" to BoardGravity.appliesTo(level),
                "corriente" to BoardCurrent.appliesTo(level),
                "horizonte" to HorizonTray.appliesTo(level),
                "testarudos" to StubbornBlocks.appliesTo(level),
                "gemelas" to TwinTrayRules.appliesTo(level),
                "reserva" to ReserveRules.appliesTo(level),
                "cola forzada" to ForcedQueue.appliesTo(level),
                "reposición instantánea" to InstantRefill.appliesTo(level),
                "portal doble" to PortalSwapRules.appliesTo(level),
                "reactor" to ReactorRules.appliesTo(level),
                "bola demoledora" to WreckingBallRules.appliesTo(level),
                "fundir" to PieceFusion.appliesTo(level),
                "molde" to MoldRule.appliesTo(level),
                "espejo" to MirrorPiece.appliesTo(level),
            ).filter { it.second }.map { it.first }
            // **Como mucho una**, no exactamente una: lo que este test defiende es que dos mecánicas
            // no coincidan nunca. Que cada nivel lleve la SUYA lo afirma
            // `todos los niveles jugables llevan exactamente una regla`, contra el catálogo entero —
            // aquí se pregunta por los `appliesTo` de cada mecánica, que es el camino por el que el
            // motor la activa de verdad, y por eso este es el test que habría cazado P-128.
            assertTrue("nivel $level lleva $activas", activas.size <= 1)
        }
    }

    // ── 3 · nada declarado que no exista ───────────────────────────────────────────────────────

    /**
     * **NINGUNA REGLA FÓSIL.** Una entrada del catálogo es una mecánica que EXISTE y que le toca a
     * algún nivel. Declarar el catálogo entero y cablear la mitad dejaría niveles con una regla que
     * no hace nada — peor que el tablero pelado que este trabajo vino a quitar.
     */
    @Test
    fun `toda regla le toca a algun nivel`() {
        HouseRule.entries.forEach { rule ->
            val niveles = HouseRule.levelsWith(rule)
            assertTrue("${rule.name} no le toca a ningún nivel", niveles.isNotEmpty())
        }
    }

    /** Y toda regla tiene quien la explique la primera vez que aparece. */
    @Test
    fun `toda regla tiene capsula, y ninguna capsula sirve a dos reglas`() {
        val capsulas = HouseRule.entries.map { it.capsule }
        assertEquals("dos reglas comparten cápsula", capsulas.size, capsulas.distinct().size)
    }

    // ── 4 · que no se repita ───────────────────────────────────────────────────────────────────

    /**
     * **DOS NIVELES SEGUIDOS NUNCA COMPARTEN REGLA.** Es la garantía mínima, y la que de verdad se
     * nota al jugar: el encargo del usuario es que **al pasar de nivel se note el cambio**.
     */
    @Test
    fun `dos niveles seguidos nunca llevan la misma regla`() {
        jugables.zipWithNext().forEach { (a, b) ->
            if (b != a + 1) return@forEach            // salto de capítulo o de tutorial
            assertTrue(
                "niveles $a y $b llevan los dos ${HouseRule.forLevel(a)}",
                HouseRule.forLevel(a) != HouseRule.forLevel(b)
            )
        }
    }

    /**
     * **Y NINGÚN CAPÍTULO REPITE**, en cuanto el catálogo sea mayor que el capítulo más largo.
     *
     * Se comprueba condicionalmente a propósito: el catálogo se está construyendo por familias, y
     * mientras tenga menos entradas que niveles un capítulo **tiene** que repetir alguna — obligarlo
     * sería pedirle a la aritmética algo imposible. Lo que el test hace es **encenderse solo** en
     * cuanto el catálogo llegue al tamaño necesario, para que la garantía completa no dependa de que
     * alguien se acuerde de escribirla ese día.
     */
    @Test
    fun `ningun capitulo repite regla cuando el catalogo da de si`() {
        if (HouseRule.entries.size < HouseRule.longestChapter) return
        LevelMap.chapters.indices.forEach { chapter ->
            val start = LevelMap.chapterStart[chapter]
            val reglas = (start until start + LevelMap.chapters[chapter].levelCount)
                .filter { it in jugables }
                .map { HouseRule.forLevel(it) }
            assertEquals(
                "el capítulo $chapter repite reglas: $reglas",
                reglas.size, reglas.distinct().size
            )
        }
    }

    /** Dos capítulos vecinos no abren con la misma regla: el cambio de bioma tiene que notarse. */
    @Test
    fun `dos capitulos vecinos no abren igual`() {
        LevelMap.chapterStart.zipWithNext().forEach { (a, b) ->
            assertTrue(
                "los capítulos que empiezan en $a y $b abren con la misma regla",
                HouseRule.forLevel(a) != HouseRule.forLevel(b)
            )
        }
    }

    // ── 5 · el dial de capítulo ────────────────────────────────────────────────────────────────

    /**
     * **LA INTENSIDAD SUBE DE BIOMA EN BIOMA Y NO DENTRO DEL BIOMA** (pedido del usuario,
     * 2026-09-03).
     *
     * Las dos mitades importan. Que suba es el encargo. Que **no** se mueva dentro del capítulo es
     * lo que la hace aprendible, y es la misma razón por la que los turnos de aviso de los biomas
     * son fijos desde el 2026-08-30: una mecánica que cambia de fuerza cada cuatro niveles no es una
     * rampa, es un vaivén (esa fue exactamente la muerte de `idleDelta`, P-118 y P-119).
     */
    @Test
    fun `la intensidad crece por capitulo y es constante dentro de el`() {
        assertEquals(0f, HouseRule.intensityFor(LevelMap.chapterStart.first()), 0.0001f)
        assertEquals(1f, HouseRule.intensityFor(LevelMap.chapterStart.last()), 0.0001f)
        var previa = -1f
        LevelMap.chapters.indices.forEach { chapter ->
            val start = LevelMap.chapterStart[chapter]
            val valores = (start until start + LevelMap.chapters[chapter].levelCount)
                .map { HouseRule.intensityFor(it) }
                .distinct()
            assertEquals("el capítulo $chapter cambia de intensidad por dentro", 1, valores.size)
            assertTrue("la intensidad baja en el capítulo $chapter", valores[0] > previa)
            previa = valores[0]
        }
    }

    /** [HouseRule.dial] interpola en las dos direcciones: hay reglas que suben y reglas que bajan. */
    @Test
    fun `el dial interpola en ambos sentidos`() {
        val primero = LevelMap.chapterStart.first()
        val ultimo = LevelMap.chapterStart.last()
        assertEquals(3, HouseRule.dial(primero, 3, 5))
        assertEquals(5, HouseRule.dial(ultimo, 3, 5))
        assertEquals(4, HouseRule.dial(primero, 4, 2))
        assertEquals(2, HouseRule.dial(ultimo, 4, 2))
        // Y en la intro no hay capítulo: la versión más amable de todo.
        assertEquals(3, HouseRule.dial(1, 3, 5))
    }

    /**
     * Los dos pasos del reparto tienen que ser **coprimos con el tamaño del catálogo**, o el
     * recorrido se cierra antes de tiempo y el capítulo repite reglas mucho antes de lo debido. Es
     * la propiedad de la que cuelgan las garantías de arriba, así que se afirma directamente en vez
     * de esperar a que se rompa por su síntoma.
     */
    @Test
    fun `los pasos del reparto son coprimos con el catalogo`() {
        fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
        val n = HouseRule.entries.size
        assertEquals("CHAPTER_STEP no es coprimo con $n", 1, gcd(HouseRule.CHAPTER_STEP, n))
        assertEquals("LEVEL_STEP no es coprimo con $n", 1, gcd(HouseRule.LEVEL_STEP, n))
        // Y la costura entre capítulos: al pasar de bioma suben A LA VEZ el número de nivel y el
        // índice de capítulo, así que el salto es la suma de los dos pasos. Si esa suma fuera
        // múltiplo del catálogo, el último nivel de un bioma y el primero del siguiente llevarían
        // siempre la misma regla — el sitio donde peor sienta, porque el jugador acaba de cambiar de
        // mundo. (Pasaba con CHAPTER_STEP = 11 y un catálogo de 10.)
        assertTrue(
            "la costura entre capítulos repite regla: ${HouseRule.LEVEL_STEP} + " +
                "${HouseRule.CHAPTER_STEP} es múltiplo de $n",
            (HouseRule.LEVEL_STEP + HouseRule.CHAPTER_STEP) % n != 0
        )
    }
}
