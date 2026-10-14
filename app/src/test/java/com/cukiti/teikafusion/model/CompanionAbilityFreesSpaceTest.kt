package com.cukiti.teikafusion.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **EL CENSO DE LA LISTA BLANCA DEL ATASCO** (P-105, auditoría de la derrota 2026-09-02).
 *
 * ## Por qué existe
 *
 * `GameViewModel.evaluateDeadEnd()` indulta una derrota por «no cabe nada» solo si el jugador lleva
 * en el cinturón una carga de una habilidad marcada con [CompanionAbilityDef.freesSpace]. Esa marca
 * es **declarada a mano**, porque el motor no puede deducir qué hace cada efecto — y adivinarlo fue
 * exactamente el error que había: la puerta anterior preguntaba «¿esta habilidad tiene algo que
 * hacer?», y con eso **Parto Real** (que AÑADE un cuerpo) y **Deseo Estelar** (que RELLENA el hueco
 * conexo) suprimían la derrota mientras empeoraban el tablero, y las dos `PASSIVE_PIECES` la
 * suprimían **sin mirar el tablero siquiera**. El resultado era un nivel sin ninguna salida: la
 * ficha rebotaba y el juego se callaba para siempre.
 *
 * Una marca declarada a mano se queda vieja en silencio. Esto es lo que impide que eso pase: si
 * mañana entra un compañero nuevo, o una habilidad cambia de efecto, hay que decidir aquí de qué
 * lado cae.
 */
class CompanionAbilityFreesSpaceTest {

    /** Las que ABREN hueco: rompen, funden o retiran madera del tablero sin colocar nada. */
    private val liberan = setOf(
        "dormilona",  // el tablero se desploma y caen filas que rematan líneas al aterrizar
        "miedoso",    // la madera se desliza y va rompiendo cada línea que completa
        "planta",     // el capullo-cañón dispara y rompe madera
        "ninja",      // parte los bloques con estrella (se los lleva del tablero)
        "hielo",      // los carámbanos perforan las columnas más llenas
        "fantasma",   // los bloques poseídos se solidifican justo donde completan línea
        "gloton",     // los bloques con estrella se despegan y caen a su boca
        "toxico",     // el miasma corroe la madera hasta que revienta entera
        "galactico"   // el eclipse pulveriza el rincón de madera más denso
    )

    /** Y las que NO. Cada una con el motivo, que es lo que hay que releer si alguien las cambia. */
    private val noLiberan = mapOf(
        // Las dos pasivas necesitan COLOCAR una ficha para hacer algo, y colocar es justo lo que no
        // se puede en un tablero atascado. Eran las peores: `instantAbilityCanAct` las daba por
        // buenas sin consultar el tablero.
        "cristal" to "es PASSIVE_PIECES: necesita colocar fichas, que es lo que no se puede",
        "fuego" to "es PASSIVE_PIECES: el fuego arde AL COLOCAR la ficha en llamas",
        // Rayco no hace nada hasta que completas una línea — y no puedes.
        "electrico" to "solo energiza: el efecto espera a una línea que no vas a poder cerrar",
        // Y estas dos empeoran el tablero de forma activa.
        "estrella" to "RELLENA el espacio vacío conexo: deja menos sitio, no más",
        "rey" to "AÑADE un cuerpo al tablero: bloquea más celdas de las que había"
    )

    @Test
    fun `las catorce habilidades estan clasificadas`() {
        val censadas = liberan + noLiberan.keys
        assertEquals(
            "Hay compañeros sin clasificar en el censo (o el censo nombra alguno que ya no existe). " +
                "Si entra una habilidad nueva, decide aquí si abre hueco.",
            COMPANION_ABILITIES.keys.sorted(),
            censadas.sorted()
        )
    }

    @Test
    fun `la marca del codigo coincide con el censo`() {
        liberan.forEach { id ->
            assertTrue(
                "«$id» abre hueco según el censo, pero su CompanionAbilityDef no lo declara.",
                COMPANION_ABILITIES.getValue(id).freesSpace
            )
        }
        noLiberan.forEach { (id, motivo) ->
            assertFalse(
                "«$id» NO debería contar como salida de un atasco: $motivo",
                COMPANION_ABILITIES.getValue(id).freesSpace
            )
        }
    }

    @Test
    fun `ninguna pasiva cuenta como salida de un atasco`() {
        COMPANION_ABILITIES.forEach { (id, def) ->
            if (def.activation == AbilityActivation.PASSIVE_PIECES) {
                assertFalse(
                    "«$id» es pasiva sobre las fichas que COLOQUES: en un tablero donde no cabe " +
                        "nada no puede hacer absolutamente nada, así que no puede indultar la derrota.",
                    def.freesSpace
                )
            }
        }
    }

    @Test
    fun `las habilidades Teika tampoco abren hueco`() {
        // Estela y Reynaldo pasan por `TeikaAbilityLoadout`, no por COMPANION_ABILITIES, y el
        // ViewModel les construye un `CompanionAbilityDef` al vuelo. Ese def se construye SIN
        // `freesSpace`, o sea `false`, que es lo correcto para las dos — pero conviene que quede
        // escrito, porque si alguna vez se les añade una habilidad que sí rompa habrá que revisarlo.
        TEIKA_ABILITY_DEFINITIONS.forEach { def ->
            assertFalse(
                "«${def.companionId}» tiene habilidad Teika: si alguna abre hueco, hay que llevar " +
                    "la marca también a TeikaAbilityDef.",
                COMPANION_ABILITIES[def.companionId]?.freesSpace ?: false
            )
        }
    }
}
