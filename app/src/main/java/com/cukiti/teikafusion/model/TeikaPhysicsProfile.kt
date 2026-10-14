package com.cukiti.teikafusion.model

/**
 * Personalidad física de cada tier vivo del tablero.
 *
 * La gravedad vertical sigue siendo universal: todos caen con el mismo ritmo y la lectura del
 * tablero no cambia según el tamaño. Lo que cambia es la respuesta lateral del jugador, la
 * resistencia de rodado, el rebote y la fuerza con la que un resultado de fusión reclama su punto
 * de nacimiento.
 *
 * [MEGA_TEIKA] conserva un perfil defensivo sólo para cargar saves heredados que pudieran traerlo
 * como cuerpo. En el juego actual T4+T4 dispara la expansión del Mega y no deja un cuerpo vivo.
 */
data class TeikaPhysicsProfile(
    val tiltResponse: Float,
    val rollingDrag: Float,
    val restitution: Float,
    val fusionPushSpeed: Float
)

object TeikaPhysicsProfiles {
    private val t1 = TeikaPhysicsProfile(
        tiltResponse = 1.15f,
        rollingDrag = 0.85f,
        restitution = 0.28f,
        fusionPushSpeed = 3.5f
    )
    private val t2 = TeikaPhysicsProfile(
        tiltResponse = 1.00f,
        rollingDrag = 1.00f,
        restitution = 0.22f,
        fusionPushSpeed = 5.0f
    )
    private val t3 = TeikaPhysicsProfile(
        tiltResponse = 0.82f,
        rollingDrag = 1.25f,
        restitution = 0.16f,
        fusionPushSpeed = 6.5f
    )
    private val t4 = TeikaPhysicsProfile(
        tiltResponse = 0.65f,
        rollingDrag = 1.55f,
        restitution = 0.10f,
        fusionPushSpeed = 8.0f
    )
    private val legacyMega = TeikaPhysicsProfile(
        tiltResponse = 0.50f,
        rollingDrag = 1.80f,
        restitution = 0.08f,
        fusionPushSpeed = 0f
    )

    fun of(type: TeikaType): TeikaPhysicsProfile = when (type) {
        TeikaType.TEIKA_1 -> t1
        TeikaType.TEIKA_2 -> t2
        TeikaType.TEIKA_3 -> t3
        TeikaType.TEIKA_4 -> t4
        TeikaType.MEGA_TEIKA -> legacyMega
    }
}
