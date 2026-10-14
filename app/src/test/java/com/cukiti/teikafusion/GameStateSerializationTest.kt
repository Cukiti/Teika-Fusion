package com.cukiti.teikafusion

import com.cukiti.teikafusion.model.CellContent
import com.cukiti.teikafusion.model.GameState
import com.cukiti.teikafusion.model.PlantAbduction
import com.cukiti.teikafusion.model.PlantEdge
import com.cukiti.teikafusion.model.Position
import com.cukiti.teikafusion.model.TeikaType
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the production save flow. The Json instance MUST match
 * GameViewModel.GameStateJson — if these drift the production save silently
 * stops persisting because the default Json rejects Map<Position, CellContent>.
 */
class GameStateSerializationTest {

    private val gameStateJson = Json {
        allowStructuredMapKeys = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun `el estado serializa y vuelve con su tablero`() {
        val original = GameState.initial(level = 100)
        val json = gameStateJson.encodeToString(original)
        assertNotNull(json)
        val decoded = gameStateJson.decodeFromString<GameState>(json)
        assertTrue(decoded.grid.isNotEmpty())
        assertEquals(original.grid.size, decoded.grid.size)
    }

    @Test
    fun `roundtrip preserves el rapto de la raiz (el ULTIMO cabo de P-111)`() {
        // ── INVERTIDO OTRA VEZ, y ya para siempre (2026-09-04) ──────────────────────────────────
        // Este test ha dicho tres cosas distintas y las tres eran ciertas cuando se escribieron:
        //   1) «efímero, como todas las mecánicas de bioma» — antes de que CONTINUAR existiera;
        //   2) «la excepción que queda, por el flag kinemático» — P-111 persistió el resto del
        //      castigo pero no esto, porque el cuerpo raptado vive como `kinematic` y ese flag lo
        //      comparten la retención de la fusión T3+T3 y la espera del tutorial;
        //   3) hoy: el rapto SE GUARDA.
        //
        // Lo que desatascó el punto 2 no fue separar el flag, que era lo que parecía tocar, sino
        // darse cuenta de que no hacía falta: **el rapto ya sabe a quién agarra** (`teikaId`), así
        // que `continueGame` repone el agarre sobre ese cuerpo y solo sobre ese. La bandera sigue
        // sin guardarse, que es lo correcto para las otras dos.
        val abduction = PlantAbduction(
            teikaId = "captured",
            teikaType = TeikaType.TEIKA_3,
            edge = PlantEdge.LEFT,
            currentRoot = Position(2, 4),
            linesBroken = 1,
            turnsElapsed = 3
        )
        val original = GameState.initial(level = 1).copy(
            plantIdleTurns = 5,
            plantAbduction = abduction,
            busyTeikaIds = setOf(abduction.teikaId)
        )

        val decoded = gameStateJson.decodeFromString<GameState>(
            gameStateJson.encodeToString(original)
        )

        // El rapto entero, con su reloj: sin `turnsElapsed` el jugador recuperaría los turnos que ya
        // había gastado la raíz, que es exactamente el «botón de anular castigo» que cerró P-111.
        assertEquals(abduction, decoded.plantAbduction)
        assertEquals(5, decoded.plantIdleTurns)
        // P-112 (2026-09-04): `busyTeikaIds` NO sobrevive. Y aquí importa el doble — es la marca de
        // «hay una animación de arrastre corriendo sobre este cuerpo», y al cargar no corre ninguna:
        // el Teika raptado tiene que verse en su sitio, no oculto esperando a un VFX que no existe.
        assertEquals(emptySet<String>(), decoded.busyTeikaIds)
    }

    /**
     * **LOS TRES RELOJES QUE VIVEN FUERA DEL ESTADO** (P-116, 2026-09-03).
     *
     * Galáctico, Miedoso y Tormenta llevan su reloj en variables del ViewModel, no en `GameState`,
     * porque se mutan a mitad de `applyBiomeTick` (que publica desde un snapshot capturado al
     * principio, así que escribir en `_gameState` desde ahí se perdería). Viajan al guardado por el
     * mismo camino que los cuerpos físicos: estampados en `withTeikaBodySaves`, leídos en
     * `continueGame`. Aquí solo se comprueba lo único que la serialización puede romper —que los
     * tres campos espejo sobrevivan al round-trip— porque si se perdieran, P-111 volvería a dejar
     * a esos tres biomas regalando turnos de gracia al salir y volver.
     */
    @Test
    fun `roundtrip preserves los relojes espejo de galactico miedoso y tormenta`() {
        val original = GameState.initial(level = 280).copy(
            galacticIdleSaved = 3,
            fearFleeSaved = 2,
            coilFailCountingSaved = true
        )

        val decoded = gameStateJson.decodeFromString<GameState>(
            gameStateJson.encodeToString(original)
        )

        assertEquals(3, decoded.galacticIdleSaved)
        assertEquals(2, decoded.fearFleeSaved)
        assertEquals(true, decoded.coilFailCountingSaved)
    }

    /**
     * **EL CASTIGO DE BIOMA SOBREVIVE AL GUARDADO** (P-111, 2026-09-03).
     *
     * El contrato que este test fija no es una preferencia de esquema: es la diferencia entre que
     * CONTINUAR sea «retomo donde lo dejé» o «me quito el castigo gratis». Antes de P-111 los 45
     * campos de bioma eran `@Transient` y salir a Inicio borraba el asedio del Rey, los pozos del
     * Glotón, la escarcha, la niebla, el agujero negro y el astro que ya te había marcado — y de
     * paso ponía a cero todos los relojes ociosos, regalando turnos de gracia nuevos.
     *
     * Se comprueban a la vez un castigo de CADA forma que existe: profundidad (muros), conjunto de
     * celdas (escarcha), mapa con clave `Position` (pozos), booleano de aviso (niebla), lista de
     * objetos (agujeros negros) y contador ocioso. Si alguien vuelve a marcar uno `@Transient`,
     * este test cae antes de que llegue a una build.
     */
    /**
     * P-329 · LAS PIEZAS DEL REY: la pieza alzada, a quién le toca el próximo golpe y la calle
     * bloqueada sobreviven a salir y volver — si no, CONTINUAR abría la calle o retiraba la pieza gratis.
     */
    @Test
    fun `roundtrip preserves la pieza del Rey y su calle`() {
        val original = GameState.initial(level = 240).copy(
            kingPieceKind = com.cukiti.teikafusion.model.KingPiece.BISHOP,
            kingPieceA = com.cukiti.teikafusion.model.KingPiece.DIAG_UP,
            kingPieceB = 7,
            kingPieceBlack = true,
            kingPieceFromEnd = true,
            kingPieceNext = true,
            kingPiecesInRow = 1,
            kingLaneCells = setOf(Position(0, 3), Position(1, 3)),
            kingRestCell = Position(7, 3),
            kingRestKind = com.cukiti.teikafusion.model.KingPiece.ROOK,
            kingRestBlack = true,
        )
        val decoded = gameStateJson.decodeFromString<GameState>(gameStateJson.encodeToString(original))
        assertEquals(com.cukiti.teikafusion.model.KingPiece.BISHOP, decoded.kingPieceKind)
        assertEquals(com.cukiti.teikafusion.model.KingPiece.DIAG_UP, decoded.kingPieceA)
        assertEquals(7, decoded.kingPieceB)
        assertEquals(true, decoded.kingPieceBlack)
        assertEquals(true, decoded.kingPieceFromEnd)
        assertEquals(true, decoded.kingPieceNext)
        assertEquals(1, decoded.kingPiecesInRow)
        assertEquals(setOf(Position(0, 3), Position(1, 3)), decoded.kingLaneCells)
        // Y la calle sigue sin admitir fichas.
        assertEquals(true, Position(1, 3) in decoded.unplaceableCells)
        // La pieza que se quedó sigue en su casilla (2026-09-28).
        assertEquals(Position(7, 3), decoded.kingRestCell)
        assertEquals(com.cukiti.teikafusion.model.KingPiece.ROOK, decoded.kingRestKind)
        assertEquals(true, decoded.kingRestBlack)
        assertEquals(true, Position(7, 3) in decoded.unplaceableCells)
    }

    @Test
    fun `roundtrip preserves el castigo de bioma`() {
        val original = GameState.initial(level = 240).copy(
            kingSwordAxis = com.cukiti.teikafusion.model.KingSword.COL,
            kingSwordLine = 3,
            glacierFrostCells = setOf(Position(1, 1), Position(2, 2)),
            gluttonCorner = 2,
            gluttonDeadCells = mapOf(Position(0, 7) to 3),
            mirrorSource = com.cukiti.teikafusion.model.HauntedMirror.TOP,
            meteorAt = Position(5, 2),
            grid = mapOf(Position(5, 2) to com.cukiti.teikafusion.model.CellContent.Wood(armor = 1, rock = true)),
            drowsyTurns = 2,
            toxicDecayCounter = 4,
            iceFreezePlacements = 3,
            frozenCells = setOf(Position(6, 6)),
            starAstroEmerged = true,
            starAstroTargetCells = setOf(Position(5, 1)),
            kingSwordIdle = 2,
            ghostIdleTurns = 1,
            gluttonCounter = 3,
            plantIdleTurns = 4
        )

        val decoded = gameStateJson.decodeFromString<GameState>(
            gameStateJson.encodeToString(original)
        )

        // La espada del Rey (P-294) sigue alzada sobre su columna.
        assertEquals(com.cukiti.teikafusion.model.KingSword.COL, decoded.kingSwordAxis)
        assertEquals(3, decoded.kingSwordLine)
        // Celdas del tablero que estorban.
        assertEquals(setOf(Position(1, 1), Position(2, 2)), decoded.glacierFrostCells)
        assertEquals(mapOf(Position(0, 7) to 3), decoded.gluttonDeadCells)
        assertEquals(2, decoded.gluttonCorner)
        assertEquals(setOf(Position(6, 6)), decoded.frozenCells)
        // Avisos y castigos en curso.
        assertEquals(com.cukiti.teikafusion.model.HauntedMirror.TOP, decoded.mirrorSource)
        assertEquals(2, decoded.drowsyTurns)
        assertEquals(3, decoded.iceFreezePlacements)
        assertEquals(4, decoded.toxicDecayCounter)
        assertEquals(true, decoded.starAstroEmerged)
        assertEquals(setOf(Position(5, 1)), decoded.starAstroTargetCells)
        // El meteorito en camino (P-294): salir y volver no lo desvía; y la roca sigue siendo roca.
        assertEquals(Position(5, 2), decoded.meteorAt)
        val roca = decoded.grid[Position(5, 2)] as com.cukiti.teikafusion.model.CellContent.Wood
        assertEquals(true, roca.rock)
        assertEquals(1, roca.armor)
        // Y los relojes ociosos: sin esto, salir y volver regala turnos de gracia.
        assertEquals(2, decoded.kingSwordIdle)
        assertEquals(1, decoded.ghostIdleTurns)
        assertEquals(3, decoded.gluttonCounter)
        assertEquals(4, decoded.plantIdleTurns)
    }

    @Test
    fun `roundtrip preserves volcano pressure`() {
        val original = GameState.initial(level = 34).copy(
            lavaRow = 3,
            volcanoDryTurns = 1
        )

        val decoded = gameStateJson.decodeFromString<GameState>(
            gameStateJson.encodeToString(original)
        )

        assertEquals(3, decoded.lavaRow)
        assertEquals(1, decoded.volcanoDryTurns)
    }

    /** Sanity check: the default Json still rejects Position keys — confirms the bug exists. */
    @Test(expected = kotlinx.serialization.SerializationException::class)
    fun `default Json still cannot serialize Position-keyed map`() {
        val state = GameState.initial(level = 100)
        Json.encodeToString(state)
    }

    /**
     * TODAS las banderas vivas de Wood sobreviven el round-trip. Si una nueva bandera de bioma se
     * añade a Wood y no aparece aquí, este test es el recordatorio de cubrirla — perder una bandera
     * en el save significa que la mecánica "se cura" gratis al recargar.
     *
     * `concrete` estuvo en esta lista hasta P-132 (2026-09-03): se guardaba y se restauraba
     * perfectamente… un valor que **ningún productor ponía nunca a true** desde que el modo Infinito
     * se borró. El test la cubría y por eso parecía viva. Los saves viejos que aún traen la clave
     * los cubre ahora `legacy saves with removed keys still decode`.
     */
    @Test
    fun `roundtrip preserves every live Wood flag`() {
        val original = GameState.initial(level = 1).copy(
            grid = GameState.initial(level = 1).grid.toMutableMap().apply {
                put(Position(1, 1), CellContent.Wood(
                    colorType = 3,
                    targetColor = com.cukiti.teikafusion.model.TargetColor.entries.first(),
                    armor = 1,
                    coil = 2, coilLit = true, coilSeen = true,
                    poisoned = true,
                    stubborn = true
                ))
            }
        )
        val decoded = gameStateJson.decodeFromString<GameState>(
            gameStateJson.encodeToString(original)
        )
        val w = decoded.grid[Position(1, 1)] as CellContent.Wood
        assertEquals(3, w.colorType)
        assertEquals(com.cukiti.teikafusion.model.TargetColor.entries.first(), w.targetColor)
        assertEquals(1, w.armor)
        assertEquals(2, w.coil)
        assertTrue(w.coilLit)
        assertTrue(w.coilSeen)
        assertTrue(w.poisoned)
        // P-71: perder esta bandera al recargar convertiría un testarudo en madera normal — la
        // mecánica "se curaría" gratis guardando y volviendo a entrar.
        assertTrue(w.stubborn)
    }

    /**
     * Los guardados VIEJOS traen claves que ya no existen (`preplaced` retirada en la it. 34,
     * `companionAbilityUsed` en la it. 33, `ink` de Wood retirada en el rework CORTE NINJA
     * 2026-07-16): ignoreUnknownKeys debe seguir tragándolas. Este test fija ese contrato — si
     * alguien quita ignoreUnknownKeys del Json de producción, revienta aquí y no en el teléfono
     * del jugador.
     */
    @Test
    fun `legacy saves with removed keys still decode`() {
        val state = GameState.initial(level = 1)
        val json = gameStateJson.encodeToString(state)
        // Inyecta las claves retiradas tal y como las escribía un save viejo.
        val legacyJson = json
            .replaceFirst("{", "{\"companionAbilityUsed\":true,")
            // `concrete` se retiró en P-132 (2026-09-03): era el bloque de hormigón de la marea del
            // Infinito y nadie lo ponía a true desde que el modo se borró. Un save escrito antes de
            // esa poda sigue trayendo la clave, y tiene que entrar igual.
            .replace("\"colorType\":", "\"preplaced\":true,\"ink\":true,\"concrete\":true,\"colorType\":")
        val decoded = gameStateJson.decodeFromString<GameState>(legacyJson)
        assertEquals(state.grid.size, decoded.grid.size)
    }

    /**
     * Corte Ninja: el tablero PARTIDO sobrevive al guardado a propósito (lección del congelado del
     * Glaciar, it. 37) — guardar-y-salir no evapora el castigo ni las líneas que faltan.
     */
    @Test
    fun `roundtrip preserves ninja cut state`() {
        val original = GameState.initial(level = 40).copy(
            ninjaCutActive = true,
            ninjaCutLinesLeft = 3
        )
        val decoded = gameStateJson.decodeFromString<GameState>(
            gameStateJson.encodeToString(original)
        )
        assertTrue(decoded.ninjaCutActive)
        assertEquals(3, decoded.ninjaCutLinesLeft)
    }

    /**
     * **UN GUARDADO DE WALLBLOCK YA NO REVIENTA** (2026-08-09). Al eliminar el modo se fueron once
     * campos de [GameState] (`isInfiniteMode`, `tideHeight`, `tideRowsTotal`, `pieceBag`, el
     * ático…), y en el disco de quien actualiza siguen estando todos. Si el `Json` de producción
     * perdiera `ignoreUnknownKeys`, cada una de esas partidas reventaría al abrirse y el jugador
     * solo vería que su progreso «desapareció». Este test fija ese contrato con el JSON exacto que
     * escribía la versión anterior.
     *
     * (El tablero 12×16 del pozo sí invalida el save, pero eso lo decide `continueGame` mirando las
     * coordenadas — aquí lo único que se comprueba es que DECODIFICA.)
     */
    @Test
    fun `un guardado de WALLBLOCK sigue decodificando`() {
        val actual = gameStateJson.encodeToString(GameState.initial(level = 1))
        val viejo = actual.replaceFirst(
            "{",
            """{"isInfiniteMode":true,"tideHeight":3.5,"tideActive":true,"tideRowsTotal":17,""" +
                """"infiniteElapsedSec":42.0,"infiniteTimeLeftMs":0,"timeReviveUsed":false,""" +
                """"infiniteDifficulty":"CHALLENGE","pieceBag":[3,1,4],"wallblockAttic":{},""" +
                """"wallblockHoldUsed":false,"tideActiveSec":123.4,"feverCharge":63.5,""" +
                """"infinitePacts":["POCKET_CLOCK"],"infiniteChallengeKind":"MULTI_LINE","""
        )
        val decoded = gameStateJson.decodeFromString<GameState>(viejo)
        assertNotNull(decoded)
        assertEquals(GameState.GRID_SIZE, decoded.gridW)
        assertEquals(GameState.GRID_SIZE, decoded.gridH)
    }

    /**
     * **LAS MECÁNICAS DE LA PASADA DEL 2026-08-30 SOBREVIVEN AL GUARDADO.** (Eran tres; la tercera,
     * `levelDeal` de El Trato, se retiró el 2026-09-23.) Persisten por el mismo motivo:
     *
     *  - `hotCells` (P-78) — lo que hay que recordar no es dónde estaban, sino **cuáles quedan**. Sin
     *    esto, guardar y salir las reinicia todas: una granja de puntos.
     *  (`goalCell`, LA PORTERÍA, se retiró el 2026-09-26.)
     *
     * Es exactamente el fallo contra el que avisa el test de las banderas de Wood: perder una
     * bandera en el save significa que la mecánica se cura gratis al recargar.
     */
    @Test
    fun `roundtrip preserves las celdas calientes`() {
        val original = GameState.initial(level = 60).copy(
            hotCells = setOf(Position(1, 6), Position(5, 2)),
        )

        val decoded = gameStateJson.decodeFromString<GameState>(
            gameStateJson.encodeToString(original)
        )

        assertEquals(setOf(Position(1, 6), Position(5, 2)), decoded.hotCells)
    }

    /**
     * El campo de color retirado sigue siendo legible para conservar compatibilidad de esquema.
     */
    @Test
    fun `roundtrip preserves legacy Teika color metadata`() {
        val original = GameState.initial(level = 60).copy(
            teikaBodySaves = listOf(
                com.cukiti.teikafusion.model.TeikaBodySave(
                    id = "a", type = TeikaType.TEIKA_2, x = 2f, y = 6f, colorType = 3
                ),
                com.cukiti.teikafusion.model.TeikaBodySave(
                    id = "b", type = TeikaType.TEIKA_1, x = 5f, y = 7f
                ),
            )
        )
        val decoded = gameStateJson.decodeFromString<GameState>(
            gameStateJson.encodeToString(original)
        )
        assertEquals(3, decoded.teikaBodySaves.first { it.id == "a" }.colorType)
        // Los nuevos guardados usan el valor neutro; este campo ya no participa en el juego.
        assertEquals(
            -1,
            decoded.teikaBodySaves.first { it.id == "b" }.colorType
        )
    }

    /**
     * Las dos herramientas de color viajan dentro de la bandeja: sin esto, guardar y retomar las
     * convierte en fichas normales y el jugador pierde la herramienta que le tocaba ese turno.
     */
    @Test
    fun `roundtrip preserves la brocha y la camaleon en la bandeja`() {
        // Un nivel con la bandeja de TRES: la prueba marca tres herramientas distintas, una por
        // ranura. Se busca en vez de escribirse un número porque el reparto de reglas de la casa
        // ([HouseRule]) cambia de nivel en cuanto crece el catálogo, y un `60` escrito a mano se
        // convertiría en un nivel con horizonte —una sola ficha jugable— sin que nadie se entere.
        val level = (com.cukiti.teikafusion.model.LevelMap.introLevels + 1..
            com.cukiti.teikafusion.model.LevelMap.totalLevels)
            .first { !com.cukiti.teikafusion.model.HorizonTray.appliesTo(it) }
        val base = GameState.initial(level = level)
        val original = base.copy(
            availablePieces = base.availablePieces.mapIndexed { i, p ->
                when (i) {
                    0 -> p.copy(isBrush = true)
                    1 -> p.copy(isChameleon = true)
                    2 -> p.copy(isGrowing = true)
                    else -> p
                }
            }
        )

        val decoded = gameStateJson.decodeFromString<GameState>(
            gameStateJson.encodeToString(original)
        )

        assertEquals(1, decoded.availablePieces.count { it.isBrush })
        assertEquals(1, decoded.availablePieces.count { it.isChameleon })
        // P-77: y la que crece. Sin esto, guardar a mitad de la apuesta la devolvería normal.
        assertEquals(1, decoded.availablePieces.count { it.isGrowing })
        // Y nunca la MISMA ficha: una brocha sin color no tiene nada que pintar.
        assertEquals(0, decoded.availablePieces.count { it.isBrush && it.isChameleon })
    }

    /**
     * **P-75 · el HORIZONTE sobrevive al guardado.** La mecánica vende «sabes lo que viene»: si la
     * cola se resorteara al retomar la partida, esa promesa duraría hasta el primer guardado y el
     * jugador que planificó tres turnos por delante se encontraría otro futuro al volver.
     *
     * De paso fija el REPARTO de apertura de un nivel con horizonte —una ficha en la mano y tres en
     * la cola—, que es lo que hace que la promesa exista desde el primer turno.
     */
    @Test
    fun `roundtrip preserves el horizonte de fichas`() {
        val nivel = com.cukiti.teikafusion.model.HorizonTray.levels().first()
        val original = GameState.initial(level = nivel)
        assertEquals(
            com.cukiti.teikafusion.model.HorizonTray.PLAYABLE,
            original.availablePieces.size
        )
        assertEquals(
            com.cukiti.teikafusion.model.HorizonTray.HORIZON,
            original.pieceHorizon.size
        )

        val decoded = gameStateJson.decodeFromString<GameState>(
            gameStateJson.encodeToString(original)
        )

        assertEquals(original.pieceHorizon.map { it.id }, decoded.pieceHorizon.map { it.id })
        assertEquals(original.pieceHorizon.map { it.shape }, decoded.pieceHorizon.map { it.shape })
    }

    /**
     * **EL PEDIDO SE RETIRÓ (2026-09-22) Y LOS GUARDADOS QUE LO TRAEN SIGUEN ENTRANDO.** Un save de
     * antes lleva la clave `requestMet`; `ignoreUnknownKeys` tiene que tragarla sin tocar nada más.
     */
    @Test
    fun `un guardado con la marca del pedido retirado sigue entrando`() {
        val original = GameState.initial(level = 60)
        val json = gameStateJson.encodeToString(original)
        val conPedido = json.replaceFirst("{", "{\"requestMet\":true,")
        val decoded = gameStateJson.decodeFromString<GameState>(conPedido)
        assertEquals(original.level, decoded.level)
        assertEquals(original.grid.keys, decoded.grid.keys)
    }

    /**
     * **Y AL REVÉS: un guardado ANTERIOR a estas mecánicas sigue entrando, y entra NEUTRO.**
     *
     * Un save de ayer no trae ninguna de las tres claves. Lo que tiene que pasar es que el nivel se
     * juegue **exactamente como se jugaba antes**: sin trato (que equivale a la carta de la casa),
     * sin celdas calientes y sin portería. Se simula con un `Json` que no escribe los valores por
     * defecto — que es justo la forma que tenía el JSON antes de que los campos existieran.
     */
    @Test
    fun `un guardado anterior a las mecanicas nuevas entra neutro`() {
        val legacyShape = Json {
            allowStructuredMapKeys = true
            ignoreUnknownKeys = true
            encodeDefaults = false          // las claves nuevas ni se escriben
        }
        val antes = GameState.initial(level = 60).copy(
            hotCells = emptySet(),
            // Y la del segundo lote (P-75), por el mismo motivo.
            pieceHorizon = emptyList(),
        )
        val json = legacyShape.encodeToString(antes)
        assertTrue("el JSON simulado no debería traer las claves nuevas",
            "hotCells" !in json &&
                "pieceHorizon" !in json)

        val decoded = gameStateJson.decodeFromString<GameState>(json)

        assertTrue(decoded.hotCells.isEmpty())
        // La cola vacía es la forma que tiene un save de ayer, y es RECUPERABLE: el primer reparto
        // la rellena entera (`GameViewModel.refillHorizonTray`).
        assertTrue(decoded.pieceHorizon.isEmpty())
        assertTrue("el tablero del save viejo no sobrevivió", decoded.grid.isNotEmpty())
    }

    /**
     * **UN GUARDADO CON LA CARGA DEL CAPITÁN SIGUE ABRIÉNDOSE** (2026-09-24). La carga del capitán
     * se borró con el rediseño de power-ups, pero hay partidas guardadas que traen sus dos campos:
     * CONTINUAR no puede romperse por un dato que ya no existe.
     */
    @Test
    fun `un guardado con la carga del capitan sigue abriendose`() {
        val original = GameState.initial(level = 40)
        val legacy = gameStateJson.encodeToString(original)
            .replaceFirst("{", "{\"captainChargeProgress\":11,\"captainChargeSpent\":true,")

        val decoded = gameStateJson.decodeFromString<GameState>(legacy)

        assertEquals(40, decoded.level)
    }
}
