package com.cukiti.teikafusion.model

import kotlinx.serialization.Serializable

/**
 * Cómo entra en juego una habilidad. (PODADO 2026-07-30) Existían además `SELECT_TEIKA` y
 * `SELECT_TWO_WOOD` —el jugador elegía objetivo tocando el tablero—, sin ninguna de las 14
 * habilidades usándolas desde que Rayco pasó a «Energía Cruzada» y Morti a «Tembleque».
 * Reintroducirlas exige rehacer el ciclo de selección entero (ver `docs/bloques/06`).
 */
enum class AbilityActivation {
    INSTANT,           // fires immediately — no board interaction needed
    PASSIVE_PIECES     // passive buff applied to next N pieces
}

data class CompanionAbilityDef(
    val name: String,
    val description: String,
    val activation: AbilityActivation,
    val passiveCount: Int = 0,      // pieces remaining (PASSIVE_PIECES only)
    /**
     * **¿Esta habilidad LIBERA celdas del tablero?** (P-105, 2026-09-02).
     *
     * Es la lista blanca del rescate por atasco: `GameViewModel.isStuck()` solo indulta una derrota
     * por «no cabe nada» si el jugador tiene carga de una habilidad marcada aquí **y la lleva en el
     * cinturón**. Antes la pregunta era `instantAbilityCanAct` —«¿tiene algo que hacer?»— y eso
     * dejaba tres agujeros por los que un nivel se quedaba SIN NINGUNA SALIDA (ver C-1 en
     * MEJORAS.md):
     *
     *  - las `PASSIVE_PIECES` (Cristal, Iñaki) devolvían `true` sin mirar el tablero, y las dos
     *    necesitan **colocar una ficha** para hacer algo — que es justo lo que no se puede;
     *  - **Parto Real** AÑADE un cuerpo (más celdas bloqueadas) y **Deseo Estelar** RELLENA el hueco
     *    conexo: las dos empeoran un tablero lleno, y las dos suprimían la derrota.
     *
     * Se declara a mano, no se deduce: el motor no sabe qué hace cada efecto y adivinarlo era el
     * error. `CompanionAbilityFreesSpaceTest` vigila que las catorce estén clasificadas.
     */
    val freesSpace: Boolean = false
)

/** Companion abilities keyed by character id. */
val COMPANION_ABILITIES: Map<String, CompanionAbilityDef> = mapOf(
    "cristal"   to CompanionAbilityDef(
        "Escudo Cristalino",
        "Saca las mejores piezas y las coloca solas (girándolas si hace falta) hasta encadenar varios reventones seguidos (más cuanto mayor es el nivel).",
        AbilityActivation.PASSIVE_PIECES, passiveCount = 3),
    "dormilona" to CompanionAbilityDef(
        "Siesta de Maite",
        "El tablero se desploma hasta el fondo y, al asentarse, caen desde arriba filas llenas (más cuanto mayor es el nivel) que rematan líneas al aterrizar.",
        AbilityActivation.INSTANT, freesSpace = true),
    "miedoso"   to CompanionAbilityDef(
        "Puzzle Deslizante",
        "Timoteo pone el tablero a armarse solo: la madera se desliza como las losetas de un rompecabezas y se agrupa hasta quedar juntita, rompiendo por el camino cada línea que va completando.",
        AbilityActivation.INSTANT, freesSpace = true),
    "planta"    to CompanionAbilityDef(
        "Cañón Floral",
        "Una flor florece en el centro y entierra a tu Teika más grande. Renace como capullo-cañón bajo el tablero: mueve la línea de puntería y suéltala para dispararlo — rompe madera y rebota antes de frenar.",
        AbilityActivation.INSTANT, freesSpace = true),
    "electrico" to CompanionAbilityDef(
        "Energía Cruzada",
        "Rayco energiza tus Teikas. Cuando completes una línea que un Teika ocupa, sus filas y columnas perpendiculares se autocompletan atrayendo madera y se rompen también.",
        AbilityActivation.INSTANT),
    "fuego"     to CompanionAbilityDef(
        "Fichas en Llamas",
        "Iñaki prende tus fichas. Al colocar una ficha en llamas, el fuego arrasa por completo todas las filas y columnas que ocupa, en las 4 direcciones.",
        AbilityActivation.PASSIVE_PIECES),
    "ninja"     to CompanionAbilityDef(
        "Corte de Sombras",
        "Kenji cruza la pantalla a tajos de katana y parte por la mitad los bloques con estrella: cada uno te da el DOBLE de estrellas y el DOBLE de puntos.",
        AbilityActivation.INSTANT, freesSpace = true),
    "hielo"     to CompanionAbilityDef(
        "Estalactitas",
        "Nieves invoca carámbanos que caen sobre las columnas más llenas y perforan su madera de arriba abajo.",
        AbilityActivation.INSTANT, freesSpace = true),
    "fantasma"  to CompanionAbilityDef(
        "Posesión",
        "Morti posee unos bloques sueltos: se vuelven espectrales, ATRAVIESAN el tablero y se solidifican justo en los huecos que completan línea.",
        AbilityActivation.INSTANT, freesSpace = true),
    "gloton"    to CompanionAbilityDef(
        "El Banquete",
        "Chueta asoma por debajo del tablero y abre las fauces: los bloques con estrella se despegan y caen a su boca. Cada bocado te da su estrella y sus puntos.",
        AbilityActivation.INSTANT, freesSpace = true),
    "toxico"    to CompanionAbilityDef(
        "Miasma Venenoso",
        "Toño esparce un miasma tóxico que corroe la madera del tablero hasta que revienta toda a la vez. Las fichas que coloques después quedan sanas.",
        AbilityActivation.INSTANT, freesSpace = true),
    "estrella"  to CompanionAbilityDef(
        "Deseo Estelar",
        "Pone en la bandeja un bloque tocado por las estrellas; al soltarlo se derrama como líquido y llena el espacio vacío conexo hasta toparse con muros o bloques.",
        AbilityActivation.INSTANT),
    "rey"       to CompanionAbilityDef(
        "Parto Real",
        "Del cuerpo de tus Teikas sale un Teika pequeño (T1), empujado hacia fuera como si lo dieran a luz. Material de fusión gratis. Solo paren los de tier 2 o más.",
        AbilityActivation.INSTANT),
    "galactico" to CompanionAbilityDef(
        "Eclipse Vivo",
        "Galo proyecta un eclipse sobre el rincón de madera más denso y lo pulveriza; el cielo te devuelve el tablero jugable.",
        AbilityActivation.INSTANT, freesSpace = true)
)

/**
 * Compañeros cuya habilidad está DESACTIVADA: siguen existiendo (claim/retrato) pero su habilidad
 * NO dispara en partida ni muestra botón. Vacío desde el rework 2026-07-17: Rayco/Iñaki/Nieves/Toño
 * recuperaron habilidad (Sobrecarga 2-tap, Cruz de Fuego, Estalactitas, Nube de Gas). Se mantiene
 * el gancho por si hay que volver a apagar alguna.
 */
val DISABLED_COMPANION_ABILITIES: Set<String> = emptySet()

/** ¿La habilidad del compañero [companionId] está activa (usable en partida)? */
fun companionAbilityEnabled(companionId: String): Boolean =
    companionId !in DISABLED_COMPANION_ABILITIES

// (PODADO 2026-07-30) `CompanionSelectionMode` (TwoTeikas / TwoWoodCells / OneTeikaChain) vivía
// aquí: el estado del modo «elige objetivo» que ninguna habilidad abría ya.

// (La cinemática de entrada del personaje se retiró: al castear una habilidad ya NO se muestra
//  el retrato gigante ni el banner del nombre; el efecto se aplica de inmediato y solo corre el
//  VFX temático sobre el tablero. El objeto CompanionCinematic de tiempos quedó obsoleto.)

// ── Mejora de habilidades de compañero (Overhaul Fase 2) ──────────────────────
// Cada compañero desbloqueado puede VER y MEJORAR su habilidad desde la screen H. Teika.
// El nivel escala el efecto (ej. Cristal: coloca fichas hasta encadenar `nivel` reventones, 1→3).
// Persistido en DataStore + respaldo cloud-save.
/**
 * **5 niveles** desde el 2026-09-24 (P-270, decisión del usuario; antes 3). Los niveles 1–3 NO cambian
 * —nadie pierde ni gana nada al actualizar— y el 4 y el 5 van POR ENCIMA: más de lo mismo donde hay
 * margen, y «eco» ([AbilityScaling.echoChance]) donde el 3 ya lo abarcaba todo.
 */
const val COMPANION_ABILITY_MAX_LEVEL = 5

/**
 * Rareza de la habilidad de un compañero. Deriva del capítulo/categoría en que se desbloquea:
 * cuanto más tarde aparece, más poderosa y rara es.
 *
 * Cuatro rarezas (2026-07-28) — antes eran COMUN/POCO_COMUN/RARA/EPICA aquí y COMÚN/RARO/ÉPICO/
 * LEGENDARIO en el selector de compañero, con colores DISTINTOS para la misma categoría. Ahora hay
 * una sola escala y un solo color por rareza, y ese color tiñe la tarjeta del Inventario.
 *
 * La rareza decide cuánto cristal y cuántos teikoins cuesta cada mejora (desde el 2026-09-24 hay un
 * solo cristal: la rareza ya no elige tiers, ver [MagicCrystal.kt]).
 */
enum class AbilityRarity(val label: String, val colorArgb: Long) {
    COMUN      ("Común",       0xFFB0BCD0),
    RARO       ("Raro",        0xFF4DB8FF),
    EPICO      ("Épico",       0xFFB983FF),
    LEGENDARIO ("Legendario",  0xFFFFC93C)
}

/** Rareza del compañero [companionId], derivada de su [NamedTeikaCategory]. */
fun abilityRarityFor(companionId: String): AbilityRarity =
    when (NamedTeikaRegistry.byId(companionId)?.category) {
        NamedTeikaCategory.TUTORIAL -> AbilityRarity.COMUN
        NamedTeikaCategory.EARLY    -> AbilityRarity.RARO
        NamedTeikaCategory.MID      -> AbilityRarity.EPICO
        NamedTeikaCategory.CLIMAX   -> AbilityRarity.LEGENDARIO
        null                        -> AbilityRarity.COMUN
    }

/**
 * AURA del nivel de habilidad: tres auras distintas para que el jugador lea el nivel de un vistazo,
 * sin abrir nada (pedido 2026-07-28). Tiñe el medallón del compañero en la mochila y en el héroe.
 */
enum class AbilityAura(val level: Int, val colorArgb: Long, val glow: Float) {
    NIVEL_1(1, 0xFF7FE3FF, 0.50f),   // celeste tenue  — recién desbloqueada
    NIVEL_2(2, 0xFF8BFF6B, 0.62f),   // verde          — reforzada
    NIVEL_3(3, 0xFFB983FF, 0.75f),   // violeta
    NIVEL_4(4, 0xFFFF7FC8, 0.88f),   // rosa intenso
    NIVEL_5(5, 0xFFFFC93C, 1.00f);   // dorado         — al máximo

    companion object {
        fun of(level: Int): AbilityAura = entries.getOrElse(level.coerceIn(1, entries.size) - 1) { NIVEL_1 }
    }
}

/**
 * Cristales por subida, por [AbilityRarity]: índice 0 = L1→L2 … índice 3 = L4→L5 (P-267, 2026-09-24).
 * Todo el roster al máximo cuesta 1.515; la campaña da ~0,7× eso ([CrystalEconomy]): hay que elegir.
 */
private val COMPANION_ABILITY_CRYSTAL_COSTS: Map<AbilityRarity, List<Int>> = mapOf(
    AbilityRarity.COMUN      to listOf(5, 10, 15, 20),
    AbilityRarity.RARO       to listOf(10, 15, 25, 35),
    AbilityRarity.EPICO      to listOf(15, 25, 35, 50),
    AbilityRarity.LEGENDARIO to listOf(20, 35, 50, 70),
)

/**
 * Coste BASE en teikoins por subida: índice 0 = L1→L2 … índice 3 = L4→L5 (se escala por rareza).
 * Todo el roster al máximo: ~47.000 teikoins, frente a ~59.000 ganados jugando la campaña.
 */
val COMPANION_ABILITY_UPGRADE_COSTS = listOf(150, 300, 500, 700)

/**
 * Multiplicador de teikoins por rareza. Se mantiene MODERADO a propósito: el freno real de la
 * progresión son los cristales (que no se compran), no las monedas (que sí). Si el multiplicador de
 * moneda fuera el muro, pagar sería subir de nivel — justo lo que no queremos.
 */
private val COMPANION_ABILITY_RARITY_COIN_MULT: Map<AbilityRarity, Float> = mapOf(
    AbilityRarity.COMUN to 1.0f,
    AbilityRarity.RARO to 1.6f,
    AbilityRarity.EPICO to 2.4f,
    AbilityRarity.LEGENDARIO to 3.2f
)

/** Texto del efecto por nivel de cada compañero (UI). Cristal definido (showcase); resto se afina en Fase 8. */
val COMPANION_ABILITY_LEVEL_TEXTS: Map<String, List<String>> = mapOf(
    "cristal" to listOf(
        "Coloca las mejores fichas solas hasta encadenar 1 reventón",
        "…hasta encadenar 2 reventones",
        "…hasta encadenar 3 reventones",
        "…hasta encadenar 4 reventones",
        "…hasta encadenar 5 reventones"
    ),
    "dormilona" to listOf(
        "El tablero se desploma y cae 1 fila llena que remata líneas",
        "Se desploma y caen 2 filas llenas",
        "Se desploma y caen 3 filas llenas",
        "Se desploma y caen 4 filas llenas",
        "Se desploma y caen 5 filas llenas"
    ),
    "miedoso" to listOf(
        "4 pasadas del rompecabezas: la madera se agrupa y rompe las líneas que forma",
        "7 pasadas: se aprieta bastante más",
        "12 pasadas: el tablero se arma del todo",
        "16 pasadas",
        "20 pasadas: el rompecabezas más apretado"
    ),
    "fuego" to listOf(
        "Prende 1 ficha: al colocarla el fuego arrasa su fila y columna",
        "Prende 2 fichas en llamas",
        "Prende TODA la bandeja en llamas",
        "TODA la bandeja + eco: 25 % de no gastar la carga",
        "TODA la bandeja + eco: 50 % de no gastar la carga"
    ),
    "planta" to listOf(
        "El disparo rebota 2 veces rompiendo madera a su paso",
        "El disparo rebota 4 veces",
        "El disparo rebota 6 veces",
        "El disparo rebota 8 veces",
        "El disparo rebota 10 veces"
    ),
    "hielo" to listOf(
        "3 carámbanos perforan las 3 columnas más llenas",
        "4 carámbanos",
        "5 carámbanos",
        "6 carámbanos",
        "7 carámbanos"
    ),
    "galactico" to listOf(
        "Eclipsa una zona 3×3 del rincón más denso y pulveriza su madera",
        "El eclipse crece a 4×4",
        "Cruz ancha (fila+columna) o 5×5, lo que más limpie, + nace 1 TEIKA_1",
        "Lo mismo + eco: 25 % de no gastar la carga",
        "Lo mismo + eco: 50 % de no gastar la carga"
    ),
    "estrella" to listOf(
        "Deseo Estelar: una ficha que se derrama y llena hasta 8 casillas vacías",
        "Llena hasta 12 casillas",
        "Llena hasta 18 casillas",
        "Llena hasta 24 casillas",
        "Llena hasta 30 casillas"
    ),
    "rey" to listOf(
        "El Teika de mayor tier pare un T1",
        "La mitad de tus Teikas paren un T1",
        "TODOS tus Teikas paren un T1",
        "TODOS paren + eco: 25 % de no gastar la carga",
        "TODOS paren + eco: 50 % de no gastar la carga"
    ),
    "toxico" to listOf(
        "El miasma corroe las 4 columnas más pesadas",
        "Corroe las 6 columnas más pesadas",
        "Corroe TODO el tablero + bonus de score",
        "TODO el tablero + eco: 25 % de no gastar la carga",
        "TODO el tablero + eco: 50 % de no gastar la carga"
    ),
    "gloton" to listOf(
        "Se traga 4 bloques con estrella (su estrella y sus puntos)",
        "Se traga 8 bloques con estrella",
        "Se traga TODOS los bloques con estrella del tablero",
        "TODOS + eco: 25 % de no gastar la carga",
        "TODOS + eco: 50 % de no gastar la carga"
    ),
    "fantasma" to listOf(
        "Posee 3 bloques y los mete en los huecos que completan línea",
        "Posee 5 bloques",
        "Posee 8 bloques",
        "Posee 10 bloques",
        "Posee 12 bloques"
    ),
    "ninja" to listOf(
        "3 tajos: parte hasta 4 bloques con estrella (doble estrella y doble puntos)",
        "5 tajos: parte hasta 8 bloques con estrella",
        "7 tajos: parte TODOS los bloques con estrella del tablero",
        "TODOS + eco: 25 % de no gastar la carga",
        "TODOS + eco: 50 % de no gastar la carga"
    ),
    "electrico" to listOf(
        "Energiza tu Teika de mayor tier (remata sus perpendiculares)",
        "Energiza tus 2 Teikas de mayor tier",
        "Energiza TODOS tus Teikas",
        "TODOS + eco: 25 % de no gastar la carga",
        "TODOS + eco: 50 % de no gastar la carga"
    )
)

fun companionAbilityLevelText(companionId: String, level: Int): String {
    val list = COMPANION_ABILITY_LEVEL_TEXTS[companionId]
    return list?.getOrNull((level - 1).coerceIn(0, list.size - 1))
        ?: "Nivel $level — habilidad reforzada"
}

@Serializable
data class CompanionAbilityUpgrades(
    val levels: Map<String, Int> = emptyMap()
) {
    fun levelOf(companionId: String): Int =
        (levels[companionId] ?: 1).coerceIn(1, COMPANION_ABILITY_MAX_LEVEL)

    fun isMaxed(companionId: String): Boolean =
        levelOf(companionId) >= COMPANION_ABILITY_MAX_LEVEL

    /**
     * Coste en teikoins para subir al siguiente nivel (escalado por rareza); [Int.MAX_VALUE] si maxed.
     *
     * [costPercent] es el `abilityUpgradeCostPercent` de Remote Config (100 = tabla original). Vive
     * AQUÍ para que el motor que cobra y la ficha que enseña el precio usen la misma cuenta: antes el
     * motor lo aplicaba y la ficha no, y con un 120 % la ficha dejaba mejorar, el motor lo rechazaba
     * en silencio y aun así salía la cinemática de «¡mejorada!».
     */
    fun costToNext(companionId: String, costPercent: Int = 100): Int {
        val lvl = levelOf(companionId)
        if (lvl >= COMPANION_ABILITY_MAX_LEVEL) return Int.MAX_VALUE
        val base = COMPANION_ABILITY_UPGRADE_COSTS.getOrElse(lvl - 1) { return Int.MAX_VALUE }
        val mult = COMPANION_ABILITY_RARITY_COIN_MULT[abilityRarityFor(companionId)] ?: 1f
        val table = (base * mult).toInt()
        return if (costPercent == 100) table else ((table.toLong() * costPercent) / 100L).toInt()
    }

    /** Cristales para la próxima subida; 0 si ya está al máximo. */
    fun crystalCostToNext(companionId: String): Int {
        val lvl = levelOf(companionId)
        if (lvl >= COMPANION_ABILITY_MAX_LEVEL) return 0
        return COMPANION_ABILITY_CRYSTAL_COSTS[abilityRarityFor(companionId)]?.getOrNull(lvl - 1) ?: 0
    }

    /**
     * ¿Se puede mejorar YA a [companionId] con estos materiales? La pregunta del punto rojo — la hacen
     * la tarjeta de la banca y la pestaña de Inventario, así que vive aquí y no en cada una (P-269).
     */
    fun canUpgradeNow(
        companionId: String,
        teikoins: Int,
        crystals: CrystalInventory,
        costPercent: Int = 100,
    ): Boolean =
        !isMaxed(companionId) && teikoins >= costToNext(companionId, costPercent) &&
            crystals.canSpend(crystalCostToNext(companionId))

    fun withUpgrade(companionId: String): CompanionAbilityUpgrades {
        val lvl = levelOf(companionId)
        if (lvl >= COMPANION_ABILITY_MAX_LEVEL) return this
        return copy(levels = levels + (companionId to lvl + 1))
    }
}
