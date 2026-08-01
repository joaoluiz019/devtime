package com.devtime.tag;

import com.devtime.tag.dto.TagRequests.TagLinkRequest;
import com.devtime.tag.dto.TagResponses.TagOptionResponse;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vínculo entre etiquetas e alvos (spec 006 §22.2).
 *
 * <p>Interface pública consumida por {@code 007-tickets}. A permissão é da feature consumidora — é
 * ela quem sabe se o requisitante pode alterar aquele ticket (§16 da spec).
 */
public interface TagLinkService {

    /**
     * Substitui atomicamente o conjunto de etiquetas do ticket (RN-313).
     *
     * <p>Substituição, e não adição incremental, porque é o que a edição de um ticket expressa: o
     * usuário envia as etiquetas que quer, não um delta. O limite de 10 é verificado sobre o
     * conjunto resultante, então trocar uma etiqueta quando já há 10 é permitido (CX-11).
     *
     * @param tagIds conjunto desejado; vazio remove todas
     * @return as etiquetas efetivamente vinculadas, já resolvidas para exibição
     */
    List<TagOptionResponse> replaceTicketTags(UUID ticketId, Collection<UUID> tagIds);

    /**
     * Converte {@code tagIds} e {@code tagNames} em um único conjunto de identificadores.
     *
     * <p>Nomes acionam {@code resolveOrCreate} (criação implícita, E-07).
     */
    List<UUID> resolveTagIds(TagLinkRequest request);

    /** Etiquetas de um ticket, na ordem de exibição. */
    List<TagOptionResponse> findByTicketId(UUID ticketId);

    /**
     * Etiquetas de vários tickets em <b>uma</b> consulta.
     *
     * <p>Evita N+1 na listagem e no quadro, onde o custo seria uma consulta por cartão.
     */
    Map<UUID, List<TagOptionResponse>> findByTicketIds(Collection<UUID> ticketIds);

    /** Desvincula todas as etiquetas do ticket, decrementando os contadores (INV-TAG-04). */
    void unlinkAllFromTicket(UUID ticketId);

    /**
     * Substitui atomicamente o conjunto de etiquetas do registro de horas (INV-TAG-01).
     *
     * <p>Interface pública consumida por {@code 008-worklogs}, fechando a dívida CE-O-03 de {@code
     * 006}: a tabela {@code work_log_tags} não podia existir antes de {@code work_logs}. A
     * permissão é da feature consumidora — é ela quem sabe se o requisitante pode alterar aquele
     * registro.
     */
    List<TagOptionResponse> replaceWorkLogTags(UUID workLogId, Collection<UUID> tagIds);

    /** Etiquetas de um registro de horas, na ordem de exibição. */
    List<TagOptionResponse> findByWorkLogId(UUID workLogId);

    /** Etiquetas de vários registros em <b>uma</b> consulta, evitando N+1 na listagem. */
    Map<UUID, List<TagOptionResponse>> findByWorkLogIds(Collection<UUID> workLogIds);

    /** Desvincula todas as etiquetas do registro, decrementando os contadores (INV-TAG-04). */
    void unlinkAllFromWorkLog(UUID workLogId);

    /** Registros de horas que possuem <b>todas</b> as etiquetas informadas (conjunção). */
    List<UUID> workLogIdsWithAllTags(Collection<UUID> tagIds);

    /**
     * Tickets que possuem <b>todas</b> as etiquetas informadas (conjunção, tickets.md §6).
     *
     * <p>Conjunção e não disjunção: filtrar por {@code urgente} e {@code checkout} significa "os
     * dois assuntos", não "qualquer um deles". A lista alimenta a especificação da listagem,
     * mantendo o filtro na consulta e nunca em memória (IMP-02).
     */
    List<UUID> ticketIdsWithAllTags(Collection<UUID> tagIds);
}
