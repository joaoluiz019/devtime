package com.devtime.worklog.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Filtros da listagem e dos totais (worklogs.md §5 e §8).
 *
 * <p>Objeto único em vez de onze parâmetros: BR-012 exige agrupar acima de cinco, e listagem e
 * totais precisam receber <b>exatamente</b> os mesmos filtros — o total exibido no topo da tela tem
 * de somar as linhas mostradas abaixo dele.
 *
 * <p>{@code userId} é filtro do requisitante, não escopo de segurança: o escopo de {@code MEMBER}
 * (§9 de permissions.md) é aplicado separadamente e <b>na consulta</b> (IMP-02, CP-16), inclusive
 * na contagem e nos totais — filtrar em memória vazaria a existência de registros de colegas pela
 * paginação.
 */
public record WorkLogFilter(
        UUID userId,
        UUID ticketId,
        UUID contractId,
        UUID clientId,
        UUID categoryId,
        List<UUID> tagIds,
        LocalDate dateFrom,
        LocalDate dateTo,
        Boolean billable,
        String source,
        String search) {

    /** Filtro vazio, para consultas sem restrição adicional. */
    public static WorkLogFilter empty() {
        return new WorkLogFilter(null, null, null, null, null, null, null, null, null, null, null);
    }
}
