package com.devtime.attachment;

import com.devtime.attachment.domain.AttachmentExceptions;
import com.devtime.attachment.dto.AttachmentResponses.QuotaResponse;
import com.devtime.shared.config.DevTimeProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementação de {@link QuotaService} (RN-801). */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuotaServiceImpl implements QuotaService {

    private final AttachmentRepository repository;
    private final DevTimeProperties properties;

    @Override
    @PreAuthorize("hasPermission(null, 'ATTACHMENT_VIEW')")
    public QuotaResponse current() {
        long used = repository.sumSizeByTenant(); // índice coberto idx_attachments_quota
        long limit = limitBytes();
        return new QuotaResponse(used, limit, percentage(used, limit));
    }

    /**
     * Passo 6 de §6.1.
     *
     * <p>CX-17: quota atingida com 1 GB exato rejeita o próximo upload. A comparação é {@code >},
     * não {@code >=}: um arquivo que faz o consumo chegar <b>exatamente</b> ao limite ainda cabe —
     * o limite é o que se pode ocupar, não o que se deve deixar livre.
     *
     * <p>R-09 / OB-03: a soma conta {@code sizeBytes} de todos os registros não excluídos,
     * inclusive os deduplicados que compartilham binário. É o que a spec determina, e a leitura é a
     * de quota <b>por uso</b> e não por ocupação física — o ganho de storage da deduplicação é do
     * produto, não do tenant.
     */
    @Override
    public void assertFits(long additionalBytes) {
        long used = repository.sumSizeByTenant();
        long limit = limitBytes();
        if (used + additionalBytes > limit) { // RN-801
            throw AttachmentExceptions.quotaExceeded(used, limit, additionalBytes);
        }
    }

    private long limitBytes() {
        return properties.attachment().tenantQuotaBytes();
    }

    /**
     * Percentual inteiro de 0 a 100.
     *
     * <p>{@code int} e não {@code double}: o valor existe para a UI decidir se exibe aviso acima de
     * 80% (§29), e uma casa decimal a mais não muda nenhuma decisão.
     */
    private int percentage(long used, long limit) {
        if (limit <= 0) {
            return 100;
        }
        return (int) Math.min(100, used * 100 / limit);
    }
}
