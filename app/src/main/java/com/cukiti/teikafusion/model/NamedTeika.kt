package com.cukiti.teikafusion.model

import com.cukiti.teikafusion.R

enum class NamedTeikaCategory(val label: String) {
    TUTORIAL("Tutorial"),
    EARLY("Primeros pasos"),
    MID("Exploración"),
    CLIMAX("Épico")
}

/**
 * Un personaje Teika con nombre propio.
 *
 * @param id           Slug único. Usado como clave en DataStore.
 * @param name         Nombre que ve el jugador.
 * @param unlockLevel  Nivel en el que se desbloquea GRATIS al completarlo (auto-claim).
 * @param category     Agrupación / rareza (selector de compañero, coste de upgrades…).
 * @param teikaType    Tipo base para mostrar silueta cuando está bloqueado.
 * @param description  Frase corta de sabor (se muestra en el overlay de revelación).
 * @param lottieResId  Recurso Lottie único de este personaje.
 *
 * V-04 (2026-07-29): el precio en teikoins se RETIRÓ. El único modelo de adquisición es
 * progresión gratis ([unlockLevel] + claim en LEVEL_COMPLETE / retroClaim).
 */
data class NamedTeika(
    val id: String,
    val name: String,
    val unlockLevel: Int,
    val category: NamedTeikaCategory,
    val teikaType: TeikaType,
    val description: String,
    val lottieResId: Int
)

/**
 * Registro de los 14 personajes del juego: uno por bioma. Su [NamedTeika.unlockLevel] se DERIVA de
 * [LevelMap.chapterStart] (el compañero i se desbloquea al entrar al capítulo/bioma i), así que
 * sigue automáticamente a la escala de niveles definida en [LevelMap].
 */
object NamedTeikaRegistry {

    val all: List<NamedTeika> = listOf(

        // Los rangos de nivel NO se anotan aquí: unlockLevel se deriva de LevelMap.chapterStart
        // (compañero i = entrada del capítulo i) y sigue solo a la escala real de niveles.

        // ── TUTORIAL (capítulos 1-3) ─────────────────────────────────────────

        NamedTeika(
            id          = "cristal",
            name        = "Cristóbal",
            unlockLevel = LevelMap.chapterStart[0],
            category    = NamedTeikaCategory.TUTORIAL,
            teikaType   = TeikaType.TEIKA_1,
            description = "Su cuerpo brilla como el cristal. Frágil pero hermoso.",
            lottieResId = R.raw.teika_cristal
        ),

        NamedTeika(
            // Personaje femenino: nombre visible = Maite, id interno = "dormilona". El id es la CLAVE
            // de la habilidad/VFX, así que está propagado en CompanionAbility, GameViewModel
            // y VFX. (Cambiar el id invalida un claim previo de "dormilon" en saves antiguos.)
            id          = "dormilona",
            name        = "Maite",
            unlockLevel = LevelMap.chapterStart[1],
            category    = NamedTeikaCategory.TUTORIAL,
            teikaType   = TeikaType.TEIKA_1,
            description = "Siempre con sueño. Pero cuando despierta, ¡es imparable!",
            lottieResId = R.raw.teika_dormilon
        ),

        NamedTeika(
            id          = "miedoso",
            name        = "Timoteo",
            unlockLevel = LevelMap.chapterStart[2],
            category    = NamedTeikaCategory.TUTORIAL,
            teikaType   = TeikaType.TEIKA_2,
            description = "Se asusta de todo, pero sigue adelante. ¡El valiente tiene miedo y actúa!",
            lottieResId = R.raw.teika_miedoso
        ),

        // ── PRIMEROS PASOS (capítulos 4-7) ───────────────────────────────────

        NamedTeika(
            id          = "planta",
            name        = "Flora",
            unlockLevel = LevelMap.chapterStart[3],
            category    = NamedTeikaCategory.EARLY,
            teikaType   = TeikaType.TEIKA_2,
            description = "Crece lento pero seguro. La paciencia es su superpoder.",
            lottieResId = R.raw.teika_planta
        ),

        NamedTeika(
            id          = "electrico",
            name        = "Rayco",
            unlockLevel = LevelMap.chapterStart[4],
            category    = NamedTeikaCategory.EARLY,
            teikaType   = TeikaType.TEIKA_2,
            description = "Chispa pura y energía sin límite. ¡Toca algo y verás!",
            lottieResId = R.raw.teika_electrico
        ),

        NamedTeika(
            id          = "fuego",
            name        = "Iñaki",
            unlockLevel = LevelMap.chapterStart[5],
            category    = NamedTeikaCategory.EARLY,
            teikaType   = TeikaType.TEIKA_3,
            description = "Nació de una chispa. Ahora nada lo apaga.",
            lottieResId = R.raw.teika_fuego
        ),

        NamedTeika(
            id          = "ninja",
            name        = "Kenji",
            unlockLevel = LevelMap.chapterStart[6],
            category    = NamedTeikaCategory.EARLY,
            teikaType   = TeikaType.TEIKA_3,
            description = "Se mueve en las sombras. Nadie lo ve venir.",
            lottieResId = R.raw.teika_ninja
        ),

        // ── EXPLORACIÓN (capítulos 8-11) ──────────────────────────────────────

        NamedTeika(
            id          = "hielo",
            name        = "Nieves",
            unlockLevel = LevelMap.chapterStart[7],
            category    = NamedTeikaCategory.MID,
            teikaType   = TeikaType.TEIKA_3,
            description = "Todo lo que toca queda congelado. Calma absoluta bajo cero.",
            lottieResId = R.raw.teika_hielo
        ),

        NamedTeika(
            id          = "fantasma",
            name        = "Morti",
            unlockLevel = LevelMap.chapterStart[8],
            category    = NamedTeikaCategory.MID,
            teikaType   = TeikaType.TEIKA_3,
            description = "Aparece y desaparece. ¿Está realmente ahí?",
            lottieResId = R.raw.teika_fantasma
        ),

        NamedTeika(
            id          = "gloton",
            name        = "Chueta",
            unlockLevel = LevelMap.chapterStart[9],
            category    = NamedTeikaCategory.MID,
            teikaType   = TeikaType.TEIKA_4,
            description = "Come más que nadie. Pero su corazón es enorme.",
            lottieResId = R.raw.teika_gloton
        ),

        NamedTeika(
            id          = "toxico",
            name        = "Toño",
            unlockLevel = LevelMap.chapterStart[10],
            category    = NamedTeikaCategory.MID,
            teikaType   = TeikaType.TEIKA_4,
            description = "Peligroso para los que se acercan demasiado. ¡Con cuidado!",
            lottieResId = R.raw.teika_toxico
        ),

        // ── ÉPICO (capítulos 12-14) ───────────────────────────────────────────

        NamedTeika(
            id          = "estrella",
            name        = "Estela",
            unlockLevel = LevelMap.chapterStart[11],
            category    = NamedTeikaCategory.CLIMAX,
            teikaType   = TeikaType.MEGA_TEIKA,
            description = "Brilló tanto que el cielo la adoptó como suya.",
            lottieResId = R.raw.teika_estrella
        ),

        NamedTeika(
            id          = "rey",
            name        = "Reynaldo",
            unlockLevel = LevelMap.chapterStart[12],
            category    = NamedTeikaCategory.CLIMAX,
            teikaType   = TeikaType.MEGA_TEIKA,
            description = "El más poderoso de todos. Gobierna con justicia y fuerza.",
            lottieResId = R.raw.teika_rey
        ),

        NamedTeika(
            id          = "galactico",
            name        = "Galo",
            unlockLevel = LevelMap.chapterStart[13],
            category    = NamedTeikaCategory.CLIMAX,
            teikaType   = TeikaType.MEGA_TEIKA,
            description = "Viene de más allá de las estrellas. El fin del viaje.",
            lottieResId = R.raw.teika_galactico
        )
    )

    /** Personaje que se desbloquea exactamente en [level], o null si no hay ninguno. */
    fun forLevel(level: Int): NamedTeika? = all.firstOrNull { it.unlockLevel == level }

    /** Personaje con id [id], o null si no existe. */
    fun byId(id: String): NamedTeika? = all.firstOrNull { it.id == id }
}
