package com.cukiti.teikafusion.model

/**
 * **ARCO DE CAPÍTULO** (2026-07-31, diagnóstico del usuario: «los 20 niveles del Cristal se sienten
 * iguales»).
 *
 * ## El problema que resuelve
 *
 * Todo lo que definía un nivel salía de una sola variable: `t = (nivel-1)/292`, la posición en TODO
 * el juego. Un capítulo de 18-22 niveles ocupa el **7 %** de esa recta, así que cualquier curva
 * basada en `t` es, dentro de un bioma, una línea plana. En el Cristal (niveles 5-22) eso significa:
 * el tablero se llena **2 celdas** más en 18 niveles, la figura avanza 6 posiciones de un pool de
 * 100 y la mecánica del bioma es una CONSTANTE — `GLACIER_FREEZE_AFTER = 3` vale igual en el primer
 * nivel del capítulo que en el último. Lo único que se movía de verdad era cuántas estrellas te
 * piden, y eso no es variedad: es la misma tarea repetida más veces.
 *
 * Y el juego tenía la pieza que faltaba sin usar: `LevelMap.localIndex(level)` —«nivel 3 de 18»—
 * existía **sin un solo llamante**.
 *
 * ## La regla
 *
 * Cada capítulo se lee en **tres actos** ([actFor]): presentación (primer 30 %), desarrollo (el
 * centro) y clímax (último 25 %).
 *
 * ## Qué mueve el arco HOY (y qué ya no)
 *
 * El diseño original modulaba el **umbral ocioso del bioma** —+1 turno en la presentación, −1 en el
 * clímax— y ese dial **ya no existe** (P-118, 2026-09-03): lo dejó sin efecto la decisión de turnos
 * FIJOS del 2026-08-30, «dos de aviso y al tercero ejecuta», la misma para todos los niveles del
 * capítulo y vigilada por `BiomePhaseContractTest`. Las funciones que lo calculaban sobrevivieron
 * año y medio sin llamantes, con tests verdes defendiéndolas; se han retirado, y abajo queda escrito
 * por qué no vuelven solas.
 *
 * Lo que el arco SÍ decide hoy:
 *  - [actFor] alimenta el ajuste de dificultad de [LevelChallenge];
 *  - [requiresMega] hace obligatorio el MEGA en todos los niveles con bioma.
 *
 * Y la textura del capítulo la mueve `LevelDirector` por otras dos columnas que sí se aplican:
 * densidad del tablero y sesgo de la bandeja.
 */
enum class ChapterAct { PRESENTACION, DESARROLLO, CLIMAX }

object ChapterArc {

    /** Fracción del capítulo que dura la presentación (amable). */
    const val PRESENTATION_END = 0.30f

    /** Fracción a partir de la cual empieza el clímax (aprieta). */
    const val CLIMAX_START = 0.75f

    // ── (RETIRADO 2026-09-03, P-118) `idleDelta` y `idleThreshold` ──────────────────────────────
    // El arco modulaba el umbral ocioso del bioma: +1 turno en la presentación, −1 en el clímax.
    // La decisión de **turnos FIJOS** del 2026-08-30 («dos de aviso y al tercero ejecuta») lo dejó
    // sin efecto: el único umbral vivo es `LevelDirector.idleThreshold`, que ignora el nivel a
    // propósito. Estas dos funciones se quedaron sin un solo llamante en producción **pero con sus
    // tests verdes afirmando «presentación = un turno MÁS, clímax = un turno MENOS»**, mientras
    // `BiomePhaseContractTest` afirmaba lo contrario llamando a la función viva. Dos suites verdes
    // defendiendo reglas opuestas es peor que el código muerto a secas: quien leyera esto creería
    // que el capítulo respira, y tenía un test dándole la razón.
    //
    // El arco SIGUE VIVO en lo que de verdad cuenta la historia del capítulo: [actFor] y
    // [requiresMega], además de la densidad y la bandeja que mueve `LevelDirector`. Si algún día
    // vuelve una curva de ocio, se implementa entera y se cablea — no se resucita desde aquí.

    /**
     * Acto al que pertenece [level] dentro de SU capítulo. Los niveles de intro (sin bioma) y
     * cualquier nivel fuera de mapa se tratan como DESARROLLO: sin bioma no hay arco que contar.
     */
    fun actFor(level: Int): ChapterAct {
        val chapter = LevelMap.chapterIndexForLevel(level)
        if (chapter < 0) return ChapterAct.DESARROLLO
        val count = LevelMap.chapters[chapter].levelCount
        if (count <= 2) return ChapterAct.DESARROLLO       // un capítulo así no da para tres actos
        val progress = LevelMap.localIndex(level).toFloat() / count
        return when {
            progress < PRESENTATION_END -> ChapterAct.PRESENTACION
            progress >= CLIMAX_START    -> ChapterAct.CLIMAX
            else                        -> ChapterAct.DESARROLLO
        }
    }

    /**
     * **¿Este nivel exige formar un MEGA TEIKA para ganar?** SIEMPRE (2026-08-01, decisión del
     * usuario: «que sea obligatorio formar el MEGA en el nivel de nuevo»).
     *
     * Se restringió al clímax el 2026-07-31c porque se creía que el suelo de 16 líneas del MEGA
     * —16 Teikas T1, uno por línea— disparaba el presupuesto de turnos. **Medido, no era cierto:**
     * el objetivo de estrellas ya exigía 16 líneas por su cuenta en todo el mapa a partir del nivel
     * ~20, así que `expectedLines` valía 16 con MEGA y sin él. Exigirlo en todos los niveles no
     * cuesta un solo turno de más; lo que encarecía los niveles era el propio presupuesto, y eso se
     * corrigió bajando los diales de [LevelBudget].
     *
     * La mitad Suika del juego vuelve así a ser obligatoria en cada nivel, que es lo que la hace
     * mitad del juego y no un adorno de final de capítulo.
     */
    fun requiresMega(level: Int): Boolean = when {
        TutorialCampaign.isTutorialLevel(level) -> TutorialCampaign.requiresMega(level)
        else -> LevelMap.chapterIndexForLevel(level) >= 0
    }

    // ── (RETIRADO 2026-09-03, P-118) `actHint` ──────────────────────────────────────────────────
    // Devolvía las dos líneas que el popup de entrada iba a pintar bajo el hint del bioma:
    // «su mecánica te da un turno MÁS de margen» y «su mecánica golpea un turno ANTES». Nunca
    // llegó a tener un llamante en producción, y menos mal: desde los turnos FIJOS del 2026-08-30
    // ninguna de las dos era cierta, así que cablearlas habría puesto al juego a contarle al
    // jugador una regla inexistente en la mitad de los niveles del mapa — justo lo que la norma de
    // la casa prohíbe («la pista debe describir la regla REAL»). Si vuelve la curva de ocio,
    // vuelven estas líneas con ella y no antes.
}
