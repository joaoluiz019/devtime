package com.devtime.shared.pagination;

import com.devtime.shared.error.BusinessRuleException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

/**
 * Validação dos limites de paginação (ART-073, RN-012).
 *
 * <p>O tamanho máximo é rejeitado explicitamente em vez de silenciosamente truncado: um cliente que
 * pede 500 itens e recebe 100 sem aviso passa a paginar errado e perde registros. Falhar alto
 * (CG-06) expõe o erro na integração, não em produção.
 */
@Component
public class PageRequestFactory {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    /**
     * @throws BusinessRuleException {@code DEVTIME-2006} / {@code 400} se {@code size} exceder 100
     */
    public Pageable validate(Pageable pageable) {
        if (pageable.getPageSize() > MAX_SIZE) {
            throw BusinessRuleException.pageSizeExceeded(pageable.getPageSize(), MAX_SIZE);
        }
        return pageable;
    }
}
