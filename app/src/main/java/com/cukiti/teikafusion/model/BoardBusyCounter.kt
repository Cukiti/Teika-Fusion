package com.cukiti.teikafusion.model

/**
 * Contabilidad de «el tablero está actuando»: cuántas secuencias lo tienen tomado ahora mismo.
 *
 * Es un CONTADOR y no un booleano a propósito. Un turno se solapa consigo mismo —la cadena arranca
 * dentro de la colocación, y una habilidad automática puede entrar detrás—, y con un booleano el
 * primero en terminar le devolvía los mandos al jugador con el tablero aún moviéndose.
 *
 * Vive aquí, fuera del ViewModel y sin corrutinas, por lo de siempre en este proyecto: una regla
 * extraída es una regla que se puede probar en JVM ([BoardBusyCounterTest]). El ViewModel se queda
 * con lo que sí necesita a Android — publicar el `StateFlow` y el perro guardián que lo libera si
 * una corrutina muere a medias.
 *
 * **Todas las operaciones devuelven si el estado VISIBLE cambió**, para que quien publique el flow
 * no tenga que deducirlo.
 */
class BoardBusyCounter {

    var depth: Int = 0
        private set

    val isBusy: Boolean get() = depth > 0

    /** Una secuencia toma el tablero. @return `true` si acaba de pasar de libre a OCUPADO. */
    fun enter(): Boolean {
        depth++
        return depth == 1
    }

    /**
     * Una secuencia lo suelta. @return `true` si el tablero acaba de quedar LIBRE.
     *
     * Un `exit` de más **no baja de cero ni libera nada**: sin esa guarda, un turno fantasma (la
     * ficha que la re-validación rechaza) soltaría los mandos que aún tiene tomados OTRA colocación.
     */
    fun exit(): Boolean {
        if (depth == 0) return false
        depth--
        return depth == 0
    }

    /**
     * El perro guardián mordió: se suelta todo de golpe. @return `true` si de verdad estaba ocupado.
     *
     * Después de esto, los `exit` de las secuencias que seguían vivas son inofensivos (ver [exit]):
     * el jugador ya tiene los mandos y nada se los vuelve a quitar por accidente.
     */
    fun forceRelease(): Boolean {
        val wasBusy = depth > 0
        depth = 0
        return wasBusy
    }
}
