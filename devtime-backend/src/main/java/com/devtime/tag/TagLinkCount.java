package com.devtime.tag;

import java.util.UUID;

/**
 * Contagem real de vínculos de uma etiqueta, por tabela de junção (INV-TAG-04).
 *
 * <p>Tipo de topo, e não record aninhado: Hibernate 6 não resolve o nome de uma classe aninhada em
 * {@code SELECT new} — exigiria a forma binária com {@code $}, que quebraria em silêncio a cada
 * renomeação. Mesma decisão de {@code MaintenanceTarget} em {@code 004}.
 */
public record TagLinkCount(UUID tagId, long links) {}
