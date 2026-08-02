package com.devtime.attachment;

import com.devtime.attachment.dto.AttachmentResponses.QuotaResponse;

/**
 * Consumo e limite de armazenamento do tenant (RN-801, spec §22.2).
 *
 * <p>OB-08: a quota é fixa em 1 GB no MVP (RS-10). Em F6 ({@code future/018-subscriptions}) ela
 * passa a vir do plano — mudança <b>aditiva</b> nesta implementação, sem alteração de modelo,
 * porque {@code Attachment} já possui {@code sizeBytes} e o índice coberto de quota.
 */
public interface QuotaService {

    /** Consumo atual do tenant. CX-18: apenas registros não excluídos com binário presente. */
    QuotaResponse current();

    /**
     * Passo 6 de §6.1: a quota comporta mais {@code additionalBytes}?
     *
     * @throws com.devtime.shared.error.BusinessRuleException {@code DEVTIME-2701} / {@code 413}
     *     informando o consumo atual (FA-09)
     */
    void assertFits(long additionalBytes);
}
