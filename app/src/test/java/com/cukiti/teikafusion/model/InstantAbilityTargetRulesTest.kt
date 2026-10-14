package com.cukiti.teikafusion.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstantAbilityTargetRulesTest {

    @Test
    fun `rey no puede ejecutarse sin un teika evolucionable`() {
        val noTargets = InstantAbilityTargets()

        assertFalse(InstantAbilityTargetRules.canExecute("REY_DECRETO", noTargets))
        assertFalse(InstantAbilityTargetRules.canExecute("rey", noTargets))
    }

    @Test
    fun `las habilidades de tablero esperan su objetivo real`() {
        val noTargets = InstantAbilityTargets()

        listOf(
            "dormilona", "miedoso", "electrico", "fantasma", "planta", "hielo",
            "gloton", "ninja", "galactico", "toxico"
        ).forEach { effectKey ->
            assertFalse("$effectKey no debe lanzar sin objetivo", InstantAbilityTargetRules.canExecute(effectKey, noTargets))
        }
    }

    @Test
    fun `estrella sigue siendo ejecutable porque crea una ficha en la bandeja`() {
        assertTrue(InstantAbilityTargetRules.canExecute("ESTRELLA_LLUVIA", InstantAbilityTargets()))
        assertTrue(InstantAbilityTargetRules.canExecute("estrella", InstantAbilityTargets()))
    }

    @Test
    fun `cada requisito habilita su habilidad`() {
        assertTrue(InstantAbilityTargetRules.canExecute("REY_DECRETO", InstantAbilityTargets(hasBirthableTeika = true)))
        assertTrue(InstantAbilityTargetRules.canExecute("miedoso", InstantAbilityTargets(hasMovableWood = true)))
        assertTrue(InstantAbilityTargetRules.canExecute("toxico", InstantAbilityTargets(hasToxicWork = true)))
    }

    @Test
    fun `habilidades desconocidas conservan compatibilidad`() {
        assertTrue(InstantAbilityTargetRules.canExecute("habilidad-nueva", InstantAbilityTargets()))
    }
}
