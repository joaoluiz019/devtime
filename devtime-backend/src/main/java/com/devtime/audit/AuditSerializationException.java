package com.devtime.audit;

import com.devtime.shared.error.BusinessRuleException;
import com.devtime.shared.error.ErrorCode;
import java.util.Map;

/**
 * Falha ao serializar o estado de um registro de auditoria.
 *
 * <p>ER-08: a auditoria é operação essencial (RN-006), então a falha propaga e reverte a alteração
 * — degradar silenciosamente produziria exatamente o que a regra proíbe, uma mudança sem trilha.
 *
 * <p>Responde {@code DEVTIME-9001} porque a causa é sempre defeito de programação (um valor não
 * serializável colocado no estado), nunca entrada do usuário — e a resposta não deve sugerir que
 * corrigir a requisição resolveria.
 */
public class AuditSerializationException extends BusinessRuleException {

    public AuditSerializationException(Throwable cause) {
        super(ErrorCode.UNEXPECTED, Map.of(), "Falha ao serializar o estado de auditoria");
        initCause(cause);
    }
}
