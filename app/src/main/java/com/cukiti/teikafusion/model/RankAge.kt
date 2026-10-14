package com.cukiti.teikafusion.model

/**
 * **P-437 · DE CUÁNDO ES EL PUESTO DEL CARNÉ.** El carné enseña el puesto de la última vez que se
 * miró la tabla (no consulta Firestore en cada visita a Inicio, P-227); sin fecha, un «#12» de hace
 * un mes se leía como el de hoy. Días de CALENDARIO en la zona del móvil, no bloques de 24 h: mirado
 * anoche a las 23:50 es «ayer» aunque solo hayan pasado diez minutos.
 */
object RankAge {
    /** «visto hoy», «visto ayer», «visto hace 5 días»; vacío si no hay fecha (puestos de antes de P-437). */
    fun label(seenAtMs: Long, nowMs: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        if (seenAtMs <= 0L) return ""
        val seen = java.time.Instant.ofEpochMilli(seenAtMs).atZone(zone).toLocalDate()
        val today = java.time.Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val days = java.time.temporal.ChronoUnit.DAYS.between(seen, today)
        return when {
            days <= 0L -> "visto hoy"
            days == 1L -> "visto ayer"
            else -> "visto hace $days días"
        }
    }
}
