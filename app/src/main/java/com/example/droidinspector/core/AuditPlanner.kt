package com.example.droidinspector.core

/** Cómo se puede responder a un rango con lo que este dispositivo conserva. */
sealed interface AuditPlan {

    /** Hay eventos finos: sesiones, horarios y aperturas. */
    data class Detailed(val range: AuditRange) : AuditPlan

    /** Solo agregados: tiempo por app, sin horarios ni sesiones. */
    data class Summarized(val range: AuditRange, val bucket: AuditBucket) : AuditPlan

    /** Ni siquiera el agregado más grueso llega tan atrás. */
    data class Unavailable(val earliestAvailableMillis: Long?) : AuditPlan
}

/**
 * Elige la fuente para un rango. Depende de la retención **medida en el dispositivo**, no de
 * constantes: los fabricantes podan distinto y cablear un límite daría respuestas falsas.
 *
 * Es puro para que la pantalla de selección y el ViewModel anuncien exactamente lo mismo.
 */
class AuditPlanner {

    fun plan(range: AuditRange, retention: RetentionWindows): AuditPlan {
        val eventsReachStart = retention.earliestEventMillis
            ?.let { range.startMillis >= it } == true
        if (eventsReachStart) return AuditPlan.Detailed(range)

        // El bucket más fino que alcance el inicio pedido: más fino = más detalle conservado
        val bucket = AuditBucket.entries.firstOrNull { candidate ->
            retention.earliestFor(candidate)?.let { range.startMillis >= it } == true
        }

        return if (bucket == null) {
            AuditPlan.Unavailable(earliestAvailable(retention))
        } else {
            AuditPlan.Summarized(range, bucket)
        }
    }

    /** La fecha más antigua con datos de cualquier fuente, para orientar al usuario. */
    fun earliestAvailable(retention: RetentionWindows): Long? =
        (retention.earliestByBucket.values + retention.earliestEventMillis)
            .filterNotNull()
            .minOrNull()
}
